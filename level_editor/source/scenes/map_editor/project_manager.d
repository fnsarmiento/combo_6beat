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
import scenes.map_editor.tabs.tileset_tab :
    MAX_TILESET_TILES,
    tileset_get_tile_count,
    tileset_set_tile_count,
    tileset_get_tile_width_px,
    tileset_get_tile_height_px,
    tileset_set_tile_size_px,
    tileset_get_path,
    tileset_set_path_and_reload,
    tileset_is_tile_active,
    tileset_set_tile_active,
    tileset_reload_texture;
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
        tileset_reload_texture(false);
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

    // Datos del Tileset
    fprintf(fp, "\n# Configuracion del Tileset\n");
    fprintf(fp, "TILESET_PNG=%s\n", tileset_get_path());
    fprintf(fp, "TILESET_TILE_WIDTH=%d\n", tileset_get_tile_width_px());
    fprintf(fp, "TILESET_TILE_HEIGHT=%d\n", tileset_get_tile_height_px());
    int tCount = tileset_get_tile_count();
    fprintf(fp, "TILESET_TILE_COUNT=%d\n", tCount);

    fprintf(fp, "TILESET_INACTIVE=");
    bool firstInactive = true;
    for (int i = 0; i < tCount; i++) {
        if (!tileset_is_tile_active(i)) {
            const(char)* sep = firstInactive ? "" : ",";
            fprintf(fp, "%s%d", sep, i);
            firstInactive = false;
        }
    }
    fprintf(fp, "\n");

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
                fprintf(fp, "TILEMAP_CELL=%d,%d,%d\n", c, r, val);
            }
        }
    }

    fclose(fp);
    return true;
}

/**
 * Carga un archivo de proyecto `.procshit` desde disco y restaura los mapas del proyecto,
 * la escena activa, el grafo de diálogos, el tileset y el tilemap.
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

    char[256] activeXmlRel = 0;
    char[256] activeJsonRel = 0;
    char[256] tilesetPngRel = 0;
    int tsTileW = tileset_get_tile_width_px();
    int tsTileH = tileset_get_tile_height_px();
    int tsCount = tileset_get_tile_count();
    int tmW = tilemap_get_width();
    int tmH = tilemap_get_height();

    for (int i = 0; i < MAX_TILESET_TILES; i++) {
        tileset_set_tile_active(i, true);
    }

    // Parsear línea por línea
    size_t pos = 0;
    while (pos < bytesRead) {
        size_t lineStart = pos;
        while (pos < bytesRead && buffer[pos] != '\n' && buffer[pos] != '\r') {
            pos++;
        }
        size_t lineLen = pos - lineStart;
        while (pos < bytesRead && (buffer[pos] == '\n' || buffer[pos] == '\r')) {
            pos++;
        }

        if (lineLen == 0) continue;

        char[1024] lineBuf = 0;
        size_t copyLen = (lineLen < lineBuf.length - 1) ? lineLen : (lineBuf.length - 1);
        for (size_t k = 0; k < copyLen; k++) {
            lineBuf[k] = buffer[lineStart + k];
        }
        lineBuf[copyLen] = '\0';

        // Ignorar comentarios '#'
        if (lineBuf[0] == '#') continue;

        const(char)* eqPtr = strchr(lineBuf.ptr, '=');
        if (eqPtr is null) continue;

        size_t keyLen = cast(size_t)(eqPtr - lineBuf.ptr);
        char[128] key = 0;
        if (keyLen >= key.length) keyLen = key.length - 1;
        for (size_t k = 0; k < keyLen; k++) {
            key[k] = lineBuf[k];
        }
        key[keyLen] = '\0';

        const(char)* val = eqPtr + 1;

        if (strcmp(key.ptr, "RESOURCES_PREFIX") == 0) {
            strncpy(world.resourcesRelPath.ptr, val, world.resourcesRelPath.length - 1);
            world.resourcesRelPath[world.resourcesRelPath.length - 1] = '\0';
        } else if (strcmp(key.ptr, "ACTIVE_SCENE_XML") == 0) {
            strncpy(activeXmlRel.ptr, val, activeXmlRel.length - 1);
        } else if (strcmp(key.ptr, "ACTIVE_DIALOGS_JSON") == 0) {
            strncpy(activeJsonRel.ptr, val, activeJsonRel.length - 1);
        } else if (strcmp(key.ptr, "MAP_ENTRY") == 0 && val[0] != '\0') {
            char[512] fullMapPath = 0;
            resolve_rel_to_project_file(filePath, val, fullMapPath.ptr, fullMapPath.length);
            project_add_map(fullMapPath.ptr);
        } else if (strcmp(key.ptr, "TILESET_PNG") == 0) {
            strncpy(tilesetPngRel.ptr, val, tilesetPngRel.length - 1);
        } else if (strcmp(key.ptr, "TILESET_TILE_WIDTH") == 0) {
            tsTileW = atoi(val);
        } else if (strcmp(key.ptr, "TILESET_TILE_HEIGHT") == 0) {
            tsTileH = atoi(val);
        } else if (strcmp(key.ptr, "TILESET_TILE_COUNT") == 0) {
            tsCount = atoi(val);
        } else if (strcmp(key.ptr, "TILESET_INACTIVE") == 0) {
            parse_inactive_list(val);
        } else if (strcmp(key.ptr, "TILEMAP_WIDTH") == 0) {
            tmW = atoi(val);
            tilemap_set_size(tmW, tmH);
            tilemap_clear();
        } else if (strcmp(key.ptr, "TILEMAP_HEIGHT") == 0) {
            tmH = atoi(val);
            tilemap_set_size(tmW, tmH);
            tilemap_clear();
        } else if (strcmp(key.ptr, "TILEMAP_CELL") == 0) {
            int col = 0, row = 0, tileIdx = -1;
            if (parse_three_ints(val, col, row, tileIdx)) {
                tilemap_set_cell(col, row, tileIdx);
            }
        }
    }

    // 1. Si hay una escena XML activa definida, cargarla primero
    if (activeXmlRel[0] != '\0') {
        char[512] fullXmlPath = 0;
        resolve_rel_to_project_file(filePath, activeXmlRel.ptr, fullXmlPath.ptr, fullXmlPath.length);
        if (FileExists(fullXmlPath.ptr)) {
            xml_load_scene(fullXmlPath.ptr, world);
            project_add_map(fullXmlPath.ptr);
        }
    }

    // 2. Aplicar configuración de Tileset del archivo .procshit
    tileset_set_tile_size_px(tsTileW, tsTileH);
    tileset_set_tile_count(tsCount);
    if (tilesetPngRel[0] != '\0') {
        tileset_set_path_and_reload(tilesetPngRel.ptr, false);
    } else {
        tileset_reload_texture(false);
    }

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

void parse_inactive_list(const(char)* str) {
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
                tileset_set_tile_active(currentVal, false);
                currentVal = 0;
                inNumber = false;
            }
        }
    }
    if (inNumber) {
        tileset_set_tile_active(currentVal, false);
    }
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
