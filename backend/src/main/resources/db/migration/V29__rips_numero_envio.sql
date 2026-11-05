-- RIPS sin factura: el número de nota (numNota) es un número propio del prestador, único para cada envío (lineamientos
-- del Ministerio, numeral 4.4.2). Se asigna al generar el lote (1, 2, 3… por consultorio) y no se reutiliza, ni de lotes
-- anulados. Las facturas normales no lo llevan.
ALTER TABLE rips_lotes ADD COLUMN numero_envio INT,
    ADD CONSTRAINT uk_rips_lotes_envio UNIQUE (tenant_id, numero_envio);
