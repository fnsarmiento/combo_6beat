package engine;

import engine.audio.Audio;
import engine.audio.MusicTrack;
import engine.draw.Sprite2D;
import engine.objects.GameObject;
import java.io.File;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import javax.imageio.ImageIO;
import kuusisto.tinysound.Sound;
import processing.core.PConstants;
import processing.core.PImage;
import processing.data.JSONObject;
import processing.data.XML;

/**
 * Gestor centralizado de resolución de rutas relativas, carga de archivos (XML/JSON),
 * instanciación de {@link GameObject} desde mapas XML y caché de imágenes/audio.
 */
public class ResourcesManager {

	// =========================================================================
	// Rutas de Contexto y Caché de Imágenes
	// =========================================================================

	private static final Path _projectPath = Path.of("").toAbsolutePath().normalize();
	private static Path _currentMapPath = null;
	private static Path _resourcesBasePath = _projectPath.resolve("resources").normalize();

	private static final HashMap<String, PImage> _imageCache = new HashMap<>();

	// =========================================================================
	// Resolución de Rutas Relativas
	// =========================================================================

	/**
	 * Resuelve una ruta relativa respecto a la raíz del proyecto ({@code game/}).
	 */
	public static String getAbsPathFromProject(String relativePath) {
		if (Path.of(relativePath).isAbsolute()) return relativePath;
		return _projectPath.resolve(relativePath).normalize().toString();
	}

	/**
	 * Resuelve una ruta relativa respecto a la carpeta de recursos activa ({@code _resourcesBasePath}).
	 */
	public static String getResourceAbsPath(String relativePath) {
		if (Path.of(relativePath).isAbsolute()) return relativePath;
		return _resourcesBasePath.resolve(relativePath).normalize().toString();
	}

	public static String getProjectPath() {
		return _projectPath.toString();
	}

	public static String getCurrentMapPath() {
		return _currentMapPath != null ? _currentMapPath.toString() : "";
	}

	public static String getResourcesBasePath() {
		return _resourcesBasePath.toString();
	}

	// =========================================================================
	// Carga de Archivos XML, JSON y Mapas de Escena
	// =========================================================================

	public static XML loadXML(String path) {
		String abs = getResourceAbsPath(path);
		if (Engine.getApp() != null) {
			return Engine.getApp().loadXML(abs);
		}
		try {
			return new XML(new File(abs));
		} catch (Exception e) {
			throw new RuntimeException("No se pudo cargar XML: " + abs, e);
		}
	}

	public static JSONObject loadJSON(String path) {
		String abs = getResourceAbsPath(path);
		if (Engine.getApp() != null) {
			return Engine.getApp().loadJSONObject(abs);
		}
		try {
			return JSONObject.parse(Files.readString(Path.of(abs)));
		} catch (Exception e) {
			throw new RuntimeException("No se pudo cargar JSON: " + abs, e);
		}
	}

	/**
	 * Actualiza el contexto del mapa actual y recalcula {@code _resourcesBasePath} a partir
	 * del atributo {@code resources} de la escena XML.
	 */
	public static void setMapContext(String path, String resourcesProp) {
		_currentMapPath = Path.of(getAbsPathFromProject(path)).normalize();
		String prop = (resourcesProp != null && !resourcesProp.isEmpty()) ? resourcesProp : "resources/";
		if (Path.of(prop).isAbsolute()) {
			_resourcesBasePath = Path.of(prop).normalize();
		} else if (_currentMapPath.getParent() != null) {
			_resourcesBasePath = _currentMapPath.getParent().resolve(prop).normalize();
		}
	}

	/**
	 * Carga un archivo XML de escena y configura automáticamente el directorio base de recursos.
	 */
	public static XML loadMap(String path) {
		_currentMapPath = Path.of(getAbsPathFromProject(path)).normalize();

		XML xml;
		if (Engine.getApp() != null) {
			xml = Engine.getApp().loadXML(_currentMapPath.toString());
		} else {
			try {
				xml = new XML(_currentMapPath.toFile());
			} catch (Exception e) {
				throw new RuntimeException("No se pudo cargar mapa XML: " + _currentMapPath, e);
			}
		}

		String resourcesProp = xml.getString("resources", "resources/");
		setMapContext(path, resourcesProp);

		return xml;
	}

	// =========================================================================
	// Instanciación de Objetos desde Nodos XML
	// =========================================================================

	public static Sprite2D loadSprite(XML xml) {
		Sprite2D spr = new Sprite2D("", 0);
		spr.fromXML(xml);
		return spr;
	}

	public static Sprite2D[] loadSprites(XML mapXml) {
		if (mapXml == null) return new Sprite2D[0];
		XML[] children = mapXml.getChildren("Sprite");
		Sprite2D[] sprites = new Sprite2D[children.length];
		for (int i = 0; i < children.length; i++) {
			sprites[i] = loadSprite(children[i]);
		}
		return sprites;
	}

	/**
	 * Instancia por reflexión una subclase de {@link GameObject} (requiere constructor público vacío)
	 * y deserializa sus propiedades llamando a {@link GameObject#fromXML(XML)}.
	 */
	public static <T extends GameObject> T loadCustomObject(XML xml, Class<T> clazz) {
		try {
			T obj = clazz.getDeclaredConstructor().newInstance();
			obj.fromXML(xml);
			return obj;
		} catch (Exception e) {
			throw new RuntimeException("Error al instanciar " + clazz.getSimpleName() + ". Verifica que tenga un constructor vacío.", e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T extends GameObject> T[] loadCustomObjects(XML mapXml, String tagName, Class<T> clazz) {
		if (mapXml == null) return (T[]) Array.newInstance(clazz, 0);
		XML[] children = mapXml.getChildren(tagName);
		T[] objects = (T[]) Array.newInstance(clazz, children.length);
		for (int i = 0; i < children.length; i++) {
			objects[i] = loadCustomObject(children[i], clazz);
		}
		return objects;
	}

	// =========================================================================
	// Carga y Caché de Imágenes y Audio
	// =========================================================================

	/**
	 * Carga una imagen resolviendo primero respecto a {@code _resourcesBasePath} y luego respecto
	 * al directorio del mapa activo, reutilizando instancias en caché por ruta absoluta.
	 */
	public static PImage loadImage(String path) {
		String absPath = getResourceAbsPath(path);
		File f = new File(absPath);
		if (!f.exists() && _currentMapPath != null && _currentMapPath.getParent() != null) {
			Path fromMap = _currentMapPath.getParent().resolve(path).normalize();
			if (fromMap.toFile().exists()) {
				absPath = fromMap.toString();
			}
		}
		return _imageCache.computeIfAbsent(absPath, k -> {
			if (Engine.getApp() != null) {
				return Engine.getApp().loadImage(k);
			}
			try {
				java.awt.image.BufferedImage bimg = ImageIO.read(new File(k));
				if (bimg == null) return null;
				PImage pimg = new PImage(bimg.getWidth(), bimg.getHeight(), PConstants.ARGB);
				bimg.getRGB(0, 0, pimg.width, pimg.height, pimg.pixels, 0, pimg.width);
				pimg.updatePixels();
				return pimg;
			} catch (Exception e) {
				return null;
			}
		});
	}

	public static void clearImageCache() {
		_imageCache.clear();
	}

	public static MusicTrack loadBGM(String path) {
		return Audio.loadBGM(getResourceAbsPath(path));
	}

	public static Sound loadSFX(String path) {
		return Audio.loadSFX(getResourceAbsPath(path));
	}
}
