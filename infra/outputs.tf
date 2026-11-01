output "url" {
  value = local.url_web
}

output "nameservers" {
  description = "Los que deben estar en el registrador (Colombia Hosting)."
  value       = aws_route53_zone.principal.name_servers
}

output "variables_github" {
  description = "GitHub > Settings > Secrets and variables > Actions > Variables (para deploy.yml)."
  value = {
    AWS_REGION                 = var.region
    AWS_DEPLOY_ROLE_ARN        = aws_iam_role.deploy.arn
    ECR_REPOSITORY             = aws_ecr_repository.api.name
    EC2_INSTANCE_ID            = aws_instance.api.id
    WEB_BUCKET                 = aws_s3_bucket.web.bucket
    CLOUDFRONT_DISTRIBUTION_ID = aws_cloudfront_distribution.web.id
  }
}

output "consola_instancia" {
  description = "Abre una terminal en la EC2 por Session Manager (sin SSH)."
  value       = "aws ssm start-session --profile ${var.perfil_aws} --region ${var.region} --target ${aws_instance.api.id}"
}
