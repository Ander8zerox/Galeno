# Arquitectura

## Principios

1. **Monolito modular.** Un solo despliegue con módulos por dominio. Cada módulo expone un *service* público y oculta su repositorio: así cualquier módulo se puede extraer a un servicio aparte sin reescribir a sus consumidores.
2. **API-first.** El front solo conoce `/api/v1/*` y el contrato OpenAPI. Los errores van en formato RFC 9457 (`problem+json`, con `errores` por campo en las validaciones).
3. **Multi-tenant desde el día 1.** Hay una columna `tenant_id` en cada tabla clínica, con `@TenantId` de Hibernate. El consultorio sale del JWT, así que no hay consulta que pueda olvidar el filtro.
4. **La IA propone, las reglas verifican y el médico firma.** Toda salida de IA es estructurada (esquema JSON), pasa por validaciones deterministas y queda auditada.
5. **Infraestructura detrás de interfaces:** `LlmClient` (Bedrock o simulado) y la cola de IA (hoy MySQL, mañana SQS).

## Módulos (`backend/src/main/java/com/galeno`)

| Paquete | Responsabilidad |
|---|---|
| `identidad` | Consultorios (tenants), usuarios, membresías y roles, login con selector de consultorio, equipo e invitaciones |
| `pacientes` | Pacientes; `resumenAnonimizado()` es lo único que se envía a la IA |
| `formulacion` | Borrador con IA → `ValidadorFormula` (alergias, datos faltantes) → fórmula firmada inmutable |
| `apoyodiagnostico` | Diferenciales CIE-10, datos faltantes y signos de alarma |
| `agenda` | Horario semanal por médico, cupos disponibles, citas y sus estados |
| `historia` | Atenciones (borrador → firmada), diagnósticos CIE-10, notas aclaratorias, documentos clínicos y auditoría de acceso |
| `shared.ai` | `LlmClient`, prompts versionados (`resources/prompts`), cola y auditoría `ai_jobs` |
| `shared.tenancy` | `TenantContext`, filtro del JWT y resolver de Hibernate |
| `shared.security` | Spring Security como resource server de JWT, `Permiso`, `SesionVigenteFilter` |

## Usuarios, consultorios y permisos

```
usuarios (persona, global) ──< membresias (usuario × consultorio, varios roles, ACTIVA|SUSPENDIDA) >── consultorios
     └── perfiles_medicos (registro ReTHUS, especialidad)      └── asignaciones_secretario (secretario → médicos)
invitaciones (por consultorio, token de un solo uso, 7 días; solo se guarda su SHA-256)
registros_consultorio (solicitudes de registro sin verificar, 24 horas; solo se guarda el SHA-256 del token)
```

- **Una persona, una cuenta.** Un médico que atiende en dos consultorios usa la misma cuenta; en cada uno tiene sus propios roles.
- **Roles → permisos.** El código autoriza por `Permiso`, nunca por rol. Los roles (`identidad/Rol.java`) son conjuntos de permisos:

| Permiso | ADMIN | MEDICO | SECRETARIO |
|---|:-:|:-:|:-:|
| `EQUIPO_GESTIONAR` | ✅ | | |
| `PACIENTE_GESTIONAR` (datos demográficos) | ✅ | ✅ | ✅ |
| `HISTORIA_VER` (alergias, antecedentes) | | ✅ | |
| `FORMULA_REDACTAR`, `FORMULA_FIRMAR`, `IA_DIAGNOSTICO` | | ✅ | |

