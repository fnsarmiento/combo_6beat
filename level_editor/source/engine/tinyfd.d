module engine.tinyfd;

nothrow:
@nogc:

import core.stdc.stdio : FILE, fgets, snprintf;
import core.stdc.string : strlen, strncpy;
import core.sys.posix.stdio : popen, pclose;

public:

/**
 * Abre un diálogo nativo para seleccionar un archivo (estilo tinyfiledialogs).
 * Retorna el puntero al buffer interno con la ruta seleccionada, o null si se canceló.
 */
const(char)* tinyfd_openFileDialog(
    const(char)* title,
    const(char)* defaultPathAndFile,
    int numOfFilterPatterns,
    const(char)** filterPatterns,
    const(char)* singleFilterDescription,
    int allowMultipleSelects
) {
    return open_dialog_internal(title, defaultPathAndFile, numOfFilterPatterns, filterPatterns, false);
}

/**
 * Abre un diálogo nativo para guardar un archivo (estilo tinyfiledialogs).
 */
const(char)* tinyfd_saveFileDialog(
    const(char)* title,
    const(char)* defaultPathAndFile,
    int numOfFilterPatterns,
    const(char)** filterPatterns,
    const(char)* singleFilterDescription
) {
    return open_dialog_internal(title, defaultPathAndFile, numOfFilterPatterns, filterPatterns, true);
}

/**
 * Helper simplificado para seleccionar un archivo PNG y copiarlo a un buffer de destino.
 * Retorna true si el usuario seleccionó un archivo válido.
 */
bool open_png_file_dialog(char* outBuffer, size_t bufferSize, const(char)* title = "Seleccionar PNG") {
    if (outBuffer is null || bufferSize == 0) {
        return false;
    }

    const(char)*[2] patterns = ["*.png", "*.PNG"];
    const(char)* selected = tinyfd_openFileDialog(
        title,
        "",
        2,
        patterns.ptr,
        "PNG Images (*.png)",
        0
    );

    if (selected is null || selected[0] == '\0') {
        return false;
    }

    strncpy(outBuffer, selected, bufferSize - 1);
    outBuffer[bufferSize - 1] = '\0';
    return true;
}

/**
 * Helper para seleccionar un archivo de escena XML para cargar.
 */
bool open_xml_file_dialog(char* outBuffer, size_t bufferSize, const(char)* title = "Cargar Escena XML") {
    if (outBuffer is null || bufferSize == 0) {
        return false;
    }

    const(char)*[2] patterns = ["*.xml", "*.XML"];
    const(char)* selected = tinyfd_openFileDialog(
        title,
        "",
        2,
        patterns.ptr,
        "XML Scene (*.xml)",
        0
    );

    if (selected is null || selected[0] == '\0') {
        return false;
    }

    strncpy(outBuffer, selected, bufferSize - 1);
    outBuffer[bufferSize - 1] = '\0';
    return true;
}

/**
 * Helper para elegir dónde guardar un archivo de escena XML.
 */
bool save_xml_file_dialog(char* outBuffer, size_t bufferSize, const(char)* title = "Guardar Escena XML") {
    if (outBuffer is null || bufferSize == 0) {
        return false;
    }

    const(char)*[2] patterns = ["*.xml", "*.XML"];
    const(char)* selected = tinyfd_saveFileDialog(
        title,
        "scene.xml",
        2,
        patterns.ptr,
        "XML Scene (*.xml)"
    );

    if (selected is null || selected[0] == '\0') {
        return false;
    }

    strncpy(outBuffer, selected, bufferSize - 1);
    outBuffer[bufferSize - 1] = '\0';
    return true;
}

/**
 * Helper para seleccionar un archivo JSON de diálogos para cargar.
 */
bool open_json_file_dialog(char* outBuffer, size_t bufferSize, const(char)* title = "Cargar Dialogos JSON") {
    if (outBuffer is null || bufferSize == 0) {
        return false;
    }

    const(char)*[2] patterns = ["*.json", "*.JSON"];
    const(char)* selected = tinyfd_openFileDialog(
        title,
        "",
        2,
        patterns.ptr,
        "JSON Dialogs (*.json)",
        0
    );

    if (selected is null || selected[0] == '\0') {
        return false;
    }

    strncpy(outBuffer, selected, bufferSize - 1);
    outBuffer[bufferSize - 1] = '\0';
    return true;
}

/**
 * Helper para elegir dónde guardar un archivo JSON de diálogos.
 */
