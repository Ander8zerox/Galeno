-- Importación de historias desde RIPS (JSON de la factura electrónica en salud).
-- Flujo: subir archivos → lote EN_REVISION (un registro por paciente) → confirmar → (opcional) deshacer.

CREATE TABLE lotes_importacion (
    id                BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id         BINARY(16)   NOT NULL,
    origen            VARCHAR(20)  NOT NULL, -- RIPS_JSON
    estado            VARCHAR(20)  NOT NULL, -- EN_REVISION | CONFIRMADO | DESHECHO | DESCARTADO
    archivos          INT          NOT NULL,
    facturas          INT          NOT NULL,
    prestadores       VARCHAR(500),          -- NIT de los prestadores encontrados
    pacientes         INT          NOT NULL,
    atenciones        INT          NOT NULL,
    errores           INT          NOT NULL,
    omitidos          INT          NOT NULL, -- servicios que esta versión no importa (medicamentos, otros)
    resultado         JSON,                  -- qué se creó al confirmar y qué se quitó al deshacer
    creado_por        BINARY(16)   NOT NULL,
    creado_en         DATETIME(6)  NOT NULL,
    confirmado_en     DATETIME(6),
    deshecho_en       DATETIME(6),
    CONSTRAINT fk_lotes_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    INDEX ix_lotes_consultorio (tenant_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Un registro por paciente del lote, con todas sus atenciones de todos los archivos
CREATE TABLE registros_importacion (
    id               BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id        BINARY(16)  NOT NULL,
    lote_id          BINARY(16)  NOT NULL,
    orden            INT         NOT NULL,
    tipo_documento   VARCHAR(5)  NOT NULL,
    numero_documento VARCHAR(30) NOT NULL,
    estado           VARCHAR(20) NOT NULL, -- NUEVO | EXISTENTE | ERROR
    incluido         BIT         NOT NULL,
    atenciones       INT         NOT NULL,
    datos            JSON        NOT NULL,
    mensajes         JSON        NOT NULL,
    paciente_id      BINARY(16),
    CONSTRAINT uk_registros_documento UNIQUE (lote_id, tipo_documento, numero_documento),
    CONSTRAINT fk_registros_lote FOREIGN KEY (lote_id) REFERENCES lotes_importacion (id),
    INDEX ix_registros_lote (lote_id, orden)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- El RIPS no trae nombres: el paciente importado queda marcado hasta que se completen sus datos
ALTER TABLE pacientes
    ADD COLUMN datos_incompletos   BIT        NOT NULL DEFAULT 0,
    ADD COLUMN lote_importacion_id BINARY(16);

-- Atenciones importadas: sin médico de Galeno, ligadas al lote y con una clave para no duplicarlas
ALTER TABLE atenciones
    MODIFY COLUMN medico_membresia_id BINARY(16) NULL,
    MODIFY COLUMN medico_usuario_id   BINARY(16) NULL,
    ADD COLUMN lote_importacion_id BINARY(16),
    ADD COLUMN clave_importacion   VARCHAR(160),
    ADD CONSTRAINT uk_atenciones_clave_importacion UNIQUE (tenant_id, clave_importacion),
    ADD INDEX ix_atenciones_lote (lote_importacion_id);
