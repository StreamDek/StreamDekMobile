import { useState, useCallback, useEffect, useMemo } from 'react';
import { Share } from 'react-native';
import { API_BASE } from '../constants/api';
import { useAuth } from '../context/AuthContext';
import { useProfile } from '../context/ProfileContext';
import { useTrakt } from '../context/TraktContext';
import { useWatched } from '../context/WatchedContext';
import { useLanguage } from '../context/LanguageContext';
import type { ActionSheetAction } from '../components/ActionSheet';
import { buildAuthHeaders } from '../utils/authHeaders';
import {
  mergeWatchlistItems,
  normalizeWatchlistItem,
  readWatchlistItems,
  readWatchlistRemovalIds,
  watchlistItemMatchesId,
  writeWatchlistItems,
  writeWatchlistRemovalIds,
} from '../utils/watchlist';
import { getProfileStorageOwnerId } from '../utils/profileStorage';



interface Options {
  navigation: any;
  /**
   * Override the watchlist for "in watchlist" checks — pass `allItems` from a
   * screen that already manages its own watchlist state (e.g. WatchlistScreen).
   * When omitted, the hook loads from local Storage + Trakt context.
   */
  watchlistOverride?: any[];
  /**
   * Custom removal handler for screens that need Trakt-aware removal
   * (e.g. WatchlistScreen). When provided, replaces the hook's default
   * toggleWatchlist for the "Remove from watchlist" action.
   */
  onWatchlistRemove?: (item: any) => void;
  onWatchedChange?: () => void;
}

