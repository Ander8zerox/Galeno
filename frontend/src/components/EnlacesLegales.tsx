/**
 * Términos de uso y política de datos: al pie del ingreso, del registro, del portal de partners y del menú. Se abren en
 * otra pestaña para no perder lo que se esté escribiendo.
 */
export function EnlacesLegales({ className = "enlaces-legales" }: { className?: string }) {
  return (
    <p className={className}>
      <a href="/terminos" target="_blank" rel="noreferrer">Términos de uso</a>
      {" · "}
      <a href="/politica-datos" target="_blank" rel="noreferrer">Política de datos</a>
    </p>
  );
}
