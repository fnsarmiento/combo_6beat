---
name: build-and-verify
description: >-
  Compila y verifica los dos subproyectos de Combo 6Beat (level_editor en D con DUB y source/combo_6beat en Java 17 con Maven offline).
  Usar siempre después de modificar código fuente en level_editor/ o source/combo_6beat/, o para diagnosticar errores de compilación.
---

# Build y Verificación de Combo 6Beat

Este proyecto tiene dos builds independientes que deben mantenerse verdes por separado.

## 1. `level_editor/` (Dlang + raylib-d + raygui)

Ejecutar desde `/home/sar/Documentos/Combo 6Beat/level_editor`:

```bash
dub build
```

### Qué verifica
- Compilación estricta bajo `nothrow: @nogc:` en todos los módulos bajo `source/`.
- Enlazado con `raylib-d` (`~>6.2.0`), librería del sistema `raylib` y `libraygui.a`.

### Errores frecuentes
- Uso accidental del GC (`string`, `~`, `new`, arrays dinámicos) falla en compilación con error `@nogc`. Reemplazar por buffers `char[N]` + `snprintf`/`strncpy` o `malloc`/`free`.
- No ejecutar el binario GUI `./level_editor` en entornos headless sin display.

## 2. `source/combo_6beat/` (Java 17 + Maven + Processing 4)

Ejecutar desde `/home/sar/Documentos/Combo 6Beat/source/combo_6beat`:

```bash
mvn -o compile
```

*(El flag `-o` coincide con `.zed/tasks.json` y evita descargas innecesarias usando la caché local de Maven).*

### Ejecución interactiva (solo con display gráfico activo)

```bash
mvn -o compile exec:java -Dexec.mainClass=game.Main
```

### Qué verifica
- Compatibilidad Java 17 (`pom.xml`), resolución de `org.processing:core:4.5.7`, `sojamo:controlP5:2.2.6` y código fuente de `engine/`, `game/` y `kuusisto/tinysound/`.
- Nota: si solo se modificaron archivos `.md` de documentación, aún conviene ejecutar ambos builds como verificación base de no-regresión si se solicita validación completa.
