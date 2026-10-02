# Settings persistence (Mobile)

How every StreamDek Mobile setting is stored, synced and restored, what was wrong, and how to
diagnose a report of "my setting went back to default".

## Root cause of the reverting settings

Picture-in-Picture, StreamDek Fuse (`media_hub_enabled`) and the seek/progress-bar settings are all
**cloud-synced** settings. Each was written to the device correctly; they were then overwritten on the
next launch by the account's copy. Five shared defects made that possible - none of them specific to
those three settings:

1. **Cloud hydration ignored unsent local edits.** Startup, foreground and profile-switch refreshes
   wrote whatever the account returned straight into local storage.
2. **Failed uploads were dropped.** A setting changed offline, or just before the app was closed, was
   never sent again - so the account kept the old value and (1) restored it on relaunch.
3. **The backend fills in defaults on read** (PiP comes back *disabled*), so an account that had never
   received the value still "had" one, and it won.
4. **Every change uploaded the whole settings snapshot**, unserialised. Two overlapping requests could
   land out of order and put an older value back.
5. **No owner check on responses.** A fetch started for one account/profile could be applied after a
   logout or profile switch; startup could fetch account defaults before the profile was restored;
   logout left the store pointing at the departing profile.

Also found and fixed: the legacy profile-migration list omitted newer profile settings; an empty
rating-provider selection was read back as "defaults"; the profile audio language was never stored
on the device at all.

## How it works now

| Step | Behaviour |
| --- | --- |
| User changes a setting | Value **and** a pending-upload revision are committed to disk in one synchronous transaction before the setter returns (`DurableSettingsPreferences`). A disk failure is reported to the user. |
| Upload | One network operation at a time (`SettingsSyncCoordinator`). Only the changed fields are sent. Success clears only the revision that was actually sent; an edit made during a slow request stays pending and is sent next. Failures retry (2 s, 15 s) and again on the next launch/foreground. |
| App closes / is killed | Nothing is lost: the value and its pending marker are already on disk. |
| Relaunch | The store is read synchronously before first composition. Pending edits are uploaded **before** the account is read. |
| Cloud hydration | The account's values are applied except for any key that still has a pending local edit (composite settings are protected as a unit). Downloaded values never create a new upload. |
| Logout / login / profile switch | The in-flight sync is cancelled, the store is re-pointed, and an owner generation is bumped; any late response for the previous owner is discarded. |
| Upgrade | Existing values are journalled once as pending, so the first sync after the update sends the device's real choices instead of accepting backend defaults. Profile keys missing from the old migration list are repaired. |

The audio language is also a field on the profile itself (the TV and web read it there). It is now
stored locally, uploaded in the same acknowledged step as the settings document, and adopted once
from the profile on installs that never had it locally.

## Source of truth

- `BackupSettingsRegistry` (`BackupFormat.kt`) - every setting and whether it is **Device** or **Profile**.
- `SettingsSyncRegistry` - every cloud field and the local key(s) behind it.
- Tests fail if a setting the store reads is in neither, or a cloud field has no local owner.

### 1. Device-only (this install, never synced) - 15

`debrid_cloud_sync`, `doh_enabled`, `doh_provider`, `doh_custom_endpoint`, `downloads_enabled`,
`dv7_hevc_fallback`, `playback_buffer_seconds` (Buffer Ahead), `remember_last_profile_at_startup`, `torrent_enabled`, `torrent_streaming_mode`,
`torrent_profile`, `torrent_cache_size_gb`, `torrent_port`, `torrent_run_foreground`, `tunneled_playback`

### 2. Account-synced device preferences (cloud, per account, cached per account on the device) - 30

`animation_speed`, `app_appearance`, `app_language`, `auto_update_checks`,
`collapsible_navigation_enabled`, `decoder_mode`, `double_tap_play_pause_enabled`,
`double_tap_seek_enabled`, `double_tap_seek_seconds`, `fullscreen_status_bar`, `header_style`,
`hold_to_speed_enabled`, `hold_to_speed_multiplier`, `home_density`, **`media_hub_enabled` (Fuse)**,
`navigation_auto_collapse_seconds`, `navigation_collapse_trigger`, `navigation_expanded_headers`,
**`pip_enabled` (PiP)**, `player_control_layout`, `player_engine`, `player_level_gestures_enabled`,
`player_title_display`, `render_surface`, `show_nav_labels`, `show_player_control_labels`,
**`swipe_to_seek_enabled`**, `sync_on_cellular`, `theme_preset`, `visual_effects`

### 3. Profile-synced (cloud, per profile) - 77

