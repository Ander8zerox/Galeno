package com.galeno.rips;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class FacturaXmlTest {

    private static final String FACTURA = """
            <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                     xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
                     xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2">
              <cbc:CustomizationID>SS-CUFE</cbc:CustomizationID>
              <cbc:ID>FE-100</cbc:ID>
              <cac:InvoicePeriod><cbc:StartDate>2026-11-01</cbc:StartDate><cbc:EndDate>2026-11-30</cbc:EndDate></cac:InvoicePeriod>
              <cac:AccountingSupplierParty><cac:Party><cac:PartyTaxScheme>
                <cbc:CompanyID schemeID="7" schemeName="31">900123456</cbc:CompanyID>
              </cac:PartyTaxScheme></cac:Party></cac:AccountingSupplierParty>
              <cac:PrepaidPayment><cbc:ID schemeID="02">1</cbc:ID><cbc:PaidAmount currencyID="COP">4500.00</cbc:PaidAmount></cac:PrepaidPayment>
              <cac:PrepaidPayment><cbc:ID schemeID="01">2</cbc:ID><cbc:PaidAmount currencyID="COP">3000.00</cbc:PaidAmount></cac:PrepaidPayment>
              <cac:PrepaidPayment><cbc:ID schemeID="04">3</cbc:ID><cbc:PaidAmount currencyID="COP">99999.00</cbc:PaidAmount></cac:PrepaidPayment>
            </Invoice>
            """;

    @Test
    void leeLaFacturaSola() {
        var x = FacturaXml.leer(FACTURA.getBytes(StandardCharsets.UTF_8));

        assertThat(x.nit()).isEqualTo("900123456");
        assertThat(x.numero()).isEqualTo("FE-100");
        assertThat(x.desde()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(x.hasta()).isEqualTo(LocalDate.of(2026, 11, 30));
        // Copago y cuota moderadora; el anticipo (04) no es pago moderador
        assertThat(x.pagosModeradores()).isEqualByComparingTo("7500");
    }

    @Test
    void leeLaFacturaDentroDelAttachedDocumentDeLaDian() {
        String adjunto = """
                <AttachedDocument xmlns="urn:oasis:names:specification:ubl:schema:xsd:AttachedDocument-2"
                    xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
                    xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2">
                  <cbc:ID>ADJ-1</cbc:ID>
                  <cac:Attachment><cac:ExternalReference><cbc:MimeCode>text/xml</cbc:MimeCode>
                    <cbc:Description><![CDATA[%s]]></cbc:Description>
                  </cac:ExternalReference></cac:Attachment>
                </AttachedDocument>
                """.formatted(FACTURA);

        var x = FacturaXml.leer(adjunto.getBytes(StandardCharsets.UTF_8));
        assertThat(x.numero()).isEqualTo("FE-100");
        assertThat(x.pagosModeradores()).isEqualByComparingTo("7500");
    }

    @Test
    void rechazaEntidadesExternasYArchivosQueNoSonFacturas() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><Invoice>&e;</Invoice>";
        assertThatThrownBy(() -> FacturaXml.leer(xxe.getBytes(StandardCharsets.UTF_8))).hasMessageContaining("XML válido");
        assertThatThrownBy(() -> FacturaXml.leer("<CreditNote/>".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("no es una factura");
        assertThatThrownBy(() -> FacturaXml.leer(new byte[0])).hasMessageContaining("5 MB");
    }
}
