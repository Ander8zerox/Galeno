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
  ["AS", "Adulto sin identificación"], ["CN", "Certificado de nacido vivo"],
  ["SC", "Salvoconducto"], ["PE", "Permiso especial de permanencia"], ["CD", "Carné diplomático"],
  ["DE", "Documento extranjero"], ["SI", "Sin identificación"],
];
// SISPRO publica también NV con el mismo nombre que CN: no se ofrece (se usa CN), pero una ficha importada con NV se ve
const NV: [string, string] = ["NV", "Certificado de nacido vivo (NV)"];

/** Teléfonos de Colombia (10 dígitos): celular que empieza por 3 o fijo por 60, con +57 o separadores opcionales. */
const SEP = String.raw`[\s\(\)\.\-]*`;
const PATRON_TELEFONO = String.raw`(\+?57)?` + SEP + "(3(" + SEP + String.raw`\d){9}|6` + SEP + "0(" + SEP + String.raw`\d){8})` + SEP;
const AYUDA_TELEFONO = "Celular de 10 dígitos que empiece por 3, o fijo de 10 que empiece por 60";

const PARENTESCOS = ["Madre", "Padre", "Abuela", "Abuelo", "Tía", "Tío", "Hermana", "Hermano", "Tutor legal", "Acudiente"];

/** Edad cumplida a hoy; null si la fecha no está completa. */
export function edadDe(fechaNacimiento: string | undefined): number | null {
  if (!fechaNacimiento || !/^\d{4}-\d{2}-\d{2}$/.test(fechaNacimiento)) return null;
  const [a, m, d] = fechaNacimiento.split("-").map(Number);
  const hoy = new Date();
  let edad = hoy.getFullYear() - a;
  if (hoy.getMonth() + 1 < m || (hoy.getMonth() + 1 === m && hoy.getDate() < d)) edad--;
  return edad;
}

/** Igual que el servidor (TiposDocumento): CC de 3 a 10 dígitos y TI de 10 u 11, solo números. */
function errorNumero(tipo: string, numero: string): string | null {
  const n = numero.trim();
  if (!n) return null;
  if (tipo === "CC") return /^\d{3,10}$/.test(n) ? null : "La cédula lleva solo números, de 3 a 10 dígitos";
  if (tipo === "TI") return /^\d{10,11}$/.test(n) ? null : "La tarjeta de identidad lleva solo números, 10 u 11 dígitos";
  return /^[A-Za-z0-9-]{1,20}$/.test(n) ? null : "Solo letras, números y guiones, máximo 20";
}

/** La regla RVC007 del RIPS, con su año de tolerancia: RC hasta 7 años, TI de 7 a 17, CC desde 18. */
function errorEdad(tipo: string, edad: number | null): string | null {
  if (edad === null) return null;
  const ok = tipo === "RC" ? edad < 8 : tipo === "TI" ? edad >= 6 && edad <= 18 : tipo === "CC" ? edad >= 17 : true;
  return ok ? null : `Con ${edad} años no corresponde ${tipo}: el RIPS usa RC hasta los 7 años, TI de 7 a 17 y CC desde los 18`;
}

const VACIO: PacienteRequest = {
  tipoDocumento: "CC", numeroDocumento: "", nombres: "", apellidos: "", fechaNacimiento: "", sexo: "F",
  telefono: "", email: "", alergias: "", antecedentes: "", sinCorreosCitas: false, sinCorreo: false,
  responsableNombre: "", responsableParentesco: "", responsableTipoDocumento: "CC", responsableNumeroDocumento: "",
  responsableTelefono: "",
};

