# Modelo Conceptual, Lógico Relacional y Normalización (3FN) — *Combo 6Beat*

## 1. De Modelo Conceptual (DER Chen) a Modelo Lógico (Tablas 3FN)

El diseño de la base de datos parte de un **DER Conceptual en notación Chen pura** ([DER_Conceptual_Combo6Beat.drawio](DER_Conceptual_Combo6Beat.drawio) / [DER_Conceptual_Combo6Beat.png](DER_Conceptual_Combo6Beat.png)) **sin claves foráneas (`FK`) ni tablas intermedias**, y se transforma en el **Modelo Relacional 3FN** ([Modelo_Relacional_Combo6Beat.drawio](Modelo_Relacional_Combo6Beat.drawio) / [Modelo_Relacional_Combo6Beat.png](Modelo_Relacional_Combo6Beat.png)) implementado en [01_ddl_creacion.sql](01_ddl_creacion.sql) (**12 tablas, 4 índices, 14 triggers y 7 vistas**).

### Reglas de transformación aplicadas

| Elemento en el DER Conceptual (Chen) | Transformación al Modelo Lógico Relacional (SQLite 3FN) |
| :--- | :--- |
| **Entidad Fuerte** (`ZONAS`, `ENEMIGOS`, `PARTIDAS`, `CATALOGO_BATALLAS`, `CATALOGO_ITEMS`, `HISTORIAL_OBJETOS`, `CANCIONES_PERSONALIZADAS`) | Se convierte en tabla propia con su **PK** (`PRIMARY KEY`). |
| **Entidad Débil** (`FLAGS_PARTIDA`, doble rectángulo + relación identificadora `definir`) | Se convierte en tabla con **PK compuesta** `(id_partida, flag_nombre)`, heredando `id_partida` como `FK` con `ON DELETE CASCADE`. |
| **Relaciones `1:N`** (`estar en`, `se ubica en`, `pertenece a`, `recompensa`, `desbloquea amuleto (score)`, `auditar`, `referenciar`) | Propagan la **PK** del lado `1` hacia el lado `N` como **clave foránea (`FK`)**: `id_zona_actual`, `id_zona_retorno`, `id_zona`, `id_item_recompensa`, `id_amuleto_score`, `id_partida`, `id_item`. |
| **Relación `1:1` `protagoniza`** *(ENEMIGOS ↔ CATALOGO_BATALLAS)* | Propaga `id_batalla` en `ENEMIGOS` como **`FK` con restricción `UNIQUE`**. |
| **Relación `N:M` `vencer`** *(PARTIDAS ↔ CATALOGO_BATALLAS)* | Se materializa en la tabla asociativa **`BATALLAS_VENCIDAS`** con PK compuesta `(id_partida, id_batalla)` y sus atributos propios (`puntaje_maximo`, `max_combo`, `perfects`, `goods`, `safes`, `misses`, `intentos_totales`, fechas). |
| **Relación `N:M` `poseer`** *(PARTIDAS ↔ CATALOGO_ITEMS)* | Se materializa en la tabla asociativa **`OBJETOS_OBTENIDOS`** con PK compuesta `(id_partida, id_item)` y sus atributos (`cantidad`, `equipado`, fechas). |
| **Relación `N:M` `utilizar`** *(CANCIONES_PERSONALIZADAS ↔ CATALOGO_ITEMS)* | Se materializa en la tabla asociativa **`CANCION_OBJETOS_INTENTO`** con PK compuesta `(id_cancion, tipo_registro, id_item)` para separar atómicamente los ítems del intento `'ULTIMO'` frente a los resguardados del `'MEJOR'`. |

---

## 2. Verificación de Formas Normales (1FN, 2FN, 3FN)

