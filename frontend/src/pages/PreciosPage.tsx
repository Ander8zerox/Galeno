import { ListaPrecios } from "../features/rips/ListaPrecios";

/** Lista de precios por CUPS para los consultorios sin el módulo de RIPS (con el módulo, es una pestaña de RIPS). */
export function PreciosPage() {
  return (
    <section>
      <div className="page-header"><h1>Precios</h1></div>
      <ListaPrecios />
    </section>
  );
}
