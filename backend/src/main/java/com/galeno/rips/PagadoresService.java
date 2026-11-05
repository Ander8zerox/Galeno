package com.galeno.rips;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.historia.HistoriaService;
import com.galeno.modulos.Modulo;
import com.galeno.modulos.ModulosService;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Pagadores del consultorio (EPS, prepagadas, ARL, SOAT, particular) y sus tarifas por CUPS. El pagador "Particular"
 * existe siempre: se crea la primera vez que se consultan los pagadores.
 */
@Service
public class PagadoresService {

    private static final Pattern NIT = Pattern.compile("\\d{5,15}(-\\d)?");
    private static final Pattern CODIGO = Pattern.compile("[A-Za-z0-9-]{1,64}");
    /** El particular: cobertura 15 "Particular", factura sin contrato 07 "Atención a pacientes particulares", por evento. */
    static final Pagador.Datos PARTICULAR = new Pagador.Datos("Particular", Pagador.Tipo.PARTICULAR, null, null, "15",
            "04", null, null, "07", false, true);

    private final PagadorRepository pagadores;
    private final TarifaRepository tarifas;
    private final ReferenciasService referencias;
    private final ModulosService modulos;
    private final HistoriaService historia;

    PagadoresService(PagadorRepository pagadores, TarifaRepository tarifas, ReferenciasService referencias,
                     ModulosService modulos, @Lazy HistoriaService historia) {
        this.pagadores = pagadores;
        this.tarifas = tarifas;
        this.referencias = referencias;
        this.modulos = modulos;
        this.historia = historia;
    }

    /** Activos primero; con el particular (lo crea si aún no existe). */
    @Transactional
    public List<PagadorVista> listar() {
        modulos.exigir(Modulo.RIPS);
        if (pagadores.findFirstByTipo(Pagador.Tipo.PARTICULAR).isEmpty()) {
            pagadores.saveAndFlush(new Pagador(PARTICULAR));
        }
        return pagadores.findAllByOrderByActivoDescNombre().stream().map(this::vista).toList();
    }

    @Transactional
    public PagadorVista crear(Pagador.Datos datos) {
        modulos.exigir(Modulo.RIPS);
        Pagador.Datos d = validar(datos);
        if (d.tipo() == Pagador.Tipo.PARTICULAR) {
            throw ApiProblem.badRequest("El pagador particular ya existe: no se puede crear otro");
        }
        return vista(pagadores.saveAndFlush(new Pagador(d)));
    }

    @Transactional
    public PagadorVista actualizar(UUID id, Pagador.Datos datos) {
        modulos.exigir(Modulo.RIPS);
        Pagador p = get(id);
        Pagador.Datos d = validar(datos);
        boolean eraParticular = p.getTipo() == Pagador.Tipo.PARTICULAR;
        if (eraParticular != (d.tipo() == Pagador.Tipo.PARTICULAR)) {
            throw ApiProblem.badRequest("El pagador particular no cambia de tipo, ni otro pagador puede volverse particular");
        }
        if (eraParticular && !d.activo()) {
            throw ApiProblem.badRequest("El pagador particular no se puede desactivar");
        }
        p.actualizar(d);
        return vista(pagadores.saveAndFlush(p));
    }

    Pagador get(UUID id) {
        return pagadores.findById(id).orElseThrow(() -> ApiProblem.notFound("Pagador no encontrado"));
    }

    /** Para el paciente y el cobro: debe existir y estar activo. */
    Pagador activo(UUID id) {
        Pagador p = get(id);
        if (!p.isActivo()) {
            throw ApiProblem.badRequest("El pagador " + p.getNombre() + " está desactivado");
        }
        return p;
    }

