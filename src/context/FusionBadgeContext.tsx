import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { useProfile } from './ProfileContext';
import { profileScopedStorageKey } from '../utils/profileStorage';
import { fetchAccountPreferences, patchAccountPreferences } from '../utils/accountPreferences';
import {
  FusionBadgeFilter,
  FusionBadgeGroupMatches,
  FusionBadgeSource,
  fetchFusionBadgeSource,
  matchFusionBadges,
  flattenFusionBadges,
} from '../utils/fusionBadges';
import { AddonStream } from './AddonContext';

const SETTINGS_STORAGE_KEY = 'fusion_badge_settings';
const SOURCES_STORAGE_KEY = 'fusion_badge_sources';
const SOURCE_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000; // 6 hours, matches backend cache TTL

export const MAX_FUSION_BADGE_URLS = 3;
export const DEFAULT_FUSION_BADGE_URL = 'https://pastebin.com/raw/5xiu5fLL';

export type BadgePosition = 'top' | 'bottom';

export interface FusionBadgeSourceState {
  url: string;
  source: FusionBadgeSource | null;
  loading: boolean;
  error: string | null;
}

type FusionBadgeContextValue = {
  fusionBadgesEnabled: boolean;
  setFusionBadgesEnabled: (value: boolean) => Promise<void>;
  showSizeBadges: boolean;
  setShowSizeBadges: (value: boolean) => Promise<void>;
  badgePosition: BadgePosition;
  setBadgePosition: (value: BadgePosition) => Promise<void>;
  badgeUrls: string[];
  addBadgeUrl: (url: string) => Promise<{ ok: boolean; error?: string }>;
  removeBadgeUrl: (url: string) => Promise<void>;
  refreshBadgeUrl: (url: string) => Promise<void>;
  sources: Record<string, FusionBadgeSourceState>;
  getBadgesForStream: (stream: AddonStream) => FusionBadgeFilter[];
  getBadgeGroupsForStream: (stream: AddonStream) => FusionBadgeGroupMatches[];
  isReady: boolean;
};

const FusionBadgeContext = createContext<FusionBadgeContextValue>({
  fusionBadgesEnabled: true,
  setFusionBadgesEnabled: async () => {},
  showSizeBadges: true,
  setShowSizeBadges: async () => {},
  badgePosition: 'bottom',
  setBadgePosition: async () => {},
  badgeUrls: [DEFAULT_FUSION_BADGE_URL],
  addBadgeUrl: async () => ({ ok: false, error: 'Not ready' }),
  removeBadgeUrl: async () => {},
  refreshBadgeUrl: async () => {},
  sources: {},
  getBadgesForStream: () => [],
  getBadgeGroupsForStream: () => [],
  isReady: false,
});

type Settings = {
  fusionBadgesEnabled: boolean;
  showSizeBadges: boolean;
  badgePosition: BadgePosition;
  badgeUrls: string[];
};

const DEFAULT_SETTINGS: Settings = {
  fusionBadgesEnabled: true,
  showSizeBadges: true,
  badgePosition: 'bottom',
  badgeUrls: [DEFAULT_FUSION_BADGE_URL],
};

