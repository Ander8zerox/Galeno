# Hoja de ruta

## Hecho

- Esqueleto de la Capa 1: pacientes, redactor de fórmulas con IA, apoyo diagnóstico y multi-tenant.
- Equipo del consultorio: membresías con varios roles, permisos, selector de consultorio e invitaciones.
- Agenda por médico: horario semanal, cupos, citas, sobrecupos, estados y vista para secretarios.

## Siguiente

### Modelo de historia clínica (es requisito de la importación)
- `atenciones`: fecha, médico, motivo, enfermedad actual, examen, diagnósticos CIE-10, plan y `origen` (GALENO o IMPORTADA).
- `documentos_clinicos`: el PDF original en S3, inmutable y con hash.
- Una nota importada o firmada no se edita (Res. 1995).
- Conexión con la agenda: "Atender" una cita abre la atención.

### Importación de historias desde otros sistemas
El principio: primero las fuentes masivas, todas por el mismo flujo **extraer → revisar → confirmar**.

| Prioridad | Fuente | Cómo se procesa |
|---|---|---|
| 1 | Excel o CSV de pacientes | Mapeo de columnas, guardado como plantilla por sistema de origen |
| 2 | **RIPS** (JSON de la Res. 2275/2023 o TXT antiguos) | Parser determinista: pacientes, atenciones, CIE-10 y medicamentos |
| 3 | PDF en lote (ZIP, digitales o escaneados) | IA con salida estructurada, un trabajo por archivo en `ai_jobs`; procesamiento por lotes de Bedrock para migraciones grandes |
| 4 | Texto pegado (un paciente) | El mismo extractor de IA de los PDF |
| 5 | FHIR / RDA (Res. 866/2021) | Cuando un cliente lo pida |

- **Zona de revisión.** Un `lote_importacion` guarda cada registro con su estado (listo, revisar, duplicado, error), su origen (archivo y página) y la confianza. Nada entra a la historia sin aprobación. Se puede aprobar en bloque y el lote se puede deshacer completo.
- **Deduplicación.** Primero por tipo y número de documento; si no coincide, por parecido de nombre y fecha de nacimiento.
- **Original.** El PDF queda en S3 como documento soporte del paciente.
- **Antes de construir el punto 3:** un piloto con 20 historias reales para medir calidad, tokens y costo.
- **Legal.** Contrato de encargado del tratamiento (Ley 1581) que mencione la IA en AWS, auditoría de quién importó y aprobó, y conservación del original (Res. 1995).
- **Comercial.** Ofrecer "migración asistida incluida" para el primer lote de cada cliente.

### Agenda, siguiente iteración
- Bloqueos (vacaciones, festivos de Colombia), vista semanal y recordatorios por WhatsApp o SMS.

### Otros pendientes
- Portal del paciente: cuenta separada que ve sus citas y fórmulas.
- PDF de la fórmula con firma del médico.
- Registro de consultorios nuevos, recuperación de contraseña, invitaciones por correo (SES) y MFA.
- Catálogo CUM/INVIMA y rangos de dosis en `ValidadorFormula`.
- Cifrado de campos sensibles y log de acceso a historias.
- Pruebas de integración con Testcontainers.
- RIPS y facturación electrónica.
