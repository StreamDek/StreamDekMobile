import type { InstalledAddon } from '../context/AddonContext';

export type HomeCatalogSection = {
  id: string;
  title: string;
  endpoint: string;
  enabled: boolean;
  source?: 'builtin' | 'addon';
  contentType?: 'movie' | 'tv' | 'sport' | 'mixed';
};

// Stremio-native catalog types that represent live content. Note that native
// 'tv' means live television channels — series catalogs use 'series'.
export const LIVE_ADDON_CATALOG_TYPES = new Set([
  'tv', 'channel', 'channels', 'event', 'events', 'live', 'sport', 'sports',
]);

// Catalogs that list a user's own debrid/cloud files. Items in these catalogs
// must play directly from the owning addon — even when they carry real
// IMDb/TMDB ids — instead of opening the detail page and hunting for sources.
const DEBRID_CATALOG_KEYWORDS = [
  'debrid', 'torbox', 'premiumize', 'alldebrid', 'offcloud', 'easydebrid',
  'put.io', 'putio', 'pikpak', 'seedr', 'cloud', 'download',
];

export function isDebridCatalog(identityText: string): boolean {
  const text = identityText.toLowerCase();
  return DEBRID_CATALOG_KEYWORDS.some(keyword => text.includes(keyword));
}

export function mapAddonCatalogType(rawType: string): 'movie' | 'tv' | 'sport' | null {
  if (rawType === 'movie') return 'movie';
  if (rawType === 'series') return 'tv';
  if (LIVE_ADDON_CATALOG_TYPES.has(rawType)) return 'sport';
  // Debrid cloud/download catalogs commonly publish under 'other'.
  if (rawType === 'other') return 'movie';
  return null;
}

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

const MAX_SECTION_TITLE_LENGTH = 30;

function truncateAtWordBoundary(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text;
  const cut = text.slice(0, maxLength - 1);
  const lastSpace = cut.lastIndexOf(' ');
  return `${(lastSpace > maxLength * 0.5 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`;
}

export function buildAddonSectionTitle(addonName: string, catalogName?: string | null): string {
  const addon = (addonName ?? '').trim();
  const catalog = (catalogName ?? '').trim();
  if (!catalog) return truncateAtWordBoundary(addon, MAX_SECTION_TITLE_LENGTH);
  // Skip the addon prefix when the catalog name already identifies it.
  if (!addon || catalog.toLowerCase().includes(addon.toLowerCase())) {
    return truncateAtWordBoundary(catalog, MAX_SECTION_TITLE_LENGTH);
  }
  const combined = `${addon} - ${catalog}`;
  if (combined.length <= MAX_SECTION_TITLE_LENGTH) return combined;
  // Prefer the more descriptive catalog name over a truncated combination.
  return truncateAtWordBoundary(catalog, MAX_SECTION_TITLE_LENGTH);
}

export function buildAddonHomeSections(addons: InstalledAddon[]): HomeCatalogSection[] {
  return addons
    .filter(addon => addon.enabled)
    .sort((a, b) => a.position - b.position)
    .flatMap<HomeCatalogSection>(addon => {
      const catalogs = Array.isArray(addon.manifest?.catalogs) ? addon.manifest.catalogs : [];
      return catalogs.flatMap<HomeCatalogSection>((catalog, index) => {
        const rawType = String(catalog?.type ?? '').toLowerCase();
        const type = mapAddonCatalogType(rawType);
        const catalogId = String(catalog?.id ?? '').trim();
        if (!catalogId || !type) return [];

        const version = encodeURIComponent(String(addon.manifest?.version ?? '0'));
        const transport = resolveAddonCatalogBaseUrl(addon);
        const params = new URLSearchParams({ v: version });
        if (transport) params.set('transport', transport);
        // Carry the addon's native type so catalog/stream URLs hit the right path
        // (internal 'tv' = series, but native 'tv' = live channels).
        params.set('addonType', rawType);
        const catalogIdentity = [addon.manifest?.id, addon.manifest?.name, catalogId, catalog?.name]
          .filter(Boolean)
          .join(' ');
        if (isDebridCatalog(catalogIdentity)) params.set('direct', '1');
        const endpoint = `addon://${encodeURIComponent(addon.id)}/${encodeURIComponent(type)}/${encodeURIComponent(catalogId)}?${params.toString()}`;
        const title = buildAddonSectionTitle(addon.manifest.name, catalog?.name);

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
