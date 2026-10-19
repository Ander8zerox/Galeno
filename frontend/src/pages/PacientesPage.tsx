import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api, ApiError, unwrap, type Schemas } from "../api/client";
import { useAuth } from "../auth/AuthContext";

type PacienteRequest = Schemas["PacienteRequest"];

const VACIO: PacienteRequest = {
  tipoDocumento: "CC", numeroDocumento: "", nombres: "", apellidos: "", fechaNacimiento: "", sexo: "F",
  telefono: "", email: "", alergias: "", antecedentes: "",
};

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

      {creando && <NuevoPaciente onCreado={(id) => navigate(`/pacientes/${id}`)} />}

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
                <td>{p.nombres} {p.apellidos}</td>
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

function NuevoPaciente({ onCreado }: { onCreado: (id: string) => void }) {
  const clinico = useAuth().puede("HISTORIA_VER");
  const [form, setForm] = useState<PacienteRequest>(VACIO);
  const queryClient = useQueryClient();
  const crear = useMutation({
    mutationFn: async (body: PacienteRequest) => unwrap(await api.POST("/api/v1/pacientes", { body })),
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: ["pacientes"] });
      if (p.id) onCreado(p.id);
    },
  });
  const errores = crear.error instanceof ApiError ? crear.error.errores : {};
  const set = (campo: keyof PacienteRequest) => (e: { target: { value: string } }) => setForm({ ...form, [campo]: e.target.value });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    // Los campos opcionales vacíos se envían como ausentes, no como cadenas vacías
    crear.mutate(Object.fromEntries(Object.entries(form).filter(([, v]) => v !== "")) as PacienteRequest);
  }

  return (
    <form className="card form-grid" onSubmit={onSubmit}>
      <label>Tipo de documento
        <select value={form.tipoDocumento} onChange={set("tipoDocumento")}>
          {["CC", "TI", "CE", "PA", "RC", "PT"].map((t) => <option key={t}>{t}</option>)}
        </select>
      </label>
      <Campo label="Número de documento" error={errores.numeroDocumento}><input value={form.numeroDocumento} onChange={set("numeroDocumento")} required /></Campo>
      <Campo label="Nombres" error={errores.nombres}><input value={form.nombres} onChange={set("nombres")} required /></Campo>
      <Campo label="Apellidos" error={errores.apellidos}><input value={form.apellidos} onChange={set("apellidos")} required /></Campo>
      <Campo label="Fecha de nacimiento" error={errores.fechaNacimiento}><input type="date" value={form.fechaNacimiento} onChange={set("fechaNacimiento")} required /></Campo>
      <label>Sexo
        <select value={form.sexo} onChange={set("sexo")}>
          <option value="F">Femenino</option><option value="M">Masculino</option><option value="I">Indeterminado</option>
        </select>
      </label>
      <Campo label="Teléfono" error={errores.telefono}><input value={form.telefono} onChange={set("telefono")} /></Campo>
      <Campo label="Correo" error={errores.email}><input type="email" value={form.email} onChange={set("email")} /></Campo>
      {clinico && <Campo label="Alergias (separadas por coma)" error={errores.alergias} wide><input value={form.alergias} onChange={set("alergias")} /></Campo>}
      {clinico && <Campo label="Antecedentes" error={errores.antecedentes} wide><textarea rows={2} value={form.antecedentes} onChange={set("antecedentes")} /></Campo>}
      {crear.error && <p className="alert alert-error wide">{crear.error.message}</p>}
      <div className="wide"><button className="btn" disabled={crear.isPending}>{crear.isPending ? "Guardando…" : "Guardar paciente"}</button></div>
    </form>
  );
}

function Campo({ label, error, wide, children }: { label: string; error?: string; wide?: boolean; children: React.ReactNode }) {
  return (
    <label className={wide ? "wide" : undefined}>
      {label}
      {children}
      {error && <small className="field-error">{error}</small>}
    </label>
  );
}
