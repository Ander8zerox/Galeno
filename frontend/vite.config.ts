import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// En desarrollo el proxy imita a CloudFront: front y API en el mismo origen (/api -> backend).
// `vite --mode e2e` usa la API de pruebas (esquema galeno_e2e, puerto 8091) sin tocar la base local.
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    port: 5180,
    proxy: {
      "/api": mode === "e2e" ? "http://localhost:8091" : "http://localhost:8090",
    },
  },
}));
