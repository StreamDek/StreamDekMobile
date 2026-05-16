import React, { useEffect, useRef, useState } from 'react';
import { Animated, StyleSheet, Easing } from 'react-native';
import { useVideoPlayer, VideoView } from 'expo-video';
import * as SplashScreen from 'expo-splash-screen';
import { useAppReady } from '../context/AppReadyContext';

export function AnimatedSplash() {
  const { isReady } = useAppReady();
  const [hidden, setHidden] = useState(false);
  const opacity = useRef(new Animated.Value(1)).current;

  const player = useVideoPlayer(require('../../assets/splash-loader.mp4'), p => {
    p.loop  = true;
    p.muted = true;
    p.play();
  });

  useEffect(() => {
    // Belt-and-suspenders: ensure playback starts after mount in case the
    // player initializer fires before the native surface is ready.
    player.play();
  }, []);

  useEffect(() => {
    SplashScreen.preventAutoHideAsync().catch(() => {});
    setTimeout(() => {
      SplashScreen.hideAsync().catch(() => {});
    }, 150);
  }, []);

  useEffect(() => {
    if (isReady) {
      Animated.timing(opacity, {
        toValue: 0,
        duration: 700,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }).start(() => {
        player.pause();
        setHidden(true);
      });
    }
  }, [isReady, opacity]);

  if (hidden) return null;

  return (
    <Animated.View style={[StyleSheet.absoluteFill, styles.container, { opacity }]}>
      <VideoView
        player={player}
        style={StyleSheet.absoluteFill}
        contentFit="cover"
        nativeControls={false}
      />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#0d0d1a',
    zIndex: 9999,
    elevation: 9999,
  },
});
