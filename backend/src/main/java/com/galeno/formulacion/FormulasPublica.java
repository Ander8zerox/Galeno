package com.galeno.formulacion;

import java.util.Collection;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública del módulo de fórmulas para otros módulos. */
@Service
public class FormulasPublica implements com.galeno.historia.MedicamentosDeAtencion {

    private final FormulaRepository formulas;

    FormulasPublica(FormulaRepository formulas) {
        this.formulas = formulas;
    }

    @Transactional(readOnly = true)
    public long delPaciente(UUID pacienteId) {
        return formulas.countByPacienteId(pacienteId);
    }

    /** "Losartán 50 mg tableta oral · 1 tableta cada 12 horas por 90 días" por cada ítem de las fórmulas firmadas. */
    @Override
    @Transactional(readOnly = true)
    public java.util.Map<UUID, java.util.List<String>> lineas(Collection<UUID> atencionIds) {
        if (atencionIds.isEmpty()) {
            return java.util.Map.of();
        }
        return formulas.findByAtencionIdIn(atencionIds).stream().collect(java.util.stream.Collectors.groupingBy(
                Formula::getAtencionId, java.util.stream.Collectors.flatMapping(f -> f.getItems().stream().map(FormulasPublica::linea),
                        java.util.stream.Collectors.toList())));
    }

    private static String linea(FormulaItem i) {
        String nombre = java.util.stream.Stream.of(i.getMedicamento(), i.getConcentracion(), i.getFormaFarmaceutica(), i.getVia())
                .filter(s -> s != null && !s.isBlank()).collect(java.util.stream.Collectors.joining(" "));
        String uso = java.util.stream.Stream.of(i.getDosis(), i.getFrecuencia() == null ? null : "cada " + i.getFrecuencia().replaceFirst("(?i)^cada ", ""),
                        i.getDuracion() == null ? null : "por " + i.getDuracion().replaceFirst("(?i)^por ", ""),
                        i.getCantidad() == null ? null : "cantidad " + i.getCantidad())
                .filter(s -> s != null && !s.isBlank()).collect(java.util.stream.Collectors.joining(" "));
        return uso.isEmpty() ? nombre : nombre + " · " + uso;
    }

    @Transactional(readOnly = true)
    public long ligadasA(Collection<UUID> atencionIds) {
        return atencionIds.isEmpty() ? 0 : formulas.countByAtencionIdIn(atencionIds);
    }
}
