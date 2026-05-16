import React, { memo, useMemo } from 'react';
import { Image as RNImage, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { BlurView } from 'expo-blur';
import { useTheme } from '../context/ThemeContext';
import { RatingBadge } from './RatingBadge';
import type { ContinueWatchingStyle } from '../context/DisplaySettingsContext';
import { getDeviceProfile } from '../utils/deviceProfile';

// ── Card dimensions ───────────────────────────────────────────────────────────
export const CW_CARD_WIDTH: Record<ContinueWatchingStyle, number> = {
  cinematic: 288,
  glass:     288,
  ticket:    316,
  mini:      292,
  stacked:   155,
};

interface Props {
  item: any;
  cardStyle: ContinueWatchingStyle;
  onPress: () => void;
  onLongPress?: () => void;
}

function timeRemaining(item: any): string | null {
  const p = item?.progress ?? 0;
  if (item?.runtime && p > 0 && p < 100) {
    const remaining = Math.max(1, Math.round(item.runtime * (1 - p / 100)));
    return remaining >= 60
      ? `${Math.floor(remaining / 60)}h ${remaining % 60}m left`
      : `${remaining}m left`;
  }
  if (item?.type === 'tv') return 'Series';
  return item?.year ? String(item.year) : null;
}

function ratingColor(rating: number) {
  return rating >= 7 ? '#00e676' : rating >= 5 ? '#ffd740' : '#c97070';
}

// ── 1. Cinematic ────────────────────────���────────────────────────��────────────
// Full 16:9 backdrop with bottom gradient overlay. Title / time pill / rating
// sit inside the gradient. Thin accent progress scrubber at the very bottom.
function CinematicCard({ item, onPress, onLongPress }: Omit<Props, 'cardStyle'>) {
  const { theme } = useTheme();
  const c = theme.colors;
  const W = CW_CARD_WIDTH.cinematic;
  const H = Math.round(W * 9 / 16);
  const timeLabel = useMemo(() => timeRemaining(item), [item]);
  const imgSrc = item.backdrop || item.poster;

  return (
    <TouchableOpacity style={[S.base, { width: W, borderRadius: 14, overflow: 'hidden', backgroundColor: c.cardBg }]} onPress={onPress} onLongPress={onLongPress} delayLongPress={350} activeOpacity={0.88}>
      {imgSrc
        ? <Image source={{ uri: imgSrc }} style={{ width: W, height: H }} contentFit="cover" transition={0} />
        : <View style={{ width: W, height: H, backgroundColor: '#141420', justifyContent: 'center', alignItems: 'center' }}><Text style={{ fontSize: 32 }}>🎬</Text></View>}

      <LinearGradient colors={['transparent', 'rgba(0,0,0,0.55)', 'rgba(0,0,0,0.92)']} locations={[0, 0.42, 1]} style={[StyleSheet.absoluteFillObject, { justifyContent: 'flex-end' }]} pointerEvents="none">
        <View style={S.cinematicMeta}>
          <View style={S.cinematicRow}>
            <Text style={S.cinematicTitle} numberOfLines={1}>{item.title}</Text>
            {timeLabel ? <View style={S.timePill}><Text style={S.timePillText}>{timeLabel}</Text></View> : null}
          </View>
          {item.rating > 0 && <View style={{ marginTop: 4 }}><RatingBadge rating={item.rating} size={9} textColor={ratingColor(item.rating)} /></View>}
        </View>
      </LinearGradient>

      {item.progress > 0 && (
        <View style={[S.progressTrack, { width: W }]}>
          <View style={[S.progressFill, { width: W * (item.progress / 100), backgroundColor: c.progressFill }]} />
        </View>
      )}
    </TouchableOpacity>
  );
}

// ── 2. Glass ───────────────────────────────��─────────────────────────────��────
// Full-bleed backdrop with a BlurView cover over the bottom third. This keeps
// the artwork visible while making the metadata feel like real glass on top.
function GlassCard({ item, onPress, onLongPress }: Omit<Props, 'cardStyle'>) {
  const { theme, resolvedAppearance } = useTheme();
  const c = theme.colors;
  const deviceProfile = useMemo(() => getDeviceProfile(), []);
  const W = CW_CARD_WIDTH.glass;
  const H = Math.round(W * 9 / 16);
  const glassH = Math.round(H / 3);
  const timeLabel = useMemo(() => timeRemaining(item), [item]);
  const isDark = resolvedAppearance === 'dark';
  const imgSrc = item.backdrop || item.poster;

  return (
    <TouchableOpacity style={[S.base, { width: W, height: H, borderRadius: 14, overflow: 'hidden', backgroundColor: c.cardBg }]} onPress={onPress} onLongPress={onLongPress} delayLongPress={350} activeOpacity={0.88}>
      {imgSrc
        ? <Image source={{ uri: imgSrc }} style={StyleSheet.absoluteFillObject} contentFit="cover" transition={0} />
        : <View style={{ width: W, height: H, position: 'absolute', top: 0, backgroundColor: '#141420', justifyContent: 'center', alignItems: 'center' }}><Text style={{ fontSize: 32 }}>🎬</Text></View>}

      <LinearGradient
        colors={['transparent', isDark ? 'rgba(0,0,0,0.24)' : 'rgba(0,0,0,0.08)', isDark ? 'rgba(0,0,0,0.62)' : 'rgba(0,0,0,0.22)']}
        locations={[0, 0.42, 1]}
        style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: glassH + 26 }}
        pointerEvents="none"
      />

      {deviceProfile.enableHeavyBlur ? (
        <BlurView intensity={isDark ? 58 : 46} tint={isDark ? 'dark' : 'light'} style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: glassH, overflow: 'hidden', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: isDark ? 'rgba(255,255,255,0.18)' : 'rgba(255,255,255,0.36)', paddingHorizontal: 12, paddingTop: 9 }}>
          {imgSrc ? (
            <RNImage
              source={{ uri: imgSrc }}
              blurRadius={18}
              resizeMode="cover"
              style={{ position: 'absolute', left: 0, right: 0, top: -(H - glassH), width: W, height: H }}
            />
          ) : null}
          <View style={[StyleSheet.absoluteFillObject, { backgroundColor: isDark ? 'rgba(7,8,12,0.34)' : 'rgba(255,255,255,0.24)' }]} />
          <View style={{ zIndex: 1 }}>
            <View style={S.glassRow}>
              <Text style={[S.glassTitle, { color: isDark ? '#f0f0f8' : '#111' }]} numberOfLines={1}>{item.title}</Text>
              {timeLabel && <Text style={[S.glassTime, { color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(0,0,0,0.55)' }]}>{timeLabel}</Text>}
            </View>
            {item.rating > 0 && <View style={{ marginTop: 3 }}><RatingBadge rating={item.rating} size={9} textColor={ratingColor(item.rating)} /></View>}
          </View>
          {item.progress > 0 && (
            <View style={[S.glassProgressTrack, { width: W }]}>
              <View style={[S.glassProgressFill, { width: W * (item.progress / 100), backgroundColor: c.progressFill }]} />
            </View>
          )}
        </BlurView>
      ) : (
        <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: glassH, overflow: 'hidden', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: isDark ? 'rgba(255,255,255,0.18)' : 'rgba(255,255,255,0.30)', paddingHorizontal: 12, paddingTop: 9, backgroundColor: isDark ? 'rgba(7,8,12,0.68)' : 'rgba(255,255,255,0.70)' }}>
          <View style={{ zIndex: 1 }}>
            <View style={S.glassRow}>
              <Text style={[S.glassTitle, { color: isDark ? '#f0f0f8' : '#111' }]} numberOfLines={1}>{item.title}</Text>
              {timeLabel && <Text style={[S.glassTime, { color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(0,0,0,0.55)' }]}>{timeLabel}</Text>}
            </View>
            {item.rating > 0 && <View style={{ marginTop: 3 }}><RatingBadge rating={item.rating} size={9} textColor={ratingColor(item.rating)} /></View>}
          </View>
          {item.progress > 0 && (
            <View style={[S.glassProgressTrack, { width: W }]}>
              <View style={[S.glassProgressFill, { width: W * (item.progress / 100), backgroundColor: c.progressFill }]} />
            </View>
          )}
        </View>
      )}
    </TouchableOpacity>
  );
}

// ── 3. Ticket ────────────────────────────────��────────────────────────────────
// Wide flat card (~88px tall). Full-bleed backdrop with heavy dark overlay.
// Title on the left, time-remaining pill on the right. Accent progress at bottom.
function TicketCard({ item, onPress, onLongPress }: Omit<Props, 'cardStyle'>) {
  const { theme, resolvedAppearance } = useTheme();
  const c = theme.colors;
  const W = CW_CARD_WIDTH.ticket;
  const H = 88;
  const timeLabel = useMemo(() => timeRemaining(item), [item]);
  const isDark = resolvedAppearance === 'dark';
  const imgSrc = item.backdrop || item.poster;

  return (
    <TouchableOpacity style={[S.base, { width: W, height: H, borderRadius: 14, overflow: 'hidden', backgroundColor: isDark ? '#0d0d14' : c.cardBg }]} onPress={onPress} onLongPress={onLongPress} delayLongPress={350} activeOpacity={0.88}>
      {imgSrc && (
        <>
          <Image source={{ uri: imgSrc }} style={StyleSheet.absoluteFillObject} contentFit="cover" transition={0} />
          <View style={[StyleSheet.absoluteFillObject, { backgroundColor: isDark ? 'rgba(0,0,0,0.72)' : 'rgba(0,0,0,0.52)' }]} />
        </>
      )}
      <View style={S.ticketContent}>
        <View style={S.ticketLeft}>
          <Text style={S.ticketTitle} numberOfLines={2}>{item.title}</Text>
          <View style={S.ticketMetaRow}>
            {item.year ? <Text style={S.ticketMeta}>{item.year}</Text> : null}
            {item.year && item.rating > 0 ? <Text style={S.ticketMetaDot}>·</Text> : null}
            {item.rating > 0 && <RatingBadge rating={item.rating} size={8} textColor={ratingColor(item.rating)} />}
          </View>
        </View>
        {timeLabel && (
          <View style={[S.ticketTimePill, { backgroundColor: isDark ? 'rgba(255,255,255,0.14)' : 'rgba(0,0,0,0.35)' }]}>
            <Text style={S.ticketTimeText}>{timeLabel}</Text>
          </View>
        )}
      </View>
      {item.progress > 0 && (
        <View style={[S.progressTrack, { width: W }]}>
          <View style={[S.progressFill, { width: W * (item.progress / 100), backgroundColor: c.progressFill }]} />
        </View>
      )}
    </TouchableOpacity>
  );
}

// ── 4. Mini ──────────────────────────────���────────────────────────────────────
// Compact horizontal card. Small backdrop thumbnail on the left; title, rating
// and time-remaining in a text block on the right. Progress spans full width.
function MiniCard({ item, onPress, onLongPress }: Omit<Props, 'cardStyle'>) {
  const { theme, resolvedAppearance } = useTheme();
  const c = theme.colors;
  const W = CW_CARD_WIDTH.mini;
  const thumbW = 120;
  const H = 78;
  const timeLabel = useMemo(() => timeRemaining(item), [item]);
  const isDark = resolvedAppearance === 'dark';
  const imgSrc = item.backdrop || item.poster;

  return (
    <TouchableOpacity style={[S.base, { width: W, height: H, borderRadius: 12, overflow: 'hidden', flexDirection: 'row', backgroundColor: isDark ? '#121218' : c.cardBg, borderWidth: StyleSheet.hairlineWidth, borderColor: c.border }]} onPress={onPress} onLongPress={onLongPress} delayLongPress={350} activeOpacity={0.88}>
      {/* Thumbnail */}
      <View style={{ width: thumbW, height: H }}>
        {imgSrc
          ? <Image source={{ uri: imgSrc }} style={{ width: thumbW, height: H }} contentFit="cover" transition={0} />
          : <View style={{ width: thumbW, height: H, backgroundColor: '#1a1a28', justifyContent: 'center', alignItems: 'center' }}><Text style={{ fontSize: 22 }}>🎬</Text></View>}
        {/* Subtle right-side fade */}
        <LinearGradient colors={['transparent', isDark ? 'rgba(18,18,24,0.9)' : 'rgba(240,240,248,0.9)']} start={{ x: 0.5, y: 0 }} end={{ x: 1, y: 0 }} style={{ position: 'absolute', right: 0, top: 0, bottom: 0, width: 28 }} pointerEvents="none" />
      </View>

      {/* Text block */}
      <View style={{ flex: 1, paddingHorizontal: 10, paddingVertical: 10, justifyContent: 'space-between' }}>
        <Text style={[S.miniTitle, { color: isDark ? '#f0f0f8' : c.textPrimary }]} numberOfLines={2}>{item.title}</Text>
        <View>
          <View style={S.miniMetaRow}>
            {item.rating > 0 && <RatingBadge rating={item.rating} size={8} textColor={ratingColor(item.rating)} />}
            {item.year && <Text style={[S.miniYear, { color: isDark ? 'rgba(255,255,255,0.45)' : c.mutedText }]}>{item.year}</Text>}
          </View>
          {timeLabel && <Text style={[S.miniTime, { color: isDark ? 'rgba(255,255,255,0.55)' : c.mutedText }]}>{timeLabel}</Text>}
        </View>
      </View>

      {/* Progress bar full width at very bottom */}
      {item.progress > 0 && (
        <View style={[S.progressTrack, { width: W }]}>
          <View style={[S.progressFill, { width: W * (item.progress / 100), backgroundColor: c.progressFill }]} />
        </View>
      )}
    </TouchableOpacity>
  );
}

// ── 5. Stacked ────────────────────────────────────────────────────────────────
// Portrait-ish card. 16:9 backdrop on top, text block below. Consistent with
// other home screen section cards but enriched with progress.
function StackedCard({ item, onPress, onLongPress }: Omit<Props, 'cardStyle'>) {
  const { theme, resolvedAppearance } = useTheme();
  const c = theme.colors;
  const W = CW_CARD_WIDTH.stacked;
  const imgH = Math.round(W * 9 / 16);
  const timeLabel = useMemo(() => timeRemaining(item), [item]);
  const isDark = resolvedAppearance === 'dark';
  const imgSrc = item.backdrop || item.poster;

  return (
    <TouchableOpacity style={[S.base, { width: W, borderRadius: 12, overflow: 'hidden', backgroundColor: isDark ? '#121218' : c.cardBg, borderWidth: StyleSheet.hairlineWidth, borderColor: c.border }]} onPress={onPress} onLongPress={onLongPress} delayLongPress={350} activeOpacity={0.88}>
      {/* Backdrop */}
      <View style={{ width: W, height: imgH }}>
        {imgSrc
          ? <Image source={{ uri: imgSrc }} style={{ width: W, height: imgH }} contentFit="cover" transition={0} />
          : <View style={{ width: W, height: imgH, backgroundColor: '#1a1a28', justifyContent: 'center', alignItems: 'center' }}><Text style={{ fontSize: 28 }}>🎬</Text></View>}
        {/* Bottom fade */}
        <LinearGradient colors={['transparent', isDark ? 'rgba(18,18,24,0.5)' : 'rgba(240,240,248,0.4)']} style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 20 }} pointerEvents="none" />
        {/* Progress bar at very bottom of image */}
        {item.progress > 0 && (
          <View style={[S.progressTrack, { width: W }]}>
            <View style={[S.progressFill, { width: W * (item.progress / 100), backgroundColor: c.progressFill }]} />
          </View>
        )}
      </View>

      {/* Text block below image */}
      <View style={{ paddingHorizontal: 9, paddingTop: 8, paddingBottom: 10 }}>
        <Text style={[S.stackedTitle, { color: isDark ? '#e8e8f0' : c.textPrimary }]} numberOfLines={2}>{item.title}</Text>
        <View style={S.stackedMetaRow}>
          {item.year ? <Text style={[S.stackedYear, { color: isDark ? 'rgba(255,255,255,0.45)' : c.mutedText }]}>{item.year}</Text> : null}
          {item.year && item.rating > 0 ? <Text style={[S.stackedDot, { color: isDark ? 'rgba(255,255,255,0.3)' : c.mutedText }]}>·</Text> : null}
          {item.rating > 0 && <RatingBadge rating={item.rating} size={8} textColor={ratingColor(item.rating)} />}
        </View>
        {timeLabel && (
          <Text style={[S.stackedTime, { color: isDark ? 'rgba(255,255,255,0.5)' : c.mutedText }]}>{timeLabel}</Text>
        )}
      </View>
    </TouchableOpacity>
  );
}

// ── Public component ──────────────────────────────────────────────────────────
export const ContinueWatchingCard = memo<Props>(({ item, cardStyle, onPress, onLongPress }) => {
  if (cardStyle === 'glass')    return <GlassCard   item={item} onPress={onPress} onLongPress={onLongPress} />;
  if (cardStyle === 'ticket')   return <TicketCard  item={item} onPress={onPress} onLongPress={onLongPress} />;
  if (cardStyle === 'mini')     return <MiniCard    item={item} onPress={onPress} onLongPress={onLongPress} />;
  if (cardStyle === 'stacked')  return <StackedCard item={item} onPress={onPress} onLongPress={onLongPress} />;
  return <CinematicCard item={item} onPress={onPress} onLongPress={onLongPress} />;
});

// ────────────────────────────────────────────��────────────────────────────────
// Skeletal previews — always rendered on a fixed dark surface so they look
// identical in both light and dark app themes.
// ───────────────────────────────────────��─────────────────────────────────────
interface SkeletonProps { selected?: boolean; colors: any; }

function parseSkeletonColor(color: string) {
  const rgba = color.match(/rgba?\(([^)]+)\)/i);
  if (rgba) {
    const [r, g, b] = rgba[1].split(',').map(part => Number(part.trim()));
    if ([r, g, b].every(Number.isFinite)) return { r, g, b };
  }

  const cleaned = color.replace('#', '');
  const normalized = cleaned.length === 3
    ? cleaned.split('').map(char => char + char).join('')
    : cleaned;
  const int = parseInt(normalized, 16);
  if (!Number.isFinite(int)) return { r: 255, g: 255, b: 255 };
  return { r: (int >> 16) & 255, g: (int >> 8) & 255, b: int & 255 };
}

