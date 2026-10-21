package com.galeno.partners;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** Pago a un partner de sus comisiones disponibles (el operador transfiere y lo registra aquí). */
@Entity
@Table(name = "liquidaciones_partner")
class LiquidacionPartner {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "partner_id", nullable = false, updatable = false)
    private UUID partnerId;

    @Column(nullable = false, updatable = false)
    private long total;

    @Column(nullable = false, updatable = false)
    private int comisiones;

    @Column(nullable = false, updatable = false, length = 100)
    private String referencia;

    @Column(name = "fecha_pago", nullable = false, updatable = false)
    private LocalDate fechaPago;

    @Column(name = "creada_por", nullable = false, updatable = false)
    private UUID creadaPor;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    protected LiquidacionPartner() {
    }

    LiquidacionPartner(UUID partnerId, long total, int comisiones, String referencia, LocalDate fechaPago, UUID creadaPor) {
        this.partnerId = partnerId;
        this.total = total;
        this.comisiones = comisiones;
        this.referencia = referencia;
        this.fechaPago = fechaPago;
        this.creadaPor = creadaPor;
        this.creadaEn = Instant.now();
    }

    UUID getId() {
        return id;
    }

    UUID getPartnerId() {
        return partnerId;
    }

    long getTotal() {
        return total;
    }

    int getComisiones() {
        return comisiones;
    }

    String getReferencia() {
        return referencia;
    }

    LocalDate getFechaPago() {
        return fechaPago;
    }

    Instant getCreadaEn() {
        return creadaEn;
    }
}
