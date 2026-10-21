package com.galeno.historia;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Registro de una consulta. Mientras es BORRADOR solo su autor la ve y la edita. Al firmarla queda
 * inmutable (Res. 1995 de 1999): cualquier corrección posterior es una {@link NotaAclaratoria}.
 */
@Entity
@Table(name = "atenciones")
public class Atencion {

    public enum Estado {
        BORRADOR,
        FIRMADA
    }

    public enum Origen {
        GALENO,
        IMPORTADA
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    /** Vacío en las atenciones importadas: no las registró un médico de Galeno. */
    @Column(name = "medico_membresia_id", updatable = false)
    private UUID medicoMembresiaId;

    @Column(name = "medico_usuario_id", updatable = false)
    private UUID medicoUsuarioId;

    @Column(name = "medico_nombre", nullable = false, updatable = false, length = 200)
    private String medicoNombre;

    @Column(name = "registro_medico", length = 50)
    private String registroMedico;

    @Column(name = "cita_id", updatable = false)
    private UUID citaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Origen origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "fecha_atencion", nullable = false)
    private Instant fechaAtencion;

    @Column(name = "motivo_consulta", length = 1000)
    private String motivoConsulta;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "enfermedad_actual")
    private String enfermedadActual;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String antecedentes;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "revision_sistemas")
    private String revisionSistemas;

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

    @Column(name = "peso_kg", precision = 5, scale = 1)
    private BigDecimal pesoKg;

    @Column(name = "talla_cm", precision = 5, scale = 1)
    private BigDecimal tallaCm;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "examen_fisico")
    private String examenFisico;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String analisis;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String plan;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "atencion_diagnosticos", joinColumns = @JoinColumn(name = "atencion_id"))
    @OrderColumn(name = "orden")
    private List<Diagnostico> diagnosticos = new ArrayList<>();

    @Column(name = "lote_importacion_id", updatable = false)
    private UUID loteImportacionId;

    /** Identifica el servicio de origen (factura, usuario, servicio) para no importarlo dos veces. */
    @Column(name = "clave_importacion", updatable = false, length = 160)
    private String claveImportacion;

    /** Bloqueo optimista: dos pestañas no pueden pisarse el borrador. */
    @Version
    private long version;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    @Column(name = "firmada_en")
    private Instant firmadaEn;

    protected Atencion() {
    }

    static Atencion nueva(UUID pacienteId, UUID medicoMembresiaId, UUID medicoUsuarioId, String medicoNombre,
                          UUID citaId, String antecedentesPaciente) {
        Atencion a = new Atencion();
        a.pacienteId = pacienteId;
        a.medicoMembresiaId = medicoMembresiaId;
        a.medicoUsuarioId = medicoUsuarioId;
        a.medicoNombre = medicoNombre;
        a.citaId = citaId;
        a.origen = Origen.GALENO;
        a.estado = Estado.BORRADOR;
        a.fechaAtencion = Instant.now();
        a.antecedentes = antecedentesPaciente;
        a.creadaEn = a.fechaAtencion;
        a.actualizadaEn = a.fechaAtencion;
        return a;
    }

    /** Atención que viene de otro sistema (p. ej. RIPS): queda cerrada e inmutable desde que se importa. */
    static Atencion importada(UUID pacienteId, Instant fecha, String descripcion, String profesional,
                              List<Diagnostico> diagnosticos, String claveImportacion, UUID loteImportacionId) {
        Atencion a = new Atencion();
        a.pacienteId = pacienteId;
        a.medicoNombre = profesional;
        a.origen = Origen.IMPORTADA;
        a.estado = Estado.FIRMADA;
        a.fechaAtencion = fecha;
        a.motivoConsulta = descripcion;
        a.diagnosticos.addAll(diagnosticos);
        a.claveImportacion = claveImportacion;
        a.loteImportacionId = loteImportacionId;
        a.creadaEn = Instant.now();
        a.actualizadaEn = a.creadaEn;
        a.firmadaEn = a.creadaEn;
        return a;
    }

    void actualizar(Contenido c) {
        exigirBorrador();
        long principales = c.diagnosticos().stream().filter(Diagnostico::isPrincipal).count();
        if (principales > 1) {
            throw ApiProblem.badRequest("Solo puede haber un diagnóstico principal");
        }
        this.motivoConsulta = c.motivoConsulta();
        this.enfermedadActual = c.enfermedadActual();
        this.antecedentes = c.antecedentes();
        this.revisionSistemas = c.revisionSistemas();
        this.taSistolica = c.taSistolica();
        this.taDiastolica = c.taDiastolica();
        this.frecuenciaCardiaca = c.frecuenciaCardiaca();
        this.frecuenciaRespiratoria = c.frecuenciaRespiratoria();
        this.temperatura = c.temperatura();
        this.saturacionO2 = c.saturacionO2();
        this.pesoKg = c.pesoKg();
        this.tallaCm = c.tallaCm();
        this.examenFisico = c.examenFisico();
        this.analisis = c.analisis();
        this.plan = c.plan();
        this.diagnosticos.clear();
        this.diagnosticos.addAll(c.diagnosticos());
        this.actualizadaEn = Instant.now();
    }

    void firmar(String registroMedico) {
        exigirBorrador();
        if (motivoConsulta == null || motivoConsulta.isBlank()) {
            throw ApiProblem.badRequest("Para firmar, registre el motivo de consulta");
        }
        if (diagnosticos.stream().filter(Diagnostico::isPrincipal).count() != 1) {
            throw ApiProblem.badRequest("Para firmar, marque exactamente un diagnóstico principal");
        }
        this.registroMedico = registroMedico;
        this.estado = Estado.FIRMADA;
        this.firmadaEn = Instant.now();
        this.actualizadaEn = firmadaEn;
    }

    boolean firmada() {
        return estado == Estado.FIRMADA;
    }

    private void exigirBorrador() {
        if (estado != Estado.BORRADOR) {
            throw ApiProblem.conflict("La atención ya está firmada y no se puede modificar. Agregue una nota aclaratoria.");
        }
    }

    /** Lo que el médico escribe. */
    record Contenido(String motivoConsulta, String enfermedadActual, String antecedentes, String revisionSistemas,
                     Integer taSistolica, Integer taDiastolica, Integer frecuenciaCardiaca,
                     Integer frecuenciaRespiratoria, BigDecimal temperatura, Integer saturacionO2, BigDecimal pesoKg,
                     BigDecimal tallaCm, String examenFisico, String analisis, String plan,
                     List<Diagnostico> diagnosticos) {
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

    public UUID getMedicoUsuarioId() {
        return medicoUsuarioId;
    }

    public String getMedicoNombre() {
        return medicoNombre;
    }

    public String getRegistroMedico() {
        return registroMedico;
    }

    public UUID getCitaId() {
        return citaId;
    }

    public Origen getOrigen() {
        return origen;
    }

    public Estado getEstado() {
        return estado;
    }

    public Instant getFechaAtencion() {
        return fechaAtencion;
    }

    public String getMotivoConsulta() {
        return motivoConsulta;
    }

    public String getEnfermedadActual() {
        return enfermedadActual;
    }

    public String getAntecedentes() {
        return antecedentes;
    }

    public String getRevisionSistemas() {
        return revisionSistemas;
    }

    public Integer getTaSistolica() {
        return taSistolica;
    }

    public Integer getTaDiastolica() {
        return taDiastolica;
    }

    public Integer getFrecuenciaCardiaca() {
        return frecuenciaCardiaca;
    }

    public Integer getFrecuenciaRespiratoria() {
        return frecuenciaRespiratoria;
    }

    public BigDecimal getTemperatura() {
        return temperatura;
    }

    public Integer getSaturacionO2() {
        return saturacionO2;
    }

    public BigDecimal getPesoKg() {
        return pesoKg;
    }

    public BigDecimal getTallaCm() {
        return tallaCm;
    }

    public String getExamenFisico() {
        return examenFisico;
    }

    public String getAnalisis() {
        return analisis;
    }

    public String getPlan() {
        return plan;
    }

    public List<Diagnostico> getDiagnosticos() {
        return diagnosticos;
    }

    public UUID getLoteImportacionId() {
        return loteImportacionId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getFirmadaEn() {
        return firmadaEn;
    }
}
