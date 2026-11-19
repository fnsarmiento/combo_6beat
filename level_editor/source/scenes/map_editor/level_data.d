module scenes.map_editor.level_data;

nothrow:
@nogc:

import raylib;
import core.stdc.stdio : snprintf;
import core.stdc.string : strlen, strncpy, strstr, strcmp;
import engine.path_utils;

public:

enum MAX_LEVEL_OBJECTS = 1024;
enum MAX_CUSTOM_PROPS = 16;

enum ObjectType : int {
    Box    = 0,
    Sprite = 1,
    Struct = 2
}

enum CustomValueType : int {
    Int    = 0,
    Float  = 1,
    Bool   = 2,
    String = 3,
    Vec2   = 4
}

/**
 * Propiedades específicas de un objeto tipo Box.
 * Por defecto siempre se crea en la posición (0, 0).
 */
struct BoxData {
    Vector2 position = Vector2(0.0f, 0.0f);
    Vector2 size     = Vector2(64.0f, 64.0f);
    int layer        = 1;
    int mask         = 1;
    bool disabled    = false;
}

/**
 * Propiedades específicas de un objeto tipo Sprite (Sprite2D + AnimatedSprite2D unificados).
 * Por defecto siempre se crea en la posición (0, 0).
 */
struct SpriteData {
    char[256] resourcePath = 0; // Siempre almacenado en modo relativo al resources del XML
    int rows               = 1;
    int cols               = 1;
    Vector2 position       = Vector2(0.0f, 0.0f);
    Vector2 scale          = Vector2(1.0f, 1.0f);
    float rotation         = 0.0f;
    bool visible           = true;
    float delay            = 0.1f;
    bool centerPosition    = false;
    int zIndex             = 0;

    // Estado interno de textura y animación para previsualización en el editor
    Texture2D texture;
    bool hasTexture        = false;
    float animTimer        = 0.0f;
    int currentFrame       = 0;
}

/**
 * Propiedad personalizada clave-valor para objetos de tipo Struct.
 */
struct CustomProperty {
    bool used              = false;
    char[64] key           = 0;
    CustomValueType type   = CustomValueType.String;
    int intVal             = 0;
    float floatVal         = 0.0f;
    bool boolVal           = false;
    char[128] stringVal    = 0;
    Vector2 vec2Val        = Vector2(0.0f, 0.0f);
}

/**
 * Datos custom para un objeto tipo Struct.
 */
struct StructData {
    CustomProperty[MAX_CUSTOM_PROPS] props;
    int propCount = 0;
}

/**
 * Objeto general del Level Editor (Box, Sprite o Struct).
 */
struct LevelObject {
    bool used         = false;
    int uid           = 0;
    int parentUid     = -1; // -1 si está en la raíz, o uid del Struct padre
    char[64] id       = 0;
    char[64] xmlTag   = 0;  // Etiqueta XML (ej. "Box", "Body", "Area", "Sprite", "NPC", "Struct")
    ObjectType type   = ObjectType.Box;
    char[64] group    = 0;
    bool expanded     = true; // Para Structs con elementos hijos

    BoxData box;
    SpriteData sprite;
    StructData customStruct;
}

/**
 * Contenedor del estado de objetos y rutas relativas del Level Editor.
 */
struct LevelWorld {
    LevelObject[MAX_LEVEL_OBJECTS] objects;
    int selectedUid = -1;
    int nextUid     = 1;

    // Contexto del archivo XML y ruta relativa de resources (<Map resources="../">)
    char[512] xmlFilePath      = 0;
    char[512] xmlDir           = 0;
    char[256] resourcesRelPath = 0;
    char[256] dialogsRelPath   = 0;
    char[64] startDialogId     = 0;
    float spawnX               = -1.0f;
    float spawnY               = -1.0f;
}

/**
 * Acceso global al mundo del nivel actual (compartido con el cargador XML y pestañas).
 */
ref LevelWorld get_active_level_world() {
    return global_level_world;
}

/**
 * Convierte cualquier ruta seleccionada con el diálogo de archivos a una ruta relativa
 * respecto al atributo 'resources' del XML actual.
 */
void scene_to_relative_resource_path(const(char)* selectedPath, char* outRelPath, size_t maxLen) {
    path_make_relative_to_resources(
        selectedPath,
        global_level_world.xmlDir.ptr,
        global_level_world.resourcesRelPath.ptr,
        outRelPath,
        maxLen
    );
}

