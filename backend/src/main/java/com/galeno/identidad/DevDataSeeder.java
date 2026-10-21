package com.galeno.identidad;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.galeno.shared.tenancy.TenantContext;

/**
 * Solo en el perfil {@code local}. Escenario de prueba:
 * <ul>
 *   <li>Consultorio Demo: Dra. Demo (ADMIN + MEDICO), Dr. Gómez (MEDICO) y Laura (SECRETARIO, atiende a la Dra. Demo).</li>
 *   <li>Clínica Sur Demo: Dr. Gómez (ADMIN + MEDICO) y Dra. Demo (MEDICO): la Dra. Demo tiene que elegir consultorio.</li>
 * </ul>
 * Todos usan la misma contraseña de prueba ({@code app.seed.password}).
 */
@Component
@Profile("local")
@org.springframework.core.annotation.Order(0) // antes de marcar los operadores de la plataforma
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    static final String SECRETARIO = "secretario@demo.local";
    /** Operador de la plataforma (app.plataforma.operadores en application-local.yml). */
    static final String PLATAFORMA = "plataforma@demo.local";
    static final String MEDICO_2 = "medico2@demo.local";

    private final ConsultorioRepository consultorios;
    private final UsuarioRepository usuarios;
    private final PerfilMedicoRepository perfiles;
    private final MembresiaRepository membresias;
    private final AsignacionSecretarioRepository asignaciones;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final String email;
    private final String password;

    DevDataSeeder(ConsultorioRepository consultorios, UsuarioRepository usuarios, PerfilMedicoRepository perfiles,
                  MembresiaRepository membresias, AsignacionSecretarioRepository asignaciones,
                  PasswordEncoder passwordEncoder, TransactionTemplate tx,
                  @Value("${app.seed.email}") String email, @Value("${app.seed.password}") String password) {
        this.consultorios = consultorios;
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.membresias = membresias;
        this.asignaciones = asignaciones;
        this.passwordEncoder = passwordEncoder;
        this.tx = tx;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantContext.runAs(TenantContext.SYSTEM, () -> tx.executeWithoutResult(s -> usuario(PLATAFORMA, "Operador Galeno")));
        if (usuarios.existsByEmail(SECRETARIO)) {
            return;
        }
        // 1. Personas y consultorios (no pertenecen a ningún tenant)
        Ids ids = TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(s -> {
            Usuario dra = usuario(email, "Dra. Demo");
            Usuario gomez = usuario(MEDICO_2, "Dr. Gómez");
            Usuario laura = usuario(SECRETARIO, "Laura Secretaria");
            perfil(dra, "RM-00000", "Medicina general");
            perfil(gomez, "RM-00001", "Medicina interna");
            // Si la Dra. Demo ya existía (migración V2), su consultorio es el Demo
            UUID demo = membresias.findByUsuarioId(dra.getId()).stream().findFirst().map(Membresia::getTenantId)
                    .orElseGet(() -> consultorios.save(new Consultorio("Consultorio Demo", "900000000-1")).getId());
            UUID sur = consultorios.save(new Consultorio("Clínica Sur Demo", "900000000-2")).getId();
            return new Ids(demo, sur, dra.getId(), gomez.getId(), laura.getId());
        }));

        // 2. Membresías, cada una dentro de su consultorio
        TenantContext.runAs(ids.demo(), () -> tx.executeWithoutResult(s -> {
            UUID draDemo = membresia(ids.dra(), Set.of(Rol.ADMIN, Rol.MEDICO));
            membresia(ids.gomez(), Set.of(Rol.MEDICO));
            UUID laura = membresia(ids.laura(), Set.of(Rol.SECRETARIO));
            asignaciones.save(new AsignacionSecretario(laura, draDemo));
        }));
        TenantContext.runAs(ids.sur(), () -> tx.executeWithoutResult(s -> {
            membresia(ids.gomez(), Set.of(Rol.ADMIN, Rol.MEDICO));
            membresia(ids.dra(), Set.of(Rol.MEDICO));
        }));
        log.info("Datos de prueba: {}, {} y {} (contraseña en application-local.yml)", email, MEDICO_2, SECRETARIO);
    }

    private Usuario usuario(String correo, String nombre) {
        return usuarios.findByEmail(correo)
                .orElseGet(() -> usuarios.save(new Usuario(correo, passwordEncoder.encode(password), nombre)));
    }

    private void perfil(Usuario u, String registro, String especialidad) {
        if (!perfiles.existsById(u.getId())) {
            perfiles.save(new PerfilMedico(u.getId(), registro, especialidad));
        }
    }

    private UUID membresia(UUID usuarioId, Set<Rol> roles) {
        return membresias.findByUsuarioIdAndTenantId(usuarioId, TenantContext.get())
                .orElseGet(() -> membresias.save(new Membresia(usuarioId, List.copyOf(roles))))
                .getId();
    }

    private record Ids(UUID demo, UUID sur, UUID dra, UUID gomez, UUID laura) {
    }
}
