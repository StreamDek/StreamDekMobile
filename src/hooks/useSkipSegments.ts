import { useCallback, useEffect, useMemo, useState } from 'react';
import { episodeProgressKey } from '../context/WatchProgressContext';
import {
  fetchEpisodeSegments,
  type IntroDbSegmentType,
  type IntroSegment,
} from '../services/introdb/introDbClient';
import { tmdbFetch } from '../utils/tmdbFetch';

export interface NextEpisodeTarget {
  showId: number;
  showTitle: string;
  showPoster: string | null;
  showBackdrop: string | null;
  imdbId: string | null;
  season: number;
  episodeNumber: number;
  episodeName: string | null;
  episodeOverview: string | null;
  episodeStill: string | null;
  episodeReleaseDate: string | null;
  episodeRuntime: number | null;
  progressKey: string;
}

export interface SkipSegmentsAction {
  kind: 'skip' | 'next_episode';
  segmentType: IntroDbSegmentType;
  labelKey: 'skip_intro_button' | 'skip_recap_button' | 'next_episode_button';
  targetTimeSec?: number;
  nextEpisodeTarget?: NextEpisodeTarget;
}

interface UseSkipSegmentsOptions {
  enabled: boolean;
  type: string;
  imdbId?: string | null;
  showId?: string | number | null;
  season?: number | null;
  episode?: number | null;
  currentTime: number;
  duration?: number | null;
  title?: string | null;
  backdrop?: string | null;
  poster?: string | null;
  nextEpisodeThresholdMode?: 'percent' | 'minutes';
  nextEpisodeThresholdPercent?: number;
  nextEpisodeThresholdMinutes?: number;
}

interface UseSkipSegmentsResult {
  segments: IntroSegment[];
  loading: boolean;
  error: string | null;
  activeAction: SkipSegmentsAction | null;
  hasImmediateAction: boolean;
  markActionHandled: () => void;
  refresh: () => Promise<void>;
}

const SEGMENT_PRIORITY: Record<IntroDbSegmentType, number> = {
  intro: 0,
  recap: 1,
  outro: 2,
};

function parsePositiveInteger(value: unknown): number | null {
  const next = Number(value);
  if (!Number.isFinite(next) || next <= 0) return null;
  return Math.trunc(next);
}

