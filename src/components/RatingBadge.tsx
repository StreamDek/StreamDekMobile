import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

interface Props {
  rating: number;
  size?: number;
  style?: object;
  textColor?: string;
  ratingBackgroundColor?: string;
}

export function RatingBadge({
  rating,
  size = 11,
  style,
  textColor = '#111827',
  ratingBackgroundColor = 'rgba(8,10,14,0.42)',
}: Props) {
  const logoHeight = Math.max(14, Math.round(size + 5));
  const logoWidth = Math.round(logoHeight * 2.1);
  const logoTextSize = Math.max(8, Math.round(size - 1));
  const ratingPillHeight = Math.max(14, Math.round(size + 4));

  return (
    <View style={[styles.row, style]}>
      <View style={[styles.logo, { height: logoHeight, width: logoWidth }]}>
        <Text
          style={[styles.logoText, { fontSize: logoTextSize }]}
          numberOfLines={1}
          adjustsFontSizeToFit
          minimumFontScale={0.9}
        >
          IMDb
        </Text>
      </View>
      <View style={[styles.ratingPill, { minHeight: ratingPillHeight, paddingHorizontal: Math.max(5, Math.round(size * 0.42)), backgroundColor: ratingBackgroundColor }]}>
        <Text style={[styles.ratingText, { fontSize: size, color: textColor }]}>{rating.toFixed(1)}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  logo: {
    backgroundColor: '#F5C518',
    borderRadius: 4,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 2,
  },
  logoText: {
    color: '#000000',
    fontWeight: '800',
    letterSpacing: 0,
    includeFontPadding: false,
  },
  ratingPill: {
    borderRadius: 999,
    justifyContent: 'center',
    alignItems: 'center',
  },
  ratingText: {
    fontWeight: '800',
  },
});
