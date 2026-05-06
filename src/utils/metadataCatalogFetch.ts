import { tmdbFetch } from './tmdbFetch';

const CINEMETA_BASE = 'https://v3-cinemeta.strem.io';

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

function buildCinemetaUrl(endpoint: string): string {
  const trimmed = endpoint.replace(/^\/cinemeta\//, '').replace(/^\//, '');
  const [path, query = ''] = trimmed.split('?');
  const params = new URLSearchParams(query);
  const extraArgs = params.toString();

  if (!extraArgs) return `${CINEMETA_BASE}/${path}.json`;
  return `${CINEMETA_BASE}/${path}/${extraArgs}.json`;
}

export async function fetchMetadataCatalog(endpoint: string): Promise<MetadataCatalogResponse> {
  if (!endpoint.startsWith('/cinemeta/')) {
    const response = await tmdbFetch(endpoint);
    if (!response.ok) throw new Error('TMDB catalog fetch failed');
    const data = await response.json();
    return {
      results: data?.results ?? [],
      total_pages: data?.total_pages,
    };
  }

  const response = await fetch(buildCinemetaUrl(endpoint));
  if (!response.ok) throw new Error(`Cinemeta catalog fetch failed: ${response.status}`);
  const data = await response.json();
  const metas = Array.isArray(data?.metas) ? data.metas : [];

  return {
    results: metas.map(normalizeCinemetaItem).filter((item: MetadataCatalogItem) => item.id.length > 0),
    total_pages: data?.hasMore ? 2 : 1,
  };
}
