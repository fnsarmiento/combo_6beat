-- =============================================================================
-- Combo 6Beat — Script DML de Datos Iniciales y Registros de Prueba (SQLite)
-- Ejecutar después de: 01_ddl_creacion.sql
-- =============================================================================

PRAGMA foreign_keys = ON;

BEGIN TRANSACTION;

-- -----------------------------------------------------------------------------
-- 1. Metadatos y Catálogo Base de Ítems y Amuletos (3FN)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO DB_META (clave, valor) VALUES
    ('legacy_saves_migrated', '1'),
    ('schema_version', '2.0');

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

-- -----------------------------------------------------------------------------
-- 2. Partidas de Prueba (Cargador de 4 Ranuras)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO PARTIDAS (
    id_partida, slot, puntos_tienda, heal_strategy
) VALUES
    (1, 1, 2450, 'OPTIMAL'),
    (2, 2, 6800, 'MINIMAL');

-- -----------------------------------------------------------------------------
-- 3. Récords de Canciones por Partida (disparan trg_records_validar_* y trg_records_touch_partida_*)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO RECORDS_CANCIONES (
    id_partida, clave_cancion, titulo, artista, dificultad, puntaje_maximo, max_combo, perfects, goods, safes, misses, intentos_totales, victorias_totales
) VALUES
    (1, 'resources/songs/base/fiend - 2DF2/fiend - 2DF2 [Easy].osu',
        '2DF2', 'fiend', 'Easy', 184200, 142, 150, 22, 4, 2, 2, 1),
    (1, 'resources/songs/base/fiend - 2DF2/fiend - 2DF2 [Normal].osu',
        '2DF2', 'fiend', 'Normal', 246800, 178, 165, 13, 0, 0, 1, 1),
    (2, 'resources/songs/base/fiend - 2DF2/fiend - 2DF2 [Hard].osu',
        '2DF2', 'fiend', 'Hard', 412500, 310, 295, 18, 2, 0, 3, 2),
    (2, 'resources/songs/base/glass beach - cold weather/glass beach - cold weather [Insane].osu',
        'cold weather', 'glass beach', 'Insane', 785400, 642, 1180, 145, 25, 12, 4, 2);

-- -----------------------------------------------------------------------------
-- 4. Objetos Obtenidos (disparan trg_objetos_audit_* en HISTORIAL_OBJETOS)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO OBJETOS_OBTENIDOS (id_partida, id_item, cantidad, equipado) VALUES
    (1, 'potion_small',           3, 0),
    (1, 'powerup_reaction_plus',  1, 1),
    (1, 'powerup_invincibility',  1, 0),
    (2, 'potion_medium',          2, 0),
    (2, 'potion_large',           2, 0),
    (2, 'amulet_dual_slot',       1, 1),
    (2, 'amulet_healing',         1, 1),
    (2, 'powerup_reaction_minus', 1, 1),
    (2, 'amulet_medallion_swamp', 1, 0),
    (2, 'amulet_overdrive_32x',   1, 0),
    (2, 'amulet_medallion_ruins', 1, 0);

-- Simular consumo de 1 Poción Grande en la Partida 2 para generar evento 'USADO' en HISTORIAL_OBJETOS
UPDATE OBJETOS_OBTENIDOS
SET cantidad = cantidad - 1
WHERE id_partida = 2 AND id_item = 'potion_large' AND cantidad > 1;

-- -----------------------------------------------------------------------------
-- 5. Flags por Partida (Entidad Débil)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO FLAGS_PARTIDA (id_partida, flag_nombre) VALUES
    (1, 'TUTORIAL_6K_DONE'),
    (1, 'SHOP_UNLOCKED'),
    (2, 'TUTORIAL_6K_DONE'),
    (2, 'SHOP_UNLOCKED'),
    (2, 'DUAL_SLOT_UNLOCKED'),
    (2, 'FULL_COMBO_ACHIEVED');

-- -----------------------------------------------------------------------------
-- 6. Canciones Personalizadas (Módulo Custom Independiente + Prueba Triggers)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO CANCIONES_PERSONALIZADAS (
    id_cancion, clave_unica, titulo, artista, dificultad, bpm, ruta_portada, ruta_audio, ruta_chart, cantidad_notas
) VALUES
    (1, 'resources/songs/custom/fiend - 2DF2 [Custom 6K].osu',
        '2DF2 (Custom)', 'fiend', 'Custom 6K Normal', 155.0,
        'resources/songs/a1/cover.jpg',
        'resources/songs/a1/audio.wav',
        'resources/songs/custom/fiend - 2DF2 [Custom 6K].osu', 178),
    (2, 'resources/songs/custom/glass beach - cold weather [Custom 6K].osu',
        'cold weather (Custom)', 'glass beach', 'Custom 6K Hard', 155.0,
        'resources/sprites/backgrounds/debug/beach_ruins.png',
        'resources/songs/audio.wav',
        'resources/songs/custom/glass beach - cold weather [Custom 6K].osu', 1362);

-- Intento 1 en Canción 1 (15400 pts con Amuleto de Calma (+10ms) -> el trigger promueve récord y resguarda 'MEJOR')
DELETE FROM CANCION_OBJETOS_INTENTO WHERE id_cancion = 1 AND tipo_registro = 'ULTIMO';
INSERT INTO CANCION_OBJETOS_INTENTO (id_cancion, tipo_registro, id_item) VALUES
    (1, 'ULTIMO', 'powerup_reaction_plus');
UPDATE CANCIONES_PERSONALIZADAS
SET puntaje_ultimo_intento = 15400,
    combo_ultimo_intento = 112,
    precision_ultimo_intento = 96.8,
    veces_jugada = veces_jugada + 1,
    fecha_ultimo_intento = CURRENT_TIMESTAMP
WHERE id_cancion = 1;

-- Intento 2 en Canción 1 (puntaje menor: 9800 pts con Amuleto de Escudo Único -> el récord 15400 y Amuleto de Calma quedan resguardados)
DELETE FROM CANCION_OBJETOS_INTENTO WHERE id_cancion = 1 AND tipo_registro = 'ULTIMO';
INSERT INTO CANCION_OBJETOS_INTENTO (id_cancion, tipo_registro, id_item) VALUES
    (1, 'ULTIMO', 'powerup_invincibility');
UPDATE CANCIONES_PERSONALIZADAS
SET puntaje_ultimo_intento = 9800,
    combo_ultimo_intento = 64,
    precision_ultimo_intento = 84.5,
    veces_jugada = veces_jugada + 1,
    fecha_ultimo_intento = CURRENT_TIMESTAMP
WHERE id_cancion = 1;

-- Intento 1 en Canción 2 (28900 pts con Relicario Gemelo, Amuleto de Filo (-7.5ms) y Amuleto de Curación)
DELETE FROM CANCION_OBJETOS_INTENTO WHERE id_cancion = 2 AND tipo_registro = 'ULTIMO';
INSERT INTO CANCION_OBJETOS_INTENTO (id_cancion, tipo_registro, id_item) VALUES
    (2, 'ULTIMO', 'amulet_dual_slot'),
    (2, 'ULTIMO', 'powerup_reaction_minus'),
    (2, 'ULTIMO', 'amulet_healing');
UPDATE CANCIONES_PERSONALIZADAS
SET puntaje_ultimo_intento = 28900,
    combo_ultimo_intento = 195,
    precision_ultimo_intento = 94.2,
    veces_jugada = veces_jugada + 1,
    fecha_ultimo_intento = CURRENT_TIMESTAMP
WHERE id_cancion = 2;

COMMIT;
