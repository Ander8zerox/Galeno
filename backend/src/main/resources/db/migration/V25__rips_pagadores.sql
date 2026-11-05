-- RIPS fase 2, entrega 2 (ver docs/diseno-rips.md): pagadores del consultorio, tarifas por convenio, pagador del
-- paciente y cobro de cada cita o atención. Todo es del módulo de RIPS; los códigos son de rips_referencias (V22, V24).

-- EPS, prepagadas, ARL, SOAT… con lo que pide la factura de salud (Documento Técnico 2): cobertura, modalidad de pago,
-- contrato (CUCON) o póliza, o el motivo de la factura sin contrato. Se desactivan en vez de borrarse.
CREATE TABLE rips_pagadores (
    id                   BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id            BINARY(16)   NOT NULL,
    nombre               VARCHAR(150) NOT NULL,
    tipo                 VARCHAR(20)  NOT NULL,
    codigo_entidad       VARCHAR(20),
    nit                  VARCHAR(20),
    cobertura            VARCHAR(2),
    modalidad_pago       VARCHAR(2),
    cucon                VARCHAR(64),
    numero_poliza        VARCHAR(30),
    factura_sin_contrato VARCHAR(2),
    exige_autorizacion   BOOLEAN      NOT NULL,
    activo               BOOLEAN      NOT NULL,
    actualizado_en       DATETIME(6)  NOT NULL,
    CONSTRAINT fk_rips_pagadores_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_rips_pagadores_tenant ON rips_pagadores (tenant_id, activo);

-- Precio de un CUPS con un pagador (convenio). Sin tarifa, vale la lista general (precios_cups).
CREATE TABLE rips_tarifas (
    id             BINARY(16)    NOT NULL PRIMARY KEY,
    tenant_id      BINARY(16)    NOT NULL,
    pagador_id     BINARY(16)    NOT NULL,
    codigo_cups    VARCHAR(10)   NOT NULL,
    valor          DECIMAL(12,2) NOT NULL,
    actualizado_en DATETIME(6)   NOT NULL,
    CONSTRAINT uk_rips_tarifas UNIQUE (pagador_id, codigo_cups),
    CONSTRAINT fk_rips_tarifas_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_rips_tarifas_pagador FOREIGN KEY (pagador_id) REFERENCES rips_pagadores (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Pagador principal del paciente: cada cita lo hereda.
ALTER TABLE rips_pacientes ADD COLUMN pagador_id BINARY(16),
    ADD CONSTRAINT fk_rips_pacientes_pagador FOREIGN KEY (pagador_id) REFERENCES rips_pagadores (id);

-- Pagador y cobro de una cita (lo registra la secretaria al recibir al paciente) o de una atención (lo corrige quien
-- atiende, o lo registra si la atención no viene de una cita). Campos C03, C18, C19 y C20 del Documento Técnico 1.
-- Cada fila es de una cita o de una atención, no de ambas (lo garantiza CobrosService: MySQL no admite un CHECK sobre
-- una columna con ON DELETE CASCADE).
CREATE TABLE rips_cobros (
    id                     BINARY(16)    NOT NULL PRIMARY KEY,
    tenant_id              BINARY(16)    NOT NULL,
    cita_id                BINARY(16),
    atencion_id            BINARY(16),
    pagador_id             BINARY(16)    NOT NULL,
    numero_autorizacion    VARCHAR(30),
    concepto_recaudo       VARCHAR(2)    NOT NULL,
    valor_pago_moderador   DECIMAL(12,2) NOT NULL,
    factura_pago_moderador VARCHAR(20),
    actualizado_en         DATETIME(6)   NOT NULL,
    CONSTRAINT uk_rips_cobros_cita UNIQUE (cita_id),
    CONSTRAINT uk_rips_cobros_atencion UNIQUE (atencion_id),
    CONSTRAINT fk_rips_cobros_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_rips_cobros_cita FOREIGN KEY (cita_id) REFERENCES citas (id),
    -- Si se elimina el borrador de la atención, su cobro se va con él
    CONSTRAINT fk_rips_cobros_atencion FOREIGN KEY (atencion_id) REFERENCES atenciones (id) ON DELETE CASCADE,
    CONSTRAINT fk_rips_cobros_pagador FOREIGN KEY (pagador_id) REFERENCES rips_pagadores (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
