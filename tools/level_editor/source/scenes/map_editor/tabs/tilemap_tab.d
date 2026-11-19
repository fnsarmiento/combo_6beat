module scenes.map_editor.tabs.tilemap_tab;

nothrow:
@nogc:

import raylib;
import raygui;
import core.stdc.stdio : snprintf;
import core.stdc.math : floorf;
import scenes.map_editor.gui_widgets;
import scenes.map_editor.camera_controller;
import scenes.map_editor.tabs.tileset_tab :
    tile_pack,
    tile_unpack_tileset,
    tile_unpack_index,
    tileset_get_tileset_count,
    tileset_get_tile_count_at,
    tileset_is_tile_active_at,
    tileset_is_packed_tile_active,
    tileset_draw_packed_tile;

public:

enum int MAX_MAP_WIDTH  = 512;
enum int MAX_MAP_HEIGHT = 512;

enum TilemapTool : int {
    Pencil     = 0, // Pintar celda por celda (Click Izq = Primario, Click Der = Alternativo)
    Rectangle  = 1, // Rellenar rectángulo arrastrando
    Bucket     = 2, // Balde de relleno (Flood Fill)
    Eyedropper = 3, // Cuentagotas (Click Izq = Primario, Click Der = Alternativo)
    Eraser     = 4  // Borrador rápido
}

/**
 * Inicializa el estado de la pestaña Tilemap.
 */
void tilemap_tab_init() {
    map_width = 32;
    map_height = 20;
    selected_tile_primary = tile_pack(0, 0);
    selected_tile_secondary = -1;
    palette_tileset_filter = -1;
    active_tool = cast(int) TilemapTool.Pencil;
    palette_scroll = Vector2(0, 0);
    tilemap_cam.initialized = false;
    is_painting_stroke = false;
    is_dragging_rect = false;
    undo_head = 0;
    undo_count = 0;
    current_stroke_change_count = 0;
    clear_tilemap_cells();
}

/**
 * Reajusta las celdas del Tilemap tras eliminar un Tileset de la lista.
 */
void tilemap_remap_after_tileset_removed(int removedTs) {
    int totalCells = MAX_MAP_WIDTH * MAX_MAP_HEIGHT;
    for (int i = 0; i < totalCells; i++) {
        int val = map_cells[i];
        if (val < 0) continue;
        int ts = tile_unpack_tileset(val);
        int tid = tile_unpack_index(val);
        if (ts == removedTs) {
            map_cells[i] = -1;
        } else if (ts > removedTs) {
            map_cells[i] = tile_pack(ts - 1, tid);
        }
    }

    if (selected_tile_primary >= 0) {
        int pts = tile_unpack_tileset(selected_tile_primary);
        int ptid = tile_unpack_index(selected_tile_primary);
        if (pts == removedTs) {
            selected_tile_primary = find_first_active_packed_tile();
        } else if (pts > removedTs) {
            selected_tile_primary = tile_pack(pts - 1, ptid);
        }
    }

    if (selected_tile_secondary >= 0) {
        int sts = tile_unpack_tileset(selected_tile_secondary);
        int stid = tile_unpack_index(selected_tile_secondary);
        if (sts == removedTs) {
            selected_tile_secondary = -1;
        } else if (sts > removedTs) {
            selected_tile_secondary = tile_pack(sts - 1, stid);
        }
    }

    if (palette_tileset_filter == removedTs) {
        palette_tileset_filter = -1;
    } else if (palette_tileset_filter > removedTs) {
        palette_tileset_filter--;
    }

    undo_head = 0;
    undo_count = 0;
    current_stroke_change_count = 0;
}

/**
 * Deshace la última acción realizada en el Tilemap (Ctrl+Z independiente).
 */
void tilemap_tab_undo() {
    if (undo_count <= 0) {
        return;
    }
    undo_head = (undo_head - 1 + MAX_UNDO_STEPS) % MAX_UNDO_STEPS;
    undo_count--;

    TilemapUndoStep* step = &undo_steps[undo_head];
    for (int i = step.count - 1; i >= 0; i--) {
        int idx = step.changes[i].cellIndex;
        if (idx >= 0 && idx < MAX_MAP_WIDTH * MAX_MAP_HEIGHT) {
            map_cells[idx] = step.changes[i].oldValue;
        }
    }
}

int tilemap_get_width() {
    return map_width;
}

int tilemap_get_height() {
    return map_height;
}

