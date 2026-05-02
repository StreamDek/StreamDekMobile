import React, { useEffect, useRef } from 'react';
import {
  Modal, View, Text, TouchableOpacity, StyleSheet,
  Animated, Pressable, ScrollView, Dimensions, Easing,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../context/ThemeContext';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

export interface ActionSheetAction {
  label:    string;
  icon?:    IoniconName;
  onPress:  () => void;
  variant?: 'default' | 'accent' | 'destructive' | 'cancel';
}

interface ActionSheetProps {
  visible:   boolean;
  onClose:   () => void;
  title?:    string;
  subtitle?: string;
  actions:   ActionSheetAction[];
}

export const ActionSheet: React.FC<ActionSheetProps> = ({
  visible, onClose, title, subtitle, actions,
}) => {
  const insets = useSafeAreaInsets();
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const isLightAppearance = resolvedAppearance === 'light';
  const lightMonoContrast = isLightAppearance && theme.id === 'monochrome';
  const screenHeight = Dimensions.get('window').height;
  const slideAnim = useRef(new Animated.Value(screenHeight)).current;
  const fadeAnim  = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      slideAnim.setValue(screenHeight);
      Animated.parallel([
        Animated.timing(fadeAnim,  { toValue: 1, duration: 220, useNativeDriver: true }),
        Animated.timing(slideAnim, {
          toValue: 0,
          duration: 280,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }),
      ]).start();
    } else {
      Animated.parallel([
        Animated.timing(fadeAnim,  { toValue: 0, duration: 160, useNativeDriver: true }),
        Animated.timing(slideAnim, {
          toValue: screenHeight,
          duration: 180,
          easing: Easing.in(Easing.cubic),
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [screenHeight, slideAnim, fadeAnim, visible]);

  const getActionColor = (variant: ActionSheetAction['variant']) => {
    switch (variant) {
      case 'accent':      return isLightAppearance ? (lightMonoContrast ? '#111111' : colors.accent) : colors.accentSoft;
      case 'destructive': return '#ff4d4d';
      case 'cancel':      return isLightAppearance ? colors.textSecondary : colors.subText;
      default:            return isLightAppearance ? colors.textPrimary : '#e8e8f0';
    }
  };

  return (
    <Modal visible={visible} transparent animationType="none" statusBarTranslucent onRequestClose={onClose}>
      {/* Dimmed backdrop */}
      <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: colors.overlayStrong, opacity: fadeAnim }]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />
      </Animated.View>

      {/* Sheet */}
      <Animated.View
        style={{
          position: 'absolute', left: 0, right: 0, bottom: 0,
          transform: [{ translateY: slideAnim }],
        }}
      >
        <View style={{
          backgroundColor: colors.cardBg,
          borderTopLeftRadius: 20, borderTopRightRadius: 20,
          borderTopWidth: 1, borderLeftWidth: 1, borderRightWidth: 1,
          borderColor: colors.border,
          paddingBottom: insets.bottom + 8,
          overflow: 'hidden',
          shadowColor: '#000',
          shadowOpacity: isLightAppearance ? 0.12 : 0.28,
          shadowRadius: 18,
          shadowOffset: { width: 0, height: -6 },
          elevation: 18,
        }}>
          {/* Handle */}
          <View style={{
            width: 36, height: 4, borderRadius: 2,
            backgroundColor: colors.border,
            alignSelf: 'center', marginTop: 10, marginBottom: 4,
          }} />

          {/* Title block */}
          {(title || subtitle) && (
            <View style={{
              paddingHorizontal: 20, paddingVertical: 12,
              borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.border,
            }}>
              {title    && <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '800', textAlign: 'center' }}>{title}</Text>}
              {subtitle && <Text style={{ color: colors.textSecondary, fontSize: 12, textAlign: 'center', marginTop: 3, lineHeight: 18 }}>{subtitle}</Text>}
            </View>
          )}

          {/* Actions */}
          <ScrollView bounces={false} showsVerticalScrollIndicator={false} style={{ maxHeight: screenHeight * 0.5 }}>
            {actions.map((action, i) => {
              const isLast   = i === actions.length - 1;
              const isCancel = action.variant === 'cancel';
              return (
                <React.Fragment key={i}>
                  {isCancel && i > 0 && (
                    <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: colors.border }} />
                  )}
                  <TouchableOpacity
                    onPress={() => { onClose(); action.onPress(); }}
                    activeOpacity={0.7}
                    style={{
                      flexDirection: 'row', alignItems: 'center', gap: 14,
                      paddingHorizontal: 20, paddingVertical: 16,
                      backgroundColor: isCancel ? colors.inputBg : 'transparent',
                      ...((!isLast && !isCancel) ? {
                        borderBottomWidth: StyleSheet.hairlineWidth,
                        borderBottomColor: colors.border,
                      } : {}),
                    }}
                  >
                    {action.icon && (
                      <Ionicons name={action.icon} size={20} color={getActionColor(action.variant)} />
                    )}
                    <Text style={{
                      flex: 1,
                      fontSize: 15,
                      fontWeight: isCancel ? '600' : '700',
                      color: getActionColor(action.variant),
                    }}>
                      {action.label}
                    </Text>
                  </TouchableOpacity>
                </React.Fragment>
              );
            })}
          </ScrollView>
        </View>
      </Animated.View>
    </Modal>
  );
};
