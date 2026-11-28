package com.galeno.tratamiento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.formatos.FormatosService;
import com.galeno.historia.HistoriaService;
import com.galeno.historia.ListaPrecios;
import com.galeno.historia.Procedimiento;
import com.galeno.identidad.IdentidadService;
import com.galeno.pacientes.PacienteService;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Planes de tratamiento: crear, editar los ítems mientras no estén aceptados, presentar, aceptar (la primera vez y
 * los adicionales) y cerrar. Solo con el odontograma activo en el consultorio.
 */
@Service
public class TratamientosService {

    /** El plan es de odontología: aparece cuando el consultorio usa el odontograma. */
    static final String FORMATO_REQUERIDO = "odonto.odontograma";
    static final int MAX_FASES = 20;
    static final int MAX_ITEMS = 200;
    static final int MAX_VIGENCIA = 365;
    static final String PREFIJO_FIRMA = "data:image/png;base64,";
    static final int MAX_FIRMA = 400_000;

    private static final EnumSet<PlanTratamiento.Estado> VIGENTES = EnumSet.of(PlanTratamiento.Estado.BORRADOR,
            PlanTratamiento.Estado.PRESENTADO, PlanTratamiento.Estado.ACEPTADO);

    private final PlanRepository planes;
    private final ItemPlanRepository items;
    private final AceptacionPlanRepository aceptaciones;
    private final FormatosService formatos;
    private final PacienteService pacientes;
    private final HistoriaService historia;
    private final ListaPrecios precios;
    private final IdentidadService identidad;

    TratamientosService(PlanRepository planes, ItemPlanRepository items, AceptacionPlanRepository aceptaciones,
                        FormatosService formatos, PacienteService pacientes, HistoriaService historia,
                        ListaPrecios precios, IdentidadService identidad) {
        this.planes = planes;
        this.items = items;
        this.aceptaciones = aceptaciones;
        this.formatos = formatos;
        this.pacientes = pacientes;
        this.historia = historia;
        this.precios = precios;
        this.identidad = identidad;
    }

    // --- lectura ---

    @Transactional(readOnly = true)
    public List<PlanTratamientoVista> delPaciente(UUID pacienteId) {
        exigirFormato();
        pacientes.get(pacienteId);
        return planes.findByPacienteIdOrderByCreadoEnDesc(pacienteId).stream().map(this::vista).toList();
    }

    @Transactional(readOnly = true)
    public PlanTratamientoVista ver(UUID id) {
        exigirFormato();
        return vista(plan(id));
    }

    /**
     * Lo que sugiere el último odontograma firmado del paciente, con el precio de la lista si lo hay. No guarda nada:
     * el odontólogo elige qué agregar.
     */
    @Transactional(readOnly = true)
    public SugerenciasPlan sugerencias(UUID pacienteId) {
        exigirFormato();
        pacientes.get(pacienteId);
        var ultimo = formatos.ultimasRespuestas(FORMATO_REQUERIDO, historia.atencionesFirmadasRecientes(pacienteId));
        if (ultimo.isEmpty()) {
            return new SugerenciasPlan(null, List.of());
        }
        List<SugerenciasOdontograma.Sugerencia> s = SugerenciasOdontograma.de(ultimo.get().respuestas());
        List<String> codigos = s.stream().map(SugerenciasOdontograma.Sugerencia::codigoCups).distinct().toList();
        Map<String, String> nombres = historia.nombresCups(codigos);
        Map<String, BigDecimal> lista = precios.precios(codigos, new ListaPrecios.Contexto(pacienteId, null, null));
        return new SugerenciasPlan(ultimo.get().atencionId(), s.stream()
                .map(x -> new SugerenciaVista(x.fase(), x.fase() == 1 ? "Operatoria" : "Cirugía", x.codigoCups(),
                        nombres.getOrDefault(x.codigoCups(), x.codigoCups()), x.diente(), x.superficies(), 1,
                        lista.get(x.codigoCups()), x.motivo()))
                .toList());
    }

    /** @param atencionId la atención del odontograma del que salen; nula si el paciente no tiene odontograma firmado */
    public record SugerenciasPlan(UUID atencionId, List<SugerenciaVista> sugerencias) {
    }

    /** @param valorUnitario nulo si el CUPS no tiene precio en la lista */
    public record SugerenciaVista(int fase, String faseNombre, String codigoCups, String descripcion, String diente,
                                  String superficies, int cantidad, BigDecimal valorUnitario, String motivo) {
    }

