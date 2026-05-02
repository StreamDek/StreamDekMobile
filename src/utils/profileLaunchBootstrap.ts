import { API_BASE } from '../constants/api';
import type { SessionUser } from '../lib/authClient';
import {
  createProfile,
  fetchProfiles,
  type StreamProfile,
} from './profileApi';

export type ProfileLaunchHeroItem = {
  id: string;
  type: 'movie' | 'tv';
  title: string;
  backdrop: string | null;
  poster?: string | null;
  description?: string;
  year?: number;
};

type ProfileLaunchBootstrap = {
  uid: string;
  profiles: StreamProfile[];
  heroItems: ProfileLaunchHeroItem[];
};

let cachedBootstrap: ProfileLaunchBootstrap | null = null;

function normalizeHeroItems(movieJson: any, tvJson: any): ProfileLaunchHeroItem[] {
  return [
    ...((movieJson?.results ?? []) as any[]).map(item => ({ ...item, type: 'movie' as const })),
    ...((tvJson?.results ?? []) as any[]).map(item => ({ ...item, type: 'tv' as const })),
  ]
    .filter(item => !!(item.backdrop ?? item.poster))
    .slice(0, 10)
    .map(item => ({
      id: `${item.type}:${item.tmdbId ?? item.id}`,
      type: item.type,
      title: item.title ?? '',
      backdrop: item.backdrop ?? item.poster ?? null,
      poster: item.poster ?? null,
      description: item.description ?? '',
      year: item.year,
    }));
}

export async function preloadProfileLaunchBootstrap(user: SessionUser): Promise<ProfileLaunchBootstrap | null> {
  try {
    const [profilesLoaded, movieRes, tvRes] = await Promise.all([
      fetchProfiles(user).catch(() => [] as StreamProfile[]),
      fetch(`${API_BASE}/tmdb/trending/movie`).then(r => r.json()).catch(() => null),
      fetch(`${API_BASE}/tmdb/trending/tv`).then(r => r.json()).catch(() => null),
    ]);

    let profiles = profilesLoaded;
    if (profiles.length === 0) {
      const defaultName = user.displayName?.trim() || 'My Profile';
      const created = await createProfile(user, { name: defaultName, avatarIndex: 0 });
      profiles = [created];
    }

    cachedBootstrap = {
      uid: user.uid,
      profiles,
      heroItems: normalizeHeroItems(movieRes, tvRes),
    };
    return cachedBootstrap;
  } catch {
    return null;
  }
}

export function peekProfileLaunchBootstrap(uid: string | null | undefined): ProfileLaunchBootstrap | null {
  if (!uid || !cachedBootstrap || cachedBootstrap.uid !== uid) return null;
  return cachedBootstrap;
}

export function consumeProfileLaunchBootstrap(uid: string | null | undefined): ProfileLaunchBootstrap | null {
  if (!uid || !cachedBootstrap || cachedBootstrap.uid !== uid) return null;
  return cachedBootstrap;
}

export function clearProfileLaunchBootstrap(uid?: string | null) {
  if (!cachedBootstrap) return;
  if (uid && cachedBootstrap.uid !== uid) return;
  cachedBootstrap = null;
}
