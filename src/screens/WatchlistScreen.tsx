import React, { useEffect, useState, useCallback, useMemo } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity,
  StatusBar, Dimensions, RefreshControl, FlatList,
} from 'react-native';
import { BlurTargetView } from 'expo-blur';
import { Image } from 'expo-image';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { API_BASE } from '../constants/api';
import { Storage } from '../utils/storage';
import { runIdle } from '../utils/idleTask';
import { StackBottomNav } from '../components/StackBottomNav';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { ActionSheet } from '../components/ActionSheet';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { FadeInView, SkeletonBlock, SkeletonMediaCard, SkeletonText } from '../components/Skeleton';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useTrakt } from '../context/TraktContext';
import { useLanguage } from '../context/LanguageContext';
import { useLongPressActions } from '../hooks/useLongPressActions';
import { buildAuthHeaders } from '../utils/authHeaders';
import { RatingBadge } from '../components/RatingBadge';
import {
  mediaListItemKey,
  mergeWatchlistItems,
  readWatchlistItems,
  readWatchlistRemovalIds,
  watchlistItemMatchesId,
  writeWatchlistItems,
  writeWatchlistRemovalIds,
  uniqueItemsById,
} from '../utils/watchlist';
import { getProfileStorageOwnerId } from '../utils/profileStorage';



const { width: SCREEN_WIDTH } = Dimensions.get('window');
const H_PAD = 14;
const CARD_GAP = 8;
const cardWidth = (cols: number) => (SCREEN_WIDTH - H_PAD * 2 - CARD_GAP * (cols - 1)) / cols;
const GRID_SKELETON = Array.from({ length: 12 }, (_, i) => ({ id: `watch-skeleton-${i}` }));

type FilterType = 'all' | 'movie' | 'tv';

// ─── Card ─────────────────────────────────────────────────────────────────────

function WatchCard({ item, onPress, onLongPress, width }: { item: any; onPress: () => void; onLongPress: () => void; width: number }) {
  const { theme: { colors } } = useTheme();
  const cardH = Math.round(width * 1.5);
  const styles = useMemo(() => StyleSheet.create({
    progressTrack: {
      position: 'absolute',
      bottom: 0,
      left: 0,
      height: 4,
      backgroundColor: 'rgba(255,255,255,0.3)',
    },
    progressFill: { height: 4, backgroundColor: colors.progressFill },
  }), [colors.progressFill]);

  const hasProgress = typeof item.progress === 'number' && item.progress > 0;
  const hasRuntime = typeof item.runtime === 'number' && item.runtime > 0;
  const ratingColor = (item.rating ?? 0) >= 7 ? '#00e676' : (item.rating ?? 0) >= 5 ? '#ffd740' : '#c97070';

  let timeLabel: string | null = null;
  if (hasProgress && hasRuntime) {
    const remaining = Math.max(1, Math.round((item.runtime as number) * (1 - (item.progress as number) / 100)));
    timeLabel = remaining < 60 ? `${remaining}m left` : `${Math.floor(remaining / 60)}h ${remaining % 60}m left`;
  } else if (hasRuntime) {
    const runtime = item.runtime as number;
    timeLabel = runtime < 60 ? `${runtime}m` : `${Math.floor(runtime / 60)}h ${runtime % 60}m`;
  }

  return (
    <TouchableOpacity
      style={{ width, borderRadius: 12, overflow: 'hidden', backgroundColor: colors.cardBg, borderWidth: 1, borderColor: colors.border }}
      activeOpacity={0.82}
      onPress={onPress}
      onLongPress={onLongPress}
      delayLongPress={350}
    >
      <View>
        {item.poster ? (
          <Image
            source={{ uri: item.poster }}
            style={{ width, height: cardH }}
            contentFit="cover"
            transition={200}
            cachePolicy="memory-disk"
          />
        ) : (
          <View style={{ width, height: cardH, backgroundColor: colors.inputBg, justifyContent: 'center', alignItems: 'center' }}>
            <Ionicons name={item.type === 'tv' ? 'tv-outline' : 'film-outline'} size={34} color={colors.placeholder} />
          </View>
        )}
        {item.rating > 0 && (
          <View style={{ position: 'absolute', top: 8, right: 8 }}>
            <RatingBadge rating={item.rating} size={9} textColor={ratingColor} />
          </View>
        )}
        {hasProgress && (
          <View style={[styles.progressTrack, { width }]}>
            <View style={[styles.progressFill, { width: width * ((item.progress as number) / 100) }]} />
          </View>
        )}
      </View>
      <View style={{ padding: 10, paddingTop: 8 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '700', lineHeight: 18, marginBottom: 3 }} numberOfLines={2}>{item.title}</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Text style={{ color: colors.mutedText, fontSize: 10 }}>{item.year}</Text>
          {timeLabel ? <Text style={{ color: colors.progressFill, fontWeight: '700', fontSize: 10 }}>{timeLabel}</Text> : null}
        </View>
      </View>
    </TouchableOpacity>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const makeStyles = (c: ThemeColors) => StyleSheet.create({
  container: { flex: 1, backgroundColor: c.bg },
  // Sticky header — absolutely positioned so content scrolls behind (transparency visible)
  stickyHeader: {
    position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10,
    backgroundColor: c.bgHeader,
    paddingHorizontal: 20, paddingBottom: 12,
  },
  headerFade: { position: 'absolute', left: 0, right: 0, height: 32, zIndex: 9 },
  headerRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 12 },
  heading: { color: c.textPrimary, fontSize: 28, fontWeight: '900', letterSpacing: 0.5 },
  countBadge: { borderRadius: 20, paddingHorizontal: 10, paddingVertical: 4, backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border },
  countText: { color: c.accentSoft, fontSize: 12, fontWeight: '700' },
  filterRow: { flexDirection: 'row', gap: 8 },
  filterPill: { paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20, borderWidth: 1, borderColor: c.border, backgroundColor: c.cardBg },
  filterPillOn: { borderColor: c.accent, backgroundColor: c.accent + '22' },
  filterText: { fontSize: 13, fontWeight: '600', color: c.mutedText },
  filterTextOn: { color: c.accentSoft, fontWeight: '700' },
  instruction: { color: c.mutedText, fontSize: 12, lineHeight: 18, marginTop: 8 },
  grid: { paddingHorizontal: H_PAD, paddingTop: 16 },
  gridRow: { flexDirection: 'row', marginBottom: CARD_GAP },
  gridSkeleton: { paddingHorizontal: H_PAD, paddingTop: 16 },
  gridSkeletonRow: { flexDirection: 'row', marginBottom: CARD_GAP },
  colToggleBtn: {
    width: 36, height: 36, borderRadius: 18,
    backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center',
  },
  empty: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 40 },
  emptyIconWrap: {
    width: 80, height: 80, borderRadius: 40,
    backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center', marginBottom: 20,
  },
  emptyTitle: { color: c.textPrimary, fontSize: 20, fontWeight: '700', marginBottom: 10, textAlign: 'center' },
  emptyDesc: { color: c.textSecondary, fontSize: 14, textAlign: 'center', lineHeight: 22 },
});

