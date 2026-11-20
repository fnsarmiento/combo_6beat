package game.core.notes;

import engine.Engine;
import engine.ResourcesManager;
import game.Global;
import game.core.db.CustomSongRepository;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import processing.data.XML;

/**
 * Cargador dinámico de canciones base (resources/songs/base/) y custom (resources/songs/custom/).
 * Agrupa automáticamente todos los archivos .osu de una misma canción en un SongPack
 * con múltiples dificultades seleccionables, aplicando overrides opcionales desde songs.xml.
 */
public class SongPackLoader {

	public static class ChartVariant {
		public String id = "";
		public String title = "Cancion";
		public String artist = "";
		public String difficulty = "Normal";
		public String osuAbsPath = "";
		public String osuRelPath = "";
		public String coverRelPath = "";
		public int noteCount = 0;
		public float bpm = 120.0f;
		public float scrollSpeed = 0.22f;

		// Metadatos opcionales de jefe (sobrescribibles desde songs.xml)
		public String bossId = "";
		public String bossName = "Jefe";
		public String bossSpecies = "";
		public int bossHp = 500;
		public int attackDamage = 12;
		public int hitDamage = 6;
		public String bossSprite = "sprites/bosses/bear.png";
		public int bossRows = 3;
		public int bossCols = 3;
		public float bossDelay = 0.11f;
		public float bossScaleX = 1.6f;
		public float bossScaleY = 1.6f;
		public String defeatFlag = "";
		public String rewardItem = "";

		// Enlace opcional a SQLite si pertenece a CANCIONES_PERSONALIZADAS
		public CustomSongRepository.CustomSongSummary customDbSummary = null;
	}

	public static class SongPack {
		public String key = "";
		public String title = "Cancion";
		public String artist = "";
		public String coverRelPath = "";
		public boolean isCustom = false;
		public final List<ChartVariant> difficulties = new ArrayList<>();
	}

	private static List<SongPack> _cachedBasePacks = null;

	public static void invalidateBaseCache() {
		_cachedBasePacks = null;
	}

	/**
	 * Escanea dinámicamente resources/songs/base/ agrupando todos los .osu por carpeta/canción
	 * y aplicando metadatos opcionales de resources/songs/songs.xml si existen.
	 */
	public static List<SongPack> loadBasePacks() {
		return loadBasePacks(false);
	}

	public static List<SongPack> loadBasePacks(boolean forceRefresh) {
		if (!forceRefresh && _cachedBasePacks != null) {
			return _cachedBasePacks;
		}
		Map<String, XML[]> manifestOverrides = readManifestOverrides();
		Map<String, SongPack> packMap = new LinkedHashMap<>();

		File baseDir = new File(ResourcesManager.getAbsPathFromProject("resources/songs/base"));
		if (baseDir.exists() && baseDir.isDirectory()) {
			List<Path> osuFiles = new ArrayList<>();
			try (Stream<Path> walk = Files.walk(baseDir.toPath(), 4)) {
				walk.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".osu"))
					.sorted()
					.forEach(osuFiles::add);
			} catch (IOException e) {
				System.err.println("Error escaneando resources/songs/base/: " + e.getMessage());
			}

