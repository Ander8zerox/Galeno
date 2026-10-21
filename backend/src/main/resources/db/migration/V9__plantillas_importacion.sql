-- Mapeo de columnas recordado por consultorio: cuando llega otro listado con los mismos encabezados
-- (el mismo sistema de origen), se propone el mapeo que se usó la última vez.
CREATE TABLE plantillas_importacion (
    id             BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id      BINARY(16)   NOT NULL,
    firma          VARCHAR(2000) NOT NULL, -- encabezados normalizados, separados por |
    firma_hash     VARCHAR(64)  NOT NULL, -- SHA-256 de la firma (para el índice único)
    mapeo          JSON         NOT NULL,
    actualizada_en DATETIME(6)  NOT NULL,
    CONSTRAINT fk_plantillas_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT uk_plantillas_firma UNIQUE (tenant_id, firma_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
