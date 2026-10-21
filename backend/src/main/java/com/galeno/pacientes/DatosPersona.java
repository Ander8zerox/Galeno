package com.galeno.pacientes;

import java.time.LocalDate;

/**
 * Datos de una persona que llegan de un listado de otro sistema. Un campo en {@code null} significa que el
 * listado no lo trae (la columna no existe o la celda está vacía): nunca borra lo que ya hay en Galeno.
 */
public record DatosPersona(String nombres, String apellidos, LocalDate fechaNacimiento, String sexo, String telefono,
                           String email) {

    /** Lo mínimo para crear un paciente que no existe. */
    public boolean completaParaCrear() {
        return nombres != null && apellidos != null && fechaNacimiento != null && sexo != null;
    }

    /** Un cambio que la importación hizo (o haría) sobre un campo, para mostrarlo y para poder deshacerlo. */
    public record Cambio(String campo, String antes, String despues) {
    }
}
