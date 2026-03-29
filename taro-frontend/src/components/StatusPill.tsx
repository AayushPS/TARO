import { toneForStatus, type Tone } from '../lib/format'

interface StatusPillProps {
  label: string
  tone?: Tone
}

export function StatusPill({ label, tone }: StatusPillProps) {
  const resolvedTone = tone ?? toneForStatus(label)
  return (
    <span className={`status-pill status-pill--${resolvedTone}`}>{label}</span>
  )
}
