import { API_BASE } from '../constants/api';
import { cachedFetch } from './apiCache';

// ── Active key (set by TmdbApiKeyContext on mount / change) ───────────────────
let _activeKey: string | null = null;

export function __setTmdbActiveKey(key: string | null) {
  _activeKey = key;
}

// ── Image helpers ─────────────────────────────────────────────────────────────
const TMDB_BASE = 'https://api.themoviedb.org/3';
const IMG_ORIG  = 'https://image.tmdb.org/t/p/original';
const IMG_W500  = 'https://image.tmdb.org/t/p/w500';

function img(path: string | null | undefined, size: 'original' | 'w500' = 'original'): string | null {
  if (!path) return null;
  return `${size === 'w500' ? IMG_W500 : IMG_ORIG}${path}`;
}

// ── List item normalizer ──────────────────────────────────────────────────────
function normalizeListItem(item: any, type: 'movie' | 'tv'): any {
  const mediaType = item.media_type === 'tv' ? 'tv' : item.media_type === 'movie' ? 'movie' : type;
  return {
    id:          String(item.id),
    tmdbId:      item.id,
    type:        mediaType,
    title:       item.title ?? item.name ?? '',
    year:        parseInt((item.release_date ?? item.first_air_date ?? '').slice(0, 4)) || undefined,
    poster:      img(item.poster_path, 'w500'),
    backdrop:    img(item.backdrop_path),
    rating:      Math.round((item.vote_average ?? 0) * 10) / 10,
    description: item.overview ?? '',
    adult:       item.adult === true,
  };
}

// ── Full detail normalizer ────────────────────────────────────────────────────
function normalizeDetails(data: any, type: 'movie' | 'tv'): any {
  const backdrops = (data.images?.backdrops ?? [])
    .slice(0, 10)
    .map((b: any) => img(b.file_path))
    .filter(Boolean) as string[];

  const logos: any[] = data.images?.logos ?? [];
  const titleLogoObj = logos.find((l: any) => l.iso_639_1 === 'en') ?? logos[0];

  const trailerKey =
    (data.videos?.results ?? []).find((v: any) => v.type === 'Trailer' && v.site === 'YouTube')?.key ??
    (data.videos?.results ?? []).find((v: any) => v.site === 'YouTube')?.key ??
    null;

  const cast = (data.credits?.cast ?? []).slice(0, 20).map((p: any) => ({
    id:        p.id,
    name:      p.name,
    character: p.character,
    photo:     img(p.profile_path, 'w500'),
  }));

  const crew = (data.credits?.crew ?? [])
    .filter((p: any) => ['Director', 'Writer', 'Screenplay', 'Creator', 'Executive Producer'].includes(p.job))
    .slice(0, 10)
    .map((p: any) => ({ id: p.id, name: p.name, job: p.job, photo: img(p.profile_path, 'w500') }));

  const providerData = (data['watch/providers']?.results?.US ?? data['watch/providers']?.results?.GB ?? {});
  const flatrate     = [...(providerData.flatrate ?? []), ...(providerData.subscription ?? [])];
  const streamingProviders = flatrate.map((p: any) => ({
    id:   p.provider_id,
    name: p.provider_name,
    logo: img(p.logo_path, 'w500'),
  }));
  const rentProviders = (providerData.rent ?? []).map((p: any) => ({
    id:   p.provider_id,
    name: p.provider_name,
    logo: img(p.logo_path, 'w500'),
  }));
  const providers = streamingProviders;

  const networks = type === 'tv'
    ? (data.networks ?? []).map((n: any) => ({ id: n.id, name: n.name, logo: img(n.logo_path, 'w500') }))
    : [];

  const productionCompanies = (data.production_companies ?? []).map((c: any) => ({
    id:   c.id,
    name: c.name,
    logo: img(c.logo_path, 'w500'),
  }));

  const genres     = (data.genres ?? []).map((g: any) => g.id);
  const genreNames = (data.genres ?? []).map((g: any) => g.name);

  const seasons = type === 'tv'
    ? (data.seasons ?? [])
        .filter((s: any) => s.season_number > 0)
        .map((s: any) => ({
          id:            s.id,
          season_number: s.season_number,
          name:          s.name,
          episode_count: s.episode_count,
          poster_path:   s.poster_path,
          poster:        img(s.poster_path, 'w500'),
          air_date:      s.air_date,
        }))
    : undefined;

  const releaseDate  = data.release_date ?? data.first_air_date ?? '';
  const isUnreleased = releaseDate ? new Date(releaseDate) > new Date() : false;
  const backdropUri  = img(data.backdrop_path);

  return {
    id:                   String(data.id),
    tmdbId:               data.id,
    type,
    title:                data.title ?? data.name ?? '',
    tagline:              data.tagline ?? '',
    description:          data.overview ?? '',
    year:                 parseInt(releaseDate.slice(0, 4)) || undefined,
    releaseDate:          type === 'movie' ? (data.release_date ?? null) : null,
    firstAirDate:         type === 'tv'    ? (data.first_air_date ?? null) : null,
    runtime:              type === 'movie' ? (data.runtime ?? null) : (data.episode_run_time?.[0] ?? null),
    rating:               Math.round((data.vote_average ?? 0) * 10) / 10,
    imdbId:               data.external_ids?.imdb_id ?? null,
    genres,
    genreNames,
    poster:               img(data.poster_path, 'w500'),
    backdrop:             backdropUri,
    backdrops:            backdrops.length > 0 ? backdrops : (backdropUri ? [backdropUri] : []),
    titleLogo:            titleLogoObj ? img(titleLogoObj.file_path) : null,
    trailerKey,
    cast,
    crew,
    providers,
    streamingProviders,
    rentProviders,
    networks,
    productionCompanies,
    seasons,
    numberOfSeasons:      type === 'tv' ? (data.number_of_seasons ?? 0) : undefined,
    status:               data.status ?? null,
    isUnreleased,
    adult:                data.adult === true,
  };
}

