import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import DraggableFlatList, { RenderItemParams } from 'react-native-draggable-flatlist';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { BlurTargetView } from 'expo-blur';
import { StackBottomNav } from '../components/StackBottomNav';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { EntranceFade } from '../components/EntranceFade';
import { AppleToggle } from '../components/AppleToggle';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useLanguage } from '../context/LanguageContext';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useAddons } from '../context/AddonContext';
import { useTmdbApiKey } from '../context/TmdbApiKeyContext';
import { Storage } from '../utils/storage';
import { buildAddonHomeSections, buildDefaultHomeSections, type HomeCatalogSection } from '../utils/homeCatalogSections';
import { getHomeSectionStorageKeys, mergeSavedHomeSections } from '../utils/homeLayoutConfig';

const CURRENT_YEAR = new Date().getFullYear();

function makeStyles(c: ThemeColors) {
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: c.bg },
    title: { color: c.textPrimary, fontSize: 30, fontWeight: '900', letterSpacing: -0.6 },
    subtitle: { color: c.textSecondary, fontSize: 14, lineHeight: 20, marginTop: 8, marginBottom: 20 },
    sectionTitle: { color: c.textPrimary, fontSize: 14, fontWeight: '800', letterSpacing: 0.4, marginBottom: 10 },
    card: {
      backgroundColor: c.cardBgElevated ?? c.cardBg,
      borderWidth: 1,
      borderColor: c.border,
      borderRadius: 22,
      overflow: 'hidden',
      marginBottom: 28,
    },
    row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 18, paddingVertical: 16 },
    rowIcon: { width: 34, height: 34, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
    rowInfo: { flex: 1 },
    rowLabel: { color: c.textPrimary, fontSize: 16, fontWeight: '700' },
    rowSub: { color: c.textSecondary, fontSize: 13, lineHeight: 18, marginTop: 3 },
    divider: { height: 1, backgroundColor: c.borderSoft, marginLeft: 58 },
    providerChoiceRow: {
      flexDirection: 'row',
      gap: 10,
      paddingHorizontal: 18,
      paddingTop: 16,
      paddingBottom: 14,
    },
    providerChoice: {
      flex: 1,
      flexDirection: 'row',
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      gap: 10,
      paddingHorizontal: 14,
      paddingVertical: 14,
      borderWidth: 1,
      borderColor: c.border,
      borderRadius: 16,
      backgroundColor: c.inputBg,
    },
    providerChoiceActive: {
      backgroundColor: `${c.accent}10`,
      borderColor: c.accent,
    },
    radioWrap: {
      width: 20,
      height: 20,
      borderRadius: 10,
      borderWidth: 1.5,
      borderColor: c.border,
      alignItems: 'center',
      justifyContent: 'center',
    },
    radioDot: {
      width: 8,
      height: 8,
      borderRadius: 4,
      backgroundColor: c.accent,
    },
    catalogHeader: {
      paddingHorizontal: 16,
      paddingTop: 14,
      paddingBottom: 10,
      borderBottomWidth: 1,
      borderBottomColor: c.borderSoft,
    },
    catalogHeaderCard: {
      backgroundColor: c.cardBgElevated ?? c.cardBg,
      borderWidth: 1,
      borderColor: c.border,
      borderTopLeftRadius: 22,
      borderTopRightRadius: 22,
      overflow: 'hidden',
    },
    catalogTitle: { color: c.textPrimary, fontSize: 14, fontWeight: '800', letterSpacing: 0.3 },
    catalogHint: { color: c.textSecondary, fontSize: 12, lineHeight: 17, marginTop: 4 },
    catalogItemShell: {
      marginHorizontal: 20,
      backgroundColor: c.cardBgElevated ?? c.cardBg,
      borderLeftWidth: 1,
      borderRightWidth: 1,
      borderColor: c.border,
    },
    catalogItemShellLast: {
      borderBottomWidth: 1,
      borderBottomLeftRadius: 22,
      borderBottomRightRadius: 22,
      overflow: 'hidden',
      marginBottom: 28,
    },
    layoutRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 14, paddingVertical: 14 },
    layoutGrip: { width: 28, alignItems: 'center', justifyContent: 'center' },
    layoutInfo: { flex: 1 },
    layoutLabel: { color: c.textPrimary, fontSize: 15, fontWeight: '700' },
    layoutSub: { color: c.textSecondary, fontSize: 12, lineHeight: 17, marginTop: 3 },
    layoutDivider: { height: 1, backgroundColor: c.borderSoft, marginHorizontal: 14 },
    layoutDragActive: {
      backgroundColor: c.cardBg,
      borderRadius: 14,
      marginHorizontal: 10,
      marginVertical: 4,
    },
  });
}

