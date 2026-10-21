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
  telefono: "", email: "", alergias: "", antecedentes: "",
};

function desde(p: Paciente): PacienteRequest {
  return {
    tipoDocumento: p.tipoDocumento!, numeroDocumento: p.numeroDocumento!,
    // Un paciente importado de RIPS trae nombres de relleno: el formulario arranca vacío para escribir los reales
    nombres: p.datosIncompletos ? "" : p.nombres!, apellidos: p.datosIncompletos ? "" : p.apellidos!,
    fechaNacimiento: p.fechaNacimiento!, sexo: p.sexo!, telefono: p.telefono ?? "", email: p.email ?? "",
    alergias: p.alergias ?? "", antecedentes: p.antecedentes ?? "",
  };
}

/** Crea un paciente o, si recibe `paciente`, edita sus datos (el documento no se cambia). */
export function PacienteForm({ paciente, onGuardado }: { paciente?: Paciente; onGuardado: (p: Paciente) => void }) {
  const clinico = useAuth().puede("HISTORIA_VER");
  const [form, setForm] = useState<PacienteRequest>(paciente ? desde(paciente) : VACIO);
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
  const set = (campo: keyof PacienteRequest) => (e: { target: { value: string } }) => setForm({ ...form, [campo]: e.target.value });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    // Los campos opcionales vacíos se envían como ausentes, no como cadenas vacías
    guardar.mutate(Object.fromEntries(Object.entries(form).filter(([, v]) => v !== "")) as PacienteRequest);
  }

  return (
    <form className="card form-grid" onSubmit={onSubmit}>
      <label>Tipo de documento
        <select value={form.tipoDocumento} onChange={set("tipoDocumento")} disabled={!!paciente}>
          {TIPOS_DOCUMENTO.map(([t, nombre]) => <option key={t} value={t}>{t} · {nombre}</option>)}
        </select>
      </label>
      <Campo label="Número de documento" error={errores.numeroDocumento}>
        <input value={form.numeroDocumento} onChange={set("numeroDocumento")} required disabled={!!paciente} />
      </Campo>
      <Campo label="Nombres" error={errores.nombres}><input value={form.nombres} onChange={set("nombres")} required autoFocus={!!paciente} /></Campo>
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
      {guardar.error && <p className="alert alert-error wide">{guardar.error.message}</p>}
      <div className="wide"><button className="btn" disabled={guardar.isPending}>{guardar.isPending ? "Guardando…" : paciente ? "Guardar cambios" : "Guardar paciente"}</button></div>
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
