import React, { useState, useMemo, useCallback } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity,
  StatusBar, Modal, TextInput, ActivityIndicator,
  Pressable, KeyboardAvoidingView, Platform, RefreshControl,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useAddons, InstalledAddon } from '../context/AddonContext';
import { useDebrid, DEBRID_PROVIDERS, DebridProviderName, DebridService } from '../context/DebridContext';
import { useLanguage, TranslationKey } from '../context/LanguageContext';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { AppleToggle } from '../components/AppleToggle';
import { ConfirmSheet } from '../components/ConfirmSheet';


type Tab = 'addons' | 'debrid';

// ── Styles ───────────────────────────────────────────────────────────────────

const makeStyles = (c: ThemeColors) => {
  const isLightAppearance = c.bg === '#f2f4f8';
  const lightCardBorder = 'rgba(17,24,39,0.12)';
  return StyleSheet.create({
  container:    { flex: 1, backgroundColor: c.bg },
  backBtn:      { flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-start', marginBottom: 20 },
  backText:     { color: c.accentSoft, fontSize: 15, fontWeight: '600' },
  heading:      { color: c.textPrimary, fontSize: 28, fontWeight: '900', letterSpacing: 0.5, marginBottom: 6 },
  subheading:   { color: c.subText, fontSize: 14, marginBottom: 20 },
  tabRow:       { flexDirection: 'row', gap: 8, marginBottom: 20 },
  tabPill: {
    paddingHorizontal: 16, paddingVertical: 7, borderRadius: 20,
    borderWidth: 1, borderColor: c.border, backgroundColor: c.cardBg,
  },
  tabPillOn:    { borderColor: isLightAppearance ? 'rgba(17,24,39,0.28)' : c.accent, backgroundColor: isLightAppearance ? 'rgba(17,24,39,0.10)' : c.accent + '22' },
  tabText:      { fontSize: 13, fontWeight: '600', color: c.mutedText },
  tabTextOn:    { color: c.accentSoft, fontWeight: '700' },
  content:      { paddingHorizontal: 16 },
  // ── Addon card ──
  addonCard: {
    backgroundColor: c.cardBg, borderRadius: 14, borderWidth: 1,
    borderColor: c.border, padding: 14, marginBottom: 10,
  },
  addonTop:     { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  addonIconWrap: {
    width: 44, height: 44, borderRadius: 10,
    backgroundColor: c.inputBg, justifyContent: 'center', alignItems: 'center',
    borderWidth: 1, borderColor: c.inputBorder,
  },
  addonInfo:    { flex: 1 },
  addonName:    { color: c.textPrimary, fontSize: 14, fontWeight: '800', marginBottom: 2 },
  addonVersion: { color: c.mutedText, fontSize: 11, marginBottom: 4 },
  addonDesc:    { color: c.subText, fontSize: 12, lineHeight: 17 },
  addonBottom:  { flexDirection: 'row', gap: 6, marginTop: 10, flexWrap: 'wrap', alignItems: 'center' },
  tag: {
    paddingHorizontal: 8, paddingVertical: 3, borderRadius: 20,
    backgroundColor: isLightAppearance ? 'rgba(17,24,39,0.08)' : c.inputBg, borderWidth: 1, borderColor: isLightAppearance ? 'rgba(17,24,39,0.18)' : c.inputBorder,
  },
  tagText:      { color: c.mutedText, fontSize: 10, fontWeight: '600' },
  removeBtn:    { marginLeft: 'auto' as any, padding: 4 },
  // ── Debrid provider card ──
  debridCard: {
    backgroundColor: isLightAppearance ? '#ffffff' : c.cardBg, borderRadius: 14, borderWidth: 1,
    borderColor: isLightAppearance ? lightCardBorder : c.border, padding: 16, marginBottom: 10,
    shadowColor: isLightAppearance ? '#000' : 'transparent',
    shadowOpacity: isLightAppearance ? 0.05 : 0,
    shadowRadius: isLightAppearance ? 6 : 0,
    shadowOffset: isLightAppearance ? { width: 0, height: 2 } : { width: 0, height: 0 },
    elevation: isLightAppearance ? 2 : 0,
  },
  debridTop:    { flexDirection: 'row', alignItems: 'center', gap: 12 },
  debridDot:    { width: 12, height: 12, borderRadius: 6 },
  debridLabel:  { flex: 1, color: c.textPrimary, fontSize: 15, fontWeight: '800' },
  debridStatus: { fontSize: 11, fontWeight: '700', paddingHorizontal: 8, paddingVertical: 3, borderRadius: 20 },
  debridDesc:   { color: c.subText, fontSize: 12, marginTop: 8, lineHeight: 17 },
  debridUser:   { color: c.accentSoft, fontSize: 12, fontWeight: '700', marginTop: 4 },
  debridActions:{ flexDirection: 'row', gap: 8, marginTop: 12 },
  // Reorder buttons
  reorderBtns:  { flexDirection: 'column', gap: 2, marginLeft: 4 },
  reorderBtn: {
    width: 28, height: 28, borderRadius: 8,
    backgroundColor: c.inputBg, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center',
  },
  reorderBtnDisabled: { opacity: 0.3 },
  connectBtn: {
    flex: 1, padding: 10, borderRadius: 999, alignItems: 'center',
    backgroundColor: c.accent,
    borderWidth: isLightAppearance ? 1 : 0,
    borderColor: isLightAppearance ? c.accentSoft : 'transparent',
  },
  connectBtnText: { color: c.buttonText, fontSize: 13, fontWeight: '800' },
  disconnectBtn: {
    flex: 1, padding: 10, borderRadius: 999, alignItems: 'center',
    backgroundColor: '#c0392b', borderWidth: 0,
  },
  disconnectBtnText: { color: '#ffffff', fontSize: 13, fontWeight: '700' },
  ultraCard: {
    backgroundColor: '#c0392b',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.20)',
    padding: 16,
    marginBottom: 16,
  },
  ultraTop: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  ultraIcon: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.22)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  ultraInfo: { flex: 1 },
  ultraTitle: { color: '#ffffff', fontSize: 16, fontWeight: '900' },
  ultraSubtitle: { color: 'rgba(255,255,255,0.86)', fontSize: 12, lineHeight: 17, marginTop: 4, fontWeight: '600' },
  // ── Install FAB ──
  fab: {
    position: 'absolute', right: 16, bottom: BOTTOM_NAV_HEIGHT + 16,
    width: 52, height: 52, borderRadius: 26,
    backgroundColor: c.accent,
    borderWidth: isLightAppearance ? 1 : 0,
    borderColor: isLightAppearance ? c.accentSoft : 'transparent', justifyContent: 'center', alignItems: 'center',
    shadowColor: c.accent, shadowOpacity: 0.5, shadowRadius: 8, shadowOffset: { width: 0, height: 4 },
    elevation: 8,
  },
  // ── Empty state ──
  empty:        { alignItems: 'center', paddingTop: 40, paddingHorizontal: 24 },
  emptyIcon:    {
    width: 72, height: 72, borderRadius: 36,
    backgroundColor: c.cardBg, borderWidth: 1, borderColor: c.border,
    justifyContent: 'center', alignItems: 'center', marginBottom: 16,
  },
  emptyTitle:   { color: c.textPrimary, fontSize: 18, fontWeight: '700', marginBottom: 8, textAlign: 'center' },
  emptyDesc:    { color: c.textSecondary, fontSize: 13, textAlign: 'center', lineHeight: 20 },
  // ── Modal ──
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.7)', justifyContent: 'flex-end' },
  modalCard: {
    backgroundColor: c.cardBg, borderTopLeftRadius: 20, borderTopRightRadius: 20,
    padding: 24, paddingBottom: 40, borderWidth: 1, borderColor: c.border,
  },
  modalHandle:  { width: 36, height: 4, borderRadius: 2, backgroundColor: c.border, alignSelf: 'center', marginBottom: 20 },
  modalTitle:   { color: c.textPrimary, fontSize: 18, fontWeight: '900', marginBottom: 6 },
  modalSubtitle:{ color: c.subText, fontSize: 13, marginBottom: 20, lineHeight: 18 },
  modalLink:    { color: c.accentSoft, textDecorationLine: 'underline' },
  modalInput: {
    backgroundColor: c.inputBg, borderRadius: 12, padding: 14,
    color: c.textPrimary, fontSize: 14, borderWidth: 1, borderColor: c.inputBorder, marginBottom: 16,
  },
  modalBtn: {
    backgroundColor: c.accent,
    borderWidth: isLightAppearance ? 1 : 0,
    borderColor: isLightAppearance ? c.accentSoft : 'transparent', padding: 15, borderRadius: 999, alignItems: 'center', marginBottom: 10,
  },
  modalBtnText: { color: c.buttonText, fontSize: 15, fontWeight: '800' },
  modalBtnSecondary: {
    backgroundColor: '#c0392b', padding: 15, borderRadius: 999, alignItems: 'center',
    borderWidth: 0,
  },
  modalBtnSecondaryText: { color: '#ffffff', fontSize: 14, fontWeight: '700' },
  errorText:    { color: '#c0392b', fontSize: 13, marginBottom: 12, textAlign: 'center' },
  // ── Confirmation modals (Centered) ──
  confirmBackdrop: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.75)',
    justifyContent: 'center', alignItems: 'center', paddingHorizontal: 32,
  },
  confirmCard: {
    backgroundColor: c.cardBg, borderRadius: 20, padding: 28,
    borderWidth: 1, borderColor: c.border, width: '100%', alignItems: 'center',
  },
  confirmIconWrap: {
    width: 56, height: 56, borderRadius: 28, alignSelf: 'center', marginBottom: 16,
    backgroundColor: '#c0392b18', borderWidth: 1, borderColor: '#c0392b33',
    justifyContent: 'center', alignItems: 'center',
  },
  confirmTitle: { color: c.textPrimary, fontSize: 17, fontWeight: '900', textAlign: 'center', marginBottom: 8 },
  confirmDesc:  { color: c.subText, fontSize: 13, textAlign: 'center', lineHeight: 20, marginBottom: 24 },
  confirmHighlight: { color: c.accentSoft, fontWeight: '700' },
  confirmBtnRow: { flexDirection: 'row', gap: 12, width: '100%' },
  confirmCancel: {
    flex: 1, paddingVertical: 13, borderRadius: 12, alignItems: 'center',
    backgroundColor: c.inputBg, borderWidth: 1, borderColor: c.border,
  },
  confirmCancelText: { color: c.accentSoft, fontSize: 14, fontWeight: '700' },
  confirmRemove: {
    flex: 1, paddingVertical: 13, borderRadius: 12, alignItems: 'center',
    backgroundColor: '#c0392b18', borderWidth: 1, borderColor: '#c0392b66',
  },
  confirmRemoveText: { color: '#c0392b', fontSize: 14, fontWeight: '800' },
  });
};

