package com.galeno.agenda;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública del módulo de agenda para otros módulos (p. ej. la historia clínica). */
@Service
public class AgendaPublica {

    /** Motivo de la cita que se registra al atender a un paciente que llegó sin cita. */
    public static final String MOTIVO_SIN_CITA = "Atención sin cita";

    private final CitaRepository citas;
    private final AgendaMedicoRepository agendas;

    AgendaPublica(CitaRepository citas, AgendaMedicoRepository agendas) {
        this.citas = citas;
        this.agendas = agendas;
    }

    /** Citas abiertas (programadas o confirmadas) del paciente con el médico ese día, en la zona del consultorio. */
    @Transactional(readOnly = true)
    public List<CitaResumen> abiertasDelDia(UUID pacienteId, UUID medicoId, LocalDate fecha, ZoneId zona) {
        Instant fin = fecha.plusDays(1).atStartOfDay(zona).toInstant();
        return citas.proximasDelPaciente(pacienteId, fecha.atStartOfDay(zona).toInstant()).stream()
                .filter(c -> c.getMedicoMembresiaId().equals(medicoId) && c.getInicio().isBefore(fin)
                        && Cita.Estado.ABIERTOS.contains(c.getEstado()))
                .map(AgendaPublica::resumen).toList();
    }

    /**
     * Registra en la agenda del médico la atención de un paciente que llegó sin cita: un sobrecupo que empieza ahora,
     * con la duración de cita del médico. Así ese tiempo se ve ocupado y la búsqueda de espacios no lo ofrece.
     */
    @Transactional
    public UUID registrarCitaSinCita(UUID pacienteId, UUID medicoId, UUID creadaPor) {
        Instant inicio = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        int minutos = agendas.findById(medicoId).map(AgendaMedico::getDuracionMinutos).orElse(AgendaMedico.DURACION_DEFECTO);
        Cita cita = new Cita(medicoId, pacienteId, inicio, inicio.plus(Duration.ofMinutes(minutos)), MOTIVO_SIN_CITA, true,
                creadaPor);
        return citas.save(cita).getId();
    }

    /**
     * Si se elimina el borrador de una atención sin cita, su cita (la que se registró al crearla) se cancela: el
     * paciente no se atendió, y sin esto quedaría abierta y podría terminar marcada como "No asistió".
     */
    @Transactional
    public void cancelarCitaSinCita(UUID citaId) {
        citas.findById(citaId)
                .filter(c -> c.isSobrecupo() && MOTIVO_SIN_CITA.equals(c.getMotivo())
                        && Cita.Estado.ABIERTOS.contains(c.getEstado()))
                .ifPresent(c -> c.cambiarEstado(Cita.Estado.CANCELADA, "Se eliminó el borrador de la atención"));
    }

    @Transactional(readOnly = true)
    public Optional<CitaResumen> cita(UUID citaId) {
        return citas.findById(citaId).map(AgendaPublica::resumen);
    }

    /** Las del consultorio entre esas (las de otro consultorio no aparecen). */
    @Transactional(readOnly = true)
    public List<CitaResumen> citas(Collection<UUID> citaIds) {
        return citas.findAllById(citaIds).stream().map(AgendaPublica::resumen).toList();
    }

    @Transactional(readOnly = true)
    public long citasDelPaciente(UUID pacienteId) {
        return citas.countByPacienteId(pacienteId);
    }

    /** Al firmar la atención de una cita, la cita queda atendida (si aún estaba abierta). */
    @Transactional
    public void marcarAtendida(UUID citaId) {
        citas.findById(citaId)
                .filter(c -> Cita.Estado.ABIERTOS.contains(c.getEstado()))
                .ifPresent(c -> c.cambiarEstado(Cita.Estado.ATENDIDA, null));
    }

    private static CitaResumen resumen(Cita c) {
        return new CitaResumen(c.getId(), c.getPacienteId(), c.getMedicoMembresiaId(), c.getInicio(), c.getEstado());
    }

    public record CitaResumen(UUID id, UUID pacienteId, UUID medicoMembresiaId, Instant inicio, Cita.Estado estado) {

        /** Programada o confirmada: todavía no se atendió, ni se canceló. */
        public boolean abierta() {
            return Cita.Estado.ABIERTOS.contains(estado);
        }
    }
}
