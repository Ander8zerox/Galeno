# Configuración de la API en SSM Parameter Store, bajo /galeno/prod/<VARIABLE>. Al desplegar, la EC2 lee todo ese
# camino y lo escribe como su archivo de entorno (/opt/galeno/desplegar.sh): cambiar un valor es aplicar Terraform
# y volver a desplegar, sin tocar la instancia. Parámetros estándar: sin costo.

locals {
  # Súbelas para rotar el secreto (la API lo toma en el siguiente despliegue)
  version_clave_bd = 1
  version_jwt      = 1
  version_origen   = 2 # 2: el log de acceso de Caddy lo guardó en claro el 2 de noviembre de 2026

  entorno_api = merge(
    {
      SPRING_PROFILES_ACTIVE       = "prod"
      AWS_REGION                   = var.region
      SPRING_DATASOURCE_URL        = "jdbc:mysql://${aws_db_instance.principal.endpoint}/galeno?sslMode=REQUIRED&connectionTimeZone=UTC&useUnicode=true&characterEncoding=utf8"
      SPRING_DATASOURCE_USERNAME   = aws_db_instance.principal.username
      APP_WEB_BASE_URL             = local.url_web
      APP_DOCUMENTOS_BUCKET        = aws_s3_bucket.documentos.bucket
      APP_CORREO_REMITENTE         = "Galenum <no-responder@${var.dominio}>"
      APP_CORREO_CONFIGURATION_SET = aws_sesv2_configuration_set.galeno.configuration_set_name
      APP_CORREO_COLA_EVENTOS      = aws_sqs_queue.correo_eventos.url
    },
    # SSM no acepta valores vacíos
    var.operadores_plataforma == "" ? {} : { APP_PLATAFORMA_OPERADORES = var.operadores_plataforma },
  )
}

resource "aws_ssm_parameter" "entorno" {
  for_each = local.entorno_api

  name  = "/galeno/prod/${each.key}"
  type  = "String"
  value = each.value
}

resource "aws_ssm_parameter" "clave_bd" {
  name             = "/galeno/prod/SPRING_DATASOURCE_PASSWORD"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.bd.result
  value_wo_version = local.version_clave_bd
}

ephemeral "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_ssm_parameter" "jwt" {
  name             = "/galeno/prod/APP_SECURITY_JWT_SECRET"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.jwt.result
  value_wo_version = local.version_jwt
}

# Este sí queda en el estado: CloudFront lo necesita en claro para agregarlo como encabezado de origen.
# Para rotarlo sin cortar el servicio, ver "3a" en docs/aws-capa-1.md.
resource "random_password" "origen" {
  length  = 48
  special = false

  keepers = {
    version = local.version_origen
  }
}

resource "aws_ssm_parameter" "origen" {
  name             = "/galeno/prod/APP_WEB_ORIGEN_SECRETO"
  type             = "SecureString"
  value_wo         = random_password.origen.result
  value_wo_version = local.version_origen
}
