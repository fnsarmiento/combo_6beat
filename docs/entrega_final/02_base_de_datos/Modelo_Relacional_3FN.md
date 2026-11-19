# Modelo Conceptual, Lógico Relacional y Normalización (3FN) — *Combo 6Beat*

## 1. De Modelo Conceptual (DER Chen) a Modelo Lógico (Tablas 3FN)

El diseño de la base de datos parte de un **DER Conceptual en notación Chen pura** ([DER_Conceptual_Combo6Beat.png](DER_Conceptual_Combo6Beat.png)) sin claves foráneas (`FK`) ni tablas intermedias, y se transforma siguiendo las reglas del modelo relacional hasta alcanzar la **Tercera Forma Normal (3FN)** en [01_ddl_creacion.sql](01_ddl_creacion.sql).

### Reglas de transformación aplicadas

| Elemento en el DER Conceptual (Chen) | Transformación al Modelo Lógico Relacional (SQLite 3FN) |
| :--- | :--- |
| **Entidad Fuerte** (`ZONAS`, `PARTIDAS`, `CATALOGO_BATALLAS`, `CATALOGO_ITEMS`, `HISTORIAL_OBJETOS`, `CANCIONES_PERSONALIZADAS`) | Se convierte en tabla propia con su **PK** (`PRIMARY KEY`). |
| **Entidad Débil** (`FLAGS_PARTIDA`, doble rectángulo + relación identificadora `definir`) | Se convierte en tabla con **PK compuesta** `(id_partida, flag_nombre)`, heredando `id_partida` como `FK` con `ON DELETE CASCADE`. |
| **Relaciones `1:N`** (`estar`, `ubicar`, `recompensar`, `auditar`, `referenciar`) | Propagan la **PK** del lado `1` hacia el lado `N` como **clave foránea (`FK`)**: `id_zona_actual`, `id_zona_retorno`, `id_zona`, `id_item_recompensa`, `id_partida`, `id_item`. |
| **Relación `N:M` `vencer`** *(PARTIDAS ↔ CATALOGO_BATALLAS)* | Se materializa en la tabla asociativa **`BATALLAS_VENCIDAS`** con PK compuesta `(id_partida, id_batalla)` y sus atributos propios (`puntaje_maximo`, `max_combo`, `perfects`, `goods`, `safes`, `misses`, `intentos_totales`, fechas). |
| **Relación `N:M` `poseer`** *(PARTIDAS ↔ CATALOGO_ITEMS)* | Se materializa en la tabla asociativa **`OBJETOS_OBTENIDOS`** con PK compuesta `(id_partida, id_item)` y sus atributos (`cantidad`, `equipado`, fechas). |
| **Relación `N:M` `utilizar`** *(CANCIONES_PERSONALIZADAS ↔ CATALOGO_ITEMS)* | Se materializa en la tabla asociativa **`CANCION_OBJETOS_INTENTO`** con PK compuesta `(id_cancion, tipo_registro, id_item)` para separar atómicamente los ítems del intento `'ULTIMO'` frente a los resguardados del `'MEJOR'`. |

---

## 2. Verificación de Formas Normales (1FN, 2FN, 3FN)

1. **Primera Forma Normal (1FN):** Todos los atributos contienen valores atómicos indivisibles. Ninguna tabla guarda listas separadas por comas (los flags viven en `FLAGS_PARTIDA` y los chips/ítems de canciones custom viven normalizados en `CANCION_OBJETOS_INTENTO`).
2. **Segunda Forma Normal (2FN):** En todas las tablas con clave primaria compuesta (`BATALLAS_VENCIDAS`, `OBJETOS_OBTENIDOS`, `FLAGS_PARTIDA`, `CANCION_OBJETOS_INTENTO`), cada columna no clave depende funcionalmente de la clave primaria completa y nunca de un subconjunto parcial de la misma.
3. **Tercera Forma Normal (3FN):** Se eliminan todas las dependencias transitivas extrayendo los atributos descriptivos estáticos a catálogos propios:
   - `ZONAS` concentra `nombre`, `escena_xml_relativa` y `orden_progresion` (en lugar de repetirlos en `PARTIDAS` o `CATALOGO_BATALLAS`).
   - `CATALOGO_ITEMS` concentra `nombre`, `categoria`, `curacion_hp` y `descripcion` (en lugar de duplicarlos en `OBJETOS_OBTENIDOS`, `HISTORIAL_OBJETOS` o `CANCION_OBJETOS_INTENTO`).
   - `CATALOGO_BATALLAS` concentra `boss_nombre`, `especie`, `defeat_flag`, `escena_combate_relativa` e `id_item_recompensa` (en lugar de repetirlos en `BATALLAS_VENCIDAS`).

---

## 3. Diccionario de Datos (Esquema Lógico SQLite)

