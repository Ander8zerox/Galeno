package com.galeno.rips;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.rips.DatosRipsService.PacienteRips;
import com.galeno.rips.DatosRipsService.Prestador;
import com.galeno.rips.DatosRipsService.Profesional;
import com.galeno.rips.ReferenciasService.ReferenciaVista;

/**
 * Datos del RIPS. Tablas de referencia: cualquier miembro. Paciente: quien edita pacientes (también la secretaria).
 * Prestador y profesionales: RIPS_GESTIONAR. Todo exige el módulo de RIPS activo (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/rips")
public class DatosRipsController {

    private final DatosRipsService datos;
    private final ReferenciasService referencias;

    DatosRipsController(DatosRipsService datos, ReferenciasService referencias) {
        this.datos = datos;
        this.referencias = referencias;
    }

    /** Municipios, países y servicios se buscan con {@code q}; las demás tablas vienen completas. */
    @GetMapping("/referencias/{tabla}")
    public List<ReferenciaVista> referencias(@PathVariable String tabla, @RequestParam(required = false) String q) {
        return referencias.listar(tabla, q);
    }

    /** Datos que faltan de varios pacientes (los de las citas del día); a lo sumo 200 por consulta. */
    @GetMapping("/pacientes/faltantes")
    public List<Faltantes> faltantes(@RequestParam @Size(max = 200) List<UUID> ids) {
        return datos.faltantes(ids).entrySet().stream().map(e -> new Faltantes(e.getKey(), e.getValue())).toList();
    }

    public record Faltantes(UUID pacienteId, List<String> faltantes) {
    }

    @GetMapping("/pacientes/{pacienteId}")
    public PacienteRips paciente(@PathVariable UUID pacienteId) {
        return datos.paciente(pacienteId);
    }

    @PutMapping("/pacientes/{pacienteId}")
    public PacienteRips guardarPaciente(@PathVariable UUID pacienteId, @Valid @RequestBody DatosPaciente r) {
        return datos.guardarPaciente(pacienteId, new PacienteRips(r.tipoUsuario(), r.paisResidencia(),
                r.municipioResidencia(), null, null, r.zonaResidencia(), r.paisOrigen(), List.of()));
    }

    @GetMapping("/prestador")
    public Prestador prestador() {
        return datos.prestador();
    }

    @PutMapping("/prestador")
    public Prestador guardarPrestador(@Valid @RequestBody DatosPrestador r) {
        return datos.guardarPrestador(new Prestador(r.codigoHabilitacion(), r.servicio(), r.modalidadAtencion(),
                r.grupoServicios(), r.finalidad(), r.causaExterna(), r.viaIngreso(), true));
    }

    @GetMapping("/profesionales")
    public List<Profesional> profesionales() {
        return datos.profesionales();
    }

    @PutMapping("/profesionales/{usuarioId}")
    public Profesional guardarProfesional(@PathVariable UUID usuarioId, @Valid @RequestBody DocumentoProfesional r) {
        return datos.guardarProfesional(usuarioId, r.tipoDocumento(), r.numeroDocumento());
    }

    public record DatosPaciente(@Size(max = 2) String tipoUsuario, @Size(max = 3) String paisResidencia,
                                @Size(max = 5) String municipioResidencia, @Size(max = 2) String zonaResidencia,
                                @Size(max = 3) String paisOrigen) {
    }

    public record DatosPrestador(@Size(max = 12) String codigoHabilitacion, @Size(max = 4) String servicio,
                                 @Size(max = 2) String modalidadAtencion, @Size(max = 2) String grupoServicios,
                                 @Size(max = 2) String finalidad, @Size(max = 2) String causaExterna,
                                 @Size(max = 2) String viaIngreso) {
    }

    public record DocumentoProfesional(@NotBlank @Size(max = 2) String tipoDocumento,
                                       @NotBlank @Size(max = 20) String numeroDocumento) {
    }
}
