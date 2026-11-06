package com.galeno.rips;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.galeno.rips.ArmadorRips.Nivel;

/**
 * Arma el RIPS de una nota crédito, débito o de ajuste sobre el JSON guardado del lote, y lo revisa. No consulta la
 * base, como {@link ArmadorRips}.
 *
 * <p>Según los lineamientos del Ministerio (2.2.2 a 2.2.4): solo van los usuarios y servicios afectados, con el mismo
 * consecutivo que en la factura y el registro completo. En la NC y la ND cada servicio lleva el valor de la nota (lo
 * glosado o lo que se suma) y los pagos moderadores en 0. La NA corrige datos y deja los valores como estaban. Una
 * glosa de valor y de datos a la vez va como NC con los datos corregidos.
 */
final class ArmadorNota {

    enum Tipo { NC, ND, NA }

    static final Pattern NUMERO = Pattern.compile("[A-Za-z0-9-]{1,20}");
    private static final Pattern AUTORIZACION = Pattern.compile("[A-Za-z0-9-]{1,30}");
    private static final Pattern CIE10 = Pattern.compile("[A-Z][0-9]{2}[0-9X]?");
    private static final List<String> LISTAS = List.of("consultas", "procedimientos");

    /**
     * Lo que se puede corregir de cada servicio y contra qué tabla se valida ({@code cie10}: el catálogo CIE-10;
     * {@code null}: solo el formato). El CUPS no: cambiarlo cambia lo facturado y va por NC o ND.
     */
    static final Map<String, Map<String, String>> CORREGIBLES = Map.of(
            "consultas", campos("codDiagnosticoPrincipal", "cie10", "codDiagnosticoRelacionado1", "cie10",
                    "codDiagnosticoRelacionado2", "cie10", "codDiagnosticoRelacionado3", "cie10",
                    "tipoDiagnosticoPrincipal", "tipo_diagnostico", "finalidadTecnologiaSalud", "finalidad",
                    "causaMotivoAtencion", "causa_externa", "modalidadGrupoServicioTecSal", "modalidad_atencion",
                    "grupoServicios", "grupo_servicios", "codServicio", "servicio", "numAutorizacion", null),
            "procedimientos", campos("codDiagnosticoPrincipal", "cie10", "codDiagnosticoRelacionado", "cie10",
                    "finalidadTecnologiaSalud", "finalidad", "modalidadGrupoServicioTecSal", "modalidad_atencion",
                    "grupoServicios", "grupo_servicios", "codServicio", "servicio", "viaIngresoServicioSalud",
                    "via_ingreso", "numAutorizacion", null));

