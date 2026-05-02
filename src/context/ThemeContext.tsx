import React, { createContext, useContext, useEffect, useState, useMemo, useRef } from 'react';
import { useColorScheme } from 'react-native';
import * as SystemUI from 'expo-system-ui';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { useProfile } from './ProfileContext';
import { profileScopedStorageKey } from '../utils/profileStorage';

export interface ThemeColors {
  bg: string;
  bgMid: string;        // bg at ~50% opacity for gradients
  bgHeader: string;     // bg at 70% opacity for sticky headers
  bgHeaderSolid: string;
  cardBg: string;
  cardBgElevated: string;
  border: string;
  borderSoft: string;
  accent: string;
  accentSoft: string;
  buttonText: string;   // text/icon color on accent-background buttons
  progressFill: string; // progress bar color inside play buttons
  toggleOn: string;     // active track color for AppleToggle
  textPrimary: string;
  textSecondary: string;
  textMuted: string;
  textInverse: string;
  mutedText: string;
  placeholder: string;
  subText: string;
  inputBg: string;
  inputBorder: string;
  overlay: string;
  overlayStrong: string;
}

export type AppearanceMode = 'dark' | 'light' | 'system';
export type ResolvedAppearanceMode = 'dark' | 'light';

export interface ThemeOption {
  id: string;
  name: string;
  description: string;
  swatch: string;
}

export interface Theme extends ThemeOption {
  appearance: AppearanceMode;
  resolvedAppearance: ResolvedAppearanceMode;
  colors: ThemeColors;
}

const THEME_OPTIONS: ThemeOption[] = [
  {
    id: 'monochrome',
    name: 'Monochrome',
    description: 'Pure black with crisp white accents',
    swatch: '#ffffff',
  },
  {
    id: 'ocean',
    name: 'Ocean',
    description: 'Deep black with soft blue accents',
    swatch: '#1877F2',
  },
  {
    id: 'emerald',
    name: 'Emerald',
    description: 'Dark charcoal with soft green accents',
    swatch: '#45a865',
  },
];

const DEFAULT_THEME_OPTION = THEME_OPTIONS[0];

const THEME_STORAGE_KEY = 'streamdek_theme';
const APPEARANCE_STORAGE_KEY = 'streamdek_appearance';
const HERO_SYNOPSIS_STORAGE_KEY = 'streamdek_show_hero_synopsis';

const DARK_SURFACES = {
  bg: '#000000',
  bgMid: 'rgba(0,0,0,0.68)',
  bgHeader: 'rgba(0,0,0,0.84)',
  bgHeaderSolid: '#000000',
  cardBg: '#0a0a0a',
  cardBgElevated: '#121212',
  border: 'rgba(255,255,255,0.13)',
  borderSoft: 'rgba(255,255,255,0.10)',
  textPrimary: '#f5f7fb',
  textSecondary: '#cdcdcd',
  textMuted: '#8a8a8a',
  textInverse: '#0d1118',
  mutedText: '#8a8a8a',
  placeholder: '#666666',
  subText: '#b3b3b3',
  inputBg: '#111111',
  inputBorder: 'rgba(255,255,255,0.12)',
  overlay: 'rgba(0,0,0,0.5)',
  overlayStrong: 'rgba(0,0,0,0.76)',
};

const LIGHT_SURFACES = {
  bg: '#f2f4f8',
  bgMid: 'rgba(242,244,248,0.72)',
  bgHeader: 'rgba(248,249,252,0.78)',
  bgHeaderSolid: '#f8f9fc',
  cardBg: '#ffffff',
  cardBgElevated: '#fbfcfe',
  border: 'rgba(16,24,40,0.10)',
  borderSoft: 'rgba(16,24,40,0.06)',
  textPrimary: '#101828',
  textSecondary: '#475467',
  textMuted: '#667085',
  textInverse: '#ffffff',
  mutedText: '#667085',
  placeholder: '#98a2b3',
  subText: '#526075',
  inputBg: '#eef2f7',
  inputBorder: 'rgba(16,24,40,0.09)',
  overlay: 'rgba(15,23,42,0.22)',
  overlayStrong: 'rgba(15,23,42,0.44)',
};

