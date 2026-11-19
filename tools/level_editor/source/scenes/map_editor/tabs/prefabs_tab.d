module scenes.map_editor.tabs.prefabs_tab;

nothrow:
@nogc:

import raylib;
import raygui;
import core.stdc.stdio : FILE, fprintf, snprintf;
import core.stdc.stdlib : atoi, strtof;
import core.stdc.string : strlen, strncpy, strcmp, strchr;
import core.stdc.math : floorf;
import scenes.map_editor.level_data;
import scenes.map_editor.gui_widgets;
import scenes.map_editor.camera_controller;
import scenes.map_editor.tabs.level_inspector_panel : inspector_panel_draw;
import scenes.map_editor.project_manager : ProcshitProject, get_active_project, procshit_save_project;

public:

enum int MAX_PREFABS = 64;
enum int MAX_PREFAB_OBJECTS = 16;

struct PrefabDef {
    bool used = false;
    char[64] name = 0;
    LevelObject[MAX_PREFAB_OBJECTS] objects;
}

/**
 * Inicializa la pestaña de Prefabs (objetos reutilizables guardados dentro de .procshit).
 */
void prefabs_tab_init() {
    level_world_clear(prefab_world);
    snprintf(prefab_world.resourcesRelPath.ptr, prefab_world.resourcesRelPath.length, "../");
    prefab_camera.initialized = false;
    prefab_active_tool = 0;
    prefab_snap_16px = false;
    prefab_dragging_uid = -1;
    prefab_resizing_uid = -1;
    prefab_resizing_handle = -1;
    prefab_creating_box = false;
    prefab_list_scroll = Vector2(0, 0);
    prefab_tree_scroll = Vector2(0, 0);
    prefab_save_timer = 0.0f;

    prefabs_reset_to_defaults();
}

/**
 * Libera recursos de la pestaña de Prefabs.
 */
void prefabs_tab_close() {
    level_world_clear(prefab_world);
    for (int p = 0; p < MAX_PREFABS; p++) {
        for (int i = 0; i < MAX_PREFAB_OBJECTS; i++) {
            prefabs[p].objects[i].sprite.texture = Texture2D.init;
            prefabs[p].objects[i].sprite.hasTexture = false;
        }
    }
}

/**
 * Sincroniza el mundo temporal de edición hacia el slot activo de prefabs[].
 */
void prefabs_sync_active_from_world() {
    if (active_prefab_idx < 0 || active_prefab_idx >= MAX_PREFABS || !prefabs[active_prefab_idx].used) {
        return;
    }

    PrefabDef* pf = &prefabs[active_prefab_idx];
    for (int i = 0; i < MAX_PREFAB_OBJECTS; i++) {
        pf.objects[i] = LevelObject.init;
    }

    int dstIdx = 0;
    for (int i = 0; i < MAX_LEVEL_OBJECTS && dstIdx < MAX_PREFAB_OBJECTS; i++) {
        if (!prefab_world.objects[i].used) continue;
        pf.objects[dstIdx] = prefab_world.objects[i];
        pf.objects[dstIdx].sprite.texture = Texture2D.init;
        pf.objects[dstIdx].sprite.hasTexture = false;
        if (dstIdx == 0 && pf.objects[0].id[0] != '\0') {
            strncpy(pf.name.ptr, pf.objects[0].id.ptr, pf.name.length - 1);
            pf.name[pf.name.length - 1] = '\0';
        }
        dstIdx++;
    }
}

/**
 * Carga el prefab indicado dentro de prefab_world para editarlo en la pestaña Prefabs.
 */
void prefabs_load_slot_into_world(int idx) {
    if (idx < 0 || idx >= MAX_PREFABS || !prefabs[idx].used) {
        return;
    }

    // Copiar contexto de rutas desde el LevelWorld principal para resolver sprites correctamente
    ref LevelWorld mainWorld = get_active_level_world();
    level_world_clear(prefab_world);
    strncpy(prefab_world.xmlDir.ptr, mainWorld.xmlDir.ptr, prefab_world.xmlDir.length - 1);
    strncpy(prefab_world.resourcesRelPath.ptr, mainWorld.resourcesRelPath.ptr, prefab_world.resourcesRelPath.length - 1);
    if (prefab_world.resourcesRelPath[0] == '\0') {
        snprintf(prefab_world.resourcesRelPath.ptr, prefab_world.resourcesRelPath.length, "../");
    }

    active_prefab_idx = idx;
    PrefabDef* pf = &prefabs[idx];
    int maxUid = 0;
    int firstUid = -1;

    for (int i = 0; i < MAX_PREFAB_OBJECTS; i++) {
        if (!pf.objects[i].used) continue;
        prefab_world.objects[i] = pf.objects[i];
        prefab_world.objects[i].sprite.texture = Texture2D.init;
        prefab_world.objects[i].sprite.hasTexture = false;
        if (prefab_world.objects[i].type == ObjectType.Sprite &&
            prefab_world.objects[i].sprite.resourcePath[0] != '\0') {
            level_sprite_reload_texture(prefab_world.objects[i].sprite);
        }
        if (prefab_world.objects[i].uid > maxUid) {
            maxUid = prefab_world.objects[i].uid;
        }
        if (firstUid < 0) {
            firstUid = prefab_world.objects[i].uid;
        }
    }

    prefab_world.nextUid = maxUid + 1;
    prefab_world.selectedUid = firstUid;
}

/**
 * Retorna cantidad de prefabs activos.
 */
int prefabs_get_count() {
    int cnt = 0;
    for (int i = 0; i < MAX_PREFABS; i++) {
        if (prefabs[i].used) cnt++;
    }
    return cnt;
}

bool prefabs_is_used(int idx) {
    return idx >= 0 && idx < MAX_PREFABS && prefabs[idx].used;
}

const(char)* prefabs_get_name(int idx) {
    if (idx < 0 || idx >= MAX_PREFABS || !prefabs[idx].used) return "";
    return prefabs[idx].name.ptr;
}

const(char)* prefabs_get_xml_tag(int idx) {
    if (idx < 0 || idx >= MAX_PREFABS || !prefabs[idx].used) return "Struct";
    if (prefabs[idx].objects[0].used && prefabs[idx].objects[0].xmlTag[0] != '\0') {
        return prefabs[idx].objects[0].xmlTag.ptr;
    }
    return "Struct";
}

int prefabs_get_active_index() {
    return active_prefab_idx;
}

void prefabs_set_active_index(int idx) {
    if (idx >= 0 && idx < MAX_PREFABS && prefabs[idx].used) {
        prefabs_sync_active_from_world();
        prefabs_load_slot_into_world(idx);
    }
}

/**
 * Instancia el prefab indicado dentro del LevelWorld del mapa actual en la posición spawnPos.
 * Cuando el mapa se guarda a .xml, esta estructura queda añadida al final del XML para Java.
 */
