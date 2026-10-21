-- Suscripciones: prueba de 30 días, planes mensual y anual pagados por transferencia o consignación,
-- revisados por un operador de la plataforma. Vencida: 5 días de gracia; luego, solo lectura.

CREATE TABLE planes_suscripcion (
    codigo         VARCHAR(20)  NOT NULL PRIMARY KEY, -- MENSUAL | ANUAL
    nombre         VARCHAR(60)  NOT NULL,
    meses          INT          NOT NULL,
    precio         BIGINT       NOT NULL,             -- pesos colombianos; lo define la plataforma
    activo         BIT          NOT NULL,
    actualizado_en DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO planes_suscripcion VALUES
    ('MENSUAL', 'Mensual', 1, 0, 1, NOW(6)),
    ('ANUAL', 'Anual', 12, 0, 1, NOW(6));

CREATE TABLE suscripciones (
    id                BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id         BINARY(16)   NOT NULL,
    prueba_hasta      DATETIME(6)  NOT NULL, -- fin de la prueba (o de una cortesía)
    pagada_hasta      DATETIME(6),           -- fin del último periodo pagado
    suspendida        BIT          NOT NULL, -- suspensión manual de la plataforma
    motivo_suspension VARCHAR(300),
    creada_en         DATETIME(6)  NOT NULL,
    actualizada_en    DATETIME(6)  NOT NULL,
    version           BIGINT       NOT NULL,
    CONSTRAINT uk_suscripciones_consultorio UNIQUE (tenant_id),
    CONSTRAINT fk_suscripciones_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Los consultorios que ya existen empiezan su prueba hoy
INSERT INTO suscripciones (id, tenant_id, prueba_hasta, pagada_hasta, suspendida, creada_en, actualizada_en, version)
SELECT UUID_TO_BIN(UUID()), id, NOW(6) + INTERVAL 30 DAY, NULL, 0, NOW(6), NOW(6), 0 FROM consultorios;

CREATE TABLE pagos_suscripcion (
    id                 BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id          BINARY(16)   NOT NULL,
    plan_codigo        VARCHAR(20)  NOT NULL,
    meses              INT          NOT NULL,
    monto              BIGINT       NOT NULL,
    fecha_pago         DATE         NOT NULL,
    referencia         VARCHAR(100) NOT NULL,
    comprobante_nombre VARCHAR(200),          -- el archivo va en comprobantes_pago
    comprobante_tipo   VARCHAR(60),
    estado             VARCHAR(20)  NOT NULL, -- REPORTADO | APROBADO | RECHAZADO
    motivo_rechazo     VARCHAR(300),
    reportado_por      BINARY(16)   NOT NULL,
    reportado_en       DATETIME(6)  NOT NULL,
    revisado_por       BINARY(16),
    revisado_en        DATETIME(6),
    periodo_desde      DATETIME(6),
    periodo_hasta      DATETIME(6),
    CONSTRAINT fk_pagos_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_pagos_plan FOREIGN KEY (plan_codigo) REFERENCES planes_suscripcion (codigo),
    INDEX ix_pagos_estado (estado, reportado_en),
    INDEX ix_pagos_consultorio (tenant_id, reportado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Aparte para que listar pagos no cargue los archivos
CREATE TABLE comprobantes_pago (
    pago_id   BINARY(16)  NOT NULL PRIMARY KEY,
    contenido MEDIUMBLOB  NOT NULL, -- PDF o imagen de hasta 5 MB
    CONSTRAINT fk_comprobantes_pago FOREIGN KEY (pago_id) REFERENCES pagos_suscripcion (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Datos de pago que ven los consultorios (cuenta bancaria, a nombre de quién…)
CREATE TABLE parametros_plataforma (
    clave          VARCHAR(60)  NOT NULL PRIMARY KEY,
    valor          TEXT         NOT NULL,
    actualizado_en DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO parametros_plataforma VALUES
    ('datos_pago', 'Pendiente: la plataforma aún no configuró la cuenta para transferencias.', NOW(6));

-- Operadores de la plataforma: los marca la configuración (app.plataforma.operadores) al arrancar
CREATE TABLE operadores_plataforma (
    usuario_id BINARY(16)  NOT NULL PRIMARY KEY,
    creado_en  DATETIME(6) NOT NULL,
    CONSTRAINT fk_operadores_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE auditoria_plataforma (
    id             BINARY(16)   NOT NULL PRIMARY KEY,
    operador_id    BINARY(16)   NOT NULL,
    accion         VARCHAR(40)  NOT NULL,
    consultorio_id BINARY(16),
    detalle        VARCHAR(500),
    creado_en      DATETIME(6)  NOT NULL,
    INDEX ix_auditoria_plataforma (creado_en),
    INDEX ix_auditoria_plataforma_consultorio (consultorio_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Avisos de vencimiento ya enviados (uno por tipo y por fecha de vencimiento)
CREATE TABLE avisos_suscripcion (
    tenant_id   BINARY(16)  NOT NULL,
    tipo        VARCHAR(30) NOT NULL,
    vencimiento DATETIME(6) NOT NULL,
    enviado_en  DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, tipo, vencimiento)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
