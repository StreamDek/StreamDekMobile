import React, { useEffect, useState, useMemo, useCallback, useRef } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity,
  ActivityIndicator, FlatList, StatusBar, Modal, Pressable, Animated, Linking,
  Dimensions, Platform,
} from 'react-native';
import { runIdle } from '../utils/idleTask';
import { Image } from 'expo-image';
import { BlurTargetView, BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import * as ScreenOrientation from 'expo-screen-orientation';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { API_BASE } from '../constants/api';
import { tmdbFetch } from '../utils/tmdbFetch';
import { Storage } from '../utils/storage';
import { TrailerModal } from '../components/TrailerModal';
import { PrimaryActionButton, getPrimaryActionPalette } from '../components/PrimaryActionButton';
import { ActionSheet } from '../components/ActionSheet';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { StreamSourceRow } from '../components/StreamSourceRow';
import { GlassBackButton } from '../components/GlassBackButton';
import { HeroTrailerBackground } from '../components/HeroTrailerBackground';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useUIStyle } from '../context/UIStyleContext';
import { useDisplaySettings } from '../context/DisplaySettingsContext';
import { useTrakt } from '../context/TraktContext';
import { useAddons, AddonStream } from '../context/AddonContext';
import { useDebrid } from '../context/DebridContext';
import { useLanguage } from '../context/LanguageContext';
import { useMdbListSettings } from '../context/MdbListSettingsContext';
import { useWatched } from '../context/WatchedContext';
import { movieProgressKey, episodeProgressKey, useWatchProgress } from '../context/WatchProgressContext';
import { useAppLifecycle } from '../context/AppLifecycleContext';
import { buildAuthHeaders } from '../utils/authHeaders';
import {
  normalizeWatchlistItem,
  readWatchlistItems,
  readWatchlistRemovalIds,
  watchlistItemMatchesId,
  writeWatchlistItems,
  writeWatchlistRemovalIds,
} from '../utils/watchlist';
import { StackBottomNav, BOTTOM_NAV_HEIGHT } from '../components/StackBottomNav';
import { MediaDetailSkeleton } from '../components/Skeleton';
import { getProfileStorageOwnerId, progressFileStorageKey } from '../utils/profileStorage';
import { getStreamCapableAddonsForType } from '../utils/addonCapabilities';
import {
  ExternalRating,
  MDBLIST_PROVIDER_AUDIENCE,
  MDBLIST_PROVIDER_IMDB,
  MDBLIST_PROVIDER_LETTERBOXD,
  MDBLIST_PROVIDER_METACRITIC,
  MDBLIST_PROVIDER_TMDB,
  MDBLIST_PROVIDER_TOMATOES,
  MDBLIST_PROVIDER_TRAKT,
  fetchMdbListRatings,
  getEnabledMdbListProviders,
  getMdbListErrorCode,
  shouldFetchMdbListRatings,
} from '../utils/mdblist';



// ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Streaming provider deep links (TMDB provider ID ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ app scheme + website) ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬
const PROVIDER_LINKS: Record<number, { appScheme: string; web: string }> = {
  8:    { appScheme: 'nflx://www.netflix.com/browse',          web: 'https://www.netflix.com' },
  9:    { appScheme: 'aiv://aiv/search',                       web: 'https://www.primevideo.com' },
  337:  { appScheme: 'disneyplus://',                          web: 'https://www.disneyplus.com' },
  350:  { appScheme: 'videos://',                              web: 'https://tv.apple.com' },
  2:    { appScheme: 'videos://',                              web: 'https://tv.apple.com' },
  15:   { appScheme: 'hulu://',                                web: 'https://www.hulu.com' },
  1899: { appScheme: 'max://',                                 web: 'https://www.max.com' },
  384:  { appScheme: 'max://',                                 web: 'https://www.max.com' },
  387:  { appScheme: 'max://',                                 web: 'https://www.max.com' },
  386:  { appScheme: 'peacock://',                             web: 'https://www.peacocktv.com' },
  531:  { appScheme: 'paramount://',                           web: 'https://www.paramountplus.com' },
  37:   { appScheme: 'showtime://',                            web: 'https://www.sho.com' },
  43:   { appScheme: 'starz://',                               web: 'https://www.starz.com' },
  283:  { appScheme: 'crunchyroll://',                         web: 'https://www.crunchyroll.com' },
  192:  { appScheme: 'youtube://',                             web: 'https://www.youtube.com' },
  188:  { appScheme: 'youtube://',                             web: 'https://www.youtube.com' },
  300:  { appScheme: 'plutotv://',                             web: 'https://pluto.tv' },
  520:  { appScheme: 'discoveryplus://',                       web: 'https://www.discoveryplus.com' },
  521:  { appScheme: 'discoveryplus://',                       web: 'https://www.discoveryplus.com' },
  257:  { appScheme: 'fubo://',                                web: 'https://www.fubo.tv' },
  444:  { appScheme: 'paramountplus://',                       web: 'https://www.paramountplus.com' },
  613:  { appScheme: 'frndlytv://',                            web: 'https://frndlytv.com' },
};

const { width: SCREEN_WIDTH } = Dimensions.get('window');

async function openProvider(provider: { id: number; name: string }): Promise<void> {
  const link = PROVIDER_LINKS[provider.id];
  if (!link) return;
  try {
    const canOpen = await Linking.canOpenURL(link.appScheme);
    await Linking.openURL(canOpen ? link.appScheme : link.web);
  } catch {
    try { await Linking.openURL(link.web); } catch { /* ignore */ }
  }
}

/** Per-user individual progress file storage key ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â must match PlayerScreen. */
function progressFileKey(uid: string | null, itemKey: string): string {
  return progressFileStorageKey(uid, itemKey);
}

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

function formatRuntimeButton(runtimeMinutes?: number | null): string | null {
  if (!runtimeMinutes || runtimeMinutes <= 0) return null;
  const hours = Math.floor(runtimeMinutes / 60);
  const minutes = runtimeMinutes % 60;
  if (hours <= 0) return `${minutes}m`;
  if (minutes === 0) return `${hours}h`;
  return `${hours}h ${minutes}m`;
}

function formatDisplayName(code: string | null | undefined, type: 'language' | 'region'): string | null {
  if (!code) return null;
  try {
    const label = new Intl.DisplayNames(['en'], { type }).of(code.toUpperCase());
    return label ?? code.toUpperCase();
  } catch {
    return code.toUpperCase();
  }
}

function formatCountryList(value: unknown): string | null {
  const countries = Array.isArray(value) ? value : value ? [value] : [];
  const labels = countries
    .map(country => {
      if (typeof country === 'object' && country !== null) {
        const record = country as Record<string, unknown>;
        const code = record.iso_3166_1 ?? record.code;
        const name = record.name;
        if (typeof code === 'string' && code.trim()) return formatDisplayName(code, 'region');
        if (typeof name === 'string' && name.trim()) return name;
        return null;
      }
      return formatDisplayName(String(country), 'region');
    })
    .filter((country): country is string => Boolean(country));
  return labels.length > 0 ? labels.join(', ') : null;
}

function formatOriginalLanguage(media: any): string | null {
  const code = media?.originalLanguage ?? media?.original_language;
  if (code) return formatDisplayName(code, 'language');
  const spoken = media?.spokenLanguages ?? media?.spoken_languages;
  const first = Array.isArray(spoken) ? spoken[0] : null;
  if (!first) return null;
  return first.english_name ?? first.name ?? formatDisplayName(first.iso_639_1, 'language');
}

function formatContinueTime(positionSec?: number | null): string | null {
  if (!positionSec || !Number.isFinite(positionSec) || positionSec <= 0) return null;
  const totalSeconds = Math.max(0, Math.floor(positionSec));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function truncateButtonText(value?: string | null, maxLength = 42): string | null {
  if (!value) return null;
  const trimmed = value.trim().replace(/\s+/g, ' ');
  if (!trimmed) return null;
  if (trimmed.length <= maxLength) return trimmed;
  return `${trimmed.slice(0, Math.max(0, maxLength - 3)).trimEnd()}...`;
}

function clampProgressPercent(positionSec: number, durationSec: number): number | null {
  if (!Number.isFinite(positionSec) || !Number.isFinite(durationSec) || positionSec <= 0 || durationSec <= 0) return null;
  const pct = (positionSec / durationSec) * 100;
  if (!Number.isFinite(pct) || pct <= 0) return null;
  return Math.max(0, Math.min(100, pct));
}

function getEpisodeRuntimeSeconds(episode: any, fallbackRuntimeMinutes?: number | null): number {
  const runtimeMinutes = Number(episode?.runtime ?? fallbackRuntimeMinutes ?? 0);
  if (!Number.isFinite(runtimeMinutes) || runtimeMinutes <= 0) return 0;
  return Math.round(runtimeMinutes * 60);
}

function isFutureDate(value?: string | null): boolean {
  if (!value) return false;
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return date.getTime() > today.getTime();
}

type Tab = 'about' | 'seasons' | 'streams';

const externalRatingVisuals: Record<string, { label: string; color: string; format: (value: number) => string; logo?: any; logoWidth?: number; logoMarginRight?: number }> = {
  [MDBLIST_PROVIDER_IMDB]: { label: 'IMDb', color: '#F5C518', format: value => value.toFixed(1), logo: require('../../assets/ratings/imdb-logo.png'), logoWidth: 28, logoMarginRight: 0 },
  [MDBLIST_PROVIDER_TMDB]: { label: 'TMDB', color: '#01B4E4', format: value => String(Math.round(value)), logo: require('../../assets/ratings/tmdb-logo.png'), logoWidth: 28, logoMarginRight: -5 },
  [MDBLIST_PROVIDER_TOMATOES]: { label: 'RT', color: '#FA320A', format: value => `${Math.round(value)}%`, logo: require('../../assets/ratings/rotten-tomatoes-logo.png'), logoWidth: 22, logoMarginRight: -4 },
  [MDBLIST_PROVIDER_METACRITIC]: { label: 'MC', color: '#FFCC33', format: value => String(Math.round(value)), logo: require('../../assets/ratings/metacritic-logo.png'), logoWidth: 22, logoMarginRight: -4 },
  [MDBLIST_PROVIDER_TRAKT]: { label: 'Trakt', color: '#ED1C24', format: value => String(Math.round(value)), logo: require('../../assets/ratings/trakt-logo.png'), logoWidth: 24, logoMarginRight: -4 },
  [MDBLIST_PROVIDER_LETTERBOXD]: { label: 'LB', color: '#00E054', format: value => value.toFixed(1), logo: require('../../assets/ratings/letterboxd-logo.png'), logoWidth: 26, logoMarginRight: -5 },
  [MDBLIST_PROVIDER_AUDIENCE]: { label: 'Audience', color: '#FA320A', format: value => `${Math.round(value)}%`, logo: require('../../assets/ratings/audience-score.png'), logoWidth: 22, logoMarginRight: -4 },
};

const INLINE_FALLBACK_RATING_SOURCE = '__inline_fallback_rating__';

function buildFallbackExternalRatings(value: unknown): ExternalRating[] {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return [];
  return [{ source: INLINE_FALLBACK_RATING_SOURCE as any, value: Math.round(value * 10) / 10 }];
}

function formatInlineFallbackRating(rating: ExternalRating | null | undefined): string | null {
  if (!rating) return null;
  if (String(rating.source) === INLINE_FALLBACK_RATING_SOURCE) return rating.value.toFixed(1);
  const visuals = externalRatingVisuals[rating.source];
  const label = visuals ? visuals.label : 'IMDb';
  const formatted = visuals ? visuals.format(rating.value) : rating.value.toFixed(1);
  return label + ' ' + formatted;
}

function ExternalRatingsRow({
  ratings,
  colors,
  isLightAppearance,
  centered = false,
  fallbackGenre = null,
}: {
  ratings: ExternalRating[];
  colors: ThemeColors;
  isLightAppearance: boolean;
  centered?: boolean;
  fallbackGenre?: string | null;
}) {
  if (ratings.length === 0) return null;

  return (
    <View
      style={{
        flexDirection: 'row',
        flexWrap: 'nowrap',
        alignItems: 'center',
        alignSelf: centered ? 'center' : 'stretch',
        gap: 8,
        width: centered ? 'auto' : '100%',
        maxWidth: '100%',
        justifyContent: centered ? 'center' : 'flex-start',
      }}
    >
      {ratings.map(rating => {
        const visuals = externalRatingVisuals[rating.source];
        const isInlineFallback = String(rating.source) === INLINE_FALLBACK_RATING_SOURCE;
        const fallbackVisuals = externalRatingVisuals[MDBLIST_PROVIDER_IMDB];
        const effectiveVisuals = isInlineFallback ? fallbackVisuals : visuals;
        if (!effectiveVisuals && !isInlineFallback) return null;
        const ratingColor = effectiveVisuals?.color ?? (isLightAppearance ? '#0f172a' : '#f8fafc');
        return (
          <View
            key={rating.source}
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 1,
              minWidth: 0,
            }}
          >
            {effectiveVisuals?.logo ? (
              <Image
                source={effectiveVisuals.logo}
                style={{ width: effectiveVisuals.logoWidth ?? 24, height: 12, marginRight: effectiveVisuals.logoMarginRight ?? 0 }}
                contentFit="contain"
                transition={0}
              />
            ) : (
              <Text style={{ color: ratingColor, fontSize: 10, fontWeight: '900' }}>{effectiveVisuals?.label ?? 'IMDb'}</Text>
            )}
            <Text style={{ color: ratingColor, fontSize: 12, fontWeight: '800' }}>{isInlineFallback ? rating.value.toFixed(1) : effectiveVisuals?.format(rating.value)}</Text>
            {isInlineFallback && fallbackGenre ? (
              <Text style={{ color: isLightAppearance ? colors.textSecondary : colors.subText, fontSize: 12, fontWeight: '700', marginLeft: 4 }}>{String.fromCharCode(183)} {fallbackGenre}</Text>
            ) : null}
          </View>
        );
      })}
    </View>
  );
}

