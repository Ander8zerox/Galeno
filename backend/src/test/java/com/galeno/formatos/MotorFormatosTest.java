package com.galeno.formatos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

import tools.jackson.databind.JsonNode;

class MotorFormatosTest {

    private static final String DEFINICION = """
            {"secciones": [
              {"clave": "habitos", "titulo": "Hábitos", "campos": [
                {"clave": "cepillado", "tipo": "seleccion", "etiqueta": "Cepillado", "opciones": ["1 vez", "2 veces"], "obligatorio": true},
                {"clave": "bruxismo", "tipo": "si_no", "etiqueta": "Bruxismo"},
                {"clave": "cual", "tipo": "texto", "etiqueta": "¿Desde cuándo?", "obligatorio": true,
                 "visibleSi": {"campo": "bruxismo", "igual": true}},
                {"clave": "habitos", "tipo": "multiple", "etiqueta": "Hábitos", "opciones": ["A", "B", "C"]},
                {"clave": "placa", "tipo": "numero", "etiqueta": "Placa", "unidad": "%", "min": 0, "max": 100},
                {"clave": "fecha", "tipo": "fecha", "etiqueta": "Fecha"}
              ]},
              {"clave": "examen", "titulo": "Examen", "campos": [
                {"clave": "estructuras", "tipo": "tabla", "etiqueta": "Estructuras",
                 "filas": [{"clave": "labios", "etiqueta": "Labios"}, {"clave": "lengua", "etiqueta": "Lengua"}],
                 "columnas": [{"clave": "estado", "etiqueta": "Estado", "tipo": "seleccion", "opciones": ["Normal", "Anormal"]},
                              {"clave": "hallazgo", "etiqueta": "Hallazgo", "tipo": "texto"}]},
                {"clave": "eva", "tipo": "escala", "etiqueta": "EVA", "obligatorio": true,
                 "items": [{"clave": "a", "etiqueta": "Ítem A", "opciones": [{"etiqueta": "No", "puntaje": 0}, {"etiqueta": "Sí", "puntaje": 2}]},
                           {"clave": "b", "etiqueta": "Ítem B", "opciones": [{"etiqueta": "No", "puntaje": 0}, {"etiqueta": "Sí", "puntaje": 1}]}],
                 "rangos": [{"min": 0, "max": 1, "interpretacion": "Bajo"}, {"min": 2, "max": 3, "interpretacion": "Alto"}]}
              ]}
            ]}""";

    private static DefinicionFormato definicion(String json) {
        DefinicionFormato d = DefinicionFormato.leer(DefinicionFormato.JSON.readTree(json));
        d.verificar("prueba");
        return d;
    }

    private static JsonNode json(String s) {
        return DefinicionFormato.JSON.readTree(s);
    }

    private static JsonNode normalizar(String respuestas) {
        return ValidadorRespuestas.normalizar("Prueba", definicion(DEFINICION), json(respuestas));
    }

    // --- catálogo ---

