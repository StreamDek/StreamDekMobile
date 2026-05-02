import AsyncStorage from '@react-native-async-storage/async-storage';

const AUTH_STORAGE_KEY = 'streamdek_auth_session_v1';

export interface SessionUser {
  uid: string;
  email: string | null;
  displayName: string | null;
  subscriptionStatus: string;
  accessToken: string;
}

export interface SessionAuth {
  token: string;
  user: SessionUser;
}

export async function loadStoredAuthSession(): Promise<SessionAuth | null> {
  try {
    const raw = await AsyncStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;

    const parsed = JSON.parse(raw) as SessionAuth;
    if (!parsed?.token || !parsed?.user?.uid) {
      return null;
    }

    return parsed;
  } catch {
    return null;
  }
}

export async function storeAuthSession(session: SessionAuth): Promise<void> {
  await AsyncStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
}

export async function clearStoredAuthSession(): Promise<void> {
  await AsyncStorage.removeItem(AUTH_STORAGE_KEY);
}

export async function getStoredAuthToken(): Promise<string | null> {
  const session = await loadStoredAuthSession();
  return session?.token ?? null;
}
