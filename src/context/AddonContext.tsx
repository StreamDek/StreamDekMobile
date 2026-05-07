import React, {
  createContext, useContext, useState, useCallback, useEffect, useMemo, useRef,
} from 'react';
import { API_BASE } from '../constants/api';
import { useAuth } from './AuthContext';
import { useLanguage } from './LanguageContext';
import { Storage } from '../utils/storage';
import { DebridProviderName } from './DebridContext';
import {
  isAllowedPlaybackStream,
  scoreStream,
  sortStreams,
} from '../utils/streamSelection';
import type { StreamScoreOptions } from '../utils/streamSelection';
import { useStreamSelectionSettings } from './StreamSelectionContext';
import { buildAuthHeaders } from '../utils/authHeaders';
import { getMobileClientIdentityHeaders } from '../utils/clientIdentity';
import { getSharedCachedAsync, invalidateSharedCache } from '../utils/sharedDataCache';

const CACHE_TTL = 10 * 60 * 1000; // 10 minutes
const ULTRA_BOOST_STORAGE_KEY = 'streamdek_ultra_boost_enabled';
const ADDON_STATE_TTL_MS = 20_000;

export interface UltraManifestMeta {
  name: string;
  version: string;
}


export interface AddonManifest {
  id: string;
  name: string;
  version: string;
  description?: string;
  logo?: string;
  resources: (string | { name: string; types: string[] })[];
  types: string[];
  catalogs: { type: string; id: string; name: string }[];
  idPrefixes?: string[];
}

export interface InstalledAddon {
  id: string;        // UUID primary key
  enabled: boolean;
  position: number;  // display + stream priority order
  manifest: AddonManifest;
}

export interface AddonStream {
  addonId: string;
  addonName: string;
  name?: string;
  title?: string;
  description?: string;
  url?: string;
  infoHash?: string;
  fileIdx?: number;
  behaviorHints?: { filename?: string; bingeGroup?: string; videoSize?: number };
  /** Parsed quality label e.g. '4K', '1080p', '720p' */
  quality: string | null;
  /** Parsed size string e.g. '12.4 GB' */
  size: string | null;
  /** Which Debrid providers have this hash cached. */
  cachedBy: DebridProviderName[];
  /** Ultra raw torrent results are surfaced directly by the backend. */
  streamdekAllowRawTorrent?: boolean;
}

interface CacheEntry {
  streams: AddonStream[];
  expiresAt: number;
}

interface AddonContextType {
  addons: InstalledAddon[];
  isLoading: boolean;
  ultraEntitled: boolean;
  ultraBoostEnabled: boolean;
  ultraManifest: UltraManifestMeta | null;
  setUltraBoostEnabled(enabled: boolean): Promise<void>;
  refreshUltraEntitlement(): Promise<void>;
  installAddon(url: string): Promise<{ success: boolean; error?: string }>;
  uninstallAddon(id: string): Promise<void>;
  toggleAddon(id: string, enabled: boolean): Promise<void>;
  /**
   * Batch fetch — waits for ALL enabled addons and returns combined results.
   * Used by PlayerScreen for auto-selection (user doesn't see the list).
   */
  fetchStreams(type: string, videoId: string): Promise<AddonStream[]>;
  /**
   * Progressive fetch — calls each enabled addon in parallel and invokes
   * `onUpdate` as each one responds so the UI can render results incrementally.
   * Results are deduplicated across addons. Cached for CACHE_TTL ms.
   *
   * @param onUpdate  Called after every addon resolves with the accumulated
   *                  stream list and the number of addons still pending.
   * @param signal    Optional AbortSignal to cancel all in-flight requests.
   */
  fetchStreamsProgressive(
    type: string,
    videoId: string,
    onUpdate: (streams: AddonStream[], pendingCount: number) => void,
    signal?: AbortSignal,
  ): Promise<void>;
  refreshAddons(): Promise<void>;
  reorderAddons(orderedIds: string[]): Promise<void>;
}

const AddonContext = createContext<AddonContextType>({
  addons: [],
  isLoading: false,
  ultraEntitled: false,
  ultraBoostEnabled: false,
  ultraManifest: null,
  setUltraBoostEnabled: async () => {},
  refreshUltraEntitlement: async () => {},
  installAddon:            async () => ({ success: false }),
  uninstallAddon:          async () => {},
  toggleAddon:             async () => {},
  fetchStreams:            async () => [],
  fetchStreamsProgressive: async () => {},
  refreshAddons:           async () => {},
  reorderAddons:           async () => {},
});

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Removes duplicate streams across addons.
 * Keeps the copy with the most Debrid providers when there's a tie on infoHash.
 */
