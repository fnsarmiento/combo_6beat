module scenes.map_editor.tabs.level_editor_tab;

nothrow:
@nogc:

import raylib;
import raygui;
import core.stdc.stdio : snprintf;
import core.stdc.math : floorf;
import engine.tinyfd : open_xml_file_dialog, save_xml_file_dialog;
import scenes.map_editor.level_data;
import scenes.map_editor.gui_widgets;
import scenes.map_editor.camera_controller;
import scenes.map_editor.xml_scene : xml_load_scene, xml_save_scene;
import scenes.map_editor.project_manager : project_add_map;
import scenes.map_editor.tabs.level_hierarchy_panel;
import scenes.map_editor.tabs.level_inspector_panel;
import scenes.map_editor.tabs.tileset_tab :
    tileset_reload_texture,
    tileset_is_tile_active,
    tileset_draw_tile;
import scenes.map_editor.tabs.tilemap_tab :
    tilemap_get_width,
    tilemap_get_height,
    tilemap_get_cell;

public:

enum LevelTool : int {
    Select = 0, // Shortcut: Q
    Move   = 1  // Shortcut: W
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
    active_tool = cast(int) LevelTool.Select;
    snap_32px = false;
    save_feedback_timer = 0.0f;
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

    // Atajos tipo Godot: Q (Seleccionar) y W (Mover) cuando no se está escribiendo texto
    if (!ctrlDown && !gui_is_editing_field()) {
        if (IsKeyPressed(KeyboardKey.KEY_Q)) {
            active_tool = cast(int) LevelTool.Select;
            dragging_uid = -1;
        } else if (IsKeyPressed(KeyboardKey.KEY_W)) {
            active_tool = cast(int) LevelTool.Move;
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

__gshared EditorCamera ed_camera;
__gshared int dragging_uid = -1;
__gshared Vector2 drag_offset = Vector2(0, 0);
__gshared int active_tool = 0; // 0 = Q (Select), 1 = W (Move)
__gshared bool snap_32px = false;
__gshared float save_feedback_timer = 0.0f;

float snap_to_32(float v) {
    return floorf((v + 16.0f) / 32.0f) * 32.0f;
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

    // Selector de Herramienta Godot: [Q] Select | [W] Move
    GuiToggleGroup(Rectangle(x, y, 88.0f, h), "[Q] Select;[W] Move", &active_tool);
    x += 184.0f;

    // Modo exacto de 32 píxeles (Snap 32px)
    GuiToggle(Rectangle(x, y, 88.0f, h), GuiIconText(GuiIconName.ICON_GRID, "32px"), &snap_32px);
    x += 94.0f;

    // Botón Cargar XML
    if (GuiButton(Rectangle(x, y, 108.0f, h), GuiIconText(GuiIconName.ICON_FOLDER_OPEN, "Cargar XML"))) {
        char[512] xmlPath;
        if (open_xml_file_dialog(xmlPath.ptr, xmlPath.length, "Cargar Escena XML")) {
            gui_clear_active_field();
            if (xml_load_scene(xmlPath.ptr, world)) {
                project_add_map(xmlPath.ptr);
            }
        }
    }
    x += 114.0f;

    // Botón Guardar XML (Ctrl+S)
    if (GuiButton(Rectangle(x, y, 114.0f, h), GuiIconText(GuiIconName.ICON_FILE_SAVE, "Guardar XML"))) {
        char[512] xmlPath;
        if (save_xml_file_dialog(xmlPath.ptr, xmlPath.length, "Guardar Escena XML")) {
            gui_clear_active_field();
            if (xml_save_scene(xmlPath.ptr, world)) {
                project_add_map(xmlPath.ptr);
                save_feedback_timer = 2.0f;
            }
        }
    }
    x += 120.0f;

    // Atributo <Map resources="..."> relativo al archivo XML
    GuiLabel(Rectangle(x, y, 74.0f, h), "res rel:");
    x += 74.0f;

    if (gui_search_box(Rectangle(x, y, 90.0f, h), world.resourcesRelPath.ptr, cast(int) world.resourcesRelPath.length, FIELD_MAP_RESOURCES_REL)) {
        level_world_reload_all_textures(world);
        tileset_reload_texture(false);
    }
    x += 96.0f;

    // Botón para resetear la cámara a (0, 0)
    if (GuiButton(Rectangle(x, y, 96.0f, h), GuiIconText(GuiIconName.ICON_ZOOM_CENTER, "(0,0)"))) {
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

    // Dibujar todos los objetos en coordenadas del mundo 2D
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used) {
            continue;
        }

        LevelObject* obj = &world.objects[i];
        bool isSelected = (obj.uid == world.selectedUid);

        if (obj.type == ObjectType.Box) {
            draw_world_box(*obj, isSelected);
        } else if (obj.type == ObjectType.Sprite) {
            draw_world_sprite(*obj, isSelected);
        }

        if (isSelected && active_tool == cast(int) LevelTool.Move) {
            draw_move_gizmo(*obj);
        }
    }

    EndMode2D();

    // HUD informativo de herramienta, modo 32px, zoom y coordenadas
    Vector2 mouseWorld = editor_camera_screen_to_world(ed_camera, GetMousePosition());
    const(char)* toolName = (active_tool == cast(int) LevelTool.Move) ? "W: Mover" : "Q: Seleccionar";
    const(char)* snapName = snap_32px ? "ON (32px)" : "OFF";

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
            if (cellTile >= 0 && tileset_is_tile_active(cellTile)) {
                Rectangle cellRect = Rectangle(c * CELL_SIZE, r * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                if (cellTile >= 64 && cellTile <= 74) {
                    tileset_draw_tile(48, cellRect);
                } else if (cellTile == 39) {
                    tileset_draw_tile(c >= 26 ? 50 : 0, cellRect);
                } else if ((cellTile >= 40 && cellTile <= 42) ||
                           cellTile == 96 || cellTile == 97 ||
                           cellTile == 112 || cellTile == 113 ||
                           cellTile == 128 || cellTile == 129) {
                    tileset_draw_tile(0, cellRect);
                }
                tileset_draw_tile(cellTile, cellRect);
            }
        }
    }
}

void draw_move_gizmo(ref const(LevelObject) obj) {
    Vector2 pos;
    if (obj.type == ObjectType.Box) {
        pos = obj.box.position;
    } else if (obj.type == ObjectType.Sprite) {
        pos = obj.sprite.position;
    } else {
        return;
    }

    float len = 36.0f / ed_camera.camera.zoom;
    float thick = 2.5f / ed_camera.camera.zoom;
    DrawLineEx(pos, Vector2(pos.x + len, pos.y), thick, Color(255, 80, 80, 255));
    DrawLineEx(pos, Vector2(pos.x, pos.y + len), thick, Color(80, 255, 100, 255));
    DrawCircleV(pos, 4.5f / ed_camera.camera.zoom, Colors.YELLOW);
}

void handle_viewport_object_interaction(Rectangle viewArea, ref LevelWorld world) {
    Vector2 mouseScreen = GetMousePosition();
    bool insideView = CheckCollisionPointRec(mouseScreen, viewArea);
    Vector2 mouseWorld = editor_camera_screen_to_world(ed_camera, mouseScreen);

    if (IsMouseButtonReleased(MouseButton.MOUSE_BUTTON_LEFT)) {
        dragging_uid = -1;
    }

    // Mover objeto si estamos en herramienta Move (W)
    if (active_tool == cast(int) LevelTool.Move &&
        dragging_uid >= 0 &&
        IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT)) {
        LevelObject* dragged = level_world_find(world, dragging_uid);
        if (dragged !is null) {
            float targetX = mouseWorld.x - drag_offset.x;
            float targetY = mouseWorld.y - drag_offset.y;

            if (snap_32px) {
                targetX = snap_to_32(targetX);
                targetY = snap_to_32(targetY);
            }

            if (dragged.type == ObjectType.Box) {
                dragged.box.position = Vector2(targetX, targetY);
            } else if (dragged.type == ObjectType.Sprite) {
                dragged.sprite.position = Vector2(targetX, targetY);
            }
        }
        return;
    }

