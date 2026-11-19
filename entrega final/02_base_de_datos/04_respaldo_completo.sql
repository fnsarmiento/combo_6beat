-- =============================================================================
-- Combo 6Beat — Respaldo Completo de la Base de Datos (SQLite .dump)
-- Importar desde cero con: sqlite3 combo6beat.db < 04_respaldo_completo.sql
-- =============================================================================
PRAGMA foreign_keys=OFF;
BEGIN TRANSACTION;
CREATE TABLE DB_META (
    clave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);
INSERT INTO DB_META VALUES('legacy_saves_migrated','1');
INSERT INTO DB_META VALUES('schema_version','1.0');
CREATE TABLE ZONAS (
    id_zona             TEXT PRIMARY KEY,
    nombre              TEXT NOT NULL,
    escena_xml_relativa TEXT NOT NULL UNIQUE,
    orden_progresion    INTEGER NOT NULL DEFAULT 0
);
INSERT INTO ZONAS VALUES('hub','El Hub','resources/scenes/hub.xml',0);
INSERT INTO ZONAS VALUES('swamp','Swamp (Pantano)','resources/scenes/swamp.xml',1);
INSERT INTO ZONAS VALUES('beach_ruins','Beach','resources/scenes/beach_ruins.xml',2);
INSERT INTO ZONAS VALUES('debug_a','Sala de Pruebas','resources/scenes/a.xml',99);
CREATE TABLE CATALOGO_ITEMS (
    id_item     TEXT PRIMARY KEY,
    nombre      TEXT NOT NULL,
    categoria   TEXT NOT NULL CHECK (categoria IN ('HEAL', 'KEY', 'POWERUP', 'AMULET', 'MISC')),
    curacion_hp INTEGER NOT NULL DEFAULT 0 CHECK (curacion_hp >= 0),
    descripcion TEXT NOT NULL DEFAULT ''
);
INSERT INTO CATALOGO_ITEMS VALUES('potion_small','Pocion Pequena','HEAL',25,'Restaura 25 HP');
INSERT INTO CATALOGO_ITEMS VALUES('potion_medium','Pocion Mediana','HEAL',50,'Restaura 50 HP');
INSERT INTO CATALOGO_ITEMS VALUES('potion_large','Pocion Grande','HEAL',90,'Restaura 90 HP');
INSERT INTO CATALOGO_ITEMS VALUES('key_exit','Llave Virtual','KEY',0,'Abre la puerta de salida de la realidad virtual');
INSERT INTO CATALOGO_ITEMS VALUES('powerup_reaction_plus','Chip +10ms','POWERUP',0,'+10ms ventana de reaccion, +25 pts coste curacion');
INSERT INTO CATALOGO_ITEMS VALUES('powerup_reaction_minus','Chip -7.5ms','POWERUP',0,'-7.5ms ventana de reaccion, -20 pts coste curacion');
INSERT INTO CATALOGO_ITEMS VALUES('powerup_invincibility','Escudo Unico','POWERUP',0,'Invencibilidad de un solo uso por pista');
INSERT INTO CATALOGO_ITEMS VALUES('amulet_healing','Amuleto de Curacion','AMULET',0,'Aumenta la curacion por notas consecutivas del 50% al 75% del dano recibido');
INSERT INTO CATALOGO_ITEMS VALUES('amulet_medallion_swamp','Medallon del Pantano','AMULET',0,'Eco del tercer escondite de La Gata en el Pantano');
INSERT INTO CATALOGO_ITEMS VALUES('amulet_medallion_ruins','Medallon del Ocaso','AMULET',0,'Eco del tercer escondite de La Gata en Playas y Ruinas');
CREATE TABLE CATALOGO_BATALLAS (
    id_batalla              TEXT PRIMARY KEY,
    boss_id                 TEXT NOT NULL UNIQUE,
    boss_nombre             TEXT NOT NULL,
    especie                 TEXT NOT NULL DEFAULT '',
    defeat_flag             TEXT NOT NULL UNIQUE,
    escena_combate_relativa TEXT NOT NULL,
    id_zona                 TEXT NOT NULL REFERENCES ZONAS(id_zona) ON UPDATE CASCADE ON DELETE RESTRICT,
    id_item_recompensa      TEXT NULL REFERENCES CATALOGO_ITEMS(id_item) ON UPDATE CASCADE ON DELETE SET NULL,
    es_historia_principal   INTEGER NOT NULL DEFAULT 1 CHECK (es_historia_principal IN (0, 1))
);
INSERT INTO CATALOGO_BATALLAS VALUES('batalla_swamp_1','boss_swamp_1','Kael','Gemelo 1','BOSS_SWAMP_1_DEFEATED','resources/songs/swamp_1_kael.scene','swamp',NULL,1);
INSERT INTO CATALOGO_BATALLAS VALUES('batalla_swamp_2','boss_swamp_2','Kael & Vesper','Gemelos','BOSS_SWAMP_2_DEFEATED','resources/songs/swamp_2_twins.scene','swamp','powerup_reaction_plus',1);
INSERT INTO CATALOGO_BATALLAS VALUES('batalla_swamp_3','boss_swamp_3','Grail','Oso + Buho','BOSS_SWAMP_3_DEFEATED','resources/songs/swamp_3_grail.scene','swamp','potion_large',1);
INSERT INTO CATALOGO_BATALLAS VALUES('batalla_beach_1','boss_beach_1','Kael & Vesper','Costa','BOSS_BEACH_1_DEFEATED','resources/songs/beach_1_twins.scene','beach_ruins','potion_medium',1);
INSERT INTO CATALOGO_BATALLAS VALUES('batalla_beach_2','boss_beach_2','Kael & Vesper','Ruinas','BOSS_BEACH_2_DEFEATED','resources/songs/beach_2_twins.scene','beach_ruins','amulet_healing',1);
INSERT INTO CATALOGO_BATALLAS VALUES('batalla_beach_3','boss_grail','Grail','Oso + Buho','BOSS_BEACH_3_DEFEATED','resources/songs/grail_sanctum.scene','beach_ruins','powerup_reaction_minus',1);
INSERT INTO CATALOGO_BATALLAS VALUES('batalla_cat_7','boss_cat','La Gata','Felino','BOSS_CAT_FINAL_DEFEATED','resources/songs/glass beach.scene','hub',NULL,1);
INSERT INTO CATALOGO_BATALLAS VALUES('batalla_bear','boss_bear','Oso','Oso Guerrero','BOSS_RABY_DEFEATED','resources/songs/fiend - 2DF2.scene','beach_ruins','key_exit',0);
INSERT INTO CATALOGO_BATALLAS VALUES('batalla_nash','boss_nash','Nash','Perico + Hiena','BOSS_NASH_DEFEATED','resources/songs/nash_storm.scene','beach_ruins','potion_large',0);
CREATE TABLE PARTIDAS (
    id_partida          INTEGER PRIMARY KEY AUTOINCREMENT,
    slot                INTEGER NOT NULL UNIQUE CHECK (slot BETWEEN 1 AND 4),
    id_zona_actual      TEXT NOT NULL REFERENCES ZONAS(id_zona) ON UPDATE CASCADE ON DELETE RESTRICT,
    player_x            REAL NOT NULL DEFAULT 256.0,
    player_y            REAL NOT NULL DEFAULT 224.0,
    id_zona_retorno     TEXT NOT NULL REFERENCES ZONAS(id_zona) ON UPDATE CASCADE ON DELETE RESTRICT,
    return_x            REAL NOT NULL DEFAULT 256.0,
    return_y            REAL NOT NULL DEFAULT 224.0,
    heal_strategy       TEXT NOT NULL DEFAULT 'OPTIMAL' CHECK (heal_strategy IN ('OPTIMAL', 'MINIMAL')),
    estado_general      TEXT NOT NULL DEFAULT 'EN_PROGRESO' CHECK (estado_general IN ('EN_PROGRESO', 'FINAL_NORMAL', 'FINAL_VERDADERO', 'BUCLE_AGOTADO')),
    progreso_porcentaje REAL NOT NULL DEFAULT 0.0 CHECK (progreso_porcentaje >= 0.0 AND progreso_porcentaje <= 100.0),
    fecha_creacion      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO PARTIDAS VALUES(1,1,'swamp',640,720,'swamp',640,720,'OPTIMAL','EN_PROGRESO',28.6,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO PARTIDAS VALUES(2,2,'beach_ruins',1824,960,'beach_ruins',1824,960,'MINIMAL','BUCLE_AGOTADO',85.7,'2026-11-16 20:17:53','2026-11-16 20:17:53');
CREATE TABLE BATALLAS_VENCIDAS (
    id_partida              INTEGER NOT NULL REFERENCES PARTIDAS(id_partida) ON DELETE CASCADE,
    id_batalla              TEXT NOT NULL REFERENCES CATALOGO_BATALLAS(id_batalla) ON UPDATE CASCADE ON DELETE RESTRICT,
    puntaje_maximo          INTEGER NOT NULL DEFAULT 0 CHECK (puntaje_maximo >= 0),
    max_combo               INTEGER NOT NULL DEFAULT 0 CHECK (max_combo >= 0),
    perfects                INTEGER NOT NULL DEFAULT 0 CHECK (perfects >= 0),
    goods                   INTEGER NOT NULL DEFAULT 0 CHECK (goods >= 0),
    safes                   INTEGER NOT NULL DEFAULT 0 CHECK (safes >= 0),
    misses                  INTEGER NOT NULL DEFAULT 0 CHECK (misses >= 0),
    intentos_totales        INTEGER NOT NULL DEFAULT 1 CHECK (intentos_totales >= 1),
    fecha_primera_victoria  TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_ultima_victoria   TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_partida, id_batalla)
);
INSERT INTO BATALLAS_VENCIDAS VALUES(1,'batalla_swamp_1',14200,92,110,14,4,2,1,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO BATALLAS_VENCIDAS VALUES(1,'batalla_swamp_2',18550,128,165,22,6,3,2,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO BATALLAS_VENCIDAS VALUES(2,'batalla_swamp_1',16800,115,120,8,2,0,1,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO BATALLAS_VENCIDAS VALUES(2,'batalla_swamp_2',21400,152,180,14,2,1,1,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO BATALLAS_VENCIDAS VALUES(2,'batalla_swamp_3',24900,174,210,18,4,2,2,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO BATALLAS_VENCIDAS VALUES(2,'batalla_beach_1',26100,188,225,16,3,1,1,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO BATALLAS_VENCIDAS VALUES(2,'batalla_beach_2',29300,210,240,12,2,1,1,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO BATALLAS_VENCIDAS VALUES(2,'batalla_beach_3',33800,245,290,15,3,0,1,'2026-11-16 20:17:53','2026-11-16 20:17:53');
CREATE TABLE OBJETOS_OBTENIDOS (
    id_partida          INTEGER NOT NULL REFERENCES PARTIDAS(id_partida) ON DELETE CASCADE,
    id_item             TEXT NOT NULL REFERENCES CATALOGO_ITEMS(id_item) ON UPDATE CASCADE ON DELETE RESTRICT,
    cantidad            INTEGER NOT NULL DEFAULT 1 CHECK (cantidad >= 0),
    equipado            INTEGER NOT NULL DEFAULT 0 CHECK (equipado IN (0, 1)),
    fecha_obtencion     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_partida, id_item)
);
INSERT INTO OBJETOS_OBTENIDOS VALUES(1,'potion_small',3,0,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO OBJETOS_OBTENIDOS VALUES(1,'powerup_reaction_plus',1,1,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO OBJETOS_OBTENIDOS VALUES(1,'powerup_invincibility',1,0,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO OBJETOS_OBTENIDOS VALUES(2,'potion_medium',2,0,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO OBJETOS_OBTENIDOS VALUES(2,'potion_large',1,0,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO OBJETOS_OBTENIDOS VALUES(2,'key_exit',1,0,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO OBJETOS_OBTENIDOS VALUES(2,'amulet_healing',1,1,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO OBJETOS_OBTENIDOS VALUES(2,'powerup_reaction_minus',1,1,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO OBJETOS_OBTENIDOS VALUES(2,'amulet_medallion_swamp',1,0,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO OBJETOS_OBTENIDOS VALUES(2,'amulet_medallion_ruins',1,0,'2026-11-16 20:17:53','2026-11-16 20:17:53');
CREATE TABLE HISTORIAL_OBJETOS (
    id_historial    INTEGER PRIMARY KEY AUTOINCREMENT,
    id_partida      INTEGER NOT NULL REFERENCES PARTIDAS(id_partida) ON DELETE CASCADE,
    id_item         TEXT NOT NULL REFERENCES CATALOGO_ITEMS(id_item) ON UPDATE CASCADE ON DELETE RESTRICT,
    evento          TEXT NOT NULL CHECK (evento IN ('OBTENIDO', 'USADO', 'EQUIPADO', 'DESEQUIPADO')),
    cantidad_delta  INTEGER NOT NULL,
    fecha_evento    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO HISTORIAL_OBJETOS VALUES(1,1,'potion_small','OBTENIDO',3,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(2,1,'powerup_reaction_plus','OBTENIDO',1,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(3,1,'powerup_reaction_plus','EQUIPADO',0,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(4,1,'powerup_invincibility','OBTENIDO',1,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(5,2,'potion_medium','OBTENIDO',2,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(6,2,'potion_large','OBTENIDO',2,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(7,2,'key_exit','OBTENIDO',1,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(8,2,'amulet_healing','OBTENIDO',1,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(9,2,'amulet_healing','EQUIPADO',0,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(10,2,'powerup_reaction_minus','OBTENIDO',1,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(11,2,'powerup_reaction_minus','EQUIPADO',0,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(12,2,'amulet_medallion_swamp','OBTENIDO',1,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(13,2,'amulet_medallion_ruins','OBTENIDO',1,'2026-11-16 20:17:53');
INSERT INTO HISTORIAL_OBJETOS VALUES(14,2,'potion_large','USADO',-1,'2026-11-16 20:17:53');
CREATE TABLE FLAGS_PARTIDA (
    id_partida       INTEGER NOT NULL REFERENCES PARTIDAS(id_partida) ON DELETE CASCADE,
    flag_nombre      TEXT NOT NULL,
    fecha_definicion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_partida, flag_nombre)
);
INSERT INTO FLAGS_PARTIDA VALUES(1,'HUB_INTRO_DONE','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(1,'SWAMP_ENTERED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(1,'BOSS_SWAMP_1_DEFEATED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(1,'BOSS_SWAMP_2_DEFEATED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(1,'CAT_SWAMP_1','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'HUB_INTRO_DONE','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'SWAMP_ENTERED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'BOSS_SWAMP_1_DEFEATED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'BOSS_SWAMP_2_DEFEATED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'BOSS_SWAMP_3_DEFEATED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'BOSS_BEACH_1_DEFEATED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'BOSS_BEACH_2_DEFEATED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'BOSS_BEACH_3_DEFEATED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'SWAMP_MEDALLION_TAKEN','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'RUINS_MEDALLION_TAKEN','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'ROUTE_TRUE_UNLOCKED','2026-11-16 20:17:53');
INSERT INTO FLAGS_PARTIDA VALUES(2,'LOOP_EXHAUSTED_MODE','2026-11-16 20:17:53');
CREATE TABLE CANCIONES_PERSONALIZADAS (
    id_cancion               INTEGER PRIMARY KEY AUTOINCREMENT,
    clave_unica              TEXT NOT NULL UNIQUE,
    titulo                   TEXT NOT NULL,
    artista                  TEXT NOT NULL DEFAULT '',
    dificultad               TEXT NOT NULL DEFAULT '6K',
    bpm                      REAL NOT NULL DEFAULT 120.0 CHECK (bpm > 0.0),
    ruta_portada             TEXT NOT NULL DEFAULT '',
    ruta_audio               TEXT NOT NULL DEFAULT '',
    ruta_chart               TEXT NOT NULL,
    cantidad_notas           INTEGER NOT NULL DEFAULT 0 CHECK (cantidad_notas >= 0),
    puntaje_ultimo_intento   INTEGER NOT NULL DEFAULT 0 CHECK (puntaje_ultimo_intento >= 0),
    combo_ultimo_intento     INTEGER NOT NULL DEFAULT 0 CHECK (combo_ultimo_intento >= 0),
    precision_ultimo_intento REAL NOT NULL DEFAULT 0.0 CHECK (precision_ultimo_intento >= 0.0 AND precision_ultimo_intento <= 100.0),
    puntaje_mas_alto         INTEGER NOT NULL DEFAULT 0 CHECK (puntaje_mas_alto >= 0),
    combo_mas_alto           INTEGER NOT NULL DEFAULT 0 CHECK (combo_mas_alto >= 0),
    precision_mas_alta       REAL NOT NULL DEFAULT 0.0 CHECK (precision_mas_alta >= 0.0 AND precision_mas_alta <= 100.0),
    veces_jugada             INTEGER NOT NULL DEFAULT 0 CHECK (veces_jugada >= 0),
    fecha_ultimo_intento     TEXT NULL,
    fecha_record             TEXT NULL
);
INSERT INTO CANCIONES_PERSONALIZADAS VALUES(1,'resources/songs/custom/fiend - 2DF2 [Custom 6K].osu','2DF2 (Custom)','fiend','Custom 6K Normal',155,'resources/songs/a1/cover.jpg','resources/songs/a1/audio.wav','resources/songs/custom/fiend - 2DF2 [Custom 6K].osu',178,9800,64,84.5,15400,112,96.8,2,'2026-11-16 20:17:53','2026-11-16 20:17:53');
INSERT INTO CANCIONES_PERSONALIZADAS VALUES(2,'resources/songs/custom/glass beach - cold weather [Custom 6K].osu','cold weather (Custom)','glass beach','Custom 6K Hard',155,'resources/sprites/backgrounds/debug/beach_ruins.png','resources/songs/audio.wav','resources/songs/custom/glass beach - cold weather [Custom 6K].osu',1362,28900,195,94.2,28900,195,94.2,1,'2026-11-16 20:17:53','2026-11-16 20:17:53');
CREATE TABLE CANCION_OBJETOS_INTENTO (
    id_cancion    INTEGER NOT NULL REFERENCES CANCIONES_PERSONALIZADAS(id_cancion) ON DELETE CASCADE,
    tipo_registro TEXT NOT NULL CHECK (tipo_registro IN ('ULTIMO', 'MEJOR')),
    id_item       TEXT NOT NULL REFERENCES CATALOGO_ITEMS(id_item) ON UPDATE CASCADE ON DELETE RESTRICT,
    PRIMARY KEY (id_cancion, tipo_registro, id_item)
);
INSERT INTO CANCION_OBJETOS_INTENTO VALUES(1,'MEJOR','powerup_reaction_plus');
INSERT INTO CANCION_OBJETOS_INTENTO VALUES(1,'ULTIMO','powerup_invincibility');
INSERT INTO CANCION_OBJETOS_INTENTO VALUES(2,'ULTIMO','powerup_reaction_minus');
INSERT INTO CANCION_OBJETOS_INTENTO VALUES(2,'ULTIMO','amulet_healing');
INSERT INTO CANCION_OBJETOS_INTENTO VALUES(2,'MEJOR','amulet_healing');
INSERT INTO CANCION_OBJETOS_INTENTO VALUES(2,'MEJOR','powerup_reaction_minus');
PRAGMA writable_schema=ON;
CREATE TABLE IF NOT EXISTS sqlite_sequence(name,seq);
DELETE FROM sqlite_sequence;
INSERT INTO sqlite_sequence VALUES('PARTIDAS',2);
INSERT INTO sqlite_sequence VALUES('HISTORIAL_OBJETOS',14);
INSERT INTO sqlite_sequence VALUES('CANCIONES_PERSONALIZADAS',2);
CREATE VIEW v_resumen_partidas AS
SELECT
    p.id_partida,
    p.slot,
    z.id_zona,
    z.nombre AS zona_actual,
    z.escena_xml_relativa AS escena_actual,
    p.player_x,
    p.player_y,
    zr.escena_xml_relativa AS return_scene,
    p.return_x,
    p.return_y,
    p.heal_strategy,
    p.estado_general,
    p.progreso_porcentaje,
    p.fecha_creacion,
    p.fecha_actualizacion,
    COALESCE((
        SELECT SUM(oo.cantidad)
        FROM OBJETOS_OBTENIDOS oo
        WHERE oo.id_partida = p.id_partida
    ), 0) AS total_items,
    COALESCE((
        SELECT SUM(oo.cantidad)
        FROM OBJETOS_OBTENIDOS oo
        INNER JOIN CATALOGO_ITEMS ci ON ci.id_item = oo.id_item
        WHERE oo.id_partida = p.id_partida
          AND ci.categoria IN ('POWERUP', 'AMULET')
    ), 0) AS total_amuletos,
    COALESCE((
        SELECT COUNT(*)
        FROM BATALLAS_VENCIDAS bv
        WHERE bv.id_partida = p.id_partida
    ), 0) AS total_batallas_vencidas
FROM PARTIDAS p
INNER JOIN ZONAS z ON z.id_zona = p.id_zona_actual
INNER JOIN ZONAS zr ON zr.id_zona = p.id_zona_retorno;
CREATE VIEW v_mejores_puntajes_batallas AS
SELECT
    p.slot AS slot_partida,
    p.id_partida,
    cb.id_batalla,
    cb.boss_id,
    cb.boss_nombre,
    cb.especie,
    z.nombre AS zona_batalla,
    bv.puntaje_maximo,
    bv.max_combo,
    bv.perfects,
    bv.goods,
    bv.safes,
    bv.misses,
    bv.intentos_totales,
    bv.fecha_primera_victoria,
    bv.fecha_ultima_victoria
FROM BATALLAS_VENCIDAS bv
INNER JOIN PARTIDAS p ON p.id_partida = bv.id_partida
INNER JOIN CATALOGO_BATALLAS cb ON cb.id_batalla = bv.id_batalla
INNER JOIN ZONAS z ON z.id_zona = cb.id_zona;
CREATE VIEW v_inventario_detallado AS
SELECT
    p.slot AS slot_partida,
    p.id_partida,
    ci.id_item,
    ci.nombre AS item_nombre,
    ci.categoria,
    ci.curacion_hp,
    ci.descripcion,
    oo.cantidad,
    oo.equipado,
    oo.fecha_obtencion,
    oo.fecha_actualizacion
FROM OBJETOS_OBTENIDOS oo
INNER JOIN PARTIDAS p ON p.id_partida = oo.id_partida
INNER JOIN CATALOGO_ITEMS ci ON ci.id_item = oo.id_item;
CREATE VIEW v_historial_objetos AS
SELECT
    ho.id_historial,
    p.slot AS slot_partida,
    p.id_partida,
    ci.id_item,
    ci.nombre AS item_nombre,
    ci.categoria,
    ho.evento,
    ho.cantidad_delta,
    ho.fecha_evento
FROM HISTORIAL_OBJETOS ho
INNER JOIN PARTIDAS p ON p.id_partida = ho.id_partida
INNER JOIN CATALOGO_ITEMS ci ON ci.id_item = ho.id_item
ORDER BY ho.id_historial DESC;
CREATE VIEW v_canciones_custom_objetos AS
SELECT
    cp.id_cancion,
    cp.clave_unica,
    cp.titulo,
    cp.artista,
    cp.dificultad,
    cp.bpm,
    cp.puntaje_mas_alto,
    cp.puntaje_ultimo_intento,
    coi.tipo_registro,
    ci.id_item,
    ci.nombre AS item_nombre,
    ci.categoria
FROM CANCION_OBJETOS_INTENTO coi
INNER JOIN CANCIONES_PERSONALIZADAS cp ON cp.id_cancion = coi.id_cancion
INNER JOIN CATALOGO_ITEMS ci ON ci.id_item = coi.id_item;
CREATE VIEW v_estadisticas_canciones_custom AS
SELECT
    cp.id_cancion,
    cp.clave_unica,
    cp.titulo,
    cp.artista,
    cp.dificultad,
    cp.bpm,
    cp.ruta_portada,
    cp.ruta_audio,
    cp.ruta_chart,
    cp.cantidad_notas,
    cp.puntaje_ultimo_intento,
    cp.combo_ultimo_intento,
    cp.precision_ultimo_intento,
    cp.puntaje_mas_alto,
    cp.combo_mas_alto,
    cp.precision_mas_alta,
    cp.veces_jugada,
    cp.fecha_ultimo_intento,
    cp.fecha_record,
    COALESCE((
        SELECT GROUP_CONCAT(ci.nombre, ', ')
        FROM CANCION_OBJETOS_INTENTO coi
        INNER JOIN CATALOGO_ITEMS ci ON ci.id_item = coi.id_item
        WHERE coi.id_cancion = cp.id_cancion
          AND coi.tipo_registro = 'MEJOR'
    ), 'Ninguno') AS objetos_record,
    COALESCE((
        SELECT GROUP_CONCAT(ci.nombre, ', ')
        FROM CANCION_OBJETOS_INTENTO coi
        INNER JOIN CATALOGO_ITEMS ci ON ci.id_item = coi.id_item
        WHERE coi.id_cancion = cp.id_cancion
          AND coi.tipo_registro = 'ULTIMO'
    ), 'Ninguno') AS objetos_ultimo_intento
FROM CANCIONES_PERSONALIZADAS cp
ORDER BY cp.id_cancion ASC;
CREATE TRIGGER trg_canciones_validar_insert
BEFORE INSERT ON CANCIONES_PERSONALIZADAS
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN NEW.bpm <= 0.0 THEN RAISE(ABORT, 'El BPM de la canción custom debe ser mayor a 0')
        WHEN NEW.puntaje_ultimo_intento < 0 OR NEW.puntaje_mas_alto < 0 THEN RAISE(ABORT, 'Los puntajes no pueden ser negativos')
        WHEN NEW.clave_unica LIKE '/%' OR NEW.clave_unica GLOB '[A-Za-z]:*' THEN RAISE(ABORT, 'clave_unica debe usar ruta relativa')
        WHEN NEW.ruta_chart LIKE '/%' OR NEW.ruta_chart GLOB '[A-Za-z]:*' THEN RAISE(ABORT, 'ruta_chart debe usar ruta relativa')
        WHEN NEW.ruta_portada LIKE '/%' OR NEW.ruta_portada GLOB '[A-Za-z]:*' THEN RAISE(ABORT, 'ruta_portada debe usar ruta relativa')
        WHEN NEW.ruta_audio LIKE '/%' OR NEW.ruta_audio GLOB '[A-Za-z]:*' THEN RAISE(ABORT, 'ruta_audio debe usar ruta relativa')
    END;
END;
CREATE TRIGGER trg_canciones_validar_update
BEFORE UPDATE ON CANCIONES_PERSONALIZADAS
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN NEW.bpm <= 0.0 THEN RAISE(ABORT, 'El BPM de la canción custom debe ser mayor a 0')
        WHEN NEW.puntaje_ultimo_intento < 0 OR NEW.puntaje_mas_alto < 0 THEN RAISE(ABORT, 'Los puntajes no pueden ser negativos')
        WHEN NEW.clave_unica LIKE '/%' OR NEW.clave_unica GLOB '[A-Za-z]:*' THEN RAISE(ABORT, 'clave_unica debe usar ruta relativa')
        WHEN NEW.ruta_chart LIKE '/%' OR NEW.ruta_chart GLOB '[A-Za-z]:*' THEN RAISE(ABORT, 'ruta_chart debe usar ruta relativa')
        WHEN NEW.ruta_portada LIKE '/%' OR NEW.ruta_portada GLOB '[A-Za-z]:*' THEN RAISE(ABORT, 'ruta_portada debe usar ruta relativa')
        WHEN NEW.ruta_audio LIKE '/%' OR NEW.ruta_audio GLOB '[A-Za-z]:*' THEN RAISE(ABORT, 'ruta_audio debe usar ruta relativa')
    END;
END;
CREATE TRIGGER trg_canciones_record_after_insert
AFTER INSERT ON CANCIONES_PERSONALIZADAS
FOR EACH ROW
WHEN NEW.puntaje_ultimo_intento > COALESCE(NEW.puntaje_mas_alto, 0)
BEGIN
    UPDATE CANCIONES_PERSONALIZADAS
    SET puntaje_mas_alto   = NEW.puntaje_ultimo_intento,
        combo_mas_alto     = NEW.combo_ultimo_intento,
        precision_mas_alta = NEW.precision_ultimo_intento,
        fecha_record       = COALESCE(NEW.fecha_ultimo_intento, CURRENT_TIMESTAMP)
    WHERE id_cancion = NEW.id_cancion;
END;
CREATE TRIGGER trg_canciones_record_after_update
AFTER UPDATE OF puntaje_ultimo_intento, combo_ultimo_intento, precision_ultimo_intento, veces_jugada
ON CANCIONES_PERSONALIZADAS
FOR EACH ROW
WHEN NEW.puntaje_ultimo_intento > COALESCE(OLD.puntaje_mas_alto, 0)
  OR (OLD.veces_jugada = 0 AND NEW.veces_jugada > 0 AND NEW.puntaje_mas_alto = 0)
BEGIN
    UPDATE CANCIONES_PERSONALIZADAS
    SET puntaje_mas_alto   = NEW.puntaje_ultimo_intento,
        combo_mas_alto     = NEW.combo_ultimo_intento,
        precision_mas_alta = NEW.precision_ultimo_intento,
        fecha_record       = COALESCE(NEW.fecha_ultimo_intento, CURRENT_TIMESTAMP)
    WHERE id_cancion = NEW.id_cancion;

    DELETE FROM CANCION_OBJETOS_INTENTO
    WHERE id_cancion = NEW.id_cancion
      AND tipo_registro = 'MEJOR';

    INSERT INTO CANCION_OBJETOS_INTENTO (id_cancion, tipo_registro, id_item)
    SELECT NEW.id_cancion, 'MEJOR', id_item
    FROM CANCION_OBJETOS_INTENTO
    WHERE id_cancion = NEW.id_cancion
      AND tipo_registro = 'ULTIMO';
END;
CREATE TRIGGER trg_objetos_validar_insert
BEFORE INSERT ON OBJETOS_OBTENIDOS
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN NEW.cantidad < 0 THEN RAISE(ABORT, 'La cantidad de un objeto no puede ser negativa')
    END;
END;
CREATE TRIGGER trg_objetos_validar_update
BEFORE UPDATE ON OBJETOS_OBTENIDOS
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN NEW.cantidad < 0 THEN RAISE(ABORT, 'La cantidad de un objeto no puede ser negativa')
    END;
END;
CREATE TRIGGER trg_objetos_audit_insert
AFTER INSERT ON OBJETOS_OBTENIDOS
FOR EACH ROW
BEGIN
    INSERT INTO HISTORIAL_OBJETOS (id_partida, id_item, evento, cantidad_delta)
    SELECT NEW.id_partida, NEW.id_item, 'OBTENIDO', NEW.cantidad
    WHERE NEW.cantidad > 0;

    INSERT INTO HISTORIAL_OBJETOS (id_partida, id_item, evento, cantidad_delta)
    SELECT NEW.id_partida, NEW.id_item, 'EQUIPADO', 0
    WHERE NEW.equipado = 1;

    UPDATE PARTIDAS
    SET fecha_actualizacion = CURRENT_TIMESTAMP
    WHERE id_partida = NEW.id_partida;
END;
CREATE TRIGGER trg_objetos_audit_update
AFTER UPDATE OF cantidad, equipado ON OBJETOS_OBTENIDOS
FOR EACH ROW
BEGIN
    INSERT INTO HISTORIAL_OBJETOS (id_partida, id_item, evento, cantidad_delta)
    SELECT NEW.id_partida, NEW.id_item, 'OBTENIDO', NEW.cantidad - OLD.cantidad
    WHERE NEW.cantidad > OLD.cantidad;

    INSERT INTO HISTORIAL_OBJETOS (id_partida, id_item, evento, cantidad_delta)
    SELECT NEW.id_partida, NEW.id_item, 'USADO', NEW.cantidad - OLD.cantidad
    WHERE NEW.cantidad < OLD.cantidad;

    INSERT INTO HISTORIAL_OBJETOS (id_partida, id_item, evento, cantidad_delta)
    SELECT NEW.id_partida, NEW.id_item, 'EQUIPADO', 0
    WHERE OLD.equipado = 0 AND NEW.equipado = 1;

    INSERT INTO HISTORIAL_OBJETOS (id_partida, id_item, evento, cantidad_delta)
    SELECT NEW.id_partida, NEW.id_item, 'DESEQUIPADO', 0
    WHERE OLD.equipado = 1 AND NEW.equipado = 0;

    UPDATE OBJETOS_OBTENIDOS
    SET fecha_actualizacion = CURRENT_TIMESTAMP
    WHERE id_partida = NEW.id_partida
      AND id_item = NEW.id_item
      AND NEW.cantidad > 0;

    DELETE FROM OBJETOS_OBTENIDOS
    WHERE id_partida = NEW.id_partida
      AND id_item = NEW.id_item
      AND NEW.cantidad <= 0;

    UPDATE PARTIDAS
    SET fecha_actualizacion = CURRENT_TIMESTAMP
    WHERE id_partida = NEW.id_partida;
END;
CREATE TRIGGER trg_batallas_progreso_insert
AFTER INSERT ON BATALLAS_VENCIDAS
FOR EACH ROW
BEGIN
    UPDATE PARTIDAS
    SET progreso_porcentaje = MIN(100.0, ROUND(
            COALESCE((
                SELECT COUNT(*) * 100.0 / MAX(1, (SELECT COUNT(*) FROM CATALOGO_BATALLAS WHERE es_historia_principal = 1))
                FROM BATALLAS_VENCIDAS bv
                INNER JOIN CATALOGO_BATALLAS cb ON cb.id_batalla = bv.id_batalla
                WHERE bv.id_partida = NEW.id_partida AND cb.es_historia_principal = 1
            ), 0.0), 1
        )),
        fecha_actualizacion = CURRENT_TIMESTAMP
    WHERE id_partida = NEW.id_partida;
END;
CREATE TRIGGER trg_batallas_progreso_update
AFTER UPDATE ON BATALLAS_VENCIDAS
FOR EACH ROW
BEGIN
    UPDATE PARTIDAS
    SET progreso_porcentaje = MIN(100.0, ROUND(
            COALESCE((
                SELECT COUNT(*) * 100.0 / MAX(1, (SELECT COUNT(*) FROM CATALOGO_BATALLAS WHERE es_historia_principal = 1))
                FROM BATALLAS_VENCIDAS bv
                INNER JOIN CATALOGO_BATALLAS cb ON cb.id_batalla = bv.id_batalla
                WHERE bv.id_partida = NEW.id_partida AND cb.es_historia_principal = 1
            ), 0.0), 1
        )),
        fecha_actualizacion = CURRENT_TIMESTAMP
    WHERE id_partida = NEW.id_partida;
END;
CREATE INDEX idx_historial_partida_fecha
    ON HISTORIAL_OBJETOS(id_partida, fecha_evento DESC);
CREATE INDEX idx_batallas_vencidas_partida
    ON BATALLAS_VENCIDAS(id_partida);
CREATE INDEX idx_canciones_custom_titulo
    ON CANCIONES_PERSONALIZADAS(titulo);
PRAGMA writable_schema=OFF;
COMMIT;
