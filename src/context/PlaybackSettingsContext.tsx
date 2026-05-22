import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { fetchAccountPreferences, patchAccountPreferences } from '../utils/accountPreferences';

const PLAYBACK_SETTINGS_KEY = 'playback_settings';

export type PlaybackDecoderMode = 'auto' | 'hardware' | 'hardware_plus' | 'software';
export type PlaybackRenderSurface = 'standard' | 'compatibility';
export type NextEpisodeThresholdMode = 'percent' | 'minutes';

type PlaybackSettingsValue = {
  decoderMode: PlaybackDecoderMode;
  setDecoderMode: (value: PlaybackDecoderMode) => Promise<void>;
  renderSurface: PlaybackRenderSurface;
  setRenderSurface: (value: PlaybackRenderSurface) => Promise<void>;
  preferEmbeddedMpvByDefault: boolean;
  setPreferEmbeddedMpvByDefault: (value: boolean) => Promise<void>;
  skipSegmentsEnabled: boolean;
  setSkipSegmentsEnabled: (value: boolean) => Promise<void>;
  introContributionEnabled: boolean;
  setIntroContributionEnabled: (value: boolean) => Promise<void>;
  introDbApiKey: string;
  setIntroDbApiKey: (value: string) => Promise<void>;
  autoPlayNextEpisodeEnabled: boolean;
  setAutoPlayNextEpisodeEnabled: (value: boolean) => Promise<void>;
  preferBingeGroupNextEpisode: boolean;
  setPreferBingeGroupNextEpisode: (value: boolean) => Promise<void>;
  nextEpisodeThresholdMode: NextEpisodeThresholdMode;
  setNextEpisodeThresholdMode: (value: NextEpisodeThresholdMode) => Promise<void>;
  nextEpisodeThresholdPercent: number;
  setNextEpisodeThresholdPercent: (value: number) => Promise<void>;
  nextEpisodeThresholdMinutes: number;
  setNextEpisodeThresholdMinutes: (value: number) => Promise<void>;
  refreshFromCloud: () => Promise<void>;
  isReady: boolean;
};

const PlaybackSettingsContext = createContext<PlaybackSettingsValue>({
  decoderMode: 'auto',
  setDecoderMode: async () => {},
  renderSurface: 'standard',
  setRenderSurface: async () => {},
  preferEmbeddedMpvByDefault: true,
  setPreferEmbeddedMpvByDefault: async () => {},
  skipSegmentsEnabled: true,
  setSkipSegmentsEnabled: async () => {},
  introContributionEnabled: false,
  setIntroContributionEnabled: async () => {},
  introDbApiKey: '',
  setIntroDbApiKey: async () => {},
  autoPlayNextEpisodeEnabled: false,
  setAutoPlayNextEpisodeEnabled: async () => {},
  preferBingeGroupNextEpisode: true,
  setPreferBingeGroupNextEpisode: async () => {},
  nextEpisodeThresholdMode: 'minutes',
  setNextEpisodeThresholdMode: async () => {},
  nextEpisodeThresholdPercent: 95,
  setNextEpisodeThresholdPercent: async () => {},
  nextEpisodeThresholdMinutes: 2,
  setNextEpisodeThresholdMinutes: async () => {},
  refreshFromCloud: async () => {},
  isReady: false,
});

