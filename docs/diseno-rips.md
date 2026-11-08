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

Esto también afecta a la importación: `RipsParser` se escribió con la 2275 y había que revisarlo contra el Documento
Técnico 1 vigente (hecho: ver "Importación revisada contra el DT1 v003" al final).

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

## Fase 2, entrega 3: lote y JSON (diseño, 5 de noviembre de 2026)

Genera el archivo del RIPS de una factura, validado con lo que Galenum puede revisar sin el Ministerio. El envío al
MUV sigue en espera (botón deshabilitado).

### Qué dice la norma (Documento Técnico 1 v003 y ejemplos oficiales)

- **Estructura:** `numDocumentoIdObligado` (NIT), `numFactura`, `tipoNota`, `numNota` y `usuarios`. Cada usuario lleva
  sus datos (U01 a U12) y `servicios` con `consultas` y `procedimientos` (urgencias, hospitalización, recién nacidos,
  medicamentos y otros servicios no aplican a un consultorio).
- **Consulta** (C01 a C30): prestador (12 caracteres), fecha y hora (`AAAA-MM-DD HH:MM`), autorización, CUPS, modalidad,
  grupo, servicio (numérico), finalidad, causa, diagnóstico principal y hasta 3 relacionados (CIE-10, con los pares
  CIE-11 nulos), tipo de diagnóstico principal, documento del profesional, valor, concepto y valor del pago moderador,
  factura del pago moderador, `codigoVIDA` (nulo) y consecutivo.
- **Procedimiento** (P01 a P27): parecido, con `idMIPRES`, vía de ingreso, un diagnóstico relacionado y la complicación.
  No tiene cantidad: un CUPS con cantidad 2 son dos registros.
- **Usuario:** `incapacidad` es `SI`/`NO` (tabla `LstSiNo`); `registroSIRAS`, obligatorio con tipo de usuario 10 o 14.
- **Consecutivos:** de usuarios y de cada lista de servicios, desde 1 y sin repetir.
- **Tipo de nota** (`TipoNota`): NC nota crédito, ND nota débito, NA nota ajuste RIPS y **RS RIPS sin factura**
  (profesional independiente no obligado a facturar, RVC091).
- **97 reglas de validación** (RVC001 a RVC098). Las que Galenum puede revisar por su cuenta:
  - Fechas: nacimiento y servicio no futuros (RVC006, RVC013) ni el servicio antes del nacimiento (RVC079).
  - Edad y tipo de documento (RVC007): RC hasta 7 años, TI de 7 a 18, CC desde 18, con un año de tolerancia.
  - CUPS: que exista en `CUPSRips` (RVC096), que sea de consulta o de procedimiento (RVC015, RVC020), que aplique al sexo
    (RVC016), y que no se repita el mismo día para el mismo paciente (RVC018).
  - CIE-10: sexo y edad (RVC028); los relacionados no repiten el principal ni entre sí (RVC086, RVC087); el principal de
    un procedimiento no es de síntomas (R00-R99) ni de factores Z (RVC032, RVC033).
  - Valor del servicio: mayor que 0 si la modalidad de pago es "por evento", 0 en las demás (RVC034, rechazo).
  - Pagos moderadores (RVC035, RVC037, RVC060, RVC061, RVC092): ya los valida el cobro de la entrega 2.
  - SIRAS con SOAT o lesionado sin SOAT (RVC095).
  - Con el XML de la factura: NIT (RVC001), número (RVC004), período (RVC014) y suma de pagos moderadores (RVC036).
  - Validaciones cruzadas del numeral 1.8 (cobertura × modalidad × tipo de usuario × concepto): se transcriben de las
    páginas 8 y 9 del PDF.
  - Las demás dependen de tablas del Ministerio (habilitación, MIPRES, RUAF) o de servicios que Galenum no maneja: las
    revisa el MUV.

### Propuesta

- **Lote** (`rips_lotes` y `rips_lote_atenciones`): las atenciones firmadas de **un pagador** en **un período** que van en
  **una factura**.
  - Se crea eligiendo pagador y fechas; Galenum trae las atenciones firmadas con ese pagador (el del cobro de la
    atención, de su cita o del paciente) que no estén en otro lote, y se pueden quitar las que no van.
  - **Revisión:** por atención, lo que falta o no pasa una regla, con enlace para corregirlo (datos del paciente, del
    prestador, del profesional, el cobro). Los avisos que el MUV trata como notificación no bloquean; los de rechazo
    sí.
  - Se escribe el **número de la factura** emitida en el facturador del consultorio y se **genera**: el JSON queda
    guardado tal cual (no cambia si después se corrige un dato) y se descarga como `RIPS_<factura>.json`.
  - Estados: **borrador** (se edita), **generado** (atenciones y cobros bloqueados) y **anulado** (con motivo; libera las
    atenciones para otro lote).
  - Botón **"Enviar al Ministerio"** deshabilitado, con el aviso de que viene después.