export function useLongPressActions({ navigation, watchlistOverride, onWatchlistRemove, onWatchedChange }: Options) {
  const { user } = useAuth();
  const { activeProfile } = useProfile();
  const { watchlist: traktWatchlist, isConnected, refreshWatchlist } = useTrakt();
  const {
    isMovieWatched, isSeriesWatched,
    toggleMovieWatched, unmarkSeriesWatched,
    markAllEpisodesWatched,
  } = useWatched();
  const { t } = useLanguage();

  const [longPressItem,          setLongPressItem]          = useState<any | null>(null);
  const [seriesWatchConfirmItem, setSeriesWatchConfirmItem] = useState<any | null>(null);
  const [localWatchlist,         setLocalWatchlist]         = useState<any[]>([]);
  const [watchlistRemovalIds,    setWatchlistRemovalIds]    = useState<string[]>([]);
  const storageOwnerId = getProfileStorageOwnerId(user?.uid, activeProfile?.id);
  const legacyOwnerId = user?.uid ?? null;

  // Load local watchlist once so we can show correct "add/remove" label
  useEffect(() => {
    if (watchlistOverride !== undefined) return;
    readWatchlistItems(storageOwnerId, legacyOwnerId)
      .then(items => setLocalWatchlist(items))
      .catch(() => {});
  }, [legacyOwnerId, storageOwnerId, watchlistOverride]);

  useEffect(() => {
    if (watchlistOverride !== undefined) return;
    readWatchlistRemovalIds(storageOwnerId, legacyOwnerId)
      .then(ids => setWatchlistRemovalIds(ids))
      .catch(() => {});
  }, [legacyOwnerId, storageOwnerId, watchlistOverride]);

  const combinedWatchlist = useMemo(() => {
    if (watchlistOverride !== undefined) return watchlistOverride;
    const removedIds = new Set(watchlistRemovalIds);
    const traktMapped = traktWatchlist.map((i: any) => ({
      ...i,
      id: i.tmdbId != null ? String(i.tmdbId) : i.id,
    })).filter((i: any) => !removedIds.has(String(i.id)));
    const localFiltered = localWatchlist.filter((i: any) => !removedIds.has(String(i.id)));
    return mergeWatchlistItems(traktMapped, localFiltered);
  }, [watchlistOverride, traktWatchlist, localWatchlist, watchlistRemovalIds]);

  const handleShare = useCallback((item: any) => {
    const year = item.year ? ` (${item.year})` : '';
    Share.share({ message: `Check out ${item.title}${year} on StreamDek!` });
  }, []);

  const toggleWatchlist = useCallback(async (item: any) => {
    const itemId = String(item.id);
    const current = await readWatchlistItems(storageOwnerId, legacyOwnerId);
    const exists = current.some((i: any) => watchlistItemMatchesId(i, itemId));
    const updated = exists
      ? current.filter((i: any) => !watchlistItemMatchesId(i, itemId))
      : [...current, normalizeWatchlistItem({
          id: itemId, tmdbId: Number(item.id), title: item.title, poster: item.poster,
          type: item.type, year: item.year, rating: item.rating,
        })];
    await writeWatchlistItems(storageOwnerId, updated);
    setLocalWatchlist(updated);
    if (exists) {
      const nextRemovalIds = Array.from(new Set([...watchlistRemovalIds, itemId]));
      setWatchlistRemovalIds(nextRemovalIds);
      await writeWatchlistRemovalIds(storageOwnerId, nextRemovalIds);
    } else {
      const nextRemovalIds = watchlistRemovalIds.filter(id => id !== itemId);
      setWatchlistRemovalIds(nextRemovalIds);
      await writeWatchlistRemovalIds(storageOwnerId, nextRemovalIds);
    }

    // Sync with Trakt if connected
    if (isConnected) {
      try {
        const endpoint = exists ? '/trakt/sync/watchlist/remove' : '/trakt/sync/watchlist/add';
        const entry = {
          title: item.title,
          year:  parseInt(String(item.year)) || undefined,
          ids:   { tmdb: Number(item.id) },
        };
        const payload = item.type !== 'tv'
          ? { movies: [entry], shows: [] }
          : { movies: [], shows: [entry] };
        await fetch(`${API_BASE}${endpoint}`, {
          method:  'POST',
          headers: await buildAuthHeaders(user, { profileId: activeProfile?.id }),
          body:    JSON.stringify(payload),
        });
        await refreshWatchlist();
      } catch {}
    }
  }, [activeProfile?.id, isConnected, legacyOwnerId, refreshWatchlist, storageOwnerId, user, watchlistRemovalIds]);

  const handleSeriesMarkWatched = useCallback(async (item: any) => {
    try {
      const res  = await fetch(`${API_BASE}/tmdb/details/tv/${item.id}`);
      const data = await res.json();
      const seasons = (data.seasons ?? []).filter((s: any) => s.season_number > 0);
      await markAllEpisodesWatched(Number(item.id), data.imdbId, item.title, seasons);
      onWatchedChange?.();
    } catch {}
  }, [markAllEpisodesWatched, onWatchedChange]);

  const buildActions = useCallback((item: any): ActionSheetAction[] => {
    if (!item) return [];
    const isMovie = item.type !== 'tv';
    const watched = isMovie ? isMovieWatched(Number(item.id)) : isSeriesWatched(Number(item.id));
    const inWl    = combinedWatchlist.some((i: any) => String(i.id) === String(item.id));

    return [
      // Mark as watched / Unwatch
      watched
        ? {
            label:   isMovie ? t('watched_unwatch') : t('card_unwatch_series'),
            icon:    'eye-off-outline' as const,
            variant: 'default' as const,
            onPress: () => {
              if (isMovie) toggleMovieWatched(Number(item.id), item.imdbId, item.title, item.year);
              else         unmarkSeriesWatched(Number(item.id));
              onWatchedChange?.();
            },
          }
        : {
            label:   t('watched_mark'),
            icon:    'checkmark-circle-outline' as const,
            variant: 'accent' as const,
            onPress: () => {
              if (isMovie) toggleMovieWatched(Number(item.id), item.imdbId, item.title, item.year);
              else         setSeriesWatchConfirmItem(item);
              if (isMovie) onWatchedChange?.();
            },
          },
      // Share
      {
        label:   t('card_share'),
        icon:    'share-outline' as const,
        variant: 'default' as const,
        onPress: () => handleShare(item),
      },
      // Watchlist add / remove
      inWl
        ? {
            label:   t('card_watchlist_remove'),
            icon:    'bookmark-outline' as const,
            variant: 'destructive' as const,
            onPress: () => {
              if (onWatchlistRemove) onWatchlistRemove(item);
              else toggleWatchlist(item);
            },
          }
        : {
            label:   t('card_watchlist_add'),
            icon:    'bookmark-outline' as const,
            variant: 'default' as const,
            onPress: () => toggleWatchlist(item),
          },
      // Cancel
      {
        label:   'Cancel',
        icon:    'close-outline' as const,
        variant: 'cancel' as const,
        onPress: () => {},
      },
    ];
  }, [
    combinedWatchlist, user, t,
    isMovieWatched, isSeriesWatched,
    toggleMovieWatched, unmarkSeriesWatched,
    handleShare, toggleWatchlist, onWatchlistRemove, onWatchedChange,
  ]);

  const handleLongPress = useCallback((item: any) => {
    setLongPressItem(item);
  }, []);

  return {
    longPressItem,
    setLongPressItem,
    handleLongPress,
    buildActions,
    seriesWatchConfirmItem,
    setSeriesWatchConfirmItem,
    handleSeriesMarkWatched,
  };
}
