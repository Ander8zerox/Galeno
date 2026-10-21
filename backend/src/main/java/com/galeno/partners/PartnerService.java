package com.galeno.partners;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import com.galeno.identidad.CodigosReferido;
import com.galeno.identidad.ConsultorioRegistrado;
import com.galeno.identidad.IdentidadService;
import com.galeno.identidad.IdentidadService.ConsultorioPlataforma;
import com.galeno.identidad.SesionService;
import com.galeno.plataforma.PlataformaService;
import com.galeno.shared.correo.Correo;
import com.galeno.shared.correo.EnvioCorreo;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.security.TokenService;
import com.galeno.shared.security.TokenService.IssuedToken;
import com.galeno.shared.tenancy.TenantContext;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;
import com.galeno.shared.web.LimiteIntentos;
import com.galeno.suscripciones.SuscripcionService;
import com.galeno.suscripciones.SuscripcionService.EstadoVista;
import com.galeno.suscripciones.SuscripcionService.PagoAprobado;

/**
 * Programa de partners: quien vende Galeno a un consultorio recibe una comisión sobre cada pago de ese consultorio
 * mientras siga pagando, y el consultorio referido recibe un descuento.
 *
 * <ul>
 *   <li>El partner se inscribe (verificando su correo) y la plataforma lo aprueba; al aprobarlo recibe su código.</li>
 *   <li>Un consultorio que se registra con ese código queda atribuido al partner (a lo sumo uno, fijo).</li>
 *   <li>Cada pago aprobado de un referido genera una comisión retenida unos días, luego disponible.</li>
 *   <li>La plataforma liquida las disponibles cuando suman el mínimo: transfiere y registra el pago.</li>
 * </ul>
 * Se paga sobre pagos reales (la prueba gratis no genera nada) y no hay comisión si el partner es el mismo
 * administrador del consultorio o tiene su mismo NIT.
 */
@Service
class PartnerService implements CodigosReferido {

