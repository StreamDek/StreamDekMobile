import React, { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { Image } from 'expo-image';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useAuth } from '../context/AuthContext';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useLanguage } from '../context/LanguageContext';
import { Storage } from '../utils/storage';
import { ConfirmSheet } from '../components/ConfirmSheet';

type Mode = 'login' | 'signup' | 'forgot';

const makeStyles = (c: ThemeColors, isLightMonochrome: boolean) => StyleSheet.create({
  flex: { flex: 1, backgroundColor: c.bg },
  scroll: { paddingHorizontal: 24 },
  backBtn: { flexDirection: 'row', alignItems: 'center', gap: 2, alignSelf: 'flex-start', marginBottom: 32 },
  backText: { color: c.accentSoft, fontSize: 15, fontWeight: '600' },
  brand: { alignItems: 'center', marginBottom: 28 },
  brandSub: { color: c.subText, fontSize: 15 },
  tabs: { flexDirection: 'row', gap: 10, marginBottom: 22 },
  tab: {
    flex: 1,
    minWidth: 0,
    paddingVertical: 14,
    paddingHorizontal: 10,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: c.inputBorder,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: c.inputBg,
  },
  tabActive: { backgroundColor: c.accent, borderColor: isLightMonochrome ? 'rgba(17,24,39,0.18)' : c.accent },
  tabText: { color: c.subText, fontWeight: '800', fontSize: 12, textAlign: 'center' },
  tabTextActive: { color: c.buttonText },
  card: {
    backgroundColor: c.inputBg,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: c.inputBorder,
    marginBottom: 12,
    overflow: 'hidden',
  },
  field: { paddingHorizontal: 16, paddingVertical: 14 },
  fieldBorder: { borderTopWidth: 1, borderTopColor: c.inputBorder },
  fieldLabel: { color: c.subText, fontSize: 11, fontWeight: '700', letterSpacing: 0.6, textTransform: 'uppercase', marginBottom: 10 },
  inputRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  input: { flex: 1, color: '#e0e0f0', fontSize: 15, paddingVertical: 0 },
  eyeBtn: { padding: 4 },
  helperRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 22 },
  rememberBtn: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  rememberBox: {
    width: 20,
    height: 20,
    borderRadius: 6,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  helperText: { color: c.subText, fontSize: 13 },
  helperLink: { color: c.accentSoft, fontSize: 13, fontWeight: '700' },
  submitBtn: {
    backgroundColor: c.accent,
    borderRadius: 999,
    paddingVertical: 16,
    alignItems: 'center',
    marginBottom: 18,
    borderWidth: isLightMonochrome ? 1 : 0,
    borderColor: isLightMonochrome ? 'rgba(17,24,39,0.18)' : 'transparent',
  },
  submitDisabled: { opacity: 0.55 },
  submitText: { color: c.buttonText, fontSize: 16, fontWeight: '800', letterSpacing: 0.3 },
  footerRow: { alignItems: 'center', marginTop: 4 },
  footerText: { color: c.mutedText, fontSize: 14, textAlign: 'center', lineHeight: 20 },
  footerLink: { color: c.accentSoft, fontWeight: '800', fontSize: 14 },
  note: { color: c.placeholder, fontSize: 13, textAlign: 'center', marginBottom: 20 },
});

