package com.galeno.partners;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** Inscripción que espera la verificación del correo (24 horas; solo se guarda el hash del token). */
@Entity
@Table(name = "inscripciones_partner")
class InscripcionPartner {

    static final Duration VIGENCIA = Duration.ofHours(24);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, updatable = false, length = 100)
    private String passwordHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String datos;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "confirmado_en")
    private Instant confirmadoEn;

    protected InscripcionPartner() {
    }

    InscripcionPartner(String tokenHash, String email, String passwordHash, String datos) {
        this.tokenHash = tokenHash;
        this.email = email;
        this.passwordHash = passwordHash;
        this.datos = datos;
        this.creadoEn = Instant.now();
        this.expiraEn = creadoEn.plus(VIGENCIA);
    }

    boolean vigente() {
        return confirmadoEn == null && Instant.now().isBefore(expiraEn);
    }

    void confirmar() {
        this.confirmadoEn = Instant.now();
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getDatos() {
        return datos;
    }
}
