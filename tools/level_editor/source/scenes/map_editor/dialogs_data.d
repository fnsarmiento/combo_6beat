module scenes.map_editor.dialogs_data;

nothrow:
@nogc:

import raylib : Vector2, FileExists;
import core.stdc.stdio : FILE, fopen, fclose, fseek, ftell, fread, fprintf, SEEK_END, SEEK_SET, snprintf;
import core.stdc.stdlib : malloc, free;
import core.stdc.string : strlen, strncpy, strcmp;

public:

enum int MAX_DIALOG_NODES = 128;
enum int MAX_DIALOG_LINKS = 256;

/**
 * Tipos de nodos disponibles en el Dialogs Editor (estilo Visual Shaders).
 */
enum DialogNodeType : int {
    Dialog   = 0, // Nodo de diálogo principal (title, desc, default, option1, option2)
    Load     = 1, // __load__ (reemplaza a __loadr__)
    SaveNext = 2, // __save_next__
    PlaySfx  = 3, // __playsfx__
    Define   = 4, // __define__ (estilo C)
    IfDef    = 5, // __ifdef__  (estilo C)
    IfNDef   = 6, // __ifndef__ (estilo C)
    GiveItem = 7  // __give_item__ (itemId o itemId:count)
}

/**
 * Índices de los pines de salida de un nodo tipo Dialog.
 */
enum DialogBranchSlot : int {
    Default = 0,
    Option1 = 1,
    Option2 = 2
}

/**
 * Nodo individual dentro del grafo visual de diálogos.
 */
struct DialogGraphNode {
    bool used           = false;
    int uid             = 0;
    DialogNodeType type = DialogNodeType.Dialog;
    Vector2 position    = Vector2(0.0f, 0.0f);

    // Campos para nodos de tipo Dialog
    char[64] id           = 0; // Ej: "__start__", "return", "narrator_response"
    char[128] title       = 0; // Ej: "[Narrador]"
    char[256] desc        = 0; // Texto del diálogo
    bool hasOption1       = false;
    char[128] option1Text = 0;
    bool hasOption2       = false;
    char[128] option2Text = 0;

    // Argumento para nodos de directiva (__load__, __save_next__, __playsfx__, __define__, __ifdef__, __ifndef__)
    char[256] argValue    = 0;

    // Redirecciones condicionales a nivel de nodo (ej. tras victoria o derrota en combate)
    char[128] nodeIfDef       = 0;
    char[128] nodeNextIfDef   = 0;
    char[128] nodeIfNDef      = 0;
    char[128] nodeNextIfNDef  = 0;
}

/**
 * Conexión (cable) entre el pin de salida (fromNodeUid, fromSlot) y el pin de entrada (toNodeUid, toSlot).
 */
struct DialogGraphLink {
    bool used       = false;
    int fromNodeUid = -1;
    int fromSlot    = 0;
    int toNodeUid   = -1;
    int toSlot      = 0;
}

/**
 * Grafo completo del Dialogs Editor.
 */
struct DialogGraph {
    DialogGraphNode[MAX_DIALOG_NODES] nodes;
    DialogGraphLink[MAX_DIALOG_LINKS] links;
    int selectedNodeUid = -1;
    int nextUid         = 1;
    char[512] jsonFilePath = 0;
}

/**
 * Inicializa el grafo con el ejemplo base del usuario (__start__, __load__, __save_next__, return, narrator_response).
 */
