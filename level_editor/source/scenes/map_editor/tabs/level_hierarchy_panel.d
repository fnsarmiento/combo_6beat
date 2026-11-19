module scenes.map_editor.tabs.level_hierarchy_panel;

nothrow:
@nogc:

import raylib;
import raygui;
import core.stdc.stdio : snprintf;
import core.stdc.string : strcmp, strncpy;
import scenes.map_editor.level_data;
import scenes.map_editor.gui_widgets;
import scenes.map_editor.tabs.level_editor_tab : level_editor_get_camera_center;
import scenes.map_editor.tabs.prefabs_tab :
    MAX_PREFABS,
    prefabs_get_count,
    prefabs_is_used,
    prefabs_get_name,
    prefabs_get_xml_tag,
    prefabs_get_active_index,
    prefabs_set_active_index,
    prefabs_instantiate_into_world,
    prefabs_create_from_world_object;

public:

/**
 * Inicializa el estado del panel izquierdo (jerarquía de objetos).
 */
void hierarchy_panel_init() {
    search_query[0] = '\0';
    scroll_offset = Vector2(0, 0);
    new_object_type = cast(int) ObjectType.Box;
}

/**
 * Dibuja el panel izquierdo del Level Editor:
 * - Botón '+' arriba (estilo Godot) junto al selector de tipo para agregar objetos.
 * - Botones para mover el elemento seleccionado hacia arriba / abajo respetando grupos y structs.
 * - Barra buscadora por ID.
 * - Lista scrolleable agrupada por 'group' (si no es vacío ni "default"), mientras que los
 *   miembros internos de un Struct ignoran los grupos de arriba y se indentan bajo su Struct.
 */
void hierarchy_panel_draw(Rectangle bounds, ref LevelWorld world) {
    GuiPanel(bounds, "Objetos (Jerarquia)");

    float pad = 10.0f;
    float x = bounds.x + pad;
    float y = bounds.y + 32.0f;
    float w = bounds.width - pad * 2.0f;

    draw_top_add_bar(x, y, w, world);
    draw_search_bar(x, y, w);
    draw_object_tree_list(x, y, w, (bounds.y + bounds.height) - y - pad, world);
}

private:

enum int FIELD_SEARCH_BOX = 3001;
enum int MAX_GROUPS       = 64;
enum float ITEM_HEIGHT    = 28.0f;
enum float GROUP_HEIGHT   = 24.0f;
enum float ITEM_SPACING   = 4.0f;
enum float INDENT_STEP    = 18.0f;

__gshared char[64] search_query = 0;
__gshared Vector2 scroll_offset = Vector2(0, 0);
__gshared Rectangle scroll_view = Rectangle(0, 0, 0, 0);
__gshared int new_object_type = 0;

__gshared int pending_delete_uid = -1;
__gshared int pending_move_uid   = -1;
__gshared int pending_move_dir   = 0;

