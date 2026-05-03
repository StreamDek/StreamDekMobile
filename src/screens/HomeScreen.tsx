import React, { useEffect, useState, useCallback, useRef, useMemo } from 'react';
import { useFocusEffect, useScrollToTop } from '@react-navigation/native';
import {
  View, Text, StyleSheet, RefreshControl,
  Animated, Easing, PanResponder, StatusBar, TouchableOpacity,
  ScrollView, Dimensions, Share,
  Image as RNImage,
} from 'react-native';
import { Image } from 'expo-image';
import { BlurTargetView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Storage } from '../utils/storage';
import { API_BASE } from '../constants/api';
import { tmdbFetch } from '../utils/tmdbFetch';
import { SectionStrip } from '../components/SectionStrip';
import { NetworkStrip } from '../components/NetworkStrip';
import { ActionSheet } from '../components/ActionSheet';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { TrailerModal } from '../components/TrailerModal';
import { PrimaryActionButton, getPrimaryActionPalette } from '../components/PrimaryActionButton';
import { StackBottomNav } from '../components/StackBottomNav';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { Ionicons } from '@expo/vector-icons';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useUIStyle } from '../context/UIStyleContext';
import { useTrakt } from '../context/TraktContext';
import { useLanguage } from '../context/LanguageContext';
import { useWatched } from '../context/WatchedContext';
import { movieProgressKey } from '../context/WatchProgressContext';
import { useAppReady } from '../context/AppReadyContext';
import { buildAuthHeaders } from '../utils/authHeaders';
import {
  mergeWatchlistItems,
  normalizeWatchlistItem,
  readWatchlistItems,
  readWatchlistRemovalIds,
  watchlistItemMatchesId,
  writeWatchlistItems,
  writeWatchlistRemovalIds,
  uniqueItemsById,
} from '../utils/watchlist';
import { RatingBadge } from '../components/RatingBadge';
import { ContinueWatchingCard } from '../components/ContinueWatchingCard';
import { useDisplaySettings } from '../context/DisplaySettingsContext';
import { getProfileStorageOwnerId, profileScopedStorageKey, progressIndexStorageKey } from '../utils/profileStorage';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const HERO_HEIGHT = 616;
const DOCUMENTARY_SECTION = { id: 'documentaries', title: 'Documentaries', endpoint: '/tmdb/discover?type=movie&genre_id=99&sort_by=popularity.desc', enabled: false };

