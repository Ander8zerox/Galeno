-- Tipos de cita del consultorio (primera vez, control, procedimiento…): cada uno con su duración y, si se quiere, su
-- CUPS. La clase (primera vez o control) decide el CUPS de consulta que se sugiere en la atención y la finalidad del RIPS.
CREATE TABLE tipos_cita (
    id               BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id        BINARY(16)  NOT NULL,
    nombre           VARCHAR(80) NOT NULL,
    duracion_minutos INT         NOT NULL,
    clase            VARCHAR(20) NOT NULL, -- PRIMERA_VEZ, CONTROL, OTRA
    cups             VARCHAR(10) NULL,     -- sin él: el de consulta según la clase y el servicio habilitado
    activo           BOOLEAN     NOT NULL DEFAULT TRUE,
    creado_en        DATETIME(6) NOT NULL,
    CONSTRAINT fk_tipos_cita_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    UNIQUE KEY uk_tipos_cita_nombre (tenant_id, nombre)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE citas
    ADD COLUMN tipo_cita_id BINARY(16) NULL,
    ADD CONSTRAINT fk_citas_tipo FOREIGN KEY (tipo_cita_id) REFERENCES tipos_cita (id);
