# Guía de operación de Galeno en AWS

Cómo se opera Galeno en producción hoy (noviembre de 2026). El diseño y el porqué de cada pieza están en
[aws-capa-1.md](aws-capa-1.md) y [arquitectura.md](arquitectura.md).

Todos los comandos son para **Git Bash** en Windows, desde la carpeta del proyecto (`C:\Proyectos\Galeno`).

| Dato | Valor |
|---|---|
| Dominio | https://galenum.com.co |
| Proyecto de AWS | Cuenta 821211779252, región **us-east-2** (Ohio) |
| Perfil de la CLI | `galeno` |
| Infraestructura | Terraform en `infra/`, estado local en `infra/terraform.tfstate` (no está en Git: no lo borres) |

---

## 1. Iniciar sesión en AWS desde la terminal (y trabajar con Claude)

Galeno no usa llaves de acceso guardadas. Se entra a AWS con `aws login`, que abre el navegador y deja una sesión
temporal en el computador. Esa misma sesión la usan `tools/desplegar.sh`, Terraform y Claude Code cuando ejecuta
comandos de AWS por ti.

### Una sola vez: atajos en Git Bash

Hay dos AWS CLI instaladas y la vieja (2.33) va primero en el PATH, pero no entiende `aws login`. Para usar siempre
la nueva, y para tener Terraform a mano, agrega esto al final de `~/.bashrc` y abre una terminal nueva:

```bash
alias aws='"$LOCALAPPDATA/Programs/Amazon/AWSCLIV2/aws.exe"'
alias terraform='"$(ls "$LOCALAPPDATA"/Microsoft/WinGet/Packages/Hashicorp.Terraform_*/terraform.exe | head -1)"'
export AWS_PROFILE=galeno
```

Compruébalo con `aws --version`: debe decir 2.32 o más (hoy 2.38.4).

### Cada vez que empiezas a trabajar

```bash
aws login --profile galeno
```

