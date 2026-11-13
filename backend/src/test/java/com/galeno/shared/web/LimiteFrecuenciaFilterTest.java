package com.galeno.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LimiteFrecuenciaFilterTest {

    private final LimiteFrecuenciaFilter filtro = new LimiteFrecuenciaFilter(new IpCliente(""), 5, 2);

    private static MockHttpServletRequest peticion(String metodo, String ruta, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setServletPath(ruta);
        request.setRemoteAddr(ip);
        return request;
    }

    /** Estado de la respuesta: 200 si pasó a la cadena. */
    private int filtrar(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filtro.doFilter(request, response, chain);
        if (chain.getRequest() == null) {
            assertThat(response.getHeader("Retry-After")).isNotBlank();
            assertThat(response.getContentAsString()).contains("Demasiadas solicitudes");
        }
        return chain.getRequest() != null ? 200 : response.getStatus();
    }

    @Test
    void lasRutasSensiblesTienenUnTopeMasBajoPorIp() throws Exception {
        assertThat(filtrar(peticion("POST", "/api/v1/auth/login", "198.51.100.1"))).isEqualTo(200);
        assertThat(filtrar(peticion("POST", "/api/v1/plataforma/login", "198.51.100.1"))).isEqualTo(200);
        assertThat(filtrar(peticion("POST", "/api/v1/partners/login", "198.51.100.1"))).isEqualTo(429);
        // Otra IP no se ve afectada, y lo no sensible de la misma IP sigue pasando
        assertThat(filtrar(peticion("POST", "/api/v1/auth/login", "198.51.100.2"))).isEqualTo(200);
        assertThat(filtrar(peticion("GET", "/api/v1/pacientes", "198.51.100.1"))).isEqualTo(200);
    }

    @Test
    void validarCodigosDePartnerCuentaComoSensible() throws Exception {
        assertThat(filtrar(peticion("GET", "/api/v1/partners/codigos/ABC", "198.51.100.3"))).isEqualTo(200);
        assertThat(filtrar(peticion("GET", "/api/v1/partners/codigos/ABD", "198.51.100.3"))).isEqualTo(200);
        assertThat(filtrar(peticion("GET", "/api/v1/partners/codigos/ABE", "198.51.100.3"))).isEqualTo(429);
    }

    @Test
    void elTopeGeneralCubreTodaLaApi() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(filtrar(peticion("GET", "/api/v1/agenda/citas", "198.51.100.4"))).isEqualTo(200);
        }
        assertThat(filtrar(peticion("GET", "/api/v1/agenda/citas", "198.51.100.4"))).isEqualTo(429);
    }

    @Test
    void elChequeoDeSaludNoCuenta() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(filtrar(peticion("GET", "/actuator/health", "198.51.100.5"))).isEqualTo(200);
        }
    }
}