- **Armado:** de cada atención salen una consulta (los CUPS de consulta) y los procedimientos (uno por unidad).
  - Finalidad, causa, modalidad, grupo, servicio y vía de ingreso: los valores por defecto del prestador, que se
    pueden cambiar por atención en la revisión.
  - Diagnósticos: el principal y hasta 3 relacionados de la atención (en el procedimiento, el principal y uno
    relacionado); el tipo sale del tipo del diagnóstico (impresión 01, confirmado nuevo 02, confirmado repetido 03).
  - Profesional: el documento guardado en la pestaña Profesionales.
  - Valores: el de cada procedimiento, o 0 si la modalidad del pagador no es "por evento"; el pago moderador va en
    el primer servicio de la atención.
- **Catálogo `CUPSRips`:** cargar sus columnas (uso consulta o procedimiento, sexo, ámbito, cobertura, máximo por día)
  junto a nuestro catálogo CUPS. La descarga pasa unas mil páginas de la consulta pública, una sola vez.
- **Permisos:** `RIPS_GESTIONAR`. Ver un lote muestra diagnósticos, así que queda en la auditoría como la historia.

### Decisiones (5 de noviembre de 2026)

1. **Servicios:** solo consultas y procedimientos, que es lo que registra un consultorio.
2. **XML de la factura:** opcional. Si se carga, Galenum cruza NIT, número, período y pagos moderadores antes de
   generar; si no, esas reglas quedan para el MUV, que igual exige el XML al enviar.
3. **Incapacidad:** un campo `SI`/`NO` en la atención que marca el médico (por defecto NO), porque es quien la expide.
   El lote la toma de ahí; si un paciente tiene varias atenciones en el lote, va `SI` si alguna la tiene.
4. **RIPS sin factura (RS):** opción del lote para el profesional independiente no obligado a facturar que atiende
   particulares. El lote RS no lleva número de factura ni XML, y el valor de cada servicio es lo que pagó el paciente
   (RVC091), no el 0 de las modalidades que no son "por evento".
5. **Notas crédito, débito y ajuste:** para una entrega posterior. Mientras tanto, un lote generado se anula (con motivo)
   y se genera otro.

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

## Cómo quedó la fase 2, entrega 3 (lote y JSON)

- **Catálogo CUPSRips** (V26, `tools/rips/generar-migracion.mjs … V26 …`): las 13.640 filas, con lo que revisa el
  lote (uso AC/AP/AT, sexo, ámbito, máximo por día y cobertura). La descarga pide 2.000 filas por página (el máximo de
  la consulta pública): son 7 páginas y no 1.364.
- **Incapacidad** (`atenciones.incapacidad`, V27): casilla "Expidió incapacidad" en el editor de la atención, con el
  módulo activo. Es parte de lo que se firma; sin el módulo llega nula y no cambia.
