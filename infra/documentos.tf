# Documentos de la historia clínica (AlmacenDocumentos.EnS3). Se conservan al menos 15 años (Res. 1995 de 1999 y
# 839 de 2017): sin reglas de borrado y protegido contra destroy.

resource "aws_kms_key" "documentos" {
  description             = "Galeno prod - documentos de la historia clinica"
  enable_key_rotation     = true
  deletion_window_in_days = 30
}

resource "aws_kms_alias" "documentos" {
  name          = "alias/${local.nombre}-documentos"
  target_key_id = aws_kms_key.documentos.key_id
}

resource "aws_s3_bucket" "documentos" {
  bucket = "${local.nombre}-documentos-${local.cuenta}"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_ownership_controls" "documentos" {
  bucket = aws_s3_bucket.documentos.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "documentos" {
  bucket                  = aws_s3_bucket.documentos.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "documentos" {
  bucket = aws_s3_bucket.documentos.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documentos" {
  bucket = aws_s3_bucket.documentos.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.documentos.arn
    }
    bucket_key_enabled = true # una llamada a KMS por lote y no por objeto: menos costo
  }
}

data "aws_iam_policy_document" "documentos" {
  statement {
    sid       = "SoloTLS"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.documentos.arn, "${aws_s3_bucket.documentos.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  # Leer o escribir documentos: solo la API. La administración del bucket (esta política) sigue abierta a la cuenta,
  # para no quedar bloqueados.
  statement {
    sid       = "ContenidoSoloParaLaApi"
    effect    = "Deny"
    actions   = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject"]
    resources = ["${aws_s3_bucket.documentos.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "ArnNotEquals"
      variable = "aws:PrincipalArn"
      values   = [aws_iam_role.api.arn]
    }
  }

  # Nadie borra versiones: un borrado normal solo deja un marcador y el documento se puede recuperar
  statement {
    sid       = "SinBorrarVersiones"
    effect    = "Deny"
    actions   = ["s3:DeleteObjectVersion"]
    resources = ["${aws_s3_bucket.documentos.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }
}

resource "aws_s3_bucket_policy" "documentos" {
  bucket = aws_s3_bucket.documentos.id
  policy = data.aws_iam_policy_document.documentos.json

  depends_on = [aws_s3_bucket_public_access_block.documentos]
}
