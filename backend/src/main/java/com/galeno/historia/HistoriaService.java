package com.galeno.historia;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.agenda.AgendaPublica;
import com.galeno.agenda.AgendaPublica.CitaResumen;
import com.galeno.formatos.FormatosService;
import com.galeno.formatos.FormatosService.FormatoDiligenciado;
import com.galeno.formatos.FormatosService.RespuestasFormato;
import com.galeno.historia.AccesoHistoria.Accion;
import com.galeno.identidad.IdentidadService;
import com.galeno.modulos.Modulo;
import com.galeno.modulos.ModulosService;
import com.galeno.pacientes.Paciente;
import com.galeno.pacientes.PacienteService;
import com.galeno.shared.pdf.NombreArchivo;
import com.galeno.shared.pdf.PdfRenderer;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Historia clínica del paciente. La ven todos los médicos del consultorio (historia compartida);
 * cada consulta queda en {@code accesos_historia}. Solo el autor ve y edita sus borradores.
 */
@Service
public class HistoriaService {

    /** El formato de incapacidad: al firmar, marca la incapacidad del RIPS. */
    static final String CODIGO_INCAPACIDAD = "general.incapacidad";

    static final long MAX_BYTES = 20L * 1024 * 1024;

    private final AtencionRepository atenciones;
    private final NotaAclaratoriaRepository notas;
    private final DocumentoClinicoRepository documentos;
    private final AccesoHistoriaRepository accesos;
    private final Cie10Repository cie10;
    private final AlmacenDocumentos almacen;
    private final PacienteService pacientes;
    private final IdentidadService identidad;
    private final AgendaPublica agenda;
    private final PreconsultaService preconsultaDe;
    private final MedicamentosDeAtencion medicamentos;
    private final PdfRenderer pdf;
    private final FormatosService formatos;
    private final CupsRepository cups;
    private final ModulosService modulos;
    private final ListaPrecios precios;
    private final ServicioHabilitado servicio;

    HistoriaService(AtencionRepository atenciones, NotaAclaratoriaRepository notas,
                    DocumentoClinicoRepository documentos, AccesoHistoriaRepository accesos, Cie10Repository cie10,
                    AlmacenDocumentos almacen, PacienteService pacientes, IdentidadService identidad,
                    AgendaPublica agenda, PdfRenderer pdf, FormatosService formatos, CupsRepository cups,
                    ModulosService modulos, java.util.Optional<ListaPrecios> precios,
                    java.util.Optional<ServicioHabilitado> servicio, PreconsultaService preconsultaDe,
                    java.util.Optional<MedicamentosDeAtencion> medicamentos) {
        this.medicamentos = medicamentos.orElse(ids -> java.util.Map.of());
        this.preconsultaDe = preconsultaDe;
        this.atenciones = atenciones;
        this.notas = notas;
        this.documentos = documentos;
        this.accesos = accesos;
        this.cie10 = cie10;
        this.almacen = almacen;
        this.pacientes = pacientes;
        this.identidad = identidad;
        this.agenda = agenda;
        this.pdf = pdf;
        this.formatos = formatos;
        this.cups = cups;
        this.modulos = modulos;
        this.precios = precios.orElse((codigos, contexto) -> java.util.Map.of());
        this.servicio = servicio.orElse(java.util.Optional::empty);
    }

    // --- línea de tiempo ---

    @Transactional
    public List<EventoHistoria> linea(UUID pacienteId) {
        pacientes.get(pacienteId);
        AuthUser yo = AuthUser.current();
        List<EventoHistoria> eventos = new ArrayList<>();
        atenciones.visibles(pacienteId, yo.id()).forEach(a -> eventos.add(EventoHistoria.de(a)));
        documentos.findByPacienteIdOrderByCreadoEnDesc(pacienteId).forEach(d -> eventos.add(EventoHistoria.de(d)));
        eventos.sort(Comparator.comparing(EventoHistoria::fecha).reversed());
        auditar(pacienteId, Accion.VER_HISTORIA, null);
        return eventos;
    }

    // --- atenciones ---

    /**
     * Las citas abiertas de hoy del paciente con quien consulta. Antes de una "Nueva atención": si tiene cita, la
     * atención debe ligarse a ella (si no, la cita quedaría abierta y podría terminar como "No asistió").
     */
    @Transactional(readOnly = true)
    public List<CitaResumen> citasDeHoy(UUID pacienteId) {
        pacientes.get(pacienteId);
        AuthUser yo = AuthUser.current();
        java.time.ZoneId zona = identidad.zonaHoraria(yo.tenantId());
        return agenda.abiertasDelDia(pacienteId, yo.membresiaId(), java.time.LocalDate.now(zona), zona);
    }

