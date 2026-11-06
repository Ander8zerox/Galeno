-- RIPS: notas crédito, débito y de ajuste (ver "Notas crédito, débito y de ajuste" en docs/diseno-rips.md). Cada nota
-- es de un lote generado y se arma sobre su JSON guardado (mismos consecutivos). La crédito y la débito las emite el
-- facturador del consultorio ante la DIAN; Galenum genera su RIPS. La NC al 100% se registra sin RIPS.
CREATE TABLE rips_notas (
    id               BINARY(16)     NOT NULL PRIMARY KEY,
    tenant_id        BINARY(16)     NOT NULL,
    lote_id          BINARY(16)     NOT NULL,
    tipo             VARCHAR(2)     NOT NULL, -- NC | ND | NA
    total            BOOLEAN        NOT NULL, -- NC al 100%: sin servicios ni JSON
    numero           VARCHAR(20)    NOT NULL,
    motivo           VARCHAR(300)   NOT NULL,
    estado           VARCHAR(20)    NOT NULL, -- BORRADOR | GENERADA | ANULADA
    -- Los servicios afectados: [{"clave": "1-consultas-1", "valor": 12000, "datos": {"campo": "valor"}}]
    servicios        JSON           NOT NULL,
    -- Lo que se leyó del XML de la nota (CreditNote o DebitNote), si se cargó
    xml_numero       VARCHAR(20),
    xml_factura      VARCHAR(20),
    xml_valor        DECIMAL(14, 2),
    json             MEDIUMTEXT,
    creado_por       BINARY(16)     NOT NULL,
    creado_en        DATETIME(6)    NOT NULL,
    generado_por     BINARY(16),
    generado_en      DATETIME(6),
    anulado_por      BINARY(16),
    anulado_en       DATETIME(6),
    motivo_anulacion VARCHAR(300),
    actualizado_en   DATETIME(6)    NOT NULL,
    CONSTRAINT fk_rips_notas_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_rips_notas_lote FOREIGN KEY (lote_id) REFERENCES rips_lotes (id),
    INDEX ix_rips_notas_lote (lote_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
