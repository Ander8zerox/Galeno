package com.galeno.plataforma;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.plataforma.PlataformaService.EventoAuditoria;
import com.galeno.plataforma.PlataformaService.SesionPlataforma;
import com.galeno.shared.correo.SupresionCorreos.Suprimido;
import com.galeno.suscripciones.SuscripcionService.Archivo;
import com.galeno.suscripciones.SuscripcionService.ConsultorioVista;
import com.galeno.suscripciones.SuscripcionService.EstadoVista;
import com.galeno.suscripciones.SuscripcionService.PagoVista;
import com.galeno.suscripciones.SuscripcionService.PlanVista;

/** Consola de la plataforma: login público; todo lo demás exige el token de operador (PLATAFORMA). */
@RestController
@RequestMapping("/api/v1/plataforma")
public class PlataformaController {

    private final PlataformaService plataforma;

    PlataformaController(PlataformaService plataforma) {
        this.plataforma = plataforma;
    }

    public record LoginPlataforma(@NotBlank @Size(max = 190) String email, @NotBlank @Size(max = 200) String password) {
    }

    @PostMapping("/login")
    public SesionPlataforma login(@Valid @RequestBody LoginPlataforma r) {
        return plataforma.login(r.email(), r.password());
    }

    // --- pagos ---

    /** {@code estado}: REPORTADO (por revisar), APROBADO o RECHAZADO; sin él, todos. */
    @GetMapping("/pagos")
    public List<PagoVista> pagos(@RequestParam(required = false) String estado) {
        return plataforma.pagos(estado);
    }

    @GetMapping("/pagos/{id}/comprobante")
    public ResponseEntity<byte[]> comprobante(@PathVariable UUID id) {
        Archivo a = plataforma.comprobante(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(a.nombre()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(a.contenido());
    }

    @PostMapping("/pagos/{id}/aprobar")
    public PagoVista aprobar(@PathVariable UUID id) {
        return plataforma.aprobar(id);
    }

    public record Motivo(@NotBlank @Size(max = 300) String motivo) {
    }

    @PostMapping("/pagos/{id}/rechazar")
    public PagoVista rechazar(@PathVariable UUID id, @Valid @RequestBody Motivo r) {
        return plataforma.rechazar(id, r.motivo());
    }

    // --- consultorios ---

    @GetMapping("/consultorios")
    public List<ConsultorioVista> consultorios() {
        return plataforma.consultorios();
    }

    public record PagoRecibido(@NotBlank String plan, @Positive long monto, @NotNull LocalDate fechaPago,
                               @NotBlank @Size(max = 100) String referencia) {
    }

    /** Un pago que llegó sin que el consultorio lo reportara: queda aprobado de una vez. */
    @PostMapping("/consultorios/{id}/pagos")
    public PagoVista registrarPago(@PathVariable UUID id, @Valid @RequestBody PagoRecibido r) {
        return plataforma.registrarPago(id, r.plan(), r.monto(), r.fechaPago(), r.referencia());
    }

    public record Cortesia(@Min(1) @Max(366) int dias, @NotBlank @Size(max = 300) String motivo) {
    }

    @PostMapping("/consultorios/{id}/cortesia")
    public EstadoVista cortesia(@PathVariable UUID id, @Valid @RequestBody Cortesia r) {
        return plataforma.regalarDias(id, r.dias(), r.motivo());
    }

    @PostMapping("/consultorios/{id}/suspender")
    public EstadoVista suspender(@PathVariable UUID id, @Valid @RequestBody Motivo r) {
        return plataforma.suspender(id, r.motivo());
    }

    @PostMapping("/consultorios/{id}/reactivar")
    public EstadoVista reactivar(@PathVariable UUID id) {
        return plataforma.reactivar(id);
    }

    // --- configuración ---

    @GetMapping("/planes")
    public List<PlanVista> planes() {
        return plataforma.planes();
    }

    public record CambioPlan(@NotBlank @Size(max = 60) String nombre, @PositiveOrZero long precio, boolean activo) {
    }

    @PutMapping("/planes/{codigo}")
    public PlanVista actualizarPlan(@PathVariable String codigo, @Valid @RequestBody CambioPlan r) {
        return plataforma.actualizarPlan(codigo, r.nombre(), r.precio(), r.activo());
    }

    public record DatosPago(@NotBlank @Size(max = 2000) String texto) {
    }

    @GetMapping("/datos-pago")
    public DatosPago datosPago() {
        return new DatosPago(plataforma.datosPago());
    }

    @PutMapping("/datos-pago")
    public DatosPago cambiarDatosPago(@Valid @RequestBody DatosPago r) {
        return new DatosPago(plataforma.cambiarDatosPago(r.texto()));
    }

    @GetMapping("/auditoria")
    public List<EventoAuditoria> auditoria() {
        return plataforma.auditoria();
    }

    // --- correos que rebotaron o se quejaron ---

    @GetMapping("/correos-suprimidos")
    public List<Suprimido> correosSuprimidos() {
        return plataforma.correosSuprimidos();
    }

    public record DireccionCorreo(@NotBlank @Size(max = 190) String email) {
    }

    /** Volver a enviarle a una dirección (el usuario corrigió su buzón). El correo va en el cuerpo, no en la URL. */
    @PostMapping("/correos-suprimidos/quitar")
    public ResponseEntity<Void> quitarSuprimido(@Valid @RequestBody DireccionCorreo r) {
        plataforma.quitarSuprimido(r.email());
        return ResponseEntity.noContent().build();
    }
}
