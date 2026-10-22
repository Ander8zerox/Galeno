import { useEffect, useState, type ReactNode } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { LogOut, Menu, X, type LucideIcon } from "lucide-react";

/** Una entrada del menú: navega a una ruta (consultorio) o cambia de pestaña (consola de la plataforma). */
export type ItemMenu = {
  etiqueta: string;
  icono: LucideIcon;
  to?: string;
  activo?: boolean;
  onClick?: () => void;
  contador?: number;
};

/**
 * Estructura de las tres aplicaciones: menú lateral oscuro a la izquierda y el contenido a la derecha.
 * En el celular el menú se oculta y se abre encima del contenido con el botón de la barra superior.
 */
export function Shell({ marca, items, arriba, usuario, onSalir, children }: {
  marca: string;
  items: ItemMenu[];
  arriba?: ReactNode;
  usuario: string;
  onSalir: () => void;
  children: ReactNode;
}) {
  const [abierto, setAbierto] = useState(false);
  const location = useLocation();
  useEffect(() => setAbierto(false), [location.pathname]);
  useEffect(() => {
    if (!abierto) return;
    const cerrar = (e: KeyboardEvent) => e.key === "Escape" && setAbierto(false);
    window.addEventListener("keydown", cerrar);
    return () => window.removeEventListener("keydown", cerrar);
  }, [abierto]);

  return (
    <div className="shell">
      <aside className={`sidebar ${abierto ? "abierto" : ""}`} aria-label="Menú principal">
        <div className="sidebar-marca">
          <span className="logo" aria-hidden="true" />
          <span>{marca}</span>
          <button className="sidebar-cerrar" aria-label="Cerrar menú" onClick={() => setAbierto(false)}><X size={20} /></button>
        </div>
        {arriba && <div className="sidebar-arriba">{arriba}</div>}
        <nav className="sidenav">
          {items.map((i) => {
            const contenido = (
              <>
                <i.icono size={18} strokeWidth={1.75} aria-hidden="true" />
                <span className="grow">{i.etiqueta}</span>
                {!!i.contador && <span className="sidenav-contador">{i.contador}</span>}
              </>
            );
            return i.to ? (
              <NavLink key={i.etiqueta} to={i.to}>{contenido}</NavLink>
            ) : (
              <a key={i.etiqueta} href="#" className={i.activo ? "active" : ""} aria-current={i.activo ? "page" : undefined}
                onClick={(e) => { e.preventDefault(); i.onClick?.(); setAbierto(false); }}>{contenido}</a>
            );
          })}
        </nav>
        <div className="sidebar-pie">
          <span className="sidebar-iniciales" aria-hidden="true">{iniciales(usuario)}</span>
          <span className="grow sidebar-usuario">{usuario}</span>
          <button className="sidebar-salir" onClick={onSalir} aria-label="Salir" title="Salir"><LogOut size={18} /></button>
        </div>
      </aside>
      {abierto && <div className="sidebar-fondo" onClick={() => setAbierto(false)} />}

      <div className="shell-main">
        <header className="barra-movil">
          <button className="barra-movil-menu" aria-label="Abrir menú" aria-expanded={abierto} onClick={() => setAbierto(true)}><Menu size={22} /></button>
          <span className="logo" aria-hidden="true" />
          <span className="barra-movil-marca">{marca}</span>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}

function iniciales(nombre: string): string {
  const partes = nombre.replace(/^(Dra?\.|Sr\.|Sra\.)\s+/i, "").split(/\s+/).filter(Boolean);
  return ((partes[0]?.[0] ?? "") + (partes[1]?.[0] ?? "")).toUpperCase() || "·";
}
