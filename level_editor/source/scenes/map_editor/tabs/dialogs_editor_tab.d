module scenes.map_editor.tabs.dialogs_editor_tab;

nothrow:
@nogc:

import raylib;
import raygui;
import core.stdc.stdio : snprintf;
import core.stdc.string : strcmp;
import engine.tinyfd : open_json_file_dialog, save_json_file_dialog;
import scenes.map_editor.gui_widgets;
import scenes.map_editor.camera_controller;
import scenes.map_editor.dialogs_data;

public:

/**
 * Inicializa el Dialogs Editor con el grafo de nodos por defecto.
 */
void dialogs_editor_tab_init() {
    dialog_graph_init_default(graph);
    dialog_graph_clear_undo();
    graph_cam.initialized = false;
    dragging_node_uid = -1;
    connecting_from_uid = -1;
    connecting_from_slot = -1;
    add_node_popup_open = false;
    save_json_feedback_timer = 0.0f;
}

/**
 * Retorna referencia al grafo de diálogos activo (para el gestor de proyectos .procshit).
 */
ref DialogGraph dialogs_editor_tab_get_graph() {
    return graph;
}

/**
 * Actualiza atajos (Ctrl+S, Ctrl+Z, Ctrl+Shift+A) y temporizadores del Dialogs Editor.
 */
void dialogs_editor_tab_update(float dt) {
    if (save_json_feedback_timer > 0.0f) {
        save_json_feedback_timer -= dt;
    }

    bool ctrlDown = IsKeyDown(KeyboardKey.KEY_LEFT_CONTROL) || IsKeyDown(KeyboardKey.KEY_RIGHT_CONTROL);
    bool shiftDown = IsKeyDown(KeyboardKey.KEY_LEFT_SHIFT) || IsKeyDown(KeyboardKey.KEY_RIGHT_SHIFT);

    if (ctrlDown && IsKeyPressed(KeyboardKey.KEY_S)) {
        trigger_save_json();
    }

    if (ctrlDown && !shiftDown && IsKeyPressed(KeyboardKey.KEY_Z)) {
        gui_clear_active_field();
        if (dialog_graph_undo(graph)) {
            snprintf(feedback_msg.ptr, feedback_msg.length, "Undo aplicado en Dialogs Editor (Ctrl+Z)");
            save_json_feedback_timer = 1.6f;
        }
    }

    // Ctrl+Shift+A: abrir menú emergente para crear un nuevo nodo en la posición del cursor
    if (ctrlDown && shiftDown && IsKeyPressed(KeyboardKey.KEY_A)) {
        gui_clear_active_field();
        Vector2 mousePos = GetMousePosition();
        open_add_node_popup(mousePos);
    }
}

/**
 * Dibuja la pestaña Dialogs Editor:
 * - Barra superior despejada con Cargar/Guardar JSON, Undo y botón para abrir menú de nodos (Ctrl+Shift+A).
 * - Lienzo central de nodos estilo Visual Shaders con curvas Bezier e interacción de pines.
 * - Panel inspector a la derecha para editar el nodo seleccionado.
 */
void dialogs_editor_tab_draw(Rectangle workspace) {
    float rightPanelWidth = 390.0f;
    float pad = 10.0f;
    float topToolbarH = 42.0f;

    Rectangle leftArea = Rectangle(
        workspace.x + pad,
        workspace.y + pad,
        workspace.width - rightPanelWidth - pad * 3.0f,
        workspace.height - pad * 2.0f
    );

    Rectangle toolbarBounds = Rectangle(
        leftArea.x,
        leftArea.y,
        leftArea.width,
        topToolbarH
    );

    Rectangle canvasBounds = Rectangle(
        leftArea.x,
        leftArea.y + topToolbarH + 6.0f,
        leftArea.width,
        leftArea.height - topToolbarH - 6.0f
    );

    Rectangle inspectorBounds = Rectangle(
        workspace.x + workspace.width - rightPanelWidth - pad,
        workspace.y + pad,
        rightPanelWidth,
        workspace.height - pad * 2.0f
    );

    last_canvas_bounds = canvasBounds;

    draw_dialogs_toolbar(toolbarBounds, canvasBounds);
    draw_dialogs_node_canvas(canvasBounds);
    draw_dialogs_inspector(inspectorBounds);

    if (add_node_popup_open) {
        draw_add_node_popup(canvasBounds);
    }
}

