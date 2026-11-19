# Entrega Final Integradora — *Combo 6Beat*

**Institución:** Escuela Técnica Nº 35 D.E. 18 «Ing. Eduardo Latzina» (Computación)  
**Alumno:** Francisco Nahuel Sarmiento (5º 4ª) · **Docente:** Juan Manuel Moya · **Ciclo Lectivo:** 2026  

---

## Estructura de Entregables

| Carpeta / Eje | Contenido | Estado |
| :--- | :--- | :--- |
| **[01_documentacion/](01_documentacion/)** | Documentación integral del proyecto (carátula, propuesta, mecánicas, arquitectura y diseño de BD): [Documentacion_Proyecto_Combo6Beat.md](01_documentacion/Documentacion_Proyecto_Combo6Beat.md) y [Documentacion_Proyecto_Combo6Beat.pdf](01_documentacion/Documentacion_Proyecto_Combo6Beat.pdf). | Completo |
| **[02_base_de_datos/](02_base_de_datos/)** | DER Conceptual Chen puro (sin FKs ni tablas intermedias, con `FLAGS_PARTIDA` como entidad débil), modelo lógico 3FN y scripts SQLite funcionales (DDL, DML, reportes `INNER JOIN` y respaldo `.sql`). | Completo y verificado |
| **[03_codigo_fuente/](03_codigo_fuente/)** | Arquitectura POO en 4 capas ([Arquitectura_Capas_POO.md](03_codigo_fuente/Arquitectura_Capas_POO.md)). El código fuente en Java (`source/combo_6beat/`) y el editor D (`level_editor/`) permanecen en desarrollo activo y no se empaquetan todavía en `.zip`. | Arquitectura documentada / Juego en desarrollo |
| **[04_manuales/](04_manuales/)** | Manual de Usuario y Guía de Instalación en Markdown y PDF: [Manual_Usuario_e_Instalacion.md](04_manuales/Manual_Usuario_e_Instalacion.md) y [Manual_Usuario_e_Instalacion.pdf](04_manuales/Manual_Usuario_e_Instalacion.pdf). | Completo |

---

## Índice de Base de Datos (`02_base_de_datos/`)

1. **DER Conceptual (Chen puro):**
   - Imagen para documentación: [DER_Conceptual_Combo6Beat.png](02_base_de_datos/DER_Conceptual_Combo6Beat.png)
   - Vectorial y fuentes editables: [DER_Conceptual_Combo6Beat.svg](02_base_de_datos/DER_Conceptual_Combo6Beat.svg), [DER_Conceptual_Combo6Beat.drawio](02_base_de_datos/DER_Conceptual_Combo6Beat.drawio), [DER_Conceptual_Combo6Beat.canvas](02_base_de_datos/DER_Conceptual_Combo6Beat.canvas)
2. **Modelo Relacional y Diccionario de Datos (1FN / 2FN / 3FN):** [Modelo_Relacional_3FN.md](02_base_de_datos/Modelo_Relacional_3FN.md)
3. **Script DDL (11 tablas, 3 índices, 10 triggers, 6 vistas):** [01_ddl_creacion.sql](02_base_de_datos/01_ddl_creacion.sql)
4. **Script DML de Catálogos y Datos de Prueba:** [02_dml_datos_iniciales.sql](02_base_de_datos/02_dml_datos_iniciales.sql)
5. **Consultas de Defensa en Vivo (`INNER JOIN` e integridad):** [03_consultas_reportes.sql](02_base_de_datos/03_consultas_reportes.sql)
6. **Respaldo Completo Exportado (`.dump` SQLite):** [04_respaldo_completo.sql](02_base_de_datos/04_respaldo_completo.sql)

---

## Comandos Rápidos para Defensa Presencial (SQLite 3)

```bash
cd "entrega final/02_base_de_datos"

# 1. Crear BD limpia desde DDL + DML y validar consultas/vistas
rm -f defensa_combo6beat.db
sqlite3 defensa_combo6beat.db < 01_ddl_creacion.sql
sqlite3 defensa_combo6beat.db < 02_dml_datos_iniciales.sql
sqlite3 defensa_combo6beat.db < 03_consultas_reportes.sql

# 2. Verificar restauración íntegra desde el respaldo completo
rm -f defensa_restore.db
sqlite3 defensa_restore.db < 04_respaldo_completo.sql
sqlite3 defensa_restore.db < 03_consultas_reportes.sql
```
