module scenes.map_editor.project_manager;

nothrow:
@nogc:

import raylib : FileExists;
import core.stdc.stdio : FILE, fopen, fclose, fseek, ftell, fread, fprintf, snprintf, SEEK_END, SEEK_SET;
import core.stdc.stdlib : malloc, free, atoi;
import core.stdc.string : strlen, strncpy, strcmp, strncmp, strchr;
import engine.path_utils : path_get_dir, path_make_relative_to_resources, path_resolve_for_loading;
import scenes.map_editor.level_data : LevelWorld, get_active_level_world;
import scenes.map_editor.xml_scene : xml_load_scene, xml_save_scene;
import scenes.map_editor.dialogs_data : DialogGraph, dialog_graph_load_json, dialog_graph_save_json;
import scenes.map_editor.tabs.dialogs_editor_tab : dialogs_editor_tab_get_graph;
import scenes.map_editor.tabs.prefabs_tab :
    prefabs_write_to_procshit,
    prefabs_clear_all_for_load,
    prefabs_parse_procshit_line,
    prefabs_finalize_after_load;
import scenes.map_editor.tabs.tileset_tab :
    MAX_TILESETS,
    MAX_TILESET_TILES,
    tile_pack,
    tile_unpack_tileset,
    tile_unpack_index,
    tileset_get_tileset_count,
    tileset_clear_all,
    tileset_add_slot,
    tileset_get_path_at,
    tileset_set_path_and_reload_at,
    tileset_get_tile_width_at,
    tileset_get_tile_height_at,
    tileset_set_tile_size_at,
    tileset_get_tile_count_at,
    tileset_set_tile_count_at,
    tileset_is_tile_active_at,
    tileset_set_tile_active_at,
    tileset_reload_all_textures;
import scenes.map_editor.tabs.tilemap_tab :
    MAX_MAP_WIDTH,
    MAX_MAP_HEIGHT,
    tilemap_get_width,
    tilemap_get_height,
    tilemap_set_size,
    tilemap_get_cell,
    tilemap_set_cell,
    tilemap_clear;

public:

enum int MAX_PROJECT_MAPS = 64;

struct ProjectMapEntry {
    bool used = false;
    char[256] relativePath = 0; // Ruta relativa al archivo .procshit
    char[512] fullPath     = 0; // Ruta absoluta resuelta en disco
}

struct ProcshitProject {
    char[512] projectFilePath = 0;
    ProjectMapEntry[MAX_PROJECT_MAPS] maps;
    int activeMapIndex = -1;
}

/**
 * Inicializa el estado del proyecto .procshit.
 */
void project_manager_init() {
    current_project = ProcshitProject.init;
}

/**
 * Retorna referencia al proyecto activo.
 */
ref ProcshitProject get_active_project() {
    return current_project;
}

/**
 * Agrega un mapa (.xml) a la lista de mapas del proyecto (estilo Tiled) si no estaba ya registrado.
 * Retorna el índice del mapa en la lista.
 */
int project_add_map(const(char)* xmlFullPath) {
    if (xmlFullPath is null || xmlFullPath[0] == '\0') {
        return -1;
    }

    char[256] relBuf = 0;
    if (current_project.projectFilePath[0] != '\0') {
        make_rel_to_project_file(current_project.projectFilePath.ptr, xmlFullPath, relBuf.ptr, relBuf.length);
    } else {
        strncpy(relBuf.ptr, xmlFullPath, relBuf.length - 1);
        relBuf[relBuf.length - 1] = '\0';
    }

    // Verificar si ya existe en la lista
    for (int i = 0; i < MAX_PROJECT_MAPS; i++) {
        if (!current_project.maps[i].used) continue;
        if (strcmp(current_project.maps[i].fullPath.ptr, xmlFullPath) == 0 ||
            strcmp(current_project.maps[i].relativePath.ptr, relBuf.ptr) == 0) {
            strncpy(current_project.maps[i].fullPath.ptr, xmlFullPath, current_project.maps[i].fullPath.length - 1);
            strncpy(current_project.maps[i].relativePath.ptr, relBuf.ptr, current_project.maps[i].relativePath.length - 1);
            current_project.activeMapIndex = i;
            return i;
        }
    }

    // Insertar en el primer slot libre
    for (int i = 0; i < MAX_PROJECT_MAPS; i++) {
        if (!current_project.maps[i].used) {
            current_project.maps[i].used = true;
            strncpy(current_project.maps[i].fullPath.ptr, xmlFullPath, current_project.maps[i].fullPath.length - 1);
            current_project.maps[i].fullPath[current_project.maps[i].fullPath.length - 1] = '\0';
            strncpy(current_project.maps[i].relativePath.ptr, relBuf.ptr, current_project.maps[i].relativePath.length - 1);
            current_project.maps[i].relativePath[current_project.maps[i].relativePath.length - 1] = '\0';
            current_project.activeMapIndex = i;
            return i;
        }
    }

    return -1;
}

