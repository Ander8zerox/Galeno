package com.galeno.importacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.galeno.agenda.AgendaPublica;
import com.galeno.formulacion.FormulasPublica;
import com.galeno.historia.AccesoHistoria.Accion;
import com.galeno.historia.Diagnostico;
import com.galeno.historia.HistoriaService;
import com.galeno.identidad.IdentidadService;
import com.galeno.importacion.RipsParser.AtencionRips;
import com.galeno.importacion.RipsParser.DxRips;
import com.galeno.importacion.RipsParser.PacienteRips;
import com.galeno.importacion.ImportacionListadoService.Columnas;
import com.galeno.importacion.ImportacionListadoService.MapeoColumnas;
import com.galeno.pacientes.DatosPersona;
import com.galeno.pacientes.DatosPersona.Cambio;
import com.galeno.pacientes.Paciente;
import com.galeno.pacientes.PacienteService;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.security.Permiso;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Importa historias de otro sistema en dos pasos: el lote se arma y se revisa sin tocar la historia; al
 * confirmarlo se crean los pacientes que faltan y las atenciones (FIRMADAS, origen IMPORTADA, sin médico de
 * Galeno). Volver a subir los mismos archivos no duplica nada: cada servicio tiene una clave única.
 */
@Service
public class ImportacionService {

    private static final String SIN_CIE10 = "Código no encontrado en la tabla CIE-10 vigente";

    private final LoteImportacionRepository lotes;
    private final RegistroImportacionRepository registros;
    private final CupsRepository cups;
    private final PacienteService pacientes;
    private final HistoriaService historia;
    private final FormulasPublica formulas;
    private final AgendaPublica agenda;
    private final IdentidadService identidad;
    private final JsonMapper mapper;
    private final RipsParser parser;
    private final ImportacionListadoService listado;

    ImportacionService(LoteImportacionRepository lotes, RegistroImportacionRepository registros, CupsRepository cups,
                       PacienteService pacientes, HistoriaService historia, FormulasPublica formulas,
                       AgendaPublica agenda, IdentidadService identidad, JsonMapper mapper,
                       ImportacionListadoService listado) {
        this.listado = listado;
        this.lotes = lotes;
        this.registros = registros;
        this.cups = cups;
        this.pacientes = pacientes;
        this.historia = historia;
        this.formulas = formulas;
        this.agenda = agenda;
        this.identidad = identidad;
        this.mapper = mapper;
        this.parser = new RipsParser(mapper);
    }

    // --- lo que se guarda en cada registro (JSON) ---

    record DxDatos(String codigo, String descripcion, boolean principal, Diagnostico.Tipo tipo) {
    }

    record AtencionDatos(String clave, String tipo, LocalDateTime fecha, String codigoCups, String nombreCups,
                         String profesional, String factura, List<DxDatos> diagnosticos) {
    }

    record DatosRegistro(LocalDate fechaNacimiento, String sexo, String tipoUsuario, String municipio,
                         List<AtencionDatos> atenciones) {
    }

    public enum Nivel {
        ERROR,
        ADVERTENCIA
    }

    public record Mensaje(Nivel nivel, String texto) {
    }

    /** Los datos de un registro de un listado de pacientes (en lugar de {@link DatosRegistro}). */
    record DatosListado(int fila, DatosPersona persona, List<Cambio> cambios) {
    }

    /**
     * @param pacientesRestaurados al deshacer un listado: pacientes que volvieron a sus datos anteriores. Es
     *                             {@code Integer} porque los lotes guardados antes de que existiera no lo traen.
     */
    record Resultado(int pacientesCreados, int pacientesActualizados, int atencionesCreadas, int atencionesYaImportadas,
                     int atencionesEliminadas, int pacientesEliminados, int pacientesConservados,
                     Integer pacientesRestaurados) {

        Resultado {
            pacientesRestaurados = pacientesRestaurados == null ? 0 : pacientesRestaurados;
        }
    }

    // --- vistas ---

