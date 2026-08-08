package net.streamdek.mobile.nativeapp

data class SessionUser(
  val uid: String,
  val email: String?,
  val displayName: String?,
  val subscriptionStatus: String,
  val accessToken: String,
)

data class AuthSession(
  val token: String,
  val user: SessionUser,
)

data class LinkedTvDevice(
  val id: String,
  val name: String,
  val platform: String?,
  val deviceType: String?,
  val lastSeenAt: String?,
  val isCurrent: Boolean,
  val handoffPublicKey: String? = null,
)

data class PlaybackHandoffReceipt(
  val id: String,
  val expiresAt: String?,
)
data class MediaItem(
  val id: String,
  val type: String,
  val title: String,
  val year: String?,
  val poster: String?,
  val backdrop: String?,
  val rating: Double?,
  val description: String,
  val progress: Double? = null,
  val genres: List<String> = emptyList(),
  val titleLogo: String? = null,
  val addedAt: Long? = null,
  val updatedAt: Long? = null,
  val sourceAddonId: String? = null,
  val sourceAddonName: String? = null,
  val sourceCatalogType: String? = null,
  val sourceCatalogId: String? = null,
  val sourceCatalogName: String? = null,
  val sourceCatalogGenre: String? = null,
  val directStreamUrl: String? = null,
  val requestHeaders: Map<String, String> = emptyMap(),
  // ClearKey DRM, as published by IPTV playlists via #KODIPROP:inputstream.adaptive.license_type
  // / license_key lines. drmClearKeys maps hex key-id -> hex key. Only "clearkey" is understood
  // downstream (Media3); other license types are carried through but not decryptable.
  val drmLicenseType: String? = null,
  val drmClearKeys: Map<String, String> = emptyMap(),
  val resumeSeasonNumber: Int? = null,
  val resumeEpisodeNumber: Int? = null,
)

/**
 * Connection state for one tracking service on the active profile. Trakt keeps its own richer
 * [TraktStatus] because it also drives Home rows; SIMKL and MDBList only need connect/identify.
 */
data class SyncServiceStatus(
  val connected: Boolean = false,
  val username: String? = null,
  /** False when the backend has no credentials configured for the service, so connecting cannot work. */
  val available: Boolean = true,
  /** Set once a status call has actually reached the backend. */
  val checked: Boolean = false,
  /**
   * What the service's API can actually serve, as reported by the backend. MDBList keeps a
   * watchlist but has no playback API, so making it primary must not leave Continue Watching
   * silently empty with no explanation. Defaults assume full support so a backend that predates
   * the field behaves exactly as before.
   */
  val supportsWatchlist: Boolean = true,
  val supportsPlayback: Boolean = true,
)

data class SeasonSummary(
  val seasonNumber: Int,
  val name: String,
  val episodeCount: Int,
  val poster: String? = null,
)

data class EpisodeItem(
  val id: String,
  val episodeNumber: Int,
  val seasonNumber: Int,
  val name: String,
  val overview: String,
  val still: String?,
  val runtime: Int?,
  val airDate: String? = null,
  // Some Stremio bridges use an opaque per-episode id instead of the conventional
  // parentId:season:episode form. Keep that transport id separate from the display/TMDB id.
  val sourceStreamId: String? = null,
)

data class LocalAddonMeta(
  val id: String,
  val imdbId: String?,
  val type: String,
  val title: String,
  val year: String?,
  val releaseDate: String?,
  val description: String,
  val poster: String?,
  val backdrop: String?,
  val genres: List<String>,
  val runtimeMinutes: Int?,
  val cast: List<CastMember>,
  val episodes: List<EpisodeItem>,
)

data class CastMember(
  val id: String,
  val name: String,
  val character: String?,
  val photo: String?,
)

data class PersonDetail(
  val id: String,
  val name: String,
  val photo: String?,
  val biography: String?,
  val birthday: String?,
  val placeOfBirth: String?,
  val knownFor: String?,
  val popularWorks: List<MediaItem> = emptyList(),
)

data class WatchProvider(
  val id: String,
  val name: String,
  val logo: String?,
  val url: String? = null,
)

data class ExternalRating(
  val provider: String,
  val rating: Double?,
  val label: String? = null,
)

data class TraktComment(
  val id: String,
  val author: String,
  val rating: Int?,
  val body: String,
  val likes: Int,
)

data class SearchPage(
  val items: List<MediaItem>,
  val page: Int,
  val totalPages: Int,
)

