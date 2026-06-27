import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator, Modal, Pressable, ScrollView,
  StyleSheet, Text, TouchableOpacity, View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../../context/ThemeContext';
import { AddonStream } from '../../context/AddonContext';
import { useFusionBadges } from '../../context/FusionBadgeContext';
import { FusionBadgeRow } from '../FusionBadgeRow';
import { getRawStreamText } from '../../utils/rawStreamText';
import { getStreamIdentityKey } from '../../utils/streamIdentity';

export interface InPlayerSourcesSheetProps {
  visible: boolean;
  streams: AddonStream[];
  activeStreamIdentity?: string | null;
  loading?: boolean;
  onSelectStream: (stream: AddonStream) => void;
  onReload?: () => void;
  onDismiss: () => void;
}

function streamKey(stream: AddonStream): string {
  return getStreamIdentityKey(stream);
}

function SourceCard({
  stream,
  isActive,
  onPress,
}: {
  stream: AddonStream;
  isActive: boolean;
  onPress: () => void;
}) {
  const rawText = getRawStreamText(stream);
  const { badgePosition } = useFusionBadges();

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.75}
      style={[
        styles.sourceCard,
        isActive && styles.sourceCardActive,
      ]}
    >
      <View style={styles.sourceCardTop}>
        <Text style={styles.addonName} numberOfLines={1}>
          {stream.addonName ?? stream.addonId}
        </Text>
        {isActive && (
          <View style={styles.playingBadge}>
            <Ionicons name="play" size={9} color="#111" />
            <Text style={styles.playingBadgeText}>Playing</Text>
          </View>
        )}
      </View>

      {!!rawText.headline && (
        <Text style={styles.filename}>{rawText.headline}</Text>
      )}

      {badgePosition === 'top' && (
        <FusionBadgeRow stream={stream} style={{ marginBottom: rawText.lines.length > 0 ? 6 : 2 }} />
      )}

      {rawText.lines.length > 0 && (
        <Text style={styles.rawBody}>{rawText.lines.join('\n')}</Text>
      )}

      {badgePosition === 'bottom' && (
        <FusionBadgeRow stream={stream} style={{ marginTop: rawText.lines.length > 0 ? 6 : 2 }} />
      )}

      <Text style={styles.addonFooter}>{stream.addonName ?? stream.addonId}</Text>
    </TouchableOpacity>
  );
}

