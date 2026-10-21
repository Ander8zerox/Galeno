package com.galeno.suscripciones;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.shared.tenancy.TenantContext;
import com.galeno.suscripciones.Suscripcion.Estado;

/**
 * Correos de vencimiento a los administradores de cada consultorio: 7, 3 y 1 día antes, al vencer (empieza la
 * gracia) y al quedar en solo lectura. Cada aviso sale una sola vez por fecha de vencimiento: si el consultorio
 * paga, el vencimiento cambia y los avisos vuelven a empezar para la fecha nueva.
 */
@Component
class AvisosSuscripcion {

    enum Tipo {
        VENCE_7,
        VENCE_3,
        VENCE_1,
        VENCIDA,
        SUSPENDIDA
    }

    private static final Logger log = LoggerFactory.getLogger(AvisosSuscripcion.class);
    /** Un consultorio suspendido hace mucho no recibe el aviso de suspensión cuando se activa esta función. */
    private static final Duration MAX_ATRASO = Duration.ofDays(30);

    private final SuscripcionService suscripciones;
    private final AvisoSuscripcionRepository avisos;
    private final TransactionTemplate tx;

    AvisosSuscripcion(SuscripcionService suscripciones, AvisoSuscripcionRepository avisos, TransactionTemplate tx) {
        this.suscripciones = suscripciones;
        this.avisos = avisos;
        this.tx = tx;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Bogota")
    void diario() {
        int enviados = enviar(Instant.now());
        if (enviados > 0) {
            log.info("Avisos de suscripción enviados: {}", enviados);
        }
    }

    /** @return cuántos avisos envió */
    int enviar(Instant ahora) {
        int enviados = 0;
        for (Suscripcion s : suscripciones.todas()) {
            Optional<Tipo> tipo = tipo(s, ahora);
            if (tipo.isEmpty()) {
                continue;
            }
            AvisoSuscripcion.Clave clave = new AvisoSuscripcion.Clave(s.getTenantId(), tipo.get().name(), s.vence());
            boolean nuevo = TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> {
                if (avisos.existsById(clave)) {
                    return false;
                }
                avisos.save(new AvisoSuscripcion(clave));
                return true;
            }));
            if (nuevo) {
                enviarCorreo(s, tipo.get());
                enviados++;
            }
        }
        return enviados;
    }

    static Optional<Tipo> tipo(Suscripcion s, Instant ahora) {
        if (s.isSuspendida()) {
            return Optional.empty(); // suspensión manual: la explica la plataforma
        }
        Instant vence = s.vence();
        if (ahora.isBefore(vence)) {
            long horas = Duration.between(ahora, vence).toHours();
            if (horas < 24) {
                return Optional.of(Tipo.VENCE_1);
            }
            if (horas < 3 * 24) {
                return Optional.of(Tipo.VENCE_3);
            }
            if (horas < 7 * 24) {
                return Optional.of(Tipo.VENCE_7);
            }
            return Optional.empty();
        }
        if (s.estado(ahora) == Estado.VENCIDA) {
            return Optional.of(Tipo.VENCIDA);
        }
        return ahora.isBefore(vence.plus(Suscripcion.GRACIA).plus(MAX_ATRASO)) ? Optional.of(Tipo.SUSPENDIDA) : Optional.empty();
    }

    private void enviarCorreo(Suscripcion s, Tipo tipo) {
        String consultorio = suscripciones.nombreConsultorio(s.getTenantId());
        String que = s.getPagadaHasta() == null ? "La prueba gratis de Galeno" : "La suscripción a Galeno";
        String vence = suscripciones.fecha(s.vence(), s.getTenantId());
        String finGracia = suscripciones.fecha(s.vence().plus(Suscripcion.GRACIA), s.getTenantId());
        String pagar = "Para renovar, entre a Galeno con una cuenta de administrador, abra Suscripción y reporte su pago.";
        String asunto;
        String texto;
        switch (tipo) {
            case VENCE_7, VENCE_3, VENCE_1 -> {
                asunto = que + " de " + consultorio + " vence el " + vence;
                texto = "%s de «%s» vence el %s.\n\n%s".formatted(que, consultorio, vence, pagar);
            }
            case VENCIDA -> {
                asunto = que + " de " + consultorio + " venció";
                texto = ("%s de «%s» venció el %s. Todo sigue funcionando hasta el %s; después el consultorio quedará "
                        + "en solo lectura (podrá consultar y descargar las historias, pero no registrar nada nuevo).\n\n%s")
                        .formatted(que, consultorio, vence, finGracia, pagar);
            }
            default -> {
                asunto = consultorio + " quedó en solo lectura en Galeno";
                texto = ("«%s» quedó en solo lectura porque la suscripción venció el %s. Sus datos y sus historias "
                        + "clínicas siguen ahí: puede consultarlas y descargarlas. En cuanto aprobemos su pago, todo "
                        + "vuelve a funcionar.\n\n%s").formatted(consultorio, vence, pagar);
            }
        }
        suscripciones.avisar(s.getTenantId(), asunto, texto + "\n\nGaleno");
    }
}
