import React from 'react';
import { Image, StyleProp, StyleSheet, View, ViewStyle } from 'react-native';
import { AddonStream } from '../context/AddonContext';
import { useFusionBadges } from '../context/FusionBadgeContext';

const LANGUAGE_GROUP_ID = 'gl';

interface FusionBadgeRowProps {
  stream: AddonStream;
  style?: StyleProp<ViewStyle>;
  badgeHeight?: number;
}

export function FusionBadgeRow({ stream, style, badgeHeight = 18 }: FusionBadgeRowProps) {
  const { fusionBadgesEnabled, isReady, getBadgesForStream } = useFusionBadges();

  if (!isReady || !fusionBadgesEnabled) return null;

  const badges = getBadgesForStream(stream);
  if (badges.length === 0) return null;

  return (
    <View style={[styles.row, style]}>
      {badges.map(badge => {
        const isFlag = badge.groupId === LANGUAGE_GROUP_ID;
        return (
          <Image
            key={badge.id}
            source={{ uri: badge.imageURL }}
            resizeMode="contain"
            style={{ height: badgeHeight, width: isFlag ? badgeHeight : badgeHeight * 2.2 }}
          />
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: 5,
  },
});