function desde(p: Paciente): PacienteRequest {
  return {
    tipoDocumento: p.tipoDocumento!, numeroDocumento: p.numeroDocumento!,
    // Un paciente importado de RIPS trae nombres de relleno: el formulario arranca vacío para escribir los reales
    nombres: p.datosIncompletos ? "" : p.nombres!, apellidos: p.datosIncompletos ? "" : p.apellidos!,
    fechaNacimiento: p.fechaNacimiento!, sexo: p.sexo!, telefono: p.telefono ?? "", email: p.email ?? "",
    alergias: p.alergias ?? "", antecedentes: p.antecedentes ?? "", sinCorreosCitas: !!p.sinCorreosCitas,
    responsableNombre: p.responsable?.nombre ?? "", responsableParentesco: p.responsable?.parentesco ?? "",
    responsableTipoDocumento: p.responsable?.tipoDocumento ?? "CC",
    responsableNumeroDocumento: p.responsable?.numeroDocumento ?? "", responsableTelefono: p.responsable?.telefono ?? "",
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

  const edad = edadDe(form.fechaNacimiento);
  const menor = edad !== null && edad < 18;
  // Solo al crear: el documento no se edita y una ficha antigua no debe quedar sin poder guardarse
  const errorDocumento = paciente ? null : errorNumero(form.tipoDocumento, form.numeroDocumento);
  const errorDocumentoEdad = paciente ? null : errorEdad(form.tipoDocumento, edad);
  const tipos = form.tipoDocumento === "NV" ? [...TIPOS_DOCUMENTO, NV] : TIPOS_DOCUMENTO;

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (errorDocumento || errorDocumentoEdad) return;
    // Los campos opcionales vacíos se envían como ausentes, no como cadenas vacías; sin responsable, ninguno de sus
    // campos (el tipo de documento trae CC por defecto)
    const sinResponsable = !form.responsableNombre?.trim();
    guardar.mutate(Object.fromEntries(Object.entries(form).filter(([k, v]) =>
      v !== "" && !(sinResponsable && k.startsWith("responsable")))) as PacienteRequest);
  }

  return (
    <form className="card form-grid" onSubmit={onSubmit}>
      <p className="small muted wide">Los campos con <span className="req">*</span> son obligatorios.</p>
      <label><span>Tipo de documento <span className="req">*</span></span>
        <select value={form.tipoDocumento} onChange={set("tipoDocumento")} disabled={!!paciente}>
          {tipos.map(([t, nombre]) => <option key={t} value={t}>{t} · {nombre}</option>)}
        </select>
      </label>
      <Campo label="Número de documento" requerido error={errores.numeroDocumento ?? errorDocumento ?? undefined}>
        <input value={form.numeroDocumento} onChange={set("numeroDocumento")} required disabled={!!paciente}
          inputMode={form.tipoDocumento === "CC" || form.tipoDocumento === "TI" ? "numeric" : undefined}
          aria-invalid={!!errorDocumento} />
      </Campo>
      <Campo label="Nombres" requerido error={errores.nombres}><input value={form.nombres} onChange={set("nombres")} required autoFocus={!!paciente} /></Campo>
      <Campo label="Apellidos" requerido error={errores.apellidos}><input value={form.apellidos} onChange={set("apellidos")} required /></Campo>
      <Campo label="Fecha de nacimiento" requerido error={errores.fechaNacimiento ?? errorDocumentoEdad ?? undefined}><input type="date" value={form.fechaNacimiento} onChange={set("fechaNacimiento")} required /></Campo>
      <label><span>Sexo <span className="req">*</span></span>
        <select value={form.sexo} onChange={set("sexo")}>
          <option value="F">Femenino</option><option value="M">Masculino</option><option value="I">Indeterminado</option>
        </select>
      </label>
      <Campo label="Teléfono" requerido error={errores.telefono}>
        <input type="tel" inputMode="tel" value={form.telefono} onChange={set("telefono")} required
          pattern={PATRON_TELEFONO} title={AYUDA_TELEFONO} placeholder="300 123 4567" />
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
      {menor && (
        <fieldset className="wide form-grid">
          <legend>Responsable del menor <span className="req">*</span></legend>
          <p className="small muted wide">Tiene {edad} años: registre quién responde por el paciente (padre, madre, acudiente o tutor).</p>
          {errores.responsableCompleto && <p className="alert alert-error small wide">{errores.responsableCompleto}</p>}
          <Campo label="Nombre completo" requerido error={errores.responsableNombre}>
            <input value={form.responsableNombre} onChange={set("responsableNombre")} required maxLength={200} />
          </Campo>
          <Campo label="Parentesco" requerido error={errores.responsableParentesco}>
            <input list="parentescos" value={form.responsableParentesco} onChange={set("responsableParentesco")} required maxLength={40} />
            <datalist id="parentescos">{PARENTESCOS.map((p) => <option key={p} value={p} />)}</datalist>
          </Campo>
          <label><span>Tipo de documento</span>
            <select value={form.responsableTipoDocumento} onChange={set("responsableTipoDocumento")}>
              {TIPOS_DOCUMENTO.map(([t, nombre]) => <option key={t} value={t}>{t} · {nombre}</option>)}
            </select>
          </label>
          <Campo label="Número de documento" error={errores.responsableNumeroDocumento}>
            <input value={form.responsableNumeroDocumento} onChange={set("responsableNumeroDocumento")} maxLength={30} />
          </Campo>
          <Campo label="Teléfono" requerido error={errores.responsableTelefono}>
            <input type="tel" inputMode="tel" value={form.responsableTelefono} onChange={set("responsableTelefono")} required
              pattern={PATRON_TELEFONO} title={AYUDA_TELEFONO} />
          </Campo>
        </fieldset>
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
