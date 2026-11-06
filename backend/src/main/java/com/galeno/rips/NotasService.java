package com.galeno.rips;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.historia.AccesoHistoria.Accion;
import com.galeno.historia.HistoriaService;
import com.galeno.modulos.Modulo;
import com.galeno.modulos.ModulosService;
import com.galeno.pacientes.Paciente;
import com.galeno.pacientes.PacienteService;
import com.galeno.rips.ArmadorNota.HallazgoNota;
import com.galeno.rips.ArmadorNota.ItemNota;
import com.galeno.rips.ArmadorNota.ServicioLote;
import com.galeno.rips.ArmadorNota.Tipo;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Notas crédito, débito y de ajuste de los lotes generados (ver "Notas crédito, débito y de ajuste" en
 * docs/diseno-rips.md). Se arman sobre el JSON guardado del lote; ver o generar una nota queda en la auditoría de la
 * historia de cada paciente afectado, porque lleva diagnósticos.
 */
@Service
public class NotasService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final NotaRepository notas;
    private final LoteRepository lotes;
    private final ReferenciasService referencias;
    private final HistoriaService historia;
    private final PacienteService pacientes;
    private final ModulosService modulos;

    NotasService(NotaRepository notas, LoteRepository lotes, ReferenciasService referencias, HistoriaService historia,
                 PacienteService pacientes, ModulosService modulos) {
        this.notas = notas;
        this.lotes = lotes;
        this.referencias = referencias;
        this.historia = historia;
        this.pacientes = pacientes;
        this.modulos = modulos;
    }

    /** Los servicios del RIPS del lote, con lo que ya acreditaron (NC) o sumaron (ND) sus notas generadas. */
    @Transactional(readOnly = true)
    public List<ServicioVista> servicios(UUID loteId) {
        modulos.exigir(Modulo.RIPS);
        Lote lote = generado(loteId);
        Map<String, BigDecimal> acreditado = sumas(loteId, Tipo.NC, null);
        Map<String, BigDecimal> debitado = sumas(loteId, Tipo.ND, null);
        Map<String, String> nombres = new HashMap<>();
        return ArmadorNota.servicios(json(lote)).stream().map(s -> {
            Map<String, Object> datos = new LinkedHashMap<>();
            ArmadorNota.CORREGIBLES.get(s.lista()).keySet().forEach(c -> datos.put(c, s.registro().get(c)));
            String paciente = nombres.computeIfAbsent(s.tipoDocumento() + " " + s.numeroDocumento(),
                    k -> paciente(s).map(p -> p.getNombres() + " " + p.getApellidos()).orElse(""));
            return new ServicioVista(s.clave(), s.lista(), s.codigo(), s.fecha(), s.tipoDocumento() + " " + s.numeroDocumento(),
                    paciente, s.valor(), acreditado.getOrDefault(s.clave(), BigDecimal.ZERO),
                    debitado.getOrDefault(s.clave(), BigDecimal.ZERO), datos);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<NotaRipsResumen> listar(UUID loteId) {
        modulos.exigir(Modulo.RIPS);
        lote(loteId);
        return notas.findByLoteIdOrderByCreadoEn(loteId).stream().map(n -> new NotaRipsResumen(n.getId(), n.getTipo(),
                n.isTotal(), n.getNumero(), n.getMotivo(), n.getEstado(), total(n), n.getCreadoEn(), n.getGeneradoEn(),
                n.getAnuladoEn())).toList();
    }

    /** @param total NC al 100% (la factura se devolvió completa): queda registrada sin servicios ni RIPS */
    @Transactional
    public NotaRipsVista crear(UUID loteId, Tipo tipo, boolean total, String numero, String motivo) {
        modulos.exigir(Modulo.RIPS);
        Lote lote = generado(loteId);
        if (lote.isSinFactura()) {
            throw ApiProblem.badRequest("Un RIPS sin factura no lleva notas: no hay factura que corregir");
        }
        if (tipo == null) {
            throw ApiProblem.badRequest("Elija el tipo de nota");
        }
        if (total && tipo != Tipo.NC) {
            throw ApiProblem.badRequest("Solo la nota crédito puede ser por el 100% de la factura");
        }
        List<Nota> vigentes = notas.findByLoteIdOrderByCreadoEn(loteId).stream()
                .filter(n -> n.getEstado() != Nota.Estado.ANULADA).toList();
        if (vigentes.stream().anyMatch(Nota::isTotal)) {
            throw ApiProblem.conflict("La factura tiene una nota crédito al 100%: ya no admite otras notas");
        }
        if (total && vigentes.stream().anyMatch(n -> n.getEstado() == Nota.Estado.GENERADA)) {
            throw ApiProblem.conflict("La factura ya tiene notas generadas: la nota crédito al 100% no puede sumarse a ellas");
        }
        String n = numero(numero);
        String m = motivo(motivo);
        if (vigentes.stream().anyMatch(x -> x.getTipo() == tipo && x.getNumero().equals(n))) {
            throw ApiProblem.conflict("Ya hay una nota " + tipo + " con el número " + n + " en este lote");
        }
        Nota nota = notas.saveAndFlush(new Nota(loteId, tipo, total, n, m, AuthUser.current().id()));
        return vista(nota, lote);
    }

    @Transactional
    public NotaRipsVista obtener(UUID notaId) {
        modulos.exigir(Modulo.RIPS);
        Nota nota = nota(notaId);
        NotaRipsVista v = vista(nota, lote(nota.getLoteId()));
        auditar(nota, Accion.VER_RIPS);
        return v;
    }

    @Transactional
    public NotaRipsVista guardar(UUID notaId, String numero, String motivo, List<ItemNota> items) {
        modulos.exigir(Modulo.RIPS);
        Nota nota = nota(notaId);
        if (nota.isTotal()) {
            throw ApiProblem.conflict("La nota crédito al 100% no lleva servicios");
        }
        List<ItemNota> lista = items == null ? List.of() : items;
        if (lista.size() > 2000) {
            throw ApiProblem.badRequest("Demasiados servicios en la nota");
        }
        nota.actualizar(numero(numero), motivo(motivo), JSON.writeValueAsString(lista));
        return vista(notas.saveAndFlush(nota), lote(nota.getLoteId()));
    }

    /** @param contenido {@code null}: quita el XML cargado */
    @Transactional
    public NotaRipsVista cargarXml(UUID notaId, byte[] contenido) {
        modulos.exigir(Modulo.RIPS);
        Nota nota = nota(notaId);
        nota.cargarXml(contenido == null ? null : FacturaXml.leerNota(contenido));
        return vista(notas.saveAndFlush(nota), lote(nota.getLoteId()));
    }

    /** Con algún rechazo no se genera: la respuesta lo dice. */
    @Transactional
    public NotaRipsVista generar(UUID notaId) {
        modulos.exigir(Modulo.RIPS);
        Nota nota = nota(notaId);
        nota.exigirBorrador();
        Lote lote = generado(nota.getLoteId());
        ArmadorNota.Resultado r = armar(nota, lote);
        long rechazos = r.hallazgos().stream().filter(h -> h.nivel() == ArmadorRips.Nivel.RECHAZO).count();
        if (rechazos > 0) {
            throw ApiProblem.badRequest("No se puede generar: hay " + rechazos + (rechazos == 1 ? " problema" : " problemas")
                    + " en la nota. Corríjalos y vuelva a intentar.");
        }
        nota.generar(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(r.json()), AuthUser.current().id());
        notas.saveAndFlush(nota);
        auditar(nota, Accion.GENERAR_RIPS);
        return vista(nota, lote);
    }

    @Transactional
    public NotaRipsVista anular(UUID notaId, String motivo) {
        modulos.exigir(Modulo.RIPS);
        Nota nota = nota(notaId);
        nota.anular(motivo(motivo), AuthUser.current().id());
        return vista(notas.saveAndFlush(nota), lote(nota.getLoteId()));
    }

    @Transactional
    public void eliminar(UUID notaId) {
        modulos.exigir(Modulo.RIPS);
        Nota nota = nota(notaId);
        nota.exigirBorrador();
        notas.delete(nota);
    }

    @Transactional
    public LotesService.Archivo descargar(UUID notaId) {
        modulos.exigir(Modulo.RIPS);
        Nota nota = nota(notaId);
        if (nota.getJson() == null) {
            throw ApiProblem.conflict(nota.isTotal() ? "La nota crédito al 100% no lleva RIPS" : "La nota no se ha generado");
        }
        auditar(nota, Accion.VER_RIPS);
        Lote lote = lote(nota.getLoteId());
        return new LotesService.Archivo("RIPS_" + lote.getNumeroFactura() + "_" + nota.getTipo() + nota.getNumero() + ".json",
                nota.getJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // --- armado ---

    private ArmadorNota.Resultado armar(Nota nota, Lote lote) {
        List<ItemNota> items = items(nota);
        Map<String, Set<String>> codigos = new HashMap<>();
        for (ItemNota i : items) {
            if (i.datos() == null) {
                continue;
            }
            i.datos().forEach((campo, valor) -> {
                String tabla = ArmadorNota.CORREGIBLES.values().stream().filter(m -> m.containsKey(campo))
                        .map(m -> m.get(campo)).findFirst().orElse(null);
                if (tabla != null && valor != null && !valor.isBlank()) {
                    codigos.computeIfAbsent(tabla, k -> new HashSet<>()).add(valor.strip().toUpperCase());
                }
            });
        }
        Map<String, Set<String>> validos = new HashMap<>();
        codigos.forEach((tabla, cods) -> validos.put(tabla, tabla.equals("cie10") ? historia.nombresCie10(cods).keySet()
                : referencias.filas(tabla, cods).entrySet().stream().filter(e -> e.getValue().habilitado())
                        .map(Map.Entry::getKey).collect(Collectors.toSet())));
        return ArmadorNota.armar(new ArmadorNota.Entrada(json(lote), nota.getTipo(), nota.getNumero(), items,
                sumas(lote.getId(), Tipo.NC, nota.getId()), validos, nota.xml()));
    }

    /** Lo que suman, por servicio, las notas generadas de ese tipo (sin contar la nota {@code excepto}). */
    private Map<String, BigDecimal> sumas(UUID loteId, Tipo tipo, UUID excepto) {
        Map<String, BigDecimal> suma = new HashMap<>();
        notas.findByLoteIdOrderByCreadoEn(loteId).stream()
                .filter(n -> n.getTipo() == tipo && !n.isTotal() && n.getEstado() == Nota.Estado.GENERADA
                        && !n.getId().equals(excepto))
                .flatMap(n -> items(n).stream())
                .filter(i -> i.valor() != null)
                .forEach(i -> suma.merge(i.clave(), i.valor(), BigDecimal::add));
        return suma;
    }

    private static List<ItemNota> items(Nota n) {
        return JSON.readValue(n.getServicios(), new TypeReference<List<ItemNota>>() {
        });
    }

    private static Map<String, Object> json(Lote lote) {
        return JSON.readValue(lote.getJson(), new TypeReference<Map<String, Object>>() {
        });
    }

    private Optional<Paciente> paciente(ServicioLote s) {
        return s.tipoDocumento() == null ? Optional.empty() : pacientes.porDocumento(s.tipoDocumento(), s.numeroDocumento());
    }

    /** Ver o generar la nota muestra diagnósticos: queda en la historia de cada paciente afectado. */
    private void auditar(Nota nota, Accion accion) {
        if (nota.isTotal()) {
            return;
        }
        Set<String> claves = items(nota).stream().map(ItemNota::clave).collect(Collectors.toSet());
        ArmadorNota.servicios(json(lote(nota.getLoteId()))).stream().filter(s -> claves.contains(s.clave()))
                .map(this::paciente).flatMap(Optional::stream).map(Paciente::getId).distinct()
                .forEach(p -> historia.registrarAcceso(p, accion, nota.getId()));
    }

    // --- reglas ---

    private Lote lote(UUID id) {
        return lotes.findById(id).orElseThrow(() -> ApiProblem.notFound("Lote no encontrado"));
    }

    private Lote generado(UUID id) {
        Lote lote = lote(id);
        if (lote.getEstado() != Lote.Estado.GENERADO || lote.getJson() == null) {
            throw ApiProblem.conflict("Las notas son de un lote generado");
        }
        return lote;
    }

    private Nota nota(UUID id) {
        return notas.findById(id).orElseThrow(() -> ApiProblem.notFound("Nota no encontrada"));
    }

    private static String numero(String numero) {
        String n = numero == null ? "" : numero.strip().toUpperCase();
        if (!ArmadorNota.NUMERO.matcher(n).matches()) {
            throw ApiProblem.badRequest("Número de la nota: hasta 20 letras, números o guiones");
        }
        return n;
    }

    private static String motivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw ApiProblem.badRequest("Escriba el motivo de la nota");
        }
        return motivo.strip().length() > 300 ? motivo.strip().substring(0, 300) : motivo.strip();
    }

    private static BigDecimal total(Nota n) {
        return n.isTotal() || n.getTipo() == Tipo.NA ? null
                : items(n).stream().map(ItemNota::valor).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO,
                BigDecimal::add);
    }

    private NotaRipsVista vista(Nota n, Lote lote) {
        List<HallazgoNota> hallazgos = List.of();
        if (n.getEstado() == Nota.Estado.BORRADOR && !n.isTotal()) {
            hallazgos = armar(n, lote).hallazgos();
        }
        return new NotaRipsVista(n.getId(), n.getLoteId(), lote.getNumeroFactura(), n.getTipo(), n.isTotal(), n.getNumero(),
                n.getMotivo(), n.getEstado(), new ArrayList<>(items(n)), total(n), n.xml(), hallazgos,
                hallazgos.stream().filter(h -> h.nivel() == ArmadorRips.Nivel.RECHAZO).count(), n.getCreadoEn(),
                n.getGeneradoEn(), n.getAnuladoEn(), n.getMotivoAnulacion());
    }

    // --- vistas ---

    /**
     * @param clave "usuario-lista-consecutivo" del RIPS del lote
     * @param acreditado lo que ya acreditaron las NC generadas; {@code debitado}, lo que sumaron las ND
     * @param datos lo que hoy dice el RIPS en los campos que una nota puede corregir
     */
    public record ServicioVista(String clave, String lista, String codigo, String fecha, String documento,
                                String paciente, BigDecimal valor, BigDecimal acreditado, BigDecimal debitado,
                                Map<String, Object> datos) {
    }

    public record NotaRipsResumen(UUID id, Tipo tipo, boolean total, String numero, String motivo, Nota.Estado estado,
                                  BigDecimal valor, Instant creadoEn, Instant generadoEn, Instant anuladoEn) {
    }

    /** @param rechazos cuántos impiden generar (en borrador) */
    public record NotaRipsVista(UUID id, UUID loteId, String numeroFactura, Tipo tipo, boolean total, String numero,
                                String motivo, Nota.Estado estado, List<ItemNota> servicios, BigDecimal valor,
                                ArmadorNota.XmlNota xml, List<HallazgoNota> hallazgos, long rechazos, Instant creadoEn,
                                Instant generadoEn, Instant anuladoEn, String motivoAnulacion) {
    }
}
