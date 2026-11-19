# Borrador de Diálogos — Escenario 2: Playas + Ruinas (`draft_beach_ruins.json`)

> **Estado:** Borrador literario v1 (listo para iterar).  
> **Arco de la zona:** El protagonista llega con entusiasmo y propone jugar. Entre las ruinas y los encuentros con La Gata, empieza a cuestionarse por qué tiene que volver a su vida real.

---

## 1. Llegada a Playas + Ruinas (`beach_entry`)

- **`beach_entry_1` `[Viajero]`**
  > Sol cálido, mar turquesa y piedra antigua... Después de ese pantano, este lugar parece un sueño al que sí dan ganas de quedarse.

---

## 2. Batalla 4/7 — La Orilla Dorada (Los Gemelos en la Playa)

**Contexto:** Los Gemelos están aburridos en la costa. Ahora es el protagonista quien los reta alegremente.

### Pre-Batalla 4
- **`beach_b4_1` `[Kael]`**
  > ¡Miren quién sobrevivió al abrazo de Grail! Pensamos que te ibas directo a buscar tu salida.
- **`beach_b4_2` `[Viajero]`**
  > Y perderme una canción frente al mar? Ni loco. Esta vez empiezo yo: pongan algo con ritmo de verano.
- **`beach_b4_3` `[Vesper]`**
  > ¡Oh! ¡El antiguo amargado pide música por voluntad propia! ¡A sus puestos en la arena, hermano!
  - **Opción 1:** *¡A jugar en la costa! (Batalla 4)* -> Carga canción 4 (`__save_next__`: `beach_b4_win`, `__save_next_lose__`: `beach_b4_lose`)
  - **Opción 2:** *Explorar la arena primero* -> Cierra

### Derrota / Victoria Batalla 4
- **`beach_b4_lose` `[Kael]`**
  > Te encandiló el reflejo del agua, ¿eh? ¡Sacúdete la arena y vamos otra ronda!
  - **Opción 1:** *Reintentar en la costa* -> Recarga Batalla 4
  - **Opción 2:** *Luego* -> Cierra

- **`beach_b4_win_1` `[Viajero]`**
  > Eso sí fue divertido. Oigan, ¿qué son esas columnas de piedra tierra adentro?
- **`beach_b4_win_2` `[Vesper]`**
  > El Patio de las Ruinas. Allí el eco rebota hermoso. ¡Vamos, te guardamos la mejor pista para allá!

---

## 3. Batalla 5/7 — Patio de las Ruinas (Duelo Sincronizado de los Gemelos)

**Contexto:** El clímax de complicidad con los Gemelos, seguido de un comentario melancólico sobre el final del viaje.

### Pre-Batalla 5
- **`beach_b5_1` `[Kael]`**
  > ¡Llegaste al patio! Sin niebla ni barro, solo piedra vieja y seis carriles listos para arder.
- **`beach_b5_2` `[Vesper]`**
  > Si logras seguirnos este último baile doble, el santuario de Grail te abrirá sus puertas sin reservas.
  - **Opción 1:** *Bailar el duelo doble (Batalla 5)* -> Carga canción 5 (`__save_next__`: `beach_b5_win`, `__save_next_lose__`: `beach_b5_lose`)
  - **Opción 2:** *Descansar un instante* -> Cierra

### Derrota / Victoria Batalla 5
- **`beach_b5_lose` `[Vesper]`**
  > ¡Casi nos alcanzas al unísono! Toma aire y vuelve al centro del patio cuando quieras.
  - **Opción 1:** *Intentarlo de nuevo* -> Recarga Batalla 5
  - **Opción 2:** *Un segundo* -> Cierra

- **`beach_b5_win_1` `[Kael]`**
  > ¡Esooo! ¡Increíble! Nadie había seguido nuestro compás doble sin perder el aliento...
- **`beach_b5_win_2` `[Vesper]`**
  > ...Qué pena que cuando tomes la llave del santuario te vayas para siempre y el patio vuelva a quedar mudo.
