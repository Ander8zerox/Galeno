-- RIPS: registro SIRAS (campo U12 del Documento Técnico 1). Es el radicado que da la plataforma SIRAS de la ADRES al
-- reportar la atención de una víctima de accidente de tránsito; obligatorio con tipo de usuario 10 (SOAT) o 14
-- (lesionado sin SOAT). Va con el cobro, como la autorización: es dato del pagador, no de la historia clínica.
ALTER TABLE rips_cobros ADD COLUMN registro_siras VARCHAR(60);
