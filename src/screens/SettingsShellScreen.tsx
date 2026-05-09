import React, { useMemo } from 'react';
import {
  View,
  Text,
  Image,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  StatusBar,
} from 'react-native';
import Constants from 'expo-constants';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { BlurTargetView } from 'expo-blur';
import { StackBottomNav } from '../components/StackBottomNav';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useAddons } from '../context/AddonContext';
import { useDebrid } from '../context/DebridContext';
import { useTrakt } from '../context/TraktContext';
import { useLanguage } from '../context/LanguageContext';
import { PROFILE_AVATARS } from '../utils/profileApi';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

function makeStyles(c: ThemeColors) {
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: c.bg },
    content: { paddingHorizontal: 20 },
    heading: { color: c.textPrimary, fontSize: 30, fontWeight: '900', marginBottom: 8 },
    subheading: { color: c.textSecondary, fontSize: 14, lineHeight: 20, marginBottom: 22 },
    sectionTitle: { color: c.textPrimary, fontSize: 14, fontWeight: '800', letterSpacing: 0.4, marginBottom: 10 },
    card: {
      backgroundColor: c.cardBgElevated ?? c.cardBg,
      borderWidth: 1,
      borderColor: c.border,
      borderRadius: 22,
      overflow: 'hidden',
      marginBottom: 18,
    },
    divider: { height: 1, backgroundColor: c.borderSoft, marginLeft: 58 },
    row: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18, paddingVertical: 16, gap: 12 },
    rowIcon: {
      width: 34,
      height: 34,
      borderRadius: 10,
      justifyContent: 'center',
      alignItems: 'center',
    },
    rowAvatar: {
      width: 34,
      height: 34,
      borderRadius: 17,
    },
    rowInfo: { flex: 1 },
    rowLabel: { color: c.textPrimary, fontSize: 16, fontWeight: '700' },
    rowSubtitle: { color: c.textSecondary, fontSize: 13, marginTop: 3, lineHeight: 18 },
    rowValue: { color: c.textSecondary, fontSize: 13, fontWeight: '600' },
    aboutBlock: { marginBottom: 6, alignItems: 'center' },
    aboutLine: { color: c.textSecondary, fontSize: 13, lineHeight: 20, textAlign: 'center' },
  });
}

function getVisibleIconColor(color: string, resolvedAppearance: 'dark' | 'light', themeId: string, fallback: string) {
  if (resolvedAppearance === 'light' && (themeId === 'monochrome' || color === '#ffffff' || color === '#fff')) {
    return fallback;
  }
  return color;
}

function NavRow({
  icon,
  iconColor,
  label,
  subtitle,
  value,
  onPress,
  avatarSource,
}: {
  icon: IoniconName;
  iconColor: string;
  label: string;
  subtitle: string;
  value?: string;
  onPress?: () => void;
  avatarSource?: any;
}) {
  const { theme: { colors, id }, resolvedAppearance } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const visibleIconColor = getVisibleIconColor(iconColor, resolvedAppearance, id, colors.textPrimary);

  const body = (
    <View style={styles.row}>
      {avatarSource ? (
        <Image source={avatarSource} style={styles.rowAvatar} />
      ) : (
        <View style={[styles.rowIcon, { backgroundColor: `${visibleIconColor}22` }]}>
          <Ionicons name={icon} size={18} color={visibleIconColor} />
        </View>
      )}
      <View style={styles.rowInfo}>
        <Text style={styles.rowLabel}>{label}</Text>
        <Text style={styles.rowSubtitle}>{subtitle}</Text>
      </View>
      {value ? <Text style={styles.rowValue}>{value}</Text> : null}
      {onPress ? <Ionicons name="chevron-forward" size={18} color={colors.placeholder} /> : null}
    </View>
  );

  if (!onPress) return body;
  return <TouchableOpacity onPress={onPress} activeOpacity={0.78}>{body}</TouchableOpacity>;
}

