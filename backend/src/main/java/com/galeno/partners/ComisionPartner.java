package com.galeno.partners;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Comisión de un pago aprobado. El estado se calcula: retenida hasta {@code disponibleDesde} (por si el pago se
 * reversa), luego disponible, y pagada cuando entra en una liquidación. Una comisión anulada no se paga.
 */
@Entity
@Table(name = "comisiones_partner")
class ComisionPartner {

    enum Estado {
        RETENIDA,
        DISPONIBLE,
        PAGADA,
        ANULADA
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "partner_id", nullable = false, updatable = false)
    private UUID partnerId;

    @Column(name = "consultorio_id", nullable = false, updatable = false)
    private UUID consultorioId;

    @Column(name = "pago_id", nullable = false, updatable = false)
    private UUID pagoId;

    @Column(nullable = false, updatable = false)
    private long base;

    @Column(nullable = false, updatable = false)
    private int porcentaje;

    @Column(nullable = false, updatable = false)
    private long valor;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "disponible_desde", nullable = false, updatable = false)
    private Instant disponibleDesde;

    @Column(name = "anulada_en")
    private Instant anuladaEn;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @Column(name = "liquidacion_id")
    private UUID liquidacionId;

    protected ComisionPartner() {
    }

    ComisionPartner(UUID partnerId, UUID consultorioId, UUID pagoId, long base, int porcentaje, Instant aprobadoEn,
                    Duration retencion) {
        this.partnerId = partnerId;
        this.consultorioId = consultorioId;
        this.pagoId = pagoId;
        this.base = base;
        this.porcentaje = porcentaje;
        this.valor = Math.round(base * porcentaje / 100.0);
        this.creadaEn = aprobadoEn;
        this.disponibleDesde = aprobadoEn.plus(retencion);
    }

    Estado estado(Instant ahora) {
        if (anuladaEn != null) {
            return Estado.ANULADA;
        }
        if (liquidacionId != null) {
            return Estado.PAGADA;
        }
        return ahora.isBefore(disponibleDesde) ? Estado.RETENIDA : Estado.DISPONIBLE;
    }

    void anular(String motivo, Instant ahora) {
        if (liquidacionId != null) {
            throw ApiProblem.conflict("La comisión ya se pagó en una liquidación");
        }
        if (anuladaEn == null) {
            this.anuladaEn = ahora;
            this.motivoAnulacion = motivo;
        }
    }

    void liquidar(UUID liquidacionId, Instant ahora) {
        if (estado(ahora) != Estado.DISPONIBLE) {
            throw ApiProblem.conflict("La comisión no está disponible para liquidar");
        }
        this.liquidacionId = liquidacionId;
    }

    UUID getId() {
        return id;
    }

    UUID getPartnerId() {
        return partnerId;
    }

    UUID getConsultorioId() {
        return consultorioId;
    }

    long getBase() {
        return base;
    }

    int getPorcentaje() {
        return porcentaje;
    }

    long getValor() {
        return valor;
    }

    Instant getCreadaEn() {
        return creadaEn;
    }

    Instant getDisponibleDesde() {
        return disponibleDesde;
    }

    String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    UUID getLiquidacionId() {
        return liquidacionId;
    }
}
