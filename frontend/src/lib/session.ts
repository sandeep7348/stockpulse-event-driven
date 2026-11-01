const SESSION_KEY = 'alertpulse.session';

export interface Session {
  token: string;
  email: string;
  userId: string;
  expiresAt: number;
}

/** Reads display values from a JWT. The server verifies the signature; this does not. */
export function decodeToken(token: string): { sub?: string; exp?: number } | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(payload), (char) => char.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}

export function toSession(token: string, email: string): Session | null {
  const claims = decodeToken(token);
  if (!claims?.exp || !claims.sub) {
    return null;
  }
  return { token, email, userId: claims.sub, expiresAt: claims.exp * 1000 };
}

export function loadSession(now = Date.now()): Session | null {
  try {
    const stored = localStorage.getItem(SESSION_KEY);
    const session = stored ? (JSON.parse(stored) as Session) : null;
    return session && session.expiresAt > now ? session : null;
  } catch {
    return null;
  }
}

export function saveSession(session: Session | null) {
  try {
    if (session) {
      localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } else {
      localStorage.removeItem(SESSION_KEY);
    }
  } catch {
    // The session still works for this page load if storage is blocked.
  }
}

export function formatRemaining(ms: number): string {
  const minutes = Math.max(0, Math.ceil(ms / 60_000));
  return minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : `${minutes}m`;
}