    /**
     * Crea un borrador. Si viene de una cita y ya tiene atención, devuelve esa (idempotente).
     * @param registrarEnAgenda sin cita: registra en la agenda del médico una cita de ahora (sobrecupo) y la liga
     */
    @Transactional
    public AtencionVista crear(UUID pacienteId, UUID citaId, boolean registrarEnAgenda) {
        Paciente paciente = pacientes.get(pacienteId);
        AuthUser yo = AuthUser.current();
        if (citaId == null && registrarEnAgenda) {
            citaId = agenda.registrarCitaSinCita(pacienteId, yo.membresiaId(), yo.id());
        }
        if (citaId != null) {
            var existente = atenciones.findByCitaId(citaId);
            if (existente.isPresent()) {
                return obtener(existente.get().getId());
            }
            CitaResumen cita = agenda.cita(citaId).orElseThrow(() -> ApiProblem.notFound("Cita no encontrada"));
            if (!cita.pacienteId().equals(pacienteId) || !cita.medicoMembresiaId().equals(yo.membresiaId())) {
                throw ApiProblem.forbidden("Solo el médico de la cita puede atenderla");
            }
            if (cita.estado() != com.galeno.agenda.Cita.Estado.PROGRAMADA
                    && cita.estado() != com.galeno.agenda.Cita.Estado.CONFIRMADA) {
                throw ApiProblem.conflict("La cita ya no está abierta");
            }
        }
        // La atención arranca con las alergias y los antecedentes del paciente (el médico los ajusta a esta consulta)
        var clinico = pacientes.resumenClinico(pacienteId);
        String antecedentesAtencion = java.util.stream.Stream.of(
                        clinico.alergias() == null ? null : "Alergias: " + clinico.alergias().replace("\n", "; "),
                        clinico.antecedentes())
                .filter(java.util.Objects::nonNull).reduce((a, b) -> a + "\n" + b).orElse(null);
        // Con preconsulta, la atención arranca con su motivo, sus signos vitales y los medicamentos que toma
        var preconsulta = citaId == null ? java.util.Optional.<Preconsulta.Datos>empty() : preconsultaDe.datosDe(citaId);
        String medicamentos = preconsulta.map(Preconsulta.Datos::medicamentosActuales).orElse(null);
        String observaciones = preconsulta.map(Preconsulta.Datos::observaciones).orElse(null);
        antecedentesAtencion = java.util.stream.Stream.of(antecedentesAtencion,
                        medicamentos == null ? null : "Medicamentos actuales: " + medicamentos,
                        observaciones == null ? null : "Preconsulta: " + observaciones)
                .filter(java.util.Objects::nonNull).reduce((a, b) -> a + "\n" + b).orElse(null);
        Atencion nueva = Atencion.nueva(pacienteId, yo.membresiaId(), yo.id(), yo.nombre(), citaId,
                antecedentesAtencion);
        preconsulta.ifPresent(nueva::precargar);
        // Modalidad, grupo y causa del prestador (el médico los cambia si esta atención es distinta)
        servicio.encuentroPorDefecto().ifPresent(e -> nueva.registrarEncuentro(e.modalidadAtencion(), e.grupoServicios(),
                e.causaExterna(), "Institucional"));
        // A un menor lo suele acompañar su responsable: arranca con él y el médico lo cambia si vino otra persona
        if (paciente.edad() < 18 && paciente.getResponsableNombre() != null) {
            nueva.registrarAcompanante(paciente.getResponsableNombre(), paciente.getResponsableParentesco(),
                    paciente.getResponsableTipoDocumento(), paciente.getResponsableNumeroDocumento());
        }
        // Antes de guardarla: así nace con la consulta y con la versión que recibe la pantalla
        if (modulos.activo(Modulo.RIPS)) {
            consultaSugerida(pacienteId, citaId, yo.id()).ifPresent(p -> nueva.registrarProcedimientos(List.of(p)));
        }
        Atencion atencion = atenciones.save(nueva);
        formatos.agregarPorDefecto(atencion.getId(), yo.id(), firmadasRecientes(pacienteId));
        auditar(pacienteId, Accion.CREAR_ATENCION, atencion.getId());
        return vista(atencion);
    }

    @Transactional
    public AtencionVista obtener(UUID atencionId) {
        Atencion atencion = visible(atencionId);
        auditar(atencion.getPacienteId(), Accion.VER_ATENCION, atencionId);
        return vista(atencion);
    }

    /**
     * @param respuestasFormatos {@code null}: los formatos no cambian
     * @param procedimientos {@code null}: no cambian (así llega sin el módulo de RIPS)
     * @param incapacidad {@code null}: no cambia (igual)
     */
    @Transactional
    public AtencionVista guardar(UUID atencionId, long version, Atencion.Contenido contenido,
                                 List<RespuestasFormato> respuestasFormatos, List<ProcedimientoNuevo> procedimientos,
                                 Boolean incapacidad, Acompanante acompanante, Encuentro encuentro,
                                 List<OrdenNueva> ordenes, List<FactorNuevo> factores) {
        Atencion atencion = propia(atencionId);
        if (atencion.getVersion() != version) {
            throw ApiProblem.conflict("La atención cambió en otra pestaña o equipo. Recargue para ver la última versión.");
        }
        atencion.actualizar(contenido);
        if (respuestasFormatos != null) {
            formatos.guardar(atencionId, respuestasFormatos);
        }
        if (procedimientos != null) {
            modulos.exigir(Modulo.RIPS);
            atencion.registrarProcedimientos(procedimientos(procedimientos));
        }
        if (incapacidad != null) {
            atencion.marcarIncapacidad(incapacidad);
        }
        if (acompanante != null && (acompanante.nombre() != null || acompanante.parentesco() != null
                || acompanante.tipoDocumento() != null || acompanante.numeroDocumento() != null)) {
            atencion.registrarAcompanante(acompanante.nombre(), acompanante.parentesco(), acompanante.tipoDocumento(),
                    acompanante.numeroDocumento());
        }
        if (ordenes != null) {
            atencion.registrarOrdenes(ordenes(ordenes));
        }
        if (factores != null) {
            atencion.registrarFactoresRiesgo(factores.stream()
                    .map(f -> new FactorRiesgo(f.codigo(), f.observacion())).toList());
        }
        if (encuentro != null) {
            atencion.registrarEncuentro(encuentro.modalidadAtencion(), encuentro.grupoServicios(), encuentro.causaExterna(),
                    encuentro.entorno());
        }
        atenciones.flush(); // incrementa la versión antes de responder
        return vista(atencion);
    }

    /** Con CUPS, la descripción sale del catálogo; sin él (interconsulta, estudio fuera del catálogo), la del médico. */
    private List<OrdenMedica> ordenes(List<OrdenNueva> nuevas) {
        java.util.Set<String> codigos = nuevas.stream().map(OrdenNueva::codigoCups)
                .filter(c -> c != null && !c.isBlank()).map(c -> c.strip().toUpperCase()).collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, String> nombres = cups.findAllById(codigos).stream()
                .collect(java.util.stream.Collectors.toMap(Cups::getCodigo, Cups::getDescripcion));
        return nuevas.stream().map(o -> {
            String codigo = o.codigoCups() == null || o.codigoCups().isBlank() ? null : o.codigoCups().strip().toUpperCase();
            if (codigo != null && !nombres.containsKey(codigo)) {
                throw ApiProblem.badRequest("El código " + codigo + " no está en el catálogo CUPS");
            }
            return new OrdenMedica(o.tipo(), codigo, codigo != null ? nombres.get(codigo) : o.descripcion(), o.cantidad(),
                    o.indicaciones(), o.prioridad());
        }).toList();
    }

    public record OrdenNueva(OrdenMedica.Tipo tipo, String codigoCups, String descripcion, int cantidad,
                             String indicaciones, OrdenMedica.Prioridad prioridad) {
    }

    public record FactorNuevo(FactorRiesgo.Codigo codigo, String observacion) {
    }

