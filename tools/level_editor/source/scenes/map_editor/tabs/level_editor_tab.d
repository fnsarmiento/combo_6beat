module scenes.map_editor.tabs.level_editor_tab;

nothrow:
@nogc:

import raylib;
import raygui;
import core.stdc.stdio : snprintf;
import core.stdc.stdlib : system;
import core.stdc.string : strcmp;
import core.stdc.math : floorf;
import engine.tinyfd : open_xml_file_dialog, save_xml_file_dialog, open_json_file_dialog;
import scenes.map_editor.level_data;
import scenes.map_editor.gui_widgets;
import scenes.map_editor.camera_controller;
import scenes.map_editor.xml_scene : xml_load_scene, xml_save_scene;
import scenes.map_editor.project_manager : project_add_map;
import scenes.map_editor.tabs.level_hierarchy_panel;
import scenes.map_editor.tabs.level_inspector_panel;
import scenes.map_editor.tabs.tileset_tab :
    tileset_reload_all_textures,
    tileset_is_packed_tile_active,
    tileset_draw_packed_tile;
import scenes.map_editor.tabs.tilemap_tab :
    tilemap_get_width,
    tilemap_get_height,
    tilemap_get_cell;

public:

enum LevelTool : int {
    Select  = 0, // Shortcut: Q
    Move    = 1, // Shortcut: W
    DrawBox = 2  // Shortcut: B
}

/**
 * Inicializa el Level Editor (datos, cámara, panel izquierdo de jerarquía y panel derecho de inspector).
 */
void level_editor_tab_init() {
    ref LevelWorld world = get_active_level_world();
    level_world_init(world);
    hierarchy_panel_init();
    inspector_panel_init();
    ed_camera.initialized = false;
    dragging_uid = -1;
    resizing_uid = -1;
    resizing_handle = -1;
    creating_box = false;
    active_tool = cast(int) LevelTool.Select;
    snap_32px = false;
    only_select_boxes = false;
    save_feedback_timer = 0.0f;
}

/**
 * Retorna el centro actual del viewport 2D en coordenadas de mundo (para instanciar objetos).
 */
Vector2 level_editor_get_camera_center() {
    if (ed_camera.initialized) {
        return ed_camera.camera.target;
    }
    return Vector2(160.0f, 160.0f);
}

/**
 * Libera recursos del Level Editor.
 */
void level_editor_tab_close() {
    ref LevelWorld world = get_active_level_world();
    level_world_clear(world);
}

/**
 * Guarda la escena XML actual (usado por Ctrl+S y por el botón Guardar XML).
 */
void level_editor_save_scene_shortcut() {
    ref LevelWorld world = get_active_level_world();
    gui_clear_active_field();

    if (world.xmlFilePath[0] != '\0') {
        if (xml_save_scene(world.xmlFilePath.ptr, world)) {
            project_add_map(world.xmlFilePath.ptr);
            save_feedback_timer = 2.0f;
        }
    } else {
        char[512] xmlPath;
        if (save_xml_file_dialog(xmlPath.ptr, xmlPath.length, "Guardar Escena XML")) {
            if (xml_save_scene(xmlPath.ptr, world)) {
                project_add_map(xmlPath.ptr);
                save_feedback_timer = 2.0f;
            }
        }
    }
}

/**
 * Actualiza animaciones, atajos de teclado (Q, W, Ctrl+S) e interacciones del Level Editor.
 */
void level_editor_tab_update(float dt) {
    ref LevelWorld world = get_active_level_world();
    level_world_update_animations(world, dt);

    if (save_feedback_timer > 0.0f) {
        save_feedback_timer -= dt;
    }

    bool ctrlDown = IsKeyDown(KeyboardKey.KEY_LEFT_CONTROL) || IsKeyDown(KeyboardKey.KEY_RIGHT_CONTROL);

    // Atajo Ctrl+S para guardar escena XML
    if (ctrlDown && IsKeyPressed(KeyboardKey.KEY_S)) {
        level_editor_save_scene_shortcut();
        return;
    }

    // Atajo Ctrl+Z para deshacer en el Level Editor (independiente de los otros tabs)
    if (ctrlDown && IsKeyPressed(KeyboardKey.KEY_Z)) {
        gui_clear_active_field();
        level_world_undo(world);
        return;
    }

    // Atajo Ctrl+D para duplicar el objeto seleccionado en el Level Editor
    if (ctrlDown && IsKeyPressed(KeyboardKey.KEY_D)) {
        if (world.selectedUid >= 0) {
            gui_clear_active_field();
            level_world_duplicate_object(world, world.selectedUid);
        }
        return;
    }

    // Atajos tipo Godot: Q (Seleccionar), W (Mover), B (+Box) y Delete (Borrar) cuando no se está escribiendo texto
    if (!ctrlDown && !gui_is_editing_field()) {
        if (IsKeyPressed(KeyboardKey.KEY_Q)) {
            active_tool = cast(int) LevelTool.Select;
            dragging_uid = -1;
            resizing_uid = -1;
            creating_box = false;
        } else if (IsKeyPressed(KeyboardKey.KEY_W)) {
            active_tool = cast(int) LevelTool.Move;
            creating_box = false;
        } else if (IsKeyPressed(KeyboardKey.KEY_B)) {
            active_tool = cast(int) LevelTool.DrawBox;
            dragging_uid = -1;
            resizing_uid = -1;
        } else if (IsKeyPressed(KeyboardKey.KEY_DELETE) && world.selectedUid >= 0) {
            level_world_remove_object(world, world.selectedUid);
            dragging_uid = -1;
            resizing_uid = -1;
        }
    }
}

