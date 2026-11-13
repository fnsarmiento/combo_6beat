module scenes.map_editor.tabs.level_inspector_panel;

nothrow:
@nogc:

import raylib;
import raygui;
import core.stdc.stdio : snprintf;
import engine.tinyfd : open_png_file_dialog;
import scenes.map_editor.level_data;
import scenes.map_editor.gui_widgets;

public:

/**
 * Inicializa el estado del panel derecho (inspector de propiedades).
 */
void inspector_panel_init() {
    inspector_scroll = Vector2(0, 0);
}

/**
 * Dibuja el panel derecho del Level Editor con las propiedades del objeto seleccionado:
 * - Propiedades generales: ID, Tag XML, Type (Box / Sprite / Struct) y Group.
 * - Box: position (V2), size (V2), layer (bitmap), mask (bitmap), disabled (bool).
 * - Sprite: resourcePath (PNG relativo con tinyfd), rows, cols, position (V2), scale (V2),
 *           rotation (float), visible (bool), delay (float en segs), centerPosition (bool), zIndex (int).
 * - Struct: todo custom (elementos hijos + propiedades clave/valor dinámicas).
 */
void inspector_panel_draw(Rectangle bounds, ref LevelWorld world) {
    LevelObject* obj = level_world_find(world, world.selectedUid);
    if (obj is null) {
        GuiPanel(bounds, "Inspector de Propiedades");
        GuiLabel(
            Rectangle(bounds.x + 20.0f, bounds.y + 50.0f, bounds.width - 40.0f, 24.0f),
            "Selecciona un objeto de la lista izquierda"
        );
        return;
    }

    float contentHeight = estimate_inspector_height(*obj);
    if (contentHeight < bounds.height - 28.0f) {
        contentHeight = bounds.height - 28.0f;
    }

    Rectangle contentBounds = Rectangle(0, 0, bounds.width - 20.0f, contentHeight);
    GuiScrollPanel(bounds, "Inspector de Propiedades", contentBounds, &inspector_scroll, &inspector_view);

    BeginScissorMode(
        cast(int) inspector_view.x,
        cast(int) inspector_view.y,
        cast(int) inspector_view.width,
        cast(int) inspector_view.height
    );

    float x = inspector_view.x + 12.0f + inspector_scroll.x;
    float y = inspector_view.y + 12.0f + inspector_scroll.y;
    float w = inspector_view.width - 24.0f;

    draw_common_properties(x, y, w, *obj);

    final switch (obj.type) {
        case ObjectType.Box:
            draw_box_properties(x, y, w, obj.box);
            break;
        case ObjectType.Sprite:
            draw_sprite_properties(x, y, w, obj.sprite);
            break;
        case ObjectType.Struct:
            draw_struct_properties(x, y, w, world, *obj);
            break;
    }

    EndScissorMode();
}

private:

enum int FIELD_OBJ_ID           = 4001;
enum int FIELD_OBJ_GROUP        = 4002;
enum int FIELD_OBJ_XML_TAG      = 4003;

enum int FIELD_BOX_POS          = 4100;
enum int FIELD_BOX_SIZE         = 4102;
enum int FIELD_BOX_LAYER        = 4104;
enum int FIELD_BOX_MASK         = 4105;

enum int FIELD_SPRITE_PATH      = 4200;
enum int FIELD_SPRITE_ROWS      = 4201;
enum int FIELD_SPRITE_COLS      = 4202;
enum int FIELD_SPRITE_POS       = 4203;
enum int FIELD_SPRITE_SCALE     = 4205;
enum int FIELD_SPRITE_ROTATION  = 4207;
enum int FIELD_SPRITE_DELAY     = 4208;
enum int FIELD_SPRITE_ZINDEX    = 4209;

enum int FIELD_STRUCT_BASE      = 5000;

__gshared Vector2 inspector_scroll = Vector2(0, 0);
__gshared Rectangle inspector_view = Rectangle(0, 0, 0, 0);

float estimate_inspector_height(ref const(LevelObject) obj) {
    float baseH = 195.0f;
    final switch (obj.type) {
        case ObjectType.Box:
            return baseH + 290.0f;
        case ObjectType.Sprite:
            return baseH + 400.0f;
        case ObjectType.Struct:
            return baseH + 170.0f + obj.customStruct.propCount * 95.0f;
    }
}

