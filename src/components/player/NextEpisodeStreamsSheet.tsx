import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, Modal, Animated, Pressable,
  ActivityIndicator,
} from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { API_BASE } from '../../constants/api';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import { useAddons, AddonStream } from '../../context/AddonContext';
import { useLanguage } from '../../context/LanguageContext';
import { useStreamSelectionSettings } from '../../context/StreamSelectionContext';
import { parseStream } from '../../utils/streamParser';
import { sortStreams } from '../../utils/streamSelection';
import { buildAuthHeaders } from '../../utils/authHeaders';
import { getMobileClientIdentityHeaders } from '../../utils/clientIdentity';
import { NextEpisodeTarget } from '../../hooks/useSkipSegments';

const AUTO_PLAY_SECONDS = 5;

function streamKey(stream: AddonStream): string {
  return (
    stream.infoHash?.toLowerCase()
    ?? stream.url
    ?? `${stream.addonId}:${stream.behaviorHints?.filename ?? stream.title ?? stream.name ?? ''}`
  ).trim();
}

export interface NextEpisodeStreamsSheetProps {
  visible: boolean;
  target: NextEpisodeTarget | null;
  preferBingeGroupNextEpisode?: boolean;
  preferredAddonName?: string | null;
  preferredQualityGroup?: string | null;
  onSelectStream: (stream: AddonStream, allStreams: AddonStream[]) => void;
  onDismiss: () => void;
}

