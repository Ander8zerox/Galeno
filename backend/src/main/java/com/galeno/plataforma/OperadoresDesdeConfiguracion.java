package com.galeno.plataforma;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Al arrancar, marca como operadores las cuentas de {@code app.plataforma.operadores} (después del seed local). */
@Component
@org.springframework.core.annotation.Order(10)
class OperadoresDesdeConfiguracion implements ApplicationRunner {

    private final PlataformaService plataforma;

    OperadoresDesdeConfiguracion(PlataformaService plataforma) {
        this.plataforma = plataforma;
    }

    @Override
    public void run(ApplicationArguments args) {
        plataforma.marcarOperadores();
    }
}
