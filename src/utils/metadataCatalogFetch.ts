import { API_BASE } from '../constants/api';
import type { InstalledAddon } from '../context/AddonContext';
import { getMobileClientIdentityHeaders } from './clientIdentity';
import { loadStoredAuthSession } from '../lib/authClient';
import { mapAddonCatalogType } from './homeCatalogSections';
import { tmdbFetch } from './tmdbFetch';
import { getSharedCachedAsync } from './sharedDataCache';

const CINEMETA_BASE = 'https://v3-cinemeta.strem.io';
const METADATA_CATALOG_TTL_MS = 30_000;

export interface MetadataCatalogItem {
  id: string;
  tmdbId?: number | null;
  imdbId?: string | null;
  type: 'movie' | 'tv' | 'sport';
  title: string;
  year?: number;
  poster?: string | null;
  backdrop?: string | null;
  titleLogo?: string | null;
  rating?: number;
  description?: string;
  runtime?: number | null;
  /** Stremio-native type to use when requesting streams for live addon items (e.g. 'tv', 'events'). */
  addonStreamType?: string;
}

export interface MetadataCatalogResponse {
  results: MetadataCatalogItem[];
  total_pages?: number;
}

type AddonCatalogDescriptor = {
  addonId: string;
  type: 'movie' | 'tv' | 'sport';
  catalogId: string;
  skip?: number;
  baseUrl?: string;
  /** Stremio-native catalog type ('series', 'tv', 'events', …) used to build addon URLs. */
  addonType?: string;
};

function parseRuntimeMinutes(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) return Math.round(value);
  if (typeof value !== 'string') return null;
  const match = value.match(/(\d+)/);
  if (!match) return null;
  const minutes = Number(match[1]);
  return Number.isFinite(minutes) && minutes > 0 ? minutes : null;
}

function parseYear(item: any): number | undefined {
  const raw = item?.year ?? item?.releaseInfo ?? item?.released ?? '';
  const year = Number(String(raw).slice(0, 4));
  return Number.isFinite(year) && year > 1800 ? year : undefined;
}

function normalizeCinemetaItem(item: any): MetadataCatalogItem {
  const tmdbId = Number(item?.moviedb_id);
  const fallbackId = typeof item?.id === 'string' ? item.id : '';
  const resolvedId = Number.isFinite(tmdbId) && tmdbId > 0 ? String(tmdbId) : fallbackId;
  const rawRating = Number(item?.imdbRating);

  return {
    id: resolvedId,
    tmdbId: Number.isFinite(tmdbId) ? tmdbId : null,
    imdbId: typeof item?.imdb_id === 'string' ? item.imdb_id : (fallbackId.startsWith('tt') ? fallbackId : null),
    type: item?.type === 'series' ? 'tv' : 'movie',
    title: item?.name ?? '',
    year: parseYear(item),
    poster: item?.poster ?? null,
    backdrop: item?.background ?? null,
    titleLogo: item?.logo ?? null,
    rating: Number.isFinite(rawRating) ? rawRating : 0,
    description: item?.description ?? '',
    runtime: parseRuntimeMinutes(item?.runtime),
  };
}

function normalizeAddonCatalogItem(
  item: any,
  fallbackType: 'movie' | 'tv' | 'sport',
  fallbackNativeType?: string,
): MetadataCatalogItem {
  const rawNativeType = String(item?.type ?? '').toLowerCase();
  const mappedType = rawNativeType ? mapAddonCatalogType(rawNativeType) : null;
  const type = mappedType ?? fallbackType;
  const nativeType = mappedType ? rawNativeType : (fallbackNativeType ?? '').toLowerCase();
  const tmdbId = Number(item?.moviedb_id ?? item?.tmdbId);
  const rawId = typeof item?.id === 'string' ? item.id : String(item?.id ?? '');
  const imdbId = typeof item?.imdb_id === 'string'
    ? item.imdb_id
    : typeof item?.imdbId === 'string'
      ? item.imdbId
      : rawId.startsWith('tt')
        ? rawId
        : null;
  const fallbackId = Number.isFinite(tmdbId) && tmdbId > 0
    ? String(tmdbId)
    : rawId;
  const rawRating = Number(item?.imdbRating ?? item?.rating);

  return {
    id: fallbackId,
    tmdbId: Number.isFinite(tmdbId) && tmdbId > 0 ? tmdbId : null,
    imdbId,
    type,
    title: item?.name ?? item?.title ?? '',
    year: parseYear(item),
    poster: item?.poster ?? (item?.posterShape === 'landscape' ? item?.background ?? null : null),
    backdrop: item?.background ?? null,
    titleLogo: item?.logo ?? null,
    rating: Number.isFinite(rawRating) ? rawRating : 0,
    description: item?.description ?? '',
    runtime: parseRuntimeMinutes(item?.runtime),
    addonStreamType: type === 'sport' ? (nativeType || 'sport') : undefined,
  };
}