- **Login en dos pasos.** Con un consultorio, `POST /auth/login` entrega directamente el token de sesión. Con varios, entrega un *token de selección* (10 minutos), que solo permite `GET /auth/consultorios` y `POST /auth/sesion`. El token de sesión lleva `tenant_id`, `membresia_id` y los permisos.
- **Los cambios aplican de inmediato.** `SesionVigenteFilter` rechaza el token si la membresía se suspendió o perdió permisos, con una caché de 60 s que se invalida cuando el administrador hace un cambio.
- **Datos clínicos.** Quien no tiene `HISTORIA_VER` recibe `alergias` y `antecedentes` en `null`, y sus ediciones nunca los modifican.
- **Trabajos de IA.** Cada trabajo solo lo ve quien lo pidió.
- **Firma.** Exige un perfil médico y guarda en la fórmula el registro médico vigente.
- **Invitaciones.** El administrador crea la invitación y comparte el enlace (más adelante se enviará por correo con SES). El token va en el fragmento `#`, así que no llega a los logs del servidor ni de CloudFront. Si el correo ya tiene cuenta, se pide su contraseña actual y se le agrega el consultorio.
- **Asignación secretario → médicos.** Define qué agendas maneja cada secretario.
- **Registro de consultorios.** Es público y tiene dos pasos (`RegistroService`).
  - `POST /api/v1/registro` valida los datos y guarda la solicitud. Exige contraseña de al menos 10 caracteres, registro médico si la persona es médico, aceptación de términos y NIT con el dígito de verificación de la DIAN (si no lo trae, se calcula). Luego envía al correo un enlace con el token en el fragmento `#`. Responde 202 exista o no una cuenta con ese correo, para no revelar qué correos usan Galeno.
  - `POST /api/v1/registro/consultar` y `/confirmar` crean el consultorio con la constancia de los términos (versión, fecha y quién), la cuenta y el perfil médico, y la membresía ADMIN (más MEDICO si aplica). Dejan a la persona con sesión iniciada. Si el correo ya tenía cuenta, se pide la contraseña actual y la cuenta no cambia.
  - El enlace vence en 24 horas y se usa una sola vez; la confirmación bloquea la solicitud para que dos clics no creen dos consultorios. Las solicitudes vencidas se borran.
  - **Límites** (`LimiteIntentos`, en memoria): por IP (`app.registro.solicitudes-por-ip-hora`, 5 en producción) antes de validar, y 3 solicitudes válidas por correo por hora. En AWS la IP real llega en `X-Forwarded-For` desde el balanceador: hay que activar `server.forward-headers-strategy` y aceptar ese encabezado solo del ALB.
- **Recuperación de contraseña.** `POST /api/v1/auth/recuperar` responde 202 exista o no la cuenta. Si existe, envía un enlace de un solo uso que vence en 1 hora (`recuperaciones_contrasena`; token en el `#`, solo se guarda su hash), y pedir otro anula el anterior. `/auth/restablecer` cambia la contraseña y pone `usuarios.credenciales_cambiadas_en`. `SesionVigenteFilter` rechaza cualquier token emitido antes de esa fecha (sesión, selección o plataforma), así que cambiar la contraseña cierra todas las sesiones abiertas. También avisa por correo del cambio. Hay límites por IP y 3 solicitudes por correo por hora, exista o no la cuenta, para que el 429 no revele cuáles existen.
- **Correo.** Va detrás de la interfaz `EnvioCorreo`. Con `app.correo.proveedor=local` (el valor por defecto) los correos quedan en un buzón en memoria, visible en `GET /api/v1/dev/buzon` y en la página `/dev/buzon` del front (solo en desarrollo). Con `ses`, `SesEnvioCorreo` envía con la API v2 de SES desde `app.correo.remitente` y con el *configuration set* que publica los eventos.
  - **Rebotes y quejas.** SES los publica en un tema SNS suscrito a una cola SQS, y `ColaEventosSes` la lee con *long polling* (si `app.correo.cola-eventos` tiene la URL). Así la API no expone un endpoint público ni tiene que validar firmas de SNS, y si está caída los eventos esperan en la cola. `EventosSes` acepta el evento dentro del sobre de SNS o crudo.
  - **Supresión.** Un rebote permanente o una queja dejan la dirección en `correos_suprimidos` (V14), y los dos proveedores dejan de escribirle (registran un aviso en el log). Los rebotes transitorios solo se registran. En la consola de la plataforma, "Correos bloqueados" lista las direcciones y permite desbloquear una, con auditoría. En desarrollo, `POST /api/v1/dev/buzon/evento-ses` simula un evento con el mismo JSON de SES.

## Suscripciones y consola de la plataforma

```
PRUEBA (30 días) ─┐
                  ├─ vence ─> VENCIDA (5 días de gracia, con aviso) ─> SUSPENDIDA (solo lectura)
ACTIVA (pagada) ──┘                         └─────────── pago aprobado ───────────┘
```

