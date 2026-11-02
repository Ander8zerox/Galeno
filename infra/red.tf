# VPC sin NAT Gateway (costaría ~33 USD al mes): la EC2 va en una subred pública con IP propia y la base de datos en
# subredes privadas sin salida a internet.
resource "aws_vpc" "principal" {
  cidr_block           = "10.20.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = local.nombre }
}

# El security group por defecto queda sin reglas: nada lo usa por accidente
resource "aws_default_security_group" "principal" {
  vpc_id = aws_vpc.principal.id
}

resource "aws_internet_gateway" "principal" {
  vpc_id = aws_vpc.principal.id
  tags   = { Name = local.nombre }
}

resource "aws_subnet" "publica" {
  count = 2

  vpc_id            = aws_vpc.principal.id
  cidr_block        = cidrsubnet(aws_vpc.principal.cidr_block, 8, count.index)
  availability_zone = local.zonas[count.index]

  tags = { Name = "${local.nombre}-publica-${count.index}" }
}

resource "aws_subnet" "privada" {
  count = 2

  vpc_id            = aws_vpc.principal.id
  cidr_block        = cidrsubnet(aws_vpc.principal.cidr_block, 8, 10 + count.index)
  availability_zone = local.zonas[count.index]

  tags = { Name = "${local.nombre}-privada-${count.index}" }
}

resource "aws_route_table" "publica" {
  vpc_id = aws_vpc.principal.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.principal.id
  }

  tags = { Name = "${local.nombre}-publica" }
}

resource "aws_route_table_association" "publica" {
  count = 2

  subnet_id      = aws_subnet.publica[count.index].id
  route_table_id = aws_route_table.publica.id
}

resource "aws_route_table" "privada" {
  vpc_id = aws_vpc.principal.id
  tags   = { Name = "${local.nombre}-privada" }
}

resource "aws_route_table_association" "privada" {
  count = 2

  subnet_id      = aws_subnet.privada[count.index].id
  route_table_id = aws_route_table.privada.id
}

# Endpoint de S3 (gratis): los documentos van de la EC2 a S3 sin salir a internet
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.principal.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.publica.id]

  tags = { Name = "${local.nombre}-s3" }
}

# Solo CloudFront llega al puerto de la API. El prefix list también incluye distribuciones de otras cuentas: por eso
# la API exige además el encabezado X-Origen-Galeno (shared.web.OrigenCloudFrontFilter).
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "api" {
  name        = "${local.nombre}-api"
  description = "API de Galeno: solo CloudFront en el 8090, sin SSH (acceso por SSM)"
  vpc_id      = aws_vpc.principal.id
}

# HTTP directo a la API: solo hasta que el origen pase a HTTPS (var.origen_https)
resource "aws_vpc_security_group_ingress_rule" "api_desde_cloudfront" {
  count             = var.origen_https ? 0 : 1
  security_group_id = aws_security_group.api.id
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
  ip_protocol       = "tcp"
  from_port         = 8090
  to_port           = 8090
}

# Caddy (TLS) en el 443, también solo para CloudFront: el certificado se valida por DNS, sin abrir nada a internet.
# Va en un security group aparte: el prefix list de CloudFront cuenta como ~55 reglas y un grupo admite 60, así que
# no caben dos reglas con él en el mismo grupo (mientras dure la del 8090).
resource "aws_security_group" "origen_https" {
  name        = "${local.nombre}-origen-https"
  description = "Caddy en la EC2 de la API: solo CloudFront en el 443"
  vpc_id      = aws_vpc.principal.id
}

resource "aws_vpc_security_group_ingress_rule" "https_desde_cloudfront" {
  security_group_id = aws_security_group.origen_https.id
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "api_salida" {
  security_group_id = aws_security_group.api.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "bd" {
  name        = "${local.nombre}-bd"
  description = "RDS de Galeno: solo la API en el 3306"
  vpc_id      = aws_vpc.principal.id
}

resource "aws_vpc_security_group_ingress_rule" "bd_desde_api" {
  security_group_id            = aws_security_group.bd.id
  referenced_security_group_id = aws_security_group.api.id
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
}
