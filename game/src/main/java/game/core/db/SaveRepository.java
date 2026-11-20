package game.core.db;

import game.core.GameState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Repositorio SQLite para la persistencia de partidas, récords de canciones, tienda de amuletos e inventario en 3FN.
 *
 * <p>Interactúa con las tablas {@code PARTIDAS}, {@code RECORDS_CANCIONES}, {@code CATALOGO_ITEMS},
 * {@code OBJETOS_OBTENIDOS}, {@code HISTORIAL_OBJETOS} y {@code FLAGS_PARTIDA}, así como con las vistas
 * {@code v_resumen_partidas}, {@code v_records_canciones}, {@code v_inventario_detallado}
 * y {@code v_historial_objetos}.
 */
public class SaveRepository {

	// =========================================================================
	// DTOs de Lectura de Partidas e Historial de Objetos
	// =========================================================================

	public static class LoadedSaveData {
		public int slot;
		public int shopPoints = 0;
		public GameState.HealStrategy healStrategy = GameState.HealStrategy.OPTIMAL;
		public final Set<String> flags = new LinkedHashSet<>();
		public final Map<String, Integer> inventory = new LinkedHashMap<>();
		public final Set<String> equippedPowerUps = new LinkedHashSet<>();
	}

	public static class ItemHistoryView {
		public String itemName;
		public String category;
		public String eventType;
		public int delta;
		public String eventDate;
	}

	// =========================================================================
	// Consultas de Slots y Vistas de Resumen (v_resumen_partidas, RECORDS_CANCIONES, v_historial_objetos)
	// =========================================================================

	/**
	 * Convierte el índice de slot base 0 usado en memoria ({@code 0..3}) al slot 3FN ({@code 1..4} en {@code PARTIDAS.slot}).
	 */
	private static int toDbSlot(int zeroBasedSlot) {
		return GameState.clampSaveSlot(zeroBasedSlot) + 1;
	}