void tilemap_set_size(int w, int h) {
    if (w < 1) w = 1;
    if (w > MAX_MAP_WIDTH) w = MAX_MAP_WIDTH;
    if (h < 1) h = 1;
    if (h > MAX_MAP_HEIGHT) h = MAX_MAP_HEIGHT;
    map_width = w;
    map_height = h;
}

int tilemap_get_cell(int col, int row) {
    if (col < 0 || col >= MAX_MAP_WIDTH || row < 0 || row >= MAX_MAP_HEIGHT) {
        return -1;
    }
    return map_cells[row * MAX_MAP_WIDTH + col];
}

void tilemap_set_cell(int col, int row, int tileId) {
    if (col < 0 || col >= MAX_MAP_WIDTH || row < 0 || row >= MAX_MAP_HEIGHT) {
        return;
    }
    map_cells[row * MAX_MAP_WIDTH + col] = tileId;
}

void tilemap_clear() {
    clear_tilemap_cells();
}

/**
 * Actualiza el estado del Tilemap Editor (incluyendo Ctrl+Z independiente y atajos de herramientas).
 */
void tilemap_tab_update(float dt) {
    if (map_width < 1) map_width = 1;
    if (map_width > MAX_MAP_WIDTH) map_width = MAX_MAP_WIDTH;
    if (map_height < 1) map_height = 1;
    if (map_height > MAX_MAP_HEIGHT) map_height = MAX_MAP_HEIGHT;

    bool ctrlDown = IsKeyDown(KeyboardKey.KEY_LEFT_CONTROL) || IsKeyDown(KeyboardKey.KEY_RIGHT_CONTROL);
    if (ctrlDown && IsKeyPressed(KeyboardKey.KEY_Z)) {
        tilemap_tab_undo();
    }

    if (palette_tileset_filter >= tileset_get_tileset_count()) {
        palette_tileset_filter = -1;
    }

    if (selected_tile_primary >= 0 && !tileset_is_packed_tile_active(selected_tile_primary)) {
        selected_tile_primary = find_first_active_packed_tile();
    }
    if (selected_tile_secondary >= 0 && !tileset_is_packed_tile_active(selected_tile_secondary)) {
        selected_tile_secondary = -1;
    }
}

/**
 * Dibuja la pestaña Tilemap:
 * - Izquierda/Centro: Barra de herramientas (Lápiz, Rectángulo, Balde, Gotero, Goma) + Lienzo con Cámara 2D y Zoom.
 * - Derecha: Tamaño del tilemap arriba (hasta 512x512) y paleta de tiles debajo con soporte para Click Izq (Primario) y Click Der (Alternativo).
 */
void tilemap_tab_draw(Rectangle workspace) {
    float rightPanelWidth = 370.0f;
    float pad = 10.0f;
    float toolbarH = 42.0f;

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
        toolbarH
    );

    Rectangle canvasBounds = Rectangle(
        leftArea.x,
        leftArea.y + toolbarH + 6.0f,
        leftArea.width,
        leftArea.height - toolbarH - 6.0f
    );

    Rectangle rightPanel = Rectangle(
        workspace.x + workspace.width - rightPanelWidth - pad,
        workspace.y + pad,
        rightPanelWidth,
        workspace.height - pad * 2.0f
    );

    draw_tilemap_toolbar(toolbarBounds, canvasBounds);
    draw_tilemap_canvas(canvasBounds);
    draw_tilemap_right_panel(rightPanel);
}

private:

enum int FIELD_MAP_WIDTH  = 2001;
enum int FIELD_MAP_HEIGHT = 2002;
enum float CELL_WORLD_SIZE = 32.0f;

enum int MAX_UNDO_STEPS        = 24;
enum int MAX_CHANGES_PER_STEP  = 8192;

struct CellChange {
    int cellIndex;
    int oldValue;
}

struct TilemapUndoStep {
    CellChange[MAX_CHANGES_PER_STEP] changes;
    int count = 0;
}

__gshared int map_width = 32;
__gshared int map_height = 20;
__gshared int selected_tile_primary = 0;    // Click Izquierdo (L) (packed)
__gshared int selected_tile_secondary = -1; // Click Derecho (R) (-1 = Borrar / Vacío)
__gshared int palette_tileset_filter = -1; // -1 = Todos los tilesets
__gshared int active_tool = 0;

__gshared int[MAX_MAP_WIDTH * MAX_MAP_HEIGHT] map_cells;

__gshared EditorCamera tilemap_cam;
__gshared Vector2 palette_scroll = Vector2(0, 0);
__gshared Rectangle palette_view = Rectangle(0, 0, 0, 0);