- **Estado calculado.** `suscripciones` guarda fechas (`prueba_hasta`, `pagada_hasta`) y la suspensión manual; el estado se calcula con ellas (`Suscripcion.estado`), así nunca queda desactualizado porque un proceso no corrió.
- **Prueba.** Un consultorio registrado por la web recibe 30 días (evento `ConsultorioRegistrado`). Los consultorios anteriores empezaron su prueba con la migración V11, y si alguno no tiene suscripción se le abre al consultarla.
- **Pagos.** El ADMIN (permiso `SUSCRIPCION_GESTIONAR`) ve los planes y los datos bancarios, paga por transferencia o consignación y reporta el pago con referencia y comprobante (PDF, PNG o JPG de hasta 5 MB, reconocido por su contenido; va en `comprobantes_pago`, aparte, para que listar no cargue archivos). Solo puede haber un pago en revisión a la vez.
- **Extensión.** Al aprobar, se suman los meses del plan desde el vencimiento (si paga antes, no pierde días) o desde hoy si ya estaba vencida (no se cobra el tiempo vencido). Con bloqueo de fila: dos aprobaciones no pisan la fecha.
- **Solo lectura.** `SuscripcionFilter` responde 402 a todo lo que no sea GET en un consultorio suspendido, salvo la sesión y la suscripción (para poder pagar). No se bloquea el acceso completo: el consultorio debe poder consultar y entregar sus historias (Res. 1995). Caché de 60 s que se invalida con el evento `SuscripcionCambiada`.
- **Avisos** (`AvisosSuscripcion`, todos los días a las 8 a. m. hora Colombia): 7, 3 y 1 día antes, al vencer y al quedar en solo lectura. Cada uno sale una vez por fecha de vencimiento (`avisos_suscripcion`). En local, `POST /api/v1/dev/suscripciones/avisos` lo corre de inmediato.
- **Consola de la plataforma** (`/plataforma` en el front, `/api/v1/plataforma/**` en la API). La usan los operadores de Galeno, no los consultorios.
  - Login propio que entrega un token con la authority `PLATAFORMA`, sin consultorio ni permisos de consultorio, y que se verifica en cada llamada.
  - Los operadores son cuentas de Galeno marcadas por configuración (`app.plataforma.operadores`); nadie puede volverse operador desde la web.
  - Permite revisar pagos (ver el comprobante, aprobar, rechazar con motivo), registrar un pago recibido sin reporte, regalar días, suspender y reactivar, cambiar precios y datos de pago. Cada acción queda en `auditoria_plataforma`.

## Programa de partners

```
partner se inscribe → la plataforma lo aprueba (código) → consultorio se registra con el código (atribuido, 10 % de descuento)
    → cada pago aprobado → comisión del 20 % (retenida 30 días → disponible) → liquidación cuando suma $190.000
```

- **Módulo `partners`.** Depende de identidad, suscripciones y plataforma; ninguno de ellos depende de partners. Se engancha por eventos:
  - `ConsultorioRegistrado` trae el código del registro. Partners atribuye el consultorio y aplica el descuento en la suscripción (`suscripciones.descuento_porcentaje`).
  - `PagoAprobado` genera la comisión. Identidad valida el código al pedir el registro con la interfaz `CodigosReferido`, que implementa partners.
- **Partner.** Es una cuenta normal de Galeno. Se inscribe en `/partner/inscripcion` (verifica el correo como el registro: `inscripciones_partner`, token de un solo uso) y queda PENDIENTE. Al aprobarlo, la plataforma le asigna un código (`PEDROV694`). Puede quedar SUSPENDIDO (sin comisiones nuevas y su código no sirve; al reactivarlo conserva el código) o RECHAZADO.
- **Atribución** (`referidos`). A lo sumo un partner por consultorio, fijo desde el registro. Solo un operador lo cambia (`PUT /plataforma/consultorios/{id}/partner`), por ejemplo para un cliente que llegó sin el enlace. El enlace `/registro?ref=CODIGO` se recuerda 60 días en el navegador.
- **Comisiones** (`comisiones_partner`). Hay una por pago aprobado, con el porcentaje vigente guardado. La prueba gratis no genera nada, y no hay comisión si el partner es el mismo administrador del consultorio o tiene su NIT. El estado se calcula: RETENIDA (30 días), DISPONIBLE, PAGADA (en una liquidación) o ANULADA (un operador la anula, por ejemplo por un pago reversado).
- **Liquidación** (`liquidaciones_partner`). El operador transfiere y registra la referencia. Entran todas las comisiones disponibles, que deben sumar al menos el mínimo, y se toman con bloqueo para no pagar dos veces la misma.
- **Portal `/partner`.** Login propio (authority `PARTNER`, sin consultorio). Muestra el código, el enlace, el saldo (retenido, disponible, pagado), los referidos (solo el nombre del consultorio y el estado de su suscripción, nunca datos de pacientes), las comisiones, los pagos recibidos y los datos de pago.
- **Parámetros** (`app.partners.*`): `comision-porcentaje` 20, `descuento-porcentaje` 10, `minimo-liquidacion` 190000, `retencion-dias` 30.

