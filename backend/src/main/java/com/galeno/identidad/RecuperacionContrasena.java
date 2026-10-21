package com.galeno.identidad;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** Enlace de recuperación de contraseña enviado por correo (ver V12). */
@Entity
@Table(name = "recuperaciones_contrasena")
class RecuperacionContrasena {

    static final Duration VIGENCIA = Duration.ofHours(1);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(updatable = false, length = 45)
    private String ip;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    protected RecuperacionContrasena() {
    }

    RecuperacionContrasena(UUID usuarioId, String tokenHash, String ip) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.ip = ip;
        this.creadoEn = Instant.now();
        this.expiraEn = creadoEn.plus(VIGENCIA);
    }

    boolean vigente() {
        return usadoEn == null && Instant.now().isBefore(expiraEn);
    }

    /** Usar el enlace, o anularlo porque se pidió otro o porque ya se cambió la contraseña. */
    void usar() {
        if (usadoEn == null) {
            usadoEn = Instant.now();
        }
    }

    UUID getUsuarioId() {
        return usuarioId;
    }
}