function deduplicateStreams(streams: AddonStream[]): AddonStream[] {
  const seen = new Map<string, AddonStream>();
  for (const s of streams) {
    const key = s.infoHash?.toLowerCase() ?? s.url ?? '';
    if (!key) continue;
    const existing = seen.get(key);
    if (!existing || s.cachedBy.length > existing.cachedBy.length) {
      seen.set(key, s);
    }
  }
  return Array.from(seen.values());
}

function normalizeAddonMutationError(message: string | undefined, t: (key: any, params?: Record<string, string | number>) => string): string {
  const fallback = t('error_install_failed');
  if (!message) return fallback;

  const normalized = message.toLowerCase();
  if (
    normalized.includes('authentication required')
    || normalized.includes('unauthorized')
    || normalized.includes('not authenticated')
    || normalized.includes('sign in')
    || normalized.includes('login required')
    || normalized.includes('token')
  ) {
    return 'This add-on could not be installed in guest mode right now. Try again shortly, or sign in if you want to sync it to your account.';
  }

  return message;
}

function normalizeManifestUrl(url: string): string {
  const trimmed = url.trim();
  if (!trimmed) return trimmed;
  if (trimmed.startsWith('stremio://')) {
    return `https://${trimmed.slice('stremio://'.length)}`;
  }
  return trimmed;
}

// ── Provider ──────────────────────────────────────────────────────────────────

