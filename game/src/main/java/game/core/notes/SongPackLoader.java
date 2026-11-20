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
 * Cargador y agrupador dinámico de paquetes de canciones:
 * <ul>
 *   <li><b>Canciones Base ({@code resources/songs/base/}):</b> escanea recursivamente archivos {@code .osu},
 *       los agrupa por carpeta dentro de un {@link SongPack} con sus variantes de dificultad ({@link ChartVariant})
 *       ordenadas por número de notas, y aplica overrides definidos en {@code resources/songs/songs.xml}.</li>
 *   <li><b>Canciones Personalizadas ({@code resources/songs/custom/}):</b> sincroniza el directorio con la tabla
 *       SQLite {@code CANCIONES_PERSONALIZADAS} mediante {@link CustomSongRepository} y agrupa las dificultades
 *       por {@code título|artista}.</li>
 * </ul>
 */
public class SongPackLoader {

	// =========================================================================
	// Modelos de Datos de Paquetes y Variantes de Dificultad
	// =========================================================================

	/**
	 * Representa una dificultad individual ({@code .osu}) dentro de un {@link SongPack}.
	 */
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
		public float durationSec = 1.0f;
		public float nps = 0.0f;
		public float difficultyRating = 1.0f;
		public String difficultyTier = "Facil";
		public float scrollSpeed = 0.22f;

