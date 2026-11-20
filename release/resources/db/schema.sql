-- =============================================================================
-- Combo 6Beat — Esquema SQLite en 3FN (Modo Rítmico + Tienda de Amuletos)
-- Tablas principales, catálogos normalizados, triggers de auditoría/récord y vistas.
-- =============================================================================

PRAGMA foreign_keys = ON;

-- 1. Catálogos Base

CREATE TABLE IF NOT EXISTS DB_META (
    clave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS CATALOGO_ITEMS (
    id_item       TEXT PRIMARY KEY,
    nombre        TEXT NOT NULL,
    categoria     TEXT NOT NULL CHECK (categoria IN ('HEAL', 'POWERUP', 'AMULET', 'MISC')),
    curacion_hp   INTEGER NOT NULL DEFAULT 0 CHECK (curacion_hp >= 0),
    precio_tienda INTEGER NOT NULL DEFAULT 0 CHECK (precio_tienda >= 0),
    descripcion   TEXT NOT NULL DEFAULT ''
);

-- 2. Módulo de Partidas, Récords de Canciones e Inventario

CREATE TABLE IF NOT EXISTS PARTIDAS (
    id_partida          INTEGER PRIMARY KEY AUTOINCREMENT,
    slot                INTEGER NOT NULL UNIQUE CHECK (slot BETWEEN 1 AND 4),
    puntos_tienda       INTEGER NOT NULL DEFAULT 0 CHECK (puntos_tienda >= 0),
    heal_strategy       TEXT NOT NULL DEFAULT 'OPTIMAL' CHECK (heal_strategy IN ('OPTIMAL', 'MINIMAL')),
    fecha_creacion      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS RECORDS_CANCIONES (
    id_partida           INTEGER NOT NULL REFERENCES PARTIDAS(id_partida) ON DELETE CASCADE,
    clave_cancion        TEXT NOT NULL,
    titulo               TEXT NOT NULL,
    artista              TEXT NOT NULL DEFAULT '',
    dificultad           TEXT NOT NULL DEFAULT '6K',
    puntaje_maximo       INTEGER NOT NULL DEFAULT 0 CHECK (puntaje_maximo >= 0),
    max_combo            INTEGER NOT NULL DEFAULT 0 CHECK (max_combo >= 0),
    perfects             INTEGER NOT NULL DEFAULT 0 CHECK (perfects >= 0),
    goods                INTEGER NOT NULL DEFAULT 0 CHECK (goods >= 0),
    safes                INTEGER NOT NULL DEFAULT 0 CHECK (safes >= 0),
    misses               INTEGER NOT NULL DEFAULT 0 CHECK (misses >= 0),
    intentos_totales     INTEGER NOT NULL DEFAULT 1 CHECK (intentos_totales >= 1),
    victorias_totales    INTEGER NOT NULL DEFAULT 0 CHECK (victorias_totales >= 0),
    fecha_ultimo_intento TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_partida, clave_cancion)
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

-- 3. Módulo Independiente de Canciones Personalizadas

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

CREATE TABLE IF NOT EXISTS CANCION_OBJETOS_INTENTO (
    id_cancion    INTEGER NOT NULL REFERENCES CANCIONES_PERSONALIZADAS(id_cancion) ON DELETE CASCADE,
    tipo_registro TEXT NOT NULL CHECK (tipo_registro IN ('ULTIMO', 'MEJOR')),
    id_item       TEXT NOT NULL REFERENCES CATALOGO_ITEMS(id_item) ON UPDATE CASCADE ON DELETE RESTRICT,
    PRIMARY KEY (id_cancion, tipo_registro, id_item)
);

-- 4. Índices de Soporte

CREATE INDEX IF NOT EXISTS idx_historial_partida_fecha
    ON HISTORIAL_OBJETOS(id_partida, fecha_evento DESC);

CREATE INDEX IF NOT EXISTS idx_records_canciones_partida
    ON RECORDS_CANCIONES(id_partida);

CREATE INDEX IF NOT EXISTS idx_canciones_custom_titulo
    ON CANCIONES_PERSONALIZADAS(titulo);

-- 5. Triggers SQLite

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

-- 5.4 Validación y actualización de fecha en RECORDS_CANCIONES
CREATE TRIGGER IF NOT EXISTS trg_records_validar_insert
BEFORE INSERT ON RECORDS_CANCIONES
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN NEW.puntaje_maximo < 0 THEN RAISE(ABORT, 'puntaje_maximo no puede ser negativo')
        WHEN NEW.max_combo < 0 THEN RAISE(ABORT, 'max_combo no puede ser negativo')
        WHEN NEW.intentos_totales < 1 THEN RAISE(ABORT, 'intentos_totales debe ser al menos 1')
        WHEN NEW.victorias_totales < 0 THEN RAISE(ABORT, 'victorias_totales no puede ser negativo')
        WHEN NEW.clave_cancion LIKE '/%' OR NEW.clave_cancion GLOB '[A-Za-z]:*' THEN RAISE(ABORT, 'clave_cancion debe usar ruta relativa')
    END;
END;

CREATE TRIGGER IF NOT EXISTS trg_records_validar_update
BEFORE UPDATE ON RECORDS_CANCIONES
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN NEW.puntaje_maximo < 0 THEN RAISE(ABORT, 'puntaje_maximo no puede ser negativo')
        WHEN NEW.max_combo < 0 THEN RAISE(ABORT, 'max_combo no puede ser negativo')
        WHEN NEW.intentos_totales < 1 THEN RAISE(ABORT, 'intentos_totales debe ser al menos 1')
        WHEN NEW.victorias_totales < 0 THEN RAISE(ABORT, 'victorias_totales no puede ser negativo')
        WHEN NEW.clave_cancion LIKE '/%' OR NEW.clave_cancion GLOB '[A-Za-z]:*' THEN RAISE(ABORT, 'clave_cancion debe usar ruta relativa')
    END;
END;

CREATE TRIGGER IF NOT EXISTS trg_records_touch_partida_insert
AFTER INSERT ON RECORDS_CANCIONES
FOR EACH ROW
BEGIN
    UPDATE PARTIDAS
    SET fecha_actualizacion = CURRENT_TIMESTAMP
    WHERE id_partida = NEW.id_partida;
END;

CREATE TRIGGER IF NOT EXISTS trg_records_touch_partida_update
AFTER UPDATE ON RECORDS_CANCIONES
FOR EACH ROW
BEGIN
    UPDATE PARTIDAS
    SET fecha_actualizacion = CURRENT_TIMESTAMP
    WHERE id_partida = NEW.id_partida;
END;

-- 6. Vistas SQL

DROP VIEW IF EXISTS v_resumen_partidas;
CREATE VIEW IF NOT EXISTS v_resumen_partidas AS
SELECT
    p.id_partida,
    p.slot,
    p.puntos_tienda,
    p.heal_strategy,
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
        FROM RECORDS_CANCIONES rc
        WHERE rc.id_partida = p.id_partida
    ), 0) AS canciones_jugadas,
    COALESCE((
        SELECT COUNT(*)
        FROM RECORDS_CANCIONES rc
        WHERE rc.id_partida = p.id_partida
          AND rc.victorias_totales > 0
    ), 0) AS canciones_completadas
