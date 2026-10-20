import { useCallback, useEffect, useRef, useState } from 'react'
import { connectGmail } from '../services/gmailConnect'

type State = { kind: 'idle' } | { kind: 'waiting' } | { kind: 'error'; message: string }

/**
 * Runs the Gmail popup sign-in for a component. `connect` must be called from a click handler.
 * `onConnected` runs once Google has approved access (the backend has then started the first check).
 */
export function useGmailConnect(onConnected: () => void) {
  const [state, setState] = useState<State>({ kind: 'idle' })
  const abort = useRef<AbortController | null>(null)
  const latest = useRef(onConnected)
  useEffect(() => {
    latest.current = onConnected
  })
  useEffect(() => () => abort.current?.abort(), [])

  const connect = useCallback(() => {
    abort.current?.abort()
    const controller = new AbortController()
    abort.current = controller
    setState({ kind: 'waiting' })
    connectGmail(controller.signal).then(
      (result) => {
        if (controller.signal.aborted) return
        if (result.ok) {
          setState({ kind: 'idle' })
          latest.current()
        } else {
          setState({ kind: 'error', message: result.reason })
        }
      },
      (err: unknown) => {
        if (controller.signal.aborted) return
        setState({ kind: 'error', message: err instanceof Error ? err.message : 'Couldn’t open Google sign-in' })
      },
    )
  }, [])

  const cancel = useCallback(() => {
    abort.current?.abort()
    setState({ kind: 'idle' })
  }, [])

  return { state, connect, cancel }
}
