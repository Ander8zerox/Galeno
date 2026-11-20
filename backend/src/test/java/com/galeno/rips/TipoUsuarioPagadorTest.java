package com.galeno.rips;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

class TipoUsuarioPagadorTest {

    private static Pagador pagador(Pagador.Tipo tipo, String cobertura) {
        return new Pagador(new Pagador.Datos("Pagador", tipo, null, null, cobertura, null, null, null, null, false, true));
    }

    @Test
    void conPagadorParticularSoloSeGuardaEl12() {
        Pagador particular = pagador(Pagador.Tipo.PARTICULAR, "15");

        // El caso del hallazgo: 04 subsidiado con pagador particular
        assertThatThrownBy(() -> DatosRipsService.exigirTipoUsuarioDelPagador("04", particular))
                .isInstanceOf(ApiProblem.class).hasMessageContaining("12");
        assertThatCode(() -> DatosRipsService.exigirTipoUsuarioDelPagador("12", particular)).doesNotThrowAnyException();
    }

    @Test
    void conOtroPagadorSeRevisaLaCobertura() {
        Pagador eps = pagador(Pagador.Tipo.EPS, "01");

        assertThatCode(() -> DatosRipsService.exigirTipoUsuarioDelPagador("04", eps)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DatosRipsService.exigirTipoUsuarioDelPagador("12", eps))
                .isInstanceOf(ApiProblem.class).hasMessageContaining("cobertura 01");
        // Sin tipo de usuario todavía, no hay nada que revisar
        assertThatCode(() -> DatosRipsService.exigirTipoUsuarioDelPagador(null, eps)).doesNotThrowAnyException();
    }
}