data class MediaDetail(
  val id: String,
  val type: String,
  val title: String,
  val titleLogo: String?,
  val tagline: String?,
  val year: String?,
  val releaseDate: String?,
  val description: String,
  val poster: String?,
  val backdrop: String?,
  val trailerUrl: String?,
  val trailerSite: String? = null,
  val trailerKeys: List<String> = emptyList(),
  val rating: Double?,
  val imdbRating: Double?,
  val tmdbRating: Double?,
  val externalRatings: List<ExternalRating> = emptyList(),
  val genres: List<String>,
  val runtimeMinutes: Int?,
  val seasonsCount: Int?,
  val imdbId: String?,
  val seasons: List<SeasonSummary>,
  val cast: List<CastMember> = emptyList(),
  val similarTitles: List<MediaItem> = emptyList(),
  val availableOn: List<WatchProvider> = emptyList(),
  val traktComments: List<TraktComment> = emptyList(),
)

data class MediaSection(
  val id: String,
  val title: String,
  val items: List<MediaItem>,
)

data class StreamProfile(
  val id: String,
  val userId: String,
  val name: String,
  val avatarIndex: Int,
  val hasPinSet: Boolean,
  val isDefault: Boolean,
  val subtitleLanguage: String?,
  val audioLanguage: String?,
)

data class CloudPlaybackPreferences(
  val appAppearance: String? = null,
  val themePreset: String? = null,
  val headerStyle: String? = null,
  val showNavLabels: Boolean? = null,
  val collapsibleNavigationEnabled: Boolean? = null,
  val navigationAutoCollapseSeconds: Int? = null,
  val syncOnCellular: Boolean? = null,
  val detailPageStyle: String? = null,
  val continueWatchingStyle: String? = null,
  val liveLandscapeCards: Boolean? = null,
  val liveFavouriteDrawerCards: Boolean? = null,
  val liveCategoriesEnabled: Boolean? = null,
  val primarySyncService: String? = null,
  val showHeroSynopsis: Boolean? = null,
  val vividAmbient: Boolean? = null,
  val ambientTintPercent: Int? = null,
  val defaultAppCatalogsEnabled: Boolean? = null,
  val homeCatalogRowsJson: String? = null,
  val seasonTabStyle: String? = null,
  val heroTrailerAutoplay: Boolean? = null,
  val heroTrailerResolution: Int? = null,
  val ratingsEnabled: Boolean? = null,
  val externalRatingsEnabled: Boolean? = null,
  val enabledRatingProviders: List<String>? = null,
  val mdblistApiKey: String? = null,
  val pictureInPictureEnabled: Boolean? = null,
  val decoderMode: String? = null,
  val renderSurface: String? = null,
  val playerEngine: String? = null,
  val preferredAudioLanguage: String? = null,
  val skipIntroEnabled: Boolean? = null,
  val skipRecapEnabled: Boolean? = null,
  val skipEndingEnabled: Boolean? = null,
  val autoPlayNextEpisode: Boolean? = null,
  val preferBingeGroup: Boolean? = null,
  val autoLoadSubtitles: Boolean? = null,
  val nextEpisodeThresholdMode: String? = null,
  val nextEpisodeThresholdPercent: Int? = null,
  val nextEpisodeThresholdMinutes: Int? = null,
  val showStreamsList: Boolean? = null,
  val rememberLastSource: Boolean? = null,
  val blurUnwatchedEpisodes: Boolean? = null,
  val fusionBadgesEnabled: Boolean? = null,
  val streamDekFormattingEnabled: Boolean? = null,
  val showSizeBadges: Boolean? = null,
  val showAddonTmdbRatings: Boolean? = null,
  val preferredQuality: String? = null,
  val maxFileSizeGb: Int? = null,
  val badgePosition: String? = null,
  val fusionBadgeUrls: List<String>? = null,
  val activeFusionBadgeUrl: String? = null,
  val autoUpdateChecksEnabled: Boolean? = null,
)

data class UpdateManifest(
  val versionCode: Int,
  val versionName: String,
  val apkUrl: String,
  val releaseNotes: String,
  val required: Boolean,
  val minSupportedVersionCode: Int?,
  val requiredReason: String?,
  val packageName: String,
  val assetName: String?,
  val fileSizeBytes: Long?,
  val checksumSha256: String?,
)

data class AddonCatalog(
  val type: String,
  val id: String,
  val name: String,
  // Stremio catalogs can declare a "genre" extra property with a list of options (e.g. a
  // "Netflix" catalog that's really a menu of many named sub-lists like "Top 10 Series
  // Today"). Without picking one, the addon has nothing to key its response on, and some
  // addons fall back to the same generic/default result for every such catalog. When present,
  // the first option is used as the catalog's default preview row.
  val genreOptions: List<String> = emptyList(),
  /** True when the add-on declares genre as a required extra, so the catalog needs one to answer. */
  val requiresGenre: Boolean = false,
)