// Estado de trazo / herramienta rectángulo
__gshared bool is_painting_stroke = false;
__gshared bool is_dragging_rect = false;
__gshared int rect_start_col = 0;
__gshared int rect_start_row = 0;
__gshared int rect_end_col = 0;
__gshared int rect_end_row = 0;
__gshared bool rect_using_secondary = false;

// Historial de Undo independiente del Tilemap
__gshared TilemapUndoStep[MAX_UNDO_STEPS] undo_steps;
__gshared int undo_head = 0;
__gshared int undo_count = 0;
__gshared CellChange[MAX_CHANGES_PER_STEP] current_stroke_changes;
__gshared int current_stroke_change_count = 0;

void begin_undo_stroke() {
    current_stroke_change_count = 0;
}

void set_cell_with_undo(int col, int row, int newTile) {
    if (col < 0 || col >= map_width || row < 0 || row >= map_height) {
        return;
    }
    int idx = row * MAX_MAP_WIDTH + col;
    int oldVal = map_cells[idx];
    if (oldVal == newTile) {
        return;
    }

    if (current_stroke_change_count < MAX_CHANGES_PER_STEP) {
        current_stroke_changes[current_stroke_change_count].cellIndex = idx;
        current_stroke_changes[current_stroke_change_count].oldValue = oldVal;
        current_stroke_change_count++;
    }
    map_cells[idx] = newTile;
}

void commit_undo_stroke() {
    if (current_stroke_change_count <= 0) {
        return;
    }

    TilemapUndoStep* step = &undo_steps[undo_head];
    step.count = current_stroke_change_count;
    for (int i = 0; i < current_stroke_change_count; i++) {
        step.changes[i] = current_stroke_changes[i];
    }

    undo_head = (undo_head + 1) % MAX_UNDO_STEPS;
    if (undo_count < MAX_UNDO_STEPS) {
        undo_count++;
    }
    current_stroke_change_count = 0;
}

void clear_tilemap_cells() {
    for (int i = 0; i < MAX_MAP_WIDTH * MAX_MAP_HEIGHT; i++) {
        map_cells[i] = -1;
    }
}

int find_first_active_packed_tile() {
    int tsCount = tileset_get_tileset_count();
    for (int ts = 0; ts < tsCount; ts++) {
        int total = tileset_get_tile_count_at(ts);
        for (int i = 0; i < total; i++) {
            if (tileset_is_tile_active_at(ts, i)) {
                return tile_pack(ts, i);
            }
        }
    }
    return -1;
}

int count_active_tiles_in_filter() {
    int tsCount = tileset_get_tileset_count();
    int active = 0;
    for (int ts = 0; ts < tsCount; ts++) {
        if (palette_tileset_filter >= 0 && ts != palette_tileset_filter) continue;
        int total = tileset_get_tile_count_at(ts);
        for (int i = 0; i < total; i++) {
            if (tileset_is_tile_active_at(ts, i)) {
                active++;
            }
        }
    }
    return active;
}

