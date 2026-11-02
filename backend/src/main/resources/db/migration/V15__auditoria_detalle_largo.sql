-- El cambio de los datos de pago se audita con el texto anterior y el nuevo (hasta 2.000 caracteres cada uno):
-- no cabe en VARCHAR(500).
ALTER TABLE auditoria_plataforma MODIFY detalle TEXT;
