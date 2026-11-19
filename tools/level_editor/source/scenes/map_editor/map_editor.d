module scenes.map_editor.map_editor;

nothrow:
@nogc:

import raylib;
import raygui;
import core.stdc.stdio : snprintf;
import engine.scene;
import engine.tinyfd : open_procshit_file_dialog, save_procshit_file_dialog, open_xml_file_dialog;
import scenes.map_editor.gui_widgets;
import scenes.map_editor.project_manager;
import scenes.map_editor.tabs.tilemap_tab;
import scenes.map_editor.tabs.tileset_tab;
import scenes.map_editor.tabs.dialogs_editor_tab;
import scenes.map_editor.tabs.level_editor_tab;
import scenes.map_editor.tabs.prefabs_tab;

public:

enum EditorTab : int {
    Tilemap       = 0,
    Tileset       = 1,
    DialogsEditor = 2,
    LevelEditor   = 3,
    Prefabs       = 4
}

Scene map_editor = Scene(
    setup: () { on_setup(); },
    close: () { on_close(); },
    update: (float dt) { on_update(dt); },
    draw: () { on_draw(); }
);

private:

/*
	Some consts
*/
const BG_COLOR = Color(24, 26, 30, 255);
const TABS_BG_COLOR = Color(32, 35, 42, 255);

enum float TOP_TABBAR_HEIGHT = 36.0f;

__gshared int active_tab = cast(int) EditorTab.LevelEditor;
__gshared int tab_hscroll = 0;

__gshared bool show_project_maps_panel = false;
__gshared Vector2 project_maps_scroll = Vector2(0, 0);
__gshared float project_feedback_timer = 0.0f;
__gshared char[128] project_feedback_msg = 0;

void on_setup() {
    configure_gui_theme();

    project_manager_init();
    tileset_tab_init();
    tilemap_tab_init();
    dialogs_editor_tab_init();
    level_editor_tab_init();
    prefabs_tab_init();

    // Cargar automáticamente el proyecto por defecto al iniciar para que las escenas aparezcan de inmediato
    if (FileExists("beach_ruins.procshit")) {
        procshit_load_project("beach_ruins.procshit");
    } else if (FileExists("../projects/beach_ruins.procshit")) {
        procshit_load_project("../projects/beach_ruins.procshit");
    } else if (FileExists("../beach_ruins.procshit")) {
        procshit_load_project("../beach_ruins.procshit");
    }
}

void on_close() {
    tileset_tab_close();
    level_editor_tab_close();
    prefabs_tab_close();
}

void on_update(float dt) {
    if (project_feedback_timer > 0.0f) {
        project_feedback_timer -= dt;
    }

    final switch (cast(EditorTab) active_tab) {
        case EditorTab.Tilemap:
            tilemap_tab_update(dt);
            break;
        case EditorTab.Tileset:
            tileset_tab_update(dt);
            break;
        case EditorTab.DialogsEditor:
            dialogs_editor_tab_update(dt);
            break;
        case EditorTab.LevelEditor:
            level_editor_tab_update(dt);
            break;
        case EditorTab.Prefabs:
            prefabs_tab_update(dt);
            break;
    }
}

void on_draw() {
    ClearBackground(BG_COLOR);

    float screenW = cast(float) GetScreenWidth();
    float screenH = cast(float) GetScreenHeight();

    draw_top_tabs(screenW);

    Rectangle workspace = Rectangle(
        0.0f,
        TOP_TABBAR_HEIGHT,
        screenW,
        screenH - TOP_TABBAR_HEIGHT
    );

    final switch (cast(EditorTab) active_tab) {
        case EditorTab.Tilemap:
            tilemap_tab_draw(workspace);
            break;
        case EditorTab.Tileset:
            tileset_tab_draw(workspace);
            break;
        case EditorTab.DialogsEditor:
            dialogs_editor_tab_draw(workspace);
            break;
        case EditorTab.LevelEditor:
            level_editor_tab_draw(workspace);
            break;
        case EditorTab.Prefabs:
            prefabs_tab_draw(workspace);
            break;
    }

    if (show_project_maps_panel) {
        draw_project_maps_overlay(screenW, screenH);
    }

    if (project_feedback_timer > 0.0f) {
        DrawRectangle(cast(int)(screenW - 350.0f), cast(int)(TOP_TABBAR_HEIGHT + 8.0f), 336, 28, Color(35, 130, 60, 235));
        DrawText(project_feedback_msg.ptr, cast(int)(screenW - 340.0f), cast(int)(TOP_TABBAR_HEIGHT + 15.0f), 14, Colors.RAYWHITE);
    }
}

