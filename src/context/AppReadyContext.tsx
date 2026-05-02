import React, { createContext, useContext, useState } from 'react';

type AppReadyContextType = {
  isReady: boolean;
  setAppReady: (ready: boolean) => void;
};

const AppReadyContext = createContext<AppReadyContextType>({
  isReady: false,
  setAppReady: () => {},
});

export const AppReadyProvider = ({ children }: { children: React.ReactNode }) => {
  const [isReady, setAppReady] = useState(false);
  return (
    <AppReadyContext.Provider value={{ isReady, setAppReady }}>
      {children}
    </AppReadyContext.Provider>
  );
};

export const useAppReady = () => useContext(AppReadyContext);
