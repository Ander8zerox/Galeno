package com.galeno.shared.correo;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Lee los rebotes y quejas que SES publica en SNS y SNS deja en una cola SQS. Así la API no expone ningún endpoint
 * público para recibirlos (no hay firmas de SNS que validar) y si la API está caída los eventos esperan en la cola.
 * Un mensaje se borra solo después de procesarlo; si falla, SQS lo devuelve y, tras varios intentos, lo pasa a la
 * cola de mensajes fallidos (DLQ, la crea Terraform).
 */
@Component
@ConditionalOnExpression("'${app.correo.proveedor:local}' == 'ses' && '${app.correo.cola-eventos:}' != ''")
class ColaEventosSes implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ColaEventosSes.class);

    private final SqsClient sqs;
    private final String cola;
    private final EventosSes eventos;
    private Instant pausaHasta = Instant.MIN;

    ColaEventosSes(CorreoProperties props, EventosSes eventos) {
        this.sqs = SqsClient.builder().region(Region.of(props.region())).build();
        this.cola = props.colaEventos();
        this.eventos = eventos;
    }

    /** Long polling de 20 s: casi sin costo cuando no hay eventos (y SQS cobra por petición, no por espera). */
    @Scheduled(fixedDelay = 1_000)
    void leer() {
        if (Instant.now().isBefore(pausaHasta)) {
            return;
        }
        try {
            var mensajes = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(cola).maxNumberOfMessages(10).waitTimeSeconds(20).build()).messages();
            for (Message m : mensajes) {
                eventos.procesar(m.body());
                sqs.deleteMessage(b -> b.queueUrl(cola).receiptHandle(m.receiptHandle()));
            }
        } catch (SdkException e) {
            // Sin red, sin permisos o cola mal configurada: se reintenta en un minuto en vez de llenar el log
            log.error("No se pudo leer la cola de eventos de SES (se reintenta en 1 min): {}", SesEnvioCorreo.detalle(e));
            pausaHasta = Instant.now().plusSeconds(60);
        }
    }

    @Override
    public void close() {
        sqs.close();
    }
}