    @Test
    void laHistoriaOdontologicaDelCatalogoEstaBienDefinida() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/formatos/odonto.historia.v1.json")) {
            var archivo = CatalogoFormatos.leer(in, "odonto.historia.v1.json");
            assertThat(archivo.codigo()).isEqualTo("odonto.historia");
            assertThat(DefinicionFormato.leer(archivo.definicion()).secciones()).hasSize(5);
        }
    }

    @Test
    void elNombreDelArchivoDebeCoincidirConCodigoYVersion() {
        String archivo = """
                {"codigo": "odonto.historia", "nombre": "H", "especialidad": "O", "version": 2, "definicion": %s}"""
                .formatted(DEFINICION);
        assertThatThrownBy(() -> CatalogoFormatos.leer(entrada(archivo), "odonto.historia.v1.json"))
                .hasMessageContaining("debe llamarse odonto.historia.v2.json");
    }

    @Test
    void unaPropiedadMalEscritaEnLaDefinicionImpideArrancar() {
        assertThatThrownBy(() -> definicion(DEFINICION.replace("\"obligatorio\": true}", "\"obligatoria\": true}")))
                .hasMessageContaining("obligatoria");
    }

    @Test
    void rechazaDefinicionesMalArmadas() {
        assertThatThrownBy(() -> definicion(DEFINICION.replace("\"clave\": \"bruxismo\"", "\"clave\": \"cepillado\"")))
                .hasMessageContaining("repetida");
        assertThatThrownBy(() -> definicion(DEFINICION.replace("\"opciones\": [\"1 vez\", \"2 veces\"], ", "")))
                .hasMessageContaining("faltan las opciones");
        // visibleSi solo de un campo anterior
        assertThatThrownBy(() -> definicion(DEFINICION.replace("\"campo\": \"bruxismo\"", "\"campo\": \"placa\"")))
                .hasMessageContaining("visibleSi");
    }

    // --- al guardar ---

    @Test
    void guardaLimpioSinVaciosNiCamposOcultos() {
        JsonNode r = normalizar("""
                {"habitos": {"cepillado": "2 veces", "bruxismo": false, "cual": "Hace un año", "habitos": ["C", "A"],
                             "placa": 35, "fecha": "2026-11-04"},
                 "examen": {"estructuras": [{"fila": "labios", "estado": "Normal", "hallazgo": "  "},
                                            {"fila": "lengua", "estado": null, "hallazgo": ""}]}}""");

        // "cual" solo se ve con bruxismo; la fila de lengua quedó vacía
        assertThat(r.get("habitos").has("cual")).isFalse();
        assertThat(r.get("habitos").get("habitos").toString()).isEqualTo("[\"A\",\"C\"]");
        assertThat(r.get("examen").get("estructuras").toString()).isEqualTo("[{\"fila\":\"labios\",\"estado\":\"Normal\"}]");
    }

    @Test
    void unaSeccionVaciaNoSeGuarda() {
        assertThat(normalizar("{\"habitos\": {\"cepillado\": null, \"cual\": \"\"}}").isEmpty()).isTrue();
    }

    @Test
    void rechazaLoQueNoCorrespondeALaDefinicion() {
        assertProblema("{\"otra\": {}}", "la sección «otra» no existe");
        assertProblema("{\"habitos\": {\"nuevo\": 1}}", "el campo «nuevo» no existe");
        assertProblema("{\"habitos\": {\"cepillado\": \"4 veces\"}}", "Cepillado: opción no válida");
        assertProblema("{\"habitos\": {\"placa\": 120}}", "debe estar entre 0 y 100 %");
        assertProblema("{\"habitos\": {\"placa\": \"30\"}}", "se esperaba un número");
        assertProblema("{\"habitos\": {\"bruxismo\": \"sí\"}}", "se esperaba sí o no");
        assertProblema("{\"habitos\": {\"fecha\": \"04/11/2026\"}}", "AAAA-MM-DD");
        assertProblema("{\"examen\": {\"estructuras\": [{\"fila\": \"nariz\", \"estado\": \"Normal\"}]}}", "fila no válida");
        assertProblema("{\"examen\": {\"estructuras\": [{\"fila\": \"labios\", \"color\": \"rojo\"}]}}", "la columna «color» no existe");
        assertProblema("{\"examen\": {\"eva\": {\"a\": 1}}}", "Ítem A: puntaje no válido");
    }

    private static void assertProblema(String respuestas, String mensaje) {
        assertThatThrownBy(() -> normalizar(respuestas))
                .isInstanceOf(ApiProblem.class)
                .hasMessageContaining(mensaje);
    }

    // --- al firmar ---

    @Test
    void alFirmarExigeLosObligatoriosVisibles() {
        DefinicionFormato d = definicion(DEFINICION);

        assertThat(ValidadorRespuestas.faltantes(d, json("{}")))
                .containsExactly("Hábitos › Cepillado", "Examen › EVA");
        // Con bruxismo, "¿Desde cuándo?" se vuelve visible y por tanto obligatorio; la escala a medias no alcanza
        assertThat(ValidadorRespuestas.faltantes(d, normalizar("""
                {"habitos": {"cepillado": "1 vez", "bruxismo": true}, "examen": {"eva": {"a": 2}}}""")))
                .containsExactly("Hábitos › ¿Desde cuándo?", "Examen › EVA");
        assertThat(ValidadorRespuestas.faltantes(d, normalizar("""
                {"habitos": {"cepillado": "1 vez", "bruxismo": true, "cual": "Hace un año"},
                 "examen": {"eva": {"a": 2, "b": 0}}}"""))).isEmpty();
    }

    @Test
    void laEscalaSumaEInterpreta() {
        var eva = definicion(DEFINICION).secciones().get(1).campos().get(1);

        assertThat(ValidadorRespuestas.total(eva, json("{\"a\": 2, \"b\": 1}")))
                .isEqualTo(new ValidadorRespuestas.Total(3, 3, "Alto"));
        assertThat(ValidadorRespuestas.total(eva, json("{\"a\": 2}"))).isNull();
    }

    // --- PDF ---

    @Test
    void elPdfMuestraSoloLoDiligenciadoYEscapaElTexto() {
        String html = PresentacionFormato.html("Historia odontológica", definicion(DEFINICION), normalizar("""
                {"habitos": {"cepillado": "2 veces", "bruxismo": true, "cual": "<b>niño</b>", "placa": 35.50,
                             "fecha": "2026-11-04"},
                 "examen": {"eva": {"a": 2, "b": 0}}}"""));

        assertThat(html).contains("<h2>Historia odontológica</h2>", "Bruxismo", "Sí", "&lt;b&gt;niño&lt;/b&gt;",
                "35.5 %", "04/11/2026", "2 / 3", "Alto", "Ítem A: Sí (2)");
        assertThat(html).doesNotContain("Estructuras");
        assertThat(PresentacionFormato.html("Vacío", definicion(DEFINICION), json("{}"))).isEmpty();
    }

    private static InputStream entrada(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
