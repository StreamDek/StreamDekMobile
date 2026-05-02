import React, { createContext, useContext, useEffect, useState } from 'react';
import { API_BASE } from '../constants/api';
import {
  clearStoredAuthSession,
  loadStoredAuthSession,
  storeAuthSession,
  type SessionUser,
} from '../lib/authClient';
import { clearProfileLaunchBootstrap, preloadProfileLaunchBootstrap } from '../utils/profileLaunchBootstrap';

interface AuthContextType {
  user: SessionUser | null;
  authLoading: boolean;
  signIn: (email: string, password: string) => Promise<string | null>;
  signUp: (email: string, password: string) => Promise<string | null>;
  signOut: () => Promise<void>;
  requestPasswordReset: (email: string) => Promise<{ error: string | null; devResetCode?: string | null }>;
  confirmPasswordReset: (email: string, token: string, newPassword: string) => Promise<string | null>;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function friendlyAuthError(message: string): string {
  const normalized = message.toLowerCase();

  if (normalized.includes('invalid credentials')) return 'Invalid email or password.';
  if (normalized.includes('user already exists')) return 'An account with this email already exists.';
  if (normalized.includes('email and password required')) return 'Please enter both email and password.';
  if (normalized.includes('account not found')) return 'No account was found for this session.';
  if (normalized.includes('password reset email is not configured')) return 'Password reset is not available right now.';
  if (normalized.includes('unable to send password reset email')) return 'We could not send a reset code. Please try again.';
  if (normalized.includes('invalid or expired reset code')) return 'That reset code is invalid or has expired.';
  if (normalized.includes('password must be at least 6 characters')) return 'Your new password must be at least 6 characters.';
  if (normalized.includes('failed to create account session')) return 'Could not start this account session.';

  return message || 'Something went wrong. Please try again.';
}

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [authLoading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function restoreSession() {
      const session = await loadStoredAuthSession();
      if (!session) {
        if (!cancelled) {
          setUser(null);
          setLoading(false);
        }
        return;
      }

      try {
        const response = await fetch(`${API_BASE}/auth/me`, {
          headers: {
            Authorization: `Bearer ${session.token}`,
            'Content-Type': 'application/json',
          },
        });
        const data = await readJsonSafe(response);

        if (!response.ok) {
          throw new Error(data?.error ?? 'Session expired');
        }

        const nextUser = normalizeSessionUser(data.user, session.token);
        await storeAuthSession({ token: session.token, user: nextUser });
        if (!cancelled) {
          setUser(nextUser);
        }
      } catch {
        await clearStoredAuthSession();
        if (!cancelled) {
          setUser(null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void restoreSession();

    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = async (email: string, password: string): Promise<string | null> => {
    try {
      const response = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      const data = await readJsonSafe(response);

      if (!response.ok) {
        return friendlyAuthError(data?.error ?? 'Invalid credentials');
      }

      const nextUser = normalizeSessionUser(data.user, data.token);
      await preloadProfileLaunchBootstrap(nextUser);
      await storeAuthSession({ token: data.token, user: nextUser });
      setUser(nextUser);
      return null;
    } catch {
      return 'Network error. Check your connection.';
    }
  };

  const signUp = async (email: string, password: string): Promise<string | null> => {
    try {
      const response = await fetch(`${API_BASE}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      const data = await readJsonSafe(response);

      if (!response.ok) {
        return friendlyAuthError(data?.error ?? 'Could not create account');
      }

      const nextUser = normalizeSessionUser(data.user, data.token);
      await preloadProfileLaunchBootstrap(nextUser);
      await storeAuthSession({ token: data.token, user: nextUser });
      setUser(nextUser);
      return null;
    } catch {
      return 'Network error. Check your connection.';
    }
  };

  const signOut = async () => {
    clearProfileLaunchBootstrap(user?.uid);
    await clearStoredAuthSession();
    setUser(null);
  };

  const requestPasswordReset = async (email: string): Promise<{ error: string | null; devResetCode?: string | null }> => {
    try {
      const response = await fetch(`${API_BASE}/auth/password-reset/request`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });
      const data = await readJsonSafe(response);

      if (!response.ok) {
        return { error: friendlyAuthError(data?.error ?? 'Could not request password reset') };
      }

      return {
        error: null,
        devResetCode: data?.devResetCode ?? null,
      };
    } catch {
      return { error: 'Network error. Check your connection.' };
    }
  };

  const confirmPasswordReset = async (email: string, token: string, newPassword: string): Promise<string | null> => {
    try {
      const response = await fetch(`${API_BASE}/auth/password-reset/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, token, newPassword }),
      });
      const data = await readJsonSafe(response);

      if (!response.ok) {
        return friendlyAuthError(data?.error ?? 'Could not reset password');
      }

      return null;
    } catch {
      return 'Network error. Check your connection.';
    }
  };

  return (
    <AuthContext.Provider value={{ user, authLoading, signIn, signUp, signOut, requestPasswordReset, confirmPasswordReset }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};

function normalizeSessionUser(user: any, token: string): SessionUser {
  return {
    uid: String(user?.id ?? user?.uid ?? ''),
    email: user?.email ?? null,
    displayName: user?.displayName ?? null,
    subscriptionStatus: String(user?.subscriptionStatus ?? 'free'),
    accessToken: token,
  };
}

async function readJsonSafe(response: Response): Promise<any> {
  try {
    return await response.json();
  } catch {
    return {};
  }
}
