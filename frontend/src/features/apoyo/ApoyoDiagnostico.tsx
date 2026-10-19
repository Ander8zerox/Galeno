import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";
import { useAiJob } from "../../ai/useAiJob";

type Sugerencias = Schemas["SugerenciasDiagnosticas"];
type Solicitud = Schemas["SolicitudApoyo"];

const PROBABILIDAD_CLASE: Record<string, string> = { ALTA: "badge-danger", MEDIA: "badge-warn", BAJA: "" };

export function ApoyoDiagnostico({ pacienteId }: { pacienteId: string }) {
  const [form, setForm] = useState<Omit<Solicitud, "pacienteId">>({ motivoConsulta: "", enfermedadActual: "", signosVitales: "", examenFisico: "" });
  const [jobId, setJobId] = useState<string>();
  const solicitar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/apoyo-diagnostico/sugerencias", { body: { pacienteId, ...form } })),
    onSuccess: (r) => setJobId(r.jobId),
  });
  const job = useAiJob<Sugerencias>(jobId);
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    solicitar.mutate();
  }

  const r = job.resultado;
  return (
    <div className="stack">
      <form className="card form-grid" onSubmit={onSubmit}>
        <label className="wide">Motivo de consulta *<input value={form.motivoConsulta} onChange={set("motivoConsulta")} required /></label>
        <label className="wide">Enfermedad actual *<textarea rows={3} value={form.enfermedadActual} onChange={set("enfermedadActual")} required /></label>
        <label>Signos vitales<input placeholder="TA, FC, FR, T°, SatO2" value={form.signosVitales} onChange={set("signosVitales")} /></label>
        <label>Examen físico<textarea rows={2} value={form.examenFisico} onChange={set("examenFisico")} /></label>
        <div className="wide">
          <button className="btn" disabled={solicitar.isPending || job.enCurso}>{job.enCurso ? "Analizando el caso…" : "Sugerir diagnósticos"}</button>
        </div>
        {(solicitar.error || job.error) && <p className="alert alert-error wide">{solicitar.error?.message ?? job.error}</p>}
      </form>

      {r && (
        <div className="card stack">
          <p className="alert">Sugerencias de apoyo a la decisión clínica. El diagnóstico lo establece el médico tratante.</p>
          <h3>Diagnósticos diferenciales</h3>
          {r.diferenciales?.map((d, i) => (
            <div key={i} className="dx">
              <div className="row">
                <strong>{d.codigoCie10}</strong> {d.descripcion}
                <span className={`badge ${PROBABILIDAD_CLASE[d.probabilidad ?? ""] ?? ""}`}>{d.probabilidad}</span>
              </div>
              <p className="muted small">{d.razonamiento}</p>
            </div>
          ))}
          {!!r.datosFaltantes?.length && (<><h3>Datos que ayudarían a precisar</h3><ul>{r.datosFaltantes.map((x, i) => <li key={i}>{x}</li>)}</ul></>)}
          {!!r.signosDeAlarma?.length && (<><h3>Signos de alarma</h3><ul className="warnings">{r.signosDeAlarma.map((x, i) => <li key={i} className="danger">{x}</li>)}</ul></>)}
        </div>
      )}
    </div>
  );
}
