-- Un documento subido por error se anula (con motivo, quién y cuándo); nunca se borra: el archivo sigue en el almacén y
-- el documento sigue en la historia, marcado como anulado.
ALTER TABLE documentos_clinicos
    ADD COLUMN anulado_en       DATETIME(6),
    ADD COLUMN anulado_por      BINARY(16),
    ADD COLUMN motivo_anulacion VARCHAR(300);