private:

enum int FIELD_DLG_ID       = 7001;
enum int FIELD_DLG_TITLE    = 7002;
enum int FIELD_DLG_DESC     = 7003;
enum int FIELD_DLG_OPT1     = 7004;
enum int FIELD_DLG_OPT2     = 7005;
enum int FIELD_DLG_ARG      = 7006;

enum float DIALOG_NODE_W    = 250.0f;
enum float DIALOG_NODE_H    = 175.0f;
enum float DIRECTIVE_NODE_W = 220.0f;
enum float DIRECTIVE_NODE_H = 92.0f;
enum float PIN_RADIUS       = 7.0f;

__gshared DialogGraph graph;
__gshared EditorCamera graph_cam;
__gshared Rectangle last_canvas_bounds = Rectangle(0, 0, 800, 600);

__gshared int dragging_node_uid = -1;
__gshared Vector2 drag_node_offset = Vector2(0, 0);

__gshared int connecting_from_uid = -1;
__gshared int connecting_from_slot = -1;

__gshared bool add_node_popup_open = false;
__gshared Vector2 add_node_popup_screen_pos = Vector2(200, 200);
__gshared Vector2 add_node_popup_world_pos = Vector2(100, 100);

__gshared float save_json_feedback_timer = 0.0f;
__gshared char[128] feedback_msg = "Dialogos JSON guardados (Ctrl+S)";

void open_add_node_popup(Vector2 screenPos) {
    Rectangle viewArea = get_canvas_inner_area(last_canvas_bounds);
    if (CheckCollisionPointRec(screenPos, viewArea)) {
        add_node_popup_screen_pos = screenPos;
        add_node_popup_world_pos = editor_camera_screen_to_world(graph_cam, screenPos);
    } else {
        add_node_popup_screen_pos = Vector2(
            viewArea.x + viewArea.width * 0.35f,
            viewArea.y + viewArea.height * 0.25f
        );
        add_node_popup_world_pos = get_spawn_pos_in_canvas(last_canvas_bounds);
    }
    add_node_popup_open = true;
}

void draw_add_node_popup(Rectangle canvasBounds) {
    float popupW = 210.0f;
    float popupH = 282.0f;

    float px = add_node_popup_screen_pos.x;
    float py = add_node_popup_screen_pos.y;

    if (px + popupW > canvasBounds.x + canvasBounds.width - 8.0f) {
        px = canvasBounds.x + canvasBounds.width - popupW - 8.0f;
    }
    if (py + popupH > canvasBounds.y + canvasBounds.height - 8.0f) {
        py = canvasBounds.y + canvasBounds.height - popupH - 8.0f;
    }
    if (px < canvasBounds.x + 8.0f) px = canvasBounds.x + 8.0f;
    if (py < canvasBounds.y + 8.0f) py = canvasBounds.y + 8.0f;

    Rectangle popupRect = Rectangle(px, py, popupW, popupH);
    DrawRectangleRec(Rectangle(px + 4, py + 4, popupW, popupH), Color(0, 0, 0, 150));
    GuiPanel(popupRect, "Nuevo Nodo (Ctrl+Shift+A)");

    float bx = px + 10.0f;
    float by = py + 30.0f;
    float bw = popupW - 20.0f;
    float bh = 26.0f;
    float gap = 30.0f;

    DialogNodeType[8] types = [
        DialogNodeType.Dialog,
        DialogNodeType.Load,
        DialogNodeType.SaveNext,
        DialogNodeType.PlaySfx,
        DialogNodeType.Define,
        DialogNodeType.IfDef,
        DialogNodeType.IfNDef,
        DialogNodeType.GiveItem
    ];

    const(char)*[8] labels = [
        "+ Dialog ( Principal )",
        "+ __load__",
        "+ __save_next__",
        "+ __playsfx__",
        "+ __define__",
        "+ __ifdef__",
        "+ __ifndef__",
        "+ __give_item__"
    ];

    for (int i = 0; i < 8; i++) {
        if (GuiButton(Rectangle(bx, by + i * gap, bw, bh), labels[i])) {
            dialog_graph_push_undo(graph);
            dialog_graph_add_node(graph, types[i], add_node_popup_world_pos);
            add_node_popup_open = false;
            return;
        }
    }

    // Cerrar si se hace click fuera del popup
    if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_LEFT) ||
        IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_RIGHT)) {
        if (!CheckCollisionPointRec(GetMousePosition(), popupRect)) {
            add_node_popup_open = false;
        }
    }
}

