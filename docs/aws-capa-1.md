# AWS — Capa 1 (MVP en producción)

```
Usuario ─► Route 53 ─► CloudFront (TLS con ACM en us-east-1)
                         ├── /*        → S3 privado (frontend/dist), con OAC
                         └── /api/*    → EC2 t4g.small · Docker Compose (imagen de ECR)
                                            │  rol IAM: Bedrock, SSM, ECR, S3, SES, SQS, logs
                     RDS MySQL 8.4 (subred privada) · Bedrock (Claude) · SSM Parameter Store
                     ── todo lo regional en us-east-2 (Ohio) ──
```

Costo aproximado: 35–50 USD al mes, más el consumo de Bedrock. Confírmalo en la calculadora de AWS.

| Recurso | USD al mes (aprox., us-east-2) |
|---|---|
| EC2 `t4g.small` + disco gp3 de 20 GB | 14 |
| RDS `db.t4g.micro` + 20 GB gp3 + backups de 7 días | 15 |
| IPv4 pública (la IP elástica de la EC2) | 3,6 |
| KMS (la llave de los documentos) | 1 |
| Route 53 (la zona) | 0,5 |
| CloudFront, S3, SES, SNS, SQS, ECR, CloudWatch Logs | centavos con poco uso |

Lo que **no** se usa a propósito, por costo: NAT Gateway (~33 USD), balanceador (~18 USD), Secrets Manager (se usa
Parameter Store, gratis), Performance Insights, monitoreo detallado de EC2 y créditos `unlimited` de la t4g.

## Infraestructura como código (`infra/`)

Todo lo de esta página está en Terraform, en `infra/`. Terraform es gratis: lo que se cobra son los recursos que crea,
igual que si se crearan a mano. A cambio, `plan` muestra todo antes de crear nada y `destroy` no deja nada olvidado.

```bash
cd infra
terraform init
terraform plan -out capa1.tfplan   # solo lee: muestra qué se crearía
terraform apply capa1.tfplan       # paso 10: crea (y empieza a cobrar)
cd .. && tools/desplegar.sh        # paso 11: sube la API y el frontend (ver "6. Despliegue")
```

- Usa el perfil `galeno` de `aws login` (variable `perfil_aws`). El estado queda en `infra/terraform.tfstate`, fuera
  de git: guárdalo, porque tiene el secreto de origen de CloudFront.
- La zona de Route 53 se creó a mano para cambiar los nameservers en el registrador; el bloque `import` de `dns.tf` la
  adopta sin crear otra.
- Las claves de la base de datos y del JWT se generan como valores efímeros y se escriben con argumentos *write-only*:
  no quedan ni en el plan ni en el estado. Para rotarlas, sube `version_clave_bd` o `version_jwt` en `secretos.tf`.
- La configuración de la API (variables de entorno) está en SSM bajo `/galeno/prod/`. Al desplegar, la EC2 la lee con
  `/opt/galeno/desplegar.sh`: cambiar un valor es aplicar Terraform y volver a desplegar.
- `aws_acm_certificate_validation` espera a que ACM emita el certificado, y eso solo pasa cuando el dominio ya responde
  con los nameservers de Route 53. Antes de aplicar, confirma `nslookup -type=NS galenum.com.co 8.8.8.8`.
- Protegidos contra borrado: la zona y el bucket de documentos (`prevent_destroy`) y RDS (`deletion_protection`, con
  instantánea final).

