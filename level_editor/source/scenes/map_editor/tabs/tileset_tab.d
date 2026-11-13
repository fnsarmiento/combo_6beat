module scenes.map_editor.tabs.tileset_tab;

nothrow:
@nogc:

import raylib;
import raygui;
import core.stdc.stdio : snprintf;
import core.stdc.string : strncpy;
import engine.tinyfd : open_png_file_dialog;
import scenes.map_editor.gui_widgets;
import scenes.map_editor.level_data : scene_to_relative_resource_path, scene_resolve_resource_path;

public:

enum int MAX_TILESET_TILES = 1024;

/**
 * Inicializa el estado del único Tileset compartido por el Tilemap.
 */
void tileset_tab_init() {
    unload_tileset_texture();
    tileset_path[0] = '\0';
    tile_width_px = 32;
    tile_height_px = 32;
    tile_count = 32;
    selected_tileset_tile = 0;
    tileset_scroll = Vector2(0, 0);
    undo_count = 0;

    for (int i = 0; i < MAX_TILESET_TILES; i++) {
        tile_active[i] = true;
    }
}

/**
 * Libera la textura del Tileset al cerrar.
 */
void tileset_tab_close() {
    unload_tileset_texture();
}

/**
 * Deshace la última acción en la pestaña Tileset (Ctrl+Z independiente).
 */
void tileset_tab_undo() {
    if (undo_count <= 0) {
        return;
    }
    undo_count--;
    TilesetSnapshot* snap = &undo_stack[undo_count];
    tile_width_px = snap.tileWidthPx;
    tile_height_px = snap.tileHeightPx;
    tile_count = snap.tileCount;
    for (int i = 0; i < MAX_TILESET_TILES; i++) {
        tile_active[i] = snap.tileActive[i];
    }
}

/**
 * Retorna la cantidad total de tiles en el Tileset.
 */
int tileset_get_tile_count() {
    return tile_count;
}

/**
 * Establece la cantidad total de tiles en el Tileset.
 */
void tileset_set_tile_count(int count) {
    if (count < 1) count = 1;
    if (count > MAX_TILESET_TILES) count = MAX_TILESET_TILES;
    tile_count = count;
}

/**
 * Retorna el tamaño en píxeles de cada tile (ancho, alto).
 */
int tileset_get_tile_width_px() {
    return tile_width_px;
}

int tileset_get_tile_height_px() {
    return tile_height_px;
}

void tileset_set_tile_size_px(int w, int h) {
    tile_width_px = (w > 0) ? w : 32;
    tile_height_px = (h > 0) ? h : 32;
}

/**
 * Retorna si un tile específico del tileset está activo (habilitado para usarse en el Tilemap).
 */
bool tileset_is_tile_active(int tileId) {
    if (tileId < 0 || tileId >= tile_count || tileId >= MAX_TILESET_TILES) {
        return false;
    }
    return tile_active[tileId];
}

void tileset_set_tile_active(int tileId, bool active) {
    if (tileId >= 0 && tileId < MAX_TILESET_TILES) {
        tile_active[tileId] = active;
    }
}

/**
 * Retorna la ruta relativa actual del PNG del Tileset.
 */
const(char)* tileset_get_path() {
    return tileset_path.ptr;
}

/**
 * Establece la ruta relativa del PNG del Tileset y recarga su textura.
 */
bool tileset_set_path_and_reload(const(char)* relPath, bool autoCalcCount = false) {
    if (relPath is null) {
        tileset_path[0] = '\0';
        unload_tileset_texture();
        return false;
    }
    strncpy(tileset_path.ptr, relPath, tileset_path.length - 1);
    tileset_path[tileset_path.length - 1] = '\0';
    return tileset_reload_texture(autoCalcCount);
}

/**
 * Recarga la textura PNG del Tileset usando la resolución relativa al XML.
 */
bool tileset_reload_texture(bool autoCalcCount = false) {
    unload_tileset_texture();

    if (tileset_path[0] == '\0') {
        return false;
    }

    if (tileset_path[0] == '/') {
        char[256] relBuf;
        scene_to_relative_resource_path(tileset_path.ptr, relBuf.ptr, relBuf.length);
        strncpy(tileset_path.ptr, relBuf.ptr, tileset_path.length - 1);
        tileset_path[tileset_path.length - 1] = '\0';
    }

    char[1024] fullPath;
    scene_resolve_resource_path(tileset_path.ptr, fullPath.ptr, fullPath.length);
    if (!FileExists(fullPath.ptr)) {
        return false;
    }

    tileset_texture = LoadTexture(fullPath.ptr);
    has_tileset_texture = (tileset_texture.id != 0);

    if (has_tileset_texture && autoCalcCount && tile_width_px > 0 && tile_height_px > 0) {
        int cols = tileset_texture.width / tile_width_px;
        int rows = tileset_texture.height / tile_height_px;
        int total = cols * rows;
        if (total > 0 && total <= MAX_TILESET_TILES) {
            tile_count = total;
        }
    }

    return has_tileset_texture;
}

