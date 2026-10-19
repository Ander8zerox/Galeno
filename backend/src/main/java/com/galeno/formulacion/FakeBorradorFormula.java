package com.galeno.formulacion;

import java.util.List;

import org.springframework.stereotype.Component;

import com.galeno.shared.ai.LlmClient.LlmRequest;
import com.galeno.shared.ai.fake.FakeLlmResponse;

/** Respuesta de ejemplo para desarrollar sin Bedrock ({@code app.ai.provider=fake}). */
@Component
class FakeBorradorFormula implements FakeLlmResponse<BorradorFormula> {

    @Override
    public Class<BorradorFormula> type() {
        return BorradorFormula.class;
    }

    @Override
    public BorradorFormula respond(LlmRequest request) {
        return new BorradorFormula(
                List.of(new BorradorFormula.ItemPropuesto("Amoxicilina", "500 mg", "cápsula", "oral", "1 cápsula",
                        "cada 8 horas", "7 días", "21 (veintiún) cápsulas", "Tomar con abundante agua.")),
                List.of("[SIMULADO] Respuesta de prueba: active app.ai.provider=bedrock para usar Claude."));
    }
}