void trigger_save_json() {
    gui_clear_active_field();
    if (graph.jsonFilePath[0] != '\0') {
        if (dialog_graph_save_json(graph.jsonFilePath.ptr, graph)) {
            snprintf(feedback_msg.ptr, feedback_msg.length, "Dialogos JSON guardados (Ctrl+S)");
            save_json_feedback_timer = 2.0f;
        }
    } else {
        char[512] pathBuf;
        if (save_json_file_dialog(pathBuf.ptr, pathBuf.length, "Guardar Dialogos JSON")) {
            if (dialog_graph_save_json(pathBuf.ptr, graph)) {
                snprintf(feedback_msg.ptr, feedback_msg.length, "Dialogos JSON guardados (Ctrl+S)");
                save_json_feedback_timer = 2.0f;
            }
        }
    }
}

Vector2 get_spawn_pos_in_canvas(Rectangle canvasBounds) {
    Rectangle viewArea = get_canvas_inner_area(canvasBounds);
    Vector2 centerScreen = Vector2(
        viewArea.x + viewArea.width * 0.45f,
        viewArea.y + viewArea.height * 0.45f
    );
    Vector2 worldPos = editor_camera_screen_to_world(graph_cam, centerScreen);
    float offset = cast(float)((graph.nextUid % 6) * 24);
    return Vector2(worldPos.x + offset, worldPos.y + offset);
}

void draw_dialogs_toolbar(Rectangle barBounds, Rectangle canvasBounds) {
    GuiPanel(barBounds, null);

    float x = barBounds.x + 8.0f;
    float y = barBounds.y + 8.0f;
    float h = 26.0f;

    // Botón principal para abrir el menú de creación de nodos (Ctrl+Shift+A)
    if (GuiButton(Rectangle(x, y, 185.0f, h), GuiIconText(GuiIconName.ICON_BOX_MULTISIZE, "+ Nodo (Ctrl+Shift+A)"))) {
        open_add_node_popup(Vector2(barBounds.x + 20.0f, barBounds.y + barBounds.height + 12.0f));
    }
    x += 195.0f;

    // Botones Cargar / Guardar JSON separados y ordenados
    if (GuiButton(Rectangle(x, y, 118.0f, h), GuiIconText(GuiIconName.ICON_FOLDER_OPEN, "Cargar JSON"))) {
        char[512] jsonPath;
        if (open_json_file_dialog(jsonPath.ptr, jsonPath.length, "Cargar Dialogos JSON")) {
            gui_clear_active_field();
            dialog_graph_load_json(jsonPath.ptr, graph);
        }
    }
    x += 124.0f;

    if (GuiButton(Rectangle(x, y, 122.0f, h), GuiIconText(GuiIconName.ICON_FILE_SAVE, "Guardar JSON"))) {
        char[512] jsonPath;
        if (save_json_file_dialog(jsonPath.ptr, jsonPath.length, "Guardar Dialogos JSON")) {
            gui_clear_active_field();
            if (dialog_graph_save_json(jsonPath.ptr, graph)) {
                snprintf(feedback_msg.ptr, feedback_msg.length, "Dialogos JSON guardados");
                save_json_feedback_timer = 2.0f;
            }
        }
    }
    x += 128.0f;

    if (GuiButton(Rectangle(x, y, 108.0f, h), GuiIconText(GuiIconName.ICON_UNDO, "Undo (Ctrl+Z)"))) {
        gui_clear_active_field();
        if (dialog_graph_undo(graph)) {
            snprintf(feedback_msg.ptr, feedback_msg.length, "Undo aplicado (Ctrl+Z)");
            save_json_feedback_timer = 1.6f;
        }
    }
    x += 114.0f;

    if (GuiButton(Rectangle(x, y, 90.0f, h), GuiIconText(GuiIconName.ICON_ZOOM_CENTER, "Centrar"))) {
        editor_camera_reset(graph_cam, get_canvas_inner_area(canvasBounds));
    }
}

