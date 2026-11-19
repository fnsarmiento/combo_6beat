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
import scenes.map_editor.tabs.tilemap_tab : tilemap_remap_after_tileset_removed;

public:

enum int MAX_TILESETS      = 16;
enum int MAX_TILESET_TILES = 1024;

struct TilesetSlot {
    bool used = false;
    char[256] path = 0;
    Texture2D texture;
    bool hasTexture = false;
    int tileWidthPx = 32;
    int tileHeightPx = 32;
    int tileCount = 32;
    bool[MAX_TILESET_TILES] tileActive;
}

/**
 * Empaqueta (tilesetIdx, localTileId) en un entero positivo para almacenar en celdas del Tilemap.
 * Para tilesetIdx == 0 coincide exactamente con localTileId (retrocompatible).
 */
int tile_pack(int tilesetIdx, int localTileId) {
    if (localTileId < 0) {
        return -1;
    }
    int ts = (tilesetIdx < 0) ? 0 : (tilesetIdx & 0x7FFF);
    return (ts << 16) | (localTileId & 0xFFFF);
}

int tile_unpack_tileset(int packed) {
    if (packed < 0) {
        return -1;
    }
    return (packed >> 16) & 0x7FFF;
}

int tile_unpack_index(int packed) {
    if (packed < 0) {
        return -1;
    }
    return packed & 0xFFFF;
}

/**
 * Inicializa la pestaña Tileset con 1 slot vacío por defecto.
 */
void tileset_tab_init() {
    tileset_clear_all();
    tileset_add_slot("", 32, 32, 32, false);
    selected_tileset_tile = 0;
    tileset_scroll = Vector2(0, 0);
    undo_count = 0;
}

/**
 * Libera todas las texturas de tilesets al cerrar.
 */
void tileset_tab_close() {
    tileset_clear_all();
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
    int ts = snap.slotIndex;
    if (ts < 0 || ts >= tileset_count) {
        return;
    }
    tileset_slots[ts].tileWidthPx = snap.tileWidthPx;
    tileset_slots[ts].tileHeightPx = snap.tileHeightPx;
    tileset_slots[ts].tileCount = snap.tileCount;
    for (int i = 0; i < MAX_TILESET_TILES; i++) {
        tileset_slots[ts].tileActive[i] = snap.tileActive[i];
    }
    active_tileset_index = ts;
}

// -----------------------------------------------------------------------------
// API Multi-Tileset
// -----------------------------------------------------------------------------

int tileset_get_tileset_count() {
    return tileset_count;
}

int tileset_get_active_index() {
    return active_tileset_index;
}

void tileset_set_active_index(int idx) {
    if (tileset_count <= 0) {
        active_tileset_index = 0;
        return;
    }
    if (idx < 0) idx = 0;
    if (idx >= tileset_count) idx = tileset_count - 1;
    active_tileset_index = idx;
    selected_tileset_tile = 0;
}

void tileset_clear_all() {
    for (int i = 0; i < MAX_TILESETS; i++) {
        unload_slot_texture(i);
        tileset_slots[i] = TilesetSlot.init;
        for (int t = 0; t < MAX_TILESET_TILES; t++) {
            tileset_slots[i].tileActive[t] = true;
        }
    }
    tileset_count = 0;
    active_tileset_index = 0;
    selected_tileset_tile = 0;
}

int tileset_add_slot(const(char)* relPath, int tw, int th, int count, bool autoCalcCount = false) {
    if (tileset_count >= MAX_TILESETS) {
        return -1;
    }
    int idx = tileset_count++;
    tileset_slots[idx] = TilesetSlot.init;
    tileset_slots[idx].used = true;
    tileset_slots[idx].tileWidthPx = (tw > 0) ? tw : 32;
    tileset_slots[idx].tileHeightPx = (th > 0) ? th : 32;
    if (count < 1) count = 1;
    if (count > MAX_TILESET_TILES) count = MAX_TILESET_TILES;
    tileset_slots[idx].tileCount = count;
    for (int t = 0; t < MAX_TILESET_TILES; t++) {
        tileset_slots[idx].tileActive[t] = true;
    }

    if (relPath !is null && relPath[0] != '\0') {
        strncpy(tileset_slots[idx].path.ptr, relPath, tileset_slots[idx].path.length - 1);
        tileset_slots[idx].path[tileset_slots[idx].path.length - 1] = '\0';
        tileset_reload_texture_at(idx, autoCalcCount);
    }
    return idx;
}