void dialog_graph_init_default(ref DialogGraph graph) {
    dialog_graph_clear(graph);

    // 1. Nodo "__start__"
    int startUid = dialog_graph_add_node(graph, DialogNodeType.Dialog, Vector2(60.0f, 80.0f));
    DialogGraphNode* nStart = dialog_graph_find_node(graph, startUid);
    if (nStart !is null) {
        snprintf(nStart.id.ptr, nStart.id.length, "__start__");
        nStart.title[0] = '\0';
        snprintf(nStart.desc.ptr, nStart.desc.length, "Asi comienzan las historias. xd");
    }

    // 2. Nodo __load__ conectado a default de __start__
    int loadUid = dialog_graph_add_node(graph, DialogNodeType.Load, Vector2(400.0f, 60.0f));
    DialogGraphNode* nLoad = dialog_graph_find_node(graph, loadUid);
    if (nLoad !is null) {
        snprintf(nLoad.argValue.ptr, nLoad.argValue.length, "resources/songs/glass beach.scene");
    }

    // 3. Nodo __save_next__ encadenado después de __load__
    int saveNextUid = dialog_graph_add_node(graph, DialogNodeType.SaveNext, Vector2(690.0f, 60.0f));
    DialogGraphNode* nSaveNext = dialog_graph_find_node(graph, saveNextUid);
    if (nSaveNext !is null) {
        snprintf(nSaveNext.argValue.ptr, nSaveNext.argValue.length, "return");
    }

    // 4. Nodo "return"
    int returnUid = dialog_graph_add_node(graph, DialogNodeType.Dialog, Vector2(380.0f, 250.0f));
    DialogGraphNode* nReturn = dialog_graph_find_node(graph, returnUid);
    if (nReturn !is null) {
        snprintf(nReturn.id.ptr, nReturn.id.length, "return");
        snprintf(nReturn.title.ptr, nReturn.title.length, "[Narrador]");
        snprintf(nReturn.desc.ptr, nReturn.desc.length, "Como lo hiciste hdp");
        nReturn.hasOption1 = true;
        snprintf(nReturn.option1Text.ptr, nReturn.option1Text.length, "No che xd");
        nReturn.hasOption2 = true;
        snprintf(nReturn.option2Text.ptr, nReturn.option2Text.length, "Haciendolo pe causita");
    }

    // 5. Nodo "narrator_response"
    int respUid = dialog_graph_add_node(graph, DialogNodeType.Dialog, Vector2(760.0f, 280.0f));
    DialogGraphNode* nResp = dialog_graph_find_node(graph, respUid);
    if (nResp !is null) {
        snprintf(nResp.id.ptr, nResp.id.length, "narrator_response");
        snprintf(nResp.title.ptr, nResp.title.length, "[Narrador]");
        snprintf(nResp.desc.ptr, nResp.desc.length, "Niga");
    }

    // Conectar cables del ejemplo
    dialog_graph_connect(graph, startUid, DialogBranchSlot.Default, loadUid, 0);
    dialog_graph_connect(graph, loadUid, 0, saveNextUid, 0);
    dialog_graph_connect(graph, saveNextUid, 0, returnUid, 0);
    dialog_graph_connect(graph, returnUid, DialogBranchSlot.Option2, respUid, 0);

    graph.selectedNodeUid = startUid;
}

/**
 * Limpia todos los nodos y conexiones del grafo.
 */
void dialog_graph_clear(ref DialogGraph graph) {
    for (int i = 0; i < MAX_DIALOG_NODES; i++) {
        graph.nodes[i] = DialogGraphNode.init;
    }
    for (int i = 0; i < MAX_DIALOG_LINKS; i++) {
        graph.links[i] = DialogGraphLink.init;
    }
    graph.selectedNodeUid = -1;
    graph.nextUid = 1;
}

/**
 * Limpia el historial de Undo del Dialogs Editor.
 */
void dialog_graph_clear_undo() {
    dlg_undo_count = 0;
}

/**
 * Guarda una instantánea del grafo actual en la pila de Undo (Ctrl+Z) exclusiva del Dialogs Editor.
 */
void dialog_graph_push_undo(ref const(DialogGraph) graph) {
    if (dlg_undo_count >= MAX_DIALOG_UNDO) {
        for (int i = 0; i < MAX_DIALOG_UNDO - 1; i++) {
            dlg_undo_stack[i] = dlg_undo_stack[i + 1];
        }
        dlg_undo_count = MAX_DIALOG_UNDO - 1;
    }
    dlg_undo_stack[dlg_undo_count] = graph;
    dlg_undo_count++;
}

/**
 * Restaura el estado previo del grafo de diálogos (Ctrl+Z).
 */
bool dialog_graph_undo(ref DialogGraph graph) {
    if (dlg_undo_count <= 0) {
        return false;
    }
    dlg_undo_count--;
    graph = dlg_undo_stack[dlg_undo_count];
    return true;
}

/**
 * Agrega un nuevo nodo al grafo visual en la posición indicada.
 */