void draw_common_properties(float x, ref float y, float w, ref LevelObject obj) {
    gui_section_header(x, y, w, "Objeto General");

    gui_labeled_text_box(x, y, w, "ID", obj.id.ptr, cast(int) obj.id.length, FIELD_OBJ_ID);

    // Selector de Type (Box, Sprite, Struct)
    GuiLabel(Rectangle(x, y, 95.0f, 24.0f), "Type");
    int typeInt = cast(int) obj.type;
    float toggleW = (w - 95.0f) / 3.0f - 2.0f;
    GuiToggleGroup(Rectangle(x + 95.0f, y, toggleW, 24.0f), "Box;Sprite;Struct", &typeInt);
    if (typeInt != cast(int) obj.type) {
        gui_clear_active_field();
        obj.type = cast(ObjectType) typeInt;
        snprintf(obj.xmlTag.ptr, obj.xmlTag.length, "%s", level_object_type_label(obj.type));
    }
    y += 30.0f;

    gui_labeled_text_box(x, y, w, "Grupo", obj.group.ptr, cast(int) obj.group.length, FIELD_OBJ_GROUP);
    gui_labeled_text_box(x, y, w, "Tag XML", obj.xmlTag.ptr, cast(int) obj.xmlTag.length, FIELD_OBJ_XML_TAG);
    y += 6.0f;
}

void draw_box_properties(float x, ref float y, float w, ref BoxData box) {
    gui_section_header(x, y, w, "Propiedades de Box");

    gui_labeled_vec2(x, y, w, "Position", &box.position, FIELD_BOX_POS);
    gui_labeled_vec2(x, y, w, "Size", &box.size, FIELD_BOX_SIZE);

    y += 4.0f;
    gui_labeled_bitmap(x, y, w, "Layer (Bitmap)", &box.layer, FIELD_BOX_LAYER);
    gui_labeled_bitmap(x, y, w, "Mask (Bitmap)", &box.mask, FIELD_BOX_MASK);

    gui_labeled_checkbox(x, y, "Disabled", &box.disabled);
}

void draw_sprite_properties(float x, ref float y, float w, ref SpriteData sprite) {
    gui_section_header(x, y, w, "Propiedades de Sprite (2D / Animado)");

    // Resource Path en modo relativo + botón de diálogo PNG (tinyfd)
    GuiLabel(Rectangle(x, y, w, 20.0f), "Resource Path (.png relativo)");
    y += 22.0f;

    float btnBrowseW = 92.0f;
    Rectangle pathBoxBounds = Rectangle(x, y, w - btnBrowseW - 6.0f, 24.0f);
    if (gui_search_box(pathBoxBounds, sprite.resourcePath.ptr, cast(int) sprite.resourcePath.length, FIELD_SPRITE_PATH)) {
        level_sprite_reload_texture(sprite);
    }

    Rectangle browseBtnBounds = Rectangle(x + w - btnBrowseW, y, btnBrowseW, 24.0f);
    if (GuiButton(browseBtnBounds, GuiIconText(GuiIconName.ICON_FOLDER_OPEN, "PNG..."))) {
        char[512] selectedAbs;
        if (open_png_file_dialog(selectedAbs.ptr, selectedAbs.length, "Seleccionar Sprite PNG")) {
            scene_to_relative_resource_path(selectedAbs.ptr, sprite.resourcePath.ptr, sprite.resourcePath.length);
            level_sprite_reload_texture(sprite);
        }
    }
    y += 30.0f;

    if (sprite.hasTexture) {
        char[96] texInfo;
        snprintf(
            texInfo.ptr,
            texInfo.length,
            "Textura cargada: %dx%d px",
            sprite.texture.width,
            sprite.texture.height
        );
        GuiLabel(Rectangle(x, y, w, 20.0f), texInfo.ptr);
        y += 24.0f;
    }

    gui_labeled_int_spinner(x, y, w, "Rows (Filas)", &sprite.rows, 1, 128, FIELD_SPRITE_ROWS);
    gui_labeled_int_spinner(x, y, w, "Cols (Columnas)", &sprite.cols, 1, 128, FIELD_SPRITE_COLS);
    gui_labeled_int_spinner(x, y, w, "Z-Index", &sprite.zIndex, -9999, 9999, FIELD_SPRITE_ZINDEX);

    gui_labeled_vec2(x, y, w, "Position", &sprite.position, FIELD_SPRITE_POS);
    gui_labeled_vec2(x, y, w, "Scale", &sprite.scale, FIELD_SPRITE_SCALE);

    gui_labeled_float(x, y, w, "Rotation (deg)", &sprite.rotation, FIELD_SPRITE_ROTATION);
    gui_labeled_float(x, y, w, "Delay (segs)", &sprite.delay, FIELD_SPRITE_DELAY);

    gui_labeled_checkbox(x, y, "Visible", &sprite.visible);
    gui_labeled_checkbox(x, y, "Center Position", &sprite.centerPosition);
}

