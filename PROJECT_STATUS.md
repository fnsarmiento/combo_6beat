# Combo 6Beat — Análisis de Propuesta, Estado Actual y Pendientes

Documento de referencia bajo demanda derivado del análisis de `Propuesta de Tema.docx` y de todo el código fuente en `source/combo_6beat/` y `level_editor/`.

---

## 1. Resumen de la Propuesta (`Propuesta de Tema.docx`)

- **Objetivo:** Videojuego 2D pixel art que combina exploración RPG top-down (*Everhood*) con combate rítmico estricto de 6 carriles (*Rift of the NecroDancer*).
- **Premisa:** El protagonista debe escapar de una realidad virtual explorando el mapa hasta encontrar una llave que abre la puerta de salida.
- **Mecánicas definidas en la propuesta:**
  - **Exploración RPG:** Movimiento 2D con `WASD`; inicio de combate por contacto de cajas de colisión al acercarse a enemigos en el mapa.
  - **Combate Rítmico 6K:** 6 teclas configurables (`QWEASD` y `UIOJKL` por defecto), rutas de combo, notas desplazándose **de izquierda a derecha**, diferenciación por color/sprite, notas con indicador de origen/trayectoria (caballeros/pájaros con flechas de advertencia) e inhabilitación temporal de botones por ataques enemigos.
  - **Sistema de Curación Dinámico:** Puntajes por ventana de precisión en milisegundos:
    - `Perfect` (`<= 20 ms`): `+5` puntos
    - `Good` (`<= 45 ms`): `+3` puntos
    - `Safe` (`<= 60 ms`): `+1` punto
    - `Errado`: `-2` puntos
    Los puntos llenan una barra activa que habilita curaciones ilimitadas mientras permanezca activa, eligiendo ítems automáticamente según la preferencia del jugador (uso óptimo vs. uso mínimo).
  - **Power-ups y Hub estilo Celular:** Interfaz/hub con estética de dispositivo celular para configurar mejoras ocultas en el mapa (1 por pista musical: modificadores de ventana ms vs. coste de curación, e invencibilidad de un solo uso) junto a un `State Manager` persistente.
  - **Entregable mínimo:** 2 de 4 escenarios (*pantano*, *barrio en un océano*, *bosque de cerezos en ruinas*, *playa*) y 3 NPCs elegidos del catálogo híbrido:
    - *Raby* (murciélago + serpiente)
    - *Nash* (perico + hiena)
    - *Grail* (oso + búho)
    - *Creig* (mono + cabra)
    - *Larc* (carpa en pecera transportada por Raúl)
    - *Raúl* (ñandú + jirafa)

---

## 2. Lo Resuelto Ya

### En `source/combo_6beat/` (Java 17 + Maven + Processing 4.5.7)
- **Motor base (`engine/`):**
  - `Engine`: Singleton sobre `PApplet`, framebuffer virtual `480x270` escalado a `1920x1080` con `textureSampling(2)` (pixel art sin suavizado), `ControlP5` (`setAutoDraw(false)`), cálculo de `deltaTime` y `setFramesToMonitorHZ()`.
  - `KBInput`: Polling por frame (`isKeyDown`, `wasKeyJustPressed`, `wasKeyJustReleased`, `normalizeKey`).
  - `ResourcesManager`: Resolución relativa al mapa XML (`<Map resources="...">` / `<Scene resources="...">`), caché de `PImage`, carga genérica por reflexión (`loadCustomObject` / `loadCustomObjects`), carga de BGM/SFX.
  - `Scene`: Ciclo de vida (`setup`, `update`, `draw`, `close`), parser XML base (`Box`/`box`/`Rect`, `Sprite`/`Sprite2D`), delegación a `parseCustomNode`, limpieza de `ControlP5` vía `registerUI`.
  - `Box` y `CharacterBody`: AABB (`intersects`) y `moveAndSlide(dt)` con sub-stepping por ejes X/Y.
  - `Sprite2D` y `Camera2D`: Spritesheets animados (`rows`, `cols`, `delay`, `centerPosition`, `zIndex`) y cámara 2D (`position`, `scale`, `rotation`).
  - `Audio` + `MusicTrack` + `kuusisto.tinysound`: Caché/reproducción de SFX con TinySound y estructura de BGM vía `javax.sound.sampled.Clip`.
  - `engine.dialogs.{DialogManager, GameDialog}`: Diálogos con tipeo animado y opciones (`default`, `option1`, `option2`, `__save_next__`, `next`).