export const AddonProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { t } = useLanguage();
  const {
    enabled: streamSelectionEnabled,
    preferredQuality,
    maxFileSizeGB,
  } = useStreamSelectionSettings();
  const [addons, setAddons]       = useState<InstalledAddon[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [ultraEntitled, setUltraEntitled] = useState(false);
  const [ultraBoostEnabled, setUltraBoostEnabledState] = useState(false);
  const [ultraManifest, setUltraManifest] = useState<UltraManifestMeta | null>(null);

  // In-memory stream cache — survives re-renders, cleared on unmount
  const streamCache = useRef<Map<string, CacheEntry>>(new Map());

  const buildAddonHeaders = useCallback(async (
    options: {
      includeContentType?: boolean;
      headers?: HeadersInit;
    } = {},
  ) => ({
    ...(await buildAuthHeaders(user, options)),
    ...(await getMobileClientIdentityHeaders()),
  }), [user]);

  const refreshUltraEntitlement = useCallback(async () => {
    if (!user) {
      setUltraEntitled(false);
      setUltraBoostEnabledState(false);
      setUltraManifest(null);
      return;
    }

    try {
      const data = await getSharedCachedAsync(
        `addons:ultra:${user.uid}`,
        ADDON_STATE_TTL_MS,
        async () => {
          const res = await fetch(`${API_BASE}/addons/ultra/entitlement`, {
            headers: await buildAddonHeaders({ includeContentType: false }),
          });
          if (!res.ok) {
            throw new Error('Ultra entitlement unavailable');
          }
          return res.json();
        },
      );
      const entitled = !!data.ultra;
      const manifest = data?.manifest;
      setUltraManifest(
        manifest && typeof manifest.name === 'string' && typeof manifest.version === 'string'
          ? { name: manifest.name, version: manifest.version }
          : null,
      );
      setUltraEntitled(entitled);
      if (!entitled) {
        setUltraBoostEnabledState(false);
        return;
      }

      const stored = await Storage.getItem(ULTRA_BOOST_STORAGE_KEY);
      setUltraBoostEnabledState(stored == null ? true : stored === 'true');
    } catch {
      setUltraEntitled(false);
      setUltraBoostEnabledState(false);
      setUltraManifest(null);
    }
  }, [buildAddonHeaders, user]);

  useEffect(() => { void refreshUltraEntitlement(); }, [refreshUltraEntitlement]);

  const setUltraBoostEnabled = useCallback((enabled: boolean): Promise<void> => {
    streamCache.current.clear();
    setUltraBoostEnabledState(enabled);
    void Storage.setItem(ULTRA_BOOST_STORAGE_KEY, enabled ? 'true' : 'false').catch(() => {});
    return Promise.resolve();
  }, []);

  const refreshAddons = useCallback(async () => {
    setIsLoading(true);
    try {
      const scopeKey = user ? `addons:manifests:${user.uid}` : 'addons:manifests:guest';
      const data = await getSharedCachedAsync(
        scopeKey,
        ADDON_STATE_TTL_MS,
        async () => {
          const res = await fetch(`${API_BASE}/addons/manifests`, {
            headers: await buildAddonHeaders({ includeContentType: false }),
          });
          if (!res.ok) return [];
          return res.json();
        },
      );
      setAddons(data ?? []);
    } catch { /* keep stale list on network error */ }
    finally { setIsLoading(false); }
  }, [buildAddonHeaders, user]);

  useEffect(() => { refreshAddons(); }, [refreshAddons]);

  const installAddon = useCallback(async (
    url: string,
  ): Promise<{ success: boolean; error?: string }> => {
    try {
      const normalizedUrl = normalizeManifestUrl(url);
      const res = await fetch(`${API_BASE}/addons/install`, {
        method: 'POST',
        headers: await buildAddonHeaders(),
        body: JSON.stringify({ url: normalizedUrl }),
      });
      const data = await res.json();
      if (!res.ok) {
        return {
          success: false,
          error: normalizeAddonMutationError(data?.error, t),
        };
      }
      invalidateSharedCache('addons:manifests:');
      await refreshAddons();
      return { success: true };
    } catch {
      return { success: false, error: t('common_network_error') };
    }
  }, [buildAddonHeaders, refreshAddons, t]);

  const uninstallAddon = useCallback(async (id: string) => {
    const previousAddons = addons;
    setAddons(prev => prev.filter(addon => addon.id !== id));
    await fetch(`${API_BASE}/addons/uninstall`, {
      method: 'DELETE',
      headers: await buildAddonHeaders(),
      body: JSON.stringify({ id }),
    }).catch(() => {
      setAddons(previousAddons);
    });
    invalidateSharedCache('addons:manifests:');
    void refreshAddons();
  }, [addons, buildAddonHeaders, refreshAddons]);

  const toggleAddon = useCallback(async (id: string, enabled: boolean) => {
    // Invalidate stream cache — enabled addon set has changed
    streamCache.current.clear();
    const previousAddons = addons;
    // Addon enabled state is a simple preference; update locally before backend persistence.
    setAddons(prev => prev.map(a => a.id === id ? { ...a, enabled } : a));
    void fetch(`${API_BASE}/addons/toggle`, {
      method: 'POST',
      headers: await buildAddonHeaders(),
      body: JSON.stringify({ id, enabled }),
    }).then(() => {
      invalidateSharedCache('addons:manifests:');
    }).catch(() => {
      setAddons(previousAddons);
    });
  }, [addons, buildAddonHeaders]);

  const reorderAddons = useCallback(async (orderedIds: string[]) => {
    // Optimistic update: re-index positions locally so the UI responds instantly
    setAddons(prev => {
      const byId = new Map(prev.map(a => [a.id, a]));
      return orderedIds
        .filter(id => byId.has(id))
        .map((id, index) => ({ ...byId.get(id)!, position: index }));
    });
    try {
      await fetch(`${API_BASE}/addons/reorder`, {
        method: 'POST',
        headers: await buildAddonHeaders(),
        body: JSON.stringify({ order: orderedIds }),
      });
      invalidateSharedCache('addons:manifests:');
    } catch {
      await refreshAddons(); // revert on failure
    }
  }, [buildAddonHeaders, refreshAddons]);

  const streamScoreOptions = useMemo<StreamScoreOptions>(() => ({
    preferredQuality,
    maxFileSizeGB: maxFileSizeGB > 0 ? maxFileSizeGB : undefined,
  }), [maxFileSizeGB, preferredQuality]);

  const applyStreamSelection = useCallback((incoming: AddonStream[]): AddonStream[] => {
    const playableOnly = incoming.filter(
      stream => isAllowedPlaybackStream(stream) || stream.streamdekAllowRawTorrent === true,
    );
    const rankingOptions = streamSelectionEnabled ? streamScoreOptions : undefined;

    // Keep the stream list intelligently ordered even when the explicit stream
    // selection preference is off. User-facing settings only add extra caps and
    // biasing; the baseline scorer still picks sensible sources.
    return sortStreams(
      playableOnly.filter(stream => scoreStream(stream, rankingOptions) > -10000),
      rankingOptions,
    );
  }, [streamScoreOptions, streamSelectionEnabled]);

  const shouldFetchUltra = ultraEntitled && ultraBoostEnabled;

  const fetchUltraStreams = useCallback(async (
    type: string,
    videoId: string,
    signal?: AbortSignal,
  ): Promise<AddonStream[]> => {
    if (!shouldFetchUltra || !user) return [];

    try {
      const res = await fetch(
        `${API_BASE}/addons/ultra/streams/${type}/${encodeURIComponent(videoId)}`,
        { headers: await buildAddonHeaders({ includeContentType: false }), signal },
      );
      if (res.status === 403) setUltraEntitled(false);
      if (!res.ok) return [];
      const data = await res.json();
      return data.streams ?? [];
    } catch (e: any) {
      if (e?.name !== 'AbortError') {
        console.warn('[AddonContext] Ultra Boost failed:', e?.message);
      }
      return [];
    }
  }, [buildAddonHeaders, shouldFetchUltra, user]);

  // ── Batch fetch (used by PlayerScreen auto-selection) ─────────────────────

  const fetchStreams = useCallback(async (
    type: string,
    videoId: string,
  ): Promise<AddonStream[]> => {
    try {
      const [addonStreams, ultraStreams] = await Promise.all([
        fetch(
          `${API_BASE}/addons/streams/${type}/${encodeURIComponent(videoId)}`,
          { headers: await buildAddonHeaders({ includeContentType: false }) },
        ).then(async (res) => {
          if (!res.ok) return [];
          const data = await res.json();
          return data.streams ?? [];
        }).catch(() => []),
        fetchUltraStreams(type, videoId),
      ]);
      return applyStreamSelection(deduplicateStreams([...addonStreams, ...ultraStreams]));
    } catch {
      return [];
    }
  }, [applyStreamSelection, buildAddonHeaders, fetchUltraStreams]);

  // ── Progressive fetch (used by stream list screens) ───────────────────────

  const fetchStreamsProgressive = useCallback(async (
    type: string,
    videoId: string,
    onUpdate: (streams: AddonStream[], pendingCount: number) => void,
    signal?: AbortSignal,
  ): Promise<void> => {
    const enabledAddons = addons
      .filter(a => a.enabled)
      .sort((a, b) => a.position - b.position);
    const enabledAddonKey = enabledAddons.map(a => `${a.id}:${a.position}`).join(',');
    const selectionKey = streamSelectionEnabled
      ? `${preferredQuality}:${maxFileSizeGB}`
      : 'selection-off';
    const ultraKey = shouldFetchUltra ? 'ultra-on' : 'ultra-off';
    const cacheKey = `${type}:${videoId}:${enabledAddonKey}:${selectionKey}:${ultraKey}`;
    const cached   = streamCache.current.get(cacheKey);

    // Serve from cache immediately; skip network requests
    if (cached && cached.expiresAt > Date.now()) {
      onUpdate(cached.streams, 0);
      return;
    }

    if (enabledAddons.length === 0 && !shouldFetchUltra) { onUpdate([], 0); return; }

    let accumulated: AddonStream[] = [];
    let pending = enabledAddons.length + (shouldFetchUltra ? 1 : 0);

    const mergeIncoming = (incoming: AddonStream[]) => {
      accumulated = applyStreamSelection(deduplicateStreams([...accumulated, ...incoming]));
    };

    // Signal initial pending count before any requests fire
    onUpdate([], pending);

    const promises = enabledAddons.map(async (addon) => {
      try {
        const res = await fetch(
          `${API_BASE}/addons/streams/single/${addon.id}/${type}/${encodeURIComponent(videoId)}`,
          { headers: await buildAddonHeaders({ includeContentType: false }), signal },
        );
        if (res.ok) {
          const data = await res.json();
          const rawIncoming: AddonStream[] = data.streams ?? [];
          const incoming: AddonStream[] = applyStreamSelection(rawIncoming);
          // Merge and deduplicate — prefer entries with more cached providers
          mergeIncoming(incoming);
        }
      } catch (e: any) {
        if (e?.name !== 'AbortError') {
          console.warn(`[AddonContext] Addon ${addon.id} failed:`, e?.message);
        }
      } finally {
        pending--;
        onUpdate([...accumulated], pending);
      }
    });

    if (shouldFetchUltra) {
      promises.push((async () => {
        try {
          const incoming = await fetchUltraStreams(type, videoId, signal);
          mergeIncoming(incoming);
        } finally {
          pending--;
          onUpdate([...accumulated], pending);
        }
      })());
    }

    await Promise.allSettled(promises);

    // Persist to cache only if the request was not cancelled and returned results.
    // Empty results are not cached so that a retry (e.g. after IMDB ID resolves)
    // can attempt the network again rather than being blocked by a stale empty hit.
    if (!signal?.aborted && accumulated.length > 0) {
      streamCache.current.set(cacheKey, {
        streams:   accumulated,
        expiresAt: Date.now() + CACHE_TTL,
      });
    }
  }, [addons, applyStreamSelection, buildAddonHeaders, fetchUltraStreams, maxFileSizeGB, preferredQuality, shouldFetchUltra, streamSelectionEnabled]);

  return (
    <AddonContext.Provider value={{
      addons, isLoading,
      ultraEntitled, ultraBoostEnabled, ultraManifest, setUltraBoostEnabled, refreshUltraEntitlement,
      installAddon, uninstallAddon, toggleAddon,
      fetchStreams, fetchStreamsProgressive, refreshAddons, reorderAddons,
    }}>
      {children}
    </AddonContext.Provider>
  );
};

export const useAddons = () => useContext(AddonContext);