## Agenda

- **Quién ve qué.** El administrador ve todas las agendas, el médico la suya y el secretario las de sus médicos asignados (o todas, si no tiene asignaciones). Los roles se suman. El horario solo lo cambian el propio médico o un administrador.
- **Horario.** Franjas semanales en hora local del consultorio (`consultorios.zona_horaria`, por defecto `America/Bogota`) y una duración de cita por médico. Las citas se guardan en UTC.
- **Reglas al agendar.** No se agenda en el pasado. La cita debe caber en una franja y no cruzarse con otra, salvo que sea **sobrecupo**, que es una decisión explícita y queda marcada. Al cambiar el horario, las citas ya agendadas no se mueven.
- **Concurrencia.** La reserva bloquea la agenda del médico (`SELECT … FOR UPDATE`) y verifica los cruces con una lectura con bloqueo. Así, dos reservas simultáneas del mismo cupo no pueden ganar ambas. Está probado con reservas en paralelo.
- **Estados.** PROGRAMADA → CONFIRMADA → ATENDIDA, NO_ASISTIO o CANCELADA (esta exige motivo). Solo el médico de la cita la marca como atendida. Reprogramar devuelve la cita a PROGRAMADA.
- **Pendiente.** Bloqueos de agenda (vacaciones o festivos), recordatorios por WhatsApp o SMS, vista semanal y la conexión "Atender" → atención clínica.

## Historia clínica

- **Atención.** Motivo, enfermedad actual, antecedentes (se precargan del paciente), revisión por sistemas, signos vitales, examen físico, diagnósticos CIE-10 con tipo RIPS (impresión diagnóstica, confirmado nuevo o repetido), análisis y plan.
- **Borrador → firmada.** El borrador solo lo ven y editan su autor. Tiene bloqueo optimista: si otra pestaña guardó antes, la API responde 409. Para firmar se exigen motivo de consulta y exactamente un diagnóstico principal; se guarda una copia del registro médico y del nombre del médico.
- **Inmutable.** Una atención firmada no se edita ni se elimina (Res. 1995). Las correcciones son **notas aclaratorias**, también inmutables y con su autor.
- **Historia compartida.** Todos los médicos del consultorio ven las atenciones firmadas. Secretarios y administradores sin rol médico no ven la historia.
- **Conexiones.**
  - Con la agenda: "Atender" crea o reabre la atención de la cita, y al firmarla la cita queda ATENDIDA.
  - Con la fórmula: puede quedar ligada a la atención (`formulas.atencion_id`).
  - Con el apoyo diagnóstico: usa los datos de la atención y sus diferenciales se agregan como diagnósticos.
- **Documentos.** PDF, PNG o JPG de hasta 20 MB. El tipo se valida por el contenido del archivo, no por la extensión. El archivo es inmutable y se guarda su SHA-256. Va detrás de la interfaz `AlmacenDocumentos` con la clave `consultorio/paciente/uuid`. Con `app.documentos.almacen=disco` (desarrollo) queda en `app.documentos.dir`; con `s3`, en el bucket `app.documentos.bucket`. El bucket es privado, versionado y cifrado con KMS, y la escritura lleva `If-None-Match: *`, así que S3 rechaza escribir sobre una clave existente (como `CREATE_NEW` en disco). Los comprobantes de pago de las suscripciones siguen en la base de datos.
- **Auditoría.** Cada vista de historia o atención, cada firma, nota, carga o descarga queda en `accesos_historia` (quién, qué y cuándo).
- **CIE-10.** Es la tabla oficial de MinSalud (SISPRO, 12.634 códigos) con restricciones de sexo y edad. El buscador avisa cuando un código no aplica al paciente, porque RIPS lo rechazaría.
  - La migración se genera con `tools/cie10/generar-migracion.js` a partir del Excel oficial; el Excel no se guarda en el repositorio.
  - Para actualizarla cuando MinSalud publique una versión nueva, se genera otra migración con el mismo script.
