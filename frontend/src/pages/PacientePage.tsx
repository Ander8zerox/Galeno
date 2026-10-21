import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { api, unwrap } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { RedactorFormula } from "../features/formulacion/RedactorFormula";
import { HistorialFormulas } from "../features/formulacion/HistorialFormulas";
import { ApoyoDiagnostico } from "../features/apoyo/ApoyoDiagnostico";
import { ProximasCitas } from "../features/agenda/ProximasCitas";

type Tab = "formula" | "diagnostico" | "historial";

export function PacientePage() {
  const { id } = useParams<{ id: string }>();
  const { puede } = useAuth();
  const [tab, setTab] = useState<Tab>("formula");
  const paciente = useQuery({
    queryKey: ["paciente", id],
    queryFn: async () => unwrap(await api.GET("/api/v1/pacientes/{id}", { params: { path: { id: id! } } })),
  });

  if (paciente.isLoading) return <p className="muted">Cargando…</p>;
  if (paciente.error || !paciente.data) return <p className="alert alert-error">{paciente.error?.message ?? "Paciente no encontrado"}</p>;
  const p = paciente.data;
  const clinico = puede("HISTORIA_VER");

  return (
    <section>
      <Link to="/pacientes" className="back">← Pacientes</Link>
      <div className="card patient-header">
        <div>
          <h1>{p.nombres} {p.apellidos}</h1>
          <p className="muted">{p.tipoDocumento} {p.numeroDocumento} · {p.edad} años · {p.sexo === "F" ? "Femenino" : p.sexo === "M" ? "Masculino" : "Indeterminado"}</p>
          {(p.telefono || p.email) && <p className="muted small">{[p.telefono, p.email].filter(Boolean).join(" · ")}</p>}
        </div>
        <div className="patient-flags">
          {clinico ? (
            <>
              {p.alergias ? <span className="badge badge-danger">Alergias: {p.alergias}</span> : <span className="badge">Sin alergias registradas</span>}
              {p.antecedentes && <span className="badge">Antecedentes: {p.antecedentes}</span>}
            </>
          ) : (
            <span className="badge">Historia clínica: solo personal médico</span>
          )}
        </div>
      </div>

      {puede("AGENDA_GESTIONAR") && <ProximasCitas pacienteId={p.id!} />}

      {clinico && (
        <>
          <nav className="tabs">
            {puede("FORMULA_REDACTAR") && <button className={tab === "formula" ? "active" : ""} onClick={() => setTab("formula")}>Redactar fórmula</button>}
            {puede("IA_DIAGNOSTICO") && <button className={tab === "diagnostico" ? "active" : ""} onClick={() => setTab("diagnostico")}>Apoyo diagnóstico</button>}
            {puede("FORMULA_REDACTAR") && <button className={tab === "historial" ? "active" : ""} onClick={() => setTab("historial")}>Fórmulas firmadas</button>}
          </nav>
          {tab === "formula" && puede("FORMULA_REDACTAR") && <RedactorFormula pacienteId={p.id!} onFirmada={() => setTab("historial")} />}
          {tab === "diagnostico" && puede("IA_DIAGNOSTICO") && <ApoyoDiagnostico pacienteId={p.id!} />}
          {tab === "historial" && puede("FORMULA_REDACTAR") && <HistorialFormulas pacienteId={p.id!} />}
        </>
      )}
    </section>
  );
}
