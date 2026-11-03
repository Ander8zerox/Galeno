import { enlaceCorreoSoporte, enlaceWhatsappSoporte, SOPORTE } from "../soporte";

/**
 * Términos de uso, política de datos y ayuda: al pie del ingreso, del registro, del portal de partners y del menú. Los
 * legales se abren en otra pestaña para no perder lo que se esté escribiendo.
 */
export function EnlacesLegales({ className = "enlaces-legales" }: { className?: string }) {
  return (
    <p className={className}>
      <a href="/terminos" target="_blank" rel="noreferrer">Términos de uso</a>
      {" · "}
      <a href="/politica-datos" target="_blank" rel="noreferrer">Política de datos</a>
      {" · "}
      <a href={enlaceWhatsappSoporte ?? enlaceCorreoSoporte} target={enlaceWhatsappSoporte ? "_blank" : undefined}
        rel="noreferrer" title={`Ayuda: ${SOPORTE.correo}`}>Ayuda</a>
    </p>
  );
}
