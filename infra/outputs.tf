output "url" {
  value = local.url_web
}

output "nameservers" {
  description = "Los que deben estar en el registrador (Colombia Hosting)."
  value       = aws_route53_zone.principal.name_servers
}

# Lo que necesita tools/desplegar.sh (los lee con terraform output -raw)
output "region" {
  value = var.region
}

output "imagen_api" {
  value = aws_ecr_repository.api.repository_url
}

output "instancia_api" {
  value = aws_instance.api.id
}

output "bucket_web" {
  value = aws_s3_bucket.web.bucket
}

output "distribucion_web" {
  value = aws_cloudfront_distribution.web.id
}

output "consola_instancia" {
  description = "Abre una terminal en la EC2 por Session Manager (sin SSH)."
  value       = "aws ssm start-session --profile ${var.perfil_aws} --region ${var.region} --target ${aws_instance.api.id}"
}
