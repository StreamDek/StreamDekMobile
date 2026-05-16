import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  StatusBar, RefreshControl, Dimensions, FlatList,
} from 'react-native';
import { runIdle } from '../utils/idleTask';
import { Image } from 'expo-image';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { API_BASE } from '../constants/api';
import { BlurTargetView } from 'expo-blur';
import { StackBottomNav, BOTTOM_NAV_HEIGHT } from '../components/StackBottomNav';
import { ActionSheet } from '../components/ActionSheet';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { MediaCard } from '../components/MediaCard';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { TraktItem } from '../context/TraktContext';
import { useLongPressActions } from '../hooks/useLongPressActions';
import { buildAuthHeaders } from '../utils/authHeaders';
import { mediaListItemKey } from '../utils/watchlist';



const { width: SCREEN_WIDTH } = Dimensions.get('window');
const H_PAD      = 14;
const CARD_GAP   = 8;
const CARD_WIDTH  = (SCREEN_WIDTH - H_PAD * 2 - CARD_GAP * 2) / 3;
const CARD_HEIGHT = Math.round(CARD_WIDTH * 1.5);

type FilterType = 'all' | 'movie' | 'tv';
type Mode = 'trending' | 'recommended';

// ── Card ──────────────────────────────────────────────────────────────────────

