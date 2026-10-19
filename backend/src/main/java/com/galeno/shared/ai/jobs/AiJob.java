package com.galeno.shared.ai.jobs;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Trabajo de IA: es a la vez la cola (estado PENDIENTE → EN_PROCESO → COMPLETADO/FALLIDO)
 * y el registro de auditoría (quién pidió qué, con qué prompt y modelo, y qué respondió).
 */
@Entity
@Table(name = "ai_jobs")
public class AiJob {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiJobType tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiJobStatus estado;

    @Column(name = "paciente_id")
    private UUID pacienteId;

    @Column(name = "solicitado_por", nullable = false)
    private UUID solicitadoPor;

    @Column(name = "version_prompt", nullable = false, length = 60)
    private String versionPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String entrada;

    @JdbcTypeCode(SqlTypes.JSON)
    private String resultado;

    @Column(length = 1000)
    private String error;

    @Column(length = 100)
    private String modelo;

    @Column(name = "tokens_entrada")
    private Long tokensEntrada;

    @Column(name = "tokens_salida")
    private Long tokensSalida;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "iniciado_en")
    private Instant iniciadoEn;

    @Column(name = "finalizado_en")
    private Instant finalizadoEn;

    protected AiJob() {
    }

    public static AiJob pendiente(AiJobType tipo, UUID pacienteId, UUID solicitadoPor, String versionPrompt, String entradaJson) {
        AiJob job = new AiJob();
        job.tipo = tipo;
        job.estado = AiJobStatus.PENDIENTE;
        job.pacienteId = pacienteId;
        job.solicitadoPor = solicitadoPor;
        job.versionPrompt = versionPrompt;
        job.entrada = entradaJson;
        job.creadoEn = Instant.now();
        return job;
    }

    public void completar(String resultadoJson, String modelo, long tokensEntrada, long tokensSalida) {
        this.estado = AiJobStatus.COMPLETADO;
        this.resultado = resultadoJson;
        this.modelo = modelo;
        this.tokensEntrada = tokensEntrada;
        this.tokensSalida = tokensSalida;
        this.error = null;
        this.finalizadoEn = Instant.now();
    }

    public void fallar(String error) {
        this.estado = AiJobStatus.FALLIDO;
        this.error = error == null ? "Error desconocido" : error.substring(0, Math.min(error.length(), 1000));
        this.finalizadoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public AiJobType getTipo() {
        return tipo;
    }

    public AiJobStatus getEstado() {
        return estado;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public UUID getSolicitadoPor() {
        return solicitadoPor;
    }

    public String getVersionPrompt() {
        return versionPrompt;
    }

    public String getEntrada() {
        return entrada;
    }

    public String getResultado() {
        return resultado;
    }

    public String getError() {
        return error;
    }

    public String getModelo() {
        return modelo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getFinalizadoEn() {
        return finalizadoEn;
    }
}
