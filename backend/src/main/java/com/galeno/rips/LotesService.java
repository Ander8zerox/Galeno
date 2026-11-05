package com.galeno.rips;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.historia.AccesoHistoria.Accion;
import com.galeno.historia.HistoriaService;
import com.galeno.historia.HistoriaService.AtencionReferencia;
import com.galeno.historia.HistoriaService.AtencionRips;
import com.galeno.historia.HistoriaService.DiagnosticoVista;
import com.galeno.historia.HistoriaService.ProcedimientoVista;
import com.galeno.historia.ListaPrecios;
import com.galeno.identidad.IdentidadService;
import com.galeno.modulos.Modulo;
import com.galeno.modulos.ModulosService;
import com.galeno.pacientes.Paciente;
import com.galeno.pacientes.PacienteService;
import com.galeno.rips.ArmadorRips.HallazgoRips;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lotes del RIPS: se crean con las atenciones firmadas de un pagador en un período, se revisan (lo que falta o no pasa
 * una regla, con dónde corregirlo), se generan (el JSON queda guardado tal cual) y se anulan. El envío al Ministerio
 * (MUV) es de la fase 3. Ver un lote muestra diagnósticos: queda en la auditoría de la historia de cada paciente.
 */
@Service
public class LotesService {

    /** Un año de atenciones o dos mil: más que eso no es una factura de consultorio. */
    static final int MAX_DIAS = 366;
    static final int MAX_ATENCIONES = 2000;
    private static final java.util.regex.Pattern FACTURA = java.util.regex.Pattern.compile("[A-Za-z0-9-]{1,20}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final LoteRepository lotes;
    private final PagadoresService pagadores;
    private final CobrosService cobros;
    private final DatosRipsService datos;
    private final ReferenciasService referencias;
    private final RipsPacienteRepository pacientesRips;
    private final RipsPrestadorRepository prestadores;
    private final RipsProfesionalRepository profesionales;
    private final PreciosService precios;
    private final HistoriaService historia;
    private final PacienteService pacientes;
    private final IdentidadService identidad;
    private final ModulosService modulos;

    LotesService(LoteRepository lotes, PagadoresService pagadores, CobrosService cobros, DatosRipsService datos,
                 ReferenciasService referencias, RipsPacienteRepository pacientesRips,
                 RipsPrestadorRepository prestadores, RipsProfesionalRepository profesionales, PreciosService precios,
                 HistoriaService historia, PacienteService pacientes, IdentidadService identidad,
                 ModulosService modulos) {
        this.lotes = lotes;
        this.pagadores = pagadores;
        this.cobros = cobros;
        this.datos = datos;
        this.referencias = referencias;
        this.pacientesRips = pacientesRips;
        this.prestadores = prestadores;
        this.profesionales = profesionales;
        this.precios = precios;
        this.historia = historia;
        this.pacientes = pacientes;
        this.identidad = identidad;
        this.modulos = modulos;
    }

    @Transactional(readOnly = true)
    public List<LoteRipsResumen> listar() {
        modulos.exigir(Modulo.RIPS);
        Map<UUID, String> nombres = new java.util.HashMap<>();
        return lotes.findAllByOrderByCreadoEnDesc().stream().map(l -> new LoteRipsResumen(l.getId(), l.getPagadorId(),
                nombres.computeIfAbsent(l.getPagadorId(), id -> pagadores.get(id).getNombre()), l.isSinFactura(), l.getDesde(), l.getHasta(), l.getNumeroFactura(),
                l.getEstado(), l.getAtenciones().size(), l.getCreadoEn(), l.getGeneradoEn(), l.getAnuladoEn())).toList();
    }

    /**
     * Con las atenciones firmadas del período cuyo pagador (el de la atención, su cita o el paciente) es ese, que no
     * estén en otro lote vivo.
     *
     * @param sinFactura RIPS sin factura (RS): solo con el pagador particular
     */
    @Transactional
    public LoteRipsVista crear(UUID pagadorId, LocalDate desde, LocalDate hasta, boolean sinFactura) {
        modulos.exigir(Modulo.RIPS);
        if (pagadorId == null || desde == null || hasta == null) {
            throw ApiProblem.badRequest("Elija el pagador y las fechas");
        }
        if (hasta.isBefore(desde)) {
            throw ApiProblem.badRequest("La fecha final es anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAX_DIAS) {
            throw ApiProblem.badRequest("El período no puede pasar de un año");
        }
        Pagador pagador = pagadores.get(pagadorId);
        if (sinFactura && pagador.getTipo() != Pagador.Tipo.PARTICULAR) {
            throw ApiProblem.badRequest("El RIPS sin factura es para particulares atendidos por un profesional no "
                    + "obligado a facturar");
        }
        ZoneId zona = zona();
        List<AtencionRips> delPeriodo = historia.atencionesRips(desde.atStartOfDay(zona).toInstant(),
                hasta.plusDays(1).atStartOfDay(zona).toInstant());
        Set<UUID> ocupadas = delPeriodo.isEmpty() ? Set.of()
                : new HashSet<>(lotes.enLotesVivos(delPeriodo.stream().map(AtencionRips::id).toList()));
        List<UUID> ids = delPeriodo.stream()
                .filter(a -> !ocupadas.contains(a.id()))
                .filter(a -> pagadorId.equals(cobros.vigente(referencia(a)).pagadorId()))
                .map(AtencionRips::id).toList();
        if (ids.isEmpty()) {
            throw ApiProblem.badRequest("No hay atenciones firmadas de " + pagador.getNombre() + " entre " + desde + " y "
                    + hasta + " que no estén ya en otro lote");
        }
        if (ids.size() > MAX_ATENCIONES) {
            throw ApiProblem.badRequest("Son " + ids.size() + " atenciones: acorte el período (máximo " + MAX_ATENCIONES
                    + " por lote)");
        }
        Lote lote = lotes.saveAndFlush(new Lote(pagadorId, sinFactura, desde, hasta, ids, AuthUser.current().id()));
        return vista(lote, true);
    }

    @Transactional
    public LoteRipsVista obtener(UUID loteId) {
        modulos.exigir(Modulo.RIPS);
        return vista(lote(loteId), true);
    }

    /** Solo en borrador: libera sus atenciones. */
    @Transactional
    public void eliminar(UUID loteId) {
        modulos.exigir(Modulo.RIPS);
        Lote lote = lote(loteId);
        lote.exigirBorrador();
        lotes.delete(lote);
    }

    @Transactional
    public LoteRipsVista quitarAtencion(UUID loteId, UUID atencionId) {
        modulos.exigir(Modulo.RIPS);
        Lote lote = lote(loteId);
        lote.quitar(atencionId);
        return vista(lotes.saveAndFlush(lote), true);
    }

    @Transactional
    public LoteRipsVista ajustar(UUID loteId, UUID atencionId, ArmadorRips.Ajustes a) {
        modulos.exigir(Modulo.RIPS);
        String finalidad = referencias.validar("finalidad", a.finalidad(), "Finalidad");
        String causa = referencias.validar("causa_externa", a.causaExterna(), "Causa externa");
        referencias.exigirParaConsultas("finalidad", finalidad, "Finalidad");
        referencias.exigirParaConsultas("causa_externa", causa, "Causa externa");
        Lote lote = lote(loteId);
        lote.ajustar(atencionId, new ArmadorRips.Ajustes(finalidad, causa,
                referencias.validar("modalidad_atencion", a.modalidadAtencion(), "Modalidad de atención"),
                referencias.validar("grupo_servicios", a.grupoServicios(), "Grupo de servicios"),
                referencias.validar("servicio", a.servicio(), "Servicio"),
                referencias.validar("via_ingreso", a.viaIngreso(), "Vía de ingreso")));
        return vista(lotes.saveAndFlush(lote), true);
    }

    @Transactional
    public LoteRipsVista cambiarFactura(UUID loteId, String numero) {
        modulos.exigir(Modulo.RIPS);
        String n = numero == null || numero.isBlank() ? null : numero.strip().toUpperCase();
        if (n != null && !FACTURA.matcher(n).matches()) {
            throw ApiProblem.badRequest("Número de factura: hasta 20 letras, números o guiones (prefijo y consecutivo)");
        }
        Lote lote = lote(loteId);
        lote.cambiarFactura(n);
        return vista(lotes.saveAndFlush(lote), true);
    }

    /** @param contenido {@code null}: quita el XML cargado */
    @Transactional
    public LoteRipsVista cargarXml(UUID loteId, byte[] contenido) {
        modulos.exigir(Modulo.RIPS);
        Lote lote = lote(loteId);
        lote.cargarXml(contenido == null ? null : FacturaXml.leer(contenido));
        return vista(lotes.saveAndFlush(lote), true);
    }

    /** Con algún hallazgo de rechazo no se genera: la respuesta los lista. */
    @Transactional
    public LoteRipsVista generar(UUID loteId) {
        modulos.exigir(Modulo.RIPS);
        Lote lote = lote(loteId);
        lote.exigirBorrador();
        Armado armado = armar(lote);
        long rechazos = armado.resultado().hallazgos().stream().filter(h -> h.nivel() == ArmadorRips.Nivel.RECHAZO).count();
        if (rechazos > 0) {
            throw ApiProblem.badRequest("No se puede generar: hay " + rechazos + (rechazos == 1 ? " problema" : " problemas")
                    + " que el Ministerio rechazaría. Corríjalos o quite esas atenciones del lote.");
        }
        lote.generar(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(armado.resultado().json()),
                AuthUser.current().id());
        lotes.saveAndFlush(lote);
        auditar(armado, Accion.GENERAR_RIPS, lote.getId());
        return vista(lote, armado, false);
    }

    @Transactional
    public LoteRipsVista anular(UUID loteId, String motivo) {
        modulos.exigir(Modulo.RIPS);
        if (motivo == null || motivo.isBlank()) {
            throw ApiProblem.badRequest("Escriba el motivo de la anulación");
        }
        Lote lote = lote(loteId);
        if (lote.getEstado() == Lote.Estado.BORRADOR) {
            throw ApiProblem.conflict("Un lote en borrador no se anula: elimínelo");
        }
        lote.anular(motivo.strip(), AuthUser.current().id());
        return vista(lotes.saveAndFlush(lote), false);
    }

    /** El JSON tal como se generó. */
    @Transactional
    public Archivo descargar(UUID loteId) {
        modulos.exigir(Modulo.RIPS);
        Lote lote = lote(loteId);
        if (lote.getJson() == null) {
            throw ApiProblem.conflict("El lote no se ha generado");
        }
        historia.atencionesRips(ids(lote)).stream().map(AtencionRips::pacienteId).distinct()
                .forEach(p -> historia.registrarAcceso(p, Accion.VER_RIPS, lote.getId()));
        String nombre = lote.isSinFactura() ? "RIPS_sin_factura_" + lote.getDesde() + "_" + lote.getHasta()
                : "RIPS_" + lote.getNumeroFactura();
        return new Archivo(nombre + ".json", lote.getJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // --- armado ---

    private Lote lote(UUID id) {
        return lotes.findById(id).orElseThrow(() -> ApiProblem.notFound("Lote no encontrado"));
    }

    private ZoneId zona() {
        return identidad.consultorio(AuthUser.current().tenantId()).zona();
    }

    private static List<UUID> ids(Lote lote) {
        return lote.getAtenciones().stream().map(LoteAtencion::getAtencionId).toList();
    }

    private static AtencionReferencia referencia(AtencionRips a) {
        return new AtencionReferencia(a.id(), a.pacienteId(), a.citaId(), a.medicoUsuarioId(), true);
    }

    private record Armado(List<ArmadorRips.AtencionLote> atenciones, ArmadorRips.Resultado resultado) {
    }

    /** Reúne lo que el armador necesita (atenciones, pacientes, cobros, profesionales, catálogos) y arma. */
    private Armado armar(Lote lote) {
        var consultorio = identidad.consultorio(AuthUser.current().tenantId());
        Pagador.Datos pd = pagadores.get(lote.getPagadorId()).datos();
        ArmadorRips.PagadorRips pagador = new ArmadorRips.PagadorRips(lote.getPagadorId(), pd.nombre(), pd.tipo(),
                pd.cobertura(), pd.modalidadPago());
        ArmadorRips.PrestadorRips prestador = prestadores.findFirstBy()
                .map(p -> new ArmadorRips.PrestadorRips(p.getCodigoHabilitacion(), p.getServicio(), p.getModalidadAtencion(),
                        p.getGrupoServicios(), p.getFinalidad(), p.getCausaExterna(), p.getViaIngreso()))
                .orElse(new ArmadorRips.PrestadorRips(null, null, null, null, null, null, null));

        List<AtencionRips> ats = historia.atencionesRips(ids(lote));
        List<UUID> pacienteIds = ats.stream().map(AtencionRips::pacienteId).distinct().toList();
        Map<UUID, RipsPaciente> rips = pacientesRips.findAllById(pacienteIds).stream()
                .collect(Collectors.toMap(RipsPaciente::getPacienteId, Function.identity()));
        Map<UUID, List<String>> faltantes = pacienteIds.isEmpty() ? Map.of() : datos.faltantes(pacienteIds);
        Map<UUID, ArmadorRips.PacienteRips> fichas = new LinkedHashMap<>();
        for (UUID id : pacienteIds) {
            Paciente p = pacientes.get(id);
            RipsPaciente r = rips.getOrDefault(id, new RipsPaciente(id));
            fichas.put(id, new ArmadorRips.PacienteRips(id, p.getNombres() + " " + p.getApellidos(), p.getTipoDocumento(),
                    p.getNumeroDocumento(), p.getFechaNacimiento(), p.getSexo(), r.getTipoUsuario(), r.getPaisResidencia(),
                    r.getMunicipioResidencia(), r.getZonaResidencia(), r.getPaisOrigen(),
                    faltantes.getOrDefault(id, List.of()).stream().filter(f -> !f.equals("Pagador")).toList()));
        }
        Map<UUID, ArmadorRips.ProfesionalRips> docs = profesionales.findAll().stream().collect(Collectors.toMap(
                RipsProfesional::getUsuarioId, p -> new ArmadorRips.ProfesionalRips(p.getTipoDocumento(), p.getNumeroDocumento())));

        List<ArmadorRips.AtencionLote> entrada = new ArrayList<>();
        Set<String> finalidades = new HashSet<>();
        Stream.of(prestador.finalidad()).filter(Objects::nonNull).forEach(finalidades::add);
        for (AtencionRips a : ats) {
            ArmadorRips.Ajustes aj = lote.atencion(a.id()).map(LoteAtencion::ajustes).orElse(ArmadorRips.Ajustes.NINGUNO);
            if (aj.finalidad() != null) {
                finalidades.add(aj.finalidad());
            }
            List<String> sinValor = a.procedimientos().stream().filter(p -> p.valorUnitario() == null)
                    .map(ProcedimientoVista::codigoCups).distinct().toList();
            Map<String, BigDecimal> vigentes = sinValor.isEmpty() ? Map.of()
                    : precios.precios(sinValor, new ListaPrecios.Contexto(a.pacienteId(), a.citaId(), a.id()));
            entrada.add(new ArmadorRips.AtencionLote(a, fichas.get(a.pacienteId()), cobros.vigente(referencia(a)),
                    docs.get(a.medicoUsuarioId()), aj, vigentes));
        }
        Set<String> cups = ats.stream().flatMap(a -> a.procedimientos().stream()).map(ProcedimientoVista::codigoCups)
                .collect(Collectors.toSet());
        Set<String> cie10 = ats.stream().flatMap(a -> a.diagnosticos().stream()).map(DiagnosticoVista::codigoCie10)
                .collect(Collectors.toSet());
        var resultado = ArmadorRips.armar(new ArmadorRips.Entrada(consultorio.nit(), lote.isSinFactura(),
                lote.getNumeroFactura(), pagador, prestador, entrada, referencias.filas("cups_rips", cups),
                historia.cie10(cie10), referencias.filas("finalidad", finalidades), lote.xml(), consultorio.zona(),
                Instant.now()));
        return new Armado(entrada, resultado);
    }

    private void auditar(Armado armado, Accion accion, UUID loteId) {
        armado.atenciones().stream().map(a -> a.atencion().pacienteId()).distinct()
                .forEach(p -> historia.registrarAcceso(p, accion, loteId));
    }

    // --- vistas ---

    /** @param revisar en borrador, arma y revisa; generado o anulado, solo lista las atenciones */
    private LoteRipsVista vista(Lote lote, boolean revisar) {
        Armado armado = armar(lote);
        auditar(armado, Accion.VER_RIPS, lote.getId());
        return vista(lote, armado, revisar && lote.getEstado() == Lote.Estado.BORRADOR);
    }

    private LoteRipsVista vista(Lote lote, Armado armado, boolean conHallazgos) {
        List<HallazgoRips> hallazgos = conHallazgos ? armado.resultado().hallazgos() : List.of();
        ZoneId zona = zona();
        List<AtencionDelLote> lista = armado.atenciones().stream().map(a -> {
            AtencionRips at = a.atencion();
            List<String> servicios = at.procedimientos().stream().map(p -> {
                BigDecimal v = p.valorUnitario() != null ? p.valorUnitario() : a.precios().get(p.codigoCups());
                return new ServicioVista(p.codigoCups(), p.descripcion(), p.cantidad(), v, p.consulta());
            }).map(ServicioVista::texto).toList();
            List<HallazgoRips> propios = hallazgos.stream().filter(h -> at.id().equals(h.atencionId())
                    || (h.atencionId() == null && at.pacienteId().equals(h.pacienteId()))).toList();
            var c = a.cobro();
            return new AtencionDelLote(at.id(), at.pacienteId(), a.paciente().nombre(),
                    a.paciente().tipoDocumento() + " " + a.paciente().numeroDocumento(),
                    at.fecha().atZone(zona).toLocalDateTime(), at.medicoNombre(), servicios,
                    c == null ? null : c.conceptoRecaudo(), c == null ? BigDecimal.ZERO : c.valorPagoModerador(),
                    c == null ? null : c.numeroAutorizacion(), at.incapacidad(), a.ajustes(), propios);
        }).toList();
        List<HallazgoRips> delLote = hallazgos.stream().filter(h -> h.atencionId() == null && h.pacienteId() == null).toList();
        // Generado: los totales del JSON guardado, no de lo que resultaría hoy
        Map<String, Object> json = lote.getJson() == null ? armado.resultado().json()
                : JSON.readValue(lote.getJson(), new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        return new LoteRipsVista(lote.getId(), lote.getPagadorId(), pagadores.get(lote.getPagadorId()).getNombre(),
                lote.isSinFactura(), lote.getDesde(), lote.getHasta(), lote.getNumeroFactura(), lote.getEstado(),
                lote.xml(), lote.getCreadoEn(), lote.getGeneradoEn(), lote.getAnuladoEn(), lote.getMotivoAnulacion(),
                totales(json), lista, delLote,
                hallazgos.stream().filter(h -> h.nivel() == ArmadorRips.Nivel.RECHAZO).count(),
                hallazgos.stream().filter(h -> h.nivel() == ArmadorRips.Nivel.AVISO).count());
    }

    /** Cuántos usuarios, consultas y procedimientos lleva el JSON, y sus valores. */
    @SuppressWarnings("unchecked")
    static Totales totales(Map<String, Object> json) {
        int usuarios = 0;
        int consultas = 0;
        int procedimientos = 0;
        BigDecimal valor = BigDecimal.ZERO;
        BigDecimal moderadores = BigDecimal.ZERO;
        for (Map<String, Object> u : (List<Map<String, Object>>) json.get("usuarios")) {
            usuarios++;
            Map<String, Object> s = (Map<String, Object>) u.get("servicios");
            for (String lista : List.of("consultas", "procedimientos")) {
                for (Map<String, Object> x : (List<Map<String, Object>>) s.getOrDefault(lista, List.of())) {
                    if (lista.equals("consultas")) {
                        consultas++;
                    } else {
                        procedimientos++;
                    }
                    valor = valor.add(new BigDecimal(x.get("vrServicio").toString()));
                    moderadores = moderadores.add(new BigDecimal(x.get("valorPagoModerador").toString()));
                }
            }
        }
        return new Totales(usuarios, consultas, procedimientos, valor, moderadores);
    }

    private record ServicioVista(String codigo, String descripcion, int cantidad, BigDecimal valor, boolean consulta) {

        String texto() {
            return codigo + " " + descripcion + (cantidad > 1 ? " × " + cantidad : "")
                    + (valor == null ? " · sin precio" : " · $" + String.format(java.util.Locale.forLanguageTag("es-CO"),
                    "%,.0f", valor));
        }
    }

    // --- vistas públicas ---

    public record Archivo(String nombre, byte[] contenido) {
    }

    public record LoteRipsResumen(UUID id, UUID pagadorId, String pagadorNombre, boolean sinFactura, LocalDate desde,
                              LocalDate hasta, String numeroFactura, Lote.Estado estado, int atenciones,
                              Instant creadoEn, Instant generadoEn, Instant anuladoEn) {
    }

    /** Del JSON armado (en borrador) o generado. */
    public record Totales(int usuarios, int consultas, int procedimientos, BigDecimal valorServicios,
                          BigDecimal pagosModeradores) {
    }

    /**
     * @param hallazgos lo del lote completo (prestador, factura, XML); los de cada atención van en ella
     * @param rechazos cuántos impiden generar
     */
    public record LoteRipsVista(UUID id, UUID pagadorId, String pagadorNombre, boolean sinFactura, LocalDate desde,
                            LocalDate hasta, String numeroFactura, Lote.Estado estado, ArmadorRips.DatosXml xml,
                            Instant creadoEn, Instant generadoEn, Instant anuladoEn, String motivoAnulacion,
                            Totales totales, List<AtencionDelLote> atenciones, List<HallazgoRips> hallazgos,
                            long rechazos, long avisos) {
    }

    /** @param servicios "890201 CONSULTA… · $50.000", uno por CUPS */
    public record AtencionDelLote(UUID id, UUID pacienteId, String pacienteNombre, String documento,
                                  java.time.LocalDateTime fecha, String medicoNombre, List<String> servicios,
                                  String conceptoRecaudo, BigDecimal valorPagoModerador, String numeroAutorizacion,
                                  boolean incapacidad, ArmadorRips.Ajustes ajustes, List<HallazgoRips> hallazgos) {
    }

}
