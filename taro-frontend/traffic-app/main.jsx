import React from "react";
import ReactDOM from "react-dom/client";
import "@shared/styles/theme.css";
import "./styles.css";
import { ConfigProvider } from "@shared/context/ConfigContext";
import { TrafficApp } from "./App";

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ConfigProvider>
      <TrafficApp />
    </ConfigProvider>
  </React.StrictMode>
);