export function InPlayerSourcesSheet({
  visible,
  streams,
  activeStreamIdentity,
  loading = false,
  onSelectStream,
  onReload,
  onDismiss,
}: InPlayerSourcesSheetProps) {
  const { theme } = useTheme();
  const insets = useSafeAreaInsets();
  const [selectedAddon, setSelectedAddon] = useState<string>('all');

  const addonTabs = useMemo(() => {
    const names = Array.from(new Set(streams.map(s => s.addonName ?? s.addonId).filter(Boolean)));
    return names;
  }, [streams]);

  const filtered = useMemo(() => {
    if (selectedAddon === 'all') return streams;
    return streams.filter(s => (s.addonName ?? s.addonId) === selectedAddon);
  }, [streams, selectedAddon]);

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onDismiss}>
      <View style={styles.backdrop}>
        <Pressable style={StyleSheet.absoluteFillObject} onPress={onDismiss} />
        <View style={[styles.card, { paddingBottom: insets.bottom + 8 }]}>
          <View style={styles.header}>
            <Text style={styles.title}>Sources</Text>
            <View style={styles.headerActions}>
              {!!onReload && (
                <TouchableOpacity style={styles.pillBtn} onPress={onReload}>
                  <Ionicons name="reload-outline" size={14} color="rgba(255,255,255,0.85)" />
                  <Text style={styles.pillBtnText}>Reload</Text>
                </TouchableOpacity>
              )}
              <TouchableOpacity style={styles.pillClose} onPress={onDismiss}>
                <Text style={styles.pillCloseText}>Close</Text>
              </TouchableOpacity>
            </View>
          </View>

          {addonTabs.length > 1 && (
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.tabRow}
              style={styles.tabScroll}
            >
              {['all', ...addonTabs].map(tab => {
                const isActive = tab === selectedAddon;
                return (
                  <TouchableOpacity
                    key={tab}
                    style={[styles.tab, isActive && styles.tabActive]}
                    onPress={() => setSelectedAddon(tab)}
                    activeOpacity={0.75}
                  >
                    <Text style={[styles.tabText, isActive && styles.tabTextActive]}>
                      {tab === 'all' ? 'All' : tab}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          )}

          <ScrollView
            style={styles.list}
            contentContainerStyle={{ padding: 16 }}
            showsVerticalScrollIndicator={false}
          >
            {loading && streams.length === 0 && (
              <View style={styles.emptyWrap}>
                <ActivityIndicator color={theme.colors.accent} />
                <Text style={styles.emptyText}>Loading sources...</Text>
              </View>
            )}
            {!loading && filtered.length === 0 && (
              <View style={styles.emptyWrap}>
                <Ionicons name="cloud-offline-outline" size={32} color="rgba(255,255,255,0.3)" />
                <Text style={styles.emptyText}>No sources available.</Text>
              </View>
            )}
            {filtered.map((stream, idx) => {
              const key = streamKey(stream) || String(idx);
              const isActive = !!activeStreamIdentity && streamKey(stream) === activeStreamIdentity;
              return (
                <SourceCard
                  key={key}
                  stream={stream}
                  isActive={isActive}
                  onPress={() => { onSelectStream(stream); onDismiss(); }}
                />
              );
            })}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.72)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  card: {
    width: '100%',
    maxWidth: 520,
    maxHeight: '82%',
    backgroundColor: 'rgba(18,20,28,0.98)',
    borderRadius: 20,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.12)',
    overflow: 'hidden',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 14,
    flexShrink: 0,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(255,255,255,0.1)',
  },
  title: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '800',
  },
  headerActions: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
  },
  pillBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.14)',
  },
  pillBtnText: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 13,
    fontWeight: '700',
  },
  pillClose: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 7,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.16)',
  },
  pillCloseText: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 13,
    fontWeight: '700',
  },
  tabScroll: {
    flexShrink: 0,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(255,255,255,0.08)',
  },
  tabRow: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 8,
    flexDirection: 'row',
  },
  tab: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
    backgroundColor: 'transparent',
  },
  tabActive: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderColor: 'rgba(255,255,255,0.36)',
  },
  tabText: {
    color: 'rgba(255,255,255,0.6)',
    fontSize: 12,
    fontWeight: '600',
  },
  tabTextActive: {
    color: '#fff',
    fontWeight: '700',
  },
  list: {
    flexShrink: 1,
  },
  emptyWrap: {
    alignItems: 'center',
    paddingVertical: 32,
    gap: 12,
  },
  emptyText: {
    color: 'rgba(255,255,255,0.45)',
    fontSize: 14,
  },
  sourceCard: {
    borderRadius: 14,
    padding: 14,
    marginBottom: 10,
    backgroundColor: 'rgba(255,255,255,0.05)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.08)',
    gap: 6,
  },
  sourceCardActive: {
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderColor: 'rgba(255,255,255,0.28)',
  },
  sourceCardTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 2,
  },
  addonName: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
    flex: 1,
  },
  playingBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: '#fff',
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  playingBadgeText: {
    color: '#111',
    fontSize: 10,
    fontWeight: '800',
  },
  filename: {
    color: 'rgba(255,255,255,0.88)',
    fontSize: 12,
    fontWeight: '700',
    lineHeight: 18,
  },
  rawBody: {
    color: 'rgba(255,255,255,0.72)',
    fontSize: 12,
    lineHeight: 18,
  },
  addonFooter: {
    color: 'rgba(255,255,255,0.28)',
    fontSize: 11,
    fontStyle: 'italic',
    marginTop: 2,
  },
});

