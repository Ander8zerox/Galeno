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

/** Lista de precios por CUPS del consultorio (fase 1 de RIPS). Las tarifas por pagador llegan en la fase 2. */
@Service
public class PreciosService implements ListaPrecios {

    private final PrecioCupsRepository precios;
    private final HistoriaService historia;
    private final ModulosService modulos;

    PreciosService(PrecioCupsRepository precios, @org.springframework.context.annotation.Lazy HistoriaService historia,
                   ModulosService modulos) {
        this.precios = precios;
        this.historia = historia;
        this.modulos = modulos;
    }

    /** Para la historia: sin el módulo activo no hay precios que proponer. */
    @Override
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> precios(Collection<String> codigosCups) {
        if (codigosCups.isEmpty() || !modulos.activo(Modulo.RIPS)) {
            return Map.of();
        }
        return precios.findByCodigoCupsIn(codigosCups).stream()
                .collect(Collectors.toMap(PrecioCups::getCodigoCups, PrecioCups::getValor));
    }

    @Transactional(readOnly = true)
    public List<PrecioVista> listar() {
        modulos.exigir(Modulo.RIPS);
        List<PrecioCups> todos = precios.findAllByOrderByCodigoCups();
        Map<String, String> nombres = historia.nombresCups(todos.stream().map(PrecioCups::getCodigoCups).toList());
        return todos.stream().map(p -> new PrecioVista(p.getCodigoCups(), nombres.getOrDefault(p.getCodigoCups(), ""),
                p.getValor(), p.getActualizadoEn())).toList();
    }

    @Transactional
    public PrecioVista fijar(String codigoCups, BigDecimal valor) {
        modulos.exigir(Modulo.RIPS);
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
        modulos.exigir(Modulo.RIPS);
        precios.findByCodigoCups(codigoCups.strip().toUpperCase()).ifPresent(precios::delete);
    }

    public record PrecioVista(String codigoCups, String descripcion, BigDecimal valor, Instant actualizadoEn) {
    }
}
