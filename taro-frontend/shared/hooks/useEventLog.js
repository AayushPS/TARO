import { useCallback, useState } from "react";

function makeEntry(entry) {
  return {
    id: entry.id || `log-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    timestamp: entry.timestamp || new Date().toISOString(),
    ...entry
  };
}

export function useEventLog(maxEntries) {
  const [entries, setEntries] = useState([]);

  const append = useCallback((entry) => {
    const normalized = makeEntry(entry);
    setEntries((current) => {
      const next = [...current, normalized];
      return next.length > maxEntries ? next.slice(next.length - maxEntries) : next;
    });
    return normalized;
  }, [maxEntries]);

  const clear = useCallback(() => {
    setEntries([]);
  }, []);

  return {
    entries,
    append,
    clear
  };
}
