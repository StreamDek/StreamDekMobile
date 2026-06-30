import { API_BASE } from '../constants/api';
import { fetchMetadataCatalog } from './metadataCatalogFetch';
import { tmdbFetch } from './tmdbFetch';
import type { InstalledAddon } from '../context/AddonContext';
import type { HomeCatalogSection } from './homeCatalogSections';

export type CollectionCatalogSource = {
  addonId: string;
  type: string;
  catalogId: string;
  genre?: string | null;
};

export type TmdbCollectionFilters = {
  withGenres?: string | null;
  releaseDateGte?: string | null;
  releaseDateLte?: string | null;
  voteAverageGte?: number | null;
  voteAverageLte?: number | null;
  voteCountGte?: number | null;
  withOriginalLanguage?: string | null;
  withOriginCountry?: string | null;
  withKeywords?: string | null;
  withCompanies?: string | null;
  withNetworks?: string | null;
  year?: number | null;
  watchRegion?: string | null;
  withWatchProviders?: string | null;
};

export type CollectionSource = {
  provider?: string;
  addonId?: string | null;
  type?: string | null;
  catalogId?: string | null;
  genre?: string | null;
  tmdbSourceType?: string | null;
  title?: string | null;
  tmdbId?: number | string | null;
  traktListId?: number | string | null;
  mediaType?: string | null;
  sortBy?: string | null;
  sortHow?: string | null;
  filters?: TmdbCollectionFilters | null;
};

export type CollectionFolder = {
  id: string;
  title: string;
  coverImageUrl?: string | null;
  coverEmoji?: string | null;
  tileShape?: string;
  hideTitle?: boolean;
  sources?: CollectionSource[];
  catalogSources?: CollectionCatalogSource[];
};

export type Collection = {
  id: string;
  title: string;
  backdropImageUrl?: string | null;
  pinToTop?: boolean;
  viewMode?: string;
  showAllTab?: boolean;
  folders: CollectionFolder[];
};

export type CollectionValidationResult = {
  valid: boolean;
  error?: string;
  collectionCount: number;
  folderCount: number;
};

export const COLLECTIONS_STORAGE_KEY = 'streamdek_collections';

