---
name: scene-dialog-contracts
description: >-
  Especificación exacta de los formatos compartidos entre level_editor (D) y source/combo_6beat (Java): XML de escenas, JSON de diálogos, archivos .procshit y beatmaps .osu.
  Usar al crear, parsear, exportar o depurar escenas XML, diálogos JSON, mapas de tiles o archivos de proyecto.
---

# Contratos de Datos: Escenas, Diálogos y Proyectos

Referencia autoritativa basada en `level_editor/source/scenes/map_editor/` y `source/combo_6beat/src/main/java/`.

---

## 1. Escenas XML (`resources/scenes/*.xml`)

### Raíz, Tileset y Tilemap
- `level_editor/xml_scene.d` escribe:
  ```xml
  <?xml version="1.0" encoding="UTF-8" ?>
  <Map resources="../">
      <Tileset path="..." tile-width="32" tile-height="32" tile-count="256" />
      <Tilemap width="64" height="36">
          <Cell col="0" row="0" tile="16" />
          ...
      </Tilemap>
      ...
  </Map>
  ```
- Escenas legacy pueden usar `<Scene resources="../">`.
- `ResourcesManager.loadMap(path)` resuelve el atributo `resources` relativo al directorio del archivo XML (`_resourcesBasePath`).
- `Scene.parseBaseNodes` lee `<Tileset>` y `<Tilemap>` creando un `engine.draw.Tilemap2D` al inicio de `_gameObjects`.

### `<Box>` / `<Body>` / `<Area>`
- Emitidos por `xml_scene.d` con: `id`, `group`, `position-x`, `position-y`, `width`, `height`, `layer`, `mask`, `disabled` (`0`/`1`), `type="Box"`.
- En Java:
  - `Scene.parseBaseNodes` acepta `<Box>`, `<box>`, `<Rect>`.
  - `Box.fromXML` lee `id`, `group`, `position-x`, `position-y`, `width`, `height`, `disabled`. *(Nota: aún no lee `layer` ni `mask`).*
  - `RPGScene` añade a `player.collidingBoxes` todo `Box` con `group="Walls"`.

### `<Sprite>`
- Emitido por `xml_scene.d` con: `id`, `group`, `path` (relativo a `resources`), `position-x`, `position-y`, `z-index`, `rows`, `cols`, `scale-x`, `scale-y`, `rotation`, `visible` (`0`/`1`), `delay`, `center-position` (`0`/`1`), `type="Sprite2D"`.
- En Java: `Scene.parseBaseNodes` acepta `<Sprite>` y `<Sprite2D>`. `Sprite2D.fromXML` usa `center-position` con default `1` si se omite.

### `<NPC>` (Struct anidado)
- En `level_editor`, cualquier nodo complejo (`type="GameObject"` o etiqueta distinta de `Box`/`Body`/`Area`/`Sprite`) se trata como `ObjectType.Struct` conservando `xmlTag` y propiedades custom como atributos XML.
- Para un NPC compatible con `game/core/game_objects/NPC.java`:
  - Etiqueta externa: `<NPC id="..." group="NPCs" dialogId="..." type="GameObject">`
  - Hijos directos esperados por `NPC.fromXML`: `<Body .../>`, `<Area .../>`, `<Sprite .../>`.

---

## 2. Grafos de Diálogo JSON (`resources/dialogs/*.json`)

Estructura raíz por ID de diálogo (`"__start__"` es el nodo inicial por convención del editor):

```json
{
  "__start__": {
    "title": "[Narrador]",
    "desc": "Texto del diálogo",
    "default": {
      "__load__": "resources/songs/glass beach.scene",
      "__save_next__": "return"
    },
    "option1": {
      "text": "Opción 1",
      "next": "otro_nodo"
    },
    "option2": {
      "text": "Opción 2"
    }
  }
}
```

### Matriz de Directivas (Editor vs. Runtime Java)

| Clave | `level_editor` (`dialogs_data.d`) | `engine.dialogs.DialogManager` | `game.core.dialogs.DialogManager` | Significado |
| :--- | :--- | :--- | :--- | :--- |
| `next` | Lee / Escribe | Lee | Lee | Salta al ID de diálogo indicado |
| `__save_next__` | Lee / Escribe | Lee | Lee | Guarda el ID para reanudar tras cerrar/volver |
| `__load__` | Lee (`__load__` y `__loadr__`) / Escribe (`__load__`) | No (usa `__loads__`) | No (usa `__loadr__`) | Cargar nueva escena (ruta relativa a raíz del proyecto Java) |
| `__playsfx__` | Lee / Escribe | Pendiente | Pendiente | Reproducir SFX |
| `__define__` | Lee / Escribe | Pendiente | Pendiente | Definir flag/macro de estado |
| `__ifdef__` | Lee / Escribe | Pendiente | Pendiente | Rama condicional si flag definido |
| `__ifndef__` | Lee / Escribe | Pendiente | Pendiente | Rama condicional si flag no definido |

> Al trabajar en el sistema de diálogos en Java, tener en cuenta esta divergencia y priorizar unificar `__load__` y el soporte de directivas.

---

## 3. Proyectos del Editor (`.procshit`)

Formato texto línea por línea `CLAVE=VALOR` gestionado por `level_editor/source/scenes/map_editor/project_manager.d`:
- `RESOURCES_PREFIX`: prefijo `resources` del mapa activo (ej. `../`).
- `ACTIVE_SCENE_XML`, `ACTIVE_DIALOGS_JSON`, `MAP_ENTRY`: rutas relativas al archivo `.procshit`.
- `TILESET_PNG`, `TILESET_TILE_WIDTH`, `TILESET_TILE_HEIGHT`, `TILESET_TILE_COUNT`, `TILESET_INACTIVE` (lista separada por comas).
- `TILEMAP_WIDTH`, `TILEMAP_HEIGHT`, `TILEMAP_CELL=col,row,tileId`.

---

## 4. Canciones y Beatmaps (`.scene` + `.osu`)

- Escena de canción (ej. `resources/songs/glass beach.scene`): `<Scene resources="./"><Song file="./archivo.osu" /></Scene>`.
- `.osu` (osu file format v128, `Mode: 3`, `CircleSize: 6`):
  - `[General]`: `AudioFilename` (debe apuntar a `.wav` para `MusicTrack`).
  - `[TimingPoints]`: `time,beatLength,...,uninherited,...` -> `TimingPointPacker`.
  - `[HitObjects]`: `x,y,time,type,hitSound,endTime:...` -> `lane = constrain((int)(x * 6 / 512.0f), 0, 5)`, `isHold = (type & 128) != 0` -> `NotePacker`.
