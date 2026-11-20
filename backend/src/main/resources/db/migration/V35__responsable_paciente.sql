-- Responsable de un paciente menor de edad (padre, madre, acudiente o tutor). Obligatorio al guardar un menor.
-- Y quién acompaña al paciente en cada atención (para un menor, por defecto su responsable).

ALTER TABLE pacientes
    ADD COLUMN responsable_nombre           VARCHAR(200) NULL,
    ADD COLUMN responsable_parentesco       VARCHAR(40)  NULL,
    ADD COLUMN responsable_tipo_documento   VARCHAR(2)   NULL,
    ADD COLUMN responsable_numero_documento VARCHAR(30)  NULL,
    ADD COLUMN responsable_telefono         VARCHAR(30)  NULL;

ALTER TABLE atenciones
    ADD COLUMN acompanante_nombre     VARCHAR(200) NULL,
    ADD COLUMN acompanante_parentesco VARCHAR(40)  NULL;