function CollectionCard({ item, onPress, onLongPress }: { item: TraktItem; onPress: () => void; onLongPress: () => void }) {
  const { theme: { colors } } = useTheme();
  const ratingColor = (item.rating ?? 0) >= 7 ? '#00e676' : (item.rating ?? 0) >= 5 ? '#ffd740' : '#c97070';

  return (
    <TouchableOpacity
      style={{ width: CARD_WIDTH, borderRadius: 10, overflow: 'hidden', backgroundColor: colors.cardBg, borderWidth: 1, borderColor: colors.border }}
      activeOpacity={0.82}
      onPress={onPress}
      onLongPress={onLongPress}
      delayLongPress={350}
    >
      {item.poster ? (
        <Image
          source={{ uri: item.poster }}
          style={{ width: CARD_WIDTH, height: CARD_HEIGHT }}
          contentFit="cover"
          transition={200}
          cachePolicy="memory-disk"
        />
      ) : (
        <View style={{ width: CARD_WIDTH, height: CARD_HEIGHT, backgroundColor: colors.inputBg, justifyContent: 'center', alignItems: 'center' }}>
          <Ionicons name={item.type === 'tv' ? 'tv-outline' : 'film-outline'} size={28} color={colors.placeholder} />
        </View>
      )}
      {item.rating != null && (
        <View style={{ position: 'absolute', top: 6, right: 6, backgroundColor: ratingColor, borderRadius: 6, paddingHorizontal: 5, paddingVertical: 2 }}>
          <Text style={{ color: '#000', fontSize: 9, fontWeight: '800' }}>★ {item.rating.toFixed(1)}</Text>
        </View>
      )}
      {typeof item.unwatchedEpisodes === 'number' && item.unwatchedEpisodes > 0 && (
        <View style={{ position: 'absolute', top: 6, left: 6, backgroundColor: '#e040fb', borderRadius: 6, paddingHorizontal: 5, paddingVertical: 2 }}>
          <Text style={{ color: colors.textPrimary, fontSize: 9, fontWeight: '800' }}>{item.unwatchedEpisodes} new</Text>
        </View>
      )}
      <View style={{ padding: 7, paddingTop: 5 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 11, fontWeight: '700', lineHeight: 15, marginBottom: 2 }} numberOfLines={2}>{item.title}</Text>
        <Text style={{ color: colors.mutedText, fontSize: 10 }}>{item.year}</Text>
      </View>
    </TouchableOpacity>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const makeStyles = (c: ThemeColors) => StyleSheet.create({
  container: { flex: 1, backgroundColor: c.bg },
  header: {
    position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10,
    paddingHorizontal: 16, paddingBottom: 12,
    backgroundColor: c.bgHeader,
  },
  headerFade:  { position: 'absolute', left: 0, right: 0, height: 32, zIndex: 9 },
  titleRow:    { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 14 },
  backBtn: {
    width: 38, height: 38, borderRadius: 19,
    backgroundColor: c.cardBgElevated, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center',
  },
  heading:     { flex: 1, color: c.textPrimary, fontSize: 30, fontWeight: '800', letterSpacing: -0.8 },
  countBadge:  { borderRadius: 20, paddingHorizontal: 10, paddingVertical: 4, backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border },
  countText:   { color: c.accentSoft, fontSize: 12, fontWeight: '700' },
  filterRow:   { flexDirection: 'row', gap: 8 },
  filterPill:  { paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20, borderWidth: 1, borderColor: c.border, backgroundColor: c.cardBg },
  filterPillOn:{ borderColor: c.accent, backgroundColor: c.accent + '22' },
  filterText:  { fontSize: 13, fontWeight: '600', color: c.mutedText },
  filterTextOn:{ color: c.accentSoft, fontWeight: '700' },
  grid:        { paddingHorizontal: H_PAD },
  gridRow:     { flexDirection: 'row', marginBottom: CARD_GAP },
  empty:       { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 40 },
  emptyIconWrap: {
    width: 80, height: 80, borderRadius: 40,
    backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center', marginBottom: 20,
  },
  emptyTitle:  { color: c.textPrimary, fontSize: 20, fontWeight: '700', marginBottom: 10, textAlign: 'center' },
  emptyDesc:   { color: c.textSecondary, fontSize: 14, textAlign: 'center', lineHeight: 22 },
});

// ── Config ────────────────────────────────────────────────────────────────────

const CONFIG: Record<Mode, {
  heading: string;
  movieEndpoint: string;
  showEndpoint: string;
  emptyIcon: React.ComponentProps<typeof Ionicons>['name'];
  emptyTitle: string;
  emptyDesc: string;
}> = {
  trending: {
    heading:       'Trending on Trakt',
    movieEndpoint: '/trakt/trending/movies',
    showEndpoint:  '/trakt/trending/shows',
    emptyIcon:     'flame-outline',
    emptyTitle:    'Nothing trending right now',
    emptyDesc:     'Check back soon for what everyone is watching on Trakt.',
  },
  recommended: {
    heading:       'Recommended for You',
    movieEndpoint: '/trakt/recommendations/movies',
    showEndpoint:  '/trakt/recommendations/shows',
    emptyIcon:     'star-outline',
    emptyTitle:    'No recommendations yet',
    emptyDesc:     'Watch and rate more titles on Trakt to get personalised recommendations.',
  },
};

const FILTERS: { key: FilterType; label: string }[] = [
  { key: 'all',   label: 'All' },
  { key: 'movie', label: 'Movies' },
  { key: 'tv',    label: 'Series' },
];

// ── Screen ────────────────────────────────────────────────────────────────────

export const TraktCollectionScreen = ({ route, navigation }: any) => {
  const mode: Mode = route.params?.mode ?? 'trending';
  const cfg = CONFIG[mode];

  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const { theme: { colors }, resolvedAppearance } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const blurTargetRef = useRef<View | null>(null);

  const {
    longPressItem, setLongPressItem, handleLongPress, buildActions,
    seriesWatchConfirmItem, setSeriesWatchConfirmItem, handleSeriesMarkWatched,
  } = useLongPressActions({ navigation });

  const [movies,       setMovies]       = useState<TraktItem[]>([]);
  const [shows,        setShows]        = useState<TraktItem[]>([]);
  const [refreshing,   setRefreshing]   = useState(false);
  const [headerHeight, setHeaderHeight] = useState(0);
  const [filter,       setFilter]       = useState<FilterType>('all');
  const fetchData = useCallback(async () => {
    runIdle(async () => {
      const traktHeaders = await buildAuthHeaders(user, { profileId: activeProfile?.id });
      await Promise.all([
        fetch(`${API_BASE}${cfg.movieEndpoint}`, { headers: traktHeaders })
          .then(r => r.ok ? r.json() : { results: [] })
          .then(d => setMovies((d.results ?? []).map((i: TraktItem) => ({ ...i, type: 'movie' as const }))))
          .catch(() => setMovies([])),
        fetch(`${API_BASE}${cfg.showEndpoint}`, { headers: traktHeaders })
          .then(r => r.ok ? r.json() : { results: [] })
          .then(d => setShows((d.results ?? []).map((i: TraktItem) => ({ ...i, type: 'tv' as const }))))
          .catch(() => setShows([])),
      ]);
    });
  }, [activeProfile?.id, cfg, user]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const onRefresh = async () => {
    setRefreshing(true);
    await fetchData();
    setRefreshing(false);
  };

  // Merge and interleave movies + shows for the 'all' view so it's not one block of movies then shows
  const allItems = useMemo<TraktItem[]>(() => {
    const maxLen = Math.max(movies.length, shows.length);
    const merged: TraktItem[] = [];
    for (let i = 0; i < maxLen; i++) {
      if (movies[i]) merged.push(movies[i]);
      if (shows[i])  merged.push(shows[i]);
    }
    return merged;
  }, [movies, shows]);

  const displayItems = useMemo(() => {
    if (filter === 'all')   return allItems;
    if (filter === 'movie') return movies;
    return shows;
  }, [filter, allItems, movies, shows]);

  const total = allItems.length;

  return (
    <View style={{ flex: 1 }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
    <View style={styles.container}>
      <StatusBar barStyle={resolvedAppearance === 'light' ? 'dark-content' : 'light-content'} translucent backgroundColor="transparent" />

      <ActionSheet
        visible={!!longPressItem}
        onClose={() => setLongPressItem(null)}
        title={longPressItem?.title}
        subtitle={longPressItem?.year ? String(longPressItem.year) : undefined}
        actions={buildActions(longPressItem)}
      />
      <ConfirmSheet
        visible={!!seriesWatchConfirmItem}
        onClose={() => setSeriesWatchConfirmItem(null)}
        title="Mark Series as Watched"
        message="This will mark all episodes of this series as watched. Continue?"
        confirmLabel="Mark Watched"
        cancelLabel="Cancel"
        onConfirm={() => { if (seriesWatchConfirmItem) handleSeriesMarkWatched(seriesWatchConfirmItem); }}
      />

      {/* Sticky header */}
      <View
        style={[styles.header, { paddingTop: insets.top + 26 }]}
        onLayout={e => setHeaderHeight(e.nativeEvent.layout.height)}
      >
        <View style={styles.titleRow}>
          <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()} activeOpacity={0.8}>
            <Ionicons name="chevron-back" size={20} color={colors.accentSoft} />
          </TouchableOpacity>
          <Text style={styles.heading} numberOfLines={1}>{cfg.heading}</Text>
          {total > 0 && (
            <View style={styles.countBadge}>
              <Text style={styles.countText}>{total}</Text>
            </View>
          )}
        </View>

        <View style={styles.filterRow}>
          {FILTERS.map(f => (
            <TouchableOpacity
              key={f.key}
              style={[styles.filterPill, filter === f.key && styles.filterPillOn]}
              onPress={() => setFilter(f.key)}
              activeOpacity={0.75}
            >
              <Text style={[styles.filterText, filter === f.key && styles.filterTextOn]}>{f.label}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {total === 0 && !refreshing ? (
        <View style={[styles.empty, { paddingTop: headerHeight }]}>
          <View style={styles.emptyIconWrap}>
            <Ionicons name={cfg.emptyIcon} size={40} color={colors.placeholder} />
          </View>
          <Text style={styles.emptyTitle}>{cfg.emptyTitle}</Text>
          <Text style={styles.emptyDesc}>{cfg.emptyDesc}</Text>
        </View>
      ) : displayItems.length === 0 ? (
        <View style={[styles.empty, { paddingTop: headerHeight }]}>
          <View style={styles.emptyIconWrap}>
            <Ionicons name={filter === 'movie' ? 'film-outline' : 'tv-outline'} size={40} color={colors.placeholder} />
          </View>
          <Text style={styles.emptyTitle}>No {filter === 'movie' ? 'movies' : 'series'} here</Text>
          <Text style={styles.emptyDesc}>Try switching to All or check back later.</Text>
        </View>
      ) : (
        <FlatList
          key="trakt-collection-grid"
          data={displayItems}
          keyExtractor={(item, i) => mediaListItemKey(item, i)}
          numColumns={3}
          columnWrapperStyle={styles.gridRow}
          showsVerticalScrollIndicator={false}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={onRefresh}
              tintColor={colors.accentSoft}
              progressViewOffset={headerHeight}
            />
          }
          contentContainerStyle={[styles.grid, { paddingTop: headerHeight + 12, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }]}
          renderItem={({ item, index }) => {
            const ci = index % 3;
            return (
              <View style={ci < 2 ? { marginRight: CARD_GAP } : null}>
                <MediaCard
                  item={item}
                  width={CARD_WIDTH}
                  compactGrid
                  onPress={() => navigation.navigate('Detail', { movieId: item.tmdbId ?? item.id, type: item.type })}
                  onLongPress={() => handleLongPress(item)}
                />
              </View>
            );
          }}
          getItemLayout={(_, index) => {
            const rowH = CARD_HEIGHT + 42; // Estimate for title/meta and row margin
            return { length: rowH, offset: rowH * index, index };
          }}
          removeClippedSubviews
          initialNumToRender={12}
          maxToRenderPerBatch={12}
          windowSize={5}
        />
      )}
    </View>
      </BlurTargetView>
      <StackBottomNav blurTarget={blurTargetRef} />
    </View>
  );
};
