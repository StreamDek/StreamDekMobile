import React, { useEffect, useMemo, useRef } from 'react';
import { Animated, Easing, ScrollView, StyleProp, StyleSheet, TouchableOpacity, View, ViewStyle } from 'react-native';
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
const CLASSIC_HERO_HEIGHT = 465;
const CENTERED_HERO_HEIGHT = 585;
const CLASSIC_CONTENT_TOP = CLASSIC_HERO_HEIGHT - 40;
const CENTERED_CONTENT_TOP = CENTERED_HERO_HEIGHT - 56;

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

function SkeletonTabRail() {
  return (
    <View style={{ flexDirection: 'row', gap: 8 }}>
      <SkeletonBlock style={{ height: 34, width: 68, borderRadius: 20 }} />
      <SkeletonBlock style={{ height: 34, width: 72, borderRadius: 20 }} />
      <SkeletonBlock style={{ height: 34, width: 76, borderRadius: 20 }} />
    </View>
  );
}

function SkeletonBodyCopy() {
  return (
    <View style={{ gap: 10 }}>
      <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '100%' }} />
      <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '94%' }} />
      <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '88%' }} />
      <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '72%' }} />
      <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '90%', marginTop: 8 }} />
      <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '60%' }} />
    </View>
  );
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
        <ScrollView
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{ flexGrow: 1, paddingBottom: 44 }}
        >
          <View style={{ paddingTop: insetTop + 24, paddingHorizontal: 20, paddingBottom: 18, alignItems: 'center' }}>
            <SkeletonBlock style={{ width: '62%', height: 28, borderRadius: 9, marginBottom: 18 }} />
            <SkeletonBlock style={{ width: '100%', aspectRatio: 16 / 10, borderRadius: 26, borderColor: 'rgba(255,255,255,0.18)' }} />
          </View>

          <View style={{ paddingHorizontal: 14, paddingBottom: 12, alignItems: 'center' }}>
            <SkeletonBlock style={{ width: '58%', height: 78, borderRadius: 16, marginBottom: 10 }} />
            <View style={{ flexDirection: 'row', gap: 8, justifyContent: 'center', width: '100%', marginBottom: 16 }}>
              <SkeletonBlock style={{ height: 24, width: 56, borderRadius: 20 }} />
              <SkeletonBlock style={{ height: 24, width: 44, borderRadius: 20 }} />
              <SkeletonBlock style={{ height: 24, width: 52, borderRadius: 20 }} />
            </View>
            <View style={{ width: '100%', paddingHorizontal: 20, gap: 10, marginBottom: 16 }}>
              <SkeletonBlock style={{ height: 14, borderRadius: 999, width: '100%' }} />
              <SkeletonBlock style={{ height: 14, borderRadius: 999, width: '92%' }} />
              <SkeletonBlock style={{ height: 14, borderRadius: 999, width: '74%' }} />
            </View>
            <View style={{ width: '100%', paddingHorizontal: 18, marginBottom: 8 }}>
              <SkeletonBlock style={{ width: '100%', height: 52, borderRadius: 25 }} />
            </View>
          </View>

          <View style={{ paddingHorizontal: 14 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 8, marginBottom: 16 }}>
              <SkeletonBlock style={{ height: 36, width: 74, borderRadius: 20 }} />
              <SkeletonBlock style={{ height: 36, width: 74, borderRadius: 20 }} />
            </View>
            <View style={{ borderRadius: 24, padding: 14, borderWidth: 1, borderColor: 'rgba(255,255,255,0.14)', backgroundColor: withAlpha(colors.inputBg, 0.36) }}>
              <SkeletonBlock style={{ width: 148, height: 18, borderRadius: 8, marginBottom: 12 }} />
              {[0, 1, 2].map(index => (
                <SkeletonBlock
                  key={`glass-stream-${index}`}
                  style={{ width: '100%', height: 72, borderRadius: 16, borderColor: 'rgba(255,255,255,0.14)', marginBottom: index === 2 ? 0 : 10 }}
                />
              ))}
            </View>
          </View>
        </ScrollView>
      </View>
    );
  }

  if (centered) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <View style={{ position: 'absolute', top: 0, left: 0, right: 0, height: CENTERED_HERO_HEIGHT }}>
          <SkeletonBlock style={{ width: '100%', height: CENTERED_HERO_HEIGHT, borderRadius: 0, borderWidth: 0 }} />
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
        </View>

        <ScrollView
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{ flexGrow: 1, paddingTop: CENTERED_CONTENT_TOP, paddingBottom: 44 }}
        >
          <View style={{ paddingHorizontal: 14 }}>
            <View style={{ alignItems: 'center', marginTop: -150, paddingTop: 8, paddingBottom: 8 }}>
              <SkeletonBlock style={{ width: '72%', height: 80, borderRadius: 18, marginBottom: 8 }} />
              <View style={{ flexDirection: 'row', gap: 8, justifyContent: 'center', marginBottom: 14 }}>
                <SkeletonBlock style={{ height: 24, width: 56, borderRadius: 20 }} />
                <SkeletonBlock style={{ height: 24, width: 44, borderRadius: 20 }} />
                <SkeletonBlock style={{ height: 24, width: 50, borderRadius: 20 }} />
              </View>
              <View style={{ width: '100%', paddingHorizontal: 14, gap: 10, marginBottom: 16 }}>
                <SkeletonBlock style={{ width: '100%', height: 52, borderRadius: 25 }} />
                <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 8 }}>
                  <SkeletonBlock style={{ height: 34, width: 72, borderRadius: 20 }} />
                  <SkeletonBlock style={{ height: 34, width: 72, borderRadius: 20 }} />
                  <SkeletonBlock style={{ height: 34, width: 72, borderRadius: 20 }} />
                </View>
              </View>
              <View style={{ marginBottom: 14 }}>
                <SkeletonTabRail />
              </View>
            </View>

            <SkeletonBodyCopy />
          </View>
        </ScrollView>
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ position: 'absolute', top: 0, left: 0, right: 0, height: CLASSIC_HERO_HEIGHT }}>
        <SkeletonBlock style={{ width: '100%', height: CLASSIC_HERO_HEIGHT, borderRadius: 0, borderWidth: 0 }} />
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
      </View>

      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ flexGrow: 1, paddingTop: CLASSIC_CONTENT_TOP, paddingBottom: 44 }}
      >
        <View style={{ paddingHorizontal: 20 }}>
          <View style={{ flexDirection: 'row', paddingTop: 12, marginTop: -50, gap: 16 }}>
            <SkeletonBlock style={{ width: 100, height: 150, borderRadius: 12 }} />
            <View style={{ flex: 1, paddingTop: 60, gap: 10 }}>
              <SkeletonBlock style={{ height: 58, borderRadius: 14, width: '82%' }} />
              <SkeletonBlock style={{ height: 13, borderRadius: 6, width: '60%' }} />
              <View style={{ flexDirection: 'row', gap: 8, marginTop: 2 }}>
                <SkeletonBlock style={{ height: 24, width: 52, borderRadius: 20 }} />
                <SkeletonBlock style={{ height: 24, width: 44, borderRadius: 20 }} />
                <SkeletonBlock style={{ height: 24, width: 44, borderRadius: 20 }} />
              </View>
            </View>
          </View>

          <View style={{ flexDirection: 'row', gap: 10, marginBottom: 24, marginTop: 18 }}>
            <SkeletonBlock style={{ flex: 1, height: 52, borderRadius: 14 }} />
            <SkeletonBlock style={{ width: 52, height: 52, borderRadius: 14 }} />
            <SkeletonBlock style={{ width: 52, height: 52, borderRadius: 14 }} />
          </View>

          <View style={{ marginBottom: 20 }}>
            <SkeletonTabRail />
          </View>

          <SkeletonBodyCopy />
        </View>
      </ScrollView>
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
