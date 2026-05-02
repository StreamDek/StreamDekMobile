import React, { useEffect, useRef } from 'react';
import { Animated, Easing, Pressable, StyleSheet } from 'react-native';

interface Props {
  value: boolean;
  onValueChange: (value: boolean) => void;
  disabled?: boolean;
  onColor?: string;
  offColor?: string;
}

const TRACK_W = 51;
const TRACK_H = 31;
const THUMB_SIZE = 27;
const THUMB_OFFSET = 2;
const TRAVEL = TRACK_W - THUMB_SIZE - THUMB_OFFSET * 2; // 20

export const AppleToggle: React.FC<Props> = ({
  value,
  onValueChange,
  disabled = false,
  onColor = '#1877F2',
  offColor = 'rgba(120,120,128,0.24)',
}) => {
  const translateX = useRef(new Animated.Value(value ? TRAVEL : 0)).current;
  const trackOpacity = useRef(new Animated.Value(value ? 1 : 0)).current;

  useEffect(() => {
    const easing = Easing.bezier(0.25, 0.1, 0.25, 1);
    Animated.parallel([
      Animated.timing(translateX, {
        toValue: value ? TRAVEL : 0,
        duration: 180,
        easing,
        useNativeDriver: true,
      }),
      Animated.timing(trackOpacity, {
        toValue: value ? 1 : 0,
        duration: 180,
        easing,
        useNativeDriver: false,
      }),
    ]).start();
  }, [value, translateX, trackOpacity]);

  const trackBg = trackOpacity.interpolate({
    inputRange: [0, 1],
    outputRange: [offColor, onColor],
  });

  return (
    <Pressable
      onPress={() => !disabled && onValueChange(!value)}
      style={[styles.track, disabled && styles.disabled]}
      hitSlop={8}
      accessibilityRole="switch"
      accessibilityState={{ checked: value, disabled }}
    >
      <Animated.View style={[StyleSheet.absoluteFill, styles.trackFill, { backgroundColor: trackBg }]} />
      <Animated.View
        style={[
          styles.thumb,
          { transform: [{ translateX }] },
        ]}
      />
    </Pressable>
  );
};

const styles = StyleSheet.create({
  track: {
    width: TRACK_W,
    height: TRACK_H,
    borderRadius: TRACK_H / 2,
    overflow: 'hidden',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.02)',
  },
  trackFill: {
    borderRadius: TRACK_H / 2,
  },
  thumb: {
    position: 'absolute',
    left: THUMB_OFFSET,
    width: THUMB_SIZE,
    height: THUMB_SIZE,
    borderRadius: THUMB_SIZE / 2,
    backgroundColor: '#fff',
    shadowColor: '#000',
    shadowOpacity: 0.25,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 3 },
    elevation: 4,
  },
  disabled: {
    opacity: 0.45,
  },
});
