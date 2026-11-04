package com.galeno.modulos;

/** Funciones opcionales que cada consultorio activa o no. Sin activar, no se ven ni responden. */
public enum Modulo {
    /** Procedimientos con CUPS en la atención, lista de precios y generación de RIPS (ver docs/diseno-rips.md). */
    RIPS("RIPS y facturación",
            "Procedimientos con CUPS en cada atención, lista de precios y generación de los RIPS para facturar a EPS y "
                    + "prepagadas.");

    private final String nombre;
    private final String descripcion;

    Modulo(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String nombre() {
        return nombre;
    }

    public String descripcion() {
        return descripcion;
    }
}
