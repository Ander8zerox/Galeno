package com.galeno.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ConfiguracionProduccionTest {

    private static MockEnvironment completa() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://galeno.abc.us-east-2.rds.amazonaws.com:3306/galeno")
                .withProperty("spring.datasource.username", "galeno")
                .withProperty("spring.datasource.password", "x")
                .withProperty("app.security.jwt.secret", "x")
                .withProperty("app.web.base-url", "https://galeno.com.co")
                .withProperty("app.web.origen-secreto", "x")
                .withProperty("app.documentos.bucket", "galeno-docs")
                .withProperty("app.correo.remitente", "Galeno <no-responder@galeno.com.co>")
                .withProperty("app.correo.configuration-set", "galeno")
                .withProperty("app.correo.cola-eventos", "https://sqs.us-east-2.amazonaws.com/1/galeno");
    }

    @Test
    void conTodoConfiguradoNoHayProblemas() {
        assertThat(ConfiguracionProduccion.problemas(completa())).isEmpty();
    }

    @Test
    void diceQueVariableFaltaIncluidoUnPlaceholderSinResolver() {
        MockEnvironment env = completa()
                .withProperty("app.web.origen-secreto", "")
                .withProperty("app.documentos.bucket", "${APP_DOCUMENTOS_BUCKET}");

        assertThat(ConfiguracionProduccion.problemas(env))
                .containsExactly("falta APP_WEB_ORIGEN_SECRETO", "falta APP_DOCUMENTOS_BUCKET");
    }

    @Test
    void rechazaLosValoresDeDesarrollo() {
        MockEnvironment env = completa()
                .withProperty("spring.datasource.url", "jdbc:mysql://localhost:3317/galeno")
                .withProperty("app.web.base-url", "http://localhost:5180");

        assertThat(ConfiguracionProduccion.problemas(env))
                .containsExactly("SPRING_DATASOURCE_URL apunta a localhost", "APP_WEB_BASE_URL debe empezar por https://");
    }
}
