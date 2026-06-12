import React from 'react';
import { StyleProp, StyleSheet, View, ViewStyle } from 'react-native';
import { Image } from 'expo-image';
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

  const badges = React.useMemo(
    () => (isReady && fusionBadgesEnabled ? getBadgesForStream(stream) : []),
    [fusionBadgesEnabled, getBadgesForStream, isReady, stream],
  );

  if (badges.length === 0) return null;

  return (
    <View style={[styles.row, style]}>
      {badges.map(badge => {
        const isFlag = badge.groupId === LANGUAGE_GROUP_ID;
        return (
          <Image
            key={badge.id}
            source={{ uri: badge.imageURL }}
            contentFit="contain"
            cachePolicy="memory-disk"
            // Badge packs can ship animated GIF/WebP icons; animating dozens of
            // them across stream rows overwhelms the UI thread, so render the
            // first frame only.
            autoplay={false}
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
