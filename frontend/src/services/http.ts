import type { ApiErrorBody } from '../types/api'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const TOKEN_KEY = 'jobtrack.token'

/** An error response from the API, carrying the backend's message and field errors. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export const tokenStore = {
  get: (): string | null => {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set: (token: string) => {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* storage unavailable (private mode): the session lasts until reload */
    }
  },
  clear: () => {
    try {
      localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* ignore */
    }
  },
}

let onUnauthorized: (() => void) | null = null

/** Called when an authenticated request gets a 401 (token expired or revoked). */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

type Query = Record<string, string | number | boolean | null | undefined>

export function buildQuery(params: Query): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = tokenStore.get()
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(API_BASE + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check that the backend is running and try again.')
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const data: unknown = text ? safeParse(text) : null

  if (!response.ok) {
    if (response.status === 401 && token && onUnauthorized) {
      onUnauthorized()
    }
    const error = data as Partial<ApiErrorBody> | null
    const fieldErrors: Record<string, string> = {}
    for (const fe of error?.fieldErrors ?? []) {
      fieldErrors[fe.field] = fe.message
    }
    throw new ApiError(response.status, error?.message ?? fallbackMessage(response.status), fieldErrors)
  }
  return data as T
}

/** Used when an error response has no JSON body (e.g. rejected by a proxy or CORS filter). */
function fallbackMessage(status: number): string {
  if (status === 401) return 'Your session has expired. Please log in again.'
  if (status === 403) return 'The server refused this request.'
  if (status === 404) return 'Not found.'
  if (status >= 500) return 'The server ran into a problem. Please try again.'
  return `Request failed (${status})`
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export const http = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  delete: (path: string) => request<void>('DELETE', path),
}
