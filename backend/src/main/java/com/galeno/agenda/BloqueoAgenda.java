package com.galeno.agenda;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** Un rango sin atención (vacaciones, capacitación, cierre): de un médico o de todo el consultorio. */
@Entity
@Table(name = "bloqueos_agenda")
public class BloqueoAgenda {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** null: todo el consultorio. */
    @Column(name = "medico_membresia_id", updatable = false)
    private UUID medicoMembresiaId;

    @Column(nullable = false, updatable = false)
    private Instant inicio;

    @Column(nullable = false, updatable = false)
    private Instant fin;

    @Column(nullable = false, length = 200, updatable = false)
    private String motivo;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private UUID creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected BloqueoAgenda() {
    }

    BloqueoAgenda(UUID medicoMembresiaId, Instant inicio, Instant fin, String motivo, UUID creadoPor) {
        this.medicoMembresiaId = medicoMembresiaId;
        this.inicio = inicio;
        this.fin = fin;
        this.motivo = motivo;
        this.creadoPor = creadoPor;
        this.creadoEn = Instant.now();
    }

    /** ¿Aplica a la agenda de este médico? (los del consultorio aplican a todos) */
    boolean aplicaA(UUID medicoId) {
        return medicoMembresiaId == null || medicoMembresiaId.equals(medicoId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMedicoMembresiaId() {
        return medicoMembresiaId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public String getMotivo() {
        return motivo;
    }
}
