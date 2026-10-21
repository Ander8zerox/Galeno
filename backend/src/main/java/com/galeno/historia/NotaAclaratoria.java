package com.galeno.historia;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** Corrección o complemento de una atención firmada. También es inmutable. */
@Entity
@Immutable
@Table(name = "notas_aclaratorias")
public class NotaAclaratoria {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "atencion_id", nullable = false)
    private UUID atencionId;

    @Column(name = "autor_id", nullable = false)
    private UUID autorId;

    @Column(name = "autor_nombre", nullable = false, length = 200)
    private String autorNombre;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String texto;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    protected NotaAclaratoria() {
    }

    NotaAclaratoria(UUID atencionId, UUID autorId, String autorNombre, String texto) {
        this.atencionId = atencionId;
        this.autorId = autorId;
        this.autorNombre = autorNombre;
        this.texto = texto;
        this.creadaEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public String getTexto() {
        return texto;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }
}
