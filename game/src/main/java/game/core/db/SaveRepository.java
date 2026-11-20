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
import java.util.Map;
import java.util.Set;

/**
 * Repositorio SQLite para la persistencia de partidas e historia principal en 3FN.
 *
 * <p>Interactúa con las tablas {@code PARTIDAS}, {@code ZONAS}, {@code CATALOGO_BATALLAS},
 * {@code BATALLAS_VENCIDAS}, {@code CATALOGO_ITEMS}, {@code OBJETOS_OBTENIDOS},
 * {@code HISTORIAL_OBJETOS} y {@code FLAGS_PARTIDA}, así como con las vistas
 * {@code v_resumen_partidas}, {@code v_mejores_puntajes_batallas}, {@code v_inventario_detallado}
 * y {@code v_historial_objetos}.
 */
public class SaveRepository {

	// =========================================================================
	// DTOs de Lectura de Partidas, Récords de Batalla e Historial de Objetos
	// =========================================================================

	public static class LoadedSaveData {
		public long idPartida;
		public String scenePath;
		public float playerX;
		public float playerY;
		public String returnScenePath;
		public float returnX;
		public float returnY;
		public GameState.HealStrategy healStrategy = GameState.HealStrategy.OPTIMAL;
		public String generalState = "EN_PROGRESO";
		public float progressPercent = 0.0f;
		public final Set<String> flags = new LinkedHashSet<>();
		public final Map<String, Integer> inventory = new LinkedHashMap<>();
		public final Set<String> equippedPowerUps = new LinkedHashSet<>();
	}

	public static class BattleRecordView {
		public String bossName;
		public String species;
		public String zoneName;
		public int maxScore;
		public int maxCombo;
		public int totalAttempts;
	}

	public static class ItemHistoryView {
		public String itemName;
		public String category;
		public String eventType;
		public int delta;
		public String eventDate;
	}

	// =========================================================================
	// Consultas de Slots y Vistas de Resumen (v_resumen_partidas, v_mejores_puntajes_batallas, v_historial_objetos)
	// =========================================================================

