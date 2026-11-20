# Modelo Conceptual, Lógico Relacional y Normalización (3FN) — *Combo 6Beat*

## 1. De Modelo Conceptual (DER Chen) a Modelo Lógico (Tablas 3FN)

El diseño de la base de datos parte de un **DER Conceptual en notación Chen pura** ([DER_Conceptual_Combo6Beat.drawio](DER_Conceptual_Combo6Beat.drawio) / [DER_Conceptual_Combo6Beat.png](DER_Conceptual_Combo6Beat.png)) **sin claves foráneas (`FK`) ni tablas intermedias**, y se transforma en el **Modelo Relacional 3FN** ([Modelo_Relacional_Combo6Beat.drawio](Modelo_Relacional_Combo6Beat.drawio) / [Modelo_Relacional_Combo6Beat.png](Modelo_Relacional_Combo6Beat.png)) implementado en [01_ddl_creacion.sql](01_ddl_creacion.sql) (**9 tablas, 3 índices, 12 triggers y 6 vistas**).

### Reglas de transformación aplicadas

| Elemento en el DER Conceptual (Chen) | Transformación al Modelo Lógico Relacional (SQLite 3FN) |
| :--- | :--- |
| **Entidad Fuerte** (`PARTIDAS`, `CATALOGO_ITEMS`, `HISTORIAL_OBJETOS`, `CANCIONES_PERSONALIZADAS`) | Se convierte en tabla propia con su **PK** (`PRIMARY KEY`). |
| **Entidades Dependientes por Partida** (`RECORDS_CANCIONES`, `FLAGS_PARTIDA`) | Se convierten en tablas con **PK compuesta** `(id_partida, clave_cancion)` y `(id_partida, flag_nombre)`, heredando `id_partida` como `FK` con `ON DELETE CASCADE`. |
| **Relaciones `1:N`** (`registrar record`, `definir`, `auditar`, `referenciar`) | Propagan la **PK** del lado `1` hacia el lado `N` como **clave foránea (`FK`)**: `id_partida` en `RECORDS_CANCIONES`, `FLAGS_PARTIDA` e `HISTORIAL_OBJETOS`, e `id_item` en `HISTORIAL_OBJETOS`. |
| **Relación `N:M` `poseer`** *(PARTIDAS ↔ CATALOGO_ITEMS)* | Se materializa en la tabla asociativa **`OBJETOS_OBTENIDOS`** con PK compuesta `(id_partida, id_item)` y sus atributos (`cantidad`, `equipado`, `fecha_obtencion`, `fecha_actualizacion`). |
| **Relación `N:M` `utilizar`** *(CANCIONES_PERSONALIZADAS ↔ CATALOGO_ITEMS)* | Se materializa en la tabla asociativa **`CANCION_OBJETOS_INTENTO`** con PK compuesta `(id_cancion, tipo_registro, id_item)` para separar atómicamente los amuletos del intento `'ULTIMO'` frente a los resguardados del `'MEJOR'`. |

---

## 2. Verificación de Formas Normales (1FN, 2FN, 3FN)

1. **Primera Forma Normal (1FN):** Todos los atributos contienen valores atómicos indivisibles. Ninguna tabla guarda listas separadas por comas (los flags viven en `FLAGS_PARTIDA` y los amuletos/ítems de canciones custom viven normalizados en `CANCION_OBJETOS_INTENTO`).
2. **Segunda Forma Normal (2FN):** En todas las tablas con clave primaria compuesta (`RECORDS_CANCIONES`, `OBJETOS_OBTENIDOS`, `FLAGS_PARTIDA`, `CANCION_OBJETOS_INTENTO`), cada columna no clave depende funcionalmente de la clave primaria completa y nunca de un subconjunto parcial de la misma.
3. **Tercera Forma Normal (3FN):** Se eliminan todas las dependencias transitivas extrayendo los atributos descriptivos estáticos a catálogos propios:
   - `CATALOGO_ITEMS` concentra `nombre`, `categoria`, `curacion_hp`, `precio_tienda` y `descripcion` (3 pociones curativas + Relicario Gemelo y 7 amuletos de combate de la tienda).
   - `PARTIDAS` almacena el saldo `puntos_tienda` y la estrategia de curación `heal_strategy` por ranura (`slot` `1..4`), sin repetir metadatos de ítems.
   - `RECORDS_CANCIONES` registra por `(id_partida, clave_cancion)` los récords y estadísticas de cada chart jugado (`puntaje_maximo`, `max_combo`, `perfects`, `goods`, `safes`, `misses`, `intentos_totales`, `victorias_totales`, `fecha_ultimo_intento`).

---

## 3. Diccionario de Datos (9 Tablas SQLite)

### Catálogos Base
- **`CATALOGO_ITEMS`**: `id_item` (TEXT PK), `nombre` (TEXT NOT NULL), `categoria` (CHECK `'HEAL'|'POWERUP'|'AMULET'|'MISC'`), `curacion_hp` (INTEGER CHECK `>= 0`), `precio_tienda` (INTEGER CHECK `>= 0`), `descripcion` (TEXT).
- **`DB_META`**: `clave` (TEXT PK), `valor` (TEXT) — control interno de migración y versión de esquema.

