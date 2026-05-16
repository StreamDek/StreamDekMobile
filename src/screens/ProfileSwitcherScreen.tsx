import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Animated,
  Dimensions,
  Image,
  Keyboard,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { LinearGradient } from 'expo-linear-gradient';
import { API_BASE } from '../constants/api';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { useProfile } from '../context/ProfileContext';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { ActionSheet } from '../components/ActionSheet';
import { RadialLoaderScreen } from '../components/RadialLoaderScreen';
import { ProfilePinComposer } from '../components/ProfilePinComposer';
import {
  MAX_PROFILES_PER_ACCOUNT,
  PROFILE_AVATARS,
  type StreamProfile,
} from '../utils/profileApi';
import { peekProfileLaunchBootstrap, type ProfileLaunchHeroItem } from '../utils/profileLaunchBootstrap';
import { COMMON_SUBTITLE_LANGUAGES } from '../services/subtitles/SubtitleProvider';

type Step = 'grid' | 'pin' | 'manage' | 'edit';

const PIN_LENGTH = 4;
const { height: SCREEN_HEIGHT } = Dimensions.get('window');
const HERO_HEIGHT = Math.round(SCREEN_HEIGHT * 0.72);
const HERO_BLUR_SECTION_HEIGHT = Math.round(HERO_HEIGHT * 0.42) + 10;
const HERO_BLUR_SECTION_OFFSET = HERO_HEIGHT - HERO_BLUR_SECTION_HEIGHT;
const AnimatedImage = Animated.createAnimatedComponent(Image);
const LANGUAGE_OPTIONS = [
  { code: '', label: 'None' },
  ...COMMON_SUBTITLE_LANGUAGES.map(l => ({ code: l.code, label: l.label })),
];

interface Props {
  asOverlay?: boolean;
  onDismiss?: () => void;
}

