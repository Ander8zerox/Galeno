package com.galeno.rips;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.galeno.historia.Diagnostico;
import com.galeno.historia.HistoriaService.AtencionRips;
import com.galeno.historia.HistoriaService.DiagnosticoVista;
import com.galeno.historia.HistoriaService.ProcedimientoVista;
import com.galeno.rips.ArmadorRips.AtencionLote;
import com.galeno.rips.ArmadorRips.Fila;
import com.galeno.rips.ArmadorRips.HallazgoRips;
import com.galeno.rips.ArmadorRips.Nivel;
import com.galeno.rips.CobrosService.CobroVista;

class ArmadorRipsTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final UUID PAGADOR = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");
    /** 2026-11-03 09:30 en Bogotá. */
    private static final Instant FECHA = Instant.parse("2026-11-03T14:30:00Z");
    private static final ArmadorRips.PrestadorRips PRESTADOR =
            new ArmadorRips.PrestadorRips("110010000101", "328", "01", "01", "15", "38", "02");
    private static final Map<String, Fila> CUPS = Map.of(
            "890201", new Fila(true, Map.of("uso", "AC", "sexo", "Z", "maximo", "1")),
            "232102", new Fila(true, Map.of("uso", "AP", "sexo", "Z", "maximo", "4")),
            "895100", new Fila(true, Map.of("uso", "AP", "sexo", "F", "maximo", "1")));

    private static ArmadorRips.PagadorRips eps(String modalidad) {
        return new ArmadorRips.PagadorRips(PAGADOR, "Nueva EPS", Pagador.Tipo.EPS, "01", modalidad);
    }

    private static ArmadorRips.PagadorRips particular() {
        return new ArmadorRips.PagadorRips(PAGADOR, "Particular", Pagador.Tipo.PARTICULAR, "15", "04");
    }

    private static ArmadorRips.PacienteRips paciente(String tipoUsuario) {
        return new ArmadorRips.PacienteRips(UUID.randomUUID(), "Ana Prueba", "CC", "1000000001",
                LocalDate.of(1990, 5, 1), "F", tipoUsuario, "170", "11001", "01", "170", List.of());
    }

    private static CobroVista cobro(String concepto, String valor) {
        return new CobroVista(CobrosService.Origen.ATENCION, true, PAGADOR, "Nueva EPS", Pagador.Tipo.EPS, false, "AUT-1",
                concepto, new BigDecimal(valor), null, null, false);
    }

    private static ProcedimientoVista proc(String codigo, int cantidad, String valor) {
        return new ProcedimientoVista(codigo, "Descripción " + codigo, cantidad, valor == null ? null : new BigDecimal(valor),
                null, null, codigo.startsWith("890"));
    }

    private static AtencionLote atencion(ArmadorRips.PacienteRips p, CobroVista c, List<DiagnosticoVista> dx,
                                         ProcedimientoVista... procs) {
        AtencionRips a = new AtencionRips(UUID.randomUUID(), p.id(), null, UUID.randomUUID(), "Dra. Prueba", FECHA,
                false, dx, List.of(procs));
        return new AtencionLote(a, p, c, new ArmadorRips.ProfesionalRips("CC", "52000000"), ArmadorRips.Ajustes.NINGUNO,
                Map.of());
    }

    private static List<DiagnosticoVista> dx(String principal, String... relacionados) {
        List<DiagnosticoVista> l = new java.util.ArrayList<>();
        l.add(new DiagnosticoVista(principal, "Dx", true, Diagnostico.Tipo.CONFIRMADO_NUEVO));
        for (String r : relacionados) {
            l.add(new DiagnosticoVista(r, "Dx", false, Diagnostico.Tipo.IMPRESION_DIAGNOSTICA));
        }
        return l;
    }

    private static ArmadorRips.Resultado armar(ArmadorRips.PagadorRips pagador, boolean sinFactura,
                                               ArmadorRips.DatosXml xml, AtencionLote... atenciones) {
        return ArmadorRips.armar(new ArmadorRips.Entrada("900123456-7", sinFactura, sinFactura ? null : "FE-100",
                pagador, PRESTADOR, List.of(atenciones), CUPS, Map.of(), Map.of(), xml, BOGOTA, AHORA));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(ArmadorRips.Resultado r, int usuario, String cual) {
        var u = ((List<Map<String, Object>>) r.json().get("usuarios")).get(usuario);
        return (List<Map<String, Object>>) ((Map<String, Object>) u.get("servicios")).get(cual);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> usuario(ArmadorRips.Resultado r, int i) {
        return ((List<Map<String, Object>>) r.json().get("usuarios")).get(i);
    }

    @Test
    void armaLaConsultaConLosCamposDelDocumentoTecnico() {
        var r = armar(eps("04"), false, null,
                atencion(paciente("01"), cobro("02", "4500"), dx("J029", "R509"), proc("890201", 1, "50000")));

        assertThat(r.hallazgos()).noneMatch(h -> h.nivel() == Nivel.RECHAZO);
        assertThat(r.json()).containsEntry("numDocumentoIdObligado", "900123456").containsEntry("numFactura", "FE-100")
                .containsEntry("tipoNota", null);
        Map<String, Object> u = usuario(r, 0);
        assertThat(u).containsEntry("tipoUsuario", "01").containsEntry("codSexo", "F").containsEntry("incapacidad", "NO")
                .containsEntry("consecutivo", 1).containsEntry("registroSIRAS", null);
        assertThat(u.keySet()).first().isEqualTo("consecutivo"); // como en el ejemplo oficial
        Map<String, Object> c = lista(r, 0, "consultas").get(0);
        assertThat(c).containsEntry("codPrestador", "110010000101").containsEntry("fechaInicioAtencion", "2026-11-03 09:30")
                .containsEntry("codConsulta", "890201").containsEntry("codServicio", 328)
                .containsEntry("causaMotivoAtencion", "38").containsEntry("codDiagnosticoPrincipal", "J029")
                .containsEntry("codDiagnosticoRelacionado1", "R509").containsEntry("codDiagnosticoRelacionado2", null)
                .containsEntry("tipoDiagnosticoPrincipal", "02").containsEntry("numDocumentoIdentificacion", "52000000")
                .containsEntry("vrServicio", 50000L).containsEntry("conceptoRecaudo", "02")
                .containsEntry("valorPagoModerador", 4500L).containsEntry("numAutorizacion", "AUT-1")
                .containsEntry("consecutivo", 1);
        assertThat(usuario(r, 0)).doesNotContainKey("procedimientos");
    }

    @Test
    void unaCantidadDeDosSonDosRegistrosYElPagoModeradorVaSoloEnElPrimero() {
        var r = armar(eps("04"), false, null, atencion(paciente("01"), cobro("01", "8000"), dx("K029"),
                proc("890201", 1, "50000"), proc("232102", 2, "45000")));

        assertThat(lista(r, 0, "consultas")).singleElement()
                .satisfies(c -> assertThat(c).containsEntry("valorPagoModerador", 8000L));
        assertThat(lista(r, 0, "procedimientos")).hasSize(2).allSatisfy(p -> assertThat(p)
                .containsEntry("codProcedimiento", "232102").containsEntry("vrServicio", 45000L)
                .containsEntry("conceptoRecaudo", "05").containsEntry("valorPagoModerador", 0L)
                .containsEntry("viaIngresoServicioSalud", "02").doesNotContainKey("causaMotivoAtencion"));
        assertThat(lista(r, 0, "procedimientos")).extracting(p -> p.get("consecutivo")).containsExactly(1, 2);
    }

    @Test
    void fueraDePagoPorEventoElValorDelServicioEsCero() {
        var r = armar(eps("03"), false, null,
                atencion(paciente("01"), cobro("05", "0"), dx("J029"), proc("890201", 1, "50000")));

        assertThat(lista(r, 0, "consultas").get(0)).containsEntry("vrServicio", 0L);
        assertThat(r.hayRechazos()).isFalse();
    }

    @Test
    void porEventoSinPrecioNoSeGeneraPeroTomaElPrecioVigenteSiLoHay() {
        var p = paciente("01");
        var sinPrecio = atencion(p, cobro("05", "0"), dx("J029"), proc("890201", 1, null));
        assertThat(armar(eps("04"), false, null, sinPrecio).hallazgos())
                .anyMatch(h -> "RVC034".equals(h.regla()) && h.nivel() == Nivel.RECHAZO);

        var conLista = new AtencionLote(sinPrecio.atencion(), p, sinPrecio.cobro(), sinPrecio.profesional(),
                ArmadorRips.Ajustes.NINGUNO, Map.of("890201", new BigDecimal("42000")));
        var r = armar(eps("04"), false, null, conLista);
        assertThat(r.hayRechazos()).isFalse();
        assertThat(lista(r, 0, "consultas").get(0)).containsEntry("vrServicio", 42000L);
    }

    @Test
    void elRipsSinFacturaVaConTipoDeNotaRsYElValorPagadoPorElPaciente() {
        var c = new CobroVista(CobrosService.Origen.PACIENTE, false, PAGADOR, "Particular", Pagador.Tipo.PARTICULAR, false,
                null, "05", BigDecimal.ZERO, null, null, false);
        var r = armar(particular(), true, null, atencion(paciente("12"), c, dx("J029"), proc("890201", 1, "60000")));

        assertThat(r.json()).containsEntry("numFactura", null).containsEntry("tipoNota", "RS");
        assertThat(lista(r, 0, "consultas").get(0)).containsEntry("vrServicio", 60000L);
        assertThat(r.hayRechazos()).isFalse();
    }

    @Test
    void revisaLosCrucesDelNumeral18() {
        // Un cotizante (01) no se reporta con la cobertura de ARL (05)
        var arl = new ArmadorRips.PagadorRips(PAGADOR, "ARL", Pagador.Tipo.ARL, "05", "04");
        var r = armar(arl, false, null, atencion(paciente("01"), cobro("05", "0"), dx("J029"), proc("890201", 1, "60000")));
        assertThat(r.hallazgos()).anyMatch(h -> h.nivel() == Nivel.RECHAZO && h.mensaje().contains("tipo de usuario 01"));

        // Presupuesto máximo (02) solo admite pago por evento
        var pm = new ArmadorRips.PagadorRips(PAGADOR, "EPS", Pagador.Tipo.EPS, "02", "03");
        assertThat(armar(pm, false, null, atencion(paciente("01"), cobro("05", "0"), dx("J029"),
                proc("890201", 1, "1"))).hallazgos())
                .anyMatch(h -> h.nivel() == Nivel.RECHAZO && h.mensaje().contains("modalidad de pago 03"));
    }

    @Test
    void conElParticularElPacienteVaComoTipo12AunqueLaFichaDigaOtro() {
        var c = new CobroVista(CobrosService.Origen.ATENCION, true, PAGADOR, "Particular", Pagador.Tipo.PARTICULAR, false,
                null, "05", BigDecimal.ZERO, null, null, false);
        var afiliado = atencion(paciente("01"), c, dx("J029"), proc("890201", 1, "60000"));
        var r = armar(particular(), false, null, afiliado);
        assertThat(r.hayRechazos()).isFalse();
        assertThat(usuario(r, 0)).containsEntry("tipoUsuario", "12");

        // Ni siquiera hace falta tenerlo en la ficha
        var p = new ArmadorRips.PacienteRips(UUID.randomUUID(), "Sin Tipo", "CC", "1000000003", LocalDate.of(1990, 5, 1), "F",
                null, "170", "11001", "01", "170", List.of("Tipo de usuario"));
        assertThat(armar(particular(), false, null, atencion(p, c, dx("J029"), proc("890201", 1, "60000"))).hayRechazos())
                .isFalse();
    }

    @Test
    void elLesionadoDeTransitoLlevaElRegistroSiras() {
        var soat = new ArmadorRips.PagadorRips(PAGADOR, "Aseguradora SOAT", Pagador.Tipo.SOAT, "04", "04");
        var sin = new CobroVista(CobrosService.Origen.ATENCION, true, PAGADOR, "Aseguradora SOAT", Pagador.Tipo.SOAT, false,
                null, "05", BigDecimal.ZERO, null, null, true);
        var r = armar(soat, false, null, atencion(paciente("10"), sin, dx("S000"), proc("890201", 1, "50000")));
        assertThat(r.hallazgos()).anyMatch(h -> "RVC095".equals(h.regla()) && h.nivel() == Nivel.RECHAZO);

        var con = new CobroVista(CobrosService.Origen.ATENCION, true, PAGADOR, "Aseguradora SOAT", Pagador.Tipo.SOAT, false,
                null, "05", BigDecimal.ZERO, null, "SIRAS-321abc", true);
        r = armar(soat, false, null, atencion(paciente("10"), con, dx("S000"), proc("890201", 1, "50000")));
        assertThat(r.hayRechazos()).isFalse();
        assertThat(usuario(r, 0)).containsEntry("registroSIRAS", "SIRAS-321abc");

        // Sin accidente de tránsito no va, aunque el cobro lo traiga
        assertThat(usuario(armar(eps("04"), false, null, atencion(paciente("01"), cobro("05", "0"), dx("J029"),
                proc("890201", 1, "50000"))), 0)).containsEntry("registroSIRAS", null);
    }

    @Test
    void losRelacionadosNoRepitenElPrincipalNiEntreSi() {
        var r = armar(eps("04"), false, null, atencion(paciente("01"), cobro("05", "0"),
                dx("J029", "J029", "R509", "R509", "J00X", "I10X", "E119"), proc("890201", 1, "50000")));

        assertThat(lista(r, 0, "consultas").get(0)).containsEntry("codDiagnosticoRelacionado1", "R509")
                .containsEntry("codDiagnosticoRelacionado2", "J00X").containsEntry("codDiagnosticoRelacionado3", "I10X");
    }

    @Test
    void avisaLoQueElMinisterioSoloNotifica() {
        // Un hombre con un CUPS solo para mujeres y el mismo CUPS dos veces el mismo día
        var hombre = new ArmadorRips.PacienteRips(UUID.randomUUID(), "Luis Prueba", "TI", "1000000002",
                LocalDate.of(1980, 1, 1), "M", "01", "170", "11001", "01", "170", List.of());
        var r = armar(eps("04"), false, null,
                atencion(hombre, cobro("05", "0"), dx("N390"), proc("895100", 2, "30000")));

        List<HallazgoRips> avisos = r.hallazgos().stream().filter(h -> h.nivel() == Nivel.AVISO).toList();
        assertThat(avisos).extracting(HallazgoRips::regla).contains("RVC016", "RVC018", "RVC007");
        assertThat(r.hayRechazos()).isFalse();
    }

    @Test
    void sinDatosDelPacienteNiDocumentoDelProfesionalNoSeGenera() {
        var p = new ArmadorRips.PacienteRips(UUID.randomUUID(), "Ana Prueba", "CC", "1000000001",
                LocalDate.of(1990, 5, 1), "F", null, null, null, null, null, List.of("Tipo de usuario", "País de residencia"));
        var base = atencion(p, cobro("05", "0"), dx("J029"), proc("890201", 1, "50000"));
        var sinProfesional = new AtencionLote(base.atencion(), p, base.cobro(), null, ArmadorRips.Ajustes.NINGUNO, Map.of());

        var r = armar(eps("04"), false, null, sinProfesional);
        assertThat(r.hallazgos()).filteredOn(h -> h.nivel() == Nivel.RECHAZO).extracting(HallazgoRips::donde)
                .contains(ArmadorRips.Donde.PACIENTE, ArmadorRips.Donde.PROFESIONAL);
    }

    @Test
    void cruzaElXmlDeLaFactura() {
        var xml = new ArmadorRips.DatosXml("800999888", "FE-200", LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 30),
                new BigDecimal("1000"));
        var r = armar(eps("04"), false, xml,
                atencion(paciente("01"), cobro("02", "4500"), dx("J029"), proc("890201", 1, "50000")));

        assertThat(r.hallazgos()).extracting(HallazgoRips::regla).contains("RVC001", "RVC004", "RVC014", "RVC036");

        var bien = new ArmadorRips.DatosXml("900123456", "FE-100", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("4500.00"));
        assertThat(armar(eps("04"), false, bien, atencion(paciente("01"), cobro("02", "4500"), dx("J029"),
                proc("890201", 1, "50000"))).hayRechazos()).isFalse();
    }

    @Test
    void edadYTipoDeDocumentoConUnAnoDeTolerancia() {
        assertThat(ArmadorRips.documentoAcordeConEdad("RC", 7)).isTrue();
        assertThat(ArmadorRips.documentoAcordeConEdad("RC", 9)).isFalse();
        assertThat(ArmadorRips.documentoAcordeConEdad("TI", 17)).isTrue();
        assertThat(ArmadorRips.documentoAcordeConEdad("TI", 30)).isFalse();
        assertThat(ArmadorRips.documentoAcordeConEdad("CC", 17)).isTrue();
        assertThat(ArmadorRips.documentoAcordeConEdad("CC", 12)).isFalse();
        assertThat(ArmadorRips.documentoAcordeConEdad("PA", 3)).isTrue();
        assertThat(ArmadorRips.nitSinDigito("900.123.456-7")).isEqualTo("900123456");
    }
}
