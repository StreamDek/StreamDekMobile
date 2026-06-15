import React from 'react';
import { StyleProp, StyleSheet, Text, TouchableOpacity, View, ViewStyle } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AddonStream } from '../context/AddonContext';
import { ThemeColors } from '../context/ThemeContext';
import { useDisplaySettings } from '../context/DisplaySettingsContext';
import { useFusionBadges } from '../context/FusionBadgeContext';
import { FusionBadgeRow } from './FusionBadgeRow';
import { getRawStreamText } from '../utils/rawStreamText';

interface StreamSourceRowProps {
  stream: AddonStream;
  colors: ThemeColors;
  onPress: () => void;
  active?: boolean;
  style?: StyleProp<ViewStyle>;
  sourceLabel?: string;
}

// Memoized: stream rows render in long lists and parseStream runs several
// regexes — neither should repeat when unrelated state changes upstream.
export const StreamSourceRow = React.memo(function StreamSourceRow({ stream, colors, onPress, active = false, style, sourceLabel }: StreamSourceRowProps) {
  const rawText = React.useMemo(() => getRawStreamText(stream), [stream]);
  const isCached = stream.cachedBy.length > 0;
  const isLightAppearance = colors.bg === '#f4f6fb';
  const { vividAmbientEnabled } = useDisplaySettings();
  const { badgePosition } = useFusionBadges();

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.75}
      style={[
        styles.row,
        {
          backgroundColor: isLightAppearance ? colors.inputBg : (vividAmbientEnabled ? colors.inputBg + '99' : colors.inputBg),
          borderColor: active ? colors.accent : (isCached ? colors.toggleOn + '33' : colors.border),
        },
        style,
      ]}
    >
      <View style={styles.meta}>
        {!!sourceLabel && (
          <Text style={[styles.sourceLabel, { color: colors.mutedText }]} numberOfLines={1}>
            {sourceLabel}
          </Text>
        )}

        {!!rawText.headline && (
          <Text style={[styles.providerLine, { color: colors.textPrimary }]}>{rawText.headline}</Text>
        )}

        {badgePosition === 'top' && (
          <FusionBadgeRow stream={stream} style={{ marginBottom: rawText.lines.length > 0 ? 6 : 2 }} />
        )}

        {rawText.lines.length > 0 && (
          <Text style={[styles.fileTitle, { color: colors.textSecondary }]}>
            {rawText.lines.join('\n')}
          </Text>
        )}

        {badgePosition === 'bottom' && (
          <FusionBadgeRow stream={stream} style={{ marginTop: rawText.lines.length > 0 ? 6 : 2 }} />
        )}
      </View>

      <Ionicons
        name={active ? 'checkmark-circle' : 'play-circle-outline'}
        size={22}
        color={active ? colors.accent : (isCached ? colors.toggleOn : colors.accentSoft)}
      />
    </TouchableOpacity>
  );
});

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 12,
    width: '100%',
    alignSelf: 'stretch',
    borderRadius: 12,
    marginBottom: 8,
    borderWidth: 1,
    overflow: 'hidden',
  },
  meta: {
    flex: 1,
    minWidth: 0,
  },
  sourceLabel: {
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 0.6,
    textTransform: 'uppercase',
    marginBottom: 3,
  },
  providerLine: {
    color: '#e8e8f0',
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 2,
  },
  fileTitle: {
    fontSize: 11,
    lineHeight: 16,
  },
});
