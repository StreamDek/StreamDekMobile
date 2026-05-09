import React, { useEffect, useRef, useState, useMemo, useCallback } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, ActivityIndicator, StatusBar, Modal, Platform,
  Image as RNImage,
} from 'react-native';
import { Image } from 'expo-image';
import { BlurTargetView, BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAuth } from '../context/AuthContext';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useAddons, AddonStream } from '../context/AddonContext';
import { useDebrid } from '../context/DebridContext';
import { useLanguage } from '../context/LanguageContext';
import { useDisplaySettings } from '../context/DisplaySettingsContext';
import { useUIStyle } from '../context/UIStyleContext';
import { useWatched } from '../context/WatchedContext';
import { useStreamSelectionSettings } from '../context/StreamSelectionContext';
import { useWatchProgress, episodeProgressKey } from '../context/WatchProgressContext';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { ActionSheet } from '../components/ActionSheet';
import { PrimaryActionButton, getPrimaryActionPalette } from '../components/PrimaryActionButton';
import { StackBottomNav, BOTTOM_NAV_HEIGHT } from '../components/StackBottomNav';
import { selectBestStream, sortStreams, scoreStream } from '../utils/streamSelection';
import { parseStream, formatSeeds } from '../utils/streamParser';
import { isExpoGoRuntime } from '../utils/runtime';

const IMG_HEIGHT = 230;

function formatReleaseDate(value?: string | null): string | null {
  if (!value) return null;
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(date);
}

function formatRuntimeLabel(runtimeMinutes?: number | null): string | null {
  if (!runtimeMinutes || runtimeMinutes <= 0) return null;
  const hours = Math.floor(runtimeMinutes / 60);
  const minutes = runtimeMinutes % 60;
  if (hours <= 0) return `${minutes}m`;
  if (minutes === 0) return `${hours}h`;
  return `${hours}h ${minutes}m`;
}

function clampProgressPercent(positionSec: number, durationSec: number): number | null {
  if (!Number.isFinite(positionSec) || !Number.isFinite(durationSec) || positionSec <= 0 || durationSec <= 0) return null;
  const pct = (positionSec / durationSec) * 100;
  if (!Number.isFinite(pct) || pct <= 0) return null;
  return Math.max(0, Math.min(100, pct));
}

