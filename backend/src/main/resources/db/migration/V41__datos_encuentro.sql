-- Datos del encuentro (bloque 4 del RDA) en cada atención: hora de fin y, para el RIPS, modalidad, grupo de servicios
-- y causa externa (antes solo del prestador o del lote), más el entorno de la atención.
ALTER TABLE atenciones
    ADD COLUMN finalizada_en      DATETIME(6) NULL,
    ADD COLUMN modalidad_atencion VARCHAR(2)  NULL,
    ADD COLUMN grupo_servicios    VARCHAR(2)  NULL,
    ADD COLUMN causa_externa      VARCHAR(2)  NULL,
    ADD COLUMN entorno            VARCHAR(40) NULL;

-- Las firmadas antes terminan cuando se firmaron
UPDATE atenciones SET finalizada_en = firmada_en WHERE finalizada_en IS NULL AND firmada_en IS NOT NULL;
