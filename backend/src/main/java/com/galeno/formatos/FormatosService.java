package com.galeno.formatos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Formatos clínicos: catálogo, activación por consultorio, formatos por defecto de cada profesional y formatos
 * diligenciados en las atenciones. No sabe de ninguna especialidad: todo sale de las definiciones.
 *
 * <p>Las reglas de la atención (solo su autor, solo mientras es borrador) las aplica {@code HistoriaService} antes de
 * llamar a los métodos que reciben un {@code atencionId}.
 */
@Service
public class FormatosService {

    static final int MAX_POR_ATENCION = 10;

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };

    private final FormatoRepository formatos;
    private final FormatoVersionRepository versiones;
    private final FormatoConsultorioRepository activos;
    private final FormatoProfesionalRepository delProfesional;
    private final AtencionFormatoRepository diligenciados;

    /** Las versiones no cambian nunca: su definición leída se puede guardar para siempre. */
    private final Map<UUID, Version> cache = new ConcurrentHashMap<>();

    FormatosService(FormatoRepository formatos, FormatoVersionRepository versiones, FormatoConsultorioRepository activos,
                    FormatoProfesionalRepository delProfesional, AtencionFormatoRepository diligenciados) {
        this.formatos = formatos;
        this.versiones = versiones;
        this.activos = activos;
        this.delProfesional = delProfesional;
        this.diligenciados = diligenciados;
    }

    // --- catálogo y activación (administrador) ---

    @Transactional(readOnly = true)
    public List<FormatoCatalogo> catalogo() {
        Set<UUID> activosAqui = activos.findAll().stream().map(FormatoConsultorio::getFormatoId).collect(Collectors.toSet());
        return formatos.findAll().stream()
                .sorted(Comparator.comparing(Formato::getEspecialidad).thenComparing(Formato::getNombre))
                .map(f -> {
                    Version v = ultima(f.getId());
                    return new FormatoCatalogo(f.getId(), f.getCodigo(), f.getNombre(), f.getEspecialidad(),
                            f.getDescripcion(), v.version(), v.definicion().secciones().stream()
                                    .map(DefinicionFormato.Seccion::titulo).toList(), activosAqui.contains(f.getId()));
                }).toList();
    }

    /** Desactivar no toca lo ya diligenciado: solo impide agregarlo a atenciones nuevas. */
    @Transactional
    public FormatoCatalogo activar(UUID formatoId, boolean activo) {
        formato(formatoId);
        var actual = activos.findByFormatoId(formatoId);
        if (activo && actual.isEmpty()) {
            activos.save(new FormatoConsultorio(formatoId));
        } else if (!activo) {
            actual.ifPresent(activos::delete);
        }
        activos.flush();
        return catalogo().stream().filter(f -> f.formatoId().equals(formatoId)).findFirst().orElseThrow();
    }

    // --- formatos del profesional ---

    /** Los activos en el consultorio. Si no hay ninguno, la atención se ve exactamente como siempre. */
    @Transactional(readOnly = true)
    public List<FormatoDisponible> disponibles() {
        Set<UUID> mios = delProfesional.findByUsuarioId(AuthUser.current().id()).stream()
                .map(FormatoProfesional::getFormatoId).collect(Collectors.toSet());
        return activos.findAll().stream()
                .map(a -> formato(a.getFormatoId()))
                .sorted(Comparator.comparing(Formato::getNombre))
                .map(f -> new FormatoDisponible(f.getId(), f.getCodigo(), f.getNombre(), f.getEspecialidad(), mios.contains(f.getId())))
                .toList();
    }

    @Transactional
    public FormatoDisponible porDefecto(UUID formatoId, boolean porDefecto) {
        Formato f = formato(formatoId);
        UUID yo = AuthUser.current().id();
        var actual = delProfesional.findByUsuarioIdAndFormatoId(yo, formatoId);
        if (porDefecto && actual.isEmpty()) {
            exigirActivo(formatoId);
            delProfesional.save(new FormatoProfesional(yo, formatoId));
        } else if (!porDefecto) {
            actual.ifPresent(delProfesional::delete);
        }
        return new FormatoDisponible(f.getId(), f.getCodigo(), f.getNombre(), f.getEspecialidad(), porDefecto);
    }

    // --- formatos de una atención ---

    @Transactional(readOnly = true)
    public List<FormatoDiligenciado> deAtencion(UUID atencionId) {
        return diligenciados.findByAtencionIdOrderByOrden(atencionId).stream().map(this::diligenciado).toList();
    }

    /**
     * Al crear la atención: los formatos por defecto del profesional que sigan activos en el consultorio.
     *
     * @param firmadasDelPaciente sus atenciones firmadas, de la más reciente a la más antigua (para "arrastrar")
     */
    @Transactional
    public void agregarPorDefecto(UUID atencionId, UUID usuarioId, List<UUID> firmadasDelPaciente) {
        Set<UUID> activosAqui = activos.findAll().stream().map(FormatoConsultorio::getFormatoId).collect(Collectors.toSet());
        delProfesional.findByUsuarioId(usuarioId).stream()
                .map(FormatoProfesional::getFormatoId)
                .filter(activosAqui::contains)
                .map(this::formato)
                .sorted(Comparator.comparing(Formato::getNombre))
                .forEach(f -> agregar(atencionId, f.getId(), firmadasDelPaciente));
    }

    /**
     * Con la última versión publicada. Cada formato va una sola vez por atención. Los campos con {@code arrastrar}
     * parten de la atención firmada más reciente del paciente que tenga este formato (en cualquier versión).
     *
     * @param firmadasDelPaciente sus atenciones firmadas, de la más reciente a la más antigua
     */
    @Transactional
    public void agregar(UUID atencionId, UUID formatoId, List<UUID> firmadasDelPaciente) {
        formato(formatoId);
        exigirActivo(formatoId);
        List<AtencionFormato> actuales = diligenciados.findByAtencionIdOrderByOrden(atencionId);
        if (actuales.stream().anyMatch(af -> version(af.getFormatoVersionId()).formatoId().equals(formatoId))) {
            throw ApiProblem.conflict("Este formato ya está en la atención");
        }
        if (actuales.size() >= MAX_POR_ATENCION) {
            throw ApiProblem.badRequest("Una atención admite hasta " + MAX_POR_ATENCION + " formatos");
        }
        int orden = actuales.stream().mapToInt(AtencionFormato::getOrden).max().orElse(-1) + 1;
        Version v = ultima(formatoId);
        Arrastre arrastre = arrastre(v, firmadasDelPaciente);
        diligenciados.save(new AtencionFormato(atencionId, v.id(), orden,
                DefinicionFormato.JSON.writeValueAsString(arrastre.respuestas()), arrastre.desde()));
    }

    /** Los campos con "arrastrar" tomados del mismo formato en la firmada más reciente; nada si no hay. */
    private Arrastre arrastre(Version v, List<UUID> firmadas) {
        boolean hayQueArrastrar = v.definicion().secciones().stream().flatMap(s -> s.campos().stream())
                .anyMatch(DefinicionFormato.Campo::arrastrar);
        if (!hayQueArrastrar || firmadas.isEmpty()) {
            return Arrastre.NADA;
        }
        Map<UUID, AtencionFormato> delFormato = new HashMap<>();
        diligenciados.findByAtencionIdInOrderByOrden(firmadas).stream()
                .filter(af -> version(af.getFormatoVersionId()).formatoId().equals(v.formatoId()))
                .forEach(af -> delFormato.put(af.getAtencionId(), af));
        AtencionFormato previo = firmadas.stream().map(delFormato::get).filter(Objects::nonNull).findFirst().orElse(null);
        if (previo == null) {
            return Arrastre.NADA;
        }
        JsonNode anteriores = leer(previo.getRespuestas());
        ObjectNode copia = DefinicionFormato.JSON.createObjectNode();
        for (DefinicionFormato.Seccion s : v.definicion().secciones()) {
            for (DefinicionFormato.Campo c : s.campos()) {
                JsonNode valor = anteriores.path(s.clave()).get(c.clave());
                if (!c.arrastrar() || valor == null) {
                    continue;
                }
                // La versión nueva puede haber cambiado el campo: si el valor ya no es válido, no se arrastra
                ObjectNode prueba = DefinicionFormato.JSON.createObjectNode();
                prueba.putObject(s.clave()).set(c.clave(), valor);
                try {
                    ValidadorRespuestas.normalizar(v.nombre(), v.definicion(), prueba);
                } catch (ApiProblem e) {
                    continue;
                }
                ObjectNode seccion = copia.has(s.clave()) ? (ObjectNode) copia.get(s.clave()) : copia.putObject(s.clave());
                seccion.set(c.clave(), valor);
            }
        }
        return copia.isEmpty() ? Arrastre.NADA : new Arrastre(copia, previo.getAtencionId());
    }

    private record Arrastre(JsonNode respuestas, UUID desde) {
        static final Arrastre NADA = new Arrastre(DefinicionFormato.JSON.createObjectNode(), null);
    }

    @Transactional
    public void quitar(UUID atencionId, UUID atencionFormatoId) {
        diligenciados.delete(deLaAtencion(atencionId, atencionFormatoId));
    }

    /** Valida y guarda las respuestas (limpias: sin vacíos ni campos ocultos). Los formatos que no vienen no cambian. */
    @Transactional
    public void guardar(UUID atencionId, List<RespuestasFormato> respuestas) {
        for (RespuestasFormato r : respuestas) {
            AtencionFormato af = deLaAtencion(atencionId, r.id());
            Version v = version(af.getFormatoVersionId());
            JsonNode recibidas = r.respuestas() == null ? null : DefinicionFormato.JSON.valueToTree(r.respuestas());
            ObjectNode limpias = ValidadorRespuestas.normalizar(v.nombre(), v.definicion(), recibidas);
            ValidadorRespuestas.conservarFechasDeFirma(v.definicion(), limpias, leer(af.getRespuestas()));
            af.responder(DefinicionFormato.JSON.writeValueAsString(limpias));
        }
    }

    /** Obligatorios sin diligenciar, como «Formato › Sección › Campo». Vacío = se puede firmar. */
    @Transactional(readOnly = true)
    public List<String> faltantesParaFirmar(UUID atencionId) {
        List<String> faltan = new ArrayList<>();
        for (AtencionFormato af : diligenciados.findByAtencionIdOrderByOrden(atencionId)) {
            Version v = version(af.getFormatoVersionId());
            ValidadorRespuestas.faltantes(v.definicion(), leer(af.getRespuestas()))
                    .forEach(f -> faltan.add(v.nombre() + " › " + f));
        }
        return faltan;
    }

    /** Al borrar un borrador. */
    @Transactional
    public void eliminarDeAtencion(UUID atencionId) {
        diligenciados.deleteAll(diligenciados.findByAtencionIdOrderByOrden(atencionId));
        diligenciados.flush();
    }

    /** XHTML de los formatos diligenciados de cada atención (para el PDF); las que no tienen no aparecen. */
    @Transactional(readOnly = true)
    public Map<UUID, String> htmlPorAtencion(Collection<UUID> atencionIds) {
        Map<UUID, StringBuilder> html = new LinkedHashMap<>();
        if (atencionIds.isEmpty()) {
            return Map.of();
        }
        for (AtencionFormato af : diligenciados.findByAtencionIdInOrderByOrden(atencionIds)) {
            Version v = version(af.getFormatoVersionId());
            String h = PresentacionFormato.html(v.nombre(), v.definicion(), leer(af.getRespuestas()));
            if (!h.isEmpty()) {
                html.computeIfAbsent(af.getAtencionId(), k -> new StringBuilder()).append(h);
            }
        }
        return html.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
    }

    /**
     * El formato de la atención como documento propio (su PDF), si su definición lo marca así (incapacidad, remisión,
     * consentimiento…). Vacío si es un formato que solo vive en la historia (p. ej. el odontograma).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<DocumentoFormato> documento(UUID atencionId, UUID atencionFormatoId) {
        AtencionFormato af = deLaAtencion(atencionId, atencionFormatoId);
        Version v = version(af.getFormatoVersionId());
        DefinicionFormato.Documento doc = v.definicion().documento();
        if (doc == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new DocumentoFormato(v.nombre(), doc.titulo(), doc.diagnosticos(),
                PresentacionFormato.html(v.nombre(), v.definicion(), leer(af.getRespuestas()))));
    }

    /** Códigos de los formatos de la atención (p. ej. "general.incapacidad"). */
    @Transactional(readOnly = true)
    public java.util.Set<String> codigos(UUID atencionId) {
        return diligenciados.findByAtencionIdOrderByOrden(atencionId).stream()
                .map(af -> version(af.getFormatoVersionId()).codigo()).collect(Collectors.toSet());
    }

    // --- internos ---

    private FormatoDiligenciado diligenciado(AtencionFormato af) {
        Version v = version(af.getFormatoVersionId());
        return new FormatoDiligenciado(af.getId(), v.formatoId(), v.codigo(), v.nombre(), v.version(),
                DefinicionFormato.JSON.convertValue(v.json(), MAPA),
                DefinicionFormato.JSON.readValue(af.getRespuestas(), MAPA), af.getArrastradoDe());
    }

    private AtencionFormato deLaAtencion(UUID atencionId, UUID atencionFormatoId) {
        return diligenciados.findById(atencionFormatoId)
                .filter(af -> af.getAtencionId().equals(atencionId))
                .orElseThrow(() -> ApiProblem.notFound("El formato no está en esta atención"));
    }

    private void exigirActivo(UUID formatoId) {
        if (!activos.existsByFormatoId(formatoId)) {
            throw ApiProblem.conflict("Este formato no está activo en el consultorio");
        }
    }

    private Formato formato(UUID formatoId) {
        return formatos.findById(formatoId).orElseThrow(() -> ApiProblem.notFound("Formato no encontrado"));
    }

    private Version ultima(UUID formatoId) {
        FormatoVersion fv = versiones.findFirstByFormatoIdOrderByVersionDesc(formatoId)
                .orElseThrow(() -> new IllegalStateException("Formato sin versiones: " + formatoId));
        return version(fv.getId());
    }

    private Version version(UUID versionId) {
        return cache.computeIfAbsent(versionId, id -> {
            FormatoVersion fv = versiones.findById(id).orElseThrow(() -> new IllegalStateException("Versión inexistente: " + id));
            Formato f = formato(fv.getFormatoId());
            JsonNode json = leer(fv.getDefinicion());
            return new Version(fv.getId(), f.getId(), f.getCodigo(), f.getNombre(), fv.getVersion(), json,
                    DefinicionFormato.leer(json));
        });
    }

    private static JsonNode leer(String json) {
        return DefinicionFormato.JSON.readTree(json);
    }

    private record Version(UUID id, UUID formatoId, String codigo, String nombre, int version, JsonNode json,
                           DefinicionFormato definicion) {
    }

    // --- vistas ---

    /** @param secciones títulos de las secciones de la última versión, para que el administrador sepa qué trae */
    public record FormatoCatalogo(UUID formatoId, String codigo, String nombre, String especialidad, String descripcion,
                                  int version, List<String> secciones, boolean activo) {
    }

    /** @param porDefecto se agrega solo a las atenciones nuevas de quien consulta */
    public record FormatoDisponible(UUID formatoId, String codigo, String nombre, String especialidad,
                                    boolean porDefecto) {
    }

    /**
     * @param id el del formato dentro de esta atención
     * @param definicion secciones y campos de la versión con la que se diligenció (ver docs/diseno-formatos.md)
     * @param respuestas {@code {"seccion": {"campo": valor}}}
     * @param arrastradoDe la atención firmada de la que partieron los campos con "arrastrar" (p. ej. el odontograma)
     */
    public record FormatoDiligenciado(UUID id, UUID formatoId, String codigo, String nombre, int version,
                                      Map<String, Object> definicion, Map<String, Object> respuestas,
                                      UUID arrastradoDe) {
    }

    /** @param html el formato diligenciado, en XHTML para el PDF */
    public record DocumentoFormato(String nombre, String titulo, boolean diagnosticos, String html) {
    }

    /** @param id el del formato dentro de la atención */
    public record RespuestasFormato(UUID id, Map<String, Object> respuestas) {
    }
}
