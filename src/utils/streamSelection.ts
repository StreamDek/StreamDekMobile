import { AddonStream } from '../context/AddonContext';
import type { AudioTrack } from 'expo-video';
import { parseStream } from './streamParser';

const QUALITY_BASE: Record<string, number> = {
  '4K': 100,
  'UHD': 100,
  '2160P': 100,
  '1080P': 86,
  'FHD': 86,
  '720P': 62,
  'HD': 62,
  '480P': 35,
  '360P': 18,
  'SD': 10,
};

// 1. LANGUAGE (Absolute Top Priority: Must understand the movie)
const LANGUAGE_BONUS = 180;
const ENGLISH_PRIORITY_BOOST = 320;
const NON_ENGLISH_ONLY_PENALTY = 260;
// 2. AVAILABILITY (Secondary Top Priority: Minimize buffering/waiting)
const DIRECT_URL_BONUS = 380;   // Direct links are extremely preferred
const CACHED_PROVIDER_BONUS = 400; // Debrid-cached streams are our key target
const CACHED_MULTI_PROVIDER_BONUS = 45;

// 2. SOURCE QUALITY (Medium Priority: What is the release type?)
const SOURCE_PRIORITY: Record<string, number> = {
  BLURAY: 12,
  'WEB-DL': 26,
  WEBRIP: 18,
  HDRIP: 6,
  HDTV: 3,
  DVD: -10,
  TELESYNC: -120, // Avoid
  CAM: -200,      // Avoid 
};

// 3. AUDIO PREFERENCE (High Priority: Optimized for mobile/compatibility)
const AUDIO_FORMAT_PRIORITY: { pattern: string; score: number }[] = [
  { pattern: 'AAC', score: 95 },     // Standard for mobile
  { pattern: 'FLAC', score: 100 },   // High quality lossless
  { pattern: 'MP3', score: 90 },     // Max compatibility
  { pattern: 'OPUS', score: 85 },    // Modern efficiency
  { pattern: 'EAC3', score: 60 },
  { pattern: 'E-AC3', score: 60 },
  { pattern: 'DD+', score: 60 },
  { pattern: 'AC3', score: 50 },
  { pattern: 'DOLBY DIGITAL', score: 50 },
  { pattern: 'DTS-HD MA', score: -140 }, // Hard to handle on mobile
  { pattern: 'DTS-HD', score: 20 },
  { pattern: 'TRUEHD', score: 20 },
  { pattern: 'TRUE-HD', score: 20 },
  { pattern: 'DTS:X', score: -160 },
  { pattern: 'DTS', score: -90 },
];

// 4. VIDEO CODEC (Medium Priority: H264 for compatibility)
const VIDEO_CODEC_PRIORITY: { pattern: string; score: number }[] = [
  { pattern: 'H.264', score: 120 },
  { pattern: 'H264', score: 120 },
  { pattern: 'AVC', score: 120 },
  { pattern: 'X264', score: 95 },
  { pattern: 'H.265', score: 30 },
  { pattern: 'H265', score: 30 },
  { pattern: 'HEVC', score: 30 },
  { pattern: 'X265', score: 20 },
  { pattern: 'AV1', score: -80 }, // Often resource heavy on mobile
  { pattern: 'VP9', score: -10 },
];

// 5. CONTAINER (Low-Medium Priority)
const CONTAINER_PRIORITY: { pattern: string; score: number }[] = [
  { pattern: '.MP4', score: 50 },
  { pattern: ' MP4 ', score: 50 },
  { pattern: '.MKV', score: 50 },
  { pattern: ' MKV ', score: 50 },
];
const BLACKLISTED_CODECS = ['VC-1', ' VC1 ', 'WMV', 'REALVIDEO', 'RV40', 'H.263 ', 'H263'];
const VALID_VIDEO_EXTENSIONS = new Set([
  'mp4', 'm4v', 'mkv', 'webm', 'mov', 'avi', 'ts', 'm2ts', 'mpg', 'mpeg', 'wmv', 'flv',
  'm3u8', 'mpd', 'ism', 'ismv',
]);
const INVALID_MEDIA_EXTENSIONS = new Set([
  'rar', 'zip', '7z', 'gz', 'tar',
  'srt', 'ass', 'ssa', 'sub', 'idx', 'vtt',
  'txt', 'nfo', 'sfv', 'jpg', 'jpeg', 'png', 'gif', 'webp',
]);

