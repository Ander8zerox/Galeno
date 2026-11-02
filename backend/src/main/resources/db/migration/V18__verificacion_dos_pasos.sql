-- Verificación en dos pasos (TOTP: Google Authenticator, Microsoft Authenticator, 1Password...). Es de la persona, no
-- del consultorio: estas tablas no llevan tenant. Obligatoria para operadores de la plataforma y administradores de
-- consultorio; opcional para los demás (ver identidad.MfaService).

-- Secretos cifrados con AES-GCM (APP_MFA_CLAVE). secreto_pendiente: el que se está configurando y aún no se confirmó
-- con un código. ultimo_paso: el intervalo de 30 s del último código aceptado, para que no se pueda usar dos veces.
CREATE TABLE mfa_usuarios (
    usuario_id         BINARY(16)   NOT NULL PRIMARY KEY,
    secreto            VARCHAR(200),
    secreto_pendiente  VARCHAR(200),
    activo             BIT          NOT NULL,
    activado_en        DATETIME(6),
    ultimo_paso        BIGINT       NOT NULL DEFAULT 0,
    intentos_fallidos  INT          NOT NULL DEFAULT 0,
    bloqueado_hasta    DATETIME(6),
    CONSTRAINT fk_mfa_usuarios_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- De un solo uso, para cuando no se tiene el celular. Solo el HMAC del código.
CREATE TABLE mfa_codigos_recuperacion (
    id          BINARY(16)  NOT NULL PRIMARY KEY,
    usuario_id  BINARY(16)  NOT NULL,
    codigo_hash VARCHAR(64) NOT NULL,
    creado_en   DATETIME(6) NOT NULL,
    usado_en    DATETIME(6),
    CONSTRAINT fk_mfa_codigos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    INDEX ix_mfa_codigos_usuario (usuario_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "Recordar este equipo 30 días": el navegador guarda el token y lo manda en el login; aquí solo su hash.
CREATE TABLE mfa_equipos (
    id         BINARY(16)  NOT NULL PRIMARY KEY,
    usuario_id BINARY(16)  NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    creado_en  DATETIME(6) NOT NULL,
    expira_en  DATETIME(6) NOT NULL,
    CONSTRAINT uk_mfa_equipos_token UNIQUE (token_hash),
    CONSTRAINT fk_mfa_equipos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    INDEX ix_mfa_equipos_usuario (usuario_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