		// Enlace opcional a SQLite si pertenece a CANCIONES_PERSONALIZADAS
		public CustomSongRepository.CustomSongSummary customDbSummary = null;
	}

	/**
	 * Agrupa múltiples dificultades ({@link ChartVariant}) que pertenecen a una misma canción.
	 */
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

	// =========================================================================
	// Escaneo y Agrupación de Canciones Base (resources/songs/base/)
	// =========================================================================

	/**
	 * Escanea {@code resources/songs/base/} agrupando todos los {@code .osu} por carpeta
	 * y aplicando metadatos opcionales de {@code resources/songs/songs.xml}.
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
			p.difficulties.sort(Comparator.comparingDouble((ChartVariant c) -> c.difficultyRating)
				.thenComparingInt(c -> c.noteCount)
				.thenComparing(c -> c.difficulty, String.CASE_INSENSITIVE_ORDER));
		}
		result.sort(Comparator.comparing(p -> p.title, String.CASE_INSENSITIVE_ORDER));
		_cachedBasePacks = result;
		return result;
	}

	// =========================================================================
	// Escaneo y Agrupación de Canciones Personalizadas (resources/songs/custom/)
	// =========================================================================

	/**
	 * Sincroniza {@code resources/songs/custom/} con SQLite y agrupa las canciones personalizadas
	 * por {@code título|artista} con sus dificultades ordenadas por densidad de notas.
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
			populateDurationAndDifficulty(v, new File(v.osuAbsPath));
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
			p.difficulties.sort(Comparator.comparingDouble((ChartVariant c) -> c.difficultyRating)
				.thenComparingInt(c -> c.noteCount)
				.thenComparing(c -> c.difficulty, String.CASE_INSENSITIVE_ORDER));
		}
		return result;
	}

	// =========================================================================
	// Overrides desde songs.xml y Parseo Ligero de Cabeceras .osu
	// =========================================================================

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
		}
	}

	/**
	 * Lectura rápida de un {@code .osu} para poblar selectores sin cargar audio ni sprites.
	 */
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
			long firstNoteMs = -1;
			long lastNoteMs = -1;
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
							case "Title"   -> { if (!val.isEmpty()) v.title = val; }
							case "Artist"  -> v.artist = val;
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
					String[] parts = line.split(",");
					if (parts.length >= 3) {
						try {
							long t = Long.parseLong(parts[2].trim());
							if (firstNoteMs < 0 || t < firstNoteMs) firstNoteMs = t;
							if (t > lastNoteMs) lastNoteMs = t;
						} catch (NumberFormatException ignored) {}
					}
				}
			}
			v.noteCount = notes;
			v.durationSec = computeEffectiveDurationSec(notes, v.bpm, firstNoteMs, lastNoteMs);
			applyCalculatedDifficulty(v);
			return v;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Calcula la duración activa desde las marcas temporales de {@code [HitObjects]}
	 * para rellenar {@code durationSec}, {@code nps}, {@code difficultyRating} y {@code difficultyTier}.
	 */
	private static void populateDurationAndDifficulty(ChartVariant v, File osuFile) {
		long firstNoteMs = -1;
		long lastNoteMs = -1;
		if (osuFile != null && osuFile.exists() && osuFile.isFile()) {
			try (BufferedReader br = new BufferedReader(new FileReader(osuFile))) {
				String line;
				String section = "";
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("//")) continue;
					if (line.startsWith("[") && line.endsWith("]")) {
						section = line;
						continue;
					}
					if ("[HitObjects]".equals(section)) {
						String[] parts = line.split(",");
						if (parts.length >= 3) {
							try {
								long t = Long.parseLong(parts[2].trim());
								if (firstNoteMs < 0 || t < firstNoteMs) firstNoteMs = t;
								if (t > lastNoteMs) lastNoteMs = t;
							} catch (NumberFormatException ignored) {}
						}
					}
				}
			} catch (IOException ignored) {}
		}
		v.durationSec = computeEffectiveDurationSec(v.noteCount, v.bpm, firstNoteMs, lastNoteMs);
		applyCalculatedDifficulty(v);
	}

	private static float computeEffectiveDurationSec(int notes, float bpm, long firstNoteMs, long lastNoteMs) {
		if (firstNoteMs >= 0 && lastNoteMs > firstNoteMs) {
			return Math.max(1.0f, (lastNoteMs - firstNoteMs) / 1000.0f);
		}
		// Respaldo estimado si no hay rango temporal válido
		float beatsPerSec = Math.max(1.0f, bpm / 60.0f);
		return Math.max(1.0f, notes / Math.max(1.0f, beatsPerSec * 1.5f));
	}

	private static void applyCalculatedDifficulty(ChartVariant v) {
		v.nps = computeNotesPerSecond(v.noteCount, v.durationSec, 1.0f);
		v.difficultyRating = computeDifficultyRating(v.noteCount, v.bpm, v.durationSec, 1.0f);
		v.difficultyTier = getDifficultyTierLabel(v.difficultyRating);
	}

	/** Calcula las notas por segundo efectivas aplicando el multiplicador de velocidad. */
	public static float computeNotesPerSecond(int noteCount, float durationSec, float speedMultiplier) {
		float speed = Math.max(0.5f, speedMultiplier);
		float effDur = Math.max(1.0f, durationSec / speed);
		return noteCount / effDur;
	}

	/**
	 * Calcula una calificación de dificultad combinando densidad temporal (NPS),
	 * notas por pulso ({@code nps / (bpm / 60)}) y velocidad base ({@code bpm}) con factor de velocidad.
	 */
	public static float computeDifficultyRating(int noteCount, float bpm, float durationSec, float speedMultiplier) {
		if (noteCount <= 0) return 0.0f;
		float speed = Math.max(0.5f, speedMultiplier);
		float effBpm = Math.max(1.0f, bpm * speed);
		float nps = computeNotesPerSecond(noteCount, durationSec, speed);
		float notesPerBeat = nps / Math.max(0.5f, effBpm / 60.0f);
		float raw = 0.52f * nps + 0.60f * notesPerBeat + 0.30f * (effBpm / 100.0f);
		return Math.max(0.5f, Math.round(raw * 10.0f) / 10.0f);
	}

	/** Devuelve la etiqueta textual correspondiente al puntaje de dificultad calculado. */
	public static String getDifficultyTierLabel(float rating) {
		if (rating < 2.2f) return "Muy Facil";
		if (rating < 3.6f) return "Facil";
		if (rating < 5.8f) return "Normal";
		if (rating < 7.8f) return "Dificil";
		return "Experto";
	}

	// =========================================================================
	// Utilidades de Portadas y Normalización de Rutas Relativas
	// =========================================================================

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
