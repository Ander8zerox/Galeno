package com.galeno.pacientes;

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

/** Una alergia conocida del paciente: a qué, qué tipo de agente, qué reacción y qué tan grave. */
@Entity
@Table(name = "paciente_alergias")
public class AlergiaPaciente {

    public enum Tipo { MEDICAMENTO, ALIMENTO, AMBIENTAL, OTRO }

    public enum Severidad { LEVE, MODERADA, SEVERA }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(nullable = false, length = 200)
    private String agente;

    @Column(length = 300)
    private String reaccion;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Severidad severidad;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private UUID creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected AlergiaPaciente() {
    }

    AlergiaPaciente(UUID pacienteId, UUID creadoPor) {
        this.pacienteId = pacienteId;
        this.creadoPor = creadoPor;
        this.creadoEn = Instant.now();
    }

    void actualizar(Tipo tipo, String agente, String reaccion, Severidad severidad) {
        this.tipo = tipo;
        this.agente = agente;
        this.reaccion = reaccion;
        this.severidad = severidad;
        this.actualizadoEn = Instant.now();
    }

    /** "Penicilina (medicamento): urticaria, severa" */
    String texto() {
        StringBuilder b = new StringBuilder(agente).append(" (").append(tipo.name().toLowerCase()).append(")");
        if (reaccion != null || severidad != null) {
            b.append(": ");
            if (reaccion != null) {
                b.append(reaccion);
            }
            if (severidad != null) {
                b.append(reaccion != null ? ", " : "").append(severidad.name().toLowerCase());
            }
        }
        return b.toString();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getAgente() {
        return agente;
    }

    public String getReaccion() {
        return reaccion;
    }

    public Severidad getSeveridad() {
        return severidad;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
