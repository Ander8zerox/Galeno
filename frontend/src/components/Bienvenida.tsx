import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

// En sessionStorage: solo en la pestaña donde se activó el consultorio, y se va al cerrarla. Si el navegador no deja
// guardarlo (modo privado estricto), simplemente no se muestra.
const CLAVE = "galenum-bienvenida";

/** Lo llama la activación del consultorio, antes de ir a la página de inicio. */
export function marcarBienvenida() {
  try {
    sessionStorage.setItem(CLAVE, "1");
  } catch {
    /* sin almacenamiento: no hay bienvenida */
  }
}

function pendiente() {
  try {
    return sessionStorage.getItem(CLAVE) === "1";
  } catch {
    return false;
  }
}

/** Primeros pasos de un consultorio recién activado: se muestra hasta que la persona lo cierra. */
export function Bienvenida() {
  const { puede } = useAuth();
  const [visible, setVisible] = useState(pendiente);
  if (!visible) return null;

  function cerrar() {
    try {
      sessionStorage.removeItem(CLAVE);
    } catch {
      /* nada que limpiar */
    }
    setVisible(false);
  }

  return (
    <div className="card stack bienvenida">
      <h3>¡Bienvenido a Galenum!</h3>
      <p>Su prueba gratis de 30 días empezó hoy. Estos son los primeros pasos:</p>
      <ol>
        <li><Link to="/pacientes">Cree su primer paciente</Link>.</li>
        {puede("AGENDA_GESTIONAR") && <li><Link to="/agenda">Agende una cita</Link> y atiéndala desde la agenda.</li>}
        {puede("EQUIPO_GESTIONAR") && <li><Link to="/equipo">Invite a su equipo</Link>: otros médicos o la secretaria.</li>}
      </ol>
      <div><button className="btn btn-secondary" onClick={cerrar}>Entendido</button></div>
    </div>
  );
}