// ── Season normalizer ─────────────────────────────────────────────────────────
function normalizeSeason(data: any): any {
  return {
    id:            data.id,
    season_number: data.season_number,
    name:          data.name,
    overview:      data.overview,
    poster:        img(data.poster_path, 'w500'),
    episodes:      (data.episodes ?? []).map((ep: any) => ({
      id:             ep.id,
      episode_number: ep.episode_number,
      season_number:  ep.season_number,
      name:           ep.name,
      overview:       ep.overview,
      still:          img(ep.still_path, 'w500'),
      still_path:     ep.still_path,
      air_date:       ep.air_date,
      runtime:        ep.runtime ?? null,
    })),
  };
}

// ── Path → TMDB direct dispatch ───────────────────────────────────────────────
async function dispatchDirect(path: string, apiKey: string): Promise<any> {
  const base   = `api_key=${apiKey}&language=en-US`;
  const get    = async (url: string) => {
    const r = await fetch(url);
    if (!r.ok) throw new Error(`TMDB ${r.status}`);
    return r.json();
  };

  // /tmdb/details/(movie|tv)/123
  const detailsM = path.match(/^\/tmdb\/details\/(movie|tv)\/(\d+)$/);
  if (detailsM) {
    const [, type, id] = detailsM;
    const append = type === 'movie'
      ? 'images,videos,credits,watch%2Fproviders,external_ids,release_dates'
      : 'images,videos,credits,watch%2Fproviders,external_ids,content_ratings';
    const [data, similar, recommendations] = await Promise.all([
      get(
        `${TMDB_BASE}/${type}/${id}?${base}&append_to_response=${append}&include_image_language=en,null`,
      ),
      get(`${TMDB_BASE}/${type}/${id}/similar?${base}`).catch(() => ({ results: [] })),
      get(`${TMDB_BASE}/${type}/${id}/recommendations?${base}`).catch(() => ({ results: [] })),
    ]);
    const normalized = normalizeDetails(data, type as 'movie' | 'tv');
    return {
      ...normalized,
      similarTitles: [
        ...(recommendations.results ?? []),
        ...(similar.results ?? []),
      ]
        .map((item: any) => normalizeListItem(item, type as 'movie' | 'tv'))
        .filter((item: any, index: number, items: any[]) =>
          item.id !== String(id) && items.findIndex((candidate: any) => candidate.id === item.id) === index)
        .slice(0, 20),
    };
  }

  // /tmdb/season/123/1
  const seasonM = path.match(/^\/tmdb\/season\/(\d+)\/(\d+)$/);
  if (seasonM) {
    const [, tvId, num] = seasonM;
    const data = await get(`${TMDB_BASE}/tv/${tvId}/season/${num}?${base}&append_to_response=images`);
    return normalizeSeason(data);
  }

  // /tmdb/trending/(movie|tv)
  const trendingM = path.match(/^\/tmdb\/trending\/(movie|tv)$/);
  if (trendingM) {
    const type = trendingM[1] as 'movie' | 'tv';
    const data = await get(`${TMDB_BASE}/trending/${type}/week?${base}`);
    return { results: (data.results ?? []).map((i: any) => normalizeListItem(i, type)) };
  }

  // /tmdb/popular/(movie|tv)
  const popularM = path.match(/^\/tmdb\/popular\/(movie|tv)$/);
  if (popularM) {
    const type = popularM[1] as 'movie' | 'tv';
    const data = await get(`${TMDB_BASE}/${type}/popular?${base}`);
    return { results: (data.results ?? []).map((i: any) => normalizeListItem(i, type)) };
  }

  // /tmdb/discover?type=movie|tv&...
  if (path.startsWith('/tmdb/discover')) {
    const qs      = path.includes('?') ? path.slice(path.indexOf('?') + 1) : '';
    const params  = new URLSearchParams(qs);
    const type    = (params.get('type') ?? 'movie') as 'movie' | 'tv';
    const genreId = params.get('genre_id') ?? params.get('with_genres');
    const sortBy  = params.get('sort_by') ?? 'popularity.desc';
    const page    = params.get('page') ?? '1';
    const year    = params.get('year');
    const relLte  = params.get('primary_release_date.lte');
    const airLte  = params.get('first_air_date.lte');
    let url = `${TMDB_BASE}/discover/${type}?${base}&sort_by=${sortBy}&page=${page}`;
    if (genreId) url += `&with_genres=${genreId}`;
    if (year)   url += type === 'movie' ? `&primary_release_year=${year}` : `&first_air_date_year=${year}`;
    if (relLte) url += `&primary_release_date.lte=${relLte}`;
    if (airLte) url += `&first_air_date.lte=${airLte}`;
    const data = await get(url);
    return { results: (data.results ?? []).map((i: any) => normalizeListItem(i, type)), total_pages: data.total_pages };
  }

  // /tmdb/search?q=xxx or /tmdb/search?query=xxx
  if (path.startsWith('/tmdb/search')) {
    const qs     = path.includes('?') ? path.slice(path.indexOf('?') + 1) : '';
    const params = new URLSearchParams(qs);
    const query  = params.get('query') ?? params.get('q') ?? '';
    const type   = params.get('type');
    const page   = params.get('page') ?? '1';
    const endpoint = type === 'movie' ? 'search/movie' : type === 'tv' ? 'search/tv' : 'search/multi';
    const data = await get(
      `${TMDB_BASE}/${endpoint}?${base}&query=${encodeURIComponent(query)}&page=${page}&include_adult=false`,
    );
    const results = (data.results ?? []).map((i: any) =>
      normalizeListItem(i, (i.media_type ?? type ?? 'movie') as 'movie' | 'tv'),
    );
    return { results, total_pages: data.total_pages };
  }

  // /tmdb/genres/(movie|tv)
  const genresM = path.match(/^\/tmdb\/genres\/(movie|tv)$/);
  if (genresM) {
    const type = genresM[1] as 'movie' | 'tv';
    const data = await get(`${TMDB_BASE}/genre/${type}/list?${base}`);
    return { genres: data.genres ?? [] };
  }

  // /tmdb/network/123
  const networkM = path.match(/^\/tmdb\/network\/(\d+)$/);
  if (networkM) {
    const data = await get(
      `${TMDB_BASE}/discover/tv?${base}&with_networks=${networkM[1]}&sort_by=popularity.desc`,
    );
    return { results: (data.results ?? []).map((i: any) => normalizeListItem(i, 'tv')) };
  }

  // Unhandled — fall through to backend
  const r = await fetch(`${API_BASE}${path}`);
  if (!r.ok) throw new Error(`Backend ${r.status}`);
  return r.json();
}

