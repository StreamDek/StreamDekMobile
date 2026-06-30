import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import DraggableFlatList, { RenderItemParams } from 'react-native-draggable-flatlist';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { BlurTargetView } from 'expo-blur';
import { StackBottomNav, BOTTOM_NAV_HEIGHT } from '../components/StackBottomNav';
import { AppleToggle } from '../components/AppleToggle';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useCollections } from '../context/CollectionsContext';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useAddons } from '../context/AddonContext';
import { useTmdbApiKey } from '../context/TmdbApiKeyContext';
import { useLanguage } from '../context/LanguageContext';
import { Storage } from '../utils/storage';
import { resolveCollectionFolderSources, buildCollectionHomeSections, type Collection, type CollectionFolder } from '../utils/collections';
import { buildAddonHomeSections, buildDefaultHomeSections, type HomeCatalogSection } from '../utils/homeCatalogSections';
import { getHomeSectionStorageKeys, mergeSavedHomeSections } from '../utils/homeLayoutConfig';

const CURRENT_YEAR = new Date().getFullYear();

function makeStyles(c: ThemeColors) {
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: c.bg },
    content: { flex: 1 },
    listContent: { paddingHorizontal: 20, paddingBottom: 120 },
    backRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 16 },
    backText: { color: c.textSecondary, fontSize: 14, fontWeight: '600' },
    title: { color: c.textPrimary, fontSize: 30, fontWeight: '900', letterSpacing: -0.6 },
    subtitle: { color: c.textSecondary, fontSize: 14, lineHeight: 20, marginTop: 8, marginBottom: 20 },
    card: { backgroundColor: c.cardBgElevated ?? c.cardBg, borderWidth: 1, borderColor: c.border, borderRadius: 22, overflow: 'hidden', marginBottom: 18 },
    sectionTitle: { color: c.textPrimary, fontSize: 14, fontWeight: '800', letterSpacing: 0.4, marginBottom: 10 },
    summary: { color: c.textSecondary, fontSize: 14, lineHeight: 20 },
    row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 16 },
    grip: { width: 28, alignItems: 'center' },
    rowBody: { flex: 1 },
    rowTitle: { color: c.textPrimary, fontSize: 15, fontWeight: '700' },
    rowSub: { color: c.textSecondary, fontSize: 12, lineHeight: 18, marginTop: 3 },
    divider: { height: 1, backgroundColor: c.borderSoft, marginLeft: 56 },
    actionIcon: { padding: 6 },
    emptyWrap: { padding: 28, alignItems: 'center' },
    emptyTitle: { color: c.textPrimary, fontSize: 18, fontWeight: '800', marginTop: 14, marginBottom: 8, textAlign: 'center' },
    emptySub: { color: c.textSecondary, fontSize: 13, lineHeight: 20, textAlign: 'center' },
  });
}

