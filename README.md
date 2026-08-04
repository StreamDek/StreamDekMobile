# StreamDek Android Kotlin

Native Android StreamDek client built with Kotlin and Jetpack Compose.

This repository is the detached `StreamDekMobile-Kotlin` line. The old React Native / Expo app path has been removed from the repository and build flow.

## What is here

- Native app UI in `android/app/src/main/java/net/streamdek/mobile/nativeapp/`
- Android entrypoint in `android/app/src/main/java/net/streamdek/mobile/MainActivity.kt`
- Native MPV playback via `android/app/src/main/java/net/streamdek/mobile/mpv/MPVView.kt`
- Native torrent streaming service under `android/app/src/main/java/net/streamdek/mobile/torrent/`

## Requirements

- Android Studio or JDK 17+
- Android SDK Platform 36
- A backend URL in the root `.env`

Example:

```env
STREAMDEK_API_URL=http://192.168.0.2:3000
```

## Build

From `android/`:

```powershell
.\gradlew :app:compileDebugKotlin
.\gradlew :app:assembleDebug
.\gradlew :app:installDebug
.\gradlew :app:assembleRelease
```

## Device install

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## Production release

Production Android releases are published from StreamDek/StreamDekMobile by
.github/workflows/release.yml. Pushing a semantic version tag such as v2.0.4
builds the signed APK, creates the GitHub Release, generates latest.json, and
uploads that update manifest to the backend VPS.

The Android package name is permanently net.streamdek.mobile. Do not change the
application ID or UPDATE_PACKAGE_NAME; existing installations and in-app
updates depend on that identity and the production signing key remaining stable.

### One-time GitHub configuration

Configure these Actions values in StreamDek/StreamDekMobile:

- Repository variable or secret: STREAMDEK_API_URL
- Secrets: ANDROID_KEYSTORE_BASE64, ANDROID_STORE_PASSWORD,
  ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD
- Deployment secrets: SSH_HOST, SSH_USER, SSH_PRIVATE_KEY, SSH_PORT,
  and DEPLOY_PATH

DEPLOY_PATH is the backend checkout directory on the VPS. The workflow writes
the mobile manifest to:

~~~text
${DEPLOY_PATH}/release-manifests/android-mobile/latest.json
~~~

The backend mounts release-manifests at /app/release-manifests and serves the
file through:

~~~text
https://api.streamdek.net/public/updates/android-mobile/latest
~~~

### Prepare a release

1. Update the shared Android version in android/version.properties. Increase
   VERSION_CODE for every release and set VERSION_NAME to the tag version
   without the leading v.
2. Set the same version in the root package.json.
3. Add optional custom notes at .github/release-notes/vX.Y.Z.md. When this
   file is absent, GitHub generates the release notes.
4. Run the local release gates:

~~~powershell
cd android
.\gradlew :app:testDebugUnitTest :app:assembleRelease --no-daemon
cd ..
git diff --check
~~~

5. Review and commit only the intended release/version/note changes before tagging.

For example, a v2.0.4 release should contain:

~~~properties
VERSION_NAME=2.0.4
VERSION_CODE=43
~~~

Local installDebug, installRelease, and CI all read android/version.properties.
A local release build uses the debug key unless the four ANDROID_* signing
environment variables are configured; a debug-key APK cannot upgrade a
production-signed installation.

### Publish to the live repository

Confirm the live branch has not moved and that the tag is unused, then push the
validated commit and annotated tag to StreamDekMobile (not the CMP repository):

~~~powershell
git ls-remote https://github.com/StreamDek/StreamDekMobile.git refs/heads/main refs/tags/vX.Y.Z
git push https://github.com/StreamDek/StreamDekMobile.git HEAD:main
git tag -a vX.Y.Z -m "StreamDek Mobile vX.Y.Z"
git push https://github.com/StreamDek/StreamDekMobile.git refs/tags/vX.Y.Z
~~~

The tag must exactly match VERSION_NAME; otherwise the release workflow stops
before building.

### What the workflow publishes

The release workflow:

1. Validates the tag against android/version.properties.
2. Builds a minified APK signed with the production keystore.
3. Publishes streamdek-vX.Y.Z.apk in the GitHub Release.
4. Generates dist/latest.json with scripts/generate-update-manifest.js.
5. Includes package name, version code/name, APK URL, SHA-256 checksum, file
   size, release notes, required-update policy, and publication time.
6. Uploads the manifest as a workflow artifact and to
   ${DEPLOY_PATH}/release-manifests/android-mobile/latest.json.

The package field in the manifest must always be:

~~~json
{
  "platform": "android-mobile",
  "packageName": "net.streamdek.mobile"
}
~~~

### Verify the live release

Do not treat a successful APK build alone as a completed release. Confirm the
workflow, GitHub Release, public manifest, and installed APK:

~~~powershell
gh run list --repo StreamDek/StreamDekMobile --workflow release.yml --limit 3
gh release view vX.Y.Z --repo StreamDek/StreamDekMobile --json url,assets,publishedAt
curl.exe -sS "https://api.streamdek.net/public/updates/android-mobile/latest?verify=$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
~~~

Check that the public manifest has the intended versionCode, versionName,
net.streamdek.mobile, release APK URL, file size, and SHA-256 checksum. The
checksum must equal the digest shown by the GitHub Release asset.

For a connected-device upgrade test, start from the previous production build,
use the in-app update prompt, then verify:

~~~powershell
adb shell dumpsys package net.streamdek.mobile | Select-String 'versionCode=|versionName='
adb logcat -d -v brief | Select-String 'FATAL EXCEPTION|AndroidRuntime'
~~~

The upgrade must preserve app data and use the same production signing
certificate. Never uninstall the existing app merely to work around a signing
mismatch.

### Generate a manifest manually for testing

The automated workflow is the production path. To inspect a manifest locally,
set the same inputs CI uses and run the generator against an existing APK:

~~~powershell
$env:UPDATE_PLATFORM='android-mobile'
$env:UPDATE_PACKAGE_NAME='net.streamdek.mobile'
$env:UPDATE_VERSION_CODE='43'
$env:UPDATE_VERSION_NAME='2.0.4'
$env:UPDATE_ASSET_NAME='streamdek-v2.0.4.apk'
$env:UPDATE_APK_PATH='android/app/build/outputs/apk/release/app-release.apk'
$env:UPDATE_APK_URL='https://github.com/StreamDek/StreamDekMobile/releases/download/v2.0.4/streamdek-v2.0.4.apk'
$env:UPDATE_MANIFEST_OUTPUT_PATH='android/app/build/outputs/update-test/latest.json'
$env:UPDATE_REQUIRED='false'
node scripts/generate-update-manifest.js
Get-Content android/app/build/outputs/update-test/latest.json
~~~

Do not manually replace the live manifest during a normal release. Fix and
rerun a failed workflow while the tag still points at the same release commit;
do not move or reuse a published tag for different code.

## Current scope

- Auth
- Profiles
- Add-ons
- Debrid
- Trakt device auth, watchlist sync, and playback scrobbling
- Native detail, stream selection, and MPV playback

## Notes

- The repository is Kotlin/Android-only.
- The original mixed mobile repo remains separate in `C:\Dev\StreamDekMobile`.
