package com.galeno.importacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import com.galeno.agenda.AgendaPublica;
import com.galeno.formulacion.FormulasPublica;
import com.galeno.historia.HistoriaService;
import com.galeno.importacion.ImportacionService.DatosListado;
import com.galeno.importacion.ImportacionService.Mensaje;
import com.galeno.importacion.ImportacionService.Nivel;
import com.galeno.importacion.ImportacionService.Resultado;
import com.galeno.importacion.ListadoPacientes.Campo;
import com.galeno.importacion.ListadoPacientes.FilaPersona;
import com.galeno.importacion.ListadoPacientes.OrdenNombre;
import com.galeno.importacion.LectorTabla.Tabla;
import com.galeno.pacientes.DatosPersona;
import com.galeno.pacientes.DatosPersona.Cambio;
import com.galeno.pacientes.Paciente;
import com.galeno.pacientes.PacienteService;
import com.galeno.pacientes.PacienteService.Comparacion;
import com.galeno.pacientes.TiposDocumento;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Importa un listado de pacientes (Excel o CSV) de otro sistema. Su uso principal es completar los pacientes que
 * el RIPS dejó sin nombre, pero también crea los que no existen. Nunca reemplaza datos ya registrados en Galeno:
 * solo llena lo que falta, y las diferencias se muestran en la revisión.
 */
@Service
class ImportacionListadoService {

    private static final int MUESTRA = 5;

    private final LoteImportacionRepository lotes;
    private final RegistroImportacionRepository registros;
    private final PlantillaImportacionRepository plantillas;
    private final PacienteService pacientes;
    private final HistoriaService historia;
    private final FormulasPublica formulas;
    private final AgendaPublica agenda;
    private final JsonMapper mapper;

    ImportacionListadoService(LoteImportacionRepository lotes, RegistroImportacionRepository registros,
                              PlantillaImportacionRepository plantillas, PacienteService pacientes,
                              HistoriaService historia, FormulasPublica formulas, AgendaPublica agenda, JsonMapper mapper) {
        this.lotes = lotes;
        this.registros = registros;
        this.plantillas = plantillas;
        this.pacientes = pacientes;
        this.historia = historia;
        this.formulas = formulas;
        this.agenda = agenda;
        this.mapper = mapper;
    }

    public record Asignacion(Campo campo, int columna) {
    }

    public record MapeoColumnas(List<Asignacion> asignaciones, String tipoDocumentoPorDefecto, OrdenNombre ordenNombre) {
    }

    /** @param recordado el mapeo es el que se usó la última vez con un archivo de estos mismos encabezados */
    public record Columnas(List<String> encabezados, List<List<String>> muestra, int filas, MapeoColumnas sugerido,
                           boolean recordado) {
    }

    /** Lee los encabezados y propone el mapeo. No guarda nada. */
    @Transactional(readOnly = true)
    public Columnas analizar(String nombre, byte[] contenido) {
        Tabla tabla = leer(nombre, contenido);
        Optional<MapeoColumnas> recordado = plantillas.findByFirmaHash(hash(ListadoPacientes.firma(tabla.encabezados())))
                .map(p -> mapper.readValue(p.getMapeo(), MapeoColumnas.class));
        MapeoColumnas sugerido = recordado.orElseGet(() -> sugerir(tabla.encabezados()));
        return new Columnas(tabla.encabezados(), tabla.filas().stream().limit(MUESTRA).toList(), tabla.filas().size(),
                sugerido, recordado.isPresent());
    }

    private static MapeoColumnas sugerir(List<String> encabezados) {
        Map<Campo, Integer> columnas = ListadoPacientes.sugerir(encabezados);
        Integer completo = columnas.get(Campo.NOMBRE_COMPLETO);
        OrdenNombre orden = completo != null && ListadoPacientes.apellidosPrimero(encabezados.get(completo))
                ? OrdenNombre.APELLIDOS_NOMBRES : OrdenNombre.NOMBRES_APELLIDOS;
        List<Asignacion> asignaciones = columnas.entrySet().stream().map(e -> new Asignacion(e.getKey(), e.getValue())).toList();
        return new MapeoColumnas(asignaciones, columnas.containsKey(Campo.TIPO_DOCUMENTO) ? null : "CC", orden);
    }

