# Arquitectura del Código Fuente en Capas (POO) — *Combo 6Beat*

> [!NOTE]
> **Estado del código fuente para esta entrega:** El videojuego *Combo 6Beat* continúa en desarrollo activo (pulido de sprites finales, secuencias de cierre y ajustes de escenarios), por lo que **no se empaqueta todavía el archivo comprimido final del código completo** en esta carpeta. No obstante, toda la arquitectura POO en capas y el módulo completo de persistencia SQLite (`game.core.db.*` + `resources/db/schema.sql`) ya se encuentran implementados, compilados e integrados en el repositorio principal:
> - Runtime del juego (Java 17 + Maven): [../../source/combo_6beat/](../../source/combo_6beat/)
> - Editor de niveles nativo (D `@nogc` + DUB): [../../level_editor/](../../level_editor/)

---

## 1. Principios de Diseño Orientado a Objetos

El sistema está dividido en dos aplicaciones desacopladas que comparten contratos de archivos basados exclusivamente en **rutas relativas** (escenas XML, grafos JSON de diálogo, charts `.osu` y proyectos `.procshit`):

1. **Separación estricta Motor vs. Juego:** El paquete `engine.*` implementa el núcleo gráfico, físico, de audio y de carga XML sin importar ninguna clase de `game.*`. Cualquier juego 2D futuro puede reutilizar `engine.*` sin modificarlo.
2. **Arquitectura en 4 Capas en Java (`source/combo_6beat/`):**
   - **Capa de Motor Reutilizable (`engine.*`)**
   - **Capa de Dominio y Entidades (`game.core.*`)**
   - **Capa de Persistencia SQLite / Repositorios (`game.core.db.*`)**
   - **Capa de Presentación y Escenas (`game.*`, `game.scenes.*`)**
3. **Polimorfismo e Inversión de Control por Escena:** Todas las pantallas heredan de `engine.Scene` y los actores del mundo heredan de `engine.objects.GameObject`, implementando las interfaces `IUpdate`, `IDraw` y `Drawable` según su responsabilidad.

---

## 2. Diagrama de Dependencias entre Capas

```text
+-------------------------------------------------------------------+
|  CAPA 4: PRESENTACIÓN Y ESCENAS (game.*, game.scenes.*)           |
|  Main (PApplet), Config, Global, InitScene, RPGScene, ROTNScene,  |
|  ROTNCore                                                         |
+---------------------------------+---------------------------------+
                                  |
          +-----------------------+-----------------------+
          v                                               v
+---------------------------------------+   +-----------------------+
| CAPA 2: DOMINIO Y ENTIDADES           |   | CAPA 3: PERSISTENCIA  |
| (game.core.*)                         |<->| SQLite JDBC           |
| - GameState (estado + ItemDef/Slots)  |   | (game.core.db.*)      |
| - Player, NPC, Chest, SceneChanger    |   | - DatabaseManager     |
| - RPGDialog, DialogManager            |   | - SaveRepository      |
| - OsuManiaReader, NotePacker          |   | - CustomSongRepository|
+-------------------+-------------------+   +-----------+-----------+
                    |                                   |
                    v                                   v
+---------------------------------------+   +-----------------------+
| CAPA 1: MOTOR REUTILIZABLE (engine.*) |   | resources/db/         |
| - Engine, Scene, ResourcesManager     |   | schema.sql            |
| - KBInput, Camera2D, Tilemap2D        |   | (SQLite 3FN + triggers|
| - Box, CharacterBody, Sprite2D        |   |  + vistas INNER JOIN) |
| - ParticleSystem, Audio, MusicTrack   |   +-----------------------+
| - GameObject, IDraw, IUpdate, Drawable|
+---------------------------------------+
```

---

## 3. Detalle por Paquetes y Responsabilidades

### 3.1 Capa 1 — Motor Reutilizable (`source/combo_6beat/src/main/java/engine/`)

