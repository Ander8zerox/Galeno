-- En los festivos de Colombia la agenda no ofrece cupos, salvo para los médicos que atienden esos días.
ALTER TABLE agendas_medico ADD COLUMN atiende_festivos BOOLEAN NOT NULL DEFAULT FALSE;
