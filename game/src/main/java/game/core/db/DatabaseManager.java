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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import processing.data.JSONArray;
import processing.data.JSONObject;

/**
 * Gestor central de la base de datos SQLite en 3FN de Combo 6Beat ({@code combo6beat.db}).
 *
 * <p>Responsabilidades sobre el esquema ({@code resources/db/schema.sql}):
 * <ul>
 *   <li>Configurar conexiones JDBC con {@code PRAGMA foreign_keys = ON}, {@code WAL} y {@code busy_timeout}.</li>
 *   <li>Migrar automáticamente bases de datos locales existentes al esquema limpio de 9 tablas en 3FN.</li>
 *   <li>Ejecutar el script DDL/DML completo (tablas, índices, triggers con bloques {@code BEGIN...CASE...END} y vistas).</li>
 *   <li>Registrar dinámicamente ítems y amuletos en {@code CATALOGO_ITEMS}.</li>
 *   <li>Ejecutar la migración única desde archivos JSON legacy ({@code .save}) controlada por {@code DB_META}.</li>
 * </ul>
 */
public class DatabaseManager {

	// =========================================================================
	// Constantes y Estado de Conexión SQLite
	// =========================================================================

	public static final String DEFAULT_DB_FILE = "combo6beat.db";
	public static final String DEFAULT_SCHEMA_FILE = "resources/db/schema.sql";

	private static boolean _initialized = false;
	private static String _dbPath = "";
	private static String _jdbcUrl = "";

	// =========================================================================
	// Inicialización del Esquema 3FN y Gestión de Conexiones JDBC
	// =========================================================================

	/**
	 * Inicializa la base de datos por defecto ({@code combo6beat.db}) aplicando {@code resources/db/schema.sql}
	 * y migrando partidas legacy {@code .save} si aún no fueron migradas.
	 */
	public static synchronized void init() {
		String dbAbs = ResourcesManager.getAbsPathFromProject(DEFAULT_DB_FILE);
		String schemaAbs = ResourcesManager.getAbsPathFromProject(DEFAULT_SCHEMA_FILE);
		init(dbAbs, schemaAbs, true);
	}

	/**
	 * Inicializa una base de datos SQLite en una ruta específica aplicando el archivo de esquema indicado.
	 */
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

			upgradeExistingSchemaIfNeeded(conn);

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

	/**
	 * Obtiene una nueva conexión JDBC con integridad referencial ({@code foreign_keys = ON}) activa.
	 */
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

	// =========================================================================
	// Migración Automática de Esquema Legacy (Hub/Jefes -> Rítmico + Tienda 3FN)
	// =========================================================================

