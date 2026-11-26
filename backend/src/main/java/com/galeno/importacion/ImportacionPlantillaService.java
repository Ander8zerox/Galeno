package com.galeno.importacion;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import com.galeno.agenda.AgendaPublica;
import com.galeno.formulacion.FormulasPublica;
import com.galeno.historia.AccesoHistoria.Accion;
import com.galeno.historia.AtencionImportada;
import com.galeno.historia.Diagnostico;
import com.galeno.historia.HistoriaService;
import com.galeno.identidad.IdentidadService;
import com.galeno.importacion.ImportacionService.DxDatos;
import com.galeno.importacion.ImportacionService.Mensaje;
import com.galeno.importacion.ImportacionService.Nivel;
import com.galeno.importacion.ImportacionService.Resultado;
import com.galeno.importacion.PlantillaGalenum.AtencionFila;
import com.galeno.importacion.PlantillaGalenum.PacienteFila;
import com.galeno.pacientes.DatosPersona;
import com.galeno.pacientes.DatosPersona.Cambio;
import com.galeno.pacientes.ExtrasPersona;
import com.galeno.pacientes.Paciente;
import com.galeno.pacientes.PacienteService;
import com.galeno.pacientes.PacienteService.Comparacion;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Importa la plantilla de Galenum (pacientes y atenciones con su contenido clínico). Combina las reglas del listado
 * (los pacientes existentes solo se completan, nunca se reemplazan) con las del RIPS (las atenciones quedan FIRMADAS,
 * con origen IMPORTADA y el nombre del profesional del sistema anterior, y una clave que evita duplicarlas).
 */
@Service
class ImportacionPlantillaService {

    private static final String SIN_CIE10 = "Código no encontrado en la tabla CIE-10 vigente";

    private final LoteImportacionRepository lotes;
    private final RegistroImportacionRepository registros;
    private final PacienteService pacientes;
    private final HistoriaService historia;
    private final FormulasPublica formulas;
    private final AgendaPublica agenda;
    private final IdentidadService identidad;
    private final JsonMapper mapper;
    private final EntityManager em;

    ImportacionPlantillaService(LoteImportacionRepository lotes, RegistroImportacionRepository registros,
                                PacienteService pacientes, HistoriaService historia, FormulasPublica formulas,
                                AgendaPublica agenda, IdentidadService identidad, JsonMapper mapper, EntityManager em) {
        this.em = em;
        this.lotes = lotes;
        this.registros = registros;
        this.pacientes = pacientes;
        this.historia = historia;
        this.formulas = formulas;
        this.agenda = agenda;
        this.identidad = identidad;
        this.mapper = mapper;
    }

    /** Una atención lista para crear, como queda guardada en el registro. */
    record AtencionGuardada(int fila, String clave, LocalDateTime fecha, AtencionImportada contenido, List<DxDatos> diagnosticos) {
    }

    /**
     * Lo que se guarda en cada registro. {@code persona} es null si el paciente no está en la hoja Pacientes (sus
     * atenciones van a un paciente que ya existe en Galenum). {@code cambios}: lo que se cambiaría o se cambió.
     */
    record DatosPlantilla(Integer fila, DatosPersona persona, ExtrasPersona extras, List<AtencionGuardada> atenciones,
                         List<Cambio> cambios) {
    }

