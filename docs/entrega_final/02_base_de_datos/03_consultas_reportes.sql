-- =============================================================================
-- Combo 6Beat — Script de Consultas, Reportes y Demostración de Triggers
-- Útil para la defensa presencial sobre el motor SQLite.
-- =============================================================================

PRAGMA foreign_keys = ON;
.headers on
.mode box

-- 1. Verificación de integridad estructural y referencial
PRAGMA integrity_check;
PRAGMA foreign_key_check;

-- 2. Vista de resumen de partidas (puntos de tienda, amuletos y canciones completadas)
SELECT
    slot,
    puntos_tienda,
    heal_strategy,
    canciones_jugadas,
    canciones_completadas,
    total_items,
    total_amuletos
FROM v_resumen_partidas
ORDER BY slot;

-- 3. Vista de récords de canciones por partida (RECORDS_CANCIONES INNER JOIN PARTIDAS)
SELECT
    slot_partida,
    titulo,
    artista,
    dificultad,
    puntaje_maximo,
    max_combo,
    perfects,
    misses,
    intentos_totales,
    victorias_totales
FROM v_records_canciones
ORDER BY slot_partida, puntaje_maximo DESC;

-- 4. Vista de inventario actual detallado por partida (incluye precio_tienda de cada amuleto)
SELECT
    slot_partida,
    item_nombre,
    categoria,
    precio_tienda,
    cantidad,
    equipado
FROM v_inventario_detallado
ORDER BY slot_partida, categoria, precio_tienda, item_nombre;

-- 5. Vista de auditoría de movimientos de objetos (alimentada por triggers)
SELECT
    id_historial,
    slot_partida,
    item_nombre,
    evento,
    cantidad_delta,
    fecha_evento
FROM v_historial_objetos
LIMIT 15;

-- 6. Vista independiente de Canciones Personalizadas (récord vs último intento y amuletos resguardados)
SELECT
    id_cancion,
    titulo,
    dificultad,
    bpm,
    puntaje_mas_alto,
    objetos_record,
    puntaje_ultimo_intento,
    objetos_ultimo_intento,
    veces_jugada
FROM v_estadisticas_canciones_custom;

-- 7. Detalle normalizado 3FN de objetos usados en último intento vs récord
SELECT
    titulo,
    tipo_registro,
    item_nombre,
    categoria
FROM v_canciones_custom_objetos
ORDER BY id_cancion, tipo_registro, item_nombre;

-- 8. Catálogo de amuletos disponibles en la tienda ordenados por precio en puntos
SELECT
    id_item,
    nombre,
    categoria,
    precio_tienda,
    descripcion
FROM CATALOGO_ITEMS
WHERE precio_tienda > 0
ORDER BY precio_tienda ASC;
