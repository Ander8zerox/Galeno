package com.galeno.rips;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** Precio de un CUPS en el consultorio: se propone al registrar el procedimiento en la atención. */
@Entity
@Table(name = "precios_cups")
class PrecioCups {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "codigo_cups", nullable = false, updatable = false, length = 10)
    private String codigoCups;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected PrecioCups() {
    }

    PrecioCups(String codigoCups, BigDecimal valor) {
        this.codigoCups = codigoCups;
        fijar(valor);
    }

    void fijar(BigDecimal valor) {
        this.valor = valor.setScale(2, java.math.RoundingMode.HALF_UP);
        this.actualizadoEn = Instant.now();
    }

    String getCodigoCups() {
        return codigoCups;
    }

    BigDecimal getValor() {
        return valor;
    }

    Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
