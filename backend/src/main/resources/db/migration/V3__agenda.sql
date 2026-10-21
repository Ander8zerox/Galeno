-- Agenda por médico: horario semanal, duración de las citas y citas.

ALTER TABLE consultorios ADD COLUMN zona_horaria VARCHAR(40) NOT NULL DEFAULT 'America/Bogota';

-- Configuración de agenda de un médico en un consultorio (su id es la membresía del médico)
CREATE TABLE agendas_medico (
    medico_membresia_id BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id           BINARY(16)  NOT NULL,
    duracion_minutos    INT         NOT NULL,
    actualizado_en      DATETIME(6) NOT NULL,
    CONSTRAINT fk_agendas_medico_membresia FOREIGN KEY (medico_membresia_id) REFERENCES membresias (id),
    CONSTRAINT fk_agendas_medico_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Bloques de atención por día de la semana (1 = lunes … 7 = domingo), en hora local del consultorio
CREATE TABLE agenda_bloques (
    medico_membresia_id BINARY(16) NOT NULL,
    orden               INT        NOT NULL,
    dia_semana          INT        NOT NULL,
    hora_inicio         TIME       NOT NULL,
    hora_fin            TIME       NOT NULL,
    PRIMARY KEY (medico_membresia_id, orden),
    CONSTRAINT fk_agenda_bloques_agenda FOREIGN KEY (medico_membresia_id) REFERENCES agendas_medico (medico_membresia_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE citas (
    id                  BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id           BINARY(16)   NOT NULL,
    medico_membresia_id BINARY(16)   NOT NULL,
    paciente_id         BINARY(16)   NOT NULL,
    inicio              DATETIME(6)  NOT NULL, -- UTC
    fin                 DATETIME(6)  NOT NULL, -- UTC
    estado              VARCHAR(20)  NOT NULL,
    motivo              VARCHAR(300),
    sobrecupo           BIT          NOT NULL,
    motivo_cancelacion  VARCHAR(300),
    creada_por          BINARY(16)   NOT NULL,
    creada_en           DATETIME(6)  NOT NULL,
    actualizada_en      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_citas_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_citas_medico FOREIGN KEY (medico_membresia_id) REFERENCES membresias (id),
    CONSTRAINT fk_citas_paciente FOREIGN KEY (paciente_id) REFERENCES pacientes (id),
    CONSTRAINT fk_citas_creada_por FOREIGN KEY (creada_por) REFERENCES usuarios (id),
    INDEX ix_citas_medico_inicio (tenant_id, medico_membresia_id, inicio),
    INDEX ix_citas_paciente (tenant_id, paciente_id, inicio)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
