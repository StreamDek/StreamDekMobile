import React, { useEffect, useMemo, useState } from 'react';
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
  Dimensions,
} from 'react-native';
import DraggableFlatList, { RenderItemParams } from 'react-native-draggable-flatlist';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
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
import { useProfile } from '../context/ProfileContext';
import { useAddons } from '../context/AddonContext';
import { useDebrid } from '../context/DebridContext';
import { useTrakt } from '../context/TraktContext';
import { useTorrentServer } from '../context/TorrentServerContext';
import { fetchAccountBootstrap } from '../utils/accountPreferences';
import { invalidateSharedCache } from '../utils/sharedDataCache';
import { checkSyncAllowed, getSyncOverCellular, setSyncOverCellular } from '../utils/cellularGuard';
import { Storage } from '../utils/storage';
import { profileScopedStorageKey } from '../utils/profileStorage';
import {
  CinematicSkeleton,
  GlassSkeleton,
  TicketSkeleton,
  MiniSkeleton,
  StackedSkeleton,
} from '../components/ContinueWatchingCard';

const SECTION_TITLE_KEYS = {
  'general-playback': 'settings_detail_general_playback',
  'home-appearance': 'settings_detail_home_appearance',
  'account-services': 'settings_detail_account_services',
} as const;

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

type PickerKind = 'appearance' | 'theme' | 'language' | 'quality' | 'fileSize' | 'pageStyle' | 'continueStyle' | 'metadataProvider' | 'decoder' | 'surface' | 'streamingMode' | null;
type HomeLayoutSection = { id: string; title: string; endpoint: string; enabled: boolean };
const CURRENT_YEAR = new Date().getFullYear();
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
    layoutModalScroll: { marginTop: 14 },
    layoutModalContent: { paddingBottom: 40 },
    layoutFooterSpacer: { height: 120 },
    layoutHint: { color: c.textSecondary, fontSize: 12, lineHeight: 17, marginTop: 2, marginBottom: 4 },
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
    layoutRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingVertical: 14,
      paddingHorizontal: 4,
    },
    layoutGrip: {
      width: 28,
      alignItems: 'center',
      justifyContent: 'center',
      paddingVertical: 4,
    },
    layoutInfo: { flex: 1 },
    layoutLabel: { color: c.textPrimary, fontSize: 15, fontWeight: '700' },
    layoutSub: { color: c.textSecondary, fontSize: 12, lineHeight: 17, marginTop: 3 },
    layoutDivider: { height: 1, backgroundColor: c.borderSoft },
    layoutDragActive: {
      backgroundColor: c.cardBgElevated ?? c.cardBg,
      borderRadius: 16,
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
  disabled = false,
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  iconColor: string;
  label: string;
  subtitle: string;
  value?: string;
  onPress?: () => void;
  right?: React.ReactNode;
  disabled?: boolean;
}) {
  const { theme: { colors, id }, resolvedAppearance } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const visibleIconColor = getVisibleIconColor(iconColor, resolvedAppearance, id, colors.textPrimary);

  const body = (
    <View style={[styles.row, disabled ? { opacity: 0.5 } : null]}>
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
  return <TouchableOpacity onPress={onPress} activeOpacity={0.78} disabled={disabled}>{body}</TouchableOpacity>;
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
                    onClose();
                    requestAnimationFrame(() => onSelect(option.value));
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
  const windowHeight = Dimensions.get('window').height;
  const detailSection = route?.params?.section ?? 'general-playback';
  const { user, signOut } = useAuth();
  const { activeProfile } = useProfile();
  const { refreshAddons } = useAddons();
  const { refreshAccounts } = useDebrid();
  const { checkStatus } = useTrakt();
  const { config: torrentServerConfig, status: torrentServerStatus, updateConfig: updateTorrentServerConfig } = useTorrentServer();
  const { theme, appearance, resolvedAppearance, setAppearance, setThemeId, showHeroSynopsis, setShowHeroSynopsis } = useTheme();
  const { language, setLanguage, t } = useLanguage();
  const title = t((SECTION_TITLE_KEYS as Record<string, any>)[detailSection] ?? 'settings_title');
  const { uiStyle, setUiStyle } = useUIStyle();
  const { showNavLabels, setShowNavLabels, continueWatchingStyle, setContinueWatchingStyle, vividAmbientEnabled, setVividAmbientEnabled, pictureInPictureEnabled, setPictureInPictureEnabled, showStreamsList, setShowStreamsList } = useDisplaySettings();
  const { metadataProvider, tmdbKeyEnabled, tmdbApiKey, setMetadataProvider, setTmdbKeyEnabled, setTmdbApiKey } = useTmdbApiKey();
  const {
    preferredQuality,
    setPreferredQuality,
    maxFileSizeGB,
    setMaxFileSizeGB,
  } = useStreamSelectionSettings();
  const { decoderMode, setDecoderMode, renderSurface, setRenderSurface } = usePlaybackSettings();
  const { autoLoadEnabled, setAutoLoadEnabled, preferHI, setPreferHI, preferForced, setPreferForced } = useSubtitles();
  const { colors } = theme;
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const [picker, setPicker] = useState<PickerKind>(null);
  const [showHomeLayoutModal, setShowHomeLayoutModal] = useState(false);
  const [homeLayoutSections, setHomeLayoutSections] = useState<HomeLayoutSection[]>([]);
  const [tmdbDraft, setTmdbDraft] = useState(tmdbApiKey);
  const [syncRefreshing, setSyncRefreshing] = useState(false);
  const [syncOverCellular, setSyncOverCellularState] = useState(false);
  const safeIconColor = React.useCallback((color: string) => getVisibleIconColor(color, resolvedAppearance, theme.id, colors.textPrimary), [colors.textPrimary, resolvedAppearance, theme.id]);
  const homeSectionSettingsKey = profileScopedStorageKey('home_sections', user?.uid, activeProfile?.id);

  const defaultHomeSections = useMemo<HomeLayoutSection[]>(() => {
    if (metadataProvider === 'cinemeta') {
      return [
        { id: 'networks', title: t('section_networks'), endpoint: '/tmdb/networks', enabled: true },
        { id: 'featured_movie', title: t('section_featured_movies'), endpoint: '/cinemeta/catalog/movie/imdbRating', enabled: true },
        { id: 'featured_tv', title: t('section_featured_series'), endpoint: '/cinemeta/catalog/series/imdbRating', enabled: true },
        { id: 'popular_movie', title: t('section_popular_movies'), endpoint: '/cinemeta/catalog/movie/top', enabled: true },
        { id: 'popular_tv', title: t('section_popular_tv'), endpoint: '/cinemeta/catalog/series/top', enabled: true },
        { id: 'documentaries', title: t('section_documentaries'), endpoint: '/cinemeta/catalog/movie/top?genre=Documentary', enabled: false },
        { id: 'new_movie', title: t('section_new_movies'), endpoint: `/cinemeta/catalog/movie/year/${CURRENT_YEAR}`, enabled: false },
        { id: 'new_tv', title: t('section_new_series'), endpoint: `/cinemeta/catalog/series/year/${CURRENT_YEAR}`, enabled: false },
      ];
    }

    return [
      { id: 'networks', title: t('section_networks'), endpoint: '/tmdb/networks', enabled: true },
      { id: 'trending_movie', title: t('section_trending_movies'), endpoint: '/tmdb/trending/movie', enabled: true },
      { id: 'trending_tv', title: t('section_trending_tv'), endpoint: '/tmdb/trending/tv', enabled: true },
      { id: 'documentaries', title: t('section_documentaries'), endpoint: '/tmdb/discover?type=movie&genre_id=99&sort_by=popularity.desc', enabled: false },
      { id: 'popular_movie', title: t('section_popular_movies'), endpoint: '/tmdb/popular/movie', enabled: false },
      { id: 'popular_tv', title: t('section_popular_tv'), endpoint: '/tmdb/popular/tv', enabled: false },
    ];
  }, [metadataProvider, t]);

  React.useEffect(() => { setTmdbDraft(tmdbApiKey); }, [tmdbApiKey]);

  React.useEffect(() => {
    void getSyncOverCellular().then(setSyncOverCellularState);
  }, []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const saved = await Storage.getItem(homeSectionSettingsKey) ?? await Storage.getItem('home_sections');
        if (!saved) {
          if (!cancelled) setHomeLayoutSections(defaultHomeSections);
          return;
        }
        const parsed = JSON.parse(saved);
        if (!Array.isArray(parsed)) {
          if (!cancelled) setHomeLayoutSections(defaultHomeSections);
          return;
        }
        const savedMap = new Map(parsed.map((section: any) => [section?.id, section]));
        const known = parsed
          .filter((section: any) => defaultHomeSections.find(def => def.id === section?.id))
          .map((section: any) => ({
            ...defaultHomeSections.find(def => def.id === section.id)!,
            enabled: Boolean(section.enabled),
          }));
        const newOnes = defaultHomeSections.filter(def => !savedMap.has(def.id));
        const next = [...known, ...newOnes];
        if (!cancelled) setHomeLayoutSections(next);
      } catch {
        if (!cancelled) setHomeLayoutSections(defaultHomeSections);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [defaultHomeSections, homeSectionSettingsKey]);

  const handleSetSyncOverCellular = React.useCallback(async (value: boolean) => {
    setSyncOverCellularState(value);
    await setSyncOverCellular(value);
  }, []);

  const themeLabelMap: Record<string, { label: string; description: string }> = {
    monochrome: { label: t('settings_theme_monochrome'), description: t('settings_theme_monochrome_sub') },
    ocean: { label: t('settings_theme_ocean'), description: t('settings_theme_ocean_sub') },
    emerald: { label: t('settings_theme_emerald'), description: t('settings_theme_emerald_sub') },
  };
  const themeOptions = THEMES.map(item => ({
    value: item.id,
    label: themeLabelMap[item.id]?.label ?? item.name,
    description: themeLabelMap[item.id]?.description ?? item.description,
    accentColor: item.swatch ?? colors.accent,
  }));
  const themeDisplayName = themeLabelMap[theme.id]?.label ?? theme.name;
  const languageOptions = LANGUAGES.map(item => ({ value: item.code, label: `${item.flag} ${item.name}` }));
  const appearanceOptions: PickerOption[] = [
    { value: 'system', label: t('settings_system'), description: t('settings_system_sub') },
    { value: 'dark', label: t('settings_dark'), description: t('settings_dark_sub') },
    { value: 'light', label: t('settings_light'), description: t('settings_light_sub') },
  ];
  const qualityOptions: PickerOption[] = [
    { value: '4k', label: '4K', description: t('settings_quality_4k_sub') },
    { value: '1080p', label: '1080p', description: t('settings_quality_1080p_sub') },
    { value: '720p', label: '720p', description: t('settings_quality_720p_sub') },
    { value: 'best', label: t('settings_quality_best'), description: t('settings_quality_best_sub') },
  ];
  const fileSizeOptions: PickerOption[] = [0, 4, 8, 12, 20].map(value => ({ value, label: value === 0 ? t('settings_unlimited') : `${value} GB` }));
  const streamingModeOptions: PickerOption[] = [
    { value: 'regular_http', label: t('settings_local_server'), description: t('settings_streaming_mode_regular_sub') },
    { value: 'server', label: t('settings_streaming_mode'), description: t('settings_streaming_mode_server_sub') },
  ];
  const pageStyleOptions: PickerOption[] = [
    { value: 'classic', label: t('settings_classic'), description: t('settings_page_style_classic_sub') },
    { value: 'centered', label: t('settings_centered'), description: t('settings_page_style_centered_sub') },
    { value: 'glass', label: t('settings_glassy_hero'), description: t('settings_page_style_glass_sub') },
  ];
  const continueWatchingOptions: PickerOption[] = [
    { value: 'cinematic', label: t('settings_continue_style_cinematic') },
    { value: 'glass', label: t('settings_continue_style_glass') },
    { value: 'ticket', label: t('settings_continue_style_ticket') },
    { value: 'mini', label: t('settings_continue_style_mini') },
    { value: 'stacked', label: t('settings_continue_style_stacked') },
  ];
  const continueStyleLabelMap: Record<string, string> = {
    cinematic: t('settings_continue_style_cinematic'),
    glass: t('settings_continue_style_glass'),
    ticket: t('settings_continue_style_ticket'),
    mini: t('settings_continue_style_mini'),
    stacked: t('settings_continue_style_stacked'),
  };
  const metadataOptions: PickerOption[] = [
    { value: 'cinemeta', label: t('settings_cinemeta'), description: t('settings_metadata_cinemeta_sub') },
    { value: 'tmdb', label: t('settings_tmdb'), description: t('settings_metadata_tmdb_sub') },
  ];
  const decoderOptions: PickerOption[] = [
    { value: 'auto', label: t('settings_decoder_auto') },
    { value: 'hardware', label: t('settings_decoder_hardware') },
    { value: 'software', label: t('settings_decoder_software') },
  ];
  const surfaceOptions: PickerOption[] = [
    { value: 'surface', label: t('settings_surface_view') },
    { value: 'texture', label: t('settings_texture_view') },
  ];
  const qualityValueLabelMap: Record<string, string> = {
    '4k': '4K',
    '1080p': '1080p',
    '720p': '720p',
    best: t('settings_quality_best'),
  };
  const decoderValueLabelMap: Record<string, string> = {
    auto: t('settings_decoder_auto'),
    hardware: t('settings_decoder_hardware'),
    software: t('settings_decoder_software'),
  };
  const surfaceValueLabelMap: Record<string, string> = {
    surface: t('settings_surface_view'),
    texture: t('settings_texture_view'),
  };

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
    : picker === 'streamingMode' ? streamingModeOptions
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
    : picker === 'streamingMode' ? torrentServerConfig.streamingMode
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
      case 'streamingMode': void updateTorrentServerConfig({ streamingMode: value as any }); break;
      case 'pageStyle':
        void setUiStyle(value as any);
        requestAnimationFrame(() => {
          navigation.reset({
            index: 0,
            routes: [{ name: 'Main', params: { screen: 'Home' } }],
          });
        });
        break;
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

  const persistHomeLayoutSections = React.useCallback(async (sections: HomeLayoutSection[]) => {
    setHomeLayoutSections(sections);
    await Storage.setItem(homeSectionSettingsKey, JSON.stringify(sections.map(section => ({
      id: section.id,
      enabled: section.enabled,
    }))));
  }, [homeSectionSettingsKey]);

  const toggleHomeLayoutSection = React.useCallback((id: string, enabled: boolean) => {
    const next = homeLayoutSections.map(section => (
      section.id === id ? { ...section, enabled } : section
    ));
    void persistHomeLayoutSections(next);
  }, [homeLayoutSections, persistHomeLayoutSections]);

  const handleHomeLayoutReorder = React.useCallback(({ data }: { data: HomeLayoutSection[] }) => {
    void persistHomeLayoutSections(data);
  }, [persistHomeLayoutSections]);

  const renderHomeLayoutItem = React.useCallback(({ item, drag, isActive }: RenderItemParams<HomeLayoutSection>) => (
    <View style={isActive ? styles.layoutDragActive : undefined}>
      <View style={styles.layoutRow}>
        <TouchableOpacity
          onLongPress={drag}
          delayLongPress={120}
          activeOpacity={0.75}
          style={styles.layoutGrip}
        >
          <Ionicons name="reorder-three-outline" size={18} color={colors.placeholder} />
        </TouchableOpacity>
      <View style={styles.layoutInfo}>
        <Text style={styles.layoutLabel}>{item.title}</Text>
        <Text style={styles.layoutSub}>{item.enabled ? t('settings_home_layout_visible') : t('settings_home_layout_hidden')}</Text>
      </View>
        <AppleToggle
          value={item.enabled}
          onValueChange={value => { toggleHomeLayoutSection(item.id, value); }}
          onColor={colors.toggleOn}
        />
      </View>
    </View>
  ), [colors.placeholder, colors.toggleOn, styles.layoutDragActive, styles.layoutGrip, styles.layoutInfo, styles.layoutLabel, styles.layoutRow, styles.layoutSub, t, toggleHomeLayoutSection]);
  const homeLayoutSheetMaxHeight = Math.min(720, Math.floor(windowHeight * 0.88));

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
                <Text style={{ color: colors.textSecondary, fontSize: 14, fontWeight: '600' }}>{t('common_back')}</Text>
              </TouchableOpacity>
              <View>
                <Text style={styles.headerTitle}>{title}</Text>
                <Text style={styles.headerSub}>{t('settings_detail_header_sub')}</Text>
              </View>

              {detailSection === 'general-playback' ? (
                <>
                  <Text style={styles.sectionTitle}>{t('settings_general')}</Text>
                  <View style={styles.card}>
                    <SettingRow icon="language-outline" iconColor={safeIconColor('#9b5de5')} label={t('settings_language')} subtitle={t('settings_language_sub_current')} value={`${language.flag} ${language.name}`} onPress={() => setPicker('language')} />
                    <View style={styles.divider} />
                    <SettingRow icon="moon-outline" iconColor={safeIconColor('#64748b')} label={t('settings_appearance')} subtitle={t('settings_appearance_sub')} value={appearance === 'dark' ? t('settings_dark') : appearance === 'light' ? t('settings_light') : t('settings_system')} onPress={() => setPicker('appearance')} />
                    <View style={styles.divider} />
                    <SettingRow icon="color-palette-outline" iconColor={safeIconColor('#f59e0b')} label={t('settings_theme')} subtitle={t('settings_theme_sub_current')} value={themeDisplayName} onPress={() => setPicker('theme')} />
                    <View style={styles.divider} />
                    <SettingRow icon="grid-outline" iconColor={safeIconColor('#6366f1')} label={t('settings_show_nav_labels')} subtitle={t('settings_show_nav_labels_sub')} right={<AppleToggle value={showNavLabels} onValueChange={value => { void setShowNavLabels(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="cellular-outline" iconColor={safeIconColor('#0ea5e9')} label={t('settings_sync_cellular')} subtitle={t('settings_sync_cellular_sub')} right={<AppleToggle value={syncOverCellular} onValueChange={handleSetSyncOverCellular} onColor={colors.toggleOn} />} />
                  </View>

                  <Text style={styles.sectionTitle}>{t('settings_playback')}</Text>
                  <View style={styles.card}>
                    <SettingRow icon="play-circle-outline" iconColor={safeIconColor('#6366f1')} label={t('settings_picture_in_picture')} subtitle={t('settings_picture_in_picture_sub')} right={<AppleToggle value={pictureInPictureEnabled} onValueChange={value => { void setPictureInPictureEnabled(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="albums-outline" iconColor={safeIconColor('#22d3ee')} label={t('settings_show_streams_list')} subtitle={t('settings_show_streams_list_sub')} right={<AppleToggle value={showStreamsList} onValueChange={value => { void setShowStreamsList(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="swap-horizontal-outline" iconColor={safeIconColor('#14b8a6')} label={t('settings_streaming_mode')} subtitle={torrentServerConfig.streamingMode === 'server' ? t('settings_streaming_mode_server_sub') : t('settings_streaming_mode_regular_sub')} value={torrentServerConfig.streamingMode === 'server' ? t('settings_streaming_mode') : t('settings_local_server')} onPress={() => setPicker('streamingMode')} />
                    <View style={styles.divider} />
                    <SettingRow icon="server-outline" iconColor={safeIconColor('#f59e0b')} label={t('settings_streaming_server_url')} subtitle={torrentServerConfig.streamingMode === 'server' ? (torrentServerStatus.isOnline ? torrentServerStatus.url : t('settings_streaming_mode_server_sub')) : t('settings_streaming_server_url_sub')} value={torrentServerConfig.streamingMode === 'server' ? (torrentServerStatus.isOnline ? 'Online' : 'Offline') : 'Inactive'} />
                    <View style={styles.divider} />
                    <SettingRow icon="resize-outline" iconColor={safeIconColor('#22c55e')} label={t('settings_preferred_stream_quality')} subtitle={t('settings_preferred_stream_quality_sub')} value={qualityValueLabelMap[String(preferredQuality)] ?? String(preferredQuality)} onPress={() => setPicker('quality')} />
                    <View style={styles.divider} />
                    <SettingRow icon="archive-outline" iconColor={safeIconColor('#f97316')} label={t('settings_max_file_size')} subtitle={t('settings_max_file_size_sub')} value={maxFileSizeGB === 0 ? t('settings_unlimited') : `${maxFileSizeGB} GB`} onPress={() => setPicker('fileSize')} />
                    <View style={styles.divider} />
                    <SettingRow icon="tv-outline" iconColor={safeIconColor('#a78bfa')} label={t('settings_decoder_mode')} subtitle={t('settings_decoder_mode_sub')} value={decoderValueLabelMap[String(decoderMode)] ?? String(decoderMode)} onPress={() => setPicker('decoder')} />
                    <View style={styles.divider} />
                    <SettingRow icon="scan-outline" iconColor={safeIconColor('#38bdf8')} label={t('settings_render_surface')} subtitle={t('settings_render_surface_sub')} value={surfaceValueLabelMap[String(renderSurface)] ?? String(renderSurface)} onPress={() => setPicker('surface')} />
                  </View>

                  <Text style={styles.sectionTitle}>{t('settings_subtitles')}</Text>
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
                  <Text style={styles.sectionTitle}>{t('settings_detail_home_appearance')}</Text>
                  <View style={styles.card}>
                    <SettingRow icon="film-outline" iconColor={safeIconColor('#f59e0b')} label={t('settings_catalog_metadata')} subtitle={metadataProvider === 'cinemeta' ? t('settings_catalog_metadata_cinemeta') : t('settings_catalog_metadata_tmdb')} value={metadataProvider === 'cinemeta' ? t('settings_cinemeta') : t('settings_tmdb')} onPress={() => setPicker('metadataProvider')} />
                    <View style={styles.divider} />
                    <SettingRow icon="grid-outline" iconColor={safeIconColor('#38bdf8')} label={t('settings_home_layout')} subtitle={t('settings_home_layout_sub')} value={t('settings_home_layout_active_count', { n: homeLayoutSections.filter(section => section.enabled).length })} onPress={() => setShowHomeLayoutModal(true)} />
                    <View style={styles.divider} />
                    <SettingRow icon="albums-outline" iconColor={safeIconColor('#22d3ee')} label={t('settings_page_style')} subtitle={t('settings_page_style_sub')} value={uiStyle === 'glass' ? t('settings_glassy_hero') : uiStyle === 'centered' ? t('settings_centered') : t('settings_classic')} onPress={() => setPicker('pageStyle')} />
                    <View style={styles.divider} />
                    <SettingRow icon="play-circle-outline" iconColor={safeIconColor('#22c55e')} label={t('settings_continue_watching_style')} subtitle={t('settings_continue_watching_style_sub')} value={continueStyleLabelMap[String(continueWatchingStyle)] ?? String(continueWatchingStyle)} onPress={() => setPicker('continueStyle')} />
                    <View style={styles.divider} />
                    <SettingRow icon="reader-outline" iconColor={safeIconColor('#64748b')} label={t('settings_hero_synopsis')} subtitle={t('settings_hero_synopsis_sub')} right={<AppleToggle value={showHeroSynopsis} onValueChange={value => { void setShowHeroSynopsis(value); }} onColor={colors.toggleOn} />} />
                    <View style={styles.divider} />
                    <SettingRow icon="color-wand-outline" iconColor={safeIconColor('#a78bfa')} label={t('settings_ambient_background')} subtitle={t('settings_ambient_background_sub')} right={<AppleToggle value={vividAmbientEnabled} onValueChange={value => { void setVividAmbientEnabled(value); }} onColor={colors.toggleOn} />} />
                  </View>
                  {metadataProvider === 'tmdb' ? (
                    <View style={styles.card}>
                      <View style={{ padding: 18, gap: 10 }}>
                        <Text style={styles.rowLabel}>{t('settings_tmdb_api_key')}</Text>
                        <Text style={styles.rowSub}>{t('settings_tmdb_api_key_sub')}</Text>
                        <TextInput
                          value={tmdbDraft}
                          onChangeText={setTmdbDraft}
                          placeholder={t('settings_tmdb_api_key_placeholder')}
                          placeholderTextColor={colors.placeholder}
                          autoCapitalize="none"
                          autoCorrect={false}
                          style={styles.textInput}
                        />
                        <SettingRow icon="key-outline" iconColor={safeIconColor('#f59e0b')} label={t('settings_tmdb_custom_key')} subtitle={t('settings_tmdb_custom_key_sub')} right={<AppleToggle value={tmdbKeyEnabled} onValueChange={value => { void setTmdbKeyEnabled(value); }} onColor={colors.toggleOn} />} />
                        <TouchableOpacity style={[styles.actionButton, { backgroundColor: colors.accent, borderWidth: 1, borderColor: resolvedAppearance === 'light' ? 'rgba(17,24,39,0.12)' : 'rgba(255,255,255,0.14)' }]} onPress={() => { void setTmdbApiKey(tmdbDraft.trim()); }} activeOpacity={0.82}>
                          <Text style={[styles.actionButtonText, { color: colors.buttonText }]}>{t('settings_tmdb_save_key')}</Text>
                        </TouchableOpacity>
                      </View>
                    </View>
                  ) : null}
                </>
              ) : null}

              {detailSection === 'account-services' ? (
                <>
                  <Text style={styles.sectionTitle}>{t('settings_detail_account_services')}</Text>
                  <View style={styles.card}>
                    <SettingRow
                      icon="person-circle-outline"
                      iconColor={safeIconColor(colors.mutedText)}
                      label={t('settings_account')}
                      subtitle={user ? (user.email ? `${t('addons_signed_in_as')} ${user.email}` : t('settings_account_signed_in_generic')) : t('settings_account_signed_out_sub')}
                      onPress={user ? undefined : () => navigation.navigate('Auth')}
                    />
                    {user ? <View style={styles.divider} /> : null}
                    {user ? (
                      <SettingRow
                        icon="sync-outline"
                        iconColor={safeIconColor('#22c55e')}
                        label={t('settings_refresh_sync')}
                        subtitle={t('settings_refresh_sync_sub')}
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
                      <Text style={[styles.actionButtonText, { color: '#ffffff' }]}>{t('settings_sign_out')}</Text>
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
        title={picker === 'appearance' ? t('settings_appearance') : picker === 'theme' ? t('settings_theme') : picker === 'language' ? t('settings_language') : picker === 'quality' ? t('settings_preferred_stream_quality') : picker === 'fileSize' ? t('settings_max_file_size') : picker === 'streamingMode' ? t('settings_streaming_mode') : picker === 'pageStyle' ? t('settings_page_style') : picker === 'continueStyle' ? t('settings_continue_watching_style') : picker === 'metadataProvider' ? t('settings_catalog_metadata') : picker === 'decoder' ? t('settings_decoder_mode') : picker === 'surface' ? t('settings_render_surface') : t('settings_picker_choose')}
        subtitle={picker === 'metadataProvider' ? t('settings_catalog_metadata_sub') : undefined}
        options={pickerOptions}
        selectedValue={pickerValue as any}
        onSelect={handlePickerSelect}
        onClose={() => setPicker(null)}
        renderPreview={picker === 'pageStyle' ? renderPageStylePreview : picker === 'continueStyle' ? renderContinueStylePreview : undefined}
      />
      <Modal visible={showHomeLayoutModal} transparent animationType="slide" statusBarTranslucent onRequestClose={() => setShowHomeLayoutModal(false)}>
        <Pressable style={styles.modalBackdrop} onPress={() => setShowHomeLayoutModal(false)}>
          <GestureHandlerRootView style={{ width: '100%' }}>
          <Pressable style={[styles.modalCard, { paddingBottom: insets.bottom + 16, maxHeight: homeLayoutSheetMaxHeight }]} onPress={() => {}}>
            <Text style={styles.modalTitle}>{t('settings_home_layout')}</Text>
            <Text style={styles.modalSub}>{t('settings_home_layout_modal_sub')}</Text>
            <Text style={styles.layoutHint}>{t('settings_home_layout_modal_hint')}</Text>
            <View style={[styles.layoutModalScroll, { maxHeight: homeLayoutSheetMaxHeight - 92, minHeight: Math.min(420, Math.floor(windowHeight * 0.52)) }]}>
              <DraggableFlatList
                data={homeLayoutSections}
                keyExtractor={item => item.id}
                renderItem={renderHomeLayoutItem}
                onDragEnd={handleHomeLayoutReorder}
                contentContainerStyle={styles.layoutModalContent}
                showsVerticalScrollIndicator={false}
                activationDistance={4}
                autoscrollSpeed={180}
                ItemSeparatorComponent={() => <View style={styles.layoutDivider} />}
                ListFooterComponent={<View style={styles.layoutFooterSpacer} />}
              />
            </View>
          </Pressable>
          </GestureHandlerRootView>
        </Pressable>
      </Modal>
      <StackBottomNav activeTab="Settings" blurTarget={blurTargetRef} />
    </View>
  );
}
