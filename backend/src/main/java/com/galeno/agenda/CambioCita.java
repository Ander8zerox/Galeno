package com.galeno.agenda;

import java.time.Instant;
import java.util.UUID;

/**
 * Algo que el paciente debe saber de su cita. Se publica dentro de la transacción y el correo sale después de
 * confirmarla (ver {@link CorreosCitas}).
 *
 * @param inicioAnterior en una reprogramación, la hora que tenía; null en los demás
 */
record CambioCita(UUID citaId, Tipo tipo, Instant inicioAnterior) {

    enum Tipo {
        AGENDADA,
        REPROGRAMADA,
        CANCELADA,
        RECORDATORIO
    }
}