/**
 * Elimina un mapa de la lista del proyecto por su índice.
 */
void project_remove_map(int index) {
    if (index < 0 || index >= MAX_PROJECT_MAPS || !current_project.maps[index].used) {
        return;
    }

    for (int i = index; i < MAX_PROJECT_MAPS - 1; i++) {
        current_project.maps[i] = current_project.maps[i + 1];
    }
    current_project.maps[MAX_PROJECT_MAPS - 1] = ProjectMapEntry.init;

    if (current_project.activeMapIndex == index) {
        current_project.activeMapIndex = -1;
    } else if (current_project.activeMapIndex > index) {
        current_project.activeMapIndex--;
    }
}

/**
 * Cambia al mapa indicado de la lista del proyecto (cargando su XML en el editor).
 */
bool project_switch_to_map(int index) {
    if (index < 0 || index >= MAX_PROJECT_MAPS || !current_project.maps[index].used) {
        return false;
    }

    char[512] resolvedPath = 0;
    if (current_project.maps[index].fullPath[0] != '\0' && FileExists(current_project.maps[index].fullPath.ptr)) {
        strncpy(resolvedPath.ptr, current_project.maps[index].fullPath.ptr, resolvedPath.length - 1);
    } else if (current_project.projectFilePath[0] != '\0') {
        resolve_rel_to_project_file(
            current_project.projectFilePath.ptr,
            current_project.maps[index].relativePath.ptr,
            resolvedPath.ptr,
            resolvedPath.length
        );
    } else {
        strncpy(resolvedPath.ptr, current_project.maps[index].relativePath.ptr, resolvedPath.length - 1);
    }

    ref LevelWorld world = get_active_level_world();
    if (xml_load_scene(resolvedPath.ptr, world)) {
        tileset_reload_all_textures(false);
        current_project.activeMapIndex = index;
        strncpy(current_project.maps[index].fullPath.ptr, resolvedPath.ptr, current_project.maps[index].fullPath.length - 1);
        return true;
    }
    return false;
}

/**
 * Guarda todo el estado del proyecto en un archivo de texto `.procshit`:
 * - Lista de mapas del proyecto (MAP_ENTRY)
 * - Escena XML activa y prefijo resources
 * - Archivo JSON de diálogos activo
 * - Configuración del Tileset y celdas pintadas del Tilemap
 */
