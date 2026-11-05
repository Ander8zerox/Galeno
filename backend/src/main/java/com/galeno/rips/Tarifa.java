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

/** Precio de un CUPS con un pagador (convenio). Sin tarifa, vale la lista general del consultorio. */
@Entity
@Table(name = "rips_tarifas")
class Tarifa {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "pagador_id", nullable = false, updatable = false)
    private UUID pagadorId;

    @Column(name = "codigo_cups", nullable = false, updatable = false, length = 10)
    private String codigoCups;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Tarifa() {
    }

    Tarifa(UUID pagadorId, String codigoCups, BigDecimal valor) {
        this.pagadorId = pagadorId;
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