    @Transactional
    public LoteImportacion crear(String nombre, byte[] contenido) {
        PlantillaGalenum.Resultado leido;
        try {
            leido = PlantillaGalenum.leer(LectorTabla.leerHojas(nombre, contenido));
        } catch (IllegalArgumentException e) {
            throw ApiProblem.badRequest(e.getMessage());
        }
        if (leido.pacientes().isEmpty()) {
            String detalle = leido.erroresSueltos().isEmpty() ? "" : ": " + String.join("; ", leido.erroresSueltos().stream().limit(5).toList());
            throw ApiProblem.badRequest("La plantilla no tiene pacientes ni atenciones para importar" + detalle);
        }
        Map<String, String> nombresCie10 = historia.nombresCie10(leido.pacientes().values().stream()
                .flatMap(p -> p.atenciones.stream()).flatMap(a -> a.diagnosticos().stream()).map(PlantillaGalenum.Dx::codigo)
                .collect(Collectors.toSet()));

        // Los pacientes que ya existen, de una vez (no dos consultas por paciente)
        Map<String, List<Paciente>> porNumero = pacientes.porNumerosDocumento(leido.pacientes().values().stream()
                .map(p -> p.numeroDocumento).toList()).stream().collect(Collectors.groupingBy(Paciente::getNumeroDocumento));

        LoteImportacion lote = lotes.save(new LoteImportacion(LoteImportacion.Origen.PLANTILLA_GALENUM, 1, 0, null,
                leido.omitidas(), AuthUser.current().id()));
        int orden = 0;
        int errores = 0;
        int totalAtenciones = 0;
        List<RegistroImportacion> nuevos = new ArrayList<>();
        for (PacienteFila p : leido.pacientes().values()) {
            List<Mensaje> mensajes = new ArrayList<>();
            p.errores.forEach(e -> mensajes.add(new Mensaje(Nivel.ERROR, e)));
            p.advertencias.forEach(a -> mensajes.add(new Mensaje(Nivel.ADVERTENCIA, a)));
            List<AtencionGuardada> atenciones = new ArrayList<>();
            for (AtencionFila a : p.atenciones) {
                atenciones.add(guardada(a, nombresCie10, mensajes));
            }
            Revision rev = revisar(p, atenciones, mensajes, porNumero.getOrDefault(p.numeroDocumento, List.of()));
            if (rev.estado == RegistroImportacion.Estado.ERROR) {
                errores++;
            } else {
                totalAtenciones += atenciones.size();
            }
            DatosPlantilla datos = new DatosPlantilla(p.persona == null ? null : p.persona.fila(),
                    p.persona == null ? null : p.persona.datos(), p.extras, atenciones, rev.cambios);
            RegistroImportacion r = new RegistroImportacion(lote.getId(), orden++, p.tipoDocumento, p.numeroDocumento,
                    rev.estado, atenciones.size(), mapper.writeValueAsString(datos), mapper.writeValueAsString(mensajes),
                    rev.pacienteId);
            if (!rev.incluir) {
                r.incluir(false);
            }
            nuevos.add(r);
        }
        registros.saveAll(nuevos);
        lote.totales(nuevos.size(), totalAtenciones, errores);
        return lote;
    }

    private static final class Revision {
        RegistroImportacion.Estado estado;
        UUID pacienteId;
        List<Cambio> cambios = List.of();
        boolean incluir = true;
    }

    private Revision revisar(PacienteFila p, List<AtencionGuardada> atenciones, List<Mensaje> mensajes,
                             List<Paciente> mismoNumero) {
        Revision rev = new Revision();
        if (!p.errores.isEmpty()) {
            rev.estado = RegistroImportacion.Estado.ERROR;
            return rev;
        }
        Optional<Paciente> existente = mismoNumero.stream().filter(x -> x.getTipoDocumento().equals(p.tipoDocumento)).findFirst();
        if (existente.isPresent()) {
            rev.estado = RegistroImportacion.Estado.EXISTENTE;
            rev.pacienteId = existente.get().getId();
            List<Cambio> cambios = new ArrayList<>();
            if (p.persona != null) {
                Comparacion c = pacientes.compararConListado(rev.pacienteId, p.persona.datos());
                cambios.addAll(c.cambios());
                c.diferencias().forEach(d -> mensajes.add(new Mensaje(Nivel.ADVERTENCIA, d)));
            }
            if (p.extras != null) {
                cambios.addAll(pacientes.compararExtras(rev.pacienteId, p.extras));
            }
            rev.cambios = cambios;
            if (cambios.isEmpty() && atenciones.isEmpty()) {
                mensajes.add(new Mensaje(Nivel.ADVERTENCIA, "No trae datos nuevos ni atenciones para este paciente"));
                rev.incluir = false;
            }
            return rev;
        }
        if (p.persona == null) {
            mensajes.add(new Mensaje(Nivel.ERROR, "Tiene atenciones pero no está en la hoja Pacientes ni en Galenum: agréguelo a la hoja Pacientes"));
            rev.estado = RegistroImportacion.Estado.ERROR;
            return rev;
        }
        DatosPersona d = p.persona.datos();
        if (!d.completaParaCrear()) {
            List<String> faltan = new ArrayList<>();
            if (d.nombres() == null) faltan.add("nombres");
            if (d.apellidos() == null) faltan.add("apellidos");
            if (d.fechaNacimiento() == null) faltan.add("fecha de nacimiento");
            if (d.sexo() == null) faltan.add("sexo");
            mensajes.add(new Mensaje(Nivel.ERROR, "No existe en Galenum y para crearlo faltan: " + String.join(", ", faltan)));
            rev.estado = RegistroImportacion.Estado.ERROR;
            return rev;
        }
        if (!mismoNumero.isEmpty()) {
            Paciente otro = mismoNumero.getFirst();
            mensajes.add(new Mensaje(Nivel.ADVERTENCIA, "Ya existe " + otro.getNombres() + " " + otro.getApellidos()
                    + " con " + otro.getTipoDocumento() + " " + otro.getNumeroDocumento()
                    + ": si es la misma persona, no lo importe y corrija el documento en la plantilla"));
            rev.incluir = false;
        }
        if (atenciones.isEmpty()) {
            mensajes.add(new Mensaje(Nivel.ADVERTENCIA, "Se crea el paciente sin atenciones"));
        }
        rev.estado = RegistroImportacion.Estado.NUEVO;
        return rev;
    }

