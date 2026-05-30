export type UpdatePlatform = 'android-mobile' | 'android-tv';

export type UpdateManifest = {
  platform: UpdatePlatform;
  versionCode: number;
  versionName: string;
  apkUrl: string;
  releaseNotes: string;
  required: boolean;
  publishedAt: string | null;
  checksumSha256: string | null;
  minSupportedVersionCode: number | null;
  requiredReason: string | null;
  packageName: string;
  assetName: string | null;
  fileSizeBytes: number | null;
};

export type CurrentVersionInfo = {
  packageName: string;
  versionName: string;
  versionCode: number;
};

export type ApkValidationResult = {
  isValid: boolean;
  packageName: string;
  versionName: string;
  versionCode: number;
  packageMatches: boolean;
  versionMatches: boolean;
  signatureMatches: boolean;
};

export type DownloadProgress = {
  downloadedBytes: number;
  totalBytes: number | null;
  progressPercent: number | null;
};

export type DownloadResult = {
  filePath: string;
  sha256: string;
  fileSizeBytes?: number;
};

export type InstallResult = {
  status: 'installed' | 'cancelled' | 'failed';
  resultCode?: number;
};

export type UpdateCheckOutcome =
  | { status: 'up-to-date'; current: CurrentVersionInfo }
  | { status: 'update-available'; current: CurrentVersionInfo; release: UpdateManifest; isMandatory: boolean }
  | { status: 'unsupported-device' }
  | { status: 'error'; message: string };
