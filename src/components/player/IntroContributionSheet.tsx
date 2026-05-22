import React from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../../context/ThemeContext';
import { useLanguage } from '../../context/LanguageContext';

interface IntroContributionSheetProps {
  visible: boolean;
  onClose: () => void;
  startSec: number | null;
  endSec: number | null;
  currentTime: number;
  apiKey: string;
  onApiKeyChange: (value: string) => void;
  onMarkStart: () => void;
  onMarkEnd: () => void;
  onClear: () => void;
  onSubmit: () => void;
  submitting: boolean;
  error: string | null;
  successMessage: string | null;
}

function formatTime(seconds: number | null): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) return '—';
  const total = Math.floor(seconds);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const secs = total % 60;
  if (hours > 0) return `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  return `${minutes}:${String(secs).padStart(2, '0')}`;
}

export function IntroContributionSheet(props: IntroContributionSheetProps) {
  const {
    visible,
    onClose,
    startSec,
    endSec,
    currentTime,
    apiKey,
    onApiKeyChange,
    onMarkStart,
    onMarkEnd,
    onClear,
    onSubmit,
    submitting,
    error,
    successMessage,
  } = props;
  const { theme } = useTheme();
  const { t } = useLanguage();
  const insets = useSafeAreaInsets();
  const hasApiKey = apiKey.trim().length > 0;
  const canSubmit = hasApiKey && startSec != null && endSec != null && startSec < endSec && !submitting;

  return (
    <Modal visible={visible} transparent animationType="fade" statusBarTranslucent onRequestClose={onClose}>
      <View style={styles.root}>
        <Pressable style={styles.backdrop} onPress={onClose} />
        <View style={[styles.sheetWrap, { paddingBottom: insets.bottom + 20 }]}>
          <View style={[styles.sheet, { backgroundColor: theme.colors.cardBg, borderColor: theme.colors.border }]}>
            <View style={[styles.header, { borderBottomColor: theme.colors.border }]}>
              <View>
                <Text style={[styles.title, { color: theme.colors.textPrimary }]}>{t('skip_intro_contribute_title')}</Text>
                <Text style={[styles.subtitle, { color: theme.colors.textSecondary }]}>
                  {t('skip_intro_contribute_subtitle')}
                </Text>
              </View>
              <TouchableOpacity onPress={onClose} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
                <Ionicons name="close" size={22} color={theme.colors.textSecondary} />
              </TouchableOpacity>
            </View>

            <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
              <View style={[styles.statusCard, { backgroundColor: theme.colors.inputBg, borderColor: theme.colors.border }]}>
                <Text style={[styles.statusLabel, { color: theme.colors.textSecondary }]}>{t('skip_intro_current_position')}</Text>
                <Text style={[styles.statusValue, { color: theme.colors.textPrimary }]}>{formatTime(currentTime)}</Text>
              </View>

              <View style={styles.markerRow}>
                <TouchableOpacity
                  activeOpacity={0.84}
                  style={[styles.markerButton, { backgroundColor: theme.colors.inputBg, borderColor: theme.colors.border }]}
                  onPress={onMarkStart}
                >
                  <Ionicons name="flag-outline" size={18} color={theme.colors.accent} />
                  <Text style={[styles.markerButtonLabel, { color: theme.colors.textPrimary }]}>{t('skip_intro_mark_start')}</Text>
                  <Text style={[styles.markerButtonValue, { color: theme.colors.textSecondary }]}>{formatTime(startSec)}</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  activeOpacity={0.84}
                  style={[styles.markerButton, { backgroundColor: theme.colors.inputBg, borderColor: theme.colors.border }]}
                  onPress={onMarkEnd}
                >
                  <Ionicons name="flag" size={18} color={theme.colors.accent} />
                  <Text style={[styles.markerButtonLabel, { color: theme.colors.textPrimary }]}>{t('skip_intro_mark_end')}</Text>
                  <Text style={[styles.markerButtonValue, { color: theme.colors.textSecondary }]}>{formatTime(endSec)}</Text>
                </TouchableOpacity>
              </View>

              <View style={[styles.fieldCard, { backgroundColor: theme.colors.inputBg, borderColor: theme.colors.border }]}>
                <Text style={[styles.fieldTitle, { color: theme.colors.textPrimary }]}>{t('skip_intro_api_key')}</Text>
                <Text style={[styles.fieldHint, { color: theme.colors.textSecondary }]}>{t('skip_intro_api_key_sub')}</Text>
                <TextInput
                  value={apiKey}
                  onChangeText={onApiKeyChange}
                  placeholder={t('skip_intro_api_key_placeholder')}
                  placeholderTextColor={theme.colors.placeholder}
                  autoCapitalize="none"
                  autoCorrect={false}
                  style={[
                    styles.input,
                    {
                      color: theme.colors.textPrimary,
                      backgroundColor: theme.colors.cardBgElevated ?? theme.colors.cardBg,
                      borderColor: theme.colors.border,
                    },
                  ]}
                />
              </View>

              {error ? (
                <View style={[styles.feedbackCard, styles.errorCard]}>
                  <Text style={[styles.feedbackText, { color: theme.colors.textPrimary }]}>{error}</Text>
                </View>
              ) : null}
              {successMessage ? (
                <View style={[styles.feedbackCard, styles.successCard]}>
                  <Text style={[styles.feedbackText, { color: theme.colors.textPrimary }]}>{successMessage}</Text>
                </View>
              ) : null}
            </ScrollView>

            <View style={[styles.footer, { borderTopColor: theme.colors.border }]}>
              <TouchableOpacity
                activeOpacity={0.82}
                onPress={onClear}
                style={[styles.secondaryButton, { borderColor: theme.colors.border }]}
              >
                <Text style={[styles.secondaryButtonText, { color: theme.colors.textPrimary }]}>{t('skip_intro_clear_marks')}</Text>
              </TouchableOpacity>
              <TouchableOpacity
                activeOpacity={0.86}
                disabled={!canSubmit}
                onPress={onSubmit}
                style={[
                  styles.primaryButton,
                  {
                    backgroundColor: canSubmit ? theme.colors.accent : 'rgba(255,255,255,0.14)',
                    opacity: canSubmit ? 1 : 0.7,
                  },
                ]}
              >
                <Text style={[styles.primaryButtonText, { color: canSubmit ? theme.colors.buttonText : '#fff' }]}>
                  {submitting ? t('skip_intro_submitting') : t('skip_intro_submit')}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.72)',
  },
  sheetWrap: {
    paddingHorizontal: 14,
  },
  sheet: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    borderWidth: 1,
    overflow: 'hidden',
    maxHeight: '82%',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    paddingHorizontal: 18,
    paddingTop: 18,
    paddingBottom: 14,
    borderBottomWidth: 1,
    gap: 14,
  },
  title: {
    fontSize: 18,
    fontWeight: '800',
  },
  subtitle: {
    fontSize: 12,
    lineHeight: 18,
    marginTop: 4,
  },
  content: {
    padding: 18,
    gap: 14,
  },
  statusCard: {
    borderWidth: 1,
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  statusLabel: {
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  statusValue: {
    fontSize: 22,
    fontWeight: '800',
    marginTop: 4,
  },
  markerRow: {
    gap: 12,
  },
  markerButton: {
    borderWidth: 1,
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 14,
    gap: 6,
  },
  markerButtonLabel: {
    fontSize: 14,
    fontWeight: '700',
  },
  markerButtonValue: {
    fontSize: 12,
    fontWeight: '600',
  },
  fieldCard: {
    borderWidth: 1,
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 14,
    gap: 8,
  },
  fieldTitle: {
    fontSize: 14,
    fontWeight: '700',
  },
  fieldHint: {
    fontSize: 12,
    lineHeight: 18,
  },
  input: {
    minHeight: 46,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 12,
    fontSize: 13,
  },
  feedbackCard: {
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  errorCard: {
    backgroundColor: 'rgba(239,68,68,0.14)',
    borderWidth: 1,
    borderColor: 'rgba(239,68,68,0.24)',
  },
  successCard: {
    backgroundColor: 'rgba(34,197,94,0.14)',
    borderWidth: 1,
    borderColor: 'rgba(34,197,94,0.24)',
  },
  feedbackText: {
    fontSize: 12,
    lineHeight: 18,
  },
  footer: {
    flexDirection: 'row',
    gap: 10,
    paddingHorizontal: 18,
    paddingVertical: 16,
    borderTopWidth: 1,
  },
  secondaryButton: {
    flex: 1,
    minHeight: 46,
    borderWidth: 1,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.04)',
  },
  secondaryButtonText: {
    fontSize: 13,
    fontWeight: '700',
  },
  primaryButton: {
    flex: 1,
    minHeight: 46,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryButtonText: {
    fontSize: 13,
    fontWeight: '800',
  },
});