function parsePositiveInteger(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    return Math.trunc(value);
  }
  const normalized = String(value ?? '').trim();
  if (!/^\d+$/.test(normalized)) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function slugifyCollectionToken(value: unknown, fallback: string): string {
  const normalized = String(value ?? '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return normalized || fallback;
}

function buildUniqueCollectionId(baseId: string, seen: Set<string>): string {
  let candidate = baseId;
  let suffix = 2;
  while (seen.has(candidate)) {
    candidate = `${baseId}-${suffix}`;
    suffix += 1;
  }
  seen.add(candidate);
  return candidate;
}

export function normalizeCollectionSource(source: CollectionSource): CollectionSource {
  return {
    provider: source.provider ?? 'addon',
    addonId: source.addonId ?? null,
    type: source.type ?? null,
    catalogId: source.catalogId ?? null,
    genre: source.genre ?? null,
    tmdbSourceType: source.tmdbSourceType ?? null,
    title: source.title ?? null,
    tmdbId: parsePositiveInteger(source.tmdbId),
    traktListId: parsePositiveInteger(source.traktListId),
    mediaType: source.mediaType ?? null,
    sortBy: source.sortBy ?? null,
    sortHow: source.sortHow ?? null,
    filters: source.filters ?? null,
  };
}

function normalizeCatalogSource(source: CollectionCatalogSource): CollectionCatalogSource {
  return {
    addonId: String(source?.addonId ?? '').trim(),
    type: String(source?.type ?? '').trim(),
    catalogId: String(source?.catalogId ?? '').trim(),
    genre: source?.genre ?? null,
  };
}

export function normalizeImportedCollections(raw: unknown): Collection[] {
  if (!Array.isArray(raw)) return [];

  const collectionIds = new Set<string>();
  return raw.map((item, collectionIndex) => {
    const collection = item as Collection;
    const collectionTitle = String((collection as any)?.title ?? (collection as any)?.name ?? '').trim() || `Collection ${collectionIndex + 1}`;
    const collectionId = buildUniqueCollectionId(
      slugifyCollectionToken((collection as any)?.id ?? collectionTitle, `collection-${collectionIndex + 1}`),
      collectionIds,
    );

    const folderIds = new Set<string>();
    const folders = Array.isArray(collection?.folders)
      ? collection.folders.map((folder, folderIndex) => {
          const currentFolder = folder as CollectionFolder;
          const folderTitle = String((currentFolder as any)?.title ?? (currentFolder as any)?.name ?? '').trim() || `Folder ${folderIndex + 1}`;
          const folderId = buildUniqueCollectionId(
            slugifyCollectionToken((currentFolder as any)?.id ?? folderTitle, `folder-${folderIndex + 1}`),
            folderIds,
          );

          return {
            ...currentFolder,
            id: folderId,
            title: folderTitle,
            sources: Array.isArray(currentFolder.sources)
              ? currentFolder.sources.map(source => normalizeCollectionSource(source as CollectionSource))
              : currentFolder.sources,
            catalogSources: Array.isArray(currentFolder.catalogSources)
              ? currentFolder.catalogSources.map(source => normalizeCatalogSource(source as CollectionCatalogSource))
              : currentFolder.catalogSources,
          };
        })
      : [];

    return {
      ...collection,
      id: collectionId,
      title: collectionTitle,
      folders,
    };
  });
}

export function resolveCollectionFolderSources(folder: CollectionFolder): CollectionSource[] {
  if (Array.isArray(folder.sources) && folder.sources.length > 0) {
    return folder.sources.map(normalizeCollectionSource);
  }
  return (folder.catalogSources ?? []).map(source => ({
    provider: 'addon',
    addonId: source.addonId,
    type: source.type,
    catalogId: source.catalogId,
    genre: source.genre ?? null,
  }));
}

export function validateCollectionsImport(jsonString: string): CollectionValidationResult {
  if (!jsonString.trim()) {
    return { valid: false, error: 'Paste collection JSON to import.', collectionCount: 0, folderCount: 0 };
  }

  try {
    const parsed = JSON.parse(jsonString);
    if (!Array.isArray(parsed)) {
      return { valid: false, error: 'Collections JSON must be an array.', collectionCount: 0, folderCount: 0 };
    }

    const normalized = normalizeImportedCollections(parsed);
    let folderCount = 0;

    for (const collection of normalized) {
      for (const folder of collection.folders) {
        folderCount += 1;
        const folderLabel = folder.title || folder.id;
        const sources = resolveCollectionFolderSources(folder);
        for (const [sourceIndex, source] of sources.entries()) {
          const provider = String(source.provider ?? 'addon').toLowerCase();
          if (provider === 'addon') {
            if (!source.addonId || !source.type || !source.catalogId) {
              return { valid: false, error: `Folder "${folderLabel}" has an addon source missing addon/type/catalog fields at index ${sourceIndex + 1}.`, collectionCount: 0, folderCount: 0 };
            }
          } else if (provider === 'tmdb') {
            if (!source.tmdbSourceType) {
              return { valid: false, error: `Folder "${folderLabel}" has a TMDB source missing tmdbSourceType at index ${sourceIndex + 1}.`, collectionCount: 0, folderCount: 0 };
            }
          } else if (provider === 'trakt') {
            if (!parsePositiveInteger(source.traktListId)) {
              return { valid: false, error: `Folder "${folderLabel}" has a Trakt source missing a numeric traktListId at index ${sourceIndex + 1}.`, collectionCount: 0, folderCount: 0 };
            }
          } else {
            return { valid: false, error: `Unsupported collection source provider "${provider}" in "${folderLabel}".`, collectionCount: 0, folderCount: 0 };
          }
        }
      }
    }

    return { valid: true, collectionCount: normalized.length, folderCount };
  } catch (error) {
    return {
      valid: false,
      error: error instanceof Error ? error.message : 'Invalid collections JSON.',
      collectionCount: 0,
      folderCount: 0,
    };
  }
}

export function buildCollectionHomeSections(collections: Collection[]): HomeCatalogSection[] {
  return collections.flatMap(collection =>
    (collection.folders ?? []).map<HomeCatalogSection>(folder => ({
      id: `collection:${collection.id}:${folder.id}`,
      title: folder.title || collection.title,
      endpoint: `collection://${encodeURIComponent(collection.id)}/${encodeURIComponent(folder.id)}`,
      enabled: true,
      source: 'collection',
      collectionTitle: collection.title,
      folderTitle: folder.title,
      contentType: inferFolderContentType(folder),
    })),
  );
}

export function parseCollectionEndpoint(endpoint: string): { collectionId: string; folderId: string } | null {
  if (!endpoint.startsWith('collection://')) return null;
  const raw = endpoint.replace('collection://', '');
  const [collectionPart, folderPart] = raw.split('/');
  if (!collectionPart || !folderPart) return null;
  return {
    collectionId: decodeURIComponent(collectionPart),
    folderId: decodeURIComponent(folderPart),
  };
}

export function findCollectionFolder(
  collections: Collection[],
  collectionId: string,
  folderId: string,
): { collection: Collection; folder: CollectionFolder } | null {
  const collection = collections.find(candidate => candidate.id === collectionId);
  if (!collection) return null;
  const folder = collection.folders.find(candidate => candidate.id === folderId);
  if (!folder) return null;
  return { collection, folder };
}

export async function fetchCollectionFolderItems(
  folder: CollectionFolder,
  addons: InstalledAddon[],
): Promise<any[]> {
  const sources = resolveCollectionFolderSources(folder);
  const batches = await Promise.all(
    sources.map(async source => {
      try {
        const provider = String(source.provider ?? 'addon').toLowerCase();
        if (provider === 'addon') {
          const endpoint = buildAddonCollectionEndpoint(source);
          if (!endpoint) return [];
          const addon = addons.find(candidate => candidate.id === source.addonId) ?? null;
          const data = await fetchMetadataCatalog(endpoint, { addon });
          return Array.isArray(data?.results) ? data.results : [];
        }
        if (provider === 'tmdb') {
          const endpoint = buildTmdbCollectionEndpoint(source);
          if (!endpoint) return [];
          const response = await tmdbFetch(endpoint);
          if (!response.ok) return [];
          const data = await response.json();
          return Array.isArray(data?.results) ? data.results : [];
        }
        if (provider === 'trakt') {
          return await fetchTraktCollectionItems(source);
        }
        return [];
      } catch {
        return [];
      }
    }),
  );

  const deduped = new Map<string, any>();
  for (const batch of batches) {
    for (const item of batch) {
      const key = `${String(item?.type ?? 'movie')}:${String(item?.id ?? '')}`;
      if (!item?.id || deduped.has(key)) continue;
      deduped.set(key, item);
    }
  }
  return Array.from(deduped.values());
}

function inferFolderContentType(folder: CollectionFolder): HomeCatalogSection['contentType'] {
  const types = new Set(
    resolveCollectionFolderSources(folder)
      .map(source => normalizeMediaType(source.mediaType ?? source.type))
      .filter((value): value is 'movie' | 'tv' => value === 'movie' || value === 'tv'),
  );
  if (types.size === 1) return Array.from(types)[0];
  return 'mixed';
}

function normalizeMediaType(value: string | null | undefined): 'movie' | 'tv' | 'mixed' {
  const normalized = String(value ?? '').trim().toLowerCase();
  if (normalized === 'tv' || normalized === 'series' || normalized === 'show') return 'tv';
  if (normalized === 'movie') return 'movie';
  return 'mixed';
}

function buildAddonCollectionEndpoint(source: CollectionSource): string | null {
  const addonId = String(source.addonId ?? '').trim();
  const catalogId = String(source.catalogId ?? '').trim();
  const rawType = String(source.type ?? '').trim().toLowerCase();
  if (!addonId || !catalogId || !rawType) return null;
  const mappedType = rawType === 'series' ? 'tv' : rawType === 'movie' ? 'movie' : rawType;
  const params = new URLSearchParams();
  if (source.genre) params.set('genre', source.genre);
  return `addon://${encodeURIComponent(addonId)}/${encodeURIComponent(mappedType)}/${encodeURIComponent(catalogId)}${params.size > 0 ? `?${params.toString()}` : ''}`;
}

function buildTmdbCollectionEndpoint(source: CollectionSource): string | null {
  const tmdbSourceType = String(source.tmdbSourceType ?? '').trim().toUpperCase();
  const mediaType = normalizeMediaType(source.mediaType);
  const tmdbId = Number(source.tmdbId ?? 0);
  const sortBy = String(source.sortBy ?? '').trim() || defaultSortBy(mediaType);
  const params = new URLSearchParams();

  switch (tmdbSourceType) {
    case 'DISCOVER':
      params.set('type', mediaType === 'mixed' ? 'movie' : mediaType);
      params.set('sort_by', sortBy);
      appendTmdbFilters(params, source.filters ?? null, mediaType === 'mixed' ? 'movie' : mediaType);
      return `/tmdb/discover?${params.toString()}`;
    case 'LIST':
      if (tmdbId <= 0) return null;
      params.set('type', mediaType === 'mixed' ? 'movie' : mediaType);
      return `/tmdb/list/${tmdbId}?${params.toString()}`;
    case 'COLLECTION':
      if (tmdbId <= 0) return null;
      return `/tmdb/collection/${tmdbId}`;
    case 'COMPANY':
      if (tmdbId <= 0) return null;
      params.set('type', mediaType === 'mixed' ? 'movie' : mediaType);
      return `/tmdb/company/${tmdbId}?${params.toString()}`;
    case 'NETWORK':
      if (tmdbId <= 0) return null;
      return `/tmdb/network/${tmdbId}`;
    case 'PERSON':
      if (tmdbId <= 0) return null;
      params.set('type', mediaType === 'mixed' ? 'movie' : mediaType);
      return `/tmdb/person/${tmdbId}/credits?${params.toString()}`;
    case 'DIRECTOR':
      if (tmdbId <= 0) return null;
      params.set('type', mediaType === 'mixed' ? 'movie' : mediaType);
      return `/tmdb/director/${tmdbId}?${params.toString()}`;
    default:
      return null;
  }
}

async function fetchTraktCollectionItems(source: CollectionSource): Promise<any[]> {
  const traktListId = parsePositiveInteger(source.traktListId);
  if (!traktListId) return [];

  const mediaType = normalizeMediaType(source.mediaType);
  const traktTypes = mediaType === 'mixed'
    ? ['movie', 'show'] as const
    : [mediaType === 'tv' ? 'show' : 'movie' as const];

  const batches = await Promise.all(
    traktTypes.map(async traktType => {
      const params = new URLSearchParams({
        sort_by: normalizeTraktSortBy(source.sortBy),
        sort_how: normalizeTraktSortHow(source.sortHow),
      });
      const response = await fetch(`${API_BASE}/trakt/lists/${traktListId}/items/${traktType}?${params.toString()}`);
      if (!response.ok) return [];
      const data = await response.json();
      return Array.isArray(data?.results) ? data.results : [];
    }),
  );

  return batches.flat();
}

function normalizeTraktSortBy(value: string | null | undefined): string {
  const normalized = String(value ?? '').trim().toLowerCase();
  const allowed = new Set(['rank', 'added', 'title', 'released', 'runtime', 'popularity', 'percentage', 'votes', 'my_rating', 'random']);
  return allowed.has(normalized) ? normalized : 'rank';
}

function normalizeTraktSortHow(value: string | null | undefined): string {
  const normalized = String(value ?? '').trim().toLowerCase();
  return normalized === 'desc' ? 'desc' : 'asc';
}

function defaultSortBy(mediaType: 'movie' | 'tv' | 'mixed'): string {
  return mediaType === 'tv' ? 'first_air_date.desc' : 'popularity.desc';
}

function appendTmdbFilters(
  params: URLSearchParams,
  filters: TmdbCollectionFilters | null,
  mediaType: 'movie' | 'tv',
): void {
  if (!filters) return;
  if (filters.withGenres) params.set('with_genres', filters.withGenres);
  if (filters.releaseDateGte) {
    params.set(mediaType === 'movie' ? 'primary_release_date.gte' : 'first_air_date.gte', filters.releaseDateGte);
  }
  if (filters.releaseDateLte) {
    params.set(mediaType === 'movie' ? 'primary_release_date.lte' : 'first_air_date.lte', filters.releaseDateLte);
  }
  if (typeof filters.voteAverageGte === 'number') params.set('vote_average.gte', String(filters.voteAverageGte));
  if (typeof filters.voteAverageLte === 'number') params.set('vote_average.lte', String(filters.voteAverageLte));
  if (typeof filters.voteCountGte === 'number') params.set('vote_count.gte', String(filters.voteCountGte));
  if (filters.withOriginalLanguage) params.set('with_original_language', filters.withOriginalLanguage);
  if (filters.withOriginCountry) params.set('with_origin_country', filters.withOriginCountry);
  if (filters.withKeywords) params.set('with_keywords', filters.withKeywords);
  if (filters.withCompanies) params.set('with_companies', filters.withCompanies);
  if (filters.withNetworks) params.set('with_networks', filters.withNetworks);
  if (typeof filters.year === 'number') params.set('year', String(filters.year));
  if (filters.watchRegion) params.set('watch_region', filters.watchRegion);
  if (filters.withWatchProviders) params.set('with_watch_providers', filters.withWatchProviders);
}