function ExpandableText({
  text,
  style,
  maxLines = 3,
  moreLabel = 'Read more',
  lessLabel = 'Show less',
  moreColor,
}: {
  text?: string | null;
  style?: any;
  maxLines?: number;
  moreLabel?: string;
  lessLabel?: string;
  moreColor?: string;
}) {
  const [expanded, setExpanded] = useState(false);
  const [canExpand, setCanExpand] = useState(false);

  if (!text) return null;

  return (
    <View>
      {/* Hidden full-text render used solely to measure true line count */}
      <Text
        style={[style, { position: 'absolute', opacity: 0, pointerEvents: 'none' }]}
        numberOfLines={undefined}
        onTextLayout={(e) => {
          setCanExpand(e.nativeEvent.lines.length > maxLines);
        }}
      >
        {text}
      </Text>
      <Text
        style={style}
        numberOfLines={expanded ? undefined : maxLines}
      >
        {text}
      </Text>
      {canExpand && (
        <TouchableOpacity
          onPress={() => setExpanded(prev => !prev)}
          activeOpacity={0.75}
          style={{ marginTop: 6, alignSelf: 'flex-start' }}
        >
          <Text style={{ color: moreColor ?? '#a89ff8', fontSize: 12, fontWeight: '700' }}>
            {expanded ? lessLabel : moreLabel}
          </Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const makeStyles = (c: ThemeColors, isLightAppearance: boolean, vividAmbient: boolean) => {
  const isMonochromeDark = !isLightAppearance && c.accent === '#ffffff' && c.buttonText === '#111111';
  return StyleSheet.create({
  container:    { flex: 1 },
  glassContainer: { flex: 1, backgroundColor: 'transparent' },
  ambientBackdrop: { ...StyleSheet.absoluteFillObject },
  ambientBackdropImage: { ...StyleSheet.absoluteFillObject, opacity: 0.60 },
  glassAmbientBackdropImage: { ...StyleSheet.absoluteFillObject, opacity: 0.78 },
  ambientBackdropScrim: { ...StyleSheet.absoluteFillObject } as any,
  glassAmbientVeil: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.10)' : 'rgba(4,6,10,0.18)',
  },
  // Fixed backdrop + title block (absolutely positioned so streams scroll under)
  headerBlock: {
    position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10,
  },
  backdrop:     { width: '100%', height: IMG_HEIGHT },
  backdropFade: {
    position: 'absolute', bottom: 0, left: 0, right: 0, height: IMG_HEIGHT * 0.65,
  },
  glassHeroSection: {
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 14,
    alignItems: 'center',
  },
  glassHeroCard: {
    width: '100%',
    aspectRatio: 16 / 10,
    borderRadius: 26,
    overflow: 'hidden',
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.34)' : 'rgba(255,255,255,0.09)',
    borderWidth: 1,
    borderColor: isLightAppearance ? 'rgba(255,255,255,0.58)' : 'rgba(255,255,255,0.18)',
    shadowColor: '#000',
    shadowOpacity: isLightAppearance ? 0.18 : 0.38,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 14 },
    elevation: 10,
  },
  glassHeroImage: {
    width: '100%',
    height: '100%',
  },
  glassHeroScrim: {
    ...StyleSheet.absoluteFillObject,
  } as any,
  backBtn: {
    position: 'absolute', left: 16,
    width: 48, height: 48, borderRadius: 24,
    backgroundColor: 'transparent',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: isLightAppearance ? 'rgba(255,255,255,0.52)' : 'rgba(255,255,255,0.14)',
    justifyContent: 'center', alignItems: 'center',
    shadowColor: '#000',
    shadowOpacity: isLightAppearance ? 0.12 : 0.24,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
    elevation: 10,
  },
  backBtnGlassTint: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.06)' : 'rgba(15,23,42,0.08)',
  },
  backBtnGlassHighlight: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 24,
    borderTopWidth: 1,
    borderTopColor: isLightAppearance ? 'rgba(255,255,255,0.30)' : 'rgba(255,255,255,0.08)',
  },
  titleSection: {
    paddingHorizontal: 20, paddingTop: 12, paddingBottom: 16,
    borderBottomColor: c.border,
    backgroundColor: isLightAppearance ? (vividAmbient ? c.bg + '80' : c.bg) : 'transparent',
  },
  glassTitleSection: {
    backgroundColor: 'transparent',
    borderBottomColor: 'transparent',
  },
  showTitle:  { color: c.textPrimary, fontSize: 20, fontWeight: '900', letterSpacing: 0.2, marginBottom: 4 },
  epLabel:    { color: c.accentSoft, fontSize: 13, fontWeight: '700', letterSpacing: 0.3 },
  epName:     { color: c.subText, fontSize: 12, lineHeight: 18, marginTop: 2 },
  epOverview: { color: c.subText, fontSize: 13, lineHeight: 20, marginTop: 8 },
  epMetaRow:  { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginTop: 6 },
  epDate:     { color: c.mutedText, fontSize: 11, fontWeight: '700', flex: 1 },
  progressTrack: { position: 'absolute', left: 0, right: 0, bottom: 0, height: 4, backgroundColor: c.border, overflow: 'hidden' },
  progressFill: { height: 4, borderRadius: 999, backgroundColor: c.accent },
  // Filter pills
  filterScroll: { marginBottom: 14 },
  filterPill: {
    paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20,
    borderWidth: 1, borderColor: c.border, backgroundColor: c.cardBg,
  },
  filterPillOn: {
    borderColor: isLightAppearance ? 'rgba(17,24,39,0.30)' : (isMonochromeDark ? c.textPrimary : c.accent),
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.88)' : (isMonochromeDark ? '#181818' : c.accent + '22'),
  },
  filterText:   { fontSize: 13, fontWeight: '600', color: c.mutedText },
  filterTextOn: { color: isLightAppearance ? c.textPrimary : (isMonochromeDark ? c.textPrimary : c.accentSoft), fontWeight: '700' },
  // Empty states
  emptyWrap: { alignItems: 'center', paddingHorizontal: 8, paddingTop: 40, paddingBottom: 24 },
  emptyIcon: {
    width: 64, height: 64, borderRadius: 32, justifyContent: 'center', alignItems: 'center', marginBottom: 20,
  },
  emptyTitle: { color: c.textPrimary, fontSize: 17, fontWeight: '800', textAlign: 'center', marginBottom: 10 },
  emptyDesc:  { color: c.subText, fontSize: 13, textAlign: 'center', lineHeight: 20, marginBottom: 28, maxWidth: 280 },
  emptyBtn: {
    backgroundColor: c.accent, borderRadius: 999, paddingVertical: 14,
    paddingHorizontal: 32, width: '100%', alignItems: 'center',
  },
  emptyBtnText: { color: c.buttonText, fontSize: 15, fontWeight: '800' },
  infoTip: {
    flexDirection: 'row', gap: 8, padding: 12, borderRadius: 10, marginTop: 16, width: '100%',
    backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border,
  },
  playBtnTextDisabled: { color: c.mutedText },
  // Watched
  watchedRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  watchedBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    paddingHorizontal: 12, paddingVertical: 7, borderRadius: 20,
    borderWidth: 1, borderColor: c.border, backgroundColor: c.cardBg,
  },
  glassWatchedBtn: {
    overflow: 'hidden',
    borderColor: isLightAppearance ? 'rgba(255,255,255,0.30)' : 'rgba(255,255,255,0.14)',
    backgroundColor: isLightAppearance ? 'rgba(8,10,14,0.16)' : 'rgba(8,10,14,0.28)',
  },
  watchedBtnActive: { borderColor: isLightAppearance ? 'rgba(27,94,32,0.45)' : '#00e676', backgroundColor: isLightAppearance ? 'rgba(27,94,32,0.12)' : '#00e67618' },
  watchedBtnText: { fontSize: 12, fontWeight: '700', color: c.mutedText },
  watchedBtnTextActive: { color: isLightAppearance ? '#1b5e20' : '#00e676' },
  // Debrid banner
  debridBanner: {
    flexDirection: 'row', gap: 10, padding: 13, borderRadius: 12, marginBottom: 16,
    backgroundColor: '#f5a62314', borderWidth: 1, borderColor: '#f5a62344', alignItems: 'center',
  },
  // Streams
  addonLabel: {
    color: c.mutedText, fontSize: 11, fontWeight: '700',
    letterSpacing: 0.8, textTransform: 'uppercase', marginBottom: 8,
  },
  });
};

// ── StreamRow ─────────────────────────────────────────────────────────────────

