-- Motor de formatos clínicos (ver docs/diseno-formatos.md). Un formato es un dato: su definición (secciones y campos)
-- es JSON. El catálogo es global; cada consultorio activa los que usa y cada profesional elige los suyos por defecto.

-- Catálogo global (sin tenant_id). Lo carga CatalogoFormatos desde resources/formatos al arrancar.
CREATE TABLE formatos (
    id           BINARY(16)   NOT NULL PRIMARY KEY,
    codigo       VARCHAR(60)  NOT NULL,
    nombre       VARCHAR(150) NOT NULL,
    especialidad VARCHAR(80)  NOT NULL,
    descripcion  VARCHAR(500),
    CONSTRAINT uk_formatos_codigo UNIQUE (codigo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Una versión publicada no cambia nunca: las respuestas apuntan a la versión con la que se diligenciaron.
CREATE TABLE formato_versiones (
    id           BINARY(16)  NOT NULL PRIMARY KEY,
    formato_id   BINARY(16)  NOT NULL,
    version      INT         NOT NULL,
    definicion   JSON        NOT NULL,
    hash         VARCHAR(64) NOT NULL, -- SHA-256 de la definición: si el archivo cambia, el arranque falla
    publicada_en DATETIME(6) NOT NULL,
    CONSTRAINT uk_formato_versiones UNIQUE (formato_id, version),
    CONSTRAINT fk_formato_versiones_formato FOREIGN KEY (formato_id) REFERENCES formatos (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Formatos disponibles en el consultorio (la fila existe = activo). Desactivar no toca lo ya diligenciado.
CREATE TABLE formatos_consultorio (
    id          BINARY(16)  NOT NULL PRIMARY KEY,
    tenant_id   BINARY(16)  NOT NULL,
    formato_id  BINARY(16)  NOT NULL,
    activado_en DATETIME(6) NOT NULL,
    CONSTRAINT uk_formatos_consultorio UNIQUE (tenant_id, formato_id),
    CONSTRAINT fk_formatos_consultorio_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_formatos_consultorio_formato FOREIGN KEY (formato_id) REFERENCES formatos (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Los que se agregan solos a las atenciones nuevas de ese profesional (si siguen activos en el consultorio).
CREATE TABLE formatos_profesional (
    id         BINARY(16) NOT NULL PRIMARY KEY,
    tenant_id  BINARY(16) NOT NULL,
    usuario_id BINARY(16) NOT NULL,
    formato_id BINARY(16) NOT NULL,
    CONSTRAINT uk_formatos_profesional UNIQUE (tenant_id, usuario_id, formato_id),
    CONSTRAINT fk_formatos_profesional_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_formatos_profesional_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_formatos_profesional_formato FOREIGN KEY (formato_id) REFERENCES formatos (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Formatos diligenciados en una atención: se editan con el borrador y quedan inmutables al firmarla.
CREATE TABLE atencion_formatos (
    id                 BINARY(16) NOT NULL PRIMARY KEY,
    tenant_id          BINARY(16) NOT NULL,
    atencion_id        BINARY(16) NOT NULL,
    formato_version_id BINARY(16) NOT NULL,
    respuestas         JSON       NOT NULL,
    orden              INT        NOT NULL,
    CONSTRAINT fk_atencion_formatos_consultorio FOREIGN KEY (tenant_id) REFERENCES consultorios (id),
    CONSTRAINT fk_atencion_formatos_atencion FOREIGN KEY (atencion_id) REFERENCES atenciones (id),
    CONSTRAINT fk_atencion_formatos_version FOREIGN KEY (formato_version_id) REFERENCES formato_versiones (id),
    INDEX ix_atencion_formatos_atencion (tenant_id, atencion_id, orden)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