bool procshit_save_project(const(char)* filePath) {
    if (filePath is null || filePath[0] == '\0') {
        return false;
    }

    FILE* fp = fopen(filePath, "wb");
    if (fp is null) {
        return false;
    }

    strncpy(current_project.projectFilePath.ptr, filePath, current_project.projectFilePath.length - 1);
    current_project.projectFilePath[current_project.projectFilePath.length - 1] = '\0';

    ref LevelWorld world = get_active_level_world();
    ref DialogGraph dlgGraph = dialogs_editor_tab_get_graph();

    // Si hay una escena XML activa en el Level Editor, asegurarse de registrarla en la lista de mapas del proyecto
    if (world.xmlFilePath[0] != '\0') {
        project_add_map(world.xmlFilePath.ptr);
    }

    // Recalcular rutas relativas de todos los mapas respecto al archivo .procshit
    for (int i = 0; i < MAX_PROJECT_MAPS; i++) {
        if (!current_project.maps[i].used) continue;
        if (current_project.maps[i].fullPath[0] != '\0') {
            make_rel_to_project_file(
                filePath,
                current_project.maps[i].fullPath.ptr,
                current_project.maps[i].relativePath.ptr,
                current_project.maps[i].relativePath.length
            );
        }
    }

    fprintf(fp, "# PROCSHIT v1.0 - Archivo de Proyecto de Level Editor\n");
    fprintf(fp, "RESOURCES_PREFIX=%s\n", world.resourcesRelPath.ptr);

    // Guardar ruta relativa de la escena XML activa
    char[256] relActiveXml = 0;
    if (world.xmlFilePath[0] != '\0') {
        make_rel_to_project_file(filePath, world.xmlFilePath.ptr, relActiveXml.ptr, relActiveXml.length);
    }
    fprintf(fp, "ACTIVE_SCENE_XML=%s\n", relActiveXml.ptr);

    // Guardar ruta relativa del archivo JSON de diálogos activo
    char[256] relDialogsJson = 0;
    if (dlgGraph.jsonFilePath[0] != '\0') {
        make_rel_to_project_file(filePath, dlgGraph.jsonFilePath.ptr, relDialogsJson.ptr, relDialogsJson.length);
    }
    fprintf(fp, "ACTIVE_DIALOGS_JSON=%s\n", relDialogsJson.ptr);

    // Lista de mapas usados por el proyecto (tipo Tiled)
    fprintf(fp, "\n# Mapas usados por el proyecto\n");
    for (int i = 0; i < MAX_PROJECT_MAPS; i++) {
        if (!current_project.maps[i].used) continue;
        fprintf(fp, "MAP_ENTRY=%s\n", current_project.maps[i].relativePath.ptr);
    }

    // Datos de Tilesets (claves legacy del slot 0 + lista multi-tileset)
    int tsTotal = tileset_get_tileset_count();
    fprintf(fp, "\n# Configuracion de Tilesets\n");
    if (tsTotal > 0) {
        int t0Count = tileset_get_tile_count_at(0);
        fprintf(fp, "TILESET_PNG=%s\n", tileset_get_path_at(0));
        fprintf(fp, "TILESET_TILE_WIDTH=%d\n", tileset_get_tile_width_at(0));
        fprintf(fp, "TILESET_TILE_HEIGHT=%d\n", tileset_get_tile_height_at(0));
        fprintf(fp, "TILESET_TILE_COUNT=%d\n", t0Count);

        fprintf(fp, "TILESET_INACTIVE=");
        bool firstInact0 = true;
        for (int i = 0; i < t0Count; i++) {
            if (!tileset_is_tile_active_at(0, i)) {
                const(char)* sep = firstInact0 ? "" : ",";
                fprintf(fp, "%s%d", sep, i);
                firstInact0 = false;
            }
        }
        fprintf(fp, "\n");
    }

    fprintf(fp, "TILESET_COUNT=%d\n", tsTotal);
    for (int ts = 0; ts < tsTotal; ts++) {
        int tCount = tileset_get_tile_count_at(ts);
        fprintf(
            fp,
            "TILESET_ENTRY=%d,%d,%d,%d,%s\n",
            ts,
            tileset_get_tile_width_at(ts),
            tileset_get_tile_height_at(ts),
            tCount,
            tileset_get_path_at(ts)
        );
        fprintf(fp, "TILESET_ENTRY_INACTIVE=%d,", ts);
        bool firstInactive = true;
        for (int i = 0; i < tCount; i++) {
            if (!tileset_is_tile_active_at(ts, i)) {
                const(char)* sep = firstInactive ? "" : ",";
                fprintf(fp, "%s%d", sep, i);
                firstInactive = false;
            }
        }
        fprintf(fp, "\n");
    }

    // Datos del Tilemap
    int mapW = tilemap_get_width();
    int mapH = tilemap_get_height();
    fprintf(fp, "\n# Configuracion y Celdas del Tilemap\n");
    fprintf(fp, "TILEMAP_WIDTH=%d\n", mapW);
    fprintf(fp, "TILEMAP_HEIGHT=%d\n", mapH);
    for (int r = 0; r < mapH; r++) {
        for (int c = 0; c < mapW; c++) {
            int val = tilemap_get_cell(c, r);
            if (val >= 0) {
                fprintf(
                    fp,
                    "TILEMAP_CELL=%d,%d,%d,%d\n",
                    c,
                    r,
                    tile_unpack_tileset(val),
                    tile_unpack_index(val)
                );
            }
        }
    }

    // Guardar todos los Prefabs del proyecto dentro de .procshit
    prefabs_write_to_procshit(fp);

    fclose(fp);
    return true;
}