int dialog_graph_add_node(ref DialogGraph graph, DialogNodeType type, Vector2 pos) {
    int slot = -1;
    for (int i = 0; i < MAX_DIALOG_NODES; i++) {
        if (!graph.nodes[i].used) {
            slot = i;
            break;
        }
    }
    if (slot < 0) {
        return -1;
    }

    int uid = graph.nextUid++;
    DialogGraphNode* node = &graph.nodes[slot];
    *node = DialogGraphNode.init;
    node.used = true;
    node.uid = uid;
    node.type = type;
    node.position = pos;

    final switch (type) {
        case DialogNodeType.Dialog:
            if (uid == 1) {
                snprintf(node.id.ptr, node.id.length, "__start__");
            } else {
                snprintf(node.id.ptr, node.id.length, "dialog_%d", uid);
            }
            snprintf(node.title.ptr, node.title.length, "[Personaje]");
            snprintf(node.desc.ptr, node.desc.length, "Texto del dialogo...");
            break;
        case DialogNodeType.Load:
            snprintf(node.argValue.ptr, node.argValue.length, "resources/scenes/next.scene");
            break;
        case DialogNodeType.SaveNext:
            snprintf(node.argValue.ptr, node.argValue.length, "return");
            break;
        case DialogNodeType.PlaySfx:
            snprintf(node.argValue.ptr, node.argValue.length, "resources/sfx/sound.wav");
            break;
        case DialogNodeType.Define:
            snprintf(node.argValue.ptr, node.argValue.length, "FLAG_EVENTO");
            break;
        case DialogNodeType.IfDef:
            snprintf(node.argValue.ptr, node.argValue.length, "FLAG_EVENTO");
            break;
        case DialogNodeType.IfNDef:
            snprintf(node.argValue.ptr, node.argValue.length, "FLAG_EVENTO");
            break;
        case DialogNodeType.GiveItem:
            snprintf(node.argValue.ptr, node.argValue.length, "potion_small:1");
            break;
    }

    graph.selectedNodeUid = uid;
    return uid;
}

/**
 * Elimina un nodo y todos los cables conectados a él.
 */
void dialog_graph_remove_node(ref DialogGraph graph, int uid) {
    if (uid < 0) {
        return;
    }

    for (int i = 0; i < MAX_DIALOG_LINKS; i++) {
        if (graph.links[i].used &&
            (graph.links[i].fromNodeUid == uid || graph.links[i].toNodeUid == uid)) {
            graph.links[i].used = false;
        }
    }

    for (int i = 0; i < MAX_DIALOG_NODES; i++) {
        if (graph.nodes[i].used && graph.nodes[i].uid == uid) {
            graph.nodes[i].used = false;
            break;
        }
    }

    if (graph.selectedNodeUid == uid) {
        graph.selectedNodeUid = -1;
    }
}

/**
 * Busca un nodo por su uid.
 */
DialogGraphNode* dialog_graph_find_node(ref DialogGraph graph, int uid) {
    if (uid < 0) {
        return null;
    }
    for (int i = 0; i < MAX_DIALOG_NODES; i++) {
        if (graph.nodes[i].used && graph.nodes[i].uid == uid) {
            return &graph.nodes[i];
        }
    }
    return null;
}

/**
 * Busca un nodo de tipo Dialog por su nombre/ID textual.
 */
DialogGraphNode* dialog_graph_find_by_id(ref DialogGraph graph, const(char)* idStr) {
    if (idStr is null || idStr[0] == '\0') {
        return null;
    }
    for (int i = 0; i < MAX_DIALOG_NODES; i++) {
        if (graph.nodes[i].used &&
            graph.nodes[i].type == DialogNodeType.Dialog &&
            strcmp(graph.nodes[i].id.ptr, idStr) == 0) {
            return &graph.nodes[i];
        }
    }
    return null;
}

/**
 * Conecta el pin de salida (fromUid, fromSlot) con el pin de entrada (toUid, toSlot).
 * Cada pin de salida tiene a lo sumo un cable saliente (reemplaza el anterior si existía).
 */
bool dialog_graph_connect(ref DialogGraph graph, int fromUid, int fromSlot, int toUid, int toSlot) {
    if (fromUid < 0 || toUid < 0 || fromUid == toUid) {
        return false;
    }

    // Reemplazar conexión saliente previa desde el mismo pin
    dialog_graph_disconnect_output(graph, fromUid, fromSlot);

    for (int i = 0; i < MAX_DIALOG_LINKS; i++) {
        if (!graph.links[i].used) {
            graph.links[i].used        = true;
            graph.links[i].fromNodeUid = fromUid;
            graph.links[i].fromSlot    = fromSlot;
            graph.links[i].toNodeUid   = toUid;
            graph.links[i].toSlot      = toSlot;

            // Si un nodo __save_next__ se conecta a un Dialog, sincronizar automáticamente su argValue con el ID destino
            DialogGraphNode* srcNode = dialog_graph_find_node(graph, fromUid);
            DialogGraphNode* dstNode = dialog_graph_find_node(graph, toUid);
            if (srcNode !is null && dstNode !is null &&
                srcNode.type == DialogNodeType.SaveNext &&
                dstNode.type == DialogNodeType.Dialog) {
                strncpy(srcNode.argValue.ptr, dstNode.id.ptr, srcNode.argValue.length - 1);
                srcNode.argValue[srcNode.argValue.length - 1] = '\0';
            }
            return true;
        }
    }
    return false;
}

