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
    if (shouldContinue && !shouldContinue()) return null;

    if (stream.url) {
      onStep?.('direct-url');
      return stream.url;
    }
    if (!stream.infoHash) return null;

    const hint = stream.behaviorHints?.filename;
    const magnet = `magnet:?xt=urn:btih:${stream.infoHash}${hint ? `&dn=${encodeURIComponent(hint)}` : ''}`;

    if (debridAccountCount > 0) {
      onStep?.('debrid-resolve');
      try {
        const maxSizeBytes = streamSelectionEnabled && maxFileSizeGB > 0
          ? Math.round(maxFileSizeGB * 1024 * 1024 * 1024)
          : defaultMaxSizeBytes;
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
        if (resolved?.url) return resolved.url;
      } catch (error: any) {
        onDebridFailures?.('Premium resolver failed', error?.failures);
      }
    }

    if (streamingMode === 'server') {
      onStep?.('local-torrent');
      try {
        const localTorrentUrl = await createLocalTorrentPlaybackUrl(stream.infoHash, magnet, hint);
        if (shouldContinue && !shouldContinue()) return null;
        if (localTorrentUrl) return localTorrentUrl;
        onWarn?.('local-torrent-unavailable');
      } catch (error) {
        onWarn?.('local-torrent-error');
        onError?.('Local torrent playback failed', error);
      }
    }

    onStep?.('backend-torrent');
    const backendTorrentUrl = await streamTorrent(stream.infoHash, magnet, hint);
    if (shouldContinue && !shouldContinue()) return null;
    return backendTorrentUrl;
  } catch (error) {
    onError?.('Stream resolution failed', error);
    return null;
  }
}
