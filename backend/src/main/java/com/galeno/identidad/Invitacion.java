package com.galeno.identidad;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** Invitación a unirse al consultorio. Solo se guarda el hash del token, nunca el token. */
@Entity
@Table(name = "invitaciones")
public class Invitacion {

    public static final Duration VIGENCIA = Duration.ofDays(7);

    public enum Estado {
        PENDIENTE,
        ACEPTADA,
        REVOCADA
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(nullable = false, length = 100)
    private String roles;

    @Column(name = "registro_medico", length = 50)
    private String registroMedico;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "invitado_por", nullable = false)
    private UUID invitadoPor;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "aceptada_en")
    private Instant aceptadaEn;

    protected Invitacion() {
    }

    public Invitacion(String email, Set<Rol> roles, String registroMedico, String tokenHash, UUID invitadoPor) {
        this.email = email.toLowerCase();
        this.roles = roles.stream().map(Rol::name).sorted().collect(Collectors.joining(","));
        this.registroMedico = registroMedico;
        this.tokenHash = tokenHash;
        this.estado = Estado.PENDIENTE;
        this.invitadoPor = invitadoPor;
        this.creadoEn = Instant.now();
        this.expiraEn = creadoEn.plus(VIGENCIA);
    }

    public boolean vigente() {
        return estado == Estado.PENDIENTE && Instant.now().isBefore(expiraEn);
    }

    void aceptar() {
        this.estado = Estado.ACEPTADA;
        this.aceptadaEn = Instant.now();
    }

    void revocar() {
        this.estado = Estado.REVOCADA;
    }

    public Set<Rol> rolesSet() {
        EnumSet<Rol> set = EnumSet.noneOf(Rol.class);
        Arrays.stream(roles.split(",")).map(Rol::valueOf).forEach(set::add);
        return set;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getRegistroMedico() {
        return registroMedico;
    }

    public Estado getEstado() {
        return estado;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
