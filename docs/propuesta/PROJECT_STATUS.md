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
  - `Audio` + `MusicTrack` + `kuusisto.tinysound`: Caché/reproducción de SFX con TinySound y estructura de BGM vía `javax.sound.sampled.Clip` (corregidos `play()`, `getPositionSeconds()` y `resumeBGM()`).
  - `engine.dialogs.{DialogManager, GameDialog}`: Motor unificado de diálogos con tipeo animado, opciones, callbacks de estado (`onLoadRequest`, `onGiveItemRequest`, `onDefineFlag`, `onCheckFlag`), `__load__`/`__loadr__`/`__loads__`, `__save_next__`, `__playsfx__`, `__define__`, `__ifdef__`, `__ifndef__`, `__give_item__` y `next`.
- **Juego (`game/`):**
  - `Main` + `Global` + `Config` + `GameState`: Carga/guardado de `.config` JSON (compatibilidad con claves legacy `BGMMute`/`SFXMute`/`ControlWorld`), catálogo persistente de ítems y power-ups (`HealStrategy.OPTIMAL`/`MINIMAL`), flags de historia e inventario (`consumo automático`, escudo de invencibilidad, llave de salida).
  - `InitScene`: Menú inicial funcional con `PLAY` (carga `resources/scenes/beach_ruins.xml`), `SETTINGS` (vacío) y `EXIT`.
  - `RPGScene` + `Player` + `NPC` + `Chest` + `SceneChanger`: Movimiento del jugador, cámara centrada, colisiones con `Walls`/`NPC`/`Chest`, interacción `[E]` y `autoTrigger`, entrega de ítems y power-ups por cofres/NPCs/diálogos, portales `SceneChanger` con validación opcional de ítem/flag y coordenadas de spawn, más Hub estilo Celular (`TAB`/`C`) en el overworld.
  - `OsuManiaReader` + `NotePacker` + `TimingPointPacker`: Lectura de `.osu` (6K mania, `TimingPoints`, `HitObjects` con `noteType` para notas normales, de origen/rango y con cambio de trayectoria `^`/`v`, `Events`, y sección opcional `[Combo6Beat]`).
  - `ROTNCore` + `ROTNScene`: Combate rítmico horizontal de izquierda a derecha en 6 carriles, ventanas `20ms/+5`, `45ms/+3`, `60ms/+1`, `miss/-2`, barra de curación dinámica + selección automática `OPTIMAL`/`MINIMAL`, ataque periódico del jefe con advertencia e inhabilitación temporal de carril, panel animado del jefe (HP, sprite, daño, recompensa y `return-scene` desde XML `<Boss>`/`<Combat>`).

### En `level_editor/` (D `@nogc nothrow` + `raylib-d` + `raygui`)
- **Infraestructura (`engine/`):** Loop principal (`core.d`, `scene.d`), diálogos nativos con `zenity`/`kdialog` (`tinyfd.d`) y normalización/relativización de rutas (`path_utils.d`).
- **Gestor de Proyectos `.procshit` (`project_manager.d`, `map_editor.d`):** Lista de mapas estilo Tiled (`MAP_ENTRY`), escena XML activa, JSON de diálogos activo, configuración de tileset y celdas de tilemap.
- **Level Editor (`level_data.d`, `xml_scene.d`, `level_editor_tab.d`, `level_hierarchy_panel.d`, `level_inspector_panel.d`):**
  - Edición de `Box` (incluyendo bitmaps 16-bit de `layer` y `mask`), `Sprite` (animado con previsualización) y `Struct` anidados con propiedades custom (`Int`, `Float`, `Bool`, `Str`, `Vec2`), traducción conjunta por gizmo/inspector (`level_world_translate_struct`), atributos raíz `dialogs` en `<Map>` y presets rápidos (`NPC`, `Chest`, `SceneChanger`).
  - Herramientas `Q` (Select) y `W` (Move), Snap 32px, búsqueda, reordenamiento por grupo/struct, `Ctrl+Z`, `Ctrl+D`, `Ctrl+S`.
- **Dialogs Editor (`dialogs_data.d`, `dialogs_editor_tab.d`):** Editor nodal estilo Visual Shaders (curvas Bezier, pines entrada/salida, `Ctrl+Shift+A`, `Ctrl+Z`, `Ctrl+S`) que carga/exporta JSON con `__load__`, `__save_next__`, `__playsfx__`, `__define__`, `__ifdef__`, `__ifndef__` y `__give_item__`.
- **Tileset & Tilemap (`tileset_tab.d`, `tilemap_tab.d`):** Activación por tile, edición de hasta `512x512` con lápiz, rectángulo, balde (flood fill), gotero, goma, tile primario/secundario (`L`/`R`) y undo.

---

## 3. Brechas Pendientes Respecto a la Propuesta

1. **Pantalla `Settings` y pulido visual:**
   - Implementar la pantalla de `Settings` en `InitScene` (actualmente `settingsBtn()` está vacío) para rebindear teclas/audio en runtime.
   - Mejorar animaciones/feedback visual de entidades (cofres con sprites dedicado, animaciones de ataque del jefe por fase).
2. **Exportación de Tilemaps:**
   - `Scene.java` lee `<Tileset>` y `<Tilemap>` del XML de escena, y `level_editor` persiste `TILESET_*` y `TILEMAP_*` en `.procshit`; conviene asegurar flujo automático de sincronización `.procshit` <-> XML desde el editor para mapas nuevos.
3. **Contenido final:**
   - Crear al menos 2 escenarios completos, 3 NPCs híbridos del catálogo (*Raby*, *Nash*, *Grail*, *Creig*, *Larc*, *Raúl*), pistas musicales `.wav` + beatmaps `.osu` y sprites finales (hoy solo hay assets de debug/gratuitos).

---

## 4. Bugs y Desajustes Conocidos (Histórico / Estado)

- **Resueltos en esta etapa:**
  - Bug en `MusicTrack.play()` (`if (_clip == null)`), precisión decimal en `getPositionSeconds()` y `Audio.resumeBGM()`.
  - Unificación del sistema de diálogos sobre `engine.dialogs.DialogManager` (soporte de `__load__`/`__loadr__`/`__loads__`, `__playsfx__`, `__define__`, `__ifdef__`, `__ifndef__`, `__give_item__`, y eliminación de dependencias cruzadas `engine -> game`).
  - Compatibilidad de `Config.java` con `"BGMMute"`, `"SFXMute"` y `"ControlWorld"`.
  - Aceptación de `"Box"`, `"box"` y `"Rect"` en `ResourcesManager.loadBoxes`, y lectura/escritura de `layer`/`mask` en `Box.fromXML`/`toXML`.
  - Eliminación del shadowing de `id`/`group` en `Box`, `Sprite2D` y `NPC`.
- **Pendiente menor:**
  - `InitScene.settingsBtn()` sigue vacío hasta implementar el menú de configuración in-game.
