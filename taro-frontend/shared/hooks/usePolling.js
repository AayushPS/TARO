import { useCallback, useEffect, useRef, useState } from "react";

export function usePolling(fetcher, intervalMs, { paused = false, immediate = true } = {}) {
  const mountedRef = useRef(true);
  const timeoutRef = useRef(null);
  const pausedRef = useRef(paused);
  const [state, setState] = useState({
    status: "idle",
    data: null,
    error: null
  });

  const clearTimer = useCallback(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  }, []);

  const run = useCallback(async () => {
    if (pausedRef.current || !mountedRef.current) {
      return;
    }
    setState((current) => ({
      ...current,
      status: current.status === "idle" ? "loading" : current.status
    }));
    const result = await fetcher();
    if (!mountedRef.current) {
      return;
    }
    if (result?.ok === false) {
      setState((current) => ({
        ...current,
        status: "error",
        error: result.error
      }));
    } else {
      setState({
        status: "success",
        data: result?.data ?? result,
        error: null
      });
    }
    clearTimer();
    if (!pausedRef.current && mountedRef.current) {
      timeoutRef.current = setTimeout(run, intervalMs);
    }
  }, [clearTimer, fetcher, intervalMs]);

  const pause = useCallback(() => {
    pausedRef.current = true;
    clearTimer();
  }, [clearTimer]);

  const resume = useCallback(() => {
    if (!mountedRef.current) {
      return;
    }
    pausedRef.current = false;
    clearTimer();
    timeoutRef.current = setTimeout(run, 0);
  }, [clearTimer, run]);

  useEffect(() => {
    pausedRef.current = paused;
    if (paused) {
      clearTimer();
      return undefined;
    }
    if (immediate) {
      timeoutRef.current = setTimeout(run, 0);
    } else {
      timeoutRef.current = setTimeout(run, intervalMs);
    }
    return clearTimer;
  }, [clearTimer, immediate, intervalMs, paused, run]);

  useEffect(() => () => {
    mountedRef.current = false;
    clearTimer();
  }, [clearTimer]);

  return {
    ...state,
    refresh: run,
    pause,
    resume
  };
}
