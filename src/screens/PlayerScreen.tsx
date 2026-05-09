import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    Animated,
    Image,
    StatusBar,
    StyleSheet,
    Text,
    TouchableOpacity,
    View,
    AppState,
    Pressable,
    Modal,
    ScrollView,
    Platform,
    Linking,
    UIManager,
    NativeModules,
} from 'react-native';
import Slider from '@react-native-community/slider';
import { useVideoPlayer, VideoView, VideoContentFit, type AudioTrack, type SubtitleTrack } from 'expo-video';
import * as ScreenOrientation from 'expo-screen-orientation';
import Constants from 'expo-constants';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
// react-native-google-cast requires a custom dev build. Guard the import so a
// missing native module produces graceful degradation instead of a hard crash.
let GoogleCast: any = null;
let CastButton: any = null;
// Provide sentinel enum objects so value comparisons don't blow up when the
// native module is absent. The sentinel values won't match real Cast states.
let CastState: any = { CONNECTED: '__unavailable_connected__', CONNECTING: '__unavailable_connecting__', NOT_CONNECTED: '__unavailable_not_connected__', NO_DEVICES_AVAILABLE: '__unavailable_no_devices__' };
let MediaStreamType: any = { BUFFERED: 0, LIVE: 1, NONE: 2 };
try {
    const castModule = require('react-native-google-cast') as typeof import('react-native-google-cast');
    GoogleCast      = castModule.default ?? (castModule as any).GoogleCast ?? null;
    CastButton      = castModule.CastButton ?? null;
    CastState       = castModule.CastState;
    MediaStreamType = castModule.MediaStreamType;
} catch {
    // Native module not available in this build — Cast features disabled.
}

// Context & Utils
import { useTrakt, ScrobblePayload } from '../context/TraktContext';
import { useAddons, AddonStream } from '../context/AddonContext';
import { useDebrid } from '../context/DebridContext';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useLanguage } from '../context/LanguageContext';
import { useTheme } from '../context/ThemeContext';
import { useTorrentServer } from '../context/TorrentServerContext';
import { useStreamSelectionSettings } from '../context/StreamSelectionContext';
import { usePlaybackSettings } from '../context/PlaybackSettingsContext';
import { useWatchProgress } from '../context/WatchProgressContext';
import { useAppLifecycle } from '../context/AppLifecycleContext';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { PlaybackLoadingOverlay } from '../components/player/PlaybackLoadingOverlay';
import {
    getProfileStorageOwnerId,
    progressFileStorageKey,
    progressIndexStorageKey,
} from '../utils/profileStorage';
import { useDisplaySettings } from '../context/DisplaySettingsContext';
import { pickBestAudioTrack, scoreStream } from '../utils/streamSelection';
import { Storage } from '../utils/storage';
import { createLocalProxyUrl } from '../utils/torrentServerClient';
import { resolvePlayableStreamUrl } from '../services/playback/streamResolution';
import { createPlaybackDiagnostics } from '../services/playback/playbackDiagnostics';
import { createPlaybackSessionStore, usePlaybackSessionSelector } from '../services/playback/playbackSessionStore';
import { parseStream } from '../utils/streamParser';
import { getMpvNativeViewAvailabilityDiagnostics, isMpvNativeViewAvailable } from '../components/MpvPlayer';
import { isExpoGoRuntime } from '../utils/runtime';

// ── Constants & Helpers ──────────────────────────────────────────────────────

const STREAM_STALL_MS = 15_000;
const STREAM_RESOLVE_TIMEOUT_MS = 12_000;
const FIRST_FRAME_RENDER_TIMEOUT_MS = 3_500;
const LOADING_MESSAGE_MIN_VISIBLE_MS = 900;
const MIN_ACCEPTABLE_STREAM_DURATION_SEC = 5 * 60;
    const MIN_PREFERRED_STREAM_SAVE_SEC = 12;
