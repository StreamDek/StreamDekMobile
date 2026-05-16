import React, {
  createContext, useContext, useEffect, useState, useCallback, useRef, useMemo,
} from 'react';
import { API_BASE } from '../constants/api';
import { Storage } from '../utils/storage';
import { useAuth } from './AuthContext';
import { useProfile } from './ProfileContext';
import { useTrakt } from './TraktContext';
import { episodeProgressKey, movieProgressKey, useWatchProgress } from './WatchProgressContext';
import { buildAuthHeaders } from '../utils/authHeaders';


const STORAGE_KEY = 'streamdek_watched_v1';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface LocalWatchedItem {
  /** Stable composite key: "movie:{tmdbId}" | "episode:{showTmdbId}:{season}:{ep}" */
  id: string;
  type: 'movie' | 'episode';
  /** For episodes this is the SHOW's TMDB ID */
  tmdbId: number;
  imdbId?: string;
  /** For episodes this is the show title */
  title: string;
  year?: number;
  /** Episode-specific */
  season?: number;
  episode?: number;
  watchedAt: string;    // ISO timestamp
  syncedToTrakt: boolean;
}

interface WatchedContextType {
  watchedItems:           LocalWatchedItem[];
  isMovieWatched:         (tmdbId: number) => boolean;
  isEpisodeWatched:       (showTmdbId: number, season: number, episode: number) => boolean;
  isSeriesWatched:        (showTmdbId: number) => boolean;
  toggleMovieWatched:     (tmdbId: number, imdbId: string | undefined, title: string, year?: number) => Promise<void>;
  toggleEpisodeWatched:   (showTmdbId: number, showImdbId: string | undefined, showTitle: string, season: number, episode: number) => Promise<void>;
  markAllEpisodesWatched: (showTmdbId: number, showImdbId: string | undefined, showTitle: string, seasons: Array<{ season_number: number; episode_count: number }>) => Promise<void>;
  unmarkSeriesWatched:    (showTmdbId: number) => void;
  isSyncing: boolean;
}

// ── Context ───────────────────────────────────────────────────────────────────

const WatchedContext = createContext<WatchedContextType>({
  watchedItems:           [],
  isMovieWatched:         () => false,
  isEpisodeWatched:       () => false,
  isSeriesWatched:        () => false,
  toggleMovieWatched:     async () => {},
  toggleEpisodeWatched:   async () => {},
  markAllEpisodesWatched: async () => {},
  unmarkSeriesWatched:    () => {},
  isSyncing:              false,
});

// ── Provider ──────────────────────────────────────────────────────────────────

