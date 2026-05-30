import type { DownloadProgress, UpdateManifest } from '../../types/update';
import { downloadApk, subscribeToDownloadProgress, validateApk } from './nativeBridge';

function buildApkFileName(release: UpdateManifest) {
  const suffix = release.assetName?.replace(/[^a-z0-9._-]/gi, '-') ?? `streamdek-${release.versionCode}.apk`;
  return suffix.toLowerCase().endsWith('.apk') ? suffix : `${suffix}.apk`;
}

export async function downloadAndValidateRelease(
  release: UpdateManifest,
  onProgress: (progress: DownloadProgress) => void,
) {
  const unsubscribe = subscribeToDownloadProgress(onProgress);
  try {
    const result = await downloadApk({
      url: release.apkUrl,
      fileName: buildApkFileName(release),
      expectedSha256: release.checksumSha256,
    });
    const validation = await validateApk(result.filePath, release.packageName, release.versionCode);

    if (!validation.isValid) {
      throw new Error('Downloaded update failed package validation.');
    }

    return result;
  } finally {
    unsubscribe();
  }
}
