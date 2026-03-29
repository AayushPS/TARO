function nowMs() {
  return typeof performance !== "undefined" && typeof performance.now === "function"
    ? performance.now()
    : Date.now();
}

function joinUrl(baseUrl, path) {
  return new URL(path, baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`).toString();
}

function isJsonResponse(response) {
  const contentType = response.headers.get("content-type") || "";
  return contentType.includes("application/json");
}

function toApiError(payload, fallbackStatus) {
  if (payload && typeof payload === "object") {
    return {
      status: typeof payload.status === "number" ? payload.status : fallbackStatus,
      code: payload.code || "UNKNOWN_ERROR",
      message: payload.message || "Request failed",
      path: payload.path || null,
      timestamp: payload.timestamp || null
    };
  }
  return {
    status: fallbackStatus,
    code: "UNKNOWN_ERROR",
    message: "Request failed",
    path: null,
    timestamp: null
  };
}

export class ApiShapeError extends Error {
  constructor(message, meta = {}) {
    super(message);
    this.name = "ApiShapeError";
    this.meta = meta;
  }
}

export function createTaroClient({ baseUrl, timeoutMs = 15000, callerId, onLog = () => {} }) {
  async function request(method, path, { body, signal, callerScoped = false, expectJson = true } = {}) {
    const startedAt = nowMs();
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort("timeout"), timeoutMs);
    const cleanup = () => clearTimeout(timeout);
    const forwardAbort = () => controller.abort("aborted");

    if (signal) {
      if (signal.aborted) {
        cleanup();
        return {
          ok: false,
          error: {
            status: 0,
            code: "ABORTED",
            message: "Request aborted",
            path,
            timestamp: null
          }
        };
      }
      signal.addEventListener("abort", forwardAbort, { once: true });
    }

    const headers = new Headers();
    if (body !== undefined && body !== null) {
      headers.set("Content-Type", "application/json");
    }
    if (callerScoped && callerId) {
      headers.set("X-Taro-Caller-Id", callerId);
    }

    try {
      const response = await fetch(joinUrl(baseUrl, path), {
        method,
        headers,
        body: body === undefined || body === null ? undefined : JSON.stringify(body),
        signal: controller.signal
      });

      const durationMs = Math.round(nowMs() - startedAt);
      onLog({ type: "http", method, url: path, status: response.status, durationMs });

      if (!expectJson) {
        cleanup();
        return {
          ok: true,
          data: response
        };
      }

      if (!isJsonResponse(response)) {
        cleanup();
        return {
          ok: false,
          error: toApiError(
            {
              code: "API_SHAPE_ERROR",
              message: "Expected application/json response",
              path
            },
            response.status
          )
        };
      }

      const payload = await response.json();
      cleanup();
      if (response.ok) {
        return { ok: true, data: payload };
      }
      return { ok: false, error: toApiError(payload, response.status) };
    } catch (error) {
      cleanup();
      if (error?.name === "AbortError" || error === "timeout" || error === "aborted") {
        return {
          ok: false,
          error: {
            status: 0,
            code: "ABORTED",
            message: "Request aborted",
            path,
            timestamp: null
          }
        };
      }
      return {
        ok: false,
        error: {
          status: 0,
          code: "NETWORK_ERROR",
          message: error?.message || "Network request failed",
          path,
          timestamp: null
        }
      };
    } finally {
      if (signal) {
        signal.removeEventListener("abort", forwardAbort);
      }
    }
  }

  return {
    get(path, signal, options) {
      return request("GET", path, { signal, ...(options || {}) });
    },
    post(path, body, signal, options) {
      return request("POST", path, { body, signal, ...(options || {}) });
    },
    delete(path, signal, options) {
      return request("DELETE", path, { signal, ...(options || {}) });
    },
    async probe(path, signal) {
      const result = await request("GET", path, { signal, expectJson: false });
      if (!result.ok) {
        return {
          available: false,
          status: result.error.status || 0,
          code: result.error.code
        };
      }
      const response = result.data;
      return {
        available: response.status < 400,
        status: response.status,
        contentType: response.headers.get("content-type") || ""
      };
    }
  };
}