function formatContinueTime(positionSec?: number | null): string | null {
  if (!positionSec || !Number.isFinite(positionSec) || positionSec <= 0) return null;
  const totalSeconds = Math.max(0, Math.floor(positionSec));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function getContinueSeconds(item: any): number | null {
  const exactPosition = Number(item?.positionSec ?? 0);
  if (Number.isFinite(exactPosition) && exactPosition > 0) return exactPosition;

  const progressPct = Number(item?.progress ?? 0);
  const runtimeMinutes = Number(item?.runtime ?? 0);
  if (item?.type === 'movie' && Number.isFinite(progressPct) && progressPct > 0 && progressPct < 95 && Number.isFinite(runtimeMinutes) && runtimeMinutes > 0) {
    return Math.round((progressPct / 100) * runtimeMinutes * 60);
  }

  return null;
}

const makeStyles = (c: ThemeColors) => StyleSheet.create({
  container: { flex: 1, backgroundColor: c.bg },
  ambientBackdrop: {
    ...StyleSheet.absoluteFillObject,
  },
  ambientBackdropImage: {
    ...StyleSheet.absoluteFillObject,
    opacity: 0.60,
  },
  ambientBackdropScrim: {
    ...StyleSheet.absoluteFillObject,
  } as any,
  ambientBackdropGlow: {
    ...StyleSheet.absoluteFillObject,
    opacity: 0.72,
  } as any,
  topBar: {
    position: 'absolute', left: 0, right: 0, zIndex: 100,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 20,
  },
  topFade: {
    position: 'absolute', top: 0, left: 0, right: 0, zIndex: 99,
    pointerEvents: 'none',
  } as any,
  heroBottomFade: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 0,
    pointerEvents: 'none',
  } as any,
  iconBtn: {
    width: 38, height: 38, borderRadius: 19,
    backgroundColor: 'rgba(0,0,0,0.55)',
    justifyContent: 'center', alignItems: 'center',
    borderWidth: 1, borderColor: c.accent + '55',
  },
  heroSlide: { width: SCREEN_WIDTH, height: HERO_HEIGHT, overflow: 'hidden' },
  heroBg: { position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', resizeMode: 'cover' },
  heroPosterBackdropTint: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.18)',
  },
  heroPosterBottomBlend: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  } as any,
   heroContent: { position: 'absolute', bottom: 0, left: 0, right: 0, padding: 28, paddingBottom: 12, zIndex: 2 },
  heroBadge: { alignSelf: 'flex-start', borderRadius: 20, paddingHorizontal: 12, paddingVertical: 5, marginBottom: 12 },
  heroBadgeMovie: { backgroundColor: 'rgba(255,255,255,0.5)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.6)' },
  heroBadgeTv:    { backgroundColor: 'rgba(255,255,255,0.5)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.6)' },
  heroBadgeLight: {
    backgroundColor: 'rgba(255,255,255,0.92)',
    borderWidth: 1,
    borderColor: 'rgba(17,24,39,0.14)',
    shadowColor: '#000',
    shadowOpacity: 0.10,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  heroBadgeText: { color: '#111', fontSize: 11, fontWeight: '800', letterSpacing: 0.5, textTransform: 'uppercase' },
  heroTitle: {
    color: c.textPrimary, fontSize: 30, fontWeight: '900', marginBottom: 8, lineHeight: 36,
    textShadowColor: 'rgba(0,0,0,0.7)', textShadowRadius: 6, textShadowOffset: { width: 0, height: 1 },
  },
  heroTitleFallback: {
    color: '#ffffff',
    textShadowColor: 'rgba(0,0,0,0.95)',
    textShadowRadius: 6,
    textShadowOffset: { width: 0, height: 1 },
  },
  heroTitleLogo: {
    width: SCREEN_WIDTH * 0.65, height: 90, marginBottom: 8,
    shadowColor: 'rgba(0,0,0,0.95)',
    shadowOpacity: 0.42,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 1 },
    elevation: 3,
  },
  heroDesc: { color: c.textSecondary, fontSize: 13, lineHeight: 20, marginBottom: 0 },
  heroDescFallback: {
    color: '#ffffff',
    textShadowColor: 'rgba(0,0,0,0.95)',
    textShadowRadius: 5,
    textShadowOffset: { width: 0, height: 0 },
  },
  heroDescClassic: {
    textAlign: 'left',
    alignSelf: 'flex-start',
  },
  heroSynopsisWrap: {
    marginTop: 0,
    marginBottom: 8,
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 6,
    alignSelf: 'flex-start',
    maxWidth: '92%',
  },
  heroSynopsisWrapClassic: {
    alignSelf: 'flex-start',
    alignItems: 'flex-start',
    paddingHorizontal: 0,
    paddingVertical: 0,
  },
  heroSynopsisWrapLight: {
    backgroundColor: 'transparent',
    borderWidth: 0,
    borderColor: 'transparent',
    paddingHorizontal: 0,
    paddingVertical: 0,
    alignSelf: 'center',
    maxWidth: '92%',
  },
  heroSynopsisWrapCentered: {
    alignSelf: 'center',
    alignItems: 'center',
    maxWidth: '92%',
  },
  heroTopStack: {
    alignSelf: 'stretch',
    position: 'relative' as const,
    overflow: 'hidden',
    borderRadius: 34,
  },
  heroTopStackClassic: {
    width: SCREEN_WIDTH,
    marginHorizontal: -28,
    alignSelf: 'stretch',
    position: 'relative' as const,
    overflow: 'hidden',
    borderRadius: 34,
  },
  heroTopStackClassicContent: {
    paddingHorizontal: 28,
    position: 'relative' as const,
    zIndex: 1,
  },
  heroTopStackCentered: {
    alignSelf: 'stretch',
    width: SCREEN_WIDTH,
    marginHorizontal: -28,
    alignItems: 'center',
    position: 'relative' as const,
    overflow: 'hidden',
    borderRadius: 34,
  },
  heroCloudMask: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 34,
    opacity: 1,
  },
  heroCloudMaskLightBleed: {
    left: -40,
    right: -40,
  },
  heroActions: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  heroActionsClassic: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 4,
  },
  heroActionsClassicRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  heroActionBadgeDark: {
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.5)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.6)',
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
  },
  heroActionBadgeMonoDark: {
    backgroundColor: '#111111',
    borderWidth: 1,
    borderColor: '#262626',
  },
  playBtn: {
    width: Math.round(SCREEN_WIDTH * 0.43),
    backgroundColor: c.accent, borderRadius: 24, paddingHorizontal: 33, paddingVertical: 13,
    overflow: 'hidden', position: 'relative',
  },
  // Centered hero specific
  heroPlayBtnCentered: {
    backgroundColor: c.accent, borderRadius: 999,
    paddingVertical: 13, paddingHorizontal: 22,
    alignItems: 'center' as const, justifyContent: 'center' as const,
    overflow: 'hidden' as const,
    alignSelf: 'center' as const,
    width: '100%',
    maxWidth: 380,
    minWidth: 0,
    marginTop: 4,
    marginBottom: 2,
  },
  heroIconRow: {
    flexDirection: 'row' as const, justifyContent: 'center' as const,
    alignItems: 'center', gap: 8, marginTop: 14,
  },
  heroIconPill: {
    paddingHorizontal: 12, paddingVertical: 8, borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.5)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.6)',
    alignItems: 'center' as const, justifyContent: 'center' as const,
    flexDirection: 'row' as const, gap: 5,
  },
  heroIconPillMonoDark: {
    backgroundColor: '#111111',
    borderWidth: 1,
    borderColor: '#262626',
  },
  heroIconPillLight: {
    backgroundColor: 'rgba(255,255,255,0.92)',
    borderColor: 'rgba(17,24,39,0.14)',
    shadowColor: '#000',
    shadowOpacity: 0.10,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
  heroIconPillActive: { borderColor: c.accent + 'cc', backgroundColor: c.accent + '18' },
  heroIconPillActiveMonoDark: {
    borderColor: '#3a3a3a',
    backgroundColor: '#191919',
  },
  heroIconPillWatchedActive: { borderColor: '#00e676', backgroundColor: '#00e67618' },
  heroIconPillWatchedActiveMonoDark: {
    borderColor: '#3a3a3a',
    backgroundColor: '#191919',
  },
  ratingPill: { paddingHorizontal: 4, paddingVertical: 8 },
  ratingPillLight: {
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.5)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.6)',
  },
  ratingPillOuterClear: {
    paddingHorizontal: 0,
    paddingVertical: 0,
    borderRadius: 0,
    backgroundColor: 'transparent',
    borderWidth: 0,
    borderColor: 'transparent',
    shadowOpacity: 0,
    elevation: 0,
  },
  ratingPillLightClear: {
    paddingHorizontal: 0,
    paddingVertical: 0,
    borderRadius: 0,
    backgroundColor: 'transparent',
    borderWidth: 0,
    borderColor: 'transparent',
    shadowOpacity: 0,
    elevation: 0,
  },
  ratingPillMonoDark: {
    backgroundColor: '#111111',
    borderWidth: 1,
    borderColor: '#262626',
  },
  ratingPillText: { color: '#ffd740', fontSize: 13, fontWeight: '700' },
  heroWatchlistBtnLight: {
    backgroundColor: 'rgba(255,255,255,0.5)',
    borderColor: 'rgba(255,255,255,0.6)',
  },
   heroContentCentered: { position: 'absolute', bottom: 0, left: 0, right: 0, padding: 28, paddingBottom: 12, alignItems: 'center' as const, zIndex: 2 },
  heroBadgeCentered: { alignSelf: 'center' as const, borderRadius: 20, paddingHorizontal: 12, paddingVertical: 5, marginBottom: 12 },
  heroTitleCentered: {
    color: c.textPrimary, fontSize: 30, fontWeight: '900', marginBottom: 8, lineHeight: 36,
    textShadowColor: 'rgba(0,0,0,0.7)', textShadowRadius: 6, textShadowOffset: { width: 0, height: 1 },
    textAlign: 'center' as const,
  },
  heroTitleLogoCentered: {
    width: SCREEN_WIDTH * 0.65, height: 90, marginBottom: 8,
    shadowColor: 'rgba(0,0,0,0.95)',
    shadowOpacity: 0.42,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 1 },
    elevation: 3,
  },
  heroDescCentered: { color: c.textSecondary, fontSize: 13, lineHeight: 20, marginBottom: 0, textAlign: 'center' as const },
  heroDescCenteredCompact: { marginBottom: 0 },
  heroSynopsisTextLight: {
    color: '#ffffff',
    textAlign: 'center' as const,
    textShadowColor: 'rgba(0,0,0,0.95)',
    textShadowRadius: 5,
    textShadowOffset: { width: 0, height: 0 },
  },
  heroActionsCentered: { flexDirection: 'row' as const, alignItems: 'center', gap: 12, justifyContent: 'center' as const },
  heroContentGlass: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: 20,
    paddingBottom: 14,
    zIndex: 2,
  },
  heroGlassCard: {
    alignSelf: 'center' as const,
    width: Math.min(SCREEN_WIDTH - 40, 392),
    minHeight: Math.min(HERO_HEIGHT - 64, 552),
    borderRadius: 30,
    backgroundColor: 'rgba(8,10,14,0.32)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
    overflow: 'hidden' as const,
  },
  heroGlassCardLight: {
    backgroundColor: 'rgba(255,255,255,0.72)',
    borderColor: 'rgba(255,255,255,0.34)',
  },
  heroGlassPosterWrap: {
    ...StyleSheet.absoluteFillObject,
  },
  heroGlassPoster: {
    ...StyleSheet.absoluteFillObject,
  },
  heroGlassPosterFallback: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  heroGlassImageScrim: {
    ...StyleSheet.absoluteFillObject,
    bottom: -2,
  } as any,
  heroGlassPosterFallbackText: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: 0.4,
    textTransform: 'uppercase' as const,
  },
  heroGlassInfo: {
    justifyContent: 'flex-end',
    flex: 1,
    minHeight: 0,
    gap: 10,
    padding: 16,
    zIndex: 1,
  },
  heroGlassTop: {
    gap: 8,
    marginTop: 'auto',
    alignItems: 'center' as const,
  },
  heroGlassBadge: {
    alignSelf: 'center' as const,
    marginBottom: 0,
  },
  heroGlassGenreText: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.3,
    textAlign: 'center' as const,
    textShadowColor: 'rgba(0,0,0,0.55)',
    textShadowRadius: 6,
    textShadowOffset: { width: 0, height: 1 },
  },
  heroGlassGenreTextLight: {
    color: '#ffffff',
    textShadowColor: 'rgba(0,0,0,0.55)',
  },
  heroGlassTitleLogo: {
    width: '100%',
    maxWidth: '100%',
    marginBottom: 0,
  },
  heroGlassTitleFallback: {
    color: '#ffffff',
    fontSize: 28,
    fontWeight: '900',
    lineHeight: 32,
    textShadowColor: 'rgba(0,0,0,0.55)',
    textShadowRadius: 6,
    textShadowOffset: { width: 0, height: 1 },
    textAlign: 'center' as const,
  },
  heroGlassTitleFallbackLight: {
    color: '#111827',
    textShadowColor: 'rgba(255,255,255,0.0)',
    textShadowRadius: 0,
    textShadowOffset: { width: 0, height: 0 },
  },
  heroGlassSynopsis: {
    color: 'rgba(255,255,255,0.88)',
    fontSize: 13,
    lineHeight: 19,
    textAlign: 'center' as const,
  },
  heroGlassSynopsisLight: {
    color: '#334155',
  },
  heroGlassActionRow: {
    flexDirection: 'row' as const,
    alignItems: 'center',
    gap: 8,
    flexWrap: 'wrap' as const,
    marginTop: 2,
    justifyContent: 'center' as const,
  },
  dots: { 
    flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 2,
    marginTop: 6, marginBottom: 2,
  },
  dotsGlass: {
    marginTop: 24,
    marginBottom: 0,
  },
});

const getHeroBackgroundUri = (item: any) => item?.backdrop || item?.poster || null;
const getHeroForegroundUri = (item: any) => item?.titleLogo || item?.logo || item?.foreground || null;
const getHeroItemKey = (item: any) => `${item?.type ?? 'movie'}_${String(item?.id ?? item?.tmdbId ?? item?.title ?? '')}`;

const getMarqueeImageUri = (item: any) => {
  const candidates = [
    ...(Array.isArray(item?.backdrops) ? item.backdrops : []),
    item?.backdrop,
    item?.poster,
  ].filter((uri): uri is string => typeof uri === 'string' && uri.length > 0);

  if (candidates.length === 0) return null;
  return candidates[0];
};

const mergeMediaEntries = (primary: any, secondary: any) => ({
  ...secondary,
  ...primary,
  poster: primary?.poster ?? secondary?.poster ?? null,
  backdrop: primary?.backdrop ?? secondary?.backdrop ?? null,
  backdrops: Array.isArray(primary?.backdrops) && primary.backdrops.length > 0
    ? primary.backdrops
    : (Array.isArray(secondary?.backdrops) ? secondary.backdrops : []),
  titleLogo: primary?.titleLogo ?? secondary?.titleLogo ?? null,
  marqueeImageUri: getMarqueeImageUri(primary) ?? getMarqueeImageUri(secondary) ?? null,
});

const AnimatedDot = React.memo(function AnimatedDot({ active, activeColor, inactiveColor, onPress }: { active: boolean; activeColor: string; inactiveColor: string; onPress: () => void }) {
  const anim = React.useRef(new Animated.Value(active ? 1 : 0)).current;
  
  React.useEffect(() => {
    Animated.spring(anim, {
      toValue: active ? 1 : 0,
      useNativeDriver: false,
      damping: 15,
      stiffness: 120,
    }).start();
  }, [active]);

  const width = anim.interpolate({ inputRange: [0, 1], outputRange: [8, 24] });
  const opacity = anim.interpolate({ inputRange: [0, 1], outputRange: [0.3, 1] });
  
  return (
    <TouchableOpacity onPress={onPress} activeOpacity={0.8} style={{ padding: 3 }}>
      <Animated.View style={{ 
        height: 6, borderRadius: 3, 
        width,
        backgroundColor: active ? activeColor : inactiveColor,
        opacity,
      }} />
    </TouchableOpacity>
  );
});

