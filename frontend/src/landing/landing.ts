// Landing pública (landing.html → index.html en el build). Sin React: solo el menú del celular y el año del pie.
const boton = document.querySelector<HTMLButtonElement>(".menu-boton");
const barra = document.getElementById("barra");

boton?.addEventListener("click", () => {
  const abierto = barra?.classList.toggle("abierta") ?? false;
  boton.setAttribute("aria-expanded", String(abierto));
  boton.setAttribute("aria-label", abierto ? "Cerrar menú" : "Abrir menú");
});

// Al elegir una sección en el menú del celular, se cierra
document.querySelectorAll<HTMLAnchorElement>(".menu a").forEach((a) =>
  a.addEventListener("click", () => {
    barra?.classList.remove("abierta");
    boton?.setAttribute("aria-expanded", "false");
  }),
);

// Sombra en la barra al bajar
const sombra = () => barra?.classList.toggle("con-sombra", window.scrollY > 8);
window.addEventListener("scroll", sombra, { passive: true });
sombra();

const anio = document.getElementById("anio");
if (anio) anio.textContent = String(new Date().getFullYear());
