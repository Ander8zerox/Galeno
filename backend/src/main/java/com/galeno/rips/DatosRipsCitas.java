package com.galeno.rips;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.agenda.DatosRipsDeCitas;
import com.galeno.modulos.Modulo;
import com.galeno.modulos.ModulosService;

/**
 * Lo mínimo del RIPS para confirmar una cita: el pagador y el tipo de usuario del paciente. Sin ellos la atención no
 * se puede reportar ni facturar, y es mejor pedirlos cuando el paciente llama o llega que después del lote.
 */
@Component
class DatosRipsCitas implements DatosRipsDeCitas {

    private final RipsPacienteRepository pacientesRips;
    private final ModulosService modulos;

    DatosRipsCitas(RipsPacienteRepository pacientesRips, ModulosService modulos) {
        this.pacientesRips = pacientesRips;
        this.modulos = modulos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> faltanParaConfirmar(UUID pacienteId) {
        if (!modulos.activo(Modulo.RIPS)) {
            return List.of();
        }
        RipsPaciente p = pacientesRips.findById(pacienteId).orElseGet(() -> new RipsPaciente(pacienteId));
        List<String> faltan = new ArrayList<>();
        if (p.getPagadorId() == null) {
            faltan.add("Pagador");
        }
        if (p.getTipoUsuario() == null) {
            faltan.add("Tipo de usuario");
        }
        return faltan;
    }
}