void draw_top_add_bar(float x, ref float y, float w, ref LevelWorld world) {
    // Botón '+' al estilo Godot para agregar un nuevo objeto en la raíz
    float addBtnW = 34.0f;
    float moveBtnW = 28.0f;
    if (GuiButton(Rectangle(x, y, addBtnW, 26), GuiIconText(GuiIconName.ICON_BOX_MORE, "+"))) {
        level_world_push_undo(world);
        level_world_add_object(world, cast(ObjectType) new_object_type, -1);
    }

    // Selector rápido de tipo a crear (Box, Sprite, Struct)
    float toggleAreaW = w - addBtnW - (moveBtnW * 2.0f) - 14.0f;
    Rectangle typeToggleBounds = Rectangle(
        x + addBtnW + 4.0f,
        y,
        toggleAreaW / 3.0f - 2.0f,
        26.0f
    );
    GuiToggleGroup(typeToggleBounds, "Box;Sprite;Struct", &new_object_type);

    // Botones arriba para subir/bajar el objeto seleccionado respetando su grupo/struct
    float moveUpX = x + w - (moveBtnW * 2.0f) - 2.0f;
    if (GuiButton(Rectangle(moveUpX, y, moveBtnW, 26), GuiIconText(GuiIconName.ICON_ARROW_UP_FILL, null))) {
        if (world.selectedUid >= 0) {
            level_world_push_undo(world);
            level_world_move_object(world, world.selectedUid, -1);
        }
    }
    if (GuiButton(Rectangle(moveUpX + moveBtnW + 2.0f, y, moveBtnW, 26), GuiIconText(GuiIconName.ICON_ARROW_DOWN_FILL, null))) {
        if (world.selectedUid >= 0) {
            level_world_push_undo(world);
            level_world_move_object(world, world.selectedUid, 1);
        }
    }

    y += 32.0f;

    // Presets rápidos para entidades del juego (Player, Enemy, NPC, Chest, SceneChanger)
    Vector2 spawnCenter = level_editor_get_camera_center();
    float presetW = (w - 16.0f) / 5.0f;
    if (GuiButton(Rectangle(x, y, presetW, 24.0f), "+ Player")) {
        level_world_push_undo(world);
        level_world_add_player_preset(world, spawnCenter);
    }
    if (GuiButton(Rectangle(x + (presetW + 4.0f) * 1.0f, y, presetW, 24.0f), "+ Enemigo")) {
        level_world_push_undo(world);
        level_world_add_enemy_preset(world, spawnCenter);
    }
    if (GuiButton(Rectangle(x + (presetW + 4.0f) * 2.0f, y, presetW, 24.0f), "+ NPC")) {
        level_world_push_undo(world);
        level_world_add_npc_preset(world, spawnCenter);
    }
    if (GuiButton(Rectangle(x + (presetW + 4.0f) * 3.0f, y, presetW, 24.0f), "+ Cofre")) {
        level_world_push_undo(world);
        level_world_add_chest_preset(world, spawnCenter);
    }
    if (GuiButton(Rectangle(x + (presetW + 4.0f) * 4.0f, y, presetW, 24.0f), "+ Portal")) {
        level_world_push_undo(world);
        level_world_add_scene_changer_preset(world, spawnCenter);
    }
    y += 28.0f;

    // Selector e instanciador de Prefabs definidos en la pestaña 'Prefabs' (.procshit)
    int pfActive = prefabs_get_active_index();
    float navW = 26.0f;
    float midW = w - navW * 2.0f - 8.0f;
    if (GuiButton(Rectangle(x, y, navW, 24.0f), "<")) {
        int cur = pfActive;
        for (int step = 1; step < MAX_PREFABS; step++) {
            int cand = (cur - step + MAX_PREFABS) % MAX_PREFABS;
            if (prefabs_is_used(cand)) {
                prefabs_set_active_index(cand);
                break;
            }
        }
    }

    char[128] pfLabel;
    if (prefabs_is_used(pfActive)) {
        snprintf(pfLabel.ptr, pfLabel.length, "Prefab: [%s] %s", prefabs_get_xml_tag(pfActive), prefabs_get_name(pfActive));
    } else {
        snprintf(pfLabel.ptr, pfLabel.length, "(Sin Prefabs en .procshit)");
    }
    DrawRectangleRec(Rectangle(x + navW + 4.0f, y, midW, 24.0f), Color(32, 38, 48, 255));
    GuiLabel(Rectangle(x + navW + 10.0f, y, midW - 12.0f, 24.0f), pfLabel.ptr);

    if (GuiButton(Rectangle(x + w - navW, y, navW, 24.0f), ">")) {
        int cur = pfActive;
        for (int step = 1; step < MAX_PREFABS; step++) {
            int cand = (cur + step) % MAX_PREFABS;
            if (prefabs_is_used(cand)) {
                prefabs_set_active_index(cand);
                break;
            }
        }
    }
    y += 26.0f;

    float objBtnW = (w - 4.0f) * 0.5f;
    if (GuiButton(Rectangle(x, y, objBtnW, 24.0f), GuiIconText(GuiIconName.ICON_FILE_ADD, "+ Instanciar Prefab"))) {
        if (prefabs_is_used(pfActive)) {
            gui_clear_active_field();
            prefabs_instantiate_into_world(pfActive, world, spawnCenter);
        }
    }
    if (GuiButton(Rectangle(x + objBtnW + 4.0f, y, objBtnW, 24.0f), GuiIconText(GuiIconName.ICON_FILE_SAVE, "Guardar a Prefabs"))) {
        if (world.selectedUid >= 0) {
            gui_clear_active_field();
            prefabs_create_from_world_object(world, world.selectedUid);
        }
    }
    y += 28.0f;

    // Si el objeto seleccionado actualmente es un Struct, ofrecemos botón rápido para agregar hijo adentro
    LevelObject* sel = level_world_find(world, world.selectedUid);
    if (sel !is null && sel.type == ObjectType.Struct) {
        char[96] childBtnText;
        snprintf(
            childBtnText.ptr,
            childBtnText.length,
            "+ Agregar %s dentro de '%s'",
            level_object_type_label(cast(ObjectType) new_object_type),
            sel.id.ptr
        );
        if (GuiButton(Rectangle(x, y, w, 24), childBtnText.ptr)) {
            level_world_push_undo(world);
            sel.expanded = true;
            level_world_add_object(world, cast(ObjectType) new_object_type, sel.uid);
        }
        y += 28.0f;
    }
}

