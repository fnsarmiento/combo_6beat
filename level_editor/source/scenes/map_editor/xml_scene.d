module scenes.map_editor.xml_scene;

nothrow:
@nogc:

import raylib : FileExists;
import core.stdc.stdio : FILE, fopen, fclose, fseek, ftell, fread, fprintf, SEEK_END, SEEK_SET, snprintf;
import core.stdc.stdlib : malloc, free, atoi, strtof;
import core.stdc.string : strlen, strncpy, strcmp, strncmp;
import engine.path_utils : path_get_dir;
import scenes.map_editor.level_data;
import scenes.map_editor.tabs.tileset_tab :
    tileset_get_path,
    tileset_get_tile_width_px,
    tileset_get_tile_height_px,
    tileset_get_tile_count,
    tileset_set_tile_size_px,
    tileset_set_tile_count,
    tileset_set_path_and_reload,
    tileset_reload_texture;
import scenes.map_editor.tabs.tilemap_tab :
    tilemap_get_width,
    tilemap_get_height,
    tilemap_set_size,
    tilemap_get_cell,
    tilemap_set_cell,
    tilemap_clear;

public:

/**
 * Carga una escena desde un archivo XML (ej. debug_scene.xml) hacia el LevelWorld.
 * Resuelve todos los recursos de forma relativa al XML y a <Map resources="...">.
 */
bool xml_load_scene(const(char)* filePath, ref LevelWorld world) {
    if (filePath is null || filePath[0] == '\0' || !FileExists(filePath)) {
        return false;
    }

    FILE* fp = fopen(filePath, "rb");
    if (fp is null) {
        return false;
    }

    fseek(fp, 0, SEEK_END);
    long fileSize = ftell(fp);
    fseek(fp, 0, SEEK_SET);

    if (fileSize <= 0 || fileSize > 16 * 1024 * 1024) {
        fclose(fp);
        return false;
    }

    char* buffer = cast(char*) malloc(cast(size_t) fileSize + 1);
    if (buffer is null) {
        fclose(fp);
        return false;
    }

    size_t bytesRead = fread(buffer, 1, cast(size_t) fileSize, fp);
    buffer[bytesRead] = '\0';
    fclose(fp);

    // Reiniciar el mundo y guardar el contexto de rutas del XML cargado
    level_world_clear(world);
    strncpy(world.xmlFilePath.ptr, filePath, world.xmlFilePath.length - 1);
    world.xmlFilePath[world.xmlFilePath.length - 1] = '\0';
    path_get_dir(filePath, world.xmlDir.ptr, world.xmlDir.length);
    snprintf(world.resourcesRelPath.ptr, world.resourcesRelPath.length, "../");

    parse_xml_document(buffer, bytesRead, world);

    free(buffer);

    // Recargar también el tileset con el nuevo contexto relativo si tenía ruta
    tileset_reload_texture(false);

    // Seleccionar el primer objeto cargado si existe
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (world.objects[i].used) {
            world.selectedUid = world.objects[i].uid;
            break;
        }
    }

    return true;
}

/**
 * Guarda la escena actual en un archivo XML usando rutas exclusivamente relativas.
 */