int prefabs_instantiate_into_world(int prefabIdx, ref LevelWorld targetWorld, Vector2 spawnPos) {
    prefabs_sync_active_from_world();
    if (prefabIdx < 0 || prefabIdx >= MAX_PREFABS || !prefabs[prefabIdx].used) {
        return -1;
    }

    PrefabDef* pf = &prefabs[prefabIdx];
    if (!pf.objects[0].used) {
        return -1;
    }

    level_world_push_undo(targetWorld);

    int[MAX_PREFAB_OBJECTS] oldUids;
    int[MAX_PREFAB_OBJECTS] newUids;
    for (int i = 0; i < MAX_PREFAB_OBJECTS; i++) {
        oldUids[i] = -1;
        newUids[i] = -1;
    }

    int rootNewUid = -1;
    for (int i = 0; i < MAX_PREFAB_OBJECTS; i++) {
        if (!pf.objects[i].used) continue;
        const(LevelObject)* srcObj = &pf.objects[i];
        oldUids[i] = srcObj.uid;

        int mappedParent = -1;
        if (srcObj.parentUid >= 0) {
            for (int k = 0; k < i; k++) {
                if (oldUids[k] == srcObj.parentUid) {
                    mappedParent = newUids[k];
                    break;
                }
            }
        }

        int createdUid = level_world_add_object(targetWorld, srcObj.type, mappedParent);
        newUids[i] = createdUid;
        if (rootNewUid < 0) {
            rootNewUid = createdUid;
        }

        LevelObject* dstObj = level_world_find(targetWorld, createdUid);
        if (dstObj is null) continue;

        strncpy(dstObj.xmlTag.ptr, srcObj.xmlTag.ptr, dstObj.xmlTag.length - 1);
        strncpy(dstObj.group.ptr, srcObj.group.ptr, dstObj.group.length - 1);
        if (mappedParent == -1) {
            if (strcmp(srcObj.xmlTag.ptr, "Player") == 0) {
                snprintf(dstObj.id.ptr, dstObj.id.length, "%s", srcObj.id.ptr);
            } else {
                snprintf(dstObj.id.ptr, dstObj.id.length, "%s_%d", srcObj.id.ptr, createdUid);
            }
        } else {
            snprintf(dstObj.id.ptr, dstObj.id.length, "%s_%d", srcObj.id.ptr, createdUid);
        }

        dstObj.box = srcObj.box;
        dstObj.sprite = srcObj.sprite;
        dstObj.sprite.texture = Texture2D.init;
        dstObj.sprite.hasTexture = false;
        dstObj.customStruct = srcObj.customStruct;

        if (dstObj.type == ObjectType.Box) {
            dstObj.box.position.x += spawnPos.x;
            dstObj.box.position.y += spawnPos.y;
        } else if (dstObj.type == ObjectType.Sprite) {
            dstObj.sprite.position.x += spawnPos.x;
            dstObj.sprite.position.y += spawnPos.y;
            if (dstObj.sprite.resourcePath[0] != '\0') {
                level_sprite_reload_texture(dstObj.sprite);
            }
        }
    }

    if (rootNewUid >= 0) {
        targetWorld.selectedUid = rootNewUid;
    }
    return rootNewUid;
}

/**
 * Crea un nuevo Prefab en .procshit a partir del objeto seleccionado en el Level Editor.
 */
int prefabs_create_from_world_object(ref LevelWorld srcWorld, int selectedUid) {
    LevelObject* sel = level_world_find(srcWorld, selectedUid);
    if (sel is null) return -1;

    while (sel.parentUid >= 0) {
        LevelObject* p = level_world_find(srcWorld, sel.parentUid);
        if (p is null) break;
        sel = p;
    }

    prefabs_sync_active_from_world();

    int slot = -1;
    for (int i = 0; i < MAX_PREFABS; i++) {
        if (!prefabs[i].used) {
            slot = i;
            break;
        }
    }
    if (slot < 0) return -1;

    PrefabDef* pf = &prefabs[slot];
    *pf = PrefabDef.init;
    pf.used = true;
    strncpy(pf.name.ptr, sel.id.ptr, pf.name.length - 1);

    Vector2 anchor = Vector2(0.0f, 0.0f);
    if (sel.type == ObjectType.Box) {
        anchor = sel.box.position;
    } else if (sel.type == ObjectType.Sprite) {
        anchor = sel.sprite.position;
    } else {
        level_world_get_struct_anchor(srcWorld, sel.uid, anchor);
    }

    int objCount = 0;
    pf.objects[objCount] = *sel;
    pf.objects[objCount].uid = 1;
    pf.objects[objCount].parentUid = -1;
    pf.objects[objCount].sprite.texture = Texture2D.init;
    pf.objects[objCount].sprite.hasTexture = false;
    if (sel.type == ObjectType.Box) {
        pf.objects[objCount].box.position = Vector2(0.0f, 0.0f);
    } else if (sel.type == ObjectType.Sprite) {
        pf.objects[objCount].sprite.position = Vector2(0.0f, 0.0f);
    }
    objCount++;

    for (int i = 0; i < MAX_LEVEL_OBJECTS && objCount < MAX_PREFAB_OBJECTS; i++) {
        if (!srcWorld.objects[i].used || srcWorld.objects[i].parentUid != sel.uid) {
            continue;
        }
        pf.objects[objCount] = srcWorld.objects[i];
        pf.objects[objCount].uid = objCount + 1;
        pf.objects[objCount].parentUid = 1;
        pf.objects[objCount].sprite.texture = Texture2D.init;
        pf.objects[objCount].sprite.hasTexture = false;
        if (pf.objects[objCount].type == ObjectType.Box) {
            pf.objects[objCount].box.position.x -= anchor.x;
            pf.objects[objCount].box.position.y -= anchor.y;
        } else if (pf.objects[objCount].type == ObjectType.Sprite) {
            pf.objects[objCount].sprite.position.x -= anchor.x;
            pf.objects[objCount].sprite.position.y -= anchor.y;
        }
        objCount++;
    }

    prefabs_load_slot_into_world(slot);
    return slot;
}

/**
 * Actualiza la pestaña de Prefabs.
 */
void prefabs_tab_update(float dt) {
    // Mantener sincronizado el directorio base para resolver texturas relativas
    ref LevelWorld mainWorld = get_active_level_world();
    if (mainWorld.xmlDir[0] != '\0' && strcmp(prefab_world.xmlDir.ptr, mainWorld.xmlDir.ptr) != 0) {
        strncpy(prefab_world.xmlDir.ptr, mainWorld.xmlDir.ptr, prefab_world.xmlDir.length - 1);
        strncpy(prefab_world.resourcesRelPath.ptr, mainWorld.resourcesRelPath.ptr, prefab_world.resourcesRelPath.length - 1);
        level_world_reload_all_textures(prefab_world);
    }

    level_world_update_animations(prefab_world, dt);
    if (prefab_save_timer > 0.0f) {
        prefab_save_timer -= dt;
    }

    bool ctrlDown = IsKeyDown(KeyboardKey.KEY_LEFT_CONTROL) || IsKeyDown(KeyboardKey.KEY_RIGHT_CONTROL);
    if (ctrlDown && IsKeyPressed(KeyboardKey.KEY_S)) {
        gui_clear_active_field();
        prefabs_sync_active_from_world();
        ref ProcshitProject proj = get_active_project();
        if (proj.projectFilePath[0] != '\0') {
            if (procshit_save_project(proj.projectFilePath.ptr)) {
                prefab_save_timer = 2.0f;
            }
        }
        return;
    }

    if (!ctrlDown && !gui_is_editing_field()) {
        if (IsKeyPressed(KeyboardKey.KEY_Q)) {
            prefab_active_tool = 0;
            prefab_dragging_uid = -1;
            prefab_resizing_uid = -1;
            prefab_creating_box = false;
        } else if (IsKeyPressed(KeyboardKey.KEY_W)) {
            prefab_active_tool = 1;
            prefab_creating_box = false;
        } else if (IsKeyPressed(KeyboardKey.KEY_B)) {
            prefab_active_tool = 2;
            prefab_dragging_uid = -1;
            prefab_resizing_uid = -1;
        } else if (IsKeyPressed(KeyboardKey.KEY_DELETE) && prefab_world.selectedUid > 1) {
            level_world_remove_object(prefab_world, prefab_world.selectedUid);
            prefab_world.selectedUid = 1;
            prefabs_sync_active_from_world();
        }
    }

    prefabs_sync_active_from_world();
}