/**
 * Dibuja el tile `tileId` dentro del rectángulo de destino `destRect`
 * usando la textura PNG del Tileset (o un color de respaldo si aún no se cargó un PNG).
 */
void tileset_draw_tile(int tileId, Rectangle destRect, ubyte alpha = 255) {
    if (has_tileset_texture && tileset_texture.id != 0 && tile_width_px > 0 && tile_height_px > 0) {
        int texCols = tileset_texture.width / tile_width_px;
        if (texCols < 1) texCols = 1;

        int col = tileId % texCols;
        int row = tileId / texCols;

        Rectangle srcRect = Rectangle(
            cast(float)(col * tile_width_px),
            cast(float)(row * tile_height_px),
            cast(float) tile_width_px,
            cast(float) tile_height_px
        );

        DrawTexturePro(
            tileset_texture,
            srcRect,
            destRect,
            Vector2(0, 0),
            0.0f,
            Color(255, 255, 255, alpha)
        );
    } else {
        Color fallbackCol = Color(
            cast(ubyte)(50 + (tileId * 37) % 140),
            cast(ubyte)(70 + (tileId * 53) % 140),
            cast(ubyte)(100 + (tileId * 29) % 130),
            alpha
        );
        DrawRectangleRec(destRect, fallbackCol);
    }
}

/**
 * Actualiza la lógica del Tileset Editor (incluyendo Ctrl+Z independiente).
 */
void tileset_tab_update(float dt) {
    bool ctrlDown = IsKeyDown(KeyboardKey.KEY_LEFT_CONTROL) || IsKeyDown(KeyboardKey.KEY_RIGHT_CONTROL);
    if (ctrlDown && IsKeyPressed(KeyboardKey.KEY_Z)) {
        tileset_tab_undo();
    }

    if (tile_count < 1) tile_count = 1;
    if (tile_count > MAX_TILESET_TILES) tile_count = MAX_TILESET_TILES;
    if (tile_width_px < 1) tile_width_px = 1;
    if (tile_height_px < 1) tile_height_px = 1;
    if (selected_tileset_tile >= tile_count) selected_tileset_tile = tile_count - 1;
    if (selected_tileset_tile < 0) selected_tileset_tile = 0;
}

/**
 * Dibuja la interfaz del Tileset Editor:
 * - Área principal izquierda: Previsualización de los tiles del PNG; click para activar/desactivar tiles.
 * - Panel lateral derecho: Selección de PNG con tinyfd, tamaño en píxeles por tile, cantidad de tiles y estado activo.
 */
void tileset_tab_draw(Rectangle workspace) {
    float rightPanelWidth = 360.0f;

    Rectangle previewArea = Rectangle(
        workspace.x + 12.0f,
        workspace.y + 12.0f,
        workspace.width - rightPanelWidth - 36.0f,
        workspace.height - 24.0f
    );

    Rectangle sidePanel = Rectangle(
        workspace.x + workspace.width - rightPanelWidth - 12.0f,
        workspace.y + 12.0f,
        rightPanelWidth,
        workspace.height - 24.0f
    );

    draw_tileset_preview(previewArea);
    draw_tileset_side_panel(sidePanel);
}

private:

enum int MAX_TILESET_UNDO        = 32;
enum int FIELD_TILESET_PATH      = 1000;
enum int FIELD_TILESET_COUNT     = 1001;
enum int FIELD_TILESET_TILE_W    = 1002;
enum int FIELD_TILESET_TILE_H    = 1003;

struct TilesetSnapshot {
    int tileWidthPx;
    int tileHeightPx;
    int tileCount;
    bool[MAX_TILESET_TILES] tileActive;
}

__gshared char[256] tileset_path = 0;
__gshared Texture2D tileset_texture;
__gshared bool has_tileset_texture = false;

__gshared int tile_width_px = 32;
__gshared int tile_height_px = 32;
__gshared int tile_count = 32;
__gshared bool[MAX_TILESET_TILES] tile_active;
__gshared int selected_tileset_tile = 0;

__gshared TilesetSnapshot[MAX_TILESET_UNDO] undo_stack;
__gshared int undo_count = 0;

__gshared Vector2 tileset_scroll = Vector2(0, 0);
__gshared Rectangle tileset_view = Rectangle(0, 0, 0, 0);

void push_tileset_undo() {
    if (undo_count >= MAX_TILESET_UNDO) {
        for (int i = 1; i < MAX_TILESET_UNDO; i++) {
            undo_stack[i - 1] = undo_stack[i];
        }
        undo_count = MAX_TILESET_UNDO - 1;
    }

    TilesetSnapshot* snap = &undo_stack[undo_count++];
    snap.tileWidthPx = tile_width_px;
    snap.tileHeightPx = tile_height_px;
    snap.tileCount = tile_count;
    for (int i = 0; i < MAX_TILESET_TILES; i++) {
        snap.tileActive[i] = tile_active[i];
    }
}

