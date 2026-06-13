import React from 'react';
import { StyleProp, View, ViewStyle } from 'react-native';
import Animated, { Easing, FadeIn, FadeInDown } from 'react-native-reanimated';
import { getDeviceProfile } from '../utils/deviceProfile';

const STAGGER_MS = 55;
const DURATION_MS = 360;

interface EntranceFadeProps {
  /** Stagger position — each step delays the entrance slightly. */
  index?: number;
  /** Extra base delay in ms before the stagger. */
  delay?: number;
  /** 'fade-down' slides up while fading (default); 'fade' is opacity only. */
  variant?: 'fade' | 'fade-down';
  /** When false the content renders immediately with no animation. */
  enabled?: boolean;
  style?: StyleProp<ViewStyle>;
  children: React.ReactNode;
}

/**
 * Minimal entrance presentation (fade + ease-out slide) for content that has
 * just finished loading. Skipped on low-performance devices so the animation
 * never costs more than it delights.
 */
export function EntranceFade({
  index = 0,
  delay = 0,
  variant = 'fade-down',
  enabled = true,
  style,
  children,
}: EntranceFadeProps) {
  const reduceMotion = getDeviceProfile().performanceClass === 'low';

  if (!enabled || reduceMotion) {
    return <View style={style}>{children}</View>;
  }

  const base = variant === 'fade' ? FadeIn : FadeInDown;
  const entering = base
    .delay(delay + index * STAGGER_MS)
    .duration(DURATION_MS)
    .easing(Easing.out(Easing.cubic));

  return (
    <Animated.View entering={entering} style={style}>
      {children}
    </Animated.View>
  );
}
