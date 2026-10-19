package com.galeno.identidad;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    IdentidadService(MembresiaRepository membresias, UsuarioRepository usuarios, PerfilMedicoRepository perfiles) {
        this.membresias = membresias;
        this.usuarios = usuarios;
        this.perfiles = perfiles;
    }

    @Transactional(readOnly = true)
    public Optional<String> registroMedico(UUID usuarioId) {
        return perfiles.findById(usuarioId).map(PerfilMedico::getRegistroMedico);
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
