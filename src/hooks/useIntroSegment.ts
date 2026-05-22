import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchEpisodeIntroSegment, type IntroSegment } from '../services/introdb/introDbClient';

interface UseIntroSegmentOptions {
  enabled: boolean;
  type: string;
  imdbId?: string | null;
  season?: number | null;
  episode?: number | null;
  currentTime: number;
}

interface UseIntroSegmentResult {
  introSegment: IntroSegment | null;
  loading: boolean;
  error: string | null;
  shouldShowSkipIntro: boolean;
  dismissSkipIntro: () => void;
  markSkipCompleted: () => void;
  refresh: () => Promise<void>;
}

export function useIntroSegment(options: UseIntroSegmentOptions): UseIntroSegmentResult {
  const {
    enabled,
    type,
    imdbId,
    season,
    episode,
    currentTime,
  } = options;
  const [introSegment, setIntroSegment] = useState<IntroSegment | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dismissed, setDismissed] = useState(false);

  const normalizedImdbId = typeof imdbId === 'string' ? imdbId.trim() : '';
  const normalizedSeason = typeof season === 'number' && Number.isFinite(season) && season > 0 ? Math.trunc(season) : null;
  const normalizedEpisode = typeof episode === 'number' && Number.isFinite(episode) && episode > 0 ? Math.trunc(episode) : null;
  const fetchAllowed = enabled
    && type === 'tv'
    && normalizedImdbId.length > 0
    && normalizedSeason != null
    && normalizedEpisode != null;
  const episodeKey = `${type}:${normalizedImdbId}:${normalizedSeason ?? 'x'}:${normalizedEpisode ?? 'x'}`;

  useEffect(() => {
    setDismissed(false);
  }, [episodeKey]);

  const load = useCallback(async () => {
    if (!fetchAllowed || normalizedSeason == null || normalizedEpisode == null) {
      setIntroSegment(null);
      setError(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const segment = await fetchEpisodeIntroSegment(
        normalizedImdbId,
        normalizedSeason,
        normalizedEpisode,
      );
      setIntroSegment(segment);
      setError(null);
    } catch (nextError) {
      setIntroSegment(null);
      setError(nextError instanceof Error ? nextError.message : 'Could not load intro data.');
    } finally {
      setLoading(false);
    }
  }, [fetchAllowed, normalizedEpisode, normalizedImdbId, normalizedSeason]);

  useEffect(() => {
    let cancelled = false;

    if (!fetchAllowed || normalizedSeason == null || normalizedEpisode == null) {
      setIntroSegment(null);
      setError(null);
      setLoading(false);
      return () => {
        cancelled = true;
      };
    }

    setLoading(true);
    setError(null);
    void fetchEpisodeIntroSegment(
      normalizedImdbId,
      normalizedSeason,
      normalizedEpisode,
    )
      .then(segment => {
        if (!cancelled) {
          setIntroSegment(segment);
        }
      })
      .catch(nextError => {
        if (!cancelled) {
          setIntroSegment(null);
          setError(nextError instanceof Error ? nextError.message : 'Could not load intro data.');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [fetchAllowed, normalizedEpisode, normalizedImdbId, normalizedSeason]);

  const shouldShowSkipIntro = useMemo(() => {
    if (!fetchAllowed || dismissed || !introSegment) return false;
    if (!Number.isFinite(currentTime) || currentTime < 0) return false;
    return currentTime >= introSegment.startSec && currentTime < introSegment.endSec;
  }, [currentTime, dismissed, fetchAllowed, introSegment]);

  return {
    introSegment,
    loading,
    error,
    shouldShowSkipIntro,
    dismissSkipIntro: () => setDismissed(true),
    markSkipCompleted: () => setDismissed(true),
    refresh: async () => {
      await load();
    },
  };
}