/**
 * Carga un archivo de proyecto `.procshit` desde disco y restaura los mapas del proyecto,
 * la escena activa, el grafo de diálogos, los tilesets y el tilemap.
 */
bool procshit_load_project(const(char)* filePath) {
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

    if (fileSize <= 0 || fileSize > 32 * 1024 * 1024) {
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

    // Reiniciar estructura del proyecto
    current_project = ProcshitProject.init;
    strncpy(current_project.projectFilePath.ptr, filePath, current_project.projectFilePath.length - 1);
    current_project.projectFilePath[current_project.projectFilePath.length - 1] = '\0';

    ref LevelWorld world = get_active_level_world();
    ref DialogGraph dlgGraph = dialogs_editor_tab_get_graph();

    char[256] resPrefix = 0;
    char[256] activeXmlRel = 0;
    char[256] activeJsonRel = 0;
    bool hasMultiTs = false;
    bool hasLegacyTs = false;
    bool hasTilemapData = false;

    // Pasada 1: Metadatos del proyecto, rutas y detección de secciones
    size_t pos = 0;
    while (pos < bytesRead) {
        char[1024] lineBuf = 0;
        if (!read_next_kv_line(buffer, bytesRead, pos, lineBuf.ptr, lineBuf.length)) {
            continue;
        }
        const(char)* eqPtr = strchr(lineBuf.ptr, '=');
        if (eqPtr is null) continue;

        char[128] key = 0;
        const(char)* val = extract_kv(lineBuf.ptr, eqPtr, key.ptr, key.length);

        if (strcmp(key.ptr, "RESOURCES_PREFIX") == 0) {
            strncpy(resPrefix.ptr, val, resPrefix.length - 1);
        } else if (strcmp(key.ptr, "ACTIVE_SCENE_XML") == 0) {
            strncpy(activeXmlRel.ptr, val, activeXmlRel.length - 1);
        } else if (strcmp(key.ptr, "ACTIVE_DIALOGS_JSON") == 0) {
            strncpy(activeJsonRel.ptr, val, activeJsonRel.length - 1);
        } else if (strcmp(key.ptr, "MAP_ENTRY") == 0 && val[0] != '\0') {
            char[512] fullMapPath = 0;
            resolve_rel_to_project_file(filePath, val, fullMapPath.ptr, fullMapPath.length);
            project_add_map(fullMapPath.ptr);
        } else if (strcmp(key.ptr, "TILESET_ENTRY") == 0) {
            hasMultiTs = true;
        } else if (strcmp(key.ptr, "TILESET_PNG") == 0 && val[0] != '\0') {
            hasLegacyTs = true;
        } else if (strcmp(key.ptr, "TILEMAP_WIDTH") == 0 || strcmp(key.ptr, "TILEMAP_CELL") == 0) {
            hasTilemapData = true;
        }
    }

    // 1. Cargar escena XML activa si está definida
    if (activeXmlRel[0] != '\0') {
        char[512] fullXmlPath = 0;
        resolve_rel_to_project_file(filePath, activeXmlRel.ptr, fullXmlPath.ptr, fullXmlPath.length);
        if (FileExists(fullXmlPath.ptr)) {
            xml_load_scene(fullXmlPath.ptr, world);
            project_add_map(fullXmlPath.ptr);
        }
    }
    if (resPrefix[0] != '\0') {
        strncpy(world.resourcesRelPath.ptr, resPrefix.ptr, world.resourcesRelPath.length - 1);
        world.resourcesRelPath[world.resourcesRelPath.length - 1] = '\0';
    }

    // 2. Pasada 2: Restaurar tilesets y celdas del tilemap definidos en .procshit
    if (hasMultiTs) {
        tileset_clear_all();
    } else if (hasLegacyTs) {
        tileset_clear_all();
        tileset_add_slot("", 32, 32, 256, false);
    }

    int tmW = tilemap_get_width();
    int tmH = tilemap_get_height();
    bool clearedCells = false;
    prefabs_clear_all_for_load();

    pos = 0;
    while (pos < bytesRead) {
        char[1024] lineBuf = 0;
        if (!read_next_kv_line(buffer, bytesRead, pos, lineBuf.ptr, lineBuf.length)) {
            continue;
        }
        const(char)* eqPtr = strchr(lineBuf.ptr, '=');
        if (eqPtr is null) continue;

        char[128] key = 0;
        const(char)* val = extract_kv(lineBuf.ptr, eqPtr, key.ptr, key.length);

        if (strncmp(key.ptr, "PREFAB_", 7) == 0) {
            prefabs_parse_procshit_line(key.ptr, val);
            continue;
        }

        if (hasMultiTs && strcmp(key.ptr, "TILESET_ENTRY") == 0) {
            int tsIdx = 0, tw = 32, th = 32, tc = 32;
            const(char)* pathPtr = null;
            if (parse_tileset_entry(val, tsIdx, tw, th, tc, &pathPtr) && tsIdx >= 0 && tsIdx < MAX_TILESETS) {
                while (tileset_get_tileset_count() <= tsIdx) {
                    tileset_add_slot("", 32, 32, 32, false);
                }
                tileset_set_tile_size_at(tsIdx, tw, th);
                tileset_set_tile_count_at(tsIdx, tc);
                tileset_set_path_and_reload_at(tsIdx, pathPtr, false);
            }
        } else if (hasMultiTs && strcmp(key.ptr, "TILESET_ENTRY_INACTIVE") == 0) {
            const(char)* comma = strchr(val, ',');
            if (comma !is null) {
                int tsIdx = atoi(val);
                parse_inactive_list_for_ts(tsIdx, comma + 1);
            }
        } else if (!hasMultiTs && hasLegacyTs) {
            if (strcmp(key.ptr, "TILESET_PNG") == 0) {
                tileset_set_path_and_reload_at(0, val, false);
            } else if (strcmp(key.ptr, "TILESET_TILE_WIDTH") == 0) {
                tileset_set_tile_size_at(0, atoi(val), tileset_get_tile_height_at(0));
            } else if (strcmp(key.ptr, "TILESET_TILE_HEIGHT") == 0) {
                tileset_set_tile_size_at(0, tileset_get_tile_width_at(0), atoi(val));
            } else if (strcmp(key.ptr, "TILESET_TILE_COUNT") == 0) {
                tileset_set_tile_count_at(0, atoi(val));
            } else if (strcmp(key.ptr, "TILESET_INACTIVE") == 0) {
                parse_inactive_list_for_ts(0, val);
            }
        }

        if (hasTilemapData) {
            if (strcmp(key.ptr, "TILEMAP_WIDTH") == 0) {
                tmW = atoi(val);
                tilemap_set_size(tmW, tmH);
                if (!clearedCells) {
                    tilemap_clear();
                    clearedCells = true;
                }
            } else if (strcmp(key.ptr, "TILEMAP_HEIGHT") == 0) {
                tmH = atoi(val);
                tilemap_set_size(tmW, tmH);
                if (!clearedCells) {
                    tilemap_clear();
                    clearedCells = true;
                }
            } else if (strcmp(key.ptr, "TILEMAP_CELL") == 0) {
                if (!clearedCells) {
                    tilemap_clear();
                    clearedCells = true;
                }
                int col = 0, row = 0, tsIdx = 0, tileIdx = -1;
                if (parse_four_ints(val, col, row, tsIdx, tileIdx)) {
                    tilemap_set_cell(col, row, tile_pack(tsIdx, tileIdx));
                } else if (parse_three_ints(val, col, row, tileIdx)) {
                    tilemap_set_cell(col, row, tile_pack(0, tileIdx));
                }
            }
        }
    }

    if (tileset_get_tileset_count() == 0) {
        tileset_add_slot("", 32, 32, 32, false);
    }
    tileset_reload_all_textures(false);
    prefabs_finalize_after_load();

    // 3. Si hay un archivo JSON de diálogos definido, cargarlo en el Dialogs Editor
    if (activeJsonRel[0] != '\0') {
        char[512] fullJsonPath = 0;
        resolve_rel_to_project_file(filePath, activeJsonRel.ptr, fullJsonPath.ptr, fullJsonPath.length);
        if (FileExists(fullJsonPath.ptr)) {
            dialog_graph_load_json(fullJsonPath.ptr, dlgGraph);
        }
    }

    free(buffer);
    return true;
}

private:

__gshared ProcshitProject current_project;

void make_rel_to_project_file(
    const(char)* projectFilePath,
    const(char)* targetPath,
    char* outRel,
    size_t maxLen
) {
    char[512] projDir = 0;
    path_get_dir(projectFilePath, projDir.ptr, projDir.length);
    path_make_relative_to_resources(targetPath, projDir.ptr, "./", outRel, maxLen);
}

void resolve_rel_to_project_file(
    const(char)* projectFilePath,
    const(char)* relPath,
    char* outFull,
    size_t maxLen
) {
    char[512] projDir = 0;
    path_get_dir(projectFilePath, projDir.ptr, projDir.length);
    path_resolve_for_loading(relPath, projDir.ptr, "./", outFull, maxLen);
}

bool read_next_kv_line(const(char)* buffer, size_t bytesRead, ref size_t pos, char* outBuf, size_t maxLen) {
    if (pos >= bytesRead) return false;

    size_t lineStart = pos;
    while (pos < bytesRead && buffer[pos] != '\n' && buffer[pos] != '\r') {
        pos++;
    }
    size_t lineLen = pos - lineStart;
    while (pos < bytesRead && (buffer[pos] == '\n' || buffer[pos] == '\r')) {
        pos++;
    }
    if (lineLen == 0) return false;

    size_t copyLen = (lineLen < maxLen - 1) ? lineLen : (maxLen - 1);
    for (size_t k = 0; k < copyLen; k++) {
        outBuf[k] = buffer[lineStart + k];
    }
    outBuf[copyLen] = '\0';
    return outBuf[0] != '#';
}

const(char)* extract_kv(const(char)* lineBuf, const(char)* eqPtr, char* keyBuf, size_t keyMax) {
    size_t keyLen = cast(size_t)(eqPtr - lineBuf);
    if (keyLen >= keyMax) keyLen = keyMax - 1;
    for (size_t k = 0; k < keyLen; k++) {
        keyBuf[k] = lineBuf[k];
    }
    keyBuf[keyLen] = '\0';
    return eqPtr + 1;
}

void parse_inactive_list_for_ts(int tsIdx, const(char)* str) {
    if (str is null || str[0] == '\0') return;
    int currentVal = 0;
    bool inNumber = false;
    for (size_t i = 0; str[i] != '\0'; i++) {
        char c = str[i];
        if (c >= '0' && c <= '9') {
            currentVal = currentVal * 10 + (c - '0');
            inNumber = true;
        } else {
            if (inNumber) {
                tileset_set_tile_active_at(tsIdx, currentVal, false);
                currentVal = 0;
                inNumber = false;
            }
        }
    }
    if (inNumber) {
        tileset_set_tile_active_at(tsIdx, currentVal, false);
    }
}

bool parse_tileset_entry(const(char)* str, out int idx, out int tw, out int th, out int tc, const(char)** outPath) {
    idx = 0;
    tw = 32;
    th = 32;
    tc = 32;
    *outPath = "";
    if (str is null || str[0] == '\0') return false;

    const(char)* p1 = strchr(str, ',');
    if (p1 is null) return false;
    const(char)* p2 = strchr(p1 + 1, ',');
    if (p2 is null) return false;
    const(char)* p3 = strchr(p2 + 1, ',');
    if (p3 is null) return false;
    const(char)* p4 = strchr(p3 + 1, ',');
    if (p4 is null) return false;

    idx = atoi(str);
    tw = atoi(p1 + 1);
    th = atoi(p2 + 1);
    tc = atoi(p3 + 1);
    *outPath = p4 + 1;
    return true;
}

bool parse_four_ints(const(char)* str, out int a, out int b, out int c, out int d) {
    a = 0;
    b = 0;
    c = 0;
    d = 0;
    if (str is null || str[0] == '\0') return false;

    const(char)* p1 = strchr(str, ',');
    if (p1 is null) return false;
    const(char)* p2 = strchr(p1 + 1, ',');
    if (p2 is null) return false;
    const(char)* p3 = strchr(p2 + 1, ',');
    if (p3 is null) return false;

    a = atoi(str);
    b = atoi(p1 + 1);
    c = atoi(p2 + 1);
    d = atoi(p3 + 1);
    return true;
}

bool parse_three_ints(const(char)* str, out int a, out int b, out int c) {
    a = 0;
    b = 0;
    c = 0;
    if (str is null || str[0] == '\0') return false;

    const(char)* p1 = strchr(str, ',');
    if (p1 is null) return false;
    const(char)* p2 = strchr(p1 + 1, ',');
    if (p2 is null) return false;

    a = atoi(str);
    b = atoi(p1 + 1);
    c = atoi(p2 + 1);
    return true;
}
