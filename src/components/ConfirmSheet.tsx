import React, { useEffect, useRef } from 'react';
import {
  Modal, View, Text, TouchableOpacity, StyleSheet, Animated, Pressable,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../context/ThemeContext';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

interface ConfirmSheetProps {
  visible:       boolean;
  onClose:       () => void;
  title:         string;
  message?:      string;
  icon?:         IoniconName;
  iconColor?:    string;
  confirmLabel:  string;
  cancelLabel?:  string;
  onConfirm:     () => void;
  /** 'destructive' tints the confirm button red; 'accent' uses theme accent */
  variant?:      'accent' | 'destructive';
  /** Single-button info dialog — hides the cancel button */
  infoOnly?:     boolean;
}

export const ConfirmSheet: React.FC<ConfirmSheetProps> = ({
  visible, onClose, title, message, icon, iconColor,
  confirmLabel, cancelLabel = 'Cancel', onConfirm,
  variant = 'accent', infoOnly = false,
}) => {
  const { theme, resolvedAppearance } = useTheme();
  const { colors } = theme;
  const isLightAppearance = resolvedAppearance === 'light';
  const lightMonoContrast = isLightAppearance && theme.id === 'monochrome';
  const cardBgColor = isLightAppearance ? '#ffffff' : colors.cardBg;
  const cardBorderColor = isLightAppearance ? 'rgba(17,24,39,0.10)' : colors.border;
  const titleTextColor = isLightAppearance ? '#111111' : colors.textPrimary;
  const bodyTextColor = isLightAppearance ? colors.textPrimary : colors.textSecondary;
  const cancelTextColor = isLightAppearance ? colors.textPrimary : colors.textSecondary;
  const scaleAnim = useRef(new Animated.Value(0.88)).current;
  const fadeAnim  = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      Animated.parallel([
        Animated.timing(fadeAnim,  { toValue: 1, duration: 180, useNativeDriver: true }),
        Animated.spring(scaleAnim, { toValue: 1, damping: 18, stiffness: 220, useNativeDriver: true }),
      ]).start();
    } else {
      Animated.parallel([
        Animated.timing(fadeAnim,  { toValue: 0, duration: 130, useNativeDriver: true }),
        Animated.timing(scaleAnim, { toValue: 0.88, duration: 140, useNativeDriver: true }),
      ]).start();
    }
  }, [visible]);

  const confirmColor = variant === 'destructive'
    ? '#ff4d4d'
    : (isLightAppearance ? '#111111' : (lightMonoContrast ? '#111111' : colors.accent));
  const resolvedIconColor = iconColor ?? (variant === 'destructive'
    ? '#ff4d4d'
    : (isLightAppearance ? '#111111' : (lightMonoContrast ? '#111111' : colors.accent)));

  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onClose}>
      {/* Backdrop */}
      <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: colors.overlayStrong, opacity: fadeAnim }]}>
        {!infoOnly && <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />}
      </Animated.View>

      {/* Card */}
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', padding: 32 }} pointerEvents="box-none">
        <Animated.View style={{
          width: '100%', maxWidth: 340,
          backgroundColor: cardBgColor,
          borderRadius: 20, overflow: 'hidden',
          borderWidth: 1, borderColor: cardBorderColor,
          transform: [{ scale: scaleAnim }],
          opacity: fadeAnim,
          shadowColor: '#000',
          shadowOpacity: isLightAppearance ? 0.12 : 0.28,
          shadowRadius: 18,
          shadowOffset: { width: 0, height: 8 },
          elevation: 18,
        }}>
          {/* Icon + text */}
          <View style={{ padding: 28, paddingBottom: 20, alignItems: 'center', gap: 12 }}>
            {icon && (
              <View style={{
                width: 56, height: 56, borderRadius: 28,
                backgroundColor: isLightAppearance ? 'rgba(17,24,39,0.08)' : resolvedIconColor + '18',
                justifyContent: 'center', alignItems: 'center',
                marginBottom: 4,
              }}>
                <Ionicons name={icon} size={28} color={resolvedIconColor} />
              </View>
            )}
            <Text style={{
              color: titleTextColor, fontSize: 17, fontWeight: '800',
              textAlign: 'center', lineHeight: 24,
            }}>
              {title}
            </Text>
            {message && (
              <Text style={{
                color: bodyTextColor, fontSize: 13,
                textAlign: 'center', lineHeight: 20,
              }}>
                {message}
              </Text>
            )}
          </View>

          {/* Divider */}
          <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: cardBorderColor }} />

          {/* Buttons */}
          <View style={{ flexDirection: infoOnly ? 'column' : 'row' }}>
            {!infoOnly && (
              <>
                <TouchableOpacity
                  onPress={onClose}
                  activeOpacity={0.7}
                  style={{
                    flex: 1, paddingVertical: 16, alignItems: 'center',
                    borderRightWidth: StyleSheet.hairlineWidth, borderRightColor: cardBorderColor,
                  }}
                >
                  <Text style={{ color: cancelTextColor, fontSize: 15, fontWeight: '600' }}>
                    {cancelLabel}
                  </Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => { onClose(); onConfirm(); }}
                  activeOpacity={0.7}
                  style={{ flex: 1, paddingVertical: 16, alignItems: 'center' }}
                >
                  <Text style={{ color: confirmColor, fontSize: 15, fontWeight: '800' }}>
                    {confirmLabel}
                  </Text>
                </TouchableOpacity>
              </>
            )}
            {infoOnly && (
              <TouchableOpacity
                onPress={() => { onClose(); onConfirm(); }}
                activeOpacity={0.7}
                style={{ paddingVertical: 16, alignItems: 'center' }}
              >
                <Text style={{ color: confirmColor, fontSize: 15, fontWeight: '700' }}>
                  {confirmLabel}
                </Text>
              </TouchableOpacity>
            )}
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
};
