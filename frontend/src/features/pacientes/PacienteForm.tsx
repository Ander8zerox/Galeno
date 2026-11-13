import { useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, ApiError, unwrap, type Schemas } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";

type PacienteRequest = Schemas["PacienteRequest"];
type Paciente = Schemas["PacienteResponse"];

/** Tabla de tipos de documento de MinSalud (los mismos que usa RIPS). */
const TIPOS_DOCUMENTO: [string, string][] = [
  ["CC", "Cédula de ciudadanía"], ["TI", "Tarjeta de identidad"], ["CE", "Cédula de extranjería"], ["PA", "Pasaporte"],
  ["RC", "Registro civil"], ["PT", "Permiso por protección temporal"], ["MS", "Menor sin identificación"],
  ["AS", "Adulto sin identificación"], ["CN", "Certificado de nacido vivo"], ["NV", "Certificado de nacido vivo (NV)"],
  ["SC", "Salvoconducto"], ["PE", "Permiso especial de permanencia"], ["CD", "Carné diplomático"],
  ["DE", "Documento extranjero"], ["SI", "Sin identificación"],
];

const VACIO: PacienteRequest = {
  tipoDocumento: "CC", numeroDocumento: "", nombres: "", apellidos: "", fechaNacimiento: "", sexo: "F",
  telefono: "", email: "", alergias: "", antecedentes: "", sinCorreosCitas: false, sinCorreo: false,
};

function desde(p: Paciente): PacienteRequest {
  return {
    tipoDocumento: p.tipoDocumento!, numeroDocumento: p.numeroDocumento!,
    // Un paciente importado de RIPS trae nombres de relleno: el formulario arranca vacío para escribir los reales
    nombres: p.datosIncompletos ? "" : p.nombres!, apellidos: p.datosIncompletos ? "" : p.apellidos!,
    fechaNacimiento: p.fechaNacimiento!, sexo: p.sexo!, telefono: p.telefono ?? "", email: p.email ?? "",
    alergias: p.alergias ?? "", antecedentes: p.antecedentes ?? "", sinCorreosCitas: !!p.sinCorreosCitas,
    // Sin marcar aunque no tenga correo: al editar se pide el correo o que se confirme que no tiene
    sinCorreo: false,
  };
}

/**
 * Crea un paciente o, si recibe `paciente`, edita sus datos (el documento no se cambia). `inicial` prellena el alta
 * (por ejemplo, con lo que se buscó al agendar) y `onCancelar` muestra el botón para volver.
 */
