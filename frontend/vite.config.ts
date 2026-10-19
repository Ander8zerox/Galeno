import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// En desarrollo el proxy imita a CloudFront: front y API en el mismo origen (/api -> backend).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5180,
    proxy: {
      "/api": "http://localhost:8090",
    },
  },
});
