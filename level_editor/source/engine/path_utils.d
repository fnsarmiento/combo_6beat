module engine.path_utils;

nothrow:
@nogc:

import raylib : GetWorkingDirectory, FileExists;
import core.stdc.stdio : snprintf;
import core.stdc.string : strlen, strncpy, strcmp, strncmp;

public:

/**
 * Extrae el directorio padre de una ruta de archivo.
 */
void path_get_dir(const(char)* filePath, char* outDir, size_t maxLen) {
    if (outDir is null || maxLen == 0) {
        return;
    }
    outDir[0] = '\0';
    if (filePath is null || filePath[0] == '\0') {
        return;
    }

    size_t len = strlen(filePath);
    int lastSlash = -1;
    for (int i = cast(int) len - 1; i >= 0; i--) {
        if (filePath[i] == '/' || filePath[i] == '\\') {
            lastSlash = i;
            break;
        }
    }

    if (lastSlash < 0) {
        snprintf(outDir, maxLen, ".");
    } else if (lastSlash == 0) {
        snprintf(outDir, maxLen, "/");
    } else {
        size_t copyLen = cast(size_t) lastSlash;
        if (copyLen >= maxLen) {
            copyLen = maxLen - 1;
        }
        strncpy(outDir, filePath, copyLen);
        outDir[copyLen] = '\0';
    }
}

/**
 * Normaliza una ruta resolviendo segmentos '.' y '..'.
 */
void path_normalize(const(char)* inputPath, char* outPath, size_t maxLen) {
    if (outPath is null || maxLen == 0) {
        return;
    }
    outPath[0] = '\0';
    if (inputPath is null || inputPath[0] == '\0') {
        return;
    }

    bool isAbs = (inputPath[0] == '/');

    // Separar en segmentos
    enum MAX_SEGMENTS = 64;
    enum SEG_LEN = 128;
    char[SEG_LEN][MAX_SEGMENTS] segments;
    int segCount = 0;

    size_t i = 0;
    size_t inLen = strlen(inputPath);

    while (i < inLen) {
        while (i < inLen && (inputPath[i] == '/' || inputPath[i] == '\\')) {
            i++;
        }
        if (i >= inLen) {
            break;
        }

        size_t start = i;
        while (i < inLen && inputPath[i] != '/' && inputPath[i] != '\\') {
            i++;
        }
        size_t tokLen = i - start;
        if (tokLen == 0) {
            continue;
        }

        if (tokLen == 1 && inputPath[start] == '.') {
            continue;
        }

        if (tokLen == 2 && inputPath[start] == '.' && inputPath[start + 1] == '.') {
            if (segCount > 0 && strcmp(segments[segCount - 1].ptr, "..") != 0) {
                segCount--;
            } else if (!isAbs && segCount < MAX_SEGMENTS) {
                snprintf(segments[segCount].ptr, SEG_LEN, "..");
                segCount++;
            }
            continue;
        }

        if (segCount < MAX_SEGMENTS) {
            size_t cLen = tokLen < (SEG_LEN - 1) ? tokLen : (SEG_LEN - 1);
            strncpy(segments[segCount].ptr, inputPath + start, cLen);
            segments[segCount][cLen] = '\0';
            segCount++;
        }
    }

    size_t offset = 0;
    if (isAbs) {
        outPath[0] = '/';
        outPath[1] = '\0';
        offset = 1;
    }

    for (int s = 0; s < segCount; s++) {
        const(char)* sep = (s > 0) ? "/" : "";
        int written = snprintf(outPath + offset, maxLen - offset, "%s%s", sep, segments[s].ptr);
        if (written > 0) {
            offset += cast(size_t) written;
            if (offset >= maxLen - 1) {
                break;
            }
        }
    }

    if (offset == 0) {
        snprintf(outPath, maxLen, ".");
    }
}

/**
 * Calcula el directorio base de recursos combinando el directorio del XML y el atributo 'resources' de <Map>.
 */
void path_resolve_resources_root(
    const(char)* xmlDir,
    const(char)* mapResourcesAttr,
    char* outRootDir,
    size_t maxLen
) {
    const(char)* baseXmlDir = (xmlDir !is null && xmlDir[0] != '\0')
        ? xmlDir
        : GetWorkingDirectory();

    const(char)* resRel = (mapResourcesAttr !is null && mapResourcesAttr[0] != '\0')
        ? mapResourcesAttr
        : "./";

    char[1024] combined;
    snprintf(combined.ptr, combined.length, "%s/%s", baseXmlDir, resRel);
    path_normalize(combined.ptr, outRootDir, maxLen);
}

/**
 * Convierte una ruta seleccionada (absoluta o relativa) en una ruta SIEMPRE relativa
 * respecto al directorio base de recursos del XML.
 */