/**
 * Dibuja la pestaña de Prefabs:
 * - Izquierda: Catálogo de Prefabs en .procshit + Jerarquía interna del Prefab seleccionado.
 * - Centro: Viewport 2D centrado en (0, 0) para editar colisiones (Body/Area) y Sprite.
 * - Derecha: Inspector de propiedades completo (reutilizando inspector_panel_draw).
 */
void prefabs_tab_draw(Rectangle workspace) {
    float leftW  = 380.0f;
    float rightW = 420.0f;
    float pad    = 10.0f;

    Rectangle leftPanel = Rectangle(workspace.x + pad, workspace.y + pad, leftW, workspace.height - pad * 2.0f);
    Rectangle rightPanel = Rectangle(workspace.x + workspace.width - rightW - pad, workspace.y + pad, rightW, workspace.height - pad * 2.0f);
    Rectangle centerArea = Rectangle(
        leftPanel.x + leftPanel.width + pad,
        workspace.y + pad,
        workspace.width - leftW - rightW - pad * 4.0f,
        workspace.height - pad * 2.0f
    );

    draw_prefab_left_panel(leftPanel);
    draw_prefab_center_area(centerArea);

    // Temporalmente intercambiar el mundo global si el inspector recarga un PNG
    ref LevelWorld mainWorld = get_active_level_world();
    char[512] savedDir = mainWorld.xmlDir;
    char[256] savedRes = mainWorld.resourcesRelPath;
    if (mainWorld.xmlDir[0] == '\0') {
        snprintf(mainWorld.xmlDir.ptr, mainWorld.xmlDir.length, "source/combo_6beat/resources/scenes");
        snprintf(mainWorld.resourcesRelPath.ptr, mainWorld.resourcesRelPath.length, "../");
    }
    inspector_panel_draw(rightPanel, prefab_world);
    mainWorld.xmlDir = savedDir;
    mainWorld.resourcesRelPath = savedRes;
}

/**
 * Serializa todos los Prefabs dentro del archivo .procshit.
 */
void prefabs_write_to_procshit(FILE* fp) {
    prefabs_sync_active_from_world();

    fprintf(fp, "\n# Prefabs de Objetos del Proyecto\n");
    for (int p = 0; p < MAX_PREFABS; p++) {
        if (!prefabs[p].used) continue;
        fprintf(fp, "PREFAB_DEF=%d,%s\n", p, prefabs[p].name.ptr);

        for (int i = 0; i < MAX_PREFAB_OBJECTS; i++) {
            if (!prefabs[p].objects[i].used) continue;
            const(LevelObject)* o = &prefabs[p].objects[i];
            fprintf(
                fp,
                "PREFAB_OBJ=%d,%d,%d,%d,%d,%s,%s,%s\n",
                p,
                i,
                o.uid,
                o.parentUid,
                cast(int) o.type,
                o.id.ptr,
                o.xmlTag.ptr,
                o.group.ptr
            );

            if (o.type == ObjectType.Box) {
                fprintf(
                    fp,
                    "PREFAB_BOX=%d,%d,%.1f,%.1f,%.1f,%.1f,%d,%d,%d\n",
                    p,
                    i,
                    o.box.position.x,
                    o.box.position.y,
                    o.box.size.x,
                    o.box.size.y,
                    o.box.layer,
                    o.box.mask,
                    o.box.disabled ? 1 : 0
                );
            } else if (o.type == ObjectType.Sprite) {
                fprintf(
                    fp,
                    "PREFAB_SPRITE=%d,%d,%.1f,%.1f,%.2f,%.2f,%.2f,%d,%d,%d,%.2f,%d,%d,%s\n",
                    p,
                    i,
                    o.sprite.position.x,
                    o.sprite.position.y,
                    o.sprite.scale.x,
                    o.sprite.scale.y,
                    o.sprite.rotation,
                    o.sprite.zIndex,
                    o.sprite.rows,
                    o.sprite.cols,
                    o.sprite.delay,
                    o.sprite.visible ? 1 : 0,
                    o.sprite.centerPosition ? 1 : 0,
                    o.sprite.resourcePath.ptr
                );
            } else if (o.type == ObjectType.Struct) {
                for (int k = 0; k < MAX_CUSTOM_PROPS; k++) {
                    if (!o.customStruct.props[k].used) continue;
                    const(CustomProperty)* pr = &o.customStruct.props[k];
                    final switch (pr.type) {
                        case CustomValueType.Int:
                            fprintf(fp, "PREFAB_PROP=%d,%d,%d,%s,%d\n", p, i, cast(int) pr.type, pr.key.ptr, pr.intVal);
                            break;
                        case CustomValueType.Float:
                            fprintf(fp, "PREFAB_PROP=%d,%d,%d,%s,%.2f\n", p, i, cast(int) pr.type, pr.key.ptr, pr.floatVal);
                            break;
                        case CustomValueType.Bool:
                            fprintf(fp, "PREFAB_PROP=%d,%d,%d,%s,%d\n", p, i, cast(int) pr.type, pr.key.ptr, pr.boolVal ? 1 : 0);
                            break;
                        case CustomValueType.String:
                            fprintf(fp, "PREFAB_PROP=%d,%d,%d,%s,%s\n", p, i, cast(int) pr.type, pr.key.ptr, pr.stringVal.ptr);
                            break;
                        case CustomValueType.Vec2:
                            fprintf(fp, "PREFAB_PROP=%d,%d,%d,%s,%.2f\n", p, i, cast(int) pr.type, pr.key.ptr, pr.vec2Val.x);
                            break;
                    }
                }
            }
        }
    }
}

void prefabs_clear_all_for_load() {
    level_world_clear(prefab_world);
    for (int p = 0; p < MAX_PREFABS; p++) {
        prefabs[p] = PrefabDef.init;
    }
    active_prefab_idx = -1;
}

