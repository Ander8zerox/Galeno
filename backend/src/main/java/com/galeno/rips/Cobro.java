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

/**
 * Pagador y pago moderador de una cita o de una atención (campos C03, C18, C19 y C20 del Documento Técnico 1). Es del
 * módulo de RIPS: la agenda y la historia no saben que existe.
 */
@Entity
@Table(name = "rips_cobros")
class Cobro {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cita_id", updatable = false)
    private UUID citaId;

    @Column(name = "atencion_id", updatable = false)
    private UUID atencionId;

    @Column(name = "pagador_id", nullable = false)
    private UUID pagadorId;

    @Column(name = "numero_autorizacion", length = 30)
    private String numeroAutorizacion;

    @Column(name = "concepto_recaudo", nullable = false, length = 2)
    private String conceptoRecaudo;

    @Column(name = "valor_pago_moderador", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorPagoModerador;

    @Column(name = "factura_pago_moderador", length = 20)
    private String facturaPagoModerador;

    /** Radicado de la plataforma SIRAS (ADRES) para víctimas de accidentes de tránsito. */
    @Column(name = "registro_siras", length = 60)
    private String registroSiras;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Cobro() {
    }

    static Cobro deCita(UUID citaId) {
        Cobro c = new Cobro();
        c.citaId = citaId;
        return c;
    }

    static Cobro deAtencion(UUID atencionId) {
        Cobro c = new Cobro();
        c.atencionId = atencionId;
        return c;
    }

    void actualizar(UUID pagadorId, String numeroAutorizacion, String conceptoRecaudo, BigDecimal valorPagoModerador,
                    String facturaPagoModerador, String registroSiras) {
        this.pagadorId = pagadorId;
        this.registroSiras = registroSiras;
        this.numeroAutorizacion = numeroAutorizacion;
        this.conceptoRecaudo = conceptoRecaudo;
        this.valorPagoModerador = valorPagoModerador.setScale(2, java.math.RoundingMode.HALF_UP);
        this.facturaPagoModerador = facturaPagoModerador;
        this.actualizadoEn = Instant.now();
    }

    UUID getCitaId() {
        return citaId;
    }

    UUID getPagadorId() {
        return pagadorId;
    }

    String getNumeroAutorizacion() {
        return numeroAutorizacion;
    }

    String getConceptoRecaudo() {
        return conceptoRecaudo;
    }

    BigDecimal getValorPagoModerador() {
        return valorPagoModerador;
    }

    String getFacturaPagoModerador() {
        return facturaPagoModerador;
    }

    String getRegistroSiras() {
        return registroSiras;
    }
}