Appearance/Home: `ambient_tint_percent`, `blur_unwatched_episodes`, `continue_watching_style`,
`default_app_catalogs_enabled`, `detail_ambient_tint_percent`, `detail_background_mode`,
`detail_page_style`, `enabled_rating_providers`, `episode_layout`, `external_ratings_enabled`,
`hero_trailer_autoplay`, `hero_trailer_delay_seconds`, `hero_trailer_muted`, `hero_trailer_resolution`,
`home_background_mode`, `home_card_text_mode`, `home_catalog_rows`, `home_row_mode`,
`home_row_source_order`, `network_card_style`, `new_episodes_landscape`, `ratings_enabled`,
`season_tab_style`, `show_hero_synopsis`, `show_new_episodes_row`, `trailer_cache_clear_hours`,
`vivid_ambient`

Live: `live_badge`, `live_categories_enabled`, `live_favourite_drawer_cards`, `live_landscape_cards`,
**`live_progress_bar`**

Playback: `active_fusion_badge_url`, `addon_subtitle_loading`, `auto_load_subtitles`,
`auto_play_next_episode`, `auto_skip_ending_enabled`, `auto_skip_intro_enabled`,
`auto_skip_recap_enabled`, `badge_position`, `end_of_playback_recommendations_enabled`,
`favorite_source_keys`, `fusion_badge_urls`, `fusion_badges`, `max_file_size_gb`,
`next_episode_threshold_minutes`, `next_episode_threshold_mode`, `next_episode_threshold_percent`,
`prefer_binge_group`, `preferred_audio_language`, `preferred_quality`, `preferred_subtitle_language`,
`recommendation_item_count`, `recommendation_timing`, `remember_last_source`,
`secondary_audio_language`, `secondary_subtitle_language`, `show_only_preferred_subtitle_languages`,
`show_size_badges`, `show_streams_list`, `skip_ending_enabled`, `skip_intro_enabled`,
`skip_recap_enabled`, `skip_segments_enabled`, `streamdek_stream_formatting`,
`subtitle_background_color`, `subtitle_bold`, `subtitle_default_source`, `subtitle_outline`,
`subtitle_outline_color`, `subtitle_text_color`, `subtitle_text_size`, `subtitle_vertical_offset`,
`timing_provider`, `timing_provider_fallback_enabled`, `use_forced_subtitles`

General: `primary_sync_service`

### 4. Profile-local (per profile, not in the settings document) - 1

`introdb_api_key` (credential; `mdblist_api_key` is a legacy plaintext copy moved to the encrypted
vault on first read)

### 5. Session-only

None. Per-stream adjustments made inside the player (subtitle/audio delay, zoom) are deliberately
not settings and reset with the stream.

### Other stores

| Store | Scope | Write |
| --- | --- | --- |
| Profile selection, guest profiles, media-server display, display-name overrides, DoH, codec/audio-sync options, plugin managers (StreamDek, SkyStream, CloudStream), episode-notification toggle | Device | Durable (synchronous) |
| Watchlist, favourites, watched/resume history, next-up, search history, trailer cache, crash state | Device/owner data, not settings | Asynchronous (`apply`) |
| Add-ons, M3U playlists, CloudStream source settings, peer engine, debrid keys, service credentials | Own sync or vault | Unchanged |

## Diagnosing a report

`adb logcat -s StreamDekSettings`. No values, account ids, URLs or credentials are logged.

| Line | Meaning |
| --- | --- |
| `local_commit keys=N` | A user edit reached disk with its retry record. |
| `disk_write_failed` | The device could not save; the user was told. |
| `owner_hydrated pending=N` | Store pointed at an account/profile; N edits still unsent. |
| `upload_acknowledged keys=N` / `upload_failed pending=N type=X` | Upload outcome. |
| `cloud_hydrated protected=N` | Account values applied; N keys kept because of pending edits. |
| `download_failed type=X` | Account could not be read; local values stand. |
| `stale_owner_response_ignored` | A response for a previous account/profile was discarded. |
| `pending_metadata_invalid` | Retry record unreadable; every stored synced value is re-sent. |

A revert with `cloud_hydrated protected=0` right after a `local_commit` for that key would be a client
bug. A revert with no `local_commit` at all means the setter never ran.

## Known limits

- **Backend defaults - fixed.** The app now reads `GET /account/preferences?explicit=1`, which returns
  only what the account has set. The backend stores the settings document sparsely (schema version 2)
  and fills defaults only for clients that ask for the complete document (TV, web). Documents written
  before the change have the defaults baked in; there a value equal to the backend default is treated
  as never set. Until the backend is deployed the parameter is ignored and the old behaviour remains.
- **First sync after upgrade is local-wins.** On a second, rarely used device this can send that
  device's older choices over newer ones made elsewhere, once.
- **Guest to new account.** Account-level preferences chosen as a guest are not carried into a newly
  signed-in account unless the guest-data transfer is run.
- **TV is separate.** StreamDek TV has its own settings store and was not part of this change.