bool save_json_file_dialog(char* outBuffer, size_t bufferSize, const(char)* title = "Guardar Dialogos JSON") {
    if (outBuffer is null || bufferSize == 0) {
        return false;
    }

    const(char)*[2] patterns = ["*.json", "*.JSON"];
    const(char)* selected = tinyfd_saveFileDialog(
        title,
        "dialogs.json",
        2,
        patterns.ptr,
        "JSON Dialogs (*.json)"
    );

    if (selected is null || selected[0] == '\0') {
        return false;
    }

    strncpy(outBuffer, selected, bufferSize - 1);
    outBuffer[bufferSize - 1] = '\0';
    return true;
}

/**
 * Helper para seleccionar un archivo de proyecto .procshit para cargar.
 */
bool open_procshit_file_dialog(char* outBuffer, size_t bufferSize, const(char)* title = "Cargar Proyecto (.procshit)") {
    if (outBuffer is null || bufferSize == 0) {
        return false;
    }

    const(char)*[2] patterns = ["*.procshit", "*.PROCSHIT"];
    const(char)* selected = tinyfd_openFileDialog(
        title,
        "",
        2,
        patterns.ptr,
        "Project File (*.procshit)",
        0
    );

    if (selected is null || selected[0] == '\0') {
        return false;
    }

    strncpy(outBuffer, selected, bufferSize - 1);
    outBuffer[bufferSize - 1] = '\0';
    return true;
}

/**
 * Helper para elegir dónde guardar un archivo de proyecto .procshit.
 */
bool save_procshit_file_dialog(char* outBuffer, size_t bufferSize, const(char)* title = "Guardar Proyecto (.procshit)") {
    if (outBuffer is null || bufferSize == 0) {
        return false;
    }

    const(char)*[2] patterns = ["*.procshit", "*.PROCSHIT"];
    const(char)* selected = tinyfd_saveFileDialog(
        title,
        "project.procshit",
        2,
        patterns.ptr,
        "Project File (*.procshit)"
    );

    if (selected is null || selected[0] == '\0') {
        return false;
    }

    strncpy(outBuffer, selected, bufferSize - 1);
    outBuffer[bufferSize - 1] = '\0';
    return true;
}

private:

__gshared char[1024] dialog_result_buffer;

const(char)* open_dialog_internal(
    const(char)* title,
    const(char)* defaultPath,
    int numFilters,
    const(char)** filterPatterns,
    bool saveMode
) {
    dialog_result_buffer[0] = '\0';

    char[1024] cmd;
    const(char)* safeTitle = (title !is null && title[0] != '\0') ? title : "File Dialog";

    char[256] filterStr;
    filterStr[0] = '\0';
    if (numFilters > 0 && filterPatterns !is null) {
        size_t offset = 0;
        for (int i = 0; i < numFilters; i++) {
            if (filterPatterns[i] !is null) {
                const(char)* sep = (i > 0) ? " " : "";
                int written = snprintf(
                    filterStr.ptr + offset,
                    filterStr.length - offset,
                    "%s%s",
                    sep,
                    filterPatterns[i]
                );
                if (written > 0) {
                    offset += cast(size_t) written;
                    if (offset >= filterStr.length - 1) {
                        break;
                    }
                }
            }
        }
    }

    const(char)* zenitySaveFlags = saveMode ? "--save --confirm-overwrite" : "";
    const(char)* kdialogMode = saveMode ? "--getsavefilename" : "--getopenfilename";

    if (filterStr[0] != '\0') {
        snprintf(
            cmd.ptr,
            cmd.length,
            "zenity --file-selection %s --title=\"%s\" --file-filter=\"%s\" 2>/dev/null || kdialog %s . \"%s\" --title \"%s\" 2>/dev/null",
            zenitySaveFlags,
            safeTitle,
            filterStr.ptr,
            kdialogMode,
            filterStr.ptr,
            safeTitle
        );
    } else {
        snprintf(
            cmd.ptr,
            cmd.length,
            "zenity --file-selection %s --title=\"%s\" 2>/dev/null || kdialog %s . --title \"%s\" 2>/dev/null",
            zenitySaveFlags,
            safeTitle,
            kdialogMode,
            safeTitle
        );
    }

    FILE* pipe = popen(cmd.ptr, "r");
    if (pipe is null) {
        return null;
    }

    if (fgets(dialog_result_buffer.ptr, cast(int) dialog_result_buffer.length, pipe) is null) {
        pclose(pipe);
        return null;
    }

    pclose(pipe);

    size_t len = strlen(dialog_result_buffer.ptr);
    while (len > 0 && (dialog_result_buffer[len - 1] == '\n' || dialog_result_buffer[len - 1] == '\r')) {
        dialog_result_buffer[len - 1] = '\0';
        len--;
    }

    if (dialog_result_buffer[0] == '\0') {
        return null;
    }

    return dialog_result_buffer.ptr;
}
