import { create } from 'zustand';
import type { User } from '../types';
import { authService } from '../services/api';

interface AuthState {
  user: User | null;
  isLoading: boolean;
  error: string | null;
  setUser: (user: User | null) => void;
  devLogin: (displayName: string) => Promise<User>;
  logout: () => void;
  refreshProfile: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: JSON.parse(localStorage.getItem('blindtest_user') || 'null'),
  isLoading: false,
  error: null,

  setUser: (user) => {
    if (user) {
      localStorage.setItem('blindtest_user', JSON.stringify(user));
    } else {
      localStorage.removeItem('blindtest_user');
    }
    set({ user });
  },

  devLogin: async (displayName: string) => {
    set({ isLoading: true, error: null });
    try {
      const user = await authService.devLogin(displayName);
      get().setUser(user);
      set({ isLoading: false });
      return user;
    } catch (err: any) {
      set({ error: err.message || 'Erreur lors de la connexion', isLoading: false });
      throw err;
    }
  },

  logout: () => {
    get().setUser(null);
  },

  refreshProfile: async () => {
    const current = get().user;
    if (!current) return;
    try {
      const updated = await authService.getMe();
      get().setUser(updated);
    } catch (e) {
      // Ignorer si mode dev hors session cookie
    }
  },
}));
