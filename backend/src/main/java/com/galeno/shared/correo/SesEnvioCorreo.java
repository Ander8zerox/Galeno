package com.galeno.shared.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Envío real con Amazon SES (API v2). Las credenciales salen de la cadena estándar de AWS (en EC2, el rol de la
 * instancia). Cada correo va con el configuration set, que es el que publica los rebotes y quejas.
 */
@Component
@ConditionalOnProperty(name = "app.correo.proveedor", havingValue = "ses")
class SesEnvioCorreo implements EnvioCorreo, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SesEnvioCorreo.class);

    private final SesV2Client ses;
    private final CorreoProperties props;
    private final SupresionCorreos supresion;

    SesEnvioCorreo(CorreoProperties props, SupresionCorreos supresion) {
        if (props.remitente() == null || props.remitente().isBlank()) {
            throw new IllegalStateException("Falta app.correo.remitente (APP_CORREO_REMITENTE) para enviar con SES");
        }
        this.props = props;
        this.supresion = supresion;
        this.ses = SesV2Client.builder().region(Region.of(props.region())).build();
    }

    @Override
    public void enviar(Correo correo) {
        if (supresion.suprimido(correo.para())) {
            log.warn("No se envía \"{}\" a {}: la dirección rebotó o se quejó antes", correo.asunto(), correo.para());
            return;
        }
        var req = SendEmailRequest.builder()
                .fromEmailAddress(props.remitente())
                .destination(Destination.builder().toAddresses(correo.para()).build())
                .content(EmailContent.builder().simple(Message.builder()
                        .subject(utf8(correo.asunto()))
                        .body(Body.builder().text(utf8(correo.texto())).build())
                        .build()).build());
        if (props.configurationSet() != null && !props.configurationSet().isBlank()) {
            req.configurationSetName(props.configurationSet());
        }
        try {
            var res = ses.sendEmail(req.build());
            log.info("Correo enviado con SES a {}: {} ({})", correo.para(), correo.asunto(), res.messageId());
        } catch (SdkException e) { // SES rechazó el correo, o no hay red o credenciales
            // Quien llama no puede hacer nada distinto (el registro o la recuperación ya quedaron guardados): se
            // registra el fallo para revisarlo, sin romper la petición del usuario.
            log.error("SES no aceptó el correo para {} ({}): {}", correo.para(), correo.asunto(), detalle(e));
        }
    }

    static String detalle(SdkException e) {
        return e instanceof AwsServiceException a && a.awsErrorDetails() != null ? a.awsErrorDetails().errorMessage() : e.getMessage();
    }

    private static Content utf8(String texto) {
        return Content.builder().data(texto).charset("UTF-8").build();
    }

    @Override
    public void close() {
        ses.close();
    }
}