void prefabs_parse_procshit_line(const(char)* key, const(char)* val) {
    if (strcmp(key, "PREFAB_DEF") == 0) {
        const(char)* c1 = strchr(val, ',');
        if (c1 is null) return;
        int pIdx = atoi(val);
        if (pIdx < 0 || pIdx >= MAX_PREFABS) return;
        prefabs[pIdx] = PrefabDef.init;
        prefabs[pIdx].used = true;
        strncpy(prefabs[pIdx].name.ptr, c1 + 1, prefabs[pIdx].name.length - 1);
        if (active_prefab_idx < 0) active_prefab_idx = pIdx;
    } else if (strcmp(key, "PREFAB_OBJ") == 0) {
        int pIdx = 0, oIdx = 0, uid = 1, parentUid = -1, typeInt = 0;
        char[64] idStr = 0, tagStr = 0, grpStr = 0;
        if (!parse_prefab_obj_line(val, pIdx, oIdx, uid, parentUid, typeInt, idStr.ptr, tagStr.ptr, grpStr.ptr)) return;
        if (pIdx < 0 || pIdx >= MAX_PREFABS || oIdx < 0 || oIdx >= MAX_PREFAB_OBJECTS) return;
        LevelObject* o = &prefabs[pIdx].objects[oIdx];
        *o = LevelObject.init;
        o.used = true;
        o.uid = uid;
        o.parentUid = parentUid;
        o.type = cast(ObjectType) typeInt;
        o.expanded = true;
        strncpy(o.id.ptr, idStr.ptr, o.id.length - 1);
        strncpy(o.xmlTag.ptr, tagStr.ptr, o.xmlTag.length - 1);
        strncpy(o.group.ptr, grpStr.ptr, o.group.length - 1);
    } else if (strcmp(key, "PREFAB_BOX") == 0) {
        int pIdx = 0, oIdx = 0, ly = 1, mk = 1, dis = 0;
        float bx = 0, by = 0, bw = 32, bh = 32;
        if (!parse_prefab_box_line(val, pIdx, oIdx, bx, by, bw, bh, ly, mk, dis)) return;
        if (pIdx < 0 || pIdx >= MAX_PREFABS || oIdx < 0 || oIdx >= MAX_PREFAB_OBJECTS) return;
        BoxData* b = &prefabs[pIdx].objects[oIdx].box;
        b.position = Vector2(bx, by);
        b.size = Vector2(bw, bh);
        b.layer = ly;
        b.mask = mk;
        b.disabled = (dis != 0);
    } else if (strcmp(key, "PREFAB_SPRITE") == 0) {
        int pIdx = 0, oIdx = 0, zIdx = 0, rows = 1, cols = 1, vis = 1, ctr = 1;
        float px = 0, py = 0, sx = 1, sy = 1, rot = 0, dly = 0.1f;
        const(char)* pathPtr = null;
        if (!parse_prefab_sprite_line(val, pIdx, oIdx, px, py, sx, sy, rot, zIdx, rows, cols, dly, vis, ctr, &pathPtr)) return;
        if (pIdx < 0 || pIdx >= MAX_PREFABS || oIdx < 0 || oIdx >= MAX_PREFAB_OBJECTS) return;
        SpriteData* sp = &prefabs[pIdx].objects[oIdx].sprite;
        sp.position = Vector2(px, py);
        sp.scale = Vector2(sx, sy);
        sp.rotation = rot;
        sp.zIndex = zIdx;
        sp.rows = rows;
        sp.cols = cols;
        sp.delay = dly;
        sp.visible = (vis != 0);
        sp.centerPosition = (ctr != 0);
        if (pathPtr !is null) {
            strncpy(sp.resourcePath.ptr, pathPtr, sp.resourcePath.length - 1);
        }
    } else if (strcmp(key, "PREFAB_PROP") == 0) {
        int pIdx = 0, oIdx = 0, pType = 0;
        char[64] pKey = 0;
        const(char)* pVal = null;
        if (!parse_prefab_prop_line(val, pIdx, oIdx, pType, pKey.ptr, &pVal)) return;
        if (pIdx < 0 || pIdx >= MAX_PREFABS || oIdx < 0 || oIdx >= MAX_PREFAB_OBJECTS) return;
        StructData* st = &prefabs[pIdx].objects[oIdx].customStruct;
        int idx = level_struct_add_custom_prop(*st, pKey.ptr, cast(CustomValueType) pType);
        if (idx >= 0 && pVal !is null) {
            if (pType == cast(int) CustomValueType.Int) {
                st.props[idx].intVal = atoi(pVal);
            } else if (pType == cast(int) CustomValueType.Float) {
                st.props[idx].floatVal = strtof(pVal, null);
            } else if (pType == cast(int) CustomValueType.Bool) {
                st.props[idx].boolVal = (atoi(pVal) != 0);
            } else {
                strncpy(st.props[idx].stringVal.ptr, pVal, st.props[idx].stringVal.length - 1);
            }
        }
    }
}

void prefabs_finalize_after_load() {
    if (prefabs_get_count() == 0) {
        prefabs_reset_to_defaults();
        return;
    }
    if (active_prefab_idx < 0 || !prefabs[active_prefab_idx].used) {
        for (int i = 0; i < MAX_PREFABS; i++) {
            if (prefabs[i].used) {
                active_prefab_idx = i;
                break;
            }
        }
    }
    if (active_prefab_idx >= 0) {
        prefabs_load_slot_into_world(active_prefab_idx);
    }
}

private:

__gshared PrefabDef[MAX_PREFABS] prefabs;
__gshared int active_prefab_idx = 0;
__gshared LevelWorld prefab_world;
__gshared EditorCamera prefab_camera;
__gshared int prefab_active_tool = 0;
__gshared bool prefab_snap_16px = false;
__gshared int prefab_dragging_uid = -1;
__gshared Vector2 prefab_drag_offset = Vector2(0, 0);
__gshared int prefab_resizing_uid = -1;
__gshared int prefab_resizing_handle = -1;
__gshared Rectangle prefab_resize_orig = Rectangle(0, 0, 0, 0);
__gshared Vector2 prefab_resize_start_mouse = Vector2(0, 0);
__gshared bool prefab_creating_box = false;
__gshared Vector2 prefab_box_start = Vector2(0, 0);
__gshared Vector2 prefab_box_end = Vector2(0, 0);
__gshared Vector2 prefab_list_scroll = Vector2(0, 0);
__gshared Vector2 prefab_tree_scroll = Vector2(0, 0);
__gshared float prefab_save_timer = 0.0f;

float snap16(float v) {
    return floorf((v + 8.0f) / 16.0f) * 16.0f;
}

void prefabs_reset_to_defaults() {
    for (int i = 0; i < MAX_PREFABS; i++) {
        prefabs[i] = PrefabDef.init;
    }

    // 0: Player
    create_default_prefab_from_preset(0, "player", 0);
    // 1: Enemigo Vampiro
    create_default_prefab_from_preset(1, "enemy_vampire", 1);
    // 2: NPC Guardián
    create_default_prefab_from_preset(2, "npc_guardian", 2);
    // 3: Cofre
    create_default_prefab_from_preset(3, "chest_item", 3);
    // 4: Portal / SceneChanger
    create_default_prefab_from_preset(4, "portal_scene", 4);

    prefabs_load_slot_into_world(0);
}

void create_default_prefab_from_preset(int slot, const(char)* name, int presetKind) {
    level_world_clear(prefab_world);
    snprintf(prefab_world.resourcesRelPath.ptr, prefab_world.resourcesRelPath.length, "../");
    int rootUid = -1;
    if (presetKind == 0) {
        rootUid = level_world_add_player_preset(prefab_world, Vector2(0.0f, 0.0f));
    } else if (presetKind == 1) {
        rootUid = level_world_add_enemy_preset(prefab_world, Vector2(0.0f, 0.0f));
    } else if (presetKind == 2) {
        rootUid = level_world_add_npc_preset(prefab_world, Vector2(0.0f, 0.0f));
    } else if (presetKind == 3) {
        rootUid = level_world_add_chest_preset(prefab_world, Vector2(0.0f, 0.0f));
    } else {
        rootUid = level_world_add_scene_changer_preset(prefab_world, Vector2(0.0f, 0.0f));
    }
    LevelObject* root = level_world_find(prefab_world, rootUid);
    if (root !is null) {
        strncpy(root.id.ptr, name, root.id.length - 1);
    }

    prefabs[slot] = PrefabDef.init;
    prefabs[slot].used = true;
    strncpy(prefabs[slot].name.ptr, name, prefabs[slot].name.length - 1);
    active_prefab_idx = slot;
    prefabs_sync_active_from_world();
}

