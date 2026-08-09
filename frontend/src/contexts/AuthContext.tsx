import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { User } from '../types/auth';
import { getCurrentUser, login as loginRequest, logout as logoutRequest } from '../services/authService';
import { clearStoredAuth, getAccessToken, getRefreshToken, storeTokens } from '../services/api';

interface AuthContextValue {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (roles: string[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(() => getAccessToken());
  const [user, setUser] = useState<User | null>(() => {
    const stored = localStorage.getItem('forensic_user');
    return stored ? (JSON.parse(stored) as User) : null;
  });
  const [isLoading, setIsLoading] = useState(Boolean(getAccessToken()));

  useEffect(() => {
    let active = true;
    async function restore() {
      if (!getAccessToken()) {
        setIsLoading(false);
        return;
      }
      try {
        const current = await getCurrentUser();
        if (!active) return;
        localStorage.setItem('forensic_user', JSON.stringify(current));
        setUser(current);
        setToken(getAccessToken());
      } catch {
        clearStoredAuth();
        if (!active) return;
        setUser(null);
        setToken(null);
      } finally {
        if (active) setIsLoading(false);
      }
    }
    restore();
    return () => {
      active = false;
    };
  }, []);

  async function handleLogin(username: string, password: string) {
    const response = await loginRequest(username, password);
    storeTokens(response.accessToken, response.refreshToken);
    localStorage.setItem('forensic_user', JSON.stringify(response.user));
    setToken(response.accessToken);
    setUser(response.user);
  }

  async function handleLogout() {
    const refreshToken = getRefreshToken();
    try {
      await logoutRequest(refreshToken);
    } finally {
      clearStoredAuth();
      setToken(null);
      setUser(null);
    }
  }

  function hasRole(roles: string[]) {
    return Boolean(user?.roles.some((role) => roles.includes(role)));
  }

  const value = useMemo(
    () => ({ user, token, isLoading, isAuthenticated: Boolean(token && user), login: handleLogin, logout: handleLogout, hasRole }),
    [isLoading, token, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