export function SettingsShellScreen({ navigation }: any) {
  const blurTargetRef = React.useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const { user } = useAuth();
  const { profiles, activeProfile } = useProfile();
  const { addons, ultraEntitled, ultraBoostEnabled } = useAddons();
  const { accounts } = useDebrid();
  const { isConnected: traktConnected } = useTrakt();
  const { t } = useLanguage();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const appVersion = Constants.expoConfig?.version ?? '0.0.0';

  const enabledAddonCount = addons.filter(addon => addon.enabled).length + (ultraEntitled && ultraBoostEnabled ? 1 : 0);

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
        <View style={styles.container}>
          <StatusBar
            barStyle={resolvedAppearance === 'light' ? 'dark-content' : 'light-content'}
            translucent
            backgroundColor="transparent"
          />
          <ScrollView
            showsVerticalScrollIndicator={false}
            contentContainerStyle={{
              paddingTop: insets.top + 18,
              paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 18,
            }}
          >
            <View style={styles.content}>
              <Text style={styles.heading}>{t('settings_title')}</Text>
              <Text style={styles.subheading}>{t('settings_shell_subheading')}</Text>

              <Text style={styles.sectionTitle}>{t('settings_profiles_section')}</Text>
              <View style={styles.card}>
                <NavRow
                  icon="swap-horizontal-outline"
                  iconColor={colors.accentSoft}
                  avatarSource={activeProfile ? PROFILE_AVATARS[Math.min(activeProfile.avatarIndex, PROFILE_AVATARS.length - 1)].image : undefined}
                  label={t('settings_switch_profile')}
                  subtitle={activeProfile ? t('settings_switch_profile_current', { name: activeProfile.name }) : t('settings_switch_profile_sub')}
                  onPress={() => navigation.navigate('ProfileSwitcher')}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="people-outline"
                  iconColor="#2563eb"
                  label={t('settings_manage_profiles')}
                  subtitle={t('settings_manage_profiles_count', { n: profiles.length, suffix: profiles.length !== 1 ? 's' : '' })}
                  onPress={() => navigation.navigate('ManageProfiles')}
                />
              </View>

              <Text style={styles.sectionTitle}>{t('settings_preferences_section')}</Text>
              <View style={styles.card}>
                <NavRow
                  icon="settings-outline"
                  iconColor="#64748b"
                  label={t('settings_detail_general_playback')}
                  subtitle={t('settings_general_playback_sub')}
                  onPress={() => navigation.navigate('SettingsDetail', { section: 'general-playback' })}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="color-palette-outline"
                  iconColor="#f59e0b"
                  label={t('settings_detail_home_appearance')}
                  subtitle={t('settings_home_appearance_sub')}
                  onPress={() => navigation.navigate('SettingsDetail', { section: 'home-appearance' })}
                />
              </View>

              <Text style={styles.sectionTitle}>{t('settings_services_section')}</Text>
              <View style={styles.card}>
                <NavRow
                  icon="extension-puzzle-outline"
                  iconColor="#22c55e"
                  label={t('settings_addons_label')}
                  subtitle={t('settings_addons_active_count', { n: enabledAddonCount, suffix: enabledAddonCount !== 1 ? 's' : '' })}
                  onPress={() => navigation.navigate('Addons')}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="cloud-outline"
                  iconColor="#38bdf8"
                  label={t('settings_debrid_services')}
                  subtitle={accounts.length > 0 ? t('settings_debrid_connected_count', { n: accounts.length, suffix: accounts.length !== 1 ? 's' : '' }) : t('settings_debrid_services_sub')}
                  onPress={() => navigation.navigate('Addons', { initialTab: 'debrid' })}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="sync-outline"
                  iconColor="#a78bfa"
                  label={t('settings_trakt')}
                  subtitle={traktConnected ? t('settings_trakt_connected_sub') : t('settings_trakt_disconnected_sub')}
                  onPress={() => navigation.navigate('TraktSettings')}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="tv-outline"
                  iconColor="#f97316"
                  label={t('settings_link_tv')}
                  subtitle={t('settings_link_tv_sub')}
                  onPress={() => navigation.navigate('LinkTv')}
                />
              </View>

              <Text style={styles.sectionTitle}>{t('settings_account_section')}</Text>
              <View style={styles.card}>
                <NavRow
                  icon="person-circle-outline"
                  iconColor={colors.mutedText}
                  label={t('settings_account')}
                  subtitle={user ? (user.email ? `${t('addons_signed_in_as')} ${user.email}` : t('settings_account_signed_in_generic')) : t('settings_account_signed_out_shell')}
                  onPress={() => navigation.navigate(user ? 'SettingsDetail' : 'Auth', user ? { section: 'account-services' } : undefined)}
                />
              </View>

              <View style={styles.aboutBlock}>
                <Text style={styles.aboutLine}>{t('settings_made_with')}</Text>
                <Text style={styles.aboutLine}>{t('settings_version_label', { version: appVersion })}</Text>
              </View>
            </View>
          </ScrollView>
        </View>
      </BlurTargetView>
      <StackBottomNav activeTab="Settings" blurTarget={blurTargetRef} />
    </View>
  );
}