### Origen de la API: HTTP entre CloudFront y la EC2
El navegador habla con CloudFront por HTTPS, pero CloudFront llega a la EC2 por HTTP en el 8090: la EC2 no tiene
certificado propio. Es el compromiso de la Capa 1 (sin balanceador). Antes de cargar pacientes reales hay que cerrarlo,
por ejemplo con un *VPC origin* de CloudFront (sin costo, pero la EC2 tendría que ir en subred privada con salida a
internet) o con un certificado en la instancia (Caddy + Let's Encrypt en un subdominio de origen).

## Región: us-east-2

El proyecto de AWS es de la *nueva experiencia* de AWS. Cada proyecto tiene **una sola región**, que AWS asigna según
el país de la dirección de contacto. La de Galeno es **us-east-2 (Ohio)**. Para confirmarla: AWS Settings > View all
projects > Overview > Additional Info > Region. Unas políticas de control (SCP) del proyecto bloquean casi toda acción
en otras regiones, así que **todo recurso regional va en us-east-2**: VPC, EC2, RDS, S3, SES, SNS, SQS, KMS, SSM,
ECR, CloudTrail y Bedrock.

- Los valores por defecto del código ya son us-east-2: `app.documentos.region` y `app.correo.region` en
  `application.yml`, y `AWS_REGION` en `docker-compose.yml`.
- Bedrock no tiene propiedad propia: `BedrockMantleBackend.fromEnv()` usa la cadena estándar del SDK, en este orden:
  la propiedad `aws.region`, la variable `AWS_REGION`, el perfil y, por último, los metadatos de la EC2. En la
  instancia sale us-east-2 de todos modos, pero se fija `AWS_REGION=us-east-2` para no depender de eso. El endpoint
  es `bedrock-mantle.us-east-2.api.aws`.
- Usa el ID de modelo **de la región**, `anthropic.claude-haiku-5-5` (así está en `application.yml`). Los perfiles de
  inferencia `us.anthropic...` y `global.anthropic...` reparten las peticiones entre regiones, que es justo lo que el
  proyecto restringe.

### Por qué el certificado de CloudFront va en us-east-1

CloudFront es un servicio **global**: sus distribuciones no viven en ninguna región, y su API se atiende desde
us-east-1. Por eso AWS exige que el certificado de ACM que usa una distribución esté en **us-east-1**, sea cual sea la
región de lo demás. Esto no choca con la restricción de región:

- Las SCP del proyecto tienen una excepción para us-east-1 que deja pasar `acm:*`, `cloudfront:*`, `route53:*`,
  `iam:*` y `logs:*`, es decir, los servicios globales y lo que estos necesitan. Fuera de esas excepciones, crear
  recursos regionales en us-east-1 (EC2, RDS, SQS, SES...) está bloqueado.
- El certificado no guarda datos ni procesa tráfico de la aplicación: solo lo usa el borde de CloudFront. Los datos
  clínicos se quedan en us-east-2 (RDS y S3) y CloudFront llega al origen en Ohio.
- Lo único que se crea en us-east-1 es el certificado de ACM. Con Terraform eso significa un segundo `provider "aws"`
  con `alias = "us_east_1"`, usado **solo** por el `aws_acm_certificate` y su validación.
- WAF para CloudFront también tendría que ir en us-east-1. Ojo: la página de regiones de proyecto dice que usar WAF con
  CloudFront **no está disponible** en la nueva experiencia. Hay que confirmarlo antes de la Capa 2.

Fuentes: [regiones de proyecto](https://docs.aws.amazon.com/accounts/latest/reference/project-regions.html) y
[SCP y RCP de proyectos](https://docs.aws.amazon.com/accounts/latest/reference/scps-and-rcps-for-projects.html).

## Plan gratuito: qué sirve y qué no

Estado al 22 de octubre de 2026 (`aws freetier get-account-plan-state --profile galeno --region us-east-2`): plan
**FREE**, activo, 100 USD de crédito y vence el **22 de abril de 2027**.

Todo lo revisado en us-east-2 se hizo sin crear nada: `--dry-run` en EC2 y solo consultas en lo demás.

| Servicio | ¿En el plan gratuito? | Cómo se comprobó / qué hay que saber |
|---|---|---|
| EC2 `t4g.small` | Sí | `run-instances --dry-run` con AL2023 ARM: *would have succeeded*. Está en la lista de tipos elegibles del plan. El plan bloquea Spot, Fleet, hosts dedicados y raíz *instance-store*. |
| RDS MySQL | Sí | `db.t4g.micro` se puede pedir para MySQL en us-east-2. Las SCP del plan solo bloquean DocumentDB y las reservas. RDS no tiene `--dry-run`: se confirma al aplicar Terraform. |
| S3 | Sí | Bloqueadas las clases Glacier y Deep Archive. Glacier Instant Retrieval, la prevista para documentos viejos, no aparece bloqueada. Access Grants no está disponible. |
| CloudFront | Sí | Lambda@Edge no está disponible (no se usa). |
| ACM | Sí | Solo se bloquean los certificados *exportables* (no se usan). Se puede usar en us-east-1 (ver arriba). |
| SES | Sí | Fuera del *sandbox* desde el 2 de noviembre de 2026 (`ProductionAccessEnabled=true`): 50.000 correos al día y 14 por segundo. Las SCP no bloquearon la solicitud; AWS la aprobó en horas. |
| SNS | Sí | Sin restricciones. |
| SQS | Sí | Sin restricciones. |
| Bedrock, `anthropic.claude-haiku-5-5` | Sí | El modelo está en us-east-2 (`regionAvailability=AVAILABLE`, `authorizationStatus=AUTHORIZED`). Falta habilitarlo para Anthropic (formulario de caso de uso, pendiente del usuario) y probar una invocación real. |
| SSM Parameter Store | Sí | Entra como "AWS Systems Manager". |
| ECR | Sí | Sin restricciones. |
| KMS | Sí | Sin restricciones (las llaves multirregión no aplican). |

**Qué requiere pasar al plan pago:** ninguno de los servicios de la Capa 1. Lo que obliga a pasar al plan pago es el
**plan mismo**:

1. **Vence.** El plan gratuito termina a los 6 meses (el 22 de abril de 2027) o cuando se acabe el crédito, lo que
   pase primero. Entonces AWS **cierra la cuenta** y solo guarda el contenido 90 días. Para un SaaS con historias
   clínicas, que deben conservarse al menos 15 años, eso no es aceptable.
2. **El crédito alcanza para poco.** Con 35–50 USD al mes más Bedrock, los 100 USD duran unos 2 meses.

Recomendación: montar y probar la infraestructura en el plan gratuito, y **pasar al plan pago antes de cargar
pacientes reales**. Se hace desde AWS Settings > Billing. En el plan pago se puede configurar un *spend limit* que
pausa el proyecto al alcanzarlo. Ojo: con un spend limit, al acercarse al tope AWS bloquea acciones como
`bedrock:InvokeModel`, `ec2:RunInstances` o `sqs:CreateQueue`. Si de pronto aparecen errores *Access Denied*, revisa
primero eso en AWS Settings > Billing.

Fuentes: [servicios de la nueva experiencia](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html),
[planes gratuito y pago](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier-plans.html) y
[SCP de proyectos](https://docs.aws.amazon.com/accounts/latest/reference/scps-and-rcps-for-projects.html).

## Paso a paso

### 0. Proyecto
- [ ] Miembros del equipo invitados por correo desde AWS Settings (en esta experiencia no se crean usuarios de IAM)
- [ ] En el plan pago: un *spend limit* en AWS Settings y un presupuesto con alertas en Billing and Cost Management (p. ej. 30 y 60 USD)
- [ ] CloudTrail activado en `us-east-2`
- [ ] En Bedrock, habilitar Anthropic y probar `anthropic.claude-haiku-5-5` (o los que configures en `APP_AI_MODEL_FORMULA` / `APP_AI_MODEL_DIAGNOSTICO`)

### 1. Red y base de datos
- [ ] VPC con 2 subredes públicas (EC2) y 2 privadas (RDS)
- [ ] RDS MySQL 8.4, `db.t4g.micro`, 20 GB gp3, cifrado, backups de 7 días, **sin acceso público**
- [ ] Security group de RDS: solo acepta el puerto 3306 desde el SG de la instancia EC2

### 2. Secretos (SSM Parameter Store, `SecureString`)
- `/galeno/prod/SPRING_DATASOURCE_PASSWORD`
- `/galeno/prod/APP_SECURITY_JWT_SECRET` (genéralo con `openssl rand -base64 48`)
- `/galeno/prod/APP_WEB_ORIGEN_SECRETO` (genéralo igual): el mismo valor va en el encabezado de origen de CloudFront

### 3. Cómputo
- [ ] Repositorio ECR `galeno-api`
- [ ] EC2 `t4g.small` (Amazon Linux 2023, ARM) con Docker, **sin puerto 22 abierto**: el acceso es por SSM Session Manager
- [ ] Rol de la instancia con `AmazonSSMManagedInstanceCore`, lectura de ECR, `ssm:GetParameters` sobre `/galeno/prod/*`, `bedrock:InvokeModel*`, y `s3:PutObject` / `s3:GetObject` solo sobre el bucket de documentos (sin `DeleteObject`)
- [ ] El security group solo acepta el puerto 8090 desde el *prefix list* gestionado de CloudFront (`com.amazonaws.global.cloudfront.origin-facing`)
- [ ] `/opt/galeno/docker-compose.yml` con la imagen de ECR, `SPRING_PROFILES_ACTIVE=prod`, `AWS_REGION=us-east-2` y las variables de abajo; los secretos se leen de SSM al arrancar

**Perfil `prod`** (`application-prod.yml`): S3, SES y Bedrock fijos, sin seed ni buzón de desarrollo, sin Swagger,
actuator solo con `health` (sin detalles) y logs en JSON (ECS) para CloudWatch. La API **no arranca** si falta alguna
de estas variables, y el error dice cuál (`shared.ConfiguracionProduccion`): `SPRING_DATASOURCE_URL` (no puede ser
localhost), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_SECURITY_JWT_SECRET`, `APP_WEB_BASE_URL`
(con `https://`), `APP_WEB_ORIGEN_SECRETO`, `APP_DOCUMENTOS_BUCKET`, `APP_CORREO_REMITENTE`,
`APP_CORREO_CONFIGURATION_SET` y `APP_CORREO_COLA_EVENTOS`.

> La EC2 es ARM (t4g): la imagen es `linux/arm64`. El Dockerfile compila el JAR en la arquitectura del equipo que construye (`--platform=$BUILDPLATFORM`) y solo la imagen final es ARM, así que no hay que emular Maven.

### 3a. IP real del cliente detrás de CloudFront
Detrás de CloudFront, la IP que ve la API es la de un nodo de CloudFront. Si no se resuelve la del visitante, los
límites por IP (registro, recuperación de contraseña, partners) contarían a todos los usuarios como uno solo.

- La resuelve `shared.web.IpCliente` (es el único lugar). Lee `CloudFront-Viewer-Address`; si falta, el **último**
  valor de `X-Forwarded-For`, que es el que agrega CloudFront (los anteriores los puede inventar el cliente).
- Esos encabezados solo cuentan si la petición trae `X-Origen-Galeno` con el secreto de `APP_WEB_ORIGEN_SECRETO`.
  `shared.web.OrigenCloudFrontFilter` responde 403 a todo lo que llegue sin él, salvo `/actuator/health`, que lo
  consulta la propia instancia. El prefix list del security group no basta: también deja pasar las distribuciones
  de CloudFront de **otras** cuentas.
- `server.forward-headers-strategy` ya no se usa: con `framework`, Spring creía el `X-Forwarded-For` de cualquiera.
- En CloudFront, en el origen `/api/*`: encabezado personalizado `X-Origen-Galeno: <secreto>` y una *origin request
  policy* que incluya `CloudFront-Viewer-Address` y `Authorization`.
- Para rotar el secreto sin cortar el servicio: `APP_WEB_ORIGEN_SECRETO=nuevo,anterior`, luego cambiarlo en
  CloudFront y, por último, quitar el anterior.

### 3b. Documentos de la historia clínica (S3)
- [ ] Bucket `galeno-prod-documentos-<cuenta>` en us-east-2 con *Block Public Access* completo, *Object Ownership* = bucket owner enforced (sin ACL), versionado y cifrado por defecto SSE-KMS con *bucket key*
- [ ] Política del bucket que rechaza peticiones sin TLS (`aws:SecureTransport = false`) y cualquier principal distinto del rol de la instancia
- [ ] Sin reglas de borrado: la historia clínica se conserva al menos 15 años (Res. 1995 de 1999 y 839 de 2017); más adelante, una transición a S3 Glacier Instant Retrieval para lo antiguo (no a Glacier Flexible ni Deep Archive: el plan gratuito las bloquea y tardan horas en devolver un archivo)
- [ ] Variables: `APP_DOCUMENTOS_BUCKET=<nombre>` (`APP_DOCUMENTOS_ALMACEN=s3` ya lo pone el perfil prod; la región por defecto es us-east-2)
- [ ] Antes de cambiar a S3 en un ambiente con datos, copiar `data/documentos/` al bucket con las mismas claves (`aws s3 sync data/documentos s3://<bucket>/`)

### 4. Correo (SES)
- [ ] Identidad de dominio en SES (us-east-2) con DKIM (3 registros CNAME en Route 53), *custom MAIL FROM* (`correo.<dominio>`, registros MX y SPF) y un registro DMARC (`_dmarc`, empezar con `p=none`)
- [ ] Configuration set `galeno` con un destino de eventos para `BOUNCE` y `COMPLAINT` hacia el tema SNS `galeno-correo-eventos`
- [ ] Cola SQS `galeno-correo-eventos` suscrita al tema (con su DLQ, 5 intentos) y supresión a nivel de cuenta activada para rebotes y quejas
- [ ] Rol de la instancia: `ses:SendEmail` solo sobre la identidad y el configuration set, y `sqs:ReceiveMessage` / `sqs:DeleteMessage` sobre la cola
- [ ] Variables: `APP_CORREO_REMITENTE="Galeno <no-responder@<dominio>>"`, `APP_CORREO_CONFIGURATION_SET=galeno`, `APP_CORREO_COLA_EVENTOS=<URL de la cola>` (`APP_CORREO_PROVEEDOR=ses` ya lo pone el perfil prod; la región por defecto es us-east-2)
- [x] Salir del *sandbox* de SES (en la consola de SES en us-east-2, "Request production access", o `aws sesv2 put-account-details --production-access-enabled`): aprobado el 2 de noviembre de 2026

### 5. Frontend y CDN
- [ ] Bucket S3 privado para `frontend/dist` (us-east-2)
- [ ] CloudFront con dos orígenes: S3 (OAC) por defecto y la EC2 para `/api/*` (sin caché; ver 3a para encabezados)
- [ ] Rutas del SPA → `/index.html` con una CloudFront Function (`infra/funcion-spa.js`), que además redirige `www` al dominio raíz. No con respuestas de error de la distribución: esas aplican también a `/api/*`
- [ ] Certificado ACM **en us-east-1** (lo exige CloudFront; ver "Región") y registro en Route 53

### 6. Despliegue
El proyecto de AWS **no permite crear proveedores de identidad**: la SCP `DenyIAMRestrictedActions` niega
`iam:*Provider*`, así que GitHub Actions no puede entrar con OIDC. Para no guardar una llave de acceso permanente en
GitHub, el despliegue se hace desde el computador con la sesión de `aws login`. GitHub Actions sigue corriendo las
pruebas (`ci.yml`).

```bash
aws login --profile galeno       # si la sesión venció (dura 12 h)
tools/desplegar.sh               # API y frontend; también --solo-api o --solo-web
```

El script lee los datos de `terraform output`, sube la imagen `linux/arm64` a ECR con la etiqueta del commit y
`latest`, y por SSM corre `/opt/galeno/desplegar.sh` en la EC2, esperando a que `/actuator/health` responda (si no,
muestra los últimos logs). Luego publica `frontend/dist` en S3 (assets con caché de un año, `index.html` sin caché) e
invalida CloudFront. Si hay cambios sin commit, la imagen lleva la etiqueta `<commit>-local`.

Si más adelante el equipo crece y se quiere desplegar desde GitHub, hay dos caminos: un usuario de IAM con una llave
en los secretos del repositorio (permitido, pero es una llave permanente que hay que rotar) o activar las *advanced
features* del proyecto (irreversible, y la documentación no confirma que quite esta restricción).

## Siguiente capa

**Capa 2 (robustez):**
- Logs y alarmas en CloudWatch, y Sentry.
- WAF en CloudFront: confirmar antes si la nueva experiencia lo permite (ver "Región").
- Ambiente de *staging*.

**Capa 3 (disponibilidad):**
- ALB + ECS Fargate con 2 tareas o más (la misma imagen).
- RDS Multi-AZ.
- SQS para la cola de IA.
