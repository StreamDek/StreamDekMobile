import React, { useMemo } from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  useWindowDimensions,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../context/ThemeContext';
import { useLanguage } from '../context/LanguageContext';
import { useAppUpdate } from '../context/AppUpdateContext';
import { TVFocusable } from './TVFocusable';
import { getDeviceProfile } from '../utils/deviceProfile';

function makeStyles(colors: ReturnType<typeof useTheme>['theme']['colors'], isTv: boolean) {
  return StyleSheet.create({
    backdrop: {
      flex: 1,
      backgroundColor: isTv ? 'rgba(2,6,12,0.82)' : 'rgba(2,6,12,0.6)',
      justifyContent: 'center',
      paddingHorizontal: isTv ? 72 : 18,
    },
    card: {
      alignSelf: 'center',
      width: '100%',
      maxWidth: isTv ? 760 : 460,
      // maxHeight is applied inline as a number computed from the window size —
      // the body shrinks and the notes scroll, so the footer always lays out
      // below the content instead of covering it.
      borderRadius: isTv ? 30 : 26,
      borderWidth: 1,
      borderColor: colors.border,
      backgroundColor: colors.cardBgElevated ?? colors.cardBg,
      overflow: 'hidden',
    },
    hero: {
      paddingHorizontal: isTv ? 28 : 22,
      paddingTop: isTv ? 24 : 20,
      paddingBottom: 12,
      gap: 8,
      backgroundColor: colors.bgHeaderSolid,
    },
    badge: {
      alignSelf: 'flex-start',
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      borderRadius: 999,
      paddingHorizontal: 12,
      paddingVertical: 7,
      backgroundColor: `${colors.accent}22`,
      borderWidth: 1,
      borderColor: `${colors.accent}55`,
    },
    badgeText: {
      color: colors.textPrimary,
      fontWeight: '800',
      fontSize: isTv ? 13 : 12,
      letterSpacing: 0.3,
    },
    title: {
      color: colors.textPrimary,
      fontSize: isTv ? 28 : 24,
      fontWeight: '900',
      letterSpacing: -0.5,
    },
    subtitle: {
      color: colors.textSecondary,
      fontSize: isTv ? 15 : 14,
      lineHeight: isTv ? 22 : 20,
    },
    body: {
      paddingHorizontal: isTv ? 28 : 22,
      paddingTop: 14,
      gap: 10,
      flexShrink: 1,
      minHeight: 0,
    },
    metaRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 18,
    },
    sectionLabel: {
      color: colors.textSecondary,
      fontSize: 12,
      fontWeight: '800',
      letterSpacing: 0.5,
    },
    notes: {
      // Height is applied inline as a number computed from the window size.
      flexShrink: 1,
      borderRadius: 18,
      borderWidth: 1,
      borderColor: colors.border,
      backgroundColor: colors.inputBg,
      paddingHorizontal: 14,
      paddingVertical: 12,
    },
    notesText: {
      color: colors.textPrimary,
      fontSize: isTv ? 14 : 13,
      lineHeight: isTv ? 22 : 20,
    },
    infoText: {
      color: colors.textSecondary,
      fontSize: isTv ? 14 : 13,
      lineHeight: isTv ? 21 : 19,
    },
    infoStrong: {
      color: colors.textPrimary,
      fontWeight: '800',
    },
    statusText: {
      color: '#f0c36b',
      fontSize: isTv ? 14 : 13,
      lineHeight: 20,
    },
    errorText: {
      color: '#ff9b8d',
      fontSize: isTv ? 14 : 13,
      lineHeight: 20,
    },
    footer: {
      flexDirection: isTv ? 'row' : 'column-reverse',
      gap: 10,
      paddingHorizontal: isTv ? 28 : 22,
      paddingTop: 18,
      paddingBottom: 22,
    },
    button: {
      flex: 1,
      borderRadius: 18,
      minHeight: isTv ? 58 : 52,
      paddingHorizontal: 20,
      alignItems: 'center',
      justifyContent: 'center',
      flexDirection: 'row',
      gap: 10,
    },
    primaryButton: {
      backgroundColor: colors.accent,
    },
    secondaryButton: {
      backgroundColor: colors.inputBg,
      borderWidth: 1,
      borderColor: colors.border,
    },
    // Solid (fully opaque) disabled treatment — opacity on the button would
    // let the content behind it show through.
    disabledButton: {
      backgroundColor: colors.inputBg,
      borderWidth: 1,
      borderColor: colors.border,
    },
    primaryButtonText: {
      color: colors.buttonText,
      fontSize: isTv ? 16 : 15,
      fontWeight: '900',
    },
    secondaryButtonText: {
      color: colors.textPrimary,
      fontSize: isTv ? 16 : 15,
      fontWeight: '800',
    },
    disabledButtonText: {
      color: colors.textSecondary,
      fontSize: isTv ? 16 : 15,
      fontWeight: '800',
    },
  });
}

