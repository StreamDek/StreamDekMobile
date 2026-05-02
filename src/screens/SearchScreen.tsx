import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import {
  View, Text, StyleSheet, TextInput, FlatList, ScrollView,
  TouchableOpacity, ActivityIndicator, StatusBar, Keyboard, Image as RNImage, Dimensions,
  Modal, Pressable,
} from 'react-native';
import { Image } from 'expo-image';
import { BlurTargetView } from 'expo-blur';
import { runIdle } from '../utils/idleTask';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { API_BASE } from '../constants/api';
import { tmdbFetch } from '../utils/tmdbFetch';
import { ActionSheet } from '../components/ActionSheet';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { FadeInView, SkeletonBlock, SkeletonMediaCard, SkeletonText } from '../components/Skeleton';
import { Storage } from '../utils/storage';
import { StackBottomNav } from '../components/StackBottomNav';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useLanguage } from '../context/LanguageContext';
import { useLongPressActions } from '../hooks/useLongPressActions';
import { RatingBadge } from '../components/RatingBadge';
import { mediaListItemKey } from '../utils/watchlist';



const RECENT_KEY = 'recent_searches';
const MAX_RECENT = 8;
const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');
const H_PAD = 14;
const CARD_GAP = 8;
const cardWidth = (cols: number) => (SCREEN_WIDTH - H_PAD * 2 - CARD_GAP * (cols - 1)) / cols;
const GRID_SKELETON = Array.from({ length: 12 }, (_, i) => ({ id: `search-skeleton-${i}` }));

const CURRENT_YEAR = new Date().getFullYear();

type DiscoverType = 'movie' | 'tv' | 'documentary';
type DiscoverFilterSheet = 'type' | 'genre' | 'year' | null;

const DiscoverCard = React.memo(function DiscoverCard({ item, onPress, onLongPress, colors }: { item: any; onPress: () => void; onLongPress?: () => void; colors: ThemeColors }) {
  const styles = useMemo(() => StyleSheet.create({
    card: { flex: 1, borderRadius: 12, overflow: 'hidden', backgroundColor: colors.cardBg, borderWidth: 1, borderColor: colors.border },
    poster: { width: '100%', aspectRatio: 2 / 3 },
    posterFallback: { justifyContent: 'center', alignItems: 'center', backgroundColor: colors.inputBg },
    ratingBadge: {
      position: 'absolute', top: 7, right: 7,
      borderRadius: 999,
      paddingLeft: 3,
      paddingRight: 6,
      paddingVertical: 3,
      backgroundColor: 'rgba(8,10,14,0.58)',
    },
  }), [colors]);
  const ratingColor = (item.rating ?? 0) >= 7 ? '#00e676' : (item.rating ?? 0) >= 5 ? '#ffd740' : '#c97070';

  return (
    <TouchableOpacity style={styles.card} onPress={onPress} onLongPress={onLongPress} delayLongPress={350} activeOpacity={0.82}>
      {item.poster ? (
        <Image source={{ uri: item.poster }} style={styles.poster} transition={0} />
      ) : (
        <View style={[styles.poster, styles.posterFallback]}>
          <Ionicons name={item.type === 'tv' ? 'tv-outline' : 'film-outline'} size={28} color={colors.placeholder} />
        </View>
      )}
      {item.rating > 0 && (
        <View style={styles.ratingBadge}>
          <RatingBadge rating={item.rating} size={9} textColor={ratingColor} />
        </View>
      )}
    </TouchableOpacity>
  );
});

