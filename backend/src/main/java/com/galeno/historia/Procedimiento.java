package com.galeno.historia;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Lo que se hizo en la atención, con su código CUPS (la consulta o un procedimiento). Va en el RIPS junto con los
 * diagnósticos CIE-10: el CUPS dice qué se hizo; el CIE-10, por qué.
 */
@Embeddable
public class Procedimiento {

    private static final Pattern CODIGO = Pattern.compile("^[0-9A-Z]{4,10}$");
    /** Numeración FDI: permanentes 11–48 y temporales 51–85. */
    private static final Pattern DIENTE = Pattern.compile("^([1-4][1-8]|[5-8][1-5])$");
    private static final Pattern SUPERFICIES = Pattern.compile("^[VLMDO]{1,5}$");
    static final int MAX_CANTIDAD = 99;

    @Column(name = "codigo_cups", nullable = false, length = 10)
    private String codigoCups;

    @Column(nullable = false, length = 400)
    private String descripcion;

    @Column(nullable = false)
    private int cantidad;

    /** Sin valor: el consultorio no lo ha fijado (el RIPS lo pedirá al generarse). */
    @Column(name = "valor_unitario", precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(length = 2)
    private String diente;

    /** V, L, M, D y O, como en el odontograma; p. ej. "MO". */
    @Column(length = 5)
    private String superficies;

    protected Procedimiento() {
    }

    /** @param descripcion la del catálogo CUPS (no la que envíe el navegador) */
    public Procedimiento(String codigoCups, String descripcion, int cantidad, BigDecimal valorUnitario, String diente,
                         String superficies) {
        String codigo = codigoCups == null ? "" : codigoCups.strip().toUpperCase();
        if (!CODIGO.matcher(codigo).matches()) {
            throw ApiProblem.badRequest("Código CUPS no válido: " + codigoCups);
        }
        if (cantidad < 1 || cantidad > MAX_CANTIDAD) {
            throw ApiProblem.badRequest("La cantidad de " + codigo + " debe estar entre 1 y " + MAX_CANTIDAD);
        }
        if (valorUnitario != null && valorUnitario.signum() < 0) {
            throw ApiProblem.badRequest("El valor de " + codigo + " no puede ser negativo");
        }
        String d = vacio(diente) ? null : diente.strip();
        if (d != null && !DIENTE.matcher(d).matches()) {
            throw ApiProblem.badRequest("Diente no válido en " + codigo + ": use la numeración FDI (11 a 48, 51 a 85)");
        }
        String s = vacio(superficies) ? null : ordenar(superficies.strip().toUpperCase());
        if (s != null && (d == null || !SUPERFICIES.matcher(s).matches())) {
            throw ApiProblem.badRequest("Superficies no válidas en " + codigo + ": use V, L, M, D u O, con el diente");
        }
        this.codigoCups = codigo;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.valorUnitario = valorUnitario == null ? null : valorUnitario.setScale(2, java.math.RoundingMode.HALF_UP);
        this.diente = d;
        this.superficies = s;
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    /** Sin repetir y en el orden V L M D O, para que "OM" y "MO" sean lo mismo. */
    private static String ordenar(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : "VLMDO".toCharArray()) {
            if (s.indexOf(c) >= 0) {
                b.append(c);
            }
        }
        return b.length() == s.chars().distinct().count() ? b.toString() : s;
    }

    /** En RIPS, las consultas (CUPS 890…) van en "consultas" y lo demás en "procedimientos". */
    public boolean esConsulta() {
        return codigoCups.startsWith("890");
    }

    public String getCodigoCups() {
        return codigoCups;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public String getDiente() {
        return diente;
    }

    public String getSuperficies() {
        return superficies;
    }
}
