import { useCallback, useEffect, useState } from 'react';
import { API_BASE } from '../constants/api';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useTrakt } from '../context/TraktContext';
import { buildAuthHeaders } from '../utils/authHeaders';
import {
  readWatchlistItems,
  readWatchlistRemovalIds,
  watchlistItemMatchesId,
  writeWatchlistItems,
  writeWatchlistRemovalIds,
} from '../utils/watchlist';
import { getProfileStorageOwnerId } from '../utils/profileStorage';

export type WatchlistRemoveItem = {
  /** String tmdbId or any stable id used in local storage */
  id: string;
  title: string;
  year?: string | number;
  /** 'movie' | 'tv' */
  type: string;
  tmdbId?: number | null;
  /** True when the item was sourced from Trakt (requires API call to remove) */
  fromTrakt: boolean;
};

/**
 * Returns a `removeFromWatchlist` function that removes an item from both the
 * Trakt watchlist (if connected and the item came from Trakt) and the local
 * Storage watchlist (`streamdek_watchlist_{uid}`).
 */
export function useWatchlistRemove() {
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const { isConnected, refreshWatchlist } = useTrakt();
  const [watchlistRemovalIds, setWatchlistRemovalIds] = useState<string[]>([]);
  const storageOwnerId = getProfileStorageOwnerId(user?.uid, activeProfile?.id);
  const legacyOwnerId = user?.uid ?? null;

  useEffect(() => {
    if (!user) {
      setWatchlistRemovalIds([]);
      return;
    }
    readWatchlistRemovalIds(storageOwnerId, legacyOwnerId)
      .then(ids => setWatchlistRemovalIds(ids))
      .catch(() => {});
  }, [legacyOwnerId, storageOwnerId, user]);

  const removeFromWatchlist = useCallback(async (item: WatchlistRemoveItem) => {
    // ── Trakt removal ──────────────────────────────────────────────────────────
    if (isConnected && user && item.fromTrakt) {
      const entry = {
        title: item.title,
        year: parseInt(String(item.year ?? ''), 10) || undefined,
        ids: { tmdb: Number(item.tmdbId ?? item.id) },
      };
      const payload = item.type === 'movie'
        ? { movies: [entry], shows: [] }
        : { movies: [], shows: [entry] };
      await fetch(`${API_BASE}/trakt/sync/watchlist/remove`, {
        method: 'POST',
        headers: await buildAuthHeaders(user, { profileId: activeProfile?.id }),
        body: JSON.stringify(payload),
      }).catch(() => {});
      await refreshWatchlist().catch(() => {});
    }

    // ── Local Storage removal ──────────────────────────────────────────────────
    if (user) {
      const existing = await readWatchlistItems(storageOwnerId, legacyOwnerId);
      const updated = existing.filter((i: any) => !watchlistItemMatchesId(i, item.id));
      await writeWatchlistItems(storageOwnerId, updated);

      const nextRemovalIds = Array.from(new Set([...watchlistRemovalIds, String(item.id)]));
      setWatchlistRemovalIds(nextRemovalIds);
      await writeWatchlistRemovalIds(storageOwnerId, nextRemovalIds);
    }
  }, [isConnected, legacyOwnerId, refreshWatchlist, storageOwnerId, user, watchlistRemovalIds]);

  return { removeFromWatchlist };
}
