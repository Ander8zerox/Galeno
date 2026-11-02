# Guía de operación de Galeno en AWS

Cómo se opera Galeno en producción hoy (noviembre de 2026). El diseño y el porqué de cada pieza están en
[aws-capa-1.md](aws-capa-1.md) y [arquitectura.md](arquitectura.md).

Todos los comandos son para **Git Bash** en Windows, desde la carpeta del proyecto (`C:\Proyectos\Galeno`).

| Dato | Valor |
|---|---|
| Dominio | https://galenum.com.co |
| Proyecto de AWS | Cuenta 821211779252, región **us-east-2** (Ohio) |
| Perfil de la CLI | `galeno` |
| Infraestructura | Terraform en `infra/`, estado local en `infra/terraform.tfstate` (no está en Git: no lo borres) |

---

## 1. Iniciar sesión en AWS desde la terminal (y trabajar con Claude)

Galeno no usa llaves de acceso guardadas. Se entra a AWS con `aws login`, que abre el navegador y deja una sesión
temporal en el computador. Esa misma sesión la usan `tools/desplegar.sh`, Terraform y Claude Code cuando ejecuta
comandos de AWS por ti.

### Una sola vez: atajos en Git Bash

Hay dos AWS CLI instaladas y la vieja (2.33) va primero en el PATH, pero no entiende `aws login`. Para usar siempre
la nueva, y para tener Terraform a mano, agrega esto al final de `~/.bashrc` y abre una terminal nueva:

```bash
alias aws='"$LOCALAPPDATA/Programs/Amazon/AWSCLIV2/aws.exe"'
alias terraform='"$(ls "$LOCALAPPDATA"/Microsoft/WinGet/Packages/Hashicorp.Terraform_*/terraform.exe | head -1)"'
export AWS_PROFILE=galeno
```

Compruébalo con `aws --version`: debe decir 2.32 o más (hoy 2.38.4).

### Cada vez que empiezas a trabajar

```bash
aws login --profile galeno
```

