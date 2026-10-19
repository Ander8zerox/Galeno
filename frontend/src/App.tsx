import { useQuery } from "@tanstack/react-query";
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { api, unwrap } from "./api/client";
import { useAuth } from "./auth/AuthContext";
import { LoginPage } from "./pages/LoginPage";
import { SeleccionConsultorioPage } from "./pages/SeleccionConsultorioPage";
import { InvitacionPage } from "./pages/InvitacionPage";
import { PacientesPage } from "./pages/PacientesPage";
import { PacientePage } from "./pages/PacientePage";
import { EquipoPage } from "./pages/EquipoPage";

export function App() {
  const { me, seleccion, logout, puede } = useAuth();
  const location = useLocation();

  // La invitación es pública: quien la abre puede no tener cuenta todavía
  if (location.pathname === "/invitacion") return <InvitacionPage />;
  if (!me) return seleccion ? <SeleccionConsultorioPage opciones={seleccion} /> : <LoginPage />;

  return (
    <div className="layout">
      <header className="topbar">
        <Link to="/pacientes" className="brand">Galeno</Link>
        <SelectorConsultorio />
        <nav className="topnav">
          <Link to="/pacientes">Pacientes</Link>
          {puede("EQUIPO_GESTIONAR") && <Link to="/equipo">Equipo</Link>}
        </nav>
        <span className="spacer" />
        <span>{me.nombre}</span>
        <button className="btn-link" onClick={logout}>Salir</button>
      </header>
      <main className="content">
        <Routes>
          <Route path="/pacientes" element={<PacientesPage />} />
          <Route path="/pacientes/:id" element={<PacientePage />} />
          {puede("EQUIPO_GESTIONAR") && <Route path="/equipo" element={<EquipoPage />} />}
          <Route path="*" element={<Navigate to="/pacientes" replace />} />
        </Routes>
      </main>
    </div>
  );
}

/** Muestra el consultorio actual y, si la persona tiene varios, permite cambiar. */
function SelectorConsultorio() {
  const { me, elegirConsultorio } = useAuth();
  const navigate = useNavigate();
  const varios = (me?.totalConsultorios ?? 0) > 1;
  const opciones = useQuery({
    queryKey: ["mis-consultorios"],
    enabled: varios,
    queryFn: async () => unwrap(await api.GET("/api/v1/auth/consultorios")),
  });

  if (!varios || !opciones.data) return <span className="muted">{me?.consultorioNombre}</span>;
  return (
    <select
      className="consultorio-select"
      aria-label="Consultorio"
      value={me?.consultorioId}
      onChange={async (e) => {
        await elegirConsultorio(e.target.value);
        navigate("/pacientes");
      }}
    >
      {opciones.data.map((c) => <option key={c.consultorioId} value={c.consultorioId}>{c.nombre}</option>)}
    </select>
  );
}
