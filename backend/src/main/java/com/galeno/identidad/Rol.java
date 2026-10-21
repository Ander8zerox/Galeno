package com.galeno.identidad;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import com.galeno.shared.security.Permiso;

public enum Rol {
    ADMIN(EnumSet.of(Permiso.EQUIPO_GESTIONAR, Permiso.PACIENTE_GESTIONAR, Permiso.AGENDA_GESTIONAR,
            Permiso.IMPORTAR_HISTORIAS)),
    MEDICO(EnumSet.of(Permiso.PACIENTE_GESTIONAR, Permiso.HISTORIA_VER, Permiso.HISTORIA_REGISTRAR, Permiso.FORMULA_REDACTAR,
            Permiso.FORMULA_FIRMAR, Permiso.IA_DIAGNOSTICO, Permiso.AGENDA_GESTIONAR)),
    SECRETARIO(EnumSet.of(Permiso.PACIENTE_GESTIONAR, Permiso.AGENDA_GESTIONAR));

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