data class AddonManifest(
  val id: String,
  val name: String,
  val version: String,
  val description: String?,
  val logo: String?,
  val url: String? = null,
  val baseUrl: String? = null,
  val manifestUrl: String? = null,
  val transportUrl: String? = null,
  val resources: List<String> = emptyList(),
  val types: List<String> = emptyList(),
  val catalogs: List<AddonCatalog> = emptyList(),
  val behaviorConfigurable: Boolean = false,
  val configurationRequired: Boolean = false,
)

data class InstalledAddon(
  val id: String,
  val enabled: Boolean,
  val position: Int,
  val url: String? = null,
  val baseUrl: String? = null,
  val manifestUrl: String? = null,
  val transportUrl: String? = null,
  val manifest: AddonManifest,
)

data class AddonStream(
  val addonId: String,
  val addonName: String,
  val name: String?,
  val title: String?,
  val description: String?,
  val url: String?,
  val infoHash: String?,
  val fileIdx: Int?,
  val filename: String?,
  val quality: String?,
  val size: String?,
  val cachedBy: List<String>,
  val bingeGroup: String? = null,
  val requestHeaders: Map<String, String> = emptyMap(),
  val drmLicenseType: String? = null,
  val drmClearKeys: Map<String, String> = emptyMap(),
  val source: String? = null,
)

data class DebridAccount(
  val provider: String,
  val enabled: Boolean,
  val priority: Int,
  val username: String?,
)

data class DebridResolvedStream(
  val provider: String,
  val url: String,
  val filename: String,
  val filesize: Long,
)

data class TraktItem(
  val id: String,
  val tmdbId: Int?,
  val title: String,
  val type: String,
  val year: String?,
  val rating: Double?,
  val poster: String?,
  val backdrop: String?,
  val description: String?,
  val progress: Double?,
  val addedAt: Long? = null,
  val updatedAt: Long? = null,
)

data class DeviceCodeInfo(
  val deviceCode: String,
  val userCode: String,
  val verificationUrl: String,
  val expiresIn: Int,
  val interval: Int,
)

data class TraktStatus(
  val connected: Boolean,
  val username: String?,
)

data class TorrentServerSettings(
  val enabled: Boolean = true,
  val streamingMode: String = "server",
  val profile: String = "default",
  val cacheSizeGb: Int = 5,
  val port: Int = 11100,
  val runAsForegroundService: Boolean = false,
)

data class TorrentServerStatus(
  val isOnline: Boolean = false,
  val isForeground: Boolean = false,
  val requestedForeground: Boolean = false,
  val port: Int = 11100,
  val url: String = "http://127.0.0.1:11100",
  val cacheDirectory: String = "",
  val torrentStoreDirectory: String = "",
  val cacheUsageBytes: Long = 0L,
  val profile: String = "default",
  val cacheSizeGb: Int = 5,
  val recoveryMode: String = "idle",
  val lastStartupError: String = "",
  val foregroundDowngradeReason: String = "",
  val lifecycleState: String = "idle",
)

data class PlayerSession(
  val url: String,
  val title: String,
  val subtitle: String?,
  val sourceLabel: String?,
  val qualityLabel: String? = null,
  val sizeLabel: String? = null,
  val backdrop: String? = null,
  val poster: String? = null,
  val titleLogo: String? = null,
  val synopsis: String? = null,
  val mediaId: String = "",
  val mediaType: String = "movie",
  val year: Int? = null,
  val seasonNumber: Int? = null,
  val episodeNumber: Int? = null,
  val episodeTitle: String? = null,
  val pictureInPictureEnabled: Boolean = false,
  val decoderMode: String = "HW+",
  val renderSurface: String = "Standard",
  val resumePercent: Double = 0.0,
  val currentStream: AddonStream? = null,
  val imdbId: String? = null,
  val subtitleLanguage: String = "en",
  val autoLoadSubtitles: Boolean = true,
  val skipIntroEnabled: Boolean = true,
  val skipRecapEnabled: Boolean = true,
  val skipEndingEnabled: Boolean = true,
  val autoPlayNextEpisode: Boolean = true,
  val preferBingeGroup: Boolean = true,
  val nextEpisodeThresholdMode: String = "minutes",
  val isLive: Boolean = false,
  val nextEpisodeThresholdPercent: Int = 95,
  val nextEpisodeThresholdMinutes: Int = 2,
  val requestHeaders: Map<String, String> = emptyMap(),
  val drmLicenseType: String? = null,
  val drmClearKeys: Map<String, String> = emptyMap(),
  val runtimeMinutes: Int? = null,
  val addonSubtitleSources: List<UserSubtitleSource> = emptyList(),
  val userSubtitleSources: List<UserSubtitleSource> = emptyList(),
  val isProxied: Boolean = false,
  val playerEngine: String = "Auto",
  val preferredAudioLanguage: String = "en",
)