| Subpaquete / Clase | Responsabilidad POO |
| :--- | :--- |
| `Engine` | Punto de bucle principal sobre `PApplet` (Processing 4.5.7) y `ControlP5` (2.2.6); gestiona el framebuffer virtual (`createFrameBuffer`, `resizeVirtualScreen`, `setPixelPerfect`), la resolución de ventana y el cambio de escena (`changeScene(Scene)`). |
| `Scene` | Clase base de toda pantalla o nivel. Carga mapas XML (`setup()`), administra la colección polimórfica de `GameObject`, registra controles UI (`registerUI`), ejecuta `update(float deltaTime)` y `draw(PGraphics g)` y libera recursos en `close()`. |
| `ResourcesManager` | Resuelve rutas relativas respecto al proyecto (`getAbsPathFromProject`, `getResourceAbsPath`), carga XML/JSON/imágenes (`loadXML`, `loadJSON`, `loadMap`, `loadImage`) e instancia dinámicamente objetos del mapa vía reflexión (`loadCustomObject`, `loadCustomObjects`). |
| `KBInput` | Abstracción centralizada de teclado y rebinding. |
| `engine.objects.*` | Clase abstracta `GameObject` e interfaces `IUpdate`, `IDraw` para desacoplar ciclo lógico de renderizado. |
| `engine.draw.*` | `Drawable` (interfaz), `Sprite2D`, `Tilemap2D`, `Camera2D` y `ParticleSystem`. |
| `engine.physics.*` | `Box` (extiende `GameObject`) y `CharacterBody` (extiende `Box`) para colisiones AABB y movimiento top-down. |
| `engine.audio.*` | `Audio` y `MusicTrack` construidos sobre `kuusisto.tinysound` (`TinySound`). |
| `engine.dialogs.*` | `GameDialog` y `DialogManager` base para árboles/grafos de diálogo. |

### 3.2 Capa 2 — Dominio y Entidades del Juego (`source/combo_6beat/src/main/java/game/core/`)

| Subpaquete / Clase | Responsabilidad POO |
| :--- | :--- |
| `GameState` | Singleton/coordinador de sesión en memoria: administra los 4 slots de guardado, inventario, PowerUps equipados, `HealStrategy` (`OPTIMAL` / `MINIMAL`), flags narrativos, catálogos de ítems (`ItemDef`) y resúmenes de slot (`SaveSlotSummary`), delegando toda lectura/escritura en SQLite. |
| `game.core.game_objects.*` | Actores especializados del RPG: `Player` (extiende `CharacterBody`), `NPC`, `Chest` y `SceneChanger` (extienden `GameObject` implementando `IUpdate` e `IDraw`). |
| `game.core.dialogs.*` | `RPGDialog` y `DialogManager`: extienden los diálogos base con condiciones sobre flags/ítems y disparos de eventos narrativos o batallas. |
| `game.core.notes.*` | `OsuManiaReader`, `NotePacker` y `TimingPointPacker`: parsean charts `.osu` de 6 carriles, metadatos de canción, BPM y puntos de tiempo. |

### 3.3 Capa 3 — Persistencia SQLite / Repositorios (`source/combo_6beat/src/main/java/game/core/db/`)

Construida sobre el driver `org.xerial:sqlite-jdbc` (`3.54.1.0`) ejecutando [../../source/combo_6beat/resources/db/schema.sql](../../source/combo_6beat/resources/db/schema.sql):

| Clase | Responsabilidad y Métodos Principales |
| :--- | :--- |
| `DatabaseManager` | Inicializa `combo6beat.db` con `PRAGMA journal_mode = WAL`, `PRAGMA foreign_keys = ON` y `PRAGMA busy_timeout = 3000`; divide y ejecuta scripts SQL respetando triggers `BEGIN ... END;` (`splitSqlStatements`, `executeSqlScript`); garantiza rutas relativas (`toRelativeProjectPath`); registra zonas e ítems bajo demanda (`ensureZoneRegistered`, `ensureItemRegistered`) y ejecuta la migración única desde archivos `.save`/`.save2` heredados. |
| `SaveRepository` | Encapsula el módulo Historia RPG: consulta slots (`hasSlot`, `countUsedSlots`, `getSlotSummary` sobre `v_resumen_partidas`), crea y elimina partidas (`createNewGame`, `deleteSlot`), guarda estado transaccional diferencial en `PARTIDAS`/`OBJETOS_OBTENIDOS`/`FLAGS_PARTIDA` (`saveState`, disparando los triggers de auditoría en `HISTORIAL_OBJETOS`), restaura partidas (`loadState` → `LoadedSaveData`), registra victorias de jefes (`recordBattleVictory`) y provee consultas tipadas (`getBattleRecordsForSlot`, `getItemHistoryForSlot`). |
| `CustomSongRepository` | Encapsula el módulo independiente de Canciones Personalizadas: escanea `resources/songs/custom` (`syncCustomSongsFolder`), registra o actualiza charts `.osu` (`registerOrUpdateOsuFile`), importa archivos externos copiándolos en rutas relativas (`importExternalOsu`), consulta la vista consolidada (`listAll`, `getById` → `CustomSongSummary`) y guarda intentos/ítems (`recordAttempt`) delegando en los triggers la promoción de récords (`puntaje_mas_alto`) y copia `'ULTIMO'` → `'MEJOR'`. |

