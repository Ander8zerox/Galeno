package com.galeno.identidad;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.shared.security.Permiso;
import com.galeno.shared.security.SesionVerifier;

/** API pública del módulo de identidad para los demás módulos. */
@Service
public class IdentidadService implements SesionVerifier {

    private final MembresiaRepository membresias;
    private final UsuarioRepository usuarios;
    private final PerfilMedicoRepository perfiles;
    private final AsignacionSecretarioRepository asignaciones;
    private final ConsultorioRepository consultorios;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    IdentidadService(MembresiaRepository membresias, UsuarioRepository usuarios, PerfilMedicoRepository perfiles,
                     AsignacionSecretarioRepository asignaciones, ConsultorioRepository consultorios,
                     org.springframework.transaction.support.TransactionTemplate tx) {
        this.tx = tx;
        this.membresias = membresias;
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.asignaciones = asignaciones;
        this.consultorios = consultorios;
    }

    @Transactional(readOnly = true)
    public Optional<String> registroMedico(UUID usuarioId) {
        return perfiles.findById(usuarioId).map(PerfilMedico::getRegistroMedico);
    }

    /** Médicos activos del consultorio de la petición, ordenados por nombre. */
    @Transactional(readOnly = true)
    public List<MedicoResumen> medicosActivos() {
        List<Membresia> medicos = membresias.findAll().stream()
                .filter(m -> m.activa() && m.tiene(Rol.MEDICO))
                .toList();
        Map<UUID, String> nombres = usuarios.findAllById(medicos.stream().map(Membresia::getUsuarioId).toList())
                .stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
        return medicos.stream()
                .map(m -> new MedicoResumen(m.getId(), m.getUsuarioId(), nombres.get(m.getUsuarioId())))
                .sorted(Comparator.comparing(MedicoResumen::nombre))
                .toList();
    }

