package com.galeno.historia;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.galeno.agenda.AtencionesDeCitas;

/** Para la agenda: una cita tiene atención si alguien la abrió desde la historia (firmada o en borrador). */
@Component
class AtencionesDeCitasHistoria implements AtencionesDeCitas {

    private final AtencionRepository atenciones;

    AtencionesDeCitasHistoria(AtencionRepository atenciones) {
        this.atenciones = atenciones;
    }

    @Override
    public boolean tieneAtencion(UUID citaId) {
        return atenciones.findByCitaId(citaId).isPresent();
    }
}
