import React, { useMemo, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, StatusBar, Alert, ActivityIndicator,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useTrakt } from '../context/TraktContext';
import { useAuth } from '../context/AuthContext';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { TraktDeviceAuthModal } from '../components/TraktDeviceAuthModal';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { useLanguage } from '../context/LanguageContext';


type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

const makeStyles = (c: ThemeColors, isLightMonochrome: boolean) => {
  const isLightAppearance = c.bg === '#f2f4f8';
  return StyleSheet.create({
    container:    { flex: 1, backgroundColor: c.bg },
    scroll:       { flex: 1, paddingHorizontal: 20, backgroundColor: c.bg },
    backBtn:      { flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-start', marginBottom: 20 },
    backText:     { color: c.accentSoft, fontSize: 15, fontWeight: '600' },
    heading:      { color: c.textPrimary, fontSize: 28, fontWeight: '900', letterSpacing: 0.5, marginBottom: 6 },
    subheading:   { color: c.subText, fontSize: 14, marginBottom: 28 },
    sectionTitle: {
      color: c.textPrimary, fontSize: 11, fontWeight: '700',
      letterSpacing: 1, textTransform: 'uppercase',
      marginBottom: 10, marginTop: 8, paddingHorizontal: 4,
    },
    card: {
      backgroundColor: c.cardBg, borderRadius: 16,
      borderWidth: 1, borderColor: c.border,
      marginBottom: 20, overflow: 'hidden',
    },
    row:          { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 14, gap: 12 },
    divider:      { height: 1, backgroundColor: c.border, marginLeft: 56 },
    iconWrap:     { width: 36, height: 36, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
    rowInfo:      { flex: 1 },
    rowLabel:     { color: c.textPrimary, fontSize: 14, fontWeight: '600' },
    rowSub:       { color: isLightAppearance ? c.textPrimary : c.mutedText, fontSize: 12, marginTop: 2, fontWeight: isLightAppearance ? '600' : '400' },
    // Connected badge
    connectedBadge: {
      flexDirection: 'row', alignItems: 'center', gap: 5,
      backgroundColor: '#00c85320', borderRadius: 20,
      paddingHorizontal: 10, paddingVertical: 4,
    },
    connectedText:  { color: '#00c853', fontSize: 11, fontWeight: '700' },
    // Action buttons
    connectBtn: {
      margin: 16, borderRadius: 999,
      backgroundColor: isLightMonochrome ? c.accent : (isLightAppearance ? '#1877F2' : c.accent), paddingVertical: 13, alignItems: 'center',
      flexDirection: 'row', justifyContent: 'center', gap: 8,
      borderWidth: isLightMonochrome ? 1 : 0,
      borderColor: isLightMonochrome ? 'rgba(17,24,39,0.18)' : 'transparent',
    },
    connectText:    { color: isLightMonochrome ? '#111111' : (isLightAppearance ? '#ffffff' : c.buttonText), fontSize: 14, fontWeight: '700' },
    disconnectBtn:  {
      margin: 16, borderRadius: 999, borderWidth: 0,
      backgroundColor: '#c0392b', paddingVertical: 13, alignItems: 'center',
    },
    disconnectText: { color: '#ffffff', fontSize: 14, fontWeight: '700' },
    // Features
    featureItem:  { flexDirection: 'row', alignItems: 'flex-start', gap: 12, paddingHorizontal: 16, paddingVertical: 12 },
    featureIcon:  { width: 32, height: 32, borderRadius: 8, justifyContent: 'center', alignItems: 'center', marginTop: 2 },
    featureInfo:  { flex: 1 },
    featureTitle: { color: c.textPrimary, fontSize: 14, fontWeight: '600', marginBottom: 2 },
    featureDesc:  { color: c.mutedText, fontSize: 12, lineHeight: 18 },
    checkmark:    { color: '#00c853', fontSize: 12, fontWeight: '700' },
    // Info card
    infoCard:     { padding: 16, flexDirection: 'row', gap: 12, alignItems: 'flex-start' },
    infoTitle:    { color: c.textPrimary, fontSize: 13, fontWeight: '600', marginBottom: 4 },
    infoBody:     { color: c.subText, fontSize: 12, lineHeight: 18 },
    notSignedIn:  { color: c.mutedText, fontSize: 13, textAlign: 'center', paddingHorizontal: 16, paddingBottom: 16 },
  });
};

const FEATURES: { icon: IoniconName; color: string; titleKey: string; descKey: string }[] = [
  { icon: 'time-outline',         color: '#6c63ff', titleKey: 'trakt_history_title',   descKey: 'trakt_history_desc' },
  { icon: 'bookmark-outline',     color: '#00b4d8', titleKey: 'trakt_watchlist_title', descKey: 'trakt_watchlist_desc' },
  { icon: 'star-outline',         color: '#ffd740', titleKey: 'trakt_ratings_title',   descKey: 'trakt_ratings_desc' },
  { icon: 'bulb-outline',         color: '#f97316', titleKey: 'trakt_recs_title',      descKey: 'trakt_recs_desc' },
  { icon: 'radio-outline',        color: '#ed1c24', titleKey: 'trakt_scrobble_title',  descKey: 'trakt_scrobble_desc' },
  { icon: 'list-outline',         color: '#9b5de5', titleKey: 'trakt_lists_title',     descKey: 'trakt_lists_desc' },
];

export const TraktSettingsScreen = ({ navigation }: any) => {
  const insets  = useSafeAreaInsets();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const isLightMonochrome = resolvedAppearance === 'light' && theme.id === 'monochrome';
  const styles  = useMemo(() => makeStyles(colors, isLightMonochrome), [colors, isLightMonochrome]);
  const isLightAppearance = colors.bg === '#f4f6fb';
  const { isConnected, traktUsername, isLoading, disconnect, checkStatus } = useTrakt();
  const { user } = useAuth();
  const { t } = useLanguage();

  const [showModal,    setShowModal]    = useState(false);
  const [disconnecting, setDisconnecting] = useState(false);

  // Custom themed alert state
  const [alert, setAlert] = useState<{
    visible: boolean;
    title: string;
    message: string;
    confirmLabel?: string;
    cancelLabel?: string;
    onConfirm?: () => void;
    infoOnly?: boolean;
    icon?: any;
    variant?: 'accent' | 'destructive';
  }>({
    visible: false, title: '', message: '',
  });

  const showAlert = (title: string, message: string, opts: Partial<typeof alert> = {}) => {
    setAlert({
      visible: true,
      title,
      message,
      confirmLabel: t('common_ok'),
      infoOnly: true,
      ...opts,
    });
  };

  const closeAlert = () => setAlert(prev => ({ ...prev, visible: false }));


  const handleDisconnect = () => {
    showAlert(
      t('trakt_unlink_title'),
      t('trakt_unlink_confirm'),
      {
        infoOnly: false,
        confirmLabel: t('trakt_disconnect'),
        cancelLabel: t('common_cancel'),
        variant: 'destructive',
        icon: 'log-out-outline',
        onConfirm: async () => {
          setDisconnecting(true);
          await disconnect();
          setDisconnecting(false);
        },
      }
    );
  };


  return (
    <View style={styles.container}>
        <StatusBar barStyle={resolvedAppearance === 'light' ? 'dark-content' : 'light-content'} translucent backgroundColor="transparent" />

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={{
          paddingTop: insets.top + 16,
          paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24,
        }}
        showsVerticalScrollIndicator={false}
      >
        <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()}>
          <Ionicons name="chevron-back" size={20} color={colors.accentSoft} />
          <Text style={styles.backText}>{t('settings_title')}</Text>
        </TouchableOpacity>

        <Text style={styles.heading}>{t('settings_trakt')}</Text>
        <Text style={styles.subheading}>{t('trakt_page_subtitle')}</Text>

        {/* ── Account ─────────────────────────────────────────────────────── */}
        <Text style={styles.sectionTitle}>{t('trakt_account_section')}</Text>
        <View style={styles.card}>
          {isLoading ? (
            <View style={[styles.row, { justifyContent: 'center', paddingVertical: 24 }]}>
              <ActivityIndicator color={colors.accent} />
            </View>
          ) : isConnected ? (
            <>
              <View style={styles.row}>
                <View style={[styles.iconWrap, { backgroundColor: isLightAppearance ? 'rgba(192,57,43,0.10)' : '#ed1c2420' }]}>
                  <Text style={{ fontSize: 18 }}>🎬</Text>
                </View>
                <View style={styles.rowInfo}>
                  <Text style={styles.rowLabel}>{traktUsername ?? t('trakt_account_default')}</Text>
                  <Text style={styles.rowSub}>trakt.tv/{traktUsername ?? '…'}</Text>
                </View>
                <View style={styles.connectedBadge}>
                  <Ionicons name="checkmark-circle" size={13} color="#00c853" />
                  <Text style={styles.connectedText}>{t('trakt_active')}</Text>
                </View>
              </View>

              <TouchableOpacity
                style={styles.disconnectBtn}
                onPress={handleDisconnect}
                disabled={disconnecting}
                activeOpacity={0.7}
              >
                {disconnecting
                  ? <ActivityIndicator size="small" color="#ffffff" />
                  : <Text style={styles.disconnectText}>{t('trakt_disconnect')}</Text>
                }
              </TouchableOpacity>
            </>
          ) : (
            <>
              <View style={styles.row}>
                <View style={[styles.iconWrap, { backgroundColor: isLightAppearance ? 'rgba(192,57,43,0.10)' : '#ed1c2420' }]}>
                  <Text style={{ fontSize: 18 }}>🎬</Text>
                </View>
                <View style={styles.rowInfo}>
                  <Text style={styles.rowLabel}>{t('trakt_not_connected')}</Text>
                  <Text style={styles.rowSub}>{t('trakt_link_msg')}</Text>
                </View>
              </View>

              {!user ? (
                <Text style={styles.notSignedIn}>{t('trakt_sign_in_required')}</Text>
              ) : (
                  <TouchableOpacity
                    style={styles.connectBtn}
                    onPress={() => setShowModal(true)}
                    activeOpacity={0.85}
                  >
                  <Ionicons name="link-outline" size={18} color={isLightMonochrome ? '#111111' : '#ffffff'} />
                  <Text style={styles.connectText}>{t('trakt_connect_account')}</Text>
                </TouchableOpacity>
              )}
            </>
          )}
        </View>

        {/* ── Features ────────────────────────────────────────────────────── */}
        <Text style={styles.sectionTitle}>{t('trakt_what_you_get')}</Text>
        <View style={styles.card}>
          {FEATURES.map((f, i) => (
            <React.Fragment key={f.titleKey}>
              <View style={styles.featureItem}>
                <View style={[styles.featureIcon, { backgroundColor: f.color + '22' }]}>
                  <Ionicons name={f.icon} size={18} color={f.color} />
                </View>
                <View style={styles.featureInfo}>
                  <Text style={styles.featureTitle}>
                    {t(f.titleKey as any)}
                    {isConnected && <Text style={styles.checkmark}>  ✓</Text>}
                  </Text>
                  <Text style={styles.featureDesc}>{t(f.descKey as any)}</Text>
                </View>
              </View>
              {i < FEATURES.length - 1 && <View style={styles.divider} />}
            </React.Fragment>
          ))}
        </View>

        {/* ── TV / Fire TV note ────────────────────────────────────────────── */}
        <View style={styles.card}>
          <View style={styles.infoCard}>
            <Ionicons name="tv-outline" size={20} color={colors.accent} style={{ marginTop: 1 }} />
            <View style={{ flex: 1 }}>
              <Text style={styles.infoTitle}>{t('trakt_tv_note_title')}</Text>
              <Text style={styles.infoBody}>{t('trakt_tv_note_desc')}</Text>
            </View>
          </View>
        </View>
      </ScrollView>

      <TraktDeviceAuthModal
        visible={showModal}
        onSuccess={() => { setShowModal(false); checkStatus(); }}
        onDismiss={() => setShowModal(false)}
      />

        <ConfirmSheet
      visible={alert.visible}
      title={alert.title}
      message={alert.message}
        confirmLabel={alert.confirmLabel ?? t('common_ok')}
      cancelLabel={alert.cancelLabel}
        onConfirm={alert.onConfirm || (() => {})}
        onClose={closeAlert}
        infoOnly={alert.infoOnly}
        icon={alert.icon}
        variant={alert.variant}
      />
    </View>

  );
};


