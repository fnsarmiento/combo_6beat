# Borrador de Diálogos — Escenario 1: El Pantano (`draft_swamp.json`)

> **Estado:** Borrador literario v1 (listo para iterar).  
> **Arco de la zona:** El protagonista llega apurado y se niega a jugar con nadie; entre la 1.ª y 2.ª batalla se contagia del ritmo y se divierte; en la 3.ª conoce a Grail como guardaespaldas de los Gemelos.

---

## 1. Llegada al Pantano (`swamp_entry`)

- **`swamp_entry_1` `[Viajero]`**
  > Genial... barro hasta los tobillos y neblina. Si encuentro la salida de este pantano en cinco minutos, mejor.
  - *[Define `SWAMP_ENTERED`.]*

---

## 2. Batalla 1/7 — Puente de Raíces (Los Gemelos: Kael al frente)

**Contexto:** Kael y Vesper aparecen saltando sobre el único puente seco. El protagonista intenta pasar de largo sin jugar.

### Pre-Batalla 1
- **`swamp_b1_1` `[Kael]`**
  > ¡Alto ahí, zapatos limpios! Nadie cruza el Puente de Raíces sin pagar el peaje oficial del pantano.
- **`swamp_b1_2` `[Vesper]`**
  > Y antes de que busques monedas en tus bolsillos... aquí solo aceptamos notas bien dadas al compás.
- **`swamp_b1_3` `[Viajero]`**
  > No me interesa su espectáculo. Estoy buscando la salida de esta simulación y no voy a perder tiempo jugando.
- **`swamp_b1_4` `[Kael]`**
  > ¡Uy, qué carácter! Qué lástima que el barro de los costados llegue hasta el cuello. ¿Seguro que no quieres bailar?
- **`swamp_b1_5` `[Viajero]`**
  > ...Está bien. Una canción corta, termino con esto rápido y me dejan en paz. ¿Trato?
  - **Opción 1:** *Aceptar a regañadientes (Batalla 1)* -> Carga canción 1 (`__save_next__`: `swamp_b1_win`, `__save_next_lose__`: `swamp_b1_lose`)
  - **Opción 2:** *Seguir quejándose* -> `swamp_b1_refuse`

- **`swamp_b1_refuse` `[Vesper]`**
  > Puedes quedarte mirando las ranas todo el día si prefieres, relojito con patas. Aquí seguimos esperando.

### Derrota / Victoria Batalla 1
- **`swamp_b1_lose` `[Kael]`**
  > ¡Ja! ¿Ves? Para alguien con tanta prisa, tus dedos se tropiezan bastante. ¿Vamos otra vez, amargado?
  - **Opción 1:** *¡Cállate y pon la pista de nuevo!* -> Recarga Batalla 1
  - **Opción 2:** *Necesito respirar un segundo* -> Cierra

- **`swamp_b1_win_1` `[Viajero]`**
  > Listo. Ahí tienen su dichoso peaje. Ahora déjenme cruzar.
- **`swamp_b1_win_2` `[Kael]`**
  > ¡Oye, pero si sí tienes ritmo bajo toda esa cara de pocos amigos! Vesper, ¿viste cómo salvó el último compás?
- **`swamp_b1_win_3` `[Vesper]`**
  > Sublime. Aunque nos debes la revancha en el Claro de la Bruma, viajero. ¡El primero que llegue elige el ritmo!
  - *[Los Gemelos salen corriendo pantano adentro.]*

---

## 3. Batalla 2/7 — Claro de la Bruma (Los Gemelos: Vesper & Kael)

**Contexto:** El protagonista los alcanza. Aunque sigue fingiendo prisa, ya le picó la curiosidad y el orgullo competitivo.

### Pre-Batalla 2
- **`swamp_b2_1` `[Vesper]`**
  > ¡Bienvenido al Claro de la Bruma! Qué tragedia: acabas de pisar nuestra orquesta de luciérnagas imaginarias.
- **`swamp_b2_2` `[Viajero]`**
  > ¿Otra excusa? Déjame adivinar: ahora tengo que tocar otra canción para compensar a los insectos invisibles.
- **`swamp_b2_3` `[Kael]`**
  > ¡Bingo! Pero esta va más rápido y los dos vamos a turnarnos los carriles. ¿Te atreves o sigues apurado?
- **`swamp_b2_4` `[Viajero]`**
  > ...Hmph. Que sea más difícil que la primera, al menos para que valga la pena. ¡Dale play!
  - **Opción 1:** *¡Empezar Batalla 2!* -> Carga canción 2 (`__save_next__`: `swamp_b2_win`, `__save_next_lose__`: `swamp_b2_lose`)
  - **Opción 2:** *Revisar mis chips antes (TAB)* -> Cierra