const LIGHT_THEME_ACCENTS: Record<string, { accentSoft: string; buttonText: string; progressFill: string; toggleOn: string }> = {
  ocean: { accentSoft: '#155bc7', buttonText: '#ffffff', progressFill: '#0f9d58', toggleOn: '#1877F2' },
  emerald: { accentSoft: '#2f8f53', buttonText: '#ffffff', progressFill: '#0f9d58', toggleOn: '#1877F2' },
  monochrome: { accentSoft: '#374151', buttonText: '#111111', progressFill: '#0f9d58', toggleOn: '#1877F2' },
};

const MONOCHROME_DARK_SURFACES = {
  ...DARK_SURFACES,
  inputBg: '#101010',
  inputBorder: 'rgba(255,255,255,0.10)',
};

const contrastTextColor = (hex: string) => {
  const normalized = hex.replace('#', '');
  if (normalized.length !== 6) return '#ffffff';

  const r = parseInt(normalized.slice(0, 2), 16);
  const g = parseInt(normalized.slice(2, 4), 16);
  const b = parseInt(normalized.slice(4, 6), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b);
  return luminance >= 180 ? '#111111' : '#ffffff';
};

function buildColors(themeId: string, appearance: AppearanceMode): ThemeColors {
  const option = THEME_OPTIONS.find(theme => theme.id === themeId) ?? DEFAULT_THEME_OPTION;
  const surfaces = appearance === 'light'
    ? LIGHT_SURFACES
    : (option.id === 'monochrome' ? MONOCHROME_DARK_SURFACES : DARK_SURFACES);
  
  const accent = option.id === 'monochrome'
    ? '#ffffff'
    : option.swatch;
  
  const accentSoft = appearance === 'light'
    ? (LIGHT_THEME_ACCENTS[option.id]?.accentSoft ?? accent)
    : (
      option.id === 'ocean' ? '#4A90E2'
      : option.id === 'emerald' ? '#7ec494'
      : option.id === 'monochrome' ? '#e0e0e0'
      : '#e0e0e0'
    );
    
  const buttonText = option.id === 'monochrome'
    ? '#111111'
    : (LIGHT_THEME_ACCENTS[option.id]?.buttonText ?? contrastTextColor(accent));

  const toggleOn = appearance === 'light'
    ? (LIGHT_THEME_ACCENTS[option.id]?.toggleOn ?? accent)
    : '#1877F2';

  return {
    ...surfaces,
    accent,
    accentSoft,
    buttonText,
    progressFill: appearance === 'light' ? '#0f9d58' : '#00e676',
    toggleOn,
  };
}

interface ThemeContextType {
  theme: Theme;
  appearance: AppearanceMode;
  resolvedAppearance: ResolvedAppearanceMode;
  setAppearance: (mode: AppearanceMode) => Promise<void>;
  setThemeId: (id: string) => Promise<void>;
  showHeroSynopsis: boolean;
  setShowHeroSynopsis: (value: boolean) => Promise<void>;
}

const ThemeContext = createContext<ThemeContextType>({
  theme: {
    id: DEFAULT_THEME_OPTION.id,
    name: DEFAULT_THEME_OPTION.name,
    description: DEFAULT_THEME_OPTION.description,
    swatch: DEFAULT_THEME_OPTION.swatch,
    appearance: 'dark',
    resolvedAppearance: 'dark',
    colors: buildColors(DEFAULT_THEME_OPTION.id, 'dark'),
  },
  appearance: 'dark',
  resolvedAppearance: 'dark',
  setAppearance: async () => {},
  setThemeId: async () => {},
  showHeroSynopsis: true,
  setShowHeroSynopsis: async () => {},
});

export const THEMES = THEME_OPTIONS;