    private static AtencionGuardada guardada(AtencionFila a, Map<String, String> nombresCie10, List<Mensaje> mensajes) {
        List<DxDatos> dx = new ArrayList<>();
        for (PlantillaGalenum.Dx d : a.diagnosticos()) {
            String nombre = nombresCie10.get(d.codigo());
            if (nombre == null) {
                mensajes.add(new Mensaje(Nivel.ADVERTENCIA, "Atenciones, fila " + a.fila() + ": el diagnóstico " + d.codigo()
                        + " no está en la tabla CIE-10"));
            }
            dx.add(new DxDatos(d.codigo(), nombre != null ? nombre : SIN_CIE10, d.principal(), d.tipo()));
        }
        String profesional = a.profesional() + " (importado)";
        AtencionImportada contenido = new AtencionImportada(profesional, a.registroMedico(), a.motivo(), a.enfermedadActual(),
                a.revision(), a.examen(), a.taSistolica(), a.taDiastolica(), a.fc(), a.fr(), a.temperatura(), a.saturacion(),
                a.peso(), a.talla(), a.analisis(), a.plan());
        return new AtencionGuardada(a.fila(), a.clave(), a.fecha(), contenido, dx);
    }

    /**
     * Completa o crea los pacientes y crea las atenciones de los registros incluidos. Todo o nada.
     *
     * <p>Una plantilla trae miles de filas: los pacientes y las claves que ya existen se consultan de una vez, y sin
     * consultas por fila Hibernate no revisa todo lo cargado antes de cada una (con FlushMode COMMIT escribe al final,
     * en lotes). Sin esto, 1.200 pacientes con 1.400 atenciones tardaban un minuto.
     */
    @Transactional
    public Resultado confirmar(UUID loteId) {
        ZoneId zona = identidad.zonaHoraria(AuthUser.current().tenantId());
        List<RegistroImportacion> todos = registros.findByLoteIdOrderByOrden(loteId).stream()
                .filter(r -> r.isIncluido() && r.getEstado() != RegistroImportacion.Estado.ERROR).toList();
        Map<RegistroImportacion, DatosPlantilla> porRegistro = new java.util.LinkedHashMap<>();
        todos.forEach(r -> porRegistro.put(r, mapper.readValue(r.getDatos(), DatosPlantilla.class)));
        // Se vuelve a buscar: entre la revisión y la confirmación alguien pudo crear al paciente o importar las atenciones
        Map<String, Paciente> existentes = pacientes.porNumerosDocumento(todos.stream().map(RegistroImportacion::getNumeroDocumento).toList())
                .stream().collect(Collectors.toMap(p -> p.getTipoDocumento() + " " + p.getNumeroDocumento(), p -> p, (a, b) -> a));
        java.util.Set<String> claves = new java.util.HashSet<>(historia.clavesImportadas(porRegistro.values().stream()
                .flatMap(d -> d.atenciones().stream()).map(AtencionGuardada::clave).toList()));
        em.setFlushMode(FlushModeType.COMMIT);

        int creados = 0;
        int actualizados = 0;
        int atencionesCreadas = 0;
        int yaImportadas = 0;
        Map<UUID, DatosPlantilla> importados = new java.util.LinkedHashMap<>();
        for (Map.Entry<RegistroImportacion, DatosPlantilla> e : porRegistro.entrySet()) {
            RegistroImportacion r = e.getKey();
            DatosPlantilla datos = e.getValue();
            Optional<Paciente> existente = Optional.ofNullable(existentes.get(r.getTipoDocumento() + " " + r.getNumeroDocumento()));
            UUID pacienteId;
            List<Cambio> cambios = new ArrayList<>();
            if (existente.isPresent()) {
                pacienteId = existente.get().getId();
                if (datos.persona() != null) {
                    cambios.addAll(pacientes.completarDesdeListado(pacienteId, datos.persona()));
                }
                if (datos.extras() != null) {
                    cambios.addAll(pacientes.completarExtras(pacienteId, datos.extras()));
                }
                if (!cambios.isEmpty()) {
                    actualizados++;
                }
            } else if (datos.persona() != null && datos.persona().completaParaCrear()) {
                pacienteId = pacientes.crearDesdeListado(r.getTipoDocumento(), r.getNumeroDocumento(), datos.persona(), loteId).getId();
                if (datos.extras() != null) {
                    pacientes.completarExtras(pacienteId, datos.extras());
                }
                creados++;
            } else {
                continue; // ya no existe y no hay datos para crearlo
            }
            r.importadoComo(pacienteId);
            r.cambiarDatos(mapper.writeValueAsString(new DatosPlantilla(datos.fila(), datos.persona(), datos.extras(),
                    datos.atenciones(), cambios)));
            importados.put(pacienteId, datos);
        }
        // Los pacientes van a la base antes que sus atenciones (llave foránea); cada tipo se escribe en lotes JDBC
        em.flush();
        List<UUID> conAtenciones = new ArrayList<>();
        for (Map.Entry<UUID, DatosPlantilla> e : importados.entrySet()) {
            UUID pacienteId = e.getKey();
            int deEste = 0;
            for (AtencionGuardada a : e.getValue().atenciones()) {
                if (!claves.add(a.clave())) {
                    yaImportadas++; // ya estaba en Galenum o venía repetida en el archivo
                    continue;
                }
                List<Diagnostico> dx = a.diagnosticos().stream()
                        .map(d -> new Diagnostico(d.codigo(), d.descripcion(), d.principal(), d.tipo())).toList();
                historia.importarAtencionNueva(pacienteId, a.fecha().atZone(zona).toInstant(), a.contenido(), dx, a.clave(), loteId);
                deEste++;
            }
            atencionesCreadas += deEste;
            if (deEste > 0) {
                conAtenciones.add(pacienteId);
            }
        }
        em.flush();
        conAtenciones.forEach(id -> historia.registrarAcceso(id, Accion.IMPORTAR_ATENCIONES, loteId));
        return new Resultado(creados, actualizados, atencionesCreadas, yaImportadas, 0, 0, 0, 0);
    }