void draw_search_bar(float x, ref float y, float w) {
    GuiLabel(Rectangle(x, y, 22, 24), GuiIconText(GuiIconName.ICON_LENS, null));

    float clearBtnW = 26.0f;
    Rectangle searchBounds = Rectangle(x + 24.0f, y, w - 24.0f - clearBtnW - 4.0f, 24.0f);
    gui_search_box(searchBounds, search_query.ptr, cast(int) search_query.length, FIELD_SEARCH_BOX);

    if (GuiButton(Rectangle(x + w - clearBtnW, y, clearBtnW, 24.0f), GuiIconText(GuiIconName.ICON_CROSS_SMALL, null))) {
        search_query[0] = '\0';
    }

    y += 30.0f;
}

int collect_unique_root_groups(ref LevelWorld world, ref char[64][MAX_GROUPS] outGroups) {
    int groupCount = 0;
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].parentUid != -1) {
            continue;
        }
        if (level_group_is_default_or_empty(world.objects[i].group.ptr)) {
            continue;
        }
        if (!level_object_or_children_match(world, world.objects[i], search_query.ptr)) {
            continue;
        }

        bool exists = false;
        for (int g = 0; g < groupCount; g++) {
            if (strcmp(outGroups[g].ptr, world.objects[i].group.ptr) == 0) {
                exists = true;
                break;
            }
        }

        if (!exists && groupCount < MAX_GROUPS) {
            strncpy(outGroups[groupCount].ptr, world.objects[i].group.ptr, 63);
            outGroups[groupCount][63] = '\0';
            groupCount++;
        }
    }
    return groupCount;
}

void draw_object_tree_list(float x, float y, float w, float h, ref LevelWorld world) {
    char[64][MAX_GROUPS] rootGroups;
    int groupCount = collect_unique_root_groups(world, rootGroups);

    int visibleRows = count_all_visible_rows(world);
    float contentHeight = visibleRows * (ITEM_HEIGHT + ITEM_SPACING)
                        + groupCount * (GROUP_HEIGHT + ITEM_SPACING)
                        + 16.0f;
    if (contentHeight < h - 16.0f) {
        contentHeight = h - 16.0f;
    }

    Rectangle listBounds = Rectangle(x, y, w, h);
    Rectangle contentBounds = Rectangle(0, 0, w - 18.0f, contentHeight);
    GuiScrollPanel(listBounds, null, contentBounds, &scroll_offset, &scroll_view);

    pending_delete_uid = -1;
    pending_move_uid   = -1;
    pending_move_dir   = 0;

    BeginScissorMode(
        cast(int) scroll_view.x,
        cast(int) scroll_view.y,
        cast(int) scroll_view.width,
        cast(int) scroll_view.height
    );

    float baseX = scroll_view.x + 6.0f + scroll_offset.x;
    float curY  = scroll_view.y + 6.0f + scroll_offset.y;
    float rowW  = scroll_view.width - 12.0f;

    // 1. Dibujar primero los objetos raíz que pertenecen a un grupo con nombre (no vacío ni "default")
    for (int g = 0; g < groupCount; g++) {
        draw_group_header(rootGroups[g].ptr, baseX, curY, rowW);
        curY += GROUP_HEIGHT + ITEM_SPACING;

        for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
            if (!world.objects[i].used || world.objects[i].parentUid != -1) {
                continue;
            }
            if (level_group_is_default_or_empty(world.objects[i].group.ptr)) {
                continue;
            }
            if (strcmp(world.objects[i].group.ptr, rootGroups[g].ptr) != 0) {
                continue;
            }
            if (!level_object_or_children_match(world, world.objects[i], search_query.ptr)) {
                continue;
            }

            LevelObject* obj = &world.objects[i];
            draw_single_object_row(world, obj, 1, baseX, curY, rowW);
            curY += ITEM_HEIGHT + ITEM_SPACING;

            // Si es un Struct, sus miembros internos ignoran los grupos de arriba y se indentan aquí
            if (obj.type == ObjectType.Struct && obj.expanded) {
                draw_struct_children_recursive(world, obj.uid, 2, baseX, curY, rowW);
            }
        }
    }

    // 2. Dibujar los objetos raíz cuyo grupo está vacío o es "default" ("como si nada")
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].parentUid != -1) {
            continue;
        }
        if (!level_group_is_default_or_empty(world.objects[i].group.ptr)) {
            continue;
        }
        if (!level_object_or_children_match(world, world.objects[i], search_query.ptr)) {
            continue;
        }

        LevelObject* obj = &world.objects[i];
        draw_single_object_row(world, obj, 0, baseX, curY, rowW);
        curY += ITEM_HEIGHT + ITEM_SPACING;

        if (obj.type == ObjectType.Struct && obj.expanded) {
            draw_struct_children_recursive(world, obj.uid, 1, baseX, curY, rowW);
        }
    }

    EndScissorMode();

    if (pending_move_uid >= 0 && pending_move_dir != 0) {
        level_world_push_undo(world);
        level_world_move_object(world, pending_move_uid, pending_move_dir);
        pending_move_uid = -1;
        pending_move_dir = 0;
    }

    if (pending_delete_uid >= 0) {
        gui_clear_active_field();
        level_world_push_undo(world);
        level_world_remove_object(world, pending_delete_uid);
        pending_delete_uid = -1;
    }
}

