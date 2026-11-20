package com.galeno.pacientes;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;
import com.galeno.shared.web.TopesDiarios;

/** API pública del módulo de pacientes: otros módulos la usan en lugar del repositorio. */
@Service
public class PacienteService {

    /** Versión del texto de autorización de datos que se muestra en el formulario (cambia si cambia el texto). */
    public static final String TEXTO_AUTORIZACION = "v1";

    private final PacienteRepository repository;
    private final TopesDiarios topes;

    PacienteService(PacienteRepository repository, TopesDiarios topes) {
        this.repository = repository;
        this.topes = topes;
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
                        p.getNumeroDocumento(), p.getTelefono(), p.getFechaNacimiento() == null ? null : p.edad(),
                        prioridades(p))));
    }

    /**
     * @param edad null si no tiene fecha de nacimiento
     * @param prioridades por qué tiene atención prioritaria (menor, mayor de 60, gestante, discapacidad); vacía si no
     */
    public record PacienteResumen(UUID id, String nombre, String tipoDocumento, String numeroDocumento, String telefono,
                                  Integer edad, java.util.List<String> prioridades) {
    }

    /** Atención prioritaria: menores de 18, mayores de 60, gestantes y personas con discapacidad. */
    static java.util.List<String> prioridades(Paciente p) {
        java.util.List<String> lista = new java.util.ArrayList<>();
        Integer edad = p.getFechaNacimiento() == null ? null : p.edad();
        if (edad != null && edad < 18) {
            lista.add("Menor de edad");
        }
        if (edad != null && edad >= 60) {
            lista.add("Mayor de 60");
        }
        if (p.isGestante()) {
            lista.add("Gestante");
        }
        if (p.isDiscapacidad()) {
            lista.add("Discapacidad");
        }
        return lista;
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

    // --- listados de pacientes de otros sistemas (los usa el módulo de importación) ---

    /** Pacientes con ese número de documento y cualquier tipo (para detectar un TI que ya es CC, p. ej.). */
    @Transactional(readOnly = true)
    public java.util.List<Paciente> porNumeroDocumento(String numeroDocumento) {
        return repository.findByNumeroDocumento(numeroDocumento);
    }

    /** Lo que el listado cambiaría y lo que no va a cambiar pero difiere (para la revisión). */
    @Transactional(readOnly = true)
    public Comparacion compararConListado(UUID id, DatosPersona datos) {
        Paciente p = get(id);
        return new Comparacion(p.cambiosDesde(datos), p.diferenciasCon(datos));
    }

    public record Comparacion(java.util.List<DatosPersona.Cambio> cambios, java.util.List<String> diferencias) {
    }

    /** Aplica el listado sobre un paciente existente y devuelve lo que cambió (para poder deshacerlo). */
    @Transactional
    public java.util.List<DatosPersona.Cambio> completarDesdeListado(UUID id, DatosPersona datos) {
        Paciente p = get(id);
        java.util.List<DatosPersona.Cambio> cambios = p.cambiosDesde(datos);
        if (!cambios.isEmpty()) {
            p.aplicar(cambios);
        }
        return cambios;
    }

    @Transactional
    public Paciente crearDesdeListado(String tipoDocumento, String numeroDocumento, DatosPersona datos, UUID loteId) {
        return repository.save(Paciente.desdeListado(tipoDocumento, numeroDocumento, datos, loteId));
    }

    @Transactional
    public void revertirCambios(UUID id, java.util.List<DatosPersona.Cambio> cambios) {
        repository.findById(id).ifPresent(p -> p.revertir(cambios));
    }

    /** Al deshacer una importación (quien llama verifica antes que el paciente no tenga otra actividad). */
    @Transactional
    public void eliminarImportado(UUID id, UUID loteImportacionId) {
        repository.findById(id)
                .filter(p -> loteImportacionId.equals(p.getLoteImportacionId()))
                .ifPresent(repository::delete);
    }

    @Transactional(readOnly = true)
    public Page<Paciente> search(String q, boolean soloIncompletos, Pageable pageable) {
        return repository.search(q == null || q.isBlank() ? null : q.trim(), soloIncompletos, pageable);
    }

    @Transactional(readOnly = true)
    public long conDatosIncompletos() {
        return repository.countByDatosIncompletosTrue();
    }

    /**
     * @param datosClinicos si es {@code false} (p. ej. un secretario) se ignoran alergias y antecedentes:
     *                      ni se guardan ni se borran los existentes
     */
    @Transactional
    public Paciente create(PacienteRequest r, boolean datosClinicos) {
        // Solo al crear: el documento no se edita, y una ficha antigua con otro formato no debe quedar sin poder editarse
        String errorNumero = TiposDocumento.errorNumero(r.tipoDocumento(), r.numeroDocumento());
        if (errorNumero != null) {
            throw ApiProblem.badRequest(errorNumero);
        }
        int edad = java.time.Period.between(r.fechaNacimiento(), java.time.LocalDate.now()).getYears();
        if (!TiposDocumento.acordeConEdad(r.tipoDocumento(), edad)) {
            throw ApiProblem.badRequest("El tipo de documento no corresponde a la edad (" + edad + " años): el RIPS usa RC "
                    + "hasta los 7 años, TI de 7 a 17 y CC desde los 18");
        }
        if (repository.existsByTipoDocumentoAndNumeroDocumento(r.tipoDocumento(), r.numeroDocumento().strip())) {
            throw ApiProblem.conflict("Ya existe un paciente con ese documento");
        }
        if (!Boolean.TRUE.equals(r.autorizaDatos())) {
            throw ApiProblem.badRequest("Registre la autorización de tratamiento de datos personales del paciente (Ley 1581 de 2012)");
        }
        topes.paciente();
        Paciente paciente = new Paciente(r.tipoDocumento(), r.numeroDocumento().strip());
        apply(paciente, r, datosClinicos);
        return repository.save(paciente);
    }

    @Transactional
    public Paciente update(UUID id, PacienteRequest r, boolean datosClinicos) {
        Paciente paciente = get(id);
        apply(paciente, r, datosClinicos);
        return paciente;
    }

    /**
     * A quién escribirle sobre sus citas: el correo y el nombre, si tiene correo y no pidió dejar de recibirlos.
     * Del consultorio actual (el filtro por tenant no trae pacientes de otro).
     */
    @Transactional(readOnly = true)
    public Optional<DestinatarioCitas> destinatarioCitas(UUID pacienteId) {
        return repository.findById(pacienteId)
                .filter(p -> p.getEmail() != null && !p.getEmail().isBlank() && !p.isSinCorreosCitas())
                .map(p -> new DestinatarioCitas(p.getEmail().strip(), p.isDatosIncompletos() ? null : p.getNombres()));
    }

    /** @param nombres null si el paciente se importó sin nombre */
    public record DestinatarioCitas(String email, String nombres) {
    }

    private static void apply(Paciente p, PacienteRequest r, boolean datosClinicos) {
        p.actualizarDemograficos(r.nombres(), r.apellidos(), r.fechaNacimiento(), r.sexo(), Telefonos.normalizar(r.telefono()),
                r.emailAGuardar());
        // Se guarda aunque ya sea mayor: queda como contacto. Un menor no se puede guardar sin él (PacienteRequest).
        p.actualizarResponsable(texto(r.responsableNombre()), texto(r.responsableParentesco()),
                texto(r.responsableTipoDocumento()), texto(r.responsableNumeroDocumento()),
                r.responsableTelefono() == null || r.responsableTelefono().isBlank() ? null : Telefonos.normalizar(r.responsableTelefono()));
        if (r.sinCorreosCitas() != null) {
            p.preferirSinCorreosCitas(r.sinCorreosCitas());
        }
        p.actualizarPrioridad(r.gestante() != null ? r.gestante() : p.isGestante(),
                r.discapacidad() != null ? r.discapacidad() : p.isDiscapacidad());
        if (Boolean.TRUE.equals(r.autorizaDatos())) {
            p.registrarAutorizacionDatos(AuthUser.current().id(), r.medioAutorizacion(), TEXTO_AUTORIZACION);
        }
        if (datosClinicos) {
            p.actualizarClinicos(r.alergias(), r.antecedentes());
        }
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
