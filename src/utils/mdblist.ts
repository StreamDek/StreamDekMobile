export const MDBLIST_PROVIDER_IMDB = 'imdb';
export const MDBLIST_PROVIDER_TMDB = 'tmdb';
export const MDBLIST_PROVIDER_TOMATOES = 'tomatoes';
export const MDBLIST_PROVIDER_METACRITIC = 'metacritic';
export const MDBLIST_PROVIDER_TRAKT = 'trakt';
export const MDBLIST_PROVIDER_LETTERBOXD = 'letterboxd';
export const MDBLIST_PROVIDER_AUDIENCE = 'audience';

export const MDBLIST_PROVIDER_PRIORITY_ORDER = [
  MDBLIST_PROVIDER_IMDB,
  MDBLIST_PROVIDER_TMDB,
  MDBLIST_PROVIDER_TOMATOES,
  MDBLIST_PROVIDER_METACRITIC,
  MDBLIST_PROVIDER_TRAKT,
  MDBLIST_PROVIDER_LETTERBOXD,
  MDBLIST_PROVIDER_AUDIENCE,
] as const;

export type MdbListProviderId = typeof MDBLIST_PROVIDER_PRIORITY_ORDER[number];
export type MdbListErrorCode = 'invalid_api_key' | 'request_failed';

export type ExternalRating = {
  source: MdbListProviderId;
  value: number;
};

export type MdbListDisplayRating = {
  displayRating: number;
  displayRatingSource: MdbListProviderId;
  displayRatingLabel: string;
  displayRatingLabelBackgroundColor: string;
  displayRatingLabelTextColor: string;
};

export type MdbListSettings = {
  enabled: boolean;
  apiKey: string;
  useImdb: boolean;
  useTmdb: boolean;
  useTomatoes: boolean;
  useMetacritic: boolean;
  useTrakt: boolean;
  useLetterboxd: boolean;
  useAudience: boolean;
};

export type MdbListValidationResult = {
  valid: boolean;
  code?: MdbListErrorCode | 'missing_api_key';
  message?: string;
};

export const DEFAULT_MDBLIST_SETTINGS: MdbListSettings = {
  enabled: false,
  apiKey: '',
  useImdb: true,
  useTmdb: true,
  useTomatoes: true,
  useMetacritic: true,
  useTrakt: true,
  useLetterboxd: true,
  useAudience: true,
};

const ratingsCache = new Map<string, ExternalRating[]>();
const imdbRegex = /tt\d+/i;
const MDBLIST_VALIDATION_IMDB_ID = 'tt0111161';

type RatingResponse = {
  ratings?: Array<{
    rating?: number | string | null;
    score?: number | string | null;
  }>;
};

type MdbListEnrichableItem = {
  imdbId?: string | null;
  type?: string | null;
};

type MdbListError = Error & {
  code: MdbListErrorCode;
  status?: number;
};

function createMdbListError(code: MdbListErrorCode, message: string, status?: number): MdbListError {
  return Object.assign(new Error(message), { code, status });
}

function parseNumericRating(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string') {
    const normalized = value.trim().replace(/%$/, '');
    const parsed = Number(normalized);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}

function parseRatingResponse(payload: unknown): number | null {
  if (Array.isArray(payload)) {
    for (const item of payload) {
      const rating = parseNumericRating((item as { rating?: unknown })?.rating);
      if (rating != null) return rating;
    }
    return null;
  }

  if (!payload || typeof payload !== 'object') return null;
  const ratings = (payload as RatingResponse).ratings;
  if (!Array.isArray(ratings)) return null;

  for (const item of ratings) {
    const rating = parseNumericRating(item?.rating) ?? parseNumericRating(item?.score);
    if (rating != null) return rating;
  }

  return null;
}

async function readJsonResponse(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text.trim()) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function extractImdbId(value: string | null | undefined): string | null {
  if (!value) return null;
  return value.match(imdbRegex)?.[0] ?? null;
}

export function getEnabledMdbListProviders(settings: MdbListSettings): MdbListProviderId[] {
  return MDBLIST_PROVIDER_PRIORITY_ORDER.filter(provider => {
    switch (provider) {
      case MDBLIST_PROVIDER_IMDB: return settings.useImdb;
      case MDBLIST_PROVIDER_TMDB: return settings.useTmdb;
      case MDBLIST_PROVIDER_TOMATOES: return settings.useTomatoes;
      case MDBLIST_PROVIDER_METACRITIC: return settings.useMetacritic;
      case MDBLIST_PROVIDER_TRAKT: return settings.useTrakt;
      case MDBLIST_PROVIDER_LETTERBOXD: return settings.useLetterboxd;
      case MDBLIST_PROVIDER_AUDIENCE: return settings.useAudience;
      default: return false;
    }
  });
}

