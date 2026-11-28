package com.galeno.rips;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.historia.HistoriaService;
import com.galeno.historia.ListaPrecios;
import com.galeno.modulos.Modulo;
import com.galeno.modulos.ModulosService;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Lista de precios por CUPS del consultorio; funciona sin el módulo de RIPS (decidido el 28 de noviembre de 2026).
 * Con un pagador que tenga tarifa para el CUPS (solo con el módulo), vale la
 * tarifa (convenio); si no, la lista general.
 */
@Service
public class PreciosService implements ListaPrecios {

    private final PrecioCupsRepository precios;
    private final HistoriaService historia;
    private final ModulosService modulos;
    private final PagadoresService pagadores;
    private final CobrosService cobros;

    PreciosService(PrecioCupsRepository precios, @org.springframework.context.annotation.Lazy HistoriaService historia,
                   ModulosService modulos, PagadoresService pagadores, CobrosService cobros) {
        this.precios = precios;
        this.pagadores = pagadores;
        this.cobros = cobros;
        this.historia = historia;
        this.modulos = modulos;
    }

    /**
     * Para la historia y el plan de tratamiento. La lista general vale siempre (también para consultorios de solo
     * particulares); las tarifas por pagador, solo con el módulo de RIPS activo.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> precios(Collection<String> codigosCups, Contexto contexto) {
        if (codigosCups.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> resultado = new java.util.HashMap<>(precios.findByCodigoCupsIn(codigosCups).stream()
                .collect(Collectors.toMap(PrecioCups::getCodigoCups, PrecioCups::getValor)));
        if (contexto != null && modulos.activo(Modulo.RIPS)) {
            cobros.pagador(contexto).ifPresent(pagadorId -> resultado.putAll(pagadores.tarifas(pagadorId, codigosCups)));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<PrecioVista> listar() {
        List<PrecioCups> todos = precios.findAllByOrderByCodigoCups();
        Map<String, String> nombres = historia.nombresCups(todos.stream().map(PrecioCups::getCodigoCups).toList());
        return todos.stream().map(p -> new PrecioVista(p.getCodigoCups(), nombres.getOrDefault(p.getCodigoCups(), ""),
                p.getValor(), p.getActualizadoEn())).toList();
    }

    @Transactional
    public PrecioVista fijar(String codigoCups, BigDecimal valor) {
        String codigo = codigoCups.strip().toUpperCase();
        String nombre = historia.nombresCups(List.of(codigo)).get(codigo);
        if (nombre == null) {
            throw ApiProblem.badRequest("El código " + codigoCups + " no está en el catálogo CUPS");
        }
        if (valor == null || valor.signum() < 0) {
            throw ApiProblem.badRequest("El precio debe ser cero o mayor");
        }
        PrecioCups p = precios.findByCodigoCups(codigo).orElseGet(() -> new PrecioCups(codigo, valor));
        p.fijar(valor);
        p = precios.saveAndFlush(p);
        return new PrecioVista(codigo, nombre, p.getValor(), p.getActualizadoEn());
    }

    /** Quitar el precio no cambia los procedimientos ya registrados (cada uno guarda su valor). */
    @Transactional
    public void quitar(String codigoCups) {
        precios.findByCodigoCups(codigoCups.strip().toUpperCase()).ifPresent(precios::delete);
    }

    public record PrecioVista(String codigoCups, String descripcion, BigDecimal valor, Instant actualizadoEn) {
    }
}
