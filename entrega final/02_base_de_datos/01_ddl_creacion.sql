-- =============================================================================
-- Combo 6Beat — Esquema Relacional SQLite (Tercera Forma Normal - 3FN)
-- Tablas principales, catálogos normalizados, triggers de auditoría/récord y vistas.
-- =============================================================================

PRAGMA foreign_keys = ON;

-- -----------------------------------------------------------------------------
-- 1. Catálogos Base (3FN: sin dependencias transitivas en tablas operativas)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS DB_META (
    clave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ZONAS (
    id_zona             TEXT PRIMARY KEY,
    nombre              TEXT NOT NULL,
    escena_xml_relativa TEXT NOT NULL UNIQUE,
    orden_progresion    INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS CATALOGO_ITEMS (
    id_item     TEXT PRIMARY KEY,
    nombre      TEXT NOT NULL,
    categoria   TEXT NOT NULL CHECK (categoria IN ('HEAL', 'KEY', 'POWERUP', 'AMULET', 'MISC')),
    curacion_hp INTEGER NOT NULL DEFAULT 0 CHECK (curacion_hp >= 0),
    descripcion TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS CATALOGO_BATALLAS (
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

-- -----------------------------------------------------------------------------
-- 2. Módulo de Partidas (Historia RPG + Progreso)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS PARTIDAS (
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

CREATE TABLE IF NOT EXISTS BATALLAS_VENCIDAS (
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

CREATE TABLE IF NOT EXISTS OBJETOS_OBTENIDOS (
    id_partida          INTEGER NOT NULL REFERENCES PARTIDAS(id_partida) ON DELETE CASCADE,
    id_item             TEXT NOT NULL REFERENCES CATALOGO_ITEMS(id_item) ON UPDATE CASCADE ON DELETE RESTRICT,
    cantidad            INTEGER NOT NULL DEFAULT 1 CHECK (cantidad >= 0),
    equipado            INTEGER NOT NULL DEFAULT 0 CHECK (equipado IN (0, 1)),
    fecha_obtencion     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_partida, id_item)
);

CREATE TABLE IF NOT EXISTS HISTORIAL_OBJETOS (
    id_historial    INTEGER PRIMARY KEY AUTOINCREMENT,
    id_partida      INTEGER NOT NULL REFERENCES PARTIDAS(id_partida) ON DELETE CASCADE,
    id_item         TEXT NOT NULL REFERENCES CATALOGO_ITEMS(id_item) ON UPDATE CASCADE ON DELETE RESTRICT,
    evento          TEXT NOT NULL CHECK (evento IN ('OBTENIDO', 'USADO', 'EQUIPADO', 'DESEQUIPADO')),
    cantidad_delta  INTEGER NOT NULL,
    fecha_evento    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS FLAGS_PARTIDA (
    id_partida       INTEGER NOT NULL REFERENCES PARTIDAS(id_partida) ON DELETE CASCADE,
    flag_nombre      TEXT NOT NULL,
    fecha_definicion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_partida, flag_nombre)
);

-- -----------------------------------------------------------------------------
-- 3. Módulo Independiente de Canciones Personalizadas
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS CANCIONES_PERSONALIZADAS (
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

-- 3FN: los ítems/chips empleados en el último intento y los resguardados en el récord
-- se almacenan de forma atómica (sin listas concatenadas).
CREATE TABLE IF NOT EXISTS CANCION_OBJETOS_INTENTO (
    id_cancion    INTEGER NOT NULL REFERENCES CANCIONES_PERSONALIZADAS(id_cancion) ON DELETE CASCADE,
    tipo_registro TEXT NOT NULL CHECK (tipo_registro IN ('ULTIMO', 'MEJOR')),
    id_item       TEXT NOT NULL REFERENCES CATALOGO_ITEMS(id_item) ON UPDATE CASCADE ON DELETE RESTRICT,
    PRIMARY KEY (id_cancion, tipo_registro, id_item)
);

-- -----------------------------------------------------------------------------
-- 4. Índices de Soporte
-- -----------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_historial_partida_fecha
    ON HISTORIAL_OBJETOS(id_partida, fecha_evento DESC);

CREATE INDEX IF NOT EXISTS idx_batallas_vencidas_partida
    ON BATALLAS_VENCIDAS(id_partida);

CREATE INDEX IF NOT EXISTS idx_canciones_custom_titulo
    ON CANCIONES_PERSONALIZADAS(titulo);

-- -----------------------------------------------------------------------------
-- 5. Triggers SQLite (Validación BEFORE + Récord y Auditoría Automática AFTER)
-- -----------------------------------------------------------------------------

-- 5.1 Validación de integridad en CANCIONES_PERSONALIZADAS
CREATE TRIGGER IF NOT EXISTS trg_canciones_validar_insert
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

CREATE TRIGGER IF NOT EXISTS trg_canciones_validar_update
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

-- 5.2 Promoción automática de récord y resguardo de objetos en CANCIONES_PERSONALIZADAS
CREATE TRIGGER IF NOT EXISTS trg_canciones_record_after_insert
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

CREATE TRIGGER IF NOT EXISTS trg_canciones_record_after_update
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

-- 5.3 Validación y auditoría automática en OBJETOS_OBTENIDOS
CREATE TRIGGER IF NOT EXISTS trg_objetos_validar_insert
BEFORE INSERT ON OBJETOS_OBTENIDOS
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN NEW.cantidad < 0 THEN RAISE(ABORT, 'La cantidad de un objeto no puede ser negativa')
    END;
END;

CREATE TRIGGER IF NOT EXISTS trg_objetos_validar_update
BEFORE UPDATE ON OBJETOS_OBTENIDOS
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN NEW.cantidad < 0 THEN RAISE(ABORT, 'La cantidad de un objeto no puede ser negativa')
    END;
END;

CREATE TRIGGER IF NOT EXISTS trg_objetos_audit_insert
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

CREATE TRIGGER IF NOT EXISTS trg_objetos_audit_update
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

-- 5.4 Actualización automática de progreso global en PARTIDAS al vencer batallas
CREATE TRIGGER IF NOT EXISTS trg_batallas_progreso_insert
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

CREATE TRIGGER IF NOT EXISTS trg_batallas_progreso_update
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

-- -----------------------------------------------------------------------------
-- 6. Vistas SQL (INNER JOIN para consultas rápidas desde el juego)
-- -----------------------------------------------------------------------------

CREATE VIEW IF NOT EXISTS v_resumen_partidas AS
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

CREATE VIEW IF NOT EXISTS v_mejores_puntajes_batallas AS
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

CREATE VIEW IF NOT EXISTS v_inventario_detallado AS
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

CREATE VIEW IF NOT EXISTS v_historial_objetos AS
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

CREATE VIEW IF NOT EXISTS v_canciones_custom_objetos AS
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

CREATE VIEW IF NOT EXISTS v_estadisticas_canciones_custom AS
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
