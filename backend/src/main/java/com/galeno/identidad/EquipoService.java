package com.galeno.identidad;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.galeno.shared.correo.Correo;
import com.galeno.shared.correo.EnvioCorreo;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.security.SesionVigenteFilter;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;
import com.galeno.shared.web.LimiteIntentos;

/** Gestión del equipo del consultorio actual (solo administradores; lo controla SecurityConfig). */
@Service
class EquipoService {

    private static final DateTimeFormatter VENCE = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-CO"));
    /** Correos de invitación por destinatario y consultorio: que "Reenviar" no sirva para llenarle el buzón a alguien. */
    private static final int ENVIOS_POR_HORA = 5;

    private final MembresiaRepository membresias;
    private final UsuarioRepository usuarios;
    private final PerfilMedicoRepository perfiles;
    private final AsignacionSecretarioRepository asignaciones;
    private final InvitacionRepository invitaciones;
    private final ConsultorioRepository consultorios;
    private final SesionVigenteFilter sesionVigente;
    private final EnvioCorreo correo;
    private final LimiteIntentos limites;
    private final String baseUrl;

    EquipoService(MembresiaRepository membresias, UsuarioRepository usuarios, PerfilMedicoRepository perfiles,
                  AsignacionSecretarioRepository asignaciones, InvitacionRepository invitaciones,
                  ConsultorioRepository consultorios, SesionVigenteFilter sesionVigente, EnvioCorreo correo,
                  LimiteIntentos limites, @Value("${app.web.base-url}") String baseUrl) {
        this.membresias = membresias;
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.asignaciones = asignaciones;
        this.invitaciones = invitaciones;
        this.consultorios = consultorios;
        this.sesionVigente = sesionVigente;
        this.correo = correo;
        this.limites = limites;
        this.baseUrl = baseUrl;
    }

    @Transactional(readOnly = true)
    public List<Miembro> listar() {
        List<Membresia> todas = membresias.findAll();
        List<UUID> usuarioIds = todas.stream().map(Membresia::getUsuarioId).toList();
        Map<UUID, Usuario> porUsuario = usuarios.findAllById(usuarioIds).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        Map<UUID, String> registros = perfiles.findAllById(usuarioIds).stream()
                .collect(Collectors.toMap(PerfilMedico::getUsuarioId, PerfilMedico::getRegistroMedico));
        Map<UUID, List<UUID>> asignados = asignaciones.findAll().stream().collect(Collectors.groupingBy(
                AsignacionSecretario::getSecretarioMembresiaId,
                Collectors.mapping(AsignacionSecretario::getMedicoMembresiaId, Collectors.toList())));
        return todas.stream()
                .map(m -> new Miembro(m.getId(), m.getUsuarioId(), porUsuario.get(m.getUsuarioId()).getNombre(),
                        porUsuario.get(m.getUsuarioId()).getEmail(), m.getRoles().stream().sorted().toList(),
                        m.getEstado(), registros.get(m.getUsuarioId()), asignados.getOrDefault(m.getId(), List.of())))
                .sorted(Comparator.comparing(Miembro::nombre))
                .toList();
    }

    @Transactional
    public void actualizar(UUID membresiaId, Set<Rol> roles, Membresia.Estado estado, String registroMedico) {
        Membresia m = buscar(membresiaId);
        boolean soyYo = m.getId().equals(AuthUser.current().membresiaId());
        if (soyYo && (estado != Membresia.Estado.ACTIVA || !roles.contains(Rol.ADMIN))) {
            throw ApiProblem.conflict("No puede quitarse a sí mismo el acceso de administrador");
        }
        if (roles.contains(Rol.MEDICO) && !perfiles.existsById(m.getUsuarioId())) {
            if (registroMedico == null || registroMedico.isBlank()) {
                throw ApiProblem.badRequest("Para el rol MEDICO se requiere el registro médico");
            }
            perfiles.save(new PerfilMedico(m.getUsuarioId(), registroMedico.strip(), null));
        }
        if (m.tiene(Rol.SECRETARIO) && !roles.contains(Rol.SECRETARIO)) {
            asignaciones.deleteBySecretarioMembresiaId(m.getId());
        }
        if (m.tiene(Rol.MEDICO) && !roles.contains(Rol.MEDICO)) {
            asignaciones.deleteByMedicoMembresiaId(m.getId());
        }
        m.cambiarRoles(roles);
        m.cambiarEstado(estado);
        membresias.flush();
        if (membresias.findAll().stream().noneMatch(x -> x.activa() && x.tiene(Rol.ADMIN))) {
            throw ApiProblem.conflict("El consultorio debe tener al menos un administrador activo");
        }
        sesionVigente.invalidar(m.getId());
    }

    @Transactional
    public void asignarMedicos(UUID secretarioId, List<UUID> medicoIds) {
        Membresia secretario = buscar(secretarioId);
        if (!secretario.tiene(Rol.SECRETARIO)) {
            throw ApiProblem.badRequest("Solo se pueden asignar médicos a un miembro con rol SECRETARIO");
        }
        List<UUID> distintos = medicoIds.stream().distinct().toList();
        for (UUID medicoId : distintos) {
            if (!buscar(medicoId).tiene(Rol.MEDICO)) {
                throw ApiProblem.badRequest("Solo se pueden asignar miembros con rol MEDICO");
            }
        }
        asignaciones.deleteBySecretarioMembresiaId(secretarioId);
        asignaciones.flush(); // los borrados antes que las inserciones (restricción única)
        distintos.forEach(medicoId -> asignaciones.save(new AsignacionSecretario(secretarioId, medicoId)));
    }

