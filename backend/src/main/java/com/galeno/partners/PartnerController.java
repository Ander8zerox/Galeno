package com.galeno.partners;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.partners.PartnerService.CodigoPublico;
import com.galeno.partners.PartnerService.InscripcionPublica;
import com.galeno.partners.PartnerService.Panel;
import com.galeno.partners.PartnerService.PartnerVista;
import com.galeno.partners.PartnerService.SesionPartner;

/** Portal de partners. Públicos: inscripción, login y consulta de códigos; lo demás exige el token PARTNER. */
@RestController
@RequestMapping("/api/v1/partners")
public class PartnerController {

    private final PartnerService partners;

    PartnerController(PartnerService partners) {
        this.partners = partners;
    }

    /** Para el registro de consultorios: a quién corresponde el código y qué descuento da. */
    @GetMapping("/codigos/{codigo}")
    public CodigoPublico codigo(@PathVariable String codigo) {
        return partners.consultarCodigo(codigo);
    }

    public record InscripcionPartnerSolicitud(@NotBlank @Email @Size(max = 190) String email, @NotBlank @Size(max = 200) String password,
                              @NotBlank @Size(max = 200) String nombreCuenta, @NotNull Partner.TipoPersona tipoPersona,
                              @NotBlank @Size(max = 200) String nombre, @NotBlank @Size(max = 5) String tipoDocumento,
                              @NotBlank @Size(max = 30) String numeroDocumento, @NotBlank @Size(max = 30) String telefono,
                              @Size(max = 80) String banco, @Size(max = 20) String tipoCuenta,
                              @Size(max = 40) String numeroCuenta, boolean aceptaTerminos) {
    }

    /** Siempre 202 si los datos son válidos: llega un enlace al correo para confirmar. */
    @PostMapping("/inscripcion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void inscribir(@Valid @RequestBody InscripcionPartnerSolicitud r, HttpServletRequest request) {
        partners.inscribir(new PartnerService.SolicitudInscripcion(r.email(), r.password(),
                new DatosPartner(r.nombreCuenta(), r.tipoPersona(), r.nombre(), r.tipoDocumento(), r.numeroDocumento(),
                        r.telefono(), r.banco(), r.tipoCuenta(), r.numeroCuenta()), r.aceptaTerminos()), request.getRemoteAddr());
    }

    public record TokenInscripcion(@NotBlank @Size(max = 100) String token) {
    }

    @PostMapping("/inscripcion/consultar")
    public InscripcionPublica consultar(@Valid @RequestBody TokenInscripcion r) {
        return partners.consultarInscripcion(r.token());
    }

    /** {@code password} solo si el correo ya tenía cuenta en Galeno (su contraseña actual). */
    public record ConfirmacionInscripcion(@NotBlank @Size(max = 100) String token, @Size(max = 200) String password) {
    }

    @PostMapping("/inscripcion/confirmar")
    public SesionPartner confirmar(@Valid @RequestBody ConfirmacionInscripcion r) {
        return partners.confirmarInscripcion(r.token(), r.password());
    }

    public record LoginPartner(@NotBlank @Size(max = 190) String email, @NotBlank @Size(max = 200) String password) {
    }

    @PostMapping("/login")
    public SesionPartner login(@Valid @RequestBody LoginPartner r) {
        return partners.login(r.email(), r.password());
    }

    @GetMapping("/yo")
    public Panel panel() {
        return partners.miPanel();
    }

    public record DatosPagoPartner(@Size(max = 80) String banco, @Size(max = 20) String tipoCuenta,
                            @Size(max = 40) String numeroCuenta, @NotBlank @Size(max = 30) String telefono) {
    }

    @PutMapping("/yo/datos-pago")
    public PartnerVista datosPago(@Valid @RequestBody DatosPagoPartner r) {
        return partners.actualizarDatosPago(r.banco(), r.tipoCuenta(), r.numeroCuenta(), r.telefono());
    }
}
