package net.streamdek.mobile.nativeapp

import org.json.JSONArray
import org.json.JSONObject

/** Sparse documents: null is absent; false, zero and empty arrays remain explicit choices. */
internal fun cloudPreferencesPayload(preferences: CloudPlaybackPreferences): JSONObject {
  val app = JSONObject()
    .put("appAppearance", preferences.appAppearance)
    .put("themePreset", preferences.themePreset)
    .put("headerStyle", preferences.headerStyle)
    .put("showNavLabels", preferences.showNavLabels)
    .put("collapsibleNavigationEnabled", preferences.collapsibleNavigationEnabled)
    .put("navigationAutoCollapseSeconds", preferences.navigationAutoCollapseSeconds)
    .put("syncOverCellular", preferences.syncOnCellular)
  val home = JSONObject()
    .put("detailPageStyle", preferences.detailPageStyle)
    .put("continueWatchingStyle", preferences.continueWatchingStyle)
    .put("homeCardTextMode", preferences.homeCardTextMode)
    .put("networkCardStyle", preferences.networkCardStyle)
    .put("liveLandscapeCards", preferences.liveLandscapeCards)
    .put("liveFavouriteDrawerCards", preferences.liveFavouriteDrawerCards)
    .put("liveCategoriesEnabled", preferences.liveCategoriesEnabled)
    .put("primarySyncService", preferences.primarySyncService)
    .put("showHeroSynopsis", preferences.showHeroSynopsis)
    .put("vividAmbient", preferences.vividAmbient)
    .put("homeBackgroundMode", preferences.homeBackgroundMode)
    .put("ambientTintPercent", preferences.ambientTintPercent)
    .put("defaultAppCatalogsEnabled", preferences.defaultAppCatalogsEnabled)
    .put("showNewEpisodesRow", preferences.showNewEpisodesRow)
    .put("newEpisodesLandscape", preferences.newEpisodesLandscape)
    .put("homeCatalogRows", preferences.homeCatalogRowsJson?.let(::JSONArray))
    // Beside the rows they arrange, so one profile document carries the whole Home layout: the
    // rows, the order of the sources they came from, and which of the two the viewer reads.
    .put("homeRowMode", preferences.homeRowMode)
    .put("homeRowSourceOrder", preferences.homeRowSourceOrder?.let(::JSONArray))
  val detail = JSONObject()
    .put("seasonTabStyle", preferences.seasonTabStyle)
    .put("episodeLayout", preferences.episodeLayout)
    // Trailer playback choices are this device's own — see [PLATFORM_PREFERENCES_KEY]. They are
    // deliberately no longer written to the shared section: a television's idea of when a
    // trailer should start has nothing to do with a phone's, and while both wrote here the last
    // client to save quietly changed the other one's settings.
    .put("trailerCacheClearHours", preferences.trailerCacheClearHours)
    .put("detailBackgroundMode", preferences.detailBackgroundMode)
    // Beside the mode it belongs to. The home screen's copy of this stays under `home`; the
    // two pages carry their own strength now, so one of them had to move out of that section.
    .put("ambientTintPercent", preferences.detailAmbientTintPercent)
    .put("ratingsEnabled", preferences.ratingsEnabled)
    .put("externalRatingsEnabled", preferences.externalRatingsEnabled)
    .put("enabledRatingProviders", preferences.enabledRatingProviders?.let(::JSONArray))
    // The MDBList key is deliberately not written here any more. It was a secret riding on
    // an ordinary settings document; it lives in the encrypted credential store now. The key
    // is omitted rather than sent as an empty string, because a blank would overwrite a
    // legacy value the backend has not migrated out of this document yet -- and that
    // migration is the only thing that knows how to keep it.
    // Also written under `streams` below: the setting is presented on Title Pages now, but
    // clients that have not moved yet still read the older location.
    .put("blurUnwatchedEpisodes", preferences.blurUnwatchedEpisodes)
  val playback = JSONObject()
    .put("pictureInPictureEnabled", preferences.pictureInPictureEnabled)
    .put("decoderMode", preferences.decoderMode)
    .put("renderSurface", preferences.renderSurface)
    .put("playerEngine", preferences.playerEngine)
    .put("preferredAudioLanguage", preferences.preferredAudioLanguage)
    .put("preferredQuality", preferences.preferredQuality)
    .put("maxFileSizeGB", preferences.maxFileSizeGb)
    .put("skipSegmentsEnabled", if (listOf(preferences.skipIntroEnabled, preferences.skipRecapEnabled, preferences.skipEndingEnabled).all { it == null }) null else (preferences.skipIntroEnabled == true || preferences.skipRecapEnabled == true || preferences.skipEndingEnabled == true))
    .put("skipIntroEnabled", preferences.skipIntroEnabled)
    .put("skipRecapEnabled", preferences.skipRecapEnabled)
    .put("skipEndingEnabled", preferences.skipEndingEnabled)
    .put("autoSkipIntroEnabled", preferences.autoSkipIntroEnabled)
    .put("autoSkipRecapEnabled", preferences.autoSkipRecapEnabled)
    .put("autoSkipEndingEnabled", preferences.autoSkipEndingEnabled)
    .put("introdbApiKey", preferences.introdbApiKey)
    .put("autoPlayNextEpisodeEnabled", preferences.autoPlayNextEpisode)
    .put("autoplayNextEpisode", preferences.autoPlayNextEpisode)
    .put("preferBingeGroupNextEpisode", preferences.preferBingeGroup)
    .put("autoLoadSubtitles", preferences.autoLoadSubtitles)
    .put("secondaryAudioLanguage", preferences.secondaryAudioLanguage)
    .put("preferredSubtitleLanguage", preferences.preferredSubtitleLanguage)
    .put("secondarySubtitleLanguage", preferences.secondarySubtitleLanguage)
    .put("useForcedSubtitles", preferences.useForcedSubtitles)
    .put("showOnlyPreferredSubtitleLanguages", preferences.showOnlyPreferredSubtitleLanguages)
    .put("addonSubtitleLoading", preferences.addonSubtitleLoading)
    .put("subtitleDefaultSource", preferences.subtitleDefaultSource)
    .put("liveProgressBarEnabled", preferences.liveProgressBarEnabled)
    .put("liveBadgeEnabled", preferences.liveBadgeEnabled)
    .put("subtitleTextSize", preferences.subtitleTextSize)
    .put("subtitleVerticalOffset", preferences.subtitleVerticalOffset)
    .put("subtitleBold", preferences.subtitleBold)
    .put("subtitleTextColor", preferences.subtitleTextColor)
    .put("subtitleBackgroundColor", preferences.subtitleBackgroundColor)
    .put("subtitleOutline", preferences.subtitleOutline)
    .put("subtitleOutlineColor", preferences.subtitleOutlineColor)
    .put("nextEpisodeThresholdMode", preferences.nextEpisodeThresholdMode)
    .put("nextEpisodeThresholdPercent", preferences.nextEpisodeThresholdPercent)
    .put("nextEpisodeThresholdMinutes", preferences.nextEpisodeThresholdMinutes)
    .put("endOfPlaybackRecommendationsEnabled", preferences.endOfPlaybackRecommendationsEnabled)
    .put("recommendationTiming", preferences.recommendationTiming)
    .put("recommendationItemCount", preferences.recommendationItemCount)
    .put("timingProvider", preferences.timingProvider)
    .put("timingProviderFallbackEnabled", preferences.timingProviderFallbackEnabled)
  val streams = JSONObject()
    .put("showStreamsList", preferences.showStreamsList)
    .put("rememberLastSource", preferences.rememberLastSource)
    .put("favoriteSourceKeys", preferences.favoriteSourceKeys?.let(::JSONArray))
    .put("blurUnwatchedEpisodes", preferences.blurUnwatchedEpisodes)
    .put("fusionBadgesEnabled", preferences.fusionBadgesEnabled)
    .put("streamDekFormattingEnabled", preferences.streamDekFormattingEnabled)
    .put("showSizeBadges", preferences.showSizeBadges)
    .put("badgePosition", preferences.badgePosition)
    .put("fusionBadgeUrls", preferences.fusionBadgeUrls?.let(::JSONArray))
    .put("activeFusionBadgeUrl", preferences.activeFusionBadgeUrl)
  val updates = JSONObject().put("autoUpdateChecksEnabled", preferences.autoUpdateChecksEnabled)
  // This client's own settings, kept apart from the shared ones. The backend defines the
  // convention (settingsSchema.ts: perPlatformSettingKeys / PER_PLATFORM_PREFERENCES_KEY) so a
  // phone, a television and the portal all agree on where to look.
  val platformPreferences = JSONObject()
    .put(
      PLATFORM_PREFERENCES_PLATFORM,
      JSONObject()
        .put("heroTrailerAutoplay", preferences.heroTrailerAutoplay)
        .put("heroTrailerResolution", preferences.heroTrailerResolution)
        .put("heroTrailerDelaySeconds", preferences.heroTrailerDelaySeconds)
        .put("heroTrailerMuted", preferences.heroTrailerMuted)
        // Once device-local, now carried here so the portal can set them. Still this kind of
        // device's own: the television keeps separate values under its own key.
        .put("animationSpeed", preferences.animationSpeed)
        .put("appLanguage", preferences.appLanguage)
        .put("visualEffects", preferences.visualEffects)
        .put("navigationBehaviour", preferences.navigationBehaviour)
        .put("homeDensity", preferences.homeDensity)
        .put("mediaHubEnabled", preferences.mediaHubEnabled)
        .put("playerControlLayout", preferences.playerControlLayout)
        .put("showPlayerControlLabels", preferences.showPlayerControlLabels)
        .put("playerTitleDisplay", preferences.playerTitleDisplay)
        .put("fullscreenStatusBar", preferences.fullscreenStatusBar)
        .put("holdToSpeedEnabled", preferences.holdToSpeedEnabled)
        .put("holdToSpeedMultiplier", preferences.holdToSpeedMultiplier?.toDouble())
        .put("swipeToSeekEnabled", preferences.swipeToSeekEnabled)
        .put("doubleTapSeekEnabled", preferences.doubleTapSeekEnabled)
        .put("doubleTapSeekSeconds", preferences.doubleTapSeekSeconds)
        .put("doubleTapPlayPauseEnabled", preferences.doubleTapPlayPauseEnabled)
        .put("playerLevelGesturesEnabled", preferences.playerLevelGesturesEnabled),
    )
  val payload = JSONObject()
    .put("app", app)
    .put("home", home)
    .put("detail", detail)
    .put("playback", playback)
    .put("streams", streams)
    .put("updates", updates)
    .put(PLATFORM_PREFERENCES_KEY, platformPreferences)
  return payload
}

