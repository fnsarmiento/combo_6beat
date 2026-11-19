# Manual de Usuario y Guía de Instalación — *Combo 6Beat*

**Institución:** Escuela Técnica Nº 35 D.E. 18 «Ing. Eduardo Latzina»  
**Proyecto:** *Combo 6Beat* — Trabajo Final Integrador (2026)  
**Alumno:** Francisco Nahuel Sarmiento (5º 4ª) · **Docente:** Juan Manuel Moya

---

## Parte I — Guía de Instalación y Despliegue

### 1. Requisitos del Sistema

| Componente | Requisito Mínimo / Verificado | Uso en el Proyecto |
| :--- | :--- | :--- |
| **Sistema Operativo** | Linux (o Windows/macOS con JDK 17+) | Entorno de ejecución |
| **Java (JDK)** | JDK 17+ (verificado en OpenJDK 25 con compilación target Java 17) | Runtime del videojuego |
| **Apache Maven** | Maven 3.x (verificado en 3.10.0) | Compilación y ejecución (`-o` offline) |
| **SQLite 3** | `sqlite3` CLI (`3.54.x`) + `org.xerial:sqlite-jdbc:3.54.1.0` | Base de datos local y scripts AGBA |
| **DUB + Compilador D + Raylib** *(opcional)* | DUB `1.42.0`, `raylib-d ~>6.2.0` | Solo para compilar `level_editor/` |

> [!NOTE]
> Las dependencias Java (`org.processing:core:4.5.7`, `sojamo:controlP5:2.2.6` y `org.xerial:sqlite-jdbc:3.54.1.0`) se declaran en [../../source/combo_6beat/pom.xml](../../source/combo_6beat/pom.xml) y funcionan en modo offline (`mvn -o`) cuando la caché local `~/.m2/repository` está disponible.

---

### 2. Compilar y Ejecutar el Videojuego

1. Abrir una terminal y ubicarse en la carpeta del juego:
   ```bash
   cd "source/combo_6beat"
   ```
2. Ejecutar con Maven en modo offline (o usando `./run.sh`, que lanza `mvn -o compile exec:java -Dexec.mainClass=game.Main`):
   ```bash
   ./run.sh
   ```
3. **Inicialización automática de SQLite:** Al arrancar `game.Main`, la clase `DatabaseManager` crea automáticamente `combo6beat.db` aplicando [../../source/combo_6beat/resources/db/schema.sql](../../source/combo_6beat/resources/db/schema.sql), habilita `PRAGMA journal_mode = WAL` y `PRAGMA foreign_keys = ON`, y migra por única vez cualquier partida previa en formato `.save` / `.save2`.

---

### 3. Compilar el Editor de Niveles (Opcional)

El editor de escenas XML, tilemaps y grafos de diálogo vive de forma separada en `level_editor/`:

```bash
cd level_editor
dub build
./level_editor
```

---

### 4. Instalación y Restauración Manual de la Base de Datos (SQLite 3)

Para inspeccionar, recrear o defender la base de datos de forma independiente en clase utilizando los scripts de [../02_base_de_datos/](../02_base_de_datos/), ubicarse primero en la carpeta de scripts:

```bash
cd "entrega final/02_base_de_datos"
```

#### Opción A — Creación limpia desde DDL + DML

```bash
rm -f combo6beat_demo.db
sqlite3 combo6beat_demo.db < 01_ddl_creacion.sql
sqlite3 combo6beat_demo.db < 02_dml_datos_iniciales.sql
```

#### Opción B — Restauración desde el respaldo completo `.sql`

```bash
rm -f combo6beat_restore.db
sqlite3 combo6beat_restore.db < 04_respaldo_completo.sql
```

#### Verificación de integridad y vistas `INNER JOIN`

```bash
sqlite3 combo6beat_demo.db < 03_consultas_reportes.sql
```

Este comando comprueba `PRAGMA integrity_check`, `PRAGMA foreign_key_check` y muestra las 6 vistas consolidadas (`v_resumen_partidas`, `v_mejores_puntajes_batallas`, `v_inventario_detallado`, `v_historial_objetos`, `v_canciones_custom_objetos` y `v_estadisticas_canciones_custom`).

---

## Parte II — Manual de Usuario

### 5. Pantalla de Inicio y Navegación (`InitScene`)

Al iniciar el juego se presenta el menú principal con acceso a cuatro secciones:

1. **Partidas / Historia (`SAVE_SELECT`):**
   - Muestra **4 ranuras de guardado** almacenadas en SQLite (`PARTIDAS`).
   - Cada ranura ocupada indica la zona actual (`El Hub`, `Swamp (Pantano)`, `Beach`), porcentaje de progreso de historia, batallas vencidas, cantidad de ítems/amuletos y estado general (`EN_PROGRESO`, `FINAL_NORMAL`, `FINAL_VERDADERO` o `BUCLE_AGOTADO`).
   - Permite iniciar una partida nueva en ranura libre, continuar una existente o **eliminar** una ranura (limpiando en cascada sus registros asociados).

