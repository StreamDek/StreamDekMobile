import React, { useEffect, useMemo, useRef } from 'react';
import { Animated, Easing, StyleProp, StyleSheet, TouchableOpacity, View, ViewStyle } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../context/ThemeContext';

interface SkeletonBlockProps {
  style?: StyleProp<ViewStyle>;
}

interface SkeletonMediaCardProps {
  width?: number;
  height?: number;
  compactGrid?: boolean;
  variant?: 'portrait' | 'landscape';
  layout?: 'stacked' | 'horizontal';
}

interface FadeInViewProps {
  children: React.ReactNode;
  duration?: number;
  delay?: number;
  style?: StyleProp<ViewStyle>;
}

const AnimatedGradient = Animated.createAnimatedComponent(LinearGradient);

function parseColor(color: string) {
  const rgba = color.match(/rgba?\(([^)]+)\)/i);
  if (rgba) {
    const [r, g, b] = rgba[1].split(',').map(part => Number(part.trim()));
    if ([r, g, b].every(Number.isFinite)) {
      return { r, g, b };
    }
  }

  const cleaned = color.replace('#', '');
  const normalized = cleaned.length === 3
    ? cleaned.split('').map(char => char + char).join('')
    : cleaned;

  const int = parseInt(normalized, 16);
  if (!Number.isFinite(int)) {
    return { r: 255, g: 255, b: 255 };
  }
  return {
    r: (int >> 16) & 255,
    g: (int >> 8) & 255,
    b: int & 255,
  };
}

function withAlpha(color: string, alpha: number) {
  const { r, g, b } = parseColor(color);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

export function SkeletonBlock({ style }: SkeletonBlockProps) {
  const shimmer = useRef(new Animated.Value(0)).current;
  const { theme: { colors } } = useTheme();

  useEffect(() => {
    const loop = Animated.loop(
      Animated.timing(shimmer, {
        toValue: 1,
        duration: 1400,
        easing: Easing.inOut(Easing.ease),
        useNativeDriver: true,
      })
    );
    loop.start();
    return () => loop.stop();
  }, [shimmer]);

  const translateX = shimmer.interpolate({
    inputRange: [0, 1],
    outputRange: [-180, 180],
  });

  const palette = useMemo(() => ({
    base: withAlpha(colors.inputBg, 0.82),
    edge: withAlpha(colors.cardBgElevated ?? colors.cardBg, 0.12),
    glow: withAlpha(colors.accentSoft, 0.20),
    border: withAlpha(colors.border, 0.55),
  }), [colors]);

  return (
    <View style={[styles.block, { backgroundColor: palette.base, borderColor: palette.border }, style]}>
      <AnimatedGradient
        pointerEvents="none"
        colors={[palette.edge, palette.glow, palette.edge]}
        start={{ x: 0, y: 0.5 }}
        end={{ x: 1, y: 0.5 }}
        style={[styles.shimmer, { transform: [{ translateX }] }]}
      />
    </View>
  );
}

export function SkeletonText({ style }: SkeletonBlockProps) {
  return <SkeletonBlock style={[styles.textLine, style]} />;
}

export function SkeletonMediaCard({ width, height, compactGrid = false, variant = 'portrait', layout = 'stacked' }: SkeletonMediaCardProps) {
  const isHorizontal = layout === 'horizontal';
  const cardWidth = width ?? (isHorizontal ? 316 : (variant === 'landscape' ? 224 : 130));
  const cardHeight = height ?? (isHorizontal ? 72 : (variant === 'landscape' ? Math.round(cardWidth * 9 / 16) : (compactGrid ? Math.round(cardWidth * 1.5) : 195)));
  return (
    <View style={[styles.mediaCard, compactGrid && styles.mediaCardGrid, { width: cardWidth }]}>
      {isHorizontal ? (
        <View style={{ flexDirection: 'row' }}>
          <SkeletonBlock style={{ width: 128, height: cardHeight, borderRadius: 10 }} />
          <View style={{ flex: 1, paddingHorizontal: 12, paddingVertical: 12, justifyContent: 'space-between' }}>
            <View style={{ gap: 8 }}>
              <SkeletonText style={{ width: '85%', height: 14 }} />
              <SkeletonText style={{ width: '65%', height: 12 }} />
            </View>
            <View style={styles.metaRow}>
              <SkeletonText style={{ width: 34, height: 10 }} />
              <SkeletonText style={{ width: 52, height: 10 }} />
            </View>
          </View>
        </View>
      ) : (
        <>
          <SkeletonBlock style={{ width: cardWidth, height: cardHeight, borderRadius: 10 }} />
          <SkeletonText style={{ width: cardWidth * 0.88, marginTop: 9 }} />
          <View style={styles.metaRow}>
            <SkeletonText style={{ width: cardWidth * 0.22, height: 10 }} />
            <SkeletonText style={{ width: cardWidth * 0.3, height: 10 }} />
          </View>
        </>
      )}
    </View>
  );
}

export function FadeInView({ children, duration = 320, delay = 0, style }: FadeInViewProps) {
  const opacity = useRef(new Animated.Value(0)).current;
  const translateY = useRef(new Animated.Value(8)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(opacity, {
        toValue: 1,
        duration,
        delay,
        easing: Easing.out(Easing.quad),
        useNativeDriver: true,
      }),
      Animated.timing(translateY, {
        toValue: 0,
        duration,
        delay,
        easing: Easing.out(Easing.quad),
        useNativeDriver: true,
      }),
    ]).start();
  }, [delay, duration, opacity, translateY]);

  return (
    <Animated.View style={[style, { opacity, transform: [{ translateY }] }]}>
      {children}
    </Animated.View>
  );
}

