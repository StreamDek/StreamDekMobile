import React, { useState, useMemo, useCallback, useEffect } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity,
  StatusBar, RefreshControl, Dimensions,
} from 'react-native';
import { BlurTargetView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { runIdle } from '../utils/idleTask';
import { Image } from 'expo-image';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { StackBottomNav } from '../components/StackBottomNav';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { ActionSheet } from '../components/ActionSheet';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useLanguage } from '../context/LanguageContext';
import { useTrakt, TraktItem } from '../context/TraktContext';
import { useLongPressActions } from '../hooks/useLongPressActions';
import { Storage } from '../utils/storage';
import { RatingBadge } from '../components/RatingBadge';
import { mediaListItemKey, uniqueItemsById } from '../utils/watchlist';
import { getProfileStorageOwnerId, progressIndexStorageKey } from '../utils/profileStorage';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const CARD_GAP = 8;
const H_PAD    = 12;
const cardWidth = (cols: number) => Math.floor((SCREEN_WIDTH - H_PAD * 2 - CARD_GAP * (cols - 1)) / cols);

function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes}m`;
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

type FilterType = 'all' | 'movie' | 'tv';

// ── Large card with progress bar + unwatched badge ────────────────────────────

function ContinueCard({ item, onPress, onLongPress, width }: { item: TraktItem; onPress: () => void; onLongPress: () => void; width: number }) {
  const { theme: { colors } } = useTheme();
  const cardH = Math.round(width * 1.5);
  const styles = useMemo(() => StyleSheet.create({
    card: {
      width, borderRadius: 12, overflow: 'hidden',
      backgroundColor: colors.cardBg, borderWidth: 1, borderColor: colors.border,
    },
    poster:      { width, height: cardH },
    placeholder: {
      width, height: cardH,
      backgroundColor: colors.inputBg, justifyContent: 'center', alignItems: 'center',
    },
    ratingBadge: {
      position: 'absolute', top: 8, right: 8,
      paddingHorizontal: 0, paddingVertical: 0,
    },
    unwatchedBadge: {
      position: 'absolute', top: 8, left: 8,
      borderRadius: 6, paddingHorizontal: 7, paddingVertical: 4,
      backgroundColor: '#e040fb',
    },
    unwatchedText: { color: colors.textPrimary, fontSize: 11, fontWeight: '800' },
    progressTrack: {
      position: 'absolute', bottom: 0, left: 0,
      height: 4, backgroundColor: 'rgba(255,255,255,0.3)',
    },
    progressFill: { height: 4, backgroundColor: colors.progressFill },
    info:         { padding: 10, paddingTop: 8 },
    title:        { color: colors.textPrimary, fontSize: 13, fontWeight: '700', lineHeight: 18, marginBottom: 3 },
    metaRow:      { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
    year:         { color: colors.mutedText, fontSize: 10 },
    duration:     { color: colors.mutedText, fontSize: 10 },
    durationLeft: { color: colors.progressFill, fontWeight: '700', fontSize: 10 },
  }), [colors, width, cardH]);

  const hasProgress  = typeof item.progress          === 'number' && item.progress          > 0;
  const hasRuntime   = typeof item.runtime           === 'number' && item.runtime           > 0;
  const hasUnwatched = typeof item.unwatchedEpisodes === 'number' && item.unwatchedEpisodes > 0;

  let timeLabel: string | null = null;
  if (hasProgress && hasRuntime) {
    const remaining = Math.max(1, Math.round((item.runtime as number) * (1 - (item.progress as number) / 100)));
    timeLabel = `${formatDuration(remaining)} left`;
  } else if (hasRuntime) {
    timeLabel = formatDuration(item.runtime as number);
  }

  const ratingColor = (item.rating ?? 0) >= 7 ? '#00e676' : (item.rating ?? 0) >= 5 ? '#ffd740' : '#c97070';

  return (
    <TouchableOpacity style={styles.card} activeOpacity={0.82} onPress={onPress} onLongPress={onLongPress} delayLongPress={350}>
      <View>
        {(item.poster ?? item.backdrop) ? (
          <Image
            source={{ uri: item.poster ?? item.backdrop ?? undefined }}
            style={styles.poster}
            contentFit="cover"
            transition={200}
            cachePolicy="memory-disk"
          />
        ) : (
          <View style={styles.placeholder}>
            <Ionicons
              name={item.type === 'tv' ? 'tv-outline' : 'film-outline'}
              size={34} color={colors.textSecondary}
            />
          </View>
        )}
        {item.rating != null && (
          <View style={styles.ratingBadge}>
            <RatingBadge rating={item.rating} size={9} textColor={ratingColor} />
          </View>
        )}
        {hasUnwatched && (
          <View style={styles.unwatchedBadge}>
            <Text style={styles.unwatchedText}>{item.unwatchedEpisodes} new</Text>
          </View>
        )}
        {hasProgress && (
          <View style={[styles.progressTrack, { width }]}>
            <View style={[styles.progressFill, { width: width * ((item.progress as number) / 100) }]} />
          </View>
        )}
      </View>
      <View style={styles.info}>
        <Text style={styles.title} numberOfLines={2}>{item.title}</Text>
        <View style={styles.metaRow}>
          <Text style={styles.year}>{item.year}</Text>
          {timeLabel && (
            <Text style={hasProgress ? styles.durationLeft : styles.duration}>{timeLabel}</Text>
          )}
        </View>
      </View>
    </TouchableOpacity>
  );
}

// ── Grid section ──────────────────────────────────────────────────────────────

function GridSection({
  title, icon, accentColor, items, navigation, onLongPress, cardW, emptyMessage,
}: {
  title: string;
  icon: React.ComponentProps<typeof Ionicons>['name'];
  accentColor: string;
  items: TraktItem[];
  navigation: any;
  onLongPress: (item: TraktItem) => void;
  cardW: number;
  emptyMessage?: string;
}) {
  const { theme: { colors } } = useTheme();
  const isLightAppearance = colors.bg === '#f4f6fb';
  const styles = useMemo(() => StyleSheet.create({
    section:     { marginBottom: 32 },
    header:      { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 16 },
    iconWrap:    { width: 30, height: 30, borderRadius: 8, justifyContent: 'center', alignItems: 'center' },
    sectionTitle:{ flex: 1, color: colors.textPrimary, fontSize: 16, fontWeight: '800', letterSpacing: 0.3 },
    countPill:   { borderRadius: 20, paddingHorizontal: 10, paddingVertical: 3 },
    countText:   { fontSize: 12, fontWeight: '700' },
    grid:        { },
    gridRow:     { flexDirection: 'row', marginBottom: CARD_GAP },
    emptyText:   { color: colors.mutedText, fontSize: 13, lineHeight: 20 },
  }), [colors]);

  return (
    <View style={styles.section}>
      <View style={styles.header}>
        <View style={[styles.iconWrap, { backgroundColor: isLightAppearance ? 'rgba(17,24,39,0.10)' : accentColor + '22' }]}>
          <Ionicons name={icon} size={16} color={isLightAppearance ? colors.textPrimary : accentColor} />
        </View>
        <Text style={styles.sectionTitle}>{title}</Text>
      </View>
      {items.length === 0 ? (
        emptyMessage ? <Text style={styles.emptyText}>{emptyMessage}</Text> : null
      ) : (
        <View style={styles.grid}>
          {Array.from({ length: Math.ceil(items.length / (cardW > SCREEN_WIDTH / 3 ? 2 : 3)) }, (_, ri) => {
            const cols = cardW > SCREEN_WIDTH / 3 ? 2 : 3;
            const row = items.slice(ri * cols, ri * cols + cols);
            return (
              <View key={`row-${ri}`} style={styles.gridRow}>
                {row.map((item, ci) => (
                  <View key={mediaListItemKey(item, ri * cols + ci)} style={ci < row.length - 1 ? { marginRight: CARD_GAP } : null}>
                    <ContinueCard
                      item={item}
                      width={cardW}
                      onPress={() => navigation.navigate('Detail', { movieId: item.id, type: item.type })}
                      onLongPress={() => onLongPress(item)}
                    />
                  </View>
                ))}
              </View>
            );
          })}
        </View>
      )}
    </View>
  );
}

// ── Screen ────────────────────────────────────────────────────────────────────

const makeStyles = (c: ThemeColors) => StyleSheet.create({
  container:  { flex: 1, backgroundColor: c.bg },
  bottomAmbient: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: 220,
    pointerEvents: 'none',
  } as any,
  header: {
    position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10,
    paddingHorizontal: 16, paddingBottom: 12,
    backgroundColor: c.bgHeader,
  },
  headerRow: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 16 },
  filterRow:     { flexDirection: 'row', gap: 8 },
  filterPill:    { paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20, borderWidth: 1, borderColor: c.border, backgroundColor: c.cardBg },
  filterPillOn:  { borderColor: c.accent, backgroundColor: c.accent + '22' },
  filterText:    { fontSize: 13, fontWeight: '600', color: c.mutedText },
  filterTextOn:  { color: c.accentSoft, fontWeight: '700' },
  colToggleBtn:  {
    width: 36, height: 36, borderRadius: 18,
    backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center',
  },
  headerFade: { position: 'absolute', left: 0, right: 0, height: 32, zIndex: 9 },
  heading:   { flex: 1, color: c.textPrimary, fontSize: 28, fontWeight: '900', letterSpacing: 0.5 },
  countBadge:  { borderRadius: 20, paddingHorizontal: 10, paddingVertical: 4, backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border },
  countText:   { color: c.accentSoft, fontSize: 12, fontWeight: '700' },
  content:   { paddingHorizontal: H_PAD, paddingTop: 12 },
  empty:     { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 40 },
  emptyIconWrap: {
    width: 80, height: 80, borderRadius: 40,
    backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center', marginBottom: 20,
  },
  emptyTitle: { color: c.textPrimary, fontSize: 20, fontWeight: '700', marginBottom: 10, textAlign: 'center' },
  emptyDesc:  { color: c.textSecondary, fontSize: 14, textAlign: 'center', lineHeight: 22 },
});

export const ContinueWatchingScreen = ({ navigation }: any) => {
  const blurTargetRef = React.useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { theme: { colors } } = useTheme();
  const { t } = useLanguage();
  const styles = useMemo(() => makeStyles(colors), [colors]);

  const { continueWatching, refreshContinueWatching } = useTrakt();
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const [refreshing, setRefreshing] = useState(false);
  const [headerHeight, setHeaderHeight] = useState(0);
  const [filter, setFilter] = useState<FilterType>('all');
  const [localProgress, setLocalProgress] = useState<TraktItem[]>([]);
  const [gridCols, setGridCols] = useState<2 | 3>(3);
  const CARD_WIDTH = useMemo(() => cardWidth(gridCols), [gridCols]);
  const storageOwnerId = getProfileStorageOwnerId(user?.uid, activeProfile?.id);

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

  // Local progress is the primary source — scoped to the logged-in user
  const loadLocalProgress = useCallback(async () => {
    if (!user) { setLocalProgress([]); return; }
    runIdle(async () => {
      try {
        const indexKey = progressIndexStorageKey(storageOwnerId);
        const raw = await Storage.getItem(indexKey);
        if (!raw) { setLocalProgress([]); return; }
        const index: any[] = JSON.parse(raw);
        const items: TraktItem[] = index
          .filter(e => e.progressPct >= 0 && e.progressPct < 95)
          .map(e => ({
            id:       String(e.tmdbId),
            tmdbId:   e.tmdbId,
            title:    e.title,
            poster:   e.poster,
            backdrop: e.backdrop,
            type:     e.type as 'movie' | 'tv',
            year:     e.year,
            progress: e.progressPct,
          }));
        setLocalProgress(uniqueItemsById(items));
      } catch {
        setLocalProgress([]);
      }
    });
  }, [storageOwnerId, user]);

  useFocusEffect(
    useCallback(() => {
      loadLocalProgress();
    }, [loadLocalProgress])
  );

  // Trakt items whose tmdbId is already tracked locally are suppressed (local is more accurate)
  const localTmdbIds = useMemo(
    () => new Set(localProgress.map(i => Number(i.tmdbId))),
    [localProgress],
  );
  const traktFallback = useMemo(
    () => uniqueItemsById(continueWatching.filter(i => !localTmdbIds.has(Number(i.tmdbId)))),
    [continueWatching, localTmdbIds],
  );

  const filteredLocal = useMemo(() => {
    const base = filter === 'all' ? localProgress : localProgress.filter(i => i.type === filter);
    return uniqueItemsById(base);
  }, [localProgress, filter]);
  const filteredTrakt = useMemo(() => {
    const base = filter === 'all' ? traktFallback : traktFallback.filter(i => i.type === filter);
    return uniqueItemsById(base);
  }, [traktFallback, filter]);

  const FILTERS: { key: FilterType; label: string }[] = useMemo(() => [
    { key: 'all',   label: t('watchlist_filter_all') || 'All' },
    { key: 'movie', label: t('watchlist_filter_movies') || 'Movies' },
    { key: 'tv',    label: t('watchlist_filter_series') || 'TV Shows' },
  ], [t]);

  const {
    longPressItem, setLongPressItem, handleLongPress, buildActions,
    seriesWatchConfirmItem, setSeriesWatchConfirmItem, handleSeriesMarkWatched,
  } = useLongPressActions({
    navigation,
    onWatchedChange: () => {
      loadLocalProgress();
      refreshContinueWatching();
    },
  });

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    runIdle(async () => {
      await Promise.all([refreshContinueWatching(), loadLocalProgress()]);
      setRefreshing(false);
    });
  }, [refreshContinueWatching, loadLocalProgress]);

  const total = filteredLocal.length + filteredTrakt.length;

  return (
    <View style={{ flex: 1 }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
    <View style={styles.container}>
      <StatusBar barStyle="light-content" translucent backgroundColor="transparent" />
      <LinearGradient
        colors={[
          'rgba(0,0,0,0)',
          colors.accent + '10',
          colors.accent + '1c',
        ]}
        locations={[0, 0.58, 1]}
        style={styles.bottomAmbient}
      />

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

      <View
        style={[styles.header, { paddingTop: insets.top + 26 }]}
        onLayout={e => setHeaderHeight(e.nativeEvent.layout.height)}
      >
        <View style={styles.headerRow}>
          <Text style={styles.heading}>Continue Watching</Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            {total > 0 && (
              <View style={styles.countBadge}>
                <Text style={styles.countText}>
                  {total} title{total !== 1 ? 's' : ''}
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
      </View>

      {total === 0 && !refreshing ? (
        <View style={[styles.empty, { paddingTop: headerHeight }]}>
          <View style={styles.emptyIconWrap}>
            <Ionicons name="play-circle-outline" size={40} color={colors.textSecondary} />
          </View>
          <Text style={styles.emptyTitle}>Nothing here yet</Text>
          <Text style={styles.emptyDesc}>
            Start watching something — your in-progress titles will appear here automatically.
          </Text>
        </View>
      ) : (
        <ScrollView
          showsVerticalScrollIndicator={false}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.accent} progressViewOffset={headerHeight} />}
          contentContainerStyle={[styles.content, { paddingTop: headerHeight + 12, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }]}
        >
          <GridSection
            title="In Progress (Local)"
            icon="play-circle-outline"
            accentColor={colors.accent}
            items={filteredLocal}
            emptyMessage="No local in-progress titles yet."
            navigation={navigation}
            onLongPress={handleLongPress}
            cardW={CARD_WIDTH}
          />
          <GridSection
            title="In Progress (Trakt)"
            icon="time-outline"
            accentColor="#00b8d4"
            items={filteredTrakt}
            navigation={navigation}
            onLongPress={handleLongPress}
            cardW={CARD_WIDTH}
          />
        </ScrollView>
      )}
    </View>
      </BlurTargetView>
      <StackBottomNav activeTab="ContinueWatching" blurTarget={blurTargetRef} />
    </View>
  );
};