/**
 * Dibuja el Level Editor completo:
 * - Panel izquierdo: Lista scrolleable de objetos agrupados, buscador, botón '+', subir/bajar y papelera.
 * - Centro: Barra de herramientas (Q/W, Snap 32px, Cargar/Guardar XML, resources) + Viewport 2D con cámara.
 * - Panel derecho: Inspector de propiedades según el tipo de objeto seleccionado.
 */
void level_editor_tab_draw(Rectangle workspace) {
    ref LevelWorld world = get_active_level_world();

    float leftPanelWidth  = 380.0f;
    float rightPanelWidth = 420.0f;
    float pad             = 10.0f;

    Rectangle leftPanel = Rectangle(
        workspace.x + pad,
        workspace.y + pad,
        leftPanelWidth,
        workspace.height - pad * 2.0f
    );

    Rectangle rightPanel = Rectangle(
        workspace.x + workspace.width - rightPanelWidth - pad,
        workspace.y + pad,
        rightPanelWidth,
        workspace.height - pad * 2.0f
    );

    Rectangle centerArea = Rectangle(
        leftPanel.x + leftPanel.width + pad,
        workspace.y + pad,
        workspace.width - leftPanelWidth - rightPanelWidth - pad * 4.0f,
        workspace.height - pad * 2.0f
    );

    draw_center_area(centerArea, world);
    hierarchy_panel_draw(leftPanel, world);
    inspector_panel_draw(rightPanel, world);
}

private:

enum int FIELD_MAP_RESOURCES_REL = 6001;
enum int FIELD_MAP_DIALOGS_REL   = 6002;

__gshared EditorCamera ed_camera;
__gshared int dragging_uid = -1;
__gshared Vector2 drag_offset = Vector2(0, 0);
__gshared int resizing_uid = -1;
__gshared int resizing_handle = -1; // 0=TL, 1=T, 2=TR, 3=R, 4=BR, 5=B, 6=BL, 7=L
__gshared Rectangle resize_orig_rect = Rectangle(0, 0, 0, 0);
__gshared Vector2 resize_start_mouse = Vector2(0, 0);
__gshared bool creating_box = false;
__gshared Vector2 create_box_start = Vector2(0, 0);
__gshared Vector2 create_box_end = Vector2(0, 0);
__gshared int active_tool = 0; // 0 = Q (Select), 1 = W (Move), 2 = B (+Box)
__gshared bool snap_32px = false;
__gshared bool only_select_boxes = false;
__gshared float save_feedback_timer = 0.0f;

float snap_grid(float v) {
    return floorf((v + 8.0f) / 16.0f) * 16.0f;
}

void draw_center_area(Rectangle bounds, ref LevelWorld world) {
    float topBarHeight = 42.0f;

    Rectangle toolbarBounds = Rectangle(
        bounds.x,
        bounds.y,
        bounds.width,
        topBarHeight
    );

    Rectangle viewportPanelBounds = Rectangle(
        bounds.x,
        bounds.y + topBarHeight + 6.0f,
        bounds.width,
        bounds.height - topBarHeight - 6.0f
    );

    draw_xml_and_camera_toolbar(toolbarBounds, viewportPanelBounds, world);
    draw_level_viewport(viewportPanelBounds, world);
}

