package com.galeno.suscripciones;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Configuración de la plataforma editable desde la consola (p. ej. los datos para pagar). */
@Entity
@Table(name = "parametros_plataforma")
class ParametroPlataforma {

    static final String DATOS_PAGO = "datos_pago";

    @Id
    @Column(length = 60)
    private String clave;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String valor;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected ParametroPlataforma() {
    }

    void cambiar(String valor) {
        this.valor = valor;
        this.actualizadoEn = Instant.now();
    }

    String getValor() {
        return valor;
    }
}
