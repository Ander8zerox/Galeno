package com.galeno.agenda;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.agenda.Disponibilidad.Intervalo;
import com.galeno.agenda.FestivosColombia.Festivo;
import com.galeno.identidad.IdentidadService;
import com.galeno.identidad.IdentidadService.MedicoResumen;
import com.galeno.pacientes.PacienteService;
import com.galeno.pacientes.PacienteService.PacienteResumen;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.security.Permiso;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Quién ve la agenda de quién:
 * <ul>
 *   <li>Administrador: la de todos los médicos del consultorio.</li>
 *   <li>Médico: la suya.</li>
 *   <li>Secretario: la de los médicos que tiene asignados; si no tiene asignaciones, la de todos.</li>
 * </ul>
 * Los roles se suman: un médico que también es administrador ve todas.
 */
@Service
class AgendaService {

    private static final Duration MAX_RANGO = Duration.ofDays(42);
    /** Margen para agendar "ahora mismo" sin que el reloj del navegador haga fallar la petición. */
    private static final Duration TOLERANCIA_PASADO = Duration.ofMinutes(5);
    private static final int DIAS_BUSQUEDA = 60;
    private static final int ESPACIOS_POR_DIA = 3;
    private static final int MAX_ESPACIOS = 8;

    private final AgendaMedicoRepository agendas;
    private final CitaRepository citas;
    private final IdentidadService identidad;
    private final PacienteService pacientes;
    private final ObjectProvider<AtencionesDeCitas> atenciones;

    AgendaService(AgendaMedicoRepository agendas, CitaRepository citas, IdentidadService identidad,
                  PacienteService pacientes, ObjectProvider<AtencionesDeCitas> atenciones) {
        this.agendas = agendas;
        this.citas = citas;
        this.identidad = identidad;
        this.pacientes = pacientes;
        this.atenciones = atenciones;
    }

    @Transactional(readOnly = true)
    public List<MedicoAgenda> medicos() {
        AuthUser yo = AuthUser.current();
        return gestionables(yo).stream()
                .map(m -> new MedicoAgenda(m.membresiaId(), m.nombre(), puedeEditarHorario(yo, m.membresiaId())))
                .toList();
    }

    @Transactional
    public Horario horario(UUID medicoId) {
        verificarAcceso(medicoId);
        return Horario.de(obtenerOCrear(medicoId));
    }

    @Transactional
    /** @param atiendeFestivos null: se queda como estaba */
    public Horario actualizarHorario(UUID medicoId, int duracionMinutos, List<Bloque> bloques, Boolean atiendeFestivos) {
        verificarAcceso(medicoId);
        if (!puedeEditarHorario(AuthUser.current(), medicoId)) {
            throw ApiProblem.forbidden("Solo el propio médico o un administrador cambian el horario");
        }
        validarHorario(duracionMinutos, bloques);
        AgendaMedico agenda = obtenerOCrear(medicoId);
        agenda.actualizar(duracionMinutos, bloques, atiendeFestivos != null ? atiendeFestivos : agenda.isAtiendeFestivos());
        return Horario.de(agenda);
    }

