-- Una persona (usuario global) puede pertenecer a varios consultorios, con varios roles en cada uno.

-- Datos profesionales del médico: personales (ReTHUS), no dependen del consultorio
CREATE TABLE perfiles_medicos (
    usuario_id      BINARY(16)   NOT NULL PRIMARY KEY,
    registro_medico VARCHAR(50)  NOT NULL,
    especialidad    VARCHAR(120),
    actualizado_en  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_perfiles_medicos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE membresias (
    id         BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id  BINARY(16)  NOT NULL,
    usuario_id BINARY(16)  NOT NULL,
    estado     VARCHAR(20) NOT NULL,
    creado_en  DATETIME(6) NOT NULL,
    CONSTRAINT uk_membresias_usuario UNIQUE (tenant_id, usuario_id),
    CONSTRAINT fk_membresias_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_membresias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    INDEX ix_membresias_usuario (usuario_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE membresia_roles (
    membresia_id BINARY(16)  NOT NULL,
    rol          VARCHAR(20) NOT NULL,
    PRIMARY KEY (membresia_id, rol),
    CONSTRAINT fk_membresia_roles_membresia FOREIGN KEY (membresia_id) REFERENCES membresias (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Qué médicos atiende cada secretario (para la agenda)
CREATE TABLE asignaciones_secretario (
    id                     BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id              BINARY(16)  NOT NULL,
    secretario_membresia_id BINARY(16) NOT NULL,
    medico_membresia_id    BINARY(16)  NOT NULL,
    creado_en              DATETIME(6) NOT NULL,
    CONSTRAINT uk_asignacion UNIQUE (secretario_membresia_id, medico_membresia_id),
    CONSTRAINT fk_asignacion_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_asignacion_secretario FOREIGN KEY (secretario_membresia_id) REFERENCES membresias (id),
    CONSTRAINT fk_asignacion_medico FOREIGN KEY (medico_membresia_id) REFERENCES membresias (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE invitaciones (
    id              BINARY(16)   NOT NULL PRIMARY KEY,
    tenant_id       BINARY(16)   NOT NULL,
    email           VARCHAR(190) NOT NULL,
    roles           VARCHAR(100) NOT NULL, -- lista separada por comas
    registro_medico VARCHAR(50),
    token_hash      CHAR(64)     NOT NULL, -- SHA-256 del token; el token en claro nunca se guarda
    estado          VARCHAR(20)  NOT NULL,
    invitado_por    BINARY(16)   NOT NULL,
    expira_en       DATETIME(6)  NOT NULL,
    creado_en       DATETIME(6)  NOT NULL,
    aceptada_en     DATETIME(6),
    CONSTRAINT uk_invitaciones_token UNIQUE (token_hash),
    CONSTRAINT fk_invitaciones_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_invitaciones_invitado_por FOREIGN KEY (invitado_por) REFERENCES usuarios (id),
    INDEX ix_invitaciones_email (tenant_id, email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Migración de los datos existentes: cada usuario pasa a tener una membresía en su consultorio
INSERT INTO perfiles_medicos (usuario_id, registro_medico, actualizado_en)
SELECT id, registro_medico, creado_en FROM usuarios WHERE registro_medico IS NOT NULL;

INSERT INTO membresias (id, tenant_id, usuario_id, estado, creado_en)
SELECT uuid_to_bin(uuid()), tenant_id, id, IF(activo, 'ACTIVA', 'SUSPENDIDA'), creado_en FROM usuarios;

INSERT INTO membresia_roles (membresia_id, rol)
SELECT m.id, IF(u.rol = 'ASISTENTE', 'SECRETARIO', u.rol)
FROM membresias m JOIN usuarios u ON u.id = m.usuario_id;

-- Todo consultorio existente necesita al menos un administrador: lo es cada médico actual
INSERT IGNORE INTO membresia_roles (membresia_id, rol)
SELECT m.id, 'ADMIN' FROM membresias m JOIN usuarios u ON u.id = m.usuario_id WHERE u.rol = 'MEDICO';

-- La fórmula guarda el registro médico vigente al firmar (trazabilidad legal)
ALTER TABLE formulas ADD COLUMN registro_medico VARCHAR(50) AFTER medico_id;

ALTER TABLE usuarios
    DROP FOREIGN KEY fk_usuarios_consultorio,
    DROP COLUMN tenant_id,
    DROP COLUMN rol,
    DROP COLUMN registro_medico;
