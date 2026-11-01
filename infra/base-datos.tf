resource "aws_db_subnet_group" "principal" {
  name       = local.nombre
  subnet_ids = aws_subnet.privada[*].id
}

# Lo mismo que el MySQL local (docker-compose.yml), y solo conexiones cifradas
resource "aws_db_parameter_group" "mysql" {
  name   = "${local.nombre}-mysql84"
  family = "mysql8.4"

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }
  parameter {
    name  = "collation_server"
    value = "utf8mb4_0900_ai_ci"
  }
  parameter {
    name  = "time_zone"
    value = "UTC"
  }
  parameter {
    name  = "require_secure_transport"
    value = "1"
  }
}

# Clave efímera: va a RDS y a SSM como argumento write-only, así que nunca queda en el estado ni en el plan.
# Para rotarla, sube local.version_clave_bd (secretos.tf): se genera otra y se escribe en los dos lugares.
ephemeral "random_password" "bd" {
  length  = 32
  special = false
}

resource "aws_db_instance" "principal" {
  identifier     = local.nombre
  engine         = "mysql"
  engine_version = "8.4"
  instance_class = var.clase_bd

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true # con la llave administrada aws/rds (sin costo)

  db_name             = "galeno"
  username            = "galeno"
  password_wo         = ephemeral.random_password.bd.result
  password_wo_version = local.version_clave_bd

  db_subnet_group_name   = aws_db_subnet_group.principal.name
  vpc_security_group_ids = [aws_security_group.bd.id]
  parameter_group_name   = aws_db_parameter_group.mysql.name
  publicly_accessible    = false
  multi_az               = false # Capa 3

  backup_retention_period    = 7
  backup_window              = "07:00-07:30"         # 2:00 a. m. en Colombia
  maintenance_window         = "sun:08:00-sun:08:30" # domingo 3:00 a. m. en Colombia
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  # Historias clínicas: ni un destroy por error la borra, y si se elimina a propósito queda una última copia
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.nombre}-final"

  performance_insights_enabled = false
}