- **Lotes** (`rips_lotes`, `rips_lote_atenciones`, V27): pestaña **Lotes** del menú RIPS (la primera).
  - Se crea con pagador y fechas: entran las atenciones firmadas en Galenum (no las importadas) cuyo pagador vigente
    (el de la atención, su cita o el paciente) es ese y que no estén en otro lote vivo. `vigente` repite el id de la
    atención mientras el lote no esté anulado y su UNIQUE impide los dos lotes. Hasta un año y 2.000 atenciones.
  - **Revisión** (`ArmadorRips`, sin acceso a la base): cada hallazgo es **rechazo** (impide generar) o **aviso** (lo
    que el MUV solo notifica), con la regla y dónde corregirlo (paciente, prestador, NIT, profesional, pagador,
    precios, atención). Se puede quitar una atención o ajustar para ella finalidad, causa, modalidad, grupo, servicio
    y vía de ingreso.
  - **Reglas propias:** fechas (RVC006, RVC013), edad y documento (RVC007), CUPSRips (RVC096, uso, sexo RVC016, veces
    por día RVC018), CIE-10 por sexo y edad (RVC028), finalidad por sexo y edad (RVC051), principal de procedimiento
    R o Z (RVC032, RVC033), valor por evento (RVC034), SIRAS (RVC095, aviso: Galenum aún no lo registra), las reglas
    del cobro y la tabla del **numeral 1.8** (cobertura × modalidad × tipo de usuario × concepto). Los relacionados
    repetidos (RVC086, RVC087) no se reportan: se quitan al armar.
  - **Numeral 1.8:** el DT1 v003 marca la cobertura 01 como inactiva y la reparte en 16 (contributivo) y 17
    (subsidiado), pero SISPRO todavía publica la 01 y no las nuevas. Mientras tanto la 01 admite lo de las dos.
  - **XML de la factura** (opcional, `FacturaXml`): AttachedDocument de la DIAN o la factura sola, sin DTD ni entidades
    externas. Cruza NIT (RVC001), número (RVC004), período (RVC014) y la suma de `PrepaidPayment` con schemeID 01 a 03
    (RVC036). Si el lote no tiene número, toma el del XML.
  - **Armado:** un usuario por paciente; por cada unidad de un CUPS, una consulta (uso AC; sin CUPSRips, los 890…) o un
    procedimiento. Sin valor en la atención, el precio vigente (tarifa del pagador o lista general). Fuera de pago por
    evento, el valor es 0; en el RIPS sin factura, lo que pagó el paciente (RVC091). El pago moderador va en el primer
    servicio de la atención. `codSexo` es el de la ficha (M, F, I: columna Extra III de la tabla Sexo).
    Con el pagador particular, el tipo de usuario va como 12 aunque la ficha diga otro (un afiliado a una EPS que
    paga de su bolsillo es particular en esa factura; la cobertura 15 solo admite el 12).
  - **Generar** guarda el JSON tal cual (no cambia si después cambia un dato) y bloquea el cobro de esas atenciones
    (409). Se descarga como `RIPS_<factura>.json` o `RIPS_sin_factura_<desde>_<hasta>.json`. **Anular** pide motivo,
    conserva el JSON y libera las atenciones. Un borrador se elimina. "Enviar al Ministerio" queda deshabilitado
    (fase 3).
  - **RIPS sin factura:** solo con el pagador particular; tipo de nota RS, sin número de factura ni XML. El `numNota`
    es el número de envío del consultorio (`rips_lotes.numero_envio`, V29): se asigna al generar (1, 2, 3…), no se
    reutiliza ni de lotes anulados, y si la generación falla no se gasta. La pantalla avisa que es para quien no tiene
    contrato con ningún pagador y que se envía en los 5 primeros días del mes siguiente.
- **NIT del consultorio:** es el `numDocumentoIdObligado` y solo se pedía (opcional) al registrarse. La pestaña
  Prestador lo muestra y, si falta, deja escribirlo una vez (con dígito de verificación y sin repetir el de otro
  consultorio). Ya fijado no se cambia desde la app, porque identifica al consultorio en las pruebas gratuitas.
- **Auditoría:** ver, generar o descargar un lote queda en `accesos_historia` de cada paciente (`VER_RIPS`,
  `GENERAR_RIPS`), porque el lote muestra diagnósticos.
- **Permisos:** `RIPS_GESTIONAR` y el módulo activo. El NIT, también `RIPS_GESTIONAR`.
- **Pendiente:** notas crédito, débito y de ajuste; confirmar con el MUV qué `numNota` lleva un RIPS sin factura
  (hoy va nulo); envío al MUV (fase 3).

## Registro SIRAS (6 de noviembre de 2026)

- Es el radicado de la plataforma SIRAS de la ADRES al reportar la atención de una víctima de accidente de tránsito
  (campo U12). El DT1 lo hace obligatorio con tipo de usuario 10 (SOAT) o 14 (lesionado sin SOAT).
- Va en el cobro (`rips_cobros.registro_siras`, V28), como la autorización: es dato del pagador. El formulario del
  cobro (cita y atención) lo muestra si el pagador es SOAT o el paciente es tipo 10 o 14 (`CobroVista.pideSiras`); si no
  aplica, se descarta al guardar. Hasta 60 letras, números o guiones.
- En el lote: con tipo 10 o 14 y sin SIRAS es **rechazo** (RVC095 es notificación, pero el campo es obligatorio). El RIPS
  lleva uno por usuario: el de la primera atención que lo tenga; si hay distintos, avisa.

## Notas crédito, débito y de ajuste (diseño, 6 de noviembre de 2026)

Corrigen una factura ya radicada: la factura no se cambia ni se anula, se le hace una nota. La nota crédito y la débito
las emite el facturador del consultorio ante la DIAN, igual que la factura; Galenum genera el **JSON del RIPS** de la
nota. La de ajuste RIPS no lleva documento de la DIAN: es solo el RIPS corregido.

### Qué dice la norma

