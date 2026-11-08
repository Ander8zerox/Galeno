package com.galeno.agenda;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.identidad.IdentidadService;
import com.galeno.identidad.IdentidadService.ContactoConsultorio;
import com.galeno.identidad.IdentidadService.MedicoResumen;
import com.galeno.pacientes.PacienteService;
import com.galeno.pacientes.PacienteService.DestinatarioCitas;
import com.galeno.shared.correo.EnvioCorreo;
import com.galeno.shared.tenancy.TenantContext;

/**
 * Correos al paciente sobre su cita: al agendarla, si cambia de hora o se cancela, y un recordatorio 24 horas antes.
 * Solo si el consultorio los tiene activos, el paciente tiene correo y no pidió dejar de recibirlos.
 *
 * <p>Salen después de confirmar la transacción: un fallo del correo nunca impide agendar. Un aviso dentro de las 24
 * horas previas cuenta como recordatorio (no se manda otro).
 */
@Component
class CorreosCitas {

    static final Duration ANTICIPACION = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(CorreosCitas.class);

    private final CitaRepository citas;
    private final IdentidadService identidad;
    private final PacienteService pacientes;
    private final EnvioCorreo envio;
    private final TransactionTemplate nueva;

    CorreosCitas(CitaRepository citas, IdentidadService identidad, PacienteService pacientes, EnvioCorreo envio,
                 PlatformTransactionManager transacciones) {
        this.citas = citas;
        this.identidad = identidad;
        this.pacientes = pacientes;
        this.envio = envio;
        // Después de confirmar la transacción que publicó el evento, escribir exige una transacción propia
        this.nueva = new TransactionTemplate(transacciones);
        this.nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void alCambiar(CambioCita cambio) {
        try {
            enviar(cambio, Instant.now());
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el correo de la cita {} ({})", cambio.citaId(), cambio.tipo(), e);
        }
    }

    /** Cada 15 minutos (configurable para las pruebas). */
    @Scheduled(fixedDelayString = "${app.correos-citas.cada-ms:900000}",
            initialDelayString = "${app.correos-citas.espera-inicial-ms:120000}")
    void programado() {
        int enviados = recordar(Instant.now());
        if (enviados > 0) {
            log.info("Recordatorios de citas enviados: {}", enviados);
        }
    }

    /** Recordatorio de las citas que empiezan en las próximas 24 horas, en cada consultorio que envía correos. */
    int recordar(Instant ahora) {
        List<UUID> consultorios = TenantContext.callAs(TenantContext.SYSTEM, identidad::consultoriosConCorreosPacientes);
        int enviados = 0;
        for (UUID consultorio : consultorios) {
            try {
                enviados += TenantContext.callAs(consultorio, () -> {
                    List<UUID> ids = nueva.execute(t -> citas.porRecordar(ahora, ahora.plus(ANTICIPACION)).stream()
                            .map(Cita::getId).toList());
                    int n = 0;
                    for (UUID id : ids) {
                        if (enviar(new CambioCita(id, CambioCita.Tipo.RECORDATORIO, null), ahora)) {
                            n++;
                        }
                    }
                    return n;
                });
            } catch (RuntimeException e) {
                log.error("Fallaron los recordatorios del consultorio {}", consultorio, e);
            }
        }
        return enviados;
    }

    /** En el consultorio actual. @return si se envió un correo */
    boolean enviar(CambioCita cambio, Instant ahora) {
        ContactoConsultorio consultorio = identidad.contacto(TenantContext.get());
        if (!consultorio.correosPacientes()) {
            return false;
        }
        Boolean enviado = nueva.execute(t -> {
            Cita cita = citas.findById(cambio.citaId()).orElse(null);
            if (cita == null || esAtencionSinCita(cita)) {
                return false;
            }
            boolean cancelada = cambio.tipo() == CambioCita.Tipo.CANCELADA;
            if (!cancelada && (!Cita.Estado.ABIERTOS.contains(cita.getEstado()) || !cita.getInicio().isAfter(ahora))) {
                return false; // ya pasó, se atendió o se canceló después
            }
            if (cambio.tipo() == CambioCita.Tipo.RECORDATORIO && cita.getRecordatorioEnviadoEn() != null) {
                return false;
            }
            // Un aviso dentro de las 24 h previas hace de recordatorio; y sin correo del paciente tampoco se reintenta
            boolean dentroDeLaVentana = !cita.getInicio().isAfter(ahora.plus(ANTICIPACION));
            if (!cancelada && dentroDeLaVentana) {
                cita.marcarRecordatorio(ahora);
            }
            Optional<DestinatarioCitas> paciente = pacientes.destinatarioCitas(cita.getPacienteId());
            if (paciente.isEmpty()) {
                return false;
            }
            String medico = identidad.medicosActivos().stream()
                    .filter(m -> m.membresiaId().equals(cita.getMedicoMembresiaId()))
                    .map(MedicoResumen::nombre).findFirst().orElse(null);
            envio.enviar(TextosCorreoCita.correo(cambio.tipo(), consultorio, paciente.get().email(),
                    paciente.get().nombres(), medico, cita.getInicio(), cambio.inicioAnterior()));
            return true;
        });
        return Boolean.TRUE.equals(enviado);
    }

    /** La cita que se registra al atender a alguien que llegó sin cita: el paciente ya está en el consultorio. */
    private static boolean esAtencionSinCita(Cita c) {
        return c.isSobrecupo() && AgendaPublica.MOTIVO_SIN_CITA.equals(c.getMotivo());
    }
}
