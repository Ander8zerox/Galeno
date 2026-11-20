package com.galeno.agenda;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Tipos de cita del consultorio. Los ve quien gestiona la agenda; los crea y cambia el administrador. Un consultorio
 * nuevo arranca con "Primera vez" (30 min) y "Control" (20 min).
 */
@Service
public class TiposCitaService {

    private final TipoCitaRepository tipos;

    TiposCitaService(TipoCitaRepository tipos) {
        this.tipos = tipos;
    }

    @Transactional
    public List<TipoCitaVista> listar() {
        List<TipoCita> lista = tipos.findAllByOrderByCreadoEn();
        if (lista.isEmpty()) {
            tipos.save(new TipoCita("Primera vez", 30, TipoCita.Clase.PRIMERA_VEZ, null));
            tipos.save(new TipoCita("Control", 20, TipoCita.Clase.CONTROL, null));
            lista = tipos.findAllByOrderByCreadoEn();
        }
        return lista.stream().map(TipoCitaVista::de).toList();
    }

    @Transactional
    public TipoCitaVista guardar(UUID id, String nombre, int duracionMinutos, TipoCita.Clase clase, String cups,
                                 Boolean activo) {
        if (!AuthUser.current().tieneRol("ADMIN")) {
            throw ApiProblem.forbidden("Solo el administrador configura los tipos de cita");
        }
        String n = nombre == null ? "" : nombre.strip();
        if (n.isEmpty()) {
            throw ApiProblem.badRequest("Escriba el nombre del tipo de cita");
        }
        if (duracionMinutos < 5 || duracionMinutos > 240) {
            throw ApiProblem.badRequest("La duración debe estar entre 5 y 240 minutos");
        }
        String c = cups == null || cups.isBlank() ? null : cups.strip();
        if (c != null && !c.matches("[0-9]{6}")) {
            throw ApiProblem.badRequest("El CUPS lleva 6 dígitos");
        }
        boolean repetido = id == null ? tipos.existsByNombreIgnoreCase(n) : tipos.existsByNombreIgnoreCaseAndIdNot(n, id);
        if (repetido) {
            throw ApiProblem.conflict("Ya hay un tipo de cita con ese nombre");
        }
        TipoCita t;
        if (id == null) {
            t = new TipoCita(n, duracionMinutos, clase, c);
        } else {
            t = buscar(id);
            t.actualizar(n, duracionMinutos, clase, c, activo == null || activo);
        }
        return TipoCitaVista.de(tipos.save(t));
    }

    /** Para agendar: el tipo tiene que existir y estar activo. */
    @Transactional(readOnly = true)
    public TipoCita paraAgendar(UUID id) {
        TipoCita t = buscar(id);
        if (!t.isActivo()) {
            throw ApiProblem.badRequest("Ese tipo de cita está desactivado");
        }
        return t;
    }

    TipoCita buscar(UUID id) {
        return tipos.findById(id).orElseThrow(() -> ApiProblem.notFound("Tipo de cita no encontrado"));
    }

    public record TipoCitaVista(UUID id, String nombre, int duracionMinutos, TipoCita.Clase clase, String cups,
                                boolean activo) {
        static TipoCitaVista de(TipoCita t) {
            return new TipoCitaVista(t.getId(), t.getNombre(), t.getDuracionMinutos(), t.getClase(), t.getCups(),
                    t.isActivo());
        }
    }
}
