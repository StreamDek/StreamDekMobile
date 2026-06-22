import React, { useMemo } from 'react';
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { BlurTargetView } from 'expo-blur';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { StatusBar } from 'expo-status-bar';
import { StackBottomNav } from '../components/StackBottomNav';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { EntranceFade } from '../components/EntranceFade';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useLanguage } from '../context/LanguageContext';
import { useUIStyle } from '../context/UIStyleContext';

function makeStyles(c: ThemeColors, isLightAppearance: boolean) {
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: c.bg },
    content: { flex: 1, paddingHorizontal: 20 },
    title: { color: c.textPrimary, fontSize: 30, fontWeight: '900', letterSpacing: -0.6 },
    subtitle: { color: c.textSecondary, fontSize: 14, lineHeight: 20, marginTop: 8, marginBottom: 18 },
    sectionLabel: {
      color: c.mutedText,
      fontSize: 13,
      fontWeight: '800',
      letterSpacing: 1,
      textTransform: 'uppercase',
      marginBottom: 10,
    },
    sectionCard: {
      borderRadius: 24,
      borderWidth: 1,
      borderColor: c.border,
      backgroundColor: c.cardBgElevated ?? c.cardBg,
      overflow: 'hidden',
      marginBottom: 22,
    },
    optionGrid: {
      flexDirection: 'row',
      flexWrap: 'wrap',
      gap: 12,
      padding: 16,
    },
    optionCard: {
      width: '48%',
      minHeight: 196,
      borderRadius: 18,
      borderWidth: 1,
      borderColor: c.border,
      backgroundColor: c.inputBg,
      paddingHorizontal: 14,
      paddingVertical: 14,
      justifyContent: 'space-between',
      overflow: 'visible',
      position: 'relative',
    },
    optionCardActive: {
      borderColor: isLightAppearance ? (c.accent === '#ffffff' ? c.textPrimary : c.accent) : c.accent,
      backgroundColor: isLightAppearance
        ? (c.accent === '#ffffff' ? 'rgba(16,24,40,0.10)' : `${c.accent}16`)
        : `${c.accent}12`,
    },
    previewFrame: {
      minHeight: 112,
      alignItems: 'center',
      justifyContent: 'center',
      marginBottom: 14,
      marginTop: 12,
    },
    optionTitle: { color: c.textPrimary, fontSize: 15, fontWeight: '800', marginBottom: 6 },
    optionSub: { color: c.textSecondary, fontSize: 12, lineHeight: 17 },
    checkWrap: {
      position: 'absolute',
      top: 10,
      right: 10,
      width: 26,
      height: 26,
      borderRadius: 13,
      backgroundColor: isLightAppearance ? (c.accent === '#ffffff' ? c.textPrimary : c.accent) : '#ffffff',
      borderWidth: 1,
      borderColor: isLightAppearance ? 'rgba(255,255,255,0.65)' : 'rgba(17,17,17,0.08)',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 5,
      elevation: 5,
    },
  });
}

