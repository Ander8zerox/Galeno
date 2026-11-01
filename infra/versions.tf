terraform {
  # 1.11+ por los argumentos write-only (*_wo): las claves de la base de datos y del JWT no quedan en el estado
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  # Estado local (infra/terraform.tfstate, ignorado por git). Guárdalo con cuidado: tiene el secreto de origen de
  # CloudFront. Si algún día hay más de una persona aplicando, se pasa a un backend S3 con bloqueo.
}
