import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";

type Clinicos = Schemas["Clinicos"];
type Alergia = Schemas["AlergiaVista"];
type Antecedente = Schemas["AntecedenteVista"];
type TipoAlergia = NonNullable<Alergia["tipo"]>;
type Severidad = NonNullable<Alergia["severidad"]>;
type TipoAntecedente = NonNullable<Antecedente["tipo"]>;

const TIPOS_ALERGIA: [TipoAlergia, string][] = [["MEDICAMENTO", "Medicamento"], ["ALIMENTO", "Alimento"], ["AMBIENTAL", "Ambiental"], ["OTRO", "Otro"]];
const SEVERIDADES: [Severidad, string][] = [["LEVE", "Leve"], ["MODERADA", "Moderada"], ["SEVERA", "Severa"]];
const TIPOS_ANTECEDENTE: [TipoAntecedente, string, string][] = [
  ["PATOLOGICO", "Patológicos", "Hipertensión arterial, diabetes tipo 2…"],
  ["QUIRURGICO", "Quirúrgicos", "Apendicectomía, cesárea…"],
  ["TRAUMATICO", "Traumáticos", "Fractura de radio derecho…"],
  ["FARMACOLOGICO", "Farmacológicos", "Losartán 50 mg cada 12 horas…"],
  ["TOXICO", "Tóxicos", "Tabaquismo, consumo de alcohol…"],
  ["FAMILIAR", "Familiares", "Madre con diabetes…"],
  ["GINECO_OBSTETRICO", "Gineco-obstétricos", "G2P1C1, FUM…"],
  ["INMUNOLOGICO", "Inmunológicos (vacunas)", "Esquema completo, influenza 2026…"],
  ["OTRO", "Otros", ""],
];

/** Alergias y antecedentes del paciente (datos clínicos: solo con HISTORIA_VER). */
export function useClinicos(pacienteId: string, activo = true) {
  return useQuery({
    queryKey: ["clinicos", pacienteId],
    enabled: activo,
    queryFn: async () => unwrap(await api.GET("/api/v1/pacientes/{pacienteId}/clinicos", { params: { path: { pacienteId } } })),
  });
}

/** Resumen corto de alergias para avisos: "Penicilina, maní", "Niega alergias" o "Alergias sin preguntar". */
export function textoAlergias(c?: Clinicos): { texto: string; alerta: boolean } {
  if (!c) return { texto: "", alerta: false };
  const agentes = (c.alergias ?? []).map((a) => a.agente);
  if (c.alergiasTextoAnterior) agentes.push(c.alergiasTextoAnterior);
  if (agentes.length) return { texto: `Alergias: ${agentes.join(", ")}`, alerta: true };
  if (c.alergiasEstado === "NIEGA") return { texto: "Niega alergias conocidas", alerta: false };
  return { texto: "Alergias: sin preguntar", alerta: false };
}

