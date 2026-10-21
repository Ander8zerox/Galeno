package com.galeno.plataforma;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.identidad.IdentidadService;
import com.galeno.identidad.IdentidadService.ConsultorioPlataforma;
import com.galeno.identidad.SesionService;
import com.galeno.shared.correo.Correo;
import com.galeno.shared.correo.EnvioCorreo;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.security.TokenService;
import com.galeno.shared.security.TokenService.IssuedToken;
import com.galeno.shared.tenancy.TenantContext;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;
import com.galeno.suscripciones.SuscripcionService;
import com.galeno.suscripciones.SuscripcionService.Archivo;
import com.galeno.suscripciones.SuscripcionService.ConsultorioVista;
import com.galeno.suscripciones.SuscripcionService.EstadoVista;
import com.galeno.suscripciones.SuscripcionService.PagoReportado;
import com.galeno.suscripciones.SuscripcionService.PagoVista;
import com.galeno.suscripciones.SuscripcionService.PlanVista;

/**
 * Consola de la plataforma: la usan los operadores de Galeno (no los consultorios) para revisar pagos y
 * administrar suscripciones. Cada acción que cambia algo queda en {@code auditoria_plataforma}.
 *
 * <p>Los operadores son cuentas normales de Galeno marcadas por configuración ({@code app.plataforma.operadores});
 * no hay forma de volverse operador desde la web.
 */
@Service
public class PlataformaService {

    private static final Logger log = LoggerFactory.getLogger(PlataformaService.class);

    private final OperadorPlataformaRepository operadores;
    private final AuditoriaPlataformaRepository auditoria;
    private final SesionService sesiones;
    private final IdentidadService identidad;
    private final SuscripcionService suscripciones;
    private final TokenService tokens;
    private final EnvioCorreo correo;
    private final TransactionTemplate tx;
    private final List<String> correosOperadores;

    PlataformaService(OperadorPlataformaRepository operadores, AuditoriaPlataformaRepository auditoria,
                      SesionService sesiones, IdentidadService identidad, SuscripcionService suscripciones,
                      TokenService tokens, EnvioCorreo correo, TransactionTemplate tx,
                      @Value("${app.plataforma.operadores:}") List<String> correosOperadores) {
        this.operadores = operadores;
        this.auditoria = auditoria;
        this.sesiones = sesiones;
        this.identidad = identidad;
        this.suscripciones = suscripciones;
        this.tokens = tokens;
        this.correo = correo;
        this.tx = tx;
        this.correosOperadores = correosOperadores.stream().map(String::strip).filter(c -> !c.isEmpty()).toList();
    }

    public record SesionPlataforma(String accessToken, Instant expiresAt, String nombre, String email) {
    }

    public SesionPlataforma login(String email, String password) {
        SesionService.Cuenta cuenta = sesiones.verificarCredenciales(email, password)
                .filter(c -> comoSistema(() -> operadores.existsById(c.id())))
                // Mismo mensaje si no existe, si la contraseña está mal o si no es operador
                .orElseThrow(() -> ApiProblem.unauthorized("Correo o contraseña incorrectos"));
        IssuedToken token = tokens.issuePlataforma(cuenta.id(), cuenta.nombre(), cuenta.email());
        registrar(cuenta.id(), "INGRESO", null, null);
        return new SesionPlataforma(token.value(), token.expiresAt(), cuenta.nombre(), cuenta.email());
    }

    // --- pagos ---

    public List<PagoVista> pagos(String estado) {
        operador();
        return suscripciones.pagos(estado);
    }

    public Archivo comprobante(UUID pagoId) {
        UUID yo = operador();
        Archivo a = suscripciones.comprobante(pagoId);
        registrar(yo, "VER_COMPROBANTE", null, pagoId.toString());
        return a;
    }

    public PagoVista aprobar(UUID pagoId) {
        UUID yo = operador();
        PagoVista p = suscripciones.aprobar(pagoId, yo);
        registrar(yo, "APROBAR_PAGO", p.consultorioId(), p.referencia() + " · " + p.monto() + " · " + p.plan());
        return p;
    }

    public PagoVista rechazar(UUID pagoId, String motivo) {
        UUID yo = operador();
        PagoVista p = suscripciones.rechazar(pagoId, yo, motivo);
        registrar(yo, "RECHAZAR_PAGO", p.consultorioId(), p.referencia() + " · " + motivo);
        return p;
    }

    public PagoVista registrarPago(UUID consultorioId, String plan, long monto, LocalDate fechaPago, String referencia) {
        UUID yo = operador();
        PagoVista p = suscripciones.registrarPago(consultorioId, plan, monto, fechaPago, referencia, yo);
        registrar(yo, "REGISTRAR_PAGO", consultorioId, referencia + " · " + monto + " · " + plan);
        return p;
    }

