package com.galeno.historia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Un factor de riesgo identificado en la atención (bloque 10 del RDA), con una observación opcional. */
@Embeddable
public class FactorRiesgo {

    /** Lista inicial (validar con un médico); el orden es el de la pantalla. */
    public enum Codigo {
        TABAQUISMO("Tabaquismo"),
        ALCOHOL("Consumo de alcohol"),
        SUSTANCIAS_PSICOACTIVAS("Consumo de sustancias psicoactivas"),
        SEDENTARISMO("Sedentarismo"),
        OBESIDAD("Sobrepeso u obesidad"),
        HIPERTENSION("Hipertensión arterial"),
        DIABETES("Diabetes"),
        DISLIPIDEMIA("Dislipidemia"),
        ANTECEDENTE_FAMILIAR_CARDIOVASCULAR("Antecedente familiar cardiovascular"),
        EMBARAZO_ALTO_RIESGO("Embarazo de alto riesgo"),
        RIESGO_CAIDAS("Riesgo de caídas"),
        OTRO("Otro");

        private final String nombre;

        Codigo(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Codigo codigo;

    @Column(length = 300)
    private String observacion;

    protected FactorRiesgo() {
    }

    public FactorRiesgo(Codigo codigo, String observacion) {
        this.codigo = codigo;
        this.observacion = observacion == null || observacion.isBlank() ? null : observacion.strip();
    }

    public Codigo getCodigo() {
        return codigo;
    }

    public String getObservacion() {
        return observacion;
    }

    /** "Tabaquismo (10 cigarrillos al día)" */
    public String texto() {
        return codigo.nombre() + (observacion == null ? "" : " (" + observacion + ")");
    }
}
