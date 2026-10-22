package com.galeno.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OrigenCloudFrontFilterTest {

    private static MockHttpServletRequest peticion(String ruta) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ruta);
        request.setServletPath(ruta);
        return request;
    }

    /** Devuelve la cadena: si pasó, quedó con la petición. */
    private static MockFilterChain filtrar(String secretos, MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        MockFilterChain chain = new MockFilterChain();
        new OrigenCloudFrontFilter(new IpCliente(secretos)).doFilter(request, response, chain);
        return chain;
    }

    @Test
    void sinSecretoConfiguradoDejaPasarTodo() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = filtrar("", peticion("/api/v1/auth/login"), response);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rechazaLoQueNoTraeElSecretoDeOrigen() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = filtrar("s3cr3to", peticion("/api/v1/auth/login"), response);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void dejaPasarLoQueVieneDeNuestraDistribucion() throws Exception {
        MockHttpServletRequest request = peticion("/api/v1/auth/login");
        request.addHeader(IpCliente.ENCABEZADO_ORIGEN, "s3cr3to");
        MockFilterChain chain = filtrar("s3cr3to", request, new MockHttpServletResponse());

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void elChequeoDeSaludQuedaAbiertoParaLaInstancia() throws Exception {
        MockFilterChain chain = filtrar("s3cr3to", peticion("/actuator/health/liveness"), new MockHttpServletResponse());

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void unaRutaQueSoloEmpiezaParecidoNoSeSaltaElChequeo() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtrar("s3cr3to", peticion("/actuator/healthz"), response);

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
