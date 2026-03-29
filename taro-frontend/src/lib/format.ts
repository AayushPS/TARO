export function formatInstant(value?: string | null): string {
  if (!value) {
    return 'Not available'
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(parsed)
}

export function formatSeconds(value?: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return 'N/A'
  }
  if (value >= 3600) {
    return `${(value / 3600).toFixed(2)} h`
  }
  if (value >= 60) {
    return `${(value / 60).toFixed(1)} min`
  }
  return `${value.toFixed(1)} s`
}

export function formatTicks(value?: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return 'N/A'
  }
  if (value >= 3600) {
    return `t+${(value / 3600).toFixed(1)}h`
  }
  if (value >= 60) {
    return `t+${(value / 60).toFixed(1)}m`
  }
  return `t+${value}s`
}

export function formatCount(value?: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '0'
  }
  return new Intl.NumberFormat().format(value)
}

export function formatMillis(value?: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return 'N/A'
  }
  if (value >= 1000) {
    return `${(value / 1000).toFixed(2)} s`
  }
  return `${value.toFixed(1)} ms`
}

export type Tone = 'neutral' | 'good' | 'warn' | 'danger'

export function toneForStatus(status?: string | null): Tone {
  const normalized = (status ?? '').toUpperCase()
  if (!normalized) {
    return 'neutral'
  }
  if (
    normalized.includes('PASS') ||
    normalized.includes('SUCCESS') ||
    normalized.includes('OK') ||
    normalized.includes('PUBLISHED') ||
    normalized.includes('HEALTHY') ||
    normalized.includes('GREEN')
  ) {
    return 'good'
  }
  if (
    normalized.includes('FAIL') ||
    normalized.includes('ERROR') ||
    normalized.includes('CRITICAL') ||
    normalized.includes('BLOCKED') ||
    normalized.includes('RED')
  ) {
    return 'danger'
  }
  if (
    normalized.includes('RUNNING') ||
    normalized.includes('WARN') ||
    normalized.includes('PENDING') ||
    normalized.includes('QUEUE') ||
    normalized.includes('AMBER')
  ) {
    return 'warn'
  }
  return 'neutral'
}