/**
 * Resuelve una ruta relativa de recurso (ej. "debug/sprites/32x32.png") a su ruta
 * efectiva para cargarla en Raylib, usando el directorio del XML y <Map resources="...">.
 */
void scene_resolve_resource_path(const(char)* relPath, char* outFullPath, size_t maxLen) {
    path_resolve_for_loading(
        relPath,
        global_level_world.xmlDir.ptr,
        global_level_world.resourcesRelPath.ptr,
        outFullPath,
        maxLen
    );
}

/**
 * Inicializa el mundo del Level Editor.
 */
void level_world_init(ref LevelWorld world) {
    level_world_clear(world);
    snprintf(world.resourcesRelPath.ptr, world.resourcesRelPath.length, "../");
    world.dialogsRelPath[0] = '\0';
    world.startDialogId[0] = '\0';
    world.xmlFilePath[0] = '\0';
    world.xmlDir[0] = '\0';

    int boxUid = level_world_add_object(world, ObjectType.Box, -1);
    world.selectedUid = boxUid;
    level_world_reset_undo();
}

/**
 * Reinicia el historial de Undo del Level Editor.
 */
void level_world_reset_undo() {
    level_undo_count = 0;
}

/**
 * Guarda una instantánea del estado actual del LevelWorld en la pila de Undo del Level Editor.
 */
void level_world_push_undo(ref LevelWorld world) {
    if (level_undo_count >= MAX_LEVEL_UNDO) {
        for (int i = 1; i < MAX_LEVEL_UNDO; i++) {
            level_undo_stack[i - 1] = level_undo_stack[i];
        }
        level_undo_count = MAX_LEVEL_UNDO - 1;
    }

    LevelUndoSnapshot* snap = &level_undo_stack[level_undo_count++];
    snap.selectedUid = world.selectedUid;
    snap.nextUid = world.nextUid;
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        snap.objects[i] = world.objects[i];
        snap.objects[i].sprite.texture = Texture2D.init;
        snap.objects[i].sprite.hasTexture = false;
    }
}

/**
 * Deshace la última acción en el Level Editor (Ctrl+Z independiente).
 */
bool level_world_undo(ref LevelWorld world) {
    if (level_undo_count <= 0) {
        return false;
    }

    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (world.objects[i].used) {
            unload_sprite_texture(world.objects[i].sprite);
        }
    }

    level_undo_count--;
    LevelUndoSnapshot* snap = &level_undo_stack[level_undo_count];
    world.selectedUid = snap.selectedUid;
    world.nextUid = snap.nextUid;

    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        world.objects[i] = snap.objects[i];
        if (world.objects[i].used &&
            world.objects[i].type == ObjectType.Sprite &&
            world.objects[i].sprite.resourcePath[0] != '\0') {
            level_sprite_reload_texture(world.objects[i].sprite);
        }
    }
    return true;
}

/**
 * Duplica un objeto por su uid (y recursivamente sus hijos si es un Struct) para el atajo Ctrl+D.
 * Retorna el uid del nuevo objeto duplicado.
 */
int level_world_duplicate_object(ref LevelWorld world, int uid) {
    LevelObject* src = level_world_find(world, uid);
    if (src is null) {
        return -1;
    }

    level_world_push_undo(world);
    int newUid = duplicate_object_recursive(world, uid, src.parentUid);
    if (newUid >= 0) {
        world.selectedUid = newUid;
    }
    return newUid;
}

/**
 * Libera texturas y limpia todos los objetos del Level Editor.
 */
void level_world_clear(ref LevelWorld world) {
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (world.objects[i].used) {
            unload_sprite_texture(world.objects[i].sprite);
            world.objects[i] = LevelObject.init;
        }
    }
    world.selectedUid = -1;
    world.nextUid = 1;
}

/**
 * Agrega un nuevo objeto (Box, Sprite o Struct).
 * Todos los objetos Box o Sprite se crean siempre en la posición (0, 0).
 */
