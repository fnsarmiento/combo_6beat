package game.core.db;

import engine.ResourcesManager;
import game.core.GameState;
import game.core.notes.OsuManiaReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Repositorio SQLite en 3FN para el módulo independiente de Canciones Personalizadas.
 *
 * <p>Gestiona las tablas {@code CANCIONES_PERSONALIZADAS} y {@code CANCION_OBJETOS_INTENTO}
 * (con validación mediante {@code trg_canciones_validar_insert}/{@code trg_canciones_validar_update}
 * y promoción automática de récords e ítems mediante {@code trg_canciones_record_after_insert} y
 * {@code trg_canciones_record_after_update}), y consulta la vista {@code v_estadisticas_canciones_custom}.
 */
public class CustomSongRepository {

	// =========================================================================
	// Constantes y DTO de Resumen de Canción Personalizada
	// =========================================================================

	public static final String CUSTOM_SONGS_DIR = "resources/songs/custom";

	public static class CustomSongSummary {
		public long idCancion;
		public String claveUnica = "";
		public String titulo = "Cancion Custom";
		public String artista = "";
		public String dificultad = "6K";
		public float bpm = 120.0f;
		public String rutaPortada = "";
		public String rutaAudio = "";
		public String rutaChart = "";
		public int cantidadNotas = 0;

		public int puntajeUltimoIntento = 0;
		public int comboUltimoIntento = 0;
		public float precisionUltimoIntento = 0.0f;

		public int puntajeMasAlto = 0;
		public int comboMasAlto = 0;
		public float precisionMasAlta = 0.0f;

		public int vecesJugada = 0;
		public String fechaUltimoIntento = "";
		public String fechaRecord = "";
		public String objetosRecord = "Ninguno";
		public String objetosUltimoIntento = "Ninguno";
	}

	// =========================================================================
	// Sincronización con Carpeta resources/songs/custom/ e Importación Externa
	// =========================================================================

