import React, { memo, useCallback, useMemo } from 'react';
import { View, Text, FlatList, StyleSheet, TouchableOpacity } from 'react-native';
import { MediaCard } from './MediaCard';
import { FadeInView, SkeletonMediaCard, SkeletonText } from './Skeleton';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useLanguage } from '../context/LanguageContext';
import { mediaListItemKey, uniqueItemsById } from '../utils/watchlist';

interface SectionStripProps {
  title: string;
  data: any[];
  onViewAll: () => void;
  onItemPress: (item: any) => void;
  onItemLongPress?: (item: any) => void;
  loading?: boolean;
  badgeLabel?: string;
  cardVariant?: 'portrait' | 'landscape';
  cardLayout?: 'stacked' | 'horizontal';
  /** Override the default MediaCard with a custom renderer. */
  renderCard?: (item: any) => React.ReactNode;
}

const SKELETON = Array.from({ length: 6 }, (_, i) => ({ id: `sk-${i}`, skeleton: true }));

const makeStyles = (c: ThemeColors) => StyleSheet.create({
  section: { marginBottom: 32 },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 20, marginBottom: 14,
  },
  sectionTitle: { color: c.textPrimary, fontSize: 18, fontWeight: '800', letterSpacing: 0.3 },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 8, flexShrink: 1, paddingRight: 12 },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: c.accent + '44',
    backgroundColor: c.accent + '12',
  },
  badgeText: { color: c.accentSoft, fontSize: 10, fontWeight: '800', letterSpacing: 0.7, textTransform: 'uppercase' },
  viewAllBtn: {
    paddingVertical: 4, paddingHorizontal: 10, borderRadius: 20,
    borderWidth: 1, borderColor: c.accent + '55', backgroundColor: c.accent + '18',
  },
  viewAllText: { color: c.accentSoft, fontSize: 12, fontWeight: '600' },
  list: { paddingHorizontal: 20 },
});

export const SectionStrip = memo<SectionStripProps>(({ title, data, onViewAll, onItemPress, onItemLongPress, loading = false, badgeLabel, cardVariant = 'portrait', cardLayout = 'stacked', renderCard }) => {
  const { theme: { colors } } = useTheme();
  const { t } = useLanguage();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const display = useMemo(
    () => (loading ? SKELETON : uniqueItemsById(data)),
    [loading, data],
  );

  const renderItem = useCallback(({ item }: { item: any }) =>
    item.skeleton ? (
      <SkeletonMediaCard variant={cardVariant} layout={cardLayout} />
    ) : renderCard ? (
      <>{renderCard(item)}</>
    ) : (
      <MediaCard item={item} onPress={onItemPress} onLongPress={onItemLongPress} variant={cardVariant} layout={cardLayout} />
    ),
  [cardLayout, cardVariant, onItemPress, onItemLongPress, renderCard]);

  const keyExtractor = useCallback((item: any, i: number) => mediaListItemKey(item, i), []);

  return (
    <View style={styles.section}>
      <View style={styles.header}>
        {loading ? (
          <>
            <SkeletonText style={{ width: 156, height: 22 }} />
            <SkeletonText style={{ width: 76, height: 28, borderRadius: 20 }} />
          </>
        ) : (
          <>
            <View style={styles.titleRow}>
              <Text style={styles.sectionTitle}>{title}</Text>
              {badgeLabel ? (
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>{badgeLabel}</Text>
                </View>
              ) : null}
            </View>
            <TouchableOpacity onPress={onViewAll} activeOpacity={0.7} style={styles.viewAllBtn}>
              <Text style={styles.viewAllText}>{t('common_view_all')}</Text>
            </TouchableOpacity>
          </>
        )}
      </View>
      {loading ? (
        <FlatList
          data={display}
          horizontal
          keyExtractor={keyExtractor}
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.list}
          renderItem={renderItem}
          removeClippedSubviews
          maxToRenderPerBatch={6}
          windowSize={5}
          initialNumToRender={5}
        />
      ) : (
        <FadeInView>
          <FlatList
            data={display}
            horizontal
            keyExtractor={keyExtractor}
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.list}
            renderItem={renderItem}
            removeClippedSubviews
            maxToRenderPerBatch={6}
            windowSize={5}
            initialNumToRender={5}
          />
        </FadeInView>
      )}
    </View>
  );
});
