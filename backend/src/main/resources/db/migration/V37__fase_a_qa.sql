-- Ajustes de la revisión de QA (fase A).

-- Oportunidad de la cita (Res. 1552 de 2013): cuándo la pidió el paciente y qué fecha deseaba.
-- Sobrecupo con motivo, y justificación cuando se confirma sin los datos mínimos del RIPS.
ALTER TABLE citas
    ADD COLUMN solicitada_en          DATETIME(6)  NULL,
    ADD COLUMN fecha_deseada          DATE         NULL,
    ADD COLUMN motivo_sobrecupo       VARCHAR(300) NULL,
    ADD COLUMN justificacion_sin_rips VARCHAR(300) NULL;

UPDATE citas SET solicitada_en = creada_en WHERE solicitada_en IS NULL;

-- Tope de sobrecupos por día de cada médico (NULL: sin tope).
ALTER TABLE agendas_medico
    ADD COLUMN max_sobrecupos_dia INT NULL;

-- Prioridad en la atención y autorización de tratamiento de datos personales (Ley 1581 de 2012), con su evidencia.
ALTER TABLE pacientes
    ADD COLUMN gestante                  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN discapacidad              BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN autorizacion_datos_en     DATETIME(6) NULL,
    ADD COLUMN autorizacion_datos_por    BINARY(16)  NULL,
    ADD COLUMN autorizacion_datos_medio  VARCHAR(10) NULL,
    ADD COLUMN autorizacion_datos_texto  VARCHAR(10) NULL;

-- Documento de quien acompaña al paciente en la atención.
ALTER TABLE atenciones
    ADD COLUMN acompanante_tipo_documento   VARCHAR(2)  NULL,
    ADD COLUMN acompanante_numero_documento VARCHAR(30) NULL;
