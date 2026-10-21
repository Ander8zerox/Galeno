package com.galeno.pacientes;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/** API pública del módulo de pacientes: otros módulos la usan en lugar del repositorio. */
@Service
public class PacienteService {

    private final PacienteRepository repository;

    PacienteService(PacienteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Paciente get(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiProblem.notFound("Paciente no encontrado"));
    }

    /** Nombre y documento de varios pacientes a la vez (p. ej. para listar la agenda sin N+1). */
    @Transactional(readOnly = true)
    public Map<UUID, PacienteResumen> resumenes(Collection<UUID> ids) {
        return repository.findAllById(ids).stream().collect(Collectors.toMap(Paciente::getId,
                p -> new PacienteResumen(p.getId(), p.getNombres() + " " + p.getApellidos(), p.getTipoDocumento(),
                        p.getNumeroDocumento(), p.getTelefono())));
    }

    public record PacienteResumen(UUID id, String nombre, String tipoDocumento, String numeroDocumento, String telefono) {
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Paciente> buscar(UUID id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Paciente> porDocumento(String tipoDocumento, String numeroDocumento) {
        return repository.findByTipoDocumentoAndNumeroDocumento(tipoDocumento, numeroDocumento);
    }

    /** Para la importación: el RIPS identifica al paciente pero no trae su nombre. */
    @Transactional
    public Paciente crearImportado(String tipoDocumento, String numeroDocumento, java.time.LocalDate fechaNacimiento,
                                   String sexo, UUID loteImportacionId) {
        return repository.save(Paciente.importado(tipoDocumento, numeroDocumento, fechaNacimiento, sexo, loteImportacionId));
    }

    /** Al deshacer una importación (quien llama verifica antes que el paciente no tenga otra actividad). */
    @Transactional
    public void eliminarImportado(UUID id, UUID loteImportacionId) {
        repository.findById(id)
                .filter(p -> loteImportacionId.equals(p.getLoteImportacionId()))
                .ifPresent(repository::delete);
    }

    @Transactional(readOnly = true)
    public Page<Paciente> search(String q, Pageable pageable) {
        return repository.search(q == null || q.isBlank() ? null : q.trim(), pageable);
    }

    /**
     * @param datosClinicos si es {@code false} (p. ej. un secretario) se ignoran alergias y antecedentes:
     *                      ni se guardan ni se borran los existentes
     */
    @Transactional
    public Paciente create(PacienteRequest r, boolean datosClinicos) {
        if (repository.existsByTipoDocumentoAndNumeroDocumento(r.tipoDocumento(), r.numeroDocumento())) {
            throw ApiProblem.conflict("Ya existe un paciente con ese documento");
        }
        Paciente paciente = new Paciente(r.tipoDocumento(), r.numeroDocumento());
        apply(paciente, r, datosClinicos);
        return repository.save(paciente);
    }

    @Transactional
    public Paciente update(UUID id, PacienteRequest r, boolean datosClinicos) {
        Paciente paciente = get(id);
        apply(paciente, r, datosClinicos);
        return paciente;
    }

    private static void apply(Paciente p, PacienteRequest r, boolean datosClinicos) {
        p.actualizarDemograficos(r.nombres(), r.apellidos(), r.fechaNacimiento(), r.sexo(), r.telefono(), r.email());
        if (datosClinicos) {
            p.actualizarClinicos(r.alergias(), r.antecedentes());
        }
    }
}
