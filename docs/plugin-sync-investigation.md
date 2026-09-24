# Plugin sync and expected sign-in telemetry

Updated 2026-09-24. Production incidents were reported on Mobile 2.1.22 and TV 0.3.7. Production has not been changed by this investigation.

## Confirmed findings

- Fastify's installed body parser assigns HTTP 400 to an incoming stream error without a status. An interrupted upload reproduces the reported aborted-request class. The production record alone cannot establish whether the phone, network, proxy, or server closed the connection.
- An incomplete JSON body never reaches the profile persistence handler. Existing writes were single database updates, but concurrent read/merge/write operations could overwrite changes made by another device.
- Mobile cancelled and replaced its debounce job on each edit, had several upload entry points, and lacked durable failed-upload tracking. Blocking OkHttp execution does not itself prove that coroutine cancellation caused the production connection reset.
- Mobile previously converted invalid serialized plugin JSON into an empty object. The upload method now rejects it.

## Changes

Mobile serializes plugin uploads through one mutex, coalesces local edits without cancelling an active upload, and retries transient failures up to three times with exponential delay and jitter. Pending fingerprints survive client recreation; plugin contents remain in the existing profile stores. The existing foreground version watcher retries eligible pending work without an additional polling timer. Permanent failures remain pending until a fresh edit; transient exhaustion has a cooldown. A pending local change blocks cloud refresh from overwriting it.

The backend accepts the complete 2 MiB plugin document plus its request envelope, validates before writing, and uses compare-and-swap with bounded merge retries. Existing section timestamps and source-setting clocks prevent stale sections replacing newer ones. Omitted engine sections are preserved; explicit newer empty sections represent removals. Identical uploads are idempotent. This retains the existing clock contract: simultaneous edits within the same section and clock skew are not a new conflict-free merge protocol.

Incoming disconnections are retained as network failures (`CLIENT_DISCONNECTED`) with request-body/response stage and completeness diagnostics, rather than fabricated HTTP 400 responses. Validation errors and genuine server failures remain visible.

Guest favourite requests now return HTTP 401 with `SIGN_IN_REQUIRED`. Telemetry records `sign_in_required` / `action_required`, without an API error or authentication-failure count. Invalid credentials still produce an authentication failure. Historical production records are not rewritten.

## Verification

- 28 focused backend tests passed, covering the real plugin route with a truncated socket upload, malformed JSON, large complete payloads, oversized rejection, concurrent database writes, stale sections, source switches, version clocks, guest sign-in classification, and API metrics.
- Backend TypeScript validation passed.
- A live local Docker guest request returned the v1 `SIGN_IN_REQUIRED` envelope. Its database records contain action-required request/sign-in events with null error fields, and a usage row with zero authentication failures.
- Mobile: 736 unit tests passed with zero failures/errors; the debug APK built successfully using `http://192.168.0.9:3000` and installed on the connected phone with `adb install -r`, preserving app data. Physical background/reconnection recovery and cross-device convergence have not been verified; compilation and installation do not establish those behaviors.

## TV crash limitation

The supplied TV reports contain only the first application frame, `MainActivity.dispatchKeyEvent:38`. That identifies a propagation point, not the exception's cause. Current TV source already contains bounded cause/framework-frame and input diagnostics that retain the exception. No blanket exception suppression was added. A richer report or reproduction is required to diagnose the production crash.