/**
 * Desconecta cualquier cable que salga de (fromUid, fromSlot).
 */
void dialog_graph_disconnect_output(ref DialogGraph graph, int fromUid, int fromSlot) {
    for (int i = 0; i < MAX_DIALOG_LINKS; i++) {
        if (graph.links[i].used &&
            graph.links[i].fromNodeUid == fromUid &&
            graph.links[i].fromSlot == fromSlot) {
            graph.links[i].used = false;
        }
    }
}

/**
 * Desconecta cualquier cable que entre a (toUid, toSlot).
 */
void dialog_graph_disconnect_input(ref DialogGraph graph, int toUid, int toSlot) {
    for (int i = 0; i < MAX_DIALOG_LINKS; i++) {
        if (graph.links[i].used &&
            graph.links[i].toNodeUid == toUid &&
            graph.links[i].toSlot == toSlot) {
            graph.links[i].used = false;
        }
    }
}

/**
 * Retorna el nodo destino conectado al pin de salida (fromUid, fromSlot), o null si no hay conexión.
 */
DialogGraphNode* dialog_graph_get_connected_target(ref DialogGraph graph, int fromUid, int fromSlot) {
    for (int i = 0; i < MAX_DIALOG_LINKS; i++) {
        if (graph.links[i].used &&
            graph.links[i].fromNodeUid == fromUid &&
            graph.links[i].fromSlot == fromSlot) {
            return dialog_graph_find_node(graph, graph.links[i].toNodeUid);
        }
    }
    return null;
}

/**
 * Etiqueta legible para cada tipo de nodo.
 */
const(char)* dialog_node_type_title(DialogNodeType type) {
    final switch (type) {
        case DialogNodeType.Dialog:   return "Dialog";
        case DialogNodeType.Load:     return "__load__";
        case DialogNodeType.SaveNext: return "__save_next__";
        case DialogNodeType.PlaySfx:  return "__playsfx__";
        case DialogNodeType.Define:   return "__define__";
        case DialogNodeType.IfDef:    return "__ifdef__";
        case DialogNodeType.IfNDef:   return "__ifndef__";
        case DialogNodeType.GiveItem: return "__give_item__";
    }
}

/**
 * Guarda el grafo de diálogos actual en un archivo JSON siguiendo el formato de DialogManager,
 * usando __load__, __save_next__, __playsfx__, __define__, __ifdef__, __ifndef__ y next.
 */
bool dialog_graph_save_json(const(char)* filePath, ref DialogGraph graph) {
    if (filePath is null || filePath[0] == '\0') {
        return false;
    }

    FILE* fp = fopen(filePath, "wb");
    if (fp is null) {
        return false;
    }

    strncpy(graph.jsonFilePath.ptr, filePath, graph.jsonFilePath.length - 1);
    graph.jsonFilePath[graph.jsonFilePath.length - 1] = '\0';

    fprintf(fp, "{\n");

    int dialogCount = 0;
    for (int i = 0; i < MAX_DIALOG_NODES; i++) {
        if (graph.nodes[i].used && graph.nodes[i].type == DialogNodeType.Dialog) {
            dialogCount++;
        }
    }

    int writtenDialogs = 0;
    for (int i = 0; i < MAX_DIALOG_NODES; i++) {
        if (!graph.nodes[i].used || graph.nodes[i].type != DialogNodeType.Dialog) {
            continue;
        }

        DialogGraphNode* dNode = &graph.nodes[i];
        writtenDialogs++;
        bool isLastDialog = (writtenDialogs == dialogCount);

        fprintf(fp, "  \"%s\": {\n", dNode.id.ptr);
        fprintf(fp, "    \"title\": \"%s\",\n", dNode.title.ptr);
        fprintf(fp, "    \"desc\": \"%s\"", dNode.desc.ptr);
        if (dNode.nodeIfDef[0] != '\0') fprintf(fp, ",\n    \"__ifdef__\": \"%s\"", dNode.nodeIfDef.ptr);
        if (dNode.nodeNextIfDef[0] != '\0') fprintf(fp, ",\n    \"__next_ifdef__\": \"%s\"", dNode.nodeNextIfDef.ptr);
        if (dNode.nodeIfNDef[0] != '\0') fprintf(fp, ",\n    \"__ifndef__\": \"%s\"", dNode.nodeIfNDef.ptr);
        if (dNode.nodeNextIfNDef[0] != '\0') fprintf(fp, ",\n    \"__next_ifndef__\": \"%s\"", dNode.nodeNextIfNDef.ptr);

        // Evaluar ramas: default, option1, option2
        BranchDirectives defBranch = collect_branch_directives(graph, dNode.uid, DialogBranchSlot.Default);
        BranchDirectives opt1Branch = collect_branch_directives(graph, dNode.uid, DialogBranchSlot.Option1);
        BranchDirectives opt2Branch = collect_branch_directives(graph, dNode.uid, DialogBranchSlot.Option2);

        if (defBranch.hasAny()) {
            fprintf(fp, ",\n    \"default\": {\n");
            write_branch_fields(fp, null, defBranch);
            fprintf(fp, "    }");
        }

        if (dNode.hasOption1 || opt1Branch.hasAny()) {
            fprintf(fp, ",\n    \"option1\": {\n");
            write_branch_fields(fp, dNode.option1Text.ptr, opt1Branch);
            fprintf(fp, "    }");
        }

        if (dNode.hasOption2 || opt2Branch.hasAny()) {
            fprintf(fp, ",\n    \"option2\": {\n");
            write_branch_fields(fp, dNode.option2Text.ptr, opt2Branch);
            fprintf(fp, "    }");
        }

        const(char)* trailingComma = isLastDialog ? "" : ",";
        fprintf(fp, "\n  }%s\n", trailingComma);
    }

    fprintf(fp, "}\n");
    fclose(fp);
    return true;
}

