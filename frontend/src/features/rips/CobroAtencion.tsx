import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, unwrap } from "../../api/client";
import { CobroForm, faltaAutorizacion, resumenCobro } from "./CobroForm";
import { useReferencia } from "./referencias";

const ORIGEN = { ATENCION: "registrado en la atención", CITA: "de la cita", PACIENTE: "pagador del paciente, sin registrar" };

/**
 * Pagador y cobro de la atención: lo que registró la secretaria en la cita, o se registra aquí. Como no es dato
 * clínico, se puede corregir aun con la atención firmada.
 */
export function CobroAtencion({ atencionId, editable }: { atencionId: string; editable: boolean }) {
  const [editando, setEditando] = useState(false);
  const conceptos = useReferencia("concepto_recaudo");
  const cobro = useQuery({
    queryKey: ["rips-cobro", atencionId],
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/cobros/atenciones/{atencionId}", { params: { path: { atencionId } } })),
  });
  const c = cobro.data;
  if (!c) return null;
  return (
    <div className="card stack-sm">
      <div className="row between">
        <h3>Pagador y cobro {faltaAutorizacion(c) && <span className="badge badge-warn">Falta autorización</span>}</h3>
        {editable && !editando && <button className="btn-link" onClick={() => setEditando(true)}>{c.pagadorId ? "Cambiar" : "Registrar"}</button>}
      </div>
      {editando
        ? <CobroForm donde="atencion" id={atencionId} cobro={c} onGuardado={() => setEditando(false)} onCancelar={() => setEditando(false)} />
        : <p className="small">{c.pagadorId ? resumenCobro(c, conceptos.data) : <span className="muted">Sin pagador</span>}
            {c.pagadorId && <span className="muted"> · {ORIGEN[c.origen!]}</span>}</p>}
    </div>
  );
}
