import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  sendPasswordResetEmail,
  updateProfile,
  type User as FirebaseUser,
} from 'firebase/auth'
import { auth } from '../firebase'
import type { User } from '../types/models'
import { createOrMergeUser, getUser, subscribeUser } from '../services/userService'
import { bgForSeed, letterFromName } from '../lib/avatarIdentity'

type AuthState = {
  firebaseUser: FirebaseUser | null
  user: User | null
  loading: boolean
  error: string | null
  clearError: () => void
  signIn: (email: string, password: string) => Promise<void>
  signUp: (
    name: string,
    email: string,
    password: string,
    avatarBase64?: string | null,
  ) => Promise<void>
  signOut: () => Promise<void>
  resetPassword: (email: string) => Promise<void>
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [firebaseUser, setFirebaseUser] = useState<FirebaseUser | null>(null)
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const clearError = useCallback(() => setError(null), [])

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, async (fu) => {
      setFirebaseUser(fu)
      setLoading(false)
    })
    return unsub
  }, [])

  useEffect(() => {
    if (!firebaseUser) {
      setUser(null)
      return
    }
    const unsub = subscribeUser(firebaseUser.uid, async (u) => {
      if (u) {
        setUser(u)
        return
      }
      // Bootstrap user doc if missing (older accounts)
      const minimal: User = {
        uid: firebaseUser.uid,
        name: firebaseUser.displayName?.trim() || firebaseUser.email?.split('@')[0] || 'User',
        email: firebaseUser.email || '',
        tripIds: [],
        photoUrl: null,
        avatarLetter: letterFromName(
          firebaseUser.displayName || firebaseUser.email?.split('@')[0] || '',
        ),
        avatarColorHex: bgForSeed(firebaseUser.uid),
      }
      await createOrMergeUser(minimal)
      const again = await getUser(firebaseUser.uid)
      setUser(again ?? minimal)
    })
    return unsub
  }, [firebaseUser])

  const signIn = useCallback(async (email: string, password: string) => {
    setError(null)
    await signInWithEmailAndPassword(auth, email.trim(), password)
  }, [])

  const signUp = useCallback(
    async (name: string, email: string, password: string, avatarBase64?: string | null) => {
      setError(null)
      const cred = await createUserWithEmailAndPassword(auth, email.trim(), password)
      const fu = cred.user
      const displayName = name.trim()
      await updateProfile(fu, { displayName })
      await fu.reload()
      const refreshed = auth.currentUser!
      const avLetter = letterFromName(displayName)
      const avColor = bgForSeed(refreshed.uid)
      const photoStored =
        avatarBase64 != null && avatarBase64.trim() !== '' ? avatarBase64.trim() : null
      await createOrMergeUser({
        uid: refreshed.uid,
        name: displayName,
        email: email.trim(),
        photoUrl: photoStored,
        tripIds: [],
        avatarLetter: avLetter,
        avatarColorHex: avColor,
      })
    },
    [],
  )

  const signOut = useCallback(async () => {
    await firebaseSignOut(auth)
  }, [])

  const resetPassword = useCallback(async (email: string) => {
    await sendPasswordResetEmail(auth, email.trim())
  }, [])

  const refreshUser = useCallback(async () => {
    if (!firebaseUser) return
    const u = await getUser(firebaseUser.uid)
    setUser(u)
  }, [firebaseUser])

  const value = useMemo(
    () => ({
      firebaseUser,
      user,
      loading,
      error,
      clearError,
      signIn,
      signUp,
      signOut,
      resetPassword,
      refreshUser,
    }),
    [firebaseUser, user, loading, error, clearError, signIn, signUp, signOut, resetPassword, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}
