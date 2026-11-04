package com.galeno.modulos;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/** Módulos opcionales del consultorio actual. Los demás módulos preguntan aquí antes de responder. */
@Service
public class ModulosService {

    private final ModuloConsultorioRepository modulos;

    ModulosService(ModuloConsultorioRepository modulos) {
        this.modulos = modulos;
    }

    @Transactional(readOnly = true)
    public Set<Modulo> activos() {
        return modulos.findAll().stream().map(ModuloConsultorio::getModulo)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Modulo.class)));
    }

    @Transactional(readOnly = true)
    public boolean activo(Modulo modulo) {
        return modulos.existsByModulo(modulo);
    }

    /** Para los servicios del módulo: sin activar, responde 404 (como si no existiera). */
    @Transactional(readOnly = true)
    public void exigir(Modulo modulo) {
        if (!activo(modulo)) {
            throw ApiProblem.notFound("El módulo " + modulo.nombre() + " no está activo en este consultorio");
        }
    }

    /** Desactivar no borra nada: lo que el módulo generó se sigue viendo; solo deja de usarse. */
    @Transactional
    public List<ModuloVista> activar(Modulo modulo, boolean activo) {
        var actual = modulos.findByModulo(modulo);
        if (activo && actual.isEmpty()) {
            modulos.save(new ModuloConsultorio(modulo));
        } else if (!activo) {
            actual.ifPresent(modulos::delete);
        }
        modulos.flush();
        return vista();
    }

    @Transactional(readOnly = true)
    public List<ModuloVista> vista() {
        Set<Modulo> activos = activos();
        return Arrays.stream(Modulo.values())
                .map(m -> new ModuloVista(m, m.nombre(), m.descripcion(), activos.contains(m))).toList();
    }

    public record ModuloVista(Modulo modulo, String nombre, String descripcion, boolean activo) {
    }
}