	/**
	 * Actualiza una base de datos {@code combo6beat.db} previa al nuevo esquema limpio de 9 tablas en 3FN
	 * conservando partidas, puntos de tienda, estrategias de curación, flags e inventario de objetos.
	 */
	private static void upgradeExistingSchemaIfNeeded(Connection conn) throws SQLException {
		boolean hasPartidas = false;
		boolean hasPuntosTienda = false;
		boolean hasZonas = false;
		boolean hasCatalogoItems = false;
		boolean hasPrecioTienda = false;

		try (Statement st = conn.createStatement()) {
			try (ResultSet rs = st.executeQuery("PRAGMA table_info(PARTIDAS)")) {
				while (rs.next()) {
					hasPartidas = true;
					if ("puntos_tienda".equalsIgnoreCase(rs.getString("name"))) {
						hasPuntosTienda = true;
					}
				}
			}
			try (ResultSet rs = st.executeQuery(
				"SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('ZONAS', 'CATALOGO_BATALLAS', 'BATALLAS_VENCIDAS', 'ENEMIGOS')")) {
				if (rs.next()) {
					hasZonas = true;
				}
			}
			try (ResultSet rs = st.executeQuery("PRAGMA table_info(CATALOGO_ITEMS)")) {
				while (rs.next()) {
					hasCatalogoItems = true;
					if ("precio_tienda".equalsIgnoreCase(rs.getString("name"))) {
						hasPrecioTienda = true;
					}
				}
			}
		}

		boolean needsMigration = (hasPartidas && !hasPuntosTienda) || hasZonas || (hasCatalogoItems && !hasPrecioTienda);
		if (!needsMigration) {
			return;
		}

		try (Statement st = conn.createStatement()) {
			st.execute("PRAGMA foreign_keys = OFF");
			boolean prevAutoCommit = conn.getAutoCommit();
			conn.setAutoCommit(false);
			try {
				// 1. Eliminar todas las vistas y triggers existentes antes de recrear tablas
				//    (SQLite valida todos los triggers del esquema durante ALTER TABLE ... RENAME TO;
				//     schema.sql recreará inmediatamente las 6 vistas y los 12 triggers actualizados)
				List<String> existingViews = new ArrayList<>();
				try (ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'view'")) {
					while (rs.next()) {
						existingViews.add(rs.getString(1));
					}
				}
				for (String view : existingViews) {
					st.execute("DROP VIEW IF EXISTS \"" + view.replace("\"", "\"\"") + "\"");
				}

				List<String> existingTriggers = new ArrayList<>();
				try (ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'trigger'")) {
					while (rs.next()) {
						existingTriggers.add(rs.getString(1));
					}
				}
				for (String trg : existingTriggers) {
					st.execute("DROP TRIGGER IF EXISTS \"" + trg.replace("\"", "\"\"") + "\"");
				}

				// 2. Migrar tabla PARTIDAS extrayendo puntos_tienda desde FLAGS_PARTIDA (SHOP_POINTS:*)
				if (hasPartidas && !hasPuntosTienda) {
					st.execute("DROP TABLE IF EXISTS PARTIDAS_NEW");
					st.execute(
						"CREATE TABLE PARTIDAS_NEW (" +
						"id_partida INTEGER PRIMARY KEY AUTOINCREMENT, " +
						"slot INTEGER NOT NULL UNIQUE CHECK (slot BETWEEN 1 AND 4), " +
						"puntos_tienda INTEGER NOT NULL DEFAULT 0 CHECK (puntos_tienda >= 0), " +
						"heal_strategy TEXT NOT NULL DEFAULT 'OPTIMAL' CHECK (heal_strategy IN ('OPTIMAL', 'MINIMAL')), " +
						"fecha_creacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
						"fecha_actualizacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)"
					);

					boolean hasFlagsTable = false;
					try (ResultSet rs = st.executeQuery(
						"SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'FLAGS_PARTIDA'")) {
						hasFlagsTable = rs.next();
					}

					if (hasFlagsTable) {
						st.execute(
							"INSERT INTO PARTIDAS_NEW (id_partida, slot, puntos_tienda, heal_strategy, fecha_creacion, fecha_actualizacion) " +
							"SELECT p.id_partida, p.slot, " +
							"MAX(0, COALESCE((SELECT MAX(CAST(SUBSTR(fp.flag_nombre, 13) AS INTEGER)) " +
							"FROM FLAGS_PARTIDA fp WHERE fp.id_partida = p.id_partida AND fp.flag_nombre LIKE 'SHOP_POINTS:%'), 0)), " +
							"CASE WHEN p.heal_strategy IN ('OPTIMAL', 'MINIMAL') THEN p.heal_strategy ELSE 'OPTIMAL' END, " +
							"COALESCE(p.fecha_creacion, CURRENT_TIMESTAMP), " +
							"COALESCE(p.fecha_actualizacion, CURRENT_TIMESTAMP) " +
							"FROM PARTIDAS p"
						);
						st.execute("DELETE FROM FLAGS_PARTIDA WHERE flag_nombre LIKE 'SHOP_POINTS:%'");
					} else {
						st.execute(
							"INSERT INTO PARTIDAS_NEW (id_partida, slot, puntos_tienda, heal_strategy, fecha_creacion, fecha_actualizacion) " +
							"SELECT p.id_partida, p.slot, 0, " +
							"CASE WHEN p.heal_strategy IN ('OPTIMAL', 'MINIMAL') THEN p.heal_strategy ELSE 'OPTIMAL' END, " +
							"COALESCE(p.fecha_creacion, CURRENT_TIMESTAMP), " +
							"COALESCE(p.fecha_actualizacion, CURRENT_TIMESTAMP) " +
							"FROM PARTIDAS p"
						);
					}

					st.execute("DROP TABLE PARTIDAS");
					st.execute("ALTER TABLE PARTIDAS_NEW RENAME TO PARTIDAS");
				}

				// 3. Limpiar categoría legacy 'KEY' y actualizar CATALOGO_ITEMS con precio_tienda
				if (hasCatalogoItems) {
					st.execute(
						"DELETE FROM OBJETOS_OBTENIDOS WHERE id_item IN (" +
						"SELECT id_item FROM CATALOGO_ITEMS WHERE categoria = 'KEY') OR id_item LIKE 'key_%'"
					);
					st.execute(
						"DELETE FROM HISTORIAL_OBJETOS WHERE id_item IN (" +
						"SELECT id_item FROM CATALOGO_ITEMS WHERE categoria = 'KEY') OR id_item LIKE 'key_%'"
					);
					st.execute(
						"DELETE FROM CANCION_OBJETOS_INTENTO WHERE id_item IN (" +
						"SELECT id_item FROM CATALOGO_ITEMS WHERE categoria = 'KEY') OR id_item LIKE 'key_%'"
					);
					st.execute("DELETE FROM CATALOGO_ITEMS WHERE categoria = 'KEY' OR id_item LIKE 'key_%'");

					st.execute("DROP TABLE IF EXISTS CATALOGO_ITEMS_NEW");
					st.execute(
						"CREATE TABLE CATALOGO_ITEMS_NEW (" +
						"id_item TEXT PRIMARY KEY, " +
						"nombre TEXT NOT NULL, " +
						"categoria TEXT NOT NULL CHECK (categoria IN ('HEAL', 'POWERUP', 'AMULET', 'MISC')), " +
						"curacion_hp INTEGER NOT NULL DEFAULT 0 CHECK (curacion_hp >= 0), " +
						"precio_tienda INTEGER NOT NULL DEFAULT 0 CHECK (precio_tienda >= 0), " +
						"descripcion TEXT NOT NULL DEFAULT '')"
					);

					if (hasPrecioTienda) {
						st.execute(
							"INSERT INTO CATALOGO_ITEMS_NEW (id_item, nombre, categoria, curacion_hp, precio_tienda, descripcion) " +
							"SELECT id_item, nombre, " +
							"CASE WHEN categoria IN ('HEAL', 'POWERUP', 'AMULET', 'MISC') THEN categoria ELSE 'MISC' END, " +
							"MAX(0, COALESCE(curacion_hp, 0)), MAX(0, COALESCE(precio_tienda, 0)), COALESCE(descripcion, '') " +
							"FROM CATALOGO_ITEMS"
						);
					} else {
						st.execute(
							"INSERT INTO CATALOGO_ITEMS_NEW (id_item, nombre, categoria, curacion_hp, precio_tienda, descripcion) " +
							"SELECT id_item, nombre, " +
							"CASE WHEN categoria IN ('HEAL', 'POWERUP', 'AMULET', 'MISC') THEN categoria ELSE 'MISC' END, " +
							"MAX(0, COALESCE(curacion_hp, 0)), 0, COALESCE(descripcion, '') " +
							"FROM CATALOGO_ITEMS"
						);
					}

					st.execute("DROP TABLE CATALOGO_ITEMS");
					st.execute("ALTER TABLE CATALOGO_ITEMS_NEW RENAME TO CATALOGO_ITEMS");
				}

				// 4. Eliminar tablas e índices legacy del Hub y batallas RPG
				st.execute("DROP INDEX IF EXISTS idx_batallas_vencidas_partida");
				st.execute("DROP INDEX IF EXISTS idx_enemigos_zona_pos");
				st.execute("DROP TABLE IF EXISTS ENEMIGOS");
				st.execute("DROP TABLE IF EXISTS BATALLAS_VENCIDAS");
				st.execute("DROP TABLE IF EXISTS CATALOGO_BATALLAS");
				st.execute("DROP TABLE IF EXISTS ZONAS");

				conn.commit();
			} catch (SQLException e) {
				try {
					conn.rollback();
				} catch (SQLException ignored) {}
				throw e;
			} finally {
				conn.setAutoCommit(prevAutoCommit);
				st.execute("PRAGMA foreign_keys = ON");
			}
		}
	}

