package com.galeno.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NitTest {

    @Test
    void calculaElDigitoDeVerificacionDeLaDian() {
        assertThat(Nit.digitoVerificacion("800197268")).isEqualTo(4); // DIAN
        assertThat(Nit.digitoVerificacion("890903938")).isEqualTo(8); // Bancolombia
    }

    @Test
    void normalizaConOSinPuntosYConOSinDigito() {
        assertThat(Nit.normalizar("800.197.268-4")).isEqualTo("800197268-4");
        assertThat(Nit.normalizar("800197268")).isEqualTo("800197268-4");
        assertThat(Nit.normalizar(" 890 903 938-8 ")).isEqualTo("890903938-8");
    }

    @Test
    void rechazaDigitoEquivocadoYFormatosRaros() {
        assertThatThrownBy(() -> Nit.normalizar("800197268-5")).hasMessageContaining("debería ser 4");
        assertThatThrownBy(() -> Nit.normalizar("ABC123")).hasMessageContaining("dígitos");
        assertThatThrownBy(() -> Nit.normalizar("800197268-45")).hasMessageContaining("un solo número");
    }
}
