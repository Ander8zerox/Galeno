package com.galeno.agenda;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.agenda.AgendaService.CitaVista;
import com.galeno.agenda.AgendaService.Horario;
import com.galeno.agenda.AgendaService.MedicoAgenda;
import com.galeno.agenda.Disponibilidad.Intervalo;

/** Las fechas ({@code LocalDate}) se interpretan en la zona horaria del consultorio; las horas de cita, en UTC/ISO-8601. */
@RestController
@RequestMapping("/api/v1/agenda")
public class AgendaController {

    private final AgendaService agenda;

    AgendaController(AgendaService agenda) {
        this.agenda = agenda;
    }

    /** Médicos cuya agenda puede ver o gestionar quien consulta. */
    @GetMapping("/medicos")
    public List<MedicoAgenda> medicos() {
        return agenda.medicos();
    }

    @GetMapping("/medicos/{medicoId}/horario")
    public Horario horario(@PathVariable UUID medicoId) {
        return agenda.horario(medicoId);
    }

    @PutMapping("/medicos/{medicoId}/horario")
    public Horario actualizarHorario(@PathVariable UUID medicoId, @Valid @RequestBody ActualizarHorario r) {
        List<Bloque> bloques = r.bloques().stream()
                .map(b -> new Bloque(b.diaSemana(), b.horaInicio(), b.horaFin())).toList();
        return agenda.actualizarHorario(medicoId, r.duracionMinutos(), bloques);
    }

    @GetMapping("/medicos/{medicoId}/disponibilidad")
    public List<Intervalo> disponibilidad(@PathVariable UUID medicoId,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return agenda.disponibilidad(medicoId, fecha);
    }

    @GetMapping("/medicos/{medicoId}/citas")
    public List<CitaVista> citas(@PathVariable UUID medicoId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return agenda.citas(medicoId, desde, hasta);
    }

    @PostMapping("/citas")
    @ResponseStatus(HttpStatus.CREATED)
    public CitaVista agendar(@Valid @RequestBody NuevaCita r) {
        return agenda.agendar(r.medicoId(), r.pacienteId(), r.inicio().toInstant(), r.duracionMinutos(), r.motivo(),
                Boolean.TRUE.equals(r.sobrecupo()));
    }

    @PutMapping("/citas/{citaId}/horario")
    public CitaVista reprogramar(@PathVariable UUID citaId, @Valid @RequestBody Reprogramar r) {
        return agenda.reprogramar(citaId, r.inicio().toInstant(), Boolean.TRUE.equals(r.sobrecupo()));
    }

    @PutMapping("/citas/{citaId}/estado")
    public CitaVista cambiarEstado(@PathVariable UUID citaId, @Valid @RequestBody CambiarEstado r) {
        return agenda.cambiarEstado(citaId, r.estado(), r.motivoCancelacion());
    }

    @GetMapping("/pacientes/{pacienteId}/proximas")
    public List<CitaVista> proximasDelPaciente(@PathVariable UUID pacienteId) {
        return agenda.proximasDelPaciente(pacienteId);
    }

    public record ActualizarHorario(@Min(5) @Max(240) int duracionMinutos,
                                    @NotNull @Size(max = 50) List<@Valid BloqueRequest> bloques) {
    }

    public record BloqueRequest(@Min(1) @Max(7) int diaSemana, @NotNull LocalTime horaInicio, @NotNull LocalTime horaFin) {
    }

    public record NuevaCita(@NotNull UUID medicoId, @NotNull UUID pacienteId, @NotNull OffsetDateTime inicio,
                            @Min(5) @Max(240) Integer duracionMinutos, @Size(max = 300) String motivo, Boolean sobrecupo) {
    }

    public record Reprogramar(@NotNull OffsetDateTime inicio, Boolean sobrecupo) {
    }

    public record CambiarEstado(@NotNull Cita.Estado estado, @Size(max = 300) String motivoCancelacion) {
    }
}
