import React, { RefObject } from 'react';
import { View } from 'react-native';
import { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { StackBottomNav } from './StackBottomNav';

export const BOTTOM_NAV_HEIGHT_WITH_LABELS    = 70;
export const BOTTOM_NAV_HEIGHT_WITHOUT_LABELS = 62;
// Export a runtime-safe default; consumers that need the real value should
// call useDisplaySettings() themselves.
export const BOTTOM_NAV_HEIGHT = BOTTOM_NAV_HEIGHT_WITH_LABELS;

type BottomNavBarProps = BottomTabBarProps & {
  blurTarget?: RefObject<View | null>;
};

export const BottomNavBar: React.FC<BottomNavBarProps> = ({ state, navigation, blurTarget }) => {
  const activeRouteName = state.routes[state.index].name;

  return (
    <StackBottomNav
      activeTab={activeRouteName}
      blurTarget={blurTarget}
      onTabPress={(tabName) => {
        // Emit tabPress like the stock tab bar so useScrollToTop fires —
        // tapping the active tab (e.g. Home) scrolls its list back to the top.
        const route = state.routes.find(candidate => candidate.name === tabName);
        const event = navigation.emit({
          type: 'tabPress',
          target: route?.key,
          canPreventDefault: true,
        });
        if (activeRouteName !== tabName && !event.defaultPrevented) {
          navigation.navigate(tabName);
        }
      }}
    />
  );
};