void draw_struct_properties(float x, ref float y, float w, ref LevelWorld world, ref LevelObject obj) {
    gui_section_header(x, y, w, "Elementos Internos del Struct");

    float thirdW = (w - 8.0f) / 3.0f;
    int parentUid = obj.uid;

    if (GuiButton(Rectangle(x, y, thirdW, 26.0f), "+ Hijo Box")) {
        obj.expanded = true;
        level_world_add_object(world, ObjectType.Box, parentUid);
        return;
    }
    if (GuiButton(Rectangle(x + thirdW + 4.0f, y, thirdW, 26.0f), "+ Hijo Sprite")) {
        obj.expanded = true;
        level_world_add_object(world, ObjectType.Sprite, parentUid);
        return;
    }
    if (GuiButton(Rectangle(x + (thirdW + 4.0f) * 2.0f, y, thirdW, 26.0f), "+ Hijo Struct")) {
        obj.expanded = true;
        level_world_add_object(world, ObjectType.Struct, parentUid);
        return;
    }
    y += 34.0f;

    gui_section_header(x, y, w, "Propiedades Custom del Struct");

    if (GuiButton(Rectangle(x, y, w, 26.0f), GuiIconText(GuiIconName.ICON_BOX_MORE, "Agregar Propiedad Custom"))) {
        level_struct_add_custom_prop(obj.customStruct, "prop", CustomValueType.String);
    }
    y += 34.0f;

    int deletePropIndex = -1;

    for (int i = 0; i < MAX_CUSTOM_PROPS; i++) {
        if (!obj.customStruct.props[i].used) {
            continue;
        }

        CustomProperty* prop = &obj.customStruct.props[i];
        int baseField = FIELD_STRUCT_BASE + i * 10;

        float trashW = 26.0f;
        float keyW = w - trashW - 6.0f;
        gui_search_box(Rectangle(x, y, keyW, 24.0f), prop.key.ptr, cast(int) prop.key.length, baseField);

        if (gui_trash_button(Rectangle(x + w - trashW, y, trashW, 24.0f))) {
            deletePropIndex = i;
        }
        y += 28.0f;

        int cType = cast(int) prop.type;
        float typeToggleW = w / 5.0f - 2.0f;
        GuiToggleGroup(Rectangle(x, y, typeToggleW, 22.0f), "Int;Float;Bool;Str;Vec2", &cType);
        if (cType != cast(int) prop.type) {
            gui_clear_active_field();
            prop.type = cast(CustomValueType) cType;
        }
        y += 26.0f;

        final switch (prop.type) {
            case CustomValueType.Int:
                gui_labeled_int_box(x, y, w, "Valor (int)", &prop.intVal, -999999, 999999, baseField + 1);
                break;
            case CustomValueType.Float:
                gui_labeled_float(x, y, w, "Valor (float)", &prop.floatVal, baseField + 2);
                break;
            case CustomValueType.Bool:
                gui_labeled_checkbox(x, y, "Activo (bool)", &prop.boolVal);
                break;
            case CustomValueType.String:
                gui_labeled_text_box(x, y, w, "Texto", prop.stringVal.ptr, cast(int) prop.stringVal.length, baseField + 3);
                break;
            case CustomValueType.Vec2:
                gui_labeled_vec2(x, y, w, "Vector2", &prop.vec2Val, baseField + 4);
                break;
        }

        GuiLine(Rectangle(x, y - 2.0f, w, 8.0f), null);
        y += 8.0f;
    }

    if (deletePropIndex >= 0) {
        gui_clear_active_field();
        level_struct_remove_custom_prop(obj.customStruct, deletePropIndex);
    }
}