int level_world_add_object(ref LevelWorld world, ObjectType type, int parentUid = -1) {
    int slot = find_free_slot(world);
    if (slot < 0) {
        return -1;
    }

    int uid = world.nextUid++;
    LevelObject* obj = &world.objects[slot];
    *obj = LevelObject.init;
    obj.used = true;
    obj.uid = uid;
    obj.parentUid = parentUid;
    obj.type = type;
    obj.expanded = true;

    const(char)* prefix = get_type_prefix(type);
    snprintf(obj.id.ptr, obj.id.length, "%s_%d", prefix, uid);
    snprintf(obj.xmlTag.ptr, obj.xmlTag.length, "%s", level_object_type_label(type));
    snprintf(obj.group.ptr, obj.group.length, "default");

    // Todos los objetos Box o Sprite aparecen siempre en (0, 0)
    obj.box.position = Vector2(0.0f, 0.0f);
    obj.sprite.position = Vector2(0.0f, 0.0f);

    if (type == ObjectType.Struct) {
        level_struct_add_custom_prop(obj.customStruct, "custom_tag", CustomValueType.String);
    }

    world.selectedUid = uid;
    return uid;
}

/**
 * Asegura una propiedad custom dentro de un StructData (si ya existe la key, opcionalmente no la duplica).
 */
void level_struct_ensure_prop(
    ref StructData st,
    const(char)* keyName,
    CustomValueType type,
    const(char)* strVal = "",
    int intVal = 0,
    float floatVal = 0.0f,
    bool boolVal = false
) {
    for (int i = 0; i < MAX_CUSTOM_PROPS; i++) {
        if (st.props[i].used && strcmp(st.props[i].key.ptr, keyName) == 0) {
            return;
        }
    }
    int idx = level_struct_add_custom_prop(st, keyName, type);
    if (idx >= 0) {
        st.props[idx].intVal = intVal;
        st.props[idx].floatVal = floatVal;
        st.props[idx].boolVal = boolVal;
        if (strVal !is null) {
            strncpy(st.props[idx].stringVal.ptr, strVal, st.props[idx].stringVal.length - 1);
            st.props[idx].stringVal[st.props[idx].stringVal.length - 1] = '\0';
        }
    }
}

void level_struct_apply_npc_props(ref StructData st) {
    level_struct_ensure_prop(st, "dialogId", CustomValueType.String, "__start__");
    level_struct_ensure_prop(st, "dialogFile", CustomValueType.String, "dialogs/debug.json");
    level_struct_ensure_prop(st, "itemId", CustomValueType.String, "");
    level_struct_ensure_prop(st, "itemCount", CustomValueType.Int, "", 1);
    level_struct_ensure_prop(st, "autoTrigger", CustomValueType.Bool, "", 0, 0.0f, false);
    level_struct_ensure_prop(st, "songScene", CustomValueType.String, "");
    level_struct_ensure_prop(st, "type", CustomValueType.String, "GameObject");
}

void level_struct_apply_chest_props(ref StructData st) {
    level_struct_ensure_prop(st, "itemId", CustomValueType.String, "potion_small");
    level_struct_ensure_prop(st, "itemCount", CustomValueType.Int, "", 1);
    level_struct_ensure_prop(st, "requiredItem", CustomValueType.String, "");
    level_struct_ensure_prop(st, "dialogId", CustomValueType.String, "");
    level_struct_ensure_prop(st, "opened", CustomValueType.Bool, "", 0, 0.0f, false);
    level_struct_ensure_prop(st, "type", CustomValueType.String, "GameObject");
}

void level_struct_apply_scene_changer_props(ref StructData st) {
    level_struct_ensure_prop(st, "targetScene", CustomValueType.String, "resources/scenes/a.xml");
    level_struct_ensure_prop(st, "spawnX", CustomValueType.Float, "", 0, 160.0f);
    level_struct_ensure_prop(st, "spawnY", CustomValueType.Float, "", 0, 160.0f);
    level_struct_ensure_prop(st, "triggerOnTouch", CustomValueType.Bool, "", 0, 0.0f, true);
    level_struct_ensure_prop(st, "requiredItem", CustomValueType.String, "");
    level_struct_ensure_prop(st, "requiredFlag", CustomValueType.String, "");
    level_struct_ensure_prop(st, "type", CustomValueType.String, "GameObject");
}

private int add_child_box_helper(
    ref LevelWorld world,
    int parentUid,
    const(char)* idStr,
    const(char)* xmlTag,
    const(char)* groupStr,
    Vector2 pos,
    Vector2 size,
    int layer,
    int mask
) {
    int uid = level_world_add_object(world, ObjectType.Box, parentUid);
    LevelObject* child = level_world_find(world, uid);
    if (child !is null) {
        snprintf(child.id.ptr, child.id.length, "%s", idStr);
        snprintf(child.xmlTag.ptr, child.xmlTag.length, "%s", xmlTag);
        snprintf(child.group.ptr, child.group.length, "%s", groupStr);
        child.box.position = pos;
        child.box.size = size;
        child.box.layer = layer;
        child.box.mask = mask;
    }
    return uid;
}

