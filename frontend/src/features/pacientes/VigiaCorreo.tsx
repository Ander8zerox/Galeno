import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api, unwrap } from "../../api/client";

/**
 * Avisa si el correo de un paciente rebotó. El servidor de correo del paciente responde después de que SES acepta el
 * envío (segundos o minutos), así que al guardar todavía no se sabe: tras agendar, mover o cancelar una cita, se
 * consulta la ficha del paciente un rato y, si el correo fue rechazado, se muestra el aviso para corregirlo.
 */
const VigiaContext = createContext<(pacienteId: string) => void>(() => {});

const CADA_MS = 3000;
const VECES = 8; // unos 24 segundos

type Aviso = { pacienteId: string; nombre: string; email: string; motivo: string };

export function VigiaCorreoProvider({ children }: { children: ReactNode }) {
  const [aviso, setAviso] = useState<Aviso | null>(null);
  const vigilados = useRef(new Map<string, ReturnType<typeof setTimeout>>());
  const avisados = useRef(new Set<string>()); // una vez por paciente y dirección en esta sesión
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  useEffect(() => () => vigilados.current.forEach(clearTimeout), []);

  const vigilar = useCallback((pacienteId: string) => {
    const previo = vigilados.current.get(pacienteId);
    if (previo) clearTimeout(previo);
    let vez = 0;
    const revisar = async () => {
      vigilados.current.delete(pacienteId);
      let p;
      try {
        p = unwrap(await api.GET("/api/v1/pacientes/{id}", { params: { path: { id: pacienteId } } }));
      } catch {
        return; // sin red o sin permiso: no hay nada que avisar
      }
      if (!p.email || p.sinCorreosCitas) return; // no se le envió nada
      if (p.correoRechazado) {
        queryClient.setQueryData(["paciente", pacienteId], p);
        const clave = `${pacienteId}|${p.email}`;
        if (!avisados.current.has(clave)) {
          avisados.current.add(clave);
          setAviso({ pacienteId, nombre: `${p.nombres} ${p.apellidos}`, email: p.email, motivo: p.correoRechazado });
        }
        return;
      }
      if (++vez < VECES) vigilados.current.set(pacienteId, setTimeout(revisar, CADA_MS));
    };
    // La primera revisión sale de una vez: si la dirección ya estaba bloqueada, no se envió nada y se sabe ya
    vigilados.current.set(pacienteId, setTimeout(revisar, 500));
  }, [queryClient]);

  return (
    <VigiaContext.Provider value={vigilar}>
      {children}
      {aviso && (
        <div className="modal-fondo" onClick={() => setAviso(null)}>
          <div className="modal card stack" role="alertdialog" aria-modal="true" aria-labelledby="vigia-titulo"
            onClick={(e) => e.stopPropagation()}>
            <h3 id="vigia-titulo">No pudimos entregar el correo</h3>
            <p>
              El correo sobre la cita de <strong>{aviso.nombre}</strong> a <strong>{aviso.email}</strong> no llegó.
              {" "}{aviso.motivo}
            </p>
            <p className="muted small">Verifique la dirección con el paciente y corríjala en su ficha. Mientras tanto, no se le enviarán más correos.</p>
            <div className="row">
              <button className="btn" autoFocus onClick={() => { setAviso(null); navigate(`/pacientes/${aviso.pacienteId}?editar=1`); }}>
                Corregir el correo
              </button>
              <button className="btn-link" onClick={() => setAviso(null)}>Cerrar</button>
            </div>
          </div>
        </div>
      )}
    </VigiaContext.Provider>
  );
}

/** Tras una acción que envía un correo al paciente: avisa si rebota. */
export function useVigiaCorreo() {
  return useContext(VigiaContext);
}
