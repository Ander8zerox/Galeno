import { type Schemas } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { BuscadorCups } from "./BuscadorCups";
import { pesos } from "./modulos";

export type ProcedimientoForm = Schemas["ProcedimientoRequest"] & { descripcion?: string };

/** Odontología general o especializada, con o sin tilde (como sufijoConsulta en el servidor). */
const esOdontologia = (especialidad?: string) =>
  (especialidad ?? "").normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().includes("odont");

/**
 * Lo que se hizo en la atención, con su CUPS. El diente y las superficies (odontología) se muestran solo a quien es
 * odontólogo según su perfil, o si algún procedimiento ya trae diente. Quien edita un borrador es siempre su autor.
 *
 * @param atencionId para proponer la tarifa del pagador de la atención (si no tiene, la lista general)
 */
export function ProcedimientosEditor({ value, onChange, atencionId }: {
  value: ProcedimientoForm[]; onChange: (p: ProcedimientoForm[]) => void; atencionId?: string;
}) {
  const { me } = useAuth();
  const dental = esOdontologia(me?.especialidad) || value.some((p) => p.diente);
  const actualizar = (i: number, cambio: Partial<ProcedimientoForm>) => onChange(value.map((p, j) => (j === i ? { ...p, ...cambio } : p)));
  const total = value.reduce((s, p) => s + (p.valorUnitario ?? 0) * (p.cantidad ?? 1), 0);
  const numero = (v: string) => (v.trim() === "" ? undefined : Number(v.replace(/[.\s]/g, "").replace(",", ".")));

  return (
    <div className="stack">
      {!!value.length && (
        <div className="table-wrap">
          <table className="tabla-procedimientos">
            <thead><tr><th>CUPS</th><th>Cant.</th><th>Valor unitario</th>{dental && <><th>Diente</th><th>Superficies</th></>}<th /></tr></thead>
            <tbody>
              {value.map((p, i) => (
                <tr key={`${p.codigoCups}-${i}`}>
                  <td><strong>{p.codigoCups}</strong> <span className="small">{p.descripcion}</span></td>
                  <td><input aria-label="Cantidad" className="campo-corto" type="number" min={1} max={99} value={p.cantidad ?? 1}
                    onChange={(e) => actualizar(i, { cantidad: Math.max(1, Number(e.target.value) || 1) })} /></td>
                  <td><input aria-label="Valor unitario" className="campo-medio" inputMode="numeric" value={p.valorUnitario ?? ""} placeholder="Sin precio"
                    onChange={(e) => actualizar(i, { valorUnitario: numero(e.target.value) })} /></td>
                  {dental && (
                    <>
                      <td><input aria-label="Diente" className="campo-corto" maxLength={2} inputMode="numeric" value={p.diente ?? ""} placeholder="—"
                        onChange={(e) => actualizar(i, { diente: e.target.value.replace(/\D/g, "") || undefined })} /></td>
                      <td><input aria-label="Superficies" className="campo-corto" maxLength={5} value={p.superficies ?? ""} placeholder="—" title="V, L, M, D u O"
                        disabled={!p.diente} onChange={(e) => actualizar(i, { superficies: e.target.value.toUpperCase().replace(/[^VLMDO]/g, "") || undefined })} /></td>
                    </>
                  )}
                  <td><button type="button" className="btn-link danger" onClick={() => onChange(value.filter((_, j) => j !== i))}>Quitar</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {!!value.length && <span className="small">Total: <strong>{pesos(total)}</strong></span>}
      <BuscadorCups etiqueta="Agregar procedimiento (código o nombre CUPS)" atencionId={atencionId} onElegir={(c) =>
        onChange([...value, { codigoCups: c.codigo!, descripcion: c.descripcion, cantidad: 1, valorUnitario: c.valorUnitario ?? undefined }])} />
    </div>
  );
}

/** De solo lectura, en la atención firmada. */
export function ProcedimientosLectura({ procedimientos }: { procedimientos: Schemas["ProcedimientoVista"][] }) {
  if (!procedimientos.length) return null;
  const total = procedimientos.reduce((s, p) => s + (p.valorUnitario ?? 0) * (p.cantidad ?? 1), 0);
  return (
    <div>
      <h3>Procedimientos realizados</h3>
      <ul>
        {procedimientos.map((p, i) => (
          <li key={i}>
            <strong>{p.codigoCups}</strong> {p.descripcion}
            {p.cantidad !== 1 && ` × ${p.cantidad}`}
            {p.diente && ` · diente ${p.diente}${p.superficies ? ` ${p.superficies}` : ""}`}
            {" · "}<span className="muted">{pesos(p.valorUnitario)}</span>
          </li>
        ))}
      </ul>
      <span className="small">Total: <strong>{pesos(total)}</strong></span>
    </div>
  );
}
