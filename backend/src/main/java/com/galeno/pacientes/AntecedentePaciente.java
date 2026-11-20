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

/** Un antecedente del paciente, clasificado por tipo (bloque 5 del RDA). Las alergias van aparte. */
@Entity
@Table(name = "paciente_antecedentes")
public class AntecedentePaciente {

    /** En el orden en que se muestran. */
    public enum Tipo {
        PATOLOGICO("Patológicos"),
        QUIRURGICO("Quirúrgicos"),
        TRAUMATICO("Traumáticos"),
        FARMACOLOGICO("Farmacológicos"),
        TOXICO("Tóxicos"),
        FAMILIAR("Familiares"),
        GINECO_OBSTETRICO("Gineco-obstétricos"),
        INMUNOLOGICO("Inmunológicos (vacunas)"),
        OTRO("Otros");

        private final String nombre;

        Tipo(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }
    }

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

    @Column(nullable = false, length = 500)
    private String descripcion;

    /** Desde cuándo, en texto corto ("2019", "infancia"); null si no se sabe. */
    @Column(length = 20)
    private String desde;

    /** false: resuelto o del pasado (p. ej. una cirugía ya hecha sigue siendo antecedente, pero no activo). */
    @Column(nullable = false)
    private boolean vigente;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private UUID creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected AntecedentePaciente() {
    }

    AntecedentePaciente(UUID pacienteId, UUID creadoPor) {
        this.pacienteId = pacienteId;
        this.creadoPor = creadoPor;
        this.creadoEn = Instant.now();
    }

    void actualizar(Tipo tipo, String descripcion, String desde, boolean vigente) {
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.desde = desde;
        this.vigente = vigente;
        this.actualizadoEn = Instant.now();
    }

    /** "Hipertensión arterial (desde 2019)" o "… (resuelto)" */
    String texto() {
        StringBuilder b = new StringBuilder(descripcion);
        if (desde != null) {
            b.append(" (desde ").append(desde).append(")");
        }
        if (!vigente) {
            b.append(" (no vigente)");
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

    public String getDescripcion() {
        return descripcion;
    }

    public String getDesde() {
        return desde;
    }

    public boolean isVigente() {
        return vigente;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