export function AntecedentesPaciente({ pacienteId }: { pacienteId: string }) {
  const clinicos = useClinicos(pacienteId);
  const queryClient = useQueryClient();
  const actualizar = (c: Clinicos) => queryClient.setQueryData(["clinicos", pacienteId], c);
  const p = { path: { pacienteId } };

  const estado = useMutation({
    mutationFn: async (e: "NIEGA" | "SI") =>
      unwrap(await api.PUT("/api/v1/pacientes/{pacienteId}/clinicos/alergias/estado", { params: p, body: { estado: e } })),
    onSuccess: actualizar,
  });
  const borrarTexto = useMutation({
    mutationFn: async (b: { alergias: boolean; antecedentes: boolean }) =>
      unwrap(await api.POST("/api/v1/pacientes/{pacienteId}/clinicos/texto-anterior/borrar", { params: p, body: b })),
    onSuccess: actualizar,
  });

  if (clinicos.isLoading) return <p className="muted">Cargando alergias y antecedentes…</p>;
  if (clinicos.error || !clinicos.data) return <p className="alert alert-error">{clinicos.error?.message}</p>;
  const c = clinicos.data;
  const alergias = c.alergias ?? [];

  return (
    <div className="grid-2">
      <div className="card stack">
        <h3>Alergias</h3>
        {alergias.length === 0 && (
          <div className="row">
            <span className="grow">¿Tiene alergias conocidas?
              {" "}<strong>{c.alergiasEstado === "NIEGA" ? "No (niega)" : c.alergiasEstado === "SI" ? "Sí" : "Sin preguntar"}</strong>
            </span>
            {c.alergiasEstado !== "NIEGA" && <button className="btn btn-secondary" disabled={estado.isPending} onClick={() => estado.mutate("NIEGA")}>No tiene</button>}
            {c.alergiasEstado !== "SI" && <button className="btn btn-secondary" disabled={estado.isPending} onClick={() => estado.mutate("SI")}>Sí tiene</button>}
          </div>
        )}
        {estado.error && <p className="alert alert-error small">{estado.error.message}</p>}
        {alergias.map((a) => <AlergiaFila key={a.id} pacienteId={pacienteId} alergia={a} onCambio={actualizar} />)}
        {(c.alergiasEstado === "SI" || alergias.length > 0) && <AlergiaForm pacienteId={pacienteId} onCambio={actualizar} />}
        {c.alergiasTextoAnterior && (
          <TextoAnterior texto={c.alergiasTextoAnterior} pendiente={borrarTexto.isPending}
            onBorrar={() => borrarTexto.mutate({ alergias: true, antecedentes: false })} />
        )}
      </div>

      <div className="card stack">
        <h3>Antecedentes</h3>
        {(c.antecedentes ?? []).length === 0 && <p className="muted small">Sin antecedentes registrados.</p>}
        {TIPOS_ANTECEDENTE.map(([tipo, nombre]) => {
          const items = (c.antecedentes ?? []).filter((a) => a.tipo === tipo);
          if (!items.length) return null;
          return (
            <div key={tipo} className="stack-sm">
              <strong className="small">{nombre}</strong>
              {items.map((a) => <AntecedenteFila key={a.id} pacienteId={pacienteId} antecedente={a} onCambio={actualizar} />)}
            </div>
          );
        })}
        <AntecedenteForm pacienteId={pacienteId} onCambio={actualizar} />
        {c.antecedentesTextoAnterior && (
          <TextoAnterior texto={c.antecedentesTextoAnterior} pendiente={borrarTexto.isPending}
            onBorrar={() => borrarTexto.mutate({ alergias: false, antecedentes: true })} />
        )}
      </div>
    </div>
  );
}

/** Lo que se escribió antes como texto libre: se pasa a ítems y luego se borra. */
function TextoAnterior({ texto, pendiente, onBorrar }: { texto: string; pendiente: boolean; onBorrar: () => void }) {
  const [confirmando, setConfirmando] = useState(false);
  return (
    <div className="alert alert-warn small stack-sm">
      <span><strong>Registro anterior (texto libre):</strong> {texto}</span>
      <span>Páselo a ítems arriba y luego bórrelo, para que no quede duplicado.</span>
      {confirmando ? (
        <div className="row">
          <span className="grow">¿Ya lo pasó a ítems? El texto se borra.</span>
          <button className="btn" disabled={pendiente} onClick={onBorrar}>Sí, borrar</button>
          <button className="btn-link" onClick={() => setConfirmando(false)}>Volver</button>
        </div>
      ) : <div><button className="btn-link" onClick={() => setConfirmando(true)}>Ya lo pasé: borrar el texto</button></div>}
    </div>
  );
}