void draw_xml_and_camera_toolbar(Rectangle barBounds, Rectangle viewportPanelBounds, ref LevelWorld world) {
    GuiPanel(barBounds, null);

    float x = barBounds.x + 8.0f;
    float y = barBounds.y + 8.0f;
    float h = 26.0f;

    // Selector de Herramienta: [Q] Sel | [W] Mov | [B] +Box
    GuiToggleGroup(Rectangle(x, y, 70.0f, h), "[Q] Sel;[W] Mov;[B] +Box", &active_tool);
    x += 218.0f;

    // Modo exacto de 16 píxeles (Snap 16px)
    GuiToggle(Rectangle(x, y, 72.0f, h), GuiIconText(GuiIconName.ICON_GRID, "16px"), &snap_32px);
    x += 76.0f;

    // Filtro Solo Box (ignorar Sprites al hacer click en el viewport)
    GuiToggle(Rectangle(x, y, 78.0f, h), "Solo Box", &only_select_boxes);
    x += 84.0f;

    // Botón Cargar XML
    if (GuiButton(Rectangle(x, y, 88.0f, h), GuiIconText(GuiIconName.ICON_FOLDER_OPEN, "Cargar"))) {
        char[512] xmlPath;
        if (open_xml_file_dialog(xmlPath.ptr, xmlPath.length, "Cargar Escena XML")) {
            gui_clear_active_field();
            if (xml_load_scene(xmlPath.ptr, world)) {
                project_add_map(xmlPath.ptr);
            }
        }
    }
    x += 92.0f;

    // Botón Guardar XML (Ctrl+S)
    if (GuiButton(Rectangle(x, y, 92.0f, h), GuiIconText(GuiIconName.ICON_FILE_SAVE, "Guardar"))) {
        char[512] xmlPath;
        if (save_xml_file_dialog(xmlPath.ptr, xmlPath.length, "Guardar Escena XML")) {
            gui_clear_active_field();
            if (xml_save_scene(xmlPath.ptr, world)) {
                project_add_map(xmlPath.ptr);
                save_feedback_timer = 2.0f;
            }
        }
    }
    x += 96.0f;

    // Botón Importar JSON (convierte map.json de PixelLab a .xml usando el script de Python y lo abre)
    if (GuiButton(Rectangle(x, y, 118.0f, h), GuiIconText(GuiIconName.ICON_FILE_EXPORT, "Importar JSON"))) {
        char[512] jsonPath;
        if (open_json_file_dialog(jsonPath.ptr, jsonPath.length, "Seleccionar Escena JSON (PixelLab map.json)")) {
            char[512] xmlPath;
            if (save_xml_file_dialog(xmlPath.ptr, xmlPath.length, "Guardar Escena XML Convertida (.xml)")) {
                gui_clear_active_field();
                const(char)* scriptPath = FileExists("scripts/import_pixellab_scene.py")
                    ? "scripts/import_pixellab_scene.py"
                    : "../scripts/import_pixellab_scene.py";
                char[1536] cmd;
                snprintf(
                    cmd.ptr,
                    cmd.length,
                    "python3 \"%s\" \"%s\" --xml-out \"%s\"",
                    scriptPath,
                    jsonPath.ptr,
                    xmlPath.ptr
                );
                int rc = system(cmd.ptr);
                if (rc == 0 && FileExists(xmlPath.ptr)) {
                    if (xml_load_scene(xmlPath.ptr, world)) {
                        project_add_map(xmlPath.ptr);
                        save_feedback_timer = 2.0f;
                    }
                }
            }
        }
    }
    x += 124.0f;

    // Atributo <Map resources="..."> y <Map dialogs="..."> relativos al archivo XML
    GuiLabel(Rectangle(x, y, 36.0f, h), "res:");
    x += 36.0f;

    if (gui_search_box(Rectangle(x, y, 76.0f, h), world.resourcesRelPath.ptr, cast(int) world.resourcesRelPath.length, FIELD_MAP_RESOURCES_REL)) {
        level_world_reload_all_textures(world);
        tileset_reload_all_textures(false);
    }
    x += 82.0f;

    GuiLabel(Rectangle(x, y, 36.0f, h), "dlg:");
    x += 36.0f;
    gui_search_box(Rectangle(x, y, 96.0f, h), world.dialogsRelPath.ptr, cast(int) world.dialogsRelPath.length, FIELD_MAP_DIALOGS_REL);
    x += 102.0f;

    // Botón para resetear la cámara a (0, 0)
    if (GuiButton(Rectangle(x, y, 76.0f, h), GuiIconText(GuiIconName.ICON_ZOOM_CENTER, "(0,0)"))) {
        Rectangle viewArea = get_inner_view_area(viewportPanelBounds);
        editor_camera_reset(ed_camera, viewArea);
    }
}

Rectangle get_inner_view_area(Rectangle panelBounds) {
    return Rectangle(
        panelBounds.x + 2.0f,
        panelBounds.y + 24.0f,
        panelBounds.width - 4.0f,
        panelBounds.height - 26.0f
    );
}