### 3.4 Capa 4 — Presentación, Configuración y Escenas (`source/combo_6beat/src/main/java/game/`)

| Clase | Responsabilidad POO |
| :--- | :--- |
| `Main` | Extiende `processing.core.PApplet`; inicializa ventana (`1920x1080` por defecto), motor `Engine`, configuración (`Config`) y lanza `InitScene`. |
| `Config` | Gestiona resolución (`1080p`/`720p`), modo `pixelPerfect`, volúmenes BGM/SFX, silenciamiento y mapeo configurable de teclas RPG y 6K. |
| `Global` | Constantes y referencias compartidas de aplicación. |
| `InitScene` | Extiende `Scene`: implementa el menú principal (`MAIN`), gestor de 4 ranuras SQLite (`SAVE_SELECT`), selector de pistas (`SONG_SELECT` con pestañas `CANCIONES CUSTOM` + `+ IMPORTAR .OSU` y `JEFES HISTORIA`) y pantalla de ajustes (`SETTINGS`). |
| `RPGScene` | Extiende `Scene`: renderizado y exploración top-down en resolución virtual `560x315`, colisiones, interacción con NPCs/cofres, transiciones de escena y menú Teléfono/Pausa (`ESC`) con guardado en BD, equipamiento y consulta de historial. |
| `ROTNScene` y `ROTNCore` | `ROTNScene` extiende `Scene` y presenta el combate rítmico de 6 carriles en `480x270`; `ROTNCore` encapsula la lógica matemática de juicio (`HitRating`: `PERFECT 45 ms`, `GOOD 85 ms`, `SAFE 125 ms`, `MISS 170 ms` + modificadores por PowerUp `+10 ms` / `-7.5 ms`), sistema de curación por notas consecutivas y escudo único. |

---

## 4. Herramienta Externa: Editor de Niveles en D (`level_editor/`)

Siguiendo el principio de separación de herramientas, la edición de mapas, tilesets, colisiones, prefabs y grafos de diálogo se realiza fuera de Java en un ejecutable nativo escrito en **D (`@nogc nothrow`)** con `raylib-d` (`~>6.2.0`) y `raygui`:

- **`level_editor/source/app.d` y `engine/` (`core.d`, `scene.d`, `path_utils.d`, `tinyfd.d`):** Bucle nativo, manejo de ventanas/diálogos del sistema y normalización estricta a rutas relativas.
- **`level_editor/source/scenes/map_editor/`:** Gestor de proyectos `.procshit` (`project_manager.d`), serializador XML compatible con `engine.ResourcesManager` (`xml_scene.d`), modelos de nivel y diálogos (`level_data.d`, `dialogs_data.d`) y pestañas especializadas (`level_editor_tab.d`, `tilemap_tab.d`, `tileset_tab.d`, `dialogs_editor_tab.d`, `prefabs_tab.d`).

---

## 5. Dependencias de Compilación Verificadas

| Subproyecto | Archivo de Manifiesto | Stack / Dependencias |
| :--- | :--- | :--- |
| Juego (`source/combo_6beat/`) | [pom.xml](../../source/combo_6beat/pom.xml) | Java 17 (`maven.compiler.source`/`target`), `org.processing:core:4.5.7`, `sojamo:controlP5:2.2.6`, `org.xerial:sqlite-jdbc:3.54.1.0`, `kuusisto.tinysound` embebido |
| Editor (`level_editor/`) | [dub.json](../../level_editor/dub.json) | Lenguaje D (compilado vía `dub`), `raylib-d ~>6.2.0`, `libraygui.a` |
