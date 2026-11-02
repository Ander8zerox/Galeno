package com.galeno.identidad;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Verificación en dos pasos de una persona (ver V18 y {@link MfaService}). */
@Entity
@Table(name = "mfa_usuarios")
class MfaUsuario {

    static final int MAX_INTENTOS = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(15);

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    /** Cifrado con {@link CifradoMfa}. */
    @Column(length = 200)
    private String secreto;

    @Column(name = "secreto_pendiente", length = 200)
    private String secretoPendiente;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "activado_en")
    private Instant activadoEn;

    @Column(name = "ultimo_paso", nullable = false)
    private long ultimoPaso;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    protected MfaUsuario() {
    }

    MfaUsuario(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    void prepararSecreto(String secretoCifrado) {
        this.secretoPendiente = secretoCifrado;
    }

    void activar(long paso) {
        this.secreto = secretoPendiente;
        this.secretoPendiente = null;
        this.activo = true;
        this.activadoEn = Instant.now();
        this.ultimoPaso = paso;
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
    }

    void desactivar() {
        this.secreto = null;
        this.secretoPendiente = null;
        this.activo = false;
        this.activadoEn = null;
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
    }

    boolean bloqueado() {
        return bloqueadoHasta != null && Instant.now().isBefore(bloqueadoHasta);
    }

    void exito(long paso) {
        if (paso > ultimoPaso) {
            this.ultimoPaso = paso;
        }
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
    }

    /** Al quinto intento fallido seguido, 15 minutos sin poder intentar. */
    void fallo() {
        this.intentosFallidos++;
        if (intentosFallidos >= MAX_INTENTOS) {
            this.bloqueadoHasta = Instant.now().plus(BLOQUEO);
            this.intentosFallidos = 0;
        }
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    String getSecreto() {
        return secreto;
    }

    String getSecretoPendiente() {
        return secretoPendiente;
    }

    boolean isActivo() {
        return activo;
    }

    Instant getActivadoEn() {
        return activadoEn;
    }

    long getUltimoPaso() {
        return ultimoPaso;
    }
}
