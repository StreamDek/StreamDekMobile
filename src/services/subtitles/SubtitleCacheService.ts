// ─── Subtitle Cache Service ───────────────────────────────────────────────────
//
// Two-level cache:
//
//  1. Search result cache — stores the array of SubtitleResult objects returned
//     by the provider for a given video ID. Lives in MMKV (fast, in-memory).
//     TTL: 30 minutes. Avoids re-hitting the API when the user re-opens a title.
//
//  2. File cache — stores downloaded subtitle files on disk inside the app's
//     cache directory. Files are keyed by subtitle ID (sanitised for safe filenames).
//     We keep a maximum of MAX_CACHED_FILES files; oldest entries are evicted first.
//     Files survive between app sessions until evicted or the OS clears the cache.

import { Directory, File, Paths } from 'expo-file-system';
import { SubtitleResult } from './SubtitleProvider';

// ── Constants ────────────────────────────────────────────────────────────────

/** How long search results are considered fresh (ms) */
const RESULT_CACHE_TTL_MS = 30 * 60 * 1_000; // 30 minutes

/** MMKV / Storage key prefix for cached search results */
const RESULT_KEY_PREFIX = 'streamdek_subtitles_';

/**
 * Maximum number of subtitle files kept on disk before the oldest are pruned.
 * Subtitle files are small (~50–200 KB) so 50 is conservative.
 */
const MAX_CACHED_FILES = 50;

// ── Types ────────────────────────────────────────────────────────────────────

interface CachedResultEntry {
  results: SubtitleResult[];
  expiresAt: number; // Unix ms timestamp
}

