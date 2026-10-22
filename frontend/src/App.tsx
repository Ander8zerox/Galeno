import { useQuery } from "@tanstack/react-query";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { CalendarDays, CreditCard, FileUp, Users, UsersRound } from "lucide-react";
import { Shell, type ItemMenu } from "./components/Shell";
import { api, unwrap } from "./api/client";
import { useAuth } from "./auth/AuthContext";
import { LoginPage } from "./pages/LoginPage";
import { SeleccionConsultorioPage } from "./pages/SeleccionConsultorioPage";
import { InvitacionPage } from "./pages/InvitacionPage";
import { PlataformaApp } from "./pages/PlataformaApp";
import { PartnerApp } from "./pages/PartnerApp";
import { RecuperarPage, RestablecerPage } from "./pages/RecuperarPage";
import { AvisoSuscripcion, SuscripcionPage } from "./pages/SuscripcionPage";
import { BuzonDevPage, ConfirmarRegistroPage, PoliticaDatosPage, RegistroPage } from "./pages/RegistroPage";
import { PacientesPage } from "./pages/PacientesPage";
import { PacientePage } from "./pages/PacientePage";
import { EquipoPage } from "./pages/EquipoPage";
import { AgendaPage } from "./pages/AgendaPage";
import { AtencionPage } from "./pages/AtencionPage";
import { ImportarPage, LoteImportacionPage } from "./pages/ImportarPage";

export function App() {
  const { me, seleccion, logout, puede } = useAuth();
  const location = useLocation();

  // Páginas públicas: quien las abre puede no tener cuenta todavía
  if (location.pathname === "/invitacion") return <InvitacionPage />;
  if (location.pathname.startsWith("/plataforma")) return <PlataformaApp />;
  if (location.pathname.startsWith("/partner")) return <PartnerApp />;
  if (location.pathname === "/registro") return <RegistroPage />;
  if (location.pathname === "/recuperar") return <RecuperarPage />;
  if (location.pathname === "/restablecer") return <RestablecerPage />;
  if (location.pathname === "/registro/confirmar") return <ConfirmarRegistroPage />;
  if (location.pathname === "/politica-datos") return <PoliticaDatosPage />;
  if (import.meta.env.DEV && location.pathname === "/dev/buzon") return <BuzonDevPage />;
  if (!me) return seleccion ? <SeleccionConsultorioPage opciones={seleccion} /> : <LoginPage />;

  const menu: ItemMenu[] = [
    ...(puede("AGENDA_GESTIONAR") ? [{ etiqueta: "Agenda", icono: CalendarDays, to: "/agenda" }] : []),
    { etiqueta: "Pacientes", icono: Users, to: "/pacientes" },
    ...(puede("EQUIPO_GESTIONAR") ? [{ etiqueta: "Equipo", icono: UsersRound, to: "/equipo" }] : []),
    ...(puede("IMPORTAR_HISTORIAS") ? [{ etiqueta: "Importar", icono: FileUp, to: "/importar" }] : []),
    ...(puede("SUSCRIPCION_GESTIONAR") ? [{ etiqueta: "Suscripción", icono: CreditCard, to: "/suscripcion" }] : []),
  ];

  return (
    <Shell marca="Galeno" items={menu} arriba={<SelectorConsultorio />} usuario={me.nombre ?? ""} onSalir={logout}>
        <AvisoSuscripcion />
        <Routes>
          <Route path="/pacientes" element={<PacientesPage />} />
          <Route path="/pacientes/:id" element={<PacientePage />} />
          {puede("HISTORIA_VER") && <Route path="/pacientes/:pacienteId/atenciones/:atencionId" element={<AtencionPage />} />}
          {puede("EQUIPO_GESTIONAR") && <Route path="/equipo" element={<EquipoPage />} />}
          {puede("IMPORTAR_HISTORIAS") && <Route path="/importar" element={<ImportarPage />} />}
          {puede("IMPORTAR_HISTORIAS") && <Route path="/importar/:id" element={<LoteImportacionPage />} />}
          {puede("SUSCRIPCION_GESTIONAR") && <Route path="/suscripcion" element={<SuscripcionPage />} />}
          {puede("AGENDA_GESTIONAR") && <Route path="/agenda" element={<AgendaPage />} />}
          <Route path="*" element={<Navigate to={puede("AGENDA_GESTIONAR") ? "/agenda" : "/pacientes"} replace />} />
        </Routes>
    </Shell>
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

  if (!varios || !opciones.data) return <span className="consultorio-actual">{me?.consultorioNombre}</span>;
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
