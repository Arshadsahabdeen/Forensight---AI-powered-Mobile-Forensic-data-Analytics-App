import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { User } from '../types/auth';
import { getCurrentUser, login as loginRequest, logout as logoutRequest } from '../services/authService';
import { clearStoredAuth, getAccessToken, getRefreshToken, storeTokens } from '../services/api';

interface AuthContextValue {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (roles: string[]) => boolean;
}

const USER_KEY = 'forensic_user';
const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function readStoredUser(): User | null {
  try {
    const stored = window.localStorage.getItem(USER_KEY);
    return stored ? (JSON.parse(stored) as User) : null;
  } catch {
    return null;
  }
}

function writeStoredUser(user: User) {
  try {
    window.localStorage.setItem(USER_KEY, JSON.stringify(user));
  } catch {
    // storage unavailable: the session still works for this tab
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => (getAccessToken() ? readStoredUser() : null));
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
        writeStoredUser(current);
        setUser(current);
      } catch {
        clearStoredAuth();
        if (active) setUser(null);
      } finally {
        if (active) setIsLoading(false);
      }
    }
    void restore();
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const response = await loginRequest(username, password);
    storeTokens(response.accessToken, response.refreshToken);
    writeStoredUser(response.user);
    setUser(response.user);
  }, []);

  const logout = useCallback(async () => {
    try {
      await logoutRequest(getRefreshToken());
    } catch {
      // the server may already consider the session ended; clear locally regardless
    } finally {
      clearStoredAuth();
      setUser(null);
    }
  }, []);

  const hasRole = useCallback((roles: string[]) => Boolean(user?.roles.some((role) => roles.includes(role))), [user]);

  const value = useMemo(
    () => ({ user, isLoading, isAuthenticated: Boolean(user && getAccessToken()), login, logout, hasRole }),
    [user, isLoading, login, logout, hasRole]
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

/** Convenience role checks used to tailor the UI. The backend enforces the same rules. */
export function usePermissions() {
  const { hasRole } = useAuth();
  return {
    isAdmin: hasRole(['ADMIN']),
    canWrite: hasRole(['ADMIN', 'INVESTIGATOR']),
    canReview: hasRole(['ADMIN', 'REVIEWER'])
  };
}