const makeStyles = (c: ThemeColors, isLightAppearance: boolean, vividAmbient: boolean) => {
  const isLightMonochrome = isLightAppearance && c.accent === '#ffffff' && c.buttonText === '#111111';
  const isMonochromeDark = !isLightAppearance && c.accent === '#ffffff' && c.buttonText === '#111111';
  return StyleSheet.create({
  descriptionContainer: {
    marginTop: 4,
    marginBottom: 12,
  },
  moreButton: {
    alignSelf: 'flex-start',
    marginTop: 4,
    paddingVertical: 4,
  },
  moreButtonText: {
    color: c.accentSoft,
    fontSize: 13,
    fontWeight: '700',
  },
  container: { flex: 1 },
  glassContainer: {
    flex: 1,
    backgroundColor: 'transparent',
  },
  ambientBackdrop: {
    ...StyleSheet.absoluteFillObject,
  },
  glassAmbientBackdrop: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 0,
  },
  ambientBackdropImage: {
    ...StyleSheet.absoluteFillObject,
    opacity: 0.60,
  },
  glassAmbientBackdropImage: {
    ...StyleSheet.absoluteFillObject,
    opacity: 0.78,
  },
  ambientBackdropScrim: {
    ...StyleSheet.absoluteFillObject,
  } as any,
  glassAmbientVeil: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.10)' : 'rgba(4,6,10,0.18)',
  },
  ambientBackdropGlow: {
    ...StyleSheet.absoluteFillObject,
    opacity: 0.72,
  } as any,
  loader: { flex: 1, backgroundColor: c.bg, justifyContent: 'center', alignItems: 'center' },
  backdropWrapper: { height: 465, position: 'relative' },
  backdrop: { width: '100%', height: 465 },
  glassHeroSection: {
    paddingHorizontal: 20,
    paddingBottom: 22,
    alignItems: 'center',
    position: 'relative',
  },
  glassHeroTitle: {
    color: isLightAppearance ? c.textPrimary : '#fff',
    fontSize: 25,
    lineHeight: 31,
    fontWeight: '900',
    textAlign: 'center',
    marginBottom: 18,
    paddingHorizontal: 58,
    textShadowColor: isLightAppearance ? 'rgba(255,255,255,0.30)' : 'rgba(0,0,0,0.35)',
    textShadowRadius: 14,
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
  glassHeroImageScrim: {
    ...StyleSheet.absoluteFillObject,
  } as any,
  backdropGlassOverlay: {
    position: 'absolute',
    left: 8,
    right: 8,
    bottom: 0,
    height: 150,
    borderTopLeftRadius: 220,
    borderTopRightRadius: 220,
    zIndex: 0,
    overflow: 'hidden',
  },
  backdropGlassScrim: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    backgroundColor: 'transparent',
    zIndex: 2,
  },
  backdropGlassTopFade: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    height: 72,
    borderTopLeftRadius: 220,
    borderTopRightRadius: 220,
    zIndex: 3,
  },
  backdropGlassSideFeather: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: 48,
    zIndex: 4,
  },
  heroInfoShell: {
    width: '100%',
  },
  heroInfoShellCentered: {
    width: '100%',
    marginBottom: -18,
  },
  heroInfoShellGlass: {
    width: '100%',
    marginTop: -8,
  },
  heroActionsShell: {
    position: 'relative',
    zIndex: 2,
    width: '100%',
    backgroundColor: 'transparent',
    marginTop: -16,
  },
  classicTitleBlock: {
    height: 60,
    justifyContent: 'center',
    overflow: 'hidden',
    marginBottom: 18,
  },
  classicTitleLogo: {
    width: '100%',
    height: '100%',
    alignSelf: 'flex-start' as const,
    shadowColor: '#000',
    shadowOpacity: isLightAppearance ? 0.28 : 0.18,
    shadowRadius: 5,
    shadowOffset: { width: 0, height: 0 },
    elevation: isLightAppearance ? 2 : 0,
  },
  backdropPosterTint: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.34)',
  },
  backdropPosterFrame: {
    position: 'absolute',
    top: 24,
    left: 0,
    right: 0,
    bottom: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  backdropPosterImage: {
    width: 140,
    height: 210,
    borderRadius: 16,
  },
  backdropGradient: { position: 'absolute', bottom: 0, left: 0, right: 0, height: 208 },
  metaRow: { flexDirection: 'row', padding: 14, paddingTop: 12, marginTop: -50, gap: 16 },
  poster: { width: 100, height: 150, borderRadius: 12, borderWidth: 2, borderColor: c.accent + '44' },
  metaInfo: { flex: 1, paddingTop: 60 },
  title:   { color: isLightAppearance ? c.textPrimary : '#fff', fontSize: 20, fontWeight: '800', lineHeight: 26, marginBottom: 4 },
  titleTextBlock: {
    height: 60,
    justifyContent: 'center',
    overflow: 'hidden',
    marginBottom: 18,
  },
  titleText: {
    color: isLightAppearance ? c.textPrimary : '#fff',
    fontSize: 20,
    fontWeight: '800',
    lineHeight: 26,
    marginBottom: 0,
  },
  tagline: {
    color: isLightAppearance ? c.textSecondary : c.subText,
    fontSize: 12,
    fontStyle: 'italic',
    marginBottom: isLightAppearance ? 14 : 10,
  },
  pills:        { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  pill:         { backgroundColor: c.accent, borderRadius: 20, paddingHorizontal: 10, paddingVertical: 4 },
  pillDark:     {
    backgroundColor: isMonochromeDark ? c.cardBg : (isLightAppearance ? 'rgba(255,255,255,0.68)' : 'rgba(255,255,255,0.5)'),
    borderWidth: 1,
    borderColor: isMonochromeDark ? c.border : (isLightAppearance ? 'rgba(17,24,39,0.24)' : 'rgba(255,255,255,0.6)'),
    shadowColor: '#000',
    shadowOpacity: isLightAppearance ? 0.08 : 0,
    shadowRadius: 3,
    shadowOffset: { width: 0, height: 1 },
    elevation: isLightAppearance ? 1 : 0,
  },
  pillText:     { color: c.buttonText, fontSize: 11, fontWeight: '700' },
  pillDarkText: { color: isMonochromeDark ? c.textPrimary : (isLightAppearance ? c.textPrimary : '#111827'), fontSize: 11, fontWeight: '800' },
  externalRatingsSection: { paddingHorizontal: 14, paddingTop: 0, paddingBottom: 8, marginTop: -2, marginBottom: 4, alignItems: 'center' },
  glassExternalRatingsSection: { paddingBottom: 0, marginBottom: 0 },
  ratingsLoadingText: { fontSize: 12, fontWeight: '600' },
  detailStatusText: { color: isLightAppearance ? c.textSecondary : c.subText, fontSize: 12, lineHeight: 18, marginTop: 4 },
  actions: {
    flexDirection: 'row',
    paddingHorizontal: 14,
    gap: 10,
    marginBottom: 24,
    marginTop: 2,
    flexWrap: 'wrap',
  },
  classicActionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 14,
    marginBottom: 12,
    marginTop: 14,
  },
  classicActionRowLeft: {
    flex: 1,
    minWidth: 0,
  },
  classicActionRowRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    flexWrap: 'nowrap',
    flexShrink: 0,
  },
  trailerBtn: {
    backgroundColor: isLightAppearance ? c.cardBg : (vividAmbient ? c.inputBg + '99' : c.inputBg),
    borderRadius: 18,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: c.border,
  },
  trailerBtnText: { color: isLightAppearance ? c.textPrimary : '#c9c9d6', fontSize: 14, fontWeight: '700' },
  watchlistBtn: {
    backgroundColor: isLightAppearance ? c.cardBg : (vividAmbient ? c.inputBg + '99' : c.inputBg),
    borderRadius: 18,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: c.border,
    minWidth: 50,
  },
  watchlistBtnActive: isLightAppearance
    ? { borderColor: 'rgba(17,24,39,0.30)', backgroundColor: 'rgba(17,24,39,0.10)' }
    : { borderColor: c.border, backgroundColor: vividAmbient ? c.inputBg + '99' : c.inputBg },
  watchlistBtnText: { color: isLightAppearance ? c.textPrimary : '#e8e8f0', fontSize: 13, fontWeight: '700' },
  watchedBtn: { backgroundColor: isLightAppearance ? c.cardBg : (vividAmbient ? c.inputBg + '99' : c.inputBg), borderRadius: 18, padding: 14, alignItems: 'center', borderWidth: 1, borderColor: c.border, minWidth: 50 },
  watchedBtnActive: isLightAppearance
    ? { borderColor: 'rgba(27,94,32,0.45)', backgroundColor: 'rgba(27,94,32,0.12)' }
    : { borderColor: c.border, backgroundColor: vividAmbient ? c.inputBg + '99' : c.inputBg },
  epWatchedDot: {
    position: 'absolute', top: 6, right: 6,
    width: 22, height: 22, borderRadius: 11,
    backgroundColor: '#00e676', justifyContent: 'center', alignItems: 'center',
  },
  tabs: { flexDirection: 'row', paddingLeft: 14, paddingRight: 0, gap: 8, marginBottom: 14, alignSelf: 'flex-start' },
  tab: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.52)' : (vividAmbient ? c.inputBg + '99' : c.inputBg),
    borderWidth: 1,
    borderColor: isLightAppearance ? 'rgba(17,24,39,0.18)' : 'transparent',
  },
  activeTab: {
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.72)' : c.accent + '22',
    borderColor: isLightAppearance ? 'rgba(17,24,39,0.28)' : c.accent,
  },
  tabDisabled: {
    opacity: 0.68,
  },
  tabInlineSpinner: {
    alignSelf: 'center' as const,
    marginBottom: -1,
    transform: [{ scale: 0.68 }],
  },
  tabLabelWrap: {
    minWidth: 0,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
  },
  tabLoadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
  },
  tabText: { color: isLightAppearance ? c.textPrimary : c.subText, fontSize: 13, fontWeight: '700' },
  activeTabText: { color: isLightAppearance ? c.textPrimary : c.accentSoft },
  tabContent: {},
  overview: { color: isLightAppearance ? c.textSecondary : '#c0c0d8', fontSize: 14, lineHeight: 22 },
  seasonPicker: { marginTop: 10, marginBottom: 16, marginHorizontal: -14 },
  seasonChip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.52)' : (vividAmbient ? c.inputBg + '99' : c.inputBg),
    marginRight: 8,
    borderWidth: 1,
    borderColor: isLightAppearance ? 'rgba(17,24,39,0.18)' : 'transparent',
  },
  seasonChipActive: {
    backgroundColor: isLightAppearance ? 'rgba(17,24,39,0.10)' : c.accent,
    borderColor: isLightAppearance ? 'rgba(17,24,39,0.30)' : c.accent,
  },
  seasonChipText: { color: isLightAppearance ? c.textPrimary : c.subText, fontWeight: '700', fontSize: 13 },
  seasonChipTextActive: { color: isLightAppearance ? c.textPrimary : c.buttonText },
  seasonShelfHeader: {
    marginBottom: 12,
  },
  seasonShelfHeaderRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 12,
  },
  seasonShelfHeaderInfo: {
    flex: 1,
    minWidth: 0,
  },
  seasonShelfTitle: {
    color: isLightAppearance ? c.textPrimary : '#e8e8f0',
    fontSize: 18,
    fontWeight: '900',
  },
  seasonShelfMeta: {
    color: isLightAppearance ? c.textSecondary : c.subText,
    fontSize: 12,
    fontWeight: '600',
    marginTop: 3,
  },
  seasonWatchBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: isLightAppearance ? 'rgba(17,24,39,0.18)' : c.border,
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.58)' : (vividAmbient ? c.inputBg + '99' : c.inputBg),
  },
  seasonWatchBtnActive: {
    borderColor: isLightAppearance ? 'rgba(27,94,32,0.45)' : '#00e676',
    backgroundColor: isLightAppearance ? 'rgba(27,94,32,0.12)' : '#00e67618',
  },
  seasonWatchBtnText: {
    color: isLightAppearance ? c.textPrimary : c.subText,
    fontSize: 12,
    fontWeight: '700',
  },
  seasonWatchBtnTextActive: {
    color: isLightAppearance ? '#1b5e20' : '#00e676',
  },
  episodeShelf: {
    marginHorizontal: -14,
  },
  episodeShelfContent: {
    paddingHorizontal: 14,
    paddingRight: 22,
    paddingBottom: 4,
  },
  episodeCard: {
    width: Math.min(SCREEN_WIDTH * 0.76, 286),
    marginRight: 12,
    borderRadius: 16,
    overflow: 'hidden',
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.72)' : (vividAmbient ? c.inputBg + 'aa' : c.inputBg),
    borderWidth: 1,
    borderColor: isLightAppearance ? 'rgba(17,24,39,0.12)' : c.border,
  },
  episodeCardStillWrap: {
    width: '100%',
    aspectRatio: 16 / 9,
    backgroundColor: isLightAppearance ? c.inputBg : c.cardBg,
    position: 'relative',
  },
  episodeCardStill: {
    width: '100%',
    height: '100%',
  },
  episodeCardInfo: {
    padding: 12,
    minHeight: 118,
  },
  episodeCardTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
    marginBottom: 7,
  },
  episodeCardCode: {
    color: isLightAppearance ? c.textPrimary : c.accentSoft,
    fontSize: 12,
    fontWeight: '900',
  },
  episodeCardWatchBadge: {
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: '#00e676',
    alignItems: 'center',
    justifyContent: 'center',
  },
  episodeCardTitle: {
    color: isLightAppearance ? c.textPrimary : '#e8e8f0',
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '800',
    marginBottom: 5,
  },
  episodeCardMeta: {
    color: isLightAppearance ? c.textSecondary : c.mutedText,
    fontSize: 11,
    fontWeight: '600',
    marginBottom: 7,
  },
  episodeCardOverview: {
    color: isLightAppearance ? c.textSecondary : c.subText,
    fontSize: 11,
    lineHeight: 16,
  },
  episodeRow: {
    flexDirection: 'row',
    marginBottom: 16,
    backgroundColor: isLightAppearance ? c.cardBg : c.inputBg,
    borderRadius: 12,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: isLightAppearance ? c.border : 'transparent',
  },
  epStill: { width: 120, height: 72, borderTopLeftRadius: 12, borderBottomLeftRadius: 12 },
  epStillPlaceholder: { justifyContent: 'center', alignItems: 'center', backgroundColor: isLightAppearance ? c.inputBg : c.cardBg },
  epInfo: { flex: 1, padding: 10 },
  epNum:  { color: isLightAppearance ? c.textPrimary : c.accent, fontSize: 11, fontWeight: '700', marginBottom: 2 },
  epName: { color: isLightAppearance ? c.textPrimary : '#e8e8f0', fontSize: 13, fontWeight: '700', marginBottom: 4 },
  epMetaRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 6, flexWrap: 'wrap' },
  epMetaText: { color: isLightAppearance ? c.textSecondary : c.mutedText, fontSize: 11, fontWeight: '600' },
  epOverview: { color: isLightAppearance ? c.textSecondary : c.subText, fontSize: 11, lineHeight: 16 },
  episodeProgressTrack: { height: 3, borderRadius: 999, backgroundColor: c.border, marginTop: 8, overflow: 'hidden' },
  episodeProgressFill: { height: 3, borderRadius: 999, backgroundColor: c.progressFill },
  castCard: { width: 90, marginRight: 12, alignItems: 'center' },
  castPhoto: { width: 80, height: 80, borderRadius: 40, marginBottom: 6, backgroundColor: c.border },
  castNoPhoto: { justifyContent: 'center', alignItems: 'center' },
  castName: { color: isLightAppearance ? c.textPrimary : '#e8e8f0', fontSize: 11, fontWeight: '700', textAlign: 'center' },
  castChar: { color: isLightAppearance ? c.textSecondary : c.subText, fontSize: 10, textAlign: 'center' },
  metaSection: { marginTop: 18 },
  metaGrid: { flexDirection: 'row', flexWrap: 'nowrap', gap: 10, marginTop: 10, paddingRight: 8 },
  metaCard: {
    minWidth: 0, flex: 1, padding: 12, borderRadius: 12,
    backgroundColor: c.inputBg, borderWidth: 1, borderColor: c.border,
  },
  metaCardLabel: { color: c.mutedText, fontSize: 10, fontWeight: '700', letterSpacing: 0.8, textTransform: 'uppercase', marginBottom: 5 },
  metaCardValue: { color: isLightAppearance ? c.textPrimary : '#e8e8f0', fontSize: 13, fontWeight: '700' },
  compactMetaList: {
    gap: 8,
    marginTop: 10,
    alignItems: 'flex-start',
  },
  compactMetaRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'flex-start',
    flexWrap: 'wrap',
  },
  compactMetaLabel: {
    color: isLightAppearance ? c.textPrimary : '#e8e8f0',
    fontSize: 13,
    fontWeight: '800',
  },
  compactMetaValue: {
    color: isLightAppearance ? c.textPrimary : '#e8e8f0',
    fontSize: 13,
    fontWeight: '600',
    opacity: 0.86,
  },
  relatedSection: { marginTop: 24 },
  commentRow: { paddingLeft: 14, paddingRight: 14 },
  commentCard: {
    width: Math.min(SCREEN_WIDTH * 0.76, 320),
    minHeight: 220,
    marginRight: 12,
    padding: 18,
    borderRadius: 24,
    backgroundColor: isLightAppearance ? c.cardBg : (vividAmbient ? c.inputBg + '99' : c.inputBg),
    borderWidth: 1,
    borderColor: c.border,
    justifyContent: 'space-between',
  },
  commentCardHeader: {
    marginBottom: 14,
  },
  commentAuthor: {
    color: isLightAppearance ? c.textPrimary : '#ffffff',
    fontSize: 17,
    fontWeight: '800',
    marginBottom: 10,
  },
  commentRatingPill: {
    alignSelf: 'flex-start',
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 999,
    backgroundColor: isLightAppearance ? c.inputBg : c.cardBg,
    borderWidth: 1,
    borderColor: c.border,
  },
  commentRatingText: {
    color: isLightAppearance ? c.textSecondary : c.subText,
    fontSize: 12,
    fontWeight: '700',
  },
  commentBody: {
    color: isLightAppearance ? c.textSecondary : '#c9c9d6',
    fontSize: 15,
    lineHeight: 22,
    flex: 1,
  },
  commentFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginTop: 18,
  },
  commentFooterText: {
    color: isLightAppearance ? c.textSecondary : c.subText,
    fontSize: 13,
    fontWeight: '700',
  },
  relatedCard: { width: 140, marginRight: 12 },
  relatedPoster: { width: 140, height: 210, borderRadius: 12, backgroundColor: c.cardBg, marginBottom: 8 },
  relatedPosterPlaceholder: { justifyContent: 'center', alignItems: 'center' },
  relatedTitle: { color: isLightAppearance ? c.textPrimary : '#e8e8f0', fontSize: 12, fontWeight: '700', lineHeight: 16 },
  relatedYear: { color: isLightAppearance ? c.textSecondary : c.subText, fontSize: 11, marginTop: 4 },
  providerPillsRow: { paddingTop: 10, paddingLeft: 14, paddingRight: 14 },
  providerPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 14,
    paddingVertical: 10,
    marginRight: 10,
    borderRadius: 999,
    backgroundColor: isLightAppearance ? c.inputBg : (vividAmbient ? c.inputBg + '99' : c.inputBg),
    borderWidth: 1,
    borderColor: c.border,
  },
  providerPillLogo: { width: 22, height: 22, borderRadius: 11 },
  providerPillLogoFallback: {
    width: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: isLightAppearance ? c.cardBg : c.bg,
    borderWidth: 1,
    borderColor: c.border,
  },
  providerPillLogoText: { color: c.textPrimary, fontSize: 8, fontWeight: '800' },
  providerPillName: { color: isLightAppearance ? c.textPrimary : '#e8e8f0', fontSize: 13, fontWeight: '700' },
  episodeStillWrap: {
    position: 'relative',
    width: 120,
    height: 72,
    alignSelf: 'flex-start',
    overflow: 'hidden',
    borderTopLeftRadius: 12,
    borderBottomLeftRadius: 12,
    backgroundColor: isLightAppearance ? '#ffffff' : c.cardBg,
  },
  episodeStillBlur: { ...StyleSheet.absoluteFillObject },
  episodeStillShade: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(10,14,24,0.18)',
  },
  // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Centered layout variant ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬
    backdropWrapperCentered: { height: 585, position: 'relative' as const },
    backdropCentered: { width: '100%' as const, height: 585 },
  centeredMeta: {
    paddingHorizontal: 14, paddingTop: 8, paddingBottom: 8,
    alignItems: 'center' as const, marginTop: isLightAppearance ? -143 : -150,
  },
  glassCenteredMeta: {
    marginTop: 0,
    paddingTop: 0,
  },
  glassSynopsisBlock: {
    width: '100%',
    paddingHorizontal: 20,
    paddingTop: 12,
    marginTop: 0,
  },
  glassSynopsisText: {
    color: isLightAppearance ? c.textSecondary : '#f3f4f6',
    fontSize: 15,
    lineHeight: 23,
    textAlign: 'left',
  },
  glassPlayWrap: {
    paddingHorizontal: 18,
    marginTop: 14,
    marginBottom: 8,
    width: '100%',
  },
  glassSectionSpacing: {
    marginTop: 14,
  },
  glassCastSection: {
    marginTop: 12,
  },
  glassStreamsViewport: {
    minHeight: 150,
    borderRadius: 24,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: isLightAppearance ? 'rgba(255,255,255,0.58)' : 'rgba(255,255,255,0.14)',
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.44)' : 'rgba(8,10,14,0.34)',
  },
  glassCastCard: {
    width: 104,
  },
  glassCastPhoto: {
    width: 96,
    height: 132,
    borderRadius: 16,
  },
  centeredTitleLogoWrap: {
    width: '72%' as const,
    height: 80,
    marginBottom: 8,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOpacity: isLightAppearance ? 0.34 : 0.20,
    shadowRadius: 5,
    shadowOffset: { width: 0, height: 0 },
    elevation: isLightAppearance ? 3 : 0,
  },
  centeredTitleLogo: {
    width: '100%' as const,
    height: '100%' as const,
    shadowColor: '#000',
    shadowOpacity: isLightAppearance ? 0.28 : 0.16,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 0 },
  },
  centeredTitleBlock: {
    width: '72%' as const,
    height: 80,
    marginBottom: 8,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    overflow: 'hidden',
  },
  centeredTitle:   {
    color: isLightAppearance ? c.textPrimary : '#fff',
    fontSize: 20,
    fontWeight: '800',
    lineHeight: 26,
    marginBottom: 4,
    textAlign: 'center' as const,
    textShadowColor: isLightAppearance ? 'rgba(0,0,0,0.22)' : 'rgba(0,0,0,0.45)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 3,
  },
  centeredTitleLightFallback: {
    color: '#ffffff',
  },
  centeredTagline: { color: isLightAppearance ? c.textSecondary : c.subText, fontSize: 12, fontStyle: 'italic', marginBottom: 10, textAlign: 'center' as const },
  centeredPills:   { flexDirection: 'row' as const, flexWrap: 'wrap' as const, gap: 6, justifyContent: 'center' as const, width: '100%' as const },
  centeredActionsWrap: { paddingHorizontal: 14, gap: 10, marginBottom: 16, marginTop: 10 },
  centeredCombinedRowSection: { marginBottom: 14 },
  centeredCombinedRow: {
    flexDirection: 'row' as const, justifyContent: 'center' as const,
    gap: 8, flexWrap: 'nowrap' as const, alignItems: 'center' as const,
  },
  // Unified pill used for both tab chips AND icon buttons in centered mode
  centeredPill: {
    paddingHorizontal: 14, paddingVertical: 9, borderRadius: 20,
    backgroundColor: isLightAppearance ? c.cardBg : (vividAmbient ? c.inputBg + '99' : c.inputBg),
    borderWidth: 1,
    borderColor: c.border,
    alignItems: 'center' as const, justifyContent: 'center' as const,
  },
  centeredTabPill: {
    backgroundColor: isMonochromeDark ? c.cardBg : (isLightAppearance ? 'rgba(255,255,255,0.52)' : c.inputBg),
    borderColor: isMonochromeDark ? c.border : (isLightAppearance ? 'rgba(17,24,39,0.18)' : 'transparent' as const),
  },
  centeredTabPillActive: {
    backgroundColor: isMonochromeDark ? '#181818' : (isLightAppearance ? 'rgba(255,255,255,0.72)' : c.accent + '22'),
    borderColor: isMonochromeDark ? c.textPrimary : (isLightAppearance ? 'rgba(17,24,39,0.28)' : c.accent),
  },
  centeredTabPillDisabled: {
    opacity: 0.68,
  },
  centeredPillActive:        { backgroundColor: isLightAppearance ? c.cardBg : (vividAmbient ? c.inputBg + '99' : c.inputBg), borderColor: c.border },
  centeredPillWatchedActive: { backgroundColor: isLightAppearance ? c.cardBg : (vividAmbient ? c.inputBg + '99' : c.inputBg), borderColor: c.border },
  centeredPillText:          { color: isMonochromeDark ? c.textPrimary : (isLightAppearance ? c.textPrimary : c.subText), fontSize: 13, fontWeight: '700' as const },
  centeredPillTextActive:    { color: isMonochromeDark ? c.textPrimary : (isLightAppearance ? c.textPrimary : c.accentSoft), fontSize: 13, fontWeight: '700' as const },
  // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Streams tab ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬
  streamsList:     { marginTop: 4 },
  streamGroup:     { marginBottom: 20 },
  streamGroupLabel:{ color: isLightAppearance ? c.textPrimary : '#e8e8f0', fontSize: 13, fontWeight: '800', marginBottom: 8, letterSpacing: 0.2 },
  streamRow: {
    flexDirection: 'row', alignItems: 'center', gap: 10, padding: 12,
    backgroundColor: isMonochromeDark ? c.cardBg : (isLightAppearance ? c.cardBg : c.inputBg),
    borderRadius: 12, marginBottom: 8,
    borderWidth: 1,
    borderColor: isMonochromeDark ? c.border : (isLightAppearance ? c.inputBorder : c.border),
  },
  streamQualityBadge: {
    paddingHorizontal: 7, paddingVertical: 3, borderRadius: 6, minWidth: 38, alignItems: 'center',
    backgroundColor: isMonochromeDark ? c.cardBg : (isLightAppearance ? c.inputBorder : 'rgba(255,255,255,0.08)'),
    borderWidth: 1,
    borderColor: isMonochromeDark ? c.border : (isLightAppearance ? c.border : 'transparent'),
  },
  streamQualityText:  { fontSize: 10, fontWeight: '900', letterSpacing: 0.3, color: isMonochromeDark ? c.textPrimary : (isLightAppearance ? c.textPrimary : c.accentSoft) },
  streamMeta:         { flex: 1 },
  streamName:         { color: isLightAppearance ? c.textPrimary : '#e8e8f0', fontSize: 13, fontWeight: '700', marginBottom: 2 },
  streamTitle:        { color: isLightAppearance ? c.textSecondary : c.subText, fontSize: 11, lineHeight: 15 },
  streamBadges:       { flexDirection: 'row', gap: 4, marginTop: 4, flexWrap: 'wrap' },
  streamSizeBadge:    { paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
  streamCachedBadge:  { paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, backgroundColor: isMonochromeDark ? c.cardBg : '#00e67622', borderWidth: isMonochromeDark ? 1 : 0, borderColor: isMonochromeDark ? c.border : 'transparent' },
  streamCachedText:   { color: isMonochromeDark ? c.textPrimary : '#00e676', fontSize: 10, fontWeight: '700' },
  streamEmpty:        { alignItems: 'center', padding: 32 },
  streamEmptyText:    { color: isLightAppearance ? c.textSecondary : c.subText, fontSize: 14, textAlign: 'center', lineHeight: 21 },
  streamResolvingModal: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.7)', justifyContent: 'center', alignItems: 'center', padding: 32,
  },
  streamResolvingCard:  {
    backgroundColor: c.cardBg, borderRadius: 16, padding: 24, alignItems: 'center', width: '100%',
  },
  // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ End streams ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬
  detailContentBody: {
    flex: 1,
    position: 'relative',
    overflow: 'visible',
    backgroundColor: 'transparent',
    zIndex: 1,
  },
  detailContentBodyGlass: {
    backgroundColor: 'transparent',
  },
  detailContentTopTint: { position: 'absolute', left: 0, right: 0, zIndex: 0 },
  detailContentBottomTint: { position: 'absolute', left: 0, right: 0, zIndex: 0 },
  whereSection:    { marginTop: 24 },
  whereDivider:    { height: 1, backgroundColor: c.border, marginBottom: 20 },
  featuredSectionHeading: { color: isLightAppearance ? c.textPrimary : '#fff', fontSize: 24, fontWeight: '800', marginBottom: 16, letterSpacing: 0.3 },
  whereHeading:    { color: isLightAppearance ? c.textPrimary : '#fff', fontSize: 15, fontWeight: '800', marginBottom: 16, letterSpacing: 0.3 },
  whereGroup:      { marginBottom: 20 },
  whereGroupLabel: { color: isLightAppearance ? c.textPrimary : c.mutedText, fontSize: 10, fontWeight: '700', letterSpacing: 1, textTransform: 'uppercase', marginBottom: 10 },
  providerRow:     { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  providerItem:    { alignItems: 'center', width: 64 },
  providerLogo:    { width: 52, height: 52, borderRadius: 26, backgroundColor: '#f0f0f0', borderWidth: 1, borderColor: c.inputBorder },
  providerLogoFallback: { width: 52, height: 52, borderRadius: 26, backgroundColor: c.inputBg, borderWidth: 1, borderColor: c.inputBorder, justifyContent: 'center', alignItems: 'center', padding: 4 },
  providerLogoText: { color: c.accentSoft, fontSize: 8, fontWeight: '700', textAlign: 'center' },
  providerName:     { color: isLightAppearance ? c.textSecondary : c.subText, fontSize: 9, marginTop: 5, textAlign: 'center' },
  debridPlayBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 14,
    borderRadius: 12,
    backgroundColor: isLightMonochrome ? c.cardBg : (isLightAppearance ? c.accent : c.accent + '18'),
    borderWidth: 1,
    borderColor: isLightMonochrome ? c.border : (isLightAppearance ? c.accent + '55' : c.accent + '55'),
    opacity: 1,
  },
  debridPlayBtnDisabled: {
    opacity: 0.45,
  },
  debridPlayTitle: {
    color: isLightMonochrome ? c.textPrimary : (isLightAppearance ? c.buttonText : c.accentSoft),
    fontSize: 13,
    fontWeight: '700',
  },
  debridPlaySubtitle: {
    color: isLightMonochrome ? c.textSecondary : (isLightAppearance ? 'rgba(255,255,255,0.88)' : c.subText),
    fontSize: 11,
    marginTop: 2,
  },
  debridPlayIcon: {
    color: isLightAppearance ? c.buttonText : c.accent,
  },
});
};

