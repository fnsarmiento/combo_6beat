# game — Guía de Lógica del Juego (Combo 6Beat)

Capa específica de *Combo 6Beat* construida sobre `engine.*`.

## Punto de Entrada y Configuración

- **`Main` (`game/Main.java`):**
  - Ventana `1920x1080` (`P2D`), resolución virtual `480x270` (`Global.BASE_W`/`BASE_H`), tasa de refresco del monitor (`setFramesToMonitorHZ`), carga `.config`, fuentes de `RPGDialog`, instancia global `Global.player = new Player(100, 100)` y arranca `InitScene`.
- **`Config` (`game/Config.java`):**
  - Define controles 6K (`RhythmButtons = Q,W,E,A,S,D`, `RhythmAltButtons = U,I,O,J,K,L`), `RPGButtons` (`W,S,A,D,E` + flechas/Enter) y `UIButtons` (`W,S,A,D,Space,Esc` + flechas/Enter/Backspace).
  - **Desajuste conocido:** `.config` en disco usa `"BGMMute"`, `"SFXMute"` y `"ControlWorld"`, mientras `Config.java` lee/escribe `"Muted"` y `"ControlRPG"`. Tenerlo en cuenta antes de modificar persistencia.

## Escenas y Entidades

- **`InitScene` (`game/scenes/InitScene.java`):**
  - Menú principal `ControlP5` (`PLAY`, `SETTINGS`, `EXIT`). `PLAY` actualmente lanza `new RPGScene("resources/scenes/a.xml")`. `settingsBtn()` está vacío.
- **`RPGScene` (`game/scenes/RPGScene.java`):**
  - Extiende `Scene`, agrega `Camera2D` centrada en `Global.player` (bloqueando actualización del jugador mientras `DialogManager.isActive()`).
  - `parseCustomNode` soporta `<NPC>` vía `ResourcesManager.loadCustomObject(node, NPC.class)`.
  - Registra como colisionables en `player.collidingBoxes` los `Box` con `group.equals("Walls")` y los `npc.body`.
  - Pendiente: detección de interacción/colisión con `NPC.interactArea` o enemigos para disparar diálogos y transiciones a `ROTNScene`.
- **`Player` y `NPC` (`game/core/game_objects/`):**
  - `Player` extiende `CharacterBody` (`16x16`, `speed = 50.0f`), usa sprite `Idle_Down.png` (`1x8`, `centerPosition = true`).
  - `NPC` implementa `IUpdate` e `IDraw` y serializa `<NPC dialogId="...">` con `<Body>`, `<Area>` y `<Sprite>`. **Cuidado:** `NPC` redeclara `public String id` y `public String group` ocultando los de `GameObject`; mantener constructor vacío `public NPC()` para carga por reflexión.

## Subsistema Rítmico (`game/core/notes/` y `game/scenes/ROTN*`)

- **Empaquetado bit a bit en `long` (sin allocaciones por nota):**
  - `NotePacker`: bits `[0..2]` `lane` (0..5), bit `[3]` `isPressed`, bits `[4..33]` `startTime` (30 bits), bits `[34..63]` `duration` (30 bits).
  - `TimingPointPacker`: bits `[0..29]` `timeMs`, bit `[30]` `isUninherited`, bits `[31..62]` `Float.floatToIntBits(beatLength)`.
- **`OsuManiaReader`:** Parsea `.osu` (secciones `[General]`, `[TimingPoints]`, `[HitObjects]`, `[Events]`), mapeando `x` (`0..512`) a 6 carriles (`0..5`), incluyendo Hold Notes (`type & 128`).
- **`ROTNCore`:** Evalúa entradas de 6 carriles (`HitRating`: `PERFECT=40ms`, `GREAT=80ms`, `GOOD=120ms`, `MISS=160ms`), notas hold (soltar antes o mantener hasta el final), combo, score y pausa (`UIButtons[CANCEL]`).
- **`ROTNScene`:** Dibuja actualmente 6 carriles verticales (`LANE_WIDTH = 28`, `HIT_LINE_Y = 230`, `SCROLL_SPEED = 0.22f px/ms`), aunque `setup()` todavía no lee `<Song>` ni inicializa `_rhythmCore`/`_bgm`.
- **Pendiente vs. propuesta (`Propuesta de Tema.docx`):** Dirección horizontal izquierda->derecha, ventanas `20/45/60ms` para barra de curación dinámica (`+5/+3/+1/-2`), notas especiales con flechas/inhabilitación de botones y hub celular de power-ups.

## Diálogos (`game/core/dialogs/`)

- `game.core.dialogs.DialogManager` + `RPGDialog` duplican parcialmente a `engine.dialogs.*`, pero usan `Config.UIButtons` y soportan `__loadr__` hacia `ROTNScene`. Consolidar con `engine.dialogs` cuando se unifique el sistema de directivas con `level_editor/`.