    public record LoteVista(UUID id, LoteImportacion.Origen origen, LoteImportacion.Estado estado, int archivos,
                            int facturas, List<String> prestadores, int pacientes, int atenciones, int errores,
                            int omitidos, long incluidos, Resultado resultado, List<String> erroresArchivo,
                            Instant creadoEn, Instant confirmadoEn, Instant deshechoEn) {
    }

    /** {@code atenciones} va vacío si quien consulta no puede ver historias (p. ej. un ADMIN sin rol clínico). */
    public record RegistroVista(UUID id, String tipoDocumento, String numeroDocumento, RegistroImportacion.Estado estado,
                                boolean incluido, int totalAtenciones, LocalDate fechaNacimiento, String sexo,
                                List<Mensaje> mensajes, UUID pacienteId, String nombreActual,
                                List<AtencionDatos> atenciones, Integer fila, DatosPersona persona,
                                List<Cambio> cambios) {
    }

    public record Pagina<T>(List<T> items, int page, int size, long total) {
    }

    public record LoteDetalle(LoteVista lote, Pagina<RegistroVista> registros) {
    }

    public record ArchivoSubido(String nombre, byte[] contenido) {
    }

    // --- casos de uso ---

    /** Lee los archivos y arma el lote en revisión. No crea pacientes ni atenciones. */
    @Transactional
    public LoteVista crearDesdeRips(List<ArchivoSubido> subidos) {
        if (subidos.isEmpty()) {
            throw ApiProblem.badRequest("Seleccione al menos un archivo .json o .zip");
        }
        RipsParser.Resultado leido;
        try {
            leido = parser.leer(subidos.stream().map(a -> new RipsParser.Archivo(a.nombre(), a.contenido())).toList());
        } catch (IllegalArgumentException e) {
            throw ApiProblem.badRequest(e.getMessage());
        }
        if (leido.pacientes().isEmpty()) {
            String detalle = leido.erroresArchivo().isEmpty() ? "" : ": " + String.join("; ", leido.erroresArchivo().stream().limit(5).toList());
            throw ApiProblem.badRequest("No se encontró ningún paciente en los archivos" + detalle);
        }

        LoteImportacion lote = lotes.save(new LoteImportacion(LoteImportacion.Origen.RIPS_JSON, leido.archivos(),
                leido.facturas().size(), String.join(", ", leido.prestadores()), leido.omitidos(),
                AuthUser.current().id()));

        Map<String, String> nombresCie10 = historia.nombresCie10(leido.pacientes().values().stream()
                .flatMap(p -> p.atenciones.stream()).flatMap(a -> a.diagnosticos().stream()).map(DxRips::codigo)
                .collect(Collectors.toSet()));
        Map<String, String> nombresCups = cups.findAllById(leido.pacientes().values().stream()
                        .flatMap(p -> p.atenciones.stream()).map(AtencionRips::codigoCups)
                        .filter(c -> c != null).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Cups::getCodigo, Cups::getDescripcion));