void draw_level_viewport(Rectangle bounds, ref LevelWorld world) {
    GuiPanel(bounds, "Vista del Nivel 2D (Q: Seleccionar | W: Mover | Ctrl+S: Guardar | Click Der/Medio: Camara)");

    Rectangle viewArea = get_inner_view_area(bounds);

    editor_camera_update(ed_camera, viewArea);
    handle_viewport_object_interaction(viewArea, world);

    BeginScissorMode(
        cast(int) viewArea.x,
        cast(int) viewArea.y,
        cast(int) viewArea.width,
        cast(int) viewArea.height
    );

    DrawRectangleRec(viewArea, Color(28, 31, 37, 255));

    BeginMode2D(ed_camera.camera);

    // Dibujar el Tilemap de fondo en el Level Editor si tiene celdas pintadas
    draw_tilemap_background_in_level(viewArea);

    // Grilla del mundo (de 32px) y ejes en (0, 0)
    editor_camera_draw_world_grid(ed_camera, viewArea);

    // Pasada 1: Dibujar Sprites al fondo
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].type != ObjectType.Sprite) {
            continue;
        }
        LevelObject* obj = &world.objects[i];
        bool isSelected = (obj.uid == world.selectedUid);
        draw_world_sprite(*obj, isSelected);
        if (isSelected && active_tool == cast(int) LevelTool.Move) {
            draw_move_gizmo(world, *obj);
        }
    }

    // Pasada 2: Dibujar Boxes y Structs encima para editar colisiones con claridad
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].type == ObjectType.Sprite) {
            continue;
        }
        LevelObject* obj = &world.objects[i];
        bool isSelected = (obj.uid == world.selectedUid);

        if (obj.type == ObjectType.Box) {
            draw_world_box(*obj, isSelected);
            if (isSelected) {
                draw_box_resize_handles(*obj);
            }
        } else if (obj.type == ObjectType.Struct) {
            draw_world_struct(world, *obj, isSelected);
        }

        if (isSelected && active_tool == cast(int) LevelTool.Move) {
            draw_move_gizmo(world, *obj);
        }
    }

    // Previsualización de nueva Box al arrastrar en modo [B] +Box
    if (creating_box) {
        Rectangle preview = make_normalized_rect(create_box_start, create_box_end);
        DrawRectangleRec(preview, Color(80, 255, 130, 80));
        DrawRectangleLinesEx(preview, 2.0f / ed_camera.camera.zoom, Color(80, 255, 130, 255));
    }

    EndMode2D();

    // HUD informativo de herramienta, modo 16px, zoom y coordenadas
    Vector2 mouseWorld = editor_camera_screen_to_world(ed_camera, GetMousePosition());
    const(char)* toolName = (active_tool == cast(int) LevelTool.Move)
        ? "W: Mover/Resize"
        : ((active_tool == cast(int) LevelTool.DrawBox) ? "B: +Box (Arrastrar)" : "Q: Sel/Resize");
    const(char)* snapName = snap_32px ? "ON (16px)" : "OFF";

    char[192] hudText;
    snprintf(
        hudText.ptr,
        hudText.length,
        "%s | Snap: %s | Zoom: %.0f%% | Mundo: (%.0f, %.0f)",
        toolName,
        snapName,
        ed_camera.camera.zoom * 100.0f,
        mouseWorld.x,
        mouseWorld.y
    );
    DrawRectangle(cast(int) viewArea.x + 8, cast(int)(viewArea.y + viewArea.height - 28), 460, 22, Color(18, 20, 24, 210));
    DrawText(hudText.ptr, cast(int) viewArea.x + 14, cast(int)(viewArea.y + viewArea.height - 24), 13, Colors.RAYWHITE);

    if (save_feedback_timer > 0.0f) {
        DrawRectangle(cast(int) viewArea.x + 8, cast(int) viewArea.y + 8, 240, 26, Color(35, 130, 60, 230));
        DrawText("Escena XML guardada (Ctrl+S)", cast(int) viewArea.x + 16, cast(int) viewArea.y + 14, 14, Colors.RAYWHITE);
    }

    EndScissorMode();
}