### Módulo de Partidas, Récords de Canciones, Tienda e Inventario
- **`PARTIDAS`**: `id_partida` (PK AUTOINCREMENT), `slot` (UNIQUE CHECK `1..4`), `puntos_tienda` (INTEGER CHECK `>= 0`), `heal_strategy` (`'OPTIMAL'|'MINIMAL'`), `fecha_creacion`, `fecha_actualizacion`.
- **`RECORDS_CANCIONES`**: `id_partida` (PK/FK → `PARTIDAS`), `clave_cancion` (PK TEXT), `titulo` (TEXT), `artista` (TEXT), `dificultad` (TEXT), `puntaje_maximo` (`>= 0`), `max_combo` (`>= 0`), `perfects` (`>= 0`), `goods` (`>= 0`), `safes` (`>= 0`), `misses` (`>= 0`), `intentos_totales` (`>= 1`), `victorias_totales` (`>= 0`), `fecha_ultimo_intento`.
- **`OBJETOS_OBTENIDOS`**: `id_partida` (PK/FK → `PARTIDAS`), `id_item` (PK/FK → `CATALOGO_ITEMS`), `cantidad` (`>= 0`), `equipado` (`0|1`), `fecha_obtencion`, `fecha_actualizacion`.
- **`HISTORIAL_OBJETOS`**: `id_historial` (PK AUTOINCREMENT), `id_partida` (FK → `PARTIDAS`), `id_item` (FK → `CATALOGO_ITEMS`), `evento` (`'OBTENIDO'|'USADO'|'EQUIPADO'|'DESEQUIPADO'`), `cantidad_delta`, `fecha_evento`.
- **`FLAGS_PARTIDA`**: `id_partida` (PK/FK → `PARTIDAS`), `flag_nombre` (PK), `fecha_definicion`.

### Módulo Independiente de Canciones Personalizadas
- **`CANCIONES_PERSONALIZADAS`**: `id_cancion` (PK AUTOINCREMENT), `clave_unica` (TEXT UNIQUE), `titulo`, `artista`, `dificultad`, `bpm` (`> 0`), `ruta_portada`, `ruta_audio`, `ruta_chart`, `cantidad_notas`, `puntaje_ultimo_intento`, `combo_ultimo_intento`, `precision_ultimo_intento`, `puntaje_mas_alto`, `combo_mas_alto`, `precision_mas_alta`, `veces_jugada`, `fecha_ultimo_intento`, `fecha_record`.
- **`CANCION_OBJETOS_INTENTO`**: `id_cancion` (PK/FK → `CANCIONES_PERSONALIZADAS`), `tipo_registro` (PK CHECK `'ULTIMO'|'MEJOR'`), `id_item` (PK/FK → `CATALOGO_ITEMS`).

---

## 4. Índices, Triggers y Vistas SQL Implementadas

- **Índices (`CREATE INDEX`, 3):**
  1. `idx_historial_partida_fecha` (`HISTORIAL_OBJETOS(id_partida, fecha_evento DESC)`)
  2. `idx_records_canciones_partida` (`RECORDS_CANCIONES(id_partida)`)
  3. `idx_canciones_custom_titulo` (`CANCIONES_PERSONALIZADAS(titulo)`)
- **Triggers (`01_ddl_creacion.sql`, 12):**
  1. `trg_canciones_validar_insert` / `_update`: impiden `bpm <= 0`, puntajes negativos o rutas absolutas en `CANCIONES_PERSONALIZADAS`.
  2. `trg_canciones_record_after_insert` / `_update`: promueven récord y resguardan los ítems `'ULTIMO'` → `'MEJOR'` en `CANCION_OBJETOS_INTENTO`.
  3. `trg_objetos_validar_insert` / `_update`: impiden `cantidad < 0` en `OBJETOS_OBTENIDOS`.
  4. `trg_objetos_audit_insert` / `_update`: auditan automáticamente en `HISTORIAL_OBJETOS`, actualizan `PARTIDAS.fecha_actualizacion` y limpian filas con `cantidad <= 0`.
  5. `trg_records_validar_insert` / `_update`: impiden puntajes/combos/victorias negativos, `intentos_totales < 1` o rutas absolutas en `RECORDS_CANCIONES.clave_cancion`.
  6. `trg_records_touch_partida_insert` / `_update`: actualizan `PARTIDAS.fecha_actualizacion` al registrar o mejorar récords en `RECORDS_CANCIONES`.
- **Vistas (`CREATE VIEW`, 6):**
  1. `v_resumen_partidas`
  2. `v_records_canciones`
  3. `v_inventario_detallado`
  4. `v_historial_objetos`
  5. `v_canciones_custom_objetos`
  6. `v_estadisticas_canciones_custom`