FROM PARTIDAS p;

DROP VIEW IF EXISTS v_records_canciones;
CREATE VIEW IF NOT EXISTS v_records_canciones AS
SELECT
    p.slot AS slot_partida,
    p.id_partida,
    rc.clave_cancion,
    rc.titulo,
    rc.artista,
    rc.dificultad,
    rc.puntaje_maximo,
    rc.max_combo,
    rc.perfects,
    rc.goods,
    rc.safes,
    rc.misses,
    rc.intentos_totales,
    rc.victorias_totales,
    rc.fecha_ultimo_intento
FROM RECORDS_CANCIONES rc
INNER JOIN PARTIDAS p ON p.id_partida = rc.id_partida;

DROP VIEW IF EXISTS v_inventario_detallado;
CREATE VIEW IF NOT EXISTS v_inventario_detallado AS
SELECT
    p.slot AS slot_partida,
    p.id_partida,
    ci.id_item,
    ci.nombre AS item_nombre,
    ci.categoria,
    ci.curacion_hp,
    ci.precio_tienda,
    ci.descripcion,
    oo.cantidad,
    oo.equipado,
    oo.fecha_obtencion,
    oo.fecha_actualizacion
FROM OBJETOS_OBTENIDOS oo
INNER JOIN PARTIDAS p ON p.id_partida = oo.id_partida
INNER JOIN CATALOGO_ITEMS ci ON ci.id_item = oo.id_item;

DROP VIEW IF EXISTS v_historial_objetos;
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

DROP VIEW IF EXISTS v_canciones_custom_objetos;
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

DROP VIEW IF EXISTS v_estadisticas_canciones_custom;
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

-- 7. Datos Semilla del Catálogo de Ítems y Amuletos

INSERT INTO CATALOGO_ITEMS (id_item, nombre, categoria, curacion_hp, precio_tienda, descripcion) VALUES
    ('potion_small',           'Pocion Pequena',            'HEAL',   25, 0,    'Restaura 25 HP'),
    ('potion_medium',          'Pocion Mediana',            'HEAL',   50, 0,    'Restaura 50 HP'),
    ('potion_large',           'Pocion Grande',             'HEAL',   90, 0,    'Restaura 90 HP'),
    ('powerup_reaction_plus',  'Amuleto de Calma (+10ms)',  'AMULET', 0,  1200, 'Amplia +10ms las ventanas de acierto PERFECT, GOOD y SAFE.'),
    ('powerup_reaction_minus', 'Amuleto de Filo (-7.5ms)',  'AMULET', 0,  1500, 'Reduce -7.5ms las ventanas de juicio a cambio de +25% de puntaje y puntos.'),
    ('amulet_dual_slot',       'Relicario Gemelo',          'AMULET', 0,  1800, 'Te permite equipar 2 amuletos adicionales al mismo tiempo.'),
    ('amulet_healing',         'Amuleto de Curacion',       'AMULET', 0,  2200, 'Aumenta la curacion cada 2 aciertos del 50% al 75% del dano recibido.'),
    ('powerup_invincibility',  'Amuleto de Escudo Unico',   'AMULET', 0,  2800, 'Evita un golpe mortal por cancion otorgando 4s de invencibilidad.'),
    ('amulet_medallion_swamp', 'Medallon de Vitalidad',     'AMULET', 0,  3400, 'Aumenta tu vida maxima de 200 a 260 HP en cada cancion.'),
    ('amulet_overdrive_32x',   'Amuleto Overdrive (x32)',   'AMULET', 0,  3800, 'Eleva el multiplicador maximo a x32, pero requiere 21 notas GOOD/PERFECT para subir cada nivel.'),
    ('amulet_medallion_ruins', 'Medallon del Compas',       'AMULET', 0,  4200, 'El multiplicador sube cada 5 notas GOOD/PERFECT en vez de cada 7.')
ON CONFLICT(id_item) DO UPDATE SET
    nombre        = excluded.nombre,
    categoria     = excluded.categoria,
    curacion_hp   = excluded.curacion_hp,
    precio_tienda = excluded.precio_tienda,
    descripcion   = excluded.descripcion;
