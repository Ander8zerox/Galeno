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
- **Asignación secretario → médicos.** Queda guardada para la agenda, que es la siguiente entrega.

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