function alpha(color: string, opacity: number) {
  const { r, g, b } = parseSkeletonColor(color);
  return `rgba(${r}, ${g}, ${b}, ${opacity})`;
}

function skeletonPalette(colors: any) {
  return {
    canvas: alpha(colors.cardBgElevated ?? colors.cardBg, 0.96),
    image: alpha(colors.inputBg ?? colors.cardBg, 0.9),
    glass: alpha(colors.cardBgElevated ?? colors.cardBg, 0.86),
    bar: alpha(colors.border, 0.55),
    labelHi: alpha(colors.textPrimary, 0.72),
    labelLo: alpha(colors.mutedText ?? colors.subText, 0.48),
    pill: alpha(colors.accentSoft ?? colors.accent, 0.18),
    border: alpha(colors.border, 0.8),
  };
}

function SLine({ w, hi = false, palette }: { w: number | string; hi?: boolean; palette: ReturnType<typeof skeletonPalette> }) {
  return <View style={{ height: 5, width: w as any, borderRadius: 3, backgroundColor: hi ? palette.labelHi : palette.labelLo, marginTop: 4 }} />;
}

function SKWrap({ selected, colors, w, h, children }: { selected?: boolean; colors: any; w: number; h: number; children: React.ReactNode }) {
  const palette = skeletonPalette(colors);
  return (
    <View style={{
      width: w, height: h, borderRadius: 9, overflow: 'hidden',
      backgroundColor: palette.canvas,
      borderWidth: 2,
      borderColor: selected ? colors.accent : palette.border,
    }}>
      {children}
    </View>
  );
}

