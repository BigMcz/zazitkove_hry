const PART = 4
const LENGTH = 8

/**
 * Formats a typed access code as XXXX-XXXX: upper case, only letters and digits, the dash added
 * right after the fourth character while typing (deleting the dash itself removes it).
 */
export function formatAccessCode(typed: string, previous: string): string {
  const chars = typed
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, LENGTH)
  if (chars.length > PART) {
    return `${chars.slice(0, PART)}-${chars.slice(PART)}`
  }
  const keepDash = typed.length > previous.length || typed.endsWith('-')
  return chars.length === PART && keepDash ? `${chars}-` : chars
}
