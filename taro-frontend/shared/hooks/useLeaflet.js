import { useEffect, useState } from "react";

let scriptPromise = null;

function ensureStyle(url) {
  if (typeof document === "undefined") {
    return;
  }
  const existing = document.querySelector(`link[data-taro-leaflet="${url}"]`);
  if (existing) {
    return;
  }
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = url;
  link.dataset.taroLeaflet = url;
  document.head.appendChild(link);
}

function ensureLeaflet() {
  if (typeof window === "undefined") {
    return Promise.resolve(false);
  }
  if (window.L) {
    return Promise.resolve(true);
  }
  if (scriptPromise) {
    return scriptPromise;
  }
  ensureStyle("https://unpkg.com/leaflet@1.9.4/dist/leaflet.css");
  scriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
    script.async = true;
    script.onload = () => resolve(Boolean(window.L));
    script.onerror = () => reject(new Error("Failed to load Leaflet"));
    document.head.appendChild(script);
  });
  return scriptPromise;
}

export function useLeaflet() {
  const [ready, setReady] = useState(Boolean(typeof window !== "undefined" && window.L));

  useEffect(() => {
    let active = true;
    ensureLeaflet()
      .then(() => {
        if (active) {
          setReady(true);
        }
      })
      .catch(() => {
        if (active) {
          setReady(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  return { ready };
}
