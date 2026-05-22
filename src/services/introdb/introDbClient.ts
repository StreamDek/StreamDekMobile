import { cachedFetch, invalidateCacheKey } from '../../utils/apiCache';

const INTRODB_API_BASE = 'https://api.introdb.app';

export type IntroDbSegmentType = 'intro' | 'recap' | 'outro';

export interface IntroSegment {
  segmentType: IntroDbSegmentType;
  startSec: number;
  endSec: number;
}

export interface SubmitIntroSegmentInput {
  imdbId: string;
  season: number;
  episode: number;
  startSec: number;
  endSec: number;
  apiKey?: string | null;
  authToken?: string | null;
}

function getEpisodeCacheKey(imdbId: string, season: number, episode: number): string {
  return `introdb:segments:${imdbId}:${season}:${episode}`;
}

function normalizeSegmentType(value: unknown): IntroDbSegmentType | null {
  const normalized = String(value ?? '').trim().toLowerCase();
  if (normalized === 'intro' || normalized === 'recap' || normalized === 'outro') {
    return normalized;
  }
  return null;
}

export function parseClockOrSeconds(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? Math.max(0, value) : null;
  }

  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed) return null;

  const asNumber = Number(trimmed);
  if (Number.isFinite(asNumber)) {
    return Math.max(0, asNumber);
  }

  const parts = trimmed.split(':').map(part => part.trim());
  if (parts.length < 2 || parts.length > 3) return null;
  const parsed = parts.map(part => Number(part));
  if (parsed.some(part => !Number.isFinite(part) || part < 0)) return null;

  if (parsed.length === 2) {
    return parsed[0] * 60 + parsed[1];
  }

  return parsed[0] * 3600 + parsed[1] * 60 + parsed[2];
}

function normalizeSegment(raw: any): IntroSegment | null {
  const segmentType = normalizeSegmentType(raw?.segment_type ?? raw?.type ?? raw?.kind);
  const startSec = parseClockOrSeconds(raw?.start_sec ?? raw?.start ?? raw?.startSeconds ?? raw?.start_seconds);
  const endSec = parseClockOrSeconds(raw?.end_sec ?? raw?.end ?? raw?.endSeconds ?? raw?.end_seconds);

  if (!segmentType || startSec == null || endSec == null || endSec <= startSec) {
    return null;
  }

  return {
    segmentType,
    startSec,
    endSec,
  };
}

function extractRawSegments(payload: any): any[] {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.segments)) return payload.segments;
  if (Array.isArray(payload?.data)) return payload.data;

  if (payload && typeof payload === 'object') {
    const normalized = normalizeSegment(payload);
    if (normalized) return [payload];
  }

  return [];
}

async function fetchSegmentsLive(imdbId: string, season: number, episode: number, signal?: AbortSignal): Promise<IntroSegment[]> {
  const params = new URLSearchParams({
    imdb_id: imdbId,
    season: String(season),
    episode: String(episode),
  });
  const response = await fetch(`${INTRODB_API_BASE}/segments?${params.toString()}`, signal ? { signal } : undefined);
  if (!response.ok) {
    throw new Error(`IntroDB fetch failed with status ${response.status}`);
  }

  const payload = await response.json();
  return extractRawSegments(payload)
    .map(normalizeSegment)
    .filter((segment): segment is IntroSegment => segment != null);
}

export async function fetchEpisodeIntroSegment(
  imdbId: string,
  season: number,
  episode: number,
  options?: { signal?: AbortSignal },
): Promise<IntroSegment | null> {
  const read = async () => {
    const segments = await fetchSegmentsLive(imdbId, season, episode, options?.signal);
    return segments.find(segment => segment.segmentType === 'intro') ?? null;
  };

  if (options?.signal) {
    return read();
  }

  return cachedFetch(getEpisodeCacheKey(imdbId, season, episode), read);
}

export async function submitIntroSegment(input: SubmitIntroSegmentInput): Promise<void> {
  const apiKey = input.apiKey?.trim() ?? '';
  const authToken = input.authToken?.trim() ?? '';

  if (!apiKey && !authToken) {
    throw new Error('IntroDB contribution requires an API key.');
  }

  const response = await fetch(`${INTRODB_API_BASE}/segments/submit`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
      ...(apiKey ? { 'X-API-Key': apiKey } : {}),
    },
    body: JSON.stringify({
      imdb_id: input.imdbId,
      segment_type: 'intro',
      season: input.season,
      episode: input.episode,
      start_sec: input.startSec,
      end_sec: input.endSec,
    }),
  });

  if (!response.ok) {
    let message = 'Could not submit intro segment.';
    try {
      const payload = await response.json();
      if (typeof payload?.error === 'string' && payload.error.trim().length > 0) {
        message = payload.error.trim();
      }
    } catch {
      // Ignore malformed error bodies.
    }
    throw new Error(message);
  }

  await invalidateCacheKey(getEpisodeCacheKey(input.imdbId, input.season, input.episode));
}
