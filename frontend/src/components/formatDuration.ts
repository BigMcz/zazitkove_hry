/** Human readable duration in Czech: 45 s, 1 min, 3 min 05 s, 1 h 02 min. */
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.round(totalSeconds))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60

  if (hours > 0) {
    return `${hours} h ${String(minutes).padStart(2, '0')} min`
  }
  if (minutes > 0) {
    return rest === 0 ? `${minutes} min` : `${minutes} min ${String(rest).padStart(2, '0')} s`
  }
  return `${rest} s`
}