	/**
	 * Convierte el índice de slot base 0 usado en memoria ({@code 0..3}) al slot 1FN/3FN ({@code 1..4} en {@code PARTIDAS.slot}).
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
	 * (que agrega {@code PARTIDAS}, {@code ZONAS}, {@code OBJETOS_OBTENIDOS}, {@code CATALOGO_ITEMS} y {@code BATALLAS_VENCIDAS}).
	 */
	public static GameState.SaveSlotSummary getSlotSummary(int zeroBasedSlot) {
		GameState.SaveSlotSummary summary = new GameState.SaveSlotSummary();
		int dbSlot = toDbSlot(zeroBasedSlot);
		String sql =
			"SELECT zona_actual, total_items, total_amuletos, total_batallas_vencidas, " +
			"progreso_porcentaje, estado_general, fecha_actualizacion " +
			"FROM v_resumen_partidas WHERE slot = ?";

		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, dbSlot);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					summary.exists = true;
					summary.sceneLabel = rs.getString("zona_actual");
					summary.itemCount = rs.getInt("total_items");
					summary.amuletCount = rs.getInt("total_amuletos");
					summary.battlesWonCount = rs.getInt("total_batallas_vencidas");
					summary.progressPercent = rs.getFloat("progreso_porcentaje");
					summary.generalStatus = rs.getString("estado_general");
					summary.lastUpdated = rs.getString("fecha_actualizacion");
				}
			}
		} catch (SQLException e) {
			System.err.println("Error leyendo v_resumen_partidas: " + e.getMessage());
		}
		return summary;
	}

	/**
	 * Lista los récords de batallas ganadas en un slot desde la vista {@code v_mejores_puntajes_batallas}.
	 */
	public static List<BattleRecordView> getBattleRecordsForSlot(int zeroBasedSlot) {
		List<BattleRecordView> list = new ArrayList<>();
		int dbSlot = toDbSlot(zeroBasedSlot);
		String sql =
			"SELECT boss_nombre, especie, zona_batalla, puntaje_maximo, max_combo, intentos_totales " +
			"FROM v_mejores_puntajes_batallas WHERE slot_partida = ? ORDER BY fecha_ultima_victoria DESC";

		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, dbSlot);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					BattleRecordView row = new BattleRecordView();
					row.bossName = rs.getString("boss_nombre");
					row.species = rs.getString("especie");
					row.zoneName = rs.getString("zona_batalla");
					row.maxScore = rs.getInt("puntaje_maximo");
					row.maxCombo = rs.getInt("max_combo");
					row.totalAttempts = rs.getInt("intentos_totales");
					list.add(row);
				}
			}
		} catch (SQLException e) {
			System.err.println("Error leyendo v_mejores_puntajes_batallas: " + e.getMessage());
		}
		return list;
	}

	/**
	 * Busca el mejor puntaje y combo registrados en SQLite para una pista/encuentro dentro del slot.
	 * Retorna {@code { puntajeMaximo, maxCombo }} o {@code null} si no existe registro en BD.
	 */
	public static int[] getBestSongRecord(int zeroBasedSlot, String trackId, String trackTitle) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		String sql =
			"SELECT bv.puntaje_maximo, bv.max_combo " +
			"FROM BATALLAS_VENCIDAS bv " +
			"INNER JOIN PARTIDAS p ON p.id_partida = bv.id_partida " +
			"INNER JOIN CATALOGO_BATALLAS cb ON cb.id_batalla = bv.id_batalla " +
			"WHERE p.slot = ? AND (cb.boss_id = ? OR cb.boss_nombre = ?) " +
			"ORDER BY bv.puntaje_maximo DESC " +
			"LIMIT 1";

		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, dbSlot);
			ps.setString(2, trackId != null ? trackId : "");
			ps.setString(3, trackTitle != null ? trackTitle : "");
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return new int[] { rs.getInt("puntaje_maximo"), rs.getInt("max_combo") };
				}
			}
		} catch (SQLException e) {
			System.err.println("Error leyendo mejor puntaje de pista en BATALLAS_VENCIDAS: " + e.getMessage());
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
	 * sus registros en {@code BATALLAS_VENCIDAS}, {@code OBJETOS_OBTENIDOS}, {@code HISTORIAL_OBJETOS} y {@code FLAGS_PARTIDA}.
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
	public static void createNewGame(
		int zeroBasedSlot,
		String startScene,
		float playerX,
		float playerY,
		Map<String, Integer> initialInventory
	) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				try (PreparedStatement del = conn.prepareStatement("DELETE FROM PARTIDAS WHERE slot = ?")) {
					del.setInt(1, dbSlot);
					del.executeUpdate();
				}

				String zoneId = DatabaseManager.ensureZoneRegistered(conn, startScene);
				long partidaId;
				try (PreparedStatement ins = conn.prepareStatement(
					"INSERT INTO PARTIDAS (slot, id_zona_actual, player_x, player_y, id_zona_retorno, return_x, return_y, heal_strategy, estado_general, progreso_porcentaje) " +
					"VALUES (?, ?, ?, ?, ?, ?, ?, 'OPTIMAL', 'EN_PROGRESO', 0.0)",
					Statement.RETURN_GENERATED_KEYS)) {
					ins.setInt(1, dbSlot);
					ins.setString(2, zoneId);
					ins.setFloat(3, playerX);
					ins.setFloat(4, playerY);
					ins.setString(5, zoneId);
					ins.setFloat(6, playerX);
					ins.setFloat(7, playerY);
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
	 *   <li>Inserta o actualiza la fila principal en {@code PARTIDAS} (con referencias a {@code ZONAS}).</li>
	 *   <li>Sincroniza diferencialmente {@code FLAGS_PARTIDA}.</li>
	 *   <li>Sincroniza por diferencias {@code OBJETOS_OBTENIDOS} para que los triggers {@code trg_objetos_audit_insert}
	 *       y {@code trg_objetos_audit_update} registren únicamente cambios reales en {@code HISTORIAL_OBJETOS}
	 *       (y auto-eliminen filas cuya {@code cantidad} baje a {@code 0}).</li>
	 *   <li>Registra en {@code BATALLAS_VENCIDAS} cualquier jefe cuyo {@code defeat_flag} esté activo
	 *       (disparando {@code trg_batallas_progreso_insert}).</li>
	 * </ol>
	 */
	public static void saveState(
		int zeroBasedSlot,
		String scenePath,
		float playerX,
		float playerY,
		String returnScenePath,
		float returnX,
		float returnY,
		GameState.HealStrategy healStrategy,
		Set<String> flags,
		Map<String, Integer> inventory,
		Set<String> equippedIds
	) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				String zoneId = DatabaseManager.ensureZoneRegistered(conn, scenePath);
				String retZoneId = DatabaseManager.ensureZoneRegistered(conn, returnScenePath);
				String healStrat = (healStrategy != null) ? healStrategy.name() : "OPTIMAL";
				String genState = DatabaseManager.inferGeneralState(flags);

				long partidaId = findPartidaId(conn, dbSlot);
				if (partidaId <= 0) {
					try (PreparedStatement ins = conn.prepareStatement(
						"INSERT INTO PARTIDAS (slot, id_zona_actual, player_x, player_y, id_zona_retorno, return_x, return_y, heal_strategy, estado_general) " +
						"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
						ins.setInt(1, dbSlot);
						ins.setString(2, zoneId);
						ins.setFloat(3, playerX);
						ins.setFloat(4, playerY);
						ins.setString(5, retZoneId);
						ins.setFloat(6, returnX);
						ins.setFloat(7, returnY);
						ins.setString(8, healStrat);
						ins.setString(9, genState);
						ins.executeUpdate();
						try (ResultSet keys = ins.getGeneratedKeys()) {
							if (!keys.next()) throw new SQLException("No se generó id_partida");
							partidaId = keys.getLong(1);
						}
					}
				} else {
					try (PreparedStatement upd = conn.prepareStatement(
						"UPDATE PARTIDAS SET id_zona_actual = ?, player_x = ?, player_y = ?, " +
						"id_zona_retorno = ?, return_x = ?, return_y = ?, heal_strategy = ?, estado_general = ?, " +
						"progreso_porcentaje = CASE WHEN ? IN ('FINAL_NORMAL', 'FINAL_VERDADERO') THEN 100.0 ELSE progreso_porcentaje END, " +
						"fecha_actualizacion = CURRENT_TIMESTAMP " +
						"WHERE id_partida = ?")) {
						upd.setString(1, zoneId);
						upd.setFloat(2, playerX);
						upd.setFloat(3, playerY);
						upd.setString(4, retZoneId);
						upd.setFloat(5, returnX);
						upd.setFloat(6, returnY);
						upd.setString(7, healStrat);
						upd.setString(8, genState);
						upd.setString(9, genState);
						upd.setLong(10, partidaId);
						upd.executeUpdate();
					}
				}

				// Sincronizar flags de historia sin borrar los existentes que no cambiaron
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
				Set<String> safeEq = (equippedIds != null) ? equippedIds : Set.of();

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

				// Reflejar batallas vencidas definidas vía flags de diálogo si todavía no estaban registradas
				if (!safeFlags.isEmpty()) {
					try (PreparedStatement ps = conn.prepareStatement("SELECT defeat_flag, id_batalla FROM CATALOGO_BATALLAS");
						 ResultSet rs = ps.executeQuery();
						 PreparedStatement insBat = conn.prepareStatement(
							 "INSERT OR IGNORE INTO BATALLAS_VENCIDAS (id_partida, id_batalla) VALUES (?, ?)")) {
						while (rs.next()) {
							String flag = rs.getString("defeat_flag");
							if (safeFlags.contains(flag)) {
								insBat.setLong(1, partidaId);
								insBat.setString(2, rs.getString("id_batalla"));
								insBat.addBatch();
							}
						}
						insBat.executeBatch();
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
	 * {@code v_inventario_detallado}, junto con las tablas {@code FLAGS_PARTIDA} y {@code BATALLAS_VENCIDAS}.
	 */
	public static LoadedSaveData loadState(int zeroBasedSlot) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		String sql =
			"SELECT id_partida, escena_actual, player_x, player_y, return_scene, return_x, return_y, " +
			"heal_strategy, estado_general, progreso_porcentaje " +
			"FROM v_resumen_partidas WHERE slot = ?";

		try (Connection conn = DatabaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, dbSlot);
			LoadedSaveData data = null;
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) return null;
				data = new LoadedSaveData();
				data.idPartida = rs.getLong("id_partida");
				data.scenePath = rs.getString("escena_actual");
				data.playerX = rs.getFloat("player_x");
				data.playerY = rs.getFloat("player_y");
				data.returnScenePath = rs.getString("return_scene");
				data.returnX = rs.getFloat("return_x");
				data.returnY = rs.getFloat("return_y");
				try {
					data.healStrategy = GameState.HealStrategy.valueOf(rs.getString("heal_strategy"));
				} catch (Exception ignored) {
					data.healStrategy = GameState.HealStrategy.OPTIMAL;
				}
				data.generalState = rs.getString("estado_general");
				data.progressPercent = rs.getFloat("progreso_porcentaje");
			}

			try (PreparedStatement fp = conn.prepareStatement(
				"SELECT flag_nombre FROM FLAGS_PARTIDA WHERE id_partida = ?")) {
				fp.setLong(1, data.idPartida);
				try (ResultSet rs = fp.executeQuery()) {
					while (rs.next()) {
						data.flags.add(rs.getString(1));
					}
				}
			}

			try (PreparedStatement bp = conn.prepareStatement(
				"SELECT cb.defeat_flag FROM BATALLAS_VENCIDAS bv " +
				"INNER JOIN CATALOGO_BATALLAS cb ON cb.id_batalla = bv.id_batalla " +
				"WHERE bv.id_partida = ?")) {
				bp.setLong(1, data.idPartida);
				try (ResultSet rs = bp.executeQuery()) {
					while (rs.next()) {
						String flag = rs.getString(1);
						if (flag != null && !flag.isEmpty()) {
							data.flags.add(flag);
						}
					}
				}
			}

			try (PreparedStatement ip = conn.prepareStatement(
				"SELECT id_item, item_nombre, categoria, curacion_hp, descripcion, cantidad, equipado " +
				"FROM v_inventario_detallado WHERE id_partida = ?")) {
				ip.setLong(1, data.idPartida);
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
	// Registro de Victorias en Combates Rítmicos (BATALLAS_VENCIDAS y Triggers)
	// =========================================================================

	/**
	 * Registra o actualiza una victoria en {@code BATALLAS_VENCIDAS} (asegurando su entrada en {@code CATALOGO_BATALLAS}).
	 *
	 * <p>Triggers disparados automáticamente en SQLite:
	 * <ul>
	 *   <li>{@code trg_batallas_progreso_insert} / {@code trg_batallas_progreso_update}: recalcula
	 *       {@code PARTIDAS.progreso_porcentaje} según el porcentaje de batallas principales vencidas.</li>
	 *   <li>{@code trg_batallas_amuleto_score_insert} / {@code trg_batallas_amuleto_score_update}: si la batalla
	 *       define {@code id_amuleto_score} en {@code CATALOGO_BATALLAS} y {@code puntaje_maximo >= puntaje_minimo_amuleto},
	 *       otorga el amuleto automáticamente en {@code OBJETOS_OBTENIDOS}.</li>
	 * </ul>
	 */
	public static void recordBattleVictory(
		int zeroBasedSlot,
		String bossId,
		String bossName,
		String species,
		String defeatFlag,
		String battleSceneRel,
		String zoneSceneRel,
		String rewardItemId,
		int score,
		int maxCombo,
		int perfects,
		int goods,
		int safes,
		int misses
	) {
		int dbSlot = toDbSlot(zeroBasedSlot);
		try (Connection conn = DatabaseManager.getConnection()) {
			conn.setAutoCommit(false);
			try {
				long partidaId = findPartidaId(conn, dbSlot);
				if (partidaId <= 0) {
					conn.rollback();
					return;
				}

				String cleanFlag = (defeatFlag != null && !defeatFlag.isEmpty())
					? defeatFlag
					: ("BOSS_" + (bossId != null ? bossId.toUpperCase() : "CUSTOM") + "_DEFEATED");
				String cleanBossId = (bossId != null && !bossId.isEmpty()) ? bossId : cleanFlag.toLowerCase();
				String battleRel = DatabaseManager.toRelativeProjectPath(battleSceneRel);
				String zoneId = DatabaseManager.ensureZoneRegistered(conn, zoneSceneRel);

				String idBatalla = null;
				try (PreparedStatement ps = conn.prepareStatement(
					"SELECT id_batalla FROM CATALOGO_BATALLAS WHERE defeat_flag = ? OR boss_id = ? LIMIT 1")) {
					ps.setString(1, cleanFlag);
					ps.setString(2, cleanBossId);
					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next()) idBatalla = rs.getString(1);
					}
				}

				if (idBatalla == null) {
					idBatalla = "batalla_" + cleanBossId.toLowerCase().replaceAll("[^a-z0-9_]+", "_");
					String safeReward = null;
					if (rewardItemId != null && !rewardItemId.isEmpty()) {
						DatabaseManager.ensureItemRegistered(conn, GameState.getOrRegisterItemDef(rewardItemId));
						safeReward = rewardItemId;
					}
					try (PreparedStatement insCat = conn.prepareStatement(
						"INSERT OR IGNORE INTO CATALOGO_BATALLAS " +
						"(id_batalla, boss_id, boss_nombre, especie, defeat_flag, escena_combate_relativa, id_zona, id_item_recompensa, es_historia_principal) " +
						"VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)")) {
						insCat.setString(1, idBatalla);
						insCat.setString(2, cleanBossId);
						insCat.setString(3, (bossName != null && !bossName.isEmpty()) ? bossName : cleanBossId);
						insCat.setString(4, species != null ? species : "");
						insCat.setString(5, cleanFlag);
						insCat.setString(6, battleRel);
						insCat.setString(7, zoneId);
						insCat.setString(8, safeReward);
						insCat.executeUpdate();
					}
				}

				try (PreparedStatement upsert = conn.prepareStatement(
					"INSERT INTO BATALLAS_VENCIDAS (id_partida, id_batalla, puntaje_maximo, max_combo, perfects, goods, safes, misses, intentos_totales) " +
					"VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1) " +
					"ON CONFLICT(id_partida, id_batalla) DO UPDATE SET " +
					"puntaje_maximo = MAX(BATALLAS_VENCIDAS.puntaje_maximo, excluded.puntaje_maximo), " +
					"max_combo = MAX(BATALLAS_VENCIDAS.max_combo, excluded.max_combo), " +
					"perfects = MAX(BATALLAS_VENCIDAS.perfects, excluded.perfects), " +
					"goods = MAX(BATALLAS_VENCIDAS.goods, excluded.goods), " +
					"safes = MAX(BATALLAS_VENCIDAS.safes, excluded.safes), " +
					"misses = MIN(BATALLAS_VENCIDAS.misses, excluded.misses), " +
					"intentos_totales = BATALLAS_VENCIDAS.intentos_totales + 1, " +
					"fecha_ultima_victoria = CURRENT_TIMESTAMP")) {
					upsert.setLong(1, partidaId);
					upsert.setString(2, idBatalla);
					upsert.setInt(3, Math.max(0, score));
					upsert.setInt(4, Math.max(0, maxCombo));
					upsert.setInt(5, Math.max(0, perfects));
					upsert.setInt(6, Math.max(0, goods));
					upsert.setInt(7, Math.max(0, safes));
					upsert.setInt(8, Math.max(0, misses));
					upsert.executeUpdate();
				}

				conn.commit();
			} catch (Exception e) {
				conn.rollback();
				throw e;
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			System.err.println("Error registrando batalla vencida en SQLite: " + e.getMessage());
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
