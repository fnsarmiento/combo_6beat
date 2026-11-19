package game.core.db;

import engine.ResourcesManager;
import game.core.GameState;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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
import processing.data.JSONArray;
import processing.data.JSONObject;

/**
 * Gestor central de la base de datos SQLite de Combo 6Beat.
 * Inicializa el esquema 3FN, triggers, vistas, catálogos y migración única desde archivos .save.
 */
public class DatabaseManager {

	public static final String DEFAULT_DB_FILE = "combo6beat.db";
	public static final String DEFAULT_SCHEMA_FILE = "resources/db/schema.sql";

	private static boolean _initialized = false;
	private static String _dbPath = "";
	private static String _jdbcUrl = "";

	public static synchronized void init() {
		String dbAbs = ResourcesManager.getAbsPathFromProject(DEFAULT_DB_FILE);
		String schemaAbs = ResourcesManager.getAbsPathFromProject(DEFAULT_SCHEMA_FILE);
		init(dbAbs, schemaAbs, true);
	}

	public static synchronized void init(String dbFilePath, String schemaFilePath) {
		init(dbFilePath, schemaFilePath, false);
	}

	private static synchronized void init(String dbFilePath, String schemaFilePath, boolean migrateLegacySaves) {
		if (_initialized && dbFilePath.equals(_dbPath)) return;

		_dbPath = dbFilePath;
		_jdbcUrl = "jdbc:sqlite:" + _dbPath;

		File dbFile = new File(_dbPath);
		if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
			dbFile.getParentFile().mkdirs();
		}

