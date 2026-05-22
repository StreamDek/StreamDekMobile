import React, { useEffect, useRef, useState } from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';
import * as SplashScreen from 'expo-splash-screen';
import { useAppReady } from '../context/AppReadyContext';

export function AnimatedSplash() {
  const { isReady } = useAppReady();
  const [hidden, setHidden] = useState(false);
  const opacity = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    SplashScreen.preventAutoHideAsync().catch(() => {});
    setTimeout(() => {
      SplashScreen.hideAsync().catch(() => {});
    }, 150);
  }, []);

  useEffect(() => {
    return undefined;
  }, []);

  useEffect(() => {
    if (isReady) {
      Animated.timing(opacity, {
        toValue: 0,
        duration: 700,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }).start(() => {
        setHidden(true);
      });
    }
  }, [isReady, opacity]);

  if (hidden) return null;

  return (
    <Animated.View style={[StyleSheet.absoluteFill, styles.container, { opacity }]}>
      <View style={styles.backdrop} />
      <View style={styles.centerMark}>
        <View style={styles.centerRing} />
        <View style={styles.centerDot} />
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#0d0d1a',
    zIndex: 9999,
    elevation: 9999,
    justifyContent: 'center',
    alignItems: 'center',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#000000',
  },
  centerMark: {
    width: 92,
    height: 92,
    borderRadius: 46,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: '#000000',
    justifyContent: 'center',
    alignItems: 'center',
  },
  centerRing: {
    position: 'absolute',
    width: 56,
    height: 56,
    borderRadius: 28,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.78)',
  },
  centerDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#ffffff',
  },
});
