import { useCallback, useEffect, useRef, useState } from 'react'

export interface AsyncState<T> {
  data: T | undefined
  error: Error | null
  loading: boolean
  /** Re-runs the loader, keeping the current data visible while it loads. */
  reload: () => void
  setData: (updater: (current: T | undefined) => T | undefined) => void
}

/**
 * Runs an async loader whenever its dependencies change and tracks loading/error state.
 * Responses that arrive after a newer request has started are ignored, so fast typing in
 * a search box can never show stale results.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setDataState] = useState<T | undefined>(undefined)
  const [error, setError] = useState<Error | null>(null)
  const [loading, setLoading] = useState(true)
  const [version, setVersion] = useState(0)
  const requestId = useRef(0)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(loader, deps)

  useEffect(() => {
    const id = ++requestId.current
    setLoading(true)
    setError(null)
    load()
      .then((result) => {
        if (id === requestId.current) setDataState(result)
      })
      .catch((err: unknown) => {
        if (id === requestId.current) setError(err instanceof Error ? err : new Error(String(err)))
      })
      .finally(() => {
        if (id === requestId.current) setLoading(false)
      })
  }, [load, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  const setData = useCallback(
    (updater: (current: T | undefined) => T | undefined) => setDataState((current) => updater(current)),
    [],
  )

  return { data, error, loading, reload, setData }
}
