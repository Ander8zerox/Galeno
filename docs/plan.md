# Hoja de ruta

## Hecho

- Esqueleto de la Capa 1: pacientes, redactor de fórmulas con IA, apoyo diagnóstico y multi-tenant.
- Equipo del consultorio: membresías con varios roles, permisos, selector de consultorio e invitaciones.
- Agenda por médico: horario semanal, cupos, citas, sobrecupos, estados y vista para secretarios.
- Historia clínica: atenciones con firma e inmutabilidad, notas aclaratorias, documentos, CIE-10, auditoría y conexión con agenda, fórmulas y apoyo diagnóstico.
- CIE-10 oficial de MinSalud (con avisos de sexo y edad) y PDF de la fórmula y de la atención.
- Importación de RIPS JSON (varios archivos o ZIP) con revisión, confirmación y deshacer; catálogo CUPS; completar datos de pacientes importados.

## Siguiente

### Historia clínica, siguiente iteración
- Imagen de la firma del médico y código QR de verificación en los PDF, reporte de auditoría para el administrador, y documentos en S3.

### Importación de historias desde otros sistemas
El principio: primero las fuentes masivas, todas por el mismo flujo **extraer → revisar → confirmar**.

| Prioridad | Fuente | Cómo se procesa |
|---|---|---|
| ✔ | **RIPS JSON** (Res. 2275/2023; según Medifolios la reemplazó la 948/2026, sin verificar en la fuente oficial) | Hecho: pacientes por documento y atenciones con CUPS y CIE-10. Faltan medicamentos y otros servicios |
| 1 | Excel o CSV con nombres y contactos | Completa los pacientes que el RIPS dejó sin nombre; mapeo de columnas con ayuda de IA, guardado como plantilla por sistema de origen |
| 2 | RIPS TXT antiguos (AF, US, AC, AP…) | Solo si los clientes los tienen; mismo lote y revisión que el JSON |
| 3 | PDF en lote (ZIP, digitales o escaneados) | IA con salida estructurada, un trabajo por archivo en `ai_jobs`; procesamiento por lotes de Bedrock para migraciones grandes |
| 4 | Texto pegado (un paciente) | El mismo extractor de IA de los PDF |
| 5 | FHIR / RDA (Res. 866/2021) | Cuando un cliente lo pida |

- **Zona de revisión.** Hecha para RIPS (ver arquitectura). Para PDF faltan el origen (archivo y página) y la confianza por registro.
- **Deduplicación.** Primero por tipo y número de documento; si no coincide, por parecido de nombre y fecha de nacimiento.
- **Original.** El PDF queda en S3 como documento soporte del paciente.
- **Antes de construir el punto 3:** un piloto con 20 historias reales para medir calidad, tokens y costo.
- **Legal.** Contrato de encargado del tratamiento (Ley 1581) que mencione la IA en AWS, auditoría de quién importó y aprobó, y conservación del original (Res. 1995).
- **Comercial.** Ofrecer "migración asistida incluida" para el primer lote de cada cliente.

### Agenda, siguiente iteración
- Bloqueos (vacaciones, festivos de Colombia), vista semanal y recordatorios por WhatsApp o SMS.

### Otros pendientes
- Portal del paciente: cuenta separada que ve sus citas y fórmulas.
- Registro de consultorios nuevos, recuperación de contraseña, invitaciones por correo (SES) y MFA.
- Catálogo CUM/INVIMA y rangos de dosis en `ValidadorFormula`.
- Cifrado de campos sensibles y log de acceso a historias.
- Pruebas de integración con Testcontainers.
- RIPS y facturación electrónica.
