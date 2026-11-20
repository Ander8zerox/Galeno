package com.galeno.agenda;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** Un tipo de cita del consultorio: nombre, duración propia y, opcional, el CUPS que se registra. */
@Entity
@Table(name = "tipos_cita")
public class TipoCita {

    /** PRIMERA_VEZ y CONTROL deciden el CUPS de consulta sugerido (8902xx / 8903xx) y la finalidad del RIPS. */
    public enum Clase { PRIMERA_VEZ, CONTROL, OTRA }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(name = "duracion_minutos", nullable = false)
    private int duracionMinutos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Clase clase;

    @Column(length = 10)
    private String cups;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected TipoCita() {
    }

    TipoCita(String nombre, int duracionMinutos, Clase clase, String cups) {
        actualizar(nombre, duracionMinutos, clase, cups, true);
        this.creadoEn = Instant.now();
    }

    void actualizar(String nombre, int duracionMinutos, Clase clase, String cups, boolean activo) {
        this.nombre = nombre;
        this.duracionMinutos = duracionMinutos;
        this.clase = clase;
        this.cups = cups;
        this.activo = activo;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getDuracionMinutos() {
        return duracionMinutos;
    }

    public Clase getClase() {
        return clase;
    }

    public String getCups() {
        return cups;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
