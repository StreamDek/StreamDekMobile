import React, { useLayoutEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Keyboard,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useRoute } from '@react-navigation/native';
import { useTheme } from '../context/ThemeContext';
import { useProfile } from '../context/ProfileContext';
import { MAX_PROFILES_PER_ACCOUNT, PROFILE_AVATARS, type StreamProfile } from '../utils/profileApi';
import { COMMON_SUBTITLE_LANGUAGES } from '../services/subtitles/SubtitleProvider';
import { ActionSheet } from '../components/ActionSheet';
import { ProfilePinComposer } from '../components/ProfilePinComposer';

const PIN_LENGTH = 4;

const LANGUAGE_OPTIONS = [
  { code: '', label: 'None' },
  ...COMMON_SUBTITLE_LANGUAGES.map(l => ({ code: l.code, label: l.label })),
];

export function EditProfileScreen() {
  const navigation = useNavigation<any>();
  const route = useRoute<any>();
  const insets = useSafeAreaInsets();
  const { theme, resolvedAppearance } = useTheme();
  const c = theme.colors;
  const isLightMonochrome = resolvedAppearance === 'light' && theme.id === 'monochrome';
  const visibleAccent = resolvedAppearance === 'light' && (theme.id === 'monochrome' || c.accent === '#ffffff' || c.accent === '#fff')
    ? c.textPrimary
    : c.accent;

  const { profiles, createProfile, updateProfile, setProfilePin, setDefaultProfile } = useProfile();

  const profileId: string | null = route.params?.profileId ?? null;
  const existing: StreamProfile | undefined = profiles.find(p => p.id === profileId);
  const isNew = !existing;

  const [name, setName] = useState(existing?.name ?? '');
  const [avatarIndex, setAvatarIndex] = useState(existing?.avatarIndex ?? 0);
  const [subtitleLanguage, setSubtitleLanguage] = useState(existing?.subtitleLanguage ?? '');
  const [audioLanguage, setAudioLanguage] = useState(existing?.audioLanguage ?? '');
  const [subtitleSheetVisible, setSubtitleSheetVisible] = useState(false);
  const [audioSheetVisible, setAudioSheetVisible] = useState(false);
  const [showPinSection, setShowPinSection] = useState(existing?.hasPinSet ?? false);
  const [pinValue, setPinValue] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [removingPin, setRemovingPin] = useState(false);
  const [saving, setSaving] = useState(false);
  const [nameError, setNameError] = useState('');
  const [removePinConfirmVisible, setRemovePinConfirmVisible] = useState(false);
  const nameInputRef = React.useRef<TextInput | null>(null);

  useLayoutEffect(() => {
    navigation.setOptions({ title: isNew ? 'New Profile' : 'Edit Profile' });
  }, [navigation, isNew]);

  const validateAndSave = async () => {
    const trimmed = name.trim();
    if (!trimmed) { setNameError('Profile name is required.'); return; }
    setNameError('');

    if (isNew && profiles.length >= MAX_PROFILES_PER_ACCOUNT) {
      Alert.alert('Profile limit reached', `You can only have up to ${MAX_PROFILES_PER_ACCOUNT} profiles.`);
      return;
    }

    if (showPinSection && !existing?.hasPinSet && !removingPin) {
      if (pinValue.length !== PIN_LENGTH || pinValue !== confirmPin) return;
    }

    setSaving(true);
    try {
      if (isNew) {
        const { error } = await createProfile({
          name: trimmed, avatarIndex,
          subtitleLanguage: subtitleLanguage || null,
          audioLanguage: audioLanguage || null,
          pin: (showPinSection && pinValue.length === PIN_LENGTH) ? pinValue : undefined,
        });
        if (error) { Alert.alert('Error', error); return; }
      } else {
        const { error } = await updateProfile(profileId!, {
          name: trimmed, avatarIndex,
          subtitleLanguage: subtitleLanguage || null,
          audioLanguage: audioLanguage || null,
        });
        if (error) { Alert.alert('Error', error); return; }
        if (removingPin) {
          await setProfilePin(profileId!, null);
        } else if (showPinSection && pinValue.length === PIN_LENGTH && pinValue === confirmPin) {
          await setProfilePin(profileId!, pinValue);
        }
      }
      navigation.goBack();
    } finally {
      setSaving(false);
    }
  };

  const handleRemovePin = () => setRemovePinConfirmVisible(true);
  const doRemovePin = () => {
    setRemovingPin(true); setShowPinSection(false); setPinValue(''); setConfirmPin('');
  };

  const dismissNameKeyboard = () => {
    nameInputRef.current?.blur();
    Keyboard.dismiss();
  };

  const subtitleLabel = LANGUAGE_OPTIONS.find(l => l.code === subtitleLanguage)?.label ?? 'None';
  const audioLabel   = LANGUAGE_OPTIONS.find(l => l.code === audioLanguage)?.label ?? 'None';

  const subtitleActions = LANGUAGE_OPTIONS.map(lang => ({
    label: lang.label,
    variant: (lang.code === subtitleLanguage ? 'accent' : 'default') as any,
    onPress: () => setSubtitleLanguage(lang.code),
  }));
  const audioActions = LANGUAGE_OPTIONS.map(lang => ({
    label: lang.label,
    variant: (lang.code === audioLanguage ? 'accent' : 'default') as any,
    onPress: () => setAudioLanguage(lang.code),
  }));

  // c.buttonText is the correct contrast colour for text on c.accent backgrounds
  // (e.g. Monochrome dark: accent=#fff, buttonText=#111)
  const saveBg   = c.accent;
  const saveText = c.buttonText;

  // Border for unselected pills — use c.border which is always a valid colour
  return (
    <View style={{ flex: 1, backgroundColor: c.bg }}>
      {/* ── Fixed header ── */}
      <View style={[styles.header, {
        paddingTop: insets.top + 16,
        backgroundColor: c.bg,
        borderBottomColor: c.border,
      }]}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerBtn} activeOpacity={0.7}>
          <Ionicons name="close" size={22} color={c.textPrimary} />
        </TouchableOpacity>
        <View style={styles.headerTitleWrap}>
          <Text style={[styles.headerEyebrow, { color: c.mutedText }]}>Profiles</Text>
          <Text style={[styles.headerTitle, { color: c.textPrimary }]}>
            {isNew ? 'New Profile' : 'Edit Profile'}
          </Text>
        </View>
        <View style={styles.headerSpacer} />
      </View>

      {/* ── Keyboard-aware scroll area ── */}
      <ScrollView
          contentContainerStyle={[styles.body, { paddingBottom: insets.bottom + 112 }]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          {/* ── Avatar picker — horizontal scroll ── */}
          <Text style={[styles.sectionLabel, { color: c.mutedText }]}>AVATAR</Text>
          <View style={[styles.card, { backgroundColor: c.cardBg, borderColor: c.border }]}>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.avatarScroll}
              bounces={false}
            >
              {PROFILE_AVATARS.map(av => {
                const selected = av.id === avatarIndex;
                return (
                  <TouchableOpacity
                    key={av.id}
                    onPress={() => setAvatarIndex(av.id)}
                    activeOpacity={0.75}
                    style={[
                      styles.avatarOption,
                      selected && { borderColor: visibleAccent, borderWidth: 2.5 },
                    ]}
                  >
                    <Image source={av.image} style={styles.avatarImg} />
                    {selected && (
                      <View style={[styles.avatarCheck, { backgroundColor: visibleAccent, borderColor: c.bg, borderWidth: 2 }]}>
                        <Ionicons name="checkmark" size={10} color={c.buttonText} />
                      </View>
                    )}
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          </View>

          {/* ── Profile name ── */}
          <Text style={[styles.sectionLabel, { color: c.mutedText }]}>PROFILE NAME</Text>
          <View style={[styles.card, { backgroundColor: c.cardBg, borderColor: c.border }]}>
            <TextInput
              ref={nameInputRef}
              style={[styles.nameInput, { color: c.textPrimary }]}
              placeholder="e.g. Alex, Kids, Work…"
              placeholderTextColor={c.mutedText}
              value={name}
              onChangeText={t => { setName(t); setNameError(''); }}
              maxLength={30}
              returnKeyType="done"
              autoCorrect={false}
              onSubmitEditing={dismissNameKeyboard}
            />
          </View>
          {nameError ? <Text style={styles.fieldError}>{nameError}</Text> : null}

          {/* ── Language preferences ── */}
          <Text style={[styles.sectionLabel, { color: c.mutedText }]}>LANGUAGE PREFERENCES</Text>
          <View style={[styles.card, { backgroundColor: c.cardBg, borderColor: c.border }]}>
            <TouchableOpacity style={styles.optionRow} onPress={() => { dismissNameKeyboard(); setSubtitleSheetVisible(true); }} activeOpacity={0.7}>
              <Text style={[styles.optionLabel, { color: c.textPrimary }]}>Subtitle language</Text>
              <View style={styles.optionValueRow}>
                <Text style={[styles.optionValue, { color: c.mutedText }]}>{subtitleLabel}</Text>
                <Ionicons name="chevron-forward" size={16} color={c.mutedText} />
              </View>
            </TouchableOpacity>
            <View style={[styles.divider, { backgroundColor: c.border }]} />
            <TouchableOpacity style={styles.optionRow} onPress={() => { dismissNameKeyboard(); setAudioSheetVisible(true); }} activeOpacity={0.7}>
              <Text style={[styles.optionLabel, { color: c.textPrimary }]}>Audio language</Text>
              <View style={styles.optionValueRow}>
                <Text style={[styles.optionValue, { color: c.mutedText }]}>{audioLabel}</Text>
                <Ionicons name="chevron-forward" size={16} color={c.mutedText} />
              </View>
            </TouchableOpacity>
          </View>

          {/* ── Profile PIN ── */}
          <Text style={[styles.sectionLabel, { color: c.mutedText }]}>PROFILE PIN</Text>
          <View style={[styles.card, { backgroundColor: c.cardBg, borderColor: c.border }]}>
            <View style={styles.optionRow}>
              <View style={{ flex: 1 }}>
                <Text style={[styles.optionLabel, { color: c.textPrimary }]}>
                  {existing?.hasPinSet && !removingPin ? 'PIN is enabled' : 'Enable PIN lock'}
                </Text>
                <Text style={[styles.optionSub, { color: c.mutedText }]}>
                  Require a 4-digit PIN to access this profile.
                </Text>
              </View>
              {existing?.hasPinSet && !removingPin ? (
                <TouchableOpacity
                  style={[styles.pinActionBtn, { backgroundColor: 'rgba(239,68,68,0.15)', borderColor: 'rgba(239,68,68,0.4)', borderWidth: 1 }]}
                  onPress={() => { dismissNameKeyboard(); handleRemovePin(); }}
                  activeOpacity={0.75}
                >
                  <Text style={{ color: '#ef4444', fontSize: 13, fontWeight: '600' }}>Remove</Text>
                </TouchableOpacity>
              ) : (
                <TouchableOpacity
                  style={styles.pinActionBtn}
                  onPress={() => { dismissNameKeyboard(); setShowPinSection(s => !s); setPinValue(''); setConfirmPin(''); }}
                  activeOpacity={0.75}
                >
                  <Text style={{ color: c.textPrimary, fontSize: 13, fontWeight: '600' }}>
                    {showPinSection ? 'Cancel' : existing?.hasPinSet ? 'Change' : 'Set PIN'}
                  </Text>
                </TouchableOpacity>
              )}
            </View>

            {showPinSection && !removingPin && (
              <>
                <View style={[styles.divider, { backgroundColor: c.border }]} />
                <ProfilePinComposer
                  pinLabel={existing?.hasPinSet ? 'New PIN' : 'PIN'}
                  pinValue={pinValue}
                  confirmPin={confirmPin}
                  onChangePin={setPinValue}
                  onChangeConfirmPin={setConfirmPin}
                />
              </>
            )}
          </View>

          {!isNew && !existing?.isDefault && (
            <>
              <Text style={[styles.sectionLabel, { color: c.mutedText }]}>DEFAULT PROFILE</Text>
              <View style={[styles.card, { backgroundColor: c.cardBg, borderColor: c.border }]}>
                <TouchableOpacity
                  style={styles.optionRow}
                  onPress={async () => {
                    const result = await setDefaultProfile(profileId!);
                    if (result.error) {
                      Alert.alert('Unable to update profile', result.error);
                      return;
                    }
                    navigation.goBack();
                  }}
                  activeOpacity={0.75}
                >
                  <View style={{ flex: 1 }}>
                    <Text style={[styles.optionLabel, { color: c.textPrimary }]}>Make default profile</Text>
                    <Text style={[styles.optionSub, { color: c.mutedText }]}>
                      This profile will open first the next time you launch the app.
                    </Text>
                  </View>
                  <Ionicons name="star-outline" size={18} color={c.accentSoft} />
                </TouchableOpacity>
              </View>
            </>
          )}
      </ScrollView>

      <View style={[styles.bottomBar, { paddingBottom: insets.bottom + 12, backgroundColor: c.bg, borderTopColor: c.border }]}>
        <TouchableOpacity
          onPress={validateAndSave}
          style={[styles.bottomSaveBtn, { backgroundColor: saveBg, borderWidth: isLightMonochrome ? 1 : 0, borderColor: isLightMonochrome ? 'rgba(17,24,39,0.18)' : 'transparent' }, saving && styles.bottomSaveBtnDisabled]}
          activeOpacity={0.8}
          disabled={saving}
        >
          {saving
            ? <ActivityIndicator size="small" color={saveText} />
            : <Text style={[styles.bottomSaveBtnText, { color: saveText }]}>Save</Text>
          }
        </TouchableOpacity>
      </View>

      <ActionSheet
        visible={subtitleSheetVisible}
        onClose={() => setSubtitleSheetVisible(false)}
        title="Subtitle Language"
        actions={subtitleActions}
      />
      <ActionSheet
        visible={audioSheetVisible}
        onClose={() => setAudioSheetVisible(false)}
        title="Audio Language"
        actions={audioActions}
      />
      <ConfirmSheet
        visible={removePinConfirmVisible}
        onClose={() => setRemovePinConfirmVisible(false)}
        title="Remove PIN"
        message="This profile will no longer require a PIN to access."
        icon="lock-open-outline"
        confirmLabel="Remove PIN"
        variant="destructive"
        onConfirm={doRemovePin}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingHorizontal: 16,
    paddingBottom: 18,
    borderBottomWidth: StyleSheet.hairlineWidth,
    gap: 12,
  },
  headerBtn: { padding: 4, marginTop: 12 },
  headerTitleWrap: { flex: 1, gap: 4 },
  headerSpacer: { width: 64 },
  headerEyebrow: { fontSize: 12, fontWeight: '700', letterSpacing: 0.8, textTransform: 'uppercase' },
  headerTitle: { fontSize: 28, fontWeight: '800', letterSpacing: -0.3 },
  body: { padding: 16, gap: 8 },
  bottomBar: {
    paddingHorizontal: 16,
    paddingTop: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  bottomSaveBtn: {
    minHeight: 54,
    borderRadius: 27,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bottomSaveBtnDisabled: { opacity: 0.72 },
  bottomSaveBtnText: { fontSize: 16, fontWeight: '700' },
  sectionLabel: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
    marginLeft: 4,
  },
  card: { borderRadius: 14, borderWidth: StyleSheet.hairlineWidth, overflow: 'hidden', marginBottom: 16 },
  // Avatar horizontal scroll
  avatarScroll: { paddingVertical: 16, paddingHorizontal: 12, gap: 12, flexDirection: 'row' },
  avatarOption: { borderRadius: 54, padding: 4, position: 'relative' },
  avatarImg: { width: 84, height: 84, borderRadius: 42 },
  avatarCheck: {
    position: 'absolute', bottom: 1, right: 1,
    width: 22, height: 22, borderRadius: 11,
    alignItems: 'center', justifyContent: 'center',
  },
  // Name
  nameInput: { paddingHorizontal: 16, paddingVertical: 14, fontSize: 16 },
  fieldError: { color: '#ef4444', fontSize: 12, marginLeft: 4, marginBottom: 4 },
  // Options
  optionRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 14, gap: 12 },
  optionLabel: { fontSize: 15, fontWeight: '500' },
  optionSub: { fontSize: 12, marginTop: 2 },
  optionValueRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  optionValue: { fontSize: 14 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
  // PIN
  pinActionBtn: { paddingVertical: 7, paddingHorizontal: 14, borderRadius: 20 },
});