Rectangle get_canvas_inner_area(Rectangle bounds) {
    return Rectangle(
        bounds.x + 2.0f,
        bounds.y + 24.0f,
        bounds.width - 4.0f,
        bounds.height - 26.0f
    );
}

Rectangle get_node_rect(ref const(DialogGraphNode) node) {
    float w = (node.type == DialogNodeType.Dialog) ? DIALOG_NODE_W : DIRECTIVE_NODE_W;
    float h = (node.type == DialogNodeType.Dialog) ? DIALOG_NODE_H : DIRECTIVE_NODE_H;
    return Rectangle(node.position.x, node.position.y, w, h);
}

Vector2 get_input_pin_pos(ref const(DialogGraphNode) node) {
    return Vector2(node.position.x, node.position.y + 44.0f);
}

int get_output_pin_count(ref const(DialogGraphNode) node) {
    return (node.type == DialogNodeType.Dialog) ? 3 : 1;
}

Vector2 get_output_pin_pos(ref const(DialogGraphNode) node, int slot) {
    if (node.type == DialogNodeType.Dialog) {
        float baseY = node.position.y + 98.0f;
        return Vector2(node.position.x + DIALOG_NODE_W, baseY + slot * 26.0f);
    } else {
        return Vector2(node.position.x + DIRECTIVE_NODE_W, node.position.y + 52.0f);
    }
}

Color get_node_header_color(ref const(DialogGraphNode) node) {
    final switch (node.type) {
        case DialogNodeType.Dialog:
            if (strcmp(node.id.ptr, "__start__") == 0) {
                return Color(36, 130, 82, 255);
            }
            return Color(45, 95, 165, 255);
        case DialogNodeType.Load:
            return Color(180, 95, 35, 255);
        case DialogNodeType.SaveNext:
            return Color(125, 65, 165, 255);
        case DialogNodeType.PlaySfx:
            return Color(35, 135, 150, 255);
        case DialogNodeType.Define:
            return Color(165, 130, 35, 255);
        case DialogNodeType.IfDef:
            return Color(165, 55, 105, 255);
        case DialogNodeType.IfNDef:
            return Color(165, 55, 65, 255);
        case DialogNodeType.GiveItem:
            return Color(45, 145, 110, 255);
    }
}

