import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api, unwrap } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { PacienteForm } from "../features/pacientes/PacienteForm";

export function PacientesPage() {
  const [q, setQ] = useState("");
  const [creando, setCreando] = useState(false);
  const navigate = useNavigate();
  const clinico = useAuth().puede("HISTORIA_VER");

  const pacientes = useQuery({
    queryKey: ["pacientes", q],
    queryFn: async () => unwrap(await api.GET("/api/v1/pacientes", { params: { query: { q: q || undefined, size: 50 } } })),
  });

  return (
    <section>
      <div className="page-header">
        <h1>Pacientes</h1>
        <button className="btn" onClick={() => setCreando((v) => !v)}>{creando ? "Cancelar" : "Nuevo paciente"}</button>
      </div>

      {creando && <PacienteForm onGuardado={(p) => navigate(`/pacientes/${p.id}`)} />}

      <input className="search" placeholder="Buscar por nombre o documento…" value={q} onChange={(e) => setQ(e.target.value)} />

      <div className="card table-wrap">
        <table>
          <thead>
            <tr><th>Documento</th><th>Nombre</th><th>Edad</th>{clinico && <th>Alergias</th>}</tr>
          </thead>
          <tbody>
            {pacientes.data?.items?.map((p) => (
              <tr key={p.id} className="clickable" onClick={() => navigate(`/pacientes/${p.id}`)}>
                <td>{p.tipoDocumento} {p.numeroDocumento}</td>
                <td>{p.nombres} {p.apellidos}{p.datosIncompletos && <> <span className="badge badge-warn">Datos incompletos</span></>}</td>
                <td>{p.edad}</td>
                {clinico && <td>{p.alergias ? <span className="badge badge-danger">{p.alergias}</span> : <span className="muted">—</span>}</td>}
              </tr>
            ))}
            {pacientes.data?.items?.length === 0 && (
              <tr><td colSpan={clinico ? 4 : 3} className="muted center">No hay pacientes{q ? " con ese criterio" : " registrados"}.</td></tr>
            )}
          </tbody>
        </table>
        {pacientes.isLoading && <p className="muted center">Cargando…</p>}
        {pacientes.error && <p className="alert alert-error">{pacientes.error.message}</p>}
      </div>
    </section>
  );
}
