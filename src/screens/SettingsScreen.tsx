import React, { useEffect, useState, useMemo } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity, Image,
  StatusBar, Modal, Pressable, PermissionsAndroid, Platform, TextInput, Alert, ActivityIndicator, Linking, BackHandler,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { BlurTargetView } from 'expo-blur';
import { AppleToggle } from '../components/AppleToggle';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { Storage } from '../utils/storage';
import { deleteAccountDevice, fetchAccountBootstrap, type AccountBootstrap } from '../utils/accountPreferences';
import { StackBottomNav } from '../components/StackBottomNav';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { useAuth } from '../context/AuthContext';
import { useTheme, THEMES, ThemeColors } from '../context/ThemeContext';
import { useUIStyle } from '../context/UIStyleContext';
import { useTrakt } from '../context/TraktContext';
import { useLanguage, LANGUAGES } from '../context/LanguageContext';
import { useTorrentServer } from '../context/TorrentServerContext';
import { useStreamSelectionSettings } from '../context/StreamSelectionContext';
import { PlaybackDecoderMode, PlaybackRenderSurface, usePlaybackSettings } from '../context/PlaybackSettingsContext';
import { useDisplaySettings, type ContinueWatchingStyle } from '../context/DisplaySettingsContext';
import { CinematicSkeleton, GlassSkeleton, TicketSkeleton, MiniSkeleton, StackedSkeleton } from '../components/ContinueWatchingCard';
import { useDebrid } from '../context/DebridContext';
import { useAddons } from '../context/AddonContext';
import { useSubtitles } from '../context/SubtitleContext';
import { COMMON_SUBTITLE_LANGUAGES, SubtitleLanguageCode } from '../services/subtitles/SubtitleProvider';
import { DEFAULT_OS_ADDON_URL } from '../services/subtitles/OpenSubtitlesStremioProvider';
import { TORRENT_CACHE_OPTIONS, TORRENT_PROFILE_OPTIONS } from '../types/torrentServer';
import { useTmdbApiKey } from '../context/TmdbApiKeyContext';
import { useProfile } from '../context/ProfileContext';
import { MAX_PROFILES_PER_ACCOUNT, PROFILE_AVATARS } from '../utils/profileApi';
import Constants from 'expo-constants';
import { profileScopedStorageKey } from '../utils/profileStorage';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

const APP_VERSION = Constants.expoConfig?.version ?? '—';

type HomeSection = {
  id: string;
  endpoint: string;
  enabled: boolean;
};

const CURRENT_YEAR = new Date().getFullYear();

function getDefaultSections(provider: 'cinemeta' | 'tmdb'): HomeSection[] {
  if (provider === 'cinemeta') {
    return [
      { id: 'networks', endpoint: '/tmdb/networks', enabled: true },
      { id: 'featured_movie', endpoint: '/cinemeta/catalog/movie/imdbRating', enabled: true },
      { id: 'featured_tv', endpoint: '/cinemeta/catalog/series/imdbRating', enabled: true },
      { id: 'popular_movie', endpoint: '/cinemeta/catalog/movie/top', enabled: true },
      { id: 'popular_tv', endpoint: '/cinemeta/catalog/series/top', enabled: true },
      { id: 'documentaries', endpoint: '/cinemeta/catalog/movie/top?genre=Documentary', enabled: false },
      { id: 'new_movie', endpoint: `/cinemeta/catalog/movie/year?genre=${CURRENT_YEAR}`, enabled: false },
      { id: 'new_tv', endpoint: `/cinemeta/catalog/series/year?genre=${CURRENT_YEAR}`, enabled: false },
    ];
  }

  return [
    { id: 'networks', endpoint: '/tmdb/networks', enabled: true },
    { id: 'trending_movie', endpoint: '/tmdb/trending/movie', enabled: true },
    { id: 'trending_tv', endpoint: '/tmdb/trending/tv', enabled: true },
    { id: 'documentaries', endpoint: '/tmdb/discover?type=movie&genre_id=99&sort_by=popularity.desc', enabled: false },
    { id: 'popular_movie', endpoint: '/tmdb/popular/movie', enabled: false },
    { id: 'popular_tv', endpoint: '/tmdb/popular/tv', enabled: false },
  ];
}

const SETTINGS_KEY = 'home_sections';

// Map section id → translation key
const SECTION_TITLE_KEY: Record<string, any> = {
  featured_movie: 'Featured Movies',
  featured_tv: 'Featured Series',
  new_movie: 'New Movies',
  new_tv: 'New Series',
  popular_movie: 'section_popular_movies',
  popular_tv: 'section_popular_tv',
  trending_movie: 'section_trending_movies',
  trending_tv: 'section_trending_tv',
  networks: 'section_networks',
  documentaries: 'Documentaries',
};

const makeStyles = (c: ThemeColors, resolvedAppearance: 'dark' | 'light') => {
  const sectionSurface = resolvedAppearance === 'dark' ? c.cardBgElevated : c.cardBg;
  const nestedSurface = resolvedAppearance === 'dark' ? c.cardBg : c.bg;

  return StyleSheet.create({
    container: { flex: 1, backgroundColor: c.bg },
    stickyHeader: {
      position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10,
      backgroundColor: c.bgHeader,
      paddingHorizontal: 20, paddingBottom: 18,
    },
    headerFade: { position: 'absolute', left: 0, right: 0, height: 32, zIndex: 9 },
    content: { paddingHorizontal: 20 },
    card: {
      backgroundColor: sectionSurface, borderRadius: 22,
      borderWidth: 1, borderColor: c.border,
      marginBottom: 18, overflow: 'hidden',
    },
    groupedCardTop: {
      marginBottom: 0,
      borderBottomLeftRadius: 0,
      borderBottomRightRadius: 0,
    },
    groupedCardBottom: {
      marginTop: 0,
      borderTopLeftRadius: 0,
      borderTopRightRadius: 0,
    },
    divider: { height: 1, backgroundColor: c.borderSoft, marginLeft: 60 },
    dividerFull: { height: 1, backgroundColor: c.borderSoft },
    row: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18, paddingVertical: 16, gap: 12 },
    rowIcon: { width: 34, height: 34, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
    rowInfo: { flex: 1 },
    rowLabel: { color: c.textPrimary, fontSize: 16, fontWeight: '600' },
    rowSubtitle: { color: c.textSecondary, fontSize: 13, marginTop: 3, lineHeight: 18 },
    rowHint: { color: c.textSecondary, fontSize: 12, marginTop: 4, lineHeight: 18 },
    rowValue: { color: c.textSecondary, fontSize: 14 },
    avatarCircle: { width: 32, height: 32, borderRadius: 16, backgroundColor: c.accent, justifyContent: 'center', alignItems: 'center' },
    avatarLetter: { color: c.buttonText, fontSize: 14, fontWeight: '800' },
    rowActionBtn: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 6,
      paddingHorizontal: 14,
      paddingVertical: 8,
      borderRadius: 999,
      borderWidth: 1,
      borderColor: c.border,
      backgroundColor: c.cardBgElevated,
    },
    rowActionBtnDisabled: { opacity: 0.6 },
    rowActionText: { color: c.accentSoft, fontSize: 12, fontWeight: '700' },
    toastWrap: {
      position: 'absolute',
      left: 20,
      right: 20,
      bottom: 18,
      zIndex: 40,
    },
    toastCard: {
      borderRadius: 14,
      borderWidth: 1,
      paddingHorizontal: 14,
      paddingVertical: 12,
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: 10,
      shadowColor: '#000',
      shadowOpacity: 0.28,
      shadowRadius: 14,
      shadowOffset: { width: 0, height: 8 },
      elevation: 8,
    },
    toastInfo: { flex: 1 },
    toastTitle: { color: c.textPrimary, fontSize: 13, fontWeight: '800' },
    toastText: { color: c.textSecondary, fontSize: 12, lineHeight: 17, marginTop: 2 },
    toastClose: {
      width: 26,
      height: 26,
      borderRadius: 13,
      alignItems: 'center',
      justifyContent: 'center',
    },

    collapseHeader: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 14, gap: 12 },
    collapseLabel: { flex: 1, color: c.textPrimary, fontSize: 15, fontWeight: '600' },
    collapseValue: { color: c.textSecondary, fontSize: 14, fontWeight: '600' },

    themeRow: {
      flexDirection: 'row', alignItems: 'center',
      paddingHorizontal: 16, paddingLeft: 28, paddingVertical: 12,
      borderTopWidth: 1, borderTopColor: c.border, gap: 12,
      backgroundColor: nestedSurface,
    },
    themeSwatch: { width: 28, height: 28, borderRadius: 14, borderWidth: 2 },
    themeInfo: { flex: 1 },
    themeName: { color: c.textPrimary, fontSize: 15, fontWeight: '600' },
    themeDesc: { color: c.textSecondary, fontSize: 13, marginTop: 2 },
    statusPill: {
      paddingHorizontal: 10,
      paddingVertical: 4,
      borderRadius: 999,
      borderWidth: 1,
      alignItems: 'center',
      justifyContent: 'center',
    },
    statusPillText: { fontSize: 11, fontWeight: '800', letterSpacing: 0.4 },

    langRow: {
      flexDirection: 'row', alignItems: 'center',
      paddingHorizontal: 16, paddingLeft: 28, paddingVertical: 13,
      borderTopWidth: 1, borderTopColor: c.border, gap: 12,
      backgroundColor: nestedSurface,
    },
    langFlag: { fontSize: 22, width: 32, textAlign: 'center' },
    langInfo: { flex: 1 },
    langName: { color: c.textPrimary, fontSize: 15, fontWeight: '600' },
    langSub: { color: c.textSecondary, fontSize: 13, marginTop: 1 },

    layoutRow: {
      flexDirection: 'row', alignItems: 'center',
      paddingHorizontal: 16, paddingLeft: 28, paddingVertical: 10,
      borderTopWidth: 1, borderTopColor: c.border, gap: 10,
      backgroundColor: nestedSurface,
    },
    layoutReorder: { flexDirection: 'row', gap: 4 },
    arrowBtn: { width: 26, height: 26, borderRadius: 6, backgroundColor: c.border, justifyContent: 'center', alignItems: 'center' },
    disabledBtn: { opacity: 0.25 },
    layoutSectionTitle: { flex: 1, color: c.textSecondary, fontSize: 13, fontWeight: '500' },
    optionRow: {
      flexDirection: 'row', alignItems: 'center',
      paddingHorizontal: 18, paddingVertical: 16,
      borderTopWidth: 1, borderTopColor: c.border, gap: 12,
      backgroundColor: sectionSurface,
    },
    optionInfo: { flex: 1 },
    optionTitle: { color: c.textPrimary, fontSize: 16, fontWeight: '600' },
    optionSub: { color: c.textSecondary, fontSize: 13, marginTop: 3, lineHeight: 18 },
    optionValue: { color: c.textSecondary, fontSize: 14, fontWeight: '600' },
    serverHero: {
      padding: 20,
      gap: 14,
      backgroundColor: sectionSurface,
    },
    serverHeroTop: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: 14,
    },
    serverHeroIcon: {
      width: 40,
      height: 40,
      borderRadius: 12,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: '#22c55e22',
    },
    serverHeroInfo: { flex: 1, gap: 4 },
    serverHeroTitle: { color: c.textPrimary, fontSize: 17, fontWeight: '800' },
    serverHeroSub: { color: c.textSecondary, fontSize: 13, lineHeight: 19 },
    serverHint: {
      color: '#c8c8d8',
      fontSize: 13,
      lineHeight: 18,
      paddingHorizontal: 16,
      paddingBottom: 16,
    },
    inlineCode: {
      color: c.textPrimary,
      fontSize: 12,
      backgroundColor: c.bg,
      borderWidth: 1,
      borderColor: c.border,
      paddingHorizontal: 8,
      paddingVertical: 4,
      borderRadius: 8,
      overflow: 'hidden',
    },
    helperCard: {
      margin: 16,
      marginTop: 12,
      backgroundColor: c.cardBgElevated,
      borderWidth: 1,
      borderColor: c.border,
      borderRadius: 18,
      padding: 14,
      gap: 10,
    },
    helperText: { color: c.textSecondary, fontSize: 13, lineHeight: 19 },
    disabledBlock: { opacity: 0.45 },
    actionBtn: {
      alignSelf: 'flex-start',
      backgroundColor: c.accent,
      paddingHorizontal: 14,
      paddingVertical: 10,
      borderRadius: 999,
    },
    actionBtnText: { color: c.buttonText, fontSize: 13, fontWeight: '700' },

    uiStyleRow: { flexDirection: 'row', gap: 10, paddingHorizontal: 16, paddingVertical: 14 },
    uiStyleChip: {
      flex: 1,
      paddingVertical: 12,
      paddingHorizontal: 12,
      borderRadius: 12,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: c.bg,
      borderWidth: 1,
      borderColor: c.border,
      position: 'relative',
    },
    uiStyleChipActive: { backgroundColor: c.accent + '22', borderColor: c.accent },
    uiStyleChipActiveLight: {
      backgroundColor: 'rgba(17,24,39,0.08)',
      borderColor: 'rgba(17,24,39,0.26)',
    },
    uiStyleChipText: { color: c.textSecondary, fontSize: 13, fontWeight: '700' },
    uiStyleChipTextActive: { color: c.accentSoft, fontSize: 13, fontWeight: '700' },
    uiStyleChipCheck: {
      position: 'absolute',
      top: 8,
      right: 8,
    },

    modalBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.75)', justifyContent: 'center', alignItems: 'center' },
    modalCard: {
      width: '82%',
      backgroundColor: c.cardBg,
      borderRadius: 20,
      padding: 28,
      alignItems: 'center',
      borderWidth: 1,
      borderColor: c.border,
      shadowColor: '#000',
      shadowOpacity: 0.12,
      shadowRadius: 18,
      shadowOffset: { width: 0, height: 8 },
      elevation: 18,
    },
    modalIconWrap: { width: 56, height: 56, borderRadius: 28, backgroundColor: '#c9707018', justifyContent: 'center', alignItems: 'center', marginBottom: 16 },
    modalTitle: { color: c.textPrimary, fontSize: 17, fontWeight: '800', marginBottom: 10, textAlign: 'center' },
    modalDesc: { color: c.textSecondary, fontSize: 13, textAlign: 'center', lineHeight: 20, marginBottom: 24 },
    modalActions: { flexDirection: 'row', gap: 12, width: '100%' },
    modalCancel: { flex: 1, paddingVertical: 13, borderRadius: 999, backgroundColor: c.inputBg, alignItems: 'center', borderWidth: 1, borderColor: c.border },
    modalCancelText: { color: c.accentSoft, fontWeight: '700', fontSize: 14 },
    modalConfirm: { flex: 1, paddingVertical: 13, borderRadius: 999, backgroundColor: '#c9707022', alignItems: 'center', borderWidth: 1, borderColor: '#c9707044' },
    modalConfirmText: { color: '#c97070', fontWeight: '700', fontSize: 14 },
    pickerModalCard: {
      width: '88%',
      backgroundColor: c.cardBg,
      borderRadius: 22,
      padding: 18,
      maxHeight: '82%',
      borderWidth: 1,
      borderColor: c.border,
      gap: 8,
      shadowColor: '#000',
      shadowOpacity: 0.12,
      shadowRadius: 18,
      shadowOffset: { width: 0, height: 8 },
      elevation: 18,
    },
    pickerHeader: { gap: 4, paddingHorizontal: 4, paddingBottom: 8 },
    pickerOptionsScroll: { maxHeight: 420 },
    pickerOptionsContent: { gap: 8, paddingBottom: 4 },
    pickerTitle: { color: c.textPrimary, fontSize: 18, fontWeight: '800' },
    pickerSub: { color: c.textSecondary, fontSize: 12, lineHeight: 18 },
    pickerOption: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingHorizontal: 14,
      paddingVertical: 14,
      borderRadius: 14,
      backgroundColor: c.bg,
      borderWidth: 1,
      borderColor: c.border,
    },
    pickerOptionTextWrap: { flex: 1, gap: 2 },
    pickerOptionTitle: { color: c.textPrimary, fontSize: 14, fontWeight: '700' },
    pickerOptionSub: { color: c.textSecondary, fontSize: 12, lineHeight: 17 },
    themePickerSwatchWrap: {
      width: 52,
      alignItems: 'center',
      justifyContent: 'center',
    },
    themePickerSwatchOuter: {
      width: 34,
      height: 34,
      borderRadius: 17,
      borderWidth: 1,
      borderColor: c.border,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: resolvedAppearance === 'light' ? c.cardBgElevated : c.cardBg,
    },
    themePickerSwatchInner: {
      width: 20,
      height: 20,
      borderRadius: 10,
    },
    pageStylePreview: {
      width: 66,
      height: 86,
      borderRadius: 12,
      overflow: 'hidden',
      borderWidth: 1,
      borderColor: c.border,
      backgroundColor: resolvedAppearance === 'dark' ? '#111827' : '#f8fafc',
      padding: 6,
      gap: 5,
    },
    pagePreviewHero: {
      borderRadius: 7,
      backgroundColor: c.accent + '55',
    },
    pagePreviewLine: {
      height: 5,
      borderRadius: 999,
      backgroundColor: c.textSecondary + '55',
    },
    pagePreviewPill: {
      width: 18,
      height: 6,
      borderRadius: 999,
      backgroundColor: c.accent + '88',
    },
    pagePreviewRow: {
      flexDirection: 'row',
      gap: 4,
    },
    pickerCloseBtn: {
      marginTop: 8,
      borderRadius: 999,
      alignItems: 'center',
      justifyContent: 'center',
      paddingVertical: 13,
      backgroundColor: c.inputBg,
      borderWidth: 1,
      borderColor: c.border,
    },
    pickerCloseText: { color: c.accentSoft, fontSize: 14, fontWeight: '700' },
  });
};