void draw_dialogs_node_canvas(Rectangle bounds) {
    GuiPanel(
        bounds,
        "Dialogs Editor - Grafo (Ctrl+Shift+A: Nuevo Nodo | Pin Der->Izq: Conectar | Click Der Pin: Desconectar | Ctrl+Z: Undo)"
    );

    Rectangle viewArea = get_canvas_inner_area(bounds);
    if (!graph_cam.initialized) {
        editor_camera_reset(graph_cam, viewArea);
        graph_cam.camera.offset = Vector2(viewArea.x + 40.0f, viewArea.y + 40.0f);
    }

    editor_camera_update(graph_cam, viewArea);
    if (!add_node_popup_open) {
        handle_graph_interaction(viewArea);
    }

    BeginScissorMode(
        cast(int) viewArea.x,
        cast(int) viewArea.y,
        cast(int) viewArea.width,
        cast(int) viewArea.height
    );

    DrawRectangleRec(viewArea, Color(24, 27, 33, 255));

    BeginMode2D(graph_cam.camera);

    editor_camera_draw_world_grid(graph_cam, viewArea);

    // 1. Dibujar todos los cables (links) existentes con curvas Bezier
    for (int i = 0; i < MAX_DIALOG_LINKS; i++) {
        if (!graph.links[i].used) {
            continue;
        }

        DialogGraphNode* src = dialog_graph_find_node(graph, graph.links[i].fromNodeUid);
        DialogGraphNode* dst = dialog_graph_find_node(graph, graph.links[i].toNodeUid);
        if (src is null || dst is null) {
            continue;
        }

        Vector2 p1 = get_output_pin_pos(*src, graph.links[i].fromSlot);
        Vector2 p2 = get_input_pin_pos(*dst);

        Color wireColor = get_slot_wire_color(*src, graph.links[i].fromSlot);
        DrawSplineSegmentBezierCubic(
            p1,
            Vector2(p1.x + 80.0f, p1.y),
            Vector2(p2.x - 80.0f, p2.y),
            p2,
            3.0f,
            wireColor
        );
    }

    // 2. Si el usuario está arrastrando un cable nuevo, dibujarlo hacia el mouse
    if (connecting_from_uid >= 0) {
        DialogGraphNode* src = dialog_graph_find_node(graph, connecting_from_uid);
        if (src !is null) {
            Vector2 p1 = get_output_pin_pos(*src, connecting_from_slot);
            Vector2 p2 = editor_camera_screen_to_world(graph_cam, GetMousePosition());
            DrawSplineSegmentBezierCubic(
                p1,
                Vector2(p1.x + 70.0f, p1.y),
                Vector2(p2.x - 70.0f, p2.y),
                p2,
                3.0f,
                Colors.YELLOW
            );
        }
    }

    // 3. Dibujar todas las tarjetas de nodos
    for (int i = 0; i < MAX_DIALOG_NODES; i++) {
        if (!graph.nodes[i].used) {
            continue;
        }
        draw_single_graph_node(graph.nodes[i]);
    }

    EndMode2D();

    if (save_json_feedback_timer > 0.0f) {
        DrawRectangle(cast(int) viewArea.x + 10, cast(int) viewArea.y + 10, 310, 26, Color(35, 130, 60, 230));
        DrawText(feedback_msg.ptr, cast(int) viewArea.x + 18, cast(int) viewArea.y + 16, 14, Colors.RAYWHITE);
    }

    EndScissorMode();
}

Color get_slot_wire_color(ref const(DialogGraphNode) src, int slot) {
    if (src.type == DialogNodeType.Dialog) {
        if (slot == DialogBranchSlot.Default) return Color(100, 200, 255, 255);
        if (slot == DialogBranchSlot.Option1) return Color(110, 235, 140, 255);
        if (slot == DialogBranchSlot.Option2) return Color(255, 185, 90, 255);
    }
    return Color(220, 150, 255, 255);
}