1. Se abre el navegador: entra con tu cuenta del proyecto (la misma de [AWS Settings](https://settings.aws.com/)).
2. Al terminar, la terminal queda autenticada. La sesión dura **12 horas**; después se repite `aws login`.
3. Para comprobar que la sesión sirve:

```bash
aws sts get-caller-identity --profile galeno
```

Si responde con la cuenta `821211779252`, ya está. Si dice que la sesión venció o no hay credenciales, repite el login.

### Con Claude Code

- Haz el `aws login` **tú**, en tu terminal, antes de pedirle a Claude algo de AWS. Claude no inicia sesión por ti
  ni debe recibir llaves de acceso.
- Claude ya sabe que el proyecto usa el perfil `galeno` y la región `us-east-2`.
- Si un comando de Claude falla con "Access Denied" de repente, revisa primero si la sesión venció (`aws login`).
  Si la sesión está bien y el error sigue, revisa el **límite de gasto** en AWS Settings → Billing: al superarlo,
  AWS pausa el proyecto.
- Por seguridad, Claude no ejecuta `terraform apply` ni `tools/desplegar.sh` por su cuenta: te deja el comando
  listo y lo corres tú.

---

## 2. Desplegar cambios nuevos

### Requisitos

- **Docker Desktop abierto** (construye la imagen de la API).
- Node instalado (construye el frontend).
- Sesión de AWS vigente (punto 1).

### Flujo normal

1. **Trabaja en una rama** y abre un PR hacia `main`. El CI de GitHub (`ci.yml`) compila y prueba.
2. **Haz merge del PR** en GitHub.
3. **Despliega desde `main` actualizado:**

```bash
git switch main && git pull
tools/desplegar.sh
```

El script hace todo en orden y se detiene si algo falla:

| Paso | Qué hace |
|---|---|
| Sesión de AWS | Comprueba que `aws login` sigue vigente |
| API: imagen → ECR | Construye la imagen de la API para ARM (la EC2 es t4g) y la sube a ECR con dos etiquetas: el commit y `latest` |
| API: actualizar la EC2 | Por SSM (sin SSH) la EC2 lee la configuración de Parameter Store, descarga la imagen nueva y la arranca. Espera hasta 3 minutos a que la API responda; si no, muestra sus últimos logs |
| Frontend: build | `npm install` y `npm run build` |
| Frontend: S3 + CloudFront | Sube la web al bucket y borra la caché de CloudFront para que todos vean la versión nueva |

Los cambios de base de datos (migraciones de Flyway en `backend/src/main/resources/db/migration`) se aplican solos
cuando la API arranca.

### Solo una parte

```bash
tools/desplegar.sh --solo-api   # cambios solo en backend/ (o para reiniciar la API y que lea configuración nueva)
tools/desplegar.sh --solo-web   # cambios solo en frontend/
```

### Detalles a tener en cuenta

- Si hay cambios **sin commit**, el script avisa y despliega lo que está en disco (la versión queda como
  `<commit>-local`). Lo sano es desplegar siempre desde `main` limpio.
- Puedes desplegar con `npm run dev` abierto.
- La primera construcción de la imagen tarda varios minutos; las siguientes usan caché.
- **Volver a una versión anterior:** `git switch --detach <commit>` y `tools/desplegar.sh`. Ojo: las migraciones de
  base de datos no se deshacen solas.

### Cambios de infraestructura (Terraform)

Para cambios en `infra/` (permisos, parámetros, recursos):

```bash
cd infra
terraform plan -out=cambio.tfplan   # revisa qué crea, cambia o destruye
terraform apply cambio.tfplan
```

Antes de aplicar, lee el resumen `Plan: X to add, Y to change, Z to destroy`. Si aparece algo que se **destruye** y
no lo esperabas, no apliques. La base de datos y el bucket de documentos están protegidos contra borrado, pero
igual hay que leer el plan.

### Origen HTTPS (Caddy): activarlo y volver atrás

CloudFront llega a la API por HTTPS a través de Caddy (ver [aws-capa-1.md](aws-capa-1.md), "Origen de la API"). Se
activa en dos fases para no cortar el servicio: si CloudFront pasara a HTTPS antes de que Caddy tenga su certificado,
la API quedaría caída.

**Fase 1: Caddy con su certificado, CloudFront todavía por HTTP.**
1. `cd infra && terraform plan -out=origen1.tfplan`. Debe crear el repositorio `galeno-caddy`, el registro DNS
   `origen.`, la regla del 443 y cambiar la política del rol; **nada se destruye** y CloudFront no cambia. Luego
   `terraform apply origen1.tfplan && cd ..`
2. `tools/desplegar.sh --solo-api`: construye la imagen de Caddy (la primera vez, unos minutos) y la levanta.
3. Comprobar que Caddy tiene el certificado: en los logs (stream `caddy` del grupo `/galeno/prod/api`) debe aparecer
   `certificate obtained successfully` para los dos nombres.

**Fase 2: CloudFront por HTTPS.**
1. En `infra/terraform.tfvars`: `origen_https = true`.
2. `cd infra && terraform plan -out=origen2.tfplan`: cambia el origen de la distribución y **destruye la regla del
   8090** (es lo esperado). `terraform apply origen2.tfplan && cd ..`. CloudFront tarda unos minutos en propagarlo.
3. `tools/desplegar.sh --solo-api`: la API queda escuchando solo en la máquina.
4. Comprobar: `curl -sD - -o /dev/null https://galenum.com.co/api/v1/auth/me` debe mostrar
   `resource_metadata="https://…"` (antes decía `http://`).

**Rotar el secreto de origen** (`X-Origen-Galeno`, con el que la API reconoce a CloudFront) si se expone: sube
`version_origen` en `infra/secretos.tf`, `terraform plan`/`apply` y `tools/desplegar.sh --solo-api`. Entre el `apply` y
el final del despliegue (y mientras CloudFront propaga, unos minutos) la API rechaza las peticiones. Para hacerlo sin
corte, ver "3a" en [aws-capa-1.md](aws-capa-1.md). Se rotó el 2 de noviembre de 2026, porque el log de Caddy lo
guardaba en claro.

**Volver atrás** (si el origen HTTPS falla): `origen_https = false` en `terraform.tfvars`, `terraform plan`/`apply` y
`tools/desplegar.sh --solo-api`. CloudFront vuelve a la API por HTTP en el 8090.

---

## 3. Operadores de la plataforma

Los operadores son quienes entran a la consola de la plataforma (revisar pagos, administrar suscripciones). No es
un rol que se pida desde la web: lo define una lista en la configuración, y **la lista manda**. Al arrancar, la API
da acceso a quien está en ella y se lo quita a quien no está.

La lista está en `infra/terraform.tfvars`. Ese archivo no va a Git; si no existe, créalo con este contenido:

```hcl
operadores_plataforma = "andersoncabezas31@gmail.com"
```

### Agregar un operador

1. **Agrega el correo a la lista**, separado por comas:

   ```hcl
   operadores_plataforma = "andersoncabezas31@gmail.com,otra.persona@ejemplo.com"
   ```

2. **Aplica Terraform** (debe salir `1 to change`, el parámetro `APP_PLATAFORMA_OPERADORES`):

   ```bash
   cd infra && terraform plan -out=operadores.tfplan && terraform apply operadores.tfplan && cd ..
   ```

3. **Reinicia la API** para que lea la lista nueva:

   ```bash
   tools/desplegar.sh --solo-api
   ```

Qué pasa al arrancar:

- **Si la persona no tiene cuenta en Galeno:** la API se la crea (sin consultorio) y le envía el correo
  "Defina su contraseña de Galeno". El enlace dura una hora; si vence, la persona pide otro con
  "¿Olvidó su contraseña?" en https://galenum.com.co.
- **Si ya tiene cuenta** (por ejemplo, es administradora de un consultorio): queda como operador y entra a la
  plataforma con su misma contraseña.

### Quitar un operador

1. Borra su correo de `operadores_plataforma` en `infra/terraform.tfvars`.
2. Aplica Terraform (paso 2 de arriba).
3. Reinicia la API con `tools/desplegar.sh --solo-api`.

Al arrancar, la API le quita el acceso; si tenía la consola abierta, su sesión deja de servir en la siguiente
acción. Su cuenta de Galeno no se borra: si también usa un consultorio, lo sigue usando.

**Cuidado:** si la lista queda vacía, nadie entra a la consola, tú tampoco. Para recuperar el acceso, vuelve a poner
tu correo, aplica Terraform y reinicia la API.

### Cómo comprobarlo

Cada vez que se da o se quita un acceso queda registrado en `auditoria_plataforma`, con las acciones
`ACCESO_OTORGADO` y `ACCESO_RETIRADO`. En los logs de la API (punto 5) busca `app.plataforma.operadores`.

### Buenas prácticas

- Solo quien de verdad revisa pagos: un operador puede aprobar pagos, regalar días y suspender consultorios.
- Un correo por persona, nunca cuentas compartidas, para que la auditoría diga quién hizo qué.
- Contraseñas largas y únicas, además de la verificación en dos pasos (obligatoria para operadores, ver abajo).

### Verificación en dos pasos (MFA)

Al ingresar, además de la contraseña se pide el código de 6 dígitos de una app de autenticación (Google
Authenticator, Microsoft Authenticator, 1Password…).

| Quién | Cómo es |
|---|---|
| Operadores de la plataforma | Obligatoria: el primer ingreso pide configurarla (QR) antes de entrar |
| Quien administra algún consultorio | Obligatoria, igual que los operadores; también al confirmar el registro de un consultorio nuevo |
| Médicos, secretarios y partners | Opcional: se activa en **Seguridad**, en el menú |

- Al configurarla se entregan **10 códigos de recuperación** de un solo uso, que se muestran una sola vez.
- "Recordar este equipo" deja entrar 30 días desde ese navegador solo con la contraseña. En **Seguridad** se
  pueden olvidar todos los equipos.
- Tras 5 códigos equivocados seguidos, la cuenta queda 15 minutos sin poder intentar.
- La persona recibe un correo cuando se activa, se desactiva, se usa un código de recuperación o se restablece.

**Si alguien pierde el celular y los códigos:** primero confirma que quien lo pide es el titular (por ejemplo, con
una llamada al número que tienes registrado). Luego, en la consola de la plataforma, **Seguridad → Restablecer la
verificación de otra cuenta** con su correo. Queda en la auditoría como `MFA_RESTABLECIDA`, y en su próximo ingreso
la configura de nuevo. Nadie puede restablecer la suya: lo hace otro operador. Si eres el único operador y pierdes
la tuya, hay que borrarla en la base de datos (tablas `mfa_*`); por eso conviene tener al menos dos operadores, o
guardar bien tus códigos.

**La clave `APP_MFA_CLAVE` (SSM) no se rota.** Cifra los secretos de las apps; si cambia, ninguno se puede descifrar
y todas las cuentas tendrían que configurarla de nuevo (habría que restablecerlas una por una).

---

## 4. Rutas de acceso

### Galeno (consultorios)

| Para qué | Ruta |
|---|---|
| Ingresar (médicos, administradores, secretarios) | https://galenum.com.co |
| Registrar un consultorio nuevo | https://galenum.com.co/registro |
| Olvidé mi contraseña | https://galenum.com.co/recuperar |
| Política de datos | https://galenum.com.co/politica-datos |

Dentro de la aplicación, según los permisos de cada persona: `/agenda`, `/pacientes`, `/equipo`, `/importar` y
`/suscripcion`. `www.galenum.com.co` redirige al dominio sin `www`.

### Galeno Plataforma (operadores de Galeno)

| Para qué | Ruta |
|---|---|
| Consola de la plataforma | https://galenum.com.co/plataforma |

Tiene su propio inicio de sesión. Solo entran las cuentas de la lista de operadores (punto 3).

### Galeno Partners (aliados que refieren consultorios)

| Para qué | Ruta |
|---|---|
| Ingresar al panel del partner | https://galenum.com.co/partner |
| Inscribirse como partner | https://galenum.com.co/partner/inscripcion |
| Condiciones del programa | https://galenum.com.co/partner/condiciones |

### Administración en AWS

| Para qué | Dónde |
|---|---|
| Proyecto, facturación, límite de gasto, miembros del equipo | [AWS Settings](https://settings.aws.com/) |
| Recursos (EC2, RDS, S3, CloudFront, SES, logs) | [Consola de AWS](https://console.aws.amazon.com/) en la región **us-east-2 (Ohio)** |
| Presupuestos y costos | Consola de AWS → Billing and Cost Management |
| Repositorio | https://github.com/Ander8zerox/Galeno |

### Canal de soporte

Los clientes escriben a **soporte@galenum.com.co**. Aparece en "Ayuda" (al pie del ingreso, del registro, del portal de
partners y del menú), en los mensajes que dicen "escríbanos" y en el pie de todos los correos. Además, los correos de
Galenum salen de `no-responder@` con **Reply-To: soporte@**, así que si alguien responde, la respuesta llega a
soporte.

#### Conexión con ImprovMX (correo entrante)

SES solo envía correo, no lo recibe. Lo que llega a cualquier dirección `@galenum.com.co` lo recibe **ImprovMX** y lo
reenvía al Gmail del dueño de la cuenta de ImprovMX:

```
Cliente → soporte@galenum.com.co → MX de galenum.com.co (Route 53) → ImprovMX → Gmail del equipo
Galenum (SES, no-responder@) → cliente responde → Reply-To: soporte@ → (mismo camino)
```

**Configuración actual** (creada el 3 de noviembre de 2026):

| Qué | Valor |
|---|---|
| Cuenta | [app.improvmx.com](https://app.improvmx.com), plan **Free** (US$0: 1 dominio, 25 alias, 500 correos reenviados al día, registro de 7 días, sin envío) |
| Dominio | `galenum.com.co` |
| Alias | `soporte` → Gmail del equipo; `*` (catch-all) → Gmail del equipo, para no perder lo que llegue a `hola@`, `contacto@`, etc. |
| Registros DNS (Terraform, `infra/correo.tf`) | MX `@` → `10 mx1.improvmx.com` y `20 mx2.improvmx.com`; TXT `@` → `v=spf1 include:spf.improvmx.com ~all` |

Los registros DNS **solo se cambian con Terraform**: si se crean a mano en Route 53, el siguiente `terraform apply`
falla porque el registro ya existe. No chocan con SES: los rebotes de SES van a `correo.galenum.com.co`, que tiene su
propio MX, y la firma DKIM de SES no depende del SPF raíz.

**Cómo se conectó (una sola vez):**
1. Se creó la cuenta en ImprovMX con el Gmail del equipo y se agregó el dominio `galenum.com.co`.
2. Se crearon los alias `soporte` y `*`.
3. Se aplicó Terraform (`aws_route53_record.mx` y `aws_route53_record.spf`).
4. En ImprovMX, "Check": el dominio pasa de "Forwarding not active" (rojo) a activo (verde).
5. Prueba: desde otro correo, escribir a soporte@galenum.com.co, y responder un correo de Galenum (por ejemplo, el de
   "¿Olvidó su contraseña?"). Los dos deben llegar al Gmail del equipo.

**Cómo comprobarlo:**
- Panel de ImprovMX: el dominio en verde y, en "Logs", los correos reenviados de los últimos 7 días (con el motivo si
  alguno no se entregó).
- Desde la terminal, los registros publicados:
  ```bash
  nslookup -type=MX galenum.com.co 8.8.8.8
  nslookup -type=TXT galenum.com.co 8.8.8.8
  ```

**Si deja de llegar el correo de soporte:**
- Revisa el spam de Gmail (los reenvíos a veces caen ahí: marca "No es spam" y crea un filtro para `to:soporte@galenum.com.co`).
- En ImprovMX → Logs: si dice que se superó el límite diario (500), se reanuda al día siguiente; si pasa seguido, plan
  pago.
- Si el dominio volvió a rojo, alguien cambió o borró los MX: `terraform plan` en `infra/` muestra la diferencia y
  `terraform apply` los restaura.

**Cambiar a dónde llega:** en ImprovMX, edita el destino de los alias (p. ej., a otra persona del equipo, o varios
destinos separados por coma). No hace falta tocar Galenum ni Terraform.

**Privacidad:** los correos de soporte pasan por ImprovMX (un tercero, fuera de Colombia) y Google. Pide a los
clientes no enviar historias clínicas ni datos de pacientes por correo, y menciona a ambos como encargados en la
política de datos (revisión legal). Para España no sirve tal cual (ver "Expansión a España" en plan.md).

**Responder como soporte@ y no desde tu Gmail personal:** el plan gratuito de ImprovMX no envía correo. Hay dos
opciones: en Gmail, "Enviar como" con el SMTP de SES (credenciales SMTP de un usuario IAM solo con `ses:SendRawEmail`;
el dominio ya está verificado en SES), o el plan pago de ImprovMX. Mientras tanto, respondes desde tu Gmail.

**WhatsApp:** cuando tengas un número de WhatsApp Business para soporte, ponlo en `app.soporte.whatsapp`
(`application.yml`) y en `SOPORTE.whatsapp` (`frontend/src/soporte.ts`), en formato `573001234567`. Con eso, "Ayuda"
abre WhatsApp y los mensajes y correos lo mencionan. Luego despliega.

---

## 5. Logs de la aplicación

La API envía sus logs a **CloudWatch Logs**, al grupo `/galeno/prod/api`. Se guardan **30 días**. Cada línea es un
JSON con la hora, el nivel (`INFO`, `WARN`, `ERROR`), la clase que lo escribió y el mensaje.

### En la consola de AWS

1. Entra a la [consola de AWS](https://console.aws.amazon.com/) y, arriba a la derecha, confirma que la región sea
   **US East (Ohio) us-east-2**.
2. Busca **CloudWatch** y entra.
3. Menú izquierdo: **Logs → Log groups** → `/galeno/prod/api` → stream `api`.

**Ver en vivo:** en el grupo, botón **Start tailing** (Live Tail). Muestra las líneas a medida que llegan; útil
mientras pruebas algo en la web.

**Buscar:** menú izquierdo **Logs → Logs Insights**, selecciona `/galeno/prod/api`, elige el rango de tiempo y usa
una de estas consultas:

```
# Errores de las últimas horas
fields @timestamp, log.logger, message
| filter log.level = "ERROR"
| sort @timestamp desc
| limit 50
```

```
# Correos (enviados y rechazados por SES)
fields @timestamp, log.level, message
| filter log.logger like /Correo/
| sort @timestamp desc
```

```
# Arranques de la API y cambios de operadores
fields @timestamp, message
| filter message like /Started GalenoApplication|app.plataforma.operadores/
| sort @timestamp desc
```

Logs Insights cobra por datos revisados (0,005 USD por GB). Con el volumen actual es menos de un centavo por
consulta, pero conviene usar rangos de tiempo cortos.

### Desde la terminal

```bash
MSYS_NO_PATHCONV=1 aws logs tail /galeno/prod/api --since 30m --region us-east-2            # últimos 30 minutos
MSYS_NO_PATHCONV=1 aws logs tail /galeno/prod/api --follow --region us-east-2               # en vivo (Ctrl+C para salir)
MSYS_NO_PATHCONV=1 aws logs tail /galeno/prod/api --since 2h --filter-pattern ERROR --region us-east-2
```

`MSYS_NO_PATHCONV=1` evita que Git Bash convierta `/galeno/prod/api` en una ruta de Windows.

### Entrar al servidor (casos excepcionales)

Desde la consola: **EC2 → Instances →** la instancia de Galeno **→ Connect → Session Manager → Connect**. Abre una
terminal en el servidor sin SSH ni puertos abiertos. Ahí, `sudo docker ps` y `sudo docker logs --tail 100
galeno-api-1`. Para el día a día bastan los logs de CloudWatch.

---

## 6. Pendientes del plan

### Para terminar la salida a producción

| # | Pendiente | Por qué importa |
|---|---|---|
| — | **Activar Claude en Amazon Bedrock** (us-east-2) y hacer una invocación real | Las funciones de IA no responden hasta activarlo; el permiso `bedrock-mantle:CreateInference` aún no se ha probado en producción |
| 12 | **Pruebas en producción**: registrar un consultorio desde cero, agenda, historia y PDF, documentos en S3, reportar y aprobar un pago, inscripción de un partner | Confirmar de punta a punta que todo funciona en AWS y no solo en local |
| — | Revisar el gasto después de la primera semana (esperado: 35–50 USD al mes) | Confirmar que el costo real coincide con lo estimado; límite de gasto en 60 USD, presupuesto en 50 USD |

### Antes de recibir pacientes reales

- ~~**HTTPS entre CloudFront y el servidor** (Caddy + Let's Encrypt) y `X-Forwarded-Proto` en la API.~~ Activos en
  producción desde el 2 de noviembre de 2026 (sección 2, "Origen HTTPS (Caddy)").
- ~~**MFA** para los operadores de la plataforma, y después para los consultorios.~~ Obligatoria para operadores y
  administradores de consultorio, opcional para los demás (sección 3, "Verificación en dos pasos").
- **Revisión legal** de los términos y de la política de datos (hoy un borrador en `/politica-datos`). En partners,
  también la revisión contable de las condiciones.

### Siguiente en el producto

El detalle está en [plan.md](plan.md), sección "Siguiente". Lo primero es el **motor de formatos clínicos**,
empezando por **odontología** (odontograma, periodontograma, plan de tratamiento, consentimientos). Después vienen
los demás pendientes: invitaciones por correo, pasarela de pagos y factura electrónica, recordatorios de citas,
portal del paciente, RIPS y facturación electrónica.
