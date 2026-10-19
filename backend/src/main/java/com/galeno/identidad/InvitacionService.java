package com.galeno.identidad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.identidad.SesionService.SesionResponse;
import com.galeno.shared.tenancy.TenantContext;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Aceptar una invitación (endpoint público). Si el correo ya tiene cuenta en Galeno, se pide su contraseña
 * actual y se le agrega el consultorio; si no, se crea la cuenta.
 */
@Service
class InvitacionService {

    static final int MIN_PASSWORD = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvitacionRepository invitaciones;
    private final UsuarioRepository usuarios;
    private final PerfilMedicoRepository perfiles;
    private final MembresiaRepository membresias;
    private final ConsultorioRepository consultorios;
    private final PasswordEncoder passwordEncoder;
    private final SesionService sesiones;
    private final TransactionTemplate tx;

    InvitacionService(InvitacionRepository invitaciones, UsuarioRepository usuarios, PerfilMedicoRepository perfiles,
                      MembresiaRepository membresias, ConsultorioRepository consultorios,
                      PasswordEncoder passwordEncoder, SesionService sesiones, TransactionTemplate tx) {
        this.invitaciones = invitaciones;
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.membresias = membresias;
        this.consultorios = consultorios;
        this.passwordEncoder = passwordEncoder;
        this.sesiones = sesiones;
        this.tx = tx;
    }

    public InvitacionPublica consultar(String token) {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(s -> {
            Invitacion inv = vigente(token);
            Optional<Usuario> usuario = usuarios.findByEmail(inv.getEmail());
            boolean tienePerfil = usuario.map(u -> perfiles.existsById(u.getId())).orElse(false);
            boolean requiereRegistro = inv.rolesSet().contains(Rol.MEDICO) && !tienePerfil && inv.getRegistroMedico() == null;
            String consultorio = consultorios.findById(inv.getTenantId()).map(Consultorio::getNombre).orElse("");
            return new InvitacionPublica(consultorio, inv.getEmail(), inv.rolesSet().stream().sorted().toList(),
                    usuario.isPresent(), requiereRegistro);
        }));
    }

    public SesionResponse aceptar(String token, String nombre, String password, String registroMedico, String especialidad) {
        UUID consultorioId = TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(s -> vigente(token).getTenantId()));

        // Dentro del consultorio de la invitación: Hibernate asigna el tenant a la membresía nueva
        UUID usuarioId = TenantContext.callAs(consultorioId, () -> tx.execute(s -> {
            Invitacion inv = vigente(token);
            Usuario usuario = usuarios.findByEmail(inv.getEmail())
                    .map(existente -> verificarCuenta(existente, password))
                    .orElseGet(() -> crearCuenta(inv.getEmail(), nombre, password));

            Set<Rol> roles = inv.rolesSet();
            if (roles.contains(Rol.MEDICO) && !perfiles.existsById(usuario.getId())) {
                String registro = registroMedico != null && !registroMedico.isBlank() ? registroMedico.strip() : inv.getRegistroMedico();
                if (registro == null) {
                    throw ApiProblem.badRequest("Ingrese su número de registro médico");
                }
                perfiles.save(new PerfilMedico(usuario.getId(), registro, blankToNull(especialidad)));
            }

            membresias.findByUsuarioIdAndTenantId(usuario.getId(), consultorioId).ifPresentOrElse(m -> {
                m.cambiarRoles(roles);
                m.cambiarEstado(Membresia.Estado.ACTIVA);
            }, () -> membresias.save(new Membresia(usuario.getId(), roles)));
            inv.aceptar();
            return usuario.getId();
        }));

        return sesiones.elegir(usuarioId, consultorioId);
    }

    private Usuario verificarCuenta(Usuario existente, String password) {
        if (!existente.isActivo() || !passwordEncoder.matches(password, existente.getPasswordHash())) {
            throw ApiProblem.unauthorized("Ya tiene una cuenta en Galeno con este correo: ingrese su contraseña actual");
        }
        return existente;
    }

    private Usuario crearCuenta(String email, String nombre, String password) {
        if (nombre == null || nombre.isBlank()) {
            throw ApiProblem.badRequest("Ingrese su nombre");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            throw ApiProblem.badRequest("La contraseña debe tener al menos " + MIN_PASSWORD + " caracteres");
        }
        return usuarios.save(new Usuario(email, passwordEncoder.encode(password), nombre.strip()));
    }

    private Invitacion vigente(String token) {
        return invitaciones.findByTokenHash(hash(token))
                .filter(Invitacion::vigente)
                .orElseThrow(() -> ApiProblem.notFound("La invitación no es válida o ya venció"));
    }

    static String nuevoToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public record InvitacionPublica(String consultorioNombre, String email, List<Rol> roles, boolean cuentaExistente,
                                    boolean requiereRegistroMedico) {
    }
}
