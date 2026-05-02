import React, { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { deleteAccountDevice, fetchAccountBootstrap, type AccountBootstrap } from '../utils/accountPreferences';
import { activateTvCode, extractTvCode, normalizeTvCode } from '../services/tvLink';

const makeStyles = (c: ThemeColors, isLightMode: boolean, isLightMonochrome: boolean) => StyleSheet.create({
  container: { flex: 1, backgroundColor: c.bg },
  scroll: { paddingHorizontal: 24 },
  backBtn: { flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-start', marginBottom: 28 },
  backText: { color: c.accentSoft, fontSize: 15, fontWeight: '700' },
  title: { color: c.textPrimary, fontSize: 28, fontWeight: '900', marginBottom: 10 },
  subtitle: { color: c.textSecondary, fontSize: 15, lineHeight: 22, marginBottom: 24 },
  card: {
    backgroundColor: c.cardBg,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: c.border,
    padding: 18,
    gap: 14,
    marginBottom: 18,
  },
  cardTitle: { color: c.textPrimary, fontSize: 16, fontWeight: '800' },
  cardBody: { color: c.textSecondary, fontSize: 14, lineHeight: 21 },
  input: {
    height: 56,
    borderWidth: 1,
    borderColor: c.inputBorder,
    backgroundColor: c.inputBg,
    borderRadius: 14,
    color: c.textPrimary,
    fontSize: 20,
    letterSpacing: 2,
    paddingHorizontal: 16,
    fontWeight: '800',
  },
  row: { flexDirection: 'row', gap: 12 },
  actionButton: {
    flex: 1,
    minHeight: 52,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  scannerButton: {
    backgroundColor: isLightMonochrome ? c.accent : (isLightMode ? '#16a34a' : '#22c55e'),
    borderWidth: isLightMonochrome ? 1 : 0,
    borderColor: isLightMonochrome ? 'rgba(17,24,39,0.18)' : 'transparent',
  },
  scannerButtonText: { color: isLightMonochrome ? '#111111' : '#ffffff', fontSize: 15, fontWeight: '800' },
  primary: {
    backgroundColor: isLightMonochrome ? c.accent : (isLightMode ? '#16a34a' : '#22c55e'),
    borderWidth: isLightMonochrome ? 1 : 0,
    borderColor: isLightMonochrome ? 'rgba(17,24,39,0.18)' : 'transparent',
  },
  primaryText: { color: isLightMonochrome ? '#111111' : '#ffffff', fontSize: 15, fontWeight: '800' },
  secondary: {
    flex: 1,
    minHeight: 52,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: isLightMode ? '#f59e0b' : c.border,
    backgroundColor: isLightMode ? '#fff7ed' : c.inputBg,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  secondaryText: { color: isLightMode ? '#b45309' : c.textPrimary, fontSize: 15, fontWeight: '800' },
  state: { color: c.textSecondary, fontSize: 14, lineHeight: 20 },
  successCard: {
    backgroundColor: isLightMode ? '#f4fbf7' : c.cardBg,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: isLightMode ? '#b7e4c7' : c.border,
    padding: 18,
    gap: 10,
  },
  successTitle: { color: isLightMode ? '#166534' : c.textPrimary, fontSize: 18, fontWeight: '800' },
  successBody: { color: c.textSecondary, fontSize: 14, lineHeight: 21 },
  scannerBackdrop: { flex: 1, backgroundColor: c.overlayStrong, justifyContent: 'center', padding: 24 },
  scannerCard: {
    flex: 1,
    backgroundColor: c.cardBg,
    borderRadius: 22,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: c.border,
  },
  scannerHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 18,
    paddingVertical: 16,
    backgroundColor: c.cardBg,
  },
  scannerTitle: { color: c.textPrimary, fontSize: 18, fontWeight: '800' },
  scannerClose: {
    padding: 6,
    borderRadius: 999,
    backgroundColor: isLightMode ? 'rgba(15,23,42,0.06)' : 'rgba(255,255,255,0.08)',
  },
  cameraWrap: { flex: 1, backgroundColor: '#000' },
  scannerHelp: { color: c.textSecondary, fontSize: 13, lineHeight: 19, padding: 18 },
  confirmBackdrop: { flex: 1, backgroundColor: c.overlayStrong, justifyContent: 'center', alignItems: 'center', padding: 24 },
  confirmCard: {
    width: '100%',
    maxWidth: 420,
    backgroundColor: c.cardBg,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: c.border,
    padding: 22,
    gap: 14,
  },
  confirmIconWrap: {
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: isLightMode ? 'rgba(34,197,94,0.12)' : 'rgba(34,197,94,0.18)',
  },
  confirmTitle: { color: c.textPrimary, fontSize: 19, fontWeight: '800' },
  confirmBody: { color: c.textSecondary, fontSize: 14, lineHeight: 21 },
  confirmCode: { color: c.accentSoft, fontSize: 18, fontWeight: '900', letterSpacing: 1.5 },
  confirmActions: { flexDirection: 'row', gap: 12, marginTop: 4 },
  linkedHeaderRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  linkedCount: {
    color: isLightMode ? '#166534' : c.accentSoft,
    fontSize: 12,
    fontWeight: '800',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 999,
    backgroundColor: isLightMode ? '#dcfce7' : c.inputBg,
  },
  linkedDeviceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingTop: 14,
    borderTopWidth: 1,
    borderTopColor: c.border,
  },
  linkedDeviceIcon: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: isLightMode ? '#dcfce7' : 'rgba(34,197,94,0.16)',
  },
  linkedDeviceInfo: { flex: 1 },
  linkedDeviceTitle: { color: c.textPrimary, fontSize: 15, fontWeight: '700' },
  linkedDeviceMeta: { color: c.textSecondary, fontSize: 13, marginTop: 3, lineHeight: 18 },
  disconnectButton: {
    paddingHorizontal: 12,
    paddingVertical: 9,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: isLightMode ? '#fecaca' : 'rgba(248,113,113,0.32)',
    backgroundColor: isLightMode ? '#fef2f2' : 'rgba(127,29,29,0.18)',
  },
  disconnectButtonText: { color: '#dc2626', fontSize: 13, fontWeight: '800' },
});

export function LinkTvScreen({ navigation, route }: any) {
  const insets = useSafeAreaInsets();
  const { user, authLoading } = useAuth();
  const { activeProfile } = useProfile();
  const { theme } = useTheme();
  const { colors, resolvedAppearance } = theme;
  const isLightMode = resolvedAppearance === 'light';
  const isLightMonochrome = isLightMode && theme.id === 'monochrome';
  const styles = useMemo(() => makeStyles(colors, isLightMode, isLightMonochrome), [colors, isLightMode, isLightMonochrome]);
  const [cameraPermission, requestCameraPermission] = useCameraPermissions();
  const [code, setCode] = useState(normalizeTvCode(String(route?.params?.code ?? '')));
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [scannerVisible, setScannerVisible] = useState(false);
  const [linkedDeviceName, setLinkedDeviceName] = useState<string | null>(null);
  const [pendingConfirmationCode, setPendingConfirmationCode] = useState<string | null>(null);
  const [accountBootstrap, setAccountBootstrap] = useState<AccountBootstrap | null>(null);
  const [refreshingDevices, setRefreshingDevices] = useState(false);
  const [disconnectingDeviceId, setDisconnectingDeviceId] = useState<string | null>(null);

  const linkedTvDevices = useMemo(() => (accountBootstrap?.devices ?? []).filter((device) => {
    const platform = String(device.platform ?? '').toLowerCase();
    const deviceType = String(device.deviceType ?? '').toLowerCase();
    return platform.includes('tv') || deviceType.includes('tv');
  }), [accountBootstrap]);

  useEffect(() => {
    const nextCode = normalizeTvCode(String(route?.params?.code ?? ''));
    if (nextCode) {
      setCode(nextCode);
    }
  }, [route?.params?.code]);

  useEffect(() => {
    if (!authLoading && !user) {
      navigation.navigate('Auth');
    }
  }, [authLoading, navigation, user]);

  async function refreshLinkedDevices() {
    if (!user) return;
    setRefreshingDevices(true);
    try {
      const bootstrap = await fetchAccountBootstrap(user, activeProfile?.id);
      setAccountBootstrap(bootstrap);
    } finally {
      setRefreshingDevices(false);
    }
  }

  useEffect(() => {
    if (!user) {
      setAccountBootstrap(null);
      return;
    }
    void refreshLinkedDevices();
  }, [activeProfile?.id, user]);

  async function openScanner() {
    if (!cameraPermission?.granted) {
      const response = await requestCameraPermission();
      if (!response.granted) {
        Alert.alert('Camera access needed', 'Allow camera access to scan the TV QR code, or enter the code manually.');
        return;
      }
    }
    setScannerVisible(true);
  }

  async function confirmAndLink(nextCode: string) {
    if (!user) {
      navigation.navigate('Auth');
      return;
    }

    const normalized = normalizeTvCode(nextCode);
    if (normalized.length < 9) {
      setStatus('Enter the full TV code before continuing.');
      return;
    }

    setPendingConfirmationCode(normalized);
  }

  async function submitLink(normalized: string) {
    setBusy(true);
    setStatus('Authorizing TV...');

    try {
      const result = await activateTvCode(user, normalized);
      setLinkedDeviceName(result.deviceName ?? null);
      setStatus('TV linked successfully.');
      setPendingConfirmationCode(null);
      await refreshLinkedDevices();
    } catch (error: any) {
      setStatus(error?.message ?? 'Could not link this TV right now.');
    } finally {
      setBusy(false);
    }
  }

  async function handleDisconnectDevice(deviceId: string, deviceName: string) {
    if (!user || disconnectingDeviceId) return;
    setDisconnectingDeviceId(deviceId);
    try {
      await deleteAccountDevice(user, deviceId);
      setStatus(`${deviceName} disconnected.`);
      await refreshLinkedDevices();
    } catch (error: any) {
      setStatus(error?.message ?? 'Could not disconnect this TV right now.');
    } finally {
      setDisconnectingDeviceId(null);
    }
  }

  function handleScan(payload: string) {
    const extracted = extractTvCode(payload);
    if (!extracted) {
      setStatus('That QR code does not look like a StreamDek TV sign-in code.');
      setScannerVisible(false);
      return;
    }

    setScannerVisible(false);
    setCode(extracted);
    void confirmAndLink(extracted);
  }

  return (
    <View style={styles.container}>
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        keyboardVerticalOffset={Platform.OS === 'ios' ? insets.top : 0}
      >
        <ScrollView
          style={styles.container}
          contentContainerStyle={[styles.scroll, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 40 }]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()}>
            <Ionicons name="chevron-back" size={20} color={colors.accentSoft} />
            <Text style={styles.backText}>Back</Text>
          </TouchableOpacity>

          <Text style={styles.title}>Link TV</Text>
          <Text style={styles.subtitle}>
            Scan the QR code shown on StreamDek TV or enter the 8-character pairing code manually.
          </Text>

          <View style={styles.card}>
            <Text style={styles.cardTitle}>Scan QR Code</Text>
            <Text style={styles.cardBody}>
              Use the in-app scanner to approve sign-in for the TV currently showing the QR code.
            </Text>
            <TouchableOpacity style={[styles.actionButton, styles.scannerButton]} onPress={() => void openScanner()} disabled={busy}>
              <Text style={styles.scannerButtonText}>Open Scanner</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.card}>
            <Text style={styles.cardTitle}>Enter Code Manually</Text>
            <Text style={styles.cardBody}>
              If scanning is not convenient, type the pairing code from the TV screen.
            </Text>
            <TextInput
              value={code}
              onChangeText={(value) => setCode(normalizeTvCode(value))}
              placeholder="ABCD-1234"
              placeholderTextColor={colors.placeholder}
              autoCapitalize="characters"
              autoCorrect={false}
              style={styles.input}
              maxLength={9}
              returnKeyType="done"
              blurOnSubmit={false}
            />
            <View style={styles.row}>
              <TouchableOpacity style={[styles.actionButton, styles.primary]} onPress={() => void confirmAndLink(code)} disabled={busy}>
                {busy ? <ActivityIndicator color={isLightMonochrome ? '#111111' : colors.buttonText} /> : <Text style={styles.primaryText}>Authorize TV</Text>}
              </TouchableOpacity>
              <TouchableOpacity style={styles.secondary} onPress={() => setCode(normalizeTvCode(String(route?.params?.code ?? '')))} disabled={busy}>
                <Text style={styles.secondaryText}>Deep Link Code</Text>
              </TouchableOpacity>
            </View>
            {status ? <Text style={styles.state}>{status}</Text> : null}
          </View>

          <View style={styles.card}>
            <View style={styles.linkedHeaderRow}>
              <Text style={styles.cardTitle}>Linked TVs</Text>
              <Text style={styles.linkedCount}>{linkedTvDevices.length}</Text>
            </View>
            <Text style={styles.cardBody}>
              Manage TVs already linked to this account. Disconnect any device that should no longer sign in with you.
            </Text>
            {refreshingDevices ? (
              <ActivityIndicator color={colors.accentSoft} />
            ) : linkedTvDevices.length ? (
              linkedTvDevices.map((device) => {
                const deviceName = device.name?.trim() || 'StreamDek TV';
                const subtitle = device.isCurrent
                  ? `${deviceName} • This TV session`
                  : `${deviceName} • Last seen ${device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString() : 'recently'}`;
                return (
                  <View key={device.id} style={styles.linkedDeviceRow}>
                    <View style={styles.linkedDeviceIcon}>
                      <Ionicons name="tv-outline" size={20} color={isLightMode ? '#15803d' : '#22c55e'} />
                    </View>
                    <View style={styles.linkedDeviceInfo}>
                      <Text style={styles.linkedDeviceTitle}>{deviceName}</Text>
                      <Text style={styles.linkedDeviceMeta}>{subtitle}</Text>
                    </View>
                    <TouchableOpacity
                      style={styles.disconnectButton}
                      onPress={() => void handleDisconnectDevice(device.id, deviceName)}
                      activeOpacity={0.8}
                      disabled={disconnectingDeviceId === device.id}
                    >
                      {disconnectingDeviceId === device.id
                        ? <ActivityIndicator size="small" color="#dc2626" />
                        : <Text style={styles.disconnectButtonText}>Disconnect</Text>}
                    </TouchableOpacity>
                  </View>
                );
              })
            ) : (
              <Text style={styles.state}>No TVs linked yet.</Text>
            )}
          </View>

          {linkedDeviceName || status === 'TV linked successfully.' ? (
            <View style={styles.successCard}>
              <Text style={styles.successTitle}>TV authorized</Text>
              <Text style={styles.successBody}>
                {linkedDeviceName
                  ? `${linkedDeviceName} can now finish sign-in on the TV.`
                  : 'The TV can now finish sign-in and enter the app.'}
              </Text>
            </View>
          ) : null}
        </ScrollView>
      </KeyboardAvoidingView>

      <Modal visible={scannerVisible} animationType="slide" onRequestClose={() => setScannerVisible(false)}>
        <View style={styles.scannerBackdrop}>
          <View style={styles.scannerCard}>
            <View style={styles.scannerHeader}>
              <Text style={styles.scannerTitle}>Scan StreamDek TV QR</Text>
              <Pressable style={styles.scannerClose} onPress={() => setScannerVisible(false)}>
                <Ionicons name="close" size={22} color={colors.textPrimary} />
              </Pressable>
            </View>
            <View style={styles.cameraWrap}>
              <CameraView
                style={StyleSheet.absoluteFill}
                barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
                onBarcodeScanned={({ data }) => handleScan(data)}
              />
            </View>
            <Text style={styles.scannerHelp}>
              Point the camera at the QR code on your TV. We will prefill the code and ask for confirmation before approving access.
            </Text>
          </View>
        </View>
      </Modal>

      <Modal visible={!!pendingConfirmationCode} transparent animationType="fade" onRequestClose={() => setPendingConfirmationCode(null)}>
        <Pressable style={styles.confirmBackdrop} onPress={() => !busy && setPendingConfirmationCode(null)}>
          <Pressable style={styles.confirmCard} onPress={() => {}}>
            <View style={styles.confirmIconWrap}>
              <Ionicons name="tv-outline" size={24} color={colors.accentSoft} />
            </View>
            <Text style={styles.confirmTitle}>Authorize TV sign-in</Text>
            <Text style={styles.confirmBody}>
              Approve StreamDek TV using the pairing code below. The TV will finish signing in as soon as this is confirmed.
            </Text>
            <Text style={styles.confirmCode}>{pendingConfirmationCode}</Text>
            <View style={styles.confirmActions}>
              <TouchableOpacity
                style={styles.secondary}
                onPress={() => setPendingConfirmationCode(null)}
                disabled={busy}
              >
                <Text style={styles.secondaryText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.actionButton, styles.primary]}
                onPress={() => pendingConfirmationCode ? void submitLink(pendingConfirmationCode) : undefined}
                disabled={busy}
              >
                {busy ? <ActivityIndicator color={isLightMonochrome ? '#111111' : colors.buttonText} /> : <Text style={styles.primaryText}>Authorize</Text>}
              </TouchableOpacity>
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}
