# Diseño: generación de RIPS (y los CUPS en la atención)

Estado: **fase 1 construida** (4 de noviembre de 2026; ver "Cómo quedó la fase 1" al final). Norma vigente verificada:
Res. 948 de 2026, con su Documento Técnico 1 versión 003.

## Problema

Los consultorios objetivo facturan sobre todo a **EPS y medicina prepagada**. Con la **Res. 948 de 2026** (ver "Norma
vigente"), cada factura electrónica de salud va con sus **RIPS en JSON**. El validador de MinSalud (MUV) revisa los
dos y devuelve el **CUV** (código único de validación); sin el CUV, el pagador no puede recibir la factura y el
consultorio no cobra.

Hoy Galenum solo **importa** RIPS. Para que un consultorio pueda trabajar solo con Galenum, tiene que **generarlos**.

## Norma vigente (verificado el 4 de noviembre de 2026)

**Resolución 948 de 2026** del Ministerio de Salud, expedida el 14 de mayo de 2026 (Diario Oficial 53495). Se leyó
en el texto que publica el régimen legal de la Alcaldía de Bogotá; el PDF del Ministerio es un escaneo sin texto.

- **Deroga** las resoluciones 2275 de 2023, 558 de 2024 y 1884 de 2024 (art. 23). Es la única norma del RIPS como
  soporte de la factura electrónica de venta (FEV) en salud.
- **Campos y reglas:** ya no van en la resolución. Están en el **Documento Técnico 1** (campos y reglas de validación
  del RIPS) y el **Documento Técnico 2** (campos de salud adicionales en la FEV), publicados en el micrositio de
  facturación electrónica de SISPRO (art. 10). Cada actualización sale con versión, fecha, control de cambios y fecha
  de aplicación. **Antes de construir hay que descargar la versión vigente del Documento Técnico 1** y comparar los
  campos con los de este documento (que usa los nombres que ya lee `RipsParser`).
- **Fechas:** rige desde su expedición. Desde el 1 de junio de 2026 varias reglas pasaron de aviso a **rechazo**, y
  desde el 1 de julio de 2026 son exigibles los ajustes estructurales de software (parágrafo transitorio del art. 23).
- **Particulares:** el art. 8 pide el RIPS de **todas las atenciones** que soportan una FEV, sin distinguir pagador, y
  solo excluye casos puntuales (art. 2, par. 1: cirugía estética y medicina alternativa sin recursos públicos, entre
  otros). La norma no lo dice de forma expresa, pero parece que también aplica a lo que paga un particular.
  **Confirmarlo en la revisión legal**: si es así, el módulo sigue siendo opcional, pero casi todos lo necesitarán.
- **Envío al MUV (art. 17):** dos soluciones que el facturador instala en su propia infraestructura: tipo A,
  cliente/servidor (transmite archivos), y tipo B, una **API REST en contenedores Docker**, para quien integra su
  propio software. Exige registro en SISPRO (art. 18). Para la fase 3, Galenum tendría que operar la API tipo B por
  cuenta de cada consultorio, con sus credenciales.
- **Campos nuevos de la FEV (arts. 11 y 12, Documento Técnico 2):**
  - Código del prestador de 10 dígitos, modalidad de pago, cobertura o plan, número de póliza.
  - **CUCON**: el código único del contrato con el pagador, que genera el SIIFA (64 caracteres). Va en los pagadores
    del consultorio.
  - Copago, cuota moderadora, pagos compartidos y anticipos, y el período facturado.
  - Le tocan a quien emite la factura. En la fase 2 la emite el facturador del consultorio, pero conviene guardar el
    CUCON y la modalidad en cada pagador desde ya.
- **Plazo:** el prestador radica ante el pagador dentro de los 22 días hábiles siguientes a la expedición de la factura
  validada por la DIAN (art. 15).
- **CIE-11:** no aparece en el texto de la resolución.

Esto también afecta a la importación: `RipsParser` se escribió con la 2275 y hay que revisarlo contra el Documento
Técnico 1 vigente.

### Documentos técnicos vigentes (descargados el 4 de noviembre de 2026)

En el micrositio FEV RIPS de SISPRO, sección "Componentes FEV RIPS", bloque **Normatividad** (no en "Documentación
técnica"):

- [Documento Técnico 1, versión 003 del 15 de julio de 2026](https://www.minsalud.gov.co/sites/rid/Lists/BibliotecaDigital/RIDE/DE/OT/doc-tec1-tecnicas-datos-validacion-rips-fev-salud.pdf)
  (153 páginas): campos y reglas de validación del RIPS.
- [Documento Técnico 2, versión 001 del 15 de julio de 2026](https://www.minsalud.gov.co/sites/rid/Lists/BibliotecaDigital/RIDE/DE/OT/doc-tec2-campos-datos-sector-salud-generacion-fev.pdf)
  (59 páginas): campos de salud en la factura electrónica.
- [Ejemplos oficiales en JSON](https://www.minsalud.gov.co/sites/rid/Lists/BibliotecaDigital/RIDE/DE/OT/ejemplificaciones-estructura-resolucion-948-de-2026.zip)
  (consultas, procedimientos, urgencias, hospitalización, medicamentos, otros servicios, factura sin contrato).

**Lo que cambia frente a lo que ya conocíamos** (según los ejemplos y el Documento Técnico 1):

- **Usuario:** campo nuevo `registroSIRAS`, solo para víctimas de accidentes de tránsito (SOAT); en lo demás va nulo.
- **Consultas y procedimientos:**
  - Por cada diagnóstico CIE-10 hay ahora un par para **CIE-11** (`codDiagnosticoPrincipalCIE11` y
    `nomCodDiagnosticoPrincipalCIE11`, y lo mismo en los relacionados y la complicación). Hoy son opcionales (longitud
    0 a 256): se envían nulos mientras la CIE-11 no sea obligatoria.
  - Campo nuevo **`codigoVIDA`**: el identificador único de la atención en el sistema de interoperabilidad de historia
    clínica electrónica (IHCE). Hoy va nulo, pero "será obligatorio una vez entre en operación". Galenum, como historia
    clínica, tendrá que integrarse con la IHCE cuando el Ministerio la ponga en marcha: queda anotado en el plan.
  - `codPrestador` tiene **12 caracteres** (el código de habilitación con la sede) y debe corresponder al NIT del
    consultorio (`numDocumentoIdObligado`).
  - `codServicio` es numérico (p. ej. `130`); los demás códigos van como texto.
  - Cuota moderadora o copago: `conceptoRecaudo`, `valorPagoModerador` y `numFEVPagoModerador` (la factura con la que
    se cobró el pago moderador, si la hubo).
- **Listas de valores** (tipo de usuario, finalidad, causa, modalidad, grupo de servicios, concepto de recaudo…): se
  consultan en las tablas de referencia de web.sispro.gov.co. En la fase 2 hay que cargarlas como catálogos, como ya
  se hizo con CUPS y CIE-10.

## Qué tenemos y qué falta

| Dato del RIPS | Hoy | Falta |
|---|---|---|
| Catálogos CUPS y CIE-10 | Cargados (V5 y V7), con buscador de CIE-10 | Buscador de CUPS en la pantalla |
| Diagnósticos de la atención | Sí (CIE-10, principal y tipo) | — |
| Consulta o procedimiento realizado (`codConsulta`, `codProcedimiento`) | No | **Procedimientos realizados** en la atención |
| Valor del servicio, cuota moderadora o copago | No | En cada procedimiento realizado |
| Finalidad, causa externa, vía de ingreso, modalidad, grupo de servicio | No | Valores por defecto del consultorio, editables |
| Número de autorización de la EPS | No | En la atención (si el pagador la exige) |
| Paciente: documento, nacimiento, sexo | Sí | — |
| Paciente: tipo de usuario, país, municipio y zona de residencia | No | Campos nuevos del paciente |
| Paciente: EPS o prepagada | No | **Pagador** del paciente, y de cada atención |
| Consultorio: NIT (`numDocumentoIdObligado`) | Sí | — |
| Consultorio: código de habilitación REPS (`codPrestador`) y servicios habilitados (`codServicio`) | No | En los datos del consultorio |
| Profesional: tipo y número de documento | No | En el perfil del médico |
| Número de la factura electrónica (`numFactura`) | No | Lo escribe quien factura (fase 2) o sale del facturador (fase 4) |

## Conceptos

| Concepto | Qué es |
|---|---|
| **Pagador** | EPS, prepagada, ARL, SOAT o "Particular". Catálogo por consultorio, con su NIT y código. |
| **Procedimiento realizado** | Lo que se hizo en la atención, con su CUPS: cantidad, valor, diente o superficie si aplica, y si es consulta o procedimiento. Es la pieza que también usará el **plan de tratamiento**. |
| **Lote de RIPS** | Las atenciones firmadas de un pagador que van en una factura, con el número de esa factura. Es lo que se genera, se valida y se descarga. |
| **Módulo** | Una función opcional que el consultorio activa o no. "RIPS y facturación" es el primero. |

## Un módulo opcional por consultorio (decidido el 4 de noviembre de 2026)

No todos los consultorios facturan a EPS: los de solo particulares no deben ver nada de esto. Mismo principio que los
formatos clínicos: **nadie ve lo que no usa**.

- **Módulos por consultorio:** tabla `modulos_consultorio` (la fila existe = activo), como `formatos_consultorio`. El
  administrador lo activa en la configuración del consultorio. Viene incluido en todos los planes (decisión 6), así
  que la activación no depende de la suscripción.
- **Paquete propio** (`com.galeno.rips`), con sus tablas (pagadores, tarifas, lotes, resultados del MUV), sus pantallas
  y su opción en el menú. Lee las atenciones con una interfaz pública de la historia, como el motor de formatos: la
  historia no sabe que RIPS existe.
- **Desactivado, no cambia nada.** Quedan ocultos la opción del menú, los campos de RIPS de la ficha del paciente, los
  datos de RIPS del consultorio y del profesional, y las pantallas de lotes. El servidor también rechaza sus servicios
  (no basta con ocultar el botón).
- **Desactivarlo no borra nada:** los lotes generados se siguen consultando; solo no se crean nuevos.

### Límite: los procedimientos realizados van en el núcleo clínico

Los procedimientos (CUPS) no son solo de facturación: también los usará el **plan de tratamiento** y le sirven a un
consultorio de particulares para saber qué hizo y cuánto cobró. Por eso:

- El bloque "Procedimientos realizados" es parte de la **historia clínica** (tabla `atencion_procedimientos`), no del
  módulo, y queda firmado con la atención.
- **Solo se muestra cuando algo lo usa:** el módulo de RIPS o, más adelante, el plan de tratamiento. Sin ninguno de los
  dos, la atención se ve como hoy.
- El módulo de RIPS agrega solo lo de facturación: pagadores, tarifas, datos del RIPS y lotes.

## Fases

1. **Módulos y procedimientos realizados en la atención.**
   - Tabla de módulos por consultorio y pantalla del administrador para activarlos.
   - Bloque "Procedimientos realizados" (visible con el módulo activo) con buscador de CUPS (como el de CIE-10),
     cantidad y valor. En odontología, el diente y la superficie.
   - Atajos: la consulta del día se sugiere sola (primera vez o control, según la especialidad del médico). Un
     hallazgo del odontograma ("resina en 36 O") puede sugerir el CUPS.
   - Lista de precios del consultorio por CUPS, para no escribir el valor cada vez.
   - Al firmar, los procedimientos quedan inmutables como el resto de la atención.
2. **Datos para RIPS y generación del JSON.**
   - Paciente: tipo de usuario, país, municipio, zona y pagador (con número de afiliación).
   - Consultorio: código REPS, servicios habilitados, valores por defecto (finalidad, causa, modalidad).
   - Profesional: tipo y número de documento.
   - Pagadores del consultorio, con tarifas propias por CUPS si el convenio las tiene.
   - **Lote de RIPS:** se elige el pagador y el período, se revisan las atenciones (con avisos de lo que falta, como
     en la importación), se escribe el número de la factura emitida en el facturador del consultorio y se descarga el
     JSON. Antes de descargar, Galenum lo valida con las mismas reglas del MUV que se puedan revisar sin el Ministerio.
   - Las atenciones de un lote quedan marcadas para no facturarlas dos veces. Notas crédito y débito: con el tipo y
     número de nota.
3. **Envío al MUV** (en espera, ver la decisión 5). Con las credenciales del consultorio ante el Ministerio: enviar el
   lote, guardar el CUV o los rechazos y mostrarlos por atención. Hasta entonces, el botón aparece deshabilitado.
4. **Factura electrónica desde Galenum** (opcional). Integración con un proveedor tecnológico autorizado por la DIAN.
   Mientras tanto, el consultorio sigue con su facturador y Galenum le da el RIPS listo.

Cada fase se despliega sola. Para un consultorio sin el módulo activo, ninguna cambia nada; la 2 ya resuelve el
problema principal.

## Permisos

Todo lo de esta tabla exige, además, que el módulo esté activo en el consultorio.

| Acción | Quién |
|---|---|
| Activar o desactivar módulos | ADMIN |
| Registrar procedimientos en la atención | El médico autor, mientras es borrador |
| Datos de RIPS del consultorio, pagadores y lista de precios | ADMIN |
| Generar, descargar y enviar lotes de RIPS | Permiso nuevo `RIPS_GESTIONAR`: ADMIN y, si el consultorio lo asigna, un rol de facturación |
| Datos de RIPS del paciente (tipo de usuario, pagador) | Quien hoy edita pacientes (también la secretaria) |
| Cuota moderadora o copago en la cita | Quien gestiona la agenda (también la secretaria) |

Los lotes contienen datos clínicos (diagnósticos), así que su consulta queda en la auditoría, como la historia.

## Decisiones (4 de noviembre de 2026)

1. **Procedimientos:** los registra **el médico en cada atención**, con la consulta del día sugerida. Quedan firmados
   con ella.
2. **Precios:** una **lista de precios por CUPS** desde la fase 1. Las tarifas por pagador (convenios), en la fase 2.
3. **Sedes:** **una por consultorio** (un código REPS).
4. **Cuota moderadora y copago:** los registra **la secretaria antes de la atención** (al recibir al paciente) **o
   quien atiende, dentro de la atención**.
   - Como la secretaria no ve datos clínicos, el cobro no puede vivir en la historia: va en la **cita** (admisión),
     que ella ya gestiona. La atención que nace de esa cita lo toma de ahí, y el lote de RIPS lo lee de la atención.
   - Si la atención no viene de una cita, o el valor cambió, se registra o corrige dentro de la atención.
5. **Envío al MUV:** por ahora el RIPS se **genera como archivo descargable**. En el lote queda un botón **"Enviar al
   Ministerio" deshabilitado**, con un aviso de que viene después; la integración se evalúa más adelante. La fase 3
   queda en espera.
6. **Precio:** el módulo viene **incluido en todos los planes**; el consultorio solo lo activa.

## Antes de construir

- Cargar las listas de valores de SISPRO como catálogos (fase 2) y revisar `RipsParser` contra el Documento Técnico 1
  versión 003.
- Confirmar en la revisión legal si el RIPS es obligatorio para lo que paga un particular.

## Cómo quedó la fase 1

- **Módulos:** `com.galeno.modulos` (tabla `modulos_consultorio`, V21). `ModulosService.exigir(Modulo.RIPS)` responde 404
  si el módulo no está activo. Cualquier miembro lee `GET /api/v1/modulos` (la pantalla decide qué mostrar) y solo
  `MODULOS_GESTIONAR` (ADMIN) los activa.
- **Procedimientos:** en la historia clínica (`Atencion.procedimientos`, tabla `atencion_procedimientos`), con CUPS
  validado contra el catálogo (la descripción sale del catálogo), cantidad de 1 a 99, valor, diente FDI y superficies
  V L M D O. Viajan en el mismo `PUT` de la atención. Si el campo no viene, no cambian; si viene y el módulo está
  apagado, el servidor responde 404. Al firmar quedan inmutables.
- **Consulta sugerida:** al crear la atención con el módulo activo, Galenum agrega la consulta: 8902xx (primera vez, si
  el paciente no tiene atenciones firmadas) o 8903xx (control), con sufijo 01 medicina general, 03 odontología o 02
  otras especialidades, según el perfil del médico. Lleva el precio de la lista.
- **Catálogo CUPS:** pasó de `importacion` a `historia` (como el CIE-10). Lo busca quien ve la historia y quien tiene
  `RIPS_GESTIONAR` (no es dato clínico).
- **Lista de precios:** `com.galeno.rips` (tabla `precios_cups`), con `RIPS_GESTIONAR`. La historia la consulta con la
  interfaz `ListaPrecios`, que implementa el módulo de RIPS: la historia no depende de RIPS.
- **PDF:** la atención muestra "Procedimientos realizados" con CUPS, cantidad y diente, **sin valores** (la historia
  clínica es registro clínico, no de cobro).
- **Permisos nuevos:** `MODULOS_GESTIONAR` y `RIPS_GESTIONAR`, los dos del administrador. Van en el token: quien tenga
  la sesión abierta antes del despliegue ve los menús nuevos después de volver a ingresar.

## Fase 2, entrega 2: pagadores y cobro en la cita (diseño, 4 de noviembre de 2026)

### Decisiones

1. **Aseguradoras:** se precarga el catálogo oficial de SISPRO (código y NIT de cada entidad) para elegirlas de una
   lista. Trae EPS, prepagadas, ARL y aseguradoras SOAT; lo que no esté se crea a mano.
2. **Tarifas por convenio:** sí, opcionales. Si el pagador no tiene tarifa para un CUPS, se usa la lista general.
3. **Pagador:** cada paciente tiene un pagador principal; cada cita lo hereda y se puede cambiar.
4. **En la cita:** concepto del cobro, valor y número de autorización. Quien atiende puede corregirlos en la atención.

### Qué dice la norma

- **El JSON del RIPS no lleva el pagador** (va en la factura). Por cada consulta o procedimiento lleva:
  - `numAutorizacion`: de 0 a 30 caracteres; nulo si el servicio no requiere autorización.
  - `conceptoRecaudo` (tabla `conceptoRecaudo`, ya cargada): 01 copago, 02 cuota moderadora, 03 pagos compartidos en
    planes voluntarios, 04 anticipo y 05 no aplica. El 04 solo va en la factura, nunca en el RIPS.
  - `valorPagoModerador`: 0 si no aplica; al menos 1 si es cuota moderadora o bono de plan voluntario.
  - `numFEVPagoModerador`: la factura con la que se cobró el pago moderador, si la hubo.
  - La cuota moderadora solo se cobra a afiliados del régimen contributivo; al subsidiado no se le reportan pagos de
    planes voluntarios.
- **La factura de salud (Documento Técnico 2)** lleva código del prestador, modalidad de pago, cobertura o plan de
  beneficios, número de contrato (**CUCON**) o de póliza, "factura sin contrato" cuando no hay contrato, los pagos
  moderadores recibidos y el período facturado. Los escribe el facturador del consultorio; Galenum los guarda en el
  pagador para entregárselos y, en la entrega 3, para comparar el lote con el XML de la factura.
- **Validaciones cruzadas** (Documento Técnico 1, numeral 1.8): qué conceptos de recaudo admite cada combinación de
  cobertura, modalidad de pago y tipo de usuario. Se transcriben del PDF (páginas 8 y 9) en la entrega 3; el texto
  extraído pierde las columnas.

### Facturación: sistema externo

Galenum **no emite la factura electrónica**: la emite un sistema autorizado por la DIAN (proveedor tecnológico o
software propio del consultorio) que soporte la **factura del sector salud**. El MUV recibe el RIPS y el XML de esa
factura juntos y los cruza (número, NIT, valores, servicios). Por eso, en la entrega 3, el lote permitirá **cargar el
XML** de la factura para validar los totales antes de descargar. Emitir la factura desde Galenum sigue siendo la fase 4
opcional (integración con un proveedor, con costo por documento).

### Catálogo de aseguradoras

- Tabla `CodigoEAPByNit` de SISPRO (1.717 entidades, 1.401 habilitadas): código, nombre, NIT con dígito de
  verificación, régimen y tipo. Se carga en `rips_referencias` (tabla `eapb`) con una migración nueva, igual que las
  demás: sin costo en producción, porque la búsqueda va a la base de Galenum.
- Tipos que trae (noviembre de 2026): EPS del régimen contributivo (47) y subsidiado (48), medicina prepagada (25), ARL
  (20), aseguradoras en general, donde están las de SOAT (38), regímenes especiales (64), cajas de compensación (79),
  empresas solidarias (220) y las secretarías de salud de departamentos (34) y municipios (1.141).
- El buscador muestra primero EPS, prepagadas, ARL y aseguradoras, y solo las habilitadas (las liquidadas siguen en la
  tabla para las atenciones viejas). El tipo de la entidad sugiere el tipo de pagador.
- Ojo al actualizar: la consulta responde `CoberturaPlanBeneficios` con mayúscula inicial; el script debe aceptar el
  nombre sin importar mayúsculas.
- Se cargan también `coberturaPlanBeneficios` (01 plan de beneficios con UPC, 02 presupuesto máximo, 03 prima EPS o no
  asegurados SOAT, 04 póliza SOAT, 05 ARL, 06 ADRES…) y `facturaSinContrato`.

### Pagadores del consultorio (`rips_pagadores`)

| Campo | Para qué |
|---|---|
| Nombre y tipo | EPS, prepagada o plan voluntario, ARL, SOAT, entidad territorial o ADRES, particular |
| Código de la entidad y NIT | Del catálogo (o a mano si no está) |
| Cobertura o plan de beneficios | Para la factura y las validaciones cruzadas |
| Modalidad de pago | Evento, capitación, global prospectivo, paquete |
| CUCON (hasta 64 caracteres) o número de póliza | Para la factura |
| Factura sin contrato | Código de `facturaSinContrato` cuando no hay contrato |
| ¿Exige autorización? | Si es sí, la cita avisa cuando falta el número |
| Activo | Se desactiva en vez de borrar: las atenciones viejas lo siguen nombrando |

- El pagador **"Particular"** existe siempre (se crea solo al activar el módulo): concepto 05 y valor moderador 0.
- Pestaña **Pagadores** en el menú RIPS, con `RIPS_GESTIONAR`.

### Tarifas por convenio (`rips_tarifas`)

- Pagador, CUPS y valor. Se editan dentro de cada pagador, con el mismo buscador de CUPS de la lista de precios.
- El precio de un procedimiento es la tarifa del pagador de la atención; si no la tiene, el de la lista general. La
  historia lo sigue pidiendo por la interfaz `ListaPrecios`, que ahora recibe la atención; el módulo busca su pagador.

### Pagador del paciente

- `rips_pacientes` gana el pagador principal. Se elige en la tarjeta "Datos para RIPS", que lo avisa si falta.
- Al elegirlo, se **sugiere el tipo de usuario**: EPS contributiva → 01, subsidiada → 04, particular → 12. Se puede
  cambiar.

### Cobro de la cita (`rips_cobros`)

- Una fila por cita, del módulo y no de la agenda (la agenda no sabe que RIPS existe): pagador (el del paciente por
  defecto), número de autorización, concepto de recaudo, valor y factura del pago moderador (opcional).
- **Validaciones:**
  - El concepto 04 (anticipo) no se acepta: no va en el RIPS.
  - El 02 (cuota moderadora) solo con tipo de usuario contributivo (01, 02 o 03) y valor de al menos 1.
  - El 05 (no aplica) lleva valor 0.
- **En la agenda:** botón "Cobro" en cada cita abierta (con el módulo activo). La etiqueta de la cita avisa si el
  pagador exige autorización y falta el número.
- **En la atención:** tarjeta "Pagador y cobro" con lo de la cita. Si la atención no viene de una cita, se registra
  ahí. Como el cobro no es dato clínico, se puede corregir aun con la atención firmada, **hasta que entre en un lote**.
- **Permisos:** en la cita, quien gestiona la agenda (también la secretaria); en la atención, quien la registra.

### Qué no entra

El lote, el JSON y sus validaciones (entrega 3), la emisión de la factura y el envío al MUV.

## Cómo quedó la fase 2, entrega 1 (datos para el RIPS)

- **Tablas de referencia de SISPRO** en `rips_referencias` (V22): tipo de usuario, país, departamento, municipio,
  zona, finalidad, causa externa, modalidad de atención, grupo de servicios, servicio, vía de ingreso, concepto de
  recaudo, modalidad de pago y tipo de diagnóstico (1.683 filas). Se toman de la consulta pública de SISPRO
  (`ConsultarDetalleReferenciaBasica.aspx`) pasando sus páginas, porque "Exportar a Excel" exige sesión.
  - Para actualizarlas: `node tools/rips/descargar-tablas.mjs <carpeta> <tablas…>`, luego
    `node tools/rips/generar-migracion.mjs <carpeta> <archivo.sql>`, y publicar el resultado como una migración nueva.
  - Las columnas "Extra" quedan en JSON. Por ejemplo, el departamento del municipio, o si una finalidad aplica a
    consultas o procedimientos (con sus límites de sexo y edad, para validar en la entrega 3).
  - La fuente trae algunos caracteres perdidos ("tr nsito", "?" por un guion largo); el generador los corrige.
- **Paciente** (`rips_pacientes`, V23):
  - Tipo de usuario, país, municipio y zona de residencia, y país de origen. Fuera de Colombia, municipio y zona no
    aplican.
  - Se edita en la tarjeta "Datos para RIPS" de la ficha, que también ve la secretaria, y avisa lo que falta.
  - En la agenda, cada cita abierta con datos pendientes muestra "RIPS: faltan N"; al hacer clic se completan ahí
    mismo. Los faltantes de todas las citas del día salen en una sola consulta (`GET /rips/pacientes/faltantes`,
    hasta 200 pacientes). No hace falta llenar a todos los pacientes al activar el módulo: solo a los que se atienden.
  - El sexo y el tipo de documento ya coinciden con los de SISPRO (M, F, I y TipoIdPISIS).
- **Prestador** (`rips_prestador`): código de habilitación de 12 dígitos, servicio habilitado y valores por defecto
  (modalidad, grupo, finalidad y causa que apliquen a consultas, y vía de ingreso). Sin configurar, sugiere
  intramural, consulta externa, enfermedad general y "derivado de consulta externa".
- **Profesionales** (`rips_profesionales`): tipo y número de documento de cada médico activo, por consultorio.
- **Pantalla:** el menú RIPS (antes "Precios"), con las pestañas Prestador, Profesionales y Precios; `/precios` lleva a
  la pestaña Precios.
- **¿Particulares también?** La Res. 948 (art. 2) incluye a los profesionales independientes, y el art. 8 pide
  RIPS de "todas las atenciones" como soporte de la factura electrónica de salud, sin excluir por pagador. No menciona a
  los particulares ni el pago directo del usuario, así que la lectura más probable es que también lleven RIPS (tipo de
  usuario 12), pero no está explícito. Las excepciones escritas (art. 2, parágrafo 1) son centros de reconocimiento de
  conductores, centros de investigación, y estética y medicina alternativa sin recursos públicos. Pendiente confirmarlo
  con la mesa de ayuda del Ministerio antes de activar el módulo por defecto.
- **Permisos:**
  - Las listas de SISPRO, cualquier miembro.
  - Los datos del paciente, `PACIENTE_GESTIONAR`.
  - El prestador y los profesionales, `RIPS_GESTIONAR`.
  - Todo exige el módulo activo.

## Cómo quedó la fase 2, entrega 2 (pagadores y cobro)

- **Catálogos** (V24, generada con `tools/rips/generar-migracion.mjs … V24 …`): `eapb` (1.717 entidades de
  `CodigoEAPByNit`, con régimen, NIT, población y tipo; sin los correos), `cobertura` (15) y `factura_sin_contrato` (7).
  El generador ahora agrupa las tablas por migración; la descarga acepta el nombre de la tabla sin importar mayúsculas.
  - `GET /rips/referencias/eapb?q=` busca por código o nombre y ordena EPS, prepagadas, ARL, aseguradoras y regímenes
    especiales antes que las secretarías de salud. Sin texto no lista nada (son 1.700).
- **Pagadores** (`rips_pagadores`, V25): pestaña **Pagadores** del menú RIPS. Al elegir la entidad del catálogo se
  llenan nombre, NIT, código, tipo y la cobertura más común. El **particular** se crea solo la primera vez que se
  consultan los pagadores (cobertura 15, factura sin contrato 07, por evento); no se desactiva ni cambia de tipo. Los
  demás se desactivan en vez de borrarse.
- **Tarifas** (`rips_tarifas`): en cada pagador. `ListaPrecios.precios(codigos, contexto)` recibe paciente, cita y
  atención: el módulo busca el pagador (el de la atención, si no el de la cita, si no el del paciente) y sus tarifas
  reemplazan la lista general. Así la consulta sugerida y el buscador de CUPS de la atención
  (`GET /historia/cups?q=&atencionId=`) traen el precio del convenio. Cambiar de pagador no cambia los valores ya
  registrados en la atención.
- **Pagador del paciente:** `rips_pacientes.pagador_id`; la tarjeta "Datos para RIPS" lo pide (cuenta en los
  faltantes) y sugiere el tipo de usuario: EPS contributiva 01, subsidiada 04, particular 12, ARL 09, SOAT 10,
  prepagada 11.
- **Cobro** (`rips_cobros`): una fila por cita o por atención (lo garantiza `CobrosService`; MySQL no admite el CHECK
  con el borrado en cascada de la atención).
  - Agenda: cada cita abierta muestra pagador y cobro, la etiqueta "Falta autorización" si el pagador la exige, y el
    botón **Cobro**. Los cobros del día salen en una consulta (`GET /rips/cobros/citas?ids=`). Con la cita ya
    atendida o cancelada, responde 409: se corrige en la atención.
  - Atención: tarjeta **Pagador y cobro** (también en la atención firmada, para quien la registra), con el origen: de
    la cita, de la atención o el pagador del paciente sin registrar.
  - Reglas: sin anticipo (04); "no aplica" (05) con valor 0; copago, cuota moderadora o pago compartido con al menos
    $1 (si no se cobró, es "no aplica": decisión de Galenum, más estricta que el documento técnico); el particular
    solo "no aplica"; cuota moderadora solo a contributivos (01, 02, 03); pagos compartidos nunca a subsidiados (04).
    Autorización hasta 30 caracteres y factura del pago moderador hasta 20 (letras, números y guiones).
- **Permisos:** la lista de pagadores, cualquier miembro (la usan la cita y la ficha); crearlos y sus tarifas,
  `RIPS_GESTIONAR`; el cobro de la cita, `AGENDA_GESTIONAR`; el de la atención, `HISTORIA_VER` para verlo y
  `HISTORIA_REGISTRAR` para cambiarlo. Todo exige el módulo activo.
- **Interfaces nuevas de otros módulos:** `HistoriaService.referencia(atencionId)` (paciente, cita y estado, sin
  contenido clínico ni auditoría) y `AgendaPublica.citas(ids)`.
