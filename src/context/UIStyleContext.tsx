import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { useProfile } from './ProfileContext';
import { profileScopedStorageKey } from '../utils/profileStorage';

export type UIStyle = 'classic' | 'centered' | 'glass';

interface UIStyleContextType {
  uiStyle: UIStyle;
  setUiStyle: (style: UIStyle) => Promise<void>;
}

const UIStyleContext = createContext<UIStyleContextType>({
  uiStyle: 'glass',
  setUiStyle: async () => {},
});

const UI_STYLE_KEY = 'streamdek_ui_style';

export const UIStyleProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const [uiStyle, setUiStyleState] = useState<UIStyle>('glass');
  const uiStyleKey = profileScopedStorageKey(UI_STYLE_KEY, user?.uid, activeProfile?.id);
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;
    void Promise.all([
      Storage.getItem(uiStyleKey),
      Storage.getItem(UI_STYLE_KEY),
    ]).then(([value, legacyValue]) => {
      const next = value ?? legacyValue;
      if (!cancelled && (next === 'classic' || next === 'centered' || next === 'glass')) {
        setUiStyleState(next as UIStyle);
      }
      if (!cancelled && !next) {
        setUiStyleState('glass');
      }
    });
    return () => {
      cancelled = true;
    };
  }, [uiStyleKey]);

  const setUiStyle = async (style: UIStyle) => {
    setUiStyleState(style);
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
    // Page style is a local UI preference, so never block the visible layout swap on disk IO.
    persistTimerRef.current = setTimeout(() => {
      void Storage.setItem(uiStyleKey, style).catch(() => {});
      persistTimerRef.current = null;
    }, 50);
  };

  return (
    <UIStyleContext.Provider value={{ uiStyle, setUiStyle }}>
      {children}
    </UIStyleContext.Provider>
  );
};

export const useUIStyle = () => useContext(UIStyleContext);
