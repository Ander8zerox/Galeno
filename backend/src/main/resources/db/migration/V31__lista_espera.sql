-- Lista de espera de la agenda: pacientes que quieren una cita antes (o que no consiguieron cupo). Cuando se libera un
-- espacio, Galenum sugiere a quién llamar; la secretaria asigna después de hablar con el paciente.

CREATE TABLE lista_espera (
    id                  BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id           BINARY(16)   NOT NULL,
    paciente_id         BINARY(16)   NOT NULL,
    medico_membresia_id BINARY(16),               -- null: cualquier médico
    duracion_minutos    INT          NOT NULL,
    franja              VARCHAR(10)  NOT NULL,    -- CUALQUIERA, MANANA, TARDE
    antes_de            DATE,                     -- deja de sugerirse después de esta fecha
    cita_id             BINARY(16),               -- la cita que quiere adelantar; al asignar, la cita resultante
    motivo              VARCHAR(300),             -- motivo de la cita que se le dará
    nota                VARCHAR(300),
    estado              VARCHAR(12)  NOT NULL,    -- ACTIVA, ASIGNADA, RETIRADA
    creada_por          BINARY(16)   NOT NULL,
    creada_en           DATETIME(6)  NOT NULL,
    actualizada_en      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_lista_espera_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_lista_espera_paciente FOREIGN KEY (paciente_id) REFERENCES pacientes (id),
    CONSTRAINT fk_lista_espera_medico FOREIGN KEY (medico_membresia_id) REFERENCES membresias (id),
    CONSTRAINT fk_lista_espera_cita FOREIGN KEY (cita_id) REFERENCES citas (id),
    CONSTRAINT fk_lista_espera_creada_por FOREIGN KEY (creada_por) REFERENCES usuarios (id),
    INDEX ix_lista_espera_estado (tenant_id, estado, creada_en),
    INDEX ix_lista_espera_paciente (tenant_id, paciente_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
