package com.galeno.rips;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.identidad.IdentidadService;
import com.galeno.identidad.IdentidadService.MedicoResumen;
import com.galeno.modulos.Modulo;
import com.galeno.modulos.ModulosService;
import com.galeno.pacientes.PacienteService;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Datos que pide el RIPS y que el resto de Galenum no tiene: del paciente, del consultorio como prestador y del
 * documento de cada profesional. Se pueden guardar incompletos; lo que falta se avisa (y se exigirá al generar el lote).
 */
@Service
public class DatosRipsService {

    private static final String COLOMBIA = ReferenciasService.COLOMBIA;
    /** Documentos de una persona adulta que puede atender (TipoIdPISIS). */
    static final Set<String> DOCUMENTOS_PROFESIONAL = Set.of("CC", "CE", "PA", "PT", "PE", "CD", "DE");

    private final RipsPacienteRepository pacientesRips;
    private final RipsPrestadorRepository prestadores;
    private final RipsProfesionalRepository profesionales;
    private final ReferenciasService referencias;
    private final ModulosService modulos;
    private final PacienteService pacientes;
    private final IdentidadService identidad;
    private final PagadoresService pagadores;

    DatosRipsService(RipsPacienteRepository pacientesRips, RipsPrestadorRepository prestadores,
                     RipsProfesionalRepository profesionales, ReferenciasService referencias, ModulosService modulos,
                     PacienteService pacientes, IdentidadService identidad, PagadoresService pagadores) {
        this.pagadores = pagadores;
        this.pacientesRips = pacientesRips;
        this.prestadores = prestadores;
        this.profesionales = profesionales;
        this.referencias = referencias;
        this.modulos = modulos;
        this.pacientes = pacientes;
        this.identidad = identidad;
    }

    // --- paciente ---

    @Transactional(readOnly = true)
    public PacienteRips paciente(UUID pacienteId) {
        modulos.exigir(Modulo.RIPS);
        pacientes.get(pacienteId);
        return pacientesRips.findById(pacienteId).map(this::vista)
                .orElseGet(() -> vista(new RipsPaciente(pacienteId)));
    }

    /** Sin Colombia como país de residencia, el municipio y la zona no aplican y se borran. */
    @Transactional
    public PacienteRips guardarPaciente(UUID pacienteId, PacienteRips datos) {
        modulos.exigir(Modulo.RIPS);
        pacientes.get(pacienteId);
        String pais = referencias.validar("pais", datos.paisResidencia(), "País de residencia");
        boolean colombia = COLOMBIA.equals(pais);
        RipsPaciente p = pacientesRips.findById(pacienteId).orElseGet(() -> new RipsPaciente(pacienteId));
        p.actualizar(
                referencias.validar("tipo_usuario", datos.tipoUsuario(), "Tipo de usuario"),
                pais,
                colombia ? referencias.validar("municipio", datos.municipioResidencia(), "Municipio de residencia") : null,
                colombia ? referencias.validar("zona", datos.zonaResidencia(), "Zona de residencia") : null,
                referencias.validar("pais", datos.paisOrigen(), "País de origen"),
                datos.pagadorId() == null ? null : pagadores.activo(datos.pagadorId()).getId());
        return vista(pacientesRips.saveAndFlush(p));
    }

