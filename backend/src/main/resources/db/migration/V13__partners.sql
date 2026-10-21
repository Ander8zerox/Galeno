-- Programa de partners: quien vende Galeno a un consultorio recibe una comisión recurrente sobre sus pagos,
-- y el consultorio referido recibe un descuento. Los partners se inscriben y la plataforma los aprueba.

CREATE TABLE partners (
    id               BINARY(16)   NOT NULL PRIMARY KEY,
    usuario_id       BINARY(16)   NOT NULL,
    tipo_persona     VARCHAR(10)  NOT NULL, -- NATURAL | JURIDICA
    nombre           VARCHAR(200) NOT NULL, -- nombre completo o razón social
    tipo_documento   VARCHAR(5)   NOT NULL, -- CC | CE | PA | NIT
    numero_documento VARCHAR(30)  NOT NULL,
    telefono         VARCHAR(30)  NOT NULL,
    banco            VARCHAR(80),
    tipo_cuenta      VARCHAR(20),           -- AHORROS | CORRIENTE
    numero_cuenta    VARCHAR(40),
    codigo           VARCHAR(20),           -- se asigna al aprobar
    estado           VARCHAR(20)  NOT NULL, -- PENDIENTE | ACTIVO | SUSPENDIDO | RECHAZADO
    motivo           VARCHAR(300),
    terminos_version VARCHAR(20)  NOT NULL,
    creado_en        DATETIME(6)  NOT NULL,
    aprobado_en      DATETIME(6),
    CONSTRAINT uk_partners_usuario UNIQUE (usuario_id),
    CONSTRAINT uk_partners_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT uk_partners_codigo UNIQUE (codigo),
    CONSTRAINT fk_partners_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Inscripción que espera la verificación del correo (como el registro de consultorios)
CREATE TABLE inscripciones_partner (
    id            BINARY(16)   NOT NULL PRIMARY KEY,
    token_hash    VARCHAR(64)  NOT NULL,
    email         VARCHAR(190) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    datos         JSON         NOT NULL,
    creado_en     DATETIME(6)  NOT NULL,
    expira_en     DATETIME(6)  NOT NULL,
    confirmado_en DATETIME(6),
    CONSTRAINT uk_inscripciones_token UNIQUE (token_hash),
    INDEX ix_inscripciones_expira (expira_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Un consultorio tiene a lo sumo un partner; solo la plataforma lo cambia
CREATE TABLE referidos (
    consultorio_id BINARY(16)  NOT NULL PRIMARY KEY,
    partner_id     BINARY(16)  NOT NULL,
    origen         VARCHAR(20) NOT NULL, -- REGISTRO | PLATAFORMA
    atribuido_en   DATETIME(6) NOT NULL,
    CONSTRAINT fk_referidos_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorios (id),
    CONSTRAINT fk_referidos_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    INDEX ix_referidos_partner (partner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE liquidaciones_partner (
    id          BINARY(16)   NOT NULL PRIMARY KEY,
    partner_id  BINARY(16)   NOT NULL,
    total       BIGINT       NOT NULL,
    comisiones  INT          NOT NULL,
    referencia  VARCHAR(100) NOT NULL, -- de la transferencia al partner
    fecha_pago  DATE         NOT NULL,
    creada_por  BINARY(16)   NOT NULL,
    creada_en   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_liquidaciones_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    INDEX ix_liquidaciones_partner (partner_id, creada_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Una comisión por pago aprobado de un consultorio referido
CREATE TABLE comisiones_partner (
    id               BINARY(16)   NOT NULL PRIMARY KEY,
    partner_id       BINARY(16)   NOT NULL,
    consultorio_id   BINARY(16)   NOT NULL,
    pago_id          BINARY(16)   NOT NULL,
    base             BIGINT       NOT NULL, -- valor del pago
    porcentaje       INT          NOT NULL, -- el vigente cuando se aprobó el pago
    valor            BIGINT       NOT NULL,
    creada_en        DATETIME(6)  NOT NULL,
    disponible_desde DATETIME(6)  NOT NULL, -- retenida hasta aquí (por si el pago se reversa)
    anulada_en       DATETIME(6),
    motivo_anulacion VARCHAR(300),
    liquidacion_id   BINARY(16),
    CONSTRAINT uk_comisiones_pago UNIQUE (pago_id),
    CONSTRAINT fk_comisiones_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_comisiones_pago FOREIGN KEY (pago_id) REFERENCES pagos_suscripcion (id),
    CONSTRAINT fk_comisiones_liquidacion FOREIGN KEY (liquidacion_id) REFERENCES liquidaciones_partner (id),
    INDEX ix_comisiones_partner (partner_id, creada_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- El descuento del referido se guarda en la suscripción (el módulo de suscripciones no conoce a los partners)
ALTER TABLE suscripciones ADD COLUMN descuento_porcentaje INT NOT NULL DEFAULT 0;

-- El código que trae quien se registra (se valida al pedir el registro y se aplica al confirmarlo)
ALTER TABLE registros_consultorio ADD COLUMN codigo_partner VARCHAR(20);