	/**
	 * Verifica si existe una partida registrada en {@code PARTIDAS} para el slot indicado.
	 */
	public static boolean hasSlot(int zeroBasedSlot) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM PARTIDAS WHERE slot = ?")) {
			ps.setInt(1, dbSlot);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (SQLException e) {
			System.err.println("Error consultando slot en SQLite: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Cuenta el total de filas activas en la tabla {@code PARTIDAS}.
	 */
	public static int countUsedSlots() {
		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM PARTIDAS");
			 ResultSet rs = ps.executeQuery()) {
			return rs.next() ? rs.getInt(1) : 0;
		} catch (SQLException e) {
			System.err.println("Error contando partidas en SQLite: " + e.getMessage());
			return 0;
		}
	}

	/**
	 * Obtiene el resumen de un slot consultando la vista {@code v_resumen_partidas}
	 * (que agrega {@code PARTIDAS}, {@code OBJETOS_OBTENIDOS}, {@code CATALOGO_ITEMS} y {@code RECORDS_CANCIONES}).
	 */
	public static GameState.SaveSlotSummary getSlotSummary(int zeroBasedSlot) {
		GameState.SaveSlotSummary summary = new GameState.SaveSlotSummary();
		int dbSlot = toDbSlot(zeroBasedSlot);
		String sql =
			"SELECT puntos_tienda, total_items, total_amuletos, canciones_jugadas, " +
			"canciones_completadas, fecha_actualizacion " +
			"FROM v_resumen_partidas WHERE slot = ?";

		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, dbSlot);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					summary.exists = true;
					summary.shopPoints = rs.getInt("puntos_tienda");
					summary.itemCount = rs.getInt("total_items");
					summary.amuletCount = rs.getInt("total_amuletos");
					summary.songsPlayedCount = rs.getInt("canciones_jugadas");
					summary.songsClearedCount = rs.getInt("canciones_completadas");
					summary.lastUpdated = rs.getString("fecha_actualizacion");
				}
			}
		} catch (SQLException e) {
			System.err.println("Error leyendo v_resumen_partidas: " + e.getMessage());
		}
		return summary;
	}

	/**
	 * Busca el mejor puntaje y combo registrados en {@code RECORDS_CANCIONES} para una canción dentro del slot.
	 * Retorna {@code { puntajeMaximo, maxCombo }} o {@code null} si no existe registro en BD.
	 */
	public static int[] getBestSongRecord(int zeroBasedSlot, String trackId, String trackTitle) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		String cleanKey = DatabaseManager.toRelativeProjectPath(trackId);
		if (cleanKey.isEmpty() && trackId != null) {
			cleanKey = trackId.trim();
		}
		String cleanTitle = (trackTitle != null) ? trackTitle.trim() : "";

		String sql =
			"SELECT rc.puntaje_maximo, rc.max_combo " +
			"FROM RECORDS_CANCIONES rc " +
			"INNER JOIN PARTIDAS p ON p.id_partida = rc.id_partida " +
			"WHERE p.slot = ? AND (rc.clave_cancion = ? OR rc.titulo = ?) " +
			"ORDER BY rc.puntaje_maximo DESC " +
			"LIMIT 1";

		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, dbSlot);
			ps.setString(2, cleanKey);
			ps.setString(3, cleanTitle);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return new int[] { rs.getInt("puntaje_maximo"), rs.getInt("max_combo") };
				}
			}
		} catch (SQLException e) {
			System.err.println("Error leyendo mejor puntaje de canción en RECORDS_CANCIONES: " + e.getMessage());
		}
		return null;
	}

	/**
	 * Obtiene los últimos eventos de auditoría de inventario del slot desde la vista {@code v_historial_objetos}
	 * (alimentada automáticamente por los triggers {@code trg_objetos_audit_insert} y {@code trg_objetos_audit_update}).
	 */
	public static List<ItemHistoryView> getItemHistoryForSlot(int zeroBasedSlot, int limit) {
		List<ItemHistoryView> list = new ArrayList<>();
		int dbSlot = toDbSlot(zeroBasedSlot);
		String sql =
			"SELECT item_nombre, categoria, evento, cantidad_delta, fecha_evento " +
			"FROM v_historial_objetos WHERE slot_partida = ? LIMIT ?";

		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, dbSlot);
			ps.setInt(2, Math.max(1, limit));
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					ItemHistoryView row = new ItemHistoryView();
					row.itemName = rs.getString("item_nombre");
					row.category = rs.getString("categoria");
					row.eventType = rs.getString("evento");
					row.delta = rs.getInt("cantidad_delta");
					row.eventDate = rs.getString("fecha_evento");
					list.add(row);
				}
			}
		} catch (SQLException e) {
			System.err.println("Error leyendo v_historial_objetos: " + e.getMessage());
		}
		return list;
	}

	// =========================================================================
	// Creación, Eliminación, Guardado y Carga de Partidas
	// =========================================================================

	/**
	 * Elimina una partida de {@code PARTIDAS}. Gracias a {@code ON DELETE CASCADE}, limpia automáticamente
	 * sus registros en {@code RECORDS_CANCIONES}, {@code OBJETOS_OBTENIDOS}, {@code HISTORIAL_OBJETOS} y {@code FLAGS_PARTIDA}.
	 */
	public static boolean deleteSlot(int zeroBasedSlot) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement("DELETE FROM PARTIDAS WHERE slot = ?")) {
			ps.setInt(1, dbSlot);
			ps.executeUpdate();
			return true;
		} catch (SQLException e) {
			System.err.println("Error borrando partida en SQLite: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Reinicia el slot en {@code PARTIDAS} e inserta el inventario inicial en {@code OBJETOS_OBTENIDOS}
	 * (disparando {@code trg_objetos_validar_insert} y {@code trg_objetos_audit_insert} hacia {@code HISTORIAL_OBJETOS}).
	 */
	public static void createNewGame(int zeroBasedSlot, Map<String, Integer> initialInventory) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				try (PreparedStatement del = conn.prepareStatement("DELETE FROM PARTIDAS WHERE slot = ?")) {
					del.setInt(1, dbSlot);
					del.executeUpdate();
				}

				long partidaId;
				try (PreparedStatement ins = conn.prepareStatement(
					"INSERT INTO PARTIDAS (slot, puntos_tienda, heal_strategy) VALUES (?, 0, 'OPTIMAL')",
					Statement.RETURN_GENERATED_KEYS)) {
					ins.setInt(1, dbSlot);
					ins.executeUpdate();
					try (ResultSet keys = ins.getGeneratedKeys()) {
						if (!keys.next()) throw new SQLException("No se generó id_partida");
						partidaId = keys.getLong(1);
					}
				}

				if (initialInventory != null && !initialInventory.isEmpty()) {
					try (PreparedStatement insObj = conn.prepareStatement(
						"INSERT INTO OBJETOS_OBTENIDOS (id_partida, id_item, cantidad, equipado) VALUES (?, ?, ?, 0)")) {
						for (Map.Entry<String, Integer> entry : initialInventory.entrySet()) {
							if (entry.getValue() == null || entry.getValue() <= 0) continue;
							DatabaseManager.ensureItemRegistered(conn, GameState.getOrRegisterItemDef(entry.getKey()));
							insObj.setLong(1, partidaId);
							insObj.setString(2, entry.getKey());
							insObj.setInt(3, entry.getValue());
							insObj.addBatch();
						}
						insObj.executeBatch();
					}
				}
				conn.commit();
			} catch (Exception e) {
				conn.rollback();
				throw e;
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			System.err.println("Error creando nueva partida en SQLite: " + e.getMessage());
		}
	}

	/**
	 * Persiste el estado completo del jugador en una transacción atómica:
	 * <ol>
	 *   <li>Inserta o actualiza la fila principal en {@code PARTIDAS} ({@code puntos_tienda}, {@code heal_strategy}).</li>
	 *   <li>Sincroniza diferencialmente {@code FLAGS_PARTIDA}.</li>
	 *   <li>Sincroniza por diferencias {@code OBJETOS_OBTENIDOS} para que los triggers {@code trg_objetos_audit_insert}
	 *       y {@code trg_objetos_audit_update} registren únicamente cambios reales en {@code HISTORIAL_OBJETOS}
	 *       (y auto-eliminen filas cuya {@code cantidad} baje a {@code 0}).</li>
	 * </ol>
	 */
	public static void saveState(
		int zeroBasedSlot,
		int shopPoints,
		GameState.HealStrategy healStrategy,
		Set<String> flags,
		Map<String, Integer> inventory,
		Set<String> equippedPowerUps
	) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				int cleanPoints = Math.max(0, shopPoints);
				String healStrat = (healStrategy != null) ? healStrategy.name() : "OPTIMAL";

				long partidaId = findPartidaId(conn, dbSlot);
				if (partidaId <= 0) {
					try (PreparedStatement ins = conn.prepareStatement(
						"INSERT INTO PARTIDAS (slot, puntos_tienda, heal_strategy) VALUES (?, ?, ?)",
						Statement.RETURN_GENERATED_KEYS)) {
						ins.setInt(1, dbSlot);
						ins.setInt(2, cleanPoints);
						ins.setString(3, healStrat);
						ins.executeUpdate();
						try (ResultSet keys = ins.getGeneratedKeys()) {
							if (!keys.next()) throw new SQLException("No se generó id_partida");
							partidaId = keys.getLong(1);
						}
					}
				} else {
					try (PreparedStatement upd = conn.prepareStatement(
						"UPDATE PARTIDAS SET puntos_tienda = ?, heal_strategy = ?, " +
						"fecha_actualizacion = CURRENT_TIMESTAMP WHERE id_partida = ?")) {
						upd.setInt(1, cleanPoints);
						upd.setString(2, healStrat);
						upd.setLong(3, partidaId);
						upd.executeUpdate();
					}
				}

				// Sincronizar flags de la partida sin borrar los existentes que no cambiaron
				Set<String> existingFlags = new LinkedHashSet<>();
				try (PreparedStatement ps = conn.prepareStatement(
					"SELECT flag_nombre FROM FLAGS_PARTIDA WHERE id_partida = ?")) {
					ps.setLong(1, partidaId);
					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) existingFlags.add(rs.getString(1));
					}
				}
				Set<String> safeFlags = (flags != null) ? flags : Set.of();
				try (PreparedStatement delFlag = conn.prepareStatement(
					"DELETE FROM FLAGS_PARTIDA WHERE id_partida = ? AND flag_nombre = ?")) {
					for (String oldFlag : existingFlags) {
						if (!safeFlags.contains(oldFlag)) {
							delFlag.setLong(1, partidaId);
							delFlag.setString(2, oldFlag);
							delFlag.addBatch();
						}
					}
					delFlag.executeBatch();
				}
				try (PreparedStatement insFlag = conn.prepareStatement(
					"INSERT OR IGNORE INTO FLAGS_PARTIDA (id_partida, flag_nombre) VALUES (?, ?)")) {
					for (String newFlag : safeFlags) {
						if (newFlag != null && !newFlag.isEmpty() && !existingFlags.contains(newFlag)) {
							insFlag.setLong(1, partidaId);
							insFlag.setString(2, newFlag);
							insFlag.addBatch();
						}
					}
					insFlag.executeBatch();
				}

				// Sincronizar inventario por diferencias para que los triggers de auditoría solo registren cambios reales
				Map<String, int[]> dbItems = new HashMap<>(); // id_item -> [cantidad, equipado]
				try (PreparedStatement ps = conn.prepareStatement(
					"SELECT id_item, cantidad, equipado FROM OBJETOS_OBTENIDOS WHERE id_partida = ?")) {
					ps.setLong(1, partidaId);
					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) {
							dbItems.put(rs.getString("id_item"), new int[] {
								rs.getInt("cantidad"),
								rs.getInt("equipado")
							});
						}
					}
				}

				Map<String, Integer> safeInv = (inventory != null) ? inventory : Map.of();
				Set<String> safeEq = (equippedPowerUps != null) ? equippedPowerUps : Set.of();

				try (PreparedStatement updZero = conn.prepareStatement(
					"UPDATE OBJETOS_OBTENIDOS SET cantidad = 0, equipado = 0 WHERE id_partida = ? AND id_item = ?")) {
					for (String dbItemId : dbItems.keySet()) {
						int newCount = safeInv.getOrDefault(dbItemId, 0);
						if (newCount <= 0) {
							updZero.setLong(1, partidaId);
							updZero.setString(2, dbItemId);
							updZero.addBatch();
						}
					}
					updZero.executeBatch();
				}

				for (Map.Entry<String, Integer> entry : safeInv.entrySet()) {
					String itemId = entry.getKey();
					int count = (entry.getValue() != null) ? entry.getValue() : 0;
					if (itemId == null || itemId.isEmpty() || count <= 0) continue;

					int eq = safeEq.contains(itemId) ? 1 : 0;
					DatabaseManager.ensureItemRegistered(conn, GameState.getOrRegisterItemDef(itemId));

					int[] prev = dbItems.get(itemId);
					if (prev == null) {
						try (PreparedStatement ins = conn.prepareStatement(
							"INSERT INTO OBJETOS_OBTENIDOS (id_partida, id_item, cantidad, equipado) VALUES (?, ?, ?, ?)")) {
							ins.setLong(1, partidaId);
							ins.setString(2, itemId);
							ins.setInt(3, count);
							ins.setInt(4, eq);
							ins.executeUpdate();
						}
					} else if (prev[0] != count || prev[1] != eq) {
						try (PreparedStatement upd = conn.prepareStatement(
							"UPDATE OBJETOS_OBTENIDOS SET cantidad = ?, equipado = ? WHERE id_partida = ? AND id_item = ?")) {
							upd.setInt(1, count);
							upd.setInt(2, eq);
							upd.setLong(3, partidaId);
							upd.setString(4, itemId);
							upd.executeUpdate();
						}
					}
				}

				conn.commit();
			} catch (Exception e) {
				conn.rollback();
				throw e;
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			System.err.println("Error guardando partida en SQLite: " + e.getMessage());
		}
	}

	/**
	 * Carga el estado completo de una partida leyendo las vistas {@code v_resumen_partidas} y
	 * {@code v_inventario_detallado}, junto con la tabla {@code FLAGS_PARTIDA}.
	 */
	public static LoadedSaveData loadState(int zeroBasedSlot) {
		int clampedSlot = GameState.clampSaveSlot(zeroBasedSlot);
		int dbSlot = toDbSlot(clampedSlot);
		String sql =
			"SELECT id_partida, puntos_tienda, heal_strategy " +
			"FROM v_resumen_partidas WHERE slot = ?";

		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, dbSlot);
			LoadedSaveData data = null;
			long partidaId = -1L;
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) return null;
				partidaId = rs.getLong("id_partida");
				data = new LoadedSaveData();
				data.slot = clampedSlot;
				data.shopPoints = Math.max(0, rs.getInt("puntos_tienda"));
				try {
					data.healStrategy = GameState.HealStrategy.valueOf(rs.getString("heal_strategy"));
				} catch (Exception ignored) {
					data.healStrategy = GameState.HealStrategy.OPTIMAL;
				}
			}

			try (PreparedStatement fp = conn.prepareStatement(
				"SELECT flag_nombre FROM FLAGS_PARTIDA WHERE id_partida = ?")) {
				fp.setLong(1, partidaId);
				try (ResultSet rs = fp.executeQuery()) {
					while (rs.next()) {
						data.flags.add(rs.getString(1));
					}
				}
			}

			try (PreparedStatement ip = conn.prepareStatement(
				"SELECT id_item, item_nombre, categoria, curacion_hp, precio_tienda, descripcion, cantidad, equipado " +
				"FROM v_inventario_detallado WHERE id_partida = ?")) {
				ip.setLong(1, partidaId);
				try (ResultSet rs = ip.executeQuery()) {
					while (rs.next()) {
						String itemId = rs.getString("id_item");
						int count = rs.getInt("cantidad");
						boolean eq = rs.getInt("equipado") == 1;
						if (count > 0) {
							GameState.ItemCategory cat = GameState.ItemCategory.MISC;
							try {
								cat = GameState.ItemCategory.valueOf(rs.getString("categoria"));
							} catch (Exception ignored) {}
							GameState.registerItemIfAbsent(new GameState.ItemDef(
								itemId,
								rs.getString("item_nombre"),
								cat,
								rs.getInt("curacion_hp"),
								rs.getInt("precio_tienda"),
								rs.getString("descripcion")
							));
							data.inventory.put(itemId, count);
							if (eq) {
								data.equippedPowerUps.add(itemId);
							}
						}
					}
				}
			}
			return data;
		} catch (SQLException e) {
			System.err.println("Error cargando partida desde SQLite: " + e.getMessage());
			return null;
		}
	}

	// =========================================================================
	// Registro de Intentos y Récords de Canciones (RECORDS_CANCIONES y Triggers)
	// =========================================================================

	/**
	 * Registra o actualiza un intento de canción en {@code RECORDS_CANCIONES} para el slot indicado.
	 *
	 * <p>Triggers disparados automáticamente en SQLite:
	 * <ul>
	 *   <li>{@code trg_records_validar_insert} / {@code trg_records_validar_update}: valida puntajes, combos,
	 *       contadores y que {@code clave_cancion} sea una ruta relativa.</li>
	 *   <li>{@code trg_records_touch_partida_insert} / {@code trg_records_touch_partida_update}: actualiza
	 *       {@code PARTIDAS.fecha_actualizacion}.</li>
	 * </ul>
	 */
	public static void recordSongAttempt(
		int zeroBasedSlot,
		String songKey,
		String title,
		String artist,
		String difficulty,
		int score,
		int maxCombo,
		int perfects,
		int goods,
		int safes,
		int misses,
		boolean won
	) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		String safeTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "Cancion";
		String safeArtist = (artist != null) ? artist.trim() : "";
		String safeDiff = (difficulty != null && !difficulty.trim().isEmpty()) ? difficulty.trim() : "6K";

		String cleanKey = DatabaseManager.toRelativeProjectPath(songKey);
		if (cleanKey.isEmpty()) {
			cleanKey = "songs/" + safeTitle.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
		}
		while (cleanKey.startsWith("/")) {
			cleanKey = cleanKey.substring(1);
		}
		if (cleanKey.matches("^[A-Za-z]:.*")) {
			cleanKey = cleanKey.substring(2);
			while (cleanKey.startsWith("/")) {
				cleanKey = cleanKey.substring(1);
			}
		}
		if (cleanKey.isEmpty()) {
			cleanKey = "songs/track";
		}

		int cleanScore = Math.max(0, score);
		int cleanCombo = Math.max(0, maxCombo);
		int cleanPerfects = Math.max(0, perfects);
		int cleanGoods = Math.max(0, goods);
		int cleanSafes = Math.max(0, safes);
		int cleanMisses = Math.max(0, misses);
		int winDelta = won ? 1 : 0;

		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				long partidaId = findPartidaId(conn, dbSlot);
				if (partidaId <= 0) {
					try (PreparedStatement insPartida = conn.prepareStatement(
						"INSERT INTO PARTIDAS (slot, puntos_tienda, heal_strategy) VALUES (?, 0, 'OPTIMAL')",
						Statement.RETURN_GENERATED_KEYS)) {
						insPartida.setInt(1, dbSlot);
						insPartida.executeUpdate();
						try (ResultSet keys = insPartida.getGeneratedKeys()) {
							if (!keys.next()) throw new SQLException("No se generó id_partida");
							partidaId = keys.getLong(1);
						}
					}
				}

				boolean exists = false;
				int prevMaxScore = 0;
				int prevMaxCombo = 0;
				try (PreparedStatement chk = conn.prepareStatement(
					"SELECT puntaje_maximo, max_combo FROM RECORDS_CANCIONES WHERE id_partida = ? AND clave_cancion = ?")) {
					chk.setLong(1, partidaId);
					chk.setString(2, cleanKey);
					try (ResultSet rs = chk.executeQuery()) {
						if (rs.next()) {
							exists = true;
							prevMaxScore = rs.getInt("puntaje_maximo");
							prevMaxCombo = rs.getInt("max_combo");
						}
					}
				}

				if (!exists) {
					try (PreparedStatement ins = conn.prepareStatement(
						"INSERT INTO RECORDS_CANCIONES " +
						"(id_partida, clave_cancion, titulo, artista, dificultad, puntaje_maximo, max_combo, " +
						"perfects, goods, safes, misses, intentos_totales, victorias_totales) " +
						"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)")) {
						ins.setLong(1, partidaId);
						ins.setString(2, cleanKey);
						ins.setString(3, safeTitle);
						ins.setString(4, safeArtist);
						ins.setString(5, safeDiff);
						ins.setInt(6, cleanScore);
						ins.setInt(7, cleanCombo);
						ins.setInt(8, cleanPerfects);
						ins.setInt(9, cleanGoods);
						ins.setInt(10, cleanSafes);
						ins.setInt(11, cleanMisses);
						ins.setInt(12, winDelta);
						ins.executeUpdate();
					}
				} else {
					boolean isNewBestScore = cleanScore >= prevMaxScore;
					int nextMaxCombo = Math.max(prevMaxCombo, cleanCombo);
					if (isNewBestScore) {
						try (PreparedStatement upd = conn.prepareStatement(
							"UPDATE RECORDS_CANCIONES SET " +
							"titulo = ?, artista = ?, dificultad = ?, " +
							"puntaje_maximo = ?, max_combo = ?, " +
							"perfects = ?, goods = ?, safes = ?, misses = ?, " +
							"intentos_totales = intentos_totales + 1, " +
							"victorias_totales = victorias_totales + ?, " +
							"fecha_ultimo_intento = CURRENT_TIMESTAMP " +
							"WHERE id_partida = ? AND clave_cancion = ?")) {
							upd.setString(1, safeTitle);
							upd.setString(2, safeArtist);
							upd.setString(3, safeDiff);
							upd.setInt(4, cleanScore);
							upd.setInt(5, nextMaxCombo);
							upd.setInt(6, cleanPerfects);
							upd.setInt(7, cleanGoods);
							upd.setInt(8, cleanSafes);
							upd.setInt(9, cleanMisses);
							upd.setInt(10, winDelta);
							upd.setLong(11, partidaId);
							upd.setString(12, cleanKey);
							upd.executeUpdate();
						}
					} else {
						try (PreparedStatement upd = conn.prepareStatement(
							"UPDATE RECORDS_CANCIONES SET " +
							"titulo = ?, artista = ?, dificultad = ?, " +
							"max_combo = ?, " +
							"intentos_totales = intentos_totales + 1, " +
							"victorias_totales = victorias_totales + ?, " +
							"fecha_ultimo_intento = CURRENT_TIMESTAMP " +
							"WHERE id_partida = ? AND clave_cancion = ?")) {
							upd.setString(1, safeTitle);
							upd.setString(2, safeArtist);
							upd.setString(3, safeDiff);
							upd.setInt(4, nextMaxCombo);
							upd.setInt(5, winDelta);
							upd.setLong(6, partidaId);
							upd.setString(7, cleanKey);
							upd.executeUpdate();
						}
					}
				}

				conn.commit();
			} catch (Exception e) {
				conn.rollback();
				throw e;
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			System.err.println("Error registrando intento de canción en SQLite: " + e.getMessage());
		}
	}

	private static long findPartidaId(Connection conn, int dbSlot) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("SELECT id_partida FROM PARTIDAS WHERE slot = ?")) {
			ps.setInt(1, dbSlot);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getLong(1) : -1L;
			}
		}
	}
}
