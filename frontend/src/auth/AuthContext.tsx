import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { api } from '../api';
import { setAuthToken, setUnauthorizedHandler } from '../api/http';
import { loadSession, saveSession, toSession, type Session } from '../lib/session';
import type { Credentials } from '../types/api';

interface AuthValue {
  session: Session | null;
  /** Why the user was signed out without asking, shown on the sign-in screen. */
  signedOutReason: string | null;
  login(credentials: Credentials): Promise<void>;
  register(credentials: Credentials): Promise<void>;
  logout(reason?: string): void;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session | null>(() => {
    const stored = loadSession();
    setAuthToken(stored?.token ?? null);
    return stored;
  });
  const [signedOutReason, setSignedOutReason] = useState<string | null>(null);

  const logout = useCallback(
    (reason?: string) => {
      setAuthToken(null);
      saveSession(null);
      setSession(null);
      setSignedOutReason(reason ?? null);
      // Another account on this browser must not see this user's cached alerts.
      queryClient.clear();
    },
    [queryClient],
  );

  const begin = useCallback((token: string, email: string) => {
    const next = toSession(token, email);
    if (!next) {
      throw new Error('The server returned a session that could not be read.');
    }
    setAuthToken(next.token);
    saveSession(next);
    setSession(next);
    setSignedOutReason(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => logout('Your session has expired. Please sign in again.'));
  }, [logout]);

  useEffect(() => {
    if (!session) {
      return;
    }
    const timer = setTimeout(
      () => logout('Your session has expired. Please sign in again.'),
      Math.max(0, session.expiresAt - Date.now()),
    );
    return () => clearTimeout(timer);
  }, [session, logout]);

  const value = useMemo<AuthValue>(
    () => ({
      session,
      signedOutReason,
      logout,
      login: async (credentials) => begin((await api.login(credentials)).token, credentials.email),
      register: async (credentials) => begin((await api.register(credentials)).token, credentials.email),
    }),
    [session, signedOutReason, logout, begin],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return value;
}
