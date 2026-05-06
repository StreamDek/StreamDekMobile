import React, { RefObject, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Image, Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import { useAuth } from '../context/AuthContext';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useLanguage, TranslationKey } from '../context/LanguageContext';
import { useProfile } from '../context/ProfileContext';
import { useDisplaySettings } from '../context/DisplaySettingsContext';
import { PROFILE_AVATARS } from '../utils/profileApi';

export const BOTTOM_NAV_HEIGHT_WITH_LABELS = 70;
export const BOTTOM_NAV_HEIGHT_WITHOUT_LABELS = 62;
export const BOTTOM_NAV_HEIGHT = BOTTOM_NAV_HEIGHT_WITH_LABELS;

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

const TABS: { name: string; labelKey: TranslationKey; icon: IoniconName; iconActive: IoniconName }[] = [
  { name: 'Home',             labelKey: 'nav_home',      icon: 'home-outline',        iconActive: 'home'        },
  { name: 'Search',           labelKey: 'nav_search',    icon: 'search-outline',      iconActive: 'search'      },
  { name: 'ContinueWatching', labelKey: 'nav_continue',  icon: 'play-circle-outline', iconActive: 'play-circle' },
  { name: 'Watchlist',        labelKey: 'nav_watchlist', icon: 'bookmark-outline',    iconActive: 'bookmark'    },
  { name: 'Settings',         labelKey: 'nav_settings',  icon: 'settings-outline',    iconActive: 'settings'    },
];

