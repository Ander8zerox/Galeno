# Capa 1 de Galeno en AWS (ver docs/aws-capa-1.md). Todo lo regional va en var.region; lo único en us-east-1 es el
# certificado de CloudFront, que AWS exige ahí.

provider "aws" {
  region  = var.region
  profile = var.perfil_aws

  default_tags {
    tags = local.etiquetas
  }
}

provider "aws" {
  alias   = "us_east_1"
  region  = "us-east-1"
  profile = var.perfil_aws

  default_tags {
    tags = local.etiquetas
  }
}

data "aws_caller_identity" "actual" {}

data "aws_availability_zones" "disponibles" {
  state = "available"
}

locals {
  nombre  = "galeno-prod"
  cuenta  = data.aws_caller_identity.actual.account_id
  zonas   = slice(data.aws_availability_zones.disponibles.names, 0, 2)
  url_web = "https://${var.dominio}"

  etiquetas = {
    Proyecto = "galeno"
    Ambiente = "prod"
    Gestion  = "terraform"
  }
}
