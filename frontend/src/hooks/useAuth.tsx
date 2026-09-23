import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, userApi } from '../services/api'
import { setUnauthorizedHandler, tokenStore } from '../services/http'
import type { AuthResponse, User } from '../types/api'

interface AuthContextValue {
  user: User | null
  /** True while restoring a session from a stored token on first load. */
  initializing: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, fullName: string) => Promise<void>
  logout: () => void
  setUser: (user: User) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [initializing, setInitializing] = useState(() => tokenStore.get() !== null)

  const logout = useCallback(() => {
    // JWTs are stateless: logging out means forgetting the token on this device.
    tokenStore.clear()
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  useEffect(() => {
    if (!tokenStore.get()) return
    userApi
      .me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setInitializing(false))
  }, [])

  const acceptSession = useCallback((response: AuthResponse) => {
    tokenStore.set(response.accessToken)
    setUser(response.user)
  }, [])

  const login = useCallback(
    async (email: string, password: string) => acceptSession(await authApi.login({ email, password })),
    [acceptSession],
  )

  const register = useCallback(
    async (email: string, password: string, fullName: string) =>
      acceptSession(await authApi.register({ email, password, fullName })),
    [acceptSession],
  )

  const value = useMemo(
    () => ({ user, initializing, login, register, logout, setUser }),
    [user, initializing, login, register, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>')
  return context
}