int add_new_prefab_slot(const(char)* baseName, int presetKind) {
    prefabs_sync_active_from_world();
    for (int i = 0; i < MAX_PREFABS; i++) {
        if (!prefabs[i].used) {
            char[64] newName;
            snprintf(newName.ptr, newName.length, "%s_%d", baseName, i + 1);
            create_default_prefab_from_preset(i, newName.ptr, presetKind);
            prefabs_load_slot_into_world(i);
            return i;
        }
    }
    return -1;
}

void remove_prefab_slot(int idx) {
    if (idx < 0 || idx >= MAX_PREFABS || !prefabs[idx].used) return;
    if (prefabs_get_count() <= 1) return; // Mantener al menos 1 prefab

    for (int i = idx; i < MAX_PREFABS - 1; i++) {
        prefabs[i] = prefabs[i + 1];
    }
    prefabs[MAX_PREFABS - 1] = PrefabDef.init;

    if (active_prefab_idx >= prefabs_get_count()) {
        active_prefab_idx = prefabs_get_count() - 1;
    }
    if (active_prefab_idx < 0) active_prefab_idx = 0;
    prefabs_load_slot_into_world(active_prefab_idx);
}

void draw_prefab_left_panel(Rectangle bounds) {
    GuiPanel(bounds, "Prefabs en .procshit (Objetos Reutilizables)");

    float pad = 10.0f;
    float x = bounds.x + pad;
    float y = bounds.y + 32.0f;
    float w = bounds.width - pad * 2.0f;

    GuiLabel(Rectangle(x, y, w, 20.0f), "Crear Nuevo Prefab en .procshit:");
    y += 22.0f;

    float btnW = (w - 16.0f) / 5.0f;
    if (GuiButton(Rectangle(x, y, btnW, 24.0f), "+Player")) {
        add_new_prefab_slot("player", 0);
    }
    if (GuiButton(Rectangle(x + (btnW + 4.0f) * 1.0f, y, btnW, 24.0f), "+Enemigo")) {
        add_new_prefab_slot("enemy", 1);
    }
    if (GuiButton(Rectangle(x + (btnW + 4.0f) * 2.0f, y, btnW, 24.0f), "+NPC")) {
        add_new_prefab_slot("npc", 2);
    }
    if (GuiButton(Rectangle(x + (btnW + 4.0f) * 3.0f, y, btnW, 24.0f), "+Cofre")) {
        add_new_prefab_slot("chest", 3);
    }
    if (GuiButton(Rectangle(x + (btnW + 4.0f) * 4.0f, y, btnW, 24.0f), "+Portal")) {
        add_new_prefab_slot("portal", 4);
    }
    y += 30.0f;

    // 1. Lista de Prefabs guardados en el proyecto
    float listH = (bounds.height - 130.0f) * 0.45f;
    int pfCount = prefabs_get_count();
    Rectangle listBounds = Rectangle(x, y, w, listH);
    Rectangle listContent = Rectangle(0, 0, w - 20.0f, (pfCount > 0 ? pfCount : 1) * 30.0f + 8.0f);
    Rectangle listView = Rectangle(0, 0, 0, 0);

    GuiScrollPanel(listBounds, "Catalogo de Prefabs (.procshit)", listContent, &prefab_list_scroll, &listView);
    BeginScissorMode(cast(int) listView.x, cast(int) listView.y, cast(int) listView.width, cast(int) listView.height);

    int delPrefabIdx = -1;
    float rowY = listView.y + 4.0f + prefab_list_scroll.y;
    for (int i = 0; i < MAX_PREFABS; i++) {
        if (!prefabs[i].used) continue;
        bool isSel = (i == active_prefab_idx);
        Rectangle rowRect = Rectangle(listView.x + 4.0f, rowY, listView.width - 36.0f, 26.0f);
        Rectangle delRect = Rectangle(listView.x + listView.width - 28.0f, rowY, 24.0f, 26.0f);

        if (isSel) {
            DrawRectangleRec(rowRect, Color(45, 110, 185, 180));
        }

        const(char)* tag = prefabs_get_xml_tag(i);
        const(char)* pfx = isSel ? "> " : "";
        char[128] rowLabel;
        snprintf(rowLabel.ptr, rowLabel.length, "%s[%s] %s", pfx, tag, prefabs[i].name.ptr);
        if (GuiButton(rowRect, rowLabel.ptr)) {
            prefabs_set_active_index(i);
        }
        if (gui_trash_button(delRect)) {
            delPrefabIdx = i;
        }
        rowY += 30.0f;
    }
    EndScissorMode();

    if (delPrefabIdx >= 0) {
        remove_prefab_slot(delPrefabIdx);
    }

    y += listH + 10.0f;

    // 2. Nodos hijos del Prefab activo (Raíz + Body + Area + Sprite)
    GuiLabel(Rectangle(x, y, w, 20.0f), "Componentes del Prefab activo:");
    y += 22.0f;

    float compW = (w - 8.0f) / 3.0f;
    if (GuiButton(Rectangle(x, y, compW, 24.0f), "+ Body (Box)")) {
        int uid = level_world_add_object(prefab_world, ObjectType.Box, 1);
        LevelObject* c = level_world_find(prefab_world, uid);
        if (c !is null) {
            snprintf(c.id.ptr, c.id.length, "body_%d", uid);
            snprintf(c.xmlTag.ptr, c.xmlTag.length, "Body");
            c.box.position = Vector2(0.0f, 0.0f);
            c.box.size = Vector2(24.0f, 24.0f);
        }
        prefabs_sync_active_from_world();
    }
    if (GuiButton(Rectangle(x + compW + 4.0f, y, compW, 24.0f), "+ Area (Trigger)")) {
        int uid = level_world_add_object(prefab_world, ObjectType.Box, 1);
        LevelObject* c = level_world_find(prefab_world, uid);
        if (c !is null) {
            snprintf(c.id.ptr, c.id.length, "area_%d", uid);
            snprintf(c.xmlTag.ptr, c.xmlTag.length, "Area");
            c.box.position = Vector2(-6.0f, -6.0f);
            c.box.size = Vector2(36.0f, 36.0f);
            c.box.layer = 0;
            c.box.mask = 2;
        }
        prefabs_sync_active_from_world();
    }
    if (GuiButton(Rectangle(x + (compW + 4.0f) * 2.0f, y, compW, 24.0f), "+ Sprite")) {
        int uid = level_world_add_object(prefab_world, ObjectType.Sprite, 1);
        LevelObject* c = level_world_find(prefab_world, uid);
        if (c !is null) {
            snprintf(c.id.ptr, c.id.length, "sprite_%d", uid);
            c.sprite.position = Vector2(12.0f, 12.0f);
            c.sprite.centerPosition = true;
            c.sprite.zIndex = 150;
        }
        prefabs_sync_active_from_world();
    }
    y += 28.0f;

    float treeH = (bounds.y + bounds.height) - y - pad;
    int objCount = 0;
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (prefab_world.objects[i].used) objCount++;
    }

    Rectangle treeBounds = Rectangle(x, y, w, treeH);
    Rectangle treeContent = Rectangle(0, 0, w - 20.0f, (objCount > 0 ? objCount : 1) * 28.0f + 8.0f);
    Rectangle treeView = Rectangle(0, 0, 0, 0);
    GuiScrollPanel(treeBounds, "Jerarquia del Prefab", treeContent, &prefab_tree_scroll, &treeView);

    BeginScissorMode(cast(int) treeView.x, cast(int) treeView.y, cast(int) treeView.width, cast(int) treeView.height);
    float ty = treeView.y + 4.0f + prefab_tree_scroll.y;
    int delChildUid = -1;

    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!prefab_world.objects[i].used) continue;
        LevelObject* obj = &prefab_world.objects[i];
        bool isRoot = (obj.parentUid == -1);
        float indent = isRoot ? 4.0f : 22.0f;
        bool isSel = (obj.uid == prefab_world.selectedUid);

        Rectangle itemRect = Rectangle(treeView.x + indent, ty, treeView.width - indent - (isRoot ? 6.0f : 32.0f), 24.0f);
        if (isSel) {
            DrawRectangleRec(itemRect, Color(55, 120, 95, 190));
        }

        const(char)* tag = (obj.xmlTag[0] != '\0') ? obj.xmlTag.ptr : level_object_type_label(obj.type);
        char[128] itemLbl;
        snprintf(itemLbl.ptr, itemLbl.length, "[%s] %s", tag, obj.id.ptr);
        if (GuiButton(itemRect, itemLbl.ptr)) {
            prefab_world.selectedUid = obj.uid;
        }
        if (!isRoot) {
            Rectangle dBtn = Rectangle(treeView.x + treeView.width - 26.0f, ty, 22.0f, 24.0f);
            if (gui_trash_button(dBtn)) {
                delChildUid = obj.uid;
            }
        }
        ty += 28.0f;
    }
    EndScissorMode();

    if (delChildUid >= 0) {
        level_world_remove_object(prefab_world, delChildUid);
        prefab_world.selectedUid = 1;
        prefabs_sync_active_from_world();
    }
}

