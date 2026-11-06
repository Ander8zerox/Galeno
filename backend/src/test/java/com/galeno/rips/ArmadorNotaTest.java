package com.galeno.rips;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.galeno.rips.ArmadorNota.ItemNota;
import com.galeno.rips.ArmadorNota.Tipo;

class ArmadorNotaTest {

    /** El RIPS de un lote: un usuario con una consulta (con cuota moderadora) y dos procedimientos. */
    private static Map<String, Object> lote() {
        Map<String, Object> consulta = new LinkedHashMap<>(Map.of("codConsulta", "890201", "consecutivo", 1,
                "fechaInicioAtencion", "2026-11-03 09:30", "codDiagnosticoPrincipal", "J029", "vrServicio", 50000,
                "conceptoRecaudo", "02", "valorPagoModerador", 4500, "finalidadTecnologiaSalud", "15"));
        consulta.put("codDiagnosticoRelacionado1", null);
        consulta.put("numFEVPagoModerador", "FM1");
        Map<String, Object> p1 = new LinkedHashMap<>(Map.of("codProcedimiento", "232102", "consecutivo", 1,
                "fechaInicioAtencion", "2026-11-03 09:30", "codDiagnosticoPrincipal", "K021", "vrServicio", 38000,
                "conceptoRecaudo", "05", "valorPagoModerador", 0));
        Map<String, Object> p2 = new LinkedHashMap<>(p1);
        p2.put("consecutivo", 2);
        Map<String, Object> usuario = new LinkedHashMap<>(Map.of("consecutivo", 1, "tipoDocumentoIdentificacion", "CC",
                "numDocumentoIdentificacion", "1000000001"));
        usuario.put("servicios", Map.of("consultas", List.of(consulta), "procedimientos", List.of(p1, p2)));
        Map<String, Object> otro = new LinkedHashMap<>(Map.of("consecutivo", 2, "tipoDocumentoIdentificacion", "CC",
                "numDocumentoIdentificacion", "1000000002"));
        otro.put("servicios", Map.of("consultas", List.of(new LinkedHashMap<>(consulta))));
        Map<String, Object> json = new LinkedHashMap<>(Map.of("numDocumentoIdObligado", "900123456", "numFactura", "FE100"));
        json.put("usuarios", List.of(usuario, otro));
        return json;
    }

    private static final Map<String, Set<String>> VALIDOS = Map.of("cie10", Set.of("J00X", "R509", "J029"), "finalidad", Set.of("11"));

    private static ArmadorNota.Resultado armar(Tipo tipo, List<ItemNota> items, Map<String, BigDecimal> acreditado,
                                               ArmadorNota.XmlNota xml) {
        return ArmadorNota.armar(new ArmadorNota.Entrada(lote(), tipo, "NC-7", items, acreditado, VALIDOS, xml));
    }

