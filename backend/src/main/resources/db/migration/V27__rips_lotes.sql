-- RIPS fase 2, entrega 3 (ver docs/diseno-rips.md): incapacidad en la atención y lotes del RIPS.

-- El médico marca si expidió incapacidad (campo "incapacidad" del usuario en el RIPS, SI/NO). Es parte de lo que firma.
ALTER TABLE atenciones ADD COLUMN incapacidad BOOLEAN NOT NULL DEFAULT FALSE;

-- Un lote: las atenciones firmadas de un pagador en un período que van en una factura (o en un RIPS sin factura, para
-- el profesional independiente no obligado a facturar). Al generarse guarda el JSON tal cual: no cambia si después se
-- corrige un dato. Anulado, libera sus atenciones para otro lote.
CREATE TABLE rips_lotes (
    id                   BINARY(16)    NOT NULL PRIMARY KEY,
    tenant_id            BINARY(16)    NOT NULL,
    pagador_id           BINARY(16)    NOT NULL,
    sin_factura          BOOLEAN       NOT NULL,
    desde                DATE          NOT NULL,
    hasta                DATE          NOT NULL,
    numero_factura       VARCHAR(20),
    estado               VARCHAR(20)   NOT NULL, -- BORRADOR | GENERADO | ANULADO
    -- Lo que se leyó del XML de la factura, si se cargó (para cruzarlo antes de generar)
    xml_nit              VARCHAR(20),
    xml_numero           VARCHAR(20),
    xml_desde            DATE,
    xml_hasta            DATE,
    xml_pagos_moderadores DECIMAL(14, 2),
    json                 MEDIUMTEXT,
    creado_por           BINARY(16)    NOT NULL,
    creado_en            DATETIME(6)   NOT NULL,
    generado_por         BINARY(16),
    generado_en          DATETIME(6),
    anulado_por          BINARY(16),
    anulado_en           DATETIME(6),
    motivo_anulacion     VARCHAR(300),
    actualizado_en       DATETIME(6)   NOT NULL,
    CONSTRAINT fk_rips_lotes_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_rips_lotes_pagador FOREIGN KEY (pagador_id) REFERENCES rips_pagadores (id),
    INDEX ix_rips_lotes_tenant (tenant_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Las atenciones del lote y lo que se cambió para cada una en la revisión (vacío: el valor por defecto del prestador).
-- "vigente" es el id de la atención mientras el lote no esté anulado: el UNIQUE impide que una atención esté en dos
-- lotes vivos (MySQL admite varios NULL en un UNIQUE).
CREATE TABLE rips_lote_atenciones (
    lote_id            BINARY(16) NOT NULL,
    atencion_id        BINARY(16) NOT NULL,
    vigente            BINARY(16),
    finalidad          VARCHAR(2),
    causa_externa      VARCHAR(2),
    modalidad_atencion VARCHAR(2),
    grupo_servicios    VARCHAR(2),
    servicio           VARCHAR(4),
    via_ingreso        VARCHAR(2),
    PRIMARY KEY (lote_id, atencion_id),
    CONSTRAINT uk_rips_lote_atenciones_vigente UNIQUE (vigente),
    CONSTRAINT fk_rips_lote_atenciones_lote FOREIGN KEY (lote_id) REFERENCES rips_lotes (id) ON DELETE CASCADE,
    CONSTRAINT fk_rips_lote_atenciones_atencion FOREIGN KEY (atencion_id) REFERENCES atenciones (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
