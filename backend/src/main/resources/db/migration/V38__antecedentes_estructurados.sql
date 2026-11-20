-- Antecedentes y alergias estructurados (bloque 5 del Resumen Digital de Atención).
-- Los textos libres anteriores (pacientes.alergias y pacientes.antecedentes) se conservan como "registro anterior":
-- no se pueden convertir con seguridad, y el médico los pasa a ítems cuando revisa al paciente.

-- ¿Tiene alergias? NULL: no se ha preguntado; NIEGA: no tiene conocidas; SI: ver paciente_alergias.
ALTER TABLE pacientes
    ADD COLUMN alergias_estado VARCHAR(10) NULL;

CREATE TABLE paciente_alergias (
    id             BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id      BINARY(16)   NOT NULL,
    paciente_id    BINARY(16)   NOT NULL,
    tipo           VARCHAR(20)  NOT NULL, -- MEDICAMENTO, ALIMENTO, AMBIENTAL, OTRO
    agente         VARCHAR(200) NOT NULL, -- p. ej. penicilina, maní, polen
    reaccion       VARCHAR(300) NULL,
    severidad      VARCHAR(10)  NULL,     -- LEVE, MODERADA, SEVERA
    creado_por     BINARY(16)   NOT NULL,
    creado_en      DATETIME(6)  NOT NULL,
    actualizado_en DATETIME(6)  NOT NULL,
    CONSTRAINT fk_alergias_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_alergias_paciente FOREIGN KEY (paciente_id) REFERENCES pacientes (id),
    CONSTRAINT fk_alergias_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios (id),
    INDEX ix_alergias_paciente (tenant_id, paciente_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE paciente_antecedentes (
    id             BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id      BINARY(16)   NOT NULL,
    paciente_id    BINARY(16)   NOT NULL,
    tipo           VARCHAR(20)  NOT NULL, -- PATOLOGICO, QUIRURGICO, TRAUMATICO, TOXICO, FARMACOLOGICO, FAMILIAR,
                                          -- GINECO_OBSTETRICO, INMUNOLOGICO, OTRO
    descripcion    VARCHAR(500) NOT NULL,
    desde          VARCHAR(20)  NULL,     -- texto corto: "2019", "hace 5 años", "infancia"
    vigente        BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_por     BINARY(16)   NOT NULL,
    creado_en      DATETIME(6)  NOT NULL,
    actualizado_en DATETIME(6)  NOT NULL,
    CONSTRAINT fk_antecedentes_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_antecedentes_paciente FOREIGN KEY (paciente_id) REFERENCES pacientes (id),
    CONSTRAINT fk_antecedentes_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios (id),
    INDEX ix_antecedentes_paciente (tenant_id, paciente_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
