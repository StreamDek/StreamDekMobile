import React, { useMemo, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Modal,
  Pressable,
  StatusBar,
  TextInput,
  ActivityIndicator,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { BlurTargetView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { StackBottomNav } from '../components/StackBottomNav';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { AppleToggle } from '../components/AppleToggle';
import { useTheme, THEMES, ThemeColors } from '../context/ThemeContext';
import { useLanguage, LANGUAGES } from '../context/LanguageContext';
import { useUIStyle } from '../context/UIStyleContext';
import { useDisplaySettings } from '../context/DisplaySettingsContext';
import { useTmdbApiKey } from '../context/TmdbApiKeyContext';
import { useStreamSelectionSettings } from '../context/StreamSelectionContext';
import { usePlaybackSettings } from '../context/PlaybackSettingsContext';
import { useSubtitles } from '../context/SubtitleContext';
import { useAuth } from '../context/AuthContext';
import { useAddons } from '../context/AddonContext';
import { useDebrid } from '../context/DebridContext';
import { useTrakt } from '../context/TraktContext';
import { fetchAccountBootstrap } from '../utils/accountPreferences';
import { invalidateSharedCache } from '../utils/sharedDataCache';
import { checkSyncAllowed, getSyncOverCellular, setSyncOverCellular } from '../utils/cellularGuard';
import {
  CinematicSkeleton,
  GlassSkeleton,
  TicketSkeleton,
  MiniSkeleton,
  StackedSkeleton,
} from '../components/ContinueWatchingCard';

const SECTION_TITLES: Record<string, string> = {
  'general-playback': 'General, Playback and Subtitles',
  'home-appearance': 'Home and Appearance',
  'account-services': 'Account and Services',
};

type PickerOption = {
  value: string | number;
  label: string;
  description?: string;
  accentColor?: string;
};

function getVisibleIconColor(color: string, resolvedAppearance: 'dark' | 'light', themeId: string, fallback: string) {
  if (resolvedAppearance === 'light' && (themeId === 'monochrome' || color === '#ffffff' || color === '#fff')) {
    return fallback;
  }
  return color;
}

type PickerKind = 'appearance' | 'theme' | 'language' | 'quality' | 'fileSize' | 'pageStyle' | 'continueStyle' | 'metadataProvider' | 'decoder' | 'surface' | null;

function makeStyles(c: ThemeColors) {
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: c.bg },
    content: { paddingHorizontal: 20, gap: 18 },
    headerTitle: { color: c.textPrimary, fontSize: 30, fontWeight: '900', letterSpacing: -0.6 },
    headerSub: { color: c.textSecondary, fontSize: 14, lineHeight: 20, marginTop: 6 },
    sectionTitle: { color: c.textPrimary, fontSize: 14, fontWeight: '800', letterSpacing: 0.4 },
    card: {
      backgroundColor: c.cardBgElevated ?? c.cardBg,
      borderWidth: 1,
      borderColor: c.border,
      borderRadius: 22,
      overflow: 'hidden',
    },
    row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 18, paddingVertical: 16 },
    rowIcon: { width: 34, height: 34, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
    rowInfo: { flex: 1 },
    rowLabel: { color: c.textPrimary, fontSize: 16, fontWeight: '700' },
    rowSub: { color: c.textSecondary, fontSize: 13, lineHeight: 18, marginTop: 3 },
    rowValue: { color: c.textSecondary, fontSize: 13, fontWeight: '600' },
    divider: { height: 1, backgroundColor: c.borderSoft, marginLeft: 58 },
    modalBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.72)', justifyContent: 'flex-end' },
    modalCard: {
      backgroundColor: c.cardBg,
      borderTopLeftRadius: 26,
      borderTopRightRadius: 26,
      borderTopWidth: 1,
      borderColor: c.border,
      paddingHorizontal: 20,
      paddingTop: 20,
    },
    modalTitle: { color: c.textPrimary, fontSize: 19, fontWeight: '800' },
    modalSub: { color: c.textSecondary, fontSize: 13, lineHeight: 18, marginTop: 6, marginBottom: 8 },
    option: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingHorizontal: 16,
      paddingVertical: 14,
      borderRadius: 16,
      borderWidth: 1,
      borderColor: c.border,
      backgroundColor: c.cardBgElevated ?? c.cardBg,
    },
    optionText: { flex: 1 },
    optionTitle: { color: c.textPrimary, fontSize: 15, fontWeight: '700' },
    optionSub: { color: c.textSecondary, fontSize: 12, lineHeight: 17, marginTop: 3 },
    accentSwatch: { width: 16, height: 16, borderRadius: 8, borderWidth: 1, borderColor: 'rgba(255,255,255,0.25)' },
    modalScroll: { maxHeight: 420, marginTop: 14 },
    helper: { color: c.textSecondary, fontSize: 12, lineHeight: 18, paddingHorizontal: 18, paddingBottom: 14 },
    actionButton: {
      marginTop: 10,
      borderRadius: 16,
      paddingVertical: 14,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: c.accent,
    },
    actionButtonText: { color: '#ffffff', fontWeight: '800', fontSize: 14 },
    textInput: {
      backgroundColor: c.inputBg,
      borderWidth: 1,
      borderColor: c.border,
      borderRadius: 12,
      paddingHorizontal: 14,
      paddingVertical: 12,
      color: c.textPrimary,
      fontSize: 13,
      fontFamily: 'monospace',
    },
  });
}

