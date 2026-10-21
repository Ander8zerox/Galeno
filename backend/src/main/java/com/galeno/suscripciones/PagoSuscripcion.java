package com.galeno.suscripciones;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/** Pago reportado por el consultorio (transferencia o consignación) que revisa un operador de la plataforma. */
@Entity
@Table(name = "pagos_suscripcion")
class PagoSuscripcion {

    enum Estado {
        REPORTADO,
        APROBADO,
        RECHAZADO
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_codigo", nullable = false, updatable = false, length = 20)
    private String planCodigo;

    @Column(nullable = false, updatable = false)
    private int meses;

    @Column(nullable = false, updatable = false)
    private long monto;

    @Column(name = "fecha_pago", nullable = false, updatable = false)
    private LocalDate fechaPago;

    @Column(nullable = false, updatable = false, length = 100)
    private String referencia;

    @Column(name = "comprobante_nombre", updatable = false, length = 200)
    private String comprobanteNombre;

    @Column(name = "comprobante_tipo", updatable = false, length = 60)
    private String comprobanteTipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "motivo_rechazo", length = 300)
    private String motivoRechazo;

    @Column(name = "reportado_por", nullable = false, updatable = false)
    private UUID reportadoPor;

    @Column(name = "reportado_en", nullable = false, updatable = false)
    private Instant reportadoEn;

    @Column(name = "revisado_por")
    private UUID revisadoPor;

    @Column(name = "revisado_en")
    private Instant revisadoEn;

    @Column(name = "periodo_desde")
    private Instant periodoDesde;

    @Column(name = "periodo_hasta")
    private Instant periodoHasta;

    protected PagoSuscripcion() {
    }

    PagoSuscripcion(PlanSuscripcion plan, long monto, LocalDate fechaPago, String referencia,
                    String comprobanteNombre, String comprobanteTipo, UUID reportadoPor) {
        this.planCodigo = plan.getCodigo();
        this.meses = plan.getMeses();
        this.monto = monto;
        this.fechaPago = fechaPago;
        this.referencia = referencia;
        this.comprobanteNombre = comprobanteNombre;
        this.comprobanteTipo = comprobanteTipo;
        this.estado = Estado.REPORTADO;
        this.reportadoPor = reportadoPor;
        this.reportadoEn = Instant.now();
    }

    void aprobar(UUID operador, Instant desde, Instant hasta) {
        exigirReportado();
        this.estado = Estado.APROBADO;
        this.revisadoPor = operador;
        this.revisadoEn = Instant.now();
        this.periodoDesde = desde;
        this.periodoHasta = hasta;
    }

    void rechazar(UUID operador, String motivo) {
        exigirReportado();
        this.estado = Estado.RECHAZADO;
        this.motivoRechazo = motivo;
        this.revisadoPor = operador;
        this.revisadoEn = Instant.now();
    }

    private void exigirReportado() {
        if (estado != Estado.REPORTADO) {
            throw ApiProblem.conflict("Este pago ya fue revisado (" + estado + ")");
        }
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getPlanCodigo() {
        return planCodigo;
    }

    int getMeses() {
        return meses;
    }

    long getMonto() {
        return monto;
    }

    LocalDate getFechaPago() {
        return fechaPago;
    }

    String getReferencia() {
        return referencia;
    }

    String getComprobanteNombre() {
        return comprobanteNombre;
    }

    String getComprobanteTipo() {
        return comprobanteTipo;
    }

    Estado getEstado() {
        return estado;
    }

    String getMotivoRechazo() {
        return motivoRechazo;
    }

    Instant getReportadoEn() {
        return reportadoEn;
    }

    Instant getRevisadoEn() {
        return revisadoEn;
    }

    Instant getPeriodoDesde() {
        return periodoDesde;
    }

    Instant getPeriodoHasta() {
        return periodoHasta;
    }
}