private int add_child_sprite_helper(
    ref LevelWorld world,
    int parentUid,
    const(char)* idStr,
    const(char)* groupStr,
    const(char)* resPath,
    Vector2 pos,
    int zIndex,
    int rows,
    int cols
) {
    int uid = level_world_add_object(world, ObjectType.Sprite, parentUid);
    LevelObject* child = level_world_find(world, uid);
    if (child !is null) {
        snprintf(child.id.ptr, child.id.length, "%s", idStr);
        snprintf(child.group.ptr, child.group.length, "%s", groupStr);
        snprintf(child.sprite.resourcePath.ptr, child.sprite.resourcePath.length, "%s", resPath);
        child.sprite.position = pos;
        child.sprite.zIndex = zIndex;
        child.sprite.rows = rows;
        child.sprite.cols = cols;
        child.sprite.centerPosition = true;
        if (resPath[0] != '\0') {
            level_sprite_reload_texture(child.sprite);
        }
    }
    return uid;
}

/**
 * Crea un Struct preset de tipo <Player> con Body (12x12) y Sprite listos para editar.
 */
int level_world_add_player_preset(ref LevelWorld world, Vector2 pos) {
    int uid = level_world_add_object(world, ObjectType.Struct, -1);
    LevelObject* pl = level_world_find(world, uid);
    if (pl is null) return -1;

    snprintf(pl.id.ptr, pl.id.length, "player");
    snprintf(pl.xmlTag.ptr, pl.xmlTag.length, "Player");
    snprintf(pl.group.ptr, pl.group.length, "Player");
    pl.customStruct = StructData.init;
    level_struct_ensure_prop(pl.customStruct, "speed", CustomValueType.Float, "", 0, 92.0f);
    level_struct_ensure_prop(pl.customStruct, "type", CustomValueType.String, "Struct");

    add_child_box_helper(world, uid, "player_body", "Body", "Player", pos, Vector2(12.0f, 12.0f), 1, 1);
    add_child_sprite_helper(
        world, uid, "player_sprite", "Player",
        "sprites/player/The Female Adventurer - Free/Idle/Idle_Down.png",
        Vector2(pos.x + 6.0f, pos.y + 6.0f), 150, 1, 8
    );

    world.selectedUid = uid;
    return uid;
}

/**
 * Crea un Struct preset de tipo <Enemy> con Body, Area y Sprite listos para editar.
 */
int level_world_add_enemy_preset(ref LevelWorld world, Vector2 pos) {
    int uid = level_world_add_object(world, ObjectType.Struct, -1);
    LevelObject* en = level_world_find(world, uid);
    if (en is null) return -1;

    snprintf(en.id.ptr, en.id.length, "enemy_%d", uid);
    snprintf(en.xmlTag.ptr, en.xmlTag.length, "Enemy");
    snprintf(en.group.ptr, en.group.length, "Enemies");
    en.customStruct = StructData.init;
    level_struct_apply_npc_props(en.customStruct);

    char[64] bodyId, areaId, sprId;
    snprintf(bodyId.ptr, bodyId.length, "enemy_%d_body", uid);
    snprintf(areaId.ptr, areaId.length, "enemy_%d_area", uid);
    snprintf(sprId.ptr, sprId.length, "enemy_%d_sprite", uid);

    add_child_box_helper(world, uid, bodyId.ptr, "Body", "Enemies", pos, Vector2(28.0f, 28.0f), 1, 0);
    add_child_box_helper(world, uid, areaId.ptr, "Area", "Enemies", Vector2(pos.x - 8.0f, pos.y - 8.0f), Vector2(44.0f, 44.0f), 0, 2);
    add_child_sprite_helper(
        world, uid, sprId.ptr, "Enemies",
        "sprites/bosses/bear.png",
        Vector2(pos.x + 14.0f, pos.y + 6.0f), 150, 3, 3
    );

    world.selectedUid = uid;
    return uid;
}

/**
 * Crea un Struct preset de tipo <NPC> con Body, Area y Sprite listos para editar.
 */
