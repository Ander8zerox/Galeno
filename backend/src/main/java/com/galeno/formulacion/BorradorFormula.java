package com.galeno.formulacion;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Salida estructurada del modelo. El esquema JSON se deriva de este record, así que el modelo
 * siempre devuelve exactamente estos campos.
 */
public record BorradorFormula(
        @JsonPropertyDescription("Medicamentos indicados por el médico, en el orden en que los mencionó")
        List<ItemPropuesto> items,
        @JsonPropertyDescription("Alertas para el médico: datos faltantes, dosis inusuales, interacciones, alergias")
        List<String> advertencias) {

    public record ItemPropuesto(
            @JsonPropertyDescription("Principio activo por denominación común internacional, sin marca comercial")
            String medicamento,
            @JsonPropertyDescription("Concentración, p. ej. '500 mg' o '250 mg/5 mL'; vacío si el médico no la indicó")
            String concentracion,
            @JsonPropertyDescription("Forma farmacéutica, p. ej. tableta, cápsula, suspensión oral")
            String formaFarmaceutica,
            @JsonPropertyDescription("Vía de administración, p. ej. oral, intramuscular, tópica")
            String via,
            @JsonPropertyDescription("Dosis por toma, p. ej. '1 tableta' o '5 mL'")
            String dosis,
            @JsonPropertyDescription("Frecuencia, p. ej. 'cada 8 horas'")
            String frecuencia,
            @JsonPropertyDescription("Duración del tratamiento, p. ej. '7 días'")
            String duracion,
            @JsonPropertyDescription("Cantidad total a dispensar, en números y letras si es posible")
            String cantidad,
            @JsonPropertyDescription("Indicaciones adicionales para el paciente")
            String indicaciones) {
    }

    BorradorFormula conAdvertencias(List<String> adicionales) {
        List<String> todas = new ArrayList<>(adicionales);
        if (advertencias != null) {
            todas.addAll(advertencias);
        }
        return new BorradorFormula(items == null ? List.of() : items, todas);
    }
}