- **PDF.** Fórmula médica y atención en tamaño carta, con encabezado del consultorio, paciente, firma electrónica con registro médico y numeración de páginas.
  - Se hacen con XHTML + CSS (OpenHTMLtoPDF), y todo el texto del usuario se escapa.
  - El PDF de un borrador lleva la marca de agua BORRADOR.
  - Cada impresión queda en la auditoría (`IMPRIMIR_FORMULA`, `IMPRIMIR_ATENCION`).
  - Endpoints: `GET /api/v1/formulacion/formulas/{id}/pdf` y `GET /api/v1/historia/atenciones/{id}/pdf`.
  - **Historia completa** (`GET /api/v1/historia/pacientes/{id}/pdf`, botón "Descargar historia completa"): índice y todas las atenciones firmadas o importadas seguidas, de la más antigua a la más reciente (sin forzar una página por atención), con sus notas aclaratorias, más la lista de documentos adjuntos (sin los archivos). Los borradores no salen. Queda en la auditoría como `IMPRIMIR_HISTORIA`. Es la copia que se entrega al paciente o en un traslado.

## Importación de historias (RIPS y listados)

- **Fuente.** El JSON de RIPS que todo prestador genera con cada factura electrónica (un archivo por factura). Se suben varios a la vez o un ZIP (hasta 50 MB; el ZIP se corta a 5.000 archivos o 300 MB descomprimidos).
- **Flujo.** Subir → lote `EN_REVISION` con un registro por paciente (todas sus atenciones de todos los archivos) → confirmar → opcionalmente deshacer. Nada llega a la historia antes de confirmar.
- **Deduplicación.** Por tipo y número de documento. El paciente que ya existe conserva sus datos (si el RIPS trae otra fecha de nacimiento u otro sexo, se avisa). El que no existe se crea con `datos_incompletos`: el RIPS no trae nombres, así que queda como "Paciente CC 123" hasta que alguien use **Completar datos**.
- **Atenciones.** Consultas, procedimientos, urgencias y hospitalización se crean FIRMADAS con origen IMPORTADA, sin médico de Galeno (el documento del profesional del RIPS queda en el nombre), con el CUPS (catálogo `cups`, generado con `tools/cups/generar-migracion.js`; la tabla que tenemos es parcial) y los diagnósticos CIE-10 con su tipo RIPS. Medicamentos, recién nacidos y otros servicios se cuentan como omitidos.
- **Idempotencia.** Cada servicio tiene una `clave_importacion` (factura, usuario, tipo y consecutivo) única por consultorio: subir los mismos archivos otra vez no duplica nada.
- **Deshacer.** Quita las atenciones del lote y los pacientes que creó, salvo los que ya tienen otra actividad (citas, fórmulas, documentos u otras atenciones). Se niega si alguien ya agregó notas aclaratorias o fórmulas a lo importado. La auditoría (`IMPORTAR_ATENCIONES`, `DESHACER_IMPORTACION`) no se borra.
- **Permisos.** `IMPORTAR_HISTORIAS` (rol ADMIN). El detalle clínico de cada registro (atenciones y diagnósticos) solo se muestra a quien además tiene `HISTORIA_VER`.
- **Listado de pacientes (Excel o CSV).** Completa lo que el RIPS no trae.
  - **Lectura.** `.xlsx` (primera hoja, con StAX, sin DTD ni entidades externas y con límite de tamaño) o `.csv` (detecta UTF-8 o Windows-1252 y el separador `;`, `,` o tabulador). Máximo 50.000 filas.
  - **Columnas.** Se reconocen por el encabezado ("Nro. Identificación", "Apellidos y nombres", "F. Nacimiento"…) y la persona ajusta el mapeo antes de revisar. El mapeo queda en `plantillas_importacion` por firma de encabezados: el siguiente archivo del mismo sistema llega ya mapeado.
  - **Valores.** Tipos de documento escritos de cualquier forma (C.C., Cédula, PPT), números sin puntos, fechas de Excel (número de serie) o escritas (dd/mm/aaaa, aaaa-mm-dd, validadas estrictamente), nombres en mayúsculas pasados a nombre propio y separación de un nombre completo (las partículas "de la" van con el apellido; con 3 palabras se avisa).
  - **Reglas.** Al paciente creado por el RIPS sin nombre se le pone el nombre y se le quita la marca; a uno completo solo se le llenan teléfono y correo vacíos, y las diferencias (otro nombre, otra fecha) se muestran pero no se aplican. Si el número existe con otro tipo (TI → CC) el registro queda fuera hasta que alguien lo revise. Para crear uno nuevo se exigen nombres, apellidos, fecha de nacimiento y sexo.
  - **Deshacer.** Cada registro guarda los cambios aplicados (campo, antes, después). Al deshacer se restauran, salvo en los campos que alguien editó después de importar, y se borran los pacientes que creó el lote si no tienen actividad.
  - **Endpoints.** `POST /api/v1/importaciones/pacientes/columnas` (multipart `archivo`: encabezados, muestra y mapeo sugerido) y `POST /api/v1/importaciones/pacientes` (`archivo` + `mapeo` en JSON). Revisar, confirmar y deshacer usan los mismos endpoints del lote.
