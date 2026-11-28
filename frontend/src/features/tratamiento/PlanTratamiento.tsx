import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError, unwrap, type Schemas } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { BotonArchivo } from "../historia/Documentos";
import { BuscadorCups, type Cups } from "../rips/BuscadorCups";
import { pesos } from "../rips/modulos";
import { FirmaPad, type ValorFirma } from "../formatos/FirmaPad";

type Plan = Schemas["PlanTratamientoVista"];
type Item = Schemas["ItemPlanVista"];
type Sugerencia = Schemas["SugerenciaVista"];
type DatosItem = Schemas["ItemPlanTratamiento"];

const fecha = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium" });
const numero = (v: string) => (v.trim() === "" ? undefined : Number(v.replace(/[.\s$]/g, "").replace(",", ".")));
const VIGENTES = ["BORRADOR", "PRESENTADO", "ACEPTADO"];
const ESTADO: Record<string, { texto: string; clase: string }> = {
  BORRADOR: { texto: "Borrador", clase: "" },
  PRESENTADO: { texto: "Presentado al paciente", clase: "badge-info" },
  ACEPTADO: { texto: "Aceptado", clase: "badge-ok" },
  TERMINADO: { texto: "Terminado", clase: "" },
  CANCELADO: { texto: "Cancelado", clase: "badge-warn" },
};

/**
 * Plan de tratamiento con presupuesto (odontología), en la ficha del paciente. Solo aparece si el consultorio tiene el
 * odontograma activo: el servidor responde 404 y la tarjeta no se muestra.
 */
