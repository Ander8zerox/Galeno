package com.galeno.shared.security;

/**
 * Lo que se autoriza en el código. Los roles solo son conjuntos de permisos (ver {@code identidad.Rol}):
 * agregar un rol nuevo (enfermera, facturador) no obliga a tocar controladores.
 */
public enum Permiso {
    /** Invitar, suspender y cambiar roles de los miembros del consultorio. */
    EQUIPO_GESTIONAR,
    /** Crear y editar datos demográficos de pacientes. */
    PACIENTE_GESTIONAR,
    /** Ver y editar datos clínicos: alergias, antecedentes, historia. */
    HISTORIA_VER,
    FORMULA_REDACTAR,
    /** Requiere además registro médico. */
    FORMULA_FIRMAR,
    IA_DIAGNOSTICO,
    /** Ver y gestionar citas. El alcance (qué médicos) lo decide el módulo de agenda según el rol. */
    AGENDA_GESTIONAR
}
