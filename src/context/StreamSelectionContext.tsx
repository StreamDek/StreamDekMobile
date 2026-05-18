import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { fetchAccountPreferences, patchAccountPreferences } from '../utils/accountPreferences';

const STREAM_SELECTION_SETTINGS_KEY = 'stream_selection_settings';

type StreamSelectionSettingsValue = {
  enabled: boolean;
  setEnabled: (value: boolean) => Promise<void>;
  shortSourceFilterEnabled: boolean;
  effectiveShortSourceFilterEnabled: boolean;
  setShortSourceFilterEnabled: (value: boolean) => Promise<void>;
  preferredQuality: PreferredQuality;
  effectivePreferredQuality?: PreferredQuality;
  setPreferredQuality: (value: PreferredQuality) => Promise<void>;
  maxFileSizeGB: number;
  effectiveMaxFileSizeGB: number;
  setMaxFileSizeGB: (value: number) => Promise<void>;
  refreshFromCloud: () => Promise<void>;
  isReady: boolean;
};

export type PreferredQuality = 'best' | '4k' | '1080p' | '720p';

const StreamSelectionContext = createContext<StreamSelectionSettingsValue>({
  enabled: false,
  setEnabled: async () => {},
  shortSourceFilterEnabled: false,
  effectiveShortSourceFilterEnabled: false,
  setShortSourceFilterEnabled: async () => {},
  preferredQuality: '1080p',
  effectivePreferredQuality: undefined,
  setPreferredQuality: async () => {},
  maxFileSizeGB: 0,
  effectiveMaxFileSizeGB: 0,
  setMaxFileSizeGB: async () => {},
  refreshFromCloud: async () => {},
  isReady: false,
});