    public record OrdenVista(OrdenMedica.Tipo tipo, String codigoCups, String descripcion, int cantidad,
                             String indicaciones, OrdenMedica.Prioridad prioridad) {
        static OrdenVista de(OrdenMedica o) {
            return new OrdenVista(o.getTipo(), o.getCodigoCups(), o.getDescripcion(), o.getCantidad(), o.getIndicaciones(),
                    o.getPrioridad());
        }
    }

    public record FactorVista(FactorRiesgo.Codigo codigo, String nombre, String observacion) {
        static FactorVista de(FactorRiesgo f) {
            return new FactorVista(f.getCodigo(), f.getCodigo().nombre(), f.getObservacion());
        }
    }

    /** Lo que el PDF de la atención toma de otros módulos para los bloques del RDA. */
    private AtencionPdf.Extras extras(Atencion a, java.util.Map<UUID, List<String>> meds) {
        java.util.Optional<String> entidad;
        try {
            entidad = servicio.entidadResponsable(a.getId());
        } catch (RuntimeException e) {
            entidad = java.util.Optional.empty(); // sin RIPS o sin cobro: el bloque se omite
        }
        return new AtencionPdf.Extras(entidad.orElse(null),
                conCodigo("modalidad_atencion", a.getModalidadAtencion()),
                conCodigo("grupo_servicios", a.getGrupoServicios()),
                conCodigo("causa_externa", a.getCausaExterna()),
                meds.getOrDefault(a.getId(), List.of()));
    }

    private String conCodigo(String tabla, String codigo) {
        return codigo == null ? null : servicio.nombreReferencia(tabla, codigo).map(n -> codigo + " · " + n).orElse(codigo);
    }

    /** Datos del encuentro de la atención; null en el guardado: no cambian. */
    public record Encuentro(String modalidadAtencion, String grupoServicios, String causaExterna, String entorno) {
    }

    /** Agrega un formato activo del consultorio al borrador. */
    @Transactional
    public AtencionVista agregarFormato(UUID atencionId, UUID formatoId) {
        Atencion atencion = propia(atencionId);
        atencion.registrarCambio();
        formatos.agregar(atencionId, formatoId, firmadasRecientes(atencion.getPacienteId()));
        atenciones.flush();
        return vista(atencion);
    }

    /** Quita un formato del borrador, con lo que tenía diligenciado. */
    @Transactional
    public AtencionVista quitarFormato(UUID atencionId, UUID atencionFormatoId) {
        Atencion atencion = propia(atencionId);
        atencion.registrarCambio();
        formatos.quitar(atencionId, atencionFormatoId);
        atenciones.flush();
        return vista(atencion);
    }

    @Transactional
    public AtencionVista firmar(UUID atencionId) {
        Atencion atencion = propia(atencionId);
        String registro = identidad.registroMedico(atencion.getMedicoUsuarioId())
                .orElseThrow(() -> ApiProblem.conflict("Para firmar debe tener su registro médico en el perfil"));
        if (!atencion.firmada()) {
            if (pacientes.get(atencion.getPacienteId()).edad() < 18 && (atencion.getAcompananteNombre() == null
                    || atencion.getAcompananteParentesco() == null || atencion.getAcompananteTipoDocumento() == null
                    || atencion.getAcompananteNumeroDocumento() == null)) {
                throw ApiProblem.badRequest("Para firmar la atención de un menor de edad, registre quién lo acompaña: "
                        + "nombre, documento y parentesco");
            }
            List<String> faltan = formatos.faltantesParaFirmar(atencionId);
            if (!faltan.isEmpty()) {
                throw ApiProblem.badRequest("Para firmar, complete: " + String.join("; ", faltan));
            }
        }
        // Con el formato de incapacidad, el RIPS lleva "incapacidad: SI" sin marcarlo aparte
        if (!atencion.firmada() && formatos.codigos(atencionId).contains(CODIGO_INCAPACIDAD)) {
            atencion.marcarIncapacidad(true);
        }
        atencion.firmar(registro);
        if (atencion.getCitaId() != null) {
            agenda.marcarAtendida(atencion.getCitaId());
        }
        atenciones.flush();
        auditar(atencion.getPacienteId(), Accion.FIRMAR_ATENCION, atencionId);
        return vista(atencion);
    }

    @Transactional
    public void eliminarBorrador(UUID atencionId) {
        Atencion atencion = propia(atencionId);
        if (atencion.firmada()) {
            throw ApiProblem.conflict("Una atención firmada no se elimina");
        }
        // Los documentos clínicos no se borran (ni en S3 ni aquí): con documentos vigentes, el borrador tampoco. Los
        // anulados se desligan y quedan en la historia del paciente.
        if (documentos.existsByAtencionIdAndAnuladoEnIsNull(atencionId)) {
            throw ApiProblem.conflict("Este borrador tiene documentos adjuntos y no se puede eliminar. Anule los documentos "
                    + "subidos por error, o firme la atención.");
        }
        documentos.findByAtencionId(atencionId).forEach(DocumentoClinico::desligarDeAtencion);
        documentos.flush();
        formatos.eliminarDeAtencion(atencionId);
        if (atencion.getCitaId() != null) {
            agenda.cancelarCitaSinCita(atencion.getCitaId());
        }
        atenciones.delete(atencion);
    }

    @Transactional
    public AtencionVista agregarNota(UUID atencionId, String texto) {
        Atencion atencion = visible(atencionId);
        if (!atencion.firmada()) {
            throw ApiProblem.conflict("Las notas aclaratorias son para atenciones firmadas; edite el borrador");
        }
        AuthUser yo = AuthUser.current();
        notas.save(new NotaAclaratoria(atencionId, yo.id(), yo.nombre(), texto.strip()));
        auditar(atencion.getPacienteId(), Accion.NOTA_ACLARATORIA, atencionId);
        return vista(atencion);
    }

