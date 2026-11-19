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

-- 2. Vista de resumen de partidas (PARTIDAS INNER JOIN ZONAS)
SELECT
    slot,
    zona_actual,
    estado_general,
    progreso_porcentaje,
    total_batallas_vencidas,
    total_items,
    total_amuletos
FROM v_resumen_partidas
ORDER BY slot;

-- 3. Vista de mejores puntajes en batallas de historia (4 tablas con INNER JOIN)
SELECT
    slot_partida,
    boss_nombre,
    especie,
    zona_batalla,
    puntaje_maximo,
    max_combo,
    perfects,
    misses,
    intentos_totales
FROM v_mejores_puntajes_batallas
ORDER BY slot_partida, puntaje_maximo DESC;

-- 4. Vista de inventario actual detallado por partida
SELECT
    slot_partida,
    item_nombre,
    categoria,
    cantidad,
    equipado
FROM v_inventario_detallado
ORDER BY slot_partida, categoria, item_nombre;

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

-- 6. Vista independiente de Canciones Personalizadas (récord vs último intento y objetos resguardados)
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

-- 8. Vista de enemigos ubicados en las salas del Hub (posición X/Y, canción y amuleto por score)
SELECT
    enemigo_nombre,
    especie,
    sala_hub,
    hub_x,
    hub_y,
    cancion_referencia,
    puntaje_minimo_amuleto,
    amuleto_por_score
FROM v_enemigos_hub;