	// =========================================================================
	// Parser y Ejecutor de Scripts SQL (Soporte de Triggers BEGIN...CASE...END)
	// =========================================================================

	/**
	 * Ejecuta un script SQL respetando comentarios {@code --}, literales {@code '...'}
	 * y bloques compuestos {@code BEGIN ... CASE ... END;} dentro de triggers SQLite.
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

	/**
	 * Divide un script SQL en sentencias individuales sin cortar en los {@code ;} internos de un {@code CREATE TRIGGER}.
	 */
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

	// =========================================================================
	// Normalización de Rutas y Registro en Catálogo 3FN (CATALOGO_ITEMS)
	// =========================================================================

	/**
	 * Normaliza cualquier ruta a formato relativo respecto al directorio del proyecto,
	 * cumpliendo los triggers de validación de rutas relativas ({@code trg_canciones_validar_*}, {@code trg_records_validar_*}).
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

	/**
	 * Registra un objeto o amuleto en la tabla {@code CATALOGO_ITEMS} si aún no existe ({@code INSERT OR IGNORE}).
	 *
	 * <p>Tablas tocadas: {@code CATALOGO_ITEMS}.
	 */
	public static void ensureItemRegistered(Connection conn, GameState.ItemDef def) throws SQLException {
		if (def == null || def.id == null || def.id.isEmpty()) return;
		try (PreparedStatement ps = conn.prepareStatement(
			"INSERT OR IGNORE INTO CATALOGO_ITEMS (id_item, nombre, categoria, curacion_hp, precio_tienda, descripcion) VALUES (?, ?, ?, ?, ?, ?)")) {
			ps.setString(1, def.id);
			ps.setString(2, def.name != null ? def.name : def.id);
			ps.setString(3, def.category != null ? def.category.name() : "MISC");
			ps.setInt(4, Math.max(0, def.healAmount));
			ps.setInt(5, Math.max(0, def.shopPrice));
			ps.setString(6, def.description != null ? def.description : "");
			ps.executeUpdate();
		}
	}

