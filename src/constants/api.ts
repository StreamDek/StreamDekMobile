import Constants from 'expo-constants';

const explicitApiBaseUrl =
  process.env.EXPO_PUBLIC_API_BASE_URL
  ?? Constants.expoConfig?.extra?.streamdekApiUrl
  ?? Constants.manifest2?.extra?.streamdekApiUrl;

/**
 * The mobile backend URL is injected by `scripts/expo-with-env.js` from the
 * repository root `.env`. Keep that as the single source of truth.
 */
if (!explicitApiBaseUrl) {
  throw new Error(
    'Missing mobile backend URL. Start mobile through scripts/expo-with-env.js, or ensure streamdek/.env provides STREAMDEK_API_URL.'
  );
}

export const API_BASE = explicitApiBaseUrl;
