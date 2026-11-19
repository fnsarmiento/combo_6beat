# Combo 6Beat — Contexto Raíz del Proyecto

Videojuego 2D con estética pixel art que combina un **Hub interactivo por salas al ritmo** (inspiración *Crypt of the NecroDancer*, `HubScene`) y escenas narrativas de diálogo con **combate rítmico de 6 carriles** (inspiración *Rift of the NecroDancer*, `ROTNScene` / `ROTNCore`). El mapa 2D RPG antiguo fue eliminado en `feat/rhythm-vn` (respaldado en `backup/rpg-re`).

## Base de Datos SQLite (3FN)

- Esquema maestro en `game/resources/db/schema.sql` (espejado en `docs/entrega_final/02_base_de_datos/01_ddl_creacion.sql` + `02_dml_datos_iniciales.sql`).
- **`ZONAS`** representa las **salas del Hub** (`hub`, `swamp`, `beach_ruins`).
- **`ENEMIGOS`** guarda cada enemigo/jefe con su **canción de referencia** (`cancion_referencia`), su **posición `(hub_x, hub_y)`** dentro de la sala del Hub (`id_zona`), su sprite y su vínculo a `CATALOGO_BATALLAS`.
- **`CATALOGO_BATALLAS`** define recompensas y **amuletos desbloqueables por puntaje** (`id_amuleto_score`, `puntaje_minimo_amuleto`), otorgados automáticamente mediante triggers al registrar/actualizar victorias en `BATALLAS_VENCIDAS`.
- **`CANCIONES_PERSONALIZADAS`** + **`CANCION_OBJETOS_INTENTO`** gestionan el modo libre/custom con triggers automáticos de récord e ítems resguardados.

## Estructura del Workspace

- `game/`: Juego y motor en **Java 17 + Maven** (`org.processing:core:4.5.7`, `controlP5:2.2.6`, `TinySound`). **Nota:** contiene su propio repositorio `.git` interno conectado a GitHub.
- `tools/`: Herramientas de desarrollo del proyecto:
  - `tools/level_editor/`: Editor externo nativo en **D (`@nogc nothrow`) + `raylib-d` + `raygui`**.
  - `tools/projects/`: Archivos de proyecto `.procshit` (`beach_ruins.procshit`, `swamp.procshit`).
  - `tools/scripts/`: Scripts utilitarios (`import_pixellab_scene.py`, `mp3_to_wav.sh`).
- `assets_raw/`: Arte fuente y exports fuera del juego (`assets_raw/debug/`, `assets_raw/release/`).
- `docs/`: Toda la documentación unificada:
  - `docs/propuesta/`: Propuesta original (`Propuesta de Tema.docx`) y `PROJECT_STATUS.md`.
  - `docs/story/`: Biblia narrativa, personajes y borradores de diálogos.
  - `docs/arquitectura/`: Diagramas de arquitectura y DER.
  - `docs/entrega_final/`: Entregables finales integradores (documentación, BD SQLite 3FN, manuales).

## Reglas Transversales

1. **Separación de herramientas:** Todo el tooling vive en `tools/` (`tools/level_editor/`, `tools/scripts/`). No reintroducir un editor embebido dentro de `game/`.
2. **Contratos compartidos (`tools/level_editor/` <-> `game/`):**
   - Las escenas XML, los grafos JSON de diálogo y los archivos de proyecto `.procshit` deben usar **exclusivamente rutas relativas** (nunca rutas absolutas del sistema).
   - Antes de modificar el esquema XML de escenas, el formato JSON de diálogos o `.procshit`, consultar la skill `.agents/skills/scene-dialog-contracts/SKILL.md` y mantener sincronizados ambos lados (D y Java).
3. **Idioma y estilo:**
   - Identificadores de código (clases, métodos, structs, claves XML/JSON): en inglés (salvo claves/etiquetas heredadas ya presentes).
   - Comentarios, mensajes de UI del editor y documentación del proyecto: en español.
4. **Verificación tras cambios:** Usar la skill `.agents/skills/build-and-verify/SKILL.md` (`dub build` para `tools/level_editor/`, `mvn -o compile` para `game/`).

## Reglas por Subdirectorio

- `tools/level_editor/GEMINI.md` — Restricciones `@nogc nothrow`, módulos D, buffers y UI `raygui`.
- `game/GEMINI.md` — Build Maven, resolución virtual `480x270`, estado git y arquitectura general Java.
- `game/src/main/java/engine/GEMINI.md` — Contratos del motor reutilizable (`Engine`, `Scene`, `ResourcesManager`, física, audio, diálogos).
- `game/src/main/java/game/GEMINI.md` — Lógica específica de *Combo 6Beat* (`Main`, `Config`, `RPGScene`, `ROTNScene`/`ROTNCore`, `.osu`).
- `game/resources/GEMINI.md` — Convenciones de escenas XML, diálogos JSON, beatmaps `.osu`, audio y spritesheets.
