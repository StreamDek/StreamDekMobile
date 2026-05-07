import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Animated,
  AppState,
  Image,
  Modal,
  NativeModules,
  Platform,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useDisplaySettings } from '../context/DisplaySettingsContext';
import Slider from '@react-native-community/slider';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import * as ScreenOrientation from 'expo-screen-orientation';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  MpvPlayer,
  MpvPlayerHandle,
  MpvTrack,
  MpvTracksChangedEvent,
  isMpvNativeViewAvailable,
} from '../components/MpvPlayer';
import { AddonStream, useAddons } from '../context/AddonContext';
import { useAuth } from '../context/AuthContext';
import { useDebrid } from '../context/DebridContext';
import { PlaybackDecoderMode, PlaybackRenderSurface, usePlaybackSettings } from '../context/PlaybackSettingsContext';
import { useProfile } from '../context/ProfileContext';
import { useStreamSelectionSettings } from '../context/StreamSelectionContext';
import { useTorrentServer } from '../context/TorrentServerContext';
import { ScrobblePayload, useTrakt } from '../context/TraktContext';
import { useWatched } from '../context/WatchedContext';
import { useWatchProgress } from '../context/WatchProgressContext';
import { scoreStream } from '../utils/streamSelection';
import { parseStream } from '../utils/streamParser';
import { Storage } from '../utils/storage';
import { resolvePlayableStreamUrl } from '../services/playback/streamResolution';
import { useSubtitles } from '../context/SubtitleContext';
import { SubtitleResult } from '../services/subtitles/SubtitleProvider';
import { useWatchlistRemove, WatchlistRemoveItem } from '../hooks/useWatchlistRemove';
import {
  getProfileStorageOwnerId,
  progressFileStorageKey,
  progressIndexStorageKey,
} from '../utils/profileStorage';
import { ConfirmSheet } from '../components/ConfirmSheet';

const MAGIC_HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36',
};

const MIN_WATCH_SECONDS_TO_REMEMBER_SOURCE = 12;
const MIN_ACCEPTABLE_STREAM_DURATION_SEC = 5 * 60;
const CONTROLS_AUTO_HIDE_MS = 3500;
const MPV_LOADING_MESSAGES = [
  'Searching the shelves for a decent print...',
  'Asking the projectionist for something less cursed...',
  'Dusting off the reels and checking the labels...',
  'Giving another print a screen test...',
  'Swapping reels and hoping for fewer gremlins...',
  'Cueing up a different cut...',
];
const GUEST_ACCOUNT_PROMPT_SHOWN_KEY = 'streamdek_guest_account_prompt_shown';

type ResizeMode = 'contain' | 'cover' | 'stretch';
type MpvSourceOption = {
  index: number;
  identity: string;
  /** Media filename / release title */
  name: string;
  /** Resolution badge: "4K" | "1080p" | "720p" | null */
  quality: string | null;
  /** Addon / provider label */
  source: string;
};

type RememberedStreamChoice = {
  addonId?: string;
  infoHash?: string;
  url?: string;
  title?: string;
  name?: string;
  filename?: string;
  size?: string | null;
  quality?: string | null;
};

const RESIZE_MODE_LABELS: Record<ResizeMode, string> = {
  contain: 'Fit',
  cover: 'Fill',
  stretch: 'Str',
};

