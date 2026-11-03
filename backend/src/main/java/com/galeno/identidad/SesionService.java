package com.galeno.identidad;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.shared.security.AuthUser;
import com.galeno.shared.security.Permiso;
import com.galeno.shared.security.TokenService;
import com.galeno.shared.security.TokenService.IssuedToken;
import com.galeno.shared.tenancy.TenantContext;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Inicio de sesión en dos pasos: credenciales → (si tiene varios consultorios) elegir uno → token de sesión.
 * Las búsquedas entre consultorios se hacen con el tenant del sistema y en transacciones propias.
 */
@Service
public class SesionService {

    private final UsuarioRepository usuarios;
    private final MembresiaRepository membresias;
    private final ConsultorioRepository consultorios;
    private final PerfilMedicoRepository perfiles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final MfaService mfa;
    private final TransactionTemplate tx;
    /** Hash de relleno: el login tarda lo mismo exista o no el correo (evita enumerar cuentas). */
    private final String hashRelleno;

    SesionService(UsuarioRepository usuarios, MembresiaRepository membresias, ConsultorioRepository consultorios,
                  PerfilMedicoRepository perfiles, PasswordEncoder passwordEncoder, TokenService tokens, MfaService mfa,
                  TransactionTemplate tx) {
        this.usuarios = usuarios;
        this.membresias = membresias;
        this.consultorios = consultorios;
        this.perfiles = perfiles;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.mfa = mfa;
        this.tx = tx;
        this.hashRelleno = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** @param equipo token de "recordar este equipo" que guardó el navegador, o null */
    public SesionResponse login(String email, String password, String equipo) {
        Usuario u = comoSistema(() -> {
            Optional<Usuario> usuario = usuarios.findByEmail(email.toLowerCase());
            boolean valido = passwordEncoder.matches(password, usuario.map(Usuario::getPasswordHash).orElse(hashRelleno));
            if (!valido || usuario.isEmpty() || !usuario.get().isActivo()) {
                throw ApiProblem.unauthorized("Correo o contraseña incorrectos");
            }
            // Antes del código: no tiene sentido pedirlo para luego decir que no hay consultorios
            if (activas(usuario.get().getId()).isEmpty()) {
                throw ApiProblem.forbidden("Su cuenta no tiene consultorios activos");
            }
            return usuario.get();
        });
        return ingresar(u.getId(), null, equipo);
    }

    /**
     * Entrar después de verificar a la persona por otra vía (registro, invitación): pasa por la verificación en dos
     * pasos igual que el login.
     */
    public SesionResponse ingresar(UUID usuarioId, UUID consultorioId) {
        return ingresar(usuarioId, consultorioId, null);
    }

    private SesionResponse ingresar(UUID usuarioId, UUID consultorioId, String equipo) {
        return mfa.compuerta(usuarioId, MfaService.Destino.CONSULTORIO, equipo, consultorioId)
                .map(p -> new SesionResponse(p.accessToken(), p.expiresAt(), false, List.of(), p.requisito(), null, null))
                .orElseGet(() -> elegir(usuarioId, consultorioId));
    }

    /** Con el token pendiente: el código de la app (o la configuración, si apenas la está activando). */
    public SesionResponse completarMfa(String codigo, boolean recordar) {
        MfaService.Completado c = mfa.completar(MfaService.Destino.CONSULTORIO, codigo, recordar);
        SesionResponse r = elegir(c.usuarioId(), c.consultorioId());
        return new SesionResponse(r.accessToken(), r.expiresAt(), r.completa(), r.consultorios(), null, c.equipo(),
                c.codigosRecuperacion());
    }

    public record Cuenta(UUID id, String nombre, String email) {
    }

    /** Verifica correo y contraseña sin abrir sesión en un consultorio (la usa la consola de plataforma). */
    public Optional<Cuenta> verificarCredenciales(String email, String password) {
        return comoSistema(() -> {
            Optional<Usuario> usuario = usuarios.findByEmail(email.toLowerCase());
            boolean valido = passwordEncoder.matches(password, usuario.map(Usuario::getPasswordHash).orElse(hashRelleno));
            return valido && usuario.isPresent() && usuario.get().isActivo()
                    ? Optional.of(new Cuenta(usuario.get().getId(), usuario.get().getNombre(), usuario.get().getEmail()))
                    : Optional.<Cuenta>empty();
        });
    }

    /**
     * Elegir (o cambiar de) consultorio. Sirve con el token de selección o con uno de sesión: quien tiene uno de esos
     * ya pasó la verificación en dos pasos. No llamarlo desde otra entrada sin pasar por {@link #ingresar}.
     */
    public SesionResponse elegir(UUID usuarioId, UUID consultorioId) {
        return comoSistema(() -> {
            Usuario usuario = usuarios.findById(usuarioId).filter(Usuario::isActivo)
                    .orElseThrow(() -> ApiProblem.unauthorized("Cuenta no disponible"));
            return iniciar(usuario, consultorioId);
        });
    }

    public List<ConsultorioOpcion> consultorios(UUID usuarioId) {
        return comoSistema(() -> opciones(activas(usuarioId)));
    }

    public MeResponse me(AuthUser auth) {
        MeResponse me = TenantContext.callAs(auth.tenantId(), () -> tx.execute(s -> {
            Membresia m = membresias.findById(auth.membresiaId())
                    .orElseThrow(() -> ApiProblem.unauthorized("Sesión no válida"));
            Usuario u = usuarios.findById(auth.id()).orElseThrow();
            Optional<PerfilMedico> perfil = perfiles.findById(u.getId());
            Consultorio consultorio = consultorios.findById(m.getTenantId()).orElseThrow();
            return new MeResponse(u.getId(), u.getNombre(), u.getEmail(),
                    perfil.map(PerfilMedico::getRegistroMedico).orElse(null),
                    perfil.map(PerfilMedico::getEspecialidad).orElse(null),
                    m.getTenantId(), consultorio.getNombre(), consultorio.getZonaHoraria(), m.getId(), ordenados(m),
                    m.permisos().stream().sorted().toList(), 0);
        }));
        int total = consultorios(auth.id()).size();
        return me.conTotalConsultorios(total);
    }

    /** Debe ejecutarse dentro de {@link #comoSistema}. */
    private SesionResponse iniciar(Usuario usuario, UUID consultorioId) {
        List<Membresia> activas = activas(usuario.getId());
        if (activas.isEmpty()) {
            throw ApiProblem.forbidden("Su cuenta no tiene consultorios activos");
        }
        List<ConsultorioOpcion> opciones = opciones(activas);
        Membresia elegida;
        if (consultorioId != null) {
            elegida = activas.stream().filter(m -> m.getTenantId().equals(consultorioId)).findFirst()
                    .orElseThrow(() -> ApiProblem.forbidden("No tiene acceso a ese consultorio"));
        } else if (activas.size() == 1) {
            elegida = activas.getFirst();
        } else {
            IssuedToken seleccion = tokens.issueSeleccion(usuario.getId(), usuario.getNombre(), usuario.getEmail());
            return new SesionResponse(seleccion.value(), seleccion.expiresAt(), false, opciones, null, null, null);
        }
        IssuedToken token = tokens.issueSesion(usuario.getId(), usuario.getNombre(), usuario.getEmail(),
                elegida.getTenantId(), elegida.getId(), ordenados(elegida).stream().map(Enum::name).toList(),
                elegida.permisos());
        return new SesionResponse(token.value(), token.expiresAt(), true, opciones, null, null, null);
    }

    private List<Membresia> activas(UUID usuarioId) {
        return membresias.findByUsuarioId(usuarioId).stream().filter(Membresia::activa).toList();
    }

    private List<ConsultorioOpcion> opciones(List<Membresia> activas) {
        Map<UUID, Consultorio> porId = consultorios.findAllById(activas.stream().map(Membresia::getTenantId).toList())
                .stream().collect(Collectors.toMap(Consultorio::getId, Function.identity()));
        return activas.stream()
                .map(m -> new ConsultorioOpcion(m.getTenantId(), porId.get(m.getTenantId()).getNombre(), ordenados(m)))
                .sorted(Comparator.comparing(ConsultorioOpcion::nombre))
                .toList();
    }

    private static List<Rol> ordenados(Membresia m) {
        return m.getRoles().stream().sorted().toList();
    }

    private <T> T comoSistema(java.util.function.Supplier<T> action) {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(s -> action.get()));
    }

