import React from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';

export function RadialLoaderScreen() {
  const rotation = React.useRef(new Animated.Value(0)).current;

  React.useEffect(() => {
    const spinner = Animated.loop(
      Animated.timing(rotation, {
        toValue: 1,
        duration: 1400,
        easing: Easing.linear,
        useNativeDriver: true,
      }),
    );

    spinner.start();
    return () => {
      spinner.stop();
    };
  }, [rotation]);

  const spin = rotation.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '360deg'],
  });

  return (
    <View style={[StyleSheet.absoluteFill, styles.container]}>
      <Animated.View style={[styles.spinner, { transform: [{ rotate: spin }] }]}>
        <View style={styles.spinnerAccent} />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#000000',
    justifyContent: 'center',
    alignItems: 'center',
  },
  spinner: {
    width: 88,
    height: 88,
    borderRadius: 44,
    borderWidth: 3,
    borderColor: 'rgba(255,255,255,0.14)',
    justifyContent: 'flex-start',
    alignItems: 'center',
    backgroundColor: '#000000',
  },
  spinnerAccent: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginTop: 6,
    backgroundColor: '#ffffff',
  },
});
