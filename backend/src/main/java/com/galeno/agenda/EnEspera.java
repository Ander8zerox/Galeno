package com.galeno.agenda;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/** Un paciente en la lista de espera de la agenda. */
@Entity
@Table(name = "lista_espera")
public class EnEspera {

    public enum Franja {
        CUALQUIERA, MANANA, TARDE
    }

    public enum Estado {
        ACTIVA, ASIGNADA, RETIRADA
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    /** null: cualquier médico. */
    @Column(name = "medico_membresia_id", updatable = false)
    private UUID medicoMembresiaId;

    @Column(name = "duracion_minutos", nullable = false, updatable = false)
    private int duracionMinutos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Franja franja;

    @Column(name = "antes_de", updatable = false)
    private LocalDate antesDe;

    /** La cita que el paciente quiere adelantar; al asignar, la cita que se le dio. */
    @Column(name = "cita_id")
    private UUID citaId;

    @Column(length = 300, updatable = false)
    private String motivo;

    @Column(length = 300, updatable = false)
    private String nota;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Estado estado;

    @Column(name = "creada_por", nullable = false, updatable = false)
    private UUID creadaPor;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    protected EnEspera() {
    }

    EnEspera(UUID pacienteId, UUID medicoMembresiaId, int duracionMinutos, Franja franja, LocalDate antesDe, UUID citaId,
             String motivo, String nota, UUID creadaPor) {
        this.pacienteId = pacienteId;
        this.medicoMembresiaId = medicoMembresiaId;
        this.duracionMinutos = duracionMinutos;
        this.franja = franja;
        this.antesDe = antesDe;
        this.citaId = citaId;
        this.motivo = motivo;
        this.nota = nota;
        this.estado = Estado.ACTIVA;
        this.creadaPor = creadaPor;
        this.creadaEn = Instant.now();
        this.actualizadaEn = creadaEn;
    }

    void asignar(UUID cita) {
        exigirActiva();
        this.citaId = cita;
        this.estado = Estado.ASIGNADA;
        this.actualizadaEn = Instant.now();
    }

    void retirar() {
        exigirActiva();
        this.estado = Estado.RETIRADA;
        this.actualizadaEn = Instant.now();
    }

    private void exigirActiva() {
        if (estado != Estado.ACTIVA) {
            throw ApiProblem.conflict("El paciente ya no está en la lista de espera");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public UUID getMedicoMembresiaId() {
        return medicoMembresiaId;
    }

    public int getDuracionMinutos() {
        return duracionMinutos;
    }

    public Franja getFranja() {
        return franja;
    }

    public LocalDate getAntesDe() {
        return antesDe;
    }

    public UUID getCitaId() {
        return citaId;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getNota() {
        return nota;
    }

    public Estado getEstado() {
        return estado;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }
}
