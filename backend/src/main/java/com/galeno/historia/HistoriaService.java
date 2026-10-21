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
import com.galeno.historia.AccesoHistoria.Accion;
import com.galeno.identidad.IdentidadService;
import com.galeno.pacientes.Paciente;
import com.galeno.pacientes.PacienteService;
import com.galeno.shared.pdf.PdfRenderer;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Historia clínica del paciente. La ven todos los médicos del consultorio (historia compartida);
 * cada consulta queda en {@code accesos_historia}. Solo el autor ve y edita sus borradores.
 */
@Service
public class HistoriaService {

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
    private final PdfRenderer pdf;

    HistoriaService(AtencionRepository atenciones, NotaAclaratoriaRepository notas,
                    DocumentoClinicoRepository documentos, AccesoHistoriaRepository accesos, Cie10Repository cie10,
                    AlmacenDocumentos almacen, PacienteService pacientes, IdentidadService identidad,
                    AgendaPublica agenda, PdfRenderer pdf) {
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

    /** Crea un borrador. Si viene de una cita y ya tiene atención, devuelve esa (idempotente). */
    @Transactional
    public AtencionVista crear(UUID pacienteId, UUID citaId) {
        Paciente paciente = pacientes.get(pacienteId);
        AuthUser yo = AuthUser.current();
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
        Atencion atencion = atenciones.save(Atencion.nueva(pacienteId, yo.membresiaId(), yo.id(), yo.nombre(), citaId,
                paciente.getAntecedentes()));
        auditar(pacienteId, Accion.CREAR_ATENCION, atencion.getId());
        return vista(atencion);
    }

    @Transactional
    public AtencionVista obtener(UUID atencionId) {
        Atencion atencion = visible(atencionId);
        auditar(atencion.getPacienteId(), Accion.VER_ATENCION, atencionId);
        return vista(atencion);
    }

    @Transactional
    public AtencionVista guardar(UUID atencionId, long version, Atencion.Contenido contenido) {
        Atencion atencion = propia(atencionId);
        if (atencion.getVersion() != version) {
            throw ApiProblem.conflict("La atención cambió en otra pestaña o equipo. Recargue para ver la última versión.");
        }
        atencion.actualizar(contenido);
        atenciones.flush(); // incrementa la versión antes de responder
        return vista(atencion);
    }

    @Transactional
    public AtencionVista firmar(UUID atencionId) {
        Atencion atencion = propia(atencionId);
        String registro = identidad.registroMedico(atencion.getMedicoUsuarioId())
                .orElseThrow(() -> ApiProblem.conflict("Para firmar debe tener su registro médico en el perfil"));
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

    /** PDF de la atención (con marca de agua si es un borrador). Queda auditado. */
    @Transactional
    public byte[] pdfAtencion(UUID atencionId) {
        Atencion a = visible(atencionId);
        Paciente p = pacientes.get(a.getPacienteId());
        var consultorio = identidad.consultorio(AuthUser.current().tenantId());
        auditar(a.getPacienteId(), Accion.IMPRIMIR_ATENCION, atencionId);
        return pdf.render(AtencionPdf.html(a, p, consultorio, notas.findByAtencionIdOrderByCreadaEn(atencionId)));
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

    /** Descripción CIE-10 de cada código conocido (los desconocidos no aparecen en el mapa). */
    @Transactional(readOnly = true)
    public java.util.Map<String, String> nombresCie10(java.util.Collection<String> codigos) {
        return cie10.findAllById(codigos).stream()
                .collect(java.util.stream.Collectors.toMap(Cie10::getCodigo, Cie10::getDescripcion));
    }

    /** Ids de las atenciones creadas por un lote. */
    @Transactional(readOnly = true)
    public List<UUID> atencionesDelLote(UUID loteId) {
        return atenciones.findByLoteImportacionId(loteId).stream().map(Atencion::getId).toList();
    }

    /** ¿Alguien agregó notas aclaratorias a las atenciones del lote? Entonces no se puede deshacer sin perderlas. */
    @Transactional(readOnly = true)
    public long notasEnAtenciones(java.util.Collection<UUID> atencionIds) {
        return atencionIds.isEmpty() ? 0 : notas.countByAtencionIdIn(atencionIds);
    }

    @Transactional
    public int eliminarAtencionesDelLote(UUID loteId) {
        List<Atencion> delLote = atenciones.findByLoteImportacionId(loteId);
        atenciones.deleteAll(delLote);
        return delLote.size();
    }

    /** Actividad clínica del paciente en Galeno (para saber si un paciente importado se puede borrar). */
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

    private void auditar(UUID pacienteId, Accion accion, UUID recursoId) {
        accesos.save(new AccesoHistoria(pacienteId, AuthUser.current().id(), accion, recursoId));
    }

    private AtencionVista vista(Atencion a) {
        List<NotaVista> notasVista = notas.findByAtencionIdOrderByCreadaEn(a.getId()).stream()
                .map(n -> new NotaVista(n.getId(), n.getAutorNombre(), n.getTexto(), n.getCreadaEn())).toList();
        List<DocumentoVista> docs = documentos.findByPacienteIdOrderByCreadoEnDesc(a.getPacienteId()).stream()
                .filter(d -> a.getId().equals(d.getAtencionId())).map(DocumentoVista::de).toList();
        return AtencionVista.de(a, java.util.Objects.equals(a.getMedicoUsuarioId(), AuthUser.current().id()), notasVista, docs);
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

    public record DiagnosticoVista(String codigoCie10, String descripcion, boolean principal, Diagnostico.Tipo tipo) {
    }

    public record DocumentoVista(UUID id, UUID atencionId, DocumentoClinico.Tipo tipo, String descripcion,
                                 String nombreArchivo, String contentType, long tamanoBytes, Instant creadoEn) {

        static DocumentoVista de(DocumentoClinico d) {
            return new DocumentoVista(d.getId(), d.getAtencionId(), d.getTipo(), d.getDescripcion(),
                    d.getNombreArchivo(), d.getContentType(), d.getTamanoBytes(), d.getCreadoEn());
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
                                List<DiagnosticoVista> diagnosticos, List<NotaVista> notas,
                                List<DocumentoVista> documentos) {

        static AtencionVista de(Atencion a, boolean propia, List<NotaVista> notas, List<DocumentoVista> documentos) {
            return new AtencionVista(a.getId(), a.getPacienteId(), a.getCitaId(), a.getMedicoNombre(),
                    a.getRegistroMedico(), a.getOrigen(), a.getEstado(), a.getFechaAtencion(), a.getFirmadaEn(),
                    a.getVersion(), propia, a.getMotivoConsulta(), a.getEnfermedadActual(), a.getAntecedentes(),
                    a.getRevisionSistemas(), a.getTaSistolica(), a.getTaDiastolica(), a.getFrecuenciaCardiaca(),
                    a.getFrecuenciaRespiratoria(), a.getTemperatura(), a.getSaturacionO2(), a.getPesoKg(),
                    a.getTallaCm(), a.getExamenFisico(), a.getAnalisis(), a.getPlan(),
                    a.getDiagnosticos().stream().map(d -> new DiagnosticoVista(d.getCodigoCie10(), d.getDescripcion(),
                            d.isPrincipal(), d.getTipo())).toList(), notas, documentos);
        }
    }

    public enum TipoEvento {
        ATENCION,
        DOCUMENTO
    }

    public record EventoHistoria(TipoEvento tipo, UUID id, Instant fecha, String titulo, String detalle,
                                 String estado, String autor) {

        static EventoHistoria de(Atencion a) {
            String dx = a.getDiagnosticos().stream().filter(Diagnostico::isPrincipal).findFirst()
                    .map(d -> d.getCodigoCie10() + " " + d.getDescripcion()).orElse(null);
            return new EventoHistoria(TipoEvento.ATENCION, a.getId(), a.getFechaAtencion(),
                    a.getMotivoConsulta() == null || a.getMotivoConsulta().isBlank() ? "Atención sin motivo registrado"
                            : a.getMotivoConsulta(), dx,
                    a.getOrigen() == Atencion.Origen.IMPORTADA ? "IMPORTADA" : a.getEstado().name(), a.getMedicoNombre());
        }

        static EventoHistoria de(DocumentoClinico d) {
            return new EventoHistoria(TipoEvento.DOCUMENTO, d.getId(), d.getCreadoEn(),
                    d.getDescripcion() != null ? d.getDescripcion() : d.getNombreArchivo(), d.getNombreArchivo(),
                    d.getTipo().name(), null);
        }
    }
}
