import React, {
  createContext, useCallback, useContext, useEffect, useRef, useState,
} from 'react';
import { API_BASE } from '../constants/api';
import { useAuth } from './AuthContext';
import { useLanguage } from './LanguageContext';
import { Storage } from '../utils/storage';
import { DebridProviderName } from './DebridContext';
import { buildAuthHeaders } from '../utils/authHeaders';
import { getMobileClientIdentityHeaders } from '../utils/clientIdentity';
import { getSharedCachedAsync, invalidateSharedCache } from '../utils/sharedDataCache';
import { getStreamCapableAddons } from '../utils/addonCapabilities';

const CACHE_TTL = 10 * 60 * 1000;
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
  url?: string;
  baseUrl?: string;
  manifestUrl?: string;
  transportUrl?: string;
  resources: (string | { name: string; types: string[] })[];
  types: string[];
  catalogs: { type: string; id: string; name: string }[];
  idPrefixes?: string[];
}

export interface InstalledAddon {
  id: string;
  enabled: boolean;
  position: number;
  url?: string;
  baseUrl?: string;
  manifestUrl?: string;
  transportUrl?: string;
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
  quality: string | null;
  size: string | null;
  cachedBy: DebridProviderName[];
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
  fetchStreams(type: string, videoId: string): Promise<AddonStream[]>;
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
  installAddon: async () => ({ success: false }),
  uninstallAddon: async () => {},
  toggleAddon: async () => {},
  fetchStreams: async () => [],
  fetchStreamsProgressive: async () => {},
  refreshAddons: async () => {},
  reorderAddons: async () => {},
});

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

export const AddonProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { t } = useLanguage();
  const [addons, setAddons] = useState<InstalledAddon[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [ultraEntitled, setUltraEntitled] = useState(false);
  const [ultraBoostEnabled, setUltraBoostEnabledState] = useState(false);
  const [ultraManifest, setUltraManifest] = useState<UltraManifestMeta | null>(null);
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
    } catch {
      // Keep stale list on network error.
    } finally {
      setIsLoading(false);
    }
  }, [buildAddonHeaders, user]);

  useEffect(() => { void refreshAddons(); }, [refreshAddons]);

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
    streamCache.current.clear();
    const previousAddons = addons;
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
      await refreshAddons();
    }
  }, [buildAddonHeaders, refreshAddons]);

  const fetchStreams = useCallback(async (
    type: string,
    videoId: string,
  ): Promise<AddonStream[]> => {
    try {
      const streamCapableAddonIds = new Set(getStreamCapableAddons(addons).map(addon => addon.id));
      const res = await fetch(
        `${API_BASE}/addons/streams/${type}/${encodeURIComponent(videoId)}`,
        { headers: await buildAddonHeaders({ includeContentType: false }) },
      );
      if (!res.ok) return [];
      const data = await res.json();
      return (data.streams ?? []).filter((stream: AddonStream) => (
        !stream.addonId || streamCapableAddonIds.has(stream.addonId)
      ));
    } catch {
      return [];
    }
  }, [addons, buildAddonHeaders]);

  const fetchStreamsProgressive = useCallback(async (
    type: string,
    videoId: string,
    onUpdate: (streams: AddonStream[], pendingCount: number) => void,
    signal?: AbortSignal,
  ): Promise<void> => {
    const enabledAddons = getStreamCapableAddons(addons)
      .sort((a, b) => a.position - b.position);
    const ultraActive = ultraEntitled && ultraBoostEnabled;
    const enabledAddonKey = enabledAddons.map(a => `${a.id}:${a.position}`).join(',');
    const cacheKey = `${type}:${videoId}:${enabledAddonKey}:${ultraActive ? 'ultra-on' : 'ultra-off'}`;

    const cached = streamCache.current.get(cacheKey);
    if (cached && cached.expiresAt > Date.now()) {
      onUpdate(cached.streams, 0);
      return;
    }

    const totalSources = enabledAddons.length + (ultraActive ? 1 : 0);
    if (totalSources === 0) {
      onUpdate([], 0);
      return;
    }

    onUpdate([], totalSources);

    const accumulated: AddonStream[] = [];
    let pending = totalSources;
    const headers = await buildAddonHeaders({ includeContentType: false });

    // Called by each source (addon or ultra) when it settles.
    // Delivers partial results immediately so the UI populates on first response.
    const settle = () => {
      if (signal?.aborted) return;
      pending--;
      onUpdate([...accumulated], pending);
      if (pending === 0 && accumulated.length > 0) {
        streamCache.current.set(cacheKey, {
          streams: [...accumulated],
          expiresAt: Date.now() + CACHE_TTL,
        });
      }
    };

    // One fetch per enabled addon — fires in parallel, updates on each response
    const addonFetches = enabledAddons.map(async (addon) => {
      try {
        const res = await fetch(
          `${API_BASE}/addons/streams/single/${encodeURIComponent(addon.id)}/${type}/${encodeURIComponent(videoId)}`,
          { headers, signal },
        );
        if (res.ok && !signal?.aborted) {
          const data = await res.json();
          accumulated.push(...(data?.streams ?? []));
        }
      } catch (e: any) {
        if (e?.name !== 'AbortError') {
          console.warn(`[AddonContext] Addon ${addon.id} stream fetch failed:`, e?.message);
        }
      }
      settle();
    });

    const ultraFetch = ultraActive
      ? (async () => {
          try {
            const res = await fetch(
              `${API_BASE}/addons/ultra/streams/${type}/${encodeURIComponent(videoId)}`,
              { headers, signal },
            );
            if (res.ok && !signal?.aborted) {
              const data = await res.json();
              accumulated.push(...(data?.streams ?? []));
            }
          } catch (e: any) {
            if (e?.name !== 'AbortError') {
              console.warn('[AddonContext] Ultra stream fetch failed:', e?.message);
            }
          }
          settle();
        })()
      : Promise.resolve();

    await Promise.allSettled([...addonFetches, ultraFetch]);
  }, [addons, buildAddonHeaders, ultraBoostEnabled, ultraEntitled]);

  return (
    <AddonContext.Provider value={{
      addons,
      isLoading,
      ultraEntitled,
      ultraBoostEnabled,
      ultraManifest,
      setUltraBoostEnabled,
      refreshUltraEntitlement,
      installAddon,
      uninstallAddon,
      toggleAddon,
      fetchStreams,
      fetchStreamsProgressive,
      refreshAddons,
      reorderAddons,
    }}
    >
      {children}
    </AddonContext.Provider>
  );
};

export const useAddons = () => useContext(AddonContext);
