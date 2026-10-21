package com.galeno.suscripciones;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Aviso de vencimiento ya enviado: evita mandar el mismo correo dos veces (ver {@link AvisosSuscripcion}). */
@Entity
@Table(name = "avisos_suscripcion")
class AvisoSuscripcion {

    @Embeddable
    record Clave(@Column(name = "tenant_id") UUID tenantId, @Column(length = 30) String tipo,
                 @Column(name = "vencimiento") Instant vencimiento) implements Serializable {
    }

    @EmbeddedId
    private Clave clave;

    @Column(name = "enviado_en", nullable = false)
    private Instant enviadoEn;

    protected AvisoSuscripcion() {
    }

    AvisoSuscripcion(Clave clave) {
        this.clave = clave;
        this.enviadoEn = Instant.now();
    }
}
