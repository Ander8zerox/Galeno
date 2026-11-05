package com.galeno.rips;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.agenda.AgendaPublica;
import com.galeno.agenda.AgendaPublica.CitaResumen;
import com.galeno.historia.HistoriaService;
import com.galeno.historia.HistoriaService.AtencionReferencia;
import com.galeno.historia.ListaPrecios;
import com.galeno.modulos.Modulo;
import com.galeno.modulos.ModulosService;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Pagador y pago moderador de cada cita y atención (campos C03, C18, C19 y C20 del Documento Técnico 1). La
 * secretaria los registra en la cita; quien atiende los corrige en la atención (o los registra si no viene de una
 * cita). Lo que vale para una atención: lo de la atención, si no lo de su cita, si no el pagador del paciente.
 */
@Service
public class CobrosService {

    static final String NO_APLICA = "05";
    private static final String CUOTA_MODERADORA = "02";
    private static final String PAGOS_COMPARTIDOS = "03";
    /** El anticipo solo va en la factura, nunca en el RIPS. */
    private static final String ANTICIPO = "04";
    /** Tipos de usuario del régimen contributivo: los únicos a los que se cobra cuota moderadora. */
    private static final Set<String> CONTRIBUTIVO = Set.of("01", "02", "03");
    private static final String SUBSIDIADO = "04";
    private static final Pattern AUTORIZACION = Pattern.compile("[A-Za-z0-9-]{1,30}");
    private static final Pattern FACTURA = Pattern.compile("[A-Za-z0-9-]{1,20}");
    private static final Pattern SIRAS = Pattern.compile("[A-Za-z0-9-]{1,60}");
    /** Tipos de usuario que piden el registro SIRAS (U12): 10 SOAT y 14 lesionado sin SOAT. */
    static final Set<String> CON_SIRAS = Set.of("10", "14");

    private final CobroRepository cobros;
    private final RipsPacienteRepository pacientesRips;
    private final PagadoresService pagadores;
    private final ReferenciasService referencias;
    private final ModulosService modulos;
    private final AgendaPublica agenda;
    private final HistoriaService historia;
    private final LoteRepository lotes;

    CobrosService(CobroRepository cobros, RipsPacienteRepository pacientesRips, PagadoresService pagadores,
                  ReferenciasService referencias, ModulosService modulos, AgendaPublica agenda,
                  @Lazy HistoriaService historia, LoteRepository lotes) {
        this.lotes = lotes;
        this.cobros = cobros;
        this.pacientesRips = pacientesRips;
        this.pagadores = pagadores;
        this.referencias = referencias;
        this.modulos = modulos;
        this.agenda = agenda;
        this.historia = historia;
    }

    // --- cita ---

    @Transactional(readOnly = true)
    public CobroVista cita(UUID citaId) {
        modulos.exigir(Modulo.RIPS);
        CitaResumen cita = cita0(citaId);
        return cobros.findByCitaId(citaId).map(c -> vista(c, Origen.CITA, cita.pacienteId())).orElseGet(() -> porDefecto(cita.pacienteId()));
    }

    /** Solo con la cita abierta: después, el cobro se corrige en la atención. */
    @Transactional
    public CobroVista guardarCita(UUID citaId, DatosCobro datos) {
        modulos.exigir(Modulo.RIPS);
        CitaResumen cita = cita0(citaId);
        if (!cita.abierta()) {
            throw ApiProblem.conflict("La cita ya no está abierta: el cobro se corrige en la atención");
        }
        Cobro c = cobros.findByCitaId(citaId).orElseGet(() -> Cobro.deCita(citaId));
        aplicar(c, datos, cita.pacienteId());
        return vista(cobros.saveAndFlush(c), Origen.CITA, cita.pacienteId());
    }

    /** Para la agenda: el cobro de cada una de esas citas (guardado o el que se propone), en una sola consulta. */
    @Transactional(readOnly = true)
    public List<CobroCita> citas(Collection<UUID> citaIds) {
        modulos.exigir(Modulo.RIPS);
        List<CitaResumen> encontradas = agenda.citas(citaIds);
        Map<UUID, Cobro> guardados = cobros.findByCitaIdIn(encontradas.stream().map(CitaResumen::id).toList()).stream()
                .collect(Collectors.toMap(Cobro::getCitaId, Function.identity()));
        return encontradas.stream().map(cita -> new CobroCita(cita.id(), Optional.ofNullable(guardados.get(cita.id()))
                .map(c -> vista(c, Origen.CITA, cita.pacienteId())).orElseGet(() -> porDefecto(cita.pacienteId())))).toList();
    }

