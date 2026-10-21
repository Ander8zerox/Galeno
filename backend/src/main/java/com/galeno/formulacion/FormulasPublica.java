package com.galeno.formulacion;

import java.util.Collection;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública del módulo de fórmulas para otros módulos. */
@Service
public class FormulasPublica {

    private final FormulaRepository formulas;

    FormulasPublica(FormulaRepository formulas) {
        this.formulas = formulas;
    }

    @Transactional(readOnly = true)
    public long delPaciente(UUID pacienteId) {
        return formulas.countByPacienteId(pacienteId);
    }

    @Transactional(readOnly = true)
    public long ligadasA(Collection<UUID> atencionIds) {
        return atencionIds.isEmpty() ? 0 : formulas.countByAtencionIdIn(atencionIds);
    }
}
