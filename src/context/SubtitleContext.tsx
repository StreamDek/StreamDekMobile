// ─── Subtitle Context ─────────────────────────────────────────────────────────
//
// Central state + settings management for the subtitle system.
//
// Responsibilities:
//   • Persist user subtitle preferences (language order, auto-load, HI, forced,
//     custom addon URL) in Storage, following the same pattern as
//     PlaybackSettingsContext.
//   • Orchestrate a non-blocking subtitle search when the player requests it.
//   • Expose the ranked results list, download helper, and per-session delay.
//   • Provide the `useSubtitles` hook for consuming components.
//
// Data flow:
//   MpvPlayerScreen calls search() when resolvedStreamUrl is set.
//   search() runs async in the background; results arrive via state update.
//   If autoLoadEnabled, MpvPlayerScreen watches searchState and calls
//   downloadSubtitle() on the top result, then passes the local path to
//   playerRef.current.addSubtitleFile(path).

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import { Storage } from '../utils/storage';
import {
  SubtitleResult,
  SubtitleSearchParams,
  SubtitleLanguageCode,
} from '../services/subtitles/SubtitleProvider';
import {
  searchAndRankSubtitles,
  pickBestSubtitle,
  SubtitleRankingOptions,
} from '../services/subtitles/SubtitleSearchService';
import { SubtitleCacheService } from '../services/subtitles/SubtitleCacheService';
import { DEFAULT_OS_ADDON_URL } from '../services/subtitles/OpenSubtitlesStremioProvider';

// ── Storage key ───────────────────────────────────────────────────────────────

const SUBTITLE_SETTINGS_KEY = 'subtitle_settings';

// ── Types ─────────────────────────────────────────────────────────────────────

export type SubtitleSearchState = 'idle' | 'loading' | 'done' | 'error';

export interface SubtitleSettings {
  /** Whether to automatically load the best matching subtitle on playback start */
  autoLoadEnabled: boolean;
  /**
   * User's language priority list as ISO 639-2/B codes.
   * First entry = most preferred. Default: ['eng'].
   */
  languageOrder: SubtitleLanguageCode[];
  /** Prefer Hearing Impaired subtitles */
  preferHI: boolean;
  /** Prefer forced (foreign-only) subtitles */
  preferForced: boolean;
  /**
   * Stremio OpenSubtitles addon base URL.
   * Users can change this to a personalised/user-logged-in addon URL for
   * better results (e.g. with their OS account language preferences pre-baked).
   */
  addonUrl: string;
}

interface SubtitleContextValue extends SubtitleSettings {
  // ── Search state ──────────────────────────────────────────────────────────
  searchState: SubtitleSearchState;
  /** Ranked results from the last completed search — [] while idle/loading */
  results: SubtitleResult[];
  /** True while settings are being loaded from Storage on startup */
  isReady: boolean;

  // ── Per-session subtitle state ────────────────────────────────────────────
  /** ID of the currently active external (OpenSubtitles) subtitle, or null */
  activeExternalSubId: string | null;
  /** ID of the subtitle currently being downloaded, or null */
  downloadingSubId: string | null;
  /** Current subtitle delay in seconds (positive = later, negative = earlier) */
  delay: number;

  // ── Actions ───────────────────────────────────────────────────────────────
  /**
   * Kick off a non-blocking subtitle search for the given media.
   * Results arrive via the `results` + `searchState` state updates.
   * Safe to call multiple times — deduplicated by video ID within a session.
   */
  search: (params: SubtitleSearchParams, streamReleaseName?: string | null) => void;
  /**
   * Download a subtitle result and return its local file URI for MPV.
   * Returns null on failure. Sets `downloadingSubId` during the download.
   */
  downloadSubtitle: (result: SubtitleResult) => Promise<string | null>;
  /**
   * Pick and return the best subtitle from the current results list for
   * auto-load, respecting the user's language/HI/forced preferences.
   */
  getBestResult: () => SubtitleResult | null;
  /** Mark an external subtitle as the active one (after MPV loads it) */
  setActiveExternalSubId: (id: string | null) => void;
  /** Set the subtitle delay (in seconds). Caller is responsible for telling MPV. */
  setDelay: (seconds: number) => void;
  /**
   * Reset only the per-playback session state (active sub, delay) without
   * clearing search results or the dedup cache. Use this when the stream URL
   * changes but the content is the same (e.g. the user switches to a different
   * source for the same movie). Search results stay valid and are displayed
   * immediately without a new network request.
   */
  resetSession: () => void;
  /**
   * Full reset — clears results, search state, session state, and the dedup
   * cache so the next search() call will hit the network. Use this when the
   * player is destroyed or navigating to completely different content.
   */
  clearSearch: () => void;

  // ── Settings setters (async — persist to Storage) ─────────────────────────
  setAutoLoadEnabled: (value: boolean) => Promise<void>;
  setLanguageOrder: (value: SubtitleLanguageCode[]) => Promise<void>;
  setPreferHI: (value: boolean) => Promise<void>;
  setPreferForced: (value: boolean) => Promise<void>;
  setAddonUrl: (value: string) => Promise<void>;