function AlergiaFila({ pacienteId, alergia, onCambio }: { pacienteId: string; alergia: Alergia; onCambio: (c: Clinicos) => void }) {
  const [editando, setEditando] = useState(false);
  const quitar = useMutation({
    mutationFn: async () => unwrap(await api.DELETE("/api/v1/pacientes/{pacienteId}/clinicos/alergias/{alergiaId}",
      { params: { path: { pacienteId, alergiaId: alergia.id! } } })),
    onSuccess: onCambio,
  });
  if (editando) return <AlergiaForm pacienteId={pacienteId} alergia={alergia} onCambio={(c) => { setEditando(false); onCambio(c); }} onCancelar={() => setEditando(false)} />;
  return (
    <div className="row">
      <span className="grow">
        <span className={`badge ${alergia.severidad === "SEVERA" ? "badge-danger" : "badge-warn"}`}>{alergia.agente}</span>
        {" "}<span className="small muted">
          {TIPOS_ALERGIA.find(([t]) => t === alergia.tipo)?.[1]}
          {alergia.reaccion && ` · ${alergia.reaccion}`}
          {alergia.severidad && ` · ${SEVERIDADES.find(([s]) => s === alergia.severidad)?.[1].toLowerCase()}`}
        </span>
      </span>
      <button className="btn-link" onClick={() => setEditando(true)}>Editar</button>
      <button className="btn-link danger" disabled={quitar.isPending} onClick={() => quitar.mutate()}>Quitar</button>
    </div>
  );
}

function AlergiaForm({ pacienteId, alergia, onCambio, onCancelar }: {
  pacienteId: string; alergia?: Alergia; onCambio: (c: Clinicos) => void; onCancelar?: () => void;
}) {
  const [tipo, setTipo] = useState<TipoAlergia>(alergia?.tipo ?? "MEDICAMENTO");
  const [agente, setAgente] = useState(alergia?.agente ?? "");
  const [reaccion, setReaccion] = useState(alergia?.reaccion ?? "");
  const [severidad, setSeveridad] = useState<Severidad | "">(alergia?.severidad ?? "");
  const guardar = useMutation({
    mutationFn: async () => {
      const body = { tipo, agente: agente.trim(), reaccion: reaccion.trim() || undefined, severidad: severidad || undefined };
      return alergia
        ? unwrap(await api.PUT("/api/v1/pacientes/{pacienteId}/clinicos/alergias/{alergiaId}", { params: { path: { pacienteId, alergiaId: alergia.id! } }, body }))
        : unwrap(await api.POST("/api/v1/pacientes/{pacienteId}/clinicos/alergias", { params: { path: { pacienteId } }, body }));
    },
    onSuccess: (c) => { setAgente(""); setReaccion(""); setSeveridad(""); onCambio(c); },
  });
  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (agente.trim()) guardar.mutate();
  }
  return (
    <form className="form-grid compacto" onSubmit={onSubmit}>
      <label>Tipo
        <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoAlergia)}>
          {TIPOS_ALERGIA.map(([t, n]) => <option key={t} value={t}>{n}</option>)}
        </select>
      </label>
      <label>Alérgico a<input value={agente} onChange={(e) => setAgente(e.target.value)} maxLength={200} required placeholder="Penicilina, maní, polen…" /></label>
      <label>Reacción<input value={reaccion} onChange={(e) => setReaccion(e.target.value)} maxLength={300} placeholder="Urticaria, anafilaxia…" /></label>
      <label>Severidad
        <select value={severidad} onChange={(e) => setSeveridad(e.target.value as Severidad | "")}>
          <option value="">Sin dato</option>
          {SEVERIDADES.map(([s, n]) => <option key={s} value={s}>{n}</option>)}
        </select>
      </label>
      {guardar.error && <p className="alert alert-error small wide">{guardar.error.message}</p>}
      <div className="wide row">
        <button className="btn btn-secondary" disabled={!agente.trim() || guardar.isPending}>{alergia ? "Guardar" : "Agregar alergia"}</button>
        {onCancelar && <button type="button" className="btn-link" onClick={onCancelar}>Cancelar</button>}
      </div>
    </form>
  );
}

