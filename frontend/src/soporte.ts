/**
 * Contacto de soporte de Galenum. Los mismos valores están en el backend (app.soporte en application.yml), que los usa
 * en los mensajes de error y en los correos.
 */
export const SOPORTE = {
  correo: "soporte@galenum.com.co",
  /** Formato internacional sin signos, p. ej. "573001234567"; vacío, no se muestra. */
  whatsapp: "",
};

export const enlaceCorreoSoporte = `mailto:${SOPORTE.correo}`;
export const enlaceWhatsappSoporte = SOPORTE.whatsapp ? `https://wa.me/${SOPORTE.whatsapp}` : null;

/** Para textos: "soporte@galenum.com.co" (o con el WhatsApp si lo hay). */
export const contactoSoporte = SOPORTE.whatsapp ? `${SOPORTE.correo} o por WhatsApp` : SOPORTE.correo;
