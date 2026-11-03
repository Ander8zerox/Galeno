package com.galeno.identidad;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/**
 * "Recordar este equipo": durante 30 días ese navegador entra solo con la contraseña (ver V18). La contraseña sigue
 * siendo obligatoria; el token solo reemplaza el código.
 */
@Entity
@Table(name = "mfa_equipos")
class EquipoRecordado {

    static final Duration VIGENCIA = Duration.ofDays(30);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    protected EquipoRecordado() {
    }

    EquipoRecordado(UUID usuarioId, String tokenHash) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.creadoEn = Instant.now();
        this.expiraEn = creadoEn.plus(VIGENCIA);
    }

    boolean vigentePara(UUID usuario) {
        return usuarioId.equals(usuario) && Instant.now().isBefore(expiraEn);
    }
}
