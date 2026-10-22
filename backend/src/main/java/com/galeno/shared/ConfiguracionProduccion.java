package com.galeno.shared;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Con el perfil prod, la API no arranca si falta configuración: un ${VAR} sin resolver en el yml no detiene a Spring
 * (queda como texto literal), y los valores por defecto de application.yml son los de desarrollo. Se revisa antes de
 * crear cualquier bean, con un mensaje que dice exactamente qué variable falta.
 */
public class ConfiguracionProduccion implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** Propiedad y la variable de entorno que la pone (Terraform / SSM Parameter Store). */
    private static final List<String[]> OBLIGATORIAS = List.of(
            new String[] {"spring.datasource.url", "SPRING_DATASOURCE_URL"},
            new String[] {"spring.datasource.username", "SPRING_DATASOURCE_USERNAME"},
            new String[] {"spring.datasource.password", "SPRING_DATASOURCE_PASSWORD"},
            new String[] {"app.security.jwt.secret", "APP_SECURITY_JWT_SECRET"},
            new String[] {"app.web.base-url", "APP_WEB_BASE_URL"},
            new String[] {"app.web.origen-secreto", "APP_WEB_ORIGEN_SECRETO"},
            new String[] {"app.documentos.bucket", "APP_DOCUMENTOS_BUCKET"},
            new String[] {"app.correo.remitente", "APP_CORREO_REMITENTE"},
            new String[] {"app.correo.configuration-set", "APP_CORREO_CONFIGURATION_SET"},
            new String[] {"app.correo.cola-eventos", "APP_CORREO_COLA_EVENTOS"});

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        if (!env.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> problemas = problemas(env);
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Configuración de producción incompleta: " + String.join("; ", problemas));
        }
    }

    static List<String> problemas(Environment env) {
        List<String> problemas = new ArrayList<>();
        for (String[] obligatoria : OBLIGATORIAS) {
            String valor = valor(env, obligatoria[0]);
            if (valor.isBlank() || valor.contains("${")) {
                problemas.add("falta " + obligatoria[1]);
            }
        }
        // Estas dos tienen valor de desarrollo en application.yml, así que "no vacía" no basta
        if (valor(env, "spring.datasource.url").contains("localhost")) {
            problemas.add("SPRING_DATASOURCE_URL apunta a localhost");
        }
        String baseUrl = valor(env, "app.web.base-url");
        if (!baseUrl.isBlank() && !baseUrl.startsWith("https://")) {
            problemas.add("APP_WEB_BASE_URL debe empezar por https://");
        }
        return problemas;
    }

    /** Un placeholder sin resolver puede quedar literal o lanzar excepción, según el entorno: ambos cuentan como vacío. */
    private static String valor(Environment env, String propiedad) {
        try {
            return env.getProperty(propiedad, "");
        } catch (IllegalArgumentException sinResolver) {
            return "";
        }
    }
}
