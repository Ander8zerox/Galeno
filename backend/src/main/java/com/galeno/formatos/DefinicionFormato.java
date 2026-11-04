package com.galeno.formatos;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Secciones y campos de una versión de formato (la columna {@code formato_versiones.definicion}). Las respuestas se
 * guardan por clave: {@code {"seccion": {"campo": valor}}}, así que las claves no cambian entre versiones.
 */
public record DefinicionFormato(List<Seccion> secciones) {

    public enum Tipo {
        @JsonProperty("texto") TEXTO,
        @JsonProperty("texto_largo") TEXTO_LARGO,
        @JsonProperty("numero") NUMERO,
        @JsonProperty("fecha") FECHA,
        @JsonProperty("seleccion") SELECCION,
        @JsonProperty("multiple") MULTIPLE,
        @JsonProperty("si_no") SI_NO,
        /** Filas de columnas tipadas: fijas ({@code filas}) o libres. */
        @JsonProperty("tabla") TABLA,
        /** Ítems con puntaje, total automático e interpretación por rangos (Glasgow, EVA…). */
        @JsonProperty("escala") ESCALA
    }

    public record Seccion(String clave, String titulo, List<Campo> campos) {
    }

    /**
     * @param visibleSi el campo solo se muestra (y solo se guarda) si otro campo de la misma sección tiene ese valor
     * @param obligatorio se exige al firmar la atención, no al guardar el borrador
     */
    public record Campo(String clave, Tipo tipo, String etiqueta, String ayuda, boolean obligatorio,
                        List<String> opciones, String unidad, BigDecimal min, BigDecimal max, VisibleSi visibleSi,
                        List<Columna> columnas, List<Fila> filas, List<ItemEscala> items, List<Rango> rangos) {

        int puntajeMaximo() {
            return items.stream().mapToInt(i -> i.opciones().stream().mapToInt(OpcionEscala::puntaje).max().orElse(0)).sum();
        }
    }

    public record VisibleSi(String campo, JsonNode igual) {
    }

    /** Columna de una tabla: solo tipos sencillos (texto, numero, seleccion, si_no). */
    public record Columna(String clave, String etiqueta, Tipo tipo, List<String> opciones) {
    }

    public record Fila(String clave, String etiqueta) {
    }

    public record ItemEscala(String clave, String etiqueta, List<OpcionEscala> opciones) {
    }

    public record OpcionEscala(String etiqueta, int puntaje) {
    }

    /** Interpretación del total de una escala, con los extremos incluidos. */
    public record Rango(int min, int max, String interpretacion) {
    }

    private static final Set<Tipo> TIPOS_COLUMNA = Set.of(Tipo.TEXTO, Tipo.NUMERO, Tipo.SELECCION, Tipo.SI_NO);

    /**
     * Estricto: una propiedad mal escrita en el JSON (p. ej. "obligatoria") es un error, no se ignora. Un booleano que
     * no viene (como {@code obligatorio}) es {@code false}.
     */
    static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    static DefinicionFormato leer(JsonNode definicion) {
        return JSON.treeToValue(definicion, DefinicionFormato.class);
    }

    /** Revisa que la definición esté bien armada. Se llama al cargar el catálogo: un error impide arrancar. */
    void verificar(String formato) {
        exigir(secciones != null && !secciones.isEmpty(), formato, "no tiene secciones");
        Set<String> clavesSeccion = new HashSet<>();
        for (Seccion s : secciones) {
            String donde = formato + "/" + s.clave();
            exigir(clave(s.clave()) && clavesSeccion.add(s.clave()), donde, "clave de sección inválida o repetida");
            exigir(texto(s.titulo()), donde, "la sección no tiene título");
            exigir(s.campos() != null && !s.campos().isEmpty(), donde, "la sección no tiene campos");
            Map<String, Campo> anteriores = new java.util.HashMap<>();
            for (Campo c : s.campos()) {
                String dondeCampo = donde + "/" + c.clave();
                exigir(clave(c.clave()) && !anteriores.containsKey(c.clave()), dondeCampo, "clave de campo inválida o repetida");
                exigir(c.tipo() != null && texto(c.etiqueta()), dondeCampo, "falta el tipo o la etiqueta");
                verificarCampo(c, dondeCampo);
                if (c.visibleSi() != null) {
                    // Solo de un campo anterior: así el orden de evaluación es el de la pantalla
                    exigir(anteriores.containsKey(c.visibleSi().campo()) && c.visibleSi().igual() != null, dondeCampo,
                            "visibleSi debe apuntar a un campo anterior de la sección, con un valor");
                }
                anteriores.put(c.clave(), c);
            }
        }
    }

    private static void verificarCampo(Campo c, String donde) {
        switch (c.tipo()) {
            case SELECCION, MULTIPLE -> exigir(opciones(c.opciones()), donde, "faltan las opciones");
            case NUMERO -> exigir(c.min() == null || c.max() == null || c.min().compareTo(c.max()) <= 0, donde, "min mayor que max");
            case TABLA -> {
                exigir(c.columnas() != null && !c.columnas().isEmpty(), donde, "la tabla no tiene columnas");
                Set<String> claves = new HashSet<>();
                for (Columna col : c.columnas()) {
                    exigir(clave(col.clave()) && !col.clave().equals("fila") && claves.add(col.clave()) && texto(col.etiqueta()),
                            donde, "columna inválida o repetida: " + col.clave());
                    exigir(TIPOS_COLUMNA.contains(col.tipo()), donde, "tipo de columna no admitido: " + col.tipo());
                    exigir(col.tipo() != Tipo.SELECCION || opciones(col.opciones()), donde, "faltan las opciones de " + col.clave());
                }
                if (c.filas() != null) {
                    exigir(!c.filas().isEmpty() && c.filas().stream().map(Fila::clave).distinct().count() == c.filas().size()
                            && c.filas().stream().allMatch(f -> clave(f.clave()) && texto(f.etiqueta())), donde, "filas inválidas");
                }
            }
            case ESCALA -> {
                exigir(c.items() != null && !c.items().isEmpty(), donde, "la escala no tiene ítems");
                exigir(c.items().stream().map(ItemEscala::clave).distinct().count() == c.items().size(), donde, "ítems repetidos");
                for (ItemEscala i : c.items()) {
                    exigir(clave(i.clave()) && texto(i.etiqueta()) && i.opciones() != null && !i.opciones().isEmpty(), donde,
                            "ítem inválido: " + i.clave());
                    exigir(i.opciones().stream().map(OpcionEscala::puntaje).distinct().count() == i.opciones().size(), donde,
                            "puntajes repetidos en " + i.clave());
                }
                if (c.rangos() != null) {
                    exigir(c.rangos().stream().allMatch(r -> r.min() <= r.max() && texto(r.interpretacion())), donde, "rangos inválidos");
                }
            }
            default -> {
            }
        }
    }

    Map<String, Seccion> seccionesPorClave() {
        return secciones.stream().collect(Collectors.toMap(Seccion::clave, Function.identity()));
    }

    private static boolean clave(String s) {
        return s != null && s.matches("[a-z][a-z0-9_]{0,39}");
    }

    private static boolean texto(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean opciones(List<String> o) {
        return o != null && !o.isEmpty() && o.stream().allMatch(DefinicionFormato::texto) && Set.copyOf(o).size() == o.size();
    }

    private static void exigir(boolean condicion, String donde, String problema) {
        if (!condicion) {
            throw new IllegalStateException("Formato mal definido (" + donde + "): " + problema);
        }
    }
}