    /** Arma el lote en revisión con un registro por fila. */
    @Transactional
    public LoteImportacion crear(String nombre, byte[] contenido, MapeoColumnas solicitado) {
        Tabla tabla = leer(nombre, contenido);
        ListadoPacientes.Mapeo mapeo = validar(solicitado, tabla.encabezados().size());
        String firma = ListadoPacientes.firma(tabla.encabezados());
        String firmaHash = hash(firma);
        String json = mapper.writeValueAsString(solicitado);
        plantillas.findByFirmaHash(firmaHash).ifPresentOrElse(p -> p.actualizar(json),
                () -> plantillas.save(new PlantillaImportacion(firma, firmaHash, json)));

        int vacias = 0;
        Map<String, Pendiente> porDocumento = new LinkedHashMap<>();
        for (int i = 0; i < tabla.filas().size(); i++) {
            List<String> fila = tabla.filas().get(i);
            if (fila.stream().allMatch(String::isBlank)) {
                vacias++;
                continue;
            }
            // Fila 1 = encabezados, como la ve la persona en Excel
            FilaPersona fp = ListadoPacientes.interpretar(i + 2, fila, mapeo);
            String clave = fp.tipoDocumento() + " " + fp.numeroDocumento();
            Pendiente previo = fp.errores().isEmpty() ? porDocumento.get(clave) : null;
            if (previo != null) {
                previo.mensajes.add(new Mensaje(Nivel.ADVERTENCIA,
                        "También aparece en la fila " + fp.fila() + "; se usa la fila " + previo.fila.fila()));
                continue;
            }
            porDocumento.put(fp.errores().isEmpty() ? clave : "fila " + fp.fila(), revisar(fp));
        }
        if (porDocumento.isEmpty()) {
            throw ApiProblem.badRequest("El archivo no tiene filas con datos");
        }

        LoteImportacion lote = lotes.save(new LoteImportacion(LoteImportacion.Origen.PACIENTES_LISTADO, 1, 0, null,
                vacias, AuthUser.current().id()));
        int orden = 0;
        int errores = 0;
        List<RegistroImportacion> nuevos = new ArrayList<>();
        for (Pendiente p : porDocumento.values()) {
            if (p.estado == RegistroImportacion.Estado.ERROR) {
                errores++;
            }
            FilaPersona fp = p.fila;
            RegistroImportacion r = new RegistroImportacion(lote.getId(), orden++,
                    fp.tipoDocumento() != null ? fp.tipoDocumento() : "?",
                    // Un registro con error puede no tener documento: se identifica por la fila
                    p.estado == RegistroImportacion.Estado.ERROR || fp.numeroDocumento() == null
                            ? "fila " + fp.fila() : fp.numeroDocumento(),
                    p.estado, 0, mapper.writeValueAsString(new DatosListado(fp.fila(), fp.datos(), p.cambios)),
                    mapper.writeValueAsString(p.mensajes), p.pacienteId);
            if (!p.incluir) {
                r.incluir(false);
            }
            nuevos.add(r);
        }
        registros.saveAll(nuevos);
        lote.totales(nuevos.size(), 0, errores);
        return lote;
    }

    /** Lo que se sabe de una fila antes de guardarla como registro. */
    private static final class Pendiente {
        FilaPersona fila;
        RegistroImportacion.Estado estado;
        UUID pacienteId;
        List<Cambio> cambios = List.of();
        boolean incluir = true;
        final List<Mensaje> mensajes = new ArrayList<>();
    }

