import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator, Animated, Modal, Pressable,
  ScrollView, StyleSheet, Text, TouchableOpacity, View,
} from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../../context/ThemeContext';
import { useWatched } from '../../context/WatchedContext';
import { tmdbFetch } from '../../utils/tmdbFetch';

interface SeasonInfo {
  season_number: number;
  name: string;
}

interface EpisodeInfo {
  episode_number: number;
  name: string | null;
  overview: string | null;
  still: string | null;
  air_date: string | null;
}

type TmdbSeasonPayload = {
  seasons?: Array<{
    season_number?: number | string | null;
    seasonNumber?: number | string | null;
    name?: string | null;
  }> | null;
};

type TmdbEpisodePayload = {
  episodes?: Array<{
    episode_number?: number | string | null;
    episodeNumber?: number | string | null;
    name?: string | null;
    overview?: string | null;
    still?: string | null;
    still_path?: string | null;
    air_date?: string | null;
  }> | null;
};

export interface InPlayerEpisodesSheetProps {
  visible: boolean;
  showId: string | number;
  showTitle?: string | null;
  currentSeason: number;
  currentEpisode: number;
  onSelectEpisode: (season: number, episode: number) => void;
  onDismiss: () => void;
}

async function fetchJson<T>(path: string, signal?: AbortSignal): Promise<T | null> {
  try {
    const res = await tmdbFetch(path, signal ? { signal } : undefined);
    if (!res.ok) return null;
    return res.json();
  } catch {
    return null;
  }
}