int level_world_add_npc_preset(ref LevelWorld world, Vector2 pos) {
    int uid = level_world_add_object(world, ObjectType.Struct, -1);
    LevelObject* npc = level_world_find(world, uid);
    if (npc is null) return -1;

    snprintf(npc.id.ptr, npc.id.length, "npc_%d", uid);
    snprintf(npc.xmlTag.ptr, npc.xmlTag.length, "NPC");
    snprintf(npc.group.ptr, npc.group.length, "NPCs");
    npc.customStruct = StructData.init;
    level_struct_apply_npc_props(npc.customStruct);

    char[64] bodyId, areaId, sprId;
    snprintf(bodyId.ptr, bodyId.length, "npc_%d_body", uid);
    snprintf(areaId.ptr, areaId.length, "npc_%d_area", uid);
    snprintf(sprId.ptr, sprId.length, "npc_%d_sprite", uid);

    add_child_box_helper(world, uid, bodyId.ptr, "Body", "NPCs", pos, Vector2(32.0f, 32.0f), 1, 0);
    add_child_box_helper(world, uid, areaId.ptr, "Area", "NPCs", Vector2(pos.x - 6.0f, pos.y - 6.0f), Vector2(44.0f, 44.0f), 0, 2);
    add_child_sprite_helper(
        world, uid, sprId.ptr, "NPCs",
        "sprites/player/The Female Adventurer - Free/Idle/Idle_Down.png",
        Vector2(pos.x + 16.0f, pos.y + 16.0f), 150, 1, 8
    );

    world.selectedUid = uid;
    return uid;
}

/**
 * Crea un Struct preset de tipo <Chest> (Cofre) con Body, Area y Sprite listos para editar.
 */
int level_world_add_chest_preset(ref LevelWorld world, Vector2 pos) {
    int uid = level_world_add_object(world, ObjectType.Struct, -1);
    LevelObject* chest = level_world_find(world, uid);
    if (chest is null) return -1;

    snprintf(chest.id.ptr, chest.id.length, "chest_%d", uid);
    snprintf(chest.xmlTag.ptr, chest.xmlTag.length, "Chest");
    snprintf(chest.group.ptr, chest.group.length, "Chests");
    chest.customStruct = StructData.init;
    level_struct_apply_chest_props(chest.customStruct);

    char[64] bodyId, areaId, sprId;
    snprintf(bodyId.ptr, bodyId.length, "chest_%d_body", uid);
    snprintf(areaId.ptr, areaId.length, "chest_%d_area", uid);
    snprintf(sprId.ptr, sprId.length, "chest_%d_sprite", uid);

    add_child_box_helper(world, uid, bodyId.ptr, "Body", "Chests", pos, Vector2(32.0f, 32.0f), 1, 0);
    add_child_box_helper(world, uid, areaId.ptr, "Area", "Chests", Vector2(pos.x - 6.0f, pos.y - 6.0f), Vector2(44.0f, 44.0f), 0, 2);
    add_child_sprite_helper(world, uid, sprId.ptr, "Chests", "", Vector2(pos.x + 16.0f, pos.y + 16.0f), 140, 1, 1);

    world.selectedUid = uid;
    return uid;
}

/**
 * Crea un Struct preset de tipo <SceneChanger> con Area y Sprite listos para editar.
 */
int level_world_add_scene_changer_preset(ref LevelWorld world, Vector2 pos) {
    int uid = level_world_add_object(world, ObjectType.Struct, -1);
    LevelObject* changer = level_world_find(world, uid);
    if (changer is null) return -1;

    snprintf(changer.id.ptr, changer.id.length, "changer_%d", uid);
    snprintf(changer.xmlTag.ptr, changer.xmlTag.length, "SceneChanger");
    snprintf(changer.group.ptr, changer.group.length, "SceneChangers");
    changer.customStruct = StructData.init;
    level_struct_apply_scene_changer_props(changer.customStruct);

    char[64] areaId, sprId;
    snprintf(areaId.ptr, areaId.length, "changer_%d_area", uid);
    snprintf(sprId.ptr, sprId.length, "changer_%d_sprite", uid);

    add_child_box_helper(world, uid, areaId.ptr, "Area", "SceneChangers", pos, Vector2(32.0f, 64.0f), 0, 2);
    add_child_sprite_helper(world, uid, sprId.ptr, "SceneChangers", "", Vector2(pos.x + 16.0f, pos.y + 32.0f), 120, 1, 1);

    world.selectedUid = uid;
    return uid;
}