- **`beach_b5_win_3` `[Viajero]`**
  > ...¿Para siempre? Yo... tampoco estoy tan seguro de querer irme todavía.

---

## 4. Batalla 6/7 — El Santuario Interior (Grail + Gemelos)

**Contexto:** La última batalla obligatoria antes de obtener la `key_exit` (*Llave Virtual*) para volver al Hub.

### Pre-Batalla 6
- **`beach_b6_1` `[Grail]`**
  > Has recorrido cada acorde de nuestras tierras con respeto, viajero. Solo queda una prueba antes de tu llave.
- **`beach_b6_2` `[Kael]`**
  > ¡No te contengas, Grail! ¡Y tú tampoco, amigo! ¡Queremos escuchar el mejor duelo de estas ruinas!
- **`beach_b6_3` `[Viajero]`**
  > Gracias por todo, chicos. Grail... hagamos que esta última canción sea inolvidable.
  - **Opción 1:** *Comenzar Duelo del Santuario (Batalla 6)* -> Carga `resources/songs/grail_sanctum.scene` (`__save_next__`: `beach_b6_win`, `__save_next_lose__`: `beach_b6_lose`)
  - **Opción 2:** *Preparar amuletos antes* -> Cierra

### Derrota / Victoria Batalla 6
- **`beach_b6_lose` `[Grail]`**
  > Tu corazón dudó en los compases finales. Reúne tu calma y vuelve ante el altar cuando lo desees.
  - **Opción 1:** *Volver al duelo del Santuario* -> Recarga Batalla 6
  - **Opción 2:** *Salir del santuario un momento* -> Cierra

- **`beach_b6_win_1` `[Grail]`**
  > Magnífico. Has demostrado ser digno del compás y de la amistad de los jóvenes amos. Toma la Llave Virtual.
  - *[Define `BOSS_BEACH_3_DEFEATED`, entrega `key_exit` (si no la entregó ya el `.scene`).]*
- **`beach_b6_win_2` `[Vesper]`**
  > El portal de regreso al Hub ya está abierto. Si cruzas la puerta blanca... buena suerte allá afuera, viajero.

---

## 5. La Gata en Playas + Ruinas + Medallón del Ocaso (Duda sobre volver)

- **Escondite 1 (`beach_cat_1`):**
  > **`[La Gata]`:** Ya no corres, viajero. Miras las olas como quien busca una excusa para olvidarse de su propio nombre.
  - *[Define `CAT_RUINS_1`.]*

- **Escondite 2 (`beach_cat_2`):**
  > **`[Viajero]`:** Dime, gata... afuera solo hay prisa, ruido y cansancio. Aquí tengo música y amigos. ¿Para qué tengo que volver?
- **`beach_cat_2_b` `[La Gata]`**
  > Qué pregunta tan pesada para un sueño tan ligero. Encuéntrame una vez más donde muere la sombra de las ruinas.
  - *[Define `CAT_RUINS_2`.]*

- **Escondite 3 (`beach_cat_3`):**
  > **`[La Gata]`:** Aquí brilla el Medallón del Ocaso. Si lo llevas contigo al Hub, la puerta de salida te escuchará... y yo también.
  - *[Define `CAT_RUINS_3`, `RUINS_MEDALLION_TAKEN`, `ROUTE_TRUE_UNLOCKED`, entrega `amulet_medallion_ruins`.]*

---

## 6. Modo Bucle Agotado (`LOOP_EXHAUSTED_MODE`) — NPCs Cansados en Playas + Ruinas

- **`beach_twins_tired` `[Vesper]`**
  > El sol ya no calienta igual cuando la misma tarde se repite tantas veces... ¿Aún quieres jugar otra vez, amigo?
- **`beach_twins_tired_2` `[Kael]`**
  > Me tiemblan las manos y ya no me salen chistes nuevos... Ve al Hub cuando nos quieras dejar descansar, ¿sí?
- **`beach_grail_tired` `[Grail]`**
  > Apenas puedo sostener la postura ante este altar. Tu afecto nos honra, viajero... pero la verdadera bondad ahora es saber decir adiós.