void draw_single_graph_node(ref DialogGraphNode node) {
    Rectangle box = get_node_rect(node);
    bool isSelected = (graph.selectedNodeUid == node.uid);

    // Sombra y cuerpo de la tarjeta
    DrawRectangleRec(Rectangle(box.x + 4, box.y + 4, box.width, box.height), Color(10, 12, 16, 140));
    DrawRectangleRec(box, Color(38, 42, 52, 245));

    // Cabecera coloreada según el tipo de nodo
    Rectangle headerRect = Rectangle(box.x, box.y, box.width, 28.0f);
    DrawRectangleRec(headerRect, get_node_header_color(node));

    char[96] headerTitle;
    if (node.type == DialogNodeType.Dialog) {
        snprintf(headerTitle.ptr, headerTitle.length, "Dialog: %s", node.id.ptr);
    } else {
        snprintf(headerTitle.ptr, headerTitle.length, "%s", dialog_node_type_title(node.type));
    }
    DrawText(headerTitle.ptr, cast(int)(box.x + 10), cast(int)(box.y + 7), 14, Colors.RAYWHITE);

    // Botón 'x' en la esquina superior derecha del nodo
    Rectangle closeBtn = Rectangle(box.x + box.width - 22.0f, box.y + 4.0f, 18.0f, 18.0f);
    DrawRectangleRec(closeBtn, Color(180, 50, 50, 220));
    DrawText("x", cast(int)(closeBtn.x + 5), cast(int)(closeBtn.y + 2), 14, Colors.RAYWHITE);

    // Borde de selección
    Color borderCol = isSelected ? Colors.YELLOW : Color(85, 95, 115, 255);
    DrawRectangleLinesEx(box, isSelected ? 2.5f : 1.5f, borderCol);

    // Contenido según el tipo de nodo
    if (node.type == DialogNodeType.Dialog) {
        const(char)* titleStr = (node.title[0] != '\0') ? node.title.ptr : "(vacio)";
        char[128] titleLine;
        snprintf(titleLine.ptr, titleLine.length, "Title: %s", titleStr);
        DrawText(titleLine.ptr, cast(int)(box.x + 16), cast(int)(box.y + 36), 13, Color(200, 210, 225, 255));

        char[64] descPreview;
        snprintf(descPreview.ptr, descPreview.length, "%.30s", node.desc.ptr);
        DrawText(descPreview.ptr, cast(int)(box.x + 16), cast(int)(box.y + 56), 13, Color(165, 175, 190, 255));

        DrawLine(cast(int)(box.x + 8), cast(int)(box.y + 80), cast(int)(box.x + box.width - 8), cast(int)(box.y + 80), Color(65, 72, 88, 255));

        // Etiquetas de los 3 pines de salida
        DrawText("default", cast(int)(box.x + box.width - 64), cast(int)(box.y + 92), 13, Color(100, 200, 255, 255));

        char[64] opt1Label;
        if (node.hasOption1 && node.option1Text[0] != '\0') {
            snprintf(opt1Label.ptr, opt1Label.length, "opt1: %.14s", node.option1Text.ptr);
        } else {
            snprintf(opt1Label.ptr, opt1Label.length, "option1");
        }
        DrawText(opt1Label.ptr, cast(int)(box.x + 16), cast(int)(box.y + 118), 13, Color(110, 235, 140, 255));

        char[64] opt2Label;
        if (node.hasOption2 && node.option2Text[0] != '\0') {
            snprintf(opt2Label.ptr, opt2Label.length, "opt2: %.14s", node.option2Text.ptr);
        } else {
            snprintf(opt2Label.ptr, opt2Label.length, "option2");
        }
        DrawText(opt2Label.ptr, cast(int)(box.x + 16), cast(int)(box.y + 144), 13, Color(255, 185, 90, 255));
    } else {
        char[64] argPreview;
        snprintf(argPreview.ptr, argPreview.length, "%.24s", node.argValue.ptr);
        DrawText(argPreview.ptr, cast(int)(box.x + 16), cast(int)(box.y + 44), 13, Colors.RAYWHITE);
        DrawText("out", cast(int)(box.x + box.width - 34), cast(int)(box.y + 68), 12, Color(220, 150, 255, 255));
    }

    // Dibujar Pin de Entrada (Izquierda)
    Vector2 inPin = get_input_pin_pos(node);
    DrawCircleV(inPin, PIN_RADIUS, Color(230, 235, 245, 255));
    DrawCircleLines(cast(int) inPin.x, cast(int) inPin.y, PIN_RADIUS, Color(30, 30, 35, 255));

    // Dibujar Pines de Salida (Derecha)
    int outCount = get_output_pin_count(node);
    for (int s = 0; s < outCount; s++) {
        Vector2 outPin = get_output_pin_pos(node, s);
        Color pinCol = get_slot_wire_color(node, s);
        DrawCircleV(outPin, PIN_RADIUS, pinCol);
        DrawCircleLines(cast(int) outPin.x, cast(int) outPin.y, PIN_RADIUS, Color(30, 30, 35, 255));
    }
}

