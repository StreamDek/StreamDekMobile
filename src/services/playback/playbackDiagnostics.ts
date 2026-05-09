import { MutableRefObject } from 'react';
import { postClientLog } from '../../utils/clientLog';

export type PlaybackDiagnosticLevel = 'info' | 'warn' | 'error';

export type PlaybackDiagnosticMeta = {
  userId?: string | null;
  type?: string | null;
  title?: string | null;
  imdbId?: string | null;
  movieId?: string | null;
};

export function createPlaybackDiagnostics(
  tag: string,
  metaRef: MutableRefObject<PlaybackDiagnosticMeta>,
) {
  return (level: PlaybackDiagnosticLevel, message: string, context?: Record<string, unknown>) => {
    if (level === 'error') {
      console.error(message, context?.error);
    } else if (level === 'warn') {
      console.warn(message);
    } else {
      console.log(message);
    }

    void postClientLog({
      level,
      tag,
      message,
      userId: typeof metaRef.current.userId === 'string' ? metaRef.current.userId : null,
      context: {
        ...metaRef.current,
        ...(context ?? {}),
      },
    });
  };
}
