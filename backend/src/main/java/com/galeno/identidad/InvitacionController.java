package com.galeno.identidad;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.identidad.InvitacionService.InvitacionPublica;
import com.galeno.identidad.SesionService.SesionResponse;

/** Endpoints públicos: quien recibe el enlace aún no tiene sesión. */
@RestController
@RequestMapping("/api/v1/invitaciones")
public class InvitacionController {

    private final InvitacionService invitaciones;

    InvitacionController(InvitacionService invitaciones) {
        this.invitaciones = invitaciones;
    }

    @PostMapping("/consultar")
    public InvitacionPublica consultar(@Valid @RequestBody ConsultarInvitacion request) {
        return invitaciones.consultar(request.token());
    }

    @PostMapping("/aceptar")
    public SesionResponse aceptar(@Valid @RequestBody AceptarInvitacion r) {
        return invitaciones.aceptar(r.token(), r.nombre(), r.password(), r.registroMedico(), r.especialidad());
    }

    public record ConsultarInvitacion(@NotBlank @Size(max = 100) String token) {
    }

    /** {@code nombre} solo se usa si se crea una cuenta nueva. */
    public record AceptarInvitacion(@NotBlank @Size(max = 100) String token, @Size(max = 200) String nombre,
                                    @NotBlank @Size(max = 200) String password, @Size(max = 50) String registroMedico,
                                    @Size(max = 120) String especialidad) {
    }
}
