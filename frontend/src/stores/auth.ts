import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AuthState {
  token: string | null
  adminId: string | null
  username: string | null
  role: string | null
  login: (t: string, id: string, u: string, r: string) => void
  logout: () => void
  isAuthed: () => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      adminId: null,
      username: null,
      role: null,
      login: (t, id, u, r) =>
        set({ token: t, adminId: id, username: u, role: r }),
      logout: () =>
        set({ token: null, adminId: null, username: null, role: null }),
      isAuthed: () => !!get().token,
    }),
    { name: 'magent-auth' },
  ),
)