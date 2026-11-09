import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import { renameSync } from "node:fs";
import { resolve } from "node:path";

/**
 * En producción la landing pública es la raíz (`/` → index.html) y la app es `app.html`: la CloudFront Function
 * (infra/funcion-spa.js) manda a app.html toda ruta sin extensión distinta de `/`. En el código fuente se quedan
 * `index.html` (la app, para que `npm run dev` siga igual) y `landing.html`; aquí se intercambian al terminar el build.
 */
function landingEnLaRaiz(): Plugin {
  let salida = "dist";
  return {
    name: "landing-en-la-raiz",
    apply: "build",
    configResolved(config) {
      salida = resolve(config.root, config.build.outDir);
    },
    closeBundle() {
      renameSync(resolve(salida, "index.html"), resolve(salida, "app.html"));
      renameSync(resolve(salida, "landing.html"), resolve(salida, "index.html"));
    },
  };
}

// En desarrollo el proxy imita a CloudFront: front y API en el mismo origen (/api -> backend). La landing se ve en
// /landing.html. `vite --mode e2e` usa la API de pruebas (esquema galeno_e2e, puerto 8091) sin tocar la base local.
export default defineConfig(({ mode }) => ({
  plugins: [react(), landingEnLaRaiz()],
  server: {
    port: 5180,
    proxy: {
      "/api": mode === "e2e" ? "http://localhost:8091" : "http://localhost:8090",
    },
  },
  build: {
    rollupOptions: {
      input: {
        app: resolve(__dirname, "index.html"),
        landing: resolve(__dirname, "landing.html"),
      },
    },
  },
}));