function StreamRow({ stream, colors, onPlay, isLightAppearance, glass = false }: { stream: AddonStream; colors: any; onPlay: () => void; isLightAppearance: boolean; glass?: boolean }) {
  const parsed = parseStream(stream);
  const isCached = stream.cachedBy.length > 0;
  const isMonochromeDark = !isLightAppearance && colors.accent === '#ffffff' && colors.buttonText === '#111111';
  const { vividAmbientEnabled } = useDisplaySettings();

  const qualityColors: Record<string, { bg: string; text: string }> = {
    '4K':    { bg: colors.bg === '#f4f6fb' ? 'rgba(17,24,39,0.16)' : '#FFD70022', text: colors.bg === '#f4f6fb' ? '#101828' : '#FFD700' },
    '1080p': { bg: colors.bg === '#f4f6fb' ? 'rgba(17,24,39,0.16)' : '#00e67622', text: colors.bg === '#f4f6fb' ? '#101828' : '#00e676' },
    '720p':  { bg: colors.bg === '#f4f6fb' ? 'rgba(17,24,39,0.14)' : '#29b6f622', text: colors.bg === '#f4f6fb' ? '#101828' : '#29b6f6' },
    '480p':  { bg: colors.bg === '#f4f6fb' ? 'rgba(17,24,39,0.12)' : '#78909c22', text: colors.bg === '#f4f6fb' ? '#101828' : '#78909c' },
  };
  const qColor = parsed.quality
    ? (isMonochromeDark
      ? { bg: colors.cardBg, text: colors.textPrimary }
      : (qualityColors[parsed.quality] ?? { bg: colors.bg === '#f4f6fb' ? 'rgba(17,24,39,0.12)' : '#a89ff822', text: colors.bg === '#f4f6fb' ? '#101828' : '#a89ff8' }))
    : { bg: colors.inputBg, text: colors.mutedText };

  const rowStyle = {
    flexDirection: 'row' as const,
    alignItems: 'center' as const,
    gap: 10,
    padding: 12,
    backgroundColor: glass
      ? (isLightAppearance ? 'rgba(8,10,14,0.18)' : 'rgba(8,10,14,0.34)')
      : isMonochromeDark ? colors.cardBg : (isLightAppearance ? colors.cardBg : (vividAmbientEnabled ? colors.inputBg + '99' : colors.inputBg)),
    borderRadius: glass ? 16 : 12,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: glass
      ? (isLightAppearance ? 'rgba(255,255,255,0.30)' : 'rgba(255,255,255,0.14)')
      : isLightAppearance
        ? (isCached ? 'rgba(0, 188, 212, 0.28)' : colors.border)
        : (isCached ? colors.toggleOn + '33' : colors.border),
    overflow: 'hidden' as const,
    shadowColor: '#000',
    shadowOpacity: glass ? 0 : (isLightAppearance ? 0.06 : 0),
    shadowRadius: glass ? 0 : (isLightAppearance ? 4 : 0),
    shadowOffset: { width: 0, height: glass ? 0 : 1 },
    elevation: glass ? 0 : (isLightAppearance ? 1 : 0),
  };

  return (
    <TouchableOpacity
      onPress={onPlay}
      activeOpacity={0.75}
      style={rowStyle}
    >
      {/* Quality badge */}
      <View style={{ paddingHorizontal: 7, paddingVertical: 5, borderRadius: 6, backgroundColor: qColor.bg, minWidth: 46, alignItems: 'center' }}>
        <Text style={{ fontSize: 10, fontWeight: '900', color: qColor.text }}>{parsed.quality ?? '?'}</Text>
      </View>

      {/* Meta */}
      <View style={{ flex: 1 }}>
        {/* Provider / release group */}
        <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '700', marginBottom: 2 }} numberOfLines={1}>
          {parsed.providerLine}
        </Text>

        {/* Media Title (Filename) */}
        {!!parsed.fileTitle && (
          <ExpandableText
            text={parsed.fileTitle}
            style={{ color: colors.subText, fontSize: 11, marginBottom: 4, lineHeight: 16 }}
            maxLines={3}
            moreColor={colors.accentSoft}
          />
        )}

        {/* Spec line: source • codec • audio • HDR */}
        {!!parsed.specLine && (
          <Text style={{ color: colors.accentSoft, fontSize: 11, fontWeight: '600', marginBottom: 4 }} numberOfLines={1}>
            {parsed.specLine}
          </Text>
        )}

        {/* Tag pills */}
        <View style={{ flexDirection: 'row', gap: 4, flexWrap: 'wrap' }}>
          {parsed.size && (
            <View style={{ paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, backgroundColor: glass ? (isLightAppearance ? 'rgba(8,10,14,0.14)' : 'rgba(255,255,255,0.06)') : colors.cardBg, borderWidth: 1, borderColor: glass ? (isLightAppearance ? 'rgba(255,255,255,0.18)' : 'rgba(255,255,255,0.10)') : colors.border }}>
              <Text style={{ color: colors.mutedText, fontSize: 9, fontWeight: '700' }}>💾 {parsed.size}</Text>
            </View>
          )}
          {parsed.seeds != null && (
            <View style={{ paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, backgroundColor: glass ? (isLightAppearance ? 'rgba(8,10,14,0.14)' : 'rgba(255,255,255,0.06)') : colors.cardBg, borderWidth: 1, borderColor: glass ? (isLightAppearance ? 'rgba(255,255,255,0.18)' : 'rgba(255,255,255,0.10)') : colors.border }}>
              <Text style={{ color: colors.mutedText, fontSize: 9, fontWeight: '700' }}>👤 {formatSeeds(parsed.seeds)}</Text>
            </View>
          )}
          {isCached && stream.cachedBy.map(provider => (
            <View key={provider} style={{ paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, backgroundColor: isMonochromeDark ? colors.cardBg : colors.toggleOn + '22', borderWidth: isMonochromeDark ? 1 : 0, borderColor: isMonochromeDark ? colors.border : 'transparent' }}>
              <Text style={{ color: isMonochromeDark ? colors.textPrimary : colors.toggleOn, fontSize: 9, fontWeight: '700' }}>⚡ {provider}</Text>
            </View>
          ))}
          {stream.url && !isCached && (
            <View style={{ paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, backgroundColor: glass ? (isLightAppearance ? 'rgba(8,10,14,0.14)' : 'rgba(255,255,255,0.06)') : colors.cardBg, borderWidth: 1, borderColor: glass ? (isLightAppearance ? 'rgba(255,255,255,0.18)' : 'rgba(255,255,255,0.10)') : colors.border }}>
              <Text style={{ color: colors.mutedText, fontSize: 9, fontWeight: '700' }}>DIRECT</Text>
            </View>
          )}
        </View>
      </View>

      <Ionicons name="play-circle-outline" size={22} color={isCached ? (isMonochromeDark ? colors.textPrimary : colors.toggleOn) : colors.accentSoft} />
    </TouchableOpacity>
  );
}

