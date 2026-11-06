package com.galeno.rips;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Lee del XML de la factura electrónica de venta en salud lo que el RIPS cruza (Documento Técnico 2): NIT del emisor,
 * número, período de facturación y la suma de los pagos moderadores (copago, cuota moderadora y pagos compartidos; el
 * anticipo no cuenta). Acepta el AttachedDocument que entrega la DIAN (con la factura dentro) o la factura sola.
 */
final class FacturaXml {

    static final int MAX_BYTES = 5 * 1024 * 1024;
    /** schemeID de PrepaidPayment/ID que son pagos moderadores (el 04 es anticipo). */
    private static final Set<String> MODERADORES = Set.of("01", "02", "03");

    private FacturaXml() {
    }

    static ArmadorRips.DatosXml leer(byte[] contenido) {
        Element raiz = raiz(contenido, "la factura");
        if (!"Invoice".equals(raiz.getLocalName())) {
            throw ApiProblem.badRequest("El archivo no es una factura electrónica (se esperaba Invoice o AttachedDocument)");
        }
        String numero = texto(hijo(raiz, "ID"));
        Element emisor = primero(primero(primero(raiz, "AccountingSupplierParty"), "Party"), "PartyTaxScheme");
        String nit = ArmadorRips.nitSinDigito(texto(primero(emisor, "CompanyID")));
        Element periodo = hijo(raiz, "InvoicePeriod");
        BigDecimal moderadores = BigDecimal.ZERO;
        for (Element pago : hijos(raiz, "PrepaidPayment")) {
            Element id = hijo(pago, "ID");
            String concepto = id == null ? null : id.getAttribute("schemeID").strip();
            String valor = texto(hijo(pago, "PaidAmount"));
            if (concepto != null && MODERADORES.contains(concepto) && valor != null) {
                try {
                    moderadores = moderadores.add(new BigDecimal(valor));
                } catch (NumberFormatException e) {
                    throw ApiProblem.badRequest("Valor de pago moderador no válido en el XML: " + valor);
                }
            }
        }
        return new ArmadorRips.DatosXml(nit, numero, fecha(texto(primero(periodo, "StartDate"))),
                fecha(texto(primero(periodo, "EndDate"))), moderadores);
    }

    /**
     * Número, factura que referencia (BillingReference) y valor de una nota crédito o débito electrónica. El valor es el
     * PayableAmount: en la nota crédito va en LegalMonetaryTotal y en la débito, en RequestedMonetaryTotal.
     */
    static ArmadorNota.XmlNota leerNota(byte[] contenido) {
        Element raiz = raiz(contenido, "la nota");
        if (!"CreditNote".equals(raiz.getLocalName()) && !"DebitNote".equals(raiz.getLocalName())) {
            throw ApiProblem.badRequest("El archivo no es una nota crédito o débito electrónica (se esperaba CreditNote, "
                    + "DebitNote o AttachedDocument)");
        }
        Element referencia = primero(primero(raiz, "BillingReference"), "InvoiceDocumentReference");
        Element totales = hijo(raiz, "CreditNote".equals(raiz.getLocalName()) ? "LegalMonetaryTotal" : "RequestedMonetaryTotal");
        String valor = texto(hijo(totales, "PayableAmount"));
        try {
            return new ArmadorNota.XmlNota(texto(hijo(raiz, "ID")), texto(hijo(referencia, "ID")),
                    valor == null ? null : new BigDecimal(valor));
        } catch (NumberFormatException e) {
            throw ApiProblem.badRequest("Valor de la nota no válido en el XML: " + valor);
        }
    }

    /** El documento electrónico: el que viene dentro del AttachedDocument de la DIAN, o el archivo mismo. */
    private static Element raiz(byte[] contenido, String que) {
        if (contenido == null || contenido.length == 0 || contenido.length > MAX_BYTES) {
            throw ApiProblem.badRequest("El XML de " + que + " debe pesar entre 1 byte y 5 MB");
        }
        Element raiz = documento(new InputSource(new ByteArrayInputStream(contenido))).getDocumentElement();
        if ("AttachedDocument".equals(raiz.getLocalName())) {
            // El documento va como texto dentro de Attachment/ExternalReference/Description
            Element descripcion = primero(primero(primero(raiz, "Attachment"), "ExternalReference"), "Description");
            if (descripcion == null || descripcion.getTextContent().isBlank()) {
                throw ApiProblem.badRequest("El AttachedDocument no trae " + que);
            }
            raiz = documento(new InputSource(new StringReader(descripcion.getTextContent().strip()))).getDocumentElement();
        }
        return raiz;
    }

    /** Sin DTD ni entidades externas (XXE). */
    private static Document documento(InputSource fuente) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            return b.parse(fuente);
        } catch (Exception e) {
            throw ApiProblem.badRequest("No se pudo leer el XML de la factura: el archivo no es un XML válido");
        }
    }

    /** Hijo directo con ese nombre local (sin importar el prefijo). */
    private static Element hijo(Element padre, String nombre) {
        if (padre == null) {
            return null;
        }
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && nombre.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static java.util.List<Element> hijos(Element padre, String nombre) {
        java.util.List<Element> lista = new java.util.ArrayList<>();
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && nombre.equals(e.getLocalName())) {
                lista.add(e);
            }
        }
        return lista;
    }

    /** El primer descendiente con ese nombre local. */
    private static Element primero(Element padre, String nombre) {
        if (padre == null) {
            return null;
        }
        NodeList l = padre.getElementsByTagNameNS("*", nombre);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private static String texto(Element e) {
        return e == null || e.getTextContent().isBlank() ? null : e.getTextContent().strip();
    }

    private static LocalDate fecha(String s) {
        try {
            return s == null ? null : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