1. **Primera Forma Normal (1FN):** Todos los atributos contienen valores atómicos indivisibles. Ninguna tabla guarda listas separadas por comas (los flags viven en `FLAGS_PARTIDA` y los chips/ítems de canciones custom viven normalizados en `CANCION_OBJETOS_INTENTO`).
2. **Segunda Forma Normal (2FN):** En todas las tablas con clave primaria compuesta (`BATALLAS_VENCIDAS`, `OBJETOS_OBTENIDOS`, `FLAGS_PARTIDA`, `CANCION_OBJETOS_INTENTO`), cada columna no clave depende funcionalmente de la clave primaria completa y nunca de un subconjunto parcial de la misma.
3. **Tercera Forma Normal (3FN):** Se eliminan todas las dependencias transitivas extrayendo los atributos descriptivos estáticos a catálogos propios:
   - `ZONAS` concentra los datos de cada **sala del Hub** (`nombre`, `escena_xml_relativa`, `orden_progresion`).
   - `ENEMIGOS` concentra los datos de ubicación en el Hub (`id_zona`, `hub_x`, `hub_y`), su `cancion_referencia`, su `sprite_relativo` y su vínculo `1:1` a `CATALOGO_BATALLAS`.
   - `CATALOGO_ITEMS` concentra `nombre`, `categoria`, `curacion_hp` y `descripcion`.
   - `CATALOGO_BATALLAS` concentra `boss_nombre`, `especie`, `defeat_flag`, `escena_combate_relativa`, `id_item_recompensa`, `id_amuleto_score` y `puntaje_minimo_amuleto`.

---

## 3. Diccionario de Datos (12 Tablas SQLite)

### Catálogos y Hub por Salas
- **`ZONAS`**: `id_zona` (TEXT PK), `nombre` (TEXT NOT NULL — *Hub Central*, *Sala del Pantano*, *Sala de Ruinas*), `escena_xml_relativa` (TEXT UNIQUE NOT NULL), `orden_progresion` (INTEGER).
- **`ENEMIGOS`**: `id_enemigo` (TEXT PK), `nombre` (TEXT NOT NULL), `especie` (TEXT), `id_zona` (FK → `ZONAS`), `hub_x` (INTEGER CHECK `>= 0`), `hub_y` (INTEGER CHECK `>= 0`), `cancion_referencia` (TEXT NOT NULL), `sprite_relativo` (TEXT), `id_batalla` (FK UNIQUE NULL → `CATALOGO_BATALLAS`).
- **`CATALOGO_ITEMS`**: `id_item` (TEXT PK), `nombre` (TEXT NOT NULL), `categoria` (CHECK `'HEAL'|'KEY'|'POWERUP'|'AMULET'|'MISC'`), `curacion_hp` (CHECK `>= 0`), `descripcion` (TEXT).
- **`CATALOGO_BATALLAS`**: `id_batalla` (TEXT PK), `boss_id` (TEXT UNIQUE), `boss_nombre` (TEXT), `especie` (TEXT), `defeat_flag` (TEXT UNIQUE), `escena_combate_relativa` (TEXT), `id_zona` (FK → `ZONAS`), `id_item_recompensa` (FK NULL → `CATALOGO_ITEMS`), `id_amuleto_score` (FK NULL → `CATALOGO_ITEMS`), `puntaje_minimo_amuleto` (INTEGER CHECK `>= 0`), `es_historia_principal` (`0|1`).
- **`DB_META`**: `clave` (TEXT PK), `valor` (TEXT) — control interno de migración y versión de esquema.

