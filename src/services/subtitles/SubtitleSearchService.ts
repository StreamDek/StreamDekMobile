// ─── Subtitle Search & Auto-Selection Service ────────────────────────────────
//
// Wraps the provider + cache into a single searchAndRank() call that:
//   1. Returns cached results immediately if fresh
//   2. Hits the provider (non-blocking from caller's perspective via async)
//   3. Ranks results using a multi-factor scoring function
//
// Ranking factors (highest weight first):
//   1. Language preference order  — primary selector; unranked languages are last
//   2. Forced flag                — boosted or penalised per user preference
//   3. HI (Hearing Impaired) flag — boosted or penalised per user preference
//   4. Release name similarity    — rewards subtitles that match the active stream's
//                                   filename/title (word-overlap Jaccard score)
//   5. Position in API response   — OpenSubtitles returns by relevance/downloads,
//                                   so earlier = more downloaded (tiebreaker only)

import { SubtitleCacheService } from './SubtitleCacheService';
import { OpenSubtitlesStremioProvider, DEFAULT_OS_ADDON_URL } from './OpenSubtitlesStremioProvider';
import { SubtitleResult, SubtitleSearchParams, SubtitleLanguageCode } from './SubtitleProvider';

// ── Ranking configuration ────────────────────────────────────────────────────

export interface SubtitleRankingOptions {
  /** Ordered list of preferred ISO 639-2/B language codes (first = most preferred) */
  languageOrder: SubtitleLanguageCode[];
  /** If true, boost forced subtitles; if false, penalise them */
  preferForced: boolean;
  /** If true, boost HI subtitles; if false, penalise them */
  preferHI: boolean;
  /**
   * Optional: active stream release name or filename to compute release-name
   * similarity against. Boosts subtitles whose release name closely matches.
   */
  streamReleaseName?: string | null;
}

// ── Score weights ────────────────────────────────────────────────────────────

const WEIGHT_LANGUAGE_BASE = 1_000; // Points per position in languageOrder
const WEIGHT_LANGUAGE_STEP = 100;   // Each position costs this many points
const WEIGHT_FORCED_PREF   = 200;   // Bonus/penalty when forced matches preference
const WEIGHT_HI_PREF       = 150;   // Bonus/penalty when HI matches preference
const WEIGHT_RELEASE_SIM   = 300;   // Maximum bonus for a perfect release-name match
const WEIGHT_POSITION      = 10;    // Bonus per position closer to start of API results

// ── Release name similarity (word-overlap Jaccard) ──────────────────────────

/**
 * Tokenise a release name into a set of meaningful words.
 * Strips year/resolution tokens that appear in both stream and subtitle names
 * to avoid false similarity on those alone.
 */
function tokenise(text: string): Set<string> {
  return new Set(
    text
      .toLowerCase()
      // Replace separators (dots, underscores, dashes) with spaces
      .replace(/[._\-]+/g, ' ')
      // Remove common non-discriminative tokens: year, resolution, codec tags
      .replace(/\b(19|20)\d{2}\b/g, '')
      .replace(/\b(720p?|1080p?|2160p?|4k|hdr|sdr|hevc|x264|x265|h264|h265|avc|aac|ac3|dts|bluray|blu-ray|bdrip|brrip|web-?dl|webrip|hdtv|proper|repack|extended|theatrical)\b/gi, '')
      .split(/\s+/)
      .filter(token => token.length >= 3),
  );
}

/**
 * Jaccard similarity between two token sets: |A ∩ B| / |A ∪ B|.
 * Returns 0–1 where 1 is a perfect match.
 */
function jaccardSimilarity(a: Set<string>, b: Set<string>): number {
  if (a.size === 0 || b.size === 0) return 0;
  let intersection = 0;
  for (const token of a) {
    if (b.has(token)) intersection++;
  }
  const union = a.size + b.size - intersection;
  return union === 0 ? 0 : intersection / union;
}

// ── Scoring function ─────────────────────────────────────────────────────────

/**
 * Compute a numeric score for a subtitle result. Higher = better.
 * @param result      The subtitle to score
 * @param index       Its 0-based position in the API result array
 * @param totalCount  Total number of results (for position normalisation)
 * @param options     User's ranking preferences
 * @param streamTokens Tokenised stream release name (pre-computed by caller)
 */
