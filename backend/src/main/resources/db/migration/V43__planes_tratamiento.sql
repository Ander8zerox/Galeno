-- Plan de tratamiento con presupuesto (odontología, ver docs/diseno-plan-tratamiento.md).

CREATE TABLE planes_tratamiento (
    id             BINARY(16)    NOT NULL PRIMARY KEY,
    tenant_id      BINARY(16)    NOT NULL,
    paciente_id    BINARY(16)    NOT NULL,
    odontologo_id  BINARY(16)    NOT NULL,
    estado         VARCHAR(12)   NOT NULL, -- BORRADOR, PRESENTADO, ACEPTADO, TERMINADO, CANCELADO
    observaciones  VARCHAR(1000) NULL,
    vigencia_dias  INT           NOT NULL,
    presentado_en  DATETIME(6)   NULL,
    cerrado_en     DATETIME(6)   NULL,
    motivo_cierre  VARCHAR(300)  NULL,
    creado_en      DATETIME(6)   NOT NULL,
    actualizado_en DATETIME(6)   NOT NULL,
    CONSTRAINT fk_planes_tratamiento_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_planes_tratamiento_paciente FOREIGN KEY (paciente_id) REFERENCES pacientes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_planes_tratamiento_paciente ON planes_tratamiento (tenant_id, paciente_id, estado);

-- Cada aceptación del paciente: la primera (numero 1) y los adicionales (2, 3…). Lo aceptado no cambia.
CREATE TABLE planes_tratamiento_aceptaciones (
    id                BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id         BINARY(16)   NOT NULL,
    plan_id           BINARY(16)   NOT NULL,
    numero            INT          NOT NULL,
    aceptado_en       DATETIME(6)  NOT NULL,
    registrado_por    BINARY(16)   NOT NULL,
    firma             MEDIUMTEXT   NULL,     -- data:image/png;base64,… (sin firma = constancia verbal)
    nombre_firmante   VARCHAR(120) NULL,
    constancia_verbal BOOLEAN      NOT NULL,
    total             DECIMAL(14, 2) NOT NULL,
    CONSTRAINT fk_planes_aceptaciones_plan FOREIGN KEY (plan_id) REFERENCES planes_tratamiento (id),
    CONSTRAINT uq_planes_aceptaciones UNIQUE (plan_id, numero)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE planes_tratamiento_items (
    id                 BINARY(16)    NOT NULL PRIMARY KEY,
    tenant_id          BINARY(16)    NOT NULL,
    plan_id            BINARY(16)    NOT NULL,
    fase               INT           NOT NULL,
    fase_nombre        VARCHAR(80)   NULL,
    orden              INT           NOT NULL,
    codigo_cups        VARCHAR(10)   NOT NULL,
    descripcion        VARCHAR(400)  NOT NULL,
    diente             VARCHAR(2)    NULL,
    superficies        VARCHAR(5)    NULL,
    cantidad           INT           NOT NULL,
    valor_unitario     DECIMAL(12, 2) NOT NULL,
    descuento          DECIMAL(12, 2) NOT NULL,
    estado             VARCHAR(10)   NOT NULL, -- PENDIENTE, REALIZADO, CANCELADO
    aceptacion         INT           NULL,     -- número de la aceptación; nulo = aún sin aceptar (se puede editar)
    motivo_cancelacion VARCHAR(300)  NULL,
    atencion_id        BINARY(16)    NULL,     -- atención firmada donde se realizó
    realizado_en       DATETIME(6)   NULL,
    CONSTRAINT fk_planes_items_plan FOREIGN KEY (plan_id) REFERENCES planes_tratamiento (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_planes_items_plan ON planes_tratamiento_items (plan_id, fase, orden);