    private Pendiente revisar(FilaPersona fp) {
        Pendiente p = new Pendiente();
        p.fila = fp;
        fp.errores().forEach(e -> p.mensajes.add(new Mensaje(Nivel.ERROR, e)));
        fp.advertencias().forEach(a -> p.mensajes.add(new Mensaje(Nivel.ADVERTENCIA, a)));
        if (!fp.errores().isEmpty()) {
            p.estado = RegistroImportacion.Estado.ERROR;
            return p;
        }
        Optional<Paciente> existente = pacientes.porDocumento(fp.tipoDocumento(), fp.numeroDocumento());
        if (existente.isPresent()) {
            p.estado = RegistroImportacion.Estado.EXISTENTE;
            p.pacienteId = existente.get().getId();
            Comparacion c = pacientes.compararConListado(p.pacienteId, fp.datos());
            p.cambios = c.cambios();
            c.diferencias().forEach(d -> p.mensajes.add(new Mensaje(Nivel.ADVERTENCIA, d)));
            if (c.cambios().isEmpty()) {
                p.mensajes.add(new Mensaje(Nivel.ADVERTENCIA, "No trae datos nuevos para este paciente"));
                p.incluir = false;
            }
            return p;
        }
        List<Paciente> mismoNumero = pacientes.porNumeroDocumento(fp.numeroDocumento());
        if (!mismoNumero.isEmpty()) {
            Paciente otro = mismoNumero.getFirst();
            p.mensajes.add(new Mensaje(Nivel.ADVERTENCIA, "Ya existe " + otro.getNombres() + " " + otro.getApellidos()
                    + " con " + otro.getTipoDocumento() + " " + otro.getNumeroDocumento()
                    + ": si es la misma persona, no lo importe y corrija el documento en Galeno"));
            p.incluir = false;
        }
        if (!fp.datos().completaParaCrear()) {
            List<String> faltan = new ArrayList<>();
            if (fp.datos().nombres() == null) faltan.add("nombres");
            if (fp.datos().apellidos() == null) faltan.add("apellidos");
            if (fp.datos().fechaNacimiento() == null) faltan.add("fecha de nacimiento");
            if (fp.datos().sexo() == null) faltan.add("sexo");
            p.mensajes.add(new Mensaje(Nivel.ERROR, "No existe en Galeno y para crearlo faltan: " + String.join(", ", faltan)));
            p.estado = RegistroImportacion.Estado.ERROR;
            return p;
        }
        p.estado = RegistroImportacion.Estado.NUEVO;
        return p;
    }