    private static ItemNota item(String clave, String valor, Map<String, String> datos) {
        return new ItemNota(clave, valor == null ? null : new BigDecimal(valor), datos);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> servicios(ArmadorNota.Resultado r, int usuario) {
        return (Map<String, Object>) ((List<Map<String, Object>>) r.json().get("usuarios")).get(usuario).get("servicios");
    }

    @Test
    void listaLosServiciosDelLoteConSuClave() {
        assertThat(ArmadorNota.servicios(lote())).extracting(ArmadorNota.ServicioLote::clave)
                .containsExactly("1-consultas-1", "1-procedimientos-1", "1-procedimientos-2", "2-consultas-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void laNotaCreditoLlevaSoloLoAfectadoConElValorGlosadoYSinPagosModeradores() {
        var r = armar(Tipo.NC, List.of(item("1-procedimientos-2", "20000", null), item("1-consultas-1", "50000", null)),
                Map.of(), null);

        assertThat(r.hayRechazos()).isFalse();
        assertThat(r.total()).isEqualByComparingTo("70000");
        assertThat(r.json()).containsEntry("numFactura", "FE100").containsEntry("tipoNota", "NC").containsEntry("numNota", "NC-7");
        assertThat((List<?>) r.json().get("usuarios")).hasSize(1);
        Map<String, Object> s = servicios(r, 0);
        Map<String, Object> consulta = ((List<Map<String, Object>>) s.get("consultas")).get(0);
        assertThat(consulta).containsEntry("vrServicio", 50000L).containsEntry("conceptoRecaudo", "05")
                .containsEntry("valorPagoModerador", 0L).containsEntry("numFEVPagoModerador", null)
                .containsEntry("consecutivo", 1);
        // Solo el procedimiento 2, con su consecutivo de la factura
        assertThat((List<Map<String, Object>>) s.get("procedimientos")).singleElement()
                .satisfies(p -> assertThat(p).containsEntry("consecutivo", 2).containsEntry("vrServicio", 20000L));
    }

    @Test
    void laNotaCreditoNoPasaLoQueQuedaPorAcreditar() {
        var r = armar(Tipo.NC, List.of(item("1-consultas-1", "30000", null)), Map.of("1-consultas-1", new BigDecimal("25000")),
                null);
        assertThat(r.hallazgos()).anyMatch(h -> h.mensaje().contains("pasa lo que queda por acreditar"));
        assertThat(armar(Tipo.NC, List.of(item("1-consultas-1", null, null)), Map.of(), null).hayRechazos()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void laNotaDeAjusteCorrigeDatosYDejaLosValores() {
        var r = armar(Tipo.NA, List.of(item("1-consultas-1", null, Map.of("codDiagnosticoPrincipal", "j00x",
                "codDiagnosticoRelacionado1", "R509", "finalidadTecnologiaSalud", "11"))), Map.of(), null);

        assertThat(r.hayRechazos()).isFalse();
        Map<String, Object> c = ((List<Map<String, Object>>) servicios(r, 0).get("consultas")).get(0);
        assertThat(c).containsEntry("codDiagnosticoPrincipal", "J00X").containsEntry("codDiagnosticoRelacionado1", "R509")
                .containsEntry("finalidadTecnologiaSalud", "11").containsEntry("vrServicio", 50000)
                .containsEntry("valorPagoModerador", 4500).containsEntry("codConsulta", "890201");
    }

    @Test
    void laNotaDeAjusteRechazaLoQueNoSePuedeCorregir() {
        // Sin cambios
        assertThat(armar(Tipo.NA, List.of(item("1-consultas-1", null, Map.of("codDiagnosticoPrincipal", "J029"))),
                Map.of(), null).hallazgos()).anyMatch(h -> h.mensaje().contains("No hay datos corregidos"));
        // El CUPS no se corrige
        assertThat(armar(Tipo.NA, List.of(item("1-consultas-1", null, Map.of("codConsulta", "890301"))), Map.of(), null)
                .hallazgos()).anyMatch(h -> h.mensaje().contains("no se corrige"));
        // Un diagnóstico que no existe, o un relacionado igual al principal (RVC086)
        assertThat(armar(Tipo.NA, List.of(item("1-consultas-1", null, Map.of("codDiagnosticoPrincipal", "Z999"))),
                Map.of(), null).hayRechazos()).isTrue();
        assertThat(armar(Tipo.NA, List.of(item("1-consultas-1", null, Map.of("codDiagnosticoRelacionado1", "J029"))),
                Map.of(), null).hallazgos()).anyMatch(h -> h.mensaje().contains("RVC086"));
    }

    @Test
    void laNotaDebitoSoloCambiaValores() {
        assertThat(armar(Tipo.ND, List.of(item("2-consultas-1", "5000", null)), Map.of(), null).hayRechazos()).isFalse();
        assertThat(armar(Tipo.ND, List.of(item("2-consultas-1", "5000", Map.of("finalidadTecnologiaSalud", "11"))),
                Map.of(), null).hallazgos()).anyMatch(h -> h.mensaje().contains("solo cambia valores"));
    }

    @Test
    void revisaElNumeroLosServiciosYElXmlDeLaNota() {
        assertThat(ArmadorNota.armar(new ArmadorNota.Entrada(lote(), Tipo.NC, "NC 7", List.of(), Map.of(), VALIDOS, null))
                .hallazgos()).extracting(ArmadorNota.HallazgoNota::mensaje)
                .anyMatch(m -> m.contains("número de la nota")).anyMatch(m -> m.contains("al menos un servicio"));
        assertThat(armar(Tipo.NC, List.of(item("9-consultas-1", "1", null)), Map.of(), null).hayRechazos()).isTrue();

        var xmlMalo = new ArmadorNota.XmlNota("NC-8", "FE999", new BigDecimal("1"));
        assertThat(armar(Tipo.NC, List.of(item("1-consultas-1", "1000", null)), Map.of(), xmlMalo).hallazgos())
                .hasSize(3);
        var xmlBueno = new ArmadorNota.XmlNota("NC-7", "FE100", new BigDecimal("1000.00"));
        assertThat(armar(Tipo.NC, List.of(item("1-consultas-1", "1000", null)), Map.of(), xmlBueno).hayRechazos()).isFalse();
    }
}