export const FusionBadgeProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { activeProfile } = useProfile();

  const [fusionBadgesEnabled, setFusionBadgesEnabledState] = useState(DEFAULT_SETTINGS.fusionBadgesEnabled);
  const [showSizeBadges, setShowSizeBadgesState] = useState(DEFAULT_SETTINGS.showSizeBadges);
  const [badgePosition, setBadgePositionState] = useState<BadgePosition>(DEFAULT_SETTINGS.badgePosition);
  const [badgeUrls, setBadgeUrlsState] = useState<string[]>(DEFAULT_SETTINGS.badgeUrls);
  const [sources, setSources] = useState<Record<string, FusionBadgeSourceState>>({});
  const [isReady, setIsReady] = useState(false);

  const settingsRef = useRef<Settings>({ ...DEFAULT_SETTINGS });
  const sourcesRef = useRef<Record<string, FusionBadgeSourceState>>({});
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const hasLocalOverrideDuringHydrationRef = useRef(false);

  const settingsStorageKey = profileScopedStorageKey(SETTINGS_STORAGE_KEY, user?.uid, activeProfile?.id);

  sourcesRef.current = sources;

  const persistSourcesCache = useCallback((next: Record<string, FusionBadgeSourceState>) => {
    const cache: Record<string, FusionBadgeSource> = {};
    Object.values(next).forEach(entry => {
      if (entry.source) cache[entry.url] = entry.source;
    });
    void Storage.setItem(SOURCES_STORAGE_KEY, JSON.stringify(cache)).catch(() => {});
  }, []);

  const loadSource = useCallback(async (url: string, options: { refresh?: boolean } = {}) => {
    setSources(prev => ({
      ...prev,
      [url]: { url, source: prev[url]?.source ?? null, loading: true, error: null },
    }));

    try {
      const source = await fetchFusionBadgeSource(user, url, options);
      setSources(prev => {
        const next = { ...prev, [url]: { url, source, loading: false, error: null } };
        persistSourcesCache(next);
        return next;
      });
    } catch (err: any) {
      setSources(prev => ({
        ...prev,
        [url]: { url, source: prev[url]?.source ?? null, loading: false, error: err?.message ?? 'Failed to load badges' },
      }));
    }
  }, [persistSourcesCache, user]);

  const applyRemoteStreams = useCallback((remotePreferences: any | null) => {
    if (hasLocalOverrideDuringHydrationRef.current && !isReady) return;

    const remoteStreams = remotePreferences?.streams;
    if (!remoteStreams) return;

    const nextFusionBadgesEnabled = typeof remoteStreams.fusionBadgesEnabled === 'boolean'
      ? remoteStreams.fusionBadgesEnabled
      : settingsRef.current.fusionBadgesEnabled;
    const nextShowSizeBadges = typeof remoteStreams.showSizeBadges === 'boolean'
      ? remoteStreams.showSizeBadges
      : settingsRef.current.showSizeBadges;
    const nextBadgePosition = remoteStreams.badgePosition === 'top' || remoteStreams.badgePosition === 'bottom'
      ? remoteStreams.badgePosition
      : settingsRef.current.badgePosition;
    const nextBadgeUrls = Array.isArray(remoteStreams.fusionBadgeUrls) && remoteStreams.fusionBadgeUrls.length > 0
      ? remoteStreams.fusionBadgeUrls.filter((u: unknown): u is string => typeof u === 'string' && u.trim().length > 0).slice(0, MAX_FUSION_BADGE_URLS)
      : settingsRef.current.badgeUrls;

    settingsRef.current = {
      fusionBadgesEnabled: nextFusionBadgesEnabled,
      showSizeBadges: nextShowSizeBadges,
      badgePosition: nextBadgePosition,
      badgeUrls: nextBadgeUrls,
    };

    setFusionBadgesEnabledState(nextFusionBadgesEnabled);
    setShowSizeBadgesState(nextShowSizeBadges);
    setBadgePositionState(nextBadgePosition);
    setBadgeUrlsState(nextBadgeUrls);

    void Storage.setItem(settingsStorageKey, JSON.stringify(settingsRef.current));

    nextBadgeUrls.forEach((url: string) => {
      const existing = sourcesRef.current[url];
      const isStale = !existing?.source || (Date.now() - new Date(existing.source.fetchedAt).getTime()) > SOURCE_REFRESH_INTERVAL_MS;
      if (isStale && !existing?.loading) void loadSource(url);
    });
  }, [isReady, loadSource, settingsStorageKey]);

  useEffect(() => {
    let cancelled = false;

    Promise.all([
      Storage.getItem(settingsStorageKey),
      Storage.getItem(SOURCES_STORAGE_KEY),
      fetchAccountPreferences(user),
    ]).then(([rawSettings, rawSources, remotePreferences]) => {
      if (cancelled) return;

      if (rawSettings) {
        try {
          const parsed = JSON.parse(rawSettings);
          const next: Settings = {
            fusionBadgesEnabled: typeof parsed?.fusionBadgesEnabled === 'boolean' ? parsed.fusionBadgesEnabled : DEFAULT_SETTINGS.fusionBadgesEnabled,
            showSizeBadges: typeof parsed?.showSizeBadges === 'boolean' ? parsed.showSizeBadges : DEFAULT_SETTINGS.showSizeBadges,
            badgePosition: parsed?.badgePosition === 'top' || parsed?.badgePosition === 'bottom' ? parsed.badgePosition : DEFAULT_SETTINGS.badgePosition,
            badgeUrls: Array.isArray(parsed?.badgeUrls) && parsed.badgeUrls.length > 0
              ? parsed.badgeUrls.filter((u: unknown): u is string => typeof u === 'string' && u.trim().length > 0).slice(0, MAX_FUSION_BADGE_URLS)
              : DEFAULT_SETTINGS.badgeUrls,
          };
          settingsRef.current = next;
          setFusionBadgesEnabledState(next.fusionBadgesEnabled);
          setShowSizeBadgesState(next.showSizeBadges);
          setBadgePositionState(next.badgePosition);
          setBadgeUrlsState(next.badgeUrls);
        } catch {
          // Ignore malformed persisted settings.
        }
      }

      if (rawSources) {
        try {
          const parsed = JSON.parse(rawSources) as Record<string, FusionBadgeSource>;
          const next: Record<string, FusionBadgeSourceState> = {};
          Object.entries(parsed).forEach(([url, source]) => {
            next[url] = { url, source, loading: false, error: null };
          });
          setSources(next);
          sourcesRef.current = next;
        } catch {
          // Ignore malformed cache.
        }
      }

      void applyRemoteStreams(remotePreferences);

      // Ensure every configured badge URL has a source, even if not present in cache/remote.
      settingsRef.current.badgeUrls.forEach(url => {
        if (!sourcesRef.current[url]) void loadSource(url);
      });
    }).finally(() => {
      if (!cancelled) setIsReady(true);
    });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, settingsStorageKey]);

  const persist = useCallback((next: Settings) => {
    settingsRef.current = next;
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
    persistTimerRef.current = setTimeout(() => {
      const snapshot = settingsRef.current;
      void Storage.setItem(settingsStorageKey, JSON.stringify(snapshot)).catch(() => {});
      void patchAccountPreferences(user, {
        streams: {
          fusionBadgesEnabled: snapshot.fusionBadgesEnabled,
          showSizeBadges: snapshot.showSizeBadges,
          badgePosition: snapshot.badgePosition,
          fusionBadgeUrls: snapshot.badgeUrls,
        },
      });
      persistTimerRef.current = null;
    }, 100);
  }, [settingsStorageKey, user]);

  const setFusionBadgesEnabled = useCallback(async (value: boolean) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setFusionBadgesEnabledState(value);
    persist({ ...settingsRef.current, fusionBadgesEnabled: value });
  }, [isReady, persist]);

  const setShowSizeBadges = useCallback(async (value: boolean) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setShowSizeBadgesState(value);
    persist({ ...settingsRef.current, showSizeBadges: value });
  }, [isReady, persist]);

  const setBadgePosition = useCallback(async (value: BadgePosition) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setBadgePositionState(value);
    persist({ ...settingsRef.current, badgePosition: value });
  }, [isReady, persist]);

  const addBadgeUrl = useCallback(async (rawUrl: string): Promise<{ ok: boolean; error?: string }> => {
    const url = rawUrl.trim();
    if (!/^https:\/\//i.test(url)) {
      return { ok: false, error: 'Enter a valid https:// URL' };
    }
    if (settingsRef.current.badgeUrls.includes(url)) {
      return { ok: false, error: 'This URL is already imported' };
    }
    if (settingsRef.current.badgeUrls.length >= MAX_FUSION_BADGE_URLS) {
      return { ok: false, error: `You can import up to ${MAX_FUSION_BADGE_URLS} URLs` };
    }

    try {
      const source = await fetchFusionBadgeSource(user, url);
      setSources(prev => {
        const next = { ...prev, [url]: { url, source, loading: false, error: null } };
        persistSourcesCache(next);
        return next;
      });
    } catch (err: any) {
      return { ok: false, error: err?.message ?? 'Failed to load Fusion badge source' };
    }

    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    const nextUrls = [...settingsRef.current.badgeUrls, url];
    setBadgeUrlsState(nextUrls);
    persist({ ...settingsRef.current, badgeUrls: nextUrls });
    return { ok: true };
  }, [isReady, persist, persistSourcesCache, user]);

  const removeBadgeUrl = useCallback(async (url: string) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    const nextUrls = settingsRef.current.badgeUrls.filter(u => u !== url);
    setBadgeUrlsState(nextUrls);
    persist({ ...settingsRef.current, badgeUrls: nextUrls });
    setSources(prev => {
      const next = { ...prev };
      delete next[url];
      persistSourcesCache(next);
      return next;
    });
  }, [isReady, persist, persistSourcesCache]);

  const refreshBadgeUrl = useCallback(async (url: string) => {
    await loadSource(url, { refresh: true });
  }, [loadSource]);

  const activeSources = useMemo(
    () => badgeUrls.map(url => sources[url]?.source).filter((s): s is FusionBadgeSource => !!s),
    [badgeUrls, sources],
  );

  const getBadgeGroupsForStream = useCallback((stream: AddonStream): FusionBadgeGroupMatches[] => {
    if (!fusionBadgesEnabled || activeSources.length === 0) return [];
    return matchFusionBadges(stream, activeSources);
  }, [activeSources, fusionBadgesEnabled]);

  const getBadgesForStream = useCallback((stream: AddonStream): FusionBadgeFilter[] => {
    return flattenFusionBadges(getBadgeGroupsForStream(stream));
  }, [getBadgeGroupsForStream]);

  const value = useMemo<FusionBadgeContextValue>(() => ({
    fusionBadgesEnabled,
    setFusionBadgesEnabled,
    showSizeBadges,
    setShowSizeBadges,
    badgePosition,
    setBadgePosition,
    badgeUrls,
    addBadgeUrl,
    removeBadgeUrl,
    refreshBadgeUrl,
    sources,
    getBadgesForStream,
    getBadgeGroupsForStream,
    isReady,
  }), [
    fusionBadgesEnabled, setFusionBadgesEnabled,
    showSizeBadges, setShowSizeBadges,
    badgePosition, setBadgePosition,
    badgeUrls, addBadgeUrl, removeBadgeUrl, refreshBadgeUrl,
    sources, getBadgesForStream, getBadgeGroupsForStream,
    isReady,
  ]);

  return (
    <FusionBadgeContext.Provider value={value}>
      {children}
    </FusionBadgeContext.Provider>
  );
};

export const useFusionBadges = () => useContext(FusionBadgeContext);
