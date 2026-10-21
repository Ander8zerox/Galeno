-- Registro de consultorios nuevos desde la web: la solicitud queda pendiente hasta que la persona abre el
-- enlace que llegó a su correo; solo entonces se crean el consultorio, la cuenta y la membresía.
CREATE TABLE registros_consultorio (
    id                 BINARY(16)   NOT NULL PRIMARY KEY,
    token_hash         VARCHAR(64)  NOT NULL, -- SHA-256 del token del enlace (el token no se guarda)
    email              VARCHAR(190) NOT NULL,
    nombre             VARCHAR(200) NOT NULL,
    password_hash      VARCHAR(100) NOT NULL, -- se ignora si el correo ya tenía cuenta: se pide su contraseña actual
    consultorio_nombre VARCHAR(200) NOT NULL,
    nit                VARCHAR(30),
    es_medico          BIT          NOT NULL,
    registro_medico    VARCHAR(50),
    especialidad       VARCHAR(120),
    terminos_version   VARCHAR(20)  NOT NULL,
    ip                 VARCHAR(45),
    creado_en          DATETIME(6)  NOT NULL,
    expira_en          DATETIME(6)  NOT NULL,
    confirmado_en      DATETIME(6),
    consultorio_id     BINARY(16),
    CONSTRAINT uk_registros_token UNIQUE (token_hash),
    INDEX ix_registros_email (email, creado_en),
    INDEX ix_registros_expira (expira_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Constancia de la aceptación de los términos y la política de tratamiento de datos (Ley 1581 de 2012)
ALTER TABLE consultorios
    ADD COLUMN terminos_version       VARCHAR(20),
    ADD COLUMN terminos_aceptados_en  DATETIME(6),
    ADD COLUMN terminos_aceptados_por BINARY(16);