function PageStylePreview({
  value,
  active,
  colors,
  resolvedAppearance,
}: {
  value: 'classic' | 'centered' | 'glass';
  active: boolean;
  colors: ThemeColors;
  resolvedAppearance: 'dark' | 'light';
}) {
  const stroke = active ? (colors.accent === '#ffffff' ? colors.textPrimary : colors.accent) : colors.border;
  const surface = resolvedAppearance === 'light' ? '#ffffff' : 'rgba(255,255,255,0.08)';
  return (
    <View style={{ width: 88, height: 66, borderRadius: 16, borderWidth: 1.5, borderColor: stroke, backgroundColor: surface, padding: 10, justifyContent: 'space-between' }}>
      {value === 'glass' ? (
        <>
          <View style={{ height: 18, borderRadius: 10, backgroundColor: resolvedAppearance === 'light' ? 'rgba(17,24,39,0.14)' : 'rgba(255,255,255,0.12)' }} />
          <View style={{ flexDirection: 'row', gap: 5 }}>
            <View style={{ flex: 1, height: 8, borderRadius: 5, backgroundColor: stroke, opacity: 0.55 }} />
            <View style={{ flex: 1, height: 8, borderRadius: 5, backgroundColor: stroke, opacity: 0.28 }} />
          </View>
        </>
      ) : value === 'centered' ? (
        <>
          <View style={{ alignItems: 'center' }}>
            <View style={{ width: 42, height: 12, borderRadius: 8, backgroundColor: stroke, opacity: 0.55 }} />
          </View>
          <View style={{ height: 8, borderRadius: 5, backgroundColor: stroke, opacity: 0.24 }} />
        </>
      ) : (
        <>
          <View style={{ width: 30, height: 14, borderRadius: 8, backgroundColor: stroke, opacity: 0.48 }} />
          <View style={{ height: 8, borderRadius: 5, backgroundColor: stroke, opacity: 0.24 }} />
        </>
      )}
    </View>
  );
}

export function PageStyleSettingsScreen({ navigation }: any) {
  const blurTargetRef = React.useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const isLightAppearance = resolvedAppearance === 'light';
  const styles = useMemo(() => makeStyles(colors, isLightAppearance), [colors, isLightAppearance]);
  const { t } = useLanguage();
  const { uiStyle, setUiStyle } = useUIStyle();

  const options: Array<{ value: 'classic' | 'centered' | 'glass'; label: string; description: string }> = [
    { value: 'classic', label: t('settings_classic'), description: t('settings_page_style_classic_sub') },
    { value: 'centered', label: t('settings_centered'), description: t('settings_page_style_centered_sub') },
    { value: 'glass', label: t('settings_glassy_hero'), description: t('settings_page_style_glass_sub') },
  ];

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
        <ScrollView
          style={styles.container}
          contentContainerStyle={{ paddingTop: insets.top + 18, paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 28 }}
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.content}>
            <EntranceFade index={0}>
              <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.78} style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 14 }}>
                <Ionicons name="chevron-back" size={20} color={colors.textSecondary} />
                <Text style={{ color: colors.textSecondary, fontSize: 14, fontWeight: '600' }}>{t('common_back')}</Text>
              </TouchableOpacity>
              <Text style={styles.title}>{t('settings_page_style')}</Text>
              <Text style={styles.subtitle}>{t('settings_page_style_sub')}</Text>
            </EntranceFade>

            <EntranceFade index={1}>
              <Text style={styles.sectionLabel}>Page Layout</Text>
              <View style={styles.sectionCard}>
                <View style={styles.optionGrid}>
                  {options.map((option) => {
                    const active = option.value === uiStyle;
                    return (
                      <TouchableOpacity
                        key={option.value}
                        activeOpacity={0.82}
                        style={[styles.optionCard, active && styles.optionCardActive]}
                        onPress={() => { void setUiStyle(option.value); }}
                      >
                        {active ? (
                          <View style={styles.checkWrap}>
                            <Ionicons name="checkmark" size={16} color={isLightAppearance ? '#ffffff' : '#111111'} />
                          </View>
                        ) : null}
                        <View style={styles.previewFrame}>
                          <PageStylePreview value={option.value} active={active} colors={colors} resolvedAppearance={resolvedAppearance} />
                        </View>
                        <View>
                          <Text style={styles.optionTitle}>{option.label}</Text>
                          <Text style={styles.optionSub}>{option.description}</Text>
                        </View>
                      </TouchableOpacity>
                    );
                  })}
                </View>
              </View>
            </EntranceFade>
          </View>
        </ScrollView>
      </BlurTargetView>
      <StatusBar style={resolvedAppearance === 'light' ? 'dark' : 'light'} />
      <StackBottomNav activeTab="Settings" blurTarget={blurTargetRef} />
    </View>
  );
}
