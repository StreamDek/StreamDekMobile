import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../context/ThemeContext';

const PIN_LENGTH = 4;
const NUMPAD_ROWS = [['1', '2', '3'], ['4', '5', '6'], ['7', '8', '9'], ['', '0', 'del']];

type Props = {
  pinValue: string;
  confirmPin: string;
  onChangePin: (value: string) => void;
  onChangeConfirmPin: (value: string) => void;
  pinLabel?: string;
  confirmLabel?: string;
  mismatchText?: string;
};

export function ProfilePinComposer({
  pinValue,
  confirmPin,
  onChangePin,
  onChangeConfirmPin,
  pinLabel = 'PIN',
  confirmLabel = 'Confirm PIN',
  mismatchText = 'PINs do not match.',
}: Props) {
  const { theme, resolvedAppearance } = useTheme();
  const c = theme.colors;
  const isLight = resolvedAppearance === 'light';
  const [activeField, setActiveField] = React.useState<'pin' | 'confirm'>('pin');
  const pinMismatch = confirmPin.length === PIN_LENGTH && confirmPin !== pinValue;

  const appendDigit = React.useCallback((digit: string) => {
    if (activeField === 'pin') {
      if (pinValue.length < PIN_LENGTH) onChangePin(pinValue + digit);
      return;
    }
    if (confirmPin.length < PIN_LENGTH) onChangeConfirmPin(confirmPin + digit);
  }, [activeField, confirmPin, onChangeConfirmPin, onChangePin, pinValue]);

  const deleteDigit = React.useCallback(() => {
    if (activeField === 'pin') {
      onChangePin(pinValue.slice(0, -1));
      return;
    }
    onChangeConfirmPin(confirmPin.slice(0, -1));
  }, [activeField, confirmPin, onChangeConfirmPin, onChangePin, pinValue]);

  return (
    <View style={styles.container}>
      <PinField
        active={activeField === 'pin'}
        label={pinLabel}
        value={pinValue}
        onPress={() => setActiveField('pin')}
        isLight={isLight}
        textColor={c.textPrimary}
        mutedText={c.mutedText}
        borderColor={c.border}
        elevatedBg={isLight ? c.cardBgElevated : 'rgba(255,255,255,0.08)'}
      />
      <PinField
        active={activeField === 'confirm'}
        label={confirmLabel}
        value={confirmPin}
        onPress={() => setActiveField('confirm')}
        isLight={isLight}
        textColor={c.textPrimary}
        mutedText={c.mutedText}
        borderColor={pinMismatch ? '#ef4444' : c.border}
        elevatedBg={isLight ? c.cardBgElevated : 'rgba(255,255,255,0.08)'}
      />
      {pinMismatch ? (
        <Text style={styles.errorText}>{mismatchText}</Text>
      ) : null}
      <View style={styles.numpad}>
        {NUMPAD_ROWS.map((row, rowIndex) => (
          <View key={rowIndex} style={styles.numpadRow}>
            {row.map((key, keyIndex) => {
              if (!key) return <View key={keyIndex} style={styles.numpadKey} />;
              if (key === 'del') {
                return (
                  <TouchableOpacity
                    key={keyIndex}
                    style={[
                      styles.numpadKey,
                      {
                        backgroundColor: isLight ? c.cardBgElevated : 'rgba(255,255,255,0.10)',
                        borderColor: c.border,
                      },
                    ]}
                    activeOpacity={0.7}
                    onPress={deleteDigit}
                  >
                    <Ionicons name="backspace-outline" size={22} color={c.textPrimary} />
                  </TouchableOpacity>
                );
              }
              return (
                <TouchableOpacity
                  key={keyIndex}
                  style={[
                    styles.numpadKey,
                    {
                      backgroundColor: isLight ? c.cardBgElevated : 'rgba(255,255,255,0.10)',
                      borderColor: c.border,
                    },
                  ]}
                  activeOpacity={0.7}
                  onPress={() => appendDigit(key)}
                >
                  <Text style={[styles.numpadDigit, { color: c.textPrimary }]}>{key}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        ))}
      </View>
    </View>
  );
}

function PinField({
  active,
  label,
  value,
  onPress,
  isLight,
  textColor,
  mutedText,
  borderColor,
  elevatedBg,
}: {
  active: boolean;
  label: string;
  value: string;
  onPress: () => void;
  isLight: boolean;
  textColor: string;
  mutedText: string;
  borderColor: string;
  elevatedBg: string;
}) {
  return (
    <TouchableOpacity
      style={[
        styles.field,
        {
          backgroundColor: elevatedBg,
          borderColor,
          shadowOpacity: active && isLight ? 0.12 : 0,
        },
      ]}
      activeOpacity={0.82}
      onPress={onPress}
    >
      <Text style={[styles.fieldLabel, { color: textColor }]}>{label}</Text>
      <View style={styles.dots}>
        {Array.from({ length: PIN_LENGTH }, (_, index) => (
          <View
            key={index}
            style={[
              styles.dot,
              {
                backgroundColor: value.length > index ? textColor : 'transparent',
                borderColor: value.length > index ? textColor : mutedText,
              },
            ]}
          />
        ))}
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 16,
    paddingVertical: 14,
    gap: 12,
  },
  field: {
    borderWidth: 1,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 14,
    shadowColor: '#000',
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 2 },
    elevation: 1,
  },
  fieldLabel: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 10,
  },
  dots: {
    flexDirection: 'row',
    gap: 12,
  },
  dot: {
    width: 14,
    height: 14,
    borderRadius: 7,
    borderWidth: 1.5,
  },
  errorText: {
    color: '#ef4444',
    fontSize: 12,
    marginTop: -2,
  },
  numpad: {
    marginTop: 6,
    gap: 10,
  },
  numpadRow: {
    flexDirection: 'row',
    gap: 10,
  },
  numpadKey: {
    flex: 1,
    height: 54,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  numpadDigit: {
    fontSize: 24,
    fontWeight: '600',
  },
});
