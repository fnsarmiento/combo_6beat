# Combo 6Beat

Videojuego 2D pixel art desarrollado en **Java 17 (Processing 4 + ControlP5 + TinySound + SQLite)** que combina un **Hub interactivo por salas al ritmo** (inspiración *Crypt of the NecroDancer*, `HubScene`) con **combate rítmico de 6 carriles** (inspiración *Rift of the NecroDancer*, `ROTNScene` / `ROTNCore`) y persistencia relacional completa en **SQLite (3FN)**.

**Institución:** Escuela Técnica Nº 35 D.E. 18 «Ing. Eduardo Latzina» (Computación)  
**Alumno:** Francisco Nahuel Sarmiento (5º 4ª) · **Ciclo Lectivo:** 2026

---

## Estructura del Repositorio

```text
Combo 6Beat/
├── game/                      # Juego y motor en Java 17 + Maven (Processing 4)
│   ├── src/main/java/engine/  # Motor reutilizable (escenas, audio, diálogos, física, sprites)
│   ├── src/main/java/game/    # Lógica del juego (InitScene, HubScene, ROTNScene/ROTNCore, SQLite)
│   ├── resources/             # Canciones (.osu/.scene), diálogo del Hub, sprites, SFX y schema.sql
│   └── pom.xml
├── docs/
│   ├── entrega_final/         # Paquete integrador de entrega final:
│   │   ├── 01_documentacion/  # Documentación integral (Markdown y PDF)
│   │   ├── 02_base_de_datos/  # DER Chen, Modelo Relacional 3FN (Draw.io/PNG) y scripts SQLite
│   │   └── 03_codigo_fuente/  # Arquitectura POO en capas
│   ├── arquitectura/          # Diagramas de arquitectura y consigna integradora
│   ├── propuesta/             # Propuesta de tema original
│   └── story/                 # Biblia narrativa, personajes y escaleta
└── assets_raw/                # Arte fuente y exports fuera del juego
```

---

## Base de Datos SQLite (3FN)

Todo el módulo de persistencia se encuentra en [`docs/entrega_final/02_base_de_datos/`](docs/entrega_final/02_base_de_datos/) y está integrado en vivo con el juego mediante [`game/resources/db/schema.sql`](game/resources/db/schema.sql) (`DatabaseManager`, `SaveRepository`, `CustomSongRepository`):

- **Modelado:**
  - **DER Conceptual (Chen):** [`DER_Conceptual_Combo6Beat.drawio`](docs/entrega_final/02_base_de_datos/DER_Conceptual_Combo6Beat.drawio) / [`DER_Conceptual_Combo6Beat.png`](docs/entrega_final/02_base_de_datos/DER_Conceptual_Combo6Beat.png)
  - **Modelo Relacional (3FN, Crow's Foot):** [`Modelo_Relacional_Combo6Beat.drawio`](docs/entrega_final/02_base_de_datos/Modelo_Relacional_Combo6Beat.drawio) / [`Modelo_Relacional_Combo6Beat.png`](docs/entrega_final/02_base_de_datos/Modelo_Relacional_Combo6Beat.png)
  - **Diccionario de Datos y Normalización:** [`Modelo_Relacional_3FN.md`](docs/entrega_final/02_base_de_datos/Modelo_Relacional_3FN.md)
- **Scripts SQLite (12 tablas, 4 índices, 14 triggers, 7 vistas):**
  1. [`01_ddl_creacion.sql`](docs/entrega_final/02_base_de_datos/01_ddl_creacion.sql) — Creación de tablas (`ZONAS`, `ENEMIGOS`, `CATALOGO_ITEMS`, `CATALOGO_BATALLAS`, `PARTIDAS`, `BATALLAS_VENCIDAS`, `OBJETOS_OBTENIDOS`, `HISTORIAL_OBJETOS`, `FLAGS_PARTIDA`, `CANCIONES_PERSONALIZADAS`, `CANCION_OBJETOS_INTENTO`, `DB_META`), índices, triggers y vistas `INNER JOIN`.
  2. [`02_dml_datos_iniciales.sql`](docs/entrega_final/02_base_de_datos/02_dml_datos_iniciales.sql) — Catálogos semilla y partidas/batallas/canciones de prueba.
  3. [`03_consultas_reportes.sql`](docs/entrega_final/02_base_de_datos/03_consultas_reportes.sql) — Consultas de verificación e informes sobre las 7 vistas.
  4. [`04_respaldo_completo.sql`](docs/entrega_final/02_base_de_datos/04_respaldo_completo.sql) — Respaldo completo (`.dump`) listo para restaurar.

### Verificación rápida en SQLite 3

```bash
cd docs/entrega_final/02_base_de_datos

# Crear BD desde cero y ejecutar reportes
rm -f test_combo6beat.db
sqlite3 test_combo6beat.db < 01_ddl_creacion.sql
sqlite3 test_combo6beat.db < 02_dml_datos_iniciales.sql
sqlite3 test_combo6beat.db < 03_consultas_reportes.sql

# Verificar restauración desde el respaldo completo
rm -f test_restore.db
sqlite3 test_restore.db < 04_respaldo_completo.sql
sqlite3 test_restore.db < 03_consultas_reportes.sql
```

---

## Compilación y Ejecución

### Juego (Java 17 + Maven)

```bash
cd game
mvn compile exec:java -Dexec.mainClass=game.Main
```

*(Si ya tenés las dependencias en caché local, podés usar `mvn -o compile exec:java -Dexec.mainClass=game.Main`).*