internal fun cloudProfilePreferencesPayload(preferences: CloudPlaybackPreferences, payload: JSONObject = cloudPreferencesPayload(preferences)): JSONObject {
  val profileDetail = JSONObject((payload.optJSONObject("detail") ?: JSONObject()).toString()).apply { remove("mdblistApiKey") }
  // The IntroDB key stays out of this payload for the same reason the MDBList one is removed
  // above: both are account-level in the cloud and profile-scoped on the device.
  val profilePlayback = JSONObject()
    .put("preferredAudioLanguage", preferences.preferredAudioLanguage)
    .put("secondaryAudioLanguage", preferences.secondaryAudioLanguage)
    .put("preferredSubtitleLanguage", preferences.preferredSubtitleLanguage)
    .put("useForcedSubtitles", preferences.useForcedSubtitles)
    .put("subtitleDefaultSource", preferences.subtitleDefaultSource)
    .put("preferredQuality", preferences.preferredQuality)
    .put("maxFileSizeGB", preferences.maxFileSizeGb)
    .put("skipSegmentsEnabled", if (listOf(preferences.skipIntroEnabled, preferences.skipRecapEnabled, preferences.skipEndingEnabled).all { it == null }) null else (preferences.skipIntroEnabled == true || preferences.skipRecapEnabled == true || preferences.skipEndingEnabled == true))
    .put("skipIntroEnabled", preferences.skipIntroEnabled)
    .put("skipRecapEnabled", preferences.skipRecapEnabled)
    .put("skipEndingEnabled", preferences.skipEndingEnabled)
    .put("autoSkipIntroEnabled", preferences.autoSkipIntroEnabled)
    .put("autoSkipRecapEnabled", preferences.autoSkipRecapEnabled)
    .put("autoSkipEndingEnabled", preferences.autoSkipEndingEnabled)
    .put("autoPlayNextEpisodeEnabled", preferences.autoPlayNextEpisode)
    .put("autoplayNextEpisode", preferences.autoPlayNextEpisode)
    .put("preferBingeGroupNextEpisode", preferences.preferBingeGroup)
    .put("autoLoadSubtitles", preferences.autoLoadSubtitles)
    // The same profile-scoped set the television writes (PreferenceScopes.kt there). A key
    // one client scopes to the profile and the other writes only to the account ends up
    // shadowed: the profile copy wins on read, so the account-only write never shows.
    .put("showOnlyPreferredSubtitleLanguages", preferences.showOnlyPreferredSubtitleLanguages)
    .put("secondarySubtitleLanguage", preferences.secondarySubtitleLanguage)
    .put("addonSubtitleLoading", preferences.addonSubtitleLoading)
    .put("liveProgressBarEnabled", preferences.liveProgressBarEnabled)
    .put("liveBadgeEnabled", preferences.liveBadgeEnabled)
    .put("subtitleTextSize", preferences.subtitleTextSize)
    .put("subtitleVerticalOffset", preferences.subtitleVerticalOffset)
    .put("subtitleBold", preferences.subtitleBold)
    .put("subtitleTextColor", preferences.subtitleTextColor)
    .put("subtitleBackgroundColor", preferences.subtitleBackgroundColor)
    .put("subtitleOutline", preferences.subtitleOutline)
    .put("subtitleOutlineColor", preferences.subtitleOutlineColor)
    .put("nextEpisodeThresholdMode", preferences.nextEpisodeThresholdMode)
    .put("nextEpisodeThresholdPercent", preferences.nextEpisodeThresholdPercent)
    .put("nextEpisodeThresholdMinutes", preferences.nextEpisodeThresholdMinutes)
    .put("endOfPlaybackRecommendationsEnabled", preferences.endOfPlaybackRecommendationsEnabled)
    .put("recommendationTiming", preferences.recommendationTiming)
    .put("recommendationItemCount", preferences.recommendationItemCount)
    .put("timingProvider", preferences.timingProvider)
    .put("timingProviderFallbackEnabled", preferences.timingProviderFallbackEnabled)
  val profilePayload = JSONObject()
    .put("home", payload.optJSONObject("home"))
    .put("detail", profileDetail)
    .put("playback", profilePlayback)
    .put("streams", payload.optJSONObject("streams"))
  return profilePayload
}

