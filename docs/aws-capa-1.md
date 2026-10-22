# AWS — Capa 1 (MVP en producción)

```
Usuario ─► Route 53 ─► CloudFront (TLS con ACM, WAF en la Capa 2)
                         ├── /*        → S3 privado (frontend/dist), con OAC
                         └── /api/*    → EC2 t4g.small · Docker Compose (imagen de ECR)
                                            │  rol IAM: Bedrock, SSM, ECR, logs
                     RDS MySQL 8 (subred privada) · Bedrock (Claude) · SSM Parameter Store
```

Costo aproximado: 35–50 USD al mes, más el consumo de Bedrock. Confírmalo en la calculadora de AWS.

## Paso a paso

### 0. Cuenta
- [ ] MFA en root; usuarios con IAM Identity Center
- [ ] AWS Budgets con alertas (p. ej. 30 y 60 USD)
- [ ] CloudTrail activado; región `us-east-1`
- [ ] En Bedrock, verificar el acceso al modelo `anthropic.claude-haiku-5-5` (o los que configures en `APP_AI_MODEL_FORMULA` / `APP_AI_MODEL_DIAGNOSTICO`)

### 1. Red y base de datos
- [ ] VPC con 2 subredes públicas (EC2) y 2 privadas (RDS)
- [ ] RDS MySQL 8.4, `db.t4g.micro`, 20 GB gp3, cifrado, backups de 7 días, **sin acceso público**
- [ ] Security group de RDS: solo acepta el puerto 3306 desde el SG de la instancia EC2

### 2. Secretos (SSM Parameter Store, `SecureString`)
- `/galeno/prod/SPRING_DATASOURCE_PASSWORD`
- `/galeno/prod/APP_SECURITY_JWT_SECRET` (genéralo con `openssl rand -base64 48`)

### 3. Cómputo
- [ ] Repositorio ECR `galeno-api`
- [ ] EC2 `t4g.small` (Amazon Linux 2023, ARM) con Docker, **sin puerto 22 abierto**: el acceso es por SSM Session Manager
- [ ] Rol de la instancia con `AmazonSSMManagedInstanceCore`, lectura de ECR, `ssm:GetParameters` sobre `/galeno/prod/*`, `bedrock:InvokeModel*`, y `s3:PutObject` / `s3:GetObject` solo sobre el bucket de documentos (sin `DeleteObject`)
- [ ] El security group solo acepta el puerto 8090 desde el *prefix list* gestionado de CloudFront (`com.amazonaws.global.cloudfront.origin-facing`)
- [ ] `/opt/galeno/docker-compose.yml` con la imagen de ECR y estas variables: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `APP_AI_PROVIDER=bedrock`, `AWS_REGION`, y los secretos leídos de SSM al arrancar

> La imagen se construye en GitHub Actions (x86). Para una EC2 ARM (t4g), usa `docker buildx build --platform linux/arm64`, o una instancia `t3.small`.

### 3b. Documentos de la historia clínica (S3)
- [ ] Bucket `galeno-prod-documentos-<cuenta>` con *Block Public Access* completo, *Object Ownership* = bucket owner enforced (sin ACL), versionado y cifrado por defecto SSE-KMS con *bucket key*
- [ ] Política del bucket que rechaza peticiones sin TLS (`aws:SecureTransport = false`) y cualquier principal distinto del rol de la instancia
- [ ] Sin reglas de borrado: la historia clínica se conserva al menos 15 años (Res. 1995 de 1999 y 839 de 2017); más adelante, una transición a S3 Glacier Instant Retrieval para lo antiguo
- [ ] Variables: `APP_DOCUMENTOS_ALMACEN=s3`, `APP_DOCUMENTOS_BUCKET=<nombre>`, `APP_DOCUMENTOS_REGION=us-east-1`
- [ ] Antes de cambiar a S3 en un ambiente con datos, copiar `data/documentos/` al bucket con las mismas claves (`aws s3 sync data/documentos s3://<bucket>/`)

### 4. Correo (SES)
- [ ] Identidad de dominio en SES con DKIM (3 registros CNAME en Route 53), *custom MAIL FROM* (`correo.<dominio>`, registros MX y SPF) y un registro DMARC (`_dmarc`, empezar con `p=none`)
- [ ] Configuration set `galeno` con un destino de eventos para `BOUNCE` y `COMPLAINT` hacia el tema SNS `galeno-correo-eventos`
- [ ] Cola SQS `galeno-correo-eventos` suscrita al tema (con su DLQ, 5 intentos) y supresión a nivel de cuenta activada para rebotes y quejas
- [ ] Rol de la instancia: `ses:SendEmail` solo sobre la identidad y el configuration set, y `sqs:ReceiveMessage` / `sqs:DeleteMessage` sobre la cola
- [ ] Variables: `APP_CORREO_PROVEEDOR=ses`, `APP_CORREO_REMITENTE="Galeno <no-responder@<dominio>>"`, `APP_CORREO_CONFIGURATION_SET=galeno`, `APP_CORREO_COLA_EVENTOS=<URL de la cola>`, `APP_CORREO_REGION=us-east-1`
- [ ] Salir del *sandbox* de SES (en la consola, "Request production access"): mientras tanto solo se puede escribir a direcciones verificadas

### 5. Frontend y CDN
- [ ] Bucket S3 privado para `frontend/dist`
- [ ] CloudFront con dos orígenes: S3 (OAC) por defecto y la EC2 para `/api/*` (sin caché, reenviando el header `Authorization`)
- [ ] Respuestas 403 y 404 de S3 → `/index.html` (rutas del SPA)
- [ ] Certificado ACM (en `us-east-1`) y registro en Route 53

### 6. CI/CD
- [ ] Proveedor OIDC de GitHub en IAM, con un rol para `.github/workflows/deploy.yml`
- [ ] Variables del repositorio: `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN`, `ECR_REPOSITORY`, `EC2_INSTANCE_ID`, `WEB_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID`

## Siguiente capa

**Capa 2 (robustez):**
- Logs y alarmas en CloudWatch, y Sentry.
- WAF en CloudFront.
- Ambiente de *staging*.
- Esta infraestructura como código (Terraform o CDK).

**Capa 3 (disponibilidad):**
- ALB + ECS Fargate con 2 tareas o más (la misma imagen).
- RDS Multi-AZ.
- SQS para la cola de IA.