    @Transactional
    public InvitacionCreada invitar(String email, Set<Rol> roles, String registroMedico) {
        String correo = email.strip().toLowerCase();
        AuthUser yo = AuthUser.current();
        usuarios.findByEmail(correo)
                .flatMap(u -> membresias.findByUsuarioIdAndTenantId(u.getId(), yo.tenantId()))
                .ifPresent(m -> {
                    throw ApiProblem.conflict("Esa persona ya es miembro del consultorio; cambie sus roles en la lista del equipo");
                });
        String registro = registroMedico == null || registroMedico.isBlank() ? null : registroMedico.strip();
        return crearYEnviar(correo, EnumSet.copyOf(roles), registro);
    }

    /**
     * Vuelve a enviar una invitación pendiente. Solo se guarda el hash del token, así que el enlace anterior no se puede
     * recuperar: se crea uno nuevo con los mismos datos y el anterior deja de servir.
     */
    @Transactional
    public InvitacionCreada reenviar(UUID invitacionId) {
        Invitacion inv = invitaciones.findById(invitacionId)
                .filter(Invitacion::vigente)
                .orElseThrow(() -> ApiProblem.notFound("La invitación ya no está pendiente; cree una nueva"));
        return crearYEnviar(inv.getEmail(), inv.rolesSet(), inv.getRegistroMedico());
    }

    private InvitacionCreada crearYEnviar(String email, Set<Rol> roles, String registro) {
        AuthUser yo = AuthUser.current();
        if (!limites.permitir("invitacion:" + yo.tenantId() + ":" + email, ENVIOS_POR_HORA, Duration.ofHours(1))) {
            throw ApiProblem.tooManyRequests("Ya enviamos varias invitaciones a este correo. Intente de nuevo en una hora.");
        }
        invitaciones.findByEmailAndEstado(email, Invitacion.Estado.PENDIENTE).forEach(Invitacion::revocar);
        String token = InvitacionService.nuevoToken();
        Invitacion inv = invitaciones.save(new Invitacion(email, roles, registro, InvitacionService.hash(token), yo.id()));
        // El token va en el fragmento (#): el navegador no lo envía al servidor ni queda en logs de CloudFront
        String enlace = baseUrl + "/invitacion#" + token;
        Consultorio consultorio = consultorios.findById(yo.tenantId()).orElseThrow();
        String vence = VENCE.format(inv.getExpiraEn().atZone(ZoneId.of(consultorio.getZonaHoraria())));
        Correo mensaje = new Correo(email, "Invitación a " + consultorio.getNombre() + " en Galenum",
                textoInvitacion(yo.nombre(), consultorio.getNombre(), ordenados(inv), enlace, vence));
        // Después del commit: si la transacción falla, no sale un enlace que no sirve
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                correo.enviar(mensaje);
            }
        });
        return new InvitacionCreada(inv.getId(), email, ordenados(inv), inv.getExpiraEn(), enlace);
    }

    static String textoInvitacion(String quien, String consultorio, List<Rol> roles, String enlace, String vence) {
        String comoRoles = roles.stream().map(EquipoService::nombreRol).collect(Collectors.joining(" y "));
        return """
                Hola:

                %s le invitó a unirse al equipo de %s en Galenum como %s.

                Para aceptar, abra este enlace y cree su contraseña (si ya tiene cuenta en Galenum, ingrese con ella):

                %s

                El enlace es personal y vence el %s. Si no esperaba esta invitación, ignore este correo.

                Galenum""".formatted(quien, consultorio, comoRoles, enlace, vence);
    }

    private static String nombreRol(Rol rol) {
        return switch (rol) {
            case ADMIN -> "administrador";
            case MEDICO -> "médico";
            case SECRETARIO -> "secretario";
        };
    }

    @Transactional(readOnly = true)
    public List<InvitacionResumen> pendientes() {
        return invitaciones.findByEstadoOrderByCreadoEnDesc(Invitacion.Estado.PENDIENTE).stream()
                .filter(Invitacion::vigente)
                .map(i -> new InvitacionResumen(i.getId(), i.getEmail(), ordenados(i), i.getExpiraEn(), i.getCreadoEn()))
                .toList();
    }

    @Transactional
    public void revocar(UUID invitacionId) {
        invitaciones.findById(invitacionId)
                .orElseThrow(() -> ApiProblem.notFound("Invitación no encontrada"))
                .revocar();
    }

    private Membresia buscar(UUID membresiaId) {
        return membresias.findById(membresiaId).orElseThrow(() -> ApiProblem.notFound("Miembro no encontrado"));
    }

    private static List<Rol> ordenados(Invitacion i) {
        return i.rolesSet().stream().sorted().toList();
    }

    public record Miembro(UUID membresiaId, UUID usuarioId, String nombre, String email, List<Rol> roles,
                          Membresia.Estado estado, String registroMedico, List<UUID> medicosAsignados) {
    }

    public record InvitacionCreada(UUID id, String email, List<Rol> roles, Instant expiraEn, String enlace) {
    }

    public record InvitacionResumen(UUID id, String email, List<Rol> roles, Instant expiraEn, Instant creadoEn) {
    }
}
