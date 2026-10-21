package com.galeno.pacientes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "pacientes")
public class Paciente {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "tipo_documento", nullable = false, length = 5)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 30)
    private String numeroDocumento;

    @Column(nullable = false, length = 120)
    private String nombres;

    @Column(nullable = false, length = 120)
    private String apellidos;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(nullable = false, length = 1)
    private String sexo;

    @Column(length = 30)
    private String telefono;

    @Column(length = 190)
    private String email;

    @Column(length = 2000)
    private String alergias;

    @Column(length = 4000)
    private String antecedentes;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Creado por una importación sin nombre (el RIPS no lo trae): hay que completar sus datos. */
    @Column(name = "datos_incompletos", nullable = false)
    private boolean datosIncompletos;

    @Column(name = "lote_importacion_id")
    private UUID loteImportacionId;

    protected Paciente() {
    }

    public Paciente(String tipoDocumento, String numeroDocumento) {
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.creadoEn = Instant.now();
    }

    /** Paciente creado desde una importación: se identifica por documento hasta que alguien complete el nombre. */
    static Paciente importado(String tipoDocumento, String numeroDocumento, LocalDate fechaNacimiento, String sexo,
                              UUID loteImportacionId) {
        Paciente p = new Paciente(tipoDocumento, numeroDocumento);
        p.nombres = "Paciente";
        p.apellidos = tipoDocumento + " " + numeroDocumento;
        p.fechaNacimiento = fechaNacimiento;
        p.sexo = sexo;
        p.datosIncompletos = true;
        p.loteImportacionId = loteImportacionId;
        p.actualizadoEn = p.creadoEn;
        return p;
    }

    void actualizarDemograficos(String nombres, String apellidos, LocalDate fechaNacimiento, String sexo,
                                String telefono, String email) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.fechaNacimiento = fechaNacimiento;
        this.sexo = sexo;
        this.telefono = telefono;
        this.email = email;
        this.datosIncompletos = false;
        this.actualizadoEn = Instant.now();
    }

    /** Solo personal con permiso HISTORIA_VER (médicos). */
    void actualizarClinicos(String alergias, String antecedentes) {
        this.alergias = alergias;
        this.antecedentes = antecedentes;
        this.actualizadoEn = Instant.now();
    }

    public int edad() {
        return Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }

    /**
     * Contexto clínico para la IA sin datos que identifiquen al paciente (nombre, documento,
     * contacto): se envía solo lo necesario para el razonamiento clínico.
     */
    public String resumenAnonimizado() {
        return """
                Paciente: %d años, sexo %s.
                Alergias conocidas: %s
                Antecedentes: %s""".formatted(edad(), sexo, valueOrNone(alergias), valueOrNone(antecedentes));
    }

    private static String valueOrNone(String value) {
        return value == null || value.isBlank() ? "no registradas" : value.strip();
    }

    public UUID getId() {
        return id;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public String getSexo() {
        return sexo;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getEmail() {
        return email;
    }

    public String getAlergias() {
        return alergias;
    }

    public boolean isDatosIncompletos() {
        return datosIncompletos;
    }

    public UUID getLoteImportacionId() {
        return loteImportacionId;
    }

    public String getAntecedentes() {
        return antecedentes;
    }
}
