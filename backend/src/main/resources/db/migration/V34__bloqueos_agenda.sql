-- Bloqueos de agenda: vacaciones, capacitaciones o cierres. De un médico, o de todo el consultorio (médico nulo).
-- En un bloqueo no se ofrecen cupos y solo se agenda con sobrecupo, como en los festivos.

CREATE TABLE bloqueos_agenda (
    id                  BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id           BINARY(16)   NOT NULL,
    medico_membresia_id BINARY(16),               -- null: todo el consultorio
    inicio              DATETIME(6)  NOT NULL,    -- UTC
    fin                 DATETIME(6)  NOT NULL,    -- UTC
    motivo              VARCHAR(200) NOT NULL,
    creado_por          BINARY(16)   NOT NULL,
    creado_en           DATETIME(6)  NOT NULL,
    CONSTRAINT fk_bloqueos_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_bloqueos_medico FOREIGN KEY (medico_membresia_id) REFERENCES membresias (id),
    CONSTRAINT fk_bloqueos_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios (id),
    INDEX ix_bloqueos_inicio (tenant_id, inicio)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
