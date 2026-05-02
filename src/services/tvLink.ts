import type { SessionUser } from '../lib/authClient';
import { API_BASE } from '../constants/api';
import { buildAuthHeaders } from '../utils/authHeaders';
import { getMobileClientIdentityHeaders } from '../utils/clientIdentity';

export async function activateTvCode(user: SessionUser | null, userCode: string): Promise<{ success: true; deviceName?: string | null }> {
  if (!user) {
    throw new Error('Please sign in on mobile first.');
  }

  const authHeaders = await buildAuthHeaders(user);
  const identityHeaders = await getMobileClientIdentityHeaders();
  const response = await fetch(`${API_BASE}/auth/tv/activate`, {
    method: 'POST',
    headers: {
      ...authHeaders,
      ...identityHeaders,
    },
    body: JSON.stringify({
      user_code: userCode,
    }),
  });

  const data = await readJsonSafe(response);
  if (!response.ok) {
    throw new Error(data?.error ?? 'Could not link this TV right now.');
  }

  return {
    success: true,
    deviceName: data?.deviceName ?? null,
  };
}

export function normalizeTvCode(value: string): string {
  const cleaned = value.toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (cleaned.length <= 4) return cleaned;
  return `${cleaned.slice(0, 4)}-${cleaned.slice(4, 8)}`;
}

export function extractTvCode(payload: string): string | null {
  const trimmed = payload.trim();
  if (!trimmed) return null;

  try {
    const parsed = new URL(trimmed);
    const code = parsed.searchParams.get('code');
    return code ? normalizeTvCode(code) : null;
  } catch {
    return /^[A-Z0-9-]+$/i.test(trimmed) ? normalizeTvCode(trimmed) : null;
  }
}

async function readJsonSafe(response: Response): Promise<any> {
  try {
    return await response.json();
  } catch {
    return {};
  }
}
