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
- **Documentos.** PDF, PNG o JPG de hasta 20 MB. El tipo se valida por el contenido del archivo, no por la extensión. El archivo es inmutable y se guarda su SHA-256. Hoy va a disco local (`app.documentos.dir`); en AWS irá a S3 cifrado con la misma interfaz `AlmacenDocumentos`.
- **Auditoría.** Cada vista de historia o atención, cada firma, nota, carga o descarga queda en `accesos_historia` (quién, qué y cuándo).
- **CIE-10.** Es la tabla oficial de MinSalud (SISPRO, 12.634 códigos) con restricciones de sexo y edad. El buscador avisa cuando un código no aplica al paciente, porque RIPS lo rechazaría.
  - La migración se genera con `tools/cie10/generar-migracion.js` a partir del Excel oficial; el Excel no se guarda en el repositorio.
  - Para actualizarla cuando MinSalud publique una versión nueva, se genera otra migración con el mismo script.
- **PDF.** Fórmula médica y atención en tamaño carta, con encabezado del consultorio, paciente, firma electrónica con registro médico y numeración de páginas.
  - Se hacen con XHTML + CSS (OpenHTMLtoPDF), y todo el texto del usuario se escapa.
  - El PDF de un borrador lleva la marca de agua BORRADOR.
  - Cada impresión queda en la auditoría (`IMPRIMIR_FORMULA`, `IMPRIMIR_ATENCION`).
  - Endpoints: `GET /api/v1/formulacion/formulas/{id}/pdf` y `GET /api/v1/historia/atenciones/{id}/pdf`.

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
- Cifrado de campos sensibles (alergias, antecedentes) y log de acceso a historias clínicas.
- Alta de consultorios nuevos (registro del cliente) y recuperación de contraseña.
- Envío de invitaciones por correo (SES) y MFA para médicos.
- Agenda por médico, que usará las asignaciones de los secretarios.
- Edición de datos del paciente desde el front (la API ya la permite).
- Generación del PDF de la fórmula y firma del médico.
- Pruebas de integración con Testcontainers (multi-tenant, worker de IA).
- Evaluación de los prompts con casos clínicos reales, revisados por médicos.
- Regulación (Colombia): Res. 1995/1999 (historia clínica), Ley 1581/2012 (datos personales y transferencia a AWS/IA), Res. 866/2021 (interoperabilidad), RIPS y facturación electrónica.
