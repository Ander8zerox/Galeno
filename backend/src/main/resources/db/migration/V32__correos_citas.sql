-- Correos a los pacientes sobre sus citas (agendada, cambios y recordatorio 24 h antes).

-- Datos de contacto del consultorio para los correos, y si los envía. Los consultorios que ya existen empiezan sin
-- correos (sus pacientes no reciben correos por sorpresa); los nuevos se crean con correos activos.
ALTER TABLE consultorios
    ADD COLUMN direccion         VARCHAR(200),
    ADD COLUMN telefono          VARCHAR(40),
    ADD COLUMN correo_contacto   VARCHAR(190),
    ADD COLUMN correos_pacientes BIT NOT NULL DEFAULT 0;

-- Cuándo se le recordó la cita al paciente (o se le avisó dentro de las 24 h previas): evita repetir el recordatorio.
ALTER TABLE citas ADD COLUMN recordatorio_enviado_en DATETIME(6);

-- El paciente pidió no recibir correos sobre sus citas.
ALTER TABLE pacientes ADD COLUMN sin_correos_citas BIT NOT NULL DEFAULT 0;
