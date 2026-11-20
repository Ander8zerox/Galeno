import { useReferencia } from "../rips/referencias";
import { fechaHoraCorta } from "../agenda/tiempo";
import type { Schemas } from "../../api/client";

type Atencion = Schemas["AtencionVista"];
export type Encuentro = { modalidadAtencion: string; grupoServicios: string; causaExterna: string; entorno: string };

/** Entornos de atención sugeridos (texto libre mientras no se confirme la tabla oficial del RDA). */
const ENTORNOS = ["Institucional", "Hogar", "Comunitario", "Escolar", "Laboral", "Virtual"];

export function encuentroDe(a: Atencion): Encuentro {
  return {
    modalidadAtencion: a.modalidadAtencion ?? "", grupoServicios: a.grupoServicios ?? "",
    causaExterna: a.causaExterna ?? "", entorno: a.entorno ?? "",
  };
}

/**
 * Datos del encuentro (bloque 4 del RDA): modalidad, grupo de servicios y causa externa (tablas del RIPS) y el
 * entorno. Arrancan con los del prestador; se cambian solo si esta atención es distinta.
 */
export function DatosEncuentro({ valor, onChange, rips }: { valor: Encuentro; onChange: (e: Encuentro) => void; rips: boolean }) {
  const modalidades = useReferencia("modalidad_atencion", rips);
  const grupos = useReferencia("grupo_servicios", rips);
  const causas = useReferencia("causa_externa", rips);
  const set = (k: keyof Encuentro) => (e: { target: { value: string } }) => onChange({ ...valor, [k]: e.target.value });
  const resumen = [
    rips && modalidades.data?.find((m) => m.codigo === valor.modalidadAtencion)?.nombre,
    valor.entorno,
    rips && causas.data?.find((c) => c.codigo === valor.causaExterna)?.nombre,
  ].filter(Boolean).join(" · ");
  return (
    <details className="card">
      <summary><strong>Datos del encuentro</strong> <span className="muted small">{resumen || "sin datos"}</span></summary>
      <div className="form-grid">
        {rips && (
          <>
            <label>Modalidad
              <select value={valor.modalidadAtencion} onChange={set("modalidadAtencion")}>
                <option value="">La del prestador</option>
                {(modalidades.data ?? []).map((m) => <option key={m.codigo} value={m.codigo}>{m.codigo} · {m.nombre}</option>)}
              </select>
            </label>
            <label>Grupo de servicios
              <select value={valor.grupoServicios} onChange={set("grupoServicios")}>
                <option value="">El del prestador</option>
                {(grupos.data ?? []).map((m) => <option key={m.codigo} value={m.codigo}>{m.codigo} · {m.nombre}</option>)}
              </select>
            </label>
            <label>Causa de la atención
              <select value={valor.causaExterna} onChange={set("causaExterna")}>
                <option value="">La del prestador</option>
                {(causas.data ?? []).map((m) => <option key={m.codigo} value={m.codigo}>{m.codigo} · {m.nombre}</option>)}
              </select>
            </label>
          </>
        )}
        <label>Entorno
          <input value={valor.entorno} onChange={set("entorno")} maxLength={40} list="entornos-atencion" />
          <datalist id="entornos-atencion">{ENTORNOS.map((e) => <option key={e} value={e} />)}</datalist>
        </label>
      </div>
      <p className="small muted">La hora de fin se registra al firmar.</p>
    </details>
  );
}

/** Lectura: inicio y fin, modalidad, grupo, causa y entorno. */
export function EncuentroLectura({ a, rips, zona }: { a: Atencion; rips: boolean; zona: string }) {
  const modalidades = useReferencia("modalidad_atencion", rips && !!a.modalidadAtencion);
  const grupos = useReferencia("grupo_servicios", rips && !!a.grupoServicios);
  const causas = useReferencia("causa_externa", rips && !!a.causaExterna);
  const nombre = (lista: { codigo?: string; nombre?: string }[] | undefined, c?: string) =>
    c ? lista?.find((x) => x.codigo === c)?.nombre ?? c : null;
  const partes = [
    `Inicio ${fechaHoraCorta(a.fechaAtencion!, zona)}${a.finalizadaEn ? ` · fin ${fechaHoraCorta(a.finalizadaEn, zona)}` : ""}`,
    nombre(modalidades.data, a.modalidadAtencion) && `Modalidad: ${nombre(modalidades.data, a.modalidadAtencion)}`,
    nombre(grupos.data, a.grupoServicios) && `Grupo: ${nombre(grupos.data, a.grupoServicios)}`,
    nombre(causas.data, a.causaExterna) && `Causa: ${nombre(causas.data, a.causaExterna)}`,
    a.entorno && `Entorno: ${a.entorno}`,
  ].filter(Boolean);
  return <p><strong>Encuentro:</strong> {partes.join(" · ")}</p>;
}
