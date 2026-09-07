import axios from 'axios';
import type { Theme, User, UserThemeStats, MatchmakingStats } from '../types';

const rawApiUrl = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1').trim().replace(/\/+$/, '');
export const BACKEND_URL = rawApiUrl.replace(/\/api\/v1\/?$/, '');
export const API_BASE_URL = `${BACKEND_URL}/api/v1`;
export const GOOGLE_AUTH_URL = `${BACKEND_URL}/oauth2/authorization/google`;

export const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});

// Intercepteur automatique pour attacher le token Bearer si présent
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('blindtest_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const authService = {
  getMe: async (customToken?: string): Promise<User> => {
    const token = customToken || localStorage.getItem('blindtest_token');
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const params = token ? { token } : {};
    const res = await api.get<User>('/auth/me', { headers, params });
    return res.data;
  },
  devLogin: async (displayName: string, email?: string): Promise<User> => {
    const res = await api.post<User>('/auth/dev-login', { displayName, email });
    return res.data;
  },
};

export const themeService = {
  getThemes: async (): Promise<Theme[]> => {
    const res = await api.get<Theme[]>('/themes');
    return res.data;
  },
};

export const matchmakingService = {
  joinQueue: async (userId: string, preferredThemeId?: string) => {
    const res = await api.post('/matchmaking/join', { userId, preferredThemeId });
    return res.data;
  },
  leaveQueue: async (userId: string) => {
    const res = await api.post(`/matchmaking/leave?userId=${userId}`);
    return res.data;
  },
  getStatus: async (userId: string) => {
    const res = await api.get(`/matchmaking/status?userId=${userId}`);
    return res.data;
  },
  getStats: async (): Promise<MatchmakingStats> => {
    const res = await api.get<MatchmakingStats>('/matchmaking/stats');
    return res.data;
  },
};

export const soloService = {
  startSession: async (userId: string, themeId?: string) => {
    const res = await api.post<{ gameId: string; totalRounds: number }>('/games/solo/start', {
      userId,
      themeId: themeId || null,
    });
    return res.data;
  },
};

export const userService = {
  getUser: async (userId: string): Promise<User> => {
    const res = await api.get<User>(`/users/${userId}`);
    return res.data;
  },
  getStats: async (userId: string): Promise<UserThemeStats[]> => {
    const res = await api.get<UserThemeStats[]>(`/users/${userId}/stats`);
    return res.data;
  },
  getLeaderboard: async (): Promise<User[]> => {
    const res = await api.get<User[]>('/users/leaderboard');
    return res.data;
  },
};