	// =========================================================================
	// Migración Única de Archivos Legacy (.save)
	// =========================================================================

	/**
	 * Migra por única vez archivos JSON {@code .save} al esquema relacional 3FN.
	 *
	 * <p>Tablas y triggers involucrados:
	 * <ul>
	 *   <li>{@code DB_META}: consulta y marca {@code legacy_saves_migrated = '1'}.</li>
	 *   <li>{@code CATALOGO_ITEMS}: sincroniza definiciones de ítems y amuletos.</li>
	 *   <li>{@code PARTIDAS}, {@code FLAGS_PARTIDA}, {@code RECORDS_CANCIONES}
	 *       y {@code OBJETOS_OBTENIDOS} (activa {@code trg_objetos_audit_insert} hacia {@code HISTORIAL_OBJETOS}).</li>
	 * </ul>
	 */
	private static void migrateLegacySavesIfNeeded(Connection conn) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
			"SELECT valor FROM DB_META WHERE clave = 'legacy_saves_migrated'")) {
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next() && "1".equals(rs.getString(1))) {
					return;
				}
			}
		}

		GameState.ensureCatalogLoaded();
		for (GameState.ItemDef def : GameState.getCatalogSnapshot()) {
			ensureItemRegistered(conn, def);
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

				String healStrat = json.getString("HealStrategy", "OPTIMAL");
				if (!"MINIMAL".equals(healStrat)) healStrat = "OPTIMAL";

				int shopPoints = Math.max(0, json.getInt("ShopPoints", 0));
				Set<String> flags = new LinkedHashSet<>();
				JSONArray flagsArr = json.getJSONArray("Flags");
				if (flagsArr != null) {
					for (int i = 0; i < flagsArr.size(); i++) {
						String f = flagsArr.getString(i, "");
						if (f == null || f.isEmpty()) continue;
						if (f.startsWith("SHOP_POINTS:")) {
							try {
								shopPoints = Math.max(shopPoints, Integer.parseInt(f.substring("SHOP_POINTS:".length()).trim()));
							} catch (NumberFormatException ignored) {}
						} else {
							flags.add(f);
						}
					}
				}

				long partidaId;
				try (PreparedStatement ins = conn.prepareStatement(
					"INSERT INTO PARTIDAS (slot, puntos_tienda, heal_strategy) VALUES (?, ?, ?)",
					Statement.RETURN_GENERATED_KEYS)) {
					ins.setInt(1, dbSlot);
					ins.setInt(2, Math.max(0, shopPoints));
					ins.setString(3, healStrat);
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

				// Si el archivo legacy tenía flags de pistas/jefes vencidos, migrarlos como registros en RECORDS_CANCIONES
				try (PreparedStatement insRec = conn.prepareStatement(
					"INSERT OR IGNORE INTO RECORDS_CANCIONES (id_partida, clave_cancion, titulo, intentos_totales, victorias_totales) " +
					"VALUES (?, ?, ?, 1, 1)")) {
					for (String flag : flags) {
						if (flag.startsWith("BOSS_") && flag.endsWith("_DEFEATED")) {
							String songKey = "legacy/" + flag.toLowerCase(Locale.ROOT);
							insRec.setLong(1, partidaId);
							insRec.setString(2, songKey);
							insRec.setString(3, flag);
							insRec.addBatch();
						}
					}
					insRec.executeBatch();
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
							if (itemId.startsWith("key_")) continue;
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
}
