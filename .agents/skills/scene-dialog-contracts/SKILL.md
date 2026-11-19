---
name: scene-dialog-contracts
description: >-
  Especificación exacta de los formatos compartidos entre tools/level_editor (D) y game (Java): XML de escenas, JSON de diálogos, archivos .procshit y beatmaps .osu.
  Usar al crear, parsear, exportar o depurar escenas XML, diálogos JSON, mapas de tiles o archivos de proyecto.
---

# Contratos de Datos: Escenas, Diálogos y Proyectos

Referencia autoritativa basada en `tools/level_editor/source/scenes/map_editor/` y `game/src/main/java/`.

---

## 1. Escenas XML (`resources/scenes/*.xml`)

### Raíz, Tileset y Tilemap (Multi-Tileset)
- `level_editor/xml_scene.d` escribe:
  ```xml
  <?xml version="1.0" encoding="UTF-8" ?>
  <Map resources="../" dialogs="dialogs/debug.json">
      <Tileset id="0" path="..." tile-width="32" tile-height="32" tile-count="16" />
      <Tileset id="1" path="..." tile-width="32" tile-height="32" tile-count="32" />
      <Tilemap width="64" height="36">
          <Cell col="0" row="0" tileset="0" tile="12" />
          <Cell col="1" row="0" tileset="1" tile="3" />
          ...
      </Tilemap>
      ...
  </Map>
  ```
- Cada `<Tileset>` define `id` (índice base `0`), `path` relativo al directorio `resources`, `tile-width`, `tile-height` y `tile-count`.
- Cada `<Cell>` indica `col`, `row`, `tileset` (índice del `<Tileset>`) y `tile` (índice local dentro de ese tileset).
- Compatibilidad legacy: escenas antiguas pueden usar `<Scene resources="../" dialogs="...">`, omitir `id` en `<Tileset>` (se asigna secuencialmente desde `0`) y omitir `tileset` en `<Cell>` (toma por defecto `0`, con fallback para índices globales legacy al cargar en el editor).
- `ResourcesManager.loadMap(path)` resuelve el atributo `resources` relativo al directorio del archivo XML (`_resourcesBasePath`).
- `Scene.parseBaseNodes` recopila todos los nodos `<Tileset>` y el `<Tilemap>`, poblando `engine.draw.Tilemap2D` al inicio de `_gameObjects` con empaquetado interno `(tilesetIdx << 16) | localTileId`.

### `<Box>` / `<Body>` / `<Area>`
- Emitidos por `xml_scene.d` con: `id`, `group`, `position-x`, `position-y`, `width`, `height`, `layer`, `mask`, `disabled` (`0`/`1`), `type="Box"`.
- En Java:
  - `Scene.parseBaseNodes` acepta `<Box>`, `<box>`, `<Rect>`.
  - `Box.fromXML` lee `id`, `group`, `position-x`, `position-y`, `width`, `height`, `layer`, `mask`, `disabled`.
  - `RPGScene` añade a `player.collidingBoxes` todo `Box` con `group="Walls"`.

### `<Sprite>`
- Emitido por `xml_scene.d` con: `id`, `group`, `path` (relativo a `resources`), `position-x`, `position-y`, `z-index`, `rows`, `cols`, `scale-x`, `scale-y`, `rotation`, `visible` (`0`/`1`), `delay`, `center-position` (`0`/`1`), `type="Sprite2D"`.
- En Java: `Scene.parseBaseNodes` acepta `<Sprite>` y `<Sprite2D>`. `Sprite2D.fromXML` usa `center-position` con default `1` si se omite.

### Entidades `Struct`: `<Player>`, `<Enemy>`, `<NPC>`, `<Chest>`, `<SceneChanger>` y Pestaña `Prefabs` (`.procshit`)
- En `level_editor`, los nodos complejos (`type="GameObject"` o `type="Struct"`) conservan `xmlTag` y propiedades custom como atributos XML, con presets directos en Jerarquía/Inspector (`+ Player`, `+ Enemigo`, `+ NPC`, `+ Cofre`, `+ Portal`).
- **Prefabs en `.procshit` (`prefabs_tab.d`):**
  - Se crean y editan en la pestaña **`Prefabs`** del editor (`level_editor/source/scenes/map_editor/tabs/prefabs_tab.d`) y se guardan **dentro del archivo `.procshit`** (`PREFAB_DEF`, `PREFAB_OBJ`, `PREFAB_BOX`, `PREFAB_SPRITE`, `PREFAB_PROP`).
  - Desde la pestaña **`Level Editor`** se selecciona cualquier prefab del `.procshit` con `<` / `>` y se instancia con **`+ Instanciar Prefab`** (o se guarda un objeto del mapa hacia `.procshit` con **`Guardar a Prefabs`**). Al exportar la escena a `.xml`, los objetos instanciados se escriben al final del `<Map>` para el motor Java.
- **`<Player>`** (`RPGScene.parseCustomNode` -> `Global.player`):
  - Atributos: `id`, `group`, `speed` (`92.0`), `type="Struct"`.
  - Hijos directos: `<Body position-x="..." position-y="..." width="12" height="12" .../>` (configura spawn y tamaño de hitbox) y `<Sprite .../>`.
- **`<NPC>` / `<Enemy>`** (`game/core/game_objects/NPC.java`):
  - Atributos: `id`, `group`, `dialogId`, `dialogFile`, `itemId`, `itemCount`, `autoTrigger` (`0`/`1`), `songScene`, `type="GameObject"`.
  - Hijos directos esperados por `NPC.fromXML`: `<Body .../>`, `<Area .../>`, `<Sprite .../>`.