    /**
     * Lo que le falta a cada paciente, para avisarlo en la agenda sin abrir ficha por ficha. Un paciente sin registro
     * (o de otro consultorio: el filtro por tenant no lo trae) aparece con todo pendiente.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> faltantes(List<UUID> pacienteIds) {
        modulos.exigir(Modulo.RIPS);
        Map<UUID, RipsPaciente> guardados = pacientesRips.findAllById(pacienteIds).stream()
                .collect(Collectors.toMap(RipsPaciente::getPacienteId, Function.identity()));
        return pacienteIds.stream().distinct().collect(Collectors.toMap(Function.identity(),
                id -> faltantes(guardados.getOrDefault(id, new RipsPaciente(id)))));
    }

    private static List<String> faltantes(RipsPaciente p) {
        List<String> faltan = new ArrayList<>();
        if (p.getTipoUsuario() == null) {
            faltan.add("Tipo de usuario");
        }
        if (p.getPaisResidencia() == null) {
            faltan.add("País de residencia");
        } else if (COLOMBIA.equals(p.getPaisResidencia())) {
            if (p.getMunicipioResidencia() == null) {
                faltan.add("Municipio de residencia");
            }
            if (p.getZonaResidencia() == null) {
                faltan.add("Zona de residencia");
            }
        }
        if (p.getPaisOrigen() == null) {
            faltan.add("País de origen");
        }
        if (p.getPagadorId() == null) {
            faltan.add("Pagador");
        }
        return faltan;
    }

    private PacienteRips vista(RipsPaciente p) {
        List<String> faltan = faltantes(p);
        String municipio = null;
        String departamento = null;
        if (p.getMunicipioResidencia() != null) {
            var m = referencias.listar("municipio", p.getMunicipioResidencia()).stream()
                    .filter(r -> r.codigo().equals(p.getMunicipioResidencia())).findFirst();
            municipio = m.map(ReferenciasService.ReferenciaVista::nombre).orElse(null);
            departamento = m.map(ReferenciasService.ReferenciaVista::detalle).orElse(null);
        }
        return new PacienteRips(p.getTipoUsuario(), p.getPaisResidencia(), p.getMunicipioResidencia(), municipio,
                departamento, p.getZonaResidencia(), p.getPaisOrigen(), p.getPagadorId(),
                p.getPagadorId() == null ? null : pagadores.get(p.getPagadorId()).getNombre(), faltan);
    }

    // --- prestador ---

    @Transactional(readOnly = true)
    public Prestador prestador() {
        modulos.exigir(Modulo.RIPS);
        String nit = nitConsultorio();
        return prestadores.findFirstBy().map(p -> vista(p, nit)).orElse(Prestador.SUGERIDO.conNit(nit));
    }

    @Transactional
    public Prestador guardarPrestador(Prestador datos) {
        modulos.exigir(Modulo.RIPS);
        String codigo = datos.codigoHabilitacion() == null || datos.codigoHabilitacion().isBlank() ? null
                : datos.codigoHabilitacion().strip();
        if (codigo != null && !codigo.matches("\\d{12}")) {
            throw ApiProblem.badRequest("El código de habilitación del prestador tiene 12 dígitos (el código REPS con la sede)");
        }
        String finalidad = referencias.validar("finalidad", datos.finalidad(), "Finalidad");
        referencias.exigirParaConsultas("finalidad", finalidad, "Finalidad");
        String causa = referencias.validar("causa_externa", datos.causaExterna(), "Causa externa");
        referencias.exigirParaConsultas("causa_externa", causa, "Causa externa");
        RipsPrestador p = prestadores.findFirstBy().orElseGet(RipsPrestador::nuevo);
        p.actualizar(codigo,
                referencias.validar("servicio", datos.servicio(), "Servicio"),
                referencias.validar("modalidad_atencion", datos.modalidadAtencion(), "Modalidad de atención"),
                referencias.validar("grupo_servicios", datos.grupoServicios(), "Grupo de servicios"),
                finalidad, causa,
                referencias.validar("via_ingreso", datos.viaIngreso(), "Vía de ingreso"));
        return vista(prestadores.saveAndFlush(p), nitConsultorio());
    }

    /** El NIT del consultorio (numDocumentoIdObligado del RIPS), si no lo dio al registrarse. Ya fijado, no cambia. */
    @Transactional
    public Prestador fijarNit(String nit) {
        modulos.exigir(Modulo.RIPS);
        identidad.fijarNit(AuthUser.current().tenantId(), nit == null ? "" : nit);
        return prestador();
    }

