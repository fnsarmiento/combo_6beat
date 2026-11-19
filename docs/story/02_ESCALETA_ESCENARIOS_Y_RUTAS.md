# Escaleta de Escenarios, Batallas y Rutas — *Combo 6Beat*

Este documento detalla la progresión escena por escena, las **7 batallas** (3 en Pantano, 3 en Playas + Ruinas, 1 canción extra en el Hub), la mecánica de **La Gata cambiando de lugar + Medallones** y las **2 rutas finales**.

---

## Acto 0 — Escenario 0: El Hub (Apertura)

- **Ambiente:** Sala mínima de piedra pálida o suelo flotante rodeado de vacío suave, con una gran puerta iluminada hacia el Pantano y la Puerta de Salida sellada con cerradura rítmica.
- **Secuencia:**
  1. El Protagonista despierta confundido y revisa sus bolsillos. No entiende qué es este lugar, solo sabe que quiere irse.
  2. Aparece **La Gata** sentada frente a él. Lo mira fijamente y camina unos pasos deteniéndose junto al portal del Pantano.
  3. Si el protagonista intenta abrir la Puerta de Salida de inmediato, está sellada: necesita la **Llave Virtual (`key_exit`)**.
  4. La Gata le dice que el único camino abierto lleva abajo, al Pantano, y desaparece entre las sombras del marco antes de que el protagonista termine de quejarse.
  5. El protagonista cruza la puerta -> **Transición al Escenario 1 (Pantano)**.

---

## Acto 1 — Escenario 1: El Pantano (3 Batallas)

**Arco del Protagonista:** Empieza irritado, negándose a participar en los juegos. Al final de la tercera batalla admite en voz alta que hacía mucho tiempo que no se reía así.

### Ruta Principal (3 Batallas Obligatorias)
1. **Batalla 1/7 — *Puente de Raíces* (Los Gemelos: Kael toma la delantera):**
   - **Situación:** Los Gemelos bloquean el primer sendero seco del pantano. Kael anuncia que el "peaje" no es dinero, sino seguirles un ritmo de 6 carriles.
   - **Actitud del Prota:** Se niega (*"No tengo tiempo para juegos de niños, déjenme pasar o busco otro camino"*). Como el lodo rodea el puente, no tiene más remedio que aceptar *"solo esta vez y rápido"*.
   - **Post-Batalla:** Kael queda impresionado de que el "amargado" tenga buen oído, y Vesper lo invita a la siguiente zona mientras se ríen y salen corriendo.
2. **Batalla 2/7 — *Claro de la Bruma* (Los Gemelos: Vesper & Kael en dúo):**
   - **Situación:** En la mitad del pantano, Vesper finge una tragedia operística porque el protagonista supuestamente "pisó sus luciérnagas". Le exigen una revancha con más velocidad.
   - **Actitud del Prota:** Aunque empieza fingiendo fastidio, a mitad del diálogo entra en el juego de bromas de los Gemelos. Al ganar, sonríe y comenta que *"no estuvo tan mal"*.
   - **Post-Batalla:** Los Gemelos celebran como si hubieran encontrado a un hermano nuevo, pero hacen demasiado ruido...
3. **Batalla 3/7 — *Umbral del Guardián* (Grail, el Oso-Búho Guardaespaldas):**
   - **Situación:** La tierra tiembla. **Grail** aterriza frente al portal hacia Playas + Ruinas, protegiendo a los Gemelos detrás de su capa de plumas. Cree que el desconocido está acosando o agotando a los chicos.
   - **Actitud del Prota:** Ya no quiere huir por egoísmo; intenta explicarle a Grail que solo estaban jugando, pero Grail exige comprobar sus intenciones en duelo.
   - **Post-Batalla:** Los Gemelos saltan a defender al protagonista (*"¡Grail, nos estábamos divirtiendo!"*). Grail inclina la cabeza en señal de respeto, pide disculpas por juzgarlo mal y libera el paso hacia **Playas + Ruinas**.