export const WatchedProvider = ({ children }: { children: React.ReactNode }) => {
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const { isConnected: traktConnected } = useTrakt();
  const { clearProgress, clearProgressIndexEntry } = useWatchProgress();

  const [items,     setItems]     = useState<LocalWatchedItem[]>([]);
  const [isSyncing, setIsSyncing] = useState(false);

  // Keep a ref so the deferred-sync effect always reads the latest items
  // without adding `items` to its dependency array (which would re-trigger on
  // every watched action, not just on Trakt reconnect).
  const itemsRef = useRef(items);
  useEffect(() => { itemsRef.current = items; }, [items]);

  const prevTraktConnected = useRef(traktConnected);

  // ── Persistence ───────────────────────────────────────────────────────────

  useEffect(() => {
    Storage.getItem(STORAGE_KEY).then(raw => {
      if (raw) {
        try { setItems(JSON.parse(raw)); } catch {}
      }
    });
  }, []);

  const persist = useCallback((updated: LocalWatchedItem[]) => {
    setItems(updated);
    Storage.setItem(STORAGE_KEY, JSON.stringify(updated));
  }, []);

  const activeProfileId = activeProfile?.id ?? null;
  const buildProfileHeaders = useCallback(async () => (
    buildAuthHeaders(user, { profileId: activeProfileId })
  ), [activeProfileId, user]);

  // ── Auth headers ──────────────────────────────────────────────────────────

  // ── Trakt sync helpers ────────────────────────────────────────────────────

  /** Build Trakt /sync/history body from a list of local items */
  const buildSyncPayload = (pending: LocalWatchedItem[]) => {
    const movies = pending
      .filter(i => i.type === 'movie')
      .map(i => ({
        title:      i.title,
        year:       i.year,
        ids:        { tmdb: i.tmdbId },
        watched_at: i.watchedAt,
      }));

    // Group episodes by show TMDB ID for the Trakt `shows` format
    const byShow: Record<number, LocalWatchedItem[]> = {};
    for (const ep of pending.filter(i => i.type === 'episode')) {
      if (!byShow[ep.tmdbId]) byShow[ep.tmdbId] = [];
      byShow[ep.tmdbId].push(ep);
    }

    const shows = Object.entries(byShow).map(([tmdbId, eps]) => {
      const bySeason: Record<number, LocalWatchedItem[]> = {};
      for (const ep of eps) {
        const s = ep.season!;
        if (!bySeason[s]) bySeason[s] = [];
        bySeason[s].push(ep);
      }
      return {
        title: eps[0].title,
        ids:   { tmdb: Number(tmdbId) },
        seasons: Object.entries(bySeason).map(([seasonNum, seasonEps]) => ({
          number:   Number(seasonNum),
          episodes: seasonEps.map(ep => ({
            number:     ep.episode!,
            watched_at: ep.watchedAt,
          })),
        })),
      };
    });

    return { movies, shows };
  };

  /** POST pending items to Trakt and mark them synced on success */
  const syncToTrakt = useCallback(async (currentItems: LocalWatchedItem[]) => {
    if (!user || !activeProfileId || !traktConnected) return;
    const pending = currentItems.filter(i => !i.syncedToTrakt);
    if (pending.length === 0) return;

    setIsSyncing(true);
    try {
      const res = await fetch(`${API_BASE}/trakt/sync/watched`, {
        method:  'POST',
        headers: await buildProfileHeaders(),
        body:    JSON.stringify(buildSyncPayload(pending)),
      });
      if (res.ok) {
        const syncedIds = new Set(pending.map(i => i.id));
        persist(currentItems.map(i =>
          syncedIds.has(i.id) ? { ...i, syncedToTrakt: true } : i,
        ));
      }
    } catch {}
    finally { setIsSyncing(false); }
  }, [activeProfileId, buildProfileHeaders, user, traktConnected, persist]); // eslint-disable-line react-hooks/exhaustive-deps

  // Deferred sync: fires once when Trakt goes from disconnected → connected
  useEffect(() => {
    if (traktConnected && !prevTraktConnected.current && user) {
      syncToTrakt(itemsRef.current);
    }
    prevTraktConnected.current = traktConnected;
  }, [traktConnected, user]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Queries ───────────────────────────────────────────────────────────────

  const isMovieWatched = useCallback(
    (tmdbId: number) => items.some(i => i.type === 'movie' && i.tmdbId === tmdbId),
    [items],
  );

  const isEpisodeWatched = useCallback(
    (showTmdbId: number, season: number, episode: number) =>
      items.some(i =>
        i.type === 'episode' &&
        i.tmdbId === showTmdbId &&
        i.season === season &&
        i.episode === episode,
      ),
    [items],
  );

  const isSeriesWatched = useCallback(
    (showTmdbId: number) => items.some(i => i.type === 'episode' && i.tmdbId === showTmdbId),
    [items],
  );

  // ── Actions ───────────────────────────────────────────────────────────────

  const toggleMovieWatched = useCallback(async (
    tmdbId: number, imdbId: string | undefined, title: string, year?: number,
  ) => {
    const id = `movie:${tmdbId}`;
    if (items.some(i => i.id === id)) {
      // Un-watch: remove locally (Trakt doesn't support deleting individual history
      // entries without a Trakt history ID, so we only remove locally)
      persist(items.filter(i => i.id !== id));
      return;
    }

    const watchedAt = new Date().toISOString();
    let syncedToTrakt = false;

    if (traktConnected && user && activeProfileId) {
      try {
        const res = await fetch(`${API_BASE}/trakt/sync/watched`, {
          method:  'POST',
          headers: await buildProfileHeaders(),
          body:    JSON.stringify({
            movies: [{ title, year, ids: { tmdb: tmdbId }, watched_at: watchedAt }],
            shows:  [],
          }),
        });
        syncedToTrakt = res.ok;
      } catch {}
    }

    persist([...items, { id, type: 'movie', tmdbId, imdbId, title, year, watchedAt, syncedToTrakt }]);
    clearProgress(movieProgressKey(tmdbId));
    clearProgressIndexEntry(movieProgressKey(tmdbId));
  }, [activeProfileId, buildProfileHeaders, clearProgress, items, traktConnected, user, persist]);

  const toggleEpisodeWatched = useCallback(async (
    showTmdbId: number, showImdbId: string | undefined, showTitle: string,
    season: number, episode: number,
  ) => {
    const id = `episode:${showTmdbId}:${season}:${episode}`;
    if (items.some(i => i.id === id)) {
      persist(items.filter(i => i.id !== id));
      return;
    }

    const watchedAt = new Date().toISOString();
    let syncedToTrakt = false;

    if (traktConnected && user && activeProfileId) {
      try {
        const res = await fetch(`${API_BASE}/trakt/sync/watched`, {
          method:  'POST',
          headers: await buildProfileHeaders(),
          body:    JSON.stringify({
            movies: [],
            shows: [{
              title:   showTitle,
              ids:     { tmdb: showTmdbId },
              seasons: [{ number: season, episodes: [{ number: episode, watched_at: watchedAt }] }],
            }],
          }),
        });
        syncedToTrakt = res.ok;
      } catch {}
    }

    persist([...items, {
      id, type: 'episode', tmdbId: showTmdbId, imdbId: showImdbId,
      title: showTitle, season, episode, watchedAt, syncedToTrakt,
    }]);
    clearProgress(episodeProgressKey(showTmdbId, season, episode));
    clearProgressIndexEntry(episodeProgressKey(showTmdbId, season, episode));
  }, [activeProfileId, buildProfileHeaders, clearProgress, items, traktConnected, user, persist]);

  const unmarkSeriesWatched = useCallback((showTmdbId: number) => {
    persist(items.filter(i => !(i.type === 'episode' && i.tmdbId === showTmdbId)));
  }, [items, persist]);

  /** Mark every episode in the provided seasons list as watched in one batch */
  const markAllEpisodesWatched = useCallback(async (
    showTmdbId:  number,
    showImdbId:  string | undefined,
    showTitle:   string,
    seasons:     Array<{ season_number: number; episode_count: number }>,
  ) => {
    const watchedAt = new Date().toISOString();

    // Build flat list of all episode items not already watched
    const newItems: LocalWatchedItem[] = [];
    for (const s of seasons) {
      for (let ep = 1; ep <= s.episode_count; ep++) {
        const id = `episode:${showTmdbId}:${s.season_number}:${ep}`;
        if (!items.some(i => i.id === id)) {
          newItems.push({
            id, type: 'episode', tmdbId: showTmdbId, imdbId: showImdbId,
            title: showTitle, season: s.season_number, episode: ep,
            watchedAt, syncedToTrakt: false,
          });
        }
      }
    }

    if (newItems.length === 0) return;

    let synced = false;
    if (traktConnected && user && activeProfileId) {
      try {
        const bySeason: Record<number, number[]> = {};
        for (const item of newItems) {
          if (!bySeason[item.season!]) bySeason[item.season!] = [];
          bySeason[item.season!].push(item.episode!);
        }
        const res = await fetch(`${API_BASE}/trakt/sync/watched`, {
          method:  'POST',
          headers: await buildProfileHeaders(),
          body:    JSON.stringify({
            movies: [],
            shows: [{
              title:   showTitle,
              ids:     { tmdb: showTmdbId },
              seasons: Object.entries(bySeason).map(([seasonNum, eps]) => ({
                number:   Number(seasonNum),
                episodes: eps.map(n => ({ number: n, watched_at: watchedAt })),
              })),
            }],
          }),
        });
        synced = res.ok;
      } catch {}
    }

    persist([
      ...items,
      ...newItems.map(i => ({ ...i, syncedToTrakt: synced })),
    ]);
    for (const item of newItems) {
      clearProgress(episodeProgressKey(showTmdbId, item.season!, item.episode!));
      clearProgressIndexEntry(episodeProgressKey(showTmdbId, item.season!, item.episode!));
    }
  }, [activeProfileId, buildProfileHeaders, clearProgress, items, traktConnected, user, persist]);

  const contextValue = useMemo(() => ({
    watchedItems: items,
    isMovieWatched,
    isEpisodeWatched,
    isSeriesWatched,
    toggleMovieWatched,
    toggleEpisodeWatched,
    markAllEpisodesWatched,
    unmarkSeriesWatched,
    isSyncing,
  }), [
    items, isSyncing,
    isMovieWatched, isEpisodeWatched, isSeriesWatched,
    toggleMovieWatched, toggleEpisodeWatched, markAllEpisodesWatched, unmarkSeriesWatched,
  ]);

  return (
    <WatchedContext.Provider value={contextValue}>
      {children}
    </WatchedContext.Provider>
  );
};

export const useWatched = () => useContext(WatchedContext);
