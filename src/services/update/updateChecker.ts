import { Platform } from 'react-native';
import type { UpdateCheckOutcome } from '../../types/update';
import { fetchLatestAndroidMobileRelease } from './releaseMetadata';
import { getCurrentVersionInfo, isAndroidUpdateSupported } from './nativeBridge';

export async function checkForAndroidUpdate(): Promise<UpdateCheckOutcome> {
  if (Platform.OS !== 'android' || !isAndroidUpdateSupported()) {
    return { status: 'unsupported-device' };
  }

  try {
    const [current, release] = await Promise.all([
      getCurrentVersionInfo(),
      fetchLatestAndroidMobileRelease(),
    ]);

    const available = release.versionCode > current.versionCode;
    if (!available) {
      return { status: 'up-to-date', current };
    }

    const isMandatory = release.required || (
      typeof release.minSupportedVersionCode === 'number' &&
      current.versionCode < release.minSupportedVersionCode
    );

    return {
      status: 'update-available',
      current,
      release,
      isMandatory,
    };
  } catch (error) {
    return {
      status: 'error',
      message: error instanceof Error && error.message ? error.message : 'UPDATE_CHECK_UNAVAILABLE',
    };
  }
}