export const ThemeProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { activeProfile, profileSwitching, profilesReady } = useProfile();
  const [themeId, setThemeIdState] = useState('monochrome');
  const rawColorScheme = useColorScheme();
  const systemScheme: ResolvedAppearanceMode = rawColorScheme === 'light' ? 'light' : 'dark';
  const [appearance, setAppearanceState] = useState<AppearanceMode>('dark');
  const [showHeroSynopsis, setShowHeroSynopsisState] = useState(true);
  const themeStorageKey = profileScopedStorageKey(THEME_STORAGE_KEY, user?.uid, activeProfile?.id);
  const appearanceStorageKey = profileScopedStorageKey(APPEARANCE_STORAGE_KEY, user?.uid, activeProfile?.id);
  const heroSynopsisStorageKey = profileScopedStorageKey(HERO_SYNOPSIS_STORAGE_KEY, user?.uid, activeProfile?.id);
  const persistTimersRef = useRef<Record<string, ReturnType<typeof setTimeout>>>({});

  useEffect(() => {
    if ((profileSwitching && !activeProfile) || (!activeProfile && !!user?.uid && profilesReady)) {
      return;
    }
    let cancelled = false;

    void Promise.all([
      Storage.getItem(themeStorageKey),
      Storage.getItem(appearanceStorageKey),
      Storage.getItem(heroSynopsisStorageKey),
      Storage.getItem(THEME_STORAGE_KEY),
      Storage.getItem(APPEARANCE_STORAGE_KEY),
      Storage.getItem(HERO_SYNOPSIS_STORAGE_KEY),
    ]).then(([storedTheme, storedAppearance, storedHeroSynopsis, legacyTheme, legacyAppearance, legacyHeroSynopsis]) => {
      if (cancelled) return;

      const nextTheme = storedTheme ?? legacyTheme;
      const nextAppearance = storedAppearance ?? legacyAppearance;
      const nextHeroSynopsis = storedHeroSynopsis ?? legacyHeroSynopsis;

      setThemeIdState(nextTheme && THEME_OPTIONS.find(t => t.id === nextTheme) ? nextTheme : DEFAULT_THEME_OPTION.id);
      setAppearanceState(
        nextAppearance === 'dark' || nextAppearance === 'light' || nextAppearance === 'system'
          ? nextAppearance
          : 'dark',
      );
      setShowHeroSynopsisState(nextHeroSynopsis === 'false' ? false : true);
    });

    return () => {
      cancelled = true;
    };
  }, [activeProfile, appearanceStorageKey, heroSynopsisStorageKey, profileSwitching, profilesReady, themeStorageKey, user?.uid]);

  const persistSoon = (key: string, value: string) => {
    if (persistTimersRef.current[key]) clearTimeout(persistTimersRef.current[key]);
    // UI preferences must repaint immediately; storage is fast-path/MMKV-backed but still runs off the tap path.
    persistTimersRef.current[key] = setTimeout(() => {
      void Storage.setItem(key, value).catch(() => {});
      delete persistTimersRef.current[key];
    }, 50);
  };

  const setThemeId = async (id: string) => {
    const nextId = THEME_OPTIONS.find(theme => theme.id === id)?.id ?? DEFAULT_THEME_OPTION.id;
    setThemeIdState(nextId);
    persistSoon(themeStorageKey, nextId);
  };

  const setAppearance = async (mode: AppearanceMode) => {
    setAppearanceState(mode);
    persistSoon(appearanceStorageKey, mode);
  };

  const setShowHeroSynopsis = async (value: boolean) => {
    setShowHeroSynopsisState(value);
    persistSoon(heroSynopsisStorageKey, value ? 'true' : 'false');
  };

  const resolvedAppearance: ResolvedAppearanceMode =
    appearance === 'system'
      ? systemScheme
      : appearance;

  const theme = useMemo(() => {
    const option = THEME_OPTIONS.find(t => t.id === themeId) ?? DEFAULT_THEME_OPTION;
    return {
      ...option,
      appearance,
      resolvedAppearance,
      colors: buildColors(option.id, resolvedAppearance),
    };
  }, [themeId, appearance, resolvedAppearance]);

  useEffect(() => {
    void SystemUI.setBackgroundColorAsync(theme.colors.bg);
  }, [theme.colors.bg]);

  return (
    <ThemeContext.Provider value={{ theme, appearance, resolvedAppearance, setAppearance, setThemeId, showHeroSynopsis, setShowHeroSynopsis }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => useContext(ThemeContext);