void format_packed_tile_label(int packed, char* outBuf, size_t maxLen) {
    if (packed < 0) {
        snprintf(outBuf, maxLen, "Vacio");
    } else {
        snprintf(outBuf, maxLen, "T%d#%d", tile_unpack_tileset(packed), tile_unpack_index(packed));
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

void draw_tilemap_toolbar(Rectangle barBounds, Rectangle canvasBounds) {
    GuiPanel(barBounds, null);

    float x = barBounds.x + 8.0f;
    float y = barBounds.y + 8.0f;
    float h = 26.0f;

    // Selector de Herramientas del Tilemap
    GuiToggleGroup(
        Rectangle(x, y, 88.0f, h),
        "Lapiz;Rectangulo;Balde;Gotero;Goma",
        &active_tool
    );
    x += 456.0f;

    if (GuiButton(Rectangle(x, y, 95.0f, h), GuiIconText(GuiIconName.ICON_UNDO, "Ctrl+Z"))) {
        tilemap_tab_undo();
    }
    x += 102.0f;

    if (GuiButton(Rectangle(x, y, 108.0f, h), GuiIconText(GuiIconName.ICON_ZOOM_CENTER, "Vista (0,0)"))) {
        editor_camera_reset(tilemap_cam, get_canvas_inner_area(canvasBounds));
    }
}

void update_tilemap_camera(Rectangle viewArea) {
    if (!tilemap_cam.initialized) {
        editor_camera_reset(tilemap_cam, viewArea);
        tilemap_cam.camera.offset = Vector2(viewArea.x + 48.0f, viewArea.y + 48.0f);
    }

    Vector2 mousePos = GetMousePosition();
    if (!CheckCollisionPointRec(mousePos, viewArea)) {
        return;
    }

    // Mover cámara con Rueda/Botón Central o Espacio + Click
    bool isPanning = IsMouseButtonDown(MouseButton.MOUSE_BUTTON_MIDDLE) ||
                     (IsKeyDown(KeyboardKey.KEY_SPACE) &&
                      (IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT) || IsMouseButtonDown(MouseButton.MOUSE_BUTTON_RIGHT)));

    if (isPanning) {
        Vector2 delta = GetMouseDelta();
        tilemap_cam.camera.target.x -= delta.x / tilemap_cam.camera.zoom;
        tilemap_cam.camera.target.y -= delta.y / tilemap_cam.camera.zoom;
    }

    // Zoom hacia el cursor con rueda del mouse
    float wheel = GetMouseWheelMove();
    if (wheel != 0.0f) {
        Vector2 mouseWorldPos = GetScreenToWorld2D(mousePos, tilemap_cam.camera);
        tilemap_cam.camera.offset = mousePos;
        tilemap_cam.camera.target = mouseWorldPos;

        float zoomFactor = 1.0f + (wheel * 0.12f);
        if (zoomFactor < 0.2f) zoomFactor = 0.2f;

        tilemap_cam.camera.zoom *= zoomFactor;
        if (tilemap_cam.camera.zoom < 0.12f) tilemap_cam.camera.zoom = 0.12f;
        if (tilemap_cam.camera.zoom > 8.0f) tilemap_cam.camera.zoom = 8.0f;
    }
}

void flood_fill_tilemap(int startCol, int startRow, int replacementTile) {
    if (startCol < 0 || startCol >= map_width || startRow < 0 || startRow >= map_height) {
        return;
    }

    int targetTile = map_cells[startRow * MAX_MAP_WIDTH + startCol];
    if (targetTile == replacementTile) {
        return;
    }

    enum int QUEUE_CAP = 16384;
    __gshared int[QUEUE_CAP] qCol;
    __gshared int[QUEUE_CAP] qRow;
    int head = 0;
    int tail = 0;

    begin_undo_stroke();
    set_cell_with_undo(startCol, startRow, replacementTile);
    qCol[tail] = startCol;
    qRow[tail] = startRow;
    tail = (tail + 1) % QUEUE_CAP;

    int[4] dc = [1, -1, 0, 0];
    int[4] dr = [0, 0, 1, -1];

    int steps = 0;
    while (head != tail && steps < MAX_CHANGES_PER_STEP) {
        int c = qCol[head];
        int r = qRow[head];
        head = (head + 1) % QUEUE_CAP;
        steps++;

        for (int d = 0; d < 4; d++) {
            int nc = c + dc[d];
            int nr = r + dr[d];
            if (nc >= 0 && nc < map_width && nr >= 0 && nr < map_height) {
                if (map_cells[nr * MAX_MAP_WIDTH + nc] == targetTile) {
                    set_cell_with_undo(nc, nr, replacementTile);
                    int nextTail = (tail + 1) % QUEUE_CAP;
                    if (nextTail != head) {
                        qCol[tail] = nc;
                        qRow[tail] = nr;
                        tail = nextTail;
                    }
                }
            }
        }
    }

    commit_undo_stroke();
}

void apply_rectangle_fill(int c1, int r1, int c2, int r2, int tileId) {
    int minC = (c1 < c2) ? c1 : c2;
    int maxC = (c1 > c2) ? c1 : c2;
    int minR = (r1 < r2) ? r1 : r2;
    int maxR = (r1 > r2) ? r1 : r2;

    if (minC < 0) minC = 0;
    if (maxC >= map_width) maxC = map_width - 1;
    if (minR < 0) minR = 0;
    if (maxR >= map_height) maxR = map_height - 1;

    begin_undo_stroke();
    for (int r = minR; r <= maxR; r++) {
        for (int c = minC; c <= maxC; c++) {
            set_cell_with_undo(c, r, tileId);
        }
    }
    commit_undo_stroke();
}

void handle_tilemap_canvas_interaction(Rectangle viewArea) {
    Vector2 mouseScreen = GetMousePosition();
    bool insideView = CheckCollisionPointRec(mouseScreen, viewArea);
    Vector2 mouseWorld = editor_camera_screen_to_world(tilemap_cam, mouseScreen);

    int hoverCol = cast(int) floorf(mouseWorld.x / CELL_WORLD_SIZE);
    int hoverRow = cast(int) floorf(mouseWorld.y / CELL_WORLD_SIZE);
    bool inMapBounds = (hoverCol >= 0 && hoverCol < map_width && hoverRow >= 0 && hoverRow < map_height);

    // Finalizar trazo continuo de lápiz/goma
    if (is_painting_stroke &&
        !IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT) &&
        !IsMouseButtonDown(MouseButton.MOUSE_BUTTON_RIGHT)) {
        commit_undo_stroke();
        is_painting_stroke = false;
    }

    // Actualizar / finalizar herramienta Rectángulo
    if (is_dragging_rect) {
        rect_end_col = hoverCol;
        rect_end_row = hoverRow;

        bool released = rect_using_secondary
            ? IsMouseButtonReleased(MouseButton.MOUSE_BUTTON_RIGHT)
            : IsMouseButtonReleased(MouseButton.MOUSE_BUTTON_LEFT);

        if (released) {
            int fillTile = rect_using_secondary ? selected_tile_secondary : selected_tile_primary;
            apply_rectangle_fill(rect_start_col, rect_start_row, rect_end_col, rect_end_row, fillTile);
            is_dragging_rect = false;
        }
        return;
    }

    if (!insideView || !inMapBounds || IsKeyDown(KeyboardKey.KEY_SPACE) || IsMouseButtonDown(MouseButton.MOUSE_BUTTON_MIDDLE)) {
        return;
    }

    bool leftPressed  = IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_LEFT);
    bool rightPressed = IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_RIGHT);
    bool leftDown     = IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT);
    bool rightDown    = IsMouseButtonDown(MouseButton.MOUSE_BUTTON_RIGHT);

    final switch (cast(TilemapTool) active_tool) {
        case TilemapTool.Pencil:
            if ((leftPressed || rightPressed) && !is_painting_stroke) {
                begin_undo_stroke();
                is_painting_stroke = true;
            }
            if (leftDown) {
                set_cell_with_undo(hoverCol, hoverRow, selected_tile_primary);
            } else if (rightDown) {
                set_cell_with_undo(hoverCol, hoverRow, selected_tile_secondary);
            }
            break;

        case TilemapTool.Rectangle:
            if (leftPressed || rightPressed) {
                is_dragging_rect = true;
                rect_using_secondary = rightPressed;
                rect_start_col = hoverCol;
                rect_start_row = hoverRow;
                rect_end_col = hoverCol;
                rect_end_row = hoverRow;
            }
            break;

        case TilemapTool.Bucket:
            if (leftPressed) {
                flood_fill_tilemap(hoverCol, hoverRow, selected_tile_primary);
            } else if (rightPressed) {
                flood_fill_tilemap(hoverCol, hoverRow, selected_tile_secondary);
            }
            break;

        case TilemapTool.Eyedropper:
            if (leftPressed) {
                selected_tile_primary = map_cells[hoverRow * MAX_MAP_WIDTH + hoverCol];
            } else if (rightPressed) {
                selected_tile_secondary = map_cells[hoverRow * MAX_MAP_WIDTH + hoverCol];
            }
            break;

        case TilemapTool.Eraser:
            if ((leftPressed || rightPressed) && !is_painting_stroke) {
                begin_undo_stroke();
                is_painting_stroke = true;
            }
            if (leftDown || rightDown) {
                set_cell_with_undo(hoverCol, hoverRow, -1);
            }
            break;
    }
}

