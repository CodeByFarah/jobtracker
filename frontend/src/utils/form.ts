/** Turns an empty input into null so optional fields are sent as "not set". */
export function nullIfBlank(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

export function numberOrNull(value: string): number | null {
  if (value.trim() === '') return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}