void draw_prefab_center_area(Rectangle bounds) {
    float barH = 42.0f;
    Rectangle barBounds = Rectangle(bounds.x, bounds.y, bounds.width, barH);
    Rectangle viewBounds = Rectangle(bounds.x, bounds.y + barH + 6.0f, bounds.width, bounds.height - barH - 6.0f);
    Rectangle innerView = Rectangle(viewBounds.x + 2.0f, viewBounds.y + 24.0f, viewBounds.width - 4.0f, viewBounds.height - 26.0f);

    if (!prefab_camera.initialized) {
        editor_camera_reset(prefab_camera, innerView);
        prefab_camera.camera.zoom = 3.0f;
    }

    GuiPanel(barBounds, null);
    float x = barBounds.x + 8.0f;
    float y = barBounds.y + 8.0f;
    float h = 26.0f;

    GuiToggleGroup(Rectangle(x, y, 72.0f, h), "[Q] Sel;[W] Mov;[B] +Box", &prefab_active_tool);
    x += 224.0f;

    GuiToggle(Rectangle(x, y, 76.0f, h), GuiIconText(GuiIconName.ICON_GRID, "16px"), &prefab_snap_16px);
    x += 84.0f;

    if (GuiButton(Rectangle(x, y, 165.0f, h), GuiIconText(GuiIconName.ICON_FILE_SAVE, "Guardar en .procshit"))) {
        gui_clear_active_field();
        prefabs_sync_active_from_world();
        ref ProcshitProject proj = get_active_project();
        if (proj.projectFilePath[0] != '\0') {
            if (procshit_save_project(proj.projectFilePath.ptr)) {
                prefab_save_timer = 2.0f;
            }
        }
    }
    x += 172.0f;

    if (GuiButton(Rectangle(x, y, 80.0f, h), GuiIconText(GuiIconName.ICON_ZOOM_CENTER, "(0,0)"))) {
        editor_camera_reset(prefab_camera, innerView);
        prefab_camera.camera.zoom = 3.0f;
    }

    GuiPanel(viewBounds, "Vista 2D del Prefab en (0, 0) - Edita su Hitbox (Body), Area de Interaccion y Sprite");
    editor_camera_update(prefab_camera, innerView);
    handle_prefab_viewport_interaction(innerView);

    BeginScissorMode(cast(int) innerView.x, cast(int) innerView.y, cast(int) innerView.width, cast(int) innerView.height);
    DrawRectangleRec(innerView, Color(26, 29, 35, 255));

    BeginMode2D(prefab_camera.camera);
    editor_camera_draw_world_grid(prefab_camera, innerView);

    // 1. Dibujar Sprites del Prefab
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!prefab_world.objects[i].used || prefab_world.objects[i].type != ObjectType.Sprite) continue;
        draw_prefab_sprite(prefab_world.objects[i], prefab_world.objects[i].uid == prefab_world.selectedUid);
    }

    // 2. Dibujar Boxes (Body / Area) con tiradores de resize
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!prefab_world.objects[i].used || prefab_world.objects[i].type != ObjectType.Box) continue;
        bool isSel = (prefab_world.objects[i].uid == prefab_world.selectedUid);
        draw_prefab_box(prefab_world.objects[i], isSel);
    }

    if (prefab_creating_box) {
        float x0 = (prefab_box_start.x < prefab_box_end.x) ? prefab_box_start.x : prefab_box_end.x;
        float y0 = (prefab_box_start.y < prefab_box_end.y) ? prefab_box_start.y : prefab_box_end.y;
        float rw = ((prefab_box_start.x > prefab_box_end.x) ? prefab_box_start.x : prefab_box_end.x) - x0;
        float rh = ((prefab_box_start.y > prefab_box_end.y) ? prefab_box_start.y : prefab_box_end.y) - y0;
        if (rw < 4.0f) rw = 4.0f;
        if (rh < 4.0f) rh = 4.0f;
        Rectangle pr = Rectangle(x0, y0, rw, rh);
        DrawRectangleRec(pr, Color(80, 255, 130, 80));
        DrawRectangleLinesEx(pr, 2.0f / prefab_camera.camera.zoom, Color(80, 255, 130, 255));
    }

    // Cruz de origen (0, 0) del Prefab
    DrawCircleV(Vector2(0, 0), 3.0f / prefab_camera.camera.zoom, Colors.YELLOW);

    EndMode2D();

    if (prefab_save_timer > 0.0f) {
        DrawRectangle(cast(int) innerView.x + 8, cast(int) innerView.y + 8, 280, 26, Color(35, 130, 60, 230));
        DrawText("Prefabs guardados en .procshit", cast(int) innerView.x + 16, cast(int) innerView.y + 14, 14, Colors.RAYWHITE);
    }

    EndScissorMode();
}

