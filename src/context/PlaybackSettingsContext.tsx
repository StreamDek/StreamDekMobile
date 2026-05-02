import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { fetchAccountPreferences, patchAccountPreferences } from '../utils/accountPreferences';

const PLAYBACK_SETTINGS_KEY = 'playback_settings';

export type PlaybackDecoderMode = 'auto' | 'hardware' | 'hardware_plus' | 'software';
export type PlaybackRenderSurface = 'standard' | 'compatibility';

type PlaybackSettingsValue = {
  decoderMode: PlaybackDecoderMode;
  setDecoderMode: (value: PlaybackDecoderMode) => Promise<void>;
  renderSurface: PlaybackRenderSurface;
  setRenderSurface: (value: PlaybackRenderSurface) => Promise<void>;
  preferEmbeddedMpvByDefault: boolean;
  setPreferEmbeddedMpvByDefault: (value: boolean) => Promise<void>;
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
  refreshFromCloud: async () => {},
  isReady: false,
});

export const PlaybackSettingsProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const [decoderMode, setDecoderModeState] = useState<PlaybackDecoderMode>('auto');
  const [renderSurface, setRenderSurfaceState] = useState<PlaybackRenderSurface>('standard');
  const [preferEmbeddedMpvByDefault, setPreferEmbeddedMpvByDefaultState] = useState(true);
  const [isReady, setIsReady] = useState(false);
  const settingsRef = useRef({
    decoderMode: 'auto' as PlaybackDecoderMode,
    renderSurface: 'standard' as PlaybackRenderSurface,
    preferEmbeddedMpvByDefault: true,
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

    settingsRef.current = {
      decoderMode: nextDecoderMode,
      renderSurface: nextRenderSurface,
      preferEmbeddedMpvByDefault: nextPreferEmbeddedMpvByDefault,
    };

    setDecoderModeState(nextDecoderMode);
    setRenderSurfaceState(nextRenderSurface);
    setPreferEmbeddedMpvByDefaultState(nextPreferEmbeddedMpvByDefault);

    await Storage.setItem(PLAYBACK_SETTINGS_KEY, JSON.stringify({
      decoderMode: nextDecoderMode,
      renderSurface: nextRenderSurface,
      preferEmbeddedMpvByDefault: nextPreferEmbeddedMpvByDefault,
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
      refreshFromCloud,
      isReady,
    }}>
      {children}
    </PlaybackSettingsContext.Provider>
  );
};

export const usePlaybackSettings = () => useContext(PlaybackSettingsContext);