/**
 * Normalizes stream metadata into a single string for pattern matching.
 */
function streamText(stream: AddonStream): string {
  return [
    stream.name ?? '',
    stream.title ?? '',
    stream.description ?? '',
    stream.quality ?? '',
    stream.behaviorHints?.filename ?? '',
  ].join(' ').toUpperCase();
}

function extractExtension(value?: string | null): string | null {
  if (!value) return null;
  const cleaned = value.split('?')[0].split('#')[0].trim();
  const match = cleaned.match(/\.([a-z0-9]{2,5})$/i);
  return match ? match[1].toLowerCase() : null;
}

function candidateExtensions(stream: AddonStream): string[] {
  const parsed = parseStream(stream);
  const candidates = [
    stream.behaviorHints?.filename,
    stream.url,
    parsed.fileTitle,
    stream.description,
    stream.title,
    stream.name,
  ];

  return candidates
    .map(candidate => extractExtension(candidate))
    .filter((ext): ext is string => Boolean(ext));
}

function parseDurationMinutes(text: string): number | null {
  let totalMins = 0;
  let found = false;

  const minMatch = text.match(/\b(\d+)\s*(?:MIN|MINS|MINUTE|MINUTES)\b/);
  if (minMatch) {
    totalMins += parseInt(minMatch[1], 10);
    found = true;
  }

  const hrMatch = text.match(/\b(\d+)\s*(?:H|HR|HRS|HOUR|HOURS)\b/);
  if (hrMatch) {
    totalMins += parseInt(hrMatch[1], 10) * 60;
    found = true;
  }

  if (!found) {
    const emojiMatch = text.match(/(?:⏱️|⏱|⏳|DURATION:)\s*(?:(\d+)\s*H)?\s*(?:(\d+)\s*M)?/);
    if (emojiMatch && (emojiMatch[1] || emojiMatch[2])) {
      totalMins += parseInt(emojiMatch[1] || '0', 10) * 60;
      totalMins += parseInt(emojiMatch[2] || '0', 10);
      found = true;
    }
  }

  return found ? totalMins : null;
}

/**
 * Basic security/compatibility check.
 */
export function isLikelyPlayableVideoStream(stream: AddonStream): boolean {
  const text = streamText(stream);
  const duration = parseDurationMinutes(text);
  if (duration !== null && duration < 5) return false;

  const extensions = candidateExtensions(stream);
  if (extensions.length === 0) return true;

  if (extensions.some(ext => INVALID_MEDIA_EXTENSIONS.has(ext))) return false;
  return extensions.some(ext => VALID_VIDEO_EXTENSIONS.has(ext));
}

function hasHdr(text: string): boolean {
  return (
    text.includes('HDR10+') ||
    text.includes('HDR10') ||
    text.includes('DOLBY VISION') ||
    text.includes('DV ') ||
    text.includes(' HDR')
  );
}

/**
 * Scores based on resolution (4K > 1080p > 720p).
 */
function qualityScore(stream: AddonStream, text: string): number {
  if (text.includes('4K') || text.includes('UHD') || text.includes('2160')) {
    return QUALITY_BASE['4K'] + (hasHdr(text) ? 20 : 0);
  }
  if (text.includes('1080') || text.includes('FHD')) {
    return QUALITY_BASE['1080P'] + (hasHdr(text) ? 15 : 0);
  }
  if (text.includes('720') || text.includes(' HD ') || text.includes(' HD\n')) {
    return QUALITY_BASE['720P'];
  }
  if (text.includes('480')) return QUALITY_BASE['480P'];
  return QUALITY_BASE[(stream.quality ?? '').toUpperCase()] ?? 0;
}

