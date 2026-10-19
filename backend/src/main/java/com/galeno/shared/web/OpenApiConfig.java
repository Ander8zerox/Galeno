package com.galeno.shared.web;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.galeno.apoyodiagnostico.SugerenciasDiagnosticas;
import com.galeno.formulacion.BorradorFormula;

/**
 * El contrato se genera desde el código y se exporta a {@code contracts/openapi.json};
 * el front genera sus tipos a partir de ese archivo.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Galeno API").version("v1"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }

    /** Publica los esquemas de las respuestas de IA, que viajan en {@code AiJobResponse.resultado}. */
    @Bean
    OpenApiCustomizer aiResultSchemas() {
        return openApi -> {
            var converters = ModelConverters.getInstance();
            converters.readAll(BorradorFormula.class).forEach(openApi.getComponents()::addSchemas);
            converters.readAll(SugerenciasDiagnosticas.class).forEach(openApi.getComponents()::addSchemas);
        };
    }
}
