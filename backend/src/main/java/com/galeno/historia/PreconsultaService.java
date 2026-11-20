package com.galeno.historia;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.agenda.AgendaPublica;
import com.galeno.agenda.PreconsultasDeCitas;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Preconsulta de una cita: la registra quien tiene PRECONSULTA_REGISTRAR (auxiliar, secretaria con el rol o el
 * médico) mientras la cita esté abierta y sin atención. La atención de esa cita arranca con estos datos.
 */
@Service
public class PreconsultaService implements PreconsultasDeCitas {

    private final PreconsultaRepository preconsultas;
    private final AgendaPublica agenda;
    private final AtencionRepository atenciones;

    PreconsultaService(PreconsultaRepository preconsultas, AgendaPublica agenda, AtencionRepository atenciones) {
        this.preconsultas = preconsultas;
        this.agenda = agenda;
        this.atenciones = atenciones;
    }

    @Transactional(readOnly = true)
    public Optional<Vista> deCita(UUID citaId) {
        agenda.cita(citaId).orElseThrow(() -> ApiProblem.notFound("Cita no encontrada"));
        return preconsultas.findByCitaId(citaId).map(Vista::de);
    }

    @Transactional
    public Vista guardar(UUID citaId, Preconsulta.Datos datos) {
        var cita = agenda.cita(citaId).orElseThrow(() -> ApiProblem.notFound("Cita no encontrada"));
        if (!cita.abierta()) {
            throw ApiProblem.conflict("La cita ya no está abierta: la preconsulta se registra antes de atender");
        }
        if (atenciones.findByCitaId(citaId).isPresent()) {
            throw ApiProblem.conflict("El médico ya abrió la atención de esta cita: regístrelo directamente allí");
        }
        AuthUser yo = AuthUser.current();
        Preconsulta p = preconsultas.findByCitaId(citaId).orElseGet(() -> new Preconsulta(citaId, cita.pacienteId()));
        p.actualizar(limpiar(datos), yo.id(), yo.nombre());
        return Vista.de(preconsultas.save(p));
    }

    /** Para la atención de la cita (sin permiso: lo verifica quien abre la atención). */
    Optional<Preconsulta.Datos> datosDe(UUID citaId) {
        return preconsultas.findByCitaId(citaId).map(Preconsulta::datos);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> conPreconsulta(Collection<UUID> citaIds) {
        if (citaIds.isEmpty()) {
            return Set.of();
        }
        return preconsultas.findByCitaIdIn(citaIds).stream().map(Preconsulta::getCitaId).collect(Collectors.toSet());
    }

    private static Preconsulta.Datos limpiar(Preconsulta.Datos d) {
        return new Preconsulta.Datos(texto(d.motivoConsulta()), d.taSistolica(), d.taDiastolica(), d.frecuenciaCardiaca(),
                d.frecuenciaRespiratoria(), d.temperatura(), d.saturacionO2(), d.pesoKg(), d.tallaCm(),
                texto(d.medicamentosActuales()), texto(d.observaciones()));
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public record Vista(UUID id, UUID citaId, UUID pacienteId, Preconsulta.Datos datos, String registradaPor,
                        Instant actualizadaEn) {
        static Vista de(Preconsulta p) {
            return new Vista(p.getId(), p.getCitaId(), p.getPacienteId(), p.datos(), p.getRegistradaPorNombre(),
                    p.getActualizadaEn());
        }
    }
}