export function ProfileSwitcherScreen({ asOverlay = false, onDismiss }: Props) {
  const navigation = useNavigation<any>();
  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const { theme, resolvedAppearance } = useTheme();
  const c = theme.colors;
  const isLight = resolvedAppearance === 'light';
  const isLightMonochrome = isLight && theme.id === 'monochrome';
  const pageBg = c.bg;
  const cardBg = isLight ? c.cardBg : 'rgba(255,255,255,0.08)';
  const cardBorder = isLight ? c.border : 'rgba(255,255,255,0.14)';
  const primaryText = c.textPrimary;
  const secondaryText = c.textSecondary;
  const tertiaryText = isLight ? c.mutedText : 'rgba(255,255,255,0.5)';
  const iconColor = isLight ? c.textPrimary : '#fff';
  const dashedBg = isLight ? c.cardBgElevated : 'rgba(255,255,255,0.08)';
  const dashedBorder = isLight ? c.border : 'rgba(255,255,255,0.2)';
  const destructiveText = isLight ? '#dc2626' : '#fca5a5';
  const saveButtonBg = c.accent;
  const saveButtonText = c.buttonText;

  const {
    profiles, loadingProfiles, profilesReady, activeProfile, profileSwitching,
    setActiveProfile, deleteProfile, verifyProfilePin,
    createProfile, updateProfile, setProfilePin, setDefaultProfile,
  } = useProfile();
  const preloadedLaunchBootstrap = asOverlay ? peekProfileLaunchBootstrap(user?.uid) : null;

  // ── Step navigation ───────────────────────────────────────────────────────
  const [step, setStep] = useState<Step>('grid');
  const [prevStep, setPrevStep] = useState<Step>('grid');
  const fadeAnim  = useRef(new Animated.Value(0)).current;
  const slideAnim = useRef(new Animated.Value(30)).current;
  const heroOpacity = useRef(new Animated.Value(1)).current;
  const heroScale = useRef(new Animated.Value(1.015)).current;
  const heroTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const [launchHeroItems, setLaunchHeroItems] = useState<ProfileLaunchHeroItem[]>(preloadedLaunchBootstrap?.heroItems ?? []);
  const [launchHeroIndex, setLaunchHeroIndex] = useState(0);
  const [launchPrevHeroIndex, setLaunchPrevHeroIndex] = useState<number | null>(null);

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fadeAnim,  { toValue: 1, duration: 300, useNativeDriver: true }),
      Animated.timing(slideAnim, { toValue: 0, duration: 300, useNativeDriver: true }),
    ]).start();
  }, []);

  useEffect(() => {
    if (!(asOverlay && step === 'grid')) return;
    let cancelled = false;

    (async () => {
      try {
        const [movieRes, tvRes] = await Promise.all([
          fetch(`${API_BASE}/tmdb/trending/movie`),
          fetch(`${API_BASE}/tmdb/trending/tv`),
        ]);
        const [movieJson, tvJson] = await Promise.all([
          movieRes.json(),
          tvRes.json(),
        ]);

        if (cancelled) return;

        const combined = [
          ...((movieJson?.results ?? []) as any[]).map(item => ({ ...item, type: 'movie' as const })),
          ...((tvJson?.results ?? []) as any[]).map(item => ({ ...item, type: 'tv' as const })),
        ]
          .filter(item => !!(item.backdrop ?? item.poster))
          .slice(0, 10)
          .map(item => ({
            id: `${item.type}:${item.tmdbId ?? item.id}`,
            type: item.type,
            title: item.title ?? '',
            backdrop: item.backdrop ?? item.poster ?? null,
            poster: item.poster ?? null,
            description: item.description ?? '',
            year: item.year,
          }));

        setLaunchHeroItems(combined);
        setLaunchHeroIndex(0);
        setLaunchPrevHeroIndex(null);
        heroOpacity.setValue(1);
        heroScale.setValue(1.015);
        Animated.timing(heroScale, {
          toValue: 1.06,
          duration: 14000,
          useNativeDriver: true,
        }).start();
      } catch {
        if (!cancelled) setLaunchHeroItems([]);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [asOverlay, heroOpacity, heroScale, step]);

  const advanceLaunchHero = useCallback(() => {
    setLaunchHeroIndex(currentIndex => {
      if (launchHeroItems.length <= 1) return currentIndex;
      const nextIndex = (currentIndex + 1) % launchHeroItems.length;
      setLaunchPrevHeroIndex(currentIndex);
      heroOpacity.setValue(0);
      heroScale.setValue(1.015);
      Animated.parallel([
        Animated.timing(heroOpacity, {
          toValue: 1,
          duration: 900,
          useNativeDriver: true,
        }),
        Animated.timing(heroScale, {
          toValue: 1.06,
          duration: 14000,
          useNativeDriver: true,
        }),
      ]).start(() => setLaunchPrevHeroIndex(null));
      return nextIndex;
    });
  }, [heroOpacity, heroScale, launchHeroItems.length]);

  useEffect(() => {
    if (!(asOverlay && step === 'grid') || launchHeroItems.length <= 1) return;
    if (heroTimerRef.current) clearInterval(heroTimerRef.current);
    heroTimerRef.current = setInterval(() => {
      advanceLaunchHero();
    }, 3_500);

    return () => {
      if (heroTimerRef.current) {
        clearInterval(heroTimerRef.current);
        heroTimerRef.current = null;
      }
    };
  }, [advanceLaunchHero, asOverlay, launchHeroItems.length, step]);

  const goToStep = useCallback((next: Step, setup?: () => void) => {
    Animated.timing(fadeAnim, { toValue: 0, duration: 140, useNativeDriver: true }).start(() => {
      setup?.();
      setStep(next);
      Animated.timing(fadeAnim, { toValue: 1, duration: 200, useNativeDriver: true }).start();
    });
  }, [fadeAnim]);

  // ── PIN state ─────────────────────────────────────────────────────────────
  const [targetProfile, setTargetProfile] = useState<StreamProfile | null>(null);
  const [pin, setPin] = useState('');
  const [pinError, setPinError] = useState('');
  const [pinLoading, setPinLoading] = useState(false);

  const handleSelectProfile = useCallback((profile: StreamProfile) => {
    if (profile.hasPinSet) {
      goToStep('pin', () => { setTargetProfile(profile); setPin(''); setPinError(''); });
    } else {
      void setActiveProfile(profile).then(() => {
        navigation.navigate('Main', { screen: 'Home' });
        onDismiss?.();
      });
    }
  }, [goToStep, navigation, onDismiss, setActiveProfile]);

  useEffect(() => {
    if (pin.length !== 4 || !targetProfile) return;
    let cancelled = false;
    setPinLoading(true);
    verifyProfilePin(targetProfile.id, pin).then(valid => {
      if (cancelled) return;
      setPinLoading(false);
      if (valid) {
        void setActiveProfile(targetProfile).then(() => {
          navigation.navigate('Main', { screen: 'Home' });
          onDismiss?.();
        });
      }
      else { setPinError('Incorrect PIN. Try again.'); setPin(''); }
    });
    return () => { cancelled = true; };
  }, [navigation, onDismiss, pin, setActiveProfile, targetProfile]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Delete state — separate visibility from id so onClose() doesn't race onConfirm() ──
  const [deleteConfirmVisible, setDeleteConfirmVisible] = useState(false);
  const [deleteTargetId, setDeleteTargetId]     = useState<string | null>(null);
  const [deleteTargetName, setDeleteTargetName] = useState('');

  const requestDelete = useCallback((profile: StreamProfile) => {
    setDeleteTargetId(profile.id);
    setDeleteTargetName(profile.name);
    setDeleteConfirmVisible(true);
  }, []);

  const doDelete = useCallback(async () => {
    const id = deleteTargetId;
    if (!id) return;
    const profile = profiles.find(item => item.id === id);
    const fallbackProfile = profiles.find(item => item.id !== id) ?? null;
    setDeleteConfirmVisible(false);
    if (profile?.isDefault && fallbackProfile) {
      const promoteResult = await setDefaultProfile(fallbackProfile.id);
      if (promoteResult.error) {
        Alert.alert('Unable to delete profile', promoteResult.error);
        return;
      }
    }
    const result = await deleteProfile(id);
    if (result.error) {
      Alert.alert('Unable to delete profile', result.error);
      return;
    }
    setDeleteTargetId(null);
  }, [deleteProfile, deleteTargetId, profiles, setDefaultProfile]);

  // ── Edit / Create state ───────────────────────────────────────────────────
  const [editProfile, setEditProfile]   = useState<StreamProfile | null>(null);
  const [editName, setEditName]         = useState('');
  const [editAvatar, setEditAvatar]     = useState(0);
  const [editSubLang, setEditSubLang]   = useState('');
  const [editAudioLang, setEditAudioLang] = useState('');
  const [editShowPin, setEditShowPin]   = useState(false);
  const [editPinVal, setEditPinVal]     = useState('');
  const [editPinConfirm, setEditPinConfirm] = useState('');
  const [editRemovingPin, setEditRemovingPin] = useState(false);
  const [editSaving, setEditSaving]     = useState(false);
  const [editNameErr, setEditNameErr]   = useState('');
  const [subLangSheet, setSubLangSheet] = useState(false);
  const [audioLangSheet, setAudioLangSheet] = useState(false);
  const [removePinConfirm, setRemovePinConfirm] = useState(false);
  const editNameInputRef = useRef<TextInput | null>(null);

  const openEdit = useCallback((profile: StreamProfile | null, from: Step) => {
    goToStep('edit', () => {
      setPrevStep(from);
      setEditProfile(profile);
      setEditName(profile?.name ?? '');
      setEditAvatar(profile?.avatarIndex ?? 0);
      setEditSubLang(profile?.subtitleLanguage ?? '');
      setEditAudioLang(profile?.audioLanguage ?? '');
      setEditShowPin(profile?.hasPinSet ?? false);
      setEditPinVal(''); setEditPinConfirm('');
      setEditRemovingPin(false); setEditNameErr('');
    });
  }, [goToStep]);

  const dismissEditNameKeyboard = useCallback(() => {
    editNameInputRef.current?.blur();
    Keyboard.dismiss();
  }, []);

  const handleSaveEdit = useCallback(async () => {
    const trimmed = editName.trim();
    if (!trimmed) { setEditNameErr('Name is required.'); return; }
    if (editShowPin && !editProfile?.hasPinSet && !editRemovingPin) {
      if (editPinVal.length !== PIN_LENGTH || editPinVal !== editPinConfirm) return;
    }
    setEditSaving(true);
    try {
      if (editProfile) {
        const { error } = await updateProfile(editProfile.id, {
          name: trimmed, avatarIndex: editAvatar,
          subtitleLanguage: editSubLang || null, audioLanguage: editAudioLang || null,
        });
        if (error) { setEditNameErr(error); return; }
        if (editRemovingPin) await setProfilePin(editProfile.id, null);
        else if (editShowPin && editPinVal.length === PIN_LENGTH && editPinVal === editPinConfirm)
          await setProfilePin(editProfile.id, editPinVal);
      } else {
        const { error } = await createProfile({
          name: trimmed, avatarIndex: editAvatar,
          subtitleLanguage: editSubLang || null, audioLanguage: editAudioLang || null,
          pin: (editShowPin && editPinVal.length === PIN_LENGTH) ? editPinVal : undefined,
        });
        if (error) { setEditNameErr(error); return; }
      }
      goToStep(prevStep);
    } finally {
      setEditSaving(false);
    }
  }, [editName, editProfile, editAvatar, editSubLang, editAudioLang,
      editShowPin, editPinVal, editPinConfirm, editRemovingPin, prevStep,
      createProfile, updateProfile, setProfilePin, goToStep]);

  // ── Helpers ───────────────────────────────────────────────────────────────
  const avatarFor = (p: StreamProfile) =>
    PROFILE_AVATARS[Math.min(p.avatarIndex, PROFILE_AVATARS.length - 1)];

  const subLangLabel   = LANGUAGE_OPTIONS.find(l => l.code === editSubLang)?.label ?? 'None';
  const audioLangLabel = LANGUAGE_OPTIONS.find(l => l.code === editAudioLang)?.label ?? 'None';
  const subLangActions   = LANGUAGE_OPTIONS.map(l => ({ label: l.label, variant: (l.code === editSubLang ? 'accent' : 'default') as any, onPress: () => setEditSubLang(l.code) }));
  const audioLangActions = LANGUAGE_OPTIONS.map(l => ({ label: l.label, variant: (l.code === editAudioLang ? 'accent' : 'default') as any, onPress: () => setEditAudioLang(l.code) }));

  const numpadRows = [['1','2','3'],['4','5','6'],['7','8','9'],['','0','del']];

  // ── Render: Grid ─────────────────────────────────────────────────────────
  function renderGrid() {
    const allCards = [
      ...profiles.map(p => ({ type: 'profile' as const, profile: p })),
      ...(profiles.length < MAX_PROFILES_PER_ACCOUNT ? [{ type: 'add' as const, profile: null }] : []),
    ];
    const activeHero = launchHeroItems[launchHeroIndex] ?? null;
    const previousHero = launchPrevHeroIndex != null ? (launchHeroItems[launchPrevHeroIndex] ?? null) : null;
    const compactProfiles = allCards.length > MAX_PROFILES_PER_ACCOUNT;

    if (asOverlay) {
      return (
        <View style={S.launchRoot}>
          <View style={S.launchHeroArea}>
            {previousHero?.backdrop ? (
              <Image source={{ uri: previousHero.backdrop }} style={S.launchHeroImage} />
            ) : null}
            {activeHero?.backdrop ? (
              <AnimatedImage
                source={{ uri: activeHero.backdrop }}
                style={[S.launchHeroImage, { opacity: heroOpacity, transform: [{ scale: heroScale }] }]}
              />
            ) : null}
            <View style={S.launchHeroFocusBlur} pointerEvents="none">
              {previousHero?.backdrop ? (
                <Image
                  source={{ uri: previousHero.backdrop }}
                  style={S.launchHeroBlurImage}
                  blurRadius={16}
                />
              ) : null}
              {activeHero?.backdrop ? (
                <AnimatedImage
                  source={{ uri: activeHero.backdrop }}
                  style={[S.launchHeroBlurImage, { opacity: heroOpacity, transform: [{ scale: heroScale }] }]}
                  blurRadius={16}
                />
              ) : null}
              <LinearGradient
                colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.02)', 'rgba(0,0,0,0.10)', 'rgba(0,0,0,0.18)']}
                locations={[0, 0.18, 0.52, 1]}
                style={S.launchHeroBlurFeather}
              />
            </View>
            <LinearGradient
              colors={['rgba(0,0,0,0.04)', 'rgba(0,0,0,0.16)', 'rgba(0,0,0,0.6)', '#000000']}
              locations={[0, 0.28, 0.68, 1]}
              style={S.launchHeroScrim}
            />
            <LinearGradient
              colors={['rgba(0,0,0,0.0)', 'rgba(0,0,0,0.0)', 'rgba(0,0,0,0.84)', '#000000']}
              locations={[0, 0.42, 0.78, 1]}
              style={S.launchBottomBlend}
            />
            <View style={[S.launchHeroCopy, { paddingTop: insets.top + 26 }]}>
              <Text style={S.launchHeroEyebrow}>Trending now</Text>
              {activeHero?.title ? (
                <Text style={S.launchHeroTitle} numberOfLines={2}>
                  {activeHero.title}
                </Text>
              ) : null}
              {activeHero?.description ? (
                <Text style={S.launchHeroDescription} numberOfLines={3}>
                  {activeHero.description}
                </Text>
              ) : null}
            </View>
          </View>

          <View style={S.launchChooser}>
            <Text style={[S.launchHeading, compactProfiles && S.launchHeadingCompact]}>Who&apos;s watching?</Text>
            <View style={S.launchProfileGrid}>
              {allCards.map(card => {
                if (card.type === 'add') {
                  return (
                    <TouchableOpacity key="add" style={[S.launchProfileCard, compactProfiles && S.launchProfileCardCompact]} onPress={() => openEdit(null, 'grid')} activeOpacity={0.78}>
                      <View style={[S.launchAddCircle, compactProfiles && S.launchAddCircleCompact]}>
                        <Ionicons name="add" size={compactProfiles ? 28 : 32} color="rgba(255,255,255,0.78)" />
                      </View>
                      <Text style={[S.launchProfileName, compactProfiles && S.launchProfileNameCompact]}>Add Profile</Text>
                    </TouchableOpacity>
                  );
                }

                const profile = card.profile!;
                return (
                  <TouchableOpacity key={profile.id} style={[S.launchProfileCard, compactProfiles && S.launchProfileCardCompact]} onPress={() => handleSelectProfile(profile)} activeOpacity={0.78}>
                    <View style={[S.launchAvatarWrap, compactProfiles && S.launchAvatarWrapCompact]}>
                      <Image source={avatarFor(profile).image} style={[S.launchAvatar, compactProfiles && S.launchAvatarCompact]} />
                      {profile.hasPinSet ? (
                        <View style={[S.launchLockBadge, compactProfiles && S.launchLockBadgeCompact]}>
                          <Ionicons name="lock-closed" size={compactProfiles ? 11 : 12} color="#fff" />
                        </View>
                      ) : null}
                      {profile.id === activeProfile?.id ? (
                        <View style={[S.launchActiveBadge, compactProfiles && S.launchActiveBadgeCompact]}>
                          <Ionicons name="checkmark" size={compactProfiles ? 10 : 11} color="#fff" />
                        </View>
                      ) : null}
                    </View>
                    <Text style={[S.launchProfileName, compactProfiles && S.launchProfileNameCompact]} numberOfLines={1}>{profile.name}</Text>
                  </TouchableOpacity>
                );
              })}
            </View>
            <TouchableOpacity style={S.launchManageBtn} onPress={() => goToStep('manage')} activeOpacity={0.78}>
              <Text style={S.launchManageText}>Manage Profiles</Text>
            </TouchableOpacity>
          </View>
        </View>
      );
    }

    return (
      <ScrollView contentContainerStyle={[S.gridContent, { paddingTop: insets.top + 28, paddingBottom: insets.bottom + 32 }]} showsVerticalScrollIndicator={false}>
        <Text style={[S.heading, { color: primaryText }]}>Who's watching?</Text>
        <View style={S.profileGrid}>
          {allCards.map((card, idx) => {
            if (card.type === 'add') {
              return (
                <TouchableOpacity key="add" style={S.profileCard} onPress={() => openEdit(null, 'grid')} activeOpacity={0.75}>
                  <View style={[S.addCircle, { backgroundColor: dashedBg, borderColor: dashedBorder }]}><Ionicons name="add" size={44} color={tertiaryText} /></View>
                  <Text style={[S.profileName, { color: secondaryText }]}>Add Profile</Text>
                </TouchableOpacity>
              );
            }
            const profile = card.profile!;
            return (
              <TouchableOpacity key={profile.id} style={S.profileCard} onPress={() => handleSelectProfile(profile)} activeOpacity={0.75}>
                <View style={S.avatarWrapper}>
                  <Image source={avatarFor(profile).image} style={S.avatarLg} />
                  {profile.hasPinSet && <View style={[S.lockBadge, { backgroundColor: isLight ? 'rgba(17,24,39,0.82)' : 'rgba(0,0,0,0.85)', borderColor: pageBg }]}><Ionicons name="lock-closed" size={13} color="#fff" /></View>}
                  {profile.id === activeProfile?.id && <View style={S.activeBadge}><Ionicons name="checkmark" size={11} color="#fff" /></View>}
                </View>
                <Text style={[S.profileName, { color: secondaryText }]} numberOfLines={1}>{profile.name}</Text>
              </TouchableOpacity>
            );
          })}
        </View>
        <TouchableOpacity style={[S.manageBtn, { borderColor: cardBorder, backgroundColor: isLight ? cardBg : 'transparent' }]} onPress={() => goToStep('manage')} activeOpacity={0.75}>
          <Text style={[S.manageBtnText, { color: secondaryText }]}>Manage Profiles</Text>
        </TouchableOpacity>
        {!asOverlay && (
          <TouchableOpacity onPress={onDismiss} style={S.cancelBtn} activeOpacity={0.7}>
            <Text style={[S.cancelBtnText, { color: tertiaryText }]}>Cancel</Text>
          </TouchableOpacity>
        )}
      </ScrollView>
    );
  }

  // ── Render: PIN ───────────────────────────────────────────────────────────
  function renderPin() {
    return (
      <View style={[S.pinContainer, { paddingTop: insets.top + 28, paddingBottom: insets.bottom + 32 }]}>
        <TouchableOpacity style={S.backBtn} onPress={() => goToStep('grid', () => { setPin(''); setPinError(''); })} activeOpacity={0.7}>
          <Ionicons name="arrow-back" size={22} color={iconColor} />
        </TouchableOpacity>
        {targetProfile && <Image source={avatarFor(targetProfile).image} style={S.avatarMd} />}
        <Text style={[S.pinProfileName, { color: primaryText }]}>{targetProfile?.name}</Text>
        <Text style={[S.pinPrompt, { color: secondaryText }]}>Enter your PIN</Text>
        <View style={S.pinDots}>
          {[0,1,2,3].map(i => <View key={i} style={[S.pinDot, pin.length > i ? S.pinDotFilled : S.pinDotEmpty, pin.length > i ? { backgroundColor: primaryText } : { backgroundColor: isLight ? 'rgba(17,24,39,0.16)' : 'rgba(255,255,255,0.25)' }]} />)}
        </View>
        {pinError ? <Text style={S.pinError}>{pinError}</Text> : <View style={{ height: 20 }} />}
        {pinLoading ? <ActivityIndicator color="#fff" style={{ marginTop: 24 }} /> : (
          <View style={S.numpad}>
            {numpadRows.map((row, ri) => (
              <View key={ri} style={S.numpadRow}>
                {row.map((key, ki) => {
                  if (!key) return <View key={ki} style={S.numpadKey} />;
                  if (key === 'del') return (
                    <TouchableOpacity key={ki} style={S.numpadKey} onPress={() => { setPin(p => p.slice(0,-1)); setPinError(''); }} activeOpacity={0.6}>
                      <Ionicons name="backspace-outline" size={24} color={iconColor} />
                    </TouchableOpacity>
                  );
                  return (
                    <TouchableOpacity key={ki} style={S.numpadKey} onPress={() => { setPinError(''); setPin(p => p.length < 4 ? p + key : p); }} activeOpacity={0.6}>
                      <Text style={[S.numpadDigit, { color: primaryText }]}>{key}</Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            ))}
          </View>
        )}
      </View>
    );
  }

  // ── Render: Manage ────────────────────────────────────────────────────────
  function renderManage() {
    return (
      <ScrollView contentContainerStyle={[S.manageContent, { paddingTop: insets.top + 28, paddingBottom: insets.bottom + 32 }]} showsVerticalScrollIndicator={false}>
        <TouchableOpacity style={S.backBtn} onPress={() => goToStep('grid')} activeOpacity={0.7}>
          <Ionicons name="arrow-back" size={22} color={iconColor} />
        </TouchableOpacity>
        <Text style={[S.heading, { marginBottom: 28, color: primaryText }]}>Manage Profiles</Text>
        {profiles.map(profile => (
          <View key={profile.id} style={[S.manageRow, { borderBottomColor: cardBorder }]}>
            <Image source={avatarFor(profile).image} style={S.avatarSm} />
            <View style={S.manageInfo}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                <Text style={[S.manageProfileName, { color: primaryText }]}>{profile.name}</Text>
                {profile.isDefault && <Ionicons name="star" size={14} color={c.accentSoft} />}
              </View>
              <Text style={[S.manageProfileSub, { color: tertiaryText }]}>
                {[profile.isDefault ? 'Default' : null, profile.hasPinSet ? 'PIN locked' : 'No PIN'].filter(Boolean).join(' · ')}
              </Text>
            </View>
            {!profile.isDefault && (
              <TouchableOpacity
                style={S.manageEditBtn}
                onPress={async () => {
                  const result = await setDefaultProfile(profile.id);
                  if (result.error) {
                    Alert.alert('Unable to update profile', result.error);
                  }
                }}
                activeOpacity={0.7}
              >
                <Ionicons name="star-outline" size={18} color={c.accentSoft} />
              </TouchableOpacity>
            )}
            <TouchableOpacity style={S.manageEditBtn} onPress={() => openEdit(profile, 'manage')} activeOpacity={0.7}>
              <Ionicons name="pencil-outline" size={18} color={tertiaryText} />
            </TouchableOpacity>
            {profiles.length > 1 && (
              <TouchableOpacity style={S.manageDeleteBtn} onPress={() => requestDelete(profile)} activeOpacity={0.7}>
                <Ionicons name="trash-outline" size={16} color={destructiveText} />
              </TouchableOpacity>
            )}
          </View>
        ))}
        {profiles.length < MAX_PROFILES_PER_ACCOUNT && (
          <TouchableOpacity style={[S.addProfileBtn, { backgroundColor: cardBg, borderColor: cardBorder }]} onPress={() => openEdit(null, 'manage')} activeOpacity={0.75}>
            <Ionicons name="add-circle-outline" size={20} color={primaryText} />
            <Text style={[S.addProfileBtnText, { color: primaryText }]}>Add New Profile</Text>
          </TouchableOpacity>
        )}
      </ScrollView>
    );
  }

  // ── Render: Edit / Create ─────────────────────────────────────────────────
  function renderEdit() {
    const isNew = !editProfile;

    return (
      <View style={S.editScreen}>
        <ScrollView contentContainerStyle={[S.editContent, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 112 }]} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
          {/* Header */}
          <View style={S.editHeader}>
            <TouchableOpacity style={S.backBtn} onPress={() => goToStep(prevStep)} activeOpacity={0.7}>
              <Ionicons name="arrow-back" size={22} color={iconColor} />
            </TouchableOpacity>
            <Text style={[S.editTitle, { color: primaryText }]}>{isNew ? 'New Profile' : 'Edit Profile'}</Text>
            <View style={S.editHeaderSpacer} />
          </View>

          {/* Avatar picker */}
          <Text style={[S.editSectionLabel, { color: tertiaryText }]}>AVATAR</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={S.avatarScroll} style={S.avatarScrollContainer} bounces={false}>
            {PROFILE_AVATARS.map(av => {
              const sel = av.id === editAvatar;
              return (
                <TouchableOpacity key={av.id} onPress={() => setEditAvatar(av.id)} activeOpacity={0.75} style={[S.avatarPickerOption, sel && S.avatarPickerOptionSelected]}>
                  <Image source={av.image} style={S.avatarPickerImg} />
                  {sel && <View style={[S.avatarCheck, { backgroundColor: c.accent }]}><Ionicons name="checkmark" size={10} color="#fff" /></View>}
                </TouchableOpacity>
              );
            })}
          </ScrollView>

          {/* Name */}
          <Text style={[S.editSectionLabel, { color: tertiaryText }]}>PROFILE NAME</Text>
          <TextInput ref={editNameInputRef} style={[S.editNameInput, { backgroundColor: cardBg, color: primaryText }]} placeholder="e.g. Alex, Kids, Work…" placeholderTextColor={tertiaryText} value={editName} onChangeText={t => { setEditName(t); setEditNameErr(''); }} maxLength={30} returnKeyType="done" autoCorrect={false} onSubmitEditing={dismissEditNameKeyboard} />
          {editNameErr ? <Text style={S.editError}>{editNameErr}</Text> : null}

          {/* Language */}
          <Text style={[S.editSectionLabel, { color: tertiaryText }]}>LANGUAGE PREFERENCES</Text>
          <View style={[S.editCard, { backgroundColor: cardBg, borderColor: cardBorder }]}>
            <TouchableOpacity style={S.editOptionRow} onPress={() => { dismissEditNameKeyboard(); setSubLangSheet(true); }} activeOpacity={0.7}>
              <Text style={[S.editOptionLabel, { color: primaryText }]}>Subtitle language</Text>
              <View style={S.editOptionValueRow}>
                <Text style={[S.editOptionValue, { color: tertiaryText }]}>{subLangLabel}</Text>
                <Ionicons name="chevron-forward" size={16} color={tertiaryText} />
              </View>
            </TouchableOpacity>
            <View style={[S.editDivider, { backgroundColor: cardBorder }]} />
            <TouchableOpacity style={S.editOptionRow} onPress={() => { dismissEditNameKeyboard(); setAudioLangSheet(true); }} activeOpacity={0.7}>
              <Text style={[S.editOptionLabel, { color: primaryText }]}>Audio language</Text>
              <View style={S.editOptionValueRow}>
                <Text style={[S.editOptionValue, { color: tertiaryText }]}>{audioLangLabel}</Text>
                <Ionicons name="chevron-forward" size={16} color={tertiaryText} />
              </View>
            </TouchableOpacity>
          </View>

          {/* PIN */}
          <Text style={[S.editSectionLabel, { color: tertiaryText }]}>PROFILE PIN</Text>
          <View style={[S.editCard, { backgroundColor: cardBg, borderColor: cardBorder }]}>
            <View style={S.editOptionRow}>
              <View style={{ flex: 1 }}>
                <Text style={[S.editOptionLabel, { color: primaryText }]}>{editProfile?.hasPinSet && !editRemovingPin ? 'PIN is enabled' : 'Enable PIN lock'}</Text>
                <Text style={[S.editOptionSub, { color: tertiaryText }]}>Require a 4-digit PIN to access this profile.</Text>
              </View>
              {editProfile?.hasPinSet && !editRemovingPin ? (
                <TouchableOpacity style={S.pinActionBtnDestructive} onPress={() => { dismissEditNameKeyboard(); setRemovePinConfirm(true); }} activeOpacity={0.75}>
                  <Text style={{ color: destructiveText, fontSize: 13, fontWeight: '600' }}>Remove</Text>
                </TouchableOpacity>
              ) : (
                <TouchableOpacity style={S.pinActionBtn} onPress={() => { dismissEditNameKeyboard(); setEditShowPin(s => !s); setEditPinVal(''); setEditPinConfirm(''); }} activeOpacity={0.75}>
                  <Text style={{ color: primaryText, fontSize: 13, fontWeight: '600' }}>
                    {editShowPin ? 'Cancel' : editProfile?.hasPinSet ? 'Change' : 'Set PIN'}
                  </Text>
                </TouchableOpacity>
              )}
            </View>
            {editShowPin && !editRemovingPin && (
              <>
                <View style={[S.editDivider, { backgroundColor: cardBorder }]} />
                <ProfilePinComposer
                  pinLabel={editProfile?.hasPinSet ? 'New PIN' : 'PIN'}
                  pinValue={editPinVal}
                  confirmPin={editPinConfirm}
                  onChangePin={setEditPinVal}
                  onChangeConfirmPin={setEditPinConfirm}
                />
              </>
            )}
          </View>
        </ScrollView>
        <View style={[S.editBottomBar, { paddingBottom: insets.bottom + 12, backgroundColor: pageBg, borderTopColor: cardBorder }]}>
          <TouchableOpacity
            style={[S.editBottomSaveBtn, { backgroundColor: saveButtonBg, borderWidth: isLightMonochrome ? 1 : 0, borderColor: isLightMonochrome ? 'rgba(17,24,39,0.18)' : 'transparent' }, editSaving && S.editBottomSaveBtnDisabled]}
            onPress={handleSaveEdit}
            disabled={editSaving}
            activeOpacity={0.8}
          >
            {editSaving ? <ActivityIndicator size="small" color={saveButtonText} /> : <Text style={[S.editBottomSaveBtnText, { color: saveButtonText }]}>Save</Text>}
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  if ((!profilesReady && loadingProfiles) || profileSwitching) {
    return (
      <View style={[S.root, { backgroundColor: pageBg }]}>
        <RadialLoaderScreen />
      </View>
    );
  }

  return (
    <View style={[S.root, { backgroundColor: pageBg }]}>
      <Animated.View style={[S.content, { opacity: fadeAnim, transform: [{ translateY: slideAnim }] }]}>
        {step === 'grid'   && renderGrid()}
        {step === 'pin'    && renderPin()}
        {step === 'manage' && renderManage()}
        {step === 'edit'   && renderEdit()}
      </Animated.View>

      {/* Modals rendered outside animated view so they're not clipped */}
      <ConfirmSheet
        visible={deleteConfirmVisible}
        onClose={() => setDeleteConfirmVisible(false)}
        title={`Delete "${deleteTargetName}"?`}
        message="This profile and all its settings will be permanently removed."
        icon="trash-outline"
        confirmLabel="Delete"
        variant="destructive"
        onConfirm={doDelete}
      />
      <ConfirmSheet
        visible={removePinConfirm}
        onClose={() => setRemovePinConfirm(false)}
        title="Remove PIN"
        message="This profile will no longer require a PIN to access."
        icon="lock-open-outline"
        confirmLabel="Remove PIN"
        variant="destructive"
        onConfirm={() => { setEditRemovingPin(true); setEditShowPin(false); setEditPinVal(''); setEditPinConfirm(''); }}
      />
      <ActionSheet visible={subLangSheet}   onClose={() => setSubLangSheet(false)}   title="Subtitle Language" actions={subLangActions} />
      <ActionSheet visible={audioLangSheet} onClose={() => setAudioLangSheet(false)} title="Audio Language"    actions={audioLangActions} />
    </View>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────
const S = StyleSheet.create({
  root: { flex: 1 },
  content: { flex: 1, width: '100%' },
  launchRoot: {
    flex: 1,
    backgroundColor: '#000000',
  },
  launchHeroArea: {
    height: HERO_HEIGHT,
    position: 'relative',
    overflow: 'hidden',
    backgroundColor: '#000000',
  },
  launchHeroImage: {
    ...StyleSheet.absoluteFillObject,
    width: '100%',
    height: '100%',
    resizeMode: 'cover',
  },
  launchHeroFocusBlur: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: HERO_BLUR_SECTION_HEIGHT,
    overflow: 'hidden',
  },
  launchHeroBlurImage: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: -HERO_BLUR_SECTION_OFFSET,
    width: '100%',
    height: HERO_HEIGHT,
    resizeMode: 'cover',
  },
  launchHeroBlurFeather: {
    ...StyleSheet.absoluteFillObject,
  },
  launchHeroScrim: {
    ...StyleSheet.absoluteFillObject,
  },
  launchBottomBlend: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: -1,
    height: 260,
  },
  launchHeroCopy: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'flex-end',
    paddingHorizontal: 24,
    paddingBottom: 136,
  },
  launchHeroEyebrow: {
    color: 'rgba(255,255,255,0.74)',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    marginBottom: 10,
  },
  launchHeroTitle: {
    color: '#ffffff',
    fontSize: 38,
    lineHeight: 42,
    fontWeight: '900',
    letterSpacing: 0.2,
    marginBottom: 10,
    textShadowColor: 'rgba(0,0,0,0.72)',
    textShadowRadius: 12,
    textShadowOffset: { width: 0, height: 4 },
  },
  launchHeroDescription: {
    color: 'rgba(255,255,255,0.82)',
    fontSize: 14,
    lineHeight: 21,
    maxWidth: '88%',
    textShadowColor: 'rgba(0,0,0,0.58)',
    textShadowRadius: 8,
    textShadowOffset: { width: 0, height: 2 },
  },
  launchChooser: {
    flex: 1,
    marginTop: -86,
    backgroundColor: '#000000',
    borderTopLeftRadius: 34,
    borderTopRightRadius: 34,
    paddingHorizontal: 22,
    paddingTop: 18,
    paddingBottom: 28,
    alignItems: 'center',
  },
  launchHeading: {
    color: '#ffffff',
    fontSize: 28,
    lineHeight: 32,
    fontWeight: '800',
    letterSpacing: 0.2,
    marginBottom: 22,
  },
  launchHeadingCompact: {
    marginBottom: 14,
  },
  launchProfileGrid: {
    width: '100%',
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 18,
  },
  launchProfileCard: {
    width: '29%',
    minWidth: 90,
    alignItems: 'center',
    marginBottom: 14,
  },
  launchProfileCardCompact: {
    width: '30%',
    minWidth: 84,
    marginBottom: 11,
  },
  launchAvatarWrap: {
    position: 'relative',
    marginBottom: 10,
  },
  launchAvatarWrapCompact: {
    marginBottom: 8,
  },
  launchAvatar: {
    width: 88,
    height: 88,
    borderRadius: 44,
  },
  launchAvatarCompact: {
    width: 78,
    height: 78,
    borderRadius: 39,
  },
  launchAddCircle: {
    width: 88,
    height: 88,
    borderRadius: 44,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: 'rgba(255,255,255,0.24)',
    marginBottom: 10,
  },
  launchAddCircleCompact: {
    width: 78,
    height: 78,
    borderRadius: 39,
    marginBottom: 8,
  },
  launchProfileName: {
    color: '#f3f4f6',
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
    textAlign: 'center',
    width: '100%',
  },
  launchProfileNameCompact: {
    fontSize: 12.5,
    lineHeight: 16,
  },
  launchLockBadge: {
    position: 'absolute',
    right: 1,
    bottom: 1,
    width: 24,
    height: 24,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.82)',
    borderWidth: 1.5,
    borderColor: '#000000',
  },
  launchLockBadgeCompact: {
    width: 21,
    height: 21,
    borderRadius: 10.5,
  },
  launchActiveBadge: {
    position: 'absolute',
    top: 1,
    right: 1,
    width: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#22c55e',
    borderWidth: 1.5,
    borderColor: '#000000',
  },
  launchActiveBadgeCompact: {
    width: 20,
    height: 20,
    borderRadius: 10,
  },
  launchManageBtn: {
    marginTop: 10,
    minHeight: 44,
    borderRadius: 22,
    paddingHorizontal: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
  },
  launchManageText: {
    color: '#f3f4f6',
    fontSize: 14,
    fontWeight: '700',
  },

  // Grid — 2-column layout
  gridContent: { alignItems: 'center', paddingHorizontal: 28 },
  heading: { color: '#fff', fontSize: 26, fontWeight: '700', letterSpacing: 0.3, textAlign: 'center', marginBottom: 40 },
  profileGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 24, width: '100%', marginBottom: 40 },
  profileCard: { alignItems: 'center', width: '45%' as any },
  avatarWrapper: { position: 'relative', marginBottom: 12 },
  avatarLg: { width: 120, height: 120, borderRadius: 60 },
  avatarMd: { width: 72, height: 72, borderRadius: 36 },
  avatarSm: { width: 46, height: 46, borderRadius: 23 },
  addCircle: { width: 120, height: 120, borderRadius: 60, backgroundColor: 'rgba(255,255,255,0.08)', borderWidth: 2, borderColor: 'rgba(255,255,255,0.2)', borderStyle: 'dashed', alignItems: 'center', justifyContent: 'center' },
  lockBadge: { position: 'absolute', bottom: 4, right: 4, width: 26, height: 26, borderRadius: 13, backgroundColor: 'rgba(0,0,0,0.85)', alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: '#000' },
  activeBadge: { position: 'absolute', top: 4, right: 4, width: 24, height: 24, borderRadius: 12, backgroundColor: '#22c55e', alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: '#000' },
  profileName: { color: '#e5e7eb', fontSize: 15, fontWeight: '600', textAlign: 'center', width: '100%' },
  manageBtn: { borderWidth: 1, borderColor: 'rgba(255,255,255,0.3)', borderRadius: 8, paddingVertical: 10, paddingHorizontal: 28, marginBottom: 16 },
  manageBtnText: { color: '#e5e7eb', fontSize: 14, fontWeight: '600' },
  cancelBtn: { paddingVertical: 10, paddingHorizontal: 28 },
  cancelBtnText: { color: 'rgba(255,255,255,0.5)', fontSize: 14, fontWeight: '500' },
  backBtn: { alignSelf: 'flex-start', padding: 8, marginBottom: 24 },

  // PIN
  pinContainer: { flex: 1, alignItems: 'center', paddingHorizontal: 24 },
  pinProfileName: { color: '#fff', fontSize: 20, fontWeight: '700', marginTop: 14, marginBottom: 4 },
  pinPrompt: { color: 'rgba(255,255,255,0.7)', fontSize: 14, marginBottom: 28 },
  pinDots: { flexDirection: 'row', gap: 18, marginBottom: 8 },
  pinDot: { width: 16, height: 16, borderRadius: 8 },
  pinDotEmpty: { backgroundColor: 'rgba(255,255,255,0.25)' },
  pinDotFilled: { backgroundColor: '#fff' },
  pinError: { color: '#f87171', fontSize: 13, marginTop: 8, height: 20 },
  numpad: { marginTop: 16, gap: 8 },
  numpadRow: { flexDirection: 'row', gap: 8 },
  numpadKey: { width: 76, height: 56, borderRadius: 12, backgroundColor: 'rgba(255,255,255,0.1)', alignItems: 'center', justifyContent: 'center' },
  numpadDigit: { color: '#fff', fontSize: 22, fontWeight: '500' },

  // Manage
  manageContent: { paddingHorizontal: 20 },
  manageRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: 'rgba(255,255,255,0.1)', gap: 14 },
  manageInfo: { flex: 1 },
  manageProfileName: { color: '#fff', fontSize: 15, fontWeight: '600' },
  manageProfileSub: { color: 'rgba(255,255,255,0.5)', fontSize: 12, marginTop: 2 },
  manageEditBtn: { padding: 8 },
  manageDeleteBtn: { padding: 8 },
  addProfileBtn: { flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 28, paddingVertical: 14, paddingHorizontal: 18, borderRadius: 12, backgroundColor: 'rgba(255,255,255,0.08)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.15)', alignSelf: 'center' },
  addProfileBtnText: { color: '#fff', fontSize: 15, fontWeight: '600' },

  // Edit
  editScreen: { flex: 1 },
  editContent: { paddingHorizontal: 20 },
  editHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 28, gap: 12 },
  editHeaderSpacer: { width: 64 },
  editTitle: { flex: 1, color: '#fff', fontSize: 20, fontWeight: '700' },
  editBottomBar: {
    paddingHorizontal: 20,
    paddingTop: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  editBottomSaveBtn: {
    minHeight: 54,
    borderRadius: 27,
    alignItems: 'center',
    justifyContent: 'center',
  },
  editBottomSaveBtnDisabled: { opacity: 0.72 },
  editBottomSaveBtnText: { color: '#111111', fontSize: 16, fontWeight: '700' },
  editSectionLabel: { color: 'rgba(255,255,255,0.45)', fontSize: 11, fontWeight: '700', letterSpacing: 0.9, textTransform: 'uppercase', marginBottom: 10 },
  avatarScrollContainer: { marginBottom: 24 },
  avatarScroll: { gap: 12, paddingVertical: 4, paddingHorizontal: 2 },
  avatarPickerOption: { borderRadius: 54, padding: 4, position: 'relative' },
  avatarPickerOptionSelected: { borderWidth: 2.5, borderColor: '#fff' },
  avatarPickerImg: { width: 81, height: 81, borderRadius: 40.5 },
  avatarCheck: { position: 'absolute', bottom: 2, right: 2, width: 22, height: 22, borderRadius: 11, backgroundColor: '#22c55e', alignItems: 'center', justifyContent: 'center' },
  editNameInput: { backgroundColor: 'rgba(255,255,255,0.1)', borderRadius: 12, paddingHorizontal: 16, paddingVertical: 14, fontSize: 16, color: '#fff', marginBottom: 8 },
  editError: { color: '#f87171', fontSize: 13, marginBottom: 8 },
  editCard: { backgroundColor: 'rgba(255,255,255,0.07)', borderRadius: 14, borderWidth: 1, borderColor: 'rgba(255,255,255,0.12)', overflow: 'hidden', marginBottom: 20 },
  ratingRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, padding: 14 },
  ratingPill: { paddingVertical: 7, paddingHorizontal: 14, borderRadius: 20 },
  ratingPillSelected: { backgroundColor: '#ffffff' },
  ratingPillUnselected: { backgroundColor: 'transparent', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.35)' },
  ratingLabel: { fontSize: 13, fontWeight: '600' },
  editOptionRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 14, gap: 12 },
  editOptionLabel: { color: '#fff', fontSize: 15, fontWeight: '500', flex: 0 },
  editOptionSub: { color: 'rgba(255,255,255,0.45)', fontSize: 12, marginTop: 2 },
  editOptionValueRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  editOptionValue: { color: 'rgba(255,255,255,0.55)', fontSize: 14 },
  editDivider: { height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(255,255,255,0.12)', marginHorizontal: 16 },
  pinActionBtn: { paddingVertical: 7, paddingHorizontal: 14, borderRadius: 20, backgroundColor: 'rgba(255,255,255,0.15)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.25)' },
  pinActionBtnDestructive: { paddingVertical: 7, paddingHorizontal: 14, borderRadius: 20, backgroundColor: 'rgba(239,68,68,0.2)', borderWidth: 1, borderColor: 'rgba(239,68,68,0.4)' },
});
