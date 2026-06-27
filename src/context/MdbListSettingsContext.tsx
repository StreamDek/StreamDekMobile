import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { fetchAccountPreferences, patchAccountPreferences } from '../utils/accountPreferences';
import {
  clearMdbListRatingsCache,
  DEFAULT_MDBLIST_SETTINGS,
  MDBLIST_PROVIDER_AUDIENCE,
  MDBLIST_PROVIDER_IMDB,
  MDBLIST_PROVIDER_LETTERBOXD,
  MDBLIST_PROVIDER_METACRITIC,
  MDBLIST_PROVIDER_TMDB,
  MDBLIST_PROVIDER_TOMATOES,
  MDBLIST_PROVIDER_TRAKT,
  MdbListProviderId,
  MdbListSettings,
} from '../utils/mdblist';

const storageKey = (uid: string | null | undefined) => uid ? `streamdek_mdblist_${uid}` : 'streamdek_mdblist_guest';

type MdbListSettingsContextValue = MdbListSettings & {
  hasApiKey: boolean;
  setEnabled: (enabled: boolean) => Promise<void>;
  setApiKey: (apiKey: string) => Promise<void>;
  setProviderEnabled: (providerId: MdbListProviderId, enabled: boolean) => Promise<void>;
};

const MdbListSettingsContext = createContext<MdbListSettingsContextValue>({
  ...DEFAULT_MDBLIST_SETTINGS,
  hasApiKey: false,
  setEnabled: async () => {},
  setApiKey: async () => {},
  setProviderEnabled: async () => {},
});

function normalizeSettings(value: any): MdbListSettings {
  return {
    enabled: Boolean(value?.enabled),
    apiKey: typeof value?.apiKey === 'string' ? value.apiKey.trim() : '',
    useImdb: value?.useImdb !== false,
    useTmdb: value?.useTmdb !== false,
    useTomatoes: value?.useTomatoes !== false,
    useMetacritic: value?.useMetacritic !== false,
    useTrakt: value?.useTrakt !== false,
    useLetterboxd: value?.useLetterboxd !== false,
    useAudience: value?.useAudience !== false,
  };
}

function readMdbListConfig(preferences: any): MdbListSettings | null {
  const config = preferences?.integrations?.mdblist ?? preferences?.mdblist ?? null;
  if (!config || typeof config !== 'object') return null;
  const normalized = normalizeSettings(config);
  return {
    ...normalized,
    enabled: normalized.enabled && normalized.apiKey.length > 0,
  };
}

function updateProviderValue(settings: MdbListSettings, providerId: MdbListProviderId, enabled: boolean): MdbListSettings {
  switch (providerId) {
    case MDBLIST_PROVIDER_IMDB: return { ...settings, useImdb: enabled };
    case MDBLIST_PROVIDER_TMDB: return { ...settings, useTmdb: enabled };
    case MDBLIST_PROVIDER_TOMATOES: return { ...settings, useTomatoes: enabled };
    case MDBLIST_PROVIDER_METACRITIC: return { ...settings, useMetacritic: enabled };
    case MDBLIST_PROVIDER_TRAKT: return { ...settings, useTrakt: enabled };
    case MDBLIST_PROVIDER_LETTERBOXD: return { ...settings, useLetterboxd: enabled };
    case MDBLIST_PROVIDER_AUDIENCE: return { ...settings, useAudience: enabled };
    default: return settings;
  }
}

export function MdbListSettingsProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [settings, setSettings] = useState<MdbListSettings>(DEFAULT_MDBLIST_SETTINGS);
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
        const remoteConfig = readMdbListConfig(remotePreferences);
        if (remoteConfig) {
          setSettings(remoteConfig);
          await Storage.setItem(storageKey(uid), JSON.stringify(remoteConfig));
          return;
        }
      } else {
        accountPreferencesRef.current = null;
      }

      const raw = await Storage.getItem(storageKey(uid));
      if (cancelled) return;
      if (!raw) {
        setSettings(DEFAULT_MDBLIST_SETTINGS);
        return;
      }

      try {
        const parsed = normalizeSettings(JSON.parse(raw));
        setSettings({
          ...parsed,
          enabled: parsed.enabled && parsed.apiKey.length > 0,
        });
      } catch {
        setSettings(DEFAULT_MDBLIST_SETTINGS);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [user]);

  const persist = useCallback((nextSettings: MdbListSettings) => {
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
    const uid = user?.uid ?? null;
    const normalized: MdbListSettings = {
      ...nextSettings,
      apiKey: nextSettings.apiKey.trim(),
      enabled: nextSettings.enabled && nextSettings.apiKey.trim().length > 0,
    };

    clearMdbListRatingsCache();

    persistTimerRef.current = setTimeout(() => {
      void Storage.setItem(storageKey(uid), JSON.stringify(normalized)).catch(() => {});

      if (user) {
        const currentPreferences = accountPreferencesRef.current ?? {};
        const nextPreferences = {
          ...currentPreferences,
          integrations: {
            ...(currentPreferences?.integrations ?? {}),
            mdblist: normalized,
          },
          mdblist: normalized,
        };
        accountPreferencesRef.current = nextPreferences;
        void patchAccountPreferences(user, nextPreferences);
      }

      persistTimerRef.current = null;
    }, 500);
  }, [user]);

  const setEnabled = useCallback(async (enabled: boolean) => {
    setSettings(current => {
      const next = {
        ...current,
        enabled: enabled && current.apiKey.trim().length > 0,
      };
      persist(next);
      return next;
    });
  }, [persist]);

  const setApiKey = useCallback(async (apiKey: string) => {
    setSettings(current => {
      const normalizedApiKey = apiKey.trim();
      const next = {
        ...current,
        apiKey: normalizedApiKey,
        enabled: normalizedApiKey.length > 0 ? current.enabled : false,
      };
      persist(next);
      return next;
    });
  }, [persist]);

  const setProviderEnabled = useCallback(async (providerId: MdbListProviderId, enabled: boolean) => {
    setSettings(current => {
      const next = updateProviderValue(current, providerId, enabled);
      persist(next);
      return next;
    });
  }, [persist]);

  return (
    <MdbListSettingsContext.Provider
      value={{
        ...settings,
        hasApiKey: settings.apiKey.trim().length > 0,
        setEnabled,
        setApiKey,
        setProviderEnabled,
      }}
    >
      {children}
    </MdbListSettingsContext.Provider>
  );
}

export function useMdbListSettings() {
  return useContext(MdbListSettingsContext);
}