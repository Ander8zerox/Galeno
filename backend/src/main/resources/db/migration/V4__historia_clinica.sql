-- Historia clínica: atenciones (borrador → firmada e inmutable), diagnósticos CIE-10, notas aclaratorias,
-- documentos soporte y auditoría de acceso (Res. 1995 de 1999).

CREATE TABLE atenciones (
    id                  BINARY(16)    NOT NULL PRIMARY KEY,
    tenant_id           BINARY(16)    NOT NULL,
    paciente_id         BINARY(16)    NOT NULL,
    medico_membresia_id BINARY(16)    NOT NULL,
    medico_usuario_id   BINARY(16)    NOT NULL,
    medico_nombre       VARCHAR(200)  NOT NULL, -- copia al crear: el nombre no cambia en la historia
    registro_medico     VARCHAR(50),            -- copia al firmar
    cita_id             BINARY(16),
    origen              VARCHAR(20)   NOT NULL, -- GALENO | IMPORTADA
    estado              VARCHAR(20)   NOT NULL, -- BORRADOR | FIRMADA
    fecha_atencion      DATETIME(6)   NOT NULL,
    motivo_consulta     VARCHAR(1000),
    enfermedad_actual   TEXT,
    antecedentes        TEXT,
    revision_sistemas   TEXT,
    ta_sistolica        INT,
    ta_diastolica       INT,
    frecuencia_cardiaca INT,
    frecuencia_respiratoria INT,
    temperatura         DECIMAL(4, 1),
    saturacion_o2       INT,
    peso_kg             DECIMAL(5, 1),
    talla_cm            DECIMAL(5, 1),
    examen_fisico       TEXT,
    analisis            TEXT,
    plan                TEXT,
    version             BIGINT        NOT NULL,
    creada_en           DATETIME(6)   NOT NULL,
    actualizada_en      DATETIME(6)   NOT NULL,
    firmada_en          DATETIME(6),
    CONSTRAINT uk_atenciones_cita UNIQUE (cita_id),
    CONSTRAINT fk_atenciones_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_atenciones_paciente FOREIGN KEY (paciente_id) REFERENCES pacientes (id),
    CONSTRAINT fk_atenciones_medico FOREIGN KEY (medico_membresia_id) REFERENCES membresias (id),
    CONSTRAINT fk_atenciones_cita FOREIGN KEY (cita_id) REFERENCES citas (id),
    INDEX ix_atenciones_paciente (tenant_id, paciente_id, fecha_atencion)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE atencion_diagnosticos (
    atencion_id      BINARY(16)   NOT NULL,
    orden            INT          NOT NULL,
    codigo_cie10     VARCHAR(10)  NOT NULL,
    descripcion      VARCHAR(300) NOT NULL,
    principal        BIT          NOT NULL,
    tipo             VARCHAR(30)  NOT NULL, -- IMPRESION_DIAGNOSTICA | CONFIRMADO_NUEVO | CONFIRMADO_REPETIDO (como en RIPS)
    PRIMARY KEY (atencion_id, orden),
    CONSTRAINT fk_atencion_diagnosticos FOREIGN KEY (atencion_id) REFERENCES atenciones (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notas_aclaratorias (
    id          BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id   BINARY(16)   NOT NULL,
    atencion_id BINARY(16)   NOT NULL,
    autor_id    BINARY(16)   NOT NULL,
    autor_nombre VARCHAR(200) NOT NULL,
    texto       TEXT         NOT NULL,
    creada_en   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_notas_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_notas_atencion FOREIGN KEY (atencion_id) REFERENCES atenciones (id),
    INDEX ix_notas_atencion (atencion_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Archivos (PDF, imágenes) del paciente: soportes, resultados, historias de otros sistemas
CREATE TABLE documentos_clinicos (
    id             BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id      BINARY(16)   NOT NULL,
    paciente_id    BINARY(16)   NOT NULL,
    atencion_id    BINARY(16),
    tipo           VARCHAR(30)  NOT NULL,
    descripcion    VARCHAR(300),
    nombre_archivo VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    tamano_bytes   BIGINT       NOT NULL,
    sha256         CHAR(64)     NOT NULL,
    storage_key    VARCHAR(400) NOT NULL,
    origen         VARCHAR(20)  NOT NULL,
    subido_por     BINARY(16)   NOT NULL,
    creado_en      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_documentos_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_documentos_paciente FOREIGN KEY (paciente_id) REFERENCES pacientes (id),
    CONSTRAINT fk_documentos_atencion FOREIGN KEY (atencion_id) REFERENCES atenciones (id),
    INDEX ix_documentos_paciente (tenant_id, paciente_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Quién consultó qué historia y cuándo
CREATE TABLE accesos_historia (
    id          BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id   BINARY(16)  NOT NULL,
    paciente_id BINARY(16)  NOT NULL,
    usuario_id  BINARY(16)  NOT NULL,
    accion      VARCHAR(30) NOT NULL,
    recurso_id  BINARY(16),
    creado_en   DATETIME(6) NOT NULL,
    CONSTRAINT fk_accesos_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    INDEX ix_accesos_paciente (tenant_id, paciente_id, creado_en),
    INDEX ix_accesos_usuario (tenant_id, usuario_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE formulas
    ADD COLUMN atencion_id BINARY(16) AFTER ai_job_id,
    ADD CONSTRAINT fk_formulas_atencion FOREIGN KEY (atencion_id) REFERENCES atenciones (id);

-- Catálogo CIE-10 (global). Semilla con diagnósticos frecuentes de consulta externa;
-- la tabla completa de MinSalud se carga después con una migración de datos.
CREATE TABLE cie10 (
    codigo      VARCHAR(10)  NOT NULL PRIMARY KEY,
    descripcion VARCHAR(300) NOT NULL,
    FULLTEXT INDEX ft_cie10_descripcion (descripcion)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO cie10 (codigo, descripcion) VALUES
('A09X', 'Infecciones intestinales debidas a otros organismos sin especificar'),
('B349', 'Infección viral, no especificada'),
('D509', 'Anemia por deficiencia de hierro sin otra especificación'),
('E039', 'Hipotiroidismo, no especificado'),
('E119', 'Diabetes mellitus no insulinodependiente sin mención de complicación'),
('E669', 'Obesidad, no especificada'),
('E785', 'Hiperlipidemia no especificada'),
('F329', 'Episodio depresivo, no especificado'),
('F419', 'Trastorno de ansiedad, no especificado'),
('F510', 'Insomnio no orgánico'),
('G439', 'Migraña, no especificada'),
('G442', 'Cefalea debida a tensión'),
('H103', 'Conjuntivitis aguda, no especificada'),
('H660', 'Otitis media supurativa aguda'),
('H669', 'Otitis media, no especificada'),
('I10X', 'Hipertensión esencial (primaria)'),
('I839', 'Venas varicosas de los miembros inferiores sin úlcera ni inflamación'),
('J00X', 'Rinofaringitis aguda (resfriado común)'),
('J010', 'Sinusitis maxilar aguda'),
('J019', 'Sinusitis aguda, no especificada'),
('J029', 'Faringitis aguda, no especificada'),
('J030', 'Amigdalitis estreptocócica'),
('J039', 'Amigdalitis aguda, no especificada'),
('J069', 'Infección aguda de las vías respiratorias superiores, no especificada'),
('J111', 'Influenza con otras manifestaciones respiratorias, virus no identificado'),
('J189', 'Neumonía, no especificada'),
('J209', 'Bronquitis aguda, no especificada'),
('J304', 'Rinitis alérgica, no especificada'),
('J459', 'Asma, no especificada'),
('K029', 'Caries dental, no especificada'),
('K210', 'Enfermedad del reflujo gastroesofágico con esofagitis'),
('K219', 'Enfermedad del reflujo gastroesofágico sin esofagitis'),
('K297', 'Gastritis, no especificada'),
('K30X', 'Dispepsia'),
('K589', 'Síndrome del colon irritable sin diarrea'),
('K590', 'Constipación'),
('L209', 'Dermatitis atópica, de tipo no especificado'),
('L239', 'Dermatitis alérgica de contacto, de causa no especificada'),
('L309', 'Dermatitis, no especificada'),
('L700', 'Acné vulgar'),
('M545', 'Lumbago no especificado'),
('M542', 'Cervicalgia'),
('M255', 'Dolor en articulación'),
('M791', 'Mialgia'),
('N390', 'Infección de vías urinarias, sitio no especificado'),
('N760', 'Vaginitis aguda'),
('N925', 'Otras menstruaciones irregulares especificadas'),
('N944', 'Dismenorrea primaria'),
('R05X', 'Tos'),
('R101', 'Dolor abdominal localizado en parte superior'),
('R104', 'Otros dolores abdominales y los no especificados'),
('R11X', 'Náusea y vómito'),
('R42X', 'Mareo y desvanecimiento'),
('R50X', 'Fiebre de otro origen y de origen desconocido'),
('R51X', 'Cefalea'),
('R53X', 'Malestar y fatiga'),
('Z000', 'Examen médico general'),
('Z001', 'Control de salud de rutina del niño'),
('Z010', 'Examen de ojos y de la visión'),
('Z014', 'Examen ginecológico (general) (de rutina)'),
('Z300', 'Consejo y asesoramiento general sobre la anticoncepción'),
('Z348', 'Supervisión de otros embarazos normales'),
('Z719', 'Consulta, no especificada'),
('Z760', 'Consulta para repetición de receta');