export function InPlayerEpisodesSheet({
  visible,
  showId,
  showTitle,
  currentSeason,
  currentEpisode,
  onSelectEpisode,
  onDismiss,
}: InPlayerEpisodesSheetProps) {
  const { theme, resolvedAppearance } = useTheme();
  const { isEpisodeWatched } = useWatched();
  const isLight = resolvedAppearance === 'light';
  const insets = useSafeAreaInsets();
  const showIdNum = Number(showId);

  const [open, setOpen] = useState(false);
  const [seasons, setSeasons] = useState<SeasonInfo[]>([]);
  const [selectedSeason, setSelectedSeason] = useState(currentSeason);
  const [episodes, setEpisodes] = useState<EpisodeInfo[]>([]);
  const [loadingSeasons, setLoadingSeasons] = useState(false);
  const [loadingEpisodes, setLoadingEpisodes] = useState(false);

  const backdropAnim = useRef(new Animated.Value(0)).current;
  const cardAnim = useRef(new Animated.Value(60)).current;
  const abortRef = useRef<AbortController | null>(null);

  const cardBg = 'rgba(18,20,28,0.98)';
  const accent = theme.colors.accent;

  useEffect(() => {
    if (visible) {
      setOpen(true);
      setSelectedSeason(currentSeason);
      setSeasons([]);
      setEpisodes([]);
      setLoadingSeasons(true);
      setLoadingEpisodes(false);
      Animated.parallel([
        Animated.timing(backdropAnim, { toValue: 1, duration: 200, useNativeDriver: true }),
        Animated.spring(cardAnim, { toValue: 0, useNativeDriver: true, damping: 22, stiffness: 260 }),
      ]).start();
    } else {
      abortRef.current?.abort();
      abortRef.current = null;
      Animated.parallel([
        Animated.timing(backdropAnim, { toValue: 0, duration: 160, useNativeDriver: true }),
        Animated.timing(cardAnim, { toValue: 60, duration: 160, useNativeDriver: true }),
      ]).start(() => setOpen(false));
    }
  }, [visible]); // eslint-disable-line react-hooks/exhaustive-deps

  // Fetch seasons when sheet opens — no signal so it uses the cache and isn't
  // cancelled by the close animation aborting the shared abortRef.
  useEffect(() => {
    if (!visible || !showId) return;
    let cancelled = false;
    fetchJson<TmdbSeasonPayload>(`/tmdb/details/tv/${showId}`).then(data => {
      if (cancelled) return;
      const raw = Array.isArray(data?.seasons) ? data.seasons : [];
      const parsed: SeasonInfo[] = raw
        .map((s) => ({
          season_number: Number(s?.season_number ?? s?.seasonNumber ?? 0),
          name: String(s?.name ?? `Season ${s?.season_number ?? ''}`),
        }))
        .filter((season) => season.season_number > 0)
        .sort((a, b) => a.season_number - b.season_number);
      setSeasons(parsed);
      setLoadingSeasons(false);
    });
    return () => { cancelled = true; };
  }, [visible, showId]);

  // Fetch episodes when selected season changes
  useEffect(() => {
    if (!visible || !showId || !selectedSeason) return;
    let cancelled = false;
    setLoadingEpisodes(true);
    setEpisodes([]);
    fetchJson<TmdbEpisodePayload>(`/tmdb/season/${showId}/${selectedSeason}`).then(data => {
      if (cancelled) return;
      const raw = Array.isArray(data?.episodes) ? data.episodes : [];
      const parsed: EpisodeInfo[] = raw.map((ep) => ({
        episode_number: Number(ep?.episode_number ?? ep?.episodeNumber ?? 0),
        name: ep?.name ?? null,
        overview: ep?.overview ?? null,
        still: ep?.still ?? ep?.still_path ?? null,
        air_date: ep?.air_date ?? null,
      })).filter((episode) => episode.episode_number > 0);
      setEpisodes(parsed);
      setLoadingEpisodes(false);
    });
    return () => { cancelled = true; };
  }, [visible, showId, selectedSeason]);

  const handleEpisodeTap = useCallback((ep: EpisodeInfo) => {
    onSelectEpisode(selectedSeason, ep.episode_number);
  }, [onSelectEpisode, selectedSeason]);

  return (
    <Modal visible={open} transparent animationType="none" onRequestClose={onDismiss}>
      <Animated.View style={[StyleSheet.absoluteFillObject, { opacity: backdropAnim, backgroundColor: 'rgba(0,0,0,0.72)' }]}>
        <Pressable style={StyleSheet.absoluteFillObject} onPress={onDismiss} />
      </Animated.View>

      <Animated.View
        style={[
          styles.card,
          {
            backgroundColor: cardBg,
            bottom: insets.bottom + 20,
            left: 20,
            right: 20,
            transform: [{ translateY: cardAnim }],
          },
        ]}
      >
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.title}>{showTitle ? `${showTitle} — Episodes` : 'Episodes'}</Text>
          <TouchableOpacity style={styles.pillClose} onPress={onDismiss}>
            <Text style={styles.pillCloseText}>Close</Text>
          </TouchableOpacity>
        </View>

        {/* Season tabs */}
        {loadingSeasons ? (
          <ActivityIndicator color={accent} style={{ marginVertical: 12 }} />
        ) : seasons.length > 0 ? (
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.seasonTabRow}
            style={styles.seasonTabScroll}
          >
            {seasons.map(s => {
              const isActive = s.season_number === selectedSeason;
              return (
                <TouchableOpacity
                  key={s.season_number}
                  style={[
                    styles.seasonTab,
                    isActive && { backgroundColor: isLight ? '#111' : '#fff', borderColor: isLight ? '#111' : '#fff' },
                  ]}
                  onPress={() => setSelectedSeason(s.season_number)}
                  activeOpacity={0.75}
                >
                  <Text style={[
                    styles.seasonTabText,
                    isActive && { color: isLight ? '#fff' : '#111', fontWeight: '700' },
                  ]}>
                    {s.name}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        ) : null}

        {/* Episode list */}
        <ScrollView
          style={styles.episodeList}
          contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 16, paddingTop: 8 }}
          showsVerticalScrollIndicator={false}
        >
          {loadingEpisodes && (
            <ActivityIndicator color={accent} style={{ marginVertical: 24 }} />
          )}
          {!loadingEpisodes && (() => {
            const today = new Date().toISOString().split('T')[0];
            return episodes.map(ep => {
            const isPlaying = selectedSeason === currentSeason && ep.episode_number === currentEpisode;
            const isWatched = !isPlaying && showIdNum > 0 && isEpisodeWatched(showIdNum, selectedSeason, ep.episode_number);
            const isUnreleased = !ep.air_date || ep.air_date > today;
            const code = `S${String(selectedSeason).padStart(2, '0')}E${String(ep.episode_number).padStart(2, '0')}`;
            const unreleasedLabel = ep.air_date
              ? `Airs ${new Date(ep.air_date + 'T12:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`
              : 'Unreleased';
            return (
              <TouchableOpacity
                key={ep.episode_number}
                style={[
                  styles.episodeCard,
                  isPlaying && styles.episodeCardActive,
                  isWatched && styles.episodeCardWatched,
                  isUnreleased && styles.episodeCardUnreleased,
                ]}
                onPress={() => handleEpisodeTap(ep)}
                disabled={isUnreleased}
                activeOpacity={0.75}
              >
                {/* Thumbnail */}
                <View style={styles.thumbWrap}>
                  {ep.still ? (
                    <Image
                      source={{ uri: ep.still }}
                      style={[styles.thumb, isWatched && styles.thumbWatched, isUnreleased && styles.thumbUnreleased]}
                      contentFit="cover"
                    />
                  ) : (
                    <View style={[styles.thumb, styles.thumbFallback]}>
                      <Ionicons name="film-outline" size={24} color="rgba(255,255,255,0.3)" />
                    </View>
                  )}
                  {isWatched && (
                    <View style={styles.watchedOverlay}>
                      <Ionicons name="checkmark-circle" size={22} color="#00e676" />
                    </View>
                  )}
                  {isUnreleased && (
                    <View style={styles.unreleasedOverlay}>
                      <Ionicons name="lock-closed" size={18} color="rgba(255,152,0,0.9)" />
                    </View>
                  )}
                </View>

                {/* Info */}
                <View style={styles.epInfo}>
                  <View style={styles.epCodeRow}>
                    <Text style={[styles.epCode, isWatched && styles.epCodeWatched, isUnreleased && styles.epCodeUnreleased]}>{code}</Text>
                    {isPlaying && (
                      <View style={styles.playingBadge}>
                        <Text style={styles.playingBadgeText}>Playing</Text>
                      </View>
                    )}
                    {isWatched && (
                      <View style={styles.watchedBadge}>
                        <Ionicons name="checkmark" size={9} color="#00e676" />
                        <Text style={styles.watchedBadgeText}>Watched</Text>
                      </View>
                    )}
                    {isUnreleased && (
                      <View style={styles.unreleasedBadge}>
                        <Ionicons name="lock-closed" size={9} color="#ff9800" />
                        <Text style={styles.unreleasedBadgeText}>{unreleasedLabel}</Text>
                      </View>
                    )}
                  </View>
                  <Text style={[styles.epTitle, isWatched && styles.epTitleWatched, isUnreleased && styles.epTitleUnreleased]} numberOfLines={1}>{ep.name ?? code}</Text>
                  {!!ep.overview && (
                    <Text style={styles.epOverview} numberOfLines={2}>{ep.overview}</Text>
                  )}
                </View>
              </TouchableOpacity>
            );
          });
        })()}
        </ScrollView>
      </Animated.View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  card: {
    position: 'absolute',
    maxHeight: '72%',
    borderRadius: 20,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.12)',
    overflow: 'hidden',
    flexDirection: 'column',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(255,255,255,0.1)',
  },
  title: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '800',
  },
  pillClose: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 7,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.16)',
  },
  pillCloseText: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 13,
    fontWeight: '700',
  },
  seasonTabScroll: {
    flexShrink: 0,
    flexGrow: 0,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(255,255,255,0.08)',
  },
  seasonTabRow: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 8,
    flexDirection: 'row',
    alignItems: 'center',
  },
  seasonTab: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.22)',
    backgroundColor: 'transparent',
  },
  seasonTabText: {
    color: 'rgba(255,255,255,0.72)',
    fontSize: 13,
    fontWeight: '600',
  },
  episodeList: {
    flex: 1,
  },
  episodeCard: {
    flexDirection: 'row',
    gap: 14,
    padding: 12,
    borderRadius: 14,
    marginBottom: 2,
    backgroundColor: 'transparent',
  },
  episodeCardActive: {
    backgroundColor: 'rgba(255,255,255,0.07)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
  },
  episodeCardWatched: {
    opacity: 0.72,
  },
  episodeCardUnreleased: {
    opacity: 0.6,
  },
  thumbWrap: {
    width: 100,
    height: 64,
    borderRadius: 8,
    overflow: 'hidden',
    flexShrink: 0,
  },
  thumb: {
    width: '100%',
    height: '100%',
  },
  thumbFallback: {
    backgroundColor: 'rgba(255,255,255,0.06)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  thumbWatched: {
    opacity: 0.55,
  },
  thumbUnreleased: {
    opacity: 0.35,
  },
  watchedOverlay: {
    position: 'absolute',
    bottom: 4,
    right: 4,
  },
  unreleasedOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.35)',
  },
  epInfo: {
    flex: 1,
    justifyContent: 'center',
    gap: 3,
  },
  epCodeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  epCode: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.4,
  },
  epCodeWatched: {
    color: 'rgba(255,255,255,0.35)',
  },
  epCodeUnreleased: {
    color: 'rgba(255,152,0,0.5)',
  },
  playingBadge: {
    backgroundColor: 'rgba(255,255,255,0.14)',
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  playingBadgeText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: '700',
  },
  watchedBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    backgroundColor: 'rgba(0,230,118,0.12)',
    borderRadius: 10,
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderWidth: 1,
    borderColor: 'rgba(0,230,118,0.25)',
  },
  watchedBadgeText: {
    color: '#00e676',
    fontSize: 10,
    fontWeight: '700',
  },
  epTitle: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
    lineHeight: 18,
  },
  epTitleWatched: {
    color: 'rgba(255,255,255,0.55)',
    fontWeight: '600',
  },
  epTitleUnreleased: {
    color: 'rgba(255,152,0,0.6)',
    fontWeight: '600',
  },
  unreleasedBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    backgroundColor: 'rgba(255,152,0,0.12)',
    borderRadius: 10,
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderWidth: 1,
    borderColor: 'rgba(255,152,0,0.25)',
  },
  unreleasedBadgeText: {
    color: '#ff9800',
    fontSize: 10,
    fontWeight: '700',
  },
  epOverview: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: 11,
    lineHeight: 15,
    marginTop: 1,
  },
});
