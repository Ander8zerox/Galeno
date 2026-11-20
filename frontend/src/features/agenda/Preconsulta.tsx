import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError, unwrap, type Schemas } from "../../api/client";
import { AntecedentesPaciente } from "../pacientes/AntecedentesPaciente";

type Request = Schemas["PreconsultaRequest"];

const VACIO = {
  motivoConsulta: "", taSistolica: "", taDiastolica: "", frecuenciaCardiaca: "", frecuenciaRespiratoria: "",
  temperatura: "", saturacionO2: "", pesoKg: "", tallaCm: "", medicamentosActuales: "", observaciones: "",
};
type Form = typeof VACIO;

const NUMEROS: [keyof Form, string, string][] = [
  ["taSistolica", "TA sistólica", "mmHg"], ["taDiastolica", "TA diastólica", "mmHg"],
  ["frecuenciaCardiaca", "Frecuencia cardiaca", "lpm"], ["frecuenciaRespiratoria", "Frecuencia respiratoria", "rpm"],
  ["temperatura", "Temperatura", "°C"], ["saturacionO2", "Saturación O₂", "%"],
  ["pesoKg", "Peso", "kg"], ["tallaCm", "Talla", "cm"],
];

/**
 * Preconsulta de una cita: motivo, signos vitales, medicamentos actuales, alergias y antecedentes. La atención de la
 * cita arranca con estos datos.
 */
export function Preconsulta({ citaId, pacienteId, pacienteNombre, onCerrar }: {
  citaId: string; pacienteId: string; pacienteNombre?: string; onCerrar: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Form>(VACIO);
  const actual = useQuery({
    queryKey: ["preconsulta", citaId],
    queryFn: async () => unwrap(await api.GET("/api/v1/preconsulta/citas/{citaId}", { params: { path: { citaId } } })) ?? null,
  });
  useEffect(() => {
    const d = actual.data?.datos;
    if (!d) return;
    setForm(Object.fromEntries(Object.keys(VACIO).map((k) => [k, d[k as keyof typeof d] == null ? "" : String(d[k as keyof typeof d])])) as Form);
  }, [actual.data]);

  const guardar = useMutation({
    mutationFn: async () => {
      const body = Object.fromEntries(Object.entries(form).map(([k, v]) => {
        const t = v.trim();
        if (!t) return [k, undefined];
        return [k, NUMEROS.some(([n]) => n === k) ? Number(t.replace(",", ".")) : t];
      })) as Request;
      return unwrap(await api.PUT("/api/v1/preconsulta/citas/{citaId}", { params: { path: { citaId } }, body }));
    },
    onSuccess: (v) => {
      queryClient.setQueryData(["preconsulta", citaId], v);
      queryClient.invalidateQueries({ queryKey: ["citas"] });
      onCerrar();
    },
  });
  const errores = guardar.error instanceof ApiError ? guardar.error.errores : {};
  const set = (k: keyof Form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    guardar.mutate();
  }

  return (
    <div className="card stack preconsulta">
      <div className="row between">
        <h3>Preconsulta{pacienteNombre && `: ${pacienteNombre}`}</h3>
        <button type="button" className="btn-link" onClick={onCerrar}>Cerrar</button>
      </div>
      {actual.data && <p className="small muted">Registrada por {actual.data.registradaPor}. Puede corregirla hasta que el médico abra la atención.</p>}
      <form className="form-grid" onSubmit={onSubmit}>
        <label className="wide">Motivo de consulta (en palabras del paciente)
          <textarea rows={2} value={form.motivoConsulta} onChange={set("motivoConsulta")} maxLength={2000} />
        </label>
        {NUMEROS.map(([k, label, unidad]) => (
          <label key={k}>{label} <span className="muted small">({unidad})</span>
            <input inputMode="decimal" value={form[k]} onChange={set(k)} />
            {errores[k] && <small className="field-error">{errores[k]}</small>}
          </label>
        ))}
        <label className="wide">Medicamentos que toma actualmente
          <textarea rows={2} value={form.medicamentosActuales} onChange={set("medicamentosActuales")} maxLength={2000}
            placeholder="Losartán 50 mg cada 12 horas…" />
        </label>
        <label className="wide">Observaciones para el médico
          <textarea rows={2} value={form.observaciones} onChange={set("observaciones")} maxLength={2000} />
        </label>
        {guardar.error && <p className="alert alert-error wide">{guardar.error.message}</p>}
        <div className="wide row">
          <button className="btn" disabled={guardar.isPending}>{guardar.isPending ? "Guardando…" : "Guardar preconsulta"}</button>
          <button type="button" className="btn-link" onClick={onCerrar}>Cancelar</button>
        </div>
      </form>
      <AntecedentesPaciente pacienteId={pacienteId} />
    </div>
  );
}