export const HomeScreen = ({ navigation }: any) => {
  const blurTargetRef = useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const { theme, appearance, resolvedAppearance, showHeroSynopsis } = useTheme();
  const { colors } = theme;
  const { t } = useLanguage();
  const { uiStyle } = useUIStyle();
  const { continueWatchingStyle, vividAmbientEnabled } = useDisplaySettings();
  const { setAppReady } = useAppReady();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const isDarkAppearance = resolvedAppearance === 'dark';
  const isMonochromeDark = isDarkAppearance;
  const heroSectionHeight = uiStyle === 'glass' ? HERO_HEIGHT + 36 : HERO_HEIGHT;
  const { isMovieWatched, isSeriesWatched, toggleMovieWatched, markAllEpisodesWatched, unmarkSeriesWatched } = useWatched();

  const defaultSections = useMemo(() => [
    { id: 'networks',      title: t('section_networks'),        endpoint: '/tmdb/networks',       enabled: true },
    { id: 'trending_movie', title: t('section_trending_movies'), endpoint: '/tmdb/trending/movie',  enabled: true },
    { id: 'trending_tv',   title: t('section_trending_tv'),     endpoint: '/tmdb/trending/tv',     enabled: true },
    DOCUMENTARY_SECTION,
    { id: 'popular_movie',  title: t('section_popular_movies'),  endpoint: '/tmdb/popular/movie',   enabled: false },
    { id: 'popular_tv',    title: t('section_popular_tv'),      endpoint: '/tmdb/popular/tv',      enabled: false },
  ], [t]);

  const {
    isConnected: traktConnected,
    continueWatching,
    watchlist: traktWatchlist,
    trending: traktTrending,
    recommendations: traktRecommendations,
    refreshContinueWatching,
    refreshWatchlist,
    refreshTrending,
    refreshRecommendations
  } = useTrakt();

  const [sections, setSections] = useState(defaultSections);
  const [sectionData, setSectionData] = useState<Record<string, any[]>>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [watchlist, setWatchlist] = useState<any[]>([]);
  const [watchlistRemovalIds, setWatchlistRemovalIds] = useState<string[]>([]);
  const [localContinueWatching, setLocalContinueWatching] = useState<any[]>([]);
  const storageOwnerId = getProfileStorageOwnerId(user?.uid, activeProfile?.id);

  // Clear profile-scoped data the moment the active profile changes so the
  // previous profile's cards never flash on screen while the async reload runs.
  const activeProfileId = activeProfile?.id;
  useEffect(() => {
    setLocalContinueWatching([]);
  }, [activeProfileId]);
  const legacyOwnerId = user?.uid ?? null;
  const sectionSettingsKey = profileScopedStorageKey('home_sections', user?.uid, activeProfile?.id);

  const [longPressItem,          setLongPressItem]          = useState<any | null>(null);
  const [seriesWatchConfirmItem, setSeriesWatchConfirmItem] = useState<any | null>(null);
  const [heroPlayChoiceItem, setHeroPlayChoiceItem] = useState<any | null>(null);

  const [pendingWatchlistRemovals, setPendingWatchlistRemovals] = useState<Set<string>>(new Set());

  const [heroItems,     setHeroItems]     = useState<any[]>([]);
  const [heroIndex,     setHeroIndex]     = useState(0);
  const [heroPrevIndex, setHeroPrevIndex] = useState<number | null>(null);
  const [heroLogos,     setHeroLogos]     = useState<Record<string, string | null>>({});
  const [heroLogoStates, setHeroLogoStates] = useState<Record<string, 'loading' | 'loaded' | 'error'>>({});
  const [heroLogoHeights, setHeroLogoHeights] = useState<Record<string, number>>({});
  const [heroGenres,    setHeroGenres]    = useState<Record<string, string | null>>({});
  const [heroBackdrops,    setHeroBackdrops]    = useState<Record<string, string | null>>({});
  const [heroTrailerKeys,  setHeroTrailerKeys]  = useState<Record<string, string | null>>({});
  const [heroTrailerVisible, setHeroTrailerVisible] = useState<string | null>(null);
  const heroFadeIn    = useRef(new Animated.Value(1)).current;
  const heroContentAnim = useRef(new Animated.Value(1)).current; // For text content
  const heroScale     = useRef(new Animated.Value(1)).current; // Ken Burns effect
  const heroIndexRef  = useRef(0);
  const heroItemKeyRef = useRef<string | null>(null);
  const heroTimerRef  = useRef<ReturnType<typeof setInterval> | null>(null);
  const heroLengthRef = useRef(0);
  const heroTransitionDirectionRef = useRef<1 | -1>(1);
  const homeScrollViewRef = useRef<ScrollView>(null);
  useScrollToTop(homeScrollViewRef);

  // Merge Trakt watchlist with local watchlist (dedup by id, minus pending removals)
  const combinedWatchlist = useMemo(() => {
    const removedIds = new Set(watchlistRemovalIds);
    const traktMapped = traktWatchlist
      .map(t => ({ ...t, id: t.tmdbId != null ? String(t.tmdbId) : t.id }))
      .filter(t => !pendingWatchlistRemovals.has(String(t.id)) && !removedIds.has(String(t.id)));
    const localOnly = watchlist.filter(i => !pendingWatchlistRemovals.has(String(i.id)) && !removedIds.has(String(i.id)));
    return uniqueItemsById(mergeWatchlistItems(traktMapped, localOnly));
  }, [traktWatchlist, watchlist, pendingWatchlistRemovals, watchlistRemovalIds]);
  const allContinueWatching = useMemo(() => {
    if (!user) return [];
    const byId = new Map<string, any>();
    for (const item of [...continueWatching, ...localContinueWatching]) {
      const key = String(item?.tmdbId ?? item?.id ?? '');
      if (!key) continue;
      const current = byId.get(key);
      byId.set(key, current ? mergeMediaEntries(item, current) : mergeMediaEntries(item, {}));
    }
    return Array.from(byId.values());
  }, [user, continueWatching, localContinueWatching]);
  const scrollY = useRef(new Animated.Value(0)).current;
  const headerOpacity = scrollY.interpolate({ inputRange: [0, 80], outputRange: [1, 0.92], extrapolate: 'clamp' });
  const heroScrollScale = scrollY.interpolate({
    inputRange: [-100, 0, HERO_HEIGHT * 0.5],
    outputRange: [1.10, 1.0, 1.22],
    extrapolate: 'clamp',
  });
  const heroScrollTranslateY = scrollY.interpolate({
    inputRange: [-100, 0, HERO_HEIGHT],
    outputRange: [-8, 0, -90],
    extrapolate: 'clamp',
  });
  const loadSectionConfig = useCallback(async () => {
    try {
      const saved = await Storage.getItem(sectionSettingsKey) ?? await Storage.getItem('home_sections');
      if (saved) {
        const savedSections: any[] = JSON.parse(saved);
        const savedMap = new Map(savedSections.map(s => [s.id, s]));
        // Preserve order + enabled state for known sections; append any new defaults
        const known = savedSections
          .filter(s => defaultSections.find(d => d.id === s.id))
          .map(s => ({ ...defaultSections.find(d => d.id === s.id)!, enabled: s.enabled }));
        const newOnes = defaultSections.filter(d => !savedMap.has(d.id));
        const resolvedSections = [...known, ...newOnes];
        setSections(resolvedSections);
        return resolvedSections;
      } else {
        setSections(defaultSections);
        return defaultSections;
      }
    } catch (e) {
      console.error("Failed to load section config:", e);
      setSections(defaultSections);
      return defaultSections;
    }
  }, [defaultSections, sectionSettingsKey]);

  const loadWatchlist = useCallback(async () => {
    if (!user) { setWatchlist([]); return; }
    try {
      setWatchlist(await readWatchlistItems(storageOwnerId, legacyOwnerId));
    } catch {}
  }, [user, storageOwnerId, legacyOwnerId]);

  const loadWatchlistRemovals = useCallback(async () => {
    if (!user) { setWatchlistRemovalIds([]); return; }
    try {
      setWatchlistRemovalIds(await readWatchlistRemovalIds(storageOwnerId, legacyOwnerId));
    } catch {}
  }, [legacyOwnerId, storageOwnerId, user]);

  const loadLocalProgress = useCallback(async () => {
    if (!user) { setLocalContinueWatching([]); return; }
    try {
      const indexKey = progressIndexStorageKey(storageOwnerId);
      const raw = await Storage.getItem(indexKey);
      if (!raw) { setLocalContinueWatching([]); return; }
      const index: any[] = JSON.parse(raw);
      const items = index
        .filter(e => e.progressPct >= 0 && e.progressPct < 95)
          .map(e => ({
          id:       String(e.tmdbId),
          tmdbId:   e.tmdbId,
          title:    e.title,
          poster:   e.poster || undefined,
          backdrop: e.backdrop || undefined,
          type:     e.type,
          year:     e.year,
          progress: e.progressPct,
          positionSec: Number(e.positionSec ?? 0) || undefined,
          durationSec: Number(e.durationSec ?? 0) || undefined,
        }));
      setLocalContinueWatching(items);
    } catch {
      setLocalContinueWatching([]);
    }
  }, [user, storageOwnerId]);

  const fetchTmdbSections = useCallback(async (activeSections: typeof sections) => {
    const results: Record<string, any[]> = {};
    let fetchedAny = false;
    await Promise.all(
      activeSections.filter(s => s.enabled).map(async (s) => {
        try {
          const res = await tmdbFetch(s.endpoint);
          if (!res.ok) return;
          const data = await res.json();
          fetchedAny = true;
          results[s.id] = data.results || [];
        } catch {}
      })
    );
    if (Object.keys(results).length > 0) {
      setSectionData(prev => ({ ...prev, ...results }));
    }
    return fetchedAny;
  }, []);

  const fetchTraktSections = useCallback(async () => {
    if (!traktConnected || !user) return;
    refreshTrending();
    refreshRecommendations();
  }, [traktConnected, user, refreshTrending, refreshRecommendations]);

  useEffect(() => {
    let active = true;
    const init = async () => {
      const resolvedSections = await loadSectionConfig();
      const tmdbLoaded = active ? await fetchTmdbSections(resolvedSections) : false;
      if (active) {
        await Promise.all([loadWatchlist(), loadWatchlistRemovals()]);
        await loadLocalProgress();
        setLoading(!tmdbLoaded);
        setAppReady(true);
      }
    };
    init();
    return () => { active = false; };
  }, [fetchTmdbSections, loadSectionConfig, loadWatchlistRemovals, setAppReady, user?.uid, activeProfile?.id]);

  useFocusEffect(
    useCallback(() => {
      const timer = setTimeout(() => {
        void loadSectionConfig().then(resolvedSections => {
          void fetchTmdbSections(resolvedSections);
        });
        loadWatchlist();
        loadLocalProgress();
        if (traktConnected && user) {
          fetchTraktSections();
          refreshContinueWatching();
          refreshWatchlist();
        }
      }, 100);
      const timer2 = setTimeout(() => {
        loadLocalProgress();
      }, 600);
      return () => {
        clearTimeout(timer);
        clearTimeout(timer2);
      };
  }, [user, activeProfile?.id, traktConnected, fetchTraktSections, refreshContinueWatching, refreshWatchlist, loadWatchlistRemovals, loadWatchlist, loadLocalProgress, fetchTmdbSections])
  );

  useEffect(() => {
    if (sections.length === 0) return;
    void fetchTmdbSections(sections);
  }, [sections, fetchTmdbSections]);

  // Sync user-specific content whenever auth state changes
  const prevUser = useRef<typeof user>(undefined);
  useEffect(() => {
    const wasLoggedIn = prevUser.current != null;
    const isLoggedIn  = user != null;

    if (prevUser.current === undefined) {
      prevUser.current = user;
      return;
    }

    prevUser.current = user;

    if (!isLoggedIn) {
      setLocalContinueWatching([]);
      setWatchlist([]);
    } else if (!wasLoggedIn || user?.uid !== prevUser.current?.uid) {
      loadWatchlist();
      loadLocalProgress();
      fetchTraktSections();
      if (traktConnected) {
        refreshContinueWatching();
        refreshWatchlist();
      }
    }
  }, [user]);

  // Auto-refresh when Trakt connects
  const prevTraktConnected = useRef(traktConnected);
  useEffect(() => {
    if (traktConnected && !prevTraktConnected.current) {
      fetchTraktSections();
      refreshContinueWatching();
      refreshWatchlist();
    }
    prevTraktConnected.current = traktConnected;
  }, [traktConnected, fetchTraktSections, refreshContinueWatching, refreshWatchlist]);

  useEffect(() => {
      const trendingMovies = (sectionData['trending_movie'] || []).slice(0, 6).map(i => ({ ...i, type: 'movie' }));
      const trendingTv = (sectionData['trending_tv'] || []).slice(0, 6).map(i => ({ ...i, type: 'tv' }));
      const watchlistItems = combinedWatchlist
        .map(i => ({ ...i, type: i.type === 'tv' ? 'tv' : 'movie' }))
        .slice(0, 6);
      const continueItems = allContinueWatching
        .map(i => ({ ...i, type: i.type === 'tv' ? 'tv' : 'movie' }))
        .slice(0, 6);
    const isWatchedHeroItem = (item: any) => (
      item?.type === 'tv'
        ? isSeriesWatched(Number(item.id))
        : isMovieWatched(Number(item.id))
    );

    const buckets = [
      trendingMovies,
      trendingTv,
      watchlistItems,
      continueItems,
    ];

    const seenIds = new Set<string>();
    const combined: any[] = [];

    const addItem = (item: any) => {
      if (!item) return;
      const key = getHeroItemKey(item);
      if (seenIds.has(key)) return;
      if (isWatchedHeroItem(item)) return;
      if (!getHeroBackgroundUri(item) && !getHeroForegroundUri(item)) return;
      seenIds.add(key);
      combined.push(item);
    };

    for (let round = 0; round < 3 && combined.length < 12; round++) {
      for (const bucket of buckets) {
        addItem(bucket[round]);
        if (combined.length >= 12) break;
      }
    }

    if (combined.length < 12) {
      for (const bucket of buckets) {
        for (let i = 3; i < bucket.length && combined.length < 12; i++) {
          addItem(bucket[i]);
        }
        if (combined.length >= 12) break;
      }
    }

    const nextHeroItems = combined.slice(0, 12);
    const currentKey = heroItems[heroIndexRef.current] ? getHeroItemKey(heroItems[heroIndexRef.current]) : null;
    const nextIndex = currentKey
      ? nextHeroItems.findIndex(item => getHeroItemKey(item) === currentKey)
      : -1;
    const resolvedIndex = nextIndex >= 0 ? nextIndex : 0;

    setHeroItems(nextHeroItems);
    heroLengthRef.current = Math.min(combined.length, 12);
    heroIndexRef.current = resolvedIndex;
    heroItemKeyRef.current = nextHeroItems[resolvedIndex] ? getHeroItemKey(nextHeroItems[resolvedIndex]) : null;
    setHeroIndex(resolvedIndex);
    setHeroPrevIndex(null);
    heroFadeIn.setValue(1);
    heroScale.setValue(1.015);
    Animated.timing(heroScale, {
      toValue: 1.06,
      duration: 14000,
      easing: Easing.linear,
      useNativeDriver: true,
    }).start();
  }, [sectionData, combinedWatchlist, allContinueWatching, isMovieWatched, isSeriesWatched, heroFadeIn, heroScale]);

  useEffect(() => {
    if (heroItems.length === 0) return;
    void Image.prefetch(
      [
        ...heroItems
          .map(getHeroBackgroundUri)
          .filter((uri): uri is string => typeof uri === 'string' && uri.length > 0),
        ...heroItems
          .map(item => heroLogos[`${item.type}_${item.id}`])
          .filter((uri): uri is string => typeof uri === 'string' && uri.length > 0),
      ],
      'memory-disk',
    );
  }, [heroItems, heroLogos]);

  useEffect(() => {
    const sectionImages = Object.values(sectionData)
      .flat()
      .slice(0, 36)
      .flatMap((item: any) => [item?.poster, item?.backdrop])
      .filter((uri): uri is string => typeof uri === 'string' && uri.length > 0);

    if (sectionImages.length > 0) {
      void Image.prefetch(Array.from(new Set(sectionImages)), 'memory-disk');
    }
  }, [sectionData]);

  useEffect(() => {
    if (heroItems.length === 0) return;
    let cancelled = false;

    setHeroLogoStates(prev => {
      const next = { ...prev };
      for (const item of heroItems) {
        const key = `${item.type}_${item.id}`;
        if (next[key] !== 'loaded') {
          next[key] = 'loading';
        }
      }
      return next;
    });

    const loadHeroMetadata = async () => {
      const results = await Promise.all(heroItems.map(async (item) => {
        const key = `${item.type}_${item.id}`;
        try {
          const res = await tmdbFetch(`/tmdb/details/${item.type}/${item.id}`);
          if (!res.ok) {
            return { key, logo: null, genre: null, backdrop: null, trailerKey: null };
          }
          const data = await res.json();
          return {
            key,
            logo: (data?.titleLogo as string | null | undefined) ?? null,
            genre: Array.isArray(data?.genreNames) && data.genreNames.length > 0
              ? String(data.genreNames[0])
              : null,
            backdrop: data?.backdrop ?? (Array.isArray(data?.backdrops) && data.backdrops.length > 0 ? data.backdrops[0] : null),
            trailerKey: (data?.trailerKey as string | null | undefined) ?? null,
          };
        } catch {
          return { key, logo: null, genre: null, backdrop: null, trailerKey: null };
        }
      }));

      if (cancelled) return;

      setHeroLogos(prev => {
        const next = { ...prev };
        for (const result of results) {
          next[result.key] = result.logo;
        }
        return next;
      });
      setHeroLogoStates(prev => {
        const next = { ...prev };
        for (const result of results) {
          next[result.key] = result.logo ? 'loading' : 'error';
        }
        return next;
      });
      setHeroGenres(prev => {
        const next = { ...prev };
        for (const result of results) {
          next[result.key] = result.genre;
        }
        return next;
      });
      setHeroBackdrops(prev => {
        const next = { ...prev };
        for (const result of results) {
          next[result.key] = result.backdrop;
        }
        return next;
      });
      setHeroTrailerKeys(prev => {
        const next = { ...prev };
        for (const result of results) {
          next[result.key] = result.trailerKey;
        }
        return next;
      });
    };

    void loadHeroMetadata();
    return () => {
      cancelled = true;
    };
  }, [heroItems]);

  const slideTo = useCallback((index: number) => {
    if (heroLengthRef.current <= 0) {
      heroIndexRef.current = 0;
      setHeroIndex(0);
      setHeroPrevIndex(null);
      return;
    }
    const target = Math.max(0, Math.min(heroLengthRef.current - 1, index));
    if (target === heroIndexRef.current) return;

    heroTransitionDirectionRef.current = target > heroIndexRef.current ? 1 : -1;
    setHeroPrevIndex(heroIndexRef.current);
    heroFadeIn.setValue(0);
    heroContentAnim.setValue(0);
    heroScale.setValue(1.015);

    heroIndexRef.current = target;
    heroItemKeyRef.current = heroItems[target] ? getHeroItemKey(heroItems[target]) : null;
    setHeroIndex(target);

    Animated.parallel([
      Animated.timing(heroFadeIn, {
        toValue: 1,
        duration: 650,
        easing: Easing.inOut(Easing.quad),
        useNativeDriver: true,
      }),
      Animated.timing(heroContentAnim, {
        toValue: 1,
        duration: 520,
        easing: Easing.out(Easing.quad),
        useNativeDriver: true,
      }),
      Animated.timing(heroScale, {
        toValue: 1.06,
        duration: 14000,
        easing: Easing.linear,
        useNativeDriver: true,
      }),
    ]).start(() => {
      setHeroPrevIndex(null);
    });
  }, [heroContentAnim, heroFadeIn, heroItems, heroScale]);

  const startHeroTimer = useCallback(() => {
    if (heroTimerRef.current) clearInterval(heroTimerRef.current);
    heroTimerRef.current = setInterval(() => {
      if (heroLengthRef.current <= 1) return;
      const next = (heroIndexRef.current + 1) % heroLengthRef.current;
      slideTo(next);
    }, 15_000);
  }, [slideTo]);

  const heroPanResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, { dx, dy }) =>
        Math.abs(dx) > Math.abs(dy) * 1.5 && Math.abs(dx) > 12,
      onPanResponderRelease: (_, { dx, vx }) => {
        if (heroTimerRef.current) clearInterval(heroTimerRef.current);
        if ((dx < -SCREEN_WIDTH * 0.2 || vx < -0.5) && heroIndexRef.current < heroLengthRef.current - 1) {
          slideTo(heroIndexRef.current + 1);
        } else if ((dx > SCREEN_WIDTH * 0.2 || vx > 0.5) && heroIndexRef.current > 0) {
          slideTo(heroIndexRef.current - 1);
        }
        startHeroTimer();
      },
    }),
  ).current;

  useEffect(() => {
    if (heroItems.length < 2) return;
    startHeroTimer();
    return () => { if (heroTimerRef.current) clearInterval(heroTimerRef.current); };
  }, [heroItems, startHeroTimer]);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    const [tmdbLoaded] = await Promise.all([
      fetchTmdbSections(sections),
      fetchTraktSections(),
      loadWatchlist(),
      loadWatchlistRemovals(),
      loadLocalProgress(),
      ...(traktConnected ? [refreshContinueWatching(), refreshWatchlist()] : []),
    ]);
    if (tmdbLoaded) setLoading(false);
    setRefreshing(false);
  }, [fetchTmdbSections, fetchTraktSections, loadWatchlist, loadWatchlistRemovals, loadLocalProgress, traktConnected, refreshContinueWatching, refreshWatchlist, sections]);

  const handleItemPress = useCallback((item: any) => {
    navigation.navigate('Detail', { movieId: item.id, type: item.type || 'movie' });
  }, [navigation]);

  const handleHeroRewatchPress = useCallback((item: any) => {
    const progressKey = movieProgressKey(Number(item.id));
    if (item.type === 'tv') {
      navigation.navigate('Detail', { movieId: item.id, type: 'tv', startFromBeginning: true });
      return;
    }

    navigation.navigate('Player', {
      movieId: item.id,
      imdbId: item.imdbId ?? undefined,
      type: item.type || 'movie',
      title: item.title,
      year: item.year,
      synopsis: item.description ?? undefined,
      titleLogo: item.titleLogo,
      backdrop: item.backdrop ?? item.poster ?? undefined,
      poster: item.poster,
      resumeFrom: 0,
      forceStartFromBeginning: true,
      progressKey,
      runtimeSec: item.runtime ? item.runtime * 60 : undefined,
      resolveOnMount: true,
      resolverMovieId: item.id,
      resolverImdbId: item.imdbId ?? undefined,
      resolverType: item.type || 'movie',
      returnToPlayerParams: {
        movieId: item.id,
        imdbId: item.imdbId ?? undefined,
        type: item.type || 'movie',
        title: item.title,
        year: item.year,
        synopsis: item.description ?? undefined,
        titleLogo: item.titleLogo,
        backdrop: item.backdrop ?? item.poster ?? undefined,
        poster: item.poster,
        progressKey,
      },
    });
  }, [navigation]);

  const handleHeroPlayPress = useCallback((item: any) => {
    const watched = item.type !== 'tv'
      ? isMovieWatched(Number(item.id))
      : isSeriesWatched(Number(item.id));
    if (watched) {
      setHeroPlayChoiceItem(item);
      return;
    }
    handleItemPress(item);
  }, [handleItemPress, isMovieWatched, isSeriesWatched]);

  const handleViewAll = useCallback((sectionId: string, title: string) => {
    if (sectionId === 'documentaries') {
      navigation.navigate('Browse', { title, endpoint: DOCUMENTARY_SECTION.endpoint });
      return;
    }
    const type = sectionId.includes('tv') ? 'tv' : 'movie';
    navigation.navigate('Browse', { type, title });
  }, [navigation]);

  const handleNetworkPress = useCallback((item: any) => {
    navigation.navigate('Browse', { title: item.name, endpoint: `/tmdb/network/${item.id}` });
  }, [navigation]);

  const toggleWatchlist = useCallback(async (item: any) => {
    if (!user) { navigation.navigate('Auth'); return; }
    const itemId = String(item.id);
    const current = await readWatchlistItems(storageOwnerId, legacyOwnerId);
    const exists = current.some((i: any) => watchlistItemMatchesId(i, itemId));
    const updated = exists
      ? current.filter((i: any) => !watchlistItemMatchesId(i, itemId))
      : [...current, normalizeWatchlistItem({
          id: itemId,
          tmdbId: Number(item.id),
          title: item.title,
          poster: item.poster,
          backdrop: item.backdrop,
          type: item.type,
          year: item.year,
          rating: item.rating,
        })];
    await writeWatchlistItems(storageOwnerId, updated);
    setWatchlist(updated);
    if (exists) {
      const next = Array.from(new Set([...watchlistRemovalIds, itemId]));
      setWatchlistRemovalIds(next);
      await writeWatchlistRemovalIds(storageOwnerId, next);
      setPendingWatchlistRemovals(prev => new Set([...prev, itemId]));
    } else {
      const next = watchlistRemovalIds.filter(id => id !== itemId);
      setWatchlistRemovalIds(next);
      await writeWatchlistRemovalIds(storageOwnerId, next);
    }
    if (traktConnected && user) {
      try {
        const endpoint = exists ? '/trakt/sync/watchlist/remove' : '/trakt/sync/watchlist/add';
        const entry = { title: item.title, year: parseInt(String(item.year)) || undefined, ids: { tmdb: Number(item.id) } };
        const payload = item.type !== 'tv' ? { movies: [entry], shows: [] } : { movies: [], shows: [entry] };
        await fetch(`${API_BASE}${endpoint}`, {
          method: 'POST',
          headers: await buildAuthHeaders(user, { profileId: activeProfile?.id }),
          body: JSON.stringify(payload),
        });
        await refreshWatchlist();
      } catch {}
    }
    if (exists) setPendingWatchlistRemovals(prev => { const n = new Set(prev); n.delete(itemId); return n; });
  }, [legacyOwnerId, navigation, refreshWatchlist, storageOwnerId, traktConnected, user, watchlistRemovalIds]);

  const handleShare = useCallback((item: any) => {
    const year = item.year ? ` (${item.year})` : '';
    Share.share({ message: `Check out ${item.title}${year} on StreamDek!` });
  }, []);

  const handleSeriesMarkWatched = useCallback(async (item: any) => {
    try {
      const res = await tmdbFetch(`/tmdb/details/tv/${item.id}`);
      const data = await res.json();
      const seasons = (data.seasons ?? []).filter((s: any) => s.season_number > 0);
      await markAllEpisodesWatched(Number(item.id), data.imdbId, item.title, seasons);
    } catch {}
  }, [markAllEpisodesWatched]);

  const handleLongPress = useCallback((item: any) => {
    setLongPressItem(item);
  }, []);

  const buildLongPressActions = useCallback((item: any) => {
    if (!item) return [];
    const isMovie  = item.type !== 'tv';
    const watched  = isMovie ? isMovieWatched(Number(item.id)) : isSeriesWatched(Number(item.id));
    const inWl     = combinedWatchlist.some(i => watchlistItemMatchesId(i, item.id));

    return [
      watched
        ? {
            label:   isMovie ? t('watched_unwatch') : t('card_unwatch_series'),
            icon:    'eye-off-outline' as const,
            variant: 'default' as const,
            onPress: () => {
              if (isMovie) toggleMovieWatched(Number(item.id), item.imdbId, item.title, item.year);
              else         unmarkSeriesWatched(Number(item.id));
            },
          }
        : {
            label:   t('watched_mark'),
            icon:    'checkmark-circle-outline' as const,
            variant: 'accent' as const,
            onPress: () => {
              if (isMovie) toggleMovieWatched(Number(item.id), item.imdbId, item.title, item.year);
              else         setSeriesWatchConfirmItem(item);
            },
          },
      { label: t('card_share'), icon: 'share-outline' as const, variant: 'default' as const, onPress: () => handleShare(item) },
      inWl
        ? { label: t('card_watchlist_remove'), icon: 'bookmark-outline' as const, variant: 'destructive' as const, onPress: () => toggleWatchlist(item) }
        : { label: t('card_watchlist_add'), icon: 'bookmark-outline' as const, variant: 'default' as const, onPress: () => toggleWatchlist(item) },
      { label: 'Cancel', icon: 'close-outline' as const, variant: 'cancel' as const, onPress: () => {} },
    ];
  }, [combinedWatchlist, traktConnected, user, t, isMovieWatched, isSeriesWatched, toggleMovieWatched, unmarkSeriesWatched, handleShare, toggleWatchlist]);

  const renderHeroContent = (item: any, animationValue: Animated.Value) => {
    const hasProgress = typeof item.progress === 'number' && item.progress > 0;
    const continueTime = getContinueSeconds(item);
    const continueLabel = hasProgress
      ? (continueTime != null ? `${t('home_continue_btn')} from ${formatContinueTime(continueTime)}` : t('home_continue_btn'))
      : t('home_play_now_btn');
    const classicContinueLabel = hasProgress ? t('home_continue_btn') : t('home_play_now_btn');
    const inWatchlist = combinedWatchlist.some(i => watchlistItemMatchesId(i, item.id));
    const heroKey = `${item.type}_${item.id}`;
    const titleLogo   = heroLogos[heroKey] ?? getHeroForegroundUri(item);
    const titleLogoState = heroLogoStates[heroKey] ?? 'loading';
    const titleLogoHeight = heroLogoHeights[`${item.type}_${item.id}`] ?? 64;
    const badgeBottomGap = Math.max(8, Math.round(titleLogoHeight * 0.12));
    const titleBottomGap = Math.max(3, Math.round(titleLogoHeight * 0.06));
    const synopsisTopGap = Math.max(3, Math.round(titleLogoHeight * 0.04));
    const heroGenre   = heroGenres[`${item.type}_${item.id}`] ?? null;
    const trailerKey  = heroTrailerKeys[`${item.type}_${item.id}`] ?? null;
    const ratingTextColor = (item.rating ?? 0) >= 7 ? '#00e676' : (item.rating ?? 0) >= 5 ? '#ffd740' : '#c97070';
    const watched     = item.type !== 'tv' ? isMovieWatched(Number(item.id)) : isSeriesWatched(Number(item.id));
    const watchedIconColor = watched
      ? (theme.id === 'monochrome' ? '#1b5e20' : '#00e676')
      : '#111111';
    const primaryActionPalette = getPrimaryActionPalette(colors, theme.id, !isDarkAppearance);
    const translateY = animationValue.interpolate({ inputRange: [0, 1], outputRange: [20, 0] });
    const translateX = animationValue.interpolate({
      inputRange: [0, 1],
      outputRange: [18 * heroTransitionDirectionRef.current, 0],
    });
    const centered = uiStyle === 'centered';
    const glass = uiStyle === 'glass';
    const heroCloudColors: readonly [string, string, string, string] = isDarkAppearance
      ? ['rgba(0,0,0,0.00)', 'rgba(0,0,0,0.08)', 'rgba(0,0,0,0.06)', 'rgba(0,0,0,0.00)']
      : ['rgba(0,0,0,0.02)', 'rgba(0,0,0,0.44)', 'rgba(0,0,0,0.28)', 'rgba(0,0,0,0.04)'];
    const centeredBackgroundUri = heroBackdrops[heroKey] ?? getHeroBackgroundUri(item);
    const heroCloudStyle = [
      styles.heroCloudMask,
      !isDarkAppearance && styles.heroCloudMaskLightBleed,
    ];
    const showFallbackText = titleLogoState === 'error';
    const glassPosterUri = item.poster || centeredBackgroundUri || getHeroBackgroundUri(item);
    const badgeRow = (
      <View
        style={[
          glass ? styles.heroGlassBadge : centered ? styles.heroBadgeCentered : styles.heroBadge,
          item.type === 'tv' ? styles.heroBadgeTv : styles.heroBadgeMovie,
          !isDarkAppearance && styles.heroBadgeLight,
          centered && { marginBottom: badgeBottomGap },
        ]}
      >
        <Text style={styles.heroBadgeText}>
          {item.type === 'movie' ? t('home_movie_badge') : t('home_tv_badge')}
          {heroGenre ? <Text style={styles.heroBadgeText}>{'\u2009\u2022\u2009'}{heroGenre}</Text> : null}
        </Text>
      </View>
    );
    const glassGenreLabel = heroGenre ? (
      <Text style={[styles.heroGlassGenreText, !isDarkAppearance && styles.heroGlassGenreTextLight]} numberOfLines={1}>
        {heroGenre}
      </Text>
    ) : null;

    const titleSection = titleLogoState === 'error' ? (
      <Text
        style={[
          centered ? styles.heroTitleCentered : styles.heroTitle,
          styles.heroTitleFallback,
        ]}
        numberOfLines={2}
      >
        {item.title}
      </Text>
    ) : titleLogo ? (
      <Image
        source={{ uri: titleLogo }}
        style={[
          centered ? styles.heroTitleLogoCentered : styles.heroTitleLogo,
          { height: titleLogoHeight },
        ]}
        contentFit="contain"
        contentPosition={centered ? 'center' : 'left'}
        cachePolicy="memory-disk"
        priority="high"
        transition={0}
        onLoad={e => {
          const width = e.source?.width ?? 0;
          const height = e.source?.height ?? 0;
          if (!width || !height) return;
          const baseWidth = SCREEN_WIDTH * 0.65;
          const fittedHeight = Math.min(84, Math.max(42, Math.round(baseWidth * (height / width))));
          const key = `${item.type}_${item.id}`;
          setHeroLogoStates(prev => (prev[key] === 'loaded' ? prev : { ...prev, [key]: 'loaded' }));
          setHeroLogoHeights(prev => (prev[key] === fittedHeight ? prev : { ...prev, [key]: fittedHeight }));
        }}
        onError={() => {
          const key = `${item.type}_${item.id}`;
          setHeroLogoStates(prev => (prev[key] === 'error' ? prev : { ...prev, [key]: 'error' }));
        }}
      />
    ) : (
      showFallbackText ? (
        <Text
          style={[
            centered ? styles.heroTitleCentered : styles.heroTitle,
            styles.heroTitleFallback,
          ]}
          numberOfLines={2}
        >
          {item.title}
        </Text>
      ) : (
        <View
          style={{
            width: SCREEN_WIDTH * 0.65,
            height: titleLogoHeight,
            marginBottom: 8,
          }}
        />
      )
    );
    const titleSectionBlock = titleLogo ? (
      <View style={{ marginBottom: titleBottomGap }}>
        {titleSection}
      </View>
    ) : (
      titleSection
    );

    const actionRow = (
      <View style={glass ? styles.heroGlassActionRow : styles.heroIconRow}>
        {item.rating > 0 && (
          <View style={[styles.heroIconPill, !isDarkAppearance && styles.ratingPillLightClear, isMonochromeDark && styles.ratingPillMonoDark]}>
            <RatingBadge rating={item.rating} size={12} textColor={ratingTextColor} ratingBackgroundColor={!isDarkAppearance ? 'transparent' : undefined} />
          </View>
        )}
        {!!trailerKey && (
          <TouchableOpacity
            style={[styles.heroIconPill, !isDarkAppearance && styles.heroIconPillLight, isMonochromeDark && styles.heroIconPillMonoDark]}
            activeOpacity={0.8}
            onPress={() => setHeroTrailerVisible(trailerKey)}
          >
            <Ionicons name="film-outline" size={16} color={isMonochromeDark ? '#ffffff' : (isDarkAppearance ? '#111111' : '#111827')} />
          </TouchableOpacity>
        )}
        <TouchableOpacity
          style={[
            styles.heroIconPill,
            isMonochromeDark && styles.heroIconPillMonoDark,
            inWatchlist && appearance === 'dark' && !isMonochromeDark && [
              styles.heroIconPillActive,
              { borderColor: colors.accentSoft, backgroundColor: colors.accent + '30' },
            ],
            inWatchlist && isMonochromeDark && styles.heroIconPillActiveMonoDark,
            !isDarkAppearance && styles.heroIconPillLight,
          ]}
          activeOpacity={0.8}
          onPress={() => toggleWatchlist(item)}
        >
          <Ionicons
            name={inWatchlist ? 'bookmark' : 'bookmark-outline'}
            size={16}
            color={inWatchlist
              ? (isDarkAppearance
                ? (isMonochromeDark ? '#ffffff' : colors.accentSoft)
                : '#111111')
              : (isMonochromeDark ? '#ffffff' : '#111111')}
          />
        </TouchableOpacity>
        <TouchableOpacity
          style={[
            styles.heroIconPill,
            isMonochromeDark && styles.heroIconPillMonoDark,
            watched && isDarkAppearance && !isMonochromeDark && styles.heroIconPillWatchedActive,
            watched && isMonochromeDark && styles.heroIconPillWatchedActiveMonoDark,
            !isDarkAppearance && styles.heroIconPillLight,
          ]}
          activeOpacity={0.8}
          onPress={() => { if (!user) { navigation.navigate('Auth'); return; } if (item.type !== 'tv') { void toggleMovieWatched(Number(item.id), item.imdbId, item.title, item.year); } else { setSeriesWatchConfirmItem(item); } }}
        >
          <Ionicons name={watched ? 'checkmark-circle' : 'checkmark-circle-outline'} size={16} color={isMonochromeDark ? '#ffffff' : watchedIconColor} />
        </TouchableOpacity>
      </View>
    );

    if (glass) {
      return (
        <Animated.View
          style={[
            styles.heroContentGlass,
            {
              top: insets.top + 14,
              bottom: 14,
              opacity: animationValue,
              transform: [{ translateY }, { translateX }],
            },
          ]}
        >
          {glassGenreLabel ? (
            <View style={{ alignItems: 'center', marginBottom: 10 }}>
              {glassGenreLabel}
            </View>
          ) : null}
          <View style={[styles.heroGlassCard, !isDarkAppearance && styles.heroGlassCardLight]}>
            <View style={styles.heroGlassPosterWrap}>
              {glassPosterUri ? (
                <Image
                  source={{ uri: glassPosterUri }}
                  style={styles.heroGlassPoster}
                  contentFit="cover"
                  cachePolicy="memory-disk"
                  priority="high"
                  transition={0}
                />
              ) : (
                <View style={styles.heroGlassPosterFallback}>
                  <Text style={[styles.heroGlassPosterFallbackText, !isDarkAppearance && { color: '#111827' }]} numberOfLines={2}>
                    {item.type === 'movie' ? t('home_movie_badge') : t('home_tv_badge')}
                  </Text>
                </View>
              )}
              <LinearGradient
                colors={isDarkAppearance
                  ? ['rgba(4,6,10,0.08)', 'rgba(4,6,10,0.22)', 'rgba(4,6,10,0.82)']
                  : ['rgba(255,255,255,0.04)', 'rgba(255,255,255,0.10)', 'rgba(8,12,20,0.62)']}
                locations={[0, 0.45, 1]}
                style={styles.heroGlassImageScrim}
                pointerEvents="none"
              />
            </View>
            <View style={styles.heroGlassInfo}>
              <View style={styles.heroGlassTop} />
              {actionRow}
            </View>
          </View>
        </Animated.View>
      );
    }

    if (centered) {
      return (
        <Animated.View style={[styles.heroContentCentered, { opacity: animationValue, transform: [{ translateY }, { translateX }] }]}>
          <View style={styles.heroTopStackCentered}>
            {isDarkAppearance && (
              <LinearGradient
                colors={heroCloudColors}
                locations={[0, 0.36, 0.68, 1]}
                style={heroCloudStyle}
                pointerEvents="none"
              />
            )}
            {badgeRow}
            {titleSectionBlock}
            {showHeroSynopsis && (
              <View style={[
                styles.heroSynopsisWrap,
                styles.heroSynopsisWrapCentered,
                { marginTop: synopsisTopGap },
                !isDarkAppearance && styles.heroSynopsisWrapLight,
              ]}>
                <Text
                  style={[
                    styles.heroDescCentered,
                    showFallbackText ? styles.heroDescFallback : (!isDarkAppearance && styles.heroSynopsisTextLight),
                  ]}
                  numberOfLines={2}
                >
                  {item.description}
                </Text>
              </View>
            )}
              <PrimaryActionButton
                colors={colors}
                themeId={theme.id}
                isLightAppearance={!isDarkAppearance}
                fullWidth
                style={styles.heroPlayBtnCentered}
                label={continueLabel}
              progressPct={hasProgress ? item.progress : null}
              leading={<Ionicons name="play" size={16} color={primaryActionPalette.textColor} />}
              onPress={() => handleHeroPlayPress(item)}
            />
          </View>
          {actionRow}
        </Animated.View>
      );
    }

    return (
      <Animated.View style={[styles.heroContent, { opacity: animationValue, transform: [{ translateY }, { translateX }] }]}>
        <View style={[styles.heroTopStack, styles.heroTopStackClassic]}>
          {isDarkAppearance && (
            <LinearGradient
              colors={heroCloudColors}
              locations={[0, 0.36, 0.68, 1]}
              style={heroCloudStyle}
              pointerEvents="none"
            />
          )}
          <View style={styles.heroTopStackClassicContent}>
            {badgeRow}
            {titleSectionBlock}
            {showHeroSynopsis && (
              <View style={[styles.heroSynopsisWrap, !isDarkAppearance && styles.heroSynopsisWrapLight, styles.heroSynopsisWrapClassic, { marginTop: synopsisTopGap }]}>
                <Text style={[styles.heroDesc, showFallbackText ? styles.heroDescFallback : (!isDarkAppearance && styles.heroSynopsisTextLight), styles.heroDescClassic]} numberOfLines={2}>{item.description}</Text>
              </View>
            )}
            <View style={styles.heroActionsClassic}>
                <PrimaryActionButton
                  colors={colors}
                  themeId={theme.id}
                  isLightAppearance={!isDarkAppearance}
                  style={styles.playBtn}
                  label={classicContinueLabel}
                  progressPct={hasProgress ? item.progress : null}
                  leading={<Ionicons name="play" size={16} color={primaryActionPalette.textColor} />}
                  onPress={() => handleHeroPlayPress(item)}
                />
              <View style={styles.heroActionsClassicRight}>
                {item.rating > 0 && (
                  <View style={[styles.heroActionBadgeDark, !isDarkAppearance && styles.ratingPillOuterClear, isMonochromeDark && styles.heroActionBadgeMonoDark]}>
                    <RatingBadge rating={item.rating} size={13} textColor={ratingTextColor} />
                  </View>
                )}
                <TouchableOpacity
                  style={[
                    styles.heroActionBadgeDark,
                    !isDarkAppearance && styles.heroWatchlistBtnLight,
                    isMonochromeDark && styles.heroActionBadgeMonoDark,
                  ]}
                  activeOpacity={0.8}
                  onPress={() => toggleWatchlist(item)}
                  hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                >
                  <Ionicons
                    name={inWatchlist ? 'bookmark' : 'bookmark-outline'}
                    size={16}
                    color={inWatchlist
                      ? (isMonochromeDark ? '#ffffff' : (isDarkAppearance ? colors.accentSoft : '#111111'))
                      : (isMonochromeDark ? '#ffffff' : (isDarkAppearance ? colors.textPrimary : '#111111'))}
                  />
                </TouchableOpacity>
              </View>
            </View>
          </View>
        </View>
      </Animated.View>
    );
  };

  const renderHeroItem = (index: number, isOverlay: boolean) => {
    const item = heroItems[index];
    if (!item) return null;
    const glass = uiStyle === 'glass';
    const key = `${item.type}_${item.id}`;
    const backgroundUri = heroBackdrops[key] ?? getHeroBackgroundUri(item);
    const imagePosition = item.type === 'tv' ? { top: '30%' } : { top: '28%' };
    const scale = isOverlay ? Animated.multiply(heroScale, heroScrollScale) : heroScrollScale;
    const opacity = isOverlay ? heroFadeIn : 1;
    const bgTranslateX = heroContentAnim.interpolate({
      inputRange: [0, 1],
      outputRange: [22 * heroTransitionDirectionRef.current, 0],
    });
    const centered = uiStyle === 'centered' || uiStyle === 'glass';
    const heroCloudColors: readonly [string, string, string, string] = isDarkAppearance
      ? ['rgba(0,0,0,0.00)', 'rgba(0,0,0,0.08)', 'rgba(0,0,0,0.06)', 'rgba(0,0,0,0.00)']
      : ['rgba(0,0,0,0.02)', 'rgba(0,0,0,0.44)', 'rgba(0,0,0,0.28)', 'rgba(0,0,0,0.04)'];
    const heroBottomFadeBaseHeight = centered ? 290 : 260;
    const heroBottomFadeHeight = isDarkAppearance
      ? heroBottomFadeBaseHeight
      : Math.round(heroBottomFadeBaseHeight * 1.25);
    const bottomFadeColors: [string, string, string] = isDarkAppearance
      ? ['transparent', colors.bgMid, colors.bg]
      : ['rgba(13,13,26,0.00)', 'rgba(13,13,26,0.14)', 'rgba(255,255,255,0.95)'];

    return (
      <Animated.View
        style={[
          styles.heroSlide,
          StyleSheet.absoluteFill,
          glass && { height: heroSectionHeight, overflow: 'visible' as const },
          { opacity },
        ]}
      >
        {uiStyle !== 'glass' ? (
          <>
            <Animated.View style={[StyleSheet.absoluteFill, { transform: [{ scale }] }]}>
              {backgroundUri ? (
                <>
                  <Animated.View style={[StyleSheet.absoluteFill, { transform: [{ translateX: bgTranslateX }, { translateY: heroScrollTranslateY }] }]}>
                    <Image source={{ uri: backgroundUri }} style={styles.heroBg} contentFit="cover" contentPosition={imagePosition} cachePolicy="memory-disk" priority="high" transition={0} />
                  </Animated.View>
                  {isDarkAppearance && <View style={styles.heroPosterBackdropTint} />}
                </>
              ) : null}
            </Animated.View>
            {isDarkAppearance ? (
              <LinearGradient
                colors={heroCloudColors}
                locations={[0, 0.36, 0.68, 1]}
                style={[styles.heroCloudMask, styles.heroCloudMaskLightBleed]}
                pointerEvents="none"
              />
            ) : null}
            {isDarkAppearance ? (
              <LinearGradient
                colors={bottomFadeColors}
                locations={[0, 0.38, 1]}
                style={[styles.heroBottomFade, { height: heroBottomFadeHeight }]}
                pointerEvents="none"
              />
            ) : null}
          </>
        ) : null}
        {isOverlay && renderHeroContent(item, heroContentAnim)}
      </Animated.View>
    );
  };

  const activeAmbientBackdropUri = useMemo(() => {
    const currentHero = heroItems[heroIndex];
    if (!currentHero) return null;
    const currentKey = `${currentHero.type}_${currentHero.id}`;
    return heroBackdrops[currentKey] ?? getHeroBackgroundUri(currentHero);
  }, [heroBackdrops, heroIndex, heroItems]);

  return (
    <View style={{ flex: 1 }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
    <View style={styles.container}>
      {(uiStyle === 'glass' || vividAmbientEnabled) && activeAmbientBackdropUri ? (
        <View pointerEvents="none" style={styles.ambientBackdrop}>
          <RNImage
            source={{ uri: activeAmbientBackdropUri }}
            style={[styles.ambientBackdropImage, uiStyle === 'glass' && { opacity: isDarkAppearance ? 0.72 : 0.62 }]}
            resizeMode="cover"
            blurRadius={uiStyle === 'glass' ? 28 : 20}
          />
          <LinearGradient
            colors={uiStyle === 'glass'
              ? (isDarkAppearance
                ? ['rgba(7,8,12,0.16)', 'rgba(7,8,12,0.54)', colors.bg]
                : ['rgba(255,255,255,0.12)', 'rgba(255,255,255,0.32)', colors.bg])
              : (isDarkAppearance
                ? ['rgba(7,8,12,0.05)', 'rgba(7,8,12,0.62)', colors.bg]
                : ['rgba(255,255,255,0.05)', 'rgba(255,255,255,0.48)', colors.bg])}
            locations={uiStyle === 'glass' ? [0, 0.36, 1] : [0, 0.42, 1]}
            style={styles.ambientBackdropScrim}
          />
        </View>
      ) : null}
      <StatusBar barStyle={isDarkAppearance ? 'light-content' : 'dark-content'} backgroundColor="transparent" translucent />
      {isDarkAppearance && (
        <LinearGradient
          colors={[colors.overlayStrong, 'transparent']}
          style={[styles.topFade, { height: insets.top }]}
          pointerEvents="none"
        />
      )}
      <Animated.ScrollView ref={homeScrollViewRef as any} onScroll={Animated.event([{ nativeEvent: { contentOffset: { y: scrollY } } }], { useNativeDriver: true })} scrollEventThrottle={16} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.accent} />} showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 12 }}>
        {heroItems.length > 0 && (
          <View style={{ marginBottom: uiStyle === 'glass' ? 24 : 18 }}>
            <View style={{ height: heroSectionHeight }} {...heroPanResponder.panHandlers}>
              <TouchableOpacity activeOpacity={1} onPress={() => handleItemPress(heroItems[heroIndex])} style={StyleSheet.absoluteFill}>
                {heroPrevIndex !== null && <View style={StyleSheet.absoluteFill}>{renderHeroItem(heroPrevIndex, false)}</View>}
                {renderHeroItem(heroIndex, true)}
              </TouchableOpacity>
              {isDarkAppearance && uiStyle !== 'glass' && (
                <LinearGradient
                  colors={[colors.bg, 'transparent'] as const}
                  style={{ position: 'absolute', bottom: -100, left: 0, right: 0, height: 100 }}
                  pointerEvents="none"
                />
              )}
            </View>
            <View style={[styles.dots, uiStyle === 'glass' && styles.dotsGlass]}>
              {heroItems.map((_, i) => (
                <AnimatedDot
                  key={`hero-dot-${i}`}
                  active={i === heroIndex}
                  activeColor={isDarkAppearance ? (colors.accent || '#00e676') : '#111827'}
                  inactiveColor={isDarkAppearance ? '#fff' : '#111827'}
                  onPress={() => { slideTo(i); startHeroTimer(); }}
                />
              ))}
            </View>
          </View>
        )}
        {user && allContinueWatching.length > 0 && (
          <SectionStrip
            title={t('section_continue')}
            data={allContinueWatching}
            loading={false}
            onViewAll={() => navigation.navigate('ContinueWatching')}
            onItemPress={handleItemPress}
            onItemLongPress={handleLongPress}
            renderCard={item => (
              <ContinueWatchingCard
                item={item}
                cardStyle={continueWatchingStyle}
                onPress={() => handleItemPress(item)}
                onLongPress={() => handleLongPress(item)}
              />
            )}
          />
        )}
        {sections.filter(s => s.enabled).map(s => {
          const title = defaultSections.find(d => d.id === s.id)?.title ?? s.title;
          if (s.id === 'networks') return <NetworkStrip key={s.id} title={title} data={sectionData[s.id] || []} loading={loading} onNetworkPress={handleNetworkPress} />;
          return <SectionStrip key={s.id} title={title} data={sectionData[s.id] || []} loading={loading} onViewAll={() => handleViewAll(s.id, title)} onItemPress={handleItemPress} onItemLongPress={handleLongPress} />;
        })}
        {traktConnected && traktRecommendations.length > 0 && <SectionStrip title={t('section_recommended')} data={traktRecommendations} loading={false} onViewAll={() => navigation.navigate('TraktCollection', { mode: 'recommended' })} onItemPress={handleItemPress} onItemLongPress={handleLongPress} />}
        {user && traktConnected && traktTrending.length > 0 && <SectionStrip title={t('section_trakt_trending')} data={traktTrending} loading={false} onViewAll={() => navigation.navigate('TraktCollection', { mode: 'trending' })} onItemPress={handleItemPress} onItemLongPress={handleLongPress} />}
        {combinedWatchlist.length > 0 && <SectionStrip title={t('section_watchlist')} data={combinedWatchlist} loading={false} onViewAll={() => navigation.navigate('Watchlist')} onItemPress={handleItemPress} onItemLongPress={handleLongPress} />}
      </Animated.ScrollView>
      <ActionSheet visible={!!longPressItem} onClose={() => setLongPressItem(null)} title={longPressItem?.title} subtitle={longPressItem?.year ? String(longPressItem.year) : undefined} actions={buildLongPressActions(longPressItem)} />
      <ActionSheet
        visible={!!heroPlayChoiceItem}
        onClose={() => setHeroPlayChoiceItem(null)}
        title={heroPlayChoiceItem?.title}
        subtitle={heroPlayChoiceItem?.year ? String(heroPlayChoiceItem.year) : undefined}
        actions={[
          {
            label: Number(heroPlayChoiceItem?.progress) > 0
              ? (() => {
                  const continueTime = getContinueSeconds(heroPlayChoiceItem);
                  return continueTime != null
                    ? `${t('home_continue_btn')} from ${formatContinueTime(continueTime)}`
                    : t('home_continue_btn');
                })()
              : t('home_play_now_btn'),
            icon: 'play-circle-outline',
            variant: 'accent',
            onPress: () => {
              if (heroPlayChoiceItem) handleItemPress(heroPlayChoiceItem);
            },
          },
          {
            label: t('media_rewatch'),
            icon: 'refresh-circle-outline',
            variant: 'default',
            onPress: () => {
              if (heroPlayChoiceItem) handleHeroRewatchPress(heroPlayChoiceItem);
            },
          },
          { label: t('common_cancel'), icon: 'close-outline', variant: 'cancel', onPress: () => {} },
        ]}
      />
      <ConfirmSheet visible={!!seriesWatchConfirmItem} onClose={() => setSeriesWatchConfirmItem(null)} icon="checkmark-circle-outline" title={t('watched_series_title')} message={t('watched_series_msg')} confirmLabel={t('watched_series_confirm')} variant="accent" onConfirm={() => { if (seriesWatchConfirmItem) handleSeriesMarkWatched(seriesWatchConfirmItem); setSeriesWatchConfirmItem(null); }} />
      {!!heroTrailerVisible && <TrailerModal visible trailerKey={heroTrailerVisible} onClose={() => setHeroTrailerVisible(null)} />}
    </View>
      </BlurTargetView>
      <StackBottomNav activeTab="Home" blurTarget={blurTargetRef} />
    </View>
  );
};