// ─── Reusable row components ──────────────────────────────────────────────────

function LegalSection({ title, children, c }: { title: string; children: React.ReactNode; c: ThemeColors }) {
  return (
    <View style={{ gap: 6 }}>
      <Text style={{ color: c.textPrimary, fontSize: 14, fontWeight: '700' }}>{title}</Text>
      <Text style={{ color: c.textSecondary, fontSize: 13, lineHeight: 20 }}>{children}</Text>
    </View>
  );
}

function SectionHeader({ title, c }: { title: string; c: ThemeColors }) {
  return (
    <Text style={{ color: c.textSecondary, fontSize: 12, fontWeight: '700', letterSpacing: 0.8, marginBottom: 10, marginTop: 10, paddingHorizontal: 6, textTransform: 'uppercase' }}>
      {title}
    </Text>
  );
}

function NavRow({ icon, label, subtitle, onPress, iconColor, c }: {
  icon: IoniconName; label: string; subtitle?: React.ReactNode; onPress?: () => void; iconColor: string; c: ThemeColors;
}) {
  return (
    <TouchableOpacity style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 14, gap: 12 }} onPress={onPress} activeOpacity={0.7}>
      <View style={{ width: 32, height: 32, borderRadius: 8, justifyContent: 'center', alignItems: 'center', backgroundColor: iconColor + '22' }}>
        <Ionicons name={icon} size={18} color={iconColor} />
      </View>
      <View style={{ flex: 1 }}>
        <Text style={{ color: c.textPrimary, fontSize: 14, fontWeight: '600' }}>{label}</Text>
        {typeof subtitle === 'string'
          ? <Text style={{ color: c.mutedText, fontSize: 12, marginTop: 2 }}>{subtitle}</Text>
          : subtitle}
      </View>
      <Ionicons name="chevron-forward" size={16} color={c.placeholder} />
    </TouchableOpacity>
  );
}

function InfoRow({ icon, label, value, iconColor, c }: {
  icon: IoniconName; label: string; value: string; iconColor: string; c: ThemeColors;
}) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 14, gap: 12 }}>
      <View style={{ width: 32, height: 32, borderRadius: 8, justifyContent: 'center', alignItems: 'center', backgroundColor: iconColor + '22' }}>
        <Ionicons name={icon} size={18} color={iconColor} />
      </View>
      <View style={{ flex: 1 }}>
        <Text style={{ color: c.textPrimary, fontSize: 14, fontWeight: '600' }}>{label}</Text>
      </View>
      <Text style={{ color: c.mutedText, fontSize: 13 }}>{value}</Text>
    </View>
  );
}

// ─── Main screen ──────────────────────────────────────────────────────────────

function PageStyleWireframe({ value, styles }: { value: string | number; styles: ReturnType<typeof makeStyles> }) {
  if (!['classic', 'centered', 'glass'].includes(String(value))) return null;

  if (value === 'glass') {
    return (
      <View style={styles.pageStylePreview}>
        <View style={[styles.pagePreviewLine, { width: 34, alignSelf: 'center' }]} />
        <View style={[styles.pagePreviewHero, { height: 30, borderRadius: 9 }]} />
        <View style={[styles.pagePreviewLine, { width: 44, alignSelf: 'center' }]} />
        <View style={[styles.pagePreviewRow, { justifyContent: 'center' }]}>
          <View style={styles.pagePreviewPill} />
          <View style={styles.pagePreviewPill} />
        </View>
        <View style={[styles.pagePreviewLine, { width: 52 }]} />
      </View>
    );
  }

  if (value === 'centered') {
    return (
      <View style={styles.pageStylePreview}>
        <View style={[styles.pagePreviewHero, { height: 38, borderRadius: 4 }]} />
        <View style={[styles.pagePreviewLine, { width: 42, alignSelf: 'center' }]} />
        <View style={[styles.pagePreviewRow, { justifyContent: 'center' }]}>
          <View style={styles.pagePreviewPill} />
          <View style={styles.pagePreviewPill} />
        </View>
        <View style={[styles.pagePreviewLine, { width: 52 }]} />
      </View>
    );
  }

  return (
    <View style={styles.pageStylePreview}>
      <View style={[styles.pagePreviewHero, { height: 30, borderRadius: 4 }]} />
      <View style={{ flexDirection: 'row', gap: 5 }}>
        <View style={[styles.pagePreviewHero, { width: 18, height: 28, borderRadius: 4 }]} />
        <View style={{ flex: 1, gap: 4, paddingTop: 3 }}>
          <View style={[styles.pagePreviewLine, { width: 30 }]} />
          <View style={[styles.pagePreviewLine, { width: 22 }]} />
          <View style={styles.pagePreviewPill} />
        </View>
      </View>
      <View style={[styles.pagePreviewLine, { width: 50 }]} />
    </View>
  );
}

