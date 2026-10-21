package com.galeno.suscripciones;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.identidad.ConsultorioRegistrado;
import com.galeno.identidad.IdentidadService;
import com.galeno.identidad.IdentidadService.ConsultorioPlataforma;
import com.galeno.shared.correo.Correo;
import com.galeno.shared.correo.EnvioCorreo;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.security.EstadoSuscripcionVerifier;
import com.galeno.shared.security.SuscripcionCambiada;
import com.galeno.shared.tenancy.TenantContext;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;
import com.galeno.suscripciones.Suscripcion.Estado;

/**
 * Suscripción de cada consultorio y sus pagos. Los consultorios reportan pagos por transferencia o consignación;
 * un operador de la plataforma los aprueba (y así se extiende la suscripción) o los rechaza.
 *
 * <p>Los métodos "de plataforma" corren como {@link TenantContext#SYSTEM}: ven todos los consultorios. Solo los
 * llama el módulo {@code plataforma}, que exige el token de operador.
 */
@Service
public class SuscripcionService implements EstadoSuscripcionVerifier {

    static final int MAX_COMPROBANTE = 5 * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(SuscripcionService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));

    private final SuscripcionRepository suscripciones;
    private final PlanSuscripcionRepository planes;
    private final PagoSuscripcionRepository pagos;
    private final ComprobantePagoRepository comprobantes;
    private final ParametroPlataformaRepository parametros;
    private final IdentidadService identidad;
    private final EnvioCorreo correo;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate tx;

    SuscripcionService(SuscripcionRepository suscripciones, PlanSuscripcionRepository planes,
                       PagoSuscripcionRepository pagos, ComprobantePagoRepository comprobantes,
                       ParametroPlataformaRepository parametros,
                       IdentidadService identidad, EnvioCorreo correo, ApplicationEventPublisher eventos,
                       TransactionTemplate tx) {
        this.suscripciones = suscripciones;
        this.planes = planes;
        this.pagos = pagos;
        this.comprobantes = comprobantes;
        this.parametros = parametros;
        this.identidad = identidad;
        this.correo = correo;
        this.eventos = eventos;
        this.tx = tx;
    }

    // --- vistas ---

    /**
     * @param graciaHasta  hasta cuándo funciona todo aunque esté vencida (después, solo lectura)
     * @param diasRestantes días hasta {@code venceEn} (negativo si ya venció)
     */
    public record EstadoVista(Estado estado, Instant venceEn, Instant graciaHasta, long diasRestantes, boolean pagada,
                              boolean soloLectura, String motivoSuspension, boolean pagoEnRevision) {
    }

    public record PlanVista(String codigo, String nombre, int meses, long precio, boolean activo) {
    }

    public record OpcionesPago(List<PlanVista> planes, String datosPago) {
    }

    public record PagoVista(UUID id, UUID consultorioId, String consultorioNombre, String plan, int meses, long monto,
                            LocalDate fechaPago, String referencia, PagoSuscripcion.Estado estado, String motivoRechazo,
                            boolean tieneComprobante, Instant reportadoEn, Instant revisadoEn, Instant periodoDesde,
                            Instant periodoHasta) {
    }

    public record ConsultorioVista(UUID id, String nombre, String nit, Instant creadoEn, EstadoVista suscripcion,
                                   List<String> administradores) {
    }

    public record Archivo(String nombre, String tipo, byte[] contenido) {
    }

    // --- consultorio ---

    /** Estado de la suscripción del consultorio de la sesión (lo ve cualquier miembro, para el aviso). */
    public EstadoVista estado() {
        UUID tenant = AuthUser.current().tenantId();
        return TenantContext.callAs(tenant, () -> tx.execute(t -> vista(obtener(tenant), Instant.now(),
                pagos.existsByEstado(PagoSuscripcion.Estado.REPORTADO))));
    }

    public OpcionesPago opciones() {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> new OpcionesPago(
                planes.findByActivoTrueOrderByMeses().stream().map(SuscripcionService::vista).toList(), datosPago())));
    }

    public List<PagoVista> misPagos() {
        UUID tenant = AuthUser.current().tenantId();
        return TenantContext.callAs(tenant, () -> tx.execute(t -> {
            String nombre = identidad.consultorio(tenant).nombre();
            return pagos.findAllByOrderByReportadoEnDesc().stream().map(p -> vista(p, nombre)).toList();
        }));
    }

    public record ReportePago(String plan, long monto, LocalDate fechaPago, String referencia, String comprobanteNombre,
                              byte[] comprobante) {
    }

    /** El consultorio avisa que pagó. Queda en revisión hasta que un operador lo apruebe. */
    public PagoVista reportarPago(ReportePago r) {
        AuthUser yo = AuthUser.current();
        if (r.monto() <= 0) {
            throw ApiProblem.badRequest("Ingrese el valor pagado");
        }
        LocalDate hoy = LocalDate.now(identidad.zonaHoraria(yo.tenantId()));
        if (r.fechaPago().isAfter(hoy) || r.fechaPago().isBefore(hoy.minusDays(90))) {
            throw ApiProblem.badRequest("La fecha del pago debe estar entre los últimos 90 días y hoy");
        }
        String tipo = null;
        if (r.comprobante() != null && r.comprobante().length > 0) {
            if (r.comprobante().length > MAX_COMPROBANTE) {
                throw ApiProblem.badRequest("El comprobante debe pesar máximo 5 MB");
            }
            tipo = tipoComprobante(r.comprobante());
        }
        String tipoFinal = tipo;
        PagoVista pago = TenantContext.callAs(yo.tenantId(), () -> tx.execute(t -> {
            if (pagos.existsByEstado(PagoSuscripcion.Estado.REPORTADO)) {
                throw ApiProblem.conflict("Ya hay un pago en revisión. Le avisaremos por correo cuando lo revisemos.");
            }
            PlanSuscripcion plan = planes.findById(r.plan()).filter(PlanSuscripcion::isActivo)
                    .orElseThrow(() -> ApiProblem.badRequest("Plan no disponible: " + r.plan()));
            PagoSuscripcion p = pagos.save(new PagoSuscripcion(plan, r.monto(), r.fechaPago(), r.referencia().strip(),
                    tipoFinal == null ? null : nombreSeguro(r.comprobanteNombre()), tipoFinal, yo.id()));
            if (tipoFinal != null) {
                comprobantes.save(new ComprobantePago(p.getId(), r.comprobante()));
            }
            return vista(p, identidad.consultorio(yo.tenantId()).nombre());
        }));
        eventos.publishEvent(new PagoReportado(pago.id(), pago.consultorioNombre(), pago.plan(), pago.monto()));
        return pago;
    }

    /** Lo escucha el módulo de plataforma para avisar a los operadores. */
    public record PagoReportado(UUID pagoId, String consultorioNombre, String plan, long monto) {
    }

    // --- plataforma ---

    /** @param estadoTexto REPORTADO, APROBADO o RECHAZADO; {@code null} para todos */
    public List<PagoVista> pagos(String estadoTexto) {
        PagoSuscripcion.Estado estado;
        try {
            estado = estadoTexto == null ? null : PagoSuscripcion.Estado.valueOf(estadoTexto);
        } catch (IllegalArgumentException e) {
            throw ApiProblem.badRequest("Estado de pago no válido: " + estadoTexto);
        }
        return comoSistema(() -> {
            List<PagoSuscripcion> lista = estado == null ? pagos.findAllByOrderByReportadoEnDesc()
                    : pagos.findByEstadoOrderByReportadoEn(estado);
            Map<UUID, String> nombres = nombresConsultorios();
            return lista.stream().map(p -> vista(p, nombres.getOrDefault(p.getTenantId(), "?"))).toList();
        });
    }

    public Archivo comprobante(UUID pagoId) {
        return comoSistema(() -> {
            PagoSuscripcion p = pagos.findById(pagoId).orElseThrow(() -> ApiProblem.notFound("Pago no encontrado"));
            byte[] contenido = comprobantes.findById(p.getId()).map(ComprobantePago::getContenido)
                    .orElseThrow(() -> ApiProblem.notFound("Este pago no tiene comprobante"));
            return new Archivo(p.getComprobanteNombre(), p.getComprobanteTipo(), contenido);
        });
    }

    /** Aprueba el pago y extiende la suscripción por los meses del plan. */
    public PagoVista aprobar(UUID pagoId, UUID operadorId) {
        UUID consultorio = comoSistema(() -> pagos.findById(pagoId).map(PagoSuscripcion::getTenantId)
                .orElseThrow(() -> ApiProblem.notFound("Pago no encontrado")));
        asegurar(consultorio);
        PagoVista pago = comoSistema(() -> {
            PagoSuscripcion p = pagos.paraRevisar(pagoId).orElseThrow(() -> ApiProblem.notFound("Pago no encontrado"));
            Instant[] periodo = extender(p.getTenantId(), p.getMeses());
            p.aprobar(operadorId, periodo[0], periodo[1]);
            return vista(p, identidad.consultorio(p.getTenantId()).nombre());
        });
        eventos.publishEvent(new SuscripcionCambiada(consultorio));
        avisar(pago.consultorioId(), "Pago aprobado: su suscripción a Galeno está al día", """
                Hola:

                Aprobamos el pago %s de %s (plan %s). Su suscripción queda vigente hasta el %s.

                Gracias por usar Galeno.""".formatted(pago.referencia(), pesos(pago.monto()), pago.plan().toLowerCase(),
                fecha(pago.periodoHasta(), pago.consultorioId())));
        return pago;
    }

    public PagoVista rechazar(UUID pagoId, UUID operadorId, String motivo) {
        PagoVista pago = comoSistema(() -> {
            PagoSuscripcion p = pagos.paraRevisar(pagoId).orElseThrow(() -> ApiProblem.notFound("Pago no encontrado"));
            p.rechazar(operadorId, motivo.strip());
            return vista(p, identidad.consultorio(p.getTenantId()).nombre());
        });
        avisar(pago.consultorioId(), "No pudimos aprobar su pago en Galeno", """
                Hola:

                Revisamos el pago %s de %s y no lo pudimos aprobar: %s

                Puede reportarlo de nuevo desde Galeno, en Suscripción.""".formatted(pago.referencia(), pesos(pago.monto()), motivo.strip()));
        return pago;
    }

    /** Pago que el operador recibió sin que el consultorio lo reportara (p. ej. una consignación identificada). */
    public PagoVista registrarPago(UUID consultorioId, String plan, long monto, LocalDate fechaPago, String referencia,
                                   UUID operadorId) {
        if (monto <= 0) {
            throw ApiProblem.badRequest("Ingrese el valor pagado");
        }
        identidad.consultorio(consultorioId);
        UUID pagoId = TenantContext.callAs(consultorioId, () -> tx.execute(t -> {
            PlanSuscripcion p = planes.findById(plan).orElseThrow(() -> ApiProblem.badRequest("Plan no disponible: " + plan));
            return pagos.save(new PagoSuscripcion(p, monto, fechaPago, referencia.strip(), null, null, operadorId)).getId();
        }));
        return aprobar(pagoId, operadorId);
    }

    public List<ConsultorioVista> consultorios() {
        List<ConsultorioPlataforma> todos = identidad.todosLosConsultorios();
        Instant ahora = Instant.now();
        Map<UUID, EstadoVista> estados = comoSistema(() -> {
            Map<UUID, Suscripcion> porTenant = suscripciones.findAll().stream()
                    .collect(Collectors.toMap(Suscripcion::getTenantId, Function.identity()));
            java.util.Set<UUID> conPagoEnRevision = pagos.findByEstadoOrderByReportadoEn(PagoSuscripcion.Estado.REPORTADO)
                    .stream().map(PagoSuscripcion::getTenantId).collect(Collectors.toSet());
            return todos.stream().filter(c -> porTenant.containsKey(c.id())).collect(Collectors.toMap(ConsultorioPlataforma::id,
                    c -> vista(porTenant.get(c.id()), ahora, conPagoEnRevision.contains(c.id()))));
        });
        return todos.stream()
                .map(c -> new ConsultorioVista(c.id(), c.nombre(), c.nit(), c.creadoEn(),
                        estados.computeIfAbsent(c.id(), id -> vista(asegurar(id), ahora, false)),
                        identidad.correosAdministradores(c.id())))
                .sorted(Comparator.comparing(ConsultorioVista::creadoEn).reversed())
                .toList();
    }

    public EstadoVista regalarDias(UUID consultorioId, int dias) {
        if (dias < 1 || dias > 366) {
            throw ApiProblem.badRequest("Entre 1 y 366 días");
        }
        return actualizar(consultorioId, s -> s.regalarDias(dias, Instant.now()));
    }

    public EstadoVista suspender(UUID consultorioId, String motivo) {
        return actualizar(consultorioId, s -> s.suspender(motivo.strip(), Instant.now()));
    }

    public EstadoVista reactivar(UUID consultorioId) {
        return actualizar(consultorioId, s -> s.reactivar(Instant.now()));
    }

    public List<PlanVista> planes() {
        return comoSistema(() -> planes.findAllByOrderByMeses().stream().map(SuscripcionService::vista).toList());
    }

    public PlanVista actualizarPlan(String codigo, String nombre, long precio, boolean activo) {
        if (precio < 0) {
            throw ApiProblem.badRequest("El precio no puede ser negativo");
        }
        return comoSistema(() -> {
            PlanSuscripcion p = planes.findById(codigo).orElseThrow(() -> ApiProblem.notFound("Plan no encontrado"));
            p.actualizar(nombre.strip(), precio, activo);
            return vista(p);
        });
    }

    public String datosPago() {
        return comoSistema(() -> parametros.findById(ParametroPlataforma.DATOS_PAGO).map(ParametroPlataforma::getValor).orElse(""));
    }

    public String cambiarDatosPago(String valor) {
        return comoSistema(() -> {
            ParametroPlataforma p = parametros.findById(ParametroPlataforma.DATOS_PAGO).orElseThrow();
            p.cambiar(valor.strip());
            return p.getValor();
        });
    }

    // --- solo lectura (lo consulta el filtro de seguridad) ---

    @Override
    public boolean soloLectura(UUID consultorioId) {
        return TenantContext.callAs(consultorioId, () -> tx.execute(t -> obtener(consultorioId).estado(Instant.now())
                == Estado.SUSPENDIDA));
    }

    // --- ciclo de vida ---

    /** Un consultorio recién registrado empieza su prueba de 30 días. */
    @EventListener
    void alRegistrarse(ConsultorioRegistrado evento) {
        asegurar(evento.consultorioId());
        log.info("Consultorio {}: prueba de {} días", evento.consultorioId(), Suscripcion.PRUEBA.toDays());
    }

    /** Para avisos: todas las suscripciones con su consultorio. */
    List<Suscripcion> todas() {
        return comoSistema(suscripciones::findAll);
    }

    void avisar(UUID consultorioId, String asunto, String texto) {
        for (String para : identidad.correosAdministradores(consultorioId)) {
            correo.enviar(new Correo(para, asunto, texto));
        }
    }

    String fecha(Instant instante, UUID consultorioId) {
        return FECHA.format(instante.atZone(identidad.zonaHoraria(consultorioId)));
    }

    String nombreConsultorio(UUID consultorioId) {
        return identidad.consultorio(consultorioId).nombre();
    }

    // --- apoyo ---

    /** La suscripción del consultorio; si no existe (consultorio anterior a esta función), abre la prueba. */
    private Suscripcion obtener(UUID tenant) {
        return suscripciones.findByTenantId(tenant).orElseGet(() -> suscripciones.save(Suscripcion.enPrueba(Instant.now())));
    }

    private Suscripcion asegurar(UUID consultorioId) {
        return TenantContext.callAs(consultorioId, () -> tx.execute(t -> obtener(consultorioId)));
    }

    private Instant[] extender(UUID consultorioId, int meses) {
        ZoneId zona = identidad.zonaHoraria(consultorioId);
        // Quien llama ya hizo asegurar(): aquí estamos como SYSTEM y crearla dejaría mal el tenant
        Suscripcion s = suscripciones.paraActualizar(consultorioId).orElseThrow();
        return s.extenderPorPago(meses, Instant.now(), zona);
    }

    private EstadoVista actualizar(UUID consultorioId, java.util.function.Consumer<Suscripcion> cambio) {
        asegurar(consultorioId);
        EstadoVista vista = comoSistema(() -> {
            Suscripcion s = suscripciones.paraActualizar(consultorioId).orElseThrow();
            cambio.accept(s);
            return vista(s, Instant.now(), false);
        });
        eventos.publishEvent(new SuscripcionCambiada(consultorioId));
        return vista;
    }

    private <T> T comoSistema(java.util.function.Supplier<T> accion) {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> accion.get()));
    }

    private Map<UUID, String> nombresConsultorios() {
        return identidad.todosLosConsultorios().stream()
                .collect(Collectors.toMap(ConsultorioPlataforma::id, ConsultorioPlataforma::nombre));
    }

    static EstadoVista vista(Suscripcion s, Instant ahora, boolean pagoEnRevision) {
        Estado estado = s.estado(ahora);
        Instant vence = s.vence();
        // Mientras no vence, hacia arriba (recién registrado: "30 días"); ya vencida, hacia abajo (-1 = venció ayer)
        long segundos = Duration.between(ahora, vence).toSeconds();
        long dias = segundos >= 0 ? Math.ceilDiv(segundos, 86_400) : Math.floorDiv(segundos, 86_400);
        return new EstadoVista(estado, vence, vence.plus(Suscripcion.GRACIA), dias, s.getPagadaHasta() != null,
                estado == Estado.SUSPENDIDA, s.getMotivoSuspension(), pagoEnRevision);
    }

    private static PlanVista vista(PlanSuscripcion p) {
        return new PlanVista(p.getCodigo(), p.getNombre(), p.getMeses(), p.getPrecio(), p.isActivo());
    }

    private static PagoVista vista(PagoSuscripcion p, String consultorio) {
        return new PagoVista(p.getId(), p.getTenantId(), consultorio, p.getPlanCodigo(), p.getMeses(), p.getMonto(),
                p.getFechaPago(), p.getReferencia(), p.getEstado(), p.getMotivoRechazo(), p.getComprobanteTipo() != null,
                p.getReportadoEn(), p.getRevisadoEn(), p.getPeriodoDesde(), p.getPeriodoHasta());
    }

    public static String pesos(long valor) {
        return "$" + String.format(Locale.forLanguageTag("es-CO"), "%,d", valor).replace(',', '.');
    }

    /** Solo PDF, PNG o JPG, reconocidos por su contenido (no por la extensión). */
    static String tipoComprobante(byte[] b) {
        if (b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        throw ApiProblem.badRequest("El comprobante debe ser un PDF, PNG o JPG");
    }

    private static String nombreSeguro(String nombre) {
        String n = nombre == null || nombre.isBlank() ? "comprobante" : nombre.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }
}
