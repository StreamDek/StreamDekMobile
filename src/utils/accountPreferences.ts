import type { SessionUser } from '../lib/authClient';
import { API_BASE } from '../constants/api';
import { buildAuthHeaders } from './authHeaders';
import { getMobileClientIdentityHeaders } from './clientIdentity';

export interface AccountBootstrap {
  profile?: {
    displayName?: string | null;
    email?: string | null;
  } | null;
  preferences?: any;
  devices?: Array<{
    id: string;
    name?: string | null;
    platform?: string | null;
    deviceType?: string | null;
    appVersion?: string | null;
    lastSeenAt?: string | null;
    isCurrent?: boolean;
    capabilities?: Record<string, any>;
  }>;
  sessions?: Array<{
    id: string;
    clientName?: string | null;
    clientPlatform?: string | null;
    lastSeenAt?: string | null;
    isCurrent?: boolean;
    deviceId?: string | null;
  }>;
}

export async function fetchAccountPreferences(user: SessionUser | null): Promise<any | null> {
  if (!user) return null;

  try {
    const response = await fetch(`${API_BASE}/account/preferences`, {
      headers: await buildAuthHeaders(user, { includeContentType: false }),
    });

    if (!response.ok) return null;
    const data = await response.json();
    return data.preferences ?? null;
  } catch {
    return null;
  }
}

export async function fetchAccountBootstrap(user: SessionUser | null, profileId?: string | null): Promise<AccountBootstrap | null> {
  if (!user) return null;

  try {
    const authHeaders = await buildAuthHeaders(user, { includeContentType: false, profileId });
    const identityHeaders = await getMobileClientIdentityHeaders();
    const response = await fetch(`${API_BASE}/account/bootstrap`, {
      headers: {
        ...authHeaders,
        ...identityHeaders,
      },
    });

    if (!response.ok) return null;
    return await response.json();
  } catch {
    return null;
  }
}

export async function patchAccountPreferences(user: SessionUser | null, preferences: Record<string, any>): Promise<void> {
  if (!user) return;

  try {
    await fetch(`${API_BASE}/account/preferences`, {
      method: 'PATCH',
      headers: await buildAuthHeaders(user),
      body: JSON.stringify({ preferences }),
    });
  } catch {
    // Keep local settings responsive even if sync fails.
  }
}

export async function deleteAccountDevice(user: SessionUser | null, deviceId: string): Promise<void> {
  if (!user) return;

  const response = await fetch(`${API_BASE}/account/devices/${deviceId}`, {
    method: 'DELETE',
    headers: await buildAuthHeaders(user, { includeContentType: false }),
  });

  if (!response.ok) {
    let message = 'Could not disconnect this device.';
    try {
      const data = await response.json();
      message = data?.error ?? message;
    } catch {}
    throw new Error(message);
  }
}