/**
 * Carga un archivo JSON de diálogos y reconstruye automáticamente los nodos y conexiones visuales.
 */
bool dialog_graph_load_json(const(char)* filePath, ref DialogGraph graph) {
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

    if (fileSize <= 0 || fileSize > 8 * 1024 * 1024) {
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

    dialog_graph_clear(graph);
    strncpy(graph.jsonFilePath.ptr, filePath, graph.jsonFilePath.length - 1);
    graph.jsonFilePath[graph.jsonFilePath.length - 1] = '\0';

    parse_json_dialogs(buffer, bytesRead, graph);
    dialog_graph_clear_undo();

    free(buffer);
    return true;
}

private:

enum int MAX_DIALOG_UNDO = 16;
__gshared DialogGraph[MAX_DIALOG_UNDO] dlg_undo_stack;
__gshared int dlg_undo_count = 0;

struct BranchDirectives {
    char[256] loadVal         = 0;
    char[128] saveNextVal     = 0;
    char[128] saveNextLoseVal = 0;
    char[256] playSfxVal      = 0;
    char[128] defineVal       = 0;
    char[128] ifdefVal        = 0;
    char[128] ifndefVal       = 0;
    char[128] giveItemVal     = 0;
    char[128] nextVal         = 0;

    bool hasAny() const nothrow @nogc {
        return loadVal[0] != '\0' ||
               saveNextVal[0] != '\0' ||
               saveNextLoseVal[0] != '\0' ||
               playSfxVal[0] != '\0' ||
               defineVal[0] != '\0' ||
               ifdefVal[0] != '\0' ||
               ifndefVal[0] != '\0' ||
               giveItemVal[0] != '\0' ||
               nextVal[0] != '\0';
    }
}

/**
 * Recorre la cadena de nodos conectada a una rama (default, option1, option2) de un Dialog
 * y recolecta todas las directivas (__load__, __save_next__, __playsfx__, __define__, __ifdef__, __ifndef__, next).
 */
BranchDirectives collect_branch_directives(ref DialogGraph graph, int dialogUid, int branchSlot) {
    BranchDirectives res;
    DialogGraphNode* cur = dialog_graph_get_connected_target(graph, dialogUid, branchSlot);

    int safety = 0;
    while (cur !is null && safety < 32) {
        safety++;
        final switch (cur.type) {
            case DialogNodeType.Dialog:
                strncpy(res.nextVal.ptr, cur.id.ptr, res.nextVal.length - 1);
                return res;

            case DialogNodeType.Load:
                strncpy(res.loadVal.ptr, cur.argValue.ptr, res.loadVal.length - 1);
                cur = dialog_graph_get_connected_target(graph, cur.uid, 0);
                break;

            case DialogNodeType.SaveNext:
                DialogGraphNode* afterSave = dialog_graph_get_connected_target(graph, cur.uid, 0);
                if (afterSave !is null && afterSave.type == DialogNodeType.Dialog) {
                    strncpy(res.saveNextVal.ptr, afterSave.id.ptr, res.saveNextVal.length - 1);
                    return res;
                } else {
                    strncpy(res.saveNextVal.ptr, cur.argValue.ptr, res.saveNextVal.length - 1);
                    cur = afterSave;
                }
                break;

            case DialogNodeType.PlaySfx:
                strncpy(res.playSfxVal.ptr, cur.argValue.ptr, res.playSfxVal.length - 1);
                cur = dialog_graph_get_connected_target(graph, cur.uid, 0);
                break;

            case DialogNodeType.Define:
                strncpy(res.defineVal.ptr, cur.argValue.ptr, res.defineVal.length - 1);
                cur = dialog_graph_get_connected_target(graph, cur.uid, 0);
                break;

            case DialogNodeType.IfDef:
                strncpy(res.ifdefVal.ptr, cur.argValue.ptr, res.ifdefVal.length - 1);
                cur = dialog_graph_get_connected_target(graph, cur.uid, 0);
                break;

            case DialogNodeType.IfNDef:
                strncpy(res.ifndefVal.ptr, cur.argValue.ptr, res.ifndefVal.length - 1);
                cur = dialog_graph_get_connected_target(graph, cur.uid, 0);
                break;

            case DialogNodeType.GiveItem:
                strncpy(res.giveItemVal.ptr, cur.argValue.ptr, res.giveItemVal.length - 1);
                cur = dialog_graph_get_connected_target(graph, cur.uid, 0);
                break;
        }
    }

    return res;
}

void write_branch_fields(FILE* fp, const(char)* optionText, ref const(BranchDirectives) dir) {
    bool needsComma = false;

    void emit_field(const(char)* k, const(char)* v) {
        if (v is null || v[0] == '\0') {
            return;
        }
        if (needsComma) {
            fprintf(fp, ",\n");
        }
        fprintf(fp, "      \"%s\": \"%s\"", k, v);
        needsComma = true;
    }

    if (optionText !is null) {
        fprintf(fp, "      \"text\": \"%s\"", optionText);
        needsComma = true;
    }

    emit_field("__define__", dir.defineVal.ptr);
    emit_field("__ifdef__", dir.ifdefVal.ptr);
    emit_field("__ifndef__", dir.ifndefVal.ptr);
    emit_field("__give_item__", dir.giveItemVal.ptr);
    emit_field("__playsfx__", dir.playSfxVal.ptr);
    emit_field("__load__", dir.loadVal.ptr);
    emit_field("__save_next__", dir.saveNextVal.ptr);
    emit_field("__save_next_lose__", dir.saveNextLoseVal.ptr);
    emit_field("next", dir.nextVal.ptr);

    if (needsComma) {
        fprintf(fp, "\n");
    }
}

struct PendingBranchLink {
    bool used = false;
    int dialogUid = -1;
    int branchSlot = 0;
    BranchDirectives dir;
}

bool is_json_ws(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
}

void skip_ws(const(char)* src, size_t len, ref size_t pos) {
    while (pos < len && is_json_ws(src[pos])) {
        pos++;
    }
}

bool parse_json_string(const(char)* src, size_t len, ref size_t pos, char* outBuf, size_t maxLen) {
    if (outBuf !is null && maxLen > 0) {
        outBuf[0] = '\0';
    }
    skip_ws(src, len, pos);
    if (pos >= len || src[pos] != '"') {
        return false;
    }
    pos++; // saltar '"'
    size_t idx = 0;
    while (pos < len && src[pos] != '"') {
        if (src[pos] == '\\' && pos + 1 < len) {
            pos++;
        }
        if (outBuf !is null && idx < maxLen - 1) {
            outBuf[idx++] = src[pos];
        }
        pos++;
    }
    if (outBuf !is null && maxLen > 0) {
        outBuf[idx] = '\0';
    }
    if (pos < len && src[pos] == '"') {
        pos++;
    }
    return true;
}

void parse_json_dialogs(const(char)* src, size_t len, ref DialogGraph graph) {
    PendingBranchLink[128] pending;
    int pendingCount = 0;

    size_t pos = 0;
    skip_ws(src, len, pos);
    if (pos >= len || src[pos] != '{') {
        return;
    }
    pos++; // saltar '{' raíz

    int dialogIndex = 0;

    while (pos < len) {
        skip_ws(src, len, pos);
        if (pos >= len || src[pos] == '}') {
            break;
        }
        if (src[pos] == ',') {
            pos++;
            continue;
        }

        char[64] dialogId = 0;
        if (!parse_json_string(src, len, pos, dialogId.ptr, dialogId.length)) {
            pos++;
            continue;
        }

        skip_ws(src, len, pos);
        if (pos < len && src[pos] == ':') {
            pos++;
        }
        skip_ws(src, len, pos);
        if (pos >= len || src[pos] != '{') {
            continue;
        }
        pos++; // entrar al objeto del diálogo

        float colX = 80.0f + (dialogIndex % 3) * 420.0f;
        float rowY = 80.0f + (dialogIndex / 3) * 260.0f;
        dialogIndex++;

        int dUid = dialog_graph_add_node(graph, DialogNodeType.Dialog, Vector2(colX, rowY));
        DialogGraphNode* dNode = dialog_graph_find_node(graph, dUid);
        if (dNode !is null) {
            strncpy(dNode.id.ptr, dialogId.ptr, dNode.id.length - 1);
            dNode.id[dNode.id.length - 1] = '\0';
            dNode.title[0] = '\0';
            dNode.desc[0] = '\0';
        }

        // Leer propiedades internas del diálogo (title, desc, default, option1, option2)
        while (pos < len) {
            skip_ws(src, len, pos);
            if (pos >= len) break;
            if (src[pos] == '}') {
                pos++;
                break;
            }
            if (src[pos] == ',') {
                pos++;
                continue;
            }

            char[64] propName = 0;
            if (!parse_json_string(src, len, pos, propName.ptr, propName.length)) {
                pos++;
                continue;
            }

            skip_ws(src, len, pos);
            if (pos < len && src[pos] == ':') {
                pos++;
            }
            skip_ws(src, len, pos);

            if (pos < len && src[pos] == '"') {
                char[256] strVal = 0;
                parse_json_string(src, len, pos, strVal.ptr, strVal.length);
                if (dNode !is null) {
                    if (strcmp(propName.ptr, "title") == 0) {
                        strncpy(dNode.title.ptr, strVal.ptr, dNode.title.length - 1);
                    } else if (strcmp(propName.ptr, "desc") == 0) {
                        strncpy(dNode.desc.ptr, strVal.ptr, dNode.desc.length - 1);
                    } else if (strcmp(propName.ptr, "__ifdef__") == 0) {
                        strncpy(dNode.nodeIfDef.ptr, strVal.ptr, dNode.nodeIfDef.length - 1);
                    } else if (strcmp(propName.ptr, "__next_ifdef__") == 0) {
                        strncpy(dNode.nodeNextIfDef.ptr, strVal.ptr, dNode.nodeNextIfDef.length - 1);
                    } else if (strcmp(propName.ptr, "__ifndef__") == 0) {
                        strncpy(dNode.nodeIfNDef.ptr, strVal.ptr, dNode.nodeIfNDef.length - 1);
                    } else if (strcmp(propName.ptr, "__next_ifndef__") == 0) {
                        strncpy(dNode.nodeNextIfNDef.ptr, strVal.ptr, dNode.nodeNextIfNDef.length - 1);
                    }
                }
            } else if (pos < len && src[pos] == '{') {
                pos++; // entrar a sub-objeto (default, option1, option2)
                int bSlot = DialogBranchSlot.Default;
                if (strcmp(propName.ptr, "option1") == 0) {
                    bSlot = DialogBranchSlot.Option1;
                    if (dNode !is null) dNode.hasOption1 = true;
                } else if (strcmp(propName.ptr, "option2") == 0) {
                    bSlot = DialogBranchSlot.Option2;
                    if (dNode !is null) dNode.hasOption2 = true;
                }

                BranchDirectives bDir;
                while (pos < len) {
                    skip_ws(src, len, pos);
                    if (pos >= len) break;
                    if (src[pos] == '}') {
                        pos++;
                        break;
                    }
                    if (src[pos] == ',') {
                        pos++;
                        continue;
                    }

                    char[64] bKey = 0;
                    if (!parse_json_string(src, len, pos, bKey.ptr, bKey.length)) {
                        pos++;
                        continue;
                    }
                    skip_ws(src, len, pos);
                    if (pos < len && src[pos] == ':') pos++;
                    skip_ws(src, len, pos);

                    char[256] bVal = 0;
                    if (pos < len && src[pos] == '"') {
                        parse_json_string(src, len, pos, bVal.ptr, bVal.length);
                    }

                    if (strcmp(bKey.ptr, "text") == 0 && dNode !is null) {
                        if (bSlot == DialogBranchSlot.Option1) {
                            strncpy(dNode.option1Text.ptr, bVal.ptr, dNode.option1Text.length - 1);
                        } else if (bSlot == DialogBranchSlot.Option2) {
                            strncpy(dNode.option2Text.ptr, bVal.ptr, dNode.option2Text.length - 1);
                        }
                    } else if (strcmp(bKey.ptr, "__load__") == 0 || strcmp(bKey.ptr, "__loadr__") == 0 || strcmp(bKey.ptr, "__loads__") == 0) {
                        strncpy(bDir.loadVal.ptr, bVal.ptr, bDir.loadVal.length - 1);
                    } else if (strcmp(bKey.ptr, "__save_next__") == 0) {
                        strncpy(bDir.saveNextVal.ptr, bVal.ptr, bDir.saveNextVal.length - 1);
                    } else if (strcmp(bKey.ptr, "__save_next_lose__") == 0) {
                        strncpy(bDir.saveNextLoseVal.ptr, bVal.ptr, bDir.saveNextLoseVal.length - 1);
                    } else if (strcmp(bKey.ptr, "__playsfx__") == 0) {
                        strncpy(bDir.playSfxVal.ptr, bVal.ptr, bDir.playSfxVal.length - 1);
                    } else if (strcmp(bKey.ptr, "__define__") == 0) {
                        strncpy(bDir.defineVal.ptr, bVal.ptr, bDir.defineVal.length - 1);
                    } else if (strcmp(bKey.ptr, "__ifdef__") == 0) {
                        strncpy(bDir.ifdefVal.ptr, bVal.ptr, bDir.ifdefVal.length - 1);
                    } else if (strcmp(bKey.ptr, "__ifndef__") == 0) {
                        strncpy(bDir.ifndefVal.ptr, bVal.ptr, bDir.ifndefVal.length - 1);
                    } else if (strcmp(bKey.ptr, "__give_item__") == 0) {
                        strncpy(bDir.giveItemVal.ptr, bVal.ptr, bDir.giveItemVal.length - 1);
                    } else if (strcmp(bKey.ptr, "next") == 0) {
                        strncpy(bDir.nextVal.ptr, bVal.ptr, bDir.nextVal.length - 1);
                    }
                }

                if (bDir.hasAny() && pendingCount < cast(int) pending.length) {
                    pending[pendingCount].used = true;
                    pending[pendingCount].dialogUid = dUid;
                    pending[pendingCount].branchSlot = bSlot;
                    pending[pendingCount].dir = bDir;
                    pendingCount++;
                }
            }
        }
    }

    // Reconstruir los nodos de directiva y las conexiones visuales
    for (int i = 0; i < pendingCount; i++) {
        if (!pending[i].used) continue;

        DialogGraphNode* srcDialog = dialog_graph_find_node(graph, pending[i].dialogUid);
        if (srcDialog is null) continue;

        int prevUid = srcDialog.uid;
        int prevSlot = pending[i].branchSlot;
        Vector2 chainPos = Vector2(
            srcDialog.position.x + 280.0f,
            srcDialog.position.y + pending[i].branchSlot * 55.0f
        );

        void append_directive_node(DialogNodeType nType, const(char)* val) {
            if (val is null || val[0] == '\0') return;
            int nUid = dialog_graph_add_node(graph, nType, chainPos);
            DialogGraphNode* created = dialog_graph_find_node(graph, nUid);
            if (created !is null) {
                strncpy(created.argValue.ptr, val, created.argValue.length - 1);
                created.argValue[created.argValue.length - 1] = '\0';
            }
            dialog_graph_connect(graph, prevUid, prevSlot, nUid, 0);
            prevUid = nUid;
            prevSlot = 0;
            chainPos.x += 240.0f;
        }

        append_directive_node(DialogNodeType.Define, pending[i].dir.defineVal.ptr);
        append_directive_node(DialogNodeType.IfDef, pending[i].dir.ifdefVal.ptr);
        append_directive_node(DialogNodeType.IfNDef, pending[i].dir.ifndefVal.ptr);
        append_directive_node(DialogNodeType.GiveItem, pending[i].dir.giveItemVal.ptr);
        append_directive_node(DialogNodeType.PlaySfx, pending[i].dir.playSfxVal.ptr);
        append_directive_node(DialogNodeType.Load, pending[i].dir.loadVal.ptr);
        append_directive_node(DialogNodeType.SaveNext, pending[i].dir.saveNextVal.ptr);

        if (pending[i].dir.nextVal[0] != '\0') {
            DialogGraphNode* targetDialog = dialog_graph_find_by_id(graph, pending[i].dir.nextVal.ptr);
            if (targetDialog !is null) {
                dialog_graph_connect(graph, prevUid, prevSlot, targetDialog.uid, 0);
            }
        } else if (pending[i].dir.saveNextVal[0] != '\0') {
            DialogGraphNode* saveTarget = dialog_graph_find_by_id(graph, pending[i].dir.saveNextVal.ptr);
            if (saveTarget !is null) {
                dialog_graph_connect(graph, prevUid, prevSlot, saveTarget.uid, 0);
            }
        }
    }
}