void handle_graph_interaction(Rectangle viewArea) {
    Vector2 mouseScreen = GetMousePosition();
    bool insideView = CheckCollisionPointRec(mouseScreen, viewArea);
    Vector2 mouseWorld = editor_camera_screen_to_world(graph_cam, mouseScreen);

    // Finalizar arrastre de cable al soltar botón izquierdo
    if (connecting_from_uid >= 0 && IsMouseButtonReleased(MouseButton.MOUSE_BUTTON_LEFT)) {
        for (int i = MAX_DIALOG_NODES - 1; i >= 0; i--) {
            if (!graph.nodes[i].used) continue;
            Vector2 inPin = get_input_pin_pos(graph.nodes[i]);
            if (CheckCollisionPointCircle(mouseWorld, inPin, PIN_RADIUS * 1.9f)) {
                dialog_graph_push_undo(graph);
                dialog_graph_connect(graph, connecting_from_uid, connecting_from_slot, graph.nodes[i].uid, 0);
                break;
            }
        }
        connecting_from_uid = -1;
        connecting_from_slot = -1;
        return;
    }

    // Actualizar posición de nodo arrastrado
    if (dragging_node_uid >= 0) {
        if (IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT)) {
            DialogGraphNode* dragged = dialog_graph_find_node(graph, dragging_node_uid);
            if (dragged !is null) {
                dragged.position = Vector2(
                    mouseWorld.x - drag_node_offset.x,
                    mouseWorld.y - drag_node_offset.y
                );
            }
        } else {
            dragging_node_uid = -1;
        }
        return;
    }

    if (!insideView || IsKeyDown(KeyboardKey.KEY_SPACE)) {
        return;
    }

    // Click derecho sobre un pin desconecta su cable
    if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_RIGHT)) {
        for (int i = MAX_DIALOG_NODES - 1; i >= 0; i--) {
            if (!graph.nodes[i].used) continue;
            DialogGraphNode* node = &graph.nodes[i];

            Vector2 inPin = get_input_pin_pos(*node);
            if (CheckCollisionPointCircle(mouseWorld, inPin, PIN_RADIUS * 1.8f)) {
                dialog_graph_push_undo(graph);
                dialog_graph_disconnect_input(graph, node.uid, 0);
                return;
            }

            int outCount = get_output_pin_count(*node);
            for (int s = 0; s < outCount; s++) {
                Vector2 outPin = get_output_pin_pos(*node, s);
                if (CheckCollisionPointCircle(mouseWorld, outPin, PIN_RADIUS * 1.8f)) {
                    dialog_graph_push_undo(graph);
                    dialog_graph_disconnect_output(graph, node.uid, s);
                    return;
                }
            }
        }
    }

    if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_LEFT)) {
        // 1. Verificar si se hizo click en algún Pin de Salida para iniciar conexión de cable
        for (int i = MAX_DIALOG_NODES - 1; i >= 0; i--) {
            if (!graph.nodes[i].used) continue;
            DialogGraphNode* node = &graph.nodes[i];
            int outCount = get_output_pin_count(*node);
            for (int s = 0; s < outCount; s++) {
                Vector2 outPin = get_output_pin_pos(*node, s);
                if (CheckCollisionPointCircle(mouseWorld, outPin, PIN_RADIUS * 1.8f)) {
                    connecting_from_uid = node.uid;
                    connecting_from_slot = s;
                    graph.selectedNodeUid = node.uid;
                    return;
                }
            }
        }

        // 2. Verificar si se hizo click sobre una tarjeta de nodo (o su botón 'x' de eliminar)
        int deleteUid = -1;
        for (int i = MAX_DIALOG_NODES - 1; i >= 0; i--) {
            if (!graph.nodes[i].used) continue;
            DialogGraphNode* node = &graph.nodes[i];
            Rectangle box = get_node_rect(*node);

            Rectangle closeBtn = Rectangle(box.x + box.width - 22.0f, box.y + 4.0f, 18.0f, 18.0f);
            if (CheckCollisionPointRec(mouseWorld, closeBtn)) {
                deleteUid = node.uid;
                break;
            }

            if (CheckCollisionPointRec(mouseWorld, box)) {
                gui_clear_active_field();
                dialog_graph_push_undo(graph);
                graph.selectedNodeUid = node.uid;
                dragging_node_uid = node.uid;
                drag_node_offset = Vector2(mouseWorld.x - node.position.x, mouseWorld.y - node.position.y);
                break;
            }
        }

        if (deleteUid >= 0) {
            gui_clear_active_field();
            dialog_graph_push_undo(graph);
            dialog_graph_remove_node(graph, deleteUid);
        }
    }
}

