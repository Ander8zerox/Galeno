package com.galeno.identidad;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.identidad.SesionService.SesionResponse;
import com.galeno.shared.correo.Correo;
import com.galeno.shared.correo.EnvioCorreo;
import com.galeno.shared.tenancy.TenantContext;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;
import com.galeno.shared.web.LimiteIntentos;

/**
 * Registro de un consultorio nuevo (endpoints públicos), en dos pasos: la solicitud deja todo pendiente y envía
 * un enlace al correo; al abrirlo se crean el consultorio, la cuenta (o se usa la existente) y la membresía de
 * ADMIN (y MÉDICO si la persona es médico). Así nadie crea un consultorio con un correo que no es suyo.
 *
 * <p>La respuesta de la solicitud es la misma exista o no una cuenta con ese correo: el formulario público no
 * revela qué correos usan Galeno. Eso se sabe solo al abrir el enlace, cuando ya se probó que el correo es suyo.
 */
@Service
class RegistroService {

    /** Cambiarla cuando cambien los términos o la política de datos (y pedir una nueva aceptación). */
    static final String TERMINOS_VERSION = "2026-10";

    private static final Logger log = LoggerFactory.getLogger(RegistroService.class);
    private static final DateTimeFormatter VENCE = DateTimeFormatter.ofPattern("d 'de' MMMM 'a las' h:mm a", Locale.forLanguageTag("es-CO"))
            .withZone(ZoneId.of("America/Bogota"));

    private final RegistroPendienteRepository pendientes;
    private final UsuarioRepository usuarios;
    private final ConsultorioRepository consultorios;
    private final MembresiaRepository membresias;
    private final PerfilMedicoRepository perfiles;
    private final PasswordEncoder passwordEncoder;
    private final SesionService sesiones;
    private final EnvioCorreo correo;
    private final LimiteIntentos limites;
    private final TransactionTemplate tx;
    private final String baseUrl;
    private final int limitePorIp;
    private final ApplicationEventPublisher eventos;

    RegistroService(RegistroPendienteRepository pendientes, UsuarioRepository usuarios, ConsultorioRepository consultorios,
                    MembresiaRepository membresias, PerfilMedicoRepository perfiles, PasswordEncoder passwordEncoder,
                    SesionService sesiones, EnvioCorreo correo, LimiteIntentos limites, TransactionTemplate tx,
                    @Value("${app.web.base-url}") String baseUrl,
                    @Value("${app.registro.solicitudes-por-ip-hora:5}") int limitePorIp, ApplicationEventPublisher eventos) {
        this.eventos = eventos;
        this.limitePorIp = limitePorIp;
        this.pendientes = pendientes;
        this.usuarios = usuarios;
        this.consultorios = consultorios;
        this.membresias = membresias;
        this.perfiles = perfiles;
        this.passwordEncoder = passwordEncoder;
        this.sesiones = sesiones;
        this.correo = correo;
        this.limites = limites;
        this.tx = tx;
        this.baseUrl = baseUrl;
    }

    record Solicitud(String consultorioNombre, String nit, String nombre, String email, String password, boolean esMedico,
                     String registroMedico, String especialidad, boolean aceptaTerminos) {
    }

