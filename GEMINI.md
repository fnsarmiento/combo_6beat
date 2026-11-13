# Combo 6Beat — Contexto Raíz del Proyecto

Videojuego 2D con estética pixel art que combina exploración RPG top-down (inspiración *Everhood*) con combate rítmico de 6 carriles (inspiración *Rift of the NecroDancer*). La propuesta formal del trabajo final de 5to año está en `Propuesta de Tema.docx`.

## Estructura del Workspace

- `Propuesta de Tema.docx`: Especificación original de objetivo, alcance, mecánicas matemáticas (curación dinámica y power-ups), catálogo de NPCs híbridos y escenarios.
- `source/combo_6beat/`: Juego y motor en **Java 17 + Maven** (`org.processing:core:4.5.7`, `controlP5:2.2.6`, `TinySound`). **Nota:** contiene su propio repositorio `.git` interno.
- `level_editor/`: Editor externo nativo en **D (`@nogc nothrow`) + `raylib-d` + `raygui`**, que sustituye al antiguo editor Java eliminado (`game.editor.*`).
- `debug assets/`: Proyecto y mapas Tiled de referencia (`debug.tiled-project`, `Debug.tsx`, `sin nombre.tmx`, `tileset.png`).
- `release assets/`: Directorio destinado a assets finales de distribución (actualmente vacío).
- `PROJECT_STATUS.md`: Estado detallado de la propuesta, lo ya resuelto, pendientes y bugs/desajustes conocidos. Léelo antes de planificar nuevas funcionalidades o refactors transversales.

## Reglas Transversales

1. **Separación de herramientas:** Todo el tooling de edición de niveles, tilesets, tilemaps y grafos de diálogo vive en `level_editor/` (D). No reintroducir un editor embebido dentro de `source/combo_6beat/`.
2. **Contratos compartidos (`level_editor/` <-> `source/combo_6beat/`):**
   - Las escenas XML, los grafos JSON de diálogo y los archivos de proyecto `.procshit` deben usar **exclusivamente rutas relativas** (nunca rutas absolutas del sistema).
   - Antes de modificar el esquema XML de escenas, el formato JSON de diálogos o `.procshit`, consultar la skill `.agents/skills/scene-dialog-contracts/SKILL.md` y mantener sincronizados ambos lados (D y Java).
3. **Idioma y estilo:**
   - Identificadores de código (clases, métodos, structs, claves XML/JSON): en inglés (salvo claves/etiquetas heredadas ya presentes).
   - Comentarios, mensajes de UI del editor y documentación del proyecto: en español.
4. **Verificación tras cambios:** Usar la skill `.agents/skills/build-and-verify/SKILL.md` (`dub build` para `level_editor/`, `mvn -o compile` para `source/combo_6beat/`).

## Reglas por Subdirectorio

- `level_editor/GEMINI.md` — Restricciones `@nogc nothrow`, módulos D, buffers y UI `raygui`.
- `source/combo_6beat/GEMINI.md` — Build Maven, resolución virtual `480x270`, estado git y arquitectura general Java.
- `source/combo_6beat/src/main/java/engine/GEMINI.md` — Contratos del motor reutilizable (`Engine`, `Scene`, `ResourcesManager`, física, audio, diálogos).
- `source/combo_6beat/src/main/java/game/GEMINI.md` — Lógica específica de *Combo 6Beat* (`Main`, `Config`, `RPGScene`, `ROTNScene`/`ROTNCore`, `.osu`).
- `source/combo_6beat/resources/GEMINI.md` — Convenciones de escenas XML, diálogos JSON, beatmaps `.osu`, audio y spritesheets.