### Derrota / Victoria Batalla 2
- **`swamp_b2_lose` `[Vesper]`**
  > ¡Casi! Tu sonrisa te delató en el estribillo, viajero. Vuelve a intentarlo cuando quieras.
  - **Opción 1:** *Revancha ahora mismo* -> Recarga Batalla 2
  - **Opción 2:** *Luego sigo* -> Cierra

- **`swamp_b2_win_1` `[Viajero]`**
  > ¡Ja! Admito que el cambio de ritmo en la mitad estuvo genial. Hacía meses que no me concentraba así.
- **`swamp_b2_win_2` `[Kael]`**
  > ¡Míralo, Vesper, se está riendo! ¡Sabíamos que por debajo de esa prisa eras de los nuestros!
- **`swamp_b2_win_3` `[Vesper]`**
  > Ahora sí somos amigos oficiales. Solo falta cruzar el umbral norte... aunque ojo, ahí suele vigilar Grail.

---

## 4. Batalla 3/7 — Umbral Norte (Grail, el Oso-Búho Guardaespaldas)

**Contexto:** Grail intercepta al protagonista frente a la salida del Pantano hacia Playas + Ruinas. Cree que está importunando a los Gemelos.

### Pre-Batalla 3
- **`swamp_b3_1` `[Grail]`**
  > Detente ahí, forastero. Los jóvenes amos no son juguetes para tu entretenimiento ni atajos hacia tu salida.
- **`swamp_b3_2` `[Viajero]`**
  > Espera, gigante emplumado, ¡lo entendiste al revés! Ellos me retaron primero y nos estábamos divirtiendo.
- **`swamp_b3_3` `[Grail]`**
  > Mi deber es velar por su descanso. Si tus intenciones son honestas, sostén el compás frente a mi guardia.
  - **Opción 1:** *Aceptar la prueba de Grail (Batalla 3)* -> Carga canción 3 (`__save_next__`: `swamp_b3_win`, `__save_next_lose__`: `swamp_b3_lose`)
  - **Opción 2:** *Retroceder un paso* -> Cierra

### Derrota / Victoria Batalla 3
- **`swamp_b3_lose` `[Grail]`**
  > Aún te falta firmeza para cargar con este ritmo. Prepárate y vuelve cuando tu pulso no vacile.
  - **Opción 1:** *Volver a enfrentar a Grail* -> Recarga Batalla 3
  - **Opción 2:** *Prepararme mejor* -> Cierra

- **`swamp_b3_win_1` `[Kael]`**
  > ¡Grail, basta ya! ¡El viajero no es malo, es el mejor jugador que ha caído al pantano en años!
- **`swamp_b3_win_2` `[Grail]`**
  > ...Ya veo. Tus notas no tenían malicia, solo alegría. Perdona mi desconfianza, viajero.
- **`swamp_b3_win_3` `[Grail]`**
  > El camino hacia Playas y Ruinas queda abierto. Si vuelves a jugar con los chicos, cuida su energía tanto como la tuya.

---

## 5. La Gata Cambiando de Lugar + Medallón del Pantano

- **Escondite 1 (`swamp_cat_1`):**
  > **`[La Gata]`:** Corres por los puentes como si el reloj del mundo real siguiera contando aquí. No es así, miau.
  - *[Define `CAT_SWAMP_1`. Desaparece de aquí y aparece en el Escondite 2.]*

- **Escondite 2 (`swamp_cat_2`):**
  > **`[La Gata]`:** Escuché cómo te reíste con los gemelos entre la niebla. Qué curioso... ¿no querías escapar hace diez minutos?
  - *[Define `CAT_SWAMP_2`. Desaparece de aquí y aparece en el Escondite 3.]*

- **Escondite 3 (`swamp_cat_3`):**
  > **`[La Gata]`:** Encontraste mi rincón favorito del lodo. Toma este Medallón del Pantano; te servirá si decides mirar más hondo en las ruinas.
  - *[Define `CAT_SWAMP_3`, `SWAMP_MEDALLION_TAKEN`, entrega `amulet_medallion_swamp`.]*

---

## 6. Modo Bucle Agotado (`LOOP_EXHAUSTED_MODE`) — NPCs Cansados en el Pantano

**Contexto:** Si en la Ruta Secreta del Hub elegiste *"Seguir jugando"* y vuelves al Pantano.

- **`swamp_twins_tired` `[Kael & Vesper]`**
  > ¿Otro... juego aquí en el barro...? *bostezo* Perdón, viajero... nos pesan las piernas y ya gastamos todas nuestras bromas.
- **`swamp_twins_tired_2` `[Vesper]`**
  > Seguimos sonriendo porque para eso nos programaron... pero si quieres, la puerta del Hub sigue esperándote arriba.
- **`swamp_grail_tired` `[Grail]`**
  > *Respira con dificultad...* Mis alas pesan como plomo, amigo. Los jóvenes amos apenas pueden mantenerse en pie. Por favor... déjanos dormir.