void draw_tilemap_background_in_level(Rectangle viewArea) {
    int mapW = tilemap_get_width();
    int mapH = tilemap_get_height();
    if (mapW <= 0 || mapH <= 0) {
        return;
    }

    enum float CELL_SIZE = 32.0f;
    Vector2 worldTL = GetScreenToWorld2D(Vector2(viewArea.x, viewArea.y), ed_camera.camera);
    Vector2 worldBR = GetScreenToWorld2D(Vector2(viewArea.x + viewArea.width, viewArea.y + viewArea.height), ed_camera.camera);

    int minCol = cast(int) floorf(worldTL.x / CELL_SIZE) - 1;
    int maxCol = cast(int) floorf(worldBR.x / CELL_SIZE) + 1;
    int minRow = cast(int) floorf(worldTL.y / CELL_SIZE) - 1;
    int maxRow = cast(int) floorf(worldBR.y / CELL_SIZE) + 1;

    if (minCol < 0) minCol = 0;
    if (maxCol >= mapW) maxCol = mapW - 1;
    if (minRow < 0) minRow = 0;
    if (maxRow >= mapH) maxRow = mapH - 1;

    for (int r = minRow; r <= maxRow; r++) {
        for (int c = minCol; c <= maxCol; c++) {
            int cellTile = tilemap_get_cell(c, r);
            if (cellTile >= 0 && tileset_is_packed_tile_active(cellTile)) {
                Rectangle cellRect = Rectangle(c * CELL_SIZE, r * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                tileset_draw_packed_tile(cellTile, cellRect);
            }
        }
    }
}

void draw_move_gizmo(ref const(LevelWorld) world, ref const(LevelObject) obj) {
    Vector2 pos;
    if (obj.type == ObjectType.Box) {
        pos = obj.box.position;
    } else if (obj.type == ObjectType.Sprite) {
        pos = obj.sprite.position;
    } else if (!level_world_get_struct_anchor(world, obj.uid, pos)) {
        return;
    }

    float len = 36.0f / ed_camera.camera.zoom;
    float thick = 2.5f / ed_camera.camera.zoom;
    DrawLineEx(pos, Vector2(pos.x + len, pos.y), thick, Color(255, 80, 80, 255));
    DrawLineEx(pos, Vector2(pos.x, pos.y + len), thick, Color(80, 255, 100, 255));
    DrawCircleV(pos, 4.5f / ed_camera.camera.zoom, Colors.YELLOW);
}

Rectangle make_normalized_rect(Vector2 a, Vector2 b) {
    float x0 = (a.x < b.x) ? a.x : b.x;
    float y0 = (a.y < b.y) ? a.y : b.y;
    float x1 = (a.x > b.x) ? a.x : b.x;
    float y1 = (a.y > b.y) ? a.y : b.y;
    float w = x1 - x0;
    float h = y1 - y0;
    if (w < 4.0f) w = 4.0f;
    if (h < 4.0f) h = 4.0f;
    return Rectangle(x0, y0, w, h);
}

Rectangle get_box_handle_rect(ref const(BoxData) box, int handleIdx) {
    float hs = 8.0f / ed_camera.camera.zoom;
    if (hs < 3.0f) hs = 3.0f;
    float x = box.position.x;
    float y = box.position.y;
    float w = box.size.x;
    float h = box.size.y;
    float cx = x + w * 0.5f;
    float cy = y + h * 0.5f;

    Vector2 pt = Vector2(x, y);
    switch (handleIdx) {
        case 0: pt = Vector2(x, y); break;         // TL
        case 1: pt = Vector2(cx, y); break;        // T
        case 2: pt = Vector2(x + w, y); break;     // TR
        case 3: pt = Vector2(x + w, cy); break;    // R
        case 4: pt = Vector2(x + w, y + h); break; // BR
        case 5: pt = Vector2(cx, y + h); break;    // B
        case 6: pt = Vector2(x, y + h); break;     // BL
        case 7: pt = Vector2(x, cy); break;        // L
        default: break;
    }
    return Rectangle(pt.x - hs * 0.5f, pt.y - hs * 0.5f, hs, hs);
}

void draw_box_resize_handles(ref const(LevelObject) obj) {
    for (int h = 0; h < 8; h++) {
        Rectangle hr = get_box_handle_rect(obj.box, h);
        DrawRectangleRec(hr, Color(255, 230, 50, 255));
        DrawRectangleLinesEx(hr, 1.2f / ed_camera.camera.zoom, Color(30, 30, 30, 255));
    }
}

int hit_test_box_resize_handle(ref const(BoxData) box, Vector2 mouseWorld) {
    for (int h = 0; h < 8; h++) {
        Rectangle hr = get_box_handle_rect(box, h);
        // Margen extra para facilitar el agarre con el mouse
        Rectangle pad = Rectangle(hr.x - 2.0f, hr.y - 2.0f, hr.width + 4.0f, hr.height + 4.0f);
        if (CheckCollisionPointRec(mouseWorld, pad)) {
            return h;
        }
    }
    return -1;
}

void apply_box_resize_handle(ref BoxData box, int handleIdx, Rectangle orig, Vector2 delta) {
    float x0 = orig.x;
    float y0 = orig.y;
    float x1 = orig.x + orig.width;
    float y1 = orig.y + orig.height;

    if (handleIdx == 0 || handleIdx == 6 || handleIdx == 7) {
        x0 += delta.x;
        if (snap_32px) x0 = snap_grid(x0);
        if (x0 > x1 - 4.0f) x0 = x1 - 4.0f;
    }
    if (handleIdx == 2 || handleIdx == 3 || handleIdx == 4) {
        x1 += delta.x;
        if (snap_32px) x1 = snap_grid(x1);
        if (x1 < x0 + 4.0f) x1 = x0 + 4.0f;
    }
    if (handleIdx == 0 || handleIdx == 1 || handleIdx == 2) {
        y0 += delta.y;
        if (snap_32px) y0 = snap_grid(y0);
        if (y0 > y1 - 4.0f) y0 = y1 - 4.0f;
    }
    if (handleIdx == 4 || handleIdx == 5 || handleIdx == 6) {
        y1 += delta.y;
        if (snap_32px) y1 = snap_grid(y1);
        if (y1 < y0 + 4.0f) y1 = y0 + 4.0f;
    }

    box.position = Vector2(x0, y0);
    box.size = Vector2(x1 - x0, y1 - y0);
}

void handle_viewport_object_interaction(Rectangle viewArea, ref LevelWorld world) {
    Vector2 mouseScreen = GetMousePosition();
    bool insideView = CheckCollisionPointRec(mouseScreen, viewArea);
    Vector2 mouseWorld = editor_camera_screen_to_world(ed_camera, mouseScreen);

    if (IsMouseButtonReleased(MouseButton.MOUSE_BUTTON_LEFT)) {
        if (creating_box) {
            creating_box = false;
            Rectangle r = make_normalized_rect(create_box_start, create_box_end);
            if (r.width >= 4.0f && r.height >= 4.0f) {
                int newUid = level_world_add_object(world, ObjectType.Box, -1);
                LevelObject* created = level_world_find(world, newUid);
                if (created !is null) {
                    snprintf(created.group.ptr, created.group.length, "Walls");
                    created.box.position = Vector2(r.x, r.y);
                    created.box.size = Vector2(r.width, r.height);
                    created.box.layer = 1;
                    created.box.mask = 1;
                    world.selectedUid = newUid;
                }
            }
        }
        dragging_uid = -1;
        resizing_uid = -1;
        resizing_handle = -1;
    }

    // 1. Actualizar creación de nueva Box arrastrando en modo [B] +Box
    if (creating_box && IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT)) {
        float ex = mouseWorld.x;
        float ey = mouseWorld.y;
        if (snap_32px) {
            ex = snap_grid(ex);
            ey = snap_grid(ey);
        }
        create_box_end = Vector2(ex, ey);
        return;
    }

    // 2. Actualizar redimensionado por tiradores de la Box seleccionada
    if (resizing_uid >= 0 && resizing_handle >= 0 && IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT)) {
        LevelObject* resObj = level_world_find(world, resizing_uid);
        if (resObj !is null && resObj.type == ObjectType.Box) {
            Vector2 delta = Vector2(mouseWorld.x - resize_start_mouse.x, mouseWorld.y - resize_start_mouse.y);
            apply_box_resize_handle(resObj.box, resizing_handle, resize_orig_rect, delta);
        }
        return;
    }

    // 3. Mover objeto si estamos en herramienta Move (W)
    if (active_tool == cast(int) LevelTool.Move &&
        dragging_uid >= 0 &&
        IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT)) {
        LevelObject* dragged = level_world_find(world, dragging_uid);
        if (dragged !is null) {
            float targetX = mouseWorld.x - drag_offset.x;
            float targetY = mouseWorld.y - drag_offset.y;

            if (snap_32px) {
                targetX = snap_grid(targetX);
                targetY = snap_grid(targetY);
            }

            if (dragged.type == ObjectType.Box) {
                dragged.box.position = Vector2(targetX, targetY);
            } else if (dragged.type == ObjectType.Sprite) {
                dragged.sprite.position = Vector2(targetX, targetY);
            } else if (dragged.type == ObjectType.Struct) {
                Vector2 curAnchor;
                if (level_world_get_struct_anchor(world, dragged.uid, curAnchor)) {
                    level_world_translate_struct(world, dragged.uid, Vector2(targetX - curAnchor.x, targetY - curAnchor.y));
                }
            }
        }
        return;
    }

    if (!insideView || IsKeyDown(KeyboardKey.KEY_SPACE)) {
        return;
    }

    if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_LEFT)) {
        // Modo [B] +Box: iniciar rectángulo nuevo
        if (active_tool == cast(int) LevelTool.DrawBox) {
            gui_clear_active_field();
            float sx = mouseWorld.x;
            float sy = mouseWorld.y;
            if (snap_32px) {
                sx = snap_grid(sx);
                sy = snap_grid(sy);
            }
            creating_box = true;
            create_box_start = Vector2(sx, sy);
            create_box_end = Vector2(sx + 16.0f, sy + 16.0f);
            return;
        }

        // Primero verificar si se hizo click sobre alguno de los 8 tiradores de resize de la Box ya seleccionada
        if (world.selectedUid >= 0) {
            LevelObject* curSel = level_world_find(world, world.selectedUid);
            if (curSel !is null && curSel.type == ObjectType.Box) {
                int hIdx = hit_test_box_resize_handle(curSel.box, mouseWorld);
                if (hIdx >= 0) {
                    gui_clear_active_field();
                    level_world_push_undo(world);
                    resizing_uid = curSel.uid;
                    resizing_handle = hIdx;
                    resize_orig_rect = Rectangle(curSel.box.position.x, curSel.box.position.y, curSel.box.size.x, curSel.box.size.y);
                    resize_start_mouse = mouseWorld;
                    return;
                }
            }
        }

        // Buscar objeto clickeado (priorizando Boxes y Structs sobre Sprites)
        int hitUid = -1;
        for (int pass = 0; pass < 2 && hitUid < 0; pass++) {
            for (int i = MAX_LEVEL_OBJECTS - 1; i >= 0; i--) {
                if (!world.objects[i].used) {
                    continue;
                }
                LevelObject* obj = &world.objects[i];
                if (only_select_boxes && obj.type == ObjectType.Sprite) {
                    continue;
                }
                if (pass == 0 && obj.type == ObjectType.Sprite) {
                    continue;
                }
                if (pass == 1 && obj.type != ObjectType.Sprite) {
                    continue;
                }
                Rectangle hitBox = get_object_world_bounds_in_world(world, *obj);
                if (hitBox.width > 0 && hitBox.height > 0 && CheckCollisionPointRec(mouseWorld, hitBox)) {
                    hitUid = obj.uid;
                    break;
                }
            }
        }

        if (hitUid >= 0) {
            gui_clear_active_field();
            world.selectedUid = hitUid;

            if (active_tool == cast(int) LevelTool.Move) {
                LevelObject* obj = level_world_find(world, hitUid);
                if (obj !is null) {
                    level_world_push_undo(world);
                    dragging_uid = hitUid;
                    if (obj.type == ObjectType.Box) {
                        drag_offset = Vector2(mouseWorld.x - obj.box.position.x, mouseWorld.y - obj.box.position.y);
                    } else if (obj.type == ObjectType.Sprite) {
                        drag_offset = Vector2(mouseWorld.x - obj.sprite.position.x, mouseWorld.y - obj.sprite.position.y);
                    } else {
                        Vector2 anchor;
                        if (level_world_get_struct_anchor(world, obj.uid, anchor)) {
                            drag_offset = Vector2(mouseWorld.x - anchor.x, mouseWorld.y - anchor.y);
                        }
                    }
                }
            }
        } else if (active_tool == cast(int) LevelTool.Move && world.selectedUid >= 0) {
            LevelObject* sel = level_world_find(world, world.selectedUid);
            if (sel !is null) {
                if (sel.type == ObjectType.Box) {
                    level_world_push_undo(world);
                    dragging_uid = sel.uid;
                    drag_offset = Vector2(mouseWorld.x - sel.box.position.x, mouseWorld.y - sel.box.position.y);
                } else if (sel.type == ObjectType.Sprite && !only_select_boxes) {
                    level_world_push_undo(world);
                    dragging_uid = sel.uid;
                    drag_offset = Vector2(mouseWorld.x - sel.sprite.position.x, mouseWorld.y - sel.sprite.position.y);
                } else if (sel.type == ObjectType.Struct) {
                    Vector2 anchor;
                    if (level_world_get_struct_anchor(world, sel.uid, anchor)) {
                        level_world_push_undo(world);
                        dragging_uid = sel.uid;
                        drag_offset = Vector2(mouseWorld.x - anchor.x, mouseWorld.y - anchor.y);
                    }
                }
            }
        }
    }
}