/**
 * Busca la posición de referencia (ancla) de un Struct tomando su primer hijo Box o Sprite.
 */
bool level_world_get_struct_anchor(ref const(LevelWorld) world, int structUid, out Vector2 outPos) {
    outPos = Vector2(0.0f, 0.0f);
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].parentUid != structUid) {
            continue;
        }
        if (world.objects[i].type == ObjectType.Box) {
            outPos = world.objects[i].box.position;
            return true;
        }
    }
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].parentUid != structUid) {
            continue;
        }
        if (world.objects[i].type == ObjectType.Sprite) {
            outPos = world.objects[i].sprite.position;
            return true;
        } else if (world.objects[i].type == ObjectType.Struct) {
            if (level_world_get_struct_anchor(world, world.objects[i].uid, outPos)) {
                return true;
            }
        }
    }
    return false;
}

/**
 * Traslada todos los hijos (Box, Sprite y sub-Structs) de un Struct por el delta indicado.
 */
void level_world_translate_struct(ref LevelWorld world, int structUid, Vector2 delta) {
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].parentUid != structUid) {
            continue;
        }
        if (world.objects[i].type == ObjectType.Box) {
            world.objects[i].box.position.x += delta.x;
            world.objects[i].box.position.y += delta.y;
        } else if (world.objects[i].type == ObjectType.Sprite) {
            world.objects[i].sprite.position.x += delta.x;
            world.objects[i].sprite.position.y += delta.y;
        } else if (world.objects[i].type == ObjectType.Struct) {
            level_world_translate_struct(world, world.objects[i].uid, delta);
        }
    }
}

/**
 * Busca el valor string de una propiedad custom en un Struct por su clave.
 */
const(char)* level_struct_find_prop_str(ref const(StructData) st, const(char)* keyName) {
    for (int i = 0; i < MAX_CUSTOM_PROPS; i++) {
        if (st.props[i].used && strcmp(st.props[i].key.ptr, keyName) == 0) {
            return st.props[i].stringVal.ptr;
        }
    }
    return null;
}

/**
 * Elimina un objeto por su uid y recursivamente todos sus hijos internos (si es un Struct).
 */
void level_world_remove_object(ref LevelWorld world, int uid) {
    if (uid < 0) {
        return;
    }

    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (world.objects[i].used && world.objects[i].parentUid == uid) {
            level_world_remove_object(world, world.objects[i].uid);
        }
    }

    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (world.objects[i].used && world.objects[i].uid == uid) {
            unload_sprite_texture(world.objects[i].sprite);
            world.objects[i].used = false;
            break;
        }
    }

    if (world.selectedUid == uid) {
        world.selectedUid = -1;
    }
}

/**
 * Busca y retorna un puntero al objeto por su uid, o null si no existe.
 */
LevelObject* level_world_find(ref LevelWorld world, int uid) {
    if (uid < 0) {
        return null;
    }
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (world.objects[i].used && world.objects[i].uid == uid) {
            return &world.objects[i];
        }
    }
    return null;
}

/**
 * Recarga la textura de un Sprite resolviendo su resourcePath en modo relativo al XML.
 */
bool level_sprite_reload_texture(ref SpriteData sprite) {
    unload_sprite_texture(sprite);

    if (sprite.resourcePath[0] == '\0') {
        return false;
    }

    // Asegurar que la ruta almacenada en sprite.resourcePath sea siempre relativa
    if (sprite.resourcePath[0] == '/') {
        char[256] relBuf;
        scene_to_relative_resource_path(sprite.resourcePath.ptr, relBuf.ptr, relBuf.length);
        strncpy(sprite.resourcePath.ptr, relBuf.ptr, sprite.resourcePath.length - 1);
        sprite.resourcePath[sprite.resourcePath.length - 1] = '\0';
    }

    char[1024] resolvedPath;
    scene_resolve_resource_path(sprite.resourcePath.ptr, resolvedPath.ptr, resolvedPath.length);

    if (!FileExists(resolvedPath.ptr)) {
        return false;
    }

    sprite.texture = LoadTexture(resolvedPath.ptr);
    sprite.hasTexture = (sprite.texture.id != 0);
    sprite.currentFrame = 0;
    sprite.animTimer = 0.0f;
    return sprite.hasTexture;
}

/**
 * Recarga las texturas de todos los Sprites del mundo (útil tras cambiar el XML o resources path).
 */
