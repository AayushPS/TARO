export function relativeTime(iso) {
  if (!iso) {
    return "n/a";
  }
  const value = new Date(iso);
  if (Number.isNaN(value.getTime())) {
    return "n/a";
  }
  const deltaSeconds = Math.round((value.getTime() - Date.now()) / 1000);
  const formatter = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
  const thresholds = [
    { unit: "day", value: 86400 },
    { unit: "hour", value: 3600 },
    { unit: "minute", value: 60 }
  ];
  for (const threshold of thresholds) {
    if (Math.abs(deltaSeconds) >= threshold.value) {
      return formatter.format(Math.round(deltaSeconds / threshold.value), threshold.unit);
    }
  }
  return formatter.format(deltaSeconds, "second");
}

export function absoluteTime(iso) {
  if (!iso) {
    return "n/a";
  }
  const value = new Date(iso);
  if (Number.isNaN(value.getTime())) {
    return "n/a";
  }
  return new Intl.DateTimeFormat("en", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(value);
}
