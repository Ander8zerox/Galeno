package com.galeno.identidad;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import com.galeno.shared.security.Permiso;

public enum Rol {
    ADMIN(EnumSet.of(Permiso.EQUIPO_GESTIONAR, Permiso.PACIENTE_GESTIONAR)),
    MEDICO(EnumSet.of(Permiso.PACIENTE_GESTIONAR, Permiso.HISTORIA_VER, Permiso.FORMULA_REDACTAR,
            Permiso.FORMULA_FIRMAR, Permiso.IA_DIAGNOSTICO)),
    SECRETARIO(EnumSet.of(Permiso.PACIENTE_GESTIONAR));

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