- **`<Chest>`** (`game/core/game_objects/Chest.java`):
  - Atributos: `id`, `group`, `itemId`, `itemCount`, `requiredItem`, `dialogId`, `opened` (`0`/`1`), `type="Struct"`.
  - Hijos directos: `<Body .../>`, `<Area .../>`, `<Sprite .../>` (opcional; si falta se dibuja un cofre pixel art por defecto).
- **`<SceneChanger>`** (`game/core/game_objects/SceneChanger.java`):
  - Atributos: `id`, `group`, `targetScene` (ruta `.xml` o `.scene`), `spawnX`, `spawnY`, `triggerOnTouch` (`0`/`1`), `requiredItem`, `requiredFlag`, `type="Struct"`.
  - Hijos directos: `<Area .../>`, `<Body .../>` (opcional), `<Sprite .../>` (opcional; si falta se dibuja un marcador de portal por defecto).

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

`engine.dialogs.DialogManager` es la única implementación activa y se conecta a `game.core.GameState` mediante callbacks (`GameState.bindDialogHooks()`).

| Clave | `level_editor` (`dialogs_data.d`) | `engine.dialogs.DialogManager` | Significado |
| :--- | :--- | :--- | :--- |
| `next` | Lee / Escribe | Lee | Salta al ID de diálogo indicado |
| `__save_next__` | Lee / Escribe | Lee | Guarda el ID para reanudar tras ganar o cerrar |
| `__save_next_lose__` | Preserva | Lee | Guarda el ID para reanudar si el combate no se ganó (derrota/huida) |
| `__load__` | Lee (`__load__`, `__loadr__`, `__loads__`) / Escribe (`__load__`) | Lee (`__load__`, `__loadr__`, `__loads__`) | Cargar nueva escena (`.scene` o `.xml`, ruta relativa) |
| `__playsfx__` | Lee / Escribe | Lee | Reproducir SFX vía `Audio` |
| `__define__` | Lee / Escribe | Lee | Definir flag/macro de estado |
| `__ifdef__` | Lee / Escribe | Lee | Rama condicional si flag definido (o redirección a nivel nodo con `__next_ifdef__`) |
| `__ifndef__` | Lee / Escribe | Lee | Rama condicional si flag no definido (o redirección a nivel nodo con `__next_ifndef__`) |
| `__give_item__` | Lee / Escribe | Lee | Entrega ítem al jugador (`itemId` o `itemId:count`) |

---

## 3. Proyectos del Editor (`.procshit`)

Formato texto línea por línea `CLAVE=VALOR` gestionado por `level_editor/source/scenes/map_editor/project_manager.d`:
- `RESOURCES_PREFIX`: prefijo `resources` del mapa activo (ej. `../`).
- `ACTIVE_SCENE_XML`, `ACTIVE_DIALOGS_JSON`, `MAP_ENTRY`: rutas relativas al archivo `.procshit`.
- Configuración multi-tileset:
  - `TILESET_COUNT`: número de slots activos.
  - `TILESET_ENTRY=idx,tileWidth,tileHeight,tileCount,path`: definición de cada tileset (con `path` relativo a `resources`).
  - `TILESET_ENTRY_INACTIVE=idx,id1,id2,...`: IDs locales inactivos separados por comas para el tileset `idx`.
- Claves legacy espejo del slot `0` (mantienen compatibilidad): `TILESET_PNG`, `TILESET_TILE_WIDTH`, `TILESET_TILE_HEIGHT`, `TILESET_TILE_COUNT`, `TILESET_INACTIVE` (lista separada por comas).
- `TILEMAP_WIDTH`, `TILEMAP_HEIGHT`, `TILEMAP_CELL=col,row,tilesetIdx,localTileId` (al cargar acepta también el formato legacy de 3 enteros `col,row,tileId` asumiendo `tilesetIdx=0`).

---

## 4. Canciones y Beatmaps (`.scene` + `.osu`)

- Escena de canción (ej. `resources/songs/glass beach.scene`):
  - `<Scene resources="../" return-scene="resources/scenes/beach_ruins.xml">`
  - `<Song file="songs/archivo.osu" scroll-speed="0.22" />`
  - `<Boss id="..." name="..." species="..." max-hp="450" attack-damage="12" hit-damage="6" sprite="..." rows="1" cols="8" scale-x="1.8" scale-y="1.8" delay="0.08" position-x="415" position-y="118" defeat-flag="..." reward-item="key_exit" />`
  - `<Combat player-max-hp="100" heal-threshold="50" heal-active-duration="6.0" lane-lock-interval="10000" lane-lock-duration="2800" />`
- `.osu` (osu file format v128, `Mode: 3`, `CircleSize: 6`):
  - `[General]`: `AudioFilename` (se resuelve primero relativo al directorio del `.osu` y como fallback en `resources/songs/`).
  - `[TimingPoints]`: `time,beatLength,...,uninherited,...` -> `TimingPointPacker`.
  - `[HitObjects]`: `x,y,time,type,hitSound,endTime:...` -> `lane = constrain((int)(x * 6 / 512.0f), 0, 5)`, `isHold = (type & 128) != 0`, `noteType` (`0=NORMAL`, `1=RANGED_ORIGIN`, `2=SHIFT_UP`, `3=SHIFT_DOWN`) -> `NotePacker`.
  - `[Combo6Beat]` *(opcional dentro del `.osu`)*: claves `BossName`, `BossSpecies`, `BossMaxHP`, `BossSprite`, `BossRows`, `BossCols`, `BossAttackDamage`, `BossHitDamage`, `ReturnScene`, `DefeatFlag`, `RewardItem` (los atributos explícitos de `.scene` tienen prioridad si están presentes).
