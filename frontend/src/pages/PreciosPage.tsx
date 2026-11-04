import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, expectOk, unwrap, type Schemas } from "../api/client";
import { BuscadorCups, type Cups } from "../features/rips/BuscadorCups";
import { pesos } from "../features/rips/modulos";

type Precio = Schemas["PrecioVista"];
const fecha = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium" });
const numero = (v: string) => Number(v.replace(/[.\s$]/g, "").replace(",", "."));

/** Lista de precios por CUPS: se propone el valor al registrar el procedimiento en la atención. */
export function PreciosPage() {
  const queryClient = useQueryClient();
  const precios = useQuery({ queryKey: ["precios"], queryFn: async () => unwrap(await api.GET("/api/v1/rips/precios")) });
  const [nuevo, setNuevo] = useState<Cups | null>(null);
  const [valor, setValor] = useState("");
  const fijar = useMutation({
    mutationFn: async ({ codigo, v }: { codigo: string; v: number }) =>
      unwrap(await api.PUT("/api/v1/rips/precios/{codigoCups}", { params: { path: { codigoCups: codigo } }, body: { valor: v } })),
    onSuccess: () => { setNuevo(null); setValor(""); queryClient.invalidateQueries({ queryKey: ["precios"] }); queryClient.invalidateQueries({ queryKey: ["cups"] }); },
  });

  return (
    <section className="stack">
      <div className="page-header"><h1>Lista de precios</h1></div>
      <p className="muted">
        El precio de cada procedimiento (CUPS) en el consultorio. Al registrarlo en una atención se propone este valor y
        el médico lo puede cambiar. Cambiar un precio no modifica las atenciones ya registradas.
      </p>
      <div className="card stack">
        {!nuevo ? <BuscadorCups etiqueta="Agregar un CUPS a la lista" onElegir={(c) => { setNuevo(c); setValor(c.valorUnitario ? String(c.valorUnitario) : ""); }} /> : (
          <form className="row" onSubmit={(e) => { e.preventDefault(); fijar.mutate({ codigo: nuevo.codigo!, v: numero(valor) }); }}>
            <span className="grow"><strong>{nuevo.codigo}</strong> {nuevo.descripcion}</span>
            <label className="row">Precio <input className="campo-medio" inputMode="numeric" autoFocus value={valor} onChange={(e) => setValor(e.target.value)} required /></label>
            <button className="btn" disabled={fijar.isPending || valor.trim() === ""}>Guardar</button>
            <button type="button" className="btn-link" onClick={() => setNuevo(null)}>Cancelar</button>
          </form>
        )}
        {fijar.error && <p className="alert alert-error">{fijar.error.message}</p>}
      </div>
      {precios.isLoading && <p className="muted">Cargando…</p>}
      {precios.error && <p className="alert alert-error">{precios.error.message}</p>}
      {precios.data?.length === 0 && <p className="muted">Todavía no hay precios. Empiece por las consultas que más hace (p. ej. 890201 o 890203).</p>}
      {!!precios.data?.length && (
        <div className="card table-wrap">
          <table>
            <thead><tr><th>CUPS</th><th>Descripción</th><th>Precio</th><th>Actualizado</th><th /></tr></thead>
            <tbody>{precios.data.map((p) => <FilaPrecio key={p.codigoCups} precio={p} />)}</tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function FilaPrecio({ precio: p }: { precio: Precio }) {
  const queryClient = useQueryClient();
  const [editando, setEditando] = useState(false);
  const [valor, setValor] = useState(String(p.valor ?? ""));
  const refrescar = () => { queryClient.invalidateQueries({ queryKey: ["precios"] }); queryClient.invalidateQueries({ queryKey: ["cups"] }); };
  const fijar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/rips/precios/{codigoCups}", { params: { path: { codigoCups: p.codigoCups! } }, body: { valor: numero(valor) } })),
    onSuccess: () => { setEditando(false); refrescar(); },
  });
  const quitar = useMutation({
    mutationFn: async () => expectOk(await api.DELETE("/api/v1/rips/precios/{codigoCups}", { params: { path: { codigoCups: p.codigoCups! } } })),
    onSuccess: refrescar,
  });
  return (
    <tr>
      <td><strong>{p.codigoCups}</strong></td>
      <td className="small">{p.descripcion}</td>
      <td>{editando
        ? <input aria-label="Precio" className="campo-medio" inputMode="numeric" autoFocus value={valor} onChange={(e) => setValor(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") fijar.mutate(); if (e.key === "Escape") setEditando(false); }} />
        : pesos(p.valor)}
        {(fijar.error || quitar.error) && <small className="field-error">{(fijar.error ?? quitar.error)?.message}</small>}
      </td>
      <td className="small muted">{p.actualizadoEn && fecha.format(new Date(p.actualizadoEn))}</td>
      <td className="row">
        {editando
          ? <><button className="btn-link" disabled={fijar.isPending} onClick={() => fijar.mutate()}>Guardar</button><button className="btn-link" onClick={() => setEditando(false)}>Cancelar</button></>
          : <><button className="btn-link" onClick={() => setEditando(true)}>Cambiar</button><button className="btn-link danger" disabled={quitar.isPending} onClick={() => quitar.mutate()}>Quitar</button></>}
      </td>
    </tr>
  );
}
