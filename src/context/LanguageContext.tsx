import React, { createContext, useContext, useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { Storage } from '../utils/storage';
import {
  LANGUAGES, TRANSLATIONS, Language, LanguageCode, TranslationKey,
} from '../i18n/translations';
import { SERVER_SECTION_TRANSLATIONS } from '../i18n/serverSectionTranslations';
import { useAuth } from './AuthContext';
import { useProfile } from './ProfileContext';
import { profileScopedStorageKey } from '../utils/profileStorage';

const LANG_STORAGE_KEY = 'streamdek_language';

interface LanguageContextType {
  language:    Language;
  setLanguage: (code: LanguageCode) => Promise<void>;
  t:           (key: TranslationKey, params?: Record<string, string | number>) => string;
}

const LanguageContext = createContext<LanguageContextType>({
  language:    LANGUAGES[0],
  setLanguage: async () => {},
  t:           (key, params) => key,
});

export const LanguageProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const [code, setCode] = useState<LanguageCode>('en');
  const langStorageKey = profileScopedStorageKey(LANG_STORAGE_KEY, user?.uid, activeProfile?.id);
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;
    void Promise.all([
      Storage.getItem(langStorageKey),
      Storage.getItem(LANG_STORAGE_KEY),
    ]).then(([saved, legacySaved]) => {
      const next = saved ?? legacySaved;
      if (!cancelled && next && LANGUAGES.find(l => l.code === next)) {
        setCode(next as LanguageCode);
      }
      if (!cancelled && !next) {
        setCode('en');
      }
    });
    return () => {
      cancelled = true;
    };
  }, [langStorageKey]);

  const setLanguage = useCallback(async (next: LanguageCode) => {
    setCode(next);
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
    // Apply translated UI immediately; persist the preference just after the interaction frame.
    persistTimerRef.current = setTimeout(() => {
      void Storage.setItem(langStorageKey, next).catch(() => {});
      persistTimerRef.current = null;
    }, 50);
  }, [langStorageKey]);

  const t = useCallback((key: TranslationKey, params?: Record<string, string | number>): string => {
    let str = SERVER_SECTION_TRANSLATIONS[code]?.[key]
      ?? TRANSLATIONS[code][key]
      ?? SERVER_SECTION_TRANSLATIONS['en']?.[key]
      ?? TRANSLATIONS['en'][key]
      ?? key;
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        str = str.replace(new RegExp(`{${k}}`, 'g'), String(v));
      });
    }
    return str;
  }, [code]);

  const language = useMemo(() => LANGUAGES.find(l => l.code === code) ?? LANGUAGES[0], [code]);

  return (
    <LanguageContext.Provider value={{ language, setLanguage, t }}>
      {children}
    </LanguageContext.Provider>
  );
};

export const useLanguage = () => useContext(LanguageContext);

// Re-export types for convenience
export type { LanguageCode, Language, TranslationKey };
export { LANGUAGES };