  // ── Utilities ─────────────────────────────────────────────────────────────
  /** Delete all cached subtitle files from disk */
  clearFileCache: () => Promise<void>;
  /** Approximate size of the on-disk subtitle file cache in bytes */
  getFileCacheSize: () => Promise<number>;
}

// ── Defaults ──────────────────────────────────────────────────────────────────

const DEFAULT_SETTINGS: SubtitleSettings = {
  autoLoadEnabled: true,
  languageOrder: ['eng'],
  preferHI: false,
  preferForced: false,
  addonUrl: DEFAULT_OS_ADDON_URL,
};

// ── Context ───────────────────────────────────────────────────────────────────

const SubtitleContext = createContext<SubtitleContextValue>({
  ...DEFAULT_SETTINGS,
  searchState: 'idle',
  results: [],
  isReady: false,
  activeExternalSubId: null,
  downloadingSubId: null,
  delay: 0,
  search: () => {},
  downloadSubtitle: async () => null,
  getBestResult: () => null,
  setActiveExternalSubId: () => {},
  setDelay: () => {},
  resetSession: () => {},
  clearSearch: () => {},
  setAutoLoadEnabled: async () => {},
  setLanguageOrder: async () => {},
  setPreferHI: async () => {},
  setPreferForced: async () => {},
  setAddonUrl: async () => {},
  clearFileCache: async () => {},
  getFileCacheSize: async () => 0,
});

// ── Provider ──────────────────────────────────────────────────────────────────