bool xml_save_scene(const(char)* filePath, ref LevelWorld world) {
    if (filePath is null || filePath[0] == '\0') {
        return false;
    }

    FILE* fp = fopen(filePath, "wb");
    if (fp is null) {
        return false;
    }

    strncpy(world.xmlFilePath.ptr, filePath, world.xmlFilePath.length - 1);
    world.xmlFilePath[world.xmlFilePath.length - 1] = '\0';
    path_get_dir(filePath, world.xmlDir.ptr, world.xmlDir.length);

    const(char)* resRel = (world.resourcesRelPath[0] != '\0') ? world.resourcesRelPath.ptr : "../";
    fprintf(fp, "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
    fprintf(fp, "<Map resources=\"%s\">\n\n", resRel);

    // Si hay un Tileset con PNG cargado, también guardarlo de forma relativa
    const(char)* tsPath = tileset_get_path();
    if (tsPath !is null && tsPath[0] != '\0') {
        fprintf(
            fp,
            "    <Tileset path=\"%s\" tile-width=\"%d\" tile-height=\"%d\" tile-count=\"%d\" />\n\n",
            tsPath,
            tileset_get_tile_width_px(),
            tileset_get_tile_height_px(),
            tileset_get_tile_count()
        );
    }

    // Exportar capa del Tilemap si tiene al menos una celda pintada
    int tmW = tilemap_get_width();
    int tmH = tilemap_get_height();
    bool hasPaintedCells = false;
    for (int r = 0; r < tmH && !hasPaintedCells; r++) {
        for (int c = 0; c < tmW; c++) {
            if (tilemap_get_cell(c, r) >= 0) {
                hasPaintedCells = true;
                break;
            }
        }
    }
    if (hasPaintedCells) {
        fprintf(fp, "    <Tilemap width=\"%d\" height=\"%d\">\n", tmW, tmH);
        for (int r = 0; r < tmH; r++) {
            for (int c = 0; c < tmW; c++) {
                int tid = tilemap_get_cell(c, r);
                if (tid >= 0) {
                    fprintf(fp, "        <Cell col=\"%d\" row=\"%d\" tile=\"%d\" />\n", c, r, tid);
                }
            }
        }
        fprintf(fp, "    </Tilemap>\n\n");
    }

    write_objects_recursive(fp, world, -1, 1);

    fprintf(fp, "</Map>\n");
    fclose(fp);
    return true;
}

private:

enum MAX_ATTRS = 32;

struct XmlAttr {
    char[64] name   = 0;
    char[256] value = 0;
}

bool is_space(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
}

bool is_name_char(char c) {
    return (c >= 'a' && c <= 'z') ||
           (c >= 'A' && c <= 'Z') ||
           (c >= '0' && c <= '9') ||
           c == '_' || c == '-' || c == ':' || c == '.';
}

void parse_xml_document(const(char)* src, size_t len, ref LevelWorld world) {
    int[32] parentStack;
    int stackDepth = 0;

    size_t pos = 0;
    while (pos < len) {
        // Buscar el próximo '<'
        while (pos < len && src[pos] != '<') {
            pos++;
        }
        if (pos >= len) {
            break;
        }

        // Comentario XML: <!-- ... -->
        if (pos + 3 < len && src[pos + 1] == '!' && src[pos + 2] == '-' && src[pos + 3] == '-') {
            pos += 4;
            while (pos + 2 < len && !(src[pos] == '-' && src[pos + 1] == '-' && src[pos + 2] == '>')) {
                pos++;
            }
            pos += 3;
            continue;
        }

        // Declaración XML: <? ... ?>
        if (pos + 1 < len && src[pos + 1] == '?') {
            pos += 2;
            while (pos + 1 < len && !(src[pos] == '?' && src[pos + 1] == '>')) {
                pos++;
            }
            pos += 2;
            continue;
        }

        // Etiqueta de cierre: </Tag>
        if (pos + 1 < len && src[pos + 1] == '/') {
            pos += 2;
            char[64] closeTag = 0;
            size_t tIdx = 0;
            while (pos < len && is_name_char(src[pos])) {
                if (tIdx < closeTag.length - 1) {
                    closeTag[tIdx++] = src[pos];
                }
                pos++;
            }
            closeTag[tIdx] = '\0';
            while (pos < len && src[pos] != '>') {
                pos++;
            }
            if (pos < len) pos++;

            if (strcmp(closeTag.ptr, "Map") != 0 &&
                strcmp(closeTag.ptr, "Scene") != 0 &&
                strcmp(closeTag.ptr, "Tilemap") != 0 &&
                stackDepth > 0) {
                stackDepth--;
            }
            continue;
        }

        // Etiqueta de apertura: <Tag ...> o <Tag .../>
        pos++; // saltar '<'
        while (pos < len && is_space(src[pos])) {
            pos++;
        }

        char[64] tagName = 0;
        size_t tIdx = 0;
        while (pos < len && is_name_char(src[pos])) {
            if (tIdx < tagName.length - 1) {
                tagName[tIdx++] = src[pos];
            }
            pos++;
        }
        tagName[tIdx] = '\0';

        if (tagName[0] == '\0') {
            pos++;
            continue;
        }

        XmlAttr[MAX_ATTRS] attrs;
        int attrCount = 0;
        bool selfClosing = false;

        while (pos < len) {
            while (pos < len && is_space(src[pos])) {
                pos++;
            }
            if (pos >= len) {
                break;
            }

            if (src[pos] == '/' && pos + 1 < len && src[pos + 1] == '>') {
                selfClosing = true;
                pos += 2;
                break;
            }
            if (src[pos] == '>') {
                pos++;
                break;
            }

            // Leer nombre del atributo
            char[64] attrName = 0;
            size_t aIdx = 0;
            while (pos < len && is_name_char(src[pos])) {
                if (aIdx < attrName.length - 1) {
                    attrName[aIdx++] = src[pos];
                }
                pos++;
            }
            attrName[aIdx] = '\0';

            while (pos < len && is_space(src[pos])) {
                pos++;
            }

            char[256] attrVal = 0;
            if (pos < len && src[pos] == '=') {
                pos++;
                while (pos < len && is_space(src[pos])) {
                    pos++;
                }
                if (pos < len && (src[pos] == '"' || src[pos] == '\'')) {
                    char quote = src[pos++];
                    size_t vIdx = 0;
                    while (pos < len && src[pos] != quote) {
                        if (vIdx < attrVal.length - 1) {
                            attrVal[vIdx++] = src[pos];
                        }
                        pos++;
                    }
                    attrVal[vIdx] = '\0';
                    if (pos < len && src[pos] == quote) {
                        pos++;
                    }
                }
            }

            if (attrName[0] != '\0' && attrCount < MAX_ATTRS) {
                strncpy(attrs[attrCount].name.ptr, attrName.ptr, attrs[attrCount].name.length - 1);
                strncpy(attrs[attrCount].value.ptr, attrVal.ptr, attrs[attrCount].value.length - 1);
                attrCount++;
            } else if (attrName[0] == '\0') {
                pos++;
            }
        }

        // Procesar nodo <Map>
        if (strcmp(tagName.ptr, "Map") == 0) {
            const(char)* resVal = find_attr(attrs, attrCount, "resources");
            if (resVal !is null && resVal[0] != '\0') {
                strncpy(world.resourcesRelPath.ptr, resVal, world.resourcesRelPath.length - 1);
                world.resourcesRelPath[world.resourcesRelPath.length - 1] = '\0';
            }
            continue;
        }

        // Procesar nodo <Tileset> opcional
        if (strcmp(tagName.ptr, "Tileset") == 0) {
            const(char)* tw = find_attr(attrs, attrCount, "tile-width");
            const(char)* th = find_attr(attrs, attrCount, "tile-height");
            const(char)* tc = find_attr(attrs, attrCount, "tile-count");
            const(char)* tp = find_attr(attrs, attrCount, "path");
            if (tw !is null && th !is null) {
                tileset_set_tile_size_px(atoi(tw), atoi(th));
            }
            if (tc !is null) {
                tileset_set_tile_count(atoi(tc));
            }
            if (tp !is null && tp[0] != '\0') {
                char[256] relTp;
                scene_to_relative_resource_path(tp, relTp.ptr, relTp.length);
                tileset_set_path_and_reload(relTp.ptr, tc is null);
            }
            continue;
        }

        // Procesar nodo <Tilemap> y celdas <Cell> opcionales
        if (strcmp(tagName.ptr, "Tilemap") == 0) {
            const(char)* mw = find_attr(attrs, attrCount, "width");
            const(char)* mh = find_attr(attrs, attrCount, "height");
            int w = (mw !is null) ? atoi(mw) : tilemap_get_width();
            int h = (mh !is null) ? atoi(mh) : tilemap_get_height();
            tilemap_set_size(w, h);
            tilemap_clear();
            continue;
        }
        if (strcmp(tagName.ptr, "Cell") == 0) {
            const(char)* cc = find_attr(attrs, attrCount, "col");
            const(char)* cr = find_attr(attrs, attrCount, "row");
            const(char)* ct = find_attr(attrs, attrCount, "tile");
            if (cc !is null && cr !is null && ct !is null) {
                tilemap_set_cell(atoi(cc), atoi(cr), atoi(ct));
            }
            continue;
        }

        int currentParentUid = (stackDepth > 0) ? parentStack[stackDepth - 1] : -1;
        ObjectType objType = classify_xml_node(tagName.ptr, attrs, attrCount, selfClosing);

        int newUid = level_world_add_object(world, objType, currentParentUid);
        LevelObject* obj = level_world_find(world, newUid);
        if (obj is null) {
            continue;
        }

        strncpy(obj.xmlTag.ptr, tagName.ptr, obj.xmlTag.length - 1);
        obj.xmlTag[obj.xmlTag.length - 1] = '\0';

        // Resetear props por defecto que level_world_add_object agrega para Struct
        obj.customStruct = StructData.init;

        const(char)* idVal = find_attr(attrs, attrCount, "id");
        if (idVal !is null && idVal[0] != '\0') {
            strncpy(obj.id.ptr, idVal, obj.id.length - 1);
            obj.id[obj.id.length - 1] = '\0';
        }

        const(char)* groupVal = find_attr(attrs, attrCount, "group");
        if (groupVal !is null) {
            strncpy(obj.group.ptr, groupVal, obj.group.length - 1);
            obj.group[obj.group.length - 1] = '\0';
        } else {
            obj.group[0] = '\0';
        }

        if (objType == ObjectType.Box) {
            populate_box_from_attrs(obj.box, attrs, attrCount);
        } else if (objType == ObjectType.Sprite) {
            populate_sprite_from_attrs(obj.sprite, attrs, attrCount);
        } else {
            populate_struct_from_attrs(obj.customStruct, attrs, attrCount);
        }

        if (!selfClosing && stackDepth < cast(int) parentStack.length) {
            parentStack[stackDepth++] = newUid;
        }
    }
}

ObjectType classify_xml_node(
    const(char)* tagName,
    ref const(XmlAttr[MAX_ATTRS]) attrs,
    int attrCount,
    bool selfClosing
) {
    if (strcmp(tagName, "Sprite") == 0) {
        return ObjectType.Sprite;
    }
    const(char)* typeAttr = find_attr(attrs, attrCount, "type");
    if (typeAttr !is null && strncmp(typeAttr, "Sprite", 6) == 0) {
        return ObjectType.Sprite;
    }
    if (find_attr(attrs, attrCount, "path") !is null) {
        return ObjectType.Sprite;
    }

    if (strcmp(tagName, "Box") == 0 ||
        strcmp(tagName, "Body") == 0 ||
        strcmp(tagName, "Area") == 0) {
        return ObjectType.Box;
    }
    if (typeAttr !is null && strcmp(typeAttr, "Box") == 0) {
        return ObjectType.Box;
    }
    if (selfClosing && (find_attr(attrs, attrCount, "width") !is null || find_attr(attrs, attrCount, "height") !is null)) {
        return ObjectType.Box;
    }

    return ObjectType.Struct;
}

const(char)* find_attr(ref const(XmlAttr[MAX_ATTRS]) attrs, int count, const(char)* key) {
    for (int i = 0; i < count; i++) {
        if (strcmp(attrs[i].name.ptr, key) == 0) {
            return attrs[i].value.ptr;
        }
    }
    return null;
}

bool parse_bool_attr(const(char)* val, bool defaultVal) {
    if (val is null || val[0] == '\0') {
        return defaultVal;
    }
    if (strcmp(val, "1") == 0 || strcmp(val, "true") == 0 || strcmp(val, "TRUE") == 0) {
        return true;
    }
    return false;
}

void populate_box_from_attrs(ref BoxData box, ref const(XmlAttr[MAX_ATTRS]) attrs, int attrCount) {
    const(char)* px = find_attr(attrs, attrCount, "position-x");
    const(char)* py = find_attr(attrs, attrCount, "position-y");
    const(char)* w  = find_attr(attrs, attrCount, "width");
    const(char)* h  = find_attr(attrs, attrCount, "height");
    const(char)* ly = find_attr(attrs, attrCount, "layer");
    const(char)* mk = find_attr(attrs, attrCount, "mask");
    const(char)* ds = find_attr(attrs, attrCount, "disabled");

    box.position.x = (px !is null) ? strtof(px, null) : 0.0f;
    box.position.y = (py !is null) ? strtof(py, null) : 0.0f;
    box.size.x     = (w !is null)  ? strtof(w, null)  : 64.0f;
    box.size.y     = (h !is null)  ? strtof(h, null)  : 64.0f;
    box.layer      = (ly !is null) ? atoi(ly) : 1;
    box.mask       = (mk !is null) ? atoi(mk) : 1;
    box.disabled   = parse_bool_attr(ds, false);
}

void populate_sprite_from_attrs(ref SpriteData sp, ref const(XmlAttr[MAX_ATTRS]) attrs, int attrCount) {
    const(char)* pathVal = find_attr(attrs, attrCount, "path");
    const(char)* px      = find_attr(attrs, attrCount, "position-x");
    const(char)* py      = find_attr(attrs, attrCount, "position-y");
    const(char)* zIdx    = find_attr(attrs, attrCount, "z-index");
    const(char)* rows    = find_attr(attrs, attrCount, "rows");
    const(char)* cols    = find_attr(attrs, attrCount, "cols");
    const(char)* sx      = find_attr(attrs, attrCount, "scale-x");
    const(char)* sy      = find_attr(attrs, attrCount, "scale-y");
    const(char)* rot     = find_attr(attrs, attrCount, "rotation");
    const(char)* vis     = find_attr(attrs, attrCount, "visible");
    const(char)* dly     = find_attr(attrs, attrCount, "delay");
    const(char)* ctr     = find_attr(attrs, attrCount, "center-position");

    if (pathVal !is null && pathVal[0] != '\0') {
        // Convertir a relativo si viniera absoluto
        scene_to_relative_resource_path(pathVal, sp.resourcePath.ptr, sp.resourcePath.length);
    } else {
        sp.resourcePath[0] = '\0';
    }

    sp.position.x     = (px !is null)   ? strtof(px, null)   : 0.0f;
    sp.position.y     = (py !is null)   ? strtof(py, null)   : 0.0f;
    sp.zIndex         = (zIdx !is null) ? atoi(zIdx)         : 0;
    sp.rows           = (rows !is null) ? atoi(rows)         : 1;
    sp.cols           = (cols !is null) ? atoi(cols)         : 1;
    sp.scale.x        = (sx !is null)   ? strtof(sx, null)   : 1.0f;
    sp.scale.y        = (sy !is null)   ? strtof(sy, null)   : 1.0f;
    sp.rotation       = (rot !is null)  ? strtof(rot, null)  : 0.0f;
    sp.visible        = parse_bool_attr(vis, true);
    sp.delay          = (dly !is null)  ? strtof(dly, null)  : 0.1f;
    sp.centerPosition = parse_bool_attr(ctr, false);

    level_sprite_reload_texture(sp);
}

void populate_struct_from_attrs(ref StructData st, ref const(XmlAttr[MAX_ATTRS]) attrs, int attrCount) {
    for (int i = 0; i < attrCount; i++) {
        const(char)* k = attrs[i].name.ptr;
        const(char)* v = attrs[i].value.ptr;
        if (strcmp(k, "id") == 0 || strcmp(k, "group") == 0) {
            continue;
        }
        int idx = level_struct_add_custom_prop(st, k, CustomValueType.String);
        if (idx >= 0) {
            strncpy(st.props[idx].stringVal.ptr, v, st.props[idx].stringVal.length - 1);
            st.props[idx].stringVal[st.props[idx].stringVal.length - 1] = '\0';
        }
    }
}

void write_indent(FILE* fp, int indentLevel) {
    for (int i = 0; i < indentLevel; i++) {
        fprintf(fp, "    ");
    }
}

void write_objects_recursive(FILE* fp, ref LevelWorld world, int parentUid, int indentLevel) {
    for (int i = 0; i < MAX_LEVEL_OBJECTS; i++) {
        if (!world.objects[i].used || world.objects[i].parentUid != parentUid) {
            continue;
        }

        LevelObject* obj = &world.objects[i];
        const(char)* tag = (obj.xmlTag[0] != '\0') ? obj.xmlTag.ptr : level_object_type_label(obj.type);

        if (obj.type == ObjectType.Box) {
            write_indent(fp, indentLevel);
            fprintf(
                fp,
                "<%s\n",
                tag
            );
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "id=\"%s\"\n", obj.id.ptr);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "group=\"%s\"\n", obj.group.ptr);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "position-x=\"%.0f\"\n", obj.box.position.x);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "position-y=\"%.0f\"\n", obj.box.position.y);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "width=\"%.0f\"\n", obj.box.size.x);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "height=\"%.0f\"\n", obj.box.size.y);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "layer=\"%d\"\n", obj.box.layer);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "mask=\"%d\"\n", obj.box.mask);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "disabled=\"%d\"\n", obj.box.disabled ? 1 : 0);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "type=\"Box\"\n");
            write_indent(fp, indentLevel);
            fprintf(fp, "/>\n");
        } else if (obj.type == ObjectType.Sprite) {
            write_indent(fp, indentLevel);
            fprintf(fp, "<Sprite\n");
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "id=\"%s\"\n", obj.id.ptr);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "group=\"%s\"\n", obj.group.ptr);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "path=\"%s\"\n", obj.sprite.resourcePath.ptr);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "position-x=\"%.0f\"\n", obj.sprite.position.x);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "position-y=\"%.0f\"\n", obj.sprite.position.y);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "z-index=\"%d\"\n", obj.sprite.zIndex);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "rows=\"%d\"\n", obj.sprite.rows);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "cols=\"%d\"\n", obj.sprite.cols);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "scale-x=\"%.2f\"\n", obj.sprite.scale.x);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "scale-y=\"%.2f\"\n", obj.sprite.scale.y);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "rotation=\"%.2f\"\n", obj.sprite.rotation);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "visible=\"%d\"\n", obj.sprite.visible ? 1 : 0);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "delay=\"%.2f\"\n", obj.sprite.delay);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "center-position=\"%d\"\n", obj.sprite.centerPosition ? 1 : 0);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "type=\"Sprite2D\"\n");
            write_indent(fp, indentLevel);
            fprintf(fp, "/>\n");
        } else {
            const(char)* structTag = (obj.xmlTag[0] != '\0') ? obj.xmlTag.ptr : "Struct";
            write_indent(fp, indentLevel);
            fprintf(fp, "<%s\n", structTag);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "id=\"%s\"\n", obj.id.ptr);
            write_indent(fp, indentLevel + 1);
            fprintf(fp, "group=\"%s\"\n", obj.group.ptr);

            for (int p = 0; p < MAX_CUSTOM_PROPS; p++) {
                if (!obj.customStruct.props[p].used) {
                    continue;
                }
                CustomProperty* prop = &obj.customStruct.props[p];
                write_indent(fp, indentLevel + 1);
                final switch (prop.type) {
                    case CustomValueType.Int:
                        fprintf(fp, "%s=\"%d\"\n", prop.key.ptr, prop.intVal);
                        break;
                    case CustomValueType.Float:
                        fprintf(fp, "%s=\"%.2f\"\n", prop.key.ptr, prop.floatVal);
                        break;
                    case CustomValueType.Bool:
                        fprintf(fp, "%s=\"%d\"\n", prop.key.ptr, prop.boolVal ? 1 : 0);
                        break;
                    case CustomValueType.String:
                        fprintf(fp, "%s=\"%s\"\n", prop.key.ptr, prop.stringVal.ptr);
                        break;
                    case CustomValueType.Vec2:
                        fprintf(fp, "%s=\"%.2f,%.2f\"\n", prop.key.ptr, prop.vec2Val.x, prop.vec2Val.y);
                        break;
                }
            }

            write_indent(fp, indentLevel);
            fprintf(fp, ">\n");

            write_objects_recursive(fp, world, obj.uid, indentLevel + 1);

            write_indent(fp, indentLevel);
            fprintf(fp, "</%s>\n", structTag);
        }
    }
}
