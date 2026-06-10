import { API_BASE } from '../constants/api';
import type { InstalledAddon } from '../context/AddonContext';
import { getMobileClientIdentityHeaders } from './clientIdentity';
import { tmdbFetch } from './tmdbFetch';
import { getSharedCachedAsync } from './sharedDataCache';

const CINEMETA_BASE = 'https://v3-cinemeta.strem.io';
const METADATA_CATALOG_TTL_MS = 30_000;

export interface MetadataCatalogItem {
  id: string;
  tmdbId?: number | null;
  imdbId?: string | null;
  type: 'movie' | 'tv';
  title: string;
  year?: number;
  poster?: string | null;
  backdrop?: string | null;
  titleLogo?: string | null;
  rating?: number;
  description?: string;
  runtime?: number | null;
}

export interface MetadataCatalogResponse {
  results: MetadataCatalogItem[];
  total_pages?: number;
}

type AddonCatalogDescriptor = {
  addonId: string;
  type: 'movie' | 'tv';
  catalogId: string;
  skip?: number;
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

function normalizeAddonCatalogItem(item: any, fallbackType: 'movie' | 'tv'): MetadataCatalogItem {
  const rawType = String(item?.type ?? fallbackType).toLowerCase();
  const type = rawType === 'series' ? 'tv' : rawType === 'tv' ? 'tv' : 'movie';
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
  };
}

function parseAddonCatalogEndpoint(endpoint: string): AddonCatalogDescriptor | null {
  if (!endpoint.startsWith('addon://')) return null;
  try {
    const parsed = new URL(endpoint);
    const addonId = decodeURIComponent(parsed.hostname);
    const [typeSegment, catalogSegment] = parsed.pathname.replace(/^\/+/, '').split('/');
    const type = decodeURIComponent(typeSegment ?? '') as 'movie' | 'tv';
    const catalogId = decodeURIComponent(catalogSegment ?? '');
    if (!addonId || !catalogId || (type !== 'movie' && type !== 'tv')) return null;
    const skip = Number(parsed.searchParams.get('skip'));
    return {
      addonId,
      type,
      catalogId,
      skip: Number.isFinite(skip) && skip > 0 ? skip : undefined,
    };
  } catch {
    return null;
  }
}

function resolveAddonBaseUrl(addon: InstalledAddon | null | undefined): string | null {
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
  const baseUrl = resolveAddonBaseUrl(addon);
  if (!baseUrl) throw new Error('Addon base URL unavailable');

  const extra = new URLSearchParams();
  if (descriptor.skip) extra.set('skip', String(descriptor.skip));

  const query = extra.toString();
  const url = `${baseUrl}/catalog/${descriptor.type === 'tv' ? 'series' : descriptor.type}/${encodeURIComponent(descriptor.catalogId)}${query ? `/${query}` : ''}.json`;
  const response = await fetch(url, options?.signal ? { signal: options.signal } : undefined);
  if (!response.ok) throw new Error(`Addon catalog fetch failed: ${response.status}`);
  const data = await response.json();
  const metas = Array.isArray(data?.metas) ? data.metas : [];

  return {
    results: metas
      .map((item: any) => normalizeAddonCatalogItem(item, descriptor.type))
      .filter((item: MetadataCatalogItem) => item.id.length > 0),
    total_pages: data?.hasMore ? 2 : 1,
  };
}

async function fetchAddonCatalogViaBackend(
  descriptor: AddonCatalogDescriptor,
  options?: { signal?: AbortSignal },
): Promise<MetadataCatalogResponse> {
  const headers = await getMobileClientIdentityHeaders();
  const suffix = descriptor.skip ? `?skip=${descriptor.skip}` : '';
  const candidatePaths = [
    `/addons/catalog/single/${encodeURIComponent(descriptor.addonId)}/${descriptor.type}/${encodeURIComponent(descriptor.catalogId)}${suffix}`,
    `/addons/catalogs/single/${encodeURIComponent(descriptor.addonId)}/${descriptor.type}/${encodeURIComponent(descriptor.catalogId)}${suffix}`,
    `/addons/catalog/${encodeURIComponent(descriptor.addonId)}/${descriptor.type}/${encodeURIComponent(descriptor.catalogId)}${suffix}`,
  ];

  for (const path of candidatePaths) {
    const response = await fetch(`${API_BASE}${path}`, {
      headers,
      signal: options?.signal,
    }).catch(() => null);

    if (!response || response.status === 404) continue;
    if (!response.ok) throw new Error(`Addon catalog proxy failed: ${response.status}`);

    const data = await response.json();
    const metas = Array.isArray(data?.metas) ? data.metas : Array.isArray(data?.results) ? data.results : [];
    return {
      results: metas
        .map((item: any) => normalizeAddonCatalogItem(item, descriptor.type))
        .filter((item: MetadataCatalogItem) => item.id.length > 0),
      total_pages: data?.total_pages ?? (data?.hasMore ? 2 : 1),
    };
  }

  throw new Error('Addon catalog endpoint unavailable');
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
          return fetchAddonCatalogViaBackend(addonDescriptor, options);
        }
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
