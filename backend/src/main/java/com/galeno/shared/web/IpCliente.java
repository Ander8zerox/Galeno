package com.galeno.shared.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La IP del cliente, el único lugar donde se decide. Detrás de CloudFront, {@code getRemoteAddr()} es la de un
 * nodo de CloudFront; la del navegador llega en {@code CloudFront-Viewer-Address} ("ip:puerto", si la política de
 * petición al origen lo incluye) o como último valor de {@code X-Forwarded-For} (CloudFront agrega la IP que ve al
 * final, después de lo que haya mandado el cliente).
 *
 * <p>Esos encabezados solo se creen si la petición trae el secreto de origen, que CloudFront agrega como encabezado
 * personalizado: cualquiera puede mandar {@code X-Forwarded-For}, y el prefix list de CloudFront en el security
 * group también deja pasar distribuciones de otras cuentas. Sin secreto configurado (desarrollo) nunca se creen.
 */
@Component
public class IpCliente {

    public static final String ENCABEZADO_ORIGEN = "X-Origen-Galeno";

    private static final Pattern IP = Pattern.compile("[0-9A-Fa-f.:]{2,45}");

    private final List<byte[]> secretos;

    /**
     * @param secretos el secreto que CloudFront manda en {@link #ENCABEZADO_ORIGEN}; separados por coma para rotarlo
     *                 sin cortar el servicio (primero se agrega el nuevo aquí, luego se cambia en CloudFront y al final
     *                 se quita el anterior)
     */
    public IpCliente(@Value("${app.web.origen-secreto:}") String secretos) {
        this.secretos = Arrays.stream(secretos.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(s -> s.getBytes(StandardCharsets.UTF_8))
                .toList();
    }

    /** Si hay que exigir el secreto de origen (solo cuando hay CloudFront delante). */
    public boolean exigeOrigen() {
        return !secretos.isEmpty();
    }

    /** Si la petición trae un secreto de origen válido, es decir, pasó por nuestra distribución de CloudFront. */
    public boolean vieneDeCloudFront(HttpServletRequest request) {
        String valor = request.getHeader(ENCABEZADO_ORIGEN);
        if (valor == null) {
            return false;
        }
        byte[] recibido = valor.getBytes(StandardCharsets.UTF_8);
        boolean valido = false;
        for (byte[] secreto : secretos) {
            valido |= MessageDigest.isEqual(secreto, recibido); // tiempo constante
        }
        return valido;
    }

    public String de(HttpServletRequest request) {
        if (vieneDeCloudFront(request)) {
            String ip = sinPuerto(request.getHeader("CloudFront-Viewer-Address"));
            if (ip == null) {
                ip = ultimo(request.getHeader("X-Forwarded-For"));
            }
            if (ip != null) {
                return ip;
            }
        }
        return request.getRemoteAddr();
    }

    /** "198.51.100.7:46532" o "2001:db8::1:46532": el puerto va después del último ":". */
    private static String sinPuerto(String direccion) {
        if (direccion == null) {
            return null;
        }
        int dosPuntos = direccion.lastIndexOf(':');
        return dosPuntos > 0 ? valida(direccion.substring(0, dosPuntos)) : null;
    }

    private static String ultimo(String forwardedFor) {
        if (forwardedFor == null) {
            return null;
        }
        return valida(forwardedFor.substring(forwardedFor.lastIndexOf(',') + 1));
    }

    private static String valida(String ip) {
        String limpia = ip.strip();
        return IP.matcher(limpia).matches() ? limpia : null;
    }
}