void draw_tilemap_canvas(Rectangle areaBounds) {
    GuiPanel(
        areaBounds,
        "Tilemap (Click Izq: Tile Primario [L] | Click Der: Tile Alternativo [R] | Rueda/Espacio+Click: Camara/Zoom)"
    );

    Rectangle viewArea = get_canvas_inner_area(areaBounds);
    update_tilemap_camera(viewArea);
    handle_tilemap_canvas_interaction(viewArea);

    BeginScissorMode(
        cast(int) viewArea.x,
        cast(int) viewArea.y,
        cast(int) viewArea.width,
        cast(int) viewArea.height
    );

    DrawRectangleRec(viewArea, Color(26, 29, 35, 255));

    BeginMode2D(tilemap_cam.camera);

    // Calcular rango visible de celdas para soportar mapas de 512x512 con fluidez total
    Vector2 worldTL = GetScreenToWorld2D(Vector2(viewArea.x, viewArea.y), tilemap_cam.camera);
    Vector2 worldBR = GetScreenToWorld2D(Vector2(viewArea.x + viewArea.width, viewArea.y + viewArea.height), tilemap_cam.camera);

    int minCol = cast(int) floorf(worldTL.x / CELL_WORLD_SIZE) - 1;
    int maxCol = cast(int) floorf(worldBR.x / CELL_WORLD_SIZE) + 1;
    int minRow = cast(int) floorf(worldTL.y / CELL_WORLD_SIZE) - 1;
    int maxRow = cast(int) floorf(worldBR.y / CELL_WORLD_SIZE) + 1;

    if (minCol < 0) minCol = 0;
    if (maxCol >= map_width) maxCol = map_width - 1;
    if (minRow < 0) minRow = 0;
    if (maxRow >= map_height) maxRow = map_height - 1;

    // Fondo y borde total del mapa
    Rectangle fullMapRect = Rectangle(0, 0, map_width * CELL_WORLD_SIZE, map_height * CELL_WORLD_SIZE);
    DrawRectangleRec(fullMapRect, Color(38, 41, 48, 255));

    for (int r = minRow; r <= maxRow; r++) {
        for (int c = minCol; c <= maxCol; c++) {
            Rectangle cellRect = Rectangle(
                c * CELL_WORLD_SIZE,
                r * CELL_WORLD_SIZE,
                CELL_WORLD_SIZE,
                CELL_WORLD_SIZE
            );

            int cellTile = map_cells[r * MAX_MAP_WIDTH + c];
            if (cellTile >= 0 && tileset_is_packed_tile_active(cellTile)) {
                tileset_draw_packed_tile(cellTile, cellRect);
            }

            if (tilemap_cam.camera.zoom >= 0.35f) {
                DrawRectangleLinesEx(cellRect, 1.0f / tilemap_cam.camera.zoom, Color(65, 70, 80, 180));
            }
        }
    }

    DrawRectangleLinesEx(fullMapRect, 2.0f / tilemap_cam.camera.zoom, Color(120, 150, 200, 255));

    // Previsualización de la herramienta Rectángulo mientras se arrastra
    if (is_dragging_rect) {
        int c1 = (rect_start_col < rect_end_col) ? rect_start_col : rect_end_col;
        int c2 = (rect_start_col > rect_end_col) ? rect_start_col : rect_end_col;
        int r1 = (rect_start_row < rect_end_row) ? rect_start_row : rect_end_row;
        int r2 = (rect_start_row > rect_end_row) ? rect_start_row : rect_end_row;

        Rectangle previewRect = Rectangle(
            c1 * CELL_WORLD_SIZE,
            r1 * CELL_WORLD_SIZE,
            (c2 - c1 + 1) * CELL_WORLD_SIZE,
            (r2 - r1 + 1) * CELL_WORLD_SIZE
        );

        Color previewCol = rect_using_secondary ? Color(255, 120, 220, 90) : Color(255, 230, 60, 90);
        Color outlineCol = rect_using_secondary ? Color(255, 120, 220, 255) : Colors.YELLOW;
        DrawRectangleRec(previewRect, previewCol);
        DrawRectangleLinesEx(previewRect, 2.5f / tilemap_cam.camera.zoom, outlineCol);
    } else {
        // Resaltar celda bajo el cursor
        Vector2 mouseWorld = editor_camera_screen_to_world(tilemap_cam, GetMousePosition());
        int hc = cast(int) floorf(mouseWorld.x / CELL_WORLD_SIZE);
        int hr = cast(int) floorf(mouseWorld.y / CELL_WORLD_SIZE);
        if (hc >= 0 && hc < map_width && hr >= 0 && hr < map_height) {
            Rectangle hoverRect = Rectangle(hc * CELL_WORLD_SIZE, hr * CELL_WORLD_SIZE, CELL_WORLD_SIZE, CELL_WORLD_SIZE);
            DrawRectangleLinesEx(hoverRect, 2.0f / tilemap_cam.camera.zoom, Colors.YELLOW);
        }
    }

    EndMode2D();

    // HUD inferior con zoom y celda actual
    Vector2 mw = editor_camera_screen_to_world(tilemap_cam, GetMousePosition());
    int curC = cast(int) floorf(mw.x / CELL_WORLD_SIZE);
    int curR = cast(int) floorf(mw.y / CELL_WORLD_SIZE);
    char[24] lStr = 0;
    char[24] rStr = 0;
    format_packed_tile_label(selected_tile_primary, lStr.ptr, lStr.length);
    format_packed_tile_label(selected_tile_secondary, rStr.ptr, rStr.length);
    char[128] hud;
    snprintf(
        hud.ptr,
        hud.length,
        "Zoom: %.0f%% | Celda: (%d, %d) | L: %s | R: %s",
        tilemap_cam.camera.zoom * 100.0f,
        curC,
        curR,
        lStr.ptr,
        rStr.ptr
    );
    DrawRectangle(cast(int) viewArea.x + 8, cast(int)(viewArea.y + viewArea.height - 28), 400, 22, Color(18, 20, 24, 210));
    DrawText(hud.ptr, cast(int) viewArea.x + 14, cast(int)(viewArea.y + viewArea.height - 24), 13, Colors.RAYWHITE);

    EndScissorMode();
}

