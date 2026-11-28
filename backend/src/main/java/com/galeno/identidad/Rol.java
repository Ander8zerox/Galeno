package com.galeno.identidad;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import com.galeno.shared.security.Permiso;

public enum Rol {
    ADMIN(EnumSet.of(Permiso.EQUIPO_GESTIONAR, Permiso.PACIENTE_GESTIONAR, Permiso.AGENDA_GESTIONAR,
            Permiso.IMPORTAR_HISTORIAS, Permiso.SUSCRIPCION_GESTIONAR, Permiso.FORMATOS_GESTIONAR,
            Permiso.MODULOS_GESTIONAR, Permiso.RIPS_GESTIONAR, Permiso.RIPS_PACIENTE, Permiso.TRATAMIENTO_VER)),
    MEDICO(EnumSet.of(Permiso.PACIENTE_GESTIONAR, Permiso.HISTORIA_VER, Permiso.HISTORIA_REGISTRAR, Permiso.FORMULA_REDACTAR,
            Permiso.FORMULA_FIRMAR, Permiso.IA_DIAGNOSTICO, Permiso.AGENDA_GESTIONAR, Permiso.PRECONSULTA_REGISTRAR,
            Permiso.TRATAMIENTO_VER, Permiso.TRATAMIENTO_REGISTRAR, Permiso.TRATAMIENTO_ACEPTAR)),
    SECRETARIO(EnumSet.of(Permiso.PACIENTE_GESTIONAR, Permiso.AGENDA_GESTIONAR, Permiso.RIPS_PACIENTE,
            Permiso.TRATAMIENTO_VER, Permiso.TRATAMIENTO_ACEPTAR)),
    /** RIPS, lotes, notas, pagadores y precios. Se suma a otro rol (p. ej. la secretaria que factura). */
    FACTURACION(EnumSet.of(Permiso.PACIENTE_GESTIONAR, Permiso.RIPS_GESTIONAR, Permiso.RIPS_PACIENTE,
            Permiso.TRATAMIENTO_VER)),
    /**
     * Auxiliar o quien toma la preconsulta: signos vitales, motivo, medicamentos, alergias y antecedentes. Ve la agenda
     * de todos los médicos; no ve la historia clínica ni firma.
     */
    PRECONSULTA(EnumSet.of(Permiso.PACIENTE_GESTIONAR, Permiso.AGENDA_GESTIONAR, Permiso.PRECONSULTA_REGISTRAR));

    private final Set<Permiso> permisos;

    Rol(Set<Permiso> permisos) {
        this.permisos = permisos;
    }

    public static Set<Permiso> permisosDe(Collection<Rol> roles) {
        EnumSet<Permiso> todos = EnumSet.noneOf(Permiso.class);
        roles.forEach(r -> todos.addAll(r.permisos));
        return todos;
    }
}