function SettingRow({
  icon,
  iconColor,
  label,
  subtitle,
  value,
  onPress,
  right,
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  iconColor: string;
  label: string;
  subtitle: string;
  value?: string;
  onPress?: () => void;
  right?: React.ReactNode;
}) {
  const { theme: { colors, id }, resolvedAppearance } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const visibleIconColor = getVisibleIconColor(iconColor, resolvedAppearance, id, colors.textPrimary);

  const body = (
    <View style={styles.row}>
      <View style={[styles.rowIcon, { backgroundColor: `${visibleIconColor}22` }]}>
        <Ionicons name={icon} size={18} color={visibleIconColor} />
      </View>
      <View style={styles.rowInfo}>
        <Text style={styles.rowLabel}>{label}</Text>
        <Text style={styles.rowSub}>{subtitle}</Text>
      </View>
      {right ?? (value ? <Text style={styles.rowValue}>{value}</Text> : null)}
      {onPress && !right ? <Ionicons name="chevron-forward" size={18} color={colors.placeholder} /> : null}
    </View>
  );

  if (!onPress) return body;
  return <TouchableOpacity onPress={onPress} activeOpacity={0.78}>{body}</TouchableOpacity>;
}

function PickerModal({
  visible,
  title,
  subtitle,
  options,
  selectedValue,
  onClose,
  onSelect,
  renderPreview,
}: {
  visible: boolean;
  title: string;
  subtitle?: string;
  options: PickerOption[];
  selectedValue: string | number;
  onClose: () => void;
  onSelect: (value: string | number) => void;
  renderPreview?: (option: PickerOption, active: boolean) => React.ReactNode;
}) {
  const { theme: { colors }, resolvedAppearance } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const insets = useSafeAreaInsets();

  return (
    <Modal visible={visible} transparent animationType="slide" statusBarTranslucent onRequestClose={onClose}>
      <Pressable style={styles.modalBackdrop} onPress={onClose}>
        <Pressable style={[styles.modalCard, { paddingBottom: insets.bottom + 16 }]} onPress={() => {}}>
          <Text style={styles.modalTitle}>{title}</Text>
          {subtitle ? <Text style={styles.modalSub}>{subtitle}</Text> : null}
          <ScrollView style={styles.modalScroll} contentContainerStyle={{ gap: 10, paddingBottom: 4 }} showsVerticalScrollIndicator={false}>
            {options.map(option => {
              const active = option.value === selectedValue;
              return (
                <TouchableOpacity
                  key={String(option.value)}
                  style={[
                    styles.option,
                    active && {
                      borderColor: colors.accent === '#ffffff' ? colors.textPrimary : colors.accent,
                      backgroundColor: resolvedAppearance === 'light'
                        ? (colors.accent === '#ffffff' ? 'rgba(17,24,39,0.08)' : `${colors.accent}18`)
                        : `${colors.accent}16`,
                    },
                  ]}
                  activeOpacity={0.82}
                  onPress={() => {
                    onSelect(option.value);
                    onClose();
                  }}
                >
                  {renderPreview
                    ? renderPreview(option, active)
                    : option.accentColor
                      ? <View style={[styles.accentSwatch, { backgroundColor: option.accentColor }]} />
                      : null}
                  <View style={styles.optionText}>
                    <Text style={[styles.optionTitle, active && { color: colors.textPrimary }]}>{option.label}</Text>
                    {option.description ? <Text style={styles.optionSub}>{option.description}</Text> : null}
                  </View>
                  <Ionicons
                    name={active ? 'checkmark-circle' : 'ellipse-outline'}
                    size={20}
                    color={active ? (colors.accent === '#ffffff' ? colors.textPrimary : colors.accent) : colors.placeholder}
                  />
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

export function SettingsScreen({ navigation, route }: any) {
  const blurTargetRef = React.useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const detailSection = route?.params?.section ?? 'general-playback';
  const title = SECTION_TITLES[detailSection] ?? 'Settings';
  const { user, signOut } = useAuth();
  const { refreshAddons } = useAddons();
  const { refreshAccounts } = useDebrid();
  const { checkStatus } = useTrakt();
  const { theme, appearance, resolvedAppearance, setAppearance, setThemeId, showHeroSynopsis, setShowHeroSynopsis } = useTheme();
  const { language, setLanguage, t } = useLanguage();
  const { uiStyle, setUiStyle } = useUIStyle();
  const { showNavLabels, setShowNavLabels, continueWatchingStyle, setContinueWatchingStyle, vividAmbientEnabled, setVividAmbientEnabled, pictureInPictureEnabled, setPictureInPictureEnabled, showStreamsList, setShowStreamsList } = useDisplaySettings();
  const { metadataProvider, tmdbKeyEnabled, tmdbApiKey, setMetadataProvider, setTmdbKeyEnabled, setTmdbApiKey } = useTmdbApiKey();
  const { enabled: streamSelectionEnabled, setEnabled: setStreamSelectionEnabled, shortSourceFilterEnabled, setShortSourceFilterEnabled, preferredQuality, setPreferredQuality, maxFileSizeGB, setMaxFileSizeGB } = useStreamSelectionSettings();
  const { decoderMode, setDecoderMode, renderSurface, setRenderSurface } = usePlaybackSettings();
  const { autoLoadEnabled, setAutoLoadEnabled, preferHI, setPreferHI, preferForced, setPreferForced } = useSubtitles();
  const { colors } = theme;
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const [picker, setPicker] = useState<PickerKind>(null);
  const [tmdbDraft, setTmdbDraft] = useState(tmdbApiKey);
  const [syncRefreshing, setSyncRefreshing] = useState(false);
  const [syncOverCellular, setSyncOverCellularState] = useState(false);
  const safeIconColor = React.useCallback((color: string) => getVisibleIconColor(color, resolvedAppearance, theme.id, colors.textPrimary), [colors.textPrimary, resolvedAppearance, theme.id]);

  React.useEffect(() => { setTmdbDraft(tmdbApiKey); }, [tmdbApiKey]);

  React.useEffect(() => {
    void getSyncOverCellular().then(setSyncOverCellularState);
  }, []);

  const handleSetSyncOverCellular = React.useCallback(async (value: boolean) => {
    setSyncOverCellularState(value);
    await setSyncOverCellular(value);
  }, []);

  const themeOptions = THEMES.map(item => ({ value: item.id, label: item.name, description: item.description, accentColor: item.swatch ?? colors.accent }));
  const languageOptions = LANGUAGES.map(item => ({ value: item.code, label: `${item.flag} ${item.name}` }));
  const appearanceOptions: PickerOption[] = [
    { value: 'system', label: 'System', description: 'Follow the device appearance setting.' },
    { value: 'dark', label: 'Dark', description: 'Always use dark appearance.' },
    { value: 'light', label: 'Light', description: 'Always use light appearance.' },
  ];
  const qualityOptions: PickerOption[] = [
    { value: '4K', label: '4K', description: 'Prefer 4K streams first.' },
    { value: '1080p', label: '1080p', description: 'Prefer Full HD streams first.' },
    { value: '720p', label: '720p', description: 'Prefer HD streams first.' },
    { value: 'best', label: 'Best Available', description: 'Use StreamDek ranking without a fixed resolution target.' },
  ];
  const fileSizeOptions: PickerOption[] = [0, 4, 8, 12, 20].map(value => ({ value, label: value === 0 ? 'Unlimited' : `${value} GB` }));
  const pageStyleOptions: PickerOption[] = [
    { value: 'classic', label: 'Classic', description: 'Poster-forward layout with standard metadata placement.' },
    { value: 'centered', label: 'Centered', description: 'Centered presentation with cleaner spacing.' },
    { value: 'glass', label: 'Glassy Hero', description: 'Large cinematic hero with glass styling.' },
  ];
  const continueWatchingOptions: PickerOption[] = [
    { value: 'cinematic', label: 'Cinematic Backdrop' },
    { value: 'glass', label: 'Glass Overlay' },
    { value: 'ticket', label: 'Widescreen Ticket' },
    { value: 'mini', label: 'Mini Player Row' },
    { value: 'stacked', label: 'Tall Stacked' },
  ];
  const metadataOptions: PickerOption[] = [
    { value: 'cinemeta', label: 'Cinemeta', description: 'Built-in catalog with no account and no API key required.' },
    { value: 'tmdb', label: 'TMDB', description: 'Use TMDB metadata and your own API key when enabled.' },
  ];
  const decoderOptions: PickerOption[] = [
    { value: 'auto', label: 'Automatic' },
    { value: 'hardware', label: 'Hardware' },
    { value: 'software', label: 'Software' },
  ];
  const surfaceOptions: PickerOption[] = [
    { value: 'surface', label: 'Surface View' },
    { value: 'texture', label: 'Texture View' },
  ];

  const renderPageStylePreview = (option: PickerOption, active: boolean) => {
    const stroke = active ? (colors.accent === '#ffffff' ? colors.textPrimary : colors.accent) : colors.border;
    const surface = resolvedAppearance === 'light' ? '#ffffff' : 'rgba(255,255,255,0.08)';
    return (
      <View style={{ width: 46, height: 34, borderRadius: 10, borderWidth: 1.5, borderColor: stroke, backgroundColor: surface, padding: 5, justifyContent: 'space-between' }}>
        {option.value === 'glass' ? (
          <>
            <View style={{ height: 9, borderRadius: 6, backgroundColor: resolvedAppearance === 'light' ? 'rgba(17,24,39,0.14)' : 'rgba(255,255,255,0.12)' }} />
            <View style={{ flexDirection: 'row', gap: 3 }}>
              <View style={{ flex: 1, height: 4, borderRadius: 3, backgroundColor: stroke, opacity: 0.55 }} />
              <View style={{ flex: 1, height: 4, borderRadius: 3, backgroundColor: stroke, opacity: 0.28 }} />
            </View>
          </>
        ) : option.value === 'centered' ? (
          <>
            <View style={{ alignItems: 'center' }}>
              <View style={{ width: 24, height: 6, borderRadius: 4, backgroundColor: stroke, opacity: 0.55 }} />
            </View>
            <View style={{ height: 4, borderRadius: 3, backgroundColor: stroke, opacity: 0.24 }} />
          </>
        ) : (
          <>
            <View style={{ width: 18, height: 7, borderRadius: 4, backgroundColor: stroke, opacity: 0.48 }} />
            <View style={{ height: 4, borderRadius: 3, backgroundColor: stroke, opacity: 0.24 }} />
          </>
        )}
      </View>
    );
  };

  const renderContinueStylePreview = (option: PickerOption, active: boolean) => {
    const skeletonProps = { selected: active, colors };
    switch (option.value) {
      case 'cinematic': return <CinematicSkeleton {...skeletonProps} />;
      case 'glass':     return <GlassSkeleton     {...skeletonProps} />;
      case 'ticket':    return <TicketSkeleton     {...skeletonProps} />;
      case 'mini':      return <MiniSkeleton       {...skeletonProps} />;
      case 'stacked':   return <StackedSkeleton    {...skeletonProps} />;
      default:          return null;
    }
  };

  const pickerOptions = picker === 'appearance' ? appearanceOptions
    : picker === 'theme' ? themeOptions
    : picker === 'language' ? languageOptions
    : picker === 'quality' ? qualityOptions
    : picker === 'fileSize' ? fileSizeOptions
    : picker === 'pageStyle' ? pageStyleOptions
    : picker === 'continueStyle' ? continueWatchingOptions
    : picker === 'metadataProvider' ? metadataOptions
    : picker === 'decoder' ? decoderOptions
    : picker === 'surface' ? surfaceOptions
    : [];

  const pickerValue = picker === 'appearance' ? appearance
    : picker === 'theme' ? theme.id
    : picker === 'language' ? language.code
    : picker === 'quality' ? preferredQuality
    : picker === 'fileSize' ? maxFileSizeGB
    : picker === 'pageStyle' ? uiStyle
    : picker === 'continueStyle' ? continueWatchingStyle
    : picker === 'metadataProvider' ? metadataProvider
    : picker === 'decoder' ? decoderMode
    : picker === 'surface' ? renderSurface
    : '';

  const handlePickerSelect = (value: string | number) => {
    switch (picker) {
      case 'appearance': void setAppearance(value as any); break;
      case 'theme': void setThemeId(value as any); break;
      case 'language': setLanguage(value as any); break;
      case 'quality': void setPreferredQuality(value as any); break;
      case 'fileSize': void setMaxFileSizeGB(Number(value)); break;
      case 'pageStyle': void setUiStyle(value as any); break;
      case 'continueStyle': void setContinueWatchingStyle(value as any); break;
      case 'metadataProvider': void setMetadataProvider(value as any); break;
      case 'decoder': void setDecoderMode(value as any); break;
      case 'surface': void setRenderSurface(value as any); break;
      default: break;
    }
  };

  const handleRefreshSync = React.useCallback(async () => {
    if (!user || syncRefreshing) return;

    const guard = await checkSyncAllowed();
    if (!guard.allowed) {
      if (guard.reason === 'offline') return;
      // cellular_blocked — user has sync-over-cellular disabled; skip silently
      return;
    }

    setSyncRefreshing(true);
    try {
      invalidateSharedCache(`bootstrap:${user.uid}:`);
      invalidateSharedCache('addons:manifests:');
      const [bootstrap] = await Promise.allSettled([
        fetchAccountBootstrap(user),
        refreshAddons(),
        refreshAccounts(),
        checkStatus(),
      ]);
      // Persist syncOverCellular from server preferences so the device stays in sync
      if (bootstrap.status === 'fulfilled' && bootstrap.value) {
        const serverPref = bootstrap.value.preferences?.app?.syncOverCellular;
        if (typeof serverPref === 'boolean') {
          await setSyncOverCellular(serverPref);
          setSyncOverCellularState(serverPref);
        }
      }
    } finally {
      setSyncRefreshing(false);
    }
  }, [checkStatus, refreshAccounts, refreshAddons, syncRefreshing, user]);

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
        <View style={styles.container}>
          <StatusBar barStyle={resolvedAppearance === 'light' ? 'dark-content' : 'light-content'} translucent backgroundColor="transparent" />
          <LinearGradient colors={[colors.bgHeader, 'transparent']} style={{ position: 'absolute', top: 0, left: 0, right: 0, height: insets.top + 88, zIndex: 1 }} pointerEvents="none" />
          <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingTop: insets.top + 22, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }}>
            <View style={styles.content}>
              <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.78} style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <Ionicons name="chevron-back" size={20} color={colors.textSecondary} />
                <Text style={{ color: colors.textSecondary, fontSize: 14, fontWeight: '600' }}>Back</Text>
              </TouchableOpacity>
              <View>
                <Text style={styles.headerTitle}>{title}</Text>
                <Text style={styles.headerSub}>Focused detail page for faster settings interactions and background application of changes.</Text>
              </View>

              {detailSection === 'general-playback' ? (
                <>
                  <Text style={styles.sectionTitle}>General</Text>
                  <View style={styles.card}>
                    <SettingRow icon="language-outline" iconColor={safeIconColor('#9b5de5')} label={t('settings_language')} subtitle="Choose the language used for the app interface." value={`${language.flag} ${language.name}`} onPress={() => setPicker('language')} />
                    <View style={styles.divider} />
                    <SettingRow icon="moon-outline" iconColor={safeIconColor('#64748b')} label={t('settings_appearance')} subtitle={t('settings_appearance_sub')} value={appearance === 'dark' ? t('settings_dark') : appearance === 'light' ? t('settings_light') : t('settings_system')} onPress={() => setPicker('appearance')} />
                    <View style={styles.divider} />
                    <SettingRow icon="color-palette-outline" iconColor={safeIconColor('#f59e0b')} label={t('settings_theme')} subtitle="Choose the colour theme used across the app." value={theme.name} onPress={() => setPicker('theme')} />
                    <View style={styles.divider} />
                    <SettingRow icon="grid-outline" iconColor={safeIconColor('#6366f1')} label="Show Navigation Labels" subtitle="Display tab names below the nav icons." right={<AppleToggle value={showNavLabels} onValueChange={value => { void setShowNavLabels(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="cellular-outline" iconColor={safeIconColor('#0ea5e9')} label="Sync on Cellular" subtitle="Allow account and addon sync when not on Wi-Fi." right={<AppleToggle value={syncOverCellular} onValueChange={handleSetSyncOverCellular} onColor={colors.toggleOn} />} />
                  </View>

                  <Text style={styles.sectionTitle}>Playback</Text>
                  <View style={styles.card}>
                    <SettingRow icon="play-circle-outline" iconColor={safeIconColor('#6366f1')} label={t('settings_picture_in_picture')} subtitle={t('settings_picture_in_picture_sub')} right={<AppleToggle value={pictureInPictureEnabled} onValueChange={value => { void setPictureInPictureEnabled(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="albums-outline" iconColor={safeIconColor('#22d3ee')} label="Show Streams List" subtitle="Show the streams tab on media pages." right={<AppleToggle value={showStreamsList} onValueChange={value => { void setShowStreamsList(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="construct-outline" iconColor={safeIconColor('#14b8a6')} label={t('settings_stream_selection_logic')} subtitle={t('settings_stream_selection_logic_sub')} right={<AppleToggle value={streamSelectionEnabled} onValueChange={value => { void setStreamSelectionEnabled(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="timer-outline" iconColor={safeIconColor('#f59e0b')} label={t('settings_short_source_filter')} subtitle={t('settings_short_source_filter_sub')} right={<AppleToggle value={shortSourceFilterEnabled} onValueChange={value => { void setShortSourceFilterEnabled(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="tv-outline" iconColor={safeIconColor('#a78bfa')} label={t('settings_decoder_mode')} subtitle={t('settings_decoder_mode_sub')} value={String(decoderMode)} onPress={() => setPicker('decoder')} />
                    <View style={styles.divider} />
                    <SettingRow icon="scan-outline" iconColor={safeIconColor('#38bdf8')} label={t('settings_render_surface')} subtitle={t('settings_render_surface_sub')} value={String(renderSurface)} onPress={() => setPicker('surface')} />
                    <View style={styles.divider} />
                    <SettingRow icon="resize-outline" iconColor={safeIconColor('#22c55e')} label="Preferred Stream Quality" subtitle="Bias automatic stream ordering toward your preferred quality." value={String(preferredQuality)} onPress={() => setPicker('quality')} />
                    <View style={styles.divider} />
                    <SettingRow icon="archive-outline" iconColor={safeIconColor('#f97316')} label="Max File Size" subtitle="Exclude streams larger than this size." value={maxFileSizeGB === 0 ? 'Unlimited' : `${maxFileSizeGB} GB`} onPress={() => setPicker('fileSize')} />
                  </View>

                  <Text style={styles.sectionTitle}>Subtitles</Text>
                  <View style={styles.card}>
                    <SettingRow icon="text-outline" iconColor={safeIconColor('#8b5cf6')} label={t('settings_auto_load_subtitles')} subtitle={t('settings_auto_load_subtitles_sub')} right={<AppleToggle value={autoLoadEnabled} onValueChange={value => { void setAutoLoadEnabled(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="ear-outline" iconColor={safeIconColor('#ec4899')} label={t('settings_prefer_hi_subtitles')} subtitle={t('settings_prefer_hi_subtitles_sub')} right={<AppleToggle value={preferHI} onValueChange={value => { void setPreferHI(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="flag-outline" iconColor={safeIconColor('#f59e0b')} label={t('settings_prefer_forced_subtitles')} subtitle={t('settings_prefer_forced_subtitles_sub')} right={<AppleToggle value={preferForced} onValueChange={value => { void setPreferForced(value); }} onColor={colors.toggleOn} />} />
                  </View>
                </>
              ) : null}

              {detailSection === 'home-appearance' ? (
                <>
                  <Text style={styles.sectionTitle}>Home and Appearance</Text>
                  <View style={styles.card}>
                    <SettingRow icon="film-outline" iconColor={safeIconColor('#f59e0b')} label="Catalog & Metadata" subtitle={metadataProvider === 'cinemeta' ? 'Cinemeta is active.' : 'TMDB is active.'} value={metadataProvider === 'cinemeta' ? 'Cinemeta' : 'TMDB'} onPress={() => setPicker('metadataProvider')} />
                    <View style={styles.divider} />
                    <SettingRow icon="albums-outline" iconColor={safeIconColor('#22d3ee')} label={t('settings_page_style')} subtitle={t('settings_page_style_sub')} value={uiStyle === 'glass' ? 'Glassy Hero' : uiStyle === 'centered' ? 'Centered' : 'Classic'} onPress={() => setPicker('pageStyle')} />
                    <View style={styles.divider} />
                    <SettingRow icon="play-circle-outline" iconColor={safeIconColor('#22c55e')} label="Continue Watching Style" subtitle="Choose how continue watching cards appear on Home." value={String(continueWatchingStyle)} onPress={() => setPicker('continueStyle')} />
                    <View style={styles.divider} />
                    <SettingRow icon="reader-outline" iconColor={safeIconColor('#64748b')} label={t('settings_hero_synopsis')} subtitle={t('settings_hero_synopsis_sub')} right={<AppleToggle value={showHeroSynopsis} onValueChange={value => { void setShowHeroSynopsis(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="color-wand-outline" iconColor={safeIconColor('#a78bfa')} label="Ambient Background" subtitle="Show a colourful ambient glow behind home and detail screens." right={<AppleToggle value={vividAmbientEnabled} onValueChange={value => { void setVividAmbientEnabled(value); }} onColor={colors.toggleOn} />} />
                  </View>
                  {metadataProvider === 'tmdb' ? (
                    <View style={styles.card}>
                      <View style={{ padding: 18, gap: 10 }}>
                        <Text style={styles.rowLabel}>TMDB API Key</Text>
                        <Text style={styles.rowSub}>If you are signed out, this is stored on device first and can sync later when you create an account.</Text>
                        <TextInput
                          value={tmdbDraft}
                          onChangeText={setTmdbDraft}
                          placeholder="Paste your TMDB API key here"
                          placeholderTextColor={colors.placeholder}
                          autoCapitalize="none"
                          autoCorrect={false}
                          style={styles.textInput}
                        />
                        <SettingRow icon="key-outline" iconColor={safeIconColor('#f59e0b')} label="Use Custom TMDB Key" subtitle="Turn TMDB enrichment on or off." right={<AppleToggle value={tmdbKeyEnabled} onValueChange={value => { void setTmdbKeyEnabled(value); }} onColor={colors.toggleOn} />} />
                        <TouchableOpacity style={[styles.actionButton, { backgroundColor: colors.accent, borderWidth: 1, borderColor: resolvedAppearance === 'light' ? 'rgba(17,24,39,0.12)' : 'rgba(255,255,255,0.14)' }]} onPress={() => { void setTmdbApiKey(tmdbDraft.trim()); }} activeOpacity={0.82}>
                          <Text style={[styles.actionButtonText, { color: colors.buttonText }]}>Save TMDB API Key</Text>
                        </TouchableOpacity>
                      </View>
                    </View>
                  ) : null}
                </>
              ) : null}

              {detailSection === 'account-services' ? (
                <>
                  <Text style={styles.sectionTitle}>Account and Services</Text>
                  <View style={styles.card}>
                    <SettingRow
                      icon="person-circle-outline"
                      iconColor={safeIconColor(colors.mutedText)}
                      label="Account"
                      subtitle={user ? (user.email ? `Signed in as ${user.email}` : 'Signed in · Settings syncing across devices') : 'Create an account for sync, TV linking, and Trakt.'}
                      onPress={user ? undefined : () => navigation.navigate('Auth')}
                    />
                    {user ? <View style={styles.divider} /> : null}
                    {user ? (
                      <SettingRow
                        icon="sync-outline"
                        iconColor={safeIconColor('#22c55e')}
                        label="Refresh Sync"
                        subtitle="Pull the latest account, addon, debrid, and Trakt state from the cloud."
                        right={
                          <View style={{ width: 34, height: 34, borderRadius: 17, backgroundColor: '#22c55e18', borderWidth: 1, borderColor: '#22c55e55', justifyContent: 'center', alignItems: 'center' }}>
                            {syncRefreshing
                              ? <ActivityIndicator size="small" color="#22c55e" />
                              : <Ionicons name="refresh-outline" size={17} color="#22c55e" />}
                          </View>
                        }
                        onPress={() => { void handleRefreshSync(); }}
                      />
                    ) : null}
                  </View>
                  {user ? (
                    <TouchableOpacity style={[styles.actionButton, { backgroundColor: '#c0392b' }]} onPress={() => signOut()} activeOpacity={0.82}>
                      <Text style={[styles.actionButtonText, { color: '#ffffff' }]}>Sign Out</Text>
                    </TouchableOpacity>
                  ) : null}
                </>
              ) : null}
            </View>
          </ScrollView>
        </View>
      </BlurTargetView>
      <PickerModal
        visible={picker !== null}
        title={picker === 'appearance' ? t('settings_appearance') : picker === 'theme' ? t('settings_theme') : picker === 'language' ? t('settings_language') : picker === 'quality' ? 'Preferred Stream Quality' : picker === 'fileSize' ? 'Max File Size' : picker === 'pageStyle' ? t('settings_page_style') : picker === 'continueStyle' ? 'Continue Watching Style' : picker === 'metadataProvider' ? 'Catalog & Metadata' : picker === 'decoder' ? t('settings_decoder_mode') : picker === 'surface' ? t('settings_render_surface') : 'Choose an option'}
        subtitle={picker === 'metadataProvider' ? 'Pick the catalog source used across Home and metadata views.' : undefined}
        options={pickerOptions}
        selectedValue={pickerValue as any}
        onSelect={handlePickerSelect}
        onClose={() => setPicker(null)}
        renderPreview={picker === 'pageStyle' ? renderPageStylePreview : picker === 'continueStyle' ? renderContinueStylePreview : undefined}
      />
      <StackBottomNav activeTab="Settings" blurTarget={blurTargetRef} />
    </View>
  );
}