function scoreSubtitle(
  result: SubtitleResult,
  index: number,
  totalCount: number,
  options: SubtitleRankingOptions,
  streamTokens: Set<string>,
): number {
  let score = 0;

  // 1. Language preference
  const langIdx = options.languageOrder.indexOf(result.lang);
  if (langIdx >= 0) {
    // First language gets WEIGHT_LANGUAGE_BASE, each subsequent costs WEIGHT_LANGUAGE_STEP
    score += Math.max(0, WEIGHT_LANGUAGE_BASE - langIdx * WEIGHT_LANGUAGE_STEP);
  }
  // Languages not in the preference list score 0 — they'll appear after all
  // preferred languages but are still accessible via manual selection.

  // 2. Forced flag
  const isForced = result.isForced === true;
  if (options.preferForced) {
    score += isForced ? WEIGHT_FORCED_PREF : 0;
  } else {
    // Penalise forced subs when user doesn't prefer them (they're incomplete)
    score -= isForced ? WEIGHT_FORCED_PREF / 2 : 0;
  }

  // 3. HI (Hearing Impaired) flag
  const isHI = result.isHI === true;
  if (options.preferHI) {
    score += isHI ? WEIGHT_HI_PREF : 0;
  } else {
    score -= isHI ? WEIGHT_HI_PREF / 2 : 0;
  }

  // 4. Release-name similarity to active stream
  if (streamTokens.size > 0 && result.releaseName) {
    const subTokens = tokenise(result.releaseName);
    const similarity = jaccardSimilarity(streamTokens, subTokens);
    score += Math.round(similarity * WEIGHT_RELEASE_SIM);
  }

  // 5. Position bonus (OS already sorts by downloads/relevance)
  if (totalCount > 1) {
    const positionScore = ((totalCount - 1 - index) / (totalCount - 1)) * WEIGHT_POSITION;
    score += Math.round(positionScore);
  }

  return score;
}

// ── Stremio video ID builder (mirrors provider logic, needed for cache key) ──

function buildVideoId(params: SubtitleSearchParams): string | null {
  if (!params.imdbId?.startsWith('tt')) return null;
  if (params.type === 'series') {
    if (!params.season || !params.episode) return null;
    return `${params.imdbId}:${params.season}:${params.episode}`;
  }
  return params.imdbId;
}

// ── Main exported function ───────────────────────────────────────────────────

export interface SubtitleSearchResult {
  /** Ranked subtitle list — best match first */
  results: SubtitleResult[];
  /** True if the results came from the in-session cache (no network request) */
  fromCache: boolean;
}

/**
 * Fetch and rank subtitles for a piece of media.
 *
 * - Returns cached results instantly if available and fresh.
 * - Otherwise fetches from the provider (network, ~1–3 s) and stores in cache.
 * - Results are ranked according to the user's language preferences and flags.
 */
export async function searchAndRankSubtitles(
  params: SubtitleSearchParams,
  options: SubtitleRankingOptions,
  addonBaseUrl: string = DEFAULT_OS_ADDON_URL,
): Promise<SubtitleSearchResult> {
  const videoId = buildVideoId(params);

  // ── 1. Check cache ──────────────────────────────────────────────────────
  if (videoId) {
    const cached = await SubtitleCacheService.getResults(videoId);
    if (cached) {
      return {
        results: rankResults(cached, options),
        fromCache: true,
      };
    }
  }

  // ── 2. Fetch from provider ──────────────────────────────────────────────
  const provider = new OpenSubtitlesStremioProvider(addonBaseUrl);
  const raw = await provider.search(params);

  // ── 3. Store in cache (fire-and-forget, failure is non-fatal) ──────────
  if (videoId && raw.length > 0) {
    void SubtitleCacheService.setResults(videoId, raw);
  }

  // ── 4. Rank and return ──────────────────────────────────────────────────
  return {
    results: rankResults(raw, options),
    fromCache: false,
  };
}

/** Sort a list of SubtitleResult by score (highest first) */
function rankResults(
  results: SubtitleResult[],
  options: SubtitleRankingOptions,
): SubtitleResult[] {
  const streamTokens = options.streamReleaseName
    ? tokenise(options.streamReleaseName)
    : new Set<string>();

  const total = results.length;
  return [...results]
    .map((result, index) => ({
      result,
      score: scoreSubtitle(result, index, total, options, streamTokens),
    }))
    .sort((a, b) => b.score - a.score)
    .map(({ result }) => result);
}

/**
 * Pick the single best subtitle from an already-ranked list for auto-load.
 * Returns null if no suitable candidate exists.
 *
 * For auto-load we additionally require:
 *   - The subtitle's language must be in the user's preference list
 *   - Forced subtitles are only auto-loaded when the user explicitly prefers them
 */
export function pickBestSubtitle(
  rankedResults: SubtitleResult[],
  options: Pick<SubtitleRankingOptions, 'languageOrder' | 'preferForced'>,
): SubtitleResult | null {
  for (const result of rankedResults) {
    // Must be in the preferred language list
    if (!options.languageOrder.includes(result.lang)) continue;

    // Skip forced subtitles unless the user prefers them
    if (result.isForced && !options.preferForced) continue;

    return result;
  }
  return null;
}
