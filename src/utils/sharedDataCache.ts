type CacheEntry<T> = {
  value: T;
  expiresAt: number;
};

const sharedCache = new Map<string, CacheEntry<unknown>>();
const sharedInflight = new Map<string, Promise<unknown>>();

export function getSharedCachedValue<T>(key: string): T | null {
  const entry = sharedCache.get(key);
  if (!entry) return null;
  if (entry.expiresAt <= Date.now()) {
    sharedCache.delete(key);
    return null;
  }
  return entry.value as T;
}

export function primeSharedCachedValue<T>(key: string, value: T, ttlMs: number) {
  sharedCache.set(key, {
    value,
    expiresAt: Date.now() + ttlMs,
  });
}

export function invalidateSharedCache(prefix: string) {
  for (const key of sharedCache.keys()) {
    if (key.startsWith(prefix)) {
      sharedCache.delete(key);
    }
  }
  for (const key of sharedInflight.keys()) {
    if (key.startsWith(prefix)) {
      sharedInflight.delete(key);
    }
  }
}

export async function getSharedCachedAsync<T>(
  key: string,
  ttlMs: number,
  loader: () => Promise<T>,
): Promise<T> {
  const cached = getSharedCachedValue<T>(key);
  if (cached !== null) return cached;

  const inflight = sharedInflight.get(key);
  if (inflight) return inflight as Promise<T>;

  const request = (async () => {
    try {
      const value = await loader();
      primeSharedCachedValue(key, value, ttlMs);
      return value;
    } finally {
      sharedInflight.delete(key);
    }
  })();

  sharedInflight.set(key, request);
  return request;
}
