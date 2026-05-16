import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { useProfile } from './ProfileContext';
import {
  getProfileStorageOwnerId,
  progressIndexStorageKey,
  progressStorageKey,
} from '../utils/profileStorage';

export interface ProgressEntry {
  positionSec: number;
  durationSec: number;
  updatedAt: string;
}

type ProgressMap = Record<string, ProgressEntry>;

interface WatchProgressContextType {
  getProgress: (key: string) => ProgressEntry | null;
  saveProgress: (key: string, positionSec: number, durationSec: number) => void;
  clearProgress: (key: string) => void;
  clearProgressIndexEntry: (key: string) => void;
}

/** Key for a movie by TMDB ID. */
export function movieProgressKey(tmdbId: number | string): string {
  return `movie:${tmdbId}`;
}

/** Key for a specific TV episode. */
export function episodeProgressKey(
  showTmdbId: number | string,
  season: number,
  ep: number,
): string {
  return `episode:${showTmdbId}:${season}:${ep}`;
}

const WatchProgressContext = createContext<WatchProgressContextType>({
  getProgress:  () => null,
  saveProgress: () => {},
  clearProgress: () => {},
  clearProgressIndexEntry: () => {},
});

export const WatchProgressProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const [progress, setProgress] = useState<ProgressMap>({});
  const ownerId = getProfileStorageOwnerId(user?.uid ?? null, activeProfile?.id ?? null);

  // Reload progress whenever the signed-in user or profile changes
  useEffect(() => {
    const key = progressStorageKey(ownerId);
    Storage.getItem(key).then(async raw => {
      const value = raw;
      try { setProgress(value ? JSON.parse(value) : {}); } catch { setProgress({}); }
    });
    const indexKey = progressIndexStorageKey(ownerId);
    Storage.getItem(indexKey).then(async raw => {
      const value = raw;
      if (!value) return;
      try {
        const entries = JSON.parse(value);
        if (!Array.isArray(entries)) return;
        setProgress(prev => {
          const next = { ...prev };
          for (const entry of entries) {
            if (!entry || typeof entry.key !== 'string') continue;
            if (next[entry.key]) continue;
            const positionSec = Number(entry.positionSec ?? 0);
            const durationSec = Number(entry.durationSec ?? 0);
            if (!Number.isFinite(positionSec) || !Number.isFinite(durationSec) || durationSec <= 0) continue;
            next[entry.key] = {
              positionSec,
              durationSec,
              updatedAt: typeof entry.updatedAt === 'string' ? entry.updatedAt : new Date().toISOString(),
            };
          }
          return next;
        });
      } catch {
        // Ignore malformed legacy progress data.
      }
    });
  }, [ownerId]);

  const getProgress = useCallback((key: string): ProgressEntry | null => {
    return progress[key] ?? null;
  }, [progress]);

  const saveProgress = useCallback((
    key: string,
    positionSec: number,
    durationSec: number,
  ) => {
    setProgress(prev => {
      const entry: ProgressEntry = {
        positionSec,
        durationSec,
        updatedAt: new Date().toISOString(),
      };
      const next = { ...prev, [key]: entry };
      const storageKey = progressStorageKey(ownerId);
      Storage.setItem(storageKey, JSON.stringify(next));
      return next;
    });
  }, [ownerId]);

  const clearProgress = useCallback((key: string) => {
    setProgress(prev => {
      const next = { ...prev };
      delete next[key];
      const storageKey = progressStorageKey(ownerId);
      Storage.setItem(storageKey, JSON.stringify(next));
      return next;
    });
  }, [ownerId]);

  const clearProgressIndexEntry = useCallback((key: string) => {
    const indexKey = progressIndexStorageKey(ownerId);
    void (async () => {
      try {
        const raw = await Storage.getItem(indexKey);
        if (!raw) return;
        const parsed = JSON.parse(raw);
        if (!Array.isArray(parsed)) return;
        const next = parsed.filter((entry: any) => entry?.key !== key);
        await Storage.setItem(indexKey, JSON.stringify(next));
      } catch {
        // Ignore malformed legacy progress index data.
      }
    })();
  }, [ownerId]);

  return (
    <WatchProgressContext.Provider value={{ getProgress, saveProgress, clearProgress, clearProgressIndexEntry }}>
      {children}
    </WatchProgressContext.Provider>
  );
};

export const useWatchProgress = () => useContext(WatchProgressContext);
