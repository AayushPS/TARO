import path from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const rootDir = path.resolve(__dirname);

export default defineConfig({
  root: rootDir,
  plugins: [react()],
  resolve: {
    alias: {
      "@shared": path.resolve(rootDir, "shared"),
      "@maps": path.resolve(rootDir, "maps-app"),
      "@traffic": path.resolve(rootDir, "traffic-app")
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: "dist",
    rollupOptions: {
      input: {
        index: path.resolve(rootDir, "index.html"),
        maps: path.resolve(rootDir, "maps-app/index.html"),
        traffic: path.resolve(rootDir, "traffic-app/index.html")
      }
    }
  },
  test: {
    environment: "jsdom",
    globals: true
  }
});