void level_world_reload_all_textures(ref LevelWorld world) {
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (world.objects[i].used && world.objects[i].type == ObjectType.Sprite) {
            level_sprite_reload_texture(world.objects[i].sprite);
        }
    }
}

/**
 * Actualiza los temporizadores de animación de todos los Sprites activos.
 */
void level_world_update_animations(ref LevelWorld world, float dt) {
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].type != ObjectType.Sprite) {
            continue;
        }

        SpriteData* sp = &world.objects[i].sprite;
        if (sp.rows < 1) sp.rows = 1;
        if (sp.cols < 1) sp.cols = 1;

        int totalFrames = sp.rows * sp.cols;
        if (totalFrames > 1 && sp.delay > 0.001f) {
            sp.animTimer += dt;
            if (sp.animTimer >= sp.delay) {
                sp.animTimer -= sp.delay;
                sp.currentFrame = (sp.currentFrame + 1) % totalFrames;
            }
        } else {
            sp.currentFrame = 0;
        }
    }
}

/**
 * Agrega una propiedad custom a un StructData.
 */
int level_struct_add_custom_prop(
    ref StructData st,
    const(char)* keyName = "nueva_prop",
    CustomValueType type = CustomValueType.Int
) {
    for (int i = 0; i < MAX_CUSTOM_PROPS; i++) {
        if (!st.props[i].used) {
            st.props[i] = CustomProperty.init;
            st.props[i].used = true;
            st.props[i].type = type;
            snprintf(st.props[i].key.ptr, st.props[i].key.length, "%s", keyName);
            snprintf(st.props[i].stringVal.ptr, st.props[i].stringVal.length, "");
            st.propCount++;
            return i;
        }
    }
    return -1;
}

/**
 * Elimina una propiedad custom de un StructData por índice.
 */
void level_struct_remove_custom_prop(ref StructData st, int index) {
    if (index < 0 || index >= MAX_CUSTOM_PROPS || !st.props[index].used) {
        return;
    }
    st.props[index].used = false;
    if (st.propCount > 0) {
        st.propCount--;
    }
}

/**
 * Verifica si el ID de un objeto coincide con el filtro de búsqueda (case-insensitive).
 */
bool level_object_matches_search(ref const(LevelObject) obj, const(char)* query) {
    if (query is null || query[0] == '\0') {
        return true;
    }

    char[64] idLower = 0;
    char[64] queryLower = 0;
    to_lower_copy(idLower.ptr, obj.id.ptr, idLower.length);
    to_lower_copy(queryLower.ptr, query, queryLower.length);

    return strstr(idLower.ptr, queryLower.ptr) !is null;
}

/**
 * Verifica si un objeto o cualquiera de sus descendientes coincide con la búsqueda.
 */
bool level_object_or_children_match(ref LevelWorld world, ref const(LevelObject) obj, const(char)* query) {
    if (level_object_matches_search(obj, query)) {
        return true;
    }

    if (obj.type == ObjectType.Struct) {
        for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
            if (world.objects[i].used && world.objects[i].parentUid == obj.uid) {
                if (level_object_or_children_match(world, world.objects[i], query)) {
                    return true;
                }
            }
        }
    }

    return false;
}

/**
 * Retorna true si el nombre de grupo está vacío o es "default" (case-insensitive).
 * En ese caso el objeto en la raíz no se mete dentro de un encabezado de grupo.
 */
bool level_group_is_default_or_empty(const(char)* group) {
    if (group is null || group[0] == '\0') {
        return true;
    }
    char[64] lower = 0;
    to_lower_copy(lower.ptr, group, lower.length);
    return strcmp(lower.ptr, "default") == 0;
}

/**
 * Mueve un objeto hacia arriba (direction = -1) o hacia abajo (direction = +1)
 * dentro de la lista jerárquica, respetando su ámbito:
 * - Si es miembro de un Struct (parentUid != -1), solo se reordena entre los hijos de ese Struct.
 * - Si está en la raíz y tiene un grupo con nombre, solo se reordena dentro de ese mismo grupo.
 * - Si está en la raíz y su grupo es vacío o "default", solo se reordena entre los objetos sin grupo.
 */