Fuentes: DT1 v003 (campos T03 y T04, y las observaciones del valor del servicio y de los pagos moderadores) y los
*Lineamientos para la generación, validación y envío del RIPS* del Ministerio (v3.2, mayo de 2025, numerales 2.2.2 a
2.2.7 y 4.4.2 a 4.4.3; son de la Res. 2275, pero el DT1 v003 no cambia el manejo de las notas).

- **Transacción:** `numFactura` es el de la factura afectada, `tipoNota` NC, ND o NA, y `numNota` el número de la nota
  (de la DIAN en NC y ND).
- **Qué lleva:** solo los usuarios y servicios **objeto del ajuste**, con su registro completo y **el mismo
  consecutivo** que tenían en el RIPS de la factura.
- **Nota crédito parcial (glosa aceptada):** cada servicio lleva el valor glosado y la suma debe coincidir con el valor
  de la NC (XML). El valor de un servicio no puede pasar el que se reportó en la factura. Los **pagos moderadores van en
  0**.
- **Nota crédito al 100%** (factura devuelta y aceptada): se valida en el MUV **sin RIPS**; los RIPS de la factura
  quedan marcados como afectados. Galenum no genera JSON.
- **Nota débito:** por la diferencia de valor o de cantidad de servicios ya facturados (mismo consecutivo, modificando
  solo el valor o la cantidad), o con servicios que no se facturaron (nuevos registros que siguen la numeración). Para
  esto último la norma también deja expedir una factura nueva.
- **Nota de ajuste RIPS:** glosas por la calidad de los datos que no cambian el valor (diagnóstico, finalidad,
  autorización…). No lleva documento de la DIAN.
- **Glosa de valor y de datos a la vez:** se reporta como **NC** y en el mismo registro se corrigen los datos.
- **RIPS sin factura:** `numFactura` nulo y `numNota` un **número propio del prestador, único para cada envío**. El
  lineamiento lo llama "SF"; la tabla TipoNota del DT1 v003 lo llama "RS". Además, es para quien **no tiene contrato con
  ningún pagador** y se transmite **dentro de los 5 primeros días del mes siguiente**. Corregido con V29 (ver "Cómo quedó la fase 2, entrega 3").

### Propuesta

- **Nota** (`rips_notas`): pertenece a un lote **generado**. Tipo (NC, ND, NA), número, motivo, estado (borrador,
  generada, anulada) y el JSON generado. Un lote puede tener varias notas.
- **Se arma sobre el JSON guardado del lote**, no sobre los datos de hoy: así el consecutivo y los valores son los que
  recibió la EPS. En la pantalla del lote, "Nueva nota" lista los servicios del RIPS (paciente, CUPS, fecha y valor) y se
  marcan los afectados.
  - **NC:** por servicio, el valor glosado aceptado (de $1 al valor reportado, menos lo que ya hayan acreditado otras
    notas). Se pueden corregir datos en el mismo registro (glosa mixta).
  - **ND:** por servicio, el valor que se suma (mayor que 0).
  - **NA:** por servicio, los datos que se corrigen (diagnósticos, finalidad, causa, modalidad, grupo, servicio, vía de
    ingreso, autorización); el valor queda como estaba.
  - Pagos moderadores en 0 en las tres.
- **Revisión:** las mismas reglas del lote que apliquen, y además: la suma de la NC o de la ND no puede ser 0, la NC no
  puede pasar lo facturado y la NA no puede quedar sin cambios.
- **XML de la nota** (opcional, como el de la factura): cruza número, factura referenciada y valor.
- **Descarga:** `RIPS_<factura>_<NC|ND|NA><número>.json`.
- **Generada:** queda guardada tal cual; se anula con motivo. Las notas no desbloquean el lote: los datos de la
  atención siguen como se generaron.

### Decisiones (6 de noviembre de 2026)

1. **Alcance:** NC parcial, ND por mayor valor y NA. La ND con servicios nuevos queda fuera: para eso se expide una
   factura nueva, que la norma también permite.
2. **NC al 100%:** se registra en el lote (número y motivo), sin JSON. El lote queda "afectado al 100%" y sus atenciones
   no se vuelven a facturar.
3. **Glosa mixta:** dentro de la NC se pueden corregir datos del mismo registro, como dicen los lineamientos.
4. **CUPS en la NA:** no se cambia, porque cambiar el procedimiento cambia lo facturado (eso va por NC o ND).
5. **RIPS sin factura:** se corrige **antes** que las notas, en un PR corto. El `numNota` pasa a ser un consecutivo
   propio del consultorio (1, 2, 3…, único por envío), y la pantalla avisa que es para quien no tiene contrato con
   ningún pagador y que se envía en los 5 primeros días del mes siguiente.

## Cómo quedaron las notas (6 de noviembre de 2026)

