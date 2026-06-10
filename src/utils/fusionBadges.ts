import type { SessionUser } from '../lib/authClient';
import { API_BASE } from '../constants/api';
import { buildAuthHeaders } from './authHeaders';
import { AddonStream } from '../context/AddonContext';

// ── Types (mirrors backend src/routes/badges.ts) ───────────────────────────────

export interface FusionBadgeGroup {
  id: string;
  name: string;
  isExpanded?: boolean;
  color?: string;
  borderColor?: string;
}

export interface FusionBadgeFilter {
  id: string;
  groupId: string;
  name: string;
  pattern: string;
  imageURL: string;
  tagColor?: string;
  borderColor?: string;
  textColor?: string;
  tagStyle?: string;
  isEnabled?: boolean;
  type?: string;
}

export interface FusionBadgeSource {
  url: string;
  groups: FusionBadgeGroup[];
  filters: FusionBadgeFilter[];
  fetchedAt: string;
}

export interface FusionBadgeGroupMatches {
  group: FusionBadgeGroup;
  badges: FusionBadgeFilter[];
}

const SPECIAL_GROUP: FusionBadgeGroup = { id: '', name: 'Special' };

// ── Pattern compilation ─────────────────────────────────────────────────────────
// Filter patterns are PCRE-style strings prefixed with `(?i)` for case-insensitive
// matching. JS RegExp doesn't support inline `(?i)`, so it's stripped and replaced
// with the `i` flag. Some patterns use lookbehind assertions that older engines may
// reject — those filters are skipped rather than crashing the match pass.
const patternCache = new Map<string, RegExp | null>();

function compilePattern(pattern: string): RegExp | null {
  try {
    if (pattern.startsWith('(?i)')) {
      return new RegExp(pattern.slice(4), 'i');
    }
    return new RegExp(pattern, 'i');
  } catch {
    return null;
  }
}

function getCompiledPattern(pattern: string): RegExp | null {
  let compiled = patternCache.get(pattern);
  if (compiled === undefined) {
    compiled = compilePattern(pattern);
    patternCache.set(pattern, compiled);
  }
  return compiled;
}

// ── Stream matching ──────────────────────────────────────────────────────────────

function streamSearchText(stream: AddonStream): string {
  return [
    stream.name ?? '',
    stream.title ?? '',
    stream.description ?? '',
    stream.behaviorHints?.filename ?? '',
  ].join(' ');
}

/** Match a stream's metadata against one or more Fusion badge sources, grouped in source group order. */
export function matchFusionBadges(stream: AddonStream, sources: FusionBadgeSource[]): FusionBadgeGroupMatches[] {
  const text = streamSearchText(stream);
  if (!text.trim() || sources.length === 0) return [];

  const groupOrder: FusionBadgeGroup[] = [];
  const groupMap = new Map<string, FusionBadgeGroup>();
  const matchesByGroup = new Map<string, FusionBadgeFilter[]>();
  const seenKeys = new Set<string>();

  for (const source of sources) {
    for (const group of source.groups) {
      if (!groupMap.has(group.id)) {
        groupMap.set(group.id, group);
        groupOrder.push(group);
      }
    }
  }

  for (const source of sources) {
    for (const filter of source.filters) {
      if (filter.isEnabled === false) continue;
      const key = filter.imageURL || `${filter.groupId}:${filter.id}`;
      if (seenKeys.has(key)) continue;

      const re = getCompiledPattern(filter.pattern);
      if (!re || !re.test(text)) continue;
      seenKeys.add(key);

      let group = groupMap.get(filter.groupId);
      if (!group) {
        group = filter.groupId ? { id: filter.groupId, name: filter.groupId } : SPECIAL_GROUP;
        groupMap.set(filter.groupId, group);
        groupOrder.push(group);
      }

      const list = matchesByGroup.get(filter.groupId) ?? [];
      list.push(filter);
      matchesByGroup.set(filter.groupId, list);
    }
  }

  return groupOrder
    .map(group => ({ group, badges: matchesByGroup.get(group.id) ?? [] }))
    .filter(entry => entry.badges.length > 0);
}

/** Flatten grouped matches into a single ordered list of badges for row rendering. */
export function flattenFusionBadges(groups: FusionBadgeGroupMatches[]): FusionBadgeFilter[] {
  return groups.flatMap(entry => entry.badges);
}

export function countEnabledFilters(source: FusionBadgeSource): number {
  return source.filters.filter(f => f.isEnabled !== false).length;
}

export function countGroupsWithFilters(source: FusionBadgeSource): number {
  const groupIds = new Set(source.filters.map(f => f.groupId));
  return groupIds.size;
}

export function groupSourceFilters(source: FusionBadgeSource): FusionBadgeGroupMatches[] {
  const groupOrder: FusionBadgeGroup[] = [];
  const groupMap = new Map<string, FusionBadgeGroup>();
  const byGroup = new Map<string, FusionBadgeFilter[]>();

  for (const group of source.groups) {
    if (!groupMap.has(group.id)) {
      groupMap.set(group.id, group);
      groupOrder.push(group);
    }
  }

  for (const filter of source.filters) {
    let group = groupMap.get(filter.groupId);
    if (!group) {
      group = filter.groupId ? { id: filter.groupId, name: filter.groupId } : SPECIAL_GROUP;
      groupMap.set(filter.groupId, group);
      groupOrder.push(group);
    }
    const list = byGroup.get(filter.groupId) ?? [];
    list.push(filter);
    byGroup.set(filter.groupId, list);
  }

  return groupOrder
    .map(group => ({ group, badges: byGroup.get(group.id) ?? [] }))
    .filter(entry => entry.badges.length > 0);
}

// ── Backend proxy fetch ───────────────────────────────────────────────────────────

export async function fetchFusionBadgeSource(
  user: SessionUser | null,
  url: string,
  options: { refresh?: boolean } = {},
): Promise<FusionBadgeSource> {
  if (!user) throw new Error('Sign in required to load Fusion badge sources');

  const params = new URLSearchParams({ url });
  if (options.refresh) params.set('refresh', 'true');

  const response = await fetch(`${API_BASE}/badges/fusion-source?${params.toString()}`, {
    headers: await buildAuthHeaders(user, { includeContentType: false }),
  });

  if (!response.ok) {
    let message = 'Failed to load Fusion badge source';
    try {
      const data = await response.json();
      message = data?.error ?? message;
    } catch {
      // ignore — use default message
    }
    throw new Error(message);
  }

  return response.json();
}