// ── Screen ────────────────────────────────────────────────────────────────────

export const EpisodeStreamsScreen = ({ route, navigation }: any) => {
  const expoGoRuntime = isExpoGoRuntime();
  const {
    showId, showTitle, showPoster, showBackdrop, imdbId,
    season, episodeNumber, episodeName, episodeOverview, episodeReleaseDate, episodeRuntime,
    progressKey: routeProgressKey,
  } = route.params || {};

  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const { t } = useLanguage();
  const { vividAmbientEnabled } = useDisplaySettings();
  const { uiStyle } = useUIStyle();
  const { enabled: streamSelectionEnabled, preferredQuality, maxFileSizeGB } = useStreamSelectionSettings();
  const isLightAppearance = resolvedAppearance === 'light';
  const styles = useMemo(() => makeStyles(colors, isLightAppearance, vividAmbientEnabled), [colors, isLightAppearance, vividAmbientEnabled]);
  const blurTargetRef = useRef<View | null>(null);
  const { fetchStreamsProgressive, addons, ultraEntitled, ultraBoostEnabled } = useAddons();
  const { isEpisodeWatched, toggleEpisodeWatched } = useWatched();
  const { accounts: debridAccounts, resolveStream } = useDebrid();
  const { getProgress } = useWatchProgress();

  const [debridSheet,     setDebridSheet]     = useState(false);
  const [streams,         setStreams]         = useState<AddonStream[]>([]);
  const [loading,         setLoading]         = useState(true);
  const [pendingAddons,   setPendingAddons]   = useState(0);
  const [resolvingStream, setResolvingStream] = useState(false);
  const [headerHeight,    setHeaderHeight]    = useState(0);
  const [selectedAddon,   setSelectedAddon]   = useState<string>('all');

  // AbortController for the in-flight progressive fetch
  const abortRef = useRef<AbortController | null>(null);

  const enabledAddons = addons.filter(a => a.enabled);
  const ultraActive = ultraEntitled && ultraBoostEnabled;
  const hasStreamSources = enabledAddons.length > 0 || ultraActive;
  const sourceCount = enabledAddons.length + (ultraActive ? 1 : 0);

  const addonNames = useMemo(
    () => [...new Set(streams.map(s => s.addonName))],
    [streams],
  );

  const safeAddon = addonNames.includes(selectedAddon) ? selectedAddon : 'all';

  const visibleStreams = safeAddon === 'all'
    ? streams
    : streams.filter(s => s.addonName === safeAddon);

  // Sort by debrid priority (accounts already ordered by user priority), then by comprehensive stream score
  const streamOptions = useMemo(() => ({
    preferredQuality,
    maxFileSizeGB: maxFileSizeGB > 0 ? maxFileSizeGB : undefined,
  }), [maxFileSizeGB, preferredQuality]);

  const sortedVisibleStreams = sortStreams(visibleStreams, streamOptions).slice(0, 20);

  const grouped: Record<string, AddonStream[]> = {};
  for (const s of sortedVisibleStreams) {
    if (!grouped[s.addonName]) grouped[s.addonName] = [];
    grouped[s.addonName].push(s);
  }
  const useGlassDetailLayout = uiStyle === 'glass';

  const epCode = `S${String(season).padStart(2, '0')}E${String(episodeNumber).padStart(2, '0')}`;
  const playerTitle = `${showTitle}  ${epCode}`;
  const episodeMetaLabel = [formatReleaseDate(episodeReleaseDate), formatRuntimeLabel(Number(episodeRuntime ?? 0))]
    .filter(Boolean)
    .join(' · ');
  const resolvedProgressKey = useMemo(() => (
    typeof routeProgressKey === 'string' && routeProgressKey.trim().length > 0
      ? routeProgressKey
      : episodeProgressKey(Number(showId ?? 0), Number(season ?? 0), Number(episodeNumber ?? 0))
  ), [episodeNumber, routeProgressKey, season, showId]);
  const episodeProgress = useMemo(() => {
    if (isEpisodeWatched(Number(showId), season, episodeNumber)) return 100;
    const entry = getProgress(resolvedProgressKey);
    const pct = entry ? clampProgressPercent(entry.positionSec, entry.durationSec) : null;
    return pct != null && pct > 0 ? pct : null;
  }, [episodeNumber, getProgress, isEpisodeWatched, resolvedProgressKey, season, showId]);

  // Fetch streams for this episode progressively — each addon updates the UI
  // as soon as it responds instead of waiting for all of them.
  const fetchEpisodeStreams = useCallback(async () => {
    const currentEnabled = addons.filter(a => a.enabled);
    if (!user || (currentEnabled.length === 0 && !ultraActive)) { setLoading(false); return; }

    // Cancel any previous in-flight fetch
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setLoading(true);
    setStreams([]);

    const videoId = imdbId
      ? `${imdbId}:${season}:${episodeNumber}`
      : `${showId}:${season}:${episodeNumber}`;

    await fetchStreamsProgressive(
      'series',
      videoId,
      (newStreams, pending) => {
        setStreams(newStreams);
        setPendingAddons(pending);
        // Show results as soon as the first addon responds; hide full spinner
        if (newStreams.length > 0 || pending === 0) setLoading(false);
      },
      controller.signal,
    );
  }, [user, addons, ultraActive, imdbId, season, episodeNumber, showId, fetchStreamsProgressive]);

  // Cancel in-flight fetch on unmount
  useEffect(() => () => { abortRef.current?.abort(); }, []);

  // Stable ref so the focus listener always calls the latest version
  const fetchRef = useRef(fetchEpisodeStreams);
  useEffect(() => { fetchRef.current = fetchEpisodeStreams; }, [fetchEpisodeStreams]);

  // Live refs used by the focus listener to avoid stale closures
  const streamsLenRef = useRef(streams.length);
  streamsLenRef.current = streams.length;
  const loadingRef = useRef(loading);
  loadingRef.current = loading;

  // Initial fetch on mount
  useEffect(() => { fetchEpisodeStreams(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Re-fetch when returning from Addons with no results yet
  useEffect(() => {
    const unsub = navigation.addListener('focus', () => {
      if (streamsLenRef.current === 0 && !loadingRef.current) fetchRef.current();
    });
    return unsub;
  }, [navigation]);

  // Re-fetch when user logs in OR when addons first load after login
  const prevUserRef         = useRef(user);
  const prevEnabledCountRef = useRef(sourceCount);
  useEffect(() => {
    const prevUser  = prevUserRef.current;
    const prevCount = prevEnabledCountRef.current;
    prevUserRef.current          = user;
    prevEnabledCountRef.current  = sourceCount;

    const userLoggedIn   = !!user && !prevUser;
    const addonsAppeared = sourceCount > 0 && prevCount === 0;

    if ((userLoggedIn || addonsAppeared) && user && hasStreamSources && streams.length === 0) {
      fetchEpisodeStreams();
    }
  }, [user, sourceCount, hasStreamSources]); // eslint-disable-line react-hooks/exhaustive-deps

  const episodeHeroUri = route.params?.episodeStill ?? showBackdrop ?? showPoster;
  const backdropUri = showBackdrop ?? showPoster;
  const ambientBackdropUri = useGlassDetailLayout ? backdropUri : (episodeHeroUri ?? backdropUri);
  const activeSourceIdentity = (stream: AddonStream) => (
    (stream.infoHash ?? stream.url ?? stream.behaviorHints?.filename ?? stream.title ?? stream.name ?? '')
      .trim()
      .replace(/\s+/g, ' ')
      .toLowerCase()
  );

  const playStream = useCallback(async (stream: AddonStream) => {
    if (stream.url) {
      navigation.navigate('Player', {
        movieId: String(showId ?? ''),
        imdbId,
        type: 'tv',
        title: playerTitle,
        synopsis: episodeOverview ?? undefined,
        streamUrl: stream.url,
        activeStream: stream,
        streams,
        backdrop: backdropUri,
        poster: showPoster,
        season,
        episode: episodeNumber,
        progressKey: resolvedProgressKey,
        resolverMovieId: String(showId ?? ''),
        resolverImdbId: imdbId,
        resolverType: 'tv',
        sourceStreams: streams,
        activeSourceIdentity: activeSourceIdentity(stream),
        returnToPlayerParams: {
          movieId: String(showId ?? ''),
          imdbId,
          type: 'tv',
          title: playerTitle,
          synopsis: episodeOverview ?? undefined,
          backdrop: backdropUri,
          poster: showPoster,
          season,
          episode: episodeNumber,
          progressKey: resolvedProgressKey,
        },
      });
      return;
    }
    if (stream.infoHash) {
      if (debridAccounts.length === 0) {
        setDebridSheet(true);
        return;
      }
      setResolvingStream(true);
      const hint   = stream.behaviorHints?.filename;
      const magnet = `magnet:?xt=urn:btih:${stream.infoHash}${hint ? `&dn=${encodeURIComponent(hint)}` : ''}`;
      const resolved = await resolveStream(stream.infoHash, magnet, hint);
      setResolvingStream(false);
      if (resolved) {
        navigation.navigate('Player', {
          movieId: String(showId ?? ''),
          imdbId,
          type: 'tv',
          title: playerTitle,
          synopsis: episodeOverview ?? undefined,
          streamUrl: resolved.url,
          activeStream: stream,
          streams,
          backdrop: backdropUri,
          poster: showPoster,
          season,
          episode: episodeNumber,
          progressKey: resolvedProgressKey,
          resolverMovieId: String(showId ?? ''),
          resolverImdbId: imdbId,
          resolverType: 'tv',
          sourceStreams: streams,
          activeSourceIdentity: activeSourceIdentity(stream),
          returnToPlayerParams: {
            movieId: String(showId ?? ''),
            imdbId,
            type: 'tv',
            title: playerTitle,
            backdrop: backdropUri,
            poster: showPoster,
            season,
            episode: episodeNumber,
            progressKey: resolvedProgressKey,
          },
        });
      }
    }
  }, [navigation, playerTitle, debridAccounts, resolveStream, streams, backdropUri, showPoster, showId, imdbId, season, episodeNumber, resolvedProgressKey]);

  // Auto-picks the highest-scored stream and navigates immediately to MpvPlayer,
  // skipping the intermediate PlayerScreen so the loading overlay shows at once.
  const playBestStream = useCallback(() => {
    if (streams.length === 0) return;
    const best = selectBestStream(streams, streamOptions);
    if (!best) return;

    const sortedAll = sortStreams(streams, streamOptions);

    const sharedParams = {
      title: playerTitle,
      synopsis: episodeOverview ?? undefined,
      type: 'tv' as const,
      season,
      episode: episodeNumber,
      progressKey: resolvedProgressKey,
      backdrop: backdropUri,
      poster: showPoster,
      resolverImdbId: imdbId,
      resolverMovieId: String(showId ?? ''),
      resolverType: 'tv',
      returnToPlayerParams: {
        movieId: String(showId ?? ''),
        imdbId,
        type: 'tv',
        title: playerTitle,
        synopsis: episodeOverview ?? undefined,
        backdrop: backdropUri,
        poster: showPoster,
        season,
        episode: episodeNumber,
        progressKey: resolvedProgressKey,
      },
    };

    if (best.url) {
      navigation.navigate(expoGoRuntime ? 'Player' : 'MpvPlayer', {
        ...sharedParams,
        streamUrl:    best.url,
        sourceStreams: sortedAll,
      });
      return;
    }

    if (best.infoHash) {
      if (debridAccounts.length === 0) {
        setDebridSheet(true);
        return;
      }
      // Compute the same identity key used by MpvPlayerScreen so it picks this stream first.
      const preferredSourceIdentity = (
        best.infoHash ?? best.url ?? best.behaviorHints?.filename ?? best.title ?? best.name ?? ''
      ).trim().replace(/\s+/g, ' ').toLowerCase();

      navigation.navigate(expoGoRuntime ? 'Player' : 'MpvPlayer', {
        ...sharedParams,
        resolveOnMount:          true,
        sourceStreams:            sortedAll,
        preferredSourceIdentity,
      });
    }
  }, [expoGoRuntime, streams, debridAccounts.length, navigation, playerTitle, episodeOverview, season, episodeNumber,
      backdropUri, showPoster, imdbId, showId, streamSelectionEnabled, resolvedProgressKey, streamOptions]);

  // ── Render helpers ─────────────────────────────────────────────────────────

  const renderContent = () => {
    // Full-screen spinner only while we have zero results and addons are still pending
    if (loading && streams.length === 0) {
      return (
        <View style={{ alignItems: 'center', paddingTop: 48 }}>
          <ActivityIndicator color={colors.textPrimary} size="large" />
          <Text style={{ color: colors.subText, fontSize: 13, marginTop: 14 }}>
            {t('streams_searching', { n: sourceCount, plural: sourceCount !== 1 ? 's' : '' })}
          </Text>
        </View>
      );
    }

    if (!hasStreamSources) {
      return (
        <View style={styles.emptyWrap}>
          <View style={[styles.emptyIcon, { backgroundColor: colors.accent + '18' }]}>
            <Ionicons name="extension-puzzle-outline" size={32} color={colors.accent} />
          </View>
          <Text style={styles.emptyTitle}>{t('streams_no_addons_title')}</Text>
          <Text style={styles.emptyDesc}>{t('streams_no_addons_desc')}</Text>
          <TouchableOpacity style={styles.emptyBtn} onPress={() => navigation.navigate('Addons')} activeOpacity={0.85}>
            <Text style={styles.emptyBtnText}>{t('streams_setup_addons_btn')}</Text>
          </TouchableOpacity>
          <View style={styles.infoTip}>
            <Ionicons name="information-circle-outline" size={15} color={colors.mutedText} style={{ marginTop: 1 }} />
            <Text style={{ flex: 1, color: colors.mutedText, fontSize: 11, lineHeight: 17 }}>
              Popular sources include Torrentio and AIOStreams. Pair with a Debrid service to get instant, buffer-free streams.
            </Text>
          </View>
        </View>
      );
    }

    if (streams.length === 0) {
      return (
        <View style={styles.emptyWrap}>
          <Ionicons name="cloud-offline-outline" size={40} color={colors.placeholder} style={{ marginBottom: 16 }} />
          <Text style={styles.emptyTitle}>{t('streams_no_results_title')}</Text>
          <Text style={styles.emptyDesc}>
            {t('streams_no_results_desc', { n: sourceCount, plural: sourceCount !== 1 ? 's' : '' })}
          </Text>
          <TouchableOpacity
            onPress={() => navigation.navigate('Addons')}
            activeOpacity={0.85}
            style={{
              borderRadius: 12, paddingVertical: 11, paddingHorizontal: 24,
              borderWidth: 1, borderColor: colors.accent + '66', backgroundColor: colors.accent + '12',
            }}
          >
            <Text style={{ color: colors.accentSoft, fontSize: 13, fontWeight: '700' }}>{t('streams_manage_addons')}</Text>
          </TouchableOpacity>
        </View>
      );
    }

    return (
      <View>
        {/* Inline "still searching" banner while more addons are pending */}
        {pendingAddons > 0 && (
          <View style={{
            flexDirection: 'row', alignItems: 'center', gap: 10,
            paddingHorizontal: 14, paddingVertical: 10, borderRadius: 10,
            backgroundColor: colors.cardBg, borderWidth: 1, borderColor: colors.border,
            marginBottom: 14,
          }}>
            <ActivityIndicator size="small" color={colors.textPrimary} />
            <Text style={{ color: colors.subText, fontSize: 12, flex: 1 }}>
              {t('streams_searching_more', { n: pendingAddons, plural: pendingAddons !== 1 ? 's' : '' })}
            </Text>
          </View>
        )}

        {/* Debrid banner */}
        {debridAccounts.length === 0 && (
          <TouchableOpacity
            onPress={() => navigation.navigate('Addons', { initialTab: 'debrid' })}
            activeOpacity={0.85}
            style={styles.debridBanner}
          >
            <Ionicons name="flash-outline" size={16} color="#f5a623" />
            <View style={{ flex: 1 }}>
              <Text style={{ color: '#f5a623', fontSize: 12, fontWeight: '700', marginBottom: 2 }}>{t('streams_unlock_instant')}</Text>
              <Text style={{ color: '#f5a623cc', fontSize: 11, lineHeight: 16 }}>
                {t('streams_debrid_banner_desc')}
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={14} color="#f5a623" />
          </TouchableOpacity>
        )}

        {/* Grouped stream rows */}
        {Object.entries(grouped).map(([addonName, addonStreams]) => (
          <View key={addonName} style={{ marginBottom: 20 }}>
            {safeAddon === 'all' && (
              <Text style={styles.addonLabel}>{addonName}</Text>
            )}
            {addonStreams.map((stream, idx) => (
              <StreamRow
                key={`${addonName}-${idx}`}
                stream={stream}
                colors={colors}
                isLightAppearance={isLightAppearance}
                glass={useGlassDetailLayout}
                onPlay={() => playStream(stream)}
              />
            ))}
          </View>
        ))}
      </View>
    );
  };

  const PageWrapper: any = ScrollView;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
        {(vividAmbientEnabled || useGlassDetailLayout) && ambientBackdropUri ? (
          <View pointerEvents="none" style={styles.ambientBackdrop}>
            <RNImage
              source={{ uri: ambientBackdropUri }}
              style={useGlassDetailLayout ? styles.glassAmbientBackdropImage : styles.ambientBackdropImage}
              resizeMode="cover"
              blurRadius={useGlassDetailLayout ? 34 : 20}
            />
            {useGlassDetailLayout ? <View style={styles.glassAmbientVeil} /> : null}
            <LinearGradient
              colors={isLightAppearance
                ? ['rgba(255,255,255,0.02)', 'rgba(255,255,255,0.32)', colors.bg]
                : ['rgba(7,8,12,0.00)', 'rgba(7,8,12,0.44)', colors.bg]}
              locations={[0, 0.54, 1]}
              style={styles.ambientBackdropScrim}
            />
          </View>
        ) : null}
        {/* Back button — fixed outside scroll so it never moves */}
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={[styles.backBtn, { top: insets.top + 12, zIndex: 20 }]}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
        >
          <BlurView
            tint={isLightAppearance ? 'light' : 'dark'}
            intensity={isLightAppearance ? 80 : 72}
            blurMethod={Platform.OS === 'android' ? 'dimezisBlurViewSdk31Plus' : undefined}
            blurTarget={Platform.OS === 'android' ? blurTargetRef : undefined}
            style={StyleSheet.absoluteFillObject}
          />
          <View pointerEvents="none" style={styles.backBtnGlassTint} />
          <View
            pointerEvents="none"
            style={[StyleSheet.absoluteFillObject, {
              borderRadius: 24,
              borderWidth: 1,
              borderColor: isLightAppearance ? 'rgba(255,255,255,0.56)' : 'rgba(255,255,255,0.16)',
              backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.08)' : 'rgba(10,12,18,0.10)',
            }]}
          />
          <View pointerEvents="none" style={styles.backBtnGlassHighlight} />
          <Ionicons name="chevron-back" size={20} color={isLightAppearance ? colors.textPrimary : '#ffffff'} />
        </TouchableOpacity>
        <PageWrapper
          style={useGlassDetailLayout ? styles.glassContainer : styles.container}
          contentContainerStyle={{ flexGrow: 1 }}
          showsVerticalScrollIndicator={false}
        >
        <StatusBar barStyle="light-content" translucent backgroundColor="transparent" />

      {/* Fixed header: backdrop image + gradient + back button + title */}
      <View
        style={styles.headerBlock}
        onLayout={e => setHeaderHeight(e.nativeEvent.layout.height)}
      >
        {useGlassDetailLayout ? (
          <View style={[styles.glassHeroSection, { paddingTop: insets.top + 14 }]}>
            <View style={styles.glassHeroCard}>
              {episodeHeroUri ? (
                <Image source={{ uri: episodeHeroUri }} style={styles.glassHeroImage} contentFit="cover" transition={220} />
              ) : null}
              <LinearGradient
                colors={['rgba(255,255,255,0.00)', isLightAppearance ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.18)']}
                locations={[0.62, 1]}
                style={styles.glassHeroScrim}
                pointerEvents="none"
              />
            </View>
          </View>
        ) : (
          <View style={{ width: '100%', height: IMG_HEIGHT }}>
            <Image
              source={{ uri: backdropUri }}
              style={styles.backdrop}
              contentFit="cover"
              transition={300}
            />
            {!isLightAppearance && (
              <LinearGradient
                colors={['transparent', colors.bg]}
                locations={[0.35, 1]}
                style={styles.backdropFade}
                pointerEvents="none"
              />
            )}
          </View>
        )}

        {/* Title section below image */}
        <View style={[styles.titleSection, useGlassDetailLayout && styles.glassTitleSection]}>
          <Text style={styles.showTitle} numberOfLines={1}>{showTitle}</Text>
          <Text style={styles.epLabel}>{epCode}</Text>
          {!!episodeName && (
            <Text style={styles.epName} numberOfLines={2}>{episodeName}</Text>
          )}
          {!!episodeOverview && (
            <ExpandableText
              text={episodeOverview}
              style={styles.epOverview}
              maxLines={3}
              moreColor={colors.accentSoft}
            />
          )}
          {(() => {
            const watched = isEpisodeWatched(Number(showId), season, episodeNumber);
            return (
              <View style={styles.epMetaRow}>
                {episodeMetaLabel ? (
                  <Text style={styles.epDate}>{episodeMetaLabel}</Text>
                ) : (
                  <View style={{ flex: 1 }} />
                )}
                <View style={styles.watchedRow}>
                  <TouchableOpacity
                    style={[styles.watchedBtn, useGlassDetailLayout && styles.glassWatchedBtn, watched && styles.watchedBtnActive]}
                    activeOpacity={0.75}
                    onPress={() => toggleEpisodeWatched(
                      Number(showId),
                      undefined,
                      showTitle,
                      season,
                      episodeNumber,
                    )}
                  >
                    <Ionicons
                      name={watched ? 'checkmark-circle' : 'checkmark-circle-outline'}
                      size={14}
                      color={watched ? '#00e676' : colors.mutedText}
                    />
                    <Text style={[styles.watchedBtnText, watched && styles.watchedBtnTextActive]}>
                      {watched ? t('watched_badge') : t('watched_mark')}
                    </Text>
                  </TouchableOpacity>
                </View>
              </View>
            );
          })()}

          {/* Play best stream button */}
          {hasStreamSources && (() => {
            const ready = streams.length > 0;
            const searching = (loading || pendingAddons > 0) && !ready;
            const buttonProgress = episodeProgress != null && episodeProgress < 100 ? episodeProgress : null;
            const primaryPalette = getPrimaryActionPalette(colors, theme.id, isLightAppearance, useGlassDetailLayout ? 'glass' : 'solid');
            const disabled = !ready && !searching;
            return (
              <PrimaryActionButton
                colors={colors}
                themeId={theme.id}
                isLightAppearance={isLightAppearance}
                surface={useGlassDetailLayout ? 'glass' : 'solid'}
                blurTarget={blurTargetRef}
                fullWidth
                style={{ marginTop: 14 }}
                disabled={disabled}
                activeOpacity={0.85}
                onPress={playBestStream}
                progressPct={buttonProgress}
                label={searching ? 'Searching streams…' : 'Play Best Stream'}
                labelStyle={disabled ? styles.playBtnTextDisabled : undefined}
                leading={searching ? (
                  <ActivityIndicator size="small" color={disabled ? colors.mutedText : primaryPalette.textColor} />
                ) : (
                  <Ionicons name="play" size={15} color={disabled ? colors.mutedText : primaryPalette.textColor} />
                )}
                trailing={pendingAddons > 0 && ready ? (
                  <ActivityIndicator size="small" color={primaryPalette.textColor} />
                ) : undefined}
              />
            );
          })()}
        </View>
      </View>

      {/*
        Flex layout below the absolute header:
        1. Spacer fills the header height so the filter bar and list sit below it
        2. Static filter bar — never scrolls, streams scroll under it
        3. ScrollView fills remaining space
      */}
      <View style={{ height: headerHeight }} />

      {addonNames.length > 1 && (
        <View style={{
          backgroundColor: isLightAppearance ? colors.bg + '80' : 'transparent',
          borderBottomWidth: isLightAppearance ? StyleSheet.hairlineWidth : 0,
          borderBottomColor: colors.border,
        }}>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            style={styles.filterScroll}
            contentContainerStyle={{ gap: 8, paddingHorizontal: 20, paddingRight: 24 }}
          >
            {(['all', ...addonNames] as string[]).map(name => {
              const active = safeAddon === name;
              return (
                <TouchableOpacity
                  key={name}
                  onPress={() => setSelectedAddon(name)}
                  activeOpacity={0.75}
                  style={[styles.filterPill, active && styles.filterPillOn]}
                >
                  <Text style={[styles.filterText, active && styles.filterTextOn]}>
                    {name === 'all' ? t('streams_all_sources') : name}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        </View>
      )}

      {/* Scrollable streams content */}
        <ScrollView
          style={{ flex: 1 }}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{
            paddingTop: 16,
            paddingHorizontal: 20,
            paddingBottom: BOTTOM_NAV_HEIGHT + 60,
          }}
          scrollEnabled={false}
          nestedScrollEnabled={false}
        >
        {renderContent()}
      </ScrollView>

        <ActionSheet
          visible={debridSheet}
          onClose={() => setDebridSheet(false)}
          title={t('streams_debrid_required_title')}
          subtitle={t('streams_debrid_required_desc')}
          actions={[
            {
              label: 'Find Direct Sources',
              icon: 'extension-puzzle-outline',
              variant: 'accent',
              onPress: () => navigation.navigate('Addons', { initialTab: 'addons' }),
            },
            {
              label: t('streams_setup_debrid'),
              icon: 'flash-outline',
              variant: 'default',
              onPress: () => navigation.navigate('Addons', { initialTab: 'debrid' }),
            },
            {
              label: t('common_cancel'),
              icon: 'close-outline',
              variant: 'cancel',
              onPress: () => {},
            },
          ]}
        />

        {/* Debrid resolving overlay */}
        <Modal visible={resolvingStream} transparent animationType="fade">
          <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.65)', justifyContent: 'center', alignItems: 'center', padding: 32 }}>
            <View style={{
              backgroundColor: isLightAppearance ? '#ffffff' : colors.cardBgElevated ?? colors.cardBg,
              borderRadius: 16, padding: 28, alignItems: 'center', width: '100%',
              borderWidth: 1, borderColor: colors.border,
              shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.18, shadowRadius: 12, elevation: 8,
            }}>
              <ActivityIndicator size="large" color={colors.textPrimary} style={{ marginBottom: 16 }} />
              <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700', marginBottom: 6 }}>{t('streams_resolving')}</Text>
              <Text style={{ color: colors.mutedText, fontSize: 13, textAlign: 'center' }}>
                {t('streams_resolving_desc')}
              </Text>
            </View>
          </View>
        </Modal>
        </PageWrapper>
      </BlurTargetView>
      <StackBottomNav blurTarget={blurTargetRef} />
    </View>
  );
};
