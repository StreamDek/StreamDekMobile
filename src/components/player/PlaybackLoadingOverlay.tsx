import React from 'react';
import { ActivityIndicator, Animated, StyleSheet, Text, View } from 'react-native';
import { Image } from 'expo-image';

type Props = {
  visible: boolean;
  artworkUri?: string | null;
  titleLogoUri?: string | null;
  fallbackTitle?: string | null;
  synopsis?: string | null;
  loadingMessage: string;
  logoBreathAnim?: Animated.Value;
  textOpacity?: Animated.Value;
  accentColor: string;
  textColor: string;
  secondaryTextColor?: string;
};

export const PlaybackLoadingOverlay = React.memo(({
  visible,
  artworkUri,
  titleLogoUri,
  fallbackTitle,
  synopsis,
  loadingMessage,
  logoBreathAnim,
  textOpacity,
  accentColor,
  textColor,
  secondaryTextColor,
}: Props) => {
  if (!visible) return null;

  return (
    <View style={styles.container} pointerEvents="auto">
      {artworkUri ? (
        <Image source={{ uri: artworkUri }} style={styles.backdrop} contentFit="cover" cachePolicy="memory-disk" />
      ) : null}
      <View style={styles.scrim} />
      <View style={styles.content}>
        {titleLogoUri ? (
          <Animated.Image
            source={{ uri: titleLogoUri }}
            resizeMode="contain"
            style={[styles.logo, logoBreathAnim ? { opacity: logoBreathAnim } : null]}
          />
        ) : fallbackTitle ? (
          <Animated.Text style={[styles.fallbackTitle, { color: textColor }, logoBreathAnim ? { opacity: logoBreathAnim } : null]}>
            {fallbackTitle}
          </Animated.Text>
        ) : null}
        {synopsis ? (
          <Text style={[styles.synopsis, { color: secondaryTextColor ?? textColor }]} numberOfLines={2}>
            {synopsis}
          </Text>
        ) : null}
        <View style={styles.statusRow}>
          <ActivityIndicator size="small" color={accentColor} />
          <Animated.Text style={[styles.message, { color: textColor }, textOpacity ? { opacity: textOpacity } : null]}>
            {loadingMessage}
          </Animated.Text>
        </View>
      </View>
    </View>
  );
});

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
  },
  scrim: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.72)',
  },
  content: {
    width: '78%',
    alignItems: 'center',
  },
  logo: {
    width: '100%',
    maxWidth: 260,
    height: 84,
    marginBottom: 16,
  },
  fallbackTitle: {
    fontSize: 28,
    fontWeight: '800',
    textAlign: 'center',
    marginBottom: 16,
  },
  synopsis: {
    fontSize: 13,
    lineHeight: 19,
    textAlign: 'center',
    marginBottom: 18,
    opacity: 0.88,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  message: {
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
  },
});