bool tileset_remove_slot(int idx) {
    if (idx < 0 || idx >= tileset_count) {
        return false;
    }

    unload_slot_texture(idx);

    if (tileset_count == 1) {
        // Reiniciar el único slot en lugar de dejar 0 tilesets
        tileset_slots[0] = TilesetSlot.init;
        tileset_slots[0].used = true;
        tileset_slots[0].tileWidthPx = 32;
        tileset_slots[0].tileHeightPx = 32;
        tileset_slots[0].tileCount = 32;
        for (int t = 0; t < MAX_TILESET_TILES; t++) {
            tileset_slots[0].tileActive[t] = true;
        }
        tilemap_remap_after_tileset_removed(0);
        return true;
    }

    for (int i = idx; i < tileset_count - 1; i++) {
        tileset_slots[i] = tileset_slots[i + 1];
    }
    tileset_slots[tileset_count - 1] = TilesetSlot.init;
    tileset_count--;

    if (active_tileset_index >= tileset_count) {
        active_tileset_index = tileset_count - 1;
    }
    if (active_tileset_index < 0) {
        active_tileset_index = 0;
    }
    selected_tileset_tile = 0;

    tilemap_remap_after_tileset_removed(idx);
    return true;
}

const(char)* tileset_get_path_at(int ts) {
    if (ts < 0 || ts >= tileset_count) {
        return "";
    }
    return tileset_slots[ts].path.ptr;
}

bool tileset_set_path_and_reload_at(int ts, const(char)* relPath, bool autoCalcCount = false) {
    if (ts < 0 || ts >= tileset_count) {
        return false;
    }
    if (relPath is null) {
        tileset_slots[ts].path[0] = '\0';
        unload_slot_texture(ts);
        return false;
    }
    strncpy(tileset_slots[ts].path.ptr, relPath, tileset_slots[ts].path.length - 1);
    tileset_slots[ts].path[tileset_slots[ts].path.length - 1] = '\0';
    return tileset_reload_texture_at(ts, autoCalcCount);
}

int tileset_get_tile_width_at(int ts) {
    if (ts < 0 || ts >= tileset_count) return 32;
    return tileset_slots[ts].tileWidthPx;
}

int tileset_get_tile_height_at(int ts) {
    if (ts < 0 || ts >= tileset_count) return 32;
    return tileset_slots[ts].tileHeightPx;
}

void tileset_set_tile_size_at(int ts, int w, int h) {
    if (ts < 0 || ts >= tileset_count) return;
    tileset_slots[ts].tileWidthPx = (w > 0) ? w : 32;
    tileset_slots[ts].tileHeightPx = (h > 0) ? h : 32;
}

int tileset_get_tile_count_at(int ts) {
    if (ts < 0 || ts >= tileset_count) return 0;
    return tileset_slots[ts].tileCount;
}

void tileset_set_tile_count_at(int ts, int count) {
    if (ts < 0 || ts >= tileset_count) return;
    if (count < 1) count = 1;
    if (count > MAX_TILESET_TILES) count = MAX_TILESET_TILES;
    tileset_slots[ts].tileCount = count;
}

bool tileset_is_tile_active_at(int ts, int tileId) {
    if (ts < 0 || ts >= tileset_count) return false;
    TilesetSlot* slot = &tileset_slots[ts];
    if (tileId < 0 || tileId >= slot.tileCount || tileId >= MAX_TILESET_TILES) {
        return false;
    }
    return slot.tileActive[tileId];
}

void tileset_set_tile_active_at(int ts, int tileId, bool active) {
    if (ts < 0 || ts >= tileset_count) return;
    if (tileId >= 0 && tileId < MAX_TILESET_TILES) {
        tileset_slots[ts].tileActive[tileId] = active;
    }
}

bool tileset_reload_texture_at(int ts, bool autoCalcCount = false) {
    if (ts < 0 || ts >= tileset_count) {
        return false;
    }
    unload_slot_texture(ts);

    TilesetSlot* slot = &tileset_slots[ts];
    if (slot.path[0] == '\0') {
        return false;
    }

    if (slot.path[0] == '/') {
        char[256] relBuf;
        scene_to_relative_resource_path(slot.path.ptr, relBuf.ptr, relBuf.length);
        strncpy(slot.path.ptr, relBuf.ptr, slot.path.length - 1);
        slot.path[slot.path.length - 1] = '\0';
    }

    char[1024] fullPath;
    scene_resolve_resource_path(slot.path.ptr, fullPath.ptr, fullPath.length);
    if (!FileExists(fullPath.ptr)) {
        return false;
    }

    slot.texture = LoadTexture(fullPath.ptr);
    slot.hasTexture = (slot.texture.id != 0);

    if (slot.hasTexture && autoCalcCount && slot.tileWidthPx > 0 && slot.tileHeightPx > 0) {
        int cols = slot.texture.width / slot.tileWidthPx;
        int rows = slot.texture.height / slot.tileHeightPx;
        int total = cols * rows;
        if (total > 0 && total <= MAX_TILESET_TILES) {
            slot.tileCount = total;
        }
    }

    return slot.hasTexture;
}