function AntecedenteFila({ pacienteId, antecedente, onCambio }: {
  pacienteId: string; antecedente: Antecedente; onCambio: (c: Clinicos) => void;
}) {
  const [editando, setEditando] = useState(false);
  const quitar = useMutation({
    mutationFn: async () => unwrap(await api.DELETE("/api/v1/pacientes/{pacienteId}/clinicos/antecedentes/{antecedenteId}",
      { params: { path: { pacienteId, antecedenteId: antecedente.id! } } })),
    onSuccess: onCambio,
  });
  if (editando) return <AntecedenteForm pacienteId={pacienteId} antecedente={antecedente} onCambio={(c) => { setEditando(false); onCambio(c); }} onCancelar={() => setEditando(false)} />;
  return (
    <div className="row">
      <span className={`grow ${antecedente.vigente ? "" : "muted"}`}>
        {antecedente.descripcion}
        {antecedente.desde && <span className="small muted"> · desde {antecedente.desde}</span>}
        {!antecedente.vigente && <span className="small"> · no vigente</span>}
      </span>
      <button className="btn-link" onClick={() => setEditando(true)}>Editar</button>
      <button className="btn-link danger" disabled={quitar.isPending} onClick={() => quitar.mutate()}>Quitar</button>
    </div>
  );
}

function AntecedenteForm({ pacienteId, antecedente, onCambio, onCancelar }: {
  pacienteId: string; antecedente?: Antecedente; onCambio: (c: Clinicos) => void; onCancelar?: () => void;
}) {
  const [tipo, setTipo] = useState<TipoAntecedente>(antecedente?.tipo ?? "PATOLOGICO");
  const [descripcion, setDescripcion] = useState(antecedente?.descripcion ?? "");
  const [desde, setDesde] = useState(antecedente?.desde ?? "");
  const [vigente, setVigente] = useState(antecedente?.vigente ?? true);
  const guardar = useMutation({
    mutationFn: async () => {
      const body = { tipo, descripcion: descripcion.trim(), desde: desde.trim() || undefined, vigente };
      return antecedente
        ? unwrap(await api.PUT("/api/v1/pacientes/{pacienteId}/clinicos/antecedentes/{antecedenteId}", { params: { path: { pacienteId, antecedenteId: antecedente.id! } }, body }))
        : unwrap(await api.POST("/api/v1/pacientes/{pacienteId}/clinicos/antecedentes", { params: { path: { pacienteId } }, body }));
    },
    onSuccess: (c) => { setDescripcion(""); setDesde(""); setVigente(true); onCambio(c); },
  });
  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (descripcion.trim()) guardar.mutate();
  }
  const ejemplo = TIPOS_ANTECEDENTE.find(([t]) => t === tipo)?.[2];
  return (
    <form className="form-grid compacto" onSubmit={onSubmit}>
      <label>Tipo
        <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoAntecedente)}>
          {TIPOS_ANTECEDENTE.map(([t, n]) => <option key={t} value={t}>{n}</option>)}
        </select>
      </label>
      <label>Desde<input value={desde} onChange={(e) => setDesde(e.target.value)} maxLength={20} placeholder="2019, infancia…" /></label>
      <label className="wide">Descripción<input value={descripcion} onChange={(e) => setDescripcion(e.target.value)} maxLength={500} required placeholder={ejemplo} /></label>
      <label className="check"><input type="checkbox" checked={vigente} onChange={(e) => setVigente(e.target.checked)} /> Vigente</label>
      {guardar.error && <p className="alert alert-error small wide">{guardar.error.message}</p>}
      <div className="wide row">
        <button className="btn btn-secondary" disabled={!descripcion.trim() || guardar.isPending}>{antecedente ? "Guardar" : "Agregar antecedente"}</button>
        {onCancelar && <button type="button" className="btn-link" onClick={onCancelar}>Cancelar</button>}
      </div>
    </form>
  );
}
