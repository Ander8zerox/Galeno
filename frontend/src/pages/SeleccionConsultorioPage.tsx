import { useState } from "react";
import { ROL_NOMBRE, useAuth, type ConsultorioOpcion } from "../auth/AuthContext";

export function SeleccionConsultorioPage({ opciones }: { opciones: ConsultorioOpcion[] }) {
  const { elegirConsultorio, logout } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [eligiendo, setEligiendo] = useState<string | null>(null);

  async function elegir(id: string) {
    setEligiendo(id);
    setError(null);
    try {
      await elegirConsultorio(id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "No se pudo entrar al consultorio");
      setEligiendo(null);
    }
  }

  return (
    <div className="login">
      <div className="card login-card">
        <h1>¿Dónde va a trabajar?</h1>
        <p className="muted">Su cuenta tiene acceso a varios consultorios.</p>
        <div className="stack">
          {opciones.map((c) => (
            <button key={c.consultorioId} className="opcion" disabled={!!eligiendo} onClick={() => elegir(c.consultorioId!)}>
              <strong>{c.nombre}</strong>
              <span className="muted small">{c.roles?.map((r) => ROL_NOMBRE[r]).join(" · ")}</span>
            </button>
          ))}
        </div>
        {error && <p className="alert alert-error">{error}</p>}
        <button className="btn-link" onClick={logout}>Usar otra cuenta</button>
      </div>
    </div>
  );
}
