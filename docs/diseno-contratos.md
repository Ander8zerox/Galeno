# Diseño: contratos con pagadores (fase E)

Escrito el 28 de noviembre de 2026. Sin implementar. Viene del informe de QA ("crear un módulo de contratación"),
entendido como los **contratos del prestador con sus pagadores** (EPS, prepagadas, ARL, SOAT; Decreto 441 de 2022).

## Problema

Hoy los datos del contrato están **en el pagador**: un solo CUCON, una modalidad de pago, una cobertura y una tarifa por
CUPS. En la práctica un consultorio tiene con la misma EPS contratos que se renuevan cada año, a veces dos a la vez
(uno por evento y otro por capitación), con servicios, tarifas, topes y vigencia propios. Sin eso:

- Se atiende con un contrato vencido o un CUPS no contratado, y la glosa llega meses después.
- El lote de RIPS y la factura (Siigo, `contract_number`) no saben qué contrato aplica a cada atención.
- Nadie ve que el tope del contrato se está agotando.

## Qué ya existe (paquete `com.galeno.rips`)

| Hoy | Dónde |
|---|---|
| Pagador: nombre, tipo, código de entidad, NIT, cobertura, modalidad de pago, CUCON, póliza, motivo sin contrato, exige autorización | `rips_pagadores` |
| Tarifa por pagador y CUPS (valor fijo) | `rips_tarifas` |
| Lista de precios general por CUPS | `precios_cups` |
| Cobro de la cita/atención: pagador, autorización, concepto y valor del pago moderador | `rips_cobros` |
| Lote: pagador, período, atenciones | `rips_lotes` |

## Propuesta

### El contrato como entidad (`rips_contratos`)

Pagador, número del contrato, CUCON, vigencia (desde–hasta; hasta puede quedar abierto), modalidad de pago,
cobertura, exige autorización, tope de valor (opcional), estado (Activo, Terminado) y observaciones.

- El **pagador queda solo con su identidad** (nombre, tipo, código, NIT). Lo demás pasa al contrato.
- Un pagador puede tener **varios contratos**, incluso vigentes a la vez (por ejemplo, evento y capitación). Para
  particulares no hay contrato: el motivo de factura sin contrato sigue en el cobro o en la configuración del
  consultorio.

### Servicios contratados (`rips_contrato_servicios`)

Por contrato: CUPS, valor (fijo), exige autorización (si difiere del contrato) y tope de cantidad (opcional).

- **Contrato sin servicios = cubre todo** con la lista de precios general (así se comporta hoy un pagador sin
  tarifas).
- Tarifas por manual (SOAT en UVB o ISS ± %): **fuera de esta fase**; se cargan como valores fijos. Se agregan después
  si un consultorio lo pide, con el manual como tabla de referencia.

### Elegir el contrato

- En el **cobro** de la cita o la atención se guarda el contrato. Por defecto, el contrato vigente del pagador en la
  fecha de la cita que cubra los CUPS; si hay más de uno, se elige.
- El **precio** del procedimiento sale del servicio del contrato y, si no está, de la lista general (`ListaPrecios`
  ya recibe el contexto de la atención).
- El **lote de RIPS** se arma por **contrato** (una factura por contrato y período) y toma de él la modalidad, la
  cobertura y el CUCON. La factura con Siigo usará el mismo número como `contract_number`.

### Avisos

| Dónde | Aviso |
|---|---|
| Agenda, al agendar o en la etiqueta de la cita | Contrato vencido o sin contrato vigente; falta autorización |
| Atención, al registrar un procedimiento | CUPS no contratado; tope de cantidad alcanzado |
| Lote | Atenciones fuera de la vigencia del contrato |
| Pestaña Contratos | Por vencer en 30 días; tope de valor usado (suma de lo facturado en lotes) por encima del 80 % |

Son avisos, no bloqueos: el consultorio decide si atiende igual (por ejemplo, una urgencia).

### Capitación

En capitación la factura es por el valor pactado del período, no por atención: el lote del contrato capitado lleva el
valor del contrato y los RIPS de las atenciones como soporte. Según la Res. 948 de 2026 (por confirmar en el texto
oficial), la primera factura va sin RIPS y desde la segunda se reporta el período anterior. Se diseña en detalle al
implementarla, con un consultorio que tenga un contrato capitado.

## Migración de lo existente

- Por cada pagador con CUCON, modalidad o tarifas: un contrato "Contrato vigente" con esos datos, vigencia desde la
  fecha de creación del pagador y sin fecha de fin. Las tarifas pasan a sus servicios.
- Los cobros existentes se enlazan a ese contrato; los lotes ya generados no cambian (su RIPS está guardado).
- Pagadores sin esos datos quedan sin contrato.

## Permisos

| Acción | Quién |
|---|---|
| Crear y editar contratos y sus servicios | ADMIN |
| Ver contratos y avisos | ADMIN y secretaria |
| Elegir el contrato en el cobro | Quien registra el cobro (como hoy) |

Todo exige el módulo de RIPS activo.

## Decisiones abiertas

1. **Confirmar con QA** que "contratación" son estos contratos con pagadores y no contratación de personal.
2. ✅ Decidido el 28 de noviembre de 2026: el tope de valor se mide contra lo **facturado** (lotes), con lo atendido
   sin facturar como dato informativo.
3. ¿Copagos y cuotas moderadoras por contrato? Propuesta: no; los valores los fija la norma por tipo de afiliado y ya
   se registran en cada cobro.

## Entregas

1. Tablas, migración de datos y pestaña Contratos (con servicios y vigencia).
2. Contrato en el cobro y en el precio; lote por contrato.
3. Avisos (agenda, atención, lote y vencimientos).
4. Capitación, cuando haya un caso real.