2. **Canciones y Batallas (`SONG_SELECT`):** Incluye dos pestañas independientes en la parte superior:
   - **`CANCIONES CUSTOM`:** Muestra las pistas `.osu` de 6 carriles ubicadas en `resources/songs/custom/` con portada, título, BPM, último intento y récord (`puntaje_mas_alto`) junto con los PowerUps utilizados. Desde el botón **`+ IMPORTAR .OSU`** se puede importar un archivo `.osu` externo, que se copia al repositorio relativo del proyecto y se registra en SQLite (`CANCIONES_PERSONALIZADAS`).
   - **`JEFES HISTORIA`:** Permite rejugar combates de la campaña principal en modo práctica sin alterar el inventario ni los flags de las partidas guardadas.

3. **Configuración (`SETTINGS`):**
   - Control de volumen de música (**BGM**) y efectos (**SFX**), más botón de silencio rápido (**Mute**).
   - Selector de resolución de ventana (**`1920x1080`** o **`1280x720`**) y activación de escalado **`Pixel Perfect`**.
   - Reasignación (*rebinding*) de teclas tanto para exploración RPG como para los 6 carriles de combate.

---

### 6. Controles en Exploración RPG (`RPGScene`)

El mundo top-down se explora en cámara virtual `560x315`:

| Acción | Teclas por Defecto |
| :--- | :--- |
| Mover personaje (arriba / izquierda / abajo / derecha) | **`W, A, S, D`** o **Flechas de dirección** |
| Correr | **`Shift`** |
| Interactuar (hablar con NPCs, abrir cofres, usar portales) | **`E`** o **`Enter`** |
| Abrir Menú Teléfono / Pausa | **`ESC`** |
| Mostrar/ocultar cajas de colisión (depuración) | **`H`** o **`F3`** |

#### Menú Teléfono / Pausa (`ESC`)
Dentro de la exploración RPG, pulsar **`ESC`** abre el panel de estado de la partida activa para:
- Consultar **objetos obtenidos**, amuletos e historial de uso/obtención (`HISTORIAL_OBJETOS`).
- **Equipar o desequipar PowerUps y Amuletos** (registrando automáticamente el evento en SQLite).
- Alternar la estrategia de curación automática (**`OPTIMAL`** o **`MINIMAL`**).
- **Guardar partida** manualmente en la ranura actual o volver al menú principal.

---

### 7. Controles y Mecánicas del Combate Rítmico 6K (`ROTNScene`)

Las batallas contra jefes y las pistas personalizadas se disputan en un tablero de **6 carriles** (`480x270` virtual):

| Esquema | Carriles 1 a 6 (Izquierda a Derecha) |
| :--- | :--- |
| **Esquema Principal** | **`Q` · `W` · `E` · `U` · `I` · `O`** |
| **Esquema Alternativo** | **`A` · `S` · `D` · `J` · `K` · `L`** |

#### Sistema de Puntuación, Curación y Objetos
- **Ventanas de Juicio Base (`ROTNCore.HitRating`):**
  - **`PERFECT`**: hasta **45 ms** de desfasaje (`+5` unidades en barra/puntaje base).
  - **`GOOD`**: hasta **85 ms** (`+3`).
  - **`SAFE`**: hasta **125 ms** (`+1`).
  - **`MISS`**: fuera de **170 ms** (`-2`).
- **PowerUps y Chips Equipables:**
  - **`Chip +10ms`**: amplía la ventana de reacción en `+10 ms` (+25 pts de coste de curación).
  - **`Chip -7.5ms`**: estrecha la ventana en `-7.5 ms` (-20 pts de coste de curación) para mayor exigencia.
  - **`Escudo Unico`**: otorga invencibilidad frente a un fallo único durante la pista.
- **Curación Dinámica y Pociones:**
  - Acertar **2 notas consecutivas** tras recibir daño activa la recuperación rítmica del **`50%` del daño recibido** (o del **`75%`** al equipar el **`Amuleto de Curacion`**).
  - Además, las pociones del inventario (**Pequeña `25 HP`**, **Mediana `50 HP`**, **Grande `90 HP`**) pueden consumirse según la estrategia seleccionada (`OPTIMAL` / `MINIMAL`).

---

### 8. Resolución de Problemas Frecuentes

| Síntoma | Causa Probable / Solución |
| :--- | :--- |
| Error de dependencias al ejecutar `mvn -o` | Verificar que exista `~/.m2/repository` o ejecutar una primera compilación con conexión (`mvn compile`) para cachear Processing, ControlP5 y `sqlite-jdbc`. |
| La canción custom `.osu` no aparece o falla al reproducirse | Confirmar que el archivo `.osu` esté en modo 6K (`CircleSize:6`) y que su audio/portada estén referenciados con **rutas relativas** válidas (el trigger SQLite bloquea rutas absolutas). |
| Error `FOREIGN KEY constraint failed` en pruebas SQL manuales | Respetar el orden de ejecución: primero `01_ddl_creacion.sql`, luego `02_dml_datos_iniciales.sql`, o insertar primero el registro padre (`PARTIDAS`, `ZONAS`, `CATALOGO_ITEMS`). |