export function PacienteForm({ paciente, inicial, onGuardado, onCancelar }: {
  paciente?: Paciente;
  inicial?: Partial<PacienteRequest>;
  onGuardado: (p: Paciente) => void;
  onCancelar?: () => void;
}) {
  const clinico = useAuth().puede("HISTORIA_VER");
  const [form, setForm] = useState<PacienteRequest>(paciente ? desde(paciente) : { ...VACIO, ...inicial });
  const queryClient = useQueryClient();
  const guardar = useMutation({
    mutationFn: async (body: PacienteRequest) => paciente
      ? unwrap(await api.PUT("/api/v1/pacientes/{id}", { params: { path: { id: paciente.id! } }, body }))
      : unwrap(await api.POST("/api/v1/pacientes", { body })),
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: ["pacientes"] });
      queryClient.setQueryData(["paciente", p.id], p);
      onGuardado(p);
    },
  });
  const errores = guardar.error instanceof ApiError ? guardar.error.errores : {};
  const errorCorreo = errores.email ?? errores.correoResuelto;
  const set = (campo: keyof PacienteRequest) => (e: { target: { value: string } }) => setForm({ ...form, [campo]: e.target.value });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    // Los campos opcionales vacíos se envían como ausentes, no como cadenas vacías
    guardar.mutate(Object.fromEntries(Object.entries(form).filter(([, v]) => v !== "")) as PacienteRequest);
  }

  return (
    <form className="card form-grid" onSubmit={onSubmit}>
      <p className="small muted wide">Los campos con <span className="req">*</span> son obligatorios.</p>
      <label><span>Tipo de documento <span className="req">*</span></span>
        <select value={form.tipoDocumento} onChange={set("tipoDocumento")} disabled={!!paciente}>
          {TIPOS_DOCUMENTO.map(([t, nombre]) => <option key={t} value={t}>{t} · {nombre}</option>)}
        </select>
      </label>
      <Campo label="Número de documento" requerido error={errores.numeroDocumento}>
        <input value={form.numeroDocumento} onChange={set("numeroDocumento")} required disabled={!!paciente} />
      </Campo>
      <Campo label="Nombres" requerido error={errores.nombres}><input value={form.nombres} onChange={set("nombres")} required autoFocus={!!paciente} /></Campo>
      <Campo label="Apellidos" requerido error={errores.apellidos}><input value={form.apellidos} onChange={set("apellidos")} required /></Campo>
      <Campo label="Fecha de nacimiento" requerido error={errores.fechaNacimiento}><input type="date" value={form.fechaNacimiento} onChange={set("fechaNacimiento")} required /></Campo>
      <label><span>Sexo <span className="req">*</span></span>
        <select value={form.sexo} onChange={set("sexo")}>
          <option value="F">Femenino</option><option value="M">Masculino</option><option value="I">Indeterminado</option>
        </select>
      </label>
      <Campo label="Teléfono" requerido error={errores.telefono}>
        <input type="tel" inputMode="tel" value={form.telefono} onChange={set("telefono")} required
          pattern="\+?[0-9 ()\-]{7,30}" title="Solo números, al menos 7" />
      </Campo>
      {/* Un div, no un label: la casilla lleva su propio label (un label dentro de otro no es válido) */}
      <div className="campo-con-casilla">
        <Campo label="Correo" requerido={!form.sinCorreo} error={errorCorreo}>
          <input type="email" value={form.sinCorreo ? "" : form.email} onChange={set("email")} required={!form.sinCorreo}
            disabled={!!form.sinCorreo} placeholder={form.sinCorreo ? "No tiene correo" : undefined} />
        </Campo>
        <label className="check small">
          <input type="checkbox" checked={!!form.sinCorreo}
            onChange={(e) => setForm({ ...form, sinCorreo: e.target.checked, email: e.target.checked ? "" : form.email })} />
          El paciente no tiene correo
        </label>
      </div>
      {paciente?.correoRechazado && (form.email ?? "").trim().toLowerCase() === (paciente.email ?? "").toLowerCase() && (
        <p className="alert alert-warn small wide">
          Esta dirección rechazó los correos: {paciente.correoRechazado} Corríjala. Si está seguro de que es correcta,
          escriba a soporte para desbloquearla.
        </p>
      )}
      {!!form.email && !form.sinCorreo && (
        <label className="check wide">
          <input type="checkbox" checked={!!form.sinCorreosCitas} onChange={(e) => setForm({ ...form, sinCorreosCitas: e.target.checked })} />
          No enviarle correos sobre sus citas (lo pidió el paciente)
        </label>
      )}
      {clinico && <Campo label="Alergias (separadas por coma)" error={errores.alergias} wide><input value={form.alergias} onChange={set("alergias")} /></Campo>}
      {clinico && <Campo label="Antecedentes" error={errores.antecedentes} wide><textarea rows={2} value={form.antecedentes} onChange={set("antecedentes")} /></Campo>}
      {guardar.error && <p className="alert alert-error wide">{guardar.error.message}</p>}
      <div className="wide row">
        <button className="btn" disabled={guardar.isPending}>{guardar.isPending ? "Guardando…" : paciente ? "Guardar cambios" : "Guardar paciente"}</button>
        {onCancelar && <button type="button" className="btn-link" onClick={onCancelar}>Volver</button>}
      </div>
    </form>
  );
}

function Campo({ label, error, wide, requerido, children }: {
  label: string; error?: string; wide?: boolean; requerido?: boolean; children: React.ReactNode;
}) {
  return (
    <label className={wide ? "wide" : undefined}>
      <span>{label}{requerido && <> <span className="req">*</span></>}</span>
      {children}
      {error && <small className="field-error">{error}</small>}
    </label>
  );
}