- **Juego (`game/`):**
  - `Main` + `Global` + `Config`: Carga/guardado de `.config` JSON y bindings configurables para ritmo 6K, mundo RPG y UI.
  - `InitScene`: Menú inicial funcional con `PLAY` (carga `resources/scenes/a.xml`), `SETTINGS` (vacío) y `EXIT`.
  - `RPGScene` + `Player` + `NPC`: Movimiento del jugador, cámara centrada en el jugador, colisiones con `Box` de `group="Walls"` y `NPC.body`, serialización XML de `<NPC>` (`<Body>`, `<Area>`, `<Sprite>`).
  - `OsuManiaReader` + `NotePacker` + `TimingPointPacker`: Lectura de archivos `.osu` (6K mania, `TimingPoints`, `HitObjects` simples y Hold Notes, `Events`) con empaquetado bit a bit en `long`.
  - `ROTNCore` + `ROTNScene`: Evaluación de taps/hold notes, combo, score, pausa y renderizado vertical de prueba de 6 carriles.
  - `game.core.dialogs.{DialogManager, RPGDialog}`: Variante de diálogos adaptada a `Config` y `ROTNScene` (`__loadr__`).

### En `level_editor/` (D `@nogc nothrow` + `raylib-d` + `raygui`)
- **Infraestructura (`engine/`):** Loop principal (`core.d`, `scene.d`), diálogos nativos con `zenity`/`kdialog` (`tinyfd.d`) y normalización/relativización de rutas (`path_utils.d`).
- **Gestor de Proyectos `.procshit` (`project_manager.d`, `map_editor.d`):** Lista de mapas estilo Tiled (`MAP_ENTRY`), escena XML activa, JSON de diálogos activo, configuración de tileset y celdas de tilemap.
- **Level Editor (`level_data.d`, `xml_scene.d`, `level_editor_tab.d`, `level_hierarchy_panel.d`, `level_inspector_panel.d`):**
  - Edición de `Box` (incluyendo bitmaps 16-bit de `layer` y `mask`), `Sprite` (animado con previsualización) y `Struct` anidados con propiedades custom (`Int`, `Float`, `Bool`, `Str`, `Vec2`).
  - Herramientas `Q` (Select) y `W` (Move), Snap 32px, búsqueda, reordenamiento por grupo/struct, `Ctrl+Z`, `Ctrl+D`, `Ctrl+S`.
- **Dialogs Editor (`dialogs_data.d`, `dialogs_editor_tab.d`):** Editor nodal estilo Visual Shaders (curvas Bezier, pines entrada/salida, `Ctrl+Shift+A`, `Ctrl+Z`, `Ctrl+S`) que carga/exporta JSON con `__load__`, `__save_next__`, `__playsfx__`, `__define__`, `__ifdef__`, `__ifndef__`.
- **Tileset & Tilemap (`tileset_tab.d`, `tilemap_tab.d`):** Activación por tile, edición de hasta `512x512` con lápiz, rectángulo, balde (flood fill), gotero, goma, tile primario/secundario (`L`/`R`) y undo.

---

## 3. Brechas Pendientes Respecto a la Propuesta

1. **Integración completa RPG -> Diálogo -> Combate -> RPG:**
   - Detectar en `RPGScene` cercanía/interacción del `Player` con `NPC.interactArea` (y colisión directa con enemigos) para disparar diálogos o combates.
   - Conectar `ROTNScene.setup()` para leer `<Song file="..."/>` desde el XML de escena, instanciar `OsuManiaReader` + `ROTNCore` y reproducir el `MusicTrack`.
