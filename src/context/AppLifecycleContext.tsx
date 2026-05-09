import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { AppState, AppStateStatus } from 'react-native';

type AppLifecycleContextValue = {
  appState: AppStateStatus;
  isForeground: boolean;
};

const AppLifecycleContext = createContext<AppLifecycleContextValue>({
  appState: AppState.currentState,
  isForeground: AppState.currentState === 'active',
});

export function AppLifecycleProvider({ children }: { children: React.ReactNode }) {
  const [appState, setAppState] = useState<AppStateStatus>(AppState.currentState);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', setAppState);
    return () => subscription.remove();
  }, []);

  const value = useMemo<AppLifecycleContextValue>(() => ({
    appState,
    isForeground: appState === 'active',
  }), [appState]);

  return (
    <AppLifecycleContext.Provider value={value}>
      {children}
    </AppLifecycleContext.Provider>
  );
}

export function useAppLifecycle() {
  return useContext(AppLifecycleContext);
}