export function CinematicSkeleton({ selected, colors }: SkeletonProps) {
  const pFill = colors.progressFill ?? '#00e676';
  const palette = skeletonPalette(colors);
  return (
    <SKWrap selected={selected} colors={colors} w={148} h={84}>
      {/* Image area */}
      <View style={{ flex: 1, backgroundColor: palette.image, justifyContent: 'flex-end' }}>
        {/* Gradient strip */}
        <View style={{ backgroundColor: alpha(colors.bg, 0.72), paddingHorizontal: 8, paddingTop: 8, paddingBottom: 4 }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <SLine w={70} hi palette={palette} />
            <View style={{ width: 26, height: 10, borderRadius: 5, backgroundColor: palette.pill }} />
          </View>
          <SLine w={34} palette={palette} />
        </View>
        {/* Scrubber */}
        <View style={{ flexDirection: 'row', height: 3 }}>
          <View style={{ flex: 0.38, height: 3, backgroundColor: pFill }} />
          <View style={{ flex: 0.62, height: 3, backgroundColor: palette.bar }} />
        </View>
      </View>
    </SKWrap>
  );
}

export function GlassSkeleton({ selected, colors }: SkeletonProps) {
  const pFill = colors.progressFill ?? '#00e676';
  const palette = skeletonPalette(colors);
  return (
    <SKWrap selected={selected} colors={colors} w={148} h={84}>
      <View style={{ flex: 1, backgroundColor: palette.image, justifyContent: 'flex-end' }}>
      <View style={{ height: 28, backgroundColor: palette.glass, borderTopWidth: 1, borderTopColor: palette.border, paddingHorizontal: 8, paddingTop: 5 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
          <SLine w={66} hi palette={palette} />
          <SLine w={26} palette={palette} />
        </View>
        <SLine w={30} palette={palette} />
        {/* Progress inside glass */}
        <View style={{ flexDirection: 'row', height: 3, marginTop: 5 }}>
          <View style={{ flex: 0.38, height: 3, backgroundColor: pFill }} />
          <View style={{ flex: 0.62, height: 3, backgroundColor: palette.bar }} />
        </View>
      </View>
      </View>
    </SKWrap>
  );
}

export function TicketSkeleton({ selected, colors }: SkeletonProps) {
  const pFill = colors.progressFill ?? '#00e676';
  const palette = skeletonPalette(colors);
  return (
    <SKWrap selected={selected} colors={colors} w={148} h={52}>
      {/* full-bleed dark bg already set by SKWrap */}
      <View style={{ flex: 1, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, paddingBottom: 3 }}>
        <View style={{ flex: 1 }}>
          <SLine w={80} hi palette={palette} />
          <SLine w={50} palette={palette} />
          <SLine w={36} palette={palette} />
        </View>
        <View style={{ width: 32, height: 16, borderRadius: 8, backgroundColor: palette.pill, marginLeft: 8 }} />
      </View>
      <View style={{ flexDirection: 'row', height: 3 }}>
        <View style={{ flex: 0.38, height: 3, backgroundColor: pFill }} />
        <View style={{ flex: 0.62, height: 3, backgroundColor: palette.bar }} />
      </View>
    </SKWrap>
  );
}

export function MiniSkeleton({ selected, colors }: SkeletonProps) {
  const pFill = colors.progressFill ?? '#00e676';
  const palette = skeletonPalette(colors);
  return (
    <SKWrap selected={selected} colors={colors} w={148} h={58}>
      <View style={{ flex: 1, flexDirection: 'row' }}>
        {/* Thumb */}
        <View style={{ width: 58, backgroundColor: palette.image }} />
        {/* Text block */}
        <View style={{ flex: 1, padding: 8, justifyContent: 'space-between' }}>
          <View>
            <SLine w={62} hi palette={palette} />
            <SLine w={46} hi palette={palette} />
          </View>
          <View>
            <SLine w={40} palette={palette} />
            <SLine w={28} palette={palette} />
          </View>
        </View>
      </View>
      <View style={{ flexDirection: 'row', height: 3 }}>
        <View style={{ flex: 0.38, height: 3, backgroundColor: pFill }} />
        <View style={{ flex: 0.62, height: 3, backgroundColor: palette.bar }} />
      </View>
    </SKWrap>
  );
}

export function StackedSkeleton({ selected, colors }: SkeletonProps) {
  const pFill = colors.progressFill ?? '#00e676';
  const palette = skeletonPalette(colors);
  return (
    <SKWrap selected={selected} colors={colors} w={96} h={108}>
      {/* Backdrop */}
      <View style={{ height: 54, backgroundColor: palette.image, justifyContent: 'flex-end' }}>
        <View style={{ flexDirection: 'row', height: 3 }}>
          <View style={{ flex: 0.38, height: 3, backgroundColor: pFill }} />
          <View style={{ flex: 0.62, height: 3, backgroundColor: palette.bar }} />
        </View>
      </View>
      {/* Text */}
      <View style={{ flex: 1, padding: 7, justifyContent: 'space-between' }}>
        <View>
          <SLine w={64} hi palette={palette} />
          <SLine w={50} hi palette={palette} />
        </View>
        <View>
          <SLine w={40} palette={palette} />
          <SLine w={32} palette={palette} />
        </View>
      </View>
    </SKWrap>
  );
}

// ── Card styles ─────────────────────────────────���─────────────────────────────
const S = StyleSheet.create({
  base: { marginRight: 12 },

  // Cinematic
  cinematicMeta:  { paddingHorizontal: 12, paddingBottom: 10 },
  cinematicRow:   { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  cinematicTitle: { color: '#fff', fontSize: 14, fontWeight: '700', flex: 1, lineHeight: 18 },
  timePill:       { backgroundColor: 'rgba(255,255,255,0.18)', borderRadius: 20, paddingHorizontal: 8, paddingVertical: 3 },
  timePillText:   { color: '#fff', fontSize: 11, fontWeight: '600' },

  // Glass
  glassRow:          { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  glassTitle:        { fontSize: 13, fontWeight: '700', flex: 1, lineHeight: 17 },
  glassTime:         { fontSize: 11, fontWeight: '600', marginLeft: 6 },
  glassProgressTrack:{ position: 'absolute', bottom: 0, left: 0, height: 3, backgroundColor: 'rgba(255,255,255,0.2)' },
  glassProgressFill: { height: 3 },

  // Ticket
  ticketContent: { flex: 1, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 12, gap: 10 },
  ticketLeft:    { flex: 1 },
  ticketTitle:   { color: '#fff', fontSize: 14, fontWeight: '700', lineHeight: 18, marginBottom: 3 },
  ticketMetaRow: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  ticketMeta:    { color: 'rgba(255,255,255,0.55)', fontSize: 11 },
  ticketMetaDot: { color: 'rgba(255,255,255,0.35)', fontSize: 11 },
  ticketTimePill:{ borderRadius: 20, paddingHorizontal: 9, paddingVertical: 5 },
  ticketTimeText:{ color: '#fff', fontSize: 11, fontWeight: '700' },

  // Mini
  miniTitle:   { fontSize: 12, fontWeight: '700', lineHeight: 16 },
  miniMetaRow: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 2 },
  miniYear:    { fontSize: 10 },
  miniTime:    { fontSize: 10, marginTop: 1 },

  // Stacked
  stackedTitle:   { fontSize: 12, fontWeight: '700', lineHeight: 16, marginBottom: 4 },
  stackedMetaRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  stackedYear:    { fontSize: 10 },
  stackedDot:     { fontSize: 10 },
  stackedTime:    { fontSize: 10, marginTop: 3 },

  // Shared progress
  progressTrack: { position: 'absolute', bottom: 0, left: 0, height: 3, backgroundColor: 'rgba(255,255,255,0.2)' },
  progressFill:  { height: 3 },
});