		try (Connection conn = openRawConnection()) {
			try (Statement st = conn.createStatement()) {
				st.execute("PRAGMA journal_mode = WAL");
				st.execute("PRAGMA foreign_keys = ON");
				st.execute("PRAGMA busy_timeout = 3000");
			}

			String sql = Files.readString(Path.of(schemaFilePath), StandardCharsets.UTF_8);
			executeSqlScript(conn, sql);

			if (migrateLegacySaves) {
				migrateLegacySavesIfNeeded(conn);
			}
			_initialized = true;
		} catch (Exception e) {
			throw new RuntimeException("Error inicializando SQLite (" + _dbPath + "): " + e.getMessage(), e);
		}
	}

	public static boolean isInitialized() {
		return _initialized;
	}

	public static String getDbPath() {
		return _dbPath;
	}

	public static Connection getConnection() throws SQLException {
		if (!_initialized) {
			init();
		}
		Connection conn = openRawConnection();
		try (Statement st = conn.createStatement()) {
			st.execute("PRAGMA foreign_keys = ON");
			st.execute("PRAGMA busy_timeout = 3000");
		}
		return conn;
	}

	private static Connection openRawConnection() throws SQLException {
		return DriverManager.getConnection(_jdbcUrl);
	}

	/**
	 * Ejecuta un script SQL respetando comentarios --, literales '...' y bloques BEGIN ... CASE ... END; en triggers.
	 */
	public static void executeSqlScript(Connection conn, String script) throws SQLException {
		List<String> statements = splitSqlStatements(script);
		try (Statement st = conn.createStatement()) {
			for (String stmt : statements) {
				String trimmed = stmt.trim();
				if (!trimmed.isEmpty()) {
					st.execute(trimmed);
				}
			}
		}
	}

	public static List<String> splitSqlStatements(String script) {
		List<String> result = new ArrayList<>();
		if (script == null || script.isEmpty()) return result;

		StringBuilder current = new StringBuilder();
		boolean inSingleQuote = false;
		boolean inLineComment = false;
		boolean inBlockComment = false;
		boolean inTriggerBody = false;
		int beginDepth = 0;
		int caseDepth = 0;

		int n = script.length();
		for (int i = 0; i < n; i++) {
			char c = script.charAt(i);
			char next = (i + 1 < n) ? script.charAt(i + 1) : '\0';

			if (inLineComment) {
				if (c == '\n') {
					inLineComment = false;
					current.append('\n');
				}
				continue;
			}
			if (inBlockComment) {
				if (c == '*' && next == '/') {
					inBlockComment = false;
					i++;
				}
				continue;
			}
			if (inSingleQuote) {
				current.append(c);
				if (c == '\'' && next == '\'') {
					current.append(next);
					i++;
				} else if (c == '\'') {
					inSingleQuote = false;
				}
				continue;
			}

			if (c == '-' && next == '-') {
				inLineComment = true;
				i++;
				continue;
			}
			if (c == '/' && next == '*') {
				inBlockComment = true;
				i++;
				continue;
			}
			if (c == '\'') {
				inSingleQuote = true;
				current.append(c);
				continue;
			}

			if (Character.isLetterOrDigit(c) || c == '_') {
				int start = i;
				while (i + 1 < n && (Character.isLetterOrDigit(script.charAt(i + 1)) || script.charAt(i + 1) == '_')) {
					i++;
				}
				String word = script.substring(start, i + 1);
				current.append(word);
				String upper = word.toUpperCase(Locale.ROOT);

				if ("TRIGGER".equals(upper)) {
					inTriggerBody = true;
				} else if (inTriggerBody) {
					if ("BEGIN".equals(upper)) {
						beginDepth++;
					} else if ("CASE".equals(upper)) {
						caseDepth++;
					} else if ("END".equals(upper)) {
						if (caseDepth > 0) {
							caseDepth--;
						} else if (beginDepth > 0) {
							beginDepth--;
						}
					}
				}
				continue;
			}

			if (c == ';' && (!inTriggerBody || (beginDepth == 0 && caseDepth == 0))) {
				String sql = current.toString().trim();
				if (!sql.isEmpty()) {
					result.add(sql);
				}
				current.setLength(0);
				inTriggerBody = false;
				beginDepth = 0;
				caseDepth = 0;
			} else {
				current.append(c);
			}
		}

		String tail = current.toString().trim();
		if (!tail.isEmpty()) {
			result.add(tail);
		}
		return result;
	}

	/**
	 * Normaliza cualquier ruta a formato relativo respecto al directorio del proyecto.
	 */
	public static String toRelativeProjectPath(String path) {
		if (path == null) return "";
		String clean = path.trim().replace('\\', '/');
		if (clean.isEmpty()) return "";
		try {
			Path p = Path.of(clean).normalize();
			Path proj = Path.of(ResourcesManager.getProjectPath()).toAbsolutePath().normalize();
			if (p.isAbsolute()) {
				if (p.startsWith(proj)) {
					return proj.relativize(p).toString().replace('\\', '/');
				}
				int resIdx = clean.indexOf("resources/");
				if (resIdx >= 0) return clean.substring(resIdx);
				return p.getFileName().toString();
			}
		} catch (Exception ignored) {}
		while (clean.startsWith("./")) {
			clean = clean.substring(2);
		}
		return clean;
	}

	public static String ensureZoneRegistered(Connection conn, String sceneRelPath) throws SQLException {
		String rel = toRelativeProjectPath(sceneRelPath);
		if (rel.isEmpty()) rel = "resources/scenes/hub.xml";

		try (PreparedStatement ps = conn.prepareStatement(
			"SELECT id_zona FROM ZONAS WHERE escena_xml_relativa = ?")) {
			ps.setString(1, rel);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getString(1);
				}
			}
		}

		String baseName = new File(rel).getName();
		int dot = baseName.lastIndexOf('.');
		String slug = (dot > 0 ? baseName.substring(0, dot) : baseName)
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9_]+", "_");
		if (slug.isEmpty()) slug = "zona_custom";

		String zoneId = slug;
		int suffix = 1;
		while (zoneIdExists(conn, zoneId)) {
			zoneId = slug + "_" + (++suffix);
		}

		String label = inferZoneLabel(rel);
		try (PreparedStatement ins = conn.prepareStatement(
			"INSERT INTO ZONAS (id_zona, nombre, escena_xml_relativa, orden_progresion) VALUES (?, ?, ?, 50)")) {
			ins.setString(1, zoneId);
			ins.setString(2, label);
			ins.setString(3, rel);
			ins.executeUpdate();
		}
		return zoneId;
	}

	private static boolean zoneIdExists(Connection conn, String zoneId) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM ZONAS WHERE id_zona = ?")) {
			ps.setString(1, zoneId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static String inferZoneLabel(String relScene) {
		if (relScene.endsWith("hub.xml")) return "El Hub";
		if (relScene.endsWith("beach_ruins.xml")) return "Beach";
		if (relScene.endsWith("swamp.xml")) return "Swamp (Pantano)";
		if (relScene.endsWith("a.xml")) return "Sala de Pruebas";
		String fileName = new File(relScene).getName();
		int dot = fileName.lastIndexOf('.');
		return (dot > 0) ? fileName.substring(0, dot) : fileName;
	}

	public static void ensureItemRegistered(Connection conn, GameState.ItemDef def) throws SQLException {
		if (def == null || def.id == null || def.id.isEmpty()) return;
		try (PreparedStatement ps = conn.prepareStatement(
			"INSERT OR IGNORE INTO CATALOGO_ITEMS (id_item, nombre, categoria, curacion_hp, descripcion) VALUES (?, ?, ?, ?, ?)")) {
			ps.setString(1, def.id);
			ps.setString(2, def.name != null ? def.name : def.id);
			ps.setString(3, def.category != null ? def.category.name() : "MISC");
			ps.setInt(4, Math.max(0, def.healAmount));
			ps.setString(5, def.description != null ? def.description : "");
			ps.executeUpdate();
		}
	}

	private static void migrateLegacySavesIfNeeded(Connection conn) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
			"SELECT valor FROM DB_META WHERE clave = 'legacy_saves_migrated'")) {
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next() && "1".equals(rs.getString(1))) {
					return;
				}
			}
		}

		// Asegura catálogo base de ítems antes de migrar
		GameState.ensureCatalogLoaded();
		for (GameState.ItemDef def : GameState.getCatalogSnapshot()) {
			ensureItemRegistered(conn, def);
		}

		Map<String, String> battleByFlag = new HashMap<>();
		try (PreparedStatement ps = conn.prepareStatement("SELECT defeat_flag, id_batalla FROM CATALOGO_BATALLAS");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				battleByFlag.put(rs.getString(1), rs.getString(2));
			}
		}

		boolean prevAutoCommit = conn.getAutoCommit();
		conn.setAutoCommit(false);
		try {
			for (int slotIdx = 0; slotIdx < GameState.SAVE_SLOT_COUNT; slotIdx++) {
				int dbSlot = slotIdx + 1;
				String legacyName = (slotIdx == 0) ? ".save" : (".save" + dbSlot);
				File legacyFile = new File(ResourcesManager.getAbsPathFromProject(legacyName));
				if (!legacyFile.exists() || !legacyFile.isFile()) continue;

				boolean slotExists;
				try (PreparedStatement chk = conn.prepareStatement("SELECT 1 FROM PARTIDAS WHERE slot = ?")) {
					chk.setInt(1, dbSlot);
					try (ResultSet rs = chk.executeQuery()) {
						slotExists = rs.next();
					}
				}
				if (slotExists) continue;

				String raw = Files.readString(legacyFile.toPath(), StandardCharsets.UTF_8);
				JSONObject json = JSONObject.parse(raw);
				if (json == null) continue;

				String sceneRel = toRelativeProjectPath(json.getString("Scene", "resources/scenes/hub.xml"));
				String returnRel = toRelativeProjectPath(json.getString("ReturnScene", sceneRel));
				String zoneId = ensureZoneRegistered(conn, sceneRel);
				String retZoneId = ensureZoneRegistered(conn, returnRel);
				float px = json.getFloat("PlayerX", 256.0f);
				float py = json.getFloat("PlayerY", 224.0f);
				float rx = json.getFloat("ReturnX", px);
				float ry = json.getFloat("ReturnY", py);

				String healStrat = json.getString("HealStrategy", "OPTIMAL");
				if (!"MINIMAL".equals(healStrat)) healStrat = "OPTIMAL";

				Set<String> flags = new LinkedHashSet<>();
				JSONArray flagsArr = json.getJSONArray("Flags");
				if (flagsArr != null) {
					for (int i = 0; i < flagsArr.size(); i++) {
						String f = flagsArr.getString(i, "");
						if (!f.isEmpty()) flags.add(f);
					}
				}

				String status = inferGeneralState(flags);
				long partidaId;
				try (PreparedStatement ins = conn.prepareStatement(
					"INSERT INTO PARTIDAS (slot, id_zona_actual, player_x, player_y, id_zona_retorno, return_x, return_y, heal_strategy, estado_general) " +
					"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
					ins.setInt(1, dbSlot);
					ins.setString(2, zoneId);
					ins.setFloat(3, px);
					ins.setFloat(4, py);
					ins.setString(5, retZoneId);
					ins.setFloat(6, rx);
					ins.setFloat(7, ry);
					ins.setString(8, healStrat);
					ins.setString(9, status);
					ins.executeUpdate();
					try (ResultSet keys = ins.getGeneratedKeys()) {
						if (!keys.next()) continue;
						partidaId = keys.getLong(1);
					}
				}

				try (PreparedStatement insFlag = conn.prepareStatement(
					"INSERT OR IGNORE INTO FLAGS_PARTIDA (id_partida, flag_nombre) VALUES (?, ?)")) {
					for (String flag : flags) {
						insFlag.setLong(1, partidaId);
						insFlag.setString(2, flag);
						insFlag.addBatch();
					}
					insFlag.executeBatch();
				}

				// Registrar batallas vencidas según defeat_flag migrados
				try (PreparedStatement insBat = conn.prepareStatement(
					"INSERT OR IGNORE INTO BATALLAS_VENCIDAS (id_partida, id_batalla) VALUES (?, ?)")) {
					for (String flag : flags) {
						String idBat = battleByFlag.get(flag);
						if (idBat != null) {
							insBat.setLong(1, partidaId);
							insBat.setString(2, idBat);
							insBat.addBatch();
						}
					}
					insBat.executeBatch();
				}

				Set<String> equipped = new LinkedHashSet<>();
				if (json.hasKey("EquippedPowerUps")) {
					JSONArray eqArr = json.getJSONArray("EquippedPowerUps");
					if (eqArr != null) {
						for (int i = 0; i < eqArr.size(); i++) {
							String eq = eqArr.getString(i, "");
							if (!eq.isEmpty() && !"none".equals(eq)) equipped.add(eq);
						}
					}
				}
				String singleEq = json.getString("EquippedPowerUp", "none");
				if (!singleEq.isEmpty() && !"none".equals(singleEq)) {
					equipped.add(singleEq);
				}

				if (json.hasKey("Inventory")) {
					JSONObject invObj = json.getJSONObject("Inventory");
					if (invObj != null) {
						Map<String, Integer> invMap = new LinkedHashMap<>();
						for (Object keyObj : invObj.keys()) {
							String itemId = String.valueOf(keyObj);
							int count = invObj.getInt(itemId, 0);
							if (count > 0) {
								invMap.put(itemId, count);
								ensureItemRegistered(conn, GameState.getOrRegisterItemDef(itemId));
							}
						}
						try (PreparedStatement insObj = conn.prepareStatement(
							"INSERT OR REPLACE INTO OBJETOS_OBTENIDOS (id_partida, id_item, cantidad, equipado) VALUES (?, ?, ?, ?)")) {
							for (Map.Entry<String, Integer> entry : invMap.entrySet()) {
								insObj.setLong(1, partidaId);
								insObj.setString(2, entry.getKey());
								insObj.setInt(3, entry.getValue());
								insObj.setInt(4, equipped.contains(entry.getKey()) ? 1 : 0);
								insObj.addBatch();
							}
							insObj.executeBatch();
						}
					}
				}
			}

			try (PreparedStatement mark = conn.prepareStatement(
				"INSERT OR REPLACE INTO DB_META (clave, valor) VALUES ('legacy_saves_migrated', '1')")) {
				mark.executeUpdate();
			}
			conn.commit();
		} catch (Exception e) {
			conn.rollback();
			throw new SQLException("Error migrando partidas legacy .save a SQLite", e);
		} finally {
			conn.setAutoCommit(prevAutoCommit);
		}
	}

	public static String inferGeneralState(Set<String> flags) {
		if (flags != null) {
			if (flags.contains("GAME_ENDING_TRUE")) return "FINAL_VERDADERO";
			if (flags.contains("GAME_ENDING_NORMAL")) return "FINAL_NORMAL";
			if (flags.contains("LOOP_EXHAUSTED_MODE")) return "BUCLE_AGOTADO";
		}
		return "EN_PROGRESO";
	}
}
