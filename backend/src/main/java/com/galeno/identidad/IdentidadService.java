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

    IdentidadService(MembresiaRepository membresias, UsuarioRepository usuarios, PerfilMedicoRepository perfiles,
                     AsignacionSecretarioRepository asignaciones, ConsultorioRepository consultorios) {
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

    public record MedicoResumen(UUID membresiaId, UUID usuarioId, String nombre) {
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
