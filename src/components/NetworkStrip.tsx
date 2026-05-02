import React, { memo, useCallback, useMemo, useState } from 'react';
import { View, Text, FlatList, StyleSheet, TouchableOpacity } from 'react-native';
import { Image } from 'expo-image';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { FadeInView, SkeletonText } from './Skeleton';
import { mediaListItemKey } from '../utils/watchlist';

interface NetworkStripProps {
  title: string;
  data: any[];
  onNetworkPress: (item: any) => void;
  loading?: boolean;
}

const SKELETON = Array.from({ length: 6 }, (_, i) => ({ id: `network-sk-${i}`, skeleton: true }));

const toPngUrl = (uri: string | null | undefined) => {
  if (!uri) return null;
  return uri.replace(/\.svg(\?.*)?$/i, '.png$1');
};

const NetworkCard = memo(function NetworkCard({
  name,
  logo,
  onPress,
  styles,
}: {
  name: string;
  logo: string | null;
  onPress: () => void;
  styles: ReturnType<typeof makeStyles>;
}) {
  const [logoFailed, setLogoFailed] = useState(false);
  const nameParts = String(name ?? '').trim().split(/\s+/);
  const initials = nameParts.slice(0, 2).map(part => part[0]).join('').toUpperCase();
  const imageUri = toPngUrl(logo);
  const showFallback = logoFailed || !imageUri;

  return (
    <View style={styles.cardWrap}>
      <TouchableOpacity
        activeOpacity={0.82}
        onPress={onPress}
        style={styles.cardPress}
      >
        <View style={styles.card}>
          {showFallback ? (
            <View style={styles.fallback}>
              <Text style={styles.fallbackText} numberOfLines={2}>{initials || 'TV'}</Text>
            </View>
          ) : (
            <Image
              source={{ uri: imageUri }}
              style={styles.logo}
              contentFit="contain"
              cachePolicy="memory-disk"
              transition={180}
              onError={() => setLogoFailed(true)}
            />
          )}
        </View>
      </TouchableOpacity>
      <Text style={styles.name} numberOfLines={1}>{name}</Text>
    </View>
  );
});

const makeStyles = (c: ThemeColors) => StyleSheet.create({
  section: { marginBottom: 32 },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 20, marginBottom: 14,
  },
  sectionTitle: { color: c.textPrimary, fontSize: 23, fontWeight: '700', letterSpacing: 0.3 },
  list: { paddingHorizontal: 20 },
  cardWrap: { marginRight: 14, width: 142 },
  card: {
    height: 84,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: c.border,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  cardPress: { transform: [{ scale: 1 }] },
  logo: { width: '74%', height: '60%' },
  fallback: {
    width: '74%',
    height: '60%',
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f3f4f6',
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.08)',
  },
  fallbackText: {
    color: c.textPrimary,
    fontSize: 15,
    fontWeight: '800',
    letterSpacing: 0.2,
    textAlign: 'center',
    paddingHorizontal: 6,
  },
  name: {
    color: c.textPrimary,
    fontSize: 12,
    fontWeight: '700',
    marginTop: 8,
    textAlign: 'center',
  },
  skeletonCard: {
    height: 84,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: c.border,
    backgroundColor: c.cardBgElevated ?? c.cardBg,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
});

export const NetworkStrip = memo<NetworkStripProps>(({ title, data, onNetworkPress, loading = false }) => {
  const { theme: { colors } } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const display = useMemo(() => (loading ? SKELETON : data), [loading, data]);

  const renderItem = useCallback(({ item }: { item: any }) => {
    if (item.skeleton) {
      return (
        <View style={styles.cardWrap}>
          <View style={styles.skeletonCard}>
            <SkeletonText style={{ width: '72%', height: 18, borderRadius: 9 }} />
          </View>
          <SkeletonText style={{ width: '80%', height: 12, borderRadius: 6, marginTop: 8, alignSelf: 'center' }} />
        </View>
      );
    }

    return <NetworkCard name={item.name} logo={item.logo} onPress={() => onNetworkPress(item)} styles={styles} />;
  }, [onNetworkPress, styles]);

  const keyExtractor = useCallback((item: any, i: number) => mediaListItemKey(item, i), []);

  return (
    <View style={styles.section}>
      <View style={styles.header}>
        {loading ? (
          <SkeletonText style={{ width: 120, height: 22 }} />
        ) : (
          <Text style={styles.sectionTitle}>{title}</Text>
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