function parseAddonCatalogEndpoint(endpoint: string): AddonCatalogDescriptor | null {
  if (!endpoint.startsWith('addon://')) return null;
  try {
    const parsed = new URL(endpoint);
    const addonId = decodeURIComponent(parsed.hostname);
    const [typeSegment, catalogSegment] = parsed.pathname.replace(/^\/+/, '').split('/');
    const type = decodeURIComponent(typeSegment ?? '') as 'movie' | 'tv' | 'sport';
    const catalogId = decodeURIComponent(catalogSegment ?? '');
    if (!addonId || !catalogId || (type !== 'movie' && type !== 'tv' && type !== 'sport')) return null;
    const skip = Number(parsed.searchParams.get('skip'));
    const rawAddonType = (parsed.searchParams.get('addonType') ?? '').trim().toLowerCase();
    return {
      addonId,
      type,
      catalogId,
      skip: Number.isFinite(skip) && skip > 0 ? skip : undefined,
      addonType: /^[a-z][a-z0-9_-]*$/.test(rawAddonType) ? rawAddonType : undefined,
      baseUrl: (() => {
        const baseUrl = parsed.searchParams.get('transport') ?? parsed.searchParams.get('baseUrl') ?? parsed.searchParams.get('manifestUrl');
        if (typeof baseUrl !== 'string') return undefined;
        const trimmed = baseUrl.trim();
        if (!/^https?:\/\//i.test(trimmed)) return undefined;
        return trimmed.replace(/\/manifest\.json.*$/i, '').replace(/\/+$/, '');
      })(),
    };
  } catch {
    return null;
  }
}

function resolveAddonBaseUrl(addon: InstalledAddon | null | undefined, baseUrlHint?: string | null): string | null {
  if (typeof baseUrlHint === 'string') {
    const trimmed = baseUrlHint.trim();
    if (/^https?:\/\//i.test(trimmed)) {
      return trimmed.replace(/\/manifest\.json.*$/i, '').replace(/\/+$/, '');
    }
  }

  const manifest = addon?.manifest as Record<string, any> | undefined;
  const candidates = [
    (addon as Record<string, any> | undefined)?.transportUrl,
    (addon as Record<string, any> | undefined)?.baseUrl,
    (addon as Record<string, any> | undefined)?.manifestUrl,
    (addon as Record<string, any> | undefined)?.url,
    manifest?.transportUrl,
    manifest?.baseUrl,
    manifest?.manifestUrl,
    manifest?.url,
    manifest?.origin,
  ];

  for (const candidate of candidates) {
    if (typeof candidate !== 'string') continue;
    const trimmed = candidate.trim();
    if (!/^https?:\/\//i.test(trimmed)) continue;
    return trimmed.replace(/\/manifest\.json.*$/i, '').replace(/\/+$/, '');
  }

  return null;
}

async function fetchAddonCatalogDirect(
  addon: InstalledAddon,
  descriptor: AddonCatalogDescriptor,
  options?: { signal?: AbortSignal },
): Promise<MetadataCatalogResponse> {
  const baseUrl = resolveAddonBaseUrl(addon, descriptor.baseUrl);
  if (!baseUrl) throw new Error('Addon base URL unavailable');

  const extra = new URLSearchParams();
  if (descriptor.skip) extra.set('skip', String(descriptor.skip));

  const query = extra.toString();
  const catalogType = descriptor.addonType ?? (descriptor.type === 'tv' ? 'series' : descriptor.type);
  const url = `${baseUrl}/catalog/${catalogType}/${encodeURIComponent(descriptor.catalogId)}${query ? `/${query}` : ''}.json`;
  const response = await fetch(url, options?.signal ? { signal: options.signal } : undefined);
  if (!response.ok) throw new Error(`Addon catalog fetch failed: ${response.status}`);
  const data = await response.json();
  const metas = Array.isArray(data?.metas) ? data.metas : [];

  return {
    results: metas
      .map((item: any) => normalizeAddonCatalogItem(item, descriptor.type, descriptor.addonType))
      .filter((item: MetadataCatalogItem) => item.id.length > 0),
    total_pages: data?.hasMore ? 2 : 1,
  };
}

async function fetchAddonCatalogViaBackend(
  descriptor: AddonCatalogDescriptor,
  options?: { signal?: AbortSignal },
): Promise<MetadataCatalogResponse> {
  const headers: Record<string, string> = { ...(await getMobileClientIdentityHeaders()) };
  // The backend scopes addons to the signed-in user; without auth it falls
  // back to the device scope and won't find an account's installed addons.
  const session = await loadStoredAuthSession().catch(() => null);
  if (session?.user?.accessToken) {
    headers.Authorization = `Bearer ${session.user.accessToken}`;
    headers['x-user-id'] = session.user.uid;
  }

  const catalogType = descriptor.addonType ?? (descriptor.type === 'tv' ? 'series' : descriptor.type);
  const suffix = descriptor.skip ? `?skip=${descriptor.skip}` : '';
  const path = `/addons/${encodeURIComponent(descriptor.addonId)}/catalog/${encodeURIComponent(catalogType)}/${encodeURIComponent(descriptor.catalogId)}${suffix}`;

  const response = await fetch(`${API_BASE}${path}`, {
    headers,
    signal: options?.signal,
  });
  if (!response.ok) throw new Error(`Addon catalog proxy failed: ${response.status}`);

  const data = await response.json();
  const metas = Array.isArray(data?.metas) ? data.metas : Array.isArray(data?.results) ? data.results : [];
  return {
    results: metas
      .map((item: any) => normalizeAddonCatalogItem(item, descriptor.type, descriptor.addonType))
      .filter((item: MetadataCatalogItem) => item.id.length > 0),
    total_pages: data?.total_pages ?? (data?.hasMore ? 2 : 1),
  };
}

function buildCinemetaUrl(endpoint: string): string {
  const trimmed = endpoint.replace(/^\/cinemeta\//, '').replace(/^\//, '');
  const [path, query = ''] = trimmed.split('?');
  const params = new URLSearchParams(query);
  const extraArgs = params.toString();

  if (!extraArgs) return `${CINEMETA_BASE}/${path}.json`;
  return `${CINEMETA_BASE}/${path}/${extraArgs}.json`;
}

export async function fetchMetadataCatalog(
  endpoint: string,
  options?: { signal?: AbortSignal; addon?: InstalledAddon | null },
): Promise<MetadataCatalogResponse> {
  const addonDescriptor = parseAddonCatalogEndpoint(endpoint);
  const addon = options?.addon ?? null;

  if (addonDescriptor) {
    const loadAddonCatalog = async () => {
      if (addon) {
        try {
          return await fetchAddonCatalogDirect(addon, addonDescriptor, options);
        } catch {
          if (addonDescriptor.baseUrl) {
            return fetchAddonCatalogDirect({
              ...addon,
              baseUrl: addonDescriptor.baseUrl,
              transportUrl: addonDescriptor.baseUrl,
            }, addonDescriptor, options);
          }
          return fetchAddonCatalogViaBackend(addonDescriptor, options);
        }
      }
      if (addonDescriptor.baseUrl) {
        return fetchAddonCatalogDirect({
          id: addonDescriptor.addonId,
          enabled: true,
          position: 0,
          baseUrl: addonDescriptor.baseUrl,
          transportUrl: addonDescriptor.baseUrl,
          manifest: {
            id: addonDescriptor.addonId,
            name: addonDescriptor.addonId,
            version: '0',
            resources: [],
            types: [addonDescriptor.type],
            catalogs: [],
          },
        }, addonDescriptor, options);
      }
      return fetchAddonCatalogViaBackend(addonDescriptor, options);
    };

    if (options?.signal) {
      return loadAddonCatalog();
    }

    return getSharedCachedAsync(
      `catalog:${endpoint}`,
      METADATA_CATALOG_TTL_MS,
      loadAddonCatalog,
    );
  }

  if (options?.signal) {
    if (!endpoint.startsWith('/cinemeta/')) {
      const response = await tmdbFetch(endpoint, { signal: options.signal });
      if (!response.ok) throw new Error('TMDB catalog fetch failed');
      const data = await response.json();
      return {
        results: data?.results ?? [],
        total_pages: data?.total_pages,
      };
    }

    const response = await fetch(buildCinemetaUrl(endpoint), { signal: options.signal });
    if (!response.ok) throw new Error(`Cinemeta catalog fetch failed: ${response.status}`);
    const data = await response.json();
    const metas = Array.isArray(data?.metas) ? data.metas : [];

    return {
      results: metas.map(normalizeCinemetaItem).filter((item: MetadataCatalogItem) => item.id.length > 0),
      total_pages: data?.hasMore ? 2 : 1,
    };
  }

  return getSharedCachedAsync(
    `catalog:${endpoint}`,
    METADATA_CATALOG_TTL_MS,
    async () => {
      let result: MetadataCatalogResponse;

      if (!endpoint.startsWith('/cinemeta/')) {
        const response = await tmdbFetch(endpoint);
        if (!response.ok) throw new Error('TMDB catalog fetch failed');
        const data = await response.json();
        result = {
          results: data?.results ?? [],
          total_pages: data?.total_pages,
        };
      } else {
        const response = await fetch(buildCinemetaUrl(endpoint));
        if (!response.ok) throw new Error(`Cinemeta catalog fetch failed: ${response.status}`);
        const data = await response.json();
        const metas = Array.isArray(data?.metas) ? data.metas : [];

        result = {
          results: metas.map(normalizeCinemetaItem).filter((item: MetadataCatalogItem) => item.id.length > 0),
          total_pages: data?.hasMore ? 2 : 1,
        };
      }

      return result;
    },
  );
}
