package com.galeno.identidad;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.shared.security.AuthUser;
import com.galeno.shared.security.SesionVigenteFilter;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/** Gestión del equipo del consultorio actual (solo administradores; lo controla SecurityConfig). */
@Service
class EquipoService {

    private final MembresiaRepository membresias;
    private final UsuarioRepository usuarios;
    private final PerfilMedicoRepository perfiles;
    private final AsignacionSecretarioRepository asignaciones;
    private final InvitacionRepository invitaciones;
    private final SesionVigenteFilter sesionVigente;
    private final String baseUrl;

    EquipoService(MembresiaRepository membresias, UsuarioRepository usuarios, PerfilMedicoRepository perfiles,
                  AsignacionSecretarioRepository asignaciones, InvitacionRepository invitaciones,
                  SesionVigenteFilter sesionVigente, @Value("${app.web.base-url}") String baseUrl) {
        this.membresias = membresias;
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.asignaciones = asignaciones;
        this.invitaciones = invitaciones;
        this.sesionVigente = sesionVigente;
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
        invitaciones.findByEmailAndEstado(correo, Invitacion.Estado.PENDIENTE).forEach(Invitacion::revocar);
        String token = InvitacionService.nuevoToken();
        String registro = registroMedico == null || registroMedico.isBlank() ? null : registroMedico.strip();
        Invitacion inv = invitaciones.save(new Invitacion(correo, EnumSet.copyOf(roles), registro,
                InvitacionService.hash(token), yo.id()));
        // El token va en el fragmento (#): el navegador no lo envía al servidor ni queda en logs de CloudFront
        return new InvitacionCreada(inv.getId(), correo, ordenados(inv), inv.getExpiraEn(), baseUrl + "/invitacion#" + token);
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