2. **Alineación del motor rítmico con la propuesta:**
   - Ajustar `ROTNScene` si se sigue la dirección horizontal (notas de izquierda a derecha) frente al prototipo vertical actual (`HIT_LINE_Y`).
   - Ajustar ventanas de precisión y puntajes de curación dinámica (`20ms -> +5`, `45ms -> +3`, `60ms -> +1`, `miss -> -2`; actualmente `ROTNCore.HitRating` usa `40/80/120/160ms`).
   - Implementar notas especiales (indicadores de origen/trayectoria con flechas, inhabilitación temporal de carriles/botones), barra de curación dinámica y selección automática óptima/mínima.
3. **Sistemas de Progresión, Power-ups y UI:**
   - `State Manager` persistente entre escenas (llave, puerta de salida, power-ups desbloqueados, flags de diálogo `__define__`).
   - Hub estilo dispositivo celular para configurar power-ups y reglas matemáticas de ventana/coste.
   - Pantalla de `Settings` en `InitScene` (actualmente `settingsBtn()` está vacío).
4. **Exportación de Tilemaps a Java:**
   - `level_editor` guarda el tilemap en `.procshit`, pero todavía no exporta una capa de tiles dentro del XML de escena ni `Scene.java` parsea `<Tileset>` / tilemaps.
5. **Contenido final:**
   - Crear al menos 2 escenarios completos, 3 NPCs híbridos del catálogo, pistas musicales `.wav` + beatmaps `.osu` y sprites finales (hoy solo hay assets de debug/gratuitos).

---

## 4. Bugs y Desajustes Conocidos en el Código Actual

1. **`MusicTrack.play(int startMs, boolean loop)` bloqueado** (`source/combo_6beat/src/main/java/engine/audio/MusicTrack.java` línea 39): tiene `if (_clip != null) return;` en vez de `if (_clip == null) return;`, lo que impide iniciar cualquier BGM. Además `getPositionSeconds()` divide `long / 1_000_000L` perdiendo decimales, y `Audio.resumeBGM()` llama a `play()` en vez de `resume()`.
2. **Duplicidad y desfase en Diálogos:** Coexisten `engine.dialogs.{DialogManager, GameDialog}` (usado por `Scene`) y `game.core.dialogs.{DialogManager, RPGDialog}` (con soporte a `ROTNScene`). Directivas incompatibles: `debug.json` usa `__loadr__`, `engine.dialogs.DialogManager` lee `__loads__`, `game.core.dialogs.DialogManager` lee `__loadr__`, y `level_editor` exporta `__load__`. Además `__playsfx__`, `__define__`, `__ifdef__` e `__ifndef__` aún no tienen runtime en Java.
3. **Desajuste `Config.java` vs `.config`:** El archivo `.config` existente tiene `"BGMMute"`, `"SFXMute"` y `"ControlWorld"`, mientras `Config.java` lee/escribe `"Muted"` y `"ControlRPG"`.
4. **Inconsistencia menor XML en `ResourcesManager.loadBoxes`:** Busca etiqueta `"box"` en minúscula, aunque `Scene.parseBaseNodes` acepta `"Box"`, `"box"` y `"Rect"` y `level_editor` exporta `"Box"`.
5. **Shadowing de `id` / `group` y atributos ignorados:** `Box` redeclara `public String id` y `NPC` redeclara `public String id` y `public String group` ocultando los campos de `GameObject` (`getId()` devuelve `GameObject.id`). `Box.fromXML()` aún no lee `layer` ni `mask` exportados por `level_editor`.
6. **Estado sucio de git en `source/combo_6beat/`:** El working tree tiene eliminados `game/editor/*` y `.idea/*` y modificados archivos del refactor junto a `.class` en `target/` sin commitear.