void draw_top_tabs(float screenW) {
    DrawRectangle(0, 0, cast(int) screenW, cast(int) TOP_TABBAR_HEIGHT, TABS_BG_COLOR);

    float rightControlsWidth = 445.0f;

    int prevTab = active_tab;
    Rectangle tabBarBounds = Rectangle(
        8.0f, 4.0f,
        screenW - rightControlsWidth - 16.0f, TOP_TABBAR_HEIGHT - 4.0f
    );
    GuiTabBar(tabBarBounds, "Tilemap;Tileset;Dialogs Editor;Level Editor;Prefabs", &tab_hscroll, &active_tab);

    if (active_tab != prevTab) {
        gui_clear_active_field();
    }

    // Controles de Proyecto (.procshit) en la esquina superior derecha
    float bx = screenW - rightControlsWidth;
    float by = 5.0f;
    float bh = TOP_TABBAR_HEIGHT - 10.0f;

    if (GuiButton(Rectangle(bx, by, 140.0f, bh), GuiIconText(GuiIconName.ICON_FOLDER_OPEN, "Cargar .procshit"))) {
        char[512] projPath;
        if (open_procshit_file_dialog(projPath.ptr, projPath.length, "Cargar Proyecto (.procshit)")) {
            gui_clear_active_field();
            if (procshit_load_project(projPath.ptr)) {
                snprintf(project_feedback_msg.ptr, project_feedback_msg.length, "Proyecto .procshit cargado");
                project_feedback_timer = 2.2f;
            }
        }
    }
    bx += 146.0f;

    if (GuiButton(Rectangle(bx, by, 145.0f, bh), GuiIconText(GuiIconName.ICON_FILE_SAVE, "Guardar .procshit"))) {
        gui_clear_active_field();
        ref ProcshitProject proj = get_active_project();
        if (proj.projectFilePath[0] != '\0') {
            if (procshit_save_project(proj.projectFilePath.ptr)) {
                snprintf(project_feedback_msg.ptr, project_feedback_msg.length, "Proyecto .procshit guardado");
                project_feedback_timer = 2.2f;
            }
        } else {
            char[512] projPath;
            if (save_procshit_file_dialog(projPath.ptr, projPath.length, "Guardar Proyecto (.procshit)")) {
                if (procshit_save_project(projPath.ptr)) {
                    snprintf(project_feedback_msg.ptr, project_feedback_msg.length, "Proyecto .procshit guardado");
                    project_feedback_timer = 2.2f;
                }
            }
        }
    }
    bx += 151.0f;

    GuiToggle(
        Rectangle(bx, by, 140.0f, bh),
        GuiIconText(GuiIconName.ICON_FILETYPE_TEXT, "Mapas Proyecto"),
        &show_project_maps_panel
    );
}

