import React, { useState, useCallback } from 'react';
import { TouchableOpacity, TouchableOpacityProps, StyleSheet, ViewStyle } from 'react-native';

interface TVFocusableProps extends TouchableOpacityProps {
  children: React.ReactNode;
  focusedStyle?: ViewStyle;
  normalStyle?: ViewStyle;
}

export const TVFocusable: React.FC<TVFocusableProps> = ({ 
  children, 
  focusedStyle, 
  normalStyle, 
  style, 
  ...props 
}) => {
  const [isFocused, setIsFocused] = useState(false);

  const handleFocus = useCallback(() => {
    setIsFocused(true);
  }, []);

  const handleBlur = useCallback(() => {
    setIsFocused(false);
  }, []);

  return (
    <TouchableOpacity
      activeOpacity={0.8}
      onFocus={handleFocus}
      onBlur={handleBlur}
      style={[
        styles.base,
        style,
        normalStyle,
        isFocused && styles.focused,
        isFocused && focusedStyle,
      ]}
      {...props}
    >
      {children}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  base: {
    padding: 10,
    margin: 5,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: 'transparent',
    backgroundColor: '#2a2a2a',
  },
  focused: {
    borderColor: '#00ffcc',
    backgroundColor: '#3a3a3a',
    transform: [{ scale: 1.05 }],
  }
});