// Module-level cache so remounting the screen doesn't re-fetch data that was
// already loaded. Keyed by `${type}/${movieId}`. Cleared on error so a stale
// entry doesn't permanently hide a real failure.
const detailCache = new Map<string, any>();

interface TraktCommentItem {
  id: number;
  author: string;
  avatar: string | null;
  comment: string;
  likes: number;
  replies: number;
  userRating: number | null;
  spoiler: boolean;
  createdAt: string | null;
}

function buildRoutePreviewMedia(params: any, fallbackType: 'movie' | 'tv') {
  if (!params) return null;

  const title = typeof params.title === 'string' ? params.title : null;
  const poster = typeof params.poster === 'string' ? params.poster : null;
  const backdrop = typeof params.backdrop === 'string' ? params.backdrop : null;
  const synopsis = typeof params.synopsis === 'string' ? params.synopsis : null;
  const year = typeof params.year === 'number' || typeof params.year === 'string' ? params.year : null;
  const imdbId = typeof params.imdbId === 'string' ? params.imdbId : null;
  const rating = typeof params.rating === 'number' ? params.rating : null;
  const fallbackRatings = buildFallbackExternalRatings(rating);

  if (!title && !poster && !backdrop && !synopsis && !year && !imdbId && rating == null) return null;

  return {
    id: params.movieId,
    type: params.type ?? fallbackType,
    title: title ?? ' ',
    poster,
    backdrop,
    description: synopsis,
    year,
    imdbId,
    rating,
    externalRatings: fallbackRatings,
    externalRatingsMode: fallbackRatings.length > 0 ? 'fallback' : 'none',
  };
}

