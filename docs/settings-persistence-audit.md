# Settings persistence audit: Mobile, TV and shared backend

Date: 2026-10-02. Scope: Mobile `C:/Dev/StreamDekMobile`, TV `C:/Dev/StreamDekTV`, and shared settings writers in `C:/Dev/StreamDekBackend/streamdek-backend`.

## Conclusion

There were concrete architectural paths that could lose or replace preferences on both clients. The reported user's exact device sequence has not been reproduced. The fixes address demonstrated storage, ownership, hydration and concurrent-write gaps; automated tests reproduce the corresponding failure conditions. A real-device force-stop/reboot and deployed-service check remain release-validation work.

## Findings and repairs

| Gap | Consequence | Repair |
| --- | --- | --- |
| Mobile sent full settings snapshots from independent coroutines; failed saves were not durably queued | A slow old request or cloud default could replace a newer local choice | Persist each changed key with a revision, serialize network operations, send sparse changes, acknowledge only the revision sent, retry failures |
| Mobile startup/foreground reads and profile selection could overlap | A response for an old owner could hydrate the current profile | Capture account/profile/generation; reject stale responses; hydrate the selected local store before cloud reads |
| Mobile profile migration used an incomplete allow-list and skipped partially populated stores | Some controls read installation values or defaults after profile changes/upgrades | Derive scope from the settings registry; fill missing legacy keys without replacing existing profile values; version migrations |
| Mobile synced device preferences shared one installation cache between accounts | Offline account changes could expose another account's choices | Separate account caches, with a device projection only for startup readers; hardware-only settings stay local |
| TV bootstrap state started empty and a failed read could publish null | UI and features fell back to defaults offline | Persist account/profile preference snapshots; initialize from them; retain current/cached values on failed reads |
| TV edits rebuilt complete sections and depended on successful immediate networking | Unrelated defaults could be uploaded; failed changes disappeared at restart | Synchronous sparse journal writes before network work, pending overlays, serialized uploads/reads and bounded background retry |
| TV remembered active profile id was installation-wide | Switching accounts could reuse an unrelated profile id | Namespace the remembered profile by account and migrate the legacy key once |
| Preference setters used asynchronous SharedPreferences writes, often without checking disk errors | Abrupt process death could occur before the disk write; failures were invisible | Checked commit boundary for user preferences; localized save-failure feedback and diagnostics |
| Empty rating lists and selected boolean false values needed distinct treatment from absence | An intentional empty/false choice could appear as defaults | Explicit presence/type parsing; empty selections retained; codec regressions for false, zero, empty strings and arrays |
| Audio language existed in both preferences and legacy profile metadata | Settings and playback could disagree, especially across clients | Durable language save covers both representations; Mobile reads profile metadata in the same reconciliation; TV Settings, stream ranking and player use the effective language |
| Profile PUT replaced supplied sections after a client-side read; account writes used read/upsert | Concurrent devices could overwrite unrelated changes | Backend conditional writes retry against the latest document; profile GET advertises sparse merge support; new clients send only edited fields when supported |
| Add-on initialization, favourites and credential migrations also rewrote the settings JSON | Those operations could revert settings without any Settings interaction | Route these writers through the same conditional-write helpers |

PiP, gesture/seek settings and Fuse were traced through the Mobile store into UI state and player/Home consumers. TV exposes the live progress/seekbar preference and a local Fuse switch; there is no TV PiP setting in Settings. Fuse remains intentionally device-local on TV.

## History

- Mobile's full-snapshot synchronization was present in `134cd90` (2026-08-01). `73693da` (2026-09-19, “Sync the settings the phone kept to itself”) exposed more previously local controls, including Fuse/player controls, to that mechanism. This establishes code history, not proof of the reporting user's update path.
- TV's null bootstrap initialization traces to `694f158` (2026-05-02). `1c89e3e` (2026-09-19) introduced the TV platform reconciliation path whose defaults could be uploaded before a reliable local/cloud reconciliation.
- The legacy credential migration writer existed before this repair (`f765c68`, 2026-09-03). Its stale JSON write could also undo unrelated preference changes.

## Sources of truth and inventory

- [Mobile inventory](settings-persistence-inventory.md): 123 registered local keys and their cloud mapping. `BackupSettingsRegistry` owns local/profile scope; `SettingsSyncRegistry` declares every cloud field and compound-key protection. `CloudSettingsCodec` owns the wire representation.
- TV: `docs/settings-persistence-inventory.md` in the TV repository lists 92 model fields plus local stores. `PreferenceScopes` and `PlatformPreferences` determine scope; `TvSettingsJournal` is the disk-backed cache and outbox.
- Backend: `settingsSchema.ts` declares shared/platform fields and sparse-account migration. `settingsPersistence.ts` merges changes and performs conditional writes. No database schema migration is required by this repair.
- Guest settings remain on disk without cloud uploads. Choosing a different account/profile changes the owning cache before remote hydration.
- Temporary subtitle timing, currently selected playback tracks, temporary speed and per-playback adjustments remain session-only. Explicit default audio delay, subtitle appearance and other preference controls persist.