	/**
	 * Escanea {@code resources/songs/custom/}, registra o actualiza cada {@code .osu} en
	 * {@code CANCIONES_PERSONALIZADAS} y depura registros cuyos archivos fueron eliminados de disco.
	 */
	public static void syncCustomSongsFolder() {
		File dir = new File(ResourcesManager.getAbsPathFromProject(CUSTOM_SONGS_DIR));
		if (!dir.exists()) {
			dir.mkdirs();
			return;
		}
		if (!dir.isDirectory()) return;

		List<Path> osuFiles = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(dir.toPath(), 4)) {
			walk.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".osu"))
				.sorted()
				.forEach(osuFiles::add);
		} catch (IOException e) {
			System.err.println("Error escaneando canciones custom: " + e.getMessage());
			return;
		}

		for (Path osuPath : osuFiles) {
			registerOrUpdateOsuFile(osuPath.toFile());
		}

		// Eliminar de la BD canciones custom cuyos archivos .osu ya no existen en disco
		pruneMissingCustomSongs();
	}

	/**
	 * Elimina de {@code CANCIONES_PERSONALIZADAS} las filas cuyo {@code ruta_chart} ya no existe en disco
	 * (borrando en cascada sus filas en {@code CANCION_OBJETOS_INTENTO}).
	 */
	private static void pruneMissingCustomSongs() {
		List<Long> toDelete = new ArrayList<>();
		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement("SELECT id_cancion, ruta_chart FROM CANCIONES_PERSONALIZADAS");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				long id = rs.getLong("id_cancion");
				String ruta = rs.getString("ruta_chart");
				if (ruta == null || ruta.isEmpty() || !new File(ResourcesManager.getAbsPathFromProject(ruta)).exists()) {
					toDelete.add(id);
				}
			}
			if (!toDelete.isEmpty()) {
				try (PreparedStatement del = conn.prepareStatement("DELETE FROM CANCIONES_PERSONALIZADAS WHERE id_cancion = ?")) {
					for (long id : toDelete) {
						del.setLong(1, id);
						del.addBatch();
					}
					del.executeBatch();
				}
			}
		} catch (SQLException e) {
			System.err.println("Error limpiando canciones custom faltantes: " + e.getMessage());
		}
	}

	/**
	 * Parsea las cabeceras de un archivo {@code .osu} y realiza {@code INSERT} o {@code UPDATE}
	 * en {@code CANCIONES_PERSONALIZADAS} usando {@code clave_unica = ruta_chart} relativa.
	 *
	 * <p>Validado por los triggers {@code trg_canciones_validar_insert} y {@code trg_canciones_validar_update}.
	 */
	public static CustomSongSummary registerOrUpdateOsuFile(File osuFile) {
		if (osuFile == null || !osuFile.exists() || !osuFile.isFile()) return null;

		OsuManiaReader reader = new OsuManiaReader(osuFile.getAbsolutePath(), false);

		String relChart = DatabaseManager.toRelativeProjectPath(osuFile.getAbsolutePath());
		String title = !reader.getTitle().isEmpty()
			? reader.getTitle()
			: stripExt(osuFile.getName());
		String artist = reader.getArtist();
		String diff = !reader.getDifficulty().isEmpty() ? reader.getDifficulty() : "6K";
		float bpm = Math.max(1.0f, reader.getPrimaryBpm());
		int noteCount = reader.getNoteCount();

		String relAudio = "";
		if (!reader.getAudioAbsPath().isEmpty()) {
			relAudio = DatabaseManager.toRelativeProjectPath(reader.getAudioAbsPath());
		} else {
			File fallbackWav = findSiblingFile(osuFile.getParentFile(), "audio.wav", "song.wav");
			if (fallbackWav != null) {
				relAudio = DatabaseManager.toRelativeProjectPath(fallbackWav.getAbsolutePath());
			}
		}

		String relCover = "";
		if (!reader.getCoverAbsPath().isEmpty()) {
			relCover = DatabaseManager.toRelativeProjectPath(reader.getCoverAbsPath());
		} else {
			File fallbackCover = findSiblingFile(
				osuFile.getParentFile(),
				"cover.jpg", "cover.png", "bg.jpg", "bg.png", "portada.png", "portada.jpg"
			);
			if (fallbackCover != null) {
				relCover = DatabaseManager.toRelativeProjectPath(fallbackCover.getAbsolutePath());
			}
		}

		try (Connection conn = DatabaseManager.getConnection()) {
			try (PreparedStatement ps = conn.prepareStatement(
				"SELECT id_cancion FROM CANCIONES_PERSONALIZADAS WHERE clave_unica = ?")) {
				ps.setString(1, relChart);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						long existingId = rs.getLong(1);
						try (PreparedStatement upd = conn.prepareStatement(
							"UPDATE CANCIONES_PERSONALIZADAS SET " +
							"titulo = ?, artista = ?, dificultad = ?, bpm = ?, " +
							"ruta_portada = ?, ruta_audio = ?, ruta_chart = ?, cantidad_notas = ? " +
							"WHERE id_cancion = ?")) {
							upd.setString(1, title);
							upd.setString(2, artist);
							upd.setString(3, diff);
							upd.setFloat(4, bpm);
							upd.setString(5, relCover);
							upd.setString(6, relAudio);
							upd.setString(7, relChart);
							upd.setInt(8, noteCount);
							upd.setLong(9, existingId);
							upd.executeUpdate();
						}
						return getById(existingId);
					}
				}
			}

			try (PreparedStatement ins = conn.prepareStatement(
				"INSERT INTO CANCIONES_PERSONALIZADAS " +
				"(clave_unica, titulo, artista, dificultad, bpm, ruta_portada, ruta_audio, ruta_chart, cantidad_notas) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				Statement.RETURN_GENERATED_KEYS)) {
				ins.setString(1, relChart);
				ins.setString(2, title);
				ins.setString(3, artist);
				ins.setString(4, diff);
				ins.setFloat(5, bpm);
				ins.setString(6, relCover);
				ins.setString(7, relAudio);
				ins.setString(8, relChart);
				ins.setInt(9, noteCount);
				ins.executeUpdate();
				try (ResultSet keys = ins.getGeneratedKeys()) {
					if (keys.next()) {
						return getById(keys.getLong(1));
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("Error registrando canción custom en SQLite: " + e.getMessage());
		}
		return null;
	}

	/**
	 * Importa un archivo {@code .osu} externo copiándolo (junto con su audio y portada locales)
	 * dentro de {@code resources/songs/custom/<slug>/} para garantizar rutas relativas portables.
	 */
	public static CustomSongSummary importExternalOsu(File sourceOsu) throws IOException {
		if (sourceOsu == null || !sourceOsu.exists() || !sourceOsu.isFile()) {
			throw new IOException("Archivo .osu inexistente.");
		}

		Path projRoot = Path.of(ResourcesManager.getProjectPath()).toAbsolutePath().normalize();
		Path customRoot = Path.of(ResourcesManager.getAbsPathFromProject(CUSTOM_SONGS_DIR)).toAbsolutePath().normalize();
		Path srcAbs = sourceOsu.toPath().toAbsolutePath().normalize();

		if (srcAbs.startsWith(customRoot)) {
			return registerOrUpdateOsuFile(sourceOsu);
		}

		OsuManiaReader reader = new OsuManiaReader(sourceOsu.getAbsolutePath(), false);
		String baseSlug = stripExt(sourceOsu.getName())
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9_]+", "_");
		if (baseSlug.isEmpty()) baseSlug = "custom_song";

		Path targetDir = customRoot.resolve(baseSlug);
		Files.createDirectories(targetDir);

		Path targetOsu = targetDir.resolve(sourceOsu.getName());
		Files.copy(srcAbs, targetOsu, StandardCopyOption.REPLACE_EXISTING);

		if (!reader.getAudioAbsPath().isEmpty()) {
			File audioSrc = new File(reader.getAudioAbsPath());
			if (audioSrc.exists() && audioSrc.isFile()) {
				String audioName = !reader.getAudioFilename().isEmpty()
					? new File(reader.getAudioFilename()).getName()
					: audioSrc.getName();
				Files.copy(audioSrc.toPath(), targetDir.resolve(audioName), StandardCopyOption.REPLACE_EXISTING);
			}
		}

		if (!reader.getCoverAbsPath().isEmpty()) {
			File coverSrc = new File(reader.getCoverAbsPath());
			if (coverSrc.exists() && coverSrc.isFile()) {
				Files.copy(coverSrc.toPath(), targetDir.resolve(coverSrc.getName()), StandardCopyOption.REPLACE_EXISTING);
			}
		} else {
			File siblingCover = findSiblingFile(
				sourceOsu.getParentFile(),
				"cover.jpg", "cover.png", "bg.jpg", "bg.png"
			);
			if (siblingCover != null) {
				Files.copy(siblingCover.toPath(), targetDir.resolve(siblingCover.getName()), StandardCopyOption.REPLACE_EXISTING);
			}
		}

		if (projRoot != null && !targetOsu.startsWith(projRoot)) {
			throw new IOException("Ruta destino fuera del proyecto.");
		}
		return registerOrUpdateOsuFile(targetOsu.toFile());
	}

	// =========================================================================
	// Consultas sobre la Vista v_estadisticas_canciones_custom y Registro de Intentos
	// =========================================================================

	/**
	 * Lista todas las canciones personalizadas consultando la vista {@code v_estadisticas_canciones_custom}
	 * (que consolida {@code CANCIONES_PERSONALIZADAS}, {@code CANCION_OBJETOS_INTENTO} y {@code CATALOGO_ITEMS}).
	 */
	public static List<CustomSongSummary> listAll() {
		List<CustomSongSummary> list = new ArrayList<>();
		String sql = "SELECT * FROM v_estadisticas_canciones_custom";
		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(mapSummaryRow(rs));
			}
		} catch (SQLException e) {
			System.err.println("Error consultando v_estadisticas_canciones_custom: " + e.getMessage());
		}
		return list;
	}

	/**
	 * Obtiene el resumen y récords de una canción personalizada por su {@code id_cancion}
	 * consultando {@code v_estadisticas_canciones_custom}.
	 */
	public static CustomSongSummary getById(long idCancion) {
		String sql = "SELECT * FROM v_estadisticas_canciones_custom WHERE id_cancion = ?";
		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setLong(1, idCancion);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return mapSummaryRow(rs);
				}
			}
		} catch (SQLException e) {
			System.err.println("Error consultando canción custom #" + idCancion + ": " + e.getMessage());
		}
		return null;
	}

	/**
	 * Registra el último intento en {@code CANCIONES_PERSONALIZADAS} y sus objetos equipados en
	 * {@code CANCION_OBJETOS_INTENTO} ({@code tipo_registro = 'ULTIMO'}).
	 *
	 * <p>Al ejecutar el {@code UPDATE} sobre {@code CANCIONES_PERSONALIZADAS}, el trigger SQLite
	 * {@code trg_canciones_record_after_update} verifica si {@code puntaje_ultimo_intento > puntaje_mas_alto};
	 * de ser así, promueve automáticamente el nuevo récord y copia los ítems de {@code 'ULTIMO'} a {@code 'MEJOR'}
	 * en {@code CANCION_OBJETOS_INTENTO}.
	 */
	public static void recordAttempt(
		long idCancion,
		int score,
		int maxCombo,
		float accuracyPercent,
		List<String> equippedItemIds
	) {
		if (idCancion <= 0) return;

		Set<String> cleanItems = new LinkedHashSet<>();
		if (equippedItemIds != null) {
			for (String id : equippedItemIds) {
				if (id != null && !id.isEmpty() && !"none".equals(id)) {
					cleanItems.add(id);
				}
			}
		}

		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				try (PreparedStatement delLast = conn.prepareStatement(
					"DELETE FROM CANCION_OBJETOS_INTENTO WHERE id_cancion = ? AND tipo_registro = 'ULTIMO'")) {
					delLast.setLong(1, idCancion);
					delLast.executeUpdate();
				}

				if (!cleanItems.isEmpty()) {
					try (PreparedStatement insItem = conn.prepareStatement(
						"INSERT OR IGNORE INTO CANCION_OBJETOS_INTENTO (id_cancion, tipo_registro, id_item) VALUES (?, 'ULTIMO', ?)")) {
						for (String itemId : cleanItems) {
							DatabaseManager.ensureItemRegistered(conn, GameState.getOrRegisterItemDef(itemId));
							insItem.setLong(1, idCancion);
							insItem.setString(2, itemId);
							insItem.addBatch();
						}
						insItem.executeBatch();
					}
				}

				try (PreparedStatement updSong = conn.prepareStatement(
					"UPDATE CANCIONES_PERSONALIZADAS SET " +
					"puntaje_ultimo_intento = ?, " +
					"combo_ultimo_intento = ?, " +
					"precision_ultimo_intento = ?, " +
					"veces_jugada = veces_jugada + 1, " +
					"fecha_ultimo_intento = CURRENT_TIMESTAMP " +
					"WHERE id_cancion = ?")) {
					updSong.setInt(1, Math.max(0, score));
					updSong.setInt(2, Math.max(0, maxCombo));
					updSong.setFloat(3, Math.max(0.0f, Math.min(100.0f, accuracyPercent)));
					updSong.setLong(4, idCancion);
					updSong.executeUpdate();
				}

				conn.commit();
			} catch (Exception e) {
				conn.rollback();
				throw e;
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			System.err.println("Error registrando intento de canción custom: " + e.getMessage());
		}
	}

	// =========================================================================
	// Mapeo de Filas y Utilidades Internas de Archivos
	// =========================================================================

	private static CustomSongSummary mapSummaryRow(ResultSet rs) throws SQLException {
		CustomSongSummary s = new CustomSongSummary();
		s.idCancion = rs.getLong("id_cancion");
		s.claveUnica = safeStr(rs.getString("clave_unica"));
		s.titulo = safeStr(rs.getString("titulo"));
		s.artista = safeStr(rs.getString("artista"));
		s.dificultad = safeStr(rs.getString("dificultad"));
		s.bpm = rs.getFloat("bpm");
		s.rutaPortada = safeStr(rs.getString("ruta_portada"));
		s.rutaAudio = safeStr(rs.getString("ruta_audio"));
		s.rutaChart = safeStr(rs.getString("ruta_chart"));
		s.cantidadNotas = rs.getInt("cantidad_notas");

		s.puntajeUltimoIntento = rs.getInt("puntaje_ultimo_intento");
		s.comboUltimoIntento = rs.getInt("combo_ultimo_intento");
		s.precisionUltimoIntento = rs.getFloat("precision_ultimo_intento");

		s.puntajeMasAlto = rs.getInt("puntaje_mas_alto");
		s.comboMasAlto = rs.getInt("combo_mas_alto");
		s.precisionMasAlta = rs.getFloat("precision_mas_alta");

		s.vecesJugada = rs.getInt("veces_jugada");
		s.fechaUltimoIntento = safeStr(rs.getString("fecha_ultimo_intento"));
		s.fechaRecord = safeStr(rs.getString("fecha_record"));
		s.objetosRecord = safeStr(rs.getString("objetos_record"));
		s.objetosUltimoIntento = safeStr(rs.getString("objetos_ultimo_intento"));
		return s;
	}

	private static File findSiblingFile(File dir, String... candidates) {
		if (dir == null || !dir.isDirectory()) return null;
		for (String name : candidates) {
			File f = new File(dir, name);
			if (f.exists() && f.isFile()) return f;
		}
		return null;
	}

	private static String stripExt(String name) {
		if (name == null) return "";
		int dot = name.lastIndexOf('.');
		return (dot > 0) ? name.substring(0, dot) : name;
	}

	private static String safeStr(String s) {
		return s != null ? s : "";
	}
}
