// ─── Subtitle system shared types and provider interface ────────────────────
// All subtitle providers (OpenSubtitles, local, etc.) implement SubtitleProvider.
// The Stremio addon protocol is currently the only concrete implementation,
// but this interface keeps the rest of the system provider-agnostic.

/** ISO 639-2/B language code as returned by OpenSubtitles (e.g. 'eng', 'fre', 'spa') */
export type SubtitleLanguageCode = string;

/** A single subtitle result returned by a provider search */
export interface SubtitleResult {
  /** Provider-specific unique identifier (used as cache key and track label) */
  id: string;
  /** Direct download URL for the subtitle file */
  url: string;
  /** ISO 639-2/B language code, lowercase (e.g. 'eng') */
  lang: SubtitleLanguageCode;
  /** Human-readable language name (e.g. 'English') */
  langDisplay: string;
  /**
   * Release name or match hint from the provider (the "m" field from Stremio).
   * Used for release-name similarity scoring during auto-selection.
   */
  releaseName?: string;
  /** True if the subtitle is marked as Hearing Impaired */
  isHI?: boolean;
  /** True if this is a forced subtitle (foreign language lines only) */
  isForced?: boolean;
  /** Data source identifier */
  provider: 'opensubtitles';
}

/** Parameters required to search for subtitles */
export interface SubtitleSearchParams {
  /** Content type — drives the Stremio endpoint path */
  type: 'movie' | 'series';
  /** IMDB ID (required for OpenSubtitles Stremio addon), e.g. 'tt0468569' */
  imdbId?: string | null;
  /** Season number (required for series) */
  season?: number | null;
  /** Episode number (required for series) */
  episode?: number | null;
  /** Human-readable title — used for display only, not sent to API */
  title?: string | null;
  /** Release year — used for display only, not sent to API */
  year?: number | null;
}

/** Implemented by any subtitle source (provider-agnostic interface) */
export interface SubtitleProvider {
  search(params: SubtitleSearchParams): Promise<SubtitleResult[]>;
}

// ─── Language code → display name mapping ────────────────────────────────────
// ISO 639-2/B codes as used by OpenSubtitles / Stremio.

export const LANGUAGE_CODE_MAP: Record<string, string> = {
  eng: 'English',
  fre: 'French',
  spa: 'Spanish',
  ger: 'German',
  ita: 'Italian',
  por: 'Portuguese',
  dut: 'Dutch',
  pol: 'Polish',
  rus: 'Russian',
  chi: 'Chinese',
  zho: 'Chinese',
  jpn: 'Japanese',
  kor: 'Korean',
  ara: 'Arabic',
  tur: 'Turkish',
  swe: 'Swedish',
  nor: 'Norwegian',
  dan: 'Danish',
  fin: 'Finnish',
  cze: 'Czech',
  ces: 'Czech',
  hun: 'Hungarian',
  ron: 'Romanian',
  rum: 'Romanian',
  ukr: 'Ukrainian',
  hrv: 'Croatian',
  srp: 'Serbian',
  slk: 'Slovak',
  slv: 'Slovenian',
  bul: 'Bulgarian',
  heb: 'Hebrew',
  vie: 'Vietnamese',
  tha: 'Thai',
  ind: 'Indonesian',
  may: 'Malay',
  msa: 'Malay',
  per: 'Persian',
  fas: 'Persian',
  hin: 'Hindi',
  ben: 'Bengali',
  ell: 'Greek',
  cat: 'Catalan',
  lav: 'Latvian',
  lit: 'Lithuanian',
  est: 'Estonian',
};

/** Convert an ISO 639-2/B code to a human-readable language name */
export function languageCodeToName(code: string): string {
  const normalized = (code ?? '').toLowerCase().trim();
  return LANGUAGE_CODE_MAP[normalized] ?? code.toUpperCase();
}

/**
 * Ordered list of common languages for the language picker UI.
 * Each entry has a code (ISO 639-2/B) and display label.
 */
export const COMMON_SUBTITLE_LANGUAGES: { code: SubtitleLanguageCode; label: string }[] = [
  { code: 'eng', label: 'English' },
  { code: 'fre', label: 'French' },
  { code: 'spa', label: 'Spanish' },
  { code: 'ger', label: 'German' },
  { code: 'ita', label: 'Italian' },
  { code: 'por', label: 'Portuguese' },
  { code: 'dut', label: 'Dutch' },
  { code: 'pol', label: 'Polish' },
  { code: 'rus', label: 'Russian' },
  { code: 'chi', label: 'Chinese' },
  { code: 'jpn', label: 'Japanese' },
  { code: 'kor', label: 'Korean' },
  { code: 'ara', label: 'Arabic' },
  { code: 'tur', label: 'Turkish' },
  { code: 'swe', label: 'Swedish' },
  { code: 'nor', label: 'Norwegian' },
  { code: 'dan', label: 'Danish' },
  { code: 'fin', label: 'Finnish' },
  { code: 'cze', label: 'Czech' },
  { code: 'hun', label: 'Hungarian' },
  { code: 'ron', label: 'Romanian' },
  { code: 'ukr', label: 'Ukrainian' },
  { code: 'hrv', label: 'Croatian' },
];