export function NextEpisodeStreamsSheet({
  visible,
  target,
  preferBingeGroupNextEpisode = false,
  preferredAddonName,
  preferredQualityGroup,
  onSelectStream,
  onDismiss,
}: NextEpisodeStreamsSheetProps) {
  const { user } = useAuth();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const isLight = resolvedAppearance === 'light';
  const { t } = useLanguage();
  const { addons, ultraEntitled, ultraBoostEnabled } = useAddons();
  const { preferredQuality } = useStreamSelectionSettings();
  const insets = useSafeAreaInsets();

  const [open, setOpen] = useState(false);
  const [streams, setStreams] = useState<AddonStream[]>([]);
  const [loading, setLoading] = useState(false);
  const [pendingAddons, setPendingAddons] = useState(0);
  const [countdown, setCountdown] = useState<number | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const countdownStartedRef = useRef(false);
  const backdropAnim = useRef(new Animated.Value(0)).current;
  const panelAnim = useRef(new Animated.Value(400)).current;

  const streamsRef = useRef<AddonStream[]>([]);
  const onSelectStreamRef = useRef(onSelectStream);
  const onDismissRef = useRef(onDismiss);
  const preferBingeRef = useRef(preferBingeGroupNextEpisode);
  const preferredAddonRef = useRef(preferredAddonName);
  const preferredQualityGroupRef = useRef(preferredQualityGroup);
  const preferredQualityRef = useRef(preferredQuality);

  useEffect(() => { streamsRef.current = streams; }, [streams]);
  useEffect(() => { onSelectStreamRef.current = onSelectStream; }, [onSelectStream]);
  useEffect(() => { onDismissRef.current = onDismiss; }, [onDismiss]);
  useEffect(() => { preferBingeRef.current = preferBingeGroupNextEpisode; }, [preferBingeGroupNextEpisode]);
  useEffect(() => { preferredAddonRef.current = preferredAddonName; }, [preferredAddonName]);
  useEffect(() => { preferredQualityGroupRef.current = preferredQualityGroup; }, [preferredQualityGroup]);
  useEffect(() => { preferredQualityRef.current = preferredQuality; }, [preferredQuality]);

  const ultraActive = ultraEntitled && ultraBoostEnabled;

  const pickBest = useCallback((candidates: AddonStream[]): AddonStream | null => {
    if (candidates.length === 0) return null;
    const sorted = sortStreams(candidates, { preferredQuality: preferredQualityRef.current });
    if (!preferBingeRef.current) return sorted[0] ?? null;
    const normAddon = preferredAddonRef.current?.trim().toLowerCase() ?? '';
    const normQuality = preferredQualityGroupRef.current?.trim().toLowerCase() ?? '';
    const binge = candidates.filter((s) => {
      const addonOk = normAddon ? (s.addonName ?? '').trim().toLowerCase() === normAddon : true;
      const qualOk = normQuality
        ? (parseStream(s).quality ?? s.quality ?? '').trim().toLowerCase() === normQuality
        : true;
      return addonOk && qualOk;
    });
    return (binge.length > 0
      ? sortStreams(binge, { preferredQuality: preferredQualityRef.current })[0]
      : sorted[0]) ?? null;
  }, []);

  const buildRequestHeaders = useCallback(async () => ({
    ...(await buildAuthHeaders(user, { includeContentType: false })),
    ...(await getMobileClientIdentityHeaders()),
  }), [user]);

  const fetchStreams = useCallback(async () => {
    if (!target) return;
    const enabled = addons.filter((a) => a.enabled);
    if (enabled.length === 0 && !ultraActive) {
      setLoading(false);
      return;
    }

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    countdownStartedRef.current = false;

    setLoading(true);
    setStreams([]);
    setCountdown(null);
    streamsRef.current = [];

    const videoId = target.imdbId
      ? `${target.imdbId}:${target.season}:${target.episodeNumber}`
      : `${target.showId}:${target.season}:${target.episodeNumber}`;

    const headers = await buildRequestHeaders();
    const ordered = [...enabled].sort((a, b) => a.position - b.position);
    let pending = ordered.length + (ultraActive ? 1 : 0);
    let accumulated: AddonStream[] = [];
    const seen = new Set<string>();

    const merge = (incoming: AddonStream[]) => {
      for (const s of incoming) {
        const key = streamKey(s);
        if (!key || seen.has(key)) continue;
        seen.add(key);
        accumulated.push(s);
      }
    };

    const publish = () => {
      if (controller.signal.aborted) return;
      setStreams([...accumulated]);
      streamsRef.current = [...accumulated];
      setPendingAddons(pending);
      if (accumulated.length > 0 || pending === 0) {
        setLoading(false);
        if (!countdownStartedRef.current && accumulated.length > 0) {
          countdownStartedRef.current = true;
          setCountdown(AUTO_PLAY_SECONDS);
        }
      }
    };

    publish();

    const requests = ordered.map(async (addon) => {
      try {
        const res = await fetch(
          `${API_BASE}/addons/streams/single/${addon.id}/series/${encodeURIComponent(videoId)}`,
          { headers, signal: controller.signal },
        );
        if (!res.ok) return;
        const data = await res.json();
        merge(data.streams ?? []);
      } catch {
        // Ignore abort and network errors.
      } finally {
        pending = Math.max(0, pending - 1);
        publish();
      }
    });

    if (ultraActive) {
      requests.push((async () => {
        try {
          const res = await fetch(
            `${API_BASE}/addons/ultra/streams/series/${encodeURIComponent(videoId)}`,
            { headers, signal: controller.signal },
          );
          if (!res.ok) return;
          const data = await res.json();
          merge(data.streams ?? []);
        } catch {
          // Ignore abort and network errors.
        } finally {
          pending = Math.max(0, pending - 1);
          publish();
        }
      })());
    }

    await Promise.allSettled(requests);
  }, [addons, buildRequestHeaders, target, ultraActive]);

  useEffect(() => {
    if (visible) {
      setOpen(true);
      Animated.parallel([
        Animated.timing(backdropAnim, { toValue: 1, duration: 220, useNativeDriver: true }),
        Animated.spring(panelAnim, { toValue: 0, useNativeDriver: true, damping: 22, stiffness: 240 }),
      ]).start();
      void fetchStreams();
    } else {
      Animated.parallel([
        Animated.timing(backdropAnim, { toValue: 0, duration: 180, useNativeDriver: true }),
        Animated.timing(panelAnim, { toValue: 400, duration: 200, useNativeDriver: true }),
      ]).start(() => {
        setOpen(false);
      });
      abortRef.current?.abort();
      setStreams([]);
      setLoading(false);
      setCountdown(null);
      countdownStartedRef.current = false;
    }
  }, [visible]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (countdown === null || countdown <= 0) return;
    const timer = setTimeout(() => setCountdown((value) => (value !== null && value > 0 ? value - 1 : value)), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  useEffect(() => {
    if (countdown !== 0) return;
    const best = pickBest(streamsRef.current);
    if (best) onSelectStreamRef.current(best, streamsRef.current);
    else onDismissRef.current();
  }, [countdown, pickBest]);

  const handlePick = useCallback((stream: AddonStream) => {
    setCountdown(null);
    onSelectStream(stream, streams);
  }, [onSelectStream, streams]);

  const cancelAutoPlay = useCallback(() => {
    setCountdown(null);
  }, []);

  const epCode = target
    ? `S${String(target.season).padStart(2, '0')}E${String(target.episodeNumber).padStart(2, '0')}`
    : '';
  const epLine = target?.episodeName ? `${epCode} · ${target.episodeName}` : epCode;
  const backdropUri = target?.episodeStill ?? target?.showBackdrop ?? target?.showPoster ?? null;
  const panelBg = isLight ? '#f0f2f8' : '#0e1117';
  const borderColor = isLight ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)';

  const sortedStreams = sortStreams(streams, { preferredQuality });

  return (
    <Modal visible={open} transparent animationType="none" onRequestClose={onDismiss}>
      <Animated.View style={[StyleSheet.absoluteFillObject, { opacity: backdropAnim, backgroundColor: 'rgba(0,0,0,0.75)' }]}>
        <Pressable style={StyleSheet.absoluteFillObject} onPress={onDismiss} />
      </Animated.View>

      <Animated.View
        style={[
          styles.panel,
          { backgroundColor: panelBg, paddingBottom: insets.bottom + 8, transform: [{ translateY: panelAnim }] },
        ]}
      >
        <View style={styles.heroWrap}>
          {backdropUri ? (
            <Image source={{ uri: backdropUri }} style={StyleSheet.absoluteFillObject} contentFit="cover" />
          ) : (
            <View style={[StyleSheet.absoluteFillObject, { backgroundColor: isLight ? '#c8cdd9' : '#1c2030' }]} />
          )}
          <View style={styles.heroScrim} />
          <View style={styles.heroTextWrap}>
            <Text style={styles.heroShowTitle} numberOfLines={1}>{target?.showTitle ?? ''}</Text>
            <Text style={styles.heroEpLine} numberOfLines={2}>{epLine}</Text>
          </View>
        </View>

        {countdown !== null && countdown > 0 && streams.length > 0 && (
          <View style={[styles.countdownRow, { borderBottomColor: borderColor }]}>
            <Text style={[styles.countdownText, { color: isLight ? '#374151' : '#d1d5db' }]}>
              {(t as any)('next_episode_autoplay_in', { seconds: countdown }) ?? `Auto-playing in ${countdown}s`}
            </Text>
            <TouchableOpacity onPress={cancelAutoPlay} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
              <Text style={[styles.countdownCancelText, { color: colors.accentSoft }]}>
                {t('common_cancel') ?? 'Cancel'}
              </Text>
            </TouchableOpacity>
          </View>
        )}

        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
        >
          {loading && streams.length === 0 && (
            <View style={styles.emptyWrap}>
              <ActivityIndicator color={colors.accent} />
              <Text style={[styles.emptyText, { color: isLight ? '#6b7280' : '#9ca3af' }]}>
                {t('streams_loading') ?? 'Finding streams…'}
              </Text>
            </View>
          )}
          {!loading && streams.length === 0 && (
            <View style={styles.emptyWrap}>
              <Ionicons name="cloud-offline-outline" size={32} color={isLight ? '#9ca3af' : '#6b7280'} />
              <Text style={[styles.emptyText, { color: isLight ? '#6b7280' : '#9ca3af' }]}>
                {t('streams_no_streams') ?? 'No streams found.'}
              </Text>
            </View>
          )}
          {sortedStreams.map((stream, idx) => (
            <StreamRow
              key={streamKey(stream) || String(idx)}
              stream={stream}
              colors={colors}
              isLight={isLight}
              onPress={() => handlePick(stream)}
            />
          ))}
          {pendingAddons > 0 && streams.length > 0 && (
            <View style={styles.pendingRow}>
              <ActivityIndicator size="small" color={colors.mutedText} />
              <Text style={[styles.pendingText, { color: isLight ? '#9ca3af' : '#6b7280' }]}>
                {t('streams_loading') ?? 'Loading more…'}
              </Text>
            </View>
          )}
        </ScrollView>

        <TouchableOpacity
          style={[styles.cancelBtn, { borderColor }]}
          onPress={onDismiss}
          activeOpacity={0.75}
        >
          <Text style={[styles.cancelBtnText, { color: isLight ? '#374151' : '#d1d5db' }]}>
            {t('common_cancel') ?? 'Cancel'}
          </Text>
        </TouchableOpacity>
      </Animated.View>
    </Modal>
  );
}

function StreamRow({
  stream,
  colors,
  isLight,
  onPress,
}: {
  stream: AddonStream;
  colors: any;
  isLight: boolean;
  onPress: () => void;
}) {
  const parsed = parseStream(stream);
  const isCached = stream.cachedBy.length > 0;

  const qualityColors: Record<string, { bg: string; text: string }> = {
    '4K': { bg: '#FFD70022', text: '#FFD700' },
    '1080p': { bg: '#00e67622', text: '#00e676' },
    '720p': { bg: '#29b6f622', text: '#29b6f6' },
    '480p': { bg: '#78909c22', text: '#78909c' },
  };
  const qColor = parsed.quality
    ? (qualityColors[parsed.quality] ?? { bg: '#a89ff822', text: '#a89ff8' })
    : { bg: isLight ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)', text: isLight ? '#6b7280' : '#9ca3af' };

  const rowBg = isLight ? 'rgba(255,255,255,0.72)' : 'rgba(255,255,255,0.06)';
  const rowBorder = isCached
    ? (isLight ? 'rgba(0,188,212,0.3)' : 'rgba(0,230,118,0.25)')
    : (isLight ? 'rgba(0,0,0,0.1)' : 'rgba(255,255,255,0.1)');

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.75}
      style={[styles.streamRow, { backgroundColor: rowBg, borderColor: rowBorder }]}
    >
      <View style={[styles.qualityBadge, { backgroundColor: qColor.bg }]}>
        <Text style={[styles.qualityText, { color: qColor.text }]}>{parsed.quality ?? '?'}</Text>
      </View>

      <View style={styles.streamMeta}>
        <Text style={[styles.streamProvider, { color: isLight ? '#111827' : '#f3f4f6' }]} numberOfLines={1}>
          {parsed.providerLine}
        </Text>
        {!!parsed.specLine && (
          <Text style={[styles.streamSpec, { color: colors.accentSoft }]} numberOfLines={1}>
            {parsed.specLine}
          </Text>
        )}
        <View style={styles.tags}>
          {!!parsed.size && (
            <Tag isLight={isLight} label={`💾 ${parsed.size}`} />
          )}
          {parsed.seeds != null && (
            <Tag isLight={isLight} label={`👤 ${parsed.seeds}`} />
          )}
          {isCached && stream.cachedBy.map((provider) => (
            <View key={provider} style={[styles.tag, { backgroundColor: 'rgba(0,230,118,0.15)', borderColor: 'transparent' }]}>
              <Text style={[styles.tagText, { color: '#00e676' }]}>⚡ {provider}</Text>
            </View>
          ))}
          {stream.url && !isCached && (
            <Tag isLight={isLight} label="DIRECT" />
          )}
        </View>
      </View>

      <Ionicons
        name="play-circle-outline"
        size={22}
        color={isCached ? '#00e676' : colors.accentSoft}
      />
    </TouchableOpacity>
  );
}

