import { AddonStream } from '../context/AddonContext';

function normalizeStreamText(value?: string | null): string {
  return (value ?? '').trim().replace(/\s+/g, ' ').toLowerCase();
}

export function getStreamIdentityKey(stream: AddonStream | null | undefined): string {
  if (!stream) return '';
  return normalizeStreamText(
    stream.infoHash
    ?? stream.url
    ?? stream.behaviorHints?.filename
    ?? stream.title
    ?? stream.name
    ?? '',
  );
}
