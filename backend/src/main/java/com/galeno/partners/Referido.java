package com.galeno.partners;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un consultorio atribuido a un partner. A lo sumo uno; solo la plataforma lo cambia. */
@Entity
@Table(name = "referidos")
class Referido {

    enum Origen {
        /** Con el código o el enlace del partner al registrarse. */
        REGISTRO,
        /** Lo asignó un operador (p. ej. un cliente que llegó sin el enlace). */
        PLATAFORMA
    }

    @Id
    @Column(name = "consultorio_id")
    private UUID consultorioId;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origen origen;

    @Column(name = "atribuido_en", nullable = false)
    private Instant atribuidoEn;

    protected Referido() {
    }

    Referido(UUID consultorioId, UUID partnerId, Origen origen) {
        this.consultorioId = consultorioId;
        reasignar(partnerId, origen);
    }

    void reasignar(UUID partnerId, Origen origen) {
        this.partnerId = partnerId;
        this.origen = origen;
        this.atribuidoEn = Instant.now();
    }

    UUID getConsultorioId() {
        return consultorioId;
    }

    UUID getPartnerId() {
        return partnerId;
    }

    Origen getOrigen() {
        return origen;
    }

    Instant getAtribuidoEn() {
        return atribuidoEn;
    }
}
