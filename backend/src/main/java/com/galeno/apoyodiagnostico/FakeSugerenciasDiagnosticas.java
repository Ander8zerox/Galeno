package com.galeno.apoyodiagnostico;

import java.util.List;

import org.springframework.stereotype.Component;

import com.galeno.apoyodiagnostico.SugerenciasDiagnosticas.Diferencial;
import com.galeno.apoyodiagnostico.SugerenciasDiagnosticas.Probabilidad;
import com.galeno.shared.ai.LlmClient.LlmRequest;
import com.galeno.shared.ai.fake.FakeLlmResponse;

/** Respuesta de ejemplo para desarrollar sin Bedrock ({@code app.ai.provider=fake}). */
@Component
class FakeSugerenciasDiagnosticas implements FakeLlmResponse<SugerenciasDiagnosticas> {

    @Override
    public Class<SugerenciasDiagnosticas> type() {
        return SugerenciasDiagnosticas.class;
    }

    @Override
    public SugerenciasDiagnosticas respond(LlmRequest request) {
        return new SugerenciasDiagnosticas(
                List.of(new Diferencial("J02.9", "Faringitis aguda, no especificada", Probabilidad.ALTA,
                                "[SIMULADO] Odinofagia y fiebre de corta evolución."),
                        new Diferencial("J03.0", "Amigdalitis estreptocócica", Probabilidad.MEDIA,
                                "[SIMULADO] Considerar si hay exudado amigdalino y adenopatías.")),
                List.of("Presencia de exudado amigdalino", "Prueba rápida de estreptococo"),
                List.of("Dificultad respiratoria o para deglutir saliva"));
    }
}