void draw_project_maps_overlay(float screenW, float screenH) {
    float panelW = 430.0f;
    float panelH = 340.0f;
    Rectangle panelBounds = Rectangle(
        screenW - panelW - 12.0f,
        TOP_TABBAR_HEIGHT + 6.0f,
        panelW,
        panelH
    );

    DrawRectangleRec(Rectangle(panelBounds.x + 4, panelBounds.y + 4, panelBounds.width, panelBounds.height), Color(0, 0, 0, 160));
    GuiPanel(panelBounds, "Proyecto (.procshit) - Mapas del Proyecto (Estilo Tiled)");

    ref ProcshitProject proj = get_active_project();

    float x = panelBounds.x + 12.0f;
    float y = panelBounds.y + 32.0f;
    float w = panelBounds.width - 24.0f;

    const(char)* pPath = (proj.projectFilePath[0] != '\0') ? proj.projectFilePath.ptr : "(Proyecto sin guardar)";
    char[128] pathLabel;
    snprintf(pathLabel.ptr, pathLabel.length, "Archivo: %.50s", pPath);
    GuiLabel(Rectangle(x, y, w, 20.0f), pathLabel.ptr);
    y += 24.0f;

    if (GuiButton(Rectangle(x, y, (w - 8.0f) * 0.52f, 26.0f), GuiIconText(GuiIconName.ICON_FILE_ADD, "+ Agregar Mapa XML"))) {
        char[512] xmlPath;
        if (open_xml_file_dialog(xmlPath.ptr, xmlPath.length, "Agregar Mapa XML al Proyecto")) {
            int idx = project_add_map(xmlPath.ptr);
            if (idx >= 0) {
                project_switch_to_map(idx);
            }
        }
    }

    if (GuiButton(Rectangle(x + (w - 8.0f) * 0.52f + 8.0f, y, (w - 8.0f) * 0.48f, 26.0f), GuiIconText(GuiIconName.ICON_FILE_SAVE_CLASSIC, "Guardar Como..."))) {
        char[512] projPath;
        if (save_procshit_file_dialog(projPath.ptr, projPath.length, "Guardar Proyecto (.procshit)")) {
            if (procshit_save_project(projPath.ptr)) {
                snprintf(project_feedback_msg.ptr, project_feedback_msg.length, "Proyecto .procshit guardado");
                project_feedback_timer = 2.2f;
            }
        }
    }
    y += 34.0f;

    // Lista scrolleable de mapas del proyecto
    int mapCount = 0;
    for (int i = 0; i < MAX_PROJECT_MAPS; i++) {
        if (proj.maps[i].used) mapCount++;
    }

    Rectangle listBounds = Rectangle(x, y, w, panelBounds.height - (y - panelBounds.y) - 12.0f);
    float rowH = 30.0f;
    Rectangle contentRect = Rectangle(0, 0, listBounds.width - 20.0f, (mapCount > 0 ? mapCount : 1) * rowH + 8.0f);
    Rectangle viewArea = Rectangle(0, 0, 0, 0);

    GuiScrollPanel(listBounds, null, contentRect, &project_maps_scroll, &viewArea);

    BeginScissorMode(
        cast(int) viewArea.x,
        cast(int) viewArea.y,
        cast(int) viewArea.width,
        cast(int) viewArea.height
    );

    if (mapCount == 0) {
        GuiLabel(
            Rectangle(viewArea.x + 10.0f, viewArea.y + 10.0f, viewArea.width - 20.0f, 24.0f),
            "No hay mapas en el proyecto. Usa '+ Agregar Mapa XML'."
        );
    } else {
        int removeIndex = -1;
        float rowY = viewArea.y + 4.0f + project_maps_scroll.y;

        for (int i = 0; i < MAX_PROJECT_MAPS; i++) {
            if (!proj.maps[i].used) continue;

            bool isActive = (proj.activeMapIndex == i);
            Rectangle rowBtn = Rectangle(viewArea.x + 6.0f, rowY, viewArea.width - 42.0f, 26.0f);
            Rectangle delBtn = Rectangle(viewArea.x + viewArea.width - 32.0f, rowY, 26.0f, 26.0f);

            if (isActive) {
                DrawRectangleRec(rowBtn, Color(45, 95, 165, 160));
            }

            const(char)* prefix = isActive ? "[Activo] " : "";
            char[128] mapBtnLabel;
            snprintf(mapBtnLabel.ptr, mapBtnLabel.length, "%s%s", prefix, proj.maps[i].relativePath.ptr);

            if (GuiButton(rowBtn, mapBtnLabel.ptr)) {
                project_switch_to_map(i);
            }

            if (gui_trash_button(delBtn)) {
                removeIndex = i;
            }

            rowY += rowH;
        }

        if (removeIndex >= 0) {
            project_remove_map(removeIndex);
        }
    }

    EndScissorMode();
}

void configure_gui_theme() {
    GuiLoadStyleDefault();

    // Desactivar el botón 'x' de cerrar pestañas en GuiTabBar y dar buen ancho a cada pestaña
    GuiSetStyle(TABBAR, TAB_CLOSE_BUTTON, 0);
    GuiSetStyle(TABBAR, TAB_ITEMS_WIDTH, 135);

    // Estilo oscuro legible
    GuiSetStyle(DEFAULT, TEXT_SIZE, 16);
    GuiSetStyle(DEFAULT, BACKGROUND_COLOR, 0x22252cff);
    GuiSetStyle(DEFAULT, LINE_COLOR, 0x454c59ff);

    GuiSetStyle(DEFAULT, BORDER_COLOR_NORMAL, 0x4f5866ff);
    GuiSetStyle(DEFAULT, BASE_COLOR_NORMAL, 0x2d323bff);
    GuiSetStyle(DEFAULT, TEXT_COLOR_NORMAL, 0xe6eaf0ff);

    GuiSetStyle(DEFAULT, BORDER_COLOR_FOCUSED, 0x689dffff);
    GuiSetStyle(DEFAULT, BASE_COLOR_FOCUSED, 0x3a4250ff);
    GuiSetStyle(DEFAULT, TEXT_COLOR_FOCUSED, 0xffffffff);

    GuiSetStyle(DEFAULT, BORDER_COLOR_PRESSED, 0x82b4ffff);
    GuiSetStyle(DEFAULT, BASE_COLOR_PRESSED, 0x29528aff);
    GuiSetStyle(DEFAULT, TEXT_COLOR_PRESSED, 0xffffffff);
}
