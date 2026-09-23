# Media classification and metadata resolution

This change spans StreamDekMobile, StreamDekTV and StreamDekBackend. Deploy the backend before releasing the updated clients, especially for server-side stream requests. No release or deployment is part of this change.

## Findings

- **Invalid `other` details requests:** Mobile copied the catalogue resource type over each item's type. Thus a series in an `other` catalogue lost its series classification. This is present in the imported native history at `134cd906`. Mobile `03a3793` added a request guard, but did not repair classification or resolve the provider item. Home, Search, Browse, Continue Watching and Watchlist converge on the same detail loader.
- **Provider metadata 404s:** a canonical series type does not tell the backend which native provider resource to request. Generic lookup used `series`, so an `other` provider could be excluded. The source-specific proxy also rewrote native `tv` to `series` and decoded already-decoded IDs a second time. The resolver accepted ID-only metadata and stopped before trying another useful provider; it ignored resource-level ID prefixes. These are distinct ways to reach the reported failure stage.
- **Database identity defects:** the details route reused a profile-identity parser that did not understand `tmdb:tv:1399` and could extract an IMDb substring from an opaque provider ID. The route acquired this reader in backend `6e5cc311`. Details now use a strict, bounded identity reader.
- **TV-specific defects:** `other` was included in the live-type set by `44ba9e6b`. TV also preferred a numeric enrichment ID over the original provider ID and did not guard unsupported detail types.

The supplied 404 event does not identify the provider or item. These code defects are verified, but the exact cause of that production event and whether the approximately 72 events concern one or many items cannot be established from the supplied summaries. No production database or provider session was accessed.

## Contract

Canonical content classification is `movie`, `tv`, `live` or `unknown`. Only `movie` and `tv` enter TMDB enrichment. Provider resource addressing is separate: preserve the originating add-on, original item ID, original item type and catalogue resource type.

Canonical alias matching ignores case, surrounding whitespace, spaces, underscores and hyphens:

| Canonical | Recognised values before separator normalization |
| --- | --- |
| movie | movie, movies, film, films, feature film, feature films, TV movie |
| tv | tv, series, show, shows, TV show, TV shows, TV series, television, television show, television series |
| live | live, live TV, channel, channels, TV channel, TV channels, event, events, sport, sports, IPTV |
| unknown | Missing values, other, anime, documentary and unrecognised provider classifications without further evidence |

Anime and documentary are genres, not proof of movie versus series. Item type takes precedence over a mixed catalogue. Typed database IDs and real season/episode metadata can establish a missing or ambiguous classification.

**Stremio ambiguity:** a native catalogue resource named `tv` denotes channels. An item labelled `tv` in a series or mixed catalogue denotes a series; explicit episode evidence or a typed series ID also overrides the ambiguous native catalogue spelling. Canonical API `tv` always means series. Explicit live/channel values never become series merely because their names resemble a show. The [official protocol](https://stremio.github.io/stremio-addon-sdk/protocol.html) documents native live-TV catalogues and separate metadata/video identifiers.

The backend normalizes recognised metadata aliases before validating the route. Unsupported types still fail validation; `other` was not added to the TMDB allowlist. Provider-specific calls retain their original resource spelling. Generic provider lookup maps canonical requests to aliases actually advertised by each provider.

Mobile and TV keep identical Kotlin classification, database-identity and source-reference helpers. Run `scripts/check-media-metadata-parity.ps1` to detect drift. Backend tests exercise the equivalent alias vocabulary and the real validated HTTP boundary.

## Resolution and playback

1. Ask the originating provider using its original resource type and ID. Device-local providers are contacted on the device.
2. Use valid provider metadata and preserve its episode stream IDs. Retain catalogue preview metadata when the provider cannot supply more.
3. Enrich using explicit TMDB/IMDb identities. A provider-owned bare number is not assumed to be TMDB. Never mine opaque identifiers for embedded database IDs.
4. Mobile title fallback requires a unique exact normalized title, matching canonical type and matching known year. Ambiguous candidates remain unmatched. It no longer races movie and TV lookups.
5. Source-owned stream requests preserve native type and identifier. Metadata-only add-ons can still use other stream providers through a recognised identity.

TV navigation carries a source-qualified reference; both clients understand that reference. This keeps identical opaque IDs from different add-ons distinct. Sync progress accepts bounded references up to 2,048 characters, while source metadata/stream IDs remain bounded to 512. Native server-side stream lookup is explicit through `nativeIdentity`, scoped to the installed source and existing entitlement checks.

Backend provider misses and in-flight lookups retain their existing owner-scoped cache. TV source metadata caches results/misses briefly within profile scope. Unknown content is not manufactured into a movie or series to pass a metadata request.

## Diagnostics and verification boundary

Diagnostics include the bounded original type category, canonical category, identifier category, lookup stage, failure reason and provider selection counts when a provider walk occurs. Source-specific metadata has a provider fingerprint. Process-keyed HMAC fingerprints allow repeated lookups to be grouped without storing raw IDs, credentials or provider URLs. Fingerprints rotate when the backend process restarts and cannot reconstruct older events. Existing platform, version and request-correlation fields remain in use.

Automated validation covers alias variants, contextual `tv`, opaque/namespaced IDs, missing types, incomplete metadata, provider prefix rules, cached fallback, live versus VOD, source-reference round trips and catalogue/library navigation identity. Build/test results are recorded in the accompanying task response. These checks do not prove live provider availability, playback, visual behavior, deployment or the disappearance of production events.

Mobile's final full JVM suite passed 731 tests; TV's full JVM suite passed 570 tests; the backend's focused suite passed 18 tests and TypeScript compilation passed. Both Android suites compiled the updated production code. The shared Android contract parity check passed. Mobile's hard-coded-string check passed at 382 against a ceiling of 385. TV's corresponding check remains at 124 against 107: an untouched HEAD archive produces the same 124 findings, so this is a pre-existing gate failure, not an increased ceiling or a passing check.

Before release, exercise a real mixed catalogue, a local `other` provider with opaque episode IDs, an alias-labelled series, and a genuine live-TV catalogue on both clients. Verify detail opening, season selection, immediate playback, next episode, and returning from saved library entries. After backend deployment, use the new fingerprints and failure stages to determine recurrence; older anonymized events cannot be reconstructed by these changes.
