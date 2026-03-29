import { useCallback, useEffect, useRef, useState } from "react";

export function useAbortableFetch(fetcher) {
  const controllerRef = useRef(null);
  const requestIdRef = useRef(0);
  const [state, setState] = useState({
    status: "idle",
    data: null,
    error: null
  });

  const abort = useCallback(() => {
    controllerRef.current?.abort();
    controllerRef.current = null;
  }, []);

  const execute = useCallback(async (...args) => {
    abort();
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const controller = new AbortController();
    controllerRef.current = controller;
    setState((current) => ({
      ...current,
      status: "loading",
      error: null
    }));

    try {
      const result = await fetcher(controller.signal, ...args);
      if (requestId !== requestIdRef.current) {
        return result;
      }
      if (result?.ok === false) {
        setState({
          status: "error",
          data: null,
          error: result.error
        });
      } else {
        setState({
          status: "success",
          data: result,
          error: null
        });
      }
      return result;
    } catch (error) {
      if (controller.signal.aborted) {
        return {
          ok: false,
          error: {
            status: 0,
            code: "ABORTED",
            message: "Request aborted"
          }
        };
      }
      const normalizedError = {
        status: 0,
        code: "UNEXPECTED_ERROR",
        message: error?.message || "Unexpected request failure"
      };
      if (requestId === requestIdRef.current) {
        setState({
          status: "error",
          data: null,
          error: normalizedError
        });
      }
      return {
        ok: false,
        error: normalizedError
      };
    }
  }, [abort, fetcher]);

  useEffect(() => abort, [abort]);

  return {
    ...state,
    execute,
    abort
  };
}