    /** Guarda la solicitud y envía el enlace de verificación. */
    public void solicitar(Solicitud s, String ip) {
        String email = s.email().strip().toLowerCase(Locale.ROOT);
        if (!limites.permitir("registro-ip:" + ip, limitePorIp, Duration.ofHours(1))) {
            throw ApiProblem.tooManyRequests("Demasiadas solicitudes de registro. Intente de nuevo en una hora.");
        }
        List<String> errores = new ArrayList<>();
        if (!s.aceptaTerminos()) {
            errores.add("Debe aceptar los términos y la política de tratamiento de datos");
        }
        if (s.password() == null || s.password().length() < InvitacionService.MIN_PASSWORD) {
            errores.add("La contraseña debe tener al menos " + InvitacionService.MIN_PASSWORD + " caracteres");
        }
        if (s.esMedico() && blankToNull(s.registroMedico()) == null) {
            errores.add("Ingrese su número de registro médico");
        }
        String nit = null;
        if (blankToNull(s.nit()) != null) {
            try {
                nit = Nit.normalizar(s.nit());
            } catch (IllegalArgumentException e) {
                errores.add(e.getMessage());
            }
        }
        if (!errores.isEmpty()) {
            throw ApiProblem.badRequest(String.join(". ", errores));
        }
        // Por correo cuentan solo las solicitudes válidas: el límite es para no inundar un buzón con enlaces
        if (!limites.permitir("registro-email:" + email, 3, Duration.ofHours(1))) {
            throw ApiProblem.tooManyRequests("Ya enviamos varios enlaces a este correo. Revise su bandeja o intente en una hora.");
        }

        String token = InvitacionService.nuevoToken();
        String nitFinal = nit;
        RegistroPendiente pendiente = TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> {
            pendientes.borrarVencidas(Instant.now());
            return pendientes.save(new RegistroPendiente(InvitacionService.hash(token), email, s.nombre().strip(),
                    passwordEncoder.encode(s.password()), s.consultorioNombre().strip(), nitFinal, s.esMedico(),
                    s.esMedico() ? blankToNull(s.registroMedico()) : null, s.esMedico() ? blankToNull(s.especialidad()) : null,
                    TERMINOS_VERSION, ip));
        }));
        correo.enviar(new Correo(email, "Active su consultorio en Galeno", """
                Hola %s:

                Recibimos la solicitud para registrar «%s» en Galeno. Para activarlo, abra este enlace:

                %s

                El enlace vence el %s. Si usted no hizo esta solicitud, ignore este correo: no se creará nada.

                Galeno""".formatted(pendiente.getNombre(), pendiente.getConsultorioNombre(),
                baseUrl + "/registro/confirmar#" + token, VENCE.format(pendiente.getExpiraEn()))));
        log.info("Registro de consultorio solicitado desde {}", ip);
    }

    public record RegistroPublico(String email, String consultorioNombre, boolean cuentaExistente) {
    }

    public RegistroPublico consultar(String token) {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> {
            RegistroPendiente r = vigente(pendientes.findByTokenHash(InvitacionService.hash(token)));
            return new RegistroPublico(r.getEmail(), r.getConsultorioNombre(), usuarios.existsByEmail(r.getEmail()));
        }));
    }

    /**
     * Crea el consultorio y deja a la persona con sesión iniciada en él. Si el correo ya tenía cuenta en Galeno
     * (p. ej. un médico que ya trabaja en otro consultorio), exige su contraseña actual.
     */
    public SesionResponse confirmar(String token, String passwordActual) {
        record Creado(UUID usuarioId, UUID consultorioId, boolean esMedico) {
        }
        Creado creado = TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> {
            RegistroPendiente r = vigente(pendientes.paraConfirmar(InvitacionService.hash(token)));
            Usuario usuario = usuarios.findByEmail(r.getEmail()).map(existente -> {
                if (!existente.isActivo() || passwordActual == null
                        || !passwordEncoder.matches(passwordActual, existente.getPasswordHash())) {
                    throw ApiProblem.unauthorized("Ya tiene una cuenta en Galeno con este correo: ingrese su contraseña actual");
                }
                return existente;
            }).orElseGet(() -> usuarios.save(new Usuario(r.getEmail(), r.getPasswordHash(), r.getNombre())));

            Consultorio consultorio = consultorios.save(new Consultorio(r.getConsultorioNombre(), r.getNit()));
            consultorio.aceptarTerminos(r.getTerminosVersion(), Instant.now(), usuario.getId());
            if (r.isEsMedico() && !perfiles.existsById(usuario.getId())) {
                perfiles.save(new PerfilMedico(usuario.getId(), r.getRegistroMedico(), r.getEspecialidad()));
            }
            r.confirmar(consultorio.getId());
            return new Creado(usuario.getId(), consultorio.getId(), r.isEsMedico());
        }));

        // La membresía es del consultorio nuevo: va en otra transacción para que Hibernate le asigne ese tenant
        // (el consultorio ya debe estar confirmado en la base de datos por la llave foránea)
        List<Rol> roles = creado.esMedico() ? List.of(Rol.ADMIN, Rol.MEDICO) : List.of(Rol.ADMIN);
        TenantContext.runAs(creado.consultorioId(), () -> tx.executeWithoutResult(t ->
                membresias.save(new Membresia(creado.usuarioId(), roles))));
        log.info("Consultorio {} registrado", creado.consultorioId());
        eventos.publishEvent(new ConsultorioRegistrado(creado.consultorioId()));
        return sesiones.elegir(creado.usuarioId(), creado.consultorioId());
    }

    private static RegistroPendiente vigente(Optional<RegistroPendiente> r) {
        return r.filter(RegistroPendiente::vigente)
                .orElseThrow(() -> ApiProblem.notFound("El enlace no es válido, ya se usó o venció. Puede registrarse de nuevo."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
