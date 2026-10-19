import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";
import { useAiJob } from "../../ai/useAiJob";

type Borrador = Schemas["BorradorFormula"];
type Item = Schemas["ItemRequest"];

const CAMPOS: { key: keyof Item; label: string; requerido?: boolean }[] = [
  { key: "medicamento", label: "Medicamento", requerido: true },
  { key: "concentracion", label: "Concentración" },
  { key: "formaFarmaceutica", label: "Forma" },
  { key: "via", label: "Vía" },
  { key: "dosis", label: "Dosis", requerido: true },
  { key: "frecuencia", label: "Frecuencia", requerido: true },
  { key: "duracion", label: "Duración", requerido: true },
  { key: "cantidad", label: "Cantidad" },
  { key: "indicaciones", label: "Indicaciones" },
];

const ITEM_VACIO: Item = { medicamento: "", dosis: "", frecuencia: "", duracion: "" };

/** Flujo: el médico escribe en texto libre → la IA estructura → el médico revisa/edita → firma. */
export function RedactorFormula({ pacienteId, onFirmada }: { pacienteId: string; onFirmada: () => void }) {
  const [texto, setTexto] = useState("");
  const [diagnostico, setDiagnostico] = useState("");
  const [jobId, setJobId] = useState<string>();
  const [items, setItems] = useState<Item[]>([]);
  const [advertencias, setAdvertencias] = useState<string[]>([]);
  const [observaciones, setObservaciones] = useState("");
  const queryClient = useQueryClient();

  const solicitar = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST("/api/v1/formulacion/borradores", { body: { pacienteId, texto, diagnostico: diagnostico || undefined } })),
    onSuccess: (r) => setJobId(r.jobId),
  });
  const job = useAiJob<Borrador>(jobId);

  // Cuando la IA termina, su propuesta pasa a ser el borrador editable del médico.
  useEffect(() => {
    if (!job.resultado) return;
    setItems((job.resultado.items ?? []).map((i) => ({ ...ITEM_VACIO, ...i }) as Item));
    setAdvertencias(job.resultado.advertencias ?? []);
  }, [job.resultado]);

  const firmar = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST("/api/v1/formulacion/formulas", {
        body: { pacienteId, aiJobId: jobId, observaciones: observaciones || undefined, items },
      })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["formulas", pacienteId] });
      onFirmada();
    },
  });

  const actualizar = (idx: number, key: keyof Item, value: string) =>
    setItems(items.map((it, i) => (i === idx ? { ...it, [key]: value } : it)));
  const completa = items.length > 0 && items.every((it) => CAMPOS.every((c) => !c.requerido || (it[c.key] ?? "").trim()));

  return (
    <div className="stack">
      <div className="card stack">
        <label>Indicación (texto libre, como la escribiría en la historia)
          <textarea rows={3} placeholder="Ej.: amoxicilina 500 c/8h x 7 días, acetaminofén 500 c/6h si dolor"
            value={texto} onChange={(e) => setTexto(e.target.value)} />
        </label>
        <label>Diagnóstico (opcional)
          <input placeholder="Ej.: J02.9 Faringitis aguda" value={diagnostico} onChange={(e) => setDiagnostico(e.target.value)} />
        </label>
        <div className="row">
          <button className="btn" disabled={!texto.trim() || solicitar.isPending || job.enCurso} onClick={() => solicitar.mutate()}>
            {job.enCurso ? "La IA está redactando…" : "Redactar con IA"}
          </button>
          <button className="btn btn-secondary" onClick={() => setItems([...items, { ...ITEM_VACIO }])}>Agregar ítem manual</button>
        </div>
        {(solicitar.error || job.error) && <p className="alert alert-error">{solicitar.error?.message ?? job.error}</p>}
      </div>

      {advertencias.length > 0 && (
        <div className="card">
          <h3>Revise antes de firmar</h3>
          <ul className="warnings">
            {advertencias.map((a, i) => <li key={i} className={a.startsWith("ALERGIA") ? "danger" : ""}>{a}</li>)}
          </ul>
        </div>
      )}

      {items.length > 0 && (
        <div className="card stack">
          <h3>Fórmula</h3>
          <p className="muted small">La IA propone; usted revisa, corrige y firma. Nada se guarda hasta que firme.</p>
          {items.map((item, idx) => (
            <div key={idx} className="item-grid">
              {CAMPOS.map((c) => (
                <label key={c.key} className={c.key === "indicaciones" ? "wide" : undefined}>
                  {c.label}{c.requerido && " *"}
                  <input value={item[c.key] ?? ""} onChange={(e) => actualizar(idx, c.key, e.target.value)} />
                </label>
              ))}
              <button className="btn-link danger" onClick={() => setItems(items.filter((_, i) => i !== idx))}>Quitar</button>
            </div>
          ))}
          <label>Observaciones
            <input value={observaciones} onChange={(e) => setObservaciones(e.target.value)} />
          </label>
          {firmar.error && <p className="alert alert-error">{firmar.error.message}</p>}
          <div>
            <button className="btn" disabled={!completa || firmar.isPending} onClick={() => firmar.mutate()}>
              {firmar.isPending ? "Firmando…" : "Firmar fórmula"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