    static final String TERMINOS_VERSION = "2026-10";
    private static final Logger log = LoggerFactory.getLogger(PartnerService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> DOCUMENTOS = Set.of("CC", "CE", "PA", "PT", "NIT");
    private static final int MIN_PASSWORD = 10;

    private final PartnerRepository partners;
    private final InscripcionPartnerRepository inscripciones;
    private final ReferidoRepository referidos;
    private final ComisionPartnerRepository comisiones;
    private final LiquidacionPartnerRepository liquidaciones;
    private final IdentidadService identidad;
    private final SesionService sesiones;
    private final SuscripcionService suscripciones;
    private final PlataformaService plataforma;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final EnvioCorreo correo;
    private final LimiteIntentos limites;
    private final JsonMapper mapper;
    private final TransactionTemplate tx;
    private final String baseUrl;
    private final int comisionPorcentaje;
    private final int descuentoPorcentaje;
    private final long minimoLiquidacion;
    private final Duration retencion;
    private final int limitePorIp;

    PartnerService(PartnerRepository partners, InscripcionPartnerRepository inscripciones, ReferidoRepository referidos,
                   ComisionPartnerRepository comisiones, LiquidacionPartnerRepository liquidaciones,
                   IdentidadService identidad, SesionService sesiones, SuscripcionService suscripciones,
                   PlataformaService plataforma, TokenService tokens, PasswordEncoder passwordEncoder, EnvioCorreo correo,
                   LimiteIntentos limites, JsonMapper mapper, TransactionTemplate tx,
                   @Value("${app.web.base-url}") String baseUrl,
                   @Value("${app.partners.comision-porcentaje:20}") int comisionPorcentaje,
                   @Value("${app.partners.descuento-porcentaje:10}") int descuentoPorcentaje,
                   @Value("${app.partners.minimo-liquidacion:190000}") long minimoLiquidacion,
                   @Value("${app.partners.retencion-dias:30}") int retencionDias,
                   @Value("${app.registro.solicitudes-por-ip-hora:5}") int limitePorIp) {
        this.partners = partners;
        this.inscripciones = inscripciones;
        this.referidos = referidos;
        this.comisiones = comisiones;
        this.liquidaciones = liquidaciones;
        this.identidad = identidad;
        this.sesiones = sesiones;
        this.suscripciones = suscripciones;
        this.plataforma = plataforma;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.correo = correo;
        this.limites = limites;
        this.mapper = mapper;
        this.tx = tx;
        this.baseUrl = baseUrl;
        this.comisionPorcentaje = comisionPorcentaje;
        this.descuentoPorcentaje = descuentoPorcentaje;
        this.minimoLiquidacion = minimoLiquidacion;
        this.retencion = Duration.ofDays(retencionDias);
        this.limitePorIp = limitePorIp;
    }

    // ===================== público =====================

    @Override
    public Optional<String> partnerActivo(String codigo) {
        return comoSistema(() -> partners.findByCodigo(normalizarCodigo(codigo)).filter(Partner::activo).map(Partner::getNombre));
    }

    public record CodigoPublico(String codigo, String partner, int descuentoPorcentaje) {
    }

    /** Para la página de registro: a quién corresponde el código y qué descuento da. */
    public CodigoPublico consultarCodigo(String codigo) {
        String c = normalizarCodigo(codigo);
        return partnerActivo(c).map(nombre -> new CodigoPublico(c, nombre, descuentoPorcentaje))
                .orElseThrow(() -> ApiProblem.notFound("El código de partner no existe o no está activo"));
    }

    public record SolicitudInscripcion(String email, String password, DatosPartner datos, boolean aceptaTerminos) {
    }

    /** Guarda la inscripción y envía el enlace de verificación. Responde igual exista o no la cuenta. */
    public void inscribir(SolicitudInscripcion s, String ip) {
        if (!limites.permitir("partner-ip:" + ip, limitePorIp, Duration.ofHours(1))) {
            throw ApiProblem.tooManyRequests("Demasiadas solicitudes. Intente de nuevo en una hora.");
        }
        String email = s.email().strip().toLowerCase(Locale.ROOT);
        DatosPartner d = s.datos();
        if (!s.aceptaTerminos()) {
            throw ApiProblem.badRequest("Debe aceptar las condiciones del programa de partners");
        }
        if (s.password() == null || s.password().length() < MIN_PASSWORD) {
            throw ApiProblem.badRequest("La contraseña debe tener al menos " + MIN_PASSWORD + " caracteres");
        }
        if (!DOCUMENTOS.contains(d.tipoDocumento())) {
            throw ApiProblem.badRequest("Tipo de documento no válido: " + d.tipoDocumento());
        }
        String numero = d.numeroDocumento().replaceAll("[\\s.]", "").toUpperCase(Locale.ROOT);
        if (comoSistema(() -> partners.existsByTipoDocumentoAndNumeroDocumento(d.tipoDocumento(), numero))) {
            throw ApiProblem.conflict("Ya hay un partner inscrito con ese documento");
        }
        if (!limites.permitir("partner-email:" + email, 3, Duration.ofHours(1))) {
            throw ApiProblem.tooManyRequests("Ya enviamos varios enlaces a este correo. Revise su bandeja o intente en una hora.");
        }
        DatosPartner limpios = new DatosPartner(d.nombreCuenta().strip(), d.tipoPersona(), d.nombre().strip(),
                d.tipoDocumento(), numero, d.telefono().strip(), blankToNull(d.banco()), blankToNull(d.tipoCuenta()),
                blankToNull(d.numeroCuenta()));
        String token = nuevoToken();
        comoSistema(() -> {
            inscripciones.borrarVencidas(Instant.now());
            return inscripciones.save(new InscripcionPartner(hash(token), email, passwordEncoder.encode(s.password()),
                    mapper.writeValueAsString(limpios)));
        });
        correo.enviar(new Correo(email, "Confirme su inscripción como partner de Galeno", """
                Hola %s:

                Recibimos su inscripción al programa de partners de Galeno. Para confirmarla, abra este enlace:

                %s

                El enlace vence en 24 horas. Después revisaremos su inscripción y le avisaremos por correo cuando
                quede aprobada, con su código de referido.

                Galeno""".formatted(limpios.nombre(), baseUrl + "/partner/confirmar#" + token)));
    }

    public record InscripcionPublica(String email, String nombre, boolean cuentaExistente) {
    }

    public InscripcionPublica consultarInscripcion(String token) {
        InscripcionPartner i = comoSistema(() -> vigente(inscripciones.findByTokenHash(hash(token))));
        DatosPartner d = mapper.readValue(i.getDatos(), DatosPartner.class);
        return new InscripcionPublica(i.getEmail(), d.nombre(), identidad.existeCuenta(i.getEmail()));
    }

    public record SesionPartner(String accessToken, Instant expiresAt, String nombre, String email) {
    }

    /** Crea el partner (en revisión) con la cuenta nueva o, si el correo ya tenía cuenta, con su contraseña actual. */
    public SesionPartner confirmarInscripcion(String token, String passwordActual) {
        InscripcionPartner i = comoSistema(() -> vigente(inscripciones.paraConfirmar(hash(token))));
        DatosPartner d = mapper.readValue(i.getDatos(), DatosPartner.class);
        UUID usuarioId;
        String nombreCuenta;
        if (identidad.existeCuenta(i.getEmail())) {
            SesionService.Cuenta cuenta = sesiones.verificarCredenciales(i.getEmail(), passwordActual == null ? "" : passwordActual)
                    .orElseThrow(() -> ApiProblem.unauthorized("Ya tiene una cuenta en Galeno con este correo: ingrese su contraseña actual"));
            usuarioId = cuenta.id();
            nombreCuenta = cuenta.nombre();
        } else {
            usuarioId = identidad.crearCuenta(i.getEmail(), d.nombreCuenta(), i.getPasswordHash());
            nombreCuenta = d.nombreCuenta();
        }
        UUID uid = usuarioId;
        comoSistema(() -> {
            if (partners.findByUsuarioId(uid).isPresent()) {
                throw ApiProblem.conflict("Esta cuenta ya está inscrita como partner");
            }
            InscripcionPartner bloqueada = vigente(inscripciones.paraConfirmar(hash(token)));
            bloqueada.confirmar();
            return partners.save(new Partner(uid, d, TERMINOS_VERSION));
        });
        plataforma.notificarOperadores("Partner por aprobar: " + d.nombre(), """
                %s (%s %s) se inscribió como partner.

                Revíselo en la consola de la plataforma, en Partners.""".formatted(d.nombre(), d.tipoDocumento(), d.numeroDocumento()));
        IssuedToken t = tokens.issuePartner(usuarioId, nombreCuenta, i.getEmail());
        return new SesionPartner(t.value(), t.expiresAt(), nombreCuenta, i.getEmail());
    }

    public SesionPartner login(String email, String password) {
        SesionService.Cuenta cuenta = sesiones.verificarCredenciales(email, password)
                .filter(c -> comoSistema(() -> partners.findByUsuarioId(c.id()).isPresent()))
                .orElseThrow(() -> ApiProblem.unauthorized("Correo o contraseña incorrectos"));
        IssuedToken t = tokens.issuePartner(cuenta.id(), cuenta.nombre(), cuenta.email());
        return new SesionPartner(t.value(), t.expiresAt(), cuenta.nombre(), cuenta.email());
    }

    // ===================== portal del partner =====================

    public record Saldo(long retenido, long disponible, long pagado) {
    }

    public record ReferidoVista(UUID consultorioId, String consultorio, Instant desde, Referido.Origen origen,
                                String estadoSuscripcion) {
    }

    public record ComisionVista(UUID id, UUID partnerId, String consultorio, long base, int porcentaje, long valor,
                                ComisionPartner.Estado estado, Instant creadaEn, Instant disponibleDesde,
                                String motivoAnulacion) {
    }

    public record LiquidacionVista(UUID id, long total, int comisiones, String referencia, LocalDate fechaPago,
                                   Instant creadaEn) {
    }

    public record PartnerVista(UUID id, String nombre, Partner.TipoPersona tipoPersona, String tipoDocumento,
                               String numeroDocumento, String email, String telefono, String banco, String tipoCuenta,
                               String numeroCuenta, Partner.Estado estado, String motivo, String codigo, String enlace,
                               Instant creadoEn, Instant aprobadoEn, long referidos, Saldo saldo) {
    }

    /** @param puedeLiquidar el disponible alcanza el mínimo */
    public record Panel(PartnerVista partner, int comisionPorcentaje, int descuentoPorcentaje, long minimoLiquidacion,
                        boolean puedeLiquidar, List<ReferidoVista> referidos, List<ComisionVista> comisiones,
                        List<LiquidacionVista> liquidaciones) {
    }

    public Panel miPanel() {
        return panel(miPartner().getId());
    }

    public PartnerVista actualizarDatosPago(String banco, String tipoCuenta, String numeroCuenta, String telefono) {
        UUID id = miPartner().getId();
        comoSistema(() -> {
            partners.findById(id).orElseThrow().actualizarPago(blankToNull(banco), blankToNull(tipoCuenta),
                    blankToNull(numeroCuenta), telefono.strip());
            return null;
        });
        return panel(id).partner();
    }

    // ===================== consola de la plataforma =====================

    public List<PartnerVista> partners() {
        plataforma.operadorActual();
        Map<UUID, String> correos = correos();
        Instant ahora = Instant.now();
        return comoSistema(() -> {
            Map<UUID, List<ComisionPartner>> porPartner = comisiones.findAllByOrderByCreadaEnDesc().stream()
                    .collect(Collectors.groupingBy(ComisionPartner::getPartnerId));
            return partners.findAllByOrderByCreadoEnDesc().stream()
                    .map(p -> vista(p, correos.get(p.getUsuarioId()), referidos.countByPartnerId(p.getId()),
                            saldo(porPartner.getOrDefault(p.getId(), List.of()), ahora)))
                    .toList();
        });
    }

    public Panel detalle(UUID partnerId) {
        plataforma.operadorActual();
        return panel(partnerId);
    }

    public PartnerVista aprobar(UUID partnerId) {
        Partner p = comoSistema(() -> {
            Partner partner = partners.findById(partnerId).orElseThrow(() -> ApiProblem.notFound("Partner no encontrado"));
            boolean primeraVez = partner.getCodigo() == null;
            partner.aprobar(primeraVez ? codigoUnico(partner.getNombre()) : null);
            return partner;
        });
        plataforma.auditar("APROBAR_PARTNER", null, p.getNombre() + " · " + p.getCodigo());
        correos().entrySet().stream().filter(e -> e.getKey().equals(p.getUsuarioId())).findFirst()
                .ifPresent(e -> correo.enviar(new Correo(e.getValue(), "Ya es partner de Galeno", """
                        Hola %s:

                        Aprobamos su inscripción. Su código de referido es %s y su enlace:

                        %s

                        Los consultorios que se registren con su código reciben %d%% de descuento, y usted recibe el %d%%
                        de cada pago que hagan mientras sigan usando Galeno. Vea sus referidos y comisiones en %s.

                        Galeno""".formatted(p.getNombre(), p.getCodigo(), enlace(p.getCodigo()), descuentoPorcentaje,
                        comisionPorcentaje, baseUrl + "/partner"))));
        return panel(partnerId).partner();
    }

    public PartnerVista rechazar(UUID partnerId, String motivo) {
        Partner p = cambiar(partnerId, partner -> partner.rechazar(motivo.strip()));
        plataforma.auditar("RECHAZAR_PARTNER", null, p.getNombre() + " · " + motivo);
        return panel(partnerId).partner();
    }

    public PartnerVista suspender(UUID partnerId, String motivo) {
        Partner p = cambiar(partnerId, partner -> partner.suspender(motivo.strip()));
        plataforma.auditar("SUSPENDER_PARTNER", null, p.getNombre() + " · " + motivo);
        return panel(partnerId).partner();
    }

    /** Atribuye (o reasigna) un consultorio a un partner: p. ej. un cliente que llegó sin el enlace. */
    public ReferidoVista asignarReferido(UUID consultorioId, String codigo) {
        String nombre = identidad.consultorio(consultorioId).nombre();
        Partner p = comoSistema(() -> partners.findByCodigo(normalizarCodigo(codigo)).filter(Partner::activo)
                .orElseThrow(() -> ApiProblem.badRequest("El código de partner no existe o no está activo")));
        Referido r = comoSistema(() -> referidos.findById(consultorioId).map(existente -> {
            existente.reasignar(p.getId(), Referido.Origen.PLATAFORMA);
            return existente;
        }).orElseGet(() -> referidos.save(new Referido(consultorioId, p.getId(), Referido.Origen.PLATAFORMA))));
        suscripciones.aplicarDescuento(consultorioId, descuentoPorcentaje);
        plataforma.auditar("ASIGNAR_REFERIDO", consultorioId, p.getNombre() + " · " + p.getCodigo());
        return new ReferidoVista(consultorioId, nombre, r.getAtribuidoEn(), r.getOrigen(),
                suscripciones.estadoDe(consultorioId).estado().name());
    }

    public ComisionVista anularComision(UUID comisionId, String motivo) {
        ComisionPartner c = comoSistema(() -> {
            ComisionPartner comision = comisiones.findById(comisionId).orElseThrow(() -> ApiProblem.notFound("Comisión no encontrada"));
            comision.anular(motivo.strip(), Instant.now());
            return comision;
        });
        plataforma.auditar("ANULAR_COMISION", c.getConsultorioId(), c.getValor() + " · " + motivo);
        return vista(c, identidad.consultorio(c.getConsultorioId()).nombre(), Instant.now());
    }

    /** Paga al partner todas sus comisiones disponibles (deben sumar al menos el mínimo). */
    public LiquidacionVista liquidar(UUID partnerId, String referencia, LocalDate fechaPago) {
        UUID operador = plataforma.operadorActual();
        Instant ahora = Instant.now();
        LiquidacionPartner l = comoSistema(() -> {
            Partner p = partners.findById(partnerId).orElseThrow(() -> ApiProblem.notFound("Partner no encontrado"));
            List<ComisionPartner> disponibles = comisiones.pendientesParaLiquidar(partnerId).stream()
                    .filter(c -> c.estado(ahora) == ComisionPartner.Estado.DISPONIBLE).toList();
            long total = disponibles.stream().mapToLong(ComisionPartner::getValor).sum();
            if (total < minimoLiquidacion) {
                throw ApiProblem.badRequest("El disponible (" + SuscripcionService.pesos(total) + ") no alcanza el mínimo de "
                        + SuscripcionService.pesos(minimoLiquidacion));
            }
            if (p.getNumeroCuenta() == null) {
                throw ApiProblem.badRequest("El partner no tiene registrada una cuenta para el pago");
            }
            LiquidacionPartner nueva = liquidaciones.save(new LiquidacionPartner(partnerId, total, disponibles.size(),
                    referencia.strip(), fechaPago, operador));
            disponibles.forEach(c -> c.liquidar(nueva.getId(), ahora));
            return nueva;
        });
        plataforma.auditar("LIQUIDAR_PARTNER", null, partnerId + " · " + l.getTotal() + " · " + referencia);
        Partner p = comoSistema(() -> partners.findById(partnerId).orElseThrow());
        Optional.ofNullable(correos().get(p.getUsuarioId())).ifPresent(email -> correo.enviar(new Correo(email,
                "Le pagamos sus comisiones de Galeno", """
                        Hola %s:

                        Transferimos %s por %d comisiones (referencia %s, %s). Vea el detalle en %s.

                        Galeno""".formatted(p.getNombre(), SuscripcionService.pesos(l.getTotal()), l.getComisiones(),
                        l.getReferencia(), l.getFechaPago(), baseUrl + "/partner"))));
        return vista(l);
    }

    // ===================== eventos =====================

    /** Un consultorio que se registra con un código queda atribuido al partner y recibe el descuento. */
    @EventListener
    void alRegistrarseConsultorio(ConsultorioRegistrado e) {
        if (e.codigoPartner() == null) {
            return;
        }
        Optional<Partner> partner = comoSistema(() -> partners.findByCodigo(normalizarCodigo(e.codigoPartner())).filter(Partner::activo));
        if (partner.isEmpty()) {
            log.info("Consultorio {}: el código {} ya no está activo, no se atribuye", e.consultorioId(), e.codigoPartner());
            return;
        }
        Partner p = partner.get();
        boolean mismoAdmin = p.getUsuarioId().equals(e.administradorId());
        boolean mismoNit = e.nit() != null && "NIT".equals(p.getTipoDocumento())
                && e.nit().startsWith(p.getNumeroDocumento().replaceAll("-.*", ""));
        if (mismoAdmin || mismoNit) {
            log.info("Consultorio {}: autorreferido del partner {}, no se atribuye", e.consultorioId(), p.getCodigo());
            return;
        }
        comoSistema(() -> referidos.findById(e.consultorioId())
                .orElseGet(() -> referidos.save(new Referido(e.consultorioId(), p.getId(), Referido.Origen.REGISTRO))));
        suscripciones.aplicarDescuento(e.consultorioId(), descuentoPorcentaje);
    }

    /** Cada pago aprobado de un consultorio referido por un partner activo genera su comisión. */
    @EventListener
    void alAprobarPago(PagoAprobado pago) {
        comoSistema(() -> {
            Optional<Referido> referido = referidos.findById(pago.consultorioId());
            if (referido.isEmpty() || comisiones.existsByPagoId(pago.pagoId())) {
                return null;
            }
            Partner p = partners.findById(referido.get().getPartnerId()).orElseThrow();
            if (!p.activo()) {
                log.info("Pago {}: el partner {} no está activo, no genera comisión", pago.pagoId(), p.getCodigo());
                return null;
            }
            comisiones.save(new ComisionPartner(p.getId(), pago.consultorioId(), pago.pagoId(), pago.monto(),
                    comisionPorcentaje, pago.aprobadoEn(), retencion));
            return null;
        });
    }

    // ===================== apoyo =====================

    private Partner miPartner() {
        UUID usuario = AuthUser.current().id();
        return comoSistema(() -> partners.findByUsuarioId(usuario))
                .orElseThrow(() -> ApiProblem.forbidden("Esta cuenta no está inscrita como partner"));
    }

    private Panel panel(UUID partnerId) {
        Instant ahora = Instant.now();
        Map<UUID, String> nombres = identidad.todosLosConsultorios().stream()
                .collect(Collectors.toMap(ConsultorioPlataforma::id, ConsultorioPlataforma::nombre));
        record Datos(Partner p, List<Referido> refs, List<ComisionPartner> coms, List<LiquidacionPartner> liqs) {
        }
        Datos d = comoSistema(() -> new Datos(
                partners.findById(partnerId).orElseThrow(() -> ApiProblem.notFound("Partner no encontrado")),
                referidos.findByPartnerIdOrderByAtribuidoEnDesc(partnerId),
                comisiones.findByPartnerIdOrderByCreadaEnDesc(partnerId),
                liquidaciones.findByPartnerIdOrderByCreadaEnDesc(partnerId)));
        Saldo saldo = saldo(d.coms(), ahora);
        PartnerVista pv = vista(d.p(), correos().get(d.p().getUsuarioId()), d.refs().size(), saldo);
        List<ReferidoVista> refs = d.refs().stream().map(r -> {
            EstadoVista e = suscripciones.estadoDe(r.getConsultorioId());
            return new ReferidoVista(r.getConsultorioId(), nombres.getOrDefault(r.getConsultorioId(), "?"),
                    r.getAtribuidoEn(), r.getOrigen(), e.estado().name());
        }).toList();
        return new Panel(pv, comisionPorcentaje, descuentoPorcentaje, minimoLiquidacion,
                saldo.disponible() >= minimoLiquidacion, refs,
                d.coms().stream().map(c -> vista(c, nombres.getOrDefault(c.getConsultorioId(), "?"), ahora)).toList(),
                d.liqs().stream().map(PartnerService::vista).toList());
    }

    static Saldo saldo(List<ComisionPartner> lista, Instant ahora) {
        long retenido = 0;
        long disponible = 0;
        long pagado = 0;
        for (ComisionPartner c : lista) {
            switch (c.estado(ahora)) {
                case RETENIDA -> retenido += c.getValor();
                case DISPONIBLE -> disponible += c.getValor();
                case PAGADA -> pagado += c.getValor();
                default -> { }
            }
        }
        return new Saldo(retenido, disponible, pagado);
    }

    private PartnerVista vista(Partner p, String email, long numReferidos, Saldo saldo) {
        return new PartnerVista(p.getId(), p.getNombre(), p.getTipoPersona(), p.getTipoDocumento(), p.getNumeroDocumento(),
                email, p.getTelefono(), p.getBanco(), p.getTipoCuenta(), p.getNumeroCuenta(), p.getEstado(), p.getMotivo(),
                p.getCodigo(), p.getCodigo() == null ? null : enlace(p.getCodigo()), p.getCreadoEn(), p.getAprobadoEn(),
                numReferidos, saldo);
    }

    private static ComisionVista vista(ComisionPartner c, String consultorio, Instant ahora) {
        return new ComisionVista(c.getId(), c.getPartnerId(), consultorio, c.getBase(), c.getPorcentaje(), c.getValor(),
                c.estado(ahora), c.getCreadaEn(), c.getDisponibleDesde(), c.getMotivoAnulacion());
    }

    private static LiquidacionVista vista(LiquidacionPartner l) {
        return new LiquidacionVista(l.getId(), l.getTotal(), l.getComisiones(), l.getReferencia(), l.getFechaPago(),
                l.getCreadaEn());
    }

    private Partner cambiar(UUID partnerId, java.util.function.Consumer<Partner> cambio) {
        return comoSistema(() -> {
            Partner p = partners.findById(partnerId).orElseThrow(() -> ApiProblem.notFound("Partner no encontrado"));
            cambio.accept(p);
            return p;
        });
    }

    /** Correo de cada cuenta de partner (las cuentas son de identidad). */
    private Map<UUID, String> correos() {
        List<UUID> ids = comoSistema(() -> partners.findAll().stream().map(Partner::getUsuarioId).toList());
        return identidad.correosDeCuentas(ids);
    }

    private String enlace(String codigo) {
        return baseUrl + "/registro?ref=" + codigo;
    }

    /** "Laura Restrepo" → "LAURAR" + 3 dígitos; se reintenta si ya existe. */
    private String codigoUnico(String nombre) {
        String letras = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        String prefijo = (letras.isEmpty() ? "GALENO" : letras).substring(0, Math.min(6, Math.max(letras.length(), 1)));
        for (int i = 0; i < 20; i++) {
            String c = prefijo + (100 + RANDOM.nextInt(900));
            if (!partners.existsByCodigo(c)) {
                return c;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de partner único");
    }

    static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
    }

    private static InscripcionPartner vigente(Optional<InscripcionPartner> i) {
        return i.filter(InscripcionPartner::vigente)
                .orElseThrow(() -> ApiProblem.notFound("El enlace no es válido, ya se usó o venció. Inscríbase de nuevo."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String nuevoToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T comoSistema(java.util.function.Supplier<T> accion) {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> accion.get()));
    }
}
