package com.galeno.apoyodiagnostico;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Salida estructurada del asistente: apoyo a la decisión clínica, nunca un diagnóstico definitivo. */
public record SugerenciasDiagnosticas(
        @JsonPropertyDescription("Diagnósticos diferenciales ordenados de más a menos probable (máximo 5)")
        List<Diferencial> diferenciales,
        @JsonPropertyDescription("Datos de la historia, examen físico o paraclínicos que ayudarían a confirmar o descartar")
        List<String> datosFaltantes,
        @JsonPropertyDescription("Signos de alarma presentes o que se deben vigilar y que requieren atención urgente")
        List<String> signosDeAlarma) {

    public enum Probabilidad {
        ALTA, MEDIA, BAJA
    }

    public record Diferencial(
            @JsonPropertyDescription("Código CIE-10, p. ej. J02.9")
            String codigoCie10,
            @JsonPropertyDescription("Nombre del diagnóstico en español")
            String descripcion,
            Probabilidad probabilidad,
            @JsonPropertyDescription("Hallazgos del caso que apoyan o contradicen este diagnóstico, en 1-3 frases")
            String razonamiento) {
    }
}
