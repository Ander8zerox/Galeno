package com.galeno.shared.correo;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Dirección a la que ya no se envía (ver V14). Es global: no pertenece a ningún consultorio. */
@Entity
@Table(name = "correos_suprimidos")
public class CorreoSuprimido {

    public enum Motivo { REBOTE, QUEJA }

    @Id
    @Column(length = 190)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Motivo motivo;

    @Column(length = 500)
    private String detalle;

    @Column(nullable = false)
    private int eventos;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "ultimo_en", nullable = false)
    private Instant ultimoEn;

    protected CorreoSuprimido() {
    }

    CorreoSuprimido(String email, Motivo motivo, String detalle) {
        this.email = email;
        this.motivo = motivo;
        this.detalle = detalle;
        this.eventos = 1;
        this.creadoEn = Instant.now();
        this.ultimoEn = creadoEn;
    }

    /** Otro evento para la misma dirección: una queja pesa más que un rebote y no se rebaja. */
    void repetir(Motivo nuevo, String detalle) {
        if (nuevo == Motivo.QUEJA) {
            this.motivo = Motivo.QUEJA;
        }
        this.detalle = detalle;
        this.eventos++;
        this.ultimoEn = Instant.now();
    }

    public String getEmail() {
        return email;
    }

    public Motivo getMotivo() {
        return motivo;
    }

    public String getDetalle() {
        return detalle;
    }

    public int getEventos() {
        return eventos;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getUltimoEn() {
        return ultimoEn;
    }
}
