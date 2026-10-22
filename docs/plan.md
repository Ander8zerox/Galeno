# Hoja de ruta

## Hecho

- Esqueleto de la Capa 1: pacientes, redactor de fórmulas con IA, apoyo diagnóstico y multi-tenant.
- Equipo del consultorio: membresías con varios roles, permisos, selector de consultorio e invitaciones.
- Agenda por médico: horario semanal, cupos, citas, sobrecupos, estados y vista para secretarios.
- Historia clínica: atenciones con firma e inmutabilidad, notas aclaratorias, documentos, CIE-10, auditoría y conexión con agenda, fórmulas y apoyo diagnóstico.
- CIE-10 oficial de MinSalud (con avisos de sexo y edad) y PDF de la fórmula y de la atención.
- Importación de RIPS JSON (varios archivos o ZIP) con revisión, confirmación y deshacer; catálogo CUPS; completar datos de pacientes importados.
- Importación de listados de pacientes (Excel o CSV) con mapeo de columnas recordado por sistema de origen; filtro y avisos de datos incompletos.
- Registro de consultorios desde la web con verificación del correo, NIT con dígito de verificación, aceptación de términos y política de datos, y límite de intentos.
- Recuperación de contraseña por correo (enlace de un solo uso) que cierra todas las sesiones abiertas.
- Programa de partners: inscripción con aprobación de la plataforma, código y enlace de referido, 10 % de descuento para el consultorio referido, comisión recurrente del 20 % sobre cada pago, retención de 30 días y liquidación desde $190.000.
- Envío de correos con Amazon SES, con rebotes y quejas leídos de una cola SQS y lista de correos bloqueados en la consola de la plataforma (falta verificar el dominio y salir del sandbox).
- Suscripciones: prueba de 30 días, planes mensual y anual, pago por transferencia o consignación reportado por el consultorio y aprobado en la consola de la plataforma, gracia de 5 días, solo lectura y avisos por correo.

## Siguiente

### Formatos clínicos por especialidad (después de salir a AWS)
La competencia ofrece decenas de formatos (historias por especialidad, exámenes, escalas, terapias, formatos administrativos) que el consultorio activa sin costo adicional. Hoy todo especialista llena la misma atención de medicina general.
- **Motor de formatos**, no un formato programado a mano por cada especialidad: un formato son datos versionados (secciones; campos de texto, número, selección, casillas, tablas y escalas con puntaje; reglas de obligatoriedad). El consultorio activa los suyos. Lo diligenciado queda dentro de la atención firmada (inmutable), en el PDF de la historia y disponible para la IA.
- **Primera especialidad: odontología** (es el mercado inicial):
  - Odontograma interactivo por diente y superficie (caries, obturación, ausente, corona, endodoncia, extracción indicada…), con el inicial y los de evolución. Necesita un componente gráfico propio.
  - Periodontograma (profundidad de sondaje, sangrado, movilidad).
  - Historia odontológica (antecedentes médicos y odontológicos, examen estomatológico, higiene oral, índice de placa).
  - Plan de tratamiento por fases con presupuesto, y su aceptación por el paciente.
  - Evolución por sesión ligada al plan, y consentimientos informados odontológicos (exodoncia, endodoncia, anestesia local).
  - Procedimientos con CUPS de odontología para los RIPS.
- **Formatos transversales** para cualquier especialidad: incapacidad, remisión y contrarreferencia, consentimiento informado firmado por el paciente.
- Fuera por ahora: los que dependen de equipos (examen con cámara, videoquimografía, estroboscopia).

### Historia clínica, siguiente iteración
- Imagen de la firma del médico y código QR de verificación en los PDF, reporte de auditoría para el administrador, y documentos en S3.

### Agenda, siguiente iteración
- Bloqueos (vacaciones, festivos de Colombia), vista semanal y recordatorios por WhatsApp o SMS.

### Otros pendientes
- Portal del paciente: cuenta separada que ve sus citas y fórmulas.
- Invitaciones por correo (hoy se comparte el enlace) y MFA (sobre todo para los operadores de la plataforma).
- Suscripciones, siguiente iteración: pasarela de pagos (Wompi, PayU…) cuyo webhook haga lo mismo que "Aprobar"; factura electrónica de Galeno por cada pago (obligatoria ante la DIAN); renovación automática.
- Partners, antes de lanzar: revisión legal y contable de las condiciones (contrato de prestación de servicios, retención en la fuente y ReteICA sobre comisiones, cuenta de cobro o factura del partner) y reportes de comisiones para contabilidad.
- Revisión legal de los términos y de la política de datos (hoy un borrador en `/politica-datos`) antes de abrir el registro al público.
- Catálogo CUM/INVIMA y rangos de dosis en `ValidadorFormula`.
- Cifrado de campos sensibles.
- Pruebas de integración con Testcontainers.
- RIPS y facturación electrónica.

### Importación de historias desde otros sistemas (pausada: queda para el final)
El principio: primero las fuentes masivas, todas por el mismo flujo **extraer → revisar → confirmar**.

| Prioridad | Fuente | Cómo se procesa |
|---|---|---|
| ✔ | **RIPS JSON** (Res. 2275/2023; según Medifolios la reemplazó la 948/2026, sin verificar en la fuente oficial) | Hecho: pacientes por documento y atenciones con CUPS y CIE-10. Faltan medicamentos y otros servicios |
| ✔ | **Excel o CSV de pacientes** | Hecho: completa los pacientes que el RIPS dejó sin nombre y crea los que faltan; el mapeo se recuerda por encabezados. Falta: sugerir con IA las columnas que no se reconocen |
| 1 | RIPS TXT antiguos (AF, US, AC, AP…) | Solo si los clientes los tienen; mismo lote y revisión que el JSON |
| 3 | PDF en lote (ZIP, digitales o escaneados) | IA con salida estructurada, un trabajo por archivo en `ai_jobs`; procesamiento por lotes de Bedrock para migraciones grandes |
| 4 | Texto pegado (un paciente) | El mismo extractor de IA de los PDF |
| 5 | FHIR / RDA (Res. 866/2021) | Cuando un cliente lo pida |

- **Zona de revisión.** Hecha para RIPS (ver arquitectura). Para PDF faltan el origen (archivo y página) y la confianza por registro.
- **Deduplicación.** Primero por tipo y número de documento; si no coincide, por parecido de nombre y fecha de nacimiento.
- **Original.** El PDF queda en S3 como documento soporte del paciente.
- **Antes de construir el punto 3:** un piloto con 20 historias reales para medir calidad, tokens y costo.
- **Legal.** Contrato de encargado del tratamiento (Ley 1581) que mencione la IA en AWS, auditoría de quién importó y aprobó, y conservación del original (Res. 1995).
- **Comercial.** Ofrecer "migración asistida incluida" para el primer lote de cada cliente.
