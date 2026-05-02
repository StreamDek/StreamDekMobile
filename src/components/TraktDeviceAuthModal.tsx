import React, { useEffect, useRef, useState } from 'react';
import {
  Animated, Linking, Modal, StyleSheet,
  Text, TouchableOpacity, View, ActivityIndicator,
} from 'react-native';
import * as Clipboard from 'expo-clipboard';
import { Ionicons } from '@expo/vector-icons';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useTrakt, DeviceCodeInfo } from '../context/TraktContext';

interface Props {
  visible: boolean;
  onSuccess: () => void;
  onDismiss: () => void;
}

type Status = 'init' | 'waiting' | 'success' | 'expired' | 'denied' | 'error';

const makeStyles = (c: ThemeColors, isLightMonochrome: boolean) => StyleSheet.create({
  container:   { flex: 1 },
  overlay:     { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.72)' },
  sheet: {
    borderTopLeftRadius: 24, borderTopRightRadius: 24,
    borderWidth: 1, borderBottomWidth: 0,
    backgroundColor: c.cardBg, borderColor: c.border,
    padding: 24, paddingBottom: 44, minHeight: 380,
  },
  headerRow:   { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 },
  headerLeft:  { flexDirection: 'row', alignItems: 'center', gap: 12 },
  traktIcon:   { width: 38, height: 38, borderRadius: 10, justifyContent: 'center', alignItems: 'center', backgroundColor: '#ed1c2420' },
  titleText:   { color: c.textPrimary, fontSize: 18, fontWeight: '800' },
  closeBtn:    { padding: 4 },
  instructions:{ color: c.subText, fontSize: 14, lineHeight: 20, marginBottom: 16 },
  urlBadge: {
    flexDirection: 'row', alignItems: 'center', gap: 8,
    borderRadius: 10, borderWidth: 1,
    backgroundColor: c.inputBg, borderColor: c.inputBorder,
    paddingHorizontal: 14, paddingVertical: 10, marginBottom: 24,
  },
  urlText:     { color: c.textPrimary, fontSize: 14, fontWeight: '700', flex: 1 },
  codeWrap:    { alignItems: 'center', marginBottom: 24 },
  codeLabel:   { color: c.mutedText, fontSize: 11, fontWeight: '700', letterSpacing: 1, textTransform: 'uppercase', marginBottom: 10 },
  codeHint:    { color: c.subText, fontSize: 12, textAlign: 'center', marginBottom: 10 },
  codePill: {
    borderRadius: 14, borderWidth: 2,
    backgroundColor: c.inputBg, borderColor: '#ed1c2480',
    paddingHorizontal: 32, paddingVertical: 14, marginBottom: 8,
  },
  codeText:    { color: c.textPrimary, fontSize: 34, fontWeight: '900', letterSpacing: 10 },
  codeExpiry:  { color: c.mutedText, fontSize: 12 },
  waitRow:     { flexDirection: 'row', alignItems: 'center', gap: 10, justifyContent: 'center' },
  waitText:    { color: c.subText, fontSize: 14 },
  centered:    { alignItems: 'center', paddingVertical: 28, gap: 14 },
  successIcon: { width: 80, height: 80, borderRadius: 40, justifyContent: 'center', alignItems: 'center', backgroundColor: '#00c85320' },
  successText: { color: '#00c853', fontSize: 22, fontWeight: '800' },
  stateLabel:  { color: '#c97070', fontSize: 18, fontWeight: '700', textAlign: 'center' },
  stateDesc:   { color: c.subText, fontSize: 13, textAlign: 'center' },
  retryBtn:    {
    borderRadius: 12,
    paddingHorizontal: 28,
    paddingVertical: 12,
    backgroundColor: c.accent,
    borderWidth: isLightMonochrome ? 1 : 0,
    borderColor: isLightMonochrome ? 'rgba(17,24,39,0.18)' : 'transparent',
  },
  retryText:   { color: c.buttonText, fontSize: 15, fontWeight: '700' },
  initLabel:   { color: c.subText, fontSize: 14, marginTop: 12 },
});