    /**
     * Planes aceptados con procedimientos pendientes y sin actividad (aceptación o procedimiento realizado) en los
     * últimos {@code dias}: para que la secretaría llame al paciente. Los más olvidados primero.
     */
    @Transactional(readOnly = true)
    public List<SeguimientoPlan> sinActividad(int dias) {
        exigirFormato();
        if (dias < 1 || dias > 365) {
            throw ApiProblem.badRequest("Los días deben estar entre 1 y 365");
        }
        Instant limite = Instant.now().minus(java.time.Duration.ofDays(dias));
        List<PlanTratamiento> aceptados = planes.findByEstado(PlanTratamiento.Estado.ACEPTADO);
        if (aceptados.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = aceptados.stream().map(PlanTratamiento::getId).toList();
        Map<UUID, List<ItemPlan>> itemsPorPlan = items.findByPlanIdIn(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(ItemPlan::getPlanId));
        Map<UUID, Instant> ultimaAceptacion = aceptaciones.findByPlanIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(AceptacionPlan::getPlanId, AceptacionPlan::getAceptadoEn,
                        (a, b) -> a.isAfter(b) ? a : b));
        record Candidato(PlanTratamiento plan, Instant ultima, List<ItemPlan> pendientes) {
        }
        List<Candidato> candidatos = new java.util.ArrayList<>();
        for (PlanTratamiento p : aceptados) {
            List<ItemPlan> delPlan = itemsPorPlan.getOrDefault(p.getId(), List.of());
            List<ItemPlan> pendientes = delPlan.stream()
                    .filter(i -> i.aceptado() && i.getEstado() == ItemPlan.Estado.PENDIENTE).toList();
            Instant ultima = delPlan.stream().map(ItemPlan::getRealizadoEn).filter(java.util.Objects::nonNull)
                    .reduce(ultimaAceptacion.getOrDefault(p.getId(), p.getActualizadoEn()), (a, b) -> a.isAfter(b) ? a : b);
            if (!pendientes.isEmpty() && ultima.isBefore(limite)) {
                candidatos.add(new Candidato(p, ultima, pendientes));
            }
        }
        Map<UUID, com.galeno.pacientes.Paciente> pacientesPorId = pacientes.porIds(candidatos.stream()
                .map(c -> c.plan().getPacienteId()).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(com.galeno.pacientes.Paciente::getId, x -> x));
        return candidatos.stream().sorted(java.util.Comparator.comparing(Candidato::ultima)).map(c -> {
            var pa = pacientesPorId.get(c.plan().getPacienteId());
            return new SeguimientoPlan(c.plan().getId(), c.plan().getPacienteId(),
                    pa == null ? "" : pa.getNombres() + " " + pa.getApellidos(), pa == null ? null : pa.getTelefono(),
                    identidad.profesional(c.plan().getOdontologoId()).nombre(), c.pendientes().size(),
                    c.pendientes().stream().map(ItemPlan::total).reduce(BigDecimal.ZERO, BigDecimal::add), c.ultima());
        }).toList();
    }

    public record SeguimientoPlan(UUID planId, UUID pacienteId, String paciente, String telefono, String odontologo,
                                  int pendientes, BigDecimal valorPendiente, Instant ultimaActividad) {
    }

    // --- el plan ---

    @Transactional
    public PlanTratamientoVista crear(UUID pacienteId, String observaciones, Integer vigenciaDias) {
        exigirFormato();
        pacientes.get(pacienteId);
        if (planes.existsByPacienteIdAndEstadoIn(pacienteId, VIGENTES)) {
            throw ApiProblem.conflict("El paciente ya tiene un plan de tratamiento vigente: termínelo o cancélelo antes de crear otro");
        }
        PlanTratamiento p = planes.save(new PlanTratamiento(pacienteId, AuthUser.current().id(), texto(observaciones),
                vigencia(vigenciaDias)));
        return vista(p);
    }

    @Transactional
    public PlanTratamientoVista datos(UUID id, String observaciones, Integer vigenciaDias) {
        PlanTratamiento p = vigente(id);
        p.datos(texto(observaciones), vigencia(vigenciaDias));
        return vista(p);
    }

