#!/usr/bin/env bash
# Despliega Galeno en AWS desde este computador, con la sesión de `aws login` (sin llaves guardadas en ningún lado).
# El proyecto de AWS no permite el proveedor OIDC que necesitaría GitHub Actions (ver docs/aws-capa-1.md, "6. Despliegue").
#
#   tools/desplegar.sh              # API y frontend
#   tools/desplegar.sh --solo-api
#   tools/desplegar.sh --solo-web
#
# Requisitos: Docker Desktop, Node, la AWS CLI 2.32+ y Terraform (lee los datos de infra/ con terraform output).
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash: que no convierta "/opt/..." ni "/*" en rutas de Windows

# En Git Bash, pwd -W da C:/Proyectos/Galeno: terraform, aws y docker son programas de Windows y, sin la conversión
# automática de rutas, no entenderían /c/Proyectos/Galeno
raiz="$(cd "$(dirname "$0")/.." && (pwd -W 2>/dev/null || pwd))"
perfil="${AWS_PROFILE:-galeno}"
api=1
web=1
case "${1:-}" in
  --solo-api) web=0 ;;
  --solo-web) api=0 ;;
  "") ;;
  *) echo "Uso: $0 [--solo-api | --solo-web]" >&2; exit 2 ;;
esac

# La CLI nueva primero: la 2.33 que también está en el PATH no entiende las sesiones de `aws login`
aws_cli="${AWS_CLI:-}"
if [[ -z "$aws_cli" ]]; then
  nueva="${LOCALAPPDATA:-}/Programs/Amazon/AWSCLIV2/aws.exe"
  if [[ -n "${LOCALAPPDATA:-}" && -x "$nueva" ]]; then aws_cli="$nueva"; else aws_cli="aws"; fi
fi
terraform="${TERRAFORM:-$(command -v terraform || ls "${LOCALAPPDATA:-}"/Microsoft/WinGet/Packages/Hashicorp.Terraform_*/terraform.exe 2>/dev/null | head -1 || true)}"
[[ -n "$terraform" ]] || { echo "No encuentro terraform: instálalo o define TERRAFORM=<ruta>" >&2; exit 1; }

aws() { "$aws_cli" --profile "$perfil" "$@"; }
salida() { "$terraform" -chdir="$raiz/infra" output -raw "$1"; }

paso() { printf '\n== %s\n' "$*"; }

paso "Sesión de AWS"
aws sts get-caller-identity --query Arn --output text >/dev/null 2>&1 \
  || { echo "La sesión venció: corre  aws login --profile $perfil  y vuelve a intentar." >&2; exit 1; }
region="$(salida region)"
echo "perfil $perfil, región $region"

version="$(git -C "$raiz" rev-parse --short HEAD)"
if [[ -n "$(git -C "$raiz" status --porcelain --untracked-files=no)" ]]; then
  version="$version-local"
  echo "Ojo: hay cambios sin commit; se despliega lo que está en disco ($version)."
fi

