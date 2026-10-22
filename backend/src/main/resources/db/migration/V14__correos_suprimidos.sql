-- Direcciones a las que no se vuelve a escribir: rebote permanente (no existe) o queja (marcó el correo como spam).
-- Las registra la API con los eventos de SES; enviarles de nuevo daña la reputación del dominio y SES puede
-- suspender el envío. Un operador de la plataforma puede quitar una dirección si el usuario la corrigió.
CREATE TABLE correos_suprimidos (
    email        VARCHAR(190) NOT NULL PRIMARY KEY,
    motivo       VARCHAR(10)  NOT NULL, -- REBOTE | QUEJA
    detalle      VARCHAR(500),
    eventos      INT          NOT NULL DEFAULT 1,
    creado_en    DATETIME(6)  NOT NULL,
    ultimo_en    DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