    /** Genera el presupuesto para el paciente: desde aquí se puede aceptar. */
    @Transactional
    public PlanTratamientoVista presentar(UUID id) {
        PlanTratamiento p = vigente(id);
        if (p.getEstado() != PlanTratamiento.Estado.BORRADOR) {
            throw ApiProblem.conflict("Solo se presenta un plan en borrador");
        }
        if (items.findByPlanIdOrderByFaseAscOrdenAsc(id).isEmpty()) {
            throw ApiProblem.badRequest("Agregue al menos un procedimiento antes de presentar el plan");
        }
        p.presentar();
        return vista(p);
    }

    @Transactional
    public PlanTratamientoVista volverABorrador(UUID id) {
        PlanTratamiento p = vigente(id);
        if (p.getEstado() != PlanTratamiento.Estado.PRESENTADO) {
            throw ApiProblem.conflict("Solo un plan presentado y aún sin aceptar vuelve a borrador");
        }
        p.volverABorrador();
        return vista(p);
    }

    /**
     * El paciente acepta lo que aún no estaba aceptado: la primera vez, todo el plan presentado; después, los
     * adicionales. Con firma en pantalla o, sin ella, como constancia verbal registrada por quien atiende.
     */
    @Transactional
    public PlanTratamientoVista aceptar(UUID id, String firma, String nombreFirmante, boolean constanciaVerbal) {
        PlanTratamiento p = vigente(id);
        if (p.getEstado() == PlanTratamiento.Estado.BORRADOR) {
            throw ApiProblem.conflict("Presente el plan al paciente antes de registrar la aceptación");
        }
        List<ItemPlan> porAceptar = items.findByPlanIdOrderByFaseAscOrdenAsc(id).stream()
                .filter(Predicate.not(ItemPlan::aceptado)).toList();
        if (porAceptar.isEmpty()) {
            throw ApiProblem.conflict("No hay procedimientos por aceptar en este plan");
        }
        String imagen = firma(firma);
        if (imagen == null && !constanciaVerbal) {
            throw ApiProblem.badRequest("Falta la firma del paciente (o marque la aceptación verbal)");
        }
        String nombre = texto(nombreFirmante);
        if (nombre != null && nombre.length() > 120) {
            throw ApiProblem.badRequest("El nombre de quien firma admite 120 caracteres");
        }
        int numero = aceptaciones.findByPlanIdOrderByNumeroAsc(id).size() + 1;
        BigDecimal total = porAceptar.stream().map(ItemPlan::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        aceptaciones.save(new AceptacionPlan(id, numero, AuthUser.current().id(), imagen, nombre, total));
        porAceptar.forEach(i -> i.aceptar(numero));
        p.aceptar();
        return vista(p);
    }

    /** Cierre manual: el tratamiento terminó aunque queden procedimientos sin hacer (se cancelan con el motivo). */
    @Transactional
    public PlanTratamientoVista terminar(UUID id, String motivo) {
        PlanTratamiento p = vigente(id);
        if (p.getEstado() != PlanTratamiento.Estado.ACEPTADO) {
            throw ApiProblem.conflict("Solo se termina un plan aceptado; uno sin aceptar se cancela");
        }
        String m = motivoObligatorio(motivo);
        cerrarPendientes(id, m);
        p.cerrar(PlanTratamiento.Estado.TERMINADO, m);
        return vista(p);
    }

    @Transactional
    public PlanTratamientoVista cancelar(UUID id, String motivo) {
        PlanTratamiento p = vigente(id);
        String m = motivoObligatorio(motivo);
        cerrarPendientes(id, m);
        p.cerrar(PlanTratamiento.Estado.CANCELADO, m);
        return vista(p);
    }

    // --- los ítems ---

    @Transactional
    public PlanTratamientoVista agregarItem(UUID id, ItemDatos d) {
        PlanTratamiento p = vigente(id);
        List<ItemPlan> actuales = items.findByPlanIdOrderByFaseAscOrdenAsc(id);
        if (actuales.size() >= MAX_ITEMS) {
            throw ApiProblem.badRequest("El plan admite hasta " + MAX_ITEMS + " procedimientos");
        }
        int orden = actuales.stream().filter(i -> i.getFase() == d.fase()).mapToInt(ItemPlan::getOrden).max().orElse(0) + 1;
        items.save(new ItemPlan(id, normalizar(p, d, orden)));
        alEditar(p);
        return vista(p);
    }

    @Transactional
    public PlanTratamientoVista editarItem(UUID id, UUID itemId, ItemDatos d) {
        PlanTratamiento p = vigente(id);
        ItemPlan i = item(id, itemId);
        if (i.aceptado()) {
            throw ApiProblem.conflict("Este procedimiento ya fue aceptado por el paciente: no se edita (cancélelo y agregue otro como adicional)");
        }
        int orden = i.getFase() == d.fase() ? i.getOrden()
                : items.findByPlanIdOrderByFaseAscOrdenAsc(id).stream().filter(x -> x.getFase() == d.fase())
                        .mapToInt(ItemPlan::getOrden).max().orElse(0) + 1;
        i.poner(normalizar(p, d, orden));
        alEditar(p);
        return vista(p);
    }

    @Transactional
    public PlanTratamientoVista quitarItem(UUID id, UUID itemId) {
        PlanTratamiento p = vigente(id);
        ItemPlan i = item(id, itemId);
        if (i.aceptado()) {
            throw ApiProblem.conflict("Este procedimiento ya fue aceptado: no se quita, se cancela con un motivo");
        }
        items.delete(i);
        alEditar(p);
        return vista(p);
    }

    /** Un procedimiento aceptado que ya no se hará. */
    @Transactional
    public PlanTratamientoVista cancelarItem(UUID id, UUID itemId, String motivo) {
        PlanTratamiento p = vigente(id);
        ItemPlan i = item(id, itemId);
        if (!i.aceptado()) {
            throw ApiProblem.conflict("Un procedimiento sin aceptar se quita, no se cancela");
        }
        if (i.getEstado() != ItemPlan.Estado.PENDIENTE) {
            throw ApiProblem.conflict("Solo se cancela un procedimiento pendiente");
        }
        i.cancelar(motivoObligatorio(motivo));
        p.tocar();
        revisarTerminado(p);
        return vista(p);
    }

    // --- para el PDF ---

    @Transactional(readOnly = true)
    Presupuesto presupuesto(UUID id) {
        exigirFormato();
        PlanTratamiento p = plan(id);
        return new Presupuesto(p, items.findByPlanIdOrderByFaseAscOrdenAsc(id), aceptaciones.findByPlanIdOrderByNumeroAsc(id));
    }

    record Presupuesto(PlanTratamiento plan, List<ItemPlan> items, List<AceptacionPlan> aceptaciones) {
    }

    // --- internos ---

    /** Editar un plan presentado lo devuelve a borrador: el presupuesto que vio el paciente ya no es ese. */
    private void alEditar(PlanTratamiento p) {
        if (p.getEstado() == PlanTratamiento.Estado.PRESENTADO) {
            p.volverABorrador();
        } else {
            p.tocar();
        }
    }

    /** Aceptado, sin adicionales por aceptar y sin nada pendiente: terminó solo. */
    private void revisarTerminado(PlanTratamiento p) {
        if (p.getEstado() != PlanTratamiento.Estado.ACEPTADO) {
            return;
        }
        List<ItemPlan> todos = items.findByPlanIdOrderByFaseAscOrdenAsc(p.getId());
        boolean quedaAlgo = todos.stream().anyMatch(i -> !i.aceptado() || i.getEstado() == ItemPlan.Estado.PENDIENTE);
        if (!quedaAlgo) {
            p.cerrar(PlanTratamiento.Estado.TERMINADO, null);
        }
    }

    /** Al cerrar: los aceptados pendientes se cancelan con el motivo y los que nunca se aceptaron se quitan. */
    private void cerrarPendientes(UUID planId, String motivo) {
        for (ItemPlan i : items.findByPlanIdOrderByFaseAscOrdenAsc(planId)) {
            if (!i.aceptado()) {
                items.delete(i);
            } else if (i.getEstado() == ItemPlan.Estado.PENDIENTE) {
                i.cancelar(motivo);
            }
        }
    }

    private ItemPlan.Datos normalizar(PlanTratamiento p, ItemDatos d, int orden) {
        if (d.fase() < 1 || d.fase() > MAX_FASES) {
            throw ApiProblem.badRequest("La fase debe estar entre 1 y " + MAX_FASES);
        }
        // Valida el CUPS, la cantidad, el diente (FDI) y las superficies igual que un procedimiento realizado
        Procedimiento validado = new Procedimiento(d.codigoCups(), d.descripcion(), d.cantidad(), d.valorUnitario(),
                d.diente(), d.superficies());
        String codigo = validado.getCodigoCups();
        String nombre = historia.nombresCups(List.of(codigo)).get(codigo);
        if (nombre == null) {
            throw ApiProblem.badRequest("El código " + codigo + " no está en el catálogo CUPS");
        }
        String descripcion = texto(d.descripcion()) == null ? nombre : d.descripcion().strip();
        if (descripcion.length() > 400) {
            throw ApiProblem.badRequest("La descripción admite 400 caracteres");
        }
        BigDecimal valor = validado.getValorUnitario();
        if (valor == null) {
            Map<String, BigDecimal> lista = precios.precios(List.of(codigo),
                    new ListaPrecios.Contexto(p.getPacienteId(), null, null));
            valor = lista.get(codigo);
            if (valor == null) {
                throw ApiProblem.badRequest(codigo + " no tiene precio en la lista del consultorio: escriba el valor");
            }
        }
        BigDecimal descuento = d.descuento() == null ? BigDecimal.ZERO : d.descuento();
        BigDecimal subtotal = valor.multiply(BigDecimal.valueOf(validado.getCantidad()));
        if (descuento.signum() < 0 || descuento.compareTo(subtotal) > 0) {
            throw ApiProblem.badRequest("El descuento de " + codigo + " debe estar entre 0 y el valor del procedimiento");
        }
        String faseNombre = texto(d.faseNombre());
        if (faseNombre != null && faseNombre.length() > 80) {
            throw ApiProblem.badRequest("El nombre de la fase admite 80 caracteres");
        }
        return new ItemPlan.Datos(d.fase(), faseNombre, orden, codigo, descripcion, validado.getDiente(),
                validado.getSuperficies(), validado.getCantidad(), valor, descuento);
    }

    private PlanTratamientoVista vista(PlanTratamiento p) {
        List<ItemPlan> todos = items.findByPlanIdOrderByFaseAscOrdenAsc(p.getId());
        List<AceptacionPlan> acept = aceptaciones.findByPlanIdOrderByNumeroAsc(p.getId());
        TotalesPlan t = TotalesPlan.de(todos);
        String odontologo = identidad.profesional(p.getOdontologoId()).nombre();
        return new PlanTratamientoVista(p.getId(), p.getPacienteId(), p.getOdontologoId(), odontologo, p.getEstado().name(),
                p.getObservaciones(), p.getVigenciaDias(), p.getPresentadoEn(), p.getCerradoEn(), p.getMotivoCierre(),
                p.getCreadoEn(), p.getActualizadoEn(), todos.stream().map(ItemPlanVista::of).toList(),
                acept.stream().map(AceptacionPlanVista::of).toList(), t);
    }

    private PlanTratamiento plan(UUID id) {
        return planes.findById(id).orElseThrow(() -> ApiProblem.notFound("Plan de tratamiento no encontrado"));
    }

    private PlanTratamiento vigente(UUID id) {
        exigirFormato();
        PlanTratamiento p = plan(id);
        if (!p.getEstado().vigente()) {
            throw ApiProblem.conflict("El plan está " + (p.getEstado() == PlanTratamiento.Estado.TERMINADO ? "terminado" : "cancelado")
                    + ": ya no se modifica");
        }
        return p;
    }

    private ItemPlan item(UUID planId, UUID itemId) {
        return items.findById(itemId).filter(i -> i.getPlanId().equals(planId))
                .orElseThrow(() -> ApiProblem.notFound("Procedimiento no encontrado en el plan"));
    }

    private void exigirFormato() {
        if (!formatos.activoEnConsultorio(FORMATO_REQUERIDO)) {
            throw ApiProblem.notFound("El plan de tratamiento requiere el odontograma activo en el consultorio");
        }
    }

    private static int vigencia(Integer dias) {
        int v = dias == null ? PlanTratamiento.VIGENCIA_POR_DEFECTO : dias;
        if (v < 1 || v > MAX_VIGENCIA) {
            throw ApiProblem.badRequest("La vigencia de la cotización debe estar entre 1 y " + MAX_VIGENCIA + " días");
        }
        return v;
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String motivoObligatorio(String motivo) {
        String m = texto(motivo);
        if (m == null) {
            throw ApiProblem.badRequest("Escriba el motivo");
        }
        if (m.length() > 300) {
            throw ApiProblem.badRequest("El motivo admite 300 caracteres");
        }
        return m;
    }

    /** PNG en data URL, como la firma del consentimiento; vacía es nula. */
    static String firma(String datos) {
        if (datos == null || datos.isBlank()) {
            return null;
        }
        if (!datos.startsWith(PREFIJO_FIRMA) || datos.length() > MAX_FIRMA
                || !datos.substring(PREFIJO_FIRMA.length()).matches("[A-Za-z0-9+/]+={0,2}")) {
            throw ApiProblem.badRequest("La firma debe ser una imagen PNG");
        }
        return datos;
    }

    // --- contratos de la API ---

    /** @param valorUnitario nulo toma el de la lista de precios (o la tarifa del pagador del paciente) */
    public record ItemDatos(int fase, String faseNombre, String codigoCups, String descripcion, String diente,
                            String superficies, int cantidad, BigDecimal valorUnitario, BigDecimal descuento) {
    }

    public record PlanTratamientoVista(UUID id, UUID pacienteId, UUID odontologoId, String odontologo, String estado,
                            String observaciones, int vigenciaDias, Instant presentadoEn, Instant cerradoEn,
                            String motivoCierre, Instant creadoEn, Instant actualizadoEn, List<ItemPlanVista> items,
                            List<AceptacionPlanVista> aceptaciones, TotalesPlan totales) {
    }

    /** @param aceptacion número de la aceptación; nulo = por aceptar (se edita o se quita) */
    public record ItemPlanVista(UUID id, int fase, String faseNombre, int orden, String codigoCups, String descripcion,
                            String diente, String superficies, int cantidad, BigDecimal valorUnitario,
                            BigDecimal descuento, BigDecimal total, String estado, Integer aceptacion,
                            String motivoCancelacion, UUID atencionId, Instant realizadoEn) {
        static ItemPlanVista of(ItemPlan i) {
            return new ItemPlanVista(i.getId(), i.getFase(), i.getFaseNombre(), i.getOrden(), i.getCodigoCups(),
                    i.getDescripcion(), i.getDiente(), i.getSuperficies(), i.getCantidad(), i.getValorUnitario(),
                    i.getDescuento(), i.total(), i.getEstado().name(), i.getAceptacion(), i.getMotivoCancelacion(),
                    i.getAtencionId(), i.getRealizadoEn());
        }
    }

    /** La imagen de la firma no viaja en la vista: sale en el PDF. */
    public record AceptacionPlanVista(int numero, Instant aceptadoEn, UUID registradoPor, boolean conFirma,
                                  String nombreFirmante, boolean constanciaVerbal, BigDecimal total) {
        static AceptacionPlanVista of(AceptacionPlan a) {
            return new AceptacionPlanVista(a.getNumero(), a.getAceptadoEn(), a.getRegistradoPor(), a.getFirma() != null,
                    a.getNombreFirmante(), a.isConstanciaVerbal(), a.getTotal());
        }
    }

    /**
     * @param aceptado   lo que el paciente aceptó (sin lo cancelado después)
     * @param porAceptar ítems nuevos o adicionales aún sin aceptar
     */
    public record TotalesPlan(BigDecimal aceptado, BigDecimal realizado, BigDecimal pendiente, BigDecimal cancelado,
                          BigDecimal porAceptar, int procedimientos, int realizados) {
        static TotalesPlan de(List<ItemPlan> items) {
            BigDecimal realizado = suma(items, i -> i.aceptado() && i.getEstado() == ItemPlan.Estado.REALIZADO);
            BigDecimal pendiente = suma(items, i -> i.aceptado() && i.getEstado() == ItemPlan.Estado.PENDIENTE);
            BigDecimal cancelado = suma(items, i -> i.aceptado() && i.getEstado() == ItemPlan.Estado.CANCELADO);
            BigDecimal porAceptar = suma(items, i -> !i.aceptado());
            int activos = (int) items.stream().filter(i -> i.getEstado() != ItemPlan.Estado.CANCELADO).count();
            int realizados = (int) items.stream().filter(i -> i.getEstado() == ItemPlan.Estado.REALIZADO).count();
            return new TotalesPlan(realizado.add(pendiente), realizado, pendiente, cancelado, porAceptar, activos, realizados);
        }

        private static BigDecimal suma(List<ItemPlan> items, Predicate<ItemPlan> filtro) {
            return items.stream().filter(filtro).map(ItemPlan::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
