package com.galeno.agenda;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública del módulo de agenda para otros módulos (p. ej. la historia clínica). */
@Service
public class AgendaPublica {

    private final CitaRepository citas;

    AgendaPublica(CitaRepository citas) {
        this.citas = citas;
    }

    @Transactional(readOnly = true)
    public Optional<CitaResumen> cita(UUID citaId) {
        return citas.findById(citaId)
                .map(c -> new CitaResumen(c.getId(), c.getPacienteId(), c.getMedicoMembresiaId(), c.getInicio(), c.getEstado()));
    }

    /** Al firmar la atención de una cita, la cita queda atendida (si aún estaba abierta). */
    @Transactional
    public void marcarAtendida(UUID citaId) {
        citas.findById(citaId)
                .filter(c -> Cita.Estado.ABIERTOS.contains(c.getEstado()))
                .ifPresent(c -> c.cambiarEstado(Cita.Estado.ATENDIDA, null));
    }

    public record CitaResumen(UUID id, UUID pacienteId, UUID medicoMembresiaId, Instant inicio, Cita.Estado estado) {
    }
}