    /**
     * @param completa            {@code false} si la persona debe elegir consultorio o enviar el código de la
     *                            verificación en dos pasos: el token solo sirve para eso
     * @param mfa                 si no es null, falta el código ({@code VERIFICAR}) o configurar la app
     *                            ({@code CONFIGURAR}): el token es el pendiente
     * @param equipo              token de "recordar este equipo", para mandarlo en los próximos logins
     * @param codigosRecuperacion solo al terminar de configurar la app: se muestran una única vez
     */
    public record SesionResponse(String accessToken, Instant expiresAt, boolean completa,
                                 List<ConsultorioOpcion> consultorios, MfaService.Requisito mfa, String equipo,
                                 List<String> codigosRecuperacion) {
    }

    public record ConsultorioOpcion(UUID consultorioId, String nombre, List<Rol> roles) {
    }

    public record MeResponse(UUID id, String nombre, String email, String registroMedico, String especialidad,
                             UUID consultorioId, String consultorioNombre, String zonaHoraria, UUID membresiaId,
                             List<Rol> roles, List<Permiso> permisos, int totalConsultorios) {

        MeResponse conTotalConsultorios(int total) {
            return new MeResponse(id, nombre, email, registroMedico, especialidad, consultorioId, consultorioNombre,
                    zonaHoraria, membresiaId, roles, permisos, total);
        }
    }
}