export function getMdbListProviderLabel(providerId: MdbListProviderId): string {
  switch (providerId) {
    case MDBLIST_PROVIDER_IMDB: return 'IMDb';
    case MDBLIST_PROVIDER_TMDB: return 'TMDB';
    case MDBLIST_PROVIDER_TOMATOES: return 'RT';
    case MDBLIST_PROVIDER_METACRITIC: return 'MC';
    case MDBLIST_PROVIDER_TRAKT: return 'Trakt';
    case MDBLIST_PROVIDER_LETTERBOXD: return 'LB';
    case MDBLIST_PROVIDER_AUDIENCE: return 'AUD';
    default: return 'MDB';
  }
}

export function getMdbListProviderLabelBackgroundColor(providerId: MdbListProviderId): string {
  switch (providerId) {
    case MDBLIST_PROVIDER_IMDB: return '#F5C518';
    case MDBLIST_PROVIDER_TMDB: return '#01B4E4';
    case MDBLIST_PROVIDER_TOMATOES: return '#FA320A';
    case MDBLIST_PROVIDER_METACRITIC: return '#66CC33';
    case MDBLIST_PROVIDER_TRAKT: return '#ED1C24';
    case MDBLIST_PROVIDER_LETTERBOXD: return '#00A862';
    case MDBLIST_PROVIDER_AUDIENCE: return '#2563EB';
    default: return '#F5C518';
  }
}

export function getMdbListProviderLabelTextColor(providerId: MdbListProviderId): string {
  switch (providerId) {
    case MDBLIST_PROVIDER_IMDB:
    case MDBLIST_PROVIDER_METACRITIC:
      return '#000000';
    default:
      return '#FFFFFF';
  }
}

export function shouldFetchMdbListRatings(
  imdbId: string | null | undefined,
  settings: MdbListSettings,
): imdbId is string {
  return Boolean(
    settings.enabled
    && settings.apiKey.trim().length > 0
    && getEnabledMdbListProviders(settings).length > 0
    && extractImdbId(imdbId),
  );
}

export function clearMdbListRatingsCache() {
  ratingsCache.clear();
}

export function getMdbListErrorCode(error: unknown): MdbListErrorCode | null {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = (error as { code?: unknown }).code;
    if (code === 'invalid_api_key' || code === 'request_failed') return code;
  }
  return null;
}

async function fetchProviderRating(
  imdbId: string,
  mediaType: 'movie' | 'show',
  providerId: MdbListProviderId,
  apiKey: string,
  signal?: AbortSignal,
): Promise<ExternalRating | null> {
  const response = await fetch(`https://api.mdblist.com/rating/${mediaType}/${providerId}?apikey=${encodeURIComponent(apiKey)}`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      ids: [imdbId],
      provider: MDBLIST_PROVIDER_IMDB,
    }),
    signal,
  });

  const payload = await readJsonResponse(response);

  if (!response.ok) {
    console.warn('[MDBList] Provider request failed', { providerId, mediaType, status: response.status, imdbId, payload });
    if (response.status === 401 || response.status === 403) {
      throw createMdbListError('invalid_api_key', 'MDBList API key rejected', response.status);
    }
    if (response.status === 404 || response.status === 422) {
      return null;
    }
    throw createMdbListError('request_failed', `MDBList ${providerId} failed with ${response.status}`, response.status);
  }

  const rating = parseRatingResponse(payload);
  if (rating == null) {
    console.warn('[MDBList] Provider returned no usable rating', { providerId, mediaType, imdbId, payload });
    return null;
  }
  return { source: providerId, value: rating };
}

export async function validateMdbListApiKey(
  apiKey: string,
  signal?: AbortSignal,
): Promise<MdbListValidationResult> {
  const normalizedApiKey = apiKey.trim();
  if (normalizedApiKey.length === 0) {
    return { valid: false, code: 'missing_api_key', message: 'Enter an MDBList API key.' };
  }

  try {
    await fetchProviderRating(MDBLIST_VALIDATION_IMDB_ID, 'movie', MDBLIST_PROVIDER_IMDB, normalizedApiKey, signal);
    return { valid: true };
  } catch (error) {
    const code = getMdbListErrorCode(error);
    console.warn('[MDBList] API key validation result', { code, message: error instanceof Error ? error.message : String(error) });
    if (code === 'invalid_api_key') {
      return { valid: false, code, message: 'MDBList rejected this API key.' };
    }
    if (code === 'request_failed') {
      return { valid: false, code, message: 'MDBList could not be reached right now.' };
    }
    return { valid: true, message: 'MDBList accepted the key, but the response could not be verified.' };
  }
}

