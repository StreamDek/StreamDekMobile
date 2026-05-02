import { AddonStream } from '../context/AddonContext';

// ── Parsed stream model ────────────────────────────────────────────────────────

export interface ParsedStream {
  quality:     string | null;  // '4K' | '1080p' | '720p' | ...
  source:      string | null;  // 'BluRay' | 'WEB-DL' | 'WEBRip' | ...
  codec:       string | null;  // 'x265' | 'x264' | 'AV1' | ...
  audio:       string | null;  // 'Atmos' | 'DTS-HD MA' | 'DTS' | 'AAC' | ...
  hdr:         string | null;  // 'DV' | 'HDR10+' | 'HDR10' | 'HDR' | ...
  languages:   string[];       // ['English', 'Italian'] etc
  size:        string | null;  // '12.4 GB' | '850 MB' | ...
  seeds:       number | null;  // parsed from 👤 or "N seeds"
  fileTitle:    string | null;  // The actual filename/title of the media
  providerLine: string;        // first line of stream.name (release group / addon label)
  specLine:    string;         // "BluRay • x265 • Atmos • HDR10" (for display)
}

// ── Internal helpers ───────────────────────────────────────────────────────────

/** Combined search text, uppercased for pattern matching. */
function searchText(stream: AddonStream): string {
  return [
    stream.name              ?? '',
    stream.title             ?? '',
    stream.description       ?? '',
    stream.behaviorHints?.filename ?? '',
  ].join(' ').toUpperCase();
}

/** Return the label of the first pattern that matches, or null. */
function firstMatch(text: string, patterns: [RegExp, string][]): string | null {
  for (const [re, label] of patterns) {
    if (re.test(text)) return label;
  }
  return null;
}

// ── Pattern tables ─────────────────────────────────────────────────────────────

const SOURCE_PATTERNS: [RegExp, string][] = [
  [/\bBLU[\s\-]?RAY\b|\bBDRIP\b|\bBDMV\b|\bBD\b/,    'BluRay'],
  [/\bWEB[\s\-]DL\b/,                                  'WEB-DL'],
  [/\bWEBRIP\b|\bWEB[\s\-]RIP\b/,                      'WEBRip'],
  [/\bHDRIP\b/,                                         'HDRip'],
  [/\bHDTV\b/,                                          'HDTV'],
  [/\bDVDRIP\b|\bDVD\b/,                               'DVD'],
  [/\bTELESYNC\b|\b(?<!\w)TS(?!\w)\b/,                'TeleSync'],
  [/\bCAMRIP\b|\bHC\b|\bCAM\b/,                        'CAM'],
];

const CODEC_PATTERNS: [RegExp, string][] = [
  [/\bAV1\b/,                                          'AV1'],
  [/\bX\.?265\b|\bH\.?265\b|\bHEVC\b/,               'x265'],
  [/\bX\.?264\b|\bH\.?264\b/,                          'x264'],
  [/\bVP9\b/,                                          'VP9'],
  [/\bXVID\b/,                                         'XViD'],
  [/\bDIVX\b/,                                         'DivX'],
];

const AUDIO_PATTERNS: [RegExp, string][] = [
  [/\bDOLBY\s?ATMOS\b|\bATMOS\b/,                     'Atmos'],
  [/\bDTS[\s\-]HD[\s\-]MA\b/,                          'DTS-HD MA'],
  [/\bDTS[\s\-]HD\b/,                                  'DTS-HD'],
  [/\bTRUEHD\b|\bTRUE[\s\-]HD\b/,                     'TrueHD'],
  [/\bDTS[\s:\-]X\b/,                                  'DTS:X'],
  [/\bDTS[\s\-]ES\b/,                                  'DTS-ES'],
  [/\bDTS\b/,                                          'DTS'],
  [/\bE[\s\-]?AC[\s\-]?3\b|\bEAC3\b|\bDD\+\b|\bDDP\b/, 'EAC3'],
  [/\bAC[\s\-]?3\b|\bDOLBY[\s\-]DIGITAL\b|\bDD5\.1\b/, 'AC3'],
  [/\bAAC\b/,                                          'AAC'],
  [/\bFLAC\b/,                                         'FLAC'],
  [/\bOPUS\b/,                                         'Opus'],
  [/\bMP3\b/,                                          'MP3'],
];

const HDR_PATTERNS: [RegExp, string][] = [
  [/\bDOLBY[\s\-]?VISION\b|\bDVHE\b|\bDV\b/,         'DV'],
  [/\bHDR10\+\b|\bHDR10PLUS\b/,                       'HDR10+'],
  [/\bHDR10\b/,                                        'HDR10'],
  [/\bHLG\b/,                                          'HLG'],
  [/\bHDR\b/,                                          'HDR'],
];

