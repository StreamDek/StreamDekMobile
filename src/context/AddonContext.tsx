import React, {
  createContext, useContext, useState, useCallback, useEffect, useMemo, useRef,
} from 'react';
import { API_BASE } from '../constants/api';
import { useAuth } from './AuthContext';
import { useLanguage } from './LanguageContext';
import { Storage } from '../utils/storage';
import { DebridProviderName } from './DebridContext';
import {
  scoreStream,
  sortStreams,
} from '../utils/streamSelection';
import type { StreamScoreOptions } from '../utils/streamSelection';
import { useStreamSelectionSettings } from './StreamSelectionContext';
import { buildAuthHeaders } from '../utils/authHeaders';

const CACHE_TTL = 10 * 60 * 1000; // 10 minutes
const ULTRA_BOOST_STORAGE_KEY = 'streamdek_ultra_boost_enabled';


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

  // In-memory stream cache — survives re-renders, cleared on unmount
  const streamCache = useRef<Map<string, CacheEntry>>(new Map());

  const refreshUltraEntitlement = useCallback(async () => {
    if (!user) {
      setUltraEntitled(false);
      setUltraBoostEnabledState(false);
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/addons/ultra/entitlement`, {
        headers: await buildAuthHeaders(user, { includeContentType: false }),
      });
      if (!res.ok) {
        setUltraEntitled(false);
        setUltraBoostEnabledState(false);
        return;
      }

      const data = await res.json();
      const entitled = !!data.ultra;
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
    }
  }, [user]);

  useEffect(() => { void refreshUltraEntitlement(); }, [refreshUltraEntitlement]);

  const setUltraBoostEnabled = useCallback(async (enabled: boolean) => {
    streamCache.current.clear();
    setUltraBoostEnabledState(enabled);
    await Storage.setItem(ULTRA_BOOST_STORAGE_KEY, enabled ? 'true' : 'false');
  }, []);

  const refreshAddons = useCallback(async () => {
    if (!user) { setAddons([]); return; }
    setIsLoading(true);
    try {
      const res = await fetch(`${API_BASE}/addons/manifests`, {
        headers: await buildAuthHeaders(user, { includeContentType: false }),
      });
      if (res.ok) {
        const data = await res.json();
        setAddons(data ?? []);
      }
    } catch { /* keep stale list on network error */ }
    finally { setIsLoading(false); }
  }, [user]);

  useEffect(() => { refreshAddons(); }, [refreshAddons]);

  const installAddon = useCallback(async (
    url: string,
  ): Promise<{ success: boolean; error?: string }> => {
    try {
      const normalizedUrl = normalizeManifestUrl(url);
      const res = await fetch(`${API_BASE}/addons/install`, {
        method: 'POST',
        headers: await buildAuthHeaders(user),
        body: JSON.stringify({ url: normalizedUrl }),
      });
      const data = await res.json();
      if (!res.ok) return { success: false, error: data.error ?? t('error_install_failed') };
      await refreshAddons();
      return { success: true };
    } catch {
      return { success: false, error: t('common_network_error') };
    }
  }, [refreshAddons, t, user]);

  const uninstallAddon = useCallback(async (id: string) => {
    const previousAddons = addons;
    setAddons(prev => prev.filter(addon => addon.id !== id));
    await fetch(`${API_BASE}/addons/uninstall`, {
      method: 'DELETE',
      headers: await buildAuthHeaders(user),
      body: JSON.stringify({ id }),
    }).catch(() => {
      setAddons(previousAddons);
    });
    void refreshAddons();
  }, [addons, refreshAddons, user]);

  const toggleAddon = useCallback(async (id: string, enabled: boolean) => {
    // Invalidate stream cache — enabled addon set has changed
    streamCache.current.clear();
    const previousAddons = addons;
    // Addon enabled state is a simple preference; update locally before backend persistence.
    setAddons(prev => prev.map(a => a.id === id ? { ...a, enabled } : a));
    void fetch(`${API_BASE}/addons/toggle`, {
      method: 'POST',
      headers: await buildAuthHeaders(user),
      body: JSON.stringify({ id, enabled }),
    }).catch(() => {
      setAddons(previousAddons);
    });
  }, [addons, user]);

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
        headers: await buildAuthHeaders(user),
        body: JSON.stringify({ order: orderedIds }),
      });
    } catch {
      await refreshAddons(); // revert on failure
    }
  }, [refreshAddons, user]);

  const streamScoreOptions = useMemo<StreamScoreOptions>(() => ({
    preferredQuality,
    maxFileSizeGB: maxFileSizeGB > 0 ? maxFileSizeGB : undefined,
  }), [maxFileSizeGB, preferredQuality]);

  const applyStreamSelection = useCallback((incoming: AddonStream[]): AddonStream[] => {
    if (!streamSelectionEnabled) return incoming;

    // Apply local stream rules before results hit UI state/cache. This keeps
    // third-party addons aligned with StreamDek Ultra for playable checks,
    // preferred quality ordering, and the hard max-file-size constraint.
    return sortStreams(
      incoming.filter(stream => scoreStream(stream, streamScoreOptions) > -10000),
      streamScoreOptions,
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
        { headers: await buildAuthHeaders(user, { includeContentType: false }), signal },
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
  }, [shouldFetchUltra, user]);

  // ── Batch fetch (used by PlayerScreen auto-selection) ─────────────────────

  const fetchStreams = useCallback(async (
    type: string,
    videoId: string,
  ): Promise<AddonStream[]> => {
    try {
      const [addonStreams, ultraStreams] = await Promise.all([
        fetch(
          `${API_BASE}/addons/streams/${type}/${encodeURIComponent(videoId)}`,
          { headers: await buildAuthHeaders(user, { includeContentType: false }) },
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
  }, [applyStreamSelection, fetchUltraStreams, user]);

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
          { headers: await buildAuthHeaders(user, { includeContentType: false }), signal },
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
  }, [addons, applyStreamSelection, fetchUltraStreams, maxFileSizeGB, preferredQuality, shouldFetchUltra, streamSelectionEnabled, user]);

  return (
    <AddonContext.Provider value={{
      addons, isLoading,
      ultraEntitled, ultraBoostEnabled, setUltraBoostEnabled, refreshUltraEntitlement,
      installAddon, uninstallAddon, toggleAddon,
      fetchStreams, fetchStreamsProgressive, refreshAddons, reorderAddons,
    }}>
      {children}
    </AddonContext.Provider>
  );
};

export const useAddons = () => useContext(AddonContext);
