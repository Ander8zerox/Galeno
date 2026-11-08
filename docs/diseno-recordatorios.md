# Diseño: recordatorios de citas (WhatsApp, SMS o correo)

Análisis del 8 de noviembre de 2026. **Decidido ese día: empezar por correo electrónico** (ver "Cómo quedó: correos
de citas" al final). WhatsApp y SMS quedan pendientes; este análisis sirve para retomarlos.

## Problema

- Los pacientes olvidan las citas: el consultorio pierde el espacio y la secretaria llama uno por uno.
- La lista de espera (agenda, entrega 3) adelanta o asigna citas, y hoy el aviso al paciente es una llamada.
- Objetivo: recordar cada cita por el canal que el paciente sí lee (en Colombia, WhatsApp), con costo bajo y predecible.

## Qué ofrece AWS (verificado el 8 de noviembre de 2026)

La cuenta está en la **nueva experiencia de AWS, plan pago** (`aws freetier get-account-plan-state`: PAID, ACTIVE), región
us-east-2. Ambos servicios de mensajería a personas están en la lista del **plan pago**, no en la del plan gratis
([servicios soportados](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html)).

| Canal | Servicio de AWS | Costo por mensaje (USD) | Requisitos y límites |
|---|---|---|---|
| **WhatsApp** | AWS End User Messaging Social (disponible en us-east-2) | AWS 0,005 + tarifa de Meta para Colombia, mensaje de "utilidad" (≈ 0,0008, cifra de terceros sin confirmar en Meta) ≈ **0,006** | Cuenta de Meta Business verificada, un número de teléfono propio y plantillas aprobadas por Meta. Respuestas del paciente: 0,001 c/u |
| **SMS** | AWS End User Messaging SMS | **0,05087** (tabla oficial de precios por país) | Colombia: **sin remitente alfanumérico ni números largos**; solo código corto dedicado (se pide con un caso de soporte, con costo mensual) o envío internacional "best effort". Sin respuestas |
| **Correo** | Amazon SES (ya integrado en Galenum) | ≈ 0,0001 | Ya funciona (fuera del sandbox, 50.000 al día). Muchos pacientes no lo leen a tiempo |

**Ejemplo: consultorio de 5 médicos**, 15 citas al día cada uno, 22 días al mes, es decir 1.650 recordatorios al mes:

- WhatsApp: ≈ USD 10 al mes (≈ COP 40.000).
- SMS: ≈ USD 84 al mes.
- Correo: ≈ USD 0,17 al mes.

Fuentes: [precios de AWS End User Messaging](https://aws.amazon.com/end-user-messaging/pricing/) (incluye la tabla
oficial de SMS por país), [SMS por país](https://docs.aws.amazon.com/sms-voice/latest/userguide/phone-numbers-sms-by-country.html)
y [AWS End User Messaging Social](https://docs.aws.amazon.com/social-messaging/latest/userguide/what-is-service.html).
Antes de cobrar por esto hay que confirmar la tarifa de Meta para Colombia en la página oficial de precios de WhatsApp
Business, que cambia por trimestres.

**Conclusión:** WhatsApp como canal principal y correo como respaldo gratuito. SMS no conviene en Colombia por AWS: es
unas 8 veces más caro que WhatsApp y exige un código corto dedicado.

## Propuesta

### Qué recibe el paciente

- **Recordatorio:** por defecto 24 horas antes; el consultorio lo puede cambiar a 48 horas, o a 24 horas más 2 horas
  antes. Ejemplo: "Hola Ana, le recordamos su cita en *Consultorio Sonrisas* con la Dra. Pérez el martes 10 de
  noviembre a las 8:00 a. m. Dirección: Cra 10 # 20-30. Si no puede asistir, llame al 601 555 0000."
- **Cita asignada o adelantada desde la lista de espera:** "Su cita quedó para el …" (la secretaria ya habló con el
  paciente; esto lo deja por escrito).
- **Cita cancelada por el consultorio:** opcional.
- Solo plantillas de **utilidad**, aprobadas por Meta, sin publicidad. Sin datos clínicos: ni motivo ni diagnóstico
  (Ley 1581 y reserva de la historia clínica).

### Consentimiento y datos del paciente

- Campo nuevo en la ficha: **"Acepta recordatorios por WhatsApp"**, marcado por defecto al registrar al paciente si da
  su celular. La secretaria lo informa de palabra, y el primer mensaje incluye cómo dejar de recibirlos. Al responder
  "No más", deja de recibirlos.
- El celular se normaliza a formato internacional (+57 3xx…). Un teléfono fijo o inválido se marca y no se envía nada.

### Cómo funciona por dentro

- **Tabla `recordatorios`:** cita, tipo, canal, estado (PENDIENTE, ENVIADO, ENTREGADO, LEÍDO, FALLIDO), id del mensaje
  en WhatsApp, error y fechas. Una fila por cita y tipo, así no se envía dos veces.
- **Tarea programada** cada 15 minutos (en la misma API, sin servidores nuevos): busca las citas abiertas que entran
  en la ventana del recordatorio y aún no lo tienen, y envía con el SDK de AWS (`SendWhatsAppMessage`).
- **Estados de entrega:** AWS los publica en un tema SNS; se suscribe una cola SQS que la API lee, igual que hoy con
  los rebotes de correo de SES. Así no hace falta abrir un webhook público.
- **Si WhatsApp falla** (número sin WhatsApp, por ejemplo) y el paciente tiene correo, se envía por correo.
- **En la agenda:** un ícono en cada cita (enviado, entregado, leído o falló), para que la secretaria sepa a quién
  llamar.
- **Permisos de AWS:** el rol de la instancia solo puede enviar desde el número de Galenum (`social-messaging:SendWhatsAppMessage`
  sobre su ARN) y leer su cola.
- **Desarrollo y pruebas:** con un buzón local, como el de correo; nunca se envían mensajes reales desde local ni desde
  `galeno_e2e`.

### Fases

1. **Recordatorios de una vía:** consentimiento, plantillas, tarea programada, estados de entrega, respaldo por correo
   y el ícono en la agenda.
2. **Respuesta del paciente:** botones "Confirmo" y "No puedo asistir" en el mensaje. Confirmar marca la cita como
   confirmada; "No puedo" avisa a la secretaria y abre las sugerencias de la lista de espera para ese espacio. Nada se
   cancela solo.
3. **Más adelante:** número de WhatsApp propio por consultorio, y recordatorios de control (por ejemplo, "le toca su
   control de ortodoncia").

### Lo que hace la usuaria, no el código

- Crear la cuenta de **Meta Business** de Galenum y verificarla (documento de la empresa). Puede tardar días.
- Conseguir un **número de celular nuevo** para Galenum que no tenga WhatsApp instalado, o liberar uno.
- Vincular la cuenta de WhatsApp Business en la consola de AWS End User Messaging Social (us-east-2), con el asistente
  de Meta.
- Enviar las plantillas a aprobación de Meta (Galenum las deja redactadas).
- Revisar el **límite de gasto** del proyecto en AWS Settings > Billing.

## Para retomar WhatsApp (pendiente)

1. **Canal:** WhatsApp con respaldo por correo, o también SMS.
2. **Número:** uno solo de Galenum para todos (recomendado para empezar), o uno por consultorio. Implicaciones del
   número por consultorio: cada uno necesita su cuenta de Meta Business verificada y un número que no esté en la app
   de WhatsApp (casi todos ya atienden por WhatsApp Business en su número conocido; la "coexistencia" de app y API no
   está confirmada en AWS), la vinculación se hace en la consola de AWS de Galenum con el inicio de sesión de Meta del
   consultorio, y las respuestas de los pacientes no llegan a ninguna app (habría que construir una bandeja). Lo
   recomendable: número de Galenum primero y un "remitente" opcional por consultorio más adelante.
3. **Cobro al consultorio:** incluido en el plan hasta un tope mensual, incluido sin tope, o como adicional.
4. **Alcance de la primera entrega:** solo la fase 1, o con los botones de la fase 2.

## Cómo quedó: correos de citas (8 de noviembre de 2026)

- **Qué recibe el paciente:**
  - **Cita agendada:** también al asignarle un espacio desde la lista de espera.
  - **Cita cambiada:** con la hora anterior. También al adelantarla.
  - **Cita cancelada.**
  - **Recordatorio 24 horas antes.**
  - Los envía el consultorio: su nombre como remitente (con la dirección de Galenum), sus datos de contacto en el
    texto, y las respuestas van a su correo. Sin el pie de soporte de Galenum y sin datos clínicos (ni el motivo).
- **Cuándo no se envía:**
  - El consultorio no tiene los correos activos.
  - El paciente no tiene correo, o pidió no recibirlos ("No enviarle correos sobre sus citas" en su ficha).
  - Es la cita que se registra al atender a alguien que llegó sin cita.
  - La cita ya pasó o ya no está abierta.
- **Recordatorio:** una tarea cada 15 minutos busca, en cada consultorio con correos activos, las citas abiertas de las
  próximas 24 horas que no lo tienen (`citas.recordatorio_enviado_en`). Un aviso enviado dentro de esas 24 horas
  (agendada o cambiada a última hora) cuenta como recordatorio. Al reprogramar, la marca se borra.
- **Seguro ante fallos:** los correos salen después de confirmar la transacción (`CorreosCitas`); si SES falla, la cita
  queda agendada igual.
- **Configuración (Equipo > Datos del consultorio, administrador):** dirección, teléfono, correo para respuestas y el
  interruptor de correos a pacientes. Los consultorios nuevos empiezan con los correos activos; los que ya existían, sin
  ellos (el administrador los activa). Migración V32.
- **API:** `GET|PUT /equipo/consultorio` y `sinCorreosCitas` en el paciente.
- **Costo:** SES, ≈ USD 0,10 por cada 1.000 correos (≈ USD 0,35 al mes para un consultorio de 5 médicos). La tarea
  cada 15 minutos no suma costo: corre dentro de la API, en la instancia EC2 que ya se paga, y RDS con disco gp3 no
  cobra por consulta. La frecuencia no cambia el número de correos (cada cita recibe a lo sumo un recordatorio); solo
  la precisión (llega entre 24 h y 23 h 45 min antes).
- **Índice** \`ix_citas_inicio (tenant_id, inicio)\` (V33) para la búsqueda de cada 15 minutos: con 60.000 citas de
  prueba, la consulta lee 52 filas en vez de 30.060.
- **Si algún día hay más de una instancia de la API**, la tarea correría en todas y podría duplicar recordatorios:
  habría que coordinarla (por ejemplo, con un bloqueo en la base de datos).

