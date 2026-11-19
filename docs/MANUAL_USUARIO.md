# Manual de Usuario — *Combo 6Beat*

> **Aviso importante:** Esta es una **versión en desarrollo (no completamente terminada)**. El sistema de combate rítmico de 6 carriles, el selector de canciones, la configuración y la persistencia en base de datos SQLite (3FN) son funcionales; el Hub interactivo por salas y las escenas narrativas se encuentran en construcción activa y seguirán recibiendo ajustes.

---

## 1. Requisitos

- **Java 17 o superior** (JDK/JRE) instalado y disponible en el `PATH`.
- Sistema operativo: **Linux, Windows o macOS** (64 bits).
- No requiere conexión a internet ni instalación de motores de base de datos externos (SQLite funciona embebido).

---

## 2. Cómo ejecutar el juego

### Opción A — Carpeta ejecutable `release/` (recomendada)

1. Abrí la carpeta **`release/`**.
2. Ejecutá el lanzador correspondiente a tu sistema:
   - **Linux / macOS:** `./jugar.sh` (o desde terminal: `java -cp "combo6beat.jar:libs/*" game.Main`)
   - **Windows:** doble clic en `jugar.bat` (o desde CMD: `java -cp "combo6beat.jar;libs\*" game.Main`)

### Opción B — Desde código fuente (Maven)

```bash
cd game
mvn compile exec:java -Dexec.mainClass=game.Main
```

---

## 3. Menú Principal

- **JUGAR (HUB):** abre el selector de partidas (4 ranuras persistidas en SQLite) y entra al **Hub Rítmico**.
- **SELECTOR DE CANCIONES:** permite jugar canciones personalizadas `.osu` (registradas en SQLite) o los combates contra jefes de historia, elegir **Chip/Power-Up**, modo de jefe, estrategia de curación y velocidad (`0.85x`, `1.00x`, `1.25x`), además de importar nuevos archivos `.osu`.
- **CONFIGURACION:** volumen de BGM/SFX, silencio general, resolución (`1920x1080` / `1280x720`) y remapeo de controles principales y alternativos.
- **SALIR:** cierra el juego.

---

## 4. Controles por defecto

*(Todos los controles principales y alternativos se pueden personalizar desde **Configuración**).*

### Menús y Hub Rítmico
- **Moverse / Navegar:** `W A S D` o **Flechas**
- **Interactuar / Aceptar:** `Enter` / `Z` / `E`
- **Volver / Cancelar / Cambiar estrategia de cura en el altar:** `Esc` / `X`

### Combate Rítmico (6 Carriles)
- **Carriles C1, C2, C3 (lado izquierdo):** `S`, `D`, `F` *(por defecto)*
- **Carriles C4, C5, C6 (lado derecho):** `J`, `K`, `L` *(por defecto)*
- **Pausa / Salir al Hub o Selector:** `Esc`

---

## 5. Cómo funciona el Hub Rítmico

Dentro de la sala principal del Hub podés desplazarte casilla por casilla hacia las distintas estaciones y presionar **Interactuar** (`Enter` / `Z` / `E`):

- **Portales Z1 a Z4 (fila superior):** inician los combates rítmicos contra los jefes. Al ganar, el progreso, puntaje y amuletos por score se guardan automáticamente en SQLite.
- **Portal OSU (izquierda):** abre el selector de canciones personalizadas.
- **NPC La Gata (centro-izquierda):** abre diálogos informativos del Hub.
- **Altar de Chips (centro-derecha):** permite cambiar el chip/power-up equipado (`Enter`/`Z`) y la estrategia de curación (`Esc`/`X`).
- **Cristal de Guardado (derecha):** guarda manualmente el estado de la ranura activa en `combo6beat.db`.
- **Salida MENU (abajo):** vuelve al menú principal.

---

## 6. Guardado y Base de Datos

El progreso de las 4 ranuras, las batallas vencidas, el inventario, los amuletos desbloqueados por puntaje y los récords de canciones personalizadas se almacenan automáticamente en el archivo local **`combo6beat.db`** (SQLite 3FN).