    /**
     * PDF propio de un formato de la atención (incapacidad, remisión, consentimiento…), para entregar al paciente. Con
     * marca de agua si la atención es un borrador. Queda auditado como impresión de la atención.
     */
    @Transactional
    public Archivo pdfFormato(UUID atencionId, UUID atencionFormatoId) {
        Atencion a = visible(atencionId);
        var doc = formatos.documento(atencionId, atencionFormatoId)
                .orElseThrow(() -> ApiProblem.notFound("Este formato no tiene un documento para imprimir"));
        Paciente p = pacientes.get(a.getPacienteId());
        var consultorio = identidad.consultorio(AuthUser.current().tenantId());
        auditar(a.getPacienteId(), Accion.IMPRIMIR_ATENCION, atencionId);
        byte[] contenido = pdf.render(FormatoPdf.html(a, p, consultorio, doc));
        String tipo = java.text.Normalizer.normalize(doc.nombre(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return new Archivo(NombreArchivo.pdf(tipo, p.getApellidos(), p.getNombres(), a.getFechaAtencion(), consultorio.zona()),
                "application/pdf", contenido);
    }

    /** Órdenes médicas de la atención en un PDF para el paciente (con marca de agua si es un borrador). Queda auditado. */
    @Transactional
    public Archivo pdfOrdenes(UUID atencionId) {
        Atencion a = visible(atencionId);
        if (a.getOrdenes().isEmpty()) {
            throw ApiProblem.notFound("Esta atención no tiene órdenes médicas");
        }
        Paciente p = pacientes.get(a.getPacienteId());
        var consultorio = identidad.consultorio(AuthUser.current().tenantId());
        auditar(a.getPacienteId(), Accion.IMPRIMIR_ATENCION, atencionId);
        StringBuilder t = new StringBuilder("<table class=\"grilla\"><tr><th>Tipo</th><th>CUPS</th><th>Se ordena</th><th>Cant.</th><th>Indicaciones</th></tr>");
        for (OrdenMedica o : a.getOrdenes()) {
            t.append("<tr><td>").append(PdfRenderer.esc(AtencionPdf.tipoOrden(o.getTipo())))
                    .append(o.getPrioridad() == OrdenMedica.Prioridad.URGENTE ? "<br /><strong>URGENTE</strong>" : "")
                    .append("</td><td>").append(o.getCodigoCups() == null ? "" : PdfRenderer.esc(o.getCodigoCups()))
                    .append("</td><td>").append(PdfRenderer.esc(o.getDescripcion())).append("</td><td>").append(o.getCantidad())
                    .append("</td><td>").append(o.getIndicaciones() == null ? "" : PdfRenderer.esc(o.getIndicaciones()))
                    .append("</td></tr>");
        }
        t.append("</table>");
        byte[] contenido = pdf.render(FormatoPdf.html(a, p, consultorio,
                new com.galeno.formatos.FormatosService.DocumentoFormato("Órdenes médicas", "ÓRDENES MÉDICAS", true, t.toString())));
        return new Archivo(NombreArchivo.pdf("ordenes-medicas", p.getApellidos(), p.getNombres(), a.getFechaAtencion(),
                consultorio.zona()), "application/pdf", contenido);
    }

    /** PDF de la atención (con marca de agua si es un borrador). Queda auditado. */
    @Transactional
    public Archivo pdfAtencion(UUID atencionId) {
        Atencion a = visible(atencionId);
        Paciente p = pacientes.get(a.getPacienteId());
        var consultorio = identidad.consultorio(AuthUser.current().tenantId());
        auditar(a.getPacienteId(), Accion.IMPRIMIR_ATENCION, atencionId);
        byte[] contenido = pdf.render(AtencionPdf.html(a, p, consultorio, notas.findByAtencionIdOrderByCreadaEn(atencionId),
                formatos.htmlPorAtencion(List.of(atencionId)).getOrDefault(atencionId, ""),
                extras(a, medicamentos.lineas(List.of(atencionId)))));
        return new Archivo(NombreArchivo.pdf("atencion", p.getApellidos(), p.getNombres(), a.getFechaAtencion(), consultorio.zona()),
                "application/pdf", contenido);
    }

    /**
     * Historia clínica completa del paciente en un solo PDF: todas las atenciones firmadas (o importadas) en orden,
     * con sus notas aclaratorias, y la lista de documentos adjuntos. Los borradores no salen: no son registro clínico.
     */
    @Transactional
    public Archivo pdfHistoria(UUID pacienteId) {
        Paciente p = pacientes.get(pacienteId);
        AuthUser yo = AuthUser.current();
        var consultorio = identidad.consultorio(yo.tenantId());
        List<Atencion> firmadas = atenciones.firmadas(pacienteId);
        var formatosHtml = formatos.htmlPorAtencion(firmadas.stream().map(Atencion::getId).toList());
        var meds = medicamentos.lineas(firmadas.stream().map(Atencion::getId).toList());
        List<HistoriaPdf.AtencionImpresa> lista = firmadas.stream()
                .map(a -> new HistoriaPdf.AtencionImpresa(a, notas.findByAtencionIdOrderByCreadaEn(a.getId()),
                        formatosHtml.getOrDefault(a.getId(), ""), extras(a, meds)))
                .toList();
        auditar(pacienteId, Accion.IMPRIMIR_HISTORIA, null);
        byte[] contenido = pdf.render(HistoriaPdf.html(p, pacientes.resumenClinico(pacienteId), consultorio, lista,
                documentos.findByPacienteIdOrderByCreadoEnDesc(pacienteId), yo.nombre()));
        return new Archivo(NombreArchivo.pdf("historia-clinica", p.getApellidos(), p.getNombres(), Instant.now(),
                consultorio.zona()), "application/pdf", contenido);
    }

    /** Para la fórmula impresa: el diagnóstico principal de la atención en la que se emitió. */
    @Transactional(readOnly = true)
    public Optional<String> diagnosticoPrincipal(UUID atencionId) {
        return atenciones.findById(atencionId).stream()
                .flatMap(a -> a.getDiagnosticos().stream())
                .filter(Diagnostico::isPrincipal)
                .map(d -> d.getCodigoCie10() + " " + d.getDescripcion())
                .findFirst();
    }

    // --- procedimientos (CUPS) ---

    /** Con la descripción del catálogo: un código que no está en CUPS se rechaza. */
    private List<Procedimiento> procedimientos(List<ProcedimientoNuevo> nuevos) {
        java.util.Map<String, String> nombres = nombresCups(nuevos.stream()
                .map(p -> p.codigoCups() == null ? "" : p.codigoCups().strip().toUpperCase()).toList());
        return nuevos.stream().map(p -> {
            String codigo = p.codigoCups() == null ? "" : p.codigoCups().strip().toUpperCase();
            String nombre = nombres.get(codigo);
            if (nombre == null) {
                throw ApiProblem.badRequest("El código " + p.codigoCups() + " no está en el catálogo CUPS");
            }
            return new Procedimiento(codigo, nombre, p.cantidad(), p.valorUnitario(), p.diente(), p.superficies())
                    .conResultado(p.resultado());
        }).toList();
    }

    /**
     * La consulta del día: de primera vez si el paciente no tiene atenciones firmadas en el consultorio, si no de
     * control; por medicina general, odontología u otras especialidades según el perfil de quien atiende.
     */
    java.util.Optional<Procedimiento> consultaSugerida(UUID pacienteId, UUID citaId, UUID medicoUsuarioId) {
        // El tipo de la cita manda: su CUPS, o primera vez / control según su clase; sin tipo (u OTRA), el historial
        var tipo = citaId == null ? java.util.Optional.<com.galeno.agenda.AgendaPublica.TipoDeCita>empty()
                : agenda.tipoDeCita(citaId);
        var clase = tipo.map(com.galeno.agenda.AgendaPublica.TipoDeCita::clase).orElse(null);
        boolean primeraVez = clase == com.galeno.agenda.TipoCita.Clase.PRIMERA_VEZ
                || (clase != com.galeno.agenda.TipoCita.Clase.CONTROL && atenciones.firmadas(pacienteId).isEmpty());
        String especialidad = identidad.profesional(medicoUsuarioId).especialidad();
        String codigo = tipo.map(com.galeno.agenda.AgendaPublica.TipoDeCita::cups)
                .orElse((primeraVez ? "8902" : "8903") + sufijoConsulta(servicio.codigo().orElse(null), especialidad));
        return cups.findById(codigo).map(c -> new Procedimiento(c.getCodigo(), c.getDescripcion(), 1,
                precios.precios(List.of(c.getCodigo()), new ListaPrecios.Contexto(pacienteId, citaId, null)).get(c.getCodigo()),
                null, null));
    }

    /**
     * Manda el servicio habilitado del consultorio (lo que se reporta en el RIPS): 328 medicina general → 01, 334
     * odontología general → 03. Sin servicio configurado, o con otro, decide la especialidad de quien atiende.
     */
    static String sufijoConsulta(String servicioHabilitado, String especialidad) {
        if ("328".equals(servicioHabilitado)) {
            return "01";
        }
        if ("334".equals(servicioHabilitado)) {
            return "03";
        }
        return sufijoConsulta(especialidad);
    }

    /** 01 medicina general, 03 odontología general, 02 otras especialidades médicas (sufijos CUPS 8902xx / 8903xx). */
    static String sufijoConsulta(String especialidad) {
        String e = especialidad == null ? "" : java.text.Normalizer.normalize(especialidad, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        if (e.contains("odont")) {
            return "03";
        }
        if (e.isBlank() || e.contains("general")) {
            return "01";
        }
        return "02";
    }

    /**
     * Buscador de CUPS, con el precio del consultorio si lo tiene. Solo con el módulo de RIPS.
     *
     * @param atencionId desde una atención: el precio es la tarifa de su pagador, si la tiene; nulo, la lista general
     */
    @Transactional(readOnly = true)
    public List<CupsVista> buscarCups(String q, UUID atencionId) {
        modulos.exigir(Modulo.RIPS);
        String texto = q == null ? "" : q.strip();
        if (texto.length() < 2) {
            return List.of();
        }
        List<Cups> encontrados = cups.buscar(texto, PageRequest.of(0, 20));
        ListaPrecios.Contexto contexto = atencionId == null ? null : atenciones.findById(atencionId)
                .map(a -> new ListaPrecios.Contexto(a.getPacienteId(), a.getCitaId(), a.getId())).orElse(null);
        java.util.Map<String, java.math.BigDecimal> conPrecio = precios.precios(
                encontrados.stream().map(Cups::getCodigo).toList(), contexto);
        return encontrados.stream().map(c -> new CupsVista(c.getCodigo(), c.getDescripcion(), conPrecio.get(c.getCodigo()))).toList();
    }

    /**
     * Para otros módulos (RIPS): de quién es la atención y de qué cita viene, sin su contenido clínico (por eso no queda
     * en la auditoría de accesos).
     */
    @Transactional(readOnly = true)
    public Optional<AtencionReferencia> referencia(UUID atencionId) {
        return atenciones.findById(atencionId).map(a -> new AtencionReferencia(a.getId(), a.getPacienteId(), a.getCitaId(),
                a.getMedicoUsuarioId(), a.getEstado() == Atencion.Estado.FIRMADA));
    }

    public record AtencionReferencia(UUID id, UUID pacienteId, UUID citaId, UUID medicoUsuarioId, boolean firmada) {
    }

    /**
     * Para el lote del RIPS: las atenciones firmadas en Galenum entre esas fechas ({@code hasta} excluida). Llevan
     * diagnósticos: quien arma el lote registra el acceso con {@link #registrarAcceso}.
     */
    @Transactional(readOnly = true)
    public List<AtencionRips> atencionesRips(Instant desde, Instant hasta) {
        return atenciones.firmadasEntre(desde, hasta).stream().map(AtencionRips::de).toList();
    }

    /** Las mismas, por id (solo las firmadas en Galenum). */
    @Transactional(readOnly = true)
    public List<AtencionRips> atencionesRips(java.util.Collection<UUID> ids) {
        return atenciones.findAllById(ids).stream()
                .filter(a -> a.firmada() && a.getOrigen() == Atencion.Origen.GALENO)
                .sorted(Comparator.comparing(Atencion::getFechaAtencion)).map(AtencionRips::de).toList();
    }

    /** Sexo y edades de cada código CIE-10 conocido (para la regla RVC028 del RIPS). */
    @Transactional(readOnly = true)
    public java.util.Map<String, Cie10Vista> cie10(java.util.Collection<String> codigos) {
        return cie10.findAllById(codigos).stream().collect(java.util.stream.Collectors.toMap(Cie10::getCodigo,
                c -> new Cie10Vista(c.getCodigo(), c.getDescripcion(), c.getSexo(), c.getEdadMin(), c.getEdadMax(),
                        c.getCapitulo())));
    }

    /** Lo que el RIPS toma de una atención firmada. */
    public record AtencionRips(UUID id, UUID pacienteId, UUID citaId, UUID medicoUsuarioId, String medicoNombre,
                               Instant fecha, boolean incapacidad, List<DiagnosticoVista> diagnosticos,
                               List<ProcedimientoVista> procedimientos, String modalidadAtencion, String grupoServicios,
                               String causaExterna) {

        static AtencionRips de(Atencion a) {
            return new AtencionRips(a.getId(), a.getPacienteId(), a.getCitaId(), a.getMedicoUsuarioId(),
                    a.getMedicoNombre(), a.getFechaAtencion(), a.isIncapacidad(),
                    a.getDiagnosticos().stream().map(d -> new DiagnosticoVista(d.getCodigoCie10(), d.getDescripcion(),
                            d.isPrincipal(), d.getTipo())).toList(),
                    a.getProcedimientos().stream().map(ProcedimientoVista::de).toList(), a.getModalidadAtencion(),
                    a.getGrupoServicios(), a.getCausaExterna());
        }
    }

    /** Descripción CUPS de cada código conocido (los desconocidos no aparecen en el mapa). */
    @Transactional(readOnly = true)
    public java.util.Map<String, String> nombresCups(java.util.Collection<String> codigos) {
        return cups.findAllById(codigos).stream()
                .collect(java.util.stream.Collectors.toMap(Cups::getCodigo, Cups::getDescripcion));
    }

    // --- importación (la usa el módulo de importación) ---

    /** Crea una atención importada si esa clave no se había importado antes. Devuelve si la creó. */
    @Transactional
    public boolean importarAtencion(UUID pacienteId, java.time.Instant fecha, String descripcion, String profesional,
                                    List<Diagnostico> diagnosticos, String clave, UUID loteId) {
        if (atenciones.existsByClaveImportacion(clave)) {
            return false;
        }
        atenciones.save(Atencion.importada(pacienteId, fecha, descripcion, profesional, diagnosticos, clave, loteId));
        return true;
    }

    /** De esas claves de importación, las que ya tienen una atención (en consultas de 1.000 en 1.000). */
    @Transactional(readOnly = true)
    public java.util.Set<String> clavesImportadas(java.util.Collection<String> claves) {
        List<String> lista = List.copyOf(new java.util.LinkedHashSet<>(claves));
        java.util.Set<String> existentes = new java.util.HashSet<>();
        for (int i = 0; i < lista.size(); i += 1000) {
            existentes.addAll(atenciones.clavesImportadas(lista.subList(i, Math.min(i + 1000, lista.size()))));
        }
        return existentes;
    }

    /**
     * Crea la atención importada sin consultar antes si la clave existe: quien llama ya lo verificó con
     * {@link #clavesImportadas} (para importar miles sin una consulta por atención). La base igual rechaza un duplicado.
     */
    @Transactional
    public void importarAtencionNueva(UUID pacienteId, java.time.Instant fecha, AtencionImportada contenido,
                                      List<Diagnostico> diagnosticos, String clave, UUID loteId) {
        atenciones.save(Atencion.importada(pacienteId, fecha, contenido, diagnosticos, clave, loteId));
    }

    /** Como {@link #importarAtencion(UUID, java.time.Instant, String, String, List, String, UUID)}, con el contenido clínico. */
    @Transactional
    public boolean importarAtencion(UUID pacienteId, java.time.Instant fecha, AtencionImportada contenido,
                                    List<Diagnostico> diagnosticos, String clave, UUID loteId) {
        if (atenciones.existsByClaveImportacion(clave)) {
            return false;
        }
        atenciones.save(Atencion.importada(pacienteId, fecha, contenido, diagnosticos, clave, loteId));
        return true;
    }

    /** Descripción CIE-10 de cada código conocido (los desconocidos no aparecen en el mapa). */
    @Transactional(readOnly = true)
    public java.util.Map<String, String> nombresCie10(java.util.Collection<String> codigos) {
        return cie10.findAllById(codigos).stream()
                .collect(java.util.stream.Collectors.toMap(Cie10::getCodigo, Cie10::getDescripcion));
    }

    /** Ids de las atenciones creadas por un lote (sin cargarlas). */
    @Transactional(readOnly = true)
    public List<UUID> atencionesDelLote(UUID loteId) {
        return atenciones.idsDelLote(loteId);
    }

    /** ¿Alguien agregó notas aclaratorias a las atenciones del lote? Entonces no se puede deshacer sin perderlas. */
    @Transactional(readOnly = true)
    public long notasEnAtenciones(java.util.Collection<UUID> atencionIds) {
        return atencionIds.isEmpty() ? 0 : notas.countByAtencionIdIn(atencionIds);
    }

    /**
     * Borra en bloque las atenciones de un lote y sus colecciones, sin cargarlas (un lote puede traer miles, cada una con
     * cuatro colecciones). Quien llama ya verificó que no tienen notas ni fórmulas.
     */
    @Transactional
    public int eliminarAtencionesDelLote(UUID loteId) {
        UUID tenant = AuthUser.current().tenantId();
        atenciones.flush();
        atenciones.borrarDiagnosticosDelLote(loteId, tenant);
        atenciones.borrarProcedimientosDelLote(loteId, tenant);
        atenciones.borrarOrdenesDelLote(loteId, tenant);
        atenciones.borrarFactoresDelLote(loteId, tenant);
        return atenciones.borrarDelLote(loteId, tenant);
    }

    /** De esos pacientes, los que tienen atenciones o documentos en Galenum (en consultas de 1.000 en 1.000). */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> pacientesConActividad(java.util.Collection<UUID> pacienteIds) {
        List<UUID> lista = List.copyOf(pacienteIds);
        java.util.Set<UUID> con = new java.util.HashSet<>();
        for (int i = 0; i < lista.size(); i += 1000) {
            List<UUID> parte = lista.subList(i, Math.min(i + 1000, lista.size()));
            con.addAll(atenciones.pacientesConAlguna(parte));
            con.addAll(documentos.pacientesConAlguna(parte));
        }
        return con;
    }

    /** Actividad clínica del paciente en Galenum (para saber si un paciente importado se puede borrar). */
    @Transactional(readOnly = true)
    public long actividadClinica(UUID pacienteId) {
        return atenciones.countByPacienteId(pacienteId) + documentos.countByPacienteId(pacienteId);
    }

    /** Para otros módulos que también deben quedar en la auditoría de la historia. */
    @Transactional
    public void registrarAcceso(UUID pacienteId, Accion accion, UUID recursoId) {
        auditar(pacienteId, accion, recursoId);
    }

    /** Para otros módulos (p. ej. fórmulas): la atención existe, es de este paciente y la puede usar quien consulta. */
    @Transactional(readOnly = true)
    public void verificarAtencionDelPaciente(UUID atencionId, UUID pacienteId) {
        if (!visible(atencionId).getPacienteId().equals(pacienteId)) {
            throw ApiProblem.badRequest("La atención no corresponde a este paciente");
        }
    }

    // --- documentos ---

    @Transactional
    public DocumentoVista subirDocumento(UUID pacienteId, UUID atencionId, DocumentoClinico.Tipo tipo,
                                         String descripcion, String nombreArchivo, byte[] contenido) {
        pacientes.get(pacienteId);
        if (atencionId != null) {
            verificarAtencionDelPaciente(atencionId, pacienteId);
        }
        if (contenido.length == 0 || contenido.length > MAX_BYTES) {
            throw ApiProblem.badRequest("El archivo debe pesar entre 1 byte y 20 MB");
        }
        String contentType = tipoContenido(contenido);
        String nombre = nombreSeguro(nombreArchivo);
        String clave = AuthUser.current().tenantId() + "/" + pacienteId + "/" + UUID.randomUUID();
        almacen.guardar(clave, contenido);
        DocumentoClinico doc = documentos.save(new DocumentoClinico(pacienteId, atencionId, tipo,
                descripcion == null || descripcion.isBlank() ? null : descripcion.strip(), nombre, contentType,
                contenido.length, sha256(contenido), clave, AuthUser.current().id()));
        auditar(pacienteId, Accion.SUBIR_DOCUMENTO, doc.getId());
        return DocumentoVista.de(doc);
    }

    @Transactional
    public DocumentoVista anularDocumento(UUID documentoId, String motivo) {
        DocumentoClinico doc = documentos.findById(documentoId)
                .orElseThrow(() -> ApiProblem.notFound("Documento no encontrado"));
        doc.anular(AuthUser.current().id(), motivo);
        auditar(doc.getPacienteId(), Accion.ANULAR_DOCUMENTO, documentoId);
        return DocumentoVista.de(doc);
    }

    @Transactional
    public Archivo descargar(UUID documentoId) {
        DocumentoClinico doc = documentos.findById(documentoId)
                .orElseThrow(() -> ApiProblem.notFound("Documento no encontrado"));
        auditar(doc.getPacienteId(), Accion.DESCARGAR_DOCUMENTO, documentoId);
        return new Archivo(doc.getNombreArchivo(), doc.getContentType(), almacen.leer(doc.getStorageKey()));
    }

    // --- CIE-10 ---

    @Transactional(readOnly = true)
    public List<Cie10Vista> buscarCie10(String q) {
        String texto = q == null ? "" : q.strip().replace(".", "");
        if (texto.length() < 2) {
            return List.of();
        }
        return cie10.buscar(texto, PageRequest.of(0, 20)).stream()
                .map(c -> new Cie10Vista(c.getCodigo(), c.getDescripcion(), c.getSexo(), c.getEdadMin(), c.getEdadMax(),
                        c.getCapitulo())).toList();
    }

    // --- reglas ---

    /** Firmadas: cualquier médico del consultorio. Borradores: solo su autor. */
    private Atencion visible(UUID atencionId) {
        Atencion a = atenciones.findById(atencionId).orElseThrow(() -> ApiProblem.notFound("Atención no encontrada"));
        if (!a.firmada() && !java.util.Objects.equals(a.getMedicoUsuarioId(), AuthUser.current().id())) {
            throw ApiProblem.notFound("Atención no encontrada");
        }
        return a;
    }

    private Atencion propia(UUID atencionId) {
        Atencion a = visible(atencionId);
        if (!java.util.Objects.equals(a.getMedicoUsuarioId(), AuthUser.current().id())) {
            throw ApiProblem.forbidden("Solo el médico que la registró puede modificarla");
        }
        return a;
    }

    /** Atenciones firmadas del paciente, de la más reciente a la más antigua (de ahí se arrastra el odontograma). */
    private List<UUID> firmadasRecientes(UUID pacienteId) {
        List<UUID> ids = new ArrayList<>(atenciones.firmadas(pacienteId).stream().map(Atencion::getId).toList());
        java.util.Collections.reverse(ids);
        return ids;
    }

    private void auditar(UUID pacienteId, Accion accion, UUID recursoId) {
        accesos.save(new AccesoHistoria(pacienteId, AuthUser.current().id(), accion, recursoId));
    }

    private AtencionVista vista(Atencion a) {
        List<NotaVista> notasVista = notas.findByAtencionIdOrderByCreadaEn(a.getId()).stream()
                .map(n -> new NotaVista(n.getId(), n.getAutorNombre(), n.getTexto(), n.getCreadaEn())).toList();
        List<DocumentoVista> docs = documentos.findByPacienteIdOrderByCreadoEnDesc(a.getPacienteId()).stream()
                .filter(d -> a.getId().equals(d.getAtencionId())).map(DocumentoVista::de).toList();
        return AtencionVista.de(a, java.util.Objects.equals(a.getMedicoUsuarioId(), AuthUser.current().id()), notasVista, docs,
                formatos.deAtencion(a.getId()));
    }

    /** El tipo se decide por el contenido (firma del archivo), no por lo que diga el navegador. */
    static String tipoContenido(byte[] b) {
        if (b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        throw ApiProblem.badRequest("Solo se aceptan archivos PDF, PNG o JPG");
    }

    static String nombreSeguro(String nombre) {
        String limpio = nombre == null ? "" : nombre.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (limpio.isEmpty()) {
            limpio = "documento";
        }
        return limpio.length() > 200 ? limpio.substring(limpio.length() - 200) : limpio;
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- vistas ---

    public record Archivo(String nombre, String contentType, byte[] contenido) {
    }

    /**
     * @param sexo A (ambos), F o M: el diagnóstico solo aplica a ese sexo (lo valida RIPS)
     * @param edadMin edad mínima en años; {@code edadMax} 999 = sin límite
     */
    public record Cie10Vista(String codigo, String descripcion, String sexo, Integer edadMin, Integer edadMax,
                             String capitulo) {
    }

    public record NotaVista(UUID id, String autorNombre, String texto, Instant creadaEn) {
    }

    /** @param valorUnitario el precio del consultorio para ese CUPS, si lo tiene */
    public record CupsVista(String codigo, String descripcion, java.math.BigDecimal valorUnitario) {
    }

    /** Lo que envía la pantalla; la descripción sale del catálogo. */
    public record ProcedimientoNuevo(String codigoCups, int cantidad, java.math.BigDecimal valorUnitario, String diente,
                                     String superficies, String resultado) {
    }

    /** @param consulta va en "consultas" del RIPS (CUPS 890…); si no, en "procedimientos" */
    public record ProcedimientoVista(String codigoCups, String descripcion, int cantidad,
                                     java.math.BigDecimal valorUnitario, String diente, String superficies,
                                     boolean consulta, String resultado) {

        static ProcedimientoVista de(Procedimiento p) {
            return new ProcedimientoVista(p.getCodigoCups(), p.getDescripcion(), p.getCantidad(), p.getValorUnitario(),
                    p.getDiente(), p.getSuperficies(), p.esConsulta(), p.getResultado());
        }
    }

    public record DiagnosticoVista(String codigoCie10, String descripcion, boolean principal, Diagnostico.Tipo tipo) {
    }

    /** @param anuladoEn si se subió por error (sigue en la historia, con {@code motivoAnulacion}) */
    public record DocumentoVista(UUID id, UUID atencionId, DocumentoClinico.Tipo tipo, String descripcion,
                                 String nombreArchivo, String contentType, long tamanoBytes, Instant creadoEn,
                                 Instant anuladoEn, String motivoAnulacion) {

        static DocumentoVista de(DocumentoClinico d) {
            return new DocumentoVista(d.getId(), d.getAtencionId(), d.getTipo(), d.getDescripcion(),
                    d.getNombreArchivo(), d.getContentType(), d.getTamanoBytes(), d.getCreadoEn(), d.getAnuladoEn(),
                    d.getMotivoAnulacion());
        }
    }

    /** @param propia la registró quien consulta (puede editar el borrador) */
    public record AtencionVista(UUID id, UUID pacienteId, UUID citaId, String medicoNombre, String registroMedico,
                                Atencion.Origen origen, Atencion.Estado estado, Instant fechaAtencion,
                                Instant firmadaEn, long version, boolean propia, String motivoConsulta,
                                String enfermedadActual, String antecedentes, String revisionSistemas,
                                Integer taSistolica, Integer taDiastolica, Integer frecuenciaCardiaca,
                                Integer frecuenciaRespiratoria, java.math.BigDecimal temperatura,
                                Integer saturacionO2, java.math.BigDecimal pesoKg, java.math.BigDecimal tallaCm,
                                String examenFisico, String analisis, String plan,
                                List<DiagnosticoVista> diagnosticos, List<ProcedimientoVista> procedimientos,
                                boolean incapacidad, List<NotaVista> notas, List<DocumentoVista> documentos,
                                List<FormatoDiligenciado> formatos, String acompananteNombre,
                                String acompananteParentesco, String acompananteTipoDocumento,
                                String acompananteNumeroDocumento, Instant finalizadaEn, String modalidadAtencion,
                                String grupoServicios, String causaExterna, String entorno, List<OrdenVista> ordenes,
                                List<FactorVista> factoresRiesgo) {

        static AtencionVista de(Atencion a, boolean propia, List<NotaVista> notas, List<DocumentoVista> documentos,
                                List<FormatoDiligenciado> formatos) {
            return new AtencionVista(a.getId(), a.getPacienteId(), a.getCitaId(), a.getMedicoNombre(),
                    a.getRegistroMedico(), a.getOrigen(), a.getEstado(), a.getFechaAtencion(), a.getFirmadaEn(),
                    a.getVersion(), propia, a.getMotivoConsulta(), a.getEnfermedadActual(), a.getAntecedentes(),
                    a.getRevisionSistemas(), a.getTaSistolica(), a.getTaDiastolica(), a.getFrecuenciaCardiaca(),
                    a.getFrecuenciaRespiratoria(), a.getTemperatura(), a.getSaturacionO2(), a.getPesoKg(),
                    a.getTallaCm(), a.getExamenFisico(), a.getAnalisis(), a.getPlan(),
                    a.getDiagnosticos().stream().map(d -> new DiagnosticoVista(d.getCodigoCie10(), d.getDescripcion(),
                            d.isPrincipal(), d.getTipo())).toList(),
                    a.getProcedimientos().stream().map(ProcedimientoVista::de).toList(), a.isIncapacidad(), notas,
                    documentos, formatos, a.getAcompananteNombre(), a.getAcompananteParentesco(),
                    a.getAcompananteTipoDocumento(), a.getAcompananteNumeroDocumento(), a.getFinalizadaEn(),
                    a.getModalidadAtencion(), a.getGrupoServicios(), a.getCausaExterna(), a.getEntorno(),
                    a.getOrdenes().stream().map(OrdenVista::de).toList(),
                    a.getFactoresRiesgo().stream().map(FactorVista::de).toList());
        }
    }

    /** Quién acompaña al paciente en la atención; todos nulos: no cambia. */
    public record Acompanante(String nombre, String parentesco, String tipoDocumento, String numeroDocumento) {
    }

    public enum TipoEvento {
        ATENCION,
        DOCUMENTO
    }

    /** @param anulacion motivo, si es un documento anulado */
    public record EventoHistoria(TipoEvento tipo, UUID id, Instant fecha, String titulo, String detalle,
                                 String estado, String autor, String anulacion) {

        static EventoHistoria de(Atencion a) {
            String dx = a.getDiagnosticos().stream().filter(Diagnostico::isPrincipal).findFirst()
                    .map(d -> d.getCodigoCie10() + " " + d.getDescripcion()).orElse(null);
            return new EventoHistoria(TipoEvento.ATENCION, a.getId(), a.getFechaAtencion(),
                    a.getMotivoConsulta() == null || a.getMotivoConsulta().isBlank() ? "Atención sin motivo registrado"
                            : a.getMotivoConsulta(), dx,
                    a.getOrigen() == Atencion.Origen.IMPORTADA ? "IMPORTADA" : a.getEstado().name(), a.getMedicoNombre(),
                    null);
        }

        static EventoHistoria de(DocumentoClinico d) {
            return new EventoHistoria(TipoEvento.DOCUMENTO, d.getId(), d.getCreadoEn(),
                    d.getDescripcion() != null ? d.getDescripcion() : d.getNombreArchivo(), d.getNombreArchivo(),
                    d.getTipo().name(), null, d.getMotivoAnulacion());
        }
    }
}
