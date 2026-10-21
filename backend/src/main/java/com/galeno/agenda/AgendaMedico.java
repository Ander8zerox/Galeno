package com.galeno.agenda;

import java.time.Instant;
import java.time.LocalTime;
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

import org.hibernate.annotations.TenantId;

/** Configuración de la agenda de un médico en el consultorio. Su id es la membresía del médico. */
@Entity
@Table(name = "agendas_medico")
public class AgendaMedico {

    static final int DURACION_DEFECTO = 20;

    @Id
    @Column(name = "medico_membresia_id")
    private UUID medicoMembresiaId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "duracion_minutos", nullable = false)
    private int duracionMinutos;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agenda_bloques", joinColumns = @JoinColumn(name = "medico_membresia_id"))
    @OrderColumn(name = "orden")
    private List<Bloque> bloques = new ArrayList<>();

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected AgendaMedico() {
    }

    /** Horario inicial: lunes a viernes, 8:00–12:00 y 14:00–18:00, citas de 20 minutos. */
    static AgendaMedico porDefecto(UUID medicoMembresiaId) {
        AgendaMedico agenda = new AgendaMedico();
        agenda.medicoMembresiaId = medicoMembresiaId;
        agenda.duracionMinutos = DURACION_DEFECTO;
        for (int dia = 1; dia <= 5; dia++) {
            agenda.bloques.add(new Bloque(dia, LocalTime.of(8, 0), LocalTime.of(12, 0)));
            agenda.bloques.add(new Bloque(dia, LocalTime.of(14, 0), LocalTime.of(18, 0)));
        }
        agenda.actualizadoEn = Instant.now();
        return agenda;
    }

    void actualizar(int duracionMinutos, List<Bloque> nuevos) {
        this.duracionMinutos = duracionMinutos;
        this.bloques.clear();
        this.bloques.addAll(nuevos);
        this.actualizadoEn = Instant.now();
    }

    public UUID getMedicoMembresiaId() {
        return medicoMembresiaId;
    }

    public int getDuracionMinutos() {
        return duracionMinutos;
    }

    public List<Bloque> getBloques() {
        return bloques;
    }
}