export const AuthScreen = ({ navigation }: any) => {
  const insets = useSafeAreaInsets();
  const { signIn, signUp, requestPasswordReset, confirmPasswordReset } = useAuth();
  const { theme: { id: themeId, colors, resolvedAppearance } } = useTheme();
  const { t } = useLanguage();
  const isLightMonochrome = resolvedAppearance === 'light' && themeId === 'monochrome';
  const styles = useMemo(() => makeStyles(colors, isLightMonochrome), [colors, isLightMonochrome]);
  const isLightAppearance = resolvedAppearance === 'light';

  const [mode, setMode] = useState<Mode>('login');
  const [forgotStage, setForgotStage] = useState<'request' | 'confirm'>('request');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [resetCode, setResetCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [alert, setAlert] = useState({
    visible: false,
    title: '',
    message: '',
    confirmLabel: t('common_ok'),
    infoOnly: true,
    icon: undefined as any,
    variant: undefined as 'accent' | 'destructive' | undefined,
    cancelLabel: undefined as string | undefined,
    onConfirm: undefined as undefined | (() => void),
  });

  const showAlert = (
    title: string,
    message: string,
    opts: Partial<typeof alert> = {},
  ) => {
    setAlert({
      visible: true,
      title,
      message,
      confirmLabel: t('common_ok'),
      infoOnly: true,
      icon: undefined,
      variant: undefined,
      cancelLabel: undefined,
      onConfirm: undefined,
      ...opts,
    });
  };

  useEffect(() => {
    Storage.getItem('streamdek_remember_email').then((saved) => {
      if (saved) {
        setEmail(saved);
        setRememberMe(true);
      }
    });
  }, []);

  const closeAlert = () => setAlert((prev) => ({ ...prev, visible: false }));

  async function handleSubmit() {
    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      showAlert('', t('auth_enter_email'), { icon: 'mail-outline' });
      return;
    }

    if (mode === 'forgot') {
      if (forgotStage === 'request') {
        setLoading(true);
        const result = await requestPasswordReset(trimmedEmail);
        setLoading(false);

        if (result.error) {
          showAlert(t('common_error'), result.error, { icon: 'alert-circle-outline', variant: 'destructive' });
          return;
        }

        setForgotStage('confirm');
        setResetCode('');
        setNewPassword('');
        setConfirmNewPassword('');

        const message = result.devResetCode
          ? `A reset code has been sent to ${trimmedEmail}.\n\nDevelopment code: ${result.devResetCode}`
          : `A reset code has been sent to ${trimmedEmail}.`;

        showAlert(t('auth_reset_email_sent'), message, {
          icon: 'mail-unread-outline',
        });
        return;
      }

      if (!resetCode.trim()) {
        showAlert('', 'Please enter the reset code from your email.', { icon: 'keypad-outline' });
        return;
      }

      if (!newPassword) {
        showAlert('', t('auth_enter_password'), { icon: 'lock-closed-outline' });
        return;
      }

      if (newPassword.length < 6) {
        showAlert('', t('auth_pass_min_length'), { icon: 'shield-outline' });
        return;
      }

      if (newPassword !== confirmNewPassword) {
        showAlert('', t('auth_pass_match'), { icon: 'shield-outline' });
        return;
      }

      setLoading(true);
      const errorMessage = await confirmPasswordReset(trimmedEmail, resetCode.trim(), newPassword);
      setLoading(false);

      if (errorMessage) {
        showAlert(t('common_error'), errorMessage, { icon: 'alert-circle-outline', variant: 'destructive' });
        return;
      }

      setForgotStage('request');
      setResetCode('');
      setNewPassword('');
      setConfirmNewPassword('');
      setMode('login');
      setPassword('');
      showAlert('Password updated', 'You can now sign in with your new password.', {
        icon: 'shield-checkmark-outline',
        onConfirm: () => setMode('login'),
      });
      return;
    }

    if (!password) {
      showAlert('', t('auth_enter_password'), { icon: 'lock-closed-outline' });
      return;
    }

    if (mode === 'signup') {
      if (password.length < 6) {
        showAlert('', t('auth_pass_min_length'), { icon: 'shield-outline' });
        return;
      }
      if (password !== confirmPassword) {
        showAlert('', t('auth_pass_match'), { icon: 'shield-outline' });
        return;
      }
    }

    setLoading(true);
    const errorMessage = mode === 'login'
      ? await signIn(trimmedEmail, password)
      : await signUp(trimmedEmail, password);
    setLoading(false);

    if (errorMessage) {
      showAlert(t('common_error'), errorMessage, { icon: 'alert-circle-outline', variant: 'destructive' });
      return;
    }

    if (mode === 'login') {
      if (rememberMe) await Storage.setItem('streamdek_remember_email', trimmedEmail);
      else await Storage.removeItem('streamdek_remember_email');
    }

    navigation.goBack();
  }

  function switchMode(next: Mode) {
    setMode(next);
    setForgotStage('request');
    setPassword('');
    setConfirmPassword('');
    setResetCode('');
    setNewPassword('');
    setConfirmNewPassword('');
    setShowPass(false);
  }

  return (
    <View style={styles.flex}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <StatusBar barStyle={isLightAppearance ? 'dark-content' : 'light-content'} translucent backgroundColor="transparent" />
        <ScrollView
          style={styles.flex}
          contentContainerStyle={[styles.scroll, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 48 }]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()}>
            <Ionicons name="chevron-back" size={20} color={colors.accentSoft} />
            <Text style={styles.backText}>{t('auth_back')}</Text>
          </TouchableOpacity>

          <View style={styles.brand}>
            <Image
              source={require('../../assets/app-logo.png')}
              style={{ width: 34, height: 34, marginBottom: 16 }}
              contentFit="contain"
            />
            <Text style={styles.brandSub}>
              {mode === 'login' ? t('auth_welcome_back') : mode === 'signup' ? t('auth_create_account') : t('auth_reset_pass')}
            </Text>
          </View>

          <View style={styles.tabs}>
            <TouchableOpacity style={[styles.tab, mode === 'login' && styles.tabActive]} onPress={() => switchMode('login')} activeOpacity={0.85}>
              <Text numberOfLines={1} style={[styles.tabText, mode === 'login' && styles.tabTextActive]}>{t('auth_sign_in_btn')}</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.tab, mode === 'signup' && styles.tabActive]} onPress={() => switchMode('signup')} activeOpacity={0.85}>
              <Text numberOfLines={1} style={[styles.tabText, mode === 'signup' && styles.tabTextActive]}>{t('auth_create_account_btn')}</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.tab, mode === 'forgot' && styles.tabActive]} onPress={() => switchMode('forgot')} activeOpacity={0.85}>
              <Text numberOfLines={1} style={[styles.tabText, mode === 'forgot' && styles.tabTextActive]}>{t('auth_forgot')}</Text>
            </TouchableOpacity>
          </View>

          <Text style={styles.note}>
            {mode === 'forgot'
              ? forgotStage === 'request'
                ? 'We will send a reset code to your email.'
                : 'Enter the code from your email and choose a new password.'
              : 'Sign in to keep your watch history, settings, and devices in sync.'}
          </Text>

          <View style={styles.card}>
            <View style={styles.field}>
              <Text style={styles.fieldLabel}>{t('auth_email')}</Text>
              <View style={styles.inputRow}>
                <Ionicons name="mail-outline" size={17} color={colors.subText} />
                <TextInput
                  style={[styles.input, { color: colors.textPrimary }]}
                  placeholder={t('auth_email_placeholder')}
                  placeholderTextColor={colors.placeholder}
                  value={email}
                  onChangeText={setEmail}
                  autoCapitalize="none"
                  keyboardType="email-address"
                  autoComplete="email"
                  autoCorrect={false}
                />
              </View>
            </View>

            {mode !== 'forgot' && (
              <View style={[styles.field, styles.fieldBorder]}>
                <Text style={styles.fieldLabel}>{t('auth_password')}</Text>
                <View style={styles.inputRow}>
                  <Ionicons name="lock-closed-outline" size={17} color={colors.subText} />
                  <TextInput
                    style={[styles.input, { paddingRight: 32, color: colors.textPrimary }]}
                    placeholder="••••••••"
                    placeholderTextColor={colors.placeholder}
                    value={password}
                    onChangeText={setPassword}
                    secureTextEntry={!showPass}
                    autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                  />
                  <TouchableOpacity onPress={() => setShowPass((v) => !v)} style={styles.eyeBtn}>
                    <Ionicons name={showPass ? 'eye-off-outline' : 'eye-outline'} size={17} color={colors.subText} />
                  </TouchableOpacity>
                </View>
              </View>
            )}

            {mode === 'forgot' && forgotStage === 'confirm' && (
              <>
                <View style={[styles.field, styles.fieldBorder]}>
                  <Text style={styles.fieldLabel}>Reset Code</Text>
                  <View style={styles.inputRow}>
                    <Ionicons name="keypad-outline" size={17} color={colors.subText} />
                    <TextInput
                      style={[styles.input, { color: colors.textPrimary }]}
                      placeholder="12345678"
                      placeholderTextColor={colors.placeholder}
                      value={resetCode}
                      onChangeText={setResetCode}
                      keyboardType="number-pad"
                      autoCapitalize="none"
                      autoCorrect={false}
                    />
                  </View>
                </View>

                <View style={[styles.field, styles.fieldBorder]}>
                  <Text style={styles.fieldLabel}>New Password</Text>
                  <View style={styles.inputRow}>
                    <Ionicons name="lock-closed-outline" size={17} color={colors.subText} />
                    <TextInput
                      style={[styles.input, { paddingRight: 32, color: colors.textPrimary }]}
                      placeholder="••••••••"
                      placeholderTextColor={colors.placeholder}
                      value={newPassword}
                      onChangeText={setNewPassword}
                      secureTextEntry={!showPass}
                      autoComplete="new-password"
                    />
                    <TouchableOpacity onPress={() => setShowPass((v) => !v)} style={styles.eyeBtn}>
                      <Ionicons name={showPass ? 'eye-off-outline' : 'eye-outline'} size={17} color={colors.subText} />
                    </TouchableOpacity>
                  </View>
                </View>

                <View style={[styles.field, styles.fieldBorder]}>
                  <Text style={styles.fieldLabel}>Confirm New Password</Text>
                  <View style={styles.inputRow}>
                    <Ionicons name="lock-closed-outline" size={17} color={colors.subText} />
                    <TextInput
                      style={[styles.input, { color: colors.textPrimary }]}
                      placeholder="••••••••"
                      placeholderTextColor={colors.placeholder}
                      value={confirmNewPassword}
                      onChangeText={setConfirmNewPassword}
                      secureTextEntry={!showPass}
                      autoComplete="new-password"
                    />
                  </View>
                </View>
              </>
            )}

            {mode === 'signup' && (
              <View style={[styles.field, styles.fieldBorder]}>
                <Text style={styles.fieldLabel}>{t('auth_confirm_password')}</Text>
                <View style={styles.inputRow}>
                  <Ionicons name="lock-closed-outline" size={17} color={colors.subText} />
                  <TextInput
                    style={[styles.input, { color: colors.textPrimary }]}
                    placeholder="••••••••"
                    placeholderTextColor={colors.placeholder}
                    value={confirmPassword}
                    onChangeText={setConfirmPassword}
                    secureTextEntry={!showPass}
                    autoComplete="new-password"
                  />
                </View>
              </View>
            )}
          </View>

          {mode === 'login' && (
            <View style={styles.helperRow}>
              <TouchableOpacity style={styles.rememberBtn} onPress={() => setRememberMe((v) => !v)} activeOpacity={0.8}>
                <View
                  style={[
                    styles.rememberBox,
                    {
                      borderColor: rememberMe ? colors.accent : colors.inputBorder,
                      backgroundColor: rememberMe
                        ? colors.accent
                        : (isLightAppearance ? 'rgba(255,255,255,0.88)' : colors.inputBg),
                    },
                  ]}
                >
                  {rememberMe ? (
                    <Ionicons name="checkmark" size={13} color={colors.buttonText} />
                  ) : null}
                </View>
                <Text style={[styles.helperText, { color: colors.textPrimary }]}>{t('auth_remember')}</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={() => switchMode('forgot')}>
                <Text style={styles.helperLink}>{t('auth_forgot')}</Text>
              </TouchableOpacity>
            </View>
          )}

          <TouchableOpacity
            style={[styles.submitBtn, loading && styles.submitDisabled]}
            onPress={handleSubmit}
            activeOpacity={0.85}
            disabled={loading}
          >
            {loading
              ? <ActivityIndicator color={colors.buttonText} />
              : <Text style={styles.submitText}>
                  {mode === 'login'
                    ? t('auth_sign_in_btn')
                    : mode === 'signup'
                      ? t('auth_create_account_btn')
                      : forgotStage === 'request'
                        ? 'Send Reset Code'
                        : 'Reset Password'}
                </Text>
            }
          </TouchableOpacity>

          <View style={styles.footerRow}>
            {mode === 'forgot' ? (
              forgotStage === 'confirm' ? (
                <TouchableOpacity onPress={() => {
                  setForgotStage('request');
                  setResetCode('');
                  setNewPassword('');
                  setConfirmNewPassword('');
                  setShowPass(false);
                }}>
                  <Text style={styles.footerLink}>Back to email</Text>
                </TouchableOpacity>
              ) : (
                <TouchableOpacity onPress={() => switchMode('login')}>
                  <Text style={styles.footerLink}>{t('auth_back_sign_in')}</Text>
                </TouchableOpacity>
              )
            ) : mode === 'login' ? (
              <Text style={styles.footerText}>
                {t('auth_new_to')}{' '}
                <Text style={styles.footerLink} onPress={() => switchMode('signup')}>{t('auth_create_link')}</Text>
              </Text>
            ) : (
              <Text style={styles.footerText}>
                {t('auth_have_account')}{' '}
                <Text style={styles.footerLink} onPress={() => switchMode('login')}>{t('auth_sign_in_link')}</Text>
              </Text>
            )}
          </View>
        </ScrollView>

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
      </KeyboardAvoidingView>
    </View>
  );
};