        int orden = 0;
        int totalAtenciones = 0;
        int errores = 0;
        List<RegistroImportacion> nuevos = new ArrayList<>();
        for (PacienteRips p : leido.pacientes().values()) {
            List<Mensaje> mensajes = new ArrayList<>();
            p.errores.forEach(e -> mensajes.add(new Mensaje(Nivel.ERROR, e)));
            p.advertencias.stream().distinct().forEach(a -> mensajes.add(new Mensaje(Nivel.ADVERTENCIA, a)));

            List<AtencionDatos> atenciones = new ArrayList<>();
            for (AtencionRips a : p.atenciones) {
                atenciones.add(atencionDatos(a, nombresCie10, nombresCups, mensajes));
            }
            if (atenciones.isEmpty()) {
                mensajes.add(new Mensaje(Nivel.ADVERTENCIA, "No trae consultas ni procedimientos para importar"));
            }

            RegistroImportacion.Estado estado;
            UUID pacienteId = null;
            if (!p.errores.isEmpty()) {
                estado = RegistroImportacion.Estado.ERROR;
                errores++;
            } else {
                Optional<Paciente> existente = pacientes.porDocumento(p.tipoDocumento, p.numeroDocumento);
                if (existente.isPresent()) {
                    estado = RegistroImportacion.Estado.EXISTENTE;
                    pacienteId = existente.get().getId();
                    if (!p.fechaNacimiento.equals(existente.get().getFechaNacimiento())
                            || !p.sexo.equals(existente.get().getSexo())) {
                        mensajes.add(new Mensaje(Nivel.ADVERTENCIA,
                                "La fecha de nacimiento o el sexo no coinciden con los de Galeno; se conservan los de Galeno"));
                    }
                } else {
                    estado = RegistroImportacion.Estado.NUEVO;
                }
                totalAtenciones += atenciones.size();
            }
            DatosRegistro datos = new DatosRegistro(p.fechaNacimiento, p.sexo, p.tipoUsuario, p.municipio, atenciones);
            nuevos.add(new RegistroImportacion(lote.getId(), orden++, p.tipoDocumento, p.numeroDocumento, estado,
                    atenciones.size(), mapper.writeValueAsString(datos), mapper.writeValueAsString(mensajes), pacienteId));
        }
        registros.saveAll(nuevos);
        lote.totales(nuevos.size(), totalAtenciones, errores);
        return vista(lote, leido.erroresArchivo());
    }

    private static AtencionDatos atencionDatos(AtencionRips a, Map<String, String> nombresCie10,
                                               Map<String, String> nombresCups, List<Mensaje> mensajes) {
        List<DxDatos> dx = new ArrayList<>();
        for (DxRips d : a.diagnosticos()) {
            if (!d.codigo().matches("^[A-Z][0-9]{2}[0-9X]?$")) {
                mensajes.add(new Mensaje(Nivel.ADVERTENCIA, "Diagnóstico con código no válido omitido: " + d.codigo()));
                continue;
            }
            String nombre = nombresCie10.get(d.codigo());
            if (nombre == null) {
                mensajes.add(new Mensaje(Nivel.ADVERTENCIA, "El diagnóstico " + d.codigo() + " no está en la tabla CIE-10"));
            }
            dx.add(new DxDatos(d.codigo(), nombre != null ? nombre : SIN_CIE10, d.principal(), tipoDx(d.tipoRips())));
        }
        return new AtencionDatos(a.clave(), a.tipo(), a.fecha(), a.codigoCups(),
                a.codigoCups() == null ? null : nombresCups.get(a.codigoCups()), a.profesional(), a.factura(), dx);
    }

    /** Tabla de RIPS: 01 impresión diagnóstica, 02 confirmado nuevo, 03 confirmado repetido. */
    private static Diagnostico.Tipo tipoDx(String tipoRips) {
        if (tipoRips == null) {
            return Diagnostico.Tipo.IMPRESION_DIAGNOSTICA;
        }
        return switch (tipoRips.replaceFirst("^0+", "")) {
            case "2" -> Diagnostico.Tipo.CONFIRMADO_NUEVO;
            case "3" -> Diagnostico.Tipo.CONFIRMADO_REPETIDO;
            default -> Diagnostico.Tipo.IMPRESION_DIAGNOSTICA;
        };
    }

    /** Primer paso de un listado de pacientes: encabezados, muestra y mapeo propuesto. */
    public Columnas columnasListado(ArchivoSubido archivo) {
        return listado.analizar(archivo.nombre(), archivo.contenido());
    }

    /** Arma el lote en revisión de un listado de pacientes con el mapeo que eligió la persona. */
    @Transactional
    public LoteVista crearDesdeListado(ArchivoSubido archivo, MapeoColumnas mapeo) {
        return vista(listado.crear(archivo.nombre(), archivo.contenido(), mapeo), List.of());
    }

    @Transactional(readOnly = true)
    public Pagina<LoteVista> listar(Pageable pageable) {
        Page<LoteImportacion> page = lotes.findAllByOrderByCreadoEnDesc(pageable);
        return new Pagina<>(page.getContent().stream().map(l -> vista(l, List.of())).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public LoteDetalle obtener(UUID loteId, RegistroImportacion.Estado estado, Pageable pageable) {
        LoteImportacion lote = lote(loteId);
        Page<RegistroImportacion> page = estado == null
                ? registros.findByLoteIdOrderByOrden(loteId, pageable)
                : registros.findByLoteIdAndEstadoOrderByOrden(loteId, estado, pageable);
        boolean clinico = AuthUser.current().puede(Permiso.HISTORIA_VER);
        Map<UUID, String> nombres = pacientes.resumenes(page.getContent().stream()
                        .map(RegistroImportacion::getPacienteId).filter(id -> id != null).toList()).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().nombre()));
        List<RegistroVista> contenido = page.getContent().stream()
                .map(r -> vista(r, lote.getOrigen(), nombres.get(r.getPacienteId()), clinico)).toList();
        return new LoteDetalle(vista(lote, List.of()),
                new Pagina<>(contenido, page.getNumber(), page.getSize(), page.getTotalElements()));
    }

    /** Incluir o excluir un paciente del lote antes de confirmarlo. */
    @Transactional
    public RegistroVista incluir(UUID loteId, UUID registroId, boolean incluido) {
        LoteImportacion lote = lote(loteId);
        lote.exigir(LoteImportacion.Estado.EN_REVISION, "modificar");
        RegistroImportacion r = registros.findByIdAndLoteId(registroId, loteId)
                .orElseThrow(() -> ApiProblem.notFound("Registro no encontrado"));
        if (incluido && r.getEstado() == RegistroImportacion.Estado.ERROR) {
            throw ApiProblem.badRequest("Un registro con errores no se puede importar");
        }
        r.incluir(incluido);
        return vista(r, lote.getOrigen(), null, AuthUser.current().puede(Permiso.HISTORIA_VER));
    }

    @Transactional
    public LoteVista incluirTodos(UUID loteId, boolean incluido) {
        LoteImportacion lote = lote(loteId);
        lote.exigir(LoteImportacion.Estado.EN_REVISION, "modificar");
        registros.incluirTodos(loteId, incluido);
        return vista(lote, List.of());
    }

    /** Crea los pacientes nuevos y las atenciones de los registros incluidos. Todo o nada. */
    @Transactional
    public LoteVista confirmar(UUID loteId) {
        LoteImportacion lote = lotes.paraProcesar(loteId).orElseThrow(() -> ApiProblem.notFound("Lote no encontrado"));
        lote.exigir(LoteImportacion.Estado.EN_REVISION, "confirmar");
        if (lote.getOrigen() == LoteImportacion.Origen.PACIENTES_LISTADO) {
            lote.confirmar(json(listado.confirmar(loteId)));
            return vista(lote, List.of());
        }
        ZoneId zona = identidad.zonaHoraria(AuthUser.current().tenantId());

        int creados = 0;
        int actualizados = 0;
        int atencionesCreadas = 0;
        int yaImportadas = 0;
        for (RegistroImportacion r : registros.findByLoteIdOrderByOrden(loteId)) {
            if (!r.isIncluido() || r.getEstado() == RegistroImportacion.Estado.ERROR) {
                continue;
            }
            DatosRegistro datos = mapper.readValue(r.getDatos(), DatosRegistro.class);
            // Se vuelve a buscar: entre la revisión y la confirmación alguien pudo crear al paciente a mano
            Optional<Paciente> existente = pacientes.porDocumento(r.getTipoDocumento(), r.getNumeroDocumento());
            Paciente paciente;
            if (existente.isPresent()) {
                paciente = existente.get();
                actualizados++;
            } else {
                paciente = pacientes.crearImportado(r.getTipoDocumento(), r.getNumeroDocumento(), datos.fechaNacimiento(),
                        datos.sexo(), loteId);
                creados++;
            }
            r.importadoComo(paciente.getId());
            int deEste = 0;
            for (AtencionDatos a : datos.atenciones()) {
                boolean creada = historia.importarAtencion(paciente.getId(), a.fecha().atZone(zona).toInstant(),
                        descripcion(a), profesional(a), diagnosticos(a), a.clave(), loteId);
                if (creada) {
                    deEste++;
                } else {
                    yaImportadas++;
                }
            }
            atencionesCreadas += deEste;
            if (deEste > 0) {
                historia.registrarAcceso(paciente.getId(), Accion.IMPORTAR_ATENCIONES, loteId);
            }
        }
        lote.confirmar(json(new Resultado(creados, actualizados, atencionesCreadas, yaImportadas, 0, 0, 0, 0)));
        return vista(lote, List.of());
    }

    /**
     * Quita lo que creó el lote. Se niega si alguna atención importada ya tiene notas aclaratorias o fórmulas.
     * Los pacientes creados por el lote se borran solo si no tienen ninguna otra actividad en Galeno.
     */
    @Transactional
    public LoteVista deshacer(UUID loteId) {
        LoteImportacion lote = lotes.paraProcesar(loteId).orElseThrow(() -> ApiProblem.notFound("Lote no encontrado"));
        lote.exigir(LoteImportacion.Estado.CONFIRMADO, "deshacer");
        if (lote.getOrigen() == LoteImportacion.Origen.PACIENTES_LISTADO) {
            Resultado antes = lote.getResultado() == null ? null : mapper.readValue(lote.getResultado(), Resultado.class);
            lote.deshacer(json(listado.deshacer(loteId, antes)));
            return vista(lote, List.of());
        }
        List<UUID> atencionIds = historia.atencionesDelLote(loteId);
        if (historia.notasEnAtenciones(atencionIds) > 0 || formulas.ligadasA(atencionIds) > 0) {
            throw ApiProblem.conflict("Algunas atenciones importadas ya tienen notas aclaratorias o fórmulas; el lote no se puede deshacer");
        }
        List<RegistroImportacion> importados = registros.findByLoteIdOrderByOrden(loteId).stream()
                .filter(r -> r.getPacienteId() != null).toList();
        int eliminadas = historia.eliminarAtencionesDelLote(loteId);

        int borrados = 0;
        int conservados = 0;
        Map<UUID, Paciente> porId = pacientesPorId(importados.stream().map(RegistroImportacion::getPacienteId).toList());
        for (RegistroImportacion r : importados) {
            UUID pacienteId = r.getPacienteId();
            // La auditoría no se borra: queda constancia de lo importado y de que se deshizo
            historia.registrarAcceso(pacienteId, Accion.DESHACER_IMPORTACION, loteId);
            Paciente p = porId.get(pacienteId);
            if (p == null || !loteId.equals(p.getLoteImportacionId())) {
                continue; // ya existía antes del lote: se conserva
            }
            boolean sinActividad = historia.actividadClinica(pacienteId) == 0 && formulas.delPaciente(pacienteId) == 0
                    && agenda.citasDelPaciente(pacienteId) == 0;
            if (sinActividad) {
                pacientes.eliminarImportado(pacienteId, loteId);
                borrados++;
            } else {
                conservados++;
            }
        }
        Resultado antes = lote.getResultado() == null ? null : mapper.readValue(lote.getResultado(), Resultado.class);
        lote.deshacer(json(new Resultado(antes == null ? 0 : antes.pacientesCreados(),
                antes == null ? 0 : antes.pacientesActualizados(), antes == null ? 0 : antes.atencionesCreadas(),
                antes == null ? 0 : antes.atencionesYaImportadas(), eliminadas, borrados, conservados, 0)));
        return vista(lote, List.of());
    }

    /** Descarta un lote en revisión y borra sus registros (llevan datos clínicos que ya no se van a usar). */
    @Transactional
    public void descartar(UUID loteId) {
        LoteImportacion lote = lotes.paraProcesar(loteId).orElseThrow(() -> ApiProblem.notFound("Lote no encontrado"));
        lote.descartar();
        registros.borrarDelLote(loteId);
    }

    // --- apoyo ---

    private Map<UUID, Paciente> pacientesPorId(Collection<UUID> ids) {
        return ids.stream().map(id -> pacientes.buscar(id)).flatMap(Optional::stream)
                .collect(Collectors.toMap(Paciente::getId, Function.identity()));
    }

    private static String descripcion(AtencionDatos a) {
        String tipo = switch (a.tipo()) {
            case "consultas" -> "Consulta";
            case "procedimientos" -> "Procedimiento";
            case "urgencias" -> "Urgencias";
            case "hospitalizacion" -> "Hospitalización";
            default -> "Servicio";
        };
        StringBuilder s = new StringBuilder(tipo);
        if (a.codigoCups() != null) {
            s.append(" ").append(a.codigoCups());
            if (a.nombreCups() != null) {
                s.append(" · ").append(a.nombreCups());
            }
        }
        s.append(" (importado de RIPS, factura ").append(a.factura()).append(")");
        return s.length() > 1000 ? s.substring(0, 1000) : s.toString();
    }

    private static String profesional(AtencionDatos a) {
        return a.profesional() == null ? "Profesional no informado (importado de RIPS)"
                : "Profesional " + a.profesional() + " (importado de RIPS)";
    }

    private static List<Diagnostico> diagnosticos(AtencionDatos a) {
        return a.diagnosticos().stream()
                .map(d -> new Diagnostico(d.codigo(), d.descripcion(), d.principal(), d.tipo())).toList();
    }

    private LoteImportacion lote(UUID id) {
        return lotes.findById(id).orElseThrow(() -> ApiProblem.notFound("Lote no encontrado"));
    }

    private String json(Object o) {
        return mapper.writeValueAsString(o);
    }

    private LoteVista vista(LoteImportacion l, List<String> erroresArchivo) {
        List<String> prestadores = l.getPrestadores() == null || l.getPrestadores().isBlank() ? List.of()
                : List.of(l.getPrestadores().split(",\\s*"));
        return new LoteVista(l.getId(), l.getOrigen(), l.getEstado(), l.getArchivos(), l.getFacturas(), prestadores,
                l.getPacientes(), l.getAtenciones(), l.getErrores(), l.getOmitidos(),
                registros.countByLoteIdAndIncluidoTrue(l.getId()),
                l.getResultado() == null ? null : mapper.readValue(l.getResultado(), Resultado.class), erroresArchivo,
                l.getCreadoEn(), l.getConfirmadoEn(), l.getDeshechoEn());
    }

    private RegistroVista vista(RegistroImportacion r, LoteImportacion.Origen origen, String nombreActual, boolean clinico) {
        List<Mensaje> mensajes = mapper.readValue(r.getMensajes(), new TypeReference<List<Mensaje>>() {
        });
        if (origen == LoteImportacion.Origen.PACIENTES_LISTADO) {
            DatosListado datos = mapper.readValue(r.getDatos(), DatosListado.class);
            return new RegistroVista(r.getId(), r.getTipoDocumento(), r.getNumeroDocumento(), r.getEstado(),
                    r.isIncluido(), 0, datos.persona().fechaNacimiento(), datos.persona().sexo(), mensajes,
                    r.getPacienteId(), nombreActual, List.of(), datos.fila(), datos.persona(), datos.cambios());
        }
        DatosRegistro datos = mapper.readValue(r.getDatos(), DatosRegistro.class);
        return new RegistroVista(r.getId(), r.getTipoDocumento(), r.getNumeroDocumento(), r.getEstado(), r.isIncluido(),
                r.getAtenciones(), datos.fechaNacimiento(), datos.sexo(), mensajes, r.getPacienteId(), nombreActual,
                clinico ? datos.atenciones() : List.of(), null, null, List.of());
    }
}