1. Se abre el navegador: entra con tu cuenta del proyecto (la misma de [AWS Settings](https://settings.aws.com/)).
2. Al terminar, la terminal queda autenticada. La sesión dura **12 horas**; después se repite `aws login`.
3. Para comprobar que la sesión sirve:

```bash
aws sts get-caller-identity --profile galeno
```

Si responde con la cuenta `821211779252`, ya está. Si dice que la sesión venció o no hay credenciales, repite el login.

### Con Claude Code

- Haz el `aws login` **tú**, en tu terminal, antes de pedirle a Claude algo de AWS. Claude no inicia sesión por ti
  ni debe recibir llaves de acceso.
- Claude ya sabe que el proyecto usa el perfil `galeno` y la región `us-east-2`.
- Si un comando de Claude falla con "Access Denied" de repente, revisa primero si la sesión venció (`aws login`).
  Si la sesión está bien y el error sigue, revisa el **límite de gasto** en AWS Settings → Billing: al superarlo,
  AWS pausa el proyecto.
- Por seguridad, Claude no ejecuta `terraform apply` ni `tools/desplegar.sh` por su cuenta: te deja el comando
  listo y lo corres tú.

---

## 2. Desplegar cambios nuevos

### Requisitos

- **Docker Desktop abierto** (construye la imagen de la API).
- Node instalado (construye el frontend).
- Sesión de AWS vigente (punto 1).

### Flujo normal

1. **Trabaja en una rama** y abre un PR hacia `main`. El CI de GitHub (`ci.yml`) compila y prueba.
2. **Haz merge del PR** en GitHub.
3. **Despliega desde `main` actualizado:**

```bash
git switch main && git pull
tools/desplegar.sh
```

El script hace todo en orden y se detiene si algo falla:

| Paso | Qué hace |
|---|---|
| Sesión de AWS | Comprueba que `aws login` sigue vigente |
| API: imagen → ECR | Construye la imagen de la API para ARM (la EC2 es t4g) y la sube a ECR con dos etiquetas: el commit y `latest` |
| API: actualizar la EC2 | Por SSM (sin SSH) la EC2 lee la configuración de Parameter Store, descarga la imagen nueva y la arranca. Espera hasta 3 minutos a que la API responda; si no, muestra sus últimos logs |
| Frontend: build | `npm install` y `npm run build` |
| Frontend: S3 + CloudFront | Sube la web al bucket y borra la caché de CloudFront para que todos vean la versión nueva |

Los cambios de base de datos (migraciones de Flyway en `backend/src/main/resources/db/migration`) se aplican solos
cuando la API arranca.

### Solo una parte

```bash
tools/desplegar.sh --solo-api   # cambios solo en backend/ (o para reiniciar la API y que lea configuración nueva)
tools/desplegar.sh --solo-web   # cambios solo en frontend/
```

### Detalles a tener en cuenta

- Si hay cambios **sin commit**, el script avisa y despliega lo que está en disco (la versión queda como
  `<commit>-local`). Lo sano es desplegar siempre desde `main` limpio.
- Puedes desplegar con `npm run dev` abierto.
- La primera construcción de la imagen tarda varios minutos; las siguientes usan caché.
- **Volver a una versión anterior:** `git switch --detach <commit>` y `tools/desplegar.sh`. Ojo: las migraciones de
  base de datos no se deshacen solas.

### Cambios de infraestructura (Terraform)

Para cambios en `infra/` (permisos, parámetros, recursos):

```bash
cd infra
terraform plan -out=cambio.tfplan   # revisa qué crea, cambia o destruye
terraform apply cambio.tfplan
```

Antes de aplicar, lee el resumen `Plan: X to add, Y to change, Z to destroy`. Si aparece algo que se **destruye** y
no lo esperabas, no apliques. La base de datos y el bucket de documentos están protegidos contra borrado, pero
igual hay que leer el plan.

---

## 3. Operadores de la plataforma

Los operadores son quienes entran a la consola de la plataforma (revisar pagos, administrar suscripciones). No es
un rol que se pida desde la web: lo define una lista en la configuración, y **la lista manda**. Al arrancar, la API
da acceso a quien está en ella y se lo quita a quien no está.

La lista está en `infra/terraform.tfvars`. Ese archivo no va a Git; si no existe, créalo con este contenido:

```hcl
operadores_plataforma = "andersoncabezas31@gmail.com"
```

### Agregar un operador

1. **Agrega el correo a la lista**, separado por comas:

   ```hcl
   operadores_plataforma = "andersoncabezas31@gmail.com,otra.persona@ejemplo.com"
   ```

2. **Aplica Terraform** (debe salir `1 to change`, el parámetro `APP_PLATAFORMA_OPERADORES`):

   ```bash
   cd infra && terraform plan -out=operadores.tfplan && terraform apply operadores.tfplan && cd ..
   ```

3. **Reinicia la API** para que lea la lista nueva:

   ```bash
   tools/desplegar.sh --solo-api
   ```

Qué pasa al arrancar:

- **Si la persona no tiene cuenta en Galeno:** la API se la crea (sin consultorio) y le envía el correo
  "Defina su contraseña de Galeno". El enlace dura una hora; si vence, la persona pide otro con
  "¿Olvidó su contraseña?" en https://galenum.com.co.
- **Si ya tiene cuenta** (por ejemplo, es administradora de un consultorio): queda como operador y entra a la
  plataforma con su misma contraseña.

### Quitar un operador

1. Borra su correo de `operadores_plataforma` en `infra/terraform.tfvars`.
2. Aplica Terraform (paso 2 de arriba).
3. Reinicia la API con `tools/desplegar.sh --solo-api`.

Al arrancar, la API le quita el acceso; si tenía la consola abierta, su sesión deja de servir en la siguiente
acción. Su cuenta de Galeno no se borra: si también usa un consultorio, lo sigue usando.

**Cuidado:** si la lista queda vacía, nadie entra a la consola, tú tampoco. Para recuperar el acceso, vuelve a poner
tu correo, aplica Terraform y reinicia la API.

### Cómo comprobarlo

Cada vez que se da o se quita un acceso queda registrado en `auditoria_plataforma`, con las acciones
`ACCESO_OTORGADO` y `ACCESO_RETIRADO`. En los logs de la API (punto 5) busca `app.plataforma.operadores`.

### Buenas prácticas

- Solo quien de verdad revisa pagos: un operador puede aprobar pagos, regalar días y suspender consultorios.
- Un correo por persona, nunca cuentas compartidas, para que la auditoría diga quién hizo qué.
- Contraseñas largas y únicas: la consola todavía no tiene MFA.

---

## 4. Rutas de acceso

### Galeno (consultorios)

| Para qué | Ruta |
|---|---|
| Ingresar (médicos, administradores, secretarios) | https://galenum.com.co |
| Registrar un consultorio nuevo | https://galenum.com.co/registro |
| Olvidé mi contraseña | https://galenum.com.co/recuperar |
| Política de datos | https://galenum.com.co/politica-datos |

Dentro de la aplicación, según los permisos de cada persona: `/agenda`, `/pacientes`, `/equipo`, `/importar` y
`/suscripcion`. `www.galenum.com.co` redirige al dominio sin `www`.

### Galeno Plataforma (operadores de Galeno)

| Para qué | Ruta |
|---|---|
| Consola de la plataforma | https://galenum.com.co/plataforma |

Tiene su propio inicio de sesión. Solo entran las cuentas de la lista de operadores (punto 3).

### Galeno Partners (aliados que refieren consultorios)

| Para qué | Ruta |
|---|---|
| Ingresar al panel del partner | https://galenum.com.co/partner |
| Inscribirse como partner | https://galenum.com.co/partner/inscripcion |
| Condiciones del programa | https://galenum.com.co/partner/condiciones |

### Administración en AWS

| Para qué | Dónde |
|---|---|
| Proyecto, facturación, límite de gasto, miembros del equipo | [AWS Settings](https://settings.aws.com/) |
| Recursos (EC2, RDS, S3, CloudFront, SES, logs) | [Consola de AWS](https://console.aws.amazon.com/) en la región **us-east-2 (Ohio)** |
| Presupuestos y costos | Consola de AWS → Billing and Cost Management |
| Repositorio | https://github.com/Ander8zerox/Galeno |

---

## 5. Logs de la aplicación

La API envía sus logs a **CloudWatch Logs**, al grupo `/galeno/prod/api`. Se guardan **30 días**. Cada línea es un
JSON con la hora, el nivel (`INFO`, `WARN`, `ERROR`), la clase que lo escribió y el mensaje.

### En la consola de AWS

1. Entra a la [consola de AWS](https://console.aws.amazon.com/) y, arriba a la derecha, confirma que la región sea
   **US East (Ohio) us-east-2**.
2. Busca **CloudWatch** y entra.
3. Menú izquierdo: **Logs → Log groups** → `/galeno/prod/api` → stream `api`.

**Ver en vivo:** en el grupo, botón **Start tailing** (Live Tail). Muestra las líneas a medida que llegan; útil
mientras pruebas algo en la web.

**Buscar:** menú izquierdo **Logs → Logs Insights**, selecciona `/galeno/prod/api`, elige el rango de tiempo y usa
una de estas consultas:

```
# Errores de las últimas horas
fields @timestamp, log.logger, message
| filter log.level = "ERROR"
| sort @timestamp desc
| limit 50
```

```
# Correos (enviados y rechazados por SES)
fields @timestamp, log.level, message
| filter log.logger like /Correo/
| sort @timestamp desc
```

```
# Arranques de la API y cambios de operadores
fields @timestamp, message
| filter message like /Started GalenoApplication|app.plataforma.operadores/
| sort @timestamp desc
```

Logs Insights cobra por datos revisados (0,005 USD por GB). Con el volumen actual es menos de un centavo por
consulta, pero conviene usar rangos de tiempo cortos.

### Desde la terminal

```bash
MSYS_NO_PATHCONV=1 aws logs tail /galeno/prod/api --since 30m --region us-east-2            # últimos 30 minutos
MSYS_NO_PATHCONV=1 aws logs tail /galeno/prod/api --follow --region us-east-2               # en vivo (Ctrl+C para salir)
MSYS_NO_PATHCONV=1 aws logs tail /galeno/prod/api --since 2h --filter-pattern ERROR --region us-east-2
```

`MSYS_NO_PATHCONV=1` evita que Git Bash convierta `/galeno/prod/api` en una ruta de Windows.

### Entrar al servidor (casos excepcionales)

Desde la consola: **EC2 → Instances →** la instancia de Galeno **→ Connect → Session Manager → Connect**. Abre una
terminal en el servidor sin SSH ni puertos abiertos. Ahí, `sudo docker ps` y `sudo docker logs --tail 100
galeno-api-1`. Para el día a día bastan los logs de CloudWatch.

---

## 6. Pendientes del plan

### Para terminar la salida a producción

| # | Pendiente | Por qué importa |
|---|---|---|
| — | **Activar Claude en Amazon Bedrock** (us-east-2) y hacer una invocación real | Las funciones de IA no responden hasta activarlo; el permiso `bedrock-mantle:CreateInference` aún no se ha probado en producción |
| 12 | **Pruebas en producción**: registrar un consultorio desde cero, agenda, historia y PDF, documentos en S3, reportar y aprobar un pago, inscripción de un partner | Confirmar de punta a punta que todo funciona en AWS y no solo en local |
| — | Revisar el gasto después de la primera semana (esperado: 35–50 USD al mes) | Confirmar que el costo real coincide con lo estimado; límite de gasto en 60 USD, presupuesto en 50 USD |

### Antes de recibir pacientes reales

- **HTTPS entre CloudFront y el servidor.** Hoy ese tramo va por HTTP dentro de AWS (ver
  [aws-capa-1.md](aws-capa-1.md), "Origen de la API"). Opciones: origen VPC de CloudFront, o Caddy con Let's
  Encrypt en la EC2.
- **La API debe saber que el usuario llegó por HTTPS.** Hoy algunas respuestas anuncian `http://galenum.com.co`.
- **MFA** para los operadores de la plataforma, y después para los consultorios.
- **Revisión legal** de los términos y de la política de datos (hoy un borrador en `/politica-datos`). En partners,
  también la revisión contable de las condiciones.

### Siguiente en el producto

El detalle está en [plan.md](plan.md), sección "Siguiente". Lo primero es el **motor de formatos clínicos**,
empezando por **odontología** (odontograma, periodontograma, plan de tratamiento, consentimientos). Después vienen
los demás pendientes: invitaciones por correo, pasarela de pagos y factura electrónica, recordatorios de citas,
portal del paciente, RIPS y facturación electrónica.
