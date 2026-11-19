-- =============================================================================
-- Combo 6Beat — Script DML de Datos Iniciales y Registros de Prueba (SQLite)
-- Ejecutar después de: 01_ddl_creacion.sql
-- =============================================================================

PRAGMA foreign_keys = ON;

BEGIN TRANSACTION;

-- -----------------------------------------------------------------------------
-- 1. Metadatos y Catálogos Base (3FN)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO DB_META (clave, valor) VALUES
    ('legacy_saves_migrated', '1'),
    ('schema_version', '1.0');

INSERT OR IGNORE INTO ZONAS (id_zona, nombre, escena_xml_relativa, orden_progresion) VALUES
    ('hub',         'El Hub',           'resources/scenes/hub.xml',         0),
    ('swamp',       'Swamp (Pantano)',  'resources/scenes/swamp.xml',       1),
    ('beach_ruins', 'Beach',            'resources/scenes/beach_ruins.xml', 2),
    ('debug_a',     'Sala de Pruebas',  'resources/scenes/a.xml',           99);

INSERT OR IGNORE INTO CATALOGO_ITEMS (id_item, nombre, categoria, curacion_hp, descripcion) VALUES
    ('potion_small',           'Pocion Pequena',        'HEAL',    25, 'Restaura 25 HP'),
    ('potion_medium',          'Pocion Mediana',        'HEAL',    50, 'Restaura 50 HP'),
    ('potion_large',           'Pocion Grande',         'HEAL',    90, 'Restaura 90 HP'),
    ('key_exit',               'Llave Virtual',         'KEY',     0,  'Abre la puerta de salida de la realidad virtual'),
    ('powerup_reaction_plus',  'Chip +10ms',            'POWERUP', 0,  '+10ms ventana de reaccion, +25 pts coste curacion'),
    ('powerup_reaction_minus', 'Chip -7.5ms',           'POWERUP', 0,  '-7.5ms ventana de reaccion, -20 pts coste curacion'),
    ('powerup_invincibility',  'Escudo Unico',          'POWERUP', 0,  'Invencibilidad de un solo uso por pista'),
    ('amulet_healing',         'Amuleto de Curacion',   'AMULET',  0,  'Aumenta la curacion por notas consecutivas del 50% al 75% del dano recibido'),
    ('amulet_medallion_swamp', 'Medallon del Pantano',  'AMULET',  0,  'Eco del tercer escondite de La Gata en el Pantano'),
    ('amulet_medallion_ruins', 'Medallon del Ocaso',    'AMULET',  0,  'Eco del tercer escondite de La Gata en Playas y Ruinas');

INSERT OR IGNORE INTO CATALOGO_BATALLAS (
    id_batalla, boss_id, boss_nombre, especie, defeat_flag, escena_combate_relativa, id_zona, id_item_recompensa, es_historia_principal
) VALUES
    ('batalla_swamp_1', 'boss_swamp_1', 'Kael',          'Gemelo 1',      'BOSS_SWAMP_1_DEFEATED',   'resources/songs/swamp_1_kael.scene',  'swamp',       NULL,                     1),
    ('batalla_swamp_2', 'boss_swamp_2', 'Kael & Vesper', 'Gemelos',       'BOSS_SWAMP_2_DEFEATED',   'resources/songs/swamp_2_twins.scene', 'swamp',       'powerup_reaction_plus',  1),
    ('batalla_swamp_3', 'boss_swamp_3', 'Grail',         'Oso + Buho',    'BOSS_SWAMP_3_DEFEATED',   'resources/songs/swamp_3_grail.scene', 'swamp',       'potion_large',           1),
    ('batalla_beach_1', 'boss_beach_1', 'Kael & Vesper', 'Costa',         'BOSS_BEACH_1_DEFEATED',   'resources/songs/beach_1_twins.scene', 'beach_ruins', 'potion_medium',          1),
    ('batalla_beach_2', 'boss_beach_2', 'Kael & Vesper', 'Ruinas',        'BOSS_BEACH_2_DEFEATED',   'resources/songs/beach_2_twins.scene', 'beach_ruins', 'amulet_healing',         1),
    ('batalla_beach_3', 'boss_grail',   'Grail',         'Oso + Buho',    'BOSS_BEACH_3_DEFEATED',   'resources/songs/grail_sanctum.scene', 'beach_ruins', 'powerup_reaction_minus', 1),
    ('batalla_cat_7',   'boss_cat',     'La Gata',       'Felino',        'BOSS_CAT_FINAL_DEFEATED', 'resources/songs/glass beach.scene',   'hub',         NULL,                     1),
    ('batalla_bear',    'boss_bear',    'Oso',           'Oso Guerrero',  'BOSS_RABY_DEFEATED',      'resources/songs/fiend - 2DF2.scene',  'beach_ruins', 'key_exit',               0),
    ('batalla_nash',    'boss_nash',    'Nash',          'Perico + Hiena','BOSS_NASH_DEFEATED',      'resources/songs/nash_storm.scene',    'beach_ruins', 'potion_large',           0);