// ── Main Screen ───────────────────────────────────────────────────────────────

export const AddonsScreen = ({ navigation, route }: any) => {
  const insets = useSafeAreaInsets();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const { t } = useLanguage();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const isLightMonochrome = resolvedAppearance === 'light' && theme.id === 'monochrome';

  const {
    addons,
    isLoading: addonsLoading,
    ultraEntitled,
    ultraBoostEnabled,
    setUltraBoostEnabled,
    refreshUltraEntitlement,
    installAddon,
    uninstallAddon,
    toggleAddon,
    reorderAddons,
    refreshAddons,
  } = useAddons();
  const { accounts, isLoading: debridLoading, addAccount, removeAccount, reorderAccounts, refreshAccounts } = useDebrid();

  const [refreshing, setRefreshing] = useState(false);
  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    await Promise.all([refreshAddons(), refreshAccounts(), refreshUltraEntitlement()]);
    setRefreshing(false);
  }, [refreshAddons, refreshAccounts, refreshUltraEntitlement]);

  const [tab, setTab] = useState<Tab>(route?.params?.initialTab === 'debrid' ? 'debrid' : 'addons');

  // Install addon modal
  const [installModal, setInstallModal] = useState(false);
  const [installUrl, setInstallUrl]     = useState('');
  const [installing, setInstalling]     = useState(false);
  const [installError, setInstallError] = useState('');

  // Connect debrid modal
  const [debridModal, setDebridModal]       = useState<DebridProviderName | null>(null);
  const [debridKey, setDebridKey]           = useState('');
  const [debridConnecting, setDebridConnecting] = useState(false);
  const [debridError, setDebridError]       = useState('');

  // Disconnect confirmation modal
  const [disconnectTarget, setDisconnectTarget] = useState<{ name: DebridProviderName; label: string } | null>(null);
  const [disconnecting, setDisconnecting]       = useState(false);

  // Remove addon confirmation modal
  const [removeAddonTarget, setRemoveAddonTarget] = useState<InstalledAddon | null>(null);
  const [removingAddon, setRemovingAddon]         = useState(false);

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
    visible: false, title: '', message: '', confirmLabel: t('common_ok'), infoOnly: true,
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


  const handleInstall = useCallback(async () => {
    setInstallError('');
    setInstalling(true);
    const result = await installAddon(installUrl.trim());
    setInstalling(false);
    if (result.success) {
      setInstallUrl('');
      setInstallModal(false);
    } else {
      setInstallError(result.error ?? 'Failed to install addon');
    }
  }, [installUrl, installAddon]);

  const handleRemoveAddon = useCallback((addon: InstalledAddon) => {
    setRemoveAddonTarget(addon);
  }, []);

  const confirmRemoveAddon = useCallback(async () => {
    if (!removeAddonTarget) return;
    setRemovingAddon(true);
    await uninstallAddon(removeAddonTarget.id);
    setRemovingAddon(false);
    setRemoveAddonTarget(null);
  }, [removeAddonTarget, uninstallAddon]);

  const handleConnect = useCallback(async () => {
    if (!debridModal || !debridKey.trim()) return;
    setDebridError('');
    setDebridConnecting(true);
    const result = await addAccount(debridModal, debridKey.trim());
    setDebridConnecting(false);
    if (result.success) {
      setDebridKey('');
      setDebridModal(null);
    } else {
      setDebridError(result.error ?? t('error_add_account_failed'));
    }
  }, [debridModal, debridKey, addAccount]);

  const handleDisconnect = useCallback((providerName: DebridProviderName, label: string) => {
    setDisconnectTarget({ name: providerName, label });
  }, []);

  const confirmDisconnect = useCallback(async () => {
    if (!disconnectTarget) return;
    setDisconnecting(true);
    const ok = await removeAccount(disconnectTarget.name);
    setDisconnecting(false);
    if (ok) {
      setDisconnectTarget(null);
    } else {
      setDisconnectTarget(null);
      showAlert(t('common_error'), t('addons_disconnect_failed'), { variant: 'destructive', icon: 'alert-circle-outline' });
    }

  }, [disconnectTarget, removeAccount]);

  // Build sorted provider list: connected accounts by priority, then disconnected
  const sortedProviders = useMemo(() => {
    const connected = DEBRID_PROVIDERS
      .filter(p => accounts.some(a => a.provider === p.name))
      .sort((a, b) => {
        const pa = accounts.find(ac => ac.provider === a.name)?.priority ?? 99;
        const pb = accounts.find(ac => ac.provider === b.name)?.priority ?? 99;
        return pa - pb;
      });
    const disconnected = DEBRID_PROVIDERS.filter(p => !accounts.some(a => a.provider === p.name));
    return [...connected, ...disconnected];
  }, [accounts]);

  const handleMoveUp = useCallback((index: number) => {
    if (index === 0) return;
    const current = sortedProviders[index];
    if (!current || current.kind !== 'account') return;
    const currentName = current.name as DebridProviderName;
    const connectedProviders = sortedProviders
      .filter((p): p is typeof p & { name: DebridProviderName } => p.kind === 'account' && accounts.some(a => a.provider === p.name))
      .map(p => p.name);
    const cardIndexInConnected = connectedProviders.indexOf(currentName);
    if (cardIndexInConnected <= 0) return;
    const newOrder = [...connectedProviders];
    [newOrder[cardIndexInConnected - 1], newOrder[cardIndexInConnected]] =
      [newOrder[cardIndexInConnected], newOrder[cardIndexInConnected - 1]];
    reorderAccounts(newOrder);
  }, [sortedProviders, accounts, reorderAccounts]);

  const handleMoveDown = useCallback((index: number) => {
    const current = sortedProviders[index];
    if (!current || current.kind !== 'account') return;
    const currentName = current.name as DebridProviderName;
    const connectedProviders = sortedProviders
      .filter((p): p is typeof p & { name: DebridProviderName } => p.kind === 'account' && accounts.some(a => a.provider === p.name))
      .map(p => p.name);
    const cardIndexInConnected = connectedProviders.indexOf(currentName);
    if (cardIndexInConnected < 0 || cardIndexInConnected >= connectedProviders.length - 1) return;
    const newOrder = [...connectedProviders];
    [newOrder[cardIndexInConnected], newOrder[cardIndexInConnected + 1]] =
      [newOrder[cardIndexInConnected + 1], newOrder[cardIndexInConnected]];
    reorderAccounts(newOrder);
  }, [sortedProviders, accounts, reorderAccounts]);

  const handleAddonMoveUp = useCallback((index: number) => {
    if (index === 0) return;
    const ids = addons.map(a => a.id);
    [ids[index - 1], ids[index]] = [ids[index], ids[index - 1]];
    reorderAddons(ids);
  }, [addons, reorderAddons]);

  const handleAddonMoveDown = useCallback((index: number) => {
    if (index >= addons.length - 1) return;
    const ids = addons.map(a => a.id);
    [ids[index], ids[index + 1]] = [ids[index + 1], ids[index]];
    reorderAddons(ids);
  }, [addons, reorderAddons]);

  const selectedProvider = DEBRID_PROVIDERS.find(p => p.name === debridModal);

  return (
    <View style={styles.container}>
      <StatusBar barStyle={resolvedAppearance === 'light' ? 'dark-content' : 'light-content'} translucent backgroundColor="transparent" />

      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={[styles.content, {
          paddingTop: insets.top + 16,
          paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 80,
        }]}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} tintColor={colors.accent} />}
      >
        {/* ── Header ── */}
        <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <Ionicons name="chevron-back" size={20} color={colors.accentSoft} />
          <Text style={styles.backText}>Settings</Text>
        </TouchableOpacity>
        <Text style={styles.heading}>{t('addons_title')}</Text>
        <Text style={styles.subheading}>Manage your streaming sources and Debrid services</Text>

        {/* ── Tabs ── */}
        <View style={styles.tabRow}>
          {([
            { key: 'addons', label: `${t('addons_addons')}${addons.length > 0 ? ` (${addons.length})` : ''}` },
            { key: 'debrid', label: `${t('addons_debrid')}${accounts.length > 0 ? ` (${accounts.length})` : ''}` },
          ] as { key: Tab; label: string }[]).map(item => (
            <TouchableOpacity
              key={item.key}
              style={[styles.tabPill, tab === item.key && styles.tabPillOn]}
              onPress={() => setTab(item.key)}
              activeOpacity={0.75}
            >
              <Text style={[styles.tabText, tab === item.key && styles.tabTextOn]}>{item.label}</Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* ── Content ── */}
        {addonsLoading || debridLoading ? (
          <View style={{ alignItems: 'center', paddingTop: 48 }}>
            <ActivityIndicator color={colors.accent} size="large" />
          </View>
        ) : tab === 'addons' ? (
          <>
            {ultraEntitled ? (
              <UltraBoostCard
                enabled={ultraBoostEnabled}
                onToggle={value => { void setUltraBoostEnabled(value); }}
                styles={styles}
              />
            ) : null}
            {addons.length === 0 ? (
              <View style={styles.empty}>
                <View style={styles.emptyIcon}>
                  <Ionicons name="extension-puzzle-outline" size={32} color={colors.placeholder} />
                </View>
                <Text style={styles.emptyTitle}>{t('addons_no_addons')}</Text>
                <Text style={styles.emptyDesc}>{t('addons_empty_desc')}</Text>
              </View>
            ) : (
              addons.map((addon, index) => (
                <AddonCard
                  key={addon.id}
                  addon={addon}
                  styles={styles}
                  colors={colors}
                  t={t}
                  canMoveUp={index > 0}
                  canMoveDown={index < addons.length - 1}
                  onToggle={enabled => toggleAddon(addon.id, enabled)}
                  onRemove={() => handleRemoveAddon(addon)}
                  onMoveUp={() => handleAddonMoveUp(index)}
                  onMoveDown={() => handleAddonMoveDown(index)}
                />
              ))
            )}
          </>
        ) : (
          <>
          <Text style={{ color: colors.subText, fontSize: 13, lineHeight: 20, marginBottom: 16 }}>
            {t('addons_debrid_desc')}
            {accounts.length > 1 ? t('addons_debrid_priority') : ''}
          </Text>
          {sortedProviders.map((provider, index) => {
            const account = accounts.find(a => a.provider === provider.name);
            const isConnected = !!account;
            const connectedCount = accounts.length;
            // Position within connected accounts only
            const connectedIndex = sortedProviders
              .slice(0, index + 1)
              .filter(p => accounts.some(a => a.provider === p.name))
              .length - 1;
            return (
              <DebridCard
                key={provider.name}
                provider={provider}
                account={account}
                styles={styles}
                colors={colors}
                t={t}
                isLightMonochrome={isLightMonochrome}
                canMoveUp={isConnected && connectedIndex > 0}
                canMoveDown={isConnected && connectedIndex < connectedCount - 1}
                onConnect={() => { setDebridKey(''); setDebridError(''); setDebridModal(provider.kind === 'account' ? provider.name as DebridProviderName : null); }}
                onDisconnect={() => handleDisconnect(provider.name as DebridProviderName, provider.label)}
                onMoveUp={() => handleMoveUp(index)}
                onMoveDown={() => handleMoveDown(index)}
              />
            );
          })}
          </>
        )}
      </ScrollView>

      {/* ── FAB: install addon ── */}
      {tab === 'addons' && (
        <TouchableOpacity
          style={styles.fab}
          onPress={() => { setInstallUrl(''); setInstallError(''); setInstallModal(true); }}
          activeOpacity={0.85}
        >
          <Ionicons name="add" size={28} color={colors.buttonText} />
        </TouchableOpacity>
      )}

      {/* ── Install Addon Modal ── */}
      <Modal visible={installModal} transparent animationType="slide">
        <KeyboardAvoidingView
          style={styles.modalBackdrop}
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          keyboardVerticalOffset={insets.top}
        >
          <Pressable style={styles.modalBackdrop} onPress={() => setInstallModal(false)}>
            <Pressable style={styles.modalCard} onPress={() => {}}>
              <View style={styles.modalHandle} />
              <Text style={styles.modalTitle}>{t('addons_install')}</Text>
              <Text style={styles.modalSubtitle}>
                {t('addons_install_sub')}
              </Text>
              {installError ? <Text style={styles.errorText}>{installError}</Text> : null}
              <TextInput
                style={styles.modalInput}
                placeholder="https://addon-url.com/manifest.json or stremio://..."
                placeholderTextColor={colors.placeholder}
                value={installUrl}
                onChangeText={setInstallUrl}
                autoCapitalize="none"
                autoCorrect={false}
                keyboardType="url"
                returnKeyType="done"
              />
              <TouchableOpacity style={styles.modalBtn} onPress={handleInstall} disabled={installing || !installUrl.trim()}>
                {installing
                  ? <ActivityIndicator color={colors.buttonText} />
                  : <Text style={styles.modalBtnText}>{t('addons_install_btn')}</Text>
                }
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalBtnSecondary} onPress={() => setInstallModal(false)}>
                  <Text style={styles.modalBtnSecondaryText}>{t('common_cancel')}</Text>
              </TouchableOpacity>
            </Pressable>
          </Pressable>
        </KeyboardAvoidingView>
      </Modal>

      {/* ── Connect Debrid Modal ── */}
      <Modal visible={!!debridModal} transparent animationType="slide">
        <KeyboardAvoidingView
          style={styles.modalBackdrop}
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          keyboardVerticalOffset={insets.top}
        >
          <Pressable style={styles.modalBackdrop} onPress={() => setDebridModal(null)}>
            <Pressable style={styles.modalCard} onPress={() => {}}>
              <View style={styles.modalHandle} />
              <Text style={styles.modalTitle}>{t('addons_connect_provider', { name: selectedProvider?.label ?? '' })}</Text>
              <Text style={styles.modalSubtitle}>
                {t('media_sign_in_unlock')}
                {'\n'}{t('addons_secure_note')}
              </Text>
              {debridError ? <Text style={styles.errorText}>{debridError}</Text> : null}
              <TextInput
                style={styles.modalInput}
                placeholder={t('addons_api_key_placeholder')}
                placeholderTextColor={colors.placeholder}
                value={debridKey}
                onChangeText={setDebridKey}
                autoCapitalize="none"
                autoCorrect={false}
                secureTextEntry
                returnKeyType="done"
              />
              <TouchableOpacity style={styles.modalBtn} onPress={handleConnect} disabled={debridConnecting || !debridKey.trim()}>
                {debridConnecting
                   ? <ActivityIndicator color={colors.buttonText} />
                   : <Text style={styles.modalBtnText}>{t('addons_connect')}</Text>
                 }
               </TouchableOpacity>
               <TouchableOpacity style={styles.modalBtnSecondary} onPress={() => setDebridModal(null)}>
                 <Text style={styles.modalBtnSecondaryText}>{t('common_cancel')}</Text>
               </TouchableOpacity>
            </Pressable>
          </Pressable>
        </KeyboardAvoidingView>
      </Modal>

      {/* ── Disconnect Confirmation Modal ── */}
      <Modal visible={!!disconnectTarget} transparent animationType="fade">
        <Pressable style={styles.confirmBackdrop} onPress={() => !disconnecting && setDisconnectTarget(null)}>
          <Pressable style={styles.confirmCard} onPress={() => {}}>
            <View style={styles.confirmIconWrap}>
              <Ionicons name="unlink-outline" size={24} color="#c0392b" />
            </View>
             <Text style={styles.confirmTitle}>{t('addons_disconnect_title')}</Text>
             <Text style={styles.confirmDesc}>
               {t('common_remove_confirm', { item: disconnectTarget?.label ?? '' })} {t('addons_disconnect_confirm')}
               {'\n\n'}{t('addons_disconnect_sub')}
             </Text>
            <View style={styles.confirmBtnRow}>
              <TouchableOpacity
                style={styles.confirmCancel}
                 onPress={() => setDisconnectTarget(null)}
                 disabled={disconnecting}
                 activeOpacity={0.8}
               >
                 <Text style={styles.confirmCancelText}>{t('common_cancel')}</Text>
               </TouchableOpacity>
              <TouchableOpacity
                style={styles.confirmRemove}
                onPress={confirmDisconnect}
                 disabled={disconnecting}
                 activeOpacity={0.8}
               >
                 {disconnecting
                   ? <ActivityIndicator size="small" color="#c0392b" />
                   : <Text style={styles.confirmRemoveText}>{t('addons_disconnect')}</Text>
                 }
               </TouchableOpacity>
            </View>
          </Pressable>
        </Pressable>
      </Modal>

      {/* ── Remove Addon Confirmation Modal ── */}
      <Modal visible={!!removeAddonTarget} transparent animationType="fade">
        <Pressable style={styles.confirmBackdrop} onPress={() => !removingAddon && setRemoveAddonTarget(null)}>
          <Pressable style={styles.confirmCard} onPress={() => {}}>
            <View style={styles.confirmIconWrap}>
              <Ionicons name="trash-outline" size={24} color="#c0392b" />
            </View>
             <Text style={styles.confirmTitle}>{t('addons_remove_title')}</Text>
             <Text style={styles.confirmDesc}>
               {t('addons_remove_confirm')} <Text style={styles.confirmHighlight}>{removeAddonTarget?.manifest.name}</Text>?
               {'\n\n'}{t('addons_remove_sub')}
             </Text>
            <View style={styles.confirmBtnRow}>
              <TouchableOpacity
                style={styles.confirmCancel}
                 onPress={() => setRemoveAddonTarget(null)}
                 disabled={removingAddon}
                 activeOpacity={0.8}
               >
                 <Text style={styles.confirmCancelText}>{t('common_cancel')}</Text>
               </TouchableOpacity>
              <TouchableOpacity
                style={styles.confirmRemove}
                onPress={confirmRemoveAddon}
                 disabled={removingAddon}
                 activeOpacity={0.8}
               >
                 {removingAddon
                   ? <ActivityIndicator size="small" color="#c0392b" />
                   : <Text style={styles.confirmRemoveText}>{t('watchlist_remove')}</Text>
                 }
               </TouchableOpacity>
            </View>
          </Pressable>
        </Pressable>
      </Modal>

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

// ── Ultra Boost Card ──────────────────────────────────────────────────────────

function UltraBoostCard({
  enabled, onToggle, styles,
}: {
  enabled: boolean;
  onToggle: (enabled: boolean) => void;
  styles: ReturnType<typeof makeStyles>;
}) {
  return (
    <View style={styles.ultraCard}>
      <View style={styles.ultraTop}>
        <View style={styles.ultraIcon}>
          <Ionicons name="flash" size={22} color="#ffffff" />
        </View>
        <View style={styles.ultraInfo}>
          <Text style={styles.ultraTitle}>SD Ultra</Text>
          <Text style={styles.ultraSubtitle}>
            Ultra Fast Results Everytime
          </Text>
        </View>
        <AppleToggle
          value={enabled}
          onValueChange={onToggle}
          onColor="#ffffff"
          offColor="rgba(255,255,255,0.24)"
        />
      </View>
    </View>
  );
}

// ── Addon Card ────────────────────────────────────────────────────────────────

function AddonCard({
  addon, styles, colors, t, canMoveUp, canMoveDown, onToggle, onRemove, onMoveUp, onMoveDown,
}: {
  addon: InstalledAddon;
  styles: ReturnType<typeof makeStyles>;
  colors: any;
  t: (key: TranslationKey, params?: Record<string, string | number>) => string;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onToggle: (enabled: boolean) => void;
  onRemove: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
}) {
  const resources = addon.manifest.resources ?? [];
  const types     = addon.manifest.types ?? [];

  return (
    <View style={styles.addonCard}>
      <View style={styles.addonTop}>
        <View style={styles.addonIconWrap}>
          <Ionicons name="extension-puzzle-outline" size={22} color={colors.accentSoft} />
        </View>
        <View style={styles.addonInfo}>
          <Text style={styles.addonName}>{addon.manifest.name}</Text>
          <Text style={styles.addonVersion}>v{addon.manifest.version}</Text>
          {addon.manifest.description ? (
            <Text style={styles.addonDesc} numberOfLines={2}>{addon.manifest.description}</Text>
          ) : null}
        </View>
        <View style={styles.reorderBtns}>
          <TouchableOpacity
            style={[styles.reorderBtn, !canMoveUp && styles.reorderBtnDisabled]}
            onPress={canMoveUp ? onMoveUp : undefined}
            activeOpacity={canMoveUp ? 0.7 : 1}
            hitSlop={{ top: 4, bottom: 4, left: 4, right: 4 }}
          >
            <Ionicons name="chevron-up" size={13} color={colors.accentSoft} />
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.reorderBtn, !canMoveDown && styles.reorderBtnDisabled]}
            onPress={canMoveDown ? onMoveDown : undefined}
            activeOpacity={canMoveDown ? 0.7 : 1}
            hitSlop={{ top: 4, bottom: 4, left: 4, right: 4 }}
          >
            <Ionicons name="chevron-down" size={13} color={colors.accentSoft} />
          </TouchableOpacity>
        </View>
        <AppleToggle
          value={addon.enabled}
          onValueChange={onToggle}
          onColor={colors.toggleOn}
        />
      </View>

      <View style={styles.addonBottom}>
        {types.slice(0, 3).map((t, i) => {
          const label = typeof t === 'string' ? t : (t as any).name ?? String(t);
          return (
            <View key={`type-${i}`} style={styles.tag}>
              <Text style={styles.tagText}>{label}</Text>
            </View>
          );
        })}
        {resources.slice(0, 3).map((r, i) => {
          const label = typeof r === 'string' ? r : (r as any).name ?? String(r);
          return (
            <View key={`res-${i}`} style={[styles.tag, { borderColor: colors.accent + '44' }]}>
              <Text style={[styles.tagText, { color: colors.accentSoft }]}>{label}</Text>
            </View>
          );
        })}
        <TouchableOpacity style={styles.removeBtn} onPress={onRemove} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <Ionicons name="trash-outline" size={16} color="#c0392b" />
        </TouchableOpacity>
      </View>
    </View>
  );
}

