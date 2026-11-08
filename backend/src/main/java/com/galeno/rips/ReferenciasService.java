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
            "modalidad_pago", "tipo_diagnostico", "eapb", "cobertura", "factura_sin_contrato");
    /** Estas se buscan por texto (son largas); las demás se devuelven completas. */
    private static final Set<String> CON_BUSQUEDA = Set.of("municipio", "pais", "servicio", "eapb");
    /**
     * Aseguradoras: primero las que suelen pagar consultas (EPS, prepagadas, ARL, SOAT, regímenes especiales) y al final
     * las secretarías de salud, que son la mayoría de la tabla. Es el número con que empieza su "tipo" en SISPRO.
     */
    private static final List<String> ORDEN_EAPB = List.of("7", "5", "8", "6", "10", "9", "3", "4", "2", "1");
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
        if (tabla.equals("eapb") && texto.length() < 2) {
            return List.of(); // son 1.700: solo por búsqueda
        }
        List<Referencia> filas = CON_BUSQUEDA.contains(tabla) && !texto.isEmpty()
                ? referencias.buscar(tabla, texto, PageRequest.of(0, tabla.equals("eapb") ? 200 : 30))
                : referencias.findByTablaAndHabilitadoTrueOrderByCodigo(tabla);
        if (tabla.equals("eapb")) {
            filas = filas.stream().sorted(java.util.Comparator.comparingInt(ReferenciasService::ordenEapb)).limit(30).toList();
        }
        Map<String, String> departamentos = tabla.equals("municipio") ? nombres("departamento") : Map.of();
        return filas.stream().map(r -> new ReferenciaVista(r.getCodigo(), r.getNombre(),
                tabla.equals("municipio") ? departamentos.get(extra(r).get("Departamento")) : null, extra(r))).toList();
    }

    /** Una fila por su código (habilitada o no: un pagador viejo puede nombrar una entidad liquidada). */
    @Transactional(readOnly = true)
    public java.util.Optional<ReferenciaVista> porCodigo(String tabla, String codigo) {
        return referencias.findById(new Referencia.Clave(tabla, codigo))
                .map(r -> new ReferenciaVista(r.getCodigo(), r.getNombre(), null, extra(r)));
    }

    /** Esas filas de la tabla (habilitadas o no), con sus columnas extra; los códigos que no están, no aparecen. */
    @Transactional(readOnly = true)
    Map<String, ArmadorRips.Fila> filas(String tabla, java.util.Collection<String> codigos) {
        return referencias.findAllById(codigos.stream().distinct().map(c -> new Referencia.Clave(tabla, c)).toList())
                .stream().collect(Collectors.toMap(Referencia::getCodigo,
                        r -> new ArmadorRips.Fila(r.isHabilitado(), extra(r))));
    }

    private static int ordenEapb(Referencia r) {
        String tipo = extra(r).getOrDefault("tipo", "");
        int i = ORDEN_EAPB.indexOf(tipo.contains(".") ? tipo.substring(0, tipo.indexOf('.')) : tipo);
        return i < 0 ? ORDEN_EAPB.size() : i;
    }

    @Transactional(readOnly = true)
    public Map<String, String> nombres(String tabla) {
        return referencias.findByTablaAndHabilitadoTrueOrderByCodigo(tabla).stream()
                .collect(Collectors.toMap(Referencia::getCodigo, Referencia::getNombre));
    }

    /**
     * El código si está habilitado en la tabla; si no, {@code null}. No lanza excepción: sirve dentro de otra
     * transacción (una excepción de un método transaccional la marcaría para deshacerse aunque se atrape).
     */
    @Transactional(readOnly = true)
    public String siExiste(String tabla, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        return referencias.findById(new Referencia.Clave(tabla, codigo.strip())).filter(Referencia::isHabilitado)
                .map(Referencia::getCodigo).orElse(null);
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
