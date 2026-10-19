package com.galeno.identidad;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Datos profesionales del médico. Son de la persona (ReTHUS), no de un consultorio. */
@Entity
@Table(name = "perfiles_medicos")
public class PerfilMedico {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "registro_medico", nullable = false, length = 50)
    private String registroMedico;

    @Column(length = 120)
    private String especialidad;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected PerfilMedico() {
    }

    public PerfilMedico(UUID usuarioId, String registroMedico, String especialidad) {
        this.usuarioId = usuarioId;
        this.registroMedico = registroMedico;
        this.especialidad = especialidad;
        this.actualizadoEn = Instant.now();
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getRegistroMedico() {
        return registroMedico;
    }

    public String getEspecialidad() {
        return especialidad;
    }
}