function Tag({ isLight, label }: { isLight: boolean; label: string }) {
  return (
    <View
      style={[
        styles.tag,
        {
          backgroundColor: isLight ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.08)',
          borderColor: isLight ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.1)',
        },
      ]}
    >
      <Text style={[styles.tagText, { color: isLight ? '#6b7280' : '#9ca3af' }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    maxHeight: '72%',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    overflow: 'hidden',
  },
  heroWrap: {
    width: '100%',
    height: 130,
    overflow: 'hidden',
  },
  heroScrim: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.48)',
  },
  heroTextWrap: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 16,
  },
  heroShowTitle: {
    color: 'rgba(255,255,255,0.65)',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  heroEpLine: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '800',
    letterSpacing: 0.1,
  },
  countdownRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  countdownText: {
    fontSize: 13,
    fontWeight: '600',
  },
  countdownCancelText: {
    fontSize: 12,
    fontWeight: '700',
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    padding: 12,
    paddingBottom: 6,
  },
  emptyWrap: {
    alignItems: 'center',
    paddingVertical: 32,
    gap: 12,
  },
  emptyText: {
    fontSize: 14,
  },
  pendingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 8,
    paddingHorizontal: 4,
  },
  pendingText: {
    fontSize: 12,
  },
  cancelBtn: {
    marginHorizontal: 12,
    marginTop: 6,
    marginBottom: 4,
    paddingVertical: 13,
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
  },
  cancelBtnText: {
    fontSize: 15,
    fontWeight: '700',
  },
  streamRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 12,
    borderRadius: 12,
    marginBottom: 8,
    borderWidth: 1,
    overflow: 'hidden',
  },
  qualityBadge: {
    paddingHorizontal: 7,
    paddingVertical: 5,
    borderRadius: 6,
    minWidth: 46,
    alignItems: 'center',
  },
  qualityText: {
    fontSize: 10,
    fontWeight: '900',
  },
  streamMeta: {
    flex: 1,
  },
  streamProvider: {
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 2,
  },
  streamSpec: {
    fontSize: 11,
    fontWeight: '600',
    marginBottom: 4,
  },
  tags: {
    flexDirection: 'row',
    gap: 4,
    flexWrap: 'wrap',
  },
  tag: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    borderWidth: 1,
  },
  tagText: {
    fontSize: 9,
    fontWeight: '700',
  },
});