export const StreamSelectionProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const [enabled, setEnabledState] = useState(false);
  const [shortSourceFilterEnabled, setShortSourceFilterEnabledState] = useState(false);
  const [preferredQuality, setPreferredQualityState] = useState<PreferredQuality>('1080p');
  const [maxFileSizeGB, setMaxFileSizeGBState] = useState(0);
  const [isReady, setIsReady] = useState(false);
  const settingsRef = useRef({
    enabled: false,
    shortSourceFilterEnabled: false,
    preferredQuality: '1080p' as PreferredQuality,
    maxFileSizeGB: 0,
  });
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const hasLocalOverrideDuringHydrationRef = useRef(false);

  const normalizePreferredQuality = useCallback((value: unknown): PreferredQuality | null => {
    if (value === '4K') return '4k';
    return isPreferredQuality(value) ? value : null;
  }, []);

  const applyRemotePlayback = useCallback(async (remotePreferences: any | null) => {
    if (hasLocalOverrideDuringHydrationRef.current && !isReady) return;

    const remotePlayback = remotePreferences?.playback;
    if (!remotePlayback) return;

    const nextEnabled = typeof remotePlayback.streamSelectionEnabled === 'boolean'
      ? remotePlayback.streamSelectionEnabled
      : settingsRef.current.enabled;
    const nextShortSourceFilterEnabled = typeof remotePlayback.shortSourceFilterEnabled === 'boolean'
      ? remotePlayback.shortSourceFilterEnabled
      : settingsRef.current.shortSourceFilterEnabled;
    const nextPreferredQuality = normalizePreferredQuality(remotePlayback.preferredQuality)
      ?? settingsRef.current.preferredQuality;
    const nextMaxFileSizeGB = typeof remotePlayback.maxFileSizeGB === 'number'
      ? remotePlayback.maxFileSizeGB
      : settingsRef.current.maxFileSizeGB;

    settingsRef.current = {
      enabled: nextEnabled,
      shortSourceFilterEnabled: nextShortSourceFilterEnabled,
      preferredQuality: nextPreferredQuality,
      maxFileSizeGB: nextMaxFileSizeGB,
    };

    setEnabledState(nextEnabled);
    setShortSourceFilterEnabledState(nextShortSourceFilterEnabled);
    setPreferredQualityState(nextPreferredQuality);
    setMaxFileSizeGBState(nextMaxFileSizeGB);

    await Storage.setItem(STREAM_SELECTION_SETTINGS_KEY, JSON.stringify({
      enabled: nextEnabled,
      shortSourceFilterEnabled: nextShortSourceFilterEnabled,
      preferredQuality: nextPreferredQuality,
      maxFileSizeGB: nextMaxFileSizeGB,
    }));
  }, [isReady]);

  useEffect(() => {
    let cancelled = false;

    Promise.all([
      Storage.getItem(STREAM_SELECTION_SETTINGS_KEY),
      fetchAccountPreferences(user),
    ])
      .then(([raw, remotePreferences]) => {
        if (cancelled) return;
        if (raw) {
          try {
            const parsed = JSON.parse(raw);
            if (typeof parsed?.enabled === 'boolean') {
              setEnabledState(parsed.enabled);
              settingsRef.current.enabled = parsed.enabled;
            }
            if (typeof parsed?.shortSourceFilterEnabled === 'boolean') {
              setShortSourceFilterEnabledState(parsed.shortSourceFilterEnabled);
              settingsRef.current.shortSourceFilterEnabled = parsed.shortSourceFilterEnabled;
            }
            const normalizedPreferredQuality = normalizePreferredQuality(parsed?.preferredQuality);
            if (normalizedPreferredQuality) {
              setPreferredQualityState(normalizedPreferredQuality);
              settingsRef.current.preferredQuality = normalizedPreferredQuality;
            }
            if (typeof parsed?.maxFileSizeGB === 'number') {
              setMaxFileSizeGBState(parsed.maxFileSizeGB);
              settingsRef.current.maxFileSizeGB = parsed.maxFileSizeGB;
            }
          } catch {
            // Ignore malformed persisted settings.
          }
        }

        void applyRemotePlayback(remotePreferences);
      })
      .finally(() => {
        if (!cancelled) setIsReady(true);
      });

    return () => {
      cancelled = true;
    };
  }, [applyRemotePlayback, normalizePreferredQuality, user]);

  const persist = useCallback((next: typeof settingsRef.current) => {
    settingsRef.current = next;
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
    // Keep stream preference toggles instant and coalesce local/cloud persistence.
    persistTimerRef.current = setTimeout(() => {
      const snapshot = settingsRef.current;
      void Storage.setItem(STREAM_SELECTION_SETTINGS_KEY, JSON.stringify(snapshot)).catch(() => {});
      void patchAccountPreferences(user, {
        playback: {
          streamSelectionEnabled: snapshot.enabled,
          shortSourceFilterEnabled: snapshot.shortSourceFilterEnabled,
          preferredQuality: snapshot.preferredQuality,
          maxFileSizeGB: snapshot.maxFileSizeGB,
        },
      });
      persistTimerRef.current = null;
    }, 100);
  }, [user]);

  const setEnabled = useCallback(async (value: boolean) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setEnabledState(value);
    persist({ ...settingsRef.current, enabled: value });
  }, [isReady, persist]);

  const setShortSourceFilterEnabled = useCallback(async (value: boolean) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setShortSourceFilterEnabledState(value);
    persist({ ...settingsRef.current, shortSourceFilterEnabled: value });
  }, [isReady, persist]);

  const setPreferredQuality = useCallback(async (value: PreferredQuality) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setPreferredQualityState(value);
    persist({ ...settingsRef.current, preferredQuality: value });
  }, [isReady, persist]);

  const setMaxFileSizeGB = useCallback(async (value: number) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setMaxFileSizeGBState(value);
    persist({ ...settingsRef.current, maxFileSizeGB: value });
  }, [isReady, persist]);

  const refreshFromCloud = useCallback(async () => {
    const remotePreferences = await fetchAccountPreferences(user);
    await applyRemotePlayback(remotePreferences);
  }, [applyRemotePlayback, user]);

  const effectiveShortSourceFilterEnabled = false;
  const effectivePreferredQuality = preferredQuality;
  const effectiveMaxFileSizeGB = maxFileSizeGB;

  return (
    <StreamSelectionContext.Provider value={{
      enabled,
      setEnabled,
      shortSourceFilterEnabled,
      effectiveShortSourceFilterEnabled,
      setShortSourceFilterEnabled,
      preferredQuality,
      effectivePreferredQuality,
      setPreferredQuality,
      maxFileSizeGB,
      effectiveMaxFileSizeGB,
      setMaxFileSizeGB,
      refreshFromCloud,
      isReady,
    }}>
      {children}
    </StreamSelectionContext.Provider>
  );
};

export const useStreamSelectionSettings = () => useContext(StreamSelectionContext);

function isPreferredQuality(value: unknown): value is PreferredQuality {
  return value === 'best' || value === '4k' || value === '1080p' || value === '720p';
}
