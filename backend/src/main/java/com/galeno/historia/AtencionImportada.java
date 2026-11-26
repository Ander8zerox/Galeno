package com.galeno.historia;

import java.math.BigDecimal;

/**
 * Contenido clínico de una atención que llega de otro sistema en la plantilla de importación de Galenum (a diferencia
 * del RIPS, que solo trae el servicio y los diagnósticos). Los textos ya vienen recortados a lo que admite la atención.
 */
public record AtencionImportada(String profesional, String registroMedico, String motivoConsulta, String enfermedadActual,
                                String revisionSistemas, String examenFisico, Integer taSistolica, Integer taDiastolica,
                                Integer frecuenciaCardiaca, Integer frecuenciaRespiratoria, BigDecimal temperatura,
                                Integer saturacionO2, BigDecimal pesoKg, BigDecimal tallaCm, String analisis, String plan) {
}
