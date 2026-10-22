package com.galeno.partners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.identidad.IdentidadService;
import com.galeno.shared.tenancy.TenantContext;

/**
 * Solo en desarrollo: un partner activo para probar el portal ({@code /partner}) y el registro con código
 * ({@code /registro?ref=DEMO100}). Usa la misma contraseña de prueba que las demás cuentas demo.
 */
@Component
@Profile("local")
@Order(20)
class DevPartnerSeeder implements ApplicationRunner {

    static final String EMAIL = "partner@demo.local";
    static final String CODIGO = "DEMO100";
    private static final Logger log = LoggerFactory.getLogger(DevPartnerSeeder.class);

    private final PartnerRepository partners;
    private final IdentidadService identidad;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final String password;

    DevPartnerSeeder(PartnerRepository partners, IdentidadService identidad, PasswordEncoder passwordEncoder,
                     TransactionTemplate tx, @Value("${app.seed.password}") String password) {
        this.partners = partners;
        this.identidad = identidad;
        this.passwordEncoder = passwordEncoder;
        this.tx = tx;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (identidad.existeCuenta(EMAIL)) {
            return;
        }
        var usuarioId = identidad.crearCuenta(EMAIL, "Partner Demo", passwordEncoder.encode(password));
        TenantContext.runAs(TenantContext.SYSTEM, () -> tx.executeWithoutResult(t -> {
            Partner p = new Partner(usuarioId, new DatosPartner("Partner Demo", Partner.TipoPersona.NATURAL, "Partner Demo",
                    "CC", "1000000001", "3000000000", "Banco Demo", "AHORROS", "000-000000-00"), PartnerService.TERMINOS_VERSION);
            p.aprobar(CODIGO);
            partners.save(p);
        }));
        log.info("Partner de prueba: {} (código {}, contraseña en application-local.yml)", EMAIL, CODIGO);
    }
}
