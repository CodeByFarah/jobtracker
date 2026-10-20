import { gmailApi } from './api'

/**
 * Connecting Gmail in a popup, the way hosted apps connect Google accounts.
 *
 * The popup goes to Google, Google sends it to the backend callback, and the backend redirects it
 * to /gmail/done, which reports the result back to this tab and closes itself.
 *
 * Google's sign-in pages cut the popup's link to its opener (Cross-Origin-Opener-Policy), so
 * window.opener and popup.closed can't be trusted. The result travels over a BroadcastChannel and
 * a localStorage event instead, and as a last resort the tab asks the server whether Gmail is now
 * connected.
 */

export type ConnectResult = { ok: true } | { ok: false; reason: string }

const CHANNEL = 'jobtrack-gmail'
const RESULT_KEY = 'jobtrack.gmailConnectResult'
/** Set (to a timestamp) when the popup was blocked and the whole tab went to Google instead. */
const REDIRECT_FLAG = 'jobtrack.gmailConnectRedirect'
const REDIRECT_FLAG_TTL_MS = 15 * 60 * 1000
const POPUP_NAME = 'jobtrack-gmail-connect'
const SERVER_POLL_MS = 3000

/**
 * Opens Google sign-in in a popup and resolves once the user has approved or cancelled.
 * Must be called straight from a click handler (before any await), or browsers block the popup.
 * If the popup is blocked anyway, the tab itself goes to Google and this never resolves.
 */
export async function connectGmail(signal: AbortSignal): Promise<ConnectResult> {
  const popup = openPopup()
  let url: string
  let wasConnected: boolean
  try {
    const [link, status] = await Promise.all([gmailApi.connectUrl(), gmailApi.status()])
    url = link.url
    wasConnected = status.connected
  } catch (err) {
    popup?.close()
    throw err
  }

  if (!popup || popup.closed) {
    safeStorage(() => localStorage.setItem(REDIRECT_FLAG, String(Date.now())))
    window.location.assign(url)
    return new Promise<ConnectResult>(() => {})
  }
  safeStorage(() => localStorage.removeItem(REDIRECT_FLAG))
  popup.location.href = url
  popup.focus()

  try {
    return await waitForResult(signal, wasConnected)
  } finally {
    try {
      popup.close()
    } catch {
      // The link to the popup may already be cut; it closes itself.
    }
  }
}

function openPopup(): Window | null {
  const width = 500
  const height = 640
  const left = Math.max(0, window.screenX + (window.outerWidth - width) / 2)
  const top = Math.max(0, window.screenY + (window.outerHeight - height) / 2.5)
  return window.open('', POPUP_NAME, `popup=yes,width=${width},height=${height},left=${left},top=${top}`)
}

function waitForResult(signal: AbortSignal, wasConnected: boolean): Promise<ConnectResult> {
  return new Promise((resolve, reject) => {
    const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL)

    function finish(result: ConnectResult) {
      cleanup()
      resolve(result)
    }
    function onStorage(event: StorageEvent) {
      if (event.key === RESULT_KEY && event.newValue) finish(parseResult(event.newValue))
    }
    function onAbort() {
      cleanup()
      reject(new DOMException('Cancelled', 'AbortError'))
    }
    // Safety net if both messages get lost: the server knows once the account is linked.
    const timer = wasConnected
      ? undefined
      : setInterval(() => {
          gmailApi
            .status()
            .then((status) => {
              if (status.connected) finish({ ok: true })
            })
            .catch(() => {})
        }, SERVER_POLL_MS)
    function cleanup() {
      if (timer) clearInterval(timer)
      channel?.close()
      window.removeEventListener('storage', onStorage)
      signal.removeEventListener('abort', onAbort)
    }

    if (channel) channel.onmessage = (event: MessageEvent<string>) => finish(parseResult(event.data))
    window.addEventListener('storage', onStorage)
    signal.addEventListener('abort', onAbort)
  })
}

/**
 * True when this sign-in ran in the whole tab rather than a popup (and clears the marker).
 * Old markers from an abandoned sign-in are ignored.
 */
export function takeRedirectFlag(): boolean {
  let flagged = false
  safeStorage(() => {
    const at = Number(localStorage.getItem(REDIRECT_FLAG))
    flagged = at > 0 && Date.now() - at < REDIRECT_FLAG_TTL_MS
    localStorage.removeItem(REDIRECT_FLAG)
  })
  return flagged
}

/** Called by the /gmail/done page inside the popup. */
export function reportConnectResult(result: ConnectResult) {
  const message = JSON.stringify(result)
  if (typeof BroadcastChannel !== 'undefined') {
    const channel = new BroadcastChannel(CHANNEL)
    channel.postMessage(message)
    channel.close()
  }
  safeStorage(() => {
    // A changing value so the storage event fires every time; removed right after.
    localStorage.setItem(RESULT_KEY, JSON.stringify({ ...result, at: Date.now() }))
    localStorage.removeItem(RESULT_KEY)
  })
}

function parseResult(raw: string): ConnectResult {
  try {
    const value = JSON.parse(raw) as { ok?: boolean; reason?: string }
    return value.ok ? { ok: true } : { ok: false, reason: value.reason || 'Gmail wasn’t connected.' }
  } catch {
    return { ok: false, reason: 'Gmail wasn’t connected.' }
  }
}

export function safeStorage(action: () => void) {
  try {
    action()
  } catch {
    // Storage can be unavailable (private mode); the server poll still works.
  }
}
