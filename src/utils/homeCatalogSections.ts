import type { InstalledAddon } from '../context/AddonContext';

export type HomeCatalogSection = {
  id: string;
  title: string;
  endpoint: string;
  enabled: boolean;
  source?: 'builtin' | 'addon';
  contentType?: 'movie' | 'tv' | 'sport' | 'mixed';
};

function resolveAddonCatalogBaseUrl(addon: InstalledAddon): string | null {
  const manifest = addon.manifest as Record<string, any> | undefined;
  const candidates = [
    addon.transportUrl,
    addon.baseUrl,
    addon.manifestUrl,
    addon.url,
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

export function buildDefaultHomeSections(
  metadataProvider: 'cinemeta' | 'tmdb',
  currentYear: number,
  labels: {
    networks: string;
    featuredMovies: string;
    featuredSeries: string;
    popularMovies: string;
    popularTv: string;
    documentaries: string;
    newMovies: string;
    newSeries: string;
    trendingMovies: string;
    trendingTv: string;
  },
): HomeCatalogSection[] {
  if (metadataProvider === 'cinemeta') {
    return [
      { id: 'networks', title: labels.networks, endpoint: '/tmdb/networks', enabled: true, source: 'builtin', contentType: 'mixed' },
      { id: 'featured_movie', title: labels.featuredMovies, endpoint: '/cinemeta/catalog/movie/imdbRating', enabled: true, source: 'builtin', contentType: 'movie' },
      { id: 'featured_tv', title: labels.featuredSeries, endpoint: '/cinemeta/catalog/series/imdbRating', enabled: true, source: 'builtin', contentType: 'tv' },
      { id: 'popular_movie', title: labels.popularMovies, endpoint: '/cinemeta/catalog/movie/top', enabled: true, source: 'builtin', contentType: 'movie' },
      { id: 'popular_tv', title: labels.popularTv, endpoint: '/cinemeta/catalog/series/top', enabled: true, source: 'builtin', contentType: 'tv' },
      { id: 'documentaries', title: labels.documentaries, endpoint: '/cinemeta/catalog/movie/top?genre=Documentary', enabled: false, source: 'builtin', contentType: 'movie' },
      { id: 'new_movie', title: labels.newMovies, endpoint: `/cinemeta/catalog/movie/year/${currentYear}`, enabled: false, source: 'builtin', contentType: 'movie' },
      { id: 'new_tv', title: labels.newSeries, endpoint: `/cinemeta/catalog/series/year/${currentYear}`, enabled: false, source: 'builtin', contentType: 'tv' },
    ];
  }

  return [
    { id: 'networks', title: labels.networks, endpoint: '/tmdb/networks', enabled: true, source: 'builtin', contentType: 'mixed' },
    { id: 'trending_movie', title: labels.trendingMovies, endpoint: '/tmdb/trending/movie', enabled: true, source: 'builtin', contentType: 'movie' },
    { id: 'trending_tv', title: labels.trendingTv, endpoint: '/tmdb/trending/tv', enabled: true, source: 'builtin', contentType: 'tv' },
    { id: 'documentaries', title: labels.documentaries, endpoint: '/tmdb/discover?type=movie&genre_id=99&sort_by=popularity.desc', enabled: false, source: 'builtin', contentType: 'movie' },
    { id: 'popular_movie', title: labels.popularMovies, endpoint: '/tmdb/popular/movie', enabled: false, source: 'builtin', contentType: 'movie' },
    { id: 'popular_tv', title: labels.popularTv, endpoint: '/tmdb/popular/tv', enabled: false, source: 'builtin', contentType: 'tv' },
  ];
}

export function buildAddonHomeSections(addons: InstalledAddon[]): HomeCatalogSection[] {
  return addons
    .filter(addon => addon.enabled)
    .sort((a, b) => a.position - b.position)
    .flatMap<HomeCatalogSection>(addon => {
      const catalogs = Array.isArray(addon.manifest?.catalogs) ? addon.manifest.catalogs : [];
      return catalogs.flatMap<HomeCatalogSection>((catalog, index) => {
        const rawType = String(catalog?.type ?? '').toLowerCase();
        const type = rawType === 'series' ? 'tv' : rawType;
        const catalogId = String(catalog?.id ?? '').trim();
        if (!catalogId || (type !== 'movie' && type !== 'tv' && type !== 'sport')) return [];

        const version = encodeURIComponent(String(addon.manifest?.version ?? '0'));
        const transport = resolveAddonCatalogBaseUrl(addon);
        const params = new URLSearchParams({ v: version });
        if (transport) params.set('transport', transport);
        const endpoint = `addon://${encodeURIComponent(addon.id)}/${encodeURIComponent(type)}/${encodeURIComponent(catalogId)}?${params.toString()}`;
        const title = catalog?.name?.trim()
          ? `${addon.manifest.name} - ${catalog.name.trim()}`
          : addon.manifest.name;

        return [{
          id: `addon:${addon.id}:${type}:${catalogId}:${index}`,
          title,
          endpoint,
          enabled: true,
          source: 'addon',
          contentType: type,
        }];
      });
    });
}