const makeStyles = (c: ThemeColors, isLightAppearance: boolean) => {
  return StyleSheet.create({
  container: { flex: 1, backgroundColor: c.bg },
  header: {
    position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10,
    paddingHorizontal: 20, paddingBottom: 18, backgroundColor: c.bgHeader,
  },
  headerFade: { position: 'absolute', left: 0, right: 0, height: 36, zIndex: 9 },
  heading: { color: c.textPrimary, fontSize: 34, fontWeight: '800', letterSpacing: -0.9, marginBottom: 16 },
  inputRow: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.72)' : c.inputBg,
    borderRadius: 18,
    paddingHorizontal: 14, gap: 10,
    borderWidth: 1, borderColor: c.inputBorder,
    minHeight: 52,
  },
  input: { flex: 1, height: 50, color: c.textPrimary, fontSize: 16, fontWeight: '500' },
  clearBtn: {
    width: 28, height: 28, borderRadius: 14, backgroundColor: c.inputBorder,
    justifyContent: 'center', alignItems: 'center',
  },
  searchGrid: { paddingHorizontal: H_PAD, paddingTop: 12 },
  searchRow:  { flexDirection: 'row', marginBottom: CARD_GAP },
  center:      { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 40 },
  loadingText: { color: c.subText, marginTop: 14, fontSize: 14 },
  emptyTitle:  { color: c.textPrimary, fontSize: 18, fontWeight: '700', marginTop: 16, marginBottom: 8, textAlign: 'center' },
  emptyDesc:   { color: c.subText, fontSize: 13, textAlign: 'center', lineHeight: 20 },
  recentSection: { paddingTop: 20, marginBottom: 4 },
  recentHeader: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingHorizontal: 20, marginBottom: 12,
  },
  sectionTitle: { color: c.textPrimary, fontSize: 18, fontWeight: '700', letterSpacing: -0.3 },
  discoverTitle: { color: c.textPrimary, fontSize: 25, fontWeight: '600', letterSpacing: -0.65 },
  clearAll:     { color: isLightAppearance ? c.textPrimary : c.accent, fontSize: 12, fontWeight: '700' },
  recentChips:  { paddingHorizontal: 20 },
  recentChip: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    backgroundColor: c.cardBgElevated, borderRadius: 999,
    paddingHorizontal: 14, paddingVertical: 9,
    borderWidth: 1, borderColor: c.border,
  },
  recentChipText: { color: c.textSecondary, fontSize: 13, fontWeight: '500' },
  discoverSection: { paddingHorizontal: 20, paddingTop: 24 },
  filterGroup:     { marginBottom: 12 },
  filterLabel: {
    color: c.textPrimary, fontSize: 10, fontWeight: '700',
    letterSpacing: 1, textTransform: 'uppercase', marginBottom: 8,
  },
  filterRow:   { flexDirection: 'row', flexWrap: 'wrap' },
  filterScroll: { gap: 8, paddingRight: 8 },
  filterChip: {
    backgroundColor: c.cardBgElevated, borderRadius: 999,
    paddingHorizontal: 14, paddingVertical: 8,
    borderWidth: 1, borderColor: c.border,
  },
  filterChipActive:     { backgroundColor: isLightAppearance ? 'rgba(17,24,39,0.10)' : c.accent + '22', borderColor: isLightAppearance ? 'rgba(17,24,39,0.28)' : c.accent },
  filterChipText:       { color: c.subText, fontSize: 13, fontWeight: '600' },
  filterChipTextActive: { color: isLightAppearance ? c.textPrimary : c.accentSoft, fontWeight: '700' },
  filterField: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 28,
    paddingVertical: 2,
  },
  filterFieldInfo: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  filterFieldTitle: { color: c.textPrimary, fontSize: 15, fontWeight: '600', letterSpacing: -0.2 },
  filterFieldValue: { color: c.subText, fontSize: 15, fontWeight: '500' },
  filterFieldIcon: { alignItems: 'center', justifyContent: 'center' },
  filterSheetBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.42)',
    justifyContent: 'flex-end',
  },
  filterSheetCard: {
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    backgroundColor: c.cardBgElevated,
    borderWidth: 1,
    borderColor: c.border,
    paddingTop: 12,
    paddingHorizontal: 20,
    paddingBottom: 24,
    gap: 10,
  },
  filterSheetScroller: { maxHeight: SCREEN_HEIGHT * 0.62 },
  filterSheetHandle: {
    alignSelf: 'center',
    width: 42,
    height: 5,
    borderRadius: 999,
    marginBottom: 8,
    backgroundColor: isLightAppearance ? 'rgba(17,24,39,0.16)' : 'rgba(255,255,255,0.16)',
  },
  filterSheetTitle: { color: c.textPrimary, fontSize: 24, fontWeight: '700', letterSpacing: -0.6, marginBottom: 8 },
  filterSheetOption: {
    minHeight: 56,
    borderRadius: 18,
    paddingHorizontal: 16,
    paddingVertical: 13,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: c.cardBg,
    borderWidth: 1,
    borderColor: c.border,
  },
  filterSheetOptionActive: {
    backgroundColor: isLightAppearance ? 'rgba(17,24,39,0.06)' : c.accent + '16',
    borderColor: isLightAppearance ? 'rgba(17,24,39,0.18)' : c.accent + '66',
  },
  filterSheetOptionText: { color: c.textPrimary, fontSize: 15, fontWeight: '600' },
  filterSheetOptionTextActive: { color: isLightAppearance ? c.textPrimary : c.accentSoft },
  discoverGrid:   { marginTop: 16 },
  discoverRow:    { flexDirection: 'row', marginBottom: 10 },
  discoverLoader: { paddingVertical: 60, alignItems: 'center' },
  discoverEmpty:  { paddingVertical: 40, alignItems: 'center' },
  discoverSkeletonHeader: { marginTop: 18, marginBottom: 18 },
  loadMoreBtn: {
    marginTop: 8, marginBottom: 4, paddingVertical: 14,
    backgroundColor: c.cardBg, borderRadius: 14,
    alignItems: 'center', borderWidth: 1, borderColor: c.border,
  },
  loadMoreText: { color: c.accentSoft, fontSize: 14, fontWeight: '700' },
  colToggleBtn: {
    width: 36, height: 36, borderRadius: 18,
    backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center',
  },
  });
};