Additional Mobile stores: DNS and codec options are local hardware preferences; default audio delay is local; release-notification settings and display-name overrides have separate local stores; custom subtitle sources are owner-scoped and now use checked writes; plugin managers retain their existing profile document sync with durable local storage. Service credentials use their encrypted credential contract. Add-ons, playlists, server connections and watched/library records are resource APIs, not entries in the settings document. Third-party plugin code can write its own storage; the app-controlled restore path checks commit success.

## Lifecycle and validation

| Scenario | Automated evidence | Device/service boundary |
| --- | --- | --- |
| Normal restart / immediate termination | Real Mobile store recreated over disk-backed test storage; TV journal and local preference wrapper recreated; Android two-phase fixtures compile | Physical process-kill behavior not executed in this session |
| Rapid repeated changes | 50 writes followed by recreation; older acknowledgements cannot clear newer edits | Main-thread disk-write latency still needs device measurement |
| Login/logout and profile changes | Distinct account, profile and guest caches; generation protection; remembered owner scopes | No live authentication session exercised |
| Offline startup / unavailable cloud | Cached values retained, pending edits remain durable and mask stale downloads | No production outage simulation |
| Slow upload/download | Mobile coordinator tests interleave edits, downloads and owner switches; TV revision tests retain newer values | TV repository HTTP timing tested indirectly through journal contracts |
| Upgrade/migration | Mobile partially populated profile repair, backup migrations, missing-key coverage; TV legacy device migration preserves stored keys | No APK upgrade over a real user's old installation |
| Fresh restoration | Cloud/profile restoration with false/empty values; every Mobile cloud field and every non-credential TV model field round-trips | No fresh production sign-in executed |
| Concurrent device changes | Backend tests race account creation/updates, profile settings, add-on initialization, favourites and credential cleanup | No real PostgreSQL concurrency/load test or deployed backend check |

Validation commands:

```powershell
# From each Android project
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin

# From each app repository
node scripts/check-translations.mjs

# From streamdek-backend
node --test --test-isolation=none --require ts-node/register/transpile-only src/lib/__tests__/settingsPersistence.test.ts src/config/__tests__/settingsSchema.test.ts src/services/__tests__/serviceCredentials.test.ts
node node_modules/typescript/bin/tsc --noEmit --ignoreDeprecations 6.0
```

Full unit results: Mobile 814 passing; TV 640 passing. Backend focused settings/schema/credential suite: 46 passing. Both Android instrumentation suites compile. Localization consistency passes for both clients. TV hard-coded-string check passes (105/107). Mobile retains the existing hard-coded-string ratchet failure (544/385); the ceiling was not raised and the new failure message is resource-backed in all eight locales. No builds were installed, no connected device was force-stopped/rebooted, and no backend was deployed by this task.

## Real-device checks prepared

The existing instrumentation runners accept `-e suite settings -e phase write` and `-e suite settings -e phase read`. They write only dedicated fixture preferences, not viewer settings. Build/install the debug app and debug test APK on a chosen test device first. Run the write phase, force-stop the target package (or reboot the device), then run the read phase in a new process.

Mobile runner: `net.streamdek.mobile.test/net.streamdek.mobile.nativeapp.NextUpDeviceInstrumentation`.
TV runner: `com.streamdek.tv.test/com.streamdek.tv.nativeapp.data.ContentSafetyInstrumentation`.

Example after installation on the chosen device:

```powershell
adb -s DEVICE shell am instrument -w -e suite settings -e phase write com.streamdek.tv.test/com.streamdek.tv.nativeapp.data.ContentSafetyInstrumentation
adb -s DEVICE shell am force-stop com.streamdek.tv
adb -s DEVICE shell am instrument -w -e suite settings -e phase read com.streamdek.tv.test/com.streamdek.tv.nativeapp.data.ContentSafetyInstrumentation
```

For reboot coverage, repeat write, reboot the test device, wait for Android to finish booting, then read. These fixtures test actual Android storage and pending-write recovery; they do not replace manually selecting settings, observing player/Home behavior, and exercising a real login/profile/cloud session.

## Diagnostics and rollout

Mobile logs `StreamDekSettings` events for local commit, upload acknowledgement/failure, pending metadata recovery, hydration and stale-owner rejection. TV uses `TvDebugLogger` category `Settings`. Diagnostics contain event names/counts/error types, not setting values, account ids or secrets. Disk failures also produce localized user feedback.

Deploy the backend repair and release the updated apps to enable the complete cross-device fix. New clients remain usable against older backends, but old servers retain the section-level read/merge/write race. Legacy clients can still submit stale full sections even against the repaired server; updating both clients is needed to avoid that behavior. Two devices deliberately changing the same preference still use the last successfully accepted value.

Existing local values cannot reveal whether a historical stored default was intentional. The sparse-account migration and local protection prevent new default overwrites; they cannot reconstruct an already lost choice. Unsent local edits are deliberately preferred during upgrade and uploaded before hydration.
