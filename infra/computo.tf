resource "aws_ecr_repository" "api" {
  name                 = "galeno-api"
  image_tag_mutability = "MUTABLE" # el deploy publica :latest además del SHA

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Solo las últimas 10 imágenes (cada una ocupa espacio que se cobra)
resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Conserva las ultimas 10 imagenes"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}

# Caddy con el módulo de Route 53 (infra/caddy/Dockerfile): el TLS entre CloudFront y la API
resource "aws_ecr_repository" "caddy" {
  name                 = "galeno-caddy"
  image_tag_mutability = "IMMUTABLE" # la etiqueta es el hash del Dockerfile

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "caddy" {
  repository = aws_ecr_repository.caddy.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Conserva las ultimas 3 imagenes"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 3 }
      action       = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/galeno/prod/api"
  retention_in_days = 30
}

# --- Rol de la instancia ---

data "aws_iam_policy_document" "ec2_asume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "api" {
  name               = "${local.nombre}-api"
  assume_role_policy = data.aws_iam_policy_document.ec2_asume.json
}

# Session Manager (consola sin SSH) y los comandos del deploy
resource "aws_iam_role_policy_attachment" "api_ssm" {
  role       = aws_iam_role.api.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "api" {
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }
  statement {
    sid       = "EcrDescargar"
    actions   = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"]
    resources = [aws_ecr_repository.api.arn, aws_ecr_repository.caddy.arn]
  }
  # Caddy saca el certificado de origen validando por DNS: solo puede escribir los TXT _acme-challenge de la zona
  statement {
    sid       = "CertificadoDns"
    actions   = ["route53:ChangeResourceRecordSets"]
    resources = [aws_route53_zone.principal.arn]
    condition {
      test     = "ForAllValues:StringEquals"
      variable = "route53:ChangeResourceRecordSetsNormalizedRecordNames"
      values   = ["_acme-challenge.${var.dominio}", "_acme-challenge.origen.${var.dominio}"]
    }
    condition {
      test     = "ForAllValues:StringEquals"
      variable = "route53:ChangeResourceRecordSetsRecordTypes"
      values   = ["TXT"]
    }
  }
  statement {
    sid       = "CertificadoDnsLectura"
    actions   = ["route53:ListResourceRecordSets"]
    resources = [aws_route53_zone.principal.arn]
  }
  statement {
    sid       = "CertificadoDnsCambios"
    actions   = ["route53:GetChange", "route53:ListHostedZones", "route53:ListHostedZonesByName"]
    resources = ["*"]
  }
  statement {
    sid     = "Configuracion"
    actions = ["ssm:GetParametersByPath", "ssm:GetParameters", "ssm:GetParameter"]
    resources = [
      "arn:aws:ssm:${var.region}:${local.cuenta}:parameter/galeno/prod",
      "arn:aws:ssm:${var.region}:${local.cuenta}:parameter/galeno/prod/*",
    ]
  }
  # Claude por el endpoint bedrock-mantle (BedrockMantleBackend); InvokeModel cubre el endpoint bedrock-runtime.
  # Solo en la región del proyecto.
  statement {
    sid       = "Bedrock"
    actions   = ["bedrock-mantle:CreateInference", "bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.region]
    }
  }
  # Documentos: leer y crear, nunca borrar
  statement {
    sid       = "Documentos"
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.documentos.arn}/*"]
  }
  statement {
    sid       = "DocumentosLlave"
    actions   = ["kms:GenerateDataKey", "kms:Decrypt"]
    resources = [aws_kms_key.documentos.arn]
  }
  statement {
    sid     = "Correo"
    actions = ["ses:SendEmail", "ses:SendRawEmail"]
    # SES también autoriza contra la identidad del destinatario cuando está verificada (pasa con las direcciones
    # verificadas en la época del sandbox), así que el recurso es identity/*; lo que se limita es el remitente.
    resources = [
      "arn:aws:ses:${var.region}:${local.cuenta}:identity/*",
      aws_sesv2_configuration_set.galeno.arn,
    ]
    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = ["no-responder@${var.dominio}"]
    }
  }
  statement {
    # "Desbloquear" un correo en la plataforma también lo quita de la lista de supresión de la cuenta de SES. Esta
    # acción no admite un recurso específico (la lista es de la cuenta).
    sid       = "QuitarSupresionSes"
    actions   = ["ses:DeleteSuppressedDestination"]
    resources = ["*"]
  }
  statement {
    sid       = "RebotesYQuejas"
    actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.correo_eventos.arn]
  }
  statement {
    sid       = "Logs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
    resources = ["${aws_cloudwatch_log_group.api.arn}:*"]
  }
}

resource "aws_iam_role_policy" "api" {
  name   = "galeno-api"
  role   = aws_iam_role.api.id
  policy = data.aws_iam_policy_document.api.json
}

resource "aws_iam_instance_profile" "api" {
  name = "${local.nombre}-api"
  role = aws_iam_role.api.name
}

# --- Instancia ---

data "aws_ami" "al2023_arm" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-arm64"]
  }
  filter {
    name   = "architecture"
    values = ["arm64"]
  }
}

resource "aws_instance" "api" {
  ami                         = data.aws_ami.al2023_arm.id
  instance_type               = var.tipo_instancia
  subnet_id                   = aws_subnet.publica[0].id
  vpc_security_group_ids      = [aws_security_group.api.id, aws_security_group.origen_https.id]
  iam_instance_profile        = aws_iam_instance_profile.api.name
  associate_public_ip_address = true # mientras arranca; luego queda la IP elástica

  # "standard": si se agotan los créditos de CPU la instancia va más lenta, pero AWS no cobra extra
  # (con "unlimited", el valor por defecto en t4g, sí)
  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_tokens                 = "required" # solo IMDSv2
    http_put_response_hop_limit = 2          # el contenedor también necesita las credenciales del rol
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    region          = var.region
    registro        = split("/", aws_ecr_repository.api.repository_url)[0]
    imagen          = aws_ecr_repository.api.repository_url
    grupo_logs      = aws_cloudwatch_log_group.api.name
    version_compose = var.version_compose
  })

  tags = { Name = "${local.nombre}-api" }

  lifecycle {
    # Una AMI nueva o un cambio del script de arranque no deben reemplazar la instancia sin que lo decidamos
    ignore_changes = [ami, user_data]
  }
}

# IP fija: el origen de CloudFront apunta a su nombre DNS público
resource "aws_eip" "api" {
  domain   = "vpc"
  instance = aws_instance.api.id
  tags     = { Name = "${local.nombre}-api" }
}
