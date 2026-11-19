# Borrador de Diálogos — Escenario 0: El Hub (`draft_hub.json`)

> **Estado:** Borrador literario v1 (listo para iterar).  
> **Nota técnica:** Las líneas están escritas en bloques cortos (`<= 220` caracteres) para encajar cómodamente en la caja de diálogo `480x270` y en los buffers `char[256]` de `level_editor`.

---

## Escena 0A — Despertar en la Simulación (Primera vez en el Hub)

**Contexto:** El Protagonista despierta frente a una puerta cerrada y una puerta abierta hacia el Pantano. Una gata chiquita está sentada cerca.

- **`hub_intro_1` `[Viajero]`**
  > ¿Dónde estoy...? Hace un segundo estaba frente a mi escritorio y ahora todo huele a lluvia y estática.
- **`hub_intro_2` `[Viajero]`**
  > Necesito salir rápido de esta simulación. No tengo tiempo para quedarme atrapado aquí.
- **`hub_cat_guide_1` `[La Gata]`**
  > Miau. Despertaste antes que el reloj, viajero. Qué prisa tan ruidosa tienes en los zapatos.
- **`hub_cat_guide_2` `[Viajero]`**
  > ¿Una gata parlante...? Escucha, ¿esa puerta blanca es la salida? Está trabada.
- **`hub_cat_guide_3` `[La Gata]`**
  > Esa puerta pide una llave que todavía no resuena contigo. Si quieres salir, sígueme hacia aquel marco verde.
- **`hub_cat_guide_4` `[La Gata]`**
  > Abajo te espera el Pantano. No te distraigas... o quizás sí. A veces los atajos solo dan vueltas.
  - *[Define `HUB_INTRO_DONE`. La Gata camina / desaparece junto al portal del Pantano.]*

---

## Puerta del Pantano (`hub_door_swamp`)

- **`hub_door_swamp` `[Narrador]`**
  > Un portal cubierto de musgo húmedo vibra con un compás lento. Al otro lado se oyen risas lejanas.
  - **Opción 1:** *Cruzar al Pantano* -> Carga `resources/scenes/swamp.xml`
  - **Opción 2:** *Mirar alrededor* -> Cierra diálogo

---

## Puerta de Salida (`hub_exit_door`) — Regreso desde Playas + Ruinas

### Caso 1: Aún no tienes `key_exit`
- **`hub_exit_locked` `[Cerradura]`**
  > La puerta blanca no cede. La ranura reclama una Llave Virtual forjada al final del Santuario.

### Caso 2: Ruta 1 — Final Normal (Tienes `key_exit`, pero NO activaste `ROUTE_TRUE_UNLOCKED`)
- **`hub_exit_normal_1` `[Narrador]`**
  > La Llave Virtual gira sin hacer ruido. El umbral blanco se abre ante ti como una pantalla recién encendida.
- **`hub_exit_normal_2` `[Viajero]`**
  > Por fin... la salida. Aun así, siento que dejé algo pequeño mirándome desde las sombras de allá abajo.
  - **Opción 1:** *Salir de la simulación* -> Define `GAME_ENDING_NORMAL`, pantalla/texto de cierre normal.
  - **Opción 2:** *Esperar un momento* -> Cierra diálogo.

- **`hub_ending_normal_done` `[Sistema]`**
  > [FINAL NORMAL — "De vuelta al silencio"] Desconectaste la simulación sin descubrir el secreto de La Gata.

---

## Escena 0C: Ruta 2 — Regreso al Hub con `ROUTE_TRUE_UNLOCKED` (Canción Extra: Batalla 7/7)

**Contexto:** Si encontraste a La Gata en sus escondites y obtuviste el Medallón, ella aguarda justo frente a la Puerta de Salida.

### Pre-Batalla 7
- **`hub_cat_final_1` `[La Gata]`**
  > Traes los ecos del pantano y la sal de las ruinas en el bolsillo. No corriste tanto después de todo.
- **`hub_cat_final_2` `[Viajero]`**
  > Encontré tu medallón, pero... ahora que tengo la llave en la mano, mis pies pesan el doble.
- **`hub_cat_final_3` `[Viajero]`**
  > Dime la verdad, gata. Si cruzo esa puerta, ¿todo lo que viví con los Gemelos y Grail desaparece?
- **`hub_cat_final_4` `[La Gata]`**
  > Afuera te espera el ruido real; aquí, un compás prestado. Antes de abrir, muéstrame qué ritmo elegiste guardar.
  - *[Default -> Carga `resources/songs/glass beach.scene` (Batalla 7/7 contra La Gata), `__save_next__`: `hub_cat_post_win`, `__save_next_lose__`: `hub_cat_post_lose`]*

### Si pierdes contra La Gata (`hub_cat_post_lose`)
- **`hub_cat_post_lose` `[La Gata]`**
  > Tu corazón aún tiembla entre dos mundos, viajero. Respira hondo e inténtalo de nuevo cuando estés listo.
  - **Opción 1:** *Reintentar último compás* -> Recarga Batalla 7
  - **Opción 2:** *Caminar un poco por el Hub* -> Cierra diálogo

### Post-Batalla 7 — La Decisión Final (`hub_cat_post_win`)
- **`hub_cat_post_win_1` `[La Gata]`**
  > Qué melodía tan honesta. Ya demostraste que no perteneces al silencio... ni tampoco a una jaula.
- **`hub_cat_post_win_2` `[La Gata]`**
  > Te pregunto por última vez, sin trucos: ¿quieres seguir jugando en este sueño o salir de la simulación?
  - **Opción 1:** *Salir de la simulación (Final Verdadero)* -> `hub_ending_true_1`
  - **Opción 2:** *Quiero seguir jugando* -> `hub_choose_stay_1`

### Rama A: Irse en Paz (`hub_ending_true_*`)
- **`hub_ending_true_1` `[Viajero]`**
  > Fue el mejor desvío de mi vida. Pero un compás solo tiene sentido si alguna vez termina la canción.
- **`hub_ending_true_2` `[La Gata]`**
  > Entonces ve en paz. Los Gemelos dormirán tranquilos y Grail guardará tu nombre bajo sus plumas. Adiós, viajero.
  - *[Define `GAME_ENDING_TRUE`.]*
- **`hub_ending_true_3` `[Sistema]`**
  > [FINAL VERDADERO — "El Compás Guardado"] Cruzaste la puerta recordando cómo volver a empezar afuera.

### Rama B: Seguir Jugando (`LOOP_EXHAUSTED_MODE` — NPCs Cansados)
- **`hub_choose_stay_1` `[La Gata]`**
  > Como desees. El telón no caerá todavía... pero recuerda que nadie aquí fue hecho para tocar sin descanso.
  - *[Define `LOOP_EXHAUSTED_MODE`. El jugador conserva libre exploración por el Hub, Pantano y Playas + Ruinas.]*

---

## Escena 0D: Volver con La Gata durante el Bucle Agotado (`LOOP_EXHAUSTED_MODE`)

- **`hub_cat_exhausted_check` `[La Gata]`**
  > Los escuchaste respirar más lento allá abajo, ¿verdad? El escenario sigue en pie solo porque tú no cierras la puerta.
  - **Opción 1:** *Ya es hora. Quiero salir y dejarlos descansar.* -> `hub_ending_true_1`
  - **Opción 2:** *Un poco más...* -> Cierra diálogo
