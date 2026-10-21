package com.galeno.identidad;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** Solicitud de registro de un consultorio que espera la verificación del correo (ver V10). */
@Entity
@Table(name = "registros_consultorio")
class RegistroPendiente {

    static final Duration VIGENCIA = Duration.ofHours(24);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false, length = 190)
    private String email;

    @Column(nullable = false, updatable = false, length = 200)
    private String nombre;

    @Column(name = "password_hash", nullable = false, updatable = false, length = 100)
    private String passwordHash;

    @Column(name = "consultorio_nombre", nullable = false, updatable = false, length = 200)
    private String consultorioNombre;

    @Column(updatable = false, length = 30)
    private String nit;

    @Column(name = "es_medico", nullable = false, updatable = false)
    private boolean esMedico;

    @Column(name = "registro_medico", updatable = false, length = 50)
    private String registroMedico;

    @Column(updatable = false, length = 120)
    private String especialidad;

    @Column(name = "terminos_version", nullable = false, updatable = false, length = 20)
    private String terminosVersion;

    @Column(updatable = false, length = 45)
    private String ip;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "confirmado_en")
    private Instant confirmadoEn;

    @Column(name = "consultorio_id")
    private UUID consultorioId;

    @Column(name = "codigo_partner", updatable = false, length = 20)
    private String codigoPartner;

    protected RegistroPendiente() {
    }

    RegistroPendiente(String tokenHash, String email, String nombre, String passwordHash, String consultorioNombre,
                      String nit, boolean esMedico, String registroMedico, String especialidad, String terminosVersion,
                      String ip, String codigoPartner) {
        this.codigoPartner = codigoPartner;
        this.tokenHash = tokenHash;
        this.email = email;
        this.nombre = nombre;
        this.passwordHash = passwordHash;
        this.consultorioNombre = consultorioNombre;
        this.nit = nit;
        this.esMedico = esMedico;
        this.registroMedico = registroMedico;
        this.especialidad = especialidad;
        this.terminosVersion = terminosVersion;
        this.ip = ip;
        this.creadoEn = Instant.now();
        this.expiraEn = creadoEn.plus(VIGENCIA);
    }

    boolean vigente() {
        return confirmadoEn == null && Instant.now().isBefore(expiraEn);
    }

    void confirmar(UUID consultorioId) {
        this.confirmadoEn = Instant.now();
        this.consultorioId = consultorioId;
    }

    String getEmail() {
        return email;
    }

    String getNombre() {
        return nombre;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getConsultorioNombre() {
        return consultorioNombre;
    }

    String getNit() {
        return nit;
    }

    boolean isEsMedico() {
        return esMedico;
    }

    String getRegistroMedico() {
        return registroMedico;
    }

    String getEspecialidad() {
        return especialidad;
    }

    String getTerminosVersion() {
        return terminosVersion;
    }

    String getCodigoPartner() {
        return codigoPartner;
    }

    Instant getExpiraEn() {
        return expiraEn;
    }
}