    /** Membresías de los médicos que atiende un secretario (vacío si no tiene asignaciones). */
    @Transactional(readOnly = true)
    public Set<UUID> medicosAsignados(UUID secretarioMembresiaId) {
        return asignaciones.findBySecretarioMembresiaId(secretarioMembresiaId).stream()
                .map(AsignacionSecretario::getMedicoMembresiaId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public ZoneId zonaHoraria(UUID consultorioId) {
        return consultorios.findById(consultorioId).map(c -> ZoneId.of(c.getZonaHoraria())).orElse(ZoneId.of("America/Bogota"));
    }

    @Transactional(readOnly = true)
    public ConsultorioResumen consultorio(UUID consultorioId) {
        return consultorios.findById(consultorioId)
                .map(c -> new ConsultorioResumen(c.getId(), c.getNombre(), c.getNit(), ZoneId.of(c.getZonaHoraria())))
                .orElseThrow();
    }

    /** Datos del profesional para documentos (fórmulas, atenciones). */
    @Transactional(readOnly = true)
    public Profesional profesional(UUID usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow();
        Optional<PerfilMedico> perfil = perfiles.findById(usuarioId);
        return new Profesional(u.getNombre(), perfil.map(PerfilMedico::getRegistroMedico).orElse(null),
                perfil.map(PerfilMedico::getEspecialidad).orElse(null));
    }

    /** Correos de los administradores activos de un consultorio (para avisos de la suscripción). */
    public List<String> correosAdministradores(UUID consultorioId) {
        Set<UUID> ids = com.galeno.shared.tenancy.TenantContext.callAs(consultorioId, () -> tx.execute(s ->
                membresias.findAll().stream().filter(m -> m.activa() && m.tiene(Rol.ADMIN))
                        .map(Membresia::getUsuarioId).collect(java.util.stream.Collectors.toSet())));
        return com.galeno.shared.tenancy.TenantContext.callAs(com.galeno.shared.tenancy.TenantContext.SYSTEM, () ->
                tx.execute(s -> usuarios.findAllById(ids).stream().filter(Usuario::isActivo).map(Usuario::getEmail).sorted().toList()));
    }

    /** Todos los consultorios (consola de plataforma). */
    public List<ConsultorioPlataforma> todosLosConsultorios() {
        return com.galeno.shared.tenancy.TenantContext.callAs(com.galeno.shared.tenancy.TenantContext.SYSTEM, () ->
                tx.execute(s -> consultorios.findAll().stream()
                        .map(c -> new ConsultorioPlataforma(c.getId(), c.getNombre(), c.getNit(), c.getCreadoEn()))
                        .toList()));
    }

    /** Ids de las cuentas activas con esos correos (para marcar operadores de plataforma). */
    public java.util.Map<String, UUID> cuentasPorCorreo(java.util.Collection<String> correos) {
        return com.galeno.shared.tenancy.TenantContext.callAs(com.galeno.shared.tenancy.TenantContext.SYSTEM, () ->
                tx.execute(s -> correos.stream().map(c -> usuarios.findByEmail(c.strip().toLowerCase()))
                        .flatMap(Optional::stream).filter(Usuario::isActivo)
                        .collect(java.util.stream.Collectors.toMap(Usuario::getEmail, Usuario::getId))));
    }

    /** Crea una cuenta sin consultorio (p. ej. un partner). Falla si el correo ya tiene cuenta. */
    public UUID crearCuenta(String email, String nombre, String passwordHash) {
        return com.galeno.shared.tenancy.TenantContext.callAs(com.galeno.shared.tenancy.TenantContext.SYSTEM, () -> tx.execute(s -> {
            if (usuarios.existsByEmail(email.toLowerCase())) {
                throw com.galeno.shared.web.ApiExceptionHandler.ApiProblem.conflict("Ya existe una cuenta con este correo");
            }
            return usuarios.save(new Usuario(email, passwordHash, nombre)).getId();
        }));
    }

    /** Correo de cada cuenta (para avisos a partners y operadores). */
    public java.util.Map<UUID, String> correosDeCuentas(java.util.Collection<UUID> ids) {
        return com.galeno.shared.tenancy.TenantContext.callAs(com.galeno.shared.tenancy.TenantContext.SYSTEM, () ->
                tx.execute(s -> usuarios.findAllById(ids).stream()
                        .collect(java.util.stream.Collectors.toMap(Usuario::getId, Usuario::getEmail))));
    }

    public boolean existeCuenta(String email) {
        return com.galeno.shared.tenancy.TenantContext.callAs(com.galeno.shared.tenancy.TenantContext.SYSTEM, () ->
                tx.execute(s -> usuarios.existsByEmail(email.toLowerCase())));
    }

    public record ConsultorioPlataforma(UUID id, String nombre, String nit, java.time.Instant creadoEn) {
    }

    public record MedicoResumen(UUID membresiaId, UUID usuarioId, String nombre) {
    }

    public record ConsultorioResumen(UUID id, String nombre, String nit, ZoneId zona) {
    }

    public record Profesional(String nombre, String registroMedico, String especialidad) {
    }

    /** Las cuentas no son de ningún consultorio: se encuentran con cualquier tenant. */
    @Override
    @Transactional(readOnly = true)
    public java.time.Instant credencialesCambiadasEn(UUID usuarioId) {
        return usuarios.findById(usuarioId).map(Usuario::getCredencialesCambiadasEn).orElse(null);
    }

    /** Se consulta con el tenant de la petición: una membresía de otro consultorio no se encuentra. */
    @Override
    @Transactional(readOnly = true)
    public Set<Permiso> permisosVigentes(UUID membresiaId) {
        return membresias.findById(membresiaId)
                .filter(Membresia::activa)
                .filter(m -> usuarios.findById(m.getUsuarioId()).map(Usuario::isActivo).orElse(false))
                .map(Membresia::permisos)
                .orElse(null);
    }
}