void draw_group_header(const(char)* groupName, float x, float y, float w) {
    Rectangle headerRect = Rectangle(x, y, w, GROUP_HEIGHT);
    DrawRectangleRec(headerRect, Color(42, 50, 64, 255));
    DrawRectangleLinesEx(headerRect, 1.0f, Color(75, 92, 118, 255));

    char[96] label;
    snprintf(label.ptr, label.length, "Grupo: %s", groupName);
    GuiLabel(Rectangle(x + 6.0f, y, w - 12.0f, GROUP_HEIGHT), GuiIconText(GuiIconName.ICON_FOLDER, label.ptr));
}

int count_all_visible_rows(ref LevelWorld world) {
    int total = 0;
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].parentUid != -1) {
            continue;
        }
        if (!level_object_or_children_match(world, world.objects[i], search_query.ptr)) {
            continue;
        }
        total++;
        if (world.objects[i].type == ObjectType.Struct && world.objects[i].expanded) {
            total += count_struct_children_rows(world, world.objects[i].uid);
        }
    }
    return total;
}

int count_struct_children_rows(ref LevelWorld world, int parentUid) {
    int total = 0;
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].parentUid != parentUid) {
            continue;
        }
        if (!level_object_or_children_match(world, world.objects[i], search_query.ptr)) {
            continue;
        }
        total++;
        if (world.objects[i].type == ObjectType.Struct && world.objects[i].expanded) {
            total += count_struct_children_rows(world, world.objects[i].uid);
        }
    }
    return total;
}

void draw_struct_children_recursive(
    ref LevelWorld world,
    int parentUid,
    int depth,
    float baseX,
    ref float curY,
    float rowWidth
) {
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].parentUid != parentUid) {
            continue;
        }

        LevelObject* obj = &world.objects[i];
        if (!level_object_or_children_match(world, *obj, search_query.ptr)) {
            continue;
        }

        draw_single_object_row(world, obj, depth, baseX, curY, rowWidth);
        curY += ITEM_HEIGHT + ITEM_SPACING;

        if (obj.type == ObjectType.Struct && obj.expanded) {
            draw_struct_children_recursive(world, obj.uid, depth + 1, baseX, curY, rowWidth);
        }
    }
}

