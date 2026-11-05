package com.galeno.rips;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/** Datos del paciente que pide el RIPS y que la ficha no tiene (campos U03, U06, U07, U08 y U11). */
@Entity
@Table(name = "rips_pacientes")
class RipsPaciente {

    @Id
    @Column(name = "paciente_id")
    private UUID pacienteId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "tipo_usuario", length = 2)
    private String tipoUsuario;

    @Column(name = "pais_residencia", length = 3)
    private String paisResidencia;

    @Column(name = "municipio_residencia", length = 5)
    private String municipioResidencia;

    @Column(name = "zona_residencia", length = 2)
    private String zonaResidencia;

    @Column(name = "pais_origen", length = 3)
    private String paisOrigen;

    /** Pagador principal (EPS, prepagada, particular…): cada cita lo hereda. */
    @Column(name = "pagador_id")
    private UUID pagadorId;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected RipsPaciente() {
    }

    RipsPaciente(UUID pacienteId) {
        this.pacienteId = pacienteId;
    }

    void actualizar(String tipoUsuario, String paisResidencia, String municipioResidencia, String zonaResidencia,
                    String paisOrigen, UUID pagadorId) {
        this.pagadorId = pagadorId;
        this.tipoUsuario = tipoUsuario;
        this.paisResidencia = paisResidencia;
        this.municipioResidencia = municipioResidencia;
        this.zonaResidencia = zonaResidencia;
        this.paisOrigen = paisOrigen;
        this.actualizadoEn = Instant.now();
    }

    UUID getPacienteId() {
        return pacienteId;
    }

    String getTipoUsuario() {
        return tipoUsuario;
    }

    String getPaisResidencia() {
        return paisResidencia;
    }

    String getMunicipioResidencia() {
        return municipioResidencia;
    }

    String getZonaResidencia() {
        return zonaResidencia;
    }

    String getPaisOrigen() {
        return paisOrigen;
    }

    UUID getPagadorId() {
        return pagadorId;
    }
}
