# La zona se creó con la CLI para poder cambiar los nameservers en el registrador antes de tener Terraform.
# El bloque import la adopta: no se crea otra (los nameservers cambiarían).
import {
  to = aws_route53_zone.principal
  id = var.zona_id
}

resource "aws_route53_zone" "principal" {
  name    = var.dominio
  comment = "Galeno produccion (luego la adopta Terraform)"

  lifecycle {
    prevent_destroy = true
  }
}

# La app en el dominio raíz y en www (la función de CloudFront redirige www al raíz)
resource "aws_route53_record" "web" {
  for_each = toset(flatten([for n in [var.dominio, "www.${var.dominio}"] : [for t in ["A", "AAAA"] : "${n}|${t}"]]))

  zone_id = aws_route53_zone.principal.zone_id
  name    = split("|", each.key)[0]
  type    = split("|", each.key)[1]

  alias {
    name                   = aws_cloudfront_distribution.web.domain_name
    zone_id                = aws_cloudfront_distribution.web.hosted_zone_id
    evaluate_target_health = false
  }
}
