package com.galeno.suscripciones;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/**
 * Suscripción de un consultorio. El estado no se guarda: se calcula con las fechas, así nunca queda
 * desactualizado por un proceso que no corrió.
 *
 * <pre>
 * PRUEBA (30 días) ─┐
 *                   ├─ vence ─> VENCIDA (5 días de gracia) ─> SUSPENDIDA (solo lectura)
 * ACTIVA (pagada) ──┘                       └──────── pago aprobado ────────┘
 * </pre>
 */
@Entity
@Table(name = "suscripciones")
public class Suscripcion {

    public enum Estado {
        PRUEBA,
        ACTIVA,
        /** Venció pero está en los días de gracia: todo funciona, con aviso. */
        VENCIDA,
        /** Solo lectura: se puede consultar y descargar, no registrar nada nuevo. */
        SUSPENDIDA
    }

    static final Duration PRUEBA = Duration.ofDays(30);
    static final Duration GRACIA = Duration.ofDays(5);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "prueba_hasta", nullable = false)
    private Instant pruebaHasta;

    @Column(name = "pagada_hasta")
    private Instant pagadaHasta;

    @Column(nullable = false)
    private boolean suspendida;

    @Column(name = "motivo_suspension", length = 300)
    private String motivoSuspension;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    /** Descuento sobre el precio de los planes (p. ej. por venir referido por un partner). */
    @Column(name = "descuento_porcentaje", nullable = false)
    private int descuentoPorcentaje;

    @Version
    private long version;

    protected Suscripcion() {
    }

    static Suscripcion enPrueba(Instant desde) {
        Suscripcion s = new Suscripcion();
        s.pruebaHasta = desde.plus(PRUEBA);
        s.creadaEn = desde;
        s.actualizadaEn = desde;
        return s;
    }

    /** Hasta cuándo puede usar el consultorio sin restricciones (sin contar la gracia). */
    Instant vence() {
        return pagadaHasta != null && pagadaHasta.isAfter(pruebaHasta) ? pagadaHasta : pruebaHasta;
    }

    Estado estado(Instant ahora) {
        if (suspendida) {
            return Estado.SUSPENDIDA;
        }
        Instant vence = vence();
        if (ahora.isBefore(vence)) {
            return pagadaHasta != null ? Estado.ACTIVA : Estado.PRUEBA;
        }
        return ahora.isBefore(vence.plus(GRACIA)) ? Estado.VENCIDA : Estado.SUSPENDIDA;
    }

    /**
     * Un pago aprobado agrega sus meses a partir del vencimiento actual, o desde hoy si ya venció (no se cobra
     * el tiempo en que estuvo vencida). Devuelve el periodo cubierto: [desde, hasta).
     */
    Instant[] extenderPorPago(int meses, Instant ahora, ZoneId zona) {
        Instant vence = vence();
        Instant desde = vence.isAfter(ahora) ? vence : ahora;
        Instant hasta = desde.atZone(zona).plusMonths(meses).toInstant();
        this.pagadaHasta = hasta;
        this.actualizadaEn = ahora;
        return new Instant[] {desde, hasta};
    }

    /** Cortesía de la plataforma: días gratis a partir del vencimiento (o de hoy, si ya venció). */
    void regalarDias(int dias, Instant ahora) {
        Instant vence = vence();
        Instant desde = vence.isAfter(ahora) ? vence : ahora;
        Instant hasta = desde.plus(Duration.ofDays(dias));
        if (pagadaHasta != null && !pagadaHasta.isBefore(pruebaHasta)) {
            this.pagadaHasta = hasta;
        } else {
            this.pruebaHasta = hasta;
        }
        this.actualizadaEn = ahora;
    }

    void aplicarDescuento(int porcentaje, Instant ahora) {
        this.descuentoPorcentaje = porcentaje;
        this.actualizadaEn = ahora;
    }

    int getDescuentoPorcentaje() {
        return descuentoPorcentaje;
    }

    void suspender(String motivo, Instant ahora) {
        this.suspendida = true;
        this.motivoSuspension = motivo;
        this.actualizadaEn = ahora;
    }

    void reactivar(Instant ahora) {
        this.suspendida = false;
        this.motivoSuspension = null;
        this.actualizadaEn = ahora;
    }

    UUID getTenantId() {
        return tenantId;
    }

    Instant getPruebaHasta() {
        return pruebaHasta;
    }

    Instant getPagadaHasta() {
        return pagadaHasta;
    }

    boolean isSuspendida() {
        return suspendida;
    }

    String getMotivoSuspension() {
        return motivoSuspension;
    }
}