export const SearchScreen = ({ navigation }: any) => {
  const blurTargetRef = useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { theme: { colors }, resolvedAppearance } = useTheme();
  const { t } = useLanguage();
  const isLightAppearance = resolvedAppearance === 'light';
  const styles = useMemo(() => makeStyles(colors, isLightAppearance), [colors, isLightAppearance]);

  const {
    longPressItem, setLongPressItem, handleLongPress, buildActions,
    seriesWatchConfirmItem, setSeriesWatchConfirmItem, handleSeriesMarkWatched,
  } = useLongPressActions({ navigation });

  const yearOptions = useMemo(() => {
    const latestYears = Array.from({ length: 10 }, (_, i) => CURRENT_YEAR - i);
    const oldestListedYear = latestYears[latestYears.length - 1];
    return [
      { label: t('search_any_year'), value: null },
      ...latestYears.map(year => ({
        label: String(year),
        value: String(year),
      })),
      { label: `Before ${oldestListedYear}`, value: `before:${oldestListedYear - 1}` },
    ];
  }, [t]);
  const [headerHeight, setHeaderHeight] = useState(0);

  const [query, setQuery] = useState('');
  const [results, setResults] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [searched, setSearched] = useState(false);
  const [searchPage, setSearchPage] = useState(1);
  const [searchTotalPages, setSearchTotalPages] = useState(1);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inputRef = useRef<TextInput>(null);
  const [recent, setRecent] = useState<string[]>([]);

  const [discoverType, setDiscoverType] = useState<DiscoverType>('movie');
  const [genres, setGenres] = useState<{ id: number; name: string }[]>([]);
  const [selectedGenre, setSelectedGenre] = useState<number | null>(null);
  const [selectedYear, setSelectedYear] = useState<string | null>(null);
  const [activeFilterSheet, setActiveFilterSheet] = useState<DiscoverFilterSheet>(null);
  const [discoverItems, setDiscoverItems] = useState<any[]>([]);
  const [discoverPage, setDiscoverPage] = useState(1);
  const [discoverTotal, setDiscoverTotal] = useState(1);
  const [discoverLoading, setDiscoverLoading] = useState(true);
  const [discoverLoadingMore, setDiscoverLoadingMore] = useState(false);
  const [discoverLoadedOnce, setDiscoverLoadedOnce] = useState(false);
  const [gridCols, setGridCols] = useState<2 | 3>(3);
  const CARD_WIDTH = useMemo(() => cardWidth(gridCols), [gridCols]);
  const typeOptions = useMemo(() => ([
    { label: t('search_movies'), value: 'movie' as DiscoverType },
    { label: t('search_tv'), value: 'tv' as DiscoverType },
    { label: 'Documentaries', value: 'documentary' as DiscoverType },
  ]), [t]);
  const selectedGenreLabel = useMemo(
    () => selectedGenre === null ? t('search_all') : (genres.find(g => g.id === selectedGenre)?.name ?? t('search_all')),
    [genres, selectedGenre, t],
  );
  const selectedYearLabel = useMemo(
    () => yearOptions.find(option => option.value === selectedYear)?.label ?? t('search_any_year'),
    [selectedYear, t, yearOptions],
  );

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

  useEffect(() => {
    Storage.getItem(RECENT_KEY).then(v => { if (v) setRecent(JSON.parse(v)); });
  }, []);

  useEffect(() => {
    setSelectedGenre(null);
    if (discoverType === 'documentary') {
      setGenres([]);
      return;
    }
    runIdle(() => {
      tmdbFetch(`/tmdb/genres/${discoverType}`)
        .then(r => r.json())
        .then(d => setGenres(d.genres || []))
        .catch(() => setGenres([]));
    });
  }, [discoverType]);

  const fetchDiscover = useCallback(async (page: number, append = false) => {
    if (page === 1) setDiscoverLoading(true);
    else setDiscoverLoadingMore(true);
    let ok = false;
    let data: any = null;
    try {
      const isBeforeYear = typeof selectedYear === 'string' && selectedYear.startsWith('before:');
      const beforeYearValue = isBeforeYear ? selectedYear.replace('before:', '') : null;
      const isDocumentary = discoverType === 'documentary';
      const effectiveType = isDocumentary ? 'movie' : discoverType;
      const params = new URLSearchParams({
        type: effectiveType,
        page: String(page),
        ...(isDocumentary ? { genre_id: '99' } : (selectedGenre ? { genre_id: String(selectedGenre) } : {})),
        ...(!isBeforeYear && selectedYear ? { year: selectedYear } : {}),
        ...(isBeforeYear && beforeYearValue
          ? (effectiveType === 'tv'
            ? { 'first_air_date.lte': `${beforeYearValue}-12-31` }
            : { 'primary_release_date.lte': `${beforeYearValue}-12-31` })
          : {}),
      });
      const res = await tmdbFetch(`/tmdb/discover?${params}`);
      if (!res.ok) throw new Error('Discover fetch failed');
      data = await res.json();
      setDiscoverTotal(data.total_pages || 1);
      setDiscoverItems(prev => append ? [...prev, ...(data.results || [])] : (data.results || []));
      setDiscoverLoadedOnce(true);
      ok = true;
    } catch {
      if (!append) setDiscoverItems([]);
    } finally {
      if (page === 1) setDiscoverLoading(!ok && !discoverLoadedOnce);
      else setDiscoverLoading(false);
      setDiscoverLoadingMore(false);
    }
    
    // Low-priority prefetch of posters for immediate visual readiness
    if (page === 1 && ok && data) {
      const firstFew = (data.results || []).slice(0, 12).map((i: any) => i.poster).filter(Boolean);
      Image.prefetch(firstFew);
    }
    return ok;
  }, [discoverType, selectedGenre, selectedYear, discoverLoadedOnce]);

  useEffect(() => {
    setDiscoverPage(1);
    fetchDiscover(1, false);
  }, [fetchDiscover]);

  const loadMoreDiscover = () => {
    if (discoverLoadingMore || discoverPage >= discoverTotal) return;
    const next = discoverPage + 1;
    setDiscoverPage(next);
    fetchDiscover(next, true);
  };

  const handleDiscoverScroll = useCallback((event: any) => {
    if (query.length > 0 || discoverLoading || discoverLoadingMore || discoverPage >= discoverTotal) return;
    const { contentOffset, contentSize, layoutMeasurement } = event.nativeEvent;
    const remaining = contentSize.height - (contentOffset.y + layoutMeasurement.height);
    if (remaining < 320) {
      loadMoreDiscover();
    }
  }, [discoverLoading, discoverLoadingMore, discoverPage, discoverTotal, query.length]);

  const saveRecent = async (q: string) => {
    const trimmed = q.trim();
    if (!trimmed) return;
    const updated = [trimmed, ...recent.filter(r => r !== trimmed)].slice(0, MAX_RECENT);
    setRecent(updated);
    await Storage.setItem(RECENT_KEY, JSON.stringify(updated));
  };

  const fetchSearchPage = useCallback(async (q: string, page: number, append = false) => {
    const trimmed = q.trim();
    if (!trimmed) {
      setResults([]);
      setSearched(false);
      setSearchPage(1);
      setSearchTotalPages(1);
      setLoading(false);
      setLoadingMore(false);
      return;
    }

    if (page === 1) {
      setLoading(true);
      setSearched(true);
      setSearchPage(1);
    } else {
      setLoadingMore(true);
    }

    try {
      const res = await tmdbFetch(`/tmdb/search?q=${encodeURIComponent(trimmed)}&page=${page}`);
      const data = await res.json();
      const nextResults = data.results || [];
      setSearchPage(data.page || page);
      setSearchTotalPages(data.total_pages || 1);
      setResults(prev => (
        append
          ? [...prev, ...nextResults.filter((item: any) => !prev.some(existing => existing?.id === item?.id && existing?.type === item?.type))]
          : nextResults
      ));

      // Prefetch search results posters while user is still processing the screen
      const firstFew = nextResults.slice(0, 12).map((i: any) => i.poster).filter(Boolean);
      Image.prefetch(firstFew);
    } catch {
      if (!append) {
        setResults([]);
        setSearchPage(1);
        setSearchTotalPages(1);
      }
    } finally {
      if (page === 1) setLoading(false);
      else setLoadingMore(false);
    }
  }, []);

  const doSearch = useCallback(async (q: string) => {
    await fetchSearchPage(q, 1, false);
  }, [fetchSearchPage]);

  const loadMoreSearch = useCallback(() => {
    if (
      loading ||
      loadingMore ||
      !searched ||
      !query.trim() ||
      results.length === 0 ||
      searchPage >= searchTotalPages
    ) {
      return;
    }

    void fetchSearchPage(query, searchPage + 1, true);
  }, [fetchSearchPage, loading, loadingMore, query, results.length, searchPage, searchTotalPages, searched]);

  const onChangeText = useCallback((text: string) => {
    setQuery(text);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => doSearch(text), 380);
  }, [doSearch]);

  const onSubmit = useCallback(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    doSearch(query);
    saveRecent(query);
    Keyboard.dismiss();
  }, [doSearch, query]);

  const onRecentTap = useCallback((term: string) => { setQuery(term); doSearch(term); saveRecent(term); }, [doSearch]);
  const clearRecent = useCallback(async () => { setRecent([]); await Storage.removeItem(RECENT_KEY); }, []);
  const navToDetail = useCallback((item: any) => navigation.navigate('Detail', { movieId: item.id, type: item.type || 'movie' }), [navigation]);

  const showSearch = query.length > 0;
  const renderFilterOption = useCallback((
    label: string,
    active: boolean,
    onPress: () => void,
  ) => (
    <TouchableOpacity
      key={label}
      style={[styles.filterSheetOption, active && styles.filterSheetOptionActive]}
      onPress={onPress}
      activeOpacity={0.8}
    >
      <Text style={[styles.filterSheetOptionText, active && styles.filterSheetOptionTextActive]}>
        {label}
      </Text>
      {active ? <Ionicons name="checkmark-circle" size={20} color={colors.toggleOn} /> : null}
    </TouchableOpacity>
  ), [colors.toggleOn, styles.filterSheetOption, styles.filterSheetOptionActive, styles.filterSheetOptionText, styles.filterSheetOptionTextActive]);

  const renderFilterSheetContent = () => {
    if (activeFilterSheet === 'type') {
      return (
        <>
          <Text style={styles.filterSheetTitle}>{t('search_type')}</Text>
          {typeOptions.map(option => renderFilterOption(option.label, discoverType === option.value, () => {
            setDiscoverType(option.value);
            setActiveFilterSheet(null);
          }))}
        </>
      );
    }

    if (activeFilterSheet === 'genre') {
      return (
        <>
          <Text style={styles.filterSheetTitle}>{t('search_genre')}</Text>
          {renderFilterOption(t('search_all'), selectedGenre === null, () => {
            setSelectedGenre(null);
            setActiveFilterSheet(null);
          })}
          {genres.map(genre => renderFilterOption(genre.name, selectedGenre === genre.id, () => {
            setSelectedGenre(selectedGenre === genre.id ? null : genre.id);
            setActiveFilterSheet(null);
          }))}
        </>
      );
    }

    if (activeFilterSheet === 'year') {
      return (
        <>
          <Text style={styles.filterSheetTitle}>{t('search_year')}</Text>
          {yearOptions.map(option => renderFilterOption(option.label, selectedYear === option.value, () => {
            setSelectedYear(selectedYear === option.value ? null : option.value);
            setActiveFilterSheet(null);
          }))}
        </>
      );
    }

    return null;
  };


  return (
    <View style={{ flex: 1 }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
    <View style={styles.container}>
      <StatusBar barStyle={resolvedAppearance === 'light' ? 'dark-content' : 'light-content'} translucent backgroundColor="transparent" />
      <Modal
        visible={activeFilterSheet !== null}
        transparent
        animationType="slide"
        statusBarTranslucent
        onRequestClose={() => setActiveFilterSheet(null)}
      >
        <Pressable style={styles.filterSheetBackdrop} onPress={() => setActiveFilterSheet(null)}>
          <Pressable style={[styles.filterSheetCard, { paddingBottom: Math.max(insets.bottom, 16) + 8 }]} onPress={() => {}}>
            <View style={styles.filterSheetHandle} />
            <ScrollView
              showsVerticalScrollIndicator={false}
              style={styles.filterSheetScroller}
              contentContainerStyle={{ paddingBottom: 4 }}
            >
              {renderFilterSheetContent()}
            </ScrollView>
          </Pressable>
        </Pressable>
      </Modal>

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

      <View
        style={[styles.header, { paddingTop: insets.top + 26 }]}
        onLayout={e => setHeaderHeight(e.nativeEvent.layout.height)}
      >
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
          <Text style={[styles.heading, { marginBottom: 0 }]}>{t('nav_search')}</Text>
          <TouchableOpacity
            onPress={toggleGridCols}
            style={styles.colToggleBtn}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Ionicons name={gridCols === 3 ? 'grid-outline' : 'apps-outline'} size={18} color={colors.accentSoft} />
          </TouchableOpacity>
        </View>
        <View style={styles.inputRow}>
          <Ionicons name="search-outline" size={18} color={colors.mutedText} />
          <TextInput
            ref={inputRef}
            style={styles.input}
            value={query}
            onChangeText={onChangeText}
            onSubmitEditing={onSubmit}
            placeholder={t('search_placeholder')}
            placeholderTextColor={colors.mutedText}
            returnKeyType="search"
            autoCorrect={false}
            autoCapitalize="none"
            selectionColor={colors.accent}
          />
          {query.length > 0 && (
            <TouchableOpacity
              onPress={() => {
                setQuery('');
                setResults([]);
                setSearched(false);
                setSearchPage(1);
                setSearchTotalPages(1);
                setLoading(false);
                setLoadingMore(false);
              }}
              style={styles.clearBtn}
            >
              <Ionicons name="close" size={14} color={colors.subText} />
            </TouchableOpacity>
          )}
        </View>
      </View>

      {showSearch && (
        <View style={{ flex: 1 }}>
          {loading && results.length === 0 ? (
            <View style={[styles.searchGrid, { paddingTop: headerHeight + 12 }]}>
              {Array.from({ length: Math.ceil(GRID_SKELETON.length / gridCols) }, (_, ri) => (
                <View key={`search-skeleton-row-${ri}`} style={styles.searchRow}>
                  {GRID_SKELETON.slice(ri * gridCols, ri * gridCols + gridCols).map((item, ci) => (
                    <View key={item.id} style={[ci < gridCols - 1 ? { marginRight: CARD_GAP } : null, { flex: 1 }]}>
                      <SkeletonMediaCard width={CARD_WIDTH} compactGrid />
                    </View>
                  ))}
                </View>
              ))}
            </View>
          ) : searched && results.length === 0 ? (
            <View style={[styles.center, { paddingTop: headerHeight }]}>
              <Ionicons name="search-outline" size={48} color={colors.placeholder} />
              <Text style={styles.emptyTitle}>{t('search_no_results')} "{query}"</Text>
              <Text style={styles.emptyDesc}>{t('search_no_results_sub')}</Text>
            </View>
          ) : (
            <FlatList
              key={`grid-${gridCols}`}
              data={results}
              renderItem={({ item, index }) => {
                const ci = index % gridCols;
                const isLast = ci === gridCols - 1;
                return (
                  <View style={[!isLast ? { marginRight: CARD_GAP } : null, { width: CARD_WIDTH }]}>
                    <DiscoverCard item={item} onPress={() => navToDetail(item)} onLongPress={() => handleLongPress(item)} colors={colors} />
                  </View>
                );
              }}
              numColumns={gridCols}
              columnWrapperStyle={styles.searchRow}
              keyExtractor={(item, i) => mediaListItemKey(item, i)}
              showsVerticalScrollIndicator={false}
              onEndReached={loadMoreSearch}
              onEndReachedThreshold={0.4}
              keyboardShouldPersistTaps="handled"
              contentContainerStyle={[styles.searchGrid, { paddingTop: headerHeight + 12, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 16 }]}
              ListFooterComponent={loadingMore ? (
                <View style={{ paddingVertical: 20, alignItems: 'center' }}>
                  <ActivityIndicator color={colors.accentSoft} />
                </View>
              ) : null}
              removeClippedSubviews
              initialNumToRender={12}
              maxToRenderPerBatch={12}
              windowSize={5}
              getItemLayout={(_, index) => ({
                length: 242, // Height of MediaCard including margins/meta
                offset: 242 * index,
                index,
              })}
            />
          )}
        </View>
      )}

      {/* Discover Section (when NOT searching) */}
      {!showSearch && (
        <ScrollView
          showsVerticalScrollIndicator={false}
          onScroll={handleDiscoverScroll}
          scrollEventThrottle={16}
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={{ paddingTop: headerHeight, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }}
        >
          {recent.length > 0 && (
            <View style={styles.recentSection}>
              <View style={styles.recentHeader}>
                <Text style={styles.sectionTitle}>{t('search_recent')}</Text>
                <TouchableOpacity onPress={clearRecent}>
                  <Text style={styles.clearAll}>{t('search_clear_all')}</Text>
                </TouchableOpacity>
              </View>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.recentChips}>
                {recent.map((term, i) => (
                  <TouchableOpacity key={term} style={[styles.recentChip, i < recent.length - 1 ? { marginRight: 8 } : null]} onPress={() => onRecentTap(term)}>
                    <Ionicons name="time-outline" size={12} color={colors.subText} />
                    <Text style={styles.recentChipText}>{term}</Text>
                  </TouchableOpacity>
                ))}
              </ScrollView>
            </View>
          )}

          <View style={styles.discoverSection}>
            <Text style={styles.discoverTitle}>{t('search_discover')}</Text>

            <View style={[styles.filterRow, { justifyContent: 'space-between', alignItems: 'center', marginTop: 10, marginBottom: 4 }]}>
              <TouchableOpacity style={styles.filterField} onPress={() => setActiveFilterSheet('type')} activeOpacity={0.82}>
                <View style={styles.filterFieldInfo}>
                  <Text style={styles.filterFieldTitle}>{typeOptions.find(option => option.value === discoverType)?.label ?? t('search_movies')}</Text>
                  <View style={styles.filterFieldIcon}>
                    <Ionicons name="chevron-down" size={14} color={colors.placeholder} />
                  </View>
                </View>
              </TouchableOpacity>
              {genres.length > 0 ? (
                <TouchableOpacity style={styles.filterField} onPress={() => setActiveFilterSheet('genre')} activeOpacity={0.82}>
                  <View style={styles.filterFieldInfo}>
                    <Text style={styles.filterFieldValue}>{selectedGenreLabel}</Text>
                    <View style={styles.filterFieldIcon}>
                      <Ionicons name="chevron-down" size={14} color={colors.placeholder} />
                    </View>
                  </View>
                </TouchableOpacity>
              ) : <View />}
              <TouchableOpacity style={styles.filterField} onPress={() => setActiveFilterSheet('year')} activeOpacity={0.82}>
                <View style={styles.filterFieldInfo}>
                  <Text style={styles.filterFieldValue}>{selectedYearLabel}</Text>
                  <View style={styles.filterFieldIcon}>
                    <Ionicons name="chevron-down" size={14} color={colors.placeholder} />
                  </View>
                </View>
              </TouchableOpacity>
            </View>

            {discoverLoading ? (
              <View style={styles.discoverLoader}>
                <View style={{ width: '100%' }}>
                  <View style={styles.discoverSkeletonHeader}>
                    <SkeletonText style={{ width: 104, height: 12, marginBottom: 12 }} />
                    <View style={styles.filterRow}>
                      <SkeletonBlock style={{ width: 86, height: 34, borderRadius: 20 }} />
                      <SkeletonBlock style={{ width: 94, height: 34, borderRadius: 20 }} />
                      <SkeletonBlock style={{ width: 78, height: 34, borderRadius: 20 }} />
                    </View>
                  </View>
                  {Array.from({ length: 2 }, (_, rowIndex) => (
                    <View key={`discover-skeleton-row-${rowIndex}`} style={styles.discoverRow}>
                      {Array.from({ length: gridCols }, (_, colIndex) => (
                        <View key={`discover-skeleton-${rowIndex}-${colIndex}`} style={[colIndex < gridCols - 1 ? { marginRight: 10 } : null, { flex: 1 }]}>
                          <SkeletonMediaCard width={CARD_WIDTH} compactGrid />
                        </View>
                      ))}
                    </View>
                  ))}
                </View>
              </View>
            ) : discoverItems.length === 0 ? (
              <View style={styles.discoverEmpty}>
                <Text style={styles.emptyDesc}>{t('search_no_discover')}</Text>
              </View>
            ) : (
              <FadeInView style={styles.discoverGrid}>
                <FlatList
                  key={`discover-grid-${gridCols}`}
                  keyExtractor={(item, i) => mediaListItemKey(item, i)}
                  numColumns={gridCols}
                  columnWrapperStyle={styles.discoverRow}
                  scrollEnabled={false} // Container ScrollView handles scrolling
                  renderItem={({ item, index }) => {
                    if (item.id.startsWith('GHOST')) return <View style={{ flex: 1 }} />;
                    const ci = index % gridCols;
                    const isLast = ci === gridCols - 1;
                    return (
                      <View style={[!isLast ? { marginRight: 10 } : null, { flex: 1 }]}>
                        <DiscoverCard item={item} onPress={() => navToDetail(item)} onLongPress={() => handleLongPress(item)} colors={colors} />
                      </View>
                    );
                  }}
                  data={(() => {
                    const data = [...discoverItems];
                    const remainder = data.length % gridCols;
                    if (remainder > 0) {
                      for (let i = 0; i < gridCols - remainder; i++) {
                        data.push({ id: `GHOST-${i}` });
                      }
                    }
                    return data;
                  })()}
                  ListFooterComponent={discoverLoadingMore ? (
                    <View style={{ paddingVertical: 20, alignItems: 'center' }}>
                      <ActivityIndicator size="small" color={colors.accent} />
                    </View>
                  ) : null}
                />
              </FadeInView>
            )}
          </View>
        </ScrollView>
      )}
    </View>
      </BlurTargetView>
      <StackBottomNav activeTab="Search" blurTarget={blurTargetRef} />
    </View>
  );
};
