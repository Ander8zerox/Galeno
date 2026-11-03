package com.galeno.identidad;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.shared.correo.Correo;
import com.galeno.shared.correo.EnvioCorreo;
import com.galeno.shared.security.TokenService;
import com.galeno.shared.security.TokenService.IssuedToken;
import com.galeno.shared.tenancy.TenantContext;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Verificación en dos pasos con una app de autenticación (TOTP). Obligatoria para los operadores de la plataforma y
 * para quien administra algún consultorio; opcional para los demás. Las tres entradas (consultorio, plataforma,
 * partners) pasan por {@link #compuerta} después de verificar la contraseña y por {@link #completar} con el código.
 *
 * <p>Un código equivocado responde 400 y no 401: en el frontend un 401 cierra la sesión.
 */
@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);
    private static final String EMISOR = "Galenum";
    private static final int CODIGOS_RECUPERACION = 10;
    /** Sin 0/O ni 1/I/L, que se confunden al copiarlos a mano. */
    private static final String ALFABETO_RECUPERACION = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Destino { CONSULTORIO, PLATAFORMA, PARTNER }

    public enum Requisito { VERIFICAR, CONFIGURAR }

    private final MfaUsuarioRepository mfas;
    private final CodigoRecuperacionRepository codigos;
    private final EquipoRecordadoRepository equipos;
    private final UsuarioRepository usuarios;
    private final MembresiaRepository membresias;
    private final OperadoresPlataforma operadores;
    private final CifradoMfa cifrado;
    private final TokenService tokens;
    private final EnvioCorreo correo;
    private final TransactionTemplate tx;

    MfaService(MfaUsuarioRepository mfas, CodigoRecuperacionRepository codigos, EquipoRecordadoRepository equipos,
               UsuarioRepository usuarios, MembresiaRepository membresias, OperadoresPlataforma operadores,
               CifradoMfa cifrado, TokenService tokens, EnvioCorreo correo, TransactionTemplate tx) {
        this.mfas = mfas;
        this.codigos = codigos;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.membresias = membresias;
        this.operadores = operadores;
        this.cifrado = cifrado;
        this.tokens = tokens;
        this.correo = correo;
        this.tx = tx;
    }

    // ===================== al ingresar =====================

    /** Token corto que solo sirve para enviar el código (o configurar la app) antes de entrar. */
    public record Pendiente(String accessToken, Instant expiresAt, Requisito requisito) {
    }

    /**
     * Se llama con la contraseña ya verificada.
     *
     * @param equipo        token de "recordar este equipo" que manda el navegador, o null
     * @param consultorioId consultorio al que entra después del código (registro, invitación), o null
     * @return vacío si puede entrar sin código
     */
    public Optional<Pendiente> compuerta(UUID usuarioId, Destino destino, String equipo, UUID consultorioId) {
        Requisito requisito = comoSistema(() -> {
            boolean activo = mfas.findById(usuarioId).map(MfaUsuario::isActivo).orElse(false);
            if (activo) {
                return equipoVigente(usuarioId, equipo) ? null : Requisito.VERIFICAR;
            }
            return obligatorio(usuarioId, destino) ? Requisito.CONFIGURAR : null;
        });
        if (requisito == null) {
            return Optional.empty();
        }
        Usuario u = comoSistema(() -> usuarios.findById(usuarioId).orElseThrow());
        IssuedToken t = tokens.issueMfa(usuarioId, u.getNombre(), u.getEmail(), destino.name(), requisito.name(), consultorioId);
        return Optional.of(new Pendiente(t.value(), t.expiresAt(), requisito));
    }

    /**
     * @param equipo              token para "recordar este equipo" (el navegador lo guarda), o null
     * @param codigosRecuperacion los diez códigos, solo si en este paso se configuró la app
     */
    public record Completado(UUID usuarioId, UUID consultorioId, String equipo, List<String> codigosRecuperacion) {
    }

    /** Con el token pendiente de la petición: verifica el código (o activa la app, si la estaba configurando). */
    public Completado completar(Destino destino, String codigo, boolean recordar) {
        Jwt jwt = pendiente();
        if (!destino.name().equals(jwt.getClaimAsString(TokenService.CLAIM_MFA_DESTINO))) {
            throw ApiProblem.forbidden("Este código es para otra entrada de Galenum. Ingrese de nuevo.");
        }
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        String consultorio = jwt.getClaimAsString(TokenService.CLAIM_MFA_CONSULTORIO);
        UUID consultorioId = consultorio == null ? null : UUID.fromString(consultorio);
        List<String> nuevos = null;
        if (Requisito.CONFIGURAR.name().equals(jwt.getClaimAsString(TokenService.CLAIM_MFA_REQUISITO))) {
            nuevos = activar(usuarioId, codigo);
        } else {
            verificar(usuarioId, codigo);
        }
        String equipo = recordar ? recordarEquipo(usuarioId) : null;
        return new Completado(usuarioId, consultorioId, equipo, nuevos);
    }

    // ===================== configuración =====================

    /** Lo que muestra la pantalla de configuración: el QR y, para escribirlo a mano, el secreto. */
    public record Configuracion(String uri, String secreto) {
    }

    /**
     * Genera un secreto nuevo, pendiente hasta que se confirme con un código. Con el token pendiente solo si el login
     * pidió configurarla; con una sesión normal, desde "Seguridad".
     */
    public Configuracion configurar() {
        Jwt jwt = token();
        if (jwt.getClaimAsStringList(TokenService.CLAIM_AUTHORITIES).contains(TokenService.MFA_PENDIENTE)
                && !Requisito.CONFIGURAR.name().equals(jwt.getClaimAsString(TokenService.CLAIM_MFA_REQUISITO))) {
            throw ApiProblem.forbidden("Su verificación en dos pasos ya está configurada");
        }
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        byte[] secreto = Totp.nuevoSecreto();
        String email = comoSistema(() -> {
            MfaUsuario mfa = mfas.paraVerificar(usuarioId).orElseGet(() -> mfas.save(new MfaUsuario(usuarioId)));
            if (mfa.isActivo()) {
                throw ApiProblem.conflict("Su verificación en dos pasos ya está activa");
            }
            mfa.prepararSecreto(cifrado.cifrar(secreto));
            return usuarios.findById(usuarioId).orElseThrow().getEmail();
        });
        return new Configuracion(Totp.uri(EMISOR, email, secreto), Totp.base32(secreto));
    }

    /** Confirma la app con su primer código y entrega los códigos de recuperación (solo esta vez). */
    public List<String> activar(UUID usuarioId, String codigo) {
        long paso = comprobar(usuarioId, normalizar(codigo), true);
        if (paso < 0) {
            throw ApiProblem.badRequest("El código no coincide. Revise que la hora del celular esté al día y use el código actual.");
        }
        List<String> nuevos = comoSistema(() -> {
            mfas.paraVerificar(usuarioId).orElseThrow().activar(paso);
            return reemplazarCodigos(usuarioId);
        });
        avisar(usuarioId, "Activó la verificación en dos pasos", """
                Desde ahora, al ingresar a Galenum le pediremos el código de su app de autenticación.

                Si no fue usted, cambie su contraseña de inmediato y escríbanos.""");
        log.info("Verificación en dos pasos activada para {}", usuarioId);
        return nuevos;
    }

    // ===================== desde "Seguridad" =====================

    public record Estado(boolean activo, boolean obligatoria, Instant activadaEn, int codigosRestantes,
                         long equiposRecordados) {
    }

    public Estado estado(UUID usuarioId) {
        return comoSistema(() -> {
            Optional<MfaUsuario> mfa = mfas.findById(usuarioId).filter(MfaUsuario::isActivo);
            int restantes = (int) codigos.findByUsuarioId(usuarioId).stream().filter(CodigoRecuperacion::disponible).count();
            return new Estado(mfa.isPresent(), obligatoria(usuarioId), mfa.map(MfaUsuario::getActivadoEn).orElse(null),
                    mfa.isPresent() ? restantes : 0, mfa.isPresent() ? equipos.vigentes(usuarioId, Instant.now()) : 0);
        });
    }

    public void desactivar(UUID usuarioId, String codigo) {
        if (comoSistema(() -> obligatoria(usuarioId))) {
            throw ApiProblem.conflict("Su cuenta debe tener verificación en dos pasos porque administra un consultorio "
                    + "o la plataforma");
        }
        verificar(usuarioId, codigo);
        comoSistema(() -> {
            mfas.paraVerificar(usuarioId).orElseThrow().desactivar();
            codigos.borrarDe(usuarioId);
            equipos.borrarDe(usuarioId);
            return null;
        });
        avisar(usuarioId, "Desactivó la verificación en dos pasos", """
                Su cuenta de Galenum ya no pide el código de la app al ingresar.

                Si no fue usted, cambie su contraseña de inmediato y escríbanos.""");
        log.info("Verificación en dos pasos desactivada por {}", usuarioId);
    }

    public List<String> regenerarCodigos(UUID usuarioId, String codigo) {
        verificar(usuarioId, codigo);
        return comoSistema(() -> reemplazarCodigos(usuarioId));
    }

    /** Olvidar los equipos recordados: el próximo ingreso desde cualquiera de ellos pide el código. */
    public void olvidarEquipos(UUID usuarioId) {
        comoSistema(() -> {
            equipos.borrarDe(usuarioId);
            return null;
        });
    }

    /**
     * Soporte: la persona perdió el celular y los códigos de recuperación. Borra su configuración; si la verificación
     * es obligatoria para ella, el próximo ingreso le pide configurarla de nuevo.
     */
    public void restablecer(UUID usuarioId) {
        comoSistema(() -> {
            mfas.findById(usuarioId).ifPresent(MfaUsuario::desactivar);
            codigos.borrarDe(usuarioId);
            equipos.borrarDe(usuarioId);
            return null;
        });
        avisar(usuarioId, "Restablecimos su verificación en dos pasos", """
                A su solicitud, el equipo de Galenum restableció la verificación en dos pasos de su cuenta. La próxima
                vez que ingrese podrá configurarla de nuevo con su app de autenticación.

                Si usted no lo pidió, escríbanos de inmediato.""");
        log.info("Verificación en dos pasos restablecida para {}", usuarioId);
    }

    // ===================== internos =====================

    /** Lanza 400 si el código (de la app o de recuperación) no sirve, y 429 si la cuenta está bloqueada. */
    private void verificar(UUID usuarioId, String codigo) {
        String normalizado = normalizar(codigo);
        if (normalizado.length() == 8) {
            if (!usarCodigoRecuperacion(usuarioId, normalizado)) {
                throw ApiProblem.badRequest("El código no es válido o ya se usó");
            }
            avisar(usuarioId, "Usó un código de recuperación", """
                    Alguien ingresó a su cuenta de Galenum con uno de sus códigos de recuperación. Cada código sirve
                    una sola vez: si le quedan pocos, genere unos nuevos en Seguridad.

                    Si no fue usted, cambie su contraseña de inmediato y escríbanos.""");
            return;
        }
        if (comprobar(usuarioId, normalizado, false) < 0) {
            throw ApiProblem.badRequest("El código no coincide. Use el código que muestra su app en este momento.");
        }
    }

    /**
     * Compara el código con el secreto activo (o el pendiente, al configurar) y lleva la cuenta de intentos. Va en su
     * propia transacción para que un fallo quede contado aunque luego se responda con error.
     *
     * @return el paso del código, o -1 si no coincide
     */
    private long comprobar(UUID usuarioId, String codigo, boolean pendiente) {
        return comoSistema(() -> {
            MfaUsuario mfa = mfas.paraVerificar(usuarioId)
                    .orElseThrow(() -> ApiProblem.badRequest("Configure primero su app de autenticación"));
            if (mfa.bloqueado()) {
                throw ApiProblem.tooManyRequests("Demasiados códigos incorrectos. Espere 15 minutos e intente de nuevo.");
            }
            String secreto = pendiente ? mfa.getSecretoPendiente() : (mfa.isActivo() ? mfa.getSecreto() : null);
            if (secreto == null) {
                throw ApiProblem.badRequest(pendiente ? "Vuelva a empezar la configuración" : "Configure primero su app de autenticación");
            }
            long paso = Totp.verificar(cifrado.descifrar(secreto), codigo, Instant.now(), mfa.getUltimoPaso());
            if (paso < 0) {
                mfa.fallo();
            } else {
                mfa.exito(paso);
            }
            return paso;
        });
    }

    private boolean usarCodigoRecuperacion(UUID usuarioId, String codigo) {
        return comoSistema(() -> {
            MfaUsuario mfa = mfas.paraVerificar(usuarioId).filter(MfaUsuario::isActivo)
                    .orElseThrow(() -> ApiProblem.badRequest("Configure primero su app de autenticación"));
            if (mfa.bloqueado()) {
                throw ApiProblem.tooManyRequests("Demasiados códigos incorrectos. Espere 15 minutos e intente de nuevo.");
            }
            String hash = cifrado.hmac(codigo);
            Optional<CodigoRecuperacion> encontrado = codigos.findByUsuarioId(usuarioId).stream()
                    .filter(c -> c.disponible() && c.getCodigoHash().equals(hash)).findFirst();
            encontrado.ifPresentOrElse(c -> {
                c.usar();
                mfa.exito(0);
            }, mfa::fallo);
            return encontrado.isPresent();
        });
    }

    /** Debe ejecutarse dentro de {@link #comoSistema}. */
    private List<String> reemplazarCodigos(UUID usuarioId) {
        codigos.borrarDe(usuarioId);
        List<String> nuevos = new ArrayList<>();
        for (int i = 0; i < CODIGOS_RECUPERACION; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                sb.append(ALFABETO_RECUPERACION.charAt(RANDOM.nextInt(ALFABETO_RECUPERACION.length())));
            }
            String codigo = sb.toString();
            codigos.save(new CodigoRecuperacion(usuarioId, cifrado.hmac(codigo)));
            nuevos.add(codigo.substring(0, 4) + "-" + codigo.substring(4));
        }
        return nuevos;
    }

    private String recordarEquipo(UUID usuarioId) {
        String token = InvitacionService.nuevoToken();
        comoSistema(() -> equipos.save(new EquipoRecordado(usuarioId, InvitacionService.hash(token))));
        return token;
    }

    /** Debe ejecutarse dentro de {@link #comoSistema}. */
    private boolean equipoVigente(UUID usuarioId, String equipo) {
        return equipo != null && !equipo.isBlank() && equipo.length() <= 100
                && equipos.findByTokenHash(InvitacionService.hash(equipo)).filter(e -> e.vigentePara(usuarioId)).isPresent();
    }

    /** Debe ejecutarse dentro de {@link #comoSistema}. */
    private boolean obligatorio(UUID usuarioId, Destino destino) {
        return switch (destino) {
            case PLATAFORMA -> true;
            case CONSULTORIO -> administra(usuarioId);
            case PARTNER -> false;
        };
    }

    /** Si para esta persona no es opcional, entre por donde entre. Debe ejecutarse dentro de {@link #comoSistema}. */
    private boolean obligatoria(UUID usuarioId) {
        return administra(usuarioId) || operadores.esOperador(usuarioId);
    }

    private boolean administra(UUID usuarioId) {
        return membresias.findByUsuarioId(usuarioId).stream()
                .anyMatch(m -> m.activa() && m.getRoles().contains(Rol.ADMIN));
    }

    /** Sin espacios ni guiones y en mayúsculas: "123 456" y "abcd-efgh" también valen. */
    private static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private void avisar(UUID usuarioId, String asunto, String texto) {
        Usuario u = comoSistema(() -> usuarios.findById(usuarioId).orElseThrow());
        try {
            correo.enviar(new Correo(u.getEmail(), asunto, "Hola, " + u.getNombre() + ":\n\n" + texto + "\n\nGalenum"));
        } catch (RuntimeException e) {
            // El aviso es informativo: si el correo falla, la operación ya se hizo
            log.warn("No se pudo enviar el aviso de verificación en dos pasos a {}", usuarioId, e);
        }
    }

    private static Jwt token() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)) {
            throw new IllegalStateException("No hay un usuario autenticado");
        }
        return auth.getToken();
    }

    private static Jwt pendiente() {
        Jwt jwt = token();
        if (!jwt.getClaimAsStringList(TokenService.CLAIM_AUTHORITIES).contains(TokenService.MFA_PENDIENTE)) {
            throw ApiProblem.forbidden("Este paso es solo para ingresar");
        }
        return jwt;
    }

    private <T> T comoSistema(Supplier<T> action) {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(s -> action.get()));
    }
}
