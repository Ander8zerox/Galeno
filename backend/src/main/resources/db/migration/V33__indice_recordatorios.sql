-- La tarea de recordatorios (cada 15 minutos) busca, por consultorio, las citas de las próximas 24 horas. Sin este
-- índice recorre todas las citas del consultorio (el de médico e inicio no sirve: no filtra por médico).
CREATE INDEX ix_citas_inicio ON citas (tenant_id, inicio);