const PREFERRED_SIZE_LIMIT = 12 * 1024 * 1024 * 1024; // 12GB
const LAST_STREAM_KEY_PREFIX = 'streamdek_last_stream';
const MAGIC_HEADERS = { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36' };
const SEARCHING_MESSAGES = [
    'Searching the shelves for a decent print...',
    'Asking the projectionist for something less cursed...',
    'Dusting off the reels and checking the labels...',
];
const TRYING_SOURCE_MESSAGES = [
    'Giving another print a screen test...',
    'Swapping reels and hoping for fewer gremlins...',
    'Trying a version that looks less suspicious...',
];
const RECOVERY_MESSAGES = [
    'That one looked haunted. Trying another...',
    'Audio showed up, picture wandered off. Retrying...',
    'That print had personality issues. Switching...',
];
const SWITCHING_MESSAGES = [
    'Changing reels...',
    'One moment while we swap the print...',
    'Cueing up a different cut...',
];
interface RememberedStreamChoice {
    addonId?: string;
    infoHash?: string;
    url?: string;
    title?: string;
    name?: string;
    filename?: string;
    size?: string | null;
    quality?: string | null;
}

type RuntimePlaybackConfig = {
    decoderMode: 'hardware' | 'hardware_plus' | 'software';
    surfaceType: 'surfaceView' | 'textureView';
};

type PendingSameSourceRetry = {
    stream: AddonStream | null;
    upstreamUrl: string;
    resumeAtSec: number;
};

type PlayerSessionState = {
    loading: boolean;
    loadingMsg: string;
    isError: boolean;
    currentTime: number;
    duration: number;
    isPlaying: boolean;
};

type ExternalPlayerCandidate = {
    label: string;
    targetUrl: string;
};

type PlayerDrawerSection = 'sources' | 'tracks' | 'speed' | 'screen' | 'diagnostics';
type Media3ContentType = 'auto' | 'progressive' | 'hls' | 'dash' | 'smoothStreaming';
const SUBTITLE_OFF_PREFERENCE_KEY = '__off__';
const GUEST_ACCOUNT_PROMPT_SHOWN_KEY = 'streamdek_guest_account_prompt_shown';

type TrackPreferenceLike = {
    id?: string;
    language?: string;
    label?: string;
    name?: string;
};

function lastStreamKey(uid: string | null, type: string, contentId: string): string {
    const suffix = `${type}:${contentId}`;
    return uid ? `${LAST_STREAM_KEY_PREFIX}_${uid}_${suffix}` : `${LAST_STREAM_KEY_PREFIX}_${suffix}`;
}

function normalizeStreamText(value?: string | null): string {
    return (value ?? '').trim().replace(/\s+/g, ' ').toLowerCase();
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

    if (rememberedInfoHash || remembered.url) {
        return false;
    }

    return !!rememberedName
        && !!streamName
        && rememberedName === streamName
        && !!rememberedSize
        && rememberedSize === streamSize
        && remembered.quality === stream.quality;
}

function describeStream(stream: AddonStream | null | undefined, index?: number, total?: number): string {
    if (!stream) return 'unknown stream';

    const slot = typeof index === 'number' && typeof total === 'number'
        ? `#${index + 1}/${total}`
        : typeof index === 'number'
            ? `#${index + 1}`
            : '';
    const parts = [
        slot,
        stream.addonName || stream.addonId || 'unknown-addon',
        stream.quality || 'unknown-quality',
        stream.behaviorHints?.filename || stream.title || stream.name || stream.url || stream.infoHash || 'unknown-source',
        stream.cachedBy.length > 0 ? `cached:${stream.cachedBy.join(',')}` : 'uncached',
    ].filter(Boolean);

    return parts.join(' | ');
}

function preferredRuntimePlaybackConfig(
    decoderMode: 'auto' | 'hardware' | 'hardware_plus' | 'software',
    renderSurface: 'standard' | 'compatibility',
): RuntimePlaybackConfig {
    return {
        decoderMode: decoderMode === 'auto' ? 'hardware_plus' : decoderMode,
        surfaceType: renderSurface === 'compatibility' ? 'textureView' : 'surfaceView',
    };
}

function compatibilityFallbackConfig(config: RuntimePlaybackConfig): RuntimePlaybackConfig | null {
    if (config.decoderMode === 'software' && config.surfaceType === 'textureView') {
        return null;
    }
    return {
        decoderMode: 'software',
        surfaceType: 'textureView',
    };
}

function inferCastContentType(url: string | null | undefined): string | undefined {
    if (!url) return undefined;

    const normalized = url.split('?')[0].toLowerCase();
    if (normalized.endsWith('.m3u8')) return 'application/x-mpegURL';
    if (normalized.endsWith('.mpd')) return 'application/dash+xml';
    if (normalized.endsWith('.mp4') || normalized.endsWith('.m4v')) return 'video/mp4';
    if (normalized.endsWith('.mkv')) return 'video/x-matroska';
    if (normalized.endsWith('.webm')) return 'video/webm';
    if (normalized.endsWith('.mov')) return 'video/quicktime';
    if (normalized.endsWith('.avi')) return 'video/x-msvideo';
    return undefined;
}

function inferMedia3ContentType(url: string | null | undefined): Media3ContentType | undefined {
    if (!url) return undefined;

    const normalized = url.split('?')[0].toLowerCase();
    if (normalized.endsWith('.m3u8')) return 'hls';
    if (normalized.endsWith('.mpd')) return 'dash';
    if (normalized.endsWith('.ism') || normalized.endsWith('.ism/manifest')) return 'smoothStreaming';
    if (normalized.endsWith('.mp4') || normalized.endsWith('.m4v') || normalized.endsWith('.mkv') || normalized.endsWith('.webm') || normalized.endsWith('.mov') || normalized.endsWith('.avi')) {
        return 'progressive';
    }
    return 'auto';
}

function extractSourceExtension(value?: string | null): string | null {
    if (!value) return null;
    const cleaned = value.split('?')[0].split('#')[0].trim();
    const match = cleaned.match(/\.([a-z0-9]{2,5})$/i);
    return match ? match[1].toLowerCase() : null;
}

function shouldPreferMpvPlayback(playbackUrl: string, stream: AddonStream | null): boolean {
    if (Platform.OS !== 'android') return false;

    const ext = extractSourceExtension(playbackUrl)
        ?? extractSourceExtension(stream?.behaviorHints?.filename)
        ?? extractSourceExtension(stream?.title)
        ?? extractSourceExtension(stream?.name);

    if (stream?.infoHash) return true;

    return !ext || !['mp4', 'm4v', 'm3u8', 'mpd'].includes(ext);
}

function normalizeTrackPreferenceField(value?: string | null): string {
    return (value ?? '').trim().toLowerCase();
}

function buildTrackPreferenceKey(track?: TrackPreferenceLike | null): string | null {
    if (!track) return null;
    return [
        normalizeTrackPreferenceField(track.id),
        normalizeTrackPreferenceField(track.language),
        normalizeTrackPreferenceField(track.label),
        normalizeTrackPreferenceField(track.name),
    ].join('|');
}

function normalizePlaybackTime(value: number): number {
    return Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0;
}

function findTrackByPreferenceKey<T extends TrackPreferenceLike>(tracks: T[], preferenceKey: string | null): T | null {
    if (!preferenceKey) return null;
    const [preferredId, preferredLanguage, preferredLabel, preferredName] = preferenceKey.split('|');
    return (
        tracks.find(track => buildTrackPreferenceKey(track) === preferenceKey)
        ?? (preferredId ? tracks.find(track => normalizeTrackPreferenceField(track.id) === preferredId) ?? null : null)
        ?? (preferredLanguage ? tracks.find(track => normalizeTrackPreferenceField(track.language) === preferredLanguage) ?? null : null)
        ?? (preferredLabel ? tracks.find(track => normalizeTrackPreferenceField(track.label) === preferredLabel) ?? null : null)
        ?? (preferredName ? tracks.find(track => normalizeTrackPreferenceField(track.name) === preferredName) ?? null : null)
        ?? null
    );
}

interface SessionFailureStats {
    exactKeys: Set<string>;
    ultraHdFailures: number;
    hdrFailures: number;
    hevcFailures: number;
    tenBitFailures: number;
    upscaleFailures: number;
    sourceFailures: Record<string, number>;
}

function createSessionFailureStats(): SessionFailureStats {
    return {
        exactKeys: new Set<string>(),
        ultraHdFailures: 0,
        hdrFailures: 0,
        hevcFailures: 0,
        tenBitFailures: 0,
        upscaleFailures: 0,
        sourceFailures: {},
    };
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

function streamPenaltyText(stream: AddonStream): string {
    return [
        stream.name ?? '',
        stream.title ?? '',
        stream.quality ?? '',
        stream.behaviorHints?.filename ?? '',
    ].join(' ').toUpperCase();
}

function sessionPenaltyForStream(stream: AddonStream, stats: SessionFailureStats): number {
    const key = streamIdentityKey(stream);
    if (key && stats.exactKeys.has(key)) {
        return 100_000;
    }

    const parsed = parseStream(stream);
    const text = streamPenaltyText(stream);
    let penalty = 0;

    const isUltraHd = text.includes('4K') || text.includes('UHD') || text.includes('2160');
    const isHdr = Boolean(parsed.hdr) || text.includes(' HDR');
    const isHevc = parsed.codec === 'x265' || text.includes('HEVC') || text.includes('H265') || text.includes('X265');
    const isTenBit = /\b10[\s.\-]?BIT\b/.test(text);
    const isUpscaled = text.includes('UPSCALED') || text.includes('UPSCALE');

    if (isUltraHd) penalty += stats.ultraHdFailures * 220;
    if (isHdr) penalty += stats.hdrFailures * 180;
    if (isHevc) penalty += stats.hevcFailures * 170;
    if (isTenBit) penalty += stats.tenBitFailures * 150;
    if (isUpscaled) penalty += stats.upscaleFailures * 180;
    if (parsed.source && stats.sourceFailures[parsed.source]) {
        penalty += stats.sourceFailures[parsed.source] * 35;
    }

    return penalty;
}

function nextRotatingMessage(messages: string[], cursorRef: React.MutableRefObject<number>): string {
    const value = messages[cursorRef.current % messages.length];
    cursorRef.current += 1;
    return value;
}

function getUrlHost(value?: string | null): string {
    if (!value) return 'none';
    try {
        return new URL(value).host || 'unknown-host';
    } catch {
        return 'invalid-url';
    }
}

function describePlaybackPath(resolvedUrl?: string | null, upstreamUrl?: string | null): string {
    if (!resolvedUrl && !upstreamUrl) return 'No source';
    if (resolvedUrl && upstreamUrl && resolvedUrl !== upstreamUrl) {
        return 'Local proxy';
    }
    if (upstreamUrl) {
        return 'Direct / Debrid';
    }
    return 'Unknown';
}

function buildAndroidExternalPlayerIntent(url: string): string | null {
    try {
        const parsed = new URL(url);
        const scheme = parsed.protocol.replace(':', '');
        const hostAndPath = `${parsed.host}${parsed.pathname}${parsed.search}${parsed.hash}`;
        return `intent://${hostAndPath}#Intent;scheme=${scheme};action=android.intent.action.VIEW;type=video/*;end`;
    } catch {
        return null;
    }
}

function buildAndroidExternalPlayerIntentForPackage(url: string, packageName: string): string | null {
    try {
        const parsed = new URL(url);
        const scheme = parsed.protocol.replace(':', '');
        const hostAndPath = `${parsed.host}${parsed.pathname}${parsed.search}${parsed.hash}`;
        return `intent://${hostAndPath}#Intent;scheme=${scheme};package=${packageName};action=android.intent.action.VIEW;type=video/*;end`;
    } catch {
        return null;
    }
}

function buildExternalPlayerCandidates(
    url: string,
    options?: { includeDirectUrlHandler?: boolean },
): ExternalPlayerCandidate[] {
    const candidates: ExternalPlayerCandidate[] = [];
    const includeDirectUrlHandler = options?.includeDirectUrlHandler ?? false;

    if (Platform.OS === 'android') {
        const packageCandidates: Array<{ label: string; packageName: string }> = [
            { label: 'MPV', packageName: 'is.xyz.mpv' },
            { label: 'VLC', packageName: 'org.videolan.vlc' },
            { label: 'MX Player', packageName: 'com.mxtech.videoplayer.ad' },
            { label: 'MX Player Pro', packageName: 'com.mxtech.videoplayer.pro' },
            { label: 'Just Player', packageName: 'com.brouken.player' },
        ];

        for (const item of packageCandidates) {
            const intentUrl = buildAndroidExternalPlayerIntentForPackage(url, item.packageName);
            if (!intentUrl) continue;
            candidates.push({ label: item.label, targetUrl: intentUrl });
        }

        const genericIntent = buildAndroidExternalPlayerIntent(url);
        if (genericIntent) {
            candidates.push({ label: 'Android default player', targetUrl: genericIntent });
        }
    }

    if (includeDirectUrlHandler) {
        candidates.push({ label: 'Direct URL handler', targetUrl: url });
    }

    const seen = new Set<string>();
    return candidates.filter(candidate => {
        if (seen.has(candidate.targetUrl)) return false;
        seen.add(candidate.targetUrl);
        return true;
    });
}

function streamMetadataBadges(stream: AddonStream): string[] {
    const parsed = parseStream(stream);
    const badges = [
        stream.cachedBy[0] ? `Cached: ${stream.cachedBy[0]}` : null,
        parsed.quality,
        parsed.source,
        parsed.codec,
        parsed.hdr,
        parsed.audio,
        parsed.size,
    ].filter((value): value is string => Boolean(value));

    return badges.slice(0, 6);
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
    } catch (e) { }
}

function buildPayload(movieId: string, type: string, title?: string, year?: number, progress?: number): ScrobblePayload {
    const tmdbId = parseInt(movieId, 10) || undefined;
    if (type === 'tv') return { show: { title: title ?? '', year, ids: { tmdb: tmdbId } }, progress: progress ?? 0 };
    return { movie: { title: title ?? '', year, ids: { tmdb: tmdbId } }, progress: progress ?? 0 };
}

// ── PlayerScreen ─────────────────────────────────────────────────────────────

export const PlayerScreen = ({ route, navigation }: any) => {
    const { t } = useLanguage();
    const { theme } = useTheme();
    const { config: serverConfig } = useTorrentServer();
    const {
        enabled: streamSelectionEnabled,
        shortSourceFilterEnabled,
        preferredQuality,
        maxFileSizeGB,
    } = useStreamSelectionSettings();
    const castNativeModuleAvailable = !!NativeModules.RNGCCastContext;
    const platformAndroidVersion = typeof Platform.Version === 'number' ? Platform.Version : Number(Platform.Version);
    const mpvQuickActionVisible = Platform.OS === 'android' && Number.isFinite(platformAndroidVersion) && platformAndroidVersion >= 26;
    const mpvNativeViewAvailable = isMpvNativeViewAvailable();
    const {
        decoderMode,
        renderSurface,
        setDecoderMode,
        setRenderSurface,
    } = usePlaybackSettings();
    const insets = useSafeAreaInsets();
    const { pictureInPictureEnabled } = useDisplaySettings();
    const expoGoRuntime = isExpoGoRuntime();
    const shouldUseEmbeddedVideoPlayer = expoGoRuntime;
    const [runtimePlaybackConfig, setRuntimePlaybackConfig] = useState<RuntimePlaybackConfig>(() => (
        preferredRuntimePlaybackConfig(decoderMode, renderSurface)
    ));
    const [pendingSameSourceRetry, setPendingSameSourceRetry] = useState<PendingSameSourceRetry | null>(null);
    
    const {
        movieId,
        imdbId,
        type = 'movie',
        title,
        year,
        synopsis,
        titleLogo: paramTitleLogo,
        streamUrl: paramUrl,
        backdrop: paramBackdrop,
        poster: paramPoster,
        progressKey: paramProgressKey,
        resumeFrom: paramResumeFrom,
        forceStartFromBeginning: paramForceStartFromBeginning,
        activeStream: paramActiveStream,
        openSourcesOnStart: paramOpenSourcesOnStart,
        preferredSourceIndex: paramPreferredSourceIndex,
        preferredSourceIdentity: paramPreferredSourceIdentity,
    } = route.params ?? {};
    const loadingArtworkUri = paramBackdrop ?? paramPoster ?? null;
    const titleLogoUri = typeof paramTitleLogo === 'string' && paramTitleLogo.length > 0
        ? paramTitleLogo
        : null;
    const forceStartFromBeginning = Boolean(paramForceStartFromBeginning);

    const { scrobble, isConnected } = useTrakt();
    const { user } = useAuth();
    const { activeProfile } = useProfile();
    const { fetchStreams } = useAddons();
    const { accounts: debridAccounts, resolveStream, unrestrictLink, streamTorrent } = useDebrid();
    const { saveProgress, flushProgress, clearProgress } = useWatchProgress();
    const { appState, isForeground } = useAppLifecycle();
    const storageOwnerId = getProfileStorageOwnerId(user?.uid ?? null, activeProfile?.id ?? null);

    // ── State ──────────────────────────────────────────────────────────────────
    const [allStreams, setAllStreams] = useState<AddonStream[]>([]);
    const [activeStream, setActiveStream] = useState<AddonStream | null>(null);
    const [resolvedUrl, setResolvedUrl] = useState<string | null>(paramUrl ?? null);
    const [upstreamResolvedUrl, setUpstreamResolvedUrl] = useState<string | null>(paramUrl ?? null);
    const sessionStoreRef = useRef(createPlaybackSessionStore<PlayerSessionState>({
        loading: true,
        loadingMsg: t('player_resolving') || SEARCHING_MESSAGES[0],
        isError: false,
        currentTime: 0,
        duration: 0,
        isPlaying: true,
    }));
    const sessionStore = sessionStoreRef.current;
    const loading = usePlaybackSessionSelector(sessionStore, state => state.loading);
    const loadingMsg = usePlaybackSessionSelector(sessionStore, state => state.loadingMsg);
    const isError = usePlaybackSessionSelector(sessionStore, state => state.isError);
    const currentTime = usePlaybackSessionSelector(sessionStore, state => state.currentTime);
    const duration = usePlaybackSessionSelector(sessionStore, state => state.duration);
    const isPlaying = usePlaybackSessionSelector(sessionStore, state => state.isPlaying);
    const setLoading = useCallback((value: boolean) => sessionStore.setState({ loading: value }), [sessionStore]);
    const setLoadingMsg = useCallback((value: string) => sessionStore.setState({ loadingMsg: value }), [sessionStore]);
    const setIsError = useCallback((value: boolean) => sessionStore.setState({ isError: value }), [sessionStore]);
    const setCurrentTime = useCallback((value: number) => sessionStore.setState({ currentTime: value }), [sessionStore]);
    const setDuration = useCallback((value: number) => sessionStore.setState({ duration: value }), [sessionStore]);
    const setIsPlaying = useCallback((value: boolean) => sessionStore.setState({ isPlaying: value }), [sessionStore]);
    const [contentFit, setContentFit] = useState<VideoContentFit>('cover'); 
    
    // Custom UI State
    const [showControls, setShowControls] = useState(true);
    const [showSettings, setShowSettings] = useState(false);
    const [showSources, setShowSources] = useState(false);
    const [showPlayerDrawer, setShowPlayerDrawer] = useState(false);
    const [activeDrawerSection, setActiveDrawerSection] = useState<PlayerDrawerSection>('sources');
    const [recentDebridFailures, setRecentDebridFailures] = useState<Array<{ provider?: string; code?: string; message?: string }>>([]);
    const [externalPlayerErrorMessage, setExternalPlayerErrorMessage] = useState<string | null>(null);
    const [castErrorMessage, setCastErrorMessage] = useState<string | null>(null);
    const [castState, setCastState] = useState<string | 'unavailable' | null>(
        castNativeModuleAvailable ? null : 'unavailable',
    );
    const [showCompatibilitySuggestion, setShowCompatibilitySuggestion] = useState(false);
    const [playbackSpeed, setPlaybackSpeed] = useState(1.0);
    const [isHandingOffToMpv, setIsHandingOffToMpv] = useState(false);
    const [showGuestAccountPrompt, setShowGuestAccountPrompt] = useState(false);
    const isPausedPlayback = shouldUseEmbeddedVideoPlayer && !loading && !isPlaying && !isHandingOffToMpv;
    const castNativeButtonAvailable = !!UIManager.getViewManagerConfig?.('RNGoogleCastButton');
    const drawerTranslateX = useRef(new Animated.Value(360)).current;
    const loadingLogoBreathAnim = useRef(new Animated.Value(1)).current;
    const loadingTextOpacity = useRef(new Animated.Value(1)).current;
    const controlsOpacity = useRef(new Animated.Value(1)).current;

    const flushProgressAndGoBack = useCallback(() => {
        flushProgress();
        navigation.goBack();
    }, [flushProgress, navigation]);

    // ── Player & Refs ──────────────────────────────────────────────────────────
    const initialSource = '';
    const effectiveDecoderMode = Platform.OS === 'android'
        ? runtimePlaybackConfig.decoderMode
        : undefined;
    const effectiveSurfaceType = Platform.OS === 'android'
        ? runtimePlaybackConfig.surfaceType
        : undefined;
    // Memoize options so useVideoPlayer's JSON.stringify dep doesn't see a new
    // object reference on every render — only recreate the player when the
    // decoder mode actually changes (e.g. compatibility fallback kicks in).
    const playerBuilderOptions = React.useMemo(
        () => effectiveDecoderMode ? { decodingMode: effectiveDecoderMode } : undefined,
        [effectiveDecoderMode],
    );
    const player = useVideoPlayer(initialSource, p => {
        p.play();
        p.timeUpdateEventInterval = 1.0;
    }, playerBuilderOptions);

    const preferredAudioTrackKeyRef = useRef<string | null>(null);
    const preferredSubtitleTrackKeyRef = useRef<string | null>(null);

    const playerRef = useRef(player);
    const videoViewRef = useRef<React.ElementRef<typeof VideoView> | null>(null);
    const isMountedRef = useRef(true);
    const isHandlingErrorRef = useRef(false);
    const allStreamsRef = useRef<AddonStream[]>([]);
    const activeStreamRef = useRef<AddonStream | null>(null);
    const activeStreamIndexRef = useRef(-1);
    const failureStatsRef = useRef<SessionFailureStats>(createSessionFailureStats());
    const resolveInFlightCountRef = useRef(0);
    const latestResolveAttemptIdRef = useRef(0);
    const sourceLoadedRef = useRef(false);
    const firstFrameRenderedRef = useRef(false);
    const resolvedUrlRef = useRef<string | null>(paramUrl ?? null);
    const upstreamResolvedUrlRef = useRef<string | null>(paramUrl ?? null);
    const playerLogMetaRef = useRef<Record<string, unknown>>({});
    const loadingMessageTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const pendingLoadingMessageRef = useRef<string | null>(null);
    const lastLoadingMessageAtRef = useRef(0);
    const loadingCopyCursorRef = useRef(0);
    const firstFrameTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const compatibilityRetriedKeysRef = useRef<Set<string>>(new Set());
    const compatibilitySuggestionShownRef = useRef(false);
    const compatibilityFailureCountRef = useRef(0);
    const castPendingLoadRef = useRef(false);
    const castLoadingRef = useRef(false);
    const lastCastSignatureRef = useRef<string | null>(null);
    const embeddedMpvFallbackAttemptedRef = useRef(false);
    
    const lastProgressSaveRef = useRef(0);
    const lastUiTimeRef = useRef(-1);
    const playbackPosRef = useRef(0);
    const playbackDurRef = useRef(0);
    const playbackCompletedRef = useRef(false);
    const scrobbledStart = useRef(false);
    const progressTimer = useRef<any>(null);
    const appStateRef = useRef(appState);
    const payloadRef = useRef({ movieId, type, title, year });
    const pendingResumeRef = useRef<number | null>(typeof paramResumeFrom === 'number' ? paramResumeFrom : null);
    const didApplyResumeRef = useRef(false);
    const preferredSaveStartPositionRef = useRef(0);
    const preferredSaveBlockedRef = useRef(false);
    const preferredSavedForCurrentSourceRef = useRef(false);
    const validatedDurationForCurrentSourceRef = useRef(false);
    const guestPromptHandledRef = useRef(false);

    useEffect(() => {
        if (Platform.OS !== 'android') return;

        player.bufferOptions = {
            preferredForwardBufferDuration: 45,
            minBufferForPlayback: 1.5,
            maxBufferBytes: 256 * 1024 * 1024,
            prioritizeTimeOverSizeThreshold: true,
        };
    }, [player]);

    useEffect(() => {
        if (user || guestPromptHandledRef.current || loading || isError || currentTime <= 0) {
            return;
        }

        guestPromptHandledRef.current = true;
        void (async () => {
            try {
                const alreadyShown = await Storage.getItem(GUEST_ACCOUNT_PROMPT_SHOWN_KEY);
                if (alreadyShown) return;
                await Storage.setItem(GUEST_ACCOUNT_PROMPT_SHOWN_KEY, '1');
                if (isMountedRef.current) {
                    setShowGuestAccountPrompt(true);
                }
            } catch {
                // Ignore prompt persistence failures.
            }
        })();
    }, [currentTime, isError, loading, user]);

    const controlsTimerRef = useRef<any>(null);
    const didAutoOpenSourcesRef = useRef(false);
    const skipPortraitOnUnmountRef = useRef(false);

    const formatTime = (seconds: number) => {
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = Math.floor(seconds % 60);
        if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
        return `${m}:${s.toString().padStart(2, '0')}`;
    };

    const showControlsAnimated = useCallback(() => {
        if (controlsTimerRef.current) clearTimeout(controlsTimerRef.current);
        controlsOpacity.stopAnimation();
        setShowControls(true);
        Animated.timing(controlsOpacity, {
            toValue: 1,
            duration: 180,
            useNativeDriver: true,
        }).start();
    }, [controlsOpacity]);

    const hideControlsAnimated = useCallback(() => {
        if (controlsTimerRef.current) clearTimeout(controlsTimerRef.current);
        controlsOpacity.stopAnimation();
        Animated.timing(controlsOpacity, {
            toValue: 0,
            duration: 260,
            useNativeDriver: true,
        }).start(({ finished }) => {
            if (finished && !showSettings) {
                setShowControls(false);
            }
        });
    }, [controlsOpacity, showSettings]);

    const resetControlsTimer = useCallback(() => {
        showControlsAnimated();
        controlsTimerRef.current = setTimeout(() => {
            if (!showSettings) hideControlsAnimated();
        }, isPausedPlayback ? 3000 : 3500);
    }, [hideControlsAnimated, isPausedPlayback, showControlsAnimated, showSettings]);

    const toggleControls = useCallback(() => {
        const nextShow = !showControls;
        if (nextShow) {
            resetControlsTimer();
        } else {
            hideControlsAnimated();
        }
    }, [hideControlsAnimated, resetControlsTimer, showControls]);

    const rememberAudioTrackPreference = useCallback((track?: AudioTrack | null) => {
        if (!track) return;
        const key = buildTrackPreferenceKey(track);
        if (key) {
            preferredAudioTrackKeyRef.current = key;
        }
    }, []);

    const rememberSubtitleTrackPreference = useCallback((track?: SubtitleTrack | null) => {
        if (!track) return;
        const key = buildTrackPreferenceKey(track);
        if (key) {
            preferredSubtitleTrackKeyRef.current = key;
        }
    }, []);

    const applyPreferredTrackSelection = useCallback((
        availableAudioTracks: AudioTrack[] = [],
        availableSubtitleTracks: SubtitleTrack[] = [],
    ) => {
        const preferredAudioKey = preferredAudioTrackKeyRef.current;
        const preferredSubtitleKey = preferredSubtitleTrackKeyRef.current;

        if (preferredAudioKey) {
            const preferredAudioTrack = findTrackByPreferenceKey(availableAudioTracks, preferredAudioKey);
            if (preferredAudioTrack) {
                player.audioTrack = preferredAudioTrack;
            } else if (availableAudioTracks.length > 0) {
                const fallbackAudioTrack = pickBestAudioTrack(availableAudioTracks, 'en');
                if (fallbackAudioTrack) {
                    player.audioTrack = fallbackAudioTrack;
                    rememberAudioTrackPreference(fallbackAudioTrack);
                }
            }
        } else if (availableAudioTracks.length > 0) {
            const fallbackAudioTrack = pickBestAudioTrack(availableAudioTracks, 'en');
            if (fallbackAudioTrack) {
                player.audioTrack = fallbackAudioTrack;
                rememberAudioTrackPreference(fallbackAudioTrack);
            }
        }

        if (preferredSubtitleKey === SUBTITLE_OFF_PREFERENCE_KEY) {
            player.subtitleTrack = null;
            return;
        }

        if (preferredSubtitleKey) {
            const preferredSubtitleTrack = findTrackByPreferenceKey(availableSubtitleTracks, preferredSubtitleKey);
            if (preferredSubtitleTrack) {
                player.subtitleTrack = preferredSubtitleTrack;
            }
        }
    }, [player, rememberAudioTrackPreference]);

    const closePlayerDrawer = useCallback(() => {
        Animated.timing(drawerTranslateX, {
            toValue: 360,
            duration: 220,
            useNativeDriver: true,
        }).start(({ finished }) => {
            if (finished) {
                setShowPlayerDrawer(false);
                setShowSettings(false);
                setShowSources(false);
            }
        });
    }, [drawerTranslateX]);

    const openPlayerDrawer = useCallback((section: PlayerDrawerSection) => {
        setShowPlayerDrawer(true);
        setActiveDrawerSection(section);
        setShowSettings(section !== 'sources');
        setShowSources(section === 'sources');
        setShowControls(true);
        if (section === 'sources') {
            setShowSettings(false);
        } else {
            setShowSources(false);
        }
    }, []);

    const clearLoadingMessageTimer = useCallback(() => {
        if (loadingMessageTimerRef.current) {
            clearTimeout(loadingMessageTimerRef.current);
            loadingMessageTimerRef.current = null;
        }
    }, []);

    const updateLoadingMessage = useCallback((
        message: string,
        options?: { immediate?: boolean; minVisibleMs?: number },
    ) => {
        const minVisibleMs = options?.minVisibleMs ?? LOADING_MESSAGE_MIN_VISIBLE_MS;
        const now = Date.now();
        const elapsed = now - lastLoadingMessageAtRef.current;
        const shouldApplyNow = options?.immediate || elapsed >= minVisibleMs || lastLoadingMessageAtRef.current === 0;

        clearLoadingMessageTimer();

        if (shouldApplyNow) {
            pendingLoadingMessageRef.current = null;
            lastLoadingMessageAtRef.current = now;
            setLoadingMsg(message);
            return;
        }

        pendingLoadingMessageRef.current = message;
        loadingMessageTimerRef.current = setTimeout(() => {
            loadingMessageTimerRef.current = null;
            const pending = pendingLoadingMessageRef.current;
            if (!pending) return;
            pendingLoadingMessageRef.current = null;
            lastLoadingMessageAtRef.current = Date.now();
            setLoadingMsg(pending);
        }, Math.max(0, minVisibleMs - elapsed));
    }, [clearLoadingMessageTimer]);

    const showSearchingMessage = useCallback(() => {
        updateLoadingMessage(nextRotatingMessage(SEARCHING_MESSAGES, loadingCopyCursorRef), { immediate: true });
    }, [updateLoadingMessage]);

    const showTryingSourceMessage = useCallback((index: number, total: number) => {
        const witty = nextRotatingMessage(TRYING_SOURCE_MESSAGES, loadingCopyCursorRef);
        updateLoadingMessage(`${witty}\nTrying source ${index}/${total}...`);
    }, [updateLoadingMessage]);

    const showRecoveryMessage = useCallback((fallback?: string) => {
        updateLoadingMessage(fallback ?? nextRotatingMessage(RECOVERY_MESSAGES, loadingCopyCursorRef));
    }, [updateLoadingMessage]);

    const showSwitchingMessage = useCallback(() => {
        updateLoadingMessage(nextRotatingMessage(SWITCHING_MESSAGES, loadingCopyCursorRef), { immediate: true });
    }, [updateLoadingMessage]);

    useEffect(() => {
        appStateRef.current = appState;
    }, [appState]);

    useEffect(() => {
        if (!loading || isError || !isForeground) {
            loadingLogoBreathAnim.setValue(1);
            loadingTextOpacity.setValue(1);
            return;
        }

        const breath = Animated.loop(
            Animated.sequence([
                Animated.timing(loadingLogoBreathAnim, { toValue: 0.35, duration: 1400, useNativeDriver: true }),
                Animated.timing(loadingLogoBreathAnim, { toValue: 1, duration: 1400, useNativeDriver: true }),
            ]),
        );
        breath.start();
        return () => breath.stop();
    }, [appState, isError, isForeground, loading, loadingLogoBreathAnim, loadingTextOpacity]);

    useEffect(() => {
        resetControlsTimer();
        return () => {
            if (controlsTimerRef.current) clearTimeout(controlsTimerRef.current);
            clearLoadingMessageTimer();
        };
    }, [clearLoadingMessageTimer, resetControlsTimer]);

    useEffect(() => {
        if (!shouldUseEmbeddedVideoPlayer || loading || showSettings) return;
        resetControlsTimer();
    }, [isPlaying, loading, resetControlsTimer, shouldUseEmbeddedVideoPlayer, showSettings]);

    // UI Handle AppState & Orientation
    useEffect(() => {
        if (shouldUseEmbeddedVideoPlayer) {
            ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.LANDSCAPE);
        }
        StatusBar.setHidden(true);
        
        const focusSub = navigation.addListener('focus', () => {
            StatusBar.setHidden(true);
        });

        const appStateSub = AppState.addEventListener('change', nextAppState => {
            appStateRef.current = nextAppState;
            if (nextAppState === 'active') {
                StatusBar.setHidden(true);
                return;
            }
            loadingLogoBreathAnim.stopAnimation();
            loadingLogoBreathAnim.setValue(1);
            loadingTextOpacity.setValue(1);
            // Trigger PiP on 'inactive' (fires before 'background' — better timing on Android)
            // and also on 'background' as a fallback. The native startsPictureInPictureAutomatically
            // prop handles Android 12+ auto-enter; this covers manual entry and older Android.
            if (
                (nextAppState === 'inactive' || nextAppState === 'background')
                && pictureInPictureEnabled
                && shouldUseEmbeddedVideoPlayer
                && !loading
                && videoViewRef.current
            ) {
                void videoViewRef.current.startPictureInPicture().catch(() => {});
            }
        });
        
        return () => {
            focusSub();
            appStateSub.remove();
            StatusBar.setHidden(false);
            if (!skipPortraitOnUnmountRef.current) {
                ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP);
            }
            skipPortraitOnUnmountRef.current = false;
        };
    }, [loading, navigation, pictureInPictureEnabled, shouldUseEmbeddedVideoPlayer]);

    useEffect(() => { playerRef.current = player; }, [player]);
    useEffect(() => { allStreamsRef.current = allStreams; }, [allStreams]);
    useEffect(() => { activeStreamRef.current = activeStream; }, [activeStream]);
    useEffect(() => {
        activeStreamIndexRef.current = activeStream
            ? allStreams.findIndex(stream => stream === activeStream)
            : -1;
    }, [allStreams, activeStream]);
    useEffect(() => { resolvedUrlRef.current = resolvedUrl; }, [resolvedUrl]);
    useEffect(() => { upstreamResolvedUrlRef.current = upstreamResolvedUrl; }, [upstreamResolvedUrl]);
    useEffect(() => { payloadRef.current = { movieId, type, title, year }; }, [movieId, type, title, year]);
    useEffect(() => {
        playbackCompletedRef.current = false;
    }, [movieId, imdbId, type, paramUrl, paramProgressKey, shouldUseEmbeddedVideoPlayer]);
    useEffect(() => {
        playerLogMetaRef.current = {
            userId: user?.uid ?? null,
            type,
            title: title ?? null,
            imdbId: imdbId ?? null,
            movieId: movieId ?? null,
        };
    }, [user?.uid, type, title, imdbId, movieId]);

    const logPlayerEvent = useMemo(
        () => createPlaybackDiagnostics('Player', playerLogMetaRef),
        [],
    );

    const shouldHandlePlayerFailure = useCallback((reason: string): boolean => {
        if (resolveInFlightCountRef.current > 0) {
            logPlayerEvent('info', `[Player] Ignoring ${reason} while source resolution is in flight`);
            return false;
        }
        if (!resolvedUrlRef.current) {
            logPlayerEvent('info', `[Player] Ignoring ${reason} before any source URL has been assigned`);
            return false;
        }
        return true;
    }, [logPlayerEvent]);

    const pauseEmbeddedPlayer = useCallback((reason: string) => {
        if (!shouldUseEmbeddedVideoPlayer) return;
        try {
            playerRef.current?.pause();
        } catch (error) {
            logPlayerEvent('warn', `[Player] Ignored pause failure while ${reason}`, {
                error,
            });
        }
    }, [logPlayerEvent, shouldUseEmbeddedVideoPlayer]);

    const persistPreferredStream = useCallback(async (reason: string) => {
        const active = activeStreamRef.current;
        if (!active) return;
        try {
            logPlayerEvent('info',
                `[Player] Remembering preferred source because ${reason}: ${describeStream(active, activeStreamIndexRef.current, allStreamsRef.current.length)}`,
            );
            await Storage.setItem(
                lastStreamKey(user?.uid ?? null, type, imdbId ?? String(movieId)),
                JSON.stringify(serializeRememberedStream(active, upstreamResolvedUrlRef.current)),
            );
        } catch {
            // Ignore preference persistence failures
        }
    }, [user?.uid, type, imdbId, movieId, logPlayerEvent]);

    const trySameSourceCompatibilityRetry = useCallback((
        failedStream: AddonStream | null | undefined,
        resumeAtSec: number,
    ): boolean => {
        if (Platform.OS !== 'android') return false;
        if (!failedStream) return false;

        const upstreamUrl = upstreamResolvedUrlRef.current;
        if (!upstreamUrl) return false;

        const identityKey = streamIdentityKey(failedStream);
        if (identityKey && compatibilityRetriedKeysRef.current.has(identityKey)) {
            return false;
        }

        const fallbackConfig = compatibilityFallbackConfig(runtimePlaybackConfig);
        if (!fallbackConfig) return false;

        if (identityKey) {
            compatibilityRetriedKeysRef.current.add(identityKey);
        }

        logPlayerEvent(
            'warn',
            `[Player] Retrying same source with compatibility playback config for ${describeStream(failedStream, activeStreamIndexRef.current, allStreamsRef.current.length)}`,
        );
        updateLoadingMessage('Retrying this source with a more compatible playback mode...', { immediate: true });
        setRuntimePlaybackConfig(fallbackConfig);
        setPendingSameSourceRetry({
            stream: failedStream,
            upstreamUrl,
            resumeAtSec,
        });
        return true;
    }, [logPlayerEvent, runtimePlaybackConfig, updateLoadingMessage]);

    const maybeSuggestCompatibilityMode = useCallback((playbackFailure: boolean) => {
        if (Platform.OS !== 'android') return;
        if (!playbackFailure) return;
        if (compatibilitySuggestionShownRef.current) return;
        if (runtimePlaybackConfig.decoderMode === 'software' && runtimePlaybackConfig.surfaceType === 'textureView') {
            return;
        }

        compatibilityFailureCountRef.current += 1;
        if (compatibilityFailureCountRef.current < 2) return;

        compatibilitySuggestionShownRef.current = true;
        setShowCompatibilitySuggestion(true);
    }, [runtimePlaybackConfig.decoderMode, runtimePlaybackConfig.surfaceType]);

    const maybePersistPreferredStream = useCallback((
        reason: string,
        options?: { force?: boolean },
    ): boolean => {
        if (!activeStreamRef.current) return false;
        if (preferredSaveBlockedRef.current || preferredSavedForCurrentSourceRef.current) return false;
        if (!sourceLoadedRef.current || !resolvedUrlRef.current) return false;
        if (!firstFrameRenderedRef.current) return false;
        if (player.status === 'error') return false;
        if (options?.force && (isHandlingErrorRef.current || resolveInFlightCountRef.current > 0)) return false;
        if (!options?.force) {
            const watchedSec = Math.max(0, playbackPosRef.current - preferredSaveStartPositionRef.current);
            if (watchedSec < MIN_PREFERRED_STREAM_SAVE_SEC) return false;
        }

        preferredSavedForCurrentSourceRef.current = true;
        void persistPreferredStream(reason);
        return true;
    }, [persistPreferredStream, player.status]);

    useEffect(() => {
        let cancelled = false;

        const loadResumePosition = async () => {
            if (forceStartFromBeginning) return;
            if (typeof paramResumeFrom === 'number' && paramResumeFrom > 0) {
                pendingResumeRef.current = paramResumeFrom;
                return;
            }
            if (!paramProgressKey) return;

            try {
                const raw = await Storage.getItem(progressFileStorageKey(storageOwnerId, paramProgressKey));
                if (cancelled || !raw) return;
                const entry = JSON.parse(raw);
                if (typeof entry?.positionSec === 'number' && entry.positionSec > 0) {
                    pendingResumeRef.current = entry.positionSec;
                }
            } catch {
                // Ignore malformed or missing progress data
            }
        };

        loadResumePosition();
        return () => { cancelled = true; };
    }, [forceStartFromBeginning, paramResumeFrom, paramProgressKey, storageOwnerId]);

    // Track state in local state for UI updates
    useEffect(() => {
        const timeSub = player.addListener('timeUpdate', ({ currentTime }) => {
            const normalizedCurrentTime = normalizePlaybackTime(currentTime);
            if (lastUiTimeRef.current !== normalizedCurrentTime) {
                lastUiTimeRef.current = normalizedCurrentTime;
                setCurrentTime(normalizedCurrentTime);
            }
            if (player.duration > 0) setDuration(player.duration);
            if (
                shouldUseEmbeddedVideoPlayer
                && !playbackCompletedRef.current
                && player.duration > 0
                && currentTime >= Math.max(0, player.duration - 0.5)
            ) {
                playbackCompletedRef.current = true;
                if (paramProgressKey) {
                    clearProgress(paramProgressKey);
                }
                pauseEmbeddedPlayer('finishing playback');
                navigation.goBack();
            }
        });
        const statusSub = player.addListener('statusChange', () => {
            setIsPlaying(player.playing);
        });
        const playSub = player.addListener('playingChange', ({ isPlaying }) => {
            setIsPlaying(isPlaying);
        });
        return () => {
            timeSub.remove();
            statusSub.remove();
            playSub.remove();
        };
    }, [clearProgress, navigation, paramProgressKey, player, pauseEmbeddedPlayer, shouldUseEmbeddedVideoPlayer]);

    const handlePlayPause = useCallback(() => {
        if (player.playing) pauseEmbeddedPlayer('toggling pause');
        else player.play();
        setIsPlaying(!player.playing);
        resetControlsTimer();
    }, [pauseEmbeddedPlayer, player, resetControlsTimer]);

    const seekBy = useCallback((seconds: number) => {
        player.seekBy(seconds);
        resetControlsTimer();
    }, [player, resetControlsTimer]);

    const isSameStream = (s1: AddonStream | null, s2: AddonStream | null) => {
        if (!s1 || !s2) return false;
        const key1 = streamIdentityKey(s1);
        const key2 = streamIdentityKey(s2);
        if (key1 && key2) return key1 === key2;
        if (s1.infoHash && s2.infoHash) return s1.infoHash.toLowerCase() === s2.infoHash.toLowerCase();
        return s1.url === s2.url && s1.title === s2.title;
    };

    const getSessionPenalty = useCallback((stream: AddonStream): number => {
        return sessionPenaltyForStream(stream, failureStatsRef.current);
    }, []);

    const getRankedStreams = useCallback((streams: AddonStream[]): AddonStream[] => {
        const preferQuickStart = serverConfig.streamingMode === 'server';
        const baseOptions = {
            preferredQuality,
            maxFileSizeGB: maxFileSizeGB > 0 ? maxFileSizeGB : undefined,
        };
        return [...streams].sort((a, b) => (
            scoreStream(b, {
                preferQuickStart,
                ...baseOptions,
                sessionPenalty: getSessionPenalty(b),
            })
            - scoreStream(a, {
                preferQuickStart,
                ...baseOptions,
                sessionPenalty: getSessionPenalty(a),
            })
        ));
    }, [getSessionPenalty, maxFileSizeGB, preferredQuality, serverConfig.streamingMode, streamSelectionEnabled]);

    const recordFailedStream = useCallback((
        stream: AddonStream | null | undefined,
        options?: { playbackFailure?: boolean },
    ) => {
        if (!stream) return;

        const stats = failureStatsRef.current;
        const key = streamIdentityKey(stream);
        if (key) {
            stats.exactKeys.add(key);
        }

        const parsed = parseStream(stream);
        if (parsed.source) {
            stats.sourceFailures[parsed.source] = (stats.sourceFailures[parsed.source] ?? 0) + 1;
        }

        if (!options?.playbackFailure) {
            return;
        }

        const text = streamPenaltyText(stream);
        const ultraHd = text.includes('4K') || text.includes('UHD') || text.includes('2160');
        const hdr = Boolean(parsed.hdr) || text.includes(' HDR');
        const hevc = parsed.codec === 'x265' || text.includes('HEVC') || text.includes('H265') || text.includes('X265');
        const tenBit = /\b10[\s.\-]?BIT\b/.test(text);
        const upscaled = text.includes('UPSCALED') || text.includes('UPSCALE');

        if (ultraHd) stats.ultraHdFailures += 1;
        if (hdr) stats.hdrFailures += 1;
        if (hevc) stats.hevcFailures += 1;
        if (tenBit) stats.tenBitFailures += 1;
        if (upscaled) stats.upscaleFailures += 1;
    }, []);

    const logDebridFailures = useCallback((
        contextLabel: string,
        failures: Array<{ provider?: string; code?: string; message?: string }> | undefined,
    ) => {
        if (!failures?.length) return;
        setRecentDebridFailures(failures);
        for (const failure of failures) {
            logPlayerEvent(
                'warn',
                `[Player] ${contextLabel}: ${failure.provider ?? 'unknown-provider'} ${failure.code ?? 'unknown'} ${failure.message ?? 'Unknown error'}`,
            );
        }
    }, [logPlayerEvent]);

    const openExternalPlayer = useCallback(async (
        playbackUrl: string,
        reason: 'preferred' | 'manual' | 'fallback',
        stream: AddonStream | null,
    ): Promise<string> => {
        const candidates = buildExternalPlayerCandidates(playbackUrl, {
            includeDirectUrlHandler: reason === 'manual',
        });
        const errors: string[] = [];

        for (const candidate of candidates) {
            try {
                await Linking.openURL(candidate.targetUrl);
                logPlayerEvent(
                    'info',
                    `[Player] External player launch (${reason}) succeeded with ${candidate.label} for ${describeStream(stream, activeStreamIndexRef.current, allStreamsRef.current.length)}`,
                );
                return candidate.label;
            } catch (error) {
                const message = error instanceof Error ? error.message : String(error);
                errors.push(`${candidate.label}: ${message}`);
                logPlayerEvent(
                    'warn',
                    `[Player] External player launch (${reason}) failed with ${candidate.label}`,
                    { error: message },
                );
            }
        }

        throw new Error(errors.join(' | ') || 'No compatible external player found');
    }, [logPlayerEvent]);

    const launchEmbeddedMpv = useCallback((
        playbackUrl: string,
        resumeAtSec: number,
        reason: 'manual' | 'fallback' | 'default' | 'compatibility',
    ): boolean => {
        if (!isMpvNativeViewAvailable()) return false;
        if (!playbackUrl) return false;
        const preferredKey = lastStreamKey(user?.uid ?? null, type, imdbId ?? String(movieId));
        const preferredValue = activeStreamRef.current
            ? serializeRememberedStream(activeStreamRef.current, upstreamResolvedUrlRef.current ?? playbackUrl)
            : null;
        const sourceOptions = allStreamsRef.current.map((stream, index) => {
            const parsed = parseStream(stream);
            const badges = streamMetadataBadges(stream);
            return {
                index,
                identity: streamIdentityKey(stream),
                title: stream.title || `Source ${index + 1}`,
                provider: parsed.providerLine || stream.name || stream.addonName || stream.addonId || 'Unknown Provider',
                details: parsed.specLine || badges.join(' • ') || stream.quality || null,
            };
        });
        const sourceStreams = allStreamsRef.current.map(stream => ({
            addonId: stream.addonId,
            addonName: stream.addonName,
            name: stream.name,
            title: stream.title,
            url: stream.url,
            infoHash: stream.infoHash,
            fileIdx: stream.fileIdx,
            behaviorHints: stream.behaviorHints,
            quality: stream.quality,
            size: stream.size,
            cachedBy: stream.cachedBy,
        }));
        const activeSourceIdentity = streamIdentityKey(activeStreamRef.current);
        logPlayerEvent(
            'info',
            `[Player] Launching embedded MPV (${reason}) for ${describeStream(activeStreamRef.current, activeStreamIndexRef.current, allStreamsRef.current.length)}`,
        );
        skipPortraitOnUnmountRef.current = true;
        setIsHandingOffToMpv(true);
        setLoading(true);
        setShowControls(false);
        requestAnimationFrame(() => {
            navigation.replace('MpvPlayer', {
                streamUrl: playbackUrl,
                headers: MAGIC_HEADERS,
                title,
                year,
                type,
                synopsis,
                titleLogo: paramTitleLogo,
                backdrop: paramBackdrop,
                poster: paramPoster,
                initialLoadingMessage: loadingMsg,
                resumeFrom: Math.max(0, resumeAtSec),
                forceStartFromBeginning,
                returnToPlayerParams: {
                    movieId,
                    imdbId,
                    type,
                    title,
                    year,
                    synopsis,
                    titleLogo: paramTitleLogo,
                    backdrop: paramBackdrop,
                    poster: paramPoster,
                    progressKey: paramProgressKey,
                },
                rememberedSourceKey: preferredKey,
                rememberedSourceValue: preferredValue,
                sourceOptions,
                sourceStreams,
                activeSourceIdentity,
            });
        });
        return true;
    }, [
        forceStartFromBeginning,
        imdbId,
        loadingMsg,
        logPlayerEvent,
        movieId,
        navigation,
        paramBackdrop,
        paramPoster,
        paramProgressKey,
        paramTitleLogo,
        title,
        type,
        user?.uid,
        year,
    ]);

    const getEmbeddedMpvUnavailableMessage = useCallback((): string => {
        if (Platform.OS !== 'android') {
            return 'Embedded MPV is only available on Android builds.';
        }
        const sdkInt = typeof Platform.Version === 'number' ? Platform.Version : Number(Platform.Version);
        if (!Number.isFinite(sdkInt) || sdkInt < 26) {
            return 'Embedded MPV requires Android 8.0 (API 26) or newer.';
        }

        if (Constants.executionEnvironment === 'storeClient') {
            return 'Embedded MPV is not available in Expo Go. Install a development build with `npx expo run:android`.';
        }

        const diagnostics = getMpvNativeViewAvailabilityDiagnostics();
        if (diagnostics.reason) {
            return `Embedded MPV failed to load: ${diagnostics.reason}`;
        }
        return 'Embedded MPV native module is missing from this installed app build. Reinstall with `npx expo run:android`.';
    }, []);

    const shouldUseLocalProxyForPlayback = useCallback((upstreamUrl: string, stream: AddonStream | null): boolean => {
        if (serverConfig.streamingMode !== 'server') return false;
        if (upstreamUrl.startsWith('http://127.0.0.1:') || upstreamUrl.startsWith('http://localhost:')) {
            return false;
        }

        // Direct URLs returned from debrid for torrent-backed streams already play
        // correctly with headers, and proxying them through the local server has
        // been causing avoidable playback regressions.
        if (stream?.infoHash && !upstreamUrl.includes('/stream/torrent/')) {
            return false;
        }

        return true;
    }, [serverConfig.streamingMode]);

    const resolveStreamToUrl = useCallback(async (
        stream: AddonStream,
        attemptId: number,
    ): Promise<string | null> => {
        const streamIndex = allStreamsRef.current.findIndex(candidate => candidate === stream);
        const totalStreams = allStreamsRef.current.length;
        const isCurrentAttempt = () => attemptId === latestResolveAttemptIdRef.current;
        logPlayerEvent('info', `[Player] Resolving ${describeStream(stream, streamIndex, totalStreams)}`);
        try {
            const resolved = await resolvePlayableStreamUrl({
                stream,
                debridAccountCount: debridAccounts.length,
                resolveStream,
                streamTorrent,
                streamingMode: serverConfig.streamingMode,
                streamSelectionEnabled,
                maxFileSizeGB,
                defaultMaxSizeBytes: PREFERRED_SIZE_LIMIT,
                shouldContinue: isCurrentAttempt,
                onDebridFailures: (label, failures) => logDebridFailures(label, failures),
                onStep: (step) => {
                    if (step === 'direct-url') {
                        logPlayerEvent('info', `[Player] Source already has direct URL for ${describeStream(stream, streamIndex, totalStreams)}`);
                    } else if (step === 'debrid-resolve') {
                        logPlayerEvent('info', `[Player] Trying premium resolver for ${describeStream(stream, streamIndex, totalStreams)}`);
                    } else if (step === 'local-torrent') {
                        logPlayerEvent('info', `[Player] Trying local playback URL for ${describeStream(stream, streamIndex, totalStreams)}`);
                    } else if (step === 'backend-torrent') {
                        logPlayerEvent('info', `[Player] Trying backend stream URL for ${describeStream(stream, streamIndex, totalStreams)}`);
                    }
                },
                onWarn: (warning) => {
                    if (warning === 'local-torrent-unavailable') {
                        logPlayerEvent('warn', `[Player] Local playback URL unavailable for ${describeStream(stream, streamIndex, totalStreams)}`);
                    }
                },
                onError: (message, error) => {
                    logPlayerEvent('error', `[Player] ${message}`, { error: error instanceof Error ? error.message : String(error) });
                },
            });
            if (!isCurrentAttempt()) return null;
            if (resolved) {
                logPlayerEvent('info', `[Player] Resolution succeeded for ${describeStream(stream, streamIndex, totalStreams)}`);
                return resolved;
            }
        } catch (e) {
            logPlayerEvent('error', "[Player] Resolution error", { error: e instanceof Error ? e.message : String(e) });
        }
        logPlayerEvent('info', `[Player] Resolution failed for ${describeStream(stream, streamIndex, totalStreams)}`);
        return null;
    }, [debridAccounts, logDebridFailures, logPlayerEvent, resolveStream, serverConfig.streamingMode, streamTorrent]);

    const resolveStreamToUrlWithTimeout = useCallback(async (
        stream: AddonStream,
        options?: { supersede?: boolean },
    ): Promise<{ url: string | null; isStale: boolean; attemptId: number }> => {
        const streamIndex = allStreamsRef.current.findIndex(candidate => candidate === stream);
        const totalStreams = allStreamsRef.current.length;
        const startedAt = Date.now();
        const shouldSupersede = options?.supersede !== false;
        const attemptId = shouldSupersede
            ? latestResolveAttemptIdRef.current + 1
            : latestResolveAttemptIdRef.current;
        if (shouldSupersede) {
            latestResolveAttemptIdRef.current = attemptId;
        }
        resolveInFlightCountRef.current += 1;

        let timeoutHandle: ReturnType<typeof setTimeout> | null = null;
        let settled = false;

        try {
            const url = await new Promise<string | null>(resolve => {
                timeoutHandle = setTimeout(() => {
                    if (settled) return;
                    settled = true;
                    logPlayerEvent('warn', `[Player] Resolve timed out after ${STREAM_RESOLVE_TIMEOUT_MS}ms for ${describeStream(stream, streamIndex, totalStreams)}`);
                    resolve(null);
                }, STREAM_RESOLVE_TIMEOUT_MS);

                resolveStreamToUrl(stream, attemptId)
                    .then(result => {
                        if (settled) return;
                        settled = true;
                        if (timeoutHandle) {
                            clearTimeout(timeoutHandle);
                            timeoutHandle = null;
                        }
                        logPlayerEvent('info', `[Player] Resolve finished in ${Date.now() - startedAt}ms for ${describeStream(stream, streamIndex, totalStreams)} success=${Boolean(result)}`);
                        resolve(result);
                    })
                    .catch(error => {
                        if (settled) return;
                        settled = true;
                        if (timeoutHandle) {
                            clearTimeout(timeoutHandle);
                            timeoutHandle = null;
                        }
                        logPlayerEvent('error', `[Player] Resolve wrapper error for ${describeStream(stream, streamIndex, totalStreams)}`, { error: error instanceof Error ? error.message : String(error) });
                        resolve(null);
                    });
            });
            const isStale = shouldSupersede && attemptId !== latestResolveAttemptIdRef.current;
            if (isStale) {
                logPlayerEvent('info', `[Player] Ignoring stale resolve result for ${describeStream(stream, streamIndex, totalStreams)} attempt=${attemptId} latest=${latestResolveAttemptIdRef.current}`);
            }
            return { url, isStale, attemptId };
        } finally {
            if (timeoutHandle) {
                clearTimeout(timeoutHandle);
            }
            resolveInFlightCountRef.current = Math.max(0, resolveInFlightCountRef.current - 1);
        }
    }, [resolveStreamToUrl, logPlayerEvent]);

    const playStreamObject = async (
        upstreamUrl: string,
        stream: AddonStream | null,
        options?: { resumeAtSec?: number },
    ) => {
        if (!playerRef.current || !isMountedRef.current) return;
        rememberAudioTrackPreference(playerRef.current.audioTrack);
        rememberSubtitleTrackPreference(playerRef.current.subtitleTrack);
        sourceLoadedRef.current = false;
        firstFrameRenderedRef.current = false;
        clearFirstFrameTimer();
        validatedDurationForCurrentSourceRef.current = false;
        preferredSaveBlockedRef.current = false;
        preferredSavedForCurrentSourceRef.current = false;
        setDuration(0);
        embeddedMpvFallbackAttemptedRef.current = false;
        if (typeof options?.resumeAtSec === 'number' && options.resumeAtSec > 0) {
            pendingResumeRef.current = options.resumeAtSec;
            didApplyResumeRef.current = false;
        }
        setActiveStream(stream);
        activeStreamRef.current = stream;
        activeStreamIndexRef.current = stream
            ? allStreamsRef.current.findIndex(candidate => candidate === stream)
            : -1;
        logPlayerEvent('info', `[Player] Loading player source for ${describeStream(stream, activeStreamIndexRef.current, allStreamsRef.current.length)}`);
        const playableUpstreamUrl = await maybeUnrestrictDirectUrl(upstreamUrl, stream);
        const shouldUseLocalProxy = shouldUseLocalProxyForPlayback(playableUpstreamUrl, stream);
        if (serverConfig.streamingMode === 'server' && !shouldUseLocalProxy) {
            logPlayerEvent('info', `[Player] Using upstream URL directly for ${describeStream(stream, activeStreamIndexRef.current, allStreamsRef.current.length)} to avoid unnecessary local proxying`);
        }
        const playbackUrl = shouldUseLocalProxy
            ? await createLocalProxyUrl(playableUpstreamUrl, MAGIC_HEADERS)
            : playableUpstreamUrl;
        setUpstreamResolvedUrl(playableUpstreamUrl);
        setResolvedUrl(playbackUrl);
        const resumeAtSec = Math.max(options?.resumeAtSec ?? 0, player.currentTime || 0, playbackPosRef.current || 0);

        if (!pictureInPictureEnabled && shouldPreferMpvPlayback(playbackUrl, stream)) {
            logPlayerEvent('info', `[Player] Routing compatibility-heavy source to MPV for playback: ${describeStream(stream, activeStreamIndexRef.current, allStreamsRef.current.length)}`);
            if (launchEmbeddedMpv(playbackUrl, resumeAtSec, 'compatibility')) {
                return;
            }
        }

        if (!shouldUseEmbeddedVideoPlayer) {
            if (launchEmbeddedMpv(playbackUrl, resumeAtSec, 'default')) {
                return;
            }
        }

        try {
            const playbackSource = playbackUrl === playableUpstreamUrl
                ? {
                    uri: playableUpstreamUrl,
                    headers: MAGIC_HEADERS,
                    useCaching: true,
                    contentType: inferMedia3ContentType(playableUpstreamUrl),
                }
                : {
                    uri: playbackUrl,
                    useCaching: true,
                    contentType: inferMedia3ContentType(playbackUrl),
                };
            await playerRef.current.replaceAsync(
                playbackSource
            );
            if (isMountedRef.current) {
                logPlayerEvent('info', `[Player] Source replace complete for ${describeStream(stream, activeStreamIndexRef.current, allStreamsRef.current.length)}`);
                playerRef.current.play();
            }
        } catch (e) {
            logPlayerEvent('error', "[Player] playStreamObject error", { error: e instanceof Error ? e.message : String(e) });
        }
    };

    useEffect(() => {
        if (!pendingSameSourceRetry) return;

        let cancelled = false;
        const retry = async () => {
            if (cancelled || !isMountedRef.current) return;
            const retryPayload = pendingSameSourceRetry;
            setPendingSameSourceRetry(null);
            await playStreamObject(retryPayload.upstreamUrl, retryPayload.stream, { resumeAtSec: retryPayload.resumeAtSec });
            isHandlingErrorRef.current = false;
        };

        void retry();
        return () => {
            cancelled = true;
        };
    }, [pendingSameSourceRetry, playStreamObject]);

    const clearFirstFrameTimer = useCallback(() => {
        if (firstFrameTimerRef.current) {
            clearTimeout(firstFrameTimerRef.current);
            firstFrameTimerRef.current = null;
        }
    }, []);

    const handlePlayerError = useCallback(async (failedStream?: AddonStream | null) => {
        if (isHandlingErrorRef.current) return;
        isHandlingErrorRef.current = true;
        setLoading(true);
        setIsError(false);
        const resumeAtSec = Math.max(
            player.currentTime || 0,
            playbackPosRef.current || 0,
        );

        const streams = allStreamsRef.current;
        const streamThatFailed = failedStream ?? activeStreamRef.current;
        const failedIndex = streamThatFailed
            ? streams.findIndex(stream => stream === streamThatFailed)
            : -1;
        const currentIdx = Math.max(activeStreamIndexRef.current, failedIndex);
        const playbackFailure = sourceLoadedRef.current;

        recordFailedStream(streamThatFailed, { playbackFailure });
        maybeSuggestCompatibilityMode(playbackFailure);
        clearFirstFrameTimer();
        sourceLoadedRef.current = false;
        firstFrameRenderedRef.current = false;
        validatedDurationForCurrentSourceRef.current = false;
        preferredSaveBlockedRef.current = true;
        preferredSavedForCurrentSourceRef.current = false;

        if (trySameSourceCompatibilityRetry(streamThatFailed, resumeAtSec)) {
            isHandlingErrorRef.current = false;
            return;
        }

        logPlayerEvent('warn', `[Player] Advancing source after failure. currentIdx=${currentIdx} total=${streams.length}`);

        const rankedCandidates = getRankedStreams(streams).filter(candidate => {
            if (candidate === streamThatFailed) return false;
            const key = streamIdentityKey(candidate);
            return !key || !failureStatsRef.current.exactKeys.has(key);
        });

        for (const nextStream of rankedCandidates) {
            if (!isMountedRef.current) break;
            const nextIdx = streams.findIndex(stream => stream === nextStream);
            logPlayerEvent('info', `[Player] Trying next source ${describeStream(nextStream, nextIdx, streams.length)}`);
            showTryingSourceMessage(nextIdx + 1, streams.length);
            const { url, isStale } = await resolveStreamToUrlWithTimeout(nextStream);
            if (isStale) {
                isHandlingErrorRef.current = false;
                return;
            }
            if (url && isMountedRef.current) {
                await playStreamObject(url, nextStream, { resumeAtSec });
                isHandlingErrorRef.current = false;
                return;
            }
        }

        const fallbackUrl = resolvedUrlRef.current ?? upstreamResolvedUrlRef.current;
        if (!embeddedMpvFallbackAttemptedRef.current && fallbackUrl) {
            embeddedMpvFallbackAttemptedRef.current = true;
            showRecoveryMessage('Switching to embedded MPV for this source...');
            if (launchEmbeddedMpv(fallbackUrl, resumeAtSec, 'fallback')) {
                isHandlingErrorRef.current = false;
                return;
            }
        }

        setLoading(false);
        setIsError(true);
        isHandlingErrorRef.current = false;
    }, [clearFirstFrameTimer, getRankedStreams, launchEmbeddedMpv, logPlayerEvent, maybeSuggestCompatibilityMode, navigation, openExternalPlayer, player, playStreamObject, recordFailedStream, resolveStreamToUrlWithTimeout, showRecoveryMessage, trySameSourceCompatibilityRetry]);

    const scheduleFirstFrameWatchdog = useCallback(() => {
        clearFirstFrameTimer();
        if (!resolvedUrlRef.current || firstFrameRenderedRef.current || !sourceLoadedRef.current) {
            return;
        }

        firstFrameTimerRef.current = setTimeout(() => {
            firstFrameTimerRef.current = null;
            if (!isMountedRef.current || firstFrameRenderedRef.current || !sourceLoadedRef.current) {
                return;
            }
            logPlayerEvent('warn', `[Player] No video frame rendered within ${FIRST_FRAME_RENDER_TIMEOUT_MS}ms, trying another source`);
            preferredSaveBlockedRef.current = true;
            showRecoveryMessage('Audio made it. Picture called in sick. Trying another...');
            if (!shouldHandlePlayerFailure('missing first frame')) return;
            handlePlayerError(activeStreamRef.current);
        }, FIRST_FRAME_RENDER_TIMEOUT_MS);
    }, [clearFirstFrameTimer, handlePlayerError, logPlayerEvent, shouldHandlePlayerFailure]);

    const validateCurrentSourceDuration = useCallback((): boolean => {
        if (validatedDurationForCurrentSourceRef.current) return false;
        if (!sourceLoadedRef.current) return false;
        if (!shortSourceFilterEnabled) {
            validatedDurationForCurrentSourceRef.current = true;
            return false;
        }

        const currentDuration = player.duration;
        if (!currentDuration || currentDuration <= 0) return false;

        if (currentDuration < MIN_ACCEPTABLE_STREAM_DURATION_SEC) {
            const active = activeStreamRef.current;
            const activeIndex = active
                ? allStreamsRef.current.findIndex(stream => stream === active)
                : -1;
            logPlayerEvent('warn',
                `[Player] Rejecting short source (${Math.round(currentDuration)}s < ${MIN_ACCEPTABLE_STREAM_DURATION_SEC}s) for ${describeStream(active, activeIndex, allStreamsRef.current.length)}`,
            );
            preferredSaveBlockedRef.current = true;
            showRecoveryMessage('That cut ended way too early. Finding the full movie...');
            handlePlayerError(active);
            return true;
        }

        validatedDurationForCurrentSourceRef.current = true;
        return false;
    }, [handlePlayerError, player, logPlayerEvent, shortSourceFilterEnabled]);

    const cancelPendingFallbacks = useCallback((reason: string) => {
        if (!isHandlingErrorRef.current && resolveInFlightCountRef.current === 0) {
            return;
        }
        latestResolveAttemptIdRef.current += 1;
        isHandlingErrorRef.current = false;
        logPlayerEvent('info', `[Player] Cancelling pending fallback attempts because ${reason}`);
    }, [logPlayerEvent]);

    const toggleContentFit = useCallback(() => {
        setContentFit(prev => {
            if (prev === 'contain') return 'cover'; // Crop
            if (prev === 'cover') return 'fill';    // Stretch
            return 'contain';                       // Fit
        });
    }, []);

    const getCastableUrl = useCallback((): string | null => {
        const preferredUrl = upstreamResolvedUrlRef.current ?? resolvedUrlRef.current;
        if (!preferredUrl) return null;
        if (/^https?:\/\/(127\.0\.0\.1|localhost)([:/]|$)/i.test(preferredUrl)) {
            return null;
        }
        return preferredUrl;
    }, []);

    const buildCastSignature = useCallback((): string | null => {
        const castUrl = getCastableUrl();
        if (!castUrl) return null;

        const streamKey = activeStreamRef.current?.infoHash
            ?? activeStreamRef.current?.url
            ?? activeStreamRef.current?.behaviorHints?.filename
            ?? activeStreamRef.current?.title
            ?? activeStreamRef.current?.name
            ?? '';
        const startAt = Math.max(0, playbackPosRef.current || player?.currentTime || 0);
        return `${castUrl}|${streamKey}|${Math.floor(startAt)}`;
    }, [getCastableUrl, player?.currentTime]);

    const loadCurrentMediaToCast = useCallback(async (
        client: { loadMedia: (request: any) => Promise<void> },
        options?: { force?: boolean; openExpandedControls?: boolean },
    ) => {
        const castUrl = getCastableUrl();
        if (!castUrl) {
            castPendingLoadRef.current = false;
            setCastErrorMessage('This stream is not available as a remote URL yet, so it cannot be sent to a Cast device.');
            return false;
        }

        const signature = buildCastSignature();
        if (!signature) {
            castPendingLoadRef.current = false;
            setCastErrorMessage('This stream could not be prepared for casting.');
            return false;
        }
        if (!options?.force && signature === lastCastSignatureRef.current) {
            const expandedShown = await GoogleCast.showExpandedControls().catch(() => false);
            castPendingLoadRef.current = false;
            return expandedShown;
        }
        if (castLoadingRef.current) {
            return false;
        }

        castLoadingRef.current = true;
        setLoading(true);
        updateLoadingMessage('Sending this stream to your Cast device...', { immediate: true });

        try {
            const artwork = loadingArtworkUri ? [{ url: loadingArtworkUri }] : undefined;
            const startTime = Math.max(0, playbackPosRef.current || player.currentTime || 0);
            await client.loadMedia({
                autoplay: true,
                startTime,
                mediaInfo: {
                    contentId: imdbId ?? String(movieId),
                    contentType: inferCastContentType(castUrl),
                    contentUrl: castUrl,
                    metadata: {
                        type: 'generic',
                        title: title ?? 'Unknown title',
                        subtitle: activeStreamRef.current?.title
                            || activeStreamRef.current?.behaviorHints?.filename
                            || activeStreamRef.current?.name
                            || undefined,
                        releaseDate: typeof year === 'number' ? `${year}-01-01` : undefined,
                        images: artwork,
                    },
                    streamDuration: duration > 0 ? duration : undefined,
                    streamType: MediaStreamType.BUFFERED,
                },
            });
            lastCastSignatureRef.current = signature;
            castPendingLoadRef.current = false;
            logPlayerEvent('info', `[Player] Cast handoff started for ${describeStream(activeStreamRef.current, activeStreamIndexRef.current, allStreamsRef.current.length)}`);
            pauseEmbeddedPlayer('handing off to cast');
            setIsPlaying(false);
            setLoading(false);
            if (options?.openExpandedControls !== false) {
                void GoogleCast.showExpandedControls().catch(() => false);
            }
            ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP);
            flushProgressAndGoBack();
            return true;
        } catch (error) {
            castPendingLoadRef.current = false;
            logPlayerEvent('warn', '[Player] Cast handoff failed', {
                error: error instanceof Error ? error.message : String(error),
            });
            setLoading(false);
            setCastErrorMessage('Could not hand this stream off to the selected Cast device. Please try another source or reconnect the device.');
            return false;
        } finally {
            castLoadingRef.current = false;
        }
    }, [
        buildCastSignature,
        duration,
        getCastableUrl,
        imdbId,
        loadingArtworkUri,
        logPlayerEvent,
        movieId,
        navigation,
        player,
        title,
        updateLoadingMessage,
        year,
    ]);

    const handleCastPress = useCallback(async () => {
        if (!castNativeButtonAvailable) {
            setCastErrorMessage('Casting is not available in this app build yet. Rebuild the app after adding the Google Cast native plugin to enable it.');
            return;
        }

        const castUrl = getCastableUrl();
        if (!castUrl) {
            setCastErrorMessage('This stream is not available for casting yet. Let playback finish resolving first.');
            return;
        }

        if (castState === CastState.CONNECTED) {
            try {
                const session = await GoogleCast.getSessionManager().getCurrentCastSession();
                const client = session?.getClient?.() ?? session?.client ?? null;
                if (client) {
                    await loadCurrentMediaToCast(client, { force: true });
                    return;
                }
            } catch (error) {
                logPlayerEvent('warn', '[Player] Failed to access active Cast session', {
                    error: error instanceof Error ? error.message : String(error),
                });
            }
        }

        castPendingLoadRef.current = true;
        try {
            const dialogShown = await GoogleCast.showCastDialog();
            if (!dialogShown) {
                castPendingLoadRef.current = false;
                setCastErrorMessage('No Cast device picker was available. Make sure Google Cast is enabled in this build and a compatible device is on the same network.');
            }
        } catch (error) {
            castPendingLoadRef.current = false;
            logPlayerEvent('warn', '[Player] Failed to open Cast device picker', {
                error: error instanceof Error ? error.message : String(error),
            });
            setCastErrorMessage('Could not open the Cast device picker on this device.');
        }
    }, [castNativeButtonAvailable, castState, getCastableUrl, loadCurrentMediaToCast, logPlayerEvent]);

    const handleEmbeddedMpvPress = useCallback(() => {
        const playbackUrl = resolvedUrlRef.current ?? upstreamResolvedUrlRef.current;
        if (!playbackUrl) {
            setExternalPlayerErrorMessage('This source is still resolving. Try MPV once playback has started.');
            return;
        }
        const resumeAtSec = Math.max(player?.currentTime || 0, playbackPosRef.current || 0);
        if (launchEmbeddedMpv(playbackUrl, resumeAtSec, 'manual')) {
            return;
        }
        setIsHandingOffToMpv(false);
        setExternalPlayerErrorMessage(getEmbeddedMpvUnavailableMessage());
    }, [getEmbeddedMpvUnavailableMessage, launchEmbeddedMpv, player?.currentTime]);

    const handleExternalPlayerPress = useCallback(async () => {
        const playbackUrl = resolvedUrlRef.current ?? upstreamResolvedUrlRef.current;
        if (!playbackUrl) {
            setExternalPlayerErrorMessage('This source is still resolving. Try opening externally once playback has started.');
            return;
        }

        try {
            await openExternalPlayer(playbackUrl, 'manual', activeStreamRef.current);
            setLoading(false);
            ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP);
            flushProgressAndGoBack();
        } catch (error) {
            logPlayerEvent('warn', '[Player] Manual external launch failed', {
                error: error instanceof Error ? error.message : String(error),
            });
            setExternalPlayerErrorMessage('Could not open an external player for this stream. Install MPV, VLC, MX Player, or Just Player and try again.');
        }
    }, [logPlayerEvent, navigation, openExternalPlayer]);

    const maybeUnrestrictDirectUrl = useCallback(async (
        upstreamUrl: string,
        stream: AddonStream | null,
    ): Promise<string> => {
        if (!/^https?:\/\//i.test(upstreamUrl)) return upstreamUrl;
        if (upstreamUrl.startsWith('http://127.0.0.1:') || upstreamUrl.startsWith('http://localhost:')) {
            return upstreamUrl;
        }
        if (stream?.infoHash) return upstreamUrl;
        if (debridAccounts.length === 0) return upstreamUrl;

        try {
            logPlayerEvent('info', `[Player] Trying Debrid unrestriction for direct URL from ${describeStream(stream, activeStreamIndexRef.current, allStreamsRef.current.length)}`);
            const unrestricted = await unrestrictLink(upstreamUrl);
            if (unrestricted?.url) {
                logPlayerEvent('info', `[Player] Direct URL unrestricted via ${unrestricted.provider}`);
                return unrestricted.url;
            }
            logPlayerEvent('info', '[Player] Debrid unrestriction not available for direct URL, using upstream URL');
            return upstreamUrl;
        } catch (error) {
            logDebridFailures('Debrid unrestriction failed', (error as any)?.failures);
            logPlayerEvent('warn', '[Player] Debrid unrestriction failed for direct URL, using upstream URL', {
                error: error instanceof Error ? error.message : String(error),
            });
            return upstreamUrl;
        }
    }, [debridAccounts.length, logDebridFailures, logPlayerEvent, unrestrictLink]);

    const diagnosticsRows = [
        { label: 'Source', value: activeStream?.title || activeStream?.behaviorHints?.filename || activeStream?.name || 'None' },
        { label: 'Provider', value: activeStream?.addonName || activeStream?.addonId || 'Unknown' },
        { label: 'Cached By', value: activeStream?.cachedBy?.length ? activeStream.cachedBy.join(', ') : 'No cached providers' },
        { label: 'Path', value: describePlaybackPath(resolvedUrl, upstreamResolvedUrl) },
        { label: 'Cast', value: castState ?? 'unavailable' },
        { label: 'Embedded MPV', value: mpvNativeViewAvailable ? 'available' : 'unavailable' },
        { label: 'Upstream Host', value: getUrlHost(upstreamResolvedUrl) },
        { label: 'Playback Host', value: getUrlHost(resolvedUrl) },
        { label: 'Decoder', value: effectiveDecoderMode ?? 'n/a' },
        { label: 'Surface', value: effectiveSurfaceType ?? 'n/a' },
        { label: 'Duration', value: duration > 0 ? `${Math.round(duration)}s` : 'Unknown' },
        { label: 'Tracks', value: `${player.availableVideoTracks.length} video • ${player.availableAudioTracks.length} audio • ${player.availableSubtitleTracks.length} subs` },
        { label: 'Status', value: player.status },
    ];

    const playerMenuItems: Array<{ key: PlayerDrawerSection; icon: keyof typeof Ionicons.glyphMap; label: string; enabled?: boolean }> = [
        { key: 'sources', icon: 'layers-outline', label: 'Sources', enabled: allStreams.length > 0 },
        {
            key: 'tracks',
            icon: 'options-outline',
            label: 'Tracks',
            enabled: player.availableAudioTracks.length > 0 || player.availableSubtitleTracks.length > 0,
        },
        { key: 'speed', icon: 'speedometer-outline', label: 'Speed' },
        { key: 'screen', icon: 'expand-outline', label: 'Screen' },
        { key: 'diagnostics', icon: 'pulse-outline', label: 'Info' },
    ];

    const activeDrawerTitle = (
        {
            sources: 'Available Sources',
            tracks: 'Audio & Subtitles',
            speed: 'Playback Speed',
            screen: 'Screen Fit Mode',
            diagnostics: 'Playback Diagnostics',
        } satisfies Record<PlayerDrawerSection, string>
    )[activeDrawerSection];

    useEffect(() => {
        if (!castNativeModuleAvailable) {
            setCastState('unavailable');
            return;
        }

        let cancelled = false;
        void GoogleCast.getCastState()
            .then((state: any) => {
                if (!cancelled) setCastState(state);
            })
            .catch(() => {
                if (!cancelled) setCastState('unavailable');
            });

        const castStateSub = GoogleCast.onCastStateChanged((state: any) => {
            if (!cancelled) setCastState(state);
        });

        return () => {
            cancelled = true;
            castStateSub.remove();
        };
    }, [castNativeModuleAvailable]);

    useEffect(() => {
        if (!castNativeModuleAvailable) return;
        if (!castPendingLoadRef.current) return;
        if (castState !== CastState.CONNECTED) return;

        let cancelled = false;
        void GoogleCast.getSessionManager()
            .getCurrentCastSession()
            .then((session: any) => {
                if (cancelled) return;
                const client = session?.getClient?.() ?? session?.client ?? null;
                if (client) {
                    void loadCurrentMediaToCast(client);
                }
            })
            .catch((error: any) => {
                if (cancelled) return;
                logPlayerEvent('warn', '[Player] Failed to access Cast session after connect', {
                    error: error instanceof Error ? error.message : String(error),
                });
                castPendingLoadRef.current = false;
                setCastErrorMessage('A Cast device connected, but the app could not start the remote playback session.');
            });

        return () => {
            cancelled = true;
        };
    }, [castNativeModuleAvailable, castState, loadCurrentMediaToCast, logPlayerEvent]);

    // Initial Load
    useEffect(() => {
        logPlayerEvent('info', `[Player] Initializing for: "${title}" (imdbId: ${imdbId}, movieId: ${movieId})`);
        if (Platform.OS === 'android') {
            logPlayerEvent('info', `[Player] Playback config decoder=${effectiveDecoderMode} surface=${effectiveSurfaceType}`);
        }
        const init = async () => {
            showSearchingMessage();
            if (!paramUrl && !shouldUseEmbeddedVideoPlayer && !paramOpenSourcesOnStart) {
                const preferredKey = lastStreamKey(user?.uid ?? null, type, imdbId ?? String(movieId));
                logPlayerEvent('info', '[Player] Handing off source resolution directly to MPV');
                skipPortraitOnUnmountRef.current = true;
                navigation.replace('MpvPlayer', {
                    streamUrl: null,
                    headers: MAGIC_HEADERS,
                    title,
                    year,
                    type,
                    synopsis,
                    titleLogo: paramTitleLogo,
                    backdrop: paramBackdrop,
                    poster: paramPoster,
                    initialLoadingMessage: loadingMsg,
                    resumeFrom: Math.max(0, Number(paramResumeFrom) || 0),
                    forceStartFromBeginning,
                    resolveOnMount: true,
                    resolverMovieId: movieId,
                    resolverImdbId: imdbId,
                    resolverType: type,
                    preferredSourceIndex: paramPreferredSourceIndex,
                    preferredSourceIdentity: paramPreferredSourceIdentity,
                    returnToPlayerParams: {
                        movieId,
                        imdbId,
                        type,
                        title,
                        year,
                        synopsis,
                        titleLogo: paramTitleLogo,
                        backdrop: paramBackdrop,
                        poster: paramPoster,
                        progressKey: paramProgressKey,
                    },
                    rememberedSourceKey: preferredKey,
                });
                return;
            }

            if (paramUrl) {
                await playStreamObject(paramUrl, paramActiveStream ?? null);
                setLoading(false);
                return;
            }

            try {
                failureStatsRef.current = createSessionFailureStats();
                const fetched = await fetchStreams(type === 'tv' ? 'series' : 'movie', imdbId ?? String(movieId));
                const sorted = getRankedStreams(fetched);
                logPlayerEvent('info', `[Player] Fetched ${fetched.length} candidate sources, sorted ${sorted.length}`);
                allStreamsRef.current = sorted;
                setAllStreams(sorted);
                if (paramOpenSourcesOnStart) {
                    logPlayerEvent('info', '[Player] Source picker requested on start; skipping autoplay');
                    return;
                }
                if (sorted.length > 0) {
                    const preferredIdentity = typeof paramPreferredSourceIdentity === 'string'
                        ? normalizeStreamText(paramPreferredSourceIdentity)
                        : '';
                    const preferredIndexValue = typeof paramPreferredSourceIndex === 'number'
                        ? paramPreferredSourceIndex
                        : typeof paramPreferredSourceIndex === 'string'
                            ? Number(paramPreferredSourceIndex)
                            : NaN;

                    let preferredStream: AddonStream | null = null;
                    if (preferredIdentity) {
                        preferredStream = sorted.find(stream => streamIdentityKey(stream) === preferredIdentity) ?? null;
                    }
                    if (!preferredStream && Number.isFinite(preferredIndexValue)) {
                        const normalizedIndex = Math.trunc(preferredIndexValue);
                        if (normalizedIndex >= 0 && normalizedIndex < sorted.length) {
                            preferredStream = sorted[normalizedIndex];
                        }
                    }

                    let remembered: RememberedStreamChoice | null = null;
                    try {
                        const rememberedRaw = await Storage.getItem(
                            lastStreamKey(user?.uid ?? null, type, imdbId ?? String(movieId)),
                        );
                        remembered = rememberedRaw ? JSON.parse(rememberedRaw) : null;
                    } catch {
                        remembered = null;
                    }

                    if (remembered) {
                        logPlayerEvent('info', `[Player] Loaded remembered source preference for ${type}:${imdbId ?? String(movieId)}`);
                    } else {
                        logPlayerEvent('info', `[Player] No remembered source preference for ${type}:${imdbId ?? String(movieId)}`);
                    }
                    const selectedStream = preferredStream
                        ?? sorted.find(stream => streamMatchesRemembered(stream, remembered))
                        ?? sorted[0];
                    if (preferredStream) {
                        logPlayerEvent('info', `[Player] Explicit source requested from MPV selected: ${describeStream(selectedStream, sorted.findIndex(stream => stream === selectedStream), sorted.length)}`);
                    } else {
                        logPlayerEvent('info', `[Player] Preferred source selected: ${describeStream(selectedStream, sorted.findIndex(stream => stream === selectedStream), sorted.length)}`);
                    }
                    activeStreamRef.current = selectedStream;
                    activeStreamIndexRef.current = sorted.findIndex(stream => stream === selectedStream);
                    const { url, isStale } = await resolveStreamToUrlWithTimeout(selectedStream);
                    if (isStale) {
                        return;
                    }
                    if (url) { 
                        await playStreamObject(url, selectedStream);
                    } else {
                        handlePlayerError(selectedStream);
                    }
                } else {
                    handlePlayerError();
                }
            } catch (e) { 
                handlePlayerError(); 
            } finally { 
                setLoading(false); 
            }
        };
        init();
        return () => { 
            isMountedRef.current = false;
        };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [effectiveDecoderMode, effectiveSurfaceType, logPlayerEvent]);

    // Watchdog & Event Listeners
    useEffect(() => {
        const watchdog = setInterval(() => {
            if (player.status === 'loading' && !isHandlingErrorRef.current && !sourceLoadedRef.current) {
                if (!shouldHandlePlayerFailure('watchdog loading failure')) return;
                handlePlayerError();
            }
        }, STREAM_STALL_MS);
        
        const sub = player.addListener('statusChange', ({ status }) => {
            logPlayerEvent('info', `[Player] Status changed: ${status}`);
            if (status === 'readyToPlay') {
                if (firstFrameRenderedRef.current) {
                    cancelPendingFallbacks('current source rendered its first frame');
                    setLoading(false);
                } else if (sourceLoadedRef.current) {
                    scheduleFirstFrameWatchdog();
                }
            }
            if (status === 'error') {
                clearFirstFrameTimer();
                if (!shouldHandlePlayerFailure('status error')) return;
                handlePlayerError();
            }
        });

        // Source Loaded logic - ensure we have working track selections
        const loadSub = player.addListener('sourceLoad', ({ availableAudioTracks, availableSubtitleTracks, availableVideoTracks, duration: loadedDuration }) => {
            sourceLoadedRef.current = true;
            logPlayerEvent('info',
                `[Player] Source loaded with ${availableVideoTracks?.length ?? 0} video tracks, ${availableAudioTracks?.length ?? 0} audio tracks and ${availableSubtitleTracks?.length ?? 0} subtitle tracks (duration: ${Math.round(loadedDuration ?? 0)}s)`,
            );
            if (!availableVideoTracks || availableVideoTracks.length === 0) {
                logPlayerEvent('warn', "[Player] Source loaded but no video tracks found, skipping source...");
                preferredSaveBlockedRef.current = true;
                showRecoveryMessage('That reel forgot to bring video. Trying another...');
                if (!shouldHandlePlayerFailure('missing video tracks')) return;
                handlePlayerError();
                return;
            }
            if (!availableAudioTracks || availableAudioTracks.length === 0) {
                logPlayerEvent('warn', "[Player] Source loaded but no audio tracks found, skipping source...");
                preferredSaveBlockedRef.current = true;
                showRecoveryMessage('Silent cinema was not the plan. Trying another...');
                if (!shouldHandlePlayerFailure('missing audio tracks')) return;
                handlePlayerError();
                return;
            }

            applyPreferredTrackSelection(availableAudioTracks, availableSubtitleTracks);

            if (validateCurrentSourceDuration()) {
                return;
            }
            scheduleFirstFrameWatchdog();

            const resumeTarget = pendingResumeRef.current;
            if (!didApplyResumeRef.current && typeof resumeTarget === 'number' && resumeTarget > 0) {
                const safeTarget = player.duration > 0
                    ? Math.min(resumeTarget, Math.max(0, player.duration - 5))
                    : resumeTarget;
                player.currentTime = safeTarget;
                lastUiTimeRef.current = normalizePlaybackTime(safeTarget);
                setCurrentTime(safeTarget);
                playbackPosRef.current = safeTarget;
                lastProgressSaveRef.current = safeTarget;
                didApplyResumeRef.current = true;
            }

            preferredSaveStartPositionRef.current = player.currentTime;
        });

        return () => {
            clearInterval(watchdog);
            clearFirstFrameTimer();
            sub.remove();
            loadSub.remove();
        };
    }, [player, cancelPendingFallbacks, clearFirstFrameTimer, handlePlayerError, scheduleFirstFrameWatchdog, shouldHandlePlayerFailure, validateCurrentSourceDuration, logPlayerEvent]);

    useEffect(() => {
        const sub = player.addListener('videoTrackChange', ({ videoTrack, oldVideoTrack }) => {
            const nextLabel = videoTrack?.id ?? videoTrack?.mimeType ?? 'none';
            const prevLabel = oldVideoTrack?.id ?? oldVideoTrack?.mimeType ?? 'none';
            logPlayerEvent('info', `[Player] Video track changed: ${prevLabel} -> ${nextLabel}`);
        });
        return () => sub.remove();
    }, [player, logPlayerEvent]);

    useEffect(() => {
        const sub = player.addListener('timeUpdate', () => {
            validateCurrentSourceDuration();
        });
        return () => sub.remove();
    }, [player, validateCurrentSourceDuration]);

    useEffect(() => {
        const audioChangeSub = player.addListener('audioTrackChange', ({ audioTrack }) => {
            if (audioTrack) {
                rememberAudioTrackPreference(audioTrack);
            }
        });
        const subtitleChangeSub = player.addListener('subtitleTrackChange', ({ subtitleTrack }) => {
            if (subtitleTrack) {
                rememberSubtitleTrackPreference(subtitleTrack);
            }
        });
        const availableAudioTracksSub = player.addListener('availableAudioTracksChange', ({ availableAudioTracks }) => {
            if (!sourceLoadedRef.current) return;
            applyPreferredTrackSelection(availableAudioTracks, player.availableSubtitleTracks);
        });
        const availableSubtitleTracksSub = player.addListener('availableSubtitleTracksChange', ({ availableSubtitleTracks }) => {
            if (!sourceLoadedRef.current) return;
            applyPreferredTrackSelection(player.availableAudioTracks, availableSubtitleTracks);
        });
        return () => {
            audioChangeSub.remove();
            subtitleChangeSub.remove();
            availableAudioTracksSub.remove();
            availableSubtitleTracksSub.remove();
        };
    }, [applyPreferredTrackSelection, player, rememberAudioTrackPreference, rememberSubtitleTrackPreference]);

    useEffect(() => {
        if (!paramOpenSourcesOnStart) return;
        if (didAutoOpenSourcesRef.current) return;
        if (allStreams.length === 0) return;
        didAutoOpenSourcesRef.current = true;
        setShowPlayerDrawer(true);
        setShowSources(true);
        setShowControls(true);
    }, [allStreams.length, paramOpenSourcesOnStart]);

    // Progress Tracking
    useEffect(() => {
        const sub = player.addListener('timeUpdate', async ({ currentTime }) => {
            const dur = player.duration;
            playbackPosRef.current = currentTime;
            playbackDurRef.current = dur;
            if (
                activeStreamRef.current
                && currentTime - preferredSaveStartPositionRef.current >= MIN_PREFERRED_STREAM_SAVE_SEC
            ) {
                maybePersistPreferredStream(`playback remained stable for ${MIN_PREFERRED_STREAM_SAVE_SEC}s`);
            }
            if (!paramProgressKey || !dur || currentTime - lastProgressSaveRef.current < 10) return;
            lastProgressSaveRef.current = currentTime;
            try {
                saveProgress(paramProgressKey, currentTime, dur);
                await saveToProgressIndex(storageOwnerId, {
                    key: paramProgressKey, tmdbId: Number(movieId), title: title ?? '', poster: paramPoster || undefined, backdrop: paramBackdrop || undefined, type: type || 'movie',
                    year: String(year ?? ''), progressPct: Math.round((currentTime / dur) * 100), positionSec: currentTime, durationSec: dur
                });
            } catch (e) { }
        });
        return () => sub.remove();
    }, [player, user, paramProgressKey, movieId, title, paramPoster, type, year, maybePersistPreferredStream, saveProgress]);

    // Trakt Intervals
    useEffect(() => {
        const shouldRunTraktPolling = isConnected && resolvedUrl && isPlaying && appStateRef.current === 'active';
        if (!shouldRunTraktPolling) {
            if (progressTimer.current) {
                clearInterval(progressTimer.current);
                progressTimer.current = null;
            }
            return;
        }
        
        if (!scrobbledStart.current) {
            scrobbledStart.current = true;
            const p = payloadRef.current;
            scrobble('start', buildPayload(p.movieId, p.type, p.title, p.year, 0));
        }
        
        if (progressTimer.current) clearInterval(progressTimer.current);
        progressTimer.current = setInterval(() => {
            if (player.duration > 0) {
                const p = payloadRef.current;
                scrobble('pause', buildPayload(p.movieId, p.type, p.title, p.year, Math.min(100, (player.currentTime / player.duration) * 100)));
            }
        }, 60_000);

        return () => {
            if (progressTimer.current) {
                clearInterval(progressTimer.current);
                progressTimer.current = null;
            }
        };
    }, [isConnected, isPlaying, resolvedUrl, player, scrobble]);

    // Trakt Stop on Unmount
    useEffect(() => {
        return () => {
            maybePersistPreferredStream('player screen closed', { force: true });
            if (scrobbledStart.current) {
                const dur = playbackDurRef.current;
                const cur = playbackPosRef.current;
                const p = payloadRef.current;
                scrobble('stop', buildPayload(p.movieId, p.type, p.title, p.year, dur > 0 ? Math.min(100, (cur / dur) * 100) : 0));
                scrobbledStart.current = false;
            }
        };
    }, [maybePersistPreferredStream, scrobble]);

    const renderDrawerContent = () => {
        switch (activeDrawerSection) {
            case 'sources':
                return allStreams.map((stream, i) => {
                    const isActive = isSameStream(activeStream, stream);
                    const parsed = parseStream(stream);
                    const badges = streamMetadataBadges(stream);
                    return (
                        <TouchableOpacity
                            key={`${stream.infoHash ?? stream.url ?? stream.title ?? i}`}
                            style={[
                                styles.menuItem,
                                isActive && {
                                    backgroundColor: `${theme.colors.accent}40`,
                                    borderColor: theme.colors.accent,
                                    borderWidth: 2,
                                },
                            ]}
                            onPress={async () => {
                                if (isActive) {
                                    closePlayerDrawer();
                                    return;
                                }
                                closePlayerDrawer();
                                setLoading(true);
                                showSwitchingMessage();
                                const resumeAtSec = Math.max(player.currentTime || 0, playbackPosRef.current || 0);
                                try {
                                    activeStreamRef.current = stream;
                                    activeStreamIndexRef.current = allStreams.findIndex(candidate => candidate === stream);
                                    const { url, isStale } = await resolveStreamToUrlWithTimeout(stream);
                                    if (isStale) return;
                                    if (url) {
                                        await playStreamObject(url, stream, { resumeAtSec });
                                    } else {
                                        setIsError(true);
                                        setLoading(false);
                                    }
                                } catch {
                                    setLoading(false);
                                    setIsError(true);
                                }
                            }}
                        >
                            <View style={styles.menuIconBox}>
                                <Ionicons name="cloud-download-outline" size={18} color={isActive ? theme.colors.accent : 'white'} />
                            </View>
                            <View style={styles.menuTextCol}>
                                <Text style={[styles.menuText, isActive && { fontWeight: '800' }]} numberOfLines={2}>
                                    {stream.title || `Source ${i + 1}`}
                                </Text>
                                <Text style={[styles.menuSubText, isActive && { color: 'white' }]} numberOfLines={1}>
                                    {parsed.providerLine || stream.name || 'Unknown Provider'}
                                </Text>
                                {!!parsed.specLine && (
                                    <Text style={styles.menuMetaLine} numberOfLines={1}>
                                        {parsed.specLine}
                                    </Text>
                                )}
                                {badges.length > 0 && (
                                    <View style={styles.badgeWrap}>
                                        {badges.map(badge => (
                                            <View key={`${stream.infoHash ?? stream.url ?? stream.title ?? i}-${badge}`} style={[styles.metaBadge, isActive && styles.metaBadgeActive]}>
                                                <Text style={[styles.metaBadgeText, isActive && styles.metaBadgeTextActive]}>
                                                    {badge}
                                                </Text>
                                            </View>
                                        ))}
                                        {isActive && (
                                            <View style={styles.activeSourceBadge}>
                                                <Text style={styles.activeSourceBadgeText}>Playing</Text>
                                            </View>
                                        )}
                                    </View>
                                )}
                                {badges.length === 0 && isActive && (
                                    <View style={styles.activeSourceBadge}>
                                        <Text style={styles.activeSourceBadgeText}>Playing</Text>
                                    </View>
                                )}
                            </View>
                            {isActive && <Ionicons name="radio-button-on" size={20} color={theme.colors.accent} />}
                        </TouchableOpacity>
                    );
                });
            case 'tracks':
                return (
                    <>
                        {player.availableAudioTracks.length > 0 && (
                            <View style={styles.menuSection}>
                                <View style={styles.sectionHeader}>
                                    <Ionicons name="musical-notes-outline" size={18} color="rgba(255,255,255,0.4)" />
                                    <Text style={styles.sectionTitle}>Audio</Text>
                                </View>
                                {player.availableAudioTracks.map((track, i) => (
                                    <TouchableOpacity
                                        key={track.id || i}
                                        style={[styles.menuItem, player.audioTrack?.id === track.id && { backgroundColor: `${theme.colors.accent}20`, borderColor: theme.colors.accent }]}
                                        onPress={() => {
                                            preferredSaveBlockedRef.current = true;
                                            rememberAudioTrackPreference(track);
                                            player.audioTrack = track;
                                            closePlayerDrawer();
                                        }}
                                    >
                                        <View style={styles.menuIconBox}>
                                            <Ionicons name="volume-high-outline" size={18} color={player.audioTrack?.id === track.id ? theme.colors.accent : 'white'} />
                                        </View>
                                        <View style={styles.menuTextCol}>
                                            <Text style={styles.menuText}>{track.label || track.language || `Track ${i + 1}`}</Text>
                                            <Text style={styles.menuSubText}>Stereo / Multi-channel</Text>
                                        </View>
                                        {player.audioTrack?.id === track.id && <Ionicons name="radio-button-on" size={18} color={theme.colors.accent} />}
                                    </TouchableOpacity>
                                ))}
                            </View>
                        )}
                        <View style={styles.menuSection}>
                            <View style={styles.sectionHeader}>
                                <Ionicons name="chatbubbles-outline" size={18} color="rgba(255,255,255,0.4)" />
                                <Text style={styles.sectionTitle}>Subtitles</Text>
                            </View>
                        <TouchableOpacity
                            style={[styles.menuItem, !player.subtitleTrack && { backgroundColor: `${theme.colors.accent}20`, borderColor: theme.colors.accent }]}
                            onPress={() => {
                                preferredSubtitleTrackKeyRef.current = SUBTITLE_OFF_PREFERENCE_KEY;
                                player.subtitleTrack = null;
                                closePlayerDrawer();
                            }}
                        >
                            <View style={styles.menuIconBox}>
                                <Ionicons name="close-circle-outline" size={18} color={!player.subtitleTrack ? theme.colors.accent : 'white'} />
                            </View>
                            <View style={styles.menuTextCol}>
                                <Text style={styles.menuText}>Off</Text>
                                <Text style={styles.menuSubText}>No subtitles displayed</Text>
                            </View>
                            {!player.subtitleTrack && <Ionicons name="radio-button-on" size={18} color={theme.colors.accent} />}
                        </TouchableOpacity>
                        {player.availableSubtitleTracks.map((track, i) => (
                            <TouchableOpacity
                                key={track.id || i}
                                style={[styles.menuItem, player.subtitleTrack?.id === track.id && { backgroundColor: `${theme.colors.accent}20`, borderColor: theme.colors.accent }]}
                                onPress={() => {
                                    rememberSubtitleTrackPreference(track);
                                    player.subtitleTrack = track;
                                    closePlayerDrawer();
                                }}
                            >
                                <View style={styles.menuIconBox}>
                                    <Ionicons name="chatbubble-ellipses-outline" size={18} color={player.subtitleTrack?.id === track.id ? theme.colors.accent : 'white'} />
                                </View>
                                <View style={styles.menuTextCol}>
                                    <Text style={styles.menuText}>{track.label || track.language || `Subtitle ${i + 1}`}</Text>
                                    <Text style={styles.menuSubText}>External / Embedded</Text>
                                </View>
                                {player.subtitleTrack?.id === track.id && <Ionicons name="radio-button-on" size={18} color={theme.colors.accent} />}
                            </TouchableOpacity>
                        ))}
                        </View>
                    </>
                );
            case 'speed':
                return (
                    <View style={styles.speedGrid}>
                        {[0.5, 0.75, 1, 1.25, 1.5, 2].map((speed) => (
                            <TouchableOpacity
                                key={speed}
                                style={[styles.speedItem, playbackSpeed === speed && { backgroundColor: theme.colors.accent, borderColor: theme.colors.accent }]}
                                onPress={() => {
                                    player.playbackRate = speed;
                                    setPlaybackSpeed(speed);
                                    closePlayerDrawer();
                                }}
                            >
                                <Text style={[styles.speedText, playbackSpeed === speed && { color: '#000', fontWeight: 'bold' }]}>{speed}x</Text>
                                <Text style={[styles.speedSubText, playbackSpeed === speed && { color: 'rgba(0,0,0,0.6)' }]}>{speed === 1 ? 'Normal' : (speed > 1 ? 'Fast' : 'Slow')}</Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                );
            case 'screen':
                return (
                    <View style={styles.resizeRow}>
                        {['contain', 'cover', 'fill'].map((mode) => (
                            <TouchableOpacity
                                key={mode}
                                style={[styles.resizeItem, contentFit === mode && { backgroundColor: theme.colors.accent }]}
                                onPress={() => {
                                    setContentFit(mode as VideoContentFit);
                                    closePlayerDrawer();
                                }}
                            >
                                <Ionicons
                                    name={mode === 'contain' ? 'contract' : (mode === 'cover' ? 'expand' : 'resize')}
                                    size={20}
                                    color={contentFit === mode ? '#000' : 'rgba(255,255,255,0.4)'}
                                />
                                <Text style={[styles.resizeText, contentFit === mode && { color: '#000', fontWeight: 'bold' }]}>
                                    {mode === 'contain' ? 'Fit' : (mode === 'cover' ? 'Crop' : 'Stretch')}
                                </Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                );
            case 'diagnostics':
            default:
                return (
                    <View style={styles.diagnosticsCard}>
                        {diagnosticsRows.map(row => (
                            <View key={row.label} style={styles.diagnosticRow}>
                                <Text style={styles.diagnosticLabel}>{row.label}</Text>
                                <Text style={styles.diagnosticValue}>{row.value}</Text>
                            </View>
                        ))}
                        {recentDebridFailures.length > 0 && (
                            <View style={styles.diagnosticFailuresWrap}>
                                <Text style={styles.diagnosticLabel}>Recent Debrid Failures</Text>
                                {recentDebridFailures.slice(0, 3).map((failure, index) => (
                                    <Text key={`${failure.provider}-${failure.code}-${index}`} style={styles.diagnosticFailureText}>
                                        {`${failure.provider ?? 'unknown'} • ${failure.code ?? 'unknown'} • ${failure.message ?? 'Unknown error'}`}
                                    </Text>
                                ))}
                            </View>
                        )}
                    </View>
                );
        }
    };

    // ── Render ─────────────────────────────────────────────────────────────────

    if (isError) {
        return (
            <View style={styles.root}>
                {loadingArtworkUri && <Image source={{ uri: loadingArtworkUri }} style={styles.backdropBg} />}
                <View style={styles.overlayDim} />
                <View style={styles.content}>
                    <Ionicons name="alert-circle-outline" size={64} color="#ef4444" />
                    <Text style={styles.title}>{t('player_no_streams')}</Text>
                    <Text style={styles.msg}>We couldn't find a playable source.</Text>
                    <TouchableOpacity 
                        style={styles.btn} 
                        onPress={() => {
                            ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP);
                            flushProgressAndGoBack();
                        }}
                    >
                        <Text style={styles.btnText}>Go Back</Text>
                    </TouchableOpacity>
                </View>
            </View>
        );
    }

    return (
        <View style={styles.root}>
            {shouldUseEmbeddedVideoPlayer && (
                <>
                    <VideoView 
                        ref={videoViewRef}
                        style={StyleSheet.absoluteFill} 
                        player={player} 
                        contentFit={contentFit} 
                        surfaceType={effectiveSurfaceType}
                        nativeControls={false} 
                        allowsPictureInPicture={pictureInPictureEnabled}
                        startsPictureInPictureAutomatically={pictureInPictureEnabled}
                        onFirstFrameRender={() => {
                            firstFrameRenderedRef.current = true;
                            clearFirstFrameTimer();
                            logPlayerEvent('info', `[Player] First video frame rendered for ${describeStream(activeStreamRef.current, activeStreamIndexRef.current, allStreamsRef.current.length)}`);
                            if (player.status !== 'error') {
                                cancelPendingFallbacks('current source rendered its first frame');
                                setLoading(false);
                            }
                        }}
                    />
                    <Pressable 
                        style={StyleSheet.absoluteFill} 
                        onPress={toggleControls}
                    />
                </>
            )}
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
            {loading && (
                <PlaybackLoadingOverlay
                    visible={loading}
                    artworkUri={loadingArtworkUri}
                    titleLogoUri={titleLogoUri}
                    fallbackTitle={title ?? 'Playback'}
                    loadingMessage={loadingMsg}
                    logoBreathAnim={loadingLogoBreathAnim}
                    textOpacity={loadingTextOpacity}
                    accentColor={theme.colors.accent}
                    textColor="#ffffff"
                    secondaryTextColor="rgba(255,255,255,0.82)"
                />
            )}
            {isPausedPlayback && !showControls && (
                <View style={styles.pausedLogoWrap} pointerEvents="none">
                    {titleLogoUri ? (
                        <Image source={{ uri: titleLogoUri }} style={styles.logoImage} resizeMode="contain" />
                    ) : (
                        <Text style={styles.logoFallbackText} numberOfLines={2}>{title ?? 'Playback'}</Text>
                    )}
                    {!!synopsis && (
                        <Text style={styles.pausedSynopsisText} numberOfLines={4}>
                            {synopsis}
                        </Text>
                    )}
                </View>
            )}
            {shouldUseEmbeddedVideoPlayer && showControls && !loading && (
                <Animated.View style={{ opacity: controlsOpacity }}>
                <LinearGradient 
                    colors={['rgba(0,0,0,0.8)', 'transparent']}
                    pointerEvents="box-none"
                    style={[styles.topBar, { paddingTop: insets.top + 12 }]}
                >
                    <View style={styles.controlsRow}>
                        <TouchableOpacity 
                            style={styles.circleBtn} 
                            onPress={flushProgressAndGoBack}
                            hitSlop={{ top: 20, bottom: 20, left: 20, right: 20 }}
                        >
                            <Ionicons name="close" size={22} color="white" />
                        </TouchableOpacity>
                        {!loading && (
                            <View style={styles.titleContainer}>
                                <Text style={styles.videoTitle} numberOfLines={1}>{title}</Text>
                                <Text style={styles.videoSubtitle}>{year} • {type === 'tv' ? 'Episode' : 'Movie'}</Text>
                            </View>
                        )}
                        <View style={styles.circleBtnPlaceholder} />
                    </View>
                </LinearGradient>
                </Animated.View>
            )}
            {shouldUseEmbeddedVideoPlayer && castNativeButtonAvailable && CastButton && (
                <View pointerEvents="none" style={styles.hiddenCastButtonWrap}>
                    <CastButton style={styles.hiddenCastButton} />
                </View>
            )}
            {shouldUseEmbeddedVideoPlayer && showControls && !loading && (
                <Animated.View style={[styles.centerContainer, { opacity: controlsOpacity }]} pointerEvents="box-none">
                    <TouchableOpacity 
                        style={styles.centerBtn} 
                        onPress={() => seekBy(-10)}
                        hitSlop={{ top: 20, bottom: 20, left: 20, right: 20 }}
                    >
                        <Ionicons name="play-back" size={32} color="white" />
                        <Text style={styles.skipText}>10</Text>
                    </TouchableOpacity>
                    <TouchableOpacity 
                        style={styles.mainPlayBtn} 
                        onPress={handlePlayPause}
                        hitSlop={{ top: 20, bottom: 20, left: 20, right: 20 }}
                    >
                        <Ionicons name={isPlaying ? "pause-sharp" : "play-sharp"} size={36} color="#000" />
                    </TouchableOpacity>
                    <TouchableOpacity 
                        style={styles.centerBtn} 
                        onPress={() => seekBy(10)}
                        hitSlop={{ top: 20, bottom: 20, left: 20, right: 20 }}
                    >
                        <Ionicons name="play-forward" size={32} color="white" />
                        <Text style={styles.skipText}>10</Text>
                    </TouchableOpacity>
                </Animated.View>
            )}
            {shouldUseEmbeddedVideoPlayer && showControls && !loading && (
                <Animated.View style={{ opacity: controlsOpacity }}>
                <LinearGradient 
                    colors={['transparent', 'rgba(0,0,0,0.7)']}
                    pointerEvents="box-none"
                    style={[styles.bottomBar, { paddingBottom: insets.bottom + 16 }]}
                >
                    <View style={styles.sliderContainer}>
                        <Slider
                            style={styles.slider}
                            minimumValue={0}
                            maximumValue={duration}
                            value={currentTime}
                            onValueChange={(val) => {
                                lastUiTimeRef.current = normalizePlaybackTime(val);
                                setCurrentTime(val);
                                if (controlsTimerRef.current) clearTimeout(controlsTimerRef.current);
                            }}
                            onSlidingComplete={(val) => {
                                player.currentTime = val;
                                resetControlsTimer();
                            }}
                            minimumTrackTintColor={theme.colors.accent}
                            maximumTrackTintColor="rgba(255,255,255,0.2)"
                            thumbTintColor="#fff"
                        />
                        <View style={styles.timeRow}>
                            <Text style={styles.timeLabel}>{formatTime(currentTime)}</Text>
                            <Text style={styles.timeLabel}>{formatTime(duration)}</Text>
                        </View>
                        <View style={styles.quickMenuRow}>
                            {mpvQuickActionVisible && shouldUseEmbeddedVideoPlayer && !expoGoRuntime && (
                                <TouchableOpacity
                                    style={styles.quickMenuButton}
                                    onPress={handleEmbeddedMpvPress}
                                    hitSlop={{ top: 15, bottom: 15, left: 10, right: 10 }}
                                >
                                    <Ionicons
                                        name="flash-outline"
                                        size={14}
                                        color="rgba(255,255,255,0.78)"
                                    />
                                    <Text style={styles.quickMenuLabel}>
                                        MPV
                                    </Text>
                                </TouchableOpacity>
                            )}
                            <TouchableOpacity
                                style={styles.quickMenuButton}
                                onPress={handleExternalPlayerPress}
                                hitSlop={{ top: 15, bottom: 15, left: 10, right: 10 }}
                            >
                                <Ionicons
                                    name="open-outline"
                                    size={14}
                                    color="rgba(255,255,255,0.78)"
                                />
                                <Text style={styles.quickMenuLabel}>
                                    Open
                                </Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                                style={[styles.quickMenuButton, castState === CastState.CONNECTED && styles.quickMenuButtonActive]}
                                onPress={handleCastPress}
                                hitSlop={{ top: 15, bottom: 15, left: 10, right: 10 }}
                            >
                                <Ionicons
                                    name={castState === CastState.CONNECTED ? 'tv' : 'tv-outline'}
                                    size={14}
                                    color={castState === CastState.CONNECTED ? '#fff' : 'rgba(255,255,255,0.78)'}
                                />
                                <Text style={[styles.quickMenuLabel, castState === CastState.CONNECTED && styles.quickMenuLabelActive]}>
                                    Cast
                                </Text>
                            </TouchableOpacity>
                            {playerMenuItems
                                .filter(item => item.enabled !== false)
                                .map(item => {
                                    const isActive = showPlayerDrawer && activeDrawerSection === item.key;
                                    return (
                                        <TouchableOpacity
                                            key={item.key}
                                            style={[styles.quickMenuButton, isActive && styles.quickMenuButtonActive]}
                                            hitSlop={{ top: 15, bottom: 15, left: 10, right: 10 }}
                                            onPress={() => {
                                                if (item.key === 'screen') {
                                                    toggleContentFit();
                                                    return;
                                                }
                                                openPlayerDrawer(item.key);
                                            }}
                                        >
                                            <Ionicons
                                                name={
                                                    item.key === 'screen'
                                                        ? (contentFit === 'contain'
                                                            ? 'contract'
                                                            : contentFit === 'cover'
                                                                ? 'expand'
                                                                : 'resize')
                                                        : item.icon
                                                }
                                                size={14}
                                                color={isActive ? '#fff' : 'rgba(255,255,255,0.78)'}
                                            />
                                            <Text style={[styles.quickMenuLabel, isActive && styles.quickMenuLabelActive]}>
                                                {item.label}
                                            </Text>
                                        </TouchableOpacity>
                                    );
                                })}
                        </View>
                    </View>
                </LinearGradient>
                </Animated.View>
            )}
            {/* SOURCES DRAWER (LEFT) */}
            <Modal
                visible={showSources}
                transparent={true}
                animationType="fade"
                statusBarTranslucent={true}
                onRequestClose={closePlayerDrawer}
            >
                <View style={StyleSheet.absoluteFill}>
                    <Pressable 
                        style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(0,0,0,0.6)' }]} 
                        onPress={closePlayerDrawer} 
                    />
                    <View style={[styles.drawerContentOuterRight, styles.drawerContentOuterRightWide]}>
                        <View style={[styles.drawerContentRight, styles.drawerContentRightWide]}>
                            <View style={styles.drawerHeader}>
                                <View style={styles.headerTitleRow}>
                                    <Ionicons name="layers-outline" size={20} color={theme.colors.accent} style={{ marginRight: 10 }} />
                                    <Text style={styles.drawerTitle}>{activeDrawerTitle}</Text>
                                </View>
                                <TouchableOpacity onPress={closePlayerDrawer} style={styles.closeBtn}>
                                    <Ionicons name="close" size={24} color="rgba(255,255,255,0.5)" />
                                </TouchableOpacity>
                            </View>
                            <ScrollView 
                                showsVerticalScrollIndicator={false} 
                                contentContainerStyle={{ paddingBottom: 60, paddingHorizontal: 20 }}
                                scrollEventThrottle={16}
                                style={{ flex: 1 }}
                                keyboardShouldPersistTaps="handled"
                            >
                                {renderDrawerContent()}
                            </ScrollView>
                        </View>
                    </View>
                </View>
            </Modal>

            {/* SETTINGS DRAWER (RIGHT) */}
            <Modal
                visible={showSettings}
                transparent={true}
                animationType="fade"
                statusBarTranslucent={true}
                onRequestClose={closePlayerDrawer}
            >
                <View style={StyleSheet.absoluteFill}>
                    <Pressable 
                        style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(0,0,0,0.6)' }]} 
                        onPress={closePlayerDrawer} 
                    />
                    <View style={styles.drawerContentOuterRight}>
                        <View style={styles.drawerContentRight}>
                            <View style={styles.drawerHeader}>
                                <View style={styles.headerTitleRow}>
                                    <Ionicons
                                        name={
                                            activeDrawerSection === 'tracks' ? 'options-outline'
                                                : activeDrawerSection === 'speed' ? 'speedometer-outline'
                                                    : activeDrawerSection === 'screen' ? 'expand-outline'
                                                        : 'pulse-outline'
                                        }
                                        size={20}
                                        color={theme.colors.accent}
                                        style={{ marginRight: 10 }}
                                    />
                                    <Text style={styles.drawerTitle}>{activeDrawerTitle}</Text>
                                </View>
                                <TouchableOpacity onPress={closePlayerDrawer} style={styles.closeBtn}>
                                    <Ionicons name="close" size={24} color="rgba(255,255,255,0.5)" />
                                </TouchableOpacity>
                            </View>
                            <ScrollView 
                                showsVerticalScrollIndicator={false} 
                                contentContainerStyle={{ paddingBottom: 60, paddingHorizontal: 20 }}
                                scrollEventThrottle={16}
                                style={{ flex: 1 }}
                                keyboardShouldPersistTaps="handled"
                            >
                                {activeDrawerSection === 'tracks' && <View style={styles.menuSection}>
                                    <View style={styles.sectionHeader}>
                                        <Ionicons name="musical-notes-outline" size={18} color="rgba(255,255,255,0.4)" />
                                        <Text style={styles.sectionTitle}>Audio Selection</Text>
                                    </View>
                                    {player.availableAudioTracks.map((track, i) => (
                                        <TouchableOpacity 
                                            key={track.id || i}
                                            style={[styles.menuItem, player.audioTrack?.id === track.id && { backgroundColor: `${theme.colors.accent}20`, borderColor: theme.colors.accent }]}
                                            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                                onPress={() => {
                                    preferredSaveBlockedRef.current = true;
                                    rememberAudioTrackPreference(track);
                                    player.audioTrack = track;
                                    closePlayerDrawer();
                                }}
                                        >
                                            <View style={styles.menuIconBox}>
                                                <Ionicons name="volume-high-outline" size={18} color={player.audioTrack?.id === track.id ? theme.colors.accent : "white"} />
                                            </View>
                                            <View style={styles.menuTextCol}>
                                                <Text style={styles.menuText}>{track.label || track.language || `Track ${i+1}`}</Text>
                                                <Text style={styles.menuSubText}>Stereo / Multi-channel</Text>
                                            </View>
                                            {player.audioTrack?.id === track.id && <Ionicons name="radio-button-on" size={18} color={theme.colors.accent} />}
                                        </TouchableOpacity>
                                    ))}
                                </View>}
                            {activeDrawerSection === 'tracks' && <View style={styles.menuSection}>
                                <View style={styles.sectionHeader}>
                                    <Ionicons name="chatbubbles-outline" size={18} color="rgba(255,255,255,0.4)" />
                                    <Text style={styles.sectionTitle}>Subtitles</Text>
                                </View>
                        <TouchableOpacity
                            style={[styles.menuItem, !player.subtitleTrack && { backgroundColor: `${theme.colors.accent}20`, borderColor: theme.colors.accent }]}
                            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                            onPress={() => {
                                preferredSubtitleTrackKeyRef.current = SUBTITLE_OFF_PREFERENCE_KEY;
                                player.subtitleTrack = null;
                                closePlayerDrawer();
                            }}
                        >
                                    <View style={styles.menuIconBox}>
                                        <Ionicons name="close-circle-outline" size={18} color={!player.subtitleTrack ? theme.colors.accent : "white"} />
                                    </View>
                                    <View style={styles.menuTextCol}>
                                        <Text style={styles.menuText}>Off</Text>
                                        <Text style={styles.menuSubText}>No subtitles displayed</Text>
                                    </View>
                                    {!player.subtitleTrack && <Ionicons name="radio-button-on" size={18} color={theme.colors.accent} />}
                                </TouchableOpacity>
                                {player.availableSubtitleTracks.map((track, i) => (
                        <TouchableOpacity
                            key={track.id || i}
                            style={[styles.menuItem, player.subtitleTrack?.id === track.id && { backgroundColor: `${theme.colors.accent}20`, borderColor: theme.colors.accent }]}
                            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                            onPress={() => {
                                rememberSubtitleTrackPreference(track);
                                player.subtitleTrack = track;
                                closePlayerDrawer();
                            }}
                        >
                                        <View style={styles.menuIconBox}>
                                            <Ionicons name="chatbubble-ellipses-outline" size={18} color={player.subtitleTrack?.id === track.id ? theme.colors.accent : "white"} />
                                        </View>
                                        <View style={styles.menuTextCol}>
                                            <Text style={styles.menuText}>{track.label || track.language || `Subtitle ${i+1}`}</Text>
                                            <Text style={styles.menuSubText}>External / Embedded</Text>
                                        </View>
                                        {player.subtitleTrack?.id === track.id && <Ionicons name="radio-button-on" size={18} color={theme.colors.accent} />}
                                    </TouchableOpacity>
                                ))}
                            </View>}
                            {activeDrawerSection === 'speed' && <View style={styles.menuSection}>
                                <View style={styles.sectionHeader}>
                                    <Ionicons name="speedometer-outline" size={18} color="rgba(255,255,255,0.4)" />
                                    <Text style={styles.sectionTitle}>Playback Speed</Text>
                                </View>
                                <View style={styles.speedGrid}>
                                    {[0.5, 0.75, 1, 1.25, 1.5, 2].map((speed) => (
                                        <TouchableOpacity 
                                            key={speed}
                                            style={[styles.speedItem, playbackSpeed === speed && { backgroundColor: theme.colors.accent, borderColor: theme.colors.accent }]}
                                            onPress={() => { player.playbackRate = speed; setPlaybackSpeed(speed); closePlayerDrawer(); }}
                                        >
                                            <Text style={[styles.speedText, playbackSpeed === speed && { color: '#000', fontWeight: 'bold' }]}>{speed}x</Text>
                                            <Text style={[styles.speedSubText, playbackSpeed === speed && { color: 'rgba(0,0,0,0.6)' }]}>{speed === 1 ? 'Normal' : (speed > 1 ? 'Fast' : 'Slow')}</Text>
                                        </TouchableOpacity>
                                    ))}
                                </View>
                            </View>}
                            {activeDrawerSection === 'screen' && <View style={styles.menuSection}>
                                <View style={styles.sectionHeader}>
                                    <Ionicons name="expand-outline" size={18} color="rgba(255,255,255,0.4)" />
                                    <Text style={styles.sectionTitle}>Screen Fit Mode</Text>
                                </View>
                                <View style={styles.resizeRow}>
                                    {['contain', 'cover', 'fill'].map((mode) => (
                                        <TouchableOpacity 
                                            key={mode}
                                            style={[styles.resizeItem, contentFit === mode && { backgroundColor: theme.colors.accent }]}
                                            onPress={() => { setContentFit(mode as VideoContentFit); closePlayerDrawer(); }}
                                        >
                                            <Ionicons 
                                                name={mode === 'contain' ? 'contract' : (mode === 'cover' ? 'expand' : 'resize')} 
                                                size={20} 
                                                color={contentFit === mode ? '#000' : 'rgba(255,255,255,0.4)'} 
                                            />
                                            <Text style={[styles.resizeText, contentFit === mode && { color: '#000', fontWeight: 'bold' }]}>
                                                {mode === 'contain' ? 'Fit' : (mode === 'cover' ? 'Crop' : 'Stretch')}
                                            </Text>
                                        </TouchableOpacity>
                                    ))}
                                </View>
                            </View>}
                            {activeDrawerSection === 'diagnostics' && <View style={styles.menuSection}>
                                <View style={styles.diagnosticsCard}>
                                    {diagnosticsRows.map(row => (
                                        <View key={row.label} style={styles.diagnosticRow}>
                                            <Text style={styles.diagnosticLabel}>{row.label}</Text>
                                            <Text style={styles.diagnosticValue}>{row.value}</Text>
                                        </View>
                                    ))}
                                    {recentDebridFailures.length > 0 && (
                                        <View style={styles.diagnosticFailuresWrap}>
                                            <Text style={styles.diagnosticLabel}>Recent Debrid Failures</Text>
                                            {recentDebridFailures.slice(0, 3).map((failure, index) => (
                                                <Text key={`${failure.provider}-${failure.code}-${index}`} style={styles.diagnosticFailureText}>
                                                    {`${failure.provider ?? 'unknown'} • ${failure.code ?? 'unknown'} • ${failure.message ?? 'Unknown error'}`}
                                                </Text>
                                            ))}
                                        </View>
                                    )}
                                </View>
                            </View>}
                        </ScrollView>
                    </View>
                </View>
            </View>
        </Modal>
        <Modal
            visible={!!externalPlayerErrorMessage}
            transparent={true}
            animationType="fade"
            statusBarTranslucent={true}
            onRequestClose={() => setExternalPlayerErrorMessage(null)}
        >
            <View style={StyleSheet.absoluteFill}>
                <Pressable
                    style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(0,0,0,0.72)' }]}
                    onPress={() => setExternalPlayerErrorMessage(null)}
                />
                <View style={styles.externalPlayerModalWrap}>
                    <View style={styles.externalPlayerModalCard}>
                        <View style={styles.externalPlayerModalIcon}>
                            <Ionicons name="open-outline" size={22} color="#f59e0b" />
                        </View>
                        <Text style={styles.externalPlayerModalTitle}>External Player Unavailable</Text>
                        <Text style={styles.externalPlayerModalMessage}>{externalPlayerErrorMessage}</Text>
                        <TouchableOpacity
                            style={styles.externalPlayerModalButton}
                            onPress={() => setExternalPlayerErrorMessage(null)}
                            activeOpacity={0.85}
                        >
                            <Text style={styles.externalPlayerModalButtonText}>Continue In App</Text>
                        </TouchableOpacity>
                    </View>
                </View>
            </View>
        </Modal>
        <Modal
            visible={!!castErrorMessage}
            transparent={true}
            animationType="fade"
            statusBarTranslucent={true}
            onRequestClose={() => setCastErrorMessage(null)}
        >
            <View style={StyleSheet.absoluteFill}>
                <Pressable
                    style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(0,0,0,0.72)' }]}
                    onPress={() => setCastErrorMessage(null)}
                />
                <View style={styles.externalPlayerModalWrap}>
                    <View style={styles.externalPlayerModalCard}>
                        <View style={[styles.externalPlayerModalIcon, { backgroundColor: 'rgba(59,130,246,0.18)' }]}>
                            <Ionicons name="tv-outline" size={22} color="#60a5fa" />
                        </View>
                        <Text style={styles.externalPlayerModalTitle}>Casting Unavailable</Text>
                        <Text style={styles.externalPlayerModalMessage}>{castErrorMessage}</Text>
                        <TouchableOpacity
                            style={styles.externalPlayerModalButton}
                            onPress={() => setCastErrorMessage(null)}
                            activeOpacity={0.85}
                        >
                            <Text style={styles.externalPlayerModalButtonText}>Continue In App</Text>
                        </TouchableOpacity>
                    </View>
                </View>
            </View>
        </Modal>
        <Modal
            visible={showCompatibilitySuggestion}
            transparent={true}
            animationType="fade"
            statusBarTranslucent={true}
            onRequestClose={() => setShowCompatibilitySuggestion(false)}
        >
            <View style={StyleSheet.absoluteFill}>
                <Pressable
                    style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(0,0,0,0.72)' }]}
                    onPress={() => setShowCompatibilitySuggestion(false)}
                />
                <View style={styles.externalPlayerModalWrap}>
                    <View style={styles.externalPlayerModalCard}>
                        <View style={[styles.externalPlayerModalIcon, { backgroundColor: 'rgba(139,92,246,0.18)' }]}>
                            <Ionicons name="construct-outline" size={22} color="#a78bfa" />
                        </View>
                        <Text style={styles.externalPlayerModalTitle}>Try Compatibility Mode?</Text>
                        <Text style={styles.externalPlayerModalMessage}>
                            Playback has failed a few times on the current setup. Switching to software decoding with the compatibility surface may work better on this device.
                        </Text>
                        <View style={styles.compatibilityActionsRow}>
                            <TouchableOpacity
                                style={styles.compatibilitySecondaryButton}
                                onPress={() => setShowCompatibilitySuggestion(false)}
                                activeOpacity={0.85}
                            >
                                <Text style={styles.compatibilitySecondaryButtonText}>Not Now</Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                                style={styles.externalPlayerModalButton}
                                onPress={async () => {
                                    setShowCompatibilitySuggestion(false);
                                    await setDecoderMode('software');
                                    await setRenderSurface('compatibility');
                                    setRuntimePlaybackConfig({
                                        decoderMode: 'software',
                                        surfaceType: 'textureView',
                                    });
                                    logPlayerEvent('info', '[Player] User accepted compatibility mode suggestion');
                                }}
                                activeOpacity={0.85}
                            >
                                <Text style={styles.externalPlayerModalButtonText}>Apply</Text>
                            </TouchableOpacity>
                        </View>
                    </View>
                </View>
            </View>
        </Modal>
        </View>
    );
};

const styles = StyleSheet.create({
    root: { flex: 1, backgroundColor: '#000' },
    overlay: { ...StyleSheet.absoluteFill, backgroundColor: '#000', zIndex: 10 },
    backdropBg: { ...StyleSheet.absoluteFill, resizeMode: 'cover', opacity: 0.24 },
    overlayDim: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(0,0,0,0.32)' },
    content: { flex: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 32 },
    loadingTitleWrap: { maxWidth: 280, alignItems: 'center', justifyContent: 'center' },
    logoImage: { width: 220, height: 76, marginBottom: 6 },
    logoFallbackText: {
        color: '#fff',
        fontSize: 22,
        fontWeight: '800',
        textAlign: 'center',
        letterSpacing: 0.3,
    },
    title: { color: '#fff', fontSize: 22, fontWeight: '800', textAlign: 'center', marginBottom: 4 },
    year: { color: 'rgba(255,255,255,0.6)', fontSize: 14, fontWeight: '600', marginBottom: 28 },
    msg: { color: 'rgba(255,255,255,0.65)', fontSize: 13, fontWeight: '500', marginTop: 4 },
    btn: { marginTop: 24, backgroundColor: '#8b5cf6', paddingVertical: 12, paddingHorizontal: 32, borderRadius: 12 },
    btnText: { color: '#fff', fontSize: 14, fontWeight: '700' },
    topBar: { 
        position: 'absolute', 
        top: 0, 
        left: 0, 
        right: 0, 
        justifyContent: 'flex-start',
        zIndex: 20 
    },
    glassHeader: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingHorizontal: 16,
        paddingVertical: 10,
        marginHorizontal: 16,
        borderRadius: 20,
        backgroundColor: 'rgba(255, 255, 255, 0.08)',
        borderWidth: 1,
        borderColor: 'rgba(255, 255, 255, 0.12)',
    },
    controlsRow: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingHorizontal: 20,
        paddingTop: 10
    },
    topRightActions: {
        flexDirection: 'row',
        alignItems: 'center',
    },
    circleBtnPlaceholder: {
        width: 44,
        height: 44,
    },
    circleBtn: {
        width: 44,
        height: 44,
        borderRadius: 22,
        backgroundColor: 'rgba(255, 255, 255, 0.25)',
        justifyContent: 'center',
        alignItems: 'center',
        borderWidth: 1.5,
        borderColor: 'rgba(255, 255, 255, 0.4)',
    },
    circleBtnActive: {
        backgroundColor: 'rgba(59, 130, 246, 0.32)',
        borderColor: 'rgba(96, 165, 250, 0.9)',
    },
    hiddenCastButtonWrap: {
        position: 'absolute',
        top: -100,
        left: -100,
        width: 1,
        height: 1,
        opacity: 0,
    },
    hiddenCastButton: {
        width: 1,
        height: 1,
        opacity: 0,
    },
    titleContainer: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
    },
    videoTitle: { 
        color: '#fff', 
        fontSize: 16, 
        fontWeight: '700', 
        textAlign: 'center',
        textShadowColor: 'rgba(0,0,0,0.5)',
        textShadowOffset: { width: 0, height: 1 },
        textShadowRadius: 3
    },
    videoSubtitle: {
        color: 'rgba(255,255,255,0.85)',
        fontSize: 12,
        fontWeight: '600',
        marginTop: 2,
    },
    centerContainer: {
        ...StyleSheet.absoluteFillObject,
        flexDirection: 'row',
        justifyContent: 'center',
        alignItems: 'center',
        zIndex: 15,
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
        right: 24,
        top: 0,
        bottom: 0,
        maxWidth: 280,
        alignItems: 'flex-end',
        justifyContent: 'center',
        marginBottom: 0,
    },
    pausedSynopsisText: {
        marginTop: 12,
        color: 'rgba(255,255,255,0.82)',
        fontSize: 13,
        lineHeight: 19,
        textAlign: 'right',
        maxWidth: 280,
    },
    pausedPlayBtn: {
        width: 65,
        height: 65,
        borderRadius: 32.5,
        backgroundColor: 'rgba(255, 255, 255, 0.86)',
        justifyContent: 'center',
        alignItems: 'center',
        borderWidth: 1.5,
        borderColor: 'rgba(255, 255, 255, 0.4)',
    },
    centerBtn: {
        width: 80,
        height: 80,
        justifyContent: 'center',
        alignItems: 'center',
        marginHorizontal: 30,
    },
    skipText: {
        color: 'white',
        fontSize: 12,
        fontWeight: 'bold',
        marginTop: -5,
    },
    mainPlayBtn: {
        width: 70,
        height: 70,
        borderRadius: 35,
        backgroundColor: 'rgba(255, 255, 255, 0.5)',
        justifyContent: 'center',
        alignItems: 'center',
        borderWidth: 1.5,
        borderColor: 'rgba(255, 255, 255, 0.4)',
    },
    bottomBar: { 
        position: 'absolute', 
        bottom: 0, 
        left: 0, 
        right: 0, 
        justifyContent: 'flex-end',
        zIndex: 20 
    },
    sliderContainer: {
        width: '100%',
        paddingHorizontal: 20,
        marginBottom: 10,
    },
    slider: {
        width: '100%',
        height: 40,
    },
    timeRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginTop: -10,
        paddingHorizontal: 15,
    },
    timeLabel: {
        color: 'rgba(255,255,255,0.8)',
        fontSize: 12,
        fontWeight: '600',
        fontFamily: 'monospace',
    },
    quickMenuRow: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        justifyContent: 'center',
        gap: 16,
        marginTop: 16,
    },
    quickMenuButton: {
        minWidth: 84,
        paddingHorizontal: 16,
        paddingVertical: 14,
        borderRadius: 16,
        backgroundColor: 'rgba(255,255,255,0.12)',
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.14)',
        alignItems: 'center',
        justifyContent: 'center',
    },
    quickMenuButtonActive: {
        backgroundColor: 'rgba(139, 92, 246, 0.34)',
        borderColor: 'rgba(167, 139, 250, 0.8)',
    },
    quickMenuLabel: {
        color: 'rgba(255,255,255,0.8)',
        fontSize: 11,
        fontWeight: '700',
        marginTop: 5,
    },
    quickMenuLabelActive: {
        color: '#fff',
    },
    drawerOverlay: {
        ...StyleSheet.absoluteFillObject,
        backgroundColor: 'rgba(0,0,0,0.6)',
        flexDirection: 'row',
        justifyContent: 'flex-end',
    },
    drawerContentOuterRight: {
        position: 'absolute',
        top: 0,
        right: 0,
        bottom: 0,
    },
    drawerContentOuterRightWide: {
        width: '72%',
        maxWidth: 520,
    },
    drawerContentOuterLeft: {
        position: 'absolute',
        top: 0,
        left: 0,
        bottom: 0,
        width: '66.666%',
    },
    drawerContentRight: {
        width: 320,
        height: '100%',
        backgroundColor: 'rgba(17,17,17,0.45)',
        borderLeftWidth: 1,
        borderLeftColor: 'rgba(255,255,255,0.1)',
        paddingTop: 20,
    },
    drawerContentRightWide: {
        width: '100%',
    },
    drawerContentLeft: {
        width: '100%',
        height: '100%',
        backgroundColor: 'rgba(17,17,17,0.45)',
        borderRightWidth: 1,
        borderRightColor: 'rgba(255,255,255,0.1)',
        paddingTop: 20,
    },
    drawerHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 20,
        paddingBottom: 15,
        paddingHorizontal: 20,
        borderBottomWidth: 1,
        borderBottomColor: 'rgba(255,255,255,0.1)',
    },
    headerTitleRow: {
        flexDirection: 'row',
        alignItems: 'center',
    },
    drawerTitle: {
        color: 'white',
        fontSize: 16,
        fontWeight: '800',
    },
    closeBtn: {
        padding: 5,
    },
    menuSection: {
        marginBottom: 25,
    },
    sectionHeader: {
        flexDirection: 'row',
        alignItems: 'center',
        marginBottom: 12,
    },
    sectionTitle: {
        color: 'rgba(255,255,255,0.55)',
        fontSize: 11,
        fontWeight: '900',
        textTransform: 'uppercase',
        marginLeft: 8,
        letterSpacing: 1.2,
    },
    diagnosticsCard: {
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.1)',
        backgroundColor: 'rgba(255,255,255,0.04)',
        borderRadius: 16,
        padding: 14,
        gap: 10,
    },
    diagnosticRow: {
        gap: 4,
    },
    diagnosticLabel: {
        color: 'rgba(255,255,255,0.45)',
        fontSize: 11,
        fontWeight: '800',
        textTransform: 'uppercase',
        letterSpacing: 0.8,
    },
    diagnosticValue: {
        color: 'white',
        fontSize: 13,
        lineHeight: 18,
    },
    diagnosticFailuresWrap: {
        marginTop: 4,
        gap: 6,
    },
    diagnosticFailureText: {
        color: '#fca5a5',
        fontSize: 12,
        lineHeight: 17,
    },
    externalPlayerModalWrap: {
        ...StyleSheet.absoluteFillObject,
        alignItems: 'center',
        justifyContent: 'center',
        paddingHorizontal: 24,
    },
    externalPlayerModalCard: {
        width: '100%',
        maxWidth: 360,
        borderRadius: 24,
        backgroundColor: 'rgba(17,17,17,0.96)',
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.1)',
        padding: 22,
        alignItems: 'center',
    },
    externalPlayerModalIcon: {
        width: 52,
        height: 52,
        borderRadius: 26,
        backgroundColor: 'rgba(245,158,11,0.15)',
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: 14,
    },
    externalPlayerModalTitle: {
        color: 'white',
        fontSize: 18,
        fontWeight: '800',
        textAlign: 'center',
        marginBottom: 8,
    },
    externalPlayerModalMessage: {
        color: 'rgba(255,255,255,0.72)',
        fontSize: 13,
        lineHeight: 20,
        textAlign: 'center',
        marginBottom: 18,
    },
    externalPlayerModalButton: {
        minWidth: 180,
        paddingHorizontal: 18,
        paddingVertical: 12,
        borderRadius: 14,
        backgroundColor: '#8b5cf6',
        alignItems: 'center',
        justifyContent: 'center',
    },
    externalPlayerModalButtonText: {
        color: 'white',
        fontSize: 14,
        fontWeight: '800',
    },
    compatibilityActionsRow: {
        flexDirection: 'row',
        gap: 10,
        width: '100%',
        justifyContent: 'center',
    },
    compatibilitySecondaryButton: {
        minWidth: 110,
        paddingHorizontal: 16,
        paddingVertical: 12,
        borderRadius: 14,
        backgroundColor: 'rgba(255,255,255,0.08)',
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.1)',
        alignItems: 'center',
        justifyContent: 'center',
    },
    compatibilitySecondaryButtonText: {
        color: 'rgba(255,255,255,0.9)',
        fontSize: 14,
        fontWeight: '700',
    },
    menuItem: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: 16,
        paddingHorizontal: 16,
        width: '100%',
        alignSelf: 'stretch',
        borderRadius: 14,
        marginBottom: 8,
        backgroundColor: 'rgba(255,255,255,0.08)',
        borderWidth: 1.5,
        borderColor: 'rgba(255,255,255,0.12)',
    },
    menuIconBox: {
        width: 36,
        height: 36,
        borderRadius: 10,
        backgroundColor: 'rgba(255,255,255,0.05)',
        justifyContent: 'center',
        alignItems: 'center',
        marginRight: 14,
    },
    menuTextCol: {
        flex: 1,
    },
    menuText: {
        color: 'white',
        fontSize: 13,
        fontWeight: '600',
    },
    menuSubText: {
        color: 'rgba(255,255,255,0.55)',
        fontSize: 11,
        marginTop: 2,
    },
    menuMetaLine: {
        color: 'rgba(255,255,255,0.42)',
        fontSize: 11,
        marginTop: 4,
    },
    badgeWrap: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 6,
        marginTop: 8,
    },
    metaBadge: {
        paddingHorizontal: 8,
        paddingVertical: 5,
        borderRadius: 999,
        backgroundColor: 'rgba(255,255,255,0.08)',
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.08)',
    },
    metaBadgeActive: {
        backgroundColor: 'rgba(139,92,246,0.2)',
        borderColor: 'rgba(139,92,246,0.45)',
    },
    metaBadgeText: {
        color: 'rgba(255,255,255,0.78)',
        fontSize: 10,
        fontWeight: '700',
    },
    metaBadgeTextActive: {
        color: 'white',
    },
    activeSourceBadge: {
        alignSelf: 'flex-start',
        marginTop: 4,
        paddingHorizontal: 8,
        paddingVertical: 4,
        borderRadius: 999,
        backgroundColor: 'rgba(139,92,246,0.22)',
        borderWidth: 1,
        borderColor: 'rgba(139,92,246,0.45)',
    },
    activeSourceBadgeText: {
        color: '#e9ddff',
        fontSize: 10,
        fontWeight: '800',
        letterSpacing: 0.3,
        textTransform: 'uppercase',
    },
    speedGrid: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        marginHorizontal: -4,
    },
    speedItem: {
        width: '31%',
        aspectRatio: 1.5,
        backgroundColor: 'rgba(255,255,255,0.05)',
        borderRadius: 12,
        justifyContent: 'center',
        alignItems: 'center',
        margin: '1.1%',
    },
    speedText: {
        color: 'white',
        fontSize: 15,
        fontWeight: '700',
    },
    speedSubText: {
        color: 'rgba(255,255,255,0.4)',
        fontSize: 10,
        marginTop: 2,
    },
    resizeRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
    },
    resizeItem: {
        flex: 1,
        height: 60,
        backgroundColor: 'rgba(255,255,255,0.05)',
        borderRadius: 12,
        justifyContent: 'center',
        alignItems: 'center',
        marginHorizontal: 4,
    },
    resizeText: {
        color: 'rgba(255,255,255,0.5)',
        fontSize: 12,
        fontWeight: '700',
        marginTop: 4,
    },
});