    /** Completa los existentes y crea los nuevos. Guarda en cada registro lo que cambió, para poder deshacerlo. */
    @Transactional
    public Resultado confirmar(UUID loteId) {
        int creados = 0;
        int actualizados = 0;
        for (RegistroImportacion r : registros.findByLoteIdOrderByOrden(loteId)) {
            if (!r.isIncluido() || r.getEstado() == RegistroImportacion.Estado.ERROR) {
                continue;
            }
            DatosListado datos = mapper.readValue(r.getDatos(), DatosListado.class);
            // Se vuelve a buscar y a comparar: entre la revisión y la confirmación alguien pudo editar al paciente
            Optional<Paciente> existente = pacientes.porDocumento(r.getTipoDocumento(), r.getNumeroDocumento());
            if (existente.isPresent()) {
                List<Cambio> cambios = pacientes.completarDesdeListado(existente.get().getId(), datos.persona());
                r.importadoComo(existente.get().getId());
                r.cambiarDatos(mapper.writeValueAsString(new DatosListado(datos.fila(), datos.persona(), cambios)));
                if (!cambios.isEmpty()) {
                    actualizados++;
                }
            } else if (datos.persona().completaParaCrear()) {
                Paciente p = pacientes.crearDesdeListado(r.getTipoDocumento(), r.getNumeroDocumento(), datos.persona(), loteId);
                r.importadoComo(p.getId());
                r.cambiarDatos(mapper.writeValueAsString(new DatosListado(datos.fila(), datos.persona(), List.of())));
                creados++;
            }
        }
        return new Resultado(creados, actualizados, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Borra los pacientes que creó el lote (si no tienen actividad en Galeno) y devuelve los demás a como estaban,
     * salvo los campos que alguien editó después de importar.
     */
    @Transactional
    public Resultado deshacer(UUID loteId, Resultado antes) {
        int borrados = 0;
        int conservados = 0;
        int restaurados = 0;
        for (RegistroImportacion r : registros.findByLoteIdOrderByOrden(loteId)) {
            if (r.getPacienteId() == null || r.getEstado() == RegistroImportacion.Estado.ERROR) {
                continue;
            }
            Optional<Paciente> p = pacientes.buscar(r.getPacienteId());
            if (p.isEmpty()) {
                continue;
            }
            UUID id = p.get().getId();
            if (loteId.equals(p.get().getLoteImportacionId())) {
                boolean sinActividad = historia.actividadClinica(id) == 0 && formulas.delPaciente(id) == 0
                        && agenda.citasDelPaciente(id) == 0;
                if (sinActividad) {
                    pacientes.eliminarImportado(id, loteId);
                    borrados++;
                } else {
                    conservados++;
                }
            } else {
                List<Cambio> cambios = mapper.readValue(r.getDatos(), DatosListado.class).cambios();
                if (!cambios.isEmpty()) {
                    pacientes.revertirCambios(id, cambios);
                    restaurados++;
                }
            }
        }
        return new Resultado(antes == null ? 0 : antes.pacientesCreados(), antes == null ? 0 : antes.pacientesActualizados(),
                0, 0, 0, borrados, conservados, restaurados);
    }

    private static Tabla leer(String nombre, byte[] contenido) {
        try {
            return LectorTabla.leer(nombre, contenido);
        } catch (IllegalArgumentException e) {
            throw ApiProblem.badRequest(e.getMessage());
        }
    }

    private static ListadoPacientes.Mapeo validar(MapeoColumnas m, int columnas) {
        if (m == null || m.asignaciones() == null) {
            throw ApiProblem.badRequest("Falta el mapeo de columnas");
        }
        Map<Campo, Integer> porCampo = new EnumMap<>(Campo.class);
        for (Asignacion a : m.asignaciones()) {
            if (a.campo() == null || a.columna() < 0 || a.columna() >= columnas) {
                throw ApiProblem.badRequest("Asignación de columna no válida");
            }
            if (porCampo.put(a.campo(), a.columna()) != null) {
                throw ApiProblem.badRequest("El campo " + a.campo() + " está asignado a dos columnas");
            }
        }
        if (!porCampo.containsKey(Campo.NUMERO_DOCUMENTO)) {
            throw ApiProblem.badRequest("Indique qué columna tiene el número de documento");
        }
        if (!porCampo.containsKey(Campo.TIPO_DOCUMENTO)
                && (m.tipoDocumentoPorDefecto() == null || !TiposDocumento.VALIDOS.contains(m.tipoDocumentoPorDefecto()))) {
            throw ApiProblem.badRequest("Indique la columna del tipo de documento o un tipo para todos");
        }
        boolean nombres = porCampo.containsKey(Campo.NOMBRES) || porCampo.containsKey(Campo.PRIMER_NOMBRE)
                || porCampo.containsKey(Campo.NOMBRE_COMPLETO);
        boolean apellidos = porCampo.containsKey(Campo.APELLIDOS) || porCampo.containsKey(Campo.PRIMER_APELLIDO)
                || porCampo.containsKey(Campo.NOMBRE_COMPLETO);
        if (nombres != apellidos) {
            throw ApiProblem.badRequest("Indique tanto los nombres como los apellidos (o una columna con el nombre completo)");
        }
        if (!nombres && !porCampo.containsKey(Campo.TELEFONO) && !porCampo.containsKey(Campo.EMAIL)) {
            throw ApiProblem.badRequest("El archivo no trae nada que importar: asigne al menos los nombres o un dato de contacto");
        }
        return new ListadoPacientes.Mapeo(porCampo, m.tipoDocumentoPorDefecto(),
                m.ordenNombre() == null ? OrdenNombre.NOMBRES_APELLIDOS : m.ordenNombre());
    }

    private static String hash(String firma) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(firma.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
