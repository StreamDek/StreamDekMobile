import React from 'react';
import {
  Animated,
  Platform,
  StyleProp,
  StyleSheet,
  Text,
  TextStyle,
  TouchableOpacity,
  View,
  ViewStyle,
} from 'react-native';
import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { ThemeColors } from '../context/ThemeContext';

type OpacityValue = Animated.AnimatedInterpolation<number> | Animated.Value | number;

export interface PrimaryActionButtonProps {
  colors: ThemeColors;
  themeId: string;
  isLightAppearance: boolean;
  onPress: () => void;
  disabled?: boolean;
  activeOpacity?: number;
  style?: StyleProp<ViewStyle>;
  contentStyle?: StyleProp<ViewStyle>;
  labelStyle?: StyleProp<TextStyle>;
  metaStyle?: StyleProp<TextStyle>;
  label?: string;
  metaLabel?: string | null;
  progressPct?: number | null;
  labelOpacity?: OpacityValue;
  metaOpacity?: OpacityValue;
  fullWidth?: boolean;
  surface?: 'solid' | 'glass';
  blurTarget?: React.RefObject<View | null>;
  leading?: React.ReactNode;
  trailing?: React.ReactNode;
}

export function getPrimaryActionPalette(colors: ThemeColors, themeId: string, isLightAppearance: boolean, surface: 'solid' | 'glass' = 'solid') {
  const isMonochrome = themeId === 'monochrome';
  if (surface === 'glass' && !isLightAppearance) {
    return {
      backgroundColor: 'rgba(255,255,255,0.04)',
      textColor: colors.textPrimary,
      borderWidth: 1,
      borderColor: 'rgba(255,255,255,0.18)',
      shadowColor: '#000',
      shadowOpacity: 0.26,
      elevation: 16,
      trackColor: 'rgba(255,255,255,0.16)',
    };
  }

  const backgroundColor = isLightAppearance ? '#ffffff' : (isMonochrome ? '#ffffff' : colors.accent);
  const textColor = isLightAppearance ? colors.textPrimary : (isMonochrome ? '#111111' : colors.buttonText);
  const borderWidth = isLightAppearance ? 1 : 0;
  const borderColor = isLightAppearance ? 'rgba(17,24,39,0.16)' : 'transparent';
  const shadowColor = isLightAppearance ? '#000' : (isMonochrome ? '#000' : colors.accent);
  const shadowOpacity = isLightAppearance ? 0.14 : 0;
  const elevation = isLightAppearance ? 2 : 0;
  const trackColor = isLightAppearance ? 'rgba(17,24,39,0.08)' : 'rgba(255,255,255,0.16)';

  return {
    backgroundColor,
    textColor,
    borderWidth,
    borderColor,
    shadowColor,
    shadowOpacity,
    elevation,
    trackColor,
  };
}

export const PrimaryActionButton = React.memo(function PrimaryActionButton({
  colors,
  themeId,
  isLightAppearance,
  onPress,
  disabled = false,
  activeOpacity = 0.85,
  style,
  contentStyle,
  labelStyle,
  metaStyle,
  label,
  metaLabel,
  progressPct,
  labelOpacity = 1,
  metaOpacity = 1,
  fullWidth = false,
  surface = 'solid',
  blurTarget,
  leading,
  trailing,
}: PrimaryActionButtonProps) {
  const {
    backgroundColor,
    textColor,
    borderWidth,
    borderColor,
    shadowColor,
    shadowOpacity,
    elevation,
    trackColor,
  } = getPrimaryActionPalette(colors, themeId, isLightAppearance, surface);
  const glassSurface = surface === 'glass';
  const glassBlurSurface = glassSurface && !isLightAppearance;

  return (
    <TouchableOpacity
      style={[
        styles.base,
        fullWidth && styles.fullWidth,
        style,
        glassSurface && styles.glassSurface,
        {
          backgroundColor,
          borderWidth,
          borderColor,
          shadowColor,
          shadowOpacity,
          elevation,
        },
      ]}
      activeOpacity={disabled ? 1 : activeOpacity}
      disabled={disabled}
      onPress={onPress}
    >
      {glassBlurSurface ? (
        <>
          <BlurView
            tint="dark"
            intensity={86}
            blurMethod={Platform.OS === 'android' ? 'dimezisBlurViewSdk31Plus' : undefined}
            blurTarget={Platform.OS === 'android' ? blurTarget : undefined}
            style={styles.absoluteFill}
          />
          <View style={[styles.absoluteFill, styles.glassTint]} pointerEvents="none" />
          <LinearGradient
            colors={['rgba(255,255,255,0.08)', 'rgba(168,159,248,0.04)', 'rgba(255,255,255,0.03)']}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={styles.absoluteFill}
            pointerEvents="none"
          />
        </>
      ) : null}
      {typeof progressPct === 'number' && progressPct > 0 ? (
        <View style={[styles.progressTrack, { backgroundColor: trackColor }]}>
          <View style={[styles.progressFill, { backgroundColor: colors.progressFill, width: `${progressPct}%` as any }]} />
        </View>
      ) : null}
      <View style={[styles.content, (leading || trailing) ? styles.contentWithAccessories : null, contentStyle]}>
        {leading ? <View style={styles.accessory}>{leading}</View> : null}
        <Animated.View style={{ opacity: labelOpacity }}>
          <Text style={[styles.label, { color: textColor }, labelStyle]}>
            {label}
          </Text>
        </Animated.View>
        {trailing ? <View style={styles.accessory}>{trailing}</View> : null}
        {metaLabel ? (
          <Animated.View pointerEvents="none" style={[styles.metaOverlay, { opacity: metaOpacity }]}>
            <Text style={[styles.meta, { color: textColor }, metaStyle]} numberOfLines={1}>
              {metaLabel}
            </Text>
          </Animated.View>
        ) : null}
      </View>
    </TouchableOpacity>
  );
});

PrimaryActionButton.displayName = 'PrimaryActionButton';

const styles = StyleSheet.create({
  base: {
    overflow: 'hidden',
    borderWidth: 0,
    borderRadius: 24,
    paddingVertical: 10,
    paddingHorizontal: 14,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 100,
  },
  fullWidth: {
    width: '100%',
  },
  glassSurface: {
    minHeight: 56,
    borderRadius: 28,
    paddingVertical: 0,
    paddingHorizontal: 18,
    shadowRadius: 28,
    shadowOffset: { width: 0, height: 14 },
  },
  absoluteFill: {
    ...StyleSheet.absoluteFillObject,
  },
  glassTint: {
    backgroundColor: 'rgba(255,255,255,0.04)',
  },
  content: {
    minHeight: 22,
    width: '100%',
    justifyContent: 'center',
    alignItems: 'center',
  },
  contentWithAccessories: {
    flexDirection: 'row',
    gap: 8,
  },
  accessory: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    fontSize: 15,
    fontWeight: '800',
    textAlign: 'center',
  },
  metaOverlay: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  meta: {
    fontSize: 11,
    fontWeight: '700',
    lineHeight: 15,
    textAlign: 'center',
  },
  progressTrack: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 4,
  },
  progressFill: {
    height: 4,
  },
});
