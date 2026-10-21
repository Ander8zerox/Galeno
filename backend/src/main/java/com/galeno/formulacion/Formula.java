package com.galeno.formulacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/**
 * Fórmula médica firmada. Es inmutable (Resolución 1995 de 1999): una corrección es una fórmula nueva,
 * nunca un UPDATE. Guarda el trabajo de IA del que salió para la trazabilidad.
 */
@Entity
@Immutable
@Table(name = "formulas")
public class Formula {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    /** Registro médico vigente al momento de firmar. */
    @Column(name = "registro_medico", length = 50)
    private String registroMedico;

    @Column(name = "ai_job_id")
    private UUID aiJobId;

    @Column(name = "atencion_id")
    private UUID atencionId;

    @Column(length = 1000)
    private String observaciones;

    @Column(name = "firmada_en", nullable = false)
    private Instant firmadaEn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "formula_items", joinColumns = @JoinColumn(name = "formula_id"))
    @OrderColumn(name = "orden")
    private List<FormulaItem> items = new ArrayList<>();

    protected Formula() {
    }

    public Formula(UUID pacienteId, UUID medicoId, String registroMedico, UUID aiJobId, UUID atencionId,
                   String observaciones, List<FormulaItem> items) {
        this.pacienteId = pacienteId;
        this.medicoId = medicoId;
        this.registroMedico = registroMedico;
        this.atencionId = atencionId;
        this.aiJobId = aiJobId;
        this.observaciones = observaciones;
        this.items = new ArrayList<>(items);
        this.firmadaEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public UUID getMedicoId() {
        return medicoId;
    }

    public String getRegistroMedico() {
        return registroMedico;
    }

    public UUID getAiJobId() {
        return aiJobId;
    }

    public UUID getAtencionId() {
        return atencionId;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public Instant getFirmadaEn() {
        return firmadaEn;
    }

    public List<FormulaItem> getItems() {
        return items;
    }
}