Rectangle get_object_world_bounds_in_world(ref const(LevelWorld) world, ref const(LevelObject) obj) {
    if (obj.type == ObjectType.Struct) {
        Vector2 anchor;
        if (level_world_get_struct_anchor(world, obj.uid, anchor)) {
            return Rectangle(anchor.x - 10.0f, anchor.y - 10.0f, 20.0f, 20.0f);
        }
        return Rectangle(0, 0, 0, 0);
    }
    return get_object_world_bounds(obj);
}

Rectangle get_object_world_bounds(ref const(LevelObject) obj) {
    if (obj.type == ObjectType.Box) {
        return Rectangle(obj.box.position.x, obj.box.position.y, obj.box.size.x, obj.box.size.y);
    } else if (obj.type == ObjectType.Sprite) {
        int cols = (obj.sprite.cols > 0) ? obj.sprite.cols : 1;
        int rows = (obj.sprite.rows > 0) ? obj.sprite.rows : 1;
        float baseW = (obj.sprite.hasTexture && obj.sprite.texture.id != 0)
            ? (cast(float) obj.sprite.texture.width / cols)
            : 64.0f;
        float baseH = (obj.sprite.hasTexture && obj.sprite.texture.id != 0)
            ? (cast(float) obj.sprite.texture.height / rows)
            : 64.0f;

        float w = baseW * obj.sprite.scale.x;
        float h = baseH * obj.sprite.scale.y;
        float x = obj.sprite.centerPosition ? (obj.sprite.position.x - w * 0.5f) : obj.sprite.position.x;
        float y = obj.sprite.centerPosition ? (obj.sprite.position.y - h * 0.5f) : obj.sprite.position.y;
        return Rectangle(x, y, w, h);
    }
    return Rectangle(0, 0, 0, 0);
}

