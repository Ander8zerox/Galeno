# SES con el dominio verificado por DKIM, rebotes y quejas hacia SNS -> SQS (shared.correo.ColaEventosSes).
# Salir del sandbox (paso 9) es una solicitud a mano en la consola de SES; no se hace con Terraform.

resource "aws_sesv2_configuration_set" "galeno" {
  configuration_set_name = "galeno"

  delivery_options {
    tls_policy = "REQUIRE"
  }
  reputation_options {
    reputation_metrics_enabled = true
  }
  sending_options {
    sending_enabled = true
  }
}

resource "aws_sesv2_email_identity" "dominio" {
  email_identity         = var.dominio
  configuration_set_name = aws_sesv2_configuration_set.galeno.configuration_set_name
}

resource "aws_route53_record" "dkim" {
  count = 3

  zone_id = aws_route53_zone.principal.zone_id
  name    = "${aws_sesv2_email_identity.dominio.dkim_signing_attributes[0].tokens[count.index]}._domainkey.${var.dominio}"
  type    = "CNAME"
  ttl     = 1800
  records = ["${aws_sesv2_email_identity.dominio.dkim_signing_attributes[0].tokens[count.index]}.dkim.amazonses.com"]
}

# MAIL FROM propio: los rebotes vuelven a correo.<dominio> y el SPF queda alineado con el dominio
resource "aws_sesv2_email_identity_mail_from_attributes" "dominio" {
  email_identity         = aws_sesv2_email_identity.dominio.email_identity
  mail_from_domain       = "correo.${var.dominio}"
  behavior_on_mx_failure = "USE_DEFAULT_VALUE"
}

resource "aws_route53_record" "mail_from_mx" {
  zone_id = aws_route53_zone.principal.zone_id
  name    = "correo.${var.dominio}"
  type    = "MX"
  ttl     = 1800
  records = ["10 feedback-smtp.${var.region}.amazonses.com"]
}

resource "aws_route53_record" "mail_from_spf" {
  zone_id = aws_route53_zone.principal.zone_id
  name    = "correo.${var.dominio}"
  type    = "TXT"
  ttl     = 1800
  records = ["v=spf1 include:amazonses.com ~all"]
}

# Empieza en p=none (solo observa); cuando los correos salgan bien alineados se sube a quarantine
resource "aws_route53_record" "dmarc" {
  zone_id = aws_route53_zone.principal.zone_id
  name    = "_dmarc.${var.dominio}"
  type    = "TXT"
  ttl     = 1800
  records = ["v=DMARC1; p=none;"]
}

# Una dirección que rebota o se queja no vuelve a recibir correos de la cuenta
resource "aws_sesv2_account_suppression_attributes" "galeno" {
  suppressed_reasons = ["BOUNCE", "COMPLAINT"]
}

# --- Rebotes y quejas: SES -> SNS -> SQS ---

resource "aws_sns_topic" "correo_eventos" {
  name = "galeno-correo-eventos"
}

data "aws_iam_policy_document" "correo_eventos_tema" {
  statement {
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.correo_eventos.arn]
    principals {
      type        = "Service"
      identifiers = ["ses.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.cuenta]
    }
  }
}

resource "aws_sns_topic_policy" "correo_eventos" {
  arn    = aws_sns_topic.correo_eventos.arn
  policy = data.aws_iam_policy_document.correo_eventos_tema.json
}

resource "aws_sesv2_configuration_set_event_destination" "rebotes_y_quejas" {
  configuration_set_name = aws_sesv2_configuration_set.galeno.configuration_set_name
  event_destination_name = "rebotes-y-quejas"

  event_destination {
    enabled              = true
    matching_event_types = ["BOUNCE", "COMPLAINT"]
    sns_destination {
      topic_arn = aws_sns_topic.correo_eventos.arn
    }
  }

  depends_on = [aws_sns_topic_policy.correo_eventos]
}

resource "aws_sqs_queue" "correo_eventos_dlq" {
  name                      = "galeno-correo-eventos-dlq"
  message_retention_seconds = 1209600 # 14 días
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "correo_eventos" {
  name                       = "galeno-correo-eventos"
  receive_wait_time_seconds  = 20 # long polling, como ColaEventosSes
  visibility_timeout_seconds = 60
  message_retention_seconds  = 1209600
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.correo_eventos_dlq.arn
    maxReceiveCount     = 5
  })
}

data "aws_iam_policy_document" "correo_eventos_cola" {
  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.correo_eventos.arn]
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.correo_eventos.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "correo_eventos" {
  queue_url = aws_sqs_queue.correo_eventos.id
  policy    = data.aws_iam_policy_document.correo_eventos_cola.json
}

resource "aws_sns_topic_subscription" "correo_eventos" {
  topic_arn = aws_sns_topic.correo_eventos.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.correo_eventos.arn
}