### Secreto del Pantano: La Gata Móvil + Medallón del Pantano
La Gata aparece en **3 ubicaciones sucesivas** (cada encuentro define un flag y la hace desaparecer del punto anterior para aparecer en el siguiente mediante `__ifdef__` / `__ifndef__` en el mapa o nodos de diálogo):
1. **Escondite 1 (`CAT_SWAMP_1`):** Sobre un tronco caído al borde del pantano. Comenta que corres demasiado rápido para mirar el paisaje.
2. **Escondite 2 (`CAT_SWAMP_2`):** Detrás de unos juncos cerca del Claro de la Bruma. Observa cómo los Gemelos te sacaron una sonrisa y pregunta: *"¿Sigue pareciéndote una pérdida de tiempo?"*.
3. **Escondite 3 (`CAT_SWAMP_3` + `SWAMP_MEDALLION_TAKEN`):** Junto a un pequeño altar cubierto de musgo. La Gata empuja con su pata el **Medallón del Pantano (`amulet_medallion_swamp`)** hacia ti y te advierte: *"Guárdalo si quieres saber qué hay al otro lado de la puerta, no solo afuera."*

---

## Acto 2 — Escenario 2: Playas + Ruinas (3 Batallas)

**Arco del Protagonista:** Llega motivado y alegre. Juega por iniciativa propia, se encariña con los Gemelos y Grail, y —si encuentra a La Gata— empieza a dudar seriamente si quiere volver a su vida real.

### Ruta Principal (3 Batallas Obligatorias)
1. **Batalla 4/7 — *La Orilla Dorada* (Los Gemelos en la Playa):**
   - **Situación:** Sol brillante, agua turquesa y ruinas antiguas en la costa. Los Gemelos están tirando piedras al mar aburridos porque pensaron que no volverías a cruzar.
   - **Actitud del Prota:** Por primera vez, **él mismo propone la canción**: *"¿Qué esperan? Dijeron que en la playa había mejor acústica."*
   - **Post-Batalla:** El ambiente es luminoso y relajado. Los Gemelos le cuentan que más adentro, en el patio de piedra, tienen su pista favorita.
2. **Batalla 5/7 — *Patio de las Ruinas* (Duelo Sincronizado de los Gemelos):**
   - **Situación:** Entre los pilares antiguos de `beach_ruins.xml`, Kael y Vesper despliegan su canción más rápida y divertida.
   - **Post-Batalla:** Mientras recuperan el aliento riendo, a Vesper se le escapa la verdad: *"Ojalá el juego no tuviera última nota... cuando tomes la llave del santuario, este patio vuelve a quedarse mudo."* Kael intenta disimular con una broma, pero se nota que tiene miedo de quedarse solo.
3. **Batalla 6/7 — *El Santuario Interior* (Grail, con los Gemelos alentando):**
   - **Situación:** En la cámara norte de las ruinas (`beach_ruins.xml`), Grail aguarda como prueba final antes del regreso al Hub. Los Gemelos miran desde un costado sin interrumpir, animando a ambos.
   - **Actitud del Prota:** Acepta el duelo con respeto. Ya no es una pelea contra un obstáculo, sino la despedida oficial entre amigos.
   - **Post-Batalla:** Grail entrega la **Llave Virtual (`key_exit`)**. Le desea buen regreso al mundo real y los Gemelos intentan sonreír mientras se despiden, diciendo que tal vez algún día vuelva otro viajero. El portal hacia el **Hub** queda abierto.