function UpdateButton({
  label,
  primary,
  disabled,
  onPress,
}: {
  label: string;
  primary?: boolean;
  disabled?: boolean;
  onPress: () => void;
}) {
  const { theme: { colors } } = useTheme();
  const isTv = getDeviceProfile().isTv;
  const styles = useMemo(() => makeStyles(colors, isTv), [colors, isTv]);
  const handlePress = () => {
    if (!disabled) onPress();
  };
  const buttonStyle = disabled
    ? styles.disabledButton
    : primary
      ? styles.primaryButton
      : styles.secondaryButton;
  const textStyle = disabled
    ? styles.disabledButtonText
    : primary
      ? styles.primaryButtonText
      : styles.secondaryButtonText;

  if (isTv) {
    return (
      <TVFocusable
        onPress={handlePress}
        style={[styles.button, buttonStyle]}
        normalStyle={{ backgroundColor: disabled ? colors.inputBg : (primary ? colors.accent : colors.inputBg) }}
        focusedStyle={{ borderColor: primary ? colors.buttonText : colors.accent, borderWidth: 2 }}
      >
        <Text style={textStyle}>{label}</Text>
      </TVFocusable>
    );
  }

  return (
    <TouchableOpacity
      onPress={handlePress}
      activeOpacity={disabled ? 1 : 0.84}
      disabled={disabled}
      style={[styles.button, buttonStyle]}
    >
      <Text style={textStyle}>{label}</Text>
    </TouchableOpacity>
  );
}

export function UpdatePrompt() {
  const insets = useSafeAreaInsets();
  const { height: windowHeight } = useWindowDimensions();
  const { theme: { colors } } = useTheme();
  const { t } = useLanguage();
  const {
    availableRelease,
    isMandatory,
    isDownloading,
    promptVisible,
    progress,
    statusMessage,
    errorMessage,
    dismissPrompt,
    startUpdate,
  } = useAppUpdate();
  const isTv = getDeviceProfile().isTv;
  const styles = useMemo(() => makeStyles(colors, isTv), [colors, isTv]);

  // Explicit numeric sizing — percentage maxHeight inside a centered flex
  // parent is unreliable on Android, which let the footer overlap the notes.
  const cardMaxHeight = Math.max(360, windowHeight - insets.top - insets.bottom - 64);
  const notesHeight = Math.min(isTv ? 240 : 200, Math.max(110, Math.round(windowHeight * 0.2)));

  if (!availableRelease) return null;

  const primaryLabel = isDownloading
    ? (progress?.progressPercent != null
      ? t('update_button_downloading', { percent: progress.progressPercent })
      : t('update_button_preparing'))
    : t('update_button_now');

  return (
    <Modal
      visible={promptVisible}
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={dismissPrompt}
    >
      <Pressable
        style={[styles.backdrop, { paddingTop: insets.top + 12, paddingBottom: insets.bottom + 12 }]}
        onPress={isMandatory ? undefined : dismissPrompt}
      >
        <Pressable style={[styles.card, { maxHeight: cardMaxHeight }]} onPress={() => {}}>
          <View style={styles.hero}>
            <View style={styles.badge}>
              <Ionicons
                name={isMandatory ? 'warning-outline' : 'sparkles-outline'}
                size={16}
                color={colors.textPrimary}
              />
              <Text style={styles.badgeText}>
                {isMandatory ? t('update_required_badge') : t('update_available_badge')}
              </Text>
            </View>
            <Text style={styles.title}>
              {isMandatory ? t('update_required_title') : t('update_available_title')}
            </Text>
            {isMandatory ? (
              <Text style={styles.subtitle}>
                {availableRelease.requiredReason || t('update_required_subtitle')}
              </Text>
            ) : null}
          </View>

          <View style={styles.body}>
            <View style={styles.metaRow}>
              <Text style={styles.infoText}>
                <Text style={styles.infoStrong}>{t('update_version_label')} </Text>
                {availableRelease.versionName}
              </Text>
              {availableRelease.fileSizeBytes ? (
                <Text style={styles.infoText}>
                  <Text style={styles.infoStrong}>{t('update_size_label')} </Text>
                  {(availableRelease.fileSizeBytes / (1024 * 1024)).toFixed(1)} MB
                </Text>
              ) : null}
            </View>
            <Text style={styles.sectionLabel}>{t('update_release_notes')}</Text>
            <ScrollView style={[styles.notes, { height: notesHeight }]} nestedScrollEnabled showsVerticalScrollIndicator>
              <Text style={styles.notesText}>
                {availableRelease.releaseNotes || t('update_release_notes_empty')}
              </Text>
            </ScrollView>
            {statusMessage ? <Text style={styles.statusText}>{statusMessage}</Text> : null}
            {errorMessage ? <Text style={styles.errorText}>{errorMessage}</Text> : null}
          </View>

          <View style={styles.footer}>
            {!isMandatory ? (
              <UpdateButton label={t('update_button_later')} onPress={dismissPrompt} />
            ) : null}
            <UpdateButton label={primaryLabel} primary disabled={isDownloading} onPress={() => { void startUpdate(); }} />
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}
