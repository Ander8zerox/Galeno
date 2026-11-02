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
- Programa de partners: inscripción con aprobación de la plataforma, código y enlace de referido, 10 % de descuento para el consultorio referido, comisión recurrente del 20 % sobre cada pago, retención de 30 días y liquidación desde $190.000 (pasa a 30 % y $30.000: ver "Hallazgos de las pruebas en producción").
- Envío de correos con Amazon SES, con rebotes y quejas leídos de una cola SQS y lista de correos bloqueados en la consola de la plataforma (dominio verificado con DKIM, SPF y DMARC; fuera del sandbox desde noviembre de 2026).
- Suscripciones: prueba de 30 días, planes mensual y anual, pago por transferencia o consignación reportado por el consultorio y aprobado en la consola de la plataforma, gracia de 5 días, solo lectura y avisos por correo.

## Siguiente

### Hallazgos de las pruebas en producción (paso 12)
Se corrigen en una rama al terminar las pruebas, y se vuelven a probar en producción.
- **Página de inicio después de activar el consultorio.** Hoy `RegistroPage.tsx` manda a `/equipo`. Debe abrir la
  Agenda con un aviso de bienvenida: la prueba de 30 días empezó, y los siguientes pasos son crear un paciente, agendar
  una cita e invitar al equipo (decidido el 2 de noviembre de 2026).
- **Errores de IA crudos en pantalla (prioridad alta).** Si Bedrock falla, el médico ve el JSON del proveedor
  (`404: {"type":"error","request_id":…,"message":"The model 'anthropic.claude-haiku-5-5' does not exist"}`), porque
  `AiJobWorker` guarda `e.getMessage()` en el trabajo y el front lo muestra tal cual. Guardar un mensaje en español
  ("El asistente de IA no está disponible en este momento; puede llenar la fórmula manualmente") y dejar el detalle
  solo en el log, con el id del trabajo. Probado en fórmula y en apoyo diagnóstico.