export const TraktDeviceAuthModal: React.FC<Props> = ({ visible, onSuccess, onDismiss }) => {
  const { theme: { id: themeId, colors, resolvedAppearance } } = useTheme();
  const isLightMonochrome = resolvedAppearance === 'light' && themeId === 'monochrome';
  const styles = React.useMemo(() => makeStyles(colors, isLightMonochrome), [colors, isLightMonochrome]);
  const { initiateDeviceCode, pollDeviceToken } = useTrakt();

  const [status,       setStatus]       = useState<Status>('init');
  const [codeInfo,     setCodeInfo]     = useState<DeviceCodeInfo | null>(null);
  const [timeLeft,     setTimeLeft]     = useState(600);
  const [copied,       setCopied]       = useState(false);

  const pollerRef    = useRef<ReturnType<typeof setInterval> | null>(null);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const copiedTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pulseAnim    = useRef(new Animated.Value(1)).current;
  const pulseLoop    = useRef<Animated.CompositeAnimation | null>(null);

  const clearTimers = () => {
    if (pollerRef.current)    clearInterval(pollerRef.current);
    if (countdownRef.current) clearInterval(countdownRef.current);
    if (copiedTimeoutRef.current) clearTimeout(copiedTimeoutRef.current);
    pollerRef.current    = null;
    countdownRef.current = null;
    copiedTimeoutRef.current = null;
  };

  const copyActivationCode = async () => {
    if (!codeInfo?.user_code) return;
    await Clipboard.setStringAsync(codeInfo.user_code);
    setCopied(true);
    if (copiedTimeoutRef.current) clearTimeout(copiedTimeoutRef.current);
    copiedTimeoutRef.current = setTimeout(() => setCopied(false), 1500);
  };

  // Reset and start device-code flow whenever the modal opens
  useEffect(() => {
    if (!visible) { clearTimers(); return; }

    setStatus('init');
    setCodeInfo(null);
    setTimeLeft(600);

    initiateDeviceCode().then(info => {
      if (!info) { setStatus('error'); return; }
      setCodeInfo(info);
      setTimeLeft(info.expires_in);
      setStatus('waiting');
    });

    return clearTimers;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  // Pulsing animation on the code pill while waiting
  useEffect(() => {
    if (status !== 'waiting') { pulseLoop.current?.stop(); return; }
    pulseLoop.current = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, { toValue: 1.04, duration: 900, useNativeDriver: true }),
        Animated.timing(pulseAnim, { toValue: 1.00, duration: 900, useNativeDriver: true }),
      ])
    );
    pulseLoop.current.start();
    return () => pulseLoop.current?.stop();
  }, [status, pulseAnim]);

  // Countdown tick
  useEffect(() => {
    if (status !== 'waiting') return;
    countdownRef.current = setInterval(() => {
      setTimeLeft(prev => {
        if (prev <= 1) { clearTimers(); setStatus('expired'); return 0; }
        return prev - 1;
      });
    }, 1000);
    return () => { if (countdownRef.current) clearInterval(countdownRef.current); };
  }, [status]);

  // Poll backend for token
  useEffect(() => {
    if (status !== 'waiting' || !codeInfo) return;
    const interval = (codeInfo.interval ?? 5) * 1000;
    pollerRef.current = setInterval(async () => {
      const result = await pollDeviceToken(codeInfo.device_code);
      if (result === 'authorized') {
        clearTimers();
        setStatus('success');
        setTimeout(onSuccess, 1400);
      } else if (result === 'expired') {
        clearTimers();
        setStatus('expired');
      } else if (result === 'denied') {
        clearTimers();
        setStatus('denied');
      } else if (result === 'error') {
        clearTimers();
        setStatus('error');
      }
      // 'pending' / 'slow_down' keep polling
    }, interval);
    return () => { if (pollerRef.current) clearInterval(pollerRef.current); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, codeInfo]);

  const formatTime = (secs: number) =>
    `${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, '0')}`;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onDismiss}>
      <View style={styles.overlay}>
        <View style={styles.sheet}>

          {/* Header */}
          <View style={styles.headerRow}>
            <View style={styles.headerLeft}>
              <View style={styles.traktIcon}>
                <Text style={{ fontSize: 20 }}>🎬</Text>
              </View>
              <Text style={styles.titleText}>Connect Trakt</Text>
            </View>
            <TouchableOpacity onPress={onDismiss} style={styles.closeBtn}>
              <Ionicons name="close" size={22} color={colors.mutedText} />
            </TouchableOpacity>
          </View>

          {/* Loading initial code */}
          {status === 'init' && (
            <View style={styles.centered}>
              <ActivityIndicator size="large" color="#ed1c24" />
              <Text style={styles.initLabel}>Connecting to Trakt…</Text>
            </View>
          )}

          {/* Waiting for user to enter code */}
          {status === 'waiting' && codeInfo && (
            <>
              <Text style={styles.instructions}>
                Visit the link below on any device and enter the code to link your Trakt account.
              </Text>

              <TouchableOpacity
                style={styles.urlBadge}
                onPress={() => Linking.openURL(codeInfo.verification_url)}
                activeOpacity={0.7}
              >
                <Ionicons name="open-outline" size={14} color={colors.accent} />
                <Text style={styles.urlText} numberOfLines={1}>
                  {codeInfo.verification_url}
                </Text>
              </TouchableOpacity>

              <View style={styles.codeWrap}>
                <Text style={styles.codeLabel}>Your activation code</Text>
                <Text style={styles.codeHint}>Tap the code to copy it</Text>
                <TouchableOpacity
                  onPress={copyActivationCode}
                  activeOpacity={0.85}
                  accessibilityRole="button"
                  accessibilityLabel="Copy activation code"
                >
                  <Animated.View style={[styles.codePill, { transform: [{ scale: pulseAnim }] }]}>
                    <Text style={styles.codeText}>{codeInfo.user_code}</Text>
                  </Animated.View>
                </TouchableOpacity>
                {copied && <Text style={styles.codeExpiry}>Copied to clipboard</Text>}
                <Text style={styles.codeExpiry}>Expires in {formatTime(timeLeft)}</Text>
              </View>

              <View style={styles.waitRow}>
                <ActivityIndicator size="small" color={colors.accent} />
                <Text style={styles.waitText}>Waiting for authorisation…</Text>
              </View>
            </>
          )}

          {/* Authorised */}
          {status === 'success' && (
            <View style={styles.centered}>
              <View style={styles.successIcon}>
                <Ionicons name="checkmark-circle" size={52} color="#00c853" />
              </View>
              <Text style={styles.successText}>Connected!</Text>
            </View>
          )}

          {/* Expired */}
          {status === 'expired' && (
            <View style={styles.centered}>
              <Ionicons name="time-outline" size={52} color="#c97070" />
              <Text style={styles.stateLabel}>Code Expired</Text>
              <Text style={styles.stateDesc}>The activation code timed out. Try again.</Text>
              <TouchableOpacity style={styles.retryBtn} onPress={onDismiss}>
                <Text style={styles.retryText}>Try Again</Text>
              </TouchableOpacity>
            </View>
          )}

          {/* Denied or error */}
          {(status === 'denied' || status === 'error') && (
            <View style={styles.centered}>
              <Ionicons name="alert-circle-outline" size={52} color="#c97070" />
              <Text style={styles.stateLabel}>
                {status === 'denied' ? 'Access Denied' : 'Something went wrong'}
              </Text>
              <Text style={styles.stateDesc}>
                {status === 'denied'
                  ? 'You denied access on Trakt. Close and try again.'
                  : 'Could not reach Trakt. Check your connection and try again.'}
              </Text>
              <TouchableOpacity style={styles.retryBtn} onPress={onDismiss}>
                <Text style={styles.retryText}>Dismiss</Text>
              </TouchableOpacity>
            </View>
          )}

        </View>
      </View>
    </Modal>
  );
};