    if (!insideView || IsKeyDown(KeyboardKey.KEY_SPACE)) {
        return;
    }

    if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_LEFT)) {
        int hitUid = -1;
        for (int i = MAX_LEVEL_OBJECTS - 1; i >= 0; i--) {
            if (!world.objects[i].used) {
                continue;
            }
            LevelObject* obj = &world.objects[i];
            Rectangle hitBox = get_object_world_bounds(*obj);
            if (hitBox.width > 0 && hitBox.height > 0 && CheckCollisionPointRec(mouseWorld, hitBox)) {
                hitUid = obj.uid;
                break;
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
                    }
                }
            }
        } else if (active_tool == cast(int) LevelTool.Move && world.selectedUid >= 0) {
            // En modo W (Mover), también permite arrastrar el objeto ya seleccionado
            LevelObject* sel = level_world_find(world, world.selectedUid);
            if (sel !is null && (sel.type == ObjectType.Box || sel.type == ObjectType.Sprite)) {
                level_world_push_undo(world);
                dragging_uid = sel.uid;
                if (sel.type == ObjectType.Box) {
                    drag_offset = Vector2(mouseWorld.x - sel.box.position.x, mouseWorld.y - sel.box.position.y);
                } else {
                    drag_offset = Vector2(mouseWorld.x - sel.sprite.position.x, mouseWorld.y - sel.sprite.position.y);
                }
            }
        }
    }
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
