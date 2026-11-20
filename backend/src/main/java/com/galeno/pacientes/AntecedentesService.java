package com.galeno.pacientes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.shared.security.AuthUser;
import com.galeno.shared.security.Permiso;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Alergias y antecedentes estructurados del paciente. Son datos clínicos: los ve y edita quien tiene HISTORIA_VER. Los
 * textos libres de antes ({@code pacientes.alergias} y {@code pacientes.antecedentes}) se muestran como registro
 * anterior hasta que el médico los pase a ítems y los borre.
 */
@Service
public class AntecedentesService {

    private final PacienteService pacientes;
    private final AlergiaPacienteRepository alergias;
    private final AntecedentePacienteRepository antecedentes;

    AntecedentesService(PacienteService pacientes, AlergiaPacienteRepository alergias,
                        AntecedentePacienteRepository antecedentes) {
        this.pacientes = pacientes;
        this.alergias = alergias;
        this.antecedentes = antecedentes;
    }

    @Transactional(readOnly = true)
    public Clinicos ver(UUID pacienteId) {
        exigirPermiso();
        Paciente p = pacientes.get(pacienteId);
        return vista(p);
    }

    /** Responde la pregunta "¿tiene alergias?". NIEGA no se acepta si ya hay alergias registradas. */
    @Transactional
    public Clinicos estadoAlergias(UUID pacienteId, Paciente.EstadoAlergias estado) {
        exigirPermiso();
        Paciente p = pacientes.get(pacienteId);
        if (estado == Paciente.EstadoAlergias.NIEGA && !alergias.findByPacienteIdOrderByCreadoEn(pacienteId).isEmpty()) {
            throw ApiProblem.conflict("El paciente tiene alergias registradas: quítelas antes de marcar que no tiene");
        }
        p.responderAlergias(estado);
        return vista(p);
    }

    @Transactional
    public Clinicos guardarAlergia(UUID pacienteId, UUID alergiaId, AlergiaPaciente.Tipo tipo, String agente,
                                   String reaccion, AlergiaPaciente.Severidad severidad) {
        exigirPermiso();
        Paciente p = pacientes.get(pacienteId);
        if (tipo == null || texto(agente) == null) {
            throw ApiProblem.badRequest("Indique el tipo y a qué es alérgico");
        }
        AlergiaPaciente a = alergiaId == null ? new AlergiaPaciente(pacienteId, AuthUser.current().id())
                : alergias.findById(alergiaId).filter(x -> x.getPacienteId().equals(pacienteId))
                        .orElseThrow(() -> ApiProblem.notFound("Alergia no encontrada"));
        a.actualizar(tipo, texto(agente), texto(reaccion), severidad);
        alergias.save(a);
        p.responderAlergias(Paciente.EstadoAlergias.SI);
        return vista(p);
    }

    @Transactional
    public Clinicos quitarAlergia(UUID pacienteId, UUID alergiaId) {
        exigirPermiso();
        Paciente p = pacientes.get(pacienteId);
        alergias.findById(alergiaId).filter(x -> x.getPacienteId().equals(pacienteId)).ifPresent(alergias::delete);
        return vista(p);
    }

    @Transactional
    public Clinicos guardarAntecedente(UUID pacienteId, UUID antecedenteId, AntecedentePaciente.Tipo tipo,
                                       String descripcion, String desde, Boolean vigente) {
        exigirPermiso();
        Paciente p = pacientes.get(pacienteId);
        if (tipo == null || texto(descripcion) == null) {
            throw ApiProblem.badRequest("Indique el tipo y la descripción del antecedente");
        }
        AntecedentePaciente a = antecedenteId == null ? new AntecedentePaciente(pacienteId, AuthUser.current().id())
                : antecedentes.findById(antecedenteId).filter(x -> x.getPacienteId().equals(pacienteId))
                        .orElseThrow(() -> ApiProblem.notFound("Antecedente no encontrado"));
        a.actualizar(tipo, texto(descripcion), texto(desde), vigente == null || vigente);
        antecedentes.save(a);
        return vista(p);
    }

    @Transactional
    public Clinicos quitarAntecedente(UUID pacienteId, UUID antecedenteId) {
        exigirPermiso();
        Paciente p = pacientes.get(pacienteId);
        antecedentes.findById(antecedenteId).filter(x -> x.getPacienteId().equals(pacienteId)).ifPresent(antecedentes::delete);
        return vista(p);
    }

    /** Borra el texto libre anterior (cuando el médico ya lo pasó a ítems). */
    @Transactional
    public Clinicos borrarTextoAnterior(UUID pacienteId, boolean deAlergias, boolean deAntecedentes) {
        exigirPermiso();
        Paciente p = pacientes.get(pacienteId);
        p.actualizarClinicos(deAlergias ? null : p.getAlergias(), deAntecedentes ? null : p.getAntecedentes());
        return vista(p);
    }

