# source/combo_6beat — Guía para IA (Java 17 + Maven + Processing 4)

Runtime principal de *Combo 6Beat* en Java 17 sobre Processing `core:4.5.7`, `controlP5:2.2.6` y `TinySound`.

## Build y Ejecución

- **Compilación offline (obligatoria tras cambios):**
  `mvn -o compile` (en `source/combo_6beat/`).
- **Ejecución (requiere entorno gráfico / display):**
  `mvn -o compile exec:java -Dexec.mainClass=game.Main` (configurado también en `.zed/tasks.json`).
- **Nota de configuración IDE:** `.zed/debug.json` y `.zed/tasks.json` existen para Zed; `libs/controlP5.jar` y `pom.xml` gestionan dependencias.

## Arquitectura y Reglas Globales del Subproyecto

- **Separación estricta `engine/` vs `game/`:**
  - `src/main/java/engine/`: Motor genérico reutilizable. **Nunca importar paquetes `game.*` dentro de `engine/`.**
  - `src/main/java/game/`: Lógica específica de *Combo 6Beat* (escenas, entidades, notas `.osu`, configuración, flujo RPG/Rítmico).
  - Ver detalles específicos en `src/main/java/engine/GEMINI.md` y `src/main/java/game/GEMINI.md`.
- **Resolución virtual pixel art:**
  - Ventana física `1920x1080` (`PConstants.P2D`), framebuffer virtual `_virtualScreen` de `480x270` (`Global.BASE_W` x `Global.BASE_H`) con `textureSampling(2)`.
  - Los gráficos de escena (`Scene.draw(PGraphics g)`) se dibujan en coordenadas virtuales `480x270`.
  - Los elementos de `ControlP5` se dibujan sobre la pantalla física (`_cp5.draw()` fuera de `_virtualScreen`), por lo que deben escalar posiciones/tamaños respecto a la ventana real (`Global.resizeWidth`/`resizeHeight` o equivalente).
- **Contratos de datos:** Todo XML de escena o JSON de diálogo debe ser compatible con `level_editor/` (ver `resources/GEMINI.md` y `.agents/skills/scene-dialog-contracts/SKILL.md`).

## Estado del Repositorio Git Interno

- Este directorio tiene su propio `.git`.
- Actualmente el working tree contiene un refactor en curso: se eliminó el antiguo editor embebido (`game/editor/*`) y `.idea/*`, y hay cambios sin commitear en `engine/`, `game/` y `target/`.
- **Reglas de higiene:**
  - No recrear código de edición dentro de `game.editor.*`.
  - No editar manualmente archivos `.class` bajo `target/`.
  - No hacer `git commit`, `git reset` ni descartar cambios del usuario sin autorización explícita.
