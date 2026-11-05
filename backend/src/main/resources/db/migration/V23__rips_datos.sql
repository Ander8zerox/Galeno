-- RIPS fase 2, entrega 1 (ver docs/diseno-rips.md): datos que pide el RIPS y que la historia no tiene. Son del módulo
-- de RIPS: sin el módulo activo no se ven ni se piden. Los códigos son de rips_referencias (V22).

-- Datos del usuario en el RIPS (campos U03, U06, U07, U08 y U11 del Documento Técnico 1).
CREATE TABLE rips_pacientes (
    paciente_id          BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id            BINARY(16)  NOT NULL,
    tipo_usuario         VARCHAR(2),
    pais_residencia      VARCHAR(3),
    municipio_residencia VARCHAR(5),
    zona_residencia      VARCHAR(2),
    pais_origen          VARCHAR(3),
    actualizado_en       DATETIME(6) NOT NULL,
    CONSTRAINT fk_rips_pacientes_paciente FOREIGN KEY (paciente_id) REFERENCES pacientes (id),
    CONSTRAINT fk_rips_pacientes_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- El consultorio como prestador: su código de habilitación (codPrestador, 12 caracteres con la sede) y los valores que
-- se proponen en cada consulta o procedimiento.
CREATE TABLE rips_prestador (
    id                  BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id           BINARY(16)  NOT NULL,
    codigo_habilitacion VARCHAR(12),
    servicio            VARCHAR(4),
    modalidad_atencion  VARCHAR(2),
    grupo_servicios     VARCHAR(2),
    finalidad           VARCHAR(2),
    causa_externa       VARCHAR(2),
    via_ingreso         VARCHAR(2),
    actualizado_en      DATETIME(6) NOT NULL,
    CONSTRAINT uk_rips_prestador UNIQUE (tenant_id),
    CONSTRAINT fk_rips_prestador_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Documento de quien atiende (tipoDocumentoIdentificacion y numDocumentoIdentificacion de cada consulta o procedimiento).
CREATE TABLE rips_profesionales (
    id               BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id        BINARY(16)  NOT NULL,
    usuario_id       BINARY(16)  NOT NULL,
    tipo_documento   VARCHAR(2)  NOT NULL,
    numero_documento VARCHAR(20) NOT NULL,
    actualizado_en   DATETIME(6) NOT NULL,
    CONSTRAINT uk_rips_profesionales UNIQUE (tenant_id, usuario_id),
    CONSTRAINT fk_rips_profesionales_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_rips_profesionales_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
