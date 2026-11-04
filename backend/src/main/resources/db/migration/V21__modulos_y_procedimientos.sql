-- Fase 1 de RIPS (ver docs/diseno-rips.md): módulos opcionales por consultorio, procedimientos realizados (CUPS) en la
-- atención y lista de precios por CUPS.

-- Módulos activos en el consultorio (la fila existe = activo). Desactivar no borra lo que el módulo ya generó.
CREATE TABLE modulos_consultorio (
    id          BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id   BINARY(16)  NOT NULL,
    modulo      VARCHAR(30) NOT NULL,
    activado_en DATETIME(6) NOT NULL,
    CONSTRAINT uk_modulos_consultorio UNIQUE (tenant_id, modulo),
    CONSTRAINT fk_modulos_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Lo que se hizo en la atención, con su CUPS. Es de la historia clínica: queda firmado con la atención.
CREATE TABLE atencion_procedimientos (
    atencion_id    BINARY(16)    NOT NULL,
    orden          INT           NOT NULL,
    codigo_cups    VARCHAR(10)   NOT NULL,
    descripcion    VARCHAR(400)  NOT NULL,
    cantidad       INT           NOT NULL,
    valor_unitario DECIMAL(12, 2),
    diente         VARCHAR(2),
    superficies    VARCHAR(5),
    PRIMARY KEY (atencion_id, orden),
    CONSTRAINT fk_atencion_procedimientos FOREIGN KEY (atencion_id) REFERENCES atenciones (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Precio de cada CUPS en el consultorio: se propone al registrar el procedimiento.
CREATE TABLE precios_cups (
    id             BINARY(16)     NOT NULL PRIMARY KEY,
    tenant_id      BINARY(16)     NOT NULL,
    codigo_cups    VARCHAR(10)    NOT NULL,
    valor          DECIMAL(12, 2) NOT NULL,
    actualizado_en DATETIME(6)    NOT NULL,
    CONSTRAINT uk_precios_cups UNIQUE (tenant_id, codigo_cups),
    CONSTRAINT fk_precios_cups_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