if (( api )); then
  imagen="$(salida imagen_api)"
  instancia="$(salida instancia_api)"

  paso "API: imagen linux/arm64 -> ECR ($version)"
  # Configuración de Docker temporal: el token de ECR es más largo de lo que acepta el Administrador de credenciales
  # de Windows ("The stub received bad data"). Se borra al terminar y no toca ~/.docker.
  # La entrada vacía en "auths" es necesaria: con un config sin credenciales Docker vuelve a elegir wincred por su cuenta.
  docker_tmp="$(mktemp -d)"
  trap 'rm -rf "$docker_tmp"' EXIT
  printf '{"auths":{"%s":{}}}' "${imagen%%/*}" > "$docker_tmp/config.json"
  DOCKER_CONFIG="$(cygpath -m "$docker_tmp" 2>/dev/null || echo "$docker_tmp")"
  export DOCKER_CONFIG
  aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "${imagen%%/*}"
  docker buildx build --platform linux/arm64 --push -t "$imagen:$version" -t "$imagen:latest" "$raiz/backend"

  # Caddy (TLS del origen) solo se reconstruye si cambió su Dockerfile: la etiqueta es el hash del archivo
  imagen_caddy="$(salida imagen_caddy)"
  etiqueta_caddy="$(sha256sum "$raiz/infra/caddy/Dockerfile" | cut -c1-12)"
  if aws ecr describe-images --region "$region" --repository-name "${imagen_caddy##*/}" \
      --image-ids imageTag="$etiqueta_caddy" >/dev/null 2>&1; then
    echo "Caddy $etiqueta_caddy ya está en ECR."
  else
    paso "Caddy: imagen linux/arm64 -> ECR ($etiqueta_caddy)"
    docker buildx build --platform linux/arm64 --push -t "$imagen_caddy:$etiqueta_caddy" "$raiz/infra/caddy"
  fi

  paso "API: actualizar la EC2 por SSM"
  # Primero se suben docker-compose.yml y el Caddyfile del repo (infra/instancia), con los valores de terraform output.
  # Con el origen todavía en HTTP la API se publica en todas las interfaces (el security group solo deja pasar a
  # CloudFront); con el origen HTTPS, solo en la máquina.
  if [[ "$(salida origen_https)" == "true" ]]; then puerto_api="127.0.0.1:8090"; else puerto_api="8090"; fi
  compose_b64="$(sed -e "s|__IMAGEN_API__|$imagen|" -e "s|__IMAGEN_CADDY__|$imagen_caddy:$etiqueta_caddy|" \
    -e "s|__REGION__|$region|g" -e "s|__GRUPO_LOGS__|$(salida grupo_logs)|g" -e "s|__DOMINIO__|$(salida dominio)|" \
    -e "s|__PUERTO_API__|$puerto_api|" "$raiz/infra/instancia/docker-compose.yml" | base64 -w0)"
  caddyfile_b64="$(base64 -w0 < "$raiz/infra/instancia/Caddyfile")"
  # desplegar.sh (lo deja el arranque de la instancia) lee la configuración de SSM y levanta las imágenes nuevas.
  # Después espera hasta 3 minutos a que la API responda; si no, muestra sus últimos logs.
  subir="echo $compose_b64 | base64 -d > /opt/galeno/docker-compose.yml && echo $caddyfile_b64 | base64 -d > /opt/galeno/Caddyfile"
  comando='/opt/galeno/desplegar.sh && for i in $(seq 1 60); do curl -sf http://localhost:8090/actuator/health && exit 0; sleep 3; done; docker logs --tail 80 galeno-api-1; exit 1'
  id="$(aws ssm send-command --region "$region" --instance-ids "$instancia" --document-name AWS-RunShellScript \
    --timeout-seconds 600 --comment "deploy $version" \
    --parameters "{\"commands\":[\"$subir\",\"$comando\"]}" --query Command.CommandId --output text)"
  aws ssm wait command-executed --region "$region" --command-id "$id" --instance-id "$instancia" || true
  aws ssm get-command-invocation --region "$region" --command-id "$id" --instance-id "$instancia" \
    --query '[StandardOutputContent,StandardErrorContent]' --output text | tail -40
  estado="$(aws ssm get-command-invocation --region "$region" --command-id "$id" --instance-id "$instancia" --query Status --output text)"
  [[ "$estado" == "Success" ]] || { echo "La API no quedó arriba (estado SSM: $estado)." >&2; exit 1; }
  echo "API arriba."
fi

if (( web )); then
  bucket="$(salida bucket_web)"
  distribucion="$(salida distribucion_web)"

  paso "Frontend: build"
  # npm install y no npm ci: npm ci borra node_modules entero y falla (EPERM) si `npm run dev` está corriendo, porque
  # Windows no deja borrar el binario de rolldown en uso. install solo cambia lo que difiera del package-lock.
  (cd "$raiz/frontend" && npm install --no-audit --no-fund && npm run build)

  paso "Frontend: S3 + CloudFront"
  # Los archivos de assets/ llevan hash en el nombre: caché de un año. El resto (index.html) se revalida siempre.
  aws s3 sync "$raiz/frontend/dist/assets" "s3://$bucket/assets" --region "$region" --delete \
    --cache-control "public,max-age=31536000,immutable"
  aws s3 sync "$raiz/frontend/dist" "s3://$bucket" --region "$region" --delete --exclude "assets/*" \
    --cache-control "no-cache"
  aws cloudfront create-invalidation --distribution-id "$distribucion" --paths "/*" --query Invalidation.Id --output text
fi

paso "Listo: $(salida url)"
