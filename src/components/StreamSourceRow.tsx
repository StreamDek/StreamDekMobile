import React from 'react';
import { StyleProp, StyleSheet, Text, TouchableOpacity, View, ViewStyle } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AddonStream } from '../context/AddonContext';
import { ThemeColors } from '../context/ThemeContext';
import { useDisplaySettings } from '../context/DisplaySettingsContext';
import { useFusionBadges } from '../context/FusionBadgeContext';
import { FusionBadgeRow } from './FusionBadgeRow';
import { formatSeeds, parseStream } from '../utils/streamParser';

interface StreamSourceRowProps {
  stream: AddonStream;
  colors: ThemeColors;
  onPress: () => void;
  active?: boolean;
  style?: StyleProp<ViewStyle>;
  sourceLabel?: string;
}

export function StreamSourceRow({ stream, colors, onPress, active = false, style, sourceLabel }: StreamSourceRowProps) {
  const parsed = parseStream(stream);
  const isCached = stream.cachedBy.length > 0;
  const isLightAppearance = colors.bg === '#f4f6fb';
  const { vividAmbientEnabled } = useDisplaySettings();
  const { badgePosition, showSizeBadges } = useFusionBadges();

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

        {badgePosition === 'top' && (
          <FusionBadgeRow stream={stream} style={{ marginBottom: 4 }} />
        )}

        <Text style={[styles.providerLine, { color: colors.textPrimary }]} numberOfLines={1}>
          {parsed.providerLine}
        </Text>

        {!!parsed.fileTitle && (
          <Text style={[styles.fileTitle, { color: colors.textSecondary }]} numberOfLines={2}>
            {parsed.fileTitle}
          </Text>
        )}

        {!!parsed.specLine && (
          <Text style={[styles.specLine, { color: colors.accentSoft }]} numberOfLines={1}>
            {parsed.specLine}
          </Text>
        )}

        <View style={styles.badges}>
          {parsed.size && showSizeBadges && (
            <View style={[styles.badge, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
              <Text style={[styles.badgeText, { color: colors.textSecondary }]}>SIZE {parsed.size}</Text>
            </View>
          )}
          {parsed.seeds != null && (
            <View style={[styles.badge, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
              <Text style={[styles.badgeText, { color: colors.textSecondary }]}>SEEDS {formatSeeds(parsed.seeds)}</Text>
            </View>
          )}
          {isCached && stream.cachedBy.map(provider => (
            <View key={provider} style={[styles.badge, { backgroundColor: colors.toggleOn + '22', borderColor: 'transparent' }]}>
              <Text style={[styles.badgeText, { color: colors.toggleOn }]}>CACHED {provider}</Text>
            </View>
          ))}
          {stream.url && !isCached && (
            <View style={[styles.badge, { backgroundColor: colors.cardBg, borderColor: colors.border }]}>
              <Text style={[styles.badgeText, { color: colors.textSecondary }]}>DIRECT</Text>
            </View>
          )}
        </View>

        {badgePosition === 'bottom' && (
          <FusionBadgeRow stream={stream} style={{ marginTop: 4 }} />
        )}
      </View>

      <Ionicons
        name={active ? 'checkmark-circle' : 'play-circle-outline'}
        size={22}
        color={active ? colors.accent : (isCached ? colors.toggleOn : colors.accentSoft)}
      />
    </TouchableOpacity>
  );
}

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
  },
  meta: {
    flex: 1,
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
    marginBottom: 4,
  },
  specLine: {
    fontSize: 11,
    fontWeight: '600',
    marginBottom: 4,
  },
  badges: {
    flexDirection: 'row',
    gap: 4,
    flexWrap: 'wrap',
  },
  badge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    borderWidth: 1,
  },
  badgeText: {
    fontSize: 9,
    fontWeight: '700',
  },
  cachedBadge: {
    backgroundColor: '#00e67622',
    borderColor: 'transparent',
  },
  cachedText: {
    color: '#00e676',
    fontSize: 9,
    fontWeight: '700',
  },
});
