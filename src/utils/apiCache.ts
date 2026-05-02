import { Storage } from './storage';

const CACHE_PREFIX = 'api_cache_v1_';
const TTL_MS = 7 * 24 * 60 * 60 * 1000;      // 7 days
const STALE_MS = 6 * 24 * 60 * 60 * 1000;    // trigger background refresh after 6 days

interface CacheEntry<T> {
  data: T;
  timestamp: number;
}

function cacheKey(key: string): string {
  return CACHE_PREFIX + key.replace(/[^a-z0-9_\-/.]/gi, '_');
}

export async function getCached<T>(key: string): Promise<T | null> {
  try {
    const raw = await Storage.getItem(cacheKey(key));
    if (!raw) return null;
    const entry: CacheEntry<T> = JSON.parse(raw);
    if (Date.now() - entry.timestamp > TTL_MS) return null;
    return entry.data;
  } catch {
    return null;
  }
}

export async function setCached<T>(key: string, data: T): Promise<void> {
  try {
    await Storage.setItem(cacheKey(key), JSON.stringify({ data, timestamp: Date.now() } satisfies CacheEntry<T>));
  } catch { /* ignore */ }
}

export async function cachedFetch<T>(
  key: string,
  fetcher: () => Promise<T>,
): Promise<T> {
  try {
    const raw = await Storage.getItem(cacheKey(key));
    if (raw) {
      const entry: CacheEntry<T> = JSON.parse(raw);
      const age = Date.now() - entry.timestamp;
      if (age < TTL_MS) {
        if (age > STALE_MS) {
          // Silently refresh in background; don't await
          fetcher()
            .then(fresh => setCached(key, fresh))
            .catch(() => {});
        }
        return entry.data;
      }
    }
  } catch { /* cache read failure — fall through to live fetch */ }

  const data = await fetcher();
  void setCached(key, data);
  return data;
}

export async function invalidateCacheKey(key: string): Promise<void> {
  await Storage.removeItem(cacheKey(key));
}
