package com.galeno.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IpClienteTest {

    private static final String CLOUDFRONT = "130.176.10.20";

    private static MockHttpServletRequest peticion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CLOUDFRONT);
        return request;
    }

    @Test
    void sinSecretoConfiguradoNuncaCreeLosEncabezados() {
        IpCliente ip = new IpCliente("");
        MockHttpServletRequest request = peticion();
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("CloudFront-Viewer-Address", "1.2.3.4:5000");
        request.addHeader(IpCliente.ENCABEZADO_ORIGEN, "");

        assertThat(ip.exigeOrigen()).isFalse();
        assertThat(ip.de(request)).isEqualTo(CLOUDFRONT);
    }

    @Test
    void conSecretoValidoTomaLaIpDelVisitanteQueVioCloudFront() {
        IpCliente ip = new IpCliente("s3cr3to");
        MockHttpServletRequest request = peticion();
        request.addHeader(IpCliente.ENCABEZADO_ORIGEN, "s3cr3to");
        request.addHeader("CloudFront-Viewer-Address", "198.51.100.7:46532");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 198.51.100.7");

        assertThat(ip.de(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void entiendeIpv6EnViewerAddress() {
        IpCliente ip = new IpCliente("s3cr3to");
        MockHttpServletRequest request = peticion();
        request.addHeader(IpCliente.ENCABEZADO_ORIGEN, "s3cr3to");
        request.addHeader("CloudFront-Viewer-Address", "2001:db8::1:443");

        assertThat(ip.de(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void sinViewerAddressUsaElUltimoForwardedForQueEsElQueAgregaCloudFront() {
        IpCliente ip = new IpCliente("s3cr3to");
        MockHttpServletRequest request = peticion();
        request.addHeader(IpCliente.ENCABEZADO_ORIGEN, "s3cr3to");
        // El cliente mandó "6.6.6.6" para hacerse pasar por otro; CloudFront agregó la real al final
        request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9");

        assertThat(ip.de(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void conSecretoEquivocadoIgnoraLosEncabezados() {
        IpCliente ip = new IpCliente("s3cr3to");
        MockHttpServletRequest request = peticion();
        request.addHeader(IpCliente.ENCABEZADO_ORIGEN, "otro");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(ip.vieneDeCloudFront(request)).isFalse();
        assertThat(ip.de(request)).isEqualTo(CLOUDFRONT);
    }

    @Test
    void aceptaElSecretoNuevoYElAnteriorMientrasSeRota() {
        IpCliente ip = new IpCliente("nuevo, anterior");
        MockHttpServletRequest conAnterior = peticion();
        conAnterior.addHeader(IpCliente.ENCABEZADO_ORIGEN, "anterior");
        MockHttpServletRequest sinSecreto = peticion();

        assertThat(ip.vieneDeCloudFront(conAnterior)).isTrue();
        assertThat(ip.vieneDeCloudFront(sinSecreto)).isFalse();
    }

    @Test
    void descartaEncabezadosQueNoSonIp() {
        IpCliente ip = new IpCliente("s3cr3to");
        MockHttpServletRequest request = peticion();
        request.addHeader(IpCliente.ENCABEZADO_ORIGEN, "s3cr3to");
        request.addHeader("CloudFront-Viewer-Address", "<script>:1");
        request.addHeader("X-Forwarded-For", "no-es-una-ip");

        assertThat(ip.de(request)).isEqualTo(CLOUDFRONT);
    }
}
