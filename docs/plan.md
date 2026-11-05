# Hoja de ruta

Estado al **4 de noviembre de 2026**. Galenum (marca visible; el código interno sigue como "galeno") está en
producción en AWS, en https://galenum.com.co.

## Hecho

### Producto

- **Base (Capa 1):** pacientes, multi-tenant por consultorio, redactor de fórmulas con IA y apoyo diagnóstico con IA.
- **Equipo del consultorio:** membresías con varios roles (administrador, médico, secretario), permisos, selector de
  consultorio e invitaciones.
  - **Invitaciones por correo:** Galenum envía el enlace (quién invita, consultorio, roles, vencimiento). El enlace y
    "Copiar" quedan de respaldo. "Reenviar" crea un enlace nuevo y anula el anterior; máximo 5 envíos por hora a la
    misma dirección.
- **Agenda por médico:** horario semanal, cupos, citas, sobrecupos, estados, vista para secretarios y festivos de
  Colombia (con la opción "Atiendo festivos"). Confirmación antes de cancelar o marcar inasistencia, y "Deshacer" al
  confirmar o atender.
- **Historia clínica:**
  - Atenciones con firma e inmutabilidad, notas aclaratorias, documentos en S3, CIE-10 oficial de MinSalud (con
    avisos de sexo y edad), auditoría, y conexión con agenda, fórmulas y apoyo diagnóstico.
  - Anular un documento subido por error: con motivo; el archivo se conserva.
  - PDF de la atención, de la historia completa y de la fórmula. Se descargan como
    `tipo-apellidos-nombres-fecha.pdf`, nunca con el número de documento.
- **Importación:**
  - RIPS JSON (varios archivos o ZIP) con revisión, confirmación y deshacer.
  - Listados de pacientes en Excel o CSV, con el mapeo de columnas recordado por sistema de origen.
- **Registro de consultorios desde la web:** verificación del correo, NIT con dígito de verificación, aceptación de
  términos y política de datos, y límite de intentos. Al activarse, abre la agenda con un aviso de bienvenida.
- **Seguridad de las cuentas:**
  - Recuperación de contraseña por enlace de un solo uso, que cierra las sesiones abiertas.
  - MFA (TOTP): obligatoria para operadores de la plataforma y administradores de consultorio, opcional para los
    demás. Incluye códigos de recuperación, "recordar este equipo" 30 días y restablecimiento desde la consola.
- **Suscripciones:**
  - Prueba de 30 días y planes mensual y anual.
  - Pago por transferencia o consignación: lo reporta el consultorio y se aprueba en la consola de la plataforma.
  - Gracia de 5 días, después solo lectura, y avisos por correo.
  - Aviso si un precio parece mal digitado.
- **Programa de partners:**
  - Inscripción con aprobación de la plataforma, y código y enlace de referido.
  - 10 % de descuento para el consultorio referido.
  - Comisión recurrente del 30 % sobre cada pago, con retención de 30 días y liquidación desde $30.000.
- **Consola de la plataforma:** operadores, consultorios, pagos, partners, correos bloqueados y auditoría con el
  detalle y el operador de cada acción.
- **Correo:** Amazon SES con el dominio verificado (DKIM, SPF y DMARC), fuera del sandbox. Los rebotes y las quejas
  llegan por SNS y SQS. Soporte en soporte@galenum.com.co (reenvío de ImprovMX), con Reply-To en los correos y
  "Ayuda" al pie de las pantallas.
- **Motor de formatos clínicos** (`docs/diseno-formatos.md`):
  - **Fase 1:** motor genérico.
    - Tipos de campo: texto, número, fecha, selección, casillas, sí/no, tabla y escala con puntaje.
    - Obligatorios al firmar y `visibleSi`.
    - Catálogo versionado e inmutable en `backend/src/main/resources/formatos`.
    - Activación por consultorio (menú Formatos), formatos por defecto de cada profesional y varios formatos por
      atención.
    - Salen en el PDF y en la IA.
    - Primer formato: **historia odontológica v1**.
  - **Fase 2:** formato **Odontograma**.
    - Numeración FDI, cinco superficies, hallazgos por diente y por superficie, dentición permanente, temporal o
      mixta.
    - Cada atención parte del último odontograma firmado (opción genérica `arrastrar`).
    - En el PDF va como cuadrícula con colores y lista de hallazgos.
- **Consultorio de demostración:** archivos de pacientes y RIPS ficticios en `docs/demo` y guía para montarlo en
  producción.

### Infraestructura en AWS (producción)

Detalle en `docs/aws-capa-1.md` y operación en `docs/guia-operacion.md`.

- **Proyecto:** nueva experiencia de AWS, región **us-east-2**, perfil `galeno` con `aws login`. Plan pagado con
  créditos, límite de gasto de 60 USD y presupuesto de 50 USD.
