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
import com.galeno.agenda.AgendaService.DiaDisponible;
import com.galeno.agenda.AgendaService.EnEsperaVista;
import com.galeno.agenda.AgendaService.Espacio;
import com.galeno.agenda.AgendaService.Sugerencias;
import com.galeno.agenda.AgendaService.Horario;
import com.galeno.agenda.AgendaService.MedicoAgenda;
import com.galeno.agenda.Disponibilidad.Intervalo;
import com.galeno.agenda.FestivosColombia.Festivo;

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
        return agenda.actualizarHorario(medicoId, r.duracionMinutos(), bloques, r.atiendeFestivos());
    }

    @GetMapping("/festivos")
    public List<Festivo> festivos(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return agenda.festivos(desde, hasta);
    }

    @GetMapping("/medicos/{medicoId}/disponibilidad")
    public List<Intervalo> disponibilidad(@PathVariable UUID medicoId,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return agenda.disponibilidad(medicoId, fecha);
    }

    /** Cupos libres de cada día del rango (máximo 6 semanas), para las vistas de semana y mes. */
    @GetMapping("/medicos/{medicoId}/disponibilidad/dias")
    public List<DiaDisponible> disponibilidadDias(@PathVariable UUID medicoId,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return agenda.disponibilidadDias(medicoId, desde, hasta);
    }

    /**
     * Los primeros espacios libres de {@code minutos} seguidos (hasta 8, en los próximos 60 días). Sin {@code medicoId},
     * con cualquiera de los médicos que ve quien busca.
     */
    @GetMapping("/espacios")
    public List<Espacio> buscarEspacios(@RequestParam int minutos, @RequestParam(required = false) UUID medicoId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        return agenda.buscarEspacios(minutos, medicoId, desde);
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

    /** Bloqueos (vacaciones, cierres) de los médicos que ve quien consulta y del consultorio, que tocan el rango. */
    @GetMapping("/bloqueos")
    public List<AgendaService.BloqueoVista> bloqueos(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return agenda.bloqueos(desde, hasta);
    }

    /** Devuelve también las citas abiertas que quedaron dentro del bloqueo (no se tocan: hay que moverlas o cancelarlas). */
    @PostMapping("/bloqueos")
    @ResponseStatus(HttpStatus.CREATED)
    public AgendaService.BloqueoCreado bloquear(@Valid @RequestBody NuevoBloqueo r) {
        return agenda.bloquear(r.medicoId(), r.inicio().toInstant(), r.fin().toInstant(), r.motivo());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/bloqueos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarBloqueo(@PathVariable UUID id) {
        agenda.quitarBloqueo(id);
    }

    /** @param medicoId null: todo el consultorio (solo el administrador) */
    public record NuevoBloqueo(UUID medicoId, @NotNull OffsetDateTime inicio, @NotNull OffsetDateTime fin,
                               @jakarta.validation.constraints.NotBlank @Size(max = 200) String motivo) {
    }

    /** Pacientes en la lista de espera (activos), el que más lleva esperando primero. */
    @GetMapping("/lista-espera")
    public List<EnEsperaVista> listaEspera() {
        return agenda.listaEspera();
    }

    @PostMapping("/lista-espera")
    @ResponseStatus(HttpStatus.CREATED)
    public EnEsperaVista anotarEnEspera(@Valid @RequestBody NuevaEspera r) {
        return agenda.anotarEnEspera(r.pacienteId(), r.medicoId(), r.duracionMinutos(), r.franja(), r.antesDe(),
                r.citaId(), r.motivo(), r.nota());
    }

    @PostMapping("/lista-espera/{id}/retirar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirarDeEspera(@PathVariable UUID id) {
        agenda.retirarDeEspera(id);
    }

    /** Agenda (o adelanta) la cita del paciente en espera en el espacio indicado. */
    @PostMapping("/lista-espera/{id}/asignar")
    public CitaVista asignarDesdeEspera(@PathVariable UUID id, @Valid @RequestBody AsignarEspera r) {
        return agenda.asignarDesdeEspera(id, r.medicoId(), r.inicio().toInstant());
    }

    /** A quién ofrecer el espacio libre que empieza en {@code inicio}: lista de espera y citas de más tarde ese día. */
    @GetMapping("/medicos/{medicoId}/sugerencias")
    public Sugerencias sugerencias(@PathVariable UUID medicoId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio) {
        return agenda.sugerencias(medicoId, inicio.toInstant());
    }

    /**
     * @param medicoId null: cualquier médico. Se ignora si hay {@code citaId}.
     * @param duracionMinutos obligatoria sin {@code citaId}; con ella, es la de la cita
     * @param citaId la cita que el paciente quiere adelantar
     */
    public record NuevaEspera(@NotNull UUID pacienteId, UUID medicoId, @Min(5) @Max(240) Integer duracionMinutos,
                              EnEspera.Franja franja, LocalDate antesDe, UUID citaId, @Size(max = 300) String motivo,
                              @Size(max = 300) String nota) {
    }

    public record AsignarEspera(@NotNull UUID medicoId, @NotNull OffsetDateTime inicio) {
    }

    /** @param atiendeFestivos si se omite, se queda como estaba */
    public record ActualizarHorario(@Min(5) @Max(240) int duracionMinutos,
                                    @NotNull @Size(max = 50) List<@Valid BloqueRequest> bloques, Boolean atiendeFestivos) {
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
