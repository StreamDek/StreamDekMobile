import { API_BASE } from '../../constants/api';
import type { UpdateManifest } from '../../types/update';
import { parseUpdateManifest } from './releaseParser';

const MOBILE_UPDATE_MANIFEST_PATH = '/public/updates/android-mobile/latest';
const UPDATE_BASE_URL = API_BASE.replace(/\/+$/, '');

export async function fetchLatestAndroidMobileRelease(signal?: AbortSignal): Promise<UpdateManifest> {
  const response = await fetch(`${UPDATE_BASE_URL}${MOBILE_UPDATE_MANIFEST_PATH}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
    signal,
  });

  if (!response.ok) {
    throw new Error('UPDATE_CHECK_UNAVAILABLE');
  }

  return parseUpdateManifest(await response.json());
}
