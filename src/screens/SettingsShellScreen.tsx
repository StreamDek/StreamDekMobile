import React, { useMemo, useState } from 'react';
import {
  View,
  Text,
  Image,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  StatusBar,
  Modal,
  Pressable,
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
type InfoKind = 'about' | 'legal' | null;

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
    modalSub: { color: c.textSecondary, fontSize: 13, lineHeight: 18, marginTop: 6, marginBottom: 14 },
    infoSection: { gap: 8, marginTop: 12 },
    infoHeading: { color: c.textPrimary, fontSize: 14, fontWeight: '800' },
    infoBody: { color: c.textSecondary, fontSize: 13, lineHeight: 19 },
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
  const { t } = useLanguage();
  const { colors } = theme;
  const { user } = useAuth();
  const { profiles, activeProfile } = useProfile();
  const { addons, ultraEntitled, ultraBoostEnabled } = useAddons();
  const { accounts } = useDebrid();
  const { isConnected: traktConnected } = useTrakt();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const [infoModal, setInfoModal] = useState<InfoKind>(null);
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
              <Text style={styles.heading}>Settings</Text>
              <Text style={styles.subheading}>Instant navigation at the top level. Detailed configuration lives in dedicated pages.</Text>

              <Text style={styles.sectionTitle}>Profiles</Text>
              <View style={styles.card}>
                <NavRow
                  icon="swap-horizontal-outline"
                  iconColor={colors.accentSoft}
                  avatarSource={activeProfile ? PROFILE_AVATARS[Math.min(activeProfile.avatarIndex, PROFILE_AVATARS.length - 1)].image : undefined}
                  label="Switch Profile"
                  subtitle={activeProfile ? `Current: ${activeProfile.name}` : 'Open the profile selector immediately'}
                  onPress={() => navigation.navigate('ProfileSwitcher')}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="people-outline"
                  iconColor="#2563eb"
                  label="Manage Profiles"
                  subtitle={`${profiles.length} profile${profiles.length !== 1 ? 's' : ''} available`}
                  onPress={() => navigation.navigate('ManageProfiles')}
                />
              </View>

              <Text style={styles.sectionTitle}>Preferences</Text>
              <View style={styles.card}>
                <NavRow
                  icon="settings-outline"
                  iconColor="#64748b"
                  label="General, Playback and Subtitles"
                  subtitle="Player behavior, quality, language, caching, and advanced app settings."
                  onPress={() => navigation.navigate('SettingsDetail', { section: 'general-playback' })}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="color-palette-outline"
                  iconColor="#f59e0b"
                  label="Home and Appearance"
                  subtitle="Catalog, layout, page style, hero synopsis, and ambient background."
                  onPress={() => navigation.navigate('SettingsDetail', { section: 'home-appearance' })}
                />
              </View>

              <Text style={styles.sectionTitle}>Services</Text>
              <View style={styles.card}>
                <NavRow
                  icon="extension-puzzle-outline"
                  iconColor="#22c55e"
                  label="Add-ons"
                  subtitle={`${enabledAddonCount} source${enabledAddonCount !== 1 ? 's' : ''} active`}
                  onPress={() => navigation.navigate('Addons')}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="cloud-outline"
                  iconColor="#38bdf8"
                  label="Debrid Services"
                  subtitle={accounts.length > 0 ? `${accounts.length} account${accounts.length !== 1 ? 's' : ''} connected` : 'Connect premium cache providers'}
                  onPress={() => navigation.navigate('Addons', { initialTab: 'debrid' })}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="sync-outline"
                  iconColor="#a78bfa"
                  label="Trakt"
                  subtitle={traktConnected ? 'Connected and syncing' : 'Connect Trakt and sync your activity'}
                  onPress={() => navigation.navigate('TraktSettings')}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="tv-outline"
                  iconColor="#f97316"
                  label="Link TV"
                  subtitle="Pair your mobile app with the TV app"
                  onPress={() => navigation.navigate('LinkTv')}
                />
              </View>

              <Text style={styles.sectionTitle}>Account</Text>
              <View style={styles.card}>
                <NavRow
                  icon="person-circle-outline"
                  iconColor={colors.mutedText}
                  label="Account"
                  subtitle={user ? (user.email ? `Signed in as ${user.email}` : 'Signed in · Settings syncing across devices') : 'Sign in to unlock cross-device sync, TV linking, and Trakt'}
                  onPress={() => navigation.navigate(user ? 'SettingsDetail' : 'Auth', user ? { section: 'account-services' } : undefined)}
                />
              </View>

              <Text style={styles.sectionTitle}>About StreamDek</Text>
              <View style={styles.card}>
                <NavRow
                  icon="information-circle-outline"
                  iconColor="#22c55e"
                  label={t('settings_about')}
                  subtitle="App version, build information, and release identity."
                  value={`v${appVersion}`}
                  onPress={() => setInfoModal('about')}
                />
                <View style={styles.divider} />
                <NavRow
                  icon="document-text-outline"
                  iconColor="#f97316"
                  label={t('settings_legal')}
                  subtitle="Terms, privacy, and legal notices for StreamDek."
                  onPress={() => setInfoModal('legal')}
                />
              </View>
            </View>
          </ScrollView>
        </View>
      </BlurTargetView>
      <Modal visible={infoModal !== null} transparent animationType="slide" statusBarTranslucent onRequestClose={() => setInfoModal(null)}>
        <Pressable style={styles.modalBackdrop} onPress={() => setInfoModal(null)}>
          <Pressable style={[styles.modalCard, { paddingBottom: insets.bottom + 16 }]} onPress={() => {}}>
            <Text style={styles.modalTitle}>{infoModal === 'about' ? t('settings_about') : t('settings_legal')}</Text>
            <Text style={styles.modalSub}>
              {infoModal === 'about'
                ? 'Version and release information for this app build.'
                : 'Important information about how StreamDek works and your responsibilities when using third-party sources.'}
            </Text>
            <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 4 }}>
              {infoModal === 'about' ? (
                <View style={styles.infoSection}>
                  <Text style={styles.infoHeading}>{t('settings_version')}</Text>
                  <Text style={styles.infoBody}>{`StreamDek v${appVersion}`}</Text>
                </View>
              ) : null}
              {infoModal === 'legal' ? (
                <>
                  <View style={styles.infoSection}>
                    <Text style={styles.infoHeading}>{t('settings_legal_nature_title')}</Text>
                    <Text style={styles.infoBody}>{t('settings_legal_nature_body')}</Text>
                  </View>
                  <View style={styles.infoSection}>
                    <Text style={styles.infoHeading}>{t('settings_legal_third_party_title')}</Text>
                    <Text style={styles.infoBody}>{t('settings_legal_third_party_body')}</Text>
                  </View>
                  <View style={styles.infoSection}>
                    <Text style={styles.infoHeading}>{t('settings_legal_user_title')}</Text>
                    <Text style={styles.infoBody}>{t('settings_legal_user_body')}</Text>
                  </View>
                  <View style={styles.infoSection}>
                    <Text style={styles.infoHeading}>{t('settings_legal_copyright_title')}</Text>
                    <Text style={styles.infoBody}>{t('settings_legal_copyright_body')}</Text>
                  </View>
                  <View style={styles.infoSection}>
                    <Text style={styles.infoHeading}>{t('settings_legal_warranty_title')}</Text>
                    <Text style={styles.infoBody}>{t('settings_legal_warranty_body')}</Text>
                  </View>
                </>
              ) : null}
            </ScrollView>
          </Pressable>
        </Pressable>
      </Modal>
      <StackBottomNav activeTab="Settings" blurTarget={blurTargetRef} />
    </View>
  );
}
