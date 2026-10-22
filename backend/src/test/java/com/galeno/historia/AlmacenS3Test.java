package com.galeno.historia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Contra un S3 de mentira en memoria: lo que importa es qué pide el SDK y cómo se manejan las respuestas. */
class AlmacenS3Test {

    private HttpServer servidor;
    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();
    private final Map<String, String> ifNoneMatch = new ConcurrentHashMap<>();
    private AlmacenDocumentos.EnS3 almacen;

    @BeforeEach
    void iniciar() throws IOException {
        System.setProperty("aws.accessKeyId", "prueba");
        System.setProperty("aws.secretAccessKey", "prueba");
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        almacen = new AlmacenDocumentos.EnS3("galeno-docs", "us-east-1", "http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    @AfterEach
    void detener() {
        almacen.close();
        servidor.stop(0);
    }

    /** PUT guarda (o 412 si la clave existe y vino If-None-Match: *), GET devuelve el contenido o 404. */
    private void responder(HttpExchange ex) throws IOException {
        String ruta = ex.getRequestURI().getPath(); // estilo ruta: /bucket/clave
        byte[] cuerpo = ex.getRequestBody().readAllBytes();
        switch (ex.getRequestMethod()) {
            case "PUT" -> {
                String condicion = ex.getRequestHeaders().getFirst("If-None-Match");
                if (condicion != null) {
                    ifNoneMatch.put(ruta, condicion);
                }
                if ("*".equals(condicion) && objetos.containsKey(ruta)) {
                    error(ex, 412, "PreconditionFailed");
                    return;
                }
                objetos.put(ruta, decodificar(ex, cuerpo));
                ex.getResponseHeaders().add("ETag", "\"abc\"");
                ex.sendResponseHeaders(200, -1);
            }
            case "GET" -> {
                byte[] contenido = objetos.get(ruta);
                if (contenido == null) {
                    error(ex, 404, "NoSuchKey");
                    return;
                }
                ex.sendResponseHeaders(200, contenido.length);
                ex.getResponseBody().write(contenido);
            }
            default -> ex.sendResponseHeaders(405, -1);
        }
        ex.close();
    }

    /** El SDK puede mandar el cuerpo en fragmentos aws-chunked: aquí se toma solo el contenido. */
    private static byte[] decodificar(HttpExchange ex, byte[] cuerpo) {
        String encoding = ex.getRequestHeaders().getFirst("Content-Encoding");
        String decodedLength = ex.getRequestHeaders().getFirst("x-amz-decoded-content-length");
        if ((encoding == null || !encoding.contains("aws-chunked")) && decodedLength == null) {
            return cuerpo;
        }
        String texto = new String(cuerpo, StandardCharsets.ISO_8859_1);
        int inicio = texto.indexOf("\r\n") + 2;
        int largo = Integer.parseInt(texto.substring(0, texto.indexOf("\r\n")).split(";")[0].trim(), 16);
        byte[] contenido = new byte[largo];
        System.arraycopy(cuerpo, inicio, contenido, 0, largo);
        return contenido;
    }

    private static void error(HttpExchange ex, int estado, String codigo) throws IOException {
        byte[] xml = ("<Error><Code>" + codigo + "</Code><Message>" + codigo + "</Message></Error>").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/xml");
        ex.sendResponseHeaders(estado, xml.length);
        ex.getResponseBody().write(xml);
        ex.close();
    }

    @Test
    void guardaYLeeConLaClaveDelDocumento() {
        byte[] pdf = "%PDF-1.7 prueba".getBytes(StandardCharsets.UTF_8);

        almacen.guardar("tenant/paciente/doc-1", pdf);

        assertThat(objetos).containsKey("/galeno-docs/tenant/paciente/doc-1");
        assertThat(ifNoneMatch).containsEntry("/galeno-docs/tenant/paciente/doc-1", "*");
        assertThat(almacen.leer("tenant/paciente/doc-1")).isEqualTo(pdf);
    }

    @Test
    void nuncaSobrescribeUnDocumento() {
        almacen.guardar("tenant/paciente/doc-1", new byte[] {1});

        assertThatThrownBy(() -> almacen.guardar("tenant/paciente/doc-1", new byte[] {2}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("412");
        assertThat(almacen.leer("tenant/paciente/doc-1")).containsExactly(1);
    }

    @Test
    void leerUnaClaveQueNoExisteFallaConClaridad() {
        assertThatThrownBy(() -> almacen.leer("tenant/paciente/no-existe"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("404");
    }
}