function normalizeText(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function deriveShowTitle(value: string | null | undefined): string | null {
  const normalized = normalizeText(value);
  if (!normalized) return null;
  return normalized.replace(/\s+S\d+\s*E\d+\s*$/i, '').trim() || normalized;
}

async function readJson(path: string, signal?: AbortSignal): Promise<any | null> {
  const response = await tmdbFetch(path, signal ? { signal } : undefined);
  if (!response.ok) return null;
  return response.json();
}

async function fetchNextEpisodeTarget(options: {
  showId: number;
  imdbId: string | null;
  season: number;
  episode: number;
  title?: string | null;
  backdrop?: string | null;
  poster?: string | null;
  signal?: AbortSignal;
}): Promise<NextEpisodeTarget | null> {
  const {
    showId,
    imdbId,
    season,
    episode,
    title,
    backdrop,
    poster,
    signal,
  } = options;
  const details = await readJson(`/tmdb/details/tv/${showId}`, signal);
  if (!details) return null;

  const detailsTitle = normalizeText(details?.title);
  const showTitle = detailsTitle ?? deriveShowTitle(title) ?? 'Series';
  const showPoster = normalizeText(details?.poster) ?? normalizeText(poster);
  const showBackdrop = normalizeText(details?.backdrop) ?? normalizeText(backdrop);
  const resolvedImdbId = normalizeText(imdbId) ?? normalizeText(details?.imdbId);

  const candidateSeasonNumbers = Array.from(new Set([
    season,
    ...(Array.isArray(details?.seasons)
      ? details.seasons
          .map((candidate: any) => parsePositiveInteger(candidate?.season_number))
          .filter((candidate: number | null): candidate is number => candidate != null && candidate >= season)
      : []),
  ])).sort((left, right) => left - right);

  for (const candidateSeason of candidateSeasonNumbers) {
    const seasonData = await readJson(`/tmdb/season/${showId}/${candidateSeason}`, signal);
    const episodes = Array.isArray(seasonData?.episodes) ? seasonData.episodes : [];
    const nextEpisode = episodes.find((candidate: any) => {
      const episodeNumber = parsePositiveInteger(candidate?.episode_number);
      if (episodeNumber == null) return false;
      if (candidateSeason === season) return episodeNumber > episode;
      return true;
    });

    if (!nextEpisode) continue;

    const nextEpisodeNumber = parsePositiveInteger(nextEpisode?.episode_number);
    if (nextEpisodeNumber == null) continue;

    return {
      showId,
      showTitle,
      showPoster,
      showBackdrop,
      imdbId: resolvedImdbId,
      season: candidateSeason,
      episodeNumber: nextEpisodeNumber,
      episodeName: normalizeText(nextEpisode?.name),
      episodeOverview: normalizeText(nextEpisode?.overview),
      episodeStill: normalizeText(nextEpisode?.still),
      episodeReleaseDate: normalizeText(nextEpisode?.air_date),
      episodeRuntime: typeof nextEpisode?.runtime === 'number' && Number.isFinite(nextEpisode.runtime)
        ? nextEpisode.runtime
        : typeof details?.runtime === 'number' && Number.isFinite(details.runtime)
          ? details.runtime
          : null,
      progressKey: episodeProgressKey(showId, candidateSeason, nextEpisodeNumber),
    };
  }

  return null;
}

export function useSkipSegments(options: UseSkipSegmentsOptions): UseSkipSegmentsResult {
  const {
    enabled,
    type,
    imdbId,
    showId,
    season,
    episode,
    currentTime,
    title,
    backdrop,
    poster,
  } = options;
  const [segments, setSegments] = useState<IntroSegment[]>([]);
  const [nextEpisodeTarget, setNextEpisodeTarget] = useState<NextEpisodeTarget | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [handledTypes, setHandledTypes] = useState<Record<IntroDbSegmentType, boolean>>({
    intro: false,
    recap: false,
    outro: false,
  });

  const normalizedImdbId = normalizeText(imdbId) ?? '';
  const normalizedShowId = parsePositiveInteger(showId);
  const normalizedSeason = parsePositiveInteger(season);
  const normalizedEpisode = parsePositiveInteger(episode);
  const fetchAllowed = enabled
    && type === 'tv'
    && normalizedImdbId.length > 0
    && normalizedSeason != null
    && normalizedEpisode != null;
  const episodeKey = `${type}:${normalizedShowId ?? 'x'}:${normalizedImdbId}:${normalizedSeason ?? 'x'}:${normalizedEpisode ?? 'x'}`;

  useEffect(() => {
    setHandledTypes({
      intro: false,
      recap: false,
      outro: false,
    });
  }, [episodeKey]);

  const load = useCallback(async (signal?: AbortSignal) => {
    if (!fetchAllowed || normalizedSeason == null || normalizedEpisode == null) {
      setSegments([]);
      setNextEpisodeTarget(null);
      setError(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const nextSegments = await fetchEpisodeSegments(
        normalizedImdbId,
        normalizedSeason,
        normalizedEpisode,
        signal ? { signal } : undefined,
      );

      const needsNextEpisodeTarget = nextSegments.some(segment => segment.segmentType === 'outro');
      let nextEpisode: NextEpisodeTarget | null = null;

      if (needsNextEpisodeTarget && normalizedShowId != null) {
        try {
          nextEpisode = await fetchNextEpisodeTarget({
            showId: normalizedShowId,
            imdbId: normalizedImdbId,
            season: normalizedSeason,
            episode: normalizedEpisode,
            title,
            backdrop,
            poster,
            signal,
          });
        } catch {
          nextEpisode = null;
        }
      }

      if (signal?.aborted) return;
      setSegments(nextSegments);
      setNextEpisodeTarget(nextEpisode);
      setError(null);
    } catch (nextError) {
      if (signal?.aborted) return;
      setSegments([]);
      setNextEpisodeTarget(null);
      setError(nextError instanceof Error ? nextError.message : 'Could not load segment data.');
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }, [
    backdrop,
    fetchAllowed,
    normalizedEpisode,
    normalizedImdbId,
    normalizedSeason,
    normalizedShowId,
    poster,
    title,
  ]);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const activeAction = useMemo<SkipSegmentsAction | null>(() => {
    if (!fetchAllowed) return null;
    if (!Number.isFinite(currentTime) || currentTime < 0) return null;

    const activeSegment = segments
      .filter(segment => {
        if (handledTypes[segment.segmentType]) return false;
        if (!(currentTime >= segment.startSec && currentTime < segment.endSec)) return false;
        return true;
      })
      .sort((left, right) => {
        const priorityDelta = SEGMENT_PRIORITY[left.segmentType] - SEGMENT_PRIORITY[right.segmentType];
        if (priorityDelta !== 0) return priorityDelta;
        return left.startSec - right.startSec;
      })[0] ?? null;

    if (!activeSegment) return null;

    if (activeSegment.segmentType === 'intro') {
      return {
        kind: 'skip',
        segmentType: activeSegment.segmentType,
        labelKey: 'skip_intro_button',
        targetTimeSec: activeSegment.endSec,
      };
    }

    if (activeSegment.segmentType === 'recap') {
      return {
        kind: 'skip',
        segmentType: activeSegment.segmentType,
        labelKey: 'skip_recap_button',
        targetTimeSec: activeSegment.endSec,
      };
    }

    if (!nextEpisodeTarget) return null;

    return {
      kind: 'next_episode',
      segmentType: activeSegment.segmentType,
      labelKey: 'next_episode_button',
      nextEpisodeTarget,
    };
  }, [
    currentTime,
    fetchAllowed,
    handledTypes,
    nextEpisodeTarget,
    segments,
  ]);

  const hasImmediateAction = useMemo(() => {
    if (!activeAction) return false;
    return segments.some(s => s.segmentType === activeAction.segmentType && s.startSec <= 1);
  }, [activeAction, segments]);

  const markActionHandled = useCallback(() => {
    if (!activeAction) return;
    setHandledTypes(previous => ({
      ...previous,
      [activeAction.segmentType]: true,
    }));
  }, [activeAction]);

  return {
    segments,
    loading,
    error,
    activeAction,
    hasImmediateAction,
    markActionHandled,
    refresh: async () => {
      await load();
    },
  };
}
