package com.galeno.historia;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** Auditoría: cada consulta o cambio de la historia clínica deja un registro. */
@Entity
@Immutable
@Table(name = "accesos_historia")
public class AccesoHistoria {

    public enum Accion {
        VER_HISTORIA,
        VER_ATENCION,
        CREAR_ATENCION,
        FIRMAR_ATENCION,
        NOTA_ACLARATORIA,
        SUBIR_DOCUMENTO,
        DESCARGAR_DOCUMENTO,
        IMPRIMIR_ATENCION,
        IMPRIMIR_HISTORIA,
        IMPRIMIR_FORMULA,
        IMPORTAR_ATENCIONES,
        DESHACER_IMPORTACION
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Accion accion;

    @Column(name = "recurso_id")
    private UUID recursoId;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected AccesoHistoria() {
    }

    AccesoHistoria(UUID pacienteId, UUID usuarioId, Accion accion, UUID recursoId) {
        this.pacienteId = pacienteId;
        this.usuarioId = usuarioId;
        this.accion = accion;
        this.recursoId = recursoId;
        this.creadoEn = Instant.now();
    }
}
