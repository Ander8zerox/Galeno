package com.galeno.identidad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.identidad.RecuperacionService.RecuperacionPublica;
import com.galeno.shared.web.IpCliente;

/** "Olvidé mi contraseña": endpoints públicos. El token viaja en el cuerpo, nunca en la URL. */
@RestController
@RequestMapping("/api/v1/auth")
public class RecuperacionController {

    private final RecuperacionService recuperacion;
    private final IpCliente ipCliente;

    RecuperacionController(RecuperacionService recuperacion, IpCliente ipCliente) {
        this.recuperacion = recuperacion;
        this.ipCliente = ipCliente;
    }

    public record SolicitudRecuperacion(@NotBlank @Email @Size(max = 190) String email) {
    }

    /** Siempre 202 si el correo es válido, exista o no la cuenta. */
    @PostMapping("/recuperar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void solicitar(@Valid @RequestBody SolicitudRecuperacion r, HttpServletRequest request) {
        recuperacion.solicitar(r.email(), ipCliente.de(request));
    }

    public record TokenRecuperacion(@NotBlank @Size(max = 100) String token) {
    }

    @PostMapping("/restablecer/consultar")
    public RecuperacionPublica consultar(@Valid @RequestBody TokenRecuperacion r) {
        return recuperacion.consultar(r.token());
    }

    public record NuevaContrasena(@NotBlank @Size(max = 100) String token, @NotBlank @Size(max = 200) String password) {
    }

    @PostMapping("/restablecer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecer(@Valid @RequestBody NuevaContrasena r) {
        recuperacion.restablecer(r.token(), r.password());
    }
}
