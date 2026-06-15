import type { AddonStream } from '../context/AddonContext';

function splitLines(value?: string | null): string[] {
  return String(value ?? '')
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean);
}

export function getRawStreamText(stream: AddonStream): { headline: string | null; lines: string[] } {
  const headline = (stream.name ?? '').trim() || null;
  const lines = [...splitLines(stream.title), ...splitLines(stream.description)];

  if (lines.length === 0) {
    const fallback = [
      stream.behaviorHints?.filename,
      stream.url,
      stream.infoHash,
    ].find(value => typeof value === 'string' && value.trim().length > 0);
    if (fallback) lines.push(String(fallback).trim());
  }

  const seen = new Set<string>();
  const deduped = lines.filter(line => {
    const key = line.toLowerCase();
    if (headline && key === headline.toLowerCase()) return false;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  return { headline, lines: deduped };
}