- **Eliminar un borrador con documentos da error 500 (prioridad alta).** `HistoriaService.eliminarBorrador` borra la
  atención y MySQL lo rechaza por `fk_documentos_atencion` (ERROR en el log del 2 de noviembre de 2026 a las 12:14 UTC).
  Nada se pierde, pero el médico ve "Error inesperado". Responder 409 con un mensaje claro ("tiene documentos
  adjuntos: anúlelos o firme la atención"), y resolverlo junto con "Anular un documento".
  Cómo reproducirlo: agendar una cita → abrir la atención y adjuntar un documento → marcar la cita como "no asistió" →
  "Eliminar borrador".
- **"No asistió" con una atención en borrador (prioridad media).** En la misma secuencia, la agenda deja marcar la
  inasistencia aunque la cita ya tenga una atención abierta con documentos: la cita dice que el paciente no vino y la
  historia tiene una atención de esa visita. No permitirlo: "Esta cita tiene una atención en curso. Fírmela o elimine
  el borrador antes de marcar la inasistencia".
- **Acciones de un clic sin confirmación (prioridad media).** En la agenda, "Confirmar", "Atender", "No asistió" y
  "Cancelar" se ejecutan de inmediato al primer clic, igual que "Cerrar sesión": un clic por error cambia la cita o
  saca a la persona. Pedido del usuario: que pidan confirmación. Alternativa propuesta, para no acostumbrar a decir
  "sí" sin leer: confirmación (con paciente y hora) en "Cancelar" y "No asistió"; "Deshacer" por unos segundos en
  "Confirmar" y "Atender"; y en "Cerrar sesión", confirmación o alejar el botón de los demás. Se decide al
  implementarlo.
- **Anular un documento subido por error (prioridad media).** Hoy solo hay subir y descargar
  (`HistoriaController`). Si un médico sube el archivo equivocado o al paciente equivocado, no puede corregirlo, y el
  bucket no deja borrar versiones a nadie (`SinBorrarVersiones`), como debe ser para la historia clínica. Agregar
  "Anular" con motivo obligatorio: el documento sale de la lista (queda visible en un filtro de anulados), el archivo
  se conserva en S3 y queda registro de quién lo anuló y cuándo. Pasó en la prueba: se subió un PDF con datos reales a
  un paciente de prueba.
- **Auditoría de la plataforma incompleta (prioridad media).** (1) "Cambió los datos de pago" se guarda sin detalle
  (`PlataformaService.cambiarDatosPago`): debe guardar el texto anterior y el nuevo, porque cambiar la cuenta bancaria
  es la acción más sensible de la consola. (2) La tabla no muestra qué operador hizo cada acción; el dato ya está en
  `auditoria_plataforma`, falta exponerlo en `EventoAuditoria` y en la pantalla.
- **Precios de los planes sin validación (baja prioridad).** Se pudo guardar el mensual en $1.118.700 y el anual en
  $1.052.522 (error de digitación). Advertir antes de guardar si el anual cuesta más que 12 mensualidades, o si un precio
  cambia más de, digamos, 50 %.
- **Condiciones de partners (decidido el 2 de noviembre de 2026).** Comisión del **30 %** (antes 20 %), retención de
  **30 días** (sin cambio) y liquidación desde **$30.000** (antes $190.000), para que el partner cobre desde el primer
  pago de su primer referido. Con el mensual de $128.700 y el 10 % de descuento, la comisión es de $34.749. Cambiar
  `app.partners.comision-porcentaje` y `minimo-liquidacion` en `application.yml`, y `docs/arquitectura.md`; las
  pantallas toman los valores de la configuración. Las comisiones ya creadas conservan su porcentaje.
  - Mejorar el mensaje al partner aprobado: primero "gana el 30 % de cada pago de sus referidos", después cuándo se le
    transfiere.
  - Pendiente de decidir antes de tener partners reales: si la comisión recurrente tiene tope (p. ej., 12 o 24 meses).
- **Festivos en la agenda (prioridad media).** La agenda no conoce los festivos: un lunes festivo tiene el horario de
  cualquier lunes, también en la agenda pública. Calcular los festivos de Colombia en Galeno (fijos, trasladados al
  lunes por la Ley 51 de 1983 y los que dependen de la Pascua), mostrarlos con su nombre, sin disponibilidad por
  defecto, y una opción por médico "Atiendo festivos". Después, días cerrados propios del consultorio (vacaciones).
- **TA diastólica desalineada (baja prioridad).** En `.signos` (`styles.css`), el título largo ocupa dos líneas y baja su
  campo. Alinear los campos abajo (`.signos input { margin-top: auto; }`).
- **Poco espacio para la firma en los PDF (prioridad media).** `.firma { margin-top: 28px }` en `PdfRenderer.java`
  (fórmula e historia) deja unos 7 mm para firmar a mano. Subir a unos 75 px (2 cm).
- **La marca no coincide con el dominio (prioridad alta, antes de tener clientes).** La aplicación, los correos y
  los PDF dicen "Galeno", y el dominio es galenum.com.co: parece un descuido o una imitación. galeno.com está ocupado,
  y galeno.co y galenum.com están en venta en Afternic. Propuesta: cambiar la marca visible a **Galenum** (pantallas,
  correos, remitente, PDF, títulos); el código interno conserva "galeno". "Galeno" es una palabra común en salud
  (difícil de registrar ante la SIC y de encontrar en Google); "Galenum" es distintiva. Antes: consultar en la SIC que
  "Galenum" esté libre en las clases 42 y 44, y registrarla.
  **Decidido el 2 de noviembre de 2026: la marca visible pasa a Galenum.** El usuario consulta la SIC.
- **Registro abierto a cualquiera (prioridad alta, decidir el alcance).** Preocupa que un competidor use la prueba para
  copiar Galeno. No se puede impedir del todo (puede pagar un mes); el código del servidor no se expone. Propuesta:
  (a) pedir cédula y registro profesional y verificarlos en ReTHUS desde la consola, con opción de suspender; (c) prueba
  con topes (pacientes, cupo de IA, sin exportar); (d) cláusula en los términos contra el uso para copia o
  competencia; (e) bloquear correos desechables y limitar registros por IP. La aprobación previa del operador queda
  como opción si se ve abuso.
  **Decidido el 2 de noviembre de 2026: por ahora solo (d) y (e).** (a) y (c) se retoman si se ve abuso o cuando
  crezca el costo de la IA. La cláusula (d) entra en la revisión legal de los términos.
  Para (e), ya existen 5 solicitudes por IP por hora y 3 por correo por hora (`RegistroService`, en memoria). Falta:
  lista de dominios de correo desechable; un límite largo (p. ej., 2 pruebas por IP cada 30 días, en base de datos y
  no en memoria); una prueba gratis por NIT; y normalizar los alias de Gmail (`nombre+x@`, puntos) al contar pruebas,
  para que no se encadenen pruebas con un solo correo.
- **Logs del PDF (baja prioridad).** Cada vez que se genera un PDF, PDFBox escribe 13 WARN de "Using fallback font
  LiberationSans" (el contenedor no trae las fuentes estándar de PDF; el PDF sale bien), y openhtmltopdf escribe con su
  propio logger, fuera del JSON de CloudWatch. Bajar `org.apache.pdfbox.pdmodel.font` a ERROR y mandar openhtmltopdf a
  SLF4J.

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