export const SubtitleProvider = ({ children }: { children: React.ReactNode }) => {
  // ── Settings state ──────────────────────────────────────────────────────
  const [autoLoadEnabled, setAutoLoadEnabledState] = useState(DEFAULT_SETTINGS.autoLoadEnabled);
  const [languageOrder, setLanguageOrderState] = useState<SubtitleLanguageCode[]>(DEFAULT_SETTINGS.languageOrder);
  const [preferHI, setPreferHIState] = useState(DEFAULT_SETTINGS.preferHI);
  const [preferForced, setPreferForcedState] = useState(DEFAULT_SETTINGS.preferForced);
  const [addonUrl, setAddonUrlState] = useState(DEFAULT_SETTINGS.addonUrl);
  const [isReady, setIsReady] = useState(false);

  // ── Search state ────────────────────────────────────────────────────────
  const [searchState, setSearchState] = useState<SubtitleSearchState>('idle');
  const [results, setResults] = useState<SubtitleResult[]>([]);

  // ── Per-session state ───────────────────────────────────────────────────
  const [activeExternalSubId, setActiveExternalSubId] = useState<string | null>(null);
  const [downloadingSubId, setDownloadingSubId] = useState<string | null>(null);
  const [delay, setDelayState] = useState(0);

  // Deduplication: track which video IDs we've already searched this session
  const searchedVideoIdsRef = useRef<Set<string>>(new Set());
  // Keep current settings in a ref for use inside async callbacks
  const settingsRef = useRef<SubtitleSettings>({
    autoLoadEnabled,
    languageOrder,
    preferHI,
    preferForced,
    addonUrl,
  });
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    settingsRef.current = { autoLoadEnabled, languageOrder, preferHI, preferForced, addonUrl };
  }, [addonUrl, autoLoadEnabled, languageOrder, preferForced, preferHI]);

  // ── Load settings from Storage on mount ────────────────────────────────
  useEffect(() => {
    let cancelled = false;
    Storage.getItem(SUBTITLE_SETTINGS_KEY).then(raw => {
      if (cancelled) return;
      if (raw) {
        try {
          const parsed: Partial<SubtitleSettings> = JSON.parse(raw);
          if (typeof parsed.autoLoadEnabled === 'boolean') setAutoLoadEnabledState(parsed.autoLoadEnabled);
          if (Array.isArray(parsed.languageOrder) && parsed.languageOrder.length > 0) {
            setLanguageOrderState(parsed.languageOrder);
          }
          if (typeof parsed.preferHI === 'boolean') setPreferHIState(parsed.preferHI);
          if (typeof parsed.preferForced === 'boolean') setPreferForcedState(parsed.preferForced);
          if (typeof parsed.addonUrl === 'string' && parsed.addonUrl.length > 0) {
            setAddonUrlState(parsed.addonUrl);
          }
        } catch {
          // Ignore malformed stored settings
        }
      }
      setIsReady(true);
    }).catch(() => setIsReady(true));

    return () => { cancelled = true; };
  }, []);

  // ── Persist helper ──────────────────────────────────────────────────────
  const persist = useCallback((next: SubtitleSettings) => {
    settingsRef.current = next;
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
    // Subtitle options affect ranking/UI immediately; disk persistence is batched in the background.
    persistTimerRef.current = setTimeout(() => {
      void Storage.setItem(SUBTITLE_SETTINGS_KEY, JSON.stringify(settingsRef.current)).catch(() => {});
      persistTimerRef.current = null;
    }, 75);
  }, []);

  // ── Settings setters ────────────────────────────────────────────────────

  const setAutoLoadEnabled = useCallback(async (value: boolean) => {
    setAutoLoadEnabledState(value);
    persist({ ...settingsRef.current, autoLoadEnabled: value });
  }, [persist]);

  const setLanguageOrder = useCallback(async (value: SubtitleLanguageCode[]) => {
    setLanguageOrderState(value);
    persist({ ...settingsRef.current, languageOrder: value });
  }, [persist]);

  const setPreferHI = useCallback(async (value: boolean) => {
    setPreferHIState(value);
    persist({ ...settingsRef.current, preferHI: value });
  }, [persist]);

  const setPreferForced = useCallback(async (value: boolean) => {
    setPreferForcedState(value);
    persist({ ...settingsRef.current, preferForced: value });
  }, [persist]);

  const setAddonUrl = useCallback(async (value: string) => {
    const trimmed = value.trim() || DEFAULT_OS_ADDON_URL;
    setAddonUrlState(trimmed);
    // Clear cached results so the new URL is used on next search
    searchedVideoIdsRef.current.clear();
    persist({ ...settingsRef.current, addonUrl: trimmed });
  }, [persist]);

  // ── Search ──────────────────────────────────────────────────────────────

  const search = useCallback(
    (params: SubtitleSearchParams, streamReleaseName?: string | null) => {
      // Build dedup key
      const videoId =
        params.imdbId
          ? params.type === 'series' && params.season && params.episode
            ? `${params.imdbId}:${params.season}:${params.episode}`
            : params.imdbId
          : null;

      if (!videoId) {
        console.log('[SubtitleContext] search() skipped — no IMDB ID');
        return;
      }

      // Deduplicate within a session to avoid re-fetching on re-renders
      if (searchedVideoIdsRef.current.has(videoId)) return;
      searchedVideoIdsRef.current.add(videoId);

      const { languageOrder: lo, preferHI: hi, preferForced: forced, addonUrl: url } = settingsRef.current;
      const rankingOptions: SubtitleRankingOptions = {
        languageOrder: lo,
        preferHI: hi,
        preferForced: forced,
        streamReleaseName: streamReleaseName ?? null,
      };

      setSearchState('loading');
      setResults([]);

      // Run the search async — does not block the caller
      searchAndRankSubtitles(params, rankingOptions, url)
        .then(({ results: ranked }) => {
          setResults(ranked);
          setSearchState('done');
        })
        .catch(err => {
          console.warn('[SubtitleContext] search error:', err?.message);
          setSearchState('error');
        });
    },
    [],
  );

  // ── Download ────────────────────────────────────────────────────────────

  const downloadSubtitle = useCallback(async (result: SubtitleResult): Promise<string | null> => {
    // Check disk cache before downloading
    const cached = await SubtitleCacheService.getFilePath(result.id);
    if (cached) return cached;

    setDownloadingSubId(result.id);
    try {
      return await SubtitleCacheService.downloadAndCache(result);
    } finally {
      setDownloadingSubId(null);
    }
  }, []);

  // ── Auto-select helper ──────────────────────────────────────────────────

  const getBestResult = useCallback((): SubtitleResult | null => {
    if (results.length === 0) return null;
    return pickBestSubtitle(results, {
      languageOrder,
      preferForced,
    });
  }, [languageOrder, preferForced, results]);

  // ── Delay ───────────────────────────────────────────────────────────────

  const setDelay = useCallback((seconds: number) => {
    // Clamp to a sensible range (−30s … +30s) to avoid accidental extreme values
    setDelayState(Math.max(-30, Math.min(30, seconds)));
  }, []);

  // ── Session reset (source switch, same content) ─────────────────────────

  const resetSession = useCallback(() => {
    // Clear only the per-playback state — results and dedup cache are kept so
    // the subtitle list remains populated without a new network request.
    setActiveExternalSubId(null);
    setDelayState(0);
  }, []);

  // ── Full clear (content change or player destruction) ────────────────────

  const clearSearch = useCallback(() => {
    setSearchState('idle');
    setResults([]);
    setActiveExternalSubId(null);
    setDelayState(0);
    searchedVideoIdsRef.current.clear();
  }, []);

  // ── Cache utilities ─────────────────────────────────────────────────────

  const clearFileCache = useCallback(() => SubtitleCacheService.clearFileCache(), []);
  const getFileCacheSize = useCallback(() => SubtitleCacheService.getFileCacheSize(), []);

  return (
    <SubtitleContext.Provider
      value={{
        // Settings
        autoLoadEnabled,
        languageOrder,
        preferHI,
        preferForced,
        addonUrl,
        isReady,
        // Search state
        searchState,
        results,
        // Session state
        activeExternalSubId,
        downloadingSubId,
        delay,
        // Actions
        search,
        downloadSubtitle,
        getBestResult,
        setActiveExternalSubId,
        setDelay,
        resetSession,
        clearSearch,
        // Settings setters
        setAutoLoadEnabled,
        setLanguageOrder,
        setPreferHI,
        setPreferForced,
        setAddonUrl,
        // Utilities
        clearFileCache,
        getFileCacheSize,
      }}
    >
      {children}
    </SubtitleContext.Provider>
  );
};

export const useSubtitles = () => useContext(SubtitleContext);