void draw_tilemap_right_panel(Rectangle panelBounds) {
    GuiPanel(panelBounds, "Tilemap");

    float x = panelBounds.x + 14.0f;
    float y = panelBounds.y + 36.0f;
    float w = panelBounds.width - 28.0f;

    // 1. Parte superior del panel derecho: Tamaño del Tilemap (hasta 512x512)
    gui_section_header(x, y, w, "Tamano del Tilemap (Max 512x512)");
    gui_labeled_int_spinner(x, y, w, "Ancho (Cols)", &map_width, 1, MAX_MAP_WIDTH, FIELD_MAP_WIDTH);
    gui_labeled_int_spinner(x, y, w, "Alto (Filas)", &map_height, 1, MAX_MAP_HEIGHT, FIELD_MAP_HEIGHT);

    if (GuiButton(Rectangle(x, y, w, 24), GuiIconText(GuiIconName.ICON_BIN, "Limpiar Tilemap"))) {
        begin_undo_stroke();
        for (int r = 0; r < map_height; r++) {
            for (int c = 0; c < map_width; c++) {
                set_cell_with_undo(c, r, -1);
            }
        }
        commit_undo_stroke();
    }
    y += 30.0f;

    // 2. Indicador de Tiles Primario (Click Izq) y Alternativo (Click Der)
    gui_section_header(x, y, w, "Seleccion (Izq: Primario | Der: Alt)");
    char[24] lLbl = 0;
    char[24] rLbl = 0;
    format_packed_tile_label(selected_tile_primary, lLbl.ptr, lLbl.length);
    format_packed_tile_label(selected_tile_secondary, rLbl.ptr, rLbl.length);
    char[96] selInfo;
    snprintf(
        selInfo.ptr,
        selInfo.length,
        "[L] Primario: %s   |   [R] Alt: %s",
        lLbl.ptr,
        rLbl.ptr
    );
    GuiLabel(Rectangle(x, y, w, 20.0f), selInfo.ptr);
    y += 24.0f;

    // 2.5 Filtro de Tileset en la paleta
    int tsCount = tileset_get_tileset_count();
    float navW = 34.0f;
    if (GuiButton(Rectangle(x, y, navW, 22.0f), "<")) {
        if (palette_tileset_filter < 0) {
            palette_tileset_filter = (tsCount > 0) ? (tsCount - 1) : -1;
        } else {
            palette_tileset_filter--;
        }
    }
    char[64] filterTxt;
    if (palette_tileset_filter < 0) {
        snprintf(filterTxt.ptr, filterTxt.length, "Paleta: Todos (%d tilesets)", tsCount);
    } else {
        snprintf(filterTxt.ptr, filterTxt.length, "Paleta: Tileset #%d / %d", palette_tileset_filter + 1, tsCount);
    }
    GuiLabel(Rectangle(x + navW + 6.0f, y, w - navW * 2.0f - 12.0f, 22.0f), filterTxt.ptr);
    if (GuiButton(Rectangle(x + w - navW, y, navW, 22.0f), ">")) {
        if (palette_tileset_filter < 0) {
            palette_tileset_filter = (tsCount > 0) ? 0 : -1;
        } else {
            palette_tileset_filter++;
            if (palette_tileset_filter >= tsCount) palette_tileset_filter = -1;
        }
    }
    y += 28.0f;

    // 3. Parte inferior del panel derecho: Tiles activos de los Tilesets (+ slot -1 para borrar)
    Rectangle tilesScrollBounds = Rectangle(
        x,
        y,
        w,
        (panelBounds.y + panelBounds.height) - y - 12.0f
    );

    int activeTiles = count_active_tiles_in_filter() + 1; // +1 para la casilla Vacío (-1)
    int cols = 4;
    int rows = (activeTiles + cols - 1) / cols;
    float tileSize = 62.0f;
    float gap = 10.0f;

    float contentH = rows * (tileSize + gap) + gap * 2.0f;
    if (contentH < tilesScrollBounds.height - 24.0f) {
        contentH = tilesScrollBounds.height - 24.0f;
    }

    Rectangle contentRec = Rectangle(0, 0, tilesScrollBounds.width - 20.0f, contentH);
    GuiScrollPanel(tilesScrollBounds, null, contentRec, &palette_scroll, &palette_view);

    BeginScissorMode(
        cast(int) palette_view.x,
        cast(int) palette_view.y,
        cast(int) palette_view.width,
        cast(int) palette_view.height
    );

    Vector2 mousePos = GetMousePosition();
    bool mouseInPalette = CheckCollisionPointRec(mousePos, palette_view);
    float startX = palette_view.x + gap + palette_scroll.x;
    float startY = palette_view.y + gap + palette_scroll.y;

    // Casilla 0: Vacío / Goma (-1)
    draw_palette_slot(-1, 0, cols, startX, startY, tileSize, gap, mousePos, mouseInPalette);

    int visibleIndex = 1;
    for (int ts = 0; ts < tsCount; ts++) {
        if (palette_tileset_filter >= 0 && ts != palette_tileset_filter) continue;
        int total = tileset_get_tile_count_at(ts);
        for (int tileId = 0; tileId < total; tileId++) {
            if (!tileset_is_tile_active_at(ts, tileId)) {
                continue;
            }
            draw_palette_slot(tile_pack(ts, tileId), visibleIndex, cols, startX, startY, tileSize, gap, mousePos, mouseInPalette);
            visibleIndex++;
        }
    }

    EndScissorMode();
}

