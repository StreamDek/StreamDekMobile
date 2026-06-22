import React, { useMemo } from 'react';
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { BlurTargetView } from 'expo-blur';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { StatusBar } from 'expo-status-bar';
import { StackBottomNav } from '../components/StackBottomNav';
import { BOTTOM_NAV_HEIGHT } from '../components/BottomNavBar';
import { EntranceFade } from '../components/EntranceFade';
import {
  CinematicSkeleton,
  GlassSkeleton,
  TicketSkeleton,
  MiniSkeleton,
  StackedSkeleton,
} from '../components/ContinueWatchingCard';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useLanguage } from '../context/LanguageContext';
import { useDisplaySettings, type ContinueWatchingStyle } from '../context/DisplaySettingsContext';

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
      minHeight: 170,
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
      minHeight: 84,
      alignItems: 'center',
      justifyContent: 'center',
      marginBottom: 14,
      marginTop: 10,
      paddingRight: 10,
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

function renderPreview(style: ContinueWatchingStyle, colors: ThemeColors, active: boolean) {
  const props = { selected: active, colors };
  switch (style) {
    case 'cinematic':
      return <CinematicSkeleton {...props} />;
    case 'glass':
      return <GlassSkeleton {...props} />;
    case 'ticket':
      return <TicketSkeleton {...props} />;
    case 'mini':
      return <MiniSkeleton {...props} />;
    case 'stacked':
      return <StackedSkeleton {...props} />;
    default:
      return null;
  }
}

export function ContinueWatchingStyleSettingsScreen({ navigation }: any) {
  const blurTargetRef = React.useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const isLightAppearance = resolvedAppearance === 'light';
  const styles = useMemo(() => makeStyles(colors, isLightAppearance), [colors, isLightAppearance]);
  const { t } = useLanguage();
  const { continueWatchingStyle, setContinueWatchingStyle } = useDisplaySettings();

  const options: Array<{ value: ContinueWatchingStyle; label: string; description: string }> = [
    { value: 'ticket', label: 'Card', description: 'TV-style landscape card' },
    { value: 'mini', label: 'Wide', description: 'Info-dense horizontal card' },
    { value: 'stacked', label: 'Poster', description: 'Artwork-first poster card' },
    { value: 'glass', label: t('settings_continue_style_glass'), description: 'Backdrop card with a glass metadata panel' },
    { value: 'cinematic', label: t('settings_continue_style_cinematic'), description: 'Large cinematic hero card' },
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
              <Text style={styles.title}>{t('settings_continue_watching_style')}</Text>
              <Text style={styles.subtitle}>{t('settings_continue_watching_style_sub')}</Text>
            </EntranceFade>

            <EntranceFade index={1}>
              <Text style={styles.sectionLabel}>Poster Card Style</Text>
              <View style={styles.sectionCard}>
                <View style={styles.optionGrid}>
                  {options.map((option) => {
                    const active = option.value === continueWatchingStyle;
                    return (
                      <TouchableOpacity
                        key={option.value}
                        activeOpacity={0.82}
                        style={[styles.optionCard, active && styles.optionCardActive]}
                        onPress={() => { void setContinueWatchingStyle(option.value); }}
                      >
                        {active ? (
                          <View style={styles.checkWrap}>
                            <Ionicons name="checkmark" size={16} color={isLightAppearance ? '#ffffff' : '#111111'} />
                          </View>
                        ) : null}
                        <View style={styles.previewFrame}>
                          {renderPreview(option.value, colors, active)}
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
