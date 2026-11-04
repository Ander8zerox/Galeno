# Diseño: generación de RIPS (y los CUPS en la atención)

Estado: **decisiones tomadas** (4 de noviembre de 2026). Norma vigente verificada: Res. 948 de 2026. Antes de construir
falta descargar su Documento Técnico 1.

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

- Descargar la versión vigente del Documento Técnico 1 (y del 2) del micrositio de SISPRO y ajustar los campos.
- Confirmar en la revisión legal si el RIPS es obligatorio para lo que paga un particular.