void draw_palette_slot(
    int packedTile,
    int visibleIndex,
    int cols,
    float startX,
    float startY,
    float tileSize,
    float gap,
    Vector2 mousePos,
    bool mouseInPalette
) {
    int r = visibleIndex / cols;
    int c = visibleIndex % cols;

    Rectangle itemRect = Rectangle(
        startX + c * (tileSize + gap),
        startY + r * (tileSize + gap),
        tileSize,
        tileSize
    );

    if (packedTile < 0) {
        DrawRectangleRec(itemRect, Color(35, 38, 45, 255));
        DrawLineEx(
            Vector2(itemRect.x + 8, itemRect.y + 8),
            Vector2(itemRect.x + itemRect.width - 8, itemRect.y + itemRect.height - 8),
            2.0f,
            Color(160, 80, 80, 220)
        );
        DrawText("Vacio", cast(int)(itemRect.x + 12), cast(int)(itemRect.y + 24), 13, Color(200, 200, 210, 255));
    } else {
        tileset_draw_packed_tile(packedTile, itemRect);
        char[16] label;
        snprintf(label.ptr, label.length, "%d:%d", tile_unpack_tileset(packedTile), tile_unpack_index(packedTile));
        DrawRectangle(cast(int)(itemRect.x + 2), cast(int)(itemRect.y + 2), 34, 15, Color(15, 18, 25, 175));
        DrawText(label.ptr, cast(int)(itemRect.x + 4), cast(int)(itemRect.y + 3), 12, Colors.RAYWHITE);
    }

    bool isPrimary   = (selected_tile_primary == packedTile);
    bool isSecondary = (selected_tile_secondary == packedTile);

    if (isPrimary) {
        DrawRectangleLinesEx(itemRect, 3.0f, Colors.YELLOW);
        DrawRectangle(cast(int)(itemRect.x + itemRect.width - 18), cast(int) itemRect.y + 2, 16, 15, Colors.YELLOW);
        DrawText("L", cast(int)(itemRect.x + itemRect.width - 14), cast(int) itemRect.y + 3, 12, Colors.BLACK);
    }
    if (isSecondary) {
        Rectangle inner = Rectangle(itemRect.x + 2, itemRect.y + 2, itemRect.width - 4, itemRect.height - 4);
        DrawRectangleLinesEx(inner, 2.5f, Color(255, 110, 220, 255));
        DrawRectangle(cast(int)(itemRect.x + itemRect.width - 18), cast(int)(itemRect.y + itemRect.height - 17), 16, 15, Color(255, 110, 220, 255));
        DrawText("R", cast(int)(itemRect.x + itemRect.width - 14), cast(int)(itemRect.y + itemRect.height - 16), 12, Colors.BLACK);
    }
    if (!isPrimary && !isSecondary) {
        DrawRectangleLinesEx(itemRect, 1.0f, Color(170, 175, 185, 255));
    }

    if (mouseInPalette && CheckCollisionPointRec(mousePos, itemRect)) {
        if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_LEFT)) {
            selected_tile_primary = packedTile;
        } else if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_RIGHT)) {
            selected_tile_secondary = packedTile;
        }
    }
}