### Secreto de Playas + Ruinas: La Gata Móvil + Medallón del Ocaso
De nuevo, La Gata cambia de lugar en **3 puntos opcionales** de la playa y las ruinas:
1. **Escondite 1 (`CAT_RUINS_1`):** En la bahía oeste, mirando las olas. Te pregunta por qué tus pasos se volvieron más lentos ahora que la salida está cerca.
2. **Escondite 2 (`CAT_RUINS_2`):** En lo alto de un muro derrumbado del patio. El protagonista confiesa por primera vez sus dudas: afuera todo es presión, horarios y cansancio; aquí tiene ritmo y amigos.
3. **Escondite 3 (`CAT_RUINS_3` + `RUINS_MEDALLION_TAKEN` -> `ROUTE_TRUE_UNLOCKED`):** En el rincón oculto al sur/este de las ruinas, junto a un brillo dorado en la arena. La Gata te entrega el **Medallón del Ocaso (`amulet_medallion_ruins`)** (o reconoce el Medallón si ya traías el del Pantano).
   - *Nota de diseño de ruta:* Para desbloquear la Ruta Secreta basta haber encontrado a La Gata y obtenido el Medallón (con ambos medallones se activa `ROUTE_TRUE_UNLOCKED`; también puede configurarse que baste el encuentro final de Ruinas con un medallón para evitar bloqueos frustrantes si el jugador perdió un punto en el Pantano, o requerir la secuencia completa — ambas opciones quedan documentadas en los flags).

---

## Acto 3 — Regreso al Escenario 0: El Hub y las 2 Rutas Finales

Al volver al **Hub** con la `key_exit`, el juego evalúa el flag `ROUTE_TRUE_UNLOCKED`:

### Ruta 1: Final Normal (Sin haber encontrado a La Gata + Medallón)
- El Hub está tranquilo. La Gata no aparece junto a la Puerta de Salida (o solo te observa en silencio desde lejos sin bloquear el paso).
- Al usar la `key_exit` en la Puerta de Salida:
  - El protagonista siente una punzada extraña, como si hubiera dejado algo sin entender del todo, pero gira la llave.
  - La luz blanca llena la pantalla: **sale de la simulación** y despierta en su mundo sin haber descubierto el secreto de La Gata.
  - Pantalla de cierre (*Final Normal: "De vuelta al silencio"*).

### Ruta 2: Final Verdadero + Canción Extra (Habiendo encontrado a La Gata + Medallón)
- **Encuentro frente a la Puerta:** La Gata está sentada justo frente a la cerradura. Le dice que los Medallones resuenan con una duda: *no quieres irte porque por fin sientes que encajas en un compás*.
- **Batalla 7/7 — Canción Extra Final vs. La Gata (*"Ronroneo en el Fin del Mundo"*):**
  - El combate más emotivo y desafiante del juego (`glass beach.scene` / tema final dedicado).
- **La Gran Pregunta Post-Batalla:** Al terminar la canción, La Gata baja las orejas y te hace la pregunta definitiva:
  > *"Ganaste el derecho a elegir. Puedes seguir jugando aquí todo lo que quieras... o puedes cruzar la puerta y despertar. ¿Qué decides?"*

  - **Opción A — "Cruzar la puerta y volver" (Final Verdadero: *Despedida en Paz*):**
    - La Gata asiente con suavidad. Dice que los recuerdos de este compás no desaparecen solo por cerrar la pantalla, y que los Gemelos y Grail descansarán sabiendo que alguien los escuchó de verdad.
    - El protagonista cruza la puerta con una sonrisa tranquila -> **Fin del Juego (Final Verdadero)**.

  - **Opción B — "Quiero seguir jugando" (Estado `LOOP_EXHAUSTED_MODE` — *Los NPCs Cansados*):**
    - La Gata suspira sin juzgarte y se aparta: *"Entonces el metrónomo seguirá girando... pero nadie fue hecho para tocar sin descanso."*
    - **Efecto en todo el mundo:** No termina el juego; puedes volver al Pantano y a Playas + Ruinas y volver a hablar o jugar con todos, **pero el tono cambió drásticamente**:
      - **Kael & Vesper** están ojerosos, arrastran las palabras, bostezan entre chistes forzados (*"¿Otra ronda más...? Qué... qué suerte tenemos..."*) y admiten que les duelen las manos y la garganta de repetir la misma música.
      - **Grail** respira lento, con las alas caídas, y en lugar de desafiarte con orgullo te ruega con cariño que te detengas y salgas por el bien de todos.
      - **La Gata** espera siempre en el Hub. Cuando el jugador decide volver y hablarle de nuevo, puede elegir en cualquier momento **"Ya estoy listo para irme"** para activar el **Final Verdadero** y cerrar el juego.
