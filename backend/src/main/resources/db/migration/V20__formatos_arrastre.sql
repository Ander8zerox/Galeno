-- Campos con "arrastrar" (p. ej. el odontograma): al agregar el formato, parten de la última atención firmada del
-- paciente con ese formato. Se guarda de cuál, para mostrarlo.
ALTER TABLE atencion_formatos
    ADD COLUMN arrastrado_de BINARY(16) AFTER formato_version_id,
    ADD CONSTRAINT fk_atencion_formatos_arrastrado FOREIGN KEY (arrastrado_de) REFERENCES atenciones (id);
