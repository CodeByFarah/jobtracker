import { useEffect, useRef } from 'react'

const DELAYS_MS = [4000, 12000]

/**
 * A new company's logo is looked up in the background and usually lands a few seconds after the
 * page opens. While `waiting`, reload the page's data a couple of times so it appears by itself.
 */
export function useLogoArrival(waiting: boolean | null | undefined, reload: () => void) {
  const tries = useRef(0)
  useEffect(() => {
    if (!waiting || tries.current >= DELAYS_MS.length) return
    const timer = setTimeout(() => {
      tries.current += 1
      reload()
    }, DELAYS_MS[tries.current])
    return () => clearTimeout(timer)
  }, [waiting, reload])
}
