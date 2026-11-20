-- Preconsulta: lo que la auxiliar (o la secretaria con ese rol, o el médico) registra antes de que entre el paciente.
-- Una por cita. Al abrir la atención de esa cita, el médico la encuentra ya diligenciada.
CREATE TABLE preconsultas (
    id                      BINARY(16)    NOT NULL PRIMARY KEY,
    tenant_id               BINARY(16)    NOT NULL,
    cita_id                 BINARY(16)    NOT NULL,
    paciente_id             BINARY(16)    NOT NULL,
    motivo_consulta         VARCHAR(2000) NULL,
    ta_sistolica            INT           NULL,
    ta_diastolica           INT           NULL,
    frecuencia_cardiaca     INT           NULL,
    frecuencia_respiratoria INT           NULL,
    temperatura             DECIMAL(4, 1) NULL,
    saturacion_o2           INT           NULL,
    peso_kg                 DECIMAL(5, 2) NULL,
    talla_cm                DECIMAL(5, 1) NULL,
    medicamentos_actuales   VARCHAR(2000) NULL,
    observaciones           VARCHAR(2000) NULL,
    registrada_por          BINARY(16)    NOT NULL,
    registrada_por_nombre   VARCHAR(200)  NOT NULL,
    registrada_en           DATETIME(6)   NOT NULL,
    actualizada_en          DATETIME(6)   NOT NULL,
    CONSTRAINT fk_preconsultas_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_preconsultas_cita FOREIGN KEY (cita_id) REFERENCES citas (id),
    CONSTRAINT fk_preconsultas_paciente FOREIGN KEY (paciente_id) REFERENCES pacientes (id),
    CONSTRAINT fk_preconsultas_registrada_por FOREIGN KEY (registrada_por) REFERENCES usuarios (id),
    UNIQUE KEY uk_preconsultas_cita (cita_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