    private static Map<String, String> campos(String... pares) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return m;
    }

    /** Un servicio del RIPS del lote. {@code clave}: "usuario-lista-consecutivo", p. ej. "1-consultas-1". */
    record ServicioLote(String clave, int usuario, String lista, int consecutivo, String codigo, String fecha,
                    String tipoDocumento, String numeroDocumento, BigDecimal valor, Map<String, Object> registro) {
    }

    /**
     * Un servicio marcado en la nota.
     *
     * @param valor NC: lo glosado; ND: lo que se suma; NA: no aplica
     * @param datos los campos que se corrigen ({@link #CORREGIBLES}); vacío borra un relacionado o la autorización
     */
    record ItemNota(String clave, BigDecimal valor, Map<String, String> datos) {
    }

    /** Lo que se leyó del XML de la nota crédito o débito. */
    record XmlNota(String numero, String factura, BigDecimal valor) {
    }

    /**
     * @param acreditado por servicio, lo que ya acreditaron otras NC generadas del lote
     * @param validos por tabla, los códigos válidos de los que se usan en los datos ({@code cie10}: los conocidos)
     */
    record Entrada(Map<String, Object> lote, Tipo tipo, String numero, List<ItemNota> items, Map<String, BigDecimal> acreditado,
                   Map<String, Set<String>> validos, XmlNota xml) {
    }

    /** @param clave el servicio, o {@code null} si es de la nota completa */
    record HallazgoNota(Nivel nivel, String clave, String mensaje) {
    }

    record Resultado(Map<String, Object> json, List<HallazgoNota> hallazgos, BigDecimal total) {

        boolean hayRechazos() {
            return hallazgos.stream().anyMatch(h -> h.nivel() == Nivel.RECHAZO);
        }
    }

    private ArmadorNota() {
    }

    /** Los servicios (consultas y procedimientos) del RIPS del lote, en orden. */
    @SuppressWarnings("unchecked")
    static List<ServicioLote> servicios(Map<String, Object> lote) {
        List<ServicioLote> lista = new ArrayList<>();
        for (Map<String, Object> u : (List<Map<String, Object>>) lote.getOrDefault("usuarios", List.of())) {
            int usuario = entero(u.get("consecutivo"));
            Map<String, Object> servicios = (Map<String, Object>) u.getOrDefault("servicios", Map.of());
            for (String nombre : LISTAS) {
                for (Map<String, Object> s : (List<Map<String, Object>>) servicios.getOrDefault(nombre, List.of())) {
                    int consecutivo = entero(s.get("consecutivo"));
                    Object codigo = s.getOrDefault("codConsulta", s.get("codProcedimiento"));
                    lista.add(new ServicioLote(usuario + "-" + nombre + "-" + consecutivo, usuario, nombre, consecutivo,
                            Objects.toString(codigo, null), Objects.toString(s.get("fechaInicioAtencion"), null),
                            Objects.toString(u.get("tipoDocumentoIdentificacion"), null),
                            Objects.toString(u.get("numDocumentoIdentificacion"), null), decimal(s.get("vrServicio")), s));
                }
            }
        }
        return lista;
    }

    @SuppressWarnings("unchecked")
    static Resultado armar(Entrada e) {
        List<HallazgoNota> h = new ArrayList<>();
        if (e.numero() == null || !NUMERO.matcher(e.numero()).matches()) {
            h.add(new HallazgoNota(Nivel.RECHAZO, null, "Escriba el número de la nota: hasta 20 letras, números o guiones"));
        }
        if (e.items().isEmpty()) {
            h.add(new HallazgoNota(Nivel.RECHAZO, null, "Marque al menos un servicio"));
        }
        Map<String, ServicioLote> porClave = new LinkedHashMap<>();
        servicios(e.lote()).forEach(s -> porClave.put(s.clave(), s));

        // usuario → lista → consecutivo → registro de la nota
        Map<Integer, Map<String, Map<Integer, Map<String, Object>>>> afectados = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        Set<String> vistos = new HashSet<>();
        for (ItemNota item : e.items()) {
            ServicioLote s = porClave.get(item.clave());
            if (s == null) {
                h.add(new HallazgoNota(Nivel.RECHAZO, item.clave(), "El servicio no está en el RIPS del lote"));
                continue;
            }
            if (!vistos.add(item.clave())) {
                h.add(new HallazgoNota(Nivel.RECHAZO, item.clave(), "El servicio está repetido en la nota"));
                continue;
            }
            Map<String, Object> registro = new LinkedHashMap<>(s.registro());
            Map<String, String> datos = item.datos() == null ? Map.of() : item.datos();
            int cambios = aplicarDatos(e, s, registro, datos, h);
            String nombre = s.codigo() + " del " + s.fecha();
            switch (e.tipo()) {
                case NC -> {
                    BigDecimal acreditado = e.acreditado().getOrDefault(s.clave(), BigDecimal.ZERO);
                    BigDecimal disponible = s.valor().subtract(acreditado);
                    if (item.valor() == null || item.valor().compareTo(BigDecimal.ONE) < 0) {
                        h.add(new HallazgoNota(Nivel.RECHAZO, s.clave(), "Escriba el valor glosado de " + nombre
                                + " (al menos $1)"));
                    } else if (item.valor().compareTo(disponible) > 0) {
                        h.add(new HallazgoNota(Nivel.RECHAZO, s.clave(), "El valor glosado de " + nombre + " ($"
                                + item.valor().toPlainString() + ") pasa lo que queda por acreditar ($"
                                + disponible.toPlainString() + ")"));
                    }
                }
                case ND -> {
                    if (item.valor() == null || item.valor().compareTo(BigDecimal.ONE) < 0) {
                        h.add(new HallazgoNota(Nivel.RECHAZO, s.clave(), "Escriba el valor que se suma a " + nombre
                                + " (al menos $1)"));
                    }
                    if (!datos.isEmpty()) {
                        h.add(new HallazgoNota(Nivel.RECHAZO, s.clave(), "La nota débito solo cambia valores: los "
                                + "datos se corrigen con una nota de ajuste"));
                    }
                }
                case NA -> {
                    if (cambios == 0) {
                        h.add(new HallazgoNota(Nivel.RECHAZO, s.clave(), "No hay datos corregidos en " + nombre));
                    }
                }
            }
            if (e.tipo() != Tipo.NA) {
                BigDecimal valor = item.valor() == null ? BigDecimal.ZERO : item.valor();
                total = total.add(valor);
                registro.put("vrServicio", ArmadorRips.numero(valor));
                registro.put("conceptoRecaudo", CobrosService.NO_APLICA);
                registro.put("valorPagoModerador", 0L);
                registro.put("numFEVPagoModerador", null);
            }
            afectados.computeIfAbsent(s.usuario(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(s.lista(), k -> new TreeMap<>()).put(s.consecutivo(), registro);
        }
        revisarXml(e, total, h);

        List<Map<String, Object>> usuarios = new ArrayList<>();
        for (Map<String, Object> u : (List<Map<String, Object>>) e.lote().getOrDefault("usuarios", List.of())) {
            Map<String, Map<Integer, Map<String, Object>>> delUsuario = afectados.get(entero(u.get("consecutivo")));
            if (delUsuario == null) {
                continue;
            }
            Map<String, Object> copia = new LinkedHashMap<>(u);
            Map<String, Object> servicios = new LinkedHashMap<>();
            for (String nombre : LISTAS) {
                if (delUsuario.containsKey(nombre)) {
                    servicios.put(nombre, new ArrayList<>(delUsuario.get(nombre).values()));
                }
            }
            copia.put("servicios", servicios);
            usuarios.add(copia);
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("numDocumentoIdObligado", e.lote().get("numDocumentoIdObligado"));
        json.put("numFactura", e.lote().get("numFactura"));
        json.put("tipoNota", e.tipo().name());
        json.put("numNota", e.numero());
        json.put("usuarios", usuarios);
        return new Resultado(json, h, total);
    }

    /** Aplica las correcciones al registro y devuelve cuántas cambian algo. */
    private static int aplicarDatos(Entrada e, ServicioLote s, Map<String, Object> registro, Map<String, String> datos,
                                    List<HallazgoNota> h) {
        Map<String, String> corregibles = CORREGIBLES.get(s.lista());
        int cambios = 0;
        for (Map.Entry<String, String> d : datos.entrySet()) {
            String campo = d.getKey();
            String valor = d.getValue() == null || d.getValue().isBlank() ? null : d.getValue().strip().toUpperCase();
            if (!corregibles.containsKey(campo)) {
                h.add(new HallazgoNota(Nivel.RECHAZO, s.clave(), "El campo " + campo + " no se corrige en una nota"));
                continue;
            }
            if (valor == null && !campo.startsWith("codDiagnosticoRelacionado") && !campo.equals("numAutorizacion")) {
                h.add(new HallazgoNota(Nivel.RECHAZO, s.clave(), "El campo " + campo + " no puede quedar vacío"));
                continue;
            }
            String tabla = corregibles.get(campo);
            if (valor != null && !valido(e, campo, tabla, valor)) {
                h.add(new HallazgoNota(Nivel.RECHAZO, s.clave(), "El valor " + valor + " no es válido para " + campo));
                continue;
            }
            Object nuevo = campo.equals("codServicio") && valor != null ? Integer.valueOf(valor) : valor;
            if (!Objects.equals(Objects.toString(registro.get(campo), null), Objects.toString(nuevo, null))) {
                cambios++;
            }
            registro.put(campo, nuevo);
        }
        // RVC086 y RVC087 (rechazo): los relacionados no repiten el principal ni entre sí
        Object principal = registro.get("codDiagnosticoPrincipal");
        Set<Object> relacionados = new HashSet<>();
        for (String campo : corregibles.keySet()) {
            Object v = registro.get(campo);
            if (campo.startsWith("codDiagnosticoRelacionado") && v != null) {
                if (v.equals(principal)) {
                    h.add(new HallazgoNota(Nivel.RECHAZO, s.clave(), "Un relacionado repite el diagnóstico principal "
                            + "(RVC086)"));
                } else if (!relacionados.add(v)) {
                    h.add(new HallazgoNota(Nivel.RECHAZO, s.clave(), "Los relacionados se repiten (RVC087)"));
                }
            }
        }
        return cambios;
    }

    private static boolean valido(Entrada e, String campo, String tabla, String valor) {
        if (campo.equals("numAutorizacion")) {
            return AUTORIZACION.matcher(valor).matches();
        }
        if ("cie10".equals(tabla) && !CIE10.matcher(valor).matches()) {
            return false;
        }
        if (campo.equals("codServicio") && !valor.matches("\\d{1,4}")) {
            return false;
        }
        return e.validos().getOrDefault(tabla, Set.of()).contains(valor);
    }

    private static void revisarXml(Entrada e, BigDecimal total, List<HallazgoNota> h) {
        XmlNota x = e.xml();
        if (x == null) {
            return;
        }
        if (x.numero() != null && !x.numero().equals(e.numero())) {
            h.add(new HallazgoNota(Nivel.RECHAZO, null, "El número del XML (" + x.numero() + ") no es el de la nota ("
                    + e.numero() + ")"));
        }
        Object factura = e.lote().get("numFactura");
        if (x.factura() != null && !x.factura().equals(factura)) {
            h.add(new HallazgoNota(Nivel.RECHAZO, null, "El XML referencia la factura " + x.factura() + ", no la del lote ("
                    + factura + ")"));
        }
        if (x.valor() != null && e.tipo() != Tipo.NA && x.valor().compareTo(total) != 0) {
            h.add(new HallazgoNota(Nivel.RECHAZO, null, "El valor del XML ($" + x.valor().toPlainString()
                    + ") no es la suma de los servicios de la nota ($" + total.toPlainString() + ")"));
        }
    }

    private static int entero(Object o) {
        return o instanceof Number n ? n.intValue() : Integer.parseInt(Objects.toString(o, "0"));
    }

    static BigDecimal decimal(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString());
    }
}
