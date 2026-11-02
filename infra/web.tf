# Frontend en S3 privado detrás de CloudFront, y /api/* hacia la EC2. Un solo dominio, como el proxy de Vite.

resource "aws_s3_bucket" "web" {
  bucket        = "${local.nombre}-web-${local.cuenta}"
  force_destroy = true # solo tiene el build de frontend/dist, que se publica en cada deploy
}

resource "aws_s3_bucket_ownership_controls" "web" {
  bucket = aws_s3_bucket.web.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket                  = aws_s3_bucket.web.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "${local.nombre}-web"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

data "aws_iam_policy_document" "web" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.web.arn}/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.web.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id
  policy = data.aws_iam_policy_document.web.json

  depends_on = [aws_s3_bucket_public_access_block.web]
}

# --- Certificado (us-east-1: lo exige CloudFront) ---

resource "aws_acm_certificate" "web" {
  provider = aws.us_east_1

  domain_name               = var.dominio
  subject_alternative_names = ["www.${var.dominio}"]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "validacion_certificado" {
  for_each = {
    for o in aws_acm_certificate.web.domain_validation_options : o.domain_name => o
  }

  zone_id         = aws_route53_zone.principal.zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  ttl             = 300
  records         = [each.value.resource_record_value]
  allow_overwrite = true
}

# Espera a que ACM emita el certificado: solo pasa cuando los nameservers del dominio ya son los de Route 53
resource "aws_acm_certificate_validation" "web" {
  provider = aws.us_east_1

  certificate_arn         = aws_acm_certificate.web.arn
  validation_record_fqdns = [for r in aws_route53_record.validacion_certificado : r.fqdn]
}

# --- Distribución ---

data "aws_cloudfront_cache_policy" "optimizada" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_response_headers_policy" "seguridad" {
  name = "Managed-SecurityHeadersPolicy" # HSTS, nosniff, X-Frame-Options...
}

# La API no se guarda en caché: Spring responde Cache-Control: no-store y con TTL mínimo 0 CloudFront lo respeta.
# El TTL máximo es 1 (y no 0) porque solo así la política puede reenviar Authorization.
resource "aws_cloudfront_cache_policy" "api" {
  name        = "${local.nombre}-api"
  min_ttl     = 0
  default_ttl = 0
  max_ttl     = 1

  parameters_in_cache_key_and_forwarded_to_origin {
    headers_config {
      header_behavior = "whitelist"
      headers {
        items = ["Authorization"]
      }
    }
    cookies_config {
      cookie_behavior = "none" # la API no usa cookies
    }
    query_strings_config {
      query_string_behavior = "all"
    }
  }
}

# Todos los encabezados del navegador, más la IP real del visitante (shared.web.IpCliente)
resource "aws_cloudfront_origin_request_policy" "api" {
  name = "${local.nombre}-api"

  headers_config {
    header_behavior = "allViewerAndWhitelistCloudFront"
    headers {
      items = ["CloudFront-Viewer-Address"]
    }
  }
  cookies_config {
    cookie_behavior = "none"
  }
  query_strings_config {
    query_string_behavior = "none" # ya van por la política de caché
  }
}

resource "aws_cloudfront_function" "spa" {
  name    = "${local.nombre}-spa"
  runtime = "cloudfront-js-2.0"
  comment = "Rutas del SPA a index.html y www al dominio raiz"
  code    = file("${path.module}/funcion-spa.js")
  publish = true
}

resource "aws_cloudfront_distribution" "web" {
  enabled             = true
  comment             = "Galeno prod"
  aliases             = [var.dominio, "www.${var.dominio}"]
  default_root_object = "index.html"
  http_version        = "http2and3"
  is_ipv6_enabled     = true
  price_class         = "PriceClass_100" # Norteamérica y Europa: Colombia sale por Miami, y es la opción más barata

  origin {
    origin_id                = "web"
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.web.id
  }

  # Entre CloudFront y la EC2: HTTPS a Caddy en el 443 con var.origen_https; si no, HTTP al 8090 (ver
  # docs/aws-capa-1.md, "Origen de la API")
  origin {
    origin_id   = "api"
    domain_name = var.origen_https ? aws_route53_record.origen.fqdn : aws_eip.api.public_dns

    custom_origin_config {
      http_port              = 8090
      https_port             = 443
      origin_protocol_policy = var.origen_https ? "https-only" : "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
      origin_read_timeout    = 60
    }

    custom_header {
      name  = "X-Origen-Galeno"
      value = random_password.origen.result
    }
  }

  default_cache_behavior {
    target_origin_id           = "web"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.optimizada.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.seguridad.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa.arn
    }
  }

  ordered_cache_behavior {
    path_pattern               = "/api/*"
    target_origin_id           = "api"
    viewer_protocol_policy     = "https-only"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = aws_cloudfront_cache_policy.api.id
    origin_request_policy_id   = aws_cloudfront_origin_request_policy.api.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.seguridad.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.web.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}
