import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { API_BASE } from '../constants/api';
import type { SessionUser } from '../lib/authClient';
import { useAuth } from './AuthContext';
import { useProfile } from './ProfileContext';
import { buildAuthHeaders } from '../utils/authHeaders';

export type PollStatus = 'authorized' | 'pending' | 'slow_down' | 'expired' | 'denied' | 'error';

export interface TraktItem {
  id: string;
  tmdbId?: number | null;
  title: string;
  type: 'movie' | 'tv';
  year?: string;
  rating?: number | null;
  poster?: string | null;
  backdrop?: string | null;
  description?: string | null;
  runtime?: number;
  progress?: number;
  unwatchedEpisodes?: number;
}

export interface DeviceCodeInfo {
  device_code: string;
  user_code: string;
  verification_url: string;
  expires_in: number;
  interval: number;
}

export interface ScrobblePayload {
  movie?: {
    title: string;
    year?: number;
    ids?: { tmdb?: number; imdb?: string; trakt?: number };
  };
  show?: {
    title: string;
    year?: number;
    ids?: { tmdb?: number; imdb?: string; trakt?: number };
  };
  episode?: {
    season: number;
    number: number;
    title?: string;
    ids?: { trakt?: number };
  };
  progress: number;
}

interface TraktContextType {
  isConnected: boolean;
  traktUsername: string | null;
  isLoading: boolean;
  continueWatching: TraktItem[];
  watchlist: TraktItem[];
  trending: TraktItem[];
  recommendations: TraktItem[];
  checkStatus: () => Promise<void>;
  refreshContinueWatching: () => Promise<void>;
  refreshWatchlist: () => Promise<void>;
  refreshTrending: () => Promise<void>;
  refreshRecommendations: () => Promise<void>;
  initiateDeviceCode: () => Promise<DeviceCodeInfo | null>;
  pollDeviceToken: (deviceCode: string) => Promise<PollStatus>;
  disconnect: () => Promise<void>;
  scrobble: (action: 'start' | 'pause' | 'stop', payload: ScrobblePayload) => Promise<void>;
}

const TraktContext = createContext<TraktContextType>({
  isConnected: false,
  traktUsername: null,
  isLoading: true,
  continueWatching: [],
  watchlist: [],
  trending: [],
  recommendations: [],
  checkStatus: async () => {},
  refreshContinueWatching: async () => {},
  refreshWatchlist: async () => {},
  refreshTrending: async () => {},
  refreshRecommendations: async () => {},
  initiateDeviceCode: async () => null,
  pollDeviceToken: async () => 'error',
  disconnect: async () => {},
  scrobble: async () => {},
});