void unload_tileset_texture() {
    if (has_tileset_texture && tileset_texture.id != 0) {
        UnloadTexture(tileset_texture);
    }
    tileset_texture = Texture2D.init;
    has_tileset_texture = false;
}

void draw_tileset_side_panel(Rectangle panelBounds) {
    GuiPanel(panelBounds, "Tileset - Propiedades (Ctrl+Z: Deshacer)");

    float x = panelBounds.x + 16.0f;
    float y = panelBounds.y + 38.0f;
    float w = panelBounds.width - 32.0f;

    // 1. Archivo PNG del Tileset (modo relativo con tinyfd)
    gui_section_header(x, y, w, "Imagen del Tileset (.png)");
    GuiLabel(Rectangle(x, y, w, 20.0f), "Resource Path (Relativo)");
    y += 22.0f;

    float btnW = 92.0f;
    if (gui_search_box(Rectangle(x, y, w - btnW - 6.0f, 24.0f), tileset_path.ptr, cast(int) tileset_path.length, FIELD_TILESET_PATH)) {
        tileset_reload_texture(true);
    }

    if (GuiButton(Rectangle(x + w - btnW, y, btnW, 24.0f), GuiIconText(GuiIconName.ICON_FOLDER_OPEN, "PNG..."))) {
        char[512] selectedAbs;
        if (open_png_file_dialog(selectedAbs.ptr, selectedAbs.length, "Seleccionar PNG del Tileset")) {
            push_tileset_undo();
            scene_to_relative_resource_path(selectedAbs.ptr, tileset_path.ptr, tileset_path.length);
            tileset_reload_texture(true);
        }
    }
    y += 30.0f;

    if (has_tileset_texture) {
        char[96] texStatus;
        snprintf(texStatus.ptr, texStatus.length, "PNG cargado: %dx%d px", tileset_texture.width, tileset_texture.height);
        GuiLabel(Rectangle(x, y, w, 20.0f), texStatus.ptr);
    } else {
        GuiLabel(Rectangle(x, y, w, 20.0f), "Sin PNG cargado (usando vista previa)");
    }
    y += 26.0f;

    // 2. Tamaño en píxeles de cada tile y cantidad de tiles
    gui_section_header(x, y, w, "Dimensiones de Tiles");
    int prevW = tile_width_px;
    int prevH = tile_height_px;
    int prevCount = tile_count;

    gui_labeled_int_spinner(x, y, w, "Tamano X (px)", &tile_width_px, 1, 512, FIELD_TILESET_TILE_W);
    gui_labeled_int_spinner(x, y, w, "Tamano Y (px)", &tile_height_px, 1, 512, FIELD_TILESET_TILE_H);
    gui_labeled_int_spinner(x, y, w, "Cantidad Tiles", &tile_count, 1, MAX_TILESET_TILES, FIELD_TILESET_COUNT);

    if (tile_width_px != prevW || tile_height_px != prevH || tile_count != prevCount) {
        int curW = tile_width_px;
        int curH = tile_height_px;
        int curC = tile_count;
        tile_width_px = prevW;
        tile_height_px = prevH;
        tile_count = prevCount;
        push_tileset_undo();
        tile_width_px = curW;
        tile_height_px = curH;
        tile_count = curC;
    }

    if (has_tileset_texture) {
        if (GuiButton(Rectangle(x, y, w, 24.0f), "Ajustar Cantidad segun PNG")) {
            push_tileset_undo();
            int cols = tileset_texture.width / tile_width_px;
            int rows = tileset_texture.height / tile_height_px;
            int total = cols * rows;
            if (total >= 1 && total <= MAX_TILESET_TILES) {
                tile_count = total;
            }
        }
        y += 30.0f;
    }

    // 3. Activar / Desactivar tiles del Tileset
    y += 6.0f;
    gui_section_header(x, y, w, "Activacion de Tiles");

    char[64] selText;
    snprintf(selText.ptr, selText.length, "Tile Seleccionado: #%d", selected_tileset_tile);
    GuiLabel(Rectangle(x, y, w, 22.0f), selText.ptr);
    y += 26.0f;

    if (selected_tileset_tile >= 0 && selected_tileset_tile < tile_count) {
        bool prevAct = tile_active[selected_tileset_tile];
        gui_labeled_checkbox(x, y, "Tile Activo (Visible en Tilemap)", &tile_active[selected_tileset_tile]);
        if (tile_active[selected_tileset_tile] != prevAct) {
            bool newAct = tile_active[selected_tileset_tile];
            tile_active[selected_tileset_tile] = prevAct;
            push_tileset_undo();
            tile_active[selected_tileset_tile] = newAct;
        }
    }

    float halfW = (w - 6.0f) * 0.5f;
    if (GuiButton(Rectangle(x, y, halfW, 26.0f), "Activar Todos")) {
        push_tileset_undo();
        for (int i = 0; i < tile_count; i++) {
            tile_active[i] = true;
        }
    }
    if (GuiButton(Rectangle(x + halfW + 6.0f, y, halfW, 26.0f), "Desactivar Todos")) {
        push_tileset_undo();
        for (int i = 0; i < tile_count; i++) {
            tile_active[i] = false;
        }
    }
    y += 34.0f;

    if (GuiButton(Rectangle(x, y, w, 26.0f), GuiIconText(GuiIconName.ICON_UNDO, "Deshacer (Ctrl+Z)"))) {
        tileset_tab_undo();
    }
    y += 34.0f;

    int activeCount = 0;
    for (int i = 0; i < tile_count; i++) {
        if (tile_active[i]) activeCount++;
    }

    char[80] summaryBuf;
    snprintf(summaryBuf.ptr, summaryBuf.length, "Tiles habilitados: %d / %d", activeCount, tile_count);
    GuiLabel(Rectangle(x, y, w, 22.0f), summaryBuf.ptr);
}