function sourceScore(source: string | null): number {
  if (!source) return 0;
  return SOURCE_PRIORITY[source.toUpperCase()] ?? 0;
}

/**
 * Scores based on audio format (AAC/FLAC > AC3/DTS).
 */
function audioScore(text: string): number {
  let score = 0;

  for (const { pattern, score: codecScore } of AUDIO_FORMAT_PRIORITY) {
    if (text.includes(pattern)) {
      score += codecScore;
      break;
    }
  }

  const hasAtmos = text.includes('DOLBY ATMOS') || text.includes(' ATMOS');
  const hasCompatibleDolbyCore =
    text.includes('EAC3') ||
    text.includes('E-AC3') ||
    text.includes('DD+') ||
    text.includes('AC3') ||
    text.includes('DOLBY DIGITAL');

  if (hasAtmos) {
    score += hasCompatibleDolbyCore ? 8 : -80;
  }

  return score;
}

function videoCodecScore(text: string): number {
  for (const { pattern, score } of VIDEO_CODEC_PRIORITY) {
    if (text.includes(pattern)) return score;
  }
  return 0;
}

function containerScore(text: string): number {
  for (const { pattern, score } of CONTAINER_PRIORITY) {
    if (text.includes(pattern)) return score;
  }
  return 0;
}

function parseSizeGiB(size: string | number | null): number | null {
  if (size == null) return null;
  if (typeof size === 'number') return size / (1024 * 1024 * 1024);
  const match = String(size).toUpperCase().match(/([\d.]+)\s*(GB|MB|TB)/);
  if (!match) return null;

  const value = Number(match[1]);
  if (!Number.isFinite(value)) return null;

  if (match[2] === 'TB') return value * 1024;
  if (match[2] === 'MB') return value / 1024;
  return value;
}

function sizeScore(sizeGiB: number | null, quality: string | null): number {
  if (sizeGiB == null) return 0;

  if (sizeGiB > 35) return -180;
  if (sizeGiB > 25) return -120;
  if (sizeGiB > 15) return -60;
  if (sizeGiB > 8) return -20;

  if (sizeGiB < 0.25) return -100;
  if (sizeGiB < 0.5) return -65;

  if ((quality === '4K' || quality === 'UHD' || quality === '2160P') && sizeGiB < 4) return -40;
  if ((quality === '1080P' || quality === 'FHD') && sizeGiB < 1.2) return -20;

  return 0;
}

function seedScore(seeds: number | null, alreadyResolvable: boolean): number {
  if (alreadyResolvable || seeds == null) return 0;
  if (seeds >= 5000) return 120;
  if (seeds >= 1000) return 90;
  if (seeds >= 200) return 55;
  if (seeds >= 50) return 20;
  if (seeds < 5) return -60;
  return 0;
}

function languageScore(text: string, languages: string[]): number {
  const hasEnglish =
    languages.includes('English')
    || text.includes('ENGLISH')
    || /\bENG\b/.test(text);
  const hasMulti = languages.includes('Multi') || text.includes('MULTI') || text.includes('DUAL');

  if (hasEnglish) return LANGUAGE_BONUS + ENGLISH_PRIORITY_BOOST;
  if (hasMulti) return Math.round((LANGUAGE_BONUS + ENGLISH_PRIORITY_BOOST) * 0.82);

  const hasExplicitNonEnglishLanguage =
    languages.length > 0
    || /\bITA(?:LIAN)?\b|\bFRA(?:NCH)?\b|\bGER(?:MAN)?\b|\bSPA(?:NISH)?\b|\bESP\b|\bPOR(?:TUGUESE)?\b|\bRUS(?:SIAN)?\b|\bHIN(?:DI)?\b|\bKOR(?:EAN)?\b|\bCHI(?:NESE)?\b|\bJAP(?:ANESE)?\b/.test(text);
  if (hasExplicitNonEnglishLanguage) return -NON_ENGLISH_ONLY_PENALTY;

  if (text.includes('DUBBED') || text.includes('LATINO')) return -120;
  return 0;
}