    private String nitConsultorio() {
        return identidad.consultorio(AuthUser.current().tenantId()).nit();
    }

    private static Prestador vista(RipsPrestador p, String nit) {
        return new Prestador(p.getCodigoHabilitacion(), p.getServicio(), p.getModalidadAtencion(), p.getGrupoServicios(),
                p.getFinalidad(), p.getCausaExterna(), p.getViaIngreso(), true, nit);
    }

    // --- profesionales ---

    /** Los médicos activos del consultorio, con el documento que tengan registrado para el RIPS. */
    @Transactional(readOnly = true)
    public List<Profesional> profesionales() {
        modulos.exigir(Modulo.RIPS);
        Map<UUID, RipsProfesional> docs = profesionales.findAll().stream()
                .collect(Collectors.toMap(RipsProfesional::getUsuarioId, Function.identity()));
        return identidad.medicosActivos().stream().map(m -> {
            RipsProfesional d = docs.get(m.usuarioId());
            return new Profesional(m.usuarioId(), m.nombre(), d == null ? null : d.getTipoDocumento(),
                    d == null ? null : d.getNumeroDocumento());
        }).toList();
    }

    @Transactional
    public Profesional guardarProfesional(UUID usuarioId, String tipoDocumento, String numeroDocumento) {
        modulos.exigir(Modulo.RIPS);
        MedicoResumen medico = identidad.medicosActivos().stream().filter(m -> m.usuarioId().equals(usuarioId))
                .findFirst().orElseThrow(() -> ApiProblem.notFound("No es un médico activo de este consultorio"));
        String tipo = tipoDocumento == null ? "" : tipoDocumento.strip().toUpperCase();
        if (!DOCUMENTOS_PROFESIONAL.contains(tipo)) {
            throw ApiProblem.badRequest("Tipo de documento no válido para un profesional: use CC, CE, PA, PT, PE, CD o DE");
        }
        String numero = numeroDocumento == null ? "" : numeroDocumento.strip();
        if (!numero.matches("[A-Za-z0-9]{4,20}")) {
            throw ApiProblem.badRequest("El número de documento debe tener entre 4 y 20 letras o números, sin puntos ni espacios");
        }
        RipsProfesional p = profesionales.findByUsuarioId(usuarioId).orElseGet(() -> new RipsProfesional(usuarioId));
        p.actualizar(tipo, numero);
        profesionales.saveAndFlush(p);
        return new Profesional(usuarioId, medico.nombre(), tipo, numero);
    }

    // --- vistas ---

    /**
     * @param municipioNombre y {@code departamento}: para mostrar (se ignoran al guardar)
     * @param faltantes lo que el RIPS pedirá y todavía no está
     */
    public record PacienteRips(String tipoUsuario, String paisResidencia, String municipioResidencia,
                               String municipioNombre, String departamento, String zonaResidencia, String paisOrigen,
                               UUID pagadorId, String pagadorNombre, List<String> faltantes) {
    }

    /**
     * @param guardado falso: el consultorio no lo ha configurado y estos son los valores sugeridos
     * @param nit el del consultorio (de solo lectura aquí: se fija aparte, una vez)
     */
    public record Prestador(String codigoHabilitacion, String servicio, String modalidadAtencion, String grupoServicios,
                            String finalidad, String causaExterna, String viaIngreso, boolean guardado, String nit) {

        /** Intramural, consulta externa, enfermedad general y derivado de consulta externa (lo más común). */
        static final Prestador SUGERIDO = new Prestador(null, null, "01", "01", null, "38", "02", false, null);

        Prestador conNit(String nit) {
            return new Prestador(codigoHabilitacion, servicio, modalidadAtencion, grupoServicios, finalidad, causaExterna,
                    viaIngreso, guardado, nit);
        }
    }

    public record Profesional(UUID usuarioId, String nombre, String tipoDocumento, String numeroDocumento) {
    }
}