export function PlanTratamiento({ pacienteId }: { pacienteId: string }) {
  const { puede } = useAuth();
  const queryClient = useQueryClient();
  const clave = ["planes-tratamiento", pacienteId];
  const planes = useQuery({
    queryKey: clave,
    retry: false,
    queryFn: async () => unwrap(await api.GET("/api/v1/tratamientos", { params: { query: { pacienteId } } })),
  });
  const crear = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/tratamientos", { body: { pacienteId } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: clave }),
  });
  const [verAnteriores, setVerAnteriores] = useState(false);

  if (planes.error instanceof ApiError && planes.error.status === 404) return null;
  if (planes.isLoading) return null;
  if (planes.error) return <p className="alert alert-error">{planes.error.message}</p>;
  const vigente = planes.data?.find((p) => VIGENTES.includes(p.estado ?? ""));
  const anteriores = planes.data?.filter((p) => p !== vigente) ?? [];

  return (
    <section className="card stack">
      <div className="row between">
        <h2>Plan de tratamiento</h2>
        {!vigente && puede("TRATAMIENTO_REGISTRAR") && (
          <button className="btn" disabled={crear.isPending} onClick={() => crear.mutate()}>Crear plan de tratamiento</button>
        )}
      </div>
      {crear.error && <p className="alert alert-error">{crear.error.message}</p>}
      {vigente ? <PlanVigente plan={vigente} pacienteId={pacienteId} /> : <p className="muted">El paciente no tiene un plan de tratamiento vigente.</p>}
      {anteriores.length > 0 && (
        <div className="stack-sm">
          <button type="button" className="btn-link self-start" onClick={() => setVerAnteriores((v) => !v)}>
            {verAnteriores ? "Ocultar" : "Ver"} planes anteriores ({anteriores.length})
          </button>
          {verAnteriores && anteriores.map((p) => (
            <div key={p.id} className="row between">
              <span>
                <span className={`badge ${ESTADO[p.estado!]?.clase ?? ""}`}>{ESTADO[p.estado!]?.texto}</span>{" "}
                {fecha.format(new Date(p.creadoEn!))} · {p.odontologo} · {pesos(p.totales?.realizado)} realizado
                {p.motivoCierre && <span className="muted small"> · {p.motivoCierre}</span>}
              </span>
              <BotonArchivo ruta={`/api/v1/tratamientos/${p.id}/pdf`} nombre={`presupuesto-${p.id!.slice(0, 8)}.pdf`}>PDF</BotonArchivo>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function PlanVigente({ plan, pacienteId }: { plan: Plan; pacienteId: string }) {
  const { puede } = useAuth();
  const queryClient = useQueryClient();
  const editar = puede("TRATAMIENTO_REGISTRAR");
  const [panel, setPanel] = useState<"" | "agregar" | "sugerencias" | "aceptar" | "terminar" | "cancelar" | "datos">("");
  const accion = useMutation({
    mutationFn: async (fn: () => Promise<Plan>) => fn(),
    onSuccess: (p) => {
      queryClient.setQueryData<Plan[]>(["planes-tratamiento", pacienteId], (lista) => lista?.map((x) => (x.id === p.id ? p : x)));
      if (!VIGENTES.includes(p.estado ?? "")) queryClient.invalidateQueries({ queryKey: ["planes-tratamiento", pacienteId] });
      setPanel("");
    },
  });
  const id = plan.id!;
  const ruta = { params: { path: { id } } };
  const t = plan.totales ?? {};
  const porAceptar = (plan.items ?? []).some((i) => i.aceptacion == null);
  const fases = new Map<number, Item[]>();
  (plan.items ?? []).forEach((i) => fases.set(i.fase!, [...(fases.get(i.fase!) ?? []), i]));

  return (
    <div className="stack">
      <div className="row between">
        <span className="row">
          <span className={`badge ${ESTADO[plan.estado!]?.clase ?? ""}`}>{ESTADO[plan.estado!]?.texto}</span>
          <span className="muted small">{plan.odontologo} · creado el {fecha.format(new Date(plan.creadoEn!))}</span>
        </span>
        <BotonArchivo ruta={`/api/v1/tratamientos/${id}/pdf`} nombre={`presupuesto-${id.slice(0, 8)}.pdf`} className="btn btn-secondary">
          PDF del presupuesto
        </BotonArchivo>
      </div>

      <div className="row">
        <span><span className="muted small">Aceptado</span><br /><strong>{pesos(t.aceptado)}</strong></span>
        <span><span className="muted small">Realizado</span><br /><strong>{pesos(t.realizado)}</strong></span>
        <span><span className="muted small">Pendiente</span><br /><strong>{pesos(t.pendiente)}</strong></span>
        {!!t.porAceptar && <span><span className="muted small">Por aceptar</span><br /><strong>{pesos(t.porAceptar)}</strong></span>}
        <span><span className="muted small">Avance</span><br /><strong>{t.realizados ?? 0} de {t.procedimientos ?? 0}</strong></span>
      </div>

      {plan.items?.length ? (
        <div className="table-wrap">
          <table className="tabla-procedimientos">
            <thead><tr><th>Procedimiento</th><th>Diente</th><th>Cant.</th><th>Valor</th><th>Desc.</th><th>Total</th><th>Estado</th><th /></tr></thead>
            {[...fases.entries()].map(([fase, items]) => (
              <tbody key={fase}>
                <tr><th colSpan={8}>Fase {fase}{items.find((i) => i.faseNombre)?.faseNombre ? `: ${items.find((i) => i.faseNombre)!.faseNombre}` : ""}</th></tr>
                {items.map((i) => (
                  <FilaItem key={i.id} item={i} editar={editar} accion={accion} planId={id} conAceptaciones={!!plan.aceptaciones?.length} />
                ))}
              </tbody>
            ))}
          </table>
        </div>
      ) : <p className="muted">Agregue los procedimientos del plan, o tómelos del último odontograma.</p>}

      {plan.observaciones && <p className="small"><span className="muted">Observaciones:</span> {plan.observaciones}</p>}
      {accion.error && <p className="alert alert-error">{accion.error.message}</p>}

      <div className="row">
        {editar && <button className="btn btn-secondary" onClick={() => setPanel(panel === "agregar" ? "" : "agregar")}>Agregar procedimiento</button>}
        {editar && <button className="btn btn-secondary" onClick={() => setPanel(panel === "sugerencias" ? "" : "sugerencias")}>Desde el odontograma</button>}
        {editar && plan.estado === "BORRADOR" && !!plan.items?.length && (
          <button className="btn" disabled={accion.isPending} onClick={() => accion.mutate(async () => unwrap(await api.POST("/api/v1/tratamientos/{id}/presentar", ruta)))}>
            Presentar al paciente
          </button>
        )}
        {editar && plan.estado === "PRESENTADO" && (
          <button className="btn-link" onClick={() => accion.mutate(async () => unwrap(await api.POST("/api/v1/tratamientos/{id}/borrador", ruta)))}>Volver a borrador</button>
        )}
        {puede("TRATAMIENTO_ACEPTAR") && porAceptar && plan.estado !== "BORRADOR" && (
          <button className="btn" onClick={() => setPanel("aceptar")}>
            {plan.aceptaciones?.length ? "Registrar aceptación del adicional" : "Registrar aceptación"}
          </button>
        )}
        <span className="grow" />
        {editar && <button className="btn-link" onClick={() => setPanel(panel === "datos" ? "" : "datos")}>Observaciones y vigencia</button>}
        {editar && plan.estado === "ACEPTADO" && <button className="btn-link" onClick={() => setPanel("terminar")}>Terminar</button>}
        {editar && <button className="btn-link" onClick={() => setPanel("cancelar")}>Cancelar plan</button>}
      </div>
      {editar && plan.estado === "ACEPTADO" && porAceptar && (
        <p className="muted small">Lo agregado después de la aceptación es un adicional: el paciente lo acepta aparte y lo ya aceptado no cambia.</p>
      )}

      {panel === "agregar" && (
        <FormItem onCancelar={() => setPanel("")} pendiente={accion.isPending}
          onGuardar={(d) => accion.mutate(async () => unwrap(await api.POST("/api/v1/tratamientos/{id}/items", { ...ruta, body: d })))} />
      )}
      {panel === "sugerencias" && <Sugerencias pacienteId={pacienteId} planId={id} onListo={() => setPanel("")} />}
      {panel === "aceptar" && (
        <Aceptar pendiente={accion.isPending} onCancelar={() => setPanel("")}
          onAceptar={(firma, verbal) => accion.mutate(async () => unwrap(await api.POST("/api/v1/tratamientos/{id}/aceptacion",
            { ...ruta, body: { firma: firma?.imagen, nombreFirmante: firma?.nombre, constanciaVerbal: verbal } })))} />
      )}
      {(panel === "terminar" || panel === "cancelar") && (
        <Motivo titulo={panel === "terminar" ? "Terminar el plan" : "Cancelar el plan"} pendiente={accion.isPending}
          ayuda="Lo que quede pendiente se cancela con este motivo." onCancelar={() => setPanel("")}
          onConfirmar={(motivo) => accion.mutate(async () => unwrap(panel === "terminar"
            ? await api.POST("/api/v1/tratamientos/{id}/terminar", { ...ruta, body: { motivo } })
            : await api.POST("/api/v1/tratamientos/{id}/cancelar", { ...ruta, body: { motivo } })))} />
      )}
      {panel === "datos" && (
        <DatosPlan plan={plan} pendiente={accion.isPending} onCancelar={() => setPanel("")}
          onGuardar={(observaciones, vigenciaDias) => accion.mutate(async () => unwrap(await api.PUT("/api/v1/tratamientos/{id}",
            { ...ruta, body: { observaciones, vigenciaDias } })))} />
      )}
    </div>
  );
}

type Accion = ReturnType<typeof useMutation<Plan, Error, () => Promise<Plan>>>;

function FilaItem({ item: i, editar, accion, planId, conAceptaciones }: { item: Item; editar: boolean; accion: Accion; planId: string; conAceptaciones: boolean }) {
  const [modo, setModo] = useState<"" | "editar" | "cancelar">("");
  const ruta = { params: { path: { id: planId, itemId: i.id! } } };
  // Antes de la primera aceptación todo está por aceptar: solo se marca en los adicionales
  const estado = i.aceptacion == null ? (conAceptaciones ? <span className="badge badge-warn">Adicional por aceptar</span> : null)
    : i.estado === "REALIZADO" ? <span className="badge badge-ok">Realizado</span>
    : i.estado === "CANCELADO" ? <span className="badge" title={i.motivoCancelacion}>Cancelado</span>
    : <span className="badge">Pendiente</span>;
  return (
    <>
      <tr className={i.estado === "CANCELADO" ? "muted" : ""}>
        <td>{i.descripcion}<br /><span className="muted small">CUPS {i.codigoCups}{i.aceptacion && i.aceptacion > 1 ? ` · adicional ${i.aceptacion - 1}` : ""}</span>
          {i.estado === "CANCELADO" && i.motivoCancelacion && <div className="muted small">{i.motivoCancelacion}</div>}</td>
        <td>{i.diente} {i.superficies}</td>
        <td>{i.cantidad}</td>
        <td>{pesos(i.valorUnitario)}</td>
        <td>{i.descuento ? pesos(i.descuento) : ""}</td>
        <td><strong>{pesos(i.total)}</strong></td>
        <td>{estado}</td>
        <td>
          {editar && i.aceptacion == null && (
            <span className="row">
              <button type="button" className="btn-link" onClick={() => setModo(modo === "editar" ? "" : "editar")}>Editar</button>
              <button type="button" className="btn-link" disabled={accion.isPending}
                onClick={() => accion.mutate(async () => unwrap(await api.DELETE("/api/v1/tratamientos/{id}/items/{itemId}", ruta)))}>Quitar</button>
            </span>
          )}
          {editar && i.aceptacion != null && i.estado === "PENDIENTE" && (
            <button type="button" className="btn-link" onClick={() => setModo(modo === "cancelar" ? "" : "cancelar")}>Cancelar</button>
          )}
        </td>
      </tr>
      {modo !== "" && (
        <tr><td colSpan={8}>
          {modo === "editar" ? (
            <FormItem inicial={i} pendiente={accion.isPending} onCancelar={() => setModo("")}
              onGuardar={(d) => accion.mutate(async () => unwrap(await api.PUT("/api/v1/tratamientos/{id}/items/{itemId}", { ...ruta, body: d })))} />
          ) : (
            <Motivo titulo="Cancelar este procedimiento" ayuda="Ya fue aceptado: queda en el plan como cancelado, con el motivo."
              pendiente={accion.isPending} onCancelar={() => setModo("")}
              onConfirmar={(motivo) => accion.mutate(async () => unwrap(await api.POST("/api/v1/tratamientos/{id}/items/{itemId}/cancelar", { ...ruta, body: { motivo } })))} />
          )}
        </td></tr>
      )}
    </>
  );
}

function FormItem({ inicial, onGuardar, onCancelar, pendiente }: { inicial?: Item; onGuardar: (d: DatosItem) => void; onCancelar: () => void; pendiente: boolean }) {
  const [cups, setCups] = useState<Pick<Cups, "codigo" | "descripcion" | "valorUnitario"> | null>(
    inicial ? { codigo: inicial.codigoCups, descripcion: inicial.descripcion, valorUnitario: inicial.valorUnitario } : null);
  const [fase, setFase] = useState(String(inicial?.fase ?? 1));
  const [faseNombre, setFaseNombre] = useState(inicial?.faseNombre ?? "");
  const [diente, setDiente] = useState(inicial?.diente ?? "");
  const [superficies, setSuperficies] = useState(inicial?.superficies ?? "");
  const [cantidad, setCantidad] = useState(String(inicial?.cantidad ?? 1));
  const [valor, setValor] = useState(inicial?.valorUnitario != null ? String(inicial.valorUnitario) : "");
  const [descuento, setDescuento] = useState(inicial?.descuento ? String(inicial.descuento) : "");
  if (!cups) {
    return (
      <div className="card stack-sm">
        <BuscadorCups etiqueta="Procedimiento (CUPS)" onElegir={(c) => { setCups(c); setValor(c.valorUnitario != null ? String(c.valorUnitario) : ""); }} />
        <button type="button" className="btn-link self-start" onClick={onCancelar}>Cancelar</button>
      </div>
    );
  }
  return (
    <form className="card stack-sm" onSubmit={(e) => {
      e.preventDefault();
      onGuardar({ fase: Number(fase), faseNombre: faseNombre || undefined, codigoCups: cups.codigo!, descripcion: inicial?.descripcion,
        diente: diente || undefined, superficies: superficies || undefined, cantidad: Number(cantidad),
        valorUnitario: numero(valor), descuento: numero(descuento) });
    }}>
      <span><strong>{cups.codigo}</strong> {cups.descripcion}</span>
      <div className="row">
        <label>Fase <input className="campo-corto" type="number" min={1} max={20} value={fase} onChange={(e) => setFase(e.target.value)} required /></label>
        <label className="grow">Nombre de la fase <input value={faseNombre} maxLength={80} placeholder="Ej.: Operatoria" onChange={(e) => setFaseNombre(e.target.value)} /></label>
      </div>
      <div className="row">
        <label>Diente <input className="campo-corto" value={diente} maxLength={2} placeholder="36" onChange={(e) => setDiente(e.target.value)} /></label>
        <label>Superficies <input className="campo-corto" value={superficies} maxLength={5} placeholder="MO" onChange={(e) => setSuperficies(e.target.value.toUpperCase())} /></label>
        <label>Cantidad <input className="campo-corto" type="number" min={1} max={99} value={cantidad} onChange={(e) => setCantidad(e.target.value)} required /></label>
        <label>Valor <input className="campo-medio" inputMode="numeric" value={valor} placeholder="De la lista" onChange={(e) => setValor(e.target.value)} /></label>
        <label>Descuento <input className="campo-medio" inputMode="numeric" value={descuento} onChange={(e) => setDescuento(e.target.value)} /></label>
      </div>
      <div className="row">
        <button className="btn" disabled={pendiente}>{inicial ? "Guardar" : "Agregar al plan"}</button>
        <button type="button" className="btn-link" onClick={onCancelar}>Cancelar</button>
      </div>
    </form>
  );
}

function Sugerencias({ pacienteId, planId, onListo }: { pacienteId: string; planId: string; onListo: () => void }) {
  const queryClient = useQueryClient();
  const sugerencias = useQuery({
    queryKey: ["sugerencias-tratamiento", pacienteId],
    queryFn: async () => unwrap(await api.GET("/api/v1/tratamientos/sugerencias", { params: { query: { pacienteId } } })),
  });
  const [elegidas, setElegidas] = useState<Record<number, boolean>>({});
  const [valores, setValores] = useState<Record<number, string>>({});
  const agregar = useMutation({
    mutationFn: async (lista: Sugerencia[]) => {
      let plan: Plan | undefined;
      for (const [n, s] of lista.entries()) {
        const valor = s.valorUnitario ?? numero(valores[n] ?? "");
        plan = unwrap(await api.POST("/api/v1/tratamientos/{id}/items", { params: { path: { id: planId } }, body: {
          fase: s.fase!, faseNombre: s.faseNombre, codigoCups: s.codigoCups!, diente: s.diente, superficies: s.superficies,
          cantidad: s.cantidad ?? 1, valorUnitario: valor } }));
      }
      return plan;
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["planes-tratamiento", pacienteId] }),
    onSuccess: onListo,
  });
  if (sugerencias.isLoading) return <p className="muted">Leyendo el último odontograma…</p>;
  if (sugerencias.error) return <p className="alert alert-error">{sugerencias.error.message}</p>;
  const lista = sugerencias.data?.sugerencias ?? [];
  if (!sugerencias.data?.atencionId) return <p className="muted">El paciente no tiene un odontograma firmado todavía.</p>;
  if (!lista.length) return <p className="muted">El último odontograma no tiene hallazgos que sugieran procedimientos (caries, fracturas o extracciones indicadas).</p>;
  const seleccion = lista.map((s, n) => ({ s, n })).filter(({ n }) => elegidas[n] ?? true);
  const faltaValor = seleccion.some(({ s, n }) => s.valorUnitario == null && numero(valores[n] ?? "") === undefined);
  return (
    <div className="card stack-sm">
      <p className="muted small">Sugerencias del último odontograma. Revise cada una: puede quitarla o cambiarla después de agregarla.</p>
      {lista.map((s, n) => (
        <label key={n} className="row check">
          <input type="checkbox" checked={elegidas[n] ?? true} onChange={(e) => setElegidas({ ...elegidas, [n]: e.target.checked })} />
          <span className="grow"><strong>{s.diente} {s.superficies}</strong> · {s.descripcion} <span className="muted small">({s.motivo})</span></span>
          {s.valorUnitario != null ? <span>{pesos(s.valorUnitario)}</span>
            : <input className="campo-medio" inputMode="numeric" placeholder="Valor" value={valores[n] ?? ""} onChange={(e) => setValores({ ...valores, [n]: e.target.value })} />}
        </label>
      ))}
      {faltaValor && <p className="muted small">Escriba el valor de los procedimientos que no están en la lista de precios.</p>}
      {agregar.error && <p className="alert alert-error">{agregar.error.message}</p>}
      <div className="row">
        <button className="btn" disabled={agregar.isPending || !seleccion.length || faltaValor} onClick={() => agregar.mutate(seleccion.map(({ s }) => s))}>
          Agregar {seleccion.length} al plan
        </button>
        <button type="button" className="btn-link" onClick={onListo}>Cancelar</button>
      </div>
    </div>
  );
}

function Aceptar({ onAceptar, onCancelar, pendiente }: { onAceptar: (f: ValorFirma | null, verbal: boolean) => void; onCancelar: () => void; pendiente: boolean }) {
  const [firma, setFirma] = useState<ValorFirma | null>(null);
  const [verbal, setVerbal] = useState(false);
  return (
    <div className="card stack-sm">
      <strong>Aceptación del paciente</strong>
      <p className="muted small">El paciente (o su representante) firma aquí que acepta el plan y su presupuesto. Lo aceptado queda fijo.</p>
      {!verbal && <FirmaPad valor={firma} onChange={setFirma} ayuda="Firma lista. Guarde la aceptación para conservarla." />}
      <label className="check"><input type="checkbox" checked={verbal} onChange={(e) => setVerbal(e.target.checked)} /> Aceptó de palabra (sin firma)</label>
      <div className="row">
        <button className="btn" disabled={pendiente || (!verbal && !firma?.imagen)} onClick={() => onAceptar(verbal ? null : firma, verbal)}>Guardar aceptación</button>
        <button type="button" className="btn-link" onClick={onCancelar}>Cancelar</button>
      </div>
    </div>
  );
}

function Motivo({ titulo, ayuda, onConfirmar, onCancelar, pendiente }: { titulo: string; ayuda: string; onConfirmar: (m: string) => void; onCancelar: () => void; pendiente: boolean }) {
  const [motivo, setMotivo] = useState("");
  return (
    <form className="card stack-sm" onSubmit={(e) => { e.preventDefault(); onConfirmar(motivo); }}>
      <strong>{titulo}</strong>
      <label>Motivo <input value={motivo} maxLength={300} autoFocus required onChange={(e) => setMotivo(e.target.value)} /></label>
      <span className="muted small">{ayuda}</span>
      <div className="row">
        <button className="btn" disabled={pendiente || !motivo.trim()}>Confirmar</button>
        <button type="button" className="btn-link" onClick={onCancelar}>Volver</button>
      </div>
    </form>
  );
}

function DatosPlan({ plan, onGuardar, onCancelar, pendiente }: { plan: Plan; onGuardar: (o: string | undefined, v: number) => void; onCancelar: () => void; pendiente: boolean }) {
  const [observaciones, setObservaciones] = useState(plan.observaciones ?? "");
  const [vigencia, setVigencia] = useState(String(plan.vigenciaDias ?? 30));
  return (
    <form className="card stack-sm" onSubmit={(e) => { e.preventDefault(); onGuardar(observaciones || undefined, Number(vigencia)); }}>
      <label>Observaciones para el paciente <textarea value={observaciones} maxLength={1000} rows={3} onChange={(e) => setObservaciones(e.target.value)} /></label>
      <label>Vigencia de la cotización (días) <input className="campo-corto" type="number" min={1} max={365} value={vigencia} onChange={(e) => setVigencia(e.target.value)} required /></label>
      <div className="row">
        <button className="btn" disabled={pendiente}>Guardar</button>
        <button type="button" className="btn-link" onClick={onCancelar}>Cancelar</button>
      </div>
    </form>
  );
}