void path_make_relative_to_resources(
    const(char)* selectedPath,
    const(char)* xmlDir,
    const(char)* mapResourcesAttr,
    char* outRelPath,
    size_t maxLen
) {
    if (outRelPath is null || maxLen == 0) {
        return;
    }
    outRelPath[0] = '\0';
    if (selectedPath is null || selectedPath[0] == '\0') {
        return;
    }

    // Si ya es relativa, normalizarla directamente
    if (selectedPath[0] != '/') {
        path_normalize(selectedPath, outRelPath, maxLen);
        return;
    }

    char[1024] rootDir;
    path_resolve_resources_root(xmlDir, mapResourcesAttr, rootDir.ptr, rootDir.length);

    char[1024] normTarget;
    path_normalize(selectedPath, normTarget.ptr, normTarget.length);

    compute_relative_between_abs(normTarget.ptr, rootDir.ptr, outRelPath, maxLen);
}

/**
 * Resuelve la ruta final en disco para cargar un recurso (PNG) en Raylib a partir de su ruta relativa.
 */
void path_resolve_for_loading(
    const(char)* relResourcePath,
    const(char)* xmlDir,
    const(char)* mapResourcesAttr,
    char* outFullPath,
    size_t maxLen
) {
    if (outFullPath is null || maxLen == 0) {
        return;
    }
    outFullPath[0] = '\0';
    if (relResourcePath is null || relResourcePath[0] == '\0') {
        return;
    }

    // 1. Intentar respecto a (xmlDir + "/" + mapResourcesAttr + "/" + relResourcePath)
    char[1024] resRoot;
    path_resolve_resources_root(xmlDir, mapResourcesAttr, resRoot.ptr, resRoot.length);

    char[1024] candidate;
    snprintf(candidate.ptr, candidate.length, "%s/%s", resRoot.ptr, relResourcePath);
    path_normalize(candidate.ptr, outFullPath, maxLen);
    if (FileExists(outFullPath)) {
        return;
    }

    // 2. Fallback respecto a xmlDir directamente
    if (xmlDir !is null && xmlDir[0] != '\0') {
        snprintf(candidate.ptr, candidate.length, "%s/%s", xmlDir, relResourcePath);
        path_normalize(candidate.ptr, outFullPath, maxLen);
        if (FileExists(outFullPath)) {
            return;
        }
    }

    // 3. Fallback respecto al directorio actual de trabajo
    path_normalize(relResourcePath, outFullPath, maxLen);
}

private:

void compute_relative_between_abs(
    const(char)* targetAbs,
    const(char)* baseAbs,
    char* outRel,
    size_t maxLen
) {
    enum MAX_PARTS = 64;
    enum PART_LEN = 128;

    char[PART_LEN][MAX_PARTS] targetParts;
    char[PART_LEN][MAX_PARTS] baseParts;

    int targetCount = split_path_parts(targetAbs, targetParts);
    int baseCount = split_path_parts(baseAbs, baseParts);

    int common = 0;
    while (common < targetCount && common < baseCount) {
        if (strcmp(targetParts[common].ptr, baseParts[common].ptr) != 0) {
            break;
        }
        common++;
    }

    size_t offset = 0;
    outRel[0] = '\0';

    // Subir directorios desde baseAbs hasta el ancestro común
    for (int i = common; i < baseCount; i++) {
        const(char)* prefix = (offset > 0) ? "/.." : "..";
        int written = snprintf(outRel + offset, maxLen - offset, "%s", prefix);
        if (written > 0) {
            offset += cast(size_t) written;
        }
    }

    // Bajar hacia targetAbs
    for (int i = common; i < targetCount; i++) {
        const(char)* sep = (offset > 0) ? "/" : "";
        int written = snprintf(outRel + offset, maxLen - offset, "%s%s", sep, targetParts[i].ptr);
        if (written > 0) {
            offset += cast(size_t) written;
        }
    }

    if (offset == 0) {
        snprintf(outRel, maxLen, ".");
    }
}

int split_path_parts(const(char)* pathStr, ref char[128][64] parts) {
    if (pathStr is null) {
        return 0;
    }
    int count = 0;
    size_t len = strlen(pathStr);
    size_t i = 0;

    while (i < len && count < 64) {
        while (i < len && (pathStr[i] == '/' || pathStr[i] == '\\')) {
            i++;
        }
        if (i >= len) {
            break;
        }
        size_t start = i;
        while (i < len && pathStr[i] != '/' && pathStr[i] != '\\') {
            i++;
        }
        size_t tokLen = i - start;
        if (tokLen > 0) {
            size_t copyLen = tokLen < 127 ? tokLen : 127;
            strncpy(parts[count].ptr, pathStr + start, copyLen);
            parts[count][copyLen] = '\0';
            count++;
        }
    }
    return count;
}