void draw_prefab_sprite(ref const(LevelObject) obj, bool isSel) {
    int cols = (obj.sprite.cols > 0) ? obj.sprite.cols : 1;
    int rows = (obj.sprite.rows > 0) ? obj.sprite.rows : 1;
    float posX = obj.sprite.position.x;
    float posY = obj.sprite.position.y;

    if (obj.sprite.hasTexture && obj.sprite.texture.id != 0) {
        float fw = cast(float) obj.sprite.texture.width / cols;
        float fh = cast(float) obj.sprite.texture.height / rows;
        int fc = obj.sprite.currentFrame % cols;
        int fr = obj.sprite.currentFrame / cols;
        Rectangle src = Rectangle(fc * fw, fr * fh, fw, fh);
        float dw = fw * obj.sprite.scale.x;
        float dh = fh * obj.sprite.scale.y;
        Rectangle dst = Rectangle(posX, posY, dw, dh);
        Vector2 piv = obj.sprite.centerPosition ? Vector2(dw * 0.5f, dh * 0.5f) : Vector2(0, 0);
        DrawTexturePro(obj.sprite.texture, src, dst, piv, obj.sprite.rotation, Colors.WHITE);
    } else {
        float bw = 32.0f * obj.sprite.scale.x;
        float bh = 32.0f * obj.sprite.scale.y;
        Rectangle dst = Rectangle(posX, posY, bw, bh);
        Vector2 piv = obj.sprite.centerPosition ? Vector2(bw * 0.5f, bh * 0.5f) : Vector2(0, 0);
        DrawRectanglePro(dst, piv, obj.sprite.rotation, Color(150, 95, 230, 120));
    }
    if (isSel) {
        DrawCircleV(Vector2(posX, posY), 3.0f / prefab_camera.camera.zoom, Colors.YELLOW);
    }
}

Rectangle get_prefab_box_handle(ref const(BoxData) box, int hIdx) {
    float hs = 8.0f / prefab_camera.camera.zoom;
    if (hs < 2.5f) hs = 2.5f;
    float x = box.position.x, y = box.position.y, w = box.size.x, h = box.size.y;
    float cx = x + w * 0.5f, cy = y + h * 0.5f;
    Vector2 pt = Vector2(x, y);
    switch (hIdx) {
        case 0: pt = Vector2(x, y); break;
        case 1: pt = Vector2(cx, y); break;
        case 2: pt = Vector2(x + w, y); break;
        case 3: pt = Vector2(x + w, cy); break;
        case 4: pt = Vector2(x + w, y + h); break;
        case 5: pt = Vector2(cx, y + h); break;
        case 6: pt = Vector2(x, y + h); break;
        case 7: pt = Vector2(x, cy); break;
        default: break;
    }
    return Rectangle(pt.x - hs * 0.5f, pt.y - hs * 0.5f, hs, hs);
}

void draw_prefab_box(ref const(LevelObject) obj, bool isSel) {
    Rectangle r = Rectangle(obj.box.position.x, obj.box.position.y, obj.box.size.x, obj.box.size.y);
    bool isArea = (strcmp(obj.xmlTag.ptr, "Area") == 0);
    Color fill = isArea ? Color(255, 210, 60, 55) : Color(60, 180, 255, 80);
    Color line = isSel ? Colors.YELLOW : (isArea ? Color(255, 220, 80, 230) : Color(80, 200, 255, 235));
    DrawRectangleRec(r, fill);
    DrawRectangleLinesEx(r, (isSel ? 2.2f : 1.4f) / prefab_camera.camera.zoom, line);

    if (isSel) {
        for (int h = 0; h < 8; h++) {
            Rectangle hr = get_prefab_box_handle(obj.box, h);
            DrawRectangleRec(hr, Color(255, 230, 50, 255));
        }
    }
}

void handle_prefab_viewport_interaction(Rectangle viewArea) {
    Vector2 mouseScreen = GetMousePosition();
    bool inside = CheckCollisionPointRec(mouseScreen, viewArea);
    Vector2 mouseWorld = editor_camera_screen_to_world(prefab_camera, mouseScreen);

    if (IsMouseButtonReleased(MouseButton.MOUSE_BUTTON_LEFT)) {
        if (prefab_creating_box) {
            prefab_creating_box = false;
            float x0 = (prefab_box_start.x < prefab_box_end.x) ? prefab_box_start.x : prefab_box_end.x;
            float y0 = (prefab_box_start.y < prefab_box_end.y) ? prefab_box_start.y : prefab_box_end.y;
            float rw = ((prefab_box_start.x > prefab_box_end.x) ? prefab_box_start.x : prefab_box_end.x) - x0;
            float rh = ((prefab_box_start.y > prefab_box_end.y) ? prefab_box_start.y : prefab_box_end.y) - y0;
            if (rw >= 4.0f && rh >= 4.0f) {
                int uid = level_world_add_object(prefab_world, ObjectType.Box, 1);
                LevelObject* c = level_world_find(prefab_world, uid);
                if (c !is null) {
                    snprintf(c.id.ptr, c.id.length, "body_%d", uid);
                    snprintf(c.xmlTag.ptr, c.xmlTag.length, "Body");
                    c.box.position = Vector2(x0, y0);
                    c.box.size = Vector2(rw, rh);
                    prefab_world.selectedUid = uid;
                }
            }
        }
        prefab_dragging_uid = -1;
        prefab_resizing_uid = -1;
        prefab_resizing_handle = -1;
        prefabs_sync_active_from_world();
    }

    if (prefab_creating_box && IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT)) {
        prefab_box_end = prefab_snap_16px ? Vector2(snap16(mouseWorld.x), snap16(mouseWorld.y)) : mouseWorld;
        return;
    }

    if (prefab_resizing_uid >= 0 && prefab_resizing_handle >= 0 && IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT)) {
        LevelObject* ro = level_world_find(prefab_world, prefab_resizing_uid);
        if (ro !is null && ro.type == ObjectType.Box) {
            float x0 = prefab_resize_orig.x;
            float y0 = prefab_resize_orig.y;
            float x1 = x0 + prefab_resize_orig.width;
            float y1 = y0 + prefab_resize_orig.height;
            float dx = mouseWorld.x - prefab_resize_start_mouse.x;
            float dy = mouseWorld.y - prefab_resize_start_mouse.y;
            int h = prefab_resizing_handle;
            if (h == 0 || h == 6 || h == 7) { x0 += dx; if (prefab_snap_16px) x0 = snap16(x0); if (x0 > x1 - 4.0f) x0 = x1 - 4.0f; }
            if (h == 2 || h == 3 || h == 4) { x1 += dx; if (prefab_snap_16px) x1 = snap16(x1); if (x1 < x0 + 4.0f) x1 = x0 + 4.0f; }
            if (h == 0 || h == 1 || h == 2) { y0 += dy; if (prefab_snap_16px) y0 = snap16(y0); if (y0 > y1 - 4.0f) y0 = y1 - 4.0f; }
            if (h == 4 || h == 5 || h == 6) { y1 += dy; if (prefab_snap_16px) y1 = snap16(y1); if (y1 < y0 + 4.0f) y1 = y0 + 4.0f; }
            ro.box.position = Vector2(x0, y0);
            ro.box.size = Vector2(x1 - x0, y1 - y0);
        }
        return;
    }

    if (prefab_active_tool == 1 && prefab_dragging_uid >= 0 && IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT)) {
        LevelObject* dr = level_world_find(prefab_world, prefab_dragging_uid);
        if (dr !is null) {
            float tx = mouseWorld.x - prefab_drag_offset.x;
            float ty = mouseWorld.y - prefab_drag_offset.y;
            if (prefab_snap_16px) { tx = snap16(tx); ty = snap16(ty); }
            if (dr.type == ObjectType.Box) dr.box.position = Vector2(tx, ty);
            else if (dr.type == ObjectType.Sprite) dr.sprite.position = Vector2(tx, ty);
        }
        return;
    }

    if (!inside || IsKeyDown(KeyboardKey.KEY_SPACE)) return;

    if (IsMouseButtonPressed(MouseButton.MOUSE_BUTTON_LEFT)) {
        if (prefab_active_tool == 2) {
            prefab_creating_box = true;
            prefab_box_start = prefab_snap_16px ? Vector2(snap16(mouseWorld.x), snap16(mouseWorld.y)) : mouseWorld;
            prefab_box_end = Vector2(prefab_box_start.x + 16.0f, prefab_box_start.y + 16.0f);
            return;
        }

        // Chequear tiradores de resize de la Box seleccionada
        LevelObject* curSel = level_world_find(prefab_world, prefab_world.selectedUid);
        if (curSel !is null && curSel.type == ObjectType.Box) {
            for (int h = 0; h < 8; h++) {
                Rectangle hr = get_prefab_box_handle(curSel.box, h);
                if (CheckCollisionPointRec(mouseWorld, hr)) {
                    prefab_resizing_uid = curSel.uid;
                    prefab_resizing_handle = h;
                    prefab_resize_orig = Rectangle(curSel.box.position.x, curSel.box.position.y, curSel.box.size.x, curSel.box.size.y);
                    prefab_resize_start_mouse = mouseWorld;
                    return;
                }
            }
        }

        // Hit-test Boxes primero, luego Sprites
        for (int i = MAX_LEVEL_OBJECTS - 1; i >= 0; i--) {
            if (!prefab_world.objects[i].used || prefab_world.objects[i].type != ObjectType.Box) continue;
            Rectangle br = Rectangle(prefab_world.objects[i].box.position.x, prefab_world.objects[i].box.position.y, prefab_world.objects[i].box.size.x, prefab_world.objects[i].box.size.y);
            if (CheckCollisionPointRec(mouseWorld, br)) {
                prefab_world.selectedUid = prefab_world.objects[i].uid;
                if (prefab_active_tool == 1) {
                    prefab_dragging_uid = prefab_world.objects[i].uid;
                    prefab_drag_offset = Vector2(mouseWorld.x - br.x, mouseWorld.y - br.y);
                }
                return;
            }
        }
    }
}