    // --- consultorios ---

    public List<ConsultorioVista> consultorios() {
        operador();
        return suscripciones.consultorios();
    }

    public EstadoVista regalarDias(UUID consultorioId, int dias, String motivo) {
        UUID yo = operador();
        EstadoVista e = suscripciones.regalarDias(consultorioId, dias);
        registrar(yo, "REGALAR_DIAS", consultorioId, dias + " días · " + motivo);
        return e;
    }

    public EstadoVista suspender(UUID consultorioId, String motivo) {
        UUID yo = operador();
        EstadoVista e = suscripciones.suspender(consultorioId, motivo);
        registrar(yo, "SUSPENDER", consultorioId, motivo);
        return e;
    }

    public EstadoVista reactivar(UUID consultorioId) {
        UUID yo = operador();
        EstadoVista e = suscripciones.reactivar(consultorioId);
        registrar(yo, "REACTIVAR", consultorioId, null);
        return e;
    }

    // --- configuración ---

    public List<PlanVista> planes() {
        operador();
        return suscripciones.planes();
    }

    public PlanVista actualizarPlan(String codigo, String nombre, long precio, boolean activo) {
        UUID yo = operador();
        PlanVista p = suscripciones.actualizarPlan(codigo, nombre, precio, activo);
        registrar(yo, "CAMBIAR_PLAN", null, codigo + " · " + precio + (activo ? "" : " · inactivo"));
        return p;
    }

    public String datosPago() {
        operador();
        return suscripciones.datosPago();
    }

    public String cambiarDatosPago(String valor) {
        UUID yo = operador();
        String v = suscripciones.cambiarDatosPago(valor);
        registrar(yo, "CAMBIAR_DATOS_PAGO", null, null);
        return v;
    }

    public record EventoAuditoria(Instant fecha, String accion, String consultorio, String detalle) {
    }

    public List<EventoAuditoria> auditoria() {
        operador();
        Map<UUID, String> nombres = identidad.todosLosConsultorios().stream()
                .collect(Collectors.toMap(ConsultorioPlataforma::id, ConsultorioPlataforma::nombre));
        return comoSistema(() -> auditoria.findTop100ByOrderByCreadoEnDesc().stream()
                .map(a -> new EventoAuditoria(a.getCreadoEn(), a.getAccion(),
                        a.getConsultorioId() == null ? null : nombres.getOrDefault(a.getConsultorioId(), "?"), a.getDetalle()))
                .toList());
    }

    // --- avisos a los operadores ---

    @EventListener
    void alReportarPago(PagoReportado p) {
        for (String para : correosOperadores) {
            correo.enviar(new Correo(para, "Pago por revisar: " + p.consultorioNombre(), """
                    %s reportó un pago de %s (plan %s).

                    Revíselo en la consola de la plataforma, en Pagos por revisar.""".formatted(p.consultorioNombre(),
                    SuscripcionService.pesos(p.monto()), p.plan().toLowerCase())));
        }
    }

    /** Al arrancar, marca como operadores las cuentas de {@code app.plataforma.operadores} que existan. */
    void marcarOperadores() {
        Map<String, UUID> cuentas = identidad.cuentasPorCorreo(correosOperadores);
        comoSistema(() -> {
            cuentas.values().stream().filter(id -> !operadores.existsById(id))
                    .forEach(id -> operadores.save(new OperadorPlataforma(id)));
            return null;
        });
        correosOperadores.stream().filter(c -> !cuentas.containsKey(c.toLowerCase()))
                .forEach(c -> log.warn("app.plataforma.operadores: {} no tiene cuenta en Galeno", c));
    }

    // --- apoyo ---

    /** El operador de la sesión, verificado en cada llamada (si se le quita el acceso, deja de servir su token). */
    /** Para otros módulos (partners): avisar por correo a los operadores. */
    public void notificarOperadores(String asunto, String texto) {
        for (String para : correosOperadores) {
            correo.enviar(new Correo(para, asunto, texto));
        }
    }

    /** Para otros módulos con endpoints de consola (partners): el operador de la sesión, verificado. */
    public UUID operadorActual() {
        return operador();
    }

    public void auditar(String accion, UUID consultorioId, String detalle) {
        registrar(operador(), accion, consultorioId, detalle);
    }

    private UUID operador() {
        UUID id = AuthUser.current().id();
        if (!comoSistema(() -> operadores.existsById(id))) {
            throw ApiProblem.forbidden("Ya no tiene acceso a la consola de la plataforma");
        }
        return id;
    }

    private void registrar(UUID operador, String accion, UUID consultorioId, String detalle) {
        comoSistema(() -> auditoria.save(new AuditoriaPlataforma(operador, accion, consultorioId, detalle)));
    }

    private <T> T comoSistema(java.util.function.Supplier<T> accion) {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> accion.get()));
    }
}