interface MediaDetailSkeletonProps {
  onBack?: () => void;
  insetTop?: number;
  centered?: boolean;
  glass?: boolean;
}

export function MediaDetailSkeleton({ onBack, insetTop = 0, centered = false, glass = false }: MediaDetailSkeletonProps) {
  const { theme: { colors } } = useTheme();

  if (glass) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <View style={StyleSheet.absoluteFillObject}>
          <SkeletonBlock style={{ width: '100%', height: 360, borderRadius: 0, borderWidth: 0 }} />
          <LinearGradient
            colors={[withAlpha(colors.bg, 0.02), withAlpha(colors.bg, 0.48), colors.bg]}
            locations={[0, 0.58, 1]}
            style={StyleSheet.absoluteFillObject}
            pointerEvents="none"
          />
        </View>
        {onBack && (
          <TouchableOpacity
            onPress={onBack}
            style={{
              position: 'absolute', top: insetTop + 14, left: 16, zIndex: 2,
              width: 44, height: 44, borderRadius: 22,
              backgroundColor: 'rgba(8,10,14,0.28)',
              borderWidth: 1, borderColor: 'rgba(255,255,255,0.16)',
              justifyContent: 'center', alignItems: 'center',
            }}
            activeOpacity={0.8}
          >
            <Ionicons name="chevron-back" size={22} color="#fff" />
          </TouchableOpacity>
        )}
        <View style={{ paddingTop: insetTop + 72, paddingHorizontal: 20 }}>
          <SkeletonBlock style={{ width: '100%', aspectRatio: 16 / 10, borderRadius: 26, borderColor: 'rgba(255,255,255,0.18)' }} />
          <View style={{ paddingTop: 18, gap: 12 }}>
            <SkeletonBlock style={{ width: '72%', height: 22, borderRadius: 8 }} />
            <SkeletonBlock style={{ width: 74, height: 14, borderRadius: 999 }} />
            <SkeletonBlock style={{ width: '92%', height: 13, borderRadius: 999 }} />
            <SkeletonBlock style={{ width: '68%', height: 13, borderRadius: 999 }} />
            <SkeletonBlock style={{ width: '100%', height: 50, borderRadius: 25, marginTop: 8 }} />
          </View>
          <View style={{ marginTop: 30, gap: 10 }}>
            <SkeletonBlock style={{ width: 136, height: 18, borderRadius: 8 }} />
            {[0, 1, 2].map(index => (
              <SkeletonBlock
                key={`glass-stream-${index}`}
                style={{ width: '100%', height: 72, borderRadius: 16, borderColor: 'rgba(255,255,255,0.14)' }}
              />
            ))}
          </View>
        </View>
      </View>
    );
  }

  if (centered) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        {/* Full-width backdrop */}
        <View style={{ height: 420, position: 'relative' }}>
          <SkeletonBlock style={{ width: '100%', height: 420, borderRadius: 0, borderWidth: 0 }} />
          <LinearGradient
            colors={['transparent', colors.bg]}
            locations={[0.35, 1]}
            style={StyleSheet.absoluteFillObject}
            pointerEvents="none"
          />
          {onBack && (
            <TouchableOpacity
              onPress={onBack}
              style={{
                position: 'absolute', top: insetTop + 12, left: 16,
                width: 36, height: 36, borderRadius: 18,
                backgroundColor: 'rgba(0,0,0,0.45)',
                justifyContent: 'center', alignItems: 'center',
              }}
              activeOpacity={0.8}
            >
              <Ionicons name="chevron-back" size={22} color="#fff" />
            </TouchableOpacity>
          )}
          {/* Centered content overlaid on backdrop */}
          <View style={{ position: 'absolute', bottom: 0, left: 0, right: 0, alignItems: 'center', paddingHorizontal: 32, paddingBottom: 24, gap: 14 }}>
            {/* Title */}
            <SkeletonBlock style={{ height: 20, borderRadius: 6, width: '70%' }} />
            {/* Pills row */}
            <View style={{ flexDirection: 'row', gap: 8, justifyContent: 'center' }}>
              <SkeletonBlock style={{ height: 26, width: 56, borderRadius: 20 }} />
              <SkeletonBlock style={{ height: 26, width: 44, borderRadius: 20 }} />
              <SkeletonBlock style={{ height: 26, width: 50, borderRadius: 20 }} />
            </View>
            {/* Full-width play button */}
            <SkeletonBlock style={{ height: 46, borderRadius: 24, width: '100%' }} />
            {/* Icon row */}
            <View style={{ flexDirection: 'row', gap: 10, justifyContent: 'center' }}>
              <SkeletonBlock style={{ height: 38, width: 70, borderRadius: 20 }} />
              <SkeletonBlock style={{ height: 38, width: 44, borderRadius: 20 }} />
              <SkeletonBlock style={{ height: 38, width: 44, borderRadius: 20 }} />
            </View>
          </View>
        </View>

        {/* Tabs */}
        <View style={{ flexDirection: 'row', paddingHorizontal: 20, gap: 8, marginBottom: 20, marginTop: 8 }}>
          <SkeletonBlock style={{ height: 34, width: 64, borderRadius: 20 }} />
          <SkeletonBlock style={{ height: 34, width: 64, borderRadius: 20 }} />
          <SkeletonBlock style={{ height: 34, width: 64, borderRadius: 20 }} />
        </View>

        {/* Content lines */}
        <View style={{ paddingHorizontal: 20, gap: 10 }}>
          <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '100%' }} />
          <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '94%' }} />
          <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '88%' }} />
          <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '72%' }} />
          <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '90%', marginTop: 8 }} />
          <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '60%' }} />
        </View>
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      {/* Backdrop */}
      <View style={{ height: 260, position: 'relative' }}>
        <SkeletonBlock style={{ width: '100%', height: 260, borderRadius: 0, borderWidth: 0 }} />
        <LinearGradient
          colors={['transparent', colors.bg]}
          locations={[0.2, 1]}
          style={StyleSheet.absoluteFillObject}
          pointerEvents="none"
        />
        {onBack && (
          <TouchableOpacity
            onPress={onBack}
            style={{
              position: 'absolute', top: insetTop + 12, left: 16,
              width: 36, height: 36, borderRadius: 18,
              backgroundColor: 'rgba(0,0,0,0.45)',
              justifyContent: 'center', alignItems: 'center',
            }}
            activeOpacity={0.8}
          >
            <Ionicons name="chevron-back" size={22} color="#fff" />
          </TouchableOpacity>
        )}
      </View>

      {/* Poster + meta row */}
      <View style={{ flexDirection: 'row', padding: 20, paddingTop: 12, marginTop: -50, gap: 16 }}>
        <SkeletonBlock style={{ width: 100, height: 150, borderRadius: 12 }} />
        <View style={{ flex: 1, paddingTop: 60, gap: 10 }}>
          {/* Title */}
          <SkeletonBlock style={{ height: 22, borderRadius: 6, width: '80%' }} />
          {/* Tagline */}
          <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '60%' }} />
          {/* Pills */}
          <View style={{ flexDirection: 'row', gap: 8, marginTop: 2 }}>
            <SkeletonBlock style={{ height: 24, width: 52, borderRadius: 20 }} />
            <SkeletonBlock style={{ height: 24, width: 44, borderRadius: 20 }} />
            <SkeletonBlock style={{ height: 24, width: 44, borderRadius: 20 }} />
          </View>
        </View>
      </View>

      {/* Action buttons */}
      <View style={{ flexDirection: 'row', paddingHorizontal: 20, gap: 10, marginBottom: 24 }}>
        <SkeletonBlock style={{ flex: 1, height: 52, borderRadius: 14 }} />
        <SkeletonBlock style={{ width: 52, height: 52, borderRadius: 14 }} />
        <SkeletonBlock style={{ width: 52, height: 52, borderRadius: 14 }} />
      </View>

      {/* Tabs */}
      <View style={{ flexDirection: 'row', paddingHorizontal: 20, gap: 8, marginBottom: 20 }}>
        <SkeletonBlock style={{ height: 34, width: 64, borderRadius: 20 }} />
        <SkeletonBlock style={{ height: 34, width: 64, borderRadius: 20 }} />
        <SkeletonBlock style={{ height: 34, width: 64, borderRadius: 20 }} />
      </View>

      {/* Content lines */}
      <View style={{ paddingHorizontal: 20, gap: 10 }}>
        <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '100%' }} />
        <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '94%' }} />
        <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '88%' }} />
        <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '72%' }} />
        <View style={{ marginTop: 8 }}>
          <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '100%' }} />
        </View>
        <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '90%' }} />
        <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '60%' }} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  block: {
    overflow: 'hidden',
    borderRadius: 12,
    borderWidth: 1,
  },
  shimmer: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: 150,
  },
  textLine: {
    height: 12,
    borderRadius: 999,
  },
  mediaCard: {
    borderRadius: 10,
    marginRight: 12,
  },
  mediaCardGrid: {
    marginRight: 0,
  },
  metaRow: {
    marginTop: 6,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
});