/** The legacy profile endpoint replaces sections. Preserve keys owned by other clients. */
internal fun mergeSettingsPatch(current: JSONObject, patch: JSONObject): JSONObject {
  val result = JSONObject(current.toString())
  patch.keys().forEach { key ->
    val value = patch.opt(key)
    result.put(key, if (value is JSONObject) mergeSettingsPatch(result.optJSONObject(key) ?: JSONObject(), value) else value)
  }
  return result
}

internal fun removeEmptySettingsSections(document: JSONObject): JSONObject {
  document.keys().asSequence().toList().forEach { key ->
    val child = document.optJSONObject(key) ?: return@forEach
    removeEmptySettingsSections(child)
    if (child.length() == 0) document.remove(key)
  }
  return document
}

internal fun parseCloudSettings(accountPreferences: JSONObject, profilePreferences: JSONObject = JSONObject()): CloudPlaybackPreferences {
  fun mergedSection(name: String): JSONObject {
    val merged = JSONObject((accountPreferences.optJSONObject(name) ?: JSONObject()).toString())
    val scoped = profilePreferences.optJSONObject(name) ?: JSONObject()
    val keys = scoped.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      merged.put(key, scoped.opt(key))
    }
    return merged
  }
  val app = accountPreferences.optJSONObject("app") ?: JSONObject()
  val home = mergedSection("home")
  val detail = mergedSection("detail")
  val playback = mergedSection("playback")
  // These are account-owned. A legacy profile copy must not shadow later account writes.
  val accountPlayback = accountPreferences.optJSONObject("playback") ?: JSONObject()
  val streams = mergedSection("streams")
  val updates = accountPreferences.optJSONObject("updates") ?: JSONObject()
  // Settings this client holds for itself. Anything missing here falls back to the shared
  // section, so an account configured before trailer settings were split keeps what it had.
  val platform = accountPreferences.optJSONObject(PLATFORM_PREFERENCES_KEY)
    ?.optJSONObject(PLATFORM_PREFERENCES_PLATFORM)
    ?: JSONObject()
  fun optionalBoolean(source: JSONObject, key: String): Boolean? = when (val value = source.opt(key)) {
    is Boolean -> value
    is String -> value.toBooleanStrictOrNull()
    else -> null
  }
  fun optionalInt(source: JSONObject, key: String): Int? = when (val value = source.opt(key)) {
    is Number -> value.toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
    is String -> value.toIntOrNull()
    else -> null
  }
  fun optionalString(source: JSONObject, key: String): String? = if (source.has(key) && !source.isNull(key)) source.optString(key).takeIf(String::isNotBlank) else null
  fun optionalStringAllowEmpty(source: JSONObject, key: String): String? = if (source.has(key) && !source.isNull(key)) source.optString(key) else null
  fun optionalStringList(source: JSONObject, key: String): List<String>? {
    if (!source.has(key) || source.isNull(key)) return null
    val values = source.optJSONArray(key) ?: return null
    return List(values.length()) { index -> values.optString(index) }.filter(String::isNotBlank)
  }
  return CloudPlaybackPreferences(
    appAppearance = optionalString(app, "appAppearance"),
    themePreset = optionalString(app, "themePreset"),
    headerStyle = optionalString(app, "headerStyle"),
    showNavLabels = optionalBoolean(app, "showNavLabels"),
    collapsibleNavigationEnabled = optionalBoolean(app, "collapsibleNavigationEnabled"),
    navigationAutoCollapseSeconds = optionalInt(app, "navigationAutoCollapseSeconds"),
    syncOnCellular = optionalBoolean(app, "syncOverCellular"),
    detailPageStyle = optionalString(home, "detailPageStyle"),
    continueWatchingStyle = optionalString(home, "continueWatchingStyle"),
    homeCardTextMode = optionalString(home, "homeCardTextMode"),
    networkCardStyle = optionalString(home, "networkCardStyle"),
    liveLandscapeCards = optionalBoolean(home, "liveLandscapeCards"),
    liveFavouriteDrawerCards = optionalBoolean(home, "liveFavouriteDrawerCards"),
    liveCategoriesEnabled = optionalBoolean(home, "liveCategoriesEnabled"),
    primarySyncService = optionalString(home, "primarySyncService"),
    showHeroSynopsis = optionalBoolean(home, "showHeroSynopsis"),
    vividAmbient = optionalBoolean(home, "vividAmbient"),
    ambientTintPercent = optionalInt(home, "ambientTintPercent"),
    defaultAppCatalogsEnabled = optionalBoolean(home, "defaultAppCatalogsEnabled"),
    homeCatalogRowsJson = home.optJSONArray("homeCatalogRows")?.toString(),
    homeRowMode = optionalString(home, "homeRowMode"),
    homeRowSourceOrder = optionalStringList(home, "homeRowSourceOrder"),
    seasonTabStyle = optionalString(detail, "seasonTabStyle"),
    episodeLayout = optionalString(detail, "episodeLayout"),
    heroTrailerAutoplay = optionalBoolean(platform, "heroTrailerAutoplay") ?: optionalBoolean(detail, "heroTrailerAutoplay"),
    trailerCacheClearHours = optionalInt(detail, "trailerCacheClearHours"),
    detailBackgroundMode = optionalString(detail, "detailBackgroundMode"),
    // Falls back to the home value, which is where the single shared setting used to live, so
    // a profile written by an older client arrives with both pages on the strength it chose.
    detailAmbientTintPercent = optionalInt(detail, "ambientTintPercent") ?: optionalInt(home, "ambientTintPercent"),
    homeBackgroundMode = optionalString(home, "homeBackgroundMode"),
    secondaryAudioLanguage = optionalString(playback, "secondaryAudioLanguage"),
    preferredSubtitleLanguage = optionalString(playback, "preferredSubtitleLanguage"),
    secondarySubtitleLanguage = optionalString(playback, "secondarySubtitleLanguage"),
    useForcedSubtitles = optionalBoolean(playback, "useForcedSubtitles"),
    showOnlyPreferredSubtitleLanguages = optionalBoolean(playback, "showOnlyPreferredSubtitleLanguages"),
    addonSubtitleLoading = optionalString(playback, "addonSubtitleLoading"),
    subtitleDefaultSource = optionalString(playback, "subtitleDefaultSource"),
    liveProgressBarEnabled = optionalBoolean(playback, "liveProgressBarEnabled"),
    liveBadgeEnabled = optionalBoolean(playback, "liveBadgeEnabled"),
    subtitleTextSize = optionalInt(playback, "subtitleTextSize"),
    subtitleVerticalOffset = optionalInt(playback, "subtitleVerticalOffset"),
    subtitleBold = optionalBoolean(playback, "subtitleBold"),
    subtitleTextColor = optionalString(playback, "subtitleTextColor"),
    subtitleBackgroundColor = optionalString(playback, "subtitleBackgroundColor"),
    subtitleOutline = optionalBoolean(playback, "subtitleOutline"),
    subtitleOutlineColor = optionalString(playback, "subtitleOutlineColor"),
    showNewEpisodesRow = optionalBoolean(home, "showNewEpisodesRow"),
    newEpisodesLandscape = optionalBoolean(home, "newEpisodesLandscape"),
    // Read from this device type's own section only. Unlike the trailer settings there is no
    // shared value to fall back to: these were never anywhere but the phone.
    animationSpeed = optionalString(platform, "animationSpeed"),
    appLanguage = optionalString(platform, "appLanguage"),
    visualEffects = optionalString(platform, "visualEffects"),
    navigationBehaviour = optionalString(platform, "navigationBehaviour"),
    homeDensity = optionalString(platform, "homeDensity"),
    mediaHubEnabled = optionalBoolean(platform, "mediaHubEnabled"),
    heroTrailerMuted = optionalBoolean(platform, "heroTrailerMuted"),
    playerControlLayout = optionalString(platform, "playerControlLayout"),
    showPlayerControlLabels = optionalBoolean(platform, "showPlayerControlLabels"),
    playerTitleDisplay = optionalString(platform, "playerTitleDisplay"),
    fullscreenStatusBar = optionalString(platform, "fullscreenStatusBar"),
    holdToSpeedEnabled = optionalBoolean(platform, "holdToSpeedEnabled"),
    holdToSpeedMultiplier = if (platform.has("holdToSpeedMultiplier") && !platform.isNull("holdToSpeedMultiplier")) platform.optDouble("holdToSpeedMultiplier").takeIf { !it.isNaN() }?.toFloat() else null,
    swipeToSeekEnabled = optionalBoolean(platform, "swipeToSeekEnabled"),
    doubleTapSeekEnabled = optionalBoolean(platform, "doubleTapSeekEnabled"),
    doubleTapSeekSeconds = optionalInt(platform, "doubleTapSeekSeconds"),
    doubleTapPlayPauseEnabled = optionalBoolean(platform, "doubleTapPlayPauseEnabled"),
    playerLevelGesturesEnabled = optionalBoolean(platform, "playerLevelGesturesEnabled"),
    heroTrailerResolution = optionalInt(platform, "heroTrailerResolution") ?: optionalInt(detail, "heroTrailerResolution"),
    heroTrailerDelaySeconds = optionalInt(platform, "heroTrailerDelaySeconds") ?: optionalInt(detail, "heroTrailerDelaySeconds"),
    ratingsEnabled = optionalBoolean(detail, "ratingsEnabled"),
    externalRatingsEnabled = optionalBoolean(detail, "externalRatingsEnabled"),
    enabledRatingProviders = optionalStringList(detail, "enabledRatingProviders"),
    mdblistApiKey = optionalStringAllowEmpty(detail, "mdblistApiKey"),
    pictureInPictureEnabled = optionalBoolean(accountPlayback, "pictureInPictureEnabled"),
    decoderMode = optionalString(accountPlayback, "decoderMode"),
    renderSurface = optionalString(accountPlayback, "renderSurface"),
    playerEngine = optionalString(accountPlayback, "playerEngine"),
    preferredAudioLanguage = optionalString(playback, "preferredAudioLanguage"),
    introdbApiKey = optionalStringAllowEmpty(playback, "introdbApiKey"),
    skipIntroEnabled = optionalBoolean(playback, "skipIntroEnabled"),
    skipRecapEnabled = optionalBoolean(playback, "skipRecapEnabled"),
    skipEndingEnabled = optionalBoolean(playback, "skipEndingEnabled"),
    autoSkipIntroEnabled = optionalBoolean(playback, "autoSkipIntroEnabled"),
    autoSkipRecapEnabled = optionalBoolean(playback, "autoSkipRecapEnabled"),
    autoSkipEndingEnabled = optionalBoolean(playback, "autoSkipEndingEnabled"),
    autoPlayNextEpisode = optionalBoolean(playback, "autoPlayNextEpisodeEnabled") ?: optionalBoolean(playback, "autoplayNextEpisode"),
    preferBingeGroup = optionalBoolean(playback, "preferBingeGroupNextEpisode"),
    autoLoadSubtitles = optionalBoolean(playback, "autoLoadSubtitles"),
    nextEpisodeThresholdMode = optionalString(playback, "nextEpisodeThresholdMode"),
    nextEpisodeThresholdPercent = optionalInt(playback, "nextEpisodeThresholdPercent"),
    nextEpisodeThresholdMinutes = optionalInt(playback, "nextEpisodeThresholdMinutes"),
    endOfPlaybackRecommendationsEnabled = optionalBoolean(playback, "endOfPlaybackRecommendationsEnabled"),
    recommendationTiming = optionalString(playback, "recommendationTiming"),
    recommendationItemCount = optionalInt(playback, "recommendationItemCount"),
    timingProvider = optionalString(playback, "timingProvider"),
    timingProviderFallbackEnabled = optionalBoolean(playback, "timingProviderFallbackEnabled"),
    showStreamsList = optionalBoolean(streams, "showStreamsList"),
    rememberLastSource = optionalBoolean(streams, "rememberLastSource"),
    favoriteSourceKeys = optionalStringList(streams, "favoriteSourceKeys"),
    blurUnwatchedEpisodes = optionalBoolean(detail, "blurUnwatchedEpisodes") ?: optionalBoolean(streams, "blurUnwatchedEpisodes"),
    fusionBadgesEnabled = optionalBoolean(streams, "fusionBadgesEnabled"),
    streamDekFormattingEnabled = optionalBoolean(streams, "streamDekFormattingEnabled"),
    showSizeBadges = optionalBoolean(streams, "showSizeBadges"),
    preferredQuality = optionalString(playback, "preferredQuality") ?: optionalString(streams, "preferredQuality"),
    maxFileSizeGb = optionalInt(playback, "maxFileSizeGB") ?: optionalInt(streams, "maxFileSizeGB"),
    badgePosition = optionalString(streams, "badgePosition"),
    fusionBadgeUrls = optionalStringList(streams, "fusionBadgeUrls"),
    activeFusionBadgeUrl = optionalStringAllowEmpty(streams, "activeFusionBadgeUrl"),
    autoUpdateChecksEnabled = optionalBoolean(updates, "autoUpdateChecksEnabled"),
  )
}
