const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

function required(name) {
  const value = process.env[name];
  if (!value || !String(value).trim()) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return String(value).trim();
}

function optionalNumber(name) {
  const value = process.env[name];
  if (!value || !String(value).trim()) return null;
  const parsed = Number.parseInt(String(value).trim(), 10);
  if (!Number.isFinite(parsed)) {
    throw new Error(`Invalid integer for ${name}: ${value}`);
  }
  return parsed;
}

function optionalBoolean(name, fallback = false) {
  const value = process.env[name];
  if (!value || !String(value).trim()) return fallback;
  return ['1', 'true', 'yes', 'on', 'required'].includes(String(value).trim().toLowerCase());
}

function optionalString(name) {
  const value = process.env[name];
  if (!value || !String(value).trim()) return null;
  return String(value).trim();
}

function optionalFileString(name) {
  const filePath = optionalString(name);
  if (!filePath) return null;
  return fs.readFileSync(path.resolve(filePath), 'utf8').trim();
}

function sha256(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

function main() {
  const apkPath = path.resolve(required('UPDATE_APK_PATH'));
  const outputPath = path.resolve(required('UPDATE_MANIFEST_OUTPUT_PATH'));

  if (!fs.existsSync(apkPath)) {
    throw new Error(`APK file not found: ${apkPath}`);
  }

  const stat = fs.statSync(apkPath);
  const assetName = required('UPDATE_ASSET_NAME');
  const manifest = {
    platform: required('UPDATE_PLATFORM'),
    packageName: required('UPDATE_PACKAGE_NAME'),
    versionCode: Number.parseInt(required('UPDATE_VERSION_CODE'), 10),
    versionName: required('UPDATE_VERSION_NAME'),
    assetName,
    apkUrl: required('UPDATE_APK_URL'),
    checksumSha256: sha256(apkPath),
    fileSizeBytes: stat.size,
    required: optionalBoolean('UPDATE_REQUIRED', false),
    minSupportedVersionCode: optionalNumber('UPDATE_MIN_SUPPORTED_VERSION_CODE'),
    requiredReason: optionalString('UPDATE_REQUIRED_REASON'),
    releaseNotes: optionalFileString('UPDATE_RELEASE_NOTES_FILE') ?? optionalString('UPDATE_RELEASE_NOTES') ?? '',
    publishedAt: optionalString('UPDATE_PUBLISHED_AT'),
  };

  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, JSON.stringify(manifest, null, 2));
}

main();