export function CollectionDetailScreen({ navigation, route }: any) {
  const blurTargetRef = React.useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { theme: { colors }, resolvedAppearance } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const { t } = useLanguage();
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const { addons } = useAddons();
  const { metadataProvider, homeCatalogProviders } = useTmdbApiKey();
  const { collections, replaceCollections } = useCollections();
  const collectionId = String(route?.params?.collectionId ?? '');
  const collection = useMemo(() => collections.find(item => item.id === collectionId) ?? null, [collectionId, collections]);
  const storageKeys = useMemo(() => getHomeSectionStorageKeys(user?.uid, activeProfile?.id), [activeProfile?.id, user?.uid]);
  const [sections, setSections] = useState<HomeCatalogSection[]>([]);

  const defaultSections = useMemo(() => {
    const builtin = buildDefaultHomeSections(
      homeCatalogProviders,
      CURRENT_YEAR,
      {
        networks: t('section_networks'),
        featuredMovies: t('section_featured_movies'),
        featuredSeries: t('section_featured_series'),
        popularMovies: t('section_popular_movies'),
        popularTv: t('section_popular_tv'),
        documentaries: t('section_documentaries'),
        newMovies: t('section_new_movies'),
        newSeries: t('section_new_series'),
        trendingMovies: t('section_trending_movies'),
        trendingTv: t('section_trending_tv'),
        recommended: t('section_recommended'),
      },
      metadataProvider,
    );
    return [
      ...builtin,
      ...buildCollectionHomeSections(collections),
      ...buildAddonHomeSections(addons, { movie: t('catalog_type_movies'), tv: t('catalog_type_series') }),
    ];
  }, [addons, collections, homeCatalogProviders, metadataProvider, t]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        let saved: string | null = null;
        for (const key of storageKeys) {
          saved = await Storage.getItem(key);
          if (saved) break;
        }
        const parsed = saved ? JSON.parse(saved) : [];
        const merged = Array.isArray(parsed)
          ? mergeSavedHomeSections(parsed, defaultSections, homeCatalogProviders[0] ?? metadataProvider)
          : defaultSections;
        if (!cancelled) setSections(merged);
      } catch {
        if (!cancelled) setSections(defaultSections);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [defaultSections, homeCatalogProviders, metadataProvider, storageKeys]);

  const persistSections = useCallback(async (nextSections: HomeCatalogSection[]) => {
    setSections(nextSections);
    const payload = JSON.stringify(nextSections.map(section => ({ id: section.id, enabled: section.enabled })));
    await Promise.all(storageKeys.map(key => Storage.setItem(key, payload)));
  }, [storageKeys]);

  const isFolderVisibleOnHome = useCallback((folderId: string) => {
    const sectionId = `collection:${collectionId}:${folderId}`;
    return sections.find(section => section.id === sectionId)?.enabled ?? true;
  }, [collectionId, sections]);

  const toggleFolderVisibility = useCallback((folderId: string, enabled: boolean) => {
    const sectionId = `collection:${collectionId}:${folderId}`;
    const nextSections = sections.map(section => section.id === sectionId ? { ...section, enabled } : section);
    void persistSections(nextSections);
  }, [collectionId, persistSections, sections]);

  const moveFolder = useCallback(async (fromIndex: number, toIndex: number) => {
    if (!collection) return;
    if (fromIndex === toIndex) return;
    if (fromIndex < 0 || toIndex < 0 || fromIndex >= collection.folders.length || toIndex >= collection.folders.length) return;

    const nextFolders = collection.folders.slice();
    const [moved] = nextFolders.splice(fromIndex, 1);
    nextFolders.splice(toIndex, 0, moved);

    const nextCollections = collections.map(item =>
      item.id === collection.id ? { ...item, folders: nextFolders } : item,
    );

    await replaceCollections(nextCollections);
  }, [collection, collections, replaceCollections]);

  const renderHeader = () => (
    <View style={{ paddingTop: insets.top + 18 }}>
      <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.8} style={styles.backRow}>
        <Ionicons name="chevron-back" size={20} color={colors.textSecondary} />
        <Text style={styles.backText}>{t('common_back')}</Text>
      </TouchableOpacity>
      <Text style={styles.title}>{collection?.title ?? 'Collection'}</Text>
      <Text style={styles.subtitle}>Manage this collection's folders, reorder the categories, and choose which ones appear on the home screen.</Text>

      <View style={[styles.card, { padding: 18 }]}> 
        <Text style={styles.summary}>
          {collection ? `${collection.folders.length} folder${collection.folders.length === 1 ? '' : 's'} � ${collection.folders.filter(folder => isFolderVisibleOnHome(folder.id)).length} visible on home` : 'Collection not found'}
        </Text>
      </View>

      <Text style={styles.sectionTitle}>Folders</Text>
    </View>
  );

  const renderFolderRow = ({ item, drag, getIndex, isActive }: RenderItemParams<CollectionFolder>) => {
    const index = getIndex?.() ?? 0;
    const isLast = index === (collection?.folders.length ?? 1) - 1;
    const sourceCount = resolveCollectionFolderSources(item).length;
    const visibleOnHome = isFolderVisibleOnHome(item.id);

    return (
      <View style={[styles.card, index > 0 && { marginTop: 0 }, isLast && { marginBottom: 0 }]}>
        <View style={styles.row}>
          <TouchableOpacity style={styles.grip} onLongPress={drag} delayLongPress={220}>
            <Ionicons name="reorder-three-outline" size={20} color={colors.placeholder} />
          </TouchableOpacity>
          <View style={styles.rowBody}>
            <Text style={styles.rowTitle}>{item.title}</Text>
            <Text style={styles.rowSub}>{sourceCount} source{sourceCount === 1 ? '' : 's'} � {visibleOnHome ? 'Visible on home' : 'Hidden from home'}</Text>
          </View>
          <TouchableOpacity
            onPress={() => navigation.navigate('CollectionFolder', { collectionId, folderId: item.id })}
            style={styles.actionIcon}
            activeOpacity={0.8}
          >
            <Ionicons name="open-outline" size={18} color={colors.accentSoft} />
          </TouchableOpacity>
          <AppleToggle
            value={visibleOnHome}
            onValueChange={(value: boolean) => { toggleFolderVisibility(item.id, value); }}
            onColor={colors.toggleOn}
          />
        </View>
        {!isActive && !isLast ? <View style={styles.divider} /> : null}
      </View>
    );
  };

  return (
    <View style={styles.container}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
        <GestureHandlerRootView style={styles.container}>
          <StatusBar barStyle={resolvedAppearance === 'light' ? 'dark-content' : 'light-content'} translucent backgroundColor="transparent" />
          <View style={styles.content}>
            {!collection ? (
              <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={[styles.listContent, { paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }]}>
                {renderHeader()}
                <View style={[styles.card, styles.emptyWrap]}>
                  <Ionicons name="folder-open-outline" size={34} color={colors.placeholder} />
                  <Text style={styles.emptyTitle}>Collection not found</Text>
                  <Text style={styles.emptySub}>This collection is no longer available in the current profile.</Text>
                </View>
              </ScrollView>
            ) : collection.folders.length === 0 ? (
              <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={[styles.listContent, { paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }]}>
                {renderHeader()}
                <View style={[styles.card, styles.emptyWrap]}>
                  <Ionicons name="albums-outline" size={34} color={colors.placeholder} />
                  <Text style={styles.emptyTitle}>No folders yet</Text>
                  <Text style={styles.emptySub}>Import a collection that includes folders/categories to expose it on the home screen.</Text>
                </View>
              </ScrollView>
            ) : (
              <DraggableFlatList
                data={collection.folders}
                keyExtractor={item => item.id}
                renderItem={renderFolderRow}
                onDragEnd={({ from, to }) => { void moveFolder(from, to); }}
                showsVerticalScrollIndicator={false}
                activationDistance={16}
                contentContainerStyle={[styles.listContent, { paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }]}
                ListHeaderComponent={renderHeader}
              />
            )}
          </View>
        </GestureHandlerRootView>
      </BlurTargetView>
      <StackBottomNav activeTab="Settings" blurTarget={blurTargetRef} />
    </View>
  );
}