- **Tabla** `rips_notas` (V30): tipo (NC, ND, NA), si es NC al 100%, número, motivo, estado (borrador, generada,
  anulada), los servicios afectados en JSON (clave, valor y datos corregidos), lo leído del XML y el JSON generado.
- **Pantalla:** en un lote generado (con factura), la tarjeta **Notas** lista las notas y tiene "Nueva nota" (tipo,
  número, motivo; en la NC, "Por el 100% de la factura"). El editor lista los servicios del RIPS del lote (con lo ya
  acreditado o sumado), y en cada uno marcado se escribe el valor (NC y ND) o se corrigen los datos (NA, o NC con
  glosa mixta), con lo que decía antes.
- **Armado** (`ArmadorNota`, sin acceso a la base): se arma sobre el JSON guardado del lote. Van solo los usuarios y
  servicios marcados, con su consecutivo y registro completo; en la NC y la ND, `vrServicio` es el valor de la nota y
  los pagos moderadores van en 0 (concepto 05); la NA deja los valores de la factura. La clave de un servicio es
  "usuario-lista-consecutivo".
- **Reglas:** número de hasta 20 letras, números o guiones y sin repetir en el lote; NC de $1 a lo que queda por
  acreditar (lo facturado menos otras NC generadas); ND de al menos $1 y sin datos; NA con al menos un cambio. Datos
  corregibles: diagnósticos (que existan en CIE-10, sin repetir el principal ni entre sí: RVC086 y RVC087), tipo de
  diagnóstico, finalidad, causa, modalidad, grupo, servicio, vía de ingreso y autorización. El CUPS no.
- **XML de la nota** (opcional, NC y ND): CreditNote o DebitNote, o el AttachedDocument de la DIAN. Cruza el número, la
  factura que referencia (BillingReference) y el valor (PayableAmount de LegalMonetaryTotal o RequestedMonetaryTotal).
- **NC al 100%:** nace generada, sin servicios ni JSON. Después de ella el lote no admite más notas, y no se puede
  registrar si ya hay notas generadas.
- **Generada:** se descarga como `RIPS_<factura>_<tipo><número>.json` y se anula con motivo. Mientras el lote tenga
  notas generadas no se puede anular (409).
- **Auditoría:** ver, generar o descargar una nota queda en la historia de los pacientes afectados (`VER_RIPS`,
  `GENERAR_RIPS`, con el id de la nota).
- **Fuera de alcance:** la ND con servicios nuevos (se expide una factura nueva) y corregir datos del usuario (tipo de
  usuario, residencia) en la NA.

## Importación revisada contra el DT1 v003 (8 de noviembre de 2026)

Se compararon los campos que lee `RipsParser` con el Documento Técnico 1 v003 y los ejemplos oficiales de la 948 (los
de consultas y procedimientos quedaron como recursos de prueba en `backend/src/test/resources/rips/res948`).

- **Diagnóstico de egreso** (`codDiagnosticoPrincipalE`, urgencias y hospitalización): antes no se leía. Ahora es el
  principal (es el definitivo) y el de ingreso queda como relacionado si es distinto.
- **Complicación** (`codComplicacion`, procedimientos y hospitalización) y **causa de muerte**
  (`codDiagnosticoCausaMuerte`): se importan como diagnósticos relacionados.
- **CIE-11** (pares `...CIE11`, opcionales): Galenum usa CIE-10 y los ignora; si un diagnóstico viene solo en CIE-11,
  la revisión del lote lo avisa en vez de perderlo en silencio.
- **`codigoVIDA`** (identificador de la atención en la IHCE): se guarda en la descripción de la atención importada
  ("…factura FE-1, código VIDA DV64588521"), sin migración. Cuando Galenum se integre con la IHCE, se podrá pasar a un
  campo propio.
- Un diagnóstico relacionado ya no pasa a principal cuando falta el principal.
- **Sin cambios:** la estructura (`numDocumentoIdObligado`, `numFactura`, `usuarios`, `servicios`), los nombres de
  los campos que ya se leían, las fechas `AAAA-MM-DD HH:MM` y los RIPS de la 2275 (se siguen leyendo igual).
- **No se importan (como antes):** medicamentos, recién nacidos y otros servicios (se cuentan como omitidos).
- **Pendiente:** los datos del usuario para el RIPS (`codPaisResidencia`, `codZonaTerritorialResidencia`,
  `codPaisOrigen`, `incapacidad`, y el tipo de usuario y municipio que ya se leen) no se guardan en los "Datos para
  RIPS" del paciente. `registroSIRAS` es de cada atención (va en el cobro), no del paciente.
