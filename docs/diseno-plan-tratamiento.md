# Diseño: plan de tratamiento con presupuesto (odontología, fase 3)

Escrito el 28 de noviembre de 2026. Sin implementar.

## Problema

En odontología el tratamiento dura varias sesiones: el odontólogo examina, arma el plan (qué se hace en cada diente y
cuánto cuesta), el paciente lo acepta y en cada cita se va realizando una parte. Hoy Galenum tiene el odontograma y los
procedimientos realizados de cada atención, pero no el plan que los une: el presupuesto se hace en papel o en Excel y
nadie sabe qué falta por hacer ni cuánto ha pagado el paciente por lo realizado.

## Qué ya existe

| Pieza | Dónde | Uso en el plan |
|---|---|---|
| Odontograma (FDI, 5 superficies, hallazgos) | `formatos/Odontograma.java` | Punto de partida: los hallazgos sugieren procedimientos |
| Procedimiento realizado (CUPS, cantidad, valor, diente, superficies) | `historia/Procedimiento.java`, tabla `atencion_procedimientos` | Lo que se marca como hecho en cada sesión |
| Lista de precios por CUPS y tarifas por pagador | `rips/PreciosService` vía `historia/ListaPrecios` | El valor de cada ítem |
| Firma del paciente en pantalla | Consentimiento informado (fase 4 de formatos) | La aceptación del presupuesto |

## Conceptos

- **Plan de tratamiento:** del paciente, no de una atención. Lo crea un odontólogo, tiene fases y un total. Un
  paciente puede tener varios a lo largo del tiempo, pero **uno solo vigente**.
- **Fase:** grupo ordenado de ítems ("1. Urgencia", "2. Operatoria", "3. Rehabilitación"). Solo organiza.
- **Ítem:** un procedimiento planeado: CUPS, descripción, diente y superficies (opcionales), cantidad, valor unitario,
  descuento y estado.
- **Presupuesto:** el PDF del plan con sus valores, que el paciente acepta.

## Estados

Plan: **Borrador → Presentado → Aceptado → Terminado**, o **Cancelado** desde cualquiera.

- **Borrador:** se edita libremente.
- **Presentado:** se generó el PDF del presupuesto; todavía se puede volver a borrador.
- **Aceptado:** el paciente firmó en pantalla (o quedó la constancia de aceptación verbal, con quién la registró). Los
  ítems y valores aceptados quedan **fijos**.
- **Terminado:** todos los ítems están realizados o cancelados (automático), o el odontólogo lo cierra.

Ítem: **Pendiente → Realizado** (en una atención firmada) o **Cancelado** (con motivo).

**Cambios después de aceptar:** agregar un ítem o cambiar un valor crea un **adicional** (otra fase con su propia
aceptación), en vez de reescribir lo aceptado. Así el presupuesto firmado nunca cambia.

## Flujo

1. **Crear desde el odontograma.** En la atención, "Crear plan de tratamiento" toma el último odontograma y sugiere
   ítems por hallazgo; el odontólogo acepta, quita o agrega. Sugerencias iniciales (a validar con un odontólogo):

   | Hallazgo | Sugerencia (CUPS del catálogo, por superficie o diente) |
   |---|---|
   | Caries en una superficie | Obturación en resina, una o varias superficies |
   | Extracción indicada | Exodoncia de diente permanente o temporal |
   | Fractura | Obturación o corona, a elegir |

   Otros (profilaxis, detartraje, sellantes, endodoncia, prótesis) se agregan con el buscador de CUPS.
2. **Valores.** Cada ítem toma el valor de la lista de precios (o de la tarifa del pagador del paciente). Se puede
   cambiar en el borrador y aplicar un descuento por ítem o al total.
3. **Presentar y aceptar.** PDF del presupuesto (encabezado del consultorio, paciente, fases, ítems con diente,
   valores, total, vigencia de la cotización y observaciones). Firma en pantalla como el consentimiento.
4. **Realizar en cada sesión.** La atención muestra la tarjeta "Plan de tratamiento" con los ítems pendientes. Marcar
   uno lo agrega a los **procedimientos realizados** de esa atención con su CUPS, diente, superficie y el valor
   aceptado. Al firmar la atención, el ítem queda **Realizado** con el enlace a la atención; si se quita de la atención
   antes de firmar, vuelve a Pendiente.
5. **Seguimiento.** En la ficha del paciente: avance (realizados / total), valor realizado y valor pendiente. Lista del
   consultorio con planes aceptados sin actividad en 60 días, para que la secretaria llame.

## Datos

- `planes_tratamiento`: tenant, paciente, odontólogo, estado, observaciones, vigencia de la cotización (días),
  descuento global, aceptado en / por / firma (imagen) / constancia verbal, creado y actualizado.