void draw_world_box(ref const(LevelObject) obj, bool isSelected) {
    Rectangle rect = Rectangle(
        obj.box.position.x,
        obj.box.position.y,
        obj.box.size.x,
        obj.box.size.y
    );

    Color fillCol = obj.box.disabled
        ? Color(110, 110, 110, 70)
        : Color(50, 160, 240, 85);

    Color borderCol = isSelected
        ? Color(255, 220, 40, 255)
        : (obj.box.disabled ? Color(150, 150, 150, 180) : Color(70, 190, 255, 230));

    DrawRectangleRec(rect, fillCol);
    DrawRectangleLinesEx(rect, isSelected ? 2.5f : 1.5f, borderCol);
    DrawText(obj.id.ptr, cast(int)(rect.x + 4), cast(int)(rect.y + 4), 12, borderCol);
}

void draw_world_struct(ref const(LevelWorld) world, ref const(LevelObject) obj, bool isSelected) {
    Vector2 anchor;
    if (!level_world_get_struct_anchor(world, obj.uid, anchor)) {
        return;
    }

    const(char)* tag = (obj.xmlTag[0] != '\0') ? obj.xmlTag.ptr : "Struct";
    Color badgeCol = Color(185, 195, 215, 220);
    if (strcmp(tag, "Player") == 0) {
        badgeCol = Color(80, 255, 120, 240);
    } else if (strcmp(tag, "Enemy") == 0) {
        badgeCol = Color(255, 80, 95, 240);
    } else if (strcmp(tag, "NPC") == 0) {
        badgeCol = Color(50, 220, 235, 240);
    } else if (strcmp(tag, "Chest") == 0) {
        badgeCol = Color(245, 195, 55, 240);
    } else if (strcmp(tag, "SceneChanger") == 0) {
        badgeCol = Color(225, 90, 235, 240);
    }

    Color borderCol = isSelected ? Colors.YELLOW : badgeCol;
    Rectangle marker = Rectangle(anchor.x - 8.0f, anchor.y - 8.0f, 16.0f, 16.0f);
    DrawRectangleRec(marker, Color(badgeCol.r, badgeCol.g, badgeCol.b, 95));
    DrawRectangleLinesEx(marker, isSelected ? 2.5f : 1.5f, borderCol);

    const(char)* detail = null;
    if (strcmp(tag, "Chest") == 0) {
        detail = level_struct_find_prop_str(obj.customStruct, "itemId");
    } else if (strcmp(tag, "SceneChanger") == 0) {
        detail = level_struct_find_prop_str(obj.customStruct, "targetScene");
    } else if (strcmp(tag, "NPC") == 0 || strcmp(tag, "Enemy") == 0) {
        detail = level_struct_find_prop_str(obj.customStruct, "dialogId");
    }

    char[192] label;
    if (detail !is null && detail[0] != '\0') {
        snprintf(label.ptr, label.length, "[%s] %s -> %s", tag, obj.id.ptr, detail);
    } else {
        snprintf(label.ptr, label.length, "[%s] %s", tag, obj.id.ptr);
    }
    DrawText(label.ptr, cast(int)(anchor.x - 10), cast(int)(anchor.y - 22), 11, borderCol);
}