function SettingRow({
  icon,
  color,
  label,
  subtitle,
  right,
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  color: string;
  label: string;
  subtitle: string;
  right: React.ReactNode;
}) {
  const { theme: { colors } } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);

  return (
    <View style={styles.row}>
      <View style={[styles.rowIcon, { backgroundColor: `${color}22` }]}>
        <Ionicons name={icon} size={18} color={color} />
      </View>
      <View style={styles.rowInfo}>
        <Text style={styles.rowLabel}>{label}</Text>
        <Text style={styles.rowSub}>{subtitle}</Text>
      </View>
      {right}
    </View>
  );
}

export function HomeLayoutSettingsScreen({ navigation }: any) {
  const blurTargetRef = React.useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const { t } = useLanguage();
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const { addons } = useAddons();
  const {
    metadataProvider,
    homeCatalogProviders,
    defaultCatalogsEnabled,
    setDefaultCatalogsEnabled,
    setHomeCatalogProviderEnabled,
  } = useTmdbApiKey();
  const [sections, setSections] = useState<HomeCatalogSection[]>([]);
  const selectedHomeCatalogProvider = homeCatalogProviders[0] ?? metadataProvider;
  const navClearance = BOTTOM_NAV_HEIGHT + insets.bottom + 24;

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
      },
      metadataProvider,
    );
    return [...builtin, ...buildAddonHomeSections(addons, { movie: t('catalog_type_movies'), tv: t('catalog_type_series') })];
  }, [addons, homeCatalogProviders, metadataProvider, t]);

  const storageKeys = useMemo(
    () => getHomeSectionStorageKeys(user?.uid, activeProfile?.id),
    [activeProfile?.id, user?.uid],
  );

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        let saved: string | null = null;
        for (const key of storageKeys) {
          saved = await Storage.getItem(key);
          if (saved) break;
        }

        if (!saved) {
          if (!cancelled) setSections(defaultSections);
          return;
        }

        const parsed = JSON.parse(saved);
        if (!Array.isArray(parsed)) {
          if (!cancelled) setSections(defaultSections);
          return;
        }

        const merged = mergeSavedHomeSections(parsed, defaultSections, selectedHomeCatalogProvider);
        if (!cancelled) setSections(merged);
      } catch {
        if (!cancelled) setSections(defaultSections);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [defaultSections, selectedHomeCatalogProvider, storageKeys]);

  const persistSections = useCallback(async (nextSections: HomeCatalogSection[]) => {
    setSections(nextSections);
    const payload = JSON.stringify(nextSections.map(section => ({
      id: section.id,
      enabled: section.enabled,
    })));
    await Promise.all(storageKeys.map(key => Storage.setItem(key, payload)));
  }, [storageKeys]);

  const toggleSection = useCallback((id: string, enabled: boolean) => {
    const next = sections.map(section => (section.id === id ? { ...section, enabled } : section));
    void persistSections(next);
  }, [persistSections, sections]);

  const handleReorder = useCallback(({ data }: { data: HomeCatalogSection[] }) => {
    void persistSections(data);
  }, [persistSections]);

  const renderItem = useCallback(({ item, drag, isActive, getIndex }: RenderItemParams<HomeCatalogSection>) => {
    const index = getIndex?.() ?? 0;
    const isLast = index === sections.length - 1;

    return (
      <View style={[styles.catalogItemShell, isLast && styles.catalogItemShellLast]}>
        <View style={isActive ? styles.layoutDragActive : undefined}>
          <View style={styles.layoutRow}>
            <TouchableOpacity style={styles.layoutGrip} onLongPress={drag} delayLongPress={240}>
              <Ionicons name="reorder-three-outline" size={20} color={colors.placeholder} />
            </TouchableOpacity>
            <View style={styles.layoutInfo}>
              <Text style={styles.layoutLabel}>{item.title}</Text>
              <Text style={styles.layoutSub}>{item.enabled ? t('settings_home_layout_visible') : t('settings_home_layout_hidden')}</Text>
            </View>
            <AppleToggle
              value={item.enabled}
              onValueChange={(value: boolean) => { toggleSection(item.id, value); }}
              onColor={colors.toggleOn}
            />
          </View>
        </View>
      </View>
    );
  }, [colors.placeholder, colors.toggleOn, sections.length, styles.catalogItemShell, styles.catalogItemShellLast, styles.layoutDragActive, styles.layoutGrip, styles.layoutInfo, styles.layoutLabel, styles.layoutRow, styles.layoutSub, t, toggleSection]);

  const listHeader = (
    <>
      <EntranceFade index={0}>
        <View style={{ paddingHorizontal: 20, paddingTop: insets.top + 18 }}>
          <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.78} style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 14 }}>
            <Ionicons name="chevron-back" size={20} color={colors.textSecondary} />
            <Text style={{ color: colors.textSecondary, fontSize: 14, fontWeight: '600' }}>{t('common_back')}</Text>
          </TouchableOpacity>
          <Text style={styles.title}>{t('settings_catalog_home_layout')}</Text>
          <Text style={styles.subtitle}>{t('settings_home_layout_modal_sub')}</Text>
          <Text style={styles.sectionTitle}>Catalog</Text>
        </View>
      </EntranceFade>

      <EntranceFade index={1}>
        <View style={{ paddingHorizontal: 20 }}>
          <View style={styles.card}>
            <SettingRow
              icon="layers-outline"
              color="#22c55e"
              label={t('settings_default_catalogs')}
              subtitle={t('settings_default_catalogs_sub')}
              right={<AppleToggle value={defaultCatalogsEnabled} onValueChange={(value: boolean) => { void setDefaultCatalogsEnabled(value); }} onColor={colors.toggleOn} />}
            />
            {defaultCatalogsEnabled ? (
              <>
                <View style={styles.divider} />
                <View style={styles.providerChoiceRow}>
                  <TouchableOpacity
                    activeOpacity={0.82}
                    style={[styles.providerChoice, selectedHomeCatalogProvider === 'cinemeta' && styles.providerChoiceActive]}
                    onPress={() => { void setHomeCatalogProviderEnabled('cinemeta', true); }}
                  >
                    <View style={styles.rowInfo}>
                      <Text style={[styles.rowLabel, { fontSize: 14 }]} numberOfLines={1}>{t('settings_cinemeta')}</Text>
                      <Text style={[styles.rowSub, { fontSize: 11, lineHeight: 15 }]} numberOfLines={2}>Built-in metadata</Text>
                    </View>
                    <View style={[styles.radioWrap, selectedHomeCatalogProvider === 'cinemeta' && { borderColor: colors.accent }]}>
                      {selectedHomeCatalogProvider === 'cinemeta' ? <View style={styles.radioDot} /> : null}
                    </View>
                  </TouchableOpacity>
                  <TouchableOpacity
                    activeOpacity={0.82}
                    style={[styles.providerChoice, selectedHomeCatalogProvider === 'tmdb' && styles.providerChoiceActive]}
                    onPress={() => { void setHomeCatalogProviderEnabled('tmdb', true); }}
                  >
                    <View style={styles.rowInfo}>
                      <Text style={[styles.rowLabel, { fontSize: 14 }]} numberOfLines={1}>{t('settings_tmdb')}</Text>
                      <Text style={[styles.rowSub, { fontSize: 11, lineHeight: 15 }]} numberOfLines={2}>TMDB-powered rows</Text>
                    </View>
                    <View style={[styles.radioWrap, selectedHomeCatalogProvider === 'tmdb' && { borderColor: colors.accent }]}>
                      {selectedHomeCatalogProvider === 'tmdb' ? <View style={styles.radioDot} /> : null}
                    </View>
                  </TouchableOpacity>
                </View>
              </>
            ) : null}
          </View>
          <View style={styles.catalogHeaderCard}>
            <View style={styles.catalogHeader}>
              <Text style={styles.catalogTitle}>Catalog</Text>
              <Text style={styles.catalogHint}>{t('settings_home_layout_modal_hint')}</Text>
            </View>
          </View>
        </View>
      </EntranceFade>
    </>
  );

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
        <GestureHandlerRootView style={styles.container}>
          <StatusBar
            barStyle={resolvedAppearance === 'light' ? 'dark-content' : 'light-content'}
            translucent
            backgroundColor="transparent"
          />
          <DraggableFlatList
            data={sections}
            keyExtractor={item => item.id}
            renderItem={renderItem}
            onDragEnd={handleReorder}
            showsVerticalScrollIndicator={false}
            activationDistance={20}
            autoscrollSpeed={180}
            ItemSeparatorComponent={() => <View style={styles.layoutDivider} />}
            ListHeaderComponent={listHeader}
            ListFooterComponent={<View style={{ height: navClearance }} />}
            contentContainerStyle={{ paddingBottom: 8 }}
          />
        </GestureHandlerRootView>
      </BlurTargetView>
      <StackBottomNav activeTab="Settings" blurTarget={blurTargetRef} />
    </View>
  );
}
