import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { __setTmdbActiveKey } from '../utils/tmdbFetch';
import { fetchAccountPreferences, patchAccountPreferences } from '../utils/accountPreferences';

const storageKey = (uid: string | null | undefined) => uid ? `streamdek_tmdb_key_${uid}` : 'streamdek_tmdb_key_guest';

interface TmdbKeyConfig {
  enabled: boolean;
  apiKey: string;
  provider: MetadataProvider;
  homeCatalogProviders?: MetadataProvider[];
}

export type MetadataProvider = 'cinemeta' | 'tmdb';

interface TmdbApiKeyContextValue {
  tmdbKeyEnabled: boolean;
  tmdbApiKey: string;
  metadataProvider: MetadataProvider;
  homeCatalogProviders: MetadataProvider[];
  defaultCatalogsEnabled: boolean;
  setTmdbApiKey: (key: string) => Promise<void>;
  setTmdbKeyEnabled: (enabled: boolean) => Promise<void>;
  setMetadataProvider: (provider: MetadataProvider) => Promise<void>;
  setDefaultCatalogsEnabled: (enabled: boolean) => Promise<void>;
  setHomeCatalogProviderEnabled: (provider: MetadataProvider, enabled: boolean) => Promise<void>;
}

const TmdbApiKeyContext = createContext<TmdbApiKeyContextValue>({
  tmdbKeyEnabled: false,
  tmdbApiKey: '',
  metadataProvider: 'cinemeta',
  homeCatalogProviders: ['cinemeta'],
  defaultCatalogsEnabled: true,
  setTmdbApiKey: async () => {},
  setTmdbKeyEnabled: async () => {},
  setMetadataProvider: async () => {},
  setDefaultCatalogsEnabled: async () => {},
  setHomeCatalogProviderEnabled: async () => {},
});

function normalizeProvider(value: unknown): MetadataProvider {
  return value === 'tmdb' ? 'tmdb' : 'cinemeta';
}

function normalizeHomeCatalogProviders(value: unknown, fallbackProvider: MetadataProvider): MetadataProvider[] {
  if (Array.isArray(value) && value.length === 0) return [];
  const providers = Array.isArray(value)
    ? Array.from(new Set(value.map(normalizeProvider)))
    : [fallbackProvider];
  const selected = providers.includes(fallbackProvider)
    ? fallbackProvider
    : (providers[0] ?? fallbackProvider);
  return [selected];
}

function readTmdbConfig(preferences: any): TmdbKeyConfig | null {
  const config = preferences?.integrations?.tmdb ?? preferences?.tmdb ?? null;
  const inferredProvider = (
    config
    && typeof config === 'object'
    && (Boolean(config.enabled) || typeof config.apiKey === 'string' && config.apiKey.trim().length > 0)
  ) ? 'tmdb' : 'cinemeta';
  const provider = normalizeProvider(
    preferences?.integrations?.metadata?.provider
    ?? preferences?.integrations?.metadataProvider
    ?? preferences?.metadataProvider
    ?? config?.provider
    ?? inferredProvider,
  );
  const hasMetadataPreference = (
    preferences?.integrations?.metadata?.provider != null
    || preferences?.integrations?.metadataProvider != null
    || preferences?.metadataProvider != null
  );

  if (!config && !hasMetadataPreference) return null;
  if (!config || typeof config !== 'object') {
    return {
      enabled: false,
      apiKey: '',
      provider,
    };
  }
  return {
    enabled: Boolean(config.enabled),
    apiKey: typeof config.apiKey === 'string' ? config.apiKey : '',
    provider,
    homeCatalogProviders: normalizeHomeCatalogProviders(
      preferences?.integrations?.metadata?.homeCatalogProviders
      ?? preferences?.integrations?.homeCatalogProviders
      ?? preferences?.homeCatalogProviders
      ?? config?.homeCatalogProviders,
      provider,
    ),
  };
}

function getActiveTmdbKey(config: Pick<TmdbKeyConfig, 'provider' | 'enabled' | 'apiKey'>): string | null {
  if (config.provider !== 'tmdb') return null;
  if (!config.enabled) return null;
  const key = config.apiKey.trim();
  return key.length > 0 ? key : null;
}

