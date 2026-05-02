import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import {
  View, Text, StyleSheet, FlatList, ActivityIndicator,
  StatusBar, TouchableOpacity, ScrollView, Dimensions,
} from 'react-native';
import { BlurTargetView } from 'expo-blur';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { API_BASE } from '../constants/api';
import { tmdbFetch } from '../utils/tmdbFetch';
import { Storage } from '../utils/storage';
import { MediaCard } from '../components/MediaCard';
import { ActionSheet } from '../components/ActionSheet';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { FadeInView, SkeletonMediaCard } from '../components/Skeleton';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useLanguage } from '../context/LanguageContext';
import { useLongPressActions } from '../hooks/useLongPressActions';
import { mediaListItemKey } from '../utils/watchlist';
import { StackBottomNav, BOTTOM_NAV_HEIGHT } from '../components/StackBottomNav';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const H_PAD = 14;
const CARD_GAP = 8;
const cardWidth = (cols: number) => (SCREEN_WIDTH - H_PAD * 2 - CARD_GAP * (cols - 1)) / cols;
const GRID_SKELETON = Array.from({ length: 12 }, (_, i) => ({ id: `browse-skeleton-${i}` }));


// ── Styles ────────────────────────────────────────────────────────────────────

const makeStyles = (c: ThemeColors) => StyleSheet.create({
  container:    { flex: 1, backgroundColor: c.bg },
  stickyHeader: {
    position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10,
    backgroundColor: c.bgHeader,
    paddingHorizontal: 20, paddingBottom: 16,
  },
  headerFade:   { position: 'absolute', left: 0, right: 0, height: 32, zIndex: 9 },
  titleRow:     { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 14 },
  backBtn: {
    width: 38, height: 38, borderRadius: 19,
    backgroundColor: c.cardBgElevated, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center',
  },
  heading:      { flex: 1, color: c.textPrimary, fontSize: 30, fontWeight: '800', letterSpacing: -0.8 },
  countBadge:   {
    borderRadius: 999, paddingHorizontal: 11, paddingVertical: 5,
    backgroundColor: c.cardBgElevated, borderWidth: 1, borderColor: c.border,
  },
  countText:    { color: c.accentSoft, fontSize: 12, fontWeight: '700' },
  filterGroupLabel: {
    color: c.textPrimary,
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
    marginBottom: 6,
  },
  filterRow:    { flexDirection: 'row', gap: 8 },
  filterPill:   {
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: 999,
    borderWidth: 1, borderColor: c.border, backgroundColor: c.cardBgElevated,
  },
  filterPillOn: { borderColor: c.accent, backgroundColor: c.accent + '22' },
  filterText:   { fontSize: 13, fontWeight: '600', color: c.mutedText },
  filterTextOn: { color: c.accentSoft, fontWeight: '700' },
  networkFilterRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    gap: 12,
  },
  networkTypeGroup: {
    flex: 1,
    minWidth: 0,
  },
  sortBtn: {
    minWidth: 96,
    maxWidth: 128,
    height: 38,
    paddingHorizontal: 12,
    borderRadius: 19,
    borderWidth: 1,
    borderColor: c.border,
    backgroundColor: c.cardBgElevated,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  sortBtnText: {
    color: c.accentSoft,
    fontSize: 12,
    fontWeight: '700',
    flexShrink: 1,
  },
  grid:         { paddingHorizontal: H_PAD, paddingTop: 12, paddingBottom: 16 },
  row:          { justifyContent: 'space-between', marginBottom: CARD_GAP },
  skeletonWrap: { paddingHorizontal: H_PAD, paddingTop: 12, paddingBottom: 16 },
  skeletonRow:  { flexDirection: 'row', justifyContent: 'space-between', marginBottom: CARD_GAP },
  colToggleBtn: {
    width: 38, height: 38, borderRadius: 19,
    backgroundColor: c.cardBgElevated, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center',
  },
});

// ── Screen ────────────────────────────────────────────────────────────────────

interface Genre { id: number; name: string }
type NetworkSortMode = 'year' | 'title' | 'rating';