// ─── Screen ───────────────────────────────────────────────────────────────────

export const WatchlistScreen = ({ navigation }: any) => {
  const blurTargetRef = React.useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const { theme: { colors } } = useTheme();
  const { t } = useLanguage();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const { isConnected, watchlist: traktWatchlist, refreshWatchlist } = useTrakt();
  const storageOwnerId = getProfileStorageOwnerId(user?.uid, activeProfile?.id);
  const legacyOwnerId = user?.uid ?? null;
  const watchlistKey = user ? storageOwnerId : null;

  const [localItems, setLocalItems] = useState<any[]>([]);
  const [watchlistRemovalIds, setWatchlistRemovalIds] = useState<string[]>([]);
  const [pendingRemovals, setPendingRemovals] = useState<Set<string>>(new Set());
  const [filter, setFilter] = useState<FilterType>('all');
  const [refreshing, setRefreshing] = useState(false);
  const [headerHeight, setHeaderHeight] = useState(0);
  const [initialLoading, setInitialLoading] = useState(true);
  const [gridCols, setGridCols] = useState<2 | 3>(3);
  const CARD_WIDTH = useMemo(() => cardWidth(gridCols), [gridCols]);

  useEffect(() => {
    Storage.getItem('streamdek_grid_cols').then(v => { if (v === '2' || v === '3') setGridCols(Number(v) as 2 | 3); });
  }, []);
  const toggleGridCols = useCallback(() => {
    setGridCols(c => {
      const next: 2 | 3 = c === 3 ? 2 : 3;
      Storage.setItem('streamdek_grid_cols', String(next));
      return next;
    });
  }, []);

  const allItems = useMemo(() => {
    const removedIds = new Set(watchlistRemovalIds);
    const sortedTrakt = [...traktWatchlist].map(t => ({
      ...t,
      id: t.tmdbId != null ? String(t.tmdbId) : t.id,
      fromTrakt: true,
    }));
    const traktFiltered = sortedTrakt.filter(i => !pendingRemovals.has(String(i.id)) && !removedIds.has(String(i.id)));
    const localOnly = localItems.filter(i => !pendingRemovals.has(String(i.id)) && !removedIds.has(String(i.id)));
    return uniqueItemsById(mergeWatchlistItems(traktFiltered, localOnly));
  }, [traktWatchlist, localItems, pendingRemovals, watchlistRemovalIds]);
  const filteredItems = useMemo(() => {
    if (filter === 'all') return allItems;
    if (filter === 'movie') return allItems.filter(i => i.type !== 'tv');
    return allItems.filter(i => i.type === 'tv');
  }, [allItems, filter]);

  const loadLocal = useCallback(async () => {
    if (watchlistKey) {
      setLocalItems(await readWatchlistItems(storageOwnerId, legacyOwnerId));
    }
  }, [legacyOwnerId, storageOwnerId, watchlistKey]);

  const loadRemovalIds = useCallback(async () => {
    if (!user) {
      setWatchlistRemovalIds([]);
      return;
    }
    setWatchlistRemovalIds(await readWatchlistRemovalIds(storageOwnerId, legacyOwnerId));
  }, [legacyOwnerId, storageOwnerId, user]);

  const load = useCallback(async () => {
    runIdle(async () => {
      try {
        await Promise.all([loadLocal(), loadRemovalIds()]);
        // SWR: Local is loaded, immediately show it by moving past skeleton
        setInitialLoading(false);
        if (isConnected) await refreshWatchlist();
      } catch {
        setInitialLoading(false);
      }
    });
  }, [loadLocal, loadRemovalIds, isConnected, refreshWatchlist]);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  useEffect(() => {
    const unsub = navigation.addListener('focus', load);
    load();
    return unsub;
  }, [navigation, load]);

  // Trakt-aware removal used by the long-press action sheet
  const doRemove = useCallback(async (item: any) => {
    const id = String(item.id);

    // Optimistically hide the card immediately
    setPendingRemovals(prev => new Set([...prev, id]));

    // Remove from Trakt if it came from there
    if (isConnected && user && item.fromTrakt) {
      const entry = {
        title: item.title,
        year: parseInt(String(item.year)) || undefined,
        ids: { tmdb: Number(item.tmdbId ?? item.id) },
      };
      const payload = item.type === 'movie'
        ? { movies: [entry], shows: [] }
        : { movies: [], shows: [entry] };
      await fetch(`${API_BASE}/trakt/sync/watchlist/remove`, {
        method: 'POST',
        headers: await buildAuthHeaders(user, { profileId: activeProfile?.id }),
        body: JSON.stringify(payload),
      }).catch(() => { });
      await refreshWatchlist();
    }

    // Remove from local storage
    if (watchlistKey) {
      const updated = (await readWatchlistItems(storageOwnerId, legacyOwnerId)).filter((i: any) => !watchlistItemMatchesId(i, id));
      await writeWatchlistItems(storageOwnerId, updated);
      setLocalItems(updated);
    }

    const nextRemovalIds = Array.from(new Set([...watchlistRemovalIds, id]));
    setWatchlistRemovalIds(nextRemovalIds);
    await writeWatchlistRemovalIds(storageOwnerId, nextRemovalIds);

    // Clear the optimistic flag
    setPendingRemovals(prev => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  }, [isConnected, legacyOwnerId, refreshWatchlist, storageOwnerId, user, watchlistKey, watchlistRemovalIds]);

  const {
    longPressItem, setLongPressItem, handleLongPress, buildActions,
    seriesWatchConfirmItem, setSeriesWatchConfirmItem, handleSeriesMarkWatched,
  } = useLongPressActions({
    navigation,
    watchlistOverride: allItems,
    onWatchlistRemove: doRemove,
  });

  const FILTERS: { key: FilterType; label: string }[] = [
    { key: 'all', label: t('watchlist_filter_all') },
    { key: 'movie', label: t('watchlist_filter_movies') },
    { key: 'tv', label: t('watchlist_filter_series') },
  ];

  return (
    <View style={{ flex: 1 }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
    <View style={styles.container}>
      <StatusBar barStyle="light-content" translucent backgroundColor="transparent" />

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
        title={t('watched_series_title')}
        message={t('watched_series_msg')}
        confirmLabel={t('watched_series_confirm')}
        cancelLabel={t('common_cancel')}
        onConfirm={() => { if (seriesWatchConfirmItem) handleSeriesMarkWatched(seriesWatchConfirmItem); }}
      />

      {/* Sticky header — 30% transparent with bottom fade */}
      <View
        style={[styles.stickyHeader, { paddingTop: insets.top + 26 }]}
        onLayout={e => setHeaderHeight(e.nativeEvent.layout.height)}
      >
        <View style={styles.headerRow}>
          <Text style={styles.heading}>{t('watchlist_heading')}</Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            {allItems.length > 0 && (
              <View style={styles.countBadge}>
                <Text style={styles.countText}>
                  {t(allItems.length === 1 ? 'watchlist_count_singular' : 'watchlist_count_plural', { n: allItems.length })}
                </Text>
              </View>
            )}
            <TouchableOpacity
              onPress={toggleGridCols}
              style={styles.colToggleBtn}
              hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
            >
              <Ionicons name={gridCols === 3 ? 'grid-outline' : 'apps-outline'} size={18} color={colors.accentSoft} />
            </TouchableOpacity>
          </View>
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

        {allItems.length > 0 && <Text style={styles.instruction}>{t('watchlist_instruction')}</Text>}
      </View>

      {/* All content areas need paddingTop so they start below the absolute header */}
      {initialLoading ? (
        <View style={{ paddingTop: headerHeight, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }}>
          <View style={{ paddingHorizontal: 20, paddingTop: 12 }}>
            <SkeletonText style={{ width: 184, height: 18, marginBottom: 12 }} />
            <View style={styles.filterRow}>
              <SkeletonBlock style={{ width: 70, height: 34, borderRadius: 20 }} />
              <SkeletonBlock style={{ width: 88, height: 34, borderRadius: 20 }} />
              <SkeletonBlock style={{ width: 76, height: 34, borderRadius: 20 }} />
            </View>
          </View>
          <View style={styles.gridSkeleton}>
            {Array.from({ length: 4 }).map((_, ri) => (
              <View key={`skeleton-row-${ri}`} style={styles.gridSkeletonRow}>
                {GRID_SKELETON.slice(0, gridCols).map((item, ci) => (
                  <View key={`${ri}-${item.id}`} style={ci < gridCols - 1 ? { marginRight: CARD_GAP } : null}>
                    <SkeletonMediaCard width={CARD_WIDTH} compactGrid />
                  </View>
                ))}
              </View>
            ))}
          </View>
        </View>
      ) : allItems.length === 0 ? (
        <View style={[styles.empty, { paddingTop: headerHeight }]}>
          <View style={styles.emptyIconWrap}>
            <Ionicons name="bookmark-outline" size={40} color={colors.placeholder} />
          </View>
          <Text style={styles.emptyTitle}>{t('watchlist_empty_title')}</Text>
          <Text style={styles.emptyDesc}>{t('watchlist_empty_desc')}</Text>
        </View>
      ) : filteredItems.length === 0 ? (
        <View style={[styles.empty, { paddingTop: headerHeight }]}>
          <View style={styles.emptyIconWrap}>
            <Ionicons name={filter === 'movie' ? 'film-outline' : 'tv-outline'} size={40} color={colors.placeholder} />
          </View>
          <Text style={styles.emptyTitle}>
            {t('watchlist_no_type', {
              type: t(filter === 'movie' ? 'watchlist_filter_movies' : 'watchlist_filter_series'),
            })}
          </Text>
          <Text style={styles.emptyDesc}>
            {t('watchlist_no_type_sub', {
              type: t(filter === 'movie' ? 'watchlist_filter_movies' : 'watchlist_filter_series'),
            })}
          </Text>
        </View>
      ) : (
        <FlatList
          key={`watchlist-grid-${gridCols}`}
          data={filteredItems}
          keyExtractor={(item, i) => mediaListItemKey(item, i)}
          numColumns={gridCols}
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
          contentContainerStyle={[styles.grid, { paddingTop: headerHeight + 16, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }]}
          renderItem={({ item, index }) => {
            const ci = index % gridCols;
            const isLast = ci === gridCols - 1;
            return (
              <View style={ci < gridCols - 1 ? { marginRight: CARD_GAP } : null}>
                <WatchCard
                  item={item}
                  width={CARD_WIDTH}
                  onPress={() => navigation.navigate('Detail', { movieId: item.id, type: item.type })}
                  onLongPress={() => handleLongPress(item)}
                />
              </View>
            );
          }}
          getItemLayout={(_, index) => {
            const cardH = Math.round(CARD_WIDTH * 1.5);
            const rowH = cardH + 42; // Estimate for title/meta and row margin
            return { length: rowH, offset: rowH * index, index };
          }}
          removeClippedSubviews
          initialNumToRender={10}
          maxToRenderPerBatch={10}
          windowSize={5}
        />
      )}
    </View>
      </BlurTargetView>
      <StackBottomNav activeTab="Watchlist" blurTarget={blurTargetRef} />
    </View>
  );
};
