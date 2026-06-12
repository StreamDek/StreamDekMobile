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

function coerceString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function sanitizeLooseJson(raw: string): string {
  return raw.replace(
    /("(?:[^"\\]|\\.)*"|\btrue\b|\bfalse\b|\bnull\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\]|\})\s+(?="[^"]+"\s*:)/g,
    '$1, ',
  );
}

function normalizeFusionBadgeSource(payload: any, url: string): FusionBadgeSource {
  const payloadGroups = Array.isArray(payload?.groups) ? payload.groups : [];
  const payloadFilters = Array.isArray(payload?.filters)
    ? payload.filters
    : Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.badges)
        ? payload.badges
        : [];

  const groups: FusionBadgeGroup[] = payloadGroups
    .map((group: any, index: number) => ({
      id: coerceString(group?.id, `group-${index}`),
      name: coerceString(group?.name, coerceString(group?.label, `Group ${index + 1}`)),
      isExpanded: typeof group?.isExpanded === 'boolean' ? group.isExpanded : undefined,
      color: typeof group?.color === 'string' ? group.color : undefined,
      borderColor: typeof group?.borderColor === 'string' ? group.borderColor : undefined,
    }))
    .filter((group: FusionBadgeGroup) => group.id.length > 0 && group.name.length > 0);

  const knownGroupIds = new Set(groups.map(group => group.id));
  const filters: FusionBadgeFilter[] = payloadFilters
    .map((filter: any, index: number) => {
      const groupId = coerceString(
        filter?.groupId,
        coerceString(filter?.group, coerceString(filter?.category, '')),
      );
      const imageURL = coerceString(
        filter?.imageURL,
        coerceString(filter?.imageUrl, coerceString(filter?.image, coerceString(filter?.url))),
      );
      const pattern = coerceString(filter?.pattern, coerceString(filter?.regex));
      const name = coerceString(filter?.name, coerceString(filter?.label, `Badge ${index + 1}`));

      if (!groupId || !imageURL || !pattern || !name) return null;

      if (!knownGroupIds.has(groupId)) {
        knownGroupIds.add(groupId);
        groups.push({ id: groupId, name: groupId });
      }

      return {
        id: coerceString(filter?.id, `${groupId}-${index}`),
        groupId,
        name,
        pattern,
        imageURL,
        tagColor: typeof filter?.tagColor === 'string' ? filter.tagColor : undefined,
        borderColor: typeof filter?.borderColor === 'string' ? filter.borderColor : undefined,
        textColor: typeof filter?.textColor === 'string' ? filter.textColor : undefined,
        tagStyle: typeof filter?.tagStyle === 'string' ? filter.tagStyle : undefined,
        isEnabled: typeof filter?.isEnabled === 'boolean' ? filter.isEnabled : true,
        type: typeof filter?.type === 'string' ? filter.type : undefined,
      };
    })
    .filter((filter: FusionBadgeFilter | null): filter is FusionBadgeFilter => !!filter);

  if (filters.length === 0) {
    throw new Error('This URL does not contain any Fusion badge filters');
  }

  return {
    url,
    groups,
    filters,
    fetchedAt: typeof payload?.fetchedAt === 'string' ? payload.fetchedAt : new Date().toISOString(),
  };
}

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

// Cap the searched text — very long stream descriptions are the usual trigger
// for catastrophic backtracking in user-supplied badge patterns.
const MAX_SEARCH_TEXT_LENGTH = 700;
// A well-behaved pattern tests in microseconds; anything past this budget is
// backtracking pathologically and gets disabled for the rest of the session.
const SLOW_PATTERN_BUDGET_MS = 50;

function streamSearchText(stream: AddonStream): string {
  return [
    stream.name ?? '',
    stream.title ?? '',
    stream.description ?? '',
    stream.behaviorHints?.filename ?? '',
  ].join(' ').slice(0, MAX_SEARCH_TEXT_LENGTH);
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
      if (!re) continue;
      const startedAt = Date.now();
      let matched = false;
      try {
        matched = re.test(text);
      } catch {
        matched = false;
      }
      if (Date.now() - startedAt > SLOW_PATTERN_BUDGET_MS) {
        // Pathological pattern (catastrophic backtracking) — blacklist it so it
        // can never stall the JS thread again this session.
        patternCache.set(filter.pattern, null);
      }
      if (!matched) continue;
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

  if (response.ok) {
    return normalizeFusionBadgeSource(await response.json(), url);
  }

  let message = 'Failed to load Fusion badge source';
  try {
    const data = await response.json();
    message = data?.error ?? message;
  } catch {
    // ignore and use default message
  }

  let raw = '';
  try {
    const directResponse = await fetch(url);
    if (!directResponse.ok) throw new Error(message);
    raw = await directResponse.text();
  } catch {
    throw new Error(message);
  }

  try {
    return normalizeFusionBadgeSource(JSON.parse(raw), url);
  } catch {
    try {
      return normalizeFusionBadgeSource(JSON.parse(sanitizeLooseJson(raw)), url);
    } catch {
      throw new Error(message);
    }
  }
}
