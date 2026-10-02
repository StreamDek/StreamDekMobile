# Mobile settings inventory

Generated from `BackupSettingsRegistry` (local ownership) and `SettingsSyncRegistry` (cloud fields), 2026-10-02. Device entries that also have a cloud field are cached separately for each account; unsynced hardware settings remain installation-wide. Guest profiles persist locally and do not upload.

The registries are the executable source of truth. `BackupSettingsRegistryTest` checks storage-key coverage; `CloudSettingsCodecTest` checks every cloud field against ownership and serialization; `AppSettingsLifecycleTest` exercises actual store setters and recreation. Account scope can contain platform-specific wire values (`platforms.mobile`); see `CloudSettingsCodec` for the exact wire section.

| Local key | Persistence | Cloud field | Category |
| --- | --- | --- | --- |
| `active_fusion_badge_url` | Profile cloud + local profile cache | fusionBadgeUrls, activeFusionBadgeUrl | Playback |
| `addon_subtitle_loading` | Profile cloud + local profile cache | addonSubtitleLoading | Playback |
| `ambient_tint_percent` | Profile cloud + local profile cache | ambientTintPercent | Appearance |
| `animation_speed` | Account cloud + local account cache | animationSpeed | Appearance |
| `app_appearance` | Account cloud + local account cache | appAppearance | Appearance |
| `app_language` | Account cloud + local account cache | appLanguage | Appearance |
| `auto_load_subtitles` | Profile cloud + local profile cache | autoLoadSubtitles | Playback |
| `auto_play_next_episode` | Profile cloud + local profile cache | autoPlayNextEpisode | Playback |
| `auto_skip_ending_enabled` | Profile cloud + local profile cache | autoSkipEndingEnabled | Playback |
| `auto_skip_intro_enabled` | Profile cloud + local profile cache | autoSkipIntroEnabled | Playback |
| `auto_skip_recap_enabled` | Profile cloud + local profile cache | autoSkipRecapEnabled | Playback |
| `auto_update_checks` | Account cloud + local account cache | autoUpdateChecksEnabled | General |
| `badge_position` | Profile cloud + local profile cache | badgePosition | Playback |
| `blur_unwatched_episodes` | Profile cloud + local profile cache | blurUnwatchedEpisodes | Appearance |
| `collapsible_navigation_enabled` | Account cloud + local account cache | collapsibleNavigationEnabled, navigationBehaviour | Appearance |
| `continue_watching_style` | Profile cloud + local profile cache | continueWatchingStyle | Appearance |
| `debrid_cloud_sync` | Local device | — | General |
| `decoder_mode` | Account cloud + local account cache | decoderMode | Playback |
| `default_app_catalogs_enabled` | Profile cloud + local profile cache | defaultAppCatalogsEnabled | Appearance |
| `default_audio_delay_ms` | Local device | — | Playback |
| `detail_ambient_tint_percent` | Profile cloud + local profile cache | detailAmbientTintPercent | Appearance |
| `detail_background_mode` | Profile cloud + local profile cache | detailBackgroundMode | Appearance |
| `detail_page_style` | Profile cloud + local profile cache | detailPageStyle | Appearance |
| `doh_custom_endpoint` | Local device | — | General |
| `doh_enabled` | Local device | — | General |
| `doh_provider` | Local device | — | General |
| `double_tap_play_pause_enabled` | Account cloud + local account cache | doubleTapPlayPauseEnabled | Playback |
| `double_tap_seek_enabled` | Account cloud + local account cache | doubleTapSeekEnabled | Playback |
| `double_tap_seek_seconds` | Account cloud + local account cache | doubleTapSeekSeconds | Playback |
| `downloads_enabled` | Local device | — | Playback |
| `dv7_hevc_fallback` | Local device | — | Playback |
| `enabled_rating_providers` | Profile cloud + local profile cache | enabledRatingProviders | Appearance |
| `end_of_playback_recommendations_enabled` | Profile cloud + local profile cache | endOfPlaybackRecommendationsEnabled | Playback |
| `episode_layout` | Profile cloud + local profile cache | episodeLayout | Appearance |
| `external_ratings_enabled` | Profile cloud + local profile cache | externalRatingsEnabled | Appearance |
| `favorite_source_keys` | Profile cloud + local profile cache | favoriteSourceKeys | Playback |
| `fullscreen_status_bar` | Account cloud + local account cache | fullscreenStatusBar | Playback |
| `fusion_badge_urls` | Profile cloud + local profile cache | fusionBadgeUrls, activeFusionBadgeUrl | Playback |
| `fusion_badges` | Profile cloud + local profile cache | fusionBadgesEnabled | Playback |
| `header_style` | Account cloud + local account cache | headerStyle | Appearance |
| `hero_trailer_autoplay` | Profile cloud + local profile cache | heroTrailerAutoplay | Appearance |
| `hero_trailer_delay_seconds` | Profile cloud + local profile cache | heroTrailerDelaySeconds | Appearance |
| `hero_trailer_muted` | Profile cloud + local profile cache | heroTrailerMuted | Appearance |
| `hero_trailer_resolution` | Profile cloud + local profile cache | heroTrailerResolution | Appearance |
| `hold_to_speed_enabled` | Account cloud + local account cache | holdToSpeedEnabled | Playback |
| `hold_to_speed_multiplier` | Account cloud + local account cache | holdToSpeedMultiplier | Playback |
| `home_background_mode` | Profile cloud + local profile cache | homeBackgroundMode | Appearance |
| `home_card_text_mode` | Profile cloud + local profile cache | homeCardTextMode | Appearance |
| `home_catalog_rows` | Profile cloud + local profile cache | homeCatalogRowsJson | Appearance |
| `home_density` | Account cloud + local account cache | homeDensity | Appearance |
| `home_row_mode` | Profile cloud + local profile cache | homeRowMode | Appearance |
| `home_row_source_order` | Profile cloud + local profile cache | homeRowSourceOrder | Appearance |
| `live_badge` | Profile cloud + local profile cache | liveBadgeEnabled | Playlists |
| `live_categories_enabled` | Profile cloud + local profile cache | liveCategoriesEnabled | Playlists |
| `live_favourite_drawer_cards` | Profile cloud + local profile cache | liveFavouriteDrawerCards | Playlists |
| `live_landscape_cards` | Profile cloud + local profile cache | liveLandscapeCards | Playlists |
| `live_progress_bar` | Profile cloud + local profile cache | liveProgressBarEnabled | Playlists |
| `max_file_size_gb` | Profile cloud + local profile cache | maxFileSizeGb | Playback |
| `media_hub_enabled` | Account cloud + local account cache | mediaHubEnabled | Appearance |
| `navigation_auto_collapse_seconds` | Account cloud + local account cache | navigationAutoCollapseSeconds | Appearance |
| `navigation_collapse_trigger` | Account cloud + local account cache | navigationBehaviour | Appearance |
| `navigation_expanded_headers` | Account cloud + local account cache | navigationBehaviour | Appearance |
| `network_card_style` | Profile cloud + local profile cache | networkCardStyle | Appearance |
| `new_episodes_landscape` | Profile cloud + local profile cache | newEpisodesLandscape | Appearance |
| `next_episode_threshold_minutes` | Profile cloud + local profile cache | nextEpisodeThresholdMinutes | Playback |
| `next_episode_threshold_mode` | Profile cloud + local profile cache | nextEpisodeThresholdMode | Playback |
| `next_episode_threshold_percent` | Profile cloud + local profile cache | nextEpisodeThresholdPercent | Playback |
| `pip_enabled` | Account cloud + local account cache | pictureInPictureEnabled | Playback |
| `playback_buffer_seconds` | Local device | — | Playback |
| `player_control_layout` | Account cloud + local account cache | playerControlLayout | Playback |
| `player_engine` | Account cloud + local account cache | playerEngine | Playback |
| `player_level_gestures_enabled` | Account cloud + local account cache | playerLevelGesturesEnabled | Playback |
| `player_title_display` | Account cloud + local account cache | playerTitleDisplay | Playback |
| `prefer_binge_group` | Profile cloud + local profile cache | preferBingeGroup | Playback |
| `preferred_audio_language` | Profile cloud + local profile cache | preferredAudioLanguage | Playback |
| `preferred_quality` | Profile cloud + local profile cache | preferredQuality | Playback |
| `preferred_subtitle_language` | Profile cloud + local profile cache | preferredSubtitleLanguage | Playback |
| `primary_sync_service` | Profile cloud + local profile cache | primarySyncService | General |
| `ratings_enabled` | Profile cloud + local profile cache | ratingsEnabled | Appearance |
| `recommendation_item_count` | Profile cloud + local profile cache | recommendationItemCount | Playback |
| `recommendation_timing` | Profile cloud + local profile cache | recommendationTiming | Playback |
| `remember_last_profile_at_startup` | Local device | — | General |
| `remember_last_source` | Profile cloud + local profile cache | rememberLastSource | Playback |
| `render_surface` | Account cloud + local account cache | renderSurface | Playback |
| `season_tab_style` | Profile cloud + local profile cache | seasonTabStyle | Appearance |
| `secondary_audio_language` | Profile cloud + local profile cache | secondaryAudioLanguage | Playback |
| `secondary_subtitle_language` | Profile cloud + local profile cache | secondarySubtitleLanguage | Playback |
| `show_hero_synopsis` | Profile cloud + local profile cache | showHeroSynopsis | Appearance |
| `show_nav_labels` | Account cloud + local account cache | showNavLabels | Appearance |
| `show_new_episodes_row` | Profile cloud + local profile cache | showNewEpisodesRow | Appearance |
| `show_only_preferred_subtitle_languages` | Profile cloud + local profile cache | showOnlyPreferredSubtitleLanguages | Playback |
| `show_player_control_labels` | Account cloud + local account cache | showPlayerControlLabels | Playback |
| `show_size_badges` | Profile cloud + local profile cache | showSizeBadges | Playback |
| `show_streams_list` | Profile cloud + local profile cache | showStreamsList | Playback |
| `skip_ending_enabled` | Profile cloud + local profile cache | skipEndingEnabled | Playback |
| `skip_intro_enabled` | Profile cloud + local profile cache | skipIntroEnabled | Playback |
| `skip_recap_enabled` | Profile cloud + local profile cache | skipRecapEnabled | Playback |
| `skip_segments_enabled` | Profile cloud + local profile cache | skipIntroEnabled | Playback |
| `streamdek_stream_formatting` | Profile cloud + local profile cache | streamDekFormattingEnabled | Playback |
| `subtitle_background_color` | Profile cloud + local profile cache | subtitleBackgroundColor | Playback |
| `subtitle_bold` | Profile cloud + local profile cache | subtitleBold | Playback |
| `subtitle_default_source` | Profile cloud + local profile cache | subtitleDefaultSource | Playback |
| `subtitle_outline` | Profile cloud + local profile cache | subtitleOutline | Playback |
| `subtitle_outline_color` | Profile cloud + local profile cache | subtitleOutlineColor | Playback |
| `subtitle_text_color` | Profile cloud + local profile cache | subtitleTextColor | Playback |
| `subtitle_text_size` | Profile cloud + local profile cache | subtitleTextSize | Playback |
| `subtitle_vertical_offset` | Profile cloud + local profile cache | subtitleVerticalOffset | Playback |
| `swipe_to_seek_enabled` | Account cloud + local account cache | swipeToSeekEnabled | Playback |
| `sync_on_cellular` | Account cloud + local account cache | syncOnCellular | General |
| `theme_preset` | Account cloud + local account cache | themePreset | Appearance |
| `timing_provider` | Profile cloud + local profile cache | timingProvider | Playback |
| `timing_provider_fallback_enabled` | Profile cloud + local profile cache | timingProviderFallbackEnabled | Playback |
| `torrent_cache_size_gb` | Local device | — | Playback |
| `torrent_enabled` | Local device | — | Playback |
| `torrent_port` | Local device | — | Playback |
| `torrent_profile` | Local device | — | Playback |
| `torrent_run_foreground` | Local device | — | Playback |
| `torrent_streaming_mode` | Local device | — | Playback |
| `trailer_cache_clear_hours` | Profile cloud + local profile cache | trailerCacheClearHours | Appearance |
| `tunneled_playback` | Local device | — | Playback |
| `use_forced_subtitles` | Profile cloud + local profile cache | useForcedSubtitles | Playback |
| `visual_effects` | Account cloud + local account cache | visualEffects | Appearance |
| `vivid_ambient` | Profile cloud + local profile cache | vividAmbient | Appearance |

## Additional stores and session state

See [the lifecycle audit](settings-persistence-audit.md) for DNS/codec stores, audio delay, notifications, display-name overrides, subtitle sources, plugins, service credentials, and resource APIs. The table enumerates the central registry; it does not classify a third-party extension's arbitrary private keys. Temporary playback choices are session-only unless their control explicitly saves a default.