bool level_world_move_object(ref LevelWorld world, int uid, int direction) {
    if (uid < 0 || (direction != -1 && direction != 1)) {
        return false;
    }

    int slotA = -1;
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (world.objects[i].used && world.objects[i].uid == uid) {
            slotA = i;
            break;
        }
    }
    if (slotA < 0) {
        return false;
    }

    int slotB = -1;
    int step = (direction < 0) ? -1 : 1;
    for (int j = slotA + step; j >= 0 && j < MAX_LEVEL_OBJECTS; j += step) {
        if (!world.objects[j].used) {
            continue;
        }
        if (same_reorder_scope(world.objects[slotA], world.objects[j])) {
            slotB = j;
            break;
        }
    }

    if (slotB < 0) {
        return false;
    }

    LevelObject temp = world.objects[slotA];
    world.objects[slotA] = world.objects[slotB];
    world.objects[slotB] = temp;
    return true;
}

/**
 * Retorna una etiqueta legible para el tipo de objeto.
 */
const(char)* level_object_type_label(ObjectType type) {
    final switch (type) {
        case ObjectType.Box:    return "Box";
        case ObjectType.Sprite: return "Sprite";
        case ObjectType.Struct: return "Struct";
    }
}

private:

enum int MAX_LEVEL_UNDO = 24;

struct LevelUndoSnapshot {
    LevelObject[MAX_LEVEL_OBJECTS] objects;
    int selectedUid = -1;
    int nextUid = 1;
}

__gshared LevelWorld global_level_world;
__gshared LevelUndoSnapshot[MAX_LEVEL_UNDO] level_undo_stack;
__gshared int level_undo_count = 0;

int duplicate_object_recursive(ref LevelWorld world, int srcUid, int targetParentUid) {
    LevelObject* src = level_world_find(world, srcUid);
    if (src is null) {
        return -1;
    }

    int slot = find_free_slot(world);
    if (slot < 0) {
        return -1;
    }

    int newUid = world.nextUid++;
    LevelObject srcCopy = *src;

    LevelObject* dst = &world.objects[slot];
    *dst = srcCopy;
    dst.used = true;
    dst.uid = newUid;
    dst.parentUid = targetParentUid;
    snprintf(dst.id.ptr, dst.id.length, "%.48s_copy_%d", srcCopy.id.ptr, newUid);

    dst.sprite.texture = Texture2D.init;
    dst.sprite.hasTexture = false;
    if (dst.type == ObjectType.Sprite && dst.sprite.resourcePath[0] != '\0') {
        level_sprite_reload_texture(dst.sprite);
    }

    if (srcCopy.type == ObjectType.Struct) {
        for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
            if (world.objects[i].used && world.objects[i].parentUid == srcUid) {
                duplicate_object_recursive(world, world.objects[i].uid, newUid);
            }
        }
    }

    return newUid;
}

int find_free_slot(ref LevelWorld world) {
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used) {
            return i;
        }
    }
    return -1;
}

const(char)* get_type_prefix(ObjectType type) {
    final switch (type) {
        case ObjectType.Box:    return "box";
        case ObjectType.Sprite: return "sprite";
        case ObjectType.Struct: return "struct";
    }
}

void unload_sprite_texture(ref SpriteData sprite) {
    if (sprite.hasTexture && sprite.texture.id != 0) {
        UnloadTexture(sprite.texture);
    }
    sprite.texture = Texture2D.init;
    sprite.hasTexture = false;
}

bool same_reorder_scope(ref const(LevelObject) a, ref const(LevelObject) b) {
    if (a.parentUid != b.parentUid) {
        return false;
    }

    // Si son miembros de un Struct (parentUid != -1), ignoran los grupos de arriba
    if (a.parentUid != -1) {
        return true;
    }

    // Si están en la raíz, comprobar si tienen grupo vacío/default o el mismo grupo con nombre
    bool aDef = level_group_is_default_or_empty(a.group.ptr);
    bool bDef = level_group_is_default_or_empty(b.group.ptr);
    if (aDef && bDef) {
        return true;
    }
    if (aDef != bDef) {
        return false;
    }
    return strcmp(a.group.ptr, b.group.ptr) == 0;
}

void to_lower_copy(char* dst, const(char)* src, size_t maxLen) {
    if (dst is null || maxLen == 0) {
        return;
    }
    if (src is null) {
        dst[0] = '\0';
        return;
    }
    size_t i = 0;
    for (; i < maxLen - 1 && src[i] != '\0'; i++) {
        char c = src[i];
        if (c >= 'A' && c <= 'Z') {
            c = cast(char)(c + ('a' - 'A'));
        }
        dst[i] = c;
    }
    dst[i] = '\0';
}