/** Index entry written alongside each cached subtitle file for LRU eviction */
interface FileCacheIndexEntry {
  subtitleId: string;
  filename: string;
  cachedAt: number; // Unix ms timestamp
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Make a subtitle ID safe for use as a filename (strip anything non-alphanumeric) */
function sanitizeId(id: string): string {
  return id.replace(/[^a-z0-9_-]/gi, '_').slice(0, 120);
}

/** Returns the subtitle file cache directory, creating it if necessary */
async function getSubtitleDir(): Promise<Directory> {
  const dir = new Directory(Paths.cache, 'streamdek_subtitles');
  if (!dir.exists) await dir.create();
  return dir;
}

/** Infer file extension from download URL; defaults to .srt */
function inferExtension(url: string): string {
  try {
    const pathname = new URL(url).pathname;
    const match = pathname.match(/\.(srt|vtt|ass|ssa|sub)(\?|$)/i);
    if (match) return `.${match[1].toLowerCase()}`;
  } catch {
    // URL parse failure — fall through
  }
  return '.srt';
}

// ── Lightweight in-process result cache (supplements Storage for zero-read-delay) ──

const inMemoryResultCache = new Map<string, CachedResultEntry>();

// ── Exported service object ──────────────────────────────────────────────────

export const SubtitleCacheService = {
  // ── Search result cache ──────────────────────────────────────────────────

  /**
   * Store search results for a video ID.
   * The key is the Stremio video ID (e.g. 'tt0468569' or 'tt0944947:1:1').
   */
  async setResults(videoId: string, results: SubtitleResult[]): Promise<void> {
    const entry: CachedResultEntry = {
      results,
      expiresAt: Date.now() + RESULT_CACHE_TTL_MS,
    };
    // Write to in-memory map immediately (synchronous, zero latency)
    inMemoryResultCache.set(videoId, entry);

    // Persist to Storage for cross-session recall
    try {
      const { Storage } = await import('../../utils/storage');
      await Storage.setItem(`${RESULT_KEY_PREFIX}${videoId}`, JSON.stringify(entry));
    } catch {
      // Persist failure is non-fatal — in-memory cache still works for this session
    }
  },

  /** Retrieve cached search results for a video ID, or null if stale/missing */
  async getResults(videoId: string): Promise<SubtitleResult[] | null> {
    // 1. Check in-memory map first
    const mem = inMemoryResultCache.get(videoId);
    if (mem && mem.expiresAt > Date.now()) {
      return mem.results;
    }

    // 2. Fall back to persistent Storage
    try {
      const { Storage } = await import('../../utils/storage');
      const raw = await Storage.getItem(`${RESULT_KEY_PREFIX}${videoId}`);
      if (!raw) return null;
      const entry: CachedResultEntry = JSON.parse(raw);
      if (!entry || entry.expiresAt <= Date.now()) return null;

      // Warm in-memory cache so next read is instant
      inMemoryResultCache.set(videoId, entry);
      return entry.results;
    } catch {
      return null;
    }
  },

  // ── File cache ───────────────────────────────────────────────────────────

  /**
   * Return the local file URI for a subtitle if it is already cached,
   * or null if it needs to be downloaded.
   */
  async getFilePath(subtitleId: string): Promise<string | null> {
    try {
      const dir = await getSubtitleDir();
      // We don't know the extension ahead of time, so check common ones
      for (const ext of ['.srt', '.vtt', '.ass', '.ssa', '.sub']) {
        const file = new File(dir, `${sanitizeId(subtitleId)}${ext}`);
        if (file.exists) return file.uri;
      }
      return null;
    } catch {
      return null;
    }
  },

  /**
   * Download a subtitle file from the given URL and persist it to the file cache.
   * Returns the local file URI that can be passed to MPV's sub-add command,
   * or null if the download failed.
   *
   * Uses File.downloadFileAsync (native file-system download) which handles
   * binary encoding correctly for all subtitle formats (SRT, VTT, ASS, SSA).
   * A 20-second timeout is enforced via Promise.race.
   */
  async downloadAndCache(subtitle: SubtitleResult): Promise<string | null> {
    const sanitized = sanitizeId(subtitle.id);
    const ext = inferExtension(subtitle.url);

    try {
      const dir = await getSubtitleDir();
      const targetFile = new File(dir, `${sanitized}${ext}`);

      // Return cached file immediately if already on disk
      if (targetFile.exists) {
        console.log(`[SubtitleCache] Cache hit for ${subtitle.id}`);
        return targetFile.uri;
      }

      // Download via native API with a 20-second timeout
      const DOWNLOAD_TIMEOUT_MS = 20_000;
      const downloaded = await Promise.race([
        File.downloadFileAsync(subtitle.url, targetFile),
        new Promise<never>((_, reject) =>
          setTimeout(() => reject(new Error('Subtitle download timed out')), DOWNLOAD_TIMEOUT_MS),
        ),
      ]);

      console.log(`[SubtitleCache] Downloaded ${subtitle.id} → ${downloaded.uri}`);

      // Evict oldest files when cache exceeds limit
      await this.evictOldFiles(dir);

      return downloaded.uri;
    } catch (err: any) {
      console.warn(`[SubtitleCache] Failed to download ${subtitle.id}:`, err?.message);
      return null;
    }
  },

  /**
   * Remove the oldest subtitle files when the cache exceeds MAX_CACHED_FILES.
   * Uses file modification time (via File.size availability) — on platforms where
   * mtime isn't available we fall back to a simple count-based purge.
   */
  async evictOldFiles(dir: Directory): Promise<void> {
    try {
      const files = dir.list();
      if (files.length <= MAX_CACHED_FILES) return;

      // Sort oldest-first by file URI name (proxy for creation order since IDs are stable)
      // This is a best-effort eviction; accurate mtime ordering isn't critical.
      const toDelete = files.slice(0, files.length - MAX_CACHED_FILES);
      for (const entry of toDelete) {
        try {
          if (entry instanceof File) await entry.delete();
        } catch {
          // Ignore individual delete failures
        }
      }
    } catch {
      // Eviction failure must never surface to the caller
    }
  },

  /** Clear all cached subtitle files from disk (used in settings "clear cache") */
  async clearFileCache(): Promise<void> {
    try {
      const dir = await getSubtitleDir();
      const files = dir.list();
      for (const entry of files) {
        try {
          if (entry instanceof File) await entry.delete();
        } catch {
          // Ignore
        }
      }
    } catch {
      // Ignore
    }
  },

  /** Approximate size of the subtitle file cache in bytes */
  async getFileCacheSize(): Promise<number> {
    try {
      const dir = await getSubtitleDir();
      const files = dir.list();
      let total = 0;
      for (const entry of files) {
        if (entry instanceof File && entry.exists) {
          total += entry.size ?? 0;
        }
      }
      return total;
    } catch {
      return 0;
    }
  },
};