function hexToRgba(hex: string, alpha: number) {
  const h = hex.replace('#', '');
  const r = parseInt(h.substring(0, 2), 16);
  const g = parseInt(h.substring(2, 4), 16);
  const b = parseInt(h.substring(4, 6), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

function darkenHex(hex: string, factor: number) {
  const h = hex.replace('#', '');
  const r = Math.floor(parseInt(h.substring(0, 2), 16) * factor);
  const g = Math.floor(parseInt(h.substring(2, 4), 16) * factor);
  const b = Math.floor(parseInt(h.substring(4, 6), 16) * factor);
  return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`;
}

const makeStyles = (c: ThemeColors, bgRgba: string, navHeight: number, isLightAppearance: boolean) => StyleSheet.create({
  container: { position: 'absolute', bottom: 0, left: 0, right: 0, paddingHorizontal: 14 },
  shell: {
    borderRadius: 30,
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: isLightAppearance ? 'rgba(255,255,255,0.72)' : 'rgba(255,255,255,0.18)',
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOpacity: isLightAppearance ? 0.16 : 0.26,
    shadowRadius: 28,
    shadowOffset: { width: 0, height: 14 },
    elevation: 16,
  },
  blurFill: {
    ...StyleSheet.absoluteFillObject,
  },
  glassTint: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: bgRgba,
  },
  glassGradient: {
    ...StyleSheet.absoluteFillObject,
  },
  glassGlow: {
    ...StyleSheet.absoluteFillObject,
  },
  inner: { flexDirection: 'row', height: navHeight, alignItems: 'center', paddingHorizontal: 10 },
  activePill: {
    position: 'absolute',
    top: 8,
    bottom: 8,
    borderRadius: 22,
  },
  tab: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingVertical: 8, gap: 3, borderRadius: 22 },
  label: { fontSize: 10, color: c.textSecondary, fontWeight: '600', letterSpacing: 0.2 },
  labelActive: { color: c.accentSoft },
});

interface Props {
  activeTab?: string;
  blurTarget?: RefObject<View | null>;
  onTabPress?: (tabName: string) => void;
}

export const StackBottomNav: React.FC<Props> = ({ activeTab, blurTarget, onTabPress }) => {
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<any>();
  const { user } = useAuth();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const { t } = useLanguage();
  const { activeProfile } = useProfile();
  const { showNavLabels } = useDisplaySettings();

  const navHeight = showNavLabels ? BOTTOM_NAV_HEIGHT_WITH_LABELS : BOTTOM_NAV_HEIGHT_WITHOUT_LABELS;
  const iconSize = showNavLabels ? 24 : 28;
  const avatarSize = showNavLabels ? 26 : 30;
  const avatarSizeActive = showNavLabels ? 28 : 32;
  const isLightAppearance = resolvedAppearance === 'light';

  const bgRgba = useMemo(
    () => (resolvedAppearance === 'light'
      ? hexToRgba(colors.bgHeaderSolid, 0.032)
      : hexToRgba(darkenHex(colors.bgHeaderSolid, 1), 0.03)),
    [colors.bgHeaderSolid, resolvedAppearance],
  );
  const styles = useMemo(() => makeStyles(colors, bgRgba, navHeight, isLightAppearance), [bgRgba, colors, navHeight, isLightAppearance]);
  const pillTranslateX = useSharedValue(0);
  const pillWidth = useSharedValue(0);
  const hasMeasuredInitialPillRef = useRef(false);
  const [tabLayouts, setTabLayouts] = useState<Record<string, { x: number; width: number }>>({});

  const visibleTabs = TABS;

  const profileAvatar = activeProfile
    ? PROFILE_AVATARS[Math.min(activeProfile.avatarIndex, PROFILE_AVATARS.length - 1)]
    : null;

  const handleTabLayout = useCallback((tabName: string, x: number, width: number) => {
    setTabLayouts(prev => {
      const current = prev[tabName];
      if (current && current.x === x && current.width === width) return prev;
      return { ...prev, [tabName]: { x, width } };
    });
  }, []);

  useEffect(() => {
    if (!activeTab) return;
    const layout = tabLayouts[activeTab];
    if (!layout) return;

    if (!hasMeasuredInitialPillRef.current) {
      pillTranslateX.value = layout.x;
      pillWidth.value = layout.width;
      hasMeasuredInitialPillRef.current = true;
      return;
    }

    pillTranslateX.value = withSpring(layout.x, {
      damping: 18,
      stiffness: 220,
      mass: 0.9,
    });
    pillWidth.value = withSpring(layout.width, {
      damping: 18,
      stiffness: 220,
      mass: 0.9,
    });
  }, [activeTab, pillTranslateX, pillWidth, tabLayouts]);

  const activePillStyle = useAnimatedStyle(() => ({
    width: pillWidth.value,
    transform: [{ translateX: pillTranslateX.value }],
  }));

  return (
    <View style={[styles.container, { paddingBottom: Math.max(insets.bottom, 10) }]}>
      <View style={styles.shell}>
        <BlurView
          tint={isLightAppearance ? 'light' : 'dark'}
          intensity={isLightAppearance ? 78 : 86}
          blurMethod={Platform.OS === 'android' && blurTarget ? 'dimezisBlurViewSdk31Plus' : undefined}
          blurTarget={Platform.OS === 'android' ? blurTarget : undefined}
          style={styles.blurFill}
        />
        <View style={styles.glassTint} pointerEvents="none" />
        <LinearGradient
          colors={isLightAppearance
            ? ['rgba(255,255,255,0.075)', 'rgba(214,191,255,0.03)', 'rgba(255,214,236,0.04)']
            : ['rgba(255,255,255,0.024)', 'rgba(168,159,248,0.02)', 'rgba(255,255,255,0.012)']}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={styles.glassGradient}
          pointerEvents="none"
        />
        <View
          style={[
            styles.glassGlow,
            {
              backgroundColor: isLightAppearance ? 'rgba(255,255,255,0.006)' : 'rgba(255,255,255,0.004)',
            },
          ]}
          pointerEvents="none"
        />
        <View style={styles.inner}>
          {!!activeTab && !!tabLayouts[activeTab] && (
            <Animated.View
              pointerEvents="none"
              style={[
                styles.activePill,
                activePillStyle,
                {
                  backgroundColor: resolvedAppearance === 'light'
                    ? 'rgba(255,255,255,0.68)'
                    : 'rgba(255,255,255,0.08)',
                },
              ]}
            />
          )}
          {visibleTabs.map(tab => {
            const active = activeTab === tab.name;
            const isSettings = tab.name === 'Settings';
            const iconColor = active
              ? (resolvedAppearance === 'light' ? '#111111' : colors.textPrimary)
              : colors.textSecondary;

            return (
              <TouchableOpacity
                key={tab.name}
                style={styles.tab}
                activeOpacity={0.7}
                onLayout={(event) => {
                  const { x, width } = event.nativeEvent.layout;
                  handleTabLayout(tab.name, x, width);
                }}
                onPress={() => {
                  if (onTabPress) {
                    onTabPress(tab.name);
                    return;
                  }
                  navigation.navigate('Main', { screen: tab.name });
                }}
              >
                {isSettings && profileAvatar ? (
                  <Image
                    source={profileAvatar.image}
                    style={{
                      width: active ? avatarSizeActive : avatarSize,
                      height: active ? avatarSizeActive : avatarSize,
                      borderRadius: active ? avatarSizeActive / 2 : avatarSize / 2,
                      ...(active
                        ? { borderWidth: 2, borderColor: resolvedAppearance === 'light' ? '#111111' : colors.accentSoft }
                        : { opacity: 0.7 }),
                    }}
                  />
                ) : (
                  <Ionicons name={active ? tab.iconActive : tab.icon} size={iconSize} color={iconColor} />
                )}

                {showNavLabels && (
                  <Text style={[
                    styles.label,
                    active && styles.labelActive,
                    active && resolvedAppearance === 'light' && { color: '#111111' },
                  ]}>
                    {t(tab.labelKey)}
                  </Text>
                )}
              </TouchableOpacity>
            );
          })}
        </View>
      </View>
    </View>
  );
};
