package com.galeno.partners;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.partners.PartnerService.ComisionVista;
import com.galeno.partners.PartnerService.LiquidacionVista;
import com.galeno.partners.PartnerService.Panel;
import com.galeno.partners.PartnerService.PartnerVista;
import com.galeno.partners.PartnerService.ReferidoVista;

/** Partners en la consola de la plataforma (token PLATAFORMA; cada acción queda en la auditoría). */
@RestController
@RequestMapping("/api/v1/plataforma")
public class PartnersPlataformaController {

    private final PartnerService partners;

    PartnersPlataformaController(PartnerService partners) {
        this.partners = partners;
    }

    @GetMapping("/partners")
    public List<PartnerVista> partners() {
        return partners.partners();
    }

    @GetMapping("/partners/{id}")
    public Panel detalle(@PathVariable UUID id) {
        return partners.detalle(id);
    }

    /** Aprueba una inscripción (asigna el código) o reactiva un partner suspendido. */
    @PostMapping("/partners/{id}/aprobar")
    public PartnerVista aprobar(@PathVariable UUID id) {
        return partners.aprobar(id);
    }

    public record MotivoPartner(@NotBlank @Size(max = 300) String motivo) {
    }

    @PostMapping("/partners/{id}/rechazar")
    public PartnerVista rechazar(@PathVariable UUID id, @Valid @RequestBody MotivoPartner r) {
        return partners.rechazar(id, r.motivo());
    }

    @PostMapping("/partners/{id}/suspender")
    public PartnerVista suspender(@PathVariable UUID id, @Valid @RequestBody MotivoPartner r) {
        return partners.suspender(id, r.motivo());
    }

    public record PagoPartner(@NotBlank @Size(max = 100) String referencia, @NotNull LocalDate fechaPago) {
    }

    /** Liquida todas las comisiones disponibles del partner (deben sumar al menos el mínimo). */
    @PostMapping("/partners/{id}/liquidaciones")
    public LiquidacionVista liquidar(@PathVariable UUID id, @Valid @RequestBody PagoPartner r) {
        return partners.liquidar(id, r.referencia(), r.fechaPago());
    }

    @PostMapping("/comisiones/{id}/anular")
    public ComisionVista anular(@PathVariable UUID id, @Valid @RequestBody MotivoPartner r) {
        return partners.anularComision(id, r.motivo());
    }

    public record AsignacionPartner(@NotBlank @Size(max = 20) String codigo) {
    }

    /** Atribuye un consultorio a un partner (o lo reasigna). */
    @PutMapping("/consultorios/{id}/partner")
    public ReferidoVista asignar(@PathVariable UUID id, @Valid @RequestBody AsignacionPartner r) {
        return partners.asignarReferido(id, r.codigo());
    }
}
