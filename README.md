# Galeno

Software para consultorios médicos con **redactor de fórmulas** y **apoyo diagnóstico** basados en IA (Claude en Amazon Bedrock).

| Parte | Tecnología |
|---|---|
| `backend/` | Java 21, Spring Boot 4.1, JPA/Hibernate 7, Flyway, MySQL 8 — monolito modular |
| `frontend/` | React 19, Vite, TypeScript, TanStack Query, cliente generado desde OpenAPI |
| `contracts/openapi.json` | Contrato de la API (exportado desde el backend) |
| `docs/` | Arquitectura y guía de despliegue en AWS |

## Correr en local

Requisitos: Java 21, Maven, Node 24, Docker.

```bash
# 1. Base de datos (puerto 3317)
docker compose -f docker-compose.yml up -d mysql

# 2. API en http://localhost:8090 (crea un consultorio y un médico de prueba)
cd backend && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# 3. Front en http://localhost:5180 (el proxy envía /api al backend)
cd frontend && npm install && npm run dev
```

Usuarios de prueba (la contraseña está en `backend/src/main/resources/application-local.yml`):

| Correo | Consultorio Demo | Clínica Sur Demo |
|---|---|---|
| `medico@demo.local` | Administrador + Médico | Médico (debe elegir consultorio al entrar) |
| `medico2@demo.local` | Médico | Administrador + Médico |
| `secretario@demo.local` | Secretario (atiende a la Dra. Demo) | — |
Swagger UI: http://localhost:8090/swagger-ui.html

> Se usa `-f docker-compose.yml` porque este equipo tiene la variable global `COMPOSE_FILE` apuntando a otro proyecto.

### IA real (Bedrock) en local

Por defecto `APP_AI_PROVIDER=fake` devuelve respuestas simuladas sin costo. Para usar Claude:

```bash
export APP_AI_PROVIDER=bedrock AWS_REGION=us-east-2 AWS_PROFILE=<tu-perfil>
```

Cada función tiene su modelo: `APP_AI_MODEL_FORMULA` y `APP_AI_MODEL_DIAGNOSTICO` (ambos por defecto `anthropic.claude-haiku-5-5`, el más económico; si el apoyo diagnóstico necesita más calidad, se sube solo ese a `anthropic.claude-sonnet-5-5`) y la profundidad con `APP_AI_EFFORT` (`low`…`max`, por defecto `medium`).

## Cambios en la API

1. Cambia el backend y arráncalo.
2. Exporta el contrato: `curl -s localhost:8090/v3/api-docs > contracts/openapi.json`
3. Regenera los tipos del front: `cd frontend && npm run gen:api`

El CI falla si el contrato y los tipos generados no coinciden.

## Variables de entorno (producción)

| Variable | Uso |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | RDS MySQL |
| `APP_SECURITY_JWT_SECRET` | Secreto de firma de JWT (≥ 32 bytes), desde SSM Parameter Store |
| `APP_AI_PROVIDER` | `bedrock` |
| `AWS_REGION` | Región de Bedrock (`us-east-2`, la del proyecto de AWS) |

Ver [docs/arquitectura.md](docs/arquitectura.md) y [docs/aws-capa-1.md](docs/aws-capa-1.md).