const RESIZE_MODE_ICONS: Record<ResizeMode, React.ComponentProps<typeof Ionicons>['name']> = {
  cover:   'scan',
  contain: 'scan-outline',
  stretch: 'resize-outline',
};

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00';
  const total = Math.floor(seconds);
  const hrs = Math.floor(total / 3600);
  const mins = Math.floor((total % 3600) / 60);
  const secs = total % 60;
  if (hrs > 0) {
    return `${hrs}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
  return `${mins}:${String(secs).padStart(2, '0')}`;
}

function getTrackLabel(track: MpvTrack): string {
  const bits: string[] = [];
  if (track.language && track.language.trim().length > 0) bits.push(track.language.toUpperCase());
  if (track.title && track.title.trim().length > 0) bits.push(track.title);
  if (track.codec && track.codec.trim().length > 0) bits.push(track.codec.toUpperCase());
  if (bits.length === 0) return `Track ${track.id}`;
  return bits.join(' - ');
}

function normalizeLanguage(value?: string | null): string {
  return (value ?? '').trim().toLowerCase().split(/[-_]/)[0];
}

function isEnglishAudioTrack(track: MpvTrack): boolean {
  const language = normalizeLanguage(track.language);
  if (language === 'en' || language === 'eng') return true;
  const text = `${track.title ?? ''} ${track.language ?? ''}`.toLowerCase();
  return /\benglish\b|\beng\b/.test(text);
}

function pickEnglishAudioTrack(tracks: MpvTrack[]): MpvTrack | null {
  if (!tracks.length) return null;
  return tracks.find(isEnglishAudioTrack) ?? null;
}

function isEnglishSubtitleTrack(track: MpvTrack): boolean {
  const language = normalizeLanguage(track.language);
  if (language === 'en' || language === 'eng') return true;
  const text = `${track.title ?? ''} ${track.language ?? ''}`.toLowerCase();
  return /\benglish\b|\beng\b/.test(text);
}

function pickPreferredSubtitleTrack(tracks: MpvTrack[]): MpvTrack | null {
  if (!tracks.length) return null;
  return tracks.find(isEnglishSubtitleTrack) ?? tracks[0] ?? null;
}

function normalizeSourceIdentity(value: unknown): string {
  if (typeof value !== 'string') return '';
  return value.trim().toLowerCase();
}

function normalizeStreamText(value?: string | null): string {
  return (value ?? '').trim().replace(/\s+/g, ' ').toLowerCase();
}

function streamIdentityKey(stream: AddonStream | null | undefined): string {
  if (!stream) return '';
  return normalizeStreamText(
    stream.infoHash
    ?? stream.url
    ?? stream.behaviorHints?.filename
    ?? stream.title
    ?? stream.name
    ?? '',
  );
}

function serializeRememberedStream(stream: AddonStream, resolvedUrl?: string | null): RememberedStreamChoice {
  return {
    addonId: stream.addonId,
    infoHash: stream.infoHash?.toLowerCase(),
    url: stream.url ?? resolvedUrl ?? undefined,
    title: normalizeStreamText(stream.title),
    name: normalizeStreamText(stream.name),
    filename: normalizeStreamText(stream.behaviorHints?.filename),
    size: normalizeStreamText(stream.size),
    quality: stream.quality,
  };
}

function streamMatchesRemembered(stream: AddonStream, remembered: RememberedStreamChoice | null): boolean {
  if (!remembered) return false;

  const rememberedInfoHash = remembered.infoHash?.toLowerCase();
  if (rememberedInfoHash && stream.infoHash?.toLowerCase() === rememberedInfoHash) return true;
  if (remembered.url && stream.url === remembered.url) return true;
  if (remembered.addonId && stream.addonId !== remembered.addonId) return false;

  const rememberedFilename = normalizeStreamText(remembered.filename);
  const rememberedTitle = normalizeStreamText(remembered.title);
  const rememberedName = normalizeStreamText(remembered.name);
  const rememberedSize = normalizeStreamText(remembered.size);
  const streamFilename = normalizeStreamText(stream.behaviorHints?.filename);
  const streamTitle = normalizeStreamText(stream.title);
  const streamName = normalizeStreamText(stream.name);
  const streamSize = normalizeStreamText(stream.size);

  if (rememberedFilename && streamFilename && rememberedFilename === streamFilename) return true;
  if (rememberedTitle && streamTitle && rememberedTitle === streamTitle) return true;

  if (rememberedInfoHash || remembered.url) return false;

  return !!rememberedName
    && !!streamName
    && rememberedName === streamName
    && !!rememberedSize
    && rememberedSize === streamSize
    && remembered.quality === stream.quality;
}

function getUrlHost(url: unknown): string {
  if (typeof url !== 'string' || !url) return 'Unknown';
  try {
    const parsed = new URL(url);
    return parsed.host || 'Unknown';
  } catch {
    return 'Unknown';
  }
}

async function saveToProgressIndex(ownerId: string | null, entry: any): Promise<void> {
  try {
    const key = progressIndexStorageKey(ownerId);
    const raw = await Storage.getItem(key);
    const index: any[] = raw ? JSON.parse(raw) : [];
    const idx = index.findIndex(e => e.key === entry.key);
    const full = { ...entry, updatedAt: new Date().toISOString() };
    if (idx >= 0) index[idx] = full;
    else index.unshift(full);
    const pruned = index
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .slice(0, 50);
    await Storage.setItem(key, JSON.stringify(pruned));
  } catch {
    // Ignore continue-watching index save failures.
  }
}

function buildPayload(movieId: string, type: string, title?: string, year?: number, progress?: number): ScrobblePayload {
  const tmdbId = parseInt(movieId, 10) || undefined;
  if (type === 'tv') {
    return { show: { title: title ?? '', year, ids: { tmdb: tmdbId } }, progress: progress ?? 0 };
  }
  return { movie: { title: title ?? '', year, ids: { tmdb: tmdbId } }, progress: progress ?? 0 };
}

export const MpvPlayerScreen = ({ route, navigation }: any) => {
  const insets = useSafeAreaInsets();
  const { scrobble, isConnected, watchlist } = useTrakt();
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const { toggleMovieWatched, toggleEpisodeWatched } = useWatched();
  const { removeFromWatchlist } = useWatchlistRemove();
  const { fetchStreams } = useAddons();
  const subtitle = useSubtitles();
  const { saveProgress, clearProgress } = useWatchProgress();
  const storageOwnerId = getProfileStorageOwnerId(user?.uid ?? null, activeProfile?.id ?? null);
  const legacyOwnerId = user?.uid ?? null;
  const { accounts: debridAccounts, resolveStream, streamTorrent } = useDebrid();
  const { config: serverConfig } = useTorrentServer();
  const {
    decoderMode,
    renderSurface,
    setDecoderMode,
    setRenderSurface,
    refreshFromCloud: refreshPlaybackFromCloud,
  } = usePlaybackSettings();
  const {
    enabled: streamSelectionEnabled,
    setEnabled: setStreamSelectionEnabled,
    shortSourceFilterEnabled,
    setShortSourceFilterEnabled,
    maxFileSizeGB,
    preferredQuality,
    refreshFromCloud: refreshStreamSelectionFromCloud,
  } = useStreamSelectionSettings();
  const { pictureInPictureEnabled } = useDisplaySettings();
  const {
    streamUrl,
    title,
    year,
    type = 'movie',
    resumeFrom = 0,
    forceStartFromBeginning: paramForceStartFromBeginning,
    headers,
    titleLogo,
    synopsis,
    backdrop,
    poster,
    initialLoadingMessage,
    returnToPlayerParams,
    rememberedSourceKey,
    rememberedSourceValue,
    sourceOptions: routeSourceOptions,
    sourceStreams: routeSourceStreams,
    activeSourceIdentity: routeActiveSourceIdentity,
    preferredSourceIndex: routePreferredSourceIndex,
    preferredSourceIdentity: routePreferredSourceIdentity,
    resolveOnMount,
    resolverMovieId,
    resolverImdbId,
    resolverType,
    // Season + episode — present when type === 'tv'
    season: routeSeason,
    episode: routeEpisode,
  } = route.params ?? {};

  // Normalise season/episode to numbers (route params can be strings or numbers)
  const season: number | null = Number.isFinite(Number(routeSeason)) && Number(routeSeason) > 0
    ? Math.trunc(Number(routeSeason))
    : null;
  const episode: number | null = Number.isFinite(Number(routeEpisode)) && Number(routeEpisode) > 0
    ? Math.trunc(Number(routeEpisode))
    : null;

  const initialResolvedStreamUrl = typeof streamUrl === 'string' && streamUrl.length > 0
    ? streamUrl
    : null;
  const rememberedSourceStorageKey = typeof rememberedSourceKey === 'string' ? rememberedSourceKey : null;
  const routeParams = route.params ?? {};
  const returnParams = returnToPlayerParams && typeof returnToPlayerParams === 'object'
    ? (returnToPlayerParams as Record<string, unknown>)
    : null;
  const movieId = String(
    resolverMovieId
    ?? routeParams.movieId
    ?? returnParams?.movieId
    ?? '',
  );
  const imdbId = typeof resolverImdbId === 'string'
    ? resolverImdbId
    : typeof routeParams.imdbId === 'string'
      ? routeParams.imdbId
      : typeof returnParams?.imdbId === 'string'
        ? returnParams.imdbId
        : null;
  const resolverContentType = resolverType === 'tv'
    ? 'tv'
    : type === 'tv'
      ? 'tv'
      : 'movie';
  const progressKey = typeof routeParams.progressKey === 'string'
    ? routeParams.progressKey
    : typeof returnParams?.progressKey === 'string'
      ? returnParams.progressKey
      : null;
  const loadingArtworkUri = useMemo(() => {
    if (typeof backdrop === 'string' && backdrop.length > 0) return backdrop;
    if (typeof poster === 'string' && poster.length > 0) return poster;

    if (returnToPlayerParams && typeof returnToPlayerParams === 'object') {
      const params = returnToPlayerParams as Record<string, unknown>;
      if (typeof params.backdrop === 'string' && params.backdrop.length > 0) return params.backdrop;
      if (typeof params.poster === 'string' && params.poster.length > 0) return params.poster;
    }
    return null;
  }, [backdrop, poster, returnToPlayerParams]);
  const titleLogoUri = useMemo(() => {
    if (typeof titleLogo === 'string' && titleLogo.length > 0) return titleLogo;
    if (returnToPlayerParams && typeof returnToPlayerParams === 'object') {
      const params = returnToPlayerParams as Record<string, unknown>;
      if (typeof params.titleLogo === 'string' && params.titleLogo.length > 0) return params.titleLogo;
    }
    return null;
  }, [returnToPlayerParams, titleLogo]);
  const resolvedSynopsis = useMemo(() => {
    if (typeof synopsis === 'string' && synopsis.trim().length > 0) return synopsis.trim();
    if (returnToPlayerParams && typeof returnToPlayerParams === 'object') {
      const params = returnToPlayerParams as Record<string, unknown>;
      if (typeof params.synopsis === 'string' && params.synopsis.trim().length > 0) return params.synopsis.trim();
    }
    return null;
  }, [synopsis, returnToPlayerParams]);
  const forceStartFromBeginning = Boolean(paramForceStartFromBeginning);

  const playerRef = useRef<MpvPlayerHandle>(null);
  const autoEnglishSelectionKeyRef = useRef<string | null>(null);
  /**
   * True once we've attempted embedded-subtitle auto-select for the current
   * file load. Prevents the selector from re-firing on subsequent
   * onTracksChanged events (e.g. when external subs are added later).
   * Also set to true when an external subtitle starts loading so the two
   * systems never race each other.
   */
  const didAutoSelectEmbeddedRef = useRef(false);
  /** Set to true when the user explicitly picks "None" — suppresses auto-select until source changes */
  const userDisabledSubtitlesRef = useRef(false);
  /** Tracks the subtitle download error state for transient UI feedback */
  const [subDownloadError, setSubDownloadError] = React.useState(false);
  const rememberedSourceSavedRef = useRef(false);
  /** Stores the stream state to revert to if an in-player source switch fails. */
  const sourceSwitchBackupRef = useRef<{ url: string; identity: string; resumeAt: number } | null>(null);
  const rejectedShortSourceKeysRef = useRef<Set<string>>(new Set());
  const sourceResolverInFlightRef = useRef(false);
  const controlsTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const skipPortraitOnBlurRef = useRef(false);
  const cloudSyncPrimedRef = useRef(false);
  const controlsOpacity = useRef(new Animated.Value(1)).current;
  const loadingOverlayOpacity = useRef(new Animated.Value(1)).current;
  const loadingTextOpacity = useRef(new Animated.Value(1)).current;
  const logoBreathAnim = useRef(new Animated.Value(1)).current;
  const watchlistBannerAnim = useRef(new Animated.Value(200)).current;
  const lastProgressSaveRef = useRef(0);
  const playbackPosRef = useRef(0);
  const playbackDurRef = useRef(0);
  const playbackCompletedRef = useRef(false);
  const progressTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const scrobbledStartRef = useRef(false);
  const watchThresholdFiredRef = useRef(false);
  const payloadRef = useRef({
    movieId,
    type: type === 'tv' ? 'tv' : 'movie',
    title,
    year,
  });

  const [paused, setPaused] = useState(false);
  const [progressBarWidth, setProgressBarWidth] = useState(1);
  const progressBarWidthRef = useRef(1);
  const guestPromptHandledRef = useRef(false);
  const [showControls, setShowControls] = useState(true);
  const [loading, setLoading] = useState(true);
  const [startupTrackSelectionReady, setStartupTrackSelectionReady] = useState(false);
  const [watchlistBannerItem, setWatchlistBannerItem] = useState<WatchlistRemoveItem | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [didSeekInitialResume, setDidSeekInitialResume] = useState(false);
  const [isSeeking, setIsSeeking] = useState(false);
  const [effectiveResumeFrom, setEffectiveResumeFrom] = useState(() => (
    Number.isFinite(Number(resumeFrom)) ? Math.max(0, Number(resumeFrom)) : 0
  ));
  const [playbackRate, setPlaybackRate] = useState(1);
  const [resizeMode, setResizeMode] = useState<ResizeMode>('cover');
  const [audioTracks, setAudioTracks] = useState<MpvTrack[]>([]);
  const [subtitleTracks, setSubtitleTracks] = useState<MpvTrack[]>([]);
  const [selectedAudioTrackId, setSelectedAudioTrackId] = useState<number | null>(null);
  const [selectedSubtitleTrackId, setSelectedSubtitleTrackId] = useState<number | null>(null);
  const [showTrackPicker, setShowTrackPicker] = useState(false);
  const [showAudioModal, setShowAudioModal] = useState(false);
  const [switchToast, setSwitchToast] = useState<string | null>(null);
  const [subTab, setSubTab] = useState<'builtin' | 'addons' | 'style'>('builtin');
  const [subFontSize, setSubFontSize] = useState(55);
  const [subColor, setSubColor] = useState('#FFFFFFFF');
  const [subPos, setSubPos] = useState(90);
  const [showInfoModal, setShowInfoModal] = useState(false);
  const [showLoadingOverlay, setShowLoadingOverlay] = useState(true);
  const [resolvedStreamUrl, setResolvedStreamUrl] = useState<string | null>(initialResolvedStreamUrl);
  const [resolvedSourceStreams, setResolvedSourceStreams] = useState<AddonStream[]>([]);
  const [showGuestAccountPrompt, setShowGuestAccountPrompt] = useState(false);
  const [activeSourceIdentityState, setActiveSourceIdentityState] = useState<string>(() => {
    const routeIdentity = normalizeSourceIdentity(routeActiveSourceIdentity);
    if (routeIdentity) return routeIdentity;
    return normalizeSourceIdentity(routePreferredSourceIdentity);
  });
  const [loadingMessage, setLoadingMessage] = useState<string>(() => {
    if (typeof initialLoadingMessage === 'string' && initialLoadingMessage.trim().length > 0) {
      return initialLoadingMessage;
    }
    return MPV_LOADING_MESSAGES[0];
  });

  const resolvedHeaders = useMemo(
    () => ({
      ...MAGIC_HEADERS,
      ...(headers ?? {}),
    }),
    [headers],
  );
  const routeSourceStreamsList = useMemo<AddonStream[]>(() => {
    if (!Array.isArray(routeSourceStreams)) return [];
    return routeSourceStreams.filter((candidate: unknown): candidate is AddonStream => (
      Boolean(candidate) && typeof candidate === 'object'
    ));
  }, [routeSourceStreams]);
  const sourceOptions = useMemo<MpvSourceOption[]>(() => {
    if (resolvedSourceStreams.length > 0) {
      return resolvedSourceStreams
        .map((stream, index) => {
          const parsed = parseStream(stream);
          const identity = streamIdentityKey(stream);
          if (!identity) return null;
          return {
            index,
            identity,
            name: parsed.fileTitle || stream.title?.split('\n')[0]?.trim() || `Source ${index + 1}`,
            quality: stream.quality ?? null,
            source: parsed.providerLine || stream.addonName || stream.addonId || 'Unknown',
          } as MpvSourceOption;
        })
        .filter((candidate): candidate is MpvSourceOption => Boolean(candidate));
    }

    if (!Array.isArray(routeSourceOptions)) return [];

    return routeSourceOptions
      .map((candidate: any, fallbackIndex: number) => {
        const index = Number.isFinite(candidate?.index) ? Number(candidate.index) : fallbackIndex;
        const identity = normalizeSourceIdentity(candidate?.identity);
        const name = typeof candidate?.name === 'string' && candidate.name.trim().length > 0
          ? candidate.name.trim()
          : typeof candidate?.title === 'string' && candidate.title.trim().length > 0
            ? candidate.title.trim()
            : `Source ${index + 1}`;
        const quality = typeof candidate?.quality === 'string' && candidate.quality.trim().length > 0
          ? candidate.quality.trim()
          : typeof candidate?.details === 'string' && candidate.details.trim().length > 0
            ? candidate.details.trim()
            : null;
        const source = typeof candidate?.source === 'string' && candidate.source.trim().length > 0
          ? candidate.source.trim()
          : typeof candidate?.provider === 'string' && candidate.provider.trim().length > 0
            ? candidate.provider.trim()
            : 'Unknown';

        return { index, identity, name, quality, source };
      })
      .filter(candidate => candidate.identity.length > 0);
  }, [resolvedSourceStreams, routeSourceOptions]);
  const activeSourceIdentity = useMemo(() => {
    if (activeSourceIdentityState) return activeSourceIdentityState;
    return normalizeSourceIdentity(routeActiveSourceIdentity);
  }, [activeSourceIdentityState, routeActiveSourceIdentity]);

  const persistRememberedSource = useCallback(async () => {
    if (rememberedSourceSavedRef.current) return;
    if (!rememberedSourceStorageKey) return;
    if (currentTime < MIN_WATCH_SECONDS_TO_REMEMBER_SOURCE) return;

    let payload: Record<string, unknown> | null = null;
    if (rememberedSourceValue && typeof rememberedSourceValue === 'object') {
      payload = rememberedSourceValue as Record<string, unknown>;
    } else if (resolvedSourceStreams.length > 0) {
      const active = resolvedSourceStreams.find(stream => streamIdentityKey(stream) === activeSourceIdentity) ?? null;
      if (active) {
        payload = serializeRememberedStream(active, resolvedStreamUrl);
      }
    }
    if (!payload) return;

    try {
      await Storage.setItem(rememberedSourceStorageKey, JSON.stringify(payload));
      rememberedSourceSavedRef.current = true;
    } catch {
      // Ignore preference save failures.
    }
  }, [activeSourceIdentity, currentTime, rememberedSourceKey, rememberedSourceValue, resolvedSourceStreams, resolvedStreamUrl, rememberedSourceStorageKey]);

  const clearControlsTimer = useCallback(() => {
    if (!controlsTimerRef.current) return;
    clearTimeout(controlsTimerRef.current);
    controlsTimerRef.current = null;
  }, []);

  const showControlsAnimated = useCallback(() => {
    setShowControls(true);
    controlsOpacity.stopAnimation();
    Animated.timing(controlsOpacity, {
      toValue: 1,
      duration: 180,
      useNativeDriver: true,
    }).start();
  }, [controlsOpacity]);

  const hideControlsAnimated = useCallback(() => {
    controlsOpacity.stopAnimation();
    Animated.timing(controlsOpacity, {
      toValue: 0,
      duration: 220,
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) {
        setShowControls(false);
      }
    });
  }, [controlsOpacity]);

  const scheduleControlsAutoHide = useCallback(
    (forceShow: boolean = true) => {
      if (forceShow) {
        showControlsAnimated();
      }

      clearControlsTimer();
      if (loading || !!error || showTrackPicker || showAudioModal || showInfoModal) return;

      controlsTimerRef.current = setTimeout(() => {
        hideControlsAnimated();
        controlsTimerRef.current = null;
      }, paused ? 3000 : CONTROLS_AUTO_HIDE_MS);
    },
    [
      clearControlsTimer,
      error,
      hideControlsAnimated,
      loading,
      paused,
      showAudioModal,
      showControlsAnimated,
      showInfoModal,
      showTrackPicker,
    ],
  );

  const toggleControlsVisibility = useCallback(() => {
    if (showTrackPicker || showAudioModal || showInfoModal) return;

    const nextShow = !showControls;
    clearControlsTimer();

    if (nextShow) {
      scheduleControlsAutoHide(true);
      return;
    }

    hideControlsAnimated();
  }, [
    clearControlsTimer,
    hideControlsAnimated,
    scheduleControlsAutoHide,
    showAudioModal,
    showControls,
    showInfoModal,
    showTrackPicker,
  ]);

  const keepControlsAwake = useCallback(() => {
    scheduleControlsAutoHide(true);
  }, [scheduleControlsAutoHide]);

  useEffect(() => {
    scheduleControlsAutoHide(true);
    return () => {
      clearControlsTimer();
    };
  }, [clearControlsTimer, scheduleControlsAutoHide]);

  // ── Picture in Picture ────────────────────────────────────────────────────
  useEffect(() => {
    if (Platform.OS !== 'android') return;
    NativeModules.PiPModule?.setEnabled(pictureInPictureEnabled);
    return () => {
      NativeModules.PiPModule?.setEnabled(false);
    };
  }, [pictureInPictureEnabled]);

  useEffect(() => {
    if (Platform.OS !== 'android' || !pictureInPictureEnabled) return;
    const sub = AppState.addEventListener('change', state => {
      if (state === 'inactive' || state === 'background') {
        NativeModules.PiPModule?.enterPiP();
      }
    });
    return () => sub.remove();
  }, [pictureInPictureEnabled]);

  const anyPopupOpen = showTrackPicker || showAudioModal || showInfoModal;
  useEffect(() => {
    if (anyPopupOpen) {
      clearControlsTimer();
      setShowControls(false);
      controlsOpacity.setValue(0);
      return;
    }

    if (!loading && !error) {
      scheduleControlsAutoHide(true);
    }
  }, [anyPopupOpen, clearControlsTimer, controlsOpacity, error, loading, scheduleControlsAutoHide]);

  useFocusEffect(
    useCallback(() => {
      ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.LANDSCAPE).catch(() => undefined);
      return () => {
        if (!skipPortraitOnBlurRef.current) {
          ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP).catch(() => undefined);
        }
        skipPortraitOnBlurRef.current = false;
      };
    }, []),
  );

  useEffect(() => {
    if (cloudSyncPrimedRef.current) return;
    cloudSyncPrimedRef.current = true;
    void refreshPlaybackFromCloud();
    void refreshStreamSelectionFromCloud();
  }, [refreshPlaybackFromCloud, refreshStreamSelectionFromCloud]);

  // ── Subtitle search ──────────────────────────────────────────────────────
  // Fire once playback has a resolved URL and we have an IMDB ID to search with.
  // The search runs non-blocking in the background; playback is never delayed.
  useEffect(() => {
    if (!resolvedStreamUrl || !imdbId) return;
    const streamReleaseName = activeSourceOption?.name ?? null;
    subtitle.search(
      {
        type: resolverContentType === 'tv' ? 'series' : 'movie',
        imdbId,
        season,
        episode,
        title,
        year,
      },
      streamReleaseName,
    );
  // subtitle.search is stable (useCallback with no deps); intentionally excluded
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resolvedStreamUrl, imdbId]);

  // ── Auto-load best subtitle ──────────────────────────────────────────────
  // Fires whenever searchState completes OR activeExternalSubId is cleared
  // (e.g. after resetSession on source switch), so a new source gets an
  // external subtitle loaded automatically if auto-load is enabled.
  // Guards:
  //   • autoLoadEnabled must be on
  //   • search must be done (results available)
  //   • no external subtitle already active
  //   • user hasn't explicitly chosen "None" for this source
  useEffect(() => {
    if (!subtitle.autoLoadEnabled) return;
    if (subtitle.searchState !== 'done') return;
    if (subtitle.activeExternalSubId !== null) return;
    if (userDisabledSubtitlesRef.current) return;
    const best = subtitle.getBestResult();
    if (!best) return;
    void handleSelectOsSubtitle(best);
  // handleSelectOsSubtitle is a stable useCallback and referenced directly;
  // the eslint-disable below covers its absence from the dep array.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subtitle.searchState, subtitle.autoLoadEnabled, subtitle.activeExternalSubId]);

  // ── Reset subtitle session on source switch ─────────────────────────────
  // When the resolved URL changes (user picks a different stream source for the
  // same movie) we reset only per-playback state — the search results and dedup
  // cache are intentionally preserved so the subtitle list repopulates instantly
  // without a new network request (same content = same subtitles).
  //
  // We do NOT call clearSearch() here because that empties results and the search
  // effect won't re-fire unless imdbId also changes, leaving the list blank.
  const prevResolvedUrlRef = useRef<string | null>(null);
  useEffect(() => {
    if (prevResolvedUrlRef.current !== null && prevResolvedUrlRef.current !== resolvedStreamUrl) {
      subtitle.resetSession();          // clears activeExternalSubId + delay, keeps results
      didAutoSelectEmbeddedRef.current = false;   // allow embedded auto-select for new file
      userDisabledSubtitlesRef.current = false;   // lift "None" suppression for new source
      setStartupTrackSelectionReady(false);
    }
    prevResolvedUrlRef.current = resolvedStreamUrl;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resolvedStreamUrl]);

  useEffect(() => {
    if (routeSourceStreamsList.length === 0) return;
    setResolvedSourceStreams(routeSourceStreamsList);
  }, [routeSourceStreamsList]);

  useEffect(() => {
    if (resolvedStreamUrl) return;
    if (resolveOnMount) return;
    setLoading(false);
    setError('No stream URL is available for MPV playback.');
  }, [resolveOnMount, resolvedStreamUrl]);

  const rankStreams = useCallback((streams: AddonStream[]): AddonStream[] => {
    const opts = streamSelectionEnabled
      ? { preferQuickStart: true, maxFileSizeGB: maxFileSizeGB > 0 ? maxFileSizeGB : undefined, preferredQuality }
      : { preferQuickStart: true };
    return [...streams].sort((a, b) => scoreStream(b, opts) - scoreStream(a, opts));
  }, [streamSelectionEnabled, maxFileSizeGB, preferredQuality]);

  const resolveSourceStreamUrl = useCallback(async (stream: AddonStream): Promise<string | null> => {
    return resolvePlayableStreamUrl({
      stream,
      debridAccountCount: debridAccounts.length,
      resolveStream,
      streamTorrent,
      streamingMode: serverConfig.streamingMode,
      streamSelectionEnabled,
      maxFileSizeGB,
    });
  }, [debridAccounts.length, maxFileSizeGB, resolveStream, serverConfig.streamingMode, streamSelectionEnabled, streamTorrent]);

  useEffect(() => {
    if (!resolveOnMount || resolvedStreamUrl) return;
    if (sourceResolverInFlightRef.current) return;

    let cancelled = false;
    sourceResolverInFlightRef.current = true;

    void (async () => {
      try {
        setError(null);
        setLoading(true);
        setShowLoadingOverlay(true);

        const contentId = (imdbId && imdbId.trim().length > 0) ? imdbId : movieId;
        if (!contentId) {
          setError('No source identifier was provided for MPV playback.');
          setLoading(false);
          return;
        }

        const fetched = routeSourceStreamsList.length > 0
          ? routeSourceStreamsList
          : await fetchStreams(resolverContentType === 'tv' ? 'series' : 'movie', contentId);
        if (cancelled) return;

        const ranked = rankStreams(fetched);
        setResolvedSourceStreams(ranked);

        const preferredIdentity = normalizeSourceIdentity(routePreferredSourceIdentity);
        const preferredIndex = Number.isFinite(Number(routePreferredSourceIndex))
          ? Math.trunc(Number(routePreferredSourceIndex))
          : NaN;

        let remembered: RememberedStreamChoice | null = null;
        if (rememberedSourceStorageKey) {
          try {
            const rememberedRaw = await Storage.getItem(rememberedSourceStorageKey);
            remembered = rememberedRaw ? JSON.parse(rememberedRaw) : null;
          } catch {
            remembered = null;
          }
        }

        let selected = preferredIdentity
          ? ranked.find(stream => streamIdentityKey(stream) === preferredIdentity) ?? null
          : null;
        if (!selected && Number.isFinite(preferredIndex) && preferredIndex >= 0 && preferredIndex < ranked.length) {
          selected = ranked[preferredIndex];
        }
        if (!selected) {
          selected = ranked.find(stream => streamMatchesRemembered(stream, remembered)) ?? null;
        }
        if (!selected) {
          selected = ranked[0] ?? null;
        }

        if (!selected) {
          setError('No playable sources were found for this title.');
          setLoading(false);
          return;
        }

        const selectedIdentity = streamIdentityKey(selected);
        if (selectedIdentity) setActiveSourceIdentityState(selectedIdentity);

        const resolved = await resolveSourceStreamUrl(selected);
        if (cancelled) return;
        if (!resolved) {
          setError('Could not resolve a playable URL for this source.');
          setLoading(false);
          return;
        }

        rememberedSourceSavedRef.current = false;
        setResolvedStreamUrl(resolved);
      } finally {
        sourceResolverInFlightRef.current = false;
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [
    fetchStreams,
    imdbId,
    movieId,
    rankStreams,
    rememberedSourceStorageKey,
    resolveOnMount,
    resolveSourceStreamUrl,
    resolvedStreamUrl,
    resolverContentType,
    routePreferredSourceIdentity,
    routePreferredSourceIndex,
    routeSourceStreamsList,
  ]);

  useEffect(() => {
    return () => {
      void persistRememberedSource();
    };
  }, [persistRememberedSource]);

  useEffect(() => {
    payloadRef.current = {
      movieId,
      type: type === 'tv' ? 'tv' : 'movie',
      title,
      year,
    };
  }, [movieId, title, type, year]);

  // Breathing animation on the title logo/text while loading
  useEffect(() => {
    if (!loading || !!error) {
      logoBreathAnim.setValue(1);
      return;
    }
    const anim = Animated.loop(
      Animated.sequence([
        Animated.timing(logoBreathAnim, { toValue: 0.3, duration: 1400, useNativeDriver: true }),
        Animated.timing(logoBreathAnim, { toValue: 1,   duration: 1400, useNativeDriver: true }),
      ])
    );
    anim.start();
    return () => anim.stop();
  }, [error, loading, logoBreathAnim]);

  useEffect(() => {
    if (!loading || !!error) return;
    let cursor = 0;
    const FADE_MS = 300;
    const HOLD_MS = 4000;
    const tick = () => {
      Animated.timing(loadingTextOpacity, {
        toValue: 0,
        duration: FADE_MS,
        useNativeDriver: true,
      }).start(() => {
        cursor = (cursor + 1) % MPV_LOADING_MESSAGES.length;
        setLoadingMessage(MPV_LOADING_MESSAGES[cursor]);
        Animated.timing(loadingTextOpacity, {
          toValue: 1,
          duration: FADE_MS,
          useNativeDriver: true,
        }).start();
      });
    };
    const timer = setInterval(tick, HOLD_MS);
    return () => clearInterval(timer);
  }, [error, loading, loadingTextOpacity]);

  useEffect(() => {
    if (error) {
      setShowLoadingOverlay(false);
      loadingOverlayOpacity.setValue(0);
      return;
    }

    if (loading) {
      setShowLoadingOverlay(true);
      Animated.timing(loadingOverlayOpacity, {
        toValue: 1,
        duration: 160,
        useNativeDriver: true,
      }).start();
      return;
    }

    Animated.timing(loadingOverlayOpacity, {
      toValue: 0,
      duration: 420,
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) setShowLoadingOverlay(false);
    });
  }, [error, loading, loadingOverlayOpacity]);

  useEffect(() => {
    if (forceStartFromBeginning) {
      setEffectiveResumeFrom(0);
      return;
    }
    if (Number.isFinite(Number(resumeFrom)) && Number(resumeFrom) > 0) {
      setEffectiveResumeFrom(Math.max(0, Number(resumeFrom)));
      return;
    }
    if (!progressKey) return;

    let cancelled = false;
    void (async () => {
      try {
        const raw = await Storage.getItem(progressFileStorageKey(storageOwnerId, progressKey));
        if (!raw || cancelled) return;
        const parsed = JSON.parse(raw);
        if (typeof parsed?.positionSec === 'number' && parsed.positionSec > 0) {
          setEffectiveResumeFrom(parsed.positionSec);
        }
      } catch {
        // Ignore malformed resume data.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [forceStartFromBeginning, progressKey, resumeFrom, storageOwnerId]);

  useEffect(() => {
    if (user || guestPromptHandledRef.current || loading || error || currentTime <= 0) {
      return;
    }

    guestPromptHandledRef.current = true;
    void (async () => {
      try {
        const alreadyShown = await Storage.getItem(GUEST_ACCOUNT_PROMPT_SHOWN_KEY);
        if (alreadyShown) return;
        await Storage.setItem(GUEST_ACCOUNT_PROMPT_SHOWN_KEY, '1');
        setShowGuestAccountPrompt(true);
      } catch {
        // Ignore prompt persistence failures.
      }
    })();
  }, [currentTime, error, loading, user]);

  const mpvNativeViewAvailable = isMpvNativeViewAvailable();
  const canRenderNativeMpv = mpvNativeViewAvailable && typeof resolvedStreamUrl === 'string' && resolvedStreamUrl.length > 0;

  const handleLoad = (event: any) => {
    // Source loaded successfully — clear any pending switch backup
    sourceSwitchBackupRef.current = null;
    const loadedDuration = Number(event?.nativeEvent?.duration ?? 0);
    if (
      shortSourceFilterEnabled
      && Number.isFinite(loadedDuration)
      && loadedDuration > 0
      && loadedDuration < MIN_ACCEPTABLE_STREAM_DURATION_SEC
    ) {
      const activeKey = activeSourceIdentity;
      if (activeKey) {
        rejectedShortSourceKeysRef.current.add(activeKey);
      }
      const fallbackStream = resolvedSourceStreams.find(stream => {
        const key = streamIdentityKey(stream);
        return !!key && key !== activeKey && !rejectedShortSourceKeysRef.current.has(key);
      });
      if (fallbackStream) {
        setSwitchToast('This source was too short — trying another one.');
        void switchToResolvedStream(fallbackStream, effectiveResumeFrom);
        return;
      }
    }
    setDuration(Number.isFinite(loadedDuration) ? loadedDuration : 0);
    if (!didSeekInitialResume && effectiveResumeFrom > 0) {
      playerRef.current?.seekTo(effectiveResumeFrom);
      setDidSeekInitialResume(true);
    }
  };

  /**
   * Fires once when playback passes the watched threshold:
   * >= 95% watched OR <= 8 minutes remaining.
   * - Marks the item as watched (silently)
   * - Removes it from the continue-watching index
   * - If the item is on the watchlist, shows a removal prompt banner
   */
  const handleWatchThreshold = useCallback(async () => {
    const tmdbIdNum = parseInt(movieId, 10);
    if (!tmdbIdNum) return;

    // ── 1. Mark as watched ─────────────────────────────────────────────────────
    if (resolverContentType === 'tv') {
      if (season != null && episode != null) {
        await toggleEpisodeWatched(
          tmdbIdNum,
          imdbId ?? undefined,
          title ?? '',
          season,
          episode,
        ).catch(() => {});
      }
    } else {
      await toggleMovieWatched(
        tmdbIdNum,
        imdbId ?? undefined,
        title ?? '',
        typeof year === 'number' ? year : parseInt(String(year ?? ''), 10) || undefined,
      ).catch(() => {});
    }

    // ── 2. Remove from continue-watching index ─────────────────────────────────
    if (progressKey) {
      // Clear the individual progress entry now that playback finished.
      void Storage.removeItem(progressFileStorageKey(storageOwnerId, progressKey)).catch(() => {});
      clearProgress(progressKey);
      // Remove from the progress index so it disappears from Continue Watching
      const indexKey = progressIndexStorageKey(storageOwnerId);
      const raw = await Storage.getItem(indexKey).catch(() => null);
      if (raw) {
        const index = (JSON.parse(raw) as any[]).filter((e: any) => e.key !== progressKey);
        void Storage.setItem(indexKey, JSON.stringify(index));
      }
    }

    // ── 3. Check watchlist membership & show banner ────────────────────────────
    const itemType = resolverContentType === 'tv' ? 'tv' : 'movie';
    const idStr = String(tmdbIdNum);

    // Check Trakt watchlist (in-memory)
    const traktEntry = watchlist.find(
      w => w.tmdbId === tmdbIdNum && w.type === itemType,
    );
    if (traktEntry) {
      setWatchlistBannerItem({
        id: idStr,
        title: traktEntry.title,
        year: traktEntry.year,
        type: itemType,
        tmdbId: tmdbIdNum,
        fromTrakt: true,
      });
      return;
    }

    // Check local watchlist
    if (user) {
      const wlKey = `streamdek_watchlist_${storageOwnerId}`;
      const raw = await Storage.getItem(wlKey).catch(() => null)
        ?? ((storageOwnerId !== legacyOwnerId && legacyOwnerId)
          ? await Storage.getItem(`streamdek_watchlist_${legacyOwnerId}`).catch(() => null)
          : null);
      if (raw) {
        const localItems = JSON.parse(raw) as any[];
        const localEntry = localItems.find(
          (i: any) => String(i.id) === idStr || String(i.tmdbId) === idStr,
        );
        if (localEntry) {
          setWatchlistBannerItem({
            id: idStr,
            title: localEntry.title ?? title ?? '',
            year: localEntry.year ?? year,
            type: itemType,
            tmdbId: tmdbIdNum,
            fromTrakt: false,
          });
        }
      }
    }
  }, [
    movieId, resolverContentType, season, episode, imdbId, title, year,
    toggleMovieWatched, toggleEpisodeWatched,
    progressKey, user, watchlist, clearProgress,
  ]);

  const handleProgress = (event: any) => {
    const nextCurrentTime = Number(event?.nativeEvent?.currentTime ?? 0);
    const nextDuration = Number(event?.nativeEvent?.duration ?? 0);
    playbackPosRef.current = nextCurrentTime;
    if (Number.isFinite(nextDuration) && nextDuration > 0) {
      playbackDurRef.current = nextDuration;
    }
    if (!isSeeking) {
      setCurrentTime(nextCurrentTime);
    }
    if (Number.isFinite(nextDuration) && nextDuration > 0) {
      setDuration(nextDuration);
    }
    if (!didSeekInitialResume && effectiveResumeFrom > 0 && Number.isFinite(nextDuration) && nextDuration > 0) {
      playerRef.current?.seekTo(effectiveResumeFrom);
      setCurrentTime(effectiveResumeFrom);
      setDidSeekInitialResume(true);
    }
    if (loading) setLoading(false);
    if (nextCurrentTime >= MIN_WATCH_SECONDS_TO_REMEMBER_SOURCE) {
      void persistRememberedSource();
    }

    if (progressKey && Number.isFinite(nextDuration) && nextDuration > 0 && nextCurrentTime - lastProgressSaveRef.current >= 10) {
      lastProgressSaveRef.current = nextCurrentTime;
      void Storage.setItem(
        progressFileStorageKey(storageOwnerId, progressKey),
        JSON.stringify({ positionSec: nextCurrentTime, durationSec: nextDuration }),
      );
      saveProgress(progressKey, nextCurrentTime, nextDuration);
      void saveToProgressIndex(storageOwnerId, {
        key: progressKey,
        tmdbId: Number(movieId),
        title: title ?? '',
        poster: (typeof poster === 'string' && poster) ? poster : undefined,
        backdrop: (typeof backdrop === 'string' && backdrop) ? backdrop : undefined,
        type: type || 'movie',
        year: String(year ?? ''),
        progressPct: Math.round((nextCurrentTime / nextDuration) * 100),
        positionSec: nextCurrentTime,
        durationSec: nextDuration,
      });
    }

    // ── Watched threshold: >= 95% or <= 8 min remaining ──────────────────────
    // Guard: must have watched at least 2 min to avoid firing at t=0 on load.
    // The 8-min rule only applies to videos longer than 20 min (otherwise 95%
    // fires first anyway and the remaining-time check would trigger immediately).
    if (
      !watchThresholdFiredRef.current &&
      Number.isFinite(nextDuration) &&
      nextDuration >= 60 &&
      nextCurrentTime >= 120 &&        // at least 2 min watched
      (
        nextCurrentTime / nextDuration >= 0.95 ||
        (nextDuration >= 1200 && nextDuration - nextCurrentTime <= 480)  // 8 min left, video > 20 min
      )
    ) {
      watchThresholdFiredRef.current = true;
      void handleWatchThreshold();
    }

    if (
      progressKey
      && !playbackCompletedRef.current
      && Number.isFinite(nextDuration)
      && nextDuration > 0
      && nextCurrentTime >= Math.max(0, nextDuration - 0.5)
    ) {
      playbackCompletedRef.current = true;
      watchThresholdFiredRef.current = true;
      void handleWatchThreshold();
      closeMpvPlayer();
    }
  };

  const handleError = (event: any) => {
    // If this error fired while switching sources, revert silently and show a toast.
    if (sourceSwitchBackupRef.current) {
      const backup = sourceSwitchBackupRef.current;
      sourceSwitchBackupRef.current = null;
      setResolvedStreamUrl(backup.url);
      setActiveSourceIdentityState(backup.identity);
      setEffectiveResumeFrom(backup.resumeAt);
      setLoading(false);
      setShowLoadingOverlay(false);
      setSwitchToast('Could not play this source — still on your previous stream.');
      return;
    }
    const message = String(event?.nativeEvent?.error ?? 'MPV playback failed.');
    setError(message);
    setLoading(false);
  };

  const handleTracksChanged = (event: { nativeEvent?: MpvTracksChangedEvent }) => {
    const payload = event?.nativeEvent;
    if (!payload) return;

    const nextAudioTracks = Array.isArray(payload.audioTracks) ? payload.audioTracks : [];
    const nextSubtitleTracks = Array.isArray(payload.subtitleTracks) ? payload.subtitleTracks : [];
    const nextSelectedAudioTrackId =
      typeof payload.selectedAudioTrackId === 'number' ? payload.selectedAudioTrackId : null;
    const nextSelectedSubtitleTrackId =
      typeof payload.selectedSubtitleTrackId === 'number' ? payload.selectedSubtitleTrackId : null;

    setAudioTracks(nextAudioTracks);
    setSubtitleTracks(nextSubtitleTracks);
    setSelectedAudioTrackId(nextSelectedAudioTrackId);
    setSelectedSubtitleTrackId(nextSelectedSubtitleTrackId);

    // ── Auto-select English audio track ─────────────────────────────────────
    const englishTrack = pickEnglishAudioTrack(nextAudioTracks);
    if (englishTrack && nextSelectedAudioTrackId !== englishTrack.id) {
      const trackKey = `${nextAudioTracks.map(track => `${track.id}:${normalizeLanguage(track.language)}`).join('|')}::${nextSelectedAudioTrackId ?? 'none'}`;
      if (autoEnglishSelectionKeyRef.current !== trackKey) {
        autoEnglishSelectionKeyRef.current = trackKey;
        playerRef.current?.setAudioTrack(englishTrack.id);
        setSelectedAudioTrackId(englishTrack.id);
      }
    }

    // ── Auto-select embedded subtitle track ──────────────────────────────────
    // MPV's sid=auto only selects a track explicitly marked "default" in the
    // container; most files omit that flag so subtitles never appear. We fall
    // back to selecting the first embedded track once per file load.
    //
    // The `didAutoSelectEmbeddedRef` flag prevents this block from re-firing on
    // every subsequent onTracksChanged event (e.g. when an external subtitle is
    // added later, which also triggers onTracksChanged). It is also set by
    // handleSelectOsSubtitle so external and embedded selection never race.
    const preferredSubtitleTrack = pickPreferredSubtitleTrack(nextSubtitleTracks);
    if (
      !didAutoSelectEmbeddedRef.current &&
      !userDisabledSubtitlesRef.current &&
      subtitle.activeExternalSubId === null &&
      preferredSubtitleTrack &&
      nextSelectedSubtitleTrackId !== preferredSubtitleTrack.id
    ) {
      didAutoSelectEmbeddedRef.current = true;
      playerRef.current?.setSubtitleTrack(preferredSubtitleTrack.id);
      setSelectedSubtitleTrackId(preferredSubtitleTrack.id);
    }

    setStartupTrackSelectionReady(true);
  };

  // ── External subtitle selection ──────────────────────────────────────────
  // Called when the user picks an OpenSubtitles result (or by auto-load).
  // 1. Immediately locks out embedded auto-select (prevents race with onTracksChanged).
  // 2. Downloads + caches the file.
  // 3. Injects into MPV via sub-add (MPV auto-selects it via the "select" flag).
  // 4. Marks it as the active external subtitle in context.
  const handleSelectOsSubtitle = useCallback(async (result: SubtitleResult) => {
    // Prevent the embedded auto-select from running while this external sub loads.
    // Even if onTracksChanged fires mid-download with sid=null, the flag stops it.
    didAutoSelectEmbeddedRef.current = true;
    setSubDownloadError(false);

    const filePath = await subtitle.downloadSubtitle(result);
    if (!filePath) {
      // Download failed — surface briefly, allow retry
      didAutoSelectEmbeddedRef.current = false;
      setSubDownloadError(true);
      setTimeout(() => setSubDownloadError(false), 3000);
      return;
    }
    // sub-add with "select" flag: MPV adds the track and immediately activates it,
    // then fires onTracksChanged which updates selectedSubtitleTrackId in state.
    playerRef.current?.addSubtitleFile(filePath);
    subtitle.setActiveExternalSubId(result.id);
    setShowTrackPicker(false);
  }, [subtitle]);

  // Apply the current delay value to mpv whenever it changes
  useEffect(() => {
    playerRef.current?.setSubtitleDelay(subtitle.delay);
  }, [subtitle.delay]);

  useEffect(() => {
    if (!canRenderNativeMpv || !startupTrackSelectionReady) return;
    playerRef.current?.setSubtitleFontSize(subFontSize);
    playerRef.current?.setSubtitlePosition(subPos);
    playerRef.current?.setSubtitleDelay(subtitle.delay);
  }, [
    canRenderNativeMpv,
    startupTrackSelectionReady,
    subFontSize,
    subPos,
    subtitle.delay,
  ]);

  useEffect(() => {
    if (!canRenderNativeMpv || !startupTrackSelectionReady) return;
    playerRef.current?.setSubtitleColor(subColor);
    if (selectedSubtitleTrackId != null) {
      playerRef.current?.setSubtitleTrack(selectedSubtitleTrackId);
    }
  }, [
    canRenderNativeMpv,
    selectedSubtitleTrackId,
    startupTrackSelectionReady,
    subColor,
  ]);


  // Auto-dismiss the source-switch failure toast after 4 s
  useEffect(() => {
    if (!switchToast) return;
    const t = setTimeout(() => setSwitchToast(null), 4000);
    return () => clearTimeout(t);
  }, [switchToast]);

  // Spring the watchlist banner in from the bottom whenever a new item is set
  useEffect(() => {
    if (!watchlistBannerItem) return;
    watchlistBannerAnim.setValue(200);
    Animated.spring(watchlistBannerAnim, {
      toValue: 0,
      useNativeDriver: true,
      bounciness: 14,
      speed: 12,
    }).start();
  }, [watchlistBannerItem, watchlistBannerAnim]);

  const dismissWatchlistBanner = useCallback((onDone?: () => void) => {
    Animated.timing(watchlistBannerAnim, {
      toValue: 200,
      duration: 220,
      useNativeDriver: true,
    }).start(() => {
      setWatchlistBannerItem(null);
      onDone?.();
    });
  }, [watchlistBannerAnim]);

  const seekBy = (deltaSec: number) => {
    const target = Math.max(0, currentTime + deltaSec);
    playerRef.current?.seekTo(target);
    setCurrentTime(target);
    keepControlsAwake();
  };

  const getResumePosition = useCallback(() => Math.max(
    0,
    Number.isFinite(playbackPosRef.current) ? playbackPosRef.current : 0,
    Number.isFinite(currentTime) ? currentTime : 0,
  ), [currentTime]);

  const switchToResolvedStream = useCallback(async (
    targetStream: AddonStream,
    resumeAt: number,
    options?: {
      sourceIdentityOverride?: string;
      failureToast?: string;
    },
  ) => {
    const nextUrl = await resolveSourceStreamUrl(targetStream);
    if (!nextUrl) {
      if (options?.failureToast) {
        setSwitchToast(options.failureToast);
      }
      return false;
    }

    sourceSwitchBackupRef.current = {
      url: resolvedStreamUrl ?? '',
      identity: activeSourceIdentity,
      resumeAt,
    };

    rememberedSourceSavedRef.current = false;
    setError(null);
    setLoading(true);
    setShowLoadingOverlay(true);
    setDuration(0);
    setCurrentTime(0);
    setDidSeekInitialResume(false);
    setEffectiveResumeFrom(resumeAt);
    setActiveSourceIdentityState(options?.sourceIdentityOverride ?? streamIdentityKey(targetStream));
    setResolvedStreamUrl(nextUrl);
    return true;
  }, [activeSourceIdentity, resolveSourceStreamUrl, resolvedStreamUrl]);

  const cycleResizeMode = () => {
    setResizeMode(current => {
      if (current === 'cover') return 'contain';
      if (current === 'contain') return 'stretch';
      return 'cover';
    });
    keepControlsAwake();
  };

  const closeMpvPlayer = () => {
    void persistRememberedSource();
    if (scrobbledStartRef.current) {
      const cur = playbackPosRef.current;
      const dur = playbackDurRef.current;
      const payload = payloadRef.current;
      void scrobble(
        'stop',
        buildPayload(payload.movieId, payload.type, payload.title, payload.year, dur > 0 ? Math.min(100, (cur / dur) * 100) : 0),
      );
      scrobbledStartRef.current = false;
    }
    skipPortraitOnBlurRef.current = false;
    navigation.goBack();
  };

  const handlePlaybackEnded = useCallback(() => {
    playbackCompletedRef.current = true;
    watchThresholdFiredRef.current = true;
    void handleWatchThreshold().finally(() => {
      closeMpvPlayer();
    });
  }, [closeMpvPlayer, handleWatchThreshold]);

  const switchSourceInPlayer = useCallback((sourceOption: MpvSourceOption) => {
    if (!sourceOption.identity) return;
    if (sourceOption.identity === activeSourceIdentity) {
      return;
    }

    if (resolvedSourceStreams.length > 0) {
      const targetStream = resolvedSourceStreams.find(stream => streamIdentityKey(stream) === sourceOption.identity) ?? null;
      if (targetStream) {
        void (async () => {
          const resumeAt = getResumePosition();
          await persistRememberedSource();
          await switchToResolvedStream(targetStream, resumeAt, {
            sourceIdentityOverride: sourceOption.identity,
            failureToast: "Couldn't resolve this source — try a different one.",
          });
        })();
        return;
      }
    }

    void persistRememberedSource();
    const fallbackResume = Number.isFinite(currentTime) ? currentTime : 0;
    const resumeAt = Math.max(0, fallbackResume);
    const nextParams =
      returnToPlayerParams && typeof returnToPlayerParams === 'object'
        ? {
            ...(returnToPlayerParams as Record<string, unknown>),
            resumeFrom: resumeAt,
            preferredSourceIndex: sourceOption.index,
            preferredSourceIdentity: sourceOption.identity,
            openSourcesOnStart: true,
            forceStartFromBeginning,
          }
        : {
            resumeFrom: resumeAt,
            preferredSourceIndex: sourceOption.index,
            preferredSourceIdentity: sourceOption.identity,
            openSourcesOnStart: true,
            forceStartFromBeginning,
          };

    skipPortraitOnBlurRef.current = true;
    navigation.replace('LegacyPlayer', nextParams);
  }, [
    activeSourceIdentity,
    currentTime,
    navigation,
    persistRememberedSource,
    resolvedSourceStreams,
    returnToPlayerParams,
    forceStartFromBeginning,
    switchToResolvedStream,
  ]);

  const controlsVisible = (showControls || !!error || loading) && !anyPopupOpen;
  const showPausedLogoOnly = paused && !loading && !error && !anyPopupOpen && !showControls;
  const decoderModeOptions: PlaybackDecoderMode[] = ['auto', 'hardware', 'hardware_plus', 'software'];
  const surfaceOptions: PlaybackRenderSurface[] = ['standard', 'compatibility'];
  const activeSourceOption = useMemo(
    () => sourceOptions.find(option => option.identity === activeSourceIdentity) ?? null,
    [activeSourceIdentity, sourceOptions],
  );
  const speedLabel = useMemo(
    () => `${playbackRate.toFixed(2).replace('.00', '')}x`,
    [playbackRate],
  );
  const selectedAudioLabel = useMemo(() => {
    if (selectedAudioTrackId == null) return 'Auto';
    const track = audioTracks.find(item => item.id === selectedAudioTrackId);
    return track ? getTrackLabel(track) : `Track ${selectedAudioTrackId}`;
  }, [audioTracks, selectedAudioTrackId]);
  const selectedSubtitleLabel = useMemo(() => {
    if (selectedSubtitleTrackId == null) return 'Off';
    const track = subtitleTracks.find(item => item.id === selectedSubtitleTrackId);
    return track ? getTrackLabel(track) : `Track ${selectedSubtitleTrackId}`;
  }, [selectedSubtitleTrackId, subtitleTracks]);
  const mediaInfoRows = useMemo(
    () => [
      { label: 'Source', value: activeSourceOption?.name ?? 'Unknown' },
      { label: 'Provider', value: activeSourceOption?.source ?? 'Unknown' },
      { label: 'Quality', value: activeSourceOption?.quality ?? 'Unknown' },
      { label: 'Playback Host', value: getUrlHost(resolvedStreamUrl) },
      { label: 'Speed', value: speedLabel },
      { label: 'Screen Mode', value: RESIZE_MODE_LABELS[resizeMode] },
      { label: 'Decoder', value: decoderMode },
      { label: 'Surface', value: renderSurface },
      { label: 'Smart Stream Selection', value: streamSelectionEnabled ? 'Enabled' : 'Disabled' },
      { label: 'Short Source Filter', value: shortSourceFilterEnabled ? 'Enabled' : 'Disabled' },
      { label: 'Audio Track', value: selectedAudioLabel },
      { label: 'Subtitle Track', value: selectedSubtitleLabel },
      { label: 'Position', value: `${formatTime(currentTime)} / ${formatTime(duration)}` },
    ],
    [
      activeSourceOption?.quality,
      activeSourceOption?.source,
      activeSourceOption?.name,
      currentTime,
      decoderMode,
      duration,
      playbackRate,
      renderSurface,
      resizeMode,
      shortSourceFilterEnabled,
      selectedAudioLabel,
      selectedSubtitleLabel,
      speedLabel,
      streamSelectionEnabled,
      resolvedStreamUrl,
    ],
  );

  const cyclePlaybackRate = useCallback(() => {
    setPlaybackRate(current => {
      if (current >= 1.5) return 1;
      if (current >= 1.25) return 1.5;
      return 1.25;
    });
    keepControlsAwake();
  }, [keepControlsAwake]);

  useEffect(() => {
    if (!isConnected || !resolvedStreamUrl) return;
    if (!scrobbledStartRef.current) {
      const payload = payloadRef.current;
      void scrobble('start', buildPayload(payload.movieId, payload.type, payload.title, payload.year, 0));
      scrobbledStartRef.current = true;
    }

    if (progressTimerRef.current) clearInterval(progressTimerRef.current);
    progressTimerRef.current = setInterval(() => {
      const cur = playbackPosRef.current;
      const dur = playbackDurRef.current;
      if (dur > 0) {
        const payload = payloadRef.current;
        void scrobble('pause', buildPayload(payload.movieId, payload.type, payload.title, payload.year, Math.min(100, (cur / dur) * 100)));
      }
    }, 60_000);

    return () => {
      if (progressTimerRef.current) {
        clearInterval(progressTimerRef.current);
        progressTimerRef.current = null;
      }
    };
  }, [isConnected, resolvedStreamUrl, scrobble]);

  useEffect(() => {
    return () => {
      if (progressTimerRef.current) {
        clearInterval(progressTimerRef.current);
        progressTimerRef.current = null;
      }
      if (scrobbledStartRef.current) {
        const cur = playbackPosRef.current;
        const dur = playbackDurRef.current;
        const payload = payloadRef.current;
        void scrobble(
          'stop',
          buildPayload(payload.movieId, payload.type, payload.title, payload.year, dur > 0 ? Math.min(100, (cur / dur) * 100) : 0),
        );
        scrobbledStartRef.current = false;
      }
    };
  }, [scrobble]);

  return (
    <View style={styles.root}>
      <StatusBar hidden />

      {canRenderNativeMpv ? (
        <MpvPlayer
          ref={playerRef}
          style={StyleSheet.absoluteFill}
          source={resolvedStreamUrl}
          headers={resolvedHeaders}
          paused={paused || !startupTrackSelectionReady}
          rate={playbackRate}
          volume={1}
          resizeMode={resizeMode}
          onLoad={handleLoad}
          onProgress={handleProgress}
          onEnd={handlePlaybackEnded}
          onError={handleError}
          onTracksChanged={handleTracksChanged}
        />
      ) : !mpvNativeViewAvailable ? (
        <View style={[StyleSheet.absoluteFill, styles.centered]}>
          <Text style={styles.errorTitle}>Embedded MPV Unavailable</Text>
          <Text style={styles.errorMessage}>This Android build does not include the MPV native view yet.</Text>
        </View>
      ) : (
        <View style={StyleSheet.absoluteFill} />
      )}

      <Pressable style={StyleSheet.absoluteFill} onPress={toggleControlsVisibility} />

      {showLoadingOverlay && !error && mpvNativeViewAvailable && (
        <Animated.View style={[styles.loadingOverlay, { opacity: loadingOverlayOpacity }]}>
          {!!loadingArtworkUri && <Image source={{ uri: loadingArtworkUri }} style={styles.loadingBackdropImage} />}
          <View style={styles.loadingOverlayDim} />
          <View style={styles.loadingContent}>
            <Animated.View style={[styles.loadingTitleWrap, { opacity: logoBreathAnim }]}>
              {titleLogoUri ? (
                <Image source={{ uri: titleLogoUri }} style={styles.logoImage} resizeMode="contain" />
              ) : (
                <Text style={styles.logoFallbackText} numberOfLines={2}>{title ?? 'Playback'}</Text>
              )}
            </Animated.View>
            <View style={styles.loadingSpinnerBlock}>
              <Animated.Text style={[styles.loadingText, { opacity: loadingTextOpacity }]}>{loadingMessage}</Animated.Text>
            </View>
          </View>
        </Animated.View>
      )}

      {!!error && (
        <View style={[StyleSheet.absoluteFill, styles.centered]}>
          <Text style={styles.errorTitle}>Player Error</Text>
          <Text style={styles.errorMessage}>{error}</Text>
        </View>
      )}

      {/* Watchlist removal prompt — shown after 96 % completion, slides up with spring */}
      {!!watchlistBannerItem && (
        <Animated.View
          style={[styles.watchlistBannerWrap, { transform: [{ translateY: watchlistBannerAnim }] }]}
          pointerEvents="box-none"
        >
          <View style={[styles.switchToast, styles.watchlistBanner]}>
            <Ionicons name="bookmark" size={15} color="#a78bfa" />
            <Text style={styles.switchToastText}>Still in your watchlist — remove it?</Text>
            <TouchableOpacity
              style={styles.watchlistBannerRemoveBtn}
              onPress={() => {
                const item = watchlistBannerItem;
                dismissWatchlistBanner(() => removeFromWatchlist(item));
              }}
              activeOpacity={0.8}
            >
              <Text style={styles.watchlistBannerRemoveText}>Remove</Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={() => dismissWatchlistBanner()}
              hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
            >
              <Ionicons name="close" size={16} color="rgba(255,255,255,0.6)" />
            </TouchableOpacity>
          </View>
        </Animated.View>
      )}

      {/* Source-switch failure toast */}
      {!!switchToast && (
        <View style={styles.switchToastWrap} pointerEvents="box-none">
          <View style={styles.switchToast}>
            <Text style={styles.switchToastText}>{switchToast}</Text>
            <TouchableOpacity onPress={() => setSwitchToast(null)} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
              <Ionicons name="close" size={16} color="rgba(255,255,255,0.7)" />
            </TouchableOpacity>
          </View>
        </View>
      )}

      {showPausedLogoOnly && (
        <View style={styles.pausedLogoWrap} pointerEvents="none">
          <LinearGradient
            colors={['transparent', 'rgba(0,0,0,0.72)']}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
            style={StyleSheet.absoluteFillObject}
          />
          {titleLogoUri ? (
            <Image source={{ uri: titleLogoUri }} style={styles.logoImage} resizeMode="contain" />
          ) : (
            <Text style={styles.logoFallbackText} numberOfLines={2}>
              {title ?? 'Playback'}
            </Text>
          )}
          {!!resolvedSynopsis && (
            <Text style={styles.pausedSynopsisText} numberOfLines={3}>
              {resolvedSynopsis}
            </Text>
          )}
        </View>
      )}

      {controlsVisible && !loading && (
        <Animated.View
          pointerEvents="box-none"
          style={[styles.topOverlay, { paddingTop: insets.top + 6, opacity: controlsOpacity }]}
        >
          <View style={styles.topActions}>
            <TouchableOpacity style={styles.topIconBtn} onPress={closeMpvPlayer} activeOpacity={0.85}>
              <Ionicons name="close" size={24} color="#fff" />
            </TouchableOpacity>
          </View>
        </Animated.View>
      )}

      {controlsVisible && !loading && (
        <Animated.View pointerEvents="box-none" style={[StyleSheet.absoluteFillObject, { opacity: controlsOpacity }]}>
          <View pointerEvents="none" style={styles.controlsShade} />

          <View style={styles.centerControls}>
            <TouchableOpacity style={styles.seekVisualBtn} onPress={() => seekBy(-10)} activeOpacity={0.85}>
              <View style={styles.seekNumericWrap}>
                <Ionicons name="refresh-outline" size={58} color="rgba(255,255,255,0.64)"
                  style={{ transform: [{ scaleX: -1 }] }} />
                <Text style={styles.seekNumericText}>10</Text>
              </View>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.heroPlayBtn}
              onPress={() => {
                setPaused(value => !value);
                keepControlsAwake();
              }}
              activeOpacity={0.85}
            >
              <Ionicons name={paused ? 'play' : 'pause'} size={38} color="rgba(255,255,255,0.70)" />
            </TouchableOpacity>
            <TouchableOpacity style={styles.seekVisualBtn} onPress={() => seekBy(10)} activeOpacity={0.85}>
              <View style={styles.seekNumericWrap}>
                <Ionicons name="refresh-outline" size={58} color="rgba(255,255,255,0.64)" />
                <Text style={styles.seekNumericText}>10</Text>
              </View>
            </TouchableOpacity>
          </View>

          <View style={[styles.timelineBlock, { bottom: insets.bottom + 92 }]}>
            <View
              style={styles.sliderWrap}
              onLayout={e => {
                progressBarWidthRef.current = e.nativeEvent.layout.width;
                setProgressBarWidth(e.nativeEvent.layout.width);
              }}
            >
              {/* Custom visual track */}
              <View style={styles.progressTrackBg} pointerEvents="none">
                <View style={styles.progressTrackRail} />
                <View style={[styles.progressTrackFill, {
                  width: progressBarWidth * Math.min(currentTime / Math.max(duration, 1), 1),
                }]} />
                <View style={[styles.progressThumbDot, {
                  left: Math.max(0, progressBarWidth * Math.min(currentTime / Math.max(duration, 1), 1) - 7.5),
                }]} />
              </View>
              {/* Native slider — transparent, handles all touch */}
              <Slider
                style={[styles.slider, StyleSheet.absoluteFillObject]}
                minimumValue={0}
                maximumValue={Math.max(duration, 1)}
                value={Math.min(currentTime, Math.max(duration, 1))}
                onSlidingStart={() => {
                  setIsSeeking(true);
                  keepControlsAwake();
                }}
                onValueChange={value => {
                  setCurrentTime(value);
                  keepControlsAwake();
                }}
                onSlidingComplete={value => {
                  playerRef.current?.seekTo(value);
                  setCurrentTime(value);
                  setIsSeeking(false);
                  keepControlsAwake();
                }}
                minimumTrackTintColor="transparent"
                maximumTrackTintColor="transparent"
                thumbTintColor="transparent"
              />
            </View>
            <View style={styles.timePillRow}>
              <View style={styles.timePill}>
                <Text style={styles.timePillText}>{formatTime(currentTime)}</Text>
              </View>
              <View style={styles.timePill}>
                <Text style={styles.timePillText}>{formatTime(duration)}</Text>
              </View>
            </View>
          </View>

          <View style={[styles.floatingDock, { bottom: insets.bottom + 16 }]}>
            <TouchableOpacity
              style={styles.dockBtn}
              onPress={() => {
                keepControlsAwake();
                cycleResizeMode();
              }}
              activeOpacity={0.85}
            >
              <Ionicons name={RESIZE_MODE_ICONS[resizeMode]} size={21} color="#fff" />
              <Text style={styles.dockRateText}>{RESIZE_MODE_LABELS[resizeMode]}</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.dockBtn}
              onPress={cyclePlaybackRate}
              activeOpacity={0.85}
            >
              <Ionicons name="speedometer-outline" size={21} color="#fff" />
              <Text style={styles.dockRateText}>{speedLabel}</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.dockBtn}
              onPress={() => {
                clearControlsTimer();
                setShowControls(false);
                setShowTrackPicker(true);
              }}
              activeOpacity={0.85}
            >
              <View style={styles.ccIconWrap}>
                <Text style={styles.ccIconText}>CC</Text>
              </View>
              {/* Show a dot while OS subtitle search is in progress */}
              {subtitle.searchState === 'loading' && (
                <View style={styles.dockBadgeDot} />
              )}
              {/* Show active indicator when an OS subtitle is loaded */}
              {subtitle.activeExternalSubId !== null && (
                <View style={[styles.dockBadgeDot, styles.dockBadgeDotActive]} />
              )}
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.dockBtn}
              onPress={() => {
                clearControlsTimer();
                setShowControls(false);
                setShowAudioModal(true);
              }}
              activeOpacity={0.85}
            >
              <Ionicons name="musical-notes-outline" size={21} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.dockBtn}
              onPress={() => {
                clearControlsTimer();
                setShowControls(false);
                setShowInfoModal(true);
              }}
              activeOpacity={0.85}
            >
              <Ionicons name="information-circle-outline" size={22} color="#fff" />
            </TouchableOpacity>
          </View>
        </Animated.View>
      )}

      <Modal visible={showAudioModal} transparent animationType="fade" onRequestClose={() => setShowAudioModal(false)}>
        <View style={styles.modalRoot}>
          <Pressable style={styles.modalBackdrop} onPress={() => setShowAudioModal(false)} />
          <View style={styles.modalCard}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Audio</Text>
              <TouchableOpacity onPress={() => setShowAudioModal(false)}>
                <Ionicons name="close" size={22} color="rgba(255,255,255,0.8)" />
              </TouchableOpacity>
            </View>
            <ScrollView style={styles.modalList} showsVerticalScrollIndicator={false}>
              {audioTracks.length === 0 ? (
                <Text style={styles.subTabEmptyText}>No audio tracks detected</Text>
              ) : (
                audioTracks.map(track => {
                  const selected = selectedAudioTrackId === track.id;
                  return (
                    <TouchableOpacity
                      key={`audio-${track.id}`}
                      style={[styles.subTrackItem, selected && styles.subTrackItemSelected]}
                      onPress={() => {
                        playerRef.current?.setAudioTrack(track.id);
                        setSelectedAudioTrackId(track.id);
                        setShowAudioModal(false);
                      }}
                    >
                      <Text style={[styles.subTrackItemText, selected && styles.subTrackItemSelectedText]}>
                        {getTrackLabel(track)}
                      </Text>
                      {selected && <Ionicons name="checkmark" size={18} color="#1a1a1a" />}
                    </TouchableOpacity>
                  );
                })
              )}
            </ScrollView>
          </View>
        </View>
      </Modal>

      <Modal visible={showTrackPicker} transparent animationType="fade" onRequestClose={() => setShowTrackPicker(false)}>
        <View style={styles.modalRoot}>
          <Pressable style={styles.modalBackdrop} onPress={() => setShowTrackPicker(false)} />
          <View style={styles.modalCard}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Subtitles</Text>
              <TouchableOpacity onPress={() => setShowTrackPicker(false)}>
                <Ionicons name="close" size={22} color="rgba(255,255,255,0.8)" />
              </TouchableOpacity>
            </View>

            {/* ── Subtitle tab bar ──────────────────────────────────── */}
            <View style={styles.subTabRow}>
              {(['builtin', 'addons', 'style'] as const).map(tab => (
                <TouchableOpacity
                  key={tab}
                  style={[styles.subTabBtn, subTab === tab && styles.subTabBtnActive]}
                  onPress={() => setSubTab(tab)}
                  activeOpacity={0.8}
                >
                  <Text style={[styles.subTabText, subTab === tab && styles.subTabTextActive]}>
                    {tab === 'builtin' ? 'Built-in' : tab === 'addons' ? 'Addons' : 'Style'}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            {/* ── Tab content ──────────────────────────────────────── */}
            <ScrollView style={styles.modalList} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

              {/* ── Built-in tab ──────────────────────────────────── */}
              {subTab === 'builtin' && (
                <>
                  {/* None/Off row — pink highlight when active */}
                  <TouchableOpacity
                    style={[
                      styles.subTrackItem,
                      selectedSubtitleTrackId == null && subtitle.activeExternalSubId == null
                        ? styles.subTrackItemNoneActive
                        : styles.subTrackItemNone,
                    ]}
                    onPress={() => {
                      // Lock out both embedded and external auto-select
                      userDisabledSubtitlesRef.current = true;
                      didAutoSelectEmbeddedRef.current = true;
                      playerRef.current?.setSubtitleTrack(null);
                      setSelectedSubtitleTrackId(null);
                      subtitle.setActiveExternalSubId(null);
                      setShowTrackPicker(false);
                    }}
                  >
                    <Text style={[
                      styles.subTrackItemText,
                      selectedSubtitleTrackId == null && subtitle.activeExternalSubId == null
                        ? styles.subTrackItemNoneActiveText
                        : null,
                    ]}>
                      None
                    </Text>
                  </TouchableOpacity>

                  {/* Embedded tracks */}
                  {subtitleTracks.map(track => {
                    const selected = selectedSubtitleTrackId === track.id;
                    return (
                      <TouchableOpacity
                        key={`sub-${track.id}`}
                        style={[styles.subTrackItem, selected && styles.subTrackItemSelected]}
                        onPress={() => {
                          playerRef.current?.setSubtitleTrack(track.id);
                          setSelectedSubtitleTrackId(track.id);
                          subtitle.setActiveExternalSubId(null);
                          setShowTrackPicker(false);
                        }}
                      >
                        <Text style={[styles.subTrackItemText, selected && styles.subTrackItemSelectedText]}>
                          {getTrackLabel(track)}
                        </Text>
                        {selected && <Ionicons name="checkmark" size={18} color="#1a1a1a" />}
                      </TouchableOpacity>
                    );
                  })}

                  {subtitleTracks.length === 0 && (
                    <Text style={styles.subTabEmptyText}>No embedded subtitle tracks found</Text>
                  )}
                </>
              )}

              {/* ── Addons tab ──────────────────────────────────────── */}
              {subTab === 'addons' && (
                <>
                  {subDownloadError && (
                    <View style={[styles.subTabStatusRow, { backgroundColor: '#c0392b18', borderRadius: 8, marginBottom: 8 }]}>
                      <Ionicons name="warning-outline" size={15} color="#e74c3c" />
                      <Text style={[styles.subTabStatusText, { color: '#e74c3c' }]}>Download failed — check your connection and try again.</Text>
                    </View>
                  )}
                  {subtitle.searchState === 'loading' && (
                    <View style={styles.subTabStatusRow}>
                      <ActivityIndicator size="small" color="#8b5cf6" />
                      <Text style={styles.subTabStatusText}>Searching OpenSubtitles…</Text>
                    </View>
                  )}
                  {subtitle.searchState === 'error' && (
                    <Text style={styles.subTabEmptyText}>Search failed. Check your connection or addon URL in Settings.</Text>
                  )}
                  {subtitle.searchState === 'done' && subtitle.results.length === 0 && (
                    <Text style={styles.subTabEmptyText}>No results found</Text>
                  )}

                  {subtitle.results.map((result, idx) => {
                    const isActive = subtitle.activeExternalSubId === result.id;
                    const isDownloading = subtitle.downloadingSubId === result.id;

                    const flags = [
                      result.isHI ? 'HI' : null,
                      result.isForced ? 'Forced' : null,
                    ].filter(Boolean).join(' · ');
                    const primaryLabel = flags
                      ? `${result.langDisplay} · ${flags}`
                      : result.langDisplay;

                    const m = result.releaseName ?? '';
                    let secondaryLabel: string;
                    if (m.length >= 8) {
                      secondaryLabel = m;
                    } else if (m === 'h') {
                      secondaryLabel = 'Hash match — perfect timing';
                    } else if (m === 'f') {
                      secondaryLabel = 'Filename match — good timing';
                    } else if (m === 'i') {
                      secondaryLabel = 'IMDB match — timing may vary';
                    } else {
                      secondaryLabel = `Result ${idx + 1} of ${subtitle.results.length}`;
                    }

                    return (
                      <TouchableOpacity
                        key={`os-${result.id}`}
                        style={[styles.subAddonItem, isActive && styles.subAddonItemActive]}
                        onPress={() => { void handleSelectOsSubtitle(result); }}
                        disabled={isDownloading}
                        activeOpacity={0.85}
                      >
                        <View style={{ flex: 1 }}>
                          <Text style={[styles.subAddonPrimary, isActive && styles.subAddonPrimaryActive]}>
                            {primaryLabel}
                          </Text>
                          <Text style={styles.subAddonSecondary} numberOfLines={1}>
                            {secondaryLabel}
                          </Text>
                        </View>
                        {isDownloading
                          ? <ActivityIndicator size="small" color="#8b5cf6" />
                          : isActive
                            ? <Ionicons name="checkmark" size={18} color="#8b5cf6" />
                            : null}
                      </TouchableOpacity>
                    );
                  })}
                </>
              )}

              {/* ── Style tab ────────────────────────────────────────── */}
              {subTab === 'style' && (
                <>
                  {/* Preview */}
                  <View style={styles.subStylePreviewCard}>
                    <View style={styles.subStylePreviewInner}>
                      <Text style={[
                        styles.subStylePreviewText,
                        { fontSize: Math.round(subFontSize * 0.36), color: subColor.length === 9 ? subColor.slice(0, 7) : subColor },
                      ]}>
                        {'The quick brown fox\njumps over the lazy dog.'}
                      </Text>
                    </View>
                  </View>

                  {/* Font size */}
                  <View style={styles.subStyleSection}>
                    <View style={styles.subStyleSectionHeader}>
                      <Ionicons name="text-outline" size={15} color="rgba(255,255,255,0.55)" />
                      <Text style={styles.subStyleSectionTitle}>Font Size</Text>
                    </View>
                    <View style={styles.subStyleRow}>
                      <Text style={styles.subStyleLabel}>Font Size</Text>
                      <View style={styles.subStyleStepper}>
                        <TouchableOpacity
                          style={styles.subStyleStepBtn}
                          onPress={() => {
                            const next = Math.max(20, subFontSize - 5);
                            setSubFontSize(next);
                            playerRef.current?.setSubtitleFontSize(next);
                          }}
                          activeOpacity={0.7}
                        >
                          <Text style={styles.subStyleStepText}>−</Text>
                        </TouchableOpacity>
                        <Text style={styles.subStyleStepValue}>{subFontSize}</Text>
                        <TouchableOpacity
                          style={styles.subStyleStepBtn}
                          onPress={() => {
                            const next = Math.min(120, subFontSize + 5);
                            setSubFontSize(next);
                            playerRef.current?.setSubtitleFontSize(next);
                          }}
                          activeOpacity={0.7}
                        >
                          <Text style={styles.subStyleStepText}>+</Text>
                        </TouchableOpacity>
                      </View>
                    </View>
                  </View>

                  {/* Position */}
                  <View style={styles.subStyleSection}>
                    <View style={styles.subStyleSectionHeader}>
                      <Ionicons name="options-outline" size={15} color="rgba(255,255,255,0.55)" />
                      <Text style={styles.subStyleSectionTitle}>Position</Text>
                    </View>
                    <View style={styles.subPosRow}>
                      {([{ label: 'Bottom', value: 90 }, { label: 'Centre', value: 50 }, { label: 'Top', value: 10 }] as { label: string; value: number }[]).map(opt => (
                        <TouchableOpacity
                          key={opt.label}
                          style={[styles.subPosBtn, subPos === opt.value && styles.subPosBtnActive]}
                          onPress={() => {
                            setSubPos(opt.value);
                            playerRef.current?.setSubtitlePosition(opt.value);
                          }}
                          activeOpacity={0.8}
                        >
                          <Text style={[styles.subPosBtnText, subPos === opt.value && styles.subPosBtnTextActive]}>
                            {opt.label}
                          </Text>
                        </TouchableOpacity>
                      ))}
                    </View>
                  </View>

                  {/* Text color */}
                  <View style={styles.subStyleSection}>
                    <View style={styles.subStyleSectionHeader}>
                      <Ionicons name="color-palette-outline" size={15} color="rgba(255,255,255,0.55)" />
                      <Text style={styles.subStyleSectionTitle}>Text Color</Text>
                    </View>
                    <View style={styles.subColorRow}>
                      {([
                        { label: 'White',  hex: '#FFFFFFFF', display: '#FFFFFF' },
                        { label: 'Yellow', hex: '#FFFF00FF', display: '#FFFF00' },
                        { label: 'Cyan',   hex: '#00FFFFFF', display: '#00FFFF' },
                        { label: 'Red',    hex: '#FF3333FF', display: '#FF3333' },
                        { label: 'Green',  hex: '#00E676FF', display: '#00E676' },
                        { label: 'Purple', hex: '#8B5CF6FF', display: '#8B5CF6' },
                        { label: 'Orange', hex: '#FF8C00FF', display: '#FF8C00' },
                      ] as { label: string; hex: string; display: string }[]).map(c => (
                        <TouchableOpacity
                          key={c.label}
                          style={[
                            styles.subColorSwatch,
                            { backgroundColor: c.display },
                            subColor === c.hex && styles.subColorSwatchActive,
                          ]}
                          onPress={() => {
                            setSubColor(c.hex);
                            playerRef.current?.setSubtitleColor(c.hex);
                          }}
                          activeOpacity={0.8}
                        />
                      ))}
                    </View>
                  </View>

                  {/* Subtitle delay (only when a subtitle is active) */}
                  {(subtitleTracks.length > 0 || subtitle.activeExternalSubId !== null) && (
                    <View style={styles.subStyleSection}>
                      <View style={styles.subStyleSectionHeader}>
                        <Ionicons name="time-outline" size={15} color="rgba(255,255,255,0.55)" />
                        <Text style={styles.subStyleSectionTitle}>Delay</Text>
                      </View>
                      <View style={styles.delayRow}>
                        {([-0.5, -0.1] as const).map(delta => (
                          <TouchableOpacity
                            key={`delay${delta}`}
                            style={styles.delayBtn}
                            onPress={() => subtitle.setDelay(subtitle.delay + delta)}
                            activeOpacity={0.8}
                          >
                            <Text style={styles.delayBtnText}>{delta}s</Text>
                          </TouchableOpacity>
                        ))}
                        <View style={styles.delayDisplay}>
                          <Text style={styles.delayDisplayText}>
                            {subtitle.delay >= 0 ? '+' : ''}{subtitle.delay.toFixed(1)}s
                          </Text>
                        </View>
                        {([0.1, 0.5] as const).map(delta => (
                          <TouchableOpacity
                            key={`delay+${delta}`}
                            style={styles.delayBtn}
                            onPress={() => subtitle.setDelay(subtitle.delay + delta)}
                            activeOpacity={0.8}
                          >
                            <Text style={styles.delayBtnText}>+{delta}s</Text>
                          </TouchableOpacity>
                        ))}
                        <TouchableOpacity
                          style={[styles.delayBtn, styles.delayBtnReset]}
                          onPress={() => subtitle.setDelay(0)}
                          activeOpacity={0.8}
                        >
                          <Text style={styles.delayBtnText}>Reset</Text>
                        </TouchableOpacity>
                      </View>
                    </View>
                  )}
                </>
              )}
            </ScrollView>
          </View>
        </View>
      </Modal>

      <Modal visible={showInfoModal} transparent animationType="fade" onRequestClose={() => setShowInfoModal(false)}>
        <View style={styles.modalRoot}>
          <Pressable style={styles.modalBackdrop} onPress={() => setShowInfoModal(false)} />
          <View style={styles.modalCard}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Media Info</Text>
              <TouchableOpacity onPress={() => setShowInfoModal(false)}>
                <Ionicons name="close" size={22} color="rgba(255,255,255,0.8)" />
              </TouchableOpacity>
            </View>
            <ScrollView style={styles.modalList} showsVerticalScrollIndicator={false}>
              {mediaInfoRows.map(row => (
                <View key={row.label} style={styles.infoRow}>
                  <Text style={styles.infoLabel}>{row.label}</Text>
                  <Text style={styles.infoValue}>{row.value}</Text>
                </View>
              ))}
            </ScrollView>
          </View>
        </View>
      </Modal>
      <ConfirmSheet
        visible={showGuestAccountPrompt}
        onClose={() => setShowGuestAccountPrompt(false)}
        icon="person-add-outline"
        title="Create an account"
        message="Sync with TV, save your add-ons, and connect Trakt without interrupting guest playback."
        confirmLabel="Create Account"
        cancelLabel="Not Now"
        onConfirm={() => {
          setShowGuestAccountPrompt(false);
          navigation.navigate('Auth');
        }}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000',
  },
  centered: {
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 28,
  },
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#000',
  },
  loadingBackdropImage: {
    ...StyleSheet.absoluteFillObject,
  },
  loadingOverlayDim: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.34)',
  },
  loadingContent: {
    alignItems: 'center',
    paddingHorizontal: 24,
    gap: 28,
  },
  loadingTitleWrap: {
    alignItems: 'center',
  },
  loadingSpinnerBlock: {
    alignItems: 'center',
  },
  loadingText: {
    color: 'rgba(255,255,255,0.85)',
    marginTop: 10,
    fontSize: 13,
    fontWeight: '600',
  },
  errorTitle: {
    color: '#fff',
    fontSize: 20,
    fontWeight: '800',
    textAlign: 'center',
    marginBottom: 8,
  },
  errorMessage: {
    color: 'rgba(255,255,255,0.72)',
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
  },
  controlsShade: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.16)',
  },
  topOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
  },
  topMeta: {
    flex: 1,
    paddingRight: 16,
  },
  topTitle: {
    color: '#fff',
    fontSize: 22,
    fontWeight: '800',
  },
  topSubline: {
    color: 'rgba(255,255,255,0.86)',
    fontSize: 12,
    marginTop: 8,
    fontStyle: 'italic',
  },
  topSublineMuted: {
    color: 'rgba(255,255,255,0.86)',
    fontSize: 11,
    marginTop: 2,
    fontStyle: 'italic',
  },
  topEngineText: {
    color: 'rgba(255,255,255,0.68)',
    fontSize: 10,
    marginTop: 8,
    fontStyle: 'italic',
  },
  topActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
  },
  topIconBtn: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
  },
  centerControls: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: '45%',
    transform: [{ translateY: -38 }],
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 26,
  },
  seekVisualBtn: {
    width: 86,
    height: 86,
    alignItems: 'center',
    justifyContent: 'center',
    opacity: 0.5,
  },
  seekNumericWrap: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  seekNumericText: {
    position: 'absolute',
    color: 'rgba(255,255,255,0.64)',
    fontSize: 14,
    fontWeight: '800',
    letterSpacing: -0.5,
    marginTop: 4,
  },
  heroPlayBtn: {
    width: 90,
    height: 90,
    borderRadius: 45,
    alignItems: 'center',
    justifyContent: 'center',
    opacity: 0.5,
  },
  pausedOverlay: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 16,
  },
  pausedPlayCenter: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
  },
  pausedLogoWrap: {
    position: 'absolute',
    right: 0,
    top: 0,
    bottom: 0,
    width: 340,
    alignItems: 'flex-end',
    justifyContent: 'center',
    paddingRight: 24,
    overflow: 'hidden',
  },
  pausedSynopsisText: {
    maxWidth: 300,
    color: 'rgba(255,255,255,0.80)',
    fontSize: 13,
    fontWeight: '400',
    textAlign: 'right',
    lineHeight: 19,
    marginTop: 10,
    textShadowColor: 'rgba(0,0,0,0.55)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 4,
  },
  pausedPlayBtn: {
    width: 82,
    height: 82,
    borderRadius: 41,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.14)',
  },
  logoImage: {
    width: 240,
    height: 96,
    borderRadius: 8,
  },
  logoFallbackText: {
    maxWidth: 300,
    color: '#fff',
    fontSize: 30,
    fontWeight: '800',
    textAlign: 'right',
    fontStyle: 'italic',
    letterSpacing: 0.4,
    textShadowColor: 'rgba(0,0,0,0.65)',
    textShadowOffset: { width: 0, height: 2 },
    textShadowRadius: 6,
  },
  timelineBlock: {
    position: 'absolute',
    left: 0,
    right: 0,
    paddingHorizontal: 18,
  },
  sliderWrap: {
    height: 40,
    justifyContent: 'center',
    paddingHorizontal: 0,
  },
  slider: {
    width: '100%',
    height: 40,
  },
  progressTrackBg: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    justifyContent: 'center',
  },
  progressTrackRail: {
    height: 5,
    backgroundColor: 'rgba(255,255,255,0.22)',
    borderRadius: 3,
  },
  progressTrackFill: {
    position: 'absolute',
    left: 0,
    top: 17.5,
    height: 5,
    backgroundColor: '#3ea6ff',
    borderRadius: 3,
  },
  progressThumbDot: {
    position: 'absolute',
    top: 12.5,
    width: 15,
    height: 15,
    borderRadius: 7.5,
    backgroundColor: '#fff',
  },
  timePillRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 2,
    paddingHorizontal: 2,
  },
  timePill: {
    minWidth: 74,
    borderRadius: 18,
    backgroundColor: 'rgba(0,0,0,0.42)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
    paddingHorizontal: 12,
    paddingVertical: 5,
    alignItems: 'center',
  },
  timePillText: {
    color: '#fff',
    fontSize: 11,
    fontFamily: 'monospace',
  },
  floatingDock: {
    position: 'absolute',
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderRadius: 30,
    paddingHorizontal: 10,
    paddingVertical: 8,
    backgroundColor: 'rgba(0,0,0,0.56)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.24)',
  },
  dockBtn: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dockRateText: {
    position: 'absolute',
    bottom: -4,
    color: '#fff',
    fontSize: 10,
    fontWeight: '700',
    backgroundColor: 'rgba(62,166,255,0.92)',
    borderRadius: 8,
    paddingHorizontal: 5,
    paddingVertical: 1,
    overflow: 'hidden',
  },
  modalRoot: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 20,
  },
  modalBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.64)',
  },
  modalCard: {
    width: '100%',
    maxWidth: 560,
    maxHeight: '70%',
    borderRadius: 18,
    backgroundColor: 'rgba(18,18,20,0.92)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.15)',
    overflow: 'hidden',
  },
  modalHeader: {
    height: 52,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.12)',
  },
  modalTitle: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '800',
  },
  modalList: {
    maxHeight: 340,
  },
  sourceItem: {
    paddingHorizontal: 14,
    paddingVertical: 10,
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    alignSelf: 'stretch',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.08)',
    gap: 10,
  },
  sourceItemActive: {
    backgroundColor: 'rgba(139,92,246,0.15)',
  },
  sourceItemContent: {
    flex: 1,
    gap: 3,
  },
  sourceItemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  sourceItemTitle: {
    color: '#fff',
    fontSize: 13,
    fontWeight: '600',
    flex: 1,
  },
  sourceQualityBadge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 5,
    backgroundColor: 'rgba(139,92,246,0.35)',
    borderWidth: 1,
    borderColor: 'rgba(139,92,246,0.6)',
  },
  sourceQualityText: {
    color: '#d4b8ff',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  sourceActiveBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 999,
    backgroundColor: 'rgba(139,92,246,0.22)',
    borderWidth: 1,
    borderColor: 'rgba(139,92,246,0.45)',
  },
  sourceActiveBadgeText: {
    color: '#e9ddff',
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 0.3,
    textTransform: 'uppercase',
  },
  sourceItemMeta: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: 11,
    fontWeight: '500',
  },
  sourceEmptyWrap: {
    paddingHorizontal: 16,
    paddingTop: 18,
    paddingBottom: 16,
    gap: 14,
  },
  sourceEmptyText: {
    color: 'rgba(255,255,255,0.78)',
    fontSize: 13,
    lineHeight: 19,
  },
  sourcePromptBody: {
    paddingHorizontal: 14,
    paddingTop: 18,
    paddingBottom: 16,
    gap: 16,
  },
  sourcePromptText: {
    color: 'rgba(255,255,255,0.78)',
    fontSize: 14,
    lineHeight: 20,
  },
  sourcePromptActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 10,
  },
  sourcePromptSecondary: {
    minWidth: 110,
    height: 38,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.06)',
    paddingHorizontal: 12,
  },
  sourcePromptSecondaryText: {
    color: 'rgba(255,255,255,0.9)',
    fontSize: 13,
    fontWeight: '700',
  },
  sourcePromptPrimary: {
    minWidth: 142,
    height: 38,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#8b5cf6',
    paddingHorizontal: 14,
  },
  sourcePromptPrimaryText: {
    color: '#fff',
    fontSize: 13,
    fontWeight: '800',
  },
  modalSectionTitle: {
    color: 'rgba(255,255,255,0.72)',
    fontSize: 12,
    fontWeight: '800',
    paddingHorizontal: 14,
    paddingTop: 14,
    paddingBottom: 6,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  optionGrid: {
    paddingHorizontal: 14,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    paddingBottom: 12,
  },
  optionChip: {
    minHeight: 34,
    borderRadius: 18,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
    backgroundColor: 'rgba(255,255,255,0.06)',
  },
  optionChipSelected: {
    borderColor: '#8b5cf6',
    backgroundColor: 'rgba(139,92,246,0.2)',
  },
  optionChipText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'capitalize',
  },
  optionChipTextSelected: {
    color: '#e9ddff',
    fontWeight: '700',
  },
  modalItem: {
    height: 48,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.06)',
  },
  modalItemSelected: {
    backgroundColor: 'rgba(139,92,246,0.25)',
  },
  modalItemText: {
    color: '#fff',
    flex: 1,
    marginRight: 12,
    fontSize: 13,
    fontWeight: '600',
  },
  infoRow: {
    paddingHorizontal: 14,
    paddingVertical: 11,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.07)',
  },
  infoLabel: {
    color: 'rgba(255,255,255,0.68)',
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 4,
  },
  infoValue: {
    color: '#fff',
    fontSize: 13,
    lineHeight: 18,
  },

  // ── Subtitle-specific styles ─────────────────────────────────────────────

  /** CC badge on the dock subtitle button */
  ccIconWrap: {
    width: 30,
    height: 18,
    borderRadius: 4,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.85)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  ccIconText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
  },

  /** Tab bar row */
  subTabRow: {
    flexDirection: 'row',
    marginHorizontal: 14,
    marginTop: 10,
    marginBottom: 6,
    backgroundColor: 'rgba(255,255,255,0.07)',
    borderRadius: 20,
    padding: 3,
    gap: 2,
  },
  subTabBtn: {
    flex: 1,
    paddingVertical: 7,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
  },
  subTabBtnActive: {
    backgroundColor: '#fff',
  },
  subTabText: {
    fontSize: 13,
    fontWeight: '600',
    color: 'rgba(255,255,255,0.6)',
  },
  subTabTextActive: {
    color: '#1a1a1a',
    fontWeight: '700',
  },

  /** Built-in tab — None row (no subtitle active) */
  subTrackItem: {
    marginHorizontal: 14,
    marginVertical: 4,
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderRadius: 12,
    backgroundColor: 'rgba(255,255,255,0.07)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  subTrackItemNone: {
    backgroundColor: 'rgba(255,255,255,0.07)',
  },
  subTrackItemNoneActive: {
    backgroundColor: 'rgba(230,100,100,0.25)',
    borderWidth: 1,
    borderColor: 'rgba(220,80,80,0.5)',
  },
  subTrackItemSelected: {
    backgroundColor: '#fff',
  },
  subTrackItemText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  subTrackItemNoneActiveText: {
    color: '#f87171',
    fontWeight: '700',
  },
  subTrackItemSelectedText: {
    color: '#1a1a1a',
    fontWeight: '700',
  },

  /** Addons tab items */
  subAddonItem: {
    marginHorizontal: 14,
    marginVertical: 4,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 12,
    backgroundColor: 'rgba(255,255,255,0.07)',
    flexDirection: 'row',
    alignItems: 'center',
  },
  subAddonItemActive: {
    backgroundColor: 'rgba(139,92,246,0.2)',
    borderWidth: 1,
    borderColor: 'rgba(139,92,246,0.5)',
  },
  subAddonPrimary: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 2,
  },
  subAddonPrimaryActive: {
    color: '#c4b5fd',
  },
  subAddonSecondary: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: 12,
  },

  /** Empty/status messages */
  subTabEmptyText: {
    color: 'rgba(255,255,255,0.4)',
    fontSize: 13,
    textAlign: 'center',
    marginTop: 24,
    marginHorizontal: 24,
    lineHeight: 20,
  },
  subTabStatusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    marginTop: 24,
  },
  subTabStatusText: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: 13,
  },

  /** Style tab — preview card */
  subStylePreviewCard: {
    marginHorizontal: 14,
    marginTop: 10,
    marginBottom: 6,
    borderRadius: 12,
    backgroundColor: 'rgba(255,255,255,0.07)',
    overflow: 'hidden',
  },
  subStylePreviewInner: {
    minHeight: 90,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
  },
  subStylePreviewText: {
    fontWeight: '700',
    textAlign: 'center',
  },

  /** Style tab — section block */
  subStyleSection: {
    marginHorizontal: 14,
    marginTop: 8,
    borderRadius: 12,
    backgroundColor: 'rgba(255,255,255,0.07)',
    overflow: 'hidden',
    paddingBottom: 12,
  },
  subStyleSectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 14,
    paddingTop: 12,
    paddingBottom: 8,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.07)',
  },
  subStyleSectionTitle: {
    color: 'rgba(255,255,255,0.55)',
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  subStyleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    paddingTop: 10,
  },
  subStyleLabel: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },

  /** Stepper for font size */
  subStyleStepper: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 0,
  },
  subStyleStepBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255,255,255,0.12)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  subStyleStepText: {
    color: '#fff',
    fontSize: 20,
    fontWeight: '300',
    lineHeight: 22,
  },
  subStyleStepValue: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '700',
    minWidth: 40,
    textAlign: 'center',
  },

  /** Position button row */
  subPosRow: {
    flexDirection: 'row',
    gap: 8,
    paddingHorizontal: 14,
    paddingTop: 10,
  },
  subPosBtn: {
    flex: 1,
    height: 36,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
  },
  subPosBtnActive: {
    backgroundColor: 'rgba(139,92,246,0.25)',
    borderColor: '#8b5cf6',
  },
  subPosBtnText: {
    color: 'rgba(255,255,255,0.7)',
    fontSize: 13,
    fontWeight: '600',
  },
  subPosBtnTextActive: {
    color: '#c4b5fd',
    fontWeight: '700',
  },

  /** Color swatch row */
  subColorRow: {
    flexDirection: 'row',
    gap: 10,
    paddingHorizontal: 14,
    paddingTop: 10,
    flexWrap: 'wrap',
  },
  subColorSwatch: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  subColorSwatchActive: {
    borderColor: '#fff',
    transform: [{ scale: 1.15 }],
  },

  /** Row containing the delay control buttons */
  delayRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingTop: 10,
    paddingBottom: 4,
    gap: 6,
  },

  /** Individual ±0.1 / ±0.5 delay button */
  delayBtn: {
    height: 32,
    paddingHorizontal: 10,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.14)',
  },

  /** Reset delay button — slightly different tint so it stands out */
  delayBtnReset: {
    backgroundColor: 'rgba(139,92,246,0.18)',
    borderColor: 'rgba(139,92,246,0.4)',
  },

  delayBtnText: {
    color: '#fff',
    fontSize: 11,
    fontWeight: '700',
  },

  /** Centre display showing the current delay value */
  delayDisplay: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },

  delayDisplayText: {
    color: '#fff',
    fontSize: 13,
    fontWeight: '700',
    fontFamily: 'monospace',
  },

  /** Small dot badge shown on the subtitle dock button */
  dockBadgeDot: {
    position: 'absolute',
    top: 6,
    right: 6,
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: 'rgba(255,255,255,0.7)',
  },

  /** Active (purple) variant of the dock badge dot */
  dockBadgeDotActive: {
    backgroundColor: '#8b5cf6',
  },

  // ── Source-switch failure toast ──────────────────────────────────────────

  switchToastWrap: {
    position: 'absolute',
    bottom: 96,
    left: 0,
    right: 0,
    alignItems: 'center',
    zIndex: 200,
  },
  switchToast: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    backgroundColor: 'rgba(20,20,24,0.92)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.15)',
    maxWidth: 380,
    shadowColor: '#000',
    shadowOpacity: 0.4,
    shadowRadius: 12,
    elevation: 8,
  },
  switchToastText: {
    color: '#fff',
    fontSize: 13,
    fontWeight: '500',
    flex: 1,
  },
  watchlistBannerWrap: {
    position: 'absolute',
    bottom: 40,
    left: 16,
    right: 16,
    alignItems: 'center',
    zIndex: 200,
  },
  watchlistBanner: {
    gap: 8,
    width: '100%',
    maxWidth: 420,
  },
  watchlistBannerRemoveBtn: {
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 12,
    backgroundColor: '#7c3aed',
  },
  watchlistBannerRemoveText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '700',
  },

});