    private CitaResumen cita0(UUID citaId) {
        return agenda.cita(citaId).orElseThrow(() -> ApiProblem.notFound("Cita no encontrada"));
    }

    // --- atención ---

    @Transactional(readOnly = true)
    public CobroVista atencion(UUID atencionId) {
        modulos.exigir(Modulo.RIPS);
        return vigente(atencion0(atencionId));
    }

    /** Se puede corregir aun con la atención firmada (el cobro no es dato clínico), hasta que su lote se genere. */
    @Transactional
    public CobroVista guardarAtencion(UUID atencionId, DatosCobro datos) {
        modulos.exigir(Modulo.RIPS);
        AtencionReferencia a = atencion0(atencionId);
        if (lotes.enLoteGenerado(atencionId)) {
            throw ApiProblem.conflict("La atención ya está en un RIPS generado: para corregir el cobro, anule ese lote");
        }
        Cobro c = cobros.findByAtencionId(atencionId).orElseGet(() -> Cobro.deAtencion(atencionId));
        aplicar(c, datos, a.pacienteId());
        return vista(cobros.saveAndFlush(c), Origen.ATENCION, a.pacienteId());
    }

    private AtencionReferencia atencion0(UUID atencionId) {
        return historia.referencia(atencionId).orElseThrow(() -> ApiProblem.notFound("Atención no encontrada"));
    }

    /** El cobro que vale para la atención: el suyo, si no el de su cita, si no el propuesto con el pagador del paciente. */
    CobroVista vigente(AtencionReferencia a) {
        return cobros.findByAtencionId(a.id()).map(c -> vista(c, Origen.ATENCION, a.pacienteId()))
                .or(() -> Optional.ofNullable(a.citaId()).flatMap(cobros::findByCitaId).map(c -> vista(c, Origen.CITA, a.pacienteId())))
                .orElseGet(() -> porDefecto(a.pacienteId()));
    }

    /** El pagador de la atención (o de la cita, o del paciente) para las tarifas. Vacío si no hay ninguno. */
    @Transactional(readOnly = true)
    Optional<UUID> pagador(ListaPrecios.Contexto ctx) {
        if (ctx.atencionId() != null) {
            Optional<UUID> p = cobros.findByAtencionId(ctx.atencionId()).map(Cobro::getPagadorId);
            if (p.isPresent()) {
                return p;
            }
        }
        if (ctx.citaId() != null) {
            Optional<UUID> p = cobros.findByCitaId(ctx.citaId()).map(Cobro::getPagadorId);
            if (p.isPresent()) {
                return p;
            }
        }
        return ctx.pacienteId() == null ? Optional.empty()
                : pacientesRips.findById(ctx.pacienteId()).map(RipsPaciente::getPagadorId);
    }

    // --- reglas ---

    private void aplicar(Cobro c, DatosCobro d, UUID pacienteId) {
        if (d.pagadorId() == null) {
            throw ApiProblem.badRequest("El pagador es obligatorio");
        }
        Pagador pagador = pagadores.activo(d.pagadorId());
        String concepto = d.conceptoRecaudo() == null || d.conceptoRecaudo().isBlank() ? NO_APLICA : d.conceptoRecaudo().strip();
        referencias.validar("concepto_recaudo", concepto, "Concepto del cobro");
        BigDecimal valor = d.valorPagoModerador() == null ? BigDecimal.ZERO : d.valorPagoModerador();
        String tipoUsuario = tipoUsuario(pacienteId);
        validar(pagador.getTipo(), concepto, valor, tipoUsuario);
        String autorizacion = texto(d.numeroAutorizacion());
        if (autorizacion != null && !AUTORIZACION.matcher(autorizacion).matches()) {
            throw ApiProblem.badRequest("Número de autorización: hasta 30 letras, números o guiones");
        }
        String factura = texto(d.facturaPagoModerador());
        if (factura != null && !FACTURA.matcher(factura).matches()) {
            throw ApiProblem.badRequest("Factura del pago moderador: hasta 20 letras, números o guiones");
        }
        if (NO_APLICA.equals(concepto)) {
            factura = null;
        }
        String siras = texto(d.registroSiras());
        if (siras != null && !SIRAS.matcher(siras).matches()) {
            throw ApiProblem.badRequest("Registro SIRAS: hasta 60 letras, números o guiones");
        }
        if (!pideSiras(pagador.getTipo(), tipoUsuario)) {
            siras = null;
        }
        c.actualizar(pagador.getId(), autorizacion, concepto, valor, factura, siras);
    }