    /**
     * Alergias y antecedentes en texto, para la atención, los PDF y la IA (sin permiso: lo usan otros módulos que ya
     * lo verificaron). Incluye el texto anterior si todavía existe. Se usa desde {@link PacienteService#resumenClinico}.
     */
    static ResumenClinico resumen(Paciente p, AlergiaPacienteRepository alergias,
                                  AntecedentePacienteRepository antecedentes) {
        UUID pacienteId = p.getId();
        List<AlergiaPaciente> listaAlergias = alergias.findByPacienteIdOrderByCreadoEn(pacienteId);
        List<String> lineasAlergias = new ArrayList<>(listaAlergias.stream().map(AlergiaPaciente::texto).toList());
        if (lineasAlergias.isEmpty() && p.getAlergiasEstado() == Paciente.EstadoAlergias.NIEGA) {
            lineasAlergias.add("Niega alergias conocidas");
        }
        if (texto(p.getAlergias()) != null) {
            lineasAlergias.add(p.getAlergias().strip());
        }
        List<String> lineasAntecedentes = new ArrayList<>();
        antecedentes.findByPacienteIdOrderByCreadoEn(pacienteId).stream()
                .sorted(Comparator.comparing(AntecedentePaciente::getTipo))
                .collect(java.util.stream.Collectors.groupingBy(AntecedentePaciente::getTipo,
                        java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .forEach((tipo, items) -> lineasAntecedentes.add(tipo.nombre() + ": "
                        + String.join("; ", items.stream().map(AntecedentePaciente::texto).toList())));
        if (texto(p.getAntecedentes()) != null) {
            lineasAntecedentes.add(p.getAntecedentes().strip());
        }
        // Los agentes, uno por línea, para cruzarlos con los medicamentos de una fórmula
        List<String> agentes = new ArrayList<>(listaAlergias.stream().map(AlergiaPaciente::getAgente).toList());
        if (texto(p.getAlergias()) != null) {
            agentes.add(p.getAlergias().strip());
        }
        return new ResumenClinico(lineasAlergias.isEmpty() ? null : String.join("\n", lineasAlergias),
                lineasAntecedentes.isEmpty() ? null : String.join("\n", lineasAntecedentes),
                agentes.isEmpty() ? null : String.join("\n", agentes));
    }

    private Clinicos vista(Paciente p) {
        return new Clinicos(p.getAlergiasEstado(),
                alergias.findByPacienteIdOrderByCreadoEn(p.getId()).stream().map(AlergiaVista::de).toList(),
                antecedentes.findByPacienteIdOrderByCreadoEn(p.getId()).stream()
                        .sorted(Comparator.comparing(AntecedentePaciente::getTipo)).map(AntecedenteVista::de).toList(),
                texto(p.getAlergias()), texto(p.getAntecedentes()));
    }

    private static void exigirPermiso() {
        if (!AuthUser.current().puede(Permiso.HISTORIA_VER)) {
            throw ApiProblem.forbidden("Las alergias y los antecedentes son datos clínicos");
        }
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /**
     * @param alergiasEstado null: no se ha preguntado
     * @param alergiasTextoAnterior y {@code antecedentesTextoAnterior}: lo que se escribió antes como texto libre
     */
    public record Clinicos(Paciente.EstadoAlergias alergiasEstado, List<AlergiaVista> alergias,
                           List<AntecedenteVista> antecedentes, String alergiasTextoAnterior,
                           String antecedentesTextoAnterior) {
    }

    public record AlergiaVista(UUID id, AlergiaPaciente.Tipo tipo, String agente, String reaccion,
                               AlergiaPaciente.Severidad severidad) {
        static AlergiaVista de(AlergiaPaciente a) {
            return new AlergiaVista(a.getId(), a.getTipo(), a.getAgente(), a.getReaccion(), a.getSeveridad());
        }
    }

    public record AntecedenteVista(UUID id, AntecedentePaciente.Tipo tipo, String tipoNombre, String descripcion,
                                   String desde, boolean vigente) {
        static AntecedenteVista de(AntecedentePaciente a) {
            return new AntecedenteVista(a.getId(), a.getTipo(), a.getTipo().nombre(), a.getDescripcion(), a.getDesde(),
                    a.isVigente());
        }
    }

    /**
     * @param alergias una por línea; null si no hay nada registrado
     * @param antecedentes una línea por tipo
     * @param agentes los agentes de las alergias, uno por línea (para revisar una fórmula)
     */
    public record ResumenClinico(String alergias, String antecedentes, String agentes) {
    }
}
