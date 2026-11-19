# level_editor — Guía para IA (Dlang + raylib-d + raygui)

Editor nativo de niveles, tilesets, tilemaps y grafos de diálogo para *Combo 6Beat*. Sustituye al antiguo editor Java que existía dentro de `source/combo_6beat/`.

## Stack y Comandos

- **Lenguaje:** D (`dub.json`), compilación gestionada con DUB (`dub build` desde `level_editor/`).
- **Dependencias:** `raylib-d` (`~>6.2.0`), librería del sistema `raylib` y `libraygui.a` (bindings en `source/raygui.d`).
- **Diálogos de archivo nativos:** Implementados en `source/engine/tinyfd.d` invocando `zenity` o `kdialog` vía `popen` (no usa la C library `tinyfiledialogs`).

## Restricciones Obligatorias de Código (`@nogc nothrow`)

Todos los módulos `.d` del proyecto declaran `nothrow: @nogc:`. Debes respetar estrictamente:
1. **Prohibido usar el GC de D:** No usar `new`, strings dinámicos (`string`), concatenación `~`, arrays dinámicos (`T[]` con `.length = ...` o `~=`), `std.file`, `std.json`, `std.conv`, ni excepciones (`throw`).
2. **Memoria y strings estilo C:**
   - Usar buffers fijos (`char[N]`), `core.stdc.stdio : snprintf, FILE, fopen, ...`, `core.stdc.string : strlen, strncpy, strcmp, strstr, strchr` y `core.stdc.stdlib : malloc, free, atoi, strtof`.
   - Inicializar buffers antes de usar y asegurar terminación nula (`buf[buf.length - 1] = '\0'`).
3. **Capacidades fijas conocidas** (si se amplían, revisar impacto en memoria estática `__gshared` y pilas de Undo):
   - `MAX_LEVEL_OBJECTS = 256`, `MAX_CUSTOM_PROPS = 16`, `MAX_LEVEL_UNDO = 24`.
   - `MAX_MAP_WIDTH = 512`, `MAX_MAP_HEIGHT = 512`, `MAX_TILESET_TILES = 1024`.
   - `MAX_DIALOG_NODES = 128`, `MAX_DIALOG_LINKS = 256`, `MAX_DIALOG_UNDO = 16`.
   - `MAX_PROJECT_MAPS = 64`.

## Mapa de Módulos

- `source/app.d`: Entry point y loop principal (`WindowShouldClose`).
- `source/engine/`:
  - `core.d`: Ventana `1920x1080`, `SetTargetFPS(75)`, cambio de escena.
  - `scene.d`: Struct `Scene` con delegates `@nogc nothrow` (`setup`, `update`, `draw`, `close`).
  - `path_utils.d`: Normalización y cálculo de rutas relativas (`path_make_relative_to_resources`, `path_resolve_for_loading`).
  - `tinyfd.d`: Selectores de archivos `.png`, `.xml`, `.json`, `.procshit`.
- `source/scenes/map_editor/`:
  - `map_editor.d`: Shell principal con 4 pestañas (`Tilemap`, `Tileset`, `Dialogs Editor`, `Level Editor`) y barra superior de proyecto `.procshit`.
  - `project_manager.d`: Lectura/escritura del formato texto clave=valor `.procshit` (mapas, escena activa, JSON de diálogos activo, tileset, celdas de tilemap).
  - `camera_controller.d`: Pan, zoom hacia cursor y grilla infinita 32px.
  - `gui_widgets.d`: Helpers `raygui` y foco global `active_field_id` / `gui_is_editing_field()`.
  - `level_data.d` + `xml_scene.d`: Modelo en memoria (`Box`, `Sprite`, `Struct`) y parser/serializador XML propio.
  - `dialogs_data.d`: Grafo nodal, Undo y parser/serializador JSON propio.
  - `tabs/`: `level_editor_tab.d`, `level_hierarchy_panel.d`, `level_inspector_panel.d`, `dialogs_editor_tab.d`, `tileset_tab.d`, `tilemap_tab.d`.

## Convenciones Críticas al Editar

- **IDs de campos GUI (`FIELD_*`):** Cada widget editable (`gui_labeled_text_box`, `gui_search_box`, `gui_labeled_int_spinner`, etc.) usa un entero único en `gui_widgets.d` (`active_field_id`). Verifica que no colisionen rangos al añadir campos (`1000+` Tileset, `2000+` Tilemap, `3000+` Hierarchy, `4000-5999` Inspector, `6000+` Toolbar XML, `7000+` Dialogs).
- **Atajos de teclado:** Mientras `gui_is_editing_field()` sea `true`, no disparar atajos de una sola letra (`Q`, `W`).
- **Rutas siempre relativas:** Toda ruta almacenada en `SpriteData.resourcePath`, `<Tileset path="...">`, `.procshit` o JSON debe pasar por `path_utils.d` para mantenerse relativa al XML o al `.procshit`.
- **Diferido de borrado/reorden en listas UI:** En `level_hierarchy_panel.d` y `level_inspector_panel.d`, guardar acciones destructivas (`pending_delete_uid`, `deletePropIndex`) y ejecutarlas fuera del loop de iteración/scissor.
- **No distribuir binarios compilados:** El ejecutable `level_editor/level_editor` está en `.gitignore`; no modificar `libraygui.a` salvo actualización explícita de `raygui`.