export const MediaDetailScreen = ({ route, navigation }: any) => {
  const { movieId, type = 'movie', startFromBeginning = false, imdbId: routeImdbId = null } = route.params || {};
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const { t } = useLanguage();
  const mdbListSettings = useMdbListSettings();
  const { uiStyle } = useUIStyle();
  const { showStreamsList, vividAmbientEnabled, heroTrailerAutoplayEnabled } = useDisplaySettings();
  const { isForeground } = useAppLifecycle();
  const isLightAppearance = resolvedAppearance === 'light';
  const isLightMonochrome = isLightAppearance && theme.id === 'monochrome';
  const isMonochromeDark = !isLightAppearance && theme.id === 'monochrome';
  const detailBodyBg = isLightAppearance && uiStyle === 'centered' ? 'transparent' : (isLightAppearance ? colors.bgMid : colors.bg);
  const detailContentTint = isLightAppearance ? 'rgba(255,255,255,0.9)' : 'rgba(5,6,10,0.96)';
  const [heroBackdropHeight, setHeroBackdropHeight] = useState(uiStyle === 'centered' ? 585 : 465);
  const glassOverlayHeight = 150;
  const glassOverlayOffset = Math.max(0, heroBackdropHeight - glassOverlayHeight);
  const detailScrollY = useRef(new Animated.Value(0)).current;
  const ratingsRowAnimation = useRef(new Animated.Value(0)).current;
  const detailHeroLiftDelay = 56;
  const detailHeroScale = detailScrollY.interpolate({
    inputRange: [-100, 0, detailHeroLiftDelay, detailHeroLiftDelay + heroBackdropHeight * 0.6],
    outputRange: [1.04, 1.0, 1.0, 1.08],
    extrapolate: 'clamp',
  });
  const detailHeroTranslateY = detailScrollY.interpolate({
    inputRange: [-100, 0, detailHeroLiftDelay, detailHeroLiftDelay + heroBackdropHeight],
    outputRange: [-6, 0, 0, 26],
    extrapolate: 'clamp',
  });
  const detailHeroLiftDistance = heroBackdropHeight + 180;
  const detailHeroLiftTranslateY = detailScrollY.interpolate({
    inputRange: [0, detailHeroLiftDelay, detailHeroLiftDelay + detailHeroLiftDistance],
    outputRange: [0, 0, -detailHeroLiftDistance],
    extrapolate: 'clamp',
  });
  const ratingsRowTranslateY = ratingsRowAnimation.interpolate({
    inputRange: [0, 1],
    outputRange: [-12, 0],
  });
  const styles = useMemo(() => makeStyles(colors, isLightAppearance, vividAmbientEnabled), [colors, isLightAppearance, vividAmbientEnabled]);
  const detailPrimaryText = isLightAppearance ? colors.textPrimary : '#111827';
  const detailSecondaryText = isLightAppearance ? colors.textSecondary : colors.subText;
  const detailMutedIcon = isLightAppearance ? colors.textPrimary : '#e8e8f0';
  const { isConnected, continueWatching, watchlist: traktWatchlist, refreshWatchlist, refreshContinueWatching } = useTrakt();
  const {
    isMovieWatched,
    isEpisodeWatched,
    toggleMovieWatched,
    toggleEpisodeWatched,
    markSeasonWatched,
    unmarkSeasonWatched,
    markAllEpisodesWatched,
  } = useWatched();
  const {
    fetchStreamsProgressive,
    addons,
    isLoading: addonsLoading,
    ultraEntitled,
    ultraBoostEnabled,
    refreshAddons,
  } = useAddons();
  const { accounts: debridAccounts, resolveStream } = useDebrid();
  const { getProgress, clearProgress, clearProgressIndexEntry } = useWatchProgress();
  const storageOwnerId = getProfileStorageOwnerId(user?.uid, activeProfile?.id);
  const legacyOwnerId = user?.uid ?? null;
  const watchlistKey = storageOwnerId;

  const insets = useSafeAreaInsets();
  const cacheKey = `${type}/${movieId}`;
  const routePreviewMedia = useMemo(() => buildRoutePreviewMedia(route.params, type), [route.params, type]);
  const [media, setMedia] = useState<any>(() => {
    const initial = detailCache.get(cacheKey) ?? routePreviewMedia ?? null;
    return initial ? { ...initial, externalRatingsMode: initial.externalRatingsMode ?? 'none' } : null;
  });
  const [loading, setLoading] = useState(() => !detailCache.has(cacheKey) && !routePreviewMedia);
  const [activeTab, setActiveTab] = useState<Tab>('about');
  const [mdbListStatusMessage, setMdbListStatusMessage] = useState<string | null>(null);
  const [mdbListLoading, setMdbListLoading] = useState(false);
  const [showSeasonsPanel, setShowSeasonsPanel] = useState(false);
  const [selectedSeason, setSelectedSeason] = useState(1);
  const [episodes, setEpisodes] = useState<any[]>([]);
  const [episodesLoading, setEpisodesLoading] = useState(false);
  const [trailerVisible, setTrailerVisible] = useState(false);
  const [heroTrailerReady, setHeroTrailerReady] = useState(false);
  const [heroTrailerFinished, setHeroTrailerFinished] = useState(false);
  const [heroTrailerMuted, setHeroTrailerMuted] = useState(true);
  const [isDescriptionExpanded, setIsDescriptionExpanded] = useState(false);
  const [showDescriptionMore, setShowDescriptionMore] = useState(false);
  const [inWatchlist, setInWatchlist] = useState(false);
  const [vimeoKey, setVimeoKey] = useState<string | null>(() => detailCache.get(cacheKey)?.vimeoKey ?? null);
  useEffect(() => {
    setHeroBackdropHeight(uiStyle === 'centered' ? 465 : 345);
  }, [uiStyle]);

  useEffect(() => {
    setIsDescriptionExpanded(false);
    setShowDescriptionMore(false);
  }, [cacheKey, uiStyle]);

  useEffect(() => {
    const cached = detailCache.get(cacheKey);
    if (cached) {
      const cachedFallbackRatings = Array.isArray(cached.externalRatings) && cached.externalRatings.length > 0
        ? cached.externalRatings
        : buildFallbackExternalRatings(cached.rating);
      setMedia({ ...cached, imdbId: cached.imdbId ?? routeImdbId ?? null, externalRatings: cached.externalRatings ?? cachedFallbackRatings, externalRatingsMode: cached.externalRatingsMode ?? (cachedFallbackRatings.length > 0 ? 'fallback' : 'none') });
      setVimeoKey(cached.vimeoKey ?? null);
      setLoading(false);
      setMdbListLoading(false);
      return;
    }

    setMedia(routePreviewMedia ?? null);
    setVimeoKey(null);
    setLoading(!routePreviewMedia);
    setMdbListLoading(false);
  }, [cacheKey, routeImdbId, routePreviewMedia]);

  // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Sheet state ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬
  // Episode long-press action sheet
  const [epSheetEp, setEpSheetEp] = useState<any>(null);
  // "Mark series as watched" confirm
  const [seriesWatchedConfirm, setSeriesWatchedConfirm] = useState(false);
  // Play button action sheet for watched items
  const [playChoiceVisible, setPlayChoiceVisible] = useState(false);
  // "Debrid required" info sheet (for movies in streams tab)
  const [debridSheet, setDebridSheet] = useState(false);

  // Streams tab state
  const [streams, setStreams]                 = useState<AddonStream[]>([]);
  const [streamsLoading, setStreamsLoading]   = useState(false);
  const [streamsPending, setStreamsPending]   = useState(0);
  const [streamsFetchStarted, setStreamsFetchStarted] = useState(false);
  const streamsAbortRef  = useRef<AbortController | null>(null);
  const detailsAbortRef = useRef<AbortController | null>(null);
  const commentsAbortRef = useRef<AbortController | null>(null);
  const seasonsAbortRef = useRef<AbortController | null>(null);
  const streamsRequestIdRef = useRef(0);
  const scrollViewRef    = useRef<any>(null);
  const blurTargetRef = useRef<View | null>(null);
  // Guard: ref (not state) so setting it true does NOT re-render the component
  // and therefore does NOT re-run the effect and abort the in-flight controller.
  const streamsFetchedRef = useRef(false);
  // Increment this to manually re-trigger the fetch effect (focus return, login).
  const [streamsFetchKey, setStreamsFetchKey] = useState(0);
  // Lifted from StreamsTab so the filter bar can live outside the ScrollView content
  const addonNames = useMemo(
    () => [...new Set(streams.map(s => s.addonName))],
    [streams],
  );
  const [selectedAddon, setSelectedAddon] = useState<string>('all');
  // Pick a stable random backdrop from the pool (re-randomised when media changes)
  const detailHeroUri = useMemo(() => {
    const pool: string[] = media?.backdrops?.length > 0 ? media.backdrops : media?.backdrop ? [media.backdrop] : [];
    if (pool.length === 0) return media?.poster ?? null;
    return pool[0]; // Use the primary backdrop rather than a random one for visual stability
  }, [media?.id]);  // keyed on id so it's stable for the same item but re-rolls on navigation

  const ambientBackdropUri = detailHeroUri ?? media?.backdrop ?? media?.poster ?? null;
  const displayedExternalRatings = useMemo(() => {
    if (Array.isArray(media?.externalRatings) && media.externalRatings.length > 0) return media.externalRatings;
    return buildFallbackExternalRatings(media?.rating);
  }, [media?.externalRatings, media?.rating]);
  const displayedExternalRatingsMode = (displayedExternalRatings.length > 0 && media?.externalRatingsMode === 'none') ? 'fallback' : (media?.externalRatingsMode ?? (displayedExternalRatings.length > 0 ? 'fallback' : 'none'));
  const shouldShowExternalRatings = displayedExternalRatings.length > 0;
  const fallbackGenreLabel = useMemo(() => {
    const firstGenre = Array.isArray(media?.genreNames) ? media.genreNames.find((value: unknown) => typeof value === 'string' && value.trim().length > 0) : null;
    return typeof firstGenre === 'string' ? firstGenre.trim() : null;
  }, [media?.genreNames]);
  const heroTrailerKey = typeof media?.trailerKey === 'string' && media.trailerKey.length > 0 ? media.trailerKey : null;
  const isPosterOnlyHero = !media?.backdrop && !!media?.poster;
  const isMovieDetail = type !== 'tv';
  const useCompactDetailLayout = uiStyle === 'centered' || uiStyle === 'glass';
  const useGlassDetailLayout = uiStyle === 'glass';
  const canAutoplayHeroTrailer = Boolean(heroTrailerAutoplayEnabled && heroTrailerKey && !heroTrailerFinished && !trailerVisible && !isPosterOnlyHero && !useGlassDetailLayout);
  const shouldParallaxHero = true;
  const detailContentOverlap = useCompactDetailLayout ? 56 : 40;
  const detailContentStartOffset = 55;
  const detailPageContentTop = useGlassDetailLayout ? 0 : Math.max(0, heroBackdropHeight + detailContentStartOffset - detailContentOverlap);

  useEffect(() => {
    setHeroTrailerReady(false);
    setHeroTrailerFinished(false);
    setHeroTrailerMuted(true);
  }, [cacheKey, heroTrailerKey]);

  const streamCapableAddons = useMemo(
    () => getStreamCapableAddonsForType(addons, type === 'tv' ? 'series' : 'movie'),
    [addons, type],
  );
  const ultraActive = ultraEntitled && ultraBoostEnabled;
  const hasStreamSources = streamCapableAddons.length > 0 || ultraActive;
  const sourceCount = streamCapableAddons.length + (ultraActive ? 1 : 0);
  const shouldPreloadStreams = isMovieDetail && !!media && !addonsLoading && hasStreamSources;
  // Always start as false ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â the reset effect below will set it to true once
  // we know whether a preload is actually needed (after media + user are ready).
  const [streamsLoadComplete, setStreamsLoadComplete] = useState(false);
  const streamsFetchingForPlayback = isMovieDetail
    && !!media
    && hasStreamSources
    && (!streamsFetchedRef.current || streamsLoading || !streamsLoadComplete);
  const streamsTabLocked = isMovieDetail
    && !!media
    && streams.length === 0
    && (addonsLoading || streamsFetchingForPlayback);
  const isUnreleased = useMemo(() => {
    const raw = media?.releaseDate ?? media?.firstAirDate ?? null;
    if (!raw) return false;
    const release = new Date(`${raw}T00:00:00`);
    return release > new Date();
  }, [media?.releaseDate, media?.firstAirDate]);

  // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Local progress (read fresh from Storage on every screen focus) ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬
  const [localProgress, setLocalProgress] = useState<{ positionSec: number; durationSec: number } | null>(null);
  const [watchlistRemovalIds, setWatchlistRemovalIds] = useState<string[]>([]);
  const [traktComments, setTraktComments] = useState<TraktCommentItem[]>([]);

  useEffect(() => {
    if (!ambientBackdropUri) return;
    void Image.prefetch(ambientBackdropUri, 'memory-disk').catch(() => {});
  }, [ambientBackdropUri]);

  useFocusEffect(
    useCallback(() => {
      const key = movieProgressKey(movieId);
      const liveEntry = getProgress(key);
      if (liveEntry && liveEntry.durationSec > 0) {
        setLocalProgress({ positionSec: liveEntry.positionSec, durationSec: liveEntry.durationSec });
      }
      // Each movie's progress is stored per-user ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â read the user-scoped file
      Storage.getItem(progressFileStorageKey(storageOwnerId, key)).then(raw => {
        if (!raw) { setLocalProgress(null); return; }
        try {
          const entry = JSON.parse(raw);
          setLocalProgress(
            entry && entry.durationSec > 0
              ? { positionSec: entry.positionSec, durationSec: entry.durationSec }
              : null,
          );
        } catch {
          setLocalProgress(null);
        }
      });
      // Refresh Trakt continue-watching so it reflects any scrobbling that happened in Player
      if (isConnected) refreshContinueWatching();
      ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP);
    }, [movieId, getProgress, isConnected, refreshContinueWatching, storageOwnerId]),
  );

  useEffect(() => {
    if (!user) {
      setWatchlistRemovalIds([]);
      return;
    }
    let cancelled = false;
    readWatchlistRemovalIds(storageOwnerId, legacyOwnerId).then(ids => {
      if (!cancelled) setWatchlistRemovalIds(ids);
    }).catch(() => {});
    return () => { cancelled = true; };
  }, [legacyOwnerId, storageOwnerId, user?.uid]);

  // Find Trakt progress for this item from context (already loaded)
  const traktProgress = useMemo<number | null>(() => {
    if (!isConnected) return null;
    const found = continueWatching.find(item => Number(item.tmdbId) === Number(movieId));
    return typeof found?.progress === 'number' ? found.progress : null;
  }, [continueWatching, movieId, isConnected]);

  const liveMovieProgress = getProgress(movieProgressKey(movieId));

  const effectiveLocalProgress = liveMovieProgress && liveMovieProgress.durationSec > 0
    ? { positionSec: liveMovieProgress.positionSec, durationSec: liveMovieProgress.durationSec }
    : localProgress;

  const localProgressPct = effectiveLocalProgress
    ? (effectiveLocalProgress.positionSec / effectiveLocalProgress.durationSec) * 100
    : null;

  /**
   * The progress percentage to display for movies in the primary CTA.
   * Prefers fresh local device progress. Falls back to Trakt.
   * Returns null if no progress or if content is complete (>= 95 %).
   */
  const movieDisplayProgress: number | null = (() => {
    if (localProgressPct != null && localProgressPct > 0 && localProgressPct < 95) return localProgressPct;
    if (traktProgress != null && traktProgress > 0 && traktProgress < 95) return traktProgress;
    return null;
  })();

  /**
   * Seconds to seek to when resuming playback.
   * Local: exact stored position.
   * Trakt: approximate from progress% ÃƒÆ’Ã¢â‚¬â€ runtime.
   */
  const resumeFromSec: number | undefined = (() => {
    if (effectiveLocalProgress && localProgressPct != null && localProgressPct > 0 && localProgressPct < 95) {
      return effectiveLocalProgress.positionSec;
    }
    if (traktProgress != null && traktProgress > 0 && traktProgress < 95) {
      const runtimeSec = media?.runtime ? media.runtime * 60 : 0;
      if (runtimeSec > 0) return Math.round((traktProgress / 100) * runtimeSec);
    }
    return undefined;
  })();

  const watched = useMemo(() => {
    if (!media) return false;
    if (type === 'tv') {
      return (media.seasons || []).length > 0 &&
        (media.seasons || []).every((s: any) =>
          Array.from({ length: s.episode_count }, (_, i) => i + 1)
            .every(ep => isEpisodeWatched(Number(movieId), s.season_number, ep)));
    }
    return isMovieWatched(Number(movieId));
  }, [isEpisodeWatched, isMovieWatched, media, movieId, type]);

  const getEpisodeProgressPercent = useCallback((seasonNumber: number, episode: any): number | null => {
    const epNumber = Number(episode?.episode_number ?? 0);
    if (!Number.isFinite(epNumber) || epNumber <= 0) return null;
    if (isEpisodeWatched(Number(movieId), seasonNumber, epNumber)) return 100;

    const entry = getProgress(episodeProgressKey(Number(movieId), seasonNumber, epNumber));
    if (entry) {
      const fallbackRuntimeSec = getEpisodeRuntimeSeconds(episode, media?.runtime ?? null);
      const durationSec = entry.durationSec > 0 ? entry.durationSec : fallbackRuntimeSec;
      const pct = clampProgressPercent(entry.positionSec, durationSec);
      if (pct != null) return pct >= 95 ? 100 : pct;
    }

    return null;
  }, [getProgress, isEpisodeWatched, media?.runtime, movieId]);

  const seriesDisplayProgress = useMemo<number | null>(() => {
    if (type !== 'tv' || !media?.seasons?.length) return null;

    const episodeRuntimeSec = getEpisodeRuntimeSeconds(
      null,
      media.runtime ?? episodes.find((ep: any) => Number(ep.runtime) > 0)?.runtime ?? null,
    );
    if (episodeRuntimeSec <= 0) return null;

    let totalDurationSec = 0;
    let watchedDurationSec = 0;

    for (const seasonEntry of media.seasons || []) {
      const seasonNumber = Number(seasonEntry.season_number);
      const episodeCount = Math.max(0, Number(seasonEntry.episode_count ?? 0));
      for (let ep = 1; ep <= episodeCount; ep += 1) {
        totalDurationSec += episodeRuntimeSec;
        if (isEpisodeWatched(Number(movieId), seasonNumber, ep)) {
          watchedDurationSec += episodeRuntimeSec;
          continue;
        }
        const progressKey = episodeProgressKey(Number(movieId), seasonNumber, ep);
        const progress = getProgress(progressKey);
        const progressPct = progress ? clampProgressPercent(progress.positionSec, progress.durationSec) : null;
        if (progressPct != null) {
          watchedDurationSec += Math.min(episodeRuntimeSec, Math.round((progressPct / 100) * episodeRuntimeSec));
        }
      }
    }

    if (totalDurationSec <= 0 || watchedDurationSec <= 0) return null;
    const pct = (watchedDurationSec / totalDurationSec) * 100;
    return pct > 0 && pct < 100 ? pct : null;
  }, [episodes, getProgress, isEpisodeWatched, media, movieId, type]);

  const primaryPlayProgress = type === 'tv' ? seriesDisplayProgress : movieDisplayProgress;
  const movieContinueLabel = useMemo(() => {
    if (type === 'tv' || (uiStyle !== 'centered' && uiStyle !== 'glass')) return null;
    const formatted = formatContinueTime(resumeFromSec);
    return formatted ? `${t('media_continue')} from ${formatted}` : null;
  }, [resumeFromSec, t, type, uiStyle]);

  const navigateToMoviePlayer = useCallback((startFromBeginning: boolean) => {
    if (!media) return;
    const prefetchedStreams = streams.length > 0 ? streams : undefined;
    navigation.navigate('Player', {
      movieId:  media.id,
      imdbId:   media.imdbId ?? undefined,
      type,
      title:    media.title,
      year:     media.year,
      synopsis: media.description ?? undefined,
      titleLogo: media.titleLogo,
      backdrop: detailHeroUri ?? media.backdrop,
      poster:   media.poster,
      resumeFrom: startFromBeginning ? 0 : resumeFromSec,
      forceStartFromBeginning: startFromBeginning,
      progressKey: movieProgressKey(Number(movieId)),
      runtimeSec:  media.runtime ? media.runtime * 60 : undefined,
      resolveOnMount: true,
      resolverMovieId: media.id,
      resolverImdbId: media.imdbId ?? undefined,
      resolverType: type,
      sourceStreams: prefetchedStreams,
      returnToPlayerParams: {
        movieId: media.id,
        imdbId: media.imdbId ?? undefined,
        type,
        title: media.title,
        year: media.year,
        synopsis: media.description ?? undefined,
        titleLogo: media.titleLogo,
        backdrop: detailHeroUri ?? media.backdrop,
        poster: media.poster,
        progressKey: movieProgressKey(Number(movieId)),
      },
    });
  }, [detailHeroUri, media, movieId, navigation, resumeFromSec, streams, type]);

  const handlePrimaryPlayPress = useCallback(() => {
    if (watched) {
      setPlayChoiceVisible(true);
      return;
    }
    if (type === 'tv') {
      if (useGlassDetailLayout) {
        setShowSeasonsPanel(true);
      } else {
        setActiveTab('seasons');
      }
      scrollViewRef.current?.scrollTo({ y: 0, animated: true });
      return;
    }
    navigateToMoviePlayer(false);
  }, [navigation, navigateToMoviePlayer, type, watched, useGlassDetailLayout]);

  const handleContinuePlay = useCallback(() => {
    setPlayChoiceVisible(false);
    if (type === 'tv') {
      if (useGlassDetailLayout) {
        setShowSeasonsPanel(true);
      } else {
        setActiveTab('seasons');
      }
      scrollViewRef.current?.scrollTo({ y: 0, animated: true });
      return;
    }
    navigateToMoviePlayer(false);
  }, [navigateToMoviePlayer, type, useGlassDetailLayout]);

  const handleRewatchPlay = useCallback(() => {
    setPlayChoiceVisible(false);
    if (type === 'tv') {
      if (useGlassDetailLayout) {
        setShowSeasonsPanel(true);
      } else {
        setActiveTab('seasons');
      }
      setSelectedSeason(1);
      scrollViewRef.current?.scrollTo({ y: 0, animated: true });
      return;
    }
    navigateToMoviePlayer(true);
  }, [navigateToMoviePlayer, type, useGlassDetailLayout]);

  useEffect(() => {
    if (!startFromBeginning || !media || type !== 'tv') return;
    if (useGlassDetailLayout) {
      setShowSeasonsPanel(true);
    } else {
      setActiveTab('seasons');
    }
    setSelectedSeason(1);
    scrollViewRef.current?.scrollTo({ y: 0, animated: true });
  }, [media, startFromBeginning, type]);

  const playBtnLoadingDetails = useMemo(() => {
    const details: string[] = [];
    const releaseDate = formatReleaseDate(media?.releaseDate ?? media?.firstAirDate ?? null);
    const runtime = formatRuntimeButton(media?.runtime ?? null);
    const tagline = truncateButtonText(media?.tagline ?? null);
    if (releaseDate) details.push(`Released: ${releaseDate}`);
    if (runtime) details.push(`Runtime: ${runtime}`);
    if (tagline) details.push(tagline);
    return details;
  }, [media?.releaseDate, media?.firstAirDate, media?.runtime, media?.tagline]);
  const [playBtnLoadingDetailIndex, setPlayBtnLoadingDetailIndex] = useState(0);
  const STREAM_LOADING_PHRASES = [
    'Finding best stream...',
    'Checking sources...',
    'Scanning addons...',
    'Almost ready...',
  ];
  const activePlayBtnLoadingDetail = streamsTabLocked
    ? (playBtnLoadingDetails.length > 0
        ? playBtnLoadingDetails[playBtnLoadingDetailIndex % playBtnLoadingDetails.length]
        : STREAM_LOADING_PHRASES[playBtnLoadingDetailIndex % STREAM_LOADING_PHRASES.length])
    : null;
  const playButtonLocked = isMovieDetail && streams.length === 0 && streamsTabLocked;
  const showStreamsTab = type !== 'tv' && !isUnreleased && showStreamsList;
  const streamsTabLoadingIndicator = showStreamsTab
    && streams.length === 0
    && (streamsTabLocked || streamsLoading || streamsPending > 0 || addonsLoading);
  const glassStreamsLoading = showStreamsTab
    && streams.length === 0
    && hasStreamSources
    && (streamsTabLocked || streamsLoading || streamsPending > 0 || addonsLoading || !streamsFetchStarted || !streamsLoadComplete);
  const glassStreamsSettledEmpty = showStreamsTab
    && streams.length === 0
    && hasStreamSources
    && streamsFetchStarted
    && streamsLoadComplete
    && !streamsLoading
    && streamsPending === 0
    && !streamsTabLocked
    && !addonsLoading;
  const noStreamsFound = isMovieDetail && glassStreamsSettledEmpty;
  const primaryPlayLabel = playButtonLocked
    ? 'Loading streams'
    : noStreamsFound
    ? '(No Streams)'
    : isUnreleased
    ? 'Unreleased'
    : type === 'tv'
      ? primaryPlayProgress != null && primaryPlayProgress > 0
        ? 'Continue Series'
        : 'Select Episode'
      : movieDisplayProgress != null && movieDisplayProgress > 0
        ? (movieContinueLabel ?? t('media_continue'))
        : t('media_play');
  const primaryActionPalette = getPrimaryActionPalette(colors, theme.id, isLightAppearance, useGlassDetailLayout ? 'glass' : 'solid');

  useEffect(() => {
    if (playBtnLoadingDetails.length === 0) {
      setPlayBtnLoadingDetailIndex(0);
      return;
    }
    setPlayBtnLoadingDetailIndex(Math.floor(Math.random() * playBtnLoadingDetails.length));
  }, [movieId, playBtnLoadingDetails]);

  useEffect(() => {
    const hasMdblistRatings = displayedExternalRatingsMode === 'mdblist' && displayedExternalRatings.length > 0;
    if (!shouldShowExternalRatings) {
      ratingsRowAnimation.setValue(0);
      return;
    }
    if (!hasMdblistRatings || mdbListLoading) {
      ratingsRowAnimation.setValue(1);
      return;
    }
    Animated.timing(ratingsRowAnimation, {
      toValue: 1,
      duration: 240,
      useNativeDriver: true,
    }).start();
  }, [displayedExternalRatings.length, displayedExternalRatingsMode, media?.id, mdbListLoading, ratingsRowAnimation, shouldShowExternalRatings]);

  useEffect(() => {
    if (!media) return;

    const imdbId = [
      media.imdbId,
      routeImdbId,
      typeof media.id === 'string' ? media.id : null,
      typeof movieId === 'string' ? movieId : null,
    ].map(value => typeof value === 'string' ? value.match(/tt\d+/i)?.[0] ?? null : null).find(Boolean) ?? null;
    const enabledProviders = getEnabledMdbListProviders(mdbListSettings);
    const hasApiKey = mdbListSettings.apiKey.trim().length > 0;
    const fallbackRatings = buildFallbackExternalRatings(media.rating);

    if (!mdbListSettings.enabled) {
      setMdbListLoading(false);
      setMdbListStatusMessage(null);
      setMedia((current: any) => current ? { ...current, externalRatings: fallbackRatings, externalRatingsMode: fallbackRatings.length > 0 ? 'fallback' : 'none' } : current);
      return;
    }

    if (!hasApiKey) {
      setMdbListLoading(false);
      setMdbListStatusMessage(null);
      setMedia((current: any) => current ? { ...current, externalRatings: fallbackRatings, externalRatingsMode: fallbackRatings.length > 0 ? 'fallback' : 'none' } : current);
      return;
    }

    if (enabledProviders.length === 0) {
      setMdbListLoading(false);
      setMdbListStatusMessage(null);
      setMedia((current: any) => current ? { ...current, externalRatings: fallbackRatings, externalRatingsMode: fallbackRatings.length > 0 ? 'fallback' : 'none' } : current);
      return;
    }

    if (!shouldFetchMdbListRatings(imdbId, mdbListSettings)) {
      setMdbListLoading(false);
      setMdbListStatusMessage(null);
      setMedia((current: any) => current ? { ...current, externalRatings: fallbackRatings, externalRatingsMode: fallbackRatings.length > 0 ? 'fallback' : 'none' } : current);
      return;
    }

    if (loading) return;

    setMdbListLoading(true);

    let isActive = true;
    const detailId = String(media.id ?? movieId);
    const currentType = type === 'movie' ? 'movie' : 'tv';
    setMdbListStatusMessage(null);

    const idleHandle = runIdle(() => {
      void (async () => {
        try {
          const ratings = await fetchMdbListRatings(imdbId, currentType, mdbListSettings);
          const resolvedRatings = ratings.length > 0 ? ratings : fallbackRatings;
          const nextRatingsMode = ratings.length > 0 ? 'mdblist' : (fallbackRatings.length > 0 ? 'fallback' : 'none');
          const cachedCurrent = detailCache.get(cacheKey);
          if (cachedCurrent) {
            detailCache.set(cacheKey, {
              ...cachedCurrent,
              imdbId: cachedCurrent.imdbId ?? imdbId,
              externalRatings: resolvedRatings,
              externalRatingsMode: nextRatingsMode,
            });
          }
          if (!isActive) return;
          setMedia((current: any) => {
            if (!current || String(current.id ?? '') !== detailId) return current;
            return {
              ...current,
              imdbId: current.imdbId ?? imdbId,
              externalRatings: resolvedRatings,
              externalRatingsMode: nextRatingsMode,
            };
          });
          setMdbListLoading(false);
          setMdbListStatusMessage(null);
        } catch (error) {
          const cachedCurrent = detailCache.get(cacheKey);
          const cachedRatings = Array.isArray(cachedCurrent?.externalRatings) && cachedCurrent.externalRatings.length > 0
            ? cachedCurrent.externalRatings
            : Array.isArray(media.externalRatings) && media.externalRatings.length > 0
              ? media.externalRatings
              : fallbackRatings;
          const nextRatingsMode = Array.isArray(cachedCurrent?.externalRatings) && cachedCurrent.externalRatings.length > 0
            ? (cachedCurrent.externalRatingsMode ?? 'mdblist')
            : cachedRatings.length > 0
              ? (cachedRatings === fallbackRatings ? 'fallback' : (media.externalRatingsMode ?? 'mdblist'))
              : 'none';
          if (cachedCurrent) {
            detailCache.set(cacheKey, {
              ...cachedCurrent,
              externalRatings: cachedRatings,
              externalRatingsMode: nextRatingsMode,
            });
          }
          if (!isActive) return;
          setMedia((current: any) => {
            if (!current || String(current.id ?? '') !== detailId) return current;
            return { ...current, externalRatings: cachedRatings, externalRatingsMode: nextRatingsMode };
          });
          setMdbListLoading(false);
          setMdbListStatusMessage(null);
        }
      })();
    }, { timeoutMs: 250 });

    return () => {
      isActive = false;
      idleHandle.cancel();
      setMdbListLoading(false);
    };
  }, [
    media?.id,
    media?.imdbId,
    movieId,
    routeImdbId,
    type,
    mdbListSettings.enabled,
    mdbListSettings.apiKey,
    mdbListSettings.useImdb,
    mdbListSettings.useTmdb,
    mdbListSettings.useTomatoes,
    mdbListSettings.useMetacritic,
    mdbListSettings.useTrakt,
    mdbListSettings.useLetterboxd,
    mdbListSettings.useAudience,
    loading,
  ]);


  useEffect(() => {
    if (detailCache.has(cacheKey)) return; // already have data, skip fetch
    const controller = new AbortController();
    detailsAbortRef.current?.abort();
    detailsAbortRef.current = controller;
    const fetchDetails = async () => {
        try {
          const res = await tmdbFetch(`/tmdb/details/${type}/${movieId}`, { signal: controller.signal });
          const data = await res.json();
          if (controller.signal.aborted) return;
          detailCache.set(cacheKey, data);
          const fallbackRatings = buildFallbackExternalRatings(data.rating);
          setMedia({
            ...data,
            imdbId: data.imdbId ?? routeImdbId ?? null,
            externalRatings: fallbackRatings,
            externalRatingsMode: fallbackRatings.length > 0 ? 'fallback' : 'none',
          });
          setVimeoKey(data.vimeoKey ?? null);
          // Prefetch backdrop and poster for immediate crispness
          if (data.backdrop || data.poster) {
            Image.prefetch([data.backdrop, data.poster].filter(Boolean));
          }
        } catch (e) {
          if (controller.signal.aborted) return;
          console.error('Detail fetch failed:', e);
          detailCache.delete(cacheKey); // don't cache errors
        } finally {
          if (!controller.signal.aborted) {
            setLoading(false);
          }
        }
      };
    void fetchDetails();
    return () => {
      controller.abort();
      if (detailsAbortRef.current === controller) detailsAbortRef.current = null;
    };
  }, [movieId, type, cacheKey, routeImdbId]);

  useEffect(() => {
    const controller = new AbortController();
    commentsAbortRef.current?.abort();
    commentsAbortRef.current = controller;

    const fetchComments = async () => {
      try {
        const res = await fetch(`${API_BASE}/trakt/comments/${type}/${movieId}`, { signal: controller.signal });
        if (!res.ok) {
          if (!controller.signal.aborted) setTraktComments([]);
          return;
        }
        const data = await res.json();
        if (!controller.signal.aborted) setTraktComments(Array.isArray(data?.results) ? data.results : []);
      } catch {
        if (!controller.signal.aborted) setTraktComments([]);
      }
    };

    void fetchComments();
    return () => {
      controller.abort();
      if (commentsAbortRef.current === controller) commentsAbortRef.current = null;
    };
  }, [movieId, type]);

  // Derive watchlist status from local storage and/or Trakt context.
  // Uses useFocusEffect so the icon refreshes when returning from the player.
  useFocusEffect(useCallback(() => {
    const inTrakt = isConnected && traktWatchlist.some(
      item => Number(item.tmdbId) === Number(movieId),
    );
    if (watchlistRemovalIds.includes(String(movieId))) {
      setInWatchlist(false);
      return;
    }
    if (inTrakt) { setInWatchlist(true); return; }
    if (!watchlistKey) { setInWatchlist(false); return; }
    readWatchlistItems(storageOwnerId, legacyOwnerId).then(list => {
      setInWatchlist(list.some((i: any) => watchlistItemMatchesId(i, movieId)));
    });
  }, [legacyOwnerId, movieId, watchlistKey, isConnected, storageOwnerId, traktWatchlist, user?.uid, watchlistRemovalIds]));

  // Fetch streams progressively when the Streams tab becomes active.
  //
  // IMPORTANT: streamsFetchedRef is a REF, not state. If it were state, setting
  // it true inside this effect would change a dep ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ React would re-run the effect
  // ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ the cleanup would call controller.abort() ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ every fetch gets aborted before
  // it can complete. The ref update is invisible to React's dep tracking.
  useEffect(() => {
    if (!shouldPreloadStreams || streamsFetchedRef.current || !media) return;

    streamsAbortRef.current?.abort();
    const controller = new AbortController();
    streamsAbortRef.current = controller;
    const requestId = streamsRequestIdRef.current + 1;
    streamsRequestIdRef.current = requestId;

    setStreamsLoading(true);
    setStreamsLoadComplete(false);
    setStreamsFetchStarted(true);
    streamsFetchedRef.current = true; // ref ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â no re-render, no accidental abort

    const videoId    = media.imdbId ?? String(movieId);
    const streamType = type === 'tv' ? 'series' : 'movie';

    void fetchStreamsProgressive(
      streamType,
      videoId,
      (newStreams, pending) => {
        if (controller.signal.aborted || streamsRequestIdRef.current !== requestId) return;
        setStreams(newStreams);
        setStreamsPending(pending);
        if (newStreams.length > 0 || pending === 0) setStreamsLoading(false);
        if (pending === 0) setStreamsLoadComplete(true);
      },
      controller.signal,
    ).catch(() => {
      if (controller.signal.aborted || streamsRequestIdRef.current !== requestId) return;
      setStreamsPending(0);
      setStreamsLoading(false);
      setStreamsLoadComplete(true);
    });

    return () => { controller.abort(); };
  }, [shouldPreloadStreams, streamsFetchKey, media?.id, media?.imdbId, movieId, type, fetchStreamsProgressive]);

  // When returning from Auth or Addons with the Streams tab active and no results,
  // reset the guard and bump the fetch key so the effect re-runs.
  useEffect(() => {
    const unsub = navigation.addListener('focus', () => {
      if (activeTab === 'streams' && streams.length === 0 && streamsFetchedRef.current) {
        streamsFetchedRef.current = false;
        setStreamsFetchKey(k => k + 1);
      }
    });
    return unsub;
  }, [navigation, activeTab, streams.length]);

  // Stable signature of which addons are currently enabled (sorted for stability)
  const enabledAddonsSig = useMemo(
    () => `${streamCapableAddons.map(addon => addon.id).sort().join(',')}|ultra:${ultraActive ? '1' : '0'}`,
    [streamCapableAddons, ultraActive],
  );
  const prevEnabledSigRef = useRef(enabledAddonsSig);

  // When the enabled-addon set changes while the screen is mounted, reset the
  // fetch guard and clear existing streams so the UI re-fetches immediately
  // (if already on the streams tab) or freshly on next visit to that tab.
  useEffect(() => {
    const prev = prevEnabledSigRef.current;
    prevEnabledSigRef.current = enabledAddonsSig;
    if (prev === enabledAddonsSig) return;        // no real change
    streamsFetchedRef.current = false;
    setStreams([]);
    setStreamsFetchKey(k => k + 1);              // triggers re-fetch if on streams tab
  }, [enabledAddonsSig]);

  // When the user logs in while this screen is mounted (e.g. navigated to Auth
  // from the Play button or streams tab), automatically switch to Streams and
  // reload so they don't have to tap again.
  const prevUserRef = useRef(user);
  useEffect(() => {
    if (prevUserRef.current === null && user !== null && media) {
      refreshAddons();
      if (type === 'tv') {
        if (useGlassDetailLayout) {
          setShowSeasonsPanel(true);
        } else {
          setActiveTab('seasons');
        }
        scrollViewRef.current?.scrollTo({ y: 0, animated: true });
      } else {
        setActiveTab('streams');
        streamsFetchedRef.current = false;
        setStreamsFetchKey(k => k + 1);
        setStreams([]);
        setStreamsPending(0);
        setStreamsLoading(false);
      }
    }
    prevUserRef.current = user;
  }, [user, media, type, refreshAddons]);

  // Reset stream preload state whenever the movie/user/addon readiness changes.
  // The tab stays locked while addon manifests are still loading, then remains
  // locked until the progressive stream fetch finishes.
  useEffect(() => {
    if (!media) return; // wait for media to load

    streamsAbortRef.current?.abort();
    streamsFetchedRef.current = false;
    setSelectedAddon('all');
    setStreams([]);
    setStreamsPending(0);
    setStreamsFetchStarted(false);

    if (!isMovieDetail) {
      setStreamsLoadComplete(true);
      setStreamsLoading(false);
      return;
    }

    if (addonsLoading) {
      setStreamsLoadComplete(false);
      setStreamsLoading(true);
      return;
    }

    if (!hasStreamSources) {
      setStreamsLoadComplete(true);
      setStreamsLoading(false);
      return;
    }

    setStreamsLoadComplete(false);
    setStreamsLoading(true);
    setStreamsFetchKey(k => k + 1);
  }, [movieId, media?.id, isMovieDetail, addonsLoading, hasStreamSources]);

  useEffect(() => {
    if (useGlassDetailLayout && activeTab === 'streams') {
      setActiveTab('about');
      return;
    }
    if (activeTab === 'streams' && (streamsTabLocked || isUnreleased || !showStreamsList)) {
      setActiveTab('about');
    }
  }, [activeTab, streamsTabLocked, isUnreleased, showStreamsList, useGlassDetailLayout]);

  useEffect(() => {
    const shouldFetch = useGlassDetailLayout ? showSeasonsPanel : activeTab === 'seasons';
    if (!shouldFetch || type !== 'tv') return;
    const controller = new AbortController();
    seasonsAbortRef.current?.abort();
    seasonsAbortRef.current = controller;
    const fetchEpisodes = async () => {
      setEpisodesLoading(true);
      try {
        const res = await tmdbFetch(`/tmdb/season/${movieId}/${selectedSeason}`, { signal: controller.signal });
        const data = await res.json();
        if (!controller.signal.aborted) {
          setEpisodes(data.episodes || []);
        }
      } catch {
        if (!controller.signal.aborted) setEpisodes([]);
      }
      finally {
        if (!controller.signal.aborted) setEpisodesLoading(false);
      }
    };
    fetchEpisodes();
    return () => {
      controller.abort();
      if (seasonsAbortRef.current === controller) seasonsAbortRef.current = null;
    };
  }, [activeTab, showSeasonsPanel, selectedSeason, movieId, type, useGlassDetailLayout]);

  useEffect(() => {
    if (isForeground) return;
    detailsAbortRef.current?.abort();
    commentsAbortRef.current?.abort();
    seasonsAbortRef.current?.abort();
    streamsAbortRef.current?.abort();
    setEpisodesLoading(false);
    setStreamsLoading(false);
  }, [isForeground]);

  // Unified save: saves to Trakt (if connected) + local storage
  const toggleSave = useCallback(async () => {
    if (!media) return;
    const wasInWatchlist = inWatchlist;
    const optimistic = !wasInWatchlist;
    setInWatchlist(optimistic);

    try {
      const current = await readWatchlistItems(storageOwnerId, legacyOwnerId);
      const next = wasInWatchlist
        ? current.filter((i: any) => !watchlistItemMatchesId(i, movieId))
        : [...current, normalizeWatchlistItem({
            id: String(movieId),
            tmdbId: Number(movieId),
            title: media.title,
            type,
            poster: media.poster,
            rating: media.rating,
            year: media.year,
            addedAt: Date.now(),
          })];
      await writeWatchlistItems(storageOwnerId, next);

      const nextRemovalIds = wasInWatchlist
        ? Array.from(new Set([...watchlistRemovalIds, String(movieId)]))
        : watchlistRemovalIds.filter(id => id !== String(movieId));
      setWatchlistRemovalIds(nextRemovalIds);
      await writeWatchlistRemovalIds(storageOwnerId, nextRemovalIds);

      if (isConnected) {
        const endpoint = wasInWatchlist ? '/trakt/sync/watchlist/remove' : '/trakt/sync/watchlist/add';
        const entry = { title: media.title, year: parseInt(String(media.year)) || undefined, ids: { tmdb: Number(movieId) } };
        const payload = type === 'movie' ? { movies: [entry], shows: [] } : { movies: [], shows: [entry] };
        void (async () => {
          try {
            await fetch(`${API_BASE}${endpoint}`, {
              method: 'POST',
              headers: await buildAuthHeaders(user, { profileId: activeProfile?.id }),
              body: JSON.stringify(payload),
            });
            await refreshWatchlist();
          } catch {}
        })();
      }
    } catch {
      setInWatchlist(!optimistic); // revert on error
    }
  }, [activeProfile?.id, inWatchlist, isConnected, legacyOwnerId, media, movieId, refreshWatchlist, storageOwnerId, type, user, watchlistRemovalIds]);

  const handleToggleWatched = useCallback(async () => {
    if (!media) return;
    if (type === 'tv') {
      if ((media.seasons || []).length === 0) return;
      setSeriesWatchedConfirm(true);
    } else {
      await toggleMovieWatched(Number(movieId), media.imdbId ?? undefined, media.title, parseInt(String(media.year)) || undefined);
      clearProgress(movieProgressKey(Number(movieId)));
      clearProgressIndexEntry(movieProgressKey(Number(movieId)));
      setLocalProgress(null);
    }
  }, [media, type, movieId, toggleMovieWatched, clearProgress, clearProgressIndexEntry]);

  const playStream = useCallback(async (stream: AddonStream) => {
    const pKey = movieProgressKey(Number(movieId));
    const runtimeSec = media?.runtime ? media.runtime * 60 : undefined;
    const activeSourceIdentity = (
      stream.infoHash ?? stream.url ?? stream.behaviorHints?.filename ?? stream.title ?? stream.name ?? ''
    ).trim().replace(/\s+/g, ' ').toLowerCase();
    // Direct URL stream
    if (stream.url) {
      navigation.navigate('Player', {
        movieId,
        imdbId: media?.imdbId ?? undefined,
        type,
        title: media?.title,
        year: media?.year,
        synopsis: media?.description ?? undefined,
        titleLogo: media?.titleLogo,
        streamUrl: stream.url,
        activeStream: stream,
        backdrop: detailHeroUri ?? media?.backdrop,
        poster: media?.poster,
        resumeFrom: resumeFromSec,
        progressKey: pKey,
        runtimeSec,
        resolverMovieId: movieId,
        resolverImdbId: media?.imdbId ?? undefined,
        resolverType: type,
        sourceStreams: [stream],
        activeSourceIdentity,
        returnToPlayerParams: {
          movieId,
          imdbId: media?.imdbId ?? undefined,
          type,
          title: media?.title,
          year: media?.year,
          synopsis: media?.description ?? undefined,
          titleLogo: media?.titleLogo,
          backdrop: detailHeroUri ?? media?.backdrop,
          poster: media?.poster,
          progressKey: pKey,
        },
      });
      return;
    }
    // Debrid-cached torrent
    if (stream.infoHash) {
      navigation.navigate('Player', {
          movieId,
          imdbId: media?.imdbId ?? undefined,
          type,
          title: media?.title,
          year: media?.year,
          synopsis: media?.description ?? undefined,
          titleLogo: media?.titleLogo,
          activeStream: stream,
          backdrop: media?.backdrop,
          poster: media?.poster,
          resumeFrom: resumeFromSec,
          progressKey: pKey,
          runtimeSec,
          resolverMovieId: movieId,
          resolverImdbId: media?.imdbId ?? undefined,
          resolverType: type,
          sourceStreams: [stream],
          activeSourceIdentity,
          resolveOnMount: true,
          returnToPlayerParams: {
            movieId,
            imdbId: media?.imdbId ?? undefined,
            type,
            title: media?.title,
            year: media?.year,
            synopsis: media?.description ?? undefined,
            titleLogo: media?.titleLogo,
            backdrop: media?.backdrop,
            poster: media?.poster,
            progressKey: pKey,
          },
        });
    }
  }, [navigation, movieId, media, type, resumeFromSec, detailHeroUri]);

  const movieReleaseDate = type === 'movie' ? formatReleaseDate(media?.releaseDate) : null;
  const compactMovieDetails = useMemo(() => ([
    { label: 'Release Date', value: movieReleaseDate ?? 'Unknown' },
    { label: 'Status', value: media?.status ?? 'Unknown' },
    { label: 'Origin Country', value: formatCountryList(media?.originCountry ?? media?.originCountries ?? media?.origin_country ?? media?.productionCountries ?? media?.production_countries) ?? 'Unknown' },
    { label: 'Original Language', value: formatOriginalLanguage(media) ?? 'Unknown' },
  ]), [
    media?.originCountry,
    media?.originCountries,
    media?.origin_country,
    media?.originalLanguage,
    media?.original_language,
    media?.productionCountries,
    media?.production_countries,
    media?.spokenLanguages,
    media?.spoken_languages,
    media?.status,
    movieReleaseDate,
  ]);
  const availableProviders = useMemo(() => {
    const combined = [...(media?.streamingProviders ?? []), ...(media?.rentProviders ?? [])];
    const seen = new Set<string>();
    return combined.filter((provider: any) => {
      const key = String(provider?.id ?? provider?.name ?? '');
      if (!key || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }, [media?.rentProviders, media?.streamingProviders]);
  const selectedSeasonInfo = useMemo(() => (
    (media?.seasons || []).find((season: any) => Number(season.season_number) === Number(selectedSeason))
  ), [media?.seasons, selectedSeason]);
  const selectedSeasonEpisodeCount = useMemo(() => {
    const explicitCount = Number(selectedSeasonInfo?.episode_count ?? 0);
    if (Number.isFinite(explicitCount) && explicitCount > 0) return explicitCount;
    return episodes.length;
  }, [episodes.length, selectedSeasonInfo?.episode_count]);
  const selectedSeasonWatched = useMemo(() => {
    if (type !== 'tv' || selectedSeasonEpisodeCount <= 0) return false;
    return Array.from({ length: selectedSeasonEpisodeCount }, (_, index) => index + 1)
      .every(epNumber => isEpisodeWatched(Number(movieId), Number(selectedSeason), epNumber));
  }, [isEpisodeWatched, movieId, selectedSeason, selectedSeasonEpisodeCount, type]);
  const handleToggleSeasonWatched = useCallback(async () => {
    if (!media || type !== 'tv' || selectedSeasonEpisodeCount <= 0) return;
    if (selectedSeasonWatched) {
      unmarkSeasonWatched(Number(movieId), Number(selectedSeason));
      return;
    }
    await markSeasonWatched(
      Number(movieId),
      media.imdbId ?? undefined,
      media.title,
      Number(selectedSeason),
      selectedSeasonEpisodeCount,
    );
  }, [markSeasonWatched, media, movieId, selectedSeason, selectedSeasonEpisodeCount, selectedSeasonWatched, type, unmarkSeasonWatched]);

  if (loading) return (
    <MediaDetailSkeleton
      onBack={() => navigation.goBack()}
      insetTop={insets.top}
      centered={uiStyle === 'centered'}
      glass={uiStyle === 'glass'}
    />
  );
  if (!media) return (
    <View style={styles.loader}><Text style={{ color: colors.textPrimary }}>{t('common_error')}</Text></View>
  );
  const PageWrapper: any = Animated.ScrollView;

  return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
          {(vividAmbientEnabled || useGlassDetailLayout) && ambientBackdropUri ? (
            <View pointerEvents="none" style={useGlassDetailLayout ? styles.glassAmbientBackdrop : styles.ambientBackdrop}>
              <Image
                source={{ uri: ambientBackdropUri }}
                style={useGlassDetailLayout ? styles.glassAmbientBackdropImage : styles.ambientBackdropImage}
                contentFit="cover"
                blurRadius={useGlassDetailLayout ? 34 : 20}
                cachePolicy="memory-disk"
                priority="high"
                transition={0}
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
          {useGlassDetailLayout ? (
            <GlassBackButton
              onPress={() => navigation.goBack()}
              top={insets.top + 14}
              left={16}
              blurTarget={blurTargetRef}
              iconColor={isLightAppearance ? colors.textPrimary : '#fff'}
            />
          ) : null}
          {!useGlassDetailLayout ? (
            <Animated.View
              style={[
                uiStyle === 'centered' ? styles.backdropWrapperCentered : styles.backdropWrapper,
                { position: 'absolute', top: 0, left: 0, right: 0, transform: [{ translateY: detailHeroLiftTranslateY }] },
              ]}
              pointerEvents="box-none"
              onLayout={event => {
                const nextHeight = Math.round(event.nativeEvent.layout.height);
                if (nextHeight > 0 && nextHeight !== heroBackdropHeight) {
                  setHeroBackdropHeight(nextHeight);
                }
              }}
            >
              {detailHeroUri ? (
                <Animated.View style={[StyleSheet.absoluteFillObject, shouldParallaxHero ? { transform: [{ translateY: detailHeroTranslateY }, { scale: detailHeroScale }] } : null]}>
                  <Image source={{ uri: detailHeroUri }} style={uiStyle === 'centered' ? styles.backdropCentered : styles.backdrop} cachePolicy="memory-disk" priority="high" transition={0} />
                </Animated.View>
              ) : null}
              {canAutoplayHeroTrailer && heroTrailerKey ? (
                <Animated.View style={[StyleSheet.absoluteFillObject, shouldParallaxHero ? { transform: [{ translateY: detailHeroTranslateY }, { scale: detailHeroScale }] } : null]}>
                  <HeroTrailerBackground
                    videoId={heroTrailerKey}
                    play={canAutoplayHeroTrailer && isForeground}
                    muted={heroTrailerMuted}
                    ready={heroTrailerReady}
                    badgeTop={insets.top + 10}
                    onReady={() => setHeroTrailerReady(true)}
                    onEnded={() => setHeroTrailerFinished(true)}
                    onError={() => setHeroTrailerFinished(true)}
                    onToggleMute={() => setHeroTrailerMuted(current => !current)}
                  />
                </Animated.View>
              ) : null}
              {isLightAppearance && uiStyle === 'centered' && detailHeroUri ? (
                <Animated.View pointerEvents="none" style={[styles.backdropGlassOverlay, shouldParallaxHero ? { transform: [{ translateY: detailHeroTranslateY }, { scale: detailHeroScale }] } : null]}>
                  <Image
                    source={{ uri: detailHeroUri }}
                    blurRadius={4}
                    contentFit="cover"
                    cachePolicy="memory-disk"
                    priority="high"
                    transition={0}
                    style={[
                      StyleSheet.absoluteFillObject,
                      {
                        top: -glassOverlayOffset,
                        height: heroBackdropHeight,
                      },
                    ]}
                  />
                  <LinearGradient
                    colors={['rgba(8,10,14,0.00)', 'rgba(8,10,14,0.00)', 'rgba(8,10,14,0.008)']}
                    locations={[0, 0.66, 1]}
                    pointerEvents="none"
                    style={StyleSheet.absoluteFillObject}
                  />
                  <LinearGradient
                    colors={['rgba(8,10,14,0.00)', 'rgba(8,10,14,0.004)', 'rgba(8,10,14,0.00)']}
                    locations={[0, 0.5, 1]}
                    pointerEvents="none"
                    style={styles.backdropGlassTopFade}
                  />
                  <LinearGradient
                    colors={['rgba(8,10,14,0.02)', 'rgba(8,10,14,0.00)']}
                    locations={[0, 1]}
                    pointerEvents="none"
                    style={[styles.backdropGlassSideFeather, { left: 0 }]}
                  />
                  <LinearGradient
                    colors={['rgba(8,10,14,0.02)', 'rgba(8,10,14,0.00)']}
                    locations={[0, 1]}
                    pointerEvents="none"
                    style={[styles.backdropGlassSideFeather, { right: 0 }]}
                  />
                </Animated.View>
              ) : null}
              {isPosterOnlyHero ? (
                <>
                  <View style={styles.backdropPosterTint} />
                  <View style={styles.backdropPosterFrame}>
                    <Image source={{ uri: media.poster }} style={styles.backdropPosterImage} contentFit="contain" transition={200} />
                  </View>
                </>
              ) : null}
            </Animated.View>
          ) : null}
          <PageWrapper
            style={useGlassDetailLayout ? styles.glassContainer : styles.container}
            contentContainerStyle={{ flexGrow: 1, paddingTop: detailPageContentTop }}
            onScroll={Animated.event([{ nativeEvent: { contentOffset: { y: detailScrollY } } }], { useNativeDriver: true })}
            scrollEventThrottle={16}
            showsVerticalScrollIndicator={false}
        >
        <StatusBar barStyle="light-content" translucent backgroundColor="transparent" />
        <TrailerModal
          visible={trailerVisible}
          trailerKey={media.trailerKey}
          trailerKeys={media.trailerKeys}
          trailerSite={media.trailerSite}
          vimeoKey={vimeoKey}
          onClose={() => setTrailerVisible(false)}
        />
      <View style={[styles.detailContentBody, useGlassDetailLayout && styles.detailContentBodyGlass]}>
                {!useGlassDetailLayout ? (
          <>
            <LinearGradient
              colors={isLightAppearance
                ? [detailContentTint, 'rgba(255,255,255,0.78)', 'rgba(255,255,255,0.34)', 'rgba(255,255,255,0.00)']
                : [detailContentTint, 'rgba(5,6,10,0.82)', 'rgba(5,6,10,0.34)', 'rgba(5,6,10,0.00)']}
              locations={[0, 0.22, 0.58, 1]}
              style={[styles.detailContentTopTint, { top: -199, height: 189, transform: [{ scaleY: -1 }] }]}
              pointerEvents="none"
            />
            <LinearGradient
              colors={isLightAppearance
                ? [detailContentTint, detailContentTint, 'rgba(255,255,255,0.00)']
                : [detailContentTint, detailContentTint, 'rgba(5,6,10,0.00)']}
              locations={[0, 0.42, 1]}
              style={[styles.detailContentBottomTint, { top: -10, height: 270 }]}
              pointerEvents="none"
            />
          </>
        ) : null}
        {useGlassDetailLayout ? (
          <View style={[styles.glassHeroSection, { paddingTop: insets.top + 24 }]}>
            <Text numberOfLines={2} style={styles.glassHeroTitle}>{media.title}</Text>
            <View style={styles.glassHeroCard}>
              {detailHeroUri ? (
                <Image source={{ uri: detailHeroUri }} style={styles.glassHeroImage} cachePolicy="memory-disk" priority="high" transition={0} />
              ) : null}
              <LinearGradient
                colors={['rgba(255,255,255,0.00)', isLightAppearance ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.18)']}
                locations={[0.62, 1]}
                style={styles.glassHeroImageScrim}
                pointerEvents="none"
              />
            </View>
          </View>
        ) : null}
        {useCompactDetailLayout ? (
          <>
            <View style={useGlassDetailLayout ? styles.heroInfoShellGlass : styles.heroInfoShellCentered}>
              <View style={[styles.centeredMeta, useGlassDetailLayout && styles.glassCenteredMeta]}>
                {useGlassDetailLayout ? null : media.titleLogo ? (
                  <View style={styles.centeredTitleLogoWrap}>
                    <Image source={{ uri: media.titleLogo }} style={styles.centeredTitleLogo} contentFit="contain" transition={200} />
                  </View>
                ) : (
                  <View style={styles.centeredTitleBlock}>
                    <Text
                      numberOfLines={2}
                      style={[
                        styles.centeredTitle,
                        isLightAppearance && styles.centeredTitleLightFallback,
                      ]}
                    >
                      {media.title}
                    </Text>
                  </View>
                )}
                {shouldShowExternalRatings ? (
                  <Animated.View style={[styles.externalRatingsSection, useGlassDetailLayout && styles.glassExternalRatingsSection, { opacity: ratingsRowAnimation, transform: [{ translateY: ratingsRowTranslateY }] }]}>
                    <ExternalRatingsRow ratings={displayedExternalRatings} colors={colors} isLightAppearance={isLightAppearance} centered fallbackGenre={fallbackGenreLabel} />
                  </Animated.View>
                ) : null}
                {useGlassDetailLayout && (
                  <View style={styles.glassSynopsisBlock}>
                    <Text
                      style={styles.glassSynopsisText}
                      numberOfLines={isDescriptionExpanded ? undefined : 3}
                      onTextLayout={(e) => {
                        if (!showDescriptionMore && e.nativeEvent.lines.length > 3) {
                          setShowDescriptionMore(true);
                        }
                      }}
                    >
                      {media.description || t('media_no_description')}
                    </Text>
                    {!showDescriptionMore && (
                      <Text
                        style={[styles.glassSynopsisText, { position: 'absolute', opacity: 0, left: 20, right: 20 }]}
                        onTextLayout={(e) => {
                          if (e.nativeEvent.lines.length > 3) {
                            setShowDescriptionMore(true);
                          }
                        }}
                      >
                        {media.description || t('media_no_description')}
                      </Text>
                    )}
                    {showDescriptionMore && (
                      <TouchableOpacity
                        onPress={() => setIsDescriptionExpanded(!isDescriptionExpanded)}
                        style={styles.moreButton}
                      >
                        <Text style={styles.moreButtonText}>
                          {isDescriptionExpanded ? t('media_less') : t('media_more')}
                        </Text>
                      </TouchableOpacity>
                    )}
                  </View>
                )}
              </View>
            </View>
            <View style={styles.heroActionsShell}>
              <View style={styles.centeredActionsWrap}>
                {useGlassDetailLayout ? (
                  <View style={styles.glassPlayWrap}>
                    <PrimaryActionButton
                      colors={colors}
                      themeId={theme.id}
                      isLightAppearance={isLightAppearance}
                      surface="glass"
                      blurTarget={blurTargetRef}
                      fullWidth
                      progressPct={primaryPlayProgress}
                      label={primaryPlayLabel}
                      leading={playButtonLocked
                        ? <ActivityIndicator size="small" color={primaryActionPalette.textColor} />
                        : noStreamsFound
                        ? <Ionicons name="cloud-offline-outline" size={16} color={primaryActionPalette.textColor} />
                        : <Ionicons name="play" size={16} color={primaryActionPalette.textColor} />}
                      disabled={playButtonLocked || isUnreleased || noStreamsFound}
                      activeOpacity={(playButtonLocked || isUnreleased || noStreamsFound) ? 1 : 0.85}
                      onPress={handlePrimaryPlayPress}
                    />
                  </View>
                ) : (
                  <PrimaryActionButton
                    colors={colors}
                    themeId={theme.id}
                    isLightAppearance={isLightAppearance}
                    fullWidth={useCompactDetailLayout}
                    style={useCompactDetailLayout ? { width: '100%' } : { flex: 1 }}
                    progressPct={primaryPlayProgress}
                    label={primaryPlayLabel}
                    leading={playButtonLocked
                      ? <ActivityIndicator size="small" color={primaryActionPalette.textColor} />
                      : noStreamsFound
                      ? <Ionicons name="cloud-offline-outline" size={16} color={primaryActionPalette.textColor} />
                      : <Ionicons name="play" size={16} color={primaryActionPalette.textColor} />}
                    disabled={playButtonLocked || isUnreleased || noStreamsFound}
                    activeOpacity={(playButtonLocked || isUnreleased || noStreamsFound) ? 1 : 0.85}
                    onPress={handlePrimaryPlayPress}
                  />
                )}
              </View>
            </View>
          </>
        ) : (
          <>
            <View style={styles.heroInfoShell}>
              <View style={styles.metaRow}>
                <Image source={{ uri: media.poster }} style={styles.poster} />
                <View style={styles.metaInfo}>
                  {media.titleLogo && !isLightAppearance ? (
                    <View style={styles.classicTitleBlock}>
                      <Image
                        source={{ uri: media.titleLogo }}
                        style={styles.classicTitleLogo}
                        contentFit="contain"
                        contentPosition="left center"
                        transition={200}
                      />
                    </View>
                  ) : (
                    <View style={styles.titleTextBlock}>
                      <Text numberOfLines={2} style={styles.title}>{media.title}</Text>
                    </View>
                  )}
                  {media.tagline ? <Text style={styles.tagline}>"{media.tagline}"</Text> : null}
                </View>
              </View>
              {shouldShowExternalRatings ? (
                <Animated.View style={[styles.externalRatingsSection, { opacity: ratingsRowAnimation, transform: [{ translateY: ratingsRowTranslateY }] }]}>
                  <ExternalRatingsRow ratings={displayedExternalRatings} colors={colors} isLightAppearance={isLightAppearance} centered fallbackGenre={fallbackGenreLabel} />
                </Animated.View>
              ) : null}
            </View>
            <View style={styles.heroActionsShell}>
              <View style={styles.classicActionRow}>
                <View style={styles.classicActionRowLeft}>
                  <PrimaryActionButton
                    colors={colors}
                    themeId={theme.id}
                    isLightAppearance={isLightAppearance}
                    fullWidth={false}
                    style={{ flex: 1 }}
                    progressPct={primaryPlayProgress}
                    label={primaryPlayLabel}
                    leading={playButtonLocked
                      ? <ActivityIndicator size="small" color={primaryActionPalette.textColor} />
                      : noStreamsFound
                      ? <Ionicons name="cloud-offline-outline" size={16} color={primaryActionPalette.textColor} />
                      : <Ionicons name="play" size={16} color={primaryActionPalette.textColor} />}
                    disabled={playButtonLocked || isUnreleased || noStreamsFound}
                    activeOpacity={(playButtonLocked || isUnreleased || noStreamsFound) ? 1 : 0.85}
                    onPress={handlePrimaryPlayPress}
                  />
                </View>
                <View style={styles.classicActionRowRight}>
                  {media.trailerKey && !heroTrailerAutoplayEnabled && (
                    <TouchableOpacity style={styles.trailerBtn} activeOpacity={0.85} onPress={() => setTrailerVisible(true)}>
                      <Text style={styles.trailerBtnText}>{t('media_show_trailer')}</Text>
                    </TouchableOpacity>
                  )}
                  <TouchableOpacity
                    style={[styles.watchlistBtn, inWatchlist && styles.watchlistBtnActive]}
                    activeOpacity={0.85}
                    onPress={toggleSave}
                  >
                    <Ionicons
                      name={inWatchlist ? 'bookmark' : 'bookmark-outline'}
                      size={18}
                      color={inWatchlist ? (isLightAppearance ? colors.textPrimary : colors.accentSoft) : detailMutedIcon}
                    />
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.watchedBtn, watched && styles.watchedBtnActive]}
                    activeOpacity={0.85}
                    onPress={handleToggleWatched}
                  >
                    <Ionicons
                      name={watched ? 'checkmark-circle' : 'checkmark-circle-outline'}
                      size={18}
                      color={watched ? (isLightAppearance ? '#1b5e20' : '#00e676') : detailMutedIcon}
                    />
                  </TouchableOpacity>
                </View>
              </View>
            </View>
          </>
        )}

        <View style={useCompactDetailLayout ? styles.centeredCombinedRowSection : styles.actions}>
          {useCompactDetailLayout && (
            // Centered: tabs first, then icon pills ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â all unified pill shape
            <View style={styles.centeredCombinedRow}>
              {([
                'about',
                ...(media.type === 'tv' || type === 'tv' ? ['seasons'] : []),
                ...(showStreamsTab && !useGlassDetailLayout ? ['streams'] : []),
              ] as Tab[]).filter(tab => !(useGlassDetailLayout && tab === 'about')).map(tab => (
                <TouchableOpacity
                  key={tab}
                  activeOpacity={0.75}
                  onPress={() => {
                    if (useGlassDetailLayout && tab === 'seasons') {
                      setShowSeasonsPanel(v => !v);
                    } else if (!(tab === 'streams' && streamsTabLocked)) {
                      setActiveTab(tab);
                    }
                  }}
                  disabled={tab === 'streams' && streamsTabLocked}
                  style={[
                    styles.centeredPill,
                    styles.centeredTabPill,
                    (useGlassDetailLayout && tab === 'seasons' ? showSeasonsPanel : activeTab === tab) && styles.centeredTabPillActive,
                    tab === 'streams' && streamsTabLocked && styles.centeredTabPillDisabled,
                  ]}
                >
                  <View style={styles.tabLabelWrap}>
                    {tab === 'streams' && streamsTabLoadingIndicator ? (
                      <View pointerEvents="none" style={styles.tabLoadingOverlay}>
                        <ActivityIndicator size="small" color={colors.accentSoft} style={styles.tabInlineSpinner} />
                      </View>
                    ) : null}
                  <Text style={[styles.centeredPillText, (useGlassDetailLayout && tab === 'seasons' ? showSeasonsPanel : activeTab === tab) && styles.centeredPillTextActive, tab === 'streams' && streamsTabLoadingIndicator && { opacity: 0.18 }]}>
                  {tab === 'streams'
                      ? `${t('media_streams')}${streams.length > 0 ? ` (${streams.length})` : ''}`
                      : useGlassDetailLayout && tab === 'seasons'
                        ? (showSeasonsPanel ? t('media_hide_seasons') : t('media_show_seasons'))
                        : t(`media_${tab}` as any)}
                  </Text>
                  </View>
                </TouchableOpacity>
              ))}
              {media.trailerKey && !heroTrailerAutoplayEnabled && (
                <TouchableOpacity style={styles.centeredPill} activeOpacity={0.85} onPress={() => setTrailerVisible(true)}>
                  <Ionicons name="film-outline" size={17} color={colors.accentSoft} />
                </TouchableOpacity>
              )}
                <TouchableOpacity
                  style={[styles.centeredPill, inWatchlist && styles.centeredPillActive]}
                  activeOpacity={0.85}
                  onPress={toggleSave}
                >
                  <Ionicons name={inWatchlist ? 'bookmark' : 'bookmark-outline'} size={17} color={inWatchlist ? colors.accentSoft : detailMutedIcon} />
                </TouchableOpacity>
              <TouchableOpacity
                style={[styles.centeredPill, watched && styles.centeredPillWatchedActive]}
                activeOpacity={0.85}
                onPress={handleToggleWatched}
              >
                <Ionicons name={watched ? 'checkmark-circle' : 'checkmark-circle-outline'} size={17} color={watched ? '#00e676' : detailMutedIcon} />
              </TouchableOpacity>
            </View>
          )}

        {!useCompactDetailLayout && (
          <View style={styles.tabs}>
            {([
              'about',
              ...(media.type === 'tv' || type === 'tv' ? ['seasons'] : []),
              // Streams tab only for movies ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â TV episodes open a dedicated page
              ...(showStreamsTab && !useGlassDetailLayout ? ['streams'] : []),
            ] as Tab[]).map(tab => (
              <TouchableOpacity
                key={tab}
                activeOpacity={0.75}
                onPress={() => {
                  if (useGlassDetailLayout && tab === 'seasons') {
                    setShowSeasonsPanel(v => !v);
                  } else if (!(tab === 'streams' && streamsTabLocked)) {
                    setActiveTab(tab);
                  }
                }}
                disabled={tab === 'streams' && streamsTabLocked}
                style={[styles.tab, (useGlassDetailLayout && tab === 'seasons' ? showSeasonsPanel : activeTab === tab) && styles.activeTab, tab === 'streams' && streamsTabLocked && styles.tabDisabled]}
              >
                <View style={styles.tabLabelWrap}>
                  {tab === 'streams' && streamsTabLoadingIndicator ? (
                    <View pointerEvents="none" style={styles.tabLoadingOverlay}>
                      <ActivityIndicator size="small" color={colors.accentSoft} style={styles.tabInlineSpinner} />
                    </View>
                    ) : null}
                <Text style={[styles.tabText, (useGlassDetailLayout && tab === 'seasons' ? showSeasonsPanel : activeTab === tab) && styles.activeTabText, tab === 'streams' && streamsTabLoadingIndicator && { opacity: 0.18 }]}>
                  {tab === 'streams'
                    ? `${t('media_streams')}${streams.length > 0 ? ` (${streams.length})` : ''}`
                    : useGlassDetailLayout && tab === 'seasons'
                      ? (showSeasonsPanel ? t('media_hide_seasons') : t('media_show_seasons'))
                      : t(`media_${tab}` as any)}
                </Text>
                </View>
              </TouchableOpacity>
            ))}
          </View>
        )}
        </View>

        {/* Sticky filter bar ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â index 4 in stickyHeaderIndices; 0-height when not applicable */}
        <View>
          {activeTab === 'streams' && addonNames.length > 1 && streams.length > 0 && (
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              style={{ paddingVertical: 10 }}
              contentContainerStyle={{ paddingHorizontal: 14, gap: 8, paddingRight: 18 }}
            >
              {(['all', ...addonNames] as string[]).map(name => {
                const active = (addonNames.includes(selectedAddon) ? selectedAddon : 'all') === name;
                const label = name === 'all'
                  ? t('media_all_sources')
                  : (name.trim().toLowerCase() === 'ultra boost' || name.trim().toLowerCase() === 'streamdek ultra' || name.trim().toLowerCase() === 'sd ultra'
                    ? 'SD ultra'
                    : name);
                return (
                  <TouchableOpacity
                    key={name}
                    onPress={() => setSelectedAddon(name)}
                    activeOpacity={0.75}
                    style={{
                      paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20, borderWidth: 1,
                      borderColor: active ? (isMonochromeDark ? colors.textPrimary : (isLightAppearance ? colors.inputBorder : colors.accent)) : colors.border,
                      backgroundColor: active
                        ? (isMonochromeDark ? '#181818' : (isLightAppearance ? colors.inputBg : colors.accent + '22'))
                        : colors.cardBg,
                    }}
                  >
                    <Text style={{
                      fontSize: 13,
                      fontWeight: active ? '700' : '600',
                      color: active ? (isMonochromeDark ? colors.textPrimary : (isLightAppearance ? colors.textPrimary : colors.accentSoft)) : colors.textPrimary,
                    }}>
                      {label}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          )}
        </View>

        {useGlassDetailLayout && type === 'tv' && showSeasonsPanel && (
          <View style={{ paddingHorizontal: 14, paddingTop: 10, paddingBottom: 0 }}>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.seasonPicker} contentContainerStyle={{ paddingHorizontal: 14, flexGrow: 1, justifyContent: 'center' }}>
              {(media.seasons || []).map((s: any) => (
                <TouchableOpacity
                  key={s.season_number}
                  style={[styles.seasonChip, selectedSeason === s.season_number && styles.seasonChipActive]}
                  onPress={() => setSelectedSeason(s.season_number)}
                >
                  <Text style={[styles.seasonChipText, selectedSeason === s.season_number && styles.seasonChipTextActive]}>
                    S{s.season_number}
                  </Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
            <View style={styles.seasonShelfHeader}>
              <View style={styles.seasonShelfHeaderRow}>
                <View style={styles.seasonShelfHeaderInfo}>
                  <Text style={styles.seasonShelfTitle} numberOfLines={1}>
                    {selectedSeasonInfo?.name || `Season ${selectedSeason}`}
                  </Text>
                  <Text style={styles.seasonShelfMeta}>
                    {episodesLoading
                    ? t('media_loading_episodes')
                    : t('media_episodes_swipe_browse', {
                        n: episodes.length || selectedSeasonInfo?.episode_count || 0,
                        suffix: (episodes.length || selectedSeasonInfo?.episode_count || 0) === 1 ? '' : 's',
                      })}
                  </Text>
                </View>
                {selectedSeasonEpisodeCount > 0 ? (
                  <TouchableOpacity
                    activeOpacity={0.82}
                    onPress={() => { void handleToggleSeasonWatched(); }}
                    style={[styles.seasonWatchBtn, selectedSeasonWatched && styles.seasonWatchBtnActive]}
                  >
                    <Ionicons
                      name={selectedSeasonWatched ? 'checkmark-circle' : 'checkmark-circle-outline'}
                      size={14}
                      color={selectedSeasonWatched ? (isLightAppearance ? '#1b5e20' : '#00e676') : colors.mutedText}
                    />
                    <Text style={[styles.seasonWatchBtnText, selectedSeasonWatched && styles.seasonWatchBtnTextActive]}>
                    {selectedSeasonWatched ? t('media_season_watched') : t('media_mark_season_watched')}
                    </Text>
                  </TouchableOpacity>
                ) : null}
              </View>
            </View>
            {episodesLoading ? (
              <ActivityIndicator color={colors.accent} style={{ margin: 30 }} />
            ) : (
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                style={styles.episodeShelf}
                contentContainerStyle={styles.episodeShelfContent}
              >
                {episodes.map(ep => {
                  const epWatched = isEpisodeWatched(Number(movieId), selectedSeason, ep.episode_number);
                  const epProgress = getEpisodeProgressPercent(selectedSeason, ep);
                  const epReleased = formatReleaseDate(ep.air_date);
                  const epRuntime = formatRuntimeButton(Number(ep.runtime ?? media?.runtime ?? 0));
                  const epMeta = [epReleased, epRuntime].filter(Boolean).join(' - ');
                  const shouldBlurStill = !epWatched;
                  const isUnairedEpisode = isFutureDate(ep.air_date);
                  return (
                    <TouchableOpacity
                      key={ep.id}
                      style={styles.episodeCard}
                      activeOpacity={0.8}
                      onLongPress={() => setEpSheetEp(ep)}
                      delayLongPress={350}
                      onPress={() => navigation.navigate('EpisodeStreams', {
                        showId:          movieId,
                        showTitle:       media.title,
                        showPoster:      media.poster,
                        showBackdrop:    detailHeroUri ?? media.backdrop,
                        imdbId:          media.imdbId ?? null,
                        season:          selectedSeason,
                        episodeNumber:   ep.episode_number,
                        episodeName:     ep.name,
                        episodeOverview: ep.overview ?? null,
                        episodeStill:    ep.still ?? null,
                        episodeReleaseDate: ep.air_date ?? null,
                        episodeRuntime:  ep.runtime ?? media?.runtime ?? null,
                        progressKey: episodeProgressKey(Number(movieId), selectedSeason, ep.episode_number),
                      })}
                    >
                      <View style={styles.episodeCardStillWrap}>
                        {ep.still ? (
                          <>
                            <Image source={{ uri: ep.still }} style={styles.episodeCardStill} blurRadius={shouldBlurStill ? 10 : 0} />
                            {shouldBlurStill && (
                              <>
                                <BlurView intensity={28} tint="dark" style={styles.episodeStillBlur} />
                                <View style={styles.episodeStillShade} />
                              </>
                            )}
                          </>
                        ) : (
                          <View style={[styles.episodeCardStill, styles.epStillPlaceholder]}>
                            <Text style={{ fontSize: 20 }}>ÃƒÂ°Ã…Â¸Ã¢â‚¬Å“Ã‚Âº</Text>
                          </View>
                        )}
                      </View>
                      <View style={styles.episodeCardInfo}>
                        <View style={styles.episodeCardTopRow}>
                          <Text style={styles.episodeCardCode}>E{ep.episode_number}</Text>
                          {epWatched ? (
                            <View style={styles.episodeCardWatchBadge}>
                              <Ionicons name="checkmark" size={13} color="#000" />
                            </View>
                          ) : null}
                        </View>
                        <Text style={styles.episodeCardTitle} numberOfLines={2}>{ep.name}</Text>
                        {epMeta ? (
                          <Text style={styles.episodeCardMeta} numberOfLines={1}>{epMeta}</Text>
                        ) : null}
                        <Text style={styles.episodeCardOverview} numberOfLines={2}>
                          {isUnairedEpisode ? t('media_not_released_yet') : ep.overview}
                        </Text>
                        {epProgress != null && epProgress > 0 && !isUnairedEpisode && (
                          <View style={styles.episodeProgressTrack}>
                            <View style={[styles.episodeProgressFill, { width: `${epProgress}%` as any }]} />
                          </View>
                        )}
                      </View>
                    </TouchableOpacity>
                  );
                })}
              </ScrollView>
            )}
          </View>
        )}

        <Animated.ScrollView
          key={activeTab}
          ref={scrollViewRef}
          showsVerticalScrollIndicator={false}
          bounces
          style={styles.tabContent}
          contentContainerStyle={{ paddingBottom: BOTTOM_NAV_HEIGHT + 60, paddingHorizontal: 14 }}
          scrollEnabled={false}
          nestedScrollEnabled={false}
          onScroll={Animated.event([{ nativeEvent: { contentOffset: { y: detailScrollY } } }], { useNativeDriver: true })}
          scrollEventThrottle={16}
        >
          {activeTab === 'about' && (
            <View>
              {!useGlassDetailLayout && (
              <View style={styles.descriptionContainer}>
                <Text 
                  style={styles.overview}
                  numberOfLines={isDescriptionExpanded ? undefined : 3}
                >
                  {media.description || t('media_no_description')}
                </Text>
                
                {/* Hidden text for measurement */}
                {!showDescriptionMore && (
                  <Text
                    style={[styles.overview, { position: 'absolute', opacity: 0 }]}
                    onTextLayout={(e) => {
                      if (e.nativeEvent.lines.length > 3) {
                        setShowDescriptionMore(true);
                      }
                    }}
                  >
                    {media.description || t('media_no_description')}
                  </Text>
                )}

                {showDescriptionMore && (
                  <TouchableOpacity 
                    onPress={() => setIsDescriptionExpanded(!isDescriptionExpanded)}
                    style={styles.moreButton}
                  >
                    <Text style={styles.moreButtonText}>
                      {isDescriptionExpanded ? t('media_less') : t('media_more')}
                    </Text>
                  </TouchableOpacity>
                )}
              </View>
              )}

              {!useGlassDetailLayout && movieReleaseDate && (
                <View style={styles.metaSection}>
                  <Text style={styles.featuredSectionHeading}>Movie details</Text>
                  <View style={styles.metaGrid}>
                    <View style={styles.metaCard}>
                      <Text style={styles.metaCardLabel}>Release date</Text>
                      <Text style={styles.metaCardValue}>{movieReleaseDate}</Text>
                    </View>
                    {media.status ? (
                      <View style={styles.metaCard}>
                        <Text style={styles.metaCardLabel}>Status</Text>
                        <Text style={styles.metaCardValue}>{media.status}</Text>
                      </View>
                    ) : null}
                    {(media.runtime ?? 0) > 0 ? (
                      <View style={styles.metaCard}>
                        <Text style={styles.metaCardLabel}>Duration</Text>
                        <Text style={styles.metaCardValue}>{formatRuntimeButton(media.runtime)}</Text>
                      </View>
                    ) : null}
                  </View>
                </View>
              )}

              {/* Cast marquee ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â shown inline under the description */}
              {!useGlassDetailLayout && media.cast?.length > 0 && (
                <View style={{ marginTop: 20 }}>
                  <Text style={styles.featuredSectionHeading}>{t('media_cast')}</Text>
                  <FlatList
                    horizontal
                    data={media.cast}
                    keyExtractor={(c: any) => String(c.id)}
                    showsHorizontalScrollIndicator={false}
                    style={{ marginHorizontal: -14 }}
                    contentContainerStyle={{ paddingHorizontal: 14, paddingTop: 10, paddingBottom: 4 }}
                    renderItem={({ item }: { item: any }) => (
                      <TouchableOpacity
                        style={styles.castCard}
                        activeOpacity={0.72}
                        onPress={() => navigation.navigate('PersonDetail', { personId: item.id, name: item.name, photo: item.photo })}
                      >
                        {item.photo ? (
                          <Image source={{ uri: item.photo }} style={styles.castPhoto} />
                        ) : (
                          <View style={[styles.castPhoto, styles.castNoPhoto]}>
                            <Text style={{ fontSize: 24 }}>ÃƒÂ°Ã…Â¸Ã…Â½Ã‚Â­</Text>
                          </View>
                        )}
                        <Text style={styles.castName} numberOfLines={1}>{item.name}</Text>
                        <Text style={styles.castChar} numberOfLines={1}>{item.character}</Text>
                      </TouchableOpacity>
                    )}
                  />
                </View>
              )}

              {!useGlassDetailLayout && availableProviders.length > 0 && (
                <View style={styles.whereSection}>
                  <View style={styles.whereDivider} />
                  <Text style={styles.featuredSectionHeading}>Available On</Text>

                  <View style={styles.whereGroup}>
                    <ScrollView
                      horizontal
                      showsHorizontalScrollIndicator={false}
                      style={{ marginHorizontal: -14 }}
                      contentContainerStyle={styles.providerPillsRow}
                    >
                      {availableProviders.map((provider: any) => {
                        const initials = String(provider.name ?? '')
                          .split(/\s+/)
                          .slice(0, 2)
                          .map((part: string) => part[0] ?? '')
                          .join('')
                          .toUpperCase() || 'TV';
                        return (
                          <TouchableOpacity
                            key={provider.id}
                            style={styles.providerPill}
                            activeOpacity={0.78}
                            onPress={() => { void openProvider(provider); }}
                          >
                            {provider.logo ? (
                              <Image source={{ uri: provider.logo }} style={styles.providerPillLogo} contentFit="contain" transition={180} />
                            ) : (
                              <View style={styles.providerPillLogoFallback}>
                                <Text style={styles.providerPillLogoText} numberOfLines={1}>{initials}</Text>
                              </View>
                            )}
                            <Text style={styles.providerPillName} numberOfLines={1}>{provider.name}</Text>
                          </TouchableOpacity>
                        );
                      })}
                    </ScrollView>
                  </View>
                </View>
              )}

              {useGlassDetailLayout && showStreamsTab && (
                <View style={[styles.relatedSection, styles.glassSectionSpacing]}>
                  <Text style={styles.featuredSectionHeading}>{t('media_streams')}</Text>
                  <View style={styles.glassStreamsViewport}>
                    {glassStreamsLoading ? (
                      <View style={{ minHeight: 148, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16 }}>
                        <ActivityIndicator color={colors.accent} size="small" />
                        <Text style={{ color: colors.subText, fontSize: 13, marginTop: 12, textAlign: 'center' }}>
                          {t('media_searching_addons').replace('{n}', String(sourceCount))}
                        </Text>
                      </View>
                    ) : glassStreamsSettledEmpty ? (
                      <View style={{ minHeight: 148, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16 }}>
                        <Ionicons name="cloud-offline-outline" size={28} color={colors.placeholder} style={{ marginBottom: 10 }} />
                        <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '700', marginBottom: 6, textAlign: 'center' }}>
                          No streams found
                        </Text>
                        <Text style={{ color: colors.subText, fontSize: 12, lineHeight: 18, textAlign: 'center' }}>
                          None of your enabled addons returned results for this title.
                        </Text>
                      </View>
                    ) : (
                      <StreamsTab
                        key={`glass-streams-${selectedAddon}`}
                        streams={streams}
                        loading={false}
                        pendingCount={streamsPending}
                        addons={addons}
                        ultraActive={ultraActive}
                        debridAccounts={debridAccounts}
                        colors={colors}
                        onPlay={playStream}
                        user={user}
                        navigation={navigation}
                        selectedAddon={selectedAddon}
                        onFilterChange={setSelectedAddon}
                        t={t}
                        isLightAppearance={isLightAppearance}
                        presentation="rail"
                      />
                    )}
                  </View>
                </View>
              )}

              {useGlassDetailLayout && media.cast?.length > 0 && (
                <View style={styles.glassCastSection}>
                  <Text style={styles.featuredSectionHeading}>{t('media_cast')}</Text>
                  <FlatList
                    horizontal
                    data={media.cast}
                    keyExtractor={(c: any) => String(c.id)}
                    showsHorizontalScrollIndicator={false}
                    style={{ marginHorizontal: -14 }}
                    contentContainerStyle={{ paddingHorizontal: 14, paddingTop: 10, paddingBottom: 4 }}
                    renderItem={({ item }: { item: any }) => (
                      <TouchableOpacity
                        style={[styles.castCard, styles.glassCastCard]}
                        activeOpacity={0.72}
                        onPress={() => navigation.navigate('PersonDetail', { personId: item.id, name: item.name, photo: item.photo })}
                      >
                        {item.photo ? (
                          <Image source={{ uri: item.photo }} style={[styles.castPhoto, styles.glassCastPhoto]} />
                        ) : (
                          <View style={[styles.castPhoto, styles.castNoPhoto, styles.glassCastPhoto]}>
                            <Text style={{ fontSize: 24 }}>ÃƒÂ°Ã…Â¸Ã…Â½Ã‚Â­</Text>
                          </View>
                        )}
                        <Text style={styles.castName} numberOfLines={1}>{item.name}</Text>
                        <Text style={styles.castChar} numberOfLines={1}>{item.character}</Text>
                      </TouchableOpacity>
                    )}
                  />
                </View>
              )}

              {useGlassDetailLayout && availableProviders.length > 0 && (
                <View style={styles.whereSection}>
                  <View style={styles.whereDivider} />
                  <Text style={styles.featuredSectionHeading}>Available On</Text>
                  <View style={styles.whereGroup}>
                    <ScrollView
                      horizontal
                      showsHorizontalScrollIndicator={false}
                      style={{ marginHorizontal: -14 }}
                      contentContainerStyle={styles.providerPillsRow}
                    >
                      {availableProviders.map((provider: any) => {
                        const initials = String(provider.name ?? '')
                          .split(/\s+/)
                          .slice(0, 2)
                          .map((part: string) => part[0] ?? '')
                          .join('')
                          .toUpperCase() || 'TV';
                        return (
                          <TouchableOpacity
                            key={provider.id}
                            style={styles.providerPill}
                            activeOpacity={0.78}
                            onPress={() => { void openProvider(provider); }}
                          >
                            {provider.logo ? (
                              <Image source={{ uri: provider.logo }} style={styles.providerPillLogo} contentFit="contain" transition={180} />
                            ) : (
                              <View style={styles.providerPillLogoFallback}>
                                <Text style={styles.providerPillLogoText} numberOfLines={1}>{initials}</Text>
                              </View>
                            )}
                            <Text style={styles.providerPillName} numberOfLines={1}>{provider.name}</Text>
                          </TouchableOpacity>
                        );
                      })}
                    </ScrollView>
                  </View>
                </View>
              )}

              {traktComments.length > 0 && (
                <View style={styles.relatedSection}>
                  <Text style={styles.featuredSectionHeading}>Trakt Comments</Text>
                  <ScrollView
                    horizontal
                    showsHorizontalScrollIndicator={false}
                    style={{ marginHorizontal: -14 }}
                    contentContainerStyle={styles.commentRow}
                  >
                    {traktComments.map((comment) => (
                      <View key={comment.id} style={styles.commentCard}>
                        <View style={styles.commentCardHeader}>
                          <Text style={styles.commentAuthor} numberOfLines={1}>{comment.author}</Text>
                          {comment.userRating != null && (
                            <View style={styles.commentRatingPill}>
                              <Text style={styles.commentRatingText}>Rating {comment.userRating}/10</Text>
                            </View>
                          )}
                        </View>
                        <Text style={styles.commentBody} numberOfLines={5}>
                          {comment.comment}
                        </Text>
                        <View style={styles.commentFooter}>
                          <Text style={styles.commentFooterText}>
                            {comment.likes} {comment.likes === 1 ? 'like' : 'likes'}
                          </Text>
                          {comment.replies > 0 && (
                            <Text style={styles.commentFooterText}>
                              {comment.replies} {comment.replies === 1 ? 'reply' : 'replies'}
                            </Text>
                          )}
                        </View>
                      </View>
                    ))}
                  </ScrollView>
                </View>
              )}

              {(media.similarTitles?.length ?? 0) > 0 && (
                <View style={styles.relatedSection}>
                  <Text style={styles.featuredSectionHeading}>{t('media_more_like_this')}</Text>
                  <ScrollView
                    horizontal
                    showsHorizontalScrollIndicator={false}
                    style={{ marginHorizontal: -14 }}
                    contentContainerStyle={{ paddingLeft: 14, paddingRight: 14 }}
                  >
                    {(media.similarTitles ?? []).map((item: any) => (
                      <TouchableOpacity
                        key={`${item.type}-${item.id}`}
                        style={styles.relatedCard}
                        activeOpacity={0.82}
                        onPress={() => navigation.push('Detail', { movieId: item.id, type: item.type, imdbId: item.imdbId ?? undefined, title: item.title, synopsis: item.description ?? undefined, backdrop: item.backdrop ?? item.poster ?? undefined, poster: item.poster ?? undefined, year: item.year, rating: item.rating })}
                      >
                        {item.poster ? (
                          <Image source={{ uri: item.poster }} style={styles.relatedPoster} />
                        ) : (
                          <View style={[styles.relatedPoster, styles.relatedPosterPlaceholder]}>
                            <Text style={{ fontSize: 28 }}>ÃƒÂ°Ã…Â¸Ã…Â½Ã‚Â¬</Text>
                          </View>
                        )}
                        <Text style={styles.relatedTitle} numberOfLines={2}>{item.title}</Text>
                        <Text style={styles.relatedYear}>{item.year || ' '}</Text>
                      </TouchableOpacity>
                    ))}
                  </ScrollView>
                </View>
              )}
            </View>
          )}

          {activeTab === 'seasons' && type === 'tv' && !useGlassDetailLayout && (
            <View>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.seasonPicker} contentContainerStyle={{ paddingHorizontal: 14 }}>
                {(media.seasons || []).map((s: any) => (
                  <TouchableOpacity
                    key={s.season_number}
                    style={[styles.seasonChip, selectedSeason === s.season_number && styles.seasonChipActive]}
                    onPress={() => setSelectedSeason(s.season_number)}
                  >
                    <Text style={[styles.seasonChipText, selectedSeason === s.season_number && styles.seasonChipTextActive]}>
                      S{s.season_number}
                    </Text>
                  </TouchableOpacity>
                ))}
              </ScrollView>
              <View style={styles.seasonShelfHeader}>
                <View style={styles.seasonShelfHeaderRow}>
                  <View style={styles.seasonShelfHeaderInfo}>
                    <Text style={styles.seasonShelfTitle} numberOfLines={1}>
                      {selectedSeasonInfo?.name || `Season ${selectedSeason}`}
                    </Text>
                    <Text style={styles.seasonShelfMeta}>
                      {episodesLoading
                    ? t('media_loading_episodes')
                    : t('media_episodes_swipe_browse', {
                        n: episodes.length || selectedSeasonInfo?.episode_count || 0,
                        suffix: (episodes.length || selectedSeasonInfo?.episode_count || 0) === 1 ? '' : 's',
                      })}
                    </Text>
                  </View>
                  {selectedSeasonEpisodeCount > 0 ? (
                    <TouchableOpacity
                      activeOpacity={0.82}
                      onPress={() => { void handleToggleSeasonWatched(); }}
                      style={[styles.seasonWatchBtn, selectedSeasonWatched && styles.seasonWatchBtnActive]}
                    >
                      <Ionicons
                        name={selectedSeasonWatched ? 'checkmark-circle' : 'checkmark-circle-outline'}
                        size={14}
                        color={selectedSeasonWatched ? (isLightAppearance ? '#1b5e20' : '#00e676') : colors.mutedText}
                      />
                      <Text style={[styles.seasonWatchBtnText, selectedSeasonWatched && styles.seasonWatchBtnTextActive]}>
                    {selectedSeasonWatched ? t('media_season_watched') : t('media_mark_season_watched')}
                      </Text>
                    </TouchableOpacity>
                  ) : null}
                </View>
              </View>
              {episodesLoading ? (
                <ActivityIndicator color={colors.accent} style={{ margin: 30 }} />
                ) : (
                  <ScrollView
                    horizontal
                    showsHorizontalScrollIndicator={false}
                    style={styles.episodeShelf}
                    contentContainerStyle={styles.episodeShelfContent}
                  >
                  {episodes.map(ep => {
                    const epWatched = isEpisodeWatched(Number(movieId), selectedSeason, ep.episode_number);
                    const epProgress = getEpisodeProgressPercent(selectedSeason, ep);
                    const epReleased = formatReleaseDate(ep.air_date);
                    const epRuntime = formatRuntimeButton(Number(ep.runtime ?? media?.runtime ?? 0));
                  const epMeta = [epReleased, epRuntime].filter(Boolean).join(' ? ');
                    const shouldBlurStill = !epWatched;
                    const isUnairedEpisode = isFutureDate(ep.air_date);
                    return (
                      <TouchableOpacity
                        key={ep.id}
                        style={styles.episodeCard}
                        activeOpacity={0.8}
                        onLongPress={() => setEpSheetEp(ep)}
                        delayLongPress={350}
                      onPress={() => navigation.navigate('EpisodeStreams', {
                        showId:         movieId,
                        showTitle:      media.title,
                        showPoster:     media.poster,
                        showBackdrop:   detailHeroUri ?? media.backdrop,
                        imdbId:         media.imdbId ?? null,
                        season:         selectedSeason,
                        episodeNumber:  ep.episode_number,
                        episodeName:    ep.name,
                        episodeOverview: ep.overview ?? null,
                        episodeStill:   ep.still ?? null,
                        episodeReleaseDate: ep.air_date ?? null,
                        episodeRuntime: ep.runtime ?? media?.runtime ?? null,
                        progressKey: episodeProgressKey(Number(movieId), selectedSeason, ep.episode_number),
                      })}
                      >
                        <View style={styles.episodeCardStillWrap}>
                          {ep.still ? (
                            <>
                              <Image source={{ uri: ep.still }} style={styles.episodeCardStill} blurRadius={shouldBlurStill ? 10 : 0} />
                              {shouldBlurStill && (
                                <>
                                  <BlurView intensity={28} tint="dark" style={styles.episodeStillBlur} />
                                  <View style={styles.episodeStillShade} />
                                </>
                              )}
                            </>
                          ) : (
                            <View style={[styles.episodeCardStill, styles.epStillPlaceholder]}>
                              <Text style={{ fontSize: 20 }}>ÃƒÂ°Ã…Â¸Ã¢â‚¬Å“Ã‚Âº</Text>
                          </View>
                        )}
                      </View>
                      <View style={styles.episodeCardInfo}>
                        <View style={styles.episodeCardTopRow}>
                          <Text style={styles.episodeCardCode}>E{ep.episode_number}</Text>
                          {epWatched ? (
                            <View style={styles.episodeCardWatchBadge}>
                              <Ionicons name="checkmark" size={13} color="#000" />
                            </View>
                          ) : null}
                        </View>
                        <Text style={styles.episodeCardTitle} numberOfLines={2}>{ep.name}</Text>
                        {epMeta ? (
                          <Text style={styles.episodeCardMeta} numberOfLines={1}>{epMeta}</Text>
                        ) : null}
                        <Text style={styles.episodeCardOverview} numberOfLines={2}>
                          {isUnairedEpisode ? t('media_not_released_yet') : ep.overview}
                        </Text>
                        {epProgress != null && epProgress > 0 && !isUnairedEpisode && (
                          <View style={styles.episodeProgressTrack}>
                            <View style={[styles.episodeProgressFill, { width: `${epProgress}%` as any }]} />
                          </View>
                        )}
                      </View>
                    </TouchableOpacity>
                  );
                })}
                </ScrollView>
              )}
            </View>
          )}

          {activeTab === 'streams' && (
            <StreamsTab
              key={`streams-${selectedAddon}`}
              streams={streams}
              loading={streamsLoading}
              pendingCount={streamsPending}
              addons={addons}
              ultraActive={ultraActive}
              debridAccounts={debridAccounts}
              colors={colors}
              onPlay={playStream}
              user={user}
              navigation={navigation}
              selectedAddon={selectedAddon}
              onFilterChange={setSelectedAddon}
              t={t}
              isLightAppearance={resolvedAppearance === 'light'}
            />
          )}

        </Animated.ScrollView>
      </View>
        </PageWrapper>
        {!useGlassDetailLayout ? (
          <GlassBackButton
            onPress={() => navigation.goBack()}
            top={insets.top + 10}
            left={16}
            zIndex={40}
            blurTarget={blurTargetRef}
            iconColor={isLightAppearance ? colors.textPrimary : '#fff'}
          />
        ) : null}
      </BlurTargetView>
      <StackBottomNav blurTarget={blurTargetRef} />
    </View>
  );
};

// ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Streams Tab Component ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬

const INITIAL_STREAM_RENDER_LIMIT = 30;
const STREAM_RENDER_LIMIT_STEP = 50;
const RAIL_STREAM_RENDER_LIMIT = 15;

function StreamsTab({
  streams, loading, pendingCount, addons, ultraActive, debridAccounts, colors, onPlay, user, navigation,
  selectedAddon, onFilterChange, t, isLightAppearance, presentation = 'list',
}: {
  streams: AddonStream[];
  loading: boolean;
  pendingCount: number;
  addons: any[];
  ultraActive: boolean;
  debridAccounts: any[];
  colors: any;
  onPlay: (stream: AddonStream) => void;
  user: any;
  navigation: any;
  selectedAddon: string;
  onFilterChange: (addon: string) => void;
  t: (key: any) => string; // Add t to props
  isLightAppearance: boolean;
  presentation?: 'list' | 'rail';
}) {
  // StreamsTab only renders for movies ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â count movie-capable sources.
  const enabledAddons = getStreamCapableAddonsForType(addons, 'movie');
  const hasStreamSources = enabledAddons.length > 0 || ultraActive;
  const streamSourceCount = enabledAddons.length + (ultraActive ? 1 : 0);
  const getStreamSourceLabel = useMemo(() => {
    return (name: string) => {
      const normalized = name.trim().toLowerCase();
      if (normalized === 'ultra boost' || normalized === 'streamdek ultra' || normalized === 'sd ultra') {
        return 'SD ultra';
      }
      return name;
    };
  }, []);

  // Hooks must be called unconditionally ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â before any early returns
  const addonNames = useMemo(
    () => [...new Set(streams.map(s => s.addonName))],
    [streams],
  );
  // The detail page scrolls in a plain ScrollView (no virtualization), so
  // mounting hundreds of stream rows at once makes scrolling choppy. Render a
  // window and let the user reveal more.
  const [renderLimit, setRenderLimit] = useState(INITIAL_STREAM_RENDER_LIMIT);

  // Full spinner only while we have no results yet
  if (loading && streams.length === 0) {
    return (
      <View style={{ alignItems: 'center', justifyContent: 'center', minHeight: presentation === 'rail' ? 148 : undefined, paddingTop: presentation === 'rail' ? 0 : 40, paddingHorizontal: 16 }}>
        <ActivityIndicator color={colors.accent} size={presentation === 'rail' ? 'small' : 'large'} />
        <Text style={{ color: colors.subText, fontSize: 13, marginTop: 12 }}>
          {t('media_searching_addons').replace('{n}', String(streamSourceCount))}
        </Text>
      </View>
    );
  }

  // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Not logged in ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬
  if (!user && !hasStreamSources) {
    return (
      <View style={{ alignItems: 'center', paddingHorizontal: 8, paddingTop: 32, paddingBottom: 24 }}>
        <View style={{
          width: 64, height: 64, borderRadius: 32, backgroundColor: colors.accent + '18',
          justifyContent: 'center', alignItems: 'center', marginBottom: 20,
        }}>
          <Ionicons name="extension-puzzle-outline" size={34} color={colors.textPrimary} />
        </View>
        <Text style={{ color: colors.textPrimary, fontSize: 17, fontWeight: '800', textAlign: 'center', marginBottom: 10 }}>
          {t('streams_no_addons_title')}
        </Text>
        <Text style={{ color: colors.subText, fontSize: 13, textAlign: 'center', lineHeight: 20, marginBottom: 28, maxWidth: 280 }}>
          {t('streams_no_addons_desc')}
        </Text>
        <TouchableOpacity
          onPress={() => navigation.navigate('Addons')}
          activeOpacity={0.85}
          style={{
            backgroundColor: colors.accent,
            borderRadius: 999,
            paddingVertical: 14,
            paddingHorizontal: 32,
            width: '100%',
            alignItems: 'center',
            marginBottom: 12,
            shadowColor: isLightAppearance ? '#000' : colors.accent,
            shadowOpacity: isLightAppearance ? 0.16 : 0,
            shadowRadius: 4,
            shadowOffset: { width: 0, height: 1 },
            elevation: isLightAppearance ? 1 : 0,
          }}
        >
          <Text style={{ color: colors.buttonText, fontSize: 15, fontWeight: '800' }}>{t('streams_setup_addons_btn')}</Text>
        </TouchableOpacity>
        <Text style={{ color: colors.mutedText, fontSize: 11, textAlign: 'center', lineHeight: 17, maxWidth: 260 }}>
          {t('media_supports_debrid')}
        </Text>
      </View>
    );
  }

  // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Logged in but no addons installed ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬
  if (!hasStreamSources) {
    return (
      <View style={{ alignItems: 'center', paddingHorizontal: 8, paddingTop: 32, paddingBottom: 24 }}>
        <View style={{
          width: 64, height: 64, borderRadius: 32, backgroundColor: colors.accent + '18',
          justifyContent: 'center', alignItems: 'center', marginBottom: 20,
        }}>
          <Ionicons name="extension-puzzle-outline" size={32} color={colors.accent} />
        </View>
        <Text style={{ color: colors.textPrimary, fontSize: 17, fontWeight: '800', textAlign: 'center', marginBottom: 10 }}>
          No addons installed
        </Text>
        <Text style={{ color: colors.subText, fontSize: 13, textAlign: 'center', lineHeight: 20, marginBottom: 28, maxWidth: 280 }}>
          Addons are streaming sources that find links for movies and shows. Add one to start watching - compatible with any Stremio addon URL.
        </Text>
        <TouchableOpacity
          onPress={() => navigation.navigate('Addons')}
          activeOpacity={0.85}
          style={{
            backgroundColor: colors.accent, borderRadius: 14, paddingVertical: 14,
            paddingHorizontal: 32, width: '100%', alignItems: 'center', marginBottom: 16,
          }}
        >
          <Text style={{ color: colors.buttonText, fontSize: 15, fontWeight: '800' }}>Set Up Addons & Debrid</Text>
        </TouchableOpacity>
        <View style={{
          flexDirection: 'row', gap: 8, padding: 12, borderRadius: 10, width: '100%',
          backgroundColor: colors.cardBg, borderWidth: 1, borderColor: colors.border,
        }}>
          <Ionicons name="information-circle-outline" size={15} color={colors.mutedText} style={{ marginTop: 1 }} />
          <Text style={{ flex: 1, color: colors.mutedText, fontSize: 11, lineHeight: 17 }}>
            Popular sources include Torrentio and AIOStreams. Pair with a Debrid service to get instant, buffer-free streams.
          </Text>
        </View>
      </View>
    );
  }

  // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Addons configured but no results for this title ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬
  if (streams.length === 0) {
    return (
      <View style={{ alignItems: 'center', paddingHorizontal: 8, paddingTop: 32, paddingBottom: 24 }}>
        <Ionicons name="cloud-offline-outline" size={40} color={colors.placeholder} style={{ marginBottom: 16 }} />
        <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700', marginBottom: 8, textAlign: 'center' }}>
          No streams found
        </Text>
        <Text style={{ color: colors.subText, fontSize: 13, textAlign: 'center', lineHeight: 20, marginBottom: 20, maxWidth: 280 }}>
          None of your {streamSourceCount} source{streamSourceCount !== 1 ? 's' : ''} returned results for this title. It may not be indexed yet, or try adding more sources.
        </Text>
        <TouchableOpacity
          onPress={() => navigation.navigate('Addons')}
          activeOpacity={0.85}
          style={{
            borderRadius: 12, paddingVertical: 11, paddingHorizontal: 24,
            borderWidth: 1, borderColor: colors.accent + '66', backgroundColor: colors.accent + '12',
          }}
        >
          <Text style={{ color: colors.accentSoft, fontSize: 13, fontWeight: '700' }}>Manage Addons</Text>
        </TouchableOpacity>
      </View>
    );
  }

  // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Streams available ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬

  // Reset filter if the previously selected addon no longer has results
  const safeAddon = addonNames.includes(selectedAddon) ? selectedAddon : 'all';

  const visibleStreams = safeAddon === 'all'
    ? streams
    : streams.filter(s => s.addonName === safeAddon);

  const sortedVisibleStreams = visibleStreams;
  const limitedStreams = sortedVisibleStreams.slice(0, renderLimit);
  const hiddenStreamCount = sortedVisibleStreams.length - limitedStreams.length;

  // Group visible streams by addon name
  const grouped: Record<string, AddonStream[]> = {};
  for (const s of limitedStreams) {
    if (!grouped[s.addonName]) grouped[s.addonName] = [];
    grouped[s.addonName].push(s);
  }

  if (presentation === 'rail') {
    return (
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator
        style={{ maxHeight: 166 }}
        contentContainerStyle={{ paddingHorizontal: 12, paddingRight: 20, paddingVertical: 10, alignItems: 'stretch' }}
        nestedScrollEnabled
      >
        {pendingCount > 0 && (
          <View style={{
            width: 154,
            height: 144,
            marginRight: 12,
            borderRadius: 12,
            borderWidth: 1,
            borderColor: colors.border,
            backgroundColor: colors.inputBg,
            alignItems: 'center',
            justifyContent: 'center',
            paddingHorizontal: 14,
          }}>
            <ActivityIndicator size="small" color={colors.accent} />
            <Text style={{ color: colors.subText, fontSize: 12, lineHeight: 17, marginTop: 10, textAlign: 'center' }}>
              Loading more streams...
            </Text>
          </View>
        )}
        {sortedVisibleStreams.slice(0, RAIL_STREAM_RENDER_LIMIT).map((stream, idx) => (
          <View key={`${stream.addonName}-${stream.infoHash ?? stream.url ?? idx}`} style={{ width: Math.min(SCREEN_WIDTH * 0.82, 330), height: 144, marginRight: 12 }}>
            <StreamRow
              stream={stream}
              colors={colors}
              onPlay={onPlay}
              style={railStreamRowStyle}
              sourceLabel={getStreamSourceLabel(stream.addonName)}
            />
          </View>
        ))}
      </ScrollView>
    );
  }

  return (
    <View>
      {/* Inline "still searching" banner while more addons are responding */}
      {pendingCount > 0 && (
        <View style={{
          flexDirection: 'row', alignItems: 'center', gap: 10,
          paddingHorizontal: 14, paddingVertical: 10, borderRadius: 10,
          backgroundColor: colors.cardBg, borderWidth: 1, borderColor: colors.border,
          marginBottom: 14,
        }}>
          <ActivityIndicator size="small" color={colors.accent} />
          <Text style={{ color: colors.subText, fontSize: 12, flex: 1 }}>
            Searching {pendingCount} more addon{pendingCount !== 1 ? 's' : ''}...
          </Text>
        </View>
      )}

      {debridAccounts.length === 0 && (
        <TouchableOpacity
          onPress={() => navigation.navigate('Addons', { initialTab: 'debrid' })}
          activeOpacity={0.85}
          style={{
            flexDirection: 'row', gap: 10, padding: 13, borderRadius: 12, marginBottom: 16,
            backgroundColor: '#f5a62314', borderWidth: 1, borderColor: '#f5a62344',
            alignItems: 'center',
          }}
        >
          <Ionicons name="flash-outline" size={16} color="#f5a623" />
          <View style={{ flex: 1 }}>
            <Text style={{ color: '#f5a623', fontSize: 12, fontWeight: '700', marginBottom: 2 }}>
              Unlock instant streams
            </Text>
            <Text style={{ color: '#f5a623cc', fontSize: 11, lineHeight: 16 }}>
              Connect a Debrid service to get cached, buffer-free links. Tap to set up.
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={14} color="#f5a623" />
        </TouchableOpacity>
      )}

      {Object.entries(grouped).map(([addonName, addonStreams]) => (
        <View key={addonName} style={{ marginBottom: 20 }}>
          {addonStreams.map((stream, idx) => (
            <StreamRow key={`${addonName}-${idx}`} stream={stream} colors={colors} onPlay={onPlay} />
          ))}
        </View>
      ))}

      {hiddenStreamCount > 0 && (
        <TouchableOpacity
          onPress={() => setRenderLimit(limit => limit + STREAM_RENDER_LIMIT_STEP)}
          activeOpacity={0.85}
          style={{
            alignItems: 'center', paddingVertical: 12, borderRadius: 12, marginBottom: 16,
            borderWidth: 1, borderColor: colors.accent + '55', backgroundColor: colors.accent + '10',
          }}
        >
          <Text style={{ color: colors.accentSoft, fontSize: 13, fontWeight: '700' }}>
            Show {Math.min(STREAM_RENDER_LIMIT_STEP, hiddenStreamCount)} more sources ({hiddenStreamCount} hidden)
          </Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

// Hoisted so the rail rows keep a stable style reference for memoization.
const railStreamRowStyle = { flex: 1, height: '100%' as const, marginBottom: 0, alignItems: 'flex-start' as const, overflow: 'hidden' as const };

const StreamRow = React.memo(function StreamRow({ stream, colors, onPlay, style, sourceLabel }: { stream: AddonStream; colors: any; onPlay: (stream: AddonStream) => void; style?: any; sourceLabel?: string }) {
  const handlePress = React.useCallback(() => onPlay(stream), [onPlay, stream]);
  return <StreamSourceRow stream={stream} colors={colors} onPress={handlePress} style={style} sourceLabel={sourceLabel} />;
});




































