export function TmdbApiKeyProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [tmdbKeyEnabled, setEnabledState] = useState(false);
  const [tmdbApiKey, setKeyState] = useState('');
  const [metadataProvider, setMetadataProviderState] = useState<MetadataProvider>('cinemeta');
  const [homeCatalogProviders, setHomeCatalogProvidersState] = useState<MetadataProvider[]>(['cinemeta']);
  const loadedUidRef = useRef<string | null>(null);
  const accountPreferencesRef = useRef<any | null>(null);
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const uid = user?.uid ?? null;

    if (uid === loadedUidRef.current) return;
    loadedUidRef.current = uid;

    let cancelled = false;

    (async () => {
      if (user) {
        const remotePreferences = await fetchAccountPreferences(user);
        if (cancelled) return;
        accountPreferencesRef.current = remotePreferences;

        const remoteConfig = readTmdbConfig(remotePreferences);
        if (remoteConfig) {
          setEnabledState(remoteConfig.enabled);
          setKeyState(remoteConfig.apiKey);
          setMetadataProviderState(remoteConfig.provider);
          setHomeCatalogProvidersState(remoteConfig.homeCatalogProviders ?? [remoteConfig.provider]);
          __setTmdbActiveKey(getActiveTmdbKey(remoteConfig));
          await Storage.setItem(storageKey(uid), JSON.stringify(remoteConfig));
          return;
        }
      } else {
        accountPreferencesRef.current = null;
      }

      const raw = await Storage.getItem(storageKey(uid));
      if (cancelled) return;

      if (!raw) {
        setEnabledState(false);
        setKeyState('');
        setMetadataProviderState('cinemeta');
        setHomeCatalogProvidersState(['cinemeta']);
        __setTmdbActiveKey(null);
        return;
      }

      try {
        const config: TmdbKeyConfig = JSON.parse(raw);
        const enabled = config.enabled ?? false;
        const key = config.apiKey ?? '';
        const provider = normalizeProvider(
          config.provider ?? ((enabled || key.trim().length > 0) ? 'tmdb' : 'cinemeta'),
        );
        const nextHomeCatalogProviders = normalizeHomeCatalogProviders(config.homeCatalogProviders, provider);
        setEnabledState(enabled);
        setKeyState(key);
        setMetadataProviderState(provider);
        setHomeCatalogProvidersState(nextHomeCatalogProviders);
        __setTmdbActiveKey(getActiveTmdbKey({ enabled, apiKey: key, provider }));
        if (user) {
          const currentPreferences = accountPreferencesRef.current ?? {};
          const nextPreferences = {
            ...currentPreferences,
            integrations: {
              ...(currentPreferences?.integrations ?? {}),
              metadata: {
                ...(currentPreferences?.integrations?.metadata ?? {}),
                provider,
                homeCatalogProviders: nextHomeCatalogProviders,
              },
              homeCatalogProviders: nextHomeCatalogProviders,
              metadataProvider: provider,
              tmdb: {
                enabled,
                apiKey: key,
                provider,
                homeCatalogProviders: nextHomeCatalogProviders,
              },
            },
            metadataProvider: provider,
            homeCatalogProviders: nextHomeCatalogProviders,
          };
          accountPreferencesRef.current = nextPreferences;
          await patchAccountPreferences(user, nextPreferences);
        }
      } catch {
        setEnabledState(false);
        setKeyState('');
        setMetadataProviderState('cinemeta');
        setHomeCatalogProvidersState(['cinemeta']);
        __setTmdbActiveKey(null);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [user]);

  useEffect(() => {
    __setTmdbActiveKey(getActiveTmdbKey({
      provider: metadataProvider,
      enabled: tmdbKeyEnabled,
      apiKey: tmdbApiKey,
    }));
  }, [metadataProvider, tmdbKeyEnabled, tmdbApiKey]);

  const persist = useCallback((config: TmdbKeyConfig) => {
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
    const uid = user?.uid;

    // TMDB key state should update immediately; local save and account sync are best-effort background work.
    persistTimerRef.current = setTimeout(() => {
      void Storage.setItem(storageKey(uid), JSON.stringify(config)).catch(() => {});

      if (user) {
        const currentPreferences = accountPreferencesRef.current ?? {};
        const nextPreferences = {
          ...currentPreferences,
          integrations: {
            ...(currentPreferences?.integrations ?? {}),
            metadata: {
              ...(currentPreferences?.integrations?.metadata ?? {}),
              provider: config.provider,
              homeCatalogProviders: config.homeCatalogProviders ?? [config.provider],
            },
            homeCatalogProviders: config.homeCatalogProviders ?? [config.provider],
            metadataProvider: config.provider,
            tmdb: {
              enabled: config.enabled,
              apiKey: config.apiKey,
              provider: config.provider,
              homeCatalogProviders: config.homeCatalogProviders ?? [config.provider],
            },
          },
          metadataProvider: config.provider,
          homeCatalogProviders: config.homeCatalogProviders ?? [config.provider],
        };

        accountPreferencesRef.current = nextPreferences;
        void patchAccountPreferences(user, nextPreferences);
      }

      persistTimerRef.current = null;
    }, 100);
  }, [user]);

  const setTmdbApiKey = useCallback(async (key: string) => {
    setKeyState(key);
    persist({ enabled: tmdbKeyEnabled, apiKey: key, provider: metadataProvider, homeCatalogProviders });
  }, [homeCatalogProviders, metadataProvider, tmdbKeyEnabled, persist]);

  const setTmdbKeyEnabled = useCallback(async (enabled: boolean) => {
    setEnabledState(enabled);
    persist({ enabled, apiKey: tmdbApiKey, provider: metadataProvider, homeCatalogProviders });
  }, [homeCatalogProviders, metadataProvider, tmdbApiKey, persist]);

  const setMetadataProvider = useCallback(async (provider: MetadataProvider) => {
    const nextEnabled = provider === 'tmdb' ? tmdbKeyEnabled : false;
    setMetadataProviderState(provider);
    if (provider === 'cinemeta') {
      setEnabledState(false);
    }
    const nextHomeCatalogProviders = homeCatalogProviders.length > 0 ? [provider] : [];
    setHomeCatalogProvidersState(nextHomeCatalogProviders);
    persist({ enabled: nextEnabled, apiKey: tmdbApiKey, provider, homeCatalogProviders: nextHomeCatalogProviders });
  }, [homeCatalogProviders, tmdbApiKey, tmdbKeyEnabled, persist]);

  const setDefaultCatalogsEnabled = useCallback(async (enabled: boolean) => {
    const nextHomeCatalogProviders: MetadataProvider[] = enabled
      ? [homeCatalogProviders[0] ?? metadataProvider ?? 'cinemeta']
      : [];
    setHomeCatalogProvidersState(nextHomeCatalogProviders);
    persist({ enabled: tmdbKeyEnabled, apiKey: tmdbApiKey, provider: metadataProvider, homeCatalogProviders: nextHomeCatalogProviders });
  }, [homeCatalogProviders, metadataProvider, persist, tmdbApiKey, tmdbKeyEnabled]);

  const setHomeCatalogProviderEnabled = useCallback(async (provider: MetadataProvider, enabled: boolean) => {
    const nextProviders: MetadataProvider[] = enabled
      ? [provider]
      : [provider === 'tmdb' ? 'cinemeta' : 'tmdb'];
    setHomeCatalogProvidersState(nextProviders);
    persist({ enabled: tmdbKeyEnabled, apiKey: tmdbApiKey, provider: metadataProvider, homeCatalogProviders: nextProviders });
  }, [metadataProvider, persist, tmdbApiKey, tmdbKeyEnabled]);

  return (
    <TmdbApiKeyContext.Provider
      value={{
        tmdbKeyEnabled,
        tmdbApiKey,
        metadataProvider,
        homeCatalogProviders,
        defaultCatalogsEnabled: homeCatalogProviders.length > 0,
        setTmdbApiKey,
        setTmdbKeyEnabled,
        setMetadataProvider,
        setDefaultCatalogsEnabled,
        setHomeCatalogProviderEnabled,
      }}
    >
      {children}
    </TmdbApiKeyContext.Provider>
  );
}

export function useTmdbApiKey() {
  return useContext(TmdbApiKeyContext);
}
