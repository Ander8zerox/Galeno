package com.galeno.rips;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.modulos.Modulo;
import com.galeno.modulos.ModulosService;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

import tools.jackson.databind.json.JsonMapper;

/** Tablas de referencia del RIPS (SISPRO): listas para la pantalla y validación de los códigos que se guardan. */
@Service
public class ReferenciasService {

    /** Las que se pueden consultar desde la pantalla. */
    static final Set<String> TABLAS = Set.of("tipo_usuario", "pais", "departamento", "municipio", "zona", "finalidad",
            "causa_externa", "modalidad_atencion", "grupo_servicios", "servicio", "via_ingreso", "concepto_recaudo",
            "modalidad_pago", "tipo_diagnostico");
    /** Estas se buscan por texto (son largas); las demás se devuelven completas. */
    private static final Set<String> CON_BUSQUEDA = Set.of("municipio", "pais", "servicio");
    static final String COLOMBIA = "170";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ReferenciaRepository referencias;
    private final ModulosService modulos;

    ReferenciasService(ReferenciaRepository referencias, ModulosService modulos) {
        this.referencias = referencias;
        this.modulos = modulos;
    }

    /** @param q para municipios, países y servicios: código o parte del nombre (sin q, todos) */
    @Transactional(readOnly = true)
    public List<ReferenciaVista> listar(String tabla, String q) {
        modulos.exigir(Modulo.RIPS);
        if (!TABLAS.contains(tabla)) {
            throw ApiProblem.notFound("Tabla de referencia desconocida: " + tabla);
        }
        String texto = q == null ? "" : q.strip();
        List<Referencia> filas = CON_BUSQUEDA.contains(tabla) && !texto.isEmpty()
                ? referencias.buscar(tabla, texto, PageRequest.of(0, 30))
                : referencias.findByTablaAndHabilitadoTrueOrderByCodigo(tabla);
        Map<String, String> departamentos = tabla.equals("municipio") ? nombres("departamento") : Map.of();
        return filas.stream().map(r -> new ReferenciaVista(r.getCodigo(), r.getNombre(),
                tabla.equals("municipio") ? departamentos.get(extra(r).get("Departamento")) : null, extra(r))).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, String> nombres(String tabla) {
        return referencias.findByTablaAndHabilitadoTrueOrderByCodigo(tabla).stream()
                .collect(Collectors.toMap(Referencia::getCodigo, Referencia::getNombre));
    }

    /** {@code null} o vacío pasa; un código que no está habilitado en la tabla, no. */
    @Transactional(readOnly = true)
    public String validar(String tabla, String codigo, String campo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        String c = codigo.strip();
        Referencia r = referencias.findById(new Referencia.Clave(tabla, c)).filter(Referencia::isHabilitado)
                .orElseThrow(() -> ApiProblem.badRequest(campo + ": el código " + c + " no está en la tabla de SISPRO"));
        return r.getCodigo();
    }

    /** La finalidad debe aplicar a consultas (columna "Consultas" de SISPRO). */
    @Transactional(readOnly = true)
    public void exigirParaConsultas(String tabla, String codigo, String campo) {
        if (codigo == null) {
            return;
        }
        Referencia r = referencias.findById(new Referencia.Clave(tabla, codigo)).orElseThrow();
        if ("NO".equals(extra(r).get("Consultas"))) {
            throw ApiProblem.badRequest(campo + ": " + r.getNombre().toLowerCase() + " no aplica a consultas");
        }
    }

    private static Map<String, String> extra(Referencia r) {
        if (r.getExtra() == null) {
            return Map.of();
        }
        return JSON.readValue(r.getExtra(), new tools.jackson.core.type.TypeReference<Map<String, String>>() {
        });
    }

    /** @param detalle el departamento, en los municipios (hay nombres repetidos: "La Unión", "San Pedro"…) */
    public record ReferenciaVista(String codigo, String nombre, String detalle, Map<String, String> extra) {
    }
}