    /** Accidente de tránsito: pagador SOAT, o paciente amparado por SOAT (10) o lesionado sin SOAT (14). */
    static boolean pideSiras(Pagador.Tipo tipoPagador, String tipoUsuario) {
        return tipoPagador == Pagador.Tipo.SOAT || (tipoUsuario != null && CON_SIRAS.contains(tipoUsuario));
    }

    private String tipoUsuario(UUID pacienteId) {
        return pacientesRips.findById(pacienteId).map(RipsPaciente::getTipoUsuario).orElse(null);
    }

    /** Reglas del Documento Técnico 1 (campos C18 y C19) que se pueden revisar con lo que sabe Galenum. */
    static void validar(Pagador.Tipo tipoPagador, String concepto, BigDecimal valor, String tipoUsuario) {
        if (valor.signum() < 0) {
            throw ApiProblem.badRequest("El valor cobrado no puede ser negativo");
        }
        if (ANTICIPO.equals(concepto)) {
            throw ApiProblem.badRequest("El anticipo va solo en la factura, no en el RIPS");
        }
        if (NO_APLICA.equals(concepto) && valor.signum() != 0) {
            throw ApiProblem.badRequest("Sin cobro (\"no aplica\") el valor es 0");
        }
        if (!NO_APLICA.equals(concepto) && valor.compareTo(BigDecimal.ONE) < 0) {
            throw ApiProblem.badRequest("Con copago, cuota moderadora o pago compartido el valor es de al menos $1 (si no se cobró nada, el concepto es \"no aplica\")");
        }
        if (tipoPagador == Pagador.Tipo.PARTICULAR && !NO_APLICA.equals(concepto)) {
            throw ApiProblem.badRequest("Un particular no paga copago ni cuota moderadora: paga el servicio");
        }
        if (CUOTA_MODERADORA.equals(concepto) && tipoUsuario != null && !CONTRIBUTIVO.contains(tipoUsuario)) {
            throw ApiProblem.badRequest("La cuota moderadora solo se cobra a afiliados del régimen contributivo");
        }
        if (PAGOS_COMPARTIDOS.equals(concepto) && SUBSIDIADO.equals(tipoUsuario)) {
            throw ApiProblem.badRequest("Al régimen subsidiado no se le reportan pagos de planes voluntarios");
        }
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private CobroVista porDefecto(UUID pacienteId) {
        Optional<Pagador> pagador = pacientesRips.findById(pacienteId).map(RipsPaciente::getPagadorId)
                .map(pagadores::get);
        return new CobroVista(Origen.PACIENTE, false, pagador.map(Pagador::getId).orElse(null),
                pagador.map(Pagador::getNombre).orElse(null), pagador.map(Pagador::getTipo).orElse(null),
                pagador.map(Pagador::isExigeAutorizacion).orElse(false), null, NO_APLICA, BigDecimal.ZERO, null, null,
                pideSiras(pagador.map(Pagador::getTipo).orElse(null), tipoUsuario(pacienteId)));
    }

    private CobroVista vista(Cobro c, Origen origen, UUID pacienteId) {
        Pagador p = pagadores.get(c.getPagadorId());
        return new CobroVista(origen, true, p.getId(), p.getNombre(), p.getTipo(), p.isExigeAutorizacion(),
                c.getNumeroAutorizacion(), c.getConceptoRecaudo(), c.getValorPagoModerador(), c.getFacturaPagoModerador(),
                c.getRegistroSiras(), pideSiras(p.getTipo(), tipoUsuario(pacienteId)));
    }

    /** De dónde sale el cobro que se muestra: de la atención, de su cita o (sin registrar) del pagador del paciente. */
    public enum Origen { ATENCION, CITA, PACIENTE }

    public record DatosCobro(UUID pagadorId, String numeroAutorizacion, String conceptoRecaudo,
                             BigDecimal valorPagoModerador, String facturaPagoModerador, String registroSiras) {
    }

    /**
     * @param guardado falso cuando es lo que se propone (pagador del paciente, sin cobro)
     * @param exigeAutorizacion el pagador pide número de autorización: la pantalla avisa si falta
     * @param pideSiras accidente de tránsito (pagador SOAT o tipo de usuario 10 o 14): el RIPS exige el registro SIRAS
     */
    public record CobroVista(Origen origen, boolean guardado, UUID pagadorId, String pagadorNombre, Pagador.Tipo pagadorTipo,
                             boolean exigeAutorizacion, String numeroAutorizacion, String conceptoRecaudo,
                             BigDecimal valorPagoModerador, String facturaPagoModerador, String registroSiras,
                             boolean pideSiras) {
    }

    public record CobroCita(UUID citaId, CobroVista cobro) {
    }
}