    /** Festivos de Colombia entre dos fechas (para marcarlos en la agenda). */
    public List<Festivo> festivos(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde) || desde.plusYears(2).isBefore(hasta)) {
            throw ApiProblem.badRequest("El rango debe ser de máximo 2 años");
        }
        return java.util.stream.IntStream.rangeClosed(desde.getYear(), hasta.getYear())
                .mapToObj(FestivosColombia::del).flatMap(List::stream)
                .filter(f -> !f.fecha().isBefore(desde) && !f.fecha().isAfter(hasta)).toList();
    }

    @Transactional
    public List<CitaVista> citas(UUID medicoId, LocalDate desde, LocalDate hasta) {
        verificarAcceso(medicoId);
        ZoneId zona = zona();
        Instant inicio = desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();
        if (!fin.isAfter(inicio) || Duration.between(inicio, fin).compareTo(MAX_RANGO) > 0) {
            throw ApiProblem.badRequest("El rango debe ser de máximo 6 semanas");
        }
        return vistas(citas.enRango(medicoId, inicio, fin));
    }

    @Transactional
    public List<Intervalo> disponibilidad(UUID medicoId, LocalDate fecha) {
        verificarAcceso(medicoId);
        AgendaMedico agenda = obtenerOCrear(medicoId);
        if (!agenda.isAtiendeFestivos() && FestivosColombia.en(fecha).isPresent()) {
            return List.of();
        }
        ZoneId zona = zona();
        Instant inicioDia = fecha.atStartOfDay(zona).toInstant();
        Instant finDia = fecha.plusDays(1).atStartOfDay(zona).toInstant();
        List<Intervalo> ocupados = citas.cruces(medicoId, inicioDia, finDia).stream()
                .map(c -> new Intervalo(c.getInicio(), c.getFin())).toList();
        return Disponibilidad.cupos(fecha, zona, agenda.getBloques(), agenda.getDuracionMinutos(), ocupados, Instant.now());
    }

    /** Cupos libres de cada día de un rango (vistas de semana y mes), con las citas leídas en una sola consulta. */
    @Transactional
    public List<DiaDisponible> disponibilidadDias(UUID medicoId, LocalDate desde, LocalDate hasta) {
        verificarAcceso(medicoId);
        if (hasta.isBefore(desde) || desde.plusDays(MAX_RANGO.toDays() - 1).isBefore(hasta)) {
            throw ApiProblem.badRequest("El rango debe ser de máximo 6 semanas");
        }
        AgendaMedico agenda = obtenerOCrear(medicoId);
        ZoneId zona = zona();
        List<Intervalo> ocupados = ocupados(medicoId, desde, hasta, zona);
        Instant ahora = Instant.now();
        return desde.datesUntil(hasta.plusDays(1)).map(fecha -> {
            String festivo = FestivosColombia.en(fecha).map(Festivo::nombre).orElse(null);
            List<Intervalo> libres = festivo != null && !agenda.isAtiendeFestivos() ? List.of()
                    : Disponibilidad.cupos(fecha, zona, agenda.getBloques(), agenda.getDuracionMinutos(), ocupados, ahora);
            return new DiaDisponible(fecha, festivo, libres);
        }).toList();
    }

    /**
     * Los primeros espacios libres de {@code minutos} seguidos desde una fecha, con un médico o con cualquiera de los
     * que ve quien busca. A lo sumo {@value #ESPACIOS_POR_DIA} por médico y día, para que la lista no sea un solo
     * médico a las 9:00, 9:20, 9:40…; y se busca hasta {@value #DIAS_BUSQUEDA} días adelante.
     */
    @Transactional
    public List<Espacio> buscarEspacios(int minutos, UUID medicoId, LocalDate desde) {
        if (minutos < 5 || minutos > 240) {
            throw ApiProblem.badRequest("La duración debe estar entre 5 y 240 minutos");
        }
        List<MedicoResumen> medicos;
        if (medicoId != null) {
            verificarAcceso(medicoId);
            medicos = gestionables(AuthUser.current()).stream().filter(m -> m.membresiaId().equals(medicoId)).toList();
        } else {
            medicos = gestionables(AuthUser.current());
        }
        ZoneId zona = zona();
        LocalDate hoy = LocalDate.now(zona);
        LocalDate inicio = desde == null || desde.isBefore(hoy) ? hoy : desde;
        LocalDate fin = inicio.plusDays(DIAS_BUSQUEDA - 1);
        Map<UUID, AgendaMedico> porMedico = new java.util.LinkedHashMap<>();
        Map<UUID, List<Intervalo>> ocupadosPorMedico = new java.util.HashMap<>();
        for (MedicoResumen m : medicos) {
            porMedico.put(m.membresiaId(), obtenerOCrear(m.membresiaId()));
            ocupadosPorMedico.put(m.membresiaId(), ocupados(m.membresiaId(), inicio, fin, zona));
        }
        Instant ahora = Instant.now();
        List<Espacio> encontrados = new java.util.ArrayList<>();
        for (LocalDate fecha = inicio; !fecha.isAfter(fin) && encontrados.size() < MAX_ESPACIOS; fecha = fecha.plusDays(1)) {
            boolean festivo = FestivosColombia.en(fecha).isPresent();
            for (MedicoResumen m : medicos) {
                AgendaMedico agenda = porMedico.get(m.membresiaId());
                if (festivo && !agenda.isAtiendeFestivos()) {
                    continue;
                }
                Disponibilidad.espacios(fecha, zona, agenda.getBloques(), agenda.getDuracionMinutos(), minutos,
                                ocupadosPorMedico.get(m.membresiaId()), ahora).stream()
                        .limit(ESPACIOS_POR_DIA)
                        .forEach(e -> encontrados.add(new Espacio(m.membresiaId(), m.nombre(), e.inicio(), e.fin())));
            }
        }
        return encontrados.stream()
                .sorted(Comparator.comparing(Espacio::inicio).thenComparing(Espacio::medicoNombre))
                .limit(MAX_ESPACIOS).toList();
    }

    /** Lo que ocupa tiempo en la agenda del médico entre dos fechas (todas las citas menos las canceladas). */
    private List<Intervalo> ocupados(UUID medicoId, LocalDate desde, LocalDate hasta, ZoneId zona) {
        return citas.cruces(medicoId, desde.atStartOfDay(zona).toInstant(), hasta.plusDays(1).atStartOfDay(zona).toInstant())
                .stream().map(c -> new Intervalo(c.getInicio(), c.getFin())).toList();
    }

    @Transactional
    public CitaVista agendar(UUID medicoId, UUID pacienteId, Instant inicio, Integer duracionMinutos, String motivo,
                             boolean sobrecupo) {
        verificarAcceso(medicoId);
        pacientes.get(pacienteId); // 404 si el paciente no es de este consultorio
        AgendaMedico agenda = obtenerOCrear(medicoId);
        Intervalo intervalo = intervalo(inicio, duracionMinutos != null ? duracionMinutos : agenda.getDuracionMinutos());
        validarCupo(agenda, intervalo, sobrecupo, null);
        Cita cita = citas.save(new Cita(medicoId, pacienteId, intervalo.inicio(), intervalo.fin(), blankToNull(motivo),
                sobrecupo, AuthUser.current().id()));
        return vistas(List.of(cita)).getFirst();
    }

    @Transactional
    public CitaVista reprogramar(UUID citaId, Instant inicio, boolean sobrecupo) {
        Cita cita = buscar(citaId);
        AgendaMedico agenda = obtenerOCrear(cita.getMedicoMembresiaId());
        int duracion = (int) Duration.between(cita.getInicio(), cita.getFin()).toMinutes();
        Intervalo intervalo = intervalo(inicio, duracion);
        validarCupo(agenda, intervalo, sobrecupo, cita.getId());
        cita.reprogramar(intervalo.inicio(), intervalo.fin(), sobrecupo);
        return vistas(List.of(cita)).getFirst();
    }

    @Transactional
    public CitaVista cambiarEstado(UUID citaId, Cita.Estado estado, String motivoCancelacion) {
        Cita cita = buscar(citaId);
        if (estado == Cita.Estado.ATENDIDA) {
            AuthUser yo = AuthUser.current();
            if (!yo.puede(Permiso.HISTORIA_VER) || !cita.getMedicoMembresiaId().equals(yo.membresiaId())) {
                throw ApiProblem.forbidden("Solo el médico de la cita la marca como atendida");
            }
        }
        if (estado == Cita.Estado.NO_ASISTIO || estado == Cita.Estado.CANCELADA) {
            AtencionesDeCitas a = atenciones.getIfAvailable();
            if (a != null && a.tieneAtencion(citaId)) {
                throw ApiProblem.conflict("Esta cita tiene una atención en curso. Fírmela o elimine el borrador antes de "
                        + (estado == Cita.Estado.CANCELADA ? "cancelar la cita." : "marcar la inasistencia."));
            }
        }
        cita.cambiarEstado(estado, motivoCancelacion);
        return vistas(List.of(cita)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<CitaVista> proximasDelPaciente(UUID pacienteId) {
        pacientes.get(pacienteId);
        Set<UUID> visibles = gestionables(AuthUser.current()).stream().map(MedicoResumen::membresiaId)
                .collect(Collectors.toSet());
        Instant desde = LocalDate.now(zona()).atStartOfDay(zona()).toInstant();
        return vistas(citas.proximasDelPaciente(pacienteId, desde).stream()
                .filter(c -> visibles.contains(c.getMedicoMembresiaId())).toList());
    }

    // --- reglas ---

    private void validarCupo(AgendaMedico agenda, Intervalo intervalo, boolean sobrecupo, UUID excluir) {
        if (intervalo.inicio().isBefore(Instant.now().minus(TOLERANCIA_PASADO))) {
            throw ApiProblem.badRequest("No se pueden agendar citas en el pasado");
        }
        agendas.bloquear(agenda.getMedicoMembresiaId()); // serializa las reservas de este médico
        if (sobrecupo) {
            return; // el sobrecupo es una decisión explícita: puede estar fuera del horario y cruzarse
        }
        if (!agenda.isAtiendeFestivos()) {
            FestivosColombia.en(intervalo.inicio().atZone(zona()).toLocalDate()).ifPresent(f -> {
                throw ApiProblem.conflict("Es festivo (" + f.nombre() + "). Use sobrecupo si el médico atiende ese día.");
            });
        }
        if (!Disponibilidad.dentroDelHorario(intervalo, zona(), agenda.getBloques())) {
            throw ApiProblem.conflict("La hora está fuera del horario del médico. Use sobrecupo si es necesario.");
        }
        boolean ocupado = citas.crucesParaReserva(agenda.getMedicoMembresiaId(), intervalo.inicio(), intervalo.fin()).stream()
                .anyMatch(c -> !c.getId().equals(excluir));
        if (ocupado) {
            throw ApiProblem.conflict("Ese horario ya está ocupado. Elija otro cupo o use sobrecupo.");
        }
    }

    private static void validarHorario(int duracionMinutos, List<Bloque> bloques) {
        if (duracionMinutos < 5 || duracionMinutos > 240) {
            throw ApiProblem.badRequest("La duración de la cita debe estar entre 5 y 240 minutos");
        }
        for (Bloque b : bloques) {
            if (b.getDiaSemana() < 1 || b.getDiaSemana() > 7 || !b.getHoraInicio().isBefore(b.getHoraFin())) {
                throw ApiProblem.badRequest("Cada franja debe tener un día válido y la hora de inicio antes de la de fin");
            }
        }
        Map<Integer, List<Bloque>> porDia = bloques.stream().collect(Collectors.groupingBy(Bloque::getDiaSemana));
        for (List<Bloque> delDia : porDia.values()) {
            List<Bloque> orden = delDia.stream().sorted(Comparator.comparing(Bloque::getHoraInicio)).toList();
            for (int i = 1; i < orden.size(); i++) {
                LocalTime finAnterior = orden.get(i - 1).getHoraFin();
                if (orden.get(i).getHoraInicio().isBefore(finAnterior)) {
                    throw ApiProblem.badRequest("Hay franjas que se cruzan en el mismo día");
                }
            }
        }
    }

    private List<MedicoResumen> gestionables(AuthUser yo) {
        List<MedicoResumen> todos = identidad.medicosActivos();
        if (yo.tieneRol("ADMIN")) {
            return todos;
        }
        Set<UUID> visibles = new java.util.HashSet<>();
        if (yo.tieneRol("MEDICO")) {
            visibles.add(yo.membresiaId());
        }
        if (yo.tieneRol("SECRETARIO")) {
            Set<UUID> asignados = identidad.medicosAsignados(yo.membresiaId());
            if (asignados.isEmpty()) {
                return todos;
            }
            visibles.addAll(asignados);
        }
        return todos.stream().filter(m -> visibles.contains(m.membresiaId())).toList();
    }

    private static boolean puedeEditarHorario(AuthUser yo, UUID medicoId) {
        return yo.tieneRol("ADMIN") || medicoId.equals(yo.membresiaId());
    }

    private void verificarAcceso(UUID medicoId) {
        boolean visible = gestionables(AuthUser.current()).stream().anyMatch(m -> m.membresiaId().equals(medicoId));
        if (!visible) {
            throw ApiProblem.notFound("Médico no encontrado");
        }
    }

    private Cita buscar(UUID citaId) {
        Cita cita = citas.findById(citaId).orElseThrow(() -> ApiProblem.notFound("Cita no encontrada"));
        verificarAcceso(cita.getMedicoMembresiaId());
        return cita;
    }

    private AgendaMedico obtenerOCrear(UUID medicoId) {
        return agendas.findById(medicoId).orElseGet(() -> agendas.saveAndFlush(AgendaMedico.porDefecto(medicoId)));
    }

    private static Intervalo intervalo(Instant inicio, int duracionMinutos) {
        if (duracionMinutos < 5 || duracionMinutos > 240) {
            throw ApiProblem.badRequest("La duración de la cita debe estar entre 5 y 240 minutos");
        }
        return new Intervalo(inicio, inicio.plus(Duration.ofMinutes(duracionMinutos)));
    }

    private ZoneId zona() {
        return identidad.zonaHoraria(AuthUser.current().tenantId());
    }

    private List<CitaVista> vistas(List<Cita> lista) {
        Map<UUID, PacienteResumen> porPaciente = pacientes.resumenes(lista.stream().map(Cita::getPacienteId).toList());
        Map<UUID, String> medicos = identidad.medicosActivos().stream()
                .collect(Collectors.toMap(MedicoResumen::membresiaId, MedicoResumen::nombre));
        return lista.stream().map(c -> CitaVista.de(c, porPaciente.get(c.getPacienteId()),
                medicos.get(c.getMedicoMembresiaId()))).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    // --- respuestas ---

    public record MedicoAgenda(UUID membresiaId, String nombre, boolean puedeEditarHorario) {
    }

    /** @param festivo nombre del festivo de Colombia, o null */
    public record DiaDisponible(LocalDate fecha, String festivo, List<Intervalo> libres) {
    }

    public record Espacio(UUID medicoId, String medicoNombre, Instant inicio, Instant fin) {
    }

    public record Horario(UUID medicoId, int duracionMinutos, List<BloqueVista> bloques, boolean atiendeFestivos) {

        static Horario de(AgendaMedico a) {
            return new Horario(a.getMedicoMembresiaId(), a.getDuracionMinutos(), a.getBloques().stream()
                    .sorted(Comparator.comparing(Bloque::getDiaSemana).thenComparing(Bloque::getHoraInicio))
                    .map(b -> new BloqueVista(b.getDiaSemana(), b.getHoraInicio(), b.getHoraFin()))
                    .toList(), a.isAtiendeFestivos());
        }
    }

    /** @param diaSemana 1 = lunes … 7 = domingo */
    public record BloqueVista(int diaSemana, LocalTime horaInicio, LocalTime horaFin) {
    }

    public record CitaVista(UUID id, UUID medicoId, String medicoNombre, UUID pacienteId, String pacienteNombre,
                            String pacienteDocumento, String pacienteTelefono, Instant inicio, Instant fin,
                            Cita.Estado estado, String motivo, boolean sobrecupo, String motivoCancelacion) {

        static CitaVista de(Cita c, PacienteResumen p, String medico) {
            return new CitaVista(c.getId(), c.getMedicoMembresiaId(), medico, c.getPacienteId(),
                    p == null ? null : p.nombre(), p == null ? null : p.tipoDocumento() + " " + p.numeroDocumento(),
                    p == null ? null : p.telefono(), c.getInicio(), c.getFin(), c.getEstado(), c.getMotivo(),
                    c.isSobrecupo(), c.getMotivoCancelacion());
        }
    }
}
