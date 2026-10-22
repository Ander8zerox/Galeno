package com.galeno.shared.correo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param proveedor        {@code local} (buzón de desarrollo) o {@code ses}
 * @param remitente        p. ej. {@code Galeno <no-responder@galeno.com.co>}; el dominio debe estar verificado en SES
 * @param configurationSet configuration set de SES que publica rebotes y quejas en SNS (lo crea Terraform)
 * @param colaEventos      URL de la cola SQS suscrita a ese tema SNS; vacía, no se leen eventos
 * @param region           región de SES y SQS (la del proyecto de AWS), p. ej. {@code us-east-2}
 */
@ConfigurationProperties("app.correo")
public record CorreoProperties(String proveedor, String remitente, String configurationSet, String colaEventos,
                               String region) {
}
