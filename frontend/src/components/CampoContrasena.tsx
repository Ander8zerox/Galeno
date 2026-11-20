import { useState, type InputHTMLAttributes } from "react";
import { Eye, EyeOff } from "lucide-react";

/** Campo de contraseña con un botón para verla mientras se escribe (evita errores de digitación). */
export function CampoContrasena(props: Omit<InputHTMLAttributes<HTMLInputElement>, "type">) {
  const [visible, setVisible] = useState(false);
  const Icono = visible ? EyeOff : Eye;
  return (
    <span className="campo-contrasena">
      <input {...props} type={visible ? "text" : "password"} />
      <button type="button" className="ver-contrasena" onClick={() => setVisible(!visible)}
        aria-label={visible ? "Ocultar la contraseña" : "Ver la contraseña"} aria-pressed={visible}
        title={visible ? "Ocultar" : "Ver"}>
        <Icono size={18} aria-hidden="true" />
      </button>
    </span>
  );
}
