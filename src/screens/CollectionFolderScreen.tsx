import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { BlurTargetView } from 'expo-blur';
import { Ionicons } from '@expo/vector-icons';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useCollections } from '../context/CollectionsContext';
import { useAddons } from '../context/AddonContext';
import { fetchCollectionFolderItems, findCollectionFolder } from '../utils/collections';
import { MediaCard } from '../components/MediaCard';
import { StackBottomNav, BOTTOM_NAV_HEIGHT } from '../components/StackBottomNav';
import { useLongPressActions } from '../hooks/useLongPressActions';
import { ActionSheet } from '../components/ActionSheet';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { mediaListItemKey } from '../utils/watchlist';

const H_PAD = 14;
const GAP = 8;

function makeStyles(c: ThemeColors) {
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: c.bg },
    header: {
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      zIndex: 10,
      paddingHorizontal: 20,
      paddingBottom: 16,
      backgroundColor: c.bgHeader,
    },
    titleRow: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 8 },
    backBtn: {
      width: 38,
      height: 38,
      borderRadius: 19,
      backgroundColor: c.cardBgElevated,
      borderWidth: 1,
      borderColor: c.border,
      justifyContent: 'center',
      alignItems: 'center',
    },
    heading: { flex: 1, color: c.textPrimary, fontSize: 28, fontWeight: '800', letterSpacing: -0.8 },
    subheading: { color: c.textSecondary, fontSize: 13, lineHeight: 19 },
    grid: { paddingHorizontal: H_PAD, paddingTop: 12 },
    row: { justifyContent: 'space-between', marginBottom: GAP },
    emptyWrap: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 28 },
    emptyTitle: { color: c.textPrimary, fontSize: 18, fontWeight: '800', marginTop: 12, marginBottom: 8, textAlign: 'center' },
    emptySub: { color: c.textSecondary, fontSize: 13, lineHeight: 20, textAlign: 'center' },
  });
}

export function CollectionFolderScreen({ navigation, route }: any) {
  const blurTargetRef = React.useRef<View | null>(null);
  const { theme: { colors }, resolvedAppearance } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const insets = useSafeAreaInsets();
  const { collections } = useCollections();
  const { addons } = useAddons();
  const { collectionId, folderId } = route.params ?? {};
  const resolved = useMemo(() => findCollectionFolder(collections, String(collectionId ?? ''), String(folderId ?? '')), [collectionId, collections, folderId]);
  const [items, setItems] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const {
    longPressItem, setLongPressItem, handleLongPress, buildActions,
    seriesWatchConfirmItem, setSeriesWatchConfirmItem, handleSeriesMarkWatched,
  } = useLongPressActions({ navigation });

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    void (async () => {
      if (!resolved) {
        if (!cancelled) {
          setItems([]);
          setLoading(false);
        }
        return;
      }
      const nextItems = await fetchCollectionFolderItems(resolved.folder, addons);
      if (!cancelled) {
        setItems(nextItems);
        setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [addons, resolved]);

  const handlePress = useCallback((item: any) => {
    navigation.navigate('Detail', {
      movieId: item.tmdbId ?? item.id,
      type: item.type,
      imdbId: item.imdbId ?? undefined,
      title: item.title,
      synopsis: item.description ?? undefined,
      backdrop: item.backdrop ?? item.poster ?? undefined,
      poster: item.poster ?? undefined,
      year: item.year,
      rating: item.rating,
    });
  }, [navigation]);

  const cardWidth = useMemo(() => {
    const screenWidth = 390;
    return (screenWidth - H_PAD * 2 - GAP * 2) / 3;
  }, []);

  const headerHeight = insets.top + 98;

  return (
    <View style={styles.container}>
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

          <View style={[styles.header, { paddingTop: insets.top + 26 }]}>
            <View style={styles.titleRow}>
              <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()} activeOpacity={0.8}>
                <Ionicons name="chevron-back" size={20} color={colors.accentSoft} />
              </TouchableOpacity>
              <Text style={styles.heading} numberOfLines={1}>{resolved?.folder.title ?? 'Collection Folder'}</Text>
            </View>
            <Text style={styles.subheading} numberOfLines={2}>
              {resolved ? `${resolved.collection.title} â€¢ ${items.length} item${items.length === 1 ? '' : 's'}` : 'Collection folder not found'}
            </Text>
          </View>

          {loading ? (
            <View style={styles.emptyWrap}>
              <ActivityIndicator size="large" color={colors.accent} />
            </View>
          ) : !resolved || items.length === 0 ? (
            <View style={styles.emptyWrap}>
              <Ionicons name="film-outline" size={34} color={colors.placeholder} />
              <Text style={styles.emptyTitle}>Nothing to show here</Text>
              <Text style={styles.emptySub}>This folder does not currently resolve to playable items with StreamDek's supported addon/TMDB sources.</Text>
            </View>
          ) : (
            <FlatList
              data={items}
              key="collection-folder-grid"
              numColumns={3}
              keyExtractor={(item, index) => mediaListItemKey(item, index)}
              columnWrapperStyle={styles.row}
              contentContainerStyle={[styles.grid, { paddingTop: headerHeight, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 16 }]}
              renderItem={({ item }) => (
                <MediaCard
                  item={item}
                  width={cardWidth}
                  compactGrid
                  onPress={() => handlePress(item)}
                  onLongPress={() => handleLongPress(item)}
                />
              )}
            />
          )}
        </View>
      </BlurTargetView>
      <StackBottomNav blurTarget={blurTargetRef} />
    </View>
  );
}
