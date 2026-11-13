package com.galeno.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class LimiteUsuarioFilterTest {

    private final LimiteUsuarioFilter filtro = new LimiteUsuarioFilter(4, 2);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void como(String usuario) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(usuario, null, List.of()));
    }

    private int filtrar(String metodo) throws Exception {
        return filtrar(metodo, "/api/v1/pacientes");
    }

    private int filtrar(String metodo, String ruta) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setServletPath(ruta);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filtro.doFilter(request, response, chain);
        return chain.getRequest() != null ? 200 : response.getStatus();
    }

    @Test
    void losPasosDeEntradaNoGastanElCupoDeEscrituras() throws Exception {
        como("usuario-4");
        assertThat(filtrar("POST", "/api/v1/auth/mfa")).isEqualTo(200);
        assertThat(filtrar("POST", "/api/v1/auth/sesion")).isEqualTo(200);
        assertThat(filtrar("POST")).isEqualTo(200);
        assertThat(filtrar("POST")).isEqualTo(200); // 2 escrituras de datos: aún dentro del tope
    }

    @Test
    void lasEscriturasTienenUnTopeMasBajo() throws Exception {
        como("usuario-1");
        assertThat(filtrar("POST")).isEqualTo(200);
        assertThat(filtrar("PUT")).isEqualTo(200);
        assertThat(filtrar("POST")).isEqualTo(429);
        assertThat(filtrar("GET")).isEqualTo(200); // la lectura sigue (4 en total)
    }

    @Test
    void elTopeGeneralEsPorPersona() throws Exception {
        como("usuario-2");
        for (int i = 0; i < 4; i++) {
            assertThat(filtrar("GET")).isEqualTo(200);
        }
        assertThat(filtrar("GET")).isEqualTo(429);
        como("usuario-3");
        assertThat(filtrar("GET")).isEqualTo(200);
    }

    @Test
    void sinSesionNoAplica() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(filtrar("POST")).isEqualTo(200);
        }
    }
}