- `planes_tratamiento_items`: plan, fase (número y nombre), orden, CUPS, descripción, diente, superficies, cantidad,
  valor unitario, descuento, estado, motivo de cancelación, atención donde se realizó, adicional (número de la
  aceptación a la que pertenece).
- Migración nueva (V43 o la que siga).
- Paquete propio `com.galeno.tratamiento`. Lee procedimientos y atenciones por la interfaz pública de la historia,
  como el motor de formatos; la historia solo sabe que un procedimiento puede venir "de un plan" (id del ítem).

## Permisos

| Acción | Quién |
|---|---|
| Crear, editar, presentar y cancelar un plan | MÉDICO (odontólogo) |
| Registrar la aceptación | MÉDICO; la secretaria también, si el paciente firma en recepción |
| Ver el presupuesto y el avance | MÉDICO, ADMIN y secretaria (son valores, no datos clínicos) |
| Marcar ítems como realizados | El dueño de la atención |

## Decisiones (28 de noviembre de 2026)

1. **Precios sin el módulo de RIPS:** la lista de precios funciona **siempre** (sirve también a consultorios de solo
   particulares); el módulo de RIPS solo agrega las tarifas por pagador.
2. **Cambios después de aceptar:** van como **adicional** con su propia aceptación; lo aceptado no cambia.
3. **Abonos y pagos del paciente:** fuera de esta fase. Encajan con la factura particular de Siigo (fase 4 de RIPS) o
   con una caja; por ahora el plan muestra lo realizado y lo pendiente, no lo pagado.

Abiertas:

- **Visibilidad:** propuesta, el plan aparece cuando el consultorio tiene activo el formato de odontología.
- **Sugerencias por hallazgo:** la tabla de arriba es una propuesta; la debe validar un odontólogo, igual que los CUPS
  exactos. Mientras tanto, el odontólogo acepta o cambia cada sugerencia.

## Fuera de esta fase

Periodontograma, consentimientos odontológicos y la diferencia entre odontogramas (siguen en la fase 3 del plan, cada
uno en su PR), abonos y caja, y planes para otras especialidades.

## Entregas

1. Tablas, servicio y API: crear, editar, estados, aceptación y PDF del presupuesto.
2. Pantallas: plan en la ficha del paciente, creación desde el odontograma y firma.
3. Realizar en la atención (tarjeta y enlace con los procedimientos) y lista de seguimiento.

## Cómo quedó la entrega 1 (28 de noviembre de 2026)

Backend completo, sin pantallas todavía.

- **Tablas (V43):** `planes_tratamiento`, `planes_tratamiento_items` y `planes_tratamiento_aceptaciones` (una fila
  por aceptación: la primera y cada adicional, con la firma o la constancia verbal y el total aceptado). El descuento
  es por ítem; no hay descuento global.
- **Paquete `com.galeno.tratamiento`:** `TratamientosService`, `TratamientosController` y `PresupuestoPdf`.
- **API (`/api/v1/tratamientos`):** listar por paciente, ver, crear, editar datos, presentar, volver a borrador,
  aceptar (`/aceptacion`), terminar, cancelar, agregar/editar/quitar ítems, cancelar un ítem aceptado y el PDF del
  presupuesto (queda en la auditoría como `IMPRIMIR_PRESUPUESTO`).
- **Reglas:**
  - Requiere el odontograma (`odonto.odontograma`) activo en el consultorio; si no, 404.
  - Un solo plan vigente por paciente.
  - Un ítem toma el valor de la lista de precios (o la tarifa del pagador con RIPS); sin precio, hay que escribirlo.
    CUPS, diente FDI y superficies se validan como en los procedimientos realizados.
  - Editar un plan presentado lo devuelve a borrador. Lo aceptado no se edita ni se quita: se cancela con motivo.
  - Después de aceptar, lo nuevo queda "por aceptar" y se acepta como adicional.
  - Terminar o cancelar el plan cancela lo pendiente y quita lo que nunca se aceptó.
- **Permisos:** `TRATAMIENTO_VER` (médico, administrador, secretaría y facturación), `TRATAMIENTO_REGISTRAR` (médico)
  y `TRATAMIENTO_ACEPTAR` (médico y secretaría).
- **Lista de precios:** ya no exige el módulo de RIPS (servicio y API). Su pantalla sigue en el menú de RIPS: se mueve
  en la entrega 2.
- **Pruebas:** `PresupuestoTest` (totales, PDF, firma y formato de pesos) y `RolTest`. E2E contra `galeno_e2e`: 26 de
  26 casos (estados, validaciones, adicionales, PDF, cierre). Los permisos por rol no se probaron en vivo (los usuarios
  semilla de médico 2 y secretaría piden MFA).