export const PlaybackSettingsProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const [decoderMode, setDecoderModeState] = useState<PlaybackDecoderMode>('auto');
  const [renderSurface, setRenderSurfaceState] = useState<PlaybackRenderSurface>('standard');
  const [preferEmbeddedMpvByDefault, setPreferEmbeddedMpvByDefaultState] = useState(true);
  const [skipSegmentsEnabled, setSkipSegmentsEnabledState] = useState(true);
  const [introContributionEnabled, setIntroContributionEnabledState] = useState(false);
  const [introDbApiKey, setIntroDbApiKeyState] = useState('');
  const [autoPlayNextEpisodeEnabled, setAutoPlayNextEpisodeEnabledState] = useState(false);
  const [preferBingeGroupNextEpisode, setPreferBingeGroupNextEpisodeState] = useState(true);
  const [nextEpisodeThresholdMode, setNextEpisodeThresholdModeState] = useState<NextEpisodeThresholdMode>('minutes');
  const [nextEpisodeThresholdPercent, setNextEpisodeThresholdPercentState] = useState(95);
  const [nextEpisodeThresholdMinutes, setNextEpisodeThresholdMinutesState] = useState(2);
  const [isReady, setIsReady] = useState(false);
  const settingsRef = useRef({
    decoderMode: 'auto' as PlaybackDecoderMode,
    renderSurface: 'standard' as PlaybackRenderSurface,
    preferEmbeddedMpvByDefault: true,
    skipSegmentsEnabled: true,
    introContributionEnabled: false,
    introDbApiKey: '',
    autoPlayNextEpisodeEnabled: false,
    preferBingeGroupNextEpisode: true,
    nextEpisodeThresholdMode: 'minutes' as NextEpisodeThresholdMode,
    nextEpisodeThresholdPercent: 95,
    nextEpisodeThresholdMinutes: 2,
  });
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const hasLocalOverrideDuringHydrationRef = useRef(false);

  const applyRemotePlayback = useCallback(async (remotePreferences: any | null) => {
    if (hasLocalOverrideDuringHydrationRef.current && !isReady) return;

    const remotePlayback = remotePreferences?.playback;
    if (!remotePlayback) return;

    const nextDecoderMode = (
      remotePlayback.decoderMode === 'auto'
      || remotePlayback.decoderMode === 'hardware'
      || remotePlayback.decoderMode === 'hardware_plus'
      || remotePlayback.decoderMode === 'software'
    )
      ? remotePlayback.decoderMode
      : settingsRef.current.decoderMode;

    const nextRenderSurface = (
      remotePlayback.renderSurface === 'standard'
      || remotePlayback.renderSurface === 'compatibility'
    )
      ? remotePlayback.renderSurface
      : settingsRef.current.renderSurface;

    const nextPreferEmbeddedMpvByDefault = typeof remotePlayback.preferEmbeddedMpvByDefault === 'boolean'
      ? remotePlayback.preferEmbeddedMpvByDefault
      : settingsRef.current.preferEmbeddedMpvByDefault;
    const nextSkipSegmentsEnabled = typeof remotePlayback.skipSegmentsEnabled === 'boolean'
      ? remotePlayback.skipSegmentsEnabled
      : typeof remotePlayback.skipIntroEnabled === 'boolean'
        ? remotePlayback.skipIntroEnabled
        : settingsRef.current.skipSegmentsEnabled;
    const nextIntroContributionEnabled = typeof remotePlayback.introContributionEnabled === 'boolean'
      ? remotePlayback.introContributionEnabled
      : settingsRef.current.introContributionEnabled;
    const nextIntroDbApiKey = typeof remotePlayback.introDbApiKey === 'string'
      ? remotePlayback.introDbApiKey
      : settingsRef.current.introDbApiKey;
    const nextAutoPlayNextEpisodeEnabled = typeof remotePlayback.autoPlayNextEpisodeEnabled === 'boolean'
      ? remotePlayback.autoPlayNextEpisodeEnabled
      : settingsRef.current.autoPlayNextEpisodeEnabled;
    const nextPreferBingeGroupNextEpisode = typeof remotePlayback.preferBingeGroupNextEpisode === 'boolean'
      ? remotePlayback.preferBingeGroupNextEpisode
      : settingsRef.current.preferBingeGroupNextEpisode;
    const nextThresholdMode = remotePlayback.nextEpisodeThresholdMode === 'percent' || remotePlayback.nextEpisodeThresholdMode === 'minutes'
      ? remotePlayback.nextEpisodeThresholdMode
      : settingsRef.current.nextEpisodeThresholdMode;
    const nextThresholdPercent = typeof remotePlayback.nextEpisodeThresholdPercent === 'number' && Number.isFinite(remotePlayback.nextEpisodeThresholdPercent)
      ? Math.max(50, Math.min(99, Math.round(remotePlayback.nextEpisodeThresholdPercent)))
      : settingsRef.current.nextEpisodeThresholdPercent;
    const nextThresholdMinutes = typeof remotePlayback.nextEpisodeThresholdMinutes === 'number' && Number.isFinite(remotePlayback.nextEpisodeThresholdMinutes)
      ? Math.max(1, Math.min(15, Math.round(remotePlayback.nextEpisodeThresholdMinutes)))
      : settingsRef.current.nextEpisodeThresholdMinutes;

    settingsRef.current = {
      decoderMode: nextDecoderMode,
      renderSurface: nextRenderSurface,
      preferEmbeddedMpvByDefault: nextPreferEmbeddedMpvByDefault,
      skipSegmentsEnabled: nextSkipSegmentsEnabled,
      introContributionEnabled: nextIntroContributionEnabled,
      introDbApiKey: nextIntroDbApiKey,
      autoPlayNextEpisodeEnabled: nextAutoPlayNextEpisodeEnabled,
      preferBingeGroupNextEpisode: nextPreferBingeGroupNextEpisode,
      nextEpisodeThresholdMode: nextThresholdMode,
      nextEpisodeThresholdPercent: nextThresholdPercent,
      nextEpisodeThresholdMinutes: nextThresholdMinutes,
    };

    setDecoderModeState(nextDecoderMode);
    setRenderSurfaceState(nextRenderSurface);
    setPreferEmbeddedMpvByDefaultState(nextPreferEmbeddedMpvByDefault);
    setSkipSegmentsEnabledState(nextSkipSegmentsEnabled);
    setIntroContributionEnabledState(nextIntroContributionEnabled);
    setIntroDbApiKeyState(nextIntroDbApiKey);
    setAutoPlayNextEpisodeEnabledState(nextAutoPlayNextEpisodeEnabled);
    setPreferBingeGroupNextEpisodeState(nextPreferBingeGroupNextEpisode);
    setNextEpisodeThresholdModeState(nextThresholdMode);
    setNextEpisodeThresholdPercentState(nextThresholdPercent);
    setNextEpisodeThresholdMinutesState(nextThresholdMinutes);

    await Storage.setItem(PLAYBACK_SETTINGS_KEY, JSON.stringify({
      decoderMode: nextDecoderMode,
      renderSurface: nextRenderSurface,
      preferEmbeddedMpvByDefault: nextPreferEmbeddedMpvByDefault,
      skipSegmentsEnabled: nextSkipSegmentsEnabled,
      skipIntroEnabled: nextSkipSegmentsEnabled,
      introContributionEnabled: nextIntroContributionEnabled,
      introDbApiKey: nextIntroDbApiKey,
      autoPlayNextEpisodeEnabled: nextAutoPlayNextEpisodeEnabled,
      preferBingeGroupNextEpisode: nextPreferBingeGroupNextEpisode,
      nextEpisodeThresholdMode: nextThresholdMode,
      nextEpisodeThresholdPercent: nextThresholdPercent,
      nextEpisodeThresholdMinutes: nextThresholdMinutes,
    }));
  }, [isReady]);

  useEffect(() => {
    let cancelled = false;

    Promise.all([
      Storage.getItem(PLAYBACK_SETTINGS_KEY),
      fetchAccountPreferences(user),
    ])
      .then(([raw, remotePreferences]) => {
        if (cancelled) return;
        if (raw) {
          try {
            const parsed = JSON.parse(raw);
            if (parsed?.decoderMode === 'auto' || parsed?.decoderMode === 'hardware' || parsed?.decoderMode === 'hardware_plus' || parsed?.decoderMode === 'software') {
              setDecoderModeState(parsed.decoderMode);
              settingsRef.current.decoderMode = parsed.decoderMode;
            }
            if (parsed?.renderSurface === 'standard' || parsed?.renderSurface === 'compatibility') {
              setRenderSurfaceState(parsed.renderSurface);
              settingsRef.current.renderSurface = parsed.renderSurface;
            }
            if (typeof parsed?.preferEmbeddedMpvByDefault === 'boolean') {
              setPreferEmbeddedMpvByDefaultState(parsed.preferEmbeddedMpvByDefault);
              settingsRef.current.preferEmbeddedMpvByDefault = parsed.preferEmbeddedMpvByDefault;
            }
            const persistedSkipSegmentsEnabled = typeof parsed?.skipSegmentsEnabled === 'boolean'
              ? parsed.skipSegmentsEnabled
              : typeof parsed?.skipIntroEnabled === 'boolean'
                ? parsed.skipIntroEnabled
                : null;
            if (typeof persistedSkipSegmentsEnabled === 'boolean') {
              setSkipSegmentsEnabledState(persistedSkipSegmentsEnabled);
              settingsRef.current.skipSegmentsEnabled = persistedSkipSegmentsEnabled;
            }
            if (typeof parsed?.introContributionEnabled === 'boolean') {
              setIntroContributionEnabledState(parsed.introContributionEnabled);
              settingsRef.current.introContributionEnabled = parsed.introContributionEnabled;
            }
            if (typeof parsed?.introDbApiKey === 'string') {
              setIntroDbApiKeyState(parsed.introDbApiKey);
              settingsRef.current.introDbApiKey = parsed.introDbApiKey;
            }
            if (typeof parsed?.autoPlayNextEpisodeEnabled === 'boolean') {
              setAutoPlayNextEpisodeEnabledState(parsed.autoPlayNextEpisodeEnabled);
              settingsRef.current.autoPlayNextEpisodeEnabled = parsed.autoPlayNextEpisodeEnabled;
            }
            if (typeof parsed?.preferBingeGroupNextEpisode === 'boolean') {
              setPreferBingeGroupNextEpisodeState(parsed.preferBingeGroupNextEpisode);
              settingsRef.current.preferBingeGroupNextEpisode = parsed.preferBingeGroupNextEpisode;
            }
            if (parsed?.nextEpisodeThresholdMode === 'percent' || parsed?.nextEpisodeThresholdMode === 'minutes') {
              setNextEpisodeThresholdModeState(parsed.nextEpisodeThresholdMode);
              settingsRef.current.nextEpisodeThresholdMode = parsed.nextEpisodeThresholdMode;
            }
            if (typeof parsed?.nextEpisodeThresholdPercent === 'number' && Number.isFinite(parsed.nextEpisodeThresholdPercent)) {
              const nextValue = Math.max(50, Math.min(99, Math.round(parsed.nextEpisodeThresholdPercent)));
              setNextEpisodeThresholdPercentState(nextValue);
              settingsRef.current.nextEpisodeThresholdPercent = nextValue;
            }
            if (typeof parsed?.nextEpisodeThresholdMinutes === 'number' && Number.isFinite(parsed.nextEpisodeThresholdMinutes)) {
              const nextValue = Math.max(1, Math.min(15, Math.round(parsed.nextEpisodeThresholdMinutes)));
              setNextEpisodeThresholdMinutesState(nextValue);
              settingsRef.current.nextEpisodeThresholdMinutes = nextValue;
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
  }, [applyRemotePlayback, user]);

  const persist = useCallback((next: typeof settingsRef.current) => {
    settingsRef.current = next;
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
    // Playback preferences can be tapped rapidly in settings; batch local/cloud writes off the UI path.
    persistTimerRef.current = setTimeout(() => {
      const snapshot = settingsRef.current;
      void Storage.setItem(PLAYBACK_SETTINGS_KEY, JSON.stringify(snapshot)).catch(() => {});
      void patchAccountPreferences(user, {
        playback: {
          decoderMode: snapshot.decoderMode,
          renderSurface: snapshot.renderSurface,
          preferEmbeddedMpvByDefault: snapshot.preferEmbeddedMpvByDefault,
          skipSegmentsEnabled: snapshot.skipSegmentsEnabled,
          skipIntroEnabled: snapshot.skipSegmentsEnabled,
          introContributionEnabled: snapshot.introContributionEnabled,
          introDbApiKey: snapshot.introDbApiKey,
          autoPlayNextEpisodeEnabled: snapshot.autoPlayNextEpisodeEnabled,
          preferBingeGroupNextEpisode: snapshot.preferBingeGroupNextEpisode,
          nextEpisodeThresholdMode: snapshot.nextEpisodeThresholdMode,
          nextEpisodeThresholdPercent: snapshot.nextEpisodeThresholdPercent,
          nextEpisodeThresholdMinutes: snapshot.nextEpisodeThresholdMinutes,
        },
      });
      persistTimerRef.current = null;
    }, 100);
  }, [user]);

  const setDecoderMode = useCallback(async (value: PlaybackDecoderMode) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setDecoderModeState(value);
    persist({ ...settingsRef.current, decoderMode: value });
  }, [isReady, persist]);

  const setRenderSurface = useCallback(async (value: PlaybackRenderSurface) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setRenderSurfaceState(value);
    persist({ ...settingsRef.current, renderSurface: value });
  }, [isReady, persist]);

  const setPreferEmbeddedMpvByDefault = useCallback(async (value: boolean) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setPreferEmbeddedMpvByDefaultState(value);
    persist({ ...settingsRef.current, preferEmbeddedMpvByDefault: value });
  }, [isReady, persist]);

  const setSkipSegmentsEnabled = useCallback(async (value: boolean) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setSkipSegmentsEnabledState(value);
    persist({ ...settingsRef.current, skipSegmentsEnabled: value });
  }, [isReady, persist]);

  const setIntroContributionEnabled = useCallback(async (value: boolean) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setIntroContributionEnabledState(value);
    persist({ ...settingsRef.current, introContributionEnabled: value });
  }, [isReady, persist]);

  const setIntroDbApiKey = useCallback(async (value: string) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setIntroDbApiKeyState(value);
    persist({ ...settingsRef.current, introDbApiKey: value });
  }, [isReady, persist]);

  const setAutoPlayNextEpisodeEnabled = useCallback(async (value: boolean) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setAutoPlayNextEpisodeEnabledState(value);
    persist({ ...settingsRef.current, autoPlayNextEpisodeEnabled: value });
  }, [isReady, persist]);

  const setPreferBingeGroupNextEpisode = useCallback(async (value: boolean) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setPreferBingeGroupNextEpisodeState(value);
    persist({ ...settingsRef.current, preferBingeGroupNextEpisode: value });
  }, [isReady, persist]);

  const setNextEpisodeThresholdMode = useCallback(async (value: NextEpisodeThresholdMode) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    setNextEpisodeThresholdModeState(value);
    persist({ ...settingsRef.current, nextEpisodeThresholdMode: value });
  }, [isReady, persist]);

  const setNextEpisodeThresholdPercent = useCallback(async (value: number) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    const normalizedValue = Math.max(50, Math.min(99, Math.round(value)));
    setNextEpisodeThresholdPercentState(normalizedValue);
    persist({ ...settingsRef.current, nextEpisodeThresholdPercent: normalizedValue });
  }, [isReady, persist]);

  const setNextEpisodeThresholdMinutes = useCallback(async (value: number) => {
    if (!isReady) hasLocalOverrideDuringHydrationRef.current = true;
    const normalizedValue = Math.max(1, Math.min(15, Math.round(value)));
    setNextEpisodeThresholdMinutesState(normalizedValue);
    persist({ ...settingsRef.current, nextEpisodeThresholdMinutes: normalizedValue });
  }, [isReady, persist]);

  const refreshFromCloud = useCallback(async () => {
    const remotePreferences = await fetchAccountPreferences(user);
    await applyRemotePlayback(remotePreferences);
  }, [applyRemotePlayback, user]);

  return (
    <PlaybackSettingsContext.Provider value={{
      decoderMode,
      setDecoderMode,
      renderSurface,
      setRenderSurface,
      preferEmbeddedMpvByDefault,
      setPreferEmbeddedMpvByDefault,
      skipSegmentsEnabled,
      setSkipSegmentsEnabled,
      introContributionEnabled,
      setIntroContributionEnabled,
      introDbApiKey,
      setIntroDbApiKey,
      autoPlayNextEpisodeEnabled,
      setAutoPlayNextEpisodeEnabled,
      preferBingeGroupNextEpisode,
      setPreferBingeGroupNextEpisode,
      nextEpisodeThresholdMode,
      setNextEpisodeThresholdMode,
      nextEpisodeThresholdPercent,
      setNextEpisodeThresholdPercent,
      nextEpisodeThresholdMinutes,
      setNextEpisodeThresholdMinutes,
      refreshFromCloud,
      isReady,
    }}>
      {children}
    </PlaybackSettingsContext.Provider>
  );
};

export const usePlaybackSettings = () => useContext(PlaybackSettingsContext);
