package com.galeno.pacientes;

import java.util.Set;

/** Tipos de documento de identificación de la tabla de referencia de MinSalud (los que usa RIPS). */
public final class TiposDocumento {

    /** Para validaciones con {@code @Pattern}. */
    public static final String PATRON = "CC|TI|CE|PA|RC|PT|MS|AS|CN|NV|SC|PE|CD|DE|SI";

    public static final Set<String> VALIDOS = Set.of(PATRON.split("\\|"));

    private TiposDocumento() {
    }
}