export async function fetchMdbListRatings(
  imdbId: string | null | undefined,
  type: 'movie' | 'tv',
  settings: MdbListSettings,
  signal?: AbortSignal,
): Promise<ExternalRating[]> {
  const normalizedImdbId = extractImdbId(imdbId);
  if (!normalizedImdbId || !shouldFetchMdbListRatings(normalizedImdbId, settings)) {
    return [];
  }

  const providers = getEnabledMdbListProviders(settings);
  const apiKey = settings.apiKey.trim();
  const mediaType = type === 'movie' ? 'movie' : 'show';
  const cacheKey = `${mediaType}:${normalizedImdbId}:${apiKey}:${providers.join(',')}`;
  const cached = ratingsCache.get(cacheKey);
  if (cached) return cached;

  const settled = await Promise.allSettled(
    providers.map(providerId => fetchProviderRating(normalizedImdbId, mediaType, providerId, apiKey, signal)),
  );

  const ratings = settled.flatMap(result => {
    if (result.status !== 'fulfilled' || !result.value) return [];
    return [result.value];
  });

  if (ratings.length === 0) {
    const invalidKeyFailure = settled.find(result => result.status === 'rejected' && getMdbListErrorCode(result.reason) === 'invalid_api_key');
    if (invalidKeyFailure && invalidKeyFailure.status === 'rejected') {
      throw invalidKeyFailure.reason;
    }

    const fulfilledCount = settled.filter(result => result.status === 'fulfilled').length;
    const requestFailures = settled.filter(result => result.status === 'rejected' && getMdbListErrorCode(result.reason) === 'request_failed');

    if (fulfilledCount === 0 && requestFailures.length === settled.length && requestFailures[0]?.status === 'rejected') {
      throw requestFailures[0].reason;
    }

    if (requestFailures.length > 0) {
      console.warn('[MDBList] Some providers failed without returning ratings', {
        imdbId: normalizedImdbId,
        mediaType,
        providers,
        requestFailureCount: requestFailures.length,
      });
    }
  }

  ratingsCache.set(cacheKey, ratings);
  return ratings;
}

export function getPreferredMdbListRating(
  ratings: ExternalRating[],
  settings: MdbListSettings,
): ExternalRating | null {
  if (!Array.isArray(ratings) || ratings.length === 0) return null;

  const enabledProviders = getEnabledMdbListProviders(settings);
  for (const providerId of enabledProviders) {
    const match = ratings.find(rating => rating.source === providerId);
    if (match) return match;
  }

  return ratings[0] ?? null;
}

export function buildMdbListDisplayRating(
  rating: ExternalRating | null | undefined,
): MdbListDisplayRating | null {
  if (!rating) return null;

  return {
    displayRating: rating.value,
    displayRatingSource: rating.source,
    displayRatingLabel: getMdbListProviderLabel(rating.source),
    displayRatingLabelBackgroundColor: getMdbListProviderLabelBackgroundColor(rating.source),
    displayRatingLabelTextColor: getMdbListProviderLabelTextColor(rating.source),
  };
}

export async function enrichItemsWithMdbListRatings<T extends MdbListEnrichableItem>(
  items: T[],
  settings: MdbListSettings,
  options?: { signal?: AbortSignal; maxItems?: number },
): Promise<Array<T & Partial<MdbListDisplayRating>>> {
  if (!Array.isArray(items) || items.length === 0) return items;
  if (!settings.enabled || settings.apiKey.trim().length === 0) return items;

  const maxItems = typeof options?.maxItems === 'number' && Number.isFinite(options.maxItems)
    ? Math.max(0, Math.floor(options.maxItems))
    : items.length;
  if (maxItems <= 0) return items;

  const enrichedByIndex = new Map<number, MdbListDisplayRating>();

  await Promise.all(
    items.slice(0, maxItems).map(async (item, index) => {
      const type = item?.type === 'movie' || item?.type === 'tv' ? item.type : null;
      if (!type || !shouldFetchMdbListRatings(item?.imdbId, settings)) return;

      try {
        const ratings = await fetchMdbListRatings(item.imdbId, type, settings, options?.signal);
        const preferredRating = getPreferredMdbListRating(ratings, settings);
        const displayRating = buildMdbListDisplayRating(preferredRating);
        if (!displayRating) return;
        enrichedByIndex.set(index, displayRating);
      } catch (error) {
        console.warn('[MDBList] Catalog enrichment skipped for item', {
          imdbId: item?.imdbId ?? null,
          type,
          message: error instanceof Error ? error.message : String(error),
        });
      }
    }),
  );

  if (enrichedByIndex.size === 0) return items;

  return items.map((item, index) => {
    const displayRating = enrichedByIndex.get(index);
    return displayRating ? { ...item, ...displayRating } : item;
  });
}
