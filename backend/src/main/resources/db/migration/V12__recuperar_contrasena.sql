-- Recuperación de contraseña: enlace de un solo uso por correo (1 hora); solo se guarda el SHA-256 del token.
CREATE TABLE recuperaciones_contrasena (
    id         BINARY(16)  NOT NULL PRIMARY KEY,
    usuario_id BINARY(16)  NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    ip         VARCHAR(45),
    creado_en  DATETIME(6) NOT NULL,
    expira_en  DATETIME(6) NOT NULL,
    usado_en   DATETIME(6),
    CONSTRAINT uk_recuperaciones_token UNIQUE (token_hash),
    CONSTRAINT fk_recuperaciones_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    INDEX ix_recuperaciones_usuario (usuario_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Los tokens emitidos antes de esta fecha dejan de servir (cambio de contraseña = cerrar todas las sesiones)
ALTER TABLE usuarios ADD COLUMN credenciales_cambiadas_en DATETIME(6);
