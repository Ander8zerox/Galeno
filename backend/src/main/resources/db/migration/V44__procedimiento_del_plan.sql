-- Un procedimiento realizado puede venir de un plan de tratamiento: al firmar la atención, el ítem queda realizado.
ALTER TABLE atencion_procedimientos
    ADD COLUMN item_plan_id BINARY(16) NULL;

CREATE INDEX ix_planes_items_atencion ON planes_tratamiento_items (atencion_id);