function PickerModal({
  visible,
  title,
  subtitle,
  options,
  selectedValue,
  onSelect,
  onClose,
  colors,
  resolvedAppearance,
  styles,
  renderPreview,
}: {
  visible: boolean;
  title: string;
  subtitle: string;
  options: Array<{ value: string | number; label: string; description: string }>;
  selectedValue: string | number;
  onSelect: (value: string | number) => void;
  onClose: () => void;
  colors: ThemeColors;
  resolvedAppearance: 'dark' | 'light';
  styles: ReturnType<typeof makeStyles>;
  renderPreview?: (option: { value: string | number; label: string; description: string }, active: boolean) => React.ReactNode;
}) {
  const isLightMode = resolvedAppearance === 'light';
  const activeBorderColor = isLightMode ? 'rgba(17,24,39,0.28)' : colors.accent;
  const activeBackgroundColor = isLightMode ? 'rgba(17,24,39,0.08)' : colors.accent + '14';
  const activeCheckColor = isLightMode ? '#111111' : colors.accent;
  const showScroll = options.length > 6;
  const optionsContent = (
    <>
      {options.map(option => {
        const active = option.value === selectedValue;
        return (
          <TouchableOpacity
            key={String(option.value)}
            style={[
              styles.pickerOption,
              active && { borderColor: activeBorderColor, backgroundColor: activeBackgroundColor },
            ]}
            onPress={() => onSelect(option.value)}
            activeOpacity={0.8}
          >
            {renderPreview ? renderPreview(option, active) : null}
            <View style={styles.pickerOptionTextWrap}>
              <Text style={styles.pickerOptionTitle}>{option.label}</Text>
              <Text style={styles.pickerOptionSub}>{option.description}</Text>
            </View>
            {active && <Ionicons name="checkmark-circle" size={22} color={activeCheckColor} />}
          </TouchableOpacity>
        );
      })}
    </>
  );

  return (
    <Modal visible={visible} transparent animationType="fade" statusBarTranslucent onRequestClose={onClose}>
      <Pressable style={styles.modalBackdrop} onPress={onClose}>
        <Pressable style={styles.pickerModalCard} onPress={() => { }}>
          <View style={styles.pickerHeader}>
            <Text style={styles.pickerTitle}>{title}</Text>
            <Text style={styles.pickerSub}>{subtitle}</Text>
          </View>

          {showScroll ? (
            <ScrollView
              style={styles.pickerOptionsScroll}
              contentContainerStyle={styles.pickerOptionsContent}
              showsVerticalScrollIndicator={false}
            >
              {optionsContent}
            </ScrollView>
          ) : (
            <View style={styles.pickerOptionsContent}>
              {optionsContent}
            </View>
          )}

          <TouchableOpacity style={styles.pickerCloseBtn} onPress={onClose} activeOpacity={0.8}>
            <Text style={styles.pickerCloseText}>Close</Text>
          </TouchableOpacity>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  const decimals = value >= 10 || unitIndex === 0 ? 0 : 1;
  return `${value.toFixed(decimals)} ${units[unitIndex]}`;
}

export const SettingsScreen = ({ navigation }: any) => {
  const blurTargetRef = React.useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { user, signOut } = useAuth();
  const { theme, appearance, resolvedAppearance, setAppearance, setThemeId, showHeroSynopsis, setShowHeroSynopsis } = useTheme();
  const { uiStyle, setUiStyle } = useUIStyle();
  const { isConnected, traktUsername, checkStatus } = useTrakt();
  const { language, setLanguage, t } = useLanguage();
  const { refreshAccounts: refreshDebridAccounts } = useDebrid();
  const { refreshAddons } = useAddons();
  const {
    pictureInPictureEnabled,
    setPictureInPictureEnabled,
    showNavLabels,
    setShowNavLabels,
    continueWatchingStyle,
    setContinueWatchingStyle,
    showStreamsList,
    setShowStreamsList,
    vividAmbientEnabled,
    setVividAmbientEnabled,
    isReady: displaySettingsReady,
  } = useDisplaySettings();
  const {
    tmdbKeyEnabled,
    tmdbApiKey,
    metadataProvider,
    setTmdbApiKey,
    setTmdbKeyEnabled,
    setMetadataProvider,
  } = useTmdbApiKey();
  const { profiles, activeProfile: activeStreamProfile, clearActiveProfile } = useProfile();
  const [tmdbKeyInput, setTmdbKeyInput] = React.useState('');
  React.useEffect(() => { setTmdbKeyInput(tmdbApiKey); }, [tmdbApiKey]);
  const {
    config: torrentConfig,
    status: torrentStatus,
    updateConfig: updateTorrentConfig,
    refreshStatus: refreshTorrentStatus,
  } = useTorrentServer();
  const {
    enabled: streamSelectionEnabled,
    setEnabled: setStreamSelectionEnabled,
    shortSourceFilterEnabled,
    setShortSourceFilterEnabled,
    preferredQuality,
    setPreferredQuality,
    maxFileSizeGB,
    setMaxFileSizeGB,
    refreshFromCloud: refreshStreamSelectionFromCloud,
    isReady: streamSelectionReady,
  } = useStreamSelectionSettings();
  const {
    decoderMode,
    setDecoderMode,
    renderSurface,
    setRenderSurface,
    preferEmbeddedMpvByDefault,
    setPreferEmbeddedMpvByDefault,
    refreshFromCloud: refreshPlaybackFromCloud,
    isReady: playbackSettingsReady,
  } = usePlaybackSettings();
  const {
    autoLoadEnabled,
    setAutoLoadEnabled,
    languageOrder,
    setLanguageOrder,
    preferHI,
    setPreferHI,
    preferForced,
    setPreferForced,
    addonUrl,
    setAddonUrl,
    clearFileCache: clearSubtitleFileCache,
    getFileCacheSize,
    isReady: subtitleSettingsReady,
  } = useSubtitles();
  const { colors } = theme;
  const styles = useMemo(() => makeStyles(colors, resolvedAppearance), [colors, resolvedAppearance]);
  const visibleThemes = THEMES;
  const defaultSections = useMemo(() => getDefaultSections(metadataProvider), [metadataProvider]);

  const [sections, setSections] = useState(defaultSections);
  const [appearanceOpen, setAppearanceOpen] = useState(false);
  const [pageStyleOpen, setPageStyleOpen] = useState(false);
  const [layoutOpen, setLayoutOpen] = useState(false);
  const [cwStyleOpen, setCwStyleOpen] = useState(false);
  const [playerOpen, setPlayerOpen] = useState(false);
  const [tmdbModalOpen, setTmdbModalOpen] = useState(false);
  const [pickerModal, setPickerModal] = useState<'profile' | 'cache' | 'decoder' | 'surface' | 'quality' | 'fileSize' | 'theme' | 'language' | null>(null);
  const [signOutModal, setSignOutModal] = useState(false);
  const [legalModal, setLegalModal] = useState(false);

  useFocusEffect(
    React.useCallback(() => {
      const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
        if (pageStyleOpen) {
          setPageStyleOpen(false);
          return true;
        }
        if (appearanceOpen) {
          setAppearanceOpen(false);
          return true;
        }
        if (cwStyleOpen) {
          setCwStyleOpen(false);
          return true;
        }
        if (pickerModal) {
          setPickerModal(null);
          return true;
        }
        if (tmdbModalOpen) {
          setTmdbModalOpen(false);
          return true;
        }
        if (legalModal) {
          setLegalModal(false);
          return true;
        }
        if (signOutModal) {
          setSignOutModal(false);
          return true;
        }
        navigation.navigate('Home');
        return true;
      });

      return () => subscription.remove();
    }, [appearanceOpen, cwStyleOpen, legalModal, navigation, pageStyleOpen, pickerModal, signOutModal, tmdbModalOpen]),
  );
  const [disconnectModal, setDisconnectModal] = useState<{ id: string; name: string } | null>(null);
  const [disconnectingDeviceId, setDisconnectingDeviceId] = useState<string | null>(null);

  // In monochrome theme, tone down hardcoded icon colors so they don't clash
  const ic = (color: string) => theme.id === 'monochrome' ? colors.mutedText : color;
  const isLightMode = resolvedAppearance === 'light';
  const lightMonoContrast = appearance === 'light' && theme.id === 'monochrome';
  const featureIcon = (color: string) => ({
    backgroundColor: lightMonoContrast ? 'rgba(17,24,39,0.10)' : ic(color) + '22',
    iconColor: lightMonoContrast ? '#111111' : ic(color),
  });
  const appearanceIcon = featureIcon('#9b5de5');
  const synopsisIcon = featureIcon('#22d3ee');
  const themeIcon = featureIcon('#f7b731');
  const settingsCheckColor = lightMonoContrast ? '#111111' : colors.accent;
  const accountIconColor = isLightMode ? '#111111' : colors.toggleOn;
  const accountIconBackground = isLightMode ? 'rgba(17,24,39,0.10)' : colors.accent + '22';
  const [headerHeight, setHeaderHeight] = useState(0);
  const [syncRefreshing, setSyncRefreshing] = useState(false);
  const [profileName, setProfileName] = useState<string | null>(null);
  const [accountBootstrap, setAccountBootstrap] = useState<AccountBootstrap | null>(null);
  const [syncNotice, setSyncNotice] = useState<{ tone: 'success' | 'error'; title: string; text: string } | null>(null);
  // Subtitle settings UI state
  const [subtitleOpen, setSubtitleOpen] = useState(false);
  const [subtitleLangOpen, setSubtitleLangOpen] = useState(false);
  const [subtitleCacheSize, setSubtitleCacheSize] = useState<number | null>(null);
  const [addonUrlDraft, setAddonUrlDraft] = useState(addonUrl);
  const sectionSettingsKey = profileScopedStorageKey(SETTINGS_KEY, user?.uid, activeStreamProfile?.id);

  const isServerMode = torrentConfig.streamingMode === 'server';
  const torrentStatusColors = !isServerMode
    ? { backgroundColor: '#64748b22', borderColor: '#64748b55', color: '#cbd5e1' }
    : torrentStatus.isOnline
      ? { backgroundColor: '#0ea75f22', borderColor: '#0ea75f55', color: '#57e393' }
      : { backgroundColor: '#d9770622', borderColor: '#d9770655', color: '#f5b76b' };

  const activeProfile = TORRENT_PROFILE_OPTIONS.find(option => option.value === torrentConfig.profile);
  const activeCache = TORRENT_CACHE_OPTIONS.find(option => option.value === torrentConfig.cacheSizeGb);
  const profilePickerOptions = TORRENT_PROFILE_OPTIONS.map(option => ({
    value: option.value,
    label: option.label,
    description: t(`settings_profile_${option.value}` as any),
  }));
  const cachePickerOptions = TORRENT_CACHE_OPTIONS.map(option => ({
    value: option.value,
    label: option.label,
    description: option.value === 0 ? t('settings_cache_none_sub') : t('settings_cache_size_sub'),
  }));
  const appearancePickerOptions = [
    { value: 'dark', label: t('settings_dark'), description: t('settings_dark_sub') },
    { value: 'light', label: t('settings_light'), description: t('settings_light_sub') },
    { value: 'system', label: t('settings_system'), description: t('settings_system_sub') },
  ];
  const themePickerOptions = visibleThemes.map(th => ({
    value: th.id,
    label: th.name,
    description: th.description,
  }));
  const languagePickerOptions = LANGUAGES.map(lang => ({
    value: lang.code,
    label: `${lang.flag} ${lang.name}`,
    description: lang.englishName,
  }));
  const pageStyleOptions = [
    { value: 'classic', label: t('settings_page_style_classic'), description: t('settings_page_style_sub') },
    { value: 'centered', label: t('settings_page_style_centered'), description: t('settings_page_style_sub') },
    { value: 'glass', label: 'Glassy Hero', description: 'Title above a rounded hero image on a blurred ambient detail page.' },
  ];
  const decoderPickerOptions: Array<{ value: PlaybackDecoderMode; label: string; description: string }> = [
    { value: 'auto', label: 'Auto', description: 'Uses hardware with software fallback for the best compatibility.' },
    { value: 'hardware', label: 'HW', description: 'Uses hardware decoding only for the fastest path when the device supports it well.' },
    { value: 'hardware_plus', label: 'HW+', description: 'Uses hardware decoding first, with extra renderer support when needed.' },
    { value: 'software', label: 'SW', description: 'Forces software decoding for difficult files at the cost of more battery and CPU.' },
  ];
  const surfacePickerOptions: Array<{ value: PlaybackRenderSurface; label: string; description: string }> = [
    { value: 'standard', label: 'Standard', description: 'Uses SurfaceView, which is the recommended Android rendering path.' },
    { value: 'compatibility', label: 'Compatibility', description: 'Uses TextureView for devices or files that behave better with the compatibility surface.' },
  ];
  const fileSizePickerOptions: Array<{ value: number; label: string; description: string }> = [
    { value: 0,  label: 'Unlimited', description: 'No file size limit — all streams are considered.' },
    { value: 2,  label: '2 GB',      description: 'Suitable for 720p and lower quality streams.' },
    { value: 4,  label: '4 GB',      description: 'Covers most 1080p encodes.' },
    { value: 8,  label: '8 GB',      description: 'Covers 1080p BluRay and most 4K encodes.' },
    { value: 15, label: '15 GB',     description: 'Large 4K remuxes included.' },
    { value: 20, label: '20 GB',     description: 'Everything up to ultra-large 4K releases.' },
  ];
  const qualityPickerOptions = [
    { value: 'best', label: 'Best Available', description: 'Prefer the highest ranked stream.' },
    { value: '4k', label: '4K', description: 'Prefer UHD and 2160p streams when available.' },
    { value: '1080p', label: '1080p', description: 'Prefer full HD and avoid unnecessary 4K streams.' },
    { value: '720p', label: '720p', description: 'Prefer smaller HD streams.' },
  ];
  const getThemeAccentColor = (themeId: string) => {
    if (themeId === 'monochrome') {
      return resolvedAppearance === 'light' ? '#111111' : '#ffffff';
    }
    return THEMES.find(item => item.id === themeId)?.swatch ?? colors.accent;
  };
  const activeDecoderLabel = decoderPickerOptions.find(option => option.value === decoderMode)?.label ?? 'Auto';
  const activeSurfaceLabel = surfacePickerOptions.find(option => option.value === renderSurface)?.label ?? 'Standard';

  useEffect(() => {
    Promise.all([
      Storage.getItem(sectionSettingsKey),
      Storage.getItem(SETTINGS_KEY),
    ]).then(([val, legacyVal]: [string | null, string | null]) => {
      const resolvedValue = val ?? legacyVal;
      if (resolvedValue) {
        const savedSections: any[] = JSON.parse(resolvedValue);
        const savedMap = new Map(savedSections.map(s => [s.id, s]));
        const known = savedSections
          .filter(s => defaultSections.find(d => d.id === s.id))
          .map(s => ({ ...defaultSections.find(d => d.id === s.id)!, enabled: s.enabled }));
        const newOnes = defaultSections.filter(d => !savedMap.has(d.id));
        setSections([...known, ...newOnes]);
      } else {
        setSections(defaultSections);
      }
    });
  }, [defaultSections, sectionSettingsKey]);

  useEffect(() => {
    void refreshTorrentStatus();
  }, [refreshTorrentStatus]);

  useEffect(() => {
    let cancelled = false;

    if (!user) {
      setProfileName(null);
      setAccountBootstrap(null);
      return;
    }

    fetchAccountBootstrap(user, activeStreamProfile?.id).then((bootstrap) => {
      if (cancelled) return;
      setAccountBootstrap(bootstrap);
      const displayName = bootstrap?.profile?.displayName?.trim();
      setProfileName(displayName ? displayName : null);
    });

    return () => {
      cancelled = true;
    };
  }, [user]);

  useEffect(() => {
    if (!syncNotice) return;

    const timeout = setTimeout(() => {
      setSyncNotice(null);
    }, 4000);

    return () => clearTimeout(timeout);
  }, [syncNotice]);

  // Sync addonUrlDraft when the persisted value loads from storage
  useEffect(() => { setAddonUrlDraft(addonUrl); }, [addonUrl]);

  const persistSections = (next: HomeSection[]) => {
    setSections(next);
    void Storage.setItem(sectionSettingsKey, JSON.stringify(next));
  };

  const toggle = (id: string) => {
    persistSections(sections.map(s => (s.id === id ? { ...s, enabled: !s.enabled } : s)));
  };

  const moveUp = (index: number) => {
    if (index === 0) return;
    const next = [...sections];
    [next[index - 1], next[index]] = [next[index], next[index - 1]];
    persistSections(next);
  };

  const moveDown = (index: number) => {
    if (index === sections.length - 1) return;
    const next = [...sections];
    [next[index + 1], next[index]] = [next[index], next[index + 1]];
    persistSections(next);
  };

  const handleForegroundToggle = async (value: boolean) => {
    if (!value) {
      await updateTorrentConfig({ runAsForegroundService: false });
      return;
    }

    if (Platform.OS === 'android' && typeof Platform.Version === 'number' && Platform.Version >= 33) {
      const alreadyGranted = await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
      if (!alreadyGranted) {
        const result = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
          {
            title: 'Allow notifications',
            message: 'Foreground server mode needs notification permission so Android can keep the server alive.',
            buttonPositive: 'Allow',
            buttonNegative: 'Not now',
          },
        );

        if (result !== PermissionsAndroid.RESULTS.GRANTED) {
          Alert.alert(
            'Notification permission needed',
            'Foreground server mode stays off until notification permission is granted.',
          );
          await updateTorrentConfig({ runAsForegroundService: false });
          return;
        }
      }
    }

    await updateTorrentConfig({ runAsForegroundService: true });
  };

  const serverFeedback = !isServerMode
    ? t('settings_server_off_desc')
    : torrentStatus.isOnline
      ? t('settings_server_online_desc')
      : torrentStatus.lastStartupError
        ? `Native server failed to start: ${torrentStatus.lastStartupError}`
        : torrentStatus.recoveryMode === 'starting' || torrentStatus.recoveryMode === 'recovering'
          ? `Native server is ${torrentStatus.recoveryMode}.`
          : t('settings_server_offline_desc');
  const foregroundFeedback = torrentStatus.requestedForeground
    ? (torrentStatus.isForeground
      ? t('settings_foreground_active')
      : (torrentStatus.foregroundDowngradeReason || t('settings_foreground_downgraded')))
    : null;

  const handleRefreshSync = async () => {
    if (!user || syncRefreshing) return;

    setSyncRefreshing(true);
    try {
      const [bootstrap] = await Promise.all([
          fetchAccountBootstrap(user, activeStreamProfile?.id),
        refreshPlaybackFromCloud(),
        refreshStreamSelectionFromCloud(),
        refreshDebridAccounts(),
        refreshAddons(),
        checkStatus(),
      ]);

      setAccountBootstrap(bootstrap);
      const displayName = bootstrap?.profile?.displayName?.trim();
      setProfileName(displayName ? displayName : null);
      setSyncNotice({
        tone: 'success',
        title: 'Sync refreshed',
        text: 'Pulled the latest cloud settings and connected-service status from your account.',
      });
    } catch {
      setSyncNotice({
        tone: 'error',
        title: 'Refresh failed',
        text: 'Could not pull the latest cloud settings right now.',
      });
    } finally {
      setSyncRefreshing(false);
    }
  };
  const serverSettingsDisabled = !isServerMode;
  const serverSettingsDisabledStyle = serverSettingsDisabled ? styles.disabledBlock : null;
  const linkedTvDevices = (accountBootstrap?.devices ?? []).filter((device) => {
    const platform = String(device.platform ?? '').toLowerCase();
    const deviceType = String(device.deviceType ?? '').toLowerCase();
    return deviceType === 'tv' || platform.includes('tv');
  });
  const tmdbStorageTitle = user ? 'TMDB Key Storage' : 'Local TMDB Key';
  const tmdbStorageSubtitle = user
    ? 'Saved locally on this device first, then synced to your signed-in account.'
    : 'Saved only on this device for now. Sign in later if you want it synced across devices.';
  const metadataProviderLabel = metadataProvider === 'tmdb' ? 'StreamDek Catalog' : 'Cinemeta';
  const metadataProviderSubtitle = metadataProvider === 'tmdb'
    ? (user
      ? 'StreamDek Catalog active. Using your TMDB key with account sync.'
      : 'StreamDek Catalog active. Using your TMDB key stored on this device.')
    : 'Built-in Cinemeta catalog with zero setup. Selected by default.';
  const tmdbKeyRequired = metadataProvider === 'tmdb' && !tmdbApiKey.trim();

  const handleMetadataProviderSelect = React.useCallback(async (provider: 'cinemeta' | 'tmdb') => {
    await setMetadataProvider(provider);
  }, [setMetadataProvider]);

  const handleSaveTmdbKey = React.useCallback(async () => {
    const key = tmdbKeyInput.trim();
    await setTmdbApiKey(key);
    await setTmdbKeyEnabled(key.length > 0);
    if (key.length > 0 && metadataProvider !== 'tmdb') {
      await setMetadataProvider('tmdb');
    }
  }, [metadataProvider, setMetadataProvider, setTmdbApiKey, setTmdbKeyEnabled, tmdbKeyInput]);

  const handleDisconnectDevice = async (deviceId: string, deviceName: string) => {
    setDisconnectModal({ id: deviceId, name: deviceName });
  };

  const confirmDisconnectDevice = async () => {
    if (!user || !disconnectModal) return;

    setDisconnectingDeviceId(disconnectModal.id);
    try {
      await deleteAccountDevice(user, disconnectModal.id);
      setAccountBootstrap((current) => current ? ({
        ...current,
        devices: (current.devices ?? []).filter((device) => device.id !== disconnectModal.id),
        sessions: (current.sessions ?? []).filter((session) => session.deviceId !== disconnectModal.id),
      }) : current);
      setSyncNotice({
        tone: 'success',
        title: 'TV disconnected',
        text: `${disconnectModal.name} has been removed from your account.`,
      });
      setDisconnectModal(null);
    } catch (error: any) {
      setSyncNotice({
        tone: 'error',
        title: 'Disconnect failed',
        text: error?.message ?? 'Could not disconnect this TV right now.',
      });
    } finally {
      setDisconnectingDeviceId(null);
    }
  };

  return (
    <View style={{ flex: 1 }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
    <View style={styles.container}>
      <StatusBar barStyle={resolvedAppearance === 'light' ? 'dark-content' : 'light-content'} translucent backgroundColor="transparent" />

      {/* Sign-out confirmation modal */}
      <Modal visible={signOutModal} transparent animationType="fade" statusBarTranslucent onRequestClose={() => setSignOutModal(false)}>
        <Pressable style={styles.modalBackdrop} onPress={() => setSignOutModal(false)}>
          <Pressable style={styles.modalCard} onPress={() => { }}>
            <View style={styles.modalIconWrap}>
              <Ionicons name="log-out-outline" size={26} color="#c97070" />
            </View>
            <Text style={styles.modalTitle}>{t('settings_sign_out')}</Text>
            <Text style={styles.modalDesc}>{t('settings_sign_out_confirm')}</Text>
            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.modalCancel} onPress={() => setSignOutModal(false)} activeOpacity={0.8}>
                <Text style={styles.modalCancelText}>{t('settings_cancel')}</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalConfirm} onPress={() => { setSignOutModal(false); signOut(); }} activeOpacity={0.8}>
                <Text style={styles.modalConfirmText}>{t('settings_sign_out')}</Text>
              </TouchableOpacity>
            </View>
          </Pressable>
        </Pressable>
      </Modal>

      <Modal visible={!!disconnectModal} transparent animationType="fade" statusBarTranslucent onRequestClose={() => setDisconnectModal(null)}>
        <Pressable style={styles.modalBackdrop} onPress={() => setDisconnectModal(null)}>
          <Pressable style={styles.modalCard} onPress={() => { }}>
            <View style={styles.modalIconWrap}>
              <Ionicons name="tv-outline" size={26} color="#c97070" />
            </View>
            <Text style={styles.modalTitle}>Disconnect TV</Text>
            <Text style={styles.modalDesc}>
              {disconnectModal
                ? `Disconnect ${disconnectModal.name} from your StreamDek account?`
                : 'Disconnect this TV from your StreamDek account?'}
            </Text>
            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.modalCancel}
                onPress={() => setDisconnectModal(null)}
                activeOpacity={0.8}
                disabled={disconnectingDeviceId === disconnectModal?.id}
              >
                <Text style={styles.modalCancelText}>{t('settings_cancel')}</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.modalConfirm}
                onPress={() => { void confirmDisconnectDevice(); }}
                activeOpacity={0.8}
                disabled={disconnectingDeviceId === disconnectModal?.id}
              >
                {disconnectingDeviceId === disconnectModal?.id
                  ? <ActivityIndicator color="#c97070" />
                  : <Text style={styles.modalConfirmText}>Disconnect</Text>}
              </TouchableOpacity>
            </View>
          </Pressable>
        </Pressable>
      </Modal>

      <PickerModal
        visible={pickerModal === 'profile'}
        title={t('settings_torrent_profile')}
        subtitle={t('settings_torrent_profile_sub')}
        options={profilePickerOptions}
        selectedValue={torrentConfig.profile}
        resolvedAppearance={resolvedAppearance}
        onSelect={value => {
          updateTorrentConfig({ profile: value as any });
          setPickerModal(null);
        }}
        onClose={() => setPickerModal(null)}
        colors={colors}
        styles={styles}
      />

      <PickerModal
        visible={pickerModal === 'cache'}
        title={t('settings_cache_size')}
        subtitle={t('settings_cache_size_picker_sub')}
        options={cachePickerOptions}
        selectedValue={torrentConfig.cacheSizeGb}
        resolvedAppearance={resolvedAppearance}
        onSelect={value => {
          updateTorrentConfig({ cacheSizeGb: value as any });
          setPickerModal(null);
        }}
        onClose={() => setPickerModal(null)}
        colors={colors}
        styles={styles}
      />

      <PickerModal
        visible={pickerModal === 'decoder'}
        title={t('settings_decoder_mode')}
        subtitle={t('settings_decoder_mode_sub')}
        options={decoderPickerOptions}
        selectedValue={decoderMode}
        resolvedAppearance={resolvedAppearance}
        onSelect={value => {
          void setDecoderMode(value as PlaybackDecoderMode);
          setPickerModal(null);
        }}
        onClose={() => setPickerModal(null)}
        colors={colors}
        styles={styles}
      />

      <PickerModal
        visible={pickerModal === 'surface'}
        title={t('settings_render_surface')}
        subtitle={t('settings_render_surface_sub')}
        options={surfacePickerOptions}
        selectedValue={renderSurface}
        resolvedAppearance={resolvedAppearance}
        onSelect={value => {
          void setRenderSurface(value as PlaybackRenderSurface);
          setPickerModal(null);
        }}
        onClose={() => setPickerModal(null)}
        colors={colors}
        styles={styles}
      />

      <PickerModal
        visible={pickerModal === 'quality'}
        title="Preferred Stream Quality"
        subtitle="Used by automatic stream selection across devices."
        options={qualityPickerOptions}
        selectedValue={preferredQuality}
        resolvedAppearance={resolvedAppearance}
        onSelect={value => {
          void setPreferredQuality(value as typeof preferredQuality);
          setPickerModal(null);
        }}
        onClose={() => setPickerModal(null)}
        colors={colors}
        styles={styles}
      />

      <PickerModal
        visible={pickerModal === 'fileSize'}
        title="Max File Size"
        subtitle="Streams larger than this limit will be excluded from selection."
        options={fileSizePickerOptions}
        selectedValue={maxFileSizeGB}
        resolvedAppearance={resolvedAppearance}
        onSelect={value => {
          void setMaxFileSizeGB(value as number);
          setPickerModal(null);
        }}
        onClose={() => setPickerModal(null)}
        colors={colors}
        styles={styles}
      />

      <PickerModal
        visible={appearanceOpen}
        title={t('settings_appearance')}
        subtitle={t('settings_appearance_sub')}
        options={appearancePickerOptions}
        selectedValue={appearance}
        resolvedAppearance={resolvedAppearance}
        onSelect={value => {
          void setAppearance(value as any);
          setAppearanceOpen(false);
        }}
        onClose={() => setAppearanceOpen(false)}
        colors={colors}
        styles={styles}
      />

      <PickerModal
        visible={pickerModal === 'theme'}
        title={t('settings_theme')}
        subtitle="Choose the colour theme used across the app."
        options={themePickerOptions}
        selectedValue={theme.id}
        resolvedAppearance={resolvedAppearance}
        onSelect={value => {
          void setThemeId(value as any);
          setPickerModal(null);
        }}
        onClose={() => setPickerModal(null)}
        colors={colors}
        styles={styles}
        renderPreview={(option, active) => (
          <View style={styles.themePickerSwatchWrap}>
            <View
              style={[
                styles.themePickerSwatchOuter,
                active && { borderColor: getThemeAccentColor(String(option.value)) },
              ]}
            >
              <View
                style={[
                  styles.themePickerSwatchInner,
                  { backgroundColor: getThemeAccentColor(String(option.value)) },
                ]}
              />
            </View>
          </View>
        )}
      />

      <PickerModal
        visible={pickerModal === 'language'}
        title={t('settings_language')}
        subtitle="Choose the language used for the app interface."
        options={languagePickerOptions}
        selectedValue={language.code}
        resolvedAppearance={resolvedAppearance}
        onSelect={value => {
          setLanguage(value as any);
          setPickerModal(null);
        }}
        onClose={() => setPickerModal(null)}
        colors={colors}
        styles={styles}
      />

      <PickerModal
        visible={pageStyleOpen}
        title={t('settings_page_style')}
        subtitle={t('settings_page_style_sub')}
        options={pageStyleOptions}
        selectedValue={uiStyle}
        resolvedAppearance={resolvedAppearance}
        onSelect={value => {
          void setUiStyle(value as any);
          setPageStyleOpen(false);
        }}
        onClose={() => setPageStyleOpen(false)}
        colors={colors}
        styles={styles}
        renderPreview={option => <PageStyleWireframe value={option.value} styles={styles} />}
      />

      {/* Sticky header */}
      <View
        style={[styles.stickyHeader, { paddingTop: insets.top + 26 }]}
        onLayout={e => setHeaderHeight(e.nativeEvent.layout.height)}
      >
        <Text style={{ color: colors.textPrimary, fontSize: 34, fontWeight: '800', letterSpacing: -0.9 }}>{t('settings_title')}</Text>
      </View>

      {headerHeight > 0 && (
        <LinearGradient
          colors={[colors.bgHeader, 'transparent']}
          style={[styles.headerFade, { top: headerHeight }]}
          pointerEvents="none"
        />
      )}

      <ScrollView
        contentContainerStyle={[styles.content, { paddingTop: headerHeight + 8, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }]}
        showsVerticalScrollIndicator={false}
      >
        {/* ── Profiles ── */}
        {user && (
          <>
            <SectionHeader title="Profiles" c={colors} />
            <View style={styles.card}>
              {activeStreamProfile ? (
                <View style={styles.row}>
                  <Image
                    source={PROFILE_AVATARS[Math.min(activeStreamProfile.avatarIndex, PROFILE_AVATARS.length - 1)].image}
                    style={{ width: 40, height: 40, borderRadius: 20, marginRight: 2 }}
                  />
                  <View style={styles.rowInfo}>
                    <Text style={styles.rowLabel}>{activeStreamProfile.name}</Text>
                    <Text style={styles.rowSubtitle}>
                      {[
                        'Active profile',
                        activeStreamProfile.hasPinSet ? 'PIN locked' : null,
                      ].filter(Boolean).join(' · ')}
                    </Text>
                  </View>
                  <TouchableOpacity
                    style={styles.rowActionBtn}
                    onPress={() => {
                      void navigation.navigate('Main', { screen: 'Home' });
                      void clearActiveProfile();
                    }}
                    activeOpacity={0.8}
                  >
                    <Ionicons name="swap-horizontal-outline" size={16} color={colors.accentSoft} />
                    <Text style={styles.rowActionText}>Switch</Text>
                  </TouchableOpacity>
                </View>
              ) : (
                <View style={styles.row}>
                  <View style={[styles.rowIcon, { backgroundColor: colors.accent + '22' }]}>
                    <Ionicons name="person-circle-outline" size={20} color={colors.accent} />
                  </View>
                  <View style={styles.rowInfo}>
                    <Text style={styles.rowLabel}>No profile selected</Text>
                    <Text style={styles.rowSubtitle}>{profiles.length} profile{profiles.length !== 1 ? 's' : ''} available</Text>
                  </View>
                </View>
              )}
              <View style={styles.divider} />
              <NavRow
                icon="people-outline"
                label="Manage Profiles"
                subtitle={`${profiles.length} of ${MAX_PROFILES_PER_ACCOUNT} profile${profiles.length !== 1 ? 's' : ''} created`}
                iconColor={ic(colors.accent)}
                c={colors}
                onPress={() => navigation.navigate('ManageProfiles')}
              />
            </View>
          </>
        )}

        {/* ── Account and Services ── */}
        <SectionHeader title="Account And Services" c={colors} />
        <View style={styles.card}>
          {user ? (
            <>
              <View style={styles.row}>
                <View style={[styles.rowIcon, { backgroundColor: accountIconBackground }]}>
                  <View style={[styles.avatarCircle, isLightMode && { backgroundColor: '#111111' }]}>
                    <Text style={[styles.avatarLetter, isLightMode && { color: '#ffffff' }]}>{(user.email ?? '?')[0].toUpperCase()}</Text>
                  </View>
                </View>
                <View style={styles.rowInfo}>
                  <Text style={styles.rowLabel} numberOfLines={1}>{user.email}</Text>
                  <Text style={styles.rowHint}>Pulls configurations from your cloud account.</Text>
                </View>
                <TouchableOpacity
                  style={[styles.rowActionBtn, syncRefreshing && styles.rowActionBtnDisabled]}
                  onPress={() => { void handleRefreshSync(); }}
                  activeOpacity={0.8}
                  disabled={syncRefreshing}
                >
                  <Ionicons name="sync-outline" size={16} color={colors.accentSoft} />
                  <Text style={styles.rowActionText}>{syncRefreshing ? t('settings_refreshing') : 'Sync'}</Text>
                </TouchableOpacity>
              </View>
            </>
          ) : (
            <NavRow
              icon="person-circle-outline" label={t('settings_sign_in')}
              subtitle={t('settings_sign_in_sub')} iconColor={accountIconColor} c={colors}
              onPress={() => navigation.navigate('Auth')}
            />
          )}

          <View style={styles.divider} />
          <NavRow
            icon="extension-puzzle-outline"
            label={t('settings_addons')}
            subtitle={t('settings_addons_sub')}
            iconColor={ic('#00b4d8')}
            c={colors}
            onPress={() => navigation.navigate('Addons')}
          />

          {user && (
            <>
              {false ? (
                <>
                  <View style={styles.divider} />
                  <View style={{ paddingHorizontal: 16, paddingTop: 14, paddingBottom: 8 }}>
                    <Text style={styles.rowHint}>Linked TVs</Text>
                  </View>
                  {linkedTvDevices.map((device, index) => {
                    const lastSeen = device.lastSeenAt
                      ? new Date(device.lastSeenAt).toLocaleString()
                      : 'Recently linked';
                    const deviceName = device.name?.trim() || 'StreamDek TV';
                    const subtitle = device.isCurrent
                      ? `${deviceName} • This phone session`
                      : `${deviceName} • Last seen ${lastSeen}`;

                    return (
                      <React.Fragment key={device.id}>
                        <View style={styles.row}>
                          <View style={[styles.rowIcon, { backgroundColor: ic('#22c55e') + '22' }]}>
                            <Ionicons name="tv-outline" size={18} color={ic('#22c55e')} />
                          </View>
                          <View style={styles.rowInfo}>
                            <Text style={styles.rowLabel}>Linked TV</Text>
                            <Text style={styles.rowSubtitle}>{subtitle}</Text>
                          </View>
                          <TouchableOpacity
                            style={styles.rowActionBtn}
                            onPress={() => handleDisconnectDevice(device.id, deviceName)}
                            activeOpacity={0.8}
                          >
                            <Ionicons name="close-circle-outline" size={16} color="#c97070" />
                            <Text style={[styles.rowActionText, { color: '#c97070' }]}>Disconnect</Text>
                          </TouchableOpacity>
                        </View>
                        {index !== linkedTvDevices.length - 1 ? <View style={styles.divider} /> : null}
                      </React.Fragment>
                    );
                  })}
                </>
              ) : null}
              <View style={styles.divider} />
              <NavRow
                icon="film-outline"
                label={t('settings_trakt')}
                subtitle={isConnected ? `${t('settings_trakt_connected')} ${traktUsername ?? 'Trakt'}` : t('settings_trakt_connect')}
                iconColor={ic('#ed1c24')}
                c={colors}
                onPress={() => navigation.navigate('TraktSettings')}
              />
              <View style={styles.divider} />
              <NavRow
                icon="tv-outline"
                label="Link TV"
                subtitle={linkedTvDevices.length ? (
                  <View style={{ marginTop: 2 }}>
                    <Text style={{ color: colors.mutedText, fontSize: 12 }}>
                      Approve sign-in for a StreamDek TV device.
                    </Text>
                    <Text style={{ color: ic('#22c55e'), fontSize: 12, fontWeight: '800', marginTop: 3 }}>
                      {linkedTvDevices.length} linked {linkedTvDevices.length === 1 ? 'TV' : 'TVs'}
                    </Text>
                  </View>
                ) : 'Approve sign-in for a StreamDek TV device.'}
                iconColor={ic('#22c55e')}
                c={colors}
                onPress={() => navigation.navigate('LinkTv')}
              />
              <View style={styles.divider} />
              <View style={{ paddingHorizontal: 16, paddingVertical: 16 }}>
                <TouchableOpacity
                  style={{
                    width: '100%',
                    borderRadius: 999,
                    paddingVertical: 14,
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: '#c0392b',
                  }}
                  onPress={() => setSignOutModal(true)}
                  activeOpacity={0.8}
                >
                  <Text style={{ color: '#ffffff', fontSize: 14, fontWeight: '800' }}>
                    {t('settings_sign_out')}
                  </Text>
                </TouchableOpacity>
              </View>
            </>
          )}
        </View>

        {/* ── General ── */}
        {false && (
          <>
            <SectionHeader title={t('settings_general')} c={colors} />
            <View style={styles.card}>

              {/* Language — collapsible */}
              <View style={styles.serverHero}>
                <View style={styles.serverHeroTop}>
                  <View style={styles.serverHeroIcon}>
                    <Ionicons name="server-outline" size={20} color="#22c55e" />
                  </View>
                  <View style={styles.serverHeroInfo}>
                    <Text style={styles.serverHeroTitle}>{t('settings_local_server')}</Text>
                    <Text style={styles.serverHeroSub}>{t('settings_local_server_sub')}</Text>
                  </View>
                  <View style={[styles.statusPill, { backgroundColor: torrentStatusColors.backgroundColor, borderColor: torrentStatusColors.borderColor }]}>
                    <Text style={[styles.statusPillText, { color: torrentStatusColors.color }]}>
                      {!isServerMode
                        ? t('settings_server_off_short')
                        : (torrentStatus.isOnline ? t('settings_server_online_short') : t('settings_server_offline_short'))}
                    </Text>
                  </View>
                </View>
              </View>

              <View style={styles.dividerFull} />

              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_streaming_mode')}</Text>
                  <Text style={styles.optionSub}>
                    {isServerMode
                      ? t('settings_streaming_mode_server_sub')
                      : t('settings_streaming_mode_regular_sub')}
                  </Text>
                </View>
                <AppleToggle
                  value={isServerMode}
                  onValueChange={value => {
                    void updateTorrentConfig({
                      streamingMode: value ? 'server' : 'regular_http',
                      runAsForegroundService: value ? torrentConfig.runAsForegroundService : false,
                    });
                  }}
                  onColor={colors.toggleOn}
                />
              </View>

              <TouchableOpacity
                style={[styles.optionRow, serverSettingsDisabledStyle]}
                onPress={() => setPickerModal('profile')}
                activeOpacity={0.7}
                disabled={serverSettingsDisabled}
              >
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_torrent_profile')}</Text>
                  <Text style={styles.optionSub}>
                    {isServerMode ? t('settings_torrent_profile_sub') : t('settings_streaming_mode_regular_sub')}
                  </Text>
                </View>
                <Text style={styles.optionValue}>{activeProfile?.label ?? t('settings_default')}</Text>
                <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
              </TouchableOpacity>

              <TouchableOpacity
                style={[styles.optionRow, serverSettingsDisabledStyle]}
                onPress={() => setPickerModal('cache')}
                activeOpacity={0.7}
                disabled={serverSettingsDisabled}
              >
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_cache_size')}</Text>
                  <Text style={styles.optionSub}>{t('settings_cache_size_picker_sub')}</Text>
                </View>
                <Text style={styles.optionValue}>{activeCache?.label ?? t('settings_no_caching')}</Text>
                <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
              </TouchableOpacity>

              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_cache_usage')}</Text>
                  <Text style={styles.optionSub}>{t('settings_cache_usage_sub')}</Text>
                </View>
                <Text style={styles.optionValue}>{formatBytes(torrentStatus.cacheUsageBytes)}</Text>
              </View>

              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_cache_location')}</Text>
                  <Text style={styles.optionSub}>{t('settings_cache_location_sub')}</Text>
                </View>
              </View>
              {!!torrentStatus.torrentStoreDirectory && (
                <View style={{ paddingHorizontal: 16, paddingBottom: 14 }}>
                  <Text style={styles.inlineCode}>{torrentStatus.torrentStoreDirectory}</Text>
                </View>
              )}

              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_streaming_server_url')}</Text>
                  <Text style={styles.optionSub}>{t('settings_streaming_server_url_sub')}</Text>
                </View>
                <Text style={styles.inlineCode}>{torrentStatus.url}</Text>
              </View>

              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_recovery_mode')}</Text>
                  <Text style={styles.optionSub}>{t('settings_recovery_mode_sub')}</Text>
                </View>
                <Text style={styles.rowValue}>{torrentStatus.recoveryMode}</Text>
              </View>

              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_run_foreground')}</Text>
                  <Text style={styles.optionSub}>{foregroundFeedback ?? t('settings_run_foreground_sub')}</Text>
                </View>
                <AppleToggle
                  value={torrentConfig.runAsForegroundService}
                  onValueChange={value => { void handleForegroundToggle(value); }}
                  onColor={colors.toggleOn}
                />
              </View>

              <View style={styles.helperCard}>
                <Text style={styles.helperText}>
                  {torrentStatus.isOnline ? t('settings_local_server_sub') : t('settings_server_offline_help')}
                </Text>
                <Text style={styles.helperText}>{serverFeedback}</Text>
                <Text style={styles.helperText}>{`Debug Port: ${torrentStatus.port}`}</Text>
                <Text style={styles.helperText}>{`Lifecycle: ${torrentStatus.lifecycleState}`}</Text>
                {!!torrentStatus.cacheDirectory && (
                  <Text style={styles.helperText}>{`Cache Dir: ${torrentStatus.cacheDirectory}`}</Text>
                )}
                {!!torrentStatus.lastStartupError && (
                  <Text style={styles.helperText}>{`Last Error: ${torrentStatus.lastStartupError}`}</Text>
                )}
              </View>
            </View>

          </>
        )}
        <SectionHeader title="General" c={colors} />
        <View style={styles.card}>
          <TouchableOpacity style={styles.optionRow} onPress={() => setPickerModal('language')} activeOpacity={0.7}>
            <View style={[styles.rowIcon, { backgroundColor: ic('#9b5de5') + '22' }]}>
              <Ionicons name="language-outline" size={18} color={ic('#9b5de5')} />
            </View>
            <View style={styles.optionInfo}>
              <Text style={styles.optionTitle}>{t('settings_language')}</Text>
              <Text style={styles.optionSub}>
                {language.flag}  {language.name}
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
          </TouchableOpacity>

          <View style={styles.dividerFull} />

          <TouchableOpacity style={styles.optionRow} onPress={() => setAppearanceOpen(true)} activeOpacity={0.7}>
            <View style={[styles.rowIcon, { backgroundColor: appearanceIcon.backgroundColor }]}>
              <Ionicons name="moon-outline" size={18} color={appearanceIcon.iconColor} />
            </View>
            <View style={styles.optionInfo}>
              <Text style={styles.optionTitle}>{t('settings_appearance')}</Text>
              <Text style={styles.optionSub}>{t('settings_appearance_sub')}</Text>
            </View>
            <Text style={styles.optionValue}>
              {appearance === 'dark' ? t('settings_dark') : appearance === 'light' ? t('settings_light') : t('settings_system')}
            </Text>
            <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
          </TouchableOpacity>

          <View style={styles.dividerFull} />

          <TouchableOpacity style={styles.optionRow} onPress={() => setPickerModal('theme')} activeOpacity={0.7}>
            <View style={[styles.rowIcon, { backgroundColor: themeIcon.backgroundColor }]}>
              <Ionicons name="color-palette-outline" size={18} color={themeIcon.iconColor} />
            </View>
            <View style={styles.optionInfo}>
              <Text style={styles.optionTitle}>{t('settings_theme')}</Text>
              <Text style={styles.optionSub}>{theme.name}</Text>
            </View>
            <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
          </TouchableOpacity>

          <View style={styles.optionRow}>
            <View style={[styles.rowIcon, { backgroundColor: featureIcon('#6366f1').backgroundColor }]}>
              <Ionicons name="grid-outline" size={18} color={featureIcon('#6366f1').iconColor} />
            </View>
            <View style={styles.optionInfo}>
              <Text style={styles.optionTitle}>Show Navigation Labels</Text>
              <Text style={styles.optionSub}>Display tab names below the nav icons.</Text>
            </View>
            <AppleToggle
              value={showNavLabels}
              onValueChange={value => { void setShowNavLabels(value); }}
              disabled={!displaySettingsReady}
              onColor={colors.toggleOn}
            />
          </View>
        </View>

        <SectionHeader title="Home and Appearance" c={colors} />
        <View style={styles.card}>
          <NavRow
            icon="film-outline"
            label="Catalog & Metadata"
            subtitle={`${metadataProviderLabel} selected. ${metadataProviderSubtitle}`}
            iconColor={ic('#f59e0b')}
            c={colors}
            onPress={() => setTmdbModalOpen(true)}
          />

          <View style={styles.dividerFull} />

          <TouchableOpacity style={styles.collapseHeader} onPress={() => setLayoutOpen(v => !v)} activeOpacity={0.7}>
            <View style={[styles.rowIcon, { backgroundColor: ic('#f7b731') + '22' }]}>
              <Ionicons name="apps-outline" size={18} color={ic('#f7b731')} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.collapseLabel}>{t('settings_home_layout')}</Text>
              <Text style={{ color: colors.mutedText, fontSize: 12, marginTop: 1 }}>
                {t('settings_home_layout_sub')}
              </Text>
            </View>
            <Ionicons name={layoutOpen ? 'chevron-up' : 'chevron-down'} size={16} color={colors.placeholder} />
          </TouchableOpacity>

          {layoutOpen && (
            <>
              <View style={{ paddingHorizontal: 16, paddingTop: 2, paddingBottom: 6 }}>
                <Text style={styles.rowHint}>
                  {t('settings_home_layout_hint')}
                </Text>
              </View>
              {sections.map((section, index) => (
                <View key={section.id} style={styles.layoutRow}>
                  <View style={styles.layoutReorder}>
                    <TouchableOpacity onPress={() => moveUp(index)} style={[styles.arrowBtn, index === 0 && styles.disabledBtn]}>
                      <Ionicons name="chevron-up" size={14} color={colors.accentSoft} />
                    </TouchableOpacity>
                    <TouchableOpacity onPress={() => moveDown(index)} style={[styles.arrowBtn, index === sections.length - 1 && styles.disabledBtn]}>
                      <Ionicons name="chevron-down" size={14} color={colors.accentSoft} />
                    </TouchableOpacity>
                  </View>
                  <Text style={styles.layoutSectionTitle}>
                    {typeof SECTION_TITLE_KEY[section.id] === 'string' && SECTION_TITLE_KEY[section.id].startsWith('section_')
                      ? t(SECTION_TITLE_KEY[section.id])
                      : (SECTION_TITLE_KEY[section.id] ?? section.id)}
                  </Text>
                  <AppleToggle
                    value={section.enabled}
                    onValueChange={() => toggle(section.id)}
                    onColor={colors.toggleOn}
                  />
                </View>
              ))}
            </>
          )}

          <View style={styles.dividerFull} />

          <TouchableOpacity
            style={styles.row}
            onPress={() => setCwStyleOpen(true)}
            activeOpacity={0.7}
          >
            <View style={[styles.rowIcon, { backgroundColor: featureIcon('#22c55e').backgroundColor }]}>
              <Ionicons name="play-circle-outline" size={18} color={featureIcon('#22c55e').iconColor} />
            </View>
            <View style={styles.rowInfo}>
              <Text style={styles.rowLabel}>Continue Watching Style</Text>
              <Text style={styles.rowSubtitle}>
                {continueWatchingStyle === 'cinematic' ? 'Cinematic Backdrop' : continueWatchingStyle === 'glass' ? 'Glass Overlay' : continueWatchingStyle === 'ticket' ? 'Widescreen Ticket' : continueWatchingStyle === 'mini' ? 'Mini Player Row' : 'Tall Stacked'}
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
          </TouchableOpacity>

          <TouchableOpacity style={styles.optionRow} onPress={() => setPageStyleOpen(true)} activeOpacity={0.7}>
            <View style={[styles.rowIcon, { backgroundColor: ic('#22d3ee') + '22' }]}>
              <Ionicons name="albums-outline" size={18} color={ic('#22d3ee')} />
            </View>
            <View style={styles.optionInfo}>
              <Text style={styles.optionTitle}>{t('settings_page_style')}</Text>
              <Text style={styles.optionSub}>{t('settings_page_style_sub')}</Text>
            </View>
            <Text style={styles.optionValue}>
              {uiStyle === 'classic' ? t('settings_page_style_classic') : uiStyle === 'glass' ? 'Glassy Hero' : t('settings_page_style_centered')}
            </Text>
            <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
          </TouchableOpacity>

          <View style={styles.optionRow}>
            <View style={[styles.rowIcon, { backgroundColor: synopsisIcon.backgroundColor }]}>
              <Ionicons name="reader-outline" size={18} color={synopsisIcon.iconColor} />
            </View>
            <View style={styles.optionInfo}>
              <Text style={styles.optionTitle}>{t('settings_hero_synopsis')}</Text>
              <Text style={styles.optionSub}>{t('settings_hero_synopsis_sub')}</Text>
            </View>
            <AppleToggle
              value={showHeroSynopsis}
              onValueChange={value => { void setShowHeroSynopsis(value); }}
              onColor={colors.toggleOn}
            />
          </View>

          <View style={styles.optionRow}>
            <View style={[styles.rowIcon, { backgroundColor: featureIcon('#a78bfa').backgroundColor }]}>
              <Ionicons name="color-wand-outline" size={18} color={featureIcon('#a78bfa').iconColor} />
            </View>
            <View style={styles.optionInfo}>
              <Text style={styles.optionTitle}>Ambient Background</Text>
              <Text style={styles.optionSub}>Show a colourful ambient glow behind the home and detail screens. Applies to Classic and Centered Themes.</Text>
            </View>
            <AppleToggle
              value={vividAmbientEnabled}
              onValueChange={value => { void setVividAmbientEnabled(value); }}
              disabled={!displaySettingsReady}
              onColor={colors.toggleOn}
            />
          </View>
        </View>

        {/* ── Continue Watching style picker modal ── */}
        <Modal
          visible={cwStyleOpen}
          transparent
          animationType="slide"
          statusBarTranslucent
          onRequestClose={() => setCwStyleOpen(false)}
        >
          <Pressable
            style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'flex-end' }}
            onPress={() => setCwStyleOpen(false)}
          >
            <Pressable
              style={{
                backgroundColor: colors.cardBgElevated,
                borderTopLeftRadius: 24, borderTopRightRadius: 24,
                borderTopWidth: StyleSheet.hairlineWidth, borderLeftWidth: StyleSheet.hairlineWidth, borderRightWidth: StyleSheet.hairlineWidth,
                borderColor: colors.border,
                paddingTop: 10,
                paddingHorizontal: 20,
                paddingBottom: Math.max(insets.bottom, 16) + 12,
              }}
              onPress={() => {}}
            >
              {/* Handle */}
              <View style={{ width: 36, height: 4, borderRadius: 2, backgroundColor: colors.border, alignSelf: 'center', marginBottom: 18 }} />
              <Text style={{ color: colors.textPrimary, fontSize: 20, fontWeight: '700', letterSpacing: -0.4, marginBottom: 20 }}>
                Continue Watching Style
              </Text>

              {(
                [
                  { id: 'cinematic' as ContinueWatchingStyle, label: 'Cinematic Backdrop',  sub: 'Bold 16:9 image with gradient overlay and scrubber' },
                  { id: 'glass'    as ContinueWatchingStyle, label: 'Glass Overlay',        sub: 'Frosted glass panel reveals title and progress' },
                  { id: 'ticket'   as ContinueWatchingStyle, label: 'Widescreen Ticket',    sub: 'Compact wide card with blurred backdrop' },
                  { id: 'mini'     as ContinueWatchingStyle, label: 'Mini Player Row',      sub: 'Thumbnail left, details right — compact and scannable' },
                  { id: 'stacked'  as ContinueWatchingStyle, label: 'Tall Stacked',         sub: 'Portrait card with backdrop and info below' },
                ] as const
              ).map(option => {
                const active = continueWatchingStyle === option.id;
                return (
                  <TouchableOpacity
                    key={option.id}
                    onPress={() => { void setContinueWatchingStyle(option.id); setCwStyleOpen(false); }}
                    activeOpacity={0.82}
                    style={{
                      flexDirection: 'row', alignItems: 'center', gap: 16,
                      paddingVertical: 14,
                      borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.border,
                    }}
                  >
                    {/* Skeletal preview thumbnail */}
                    <View style={{ borderRadius: 8, overflow: 'hidden' }}>
                      {option.id === 'cinematic' && <CinematicSkeleton selected={active} colors={colors} />}
                      {option.id === 'glass'     && <GlassSkeleton     selected={active} colors={colors} />}
                      {option.id === 'ticket'    && <TicketSkeleton     selected={active} colors={colors} />}
                      {option.id === 'mini'      && <MiniSkeleton       selected={active} colors={colors} />}
                      {option.id === 'stacked'   && <StackedSkeleton    selected={active} colors={colors} />}
                    </View>

                    {/* Label + sub */}
                    <View style={{ flex: 1 }}>
                      <Text style={{ color: active ? colors.accentSoft : colors.textPrimary, fontSize: 15, fontWeight: active ? '700' : '600' }}>
                        {option.label}
                      </Text>
                      <Text style={{ color: colors.mutedText, fontSize: 12, marginTop: 2, lineHeight: 17 }}>
                        {option.sub}
                      </Text>
                    </View>

                    {active && <Ionicons name="checkmark-circle" size={22} color={colors.toggleOn} />}
                  </TouchableOpacity>
                );
              })}
            </Pressable>
          </Pressable>
        </Modal>

        {/* ── Integrations ── */}
        {false && <SectionHeader title="Integrations" c={colors} />}
        {false && <View style={styles.card}>
          <View style={styles.optionRow}>
            <View style={[styles.rowIcon, { backgroundColor: ic('#f59e0b') + '22' }]}>
              <Ionicons name="film-outline" size={18} color={ic('#f59e0b')} />
            </View>
            <View style={styles.optionInfo}>
              <Text style={styles.optionTitle}>Custom TMDB API Key</Text>
              <Text style={styles.optionSub}>
                Fetch artwork, trailers, credits and all metadata directly from TMDB using your own key.
              </Text>
            </View>
            <AppleToggle
              value={tmdbKeyEnabled}
              onValueChange={value => { void setTmdbKeyEnabled(value); }}
              onColor={colors.toggleOn}
            />
          </View>

          {tmdbKeyEnabled && (
            <>
              <View style={styles.dividerFull} />
              <View style={{ paddingHorizontal: 18, paddingVertical: 14, gap: 10 }}>
                <Text style={[styles.optionTitle, { fontSize: 14 }]}>TMDB API Key (v3 Auth)</Text>
                <View style={{ flexDirection: 'row', gap: 10, alignItems: 'center' }}>
                  <TextInput
                    value={tmdbKeyInput}
                    onChangeText={setTmdbKeyInput}
                    placeholder="Paste your TMDB API key here"
                    placeholderTextColor={colors.placeholder}
                    autoCapitalize="none"
                    autoCorrect={false}
                    returnKeyType="done"
                    style={[styles.inlineCode, {
                      flex: 1,
                      fontSize: 13,
                      paddingVertical: 10,
                      paddingHorizontal: 12,
                      borderRadius: 10,
                      color: colors.textPrimary,
                      minHeight: 44,
                    }]}
                  />
                  <TouchableOpacity
                    style={[
                      styles.rowActionBtn,
                      {
                        minHeight: 44,
                        paddingHorizontal: 16,
                        backgroundColor: resolvedAppearance === 'light' && theme.id === 'monochrome'
                          ? colors.accent
                          : colors.cardBgElevated,
                        borderColor: resolvedAppearance === 'light' && theme.id === 'monochrome'
                          ? 'rgba(17,24,39,0.18)'
                          : colors.border,
                      },
                    ]}
                    onPress={() => { void setTmdbApiKey(tmdbKeyInput.trim()); }}
                    activeOpacity={0.8}
                  >
                    <Text style={[styles.rowActionText, { color: resolvedAppearance === 'light' && theme.id === 'monochrome' ? '#111111' : colors.accentSoft }]}>{t('settings_save')}</Text>
                  </TouchableOpacity>
                </View>
                <Text style={styles.rowHint}>
                  Get a free API key at themoviedb.org/settings/api. When active, all enrichment data (trailers, artwork, credits, seasons, networks and more) is fetched directly from TMDB using your key.
                </Text>
              </View>
            </>
          )}
        </View>}

        {/* ── Watch Experience ── */}
        <SectionHeader title="Playback" c={colors} />

        {/* Player — collapsible */}
        <View style={styles.card}>
          <TouchableOpacity style={styles.collapseHeader} onPress={() => setPlayerOpen(v => !v)} activeOpacity={0.7}>
            <View style={[styles.rowIcon, { backgroundColor: ic('#6366f1') + '22' }]}>
              <Ionicons name="play-circle-outline" size={18} color={ic('#6366f1')} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.collapseLabel}>{t('settings_player')}</Text>
              <Text style={{ color: colors.mutedText, fontSize: 12, marginTop: 1 }}>
                {t('settings_player_sub')}
              </Text>
            </View>
            <Ionicons name={playerOpen ? 'chevron-up' : 'chevron-down'} size={16} color={colors.placeholder} />
          </TouchableOpacity>

          {playerOpen && (
            <>
              <View style={styles.dividerFull} />
              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_picture_in_picture')}</Text>
                  <Text style={styles.optionSub}>{t('settings_picture_in_picture_sub')}</Text>
                </View>
                <AppleToggle
                  value={pictureInPictureEnabled}
                  onValueChange={value => { void setPictureInPictureEnabled(value); }}
                  disabled={!displaySettingsReady}
                  onColor={colors.toggleOn}
                />
              </View>

              <View style={styles.dividerFull} />
              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>Show Streams List</Text>
                  <Text style={styles.optionSub}>Show the streams tab on media pages. Turn off to auto-select the best stream.</Text>
                </View>
                <AppleToggle
                  value={showStreamsList}
                  onValueChange={value => { void setShowStreamsList(value); }}
                  disabled={!displaySettingsReady}
                  onColor={colors.toggleOn}
                />
              </View>

              <View style={styles.dividerFull} />

              <TouchableOpacity style={styles.optionRow} onPress={() => setPickerModal('decoder')} activeOpacity={0.7}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_decoder_mode')}</Text>
                  <Text style={styles.optionSub}>
                    {t('settings_decoder_mode_sub')}
                  </Text>
                </View>
                <Text style={styles.optionValue}>{activeDecoderLabel}</Text>
                <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
              </TouchableOpacity>

              <View style={styles.dividerFull} />

              <TouchableOpacity style={styles.optionRow} onPress={() => setPickerModal('surface')} activeOpacity={0.7}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_render_surface')}</Text>
                  <Text style={styles.optionSub}>
                    {t('settings_render_surface_sub')}
                  </Text>
                </View>
                <Text style={styles.optionValue}>{activeSurfaceLabel}</Text>
                <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
              </TouchableOpacity>

              <View style={styles.dividerFull} />

              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_stream_selection_logic')}</Text>
                  <Text style={styles.optionSub}>
                    {t('settings_stream_selection_logic_sub')}
                  </Text>
                </View>
                <AppleToggle
                  value={streamSelectionEnabled}
                  onValueChange={value => { void setStreamSelectionEnabled(value); }}
                  disabled={!streamSelectionReady}
                  onColor={colors.toggleOn}
                />
              </View>

              <View style={styles.dividerFull} />

              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_short_source_filter')}</Text>
                  <Text style={styles.optionSub}>
                    {t('settings_short_source_filter_sub')}
                  </Text>
                </View>
                <AppleToggle
                  value={shortSourceFilterEnabled}
                  onValueChange={value => { void setShortSourceFilterEnabled(value); }}
                  disabled={!streamSelectionReady}
                  onColor={colors.toggleOn}
                />
              </View>

              <View style={styles.dividerFull} />

              <TouchableOpacity
                style={styles.optionRow}
                onPress={() => setPickerModal('quality')}
                activeOpacity={0.7}
                disabled={!streamSelectionReady}
              >
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>Preferred Stream Quality</Text>
                  <Text style={styles.optionSub}>
                    Carries over from web sync and biases automatic stream selection.
                  </Text>
                </View>
                <Text style={styles.optionValue}>
                  {qualityPickerOptions.find(option => option.value === preferredQuality)?.label ?? 'Best Available'}
                </Text>
                <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.optionRow}
                onPress={() => setPickerModal('fileSize')}
                activeOpacity={0.7}
                disabled={!streamSelectionReady}
              >
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>Max File Size</Text>
                  <Text style={styles.optionSub}>
                    Exclude streams larger than this size. Unlimited means no cap.
                  </Text>
                </View>
                <Text style={styles.optionValue}>
                  {maxFileSizeGB === 0 ? 'Unlimited' : `${maxFileSizeGB} GB`}
                </Text>
                <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
              </TouchableOpacity>
            </>
          )}
          <View style={styles.dividerFull} />

          {/* Subtitles — collapsible */}
          <TouchableOpacity
            style={styles.collapseHeader}
            onPress={() => {
              setSubtitleOpen(v => !v);
              if (!subtitleOpen && subtitleCacheSize === null) {
                void getFileCacheSize().then(size => setSubtitleCacheSize(size));
              }
            }}
            activeOpacity={0.7}
          >
            <View style={[styles.rowIcon, { backgroundColor: ic('#8b5cf6') + '22' }]}>
              <Ionicons name="text" size={18} color={ic('#8b5cf6')} />
            </View>
            <Text style={styles.collapseLabel}>{t('settings_subtitles_via_stremio')}</Text>
            <Ionicons
              name={subtitleOpen ? 'chevron-up' : 'chevron-down'}
              size={16}
              color={colors.mutedText}
            />
          </TouchableOpacity>

          {subtitleOpen && (
            <>
              <View style={styles.dividerFull} />

              {/* Auto-load */}
              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_auto_load_subtitles')}</Text>
                  <Text style={styles.optionSub}>
                    {t('settings_auto_load_subtitles_sub')}
                  </Text>
                </View>
                <AppleToggle
                  value={autoLoadEnabled}
                  onValueChange={value => { void setAutoLoadEnabled(value); }}
                  disabled={!subtitleSettingsReady}
                  onColor={colors.toggleOn}
                />
              </View>

              <View style={styles.dividerFull} />

              {/* Language priority */}
              <TouchableOpacity
                style={styles.optionRow}
                onPress={() => setSubtitleLangOpen(v => !v)}
                activeOpacity={0.7}
              >
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_language_priority')}</Text>
                  <Text style={styles.optionSub}>
                    {languageOrder.length > 0
                      ? languageOrder
                        .map(code => COMMON_SUBTITLE_LANGUAGES.find(l => l.code === code)?.label ?? code.toUpperCase())
                        .join(' › ')
                      : t('settings_none_selected_manual')}
                  </Text>
                </View>
                <Ionicons name={subtitleLangOpen ? 'chevron-up' : 'chevron-down'} size={16} color={colors.placeholder} />
              </TouchableOpacity>

              {subtitleLangOpen && (
                <View style={{ paddingHorizontal: 16, paddingBottom: 12, gap: 8 }}>
                  <Text style={[styles.rowHint, { paddingHorizontal: 0, marginTop: 4 }]}>
                    {t('settings_language_priority_hint')}
                  </Text>
                  {COMMON_SUBTITLE_LANGUAGES.map(lang => {
                    const idx = languageOrder.indexOf(lang.code as SubtitleLanguageCode);
                    const active = idx >= 0;
                    return (
                      <TouchableOpacity
                        key={lang.code}
                        style={{
                          flexDirection: 'row',
                          alignItems: 'center',
                          gap: 10,
                          paddingVertical: 8,
                          paddingHorizontal: 12,
                          borderRadius: 10,
                          backgroundColor: active ? colors.accent + '22' : colors.inputBg,
                          borderWidth: 1,
                          borderColor: active ? colors.accent + '55' : colors.border,
                        }}
                        onPress={() => {
                          const next = active
                            ? languageOrder.filter(c => c !== lang.code)
                            : [...languageOrder, lang.code as SubtitleLanguageCode];
                          void setLanguageOrder(next);
                        }}
                        activeOpacity={0.75}
                      >
                        <Text style={{ flex: 1, color: active ? '#e9ddff' : colors.mutedText, fontSize: 14, fontWeight: '600' }}>
                          {lang.label}
                        </Text>
                        {active && (
                          <Text style={{ color: colors.accentSoft, fontSize: 12, fontWeight: '700' }}>
                            #{idx + 1}
                          </Text>
                        )}
                        <Ionicons
                          name={active ? 'checkmark-circle' : 'add-circle-outline'}
                          size={20}
                          color={active ? colors.accent : colors.placeholder}
                        />
                      </TouchableOpacity>
                    );
                  })}
                </View>
              )}

              <View style={styles.dividerFull} />

              {/* Hearing Impaired */}
              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_prefer_hi_subtitles')}</Text>
                  <Text style={styles.optionSub}>
                    {t('settings_prefer_hi_subtitles_sub')}
                  </Text>
                </View>
                <AppleToggle
                  value={preferHI}
                  onValueChange={value => { void setPreferHI(value); }}
                  disabled={!subtitleSettingsReady}
                  onColor={colors.toggleOn}
                />
              </View>

              <View style={styles.dividerFull} />

              {/* Forced */}
              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_prefer_forced_subtitles')}</Text>
                  <Text style={styles.optionSub}>
                    {t('settings_prefer_forced_subtitles_sub')}
                  </Text>
                </View>
                <AppleToggle
                  value={preferForced}
                  onValueChange={value => { void setPreferForced(value); }}
                  disabled={!subtitleSettingsReady}
                  onColor={colors.toggleOn}
                />
              </View>

              <View style={styles.dividerFull} />

              {/* Cache management */}
              <View style={styles.optionRow}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_subtitle_file_cache')}</Text>
                  <Text style={styles.optionSub}>
                    {subtitleCacheSize !== null
                      ? `${(subtitleCacheSize / 1024).toFixed(0)} KB used — ${t('settings_subtitle_file_cache_sub')}`
                      : t('settings_subtitle_file_cache_sub')}
                  </Text>
                </View>
                <TouchableOpacity
                  style={styles.rowActionBtn}
                  onPress={async () => {
                    await clearSubtitleFileCache();
                    setSubtitleCacheSize(0);
                  }}
                  activeOpacity={0.8}
                >
                  <Ionicons name="trash-outline" size={14} color={colors.accentSoft} />
                  <Text style={styles.rowActionText}>{t('settings_clear')}</Text>
                </TouchableOpacity>
              </View>

              <View style={styles.dividerFull} />

              {/* OpenSubtitles addon URL */}
              <View style={{ paddingHorizontal: 16, paddingVertical: 14, gap: 8 }}>
                <Text style={styles.optionTitle}>{t('settings_opensubtitles_addon_url')}</Text>
                <Text style={styles.optionSub}>
                  {t('settings_opensubtitles_addon_url_sub')}
                </Text>
                <View style={{ flexDirection: 'row', gap: 8, marginTop: 4 }}>
                  <TextInput
                    style={{
                      flex: 1,
                      backgroundColor: colors.inputBg,
                      borderWidth: 1,
                      borderColor: colors.border,
                      borderRadius: 10,
                      paddingHorizontal: 12,
                      paddingVertical: 10,
                      color: colors.subText,
                      fontSize: 12,
                      fontFamily: 'monospace',
                    }}
                    value={addonUrlDraft}
                    onChangeText={setAddonUrlDraft}
                    autoCapitalize="none"
                    autoCorrect={false}
                    keyboardType="url"
                    placeholder={t('settings_opensubtitles_addon_url_placeholder')}
                    placeholderTextColor={colors.placeholder}
                    returnKeyType="done"
                    onSubmitEditing={() => { void setAddonUrl(addonUrlDraft); }}
                  />
                  <TouchableOpacity
                    style={[styles.rowActionBtn, { alignSelf: 'center' }]}
                    onPress={() => { void setAddonUrl(addonUrlDraft); }}
                    activeOpacity={0.8}
                  >
                    <Text style={styles.rowActionText}>{t('settings_save')}</Text>
                  </TouchableOpacity>
                </View>
                {addonUrl !== DEFAULT_OS_ADDON_URL && (
                  <TouchableOpacity
                    onPress={() => {
                      setAddonUrlDraft(DEFAULT_OS_ADDON_URL);
                      void setAddonUrl(DEFAULT_OS_ADDON_URL);
                    }}
                    activeOpacity={0.7}
                  >
                    <Text style={{ color: colors.mutedText, fontSize: 12, marginTop: 2 }}>
                      {t('settings_reset_to_default')}
                    </Text>
                  </TouchableOpacity>
                )}
              </View>
            </>
          )}
        </View>

        <SectionHeader title={t('settings_streaming_mode')} c={colors} />
        <View style={styles.card}>
          <View style={styles.serverHero}>
            <View style={styles.serverHeroTop}>
              <View style={[styles.serverHeroIcon, { backgroundColor: isServerMode ? '#22c55e22' : '#64748b22' }]}>
                <Ionicons name={isServerMode ? 'server-outline' : 'globe-outline'} size={20} color={isServerMode ? '#22c55e' : '#94a3b8'} />
              </View>
              <View style={styles.serverHeroInfo}>
                <Text style={styles.serverHeroTitle}>{t('settings_streaming_mode')}</Text>
                <Text style={styles.serverHeroSub}>
                  {isServerMode
                    ? t('settings_streaming_mode_server_sub')
                    : t('settings_streaming_mode_regular_sub')}
                </Text>
              </View>
              <AppleToggle
                value={isServerMode}
                onValueChange={value => {
                  void updateTorrentConfig({
                    streamingMode: value ? 'server' : 'regular_http',
                    runAsForegroundService: value ? torrentConfig.runAsForegroundService : false,
                  });
                }}
                onColor={colors.toggleOn}
              />
            </View>
          </View>

          <View style={styles.dividerFull} />

          <View style={styles.helperCard}>
            <Text style={styles.helperText}>
              {isServerMode
                ? t('settings_streaming_mode_server_sub')
                : t('settings_streaming_mode_regular_sub')}
            </Text>
            <Text style={styles.helperText}>{serverFeedback}</Text>
          </View>
        </View>

        {isServerMode && (
          <>
            <SectionHeader title={t('settings_local_server_section')} c={colors} />
            <View style={styles.card}>
              <View style={styles.serverHero}>
                <View style={styles.serverHeroTop}>
                  <View style={styles.serverHeroIcon}>
                    <Ionicons name="server-outline" size={20} color="#22c55e" />
                  </View>
                  <View style={styles.serverHeroInfo}>
                    <Text style={styles.serverHeroTitle}>{t('settings_local_server')}</Text>
                    <Text style={styles.serverHeroSub}>{t('settings_local_server_sub')}</Text>
                  </View>
                  <View style={[styles.statusPill, { backgroundColor: torrentStatusColors.backgroundColor, borderColor: torrentStatusColors.borderColor }]}>
                    <Text style={[styles.statusPillText, { color: torrentStatusColors.color }]}>
                      {!isServerMode
                        ? t('settings_server_off_short')
                        : (torrentStatus.isOnline ? t('settings_server_online_short') : t('settings_server_offline_short'))}
                    </Text>
                  </View>
                </View>
              </View>

              <View style={styles.dividerFull} />

              <TouchableOpacity style={styles.optionRow} onPress={() => setPickerModal('profile')} activeOpacity={0.7}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_torrent_profile')}</Text>
                  <Text style={styles.optionSub}>
                    {isServerMode ? t('settings_torrent_profile_sub') : t('settings_streaming_mode_regular_sub')}
                  </Text>
                </View>
                <Text style={styles.optionValue}>{activeProfile?.label ?? t('settings_default')}</Text>
                <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
              </TouchableOpacity>

              <TouchableOpacity style={styles.optionRow} onPress={() => setPickerModal('cache')} activeOpacity={0.7}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_cache_size')}</Text>
                  <Text style={styles.optionSub}>{t('settings_cache_size_picker_sub')}</Text>
                </View>
                <Text style={styles.optionValue}>{activeCache?.label ?? t('settings_no_caching')}</Text>
                <Ionicons name="chevron-forward" size={16} color={colors.placeholder} />
              </TouchableOpacity>

              <View style={[styles.optionRow, serverSettingsDisabledStyle]}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_cache_usage')}</Text>
                  <Text style={styles.optionSub}>{t('settings_cache_usage_sub')}</Text>
                </View>
                <Text style={styles.optionValue}>{formatBytes(torrentStatus.cacheUsageBytes)}</Text>
              </View>

              <View style={[styles.optionRow, serverSettingsDisabledStyle]}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_cache_location')}</Text>
                  <Text style={styles.optionSub}>{t('settings_cache_location_sub')}</Text>
                </View>
              </View>
              {!!torrentStatus.torrentStoreDirectory && (
                <View style={[{ paddingHorizontal: 16, paddingBottom: 8 }, serverSettingsDisabledStyle]}>
                  <Text style={styles.inlineCode}>{torrentStatus.torrentStoreDirectory}</Text>
                </View>
              )}
              {!!torrentStatus.cacheDirectory && (
                <View style={[{ paddingHorizontal: 16, paddingBottom: 14 }, serverSettingsDisabledStyle]}>
                  <Text style={styles.inlineCode}>{torrentStatus.cacheDirectory}</Text>
                </View>
              )}

              <View style={[styles.optionRow, serverSettingsDisabledStyle]}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_streaming_server_url')}</Text>
                  <Text style={styles.optionSub}>
                    {isServerMode ? t('settings_streaming_server_url_sub') : t('settings_streaming_mode_regular_sub')}
                  </Text>
                </View>
                <Text style={styles.inlineCode}>{torrentStatus.url}</Text>
              </View>

              <View style={[styles.optionRow, serverSettingsDisabledStyle]}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_recovery_mode')}</Text>
                  <Text style={styles.optionSub}>{t('settings_recovery_mode_sub')}</Text>
                </View>
                <Text style={styles.rowValue}>{torrentStatus.recoveryMode}</Text>
              </View>

              <View style={[styles.optionRow, serverSettingsDisabledStyle]}>
                <View style={styles.optionInfo}>
                  <Text style={styles.optionTitle}>{t('settings_run_foreground')}</Text>
                  <Text style={styles.optionSub}>
                    {isServerMode
                      ? (foregroundFeedback ?? t('settings_run_foreground_sub'))
                      : t('settings_streaming_mode_regular_sub')}
                  </Text>
                </View>
                <AppleToggle
                  value={isServerMode && torrentConfig.runAsForegroundService}
                  onValueChange={value => { void handleForegroundToggle(value); }}
                  disabled={!isServerMode}
                  onColor={colors.toggleOn}
                />
              </View>

              <View style={[styles.helperCard, serverSettingsDisabledStyle]}>
                <Text style={styles.optionTitle}>{t('settings_server_debug_title')}</Text>
                <Text style={styles.helperText}>{t('settings_server_debug_sub')}</Text>
                <Text style={styles.helperText}>{serverFeedback}</Text>
                <Text style={styles.helperText}>{`Local URL: ${torrentStatus.url}`}</Text>
                <Text style={styles.helperText}>{`Port: ${torrentStatus.port}`}</Text>
                <Text style={styles.helperText}>{`Lifecycle: ${torrentStatus.lifecycleState}`}</Text>
                <Text style={styles.helperText}>{`Recovery: ${torrentStatus.recoveryMode}`}</Text>
                <Text style={styles.helperText}>{`Foreground Requested: ${torrentStatus.requestedForeground ? 'yes' : 'no'}`}</Text>
                <Text style={styles.helperText}>{`Foreground Active: ${torrentStatus.isForeground ? 'yes' : 'no'}`}</Text>
                {!!torrentStatus.foregroundDowngradeReason && (
                  <Text style={styles.helperText}>{`Foreground Note: ${torrentStatus.foregroundDowngradeReason}`}</Text>
                )}
                <Text style={styles.helperText}>{`Usage: ${formatBytes(torrentStatus.cacheUsageBytes)}`}</Text>
                {!!torrentStatus.torrentStoreDirectory && (
                  <Text style={styles.helperText}>{`Torrent Store: ${torrentStatus.torrentStoreDirectory}`}</Text>
                )}
                {!!torrentStatus.cacheDirectory && (
                  <Text style={styles.helperText}>{`Proxy Cache: ${torrentStatus.cacheDirectory}`}</Text>
                )}
                {!!torrentStatus.lastStartupError && (
                  <Text style={styles.helperText}>{`Last Error: ${torrentStatus.lastStartupError}`}</Text>
                )}
              </View>
            </View>
          </>
        )}

        <SectionHeader title="About" c={colors} />
        <View style={styles.card}>
          <InfoRow icon="information-circle-outline" label={t('settings_version')} value={APP_VERSION} iconColor={colors.mutedText} c={colors} />
          <View style={styles.divider} />
          <NavRow icon="document-text-outline" label={t('settings_legal')} iconColor={colors.mutedText} c={colors} onPress={() => setLegalModal(true)} />
        </View>
      </ScrollView>

      {syncNotice && (
        <View pointerEvents="box-none" style={[styles.toastWrap, { bottom: BOTTOM_NAV_HEIGHT + insets.bottom + 12 }]}>
          <View
            style={[
              styles.toastCard,
              resolvedAppearance === 'light'
                ? {
                  backgroundColor: colors.cardBg,
                  borderColor: syncNotice.tone === 'success' ? 'rgba(34,197,94,0.32)' : 'rgba(239,68,68,0.32)',
                  shadowOpacity: 0.10,
                  shadowRadius: 10,
                  shadowOffset: { width: 0, height: 4 },
                }
                : syncNotice.tone === 'success'
                  ? { backgroundColor: '#10261d', borderColor: '#2a7c55' }
                  : { backgroundColor: '#2a1618', borderColor: '#7f3b43' },
            ]}
          >
            <Ionicons
              name={syncNotice.tone === 'success' ? 'checkmark-circle' : 'alert-circle'}
              size={20}
              color={resolvedAppearance === 'light'
                ? (syncNotice.tone === 'success' ? '#166534' : '#b91c1c')
                : (syncNotice.tone === 'success' ? '#57e393' : '#ff8f9a')}
            />
            <View style={styles.toastInfo}>
              <Text style={styles.toastTitle}>{syncNotice.title}</Text>
              <Text style={styles.toastText}>{syncNotice.text}</Text>
            </View>
            <TouchableOpacity style={styles.toastClose} onPress={() => setSyncNotice(null)} activeOpacity={0.8}>
              <Ionicons name="close" size={16} color={resolvedAppearance === 'light' ? colors.textPrimary : '#c8d2dd'} />
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* ── Legal Modal ──────────────────────────────────────────────────── */}
      <Modal visible={tmdbModalOpen} transparent animationType="slide" statusBarTranslucent onRequestClose={() => setTmdbModalOpen(false)}>
        <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.7)', justifyContent: 'flex-end' }}>
          <View style={{ backgroundColor: colors.cardBg, borderTopLeftRadius: 24, borderTopRightRadius: 24, paddingBottom: insets.bottom + 16, borderTopWidth: 1, borderColor: colors.border }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingTop: 20, paddingBottom: 16 }}>
              <Ionicons name="film-outline" size={22} color={ic('#f59e0b')} style={{ marginRight: 10 }} />
              <Text style={{ flex: 1, color: colors.textPrimary, fontSize: 18, fontWeight: '800' }}>Catalog & Metadata</Text>
              <TouchableOpacity onPress={() => setTmdbModalOpen(false)} activeOpacity={0.75}
                style={{ width: 32, height: 32, borderRadius: 16, backgroundColor: colors.border, justifyContent: 'center', alignItems: 'center' }}>
                <Ionicons name="close" size={18} color={colors.textPrimary} />
              </TouchableOpacity>
            </View>
            <ScrollView style={{ maxHeight: 420 }} contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 8 }} showsVerticalScrollIndicator={false}>
              <View style={[styles.card, { marginBottom: 0 }]}>
                <View style={{ paddingHorizontal: 18, paddingTop: 18, paddingBottom: 8, gap: 10 }}>
                  <Text style={[styles.optionTitle, { fontSize: 14 }]}>Choose your catalog source</Text>
                  <View style={{ gap: 10 }}>
                    <TouchableOpacity
                      activeOpacity={0.82}
                      onPress={() => { void handleMetadataProviderSelect('cinemeta'); }}
                      style={[
                        styles.pickerOption,
                        metadataProvider === 'cinemeta' && { borderColor: colors.toggleOn, backgroundColor: colors.accent + '14' },
                      ]}
                    >
                      <View style={styles.pickerOptionTextWrap}>
                        <Text style={styles.pickerOptionTitle}>Cinemeta</Text>
                        <Text style={styles.pickerOptionSub}>Built-in catalog with no sign-in and no API key required.</Text>
                      </View>
                      {metadataProvider === 'cinemeta' ? (
                        <Ionicons name="checkmark-circle" size={20} color={colors.toggleOn} />
                      ) : (
                        <Ionicons name="chevron-forward" size={18} color={colors.placeholder} />
                      )}
                    </TouchableOpacity>
                    <TouchableOpacity
                      activeOpacity={0.82}
                      onPress={() => { void handleMetadataProviderSelect('tmdb'); }}
                      style={[
                        styles.pickerOption,
                        metadataProvider === 'tmdb' && { borderColor: colors.toggleOn, backgroundColor: colors.accent + '14' },
                      ]}
                    >
                      <View style={styles.pickerOptionTextWrap}>
                        <Text style={styles.pickerOptionTitle}>StreamDek Catalog</Text>
                        <Text style={styles.pickerOptionSub}>
                          {user
                            ? 'Richer catalog powered by your TMDB key. Saved on device and synced to your account.'
                            : 'Richer catalog powered by your TMDB key. Saved on this device until you sign in.'}
                        </Text>
                      </View>
                      {metadataProvider === 'tmdb' ? (
                        <Ionicons name="checkmark-circle" size={20} color={colors.toggleOn} />
                      ) : (
                        <Ionicons name="chevron-forward" size={18} color={colors.placeholder} />
                      )}
                    </TouchableOpacity>
                  </View>
                </View>
                {metadataProvider === 'tmdb' ? (
                  <>
                    <View style={styles.dividerFull} />
                    <View style={styles.optionRow}>
                      <View style={styles.optionInfo}>
                        <Text style={styles.optionTitle}>{tmdbStorageTitle}</Text>
                        <Text style={styles.optionSub}>{tmdbStorageSubtitle}</Text>
                      </View>
                      <AppleToggle
                        value={tmdbKeyEnabled}
                        onValueChange={value => { void setTmdbKeyEnabled(value); }}
                        onColor={colors.toggleOn}
                      />
                    </View>
                    <View style={styles.dividerFull} />
                    <View style={{ paddingHorizontal: 18, paddingVertical: 14, gap: 10 }}>
                      <Text style={[styles.optionTitle, { fontSize: 14 }]}>TMDB API Key (v3 Auth)</Text>
                      {tmdbKeyRequired ? (
                        <View style={{ borderRadius: 12, borderWidth: 1, borderColor: '#f59e0b55', backgroundColor: '#f59e0b14', paddingHorizontal: 12, paddingVertical: 10 }}>
                          <Text style={{ color: ic('#f59e0b'), fontSize: 12, fontWeight: '700', marginBottom: 2 }}>
                            TMDB needs your API key
                          </Text>
                          <Text style={{ color: colors.textSecondary, fontSize: 12, lineHeight: 18 }}>
                            Paste a TMDB v3 API key below to finish switching from Cinemeta to TMDB.
                          </Text>
                        </View>
                      ) : null}
                      <TextInput
                        value={tmdbKeyInput}
                        onChangeText={setTmdbKeyInput}
                        placeholder="Paste your TMDB API key here"
                        placeholderTextColor={colors.placeholder}
                        autoCapitalize="none"
                        autoCorrect={false}
                        returnKeyType="done"
                        style={[styles.inlineCode, {
                          fontSize: 13,
                          paddingVertical: 12,
                          paddingHorizontal: 12,
                          borderRadius: 12,
                          color: colors.textPrimary,
                          minHeight: 46,
                        }]}
                      />
                      <TouchableOpacity
                        style={[
                          styles.rowActionBtn,
                          {
                            minHeight: 46,
                            justifyContent: 'center',
                            alignSelf: 'stretch',
                            backgroundColor: resolvedAppearance === 'light' && theme.id === 'monochrome'
                              ? colors.accent
                              : (resolvedAppearance === 'light' ? '#16a34a' : '#22c55e'),
                            borderColor: resolvedAppearance === 'light' && theme.id === 'monochrome'
                              ? 'rgba(17,24,39,0.18)'
                              : (resolvedAppearance === 'light' ? '#16a34a' : '#22c55e'),
                          },
                        ]}
                        onPress={() => { void handleSaveTmdbKey(); }}
                        activeOpacity={0.8}
                      >
                        <Text style={[styles.rowActionText, { textAlign: 'center', width: '100%', color: resolvedAppearance === 'light' && theme.id === 'monochrome' ? '#111111' : '#ffffff' }]}>{t('settings_save')}</Text>
                      </TouchableOpacity>
                      <Text style={styles.rowHint}>
                        Get a free API key at <Text style={{ color: colors.accentSoft }} onPress={() => { void Linking.openURL('https://www.themoviedb.org/settings/api'); }}>themoviedb.org/settings/api</Text>. TMDB keys always save locally on this device first. If you sign in, the same key is also synced to your account so it follows you to other devices.
                      </Text>
                    </View>
                  </>
                ) : null}
              </View>
            </ScrollView>
          </View>
        </View>
      </Modal>

      <Modal visible={legalModal} transparent animationType="slide" statusBarTranslucent onRequestClose={() => setLegalModal(false)}>
        <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.7)', justifyContent: 'flex-end' }}>
          <View style={{ backgroundColor: colors.cardBg, borderTopLeftRadius: 24, borderTopRightRadius: 24, paddingBottom: insets.bottom + 16, borderTopWidth: 1, borderColor: colors.border }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingTop: 20, paddingBottom: 16 }}>
              <Ionicons name="document-text-outline" size={22} color={resolvedAppearance === 'light' && theme.id === 'monochrome' ? '#111111' : colors.accent} style={{ marginRight: 10 }} />
              <Text style={{ flex: 1, color: colors.textPrimary, fontSize: 18, fontWeight: '800' }}>{t('settings_legal')}</Text>
              <TouchableOpacity onPress={() => setLegalModal(false)} activeOpacity={0.75}
                style={{ width: 32, height: 32, borderRadius: 16, backgroundColor: colors.border, justifyContent: 'center', alignItems: 'center' }}>
                <Ionicons name="close" size={18} color={colors.textPrimary} />
              </TouchableOpacity>
            </View>
            <ScrollView style={{ maxHeight: 520 }} contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 8, gap: 20 }} showsVerticalScrollIndicator={false}>
              <LegalSection title={t('settings_legal_nature_title')} c={colors}>
                {t('settings_legal_nature_body')}
              </LegalSection>
              <LegalSection title={t('settings_legal_third_party_title')} c={colors}>
                {t('settings_legal_third_party_body')}
              </LegalSection>
              <LegalSection title={t('settings_legal_user_title')} c={colors}>
                {t('settings_legal_user_body')}
              </LegalSection>
              <LegalSection title={t('settings_legal_copyright_title')} c={colors}>
                {t('settings_legal_copyright_body')}
              </LegalSection>
              <LegalSection title={t('settings_legal_warranty_title')} c={colors}>
                {t('settings_legal_warranty_body')}
              </LegalSection>
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
      </BlurTargetView>
      <StackBottomNav activeTab="Settings" blurTarget={blurTargetRef} />
    </View>
  );
};
