package com.galeno.identidad;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.identidad.EquipoService.InvitacionCreada;
import com.galeno.identidad.EquipoService.InvitacionResumen;
import com.galeno.identidad.EquipoService.Miembro;

@RestController
@RequestMapping("/api/v1/equipo")
public class EquipoController {

    private final EquipoService equipo;
    private final IdentidadService identidad;

    EquipoController(EquipoService equipo, IdentidadService identidad) {
        this.equipo = equipo;
        this.identidad = identidad;
    }

    /** Datos de contacto del consultorio que ven los pacientes en los correos de sus citas. */
    @GetMapping("/consultorio")
    public DatosConsultorio consultorio() {
        return DatosConsultorio.de(identidad.contacto(com.galeno.shared.security.AuthUser.current().tenantId()));
    }

    @PutMapping("/consultorio")
    public DatosConsultorio actualizarConsultorio(@Valid @RequestBody ActualizarConsultorio r) {
        return DatosConsultorio.de(identidad.actualizarContacto(com.galeno.shared.security.AuthUser.current().tenantId(),
                r.direccion(), r.telefono(), r.correo(), r.correosPacientes()));
    }

    /** @param correosPacientes enviar correos a los pacientes sobre sus citas (agendada, cambios y recordatorio) */
    public record DatosConsultorio(String nombre, String direccion, String telefono, String correo,
                                   boolean correosPacientes) {

        static DatosConsultorio de(IdentidadService.ContactoConsultorio c) {
            return new DatosConsultorio(c.nombre(), c.direccion(), c.telefono(), c.correo(), c.correosPacientes());
        }
    }

    public record ActualizarConsultorio(@jakarta.validation.constraints.Size(max = 200) String direccion,
                                        @jakarta.validation.constraints.Size(max = 40) String telefono,
                                        @jakarta.validation.constraints.Size(max = 190) String correo,
                                        boolean correosPacientes) {
    }

    @GetMapping
    public List<Miembro> listar() {
        return equipo.listar();
    }

    @PutMapping("/{membresiaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void actualizar(@PathVariable UUID membresiaId, @Valid @RequestBody ActualizarMiembro request) {
        equipo.actualizar(membresiaId, request.roles(), request.estado(), request.registroMedico());
    }

    /** Reemplaza los médicos que atiende un secretario. */
    @PutMapping("/{membresiaId}/medicos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void asignarMedicos(@PathVariable UUID membresiaId, @Valid @RequestBody AsignarMedicos request) {
        equipo.asignarMedicos(membresiaId, request.medicos());
    }

    /** Envía la invitación por correo y devuelve también el enlace, por si el administrador prefiere compartirlo. */
    @PostMapping("/invitaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitacionCreada invitar(@Valid @RequestBody Invitar request) {
        return equipo.invitar(request.email(), request.roles(), request.registroMedico());
    }

    /** Crea un enlace nuevo con los mismos datos, lo envía y anula el anterior. */
    @PostMapping("/invitaciones/{id}/reenviar")
    public InvitacionCreada reenviar(@PathVariable UUID id) {
        return equipo.reenviar(id);
    }

    @GetMapping("/invitaciones")
    public List<InvitacionResumen> invitaciones() {
        return equipo.pendientes();
    }

    @DeleteMapping("/invitaciones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocar(@PathVariable UUID id) {
        equipo.revocar(id);
    }

    public record ActualizarMiembro(@NotEmpty Set<Rol> roles, @NotNull Membresia.Estado estado,
                                    @Size(max = 50) String registroMedico) {
    }

    public record AsignarMedicos(@NotNull List<UUID> medicos) {
    }

    public record Invitar(@NotBlank @Email @Size(max = 190) String email, @NotEmpty Set<Rol> roles,
                          @Size(max = 50) String registroMedico) {
    }
}
