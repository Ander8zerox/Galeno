package com.galeno.historia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

class AtencionTest {

    private static Atencion borrador() {
        return Atencion.nueva(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Dra. Prueba", null, null);
    }

    private static Atencion.Contenido contenido(String motivo, List<Diagnostico> dx) {
        return new Atencion.Contenido(motivo, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, dx);
    }

    private static Diagnostico dx(String codigo, boolean principal) {
        return new Diagnostico(codigo, "Descripción", principal, null);
    }

    @Test
    void firmaConMotivoYUnDiagnosticoPrincipal() {
        Atencion a = borrador();
        a.actualizar(contenido("Dolor de garganta", List.of(dx("J029", true), dx("R50X", false))));

        a.firmar("RM-1");

        assertThat(a.getEstado()).isEqualTo(Atencion.Estado.FIRMADA);
        assertThat(a.getRegistroMedico()).isEqualTo("RM-1");
        assertThat(a.getFirmadaEn()).isNotNull();
    }

    @Test
    void noFirmaSinMotivoNiSinDiagnosticoPrincipal() {
        Atencion sinMotivo = borrador();
        sinMotivo.actualizar(contenido(" ", List.of(dx("J029", true))));
        assertThatThrownBy(() -> sinMotivo.firmar("RM-1")).isInstanceOf(ApiProblem.class).hasMessageContaining("motivo");

        Atencion sinPrincipal = borrador();
        sinPrincipal.actualizar(contenido("Control", List.of(dx("I10X", false))));
        assertThatThrownBy(() -> sinPrincipal.firmar("RM-1")).hasMessageContaining("principal");
    }

    @Test
    void unaAtencionFirmadaNoSeModifica() {
        Atencion a = borrador();
        a.actualizar(contenido("Control", List.of(dx("I10X", true))));
        a.firmar("RM-1");

        assertThatThrownBy(() -> a.actualizar(contenido("Otro", List.of(dx("I10X", true)))))
                .isInstanceOf(ApiProblem.class).hasMessageContaining("nota aclaratoria");
        assertThatThrownBy(() -> a.firmar("RM-1")).isInstanceOf(ApiProblem.class);
    }

    @Test
    void soloUnDiagnosticoPrincipal() {
        assertThatThrownBy(() -> borrador().actualizar(contenido("x", List.of(dx("J029", true), dx("J039", true)))))
                .hasMessageContaining("un diagnóstico principal");
    }

    @Test
    void normalizaYValidaElCodigoCie10() {
        assertThat(new Diagnostico("j02.9", "Faringitis", true, null).getCodigoCie10()).isEqualTo("J029");
        assertThat(new Diagnostico("I10X", "HTA", true, null).getTipo()).isEqualTo(Diagnostico.Tipo.IMPRESION_DIAGNOSTICA);
        assertThatThrownBy(() -> new Diagnostico("XYZ", "x", true, null)).hasMessageContaining("CIE-10");
    }

    @Test
    void detectaElTipoDeArchivoPorSuContenido() {
        assertThat(HistoriaService.tipoContenido("%PDF-1.7 ...".getBytes())).isEqualTo("application/pdf");
        assertThatThrownBy(() -> HistoriaService.tipoContenido("<html>".getBytes())).hasMessageContaining("PDF");
        assertThat(HistoriaService.nombreSeguro("../../etc/passwd")).doesNotContain("/");
    }
}