			for (Path osuPath : osuFiles) {
				File osuFile = osuPath.toFile();
				ChartVariant variant = parseOsuQuick(osuFile);
				if (variant == null) continue;

				String normKey = normalizeRelKey(variant.osuRelPath);
				XML[] overridePair = manifestOverrides.get(normKey);
				if (overridePair != null) {
					applyManifestOverride(variant, overridePair[0], overridePair[1]);
				} else {
					variant.bossName = variant.title;
					variant.bossSpecies = variant.artist;
				}

				if (variant.coverRelPath.isEmpty()) {
					variant.coverRelPath = findFolderCoverRel(osuFile.getParentFile());
				}

				String packKey = (osuFile.getParentFile() != null)
					? osuFile.getParentFile().getAbsolutePath()
					: (variant.title + "|" + variant.artist);

				SongPack pack = packMap.computeIfAbsent(packKey, k -> {
					SongPack p = new SongPack();
					p.key = k;
					p.title = variant.title;
					p.artist = variant.artist;
					p.coverRelPath = variant.coverRelPath;
					p.isCustom = false;
					return p;
				});
				if (pack.coverRelPath.isEmpty() && !variant.coverRelPath.isEmpty()) {
					pack.coverRelPath = variant.coverRelPath;
				}
				pack.difficulties.add(variant);
			}
		}

		List<SongPack> result = new ArrayList<>(packMap.values());
		for (SongPack p : result) {
			p.difficulties.sort(Comparator.comparingInt((ChartVariant c) -> c.noteCount)
				.thenComparing(c -> c.difficulty, String.CASE_INSENSITIVE_ORDER));
		}
		result.sort(Comparator.comparing(p -> p.title, String.CASE_INSENSITIVE_ORDER));
		_cachedBasePacks = result;
		return result;
	}

	/**
	 * Sincroniza resources/songs/custom/ con SQLite y agrupa las canciones personalizadas
	 * por título+artista con sus dificultades seleccionables.
	 */
	public static List<SongPack> loadCustomPacks(boolean syncFolder) {
		if (syncFolder) {
			CustomSongRepository.syncCustomSongsFolder();
		}
		List<CustomSongRepository.CustomSongSummary> rows = CustomSongRepository.listAll();
		Map<String, SongPack> packMap = new LinkedHashMap<>();

		for (CustomSongRepository.CustomSongSummary row : rows) {
			ChartVariant v = new ChartVariant();
			v.id = "custom_" + row.idCancion;
			v.title = row.titulo;
			v.artist = row.artista;
			v.difficulty = row.dificultad;
			v.osuRelPath = row.rutaChart;
			v.osuAbsPath = ResourcesManager.getAbsPathFromProject(row.rutaChart);
			v.coverRelPath = row.rutaPortada;
			v.noteCount = row.cantidadNotas;
			v.bpm = row.bpm;
			v.bossId = v.id;
			v.bossName = row.titulo;
			v.bossSpecies = row.dificultad + " | " + Math.round(row.bpm) + " BPM";
			v.customDbSummary = row;

			String packKey = (row.titulo + "|" + row.artista).toLowerCase(Locale.ROOT);
			SongPack pack = packMap.computeIfAbsent(packKey, k -> {
				SongPack p = new SongPack();
				p.key = k;
				p.title = row.titulo;
				p.artist = row.artista;
				p.coverRelPath = row.rutaPortada;
				p.isCustom = true;
				return p;
			});
			if (pack.coverRelPath.isEmpty() && !row.rutaPortada.isEmpty()) {
				pack.coverRelPath = row.rutaPortada;
			}
			pack.difficulties.add(v);
		}

		List<SongPack> result = new ArrayList<>(packMap.values());
		for (SongPack p : result) {
			p.difficulties.sort(Comparator.comparingInt((ChartVariant c) -> c.noteCount)
				.thenComparing(c -> c.difficulty, String.CASE_INSENSITIVE_ORDER));
		}
		return result;
	}

	private static Map<String, XML[]> readManifestOverrides() {
		Map<String, XML[]> map = new HashMap<>();
		File manifestFile = new File(ResourcesManager.getAbsPathFromProject(Global.SONGS_MANIFEST));
		if (!manifestFile.exists() || Engine.getApp() == null) return map;

		try {
			XML root = Engine.getApp().loadXML(manifestFile.getAbsolutePath());
			if (root == null) return map;
			for (XML pack : root.getChildren("Pack")) {
				for (XML chart : pack.getChildren("Chart")) {
					String f = chart.getString("file", "");
					if (!f.isEmpty()) {
						map.put(normalizeRelKey(f), new XML[] { pack, chart });
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error leyendo overrides de songs.xml: " + e.getMessage());
		}
		return map;
	}

	private static void applyManifestOverride(ChartVariant v, XML packNode, XML chartNode) {
		if (packNode != null) {
			String pTitle = packNode.getString("title", "");
			String pArtist = packNode.getString("artist", "");
			String pCover = packNode.getString("cover", "");
			if (!pTitle.isEmpty()) v.title = pTitle;
			if (!pArtist.isEmpty()) v.artist = pArtist;
			if (!pCover.isEmpty()) v.coverRelPath = pCover.startsWith("resources/") ? pCover : ("resources/" + pCover);
		}
		if (chartNode != null) {
			String cid = chartNode.getString("id", "");
			if (!cid.isEmpty()) v.id = cid;
			String diff = chartNode.getString("difficulty", "");
			if (!diff.isEmpty()) v.difficulty = diff;
			v.scrollSpeed = chartNode.getFloat("scroll-speed", v.scrollSpeed);
			v.bossId = chartNode.getString("boss-id", v.id);
			v.bossName = chartNode.getString("boss-name", v.title);
			v.bossSpecies = chartNode.getString("boss-species", v.artist);
			v.bossHp = chartNode.getInt("boss-hp", v.bossHp);
			v.attackDamage = chartNode.getInt("attack-damage", v.attackDamage);
			v.hitDamage = chartNode.getInt("hit-damage", v.hitDamage);
			v.bossSprite = chartNode.getString("boss-sprite", v.bossSprite);
			v.bossRows = chartNode.getInt("boss-rows", v.bossSprite.endsWith("bear.png") ? 3 : 1);
			v.bossCols = chartNode.getInt("boss-cols", v.bossSprite.endsWith("bear.png") ? 3 : 1);
			v.bossDelay = chartNode.getFloat("boss-delay", v.bossDelay);
			v.bossScaleX = chartNode.getFloat("boss-scale-x", v.bossScaleX);
			v.bossScaleY = chartNode.getFloat("boss-scale-y", v.bossScaleY);
			v.defeatFlag = chartNode.getString("defeat-flag", v.defeatFlag);
			v.rewardItem = chartNode.getString("reward-item", v.rewardItem);
		}
	}

	private static ChartVariant parseOsuQuick(File osuFile) {
		ChartVariant v = new ChartVariant();
		v.osuAbsPath = osuFile.getAbsolutePath();
		v.osuRelPath = toProjectRel(osuFile);
		v.id = v.osuRelPath;
		v.title = osuFile.getName().replace(".osu", "");

		try (BufferedReader br = new BufferedReader(new FileReader(osuFile))) {
			String line;
			String section = "";
			int notes = 0;
			boolean foundBpm = false;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("//")) continue;
				if (line.startsWith("[") && line.endsWith("]")) {
					section = line;
					continue;
				}
				if ("[Metadata]".equals(section)) {
					int idx = line.indexOf(':');
					if (idx > 0) {
						String k = line.substring(0, idx).trim();
						String val = line.substring(idx + 1).trim();
						switch (k) {
							case "Title" -> { if (!val.isEmpty()) v.title = val; }
							case "Artist" -> v.artist = val;
							case "Version" -> { if (!val.isEmpty()) v.difficulty = val; }
						}
					}
				} else if ("[TimingPoints]".equals(section) && !foundBpm) {
					String[] parts = line.split(",");
					if (parts.length >= 2) {
						try {
							float beatLen = Float.parseFloat(parts[1].trim());
							if (beatLen > 0.0f) {
								v.bpm = Math.round((60000.0f / beatLen) * 10.0f) / 10.0f;
								foundBpm = true;
							}
						} catch (NumberFormatException ignored) {}
					}
				} else if ("[HitObjects]".equals(section)) {
					notes++;
				}
			}
			v.noteCount = notes;
			return v;
		} catch (Exception e) {
			return null;
		}
	}

	private static String findFolderCoverRel(File folder) {
		if (folder == null || !folder.isDirectory()) return "";
		File[] imgs = folder.listFiles((dir, name) -> {
			String l = name.toLowerCase(Locale.ROOT);
			return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png");
		});
		if (imgs == null || imgs.length == 0) return "";
		return toProjectRel(imgs[0]);
	}

	private static String toProjectRel(File file) {
		try {
			Path root = Path.of(ResourcesManager.getAbsPathFromProject("")).toAbsolutePath().normalize();
			Path target = file.toPath().toAbsolutePath().normalize();
			return root.relativize(target).toString().replace('\\', '/');
		} catch (Exception e) {
			return file.getName();
		}
	}

	private static String normalizeRelKey(String p) {
		if (p == null) return "";
		String s = p.replace('\\', '/').trim();
		if (s.startsWith("resources/")) s = s.substring("resources/".length());
		return s.toLowerCase(Locale.ROOT);
	}
}
