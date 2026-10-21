package com.galeno.partners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.galeno.partners.ComisionPartner.Estado;

class ComisionPartnerTest {

    private static final Instant APROBADO = Instant.parse("2026-10-21T15:00:00Z");
    private static final Duration RETENCION = Duration.ofDays(30);

    private static ComisionPartner comision(long base) {
        return new ComisionPartner(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), base, 20, APROBADO, RETENCION);
    }

    @Test
    void elVeintePorCientoDelPagoConDescuento() {
        // Plan mensual de $190.000 con 10 % de descuento: el consultorio paga $171.000
        assertThat(comision(171_000).getValor()).isEqualTo(34_200);
    }

    @Test
    void retenida30DiasLuegoDisponibleYAlLiquidarPagada() {
        ComisionPartner c = comision(171_000);

        assertThat(c.estado(APROBADO.plus(Duration.ofDays(29)))).isEqualTo(Estado.RETENIDA);
        assertThat(c.estado(APROBADO.plus(RETENCION))).isEqualTo(Estado.DISPONIBLE);

        c.liquidar(UUID.randomUUID(), APROBADO.plus(Duration.ofDays(31)));
        assertThat(c.estado(APROBADO.plus(Duration.ofDays(31)))).isEqualTo(Estado.PAGADA);
    }

    @Test
    void noSeLiquidaRetenidaNiSeAnulaPagada() {
        ComisionPartner c = comision(171_000);
        assertThatThrownBy(() -> c.liquidar(UUID.randomUUID(), APROBADO.plus(Duration.ofDays(2)))).hasMessageContaining("no está disponible");

        c.liquidar(UUID.randomUUID(), APROBADO.plus(Duration.ofDays(40)));
        assertThatThrownBy(() -> c.anular("Devolución", APROBADO.plus(Duration.ofDays(41)))).hasMessageContaining("ya se pagó");
    }

    @Test
    void laAnuladaNoSuma() {
        ComisionPartner anulada = comision(171_000);
        anulada.anular("Pago reversado por el banco", APROBADO.plus(Duration.ofDays(3)));
        ComisionPartner retenida = comision(100_000);
        ComisionPartner disponible = new ComisionPartner(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 200_000, 20,
                APROBADO.minus(Duration.ofDays(40)), RETENCION);

        PartnerService.Saldo saldo = PartnerService.saldo(List.of(anulada, retenida, disponible), APROBADO.plus(Duration.ofDays(5)));

        assertThat(anulada.estado(APROBADO.plus(Duration.ofDays(5)))).isEqualTo(Estado.ANULADA);
        assertThat(saldo).isEqualTo(new PartnerService.Saldo(20_000, 40_000, 0));
    }

    @Test
    void ciclodelPartner() {
        Partner p = new Partner(UUID.randomUUID(), new DatosPartner("Laura", Partner.TipoPersona.NATURAL, "Laura Restrepo",
                "CC", "123", "300", null, null, null), "2026-10");
        assertThat(p.activo()).isFalse();

        p.aprobar("LAURAR123");
        assertThat(p.getCodigo()).isEqualTo("LAURAR123");
        p.suspender("Prácticas engañosas");
        assertThat(p.activo()).isFalse();
        p.aprobar("OTRO999");
        assertThat(p.getCodigo()).as("al reactivar conserva su código").isEqualTo("LAURAR123");
        assertThatThrownBy(() -> p.rechazar("tarde")).hasMessageContaining("pendiente");
    }

    @Test
    void codigosSinImportarMayusculasNiEspacios() {
        assertThat(PartnerService.normalizarCodigo(" laurar123 ")).isEqualTo("LAURAR123");
    }
}
