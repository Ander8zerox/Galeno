package com.galeno.identidad;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.shared.correo.Correo;
import com.galeno.shared.correo.EnvioCorreo;
import com.galeno.shared.security.CredencialesCambiadas;
import com.galeno.shared.tenancy.TenantContext;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;
import com.galeno.shared.web.LimiteIntentos;

/**
 * "Olvidé mi contraseña" (endpoints públicos). La solicitud responde igual exista o no la cuenta, para no revelar
 * qué correos usan Galeno; si existe, llega un enlace de un solo uso que vence en una hora. Al restablecer, todas
 * las sesiones abiertas de esa cuenta dejan de servir (ver {@code SesionVigenteFilter}).
 */
@Service
class RecuperacionService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionService.class);

    private final RecuperacionContrasenaRepository recuperaciones;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final EnvioCorreo correo;
    private final LimiteIntentos limites;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate tx;
    private final String baseUrl;
    private final int limitePorIp;

    RecuperacionService(RecuperacionContrasenaRepository recuperaciones, UsuarioRepository usuarios,
                        PasswordEncoder passwordEncoder, EnvioCorreo correo, LimiteIntentos limites,
                        ApplicationEventPublisher eventos, TransactionTemplate tx,
                        @Value("${app.web.base-url}") String baseUrl,
                        @Value("${app.registro.solicitudes-por-ip-hora:5}") int limitePorIp) {
        this.recuperaciones = recuperaciones;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.correo = correo;
        this.limites = limites;
        this.eventos = eventos;
        this.tx = tx;
        this.baseUrl = baseUrl;
        this.limitePorIp = limitePorIp;
    }

    public void solicitar(String emailCrudo, String ip) {
        String email = emailCrudo.strip().toLowerCase(Locale.ROOT);
        if (!limites.permitir("recuperar-ip:" + ip, limitePorIp, Duration.ofHours(1))) {
            throw ApiProblem.tooManyRequests("Demasiadas solicitudes. Intente de nuevo en una hora.");
        }
        // Por correo: el límite se cuenta exista o no la cuenta (si no, el 429 revelaría cuáles existen)
        if (!limites.permitir("recuperar-email:" + email, 3, Duration.ofHours(1))) {
            throw ApiProblem.tooManyRequests("Ya enviamos varios enlaces a este correo. Revise su bandeja o intente en una hora.");
        }
        String token = InvitacionService.nuevoToken();
        Optional<Usuario> usuario = TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t ->
                usuarios.findByEmail(email).filter(Usuario::isActivo).map(u -> {
                    // Pedir un enlace nuevo anula los anteriores
                    recuperaciones.sinUsar(u.getId()).forEach(RecuperacionContrasena::usar);
                    recuperaciones.save(new RecuperacionContrasena(u.getId(), InvitacionService.hash(token), ip));
                    return u;
                })));
        if (usuario.isEmpty()) {
            log.info("Recuperación de contraseña para un correo sin cuenta, desde {}", ip);
            return;
        }
        correo.enviar(new Correo(email, "Restablezca su contraseña de Galeno", """
                Hola %s:

                Recibimos una solicitud para restablecer su contraseña. Para elegir una nueva, abra este enlace:

                %s

                El enlace sirve una sola vez y vence en una hora. Si usted no lo pidió, ignore este correo: su
                contraseña sigue siendo la misma.

                Galeno""".formatted(usuario.get().getNombre(), baseUrl + "/restablecer#" + token)));
    }

    public record RecuperacionPublica(String email) {
    }

    public RecuperacionPublica consultar(String token) {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> {
            RecuperacionContrasena r = vigente(recuperaciones.findByTokenHash(InvitacionService.hash(token)));
            return new RecuperacionPublica(usuarios.findById(r.getUsuarioId()).orElseThrow().getEmail());
        }));
    }

    /** Pone la contraseña nueva, anula los demás enlaces y cierra todas las sesiones de la cuenta. */
    public void restablecer(String token, String password) {
        if (password == null || password.length() < InvitacionService.MIN_PASSWORD) {
            throw ApiProblem.badRequest("La contraseña debe tener al menos " + InvitacionService.MIN_PASSWORD + " caracteres");
        }
        Usuario usuario = TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> {
            RecuperacionContrasena r = vigente(recuperaciones.paraUsar(InvitacionService.hash(token)));
            Usuario u = usuarios.findById(r.getUsuarioId()).filter(Usuario::isActivo)
                    .orElseThrow(() -> ApiProblem.notFound("La cuenta no está disponible"));
            u.cambiarPassword(passwordEncoder.encode(password));
            recuperaciones.sinUsar(u.getId()).forEach(RecuperacionContrasena::usar);
            return u;
        }));
        eventos.publishEvent(new CredencialesCambiadas(usuario.getId()));
        correo.enviar(new Correo(usuario.getEmail(), "Su contraseña de Galeno cambió", """
                Hola %s:

                La contraseña de su cuenta en Galeno acaba de cambiar y se cerraron todas las sesiones abiertas.

                Si no fue usted, restablézcala de inmediato desde "¿Olvidó su contraseña?" y avise al administrador
                de su consultorio.

                Galeno""".formatted(usuario.getNombre())));
        log.info("Contraseña restablecida para la cuenta {}", usuario.getId());
    }

    private static RecuperacionContrasena vigente(Optional<RecuperacionContrasena> r) {
        return r.filter(RecuperacionContrasena::vigente)
                .orElseThrow(() -> ApiProblem.notFound("El enlace no es válido, ya se usó o venció. Pida uno nuevo."));
    }
}
