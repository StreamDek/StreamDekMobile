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
    let detail = '';
    try {
      const payload = await response.json() as Record<string, unknown>;
      if (typeof payload.error === 'string') {
        detail = payload.error;
      }
    } catch {
      detail = '';
    }
    throw new Error(detail || `Update server returned ${response.status}.`);
  }

  return parseUpdateManifest(await response.json());
}
