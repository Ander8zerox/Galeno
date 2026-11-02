variable "region" {
  description = "Región del proyecto de AWS (nueva experiencia): todo recurso regional va aquí."
  type        = string
  default     = "us-east-2"
}

variable "perfil_aws" {
  description = "Perfil de la CLI con la sesión de `aws login`."
  type        = string
  default     = "galeno"
}

variable "dominio" {
  description = "Dominio de producción. La app queda en https://<dominio> y www redirige ahí."
  type        = string
  default     = "galenum.com.co"
}

variable "zona_id" {
  description = "Zona de Route 53 creada a mano antes de Terraform; se adopta con un bloque import."
  type        = string
  default     = "Z053499928R750F08R0N8"
}

variable "repositorio_github" {
  description = "owner/repo que puede desplegar con OIDC (solo desde el environment production)."
  type        = string
  default     = "Ander8zerox/Galeno"
}

variable "tipo_instancia" {
  description = "EC2 de la API (ARM: la imagen se construye para linux/arm64)."
  type        = string
  default     = "t4g.small"
}

variable "clase_bd" {
  description = "Clase de la instancia de RDS."
  type        = string
  default     = "db.t4g.micro"
}

variable "operadores_plataforma" {
  description = "Correos con acceso a la consola de la plataforma, separados por coma (APP_PLATAFORMA_OPERADORES)."
  type        = string
  default     = ""
}

variable "version_compose" {
  description = "Versión del plugin de Docker Compose que se instala en la EC2."
  type        = string
  default     = "v2.32.4"
}

variable "origen_https" {
  description = <<-EOT
    CloudFront llega a la API por HTTPS (Caddy en el 443) en vez de HTTP al 8090. Se pasa a true después de desplegar
    Caddy y comprobar que tiene su certificado (ver docs/guia-operacion.md); con false se vuelve a HTTP.
  EOT
  type        = bool
  default     = false
}