    /**
     * Quita las atenciones del lote, borra los pacientes que creó (si no tienen otra actividad) y devuelve los demás a
     * como estaban, salvo los campos que alguien editó después. Se niega si lo importado ya tiene notas o fórmulas.
     */
    @Transactional
    public Resultado deshacer(UUID loteId, Resultado antes) {
        List<UUID> atencionIds = historia.atencionesDelLote(loteId);
        if (historia.notasEnAtenciones(atencionIds) > 0 || formulas.ligadasA(atencionIds) > 0) {
            throw ApiProblem.conflict("Algunas atenciones importadas ya tienen notas aclaratorias o fórmulas; el lote no se puede deshacer");
        }
        int eliminadas = historia.eliminarAtencionesDelLote(loteId);
        // Las atenciones borradas tienen que llegar a la base antes de contar la actividad de cada paciente; de ahí en
        // adelante, sin flush automático antes de cada consulta (ver confirmar)
        em.flush();
        em.setFlushMode(FlushModeType.COMMIT);
        int borrados = 0;
        int conservados = 0;
        int restaurados = 0;
        List<RegistroImportacion> importados = registros.findByLoteIdOrderByOrden(loteId).stream()
                .filter(r -> r.getPacienteId() != null && r.getEstado() != RegistroImportacion.Estado.ERROR).toList();
        List<UUID> ids = importados.stream().map(RegistroImportacion::getPacienteId).toList();
        // Actividad de todos los pacientes en tres consultas, no cuatro por paciente
        Map<UUID, Paciente> porId = pacientes.porIds(ids).stream().collect(Collectors.toMap(Paciente::getId, p -> p));
        java.util.Set<UUID> conActividad = new java.util.HashSet<>(historia.pacientesConActividad(ids));
        conActividad.addAll(formulas.pacientesConFormulas(ids));
        conActividad.addAll(agenda.pacientesConCitas(ids));
        for (RegistroImportacion r : importados) {
            Optional<Paciente> p = Optional.ofNullable(porId.get(r.getPacienteId()));
            if (p.isEmpty()) {
                continue;
            }
            UUID id = p.get().getId();
            historia.registrarAcceso(id, Accion.DESHACER_IMPORTACION, loteId);
            if (loteId.equals(p.get().getLoteImportacionId())) {
                if (!conActividad.contains(id)) {
                    pacientes.eliminarImportado(id, loteId);
                    borrados++;
                } else {
                    conservados++;
                }
            } else {
                List<Cambio> cambios = mapper.readValue(r.getDatos(), DatosPlantilla.class).cambios();
                if (cambios != null && !cambios.isEmpty()) {
                    pacientes.revertirCambios(id, cambios);
                    restaurados++;
                }
            }
        }
        return new Resultado(antes == null ? 0 : antes.pacientesCreados(), antes == null ? 0 : antes.pacientesActualizados(),
                antes == null ? 0 : antes.atencionesCreadas(), antes == null ? 0 : antes.atencionesYaImportadas(),
                eliminadas, borrados, conservados, restaurados);
    }
}
