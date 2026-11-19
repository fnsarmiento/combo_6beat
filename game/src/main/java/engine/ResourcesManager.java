package engine;

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.HashMap;

import engine.audio.Audio;
import engine.audio.MusicTrack;
import engine.draw.Sprite2D;
import engine.objects.GameObject;
import engine.physics.Box;
import kuusisto.tinysound.Sound;
import processing.core.PImage;
import processing.data.JSONObject;
import processing.data.XML;

public class ResourcesManager {
	private static final Path _projectPath = Path.of("").toAbsolutePath().normalize();
	private static Path _currentMapPath = null;
	private static Path _resourcesBasePath = _projectPath.resolve("resources").normalize();

	private static final HashMap<String, PImage> _imageCache = new HashMap<>();

	public static String getAbsPathFromProject(String relativePath) {
		if (Path.of(relativePath).isAbsolute()) return relativePath;
		return _projectPath.resolve(relativePath).normalize().toString();
	}

	public static String getResourceAbsPath(String relativePath) {
		if (Path.of(relativePath).isAbsolute()) return relativePath;
		return _resourcesBasePath.resolve(relativePath).normalize().toString();
	}

	// Files

	public static XML loadXML(String path) {
		String abs = getResourceAbsPath(path);
		if (Engine.getApp() != null) {
			return Engine.getApp().loadXML(abs);
		}
		try {
			return new XML(new java.io.File(abs));
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
			return JSONObject.parse(java.nio.file.Files.readString(Path.of(abs)));
		} catch (Exception e) {
			throw new RuntimeException("No se pudo cargar JSON: " + abs, e);
		}
	}

	public static void setMapContext(String path, String resourcesProp) {
		_currentMapPath = Path.of(getAbsPathFromProject(path)).normalize();
		String prop = (resourcesProp != null && !resourcesProp.isEmpty()) ? resourcesProp : "resources/";
		if (Path.of(prop).isAbsolute()) {
			_resourcesBasePath = Path.of(prop).normalize();
		} else if (_currentMapPath.getParent() != null) {
			_resourcesBasePath = _currentMapPath.getParent().resolve(prop).normalize();
		}
	}

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

	// Sprite

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

	// Box

	public static Box loadBox(XML xml) {
		Box box = new Box(0, 0, 0, 0);
		box.fromXML(xml);
		return box;
	}

	public static Box[] loadBoxes(XML mapXml) {
		if (mapXml == null) return new Box[0];
		java.util.ArrayList<Box> boxes = new java.util.ArrayList<>();
		for (XML child : mapXml.getChildren()) {
			String name = child.getName();
			if ("Box".equals(name) || "box".equals(name) || "Rect".equals(name)) {
				boxes.add(loadBox(child));
			}
		}
		return boxes.toArray(new Box[0]);
	}

	// Custom GameObjects

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

	// Images

	public static PImage loadImage(String path) {
		String absPath = getResourceAbsPath(path);
		java.io.File f = new java.io.File(absPath);
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
				java.awt.image.BufferedImage bimg = javax.imageio.ImageIO.read(new java.io.File(k));
				if (bimg == null) return null;
				PImage pimg = new PImage(bimg.getWidth(), bimg.getHeight(), processing.core.PConstants.ARGB);
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

	// Audio

	public static MusicTrack loadBGM(String path) { return Audio.loadBGM(getResourceAbsPath(path)); }

	public static Sound loadSFX(String path) { return Audio.loadSFX(getResourceAbsPath(path)); }

	// Others

	public static String getProjectPath() { return _projectPath.toString(); }

	public static String getCurrentMapPath() { return _currentMapPath != null ? _currentMapPath.toString() : ""; }

	public static String getResourcesBasePath() { return _resourcesBasePath.toString(); }
}
