package com.galeno.identidad;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** Un secretario atiende a uno o varios médicos del consultorio (lo usará la agenda). */
@Entity
@Table(name = "asignaciones_secretario")
public class AsignacionSecretario {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "secretario_membresia_id", nullable = false)
    private UUID secretarioMembresiaId;

    @Column(name = "medico_membresia_id", nullable = false)
    private UUID medicoMembresiaId;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected AsignacionSecretario() {
    }

    public AsignacionSecretario(UUID secretarioMembresiaId, UUID medicoMembresiaId) {
        this.secretarioMembresiaId = secretarioMembresiaId;
        this.medicoMembresiaId = medicoMembresiaId;
        this.creadoEn = Instant.now();
    }

    public UUID getSecretarioMembresiaId() {
        return secretarioMembresiaId;
    }

    public UUID getMedicoMembresiaId() {
        return medicoMembresiaId;
    }
}
