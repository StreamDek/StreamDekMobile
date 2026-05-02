// ─── OpenSubtitles V3 via Stremio Addon Protocol ─────────────────────────────
//
// The OpenSubtitles V3 Stremio addon exposes subtitles as a plain HTTP resource:
//   GET https://opensubtitles-v3.strem.io/subtitles/{type}/{videoId}.json
//
// No API key or auth token is required — the addon handles all auth server-side.
//
// Video ID format:
//   Movie:   {imdbId}              → e.g. tt0468569.json
//   Episode: {imdbId}:{s}:{e}     → e.g. tt0944947:1:1.json
//
// Response shape:
//   { "subtitles": [{ "id": "...", "url": "...", "lang": "eng", "m": "..." }] }

import {
  languageCodeToName,
  SubtitleProvider,
  SubtitleResult,
  SubtitleSearchParams,
} from './SubtitleProvider';

/** Default public Stremio OpenSubtitles V3 addon endpoint */
export const DEFAULT_OS_ADDON_URL = 'https://opensubtitles-v3.strem.io';

/** Network timeout for subtitle search requests (ms) */
const SEARCH_TIMEOUT_MS = 12_000;

/** Raw subtitle entry as returned by the Stremio addon */
interface StremioSubtitleEntry {
  id: string;
  url: string;
  /** ISO 639-2/B language code (e.g. 'eng', 'fre') */
  lang: string;
  /** Release name or match metadata (Stremio's 'm' field) */
  m?: string;
}

interface StremioSubtitlesResponse {
  subtitles: StremioSubtitleEntry[];
}

export class OpenSubtitlesStremioProvider implements SubtitleProvider {
  private readonly baseUrl: string;

  constructor(addonBaseUrl: string = DEFAULT_OS_ADDON_URL) {
    // Strip trailing slash so URL construction is always consistent
    this.baseUrl = addonBaseUrl.replace(/\/+$/, '');
  }

  async search(params: SubtitleSearchParams): Promise<SubtitleResult[]> {
    const videoId = this.buildVideoId(params);
    if (!videoId) {
      // Cannot search without an IMDB ID (and season/episode for series)
      return [];
    }

    const endpoint = `${this.baseUrl}/subtitles/${params.type}/${videoId}.json`;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), SEARCH_TIMEOUT_MS);

    try {
      const response = await fetch(endpoint, {
        signal: controller.signal,
        headers: {
          // Identify ourselves to the addon
          Accept: 'application/json',
        },
      });

      if (!response.ok) {
        console.warn(`[OpenSubtitlesStremio] HTTP ${response.status} for ${endpoint}`);
        return [];
      }

      const data: StremioSubtitlesResponse = await response.json();

      if (!Array.isArray(data?.subtitles)) {
        console.warn('[OpenSubtitlesStremio] Unexpected response shape:', data);
        return [];
      }

      const results = data.subtitles
        .filter(entry => entry?.id && entry?.url && entry?.lang)
        .map(entry => this.mapEntry(entry));

      console.log(`[OpenSubtitlesStremio] ${results.length} subtitles for ${videoId}`);
      return results;
    } catch (err: any) {
      if (err?.name === 'AbortError') {
        console.warn('[OpenSubtitlesStremio] Search timed out');
      } else {
        console.warn('[OpenSubtitlesStremio] Search failed:', err?.message);
      }
      return [];
    } finally {
      clearTimeout(timeoutId);
    }
  }

  /** Build the Stremio video ID from search params */
  private buildVideoId(params: SubtitleSearchParams): string | null {
    if (!params.imdbId || !params.imdbId.startsWith('tt')) {
      return null;
    }

    if (params.type === 'series') {
      const s = params.season;
      const e = params.episode;
      if (!s || !e || !Number.isFinite(s) || !Number.isFinite(e)) {
        return null;
      }
      return `${params.imdbId}:${s}:${e}`;
    }

    return params.imdbId;
  }

  /** Map a raw Stremio entry to our internal SubtitleResult shape */
  private mapEntry(entry: StremioSubtitleEntry): SubtitleResult {
    const lang = (entry.lang ?? '').toLowerCase().trim();
    const releaseName = entry.m?.trim() || undefined;
    const idLower = (entry.id ?? '').toLowerCase();
    const releaseNameLower = (releaseName ?? '').toLowerCase();

    // Heuristic detection of HI / Forced from the release name or id.
    // OpenSubtitles does not surface these as dedicated boolean fields via
    // the Stremio protocol, so we infer them from naming conventions.
    const isHI =
      idLower.includes('.hi.') ||
      idLower.endsWith('.hi') ||
      releaseNameLower.includes('[hi]') ||
      releaseNameLower.includes('hearing impaired') ||
      releaseNameLower.includes('(hi)');

    const isForced =
      idLower.includes('forced') ||
      releaseNameLower.includes('[forced]') ||
      releaseNameLower.includes('(forced)') ||
      releaseNameLower.includes('.forced.');

    return {
      id: entry.id,
      url: entry.url,
      lang,
      langDisplay: languageCodeToName(lang),
      releaseName,
      isHI,
      isForced,
      provider: 'opensubtitles',
    };
  }
}
