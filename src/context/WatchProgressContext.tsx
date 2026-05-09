import React, { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react';
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
  flushProgress: () => void;
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
  flushProgress: () => {},
  clearProgress: () => {},
  clearProgressIndexEntry: () => {},
});

export const WatchProgressProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const [progress, setProgress] = useState<ProgressMap>({});
  const ownerId = getProfileStorageOwnerId(user?.uid ?? null, activeProfile?.id ?? null);
  const progressRef = useRef<ProgressMap>({});
  const flushTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearFlushTimer = useCallback(() => {
    if (!flushTimerRef.current) return;
    clearTimeout(flushTimerRef.current);
    flushTimerRef.current = null;
  }, []);

  const flushProgressToStorage = useCallback((publishState = true) => {
    clearFlushTimer();
    const snapshot = progressRef.current;
    if (publishState) {
      setProgress({ ...snapshot });
    }
    void Storage.setItem(progressStorageKey(ownerId), JSON.stringify(snapshot));
  }, [clearFlushTimer, ownerId]);

  const scheduleProgressFlush = useCallback(() => {
    if (flushTimerRef.current) return;
    flushTimerRef.current = setTimeout(() => {
      flushTimerRef.current = null;
      const snapshot = progressRef.current;
      setProgress({ ...snapshot });
      void Storage.setItem(progressStorageKey(ownerId), JSON.stringify(snapshot));
    }, 5000);
  }, [ownerId]);

  // Reload progress whenever the signed-in user or profile changes
  useEffect(() => {
    const key = progressStorageKey(ownerId);
    Storage.getItem(key).then(async raw => {
      const value = raw;
      try {
        const parsed = value ? JSON.parse(value) : {};
        progressRef.current = parsed;
        setProgress(parsed);
      } catch {
        progressRef.current = {};
        setProgress({});
      }
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
        progressRef.current = {
          ...progressRef.current,
          ...Object.fromEntries(
            entries
              .filter((entry: any) => entry && typeof entry.key === 'string')
              .map((entry: any) => [
                entry.key,
                {
                  positionSec: Number(entry.positionSec ?? 0),
                  durationSec: Number(entry.durationSec ?? 0),
                  updatedAt: typeof entry.updatedAt === 'string' ? entry.updatedAt : new Date().toISOString(),
                },
              ]),
          ),
        };
      } catch {
        // Ignore malformed legacy progress data.
      }
    });
    return () => {
      flushProgressToStorage(false);
    };
  }, [flushProgressToStorage, ownerId]);

  const getProgress = useCallback((key: string): ProgressEntry | null => {
    return progressRef.current[key] ?? null;
  }, []);

  const saveProgress = useCallback((
    key: string,
    positionSec: number,
    durationSec: number,
  ) => {
    progressRef.current = {
      ...progressRef.current,
      [key]: {
        positionSec,
        durationSec,
        updatedAt: new Date().toISOString(),
      },
    };
    scheduleProgressFlush();
  }, [scheduleProgressFlush]);

  const flushProgress = useCallback(() => {
    flushProgressToStorage(true);
  }, [flushProgressToStorage]);

  const clearProgress = useCallback((key: string) => {
    const next = { ...progressRef.current };
    delete next[key];
    progressRef.current = next;
    flushProgressToStorage();
  }, [flushProgressToStorage]);

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

  useEffect(() => () => {
    flushProgressToStorage(false);
    clearFlushTimer();
  }, [clearFlushTimer, flushProgressToStorage]);

  return (
    <WatchProgressContext.Provider value={{ getProgress, saveProgress, flushProgress, clearProgress, clearProgressIndexEntry }}>
      {children}
    </WatchProgressContext.Provider>
  );
};

export const useWatchProgress = () => useContext(WatchProgressContext);
