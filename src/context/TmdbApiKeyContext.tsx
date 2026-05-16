import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { __setTmdbActiveKey } from '../utils/tmdbFetch';
import { fetchAccountPreferences, patchAccountPreferences } from '../utils/accountPreferences';

const storageKey = (uid: string | null | undefined) => uid ? `streamdek_tmdb_key_${uid}` : 'streamdek_tmdb_key_guest';

interface TmdbKeyConfig {
  enabled: boolean;
  apiKey: string;
}

interface TmdbApiKeyContextValue {
  tmdbKeyEnabled: boolean;
  tmdbApiKey: string;
  setTmdbApiKey: (key: string) => Promise<void>;
  setTmdbKeyEnabled: (enabled: boolean) => Promise<void>;
}

const TmdbApiKeyContext = createContext<TmdbApiKeyContextValue>({
  tmdbKeyEnabled: false,
  tmdbApiKey: '',
  setTmdbApiKey: async () => {},
  setTmdbKeyEnabled: async () => {},
});

function readTmdbConfig(preferences: any): TmdbKeyConfig | null {
  const config = preferences?.integrations?.tmdb ?? preferences?.tmdb ?? null;
  if (!config || typeof config !== 'object') return null;
  return {
    enabled: Boolean(config.enabled),
    apiKey: typeof config.apiKey === 'string' ? config.apiKey : '',
  };
}

export function TmdbApiKeyProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [tmdbKeyEnabled, setEnabledState] = useState(false);
  const [tmdbApiKey, setKeyState] = useState('');
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
          __setTmdbActiveKey(remoteConfig.enabled && remoteConfig.apiKey.trim() ? remoteConfig.apiKey.trim() : null);
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
        __setTmdbActiveKey(null);
        return;
      }

      try {
        const config: TmdbKeyConfig = JSON.parse(raw);
        const enabled = config.enabled ?? false;
        const key = config.apiKey ?? '';
        setEnabledState(enabled);
        setKeyState(key);
        __setTmdbActiveKey(enabled && key.trim() ? key.trim() : null);
        if (user) {
          const currentPreferences = accountPreferencesRef.current ?? {};
          const nextPreferences = {
            ...currentPreferences,
            integrations: {
              ...(currentPreferences?.integrations ?? {}),
              tmdb: {
                enabled,
                apiKey: key,
              },
            },
          };
          accountPreferencesRef.current = nextPreferences;
          await patchAccountPreferences(user, nextPreferences);
        }
      } catch {
        setEnabledState(false);
        setKeyState('');
        __setTmdbActiveKey(null);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [user]);

  useEffect(() => {
    __setTmdbActiveKey(tmdbKeyEnabled && tmdbApiKey.trim() ? tmdbApiKey.trim() : null);
  }, [tmdbKeyEnabled, tmdbApiKey]);

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
            tmdb: {
              enabled: config.enabled,
              apiKey: config.apiKey,
            },
          },
        };

        accountPreferencesRef.current = nextPreferences;
        void patchAccountPreferences(user, nextPreferences);
      }

      persistTimerRef.current = null;
    }, 100);
  }, [user]);

  const setTmdbApiKey = useCallback(async (key: string) => {
    setKeyState(key);
    persist({ enabled: tmdbKeyEnabled, apiKey: key });
  }, [tmdbKeyEnabled, persist]);

  const setTmdbKeyEnabled = useCallback(async (enabled: boolean) => {
    setEnabledState(enabled);
    persist({ enabled, apiKey: tmdbApiKey });
  }, [tmdbApiKey, persist]);

  return (
    <TmdbApiKeyContext.Provider value={{ tmdbKeyEnabled, tmdbApiKey, setTmdbApiKey, setTmdbKeyEnabled }}>
      {children}
    </TmdbApiKeyContext.Provider>
  );
}

export function useTmdbApiKey() {
  return useContext(TmdbApiKeyContext);
}
