package com.galeno.shared.correo;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.shared.correo.CorreoSuprimido.Motivo;
import com.galeno.shared.tenancy.TenantContext;

/**
 * Lista de direcciones a las que no se escribe más (rebotes permanentes y quejas). La consultan los proveedores de
 * {@link EnvioCorreo} antes de enviar; la alimentan los eventos de SES ({@link EventosSes}).
 */
@Service
public class SupresionCorreos {

    private static final Logger log = LoggerFactory.getLogger(SupresionCorreos.class);

    public record Suprimido(String email, Motivo motivo, String detalle, int eventos, Instant creadoEn, Instant ultimoEn) {
    }

    private final CorreoSuprimidoRepository repo;
    private final TransactionTemplate tx;

    SupresionCorreos(CorreoSuprimidoRepository repo, TransactionTemplate tx) {
        this.repo = repo;
        this.tx = tx;
    }

    static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean suprimido(String email) {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> repo.existsById(normalizar(email)));
    }

    public void registrar(String email, Motivo motivo, String detalle) {
        String e = normalizar(email);
        if (e.isEmpty()) {
            return;
        }
        String d = detalle == null ? null : detalle.substring(0, Math.min(detalle.length(), 500));
        TenantContext.runAs(TenantContext.SYSTEM, () -> tx.executeWithoutResult(t -> repo.findById(e).ifPresentOrElse(
                s -> s.repetir(motivo, d),
                () -> repo.save(new CorreoSuprimido(e, motivo, d)))));
        log.warn("Correo suprimido por {}: {}", motivo, e);
    }

    public List<Suprimido> listar() {
        return TenantContext.callAs(TenantContext.SYSTEM, () -> repo.findAllByOrderByUltimoEnDesc().stream()
                .map(s -> new Suprimido(s.getEmail(), s.getMotivo(), s.getDetalle(), s.getEventos(), s.getCreadoEn(), s.getUltimoEn()))
                .toList());
    }

    /** Volver a escribirle (el usuario corrigió su buzón o pidió que lo quitaran). Devuelve si estaba. */
    public boolean quitar(String email) {
        String e = normalizar(email);
        return TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(t -> {
            if (!repo.existsById(e)) {
                return false;
            }
            repo.deleteById(e);
            return true;
        }));
    }
}
