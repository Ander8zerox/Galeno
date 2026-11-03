package com.galeno.identidad;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** Código de recuperación de un solo uso (ver V18). Solo se guarda su HMAC. */
@Entity
@Table(name = "mfa_codigos_recuperacion")
class CodigoRecuperacion {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "codigo_hash", nullable = false, updatable = false, length = 64)
    private String codigoHash;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    protected CodigoRecuperacion() {
    }

    CodigoRecuperacion(UUID usuarioId, String codigoHash) {
        this.usuarioId = usuarioId;
        this.codigoHash = codigoHash;
        this.creadoEn = Instant.now();
    }

    boolean disponible() {
        return usadoEn == null;
    }

    void usar() {
        this.usadoEn = Instant.now();
    }

    String getCodigoHash() {
        return codigoHash;
    }
}