function isUltraHd(text: string, parsedQuality: string | null): boolean {
  const quality = (parsedQuality ?? '').toUpperCase();
  return quality === '4K' || quality === 'UHD' || quality === '2160P'
    || text.includes('4K')
    || text.includes('UHD')
    || text.includes('2160');
}

function isFullHd(text: string, parsedQuality: string | null): boolean {
  const quality = (parsedQuality ?? '').toUpperCase();
  return quality === '1080P' || quality === 'FHD'
    || text.includes('1080')
    || text.includes('FHD');
}

function isHevcLike(text: string, codec: string | null): boolean {
  return codec === 'x265'
    || text.includes('HEVC')
    || text.includes('H265')
    || text.includes('H.265')
    || text.includes('X265');
}

function isTenBit(text: string): boolean {
  return /\b10[\s.\-]?BIT\b/.test(text);
}

function stabilityScore(
  text: string,
  parsed: ReturnType<typeof parseStream>,
  sizeGiB: number | null,
  preferQuickStart: boolean,
): number {
  let score = 0;

  const ultraHd = isUltraHd(text, parsed.quality);
  const fullHd = isFullHd(text, parsed.quality);
  const hevcLike = isHevcLike(text, parsed.codec);
  const hdrLike = Boolean(parsed.hdr) || hasHdr(text);
  const tenBit = isTenBit(text);
  const upscaled = text.includes('UPSCALED') || text.includes('UPSCALE');
  const remux = text.includes('REMUX');
  const hasAtmos = text.includes('ATMOS');

  if (fullHd) score += 45;
  if (ultraHd) score -= preferQuickStart ? 170 : 90;
  if (hevcLike) score -= preferQuickStart ? 115 : 55;
  if (hdrLike) score -= preferQuickStart ? 140 : 80;
  if (parsed.hdr === 'DV') score -= 140;
  if (parsed.hdr === 'HDR10+' || text.includes('HDR10+')) score -= 90;
  if (tenBit) score -= 120;
  if (upscaled) score -= 220;
  if (remux) score -= 110;
  if (hasAtmos && !text.includes('EAC3') && !text.includes('DD+') && !text.includes('AC3')) score -= 45;

  if (sizeGiB != null) {
    if (preferQuickStart && sizeGiB > 8) score -= 70;
    if (preferQuickStart && sizeGiB > 12) score -= 120;
    if (sizeGiB > 20) score -= 140;
  }

  if (preferQuickStart) {
    if (fullHd && !hevcLike && !hdrLike && !tenBit) score += 70;
    if (!ultraHd && parsed.source === 'WEB-DL') score += 35;
  }

  return score;
}

export interface StreamScoreOptions {
  preferQuickStart?: boolean;
  sessionPenalty?: number;
  preferredQuality?: 'best' | '4k' | '1080p' | '720p';
  /** Hard cap in GB — streams above this size are excluded (0 = unlimited) */
  maxFileSizeGB?: number;
}

function preferredQualityScore(text: string, parsedQuality: string | null, preferredQuality?: StreamScoreOptions['preferredQuality']): number {
  if (!preferredQuality || preferredQuality === 'best') return 0;

  const ultraHd = isUltraHd(text, parsedQuality);
  const fullHd = isFullHd(text, parsedQuality);
  const hd = (parsedQuality ?? '').toUpperCase() === '720P' || text.includes('720');

  if (preferredQuality === '4k') return ultraHd ? 260 : -80;
  if (preferredQuality === '1080p') {
    if (fullHd) return 260;
    if (ultraHd) return -220;
    return -50;
  }
  if (preferredQuality === '720p') {
    if (hd) return 240;
    if (ultraHd) return -280;
    if (fullHd) return -120;
  }
  return 0;
}

