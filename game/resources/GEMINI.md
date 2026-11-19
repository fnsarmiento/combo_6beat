# resources — Guía de Assets, Escenas, Diálogos y Canciones

Directorio de recursos en runtime de `source/combo_6beat/`.

## Resolución de Rutas

- Cuando `ResourcesManager.loadMap(path)` carga un archivo XML en `resources/scenes/*.xml`, lee el atributo `resources="..."` de la etiqueta raíz (`<Map>` o `<Scene>`).
- En los XML dentro de `resources/scenes/`, usar `resources="../"` para que la base de recursos apunte a `resources/`.
- Todas las rutas internas (`path` de `<Sprite>`, `<Tileset>`, `ResourcesManager.loadJSON(...)`, etc.) deben ser **relativas a `resources/`** (por ejemplo: `debug/sprites/32x32.png`, `dialogs/debug.json`, `sprites/player/...`).
- Excepción actual: las directivas de cambio de escena en diálogos (`__load__`/`__loadr__`/`__loads__`) usan `ResourcesManager.getAbsPathFromProject(val)`, es decir, relativas a la raíz de `source/combo_6beat/` (por ejemplo: `resources/songs/glass beach.scene`).

## Formatos y Contratos

- **Escenas XML (`resources/scenes/`):**
  - Raíz `<Map resources="../">` (generado por `level_editor/`) o `<Scene resources="../">` (legacy).
  - Nodos reconocidos por `Scene.parseBaseNodes`: `<Box>`/`<box>`/`<Rect>`, `<Sprite>`/`<Sprite2D>`.
  - Nodos custom reconocidos por `RPGScene`: `<NPC>` con hijos `<Body>`, `<Area>` y `<Sprite>`.
  - `<Tileset>` exportado por `level_editor/` aún no es consumido por `Scene.java`.
- **Diálogos JSON (`resources/dialogs/`):**
  - Generados visualmente por la pestaña `Dialogs Editor` de `level_editor/`.
  - Estructura de nodos por ID (`"__start__"`, etc.) con `title`, `desc`, y ramas `default`, `option1`, `option2`.
  - Ver tabla exacta de directivas (`next`, `__save_next__`, `__load__`/`__loadr__`/`__loads__`, `__playsfx__`, `__define__`, `__ifdef__`, `__ifndef__`) en `.agents/skills/scene-dialog-contracts/SKILL.md`.
- **Canciones y Beatmaps (`resources/songs/`):**
  - Escena de canción (ej. `glass beach.scene`) + archivo `.osu` 6K (`CircleSize: 6`, `Mode: 3`).
  - **Audio BGM:** `MusicTrack` usa `javax.sound.sampled.AudioSystem`, por lo que requiere formato `.wav` (no `.mp3` directo aunque exista `audio.mp3` en disco).
- **Spritesheets (`resources/sprites/`, `resources/debug/sprites/`):**
  - PNGs en cuadrícula uniforme (`rows`, `cols`); `Sprite2D` recorta cada frame en orden fila-columna.
  - En `debug/sprites/` existen fuentes `.aseprite` junto al PNG exportado `32x32.png`.
