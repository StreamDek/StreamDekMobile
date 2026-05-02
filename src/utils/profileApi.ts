import { API_BASE } from '../constants/api';
import { buildAuthHeaders } from './authHeaders';
import type { SessionUser } from '../lib/authClient';

// ── Shared avatar definitions ────────────────────────────────────────────────
export const PROFILE_AVATARS = [
  { id: 0, image: require('../../assets/avatars/av1.png'), color: '#6366f1' },
  { id: 1, image: require('../../assets/avatars/av2.png'), color: '#ec4899' },
  { id: 2, image: require('../../assets/avatars/av3.png'), color: '#f59e0b' },
  { id: 3, image: require('../../assets/avatars/av4.png'), color: '#10b981' },
  { id: 4, image: require('../../assets/avatars/av5.png'), color: '#3b82f6' },
  { id: 5, image: require('../../assets/avatars/av6.png'), color: '#3b82f6' },
  { id: 6, image: require('../../assets/avatars/av7.png'), color: '#3b82f6' },
  { id: 7, image: require('../../assets/avatars/av8.png'), color: '#3b82f6' },
  { id: 8, image: require('../../assets/avatars/av9.png'), color: '#3b82f6' },
  { id: 9, image: require('../../assets/avatars/av10.png'), color: '#3b82f6' },
  { id: 10, image: require('../../assets/avatars/av11.png'), color: '#3b82f6' },
  { id: 11, image: require('../../assets/avatars/av12.png'), color: '#3b82f6' },
] as const;

export type AvatarId = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11;

export const MAX_PROFILES_PER_ACCOUNT = 3;

// ── Types ────────────────────────────────────────────────────────────────────
export interface StreamProfile {
  id: string;
  userId: string;
  name: string;
  avatarIndex: number;
  hasPinSet: boolean;
  isDefault: boolean;
  subtitleLanguage: string | null;
  audioLanguage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProfileInput {
  name: string;
  avatarIndex?: number;
  pin?: string;
  subtitleLanguage?: string | null;
  audioLanguage?: string | null;
}

export interface UpdateProfileInput {
  name?: string;
  avatarIndex?: number;
  subtitleLanguage?: string | null;
  audioLanguage?: string | null;
}

// ── Internal fetch wrapper ───────────────────────────────────────────────────
async function profileFetch(
  user: SessionUser,
  path: string,
  options?: RequestInit,
): Promise<{ ok: boolean; status: number; data: any }> {
  const includeContentType = options?.body != null;
  const authHeaders = await buildAuthHeaders(user, { includeContentType });
  const headers = {
    ...authHeaders,
    ...(options?.headers ?? {}),
  };
  const res = await fetch(`${API_BASE}/profiles${path}`, { ...options, headers });
  const data = await res.json().catch(() => ({}));
  return { ok: res.ok, status: res.status, data };
}

// ── API calls ────────────────────────────────────────────────────────────────
export async function fetchProfiles(user: SessionUser): Promise<StreamProfile[]> {
  const { ok, data } = await profileFetch(user, '/');
  if (!ok) throw new Error(data?.error ?? 'Failed to load profiles');
  return data.profiles ?? [];
}

export async function createProfile(
  user: SessionUser,
  input: CreateProfileInput,
): Promise<StreamProfile> {
  const { ok, data } = await profileFetch(user, '/', {
    method: 'POST',
    body: JSON.stringify(input),
  });
  if (!ok) throw new Error(data?.error ?? 'Failed to create profile');
  return data.profile;
}

export async function updateProfile(
  user: SessionUser,
  id: string,
  input: UpdateProfileInput,
): Promise<StreamProfile> {
  const { ok, data } = await profileFetch(user, `/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
  if (!ok) throw new Error(data?.error ?? 'Failed to update profile');
  return data.profile;
}

export async function deleteProfile(user: SessionUser, id: string): Promise<void> {
  const { ok, data } = await profileFetch(user, `/${id}`, { method: 'DELETE' });
  if (!ok) throw new Error(data?.error ?? 'Failed to delete profile');
}

export async function setDefaultProfile(user: SessionUser, id: string): Promise<void> {
  const { ok, data } = await profileFetch(user, `/${id}/set-default`, { method: 'POST' });
  if (!ok) throw new Error(data?.error ?? 'Failed to set default profile');
}

export async function setProfilePin(
  user: SessionUser,
  id: string,
  pin: string | null,
): Promise<void> {
  const { ok, data } = await profileFetch(user, `/${id}/pin`, {
    method: 'POST',
    body: JSON.stringify({ pin }),
  });
  if (!ok) throw new Error(data?.error ?? 'Failed to update PIN');
}

export async function verifyProfilePin(
  user: SessionUser,
  id: string,
  pin: string,
): Promise<boolean> {
  const { ok, data } = await profileFetch(user, `/${id}/verify-pin`, {
    method: 'POST',
    body: JSON.stringify({ pin }),
  });
  if (!ok) return false;
  return data.valid === true;
}