const LANGUAGE_PATTERNS: [RegExp, string][] = [
  [/\bMULTI\b|\bDUAL\b|\bDL\b/,                        'Multi'],
  [/\bENG?(?:LISH)?\b/i,                               'English'],
  [/\bITA(?:LIAN)?\b/i,                               'Italian'],
  [/\bFRA?(?:NCH)?\b/i,                               'French'],
  [/\bGER?(?:MAN)?\b/i,                                'German'],
  [/\bSPA?(?:NISH)?\b|\bESP\b/i,                       'Spanish'],
  [/\bPOR?(?:TUGUESE)?\b/i,                            'Portuguese'],
  [/\bRUS(?:SIAN)?\b/i,                                'Russian'],
  [/\bHIN(?:DI)?\b/i,                                  'Hindi'],
  [/\bKOR(?:EAN)?\b/i,                                 'Korean'],
  [/\bCHI(?:NESE)?\b|\bZHO\b/i,                        'Chinese'],
  [/\bJPN?|JAPANESE\b/i,                               'Japanese'],
];

// ── Field parsers ─────────────────────────────────────────────────────────────

function parseLanguages(text: string): string[] {
  const results: string[] = [];
  for (const [re, label] of LANGUAGE_PATTERNS) {
    if (re.test(text)) results.push(label);
  }
  return [...new Set(results)]; // unique
}

function parseSeeds(stream: AddonStream): number | null {
  const raw = [stream.name ?? '', stream.title ?? '', stream.description ?? ''].join(' ');
  // 👤 1234
  const m1 = raw.match(/👤\s*(\d[\d,]*)/);
  if (m1) return parseInt(m1[1].replace(/,/g, ''), 10);
  // "Seeds: 1234" or "1234 seeds"
  const m2 = raw.match(/(?:seeds?[\s:]+(\d[\d,]+)|(\d[\d,]+)\s+seeds?)/i);
  if (m2) return parseInt((m2[1] ?? m2[2]).replace(/,/g, ''), 10);
  return null;
}

function parseSize(stream: AddonStream): string | null {
  // Prefer the already-parsed field
  if (stream.size) {
    if (typeof stream.size === 'number') {
      const bytes = stream.size as number;
      const gb = bytes / (1024 * 1024 * 1024);
      if (gb >= 1) return `${gb.toFixed(2)} GB`;
      return `${(bytes / (1024 * 1024)).toFixed(0)} MB`;
    }
    return String(stream.size);
  }
  if (typeof stream.behaviorHints?.videoSize === 'number') {
    const bytes = stream.behaviorHints.videoSize;
    const gb = bytes / (1024 * 1024 * 1024);
    if (gb >= 1) return `${gb.toFixed(2)} GB`;
    return `${(bytes / (1024 * 1024)).toFixed(0)} MB`;
  }
  // 💾 12.4 GB or "12.4 GB" or "850 MB"
  const raw = [stream.name ?? '', stream.title ?? '', stream.description ?? '', stream.behaviorHints?.filename ?? ''].join(' ');
  const m = raw.match(/💾\s*([\d.,]+\s*(?:GB|MB|TB))|(\d+(?:\.\d+)?\s*(?:GB|MB|TB))/i);
  if (m) return (m[1] ?? m[2]).replace(/\s+/g, ' ').trim();
  return null;
}

function parseProviderLine(stream: AddonStream): string {
  // The first line of `name` is typically the release group / provider label
  const nameLine = (stream.name ?? '').split('\n')[0].trim();
  return nameLine || stream.addonName;
}

function parseFileTitle(stream: AddonStream): string | null {
  // Prefer behaviorHints.filename if available
  if (stream.behaviorHints?.filename) return stream.behaviorHints.filename;
  // Otherwise, take the first non-empty line of the title description
  const title = `${stream.title ?? ''}\n${stream.description ?? ''}`
    .split('\n')
    .map(l => l.trim())
    .find(l => l.length > 0 && !l.includes('💾') && !l.includes('👤'));
  return title || null;
}

// ── Public API ─────────────────────────────────────────────────────────────────

export function parseStream(stream: AddonStream): ParsedStream {
  const text = searchText(stream);

  const quality = stream.quality;
  const source  = firstMatch(text, SOURCE_PATTERNS);
  const codec   = firstMatch(text, CODEC_PATTERNS);
  const audio   = firstMatch(text, AUDIO_PATTERNS);
  const hdr     = firstMatch(text, HDR_PATTERNS);
  const languages = parseLanguages(text);
  const size    = parseSize(stream);
  const seeds   = parseSeeds(stream);
  const fileTitle = parseFileTitle(stream);

  // Build the human-readable spec line (skip quality — shown separately as badge)
  // Include "Multi" or other languages if detected, but English is assumed if empty or Multi.
  const langLabel = languages.includes('Multi') ? 'Multi' : (languages.length > 0 && !languages.includes('English') ? languages[0] : null);

  const specLine = [source, codec, audio, hdr, langLabel]
    .filter(Boolean)
    .join(' • ');

  const providerLine = parseProviderLine(stream);

  return { quality, source, codec, audio, hdr, languages, size, seeds, fileTitle, providerLine, specLine };
}

/** Format a seed count compactly: 1200 → "1.2K", 500 → "500" */
export function formatSeeds(n: number): string {
  if (n >= 1000) return `${(n / 1000).toFixed(1).replace(/\.0$/, '')}K`;
  return String(n);
}
