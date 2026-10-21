-- La semilla de V4 incluía R50X, que no existe en la tabla oficial de MinSalud (la fiebre no especificada es R509).
-- Se deshabilita en lugar de borrarla: las atenciones guardan el código como texto y deben seguir legibles.
UPDATE cie10 SET habilitado = 0 WHERE codigo = 'R50X';
