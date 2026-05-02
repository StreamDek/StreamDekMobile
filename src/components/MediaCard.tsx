import React, { useRef, memo } from 'react';
import { View, Text, StyleSheet, Animated, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { useTheme } from '../context/ThemeContext';
import { RatingBadge } from './RatingBadge';

interface MediaCardProps {
  item: any;
  onPress: (item: any) => void;
  onLongPress?: (item: any) => void;
  width?: number;
  compactGrid?: boolean;
  variant?: 'portrait' | 'landscape';
  layout?: 'stacked' | 'horizontal';
}

function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes}m`;
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

export const MediaCard = memo<MediaCardProps>(({ item, onPress, onLongPress, width, compactGrid = false, variant = 'portrait', layout = 'stacked' }) => {
  const { theme: { colors, resolvedAppearance } } = useTheme();
  const scale = useRef(new Animated.Value(1)).current;
  const isHorizontal = false;
  const cardWidth = width ?? (isHorizontal ? 316 : (variant === 'landscape' ? 224 : 130));
  const posterWidth = isHorizontal ? 128 : cardWidth;
  const posterHeight = isHorizontal
    ? 72
    : (variant === 'landscape'
      ? Math.round(cardWidth * 9 / 16)
      : (compactGrid ? Math.round(cardWidth * 1.5) : 195));

  const onPressIn  = () => Animated.spring(scale, { toValue: 0.94, useNativeDriver: true }).start();
  const onPressOut = () => Animated.spring(scale, { toValue: 1, friction: 4, useNativeDriver: true }).start();

  const ratingTextColor = (item.rating ?? 0) >= 7 ? '#00e676' : (item.rating ?? 0) >= 5 ? '#ffd740' : '#c97070';
  const titleColor = resolvedAppearance === 'light' ? colors.textPrimary : '#e8e8f0';
  const titleHorizontalColor = resolvedAppearance === 'light' ? colors.textPrimary : '#f3f3fb';
  const metaColor = resolvedAppearance === 'light' ? colors.textSecondary : '#7070a0';

  const hasProgress       = typeof item.progress          === 'number' && item.progress          > 0;
  const hasRuntime        = typeof item.runtime           === 'number' && item.runtime           > 0;
  const hasUnwatched      = typeof item.unwatchedEpisodes === 'number' && item.unwatchedEpisodes > 0;
  // Time label: "Xm left" when in progress, or "Xh Xm" as total
  let timeLabel: string | null = null;
  if (hasProgress && hasRuntime) {
    const remaining = Math.max(1, Math.round(item.runtime * (1 - item.progress / 100)));
    timeLabel = `${formatDuration(remaining)} left`;
  } else if (hasRuntime) {
    timeLabel = formatDuration(item.runtime);
  }

  if (isHorizontal) {
    return (
      <Animated.View style={[
        styles.card,
        styles.horizontalCard,
        compactGrid && styles.cardGrid,
        { width: cardWidth, transform: [{ scale }], backgroundColor: colors.cardBg, borderWidth: 1, borderColor: colors.border },
      ]}>
        <Pressable
          hitSlop={{ top: 15, bottom: 15, left: 15, right: 15 }}
          onPress={() => onPress(item)}
          onLongPress={onLongPress ? () => onLongPress(item) : undefined}
          delayLongPress={350}
          onPressIn={onPressIn}
          onPressOut={onPressOut}
        >
          <View style={styles.horizontalRow}>
            <View style={styles.posterWrapper}>
              {(item.marqueeImageUri ?? item.backdrop ?? item.poster) ? (
                <Image
                  source={{ uri: item.marqueeImageUri ?? item.backdrop ?? item.poster }}
                  style={[styles.poster, { width: posterWidth, height: posterHeight }]}
                  contentFit="cover"
                  cachePolicy="memory-disk"
                  priority="normal"
                  transition={200}
                />
              ) : (
                <View style={[styles.poster, { width: posterWidth, height: posterHeight }, styles.placeholderPoster]}>
                  <Text style={styles.placeholderText}>ðŸŽ¬</Text>
                </View>
              )}

              {item.rating > 0 && (
                <View style={styles.ratingBadgeHorizontal}>
                  <RatingBadge rating={item.rating} size={9} textColor={ratingTextColor} />
                </View>
              )}

              {hasUnwatched && (
                <View style={[styles.unwatchedBadge, styles.unwatchedBadgeHorizontal]}>
                  <Text style={styles.unwatchedText}>{item.unwatchedEpisodes} new</Text>
                </View>
              )}

              {hasProgress && (
                <View style={[styles.progressTrack, { width: posterWidth }]}>
                  <View style={[styles.progressFill, { width: posterWidth * (item.progress / 100), backgroundColor: colors.progressFill }]} />
                </View>
              )}
            </View>

            <View style={styles.horizontalTextBlock}>
              <Text style={[styles.titleHorizontal, { color: titleHorizontalColor }]} numberOfLines={3}>{item.title}</Text>
              <View style={styles.metaHorizontal}>
                <Text style={[styles.year, { color: metaColor }]}>{item.year}</Text>
                {timeLabel && (
                  <Text style={[styles.duration, { color: metaColor }, hasProgress && { color: colors.progressFill, fontWeight: '600' }]}>
                    {timeLabel}
                  </Text>
                )}
              </View>
            </View>
          </View>
        </Pressable>
      </Animated.View>
    );
  }

  return (
    <Animated.View style={[styles.card, compactGrid && styles.cardGrid, { width: cardWidth, transform: [{ scale }] }]}>
      <Pressable
        hitSlop={{ top: 15, bottom: 15, left: 15, right: 15 }}
        onPress={() => onPress(item)}
        onLongPress={onLongPress ? () => onLongPress(item) : undefined}
        delayLongPress={350}
        onPressIn={onPressIn}
        onPressOut={onPressOut}
      >
        <View style={styles.posterWrapper}>
          {(item.marqueeImageUri ?? (variant === 'landscape' ? (item.backdrop || item.poster) : item.poster)) ? (
            <Image
              source={{ uri: item.marqueeImageUri ?? (variant === 'landscape' ? (item.backdrop || item.poster) : item.poster) }}
              style={[styles.poster, { width: cardWidth, height: posterHeight }]}
              contentFit="cover"
              cachePolicy="memory-disk"
              priority="normal"
              transition={200}
            />
          ) : (
            <View style={[styles.poster, { width: cardWidth, height: posterHeight }, styles.placeholderPoster]}>
              <Text style={styles.placeholderText}>🎬</Text>
            </View>
          )}

          {item.rating > 0 && (
            <View style={variant === 'landscape' ? styles.ratingBadgeLandscape : styles.ratingBadgePortrait}>
              <RatingBadge rating={item.rating} size={9} textColor={ratingTextColor} />
            </View>
          )}

          {hasUnwatched && (
            <View style={[styles.unwatchedBadge, variant === 'landscape' ? styles.unwatchedBadgeLandscape : styles.unwatchedBadgePortrait]}>
              <Text style={styles.unwatchedText}>{item.unwatchedEpisodes} new</Text>
            </View>
          )}

          {hasProgress && (
            <View style={[styles.progressTrack, { width: cardWidth }]}>
              <View style={[styles.progressFill, { width: cardWidth * (item.progress / 100), backgroundColor: colors.progressFill }]} />
            </View>
          )}
        </View>

        <Text style={[styles.title, { width: cardWidth, color: titleColor }]} numberOfLines={2}>{item.title}</Text>

        <View style={styles.meta}>
          <Text style={[styles.year, { color: metaColor }]}>{item.year}</Text>
          {timeLabel && (
            <Text style={[styles.duration, { color: metaColor }, hasProgress && { color: colors.progressFill, fontWeight: '600' }]}>
              {timeLabel}
            </Text>
          )}
        </View>
      </Pressable>
    </Animated.View>
  );
});

const styles = StyleSheet.create({
  card:            { marginRight: 12, borderRadius: 10 },
  cardGrid:        { marginRight: 0 },
  horizontalCard:   { overflow: 'hidden' },
  horizontalRow:    { flexDirection: 'row', alignItems: 'stretch' },
  posterWrapper:   { borderRadius: 10, overflow: 'hidden', position: 'relative' },
  poster:          { borderRadius: 10, backgroundColor: '#1e1e2e' },
  placeholderPoster: { justifyContent: 'center', alignItems: 'center' },
  placeholderText: { fontSize: 40 },
  ratingBadgePortrait:  { position: 'absolute', bottom: 18, right: 6 },
  ratingBadgeLandscape: { position: 'absolute', bottom: 6,  right: 6 },
  ratingBadgeHorizontal:{ position: 'absolute', top: 6,    right: 6 },
  unwatchedBadge: {
    borderRadius: 6, paddingHorizontal: 6, paddingVertical: 3,
    backgroundColor: '#e040fb',
  },
  unwatchedBadgePortrait: { position: 'absolute', top: 6, left: 6 },
  unwatchedBadgeLandscape: { position: 'absolute', top: 6, left: 6 },
  unwatchedBadgeHorizontal: { position: 'absolute', top: 6, left: 6 },
  unwatchedText:   { color: '#fff', fontSize: 10, fontWeight: '800' },
  progressTrack: {
    position: 'absolute', bottom: 0, left: 0,
    height: 4, backgroundColor: 'rgba(255,255,255,0.3)',
  },
  progressFill:    { height: 4, backgroundColor: '#00e676' },
  title:           { fontSize: 12, fontWeight: '600', marginTop: 7, lineHeight: 16 },
  titleHorizontal: { fontSize: 15, fontWeight: '800', lineHeight: 20, marginTop: 0 },
  horizontalTextBlock: { flex: 1, paddingHorizontal: 12, paddingVertical: 12, justifyContent: 'space-between' },
  metaHorizontal:   { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 8 },
  meta:            { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 2 },
  year:            { fontSize: 11 },
  duration:        { fontSize: 10 },
  durationRemaining: { color: '#00e676', fontWeight: '600' },
});
