import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { useProfile } from './ProfileContext';
import { profileScopedStorageKey } from '../utils/profileStorage';

const DISPLAY_SETTINGS_KEY = 'streamdek_display_settings';

export type ContinueWatchingStyle = 'cinematic' | 'glass' | 'ticket' | 'mini' | 'stacked';

type DisplaySettingsValue = {
  pictureInPictureEnabled: boolean;
  setPictureInPictureEnabled: (value: boolean) => Promise<void>;
  showNavLabels: boolean;
  setShowNavLabels: (value: boolean) => Promise<void>;
  continueWatchingStyle: ContinueWatchingStyle;
  setContinueWatchingStyle: (style: ContinueWatchingStyle) => Promise<void>;
  showStreamsList: boolean;
  setShowStreamsList: (value: boolean) => Promise<void>;
  vividAmbientEnabled: boolean;
  setVividAmbientEnabled: (value: boolean) => Promise<void>;
  isReady: boolean;
};

const DisplaySettingsContext = createContext<DisplaySettingsValue>({
  pictureInPictureEnabled: true,
  setPictureInPictureEnabled: async () => {},
  showNavLabels: true,
  setShowNavLabels: async () => {},
  continueWatchingStyle: 'glass',
  setContinueWatchingStyle: async () => {},
  showStreamsList: true,
  setShowStreamsList: async () => {},
  vividAmbientEnabled: true,
  setVividAmbientEnabled: async () => {},
  isReady: false,
});

export const DisplaySettingsProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const [pictureInPictureEnabled, setPictureInPictureEnabledState] = useState(true);
  const [showNavLabels, setShowNavLabelsState] = useState(true);
  const [continueWatchingStyle, setContinueWatchingStyleState] = useState<ContinueWatchingStyle>('glass');
  const [showStreamsList, setShowStreamsListState] = useState(true);
  const [vividAmbientEnabled, setVividAmbientEnabledState] = useState(true);
  const [isReady, setIsReady] = useState(false);
  const settingsKey = profileScopedStorageKey(DISPLAY_SETTINGS_KEY, user?.uid, activeProfile?.id);
  const settingsRef = useRef({
    pictureInPictureEnabled: true,
    showNavLabels: true,
    continueWatchingStyle: 'glass' as ContinueWatchingStyle,
    showStreamsList: true,
    vividAmbientEnabled: true,
  });
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;
    setIsReady(false);
    void Promise.all([
      Storage.getItem(settingsKey),
      Storage.getItem(DISPLAY_SETTINGS_KEY),
    ]).then(([raw, legacyRaw]) => {
      const resolvedRaw = raw ?? legacyRaw;
      if (!resolvedRaw || cancelled) return;
      try {
        const parsed = JSON.parse(resolvedRaw);
        const next = { ...settingsRef.current };
        if (typeof parsed?.pictureInPictureEnabled === 'boolean') {
          next.pictureInPictureEnabled = parsed.pictureInPictureEnabled;
          setPictureInPictureEnabledState(parsed.pictureInPictureEnabled);
        }
        if (typeof parsed?.showNavLabels === 'boolean') {
          next.showNavLabels = parsed.showNavLabels;
          setShowNavLabelsState(parsed.showNavLabels);
        }
        if (['cinematic', 'glass', 'ticket', 'mini', 'stacked'].includes(parsed?.continueWatchingStyle)) {
          next.continueWatchingStyle = parsed.continueWatchingStyle;
          setContinueWatchingStyleState(parsed.continueWatchingStyle);
        }
        if (typeof parsed?.showStreamsList === 'boolean') {
          next.showStreamsList = parsed.showStreamsList;
          setShowStreamsListState(parsed.showStreamsList);
        }
        if (typeof parsed?.vividAmbientEnabled === 'boolean') {
          next.vividAmbientEnabled = parsed.vividAmbientEnabled;
          setVividAmbientEnabledState(parsed.vividAmbientEnabled);
        }
        settingsRef.current = next;
      } catch { /* ignore */ }
    }).finally(() => {
      if (!cancelled) setIsReady(true);
    });
    return () => { cancelled = true; };
  }, [settingsKey]);

  const persist = useCallback((next: typeof settingsRef.current) => {
    settingsRef.current = next;
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
    // Batch rapid appearance/display toggles and keep the visual switch off the storage path.
    persistTimerRef.current = setTimeout(() => {
      void Storage.setItem(settingsKey, JSON.stringify(settingsRef.current)).catch(() => {});
      persistTimerRef.current = null;
    }, 75);
  }, [settingsKey]);

  const setPictureInPictureEnabled = useCallback(async (v: boolean) => {
    setPictureInPictureEnabledState(v);
    persist({ ...settingsRef.current, pictureInPictureEnabled: v });
  }, [persist]);

  const setShowNavLabels = useCallback(async (v: boolean) => {
    setShowNavLabelsState(v);
    persist({ ...settingsRef.current, showNavLabels: v });
  }, [persist]);

  const setContinueWatchingStyle = useCallback(async (v: ContinueWatchingStyle) => {
    setContinueWatchingStyleState(v);
    persist({ ...settingsRef.current, continueWatchingStyle: v });
  }, [persist]);

  const setShowStreamsList = useCallback(async (v: boolean) => {
    setShowStreamsListState(v);
    persist({ ...settingsRef.current, showStreamsList: v });
  }, [persist]);

  const setVividAmbientEnabled = useCallback(async (v: boolean) => {
    setVividAmbientEnabledState(v);
    persist({ ...settingsRef.current, vividAmbientEnabled: v });
  }, [persist]);

  const value = useMemo(() => ({
    pictureInPictureEnabled, setPictureInPictureEnabled,
    showNavLabels, setShowNavLabels,
    continueWatchingStyle, setContinueWatchingStyle,
    showStreamsList, setShowStreamsList,
    vividAmbientEnabled, setVividAmbientEnabled,
    isReady,
  }), [pictureInPictureEnabled, showNavLabels, continueWatchingStyle, showStreamsList, vividAmbientEnabled, isReady,
       setPictureInPictureEnabled, setShowNavLabels, setContinueWatchingStyle, setShowStreamsList, setVividAmbientEnabled]);

  return (
    <DisplaySettingsContext.Provider value={value}>
      {children}
    </DisplaySettingsContext.Provider>
  );
};

export const useDisplaySettings = () => useContext(DisplaySettingsContext);