void draw_dialogs_inspector(Rectangle bounds) {
    GuiPanel(bounds, "Inspector de Nodo de Dialogo");

    DialogGraphNode* node = dialog_graph_find_node(graph, graph.selectedNodeUid);
    if (node is null) {
        GuiLabel(
            Rectangle(bounds.x + 16.0f, bounds.y + 48.0f, bounds.width - 32.0f, 24.0f),
            "Selecciona un nodo en el grafo para editarlo"
        );
        return;
    }

    float x = bounds.x + 14.0f;
    float y = bounds.y + 38.0f;
    float w = bounds.width - 28.0f;

    char[96] typeInfo;
    snprintf(typeInfo.ptr, typeInfo.length, "Tipo de Nodo: %s", dialog_node_type_title(node.type));
    gui_section_header(x, y, w, typeInfo.ptr);

    if (node.type == DialogNodeType.Dialog) {
        gui_labeled_text_box(x, y, w, "ID Dialogo", node.id.ptr, cast(int) node.id.length, FIELD_DLG_ID);
        gui_labeled_text_box(x, y, w, "Title", node.title.ptr, cast(int) node.title.length, FIELD_DLG_TITLE);
        gui_labeled_text_box(x, y, w, "Desc", node.desc.ptr, cast(int) node.desc.length, FIELD_DLG_DESC);

        y += 8.0f;
        gui_section_header(x, y, w, "Opciones de Respuesta");

        gui_labeled_checkbox(x, y, "Habilitar Option 1", &node.hasOption1);
        if (node.hasOption1) {
            gui_labeled_text_box(x, y, w, "Texto Opt 1", node.option1Text.ptr, cast(int) node.option1Text.length, FIELD_DLG_OPT1);
        }

        gui_labeled_checkbox(x, y, "Habilitar Option 2", &node.hasOption2);
        if (node.hasOption2) {
            gui_labeled_text_box(x, y, w, "Texto Opt 2", node.option2Text.ptr, cast(int) node.option2Text.length, FIELD_DLG_OPT2);
        }
    } else {
        const(char)* argLabel = get_directive_arg_label(node.type);
        GuiLabel(Rectangle(x, y, w, 22.0f), argLabel);
        y += 24.0f;
        gui_search_box(Rectangle(x, y, w, 26.0f), node.argValue.ptr, cast(int) node.argValue.length, FIELD_DLG_ARG);
        y += 36.0f;
    }

    y += 16.0f;
    if (gui_trash_button(Rectangle(x, y, w, 28.0f))) {
        gui_clear_active_field();
        dialog_graph_push_undo(graph);
        dialog_graph_remove_node(graph, node.uid);
    }
}

const(char)* get_directive_arg_label(DialogNodeType type) {
    final switch (type) {
        case DialogNodeType.Dialog:   return "Valor";
        case DialogNodeType.Load:     return "Ruta de escena (__load__):";
        case DialogNodeType.SaveNext: return "ID destino (__save_next__):";
        case DialogNodeType.PlaySfx:  return "Ruta de sonido (__playsfx__):";
        case DialogNodeType.Define:   return "Macro / Flag a definir (__define__):";
        case DialogNodeType.IfDef:    return "Condicion si definido (__ifdef__):";
        case DialogNodeType.IfNDef:   return "Condicion si NO definido (__ifndef__):";
        case DialogNodeType.GiveItem: return "Item ID[:count] (__give_item__):";
    }
}
