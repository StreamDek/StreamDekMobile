import React, { RefObject } from 'react';
import { Platform, StyleProp, StyleSheet, TouchableOpacity, View, ViewStyle } from 'react-native';
import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../context/ThemeContext';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

interface GlassBackButtonProps {
  onPress: () => void;
  top?: number;
  left?: number;
  size?: number;
  icon?: IoniconName;
  iconSize?: number;
  iconColor?: string;
  blurTarget?: RefObject<View | null>;
  style?: StyleProp<ViewStyle>;
  zIndex?: number;
}

/**
 * Floating circular back button with the same glassy/frosted blur treatment
 * used by the bottom nav and search header (BlurView + tint + gradient glow + highlight).
 */
export function GlassBackButton({
  onPress,
  top,
  left = 16,
  size = 48,
  icon = 'chevron-back',
  iconSize = 24,
  iconColor,
  blurTarget,
  style,
  zIndex = 20,
}: GlassBackButtonProps) {
  const { resolvedAppearance } = useTheme();
  const isLightAppearance = resolvedAppearance === 'light';
  const radius = size / 2;

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.8}
      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
      style={[
        styles.container,
        {
          top,
          left,
          width: size,
          height: size,
          borderRadius: radius,
          zIndex,
          shadowOpacity: isLightAppearance ? 0.12 : 0.24,
        },
        style,
      ]}
    >
      <BlurView
        tint={isLightAppearance ? 'light' : 'dark'}
        intensity={isLightAppearance ? 100 : 118}
        blurMethod={Platform.OS === 'android' && blurTarget ? 'dimezisBlurViewSdk31Plus' : undefined}
        blurTarget={Platform.OS === 'android' ? blurTarget : undefined}
        style={StyleSheet.absoluteFillObject}
      />
      <View
        pointerEvents="none"
        style={[
          styles.fill,
          { backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.06)' : 'rgba(15,23,42,0.08)' },
        ]}
      />
      <LinearGradient
        colors={isLightAppearance
          ? ['rgba(255,255,255,0.075)', 'rgba(214,191,255,0.03)', 'rgba(255,214,236,0.04)']
          : ['rgba(255,255,255,0.024)', 'rgba(168,159,248,0.02)', 'rgba(255,255,255,0.012)']}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={styles.fill}
        pointerEvents="none"
      />
      <View
        pointerEvents="none"
        style={[
          styles.fill,
          {
            borderRadius: radius,
            borderWidth: 1,
            borderColor: isLightAppearance ? 'rgba(255,255,255,0.56)' : 'rgba(255,255,255,0.16)',
            backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.08)' : 'rgba(10,12,18,0.10)',
          },
        ]}
      />
      <View
        pointerEvents="none"
        style={[
          styles.fill,
          {
            borderRadius: radius,
            borderTopWidth: 1,
            borderTopColor: isLightAppearance ? 'rgba(255,255,255,0.30)' : 'rgba(255,255,255,0.08)',
          },
        ]}
      />
      <Ionicons name={icon} size={iconSize} color={iconColor ?? (isLightAppearance ? '#111827' : '#fff')} />
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    overflow: 'hidden',
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'transparent',
    shadowColor: '#000',
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
    elevation: 10,
  },
  fill: {
    ...StyleSheet.absoluteFillObject,
  },
});
