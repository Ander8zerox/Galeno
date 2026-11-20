-- Bloques 8, 9 y 10 del RDA en cada atención.

-- 8. Resultado o hallazgo de cada procedimiento realizado.
ALTER TABLE atencion_procedimientos
    ADD COLUMN resultado VARCHAR(1000) NULL;

-- 9. Órdenes médicas: laboratorio, imágenes, procedimientos e interconsultas (se firman con la atención).
CREATE TABLE atencion_ordenes (
    atencion_id  BINARY(16)   NOT NULL,
    orden        INT          NOT NULL,
    tipo         VARCHAR(20)  NOT NULL, -- LABORATORIO, IMAGEN, PROCEDIMIENTO, INTERCONSULTA
    codigo_cups  VARCHAR(10)  NULL,
    descripcion  VARCHAR(400) NOT NULL,
    cantidad     INT          NOT NULL,
    indicaciones VARCHAR(500) NULL,
    prioridad    VARCHAR(10)  NOT NULL, -- RUTINARIA, URGENTE
    PRIMARY KEY (atencion_id, orden),
    CONSTRAINT fk_atencion_ordenes FOREIGN KEY (atencion_id) REFERENCES atenciones (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 10. Factores de riesgo identificados en la atención.
CREATE TABLE atencion_factores_riesgo (
    atencion_id BINARY(16)   NOT NULL,
    orden       INT          NOT NULL,
    codigo      VARCHAR(30)  NOT NULL,
    observacion VARCHAR(300) NULL,
    PRIMARY KEY (atencion_id, orden),
    CONSTRAINT fk_atencion_factores_riesgo FOREIGN KEY (atencion_id) REFERENCES atenciones (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