void tileset_reload_all_textures(bool autoCalcCount = false) {
    for (int i = 0; i < tileset_count; i++) {
        tileset_reload_texture_at(i, autoCalcCount);
    }
}

bool tileset_is_packed_tile_active(int packed) {
    if (packed < 0) return false;
    return tileset_is_tile_active_at(tile_unpack_tileset(packed), tile_unpack_index(packed));
}

void tileset_draw_packed_tile(int packed, Rectangle destRect, ubyte alpha = 255) {
    if (packed < 0) return;
    tileset_draw_tile_at(tile_unpack_tileset(packed), tile_unpack_index(packed), destRect, alpha);
}

void tileset_draw_tile_at(int ts, int tileId, Rectangle destRect, ubyte alpha = 255) {
    if (ts >= 0 && ts < tileset_count) {
        TilesetSlot* slot = &tileset_slots[ts];
        if (slot.hasTexture && slot.texture.id != 0 && slot.tileWidthPx > 0 && slot.tileHeightPx > 0) {
            int texCols = slot.texture.width / slot.tileWidthPx;
            if (texCols < 1) texCols = 1;

            int col = tileId % texCols;
            int row = tileId / texCols;

            Rectangle srcRect = Rectangle(
                cast(float)(col * slot.tileWidthPx),
                cast(float)(row * slot.tileHeightPx),
                cast(float) slot.tileWidthPx,
                cast(float) slot.tileHeightPx
            );

            DrawTexturePro(
                slot.texture,
                srcRect,
                destRect,
                Vector2(0, 0),
                0.0f,
                Color(255, 255, 255, alpha)
            );
            return;
        }
    }

    Color fallbackCol = Color(
        cast(ubyte)(50 + ((tileId + ts * 17) * 37) % 140),
        cast(ubyte)(70 + ((tileId + ts * 11) * 53) % 140),
        cast(ubyte)(100 + ((tileId + ts * 23) * 29) % 130),
        alpha
    );
    DrawRectangleRec(destRect, fallbackCol);
}

// -----------------------------------------------------------------------------
// Compatibilidad con API de un solo tileset (opera sobre active_tileset_index)
// -----------------------------------------------------------------------------

int tileset_get_tile_count() {
    return tileset_get_tile_count_at(active_tileset_index);
}

void tileset_set_tile_count(int count) {
    tileset_set_tile_count_at(active_tileset_index, count);
}

int tileset_get_tile_width_px() {
    return tileset_get_tile_width_at(active_tileset_index);
}

int tileset_get_tile_height_px() {
    return tileset_get_tile_height_at(active_tileset_index);
}

void tileset_set_tile_size_px(int w, int h) {
    tileset_set_tile_size_at(active_tileset_index, w, h);
}

bool tileset_is_tile_active(int tileId) {
    return tileset_is_tile_active_at(active_tileset_index, tileId);
}

void tileset_set_tile_active(int tileId, bool active) {
    tileset_set_tile_active_at(active_tileset_index, tileId, active);
}

const(char)* tileset_get_path() {
    return tileset_get_path_at(active_tileset_index);
}

bool tileset_set_path_and_reload(const(char)* relPath, bool autoCalcCount = false) {
    return tileset_set_path_and_reload_at(active_tileset_index, relPath, autoCalcCount);
}

bool tileset_reload_texture(bool autoCalcCount = false) {
    return tileset_reload_texture_at(active_tileset_index, autoCalcCount);
}

void tileset_draw_tile(int tileId, Rectangle destRect, ubyte alpha = 255) {
    tileset_draw_tile_at(active_tileset_index, tileId, destRect, alpha);
}

/**
 * Actualiza la lógica del Tileset Editor (incluyendo Ctrl+Z independiente).
 */