- **Pacientes con datos incompletos.** Filtro en la lista (`GET /api/v1/pacientes?incompletos=true`) y aviso al agendar.
- **Endpoints.** `POST /api/v1/importaciones/rips` (multipart `archivos`), `GET /api/v1/importaciones`, `GET /api/v1/importaciones/{id}?estado=`, `PUT …/{id}/registros[/{registroId}]`, `POST …/{id}/confirmar`, `POST …/{id}/deshacer` y `DELETE …/{id}` (descartar).

## Flujo de IA

```
Front ──POST /formulacion/borradores──► API ──INSERT ai_jobs (PENDIENTE)──► 202 {jobId}
Front ──GET /ai-jobs/{id} cada 1,5 s──► API
                                AiJobWorker (cada 1 s, SELECT … FOR UPDATE SKIP LOCKED)
                                  └─ handler del módulo → LlmClient (Bedrock) → validaciones
                                  └─ UPDATE ai_jobs (COMPLETADO + resultado, modelo, tokens)
```

- La llamada al modelo se hace **fuera de transacción**, en hilos virtuales, con una concurrencia configurable.
- `SKIP LOCKED` permite correr varias instancias (ECS) sin procesar dos veces un mismo trabajo.
- Los trabajos que quedan colgados más de 5 minutos vuelven a la cola, hasta 3 intentos.
- `ai_jobs` guarda quién pidió qué, la versión del prompt, el modelo, los tokens, el resultado y los errores. La fórmula firmada referencia su `ai_job_id`.

## Decisiones y cómo cambian después

| Hoy (Capa 1) | Después | Qué cambia en el código |
|---|---|---|
| JWT propio HS256 | Cognito / Keycloak | `SecurityConfig.jwtDecoder` (issuer-uri) y `AuthContext.tsx` |
| Cola en MySQL | SQS | `AiJobWorker` (reclamo de trabajos); los handlers no cambian |
| Polling cada 1,5 s | SSE / WebSocket | `useAiJob.ts` |
| MySQL en RDS | Aurora MySQL | Nada (misma familia de motor) |
| Un contenedor en EC2 | ECS Fargate + ALB | Nada (misma imagen) |
| Módulos en un proceso | Servicio de IA aparte | Implementar `LlmClient` como cliente HTTP |

## Pendientes conocidos antes de producción

- Catálogo de medicamentos (CUM/INVIMA) y rangos de dosis en `ValidadorFormula`.
- Cifrado de campos sensibles (alergias, antecedentes).
- Envío de las invitaciones por correo y MFA para médicos y operadores de la plataforma.
- Factura electrónica de Galeno por cada pago de suscripción (DIAN).
- Revisión legal de los términos y de la política de datos antes de abrir el registro al público.
- Imagen de la firma del médico en los PDF.
- Pruebas de integración con Testcontainers (multi-tenant, worker de IA).
- Evaluación de los prompts con casos clínicos reales, revisados por médicos.
- Regulación (Colombia): Res. 1995/1999 (historia clínica), Ley 1581/2012 (datos personales y transferencia a AWS/IA), Res. 866/2021 (interoperabilidad), RIPS y facturación electrónica.
