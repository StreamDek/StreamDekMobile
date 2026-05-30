import { API_BASE } from '../../constants/api';
import type { UpdateManifest } from '../../types/update';

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function trustedDownloadUrl(rawUrl: string): string {
  const apiOrigin = new URL(API_BASE).origin;
  const parsed = new URL(rawUrl, apiOrigin);
  const allowedHosts = new Set<string>([
    new URL(apiOrigin).host,
    'github.com',
    'objects.githubusercontent.com',
    'github-releases.githubusercontent.com',
  ]);

  if (parsed.protocol !== 'https:' && parsed.origin !== apiOrigin) {
    throw new Error('Update download URL is not trusted.');
  }
  if (!allowedHosts.has(parsed.host)) {
    throw new Error('Update download URL host is not trusted.');
  }

  return parsed.toString();
}

export function parseUpdateManifest(payload: unknown): UpdateManifest {
  if (!payload || typeof payload !== 'object') {
    throw new Error('Update metadata response was invalid.');
  }

  const candidate = payload as Record<string, unknown>;
  if (!isNumber(candidate.versionCode) || candidate.versionCode <= 0) {
    throw new Error('Update metadata is missing a valid version code.');
  }
  if (!isNonEmptyString(candidate.versionName)) {
    throw new Error('Update metadata is missing a valid version name.');
  }
  if (!isNonEmptyString(candidate.packageName)) {
    throw new Error('Update metadata is missing a package name.');
  }
  if (!isNonEmptyString(candidate.platform) || (candidate.platform !== 'android-mobile' && candidate.platform !== 'android-tv')) {
    throw new Error('Update metadata platform is invalid.');
  }
  if (!isNonEmptyString(candidate.apkUrl)) {
    throw new Error('Update metadata is missing a download URL.');
  }

  return {
    platform: candidate.platform,
    versionCode: candidate.versionCode,
    versionName: candidate.versionName.trim(),
    apkUrl: trustedDownloadUrl(candidate.apkUrl.trim()),
    releaseNotes: typeof candidate.releaseNotes === 'string' ? candidate.releaseNotes.trim() : '',
    required: Boolean(candidate.required),
    publishedAt: typeof candidate.publishedAt === 'string' ? candidate.publishedAt : null,
    checksumSha256: typeof candidate.checksumSha256 === 'string' && candidate.checksumSha256.trim().length > 0
      ? candidate.checksumSha256.trim().toLowerCase()
      : null,
    minSupportedVersionCode: isNumber(candidate.minSupportedVersionCode) ? candidate.minSupportedVersionCode : null,
    requiredReason: typeof candidate.requiredReason === 'string' && candidate.requiredReason.trim().length > 0
      ? candidate.requiredReason.trim()
      : null,
    packageName: candidate.packageName.trim(),
    assetName: typeof candidate.assetName === 'string' && candidate.assetName.trim().length > 0
      ? candidate.assetName.trim()
      : null,
    fileSizeBytes: isNumber(candidate.fileSizeBytes) ? candidate.fileSizeBytes : null,
  };
}