    private Pagador.Datos validar(Pagador.Datos d) {
        String nombre = texto(d.nombre());
        if (nombre == null || nombre.length() > 150) {
            throw ApiProblem.badRequest("El nombre es obligatorio (hasta 150 caracteres)");
        }
        if (d.tipo() == null) {
            throw ApiProblem.badRequest("El tipo de pagador es obligatorio");
        }
        String nit = texto(d.nit());
        if (nit != null && !NIT.matcher(nit).matches()) {
            throw ApiProblem.badRequest("NIT: solo números, con el dígito de verificación después de un guion (p. ej. 900156264-2)");
        }
        String cucon = texto(d.cucon());
        if (cucon != null && !CODIGO.matcher(cucon).matches()) {
            throw ApiProblem.badRequest("CUCON: hasta 64 letras, números o guiones");
        }
        String poliza = texto(d.numeroPoliza());
        if (poliza != null && poliza.length() > 30) {
            throw ApiProblem.badRequest("Número de póliza: hasta 30 caracteres");
        }
        String entidad = texto(d.codigoEntidad());
        if (entidad != null && referencias.porCodigo("eapb", entidad).isEmpty()) {
            throw ApiProblem.badRequest("Entidad: el código " + entidad + " no está en la tabla de SISPRO");
        }
        return new Pagador.Datos(nombre, d.tipo(), entidad, nit,
                referencias.validar("cobertura", d.cobertura(), "Cobertura o plan de beneficios"),
                referencias.validar("modalidad_pago", d.modalidadPago(), "Modalidad de pago"), cucon, poliza,
                referencias.validar("factura_sin_contrato", d.facturaSinContrato(), "Factura sin contrato"),
                d.exigeAutorizacion(), d.activo());
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    PagadorVista vista(Pagador p) {
        Pagador.Datos d = p.datos();
        // El régimen de la entidad (C contributivo, S subsidiado…) sirve para sugerir el tipo de usuario
        String regimen = d.codigoEntidad() == null ? null : referencias.porCodigo("eapb", d.codigoEntidad())
                .map(r -> r.extra().get("regimen")).orElse(null);
        return new PagadorVista(p.getId(), d.nombre(), d.tipo(), d.codigoEntidad(), d.nit(), d.cobertura(),
                d.modalidadPago(), d.cucon(), d.numeroPoliza(), d.facturaSinContrato(), d.exigeAutorizacion(),
                d.activo(), regimen);
    }

    // --- tarifas ---

    @Transactional(readOnly = true)
    public List<PreciosService.PrecioVista> tarifas(UUID pagadorId) {
        modulos.exigir(Modulo.RIPS);
        get(pagadorId);
        List<Tarifa> todas = tarifas.findByPagadorIdOrderByCodigoCups(pagadorId);
        Map<String, String> nombres = historia.nombresCups(todas.stream().map(Tarifa::getCodigoCups).toList());
        return todas.stream().map(t -> new PreciosService.PrecioVista(t.getCodigoCups(),
                nombres.getOrDefault(t.getCodigoCups(), ""), t.getValor(), t.getActualizadoEn())).toList();
    }

    @Transactional
    public PreciosService.PrecioVista fijarTarifa(UUID pagadorId, String codigoCups, BigDecimal valor) {
        modulos.exigir(Modulo.RIPS);
        get(pagadorId);
        String codigo = codigoCups.strip().toUpperCase();
        String nombre = historia.nombresCups(List.of(codigo)).get(codigo);
        if (nombre == null) {
            throw ApiProblem.badRequest("El código " + codigoCups + " no está en el catálogo CUPS");
        }
        if (valor == null || valor.signum() < 0) {
            throw ApiProblem.badRequest("La tarifa debe ser cero o mayor");
        }
        Tarifa t = tarifas.findByPagadorIdAndCodigoCups(pagadorId, codigo).orElseGet(() -> new Tarifa(pagadorId, codigo, valor));
        t.fijar(valor);
        t = tarifas.saveAndFlush(t);
        return new PreciosService.PrecioVista(codigo, nombre, t.getValor(), t.getActualizadoEn());
    }

    @Transactional
    public void quitarTarifa(UUID pagadorId, String codigoCups) {
        modulos.exigir(Modulo.RIPS);
        get(pagadorId);
        tarifas.findByPagadorIdAndCodigoCups(pagadorId, codigoCups.strip().toUpperCase()).ifPresent(tarifas::delete);
    }

    /** Las tarifas del pagador para esos CUPS (las que no tiene, no aparecen). */
    Map<String, BigDecimal> tarifas(UUID pagadorId, java.util.Collection<String> codigos) {
        return tarifas.findByPagadorIdAndCodigoCupsIn(pagadorId, codigos).stream()
                .collect(java.util.stream.Collectors.toMap(Tarifa::getCodigoCups, Tarifa::getValor));
    }

    /** @param regimen de la entidad en SISPRO (C, S, E…), para sugerir el tipo de usuario del paciente */
    public record PagadorVista(UUID id, String nombre, Pagador.Tipo tipo, String codigoEntidad, String nit,
                               String cobertura, String modalidadPago, String cucon, String numeroPoliza,
                               String facturaSinContrato, boolean exigeAutorizacion, boolean activo, String regimen) {
    }
}
