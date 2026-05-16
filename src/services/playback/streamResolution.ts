import type { AddonStream } from '../../context/AddonContext';
import type { DebridFailure, DebridProviderName, DebridResolvedStream } from '../../context/DebridContext';
import { createLocalTorrentPlaybackUrl } from '../../utils/torrentServerClient';

export interface StreamResolutionOptions {
  stream: AddonStream;
  debridAccountCount: number;
  resolveStream: (
    infoHash: string,
    magnetLink: string,
    filename?: string,
    options?: { maxSize?: number; providerHint?: DebridProviderName },
  ) => Promise<DebridResolvedStream | null>;
  streamTorrent: (infoHash: string, magnetLink: string, filename?: string) => Promise<string | null>;
  streamingMode: string;
  streamSelectionEnabled: boolean;
  maxFileSizeGB: number;
  defaultMaxSizeBytes?: number;
  shouldContinue?: () => boolean;
  onDebridFailures?: (label: string, failures: DebridFailure[] | undefined) => void;
  onStep?: (message: string) => void;
  onWarn?: (message: string) => void;
  onError?: (message: string, error: unknown) => void;
}

export async function resolvePlayableStreamUrl({
  stream,
  debridAccountCount,
  resolveStream,
  streamTorrent,
  streamingMode,
  streamSelectionEnabled,
  maxFileSizeGB,
  defaultMaxSizeBytes,
  shouldContinue,
  onDebridFailures,
  onStep,
  onWarn,
  onError,
}: StreamResolutionOptions): Promise<string | null> {
  try {
    console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl start', {
      name: stream.name ?? stream.title ?? null,
      hasUrl: !!stream.url,
      hasInfoHash: !!stream.infoHash,
      cachedBy: stream.cachedBy,
      filename: stream.behaviorHints?.filename ?? null,
      debridAccountCount,
      streamingMode,
      streamSelectionEnabled,
      maxFileSizeGB,
    });

    if (shouldContinue && !shouldContinue()) return null;

    if (stream.url) {
      onStep?.('direct-url');
      console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl direct-url hit', {
        name: stream.name ?? stream.title ?? null,
        urlPrefix: stream.url.slice(0, 80),
      });
      return stream.url;
    }
    if (!stream.infoHash) return null;

    const hint = stream.behaviorHints?.filename;
    const magnet = `magnet:?xt=urn:btih:${stream.infoHash}${hint ? `&dn=${encodeURIComponent(hint)}` : ''}`;

    if (debridAccountCount > 0) {
      onStep?.('debrid-resolve');
      try {
        const maxSizeBytes = maxFileSizeGB > 0
          ? Math.round(maxFileSizeGB * 1024 * 1024 * 1024)
          : defaultMaxSizeBytes;
        console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl debrid-resolve attempt', {
          name: stream.name ?? stream.title ?? null,
          providerHint: stream.cachedBy[0] ?? null,
          maxSizeBytes: maxSizeBytes ?? null,
        });
        const resolved = await resolveStream(
          stream.infoHash,
          magnet,
          hint,
          maxSizeBytes || stream.cachedBy[0]
            ? {
              ...(maxSizeBytes ? { maxSize: maxSizeBytes } : {}),
              ...(stream.cachedBy[0] ? { providerHint: stream.cachedBy[0] } : {}),
            }
            : undefined,
        );
        if (shouldContinue && !shouldContinue()) return null;
        if (resolved?.url) {
          console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl debrid-resolve success', {
            name: stream.name ?? stream.title ?? null,
            provider: resolved.provider ?? null,
            urlPrefix: resolved.url.slice(0, 80),
          });
          return resolved.url;
        }
        console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl debrid-resolve returned no url', {
          name: stream.name ?? stream.title ?? null,
        });
      } catch (error: any) {
        console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl debrid-resolve failed', {
          name: stream.name ?? stream.title ?? null,
          error: error instanceof Error ? error.message : String(error),
          failures: error?.failures ?? [],
        });
        onDebridFailures?.('Premium resolver failed', error?.failures);
      }
    }

    if (streamingMode === 'server') {
      onStep?.('local-torrent');
      try {
        console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl local-torrent attempt', {
          name: stream.name ?? stream.title ?? null,
        });
        const localTorrentUrl = await createLocalTorrentPlaybackUrl(stream.infoHash, magnet, hint);
        if (shouldContinue && !shouldContinue()) return null;
        if (localTorrentUrl) {
          console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl local-torrent success', {
            name: stream.name ?? stream.title ?? null,
            urlPrefix: localTorrentUrl.slice(0, 80),
          });
          return localTorrentUrl;
        }
        console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl local-torrent unavailable', {
          name: stream.name ?? stream.title ?? null,
        });
        onWarn?.('local-torrent-unavailable');
      } catch (error) {
        console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl local-torrent failed', {
          name: stream.name ?? stream.title ?? null,
          error: error instanceof Error ? error.message : String(error),
        });
        onWarn?.('local-torrent-error');
        onError?.('Local torrent playback failed', error);
      }
    }

    onStep?.('backend-torrent');
    console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl backend-torrent attempt', {
      name: stream.name ?? stream.title ?? null,
    });
    const backendTorrentUrl = await streamTorrent(stream.infoHash, magnet, hint);
    if (shouldContinue && !shouldContinue()) return null;
    if (backendTorrentUrl) {
      console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl backend-torrent success', {
        name: stream.name ?? stream.title ?? null,
        urlPrefix: backendTorrentUrl.slice(0, 80),
      });
    } else {
      console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl backend-torrent returned null', {
        name: stream.name ?? stream.title ?? null,
      });
    }
    return backendTorrentUrl;
  } catch (error) {
    console.log('[StreamDekSeriesDebug] resolvePlayableStreamUrl fatal error', {
      name: stream.name ?? stream.title ?? null,
      error: error instanceof Error ? error.message : String(error),
    });
    onError?.('Stream resolution failed', error);
    return null;
  }
}
