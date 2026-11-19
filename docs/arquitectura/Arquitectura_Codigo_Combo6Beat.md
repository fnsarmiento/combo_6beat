# Arquitectura del Código — Combo 6Beat

> [!tip] Vista Interactiva en Obsidian
> Abre el archivo visual [[Arquitectura_Codigo_Combo6Beat.canvas]] en Obsidian para explorar el grafo interactivo con nodos agrupados por paquete, colores y flechas de dependencias.

---

## 1. Panorama General del Proyecto

El proyecto se divide en un **editor nativo externo en D** (`level_editor/`), un **directorio de contratos/assets** (`resources/`) y el **juego en Java 17 + Processing 4** (`source/combo_6beat/`), el cual a su vez separa estrictamente el motor reutilizable (`engine.*`) de la lógica específica del juego (`game.*`).

```mermaid
flowchart LR
    subgraph Editor["level_editor/ (D @nogc + raylib-d + raygui)"]
        ME[map_editor.d]
        XML_D[xml_scene.d]
        DLG_D[dialogs_data.d]
        PRJ_D[project_manager.d]
        ME --> XML_D & DLG_D & PRJ_D
    end

    subgraph Contratos["resources/ (Rutas Relativas)"]
        XML["Escenas .xml\n(Box, Sprite2D, Tilemap2D,\nNPC, Chest, SceneChanger)"]
        JSON["Diálogos .json\n(Grafo nodal + directivas)"]
        OSU["Beatmaps .osu\n(osu!mania 6K)"]
        PROC[".procshit\n(Proyecto del editor)"]
    end

    subgraph Juego["source/combo_6beat/ (Java 17 + Processing 4)"]
        GAME["game.*\n(Main, Escenas, Entidades,\nGameState, Ritmo)"]
        ENGINE["engine.*\n(Engine, Scene, Física,\nGráficos, Audio, Diálogos)"]
        GAME --> ENGINE
    end

    XML_D -->|exporta| XML
    DLG_D -->|exporta| JSON
    PRJ_D -->|guarda| PROC
    XML & JSON -->|carga| ENGINE
    OSU -->|parsea| GAME
```

---

## 2. Arquitectura de Clases Java (`engine.*` vs `game.*`)

Regla clave del proyecto: **`engine.*` nunca importa `game.*`**. Toda especialización ocurre por herencia (`extends Scene`, `extends GameObject`, `extends CharacterBody`) o carga por reflexión (`ResourcesManager.loadCustomObject`).

```mermaid
classDiagram
    class PApplet
    class Main {
        +setup()
        +draw()
    }
    Main --|> PApplet
    Main --> Engine : inicializa
    Main --> Config : carga .config
    Main --> Global : crea Global.player
    Main --> InitScene : escena inicial

    class Engine {
        +changeScene(Scene)
        +pre()
        +draw()
    }
    class Scene {
        +setup()
        +update(dt)
        +draw(PGraphics)
        +parseBaseNodes(XML)
        +parseCustomNode(XML)
    }
    Engine --> Scene : gestiona

    class InitScene
    class RPGScene
    class ROTNScene
    InitScene --|> Scene
    RPGScene --|> Scene
    ROTNScene --|> Scene

    class GameObject {
        <<abstract>>
        +String id
        +String group
        +toXML()
        +fromXML()
    }
    class Box
    class CharacterBody
    class Sprite2D
    class Tilemap2D
    Box --|> GameObject
    CharacterBody --|> Box
    Sprite2D --|> GameObject
    Tilemap2D --|> GameObject

    class Player
    class NPC
    class Chest
    class SceneChanger
    Player --|> CharacterBody
    NPC --|> GameObject
    Chest --|> GameObject
    SceneChanger --|> GameObject

    RPGScene --> Player
    RPGScene --> NPC
    RPGScene --> Chest
    RPGScene --> SceneChanger
    RPGScene --> GameState

    ROTNScene --> ROTNCore
    ROTNScene --> OsuManiaReader
    OsuManiaReader --> NotePacker
    OsuManiaReader --> TimingPointPacker
```

---

## 3. Flujo de Escenas y Ciclo de Vida

```mermaid
stateDiagram-v2
    [*] --> Main : Arranque Processing (1920x1080 -> 480x270)
    Main --> InitScene : Engine.changeScene

    InitScene --> RPGScene : Play / Continue / Debug
    InitScene --> ROTNScene : Modo Freeplay

    RPGScene --> RPGScene : SceneChanger (otro mapa .xml)
    RPGScene --> ROTNScene : Diálogo (__loadr__) / SceneChanger rítmico
    ROTNScene --> RPGScene : Fin o salida del combate rítmico
```

---

## 4. Mapa Rápido de Paquetes

| Paquete / Módulo | Rol Principal | Archivos Clave |
| :--- | :--- | :--- |
| `game` | Entrada, configuración y estado global | [Main.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/Main.java), [Config.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/Config.java), [Global.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/Global.java) |
| `game.core` | Estado persistente, inventario, power-ups, guardado | [GameState.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/GameState.java) |
| `game.scenes` | Menú, exploración RPG y combate rítmico 6K | [InitScene.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/scenes/InitScene.java), [RPGScene.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/scenes/RPGScene.java), [ROTNScene.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/scenes/ROTNScene.java), [ROTNCore.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/scenes/ROTNCore.java) |
| `game.core.game_objects` | Entidades interactivas del mundo RPG | [Player.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/game_objects/Player.java), [NPC.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/game_objects/NPC.java), [Chest.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/game_objects/Chest.java), [SceneChanger.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/game_objects/SceneChanger.java) |
| `game.core.notes` | Lectura de `.osu` y empaquetado bitwise en `long` | [OsuManiaReader.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/notes/OsuManiaReader.java), [NotePacker.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/notes/NotePacker.java), [TimingPointPacker.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/notes/TimingPointPacker.java) |
| `engine` | Loop principal, escena base, recursos, teclado | [Engine.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/Engine.java), [Scene.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/Scene.java), [ResourcesManager.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/ResourcesManager.java), [KBInput.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/KBInput.java) |
| `engine.draw` & `physics` | Render 2D, cámara, tilemaps y colisiones AABB | [Sprite2D.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/draw/Sprite2D.java), [Tilemap2D.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/draw/Tilemap2D.java), [Camera2D.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/draw/Camera2D.java), [CharacterBody.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/physics/CharacterBody.java) |
| `engine.audio` & `dialogs` | SFX/BGM y sistema de diálogos JSON | [Audio.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/audio/Audio.java), [MusicTrack.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/audio/MusicTrack.java), [DialogManager.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/engine/dialogs/DialogManager.java) |
| `level_editor` (D) | Editor externo de escenas XML, diálogos JSON y tilemaps | [map_editor.d](file:///home/sar/Documentos/Combo%206Beat/level_editor/source/scenes/map_editor/map_editor.d), [xml_scene.d](file:///home/sar/Documentos/Combo%206Beat/level_editor/source/scenes/map_editor/xml_scene.d), [dialogs_data.d](file:///home/sar/Documentos/Combo%206Beat/level_editor/source/scenes/map_editor/dialogs_data.d) |
