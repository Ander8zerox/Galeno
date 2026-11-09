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

    // --- formatos transversales: documento, fecha calculada y firma ---

    @Test
    void losFormatosTransversalesDelCatalogoEstanBienDefinidos() throws Exception {
        for (String codigo : java.util.List.of("general.incapacidad", "general.remision", "general.contrarreferencia",
                "general.consentimiento")) {
            try (InputStream in = getClass().getResourceAsStream("/formatos/" + codigo + ".v1.json")) {
                var archivo = CatalogoFormatos.leer(in, codigo + ".v1.json");
                assertThat(DefinicionFormato.leer(archivo.definicion()).documento().titulo()).isNotBlank();
            }
        }
    }

    private static final String INCAPACIDAD = """
            {"secciones": [{"clave": "inc", "titulo": "Incapacidad", "campos": [
              {"clave": "inicio", "tipo": "fecha", "etiqueta": "Desde"},
              {"clave": "dias", "tipo": "numero", "etiqueta": "Días", "min": 1, "max": 540},
              {"clave": "fin", "tipo": "fecha", "etiqueta": "Hasta", "calculo": {"desde": "inicio", "dias": "dias"}},
              {"clave": "firma", "tipo": "firma", "etiqueta": "Firma del paciente", "obligatorio": true}
            ]}]}""";

    @Test
    void laFechaCalculadaLaPoneElServidor() {
        JsonNode r = ValidadorRespuestas.normalizar("Inc", definicion(INCAPACIDAD),
                json("{\"inc\": {\"inicio\": \"2026-11-30\", \"dias\": 3, \"fin\": \"2020-01-01\"}}"));

        // 30 de noviembre más 3 días: hasta el 2 de diciembre (incluido); lo que mandó el navegador se ignora
        assertThat(r.get("inc").get("fin").stringValue()).isEqualTo("2026-12-02");
        JsonNode sinDias = ValidadorRespuestas.normalizar("Inc", definicion(INCAPACIDAD), json("{\"inc\": {\"inicio\": \"2026-11-30\"}}"));
        assertThat(sinDias.get("inc").has("fin")).isFalse();
    }

    @Test
    void elCalculoDebeApuntarAUnaFechaYUnNumeroAnteriores() {
        String mal = INCAPACIDAD.replace("\"calculo\": {\"desde\": \"inicio\", \"dias\": \"dias\"}", "\"calculo\": {\"desde\": \"dias\", \"dias\": \"inicio\"}");
        assertThatThrownBy(() -> definicion(mal)).hasMessageContaining("calculo");
    }

    private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    @Test
    void laFirmaGuardaImagenNombreYFecha() {
        JsonNode r = ValidadorRespuestas.normalizar("Inc", definicion(INCAPACIDAD),
                json("{\"inc\": {\"firma\": {\"imagen\": \"" + PNG + "\", \"nombre\": \" Ana Ficticia \"}}}"));

        JsonNode f = r.get("inc").get("firma");
        assertThat(f.get("imagen").stringValue()).isEqualTo(PNG);
        assertThat(f.get("nombre").stringValue()).isEqualTo("Ana Ficticia");
        assertThat(java.time.Instant.parse(f.get("firmadaEn").stringValue())).isBeforeOrEqualTo(java.time.Instant.now());
        // Sin imagen, la firma está vacía y al firmar la atención falta
        JsonNode vacia = ValidadorRespuestas.normalizar("Inc", definicion(INCAPACIDAD), json("{\"inc\": {\"firma\": {\"imagen\": \"\"}}}"));
        assertThat(ValidadorRespuestas.faltantes(definicion(INCAPACIDAD), vacia)).containsExactly("Incapacidad › Firma del paciente");
    }

    @Test
    void laFechaDeLaFirmaLaPoneElServidorYNoCambiaSiLaFirmaEsLaMisma() {
        JsonNode previas = json("{\"inc\": {\"firma\": {\"imagen\": \"" + PNG + "\", \"firmadaEn\": \"2026-11-30T15:00:00Z\"}}}");
        // El navegador manda otra fecha: se ignora
        var nuevas = ValidadorRespuestas.normalizar("Inc", definicion(INCAPACIDAD),
                json("{\"inc\": {\"firma\": {\"imagen\": \"" + PNG + "\", \"firmadaEn\": \"2020-01-01T00:00:00Z\"}}}"));
        assertThat(nuevas.get("inc").get("firma").get("firmadaEn").stringValue()).isNotEqualTo("2020-01-01T00:00:00Z");

        // La misma imagen que ya estaba guardada conserva la fecha original
        ValidadorRespuestas.conservarFechasDeFirma(definicion(INCAPACIDAD), nuevas, previas);
        assertThat(nuevas.get("inc").get("firma").get("firmadaEn").stringValue()).isEqualTo("2026-11-30T15:00:00Z");

        // Una firma nueva (otra imagen) toma la fecha de ahora
        var otra = ValidadorRespuestas.normalizar("Inc", definicion(INCAPACIDAD),
                json("{\"inc\": {\"firma\": {\"imagen\": \"" + PNG.replace("iVBOR", "iVBOS") + "\"}}}"));
        ValidadorRespuestas.conservarFechasDeFirma(definicion(INCAPACIDAD), otra, previas);
        assertThat(otra.get("inc").get("firma").get("firmadaEn").stringValue()).isNotEqualTo("2026-11-30T15:00:00Z");
    }

    @Test
    void laFirmaDebeSerUnaImagenPng() {
        assertThatThrownBy(() -> ValidadorRespuestas.normalizar("Inc", definicion(INCAPACIDAD),
                json("{\"inc\": {\"firma\": {\"imagen\": \"data:text/html;base64,PHNjcmlwdD4=\"}}}")))
                .isInstanceOf(ApiProblem.class).hasMessageContaining("PNG");
        assertThatThrownBy(() -> ValidadorRespuestas.normalizar("Inc", definicion(INCAPACIDAD),
                json("{\"inc\": {\"firma\": {\"imagen\": \"data:image/png;base64,\\\"><script>\"}}}")))
                .isInstanceOf(ApiProblem.class).hasMessageContaining("PNG");
    }

    @Test
    void elPdfMuestraLaFirmaConSuNombre() {
        // Tal como queda guardado (la fecha la puso el servidor al firmar)
        JsonNode r = json("{\"inc\": {\"inicio\": \"2026-11-30\", \"dias\": 3, \"fin\": \"2026-12-02\", \"firma\": {\"imagen\": \"" + PNG
                        + "\", \"nombre\": \"Ana <b>Ficticia</b>\", \"firmadaEn\": \"2026-11-30T15:00:00Z\"}}}");

        String html = PresentacionFormato.html("Incapacidad", definicion(INCAPACIDAD), r);
        assertThat(html).contains("02/12/2026").contains("<img class=\"firma-imagen\" src=\"" + PNG)
                .contains("Ana &lt;b&gt;Ficticia&lt;/b&gt;").contains("Firmado el 30/11/2026 10:00");
    }
}
