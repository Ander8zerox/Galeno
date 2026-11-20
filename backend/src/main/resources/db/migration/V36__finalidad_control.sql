-- Finalidad de las consultas de control (CUPS 8903xx), aparte de la de primera vez: suele ser 16 (tratamiento) y la
-- de primera vez 15 (diagnóstico). Vacía: los controles usan la misma de primera vez, como hasta ahora.

ALTER TABLE rips_prestador ADD COLUMN finalidad_control VARCHAR(2) NULL;