### Módulo de Partidas (Hub + Progreso)
- **`PARTIDAS`**: `id_partida` (PK AUTOINCREMENT), `slot` (UNIQUE CHECK `1..4`), `id_zona_actual` (FK → `ZONAS`), `player_x`, `player_y`, `id_zona_retorno` (FK → `ZONAS`), `return_x`, `return_y`, `heal_strategy` (`'OPTIMAL'|'MINIMAL'`), `estado_general` (`'EN_PROGRESO'|'FINAL_NORMAL'|'FINAL_VERDADERO'|'BUCLE_AGOTADO'`), `progreso_porcentaje` (`0..100`), `fecha_creacion`, `fecha_actualizacion`.
- **`BATALLAS_VENCIDAS`**: `id_partida` (PK/FK), `id_batalla` (PK/FK), `puntaje_maximo`, `max_combo`, `perfects`, `goods`, `safes`, `misses`, `intentos_totales`, `fecha_primera_victoria`, `fecha_ultima_victoria`.
- **`OBJETOS_OBTENIDOS`**: `id_partida` (PK/FK), `id_item` (PK/FK), `cantidad` (`>= 0`), `equipado` (`0|1`), `fecha_obtencion`, `fecha_actualizacion`.
- **`HISTORIAL_OBJETOS`**: `id_historial` (PK AUTOINCREMENT), `id_partida` (FK), `id_item` (FK), `evento` (`'OBTENIDO'|'USADO'|'EQUIPADO'|'DESEQUIPADO'`), `cantidad_delta`, `fecha_evento`.
- **`FLAGS_PARTIDA`**: `id_partida` (PK/FK), `flag_nombre` (PK), `fecha_definicion`.

### Módulo Independiente de Canciones Personalizadas
- **`CANCIONES_PERSONALIZADAS`**: `id_cancion` (PK AUTOINCREMENT), `clave_unica` (TEXT UNIQUE), `titulo`, `artista`, `dificultad`, `bpm` (`> 0`), `ruta_portada`, `ruta_audio`, `ruta_chart`, `cantidad_notas`, `puntaje_ultimo_intento`, `combo_ultimo_intento`, `precision_ultimo_intento`, `puntaje_mas_alto`, `combo_mas_alto`, `precision_mas_alta`, `veces_jugada`, `fecha_ultimo_intento`, `fecha_record`.
- **`CANCION_OBJETOS_INTENTO`**: `id_cancion` (PK/FK), `tipo_registro` (PK CHECK `'ULTIMO'|'MEJOR'`), `id_item` (PK/FK).

---

## 4. Índices, Triggers y Vistas SQL Implementadas

- **Índices (`CREATE INDEX`, 4):**
  1. `idx_historial_partida_fecha` (`HISTORIAL_OBJETOS(id_partida, fecha_evento DESC)`)
  2. `idx_batallas_vencidas_partida` (`BATALLAS_VENCIDAS(id_partida)`)
  3. `idx_canciones_custom_titulo` (`CANCIONES_PERSONALIZADAS(titulo)`)
  4. `idx_enemigos_zona_pos` (`ENEMIGOS(id_zona, hub_x, hub_y)`)
- **Triggers (`01_ddl_creacion.sql`, 14):**
  1. `trg_canciones_validar_insert` / `_update`: impiden `bpm <= 0`, puntajes negativos o rutas absolutas.
  2. `trg_canciones_record_after_insert` / `_update`: promueven récord y resguardan los ítems `'ULTIMO'` → `'MEJOR'` en `CANCION_OBJETOS_INTENTO`.
  3. `trg_objetos_validar_insert` / `_update`: impiden `cantidad < 0`.
  4. `trg_objetos_audit_insert` / `_update`: auditan en `HISTORIAL_OBJETOS` y limpian filas con `cantidad <= 0`.
  5. `trg_batallas_progreso_insert` / `_update`: recalculan automáticamente `progreso_porcentaje` en `PARTIDAS`.
  6. `trg_batallas_amuleto_score_insert` / `_update`: otorgan automáticamente `id_amuleto_score` en `OBJETOS_OBTENIDOS` cuando `puntaje_maximo >= puntaje_minimo_amuleto`.
  7. `trg_enemigos_validar_insert` / `_update`: validan que `cancion_referencia` y `sprite_relativo` usen rutas relativas.
- **Vistas (`INNER JOIN`, 7):**
  1. `v_resumen_partidas`
  2. `v_mejores_puntajes_batallas`
  3. `v_inventario_detallado`
  4. `v_historial_objetos`
  5. `v_canciones_custom_objetos`
  6. `v_estadisticas_canciones_custom`
  7. `v_enemigos_hub`
