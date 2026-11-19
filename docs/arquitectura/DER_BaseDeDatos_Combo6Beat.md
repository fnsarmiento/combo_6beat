# Diagrama Entidad-Relación (DER) — Base de Datos SQLite (*Combo 6Beat*)

> [!tip] Archivos del DER (Notación Chen)
> - **Imagen alta resolución (PNG):** [[DER_BaseDeDatos_Combo6Beat.png]]
> - **Vectorial (SVG):** [[DER_BaseDeDatos_Combo6Beat.svg]]
> - **Editable (draw.io / diagrams.net):** [DER_BaseDeDatos_Combo6Beat.drawio](file:///home/sar/Documentos/Combo%206Beat/arquitectura/DER_BaseDeDatos_Combo6Beat.drawio)
> - **Obsidian Canvas:** [[DER_BaseDeDatos_Combo6Beat.canvas]]
>
> ![DER Combo 6Beat](DER_BaseDeDatos_Combo6Beat.png)

---

## 1. Diagrama Entidad-Relación (Mermaid `erDiagram`)

```mermaid
erDiagram
    ZONAS ||--o{ CATALOGO_BATALLAS : "ubica (id_zona)"
    CATALOGO_ITEMS ||--o{ CATALOGO_BATALLAS : "recompensa (id_item_recompensa)"
    ZONAS ||--o{ PARTIDAS : "zona actual / retorno"
    PARTIDAS ||--o{ BATALLAS_VENCIDAS : "audita victorias (CASCADE)"
    CATALOGO_BATALLAS ||--o{ BATALLAS_VENCIDAS : "referenciada en"
    PARTIDAS ||--o{ OBJETOS_OBTENIDOS : "posee inventario (CASCADE)"
    CATALOGO_ITEMS ||--o{ OBJETOS_OBTENIDOS : "define"
    PARTIDAS ||--o{ HISTORIAL_OBJETOS : "registra movimientos (CASCADE)"
    CATALOGO_ITEMS ||--o{ HISTORIAL_OBJETOS : "referencia"
    PARTIDAS ||--o{ FLAGS_PARTIDA : "persiste flags (CASCADE)"
    CANCIONES_PERSONALIZADAS ||--o{ CANCION_OBJETOS_INTENTO : "resguarda chips (CASCADE)"
    CATALOGO_ITEMS ||--o{ CANCION_OBJETOS_INTENTO : "usado en intento"

    DB_META {
        TEXT clave PK
        TEXT valor
    }

    ZONAS {
        TEXT id_zona PK
        TEXT nombre
        TEXT escena_xml_relativa UK
        INTEGER orden_progresion
    }

    CATALOGO_ITEMS {
        TEXT id_item PK
        TEXT nombre
        TEXT categoria
        INTEGER curacion_hp
        TEXT descripcion
    }

    CATALOGO_BATALLAS {
        TEXT id_batalla PK
        TEXT boss_id UK
        TEXT boss_nombre
        TEXT especie
        TEXT defeat_flag UK
        TEXT escena_combate_relativa
        TEXT id_zona FK
        TEXT id_item_recompensa FK
        INTEGER es_historia_principal
    }

    PARTIDAS {
        INTEGER id_partida PK
        INTEGER slot UK
        TEXT id_zona_actual FK
        REAL player_x
        REAL player_y
        TEXT id_zona_retorno FK
        REAL return_x
        REAL return_y
        TEXT heal_strategy
        TEXT estado_general
        REAL progreso_porcentaje
        TEXT fecha_creacion
        TEXT fecha_actualizacion
    }

    BATALLAS_VENCIDAS {
        INTEGER id_partida PK,FK
        TEXT id_batalla PK,FK
        INTEGER puntaje_maximo
        INTEGER max_combo
        INTEGER perfects
        INTEGER goods
        INTEGER safes
        INTEGER misses
        INTEGER intentos_totales
        TEXT fecha_primera_victoria
        TEXT fecha_ultima_victoria
    }

    OBJETOS_OBTENIDOS {
        INTEGER id_partida PK,FK
        TEXT id_item PK,FK
        INTEGER cantidad
        INTEGER equipado
        TEXT fecha_obtencion
        TEXT fecha_actualizacion
    }

    HISTORIAL_OBJETOS {
        INTEGER id_historial PK
        INTEGER id_partida FK
        TEXT id_item FK
        TEXT evento
        INTEGER cantidad_delta
        TEXT fecha_evento
    }

    FLAGS_PARTIDA {
        INTEGER id_partida PK,FK
        TEXT flag_nombre PK
        TEXT fecha_definicion
    }

    CANCIONES_PERSONALIZADAS {
        INTEGER id_cancion PK
        TEXT clave_unica UK
        TEXT titulo
        TEXT artista
        TEXT dificultad
        REAL bpm
        TEXT ruta_portada
        TEXT ruta_audio
        TEXT ruta_chart
        INTEGER cantidad_notas
        INTEGER puntaje_ultimo_intento
        INTEGER combo_ultimo_intento
        REAL precision_ultimo_intento
        INTEGER puntaje_mas_alto
        INTEGER combo_mas_alto
        REAL precision_mas_alta
        INTEGER veces_jugada
        TEXT fecha_ultimo_intento
        TEXT fecha_record
    }

    CANCION_OBJETOS_INTENTO {
        INTEGER id_cancion PK,FK
        TEXT tipo_registro PK
        TEXT id_item PK,FK
    }
```

---

## 2. Justificación de Tercera Forma Normal (3FN)

1. **1FN (Atributos atómicos):** Todas las columnas almacenan valores indivisibles. Los objetos usados en el último intento y los resguardados del récord en `CANCIONES_PERSONALIZADAS` no se guardan como listas de texto sino en la tabla `CANCION_OBJETOS_INTENTO` con clave `(id_cancion, tipo_registro, id_item)`.
2. **2FN (Dependencia funcional completa):** En las tablas con clave primaria compuesta (`BATALLAS_VENCIDAS`, `OBJETOS_OBTENIDOS`, `FLAGS_PARTIDA`, `CANCION_OBJETOS_INTENTO`), cada atributo no clave depende de toda la clave compuesta y no de una parte de ella.
3. **3FN (Sin dependencias transitivas):** Los datos descriptivos estáticos de mapas (`ZONAS`), objetos (`CATALOGO_ITEMS`) y jefes (`CATALOGO_BATALLAS`) residen en catálogos propios referenciados por clave foránea, evitando redundancia dentro de `PARTIDAS`, `OBJETOS_OBTENIDOS` y `BATALLAS_VENCIDAS`.

---

## 3. Archivos Relacionados
- Esquema SQL reproducible: [schema.sql](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/resources/db/schema.sql)
- Gestor JDBC y migración: [DatabaseManager.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/db/DatabaseManager.java)
- Repositorio de partidas/batallas/inventario: [SaveRepository.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/db/SaveRepository.java)
- Repositorio de canciones custom: [CustomSongRepository.java](file:///home/sar/Documentos/Combo%206Beat/source/combo_6beat/src/main/java/game/core/db/CustomSongRepository.java)