### Catálogos Base
- **`ZONAS`**: `id_zona` (TEXT PK), `nombre` (TEXT NOT NULL), `escena_xml_relativa` (TEXT UNIQUE NOT NULL), `orden_progresion` (INTEGER).
- **`CATALOGO_ITEMS`**: `id_item` (TEXT PK), `nombre` (TEXT NOT NULL), `categoria` (CHECK `'HEAL'|'KEY'|'POWERUP'|'AMULET'|'MISC'`), `curacion_hp` (CHECK `>= 0`), `descripcion` (TEXT).
- **`CATALOGO_BATALLAS`**: `id_batalla` (TEXT PK), `boss_id` (TEXT UNIQUE), `boss_nombre` (TEXT), `especie` (TEXT), `defeat_flag` (TEXT UNIQUE), `escena_combate_relativa` (TEXT), `id_zona` (FK → `ZONAS`), `id_item_recompensa` (FK NULL → `CATALOGO_ITEMS`), `es_historia_principal` (`0|1`).
- **`DB_META`**: `clave` (TEXT PK), `valor` (TEXT) — control interno de migración única desde `.save`.

### Módulo Historia RPG
- **`PARTIDAS`**: `id_partida` (PK AUTOINCREMENT), `slot` (UNIQUE CHECK `1..4`), `id_zona_actual` (FK → `ZONAS`), `player_x`, `player_y`, `id_zona_retorno` (FK → `ZONAS`), `return_x`, `return_y`, `heal_strategy` (`'OPTIMAL'|'MINIMAL'`), `estado_general` (`'EN_PROGRESO'|'FINAL_NORMAL'|'FINAL_VERDADERO'|'BUCLE_AGOTADO'`), `progreso_porcentaje` (`0..100`), `fecha_creacion`, `fecha_actualizacion`.
- **`BATALLAS_VENCIDAS`**: `id_partida` (PK/FK), `id_batalla` (PK/FK), `puntaje_maximo`, `max_combo`, `perfects`, `goods`, `safes`, `misses`, `intentos_totales`, `fecha_primera_victoria`, `fecha_ultima_victoria`.
- **`OBJETOS_OBTENIDOS`**: `id_partida` (PK/FK), `id_item` (PK/FK), `cantidad` (`>= 0`), `equipado` (`0|1`), `fecha_obtencion`, `fecha_actualizacion`.
- **`HISTORIAL_OBJETOS`**: `id_historial` (PK AUTOINCREMENT), `id_partida` (FK), `id_item` (FK), `evento` (`'OBTENIDO'|'USADO'|'EQUIPADO'|'DESEQUIPADO'`), `cantidad_delta`, `fecha_evento`.
- **`FLAGS_PARTIDA`**: `id_partida` (PK/FK), `flag_nombre` (PK), `fecha_definicion`.

### Módulo Independiente de Canciones Personalizadas
- **`CANCIONES_PERSONALIZADAS`**: `id_cancion` (PK AUTOINCREMENT), `clave_unica` (TEXT UNIQUE), `titulo`, `artista`, `dificultad`, `bpm` (`> 0`), `ruta_portada`, `ruta_audio`, `ruta_chart`, `cantidad_notas`, `puntaje_ultimo_intento`, `combo_ultimo_intento`, `precision_ultimo_intento`, `puntaje_mas_alto`, `combo_mas_alto`, `precision_mas_alta`, `veces_jugada`, `fecha_ultimo_intento`, `fecha_record`.
- **`CANCION_OBJETOS_INTENTO`**: `id_cancion` (PK/FK), `tipo_registro` (PK CHECK `'ULTIMO'|'MEJOR'`), `id_item` (PK/FK).

---

## 4. Triggers y Vistas SQL Implementadas

- **Triggers (`01_ddl_creacion.sql`):**
  1. `trg_canciones_validar_insert` / `trg_canciones_validar_update`: validación `BEFORE` con `RAISE(ABORT, ...)` para impedir `bpm <= 0`, puntajes negativos o rutas absolutas.
  2. `trg_canciones_record_after_insert` / `trg_canciones_record_after_update`: promueven automáticamente `puntaje_mas_alto`, `combo_mas_alto`, `precision_mas_alta` y `fecha_record` cuando `puntaje_ultimo_intento` supera al récord previo, y resguardan los ítems del récord copiando `'ULTIMO'` → `'MEJOR'` en `CANCION_OBJETOS_INTENTO`.
  3. `trg_objetos_validar_insert` / `trg_objetos_validar_update`: impiden `cantidad < 0`.
  4. `trg_objetos_audit_insert` / `trg_objetos_audit_update`: registran automáticamente cada obtención, consumo, equipamiento o desequipamiento en `HISTORIAL_OBJETOS` y limpian filas cuando `cantidad <= 0`.
  5. `trg_batallas_progreso_insert` / `trg_batallas_progreso_update`: recalculan automáticamente `progreso_porcentaje` en `PARTIDAS`.
- **Vistas (`INNER JOIN`):**
  1. `v_resumen_partidas`
  2. `v_mejores_puntajes_batallas`
  3. `v_inventario_detallado`
  4. `v_historial_objetos`
  5. `v_canciones_custom_objetos`
  6. `v_estadisticas_canciones_custom`