void draw_single_object_row(
    ref LevelWorld world,
    LevelObject* obj,
    int depth,
    float baseX,
    float rowY,
    float rowWidth
) {
    float indent = depth * INDENT_STEP;
    float itemX = baseX + indent;
    float itemW = rowWidth - indent;
    if (itemW < 100.0f) {
        itemW = 100.0f;
    }

    // Guías visuales de indentación
    if (depth > 0) {
        int lineX = cast(int)(baseX + (depth - 1) * INDENT_STEP + 8.0f);
        int midY = cast(int)(rowY + ITEM_HEIGHT * 0.5f);
        DrawLine(lineX, cast(int) rowY, lineX, midY, Color(110, 120, 135, 255));
        DrawLine(lineX, midY, cast(int)(itemX - 2.0f), midY, Color(110, 120, 135, 255));
    }

    float trashW    = 24.0f;
    float moveW     = 20.0f;
    float addChildW = (obj.type == ObjectType.Struct) ? 22.0f : 0.0f;
    float expandW   = (obj.type == ObjectType.Struct) ? 20.0f : 0.0f;

    if (obj.type == ObjectType.Struct) {
        int arrowIcon = obj.expanded ? GuiIconName.ICON_ARROW_DOWN_FILL : GuiIconName.ICON_ARROW_RIGHT_FILL;
        if (GuiButton(Rectangle(itemX, rowY, expandW, ITEM_HEIGHT), GuiIconText(arrowIcon, null))) {
            obj.expanded = !obj.expanded;
        }
    }

    float mainBtnX = itemX + (expandW > 0 ? expandW + 2.0f : 0.0f);
    float rightControlsW = trashW + (moveW * 2.0f) + 6.0f + (addChildW > 0 ? addChildW + 2.0f : 0.0f);
    float mainBtnW = itemW - (mainBtnX - itemX) - rightControlsW;
    if (mainBtnW < 40.0f) {
        mainBtnW = 40.0f;
    }

    int objIcon = get_object_icon(obj.type);
    const(char)* typeLabel = level_object_type_label(obj.type);
    if (obj.type == ObjectType.Struct && obj.xmlTag[0] != '\0') {
        typeLabel = obj.xmlTag.ptr;
    }
    char[128] rowLabel;
    snprintf(
        rowLabel.ptr,
        rowLabel.length,
        "%s [%s]",
        obj.id.ptr,
        typeLabel
    );

    bool isSelected = (world.selectedUid == obj.uid);
    bool toggled = isSelected;
    GuiToggle(Rectangle(mainBtnX, rowY, mainBtnW, ITEM_HEIGHT), GuiIconText(objIcon, rowLabel.ptr), &toggled);
    if (toggled && !isSelected) {
        gui_clear_active_field();
        world.selectedUid = obj.uid;
    }

    float btnCursorX = mainBtnX + mainBtnW + 2.0f;

    if (obj.type == ObjectType.Struct) {
        if (GuiButton(Rectangle(btnCursorX, rowY, addChildW, ITEM_HEIGHT), "+")) {
            obj.expanded = true;
            level_world_add_object(world, cast(ObjectType) new_object_type, obj.uid);
        }
        btnCursorX += addChildW + 2.0f;
    }

    // Botones subir/bajar en la fila (respetan el grupo o el Struct padre)
    if (GuiButton(Rectangle(btnCursorX, rowY, moveW, ITEM_HEIGHT), GuiIconText(GuiIconName.ICON_ARROW_UP, null))) {
        pending_move_uid = obj.uid;
        pending_move_dir = -1;
    }
    btnCursorX += moveW + 1.0f;

    if (GuiButton(Rectangle(btnCursorX, rowY, moveW, ITEM_HEIGHT), GuiIconText(GuiIconName.ICON_ARROW_DOWN, null))) {
        pending_move_uid = obj.uid;
        pending_move_dir = 1;
    }
    btnCursorX += moveW + 2.0f;

    // Botón rojo de basura al lado de cada objeto
    Rectangle trashBounds = Rectangle(baseX + rowWidth - trashW, rowY, trashW, ITEM_HEIGHT);
    if (gui_trash_button(trashBounds)) {
        pending_delete_uid = obj.uid;
    }
}

int get_object_icon(ObjectType type) {
    final switch (type) {
        case ObjectType.Box:    return GuiIconName.ICON_BOX;
        case ObjectType.Sprite: return GuiIconName.ICON_FILETYPE_IMAGE;
        case ObjectType.Struct: return GuiIconName.ICON_LAYERS;
    }
}