// ── Public fetch wrapper ──────────────────────────────────────────────────────
export type TmdbResponse = { ok: boolean; status: number; json: () => Promise<any> };

// Paths that benefit from caching (detail, lists, search results)
function isCacheable(path: string): boolean {
  return (
    path.startsWith('/tmdb/details/') ||
    path.startsWith('/tmdb/trending/') ||
    path.startsWith('/tmdb/popular/') ||
    path.startsWith('/tmdb/discover') ||
    path.startsWith('/tmdb/search') ||
    path.startsWith('/tmdb/genres/') ||
    path.startsWith('/tmdb/network/') ||
    path.startsWith('/tmdb/season/')
  );
}

async function fetchLive(path: string): Promise<any> {
  if (_activeKey) {
    try {
      return await dispatchDirect(path, _activeKey);
    } catch {
      // fall through to backend
    }
  }
  const r = await fetch(`${API_BASE}${path}`);
  if (!r.ok) throw new Error(`Backend ${r.status}`);
  return r.json();
}

export async function tmdbFetch(path: string): Promise<TmdbResponse> {
  try {
    if (isCacheable(path)) {
      const data = await cachedFetch(`tmdb${path}`, () => fetchLive(path));
      return { ok: true, status: 200, json: async () => data };
    }
    const data = await fetchLive(path);
    return { ok: true, status: 200, json: async () => data };
  } catch {
    return { ok: false, status: 0, json: async () => null };
  }
}