// Main scoring function - Higher score = Higher rank in the list
export function scoreStream(stream: AddonStream, options?: StreamScoreOptions): number {
  if (!isLikelyPlayableVideoStream(stream)) return -10000; // Immediate filter
  const text = streamText(stream);
  if (BLACKLISTED_CODECS.some(codec => text.includes(codec))) return -5000; // Block bad codecs

  const parsed = parseStream(stream);
  const sizeGiB = parseSizeGiB(parsed.size);

  // Hard file-size cap: exclude if known size exceeds the user limit
  const maxGB = options?.maxFileSizeGB ?? 0;
  if (maxGB > 0 && sizeGiB != null && sizeGiB > maxGB) return -10000;
  const alreadyResolvable = Boolean(stream.url || stream.cachedBy.length > 0);
  const preferQuickStart = options?.preferQuickStart ?? false;

  let score = 0;

  // A. CACHE STATUS (Highest Positive Impact)
  if (stream.cachedBy.length > 0) {
    score += CACHED_PROVIDER_BONUS + (stream.cachedBy.length - 1) * CACHED_MULTI_PROVIDER_BONUS;
  }
  if (stream.url) score += DIRECT_URL_BONUS;

  // B. LANGUAGE (Critical Filter)
  score += languageScore(text, parsed.languages);

  // C. RESOLUTION (Significant multiplier)
  score += qualityScore(stream, text) * 2;
  score += preferredQualityScore(text, parsed.quality, options?.preferredQuality);

  // D. SOURCE TYPE (WEB-DL, BluRay, etc)
  score += sourceScore(parsed.source);

  // E. AUDIO CODEC (High weight for mobile compatibility)
  score += audioScore(text) * 2;

  // F. VIDEO CODEC & CONTAINER
  score += videoCodecScore(text);
  score += containerScore(text);

  // G. FILE SIZE (Penalty for ultra-large/tiny files)
  score += sizeScore(sizeGiB, parsed.quality);

  // H. SEEDERS (Reliability check)
  score += seedScore(parsed.seeds, alreadyResolvable);

  // I. Practical playback stability for mobile devices.
  score += stabilityScore(text, parsed, sizeGiB, preferQuickStart);

  // J. FINAL PENALTIES (Avoiding bad releases)
  if (text.includes('SAMPLE')) score -= 250;
  if (text.includes('CAM') || text.includes('TELESYNC')) score -= 200;
  score -= options?.sessionPenalty ?? 0;

  return score;
}

export function sortStreams(streams: AddonStream[], options?: StreamScoreOptions): AddonStream[] {
  return [...streams].sort((a, b) => scoreStream(b, options) - scoreStream(a, options));
}

export function selectBestStream(streams: AddonStream[], options?: StreamScoreOptions): AddonStream | null {
  return sortStreams(streams, options)[0] ?? null;
}

function normalizeLanguageCode(language?: string | null): string {
  return (language ?? '').toLowerCase().trim().split(/[-_]/)[0];
}

function audioTrackPenalty(text: string): number {
  if (text.includes('COMMENTARY')) return -300;
  if (text.includes('DESCRIPTIVE')) return -220;
  if (text.includes('AUDIO DESCRIPTION')) return -220;
  if (text.includes('VISUALLY IMPAIRED')) return -220;
  return 0;
}

export function pickBestAudioTrack(tracks: AudioTrack[], preferredLanguage = 'en'): AudioTrack | null {
  if (!tracks.length) return null;

  const preferred = normalizeLanguageCode(preferredLanguage);
  const scoredTracks = tracks.map(track => {
    const text = `${track.label ?? ''} ${track.name ?? ''} ${track.language ?? ''}`.toUpperCase();
    const language = normalizeLanguageCode(track.language);
    let score = 0;

    if (language === preferred) score += 500;
    else if (preferred === 'en' && (text.includes('ENGLISH') || text.includes(' ENG'))) score += 500;
    else if (!language || language === 'und') score += 35;

    if (track.autoSelect) score += 80;
    if (track.isDefault) score += 50;

    score += audioTrackPenalty(text);
    score += audioScore(text);

    return { track, score };
  });

  return scoredTracks.sort((a, b) => b.score - a.score)[0]?.track ?? tracks[0];
}