const PROVIDER_DESC_KEY: Record<string, any> = {
  'real-debrid': 'debrid_rd_desc',
  'alldebrid':   'debrid_ad_desc',
  'premiumize':  'debrid_pm_desc',
  'torbox':      'debrid_tb_desc',
};

// ── Debrid Provider Card ──────────────────────────────────────────────────────

function DebridCard({
  provider, account, styles, colors, t, isLightMonochrome,
  canMoveUp, canMoveDown,
  onConnect, onDisconnect, onMoveUp, onMoveDown,
}: {
  provider: DebridService;
  account?: { enabled: boolean; username?: string };
  styles: ReturnType<typeof makeStyles>;
  colors: any;
  t: (key: TranslationKey, params?: Record<string, string | number>) => string;
  isLightMonochrome: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
}) {
  const isConnected = !!account;
  const isLightAppearance = colors.bg === '#f4f6fb';
  const description = provider.name === 'debrid-link'
    ? provider.description
    : t(PROVIDER_DESC_KEY[provider.name] || provider.description);

  return (
    <View style={styles.debridCard}>
      <View style={styles.debridTop}>
         <View style={[styles.debridDot, { backgroundColor: isConnected ? '#00e676' : colors.mutedText }]} />
        <Text style={styles.debridLabel}>{provider.label}</Text>

        {/* Priority reorder buttons — only shown when connected */}
        {isConnected && (
          <View style={styles.reorderBtns}>
            <TouchableOpacity
              style={[styles.reorderBtn, !canMoveUp && styles.reorderBtnDisabled]}
              onPress={canMoveUp ? onMoveUp : undefined}
              activeOpacity={canMoveUp ? 0.7 : 1}
              hitSlop={{ top: 4, bottom: 4, left: 4, right: 4 }}
            >
              <Ionicons name="chevron-up" size={13} color={colors.accentSoft} />
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.reorderBtn, !canMoveDown && styles.reorderBtnDisabled]}
              onPress={canMoveDown ? onMoveDown : undefined}
              activeOpacity={canMoveDown ? 0.7 : 1}
              hitSlop={{ top: 4, bottom: 4, left: 4, right: 4 }}
            >
              <Ionicons name="chevron-down" size={13} color={colors.accentSoft} />
            </TouchableOpacity>
          </View>
        )}

          <View style={{
           backgroundColor: isConnected ? (isLightAppearance ? 'rgba(0,230,118,0.14)' : '#00e67622') : colors.inputBg,
            borderRadius: 20, paddingHorizontal: 8, paddingVertical: 3,
             borderWidth: 1, borderColor: isConnected ? (isLightAppearance ? 'rgba(0,230,118,0.55)' : '#00e67644') : colors.border,
           }}>
            <Text style={[styles.debridStatus, { color: isConnected ? (isLightAppearance ? '#00c853' : '#00e676') : colors.mutedText }]}>
              {isConnected ? t('addons_status_connected') : t('addons_status_not_connected')}
            </Text>
          </View>
      </View>

      <Text style={styles.debridDesc}>{description}</Text>
       {isConnected && account?.username && (
         <Text style={styles.debridUser}>{t('addons_signed_in_as')} {account.username}</Text>
       )}

      <View style={styles.debridActions}>
        {isConnected ? (
          <TouchableOpacity style={styles.disconnectBtn} onPress={onDisconnect} activeOpacity={0.8}>
            <Text style={styles.disconnectBtnText}>{t('addons_disconnect')}</Text>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            style={[
              styles.connectBtn,
              isLightMonochrome && { borderColor: 'rgba(17,24,39,0.18)' },
            ]}
            onPress={onConnect}
            activeOpacity={0.85}
          >
            <Text style={styles.connectBtnText}>{t('addons_connect')}</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
}


