import React, { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useTheme } from '../context/ThemeContext';
import { useProfile } from '../context/ProfileContext';
import { useLanguage } from '../context/LanguageContext';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { MAX_PROFILES_PER_ACCOUNT, PROFILE_AVATARS, type StreamProfile } from '../utils/profileApi';

export function ManageProfilesScreen() {
  const navigation = useNavigation<any>();
  const insets = useSafeAreaInsets();
  const { theme } = useTheme();
  const { t } = useLanguage();
  const c = theme.colors;
  const isLightMonochrome = theme.resolvedAppearance === 'light' && theme.id === 'monochrome';

  const { profiles, loadingProfiles, activeProfile, deleteProfile, setDefaultProfile } = useProfile();
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [deleteConfirmVisible, setDeleteConfirmVisible] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);
  const [deleteTargetName, setDeleteTargetName] = useState('');

  const avatarFor = (p: StreamProfile) =>
    PROFILE_AVATARS[Math.min(p.avatarIndex, PROFILE_AVATARS.length - 1)];

  const requestDelete = (profile: StreamProfile) => {
    setDeleteTargetId(profile.id);
    setDeleteTargetName(profile.name);
    setDeleteConfirmVisible(true);
  };

  const confirmDelete = async () => {
    const id = deleteTargetId;
    if (!id) return;
    const profile = profiles.find(item => item.id === id);
    const fallbackProfile = profiles.find(item => item.id !== id) ?? null;
    setDeleteConfirmVisible(false);
    setDeletingId(id);
    if (profile?.isDefault && fallbackProfile) {
      const promoteResult = await setDefaultProfile(fallbackProfile.id);
      if (promoteResult.error) {
        setDeletingId(null);
        Alert.alert(t('profile_delete_failed'), promoteResult.error);
        return;
      }
    }
    const result = await deleteProfile(id);
    setDeletingId(null);
    if (result.error) {
      Alert.alert(t('profile_delete_failed'), result.error);
      return;
    }
    setDeleteTargetId(null);
  };

  return (
    <View style={[styles.root, { backgroundColor: c.bg }]}>
      <View style={[styles.header, { paddingTop: insets.top + 16, borderBottomColor: c.border }]}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn} activeOpacity={0.7}>
          <Ionicons name="arrow-back" size={22} color={c.textPrimary} />
        </TouchableOpacity>
        <View style={styles.headerTitleWrap}>
          <Text style={[styles.headerEyebrow, { color: c.mutedText }]}>{t('settings_profiles_section')}</Text>
          <Text style={[styles.headerTitle, { color: c.textPrimary }]}>{t('profile_manage_title')}</Text>
        </View>
        {profiles.length < MAX_PROFILES_PER_ACCOUNT && (
          <TouchableOpacity
            onPress={() => navigation.navigate('EditProfile', { profileId: null })}
            style={styles.addBtn}
            activeOpacity={0.7}
          >
            <Ionicons name="add" size={24} color={c.accent} />
          </TouchableOpacity>
        )}
      </View>

      {loadingProfiles ? (
        <ActivityIndicator color={c.accent} style={{ marginTop: 60 }} />
      ) : (
        <ScrollView
          contentContainerStyle={[styles.list, { paddingBottom: insets.bottom + 32 }]}
          showsVerticalScrollIndicator={false}
        >
          {profiles.map(profile => {
            const av = avatarFor(profile);
            const isActive = activeProfile?.id === profile.id;
            const isDeleting = deletingId === profile.id;

            return (
              <View key={profile.id} style={[styles.row, { backgroundColor: c.cardBg, borderColor: c.border }]}>
                <Image source={av.image} style={styles.avatar} />

                <View style={styles.info}>
                  <View style={styles.nameRow}>
                    <Text style={[styles.name, { color: c.textPrimary }]}>{profile.name}</Text>
                    {profile.isDefault && (
                      <Ionicons name="star" size={14} color={c.accentSoft} />
                    )}
                    {isActive && (
                      <View style={[styles.badge, { backgroundColor: 'rgba(99,102,241,0.15)' }]}>
                        <Text style={[styles.badgeText, { color: c.accentSoft }]}>{t('profile_active_badge')}</Text>
                      </View>
                    )}
                    {profile.isDefault && (
                      <View style={[styles.badge, { backgroundColor: c.border }]}>
                        <Text style={[styles.badgeText, { color: c.mutedText }]}>{t('profile_default_badge')}</Text>
                      </View>
                    )}
                  </View>
                  <Text style={[styles.sub, { color: c.mutedText }]}>
                    {[
                      profile.hasPinSet ? t('profile_pin_locked') : t('profile_no_pin'),
                      profile.subtitleLanguage ? t('profile_subs_language', { language: profile.subtitleLanguage }) : null,
                    ].filter(Boolean).join(' · ')}
                  </Text>
                </View>

                {isDeleting ? (
                  <ActivityIndicator size="small" color={c.mutedText} style={{ marginRight: 8 }} />
                ) : (
                  <>
                    {!profile.isDefault && (
                      <TouchableOpacity
                        style={styles.actionBtn}
                        onPress={async () => {
                          const result = await setDefaultProfile(profile.id);
                          if (result.error) {
                            Alert.alert(t('profile_update_failed'), result.error);
                          }
                        }}
                        activeOpacity={0.7}
                      >
                        <Ionicons name="star-outline" size={18} color={c.accentSoft} />
                      </TouchableOpacity>
                    )}
                    <TouchableOpacity
                      style={styles.actionBtn}
                      onPress={() => navigation.navigate('EditProfile', { profileId: profile.id })}
                      activeOpacity={0.7}
                    >
                      <Ionicons name="pencil-outline" size={18} color={c.mutedText} />
                    </TouchableOpacity>
                    {profiles.length > 1 && (
                      <TouchableOpacity
                        style={styles.actionBtn}
                        onPress={() => requestDelete(profile)}
                        activeOpacity={0.7}
                      >
                        <Ionicons name="trash-outline" size={18} color="#ef4444" />
                      </TouchableOpacity>
                    )}
                  </>
                )}
              </View>
            );
          })}

          {profiles.length === 0 && (
            <View style={styles.emptyState}>
              <Ionicons name="person-circle-outline" size={64} color={c.mutedText} />
              <Text style={[styles.emptyTitle, { color: c.textPrimary }]}>{t('profile_none_title')}</Text>
              <Text style={[styles.emptySub, { color: c.mutedText }]}>
                {t('profile_none_sub')}
              </Text>
              <TouchableOpacity
                style={[styles.emptyAddBtn, { backgroundColor: c.accent }]}
                onPress={() => navigation.navigate('EditProfile', { profileId: null })}
                activeOpacity={0.8}
              >
                <Text style={[styles.emptyAddBtnText, { color: c.buttonText }]}>{t('profile_create')}</Text>
              </TouchableOpacity>
            </View>
          )}

          {profiles.length > 0 && profiles.length < MAX_PROFILES_PER_ACCOUNT && (
            <TouchableOpacity
              style={[styles.addRowBtn, { borderColor: c.border }]}
              onPress={() => navigation.navigate('EditProfile', { profileId: null })}
              activeOpacity={0.75}
            >
              <Ionicons name="add-circle-outline" size={20} color={isLightMonochrome ? '#111111' : c.accent} />
              <Text style={[styles.addRowBtnText, { color: isLightMonochrome ? '#111111' : c.accent }]}>{t('profile_add_new')}</Text>
            </TouchableOpacity>
          )}

          <Text style={[styles.hint, { color: c.mutedText }]}>
            {t('profile_limit_hint', { n: MAX_PROFILES_PER_ACCOUNT })}
          </Text>
        </ScrollView>
      )}

      <ConfirmSheet
        visible={deleteConfirmVisible}
        onClose={() => setDeleteConfirmVisible(false)}
        title={t('profile_delete_title', { name: deleteTargetName })}
        message={t('profile_delete_sub')}
        icon="trash-outline"
        confirmLabel={t('common_delete')}
        variant="destructive"
        onConfirm={confirmDelete}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingHorizontal: 16,
    paddingBottom: 18,
    borderBottomWidth: StyleSheet.hairlineWidth,
    gap: 12,
  },
  backBtn: { padding: 4, marginTop: 12 },
  headerTitleWrap: { flex: 1, gap: 4 },
  headerEyebrow: { fontSize: 12, fontWeight: '700', letterSpacing: 0.8, textTransform: 'uppercase' },
  headerTitle: { fontSize: 28, fontWeight: '800', letterSpacing: -0.3 },
  addBtn: { padding: 4, marginTop: 10 },
  list: { padding: 16, gap: 10 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 14,
    borderWidth: StyleSheet.hairlineWidth,
    padding: 14,
    gap: 12,
  },
  avatar: { width: 48, height: 48, borderRadius: 24 },
  info: { flex: 1 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 6, flexWrap: 'wrap' },
  name: { fontSize: 15, fontWeight: '600' },
  badge: { paddingHorizontal: 7, paddingVertical: 2, borderRadius: 20 },
  badgeText: { fontSize: 11, fontWeight: '700' },
  sub: { fontSize: 12, marginTop: 3 },
  actionBtn: { padding: 8 },
  emptyState: { alignItems: 'center', paddingVertical: 60, gap: 10 },
  emptyTitle: { fontSize: 18, fontWeight: '700', marginTop: 8 },
  emptySub: { fontSize: 14, textAlign: 'center', maxWidth: 260 },
  emptyAddBtn: { marginTop: 16, paddingVertical: 12, paddingHorizontal: 32, borderRadius: 12 },
  emptyAddBtnText: { fontSize: 15, fontWeight: '700' },
  addRowBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderStyle: 'dashed',
    marginTop: 4,
  },
  addRowBtnText: { fontSize: 15, fontWeight: '600' },
  hint: { fontSize: 12, textAlign: 'center', marginTop: 20, lineHeight: 18 },
});
