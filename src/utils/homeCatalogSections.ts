import type { InstalledAddon } from '../context/AddonContext';

export type HomeCatalogSection = {
  id: string;
  title: string;
  endpoint: string;
  enabled: boolean;
  source?: 'builtin' | 'addon';
  provider?: 'cinemeta' | 'tmdb';
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

export function resolveAddonCatalogBaseUrl(addon: InstalledAddon): string | null {
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

// Returns the addon's "configure" page URL (Stremio convention: <base>/configure)
// if the addon declares itself configurable via behaviorHints, otherwise null.
export function getAddonConfigureUrl(addon: InstalledAddon): string | null {
  if (!addon.manifest?.behaviorHints?.configurable) return null;
  const base = resolveAddonCatalogBaseUrl(addon);
  if (!base) return null;
  return `${base}/configure`;
}

export function buildDefaultHomeSections(
  enabledProviders: Array<'cinemeta' | 'tmdb'>,
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
  preferredProvider?: 'cinemeta' | 'tmdb',
): HomeCatalogSection[] {
  const withSdPrefix = (title: string) => `SD - ${title}`;
  const dedupedProviders = Array.from(new Set(enabledProviders));
  const orderedProviders = preferredProvider && dedupedProviders.includes(preferredProvider)
    ? [preferredProvider, ...dedupedProviders.filter(provider => provider !== preferredProvider)]
    : dedupedProviders;

  return orderedProviders.flatMap<HomeCatalogSection>(provider => {
    const withProvider = (id: string, section: Omit<HomeCatalogSection, 'id' | 'source' | 'provider'>): HomeCatalogSection => ({
      ...section,
      id: `${provider}:${id}`,
      source: 'builtin',
      provider,
    });

    if (provider === 'cinemeta') {
      return [
        withProvider('networks', { title: withSdPrefix(labels.networks), endpoint: '/tmdb/networks', enabled: true, contentType: 'mixed' }),
        withProvider('featured_movie', { title: withSdPrefix(labels.featuredMovies), endpoint: '/cinemeta/catalog/movie/imdbRating', enabled: true, contentType: 'movie' }),
        withProvider('featured_tv', { title: withSdPrefix(labels.featuredSeries), endpoint: '/cinemeta/catalog/series/imdbRating', enabled: true, contentType: 'tv' }),
        withProvider('popular_movie', { title: withSdPrefix(labels.popularMovies), endpoint: '/cinemeta/catalog/movie/top', enabled: true, contentType: 'movie' }),
        withProvider('popular_tv', { title: withSdPrefix(labels.popularTv), endpoint: '/cinemeta/catalog/series/top', enabled: true, contentType: 'tv' }),
        withProvider('documentaries', { title: withSdPrefix(labels.documentaries), endpoint: '/cinemeta/catalog/movie/top?genre=Documentary', enabled: false, contentType: 'movie' }),
        withProvider('new_movie', { title: withSdPrefix(labels.newMovies), endpoint: `/cinemeta/catalog/movie/year/${currentYear}`, enabled: false, contentType: 'movie' }),
        withProvider('new_tv', { title: withSdPrefix(labels.newSeries), endpoint: `/cinemeta/catalog/series/year/${currentYear}`, enabled: false, contentType: 'tv' }),
      ];
    }

    return [
      withProvider('networks', { title: withSdPrefix(labels.networks), endpoint: '/tmdb/networks', enabled: true, contentType: 'mixed' }),
      withProvider('trending_movie', { title: withSdPrefix(labels.trendingMovies), endpoint: '/tmdb/trending/movie', enabled: true, contentType: 'movie' }),
      withProvider('trending_tv', { title: withSdPrefix(labels.trendingTv), endpoint: '/tmdb/trending/tv', enabled: true, contentType: 'tv' }),
      withProvider('documentaries', { title: withSdPrefix(labels.documentaries), endpoint: '/tmdb/discover?type=movie&genre_id=99&sort_by=popularity.desc', enabled: false, contentType: 'movie' }),
      withProvider('popular_movie', { title: withSdPrefix(labels.popularMovies), endpoint: '/tmdb/popular/movie', enabled: false, contentType: 'movie' }),
      withProvider('popular_tv', { title: withSdPrefix(labels.popularTv), endpoint: '/tmdb/popular/tv', enabled: false, contentType: 'tv' }),
    ];
  });
}

const MAX_SECTION_TITLE_LENGTH = 30;

function truncateAtWordBoundary(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text;
  const cut = text.slice(0, maxLength - 1);
  const lastSpace = cut.lastIndexOf(' ');
  return `${(lastSpace > maxLength * 0.5 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`;
}

export function buildAddonSectionTitle(addonName: string, catalogName?: string | null, differentiator?: string | null): string {
  const addon = (addonName ?? '').trim();
  const catalog = (catalogName ?? '').trim();
  const suffix = differentiator ? ` · ${differentiator}` : '';
  const maxLength = Math.max(MAX_SECTION_TITLE_LENGTH - suffix.length, 1);

  let base: string;
  if (!catalog) {
    base = truncateAtWordBoundary(addon, maxLength);
  } else if (!addon || catalog.toLowerCase().includes(addon.toLowerCase())) {
    // Skip the addon prefix when the catalog name already identifies it.
    base = truncateAtWordBoundary(catalog, maxLength);
  } else {
    const combined = `${addon} - ${catalog}`;
    // Prefer the more descriptive catalog name over a truncated combination.
    base = combined.length <= maxLength ? combined : truncateAtWordBoundary(catalog, maxLength);
  }
  return `${base}${suffix}`;
}

export function buildAddonHomeSections(
  addons: InstalledAddon[],
  typeLabels?: { movie: string; tv: string },
): HomeCatalogSection[] {
  return addons
    .filter(addon => addon.enabled)
    .sort((a, b) => a.position - b.position)
    .flatMap<HomeCatalogSection>(addon => {
      const catalogs = Array.isArray(addon.manifest?.catalogs) ? addon.manifest.catalogs : [];

      // Some addons publish separate movie/series catalogs that share the same
      // display name (e.g. "Apple TV+ Popular" for both). Track which catalog
      // names map to more than one content type so those rows can get a
      // "Movies"/"Series" differentiator appended to their title.
      const typesByName = new Map<string, Set<'movie' | 'tv' | 'sport'>>();
      catalogs.forEach((catalog: any) => {
        const rawType = String(catalog?.type ?? '').toLowerCase();
        const type = mapAddonCatalogType(rawType);
        const name = String(catalog?.name ?? '').trim().toLowerCase();
        if (!type || !name) return;
        const set = typesByName.get(name) ?? new Set();
        set.add(type);
        typesByName.set(name, set);
      });

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

        const catalogName = String(catalog?.name ?? '').trim();
        const isAmbiguous = catalogName.length > 0 && (typesByName.get(catalogName.toLowerCase())?.size ?? 0) > 1;
        const differentiator = isAmbiguous
          ? (type === 'movie' ? typeLabels?.movie : type === 'tv' ? typeLabels?.tv : undefined)
          : undefined;
        const title = buildAddonSectionTitle(addon.manifest.name, catalog?.name, differentiator);

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