void draw_tileset_preview(Rectangle areaBounds) {
    int cols = 8;
    if (has_tileset_texture && tile_width_px > 0) {
        int pngCols = tileset_texture.width / tile_width_px;
        if (pngCols >= 1 && pngCols <= 16) {
            cols = pngCols;
        }
    }

    int rows = (tile_count + cols - 1) / cols;
    float cellSize = 68.0f;
    float padding = 10.0f;

    float contentWidth = cols * (cellSize + padding) + padding * 2.0f;
    if (contentWidth < areaBounds.width - 20.0f) {
        contentWidth = areaBounds.width - 20.0f;
    }

    float contentHeight = rows * (cellSize + padding) + padding * 2.0f;
    if (contentHeight < areaBounds.height - 30.0f) {
        contentHeight = areaBounds.height - 30.0f;
    }

    Rectangle contentRec = Rectangle(0, 0, contentWidth, contentHeight);
    GuiScrollPanel(
        areaBounds,
        "Tileset (Click Izq: Seleccionar | Click Der: Activar/Desactivar Tile | Ctrl+Z: Deshacer)",
        contentRec,
        &tileset_scroll,
        &tileset_view
    );

    BeginScissorMode(
        cast(int) tileset_view.x,
        cast(int) tileset_view.y,
        cast(int) tileset_view.width,
        cast(int) tileset_view.height
    );

    Vector2 mousePos = GetMousePosition();
    bool mouseInView = CheckCollisionPointRec(mousePos, tileset_view);

    float startX = tileset_view.x + padding + tileset_scroll.x;
    float startY = tileset_view.y + padding + tileset_scroll.y;

    for (int i = 0; i < tile_count; i++) {
        int r = i / cols;
        int c = i % cols;

        Rectangle tileRect = Rectangle(
            startX + c * (cellSize + padding),
            startY + r * (cellSize + padding),
            cellSize,
            cellSize
        );

        bool isActive = tile_active[i];
        bool isSelected = (selected_tileset_tile == i);

        tileset_draw_tile(i, tileRect, isActive ? 255 : 75);

        if (!isActive) {
            DrawRectangleRec(tileRect, Color(20, 20, 20, 140));
            DrawLineEx(
                Vector2(tileRect.x + 4, tileRect.y + 4),
                Vector2(tileRect.x + tileRect.width - 4, tileRect.y + tileRect.height - 4),
                2.0f,
                Color(220, 60, 60, 220)
            );
        }

        Color borderCol = isSelected
            ? Colors.YELLOW
            : (isActive ? Color(200, 210, 220, 255) : Color(180, 50, 50, 200));
        DrawRectangleLinesEx(tileRect, isSelected ? 3.0f : 1.5f, borderCol);

        char[16] numBuf;
        snprintf(numBuf.ptr, numBuf.length, "#%d", i);
        DrawText(numBuf.ptr, cast(int)(tileRect.x + 5), cast(int)(tileRect.y + 4), 13, Colors.RAYWHITE);

        if (mouseInView && CheckCollisionPointRec(mousePos, tileRect)) {
            if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_LEFT)) {
                if (selected_tileset_tile == i) {
                    push_tileset_undo();
                    tile_active[i] = !tile_active[i];
                } else {
                    selected_tileset_tile = i;
                }
            } else if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_RIGHT)) {
                push_tileset_undo();
                selected_tileset_tile = i;
                tile_active[i] = !tile_active[i];
            }
        }
    }

    EndScissorMode();
}
