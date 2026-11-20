package com.galeno.historia;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** Lo que se registra antes de que el paciente entre al consultorio: motivo, signos vitales y medicamentos actuales. */
@Entity
@Table(name = "preconsultas")
public class Preconsulta {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cita_id", nullable = false, updatable = false)
    private UUID citaId;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    @Column(name = "motivo_consulta", length = 2000)
    private String motivoConsulta;

    @Column(name = "ta_sistolica")
    private Integer taSistolica;

    @Column(name = "ta_diastolica")
    private Integer taDiastolica;

    @Column(name = "frecuencia_cardiaca")
    private Integer frecuenciaCardiaca;

    @Column(name = "frecuencia_respiratoria")
    private Integer frecuenciaRespiratoria;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperatura;

    @Column(name = "saturacion_o2")
    private Integer saturacionO2;

    @Column(name = "peso_kg", precision = 5, scale = 2)
    private BigDecimal pesoKg;

    @Column(name = "talla_cm", precision = 5, scale = 1)
    private BigDecimal tallaCm;

    @Column(name = "medicamentos_actuales", length = 2000)
    private String medicamentosActuales;

    @Column(length = 2000)
    private String observaciones;

    @Column(name = "registrada_por", nullable = false)
    private UUID registradaPor;

    @Column(name = "registrada_por_nombre", nullable = false, length = 200)
    private String registradaPorNombre;

    @Column(name = "registrada_en", nullable = false, updatable = false)
    private Instant registradaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    protected Preconsulta() {
    }

    Preconsulta(UUID citaId, UUID pacienteId) {
        this.citaId = citaId;
        this.pacienteId = pacienteId;
        this.registradaEn = Instant.now();
    }

    void actualizar(Datos d, UUID por, String porNombre) {
        this.motivoConsulta = d.motivoConsulta();
        this.taSistolica = d.taSistolica();
        this.taDiastolica = d.taDiastolica();
        this.frecuenciaCardiaca = d.frecuenciaCardiaca();
        this.frecuenciaRespiratoria = d.frecuenciaRespiratoria();
        this.temperatura = d.temperatura();
        this.saturacionO2 = d.saturacionO2();
        this.pesoKg = d.pesoKg();
        this.tallaCm = d.tallaCm();
        this.medicamentosActuales = d.medicamentosActuales();
        this.observaciones = d.observaciones();
        this.registradaPor = por;
        this.registradaPorNombre = porNombre;
        this.actualizadaEn = Instant.now();
    }

    Datos datos() {
        return new Datos(motivoConsulta, taSistolica, taDiastolica, frecuenciaCardiaca, frecuenciaRespiratoria,
                temperatura, saturacionO2, pesoKg, tallaCm, medicamentosActuales, observaciones);
    }

    /** Lo que se registra (todo opcional). */
    public record Datos(String motivoConsulta, Integer taSistolica, Integer taDiastolica, Integer frecuenciaCardiaca,
                        Integer frecuenciaRespiratoria, BigDecimal temperatura, Integer saturacionO2, BigDecimal pesoKg,
                        BigDecimal tallaCm, String medicamentosActuales, String observaciones) {
    }

    public UUID getId() {
        return id;
    }

    public UUID getCitaId() {
        return citaId;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public String getRegistradaPorNombre() {
        return registradaPorNombre;
    }

    public Instant getActualizadaEn() {
        return actualizadaEn;
    }
}
