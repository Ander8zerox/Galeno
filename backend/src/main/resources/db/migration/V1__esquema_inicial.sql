-- Convenciones: ids UUID v7 en BINARY(16), fechas en UTC con DATETIME(6), utf8mb4,
-- tenant_id en toda tabla clínica. Compatible con RDS/Aurora MySQL 8.

CREATE TABLE consultorios (
    id         BINARY(16)   NOT NULL PRIMARY KEY,
    nombre     VARCHAR(200) NOT NULL,
    nit        VARCHAR(30),
    creado_en  DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE usuarios (
    id              BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id       BINARY(16)   NOT NULL,
    email           VARCHAR(190) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    nombre          VARCHAR(200) NOT NULL,
    rol             VARCHAR(20)  NOT NULL,
    registro_medico VARCHAR(50),
    activo          BIT          NOT NULL,
    creado_en       DATETIME(6)  NOT NULL,
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT fk_usuarios_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE pacientes (
    id               BINARY(16)    NOT NULL PRIMARY KEY,
    tenant_id        BINARY(16)    NOT NULL,
    tipo_documento   VARCHAR(5)    NOT NULL,
    numero_documento VARCHAR(30)   NOT NULL,
    nombres          VARCHAR(120)  NOT NULL,
    apellidos        VARCHAR(120)  NOT NULL,
    fecha_nacimiento DATE          NOT NULL,
    sexo             VARCHAR(1)    NOT NULL,
    telefono         VARCHAR(30),
    email            VARCHAR(190),
    alergias         VARCHAR(2000),
    antecedentes     VARCHAR(4000),
    creado_en        DATETIME(6)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    CONSTRAINT uk_pacientes_documento UNIQUE (tenant_id, tipo_documento, numero_documento),
    CONSTRAINT fk_pacientes_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    INDEX ix_pacientes_nombre (tenant_id, apellidos, nombres)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Cola y auditoría de IA
CREATE TABLE ai_jobs (
    id             BINARY(16)    NOT NULL PRIMARY KEY,
    tenant_id      BINARY(16)    NOT NULL,
    tipo           VARCHAR(30)   NOT NULL,
    estado         VARCHAR(20)   NOT NULL,
    paciente_id    BINARY(16),
    solicitado_por BINARY(16)    NOT NULL,
    version_prompt VARCHAR(60)   NOT NULL,
    entrada        JSON          NOT NULL,
    resultado      JSON,
    error          VARCHAR(1000),
    modelo         VARCHAR(100),
    tokens_entrada BIGINT,
    tokens_salida  BIGINT,
    intentos       INT           NOT NULL DEFAULT 0,
    creado_en      DATETIME(6)   NOT NULL,
    iniciado_en    DATETIME(6),
    finalizado_en  DATETIME(6),
    CONSTRAINT fk_ai_jobs_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    INDEX ix_ai_jobs_cola (estado, creado_en),
    INDEX ix_ai_jobs_paciente (tenant_id, paciente_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE formulas (
    id            BINARY(16)    NOT NULL PRIMARY KEY,
    tenant_id     BINARY(16)    NOT NULL,
    paciente_id   BINARY(16)    NOT NULL,
    medico_id     BINARY(16)    NOT NULL,
    ai_job_id     BINARY(16),
    observaciones VARCHAR(1000),
    firmada_en    DATETIME(6)   NOT NULL,
    CONSTRAINT fk_formulas_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_formulas_paciente FOREIGN KEY (paciente_id) REFERENCES pacientes (id),
    CONSTRAINT fk_formulas_medico FOREIGN KEY (medico_id) REFERENCES usuarios (id),
    CONSTRAINT fk_formulas_ai_job FOREIGN KEY (ai_job_id) REFERENCES ai_jobs (id),
    INDEX ix_formulas_paciente (tenant_id, paciente_id, firmada_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE formula_items (
    formula_id         BINARY(16)   NOT NULL,
    orden              INT          NOT NULL,
    medicamento        VARCHAR(200) NOT NULL,
    concentracion      VARCHAR(60),
    forma_farmaceutica VARCHAR(60),
    via                VARCHAR(40),
    dosis              VARCHAR(60)  NOT NULL,
    frecuencia         VARCHAR(60)  NOT NULL,
    duracion           VARCHAR(60)  NOT NULL,
    cantidad           VARCHAR(60),
    indicaciones       VARCHAR(500),
    PRIMARY KEY (formula_id, orden),
    CONSTRAINT fk_formula_items_formula FOREIGN KEY (formula_id) REFERENCES formulas (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
