package com.galeno.historia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/** Una orden médica de la atención (bloque 9 del RDA): qué se solicita, cuántas veces y con qué indicaciones. */
@Embeddable
public class OrdenMedica {

    public enum Tipo { LABORATORIO, IMAGEN, PROCEDIMIENTO, INTERCONSULTA }

    public enum Prioridad { RUTINARIA, URGENTE }

    static final int MAX_CANTIDAD = 99;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    /** CUPS del catálogo; null en una interconsulta o un estudio que no está en el catálogo. */
    @Column(name = "codigo_cups", length = 10)
    private String codigoCups;

    @Column(nullable = false, length = 400)
    private String descripcion;

    @Column(nullable = false)
    private int cantidad;

    @Column(length = 500)
    private String indicaciones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridad prioridad;

    protected OrdenMedica() {
    }

    /** @param descripcion la del catálogo si hay CUPS; si no, la que escribió el médico */
    public OrdenMedica(Tipo tipo, String codigoCups, String descripcion, int cantidad, String indicaciones,
                       Prioridad prioridad) {
        if (tipo == null) {
            throw ApiProblem.badRequest("Indique el tipo de la orden");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw ApiProblem.badRequest("Indique qué se ordena (el CUPS o la descripción)");
        }
        if (cantidad < 1 || cantidad > MAX_CANTIDAD) {
            throw ApiProblem.badRequest("La cantidad de una orden debe estar entre 1 y " + MAX_CANTIDAD);
        }
        this.tipo = tipo;
        this.codigoCups = codigoCups;
        this.descripcion = descripcion.strip();
        this.cantidad = cantidad;
        this.indicaciones = indicaciones == null || indicaciones.isBlank() ? null : indicaciones.strip();
        this.prioridad = prioridad == null ? Prioridad.RUTINARIA : prioridad;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getCodigoCups() {
        return codigoCups;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public String getIndicaciones() {
        return indicaciones;
    }

    public Prioridad getPrioridad() {
        return prioridad;
    }
}
