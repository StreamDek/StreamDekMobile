import { Storage } from './storage';

export type WatchlistItem = {
  id?: string | number;
  tmdbId?: number | string | null;
  type?: string;
  title?: string | null;
  year?: string | number;
  poster?: string | null;
  backdrop?: string | null;
  rating?: number | null;
  fromTrakt?: boolean;
  [key: string]: any;
};

export function watchlistStorageKey(ownerId: string | null | undefined): string {
  return ownerId ? `streamdek_watchlist_${ownerId}` : 'streamdek_watchlist';
}

export function watchlistRemovalStorageKey(ownerId: string | null | undefined): string {
  return ownerId ? `streamdek_watchlist_removed_${ownerId}` : 'streamdek_watchlist_removed';
}

export function watchlistItemId(item: WatchlistItem): string {
  const baseId = item.tmdbId ?? item.id ?? '';
  return String(baseId);
}

export function normalizeWatchlistItem(item: WatchlistItem, fromTrakt = false): WatchlistItem {
  const id = watchlistItemId(item);
  const tmdbId = item.tmdbId != null ? Number(item.tmdbId) : Number(id);

  return {
    ...item,
    id,
    tmdbId: Number.isFinite(tmdbId) ? tmdbId : null,
    fromTrakt: fromTrakt || item.fromTrakt === true,
  };
}

export function mergeWatchlistItems(traktItems: WatchlistItem[], localItems: WatchlistItem[]): WatchlistItem[] {
  const normalizedTrakt = traktItems.map(item => normalizeWatchlistItem(item, true));
  const traktIds = new Set(normalizedTrakt.map(item => watchlistItemId(item)));
  const normalizedLocal = localItems
    .map(item => normalizeWatchlistItem(item, false))
    .filter(item => !traktIds.has(watchlistItemId(item)));

  return [...normalizedTrakt, ...normalizedLocal];
}

export function watchlistItemMatchesId(item: WatchlistItem, id: string | number): boolean {
  const idStr = String(id);
  return String(item.id ?? '') === idStr || String(item.tmdbId ?? '') === idStr;
}

export function mediaItemIdentityKey<T extends {
  id?: string | number;
  tmdbId?: string | number | null;
  type?: string | null;
  title?: string | null;
  year?: string | number | null;
}>(item: T, index?: number): string {
  const type = String(item?.type ?? 'unknown');
  const baseId = item?.tmdbId ?? item?.id;
  if (baseId != null && String(baseId).length > 0) {
    return `${type}:${String(baseId)}`;
  }

  const title = String(item?.title ?? '').trim().toLowerCase();
  const year = item?.year != null ? String(item.year) : '';
  if (title) {
    return `${type}:${title}:${year}`;
  }

  return `${type}:fallback:${index ?? 0}`;
}

export function mediaListItemKey<T extends {
  id?: string | number;
  tmdbId?: string | number | null;
  type?: string | null;
  title?: string | null;
  year?: string | number | null;
}>(item: T, index: number): string {
  return `${mediaItemIdentityKey(item, index)}:${index}`;
}

export function uniqueItemsById<T extends {
  id?: string | number;
  tmdbId?: string | number | null;
  type?: string | null;
  title?: string | null;
  year?: string | number | null;
}>(items: T[]): T[] {
  const seen = new Set<string>();
  const result: T[] = [];
  for (let index = 0; index < items.length; index += 1) {
    const item = items[index];
    const id = mediaItemIdentityKey(item, index);
    if (!id || seen.has(id)) continue;
    seen.add(id);
    result.push(item);
  }
  return result;
}

async function readJsonArray<T>(
  primaryKey: string,
  fallbackKey?: string | null,
): Promise<T[]> {
  const primaryRaw = await Storage.getItem(primaryKey).catch(() => null);
  const raw = primaryRaw ?? (fallbackKey && fallbackKey !== primaryKey
    ? await Storage.getItem(fallbackKey).catch(() => null)
    : null);
  if (!raw) return [];
  try {
    return JSON.parse(raw) as T[];
  } catch {
    return [];
  }
}

export async function readWatchlistItems(
  ownerId: string | null | undefined,
  fallbackOwnerId?: string | null,
): Promise<WatchlistItem[]> {
  return readJsonArray<WatchlistItem>(
    watchlistStorageKey(ownerId),
    fallbackOwnerId ? watchlistStorageKey(fallbackOwnerId) : null,
  );
}

export async function writeWatchlistItems(
  ownerId: string | null | undefined,
  items: WatchlistItem[],
): Promise<void> {
  await Storage.setItem(watchlistStorageKey(ownerId), JSON.stringify(items)).catch(() => {});
}

export async function readWatchlistRemovalIds(
  ownerId: string | null | undefined,
  fallbackOwnerId?: string | null,
): Promise<string[]> {
  const parsed = await readJsonArray<string | number>(
    watchlistRemovalStorageKey(ownerId),
    fallbackOwnerId ? watchlistRemovalStorageKey(fallbackOwnerId) : null,
  );
  return parsed.map(id => String(id));
}

export async function writeWatchlistRemovalIds(
  ownerId: string | null | undefined,
  ids: string[],
): Promise<void> {
  const deduped = Array.from(new Set(ids.map(id => String(id))));
  await Storage.setItem(watchlistRemovalStorageKey(ownerId), JSON.stringify(deduped)).catch(() => {});
}
