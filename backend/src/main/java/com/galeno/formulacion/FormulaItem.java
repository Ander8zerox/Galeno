package com.galeno.formulacion;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class FormulaItem {

    @Column(nullable = false, length = 200)
    private String medicamento;

    @Column(length = 60)
    private String concentracion;

    @Column(name = "forma_farmaceutica", length = 60)
    private String formaFarmaceutica;

    @Column(length = 40)
    private String via;

    @Column(nullable = false, length = 60)
    private String dosis;

    @Column(nullable = false, length = 60)
    private String frecuencia;

    @Column(nullable = false, length = 60)
    private String duracion;

    @Column(length = 60)
    private String cantidad;

    @Column(length = 500)
    private String indicaciones;

    protected FormulaItem() {
    }

    public FormulaItem(String medicamento, String concentracion, String formaFarmaceutica, String via, String dosis,
                       String frecuencia, String duracion, String cantidad, String indicaciones) {
        this.medicamento = medicamento;
        this.concentracion = concentracion;
        this.formaFarmaceutica = formaFarmaceutica;
        this.via = via;
        this.dosis = dosis;
        this.frecuencia = frecuencia;
        this.duracion = duracion;
        this.cantidad = cantidad;
        this.indicaciones = indicaciones;
    }

    public String getMedicamento() {
        return medicamento;
    }

    public String getConcentracion() {
        return concentracion;
    }

    public String getFormaFarmaceutica() {
        return formaFarmaceutica;
    }

    public String getVia() {
        return via;
    }

    public String getDosis() {
        return dosis;
    }

    public String getFrecuencia() {
        return frecuencia;
    }

    public String getDuracion() {
        return duracion;
    }

    public String getCantidad() {
        return cantidad;
    }

    public String getIndicaciones() {
        return indicaciones;
    }
}