bool parse_prefab_obj_line(
    const(char)* s,
    out int pIdx,
    out int oIdx,
    out int uid,
    out int parentUid,
    out int typeInt,
    char* outId,
    char* outTag,
    char* outGrp
) {
    pIdx = oIdx = uid = parentUid = typeInt = 0;
    const(char)* c1 = strchr(s, ','); if (c1 is null) return false;
    const(char)* c2 = strchr(c1 + 1, ','); if (c2 is null) return false;
    const(char)* c3 = strchr(c2 + 1, ','); if (c3 is null) return false;
    const(char)* c4 = strchr(c3 + 1, ','); if (c4 is null) return false;
    const(char)* c5 = strchr(c4 + 1, ','); if (c5 is null) return false;
    const(char)* c6 = strchr(c5 + 1, ','); if (c6 is null) return false;
    const(char)* c7 = strchr(c6 + 1, ','); if (c7 is null) return false;

    pIdx = atoi(s);
    oIdx = atoi(c1 + 1);
    uid = atoi(c2 + 1);
    parentUid = atoi(c3 + 1);
    typeInt = atoi(c4 + 1);

    size_t l1 = cast(size_t)(c6 - (c5 + 1));
    if (l1 > 63) l1 = 63;
    strncpy(outId, c5 + 1, l1); outId[l1] = '\0';

    size_t l2 = cast(size_t)(c7 - (c6 + 1));
    if (l2 > 63) l2 = 63;
    strncpy(outTag, c6 + 1, l2); outTag[l2] = '\0';

    strncpy(outGrp, c7 + 1, 63); outGrp[63] = '\0';
    return true;
}

bool parse_prefab_box_line(
    const(char)* s,
    out int pIdx,
    out int oIdx,
    out float bx,
    out float by,
    out float bw,
    out float bh,
    out int ly,
    out int mk,
    out int dis
) {
    pIdx = oIdx = ly = mk = dis = 0;
    bx = by = bw = bh = 0.0f;
    const(char)* c1 = strchr(s, ','); if (c1 is null) return false;
    const(char)* c2 = strchr(c1 + 1, ','); if (c2 is null) return false;
    const(char)* c3 = strchr(c2 + 1, ','); if (c3 is null) return false;
    const(char)* c4 = strchr(c3 + 1, ','); if (c4 is null) return false;
    const(char)* c5 = strchr(c4 + 1, ','); if (c5 is null) return false;
    const(char)* c6 = strchr(c5 + 1, ','); if (c6 is null) return false;
    const(char)* c7 = strchr(c6 + 1, ','); if (c7 is null) return false;
    const(char)* c8 = strchr(c7 + 1, ','); if (c8 is null) return false;

    pIdx = atoi(s);
    oIdx = atoi(c1 + 1);
    bx = strtof(c2 + 1, null);
    by = strtof(c3 + 1, null);
    bw = strtof(c4 + 1, null);
    bh = strtof(c5 + 1, null);
    ly = atoi(c6 + 1);
    mk = atoi(c7 + 1);
    dis = atoi(c8 + 1);
    return true;
}

bool parse_prefab_sprite_line(
    const(char)* s,
    out int pIdx,
    out int oIdx,
    out float px,
    out float py,
    out float sx,
    out float sy,
    out float rot,
    out int zIdx,
    out int rows,
    out int cols,
    out float dly,
    out int vis,
    out int ctr,
    const(char)** outPath
) {
    pIdx = oIdx = zIdx = rows = cols = vis = ctr = 0;
    px = py = sx = sy = rot = dly = 0.0f;
    *outPath = "";
    const(char)*[13] c;
    const(char)* cur = s;
    for (int i = 0; i < 13; i++) {
        c[i] = strchr(cur, ',');
        if (c[i] is null) return false;
        cur = c[i] + 1;
    }
    pIdx = atoi(s);
    oIdx = atoi(c[0] + 1);
    px = strtof(c[1] + 1, null);
    py = strtof(c[2] + 1, null);
    sx = strtof(c[3] + 1, null);
    sy = strtof(c[4] + 1, null);
    rot = strtof(c[5] + 1, null);
    zIdx = atoi(c[6] + 1);
    rows = atoi(c[7] + 1);
    cols = atoi(c[8] + 1);
    dly = strtof(c[9] + 1, null);
    vis = atoi(c[10] + 1);
    ctr = atoi(c[11] + 1);
    *outPath = c[12] + 1;
    return true;
}

bool parse_prefab_prop_line(
    const(char)* s,
    out int pIdx,
    out int oIdx,
    out int pType,
    char* outKey,
    const(char)** outVal
) {
    pIdx = oIdx = pType = 0;
    *outVal = "";
    const(char)* c1 = strchr(s, ','); if (c1 is null) return false;
    const(char)* c2 = strchr(c1 + 1, ','); if (c2 is null) return false;
    const(char)* c3 = strchr(c2 + 1, ','); if (c3 is null) return false;
    const(char)* c4 = strchr(c3 + 1, ','); if (c4 is null) return false;

    pIdx = atoi(s);
    oIdx = atoi(c1 + 1);
    pType = atoi(c2 + 1);
    size_t kl = cast(size_t)(c4 - (c3 + 1));
    if (kl > 63) kl = 63;
    strncpy(outKey, c3 + 1, kl);
    outKey[kl] = '\0';
    *outVal = c4 + 1;
    return true;
}
