import React from 'react';
import { StyleSheet, View } from 'react-native';
import { useVideoPlayer, VideoView } from 'expo-video';

export function RadialLoaderScreen() {
  const player = useVideoPlayer(require('../../assets/splash-loader.mp4'), p => {
    p.loop = true;
    p.muted = true;
    p.play();
  });

  React.useEffect(() => {
    player.play();
  }, []);

  return (
    <View style={[StyleSheet.absoluteFill, styles.container]}>
      <VideoView
        player={player}
        style={StyleSheet.absoluteFill}
        contentFit="cover"
        nativeControls={false}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#0d0d1a',
  },
});