-- -----------------------------------------------------------------------------
-- 2. Partidas de Prueba (Cargador de 4 Ranuras)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO PARTIDAS (
    id_partida, slot, id_zona_actual, player_x, player_y, id_zona_retorno, return_x, return_y, heal_strategy, estado_general
) VALUES
    (1, 1, 'swamp',       640.0,  720.0,  'swamp',       640.0,  720.0,  'OPTIMAL', 'EN_PROGRESO'),
    (2, 2, 'beach_ruins', 1824.0, 960.0,  'beach_ruins', 1824.0, 960.0,  'MINIMAL', 'BUCLE_AGOTADO');

-- -----------------------------------------------------------------------------
-- 3. Batallas Vencidas (disparan trg_batallas_progreso_* en PARTIDAS)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO BATALLAS_VENCIDAS (
    id_partida, id_batalla, puntaje_maximo, max_combo, perfects, goods, safes, misses, intentos_totales
) VALUES
    (1, 'batalla_swamp_1', 14200,  92, 110, 14, 4, 2, 1),
    (1, 'batalla_swamp_2', 18550, 128, 165, 22, 6, 3, 2),
    (2, 'batalla_swamp_1', 16800, 115, 120,  8, 2, 0, 1),
    (2, 'batalla_swamp_2', 21400, 152, 180, 14, 2, 1, 1),
    (2, 'batalla_swamp_3', 24900, 174, 210, 18, 4, 2, 2),
    (2, 'batalla_beach_1', 26100, 188, 225, 16, 3, 1, 1),
    (2, 'batalla_beach_2', 29300, 210, 240, 12, 2, 1, 1),
    (2, 'batalla_beach_3', 33800, 245, 290, 15, 3, 0, 1);

-- -----------------------------------------------------------------------------
-- 4. Objetos Obtenidos (disparan trg_objetos_audit_* en HISTORIAL_OBJETOS)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO OBJETOS_OBTENIDOS (id_partida, id_item, cantidad, equipado) VALUES
    (1, 'potion_small',          3, 0),
    (1, 'powerup_reaction_plus', 1, 1),
    (1, 'powerup_invincibility', 1, 0),
    (2, 'potion_medium',         2, 0),
    (2, 'potion_large',          2, 0),
    (2, 'key_exit',              1, 0),
    (2, 'amulet_healing',        1, 1),
    (2, 'powerup_reaction_minus',1, 1),
    (2, 'amulet_medallion_swamp',1, 0),
    (2, 'amulet_medallion_ruins',1, 0);

-- Simular consumo de 1 Pocion Grande en la Partida 2 para generar evento 'USADO' en HISTORIAL_OBJETOS
UPDATE OBJETOS_OBTENIDOS
SET cantidad = cantidad - 1
WHERE id_partida = 2 AND id_item = 'potion_large' AND cantidad > 1;

-- -----------------------------------------------------------------------------
-- 5. Flags Narrativos por Partida (Entidad Débil)
-- -----------------------------------------------------------------------------

INSERT OR IGNORE INTO FLAGS_PARTIDA (id_partida, flag_nombre) VALUES
    (1, 'HUB_INTRO_DONE'),
    (1, 'SWAMP_ENTERED'),
    (1, 'BOSS_SWAMP_1_DEFEATED'),
    (1, 'BOSS_SWAMP_2_DEFEATED'),
    (1, 'CAT_SWAMP_1'),
    (2, 'HUB_INTRO_DONE'),
    (2, 'SWAMP_ENTERED'),
    (2, 'BOSS_SWAMP_1_DEFEATED'),
    (2, 'BOSS_SWAMP_2_DEFEATED'),
    (2, 'BOSS_SWAMP_3_DEFEATED'),
    (2, 'BOSS_BEACH_1_DEFEATED'),
    (2, 'BOSS_BEACH_2_DEFEATED'),
    (2, 'BOSS_BEACH_3_DEFEATED'),
    (2, 'SWAMP_MEDALLION_TAKEN'),
    (2, 'RUINS_MEDALLION_TAKEN'),
    (2, 'ROUTE_TRUE_UNLOCKED'),
    (2, 'LOOP_EXHAUSTED_MODE');

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

-- Intento 1 en Canción 1 (15400 pts con Chip +10ms -> el trigger promueve récord y resguarda 'MEJOR')
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

-- Intento 2 en Canción 1 (puntaje menor: 9800 pts con Escudo Unico -> el récord 15400 y Chip +10ms quedan resguardados)
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

-- Intento 1 en Canción 2 (28900 pts con Chip -7.5ms y Amuleto de Curacion)
DELETE FROM CANCION_OBJETOS_INTENTO WHERE id_cancion = 2 AND tipo_registro = 'ULTIMO';
INSERT INTO CANCION_OBJETOS_INTENTO (id_cancion, tipo_registro, id_item) VALUES
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