export const BrowseScreen = ({ navigation, route }: any) => {
  const { type, title, endpoint } = route.params || {};
  const isNetworkBrowse = String(endpoint || '').includes('/tmdb/network/');
  const { theme: { colors }, resolvedAppearance } = useTheme();
  const { t } = useLanguage();
  const styles     = useMemo(() => makeStyles(colors), [colors]);
  const insets     = useSafeAreaInsets();
  const blurTargetRef = useRef<View | null>(null);

  const {
    longPressItem, setLongPressItem, handleLongPress, buildActions,
    seriesWatchConfirmItem, setSeriesWatchConfirmItem, handleSeriesMarkWatched,
  } = useLongPressActions({ navigation });

  const [items,        setItems]        = useState<any[]>([]);
  const [page,         setPage]         = useState(1);
  const [totalPages,   setTotalPages]   = useState(1);
  const [loading,      setLoading]      = useState(true);
  const [loadingMore,  setLoadingMore]  = useState(false);
  const [headerHeight, setHeaderHeight] = useState(0);
  const [genres,       setGenres]       = useState<Genre[]>([]);
  const [selectedGenre, setSelectedGenre] = useState<Genre | null>(null);
  const [contentType,   setContentType]  = useState<'all' | 'movie' | 'tv'>('all');
  const [sortMode, setSortMode] = useState<NetworkSortMode>('year');
  const [sortSheetVisible, setSortSheetVisible] = useState(false);
  const [loadedOnce,   setLoadedOnce]   = useState(false);
  const [gridCols,     setGridCols]     = useState<2 | 3>(3);
  const CARD_WIDTH = useMemo(() => cardWidth(gridCols), [gridCols]);
  const sortLabel = useMemo(() => {
    if (sortMode === 'title') return 'Title';
    if (sortMode === 'rating') return 'Rating';
    return 'Year';
  }, [sortMode]);

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

  // Fetch genre list once on mount
  useEffect(() => {
    if (endpoint) { setGenres([]); return; }
    tmdbFetch(`/tmdb/genres/${type}`)
      .then(r => r.json())
      .then(d => setGenres(d.genres ?? []))
      .catch(() => {});
  }, [type, endpoint]);

  // Build the correct endpoint depending on whether a genre is selected
  const buildUrl = useCallback((pageNum: number, genre: Genre | null, options?: { contentType?: 'all' | 'movie' | 'tv' }) => {
    if (endpoint) {
      const params = new URLSearchParams();
      params.set('page', String(pageNum));
      if (isNetworkBrowse) {
        const nextType = options?.contentType ?? contentType;
        if (nextType !== 'all') params.set('type', nextType);
        params.set('sort', sortMode);
      }
      const separator = String(endpoint).includes('?') ? '&' : '?';
      return `${endpoint}${separator}${params.toString()}`;
    }
    if (genre) {
      return `/tmdb/discover?type=${type}&genre_id=${genre.id}&page=${pageNum}`;
    }
    return `/tmdb/browse/${type}?page=${pageNum}`;
  }, [type, endpoint, isNetworkBrowse, contentType, sortMode]);

  const fetchPage = useCallback(async (pageNum: number, genre: Genre | null, append = false, options?: { contentType?: 'all' | 'movie' | 'tv' }) => {
    try {
      const res  = await tmdbFetch(buildUrl(pageNum, genre, options));
      if (!res.ok) return false;
      const data = await res.json();
      setTotalPages(data.total_pages || 1);
      if (append) setItems(prev => [...prev, ...(data.results || [])]);
      else        setItems(data.results || []);
      setLoadedOnce(true);
      return true;
    } catch (e) {
      console.error('Browse fetch failed:', e);
      return false;
    }
  }, [buildUrl]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setPage(1);
    (async () => {
      const ok = await fetchPage(1, selectedGenre, false, { contentType });
      if (!cancelled) setLoading(!ok && !loadedOnce);
    })();
    return () => { cancelled = true; };
  }, [type, endpoint, fetchPage, contentType, sortMode, selectedGenre?.id]);

  const handleGenreSelect = useCallback((genre: Genre | null) => {
    setSelectedGenre(genre);
  }, []);

  const handleContentTypeSelect = useCallback((nextType: 'all' | 'movie' | 'tv') => {
    setContentType(nextType);
  }, []);

  const handleSortSelect = useCallback((nextSort: NetworkSortMode) => {
    setSortMode(nextSort);
    setSortSheetVisible(false);
  }, []);

  const sortActions = useMemo(() => ([
    {
      label: 'Year',
      icon: 'calendar-outline' as const,
      variant: sortMode === 'year' ? 'accent' as const : 'default' as const,
      onPress: () => handleSortSelect('year'),
    },
    {
      label: 'Title',
      icon: 'text-outline' as const,
      variant: sortMode === 'title' ? 'accent' as const : 'default' as const,
      onPress: () => handleSortSelect('title'),
    },
    {
      label: 'Rating',
      icon: 'star-outline' as const,
      variant: sortMode === 'rating' ? 'accent' as const : 'default' as const,
      onPress: () => handleSortSelect('rating'),
    },
    {
      label: 'Cancel',
      icon: 'close-outline' as const,
      variant: 'cancel' as const,
      onPress: () => setSortSheetVisible(false),
    },
  ]), [handleSortSelect, sortMode]);

  const loadMore = async () => {
    if (loadingMore || page >= totalPages) return;
    setLoadingMore(true);
    const next = page + 1;
    setPage(next);
    await fetchPage(next, selectedGenre, true, { contentType });
    setLoadingMore(false);
  };

  const countLabel = items.length > 0
    ? `${items.length}${totalPages > 1 ? '+' : ''}`
    : null;

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
      <ActionSheet
        visible={sortSheetVisible}
        onClose={() => setSortSheetVisible(false)}
        title="Sort By"
        actions={sortActions}
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
        style={[styles.stickyHeader, { paddingTop: insets.top + 26 }]}
        onLayout={e => setHeaderHeight(e.nativeEvent.layout.height)}
      >
        {/* Title row */}
        <View style={styles.titleRow}>
          <TouchableOpacity
            onPress={() => navigation.goBack()}
            style={styles.backBtn}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Ionicons name="chevron-back" size={20} color={colors.accentSoft} />
          </TouchableOpacity>

          <Text style={styles.heading} numberOfLines={1}>{title || t('browse_title')}</Text>

          {countLabel !== null && (
            <View style={styles.countBadge}>
              <Text style={styles.countText}>{countLabel}</Text>
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

        {isNetworkBrowse ? (
          <View style={styles.networkFilterRow}>
            <View style={styles.networkTypeGroup}>
              <Text style={styles.filterGroupLabel}>Type</Text>
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                style={styles.filterRow}
                contentContainerStyle={{ gap: 8, paddingRight: 4 }}
              >
                {(['all', 'movie', 'tv'] as const).map(option => {
                  const active = contentType === option;
                  return (
                    <TouchableOpacity
                      key={option}
                      onPress={() => handleContentTypeSelect(option)}
                      activeOpacity={0.75}
                      style={[styles.filterPill, active && styles.filterPillOn]}
                    >
                      <Text style={[styles.filterText, active && styles.filterTextOn]}>
                        {option === 'all' ? 'All Titles' : option === 'movie' ? 'Movies' : 'Series'}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </ScrollView>
            </View>

            <TouchableOpacity
              onPress={() => setSortSheetVisible(true)}
              activeOpacity={0.75}
              style={styles.sortBtn}
            >
              <Ionicons name="filter-outline" size={18} color={colors.accentSoft} />
              <Text style={styles.sortBtnText} numberOfLines={1}>{sortLabel}</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <>
            {genres.length > 0 && (
              <>
                <Text style={styles.filterGroupLabel}>Genre</Text>
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  style={styles.filterRow}
                  contentContainerStyle={{ gap: 8, paddingRight: 4 }}
                >
                  <TouchableOpacity
                    onPress={() => handleGenreSelect(null)}
                    activeOpacity={0.75}
                    style={[styles.filterPill, !selectedGenre && styles.filterPillOn]}
                  >
                    <Text style={[styles.filterText, !selectedGenre && styles.filterTextOn]}>All</Text>
                  </TouchableOpacity>

                  {genres.map(genre => {
                    const active = selectedGenre?.id === genre.id;
                    return (
                      <TouchableOpacity
                        key={genre.id}
                        onPress={() => handleGenreSelect(genre)}
                        activeOpacity={0.75}
                        style={[styles.filterPill, active && styles.filterPillOn]}
                      >
                        <Text style={[styles.filterText, active && styles.filterTextOn]}>
                          {genre.name}
                        </Text>
                      </TouchableOpacity>
                    );
                  })}
                </ScrollView>
              </>
            )}
          </>
        )}
      </View>

      {loading ? (
        <View style={[styles.skeletonWrap, { paddingTop: headerHeight + 12 }]}>
          {Array.from({ length: Math.ceil(GRID_SKELETON.length / gridCols) }, (_, rowIndex) => (
            <View key={`browse-skeleton-row-${rowIndex}`} style={styles.skeletonRow}>
              {GRID_SKELETON.slice(rowIndex * gridCols, rowIndex * gridCols + gridCols).map(item => (
                <SkeletonMediaCard key={item.id} width={CARD_WIDTH} compactGrid />
              ))}
            </View>
          ))}
        </View>
      ) : (
        <FadeInView>
          <FlatList
            key={`browse-grid-${gridCols}`}
            data={items}
            numColumns={gridCols}
            keyExtractor={(item, i) => mediaListItemKey(item, i)}
            contentContainerStyle={[styles.grid, { paddingTop: headerHeight, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 8 }]}
            columnWrapperStyle={styles.row}
            onEndReached={loadMore}
            onEndReachedThreshold={0.4}
            showsVerticalScrollIndicator={false}
            removeClippedSubviews
            initialNumToRender={15}
            maxToRenderPerBatch={15}
            windowSize={5}
            ListFooterComponent={
              loadingMore
                ? <ActivityIndicator color={colors.accent} style={{ margin: 20 }} />
                : null
            }
            renderItem={({ item }) => (
              <MediaCard
                item={item}
                width={CARD_WIDTH}
                compactGrid
                onPress={() => navigation.navigate('Detail', { movieId: item.id, type: item.type || type })}
                onLongPress={handleLongPress}
              />
            )}
          />
        </FadeInView>
      )}

    </View>
      </BlurTargetView>
      <StackBottomNav blurTarget={blurTargetRef} />
    </View>
  );
};