void tileset_tab_update(float dt) {
    bool ctrlDown = IsKeyDown(KeyboardKey.KEY_LEFT_CONTROL) || IsKeyDown(KeyboardKey.KEY_RIGHT_CONTROL);
    if (ctrlDown && IsKeyPressed(KeyboardKey.KEY_Z)) {
        tileset_tab_undo();
    }

    if (tileset_count <= 0) {
        tileset_add_slot("", 32, 32, 32, false);
    }
    if (active_tileset_index < 0) active_tileset_index = 0;
    if (active_tileset_index >= tileset_count) active_tileset_index = tileset_count - 1;

    TilesetSlot* slot = &tileset_slots[active_tileset_index];
    if (slot.tileCount < 1) slot.tileCount = 1;
    if (slot.tileCount > MAX_TILESET_TILES) slot.tileCount = MAX_TILESET_TILES;
    if (slot.tileWidthPx < 1) slot.tileWidthPx = 1;
    if (slot.tileHeightPx < 1) slot.tileHeightPx = 1;
    if (selected_tileset_tile >= slot.tileCount) selected_tileset_tile = slot.tileCount - 1;
    if (selected_tileset_tile < 0) selected_tileset_tile = 0;
}

/**
 * Dibuja la interfaz del Tileset Editor:
 * - Área principal izquierda: Previsualización de los tiles del PNG del tileset activo.
 * - Panel lateral derecho: Selector multi-tileset, PNG con tinyfd, tamaño en píxeles, cantidad y estado activo.
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

enum int MAX_TILESET_UNDO     = 32;
enum int FIELD_TILESET_PATH   = 1000;
enum int FIELD_TILESET_COUNT  = 1001;
enum int FIELD_TILESET_TILE_W = 1002;
enum int FIELD_TILESET_TILE_H = 1003;

struct TilesetSnapshot {
    int slotIndex;
    int tileWidthPx;
    int tileHeightPx;
    int tileCount;
    bool[MAX_TILESET_TILES] tileActive;
}

__gshared TilesetSlot[MAX_TILESETS] tileset_slots;
__gshared int tileset_count = 0;
__gshared int active_tileset_index = 0;
__gshared int selected_tileset_tile = 0;

__gshared TilesetSnapshot[MAX_TILESET_UNDO] undo_stack;
__gshared int undo_count = 0;

__gshared Vector2 tileset_scroll = Vector2(0, 0);
__gshared Rectangle tileset_view = Rectangle(0, 0, 0, 0);

void push_tileset_undo(int ts) {
    if (ts < 0 || ts >= tileset_count) return;

    if (undo_count >= MAX_TILESET_UNDO) {
        for (int i = 1; i < MAX_TILESET_UNDO; i++) {
            undo_stack[i - 1] = undo_stack[i];
        }
        undo_count = MAX_TILESET_UNDO - 1;
    }

    TilesetSlot* slot = &tileset_slots[ts];
    TilesetSnapshot* snap = &undo_stack[undo_count++];
    snap.slotIndex = ts;
    snap.tileWidthPx = slot.tileWidthPx;
    snap.tileHeightPx = slot.tileHeightPx;
    snap.tileCount = slot.tileCount;
    for (int i = 0; i < MAX_TILESET_TILES; i++) {
        snap.tileActive[i] = slot.tileActive[i];
    }
}

void unload_slot_texture(int ts) {
    if (ts < 0 || ts >= MAX_TILESETS) return;
    if (tileset_slots[ts].hasTexture && tileset_slots[ts].texture.id != 0) {
        UnloadTexture(tileset_slots[ts].texture);
    }
    tileset_slots[ts].texture = Texture2D.init;
    tileset_slots[ts].hasTexture = false;
}

void draw_tileset_side_panel(Rectangle panelBounds) {
    GuiPanel(panelBounds, "Tileset - Propiedades (Ctrl+Z: Deshacer)");

    if (tileset_count <= 0) {
        tileset_add_slot("", 32, 32, 32, false);
    }
    if (active_tileset_index < 0 || active_tileset_index >= tileset_count) {
        active_tileset_index = 0;
    }

    TilesetSlot* slot = &tileset_slots[active_tileset_index];

    float x = panelBounds.x + 16.0f;
    float y = panelBounds.y + 38.0f;
    float w = panelBounds.width - 32.0f;
    float halfW = (w - 6.0f) * 0.5f;

    // 0. Selector Multi-Tileset
    gui_section_header(x, y, w, "Tilesets del Mapa");
    float navBtnW = 36.0f;
    if (GuiButton(Rectangle(x, y, navBtnW, 24.0f), "<")) {
        gui_clear_active_field();
        active_tileset_index = (active_tileset_index - 1 + tileset_count) % tileset_count;
        selected_tileset_tile = 0;
        slot = &tileset_slots[active_tileset_index];
    }

    char[64] slotLbl;
    snprintf(slotLbl.ptr, slotLbl.length, "Tileset #%d / %d (id=%d)", active_tileset_index + 1, tileset_count, active_tileset_index);
    GuiLabel(Rectangle(x + navBtnW + 8.0f, y, w - navBtnW * 2.0f - 16.0f, 24.0f), slotLbl.ptr);

    if (GuiButton(Rectangle(x + w - navBtnW, y, navBtnW, 24.0f), ">")) {
        gui_clear_active_field();
        active_tileset_index = (active_tileset_index + 1) % tileset_count;
        selected_tileset_tile = 0;
        slot = &tileset_slots[active_tileset_index];
    }
    y += 28.0f;

    if (GuiButton(Rectangle(x, y, halfW, 24.0f), GuiIconText(GuiIconName.ICON_FILE_ADD, "+ Agregar Tileset"))) {
        gui_clear_active_field();
        int newIdx = tileset_add_slot("", 32, 32, 32, false);
        if (newIdx >= 0) {
            active_tileset_index = newIdx;
            selected_tileset_tile = 0;
            slot = &tileset_slots[active_tileset_index];
        }
    }
    if (GuiButton(Rectangle(x + halfW + 6.0f, y, halfW, 24.0f), GuiIconText(GuiIconName.ICON_BIN, "Quitar"))) {
        gui_clear_active_field();
        tileset_remove_slot(active_tileset_index);
        slot = &tileset_slots[active_tileset_index];
    }
    y += 30.0f;

    // 1. Archivo PNG del Tileset (modo relativo con tinyfd)
    gui_section_header(x, y, w, "Imagen del Tileset (.png)");
    GuiLabel(Rectangle(x, y, w, 20.0f), "Resource Path (Relativo)");
    y += 22.0f;

    float btnW = 92.0f;
    if (gui_search_box(Rectangle(x, y, w - btnW - 6.0f, 24.0f), slot.path.ptr, cast(int) slot.path.length, FIELD_TILESET_PATH)) {
        tileset_reload_texture_at(active_tileset_index, true);
    }

    if (GuiButton(Rectangle(x + w - btnW, y, btnW, 24.0f), GuiIconText(GuiIconName.ICON_FOLDER_OPEN, "PNG..."))) {
        char[512] selectedAbs;
        if (open_png_file_dialog(selectedAbs.ptr, selectedAbs.length, "Seleccionar PNG del Tileset")) {
            push_tileset_undo(active_tileset_index);
            scene_to_relative_resource_path(selectedAbs.ptr, slot.path.ptr, slot.path.length);
            tileset_reload_texture_at(active_tileset_index, true);
        }
    }
    y += 30.0f;

    if (slot.hasTexture) {
        char[96] texStatus;
        snprintf(texStatus.ptr, texStatus.length, "PNG cargado: %dx%d px", slot.texture.width, slot.texture.height);
        GuiLabel(Rectangle(x, y, w, 20.0f), texStatus.ptr);
    } else {
        GuiLabel(Rectangle(x, y, w, 20.0f), "Sin PNG cargado (usando vista previa)");
    }
    y += 26.0f;

    // 2. Tamaño en píxeles de cada tile y cantidad de tiles
    gui_section_header(x, y, w, "Dimensiones de Tiles");
    int prevW = slot.tileWidthPx;
    int prevH = slot.tileHeightPx;
    int prevCount = slot.tileCount;

    gui_labeled_int_spinner(x, y, w, "Tamano X (px)", &slot.tileWidthPx, 1, 512, FIELD_TILESET_TILE_W);
    gui_labeled_int_spinner(x, y, w, "Tamano Y (px)", &slot.tileHeightPx, 1, 512, FIELD_TILESET_TILE_H);
    gui_labeled_int_spinner(x, y, w, "Cantidad Tiles", &slot.tileCount, 1, MAX_TILESET_TILES, FIELD_TILESET_COUNT);

    if (slot.tileWidthPx != prevW || slot.tileHeightPx != prevH || slot.tileCount != prevCount) {
        int curW = slot.tileWidthPx;
        int curH = slot.tileHeightPx;
        int curC = slot.tileCount;
        slot.tileWidthPx = prevW;
        slot.tileHeightPx = prevH;
        slot.tileCount = prevCount;
        push_tileset_undo(active_tileset_index);
        slot.tileWidthPx = curW;
        slot.tileHeightPx = curH;
        slot.tileCount = curC;
    }

    if (slot.hasTexture) {
        if (GuiButton(Rectangle(x, y, w, 24.0f), "Ajustar Cantidad segun PNG")) {
            push_tileset_undo(active_tileset_index);
            int cols = slot.texture.width / slot.tileWidthPx;
            int rows = slot.texture.height / slot.tileHeightPx;
            int total = cols * rows;
            if (total >= 1 && total <= MAX_TILESET_TILES) {
                slot.tileCount = total;
            }
        }
        y += 30.0f;
    }

    // 3. Activar / Desactivar tiles del Tileset
    y += 6.0f;
    gui_section_header(x, y, w, "Activacion de Tiles");

    char[64] selText;
    snprintf(selText.ptr, selText.length, "Tile Seleccionado: T%d#%d", active_tileset_index, selected_tileset_tile);
    GuiLabel(Rectangle(x, y, w, 22.0f), selText.ptr);
    y += 26.0f;

    if (selected_tileset_tile >= 0 && selected_tileset_tile < slot.tileCount) {
        bool prevAct = slot.tileActive[selected_tileset_tile];
        gui_labeled_checkbox(x, y, "Tile Activo (Visible en Tilemap)", &slot.tileActive[selected_tileset_tile]);
        if (slot.tileActive[selected_tileset_tile] != prevAct) {
            bool newAct = slot.tileActive[selected_tileset_tile];
            slot.tileActive[selected_tileset_tile] = prevAct;
            push_tileset_undo(active_tileset_index);
            slot.tileActive[selected_tileset_tile] = newAct;
        }
    }

    if (GuiButton(Rectangle(x, y, halfW, 26.0f), "Activar Todos")) {
        push_tileset_undo(active_tileset_index);
        for (int i = 0; i < slot.tileCount; i++) {
            slot.tileActive[i] = true;
        }
    }
    if (GuiButton(Rectangle(x + halfW + 6.0f, y, halfW, 26.0f), "Desactivar Todos")) {
        push_tileset_undo(active_tileset_index);
        for (int i = 0; i < slot.tileCount; i++) {
            slot.tileActive[i] = false;
        }
    }
    y += 34.0f;

    if (GuiButton(Rectangle(x, y, w, 26.0f), GuiIconText(GuiIconName.ICON_UNDO, "Deshacer (Ctrl+Z)"))) {
        tileset_tab_undo();
    }
    y += 34.0f;

    int activeCount = 0;
    for (int i = 0; i < slot.tileCount; i++) {
        if (slot.tileActive[i]) activeCount++;
    }

    char[80] summaryBuf;
    snprintf(summaryBuf.ptr, summaryBuf.length, "Tiles habilitados: %d / %d", activeCount, slot.tileCount);
    GuiLabel(Rectangle(x, y, w, 22.0f), summaryBuf.ptr);
}

void draw_tileset_preview(Rectangle areaBounds) {
    if (tileset_count <= 0) return;
    TilesetSlot* slot = &tileset_slots[active_tileset_index];

    int cols = 8;
    if (slot.hasTexture && slot.tileWidthPx > 0) {
        int pngCols = slot.texture.width / slot.tileWidthPx;
        if (pngCols >= 1 && pngCols <= 16) {
            cols = pngCols;
        }
    }

    int rows = (slot.tileCount + cols - 1) / cols;
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

    for (int i = 0; i < slot.tileCount; i++) {
        int r = i / cols;
        int c = i % cols;

        Rectangle tileRect = Rectangle(
            startX + c * (cellSize + padding),
            startY + r * (cellSize + padding),
            cellSize,
            cellSize
        );

        bool isActive = slot.tileActive[i];
        bool isSelected = (selected_tileset_tile == i);

        tileset_draw_tile_at(active_tileset_index, i, tileRect, isActive ? 255 : 75);

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
                    push_tileset_undo(active_tileset_index);
                    slot.tileActive[i] = !slot.tileActive[i];
                } else {
                    selected_tileset_tile = i;
                }
            } else if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_RIGHT)) {
                push_tileset_undo(active_tileset_index);
                selected_tileset_tile = i;
                slot.tileActive[i] = !slot.tileActive[i];
            }
        }
    }

    EndScissorMode();
}