void draw_world_sprite(ref const(LevelObject) obj, bool isSelected) {
    if (!obj.sprite.visible && !isSelected) {
        return;
    }

    int cols = (obj.sprite.cols > 0) ? obj.sprite.cols : 1;
    int rows = (obj.sprite.rows > 0) ? obj.sprite.rows : 1;

    float posX = obj.sprite.position.x;
    float posY = obj.sprite.position.y;

    if (obj.sprite.hasTexture && obj.sprite.texture.id != 0) {
        float frameW = cast(float) obj.sprite.texture.width / cols;
        float frameH = cast(float) obj.sprite.texture.height / rows;

        int frameCol = obj.sprite.currentFrame % cols;
        int frameRow = obj.sprite.currentFrame / cols;

        Rectangle srcRec = Rectangle(
            frameCol * frameW,
            frameRow * frameH,
            frameW,
            frameH
        );

        float destW = frameW * obj.sprite.scale.x;
        float destH = frameH * obj.sprite.scale.y;

        Rectangle dstRec = Rectangle(posX, posY, destW, destH);
        Vector2 pivot = obj.sprite.centerPosition
            ? Vector2(destW * 0.5f, destH * 0.5f)
            : Vector2(0.0f, 0.0f);

        Color tint = obj.sprite.visible ? Colors.WHITE : Color(255, 255, 255, 100);
        DrawTexturePro(obj.sprite.texture, srcRec, dstRec, pivot, obj.sprite.rotation, tint);

        if (isSelected) {
            DrawCircle(cast(int) posX, cast(int) posY, 4.0f, Colors.YELLOW);
        }
    } else {
        float baseW = 64.0f * obj.sprite.scale.x;
        float baseH = 64.0f * obj.sprite.scale.y;
        Rectangle dstRec = Rectangle(posX, posY, baseW, baseH);
        Vector2 pivot = obj.sprite.centerPosition
            ? Vector2(baseW * 0.5f, baseH * 0.5f)
            : Vector2(0.0f, 0.0f);

        Color col = isSelected ? Color(180, 110, 255, 160) : Color(140, 90, 210, 120);
        DrawRectanglePro(dstRec, pivot, obj.sprite.rotation, col);
        DrawCircle(cast(int) posX, cast(int) posY, 3.0f, isSelected ? Colors.YELLOW : Colors.RAYWHITE);
        DrawText(obj.id.ptr, cast(int)(posX + 6), cast(int)(posY + 6), 12, Colors.RAYWHITE);
    }
}
