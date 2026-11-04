package com.galeno.formatos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

import tools.jackson.databind.JsonNode;

class OdontogramaTest {

    private static JsonNode json(String s) {
        return DefinicionFormato.JSON.readTree(s);
    }

    private static JsonNode normalizar(String s) {
        return Odontograma.normalizar("Odontograma", json(s));
    }

    @Test
    void numeracionFdi() {
        assertThat(Odontograma.PERMANENTES).hasSize(32).startsWith(11, 12).contains(18, 21, 38, 48).doesNotContain(19, 51);
        assertThat(Odontograma.TEMPORALES).hasSize(20).contains(51, 55, 65, 75, 85).doesNotContain(56, 11);
        // Superior: 18…11 | 21…28; en mixta, las temporales van entre las dos arcadas permanentes
        assertThat(Odontograma.filas("permanente").get(0)).startsWith(18, 17).endsWith(27, 28);
        assertThat(Odontograma.filas("permanente").get(1)).startsWith(48).endsWith(38);
        assertThat(Odontograma.filas("mixta")).extracting(f -> f.get(0)).containsExactly(18, 55, 85, 48);
    }

    @Test
    void guardaLimpioYEnOrden() {
        JsonNode v = normalizar("""
                {"denticion": "permanente", "dientes": {
                  "36": {"superficies": {"O": "caries", "M": "resina"}, "hallazgos": ["endodoncia", "corona"], "nota": "  dolor  "},
                  "11": {"superficies": {}, "hallazgos": [], "nota": ""},
                  "18": {"hallazgos": ["ausente"]}}}""");

        // Sin el diente vacío, en orden FDI, hallazgos en el orden del catálogo, superficies en V L M D O
        assertThat(v.toString()).isEqualTo("{\"denticion\":\"permanente\",\"dientes\":{"
                + "\"18\":{\"hallazgos\":[\"ausente\"]},"
                + "\"36\":{\"hallazgos\":[\"corona\",\"endodoncia\"],\"superficies\":{\"M\":\"resina\",\"O\":\"caries\"},\"nota\":\"dolor\"}}}");
    }

    @Test
    void sinDenticionNoHayOdontograma() {
        assertThat(Odontograma.normalizar("Odontograma", json("{\"dientes\": {}}"))).isNull();
    }

    @Test
    void rechazaLoQueNoEsDelCatalogo() {
        assertProblema("{\"denticion\": \"adulta\"}", "dentición no válida");
        assertProblema("{\"denticion\": \"permanente\", \"dientes\": {\"55\": {\"hallazgos\": [\"corona\"]}}}",
                "el diente 55 no está en la dentición permanente");
        assertProblema("{\"denticion\": \"temporal\", \"dientes\": {\"19\": {}}}", "el diente 19");
        assertProblema("{\"denticion\": \"permanente\", \"dientes\": {\"36\": {\"superficies\": {\"X\": \"caries\"}}}}",
                "superficie no válida");
        assertProblema("{\"denticion\": \"permanente\", \"dientes\": {\"36\": {\"superficies\": {\"O\": \"oro\"}}}}",
                "diente 36 › oclusal o incisal: hallazgo no válido");
        assertProblema("{\"denticion\": \"permanente\", \"dientes\": {\"36\": {\"hallazgos\": [\"perdido\"]}}}",
                "hallazgo no válido");
        assertProblema("{\"denticion\": \"permanente\", \"dientes\": {\"36\": {\"hallazgos\": [\"ausente\", \"corona\"]}}}",
                "un diente ausente no tiene otros hallazgos");
        assertProblema("{\"denticion\": \"permanente\", \"dientes\": {\"36\": {\"hallazgos\": [\"ausente\"], \"superficies\": {\"O\": \"caries\"}}}}",
                "un diente ausente");
    }

    private static void assertProblema(String v, String mensaje) {
        assertThatThrownBy(() -> normalizar(v)).isInstanceOf(ApiProblem.class).hasMessageContaining(mensaje);
    }

    @Test
    void listaDeHallazgosLegible() {
        List<String> h = Odontograma.hallazgos(normalizar("""
                {"denticion": "mixta", "dientes": {
                  "36": {"hallazgos": ["endodoncia"], "superficies": {"O": "caries", "M": "caries", "D": "resina"}},
                  "55": {"nota": "Movilidad"},
                  "18": {"hallazgos": ["ausente"]}}}"""));

        assertThat(h).containsExactly(
                "18: ausente",
                "36: endodoncia; caries mesial y oclusal o incisal; obturación en resina distal",
                "55: Movilidad");
    }

    @Test
    void elFormatoDelCatalogoUsaElOdontogramaYLoArrastra() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/formatos/odonto.odontograma.v1.json")) {
            var a = CatalogoFormatos.leer(in, "odonto.odontograma.v1.json");
            var campo = DefinicionFormato.leer(a.definicion()).secciones().get(0).campos().get(0);
            assertThat(campo.tipo()).isEqualTo(DefinicionFormato.Tipo.ODONTOGRAMA);
            assertThat(campo.arrastrar()).isTrue();
        }
    }

    @Test
    void elPdfDibujaLaCuadriculaYLaLista() throws Exception {
        DefinicionFormato d;
        try (InputStream in = getClass().getResourceAsStream("/formatos/odonto.odontograma.v1.json")) {
            d = DefinicionFormato.leer(CatalogoFormatos.leer(in, "odonto.odontograma.v1.json").definicion());
        }
        JsonNode r = ValidadorRespuestas.normalizar("Odontograma", d, json("""
                {"odontograma": {"dientes": {"denticion": "permanente", "dientes": {
                  "16": {"superficies": {"M": "caries"}}, "26": {"superficies": {"M": "resina"}}, "48": {"hallazgos": ["ausente"]}}},
                  "observaciones": "Higiene regular"}}"""));

        String html = PresentacionFormato.html("Odontograma", d, r);

        assertThat(html).contains("table class=\"odontograma\"", "dentición permanente", "16: caries mesial",
                "26: obturación en resina mesial", "48: ausente", "table class=\"diente ausente\"");
        // En el orden de la definición y sin repetir el título del formato en la sección
        assertThat(html.indexOf("Observaciones")).isGreaterThan(html.indexOf("table class=\"odontograma\""));
        assertThat(html).doesNotContain("<h3 class=\"seccion-formato\">Odontograma</h3>");
        // 16 (lado derecho del paciente): la mesial es la celda derecha de la fila del medio; 26: la izquierda
        String d16 = html.substring(html.indexOf(">16<"), html.indexOf(">15<"));
        assertThat(d16).contains("<tr><td></td><td></td><td style=\"background: #d64545\"></td></tr>");
        String d26 = html.substring(html.indexOf(">26<"), html.indexOf(">27<"));
        assertThat(d26).contains("<tr><td style=\"background: #2f6fd6\"></td><td></td><td></td></tr>");
    }
}
