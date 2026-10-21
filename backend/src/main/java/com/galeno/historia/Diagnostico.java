package com.galeno.historia;

import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/** Diagnóstico CIE-10 de una atención. El tipo sigue la clasificación de RIPS. */
@Embeddable
public class Diagnostico {

    /** Formato colombiano: letra + 2 dígitos + dígito o X (J029, I10X); también se acepta el de 3 caracteres. */
    private static final Pattern CODIGO = Pattern.compile("^[A-Z][0-9]{2}[0-9X]?$");

    public enum Tipo {
        IMPRESION_DIAGNOSTICA,
        CONFIRMADO_NUEVO,
        CONFIRMADO_REPETIDO
    }

    @Column(name = "codigo_cie10", nullable = false, length = 10)
    private String codigoCie10;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(nullable = false)
    private boolean principal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Tipo tipo;

    protected Diagnostico() {
    }

    public Diagnostico(String codigoCie10, String descripcion, boolean principal, Tipo tipo) {
        String codigo = codigoCie10 == null ? "" : codigoCie10.strip().toUpperCase().replace(".", "");
        if (!CODIGO.matcher(codigo).matches()) {
            throw ApiProblem.badRequest("Código CIE-10 no válido: " + codigoCie10);
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw ApiProblem.badRequest("Falta la descripción del diagnóstico " + codigo);
        }
        this.codigoCie10 = codigo;
        this.descripcion = descripcion.strip();
        this.principal = principal;
        this.tipo = tipo == null ? Tipo.IMPRESION_DIAGNOSTICA : tipo;
    }

    public String getCodigoCie10() {
        return codigoCie10;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public Tipo getTipo() {
        return tipo;
    }
}
