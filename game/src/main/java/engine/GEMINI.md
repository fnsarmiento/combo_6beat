# engine — Guía del Motor Base (Java + Processing)

Módulo genérico y desacoplado de la lógica particular de *Combo 6Beat*. **Regla absoluta:** ninguna clase bajo `engine.*` debe importar paquetes `game.*`.

## Componentes y Contratos

- **`Engine` (`engine/Engine.java`):**
  - Registra `pre`, `draw` y `keyEvent` sobre `PApplet`.
  - `_cp5.setAutoDraw(false)`: los controladores `ControlP5` se dibujan después de escalar `_virtualScreen` a la ventana física.
  - `changeScene(Scene)` ejecuta `close()` en la escena anterior antes de `setup()` en la nueva.
- **`Scene` (`engine/Scene.java`):**
  - Constructor `Scene(String path)` carga el XML vía `ResourcesManager.loadMap(path)`.
  - `setup()` conecta `DialogManager.setOnLoadRequest`, llama a `parseBaseNodes(_xmlScene)` (soporta `Box`/`box`/`Rect`, `Sprite`/`Sprite2D` y delega el resto a `parseCustomNode(XML)`), y si el XML tiene atributo `dialogs` + `start-dialog`, arranca el diálogo inicial.
  - `update(dt)` pausa la actualización de `_gameObjects` mientras `DialogManager.isActive()` sea `true`.
  - Toda UI `ControlP5` creada en una escena debe registrarse con `registerUI(...)` para limpiarse automáticamente en `close()`.
- **`ResourcesManager` (`engine/ResourcesManager.java`):**
  - `loadMap(path)` resuelve la ruta respecto al directorio del proyecto y actualiza `_resourcesBasePath` leyendo el atributo `resources` de la etiqueta raíz (por defecto `"resources/"`).
  - `loadCustomObject(XML, Class<T>)` exige un **constructor público sin argumentos** en cualquier subclase de `GameObject` instanciada por reflexión.
  - `loadImage(path)` cachea `PImage` por ruta absoluta resuelta desde `_resourcesBasePath`.
  - Nota: `loadBoxes(XML)` busca etiqueta `"box"` en minúscula, a diferencia de `Scene.parseBaseNodes` que acepta `"Box"`, `"box"` y `"Rect"`.
- **Objetos, Gráficos y Física:**
  - `GameObject` (`id`, `group`, `toXML()`, `fromXML()`), `IUpdate`, `IDraw`, `Drawable`.
  - `Sprite2D`: frames por `rows`/`cols`, `delay`, `centerPosition` (`1` por defecto en `fromXML` si falta el atributo), `zIndex` en `position.z`.
  - `Camera2D`: orden de transformaciones `rotate(rotation)` -> `scale(scale)` -> `translate(-position.x, -position.y)`.
  - `Box` + `CharacterBody`: colisiones AABB (`intersects`) y `moveAndSlide(dt)` con sub-stepping por ejes. **Cuidado:** `Box` redeclara `public String id` ocultando `GameObject.id`; al usar `getId()`/`setId()` se accede al campo de `GameObject`.
- **Input (`KBInput`):**
  - `update()` copia `_currentKeys` a `_previousKeys` al final de `Engine.pre()`, permitiendo `wasKeyJustPressed` y `wasKeyJustReleased` en el frame actual. Índices `0..255`.
- **Audio (`engine/audio/` + `kuusisto/tinysound/`):**
  - `Audio` maneja SFX con `TinySound` (`.wav` cacheado) y BGM con `MusicTrack` (`javax.sound.sampled.Clip`, WAV).
  - **Bug activo en `MusicTrack.java`:** en `play(int startMs, boolean loop)` (línea 39) dice `if (_clip != null) return;` (debe ser `== null`). Además `getPositionSeconds()` usa división entera `1_000_000L` y `Audio.resumeBGM()` llama `play()` en vez de `resume()`. No modificar `kuusisto/tinysound/` salvo necesidad explícita.
- **Diálogos (`engine/dialogs/`):**
  - `DialogManager` + `GameDialog` (escala sobre `BASE_WIDTH = 480f`, `BASE_HEIGHT = 270f`, teclas `KEYS_INTERACT`/`LEFT`/`RIGHT`).
  - Actualmente lee `__save_next__`, `next` y `__loads__`. Ver `PROJECT_STATUS.md` sobre su coexistencia con `game.core.dialogs`.