- **Terraform** en `infra/`, con el estado local en `infra/terraform.tfstate`, fuera de git: hay que guardarlo.
- **Cómputo:**
  - EC2 `t4g.small` con Docker: la API y **Caddy** con HTTPS (Let's Encrypt) entre CloudFront y el servidor.
  - IP elástica e imagen en ECR.
- **Base de datos:** RDS MySQL `db.t4g.micro`, con copias de 7 días y protección contra borrado.
- **Documentos:** S3 cifrado con KMS, con versiones que nadie puede borrar.
- **Web:** CloudFront con el certificado de ACM (en us-east-1, como lo exige CloudFront) y una función para la SPA.
  Route 53 para galenum.com.co; el dominio se compró en Colombia Hosting.
- **Secretos y configuración:** SSM Parameter Store bajo `/galeno/prod/`. Las claves de la base de datos y del JWT
  quedan fuera del estado de Terraform.
- **Logs:** CloudWatch en JSON, sin el ruido de PDFBox.
- **Despliegue:** `tools/desplegar.sh` desde el computador, con `aws login`.
- **Pruebas en producción (paso 12):** hechas. Todos los hallazgos quedaron corregidos: inicio tras activar,
  errores de IA en español, borradores con documentos, inasistencia con atención abierta, confirmaciones, anular
  documentos, auditoría, precios, condiciones de partners, festivos, signos vitales, espacio para firmar, marca
  Galenum, cláusula contra copia en los términos, soporte y logs.

### Diseños escritos

- `docs/arquitectura.md`, `docs/aws-capa-1.md` y `docs/guia-operacion.md`.
- `docs/diseno-formatos.md`: fases 1 y 2 construidas.
- `docs/diseno-rips.md`: fase 1 construida; la norma vigente quedó verificada.

## En curso

### RIPS, fase 1 (rama `feature/rips-fase1`, lista para su PR)

- **Módulos opcionales por consultorio** (menú Módulos del administrador). El primero es "RIPS y facturación",
  incluido en todos los planes. Apagado, no se ve nada y el servidor rechaza sus servicios.
- **Procedimientos realizados con CUPS en la atención:** cantidad, valor, diente FDI y superficies. Quedan firmados con
  la atención y salen en el PDF, sin valores.
- **Consulta del día sugerida:** primera vez o control, según la especialidad del médico.
- **Lista de precios por CUPS** (menú Precios).
- Permisos nuevos del administrador: `MODULOS_GESTIONAR` y `RIPS_GESTIONAR`. Tras desplegar, se ven después de volver
  a ingresar.

## Siguiente (en orden)

### 1. RIPS, fase 2: datos y generación del JSON

- ✅ **Entrega 1 (datos):** tablas de SISPRO, datos del paciente para RIPS (tarjeta en la ficha), prestador y documento
  de los profesionales (menú RIPS) y aviso "RIPS: faltan N" en cada cita de la agenda para completarlos al recibir al
  paciente. Siguen la entrega 2 (pagadores y cobro en la cita) y la 3 (lote y JSON).
- 📝 **Entrega 2 (pagadores y cobro), diseñada:** catálogo de aseguradoras de SISPRO, pagadores del consultorio con
  CUCON y modalidad, tarifas por convenio, pagador del paciente y cobro en la cita (concepto, valor y autorización).
  Ver "Fase 2, entrega 2" en `docs/diseno-rips.md`.
- **Entrega 3 (lote y JSON):** además de lo de abajo, cargar el XML de la factura emitida por el facturador externo
  para cruzar totales antes de descargar, y aplicar las validaciones cruzadas del numeral 1.8 del Documento Técnico 1.
- ⏳ **Por decidir:** activar el módulo RIPS por defecto en los consultorios nuevos (la Res. 948 parece exigir RIPS
  también por particulares; ver "¿Particulares también?" en `docs/diseno-rips.md`).


Prioridad: los consultorios objetivo facturan sobre todo a EPS y prepagadas, y sin RIPS no cobran. La norma vigente es
la **Res. 948 de 2026**, con el Documento Técnico 1 v003 y el 2 v001, ambos del 15 de julio de 2026 (ver
`docs/diseno-rips.md`).

- **Datos que faltan:**
  - Del paciente: tipo de usuario, país, municipio, zona y pagador (con número de afiliación).
  - Del consultorio: código de habilitación de 12 caracteres, servicios habilitados y valores por defecto (finalidad,
    causa, modalidad).
  - Del médico: tipo y número de documento.
- **Pagadores del consultorio** (EPS, prepagadas, ARL, SOAT, particular), con su **CUCON** y modalidad de pago, y
  tarifas por convenio.
- **Cuota moderadora y copago:** la secretaria los registra en la cita, porque no ve datos clínicos, y quien atiende
  puede corregirlos en la atención.
- **Listas de valores de SISPRO** cargadas como catálogos.
- **Lote de RIPS:**
  - Se elige el pagador y el período, se revisan las atenciones con avisos de lo que falta y se escribe el número de
    la factura.
  - Galenum valida lo que pueda validar por su cuenta y descarga el JSON.
  - Las atenciones del lote quedan marcadas para no facturarlas dos veces.
  - El botón "Enviar al Ministerio" queda deshabilitado.
- Revisar la importación (`RipsParser`) contra el Documento Técnico 1 v003. Nuevos campos: CIE-11 opcional,
  `codigoVIDA` y `registroSIRAS`.

### 2. Odontología, fase 3 de los formatos

- **Plan de tratamiento con presupuesto:** procedimientos (CUPS) por fases, con diente, superficie y valor de la lista
  de precios; aceptación del paciente; y una marca en cada sesión cuando se realizan. Antes, escribir su diseño.
- Periodontograma: profundidad de sondaje, sangrado, movilidad y recesión.
- Consentimientos odontológicos: exodoncia, endodoncia y anestesia local.
- Que un hallazgo del odontograma sugiera el CUPS del procedimiento.
- Ver la diferencia de cada odontograma frente al anterior.

### 3. Agenda

- Recordatorios por WhatsApp o SMS.
- Bloqueos por vacaciones y días cerrados del consultorio.
- Vista semanal.

### 4. Suscripciones

- Pasarela de pagos (Wompi, PayU…), cuyo webhook haga lo mismo que "Aprobar".
- Factura electrónica de Galenum por cada pago (obligatoria ante la DIAN).
- Renovación automática.

### 5. Historia clínica

- Imagen de la firma del médico y código QR de verificación en los PDF.
- Reporte de auditoría para el administrador.

### 6. Formatos transversales (fase 4)

Incapacidad, remisión y contrarreferencia, y consentimiento informado firmado por el paciente.

### 7. Más adelante

- **RIPS, fases 3 y 4:**
  - Envío al MUV (API REST en Docker, tipo B, por cuenta de cada consultorio y con sus credenciales de SISPRO).
  - Factura electrónica desde Galenum.
- **Interoperabilidad de la historia clínica (IHCE)** del Ministerio: el RIPS ya trae `codigoVIDA` (el id de la
  atención en la IHCE), que será obligatorio cuando el sistema entre en operación. Seguir su calendario.
- Portal del paciente: cuenta separada que ve sus citas y fórmulas.
- Catálogo CUM/INVIMA y rangos de dosis en las fórmulas.
- **Registro:**
  - Lista de dominios de correo desechable.
  - Límite largo por IP (por ejemplo, 2 pruebas cada 30 días, en base de datos).
  - Una prueba gratis por NIT.
  - Normalizar los alias de Gmail. Las opciones (a) verificar en ReTHUS y (c) prueba con topes se retoman si se ve
    abuso.
- Técnico: cifrado de campos sensibles y pruebas de integración con Testcontainers.
- **AWS, Capa 2 (robustez):** alarmas en CloudWatch y Sentry; WAF en CloudFront (confirmar antes que la nueva
  experiencia lo permita); ambiente de *staging*.
- **AWS, Capa 3 (disponibilidad):** ALB con ECS Fargate (2 tareas o más), RDS Multi-AZ y SQS para la cola de IA.

## Pendientes que dependen del usuario (no de código)

- **IA en producción (Bedrock):**
  - Hoy está bloqueada. Haiku 5.5 solo tiene cuotas entre regiones (perfil `us.`), y AWS las dejó en 0 y negó la
    primera solicitud.
  - Hay un caso abierto en Soporte pidiendo 100.000 tokens por minuto en la cuota L-47520EB7 (US).
  - Al aprobarlo: poner el id del modelo `us.` y hacer una invocación de prueba, para ver si las políticas del proyecto
    permiten la inferencia entre regiones.
  - Plan B: la API directa de Anthropic, con la clave en SSM. Exige actualizar la política de datos: Anthropic como
    encargado y transferencia a EE. UU.
- **Validación clínica:** que un odontólogo revise la historia odontológica v1 y el odontograma (hallazgos y colores).
  Sus correcciones salen como v2 de cada formato.
- **Revisión legal:**
  - Términos y política de datos (hoy borradores en `/terminos` y `/politica-datos`), con la cláusula contra la copia.
  - Condiciones de los partners: contrato, retención en la fuente y ReteICA, cuenta de cobro o factura. Decidir si la
    comisión tiene tope (12 o 24 meses).
  - Si el RIPS es obligatorio para lo que paga un particular: el art. 8 de la Res. 948 lo sugiere.
- **Marca:** consultar en la SIC que "Galenum" esté libre en las clases 42 y 44, y registrarla.
- **Soporte:** un número de WhatsApp para "Ayuda".
- **Costos:** revisar el gasto real de AWS frente a lo esperado (35–50 USD al mes).
- **Demo:** montar el consultorio demo en producción con `docs/demo/README.md`, si todavía no está.

## Expansión a España (idea del 3 de noviembre de 2026, después del lanzamiento en Colombia)

Es un proyecto aparte, no una configuración. Orden: validar la demanda con algunas clínicas → asesoría de un abogado
español en protección de datos sanitarios → decidir la arquitectura → adaptar el producto.

- **Datos en la UE:**
  - La nueva experiencia de AWS fija una sola región por proyecto, así que España necesita su propia cuenta con región
    europea (por ejemplo, `eu-south-2`, Madrid) y un despliegue separado.
  - No mezclar pacientes colombianos y españoles en la misma base.
- **RGPD y LOPDGDD:**
  - Los datos de salud son categoría especial (art. 9).
  - Hace falta un contrato de encargado (DPA) con cada proveedor, el registro de actividades, la evaluación de impacto
    y, probablemente, un delegado de protección de datos.
  - Además, un representante en la UE (art. 27) si la empresa sigue siendo colombiana.
- **Correo de soporte:** ImprovMX no sirve tal cual. Opciones: Google Workspace con región en Europa, o recepción con
  SES en la región europea.
- **Producto:**
  - Lo colombiano queda fuera: RIPS, CUPS, NIT, DIAN y festivos.
  - Falta lo español: facturación con **Verifactu**, **receta médica privada electrónica** (RD 192/2023), historia
    clínica según la **Ley 41/2002**, euros e IVA, y festivos nacionales y autonómicos.
- **Comercial y fiscal:** facturar a clientes españoles desde Colombia o con una sociedad en España, IVA del SaaS y
  partners en euros.

## Importación de historias desde otros sistemas (pausada: queda para el final)

Principio: primero las fuentes masivas, todas por el mismo flujo **extraer → revisar → confirmar**.

| Prioridad | Fuente | Estado o cómo se procesa |
|---|---|---|
| ✔ | **RIPS JSON** | Hecho (escrito con la Res. 2275; revisar contra el Documento Técnico 1 v003 de la 948). Faltan medicamentos y otros servicios |
| ✔ | **Excel o CSV de pacientes** | Hecho. Falta: sugerir con IA las columnas que no se reconocen |
| 1 | RIPS TXT antiguos (AF, US, AC, AP…) | Solo si los clientes los tienen |
| 3 | PDF en lote (ZIP, digitales o escaneados) | IA con salida estructurada, un trabajo por archivo; procesamiento por lotes de Bedrock para migraciones grandes |
| 4 | Texto pegado (un paciente) | El mismo extractor de IA de los PDF |
| 5 | FHIR / RDA (Res. 866/2021) | Cuando un cliente lo pida |

- **Antes del punto 3:** un piloto con 20 historias reales para medir calidad, tokens y costo.
- **Deduplicación:** primero por documento; si no coincide, por parecido de nombre y fecha de nacimiento.
- **Legal:** contrato de encargado (Ley 1581) que mencione la IA, auditoría de quién importó y aprobó, y conservación
  del original (Res. 1995).
- **Comercial:** ofrecer "migración asistida incluida" para el primer lote de cada cliente.

## Para retomar en un chat nuevo

- **Idioma y estilo:** todas las respuestas en **español**. Nivel de ayuda HIGH (explicar cada paso, sugerir
  alternativas y señalar riesgos).
- **Git:** pedir permiso antes de cada commit, push o PR. Abrir los PR con la API de GitHub (no hay `gh`). Nunca subir
  `ACCESOS-LOCAL.txt`, `infra/terraform.tfvars`, `docs/guia-operacion.pdf` ni `logingalenumverde.jfif`.
- **AWS:** solo `aws login` con el perfil `galeno` en us-east-2; nunca pedir llaves de acceso. `terraform apply`,
  `tools/desplegar.sh` y los comandos dentro de la instancia los ejecuta el usuario.
- **Local:**
  - La API corre en 8090 contra la base `galeno`, que nunca se reinicia.
  - Las pruebas de punta a punta van contra un esquema aparte, `galeno_e2e`, con una segunda API en 8091 y el
    frontend en 5181 (configuración `web-e2e` de `.claude/launch.json`).
  - Los procesos se detienen por su PID.
- **Datos de demostración:** siempre ficticios, nunca de pacientes reales.
