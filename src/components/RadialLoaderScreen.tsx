import React from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';

export function RadialLoaderScreen() {
  const rotation = React.useRef(new Animated.Value(0)).current;
  const pulse = React.useRef(new Animated.Value(0.55)).current;

  React.useEffect(() => {
    const spinner = Animated.loop(
      Animated.timing(rotation, {
        toValue: 1,
        duration: 1400,
        easing: Easing.linear,
        useNativeDriver: true,
      }),
    );
    const pulseLoop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, {
          toValue: 0.95,
          duration: 900,
          easing: Easing.inOut(Easing.cubic),
          useNativeDriver: true,
        }),
        Animated.timing(pulse, {
          toValue: 0.55,
          duration: 900,
          easing: Easing.inOut(Easing.cubic),
          useNativeDriver: true,
        }),
      ]),
    );

    spinner.start();
    pulseLoop.start();
    return () => {
      spinner.stop();
      pulseLoop.stop();
    };
  }, [pulse, rotation]);

  const spin = rotation.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '360deg'],
  });

  return (
    <View style={[StyleSheet.absoluteFill, styles.container]}>
      <Animated.View style={[styles.glow, { opacity: pulse, transform: [{ scale: pulse }] }]} />
      <Animated.View style={[styles.spinner, { transform: [{ rotate: spin }] }]}>
        <View style={styles.spinnerAccent} />
      </Animated.View>
      <View style={styles.spinnerCore} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#0d0d1a',
    justifyContent: 'center',
    alignItems: 'center',
  },
  glow: {
    position: 'absolute',
    width: 180,
    height: 180,
    borderRadius: 90,
    backgroundColor: 'rgba(76,125,255,0.28)',
  },
  spinner: {
    width: 88,
    height: 88,
    borderRadius: 44,
    borderWidth: 3,
    borderColor: 'rgba(255,255,255,0.14)',
    justifyContent: 'flex-start',
    alignItems: 'center',
  },
  spinnerAccent: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginTop: 6,
    backgroundColor: '#ffffff',
  },
  spinnerCore: {
    position: 'absolute',
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: '#ffffff',
  },
});