export const TraktProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const [isConnected, setIsConnected] = useState(false);
  const [traktUsername, setTraktUsername] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [continueWatching, setContinueWatching] = useState<TraktItem[]>([]);
  const [watchlist, setWatchlist] = useState<TraktItem[]>([]);
  const [trending, setTrending] = useState<TraktItem[]>([]);
  const [recommendations, setRecommendations] = useState<TraktItem[]>([]);
  const lastProfileSessionKeyRef = useRef<string | null>(null);

  const authUid = user?.uid ?? null;
  const authAccessToken = user?.accessToken ?? null;
  const activeProfileId = activeProfile?.id ?? null;
  const authEmail = user?.email ?? null;
  const authDisplayName = user?.displayName ?? null;
  const authSubscriptionStatus = user?.subscriptionStatus ?? 'free';
  const buildProfileHeaders = useCallback(async () => {
    const sessionUser: SessionUser | null = authUid && authAccessToken
      ? {
          uid: authUid,
          email: authEmail,
          displayName: authDisplayName,
          subscriptionStatus: authSubscriptionStatus,
          accessToken: authAccessToken,
        }
      : null;
    return buildAuthHeaders(sessionUser, { profileId: activeProfileId });
  }, [activeProfileId, authAccessToken, authDisplayName, authEmail, authSubscriptionStatus, authUid]);

  useEffect(() => {
    const sessionKey = authUid && activeProfileId ? `${authUid}:${activeProfileId}` : null;
    if (sessionKey === lastProfileSessionKeyRef.current) return;
    lastProfileSessionKeyRef.current = sessionKey;
    setIsConnected(false);
    setTraktUsername(null);
    setContinueWatching([]);
    setWatchlist([]);
    setRecommendations([]);
    setIsLoading(Boolean(sessionKey));
  }, [activeProfileId, authUid]);

  const checkStatus = useCallback(async () => {
    if (!authUid || !authAccessToken || !activeProfileId) {
      setIsConnected(false);
      setTraktUsername(null);
      setContinueWatching([]);
      setWatchlist([]);
      setRecommendations([]);
      setIsLoading(false);
      return;
    }
    try {
      const res = await fetch(`${API_BASE}/trakt/auth/status`, { headers: await buildProfileHeaders() });
      const data = await res.json();
      setIsConnected(data.connected ?? false);
      setTraktUsername(data.username ?? null);
    } catch {
      setIsConnected(false);
      setTraktUsername(null);
    } finally {
      setIsLoading(false);
    }
  }, [activeProfileId, authAccessToken, authUid, buildProfileHeaders]);

  useEffect(() => {
    void checkStatus();
  }, [checkStatus]);

  const refreshContinueWatching = useCallback(async () => {
    if (!authUid || !authAccessToken || !activeProfileId) {
      setContinueWatching([]);
      return;
    }
    try {
      const res = await fetch(`${API_BASE}/trakt/sync/playback`, { headers: await buildProfileHeaders() });
      if (!res.ok) {
        setContinueWatching([]);
        return;
      }
      const data = await res.json();
      setContinueWatching(data.results ?? []);
    } catch {
      setContinueWatching([]);
    }
  }, [activeProfileId, authAccessToken, authUid, buildProfileHeaders]);

  const refreshWatchlist = useCallback(async () => {
    if (!authUid || !authAccessToken || !activeProfileId) {
      setWatchlist([]);
      return;
    }
    try {
      const res = await fetch(`${API_BASE}/trakt/sync/watchlist/enriched`, { headers: await buildProfileHeaders() });
      if (!res.ok) {
        setWatchlist([]);
        return;
      }
      const data = await res.json();
      setWatchlist(data.results ?? []);
    } catch {
      setWatchlist([]);
    }
  }, [activeProfileId, authAccessToken, authUid, buildProfileHeaders]);

  const refreshTrending = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/trakt/trending/movies`);
      if (!res.ok) {
        setTrending([]);
        return;
      }
      const data = await res.json();
      setTrending(data.results ?? []);
    } catch {
      setTrending([]);
    }
  }, []);

  const refreshRecommendations = useCallback(async () => {
    if (!authUid || !authAccessToken || !activeProfileId) {
      setRecommendations([]);
      return;
    }
    try {
      const res = await fetch(`${API_BASE}/trakt/recommendations/movies`, { headers: await buildProfileHeaders() });
      if (!res.ok) {
        setRecommendations([]);
        return;
      }
      const data = await res.json();
      setRecommendations(data.results ?? []);
    } catch {
      setRecommendations([]);
    }
  }, [activeProfileId, authAccessToken, authUid, buildProfileHeaders]);

  useEffect(() => {
    if (isConnected) {
      void refreshContinueWatching();
      void refreshWatchlist();
      void refreshTrending();
      void refreshRecommendations();
    } else {
      setContinueWatching([]);
      setWatchlist([]);
      setTrending([]);
      setRecommendations([]);
    }
  }, [isConnected, refreshContinueWatching, refreshRecommendations, refreshTrending, refreshWatchlist]);

  const initiateDeviceCode = useCallback(async (): Promise<DeviceCodeInfo | null> => {
    try {
      const res = await fetch(`${API_BASE}/trakt/auth/device/code`, { method: 'POST' });
      if (!res.ok) return null;
      return await res.json();
    } catch {
      return null;
    }
  }, []);

  const pollDeviceToken = useCallback(async (deviceCode: string): Promise<PollStatus> => {
    if (!authUid || !authAccessToken || !activeProfileId) return 'error';
    try {
      const res = await fetch(`${API_BASE}/trakt/auth/device/poll`, {
        method: 'POST',
        headers: await buildProfileHeaders(),
        body: JSON.stringify({ device_code: deviceCode }),
      });
      const data = await res.json();
      if (data.status === 'authorized') {
        if (data.connected !== true) {
          setIsConnected(false);
          setTraktUsername(null);
          return 'error';
        }
        setIsConnected(true);
        setTraktUsername(data.username ?? null);
      }
      return (data.status as PollStatus) ?? 'error';
    } catch {
      return 'error';
    }
  }, [activeProfileId, authAccessToken, authUid, buildProfileHeaders]);

  const disconnect = useCallback(async () => {
    if (!authUid || !authAccessToken || !activeProfileId) return;
    try {
      await fetch(`${API_BASE}/trakt/auth/disconnect`, {
        method: 'DELETE',
        headers: await buildProfileHeaders(),
      });
    } catch {}
    setIsConnected(false);
    setTraktUsername(null);
    setContinueWatching([]);
    setWatchlist([]);
    setRecommendations([]);
  }, [activeProfileId, authAccessToken, authUid, buildProfileHeaders]);

  const scrobble = useCallback(async (
    action: 'start' | 'pause' | 'stop',
    payload: ScrobblePayload,
  ) => {
    if (!authUid || !authAccessToken || !activeProfileId || !isConnected) return;
    try {
      await fetch(`${API_BASE}/trakt/scrobble/${action}`, {
        method: 'POST',
        headers: await buildProfileHeaders(),
        body: JSON.stringify(payload),
      });
    } catch {}
  }, [activeProfileId, authAccessToken, authUid, buildProfileHeaders, isConnected]);

  const contextValue = useMemo(() => ({
    isConnected,
    traktUsername,
    isLoading,
    continueWatching,
    watchlist,
    trending,
    recommendations,
    checkStatus,
    refreshContinueWatching,
    refreshWatchlist,
    refreshTrending,
    refreshRecommendations,
    initiateDeviceCode,
    pollDeviceToken,
    disconnect,
    scrobble,
  }), [
    isConnected,
    traktUsername,
    isLoading,
    continueWatching,
    watchlist,
    trending,
    recommendations,
    checkStatus,
    refreshContinueWatching,
    refreshWatchlist,
    refreshTrending,
    refreshRecommendations,
    initiateDeviceCode,
    pollDeviceToken,
    disconnect,
    scrobble,
  ]);

  return (
    <TraktContext.Provider value={contextValue}>
      {children}
    </TraktContext.Provider>
  );
};

export const useTrakt = () => useContext(TraktContext);
