package net.streamdek.mobile.nativeapp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.graphics.Matrix
import android.view.TextureView
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Shapes
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import net.streamdek.mobile.BuildConfig
import net.streamdek.mobile.R
import net.streamdek.mobile.MainActivity
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.Job
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import net.streamdek.mobile.torrent.TorrentServerConfig
import net.streamdek.mobile.torrent.TorrentServerService


private enum class MainTab { Home, Search, Continue, Watchlist, Settings }
private enum class LibraryTab { Continue, Watchlist, Profiles, Addons, Debrid, Trakt }
private enum class DetailTab { About, Episodes, Streams }
private enum class DetailPageStyle { Classic, Centered }
private enum class SeasonTabStyle { Regular, Posters }
private enum class ContinueWatchingStyle { Cinematic, Glass, Ticket, Mini, Stacked }
private enum class AppAppearance { System, Dark, Light }
private enum class AppThemePreset { Monochrome, Ocean, Emerald, Amber, Crimson, Rose, Violet, White }
private enum class HeaderStyle { Classic, Modern }

// Public because it appears in PlayerSession, which is public.
data class UserSubtitleSource(
  val id: String,
  val name: String,
  val baseUrl: String,
  val enabled: Boolean = true,
)

internal object UserSubtitleSourceStore {
  private const val PREFS_NAME = "streamdek_subtitle_sources"
  private const val SOURCES_KEY = "sources"

  private fun sourcesKey(ownerKey: String): String = "$SOURCES_KEY:$ownerKey"

  fun load(context: Context, ownerKey: String): List<UserSubtitleSource> = runCatching {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(sourcesKey(ownerKey), null)
      ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(SOURCES_KEY, "[]").orEmpty()
    val array = JSONArray(raw)
    List(array.length()) { index ->
      val item = array.getJSONObject(index)
      UserSubtitleSource(
        id = item.getString("id"),
        name = item.getString("name"),
        baseUrl = item.getString("baseUrl"),
        enabled = item.optBoolean("enabled", true),
      )
    }
  }.getOrDefault(emptyList())

  fun add(context: Context, ownerKey: String, rawUrl: String): Result<Unit> = runCatching {
    val baseUrl = normalizeBaseUrl(rawUrl)
    val sources = load(context, ownerKey)
    require(sources.none { it.baseUrl.equals(baseUrl, true) }) { "That subtitle source is already added." }
    val host = Uri.parse(baseUrl).host.orEmpty().removePrefix("www.")
    val source = UserSubtitleSource(
      id = baseUrl.lowercase(Locale.US).hashCode().toUInt().toString(16),
      name = host.ifBlank { "Custom subtitle source" },
      baseUrl = baseUrl,
    )
    save(context, ownerKey, sources + source)
  }

  fun setEnabled(context: Context, ownerKey: String, id: String, enabled: Boolean) {
    save(context, ownerKey, load(context, ownerKey).map { if (it.id == id) it.copy(enabled = enabled) else it })
  }

  fun remove(context: Context, ownerKey: String, id: String) {
    save(context, ownerKey, load(context, ownerKey).filterNot { it.id == id })
  }

  private fun save(context: Context, ownerKey: String, sources: List<UserSubtitleSource>) {
    val array = JSONArray().apply {
      sources.forEach { source ->
        put(JSONObject().put("id", source.id).put("name", source.name).put("baseUrl", source.baseUrl).put("enabled", source.enabled))
      }
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(sourcesKey(ownerKey), array.toString()).apply()
  }

  private fun normalizeBaseUrl(rawUrl: String): String {
    var value = rawUrl.trim().replaceFirst(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://").trimEnd('/')
    if (value.endsWith("/manifest.json", true)) value = value.dropLast("/manifest.json".length)
    val parsed = Uri.parse(value)
    require(parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) { "Enter a valid subtitle add-on link." }
    require(!parsed.host.isNullOrBlank()) { "Enter a valid subtitle add-on link." }
    return value
  }
}
private fun settingsOwnerKey(uiState: AppUiState): String {
  val userId = uiState.session?.user?.uid
  return if (userId == null) uiState.activeProfileId?.let { "guest:$it" } ?: GUEST_OWNER_KEY
  else uiState.activeProfileId?.let { "$userId:$it" } ?: userId
}

private enum class MediaFilter { All, Movies, Series }
private enum class BrowseSort { Original, TitleAscending, TitleDescending }
private data class ResolvedPlayback(val url: String, val stream: AddonStream)
private data class PendingStreamLoad(val detailId: String, val episode: EpisodeItem?)


private val PageTitleSize = 32.sp
private val PageTitleLineHeight = 37.sp
private val LocalStreamDekHazeState = compositionLocalOf<HazeState?> { null }

private enum class SettingsRoute { GeneralPlayback, HomeAppearance, HomeLayout, DetailScreen, Streams, PlaybackAutomation, Subtitles, Addons, M3uPlaylists, Downloads, Plugins, Debrid, Trakt, ConnectTv, Ratings, Profiles, Account, AppUpdates }
private data class SearchYearOption(val label: String, val value: String?)
private data class SearchSelectionOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

private data class TraktDashboardState(
  val status: TraktStatus,
  val continueWatching: List<TraktItem>,
  val watchlist: List<TraktItem>,
  val recommendations: List<TraktItem>,
  val trending: List<TraktItem>,
)

private data class AuthFormState(
  val email: String = "",
  val password: String = "",
  val resetCode: String = "",
  val newPassword: String = "",
)

private data class AppUiState(
  val booting: Boolean = true,
  val rememberedEmail: String = "",
  val session: AuthSession? = null,
  val homeLoading: Boolean = false,
  val profileTransitioning: Boolean = false,
  val allHomeSections: List<MediaSection> = emptyList(),
  val homeSections: List<MediaSection> = emptyList(),
  val homeHeroTitleLogos: Map<String, String> = emptyMap(),
  val addonCatalogRatings: Map<String, Double> = emptyMap(),
  val searchLoading: Boolean = false,
  val searchResults: List<MediaItem> = emptyList(),
  val searchResultQuery: String = "",
  val localContinueWatching: List<MediaItem> = emptyList(),
  val localResumeEntries: List<PlaybackMemoryEntry> = emptyList(),
  // Browse navigation lives in the view model so it survives the player screen
  // replacing the main scene — otherwise backing out of a detail page opened from
  // a browse row would fall through to Home.
  val browseRow: HomeRow? = null,
  val browseLoadedItems: List<MediaItem> = emptyList(),
  val browseReturnItemId: String? = null,
  val networkBrowseItem: MediaItem? = null,
  val detailLoading: Boolean = false,
  val detail: MediaDetail? = null,
  val detailIsLive: Boolean = false,
  val detailFallbackItem: MediaItem? = null,
  val personLoading: Boolean = false,
  val selectedPerson: PersonDetail? = null,
  val seasonLoading: Boolean = false,
  val selectedSeasonEpisodes: List<EpisodeItem> = emptyList(),
  val selectedSeasonNumber: Int? = null,
  val selectedEpisode: EpisodeItem? = null,
  val detailSelectedTab: String? = null,
  val streamLoading: Boolean = false,
  val nextEpisodeLoading: Boolean = false,
  val nextEpisodeLoadingLabel: String? = null,
  val watchedEpisodeRevision: Int = 0,
  val pendingStreamSources: Int = 0,
  val availableStreams: List<AddonStream> = emptyList(),
  val profilesLoading: Boolean = false,
  val profiles: List<StreamProfile> = emptyList(),
  val activeProfileId: String? = null,
  val addonsLoading: Boolean = false,
  val addons: List<InstalledAddon> = emptyList(),
  val m3uLoading: Boolean = false,
  val m3uProgress: Float? = null,
  val m3uStatusMessage: String? = null,
  val m3uErrorMessage: String? = null,
  val m3uSources: List<M3uPlaylistSource> = emptyList(),
  val m3uChannels: List<MediaItem> = emptyList(),
  val m3uVodItems: List<MediaItem> = emptyList(),
  val downloadsEnabled: Boolean = false,
  val downloads: List<DownloadEntry> = emptyList(),
  val debridLoading: Boolean = false,
  val debridAccounts: List<DebridAccount> = emptyList(),
  val traktLoading: Boolean = false,
  val traktStatus: TraktStatus = TraktStatus(false, null),
  val traktContinueWatching: List<TraktItem> = emptyList(),
  val traktWatchlist: List<TraktItem> = emptyList(),
  val traktRecommendations: List<TraktItem> = emptyList(),
  val traktTrending: List<TraktItem> = emptyList(),
  val mergedWatchlist: List<MediaItem> = emptyList(),
  val favouriteChannels: List<MediaItem> = emptyList(),
  val playerLiveChannels: List<MediaItem> = emptyList(),
  val playerLiveChannelsLoading: Boolean = false,
  val pendingDeviceCode: DeviceCodeInfo? = null,
  val pinPromptProfileId: String? = null,
  val pinPromptProfileName: String? = null,
  val playerSession: PlayerSession? = null,
  val playerLaunching: Boolean = false,
  val playerLaunchingLabel: String? = null,
  val liveChannelSwitching: Boolean = false,
  val liveChannelSwitchingLabel: String? = null,
  // Owner key of a guest identity with local data (watchlist/favourites/add-ons) found
  // right before a successful registration - non-null while the merge-into-account prompt
  // should be offered.
  val pendingGuestMerge: String? = null,
  val returnToDetailAfterPlayer: Boolean = false,
  val playerReturnEpisodeId: String? = null,
  val showProfilePicker: Boolean = false,
  val appAppearance: AppAppearance = AppAppearance.System,
  val appLanguage: String = "en",
  val themePreset: AppThemePreset = AppThemePreset.Monochrome,
  val headerStyle: HeaderStyle = HeaderStyle.Classic,
  val pictureInPictureEnabled: Boolean = false,
  val decoderMode: String = "HW+",
  val renderSurface: String = "Standard",
  val playerEngine: String = "Auto",
  val preferredAudioLanguage: String = "en",
  val detailPageStyle: DetailPageStyle = DetailPageStyle.Classic,
  val seasonTabStyle: SeasonTabStyle = SeasonTabStyle.Regular,
  val showNavLabels: Boolean = true,
  val collapsibleNavigationEnabled: Boolean = false,
  val navigationAutoCollapseSeconds: Int = 5,
  val showStreamsList: Boolean = true,
  val heroTrailerAutoplay: Boolean = true,
  val heroTrailerResolution: Int = 720,
  val showHeroSynopsis: Boolean = true,
  val continueWatchingStyle: ContinueWatchingStyle = ContinueWatchingStyle.Glass,
  val liveLandscapeCards: Boolean = true,
  val liveFavouriteDrawerCards: Boolean = false,
  val rememberLastSource: Boolean = true,
  val syncOnCellular: Boolean = false,
  val syncRefreshing: Boolean = false,
  val skipIntroEnabled: Boolean = true,
  val skipRecapEnabled: Boolean = true,
  val skipEndingEnabled: Boolean = true,
  val autoPlayNextEpisode: Boolean = true,
  val preferBingeGroup: Boolean = true,
  val autoLoadSubtitles: Boolean = true,
  val blurUnwatchedEpisodes: Boolean = true,
  val nextEpisodeThresholdMode: String = "minutes",
  val nextEpisodeThresholdPercent: Int = 95,
  val nextEpisodeThresholdMinutes: Int = 2,
  val torrentServerSettings: TorrentServerSettings = TorrentServerSettings(),
  val torrentServerStatus: TorrentServerStatus = TorrentServerStatus(),
  val ratingsEnabled: Boolean = true,
  val externalRatingsEnabled: Boolean = true,
  val enabledRatingProviders: Set<String> = DEFAULT_RATING_PROVIDER_IDS,
  val mdblistApiKey: String = "",
  val vividAmbient: Boolean = true,
  val ambientTintPercent: Int = 100,
  val defaultAppCatalogsEnabled: Boolean = true,
  val homeCatalogRows: List<HomeCatalogRow> = emptyList(),
  val fusionBadgesEnabled: Boolean = true,
  val showSizeBadges: Boolean = true,
  val showAddonTmdbRatings: Boolean = false,
  val preferredQuality: String = "Auto",
  val maxFileSizeGb: Int = 0,
  val badgePosition: String = "Bottom",
  val fusionBadgeUrls: List<String> = listOf(DEFAULT_FUSION_BADGE_URL),
  val activeFusionBadgeUrl: String? = null,
  val autoUpdateChecksEnabled: Boolean = true,
  val updateChecking: Boolean = false,
  val updateDownloading: Boolean = false,
  val updateProgress: Float? = null,
  val availableUpdate: UpdateManifest? = null,
  val updatePromptVisible: Boolean = false,
  val updateStatusMessage: String? = null,
  val updateErrorMessage: String? = null,
  val fusionBadgeSources: Map<String, FusionBadgeSourceState> = emptyMap(),
  val errorMessage: String? = null,
  val infoMessage: String? = null,
)

private data class HomeRow(
  val id: String,
  val title: String,
  val items: List<MediaItem>,
)

private data class HomeCatalogRow(
  val id: String,
  val title: String,
  val subtitle: String,
  val builtin: Boolean,
  val enabled: Boolean = true,
)

private data class FusionBadgeGroup(
  val id: String,
  val name: String,
)

private data class FusionBadgeFilter(
  val id: String,
  val groupId: String,
  val name: String,
  val pattern: String,
  val imageUrl: String,
  val isEnabled: Boolean = true,
)

private data class FusionBadgeSource(
  val url: String,
  val groups: List<FusionBadgeGroup>,
  val filters: List<FusionBadgeFilter>,
  val fetchedAt: String = System.currentTimeMillis().toString(),
)

private data class FusionBadgeSourceState(
  val url: String,
  val source: FusionBadgeSource? = null,
  val loading: Boolean = false,
  val error: String? = null,
)

private const val DEFAULT_FUSION_BADGE_URL = "https://pastebin.com/raw/5xiu5fLL"
private const val MAX_FUSION_BADGE_URLS = 3
private fun Int.formattedItemCount(): String = String.format("%,d", this)
private const val LANGUAGE_BADGE_GROUP_ID = "gl"
private val DEFAULT_RATING_PROVIDER_ORDER = listOf("imdb", "tmdb", "tomatoes", "metacritic", "trakt", "letterboxd", "audience")
private val DEFAULT_RATING_PROVIDER_IDS = DEFAULT_RATING_PROVIDER_ORDER.toSet()

private fun parseStreamSizeGiB(size: String?): Double? {
  val raw = size?.trim().orEmpty()
  if (raw.isBlank()) return null
  val match = Regex("""([\d.]+)\s*(GB|GiB|MB|MiB|TB|TiB)""", RegexOption.IGNORE_CASE).find(raw) ?: return null
  val value = match.groupValues[1].toDoubleOrNull() ?: return null
  return when (match.groupValues[2].lowercase()) {
    "tb", "tib" -> value * 1024.0
    "mb", "mib" -> value / 1024.0
    else -> value
  }
}

// Some addons only report the size inside the title/description, so fall back to
// scanning the stream text — otherwise those results bypass the max size cap.
private fun streamSizeGiB(stream: AddonStream): Double? =
  parseStreamSizeGiB(stream.size)
    ?: parseStreamSizeGiB(listOfNotNull(stream.title, stream.name, stream.filename, stream.description).joinToString(" "))

private fun preferredQualityBoost(stream: AddonStream, preferredQuality: String): Int {
  val quality = preferredQuality.trim()
  if (quality.equals("Auto", ignoreCase = true)) return 0
  val text = listOfNotNull(stream.title, stream.name, stream.filename, stream.description, stream.quality).joinToString(" ").lowercase()
  // An exact match must dominate every other tag bonus combined (codec/audio/remux
  // add up to roughly +300) so the chosen quality genuinely wins the ranking.
  return when (quality) {
    "2160p" -> when {
      "2160" in text || "4k" in text -> 520
      "1080" in text -> 200
      "720" in text -> 90
      else -> 20
    }
    "1080p" -> when {
      "1080" in text -> 520
      "720" in text -> 200
      "2160" in text || "4k" in text -> 120
      else -> 20
    }
    "720p" -> when {
      "720" in text -> 520
      "1080" in text -> 160
      "2160" in text || "4k" in text -> 40
      else -> 20
    }
    else -> 0
  }
}

private fun rankedStreams(streams: List<AddonStream>, hasDebrid: Boolean, preferredQuality: String = "Auto", maxFileSizeGb: Int = 0): List<AddonStream> =
  streams
    .filter { stream ->
      val sizeGiB = streamSizeGiB(stream)
      maxFileSizeGb <= 0 || sizeGiB == null || sizeGiB <= maxFileSizeGb.toDouble()
    }
    .sortedWith(
      compareByDescending<AddonStream> { streamScore(it, hasDebrid, preferredQuality, maxFileSizeGb) }
        .thenBy { it.title ?: it.name ?: it.filename ?: "" },
    )

private fun streamIdentity(stream: AddonStream): String =
  listOf(stream.addonId, stream.url.orEmpty(), stream.infoHash.orEmpty(), stream.name.orEmpty(), stream.title.orEmpty()).joinToString("|")

private val liveMediaTypes = setOf("sport", "sports", "channel", "live", "iptv", "events")

/** Stable id for a Media3 download derived from the stream's URL, so re-downloading the
 * same source resumes/reuses the same entry instead of creating a duplicate. */
private fun downloadIdFor(stream: AddonStream): String = "dl:" + (stream.url ?: "").hashCode().toUInt().toString(16)

// Manifest types that mean an add-on serves live TV. Per the Stremio protocol "tv"
// is live channels; series add-ons declare "series".
private val liveAddonTypes = setOf("tv", "channel", "live", "iptv", "sport", "sports", "events")

// In the Stremio addon protocol, catalog type "tv" means Live TV channels
// (series catalogs use type "series"). Some non-standard addons use "tv" for
// series, but those always carry IMDb/TMDB database ids — live channels never do.
private fun looksLikeSeriesDatabaseId(id: String): Boolean {
  val base = id.trim().substringBefore(":")
  return base.matches(Regex("^tt\\d+$", RegexOption.IGNORE_CASE)) ||
    id.startsWith("tmdb:", ignoreCase = true) ||
    id.startsWith("tvdb:", ignoreCase = true) ||
    base.matches(Regex("^\\d+$"))
}

// Scales the page background tint by the user's ambient tint preference so the
// ambient glow can show through more (lower percent = more transparent tint).
private fun ambientTintAlpha(base: Float, percent: Int): Float =
  (base * (percent.coerceIn(20, 100) / 100f)).coerceIn(0f, 1f)

private fun MediaItem.isLiveCatalogItem(): Boolean {
  val catalogText = listOfNotNull(sourceAddonName, sourceCatalogType, sourceCatalogId, sourceCatalogName).joinToString(" ").lowercase()
  val isLiveTvChannel = sourceCatalogType == "tv" && !looksLikeSeriesDatabaseId(id)
  return sourceCatalogType in liveMediaTypes ||
    isLiveTvChannel ||
    (directStreamUrl != null && sourceCatalogType !in setOf("movie", "series", "vod")) ||
    listOf("live", "channel", "sport", "ultra max tv", "iptv").any { it in catalogText }
}

// Addons are installed dynamically by manifest URL, so there is no vendor flag
// declaring "this addon proxies its streams" — instead we infer it: either the
// stream URL resolves back to the addon's own host (rather than a third-party
// origin/CDN), or the addon supplies custom request headers (Stremio
// behaviorHints.proxyHeaders) needed to reach the real origin through it.
private fun AddonStream.isLikelyProxied(addon: InstalledAddon?): Boolean {
  val streamHost = url?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.lowercase()
  val addonHost = listOfNotNull(addon?.transportUrl, addon?.baseUrl, addon?.url)
    .firstNotNullOfOrNull { runCatching { java.net.URI(it).host }.getOrNull() }
    ?.lowercase()
  return requestHeaders.isNotEmpty() || (streamHost != null && addonHost != null && streamHost == addonHost)
}

private fun streamMatchesSportsAddon(stream: AddonStream): Boolean {
  val text = listOf(stream.addonId, stream.addonName, stream.description, stream.name, stream.title).joinToString(" ").lowercase()
  return "sport" in text || "sports" in text || "espn" in text || "football" in text || "cricket" in text || "ufc" in text || "live tv" in text || "live-tv" in text || "live event" in text
}

private fun mediaStreamsOnly(streams: List<AddonStream>, detail: MediaDetail): List<AddonStream> =
  if (detail.type == "movie" || detail.type == "tv" || detail.type == "series") streams.filterNot(::streamMatchesSportsAddon) else streams

private fun MediaDetail.withCatalogFallback(item: MediaItem?): MediaDetail {
  if (item == null) return this
  return copy(
    type = type.takeIf { it == "movie" || it == "tv" } ?: if (item.type == "series") "tv" else item.type,
    title = title.ifBlank { item.title },
    titleLogo = titleLogo ?: item.titleLogo,
    year = year ?: item.year,
    description = description.ifBlank { item.description },
    poster = poster ?: item.poster,
    backdrop = backdrop ?: item.backdrop,
    rating = rating ?: item.rating,
    tmdbRating = tmdbRating ?: item.rating,
    genres = genres.ifEmpty { item.genres },
  )
}

private fun LocalAddonMeta.seasonSummaries(): List<SeasonSummary> = episodes
  .groupBy(EpisodeItem::seasonNumber)
  .toSortedMap()
  .map { (seasonNumber, seasonEpisodes) ->
    SeasonSummary(
      seasonNumber = seasonNumber,
      name = "Season $seasonNumber",
      episodeCount = seasonEpisodes.size,
      poster = poster,
    )
  }

private fun MediaDetail.withLocalAddonMeta(meta: LocalAddonMeta): MediaDetail {
  val bridgeSeasons = meta.seasonSummaries()
  return copy(
    type = if (meta.episodes.isNotEmpty()) "tv" else type,
    title = meta.title.ifBlank { title },
    year = year ?: meta.year,
    releaseDate = releaseDate ?: meta.releaseDate,
    description = meta.description.ifBlank { description },
    poster = poster ?: meta.poster,
    backdrop = backdrop ?: meta.backdrop ?: meta.poster,
    genres = genres.ifEmpty { meta.genres },
    runtimeMinutes = runtimeMinutes ?: meta.runtimeMinutes,
    seasonsCount = if (bridgeSeasons.isNotEmpty()) bridgeSeasons.size else seasonsCount,
    imdbId = imdbId ?: meta.imdbId,
    seasons = if (bridgeSeasons.isNotEmpty()) bridgeSeasons else seasons,
    cast = cast.ifEmpty { meta.cast },
  )
}

private fun LocalAddonMeta.toFallbackDetail(item: MediaItem): MediaDetail =
  item.toFallbackDetail().copy(
    id = id,
    type = if (episodes.isNotEmpty()) "tv" else item.type,
    title = title.ifBlank { item.title },
    year = year ?: item.year,
    releaseDate = releaseDate,
    description = description.ifBlank { item.description.ifBlank { "No synopsis available." } },
    poster = poster ?: item.poster,
    backdrop = backdrop ?: item.backdrop ?: poster,
    genres = genres.ifEmpty { item.genres },
    runtimeMinutes = runtimeMinutes,
    seasonsCount = seasonSummaries().size.takeIf { it > 0 },
    imdbId = imdbId,
    seasons = seasonSummaries(),
    cast = cast,
  )

private fun MediaItem.toFallbackDetail(): MediaDetail =
  MediaDetail(
    id = id,
    type = if (isLiveCatalogItem()) "live" else if (type == "series") "tv" else type,
    title = title,
    titleLogo = null,
    tagline = null,
    year = year,
    releaseDate = null,
    description = if (isLiveCatalogItem()) description else description.ifBlank { "No synopsis available." },
    poster = poster,
    backdrop = backdrop,
    trailerUrl = null,
    rating = rating,
    imdbRating = null,
    tmdbRating = rating,
    genres = genres,
    runtimeMinutes = null,
    seasonsCount = null,
    imdbId = id.takeIf { it.startsWith("tt") },
    seasons = emptyList(),
  )

private fun streamScore(stream: AddonStream, hasDebrid: Boolean, preferredQuality: String = "Auto", maxFileSizeGb: Int = 0): Int {
  val text = listOfNotNull(stream.title, stream.name, stream.filename, stream.description, stream.quality, stream.addonName).joinToString(" ").lowercase()
  val sizeGiB = streamSizeGiB(stream)
  var score = 0
  if (maxFileSizeGb > 0 && sizeGiB != null && sizeGiB > maxFileSizeGb.toDouble()) return Int.MIN_VALUE / 4
  if (!stream.url.isNullOrBlank()) score += 380
  if (hasDebrid && !stream.infoHash.isNullOrBlank()) score += 260
  if (stream.cachedBy.isNotEmpty()) score += 400 + ((stream.cachedBy.size - 1).coerceAtLeast(0) * 45)
  score += when {
    "2160" in text || "4k" in text -> 90
    "1080" in text -> 75
    "720" in text -> 45
    else -> 20
  }
  if ("english" in text || "multi" in text) score += 70
  if ("remux" in text) score += 35
  if ("web-dl" in text || "webdl" in text) score += 26
  if ("bluray" in text || "blu-ray" in text) score += 12
  if ("aac" in text || "flac" in text || "mp3" in text || "opus" in text) score += 90
  if ("ac3" in text || "eac3" in text || "dd+" in text) score += 40
  if ("dts:x" in text) score -= 160
  else if ("dts" in text) score -= 90
  if ("h264" in text || "h.264" in text || "avc" in text || "x264" in text) score += 120
  if ("av1" in text) score -= 80
  if ("hevc" in text || "x265" in text || "h265" in text) score += 20
  if ("cam" in text) score -= 200
  if ("telesync" in text) score -= 120
  score += preferredQualityBoost(stream, preferredQuality)
  if (sizeGiB != null) {
    score += when {
      sizeGiB in 0.6..12.5 -> 30
      sizeGiB > 30.0 -> -65
      sizeGiB < 0.25 -> -55
      else -> 0
    }
  }
  return score
}

private class AuthEntryStore(context: Context) {
  private val prefs = context.getSharedPreferences("streamdek_native_auth_entry", Context.MODE_PRIVATE)

  fun loadEmail(): String = prefs.getString("last_email", "").orEmpty()

  fun saveEmail(email: String) {
    prefs.edit().putString("last_email", email).apply()
  }
}

private class AppSettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
  private val appContext = context.applicationContext
  private var profilePrefs = prefs
  private val profileSettingKeys = setOf(
    "detail_page_style", "season_tab_style", "show_streams_list", "hero_trailer_autoplay", "hero_trailer_resolution",
    "show_hero_synopsis", "continue_watching_style", "live_landscape_cards", "live_favourite_drawer_cards",
    "remember_last_source", "skip_intro_enabled", "skip_segments_enabled", "skip_recap_enabled", "skip_ending_enabled",
    "auto_play_next_episode", "prefer_binge_group", "auto_load_subtitles", "blur_unwatched_episodes",
    "next_episode_threshold_mode", "next_episode_threshold_percent", "next_episode_threshold_minutes",
    "ratings_enabled", "external_ratings_enabled", "enabled_rating_providers", "vivid_ambient", "ambient_tint_percent",
    "default_app_catalogs_enabled", "home_catalog_rows", "fusion_badges", "show_size_badges", "show_addon_tmdb_ratings",
    "preferred_quality", "max_file_size_gb", "badge_position", "fusion_badge_urls", "active_fusion_badge_url",
  )

  fun selectProfileStorage(ownerKey: String) {
    val safeOwner = ownerKey.ifBlank { GUEST_OWNER_KEY }
    profilePrefs = appContext.getSharedPreferences(
      "streamdek_native_profile_settings_" + safeOwner.hashCode().toUInt().toString(16),
      Context.MODE_PRIVATE,
    )
    if (profilePrefs.all.isEmpty()) {
      val editor = profilePrefs.edit()
      profileSettingKeys.forEach { key ->
        when (val value = prefs.all[key]) {
          is Boolean -> editor.putBoolean(key, value)
          is Int -> editor.putInt(key, value)
          is Long -> editor.putLong(key, value)
          is Float -> editor.putFloat(key, value)
          is String -> editor.putString(key, value)
          is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
        }
      }
      editor.apply()
    }
  }

  fun applyTo(state: AppUiState): AppUiState = state.copy(
    appAppearance = runCatching { AppAppearance.valueOf(prefs.getString("app_appearance", AppAppearance.System.name) ?: AppAppearance.System.name) }.getOrDefault(AppAppearance.System),
    appLanguage = normalizeAppLanguage(prefs.getString(APP_LANGUAGE_PREFERENCE, "en")),
    themePreset = runCatching { AppThemePreset.valueOf(prefs.getString("theme_preset", AppThemePreset.Monochrome.name) ?: AppThemePreset.Monochrome.name) }.getOrDefault(AppThemePreset.Monochrome),
    headerStyle = runCatching { HeaderStyle.valueOf(prefs.getString("header_style", HeaderStyle.Classic.name) ?: HeaderStyle.Classic.name) }.getOrDefault(HeaderStyle.Classic),
    pictureInPictureEnabled = prefs.getBoolean("pip_enabled", false),
    decoderMode = normalizeDecoderModeSetting(prefs.getString("decoder_mode", "HW+") ?: "HW+"),
    renderSurface = normalizeRenderSurfaceSetting(prefs.getString("render_surface", "Standard") ?: "Standard"),
    playerEngine = normalizePlayerEngineSetting(prefs.getString("player_engine", "Auto") ?: "Auto"),
    preferredAudioLanguage = normalizePreferredAudioLanguage(prefs.getString("preferred_audio_language", "en")),
    detailPageStyle = runCatching { DetailPageStyle.valueOf(profilePrefs.getString("detail_page_style", DetailPageStyle.Classic.name) ?: DetailPageStyle.Classic.name) }.getOrDefault(DetailPageStyle.Classic),
    seasonTabStyle = runCatching { SeasonTabStyle.valueOf(profilePrefs.getString("season_tab_style", SeasonTabStyle.Regular.name) ?: SeasonTabStyle.Regular.name) }.getOrDefault(SeasonTabStyle.Regular),
    showNavLabels = prefs.getBoolean("show_nav_labels", true),
    collapsibleNavigationEnabled = prefs.getBoolean("collapsible_navigation_enabled", false),
    downloadsEnabled = prefs.getBoolean("downloads_enabled", false),
    navigationAutoCollapseSeconds = prefs.getInt("navigation_auto_collapse_seconds", 5).coerceIn(2, 15),
    showStreamsList = profilePrefs.getBoolean("show_streams_list", true),
    heroTrailerAutoplay = profilePrefs.getBoolean("hero_trailer_autoplay", true),
    heroTrailerResolution = profilePrefs.getInt("hero_trailer_resolution", 720).coerceIn(360, 1080),
    showHeroSynopsis = profilePrefs.getBoolean("show_hero_synopsis", true),
    continueWatchingStyle = runCatching { ContinueWatchingStyle.valueOf(profilePrefs.getString("continue_watching_style", ContinueWatchingStyle.Glass.name) ?: ContinueWatchingStyle.Glass.name) }.getOrDefault(ContinueWatchingStyle.Glass),
    liveLandscapeCards = profilePrefs.getBoolean("live_landscape_cards", true),
    liveFavouriteDrawerCards = profilePrefs.getBoolean("live_favourite_drawer_cards", false),
    rememberLastSource = profilePrefs.getBoolean("remember_last_source", true),
    syncOnCellular = prefs.getBoolean("sync_on_cellular", false),
    skipIntroEnabled = profilePrefs.getBoolean("skip_intro_enabled", profilePrefs.getBoolean("skip_segments_enabled", true)),
    skipRecapEnabled = profilePrefs.getBoolean("skip_recap_enabled", true),
    skipEndingEnabled = profilePrefs.getBoolean("skip_ending_enabled", true),
    autoPlayNextEpisode = profilePrefs.getBoolean("auto_play_next_episode", true),
    preferBingeGroup = profilePrefs.getBoolean("prefer_binge_group", true),
    autoLoadSubtitles = profilePrefs.getBoolean("auto_load_subtitles", true),
    blurUnwatchedEpisodes = profilePrefs.getBoolean("blur_unwatched_episodes", true),
    nextEpisodeThresholdMode = profilePrefs.getString("next_episode_threshold_mode", "minutes") ?: "minutes",
    nextEpisodeThresholdPercent = profilePrefs.getInt("next_episode_threshold_percent", 95).coerceIn(50, 99),
    nextEpisodeThresholdMinutes = profilePrefs.getInt("next_episode_threshold_minutes", 2).coerceIn(1, 15),
    torrentServerSettings = TorrentServerSettings(
      enabled = prefs.getBoolean("torrent_enabled", true),
      streamingMode = prefs.getString("torrent_streaming_mode", "server") ?: "server",
      profile = prefs.getString("torrent_profile", "default") ?: "default",
      cacheSizeGb = prefs.getInt("torrent_cache_size_gb", 5),
      port = prefs.getInt("torrent_port", 11100),
      runAsForegroundService = prefs.getBoolean("torrent_run_foreground", false),
    ),
    ratingsEnabled = profilePrefs.getBoolean("ratings_enabled", true),
    externalRatingsEnabled = profilePrefs.getBoolean("external_ratings_enabled", true),
    enabledRatingProviders = parseRatingProviderIds(profilePrefs.getString("enabled_rating_providers", null)),
    mdblistApiKey = prefs.getString("mdblist_api_key", "") ?: "",
    vividAmbient = profilePrefs.getBoolean("vivid_ambient", true),
    ambientTintPercent = profilePrefs.getInt("ambient_tint_percent", 100).coerceIn(20, 100),
    defaultAppCatalogsEnabled = profilePrefs.getBoolean("default_app_catalogs_enabled", true),
    homeCatalogRows = parseHomeCatalogRows(profilePrefs.getString("home_catalog_rows", null)),
    fusionBadgesEnabled = profilePrefs.getBoolean("fusion_badges", true),
    showSizeBadges = profilePrefs.getBoolean("show_size_badges", true),
    showAddonTmdbRatings = profilePrefs.getBoolean("show_addon_tmdb_ratings", false),
    preferredQuality = profilePrefs.getString("preferred_quality", "Auto") ?: "Auto",
    maxFileSizeGb = profilePrefs.getInt("max_file_size_gb", 0),
    badgePosition = profilePrefs.getString("badge_position", "Bottom") ?: "Bottom",
    fusionBadgeUrls = parseFusionBadgeUrls(profilePrefs.getString("fusion_badge_urls", null)),
    activeFusionBadgeUrl = profilePrefs.getString("active_fusion_badge_url", null),
    autoUpdateChecksEnabled = prefs.getBoolean("auto_update_checks", true),
  )

  fun saveAutoUpdateChecks(value: Boolean) { prefs.edit().putBoolean("auto_update_checks", value).apply() }
  fun saveAppAppearance(value: AppAppearance) { prefs.edit().putString("app_appearance", value.name).apply() }
  fun saveAppLanguage(value: String) { prefs.edit().putString(APP_LANGUAGE_PREFERENCE, normalizeAppLanguage(value)).apply() }
  fun saveThemePreset(value: AppThemePreset) { prefs.edit().putString("theme_preset", value.name).apply() }
  fun saveHeaderStyle(value: HeaderStyle) { prefs.edit().putString("header_style", value.name).apply() }
  fun savePictureInPictureEnabled(value: Boolean) { prefs.edit().putBoolean("pip_enabled", value).apply() }
  fun saveDecoderMode(value: String) { prefs.edit().putString("decoder_mode", normalizeDecoderModeSetting(value)).apply() }
  fun saveRenderSurface(value: String) { prefs.edit().putString("render_surface", normalizeRenderSurfaceSetting(value)).apply() }
  fun savePlayerEngine(value: String) { prefs.edit().putString("player_engine", normalizePlayerEngineSetting(value)).apply() }
  fun savePreferredAudioLanguage(value: String) { prefs.edit().putString("preferred_audio_language", normalizePreferredAudioLanguage(value)).apply() }
  fun saveDetailPageStyle(value: DetailPageStyle) { profilePrefs.edit().putString("detail_page_style", value.name).apply() }
  fun saveSeasonTabStyle(value: SeasonTabStyle) { profilePrefs.edit().putString("season_tab_style", value.name).apply() }
  fun saveShowNavLabels(value: Boolean) { prefs.edit().putBoolean("show_nav_labels", value).apply() }
  fun saveCollapsibleNavigationEnabled(value: Boolean) { prefs.edit().putBoolean("collapsible_navigation_enabled", value).apply() }
  fun saveDownloadsEnabled(value: Boolean) { prefs.edit().putBoolean("downloads_enabled", value).apply() }
  fun saveNavigationAutoCollapseSeconds(value: Int) { prefs.edit().putInt("navigation_auto_collapse_seconds", value.coerceIn(2, 15)).apply() }
  fun saveShowStreamsList(value: Boolean) { profilePrefs.edit().putBoolean("show_streams_list", value).apply() }
  fun saveHeroTrailerAutoplay(value: Boolean) { profilePrefs.edit().putBoolean("hero_trailer_autoplay", value).apply() }
  fun saveHeroTrailerResolution(value: Int) { profilePrefs.edit().putInt("hero_trailer_resolution", value.coerceIn(360, 1080)).apply() }
  fun saveShowHeroSynopsis(value: Boolean) { profilePrefs.edit().putBoolean("show_hero_synopsis", value).apply() }
  fun saveContinueWatchingStyle(value: ContinueWatchingStyle) { profilePrefs.edit().putString("continue_watching_style", value.name).apply() }
  fun saveLiveLandscapeCards(value: Boolean) { profilePrefs.edit().putBoolean("live_landscape_cards", value).apply() }
  fun saveLiveFavouriteDrawerCards(value: Boolean) { profilePrefs.edit().putBoolean("live_favourite_drawer_cards", value).apply() }
  fun saveRememberLastSource(value: Boolean) { profilePrefs.edit().putBoolean("remember_last_source", value).apply() }
  fun saveSyncOnCellular(value: Boolean) { prefs.edit().putBoolean("sync_on_cellular", value).apply() }
  fun saveSkipIntroEnabled(value: Boolean) { profilePrefs.edit().putBoolean("skip_intro_enabled", value).putBoolean("skip_segments_enabled", value).apply() }
  fun saveSkipRecapEnabled(value: Boolean) { profilePrefs.edit().putBoolean("skip_recap_enabled", value).apply() }
  fun saveSkipEndingEnabled(value: Boolean) { profilePrefs.edit().putBoolean("skip_ending_enabled", value).apply() }
  fun saveAutoPlayNextEpisode(value: Boolean) { profilePrefs.edit().putBoolean("auto_play_next_episode", value).apply() }
  fun savePreferBingeGroup(value: Boolean) { profilePrefs.edit().putBoolean("prefer_binge_group", value).apply() }
  fun saveAutoLoadSubtitles(value: Boolean) { profilePrefs.edit().putBoolean("auto_load_subtitles", value).apply() }
  fun saveBlurUnwatchedEpisodes(value: Boolean) { profilePrefs.edit().putBoolean("blur_unwatched_episodes", value).apply() }
  fun saveNextEpisodeThresholdMode(value: String) { profilePrefs.edit().putString("next_episode_threshold_mode", value).apply() }
  fun saveNextEpisodeThresholdPercent(value: Int) { profilePrefs.edit().putInt("next_episode_threshold_percent", value.coerceIn(50, 99)).apply() }
  fun saveNextEpisodeThresholdMinutes(value: Int) { profilePrefs.edit().putInt("next_episode_threshold_minutes", value.coerceIn(1, 15)).apply() }
  fun saveTorrentServerSettings(value: TorrentServerSettings) {
    prefs.edit()
      .putBoolean("torrent_enabled", value.enabled)
      .putString("torrent_streaming_mode", value.streamingMode)
      .putString("torrent_profile", value.profile)
      .putInt("torrent_cache_size_gb", value.cacheSizeGb)
      .putInt("torrent_port", value.port)
      .putBoolean("torrent_run_foreground", value.runAsForegroundService)
      .apply()
  }
  fun saveRatingsEnabled(value: Boolean) { profilePrefs.edit().putBoolean("ratings_enabled", value).apply() }
  fun saveExternalRatingsEnabled(value: Boolean) { profilePrefs.edit().putBoolean("external_ratings_enabled", value).apply() }
  fun saveEnabledRatingProviders(value: Set<String>) { profilePrefs.edit().putString("enabled_rating_providers", JSONArray(value.toList()).toString()).apply() }
  fun saveMdblistApiKey(value: String) { prefs.edit().putString("mdblist_api_key", value.trim()).apply() }
  fun saveVividAmbient(value: Boolean) { profilePrefs.edit().putBoolean("vivid_ambient", value).apply() }
  fun saveAmbientTintPercent(value: Int) { profilePrefs.edit().putInt("ambient_tint_percent", value.coerceIn(20, 100)).apply() }
  fun saveDefaultAppCatalogsEnabled(value: Boolean) { profilePrefs.edit().putBoolean("default_app_catalogs_enabled", value).apply() }
  fun saveHomeCatalogRows(rows: List<HomeCatalogRow>) { profilePrefs.edit().putString("home_catalog_rows", serializeHomeCatalogRows(rows)).apply() }
  fun saveFusionBadges(value: Boolean) { profilePrefs.edit().putBoolean("fusion_badges", value).apply() }
  fun saveShowSizeBadges(value: Boolean) { profilePrefs.edit().putBoolean("show_size_badges", value).apply() }
  fun saveShowAddonTmdbRatings(value: Boolean) { profilePrefs.edit().putBoolean("show_addon_tmdb_ratings", value).apply() }
  fun savePreferredQuality(value: String) { profilePrefs.edit().putString("preferred_quality", value).apply() }
  fun saveMaxFileSizeGb(value: Int) { profilePrefs.edit().putInt("max_file_size_gb", value).apply() }
  fun saveBadgePosition(value: String) { profilePrefs.edit().putString("badge_position", value).apply() }
  fun saveFusionBadgeUrls(urls: List<String>, activeUrl: String?) {
    profilePrefs.edit()
      .putString("fusion_badge_urls", JSONArray(urls).toString())
      .putString("active_fusion_badge_url", activeUrl)
      .apply()
  }
}

private fun parseFusionBadgeUrls(raw: String?): List<String> {
  if (raw.isNullOrBlank()) return listOf(DEFAULT_FUSION_BADGE_URL)
  return runCatching {
    val source = JSONArray(raw)
    buildList {
      for (index in 0 until source.length()) {
        val url = source.optString(index).trim()
        if (url.isNotBlank() && !contains(url)) add(url)
      }
    }.take(MAX_FUSION_BADGE_URLS).ifEmpty { listOf(DEFAULT_FUSION_BADGE_URL) }
  }.getOrDefault(listOf(DEFAULT_FUSION_BADGE_URL))
}

private fun parseRatingProviderIds(raw: String?): Set<String> {
  if (raw.isNullOrBlank()) return DEFAULT_RATING_PROVIDER_IDS
  return runCatching {
    val source = JSONArray(raw)
    buildSet {
      for (index in 0 until source.length()) {
        source.optString(index).trim().lowercase().ifBlank { null }?.let(::add)
      }
    }.ifEmpty { DEFAULT_RATING_PROVIDER_IDS }
  }.getOrDefault(DEFAULT_RATING_PROVIDER_IDS)
}

private fun normalizeDecoderModeSetting(raw: String): String = when (raw.trim()) {
  "Prefer Device", "HW+" -> "HW+"
  "Device Only", "HW" -> "HW"
  "Prefer App", "SW" -> "SW"
  else -> "HW+"
}

private fun normalizeRenderSurfaceSetting(raw: String): String = when (raw.trim()) {
  "Texture", "Standard" -> "Standard"
  "Compatibility" -> "Compatibility"
  else -> "Standard"
}

internal fun normalizePlayerEngineSetting(raw: String): String = when (raw.trim().lowercase(Locale.US)) {
  "media3", "exo", "exoplayer" -> "Media3"
  "mpv" -> "MPV"
  else -> "Auto"
}

private fun TorrentServerSettings.toServiceConfig(): TorrentServerConfig = TorrentServerConfig(
  enabled = enabled,
  streamingMode = streamingMode,
  profile = profile,
  cacheSizeGb = cacheSizeGb,
  port = port,
  runAsForegroundService = runAsForegroundService,
)

private fun torrentServerStatusFromSnapshot(snapshot: Map<String, Any>, fallback: TorrentServerSettings): TorrentServerStatus {
  val port = (snapshot["port"] as? Number)?.toInt() ?: fallback.port
  return TorrentServerStatus(
    isOnline = snapshot["isOnline"] as? Boolean ?: false,
    isForeground = snapshot["isForeground"] as? Boolean ?: false,
    requestedForeground = snapshot["requestedForeground"] as? Boolean ?: fallback.runAsForegroundService,
    port = port,
    url = snapshot["url"] as? String ?: "http://127.0.0.1:$port",
    cacheDirectory = snapshot["cacheDirectory"] as? String ?: "",
    torrentStoreDirectory = snapshot["torrentStoreDirectory"] as? String ?: "",
    cacheUsageBytes = (snapshot["cacheUsageBytes"] as? Number)?.toLong() ?: 0L,
    profile = snapshot["profile"] as? String ?: fallback.profile,
    cacheSizeGb = (snapshot["cacheSizeGb"] as? Number)?.toInt() ?: fallback.cacheSizeGb,
    recoveryMode = snapshot["recoveryMode"] as? String ?: "idle",
    lastStartupError = snapshot["lastStartupError"] as? String ?: "",
    foregroundDowngradeReason = snapshot["foregroundDowngradeReason"] as? String ?: "",
    lifecycleState = snapshot["lifecycleState"] as? String ?: "idle",
  )
}

private fun buildTorrentMagnet(infoHash: String, filename: String?): String {
  val normalized = infoHash.trim()
  val displayName = filename?.takeIf { it.isNotBlank() }?.let { "&dn=${Uri.encode(it)}" }.orEmpty()
  return "magnet:?xt=urn:btih:$normalized$displayName"
}


private data class ThemeAccentPalette(
  val accent: Color,
  val tertiary: Color,
)

private fun themeAccentPalette(theme: AppThemePreset): ThemeAccentPalette = when (theme) {
  AppThemePreset.Monochrome -> ThemeAccentPalette(accent = Color(0xFFFFFFFF), tertiary = Color(0xFFE5E7EB))
  AppThemePreset.Ocean -> ThemeAccentPalette(accent = Color(0xFF60A5FA), tertiary = Color(0xFF38BDF8))
  AppThemePreset.Emerald -> ThemeAccentPalette(accent = Color(0xFF34D399), tertiary = Color(0xFF10B981))
  AppThemePreset.Amber -> ThemeAccentPalette(accent = Color(0xFFFBBF24), tertiary = Color(0xFFF59E0B))
  AppThemePreset.Crimson -> ThemeAccentPalette(accent = Color(0xFFFB7185), tertiary = Color(0xFFE11D48))
  AppThemePreset.Rose -> ThemeAccentPalette(accent = Color(0xFFF9A8D4), tertiary = Color(0xFFF472B6))
  AppThemePreset.Violet -> ThemeAccentPalette(accent = Color(0xFFC4B5FD), tertiary = Color(0xFFA78BFA))
  AppThemePreset.White -> ThemeAccentPalette(accent = Color(0xFFE2E8F0), tertiary = Color(0xFFCBD5E1))
}

private fun appColorScheme(theme: AppThemePreset, darkMode: Boolean) = themeAccentPalette(theme).let { palette ->
  if (darkMode) {
    darkColorScheme(
      primary = palette.accent,
      secondary = palette.accent.copy(alpha = 0.92f),
      tertiary = palette.tertiary,
      background = Color.Black,
      surface = Color(0xFF111111),
      surfaceVariant = Color(0xFF1D1D1D),
      onPrimary = Color(0xFF06243B),
      onBackground = Color(0xFFF5F7FB),
      onSurface = Color(0xFFF5F7FB),
    )
  } else {
    lightColorScheme(
      primary = palette.accent,
      secondary = palette.tertiary,
      tertiary = palette.tertiary,
      background = Color(0xFFF5F4F0),
      surface = Color.White,
      surfaceVariant = Color(0xFFE2E8F0),
      onPrimary = Color.Black,
      onBackground = Color(0xFF0F172A),
      onSurface = Color(0xFF0F172A),
    )
  }
}

private class WatchedEpisodeStore(context: Context) {
  private val prefs = context.getSharedPreferences("streamdek_native_watched_episodes", Context.MODE_PRIVATE)

  private fun storageKey(ownerKey: String, showId: String): String = "watched:$ownerKey:$showId"

  fun load(ownerKey: String, showId: String): List<String> {
    val raw = prefs.getString(storageKey(ownerKey, showId), null) ?: return emptyList()
    return runCatching {
      val source = JSONArray(raw)
      buildList {
        for (index in 0 until source.length()) {
          source.optString(index).trim().ifBlank { null }?.let(::add)
        }
      }
    }.getOrDefault(emptyList())
  }

  fun save(ownerKey: String, showId: String, watchedEpisodeIds: List<String>) {
    prefs.edit().putString(storageKey(ownerKey, showId), JSONArray(watchedEpisodeIds.distinct()).toString()).apply()
  }
}

private class WatchedMovieStore(context: Context) {
  private val prefs = context.getSharedPreferences("streamdek_native_watched_movies", Context.MODE_PRIVATE)

  private fun storageKey(ownerKey: String): String = "watched_movies:$ownerKey"

  fun load(ownerKey: String): List<String> {
    val raw = prefs.getString(storageKey(ownerKey), null) ?: return emptyList()
    return runCatching {
      val source = JSONArray(raw)
      buildList {
        for (index in 0 until source.length()) {
          source.optString(index).trim().ifBlank { null }?.let(::add)
        }
      }
    }.getOrDefault(emptyList())
  }

  fun save(ownerKey: String, movieIds: List<String>) {
    prefs.edit().putString(storageKey(ownerKey), JSONArray(movieIds.distinct()).toString()).apply()
  }
}

private class WatchedTitleStore(context: Context) {
  private val prefs = context.getSharedPreferences("streamdek_native_watched_titles", Context.MODE_PRIVATE)
  private fun storageKey(ownerKey: String): String = "watched_titles:$ownerKey"

  fun load(ownerKey: String): Set<String> = runCatching {
    val source = JSONArray(prefs.getString(storageKey(ownerKey), "[]"))
    buildSet { for (index in 0 until source.length()) source.optString(index).takeIf { it.isNotBlank() }?.let(::add) }
  }.getOrDefault(emptySet())

  fun add(ownerKey: String, item: MediaItem) {
    val key = watchedTitleKey(item.type, item.id)
    prefs.edit().putString(storageKey(ownerKey), JSONArray((load(ownerKey) + key).toList()).toString()).apply()
  }
}

private data class PlaybackMemoryEntry(
  val mediaId: String,
  val mediaType: String,
  val title: String,
  val year: String? = null,
  val poster: String? = null,
  val backdrop: String? = null,
  val seasonNumber: Int? = null,
  val episodeNumber: Int? = null,
  val progressPercent: Double = 0.0,
  val durationSeconds: Int? = null,
  val stream: AddonStream? = null,
  val isLive: Boolean = false,
  val updatedAt: Long = System.currentTimeMillis(),
)

private fun resumePositionLabel(entry: PlaybackMemoryEntry): String? {
  val durationSeconds = entry.durationSeconds ?: return null
  if (durationSeconds <= 0) return null
  val positionSeconds = (durationSeconds * entry.progressPercent / 100.0).toInt()
  return "%02d:%02d".format(positionSeconds / 3600, (positionSeconds % 3600) / 60)
}

private class PlaybackResumeStore(context: Context) {
  private val prefs = context.getSharedPreferences("streamdek_native_playback_resume", Context.MODE_PRIVATE)

  private fun storageKey(ownerKey: String): String = "resume:$ownerKey"

  fun loadAll(ownerKey: String): List<PlaybackMemoryEntry> {
    val raw = prefs.getString(storageKey(ownerKey), null) ?: return emptyList()
    return runCatching {
      val source = JSONArray(raw)
      buildList {
        for (index in 0 until source.length()) {
          parsePlaybackMemoryEntry(source.optJSONObject(index) ?: continue)?.let(::add)
        }
      }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())
  }

  fun save(ownerKey: String, entry: PlaybackMemoryEntry) {
    val updated = loadAll(ownerKey)
      .filterNot { playbackMemoryKey(it.mediaId, it.mediaType, it.seasonNumber, it.episodeNumber) == playbackMemoryKey(entry.mediaId, entry.mediaType, entry.seasonNumber, entry.episodeNumber) }
      .toMutableList()
    updated.add(0, entry.copy(updatedAt = System.currentTimeMillis()))
    persist(ownerKey, updated.take(80))
  }

  fun removeTitle(ownerKey: String, mediaId: String, mediaType: String) {
    val targetType = normalizedMediaType(mediaType)
    persist(ownerKey, loadAll(ownerKey).filterNot { it.mediaId == mediaId && normalizedMediaType(it.mediaType) == targetType })
  }
  fun remove(ownerKey: String, mediaId: String, mediaType: String, seasonNumber: Int?, episodeNumber: Int?) {
    val key = playbackMemoryKey(mediaId, mediaType, seasonNumber, episodeNumber)
    persist(ownerKey, loadAll(ownerKey).filterNot { playbackMemoryKey(it.mediaId, it.mediaType, it.seasonNumber, it.episodeNumber) == key })
  }

  private fun persist(ownerKey: String, entries: List<PlaybackMemoryEntry>) {
    prefs.edit().putString(storageKey(ownerKey), JSONArray(entries.map(::playbackMemoryToJson)).toString()).commit()
  }
}

private fun playbackMemoryKey(mediaId: String, mediaType: String, seasonNumber: Int?, episodeNumber: Int?): String =
  listOf(mediaType, mediaId, seasonNumber ?: -1, episodeNumber ?: -1).joinToString(":")

private fun playbackMemoryToJson(entry: PlaybackMemoryEntry): JSONObject = JSONObject()
  .put("mediaId", entry.mediaId)
  .put("mediaType", entry.mediaType)
  .put("title", entry.title)
  .put("year", entry.year)
  .put("poster", entry.poster)
  .put("backdrop", entry.backdrop)
  .put("seasonNumber", entry.seasonNumber)
  .put("episodeNumber", entry.episodeNumber)
  .put("progressPercent", entry.progressPercent)
  .put("durationSeconds", entry.durationSeconds)
  .put("isLive", entry.isLive)
  .put("updatedAt", entry.updatedAt)
  .put("stream", entry.stream?.let(::addonStreamToJson))

private fun parsePlaybackMemoryEntry(json: JSONObject): PlaybackMemoryEntry? {
  val mediaId = json.optString("mediaId").trim()
  val mediaType = json.optString("mediaType").trim()
  val title = json.optString("title").trim()
  if (mediaId.isBlank() || mediaType.isBlank() || title.isBlank()) return null
  return PlaybackMemoryEntry(
    mediaId = mediaId,
    mediaType = mediaType,
    title = title,
    year = json.optString("year").ifBlank { null },
    poster = json.optString("poster").ifBlank { null },
    backdrop = json.optString("backdrop").ifBlank { null },
    seasonNumber = json.optInt("seasonNumber").takeIf { it > 0 },
    episodeNumber = json.optInt("episodeNumber").takeIf { it > 0 },
    progressPercent = json.optDouble("progressPercent").takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 0.0,
    durationSeconds = json.optInt("durationSeconds").takeIf { it > 0 },
    isLive = json.optBoolean("isLive", false),
    stream = json.optJSONObject("stream")?.let(::parseAddonStreamJson),
    updatedAt = json.optLong("updatedAt").takeIf { it > 0L } ?: 0L,
  )
}

private fun addonStreamToJson(stream: AddonStream): JSONObject = JSONObject()
  .put("addonId", stream.addonId)
  .put("addonName", stream.addonName)
  .put("source", stream.source)
  .put("name", stream.name)
  .put("title", stream.title)
  .put("description", stream.description)
  .put("url", stream.url)
  .put("infoHash", stream.infoHash)
  .put("fileIdx", stream.fileIdx)
  .put("filename", stream.filename)
  .put("quality", stream.quality)
  .put("size", stream.size)
  .put("cachedBy", JSONArray(stream.cachedBy))
  .put("bingeGroup", stream.bingeGroup)
  .put("requestHeaders", JSONObject(stream.requestHeaders))

private fun parseAddonStreamJson(json: JSONObject): AddonStream = AddonStream(
  addonId = json.optString("addonId"),
  addonName = json.optString("addonName"),
  name = json.optString("name").ifBlank { null },
  title = json.optString("title").ifBlank { null },
  description = json.optString("description").ifBlank { null },
  url = json.optString("url").ifBlank { null },
  infoHash = json.optString("infoHash").ifBlank { null },
  fileIdx = json.optInt("fileIdx").takeIf { it >= 0 },
  filename = json.optString("filename").ifBlank { null },
  quality = json.optString("quality").ifBlank { null },
  size = json.optString("size").ifBlank { null },
  bingeGroup = json.optString("bingeGroup").ifBlank { null },
  requestHeaders = json.optJSONObject("requestHeaders")?.toStringMap().orEmpty(),
  source = json.optString("source").ifBlank { null },
  cachedBy = json.optJSONArray("cachedBy")?.let { source ->
    buildList {
      for (index in 0 until source.length()) {
        source.optString(index).trim().ifBlank { null }?.let(::add)
      }
    }
  } ?: emptyList(),
)

private fun JSONObject.toStringMap(): Map<String, String> = buildMap {
  keys().forEach { key -> optString(key).trim().takeIf { it.isNotBlank() }?.let { put(key, it) } }
}
private fun normalizedMediaType(type: String): String = when (type.trim().lowercase()) {
  "tv", "series", "show" -> "tv"
  else -> type.trim().lowercase()
}

private fun watchedTitleKey(type: String, id: String): String = "${normalizedMediaType(type)}:$id"
private fun watchedEpisodeKey(showId: String, seasonNumber: Int, episodeNumber: Int): String =
  "episode:$showId:$seasonNumber:$episodeNumber"

internal fun completedEpisodeWatchedIds(existing: List<String>, watchedKey: String): List<String> =
  if (watchedKey in existing) existing else existing + watchedKey

private fun watchedOwnerKey(session: AuthSession?, activeProfileId: String?): String {
  val userId = session?.user?.uid
  return when {
    userId.isNullOrBlank() -> activeProfileId?.let { "guest:$it" } ?: GUEST_OWNER_KEY
    activeProfileId.isNullOrBlank() -> userId
    else -> "$userId:$activeProfileId"
  }
}
private const val GUEST_OWNER_KEY = "guest"

private fun serializeHomeCatalogRows(rows: List<HomeCatalogRow>): String = JSONArray().apply {
  rows.forEach { row ->
    put(JSONObject().put("id", row.id).put("enabled", row.enabled))
  }
}.toString()

private fun parseHomeCatalogRows(raw: String?): List<HomeCatalogRow> {
  if (raw.isNullOrBlank()) return emptyList()
  return runCatching {
    val source = JSONArray(raw)
    buildList {
      for (index in 0 until source.length()) {
        val item = source.optJSONObject(index) ?: continue
        val id = item.optString("id").trim()
        if (id.isEmpty()) continue
        add(HomeCatalogRow(id = id, title = id, subtitle = "", builtin = false, enabled = item.optBoolean("enabled", true)))
      }
    }
  }.getOrDefault(emptyList())
}

private fun isBuiltinHomeCatalog(id: String): Boolean = !id.startsWith("addon:")

private fun builtinHomeCatalogCandidates(): List<HomeCatalogRow> = listOf(
  HomeCatalogRow("new_movies", "New Movies", "Provided by StreamDek", builtin = true),
  HomeCatalogRow("new_series", "New Series", "Provided by StreamDek", builtin = true),
  HomeCatalogRow("streaming_networks", "Streaming Networks", "Provided by StreamDek", builtin = true),
  HomeCatalogRow("trending_movies", "Trending Movies", "Provided by StreamDek", builtin = true),
  HomeCatalogRow("trending_series", "Trending Series", "Provided by StreamDek", builtin = true),
)

private fun addonHomeCatalogTitle(addonName: String, catalogName: String, type: String, needsDifferentiator: Boolean): String {
  val cleaned = cleanAddonCatalogLabel(catalogName, addonName)
  val base = cleaned.ifBlank { cleanAddonCatalogLabel(addonName).ifBlank { addonName } }
  return if (!needsDifferentiator) base else "$base ${
    when (type) {
      "movie" -> "Movies"
      "series", "show" -> "Series"
      "tv", "channel", "live", "iptv" -> "Live"
      "sport", "sports", "events" -> "Sports"
      else -> "Series"
    }
  }"
}

private fun addonHomeCatalogCandidates(addons: List<InstalledAddon>): List<HomeCatalogRow> {
  val enabledAddons = addons.filter { it.enabled }.sortedBy { it.position }
  val duplicateCatalogNames = enabledAddons
    .flatMap { addon -> addon.manifest.catalogs.map { catalog -> catalog.name.trim().lowercase() to catalog.type.trim().lowercase() } }
    .filter { (_, type) -> type in setOf("movie", "series", "tv", "sport", "sports", "channel", "live", "other") }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, values) -> values.toSet().size > 1 }
  return enabledAddons.flatMap { addon ->
    addon.manifest.catalogs.mapIndexedNotNull { index, catalog ->
      val rawType = catalog.type.trim().lowercase()
      val mappedType = when (rawType) {
        "movie" -> "movie"
        "series", "tv", "sport", "sports", "channel", "live", "other" -> "tv"
        else -> null
      } ?: return@mapIndexedNotNull null
      val title = addonHomeCatalogTitle(addon.manifest.name, catalog.name.ifBlank { catalog.id }, rawType, duplicateCatalogNames[catalog.name.trim().lowercase()] == true)
      HomeCatalogRow(
        id = "addon:${addon.id}:$rawType:${catalog.id}:$index",
        title = title,
        subtitle = "From ${addon.manifest.name}",
        builtin = false,
      )
    }
  }
}

private fun isLiveHomeCatalogRowId(id: String): Boolean {
  if (!id.startsWith("addon:")) return false
  val rawType = id.split(":").getOrNull(2)?.lowercase().orEmpty()
  return rawType in setOf("tv", "channel", "live", "iptv", "sport", "sports", "events")
}

private fun mergeHomeCatalogRows(existing: List<HomeCatalogRow>, addons: List<InstalledAddon>): List<HomeCatalogRow> {
  val candidates = (builtinHomeCatalogCandidates() + addonHomeCatalogCandidates(addons)).associateBy { it.id }
  val merged = mutableListOf<HomeCatalogRow>()
  existing.forEach { persisted ->
    val candidate = candidates[persisted.id] ?: return@forEach
    merged.add(candidate.copy(enabled = persisted.enabled))
  }
  candidates.values.forEach { candidate ->
    if (merged.none { it.id == candidate.id }) {
      if (isLiveHomeCatalogRowId(candidate.id)) {
        // New live TV rows default to sitting just below Streaming Networks.
        // Users can still re-arrange them from the Home rows settings page —
        // persisted arrangements above always win.
        var insertAt = merged.indexOfFirst { it.id == "streaming_networks" } + 1
        if (insertAt <= 0) {
          insertAt = merged.size
        } else {
          while (insertAt < merged.size && isLiveHomeCatalogRowId(merged[insertAt].id)) insertAt++
        }
        merged.add(insertAt, candidate)
      } else {
        merged.add(candidate)
      }
    }
  }
  return if (merged.isEmpty()) candidates.values.toList() else merged
}

private fun applyHomeCatalogLayout(sections: List<MediaSection>, rows: List<HomeCatalogRow>, defaultBuiltinsEnabled: Boolean): List<MediaSection> {
  val sectionMap = sections.associateBy { it.id }
  return rows.mapNotNull { row ->
    if (!row.enabled) return@mapNotNull null
    if (row.builtin && !defaultBuiltinsEnabled) return@mapNotNull null
    sectionMap[row.id]
  }
}

private fun normalizeFusionBadgeSource(json: JSONObject, url: String): FusionBadgeSource {
  val payloadGroups = json.optJSONArray("groups") ?: JSONArray()
  val payloadFilters = json.optJSONArray("filters") ?: json.optJSONArray("__array") ?: json.optJSONArray("badges") ?: JSONArray()
  val groups = mutableListOf<FusionBadgeGroup>()
  val knownGroupIds = linkedSetOf<String>()
  for (index in 0 until payloadGroups.length()) {
    val group = payloadGroups.optJSONObject(index) ?: continue
    val id = group.optString("id").ifBlank { "group-$index" }
    val name = group.optString("name").ifBlank { group.optString("label").ifBlank { "Group ${index + 1}" } }
    if (id.isNotBlank() && name.isNotBlank()) {
      groups += FusionBadgeGroup(id, name)
      knownGroupIds += id
    }
  }
  val filters = buildList {
    for (index in 0 until payloadFilters.length()) {
      val filter = payloadFilters.optJSONObject(index) ?: continue
      val groupId = filter.optString("groupId").ifBlank { filter.optString("group").ifBlank { filter.optString("category") } }
      val imageUrl = filter.optString("imageURL").ifBlank { filter.optString("imageUrl").ifBlank { filter.optString("image").ifBlank { filter.optString("url") } } }
      val pattern = filter.optString("pattern").ifBlank { filter.optString("regex") }
      val name = filter.optString("name").ifBlank { filter.optString("label").ifBlank { "Badge ${index + 1}" } }
      if (groupId.isBlank() || imageUrl.isBlank() || pattern.isBlank() || name.isBlank()) continue
      if (knownGroupIds.add(groupId)) groups += FusionBadgeGroup(groupId, groupId)
      add(
        FusionBadgeFilter(
          id = filter.optString("id").ifBlank { "$groupId-$index" },
          groupId = groupId,
          name = name,
          pattern = pattern,
          imageUrl = imageUrl,
          isEnabled = if (filter.has("isEnabled")) filter.optBoolean("isEnabled", true) else true,
        )
      )
    }
  }
  if (filters.isEmpty()) error("This URL does not contain any Fusion badge filters")
  return FusionBadgeSource(url = url, groups = groups, filters = filters)
}

private fun fusionStreamSearchText(stream: AddonStream): String = listOfNotNull(
  stream.name,
  stream.title,
  stream.description,
  stream.filename,
  stream.quality,
).joinToString(" ").take(700)

private val fusionRegexCache = object : LinkedHashMap<String, Regex?>(128, 0.75f, true) {
  override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex?>): Boolean = size > 128
}

@Synchronized
private fun fusionRegex(pattern: String): Regex? {
  if (fusionRegexCache.containsKey(pattern)) return fusionRegexCache[pattern]
  val compiled = runCatching {
    if (pattern.startsWith("(?i)")) Regex(pattern.drop(4), RegexOption.IGNORE_CASE) else Regex(pattern, RegexOption.IGNORE_CASE)
  }.getOrNull()
  fusionRegexCache[pattern] = compiled
  return compiled
}

private fun matchFusionBadgeFilters(stream: AddonStream, sources: List<FusionBadgeSource>): List<FusionBadgeFilter> {
  val text = fusionStreamSearchText(stream)
  if (text.isBlank() || sources.isEmpty()) return emptyList()
  val seen = linkedSetOf<String>()
  return buildList {
    sources.forEach { source ->
      source.filters.forEach { filter ->
        if (!filter.isEnabled) return@forEach
        val key = filter.imageUrl.ifBlank { "${filter.groupId}:${filter.id}" }
        if (key in seen) return@forEach
        val regex = fusionRegex(filter.pattern) ?: return@forEach
        val matched = runCatching { regex.containsMatchIn(text) }.getOrDefault(false)
        if (!matched) return@forEach
        seen += key
        add(filter)
      }
    }
  }
}

private fun activeFusionSources(uiState: AppUiState): List<FusionBadgeSource> {
  val active = uiState.activeFusionBadgeUrl?.takeIf { it in uiState.fusionBadgeUrls }
  val urls = if (active != null) listOf(active) else uiState.fusionBadgeUrls
  return urls.mapNotNull { uiState.fusionBadgeSources[it]?.source }
}

private fun fusionBadgeSourceSummary(uiState: AppUiState): String {
  val loaded = uiState.fusionBadgeUrls.mapNotNull { uiState.fusionBadgeSources[it]?.source }
  val active = uiState.activeFusionBadgeUrl ?: uiState.fusionBadgeUrls.firstOrNull().orEmpty()
  val activeSource = uiState.fusionBadgeSources[active]?.source
  val count = activeSource?.filters?.count { it.isEnabled } ?: loaded.sumOf { it.filters.count { filter -> filter.isEnabled } }
  val groups = activeSource?.filters?.map { it.groupId }?.distinct()?.size ?: loaded.flatMap { it.filters.map { filter -> filter.groupId } }.distinct().size
  return if (count > 0) "$count badge styles ready${if (groups > 0) " in $groups groups" else ""}" else "No badge styles selected"
}

private data class LiveChannelSwitchSnapshot(
  val detail: MediaDetail?,
  val detailIsLive: Boolean,
  val fallbackItem: MediaItem?,
  val selectedEpisode: EpisodeItem?,
  val availableStreams: List<AddonStream>,
  val sourceAddonId: String?,
  val sourceCatalogType: String?,
  val directStream: AddonStream?,
  val playerSession: PlayerSession?,
)

private class NativeAppViewModel(application: Application) : AndroidViewModel(application) {
  private val sessionStore = SessionStore(application.applicationContext)
  private val profileSelectionStore = ProfileSelectionStore(application.applicationContext)
  private val guestProfileStore = GuestProfileStore(application.applicationContext)
  private val watchlistStore = WatchlistStore(application.applicationContext)
  private val favouriteChannelStore = FavouriteChannelStore(application.applicationContext)
  private val authEntryStore = AuthEntryStore(application.applicationContext)
  private val appSettingsStore = AppSettingsStore(application.applicationContext)
  private val playbackResumeStore = PlaybackResumeStore(application.applicationContext)
  private val watchedEpisodeStore = WatchedEpisodeStore(application.applicationContext)
  private val watchedMovieStore = WatchedMovieStore(application.applicationContext)
  private val watchedTitleStore = WatchedTitleStore(application.applicationContext)
  private var torrentStatusRefreshJob: Job? = null
  private var pluginSyncJob: Job? = null
  private var pluginRefreshJob: Job? = null
  private val apiClient = StreamDekApiClient(application.applicationContext)
  private var searchRequestGeneration: Long = 0L
  private var streamRequestGeneration: Long = 0L
  private var playbackRequestGeneration: Long = 0L
  private var liveChannelCatalogGeneration: Long = 0L
  private var m3uLoadGeneration: Long = 0L
  private var liveChannelSwitchSnapshot: LiveChannelSwitchSnapshot? = null
  private var pendingStreamLoad: PendingStreamLoad? = null
  private var pendingDirectContinueEntry: PlaybackMemoryEntry? = null
  private var pendingDirectContinueFallback: (() -> Unit)? = null
  private val pendingHomeHeroLogoKeys = mutableSetOf<String>()
  private val pendingAddonRatingKeys = mutableSetOf<String>()
  // Items whose fetchDetails() call already came back empty/failed once (e.g. a bridge item
  // with a placeholder title and an id TMDB can never resolve). Without this, resolveAddonCatalogRatings
  // re-fetches these same doomed items on every retrigger of its LaunchedEffect (which itself
  // fires again each time addonCatalogRatings.size changes from a *different* item succeeding),
  // producing a burst of repeated identical failing requests (e.g. /tmdb/search?query=_) instead
  // of one failed attempt per item.
  private val heroLogoFailedKeys = mutableSetOf<String>()
  private val addonRatingFailedKeys = mutableSetOf<String>()
  private var detailSourceAddonId: String? = null
  private var detailSourceCatalogType: String? = null
  private var detailDirectStream: AddonStream? = null
  // For a local add-on item, detail.id needs to stay the TMDB id so season/rating/Trakt
  // follow-up calls keep working once TMDB enrichment succeeds -- but the add-on's own
  // /stream endpoint needs its OWN canonical id (from resolveLocalAddonStreamId), which is
  // often not the same value at all. Kept as a separate field rather than overloading detail.id
  // for this one case. Null whenever the open detail isn't a local add-on item.
  private var detailLocalStreamId: String? = null
  private var detailLocalEpisodes: List<EpisodeItem> = emptyList()
  private var routeAfterProfileRefresh = false

  private val restoredSession = sessionStore.load()
  private val restoredGuestProfiles = if (restoredSession == null) guestProfileStore.load() else emptyList()
  private val restoredGuestProfile = restoredGuestProfiles.firstOrNull { it.id == profileSelectionStore.load(GUEST_OWNER_KEY) }
    ?: restoredGuestProfiles.firstOrNull { it.isDefault }
    ?: restoredGuestProfiles.firstOrNull()

  var uiState by mutableStateOf(
    appSettingsStore.applyTo(
      AppUiState(
        booting = false,
        rememberedEmail = authEntryStore.loadEmail(),
        session = restoredSession,
        profilesLoading = restoredSession != null,
        profiles = restoredGuestProfiles,
        activeProfileId = restoredGuestProfile?.id,
        showProfilePicker = restoredSession != null || restoredGuestProfiles.size > 1,
        mergedWatchlist = if (restoredSession == null) {
          watchlistStore.load(restoredGuestProfile?.id?.let { "guest:$it" } ?: GUEST_OWNER_KEY)
        } else {
          emptyList()
        },
        favouriteChannels = if (restoredSession == null) {
          favouriteChannelStore.load(restoredGuestProfile?.id?.let { "guest:$it" } ?: GUEST_OWNER_KEY)
        } else {
          emptyList()
        },
      ),
    ),
  )
    private set

  init {
    DisplayNameOverrides.initialize(application.applicationContext)
    LocalAddonManager.initialize(application.applicationContext)
    M3uPlaylistManager.initialize(application.applicationContext)
    StreamDekDownloads.initialize(application.applicationContext)
    val initialOwnerKey = activeOwnerKey() ?: GUEST_OWNER_KEY
    LocalAddonManager.selectProfileStorage(initialOwnerKey)
    M3uPlaylistManager.selectProfileStorage(initialOwnerKey)
    appSettingsStore.selectProfileStorage(initialOwnerKey)
    uiState = appSettingsStore.applyTo(uiState)
    StreamDekPlugins.initialize(application.applicationContext)
    StreamDekPlugins.manager.onStateChanged = { raw -> syncActiveProfilePlugins(raw) }
    StreamDekPlugins.manager.selectProfileStorage(activeOwnerKey() ?: GUEST_OWNER_KEY)
    syncTorrentServer()
    restore()
  }

  fun clearTransientMessage() { uiState = uiState.copy(errorMessage = null, infoMessage = null) }

  fun rememberAuthEmail(email: String) {
    authEntryStore.saveEmail(email)
    uiState = uiState.copy(rememberedEmail = email)
  }

  fun signOut() {
    sessionStore.clear()
    uiState = appSettingsStore.applyTo(AppUiState(booting = false, rememberedEmail = authEntryStore.loadEmail(), mergedWatchlist = watchlistStore.load(GUEST_OWNER_KEY), favouriteChannels = favouriteChannelStore.load(GUEST_OWNER_KEY)))
    bootstrapAfterAuth(forceHome = true)
  }

  fun signIn(email: String, password: String, rememberSession: Boolean) {
    rememberAuthEmail(email)
    submitAuth { apiClient.login(email, password) }
  }

  fun signUp(email: String, password: String, rememberSession: Boolean) {
    rememberAuthEmail(email)
    // Capture the guest identity being left behind *before* auth changes activeOwnerKey().
    val guestKey = if (uiState.session == null) activeOwnerKey() else null
    val mergeSourceKey = guestKey?.takeIf(::hasGuestDataFor)
    submitAuth(guestDataOwnerKey = mergeSourceKey) { apiClient.register(email, password) }
  }

  private fun hasGuestDataFor(ownerKey: String): Boolean =
    watchlistStore.load(ownerKey).isNotEmpty() ||
      favouriteChannelStore.load(ownerKey).isNotEmpty() ||
      playbackResumeStore.loadAll(ownerKey).isNotEmpty() ||
      LocalAddonManager.list().isNotEmpty()

  fun requestPasswordReset(email: String) {
    launchWork(
      onStart = { uiState = uiState.copy(errorMessage = null, infoMessage = null) },
      block = { apiClient.requestPasswordReset(email) },
      onSuccess = { code -> uiState = uiState.copy(infoMessage = if (code.isNullOrBlank()) "Reset request sent." else "Reset code: $code") },
    )
  }

  fun confirmPasswordReset(email: String, token: String, newPassword: String) {
    launchWork(
      onStart = { uiState = uiState.copy(errorMessage = null, infoMessage = null) },
      block = { apiClient.confirmPasswordReset(email, token, newPassword) },
      onSuccess = { uiState = uiState.copy(infoMessage = "Password updated. You can sign in now.") },
    )
  }

  fun dismissPlayer(progressPercent: Double? = null) {
    liveChannelSwitchSnapshot = null
    progressPercent?.let {
      saveCurrentPlaybackSnapshot(it)
      scrobbleCurrentPlayer("pause", it)
    }
    uiState = uiState.copy(playerSession = null, playerLaunching = false, playerLaunchingLabel = null, streamLoading = false, nextEpisodeLoading = false, nextEpisodeLoadingLabel = null)
    stopOnDemandTorrentServer()
  }

  fun cancelPlayerLaunch() {
    playbackRequestGeneration += 1
    uiState = uiState.copy(playerLaunching = false, playerLaunchingLabel = null, streamLoading = false, returnToDetailAfterPlayer = false, playerReturnEpisodeId = null)
  }

  fun setBrowseRow(row: HomeRow?) {
    uiState = if (row == null) {
      uiState.copy(browseRow = null, browseLoadedItems = emptyList(), browseReturnItemId = null)
    } else {
      uiState.copy(browseRow = row, browseLoadedItems = row.items, browseReturnItemId = null)
    }
  }

  fun rememberBrowseReturnItem(item: MediaItem) {
    uiState = uiState.copy(browseReturnItemId = item.id)
  }

  fun playLiveChannel(item: MediaItem) {
    if (!item.isLiveCatalogItem() || uiState.liveChannelSwitching) return
    liveChannelSwitchSnapshot = LiveChannelSwitchSnapshot(
      detail = uiState.detail,
      detailIsLive = uiState.detailIsLive,
      fallbackItem = uiState.detailFallbackItem,
      selectedEpisode = uiState.selectedEpisode,
      availableStreams = uiState.availableStreams,
      sourceAddonId = detailSourceAddonId,
      sourceCatalogType = detailSourceCatalogType,
      directStream = detailDirectStream,
      playerSession = uiState.playerSession,
    )
    uiState = uiState.copy(liveChannelSwitching = true, liveChannelSwitchingLabel = item.title, errorMessage = null)
    loadDetail(item.type, item.id, item)
    playBestStream()
  }

  fun hasLiveChannelFallback(): Boolean = liveChannelSwitchSnapshot?.playerSession != null

  fun cancelLiveChannelSwitch() {
    val snapshot = liveChannelSwitchSnapshot ?: return
    playbackRequestGeneration += 1
    streamRequestGeneration += 1
    detailSourceAddonId = snapshot.sourceAddonId
    detailSourceCatalogType = snapshot.sourceCatalogType
    detailDirectStream = snapshot.directStream
    uiState = uiState.copy(
      detail = snapshot.detail, detailIsLive = snapshot.detailIsLive, detailFallbackItem = snapshot.fallbackItem,
      selectedEpisode = snapshot.selectedEpisode, availableStreams = snapshot.availableStreams, playerSession = snapshot.playerSession,
      streamLoading = false, playerLaunching = false, playerLaunchingLabel = null,
      liveChannelSwitching = false, liveChannelSwitchingLabel = null, errorMessage = null,
    )
    liveChannelSwitchSnapshot = null
  }

  private fun loadPlayerLiveChannels(anchor: MediaItem) {
    val addonId = anchor.sourceAddonId ?: return
    val catalogType = anchor.sourceCatalogType ?: return
    val catalogId = anchor.sourceCatalogId ?: return
    val matchesCatalog: (MediaItem) -> Boolean = {
      // Match cheap source fields first. Calling isLiveCatalogItem() for every entry compiles
      // regexes and was especially costly when a large enabled M3U playlist leaked into a
      // different add-on's card click.
      it.sourceAddonId == addonId && it.sourceCatalogType == catalogType &&
        it.sourceCatalogId == catalogId && it.sourceCatalogGenre == anchor.sourceCatalogGenre
    }
    val generation = ++liveChannelCatalogGeneration
    val sourceLists = if (M3uPlaylistManager.isM3uSourceId(addonId)) {
      listOf(uiState.m3uChannels)
    } else {
      // Keep M3U channels out of other add-ons' player catalogs. Any previously selected M3U
      // catalog may still be present in playerLiveChannels, so source-field filtering remains
      // mandatory even though the explicit m3uChannels list is not included here.
      listOf(
        uiState.playerLiveChannels,
        uiState.browseLoadedItems,
        *uiState.allHomeSections.map { it.items }.toTypedArray(),
        *uiState.homeSections.map { it.items }.toTypedArray(),
      )
    }
    // A live-card tap must only do constant work on the main thread. Enabled IPTV playlists can
    // contain tens of thousands of channels; concatenating, filtering, regex-checking and
    // de-duplicating those lists synchronously caused the observed five-second input ANR.
    uiState = uiState.copy(playerLiveChannels = listOf(anchor), playerLiveChannelsLoading = true)
    viewModelScope.launch {
      var loaded = withContext(Dispatchers.Default) {
        sequence {
          sourceLists.forEach { list -> yieldAll(list) }
          yield(anchor)
        }.filter(matchesCatalog).distinctBy { "${it.type}-${it.id}" }.toList()
          .ifEmpty { listOf(anchor) }
      }
      if (generation != liveChannelCatalogGeneration) return@launch
      uiState = uiState.copy(playerLiveChannels = loaded)
      if (M3uPlaylistManager.isM3uSourceId(addonId)) {
        uiState = uiState.copy(playerLiveChannelsLoading = false)
        return@launch
      }
      val addon = uiState.addons.firstOrNull { it.id == addonId && it.enabled }
      if (addon == null) {
        uiState = uiState.copy(playerLiveChannelsLoading = false)
        return@launch
      }
      var pages = 0
      while (pages++ < 250 && loaded.size < 5_000) {
        val page = apiClient.fetchMoreCatalogItems(
          uiState.session, addon, catalogType, catalogId, anchor.sourceCatalogName ?: catalogType,
          anchor.sourceCatalogGenre, loaded.size, uiState.activeProfileId,
        ).getOrElse { emptyList() }.filter(matchesCatalog)
        if (generation != liveChannelCatalogGeneration) return@launch
        val merged = withContext(Dispatchers.Default) {
          (loaded.asSequence() + page.asSequence()).distinctBy { "${it.type}-${it.id}" }.toList()
        }
        if (merged.size == loaded.size) break
        loaded = merged
        uiState = uiState.copy(playerLiveChannels = loaded)
      }
      if (generation == liveChannelCatalogGeneration) {
        uiState = uiState.copy(playerLiveChannels = loaded, playerLiveChannelsLoading = false)
      }
    }
  }

  fun setNetworkBrowseItem(item: MediaItem?) { uiState = uiState.copy(networkBrowseItem = item) }

  fun clearBrowseNavigation() { uiState = uiState.copy(browseRow = null, networkBrowseItem = null) }

  fun clearPlayerReturnTarget() {
    uiState = uiState.copy(returnToDetailAfterPlayer = false, playerReturnEpisodeId = null)
  }

  fun onPlayerPlaybackEnded() {
    val completedPlayer = uiState.playerSession
    if (completedPlayer?.mediaType == "tv" && completedPlayer.seasonNumber != null && completedPlayer.episodeNumber != null) {
      val ownerKey = watchedOwnerKey(uiState.session, uiState.activeProfileId)
      val watchedKey = watchedEpisodeKey(completedPlayer.mediaId, completedPlayer.seasonNumber, completedPlayer.episodeNumber)
      val existing = watchedEpisodeStore.load(ownerKey, completedPlayer.mediaId)
      if (watchedKey !in existing) {
        watchedEpisodeStore.save(ownerKey, completedPlayer.mediaId, completedEpisodeWatchedIds(existing, watchedKey))
        uiState = uiState.copy(watchedEpisodeRevision = uiState.watchedEpisodeRevision + 1)
      }
    }
    val player = uiState.playerSession ?: return
    saveCurrentPlaybackSnapshot(100.0)
    val detail = uiState.detail
    val currentEpisode = uiState.selectedEpisode
    if (!player.autoPlayNextEpisode || detail == null || detail.type != "tv" || currentEpisode == null) {
      dismissPlayer(100.0)
      return
    }
    val loadedNextEpisode = uiState.selectedSeasonEpisodes
      .filter { it.seasonNumber == currentEpisode.seasonNumber && it.episodeNumber > currentEpisode.episodeNumber }
      .minByOrNull { it.episodeNumber }
    val nextSeasonNumber = detail.seasons
      .map { it.seasonNumber }
      .filter { it > currentEpisode.seasonNumber }
      .minOrNull()
    if (loadedNextEpisode == null && nextSeasonNumber == null) {
      dismissPlayer(100.0)
      return
    }
    val currentStream = player.currentStream
    launchWork(
      onStart = { uiState = uiState.copy(streamLoading = true, nextEpisodeLoading = true, nextEpisodeLoadingLabel = loadedNextEpisode?.let { "S${it.seasonNumber} • E${it.episodeNumber}" } ?: nextSeasonNumber?.let { "Season $it" }, errorMessage = null) },
      block = {
        val nextEpisode = loadedNextEpisode ?: apiClient.fetchSeason(detail.id, nextSeasonNumber!!)
          .getOrElse { return@launchWork Result.failure(it) }
          .minByOrNull { it.episodeNumber }
          ?: return@launchWork Result.failure(IllegalStateException("The next season has no playable episodes."))
        val ids = streamLookupIds(detail).distinct()
          .map { "$it:${nextEpisode.seasonNumber}:${nextEpisode.episodeNumber}" }
        fetchStreamsForPlayback("series", ids).map { streams -> nextEpisode to streams }
      },
      onSuccess = { (nextEpisode, streams) ->
        val ranked = rankedStreams(mediaStreamsOnly(streams, detail), uiState.debridAccounts.isNotEmpty(), uiState.preferredQuality, uiState.maxFileSizeGb)
        val sameBingeGroup = currentStream?.bingeGroup?.takeIf { it.isNotBlank() }?.let { group -> ranked.firstOrNull { it.bingeGroup == group } }
        val sameAddonAndQuality = currentStream?.let { current -> ranked.firstOrNull { it.addonId == current.addonId && it.quality == current.quality } }
        val selected = if (player.preferBingeGroup) sameBingeGroup ?: sameAddonAndQuality ?: ranked.firstOrNull() else ranked.firstOrNull()
        val selectionStrategy = when {
          selected == null -> "none"
          sameBingeGroup != null && selected == sameBingeGroup -> "binge-group"
          sameAddonAndQuality != null && selected == sameAddonAndQuality -> "addon-quality"
          else -> "ranked-fallback"
        }
        android.util.Log.i("StreamDekPlayback", "next-source strategy=$selectionStrategy currentGroup=${currentStream?.bingeGroup ?: "none"} selectedGroup=${selected?.bingeGroup ?: "none"} addon=${selected?.addonName ?: "none"} quality=${selected?.quality ?: "unknown"}")
        uiState = uiState.copy(selectedEpisode = nextEpisode, availableStreams = ranked)
        if (selected == null) {
          dismissPlayer(100.0)
          uiState = uiState.copy(errorMessage = "No playable source was found for the next episode.")
        } else {
          playStream(selected, nextEpisode, 0.0)
        }
      },
      onFailure = { message -> dismissPlayer(100.0); uiState = uiState.copy(streamLoading = false, nextEpisodeLoading = false, nextEpisodeLoadingLabel = null, errorMessage = message) },
    )
  }
  fun playAdjacentEpisode(direction: Int) {
    val detail = uiState.detail ?: return
    val current = uiState.selectedEpisode ?: return
    if (detail.type != "tv" || direction == 0) return
    val sameSeason = uiState.selectedSeasonEpisodes
      .filter { it.seasonNumber == current.seasonNumber }
      .sortedBy { it.episodeNumber }
    val sameSeasonTarget = if (direction > 0) sameSeason.firstOrNull { it.episodeNumber > current.episodeNumber } else sameSeason.lastOrNull { it.episodeNumber < current.episodeNumber }
    val adjacentSeason = detail.seasons.map { it.seasonNumber }
      .filter { if (direction > 0) it > current.seasonNumber else it < current.seasonNumber }
      .let { values -> if (direction > 0) values.minOrNull() else values.maxOrNull() }
    if (sameSeasonTarget == null && adjacentSeason == null) return
    val currentStream = uiState.playerSession?.currentStream
    launchWork(
      onStart = {
        val label = sameSeasonTarget?.let { "S${it.seasonNumber} • E${it.episodeNumber}" } ?: adjacentSeason?.let { "Season $it" }
        uiState = uiState.copy(streamLoading = true, nextEpisodeLoading = true, nextEpisodeLoadingLabel = label, errorMessage = null)
      },
      block = {
        val target = sameSeasonTarget ?: apiClient.fetchSeason(detail.id, adjacentSeason!!)
          .getOrElse { return@launchWork Result.failure(it) }
          .let { episodes -> if (direction > 0) episodes.minByOrNull { it.episodeNumber } else episodes.maxByOrNull { it.episodeNumber } }
          ?: return@launchWork Result.failure(IllegalStateException("No adjacent episode was found."))
        val ids = streamLookupIds(detail).distinct()
          .map { "$it:${target.seasonNumber}:${target.episodeNumber}" }
        fetchStreamsForPlayback("series", ids).map { streams -> target to streams }
      },
      onSuccess = { (target, streams) ->
        val ranked = rankedStreams(mediaStreamsOnly(streams, detail), uiState.debridAccounts.isNotEmpty(), uiState.preferredQuality, uiState.maxFileSizeGb)
        val bingeMatch = currentStream?.bingeGroup?.takeIf { it.isNotBlank() }?.let { group -> ranked.firstOrNull { it.bingeGroup == group } }
        val addonMatch = currentStream?.let { source -> ranked.firstOrNull { it.addonId == source.addonId && it.quality == source.quality } }
        val selected = if (uiState.preferBingeGroup) bingeMatch ?: addonMatch ?: ranked.firstOrNull() else ranked.firstOrNull()
        uiState = uiState.copy(selectedEpisode = target, availableStreams = ranked)
        if (selected == null) {
          uiState = uiState.copy(streamLoading = false, nextEpisodeLoading = false, nextEpisodeLoadingLabel = null, errorMessage = "No playable source was found for S${target.seasonNumber} E${target.episodeNumber}.")
        } else {
          playStream(selected, target, 0.0)
        }
      },
      onFailure = { message -> uiState = uiState.copy(streamLoading = false, nextEpisodeLoading = false, nextEpisodeLoadingLabel = null, errorMessage = message) },
    )
  }
  fun scrobblePlayer(action: String, progressPercent: Double) { scrobbleCurrentPlayer(action, progressPercent) }

  fun savePlayerProgressCheckpoint(progressPercent: Double) {
    if (uiState.playerSession == null) return
    saveCurrentPlaybackSnapshot(progressPercent)
  }

  fun loadHome(force: Boolean = false) {
    if (uiState.homeSections.isNotEmpty() && !force) return
    launchWork(
      onStart = { uiState = uiState.copy(homeLoading = true, errorMessage = null) },
      block = { apiClient.fetchHomeSections(uiState.session, uiState.addons, uiState.activeProfileId) },
      onSuccess = { sections ->
        val mergedRows = mergeHomeCatalogRows(uiState.homeCatalogRows, uiState.addons)
        uiState = uiState.copy(
          homeLoading = false,
          allHomeSections = sections,
          homeCatalogRows = mergedRows,
          homeSections = applyHomeCatalogLayout(sections, mergedRows, uiState.defaultAppCatalogsEnabled),
        )
        appSettingsStore.saveHomeCatalogRows(mergedRows)
      },
      onFailure = { message -> uiState = uiState.copy(homeLoading = false, errorMessage = message) },
    )
  }

  fun resolveHomeHeroTitleLogos(items: List<MediaItem>) {
    val requests = items
      .asSequence()
      .filter { it.titleLogo.isNullOrBlank() }
      .distinctBy(::homeHeroMediaKey)
      .filter { homeHeroMediaKey(it) !in uiState.homeHeroTitleLogos }
      .filter { homeHeroMediaKey(it) !in heroLogoFailedKeys }
      .filter { pendingHomeHeroLogoKeys.add(homeHeroMediaKey(it)) }
      .take(12)
      .toList()
    if (requests.isEmpty()) return
    viewModelScope.launch {
      try {
        val resolved = supervisorScope {
          requests.map { item ->
            async {
              val logo = apiClient.fetchDetails(item.type, item.id, item.title, item.year).getOrNull()?.titleLogo?.takeIf { it.isNotBlank() }
              homeHeroMediaKey(item) to logo
            }
          }.awaitAll().toMap()
        }
        val succeeded = resolved.filterValues { !it.isNullOrBlank() }.mapValues { it.value!! }
        if (succeeded.isNotEmpty()) uiState = uiState.copy(homeHeroTitleLogos = uiState.homeHeroTitleLogos + succeeded)
        resolved.keys.filter { it !in succeeded }.forEach { heroLogoFailedKeys.add(it) }
      } finally {
        requests.forEach { pendingHomeHeroLogoKeys.remove(homeHeroMediaKey(it)) }
      }
    }
  }

  // Third-party addon catalogs rarely carry ratings, so resolve TMDB scores for
  // their items on demand (same batched/cached approach as hero title logos).
  fun resolveAddonCatalogRatings(items: List<MediaItem>) {
    if (!uiState.showAddonTmdbRatings) return
    val requests = items
      .asSequence()
      .filter { it.sourceAddonId != null && it.rating == null && !it.isLiveCatalogItem() }
      .distinctBy(::homeHeroMediaKey)
      .filter { homeHeroMediaKey(it) !in uiState.addonCatalogRatings }
      .filter { homeHeroMediaKey(it) !in addonRatingFailedKeys }
      .filter { pendingAddonRatingKeys.add(homeHeroMediaKey(it)) }
      .take(12)
      .toList()
    if (requests.isEmpty()) return
    viewModelScope.launch {
      try {
        val resolved = supervisorScope {
          requests.map { item ->
            async {
              val rating = apiClient.fetchDetails(item.type, item.id, item.title, item.year).getOrNull()
                ?.let { it.tmdbRating ?: it.rating }
                ?.takeIf { it > 0.0 }
              homeHeroMediaKey(item) to rating
            }
          }.awaitAll().toMap()
        }
        val succeeded = resolved.filterValues { it != null }.mapValues { it.value!! }
        if (succeeded.isNotEmpty()) uiState = uiState.copy(addonCatalogRatings = uiState.addonCatalogRatings + succeeded)
        resolved.keys.filter { it !in succeeded }.forEach { addonRatingFailedKeys.add(it) }
      } finally {
        requests.forEach { pendingAddonRatingKeys.remove(homeHeroMediaKey(it)) }
      }
    }
  }

  fun search(query: String) {
    val normalized = query.trim()
    if (normalized.length < 2) return
    val generation = ++searchRequestGeneration
    uiState = uiState.copy(searchLoading = true, errorMessage = null)
    viewModelScope.launch {
      apiClient.search(normalized)
        .onSuccess { page ->
          if (generation == searchRequestGeneration) {
            uiState = uiState.copy(searchLoading = false, searchResults = page.items, searchResultQuery = normalized)
          }
        }
        .onFailure { error ->
          if (generation == searchRequestGeneration) {
            uiState = uiState.copy(searchLoading = false, searchResultQuery = normalized, errorMessage = error.message ?: "Search failed.")
          }
        }
    }
  }

  /**
   * Pages in more items for a "View All" row. For add-on catalog rows, [anchorItem] carries
   * sourceAddonId/sourceCatalogType/sourceCatalogId/sourceCatalogGenre identifying which add-on
   * catalog to re-query, and [skip] is the number of items already shown for it (Stremio-style
   * item offset). Built-in TMDB rows (New Movies, Trending, ...) don't have an add-on source —
   * those items carry no sourceAddonId — so they're identified by [rowId] instead and paged by
   * TMDB page number (TMDB's default page size is 20, which is exactly why "View All" used to
   * cap at 20 items with no way to load more).
   * Returns an empty list (never throws) when the row has no re-queryable source at all (e.g.
   * Continue Watching, Watchlist, Trakt recommendations) or the add-on is no longer
   * installed/enabled.
   */
  suspend fun loadMoreRowItems(rowId: String, anchorItem: MediaItem?, skip: Int): List<MediaItem> {
    val addonId = anchorItem?.sourceAddonId
    val fetched = if (addonId != null) {
      val addon = uiState.addons.firstOrNull { it.id == addonId && it.enabled }
      if (addon == null) {
        android.util.Log.d("StreamDekPaging", "loadMoreRowItems row=$rowId: addon $addonId not found/enabled")
        emptyList()
      } else {
        val catalogType = anchorItem.sourceCatalogType
        val catalogId = anchorItem.sourceCatalogId
        if (catalogType == null || catalogId == null) {
          android.util.Log.d("StreamDekPaging", "loadMoreRowItems row=$rowId: missing catalogType/catalogId on anchor")
          emptyList()
        } else {
          val catalogName = anchorItem.sourceCatalogName ?: catalogType
          android.util.Log.d("StreamDekPaging", "loadMoreRowItems row=$rowId: addon-catalog path addon=$addonId type=$catalogType catalogId=$catalogId genre=${anchorItem.sourceCatalogGenre} skip=$skip")
          apiClient.fetchMoreCatalogItems(uiState.session, addon, catalogType, catalogId, catalogName, anchorItem.sourceCatalogGenre, skip, uiState.activeProfileId)
            .onFailure { android.util.Log.w("StreamDekPaging", "fetchMoreCatalogItems failed row=$rowId", it) }
            .onSuccess { android.util.Log.d("StreamDekPaging", "fetchMoreCatalogItems row=$rowId returned ${it.size}") }
            .getOrDefault(emptyList())
        }
      }
    } else {
      val page = (skip / 20) + 1
      android.util.Log.d("StreamDekPaging", "loadMoreRowItems row=$rowId: built-in path page=$page skip=$skip")
      apiClient.fetchMoreBuiltInSection(rowId, page)
        .onFailure { android.util.Log.w("StreamDekPaging", "fetchMoreBuiltInSection failed row=$rowId", it) }
        .onSuccess { android.util.Log.d("StreamDekPaging", "fetchMoreBuiltInSection row=$rowId page=$page returned ${it.size}") }
        .getOrDefault(emptyList())
    }
    if (uiState.browseRow?.id == rowId && fetched.isNotEmpty()) {
      uiState = uiState.copy(browseLoadedItems = (uiState.browseLoadedItems + fetched).distinctBy { "${it.type}-${it.id}" })
    }
    return fetched
  }
  fun resumeContinueWatching(item: MediaItem, onUnavailable: () -> Unit): Boolean {
    val ownerKey = activeOwnerKey() ?: GUEST_OWNER_KEY
    val entry = playbackResumeStore.loadAll(ownerKey)
      .filter { it.mediaId == item.id && normalizedMediaType(it.mediaType) == normalizedMediaType(item.type) && !it.isLive }
      .maxByOrNull { it.updatedAt }
      ?.takeIf { remembered ->
        remembered.stream?.let { stream ->
          !stream.url.isNullOrBlank() || !stream.infoHash.isNullOrBlank()
        } == true
      }
      ?: return false
    pendingDirectContinueEntry = entry
    pendingDirectContinueFallback = onUnavailable
    loadDetail(item.type, item.id, item, preservePendingContinue = true)
    return true
  }

  private fun playPendingContinue(detail: MediaDetail, episode: EpisodeItem?): Boolean {
    val entry = pendingDirectContinueEntry?.takeIf {
      (it.mediaId == detail.id || it.mediaId == uiState.detailFallbackItem?.id) && normalizedMediaType(it.mediaType) == normalizedMediaType(detail.type)
    } ?: return false
    val stream = entry.stream ?: return false
    pendingDirectContinueEntry = null
    playStream(stream, episode, entry.progressPercent)
    return true
  }

  private fun continueFallbackEpisode(item: MediaItem?): EpisodeItem? {
    val season = item?.resumeSeasonNumber ?: return null
    val episode = item.resumeEpisodeNumber ?: return null
    return EpisodeItem(
      id = "${item.id}:$season:$episode",
      episodeNumber = episode,
      seasonNumber = season,
      name = "Episode $episode",
      overview = "",
      still = null,
      runtime = null,
    )
  }
  fun loadDetail(type: String, id: String, fallbackItem: MediaItem? = null, preservePendingContinue: Boolean = false) {
    if (!preservePendingContinue) {
      pendingDirectContinueEntry = null
      pendingDirectContinueFallback = null
    }
    val detailIsLive = fallbackItem?.isLiveCatalogItem() == true
    detailSourceAddonId = fallbackItem?.sourceAddonId
    detailSourceCatalogType = fallbackItem?.sourceCatalogType
    detailLocalStreamId = null
    detailLocalEpisodes = emptyList()
    detailDirectStream = fallbackItem?.directStreamUrl?.let { url ->
      val addon = uiState.addons.firstOrNull { it.id == fallbackItem.sourceAddonId }
      AddonStream(
        addonId = fallbackItem.sourceAddonId.orEmpty(),
        addonName = addon?.manifest?.name ?: fallbackItem.sourceAddonName ?: if (detailIsLive) "Live source" else "M3U Playlist",
        name = fallbackItem.title,
        title = fallbackItem.title,
        description = fallbackItem.description.ifBlank { null },
        url = url,
        infoHash = null,
        fileIdx = null,
        filename = null,
        quality = null,
        size = null,
        cachedBy = emptyList(),
        requestHeaders = fallbackItem.requestHeaders,
      )
    }
    if (detailIsLive && fallbackItem != null) {
      loadPlayerLiveChannels(fallbackItem)
      val fallback = fallbackItem.toFallbackDetail()
      uiState = uiState.copy(
        detailLoading = false, detail = fallback, detailIsLive = true, detailFallbackItem = fallbackItem, selectedPerson = null, personLoading = false,
        selectedSeasonEpisodes = emptyList(), selectedSeasonNumber = null, selectedEpisode = null,
        detailSelectedTab = null, pendingStreamSources = 0, availableStreams = emptyList(), errorMessage = null,
      )
      loadStreamsForCurrentDetail(null)
      return
    }
    if (fallbackItem != null && fallbackItem.sourceAddonId?.let(M3uPlaylistManager::isM3uSourceId) == true) {
      // M3U VOD entries already contain their final playable URL. Avoid sending their local
      // playlist IDs to TMDB/backend detail endpoints; show the playlist metadata immediately
      // and expose the direct stream through the normal source/player UI.
      val fallback = fallbackItem.toFallbackDetail()
      uiState = uiState.copy(
        detailLoading = false, detail = fallback, detailIsLive = false, detailFallbackItem = fallbackItem, selectedPerson = null, personLoading = false,
        selectedSeasonEpisodes = emptyList(), selectedSeasonNumber = null, selectedEpisode = null,
        detailSelectedTab = null, pendingStreamSources = 0, availableStreams = emptyList(), errorMessage = null,
      )
      loadStreamsForCurrentDetail(null)
      return
    }
    val localAddonId = fallbackItem?.sourceAddonId?.takeIf { LocalAddonManager.isLocalAddonId(it) }
    if (localAddonId != null && fallbackItem != null) {
      // CNCVerse-style catalogs intentionally keep previews tiny (and sometimes use "_" as
      // the preview title). The canonical /meta response is the source of truth for the real
      // title, synopsis, year and opaque episode ids; TMDB enrichment is layered on top of it.
      launchWork(
        onStart = { uiState = uiState.copy(detailLoading = true, detail = null, detailIsLive = false, detailFallbackItem = fallbackItem, selectedPerson = null, personLoading = false, selectedSeasonEpisodes = emptyList(), selectedSeasonNumber = null, selectedEpisode = null, detailSelectedTab = null, pendingStreamSources = 0, availableStreams = emptyList(), errorMessage = null) },
        block = {
          val addon = uiState.addons.firstOrNull { it.id == localAddonId }
            ?: return@launchWork Result.failure(IllegalStateException("The local add-on is no longer installed."))
          val bridgeMeta = apiClient.fetchLocalAddonMeta(addon, type, id).getOrThrow()
          val lookupId = bridgeMeta.imdbId ?: bridgeMeta.id
          val typeGuesses = when {
            bridgeMeta.episodes.isNotEmpty() || bridgeMeta.type in setOf("tv", "series", "show") -> listOf("tv", "movie")
            bridgeMeta.type == "movie" -> listOf("movie", "tv")
            else -> listOf("movie", "tv")
          }
          val enriched = coroutineScope {
            val guessResults = typeGuesses.map { guess ->
              async { apiClient.fetchDetails(guess, lookupId, bridgeMeta.title, bridgeMeta.year).getOrNull() }
            }.awaitAll()
            guessResults.firstNotNullOfOrNull { it }
          }
          android.util.Log.d("StreamDekDetail", "local addon detail addon=$localAddonId rawType=$type bridgeId=${bridgeMeta.id} title=${bridgeMeta.title} episodes=${bridgeMeta.episodes.size} enriched=${enriched != null}")
          Result.success(bridgeMeta to enriched)
        },
        onSuccess = { (bridgeMeta, enriched) ->
          detailLocalStreamId = bridgeMeta.id
          detailLocalEpisodes = bridgeMeta.episodes
          val resolvedDetail = (enriched?.withLocalAddonMeta(bridgeMeta) ?: bridgeMeta.toFallbackDetail(fallbackItem)).withCatalogFallback(fallbackItem)
          val unreleasedMovie = resolvedDetail.type == "movie" && isFutureReleaseDate(resolvedDetail.releaseDate)
          uiState = uiState.copy(
            detailLoading = false,
            detail = resolvedDetail,
            detailIsLive = false,
            streamLoading = false,
            pendingStreamSources = 0,
            errorMessage = null,
            availableStreams = if (unreleasedMovie) emptyList() else uiState.availableStreams,
          )
          if (enriched != null) {
            refreshExternalRatings(resolvedDetail)
            refreshTraktComments(resolvedDetail)
          }
          if (resolvedDetail.type == "tv" && resolvedDetail.seasons.isNotEmpty()) {
            val restartSeason = resolvedDetail.seasons.firstOrNull { it.seasonNumber == fallbackItem.resumeSeasonNumber } ?: resolvedDetail.seasons.first()
            loadSeason(resolvedDetail.id, restartSeason.seasonNumber, fallbackItem.resumeEpisodeNumber, resumeKnownSource = pendingDirectContinueEntry != null)
          } else if (!unreleasedMovie) {
            if (!playPendingContinue(resolvedDetail, continueFallbackEpisode(fallbackItem))) loadStreamsForCurrentDetail(null)
          }
        },
        onFailure = {
          detailLocalStreamId = id
          detailLocalEpisodes = emptyList()
          val fallback = fallbackItem.toFallbackDetail()
          uiState = uiState.copy(detailLoading = false, detail = fallback, detailIsLive = false, errorMessage = null)
          if (!playPendingContinue(fallback, continueFallbackEpisode(fallbackItem))) loadStreamsForCurrentDetail(null)
        },
      )
      return
    }
    launchWork(
      onStart = { uiState = uiState.copy(detailLoading = true, detail = null, detailIsLive = detailIsLive, detailFallbackItem = fallbackItem, selectedPerson = null, personLoading = false, selectedSeasonEpisodes = emptyList(), selectedSeasonNumber = null, selectedEpisode = null, detailSelectedTab = null, pendingStreamSources = 0, availableStreams = emptyList(), errorMessage = null) },
      block = { apiClient.fetchDetails(type, id, fallbackItem?.title, fallbackItem?.year) },
      onSuccess = { detail ->
        val resolvedDetail = detail.withCatalogFallback(fallbackItem)
        val unreleasedMovie = resolvedDetail.type == "movie" && isFutureReleaseDate(resolvedDetail.releaseDate)
        uiState = uiState.copy(
          detailLoading = false,
          detail = resolvedDetail,
          detailIsLive = detailIsLive,
          streamLoading = false,
          pendingStreamSources = 0,
          availableStreams = if (unreleasedMovie) emptyList() else uiState.availableStreams,
        )
        refreshExternalRatings(resolvedDetail)
        refreshTraktComments(resolvedDetail)
        if (resolvedDetail.type == "tv" && resolvedDetail.seasons.isNotEmpty()) {
          val restartSeason = resolvedDetail.seasons.firstOrNull { it.seasonNumber == fallbackItem?.resumeSeasonNumber } ?: resolvedDetail.seasons.first()
          loadSeason(resolvedDetail.id, restartSeason.seasonNumber, fallbackItem?.resumeEpisodeNumber, resumeKnownSource = pendingDirectContinueEntry != null)
        } else if (!unreleasedMovie) {
          if (!playPendingContinue(resolvedDetail, continueFallbackEpisode(fallbackItem))) loadStreamsForCurrentDetail(null)
        }
      },
      onFailure = { message ->
        val fallback = fallbackItem?.toFallbackDetail()
        if (fallback != null) {
          uiState = uiState.copy(detailLoading = false, detail = fallback, detailIsLive = detailIsLive, errorMessage = null)
          if (!playPendingContinue(fallback, continueFallbackEpisode(fallbackItem))) loadStreamsForCurrentDetail(null)
        } else {
          uiState = uiState.copy(detailLoading = false, errorMessage = message)
        }
      },
    )
  }

  fun openPerson(person: CastMember) {
    if (person.id.isBlank()) {
      uiState = uiState.copy(
        selectedPerson = PersonDetail(
          id = person.id,
          name = person.name,
          photo = person.photo,
          biography = null,
          birthday = null,
          placeOfBirth = null,
          knownFor = person.character,
        ),
      )
      return
    }
    launchWork(
      onStart = { uiState = uiState.copy(personLoading = true, errorMessage = null) },
      block = { apiClient.fetchPerson(person.id) },
      onSuccess = { detail -> uiState = uiState.copy(personLoading = false, selectedPerson = detail) },
      onFailure = {
        uiState = uiState.copy(
          personLoading = false,
          selectedPerson = PersonDetail(
            id = person.id,
            name = person.name,
            photo = person.photo,
            biography = null,
            birthday = null,
            placeOfBirth = null,
            knownFor = person.character,
          ),
        )
      },
    )
  }

  fun closePerson() { uiState = uiState.copy(selectedPerson = null, personLoading = false) }
  fun loadSeason(tvId: String, seasonNumber: Int, preferredEpisodeNumber: Int? = null, resumeKnownSource: Boolean = false) {
    if (detailLocalEpisodes.isNotEmpty()) {
      val episodes = detailLocalEpisodes.filter { it.seasonNumber == seasonNumber }.sortedBy(EpisodeItem::episodeNumber)
      uiState = uiState.copy(seasonLoading = false, selectedSeasonEpisodes = episodes, selectedSeasonNumber = seasonNumber, errorMessage = null)
      (episodes.firstOrNull { it.episodeNumber == preferredEpisodeNumber } ?: episodes.firstOrNull())?.let { episode ->
        if (!resumeKnownSource || !playPendingContinue(uiState.detail ?: return@let, episode)) loadStreamsForCurrentDetail(episode)
      }
      return
    }
    launchWork(
      onStart = { uiState = uiState.copy(seasonLoading = true, selectedSeasonNumber = seasonNumber, errorMessage = null) },
      block = { apiClient.fetchSeason(tvId, seasonNumber) },
      onSuccess = { episodes ->
        uiState = uiState.copy(seasonLoading = false, selectedSeasonEpisodes = episodes, selectedSeasonNumber = seasonNumber)
        (episodes.firstOrNull { it.episodeNumber == preferredEpisodeNumber } ?: episodes.firstOrNull())?.let { episode ->
          if (!resumeKnownSource || !playPendingContinue(uiState.detail ?: return@let, episode)) loadStreamsForCurrentDetail(episode)
        }
      },
      onFailure = { message -> uiState = uiState.copy(seasonLoading = false, errorMessage = message) },
    )
  }

  private fun streamLookupIds(detail: MediaDetail): List<String> {
    // Local add-on items keep detail.id as the TMDB id (for season/rating/Trakt calls) once
    // enrichment succeeds, but the add-on's own /stream endpoint needs its own canonical id --
    // tracked separately since it can differ completely from whatever TMDB resolved to.
    detailLocalStreamId?.takeIf { it.isNotBlank() }?.let { return listOf(it) }
    val imdbId = detail.imdbId?.let { Regex("tt\\d+", RegexOption.IGNORE_CASE).find(it)?.value }
    return listOfNotNull(imdbId ?: detail.id.takeIf(String::isNotBlank))
  }

  fun loadStreamsForCurrentDetail(episode: EpisodeItem? = null) {
    val detail = uiState.detail ?: return
    if (detail.type == "movie" && isFutureReleaseDate(detail.releaseDate)) {
      pendingStreamLoad = null
      uiState = uiState.copy(streamLoading = false, pendingStreamSources = 0, availableStreams = emptyList(), selectedEpisode = null, errorMessage = null)
      return
    }
    if (uiState.addonsLoading) {
      pendingStreamLoad = PendingStreamLoad(detail.id, episode)
      uiState = uiState.copy(streamLoading = true, pendingStreamSources = 0, selectedEpisode = episode, errorMessage = null)
      return
    }
    pendingStreamLoad = null
    detailDirectStream?.let { stream ->
      uiState = uiState.copy(streamLoading = false, pendingStreamSources = 0, availableStreams = listOf(stream), selectedEpisode = episode, errorMessage = null)
      return
    }
    val type: String
    val ids: List<String>
    when {
      detail.type == "tv" && episode != null -> {
        val bridgeEpisodeId = episode.sourceStreamId?.takeIf { it.isNotBlank() }
        type = if (bridgeEpisodeId != null) detailSourceCatalogType ?: "series" else "series"
        ids = if (bridgeEpisodeId != null) {
          listOf(bridgeEpisodeId)
        } else {
          streamLookupIds(detail)
            .distinct()
            .map { "${it}:${episode.seasonNumber}:${episode.episodeNumber}" }
        }
      }
      detail.type == "tv" && !uiState.detailIsLive -> {
        uiState = uiState.copy(streamLoading = false, pendingStreamSources = 0, errorMessage = "Choose an episode first.")
        return
      }
      else -> {
        val localBridgeType = detailSourceAddonId
          ?.takeIf(LocalAddonManager::isLocalAddonId)
          ?.let { detailSourceCatalogType }
        type = if (uiState.detailIsLive) detailSourceCatalogType ?: detail.type else localBridgeType ?: detail.type
        ids = streamLookupIds(detail).distinct()
      }
    }
    beginProgressiveStreamLoad(detail = detail, type = type, ids = ids, episode = episode)
  }

  private fun beginProgressiveStreamLoad(detail: MediaDetail, type: String, ids: List<String>, episode: EpisodeItem?) {
    val candidates = ids.filter { it.isNotBlank() }.distinct()
    if (candidates.isEmpty()) {
      uiState = uiState.copy(streamLoading = false, pendingStreamSources = 0, availableStreams = emptyList(), selectedEpisode = episode, errorMessage = "No stream identifiers were available for this title.")
      return
    }

    val enabledAddons = uiState.addons
      .filter { addon ->
        if (!addon.enabled) return@filter false
        if (uiState.detailIsLive) {
          // Prefer the add-on the channel came from; otherwise only ask add-ons that
          // actually serve live TV. Movie/series add-ons are never queried here.
          if (detailSourceAddonId != null) addon.id == detailSourceAddonId else addonSupportsLiveStreams(addon)
        } else {
          addonSupportsStreamType(addon, type)
        }
      }
      .sortedBy { it.position }
    val pluginState = StreamDekPlugins.manager.state
    val enabledPluginRepos = pluginState.repos.filter { it.enabled }.mapTo(mutableSetOf()) { it.url }
    val pluginSourceCount = if (!uiState.detailIsLive && pluginState.enabled && pluginState.providers.any { it.enabled && it.repoUrl in enabledPluginRepos }) 1 else 0
    val totalSources = candidates.size * enabledAddons.size + pluginSourceCount
    val generation = ++streamRequestGeneration
    val merged = linkedMapOf<String, AddonStream>()
    val requestGate = Semaphore(4)
    var completedSources = 0
    var lastError: Throwable? = null

    fun streamKey(stream: AddonStream): String = addonStreamPlaybackIdentity(stream)

    fun publish() {
      if (generation != streamRequestGeneration) return
      val ranked = rankedStreams(mediaStreamsOnly(merged.values.toList(), detail), uiState.debridAccounts.isNotEmpty(), uiState.preferredQuality, uiState.maxFileSizeGb)
      val remaining = (totalSources - completedSources).coerceAtLeast(0)
      uiState = uiState.copy(
        streamLoading = remaining > 0,
        pendingStreamSources = remaining,
        availableStreams = ranked,
        selectedEpisode = episode,
      )
    }

    uiState = uiState.copy(streamLoading = true, pendingStreamSources = totalSources, availableStreams = emptyList(), selectedEpisode = episode, errorMessage = null)

    viewModelScope.launch {
      if (pluginSourceCount > 0) {
        launch {
          val pluginType = if (detail.type == "series") "tv" else detail.type
          val pluginId = apiClient.resolvePluginMediaId(pluginType, detail.id)
          StreamDekPlugins.manager.streams(pluginId, pluginType, episode?.seasonNumber, episode?.episodeNumber) { providerStreams ->
            withContext(Dispatchers.Main.immediate) {
              if (generation == streamRequestGeneration) {
                providerStreams.forEach { stream -> merged.putIfAbsent(streamKey(stream), stream) }
                publish()
              }
            }
          }.forEach { stream -> merged.putIfAbsent(streamKey(stream), stream) }
          completedSources += 1
          publish()
        }
      }
      for (id in candidates) {
        for (addon in enabledAddons) {
          launch {
            requestGate.withPermit { fetchAddonStreamsWithRetry(addon, type, id) }
              .onSuccess { streams -> streams.forEach { stream -> merged.putIfAbsent(streamKey(stream), stream) } }
              .onFailure { lastError = it }
            completedSources += 1
            publish()
          }
        }

      }
    }.invokeOnCompletion {
      if (generation != streamRequestGeneration) return@invokeOnCompletion
      viewModelScope.launch {
        // The aggregate endpoint fans out across every installed add-on, so it is
        // skipped for live channels — those are served only by live-capable add-ons.
        if (merged.isEmpty() && !uiState.detailIsLive) {
          val fallback = mutableListOf<AddonStream>()
          for (id in candidates) {
            apiClient.fetchStreams(uiState.session, type, id, uiState.activeProfileId)
              .onSuccess { fallback += it }
              .onFailure { lastError = it }
          }
          fallback.forEach { stream -> merged.putIfAbsent(streamKey(stream), stream) }
        }
        val ranked = rankedStreams(mediaStreamsOnly(merged.values.toList(), detail), uiState.debridAccounts.isNotEmpty(), uiState.preferredQuality, uiState.maxFileSizeGb)
        uiState = uiState.copy(
          streamLoading = false,
          pendingStreamSources = 0,
          availableStreams = ranked,
          selectedEpisode = episode,
          errorMessage = lastError.let { error -> if (ranked.isEmpty() && error != null) error.message ?: "No playable streams were found." else uiState.errorMessage },
        )
      }
    }
  }

  private suspend fun fetchAddonStreamsWithRetry(addon: InstalledAddon, type: String, id: String): Result<List<AddonStream>> {
    val requestedType = type.trim().lowercase()
    val typeCandidates = if (uiState.detailIsLive) {
      buildList {
        add(requestedType)
        detailSourceCatalogType?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let(::add)
        addon.manifest.types.mapTo(this) { it.trim().lowercase() }
        addAll(listOf("live", "channel", "tv", "sport", "sports", "other"))
      }.filter { it.isNotBlank() }.distinct()
    } else {
      listOf(requestedType)
    }
    var lastError: Throwable? = null
    val isLocalAddon = LocalAddonManager.isLocalAddonId(addon.id)
    if (!isLocalAddon) {
      for (candidateType in typeCandidates) {
        apiClient.fetchStreamsFromAddon(uiState.session, addon.id, candidateType, id, uiState.activeProfileId)
          .onSuccess { if (it.isNotEmpty()) return Result.success(it) }
          .onFailure { lastError = it }
      }
    }
    val baseId = id.substringBefore(":")
    val requiresImdbId = !uiState.detailIsLive && (requestedType == "movie" || requestedType == "series" || requestedType == "tv")
    // Local add-ons only ever fetch directly from the device (there's no backend copy to try
    // first), so they always go straight to the on-device fetch below.
    if (isLocalAddon || !requiresImdbId || baseId.matches(Regex("^tt\\d+$", RegexOption.IGNORE_CASE))) {
      if (!isLocalAddon) delay(180L)
      for (candidateType in typeCandidates.take(3)) {
        apiClient.fetchFreshStreamsFromAddon(addon, candidateType, id)
          .onSuccess { if (it.isNotEmpty()) return Result.success(it) }
          .onFailure { lastError = it }
      }
    }
    return lastError.let { error -> if (error == null) Result.success(emptyList()) else Result.failure(error) }
  }

  private suspend fun fetchStreamsForPlayback(type: String, ids: List<String>): Result<List<AddonStream>> {
    val merged = mutableListOf<AddonStream>()
    var lastError: Throwable? = null
    val candidates = ids.filter { it.isNotBlank() }.distinct()
    val live = uiState.detailIsLive
    val enabledAddons = uiState.addons
      .filter { addon ->
        addon.enabled && if (live) addonSupportsLiveStreams(addon) else addonSupportsStreamType(addon, type)
      }
      .sortedBy { it.position }

    for (id in candidates) {
      for (addon in enabledAddons) {
        fetchAddonStreamsWithRetry(addon, type, id)
          .onSuccess { streams -> merged += streams }
          .onFailure { lastError = it }
      }

      // The aggregate endpoint spans every add-on, so live channels never use it.
      if (merged.isEmpty() && !live) {
        apiClient.fetchStreams(uiState.session, type, id, uiState.activeProfileId)
          .onSuccess { streams -> merged += streams }
          .onFailure { lastError = it }
      }
    }

    // Plugin providers are movie/series scrapers — not a source for live channels.
    if (!live) {
      val pluginType = if (type == "series") "tv" else type
      val pluginCandidate = candidates.firstOrNull()?.substringBefore(":").orEmpty()
      val pluginId = apiClient.resolvePluginMediaId(pluginType, pluginCandidate)
      merged += StreamDekPlugins.manager.streams(pluginId, pluginType, null, null)
    }

    val unique = merged.distinctBy(::addonStreamPlaybackIdentity)
    return lastError.let { error -> if (unique.isNotEmpty() || error == null) Result.success(unique) else Result.failure(error) }
  }

  /**
   * Live channels must only ever be resolved by add-ons that actually serve live TV.
   * [addonSupportsStreamType] maps "tv" onto "series", which would otherwise match
   * every movie/series debrid add-on and waste a request per channel on sources that
   * can never answer.
   */
  private fun addonSupportsLiveStreams(addon: InstalledAddon): Boolean {
    val resources = addon.manifest.resources.map { it.trim().lowercase() }
    if (resources.isNotEmpty() && resources.none { it == "stream" || it == "streams" }) return false
    val types = addon.manifest.types.map { it.trim().lowercase() }
    // An add-on with no declared types is ambiguous — never assume it serves live TV.
    return types.any { it in liveAddonTypes }
  }

  private fun addonSupportsStreamType(addon: InstalledAddon, type: String): Boolean {
    val resources = addon.manifest.resources.map { it.trim().lowercase() }
    if (resources.isNotEmpty() && resources.none { it == "stream" || it == "streams" }) return false
    val nativeType = if (type == "tv") "series" else type.trim().lowercase()
    val types = addon.manifest.types.map { it.trim().lowercase() }
    if (types.isEmpty()) return true
    return nativeType in types || (nativeType == "series" && "tv" in types)
  }

  private fun needsFreshPlaybackUrl(stream: AddonStream): Boolean {
    val url = stream.url ?: return false
    return runCatching {
      val uri = Uri.parse(url)
      uri.host.equals("pengu.uk", ignoreCase = true) && uri.path.orEmpty().startsWith("/direct/")
    }.getOrDefault(false)
  }

  private suspend fun refreshStreamForPlayback(stream: AddonStream, detail: MediaDetail, episode: EpisodeItem?): AddonStream {
    if (!needsFreshPlaybackUrl(stream)) return stream
    val addon = uiState.addons.firstOrNull { it.id == stream.addonId }
      ?: throw IllegalStateException("The addon needed to refresh this playback link is unavailable.")
    val streamType = if (detail.type == "tv") "series" else detail.type
    val mediaId = detail.imdbId?.takeIf { it.isNotBlank() } ?: detail.id
    val videoId = episode?.let { "$mediaId:${it.seasonNumber}:${it.episodeNumber}" } ?: mediaId
    val fresh = apiClient.fetchFreshStreamsFromAddon(addon, streamType, videoId).getOrThrow()
    return fresh.firstOrNull { candidate ->
      !stream.bingeGroup.isNullOrBlank() && candidate.bingeGroup == stream.bingeGroup
    } ?: fresh.firstOrNull { candidate ->
      !stream.filename.isNullOrBlank() && candidate.filename == stream.filename && candidate.name == stream.name
    } ?: throw IllegalStateException("This addon could not refresh the selected playback link. Try another source.")
  }

  private suspend fun resolvePlayback(stream: AddonStream, detail: MediaDetail, episode: EpisodeItem?): Result<ResolvedPlayback> = runCatching {
    val playbackStream = refreshStreamForPlayback(stream, detail, episode)
    val directUrl = playbackStream.url?.takeIf { it.isNotBlank() && !it.startsWith("magnet:", ignoreCase = true) }
    if (directUrl != null) {
      val shouldPrepareBridgeHls = detailLocalStreamId != null && playbackStream.requestHeaders.isNotEmpty()
      val preparedUrl = if (shouldPrepareBridgeHls) {
        apiClient.prepareHlsForPlayback(directUrl, playbackStream.requestHeaders).getOrDefault(directUrl)
      } else {
        directUrl
      }
      return@runCatching ResolvedPlayback(preparedUrl, playbackStream)
    }

    val infoHash = playbackStream.infoHash?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException("This source does not contain a playable URL or torrent hash.")
    var lastFailure: Throwable? = null

    val maxSizeBytes = uiState.maxFileSizeGb.takeIf { it > 0 }?.toLong()?.times(1024L * 1024L * 1024L)
    if (uiState.session != null && uiState.debridAccounts.isNotEmpty()) {
      apiClient.resolveStream(uiState.session!!, playbackStream, maxSizeBytes)
        .onFailure { lastFailure = it }
        .getOrNull()?.url?.takeIf { it.isNotBlank() }?.let { resolvedUrl ->
          return@runCatching ResolvedPlayback(resolvedUrl, playbackStream)
        }
    }

    val torrentSettings = uiState.torrentServerSettings
    if (torrentSettings.streamingMode == "server") {
      ensureTorrentServerReady(torrentSettings.toServiceConfig())
        .onFailure { lastFailure = it }
        .getOrNull()?.let { activeConfig ->
          return@runCatching ResolvedPlayback(
            TorrentServerService.createTorrentProxyUrl(
              activeConfig,
              infoHash,
              buildTorrentMagnet(infoHash, playbackStream.filename),
              playbackStream.filename,
            ),
            playbackStream,
          )
        }

      apiClient.streamTorrent(playbackStream)
        .onFailure { lastFailure = it }
        .getOrNull()?.takeIf { it.isNotBlank() }?.let { backendUrl ->
          return@runCatching ResolvedPlayback(backendUrl, playbackStream)
        }
    }

    throw IllegalStateException(lastFailure?.message ?: "This torrent could not be resolved. Enable the local torrent server or try another source.")
  }

  fun playStream(
    stream: AddonStream,
    episode: EpisodeItem? = null,
    resumePercentOverride: Double? = null,
    returnToEpisodeStreams: Boolean = false,
  ) {
    val detail = uiState.detail ?: return
    val selectedEpisode = episode ?: uiState.selectedEpisode
    val requestGeneration = ++playbackRequestGeneration
    launchWork(
      onStart = {
        uiState = uiState.copy(
          streamLoading = true,
          errorMessage = null,
          returnToDetailAfterPlayer = true,
          playerLaunching = true,
          playerLaunchingLabel = buildSourceLabel(stream) ?: stream.addonName,
          playerReturnEpisodeId = if (returnToEpisodeStreams) selectedEpisode?.id else null,
        )
      },
      block = { resolvePlayback(stream, detail, selectedEpisode) },
      onSuccess = success@ { playback ->
        if (requestGeneration != playbackRequestGeneration) return@success
        val resumePercent = resumePercentOverride ?: loadPlaybackMemoryEntry(detail, selectedEpisode)?.progressPercent ?: 0.0
        pendingDirectContinueFallback = null
        val builtSession = buildPlayerSession(playback.url, detail, buildSourceLabel(playback.stream), selectedEpisode, playback.stream, resumePercent)

        // Switching channels while one is already playing: swap the session straight over.
        // The old content stays visibly playing until the new source is actually ready -
        // ExoPlaybackView prepares the next source in the background and swaps in place
        // (see prepareSourceInBackground/promotePendingPlayer), and mpv's `loadfile ...
        // replace` does the same natively - so there's no need to pre-verify reachability
        // with a separate probe first. channelSwitchLoading stays true until the player
        // itself confirms the new channel is decoding (onChannelSwitchPlaybackStarted).
        uiState = uiState.copy(
          streamLoading = false,
          nextEpisodeLoading = false,
          nextEpisodeLoadingLabel = null,
          playerSession = builtSession,
          playerLaunching = false,
          playerLaunchingLabel = null,
          liveChannelSwitching = liveChannelSwitchSnapshot != null,
          liveChannelSwitchingLabel = uiState.liveChannelSwitchingLabel,
        )
      },
      onFailure = failure@ { message ->
        if (requestGeneration != playbackRequestGeneration) return@failure
        // Live channels commonly carry dead mirrors — roll on to the next source
        // instead of stopping at the first failure.
        if (uiState.detailIsLive && playNextLiveSource(stream, episode)) return@failure
        pendingDirectContinueFallback?.let { showDetails ->
          pendingDirectContinueFallback = null
          pendingDirectContinueEntry = null
          uiState = uiState.copy(playerLaunching = false, playerLaunchingLabel = null, streamLoading = false, nextEpisodeLoading = false, nextEpisodeLoadingLabel = null, errorMessage = null)
          showDetails()
          return@failure
        }
        if (uiState.liveChannelSwitching) {
          val failedChannel = uiState.liveChannelSwitchingLabel
          cancelLiveChannelSwitch()
          uiState = uiState.copy(infoMessage = failedChannel?.let { "Could not load $it. Kept the current channel playing." } ?: message)
          return@failure
        }
        uiState = uiState.copy(playerLaunching = false, playerLaunchingLabel = null, streamLoading = false, nextEpisodeLoading = false, nextEpisodeLoadingLabel = null, errorMessage = message)
      },
    )
  }

  /**
   * Advances to the next live source after [failedStream]. Returns false when the
   * list is exhausted so the caller can surface the original error.
   */
  private fun playNextLiveSource(failedStream: AddonStream, episode: EpisodeItem?): Boolean {
    val detail = uiState.detail ?: return false
    val streams = uiState.availableStreams
    val failedKey = streamIdentity(failedStream)
    val index = streams.indexOfFirst { streamIdentity(it) == failedKey }
    val next = streams.drop((index + 1).coerceAtLeast(0)).firstOrNull { streamIdentity(it) != failedKey }
    if (next != null) {
      uiState = uiState.copy(playerLaunchingLabel = buildSourceLabel(next) ?: next.addonName)
      playStream(next, episode)
      return true
    }
    // The remembered source failed before the full list was loaded — forget it and
    // start a fresh search so the channel still plays.
    if (index < 0 && loadPlaybackMemoryEntry(detail, null)?.stream != null) {
      playbackResumeStore.removeTitle(activeOwnerKey() ?: GUEST_OWNER_KEY, detail.id, if (detail.type == "series") "tv" else detail.type)
      uiState = uiState.copy(localResumeEntries = loadResumeEntries())
      playBestStream(episode)
      return true
    }
    return false
  }

  fun confirmLiveChannelSwitchStarted() {
    if (!uiState.liveChannelSwitching) return
    val detail = uiState.detail
    val stream = uiState.playerSession?.currentStream
    if (detail != null && stream != null && uiState.detailIsLive) rememberLiveSource(detail, stream)
    // Keep the previous working session snapshot available. If this new feed later
    // errors or stalls, Back can still restore it instead of leaving the player.
    uiState = uiState.copy(liveChannelSwitching = false, liveChannelSwitchingLabel = null)
  }
  private fun rememberLiveSource(detail: MediaDetail, stream: AddonStream) {
    if (!uiState.rememberLastSource) return
    val ownerKey = activeOwnerKey() ?: GUEST_OWNER_KEY
    playbackResumeStore.save(
      ownerKey,
      PlaybackMemoryEntry(
        mediaId = detail.id,
        mediaType = if (detail.type == "series") "tv" else detail.type,
        title = detail.title,
        year = detail.year,
        poster = detail.poster,
        backdrop = detail.backdrop,
        progressPercent = 0.0,
        isLive = true,
        stream = stream,
      ),
    )
    uiState = uiState.copy(localResumeEntries = loadResumeEntries())
  }

  fun playBestStream(episode: EpisodeItem? = null) {
    val detail = uiState.detail ?: return
    if (detail.type == "movie" && isFutureReleaseDate(detail.releaseDate)) {
      uiState = uiState.copy(streamLoading = false, pendingStreamSources = 0, availableStreams = emptyList(), errorMessage = null)
      return
    }
    // With no explicit episode selection on a series, resume the episode the user
    // last watched so the "Continue S#-E#" CTA plays exactly where they left off.
    val selectedEpisode = episode ?: uiState.selectedEpisode ?: rememberedEpisodeFor(detail)
    val remembered = loadPlaybackMemoryEntry(detail, selectedEpisode)
    if (!uiState.detailIsLive && uiState.rememberLastSource && remembered?.stream != null && (remembered.progressPercent > 0.0 || !remembered.stream.url.isNullOrBlank() || !remembered.stream.infoHash.isNullOrBlank())) {
      playStream(remembered.stream, selectedEpisode, remembered.progressPercent)
      return
    }
    val cached = uiState.availableStreams.firstOrNull()
    if (cached != null && selectedEpisode == uiState.selectedEpisode) {
      playStream(cached, selectedEpisode)
      return
    }
    launchWork(
      onStart = { uiState = uiState.copy(streamLoading = true, errorMessage = null) },
      block = {
        when {
          uiState.detailIsLive && detailSourceAddonId != null -> {
            val addon = uiState.addons.firstOrNull { it.id == detailSourceAddonId }
              ?: return@launchWork Result.failure(IllegalStateException("The live addon is no longer enabled."))
            val mediaId = detail.id.takeIf { it.isNotBlank() }
              ?: return@launchWork Result.failure(IllegalStateException("This channel has no stream identifier."))
            fetchAddonStreamsWithRetry(addon, detailSourceCatalogType ?: detail.type, mediaId)
          }
          detail.type == "tv" && !uiState.detailIsLive && selectedEpisode != null -> {
            val bridgeEpisodeId = selectedEpisode.sourceStreamId?.takeIf { it.isNotBlank() }
            if (bridgeEpisodeId != null) {
              fetchStreamsForPlayback(detailSourceCatalogType ?: "series", listOf(bridgeEpisodeId))
            } else {
              val ids = streamLookupIds(detail).distinct()
              fetchStreamsForPlayback("series", ids.map { "${it}:${selectedEpisode.seasonNumber}:${selectedEpisode.episodeNumber}" })
            }
          }
          detail.type == "tv" && !uiState.detailIsLive -> Result.failure(IllegalStateException("Choose an episode first."))
          uiState.detailIsLive -> {
            // Source add-on unknown — still restrict the search to live TV add-ons.
            fetchStreamsForPlayback(detailSourceCatalogType ?: detail.type, listOf(detail.id))
          }
          else -> {
            val ids = streamLookupIds(detail).distinct()
            val localBridgeType = detailSourceAddonId
              ?.takeIf(LocalAddonManager::isLocalAddonId)
              ?.let { detailSourceCatalogType }
            fetchStreamsForPlayback(localBridgeType ?: detail.type, ids)
          }
        }
      },
      onSuccess = { streams ->
        val ranked = rankedStreams(mediaStreamsOnly(streams, detail), uiState.debridAccounts.isNotEmpty(), uiState.preferredQuality, uiState.maxFileSizeGb)
        uiState = uiState.copy(streamLoading = false, availableStreams = ranked, selectedEpisode = selectedEpisode)
        val preferred = remembered?.stream?.takeIf { uiState.detailIsLive && uiState.rememberLastSource }?.let { saved ->
          ranked.firstOrNull { candidate ->
            streamIdentity(candidate) == streamIdentity(saved) ||
              addonStreamPlaybackIdentity(candidate) == addonStreamPlaybackIdentity(saved) ||
              (candidate.addonId == saved.addonId && listOf(candidate.source, candidate.name, candidate.title).any { label ->
                !label.isNullOrBlank() && listOf(saved.source, saved.name, saved.title).any { rememberedLabel -> label.equals(rememberedLabel, ignoreCase = true) }
              })
          }
        } ?: ranked.firstOrNull()
        preferred?.let { playStream(it, selectedEpisode) } ?: run {
          uiState = uiState.copy(errorMessage = "No playable streams were found.")
        }
      },
      onFailure = { message -> uiState = uiState.copy(streamLoading = false, nextEpisodeLoading = false, nextEpisodeLoadingLabel = null, errorMessage = message) },
    )
  }
  fun refreshProfiles(showLoading: Boolean = true, refreshScopedData: Boolean = true) {
    val session = uiState.session ?: return
    launchWork(
      onStart = { if (showLoading) uiState = uiState.copy(profilesLoading = true, errorMessage = null) },
      block = { apiClient.fetchProfiles(session) },
      onSuccess = { profiles ->
        val selected = determineActiveProfile(session, profiles)
        val showPicker = if (routeAfterProfileRefresh) profiles.size > 1 else uiState.showProfilePicker
        routeAfterProfileRefresh = false
        uiState = uiState.copy(profilesLoading = false, profiles = profiles, activeProfileId = selected?.id ?: uiState.activeProfileId, showProfilePicker = showPicker)
        selected?.id?.let { profileSelectionStore.save(session.user.uid, it) }
        if (refreshScopedData) refreshProfileScopedData()
      },
      onFailure = { message ->
        routeAfterProfileRefresh = false
        uiState = uiState.copy(profilesLoading = false, errorMessage = message)
      },
    )
  }

  fun selectProfile(profileId: String) {
    val profile = uiState.profiles.firstOrNull { it.id == profileId } ?: return
    val session = uiState.session
    if (session == null) {
      profileSelectionStore.save(GUEST_OWNER_KEY, profileId)
      uiState = uiState.copy(activeProfileId = profileId, showProfilePicker = false, availableStreams = emptyList(), profileTransitioning = true)
      refreshProfileScopedData()
      loadHome(force = true)
      return
    }
    if (profile.hasPinSet && (uiState.showProfilePicker || profile.id != uiState.activeProfileId)) {
      uiState = uiState.copy(pinPromptProfileId = profile.id, pinPromptProfileName = profile.name)
      return
    }
    profileSelectionStore.save(session.user.uid, profileId)
    uiState = uiState.copy(activeProfileId = profileId, showProfilePicker = false, pinPromptProfileId = null, pinPromptProfileName = null, availableStreams = emptyList(), profileTransitioning = true)
    refreshProfileScopedData()
    loadHome(force = true)
  }

  fun submitProfilePin(pin: String) {
    val session = uiState.session ?: return
    val profileId = uiState.pinPromptProfileId ?: return
    launchWork(
      onStart = { uiState = uiState.copy(profilesLoading = true, errorMessage = null) },
      block = { apiClient.verifyProfilePin(session, profileId, pin) },
      onSuccess = { valid ->
        if (valid) {
          profileSelectionStore.save(session.user.uid, profileId)
          uiState = uiState.copy(profilesLoading = false, activeProfileId = profileId, showProfilePicker = false, pinPromptProfileId = null, pinPromptProfileName = null, profileTransitioning = true)
          refreshProfileScopedData()
          loadHome(force = true)
        } else uiState = uiState.copy(profilesLoading = false, errorMessage = "Incorrect PIN. Try again.")
      },
      onFailure = { message -> uiState = uiState.copy(profilesLoading = false, errorMessage = message) },
    )
  }

  fun finishProfileTransition() { uiState = uiState.copy(profileTransitioning = false) }
  fun cancelProfilePinPrompt() { uiState = uiState.copy(pinPromptProfileId = null, pinPromptProfileName = null) }
  fun dismissProfilePicker() { uiState = uiState.copy(showProfilePicker = false) }

  fun showProfilePicker() {
    if (uiState.session == null) {
      if (uiState.profiles.isEmpty()) uiState = uiState.copy(infoMessage = "Create a local profile from Settings first.")
      else uiState = uiState.copy(showProfilePicker = true)
      return
    }
    uiState = uiState.copy(showProfilePicker = true)
    refreshProfiles(showLoading = uiState.profiles.isEmpty(), refreshScopedData = false)
  }

  fun createProfile(name: String, avatarIndex: Int = 0) {
    val normalized = name.trim().take(32)
    if (normalized.isBlank()) return
    val session = uiState.session
    if (session == null) {
      val profile = StreamProfile("guest-${UUID.randomUUID()}", GUEST_OWNER_KEY, normalized, avatarIndex.coerceIn(0, 11), false, uiState.profiles.isEmpty(), null, null)
      val profiles = uiState.profiles + profile
      guestProfileStore.save(profiles)
      profileSelectionStore.save(GUEST_OWNER_KEY, profile.id)
      uiState = uiState.copy(profiles = profiles, activeProfileId = profile.id, showProfilePicker = false, infoMessage = "Local profile created on this device.")
      refreshProfileScopedData()
      return
    }
    launchWork(
      onStart = { uiState = uiState.copy(profilesLoading = true, errorMessage = null) },
      block = { apiClient.createProfile(session, normalized, avatarIndex) },
      onSuccess = { profile ->
        val profiles = uiState.profiles + profile
        profileSelectionStore.save(session.user.uid, profile.id)
        uiState = uiState.copy(profilesLoading = false, profiles = profiles, activeProfileId = profile.id, showProfilePicker = false)
        refreshProfileScopedData()
      },
      onFailure = { message -> uiState = uiState.copy(profilesLoading = false, errorMessage = message) },
    )
  }

  fun deleteProfile(profileId: String) {
    val profile = uiState.profiles.firstOrNull { it.id == profileId } ?: return
    val fallback = uiState.profiles.firstOrNull { it.id != profileId }
    if (fallback == null) {
      uiState = uiState.copy(infoMessage = "At least one profile is required.")
      return
    }
    val session = uiState.session
    if (session == null) {
      val profiles = uiState.profiles.filterNot { it.id == profileId }.map { it.copy(isDefault = if (profile.isDefault) it.id == fallback.id else it.isDefault) }
      guestProfileStore.save(profiles)
      val selectedId = if (uiState.activeProfileId == profileId) fallback.id else uiState.activeProfileId
      profileSelectionStore.save(GUEST_OWNER_KEY, selectedId)
      uiState = uiState.copy(profiles = profiles, activeProfileId = selectedId)
      refreshProfileScopedData()
      return
    }
    launchWork(
      onStart = { uiState = uiState.copy(profilesLoading = true, errorMessage = null) },
      block = {
        if (profile.isDefault) apiClient.setDefaultProfile(session, fallback.id).getOrThrow()
        apiClient.deleteProfile(session, profileId)
      },
      onSuccess = {
        if (uiState.activeProfileId == profileId) {
          profileSelectionStore.save(session.user.uid, fallback.id)
          uiState = uiState.copy(activeProfileId = fallback.id)
        }
        refreshProfiles()
      },
      onFailure = { message -> uiState = uiState.copy(profilesLoading = false, errorMessage = message) },
    )
  }

  fun makeDefaultProfile(profileId: String) {
    val session = uiState.session
    if (session == null) {
      val profiles = uiState.profiles.map { it.copy(isDefault = it.id == profileId) }
      guestProfileStore.save(profiles)
      uiState = uiState.copy(profiles = profiles)
      return
    }
    launchWork(onStart = {}, block = { apiClient.setDefaultProfile(session, profileId) }, onSuccess = { refreshProfiles() })
  }

  fun updateProfile(profileId: String, name: String, avatarIndex: Int) {
    val normalized = name.trim().take(32)
    if (normalized.isBlank()) return
    val session = uiState.session
    if (session == null) {
      val profiles = uiState.profiles.map { if (it.id == profileId) it.copy(name = normalized, avatarIndex = avatarIndex.coerceIn(0, 11)) else it }
      guestProfileStore.save(profiles)
      uiState = uiState.copy(profiles = profiles)
      return
    }
    launchWork(onStart = { uiState = uiState.copy(profilesLoading = true) }, block = { apiClient.updateProfile(session, profileId, normalized, avatarIndex) }, onSuccess = { refreshProfiles() })
  }

  fun updateProfilePin(profileId: String, pin: String?) {
    val session = uiState.session ?: run {
      uiState = uiState.copy(infoMessage = "PINs are available for synced profiles after signing in.")
      return
    }
    launchWork(onStart = { uiState = uiState.copy(profilesLoading = true) }, block = { apiClient.setProfilePin(session, profileId, pin) }, onSuccess = { refreshProfiles() })
  }
  // Local add-ons (installed from a localhost/LAN manifest, see LocalAddonManager) live entirely
  // on-device and have no backend counterpart. They're appended after the backend-managed list
  // and renumbered into one continuous `position` sequence so the rest of the app (home rows,
  // stream fan-out ordering, up/down arrows) can keep treating `uiState.addons` as a single list.
  private fun mergeWithLocalAddons(backendAddons: List<InstalledAddon>): List<InstalledAddon> =
    (backendAddons.sortedBy { it.position } + LocalAddonManager.list()).mapIndexed { index, addon -> addon.copy(position = index) }

  fun refreshAddons() {
    launchWork(
      onStart = { uiState = uiState.copy(addonsLoading = true, errorMessage = null) },
      block = {
        // Local add-ons have no server keeping their manifest fresh, so "refresh" re-fetches
        // each one directly from the device before merging in the backend-managed list.
        LocalAddonManager.list().forEach { local -> runCatching { LocalAddonManager.refresh(local.id) } }
        apiClient.fetchAddons(uiState.session, uiState.activeProfileId)
      },
      onSuccess = { addons ->
        val merged = mergeWithLocalAddons(addons)
        val mergedRows = mergeHomeCatalogRows(uiState.homeCatalogRows, merged)
        uiState = uiState.copy(
          addonsLoading = false,
          addons = merged,
          homeCatalogRows = mergedRows,
          homeSections = applyHomeCatalogLayout(uiState.allHomeSections, mergedRows, uiState.defaultAppCatalogsEnabled),
        )
        appSettingsStore.saveHomeCatalogRows(mergedRows)
        loadHome(force = true)
        pendingStreamLoad?.let { pending ->
          val currentDetail = uiState.detail
          if (currentDetail?.id == pending.detailId) loadStreamsForCurrentDetail(pending.episode)
          else pendingStreamLoad = null
        }
      },
      onFailure = { message ->
        // Even if the backend call fails (e.g. offline), keep showing whatever backend add-ons
        // we already had plus a freshly re-read local add-on list.
        val previousBackend = uiState.addons.filterNot { LocalAddonManager.isLocalAddonId(it.id) }
        uiState = uiState.copy(addonsLoading = false, errorMessage = message, addons = mergeWithLocalAddons(previousBackend))
        pendingStreamLoad?.let { pending ->
          if (uiState.detail?.id == pending.detailId) loadStreamsForCurrentDetail(pending.episode)
          else pendingStreamLoad = null
        }
      },
    )
  }

  fun installAddon(url: String) {
    val manifestUrl = normalizeAddonManifestUrl(url)
    if (manifestUrl == null) {
      uiState = uiState.copy(errorMessage = "Enter a valid Stremio add-on manifest URL.")
      return
    }
    // A manifest hosted on localhost/LAN can only ever be fetched by this device — the regular
    // backend-install flow would send the URL to StreamDek's server, where 127.0.0.1 means
    // something completely different. Install and serve those add-ons directly instead.
    if (isLocalNetworkUrl(manifestUrl)) {
      launchWork(
        onStart = { uiState = uiState.copy(addonsLoading = true, errorMessage = null) },
        block = { LocalAddonManager.install(manifestUrl) },
        onSuccess = {
          uiState = uiState.copy(infoMessage = "Add-on installed from your network.")
          refreshAddons()
        },
      )
      return
    }
    launchWork(
      onStart = { uiState = uiState.copy(addonsLoading = true, errorMessage = null) },
      block = { apiClient.installAddon(uiState.session, manifestUrl, uiState.activeProfileId) },
      onSuccess = {
        uiState = uiState.copy(infoMessage = "Add-on installed.")
        refreshAddons()
      },
    )
  }
  fun toggleAddon(addon: InstalledAddon, enabled: Boolean) {
    if (LocalAddonManager.isLocalAddonId(addon.id)) {
      LocalAddonManager.setEnabled(addon.id, enabled)
      refreshAddons()
      return
    }
    launchWork(onStart = {}, block = { apiClient.toggleAddon(uiState.session, addon.id, enabled, uiState.activeProfileId) }, onSuccess = { refreshAddons() })
  }
  fun uninstallAddon(addonId: String) {
    if (LocalAddonManager.isLocalAddonId(addonId)) {
      LocalAddonManager.remove(addonId)
      refreshAddons()
      return
    }
    launchWork(onStart = {}, block = { apiClient.uninstallAddon(uiState.session, addonId, uiState.activeProfileId) }, onSuccess = { refreshAddons() })
  }
  fun moveAddon(addonId: String, delta: Int) {
    val current = uiState.addons.sortedBy { it.position }
    val index = current.indexOfFirst { it.id == addonId }
    if (index < 0) return
    val target = index + delta
    if (target < 0 || target >= current.size) return
    val movingLocal = LocalAddonManager.isLocalAddonId(addonId)
    // Local and backend add-ons are reordered within their own group only — they're persisted
    // in two different places, so there's no single ordering call that could move one across
    // the other's boundary.
    if (movingLocal != LocalAddonManager.isLocalAddonId(current[target].id)) return
    if (movingLocal) {
      LocalAddonManager.move(addonId, delta)
      refreshAddons()
      return
    }
    val backendList = current.filterNot { LocalAddonManager.isLocalAddonId(it.id) }.toMutableList()
    val backendIndex = backendList.indexOfFirst { it.id == addonId }
    val backendTarget = (backendIndex + delta).coerceIn(0, backendList.lastIndex)
    if (backendIndex < 0 || backendIndex == backendTarget) return
    val item = backendList.removeAt(backendIndex)
    backendList.add(backendTarget, item)
    val reordered = backendList.mapIndexed { position, addon -> addon.copy(position = position) }
    uiState = uiState.copy(addons = mergeWithLocalAddons(reordered))
    launchWork(onStart = {}, block = { apiClient.reorderAddons(uiState.session, reordered.map { it.id }, uiState.activeProfileId) }, onSuccess = { refreshAddons() })
  }

  private fun publishM3uProgress(generation: Long, progress: M3uLoadProgress, aggregateFraction: Float? = progress.fraction) {
    viewModelScope.launch(Dispatchers.Main.immediate) {
      if (generation != m3uLoadGeneration) return@launch
      uiState = uiState.copy(
        m3uProgress = aggregateFraction?.coerceIn(0f, 1f),
        m3uStatusMessage = progress.message,
        m3uErrorMessage = null,
      )
    }
  }

  fun loadM3uPlaylists() {
    val sources = M3uPlaylistManager.list()
    val enabledSources = sources.filter { it.enabled }
    val generation = ++m3uLoadGeneration
    uiState = uiState.copy(m3uSources = sources)
    if (enabledSources.isEmpty()) {
      uiState = uiState.copy(
        m3uLoading = false,
        m3uProgress = null,
        m3uStatusMessage = null,
        m3uErrorMessage = null,
        m3uChannels = emptyList(),
        m3uVodItems = emptyList(),
      )
      return
    }
    uiState = uiState.copy(
      m3uLoading = true,
      m3uProgress = 0f,
      m3uStatusMessage = "Preparing ${enabledSources.size} playlist(s)…",
      m3uErrorMessage = null,
    )
    viewModelScope.launch {
      val liveChannels = mutableListOf<MediaItem>()
      val vodItems = mutableListOf<MediaItem>()
      val failures = mutableListOf<String>()
      enabledSources.forEachIndexed { index, source ->
        M3uPlaylistManager.fetchItems(source) { progress ->
          val aggregate = progress.fraction?.let { (index + it) / enabledSources.size.toFloat() }
          publishM3uProgress(
            generation,
            progress.copy(message = "Playlist ${index + 1} of ${enabledSources.size} • ${progress.message}"),
            aggregate,
          )
        }.onSuccess { content ->
          liveChannels += content.liveChannels
          vodItems += content.vodItems
        }.onFailure { error ->
          failures += "${source.name}: ${error.message ?: "Unable to load"}"
        }
      }
      if (generation != m3uLoadGeneration) return@launch
      val total = liveChannels.size + vodItems.size
      uiState = uiState.copy(
        m3uLoading = false,
        m3uProgress = 1f,
        m3uStatusMessage = "Loaded ${total.formattedItemCount()} items: ${liveChannels.size.formattedItemCount()} live and ${vodItems.size.formattedItemCount()} VOD",
        m3uErrorMessage = failures.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        m3uChannels = liveChannels,
        m3uVodItems = vodItems,
      )
    }
  }

  fun addM3uPlaylist(url: String, name: String) {
    val generation = ++m3uLoadGeneration
    uiState = uiState.copy(
      m3uLoading = true,
      m3uProgress = 0f,
      m3uStatusMessage = "Connecting to playlist…",
      m3uErrorMessage = null,
      errorMessage = null,
    )
    viewModelScope.launch {
      M3uPlaylistManager.add(url, name) { progress -> publishM3uProgress(generation, progress) }
        .onSuccess { result ->
          if (generation != m3uLoadGeneration) return@onSuccess
          val total = result.content.size
          uiState = uiState.copy(
            m3uLoading = false,
            m3uProgress = 1f,
            m3uStatusMessage = "Added ${result.source.name}: ${total.formattedItemCount()} items (${result.content.liveChannels.size.formattedItemCount()} live, ${result.content.vodItems.size.formattedItemCount()} VOD)",
            m3uErrorMessage = null,
            m3uSources = M3uPlaylistManager.list(),
            m3uChannels = uiState.m3uChannels + result.content.liveChannels,
            m3uVodItems = uiState.m3uVodItems + result.content.vodItems,
          )
        }
        .onFailure { error ->
          if (generation != m3uLoadGeneration) return@onFailure
          uiState = uiState.copy(
            m3uLoading = false,
            m3uProgress = null,
            m3uStatusMessage = null,
            m3uErrorMessage = error.message ?: "Unable to add that playlist.",
          )
        }
    }
  }

  fun removeM3uPlaylist(id: String) {
    M3uPlaylistManager.remove(id)
    loadM3uPlaylists()
  }

  fun setM3uPlaylistEnabled(id: String, enabled: Boolean) {
    M3uPlaylistManager.setEnabled(id, enabled)
    loadM3uPlaylists()
  }

  fun moveM3uPlaylist(id: String, delta: Int) {
    M3uPlaylistManager.move(id, delta)
    uiState = uiState.copy(m3uSources = M3uPlaylistManager.list())
  }
  fun setDownloadsEnabled(value: Boolean) {
    appSettingsStore.saveDownloadsEnabled(value)
    uiState = uiState.copy(downloadsEnabled = value)
    if (value) refreshDownloads()
  }

  fun refreshDownloads() {
    uiState = uiState.copy(downloads = StreamDekDownloads.currentDownloadEntries())
  }

  /** Only streams playable through Media3 with a plain HTTP(S) URL can be downloaded - no
   * torrent-only (`infoHash`) streams, and never live channels (there's no finite file). */
  fun isDownloadEligible(stream: AddonStream, isLive: Boolean): Boolean =
    uiState.downloadsEnabled && !isLive && stream.infoHash.isNullOrBlank() && !stream.url.isNullOrBlank() &&
      (stream.url.startsWith("http://", ignoreCase = true) || stream.url.startsWith("https://", ignoreCase = true))

  fun downloadStream(stream: AddonStream, title: String) {
    val url = stream.url ?: return
    if (!isDownloadEligible(stream, isLive = false)) return
    val id = downloadIdFor(stream)
    StreamDekDownloads.startDownload(id, url, title, null)
    uiState = uiState.copy(infoMessage = "Downloading \"$title\"…")
    refreshDownloads()
  }

  fun removeDownload(id: String) {
    StreamDekDownloads.removeDownload(id)
    refreshDownloads()
  }

  /** Plays a completed download back through the regular player. Forces the Media3 engine
   * (not "Auto", which could fall back to mpv) since only Media3's data source chain knows how
   * to read from the shared download cache - mpv has no idea it exists. */
  fun playDownload(entry: DownloadEntry) {
    if (entry.state != DownloadState.COMPLETED) return
    uiState = uiState.copy(
      returnToDetailAfterPlayer = false,
      playerSession = PlayerSession(
        url = entry.url,
        title = entry.title,
        subtitle = null,
        sourceLabel = "Downloaded",
        mediaId = entry.id,
        mediaType = "movie",
        isLive = false,
        playerEngine = "Media3",
      ),
    )
  }

  fun refreshDebridAccounts() {
    val session = uiState.session ?: return
    launchWork(
      onStart = { uiState = uiState.copy(debridLoading = true, errorMessage = null) },
      block = { apiClient.fetchDebridAccounts(session) },
      onSuccess = { accounts -> uiState = uiState.copy(debridLoading = false, debridAccounts = accounts.sortedBy { it.priority }) },
      onFailure = { message -> uiState = uiState.copy(debridLoading = false, errorMessage = message) },
    )
  }

  fun addDebridAccount(provider: String, apiKey: String) {
    val session = uiState.session ?: return
    launchWork(onStart = { uiState = uiState.copy(debridLoading = true) }, block = { apiClient.addDebridAccount(session, provider, apiKey) }, onSuccess = { refreshDebridAccounts() })
  }
  fun removeDebridAccount(provider: String) { val session = uiState.session ?: return; launchWork(onStart = {}, block = { apiClient.removeDebridAccount(session, provider) }, onSuccess = { refreshDebridAccounts() }) }
  fun moveDebridAccount(provider: String, delta: Int) {
    val session = uiState.session ?: return
    val current = uiState.debridAccounts.sortedBy { it.priority }.toMutableList()
    val index = current.indexOfFirst { it.provider == provider }
    val target = (index + delta).coerceIn(0, current.lastIndex)
    if (index < 0 || index == target) return
    val item = current.removeAt(index)
    current.add(target, item)
    val reordered = current.mapIndexed { priority, account -> account.copy(priority = priority) }
    uiState = uiState.copy(debridAccounts = reordered)
    launchWork(onStart = {}, block = { apiClient.reorderDebridAccounts(session, reordered.map { it.provider }) }, onSuccess = { refreshDebridAccounts() })
  }

  fun requestTraktDeviceCode() {
    launchWork(onStart = { uiState = uiState.copy(traktLoading = true) }, block = { apiClient.requestTraktDeviceCode() }, onSuccess = { code -> uiState = uiState.copy(traktLoading = false, pendingDeviceCode = code) })
  }
  fun pollTraktAuthorization() {
    val session = uiState.session ?: return
    val profileId = uiState.activeProfileId ?: return
    val code = uiState.pendingDeviceCode ?: return
    launchWork(onStart = { uiState = uiState.copy(traktLoading = true) }, block = { apiClient.pollTraktDeviceCode(session, profileId, code.deviceCode) }, onSuccess = { refreshTraktData() })
  }
  fun disconnectTrakt() { val session = uiState.session ?: return; val profileId = uiState.activeProfileId ?: return; launchWork(onStart = {}, block = { apiClient.disconnectTrakt(session, profileId) }, onSuccess = { refreshTraktData() }) }

  fun refreshConnectedServices() {
    val session = uiState.session ?: run {
      uiState = uiState.copy(infoMessage = "Sign in to refresh settings from cloud sync.")
      return
    }
    if (uiState.syncRefreshing) return
    if (!cloudSyncAllowed()) {
      uiState = uiState.copy(infoMessage = "Sync is paused on cellular. Enable Sync on Cellular to refresh account and addon data.")
      return
    }
    launchWork(
      onStart = { uiState = uiState.copy(syncRefreshing = true, errorMessage = null, infoMessage = null) },
      block = {
        coroutineScope {
          val preferences = async { apiClient.fetchCloudPlaybackPreferences(session, uiState.activeProfileId) }
          val minimumAnimation = async { delay(500) }
          minimumAnimation.await()
          preferences.await()
        }
      },
      onSuccess = { preferences ->
        applyCloudPlaybackPreferences(preferences)
        uiState = uiState.copy(syncRefreshing = false, infoMessage = "Cloud settings refreshed.")
        refreshProfiles(showLoading = false, refreshScopedData = false)
        refreshAddons()
        refreshDebridAccounts()
        refreshTraktData()
        loadHome(force = true)
      },
      onFailure = { message -> uiState = uiState.copy(syncRefreshing = false, errorMessage = message) },
    )
  }

  private fun applyCloudPlaybackPreferences(preferences: CloudPlaybackPreferences) {
    val appAppearance = preferences.appAppearance?.let { runCatching { AppAppearance.valueOf(it) }.getOrNull() }
    val themePreset = preferences.themePreset?.let { runCatching { AppThemePreset.valueOf(it) }.getOrNull() }
    val headerStyle = preferences.headerStyle?.let { runCatching { HeaderStyle.valueOf(it) }.getOrNull() }
    val detailPageStyle = preferences.detailPageStyle?.let { runCatching { DetailPageStyle.valueOf(it) }.getOrNull() }
    val continueWatchingStyle = preferences.continueWatchingStyle?.let { runCatching { ContinueWatchingStyle.valueOf(it) }.getOrNull() }
    val seasonTabStyle = preferences.seasonTabStyle?.let { runCatching { SeasonTabStyle.valueOf(it) }.getOrNull() }
    val decoderMode = preferences.decoderMode?.let(::normalizeDecoderModeSetting)
    val renderSurface = preferences.renderSurface?.let(::normalizeRenderSurfaceSetting)
    val playerEngine = preferences.playerEngine?.let(::normalizePlayerEngineSetting)
    val preferredAudioLanguage = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }?.audioLanguage?.takeIf { it.isNotBlank() }?.let(::normalizePreferredAudioLanguage) ?: preferences.preferredAudioLanguage?.let(::normalizePreferredAudioLanguage)
    val ratingProviders = preferences.enabledRatingProviders?.map { it.trim().lowercase() }?.filter(String::isNotBlank)?.toSet()
    val fusionBadgeUrls = preferences.fusionBadgeUrls?.distinct()?.take(MAX_FUSION_BADGE_URLS)
    val activeFusionBadgeUrl = preferences.activeFusionBadgeUrl?.takeIf { it in (fusionBadgeUrls ?: uiState.fusionBadgeUrls) }
    val homeCatalogRows = preferences.homeCatalogRowsJson?.let(::parseHomeCatalogRows)

    appAppearance?.let(appSettingsStore::saveAppAppearance)
    themePreset?.let(appSettingsStore::saveThemePreset)
    headerStyle?.let(appSettingsStore::saveHeaderStyle)
    preferences.showNavLabels?.let(appSettingsStore::saveShowNavLabels)
    preferences.collapsibleNavigationEnabled?.let(appSettingsStore::saveCollapsibleNavigationEnabled)
    preferences.navigationAutoCollapseSeconds?.let(appSettingsStore::saveNavigationAutoCollapseSeconds)
    preferences.syncOnCellular?.let(appSettingsStore::saveSyncOnCellular)
    detailPageStyle?.let(appSettingsStore::saveDetailPageStyle)
    continueWatchingStyle?.let(appSettingsStore::saveContinueWatchingStyle)
    preferences.liveLandscapeCards?.let(appSettingsStore::saveLiveLandscapeCards)
    preferences.liveFavouriteDrawerCards?.let(appSettingsStore::saveLiveFavouriteDrawerCards)
    preferences.showHeroSynopsis?.let(appSettingsStore::saveShowHeroSynopsis)
    preferences.vividAmbient?.let(appSettingsStore::saveVividAmbient)
    preferences.ambientTintPercent?.let(appSettingsStore::saveAmbientTintPercent)
    preferences.defaultAppCatalogsEnabled?.let(appSettingsStore::saveDefaultAppCatalogsEnabled)
    homeCatalogRows?.let(appSettingsStore::saveHomeCatalogRows)
    seasonTabStyle?.let(appSettingsStore::saveSeasonTabStyle)
    preferences.heroTrailerAutoplay?.let(appSettingsStore::saveHeroTrailerAutoplay)
    preferences.heroTrailerResolution?.let(appSettingsStore::saveHeroTrailerResolution)
    preferences.ratingsEnabled?.let(appSettingsStore::saveRatingsEnabled)
    preferences.externalRatingsEnabled?.let(appSettingsStore::saveExternalRatingsEnabled)
    ratingProviders?.let(appSettingsStore::saveEnabledRatingProviders)
    preferences.mdblistApiKey?.let(appSettingsStore::saveMdblistApiKey)
    preferences.pictureInPictureEnabled?.let(appSettingsStore::savePictureInPictureEnabled)
    decoderMode?.let(appSettingsStore::saveDecoderMode)
    renderSurface?.let(appSettingsStore::saveRenderSurface)
    playerEngine?.let(appSettingsStore::savePlayerEngine)
    preferredAudioLanguage?.let(appSettingsStore::savePreferredAudioLanguage)
    preferences.skipIntroEnabled?.let(appSettingsStore::saveSkipIntroEnabled)
    preferences.skipRecapEnabled?.let(appSettingsStore::saveSkipRecapEnabled)
    preferences.skipEndingEnabled?.let(appSettingsStore::saveSkipEndingEnabled)
    preferences.autoPlayNextEpisode?.let(appSettingsStore::saveAutoPlayNextEpisode)
    preferences.preferBingeGroup?.let(appSettingsStore::savePreferBingeGroup)
    preferences.autoLoadSubtitles?.let(appSettingsStore::saveAutoLoadSubtitles)
    preferences.nextEpisodeThresholdMode?.let(appSettingsStore::saveNextEpisodeThresholdMode)
    preferences.nextEpisodeThresholdPercent?.let(appSettingsStore::saveNextEpisodeThresholdPercent)
    preferences.nextEpisodeThresholdMinutes?.let(appSettingsStore::saveNextEpisodeThresholdMinutes)
    preferences.showStreamsList?.let(appSettingsStore::saveShowStreamsList)
    preferences.rememberLastSource?.let(appSettingsStore::saveRememberLastSource)
    preferences.blurUnwatchedEpisodes?.let(appSettingsStore::saveBlurUnwatchedEpisodes)
    preferences.fusionBadgesEnabled?.let(appSettingsStore::saveFusionBadges)
    preferences.showSizeBadges?.let(appSettingsStore::saveShowSizeBadges)
    preferences.showAddonTmdbRatings?.let(appSettingsStore::saveShowAddonTmdbRatings)
    preferences.preferredQuality?.let(appSettingsStore::savePreferredQuality)
    preferences.maxFileSizeGb?.let(appSettingsStore::saveMaxFileSizeGb)
    preferences.badgePosition?.let(appSettingsStore::saveBadgePosition)
    fusionBadgeUrls?.let { appSettingsStore.saveFusionBadgeUrls(it, activeFusionBadgeUrl) }
    preferences.autoUpdateChecksEnabled?.let(appSettingsStore::saveAutoUpdateChecks)

    uiState = uiState.copy(
      appAppearance = appAppearance ?: uiState.appAppearance,
      themePreset = themePreset ?: uiState.themePreset,
      headerStyle = headerStyle ?: uiState.headerStyle,
      showNavLabels = preferences.showNavLabels ?: uiState.showNavLabels,
      collapsibleNavigationEnabled = preferences.collapsibleNavigationEnabled ?: uiState.collapsibleNavigationEnabled,
      navigationAutoCollapseSeconds = preferences.navigationAutoCollapseSeconds?.coerceIn(2, 15) ?: uiState.navigationAutoCollapseSeconds,
      syncOnCellular = preferences.syncOnCellular ?: uiState.syncOnCellular,
      detailPageStyle = detailPageStyle ?: uiState.detailPageStyle,
      continueWatchingStyle = continueWatchingStyle ?: uiState.continueWatchingStyle,
      liveLandscapeCards = preferences.liveLandscapeCards ?: uiState.liveLandscapeCards,
      liveFavouriteDrawerCards = preferences.liveFavouriteDrawerCards ?: uiState.liveFavouriteDrawerCards,
      showHeroSynopsis = preferences.showHeroSynopsis ?: uiState.showHeroSynopsis,
      vividAmbient = preferences.vividAmbient ?: uiState.vividAmbient,
      ambientTintPercent = (preferences.ambientTintPercent ?: uiState.ambientTintPercent).coerceIn(20, 100),
      defaultAppCatalogsEnabled = preferences.defaultAppCatalogsEnabled ?: uiState.defaultAppCatalogsEnabled,
      homeCatalogRows = homeCatalogRows ?: uiState.homeCatalogRows,
      seasonTabStyle = seasonTabStyle ?: uiState.seasonTabStyle,
      heroTrailerAutoplay = preferences.heroTrailerAutoplay ?: uiState.heroTrailerAutoplay,
      heroTrailerResolution = preferences.heroTrailerResolution?.coerceIn(360, 1080) ?: uiState.heroTrailerResolution,
      ratingsEnabled = preferences.ratingsEnabled ?: uiState.ratingsEnabled,
      externalRatingsEnabled = preferences.externalRatingsEnabled ?: uiState.externalRatingsEnabled,
      enabledRatingProviders = ratingProviders ?: uiState.enabledRatingProviders,
      mdblistApiKey = preferences.mdblistApiKey?.trim() ?: uiState.mdblistApiKey,
      pictureInPictureEnabled = preferences.pictureInPictureEnabled ?: uiState.pictureInPictureEnabled,
      decoderMode = decoderMode ?: uiState.decoderMode,
      renderSurface = renderSurface ?: uiState.renderSurface,
      playerEngine = playerEngine ?: uiState.playerEngine,
      preferredAudioLanguage = preferredAudioLanguage ?: uiState.preferredAudioLanguage,
      skipIntroEnabled = preferences.skipIntroEnabled ?: uiState.skipIntroEnabled,
      skipRecapEnabled = preferences.skipRecapEnabled ?: uiState.skipRecapEnabled,
      skipEndingEnabled = preferences.skipEndingEnabled ?: uiState.skipEndingEnabled,
      autoPlayNextEpisode = preferences.autoPlayNextEpisode ?: uiState.autoPlayNextEpisode,
      preferBingeGroup = preferences.preferBingeGroup ?: uiState.preferBingeGroup,
      autoLoadSubtitles = preferences.autoLoadSubtitles ?: uiState.autoLoadSubtitles,
      nextEpisodeThresholdMode = preferences.nextEpisodeThresholdMode ?: uiState.nextEpisodeThresholdMode,
      nextEpisodeThresholdPercent = preferences.nextEpisodeThresholdPercent?.coerceIn(50, 99) ?: uiState.nextEpisodeThresholdPercent,
      nextEpisodeThresholdMinutes = preferences.nextEpisodeThresholdMinutes?.coerceIn(1, 15) ?: uiState.nextEpisodeThresholdMinutes,
      showStreamsList = preferences.showStreamsList ?: uiState.showStreamsList,
      rememberLastSource = preferences.rememberLastSource ?: uiState.rememberLastSource,
      blurUnwatchedEpisodes = preferences.blurUnwatchedEpisodes ?: uiState.blurUnwatchedEpisodes,
      fusionBadgesEnabled = preferences.fusionBadgesEnabled ?: uiState.fusionBadgesEnabled,
      showSizeBadges = preferences.showSizeBadges ?: uiState.showSizeBadges,
      showAddonTmdbRatings = preferences.showAddonTmdbRatings ?: uiState.showAddonTmdbRatings,
      preferredQuality = preferences.preferredQuality ?: uiState.preferredQuality,
      maxFileSizeGb = preferences.maxFileSizeGb ?: uiState.maxFileSizeGb,
      badgePosition = preferences.badgePosition ?: uiState.badgePosition,
      fusionBadgeUrls = fusionBadgeUrls ?: uiState.fusionBadgeUrls,
      activeFusionBadgeUrl = if (fusionBadgeUrls != null) activeFusionBadgeUrl else uiState.activeFusionBadgeUrl,
      autoUpdateChecksEnabled = preferences.autoUpdateChecksEnabled ?: uiState.autoUpdateChecksEnabled,
    )
    if (homeCatalogRows != null) uiState = uiState.copy(homeSections = applyHomeCatalogLayout(uiState.allHomeSections, homeCatalogRows, uiState.defaultAppCatalogsEnabled))
    if (fusionBadgeUrls != null) refreshFusionBadgeSources()
    uiState.detail?.let(::refreshExternalRatings)
  }
  fun refreshTraktData() {
    val session = uiState.session
    val profileId = uiState.activeProfileId
    launchWork(
      onStart = { uiState = uiState.copy(traktLoading = true, errorMessage = null) },
      block = {
        val trending = apiClient.fetchTraktTrending().getOrElse { emptyList() }
        if (session == null || profileId == null) {
          Result.success(TraktDashboardState(TraktStatus(false, null), emptyList(), emptyList(), emptyList(), trending))
        } else {
          val status = apiClient.fetchTraktStatus(session, profileId).getOrElse { TraktStatus(false, null) }
          val cw = if (status.connected) apiClient.fetchTraktContinueWatching(session, profileId).getOrElse { emptyList() } else emptyList()
          val wl = if (status.connected) apiClient.fetchTraktWatchlist(session, profileId).getOrElse { emptyList() } else emptyList()
          val recs = if (status.connected) apiClient.fetchTraktRecommendations(session, profileId).getOrElse { emptyList() } else emptyList()
          Result.success(TraktDashboardState(status, cw, wl, recs, trending))
        }
      },
      onSuccess = { dashboard ->
        val watchedTitles = watchedTitleStore.load(activeOwnerKey() ?: GUEST_OWNER_KEY)
        uiState = uiState.copy(
          traktLoading = false,
          traktStatus = dashboard.status,
          traktContinueWatching = dashboard.continueWatching.filterNot { watchedTitleKey(it.type, it.tmdbId?.toString() ?: it.id) in watchedTitles },
          traktWatchlist = dashboard.watchlist,
          traktRecommendations = dashboard.recommendations,
          traktTrending = dashboard.trending,
          pendingDeviceCode = null,
          mergedWatchlist = mergeWatchlistWithLocal(dashboard.watchlist),
        )
      },
      onFailure = { message -> uiState = uiState.copy(traktLoading = false, errorMessage = message) },
    )
  }

  fun toggleWatchlist(item: MediaItem) {
    val ownerKey = activeOwnerKey() ?: return
    val current = loadLocalWatchlist().toMutableList()
    val index = current.indexOfFirst { it.id == item.id && it.type == item.type }
    val remove = index >= 0
    if (remove) current.removeAt(index) else current.add(0, item.copy(addedAt = item.addedAt ?: System.currentTimeMillis()))
    watchlistStore.save(ownerKey, current)
    uiState = uiState.copy(mergedWatchlist = mergeWatchlistWithLocal(uiState.traktWatchlist, current))
    val session = uiState.session
    val profileId = uiState.activeProfileId
    if (session != null && profileId != null && uiState.traktStatus.connected) {
      launchWork(onStart = {}, block = { apiClient.syncWatchlist(session, profileId, item, remove) }, onSuccess = { refreshTraktData() })
    }
  }

  fun toggleFavouriteChannel(item: MediaItem) {
    if (!item.isLiveCatalogItem()) return
    val ownerKey = activeOwnerKey() ?: return
    val current = loadLocalFavouriteChannels().toMutableList()
    val index = current.indexOfFirst { it.id == item.id }
    if (index >= 0) current.removeAt(index) else current.add(0, item.copy(addedAt = item.addedAt ?: System.currentTimeMillis()))
    favouriteChannelStore.save(ownerKey, current)
    uiState = uiState.copy(favouriteChannels = current)
  }

  fun clearFavouriteChannels() {
    val ownerKey = activeOwnerKey() ?: return
    favouriteChannelStore.clear(ownerKey)
    uiState = uiState.copy(favouriteChannels = emptyList())
  }

  fun toggleFavouriteChannelForCurrentSession() {
    val session = uiState.playerSession ?: return
    if (!session.isLive) return
    val item = uiState.detailFallbackItem?.takeIf { it.id == session.mediaId } ?: MediaItem(
      id = session.mediaId,
      type = session.mediaType,
      title = session.title,
      year = session.year?.toString(),
      poster = session.poster,
      backdrop = session.backdrop,
      rating = null,
      description = session.synopsis.orEmpty(),
      sourceAddonId = session.currentStream?.addonId,
      sourceAddonName = session.currentStream?.addonName,
    )
    toggleFavouriteChannel(item)
  }

  fun markWatched(item: MediaItem) {
    val ownerKey = activeOwnerKey() ?: return
    watchedTitleStore.add(ownerKey, item)
    playbackResumeStore.removeTitle(ownerKey, item.id, item.type)
    if (item.type == "movie") {
      watchedMovieStore.save(ownerKey, (watchedMovieStore.load(ownerKey) + item.id).distinct())
    }

    val localWatchlist = loadLocalWatchlist().filterNot { it.id == item.id && it.type == item.type }
    watchlistStore.save(ownerKey, localWatchlist)
    uiState = uiState.copy(
      mergedWatchlist = uiState.mergedWatchlist.filterNot { it.id == item.id && normalizedMediaType(it.type) == normalizedMediaType(item.type) },
      localContinueWatching = loadLocalContinueWatching(), localResumeEntries = loadResumeEntries(),
      traktContinueWatching = uiState.traktContinueWatching.filterNot { (it.tmdbId?.toString() ?: it.id) == item.id && normalizedMediaType(it.type) == normalizedMediaType(item.type) },
      infoMessage = "${item.title} marked as watched.",
    )

    val session = uiState.session
    val profileId = uiState.activeProfileId
    if (session != null && profileId != null && uiState.traktStatus.connected) {
      viewModelScope.launch {
        apiClient.syncWatchlist(session, profileId, item, remove = true)
        if (item.type == "movie") {
          apiClient.fetchDetails(item.type, item.id, item.title, item.year)
            .onSuccess { detail -> apiClient.syncWatchedMovie(session, profileId, detail) }
        }
        refreshTraktData()
      }
    }
  }

  fun restartFromBeginning(item: MediaItem) {
    val ownerKey = activeOwnerKey() ?: GUEST_OWNER_KEY
    playbackResumeStore.removeTitle(ownerKey, item.id, item.type)
    uiState = uiState.copy(
      localContinueWatching = loadLocalContinueWatching(),
      localResumeEntries = loadResumeEntries(),
      traktContinueWatching = uiState.traktContinueWatching.filterNot {
        (it.tmdbId?.toString() ?: it.id) == item.id && normalizedMediaType(it.type) == normalizedMediaType(item.type)
      },
      infoMessage = "${item.title} will start from the beginning.",
    )
  }
  fun setAutoUpdateChecks(enabled: Boolean) {
    appSettingsStore.saveAutoUpdateChecks(enabled)
    uiState = uiState.copy(autoUpdateChecksEnabled = enabled)
    syncCloudPreferences()
    if (enabled) checkForUpdates(manual = false)
  }

  fun checkForUpdates(manual: Boolean = true) {
    if (uiState.updateChecking) return
    launchWork(
      onStart = { uiState = uiState.copy(updateChecking = true, updateErrorMessage = null, updateStatusMessage = if (manual) "Checking for updates..." else null) },
      block = { apiClient.fetchLatestMobileUpdate() },
      onSuccess = { release ->
        val available = release.versionCode > BuildConfig.VERSION_CODE || compareSemanticVersions(release.versionName, BuildConfig.VERSION_NAME) > 0
        val mandatory = release.required || (release.minSupportedVersionCode?.let { BuildConfig.VERSION_CODE < it } == true)
        uiState = uiState.copy(updateChecking = false, availableUpdate = release.takeIf { available }, updatePromptVisible = available && (manual || uiState.autoUpdateChecksEnabled || mandatory), updateStatusMessage = if (available) "Version ${release.versionName} is available." else if (manual) "You are already on the latest version." else null, updateErrorMessage = null)
      },
      onFailure = { message -> uiState = uiState.copy(updateChecking = false, updateStatusMessage = null, updateErrorMessage = message) },
    )
  }

  fun dismissUpdatePrompt() {
    val release = uiState.availableUpdate
    val mandatory = release?.required == true || (release?.minSupportedVersionCode?.let { BuildConfig.VERSION_CODE < it } == true)
    if (!mandatory && !uiState.updateDownloading) uiState = uiState.copy(updatePromptVisible = false)
  }

  fun startUpdate() {
    val release = uiState.availableUpdate ?: return
    if (uiState.updateDownloading) return
    val context = getApplication<Application>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
      context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${BuildConfig.APPLICATION_ID}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      uiState = uiState.copy(updatePromptVisible = true, updateStatusMessage = "Allow installs from StreamDek, then tap Update Now again.")
      return
    }
    val safeName = release.assetName?.replace(Regex("[^a-zA-Z0-9._-]"), "-") ?: "streamdek-${release.versionCode}.apk"
    val destination = java.io.File(context.cacheDir, "updates/$safeName")
    launchWork(
      onStart = { uiState = uiState.copy(updateDownloading = true, updateProgress = 0f, updateErrorMessage = null, updateStatusMessage = "Downloading version ${release.versionName}...") },
      block = { apiClient.downloadUpdate(release, destination) { downloaded, total ->
        val progress = total?.takeIf { it > 0L }?.let { (downloaded.toDouble() / it.toDouble()).toFloat().coerceIn(0f, 1f) }
        viewModelScope.launch(Dispatchers.Main) { uiState = uiState.copy(updateProgress = progress) }
      } },
      onSuccess = { apk ->
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        uiState = uiState.copy(updateDownloading = false, updateProgress = null, updateStatusMessage = "Download complete. Opening the installer...")
        context.startActivity(intent)
      },
      onFailure = { message -> uiState = uiState.copy(updateDownloading = false, updateProgress = null, updateErrorMessage = message, updateStatusMessage = null, updatePromptVisible = true) },
    )
  }

  fun apiBaseUrl(): String = apiClient.apiBaseUrl

  private fun cloudSyncAllowed(): Boolean {
    if (uiState.syncOnCellular) return true
    val manager = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return true
    return !capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
  }
private fun watchedOwnerKey(session: AuthSession?, activeProfileId: String?): String {
  val userId = session?.user?.uid
  return when {
    userId.isNullOrBlank() -> activeProfileId?.let { "guest:$it" } ?: GUEST_OWNER_KEY
    activeProfileId.isNullOrBlank() -> userId
    else -> "$userId:$activeProfileId"
  }
}

  private fun syncCloudPreferences(force: Boolean = false) {
    val session = uiState.session ?: return
    if (!force && !cloudSyncAllowed()) return
    val preferences = CloudPlaybackPreferences(
      appAppearance = uiState.appAppearance.name,
      themePreset = uiState.themePreset.name,
      headerStyle = uiState.headerStyle.name,
      showNavLabels = uiState.showNavLabels,
      collapsibleNavigationEnabled = uiState.collapsibleNavigationEnabled,
      navigationAutoCollapseSeconds = uiState.navigationAutoCollapseSeconds,
      syncOnCellular = uiState.syncOnCellular,
      detailPageStyle = uiState.detailPageStyle.name,
      continueWatchingStyle = uiState.continueWatchingStyle.name,
      liveLandscapeCards = uiState.liveLandscapeCards,
      liveFavouriteDrawerCards = uiState.liveFavouriteDrawerCards,
      showHeroSynopsis = uiState.showHeroSynopsis,
      vividAmbient = uiState.vividAmbient,
      ambientTintPercent = uiState.ambientTintPercent,
      defaultAppCatalogsEnabled = uiState.defaultAppCatalogsEnabled,
      homeCatalogRowsJson = serializeHomeCatalogRows(uiState.homeCatalogRows),
      seasonTabStyle = uiState.seasonTabStyle.name,
      heroTrailerAutoplay = uiState.heroTrailerAutoplay,
      heroTrailerResolution = uiState.heroTrailerResolution,
      ratingsEnabled = uiState.ratingsEnabled,
      externalRatingsEnabled = uiState.externalRatingsEnabled,
      enabledRatingProviders = uiState.enabledRatingProviders.sorted(),
      mdblistApiKey = uiState.mdblistApiKey,
      pictureInPictureEnabled = uiState.pictureInPictureEnabled,
      decoderMode = uiState.decoderMode,
      renderSurface = uiState.renderSurface,
      playerEngine = uiState.playerEngine,
      preferredAudioLanguage = uiState.preferredAudioLanguage,
      skipIntroEnabled = uiState.skipIntroEnabled,
      skipRecapEnabled = uiState.skipRecapEnabled,
      skipEndingEnabled = uiState.skipEndingEnabled,
      autoPlayNextEpisode = uiState.autoPlayNextEpisode,
      preferBingeGroup = uiState.preferBingeGroup,
      autoLoadSubtitles = uiState.autoLoadSubtitles,
      nextEpisodeThresholdMode = uiState.nextEpisodeThresholdMode,
      nextEpisodeThresholdPercent = uiState.nextEpisodeThresholdPercent,
      nextEpisodeThresholdMinutes = uiState.nextEpisodeThresholdMinutes,
      showStreamsList = uiState.showStreamsList,
      rememberLastSource = uiState.rememberLastSource,
      blurUnwatchedEpisodes = uiState.blurUnwatchedEpisodes,
      fusionBadgesEnabled = uiState.fusionBadgesEnabled,
      showSizeBadges = uiState.showSizeBadges,
      showAddonTmdbRatings = uiState.showAddonTmdbRatings,
      preferredQuality = uiState.preferredQuality,
      maxFileSizeGb = uiState.maxFileSizeGb,
      badgePosition = uiState.badgePosition,
      fusionBadgeUrls = uiState.fusionBadgeUrls,
      activeFusionBadgeUrl = uiState.activeFusionBadgeUrl,
      autoUpdateChecksEnabled = uiState.autoUpdateChecksEnabled,
    )
    viewModelScope.launch { apiClient.patchCloudPreferences(session, preferences, uiState.activeProfileId) }
  }
  fun setAppAppearance(value: AppAppearance) { appSettingsStore.saveAppAppearance(value); uiState = uiState.copy(appAppearance = value); syncCloudPreferences() }
  fun setAppLanguage(value: String) {
    val normalized = normalizeAppLanguage(value)
    appSettingsStore.saveAppLanguage(normalized)
    uiState = uiState.copy(appLanguage = normalized)
  }
  fun setThemePreset(value: AppThemePreset) { appSettingsStore.saveThemePreset(value); uiState = uiState.copy(themePreset = value); syncCloudPreferences() }
  fun setHeaderStyle(value: HeaderStyle) { appSettingsStore.saveHeaderStyle(value); uiState = uiState.copy(headerStyle = value); syncCloudPreferences() }
  fun setPictureInPictureEnabled(value: Boolean) { appSettingsStore.savePictureInPictureEnabled(value); uiState = uiState.copy(pictureInPictureEnabled = value); syncCloudPreferences() }
  fun setDecoderMode(value: String) { appSettingsStore.saveDecoderMode(value); uiState = uiState.copy(decoderMode = value); syncCloudPreferences() }
  fun setRenderSurface(value: String) { appSettingsStore.saveRenderSurface(value); uiState = uiState.copy(renderSurface = value); syncCloudPreferences() }
  fun setPlayerEngine(value: String) { val normalized = normalizePlayerEngineSetting(value); appSettingsStore.savePlayerEngine(normalized); uiState = uiState.copy(playerEngine = normalized); syncCloudPreferences() }
  fun setPreferredAudioLanguage(value: String) {
    val normalized = normalizePreferredAudioLanguage(value)
    val profileId = uiState.activeProfileId
    val updatedProfiles = if (profileId == null) uiState.profiles else uiState.profiles.map { profile ->
      if (profile.id == profileId) profile.copy(audioLanguage = normalized) else profile
    }
    uiState = uiState.copy(preferredAudioLanguage = normalized, profiles = updatedProfiles)
    val session = uiState.session
    if (profileId != null && session == null) {
      guestProfileStore.save(updatedProfiles)
    } else if (profileId != null && session != null) {
      viewModelScope.launch {
        apiClient.updateProfileAudioLanguage(session, profileId, normalized).onFailure { error ->
          uiState = uiState.copy(errorMessage = error.message ?: "Could not save the profile audio language.")
        }
      }
    } else {
      appSettingsStore.savePreferredAudioLanguage(normalized)
      syncCloudPreferences()
    }
  }
  fun setDetailPageStyle(style: DetailPageStyle) { appSettingsStore.saveDetailPageStyle(style); uiState = uiState.copy(detailPageStyle = style); syncCloudPreferences() }
  fun setSeasonTabStyle(style: SeasonTabStyle) { appSettingsStore.saveSeasonTabStyle(style); uiState = uiState.copy(seasonTabStyle = style); syncCloudPreferences() }
  fun setShowNavLabels(value: Boolean) { appSettingsStore.saveShowNavLabels(value); uiState = uiState.copy(showNavLabels = value); syncCloudPreferences() }
  fun setCollapsibleNavigationEnabled(value: Boolean) { appSettingsStore.saveCollapsibleNavigationEnabled(value); uiState = uiState.copy(collapsibleNavigationEnabled = value); syncCloudPreferences() }
  fun setNavigationAutoCollapseSeconds(value: Int) {
    val seconds = value.coerceIn(2, 15)
    appSettingsStore.saveNavigationAutoCollapseSeconds(seconds)
    uiState = uiState.copy(navigationAutoCollapseSeconds = seconds)
    syncCloudPreferences()
  }
  fun setShowStreamsList(value: Boolean) { appSettingsStore.saveShowStreamsList(value); uiState = uiState.copy(showStreamsList = value); syncCloudPreferences() }
  fun setHeroTrailerAutoplay(value: Boolean) { appSettingsStore.saveHeroTrailerAutoplay(value); uiState = uiState.copy(heroTrailerAutoplay = value); syncCloudPreferences() }
  fun setHeroTrailerResolution(value: Int) { appSettingsStore.saveHeroTrailerResolution(value); uiState = uiState.copy(heroTrailerResolution = value); syncCloudPreferences() }
  fun setShowHeroSynopsis(value: Boolean) { appSettingsStore.saveShowHeroSynopsis(value); uiState = uiState.copy(showHeroSynopsis = value); syncCloudPreferences() }
  fun setContinueWatchingStyle(style: ContinueWatchingStyle) { appSettingsStore.saveContinueWatchingStyle(style); uiState = uiState.copy(continueWatchingStyle = style); syncCloudPreferences() }
  fun setLiveLandscapeCards(value: Boolean) { appSettingsStore.saveLiveLandscapeCards(value); uiState = uiState.copy(liveLandscapeCards = value); syncCloudPreferences() }
  fun setLiveFavouriteDrawerCards(value: Boolean) { appSettingsStore.saveLiveFavouriteDrawerCards(value); uiState = uiState.copy(liveFavouriteDrawerCards = value); syncCloudPreferences() }
  fun setRememberLastSource(value: Boolean) { appSettingsStore.saveRememberLastSource(value); uiState = uiState.copy(rememberLastSource = value); syncCloudPreferences() }
  fun setSyncOnCellular(value: Boolean) { appSettingsStore.saveSyncOnCellular(value); uiState = uiState.copy(syncOnCellular = value); syncCloudPreferences(force = true) }
  fun setSkipIntroEnabled(value: Boolean) { appSettingsStore.saveSkipIntroEnabled(value); uiState = uiState.copy(skipIntroEnabled = value); syncCloudPreferences() }
  fun setSkipRecapEnabled(value: Boolean) { appSettingsStore.saveSkipRecapEnabled(value); uiState = uiState.copy(skipRecapEnabled = value); syncCloudPreferences() }
  fun setSkipEndingEnabled(value: Boolean) { appSettingsStore.saveSkipEndingEnabled(value); uiState = uiState.copy(skipEndingEnabled = value); syncCloudPreferences() }
  fun setAutoPlayNextEpisode(value: Boolean) { appSettingsStore.saveAutoPlayNextEpisode(value); uiState = uiState.copy(autoPlayNextEpisode = value); syncCloudPreferences() }
  fun setPreferBingeGroup(value: Boolean) { appSettingsStore.savePreferBingeGroup(value); uiState = uiState.copy(preferBingeGroup = value); syncCloudPreferences() }
  fun setNextEpisodeThresholdMode(value: String) { appSettingsStore.saveNextEpisodeThresholdMode(value); uiState = uiState.copy(nextEpisodeThresholdMode = value); syncCloudPreferences() }
  fun setNextEpisodeThresholdPercent(value: Int) { appSettingsStore.saveNextEpisodeThresholdPercent(value); uiState = uiState.copy(nextEpisodeThresholdPercent = value.coerceIn(50, 99)); syncCloudPreferences() }
  fun setNextEpisodeThresholdMinutes(value: Int) { appSettingsStore.saveNextEpisodeThresholdMinutes(value); uiState = uiState.copy(nextEpisodeThresholdMinutes = value.coerceIn(1, 15)); syncCloudPreferences() }
  fun setAutoLoadSubtitles(value: Boolean) { appSettingsStore.saveAutoLoadSubtitles(value); uiState = uiState.copy(autoLoadSubtitles = value); syncCloudPreferences() }
  fun setBlurUnwatchedEpisodes(value: Boolean) { appSettingsStore.saveBlurUnwatchedEpisodes(value); uiState = uiState.copy(blurUnwatchedEpisodes = value); syncCloudPreferences() }
  fun setDetailSelectedTab(tab: String) { uiState = uiState.copy(detailSelectedTab = tab) }
  fun setRatingsEnabled(value: Boolean) { appSettingsStore.saveRatingsEnabled(value); uiState = uiState.copy(ratingsEnabled = value); syncCloudPreferences() }
  fun setExternalRatingsEnabled(value: Boolean) {
    appSettingsStore.saveExternalRatingsEnabled(value)
    uiState = uiState.copy(externalRatingsEnabled = value)
    syncCloudPreferences()
    uiState.detail?.let(::refreshExternalRatings)
  }
  fun setRatingProviderEnabled(providerId: String, enabled: Boolean) {
    val normalized = providerId.trim().lowercase()
    val providers = if (enabled) uiState.enabledRatingProviders + normalized else uiState.enabledRatingProviders - normalized
    appSettingsStore.saveEnabledRatingProviders(providers)
    uiState = uiState.copy(enabledRatingProviders = providers)
    syncCloudPreferences()
    uiState.detail?.let(::refreshExternalRatings)
  }
  fun setMdblistApiKey(value: String) {
    appSettingsStore.saveMdblistApiKey(value)
    uiState = uiState.copy(mdblistApiKey = value.trim())
    syncCloudPreferences()
    uiState.detail?.let(::refreshExternalRatings)
  }
  fun setVividAmbient(value: Boolean) { appSettingsStore.saveVividAmbient(value); uiState = uiState.copy(vividAmbient = value); syncCloudPreferences() }
  fun setAmbientTintPercent(value: Int) { val clamped = value.coerceIn(20, 100); appSettingsStore.saveAmbientTintPercent(clamped); uiState = uiState.copy(ambientTintPercent = clamped); syncCloudPreferences() }
  fun setDefaultAppCatalogsEnabled(value: Boolean) {
    appSettingsStore.saveDefaultAppCatalogsEnabled(value)
    uiState = uiState.copy(defaultAppCatalogsEnabled = value, homeSections = applyHomeCatalogLayout(uiState.allHomeSections, uiState.homeCatalogRows, value))
    syncCloudPreferences()
  }
  fun setHomeCatalogRowEnabled(rowId: String, enabled: Boolean) {
    val rows = uiState.homeCatalogRows.map { if (it.id == rowId) it.copy(enabled = enabled) else it }
    appSettingsStore.saveHomeCatalogRows(rows)
    uiState = uiState.copy(homeCatalogRows = rows, homeSections = applyHomeCatalogLayout(uiState.allHomeSections, rows, uiState.defaultAppCatalogsEnabled))
    syncCloudPreferences()
  }
  fun moveHomeCatalogRow(rowId: String, delta: Int) {
    val current = uiState.homeCatalogRows.toMutableList()
    val index = current.indexOfFirst { it.id == rowId }
    val target = (index + delta).coerceIn(0, current.lastIndex)
    if (index < 0 || index == target) return
    val item = current.removeAt(index)
    current.add(target, item)
    appSettingsStore.saveHomeCatalogRows(current)
    uiState = uiState.copy(homeCatalogRows = current, homeSections = applyHomeCatalogLayout(uiState.allHomeSections, current, uiState.defaultAppCatalogsEnabled))
    syncCloudPreferences()
  }
  fun setFusionBadgesEnabled(value: Boolean) { appSettingsStore.saveFusionBadges(value); uiState = uiState.copy(fusionBadgesEnabled = value); syncCloudPreferences() }
  fun setShowSizeBadges(value: Boolean) { appSettingsStore.saveShowSizeBadges(value); uiState = uiState.copy(showSizeBadges = value); syncCloudPreferences() }
  fun setShowAddonTmdbRatings(value: Boolean) { appSettingsStore.saveShowAddonTmdbRatings(value); uiState = uiState.copy(showAddonTmdbRatings = value); syncCloudPreferences() }
  fun setPreferredQuality(value: String) { appSettingsStore.savePreferredQuality(value); uiState = uiState.copy(preferredQuality = value); syncCloudPreferences() }
  fun setMaxFileSizeGb(value: Int) { appSettingsStore.saveMaxFileSizeGb(value); uiState = uiState.copy(maxFileSizeGb = value); syncCloudPreferences() }
  fun setBadgePosition(value: String) { appSettingsStore.saveBadgePosition(value); uiState = uiState.copy(badgePosition = value); syncCloudPreferences() }
  fun updateTorrentServerSettings(transform: (TorrentServerSettings) -> TorrentServerSettings) {
    val updated = transform(uiState.torrentServerSettings)
    appSettingsStore.saveTorrentServerSettings(updated)
    uiState = uiState.copy(torrentServerSettings = updated)
    syncTorrentServer()
  }

  fun addFusionBadgeUrl(url: String) {
    val normalized = url.trim()
    if (normalized.isBlank()) return
    if (uiState.fusionBadgeUrls.size >= MAX_FUSION_BADGE_URLS && normalized !in uiState.fusionBadgeUrls) {
      uiState = uiState.copy(errorMessage = "You can add up to $MAX_FUSION_BADGE_URLS badge collections.")
      return
    }
    val urls = (uiState.fusionBadgeUrls + normalized).distinct().take(MAX_FUSION_BADGE_URLS)
    val active = uiState.activeFusionBadgeUrl ?: urls.firstOrNull()
    appSettingsStore.saveFusionBadgeUrls(urls, active)
    uiState = uiState.copy(fusionBadgeUrls = urls, activeFusionBadgeUrl = active)
    syncCloudPreferences()
    refreshFusionBadgeUrl(normalized, refresh = true)
  }

  fun removeFusionBadgeUrl(url: String) {
    val urls = uiState.fusionBadgeUrls.filterNot { it == url }.ifEmpty { listOf(DEFAULT_FUSION_BADGE_URL) }
    val active = uiState.activeFusionBadgeUrl?.takeIf { it in urls } ?: urls.firstOrNull()
    appSettingsStore.saveFusionBadgeUrls(urls, active)
    uiState = uiState.copy(
      fusionBadgeUrls = urls,
      activeFusionBadgeUrl = active,
      fusionBadgeSources = uiState.fusionBadgeSources - url,
    )
    syncCloudPreferences()
    urls.forEach { if (uiState.fusionBadgeSources[it]?.source == null) refreshFusionBadgeUrl(it) }
  }

  fun setActiveFusionBadgeUrl(url: String) {
    if (url !in uiState.fusionBadgeUrls) return
    appSettingsStore.saveFusionBadgeUrls(uiState.fusionBadgeUrls, url)
    uiState = uiState.copy(activeFusionBadgeUrl = url)
    syncCloudPreferences()
  }

  fun refreshFusionBadgeUrl(url: String, refresh: Boolean = true) {
    val normalized = url.trim()
    if (normalized.isBlank()) return
    launchWork(
      onStart = {
        uiState = uiState.copy(
          fusionBadgeSources = uiState.fusionBadgeSources + (normalized to FusionBadgeSourceState(normalized, uiState.fusionBadgeSources[normalized]?.source, loading = true)),
        )
      },
      block = { apiClient.fetchFusionBadgeSource(normalized).map { normalizeFusionBadgeSource(it, normalized) } },
      onSuccess = { source ->
        val active = uiState.activeFusionBadgeUrl ?: normalized
        if (uiState.activeFusionBadgeUrl == null) appSettingsStore.saveFusionBadgeUrls(uiState.fusionBadgeUrls, active)
        uiState = uiState.copy(
          activeFusionBadgeUrl = active,
          fusionBadgeSources = uiState.fusionBadgeSources + (normalized to FusionBadgeSourceState(normalized, source, loading = false)),
        )
      },
      onFailure = { message ->
        uiState = uiState.copy(
          fusionBadgeSources = uiState.fusionBadgeSources + (normalized to FusionBadgeSourceState(normalized, uiState.fusionBadgeSources[normalized]?.source, loading = false, error = message)),
          errorMessage = message,
        )
      },
    )
  }

  private fun refreshFusionBadgeSources() {
    uiState.fusionBadgeUrls.forEach { url ->
      if (uiState.fusionBadgeSources[url]?.source == null) refreshFusionBadgeUrl(url, refresh = false)
    }
  }


  private fun syncTorrentServer() {
    val app = getApplication<Application>()
    val config = uiState.torrentServerSettings.toServiceConfig()
    val shouldRun = config.enabled && config.streamingMode == "server" &&
      (config.runAsForegroundService || (uiState.playerSession != null && TorrentServerService.isOnline))
    if (shouldRun) {
      val intent = TorrentServerService.createIntent(app, config)
      val notificationsAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
      if (config.runAsForegroundService && notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(app, intent)
      } else {
        app.startService(intent)
      }
      scheduleTorrentStatusRefresh()
    } else {
      runCatching { app.stopService(TorrentServerService.createIntent(app, config)) }
      TorrentServerService.markStopped()
    }
    refreshTorrentServerStatus()
  }

  private fun stopOnDemandTorrentServer() {
    val config = uiState.torrentServerSettings.toServiceConfig()
    if (config.runAsForegroundService) return
    torrentStatusRefreshJob?.cancel()
    val app = getApplication<Application>()
    runCatching { app.stopService(TorrentServerService.createIntent(app, config)) }
    TorrentServerService.markStopped()
    refreshTorrentServerStatus()
  }

  private fun scheduleTorrentStatusRefresh() {
    torrentStatusRefreshJob?.cancel()
    torrentStatusRefreshJob = viewModelScope.launch {
      repeat(6) { attempt ->
        delay(if (attempt == 0) 250L else 500L)
        refreshTorrentServerStatus()
      }
    }
  }

  private suspend fun ensureTorrentServerReady(config: TorrentServerConfig): Result<TorrentServerConfig> {
    val app = getApplication<Application>()
    val intent = TorrentServerService.createIntent(app, config)
    runCatching {
      val notificationsAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
      if (config.runAsForegroundService && notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(app, intent)
      } else {
        app.startService(intent)
      }
    }.onFailure {
      refreshTorrentServerStatus()
      return Result.failure(it)
    }
    repeat(12) {
      delay(250L)
      if (TorrentServerService.isOnline) {
        refreshTorrentServerStatus()
        return Result.success(config.copy(port = TorrentServerService.activePort))
      }
    }
    refreshTorrentServerStatus()
    return Result.failure(IllegalStateException(TorrentServerService.lastStartupError?.ifBlank { null } ?: "Local torrent server did not come online in time."))
  }

  private fun refreshTorrentServerStatus() {
    uiState = uiState.copy(
      torrentServerStatus = torrentServerStatusFromSnapshot(
        TorrentServerService.snapshot(uiState.torrentServerSettings.toServiceConfig()),
        uiState.torrentServerSettings,
      ),
    )
  }

  private fun refreshExternalRatings(detail: MediaDetail) {
    val key = uiState.mdblistApiKey
    if (!uiState.externalRatingsEnabled || key.isBlank() || uiState.enabledRatingProviders.isEmpty()) return
    launchWork(
      onStart = {},
      block = { apiClient.fetchMdblistRatings(detail.type, detail.id, detail.imdbId, key, uiState.enabledRatingProviders.toList()) },
      onSuccess = { ratings ->
        if (ratings.isEmpty()) return@launchWork
        val current = uiState.detail ?: return@launchWork
        if (current.id == detail.id && current.type == detail.type) {
          uiState = uiState.copy(detail = current.copy(externalRatings = ratings))
        }
      },
      onFailure = {},
    )
  }

  private fun refreshTraktComments(detail: MediaDetail) {
    launchWork(
      onStart = {},
      block = { apiClient.fetchTraktComments(uiState.session, detail.type, detail.id, detail.imdbId) },
      onSuccess = { comments ->
        if (comments.isEmpty()) return@launchWork
        val current = uiState.detail ?: return@launchWork
        if (current.id == detail.id && current.type == detail.type) {
          uiState = uiState.copy(detail = current.copy(traktComments = comments))
        }
      },
      onFailure = {},
    )
  }

  private fun restore() {
    launchWork(
      onStart = { uiState = uiState.copy(errorMessage = null) },
      block = { Result.success(sessionStore.load()) },
      onSuccess = { session ->
        if (session == null) {
          val profiles = guestProfileStore.load()
          val selected = profiles.firstOrNull { it.id == profileSelectionStore.load(GUEST_OWNER_KEY) } ?: profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
          uiState = appSettingsStore.applyTo(uiState.copy(booting = false, session = null, profilesLoading = false, profiles = profiles, activeProfileId = selected?.id, showProfilePicker = profiles.size > 1, mergedWatchlist = watchlistStore.load(selected?.id?.let { "guest:$it" } ?: GUEST_OWNER_KEY), favouriteChannels = favouriteChannelStore.load(selected?.id?.let { "guest:$it" } ?: GUEST_OWNER_KEY)))
        } else {
          routeAfterProfileRefresh = true
          uiState = appSettingsStore.applyTo(uiState.copy(booting = false, session = session, profilesLoading = true, showProfilePicker = true, mergedWatchlist = watchlistStore.load(activeOwnerKey() ?: GUEST_OWNER_KEY), favouriteChannels = favouriteChannelStore.load(activeOwnerKey() ?: GUEST_OWNER_KEY)))
        }
        bootstrapAfterAuth()
      },
      onFailure = {
        val profiles = guestProfileStore.load()
        val selected = profiles.firstOrNull { it.id == profileSelectionStore.load(GUEST_OWNER_KEY) } ?: profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
        uiState = appSettingsStore.applyTo(uiState.copy(booting = false, session = null, profilesLoading = false, profiles = profiles, activeProfileId = selected?.id, showProfilePicker = profiles.size > 1, errorMessage = null, mergedWatchlist = watchlistStore.load(selected?.id?.let { "guest:$it" } ?: GUEST_OWNER_KEY), favouriteChannels = favouriteChannelStore.load(selected?.id?.let { "guest:$it" } ?: GUEST_OWNER_KEY)))
        bootstrapAfterAuth()
      },
    )
  }

  private fun submitAuth(guestDataOwnerKey: String? = null, block: suspend () -> Result<AuthSession>) {
    launchWork(
      onStart = { uiState = uiState.copy(booting = true, errorMessage = null, infoMessage = null) },
      block = block,
      onSuccess = { session ->
        sessionStore.save(session)
        routeAfterProfileRefresh = true
        uiState = appSettingsStore.applyTo(uiState.copy(booting = false, session = session, profilesLoading = true, showProfilePicker = true, pendingGuestMerge = guestDataOwnerKey))
        bootstrapAfterAuth(forceHome = true)
      },
      onFailure = { message -> uiState = uiState.copy(booting = false, errorMessage = message) },
    )
  }

  fun dismissGuestMergePrompt() {
    uiState = uiState.copy(pendingGuestMerge = null)
  }

  /** Moves a just-left-behind guest identity's watchlist, favourites, continue-watching
   * progress, and locally-added add-ons into the profile the user just registered/landed
   * on. General app settings need no migration - most of them already live in a single
   * shared preferences file rather than being keyed per owner. */
  fun mergeGuestDataIntoAccount() {
    val sourceKey = uiState.pendingGuestMerge ?: return
    uiState = uiState.copy(pendingGuestMerge = null)
    val targetKey = activeOwnerKey() ?: return
    if (targetKey == sourceKey) return
    val mergedWatchlist = (watchlistStore.load(targetKey) + watchlistStore.load(sourceKey)).distinctBy { "${it.type}:${it.id}" }
    watchlistStore.save(targetKey, mergedWatchlist)
    val mergedFavourites = (favouriteChannelStore.load(targetKey) + favouriteChannelStore.load(sourceKey)).distinctBy { "${it.type}:${it.id}" }
    favouriteChannelStore.save(targetKey, mergedFavourites)
    playbackResumeStore.loadAll(sourceKey).forEach { entry -> playbackResumeStore.save(targetKey, entry) }
    LocalAddonManager.copyProfileStorageTo(sourceKey, targetKey)
    refreshProfileScopedData()
    uiState = uiState.copy(infoMessage = "Your favourites, watchlist, and add-ons were moved into your new account.")
  }

  private fun bootstrapAfterAuth(forceHome: Boolean = false) {
    syncTorrentServer()
    uiState.session?.let { session ->
      viewModelScope.launch {
        apiClient.fetchCloudPlaybackPreferences(session, uiState.activeProfileId).onSuccess { preferences ->
          applyCloudPlaybackPreferences(preferences)
          loadHome(force = true)
        }
      }
    }
    refreshFusionBadgeSources()
    loadHome(force = forceHome)
    refreshAddons()
    loadM3uPlaylists()
    refreshTraktData()
    uiState = uiState.copy(mergedWatchlist = loadLocalWatchlist(), favouriteChannels = loadLocalFavouriteChannels(), localContinueWatching = loadLocalContinueWatching(), localResumeEntries = loadResumeEntries())
    if (uiState.session != null) {
      refreshProfiles()
      refreshDebridAccounts()
    }
    if (uiState.autoUpdateChecksEnabled && uiState.availableUpdate == null) checkForUpdates(manual = false)
  }

  private fun refreshProfileScopedData() {
    val ownerKey = activeOwnerKey() ?: GUEST_OWNER_KEY
    appSettingsStore.selectProfileStorage(ownerKey)
    LocalAddonManager.selectProfileStorage(ownerKey)
    M3uPlaylistManager.selectProfileStorage(ownerKey)
    loadM3uPlaylists()
    val activeProfile = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }
    refreshTraktData()
    refreshProfilePlugins()
    uiState = appSettingsStore.applyTo(uiState.copy(
      mergedWatchlist = loadLocalWatchlist(),
      favouriteChannels = loadLocalFavouriteChannels(),
      localContinueWatching = loadLocalContinueWatching(),
      localResumeEntries = loadResumeEntries(),
      preferredAudioLanguage = normalizePreferredAudioLanguage(activeProfile?.audioLanguage?.takeIf { it.isNotBlank() } ?: "en"),
    ))
    refreshAddons()
    val session = uiState.session
    val profileId = uiState.activeProfileId
    if (session != null && profileId != null) {
      viewModelScope.launch {
        apiClient.fetchCloudPlaybackPreferences(session, profileId).onSuccess { preferences ->
          if (uiState.activeProfileId == profileId) {
            applyCloudPlaybackPreferences(preferences)
            syncCloudPreferences(force = true)
            loadHome(force = true)
          }
        }
      }
    }
  }

  private fun refreshProfilePlugins() {
    val ownerKey = activeOwnerKey() ?: GUEST_OWNER_KEY
    StreamDekPlugins.manager.selectProfileStorage(ownerKey)
    val session = uiState.session ?: return
    val profileId = uiState.activeProfileId ?: return
    pluginRefreshJob?.cancel()
    pluginRefreshJob = viewModelScope.launch {
      apiClient.fetchProfilePlugins(session, profileId).onSuccess { cloudJson ->
        if (uiState.activeProfileId != profileId || activeOwnerKey() != ownerKey) return@onSuccess
        val cloudHasState = runCatching {
          JSONObject(cloudJson).let { root -> root.has("enabled") || root.has("repos") || root.has("providers") }
        }.getOrDefault(false)
        val localSnapshot = StreamDekPlugins.manager.snapshotJson(includeCode = false)
        val localIsNewer = StreamDekPlugins.manager.state.updatedAt > StreamDekPlugins.manager.snapshotUpdatedAt(cloudJson)
        if (cloudHasState && !localIsNewer) {
          StreamDekPlugins.manager.restoreCloudState(cloudJson)
          StreamDekPlugins.manager.state.repos
            .filter { repo -> StreamDekPlugins.manager.state.providers.none { provider -> provider.repoUrl == repo.url } || StreamDekPlugins.manager.state.providers.any { provider -> provider.repoUrl == repo.url && provider.code.isBlank() } }
            .forEach { repo -> StreamDekPlugins.manager.refresh(repo.url) }
        } else if (StreamDekPlugins.manager.state.repos.isNotEmpty() || StreamDekPlugins.manager.state.updatedAt > 0L) {
          apiClient.putProfilePlugins(session, profileId, localSnapshot)
        }
      }
    }
  }

  private fun syncActiveProfilePlugins(raw: String) {
    val session = uiState.session ?: return
    val profileId = uiState.activeProfileId ?: return
    pluginSyncJob?.cancel()
    pluginSyncJob = viewModelScope.launch {
      delay(350)
      repeat(3) { attempt ->
        if (apiClient.putProfilePlugins(session, profileId, raw).isSuccess) return@launch
        delay(500L * (attempt + 1))
      }
    }
  }

  private fun determineActiveProfile(session: AuthSession, profiles: List<StreamProfile>): StreamProfile? {
    val stored = profileSelectionStore.load(session.user.uid)
    return profiles.firstOrNull { it.id == stored } ?: profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
  }

  private fun activeOwnerKey(): String? {
    val userId = uiState.session?.user?.uid
    if (userId == null) return uiState.activeProfileId?.let { "guest:$it" } ?: GUEST_OWNER_KEY
    val profileId = uiState.activeProfileId ?: return userId
    return "$userId:$profileId"
  }

  private fun loadLocalWatchlist(): List<MediaItem> = watchlistStore.load(activeOwnerKey() ?: GUEST_OWNER_KEY)

  private fun loadLocalFavouriteChannels(): List<MediaItem> = favouriteChannelStore.load(activeOwnerKey() ?: GUEST_OWNER_KEY)

  private fun loadLocalContinueWatching(): List<MediaItem> {
    val ownerKey = activeOwnerKey() ?: GUEST_OWNER_KEY
    val watchedTitles = watchedTitleStore.load(ownerKey)
    return playbackResumeStore.loadAll(ownerKey)
      .filterNot { entry -> watchedTitleKey(entry.mediaType, entry.mediaId) in watchedTitles }
      .filter { entry -> !entry.isLive && entry.mediaType.lowercase() !in setOf("live", "channel", "sports", "sport") }
      .map { entry ->
        MediaItem(
          id = entry.mediaId,
          type = entry.mediaType,
          title = entry.title,
          year = entry.year,
          poster = entry.poster,
          backdrop = entry.backdrop,
          rating = null,
          description = "",
          progress = entry.progressPercent,
          updatedAt = entry.updatedAt,
          resumeSeasonNumber = entry.seasonNumber,
          resumeEpisodeNumber = entry.episodeNumber,
        )
      }
  }

  private fun loadResumeEntries(): List<PlaybackMemoryEntry> =
    playbackResumeStore.loadAll(activeOwnerKey() ?: GUEST_OWNER_KEY)

  private fun rememberedEpisodeFor(detail: MediaDetail): EpisodeItem? {
    if (detail.type != "tv" || uiState.detailIsLive) return null
    val entry = playbackResumeStore.loadAll(activeOwnerKey() ?: GUEST_OWNER_KEY)
      .filter { it.mediaId == detail.id && it.mediaType == "tv" && it.progressPercent in 1.0..94.9 }
      .maxByOrNull { it.updatedAt } ?: return null
    val seasonNumber = entry.seasonNumber ?: return null
    val episodeNumber = entry.episodeNumber ?: return null
    return EpisodeItem(
      id = "${detail.id}:$seasonNumber:$episodeNumber",
      episodeNumber = episodeNumber,
      seasonNumber = seasonNumber,
      name = "Episode $episodeNumber",
      overview = "",
      still = null,
      runtime = entry.durationSeconds?.div(60),
    )
  }

  private fun loadPlaybackMemoryEntry(detail: MediaDetail, episode: EpisodeItem?): PlaybackMemoryEntry? {
    val ownerKey = activeOwnerKey() ?: GUEST_OWNER_KEY
    val mediaType = if (detail.type == "series") "tv" else detail.type
    return playbackResumeStore.loadAll(ownerKey).firstOrNull {
      it.mediaId == detail.id &&
        it.mediaType == mediaType &&
        it.seasonNumber == episode?.seasonNumber &&
        it.episodeNumber == episode?.episodeNumber
    }
  }

  private fun saveCurrentPlaybackSnapshot(progressPercent: Double) {
    val ownerKey = activeOwnerKey() ?: GUEST_OWNER_KEY
    val player = uiState.playerSession ?: return
    val normalizedProgress = progressPercent.coerceIn(0.0, 100.0)
    if (player.isLive) {
      // Live has no meaningful resume position, but the entry is still worth keeping
      // as a record of the source that worked. Continue Watching filters live entries
      // out on load, so this never shows up there.
      if (uiState.rememberLastSource) {
        playbackResumeStore.save(
          ownerKey,
          PlaybackMemoryEntry(
            mediaId = player.mediaId,
            mediaType = player.mediaType,
            title = player.title,
            year = player.year?.toString(),
            poster = player.poster,
            backdrop = player.backdrop,
            progressPercent = 0.0,
            isLive = true,
            stream = if (uiState.rememberLastSource) player.currentStream else null,
          ),
        )
      } else {
        playbackResumeStore.remove(ownerKey, player.mediaId, player.mediaType, player.seasonNumber, player.episodeNumber)
      }
      uiState = uiState.copy(localContinueWatching = loadLocalContinueWatching(), localResumeEntries = loadResumeEntries())
      return
    }
    if (normalizedProgress >= 95.0 || normalizedProgress <= 1.0) {
      playbackResumeStore.remove(ownerKey, player.mediaId, player.mediaType, player.seasonNumber, player.episodeNumber)
    } else {
      playbackResumeStore.save(
        ownerKey,
        PlaybackMemoryEntry(
          mediaId = player.mediaId,
          mediaType = player.mediaType,
          title = player.title,
          year = player.year?.toString(),
          poster = player.poster,
          backdrop = player.backdrop,
          seasonNumber = player.seasonNumber,
          episodeNumber = player.episodeNumber,
          progressPercent = normalizedProgress,
          durationSeconds = player.runtimeMinutes?.times(60),
          isLive = player.isLive,
          stream = if (uiState.rememberLastSource) player.currentStream else null,
        ),
      )
    }
    uiState = uiState.copy(localContinueWatching = loadLocalContinueWatching(), localResumeEntries = loadResumeEntries())
  }

  private fun mergedContinueWatchingItems(): List<MediaItem> {
    val merged = linkedMapOf<String, MediaItem>()
    uiState.traktContinueWatching.forEach { item ->
      val media = MediaItem(item.tmdbId?.toString() ?: item.id, item.type, item.title, item.year, item.poster, item.backdrop, item.rating, item.description ?: "", item.progress)
      merged["${media.type}:${media.id}"] = media
    }
    uiState.localContinueWatching.forEach { item ->
      val key = "${item.type}:${item.id}"
      val existing = merged[key]
      merged[key] = if (existing == null) item else existing.copy(
        title = item.title.ifBlank { existing.title },
        year = item.year ?: existing.year,
        poster = item.poster ?: existing.poster,
        backdrop = item.backdrop ?: existing.backdrop,
        progress = maxOf(existing.progress ?: 0.0, item.progress ?: 0.0),
        resumeSeasonNumber = item.resumeSeasonNumber ?: existing.resumeSeasonNumber,
        resumeEpisodeNumber = item.resumeEpisodeNumber ?: existing.resumeEpisodeNumber,
      )
    }
    return merged.values.sortedByDescending { it.progress ?: 0.0 }
  }

  private fun mergeWatchlistWithLocal(traktItems: List<TraktItem>, localOverride: List<MediaItem>? = null): List<MediaItem> {
    val merged = linkedMapOf<String, MediaItem>()
    traktItems.forEach { item ->
      val media = item.toMediaItem()
      merged["${media.type}:${media.id}"] = media
    }
    (localOverride ?: loadLocalWatchlist()).forEach { item ->
      val key = "${item.type}:${item.id}"
      val traktItem = merged[key]
      merged[key] = if (traktItem == null) item else item.copy(
        addedAt = item.addedAt ?: traktItem.addedAt,
        updatedAt = item.updatedAt ?: traktItem.updatedAt,
      )
    }
    return merged.values.sortedWith(
      compareByDescending<MediaItem> { it.addedAt ?: Long.MIN_VALUE }
        .thenByDescending { it.updatedAt ?: Long.MIN_VALUE },
    )
  }

  private fun scrobbleCurrentPlayer(action: String, progressPercent: Double) {
    val session = uiState.session ?: return
    val profileId = uiState.activeProfileId ?: return
    val player = uiState.playerSession ?: return
    if (!uiState.traktStatus.connected) return
    if (action == "pause" || action == "stop") saveCurrentPlaybackSnapshot(progressPercent)
    launchWork(
      onStart = {},
      block = {
        apiClient.scrobbleTrakt(
          session = session,
          profileId = profileId,
          action = action,
          payload = TraktScrobblePayload(
            mediaId = uiState.detail?.id ?: player.mediaId,
            mediaType = uiState.detail?.type ?: player.mediaType,
            title = uiState.detail?.title ?: player.title,
            year = uiState.detail?.year?.toIntOrNull() ?: player.year,
            progress = progressPercent,
            seasonNumber = uiState.selectedEpisode?.seasonNumber ?: player.seasonNumber,
            episodeNumber = uiState.selectedEpisode?.episodeNumber ?: player.episodeNumber,
            episodeTitle = uiState.selectedEpisode?.name ?: player.episodeTitle,
          ),
        )
      },
      onSuccess = {
        if (action == "pause" || action == "stop") refreshTraktData()
      },
      onFailure = {},
    )
  }

  private fun <T> launchWork(onStart: () -> Unit, block: suspend () -> Result<T>, onSuccess: (T) -> Unit, onFailure: (String) -> Unit = { message -> uiState = uiState.copy(errorMessage = message) }) {
    onStart()
    viewModelScope.launch {
      val result = runCatching { withContext(Dispatchers.IO) { block() } }.getOrElse { Result.failure(it) }
      result.onSuccess(onSuccess).onFailure { throwable -> onFailure(throwable.message ?: "Network error. Check your connection.") }
    }
  }

  private fun traktItemId(item: TraktItem): String = item.tmdbId?.toString() ?: item.id

  private fun buildPlayerSession(url: String, detail: MediaDetail, sourceLabel: String?, episode: EpisodeItem?, stream: AddonStream, resumePercent: Double = 0.0): PlayerSession = PlayerSession(
    url = url,
    title = sanitizeDisplayText(detail.title).orEmpty(),
    subtitle = sanitizeDisplayText(episode?.let { "S${it.seasonNumber} E${it.episodeNumber}" } ?: detail.year),
    sourceLabel = sanitizeDisplayText(sourceLabel),
    qualityLabel = stream.quality,
    sizeLabel = stream.size,
    backdrop = episode?.still ?: detail.backdrop ?: detail.poster,
    poster = detail.poster,
    titleLogo = detail.titleLogo,
    // Live channels have no synopsis — keep it null so the player omits the block.
    synopsis = sanitizeDisplayText(if (uiState.detailIsLive) null else episode?.overview?.ifBlank { detail.description } ?: detail.description),
    mediaId = detail.id,
    mediaType = detail.type,
    year = detail.year?.toIntOrNull(),
    seasonNumber = episode?.seasonNumber,
    episodeNumber = episode?.episodeNumber,
    episodeTitle = sanitizeDisplayText(episode?.name),
    pictureInPictureEnabled = uiState.pictureInPictureEnabled,
    decoderMode = uiState.decoderMode,
    renderSurface = uiState.renderSurface,
    playerEngine = uiState.playerEngine,
    preferredAudioLanguage = uiState.preferredAudioLanguage,
    resumePercent = resumePercent,
    currentStream = stream,
    imdbId = detail.imdbId ?: detail.id.takeIf { it.startsWith("tt") },
    subtitleLanguage = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }?.subtitleLanguage?.takeIf { it.isNotBlank() } ?: "en",
    autoLoadSubtitles = uiState.autoLoadSubtitles,
    skipIntroEnabled = uiState.skipIntroEnabled,
    skipRecapEnabled = uiState.skipRecapEnabled,
    skipEndingEnabled = uiState.skipEndingEnabled,
    autoPlayNextEpisode = uiState.autoPlayNextEpisode,
    preferBingeGroup = uiState.preferBingeGroup,
    nextEpisodeThresholdMode = uiState.nextEpisodeThresholdMode,
    nextEpisodeThresholdPercent = uiState.nextEpisodeThresholdPercent,
    nextEpisodeThresholdMinutes = uiState.nextEpisodeThresholdMinutes,
    requestHeaders = stream.requestHeaders,
    isLive = uiState.detailIsLive,
    runtimeMinutes = episode?.runtime ?: detail.runtimeMinutes,
    addonSubtitleSources = addonSubtitleSources(),
    userSubtitleSources = UserSubtitleSourceStore.load(getApplication<Application>().applicationContext, activeOwnerKey() ?: GUEST_OWNER_KEY),
    isProxied = stream.isLikelyProxied(uiState.addons.firstOrNull { it.id == stream.addonId }),
  )

  // Installed, enabled addons that advertise the Stremio "subtitles" resource are
  // queried alongside the user's manual subtitle sources.
  private fun addonSubtitleSources(): List<UserSubtitleSource> = uiState.addons
    .filter { addon ->
      addon.enabled && addon.manifest.resources.any { it.trim().equals("subtitles", ignoreCase = true) }
    }
    .mapNotNull { addon ->
      val manifestUrl = addon.transportUrl ?: addon.manifestUrl ?: addon.url ?: addon.baseUrl ?: return@mapNotNull null
      val baseUrl = manifestUrl.substringBeforeLast("/manifest.json", missingDelimiterValue = manifestUrl).trimEnd('/')
      if (baseUrl.isBlank()) return@mapNotNull null
      UserSubtitleSource(
        id = "addon:${addon.id}",
        name = addon.manifest.name.ifBlank { addon.id },
        baseUrl = baseUrl,
      )
    }

  private fun buildSourceLabel(stream: AddonStream): String? = listOfNotNull(stream.addonName.takeIf { it.isNotBlank() }, stream.source?.takeIf { it.isNotBlank() }, stream.quality, stream.size?.takeIf { it.isNotBlank() }?.let { "[$it]" }).distinct().joinToString(" \u2022 ").ifBlank { stream.name }
}
private class NativeAppViewModelFactory(
  private val application: Application,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    return NativeAppViewModel(application) as T
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StreamDekNativeApp(
  pendingAddonManifestUrl: String? = null,
  onAddonManifestConsumed: () -> Unit = {},
) {
  val context = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
  val viewModel = viewModel<NativeAppViewModel>(factory = NativeAppViewModelFactory(context))
  val snackbarHostState = remember { SnackbarHostState() }
  val uiState = viewModel.uiState
  val systemDarkMode = isSystemInDarkTheme()
  val darkMode = when (uiState.appAppearance) {
    AppAppearance.Dark -> true
    AppAppearance.Light -> false
    AppAppearance.System -> systemDarkMode
  }
  val colorScheme = remember(uiState.themePreset, darkMode) { appColorScheme(uiState.themePreset, darkMode) }
  val activity = LocalContext.current as? Activity
  DisposableEffect(activity, darkMode) {
    activity?.window?.let { window ->
      // Deprecated in API 35+ edge-to-edge, but still the correct way to tint the
      // navigation bar on the API levels this app supports.
      @Suppress("DEPRECATION")
      window.navigationBarColor = if (darkMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
      WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = !darkMode
        isAppearanceLightNavigationBars = !darkMode
      }
    }
    onDispose {}
  }

  LaunchedEffect(uiState.errorMessage, uiState.infoMessage) {
    val message = uiState.errorMessage ?: uiState.infoMessage
    if (!message.isNullOrBlank()) {
      snackbarHostState.showSnackbar(message)
      viewModel.clearTransientMessage()
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    shapes = Shapes(
      extraSmall = RoundedCornerShape(10.dp),
      small = RoundedCornerShape(14.dp),
      medium = RoundedCornerShape(18.dp),
      large = RoundedCornerShape(24.dp),
      extraLarge = RoundedCornerShape(30.dp),
    ),
  ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
          containerColor = Color.Transparent,
          snackbarHost = {
            SnackbarHost(
              hostState = snackbarHostState,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 104.dp),
              snackbar = { data ->
                Snackbar(
                  snackbarData = data,
                  shape = RoundedCornerShape(20.dp),
                  containerColor = MaterialTheme.colorScheme.surface,
                  contentColor = MaterialTheme.colorScheme.onSurface,
                  actionColor = MaterialTheme.colorScheme.primary,
                )
              },
            )
          },
        ) { padding ->
          // The player replaces the whole main scene, so hold the main scene's
          // saveable state (scroll positions, browse page state) while it is away.
          val rootStateHolder = rememberSaveableStateHolder()
          AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            targetState = Pair(uiState.booting, uiState.playerSession != null || uiState.playerLaunching),
            transitionSpec = {
              (fadeIn() + slideInVertically { it / 8 }) togetherWith
                (fadeOut() + slideOutVertically { -it / 12 })
            },
            label = "root_state",
          ) { state ->
            when {
              state.first -> SplashScene()
              state.second && uiState.playerSession == null -> PlayerLaunchScreen(uiState.playerLaunchingLabel, viewModel::cancelPlayerLaunch)
              state.second && uiState.playerSession != null -> { NativePlayerScreen(
                session = uiState.playerSession,
                availableStreams = uiState.availableStreams,
                onBack = viewModel::dismissPlayer,
                onScrobble = viewModel::scrobblePlayer,
                onProgressCheckpoint = viewModel::savePlayerProgressCheckpoint,
                onSelectStream = { stream, resumePercent -> viewModel.playStream(stream, resumePercentOverride = resumePercent) },
                onReloadStreams = { viewModel.loadStreamsForCurrentDetail(uiState.selectedEpisode) },
                onPlaybackEnded = viewModel::onPlayerPlaybackEnded,
                nextEpisodeLoading = uiState.nextEpisodeLoading,
                nextEpisodeLoadingLabel = uiState.nextEpisodeLoadingLabel,
                onPreviousEpisode = { viewModel.playAdjacentEpisode(-1) },
                onNextEpisode = { viewModel.playAdjacentEpisode(1) },
                isFavourite = uiState.favouriteChannels.any { it.id == uiState.playerSession.mediaId },
                onToggleFavourite = viewModel::toggleFavouriteChannelForCurrentSession,
                liveChannels = uiState.playerLiveChannels,
                liveChannelsLoading = uiState.playerLiveChannelsLoading,
                channelSwitchLoading = uiState.liveChannelSwitching,
                channelSwitchLoadingLabel = uiState.liveChannelSwitchingLabel,
                onCancelChannelSwitch = viewModel::cancelLiveChannelSwitch,
                onChannelSwitchPlaybackStarted = viewModel::confirmLiveChannelSwitchStarted,
                channelSwitchFallbackAvailable = uiState.playerSession?.isLive == true && viewModel.hasLiveChannelFallback(),
                favouriteChannels = uiState.favouriteChannels,
                favouriteDrawerCards = uiState.liveFavouriteDrawerCards,
                onSelectLiveChannel = viewModel::playLiveChannel,
                onToggleFavouriteDrawerCards = viewModel::setLiveFavouriteDrawerCards,
                onClearFavourites = viewModel::clearFavouriteChannels,
                downloadsEnabled = uiState.downloadsEnabled,
                onDownloadStream = { stream -> viewModel.downloadStream(stream, uiState.detail?.title ?: uiState.playerSession?.title ?: "Download") },
              )
              }
              else -> rootStateHolder.SaveableStateProvider("main_scene") {
                MainScene(viewModel, pendingAddonManifestUrl, onAddonManifestConsumed)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PlayerLaunchScreen(sourceLabel: String?, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    GlassCircleButton(
      onClick = onBack,
      modifier = Modifier.statusBarsPadding().padding(20.dp).align(Alignment.TopStart),
    ) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
    }
    Column(
      modifier = Modifier.align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
      Text("Preparing stream", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
      sourceLabel?.takeIf { it.isNotBlank() }?.let {
        Text(it, color = Color.White.copy(alpha = 0.64f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun GradientBackdrop(content: @Composable () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(Color(0xFF040B14), Color(0xFF0B1830), Color(0xFF07111C)),
        ),
      ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(340.dp)
        .background(
          brush = Brush.radialGradient(
            colors = listOf(Color(0xFF123B55), Color.Transparent),
            radius = 900f,
          ),
        ),
    )
    content()
  }
}

@Composable
private fun SplashScene() {
  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 58.dp, bottom = 120.dp),
      verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
      item { SkeletonBlock(modifier = Modifier.fillMaxWidth().height(440.dp), radius = 30.dp) }
      item { SkeletonBlock(modifier = Modifier.fillMaxWidth().height(54.dp), radius = 999.dp) }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          SkeletonBlock(modifier = Modifier.weight(1f).height(44.dp), radius = 999.dp)
          SkeletonBlock(modifier = Modifier.weight(1f).height(44.dp), radius = 999.dp)
          SkeletonBlock(modifier = Modifier.size(44.dp), radius = 999.dp)
          SkeletonBlock(modifier = Modifier.size(44.dp), radius = 999.dp)
        }
      }
      item { SkeletonBlock(modifier = Modifier.fillMaxWidth().height(88.dp), radius = 18.dp) }
      item { SkeletonBlock(modifier = Modifier.fillMaxWidth(0.58f).height(30.dp), radius = 12.dp) }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
          SkeletonBlock(modifier = Modifier.weight(1f).height(82.dp), radius = 18.dp)
          SkeletonBlock(modifier = Modifier.weight(1f).height(82.dp), radius = 18.dp)
          SkeletonBlock(modifier = Modifier.weight(1f).height(82.dp), radius = 18.dp)
        }
      }
    }
  }
}

@Composable
private fun DetailSkeletonScene(style: DetailPageStyle) {
  val skeletonBackground = MaterialTheme.colorScheme.background
  Box(modifier = Modifier.fillMaxSize().background(skeletonBackground)) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 132.dp),
      verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
      item {
        val heroHeight = if (style == DetailPageStyle.Centered) 720.dp else 660.dp
        Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
          SkeletonBlock(modifier = Modifier.fillMaxSize(), radius = 0.dp)
          Box(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .height(220.dp)
              .background(Brush.verticalGradient(colors = listOf(Color.Transparent, skeletonBackground.copy(alpha = 0.72f), skeletonBackground))),
          )
          Column(
            modifier = Modifier
              .align(if (style == DetailPageStyle.Centered) Alignment.BottomCenter else Alignment.BottomStart)
              .fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = if (style == DetailPageStyle.Centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(if (style == DetailPageStyle.Centered) 0.24f else 0.32f).height(18.dp), radius = 999.dp)
            SkeletonBlock(modifier = Modifier.fillMaxWidth(if (style == DetailPageStyle.Centered) 0.58f else 0.62f).height(58.dp), radius = 18.dp)
            SkeletonBlock(modifier = Modifier.fillMaxWidth(if (style == DetailPageStyle.Centered) 0.72f else 0.82f).height(14.dp), radius = 999.dp)
            SkeletonBlock(modifier = Modifier.fillMaxWidth(if (style == DetailPageStyle.Centered) 0.64f else 0.76f).height(14.dp), radius = 999.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
              SkeletonBlock(modifier = Modifier.weight(1f).height(52.dp), radius = 999.dp)
              SkeletonBlock(modifier = Modifier.weight(1f).height(52.dp), radius = 999.dp)
              SkeletonBlock(modifier = Modifier.size(52.dp), radius = 999.dp)
            }
          }
        }
      }
      item {
        Row(modifier = Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          SkeletonBlock(modifier = Modifier.weight(1f).height(48.dp), radius = 999.dp)
          SkeletonBlock(modifier = Modifier.weight(1f).height(48.dp), radius = 999.dp)
          SkeletonBlock(modifier = Modifier.weight(1f).height(48.dp), radius = 999.dp)
        }
      }
      item {
        Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.34f).height(28.dp), radius = 12.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth().height(15.dp), radius = 999.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.92f).height(15.dp), radius = 999.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.78f).height(15.dp), radius = 999.dp)
        }
      }
      item {
        Row(modifier = Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          SkeletonBlock(modifier = Modifier.weight(1f).height(136.dp), radius = 22.dp)
          SkeletonBlock(modifier = Modifier.weight(1f).height(136.dp), radius = 22.dp)
        }
      }
      item {
        Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.28f).height(24.dp), radius = 12.dp)
          repeat(3) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth().height(92.dp), radius = 20.dp)
          }
        }
      }
    }
  }
}

@Composable
private fun SkeletonBlock(modifier: Modifier = Modifier, radius: androidx.compose.ui.unit.Dp = 18.dp) {
  val lightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val skeletonColor = MaterialTheme.colorScheme.onSurface
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(radius))
      .background(skeletonColor.copy(alpha = if (lightMode) 0.16f else 0.08f))
      .border(1.dp, skeletonColor.copy(alpha = if (lightMode) 0.14f else 0.06f), RoundedCornerShape(radius)),
  )
}

@Composable
private fun AuthScene(viewModel: NativeAppViewModel, onContinueAsGuest: (() -> Unit)? = null) {
  val uiState = viewModel.uiState
  var mode by rememberSaveable { mutableStateOf("signin") }
  var showPassword by rememberSaveable { mutableStateOf(false) }
  var form by remember(uiState.rememberedEmail) { mutableStateOf(AuthFormState(email = uiState.rememberedEmail)) }
  val white = Color.White
  val muted = Color(0xFFB6C4C8)
  val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = white,
    unfocusedTextColor = white,
    focusedBorderColor = white.copy(alpha = 0.72f),
    unfocusedBorderColor = white.copy(alpha = 0.20f),
    focusedLabelColor = white,
    unfocusedLabelColor = muted,
    focusedPlaceholderColor = muted,
    unfocusedPlaceholderColor = muted,
    cursorColor = white,
    focusedLeadingIconColor = white,
    unfocusedLeadingIconColor = muted,
    focusedTrailingIconColor = white,
    unfocusedTrailingIconColor = muted,
    focusedContainerColor = Color.White.copy(alpha = 0.04f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.025f),
  )

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colorStops = arrayOf(
            0f to Color(0xFF12383C),
            0.52f to Color(0xFF071719),
            1f to Color(0xFF020607),
          ),
        ),
      ),
  ) {
    val compact = maxHeight < 720.dp
    val logoSize = if (compact) 54.dp else 62.dp
    val sectionGap = if (compact) 9.dp else 12.dp
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 28.dp, vertical = if (compact) 12.dp else 18.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Image(
        painter = painterResource(R.drawable.streamdek_logo_transparent),
        contentDescription = "StreamDek",
        modifier = Modifier.size(logoSize),
        contentScale = ContentScale.Fit,
      )
      Text("StreamDek", color = white, fontSize = if (compact) 24.sp else 27.sp, fontWeight = FontWeight.Black)
      Text("Your entertainment, all in one place", color = muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

      Spacer(modifier = Modifier.height(if (compact) 14.dp else 20.dp))
      Text(
        when (mode) {
          "signup" -> "Create Your Account"
          "reset" -> "Reset Your Password"
          else -> "Welcome Back"
        },
        modifier = Modifier.fillMaxWidth(),
        color = white,
        fontSize = if (compact) 27.sp else 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Black,
      )
      Text(
        when (mode) {
          "signup" -> "Sign up to sync your library, profiles, and progress."
          "reset" -> "Enter your details to recover access to StreamDek."
          else -> "Sign in to access your library and progress."
        },
        modifier = Modifier.fillMaxWidth(),
        color = muted,
        style = MaterialTheme.typography.bodyMedium,
      )

      Spacer(modifier = Modifier.height(sectionGap))
      OutlinedTextField(
        value = form.email,
        onValueChange = { form = form.copy(email = it); viewModel.rememberAuthEmail(it) },
        placeholder = { InputGuideText("Email") },
        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        colors = fieldColors,
      )

      Spacer(modifier = Modifier.height(9.dp))
      if (mode != "reset") {
        OutlinedTextField(
          value = form.password,
          onValueChange = { form = form.copy(password = it) },
          placeholder = { InputGuideText(if (mode == "signup") "Create password" else "Password") },
          leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
          trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
              Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = if (showPassword) "Hide password" else "Show password")
            }
          },
          visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          colors = fieldColors,
        )
      } else {
        OutlinedTextField(
          value = form.resetCode,
          onValueChange = { form = form.copy(resetCode = it) },
          placeholder = { InputGuideText("Reset code") },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          singleLine = true,
          colors = fieldColors,
        )
        Spacer(modifier = Modifier.height(9.dp))
        OutlinedTextField(
          value = form.newPassword,
          onValueChange = { form = form.copy(newPassword = it) },
          placeholder = { InputGuideText("New password") },
          leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
          visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          colors = fieldColors,
        )
      }

      if (mode == "signin") {
        TextButton(
          onClick = { mode = "reset" },
          modifier = Modifier.align(Alignment.End),
          colors = ButtonDefaults.textButtonColors(contentColor = white),
          contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        ) { Text("Forgot password?", fontWeight = FontWeight.Bold) }
      } else {
        Spacer(modifier = Modifier.height(8.dp))
      }

      Button(
        onClick = {
          when (mode) {
            "signin" -> viewModel.signIn(form.email.trim(), form.password, true)
            "signup" -> viewModel.signUp(form.email.trim(), form.password, true)
            else -> if (form.resetCode.isBlank() || form.newPassword.isBlank()) viewModel.requestPasswordReset(form.email.trim()) else viewModel.confirmPasswordReset(form.email.trim(), form.resetCode, form.newPassword)
          }
        },
        enabled = !uiState.booting,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = white, contentColor = Color.Black, disabledContainerColor = white.copy(alpha = 0.58f), disabledContentColor = Color.Black.copy(alpha = 0.58f)),
      ) {
        if (uiState.booting) {
          CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.Black)
        } else {
          Text(
            when (mode) {
              "signin" -> "Sign In"
              "signup" -> "Create Account"
              else -> if (form.resetCode.isBlank() || form.newPassword.isBlank()) "Send Reset Code" else "Reset Password"
            },
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
          )
        }
      }

      if (mode != "reset") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
          Text(if (mode == "signin") "Don't have an account? " else "Already have an account? ", color = muted, style = MaterialTheme.typography.bodyMedium)
          TextButton(onClick = { mode = if (mode == "signin") "signup" else "signin" }, colors = ButtonDefaults.textButtonColors(contentColor = white), contentPadding = PaddingValues(horizontal = 2.dp)) {
            Text(if (mode == "signin") "Sign Up" else "Sign In", fontWeight = FontWeight.Black)
          }
        }
      } else {
        TextButton(onClick = { mode = "signin" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = white)) {
          Text("Back to Sign In", fontWeight = FontWeight.Bold)
        }
      }

      if (onContinueAsGuest != null && mode == "signin") {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Box(modifier = Modifier.weight(1f).height(1.dp).background(white.copy(alpha = 0.14f)))
          Text("or", color = muted)
          Box(modifier = Modifier.weight(1f).height(1.dp).background(white.copy(alpha = 0.14f)))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = onContinueAsGuest,
          modifier = Modifier.fillMaxWidth().height(52.dp),
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(1.dp, white.copy(alpha = 0.24f)),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = white),
        ) { Text("Continue Without Account", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
      }

      uiState.errorMessage?.let { message ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, modifier = Modifier.fillMaxWidth(), color = Color(0xFFFF8A80), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}
@Composable
private fun NavigationCaretCue() {
  var tintVisible by remember { mutableStateOf(false) }
  var caretVisible by remember { mutableStateOf(false) }
  val motion = rememberInfiniteTransition(label = "collapsed_navigation_caret")
  val travelX by motion.animateFloat(
    initialValue = 5f,
    targetValue = -5f,
    animationSpec = infiniteRepeatable(tween(320, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "collapsed_navigation_caret_travel",
  )
  val caretScale by motion.animateFloat(
    initialValue = 0.90f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(tween(320, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "collapsed_navigation_caret_scale",
  )

  LaunchedEffect(Unit) {
    tintVisible = true
    delay(240)
    caretVisible = true
  }

  Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
    AnimatedVisibility(
      visible = tintVisible,
      enter = fadeIn(tween(220)),
      exit = fadeOut(tween(160)),
      modifier = Modifier.matchParentSize(),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.38f), CircleShape)
          .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
      )
    }
    AnimatedVisibility(
      visible = caretVisible,
      enter = fadeIn(tween(160)),
      exit = fadeOut(tween(120)),
    ) {
      Icon(
        Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = "Tap to expand navigation",
        tint = Color.White,
        modifier = Modifier
          .size(23.dp)
          .graphicsLayer {
            translationX = travelX
            scaleX = caretScale
            scaleY = caretScale
          },
      )
    }
  }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScene(viewModel: NativeAppViewModel, pendingAddonManifestUrl: String?, onAddonManifestConsumed: () -> Unit) {
  val uiState = viewModel.uiState
  var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
  var previousTab by rememberSaveable { mutableStateOf(MainTab.Home) }
  var openDetail by rememberSaveable { mutableStateOf(uiState.detail?.let { it.type to it.id }) }
  val browseRow = uiState.browseRow
  val networkBrowse = uiState.networkBrowseItem
  // Settings keeps a history stack so backing out of a nested page returns to the
  // page it was opened from instead of jumping to the settings home.
  var settingsStackRaw by rememberSaveable { mutableStateOf("") }
  val settingsStack = remember(settingsStackRaw) {
    settingsStackRaw.split('|').filter { it.isNotBlank() }.mapNotNull { name -> runCatching { SettingsRoute.valueOf(name) }.getOrNull() }
  }
  val settingsRoute = settingsStack.lastOrNull()
  fun setSettingsRoute(route: SettingsRoute?) {
    settingsStackRaw = when {
      route == null -> ""
      settingsStack.lastOrNull() == route -> settingsStackRaw
      // Re-entering a page already in the history rewinds to it rather than looping.
      settingsStack.contains(route) -> settingsStack.subList(0, settingsStack.indexOf(route) + 1).joinToString("|") { it.name }
      else -> (settingsStack + route).joinToString("|") { it.name }
    }
  }
  fun popSettingsRoute() {
    settingsStackRaw = settingsStack.dropLast(1).joinToString("|") { it.name }
  }
  var detailReturnFromSettings by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
  var showExitPrompt by rememberSaveable { mutableStateOf(false) }
  var showAuth by rememberSaveable { mutableStateOf(false) }
  var requireGuestProfile by rememberSaveable { mutableStateOf(false) }
  var guestProfileCountAtEntry by rememberSaveable { mutableIntStateOf(-1) }
  var homeScrollToTopSignal by rememberSaveable { mutableStateOf(0) }
  var addonInstallPromptUrl by remember { mutableStateOf<String?>(null) }
  var navigationExpanded by rememberSaveable { mutableStateOf(!uiState.collapsibleNavigationEnabled) }
  var navigationActivityKey by remember { mutableIntStateOf(0) }
  var showNavigationCaret by remember { mutableStateOf(false) }
  val showProfilePicker = uiState.showProfilePicker && openDetail == null && browseRow == null && networkBrowse == null
  val activity = LocalContext.current as? Activity

  LaunchedEffect(Unit) {
    viewModel.loadHome()
  }

  LaunchedEffect(uiState.collapsibleNavigationEnabled) {
    navigationExpanded = !uiState.collapsibleNavigationEnabled
  }

  LaunchedEffect(uiState.collapsibleNavigationEnabled, uiState.navigationAutoCollapseSeconds, navigationExpanded, navigationActivityKey, uiState.updateDownloading) {
    if (uiState.collapsibleNavigationEnabled && navigationExpanded && !uiState.updateDownloading) {
      delay(uiState.navigationAutoCollapseSeconds * 1_000L)
      navigationExpanded = false
    }
  }

  LaunchedEffect(uiState.updateDownloading) {
    if (uiState.updateDownloading && uiState.collapsibleNavigationEnabled) {
      navigationActivityKey += 1
      navigationExpanded = true
    }
  }

  LaunchedEffect(uiState.collapsibleNavigationEnabled, navigationExpanded) {
    showNavigationCaret = false
    if (!uiState.collapsibleNavigationEnabled || navigationExpanded) return@LaunchedEffect
    while (true) {
      delay(6500)
      showNavigationCaret = true
      delay(1400)
      showNavigationCaret = false
    }
  }

  LaunchedEffect(pendingAddonManifestUrl, showAuth, requireGuestProfile, showProfilePicker, uiState.profileTransitioning) {
    val manifestUrl = pendingAddonManifestUrl ?: return@LaunchedEffect
    if (!showAuth && !requireGuestProfile && !showProfilePicker && !uiState.profileTransitioning) {
      if (selectedTab != MainTab.Settings) previousTab = selectedTab
      selectedTab = MainTab.Settings
      setSettingsRoute(SettingsRoute.Addons)
      addonInstallPromptUrl = manifestUrl
      onAddonManifestConsumed()
    }
  }

  LaunchedEffect(uiState.playerSession, uiState.returnToDetailAfterPlayer, uiState.detail?.id) {
    if (uiState.playerSession == null && uiState.returnToDetailAfterPlayer) {
      uiState.detail?.let { detail -> openDetail = detail.type to detail.id }
    }
  }

  LaunchedEffect(uiState.session) {
    if (uiState.session != null) {
      showAuth = false
      requireGuestProfile = false
      guestProfileCountAtEntry = -1
    }
  }

  LaunchedEffect(requireGuestProfile, uiState.session, uiState.profiles.size) {
    if (requireGuestProfile && uiState.session == null && uiState.profiles.size > guestProfileCountAtEntry) {
      requireGuestProfile = false
      guestProfileCountAtEntry = -1
      settingsStackRaw = ""
      selectedTab = MainTab.Home
      previousTab = MainTab.Home
    }
  }

  LaunchedEffect(uiState.profileTransitioning) {
    if (uiState.profileTransitioning) {
      selectedTab = MainTab.Home
      previousTab = MainTab.Home
      settingsStackRaw = ""
      openDetail = null
      viewModel.clearBrowseNavigation()
      detailReturnFromSettings = null
    }
  }
  LaunchedEffect(uiState.profileTransitioning) {
    if (!uiState.profileTransitioning) return@LaunchedEffect
    delay(520)
    viewModel.finishProfileTransition()
  }

  BackHandler(enabled = uiState.playerSession == null) {
    when {
      requireGuestProfile -> {
        requireGuestProfile = false
        guestProfileCountAtEntry = -1
        settingsStackRaw = ""
        showAuth = true
      }
      openDetail != null -> {
        openDetail = null
        viewModel.clearPlayerReturnTarget()
      }
      networkBrowse != null -> viewModel.setNetworkBrowseItem(null)
      browseRow != null -> viewModel.setBrowseRow(null)
      settingsRoute != null -> popSettingsRoute()
      selectedTab == MainTab.Settings && detailReturnFromSettings != null -> {
        val returnDetail = detailReturnFromSettings
        detailReturnFromSettings = null
        selectedTab = if (previousTab != MainTab.Settings) previousTab else MainTab.Home
        openDetail = returnDetail
      }
      selectedTab != MainTab.Home -> {
        selectedTab = if (previousTab != selectedTab) previousTab else MainTab.Home
        previousTab = MainTab.Home
      }
      else -> showExitPrompt = true
    }
  }

  if (showExitPrompt) {
    AlertDialog(
      onDismissRequest = { showExitPrompt = false },
      title = { Text("Exit StreamDek?") },
      text = { Text("You are at the first screen. Do you want to close the app?") },
      confirmButton = { TextButton(onClick = { showExitPrompt = false; activity?.finish() }) { Text("Exit") } },
      dismissButton = { TextButton(onClick = { showExitPrompt = false }) { Text("Stay") } },
    )
  }

  addonInstallPromptUrl?.let { manifestUrl ->
    AlertDialog(
      onDismissRequest = { addonInstallPromptUrl = null },
      icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
      title = { Text("Install Stremio add-on?") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("StreamDek will install the add-on manifest from:")
          Text(manifestUrl, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
      },
      confirmButton = {
        Button(onClick = {
          addonInstallPromptUrl = null
          viewModel.installAddon(manifestUrl)
        }) { Text("Install") }
      },
      dismissButton = {
        TextButton(onClick = { addonInstallPromptUrl = null }) { Text("Cancel") }
      },
    )
  }

  if (uiState.updatePromptVisible && uiState.availableUpdate != null) {
    UpdatePromptDialog(uiState = uiState, onUpdate = viewModel::startUpdate, onDismiss = viewModel::dismissUpdatePrompt)
  }

  if (uiState.pendingGuestMerge != null && !uiState.profilesLoading) {
    AlertDialog(
      onDismissRequest = viewModel::dismissGuestMergePrompt,
      icon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) },
      title = { Text("Move your data to this account?") },
      text = { Text("You had favourites, a watchlist, or add-ons set up before signing in. Move them into your new account? Nothing already on this account will be overwritten.") },
      confirmButton = { Button(onClick = viewModel::mergeGuestDataIntoAccount) { Text("Move my data") } },
      dismissButton = { TextButton(onClick = viewModel::dismissGuestMergePrompt) { Text("Not now") } },
    )
  }

  val hazeState = rememberHazeState()
  Scaffold(
    containerColor = Color.Transparent,
    bottomBar = {
      if (!showProfilePicker && !showAuth && !requireGuestProfile && !uiState.profileTransitioning && uiState.pinPromptProfileId == null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          contentAlignment = Alignment.BottomEnd,
        ) {
          val lightNavigation = MaterialTheme.colorScheme.background.luminance() > 0.5f
          val activeProfile = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }
          val expandedNavIconSize by animateDpAsState(if (uiState.showNavLabels) 24.dp else 36.dp, label = "expanded_nav_icon_size")
          val expandedNavProfileSize by animateDpAsState(if (uiState.showNavLabels) 28.dp else 38.dp, label = "expanded_nav_profile_size")
          BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(74.dp),
            contentAlignment = Alignment.CenterEnd,
          ) {
            val expanded = !uiState.collapsibleNavigationEnabled || navigationExpanded
            val navigationWidth by animateDpAsState(
              targetValue = if (expanded) maxWidth else 74.dp,
              animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
              label = "floating_navigation_width",
            )
            val navigationCornerRadius by animateDpAsState(
              targetValue = if (expanded) 30.dp else 37.dp,
              animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
              label = "floating_navigation_corner",
            )

            FrostedGlassSurface(
              modifier = Modifier.width(navigationWidth).height(74.dp),
              shape = RoundedCornerShape(navigationCornerRadius),
              hazeStateOverride = hazeState,
              blurRadius = 68f,
              contentPadding = PaddingValues(7.dp),
              tintAlpha = if (lightNavigation) 0.14f else 0.06f,
              borderAlpha = if (lightNavigation) 0.10f else 0.08f,
              baseAlpha = if (lightNavigation) 0.28f else 0.08f,
              fillColorOverride = if (lightNavigation) null else Color.White,
              showEdgeGradient = false,
            ) {
              AnimatedContent(
                targetState = expanded,
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd,
                transitionSpec = {
                  fadeIn(tween(durationMillis = 150, delayMillis = if (targetState) 170 else 0)) togetherWith
                    fadeOut(tween(durationMillis = 100))
                },
                label = "floating_navigation_content",
              ) { showExpandedContent ->
                if (showExpandedContent && uiState.updateDownloading) {
                  Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    CircularProgressIndicator(
                      progress = { uiState.updateProgress ?: 0f },
                      modifier = Modifier.size(26.dp),
                      strokeWidth = 2.5.dp,
                      color = MaterialTheme.colorScheme.onSurface,
                      trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                      Text("Updating StreamDek", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                      LinearProgressIndicator(
                        progress = { uiState.updateProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)),
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                      )
                    }
                    Text(
                      uiState.updateProgress?.let { "${(it * 100).toInt()}%" } ?: "…",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                  }
                } else if (showExpandedContent) {
                  Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    listOf(
                      MainTab.Home to Icons.Rounded.Home,
                      MainTab.Search to Icons.Rounded.Search,
                      MainTab.Continue to Icons.Rounded.PlayCircleOutline,
                      MainTab.Watchlist to Icons.Rounded.Bookmark,
                      MainTab.Settings to Icons.Rounded.ManageAccounts,
                    ).forEach { (tab, icon) ->
                      val selected = selectedTab == tab
                      Column(
                        modifier = Modifier
                          .weight(1f)
                          .height(60.dp)
                          .clip(RoundedCornerShape(22.dp))
                          .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = if (lightNavigation) 0.11f else 0.15f) else Color.Transparent)
                          .clickable {
                            viewModel.clearPlayerReturnTarget()
                            if (tab == MainTab.Settings && openDetail != null) {
                              detailReturnFromSettings = openDetail
                            } else if (tab != MainTab.Settings) {
                              detailReturnFromSettings = null
                            }
                            openDetail = null
                            viewModel.clearBrowseNavigation()
                            setSettingsRoute(null)
                            if (tab == MainTab.Home && selectedTab == MainTab.Home) homeScrollToTopSignal += 1
                            if (selectedTab != tab) previousTab = selectedTab
                            selectedTab = tab
                            if (uiState.collapsibleNavigationEnabled) navigationActivityKey += 1
                          },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (uiState.showNavLabels) Arrangement.spacedBy(2.dp, Alignment.CenterVertically) else Arrangement.Center,
                      ) {
                        if (tab == MainTab.Settings && activeProfile != null) {
                          Box(
                            modifier = Modifier
                              .size(expandedNavProfileSize)
                              .clip(CircleShape)
                              .background(profileAvatarColor(activeProfile.avatarIndex))
                              .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.52f else 0.24f), CircleShape),
                            contentAlignment = Alignment.Center,
                          ) {
                            ProfileAvatarImage(avatarIndex = activeProfile.avatarIndex, modifier = Modifier.fillMaxSize())
                          }
                        } else {
                          Icon(icon, contentDescription = tab.name, tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), modifier = Modifier.size(expandedNavIconSize))
                        }
                        if (uiState.showNavLabels) {
                          Text(tab.name, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), maxLines = 1)
                        }
                      }
                    }
                  }
                } else {
                  Box(
                    modifier = Modifier
                      .fillMaxSize()
                      .clip(CircleShape)
                      .clickable {
                        navigationActivityKey += 1
                        navigationExpanded = true
                      },
                    contentAlignment = Alignment.Center,
                  ) {
                    if (uiState.updateDownloading) {
                      CircularProgressIndicator(
                        progress = { uiState.updateProgress ?: 0f },
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                      )
                    }
                    if (activeProfile != null) {
                      Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(profileAvatarColor(activeProfile.avatarIndex)),
                        contentAlignment = Alignment.Center,
                      ) {
                        ProfileAvatarImage(avatarIndex = activeProfile.avatarIndex, modifier = Modifier.fillMaxSize())
                      }
                    } else {
                      Icon(Icons.Rounded.ManageAccounts, contentDescription = "Expand navigation", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp))
                    }
                    AnimatedVisibility(
                      visible = showNavigationCaret,
                      enter = fadeIn(tween(180)),
                      exit = fadeOut(tween(220)),
                      modifier = Modifier.align(Alignment.Center),
                    ) {
                      NavigationCaretCue()

                    }
                  }
                }
              }
            }
          }
        }
      }
    },  ) { padding ->
    // Keeps scroll positions (and other saveable UI state) alive while screens are
    // temporarily removed from composition — e.g. Home while a detail page is open.
    val browseStateHolder = rememberSaveableStateHolder()
    AnimatedContent(
      targetState = openDetail,
      modifier = Modifier.fillMaxSize().hazeSource(hazeState),
      transitionSpec = {
        when {
          // Opening a detail page: gentle rise from 96% scale with a fade.
          targetState != null && initialState == null ->
            (fadeIn(tween(240, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.96f, animationSpec = tween(320, easing = FastOutSlowInEasing))) togetherWith
              fadeOut(tween(140))
          // Closing back to browse: quick settle without bounce.
          targetState == null && initialState != null ->
            fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
              (fadeOut(tween(170)) + scaleOut(targetScale = 0.98f, animationSpec = tween(170)))
          else -> fadeIn(tween(200)) togetherWith fadeOut(tween(120))
        }
      },
      label = "detail_transition",
    ) { detail ->
      if (uiState.profileTransitioning) {
        ProfileHomeTransitionOverlay(uiState)
      } else if (showAuth) {
        AuthScene(
          viewModel = viewModel,
          onContinueAsGuest = {
            guestProfileCountAtEntry = uiState.profiles.size
            requireGuestProfile = true
            showAuth = false
            previousTab = selectedTab
            selectedTab = MainTab.Settings
            setSettingsRoute(SettingsRoute.Profiles)
          },
        )
      } else if (showProfilePicker) {
        ProfilePickerScreen(
          uiState = uiState,
          onProfileSelected = viewModel::selectProfile,
          onSubmitProfilePin = viewModel::submitProfilePin,
          onCancelProfilePin = viewModel::cancelProfilePinPrompt,
          onManageProfiles = {
            viewModel.dismissProfilePicker()
            previousTab = selectedTab
            selectedTab = MainTab.Settings
            setSettingsRoute(SettingsRoute.Profiles)
          },
          onOpenProfileManager = {
            viewModel.dismissProfilePicker()
            previousTab = selectedTab
            selectedTab = MainTab.Settings
            setSettingsRoute(SettingsRoute.Profiles)
          },
        )
      } else if (detail == null) {
        if (networkBrowse != null) {
          browseStateHolder.SaveableStateProvider("network_browse") {
            NetworkBrowseScreen(network = networkBrowse, headerStyle = uiState.headerStyle, onBack = { viewModel.setNetworkBrowseItem(null) }, onOpen = { item -> openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) })
          }
        } else if (browseRow != null) {
          browseStateHolder.SaveableStateProvider("browse_row_${browseRow.id}") {
            BrowseSectionScreen(row = browseRow, loadedItems = uiState.browseLoadedItems, returnItemId = uiState.browseReturnItemId, headerStyle = uiState.headerStyle, liveLandscapeCards = uiState.liveLandscapeCards, watchlistItems = uiState.mergedWatchlist, favouriteItems = uiState.favouriteChannels, addons = uiState.addons, onBack = { viewModel.setBrowseRow(null) }, onOpen = { item -> if (item.type == "network") viewModel.setNetworkBrowseItem(item) else { viewModel.rememberBrowseReturnItem(item); openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) } }, onToggleWatchlist = viewModel::toggleWatchlist, onToggleFavourite = viewModel::toggleFavouriteChannel, onEnableAddon = { addon -> viewModel.toggleAddon(addon, true) }, onMarkWatched = viewModel::markWatched, onLoadMore = viewModel::loadMoreRowItems)
          }
        } else {
          AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
              // Fade-through: the old tab exits quickly before the new one fades in,
              // so only one heavy screen draws at a time and the incoming tab gets a
              // few frames to compose while still invisible — keeps switching smooth.
              (fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(210, delayMillis = 90, easing = LinearOutSlowInEasing))) togetherWith
                fadeOut(tween(90, easing = FastOutLinearInEasing))
            },
            label = "tab_transition",
          ) { tab ->
          when (tab) {
            MainTab.Home -> browseStateHolder.SaveableStateProvider("tab_home") {
              HomeTab(uiState = uiState, scrollToTopSignal = homeScrollToTopSignal, onReload = { viewModel.loadHome(force = true) }, onOpen = { item -> if (item.type == "network") viewModel.setNetworkBrowseItem(item) else { openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) } }, onPlayContinueWatching = { item -> if (!viewModel.resumeContinueWatching(item, onUnavailable = { openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) })) { openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) } }, onViewAll = { row -> if (row.id == "continue") selectedTab = MainTab.Continue else viewModel.setBrowseRow(when (row.id) { "m3u_playlists_live" -> row.copy(items = uiState.m3uChannels); "m3u_playlists_vod" -> row.copy(items = uiState.m3uVodItems); else -> row }) }, onToggleWatchlist = viewModel::toggleWatchlist, onMarkWatched = viewModel::markWatched, onRestartFromBeginning = { item -> viewModel.restartFromBeginning(item); openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) }, onResolveHeroTitleLogos = viewModel::resolveHomeHeroTitleLogos, onResolveAddonRatings = viewModel::resolveAddonCatalogRatings, onToggleFavourite = viewModel::toggleFavouriteChannel, onEnableAddon = { addon -> viewModel.toggleAddon(addon, true) })
            }
            MainTab.Search -> browseStateHolder.SaveableStateProvider("tab_search") {
              SearchTab(uiState = uiState, ownerKey = watchedOwnerKey(uiState.session, uiState.activeProfileId), onSearch = viewModel::search, onOpen = { item -> openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) }, onToggleWatchlist = viewModel::toggleWatchlist, onMarkWatched = viewModel::markWatched)
            }
            MainTab.Continue -> browseStateHolder.SaveableStateProvider("tab_continue") {
              ContinueTab(uiState = uiState, onOpen = { item -> if (!viewModel.resumeContinueWatching(item, onUnavailable = { openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) })) { openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) } }, onOpenDetails = { item -> openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) }, onToggleWatchlist = viewModel::toggleWatchlist, onMarkWatched = viewModel::markWatched, onRestartFromBeginning = { item -> viewModel.restartFromBeginning(item); openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) })
            }
            MainTab.Watchlist -> browseStateHolder.SaveableStateProvider("tab_watchlist") {
              WatchlistTab(uiState = uiState, onOpen = { item -> openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) }, onToggleWatchlist = viewModel::toggleWatchlist, onMarkWatched = viewModel::markWatched)
            }
            MainTab.Settings -> SettingsTab(
              uiState = uiState,
              route = settingsRoute,
              apiBaseUrl = viewModel.apiBaseUrl(),
              onRouteChange = { setSettingsRoute(it) },
              onSubmitProfilePin = viewModel::submitProfilePin,
              onCancelProfilePin = viewModel::cancelProfilePinPrompt,
              onBack = {
                if (requireGuestProfile) {
                  requireGuestProfile = false
                  guestProfileCountAtEntry = -1
                  setSettingsRoute(null)
                  showAuth = true
                } else popSettingsRoute()
              },
              onSwitchProfile = { if (uiState.session == null) setSettingsRoute(SettingsRoute.Profiles) else { setSettingsRoute(null); viewModel.showProfilePicker() } },
              onSelectProfile = viewModel::selectProfile,
              onCreateProfile = viewModel::createProfile,
              onUpdateProfile = viewModel::updateProfile,
              onDeleteProfile = viewModel::deleteProfile,
              onMakeDefaultProfile = viewModel::makeDefaultProfile,
              onUpdateProfilePin = viewModel::updateProfilePin,
              onSignOut = viewModel::signOut,
              onSignIn = { showAuth = true },
              onAppAppearanceChange = viewModel::setAppAppearance,
              onAppLanguageChange = { language ->
                viewModel.setAppLanguage(language)
                activity?.recreate()
              },
              onThemePresetChange = viewModel::setThemePreset,
              onHeaderStyleChange = viewModel::setHeaderStyle,
              onPictureInPictureEnabledChange = viewModel::setPictureInPictureEnabled,
              onDecoderModeChange = viewModel::setDecoderMode,
              onRenderSurfaceChange = viewModel::setRenderSurface,
              onPlayerEngineChange = viewModel::setPlayerEngine,
              onPreferredAudioLanguageChange = viewModel::setPreferredAudioLanguage,
              onDetailPageStyleChange = viewModel::setDetailPageStyle,
              onSeasonTabStyleChange = viewModel::setSeasonTabStyle,
              onShowNavLabelsChange = viewModel::setShowNavLabels,
              onCollapsibleNavigationEnabledChange = viewModel::setCollapsibleNavigationEnabled,
              onNavigationAutoCollapseSecondsChange = viewModel::setNavigationAutoCollapseSeconds,
              onSyncOnCellularChange = viewModel::setSyncOnCellular,
              onSkipIntroEnabledChange = viewModel::setSkipIntroEnabled,
              onSkipRecapEnabledChange = viewModel::setSkipRecapEnabled,
              onSkipEndingEnabledChange = viewModel::setSkipEndingEnabled,
              onAutoPlayNextEpisodeChange = viewModel::setAutoPlayNextEpisode,
              onPreferBingeGroupChange = viewModel::setPreferBingeGroup,
              onNextEpisodeThresholdModeChange = viewModel::setNextEpisodeThresholdMode,
              onNextEpisodeThresholdPercentChange = viewModel::setNextEpisodeThresholdPercent,
              onNextEpisodeThresholdMinutesChange = viewModel::setNextEpisodeThresholdMinutes,
              onAutoLoadSubtitlesChange = viewModel::setAutoLoadSubtitles,
              onShowStreamsListChange = viewModel::setShowStreamsList,
              onHeroTrailerAutoplayChange = viewModel::setHeroTrailerAutoplay,
              onHeroTrailerResolutionChange = viewModel::setHeroTrailerResolution,
              onShowHeroSynopsisChange = viewModel::setShowHeroSynopsis,
              onContinueWatchingStyleChange = viewModel::setContinueWatchingStyle,
              onLiveLandscapeCardsChange = viewModel::setLiveLandscapeCards,
              onLiveFavouriteDrawerCardsChange = viewModel::setLiveFavouriteDrawerCards,
              onRememberLastSourceChange = viewModel::setRememberLastSource,
              onBlurUnwatchedEpisodesChange = viewModel::setBlurUnwatchedEpisodes,
              onRatingsEnabledChange = viewModel::setRatingsEnabled,
              onExternalRatingsEnabledChange = viewModel::setExternalRatingsEnabled,
              onRatingProviderEnabledChange = viewModel::setRatingProviderEnabled,
              onMdblistApiKeyChange = viewModel::setMdblistApiKey,
              onVividAmbientChange = viewModel::setVividAmbient,
              onAmbientTintPercentChange = viewModel::setAmbientTintPercent,
              onFusionBadgesChange = viewModel::setFusionBadgesEnabled,
              onShowSizeBadgesChange = viewModel::setShowSizeBadges,
              onShowAddonTmdbRatingsChange = viewModel::setShowAddonTmdbRatings,
              onPreferredQualityChange = viewModel::setPreferredQuality,
              onMaxFileSizeChange = viewModel::setMaxFileSizeGb,
              onBadgePositionChange = viewModel::setBadgePosition,
              onUpdateTorrentServerSettings = viewModel::updateTorrentServerSettings,
              onAddFusionBadgeUrl = viewModel::addFusionBadgeUrl,
              onRemoveFusionBadgeUrl = viewModel::removeFusionBadgeUrl,
              onRefreshFusionBadgeUrl = { viewModel.refreshFusionBadgeUrl(it) },
              onSetActiveFusionBadgeUrl = viewModel::setActiveFusionBadgeUrl,
              onDefaultAppCatalogsEnabledChange = viewModel::setDefaultAppCatalogsEnabled,
              onHomeCatalogRowEnabledChange = viewModel::setHomeCatalogRowEnabled,
              onMoveHomeCatalogRow = viewModel::moveHomeCatalogRow,
              onRefreshHome = { viewModel.loadHome(force = true) },
              onRefreshAddons = viewModel::refreshAddons,
              onInstallAddon = viewModel::installAddon,
              onToggleAddon = viewModel::toggleAddon,
              onUninstallAddon = viewModel::uninstallAddon,
              onMoveAddon = viewModel::moveAddon,
              onAddM3uPlaylist = viewModel::addM3uPlaylist,
              onRemoveM3uPlaylist = viewModel::removeM3uPlaylist,
              onSetM3uPlaylistEnabled = viewModel::setM3uPlaylistEnabled,
              onMoveM3uPlaylist = viewModel::moveM3uPlaylist,
              onRefreshM3uPlaylists = viewModel::loadM3uPlaylists,
              onDownloadsEnabledChange = viewModel::setDownloadsEnabled,
              onRefreshDownloads = viewModel::refreshDownloads,
              onRemoveDownload = viewModel::removeDownload,
              onPlayDownload = viewModel::playDownload,
              onRefreshDebrid = viewModel::refreshDebridAccounts,
              onAddDebrid = viewModel::addDebridAccount,
              onRemoveDebrid = viewModel::removeDebridAccount,
              onMoveDebrid = viewModel::moveDebridAccount,
              onRequestTraktDeviceCode = viewModel::requestTraktDeviceCode,
              onPollTraktAuthorization = viewModel::pollTraktAuthorization,
              onDisconnectTrakt = viewModel::disconnectTrakt,
              onRefreshTrakt = viewModel::refreshTraktData,
              onRefreshSync = viewModel::refreshConnectedServices,
              onAutoUpdateChecksChange = viewModel::setAutoUpdateChecks,
              onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
              onStartUpdate = viewModel::startUpdate,
            )
          }
        }
        }
      } else {
        DetailScreen(
          uiState = uiState,
          onBack = {
            openDetail = null
            viewModel.clearPlayerReturnTarget()
          },
          onLoadStreams = viewModel::loadStreamsForCurrentDetail,
          onDetailTabChange = viewModel::setDetailSelectedTab,
          onPlayStream = { stream, episode -> viewModel.playStream(stream, episode) },
          onPlayEpisodeStream = { stream, episode -> viewModel.playStream(stream, episode, returnToEpisodeStreams = true) },
          onClearPlayerReturnTarget = viewModel::clearPlayerReturnTarget,
          onPlayBestStream = viewModel::playBestStream,
          onLoadSeason = viewModel::loadSeason,
          onToggleWatchlist = viewModel::toggleWatchlist,
          onToggleFavourite = viewModel::toggleFavouriteChannel,
          onOpenPerson = viewModel::openPerson,
          onClosePerson = viewModel::closePerson,
          onOpenRelated = { item -> openDetail = item.type to item.id; viewModel.loadDetail(item.type, item.id, item) },
          onDownloadStream = { stream, title -> viewModel.downloadStream(stream, title) },
          isStreamDownloadEligible = { stream -> viewModel.isDownloadEligible(stream, uiState.detailIsLive) },
        )
      }
    }
  }
}


@Composable
private fun UpdatePromptDialog(uiState: AppUiState, onUpdate: () -> Unit, onDismiss: () -> Unit) {
  val release = uiState.availableUpdate ?: return
  val mandatory = release.required || (release.minSupportedVersionCode?.let { BuildConfig.VERSION_CODE < it } == true)
  AlertDialog(
    onDismissRequest = { if (!mandatory && !uiState.updateDownloading) onDismiss() },
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
          Text(if (mandatory) "Required update" else "Update available", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        }
        Text("StreamDek ${release.versionName}", fontWeight = FontWeight.Black)
      }
    },
    text = {
      Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        release.requiredReason?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
        if (release.releaseNotes.isNotBlank()) {
          Text("What is new", fontWeight = FontWeight.Black)
          Text(release.releaseNotes, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
        }
        uiState.updateProgress?.let { progress ->
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}% downloaded", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
              progress = { progress },
              modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
              color = MaterialTheme.colorScheme.primary,
              trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            )
          }
        }
        uiState.updateStatusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        uiState.updateErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
    },
    confirmButton = {
      Button(onClick = onUpdate, enabled = !uiState.updateDownloading, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
        if (uiState.updateDownloading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text("Update Now", fontWeight = FontWeight.Black)
      }
    },
    dismissButton = {
      if (!mandatory) TextButton(onClick = onDismiss, enabled = !uiState.updateDownloading) { Text("Later") }
    },
    containerColor = MaterialTheme.colorScheme.surface,
  )
}

@Composable
private fun ProfileHomeTransitionOverlay(uiState: AppUiState) {
  val profile = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }
  val pulse = rememberInfiniteTransition(label = "profile_home_transition")
  val scale by pulse.animateFloat(
    initialValue = 0.92f,
    targetValue = 1.06f,
    animationSpec = infiniteRepeatable(tween(760, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "profile_avatar_pulse",
  )

  Box(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center,
  ) {

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
      Box(
        modifier = Modifier
          .size(108.dp)
          .graphicsLayer { scaleX = scale; scaleY = scale }
          .clip(CircleShape)
          .background(profileAvatarColor(profile?.avatarIndex ?: 0))
          .border(3.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f), CircleShape),
      ) {
        ProfileAvatarImage(profile?.avatarIndex ?: 0, Modifier.fillMaxSize())
      }
      Text("Loading " + (profile?.name ?: "your profile"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
      Text("Preparing your home screen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f))
      LinearProgressIndicator(
        modifier = Modifier.width(180.dp).clip(RoundedCornerShape(999.dp)),
        color = MaterialTheme.colorScheme.onBackground,
        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
      )
    }
  }
}

@Composable
private fun ProfilePickerScreen(
  uiState: AppUiState,
  onProfileSelected: (String) -> Unit,
  onSubmitProfilePin: (String) -> Unit,
  onCancelProfilePin: () -> Unit,
  onManageProfiles: () -> Unit,
  onOpenProfileManager: () -> Unit,
) {
  val heroItems = remember(uiState.allHomeSections, uiState.homeSections) {
    profileSwitcherHeroItems(uiState.allHomeSections.ifEmpty { uiState.homeSections })
  }
  var heroIndex by rememberSaveable(heroItems.map { it.id }.joinToString("|")) { mutableStateOf(0) }
  val heroItem = heroItems.getOrNull(heroIndex.coerceIn(0, (heroItems.size - 1).coerceAtLeast(0)))
  var pin by rememberSaveable(uiState.pinPromptProfileId) { mutableStateOf("") }

  LaunchedEffect(heroItems.size, heroIndex) {
    if (heroItems.size <= 1) return@LaunchedEffect
    delay(3500)
    heroIndex = (heroIndex + 1) % heroItems.size
  }

  val context = LocalContext.current
  val profileHazeState = rememberHazeState()
  val lightProfile = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val profileForeground = MaterialTheme.colorScheme.onBackground
  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    AnimatedContent(
      targetState = heroItem,
      modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(560.dp),
      transitionSpec = {
        fadeIn(animationSpec = tween(720)) togetherWith fadeOut(animationSpec = tween(720))
      },
      label = "profile_hero_crossfade",
    ) { currentHero ->
      if (currentHero == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
      } else {
        Box(modifier = Modifier.fillMaxSize()) {
          AsyncImage(
            model = ImageRequest.Builder(context)
              .data(currentHero.backdrop ?: currentHero.poster)
              .size(1280, 720)
              .memoryCachePolicy(CachePolicy.ENABLED)
              .diskCachePolicy(CachePolicy.ENABLED)
              .crossfade(false)
              .build(),
            contentDescription = currentHero.title,
            modifier = Modifier.fillMaxSize().hazeSource(profileHazeState),
            contentScale = ContentScale.Crop,
          )
          ProfileHeroGlassPane(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp),
            hazeState = profileHazeState,
          ) {
            Text("TRENDING NOW", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.76f))
            Text(
              currentHero.title,
              style = MaterialTheme.typography.displaySmall,
              fontWeight = FontWeight.Black,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              color = Color.White,
            )
            Text(
              currentHero.description.ifBlank { currentHero.year ?: "" },
              style = MaterialTheme.typography.titleMedium,
              color = Color.White.copy(alpha = 0.80f),
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(480.dp)
        .background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              0.00f to Color.Transparent,
              0.28f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
              1.00f to MaterialTheme.colorScheme.background,
            ),
          ),
        ),
    )
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = 22.dp, vertical = 58.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
      Text(
        "Who's watching?",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Black,
        color = profileForeground,
      )
      if (uiState.profilesLoading && uiState.profiles.isEmpty()) {
        Column(
          modifier = Modifier.height(154.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(34.dp),
            strokeWidth = 3.dp,
            color = profileForeground,
            trackColor = profileForeground.copy(alpha = 0.16f),
          )
          Spacer(modifier = Modifier.height(14.dp))
          Text("Loading profiles...", color = profileForeground.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
        }
      } else {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(34.dp),
          contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
          items(uiState.profiles, key = { it.id }) { profile ->
            ProfilePickerAvatar(profile = profile, onClick = { onProfileSelected(profile.id) })
          }
          item {
            AddProfileAvatar(onClick = onManageProfiles)
          }
        }
        Button(
          onClick = onOpenProfileManager,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (lightProfile) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF171717),
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
          shape = RoundedCornerShape(999.dp),
        ) {
          Text("Manage Profiles", fontWeight = FontWeight.Bold)
        }
      }
    }
    uiState.pinPromptProfileId?.let { profileId ->
      val profile = uiState.profiles.firstOrNull { it.id == profileId }
      ProfilePinPadScreen(
        profile = profile,
        pin = pin,
        onPinChange = { updated -> pin = updated.filter(Char::isDigit).take(4) },
        onSubmit = {
          if (pin.length == 4) {
            onSubmitProfilePin(pin)
            pin = ""
          }
        },
        onBack = { pin = ""; onCancelProfilePin() },
      )
    }
  }
}

@Composable
private fun ProfileHeroGlassPane(
  modifier: Modifier = Modifier,
  hazeState: HazeState,
  content: @Composable ColumnScope.() -> Unit,
) {
  FrostedGlassSurface(
    modifier = modifier,
    shape = RectangleShape,
    hazeStateOverride = hazeState,
    blurRadius = 68f,
    tintAlpha = 0.06f,
    borderAlpha = 0f,
    baseAlpha = 0.08f,
    fillColorOverride = Color.White,
    showEdgeGradient = false,
  ) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              0.00f to Color.Black.copy(alpha = 0.08f),
              0.48f to Color.Black.copy(alpha = 0.28f),
              0.80f to Color.Black.copy(alpha = 0.72f),
              1.00f to Color.Black,
            ),
          ),
        ),
    )
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
  }
}

private fun profileAvatarResId(index: Int): Int {
  val avatars = listOf(
    R.drawable.profile_avatar_1,
    R.drawable.profile_avatar_2,
    R.drawable.profile_avatar_3,
    R.drawable.profile_avatar_4,
    R.drawable.profile_avatar_5,
    R.drawable.profile_avatar_6,
    R.drawable.profile_avatar_7,
    R.drawable.profile_avatar_8,
    R.drawable.profile_avatar_9,
    R.drawable.profile_avatar_10,
    R.drawable.profile_avatar_11,
    R.drawable.profile_avatar_12,
  )
  return avatars[index.floorMod(avatars.size)]
}

@Composable
private fun ProfilePinPadScreen(
  profile: StreamProfile?,
  pin: String,
  onPinChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  LaunchedEffect(pin) {
    if (pin.length == 4) onSubmit()
  }
  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    GlassCircleButton(
      onClick = onBack,
      modifier = Modifier
        .align(Alignment.TopStart)
        .statusBarsPadding()
        .padding(start = 24.dp, top = 34.dp)
        .size(54.dp),
    ) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(34.dp))
    }
    Column(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(horizontal = 34.dp)
        .offset(y = 178.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Box(
        modifier = Modifier
          .size(104.dp)
          .clip(CircleShape)
          .background(profileAvatarColor(profile?.avatarIndex ?: 0)),
        contentAlignment = Alignment.Center,
      ) {
        if (profile != null) ProfileAvatarImage(avatarIndex = profile.avatarIndex, modifier = Modifier.fillMaxSize())
      }
      Text(profile?.name ?: "Profile", color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
      Text("Enter your PIN", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f), fontSize = 20.sp, lineHeight = 24.sp)
      Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(top = 14.dp)) {
        repeat(4) { index ->
          Box(
            modifier = Modifier
              .size(16.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (index < pin.length) 0.92f else 0.22f)),
          )
        }
      }
      Column(
        modifier = Modifier.padding(top = 36.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "delete")).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            row.forEach { key ->
              PinPadButton(
                label = key,
                onClick = {
                  when {
                    key.isBlank() -> Unit
                    key == "delete" -> onPinChange(pin.dropLast(1))
                    pin.length < 4 -> onPinChange(pin + key)
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PinPadButton(label: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .size(width = 64.dp, height = 47.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(if (label.isBlank()) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
      .clickable(enabled = label.isNotBlank(), onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (label == "delete") {
      Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Delete digit", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    } else {
      Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
    }
  }
}
@Composable
private fun ProfileAvatarImage(avatarIndex: Int, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(profileAvatarResId(avatarIndex)),
    contentDescription = null,
    modifier = modifier,
    contentScale = ContentScale.Crop,
  )
}

@Composable
private fun ProfileAvatarPicker(selectedAvatarIndex: Int, onSelect: (Int) -> Unit) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
    items((0 until 12).toList(), key = { "profile-avatar-$it" }) { index ->
      Box(
        modifier = Modifier
          .size(58.dp)
          .clip(CircleShape)
          .background(profileAvatarColor(index))
          .border(2.dp, if (selectedAvatarIndex == index) Color.White else Color.Transparent, CircleShape)
          .clickable { onSelect(index) },
        contentAlignment = Alignment.Center,
      ) {
        ProfileAvatarImage(avatarIndex = index, modifier = Modifier.fillMaxSize())
        if (selectedAvatarIndex == index) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).size(18.dp))
        }
      }
    }
  }
}

@Composable
private fun ProfilePickerAvatar(profile: StreamProfile, onClick: () -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
    modifier = Modifier.width(100.dp).clickable(onClick = onClick),
  ) {
    Box(
      modifier = Modifier
        .size(100.dp),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .size(92.dp)
          .clip(CircleShape)
          .background(profileAvatarColor(profile.avatarIndex)),
      ) {
        ProfileAvatarImage(avatarIndex = profile.avatarIndex, modifier = Modifier.fillMaxSize())
      }
      if (profile.hasPinSet) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Color.Black, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Rounded.Lock, contentDescription = "PIN protected", tint = Color.Black, modifier = Modifier.size(16.dp))
        }
      }
    }
    Text(
      profile.name,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

@Composable
private fun AddProfileAvatar(onClick: () -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
    modifier = Modifier.width(112.dp).clickable(onClick = onClick),
  ) {
    Box(
      modifier = Modifier
        .size(92.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        .border(2.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f))
    }
    Text("Add Profile", maxLines = 1, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
  }
}

private fun profileAvatarColor(index: Int): Color {
  val colors = listOf(
    Color(0xFFE51E2A),
    Color(0xFFF4D81E),
    Color(0xFF1367F2),
    Color(0xFF16A34A),
    Color(0xFFB91C1C),
    Color(0xFF7C3AED),
  )
  return colors[index.floorMod(colors.size)]
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

private fun TraktItem.toMediaItem(): MediaItem = MediaItem(
  id = tmdbId?.toString() ?: id,
  type = type,
  title = title,
  year = year,
  poster = poster,
  backdrop = backdrop,
  rating = rating,
  description = description.orEmpty(),
  progress = progress,
  addedAt = addedAt,
  updatedAt = updatedAt,
)

private fun combinedContinueWatching(uiState: AppUiState): List<MediaItem> {
  val merged = linkedMapOf<String, MediaItem>()
  uiState.traktContinueWatching.forEach { item ->
    val media = item.toMediaItem()
    merged["${media.type}:${media.id}"] = media
  }
  uiState.localContinueWatching.forEach { item ->
    val key = "${item.type}:${item.id}"
    val existing = merged[key]
    merged[key] = if (existing == null) item else existing.copy(
      title = item.title.ifBlank { existing.title },
      year = item.year ?: existing.year,
      poster = item.poster ?: existing.poster,
      backdrop = item.backdrop ?: existing.backdrop,
      progress = maxOf(existing.progress ?: 0.0, item.progress ?: 0.0),
      updatedAt = maxOf(existing.updatedAt ?: 0L, item.updatedAt ?: 0L).takeIf { it > 0L } ?: existing.updatedAt ?: item.updatedAt,
    )
  }
  return merged.values.sortedWith(compareByDescending<MediaItem> { it.updatedAt ?: 0L }.thenByDescending { it.progress ?: 0.0 })
}

private fun isSeriesType(type: String): Boolean = type == "tv" || type == "series" || type == "show"
private fun homeHeroMediaKey(item: MediaItem): String = "${if (isSeriesType(item.type)) "tv" else item.type}:${item.id}"

private fun profileSwitcherHeroItems(sections: List<MediaSection>): List<MediaItem> {
  // Preferred sources first, then any remaining sections as a fallback, so the
  // hero keeps working even when the user disables or re-arranges builtin rows.
  val preferred = listOf("trending_movies", "trending_series", "new_movies", "new_series")
    .flatMap { sectionId -> sections.firstOrNull { it.id == sectionId }?.items.orEmpty() }
  val fallback = sections.flatMap { it.items }
  return (preferred + fallback)
    .filter { item -> item.type == "movie" || isSeriesType(item.type) }
    .filter { item -> !item.backdrop.isNullOrBlank() || !item.poster.isNullOrBlank() }
    .distinctBy { item -> "${item.type}:${item.id}" }
    .take(12)
}

private fun mixedHeroItems(sections: List<MediaSection>, continueWatching: List<MediaItem>, watchlist: List<MediaItem>): List<MediaItem> {
  fun section(id: String): List<MediaItem> = sections.firstOrNull { it.id == id }?.items.orEmpty()
  val buckets = listOf(
    section("new_movies"),
    section("new_series"),
    section("trending_movies"),
    section("trending_series"),
    continueWatching,
    watchlist,
  )
  val selected = mutableListOf<MediaItem>()
  val seen = mutableSetOf<String>()
  var position = 0
  while (selected.size < 12 && buckets.any { position < it.size }) {
    buckets.forEach { bucket ->
      val item = bucket.getOrNull(position) ?: return@forEach
      if (seen.add("${item.type}-${item.id}")) selected += item
      if (selected.size == 12) return selected
    }
    position += 1
  }
  return selected
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTab(uiState: AppUiState, scrollToTopSignal: Int, onReload: () -> Unit, onOpen: (MediaItem) -> Unit, onPlayContinueWatching: (MediaItem) -> Unit, onViewAll: (HomeRow) -> Unit, onToggleWatchlist: (MediaItem) -> Unit, onMarkWatched: (MediaItem) -> Unit, onRestartFromBeginning: (MediaItem) -> Unit, onResolveHeroTitleLogos: (List<MediaItem>) -> Unit, onResolveAddonRatings: (List<MediaItem>) -> Unit = {}, onToggleFavourite: (MediaItem) -> Unit = {}, onEnableAddon: (InstalledAddon) -> Unit = {}) {
  if (uiState.homeLoading && uiState.homeSections.isEmpty()) {
    SplashScene()
    return
  }

  val continueWatching = remember(uiState.traktContinueWatching, uiState.localContinueWatching) { combinedContinueWatching(uiState) }
  val rawHeroItems = remember(uiState.homeSections, continueWatching, uiState.mergedWatchlist) {
    mixedHeroItems(uiState.homeSections, continueWatching, uiState.mergedWatchlist)
  }
  LaunchedEffect(rawHeroItems) { onResolveHeroTitleLogos(rawHeroItems) }
  LaunchedEffect(uiState.homeSections, uiState.showAddonTmdbRatings, uiState.addonCatalogRatings.size) {
    if (uiState.showAddonTmdbRatings) onResolveAddonRatings(uiState.homeSections.flatMap { it.items })
  }
  val heroItems = remember(rawHeroItems, uiState.homeHeroTitleLogos) {
    rawHeroItems.map { item ->
      val resolvedLogo = uiState.homeHeroTitleLogos[homeHeroMediaKey(item)]
      if (item.titleLogo.isNullOrBlank() && !resolvedLogo.isNullOrBlank()) item.copy(titleLogo = resolvedLogo) else item
    }
  }
  val pagerState = rememberPagerState(pageCount = { heroItems.size })

  LaunchedEffect(heroItems.size, pagerState.settledPage) {
    if (heroItems.size <= 1) return@LaunchedEffect
    delay(5500)
    val nextPage = (pagerState.settledPage + 1) % heroItems.size
    pagerState.animateScrollToPage(nextPage)
  }

  val recommendations = remember(uiState.traktRecommendations) {
    uiState.traktRecommendations.map {
      MediaItem(
        id = it.tmdbId?.toString() ?: it.id,
        type = it.type,
        title = it.title,
        year = it.year,
        poster = it.poster,
        backdrop = it.backdrop,
        rating = it.rating,
        description = it.description.orEmpty(),
        progress = it.progress,
      )
    }
  }
  val trending = remember(uiState.traktTrending) {
    uiState.traktTrending.map {
      MediaItem(
        id = it.tmdbId?.toString() ?: it.id,
        type = it.type,
        title = it.title,
        year = it.year,
        poster = it.poster,
        backdrop = it.backdrop,
        rating = it.rating,
        description = it.description.orEmpty(),
        progress = it.progress,
      )
    }
  }
  val rows = remember(uiState.homeSections, continueWatching, recommendations, trending, uiState.mergedWatchlist, uiState.favouriteChannels, uiState.m3uChannels, uiState.m3uVodItems, uiState.addonCatalogRatings, uiState.showAddonTmdbRatings) {
    buildList {
      if (continueWatching.isNotEmpty()) add(HomeRow("continue", "Continue Watching", continueWatching))
      if (uiState.favouriteChannels.isNotEmpty()) add(HomeRow("favourites", "Live TV Favourites", uiState.favouriteChannels))
      // Home only needs a small preview. View All receives the complete list separately, which
      // keeps composition and card clicks bounded even for very large IPTV playlists.
      if (uiState.m3uChannels.isNotEmpty()) add(HomeRow("m3u_playlists_live", "Playlist Live TV", uiState.m3uChannels.take(30)))
      if (uiState.m3uVodItems.isNotEmpty()) add(HomeRow("m3u_playlists_vod", "Playlist VOD", uiState.m3uVodItems.take(30)))
      uiState.homeSections.forEach { section ->
        if (section.items.isEmpty()) return@forEach
        val items = if (!uiState.showAddonTmdbRatings) section.items else section.items.map { item ->
          if (item.rating != null) item else uiState.addonCatalogRatings[homeHeroMediaKey(item)]?.let { item.copy(rating = it) } ?: item
        }
        add(HomeRow(section.id, section.title, items))
      }
      if (recommendations.isNotEmpty()) add(HomeRow("recommended", "Recommended For You", recommendations))
      if (trending.isNotEmpty()) add(HomeRow("trending", "Trending On Trakt", trending))
      if (uiState.mergedWatchlist.isNotEmpty()) add(HomeRow("watchlist", "Watchlist", uiState.mergedWatchlist))
    }
  }
  val heroBackdrop = heroItems.getOrNull(pagerState.currentPage.coerceIn(0, (heroItems.size - 1).coerceAtLeast(0)))
  val listState = rememberLazyListState()

  // Track the last handled signal so re-entering composition (e.g. returning from a
  // detail page with restored scroll state) doesn't replay an old scroll-to-top.
  var handledScrollToTopSignal by rememberSaveable { mutableIntStateOf(scrollToTopSignal) }
  LaunchedEffect(scrollToTopSignal) {
    if (scrollToTopSignal > handledScrollToTopSignal) {
      handledScrollToTopSignal = scrollToTopSignal
      listState.animateScrollToItem(0)
    }
  }

  PullToRefreshBox(isRefreshing = uiState.homeLoading, onRefresh = onReload, modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    val lightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    if (uiState.vividAmbient && heroBackdrop != null) {
      AsyncImage(
        model = heroBackdrop.backdrop ?: heroBackdrop.poster,
        contentDescription = null,
        modifier = Modifier
          .fillMaxSize()
          .blur(38.dp)
          .graphicsLayer { alpha = if (lightMode) 0.38f else 0.30f },
        contentScale = ContentScale.Crop,
      )
      val tint = uiState.ambientTintPercent
      Box(modifier = Modifier.fillMaxSize().background(if (lightMode) MaterialTheme.colorScheme.background.copy(alpha = ambientTintAlpha(0.48f, tint)) else Color.Black.copy(alpha = ambientTintAlpha(0.36f, tint))))
      Box(
        modifier = Modifier.fillMaxSize().background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              0.00f to Color.Transparent,
              0.42f to (if (lightMode) MaterialTheme.colorScheme.background.copy(alpha = ambientTintAlpha(0.10f, tint)) else Color.Black.copy(alpha = ambientTintAlpha(0.36f, tint))),
              0.72f to MaterialTheme.colorScheme.background.copy(alpha = ambientTintAlpha(if (lightMode) 0.55f else 0.54f, tint)),
              1.00f to MaterialTheme.colorScheme.background.copy(alpha = ambientTintAlpha(1f, tint).coerceAtLeast(0.62f)),
            ),
          ),
        ),
      )
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 104.dp),
      verticalArrangement = Arrangement.spacedBy(if (heroItems.isNotEmpty()) 12.dp else 26.dp),
    ) {
      if (heroItems.isNotEmpty()) {
        item {
          HomeHeroCarousel(
            items = heroItems,
            pagerState = pagerState,
            onOpen = onOpen,
            showSynopsis = uiState.showHeroSynopsis,
            scrollOffset = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0 },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
      itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
        Column {
          if (heroItems.isNotEmpty() && index > 0) Spacer(modifier = Modifier.height(14.dp))
          HomeStrip(rowId = row.id, title = row.title, items = row.items, continueWatchingStyle = uiState.continueWatchingStyle, liveLandscapeCards = uiState.liveLandscapeCards, watchlistItems = uiState.mergedWatchlist, favouriteItems = uiState.favouriteChannels, addons = uiState.addons, onOpen = onOpen, onViewAll = { onViewAll(row) }, onToggleWatchlist = onToggleWatchlist, onToggleFavourite = onToggleFavourite, onEnableAddon = onEnableAddon, onMarkWatched = onMarkWatched, onRestartFromBeginning = onRestartFromBeginning, onPlayContinueWatching = onPlayContinueWatching)
        }
      }
    }
  }
  }
}

private data class HomeHeroLayer(
  val page: Int,
  val visibility: Float,
  val offset: Float,
)

private fun homeHeroPageOffset(
  pagerState: androidx.compose.foundation.pager.PagerState,
  page: Int,
): Float {
  val fraction = pagerState.currentPageOffsetFraction
  // A freshly measured pager can briefly report a stale/NaN fraction. The hero layer
  // multiplies this by 22x the hero width, so even a tiny residual would push the
  // image clean off screen — treat anything near zero as settled.
  val safeFraction = if (fraction.isNaN() || kotlin.math.abs(fraction) < 0.001f) 0f else fraction
  return (pagerState.currentPage - page) + safeFraction
}

private fun homeHeroPageVisibility(
  pagerState: androidx.compose.foundation.pager.PagerState,
  page: Int,
): Float = (1f - kotlin.math.abs(homeHeroPageOffset(pagerState, page))).coerceIn(0f, 1f)

@Composable
private fun HeroTitleArtwork(
  url: String,
  title: String,
  modifier: Modifier,
  alignment: Alignment = Alignment.Center,
  onError: (() -> Unit)? = null,
) {
  val lightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
  Box(modifier = modifier, contentAlignment = alignment) {
    if (lightMode) {
      AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().offset(y = 1.dp).blur(3.dp).graphicsLayer { alpha = 0.58f },
        contentScale = ContentScale.Fit,
        alignment = alignment,
        colorFilter = ColorFilter.tint(Color.Black),
      )
    }
    AsyncImage(
      model = url,
      contentDescription = title,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Fit,
      alignment = alignment,
      onError = { onError?.invoke() },
    )
  }
}

@Composable
private fun HomeHeroTitle(item: MediaItem, lightMode: Boolean) {
  var logoFailed by remember(item.id, item.titleLogo) { mutableStateOf(false) }
  val titleLogo = item.titleLogo
  if (!titleLogo.isNullOrBlank() && !logoFailed) {
    HeroTitleArtwork(
      url = titleLogo,
      title = item.title,
      modifier = Modifier.fillMaxWidth(0.74f).height(92.dp),
      onError = { logoFailed = true },
    )
  } else {
    Text(
      item.title,
      style = MaterialTheme.typography.displayMedium.copy(shadow = if (lightMode) Shadow(Color.Black.copy(alpha = 0.72f), Offset(0f, 2f), 5f) else null),
      fontWeight = FontWeight.Black,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      color = Color.White,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
  }
}

@Composable
private fun HomeHeroCarousel(
  items: List<MediaItem>,
  pagerState: androidx.compose.foundation.pager.PagerState,
  onOpen: (MediaItem) -> Unit,
  showSynopsis: Boolean,
  scrollOffset: () -> Int,
  modifier: Modifier = Modifier,
) {
  if (items.isEmpty()) return
  val lightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val pagerScope = rememberCoroutineScope()
  BoxWithConstraints(
    modifier = modifier.fillMaxWidth(),
  ) {
    val currentPage = pagerState.currentPage.coerceIn(items.indices)
    val currentItem = items[currentPage]
    val heroHeight = 640.dp
    val heroDotsLaneHeight = 26.dp
    val heroBoundaryFadeHeight = 260.dp
    val heroWidthPx = maxWidth.value
    val heroItemSpacing = if (maxWidth < 390.dp) 4.dp else if (showSynopsis) 8.dp else 6.dp
    val heroCtaSpacing = if (maxWidth < 390.dp || !showSynopsis) 4.dp else 7.dp
    // While settled, draw the current page dead centre at full opacity. Anything else
    // risks a mis-measured offset hiding the hero until the user swipes it back.
    val visiblePages = if (!pagerState.isScrollInProgress) {
      listOf(HomeHeroLayer(currentPage, 1f, 0f))
    } else {
      listOf(
        currentPage,
        (currentPage - 1).coerceAtLeast(0),
        (currentPage + 1).coerceAtMost(items.lastIndex),
      ).distinct().mapNotNull { index ->
        val offset = homeHeroPageOffset(pagerState, index)
        val visibility = homeHeroPageVisibility(pagerState, index)
        if (visibility <= 0f) null else HomeHeroLayer(index, visibility, offset)
      }.sortedBy { it.visibility }
        // Never end up with nothing to draw.
        .ifEmpty { listOf(HomeHeroLayer(currentPage, 1f, 0f)) }
    }

    Box(modifier = Modifier.fillMaxWidth().height(heroHeight + heroDotsLaneHeight)) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(heroHeight).graphicsLayer { alpha = 0.01f },
      ) { Box(modifier = Modifier.fillMaxSize()) }

      Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(heroHeight).clip(RectangleShape)) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationY = scrollOffset().toFloat() * 0.46f }) {
          visiblePages.forEach { layer ->
            AsyncImage(
              model = items[layer.page].backdrop ?: items[layer.page].poster,
              contentDescription = items[layer.page].title,
              modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = layer.visibility
                translationX = -layer.offset * heroWidthPx * 22f
                scaleX = 1.08f
                scaleY = 1.08f
              },
              contentScale = ContentScale.Crop,
              alignment = Alignment.Center,
            )
          }

          if (!lightMode) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .height(240.dp)
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f), Color.Black),
                ),
              ),
          )
          }

        }

        if (!lightMode) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .height(heroBoundaryFadeHeight / 2)
              .background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)),
              ),
          )
        }

        Column(
          modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp).padding(top = 42.dp, bottom = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(heroCtaSpacing),
        ) {
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            visiblePages.forEach { layer ->
              Box(modifier = Modifier.graphicsLayer { alpha = layer.visibility; translationX = -layer.offset * heroWidthPx * 12f }) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(heroItemSpacing),
              ) {
                HomeHeroTitle(items[layer.page], lightMode)
                Text(
                  (listOf(if (items[layer.page].type == "tv" || items[layer.page].type == "series") "Series" else "Movie") + items[layer.page].genres.take(3)).joinToString(" \u00B7 "),
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color.White.copy(alpha = 0.72f),
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (showSynopsis && items[layer.page].description.isNotBlank()) {
                  Text(
                    items[layer.page].description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.88f),
                  )
                }
              }
              }
            }
          }

          Button(
            onClick = { onOpen(currentItem) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth(0.68f),
          ) {
            Text("View Details", fontWeight = FontWeight.ExtraBold)
          }
        }
      }

      if (items.size > 1) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(heroDotsLaneHeight),
          contentAlignment = Alignment.BottomCenter,
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, _ ->
              val activeFraction = homeHeroPageVisibility(pagerState, index)
              Box(
                modifier = Modifier
                  .clickable { pagerScope.launch { pagerState.animateScrollToPage(index) } }
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.onBackground)
                  .graphicsLayer { alpha = 0.30f + (0.62f * activeFraction) }
                  .width((8f + (18f * activeFraction)).dp)
                  .height(8.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NetworkBrowseScreen(network: MediaItem, headerStyle: HeaderStyle, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
  val api = remember { StreamDekApiClient() }
  BackHandler(onBack = onBack)
  var query by rememberSaveable(network.id) { mutableStateOf("") }
  var type by rememberSaveable(network.id) { mutableStateOf("all") }
  var sort by rememberSaveable(network.id) { mutableStateOf("trending") }
  var genreId by rememberSaveable(network.id) { mutableStateOf<Int?>(null) }
  var year by rememberSaveable(network.id) { mutableStateOf<String?>(null) }
  var selectionSheet by rememberSaveable(network.id) { mutableStateOf<String?>(null) }
  var columns by rememberSaveable(network.id) { mutableStateOf(3) }
  var genres by remember(network.id) { mutableStateOf<List<DiscoverGenre>>(emptyList()) }
  var catalogItems by remember(network.id) { mutableStateOf<List<MediaItem>>(emptyList()) }
  var loading by remember(network.id) { mutableStateOf(true) }
  var page by remember(network.id) { mutableStateOf(1) }
  var totalPages by remember(network.id) { mutableStateOf(1) }
  var activeRequestToken by remember(network.id) { mutableStateOf("") }
  val listState = rememberLazyGridState()
  val years: List<String> = remember { (java.time.Year.now().value downTo 1980).map(Int::toString) }
  val scope = rememberCoroutineScope()
  val modernHeader = headerStyle == HeaderStyle.Modern
  val headerHazeState = rememberHazeState()

  fun load(targetPage: Int, append: Boolean) {
    val requestedQuery = query.trim()
    val requestToken = listOf(network.id, targetPage, type, genreId, year, sort, requestedQuery, System.nanoTime()).joinToString(":")
    activeRequestToken = requestToken
    loading = true
    scope.launch {
      val firstPage = api.fetchNetworkCatalog(network.id, targetPage, type, genreId, year, sort, "")
        .getOrElse {
          if (activeRequestToken == requestToken) {
            if (!append) catalogItems = emptyList()
            loading = false
          }
          return@launch
        }
      if (activeRequestToken != requestToken) return@launch

      if (requestedQuery.isNotBlank() && !append) {
        val collected = firstPage.items.toMutableList()
        val lastSearchPage = minOf(firstPage.totalPages, 20)
        if (lastSearchPage > 1) {
          (2..lastSearchPage).chunked(4).forEach { pageChunk ->
            val chunkItems = coroutineScope {
              pageChunk.map { searchPage ->
                async { api.fetchNetworkCatalog(network.id, searchPage, type, genreId, year, sort, "").getOrNull()?.items.orEmpty() }
              }.awaitAll()
            }
            if (activeRequestToken != requestToken) return@launch
            chunkItems.forEach(collected::addAll)
          }
        }
        val queryTokens = requestedQuery.lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        catalogItems = collected
          .distinctBy { item -> "${item.type}:${item.id}" }
          .filter { item ->
            val searchText = listOf(item.title, item.year, item.description).joinToString(" ").lowercase()
            queryTokens.all(searchText::contains)
          }
        page = 1
        totalPages = 1
      } else {
        catalogItems = if (append) (catalogItems + firstPage.items).distinctBy { item -> "${item.type}:${item.id}" } else firstPage.items
        page = firstPage.page
        totalPages = firstPage.totalPages
      }
      if (activeRequestToken != requestToken) return@launch
      loading = false
    }
  }

  LaunchedEffect(type) {
    val genreType = if (type == "tv") "tv" else "movie"
    api.fetchDiscoverGenres(genreType).onSuccess { genres = it }
  }
  LaunchedEffect(network.id, type, sort, genreId, year, query) {
    delay(if (query.isBlank()) 0 else 350)
    load(1, false)
  }
  LaunchedEffect(listState, page, totalPages, loading, query) {
    snapshotFlow { listState.canScrollForward }.collect { canScrollForward ->
      if (query.isBlank() && !canScrollForward && !loading && page < totalPages) load(page + 1, true)
    }
  }

  val selectionOptions = remember(selectionSheet, type, sort, genreId, year, genres) {
    when (selectionSheet) {
      "type" -> listOf(
        SearchSelectionOption("All", type == "all") { type = "all"; selectionSheet = null },
        SearchSelectionOption("Movies", type == "movie") { type = "movie"; selectionSheet = null },
        SearchSelectionOption("Series", type == "tv") { type = "tv"; selectionSheet = null },
      )
      "genre" -> buildList {
        add(SearchSelectionOption("All Genres", genreId == null) { genreId = null; selectionSheet = null })
        genres.forEach { genre ->
          add(SearchSelectionOption(genre.name, genreId == genre.id) { genreId = genre.id; selectionSheet = null })
        }
      }
      "year" -> buildList {
        add(SearchSelectionOption("Any Year", year == null) { year = null; selectionSheet = null })
        years.forEach { option ->
          add(SearchSelectionOption(option, year == option) { year = option; selectionSheet = null })
        }
      }
      else -> emptyList()
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(columns),
      state = listState,
      modifier = Modifier.fillMaxSize().then(if (modernHeader) Modifier.hazeSource(headerHazeState) else Modifier),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (modernHeader) 272.dp else 274.dp, bottom = 126.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      when {
        loading && catalogItems.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
          Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        catalogItems.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
          LibraryEmptyState(
            icon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(48.dp)) },
            title = "No matching titles",
            subtitle = "Try changing the search or filters.",
          )
        }
        else -> gridItems(catalogItems, key = { item -> "${item.type}:${item.id}" }) { item ->
          LibraryPosterTile(item = item, modifier = Modifier.fillMaxWidth(), showMeta = false, onClick = { onOpen(item) })
        }
      }
      if (loading && catalogItems.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
        }
      }
    }

    if (modernHeader) {
      val lightHeader = MaterialTheme.colorScheme.background.luminance() > 0.5f
      Box(modifier = Modifier.align(Alignment.TopCenter).zIndex(2f).fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        FrostedGlassSurface(
          modifier = Modifier.fillMaxWidth().height(204.dp),
          shape = RoundedCornerShape(30.dp),
          hazeStateOverride = headerHazeState,
          blurRadius = 68f,
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          tintAlpha = if (lightHeader) 0.14f else 0.06f,
          borderAlpha = if (lightHeader) 0.10f else 0f,
          baseAlpha = if (lightHeader) 0.28f else 0.08f,
          fillColorOverride = if (lightHeader) null else Color.White,
          showEdgeGradient = false,
        ) {
          NetworkCatalogHeaderContent(
            network = network,
            query = query,
            onQueryChange = { query = it },
            columns = columns,
            onToggleColumns = { columns = if (columns == 3) 2 else 3 },
            type = type,
            genres = genres,
            genreId = genreId,
            year = year,
            onOpenFilter = { selectionSheet = it },
          )
        }
      }
    } else {
      NetworkCatalogHeaderContent(
        network = network,
        query = query,
        onQueryChange = { query = it },
        columns = columns,
        onToggleColumns = { columns = if (columns == 3) 2 else 3 },
        type = type,
        genres = genres,
        genreId = genreId,
        year = year,
        onOpenFilter = { selectionSheet = it },
        modifier = Modifier.align(Alignment.TopCenter).zIndex(2f).fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f)).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
      )
    }
  }
  if (selectionSheet != null && selectionOptions.isNotEmpty()) {
    SearchSelectionDialog(
      title = when (selectionSheet) {
        "type" -> "Choose Type"
        "genre" -> "Choose Genre"
        "year" -> "Choose Year"
        else -> "Choose Filter"
      },
      options = selectionOptions,
      onDismiss = { selectionSheet = null },
    )
  }
}

@Composable
private fun NetworkCatalogHeaderContent(
  network: MediaItem,
  query: String,
  onQueryChange: (String) -> Unit,
  columns: Int,
  onToggleColumns: () -> Unit,
  type: String,
  genres: List<DiscoverGenre>,
  genreId: Int?,
  year: String?,
  onOpenFilter: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AdaptivePageTitle(title = networkCatalogDisplayName(network.title), maxLines = 2)
        Text("Network catalog", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
      }
      GlassCircleButton(onClick = onToggleColumns) {
        Icon(if (columns == 3) Icons.Rounded.ViewAgenda else Icons.Rounded.ViewModule, contentDescription = "Change grid size", tint = MaterialTheme.colorScheme.onBackground)
      }
    }
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      modifier = Modifier.fillMaxWidth(),
      placeholder = { InputGuideText("Search within this network") },
      leadingIcon = { Icon(Icons.Rounded.Search, null) },
      trailingIcon = if (query.isNotBlank()) ({ IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, "Clear") } }) else null,
      singleLine = true,
      shape = RoundedCornerShape(18.dp),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      SearchDiscoverField("Type", when (type) { "movie" -> "Movies"; "tv" -> "Series"; else -> "Type" }, Modifier.weight(1f)) { onOpenFilter("type") }
      SearchDiscoverField("Genre", genres.firstOrNull { it.id == genreId }?.name ?: "Genre", Modifier.weight(1f), enabled = genres.isNotEmpty()) { onOpenFilter("genre") }
      SearchDiscoverField("Year", year ?: "Year", Modifier.weight(1f)) { onOpenFilter("year") }
    }
  }
}
@Composable
private fun NetworkCatalogLandscapeGrid(items: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    items.chunked(2).forEach { rowItems ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        rowItems.forEach { item ->
          NetworkCatalogLandscapeCard(item = item, modifier = Modifier.weight(1f), onClick = { onOpen(item) })
        }
        if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun NetworkCatalogLandscapeCard(item: MediaItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Box(
    modifier = modifier
      .aspectRatio(16f / 10f)
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.surface)
      .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
      .clickable(onClick = onClick),
  ) {
    AsyncImage(
      model = item.backdrop ?: item.poster,
      contentDescription = item.title,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop,
    )
    Box(
      modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(
          colorStops = arrayOf(
            0.00f to Color.Black.copy(alpha = 0.04f),
            0.48f to Color.Black.copy(alpha = 0.16f),
            1.00f to Color.Black.copy(alpha = 0.88f),
          ),
        ),
      ),
    )
    CardImdbRatingBadge(rating = item.rating, topPadding = 5.dp)
    Column(
      modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(item.title, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(item.year ?: item.type.replaceFirstChar(Char::uppercase), color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, maxLines = 1)
    }
  }
}
private fun networkCatalogDisplayName(title: String): String =
  if (title.contains("Amazon", ignoreCase = true) && title.contains("Prime", ignoreCase = true)) "Amazon Prime" else title

@Composable
private fun AdaptivePageTitle(
  title: String,
  modifier: Modifier = Modifier,
  maxLines: Int = 2,
  color: Color = MaterialTheme.colorScheme.onBackground,
) {
  val adaptiveSize = when {
    title.length >= 38 -> 22.sp
    title.length >= 30 -> 24.sp
    title.length >= 22 -> 27.sp
    else -> PageTitleSize
  }
  Text(
    text = title,
    modifier = modifier,
    fontSize = adaptiveSize,
    lineHeight = (adaptiveSize.value + 4f).sp,
    fontWeight = FontWeight.ExtraBold,
    color = color,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
  )
}

private val builtInPageableRowIds = setOf("new_movies", "new_series", "trending_movies", "trending_series")

@Composable
private fun BrowseSectionScreen(row: HomeRow, loadedItems: List<MediaItem>, returnItemId: String?, headerStyle: HeaderStyle, liveLandscapeCards: Boolean, watchlistItems: List<MediaItem>, favouriteItems: List<MediaItem> = emptyList(), addons: List<InstalledAddon> = emptyList(), onBack: () -> Unit, onOpen: (MediaItem) -> Unit, onToggleWatchlist: (MediaItem) -> Unit, onToggleFavourite: (MediaItem) -> Unit = {}, onEnableAddon: (InstalledAddon) -> Unit = {}, onMarkWatched: (MediaItem) -> Unit, onLoadMore: (suspend (String, MediaItem?, Int) -> List<MediaItem>)? = null) {
  fun isFavourite(item: MediaItem): Boolean = favouriteItems.any { it.id == item.id && it.type == item.type }
  var filter by rememberSaveable(row.id) { mutableStateOf(MediaFilter.All) }
  var columns by rememberSaveable(row.id) { mutableStateOf(3) }
  var actionItem by remember { mutableStateOf<MediaItem?>(null) }
  var disabledAddonPrompt by remember { mutableStateOf<InstalledAddon?>(null) }
  val isFavouritesRow = row.id == "favourites"
  fun addonFor(item: MediaItem): InstalledAddon? = item.sourceAddonId?.let { id -> addons.firstOrNull { it.id == id } }
  fun handleOpen(item: MediaItem) {
    val addon = if (isFavouritesRow) addonFor(item) else null
    if (addon != null && !addon.enabled) disabledAddonPrompt = addon else onOpen(item)
  }
  val browseHazeState = rememberHazeState()
  val modernHeader = headerStyle == HeaderStyle.Modern
  val isM3uRow = row.id.startsWith("m3u_playlists_")
  val isLiveRow = row.id == "m3u_playlists_live" || row.title.contains("live", true) || row.title.contains("sport", true) || row.items.any(MediaItem::isLiveCatalogItem)
  val isNetworkRow = row.id == "streaming_networks" || (!isM3uRow && row.items.any { it.type == "network" })
  val usesLandscapeCards = (isLiveRow && liveLandscapeCards) || isNetworkRow
  var query by rememberSaveable(row.id) { mutableStateOf("") }
  var browseSort by rememberSaveable(row.id) { mutableStateOf(BrowseSort.Original) }
  // "View All" starts from whatever the home row already fetched, then pages in more from the
  // same add-on catalog as the user scrolls — most add-on catalogs only return a handful of
  // items per request so large add-on catalogs are fetched incrementally.
  var isLoadingMore by remember(row.id) { mutableStateOf(false) }
  var canLoadMore by remember(row.id) {
    mutableStateOf(
      onLoadMore != null && !isM3uRow && row.items.isNotEmpty() &&
        (
          row.items.last().let { it.sourceAddonId != null && it.sourceCatalogType != null && it.sourceCatalogId != null } ||
            row.id in builtInPageableRowIds
        ),
    )
  }
  // M3U parser IDs are already unique; do not allocate another full list just to deduplicate a
  // potentially huge playlist. Other rows keep their existing defensive de-duplication.
  val uniqueItems = remember(row.id, loadedItems) { if (isM3uRow) loadedItems else loadedItems.distinctBy { "${it.type}-${it.id}" } }
  val hasMovies = remember(uniqueItems, isM3uRow) { !isM3uRow && uniqueItems.any { it.type.equals("movie", true) } }
  val hasSeries = remember(uniqueItems, isM3uRow) { !isM3uRow && uniqueItems.any { it.type.equals("tv", true) || it.type.equals("series", true) } }
  val showsTypeFilters = hasMovies && hasSeries
  // Live TV / sports rows hold long channel lists, so they get local search. Filtering and
  // sorting run on Default rather than the UI thread so very large IPTV lists remain responsive.
  val showSearch = isLiveRow || uniqueItems.size >= 24
  var filteredItems by remember(row.id) { mutableStateOf<List<MediaItem>>(emptyList()) }
  LaunchedEffect(uniqueItems, filter, showsTypeFilters, query, browseSort) {
    filteredItems = withContext(Dispatchers.Default) {
      val typeFiltered = if (showsTypeFilters) uniqueItems.filteredBy(filter) else uniqueItems
      val normalizedQuery = query.trim()
      val searched = if (normalizedQuery.isBlank()) typeFiltered else typeFiltered.filter {
        it.title.contains(normalizedQuery, ignoreCase = true) || it.description.contains(normalizedQuery, ignoreCase = true)
      }
      when (browseSort) {
        BrowseSort.Original -> searched
        BrowseSort.TitleAscending -> searched.sortedWith(compareBy<MediaItem> { it.title.lowercase() })
        BrowseSort.TitleDescending -> searched.sortedWith(compareByDescending<MediaItem> { it.title.lowercase() })
      }
    }
  }
  val showHeaderFilters = !isNetworkRow && showsTypeFilters
  val searchExtra = if (showSearch) 72.dp else 0.dp
  val modernHeaderHeight = (if (showHeaderFilters) 164.dp else 124.dp) + searchExtra
  val modernContentTop = (if (showHeaderFilters) 234.dp else 194.dp) + searchExtra
  val classicContentTop = (if (showHeaderFilters) 202.dp else 152.dp) + searchExtra
  BackHandler(onBack = onBack)

  val gridState = rememberLazyGridState()
  LaunchedEffect(returnItemId, filteredItems.size, query, browseSort) {
    val targetId = returnItemId ?: return@LaunchedEffect
    val targetIndex = withContext(Dispatchers.Default) { filteredItems.indexOfFirst { it.id == targetId } }
    if (targetIndex >= 0) gridState.scrollToItem(targetIndex)
  }
  // Only auto-page while the user is looking at the unfiltered list: once "skip" no longer
  // lines up with what's on screen (a search/type filter is active), fetching more would just
  // silently re-request items already loaded.
  val canAutoPage = canLoadMore && filter == MediaFilter.All && query.isBlank() && browseSort == BrowseSort.Original
  // Deliberately NOT info.totalItemsCount: the loading-spinner row below is itself one more
  // grid item while isLoadingMore is true, so totalItemsCount ticks up the instant a fetch
  // starts. That shifted this threshold just enough to flip nearEnd back to false one frame
  // later, restarting this effect's LaunchedEffect (nearEnd is one of its keys) and cancelling
  // the fetch coroutine mid-flight — logs showed the backend request succeeding with a full
  // page of items that then got thrown away as a cancellation. filteredItems.size is the count
  // of actual content tiles, unaffected by the spinner, so it doesn't move under the request.
  // Keyed on filteredItems.size so the derivedStateOf's closure picks up the current count —
  // remember with no keys would otherwise freeze this on the very first composition's count.
  val nearEnd by remember(filteredItems.size) {
    derivedStateOf {
      val info = gridState.layoutInfo
      val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
      filteredItems.isNotEmpty() && lastVisible >= filteredItems.size - 6
    }
  }
  // isLoadingMore is intentionally NOT a key here: it's mutated inside this same effect, and a
  // LaunchedEffect cancels-and-relaunches itself the instant one of its own keys changes. With
  // it listed as a key, setting it to true immediately restarted this effect, which cancelled
  // the in-flight fetch before it could ever finish and left loading stuck forever — the exact
  // "never loads more" bug. It only needs to be checked (as a re-entrancy guard), not restarted on.
  LaunchedEffect(row.id, nearEnd, canAutoPage) {
    android.util.Log.d("StreamDekPaging", "effect check row=${row.id} nearEnd=$nearEnd canAutoPage=$canAutoPage isLoadingMore=$isLoadingMore canLoadMore=$canLoadMore items=${uniqueItems.size}")
    if (!nearEnd || !canAutoPage || isLoadingMore) return@LaunchedEffect
    val anchor = row.items.lastOrNull() ?: return@LaunchedEffect
    val loader = onLoadMore ?: return@LaunchedEffect
    isLoadingMore = true
    val skip = uniqueItems.size
    android.util.Log.d("StreamDekPaging", "fetching more row=${row.id} skip=$skip anchorAddon=${anchor.sourceAddonId} anchorCatalog=${anchor.sourceCatalogType}/${anchor.sourceCatalogId}")
    // runCatching catches EVERYTHING, including CancellationException — and this effect gets
    // legitimately cancelled-and-relaunched whenever nearEnd flips (that's the whole point of
    // listing it as a key). Swallowing that cancellation instead of rethrowing it made an
    // in-flight fetch that was cancelled right as it succeeded look identical to "the add-on
    // returned nothing", which is what was permanently disabling further loading after exactly
    // one page. Cancellation must propagate, not be treated as a normal failure.
    val fetched = try {
      loader(row.id, anchor, skip)
    } catch (e: kotlinx.coroutines.CancellationException) {
      isLoadingMore = false
      throw e
    } catch (e: Throwable) {
      android.util.Log.w("StreamDekPaging", "loadMore threw for row=${row.id}", e)
      emptyList()
    }
    val existingKeys = uniqueItems.mapTo(mutableSetOf()) { "${it.type}-${it.id}" }
    val newOnes = fetched.filter { "${it.type}-${it.id}" !in existingKeys }
    android.util.Log.d("StreamDekPaging", "result row=${row.id} fetched=${fetched.size} new=${newOnes.size}")
    if (newOnes.isEmpty()) canLoadMore = false
    isLoadingMore = false
  }

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyVerticalGrid(
      state = gridState,
      columns = GridCells.Fixed(if (usesLandscapeCards) 2 else columns),
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .then(if (modernHeader) Modifier.hazeSource(browseHazeState) else Modifier),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = if (modernHeader) modernContentTop else classicContentTop, bottom = 126.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      if (filteredItems.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          LibraryEmptyState(icon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f), modifier = Modifier.size(54.dp)) }, title = "No titles here", subtitle = "This section does not have matching items for the selected filter.")
        }
      } else {
        gridItems(filteredItems, key = { "${it.type}-${it.id}" }) { item ->
          val disabled = isFavouritesRow && addonFor(item)?.enabled == false
          when {
            isLiveRow && liveLandscapeCards -> NetworkHomeCard(item = item, sports = true, modifier = Modifier.fillMaxWidth(), dimmed = disabled, favourite = isFavourite(item), onClick = { handleOpen(item) })
            isNetworkRow -> NetworkHomeCard(item = item, sports = false, modifier = Modifier.fillMaxWidth(), dimmed = disabled, favourite = isFavourite(item), onClick = { handleOpen(item) })
            else -> LibraryPosterTile(item = item, modifier = Modifier.alpha(if (disabled) 0.4f else 1f), showMeta = false, onClick = { handleOpen(item) }, onLongPress = { actionItem = item })
          }
        }
        if (isLoadingMore) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), strokeWidth = 2.5.dp)
            }
          }
        }
      }
    }

    if (modernHeader) {
      val lightHeader = MaterialTheme.colorScheme.background.luminance() > 0.5f
      Box(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .zIndex(4f)
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
      ) {
        FrostedGlassSurface(
          modifier = Modifier.fillMaxWidth().height(modernHeaderHeight),
          shape = RoundedCornerShape(30.dp),
          hazeStateOverride = browseHazeState,
          blurRadius = 68f,
          contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
          tintAlpha = if (lightHeader) 0.14f else 0.06f,
          borderAlpha = if (lightHeader) 0.10f else 0f,
          baseAlpha = if (lightHeader) 0.28f else 0.08f,
          fillColorOverride = if (lightHeader) null else Color.White,
          showEdgeGradient = false,
        ) {
          BrowseSectionHeaderContent(
            title = row.title,
            countLabel = when {
              isNetworkRow -> "${filteredItems.size} networks"
              isLiveRow -> "${filteredItems.size} channels"
              else -> "${filteredItems.size} titles"
            },
            columns = columns,
            showColumnToggle = !usesLandscapeCards,
            onToggleColumns = { columns = if (columns == 3) 2 else 3 },
            selectedFilter = filter,
            showFilters = showHeaderFilters,
            onFilterChange = { filter = it },
            query = query,
            showSearch = showSearch,
            onQueryChange = { query = it },
            showSort = isM3uRow,
            sortLabel = when (browseSort) {
              BrowseSort.Original -> "Playlist order"
              BrowseSort.TitleAscending -> "A-Z"
              BrowseSort.TitleDescending -> "Z-A"
            },
            onToggleSort = {
              browseSort = when (browseSort) {
                BrowseSort.Original -> BrowseSort.TitleAscending
                BrowseSort.TitleAscending -> BrowseSort.TitleDescending
                BrowseSort.TitleDescending -> BrowseSort.Original
              }
            },
          )
        }
      }
    } else {
      Box(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .zIndex(4f)
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
          .statusBarsPadding()
          .padding(horizontal = 20.dp, vertical = 12.dp),
      ) {
        BrowseSectionHeaderContent(
          title = row.title,
          countLabel = when {
            isNetworkRow -> "${filteredItems.size} networks"
            isLiveRow -> "${filteredItems.size} channels"
            else -> "${filteredItems.size} titles"
          },
          columns = columns,
          showColumnToggle = !usesLandscapeCards,
          onToggleColumns = { columns = if (columns == 3) 2 else 3 },
          selectedFilter = filter,
          showFilters = showHeaderFilters,
          onFilterChange = { filter = it },
          query = query,
          showSearch = showSearch,
          onQueryChange = { query = it },
          showSort = isM3uRow,
          sortLabel = when (browseSort) {
            BrowseSort.Original -> "Playlist order"
            BrowseSort.TitleAscending -> "A-Z"
            BrowseSort.TitleDescending -> "Z-A"
          },
          onToggleSort = {
            browseSort = when (browseSort) {
              BrowseSort.Original -> BrowseSort.TitleAscending
              BrowseSort.TitleAscending -> BrowseSort.TitleDescending
              BrowseSort.TitleDescending -> BrowseSort.Original
            }
          },
        )
      }
    }
  }
  actionItem?.let { item ->
    if (isFavouritesRow) {
      FavouriteChannelActionsDialog(
        item = item,
        onRemoveFromFavourites = { onToggleFavourite(item) },
        onDismiss = { actionItem = null },
      )
    } else {
      MediaCardActionsDialog(
        item = item,
        inWatchlist = watchlistItems.any { it.id == item.id && it.type == item.type },
        includeRemoveAction = true,
        onAddToWatchlist = { onToggleWatchlist(item) },
        onRemoveFromWatchlist = { onToggleWatchlist(item) },
        onMarkWatched = { onMarkWatched(item) },
        onDismiss = { actionItem = null },
      )
    }
  }
  disabledAddonPrompt?.let { addon ->
    AlertDialog(
      onDismissRequest = { disabledAddonPrompt = null },
      title = { Text("Add-on disabled") },
      text = { Text("${addon.manifest.name} is currently turned off. Enable it to watch this channel?") },
      confirmButton = {
        Button(onClick = { onEnableAddon(addon); disabledAddonPrompt = null }) { Text("Enable") }
      },
      dismissButton = { TextButton(onClick = { disabledAddonPrompt = null }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun BrowseSectionHeaderContent(
  title: String,
  countLabel: String,
  columns: Int,
  showColumnToggle: Boolean,
  onToggleColumns: () -> Unit,
  selectedFilter: MediaFilter,
  showFilters: Boolean,
  onFilterChange: (MediaFilter) -> Unit,
  query: String = "",
  showSearch: Boolean = false,
  onQueryChange: (String) -> Unit = {},
  showSort: Boolean = false,
  sortLabel: String = "",
  onToggleSort: () -> Unit = {},
) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AdaptivePageTitle(title = title, maxLines = 2)
        Text(countLabel, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
      }
      if (showSort) {
        TextButton(onClick = onToggleSort) {
          Text(sortLabel, fontWeight = FontWeight.Bold)
        }
      }
      if (showColumnToggle) {
        GlassCircleButton(onClick = onToggleColumns) {
          Icon(if (columns == 3) Icons.Rounded.ViewAgenda else Icons.Rounded.ViewModule, contentDescription = "Change grid size", tint = MaterialTheme.colorScheme.onBackground)
        }
      }
    }
    if (showSearch) {
      OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { InputGuideText("Search this list") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = if (query.isNotBlank()) ({ IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, "Clear") } }) else null,
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
      )
    }
    if (showFilters) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MediaFilter.values().forEach { option ->
          FilterChip(selected = selectedFilter == option, onClick = { onFilterChange(option) }, label = { Text(if (option == MediaFilter.All) "All" else option.name) })
        }
      }
    }
  }
}
@Composable
private fun NetworkHomeCard(item: MediaItem, sports: Boolean = false, modifier: Modifier = Modifier.width(176.dp), dimmed: Boolean = false, favourite: Boolean = false, onClick: () -> Unit) {
  Column(
    modifier = modifier.alpha(if (dimmed) 0.4f else 1f),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(104.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(if (sports) Color(0xFF171717) else Color.White)
        .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = if (sports) 0.dp else 22.dp, vertical = if (sports) 0.dp else 18.dp),
      contentAlignment = Alignment.Center,
    ) {
      AsyncImage(
        model = if (sports) item.backdrop ?: item.poster else item.poster,
        contentDescription = item.title,
        modifier = Modifier.fillMaxSize(),
        contentScale = if (sports) ContentScale.Crop else ContentScale.Fit,
      )
      if (favourite) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(7.dp)
            .size(23.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Rounded.Star,
            contentDescription = "Favourite channel",
            tint = Color(0xFFFACC15),
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }
    Text(
      item.title,
      color = MaterialTheme.colorScheme.onBackground,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun HomeStrip(rowId: String, title: String, items: List<MediaItem>, continueWatchingStyle: ContinueWatchingStyle, liveLandscapeCards: Boolean, watchlistItems: List<MediaItem>, favouriteItems: List<MediaItem> = emptyList(), addons: List<InstalledAddon> = emptyList(), onOpen: (MediaItem) -> Unit, onViewAll: () -> Unit, onToggleWatchlist: (MediaItem) -> Unit, onToggleFavourite: (MediaItem) -> Unit = {}, onEnableAddon: (InstalledAddon) -> Unit = {}, onMarkWatched: (MediaItem) -> Unit, onRestartFromBeginning: (MediaItem) -> Unit, onPlayContinueWatching: (MediaItem) -> Unit) {
  fun isFavourite(item: MediaItem): Boolean = favouriteItems.any { it.id == item.id && it.type == item.type }
  val isAddonRow = rowId.startsWith("addon:")
  val isFavouritesRow = rowId == "favourites"
  val isSportsRow = title.contains("sport", ignoreCase = true) || title.contains("live", ignoreCase = true) || items.any(MediaItem::isLiveCatalogItem)
  var actionItem by remember { mutableStateOf<MediaItem?>(null) }
  var disabledAddonPrompt by remember { mutableStateOf<InstalledAddon?>(null) }
  fun addonFor(item: MediaItem): InstalledAddon? = item.sourceAddonId?.let { id -> addons.firstOrNull { it.id == id } }
  fun handleOpen(item: MediaItem) {
    val addon = if (isFavouritesRow) addonFor(item) else null
    if (addon != null && !addon.enabled) {
      disabledAddonPrompt = addon
    } else {
      onOpen(item)
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = if (isAddonRow) 20.sp else 22.5.sp, fontWeight = if (isAddonRow) FontWeight.Bold else FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onViewAll) { Text("View All", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f), fontWeight = FontWeight.Bold) }
      }
      Box(
        modifier = Modifier
          .width(if (rowId == "continue") 102.dp else if (isAddonRow) 56.dp else 68.dp)
          .height(7.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (rowId == "continue") 0.96f else if (isAddonRow) 0.24f else 0.32f)),
      )
    }
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      items(items, key = { "${it.type}-${it.id}" }) { item ->
        val disabled = isFavouritesRow && addonFor(item)?.enabled == false
        if (rowId == "continue") {
          ContinueWatchingCard(item = item, style = continueWatchingStyle, onClick = { onPlayContinueWatching(item) }, onLongPress = { actionItem = item })
        } else if (rowId == "streaming_networks" || (isSportsRow && liveLandscapeCards)) {
          NetworkHomeCard(item = item, sports = isSportsRow, dimmed = disabled, favourite = isFavourite(item), onClick = { handleOpen(item) })
        } else {
          PosterCard(item = item, dimmed = disabled, onClick = { handleOpen(item) }, onLongPress = { actionItem = item })
        }
      }
    }
  }
  actionItem?.let { item ->
    if (isFavouritesRow) {
      FavouriteChannelActionsDialog(
        item = item,
        onRemoveFromFavourites = { onToggleFavourite(item) },
        onDismiss = { actionItem = null },
      )
    } else if (rowId == "continue") {
      ContinueWatchingActionsDialog(
        item = item,
        inWatchlist = watchlistItems.any { it.id == item.id && it.type == item.type },
        onRestartFromBeginning = { onRestartFromBeginning(item) },
        onOpenDetails = { onOpen(item) },
        onAddToWatchlist = { onToggleWatchlist(item) },
        onMarkWatched = { onMarkWatched(item) },
        onDismiss = { actionItem = null },
      )
    } else {
      MediaCardActionsDialog(
        item = item,
        inWatchlist = watchlistItems.any { it.id == item.id && it.type == item.type },
        includeRemoveAction = false,
        onAddToWatchlist = { onToggleWatchlist(item) },
        onRemoveFromWatchlist = { onToggleWatchlist(item) },
        onMarkWatched = { onMarkWatched(item) },
        onDismiss = { actionItem = null },
      )
    }
  }
  disabledAddonPrompt?.let { addon ->
    AlertDialog(
      onDismissRequest = { disabledAddonPrompt = null },
      title = { Text("Add-on disabled") },
      text = { Text("${addon.manifest.name} is currently turned off. Enable it to watch this channel?") },
      confirmButton = {
        Button(onClick = { onEnableAddon(addon); disabledAddonPrompt = null }) { Text("Enable") }
      },
      dismissButton = { TextButton(onClick = { disabledAddonPrompt = null }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun LibraryHeaderPanel(

  title: String,
  count: Int? = null,
  note: String? = null,
  selectedFilter: MediaFilter,
  onFilterChange: (MediaFilter) -> Unit,
  actionIcon: ImageVector,
  actionLabel: String,
  onAction: () -> Unit,
  searchQuery: String? = null,
  onSearchQueryChange: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  FrostedGlassSurface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(32.dp),
    blurRadius = 48f,
    contentPadding = PaddingValues(18.dp),
    tintAlpha = 0.17f,
    borderAlpha = 0.22f,
  ) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        count?.let { GlassPill("$it titles") }
        GlassCircleButton(modifier = Modifier.size(52.dp), onClick = onAction) {
          Icon(actionIcon, contentDescription = actionLabel, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
        }
      }
    }
    if (searchQuery != null && onSearchQueryChange != null) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { InputGuideText("Search movies & TV series...") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
          unfocusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
          cursorColor = MaterialTheme.colorScheme.onBackground,
          focusedTextColor = MaterialTheme.colorScheme.onBackground,
          unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
          focusedLeadingIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
          unfocusedLeadingIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
          focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
          unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
        ),
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      MediaFilter.values().forEach { filter ->
        FilterChip(
          selected = selectedFilter == filter,
          onClick = { onFilterChange(filter) },
          label = { Text(if (filter == MediaFilter.All) "All" else filter.name) },
        )
      }
    }
    note?.let { Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f), style = MaterialTheme.typography.bodyLarge) }
    }
  }
}

private fun List<MediaItem>.filteredBy(filter: MediaFilter): List<MediaItem> = when (filter) {
  MediaFilter.All -> this
  MediaFilter.Movies -> filter { it.type == "movie" }
  MediaFilter.Series -> filter { it.type == "tv" || it.type == "series" }
}

@Composable
private fun LibraryEmptyState(icon: @Composable () -> Unit, title: String, subtitle: String) {
  Box(modifier = Modifier.fillMaxWidth().height(520.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(horizontal = 28.dp)) {
      Box(
        modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.055f)).border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center,
      ) { icon() }
      Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
      Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
  }
}

@Composable
private fun MediaCardActionsDialog(
  item: MediaItem,
  inWatchlist: Boolean,
  includeRemoveAction: Boolean,
  onAddToWatchlist: () -> Unit,
  onRemoveFromWatchlist: () -> Unit,
  onMarkWatched: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    CardActionsAmbientScaffold(item = item, onDismiss = onDismiss) {
      if (!inWatchlist || !includeRemoveAction) {
        AmbientActionRow("Add to Watchlist", icon = Icons.Rounded.Bookmark) {
          if (!inWatchlist) onAddToWatchlist()
          onDismiss()
        }
      }
      if (includeRemoveAction && inWatchlist) {
        AmbientActionRow("Remove from Watchlist", icon = Icons.Rounded.Close) {
          onRemoveFromWatchlist()
          onDismiss()
        }
      }
      AmbientActionRow("Mark as Watched", icon = Icons.Rounded.CheckCircle) {
        onMarkWatched()
        onDismiss()
      }
      AmbientActionRow("Share", icon = Icons.Rounded.Share) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, item.title)
        }
        context.startActivity(Intent.createChooser(shareIntent, item.title))
        onDismiss()
      }
    }
  }
}

@Composable
private fun ContinueWatchingActionsDialog(
  item: MediaItem,
  inWatchlist: Boolean,
  onRestartFromBeginning: () -> Unit,
  onOpenDetails: () -> Unit,
  onAddToWatchlist: () -> Unit,
  onMarkWatched: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    CardActionsAmbientScaffold(item = item, onDismiss = onDismiss) {
      AmbientActionRow("Restart from Beginning", icon = Icons.Rounded.Replay) {
        onDismiss()
        onRestartFromBeginning()
      }
      AmbientActionRow("Details", icon = Icons.Rounded.Info) {
        onDismiss()
        onOpenDetails()
      }
      if (!inWatchlist) {
        AmbientActionRow("Add to Watchlist", icon = Icons.Rounded.Bookmark) {
          onAddToWatchlist()
          onDismiss()
        }
      }
      AmbientActionRow("Mark as Watched", icon = Icons.Rounded.CheckCircle) {
        onMarkWatched()
        onDismiss()
      }
      AmbientActionRow("Share", icon = Icons.Rounded.Share) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, item.title)
        }
        context.startActivity(Intent.createChooser(shareIntent, item.title))
        onDismiss()
      }
    }
  }
}
@Composable
private fun FavouriteChannelActionsDialog(item: MediaItem, onRemoveFromFavourites: () -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    CardActionsAmbientScaffold(item = item, onDismiss = onDismiss) {
      AmbientActionRow("Remove from Favourites", icon = Icons.Rounded.Close) {
        onRemoveFromFavourites()
        onDismiss()
      }
      AmbientActionRow("Share", icon = Icons.Rounded.Share) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, item.title)
        }
        context.startActivity(Intent.createChooser(shareIntent, item.title))
        onDismiss()
      }
    }
  }
}

// Long-press preview: an ambient, blurred-up version of the card's own art covers the whole
// window edge-to-edge — including behind the status bar, whose icons stay visible on top since
// the Dialog is edge-to-edge (decorFitsSystemWindows = false) rather than inset below it. The
// actions render directly on the page (no separate bottom sheet/card), anchored toward the
// bottom under the enlarged poster.
@Composable
private fun CardActionsAmbientScaffold(
  item: MediaItem,
  onDismiss: () -> Unit,
  actions: @Composable ColumnScope.() -> Unit,
) {
  val ambientArt = item.backdrop ?: item.poster
  Box(
    modifier = Modifier
      .fillMaxSize()
      .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
    contentAlignment = Alignment.BottomCenter,
  ) {
    if (ambientArt != null) {
      AsyncImage(
        model = ambientArt,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().blur(60.dp),
        contentScale = ContentScale.Crop,
      )
    }
    Box(
      modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(
          colorStops = arrayOf(
            0.00f to Color.Black.copy(alpha = 0.46f),
            0.40f to Color.Black.copy(alpha = 0.36f),
            0.72f to Color.Black.copy(alpha = 0.68f),
            1.00f to Color.Black.copy(alpha = 0.92f),
          ),
        ),
      ),
    )
    Column(
      modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 24.dp, bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.weight(1f))
      Surface(
        modifier = Modifier
          .width(196.dp)
          .aspectRatio(2f / 3f)
          .clickable(enabled = false, onClick = {}),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 24.dp,
        color = MaterialTheme.colorScheme.surface,
      ) {
        AsyncImage(
          model = item.poster ?: item.backdrop,
          contentDescription = item.title,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        item.title,
        color = Color.White,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 32.dp),
      )
      item.year?.let { year ->
        Spacer(modifier = Modifier.height(2.dp))
        Text(year, color = Color.White.copy(alpha = 0.66f), style = MaterialTheme.typography.bodyMedium)
      }
      Spacer(modifier = Modifier.height(22.dp))
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).clickable(enabled = false, onClick = {}),
        content = actions,
      )
    }
  }
}

@Composable
private fun AmbientActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 14.dp, horizontal = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f))
  }
}

@Composable
private fun MediaGrid(
  items: List<MediaItem>,
  onOpen: (MediaItem) -> Unit,
  columns: Int = 3,
  showMeta: Boolean = true,
  onToggleWatchlist: ((MediaItem) -> Unit)? = null,
  watchlistItems: List<MediaItem> = emptyList(),
  includeRemoveAction: Boolean = false,
  onMarkWatched: ((MediaItem) -> Unit)? = null,
  continueWatchingActions: Boolean = false,
  onRestartFromBeginning: ((MediaItem) -> Unit)? = null,
  onOpenDetails: ((MediaItem) -> Unit)? = null,
) {
  var actionItem by remember { mutableStateOf<MediaItem?>(null) }
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
    items.chunked(columns).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { item ->
          LibraryPosterTile(item = item, modifier = Modifier.weight(1f), showMeta = showMeta, onClick = { onOpen(item) }, onLongPress = { actionItem = item })
        }
        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
      }
    }
  }
  actionItem?.let { item ->
    if (continueWatchingActions && onRestartFromBeginning != null) {
      ContinueWatchingActionsDialog(
        item = item,
        inWatchlist = watchlistItems.any { it.id == item.id && it.type == item.type },
        onRestartFromBeginning = { onRestartFromBeginning(item) },
        onOpenDetails = { (onOpenDetails ?: onOpen)(item) },
        onAddToWatchlist = { onToggleWatchlist?.invoke(item) },
        onMarkWatched = { onMarkWatched?.invoke(item) },
        onDismiss = { actionItem = null },
      )
    } else {
      MediaCardActionsDialog(
        item = item,
        inWatchlist = watchlistItems.any { it.id == item.id && it.type == item.type },
        includeRemoveAction = includeRemoveAction,
        onAddToWatchlist = { onToggleWatchlist?.invoke(item) },
        onRemoveFromWatchlist = { onToggleWatchlist?.invoke(item) },
        onMarkWatched = { onMarkWatched?.invoke(item) },
        onDismiss = { actionItem = null },
      )
    }
  }
}

@Composable
private fun SearchGridSkeleton(columns: Int, rows: Int = 3) {
  // Mirrors the MediaGrid poster layout so the loading state matches the results.
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
    repeat(rows) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(columns) {
          SkeletonBlock(modifier = Modifier.weight(1f).aspectRatio(0.68f), radius = 16.dp)
        }
      }
    }
  }
}

@Composable
private fun LibraryPosterTile(item: MediaItem, modifier: Modifier = Modifier, showMeta: Boolean = true, onClick: () -> Unit, onLongPress: () -> Unit = {}) {
  Column(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f), RoundedCornerShape(16.dp)).pointerInput(item.id, item.type) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() }) },
  ) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)) {
      AsyncImage(model = item.poster ?: item.backdrop, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      CardImdbRatingBadge(rating = item.rating)
      Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.055f))) {
        Box(modifier = Modifier.fillMaxWidth(((item.progress ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)).height(4.dp).background(Color(0xFF22C55E)))
      }
    }
    if (showMeta) {
      Column(modifier = Modifier.fillMaxWidth().height(72.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(item.year ?: item.type.replaceFirstChar(Char::uppercase), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f), maxLines = 1, fontSize = 11.sp)
          item.progress?.takeIf { it > 0.0 }?.let { Text("${it.toInt()}%", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 10.sp) }
        }
      }
    }
  }
}

@Composable
private fun LibraryStreamDekHeader(
  title: String,
  subtitle: String,
  count: Int,
  selectedFilter: MediaFilter,
  onFilterChange: (MediaFilter) -> Unit,
  columns: Int,
  onToggleColumns: () -> Unit,
  style: HeaderStyle,
  hazeState: HazeState,
  modifier: Modifier = Modifier,
) {
  val content: @Composable BoxScope.() -> Unit = {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          AdaptivePageTitle(title = title, maxLines = 2)
          if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text("$count titles", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
          GlassCircleButton(onClick = onToggleColumns) {
            Icon(if (columns == 3) Icons.Rounded.ViewAgenda else Icons.Rounded.ViewModule, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MediaFilter.values().forEach { value ->
          FilterChip(selected = selectedFilter == value, onClick = { onFilterChange(value) }, label = { Text(if (value == MediaFilter.All) "All" else value.name) })
        }
      }
    }
  }
  if (style == HeaderStyle.Modern) {
    val lightHeader = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Box(modifier = modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
      FrostedGlassSurface(
        modifier = Modifier.fillMaxWidth().height(152.dp),
        shape = RoundedCornerShape(30.dp),
        hazeStateOverride = hazeState,
        blurRadius = 68f,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        tintAlpha = if (lightHeader) 0.14f else 0.06f,
        borderAlpha = if (lightHeader) 0.10f else 0f,
        baseAlpha = if (lightHeader) 0.28f else 0.08f,
        fillColorOverride = if (lightHeader) null else Color.White,
        showEdgeGradient = false,
        content = content,
      )
    }
  } else {
    Box(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), content = content)
  }
}

@Composable
private fun ContinueTab(uiState: AppUiState, onOpen: (MediaItem) -> Unit, onOpenDetails: (MediaItem) -> Unit, onToggleWatchlist: (MediaItem) -> Unit, onMarkWatched: (MediaItem) -> Unit, onRestartFromBeginning: (MediaItem) -> Unit) {
  var filter by rememberSaveable { mutableStateOf(MediaFilter.All) }
  var columns by rememberSaveable { mutableStateOf(3) }
  val items = remember(uiState.traktContinueWatching, uiState.localContinueWatching, filter) { combinedContinueWatching(uiState).filteredBy(filter) }
  val modernHeader = uiState.headerStyle == HeaderStyle.Modern
  val headerHazeState = rememberHazeState()
  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(if (modernHeader) Modifier.hazeSource(headerHazeState) else Modifier),
      contentPadding = PaddingValues(top = if (modernHeader) 222.dp else 0.dp, bottom = 126.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      if (!modernHeader) {
        stickyHeader {
          LibraryStreamDekHeader(
            title = "Continue",
            subtitle = "",
            count = items.size,
            selectedFilter = filter,
            onFilterChange = { filter = it },
            columns = columns,
            onToggleColumns = { columns = if (columns == 3) 2 else 3 },
            style = HeaderStyle.Classic,
            hazeState = headerHazeState,
          )
        }
      }
      if (items.isEmpty()) {
        item { LibraryEmptyState(icon = { Icon(Icons.Rounded.PlayCircleOutline, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f), modifier = Modifier.size(54.dp)) }, title = "Nothing here yet", subtitle = "Start watching something - your in-progress titles will appear here automatically.") }
      } else {
        item { MediaGrid(items, onOpen, columns = columns, onToggleWatchlist = onToggleWatchlist, watchlistItems = uiState.mergedWatchlist, onMarkWatched = onMarkWatched, continueWatchingActions = true, onRestartFromBeginning = onRestartFromBeginning, onOpenDetails = onOpenDetails) }
      }
    }
    if (modernHeader) {
      LibraryStreamDekHeader(
        title = "Continue",
        subtitle = "",
        count = items.size,
        selectedFilter = filter,
        onFilterChange = { filter = it },
        columns = columns,
        onToggleColumns = { columns = if (columns == 3) 2 else 3 },
        style = HeaderStyle.Modern,
        hazeState = headerHazeState,
        modifier = Modifier.align(Alignment.TopCenter).zIndex(4f),
      )
    }
  }
}

@Composable
private fun WatchlistTab(uiState: AppUiState, onOpen: (MediaItem) -> Unit, onToggleWatchlist: (MediaItem) -> Unit, onMarkWatched: (MediaItem) -> Unit) {
  var filter by rememberSaveable { mutableStateOf(MediaFilter.All) }
  var columns by rememberSaveable { mutableStateOf(3) }
  val items = remember(uiState.mergedWatchlist, filter) {
    uiState.mergedWatchlist
      .filteredBy(filter)
      .sortedWith(compareByDescending<MediaItem> { it.addedAt ?: Long.MIN_VALUE }.thenByDescending { it.updatedAt ?: Long.MIN_VALUE })
  }
  val modernHeader = uiState.headerStyle == HeaderStyle.Modern
  val headerHazeState = rememberHazeState()
  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(if (modernHeader) Modifier.hazeSource(headerHazeState) else Modifier),
      contentPadding = PaddingValues(top = if (modernHeader) 222.dp else 0.dp, bottom = 126.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      if (!modernHeader) {
        stickyHeader {
          LibraryStreamDekHeader(
            title = "Watchlist",
            subtitle = "",
            count = items.size,
            selectedFilter = filter,
            onFilterChange = { filter = it },
            columns = columns,
            onToggleColumns = { columns = if (columns == 3) 2 else 3 },
            style = HeaderStyle.Classic,
            hazeState = headerHazeState,
          )
        }
      }
      if (items.isEmpty()) {
        item { LibraryEmptyState(icon = { Icon(Icons.Rounded.Bookmark, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f), modifier = Modifier.size(54.dp)) }, title = "Your watchlist is empty", subtitle = "Add movies and TV series from the detail page to watch them later.") }
      } else {
        item { MediaGrid(items, onOpen, columns = columns, showMeta = false, onToggleWatchlist = onToggleWatchlist, watchlistItems = uiState.mergedWatchlist, includeRemoveAction = true, onMarkWatched = onMarkWatched) }
      }
    }
    if (modernHeader) {
      LibraryStreamDekHeader(
        title = "Watchlist",
        subtitle = "",
        count = items.size,
        selectedFilter = filter,
        onFilterChange = { filter = it },
        columns = columns,
        onToggleColumns = { columns = if (columns == 3) 2 else 3 },
        style = HeaderStyle.Modern,
        hazeState = headerHazeState,
        modifier = Modifier.align(Alignment.TopCenter).zIndex(4f),
      )
    }
  }
}

@Composable
private fun SearchTab(uiState: AppUiState, ownerKey: String, onSearch: (String) -> Unit, onOpen: (MediaItem) -> Unit, onToggleWatchlist: (MediaItem) -> Unit, onMarkWatched: (MediaItem) -> Unit) {

  val listState = rememberLazyListState()
  var query by rememberSaveable { mutableStateOf("") }
  var filter by rememberSaveable { mutableStateOf(MediaFilter.All) }
  var columns by rememberSaveable { mutableStateOf(3) }
  val searchContext = LocalContext.current
  val searchPrefs = remember(searchContext) { searchContext.getSharedPreferences("streamdek_native_search", Context.MODE_PRIVATE) }
  val recentSearchesKey = remember(ownerKey) { "recent_searches:$ownerKey" }
  var recentSearches by rememberSaveable(ownerKey) {
    mutableStateOf<List<String>>(runCatching {
      val stored = JSONArray(searchPrefs.getString(recentSearchesKey, "[]"))
      List(stored.length()) { index -> stored.optString(index) }.filter { it.isNotBlank() }.take(3)
    }.getOrDefault(emptyList()))
  }
  var discoverType by rememberSaveable { mutableStateOf("movie") }
  var selectedGenreId by rememberSaveable { mutableStateOf<Int?>(null) }
  var selectedYear by rememberSaveable { mutableStateOf<String?>(null) }
  var discoverGenres by remember { mutableStateOf<List<DiscoverGenre>>(emptyList()) }
  var discoverItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
  var discoverPage by remember { mutableStateOf(1) }
  var discoverTotalPages by remember { mutableStateOf(1) }
  var discoverLoading by remember { mutableStateOf(true) }
  var discoverLoadingMore by remember { mutableStateOf(false) }
  var discoverSheet by remember { mutableStateOf<String?>(null) }
  var discoverRequestedPage by remember { mutableStateOf<Int?>(null) }

  val apiClient = remember { StreamDekApiClient() }
  val scope = rememberCoroutineScope()
  val yearOptions = remember {
    val currentYear = java.time.LocalDate.now().year
    buildList {
      add(SearchYearOption("Any Year", null))
      for (year in currentYear downTo (currentYear - 19)) {
        add(SearchYearOption(year.toString(), year.toString()))
      }
      add(SearchYearOption("Before 2000", "before:1999"))
    }
  }

  fun discoverTypeLabel(value: String): String = when (value) {
    "tv" -> "Series"
    "documentary" -> "Documentaries"
    else -> "Movies"
  }

  fun selectedGenreLabel(): String = discoverGenres.firstOrNull { it.id == selectedGenreId }?.name ?: "All Genres"
  fun selectedYearLabel(): String = yearOptions.firstOrNull { it.value == selectedYear }?.label ?: "Any Year"

  fun loadDiscover(page: Int, append: Boolean) {
    if (discoverRequestedPage == page) return
    scope.launch {
      discoverRequestedPage = page
      if (page <= 1) discoverLoading = true else discoverLoadingMore = true
      apiClient.fetchDiscover(discoverType, page, selectedGenreId, selectedYear)
        .onSuccess { payload ->
          discoverPage = payload.page
          discoverTotalPages = payload.totalPages
          discoverItems = if (append) {
            (discoverItems + payload.items).distinctBy { "${it.type}-${it.id}" }
          } else {
            payload.items.distinctBy { "${it.type}-${it.id}" }
          }
        }
        .onFailure {
          if (!append) discoverItems = emptyList()
        }
      discoverLoading = false
      discoverLoadingMore = false
      discoverRequestedPage = null
    }
  }

  LaunchedEffect(query) {
    val normalized = query.trim()
    if (normalized.isBlank()) return@LaunchedEffect
    delay(500)
    if (normalized.length > 1) onSearch(normalized)
  }

  LaunchedEffect(query, uiState.searchLoading, uiState.searchResults) {
    val normalized = query.trim()
    if (normalized.length > 1 && uiState.searchResultQuery == normalized && !uiState.searchLoading && uiState.searchResults.isNotEmpty()) {
      recentSearches = listOf(normalized, *recentSearches.filterNot { it.equals(normalized, ignoreCase = true) }.toTypedArray()).take(3)
      searchPrefs.edit().putString(recentSearchesKey, JSONArray(recentSearches).toString()).apply()
    }
  }

  LaunchedEffect(discoverType) {
    selectedGenreId = null
    if (discoverType == "documentary") {
      discoverGenres = emptyList()
    } else {
      apiClient.fetchDiscoverGenres(discoverType)
        .onSuccess { discoverGenres = it }
        .onFailure { discoverGenres = emptyList() }
    }
  }

  LaunchedEffect(discoverType, selectedGenreId, selectedYear) {
    discoverPage = 1
    discoverTotalPages = 1
    discoverRequestedPage = null
    loadDiscover(page = 1, append = false)
  }

  LaunchedEffect(listState, query, discoverPage, discoverTotalPages, discoverLoading, discoverLoadingMore, discoverItems.size, discoverRequestedPage) {
    snapshotFlow { listState.canScrollForward }
      .collect { canScrollForward ->
        if (canScrollForward) return@collect
        if (query.isNotBlank() || discoverLoading || discoverLoadingMore || discoverPage >= discoverTotalPages || discoverItems.isEmpty()) return@collect
        val nextPage = discoverPage + 1
        if (discoverRequestedPage == nextPage) return@collect
        loadDiscover(nextPage, append = true)
      }
  }

  val searchResults = remember(uiState.searchResults, filter) {
    uiState.searchResults.distinctBy { "${it.type}-${it.id}" }.filteredBy(filter)
  }

  val discoverOptions = remember(discoverSheet, discoverType, discoverGenres, selectedGenreId, selectedYear) {
    when (discoverSheet) {
      "type" -> listOf("movie", "tv", "documentary").map { value ->
        SearchSelectionOption(
          label = discoverTypeLabel(value),
          selected = discoverType == value,
          onSelect = {
            discoverType = value
            discoverSheet = null
          },
        )
      }
      "genre" -> buildList {
        add(
          SearchSelectionOption(
            label = "All Genres",
            selected = selectedGenreId == null,
            onSelect = {
              selectedGenreId = null
              discoverSheet = null
            },
          )
        )
        discoverGenres.forEach { genre ->
          add(
            SearchSelectionOption(
              label = genre.name,
              selected = genre.id == selectedGenreId,
              onSelect = {
                selectedGenreId = genre.id
                discoverSheet = null
              },
            )
          )
        }
      }
      "year" -> yearOptions.map { option ->
        SearchSelectionOption(
          label = option.label,
          selected = option.value == selectedYear,
          onSelect = {
            selectedYear = option.value
            discoverSheet = null
          },
        )
      }
      else -> emptyList()
    }
  }

  val modernHeader = uiState.headerStyle == HeaderStyle.Modern
  val headerHazeState = rememberHazeState()
  Box(modifier = Modifier.fillMaxSize()) {
  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(if (modernHeader) Modifier.hazeSource(headerHazeState) else Modifier),
    contentPadding = PaddingValues(top = if (modernHeader) 236.dp else 0.dp, bottom = 126.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    if (!modernHeader) {
      stickyHeader {
        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
          SearchHeader(
            query = query,
            columns = columns,
            showResultsState = query.isNotBlank() && searchResults.isNotEmpty(),
            onQueryChange = { query = it },
            onClear = { query = "" },
            onToggleColumns = { columns = if (columns == 3) 2 else 3 },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    if (query.isBlank()) {
      if (recentSearches.isNotEmpty()) {
        item {
          SearchRecentSection(
            recentSearches = recentSearches,
            onSearchPress = { query = it },
            onRemoveSearch = { recent ->
              recentSearches = recentSearches.filterNot { it == recent }
              searchPrefs.edit().putString(recentSearchesKey, JSONArray(recentSearches).toString()).apply()
            },
          )
        }
      }
      item {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          SearchSectionLabel(
            title = "Discover",
            subtitle = "",
            horizontalPadding = 0.dp,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            SearchDiscoverField(
              label = "Type",
              value = discoverTypeLabel(discoverType),
              modifier = Modifier.weight(1f),
              onClick = { discoverSheet = "type" },
            )
            SearchDiscoverField(
              label = "Genre",
              value = selectedGenreLabel(),
              modifier = Modifier.weight(1f),
              enabled = discoverType != "documentary" && discoverGenres.isNotEmpty(),
              onClick = { if (discoverType != "documentary" && discoverGenres.isNotEmpty()) discoverSheet = "genre" },
            )
            SearchDiscoverField(
              label = "Year",
              value = selectedYearLabel(),
              modifier = Modifier.weight(1f),
              onClick = { discoverSheet = "year" },
            )
          }
        }
      }
      when {
        discoverLoading && discoverItems.isEmpty() -> {
          item { SearchGridSkeleton(columns = columns) }
        }
        discoverItems.isEmpty() -> {
          item {
            LibraryEmptyState(
              icon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f), modifier = Modifier.size(54.dp)) },
              title = "Nothing matched these filters",
              subtitle = "Try a different type, genre, or year.",
            )
          }
        }
        else -> {
          item { MediaGrid(discoverItems, onOpen, columns = columns, showMeta = false, onToggleWatchlist = onToggleWatchlist, watchlistItems = uiState.mergedWatchlist, onMarkWatched = onMarkWatched) }
          if (discoverPage < discoverTotalPages) {
            item {
              Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                if (discoverLoadingMore) {
                  CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
              }
            }
          }
        }
      }
    } else {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MediaFilter.values().forEach { value ->
              FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(if (value == MediaFilter.All) "All" else value.name) })
            }
          }
          Text(
            text = "Results",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }
      }
      when {
        uiState.searchLoading && searchResults.isEmpty() -> {
          item { SearchGridSkeleton(columns = columns) }
        }
        searchResults.isEmpty() -> {
          item {
            LibraryEmptyState(
              icon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f), modifier = Modifier.size(54.dp)) },
              title = "No results",
              subtitle = "Try another title or clear the search to return to Discover.",
            )
          }
        }
        else -> {
          item { MediaGrid(searchResults.take(60), onOpen, columns = columns, showMeta = false, onToggleWatchlist = onToggleWatchlist, watchlistItems = uiState.mergedWatchlist, onMarkWatched = onMarkWatched) }
        }
      }
    }
  }
  if (modernHeader) {
    val lightHeader = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Box(modifier = Modifier.align(Alignment.TopCenter).zIndex(4f).fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
      FrostedGlassSurface(
        modifier = Modifier.fillMaxWidth().height(166.dp),
        shape = RoundedCornerShape(30.dp),
        hazeStateOverride = headerHazeState,
        blurRadius = 68f,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        tintAlpha = if (lightHeader) 0.14f else 0.06f,
        borderAlpha = if (lightHeader) 0.10f else 0f,
        baseAlpha = if (lightHeader) 0.28f else 0.08f,
        fillColorOverride = if (lightHeader) null else Color.White,
        showEdgeGradient = false,
      ) {
        SearchHeader(
          query = query,
          columns = columns,
          showResultsState = query.isNotBlank() && searchResults.isNotEmpty(),
          onQueryChange = { query = it },
          onClear = { query = "" },
          onToggleColumns = { columns = if (columns == 3) 2 else 3 },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
  }

  if (discoverSheet != null && discoverOptions.isNotEmpty()) {
    SearchSelectionDialog(
      title = when (discoverSheet) {
        "type" -> "Choose Type"
        "genre" -> "Choose Genre"
        else -> "Choose Year"
      },
      options = discoverOptions,
      onDismiss = { discoverSheet = null },
    )
  }
}

@Composable
private fun SearchDiscoverField(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  Row(
    modifier = modifier
      .heightIn(min = 28.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(enabled = enabled, onClick = onClick)
      .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      value,
      color = if (label == "Type") MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
      fontSize = 15.sp,
      fontWeight = if (label == "Type") FontWeight.SemiBold else FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Icon(
      Icons.Rounded.KeyboardArrowDown,
      contentDescription = null,
      tint = if (enabled) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f),
      modifier = Modifier.size(16.dp),
    )
  }
}

@Composable
private fun SearchSelectionDialog(
  title: String,
  options: List<SearchSelectionOption>,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.58f))
        .clickable(onClick = onDismiss),
      contentAlignment = Alignment.BottomCenter,
    ) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 18.dp)
          .clickable(enabled = false, onClick = {}),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(options.size) { index ->
              val option = options[index]
              OutlinedButton(
                onClick = option.onSelect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = if (option.selected) 0.26f else 0.10f)),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = if (option.selected) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f),
                  contentColor = MaterialTheme.colorScheme.onBackground,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
              ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                  Text(option.label, fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Medium)
                  if (option.selected) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SearchHeader(
  query: String,
  columns: Int,
  showResultsState: Boolean,
  onQueryChange: (String) -> Unit,
  onClear: () -> Unit,
  onToggleColumns: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AdaptivePageTitle(title = "Search", maxLines = 1)
      }
      GlassCircleButton(onClick = onToggleColumns) {
        Icon(if (columns == 3) Icons.Rounded.ViewAgenda else Icons.Rounded.ViewModule, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
      }
    }
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      placeholder = { InputGuideText("Search movies, TV and catalogs") },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      trailingIcon = if (query.isNotBlank()) ({ IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear") } }) else null,
      shape = RoundedCornerShape(20.dp),
      colors = androidx.compose.material3.TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
        unfocusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.onBackground,
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedLeadingIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
      ),
    )
    if (showResultsState) {
      Text(if (columns == 3) "Three-column layout" else "Two-column layout", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun SearchRecentSection(recentSearches: List<String>, onSearchPress: (String) -> Unit, onRemoveSearch: (String) -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SearchSectionLabel(
      title = "Recent Searches",
      subtitle = "Jump back into titles you looked up earlier.",
      horizontalPadding = 0.dp,
    )
    recentSearches.forEach { recentQuery ->
      SearchRecentRow(query = recentQuery, onSearchPress = { onSearchPress(recentQuery) }, onRemovePress = { onRemoveSearch(recentQuery) })
    }
  }
}

@Composable
private fun SearchSectionLabel(title: String, subtitle: String, horizontalPadding: Dp = 16.dp) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = horizontalPadding),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(title, fontSize = if (title == "Discover") 24.3.sp else 18.sp, lineHeight = if (title == "Discover") 29.7.sp else 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    if (subtitle.isNotBlank()) {
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f))
    }
  }
}

@Composable
private fun SearchRecentRow(query: String, onSearchPress: () -> Unit, onRemovePress: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.035f))
      .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
      .clickable(onClick = onSearchPress)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f), RoundedCornerShape(999.dp)).padding(9.dp), contentAlignment = Alignment.Center) {
      Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f), modifier = Modifier.size(18.dp))
    }
    Text(query, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    IconButton(onClick = onRemovePress) {
      Icon(Icons.Rounded.Close, contentDescription = "Remove recent search", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f))
    }
  }
}

@Composable
private fun LibraryTabScreen(viewModel: NativeAppViewModel) {
  val uiState = viewModel.uiState
  var selected by rememberSaveable { mutableStateOf(LibraryTab.Continue) }
  var addonUrl by rememberSaveable { mutableStateOf("") }
  var profileName by rememberSaveable { mutableStateOf("") }
  var selectedProvider by rememberSaveable { mutableStateOf("real-debrid") }
  var providerApiKey by rememberSaveable { mutableStateOf("") }
  var pinPrompt by rememberSaveable(uiState.pinPromptProfileId) { mutableStateOf("") }
  var editingPinProfileId by rememberSaveable { mutableStateOf<String?>(null) }
  var newPin by rememberSaveable(editingPinProfileId) { mutableStateOf("") }
  var confirmPin by rememberSaveable(editingPinProfileId) { mutableStateOf("") }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LibraryTab.values().toList(), key = { it.name }) { tab ->
          FilterChip(selected = selected == tab, onClick = { selected = tab }, label = { Text(tab.name) })
        }
      }
    }

    when (selected) {
      LibraryTab.Continue -> {
        items(combinedContinueWatching(uiState), key = { "continue-${it.type}-${it.id}" }) { item ->
          LibraryMediaCard(item = item)
        }
      }
      LibraryTab.Watchlist -> {
        items(uiState.mergedWatchlist, key = { "watchlist-${it.type}-${it.id}" }) { item ->
          SearchResultRow(item = item, onClick = { viewModel.toggleWatchlist(item) })
        }
      }
      LibraryTab.Profiles -> {
        item {
          GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              OutlinedTextField(value = profileName, onValueChange = { profileName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New profile name") }, singleLine = true)
              Button(onClick = {
                if (profileName.isNotBlank()) {
                  viewModel.createProfile(profileName.trim())
                  profileName = ""
                }
              }, modifier = Modifier.fillMaxWidth()) { Text("Create profile") }
            }
          }
        }
        if (uiState.pinPromptProfileId != null) {
          item {
            GlassCard {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PIN required", fontWeight = FontWeight.Bold)
                Text("Enter the 4-digit PIN for ${uiState.pinPromptProfileName ?: "this profile"}.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                OutlinedTextField(
                  value = pinPrompt,
                  onValueChange = { pinPrompt = it.filter(Char::isDigit).take(4) },
                  modifier = Modifier.fillMaxWidth(),
                  label = { Text("PIN") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                  visualTransformation = PasswordVisualTransformation(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = {
                      viewModel.submitProfilePin(pinPrompt)
                      pinPrompt = ""
                    },
                    enabled = pinPrompt.length == 4,
                  ) { Text("Unlock") }
                  TextButton(
                    onClick = {
                      pinPrompt = ""
                      viewModel.cancelProfilePinPrompt()
                    },
                  ) { Text("Cancel") }
                }
              }
            }
          }
        }
        items(uiState.profiles, key = { "profile-${it.id}" }) { profile ->
          GlassCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.name, fontWeight = FontWeight.Bold)
                Text(buildString {
                  append(if (profile.id == uiState.activeProfileId) "Active" else "Tap to switch")
                  if (profile.isDefault) append(" \u2022 Default")
                  if (profile.hasPinSet) append(" \u2022 PIN set")
                }, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { viewModel.selectProfile(profile.id) }) { Text("Use") }
                TextButton(onClick = { viewModel.makeDefaultProfile(profile.id) }) { Text("Default") }
                if (!profile.isDefault) {
                  TextButton(onClick = { viewModel.deleteProfile(profile.id) }) { Text("Delete") }
                }
              }
            }
            Spacer(modifier = Modifier.height(5.dp))
          if (editingPinProfileId == profile.id) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = newPin,
                  onValueChange = { newPin = it.filter(Char::isDigit).take(4) },
                  modifier = Modifier.fillMaxWidth(),
                  label = { Text(if (profile.hasPinSet) "New PIN" else "PIN") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                  visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                  value = confirmPin,
                  onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) },
                  modifier = Modifier.fillMaxWidth(),
                  label = { Text("Confirm PIN") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                  visualTransformation = PasswordVisualTransformation(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = {
                      viewModel.updateProfilePin(profile.id, newPin)
                      editingPinProfileId = null
                      newPin = ""
                      confirmPin = ""
                    },
                    enabled = newPin.length == 4 && confirmPin == newPin,
                  ) { Text(if (profile.hasPinSet) "Change PIN" else "Set PIN") }
                  if (profile.hasPinSet) {
                    TextButton(
                      onClick = {
                        viewModel.updateProfilePin(profile.id, null)
                        editingPinProfileId = null
                        newPin = ""
                        confirmPin = ""
                      },
                    ) { Text("Remove PIN") }
                  }
                  TextButton(
                    onClick = {
                      editingPinProfileId = null
                      newPin = ""
                      confirmPin = ""
                    },
                  ) { Text("Cancel") }
                }
              }
            } else {
              TextButton(onClick = { editingPinProfileId = profile.id }) {
                Text(if (profile.hasPinSet) "Manage PIN" else "Set PIN")
              }
            }
          }
        }
      }
      LibraryTab.Addons -> {
        item {
          GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              OutlinedTextField(value = addonUrl, onValueChange = { addonUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Add-on link") }, singleLine = true)
              Button(onClick = {
                if (addonUrl.isNotBlank()) {
                  viewModel.installAddon(addonUrl.trim())
                  addonUrl = ""
                }
              }, modifier = Modifier.fillMaxWidth()) { Text("Install add-on") }
            }
          }
        }
        items(uiState.addons, key = { "addon-${it.id}" }) { addon ->
          GlassCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(addon.manifest.name, fontWeight = FontWeight.Bold)
                Text("${addon.manifest.version} - ${addon.id}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                addon.manifest.description?.takeIf { it.isNotBlank() }?.let {
                  Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
              }
              Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  TextButton(onClick = { viewModel.moveAddon(addon.id, -1) }) { Text("Up") }
                  TextButton(onClick = { viewModel.moveAddon(addon.id, 1) }) { Text("Down") }
                }
                Switch(checked = addon.enabled, onCheckedChange = { viewModel.toggleAddon(addon, it) })
                TextButton(onClick = { viewModel.uninstallAddon(addon.id) }) { Text("Remove") }
              }
            }
          }
        }
      }
      LibraryTab.Debrid -> {
        item {
          GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("real-debrid", "alldebrid", "premiumize", "torbox", "debrid-link").forEach { provider ->
                  FilterChip(selected = selectedProvider == provider, onClick = { selectedProvider = provider }, label = { Text(provider) })
                }
              }
              OutlinedTextField(value = providerApiKey, onValueChange = { providerApiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Service access key") }, singleLine = true)
              Button(onClick = {
                if (providerApiKey.isNotBlank()) {
                  viewModel.addDebridAccount(selectedProvider, providerApiKey.trim())
                  providerApiKey = ""
                }
              }, modifier = Modifier.fillMaxWidth()) { Text("Connect service") }
            }
          }
        }
        items(uiState.debridAccounts, key = { "debrid-${it.provider}" }) { account ->
          GlassCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account.provider, fontWeight = FontWeight.Bold)
                Text(account.username ?: "Configured", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
              }
              Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  TextButton(onClick = { viewModel.moveDebridAccount(account.provider, -1) }) { Text("Up") }
                  TextButton(onClick = { viewModel.moveDebridAccount(account.provider, 1) }) { Text("Down") }
                }
                TextButton(onClick = { viewModel.removeDebridAccount(account.provider) }) { Text("Remove") }
              }
            }
          }
        }
      }
      LibraryTab.Trakt -> {
        item {
          GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(if (uiState.traktStatus.connected) "Connected as ${uiState.traktStatus.username ?: "unknown"}" else "Trakt is not connected", fontWeight = FontWeight.Bold)
              uiState.pendingDeviceCode?.let { code ->
                Text("Go to ${code.verificationUrl} and enter ${code.userCode}", color = MaterialTheme.colorScheme.secondary)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::requestTraktDeviceCode) { Text("Start device auth") }
                if (uiState.pendingDeviceCode != null) {
                  Button(onClick = viewModel::pollTraktAuthorization) { Text("Check status") }
                }
                if (uiState.traktStatus.connected) {
                  TextButton(onClick = viewModel::disconnectTrakt) { Text("Disconnect") }
                }
              }
            }
          }
        }
        item {
          GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Recommendations", fontWeight = FontWeight.Bold)
              uiState.traktRecommendations.take(5).forEach { item ->
        Text("${item.title}${item.year?.let { " ($it)" } ?: ""}")
              }
            }
          }
        }
        item {
          GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Trending", fontWeight = FontWeight.Bold)
              uiState.traktTrending.take(5).forEach { item ->
        Text("${item.title}${item.year?.let { " ($it)" } ?: ""}")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(24.dp),
    ) {
      Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp), content = content)
    }
  }
}

@Composable
private fun SettingsRow(icon: String, iconColor: Color, title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
  Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(iconColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
      Text(icon, color = iconColor, fontWeight = FontWeight.Black)
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
    }
    if (trailing != null) trailing() else Text(">", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f), style = MaterialTheme.typography.headlineSmall)
  }
}

@Composable
private fun SettingsDivider() {
  Box(modifier = Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)))
}
@Composable
private fun AmbientTintSlider(percent: Int, enabled: Boolean, onPercentChange: (Int) -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Ambient Tint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Text("Lower values make the page tint more transparent so the ambient glow shows through on Home, media details, and episode pages.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.68f else 0.42f))
      }
      Text(
        "$percent%",
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
      )
    }
    androidx.compose.material3.Slider(
      value = percent.toFloat(),
      onValueChange = { onPercentChange((it + 0.5f).toInt().coerceIn(20, 100)) },
      valueRange = 20f..100f,
      steps = 15,
      enabled = enabled,
      colors = androidx.compose.material3.SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
        disabledActiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
        disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
      ),
    )
  }
}

@Composable
private fun NavigationAutoCollapseDelaySlider(seconds: Int, enabled: Boolean, onSecondsChange: (Int) -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Auto-collapse delay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Text("How long the full navigation stays open after its last use.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.68f else 0.42f))
      }
      Text(
        "$seconds sec",
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
      )
    }
    androidx.compose.material3.Slider(
      value = seconds.toFloat(),
      onValueChange = { onSecondsChange((it + 0.5f).toInt()) },
      valueRange = 2f..15f,
      steps = 12,
      enabled = enabled,
      colors = androidx.compose.material3.SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
        disabledActiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
        disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
      ),
    )
  }
}

@Composable
private fun RoundedInput(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: @Composable (() -> Unit)? = null,
  placeholder: @Composable (() -> Unit)? = null,
  singleLine: Boolean = true,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    label = label,
    placeholder = placeholder,
    singleLine = singleLine,
    shape = RoundedCornerShape(18.dp),
  )
}

@Composable
private fun InputGuideText(text: String) {
  Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f))
}

@Composable
private fun FusionBadgeUrlsDialog(
  uiState: AppUiState,
  onDismiss: () -> Unit,
  onImport: (String) -> Unit,
  onSetActive: (String) -> Unit,
  onRefresh: (String) -> Unit,
  onRemove: (String) -> Unit,
  onPreview: (FusionBadgeSource) -> Unit,
) {
  var newUrl by rememberSaveable { mutableStateOf("") }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = 720.dp),
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    ) {
      Column(modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Badge Collections", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
          Text("Add up to $MAX_FUSION_BADGE_URLS badge collections using links supplied by a trusted source.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f), style = MaterialTheme.typography.bodyMedium)
        }
        RoundedInput(
          value = newUrl,
          onValueChange = { newUrl = it },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { InputGuideText("Paste a badge collection link") },
        )
        Button(
          onClick = { onImport(newUrl); newUrl = "" },
          enabled = newUrl.isNotBlank(),
          modifier = Modifier.fillMaxWidth().height(56.dp),
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
        ) { Text("Add Collection", fontWeight = FontWeight.Black) }

        uiState.fusionBadgeUrls.forEach { url ->
          val state = uiState.fusionBadgeSources[url]
          val source = state?.source
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
                ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(url, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
              val enabled = source?.filters?.count { it.isEnabled } ?: 0
              val groups = source?.filters?.map { it.groupId }?.distinct()?.size ?: 0
              Text(
                when {
                  state?.loading == true -> "Loading..."
                  state?.error != null -> state.error
                  source != null -> "Ready - $enabled badges in $groups categories"
                  else -> "Not loaded yet"
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state?.error == null) 0.68f else 0.88f),
                style = MaterialTheme.typography.bodyMedium,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onSetActive(url) }, shape = RoundedCornerShape(14.dp)) {
                  Text(if (uiState.activeFusionBadgeUrl == url || (uiState.activeFusionBadgeUrl == null && uiState.fusionBadgeUrls.firstOrNull() == url)) "In use" else "Use this")
                }
                IconButton(onClick = { source?.let(onPreview) }, enabled = source != null) { Icon(Icons.Rounded.Visibility, contentDescription = "Preview", tint = MaterialTheme.colorScheme.onSurface) }
                IconButton(onClick = { onRefresh(url) }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface) }
                IconButton(onClick = { onRemove(url) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFFF4D5E)) }
              }
            }
          }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
      }
    }
  }
}

@Composable
private fun FusionBadgePreviewDialog(source: FusionBadgeSource, onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 760.dp),
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
    ) {
      Column(modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Badge Preview", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(source.url, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${source.filters.count { it.isEnabled }} badges", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
        source.filters.groupBy { it.groupId }.forEach { (groupId, badges) ->
          val groupName = source.groups.firstOrNull { it.id == groupId }?.name ?: groupId.ifBlank { "Special" }
          Text(groupName.uppercase(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
          androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            badges.take(24).forEach { badge ->
              Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.width(92.dp)) {
                AsyncImage(model = badge.imageUrl, contentDescription = badge.name, modifier = Modifier.height(30.dp).fillMaxWidth(), contentScale = ContentScale.Fit)
                Text(badge.name, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
              }
            }
          }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(
  uiState: AppUiState,
  route: SettingsRoute?,
  apiBaseUrl: String,
  onRouteChange: (SettingsRoute) -> Unit,
  onSubmitProfilePin: (String) -> Unit,
  onCancelProfilePin: () -> Unit,
  onBack: () -> Unit,
  onSwitchProfile: () -> Unit,
  onSelectProfile: (String) -> Unit,
  onCreateProfile: (String, Int) -> Unit,
  onUpdateProfile: (String, String, Int) -> Unit,
  onDeleteProfile: (String) -> Unit,
  onMakeDefaultProfile: (String) -> Unit,
  onUpdateProfilePin: (String, String?) -> Unit,
  onSignOut: () -> Unit,
  onSignIn: () -> Unit,
  onAppAppearanceChange: (AppAppearance) -> Unit,
  onAppLanguageChange: (String) -> Unit,
  onThemePresetChange: (AppThemePreset) -> Unit,
  onHeaderStyleChange: (HeaderStyle) -> Unit,
  onPictureInPictureEnabledChange: (Boolean) -> Unit,
  onDecoderModeChange: (String) -> Unit,
  onRenderSurfaceChange: (String) -> Unit,
  onPlayerEngineChange: (String) -> Unit,
  onPreferredAudioLanguageChange: (String) -> Unit,
  onDetailPageStyleChange: (DetailPageStyle) -> Unit,
  onSeasonTabStyleChange: (SeasonTabStyle) -> Unit,
  onShowNavLabelsChange: (Boolean) -> Unit,
  onCollapsibleNavigationEnabledChange: (Boolean) -> Unit,
  onNavigationAutoCollapseSecondsChange: (Int) -> Unit,
  onSyncOnCellularChange: (Boolean) -> Unit,
  onSkipIntroEnabledChange: (Boolean) -> Unit,
  onSkipRecapEnabledChange: (Boolean) -> Unit,
  onSkipEndingEnabledChange: (Boolean) -> Unit,
  onAutoPlayNextEpisodeChange: (Boolean) -> Unit,
  onPreferBingeGroupChange: (Boolean) -> Unit,
  onNextEpisodeThresholdModeChange: (String) -> Unit,
  onNextEpisodeThresholdPercentChange: (Int) -> Unit,
  onNextEpisodeThresholdMinutesChange: (Int) -> Unit,
  onAutoLoadSubtitlesChange: (Boolean) -> Unit,
  onShowStreamsListChange: (Boolean) -> Unit,
  onHeroTrailerAutoplayChange: (Boolean) -> Unit,
  onHeroTrailerResolutionChange: (Int) -> Unit,
  onShowHeroSynopsisChange: (Boolean) -> Unit,
  onContinueWatchingStyleChange: (ContinueWatchingStyle) -> Unit,
  onLiveLandscapeCardsChange: (Boolean) -> Unit,
  onLiveFavouriteDrawerCardsChange: (Boolean) -> Unit,
  onRememberLastSourceChange: (Boolean) -> Unit,
  onBlurUnwatchedEpisodesChange: (Boolean) -> Unit,
  onRatingsEnabledChange: (Boolean) -> Unit,
  onExternalRatingsEnabledChange: (Boolean) -> Unit,
  onRatingProviderEnabledChange: (String, Boolean) -> Unit,
  onMdblistApiKeyChange: (String) -> Unit,
  onVividAmbientChange: (Boolean) -> Unit,
  onAmbientTintPercentChange: (Int) -> Unit,
  onFusionBadgesChange: (Boolean) -> Unit,
  onShowSizeBadgesChange: (Boolean) -> Unit,
  onShowAddonTmdbRatingsChange: (Boolean) -> Unit,
  onPreferredQualityChange: (String) -> Unit,
  onMaxFileSizeChange: (Int) -> Unit,
  onBadgePositionChange: (String) -> Unit,
  onUpdateTorrentServerSettings: ((TorrentServerSettings) -> TorrentServerSettings) -> Unit,
  onAddFusionBadgeUrl: (String) -> Unit,
  onRemoveFusionBadgeUrl: (String) -> Unit,
  onRefreshFusionBadgeUrl: (String) -> Unit,
  onSetActiveFusionBadgeUrl: (String) -> Unit,
  onDefaultAppCatalogsEnabledChange: (Boolean) -> Unit,
  onHomeCatalogRowEnabledChange: (String, Boolean) -> Unit,
  onMoveHomeCatalogRow: (String, Int) -> Unit,
  onRefreshHome: () -> Unit,
  onRefreshAddons: () -> Unit,
  onInstallAddon: (String) -> Unit,
  onToggleAddon: (InstalledAddon, Boolean) -> Unit,
  onUninstallAddon: (String) -> Unit,
  onMoveAddon: (String, Int) -> Unit,
  onAddM3uPlaylist: (String, String) -> Unit,
  onRemoveM3uPlaylist: (String) -> Unit,
  onSetM3uPlaylistEnabled: (String, Boolean) -> Unit,
  onMoveM3uPlaylist: (String, Int) -> Unit,
  onRefreshM3uPlaylists: () -> Unit,
  onDownloadsEnabledChange: (Boolean) -> Unit,
  onRefreshDownloads: () -> Unit,
  onRemoveDownload: (String) -> Unit,
  onPlayDownload: (DownloadEntry) -> Unit,
  onRefreshDebrid: () -> Unit,
  onAddDebrid: (String, String) -> Unit,
  onRemoveDebrid: (String) -> Unit,
  onMoveDebrid: (String, Int) -> Unit,
  onRequestTraktDeviceCode: () -> Unit,
  onPollTraktAuthorization: () -> Unit,
  onDisconnectTrakt: () -> Unit,
  onRefreshTrakt: () -> Unit,
  onRefreshSync: () -> Unit,
  onAutoUpdateChecksChange: (Boolean) -> Unit,
  onCheckForUpdates: () -> Unit,
  onStartUpdate: () -> Unit,
) {
  var settingsRefreshing by remember { mutableStateOf(false) }
  var settingsSearchVisible by rememberSaveable { mutableStateOf(false) }
  var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
  var settingsSearchRevealedAtMs by remember { mutableStateOf(0L) }
  var settingsPullDistance by remember { mutableFloatStateOf(0f) }
  val settingsListStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
  // Consumes the first ~28dp of a downward pull on the settings home list to reveal
  // search, via onPreScroll - which runs before PullToRefreshBox's own nested-scroll
  // handling sees anything - so that small "reveal search" swipe never reaches
  // PullToRefreshBox's drag detector and never shows/starts its refresh indicator. Once
  // that budget is used (search already visible), scroll passes through untouched, so a
  // deliberate, larger continued pull still refreshes normally.
  val settingsSearchPullConnection = remember(route) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (route == null && !settingsSearchVisible && available.y > 0f) {
          val remainingToReveal = (28f - settingsPullDistance).coerceAtLeast(0f)
          val consumedY = available.y.coerceAtMost(remainingToReveal)
          settingsPullDistance += consumedY
          if (settingsPullDistance >= 28f) {
            settingsSearchVisible = true
            settingsSearchRevealedAtMs = android.os.SystemClock.elapsedRealtime()
          }
          return Offset(0f, consumedY)
        }
        if (available.y <= 0f) settingsPullDistance = 0f
        return Offset.Zero
      }
    }
  }
  val settingsRefreshScope = rememberCoroutineScope()
  val settingsSearchEntries = remember {
    listOf(
      SettingsRoute.Account to "account sign in sign out email sync services",
      SettingsRoute.Profiles to "profile switch kids pin default avatar family",
      SettingsRoute.GeneralPlayback to "language theme colour color dark light header navigation cellular",
      SettingsRoute.HomeAppearance to "home appearance rows catalog layout cards live channel",
      SettingsRoute.DetailScreen to "detail hero trailer autoplay synopsis ratings badges ambient",
      SettingsRoute.Streams to "streams source quality size player audio pip torrent peer magnet cache storage download",
      SettingsRoute.PlaybackAutomation to "autoplay skip intro recap ending next episode binge",
      SettingsRoute.Subtitles to "subtitle subtitles caption captions language source",
      SettingsRoute.Addons to "addon add-on catalog channel provider install configure source",
      SettingsRoute.Plugins to "plugin scraper provider repository javascript",
      SettingsRoute.Debrid to "premium debrid real debrid alldebrid torbox account",
      SettingsRoute.Trakt to "trakt scrobble watchlist history sync",
      SettingsRoute.ConnectTv to "tv television pair pairing code connect",
      SettingsRoute.Ratings to "rating imdb tmdb mdblist badge score",
      SettingsRoute.AppUpdates to "update version apk install release",
    )
  }
  val settingsSearchResults = remember(settingsSearchQuery) {
    val stopWords = setOf("a", "an", "and", "can", "do", "for", "how", "i", "in", "is", "my", "of", "the", "to", "want", "where")
    val query = settingsSearchQuery.trim().lowercase()
    val tokens = query.split(Regex("[^a-z0-9]+"), limit = 0).filter { it.length > 1 && it !in stopWords }
    if (query.isBlank()) emptyList() else settingsSearchEntries.map { (destination, keywords) ->
      val haystack = "${settingsRouteTitle(destination)} ${settingsRouteSubtitle(destination)} $keywords".lowercase()
      destination to ((if (haystack.contains(query)) 20 else 0) + tokens.count(haystack::contains))
    }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
  }
  var fullScreenProfilePin by rememberSaveable(uiState.pinPromptProfileId) { mutableStateOf("") }
  var showFusionBadgeUrls by rememberSaveable { mutableStateOf(false) }
  var previewFusionSource by remember { mutableStateOf<FusionBadgeSource?>(null) }
  if (showFusionBadgeUrls) {
    FusionBadgeUrlsDialog(
      uiState = uiState,
      onDismiss = { showFusionBadgeUrls = false },
      onImport = onAddFusionBadgeUrl,
      onSetActive = onSetActiveFusionBadgeUrl,
      onRefresh = onRefreshFusionBadgeUrl,
      onRemove = onRemoveFusionBadgeUrl,
      onPreview = { previewFusionSource = it },
    )
  }
  previewFusionSource?.let { source ->
    FusionBadgePreviewDialog(source = source, onDismiss = { previewFusionSource = null })
  }
  if (route == SettingsRoute.Profiles && uiState.pinPromptProfileId != null) {
    val profile = uiState.profiles.firstOrNull { it.id == uiState.pinPromptProfileId }
    ProfilePinPadScreen(
      profile = profile,
      pin = fullScreenProfilePin,
      onPinChange = { fullScreenProfilePin = it.filter(Char::isDigit).take(4) },
      onSubmit = {
        if (fullScreenProfilePin.length == 4) {
          onSubmitProfilePin(fullScreenProfilePin)
          fullScreenProfilePin = ""
        }
      },
      onBack = {
        fullScreenProfilePin = ""
        onCancelProfilePin()
      },
    )
    return
  }
  Box(modifier = Modifier.fillMaxSize()) {
    PullToRefreshBox(
      isRefreshing = settingsRefreshing,
      onRefresh = {
        val justRevealedSearch = route == null && android.os.SystemClock.elapsedRealtime() - settingsSearchRevealedAtMs < 700L
        if (route == null && (!settingsSearchVisible || justRevealedSearch)) {
          settingsSearchVisible = true
          settingsSearchRevealedAtMs = android.os.SystemClock.elapsedRealtime()
        } else {
          settingsRefreshing = true
          onRefreshSync()
          settingsRefreshScope.launch { delay(900); settingsRefreshing = false }
        }
      },
      modifier = Modifier.fillMaxSize().nestedScroll(settingsSearchPullConnection),
    ) {
      val settingsRouteKey = route?.name ?: "settings-home"
      val settingsListState = remember(settingsRouteKey) {
        settingsListStates.getOrPut(settingsRouteKey) { androidx.compose.foundation.lazy.LazyListState() }
      }
      LazyColumn(
        state = settingsListState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = if (route == null) 64.dp else 132.dp, bottom = 126.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
    if (route == null) {
      item {
        Column {
          AdaptivePageTitle(title = "Settings", maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
          AnimatedVisibility(
            visible = settingsSearchVisible,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(280)) + fadeIn(tween(180)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(220)) + fadeOut(tween(140)),
          ) {
            Column(modifier = Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedTextField(
                value = settingsSearchQuery,
                onValueChange = { settingsSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                  IconButton(onClick = { settingsSearchQuery = ""; settingsSearchVisible = false }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close settings search")
                  }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                  focusedContainerColor = MaterialTheme.colorScheme.surface,
                  unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                  disabledContainerColor = MaterialTheme.colorScheme.surface,
                ),
              )
              if (settingsSearchQuery.isNotBlank()) {
                SettingsSection(if (settingsSearchResults.isEmpty()) "No matching settings" else "Results") {
                  settingsSearchResults.forEachIndexed { index, destination ->
                    if (index > 0) SettingsDivider()
                    SettingsNavRow(
                      "GO", MaterialTheme.colorScheme.primary, settingsRouteTitle(destination),
                      settingsRouteSubtitle(destination), onClick = { settingsSearchQuery = ""; onRouteChange(destination) },
                    )
                  }
                  if (settingsSearchResults.isEmpty()) {
                    Text("Try another phrase, such as change subtitle language or install an add-on.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                  }
                }
              }
            }
          }
        }
      }
      item {
        SettingsSection("Account & Profiles") {
          SettingsNavRow(uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }?.name?.trim()?.firstOrNull()?.uppercase() ?: "?", Color(0xFFE5E7EB), "Account", uiState.session?.user?.email ?: "Signed out", onClick = { onRouteChange(SettingsRoute.Account) })
          SettingsDivider()
          SettingsProfileRow(uiState = uiState, onClick = onSwitchProfile)
        }
      }
      item {
        SettingsSection("Preferences") {
          SettingsNavRow("GE", Color(0xFF94A3B8), "General", "Language, colours, and everyday app choices.", onClick = { onRouteChange(SettingsRoute.GeneralPlayback) })
          SettingsDivider()
          SettingsNavRow("HM", Color(0xFFF59E0B), "Home and Appearance", "Choose your Home rows and how pages look.", onClick = { onRouteChange(SettingsRoute.HomeAppearance) })
          SettingsDivider()
          SettingsNavRow("DT", Color(0xFF22C55E), "Detail Screen", "Choose how trailers and ratings appear.", onClick = { onRouteChange(SettingsRoute.DetailScreen) })
          SettingsDivider()
          SettingsNavRow("S", Color(0xFFEC4899), "Streams", "Choose how streams are shown and played.", onClick = { onRouteChange(SettingsRoute.Streams) })
          SettingsDivider()
          SettingsNavRow("NXT", Color(0xFF22C55E), "Playback Automation", "Choose what StreamDek skips and when the next episode starts.", onClick = { onRouteChange(SettingsRoute.PlaybackAutomation) })
          SettingsDivider()
          SettingsNavRow("SUB", Color(0xFFA78BFA), "Subtitles", "Choose automatic subtitles and add subtitle sources.", onClick = { onRouteChange(SettingsRoute.Subtitles) })
        }
      }
      item {
        SettingsSection("Services") {
          SettingsNavRow("+", Color(0xFF22C55E), "Add-ons", "${uiState.addons.count { it.enabled }} on - ${uiState.addons.sumOf { supportedHomeCatalogCount(it) }} Home rows", onClick = { onRouteChange(SettingsRoute.Addons) })
          SettingsDivider()
          SettingsNavRow("M3U", Color(0xFFEC4899), "M3U Playlists", if (uiState.m3uSources.isEmpty()) "Add an IPTV playlist URL" else "${uiState.m3uSources.count { it.enabled }} of ${uiState.m3uSources.size} playlists on", onClick = { onRouteChange(SettingsRoute.M3uPlaylists) })
          SettingsDivider()
          SettingsNavRow("JS", Color(0xFFF59E0B), "Plugins", "${StreamDekPlugins.manager.state.let { state -> val enabledRepos = state.repos.filter { it.enabled }.map { it.url }.toSet(); state.providers.count { it.enabled && it.repoUrl in enabledRepos } }} streaming sources on", onClick = { onRouteChange(SettingsRoute.Plugins) })
          SettingsDivider()
          SettingsNavRow("DB", Color(0xFF38BDF8), "Premium Services", if (uiState.debridAccounts.isEmpty()) "Connect a supported premium service" else "${uiState.debridAccounts.size} services connected", onClick = { onRouteChange(SettingsRoute.Debrid) })
          SettingsDivider()
          SettingsNavRow("T", Color(0xFFA78BFA), "Trakt", if (uiState.traktStatus.connected) "Connected as ${uiState.traktStatus.username ?: "Trakt"}" else "Connect Trakt to keep your activity up to date", onClick = { onRouteChange(SettingsRoute.Trakt) })
          SettingsDivider()
          SettingsNavRow("TV", Color(0xFF38BDF8), "Connect to TV", "Scan or enter a pairing code and manage linked TVs.", onClick = { onRouteChange(SettingsRoute.ConnectTv) })
        }
      }
      item {
        SettingsSection("Downloads") {
          SettingsSwitchRow("DL", Color(0xFF22C55E), "Allow Downloads", "Let StreamDek save eligible movies and episodes for offline playback.", uiState.downloadsEnabled, onDownloadsEnabledChange)
          if (uiState.downloadsEnabled) {
            SettingsDivider()
            SettingsNavRow("DL", Color(0xFF22C55E), "Manage Downloads", "${uiState.downloads.size} download(s) on this device", onClick = { onRouteChange(SettingsRoute.Downloads) })
          }
        }
      }
      item {
        SettingsSection("About") {
          SettingsNavRow("UP", Color(0xFF22C55E), "App Updates", uiState.availableUpdate?.let { "Version ${it.versionName} available" } ?: "Version ${BuildConfig.VERSION_NAME}", onClick = { onRouteChange(SettingsRoute.AppUpdates) })
        }
      }

    } else {
      item { SettingsDetailHeader(route = route) }
      when (route) {
        SettingsRoute.GeneralPlayback -> {
          item {
            SettingsSection("General") {
              SettingsChoiceRow("XA", Color(0xFFA78BFA), "Language", "Choose the language used for the app interface.", supportedAppLanguages.keys.toList(), uiState.appLanguage, onAppLanguageChange)
              SettingsDivider()
              SettingsChoiceRow("MO", Color(0xFF64748B), "Appearance", "Choose the app color scheme.", AppAppearance.values().map { it.name }, uiState.appAppearance.name) { selected ->
                onAppAppearanceChange(AppAppearance.valueOf(selected))
              }
              SettingsDivider()
              ThemePresetPicker(selected = uiState.themePreset, onSelected = onThemePresetChange)
              SettingsDivider()
              SettingsChoiceRow("HDR", Color(0xFF22D3EE), "Header Style", "Choose the header style used on browsing pages.", listOf("Default", "Modern"), if (uiState.headerStyle == HeaderStyle.Classic) "Default" else "Modern") { selected ->
                onHeaderStyleChange(if (selected == "Modern") HeaderStyle.Modern else HeaderStyle.Classic)
              }
              SettingsDivider()
              SettingsSwitchRow("NET", Color(0xFF38BDF8), "Sync on Cellular", "Keep your account and add-ons up to date when using mobile data.", uiState.syncOnCellular, onSyncOnCellularChange)
            }
          }
          item {
            SettingsSection("Floating Navigation") {
              SettingsSwitchRow("LBL", Color(0xFF6366F1), "Show Navigation Labels", "Show page names below the navigation icons.", uiState.showNavLabels, onShowNavLabelsChange)
              SettingsDivider()
              SettingsSwitchRow("NAV", Color(0xFF22D3EE), "Collapsible Floating Navigation", "Hide the navigation after you choose a page, leaving your profile button visible.", uiState.collapsibleNavigationEnabled, onCollapsibleNavigationEnabledChange)
              SettingsDivider()
              NavigationAutoCollapseDelaySlider(
                seconds = uiState.navigationAutoCollapseSeconds,
                enabled = uiState.collapsibleNavigationEnabled,
                onSecondsChange = onNavigationAutoCollapseSecondsChange,
              )
            }
          }
        }
        SettingsRoute.PlaybackAutomation -> {
          item {
            SettingsSection("Playback Automation") {
              SettingsSwitchRow("IN", Color(0xFF60A5FA), "Skip Intro", "Show a button when an intro can be skipped.", uiState.skipIntroEnabled, onSkipIntroEnabledChange)
              SettingsDivider()
              SettingsSwitchRow("RE", Color(0xFF38BDF8), "Skip Recap", "Show a button when a recap can be skipped.", uiState.skipRecapEnabled, onSkipRecapEnabledChange)
              SettingsDivider()
              SettingsSwitchRow("END", Color(0xFFF59E0B), "Skip Ending", "Show a button when the ending can be skipped.", uiState.skipEndingEnabled, onSkipEndingEnabledChange)
              SettingsDivider()
              SettingsSwitchRow("NXT", Color(0xFF22C55E), "Auto-Play Next Episode", "Start the next episode automatically near the end.", uiState.autoPlayNextEpisode, onAutoPlayNextEpisodeChange)
              SettingsDivider()
              SettingsSwitchRow("BG", Color(0xFFA78BFA), "Keep the Same Source", "Try to keep using the same source and video quality for the next episode.", uiState.preferBingeGroup, onPreferBingeGroupChange)
            }
          }
          item {
            NextEpisodeThresholdSettings(uiState, onNextEpisodeThresholdModeChange, onNextEpisodeThresholdPercentChange, onNextEpisodeThresholdMinutesChange)
          }
        }
        SettingsRoute.Subtitles -> {
          item {
            SettingsSection("Subtitles") {
              SettingsSwitchRow("SUB", Color(0xFFA78BFA), "Auto-Load Subtitles", "Automatically choose matching subtitles when playback starts.", uiState.autoLoadSubtitles, onAutoLoadSubtitlesChange)
            }
          }
          item { SubtitleSourcesSettings(ownerKey = settingsOwnerKey(uiState)) }
        }
        SettingsRoute.ConnectTv -> {
          item { ConnectToTvSettings(uiState = uiState) }
        }
        SettingsRoute.HomeAppearance -> {
          item {
            SettingsSection("Home and Appearance") {
              SettingsNavRow("GRID", Color(0xFF38BDF8), "Catalog & Home Layout", "Choose which rows appear on Home and drag to reorder them.", value = "${uiState.homeCatalogRows.count { it.enabled }}", onClick = { onRouteChange(SettingsRoute.HomeLayout) })
              SettingsDivider()
              SettingsChoiceRow("LAY", Color(0xFF22D3EE), "Page Style", "Choose how media pages and the Home spotlight are arranged.", DetailPageStyle.values().map { it.name }, uiState.detailPageStyle.name) { selected ->
                onDetailPageStyleChange(DetailPageStyle.valueOf(selected))
              }
              SettingsDivider()
              SettingsChoiceRow("PLAY", Color(0xFF22C55E), "Continue Watching Style", "Choose how continue watching cards appear on Home.", ContinueWatchingStyle.values().map { it.name }, uiState.continueWatchingStyle.name) { selected ->
                onContinueWatchingStyleChange(ContinueWatchingStyle.valueOf(selected))
              }
              SettingsDivider()
              SettingsSwitchRow("TV", Color(0xFFF97316), "Landscape Live TV Cards", "Show live TV channels as wide landscape cards.", uiState.liveLandscapeCards, onLiveLandscapeCardsChange)
              SettingsDivider()
              SettingsSwitchRow("FAV", Color(0xFFFACC15), "Card-style Player Favourites", "Show channel artwork in the live-player favourites drawer. Off uses the compact text list.", uiState.liveFavouriteDrawerCards, onLiveFavouriteDrawerCardsChange)
              SettingsDivider()
              SettingsSwitchRow("DOC", Color(0xFF94A3B8), "Show Hero Synopsis", "Show the story summary in the Home spotlight.", uiState.showHeroSynopsis, onShowHeroSynopsisChange)
              SettingsDivider()
              SettingsSwitchRow("AMB", Color(0xFFA78BFA), "Ambient Background", "Show a colourful ambient glow behind home and detail screens.", uiState.vividAmbient, onVividAmbientChange)
              SettingsDivider()
              AmbientTintSlider(percent = uiState.ambientTintPercent, enabled = uiState.vividAmbient, onPercentChange = onAmbientTintPercentChange)
            }
          }
        }
        SettingsRoute.HomeLayout -> item { CatalogHomeLayoutSettings(uiState, onDefaultAppCatalogsEnabledChange, onHomeCatalogRowEnabledChange, onMoveHomeCatalogRow, dragScrollBy = { delta -> settingsListState.scrollBy(delta) }) }
        SettingsRoute.DetailScreen -> {
          item {
            SettingsSection("Detail Screen") {
              SettingsSwitchRow("TRL", Color(0xFF22C55E), "Hero trailer autoplay", "Play a trailer automatically at the top of a media page when one is available.", uiState.heroTrailerAutoplay, onHeroTrailerAutoplayChange)
              SettingsDivider()
              SettingsChoiceRow("HD", Color(0xFF38BDF8), "Trailer resolution", "Choose the best video quality trailers may use.", listOf("360p", "720p", "1080p"), "${uiState.heroTrailerResolution}p") { selected -> onHeroTrailerResolutionChange(selected.removeSuffix("p").toInt()) }
              SettingsDivider()
              SettingsNavRow("MDB", Color(0xFFF5C518), "Ratings", "Turn ratings on and choose which rating services appear.", value = if (uiState.ratingsEnabled) "Enabled" else "Off", onClick = { onRouteChange(SettingsRoute.Ratings) })
              SettingsDivider()
              SettingsChoiceRow("SEA", Color(0xFF38BDF8), "Season Tabs", "Choose regular tabs or poster image tabs for series seasons.", SeasonTabStyle.values().map { it.name }, uiState.seasonTabStyle.name) { selected ->
                onSeasonTabStyleChange(SeasonTabStyle.valueOf(selected))
              }
            }
          }
        }
        SettingsRoute.Ratings -> {
          item {
            RatingsSettingsSummary(
              uiState = uiState,
              onRatingsEnabledChange = onRatingsEnabledChange,
              onExternalRatingsEnabledChange = onExternalRatingsEnabledChange,
              onRatingProviderEnabledChange = onRatingProviderEnabledChange,
              onMdblistApiKeyChange = onMdblistApiKeyChange,
            )
          }
          item {
            SettingsSection("Add-on Catalogues") {
              SettingsSwitchRow("TMD", Color(0xFF22C55E), "TMDB Ratings on Add-on Rows", "Show TMDB ratings for titles from your installed add-ons.", uiState.showAddonTmdbRatings, onShowAddonTmdbRatingsChange)
            }
          }
        }
        SettingsRoute.Streams -> {
          item {
            SettingsSection("Streams") {
              SettingsSwitchRow("LST", Color(0xFF22D3EE), "Show Streams List", "Show available streams on media pages.", uiState.showStreamsList, onShowStreamsListChange)
              SettingsDivider()
              SettingsSwitchRow("SRC", Color(0xFF6366F1), "Remember Last Source", "Try the source you used last when you return to a title.", uiState.rememberLastSource, onRememberLastSourceChange)
              SettingsDivider()
              SettingsChoiceRow("Q", Color(0xFF22C55E), "Preferred Stream Quality", "Put your preferred video quality near the top.", listOf("2160p", "1080p", "720p", "Auto"), uiState.preferredQuality, onPreferredQualityChange)
              SettingsDivider()
              SettingsChoiceRow("GB", Color(0xFFF97316), "Max File Size", "Exclude streams larger than this size.", listOf("0", "4", "8", "12", "20"), uiState.maxFileSizeGb.toString()) { onMaxFileSizeChange(it.toInt()) }
              SettingsDivider()
              SettingsSwitchRow("BLR", Color(0xFFA78BFA), "Blur Unwatched Episodes", "Blur episode art until the episode has been marked watched.", uiState.blurUnwatchedEpisodes, onBlurUnwatchedEpisodesChange)
            }
          }
          item {
            SettingsSection("Stream Details") {
              SettingsSwitchRow("FSN", Color(0xFFEC4899), "Stream Detail Badges", "Show useful quality and format labels on stream choices.", uiState.fusionBadgesEnabled, onFusionBadgesChange)
              SettingsDivider()
              SettingsSwitchRow("SIZ", Color(0xFFF97316), "Size Badges", "Show the download size on stream choices.", uiState.showSizeBadges, onShowSizeBadgesChange)
              SettingsDivider()
              SettingsChoiceRow("POS", Color(0xFF22D3EE), "Badge Position", "Choose whether stream labels appear at the top or bottom.", listOf("Top", "Bottom"), uiState.badgePosition, onBadgePositionChange)
              SettingsDivider()
              SettingsNavRow("TAG", Color(0xFFA78BFA), "Badge Styles", fusionBadgeSourceSummary(uiState), onClick = { showFusionBadgeUrls = true })
            }
          }
          item {
            SettingsSection("Playback") {
              SettingsChoiceRow("PLY", Color(0xFF22C55E), "Default Player", "Automatic starts with Media3 and switches to mpv if a source cannot play.", listOf("Auto", "Media3", "MPV"), uiState.playerEngine, onPlayerEngineChange)
              SettingsDivider()
              SettingsChoiceRow("AUD", Color(0xFFF59E0B), "Default Audio", "Choose the spoken language StreamDek should prefer when a video offers more than one.", listOf("en", "original", "es", "fr", "de", "it", "pt", "ja", "ko", "hi"), uiState.preferredAudioLanguage, onPreferredAudioLanguageChange)
              SettingsDivider()
              SettingsSwitchRow("PIP", Color(0xFF6366F1), "Floating Player", "Keep the video in a small window when you leave StreamDek.", uiState.pictureInPictureEnabled, onPictureInPictureEnabledChange)
              SettingsDivider()
              SettingsChoiceRow("HW", Color(0xFFA78BFA), "mpv Video Compatibility", "Used when mpv is selected or Automatic switches to it. Recommended works for most videos.", listOf("HW+", "HW", "SW"), uiState.decoderMode, onDecoderModeChange)
              SettingsDivider()
              SettingsChoiceRow("SF", Color(0xFF06B6D4), "mpv Display", "Used when mpv is selected or Automatic switches to it. Try Compatibility if the picture is missing.", listOf("Standard", "Compatibility"), uiState.renderSurface, onRenderSurfaceChange)
            }
          }
          item {
            SettingsSection("Peer-to-Peer Playback") {
              SettingsSwitchRow("TOR", Color(0xFF22C55E), "Enable Peer-to-Peer Playback", "Play torrent or magnet sources through this phone when no direct web stream is available. Video data is temporary, not a saved download.", uiState.torrentServerSettings.enabled, onCheckedChange = { enabled ->
                onUpdateTorrentServerSettings { current -> current.copy(enabled = enabled) }
              })
              SettingsDivider()
              SettingsChoiceRow("SRV", Color(0xFF38BDF8), "Source Handling", "Built-in engine supports peer-to-peer sources. Web links only ignores torrent and magnet sources.", listOf("Built-in engine", "Web links only"), if (uiState.torrentServerSettings.streamingMode == "server") "Built-in engine" else "Web links only") {
                onUpdateTorrentServerSettings { current -> current.copy(streamingMode = if (it == "Built-in engine") "server" else "regular_http") }
              }
              SettingsDivider()
              SettingsChoiceRow("PRF", Color(0xFF22C55E), "Startup Profile", "Balanced works well for most people. Battery saver uses less power; faster choices may use more battery and data.", listOf("Balanced", "Battery saver", "Fast", "Fastest"), when (uiState.torrentServerSettings.profile) { "soft" -> "Battery saver"; "fast" -> "Fast"; "ultra_fast" -> "Fastest"; else -> "Balanced" }) {
                onUpdateTorrentServerSettings { current -> current.copy(profile = when (it) { "Battery saver" -> "soft"; "Fast" -> "fast"; "Fastest" -> "ultra_fast"; else -> "default" }) }
              }
              SettingsDivider()
              SettingsChoiceRow("CCH", Color(0xFFF59E0B), "Temporary Storage Limit", "Choose how much device storage the playback cache may use. StreamDek removes old temporary data as needed.", listOf("2", "5", "10", "20"), uiState.torrentServerSettings.cacheSizeGb.toString()) {
                onUpdateTorrentServerSettings { current -> current.copy(cacheSizeGb = it.toInt()) }
              }
              SettingsDivider()
              SettingsSwitchRow("BG", Color(0xFFEC4899), "Keep Engine Ready", "Keep peer-to-peer playback ready while StreamDek is in the background.", uiState.torrentServerSettings.runAsForegroundService, onCheckedChange = { enabled ->
                onUpdateTorrentServerSettings { current -> current.copy(runAsForegroundService = enabled) }
              })
              SettingsDivider()
              TorrentServerStatusRow(uiState.torrentServerStatus)
              SettingsDivider()
              SettingsInfoRow("Storage Used", "${uiState.torrentServerStatus.cacheSizeGb} GB", formatBytesLabel(uiState.torrentServerStatus.cacheUsageBytes))
              uiState.torrentServerStatus.lastStartupError.takeIf { it.isNotBlank() }?.let { message ->
                SettingsDivider()
                SettingsInfoRow("Playback Problem", message, uiState.torrentServerStatus.lifecycleState.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() })
              }
            }
          }
        }
        SettingsRoute.Addons -> item { AddonsSettingsSummary(uiState, onRefreshAddons, onInstallAddon, onToggleAddon, onUninstallAddon, onMoveAddon, dragScrollBy = { delta -> settingsListState.scrollBy(delta) }) }
        SettingsRoute.M3uPlaylists -> item { M3uPlaylistsSettingsSummary(uiState, onAddM3uPlaylist, onRemoveM3uPlaylist, onSetM3uPlaylistEnabled, onMoveM3uPlaylist, onRefreshM3uPlaylists) }
        SettingsRoute.Downloads -> item { DownloadsSettingsSummary(uiState, onRefreshDownloads, onRemoveDownload, onPlayDownload) }
        SettingsRoute.Plugins -> item { PluginsSettingsSummary() }
        SettingsRoute.Debrid -> item { DebridSettingsSummary(uiState, onRefreshDebrid, onAddDebrid, onRemoveDebrid, onMoveDebrid) }
        SettingsRoute.Trakt -> item { TraktSettingsSummary(uiState, onRequestTraktDeviceCode, onPollTraktAuthorization, onDisconnectTrakt, onRefreshTrakt) }
        SettingsRoute.Profiles -> item { ProfilesSettingsSummary(uiState, onSwitchProfile, onSelectProfile, onSubmitProfilePin, onCancelProfilePin, onCreateProfile, onUpdateProfile, onDeleteProfile, onMakeDefaultProfile, onUpdateProfilePin) }
        SettingsRoute.Account -> item { AccountSettingsSummary(uiState, onSignOut, onSignIn, onRefreshSync) }
        SettingsRoute.AppUpdates -> item { AppUpdatesSettingsSummary(uiState, onAutoUpdateChecksChange, onCheckForUpdates, onStartUpdate) }
      }
    }
  }
    }
    if (route != null) {

      GlassCircleButton(
        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 22.dp, top = 18.dp).zIndex(5f),
        onClick = onBack,
      ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface) }
    }
  }

}
@Composable
private fun ProfilesSettingsSummary(
  uiState: AppUiState,
  onOpenSwitcher: () -> Unit,
  onSelectProfile: (String) -> Unit,
  onSubmitProfilePin: (String) -> Unit,
  onCancelProfilePin: () -> Unit,
  onCreateProfile: (String, Int) -> Unit,
  onUpdateProfile: (String, String, Int) -> Unit,
  onDeleteProfile: (String) -> Unit,
  onMakeDefaultProfile: (String) -> Unit,
  onUpdateProfilePin: (String, String?) -> Unit,
) {
  var createExpanded by rememberSaveable { mutableStateOf(false) }
  var profileName by rememberSaveable { mutableStateOf("") }
  var selectedAvatarIndex by rememberSaveable { mutableStateOf(0) }
  var expandedProfileId by rememberSaveable { mutableStateOf(uiState.activeProfileId) }
  var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
  var editingName by rememberSaveable(editingProfileId) { mutableStateOf("") }
  var editingAvatarIndex by rememberSaveable(editingProfileId) { mutableStateOf(0) }
  var editingPinProfileId by rememberSaveable { mutableStateOf<String?>(null) }
  var newPin by rememberSaveable(editingPinProfileId) { mutableStateOf("") }
  var confirmPin by rememberSaveable(editingPinProfileId) { mutableStateOf("") }
  var deleteProfileId by rememberSaveable { mutableStateOf<String?>(null) }
  var pin by rememberSaveable(uiState.pinPromptProfileId) { mutableStateOf("") }

  LaunchedEffect(uiState.activeProfileId, uiState.profiles.map(StreamProfile::id)) {
    if (expandedProfileId !in uiState.profiles.map(StreamProfile::id)) expandedProfileId = uiState.activeProfileId
  }

  uiState.pinPromptProfileId?.let { profileId ->
    val profile = uiState.profiles.firstOrNull { it.id == profileId }
    ProfilePinPadScreen(
      profile = profile,
      pin = pin,
      onPinChange = { updated -> pin = updated.filter(Char::isDigit).take(4) },
      onSubmit = {
        if (pin.length == 4) {
          onSubmitProfilePin(pin)
          pin = ""
        }
      },
      onBack = { pin = ""; onCancelProfilePin() },
    )
    return
  }

  deleteProfileId?.let { profileId ->
    val profile = uiState.profiles.firstOrNull { it.id == profileId }
    AlertDialog(
      onDismissRequest = { deleteProfileId = null },
      icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
      title = { Text("Delete ${profile?.name ?: "profile"}?") },
      text = { Text("This removes this profile from StreamDek. Other profiles and their viewing activity will stay untouched.") },
      confirmButton = {
        Button(
          onClick = { onDeleteProfile(profileId); deleteProfileId = null },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
        ) { Text("Delete profile") }
      },
      dismissButton = { TextButton(onClick = { deleteProfileId = null }) { Text("Keep profile") } },
    )
  }

  val activeProfile = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }
  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    activeProfile?.let { profile ->
      Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier.size(72.dp).clip(CircleShape).background(profileAvatarColor(profile.avatarIndex))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f), CircleShape),
            ) { ProfileAvatarImage(profile.avatarIndex, Modifier.fillMaxSize()) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
              Text("CURRENT PROFILE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
              Text(profile.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(
                listOfNotNull(if (profile.isDefault) "Default" else null, if (profile.hasPinSet) "PIN protected" else null).joinToString(" • ").ifBlank { "Ready to watch" },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
          if (uiState.session != null) {
            OutlinedButton(onClick = onOpenSwitcher, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
              Icon(Icons.Rounded.ManageAccounts, contentDescription = null, modifier = Modifier.size(19.dp))
              Spacer(Modifier.width(8.dp))
              Text("Open profile switcher", fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Your profiles", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("${uiState.profiles.size} of 3 profiles", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f), style = MaterialTheme.typography.bodySmall)
      }
      Button(
        onClick = { createExpanded = !createExpanded },
        enabled = uiState.profiles.size < 3,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
      ) {
        Icon(if (createExpanded) Icons.Rounded.Close else Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (createExpanded) "Close" else "Add")
      }
    }

    AnimatedVisibility(
      visible = createExpanded,
      enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(240)) + fadeIn(tween(180)),
      exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(200)) + fadeOut(tween(140)),
    ) {
      Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("Create a profile", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
          OutlinedTextField(
            value = profileName,
            onValueChange = { profileName = it.take(32) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { InputGuideText("Profile name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
          )
          ProfileAvatarPicker(selectedAvatarIndex = selectedAvatarIndex, onSelect = { selectedAvatarIndex = it })
          Button(
            onClick = {
              val name = profileName.trim()
              if (name.isNotBlank()) {
                onCreateProfile(name, selectedAvatarIndex)
                profileName = ""
                selectedAvatarIndex = (selectedAvatarIndex + 1).floorMod(12)
                createExpanded = false
              }
            },
            enabled = profileName.trim().isNotBlank() && uiState.profiles.size < 3,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
          ) { Text("Create profile", fontWeight = FontWeight.Bold) }
        }
      }
    }

    uiState.profiles.forEach { profile ->
      val expanded = expandedProfileId == profile.id
      val active = profile.id == uiState.activeProfileId
      Surface(
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.065f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.26f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f)),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().clickable {
              expandedProfileId = if (expanded) null else profile.id
              if (expanded) {
                editingProfileId = null
                editingPinProfileId = null
              }
            }.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier.size(58.dp).clip(CircleShape).background(profileAvatarColor(profile.avatarIndex))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), CircleShape),
            ) { ProfileAvatarImage(profile.avatarIndex, Modifier.fillMaxSize()) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(profile.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(profileStatusLabel(profile, uiState.activeProfileId), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
              if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
              contentDescription = if (expanded) "Collapse profile" else "Manage profile",
              tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            )
          }

          AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(230)) + fadeIn(tween(170)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(190)) + fadeOut(tween(130)),
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              SettingsDivider()
              when {
                editingProfileId == profile.id -> {
                  Text("Edit profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                  OutlinedTextField(
                    value = editingName,
                    onValueChange = { editingName = it.take(32) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { InputGuideText("Profile name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                  )
                  ProfileAvatarPicker(selectedAvatarIndex = editingAvatarIndex, onSelect = { editingAvatarIndex = it })
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                      onClick = {
                        val name = editingName.trim()
                        if (name.isNotBlank()) {
                          onUpdateProfile(profile.id, name, editingAvatarIndex)
                          editingProfileId = null
                        }
                      },
                      enabled = editingName.trim().isNotBlank(),
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(14.dp),
                    ) { Text("Save") }
                    OutlinedButton(onClick = { editingProfileId = null }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Cancel") }
                  }
                }
                editingPinProfileId == profile.id -> {
                  Text(if (profile.hasPinSet) "Change PIN" else "Protect with a PIN", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                  OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { InputGuideText("New 4-digit PIN") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                  )
                  OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { InputGuideText("Confirm PIN") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                      onClick = {
                        onUpdateProfilePin(profile.id, newPin)
                        editingPinProfileId = null
                        newPin = ""
                        confirmPin = ""
                      },
                      enabled = newPin.length == 4 && newPin == confirmPin,
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(14.dp),
                    ) { Text(if (profile.hasPinSet) "Change PIN" else "Set PIN") }
                    OutlinedButton(
                      onClick = { editingPinProfileId = null; newPin = ""; confirmPin = "" },
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(14.dp),
                    ) { Text("Cancel") }
                  }
                  if (profile.hasPinSet) {
                    TextButton(
                      onClick = { onUpdateProfilePin(profile.id, null); editingPinProfileId = null; newPin = ""; confirmPin = "" },
                      modifier = Modifier.align(Alignment.End),
                    ) { Text("Remove PIN", color = MaterialTheme.colorScheme.error) }
                  }
                }
                else -> {
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                      onClick = { onSelectProfile(profile.id) },
                      enabled = !active,
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text(if (active) "In use" else "Use profile")
                    }
                    OutlinedButton(
                      onClick = { onMakeDefaultProfile(profile.id) },
                      enabled = !profile.isDefault,
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text(if (profile.isDefault) "Default" else "Make default")
                    }
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                      onClick = {
                        editingProfileId = profile.id
                        editingPinProfileId = null
                        editingName = profile.name
                        editingAvatarIndex = profile.avatarIndex
                      },
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(14.dp),
                    ) { Text("Edit") }
                    OutlinedButton(
                      onClick = { editingPinProfileId = profile.id; editingProfileId = null },
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(17.dp))
                      Spacer(Modifier.width(6.dp))
                      Text(if (profile.hasPinSet) "Manage PIN" else "Add PIN")
                    }
                  }
                  TextButton(
                    onClick = { deleteProfileId = profile.id },
                    enabled = !profile.isDefault && uiState.profiles.size > 1,
                    modifier = Modifier.align(Alignment.End),
                  ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete profile", color = MaterialTheme.colorScheme.error)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun profileStatusLabel(profile: StreamProfile, activeProfileId: String?): String = buildList {
  if (profile.id == activeProfileId) add("In use")
  if (profile.isDefault) add("Default")
  if (profile.hasPinSet) add("PIN protected")
  if (isEmpty()) add("Ready")
}.joinToString(" • ")
private fun normalizeTvCode(value: String): String {
  val cleaned = value.uppercase().filter(Char::isLetterOrDigit).take(8)
  return if (cleaned.length <= 4) cleaned else "${cleaned.take(4)}-${cleaned.drop(4)}"
}

private fun extractTvCode(payload: String): String? {
  val trimmed = payload.trim()
  if (trimmed.isBlank()) return null
  val queryCode = runCatching { android.net.Uri.parse(trimmed).getQueryParameter("code") }.getOrNull()
  val normalized = normalizeTvCode(queryCode ?: trimmed)
  return normalized.takeIf { it.length == 9 }
}

@Composable
private fun ConnectToTvSettings(uiState: AppUiState) {
  val session = uiState.session
  val profileId = uiState.activeProfileId
  val apiClient = remember { StreamDekApiClient() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var code by rememberSaveable { mutableStateOf("") }
  var pendingCode by rememberSaveable { mutableStateOf<String?>(null) }
  var devices by remember { mutableStateOf<List<LinkedTvDevice>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var status by rememberSaveable { mutableStateOf<String?>(null) }
  var refreshKey by remember { mutableIntStateOf(0) }
  val scanner = remember(context) {
    val options = GmsBarcodeScannerOptions.Builder()
      .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
      .enableAutoZoom()
      .build()
    GmsBarcodeScanning.getClient(context, options)
  }

  LaunchedEffect(session?.user?.uid, profileId, refreshKey) {
    if (session == null) {
      devices = emptyList()
      return@LaunchedEffect
    }
    loading = true
    apiClient.fetchLinkedTvDevices(session, profileId)
      .onSuccess { devices = it }
      .onFailure { status = it.message ?: "Could not load linked TVs." }
    loading = false
  }

  fun requestAuthorization(rawCode: String) {
    val normalized = normalizeTvCode(rawCode)
    if (normalized.length != 9) {
      status = "Enter the full 8-character TV code."
    } else {
      pendingCode = normalized
      status = null
    }
  }

  pendingCode?.let { confirmationCode ->
    AlertDialog(
      onDismissRequest = { if (!busy) pendingCode = null },
      icon = { Icon(Icons.Rounded.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      title = { Text("Authorize TV sign-in") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Approve StreamDek TV using the pairing code below. The TV will finish signing in as soon as this is confirmed.")
          Text(confirmationCode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
      },
      confirmButton = {
        Button(
          enabled = !busy,
          onClick = {
            val activeSession = session ?: return@Button
            busy = true
            status = "Authorizing TV..."
            scope.launch {
              apiClient.activateTvCode(activeSession, confirmationCode)
                .onSuccess { deviceName ->
                  status = "${deviceName ?: "TV"} linked successfully."
                  pendingCode = null
                  code = ""
                  refreshKey += 1
                }
                .onFailure { status = it.message ?: "Could not link this TV right now." }
              busy = false
            }
          },
        ) { if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Authorize TV") }
      },
      dismissButton = { TextButton(enabled = !busy, onClick = { pendingCode = null }) { Text("Cancel") } },
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    if (session == null) {
      SettingsSection("Connect to TV") {
        Text("Sign in on this phone before linking a StreamDek TV.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
      }
      return@Column
    }
    SettingsSection("Scan QR Code") {
      Text("Scan the QR code shown on StreamDek TV. You will confirm the code before access is approved.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f), style = MaterialTheme.typography.bodyMedium)
      Spacer(modifier = Modifier.height(14.dp))
      Button(
        onClick = {
          scanner.startScan()
            .addOnSuccessListener { barcode ->
              val extracted = extractTvCode(barcode.rawValue.orEmpty())
              if (extracted == null) status = "That QR code is not a StreamDek TV pairing code." else {
                code = extracted
                requestAuthorization(extracted)
              }
            }
            .addOnFailureListener { status = it.message ?: "Could not open the QR scanner." }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Open Scanner", fontWeight = FontWeight.Bold)
      }
    }
    SettingsSection("Enter Code Manually") {
      Text("Type the 8-character pairing code displayed on the TV.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f), style = MaterialTheme.typography.bodyMedium)
      Spacer(modifier = Modifier.height(12.dp))
      OutlinedTextField(
        value = code,
        onValueChange = { code = normalizeTvCode(it) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("TV pairing code") },
        placeholder = { InputGuideText("ABCD-1234") },
      )
      Spacer(modifier = Modifier.height(12.dp))
      Button(onClick = { requestAuthorization(code) }, enabled = code.length == 9 && !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Authorize TV", fontWeight = FontWeight.Bold)
      }
    }
    status?.let { Text(it, color = if (it.contains("success", true) || it.contains("linked", true)) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f), fontWeight = FontWeight.SemiBold) }
    SettingsSection("Linked TVs") {
      Text("Manage televisions already authorized for this account.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f), style = MaterialTheme.typography.bodyMedium)
      Spacer(modifier = Modifier.height(12.dp))
      when {
        loading -> Box(modifier = Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        devices.isEmpty() -> Text("No TVs linked yet.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
        else -> devices.forEachIndexed { index, device ->
          if (index > 0) SettingsDivider()
          Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(device.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
              Text(if (device.isCurrent) "This TV session" else device.lastSeenAt?.let { "Last seen $it" } ?: "Linked TV", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(enabled = !busy, onClick = {
              busy = true
              scope.launch {
                apiClient.disconnectAccountDevice(session, device.id)
                  .onSuccess { status = "${device.name} disconnected."; refreshKey += 1 }
                  .onFailure { status = it.message ?: "Could not disconnect this TV." }
                busy = false
              }
            }) { Text("Disconnect") }
          }
        }
      }
    }
  }
}

@Composable
private fun activeProfileName(uiState: AppUiState): String {
  val profile = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }
  return profile?.let { "Open profile selector - current ${it.name}" } ?: "Open the profile selector immediately"
}

@Composable
private fun SettingsDetailHeader(route: SettingsRoute) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    AdaptivePageTitle(title = settingsRouteTitle(route), maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
    Text(settingsRouteSubtitle(route), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun NextEpisodeThresholdSettings(
  uiState: AppUiState,
  onModeChange: (String) -> Unit,
  onPercentChange: (Int) -> Unit,
  onMinutesChange: (Int) -> Unit,
) {
  SettingsSection("Next Episode Button") {
    Text("Choose when the next-episode button appears near the end.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      listOf("percent" to "Percentage", "minutes" to "Minutes").forEach { (mode, label) ->
        val selected = uiState.nextEpisodeThresholdMode == mode
        OutlinedButton(
          onClick = { onModeChange(mode) },
          modifier = Modifier.weight(1f).height(48.dp),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
          ),
          border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        ) { Text(label, fontWeight = FontWeight.Bold) }
      }
    }
    Spacer(modifier = Modifier.height(14.dp))
    val percentMode = uiState.nextEpisodeThresholdMode == "percent"
    val value = if (percentMode) uiState.nextEpisodeThresholdPercent else uiState.nextEpisodeThresholdMinutes
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp)) {
      Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(if (percentMode) "$value% watched" else "$value min left", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
          Text("Drag to adjust", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
        }
        androidx.compose.material3.Slider(
          value = value.toFloat(),
          onValueChange = { updated -> if (percentMode) onPercentChange((updated + 0.5f).toInt()) else onMinutesChange((updated + 0.5f).toInt()) },
          valueRange = if (percentMode) 50f..99f else 1f..15f,
          steps = if (percentMode) 48 else 13,
        )
      }
    }
  }
}

private fun settingsRouteTitle(route: SettingsRoute): String = when (route) {
  SettingsRoute.GeneralPlayback -> "General"
  SettingsRoute.HomeAppearance -> "Home and Appearance"
  SettingsRoute.HomeLayout -> "Catalog & Home Layout"
  SettingsRoute.DetailScreen -> "Detail Screen"
  SettingsRoute.Streams -> "Streams"
  SettingsRoute.Addons -> "Add-ons"
  SettingsRoute.M3uPlaylists -> "M3U Playlists"
  SettingsRoute.Downloads -> "Downloads"
  SettingsRoute.Plugins -> "Plugins"
  SettingsRoute.ConnectTv -> "Connect to TV"
  SettingsRoute.Debrid -> "Premium Services"
  SettingsRoute.Trakt -> "Trakt"
  SettingsRoute.PlaybackAutomation -> "Playback Automation"
  SettingsRoute.Subtitles -> "Subtitles"
  SettingsRoute.Ratings -> "Ratings"
  SettingsRoute.Profiles -> "Profiles"
  SettingsRoute.Account -> "Account and Services"
  SettingsRoute.AppUpdates -> "App Updates"
}

private fun settingsRouteSubtitle(route: SettingsRoute): String = when (route) {
  SettingsRoute.GeneralPlayback -> "Choose the language, colours, and everyday app options."
  SettingsRoute.HomeAppearance -> "Choose what appears on Home and how the app looks."
  SettingsRoute.HomeLayout -> "Choose which rows appear on Home and drag to reorder them. Changes apply in the background."
  SettingsRoute.DetailScreen -> "Choose how trailers and title information appear."
  SettingsRoute.Streams -> "Choose how StreamDek sorts, labels, and shows streams."
  SettingsRoute.Addons -> "Add, arrange, turn on, or remove streaming sources."
  SettingsRoute.M3uPlaylists -> "Add IPTV M3U playlist URLs and choose which ones are on."
  SettingsRoute.Downloads -> "See, play, and remove titles saved for offline playback."
  SettingsRoute.Plugins -> "Add plugin collections and choose the streaming sources they provide."
  SettingsRoute.ConnectTv -> "Pair this phone with StreamDek TV and manage authorized televisions."
  SettingsRoute.Debrid -> "Connect premium services and choose which one StreamDek tries first."
  SettingsRoute.Trakt -> "Connect Trakt and keep the current profile up to date."
  SettingsRoute.PlaybackAutomation -> "Choose what can be skipped and when the next episode starts."
  SettingsRoute.Subtitles -> "Choose automatic subtitles and manage the sources StreamDek searches."
  SettingsRoute.Ratings -> "Choose which ratings appear on media pages."
  SettingsRoute.Profiles -> "Create, switch, secure, and manage local viewing profiles."
  SettingsRoute.Account -> "Refresh account state or sign in and out."
  SettingsRoute.AppUpdates -> "See your app version and check for updates."
}

private fun settingsGlyph(icon: String): ImageVector = when (icon) {
  "@" -> Icons.Rounded.AccountCircle
  "+", "JS" -> Icons.Rounded.Extension
  "DB" -> Icons.Rounded.Cloud
  "T" -> Icons.Rounded.Link
  "GE" -> Icons.Rounded.Tune
  "NET" -> Icons.Rounded.Wifi
  "HW" -> Icons.Rounded.Memory
  "SF" -> Icons.Rounded.AspectRatio
  "PIP" -> Icons.Rounded.PictureInPicture
  "IN" -> Icons.Rounded.FastForward
  "RE" -> Icons.Rounded.Replay
  "LINK" -> Icons.Rounded.Link
  "END" -> Icons.Rounded.SkipNext
  "NXT" -> Icons.Rounded.SkipNext
  "BG" -> Icons.Rounded.GridView
  "SUB", "Aa" -> Icons.Rounded.Subtitles
  "LBL", "DOC", "AMB" -> Icons.Rounded.Visibility
  "HM", "GRID", "LAY" -> Icons.Rounded.GridView
  "DT", "TRL", "PLAY" -> Icons.Rounded.Movie
  "S", "FSN", "POS" -> Icons.Rounded.Tune
  "SIZ", "GB", "APP" -> Icons.Rounded.Storage
  "Q" -> Icons.Rounded.HighQuality
  "URL" -> Icons.Rounded.Link
  "UP" -> Icons.Rounded.Update
  "REF" -> Icons.Rounded.Refresh
  "XA", "HI" -> Icons.Rounded.Language
  "MO", "TH" -> Icons.Rounded.Palette
  "FOL" -> Icons.Rounded.Folder
  "SEA", "TV", "M3U" -> Icons.Rounded.Tv
  "LIVE" -> Icons.Rounded.Tv
  "DL" -> Icons.Rounded.Download
  "MDB", "RAT" -> Icons.Rounded.Star
  else -> Icons.Rounded.Security
}

@Composable
private fun SettingsProfileRow(uiState: AppUiState, onClick: () -> Unit) {
  val profile = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(profileAvatarColor(profile?.avatarIndex ?: 0))
        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      if (profile != null) {
        ProfileAvatarImage(avatarIndex = profile.avatarIndex, modifier = Modifier.fillMaxSize())
      } else {
        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
      }
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("Switch Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      Text(profile?.let { "Current: ${it.name}" } ?: "Create or select a viewing profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
  }
}
@Composable
private fun SettingsNavRow(icon: String, iconColor: Color, title: String, subtitle: String, value: String? = null, onClick: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
    SettingsIcon(icon, iconColor)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    value?.let {
      Text(if (title == "Max File Size" && it == "0") "Unlimited" else if (it == "Auto") "Best Available" else it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
  }
}

@Composable
private fun SettingsStaticRow(icon: String, iconColor: Color, title: String, subtitle: String) {
  Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
    SettingsIcon(icon, iconColor)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
    }
  }
}

private fun formatBytesLabel(bytes: Long): String {
  if (bytes <= 0L) return "No storage used"
  val kib = 1024.0
  val mib = kib * 1024.0
  val gib = mib * 1024.0
  return when {
    bytes >= gib -> String.format(Locale.US, "%.1f GB used", bytes / gib)
    bytes >= mib -> String.format(Locale.US, "%.1f MB used", bytes / mib)
    else -> String.format(Locale.US, "%.0f KB used", bytes / kib)
  }
}

@Composable
private fun SettingsInfoRow(title: String, value: String, subtitle: String) {
  Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
    SettingsIcon("APP", Color(0xFF94A3B8))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    Text(value, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
  }
}

@Composable
private fun TorrentServerStatusRow(status: TorrentServerStatus) {
  Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(if (status.isOnline) Color(0xFF22C55E) else Color(0xFF64748B)))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("Playback Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      Text(if (status.isOnline) "Ready to play" else "Needs attention before playback", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Text(if (status.isOnline) "Ready" else "Not ready", color = if (status.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun ThemePresetPicker(selected: AppThemePreset, onSelected: (AppThemePreset) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 10.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      SettingsIcon("TH", Color(0xFFF59E0B))
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text("Choose the colour theme used across the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
      }
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      AppThemePreset.values().forEach { preset ->
        val palette = themeAccentPalette(preset)
        Column(
          modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected == preset) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .border(1.dp, if (selected == preset) MaterialTheme.colorScheme.primary.copy(alpha = 0.64f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .clickable { onSelected(preset) }
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(10.dp)).background(palette.accent))
            Box(modifier = Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(10.dp)).background(palette.tertiary))
          }
          Text(preset.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
      }
    }
  }
}

@Composable
private fun SettingsSwitchRow(icon: String, iconColor: Color, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, logoProvider: String? = null, enabled: Boolean = true) {
  Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
    if (logoProvider != null) {
      Image(
        painter = painterResource(ratingProviderLogoRes(logoProvider)),
        contentDescription = title,
        modifier = Modifier.size(22.dp),
        contentScale = ContentScale.Fit,
      )
    } else {
      SettingsIcon(icon, iconColor)
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
      ),
    )
  }
}

@Composable
private fun SettingsChoiceRow(icon: String, iconColor: Color, title: String, subtitle: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
  var showSheet by rememberSaveable(title) { mutableStateOf(false) }
  SettingsNavRow(icon = icon, iconColor = iconColor, title = title, subtitle = subtitle, value = settingsOptionLabel(title, selected), onClick = { showSheet = true })
  if (showSheet) {
    SettingsChoiceSheet(
      title = title,
      options = options,
      selected = selected,
      onDismiss = { showSheet = false },
      onSelected = {
        onSelected(it)
        showSheet = false
      },
    )
  }
}

@Composable
private fun SettingsChoiceSheet(title: String, options: List<String>, selected: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.66f))
        .clickable(onClick = onDismiss),
      contentAlignment = Alignment.BottomCenter,
    ) {
      Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = false, onClick = {}),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
      ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
          if (title == "Continue Watching Style" || title == "Page Style") {
            options.chunked(2).forEach { rowOptions ->
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowOptions.forEach { option ->
                  val isSelected = selected == option
                  Column(
                    modifier = Modifier
                      .weight(1f)
                      .clip(RoundedCornerShape(20.dp))
                      .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                      .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                      .clickable { onSelected(option) }
                      .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    if (title == "Continue Watching Style") {
                      ContinueWatchingStyleSkeletonPreview(
                        style = runCatching { ContinueWatchingStyle.valueOf(option) }.getOrDefault(ContinueWatchingStyle.Glass),
                        selected = isSelected,
                      )
                    } else {
                      PageStyleSkeletonPreview(
                        style = runCatching { DetailPageStyle.valueOf(option) }.getOrDefault(DetailPageStyle.Classic),
                        selected = isSelected,
                      )
                    }
                    Text(settingsOptionLabel(title, option), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    settingsOptionDescription(title, option)?.let { Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall) }
                  }
                }
                if (rowOptions.size == 1) Spacer(modifier = Modifier.weight(1f))
              }
            }
          } else {
            options.forEach { option ->
              val isSelected = selected == option
              Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)).border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(18.dp)).clickable { onSelected(option) }.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(settingsOptionLabel(title, option), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                  settingsOptionDescription(title, option)?.let { Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall) }
                }
                if (isSelected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) else Box(modifier = Modifier.size(22.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f), CircleShape))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ContinueWatchingStyleSkeletonPreview(style: ContinueWatchingStyle, selected: Boolean) {
  val border = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
  val fill = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
  when (style) {
    ContinueWatchingStyle.Cinematic -> {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(88.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(fill)
          .border(1.dp, border, RoundedCornerShape(16.dp)),
      ) {
        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.58f).height(12.dp), radius = 8.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.32f).height(8.dp), radius = 8.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth().height(4.dp), radius = 999.dp)
        }
      }
    }
    ContinueWatchingStyle.Glass -> {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(88.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(fill)
          .border(1.dp, border, RoundedCornerShape(16.dp)),
      ) {
        SkeletonBlock(modifier = Modifier.fillMaxSize(), radius = 16.dp)
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(34.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(
              width = 1.dp,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
              shape = RectangleShape,
            ),
        )
        Column(
          modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.58f).height(7.dp), radius = 8.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.32f).height(5.dp), radius = 8.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth().height(3.dp), radius = 999.dp)
        }
      }
    }
    ContinueWatchingStyle.Ticket -> {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(fill)
          .border(1.dp, border, RoundedCornerShape(16.dp))
          .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SkeletonBlock(modifier = Modifier.weight(1f).height(40.dp), radius = 10.dp)
        SkeletonBlock(modifier = Modifier.width(32.dp).height(12.dp), radius = 8.dp)
      }
    }
    ContinueWatchingStyle.Mini -> {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(58.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(fill)
          .border(1.dp, border, RoundedCornerShape(14.dp))
          .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SkeletonBlock(modifier = Modifier.width(52.dp).fillMaxSize(), radius = 10.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.64f).height(10.dp), radius = 8.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.36f).height(8.dp), radius = 8.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth().height(4.dp), radius = 999.dp)
        }
      }
    }
    ContinueWatchingStyle.Stacked -> {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(fill)
          .border(1.dp, border, RoundedCornerShape(14.dp))
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(44.dp), radius = 10.dp)
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.62f).height(10.dp), radius = 8.dp)
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.40f).height(8.dp), radius = 8.dp)
      }
    }
  }
}

@Composable
private fun PageStyleSkeletonPreview(style: DetailPageStyle, selected: Boolean) {
  val border = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
  val fill = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(128.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(fill)
      .border(1.dp, border, RoundedCornerShape(16.dp))
      .padding(10.dp),
  ) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SkeletonBlock(modifier = Modifier.fillMaxWidth().weight(1f), radius = 12.dp)
      if (style == DetailPageStyle.Centered) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.54f).height(11.dp), radius = 8.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.72f).height(8.dp), radius = 8.dp)
        }
      } else {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.62f).height(11.dp), radius = 8.dp)
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.78f).height(8.dp), radius = 8.dp)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        SkeletonBlock(modifier = Modifier.weight(1f).height(8.dp), radius = 999.dp)
        SkeletonBlock(modifier = Modifier.weight(1f).height(8.dp), radius = 999.dp)
        SkeletonBlock(modifier = Modifier.weight(1f).height(8.dp), radius = 999.dp)
      }
    }
  }
}

private fun settingsOptionLabel(title: String, option: String): String = when {
  title == "Language" -> supportedAppLanguages[normalizeAppLanguage(option)] ?: "English"
  title == "Default Player" -> when (option) {
    "Auto" -> "Auto"
    "Media3" -> "ExoPlayer"
    "MPV" -> "libmpv"
    else -> option
  }
  title == "Default Audio" -> when (option) {
    "en" -> "English"
    "original" -> "Original / video default"
    "es" -> "Spanish"
    "fr" -> "French"
    "de" -> "German"
    "it" -> "Italian"
    "pt" -> "Portuguese"
    "ja" -> "Japanese"
    "ko" -> "Korean"
    "hi" -> "Hindi"
    else -> option
  }
  title == "Max File Size" && option == "0" -> "Unlimited"
  title == "Max File Size" -> "$option GB"
  title == "Preferred Stream Quality" && option == "2160p" -> "4K"
  title == "Preferred Stream Quality" && option == "Auto" -> "Best Available"
  title == "mpv Video Compatibility" -> when (option) {
    "HW+" -> "Recommended"
    "HW" -> "Device"
    "SW" -> "Safe mode"
    else -> option
  }
  title == "Playback Method" -> when (option) {
    "Local Server" -> "On this device"
    "Direct HTTP" -> "Direct links only"
    else -> option
  }
  title == "Startup Speed" -> when (option) {
    "default" -> "Balanced"
    "soft" -> "Gentler"
    "fast" -> "Faster"
    "ultra_fast" -> "Fastest"
    else -> option
  }
  title == "Appearance" -> when (option) {
    AppAppearance.System.name -> "Follow system"
    AppAppearance.Dark.name -> "Dark"
    AppAppearance.Light.name -> "Light"
    else -> option
  }
  title == "Theme" -> when (option) {
    AppThemePreset.Monochrome.name -> "Monochrome"
    AppThemePreset.Ocean.name -> "Ocean"
    AppThemePreset.Emerald.name -> "Emerald"
    AppThemePreset.Amber.name -> "Amber"
    AppThemePreset.Crimson.name -> "Crimson"
    AppThemePreset.Rose.name -> "Rose"
    AppThemePreset.Violet.name -> "Violet"
    AppThemePreset.White.name -> "White"
    else -> option
  }
  title == "Continue Watching Style" -> when (option) {
    ContinueWatchingStyle.Cinematic.name -> "Cinematic"
    ContinueWatchingStyle.Glass.name -> "Glass"
    ContinueWatchingStyle.Ticket.name -> "Card"
    ContinueWatchingStyle.Mini.name -> "Wide"
    ContinueWatchingStyle.Stacked.name -> "Poster"
    else -> option
  }
  title == "Page Style" -> when (option) {
    DetailPageStyle.Classic.name -> "Classic"
    DetailPageStyle.Centered.name -> "Centered"
    else -> option
  }
  else -> option
}

private fun settingsOptionDescription(title: String, option: String): String? = when (title) {
  "Language" -> when (normalizeAppLanguage(option)) {
    "en" -> "English"
    "es" -> "Español · Spanish"
    "fr" -> "Français · French"
    "it" -> "Italiano · Italian"
    "nl" -> "Nederlands · Dutch"
    else -> null
  }
  "Preferred Stream Quality" -> when (option) {
    "2160p" -> "Prefer 4K streams first."
    "1080p" -> "Prefer Full HD streams first."
    "720p" -> "Prefer HD streams first."
    else -> "Use StreamDek ranking without a fixed resolution target."
  }
  "Appearance" -> when (option) {
    AppAppearance.System.name -> "Follow the current device appearance."
    AppAppearance.Dark.name -> "Always use the dark app theme."
    AppAppearance.Light.name -> "Always use the light app theme."
    else -> null
  }
  "Theme" -> "Change the accent color used across supported controls and surfaces."
  "Max File Size" -> if (option == "0") "Do not filter streams by file size." else "Exclude streams larger than ${option} GB."
  "Badge Position" -> "Place Fusion badges at the ${option.lowercase()} of each stream card."
  "Continue Watching Style" -> when (option) {
    ContinueWatchingStyle.Cinematic.name -> "Large cinematic hero card."
    ContinueWatchingStyle.Glass.name -> "Backdrop card with a frosted information panel."
    ContinueWatchingStyle.Ticket.name -> "TV-style landscape card."
    ContinueWatchingStyle.Mini.name -> "Compact wide card."
    ContinueWatchingStyle.Stacked.name -> "Artwork-first poster card."
    else -> null
  }
  "Page Style" -> "Use the $option layout on media pages."
  "Season Tabs" -> if (option == SeasonTabStyle.Posters.name) "Show image tabs for seasons." else "Use compact regular season tabs."
  else -> null
}

@Composable
private fun SettingsIcon(icon: String, iconColor: Color) {
  Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(iconColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
    Icon(settingsGlyph(icon), contentDescription = null, tint = iconColor)
  }
}

@Composable
private fun CatalogHomeLayoutSettings(
  uiState: AppUiState,
  onDefaultAppCatalogsEnabledChange: (Boolean) -> Unit,
  onHomeCatalogRowEnabledChange: (String, Boolean) -> Unit,
  onMoveHomeCatalogRow: (String, Int) -> Unit,
  dragScrollBy: suspend (Float) -> Float,
) {
  val density = LocalDensity.current
  val reorderThresholdPx = with(density) { 56.dp.toPx() }
  var localRows by remember { mutableStateOf(uiState.homeCatalogRows) }
  LaunchedEffect(uiState.homeCatalogRows) {
    localRows = uiState.homeCatalogRows
  }
  Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
    SettingsSection("Home Rows") {
      SettingsSwitchRow("GRID", Color(0xFF22C55E), "StreamDek Home Rows", "Show the rows that come with StreamDek.", uiState.defaultAppCatalogsEnabled, onDefaultAppCatalogsEnabledChange)
    }
    SettingsSection("Choose and Reorder Rows") {
      Text("Drag a row by its handle to place it exactly where you want. Use the switch to show or hide it.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
      localRows.forEachIndexed { index, row ->
        key(row.id) {
          if (index > 0) SettingsDivider()
          HomeCatalogRowItem(
            row = row,
            onEnabledChange = { enabled ->
              localRows = localRows.map { current -> if (current.id == row.id) current.copy(enabled = enabled) else current }
              onHomeCatalogRowEnabledChange(row.id, enabled)
            },
            onMove = { delta ->
              val from = localRows.indexOfFirst { it.id == row.id }
              val to = (from + delta).coerceIn(0, localRows.lastIndex)
              if (from >= 0 && from != to) {
                val reordered = localRows.toMutableList()
                val moved = reordered.removeAt(from)
                reordered.add(to, moved)
                localRows = reordered
                onMoveHomeCatalogRow(row.id, delta)
              }
            },
            reorderThresholdPx = reorderThresholdPx,
            dragScrollBy = dragScrollBy,
          )
        }
      }
    }
  }
}

@Composable
private fun HomeCatalogRowItem(
  row: HomeCatalogRow,
  onEnabledChange: (Boolean) -> Unit,
  onMove: (Int) -> Unit,
  reorderThresholdPx: Float,
  dragScrollBy: suspend (Float) -> Float,
) {
  val latestOnMove by rememberUpdatedState(onMove)
  var dragging by remember(row.id) { mutableStateOf(false) }
  var dragOffsetY by remember(row.id) { mutableFloatStateOf(0f) }
  var itemTopInRoot by remember(row.id) { mutableFloatStateOf(0f) }
  var autoScrollStep by remember(row.id) { mutableFloatStateOf(0f) }
  val density = LocalDensity.current
  val configuration = LocalConfiguration.current
  val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
  val topEdgePx = with(density) { 172.dp.toPx() }
  val bottomEdgePx = screenHeightPx - with(density) { 132.dp.toPx() }
  val maxAutoScrollStepPx = with(density) { 9.dp.toPx() }

  fun applyDragDelta(delta: Float) {
    dragOffsetY += delta
    while (dragOffsetY > reorderThresholdPx) {
      dragOffsetY -= reorderThresholdPx
      latestOnMove(1)
    }
    while (dragOffsetY < -reorderThresholdPx) {
      dragOffsetY += reorderThresholdPx
      latestOnMove(-1)
    }
  }

  // Auto-scroll the settings list while dragging near the screen edges. The scroll
  // amount is fed back into the drag offset so the row stays pinned under the
  // finger and keeps stepping through positions as the list moves.
  LaunchedEffect(dragging) {
    while (dragging) {
      val step = autoScrollStep
      if (step != 0f) {
        val consumed = dragScrollBy(step)
        if (consumed != 0f) applyDragDelta(consumed)
      }
      delay(16)
    }
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .onGloballyPositioned { itemTopInRoot = it.positionInRoot().y }
      .zIndex(if (dragging) 2f else 0f)
      .graphicsLayer {
        translationY = dragOffsetY
        scaleX = if (dragging) 1.02f else 1f
        scaleY = if (dragging) 1.02f else 1f
        shadowElevation = if (dragging) 18f else 0f
      }
      .clip(RoundedCornerShape(18.dp))
      .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f) else Color.Transparent)
      .padding(horizontal = 8.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.14f else 0.06f))
        .pointerInput(row.id) {
          detectDragGestures(
            onDragStart = {
              dragging = true
              dragOffsetY = 0f
              autoScrollStep = 0f
            },
            onDragEnd = {
              dragging = false
              dragOffsetY = 0f
              autoScrollStep = 0f
            },
            onDragCancel = {
              dragging = false
              dragOffsetY = 0f
              autoScrollStep = 0f
            },
            onDrag = { change, dragAmount ->
              change.consume()
              applyDragDelta(dragAmount.y)
              val pointerRootY = itemTopInRoot + dragOffsetY + change.position.y
              autoScrollStep = when {
                pointerRootY < topEdgePx -> -((topEdgePx - pointerRootY) / 6f).coerceIn(0f, maxAutoScrollStepPx)
                pointerRootY > bottomEdgePx -> ((pointerRootY - bottomEdgePx) / 6f).coerceIn(0f, maxAutoScrollStepPx)
                else -> 0f
              }
            },
          )
        },
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Rounded.DragHandle, contentDescription = "Drag to reorder", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.92f else 0.54f))
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      Text(if (dragging) "Moving - release to place" else row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.88f else 0.68f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Switch(checked = row.enabled, onCheckedChange = onEnabledChange)
  }
}

@Composable
private fun SettingsSegment(text: String, selected: Boolean, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .height(42.dp)
      .clip(RoundedCornerShape(999.dp))
      .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
      .border(1.dp, if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), RoundedCornerShape(999.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

private fun supportedHomeCatalogCount(addon: InstalledAddon): Int = addon.manifest.catalogs.count {
  when (it.type.trim().lowercase()) {
    "movie", "series" -> true
    else -> false
  }
}

private fun supportedHomeCatalogLabels(addon: InstalledAddon): List<String> = addon.manifest.catalogs.mapNotNull {
  when (it.type.trim().lowercase()) {
    "movie" -> "Movies"
    "series" -> "Series"
    else -> null
  }
}.distinct()

@Composable
private fun AddonMetric(value: String, label: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun AddonTag(text: String, active: Boolean = false) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(999.dp))
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 0.13f else 0.07f))
      .padding(horizontal = 10.dp, vertical = 6.dp),
  ) {
    Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 0.88f else 0.64f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
  }
}

@Composable
private fun AddonLogo(addon: InstalledAddon) {
  Box(
    modifier = Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    contentAlignment = Alignment.Center,
  ) {
    if (!addon.manifest.logo.isNullOrBlank()) {
      AsyncImage(model = addon.manifest.logo, contentDescription = addon.manifest.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
      Text(addon.manifest.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
  }
}

@Composable
private fun AddonIconButton(icon: ImageVector, contentDescription: String, tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f), enabled: Boolean = true, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .size(42.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.06f else 0.025f))
      .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = contentDescription, tint = tint.copy(alpha = if (enabled) 1f else 0.32f))
  }
}

private fun addonConfigureUrl(addon: InstalledAddon): String? {
  if (!addon.manifest.behaviorConfigurable) return null
  val rawBase = addon.manifest.baseUrl
    ?: addon.baseUrl
    ?: addon.manifest.transportUrl
    ?: addon.transportUrl
    ?: addon.manifest.manifestUrl
    ?: addon.manifest.url
    ?: addon.manifestUrl
    ?: addon.url
  val normalized = rawBase?.trim().orEmpty().trimEnd('/')
  if (normalized.isBlank()) return null
  return if (normalized.endsWith("/manifest.json", ignoreCase = true)) {
    normalized.removeSuffix("/manifest.json") + "/configure"
  } else {
    "$normalized/configure"
  }
}

@Composable
private fun AddonServiceCard(
  addon: InstalledAddon,
  index: Int,
  total: Int,
  onRefreshAddons: () -> Unit,
  onToggleAddon: (InstalledAddon, Boolean) -> Unit,
  onUninstallAddon: (String) -> Unit,
  onMoveAddon: (String, Int) -> Unit,
  dragScrollBy: suspend (Float) -> Float = { 0f },
) {
  val context = LocalContext.current
  val configureUrl = remember(addon.id, addon.manifest.behaviorConfigurable, addon.manifest.baseUrl, addon.baseUrl, addon.manifest.manifestUrl, addon.manifest.url, addon.url, addon.manifest.transportUrl, addon.transportUrl) { addonConfigureUrl(addon) }
  var expanded by rememberSaveable(addon.id) { mutableStateOf(false) }
  var showDetails by remember { mutableStateOf(false) }
  var displayNameVersion by remember { mutableStateOf(0) }
  val displayName = remember(addon.id, addon.manifest.name, displayNameVersion) { DisplayNameOverrides.resolve(addon.id, addon.manifest.name) }
  if (showDetails) AddonDetailsDialog(addon, displayName, { displayNameVersion++ }, { showDetails = false })

  val latestOnMove by rememberUpdatedState { delta: Int -> onMoveAddon(addon.id, delta) }
  var dragging by remember(addon.id) { mutableStateOf(false) }
  var dragOffsetY by remember(addon.id) { mutableFloatStateOf(0f) }
  var itemTopInRoot by remember(addon.id) { mutableFloatStateOf(0f) }
  var autoScrollStep by remember(addon.id) { mutableFloatStateOf(0f) }
  val density = LocalDensity.current
  val configuration = LocalConfiguration.current
  val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
  val topEdgePx = with(density) { 172.dp.toPx() }
  val bottomEdgePx = screenHeightPx - with(density) { 132.dp.toPx() }
  val maxAutoScrollStepPx = with(density) { 9.dp.toPx() }
  val reorderThresholdPx = with(density) { 74.dp.toPx() }

  fun applyDragDelta(delta: Float) {
    dragOffsetY += delta
    while (dragOffsetY > reorderThresholdPx) {
      dragOffsetY -= reorderThresholdPx
      latestOnMove(1)
    }
    while (dragOffsetY < -reorderThresholdPx) {
      dragOffsetY += reorderThresholdPx
      latestOnMove(-1)
    }
  }

  LaunchedEffect(dragging) {
    while (dragging) {
      val step = autoScrollStep
      if (step != 0f) {
        val consumed = dragScrollBy(step)
        if (consumed != 0f) applyDragDelta(consumed)
      }
      delay(16)
    }
  }

  Surface(
    modifier = Modifier
      .onGloballyPositioned { itemTopInRoot = it.positionInRoot().y }
      .zIndex(if (dragging) 2f else 0f)
      .graphicsLayer {
        translationY = dragOffsetY
        scaleX = if (dragging) 1.02f else 1f
        scaleY = if (dragging) 1.02f else 1f
        shadowElevation = if (dragging) 18f else 0f
      },
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f)),
  ) {
    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier.pointerInput(addon.id) {
            detectDragGesturesAfterLongPress(
              onDragStart = {
                dragging = true
                dragOffsetY = 0f
                autoScrollStep = 0f
              },
              onDragEnd = {
                dragging = false
                dragOffsetY = 0f
                autoScrollStep = 0f
              },
              onDragCancel = {
                dragging = false
                dragOffsetY = 0f
                autoScrollStep = 0f
              },
              onDrag = { change, dragAmount ->
                change.consume()
                applyDragDelta(dragAmount.y)
                val pointerRootY = itemTopInRoot + dragOffsetY + change.position.y
                autoScrollStep = when {
                  pointerRootY < topEdgePx -> -((topEdgePx - pointerRootY) / 6f).coerceIn(0f, maxAutoScrollStepPx)
                  pointerRootY > bottomEdgePx -> ((pointerRootY - bottomEdgePx) / 6f).coerceIn(0f, maxAutoScrollStepPx)
                  else -> 0f
                }
              },
            )
          },
        ) {
          AddonLogo(addon)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            buildString {
              append(if (addon.enabled) "Active" else "Off")
              append("  •  ${addon.manifest.catalogs.size} Home rows")
              if (addon.manifest.behaviorConfigurable) append("  •  Configurable")
            },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Switch(checked = addon.enabled, onCheckedChange = { onToggleAddon(addon, it) })
        Icon(
          if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
          contentDescription = if (expanded) "Hide actions" else "Show actions",
          tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
        )
      }
      AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SettingsDivider()
          addon.manifest.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showDetails = true }) { Icon(Icons.Rounded.Info, contentDescription = "Add-on details") }
            if (configureUrl != null) IconButton(onClick = {
              runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(configureUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Icon(Icons.Rounded.Settings, contentDescription = "Configure add-on") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onMoveAddon(addon.id, -1) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
            IconButton(onClick = { onMoveAddon(addon.id, 1) }, enabled = index < total - 1) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
            IconButton(onClick = onRefreshAddons) { Icon(Icons.Rounded.Refresh, "Refresh") }
            IconButton(onClick = { onUninstallAddon(addon.id) }) { Icon(Icons.Rounded.Delete, "Remove", tint = Color(0xFFEF476F)) }
          }
        }
      }
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f))
    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
private fun AddonDetailsDialog(addon: InstalledAddon, displayName: String, onRenamed: () -> Unit, onDismiss: () -> Unit) {
  var nameField by remember(addon.id) { mutableStateOf(displayName) }
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
      Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          AddonLogo(addon)
          Column(modifier = Modifier.weight(1f)) {
            Text("Add-on details", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f))
            Text(addon.manifest.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
        }
        if (LocalAddonManager.isLocalAddonId(addon.id)) {
          Text(
            "This add-on is available while your phone is connected to the same network as the device providing it.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall,
          )
        }
        SettingsDivider()
        OutlinedTextField(
          value = nameField,
          onValueChange = { nameField = it },
          label = { Text("Display name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(onClick = {
            DisplayNameOverrides.set(addon.id, nameField.takeIf { it.isNotBlank() && it != addon.manifest.name })
            onRenamed()
          }, enabled = nameField.isNotBlank()) { Text("Save name") }
          if (DisplayNameOverrides.get(addon.id) != null) {
            TextButton(onClick = {
              DisplayNameOverrides.clear(addon.id)
              nameField = addon.manifest.name
              onRenamed()
            }) { Text("Reset to default") }
          }
        }
        SettingsDivider()
        DetailRow("Version", addon.manifest.version)
        addon.manifest.description?.takeIf { it.isNotBlank() }?.let { DetailRow("About", it) }
        DetailRow(
          "Setup",
          when {
            addon.manifest.configurationRequired -> "Finish setup before using"
            addon.manifest.behaviorConfigurable -> "Optional settings available"
            else -> "Ready to use"
          },
        )
        if (addon.manifest.catalogs.isNotEmpty()) {
          Text("Home rows (${addon.manifest.catalogs.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
          addon.manifest.catalogs.forEach { catalog ->
            Text("• ${catalog.name.ifBlank { "Untitled row" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f))
          }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
      }
    }
  }
}

@Composable
private fun SubtitleSourcesSettings(ownerKey: String) {
  val context = LocalContext.current
  var sourceUrl by rememberSaveable { mutableStateOf("") }
  var sources by remember(ownerKey) { mutableStateOf(UserSubtitleSourceStore.load(context, ownerKey)) }
  var message by remember { mutableStateOf<String?>(null) }

  fun refreshSources() {
    sources = UserSubtitleSourceStore.load(context, ownerKey)
  }

  SettingsSection("Subtitle Sources") {
    SettingsStaticRow("SUB", Color(0xFFA78BFA), "OpenSubtitles", "Built in and always available.")
    if (sources.isNotEmpty()) SettingsDivider()
    sources.forEachIndexed { index, source ->
      if (index > 0) SettingsDivider()
      Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(source.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
          Text("Included in subtitle searches", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall)
        }
        Switch(
          checked = source.enabled,
          onCheckedChange = { enabled ->
            UserSubtitleSourceStore.setEnabled(context, ownerKey, source.id, enabled)
            refreshSources()
          },
        )
        IconButton(onClick = {
          UserSubtitleSourceStore.remove(context, ownerKey, source.id)
          refreshSources()
          message = "Subtitle source removed."
        }) {
          Icon(Icons.Rounded.Delete, contentDescription = "Remove subtitle source", tint = MaterialTheme.colorScheme.error)
        }
      }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text("Add your own", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    Text("Paste the link supplied by your subtitle provider. StreamDek will search it alongside the built-in source.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f), style = MaterialTheme.typography.bodySmall)
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
      value = sourceUrl,
      onValueChange = { sourceUrl = it; message = null },
      modifier = Modifier.fillMaxWidth(),
      placeholder = { InputGuideText("Paste subtitle add-on link") },
      leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
      singleLine = true,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Button(
      onClick = {
        UserSubtitleSourceStore.add(context, ownerKey, sourceUrl)
          .onSuccess {
            sourceUrl = ""
            message = "Subtitle source added."
            refreshSources()
          }
          .onFailure { message = it.message ?: "Could not add that subtitle source." }
      },
      enabled = sourceUrl.isNotBlank(),
      modifier = Modifier.fillMaxWidth(),
    ) { Text("Add Subtitle Source", fontWeight = FontWeight.Bold) }
    message?.let { Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall) }
  }
}

@Composable
private fun PluginsSettingsSummary() {
  val scope = rememberCoroutineScope()
  var repositoryUrl by rememberSaveable { mutableStateOf("") }
  var pluginState by remember { mutableStateOf(StreamDekPlugins.manager.state) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var expandedPluginUrl by rememberSaveable { mutableStateOf<String?>(null) }
  var detailsRepoUrl by rememberSaveable { mutableStateOf<String?>(null) }
  var displayNameVersion by remember { mutableStateOf(0) }

  fun syncState() {
    pluginState = StreamDekPlugins.manager.state
  }

  detailsRepoUrl?.let { url ->
    pluginState.repos.firstOrNull { it.url == url }?.let { repository ->
      PluginRepoDetailsDialog(
        repository = repository,
        providers = pluginState.providers.filter { it.repoUrl == url },
        onRenamed = { displayNameVersion++ },
        onDismiss = { detailsRepoUrl = null },
      )
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    SettingsSection("Plugins") {
      SettingsSwitchRow(
        "JS",
        Color(0xFFF59E0B),
        "Enable Plugins",
        "Let installed plugins add more places to find streams.",
        pluginState.enabled,
        onCheckedChange = {
          StreamDekPlugins.manager.enable(it)
          syncState()
        },
      )
    }

    SettingsSection("Add a Plugin Collection") {
      OutlinedTextField(
        value = repositoryUrl,
        onValueChange = { repositoryUrl = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { InputGuideText("Paste the plugin collection link") },
        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
        singleLine = true,
        enabled = !busy,
      )

      Button(
        onClick = {
          busy = true
          message = null
          scope.launch {
            StreamDekPlugins.manager.add(repositoryUrl)
              .onSuccess {
                repositoryUrl = ""
                message = "Plugin collection added."
              }
              .onFailure { message = it.message ?: "Could not add that plugin collection." }
            syncState()
            busy = false
          }
        },
        enabled = repositoryUrl.isNotBlank() && !busy,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (busy) "Working..." else "Add Collection")
      }
      message?.let {
        Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f), style = MaterialTheme.typography.bodySmall)
      }
    }

    SettingsSection("Installed Collections") {
      if (pluginState.repos.isEmpty()) {
        Text("No plugin collections added yet.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
      }
      pluginState.repos.forEachIndexed { index, repository ->
        if (index > 0) {
          Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)))
        }
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              val repoDisplayName = remember(repository.url, repository.name, displayNameVersion) { DisplayNameOverrides.resolve("plugin:" + repository.url, repository.name) }
              val sourceCount = pluginState.providers.count { it.repoUrl == repository.url }
              Text(repoDisplayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
              Text("$sourceCount ${if (sourceCount == 1) "source" else "sources"}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
            }
            Switch(
              checked = repository.enabled,
              onCheckedChange = { enabled ->
                StreamDekPlugins.manager.enableRepo(repository.url, enabled)
                syncState()
              },
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { detailsRepoUrl = repository.url }) { Text("Details") }
            TextButton(
              onClick = {
                busy = true
                scope.launch {
                  StreamDekPlugins.manager.refresh(repository.url)
                    .onSuccess { message = "Plugin collection updated." }
                    .onFailure { message = it.message ?: "Could not update this plugin collection." }
                  syncState()
                  busy = false
                }
              },
              enabled = !busy,
            ) { Text("Refresh") }
            TextButton(onClick = { StreamDekPlugins.manager.remove(repository.url); syncState() }) { Text("Remove") }
          }
        }
      }
    }

    SettingsSection("Sources by Plugin") {
      if (pluginState.repos.isEmpty()) {
        Text("Sources appear here after you add a plugin collection.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
      }
      pluginState.repos.forEachIndexed { pluginIndex, repository ->
        if (pluginIndex > 0) SettingsDivider()
        val pluginSources = pluginState.providers.filter { it.repoUrl == repository.url }
        val expanded = expandedPluginUrl == repository.url
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { expandedPluginUrl = if (expanded) null else repository.url }
            .padding(horizontal = 4.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val repoDisplayName = remember(repository.url, repository.name, displayNameVersion) { DisplayNameOverrides.resolve("plugin:" + repository.url, repository.name) }
            Text(repoDisplayName, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
              "${pluginSources.size} ${if (pluginSources.size == 1) "source" else "sources"}",
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
              style = MaterialTheme.typography.bodySmall,
            )
          }
          Icon(
            if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse ${repository.name}" else "Expand ${repository.name}",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
          )
        }
        AnimatedVisibility(visible = expanded) {
          Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (pluginSources.isEmpty()) {
              Text(
                "This plugin does not provide any compatible stream sources.",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall,
              )
            }
            pluginSources.forEachIndexed { sourceIndex, provider ->
              if (sourceIndex > 0) SettingsDivider()
              val supportedTypes = provider.types.map { type ->
                if (type.equals("movie", true)) "Movies"
                else if (type.equals("tv", true) || type.equals("series", true)) "Series"
                else "Streams"
              }.distinct().joinToString(" / ")
              SettingsSwitchRow(
                "P",
                Color(0xFF38BDF8),
                provider.name,
                "$supportedTypes - ${repository.name}",
                provider.enabled && repository.enabled,
                onCheckedChange = {
                  StreamDekPlugins.manager.enableProvider(provider.id, it)
                  syncState()
                },
                enabled = repository.enabled,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PluginRepoDetailsDialog(repository: PluginRepo, providers: List<PluginProvider>, onRenamed: () -> Unit, onDismiss: () -> Unit) {
  val overrideKey = "plugin:" + repository.url
  var nameField by remember(repository.url) { mutableStateOf(DisplayNameOverrides.resolve(overrideKey, repository.name)) }
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
      Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Plugin collection details", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f))
        Text(repository.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        SettingsDivider()
        OutlinedTextField(
          value = nameField,
          onValueChange = { nameField = it },
          label = { Text("Display name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(onClick = {
            DisplayNameOverrides.set(overrideKey, nameField.takeIf { it.isNotBlank() && it != repository.name })
            onRenamed()
          }, enabled = nameField.isNotBlank()) { Text("Save name") }
          if (DisplayNameOverrides.get(overrideKey) != null) {
            TextButton(onClick = {
              DisplayNameOverrides.clear(overrideKey)
              nameField = repository.name
              onRenamed()
            }) { Text("Reset to default") }
          }
        }
        SettingsDivider()

        DetailRow("Version", repository.version)
        repository.description?.takeIf { it.isNotBlank() }?.let { DetailRow("Description", it) }
        DetailRow("Status", if (repository.enabled) "On" else "Off")
        if (providers.isNotEmpty()) {
          Text("Sources (${providers.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
          providers.forEach { provider ->
            Text(
              "• ${provider.name} — ${provider.types.joinToString("/")}${if (!provider.enabled) " (off)" else ""}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
            )
          }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
      }
    }
  }
}

@Composable
private fun M3uPlaylistsSettingsSummary(
  uiState: AppUiState,
  onAddM3uPlaylist: (String, String) -> Unit,
  onRemoveM3uPlaylist: (String) -> Unit,
  onSetM3uPlaylistEnabled: (String, Boolean) -> Unit,
  onMoveM3uPlaylist: (String, Int) -> Unit,
  onRefreshM3uPlaylists: () -> Unit,
) {
  var playlistUrl by rememberSaveable { mutableStateOf("") }
  var playlistName by rememberSaveable { mutableStateOf("") }
  var showAddField by rememberSaveable { mutableStateOf(false) }
  var addingFromSourceCount by rememberSaveable { mutableIntStateOf(-1) }
  val sources = uiState.m3uSources.sortedBy { it.position }
  LaunchedEffect(sources.size, uiState.m3uLoading, addingFromSourceCount) {
    if (!uiState.m3uLoading && addingFromSourceCount >= 0 && sources.size > addingFromSourceCount) {
      playlistUrl = ""
      playlistName = ""
      showAddField = false
      addingFromSourceCount = -1
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Surface(
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
      shape = RoundedCornerShape(22.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
    ) {
      Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
          Text("+", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("M3U playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text("${(uiState.m3uChannels.size + uiState.m3uVodItems.size).formattedItemCount()} items (${uiState.m3uChannels.size.formattedItemCount()} live, ${uiState.m3uVodItems.size.formattedItemCount()} VOD) from ${sources.count { it.enabled }} playlist(s)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onRefreshM3uPlaylists, enabled = !uiState.m3uLoading) { Icon(Icons.Rounded.Refresh, "Refresh playlists") }
      }
    }

    if (uiState.m3uLoading || uiState.m3uStatusMessage != null || uiState.m3uErrorMessage != null) {
      Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (uiState.m3uLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
              uiState.m3uStatusMessage ?: if (uiState.m3uLoading) "Loading playlist…" else "Playlist loading finished",
              modifier = Modifier.weight(1f),
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.SemiBold,
            )
          }
          uiState.m3uProgress?.let { progress ->
            LinearProgressIndicator(
              progress = { progress.coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
              color = MaterialTheme.colorScheme.primary,
              trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            )
            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          }
          uiState.m3uErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
      }
    }

    AnimatedVisibility(visible = showAddField) {
      Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = playlistName, onValueChange = { playlistName = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { InputGuideText("Playlist name (optional)") }, singleLine = true, shape = RoundedCornerShape(16.dp),
          )
          OutlinedTextField(
            value = playlistUrl, onValueChange = { playlistUrl = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { InputGuideText("Paste an M3U playlist link") }, leadingIcon = { Icon(Icons.Rounded.Link, null) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
          )
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { showAddField = false; playlistUrl = ""; playlistName = ""; addingFromSourceCount = -1 }) { Text("Cancel") }
            Button(onClick = {
              playlistUrl.trim().takeIf { it.isNotEmpty() }?.let { addingFromSourceCount = sources.size; onAddM3uPlaylist(it, playlistName) }
            }, enabled = playlistUrl.isNotBlank() && !uiState.m3uLoading, shape = RoundedCornerShape(14.dp)) {
              Text(if (uiState.m3uLoading) "Adding…" else "Add")
            }
          }
        }
      }
    }
    if (!showAddField) {
      OutlinedButton(onClick = { showAddField = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Add a playlist", fontWeight = FontWeight.SemiBold)
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text("Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text("${sources.size}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f), style = MaterialTheme.typography.labelLarge)
    }
    if (sources.isEmpty()) {
      Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Text("No M3U playlists added yet.", modifier = Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), textAlign = TextAlign.Center)
      }
    } else {
      sources.forEachIndexed { index, source ->
        key(source.id) {
          Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f))) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(source.url, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = source.enabled, onCheckedChange = { onSetM3uPlaylistEnabled(source.id, it) })
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onMoveM3uPlaylist(source.id, -1) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                IconButton(onClick = { onMoveM3uPlaylist(source.id, 1) }, enabled = index < sources.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                IconButton(onClick = { onRemoveM3uPlaylist(source.id) }) { Icon(Icons.Rounded.Delete, "Remove", tint = Color(0xFFEF476F)) }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DownloadsSettingsSummary(uiState: AppUiState, onRefresh: () -> Unit, onRemove: (String) -> Unit, onPlay: (DownloadEntry) -> Unit) {
  LaunchedEffect(Unit) {
    while (true) {
      onRefresh()
      delay(2_000)
    }
  }
  val downloads = uiState.downloads.sortedByDescending { it.startTimeMs }
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    if (downloads.isEmpty()) {
      Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Text("No downloads yet. Downloads started from the player's Sources list appear here.", modifier = Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), textAlign = TextAlign.Center)
      }
    } else {
      downloads.forEach { download ->
        key(download.id) {
          Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f))) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Text(download.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    when (download.state) {
                      DownloadState.COMPLETED -> "Downloaded"
                      DownloadState.FAILED -> "Failed"
                      DownloadState.REMOVING -> "Removing…"
                      DownloadState.QUEUED -> "Queued"
                      DownloadState.PAUSED -> "Paused"
                      DownloadState.DOWNLOADING -> "Downloading ${download.percentDownloaded.toInt()}%"
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    style = MaterialTheme.typography.bodySmall,
                  )
                }
                if (download.state == DownloadState.COMPLETED) {
                  IconButton(onClick = { onPlay(download) }) { Icon(Icons.Rounded.PlayArrow, "Play download", tint = MaterialTheme.colorScheme.primary) }
                }
                IconButton(onClick = { onRemove(download.id) }) { Icon(Icons.Rounded.Delete, "Remove download", tint = Color(0xFFEF476F)) }
              }
              if (download.state == DownloadState.DOWNLOADING) {
                LinearProgressIndicator(
                  progress = { (download.percentDownloaded / 100f).coerceIn(0f, 1f) },
                  modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AddonsSettingsSummary(uiState: AppUiState, onRefreshAddons: () -> Unit, onInstallAddon: (String) -> Unit, onToggleAddon: (InstalledAddon, Boolean) -> Unit, onUninstallAddon: (String) -> Unit, onMoveAddon: (String, Int) -> Unit, dragScrollBy: suspend (Float) -> Float = { 0f }) {
  var addonUrl by rememberSaveable { mutableStateOf("") }
  var showAddField by rememberSaveable { mutableStateOf(false) }
  val addons = uiState.addons.sortedBy { it.position }
  val activeCount = addons.count { it.enabled }
  val catalogCount = addons.sumOf { supportedHomeCatalogCount(it) }
  val context = LocalContext.current
  val reorderHintPrefs = remember { context.getSharedPreferences(APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE) }
  var showReorderHint by remember { mutableStateOf(!reorderHintPrefs.getBoolean("addon_reorder_hint_dismissed", false)) }
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Surface(
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
      shape = RoundedCornerShape(22.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
    ) {
      Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
          Text("+", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("Your add-ons", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text("$activeCount of ${addons.size} active  •  $catalogCount Home rows", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onRefreshAddons, enabled = !uiState.addonsLoading) { Icon(Icons.Rounded.Refresh, "Refresh add-ons") }
      }
    }

    AnimatedVisibility(visible = showAddField) {
      Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = addonUrl, onValueChange = { addonUrl = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { InputGuideText("Paste add-on manifest link") }, leadingIcon = { Icon(Icons.Rounded.Link, null) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
          )
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { showAddField = false; addonUrl = "" }) { Text("Cancel") }
            Button(onClick = {
              addonUrl.trim().takeIf { it.isNotEmpty() }?.let { onInstallAddon(it); addonUrl = ""; showAddField = false }
            }, enabled = addonUrl.isNotBlank() && !uiState.addonsLoading, shape = RoundedCornerShape(14.dp)) {
              Text(if (uiState.addonsLoading) "Adding…" else "Add")
            }
          }
        }
      }
    }
    if (!showAddField) {
      OutlinedButton(onClick = { showAddField = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Add an add-on", fontWeight = FontWeight.SemiBold)
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text("Installed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text("${addons.size}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f), style = MaterialTheme.typography.labelLarge)
    }
    if (addons.isEmpty()) {
      Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Text("No add-ons installed yet.", modifier = Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), textAlign = TextAlign.Center)
      }
    } else {
      AnimatedVisibility(visible = showReorderHint) {
        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp)) {
          Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(
              "Long-press and drag a logo to reorder your add-ons.",
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
            IconButton(
              onClick = {
                showReorderHint = false
                reorderHintPrefs.edit().putBoolean("addon_reorder_hint_dismissed", true).apply()
              },
              modifier = Modifier.size(24.dp),
            ) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss hint", modifier = Modifier.size(16.dp)) }
          }
        }
      }
      addons.forEachIndexed { index, addon ->
        key(addon.id) {
          AddonServiceCard(addon, index, addons.size, onRefreshAddons, onToggleAddon, onUninstallAddon, onMoveAddon, dragScrollBy)
        }
      }
    }
  }
}

@Composable
private fun DebridServiceCard(providerLabel: String, account: DebridAccount, index: Int, total: Int, isActive: Boolean, onRemoveDebrid: (String) -> Unit, onMoveDebrid: (String, Int) -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f))) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(if (account.enabled) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(providerLabel, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          val resolverStatus = when {
            !account.enabled -> "Disabled"
            isActive -> "Used first"
            else -> "Backup"
          }
          val accountLabel = account.username?.let { "Signed in as $it" } ?: "Connected"
          Text("$resolverStatus | Order ${index + 1} | $accountLabel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onMoveDebrid(account.provider, -1) }, enabled = index > 0, shape = RoundedCornerShape(999.dp)) { Text("Up") }
        OutlinedButton(onClick = { onMoveDebrid(account.provider, 1) }, enabled = index < total - 1, shape = RoundedCornerShape(999.dp)) { Text("Down") }
        TextButton(onClick = { onRemoveDebrid(account.provider) }) { Text("Disconnect", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold) }
      }
    }
  }
}

@Composable
private fun DebridSettingsSummary(uiState: AppUiState, onRefreshDebrid: () -> Unit, onAddDebrid: (String, String) -> Unit, onRemoveDebrid: (String) -> Unit, onMoveDebrid: (String, Int) -> Unit) {
  val providerOptions = listOf("real-debrid" to "Real-Debrid", "alldebrid" to "AllDebrid", "premiumize" to "Premiumize", "torbox" to "TorBox", "debrid-link" to "Debrid-Link")
  var selectedProvider by rememberSaveable { mutableStateOf(providerOptions.first().first) }
  var apiKey by rememberSaveable(selectedProvider) { mutableStateOf("") }
  val accounts = uiState.debridAccounts.sortedBy { it.priority }
  Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    SettingsSection("Connect a Service") {
      Text("Service", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(providerOptions) { provider ->
          FilterChip(selected = provider.first == selectedProvider, onClick = { selectedProvider = provider.first }, label = { Text(provider.second) })
        }
      }
      OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { InputGuideText("Access key") })
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { val trimmed = apiKey.trim(); if (trimmed.isNotEmpty()) { onAddDebrid(selectedProvider, trimmed); apiKey = "" } }, enabled = apiKey.isNotBlank() && !uiState.debridLoading, shape = RoundedCornerShape(999.dp)) { Text("Connect") }
        OutlinedButton(onClick = onRefreshDebrid, shape = RoundedCornerShape(999.dp)) { Text("Refresh") }
      }
    }
    if (accounts.isEmpty()) {
      SettingsSection("Connected Services") { Text("No premium services connected.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f), style = MaterialTheme.typography.bodyMedium) }
    } else {
      val activeProvider = accounts.firstOrNull { it.enabled }?.provider
      accounts.forEachIndexed { index, account ->
        val label = providerOptions.firstOrNull { it.first == account.provider }?.second ?: account.provider
        DebridServiceCard(providerLabel = label, account = account, index = index, total = accounts.size, isActive = account.provider == activeProvider, onRemoveDebrid = onRemoveDebrid, onMoveDebrid = onMoveDebrid)
      }
    }
  }
}

private fun openExternalUrl(context: Context, url: String) {
  runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun TraktSettingsSummary(uiState: AppUiState, onRequestTraktDeviceCode: () -> Unit, onPollTraktAuthorization: () -> Unit, onDisconnectTrakt: () -> Unit, onRefreshTrakt: () -> Unit) {
  val context = LocalContext.current
  Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
    SettingsSection("Status") {
      SettingsStaticRow("T", Color(0xFFA78BFA), if (uiState.traktStatus.connected) (uiState.traktStatus.username ?: "Connected") else "Not connected", uiState.traktStatus.username?.let { "trakt.tv/$it" } ?: "Connect Trakt for the profile you are using.")
      SettingsDivider()
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onRefreshTrakt, shape = RoundedCornerShape(999.dp)) { Text("Refresh") }
        if (uiState.traktStatus.connected) TextButton(onClick = onDisconnectTrakt) { Text("Disconnect", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold) } else Button(onClick = onRequestTraktDeviceCode, enabled = !uiState.traktLoading, shape = RoundedCornerShape(999.dp)) { Text("Connect Trakt") }
      }
    }
    uiState.pendingDeviceCode?.let { code ->
      SettingsSection("Device Code") {
        SettingsStaticRow("T", Color(0xFFA78BFA), code.userCode, code.verificationUrl)
        Text("Open the Trakt verification page, enter the code above, then come back here and confirm.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(onClick = { openExternalUrl(context, code.verificationUrl) }, shape = RoundedCornerShape(999.dp)) { Text("Open Trakt") }
          Button(onClick = onPollTraktAuthorization, enabled = !uiState.traktLoading, shape = RoundedCornerShape(999.dp)) { Text("I Connected It") }
        }
      }
    }
    SettingsSection("What Stays Up to Date") {
      SettingsStaticRow("TV", Color(0xFFE5E7EB), "Your Trakt activity", "Your viewing progress, watchlist, recommendations, and ratings stay up to date with this profile.")
    }
  }
}

@Composable
private fun RatingsSettingsSummary(
  uiState: AppUiState,
  onRatingsEnabledChange: (Boolean) -> Unit,
  onExternalRatingsEnabledChange: (Boolean) -> Unit,
  onRatingProviderEnabledChange: (String, Boolean) -> Unit,
  onMdblistApiKeyChange: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
    SettingsSection("Ratings") {
      SettingsSwitchRow("RAT", Color(0xFFF5C518), "Show Ratings", "Show ratings on supported media pages and posters.", uiState.ratingsEnabled, onRatingsEnabledChange)
      SettingsDivider()
      SettingsSwitchRow("EXT", Color(0xFF22D3EE), "Show More Rating Services", "Show scores from services such as IMDb and Rotten Tomatoes.", uiState.externalRatingsEnabled, onExternalRatingsEnabledChange)
    }
    SettingsSection("MDBList Connection") {
      Text("MDBList Access Key", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
      Text("Get an access key from mdblist.com/preferences to show ratings from other services.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), style = MaterialTheme.typography.titleSmall)
      OutlinedTextField(value = uiState.mdblistApiKey, onValueChange = onMdblistApiKeyChange, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { InputGuideText("Paste access key") })
      Spacer(modifier = Modifier.height(10.dp))
      Button(onClick = { onMdblistApiKeyChange(uiState.mdblistApiKey) }, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(999.dp)) {
        Text("Save", fontWeight = FontWeight.Black)
      }
    }
    SettingsSection("Rating Services") {
      listOf(
        Triple("imdb", "IMDb", Color(0xFFF5C518)),
        Triple("tmdb", "TMDB", Color(0xFF22D3EE)),
        Triple("tomatoes", "Rotten Tomatoes", Color(0xFFEF4444)),
        Triple("metacritic", "Metacritic", Color(0xFF60A5FA)),
        Triple("trakt", "Trakt", Color(0xFFE11D48)),
        Triple("letterboxd", "Letterboxd", Color(0xFF22C55E)),
        Triple("audience", "Audience Score", Color(0xFFF97316)),
      ).forEachIndexed { index, provider ->
        if (index > 0) SettingsDivider()
        SettingsSwitchRow(
          provider.second.take(3).uppercase(),
          provider.third,
          provider.second,
          "Show this rating when it is available.",
          provider.first in uiState.enabledRatingProviders,
          { enabled -> onRatingProviderEnabledChange(provider.first, enabled) },
          logoProvider = provider.first,
        )
      }
    }
  }
}

@Composable
private fun AccountSettingsSummary(uiState: AppUiState, onSignOut: () -> Unit, onSignIn: () -> Unit, onRefreshSync: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
    SettingsSection("Account and Services") {
      SettingsStaticRow("@", Color(0xFFE5E7EB), "Account", uiState.session?.user?.email ?: "Signed out")
      if (uiState.session != null) {
        SettingsDivider()
        RefreshSyncRow(refreshing = uiState.syncRefreshing, onClick = onRefreshSync)
      }
    }
    if (uiState.session == null) {
      Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary), shape = RoundedCornerShape(999.dp)) {
        Text("Sign in or create account", fontWeight = FontWeight.SemiBold)
      }
    } else {
      Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC9352D), contentColor = Color.White), shape = RoundedCornerShape(999.dp)) {
        Text("Sign Out", fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
private fun RefreshSyncRow(refreshing: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(enabled = !refreshing, onClick = onClick).padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingsIcon("REF", MaterialTheme.colorScheme.primary)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("Refresh Now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
      Text("Bring this profile up to date across StreamDek, add-ons, premium services, and Trakt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), maxLines = 3)
    }
    Box(
      modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      if (refreshing) CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
      else Icon(Icons.Rounded.Refresh, contentDescription = "Refresh sync", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
  }
}

@Composable
private fun AppUpdatesSettingsSummary(uiState: AppUiState, onAutoCheckChange: (Boolean) -> Unit, onCheckNow: () -> Unit, onStartUpdate: () -> Unit) {
  val release = uiState.availableUpdate
  Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
    SettingsSection("App Updates") {
      SettingsSwitchRow("UP", Color(0xFF22C55E), "Check Automatically", "Let StreamDek notify you when a new version is available.", uiState.autoUpdateChecksEnabled, onAutoCheckChange)
      SettingsDivider()
      SettingsNavRow(
        "DL",
        Color(0xFF38BDF8),
        "Check for Updates",
        uiState.updateErrorMessage ?: uiState.updateStatusMessage ?: "Check the StreamDek update service now.",
        value = when { uiState.updateChecking -> "Checking"; release != null -> "Available"; else -> "Current" },
        onClick = onCheckNow,
      )
      SettingsDivider()
      SettingsStaticRow("APP", Color(0xFF94A3B8), "Current Version", BuildConfig.VERSION_NAME)
    }
    if (release != null) {
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f), RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(if (release.required) "Required update" else "Update available", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
          }
          Text("StreamDek ${release.versionName}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
          release.fileSizeBytes?.let { Text("Download size ${formatBytesLabel(it).removeSuffix(" used")}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), fontWeight = FontWeight.Bold) }
          if (release.releaseNotes.isNotBlank()) {
            Text("What is new", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f), fontWeight = FontWeight.Black)
            Text(release.releaseNotes, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyLarge)
          }
          uiState.updateProgress?.let { progress ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}% downloaded", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
              LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
              )
            }
          }
          uiState.updateStatusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
          uiState.updateErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
          Button(onClick = onStartUpdate, enabled = !uiState.updateDownloading, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary), shape = RoundedCornerShape(999.dp)) {
            if (uiState.updateDownloading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("Update Now", fontWeight = FontWeight.Black)
          }
        }
      }
    }
  }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
  uiState: AppUiState,
  onBack: () -> Unit,
  onLoadStreams: (EpisodeItem?) -> Unit,
  onDetailTabChange: (String) -> Unit,
  onPlayStream: (AddonStream, EpisodeItem?) -> Unit,
  onPlayEpisodeStream: (AddonStream, EpisodeItem?) -> Unit,
  onClearPlayerReturnTarget: () -> Unit,
  onPlayBestStream: (EpisodeItem?) -> Unit,
  onLoadSeason: (String, Int) -> Unit,
  onToggleWatchlist: (MediaItem) -> Unit,
  onToggleFavourite: (MediaItem) -> Unit,
  onOpenPerson: (CastMember) -> Unit,
  onClosePerson: () -> Unit,
  onOpenRelated: (MediaItem) -> Unit,
  onDownloadStream: (AddonStream, String) -> Unit = { _, _ -> },
  isStreamDownloadEligible: (AddonStream) -> Boolean = { false },
) {
  val detail = uiState.detail
  if (uiState.detailLoading || detail == null) {
    DetailSkeletonScene(style = uiState.detailPageStyle)
    return
  }
  // Live channels have no synopsis worth reading — open straight on the sources list.
  val defaultTab = when {
    uiState.detailIsLive && uiState.showStreamsList -> DetailTab.Streams.name
    detail.type == "tv" && detail.seasons.isNotEmpty() -> DetailTab.Episodes.name
    else -> DetailTab.About.name
  }
  var selectedTab by rememberSaveable(detail.id) { mutableStateOf(uiState.detailSelectedTab ?: defaultTab) }
  LaunchedEffect(detail.id) {
    val restoredTab = uiState.detailSelectedTab ?: defaultTab
    selectedTab = restoredTab
    onDetailTabChange(restoredTab)
  }
  val selectedEpisode = uiState.selectedEpisode
  var episodePageId by rememberSaveable(detail.id, "episodePage") { mutableStateOf(uiState.playerReturnEpisodeId) }
  val context = LocalContext.current
  val watchedEpisodeStore = remember(context) { WatchedEpisodeStore(context.applicationContext) }
  val watchedMovieStore = remember(context) { WatchedMovieStore(context.applicationContext) }
  val watchedOwnerKey = remember(uiState.session?.user?.uid, uiState.activeProfileId) { watchedOwnerKey(uiState.session, uiState.activeProfileId) }
  var watchedEpisodeIds by rememberSaveable(detail.id, watchedOwnerKey) { mutableStateOf(watchedEpisodeStore.load(watchedOwnerKey, detail.id)) }
  LaunchedEffect(detail.id, watchedOwnerKey, uiState.watchedEpisodeRevision) {
    watchedEpisodeIds = watchedEpisodeStore.load(watchedOwnerKey, detail.id)
  }
  val detailScope = rememberCoroutineScope()
  fun persistWatchedEpisodeIds(updated: List<String>) {
    watchedEpisodeIds = updated.distinct()
    watchedEpisodeStore.save(watchedOwnerKey, detail.id, watchedEpisodeIds)
  }
  var watchedMovieIds by rememberSaveable(watchedOwnerKey) { mutableStateOf(watchedMovieStore.load(watchedOwnerKey)) }
  val movieWatched = detail.type == "movie" && detail.id in watchedMovieIds
  fun persistMovieWatched(watched: Boolean) {
    val updated = if (watched) (watchedMovieIds + detail.id).distinct() else watchedMovieIds.filterNot { it == detail.id }
    watchedMovieIds = updated
    watchedMovieStore.save(watchedOwnerKey, updated)
    if (watched) {
      val session = uiState.session
      val profileId = uiState.activeProfileId
      if (session != null && profileId != null && uiState.traktStatus.connected) {
        val apiClient = StreamDekApiClient()
        detailScope.launch { apiClient.syncWatchedMovie(session, profileId, detail) }
      }
    }
  }
  val episodePage = episodePageId?.let { id -> uiState.selectedSeasonEpisodes.firstOrNull { it.id == id } ?: selectedEpisode?.takeIf { it.id == id } }
  val watchlistItem = MediaItem(
    id = detail.id,
    type = detail.type,
    title = detail.title,
    year = detail.year,
    poster = detail.poster,
    backdrop = detail.backdrop,
    rating = detail.rating,
    description = detail.description,
  )
  val favouriteItem = uiState.detailFallbackItem?.takeIf { it.id == detail.id }?.copy(
    title = detail.title,
    poster = detail.poster ?: uiState.detailFallbackItem.poster,
    backdrop = detail.backdrop ?: uiState.detailFallbackItem.backdrop,
  ) ?: watchlistItem
  val inWatchlist = uiState.mergedWatchlist.any { it.id == detail.id && it.type == detail.type }
  val isFavouriteChannel = uiState.favouriteChannels.any { it.id == detail.id }
  val proxyStatus = if (uiState.detailIsLive) {
    uiState.availableStreams.firstOrNull()?.let { stream ->
      stream.isLikelyProxied(uiState.addons.firstOrNull { it.id == stream.addonId })
    }
  } else {
    null
  }
  val metadataLine = detail.genres
    .takeIf { it.isNotEmpty() }
    ?.take(4)
    ?.joinToString(" \u00B7 ")
    ?: listOfNotNull(
      detail.type.replaceFirstChar(Char::uppercase),
      detail.year,
      detail.runtimeMinutes?.let { "${it}m" },
      detail.seasonsCount?.takeIf { detail.type == "tv" }?.let { "$it seasons" },
    ).joinToString(" \u00B7 ")
  val backdrop = detail.backdrop ?: detail.poster
  val streamCount = uiState.availableStreams.size
  val isUnreleasedMovie = detail.type == "movie" && isFutureReleaseDate(detail.releaseDate)
  val resumeMemory = remember(detail.id, detail.type, uiState.localResumeEntries) {
    val mediaType = if (detail.type == "series") "tv" else detail.type
    uiState.localResumeEntries
      .filter { it.mediaId == detail.id && it.mediaType == mediaType && !it.isLive && it.progressPercent in 3.0..94.9 }
      .maxByOrNull { it.updatedAt }
  }
  val primaryPlayLabel = when {
    isUnreleasedMovie -> "Unreleased"
    // Resume state comes from the local store, so surface it instantly — stream
    // discovery keeps running in the background and never blocks this label.
    resumeMemory != null -> buildString {
      append("Continue")
      val seasonNumber = resumeMemory.seasonNumber
      val episodeNumber = resumeMemory.episodeNumber
      if (seasonNumber != null && episodeNumber != null) append(" S$seasonNumber-E$episodeNumber")
      resumePositionLabel(resumeMemory)?.let { append(" from $it") }
    }
    // Streams publish progressively — the button is ready as soon as the first
    // source lands, while remaining sources keep loading in the background.
    uiState.streamLoading && uiState.availableStreams.isEmpty() -> "Loading..."
    else -> "Play"
  }
  // Live channels carry no synopsis — leave it blank so the hero omits the block
  // entirely rather than showing a placeholder.
  val overview = if (uiState.detailIsLive) "" else detail.description.ifBlank { "No synopsis available." }
  var overviewExpanded by rememberSaveable(detail.id, "overview") { mutableStateOf(false) }
  var trailerPopupUrl by rememberSaveable(detail.id, "trailer") { mutableStateOf<String?>(null) }
  LaunchedEffect(episodePageId) {
    if (episodePageId != null) trailerPopupUrl = null
  }
  val listState = rememberLazyListState()
  val detailHazeState = rememberHazeState()
  LaunchedEffect(selectedTab) {
    if (selectedTab == DetailTab.Streams.name && uiState.showStreamsList) {
      delay(16)
      listState.animateScrollToItem(1)
    }
  }
  val lightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    if (uiState.vividAmbient) {
      AsyncImage(
        model = backdrop,
        contentDescription = null,
        modifier = Modifier
          .fillMaxSize()
          .blur(34.dp)
          .graphicsLayer { alpha = if (lightMode) 0.42f else 0.32f },
        contentScale = ContentScale.Crop,
      )
      val tint = uiState.ambientTintPercent
      Box(
        modifier = Modifier.fillMaxSize().background(
          MaterialTheme.colorScheme.background.copy(alpha = ambientTintAlpha(if (lightMode) 0.46f else 0.36f, tint)),
        ),
      )
      Box(
        modifier = Modifier.fillMaxSize().background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              0.00f to MaterialTheme.colorScheme.background.copy(alpha = ambientTintAlpha(if (lightMode) 0.10f else 0.11f, tint)),
              0.34f to MaterialTheme.colorScheme.background.copy(alpha = ambientTintAlpha(if (lightMode) 0.28f else 0.44f, tint)),
              0.68f to MaterialTheme.colorScheme.background.copy(alpha = ambientTintAlpha(if (lightMode) 0.58f else 0.552f, tint)),
              1.00f to MaterialTheme.colorScheme.background.copy(alpha = ambientTintAlpha(1f, tint).coerceAtLeast(0.62f)),
            ),
          ),
        ),
      )
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 132.dp),
      verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
      item {
        DetailHero(
          detail = detail,
          backdrop = backdrop,
          metadataLine = metadataLine,
          style = uiState.detailPageStyle,
          scrollOffset = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0 },
          autoPlayTrailer = uiState.heroTrailerAutoplay && episodePage == null && !uiState.detailIsLive,
          trailerResolution = uiState.heroTrailerResolution,
          hazeState = detailHazeState,
          streamCount = streamCount,
          streamLoading = uiState.streamLoading,
          selectedTab = selectedTab,
          showEpisodes = detail.type == "tv" && !uiState.detailIsLive,
          inWatchlist = inWatchlist,
          showMediaActions = !uiState.detailIsLive,
          showFavouriteAction = uiState.detailIsLive,
          isFavourite = isFavouriteChannel,
          proxyStatus = proxyStatus,
          ratingsEnabled = uiState.ratingsEnabled,
          externalRatingsEnabled = uiState.externalRatingsEnabled,
          enabledRatingProviders = uiState.enabledRatingProviders,
          overview = overview,
          overviewExpanded = overviewExpanded,
          onOverviewExpandedChange = { overviewExpanded = it },
          showStreamsList = uiState.showStreamsList && !isUnreleasedMovie,
          primaryPlayLabel = primaryPlayLabel,
          showWatchedAction = detail.type == "movie" && !uiState.detailIsLive,
          watched = movieWatched,
          onPlay = { onPlayBestStream(selectedEpisode) },
          onTrailer = { if (!detail.trailerUrl.isNullOrBlank()) trailerPopupUrl = detail.trailerUrl },
          onAbout = { selectedTab = DetailTab.About.name; onDetailTabChange(DetailTab.About.name) },
          onStreams = {
            if (uiState.showStreamsList && (detail.type != "tv" || uiState.detailIsLive)) {
              selectedTab = DetailTab.Streams.name
              onDetailTabChange(DetailTab.Streams.name)
            }
          },
          onEpisodes = { selectedTab = DetailTab.Episodes.name; onDetailTabChange(DetailTab.Episodes.name) },
          onSave = { onToggleWatchlist(watchlistItem) },
          onToggleFavourite = { onToggleFavourite(favouriteItem) },
          onToggleWatched = { persistMovieWatched(!movieWatched) },
        )
      }

      when (selectedTab) {
        DetailTab.About.name -> {
          if (!uiState.detailIsLive) {
            item { DetailFactsSection(detail = detail) }
          }
          item { DetailCastSection(cast = detail.cast, onOpenPerson = onOpenPerson) }
          item { DetailAvailableOnSection(detail = detail) }
          if (!uiState.detailIsLive) {
            item { DetailTraktCommentSection(detail = detail, uiState = uiState) }
          }
          item { DetailMoreLikeThisSection(items = detail.similarTitles, onOpen = onOpenRelated) }
        }
        DetailTab.Episodes.name -> {
          if (detail.type == "tv" && detail.seasons.isNotEmpty()) {
            item {
              Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  Text("Episodes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                  val selectedSeasonIds = uiState.selectedSeasonEpisodes.map { watchedEpisodeKey(detail.id, it.seasonNumber, it.episodeNumber) }
                  val fullSeasonWatched = selectedSeasonIds.isNotEmpty() && selectedSeasonIds.all { it in watchedEpisodeIds }
                  OutlinedButton(
                    onClick = {
                      persistWatchedEpisodeIds(
                        if (fullSeasonWatched) {
                          watchedEpisodeIds.filterNot { it in selectedSeasonIds }
                        } else {
                          (watchedEpisodeIds + selectedSeasonIds).distinct()
                        }
                      )
                    },
                    enabled = selectedSeasonIds.isNotEmpty(),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, if (fullSeasonWatched) Color(0xFF22C55E) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.34f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                  ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = if (fullSeasonWatched) Color(0xFF22C55E) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (fullSeasonWatched) "Season watched" else "Mark season", fontWeight = FontWeight.Bold)
                  }
                }
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                  SeasonSelector(
                    seasons = detail.seasons,
                    selectedSeasonNumber = uiState.selectedSeasonNumber,
                    style = uiState.seasonTabStyle,
                    fallbackPoster = detail.poster ?: detail.backdrop,
                    onSelect = { season -> onLoadSeason(detail.id, season.seasonNumber) },
                  )
                }
                if (uiState.seasonLoading) {
                  Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SeasonSelectorSkeleton(style = uiState.seasonTabStyle)
                  }
                } else {
                  LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    items(uiState.selectedSeasonEpisodes, key = { "episode-${it.id}" }) { episode ->
                      val watchedKey = watchedEpisodeKey(detail.id, episode.seasonNumber, episode.episodeNumber)
                      EpisodeViewportCard(
                        episode = episode,
                        watched = watchedKey in watchedEpisodeIds,
                        blurUnwatched = uiState.blurUnwatchedEpisodes,
                        onToggleWatched = {
                          persistWatchedEpisodeIds(if (watchedKey in watchedEpisodeIds) watchedEpisodeIds - watchedKey else watchedEpisodeIds + watchedKey)
                        },
                        onOpen = {
                          if (uiState.showStreamsList) {
                            episodePageId = episode.id
                            onLoadStreams(episode)
                          } else {
                            onPlayBestStream(episode)
                          }
                        },
                      )
                    }
                  }
                }
              }
            }
          }
        }
        DetailTab.Streams.name -> {
          if (uiState.showStreamsList) {
            item(key = "sources-anchor") { Spacer(modifier = Modifier.height(1.dp)) }
            item(key = "sources-content") {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text("Sources", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onLoadStreams(selectedEpisode) }, shape = RoundedCornerShape(999.dp)) {
                  Text(if (uiState.streamLoading) "Loading" else "Reload")
                }
              }
              StreamListContent(
                uiState = uiState,
                selectedEpisode = selectedEpisode,
                onPlayStream = onPlayStream,
                onDownloadStream = { stream -> onDownloadStream(stream, detail.title) },
                isStreamDownloadEligible = isStreamDownloadEligible,
                horizontalPadding = 24.dp,
              )
            }
            }
          }
        }
      }
    }

    GlassCircleButton(
      modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 26.dp, top = 18.dp),
      hazeState = detailHazeState,
      navigationHazeStyle = true,
      onClick = onBack,
    ) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
    }

    if (uiState.personLoading) {
      Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
      }
    }
    uiState.selectedPerson?.let { person ->
      PersonDetailDialog(
        person = person,
        onDismiss = onClosePerson,
        onOpenWork = { item ->
          onClosePerson()
          onOpenRelated(item)
        },
      )
    }
    trailerPopupUrl?.let { url ->
      TrailerDialog(
        title = detail.title,
        url = url,
        backdropUrl = detail.backdrop ?: detail.poster,
        maxHeight = uiState.heroTrailerResolution,
        onDismiss = { trailerPopupUrl = null },
        onOpenExternal = { openTrailer(context, url) },
      )
    }
    episodePage?.let { episode ->
      val watchedKey = watchedEpisodeKey(detail.id, episode.seasonNumber, episode.episodeNumber)
      EpisodeStreamsPage(
        detail = detail,
        episode = episode,
        uiState = uiState,
        watched = watchedKey in watchedEpisodeIds,
        onBack = {
          episodePageId = null
          onClearPlayerReturnTarget()
        },
        onReload = { onLoadStreams(episode) },
        onToggleWatched = {
          persistWatchedEpisodeIds(if (watchedKey in watchedEpisodeIds) watchedEpisodeIds - watchedKey else watchedEpisodeIds + watchedKey)
        },
        onPlayStream = onPlayEpisodeStream,
        onDownloadStream = onDownloadStream,
        isStreamDownloadEligible = isStreamDownloadEligible,
      )
    }
  }

}
private fun openTrailer(context: Context, trailerUrl: String?) {
  if (trailerUrl.isNullOrBlank()) return
  runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}

private fun isDirectTrailerUrl(url: String): Boolean {
  val lower = url.lowercase()
  return lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.endsWith(".webm") || lower.contains("/trailers/")
}

private fun youtubeTrailerKey(url: String): String? {
  val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
  val host = uri.host.orEmpty().lowercase()
  val segments = uri.pathSegments
  return when {
    "youtu.be" in host -> uri.lastPathSegment
    "youtube.com" in host -> when {
      uri.getQueryParameter("v") != null -> uri.getQueryParameter("v")
      segments.firstOrNull().equals("embed", ignoreCase = true) -> segments.getOrNull(1)
      segments.firstOrNull().equals("shorts", ignoreCase = true) -> segments.getOrNull(1)
      else -> segments.lastOrNull()
    }
    else -> null
  }?.substringBefore('?')?.substringBefore('&')?.ifBlank { null }
}

private fun vimeoTrailerKey(url: String): String? {
  val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
  val host = uri.host.orEmpty().lowercase()
  if (!host.contains("vimeo.com")) return null
  return uri.pathSegments.lastOrNull()?.ifBlank { null }
}

private fun trailerEmbedUrl(url: String, autoPlay: Boolean, muted: Boolean): String {
  val autoplayFlag = if (autoPlay) 1 else 0
  val muteFlag = if (muted) 1 else 0
  youtubeTrailerKey(url)?.let { key ->
    return "https://www.youtube.com/embed/$key?autoplay=$autoplayFlag&mute=$muteFlag&playsinline=1&controls=0&disablekb=1&fs=0&iv_load_policy=3&rel=0&modestbranding=1&enablejsapi=1&origin=https%3A%2F%2Fstreamdek.com"
  }
  vimeoTrailerKey(url)?.let { key ->
    return "https://player.vimeo.com/video/$key?autoplay=$autoplayFlag&muted=$muteFlag&title=0&byline=0&portrait=0"
  }
  return url
}

private fun trailerEmbedHtml(url: String, autoPlay: Boolean, muted: Boolean): String {
  val youtubeKey = youtubeTrailerKey(url)
  if (youtubeKey != null) {
    val autoplayFlag = if (autoPlay) 1 else 0
    val muteFlag = if (muted) 1 else 0
    return """
      <!doctype html>
      <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
          <style>
            html, body, #player, iframe {
              position: fixed !important;
              inset: 0 !important;
              width: 100vw !important;
              height: 100vh !important;
              margin: 0 !important;
              padding: 0 !important;
              border: 0 !important;
              overflow: hidden !important;
              background: #000 !important;
              pointer-events: none !important;
            }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script>
            let streamdekState = 'LOADING';
            let streamdekPlayer = null;
            function onYouTubeIframeAPIReady() {
              streamdekPlayer = new YT.Player('player', {
                videoId: '$youtubeKey',
                playerVars: {
                  autoplay: $autoplayFlag,
                  controls: 0,
                  disablekb: 1,
                  fs: 0,
                  iv_load_policy: 3,
                  rel: 0,
                  playsinline: 1,
                  modestbranding: 1,
                  origin: 'https://www.youtube.com'
                },
                events: {
                  onReady: function(event) {
                    if ($muteFlag === 1) event.target.mute(); else event.target.unMute();
                    if ($autoplayFlag === 1) event.target.playVideo();
                    streamdekState = 'READY';
                  },
                  onStateChange: function(event) {
                    if (event.data === YT.PlayerState.ENDED) streamdekState = 'ENDED';
                    else if (event.data === YT.PlayerState.PLAYING || event.data === YT.PlayerState.BUFFERING) streamdekState = 'READY';
                  },
                  onError: function() { streamdekState = 'ERROR'; }
                }
              });
            }
            window.streamdekPlayback = function() {
              if (streamdekState === 'READY' && streamdekPlayer) {
                return 'READY:' + (streamdekPlayer.getPlayerState() !== YT.PlayerState.PLAYING) + ':' + (streamdekPlayer.getCurrentTime() || 0);
              }
              return streamdekState;
            };
          </script>
          <script src="https://www.youtube.com/iframe_api"></script>
        </body>
      </html>
    """.trimIndent()
  }
  val embedUrl = trailerEmbedUrl(url, autoPlay = autoPlay, muted = muted)
  return """
    <html><head><meta name="viewport" content="width=device-width, initial-scale=1" />
    <style>html,body,iframe{position:fixed;inset:0;width:100vw;height:100vh;margin:0;border:0;overflow:hidden;background:#000}</style></head>
    <body><iframe src="$embedUrl" allow="autoplay; encrypted-media; picture-in-picture" referrerpolicy="origin"></iframe></body></html>
  """.trimIndent()
}
@Composable
private fun TrailerDialog(title: String, url: String, backdropUrl: String?, maxHeight: Int = 720, onDismiss: () -> Unit, onOpenExternal: () -> Unit) {
  var trailerReady by remember(url) { mutableStateOf(false) }
  var trailerFailed by remember(url) { mutableStateOf(false) }
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.94f),
      color = Color(0xFF050505),
      shape = RoundedCornerShape(22.dp),
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(title, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          TextButton(onClick = onOpenExternal) { Text("Open") }
          TextButton(onClick = onDismiss) { Text("Close") }
        }
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
          if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
              model = backdropUrl,
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
          }
          TrailerPlaybackView(
            url = url,
            modifier = Modifier.fillMaxSize(),
            autoPlay = true,
            muted = false,
            maxHeight = maxHeight,
            preferWebEmbed = false,
            onReadyChanged = { ready -> trailerReady = ready; if (ready) trailerFailed = false },
            onLoadFailed = { trailerFailed = true },
            onEnded = onDismiss,
          )
          if (!trailerReady && !trailerFailed) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
          }
          if (trailerFailed) {
            Column(
              modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text("Trailer could not be loaded.", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
              OutlinedButton(onClick = onOpenExternal, shape = RoundedCornerShape(999.dp)) { Text("Open Externally") }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun YoutubeLoginDialog(onDismiss: () -> Unit, onRetry: () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.66f).heightIn(max = 520.dp),
      color = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
      shape = RoundedCornerShape(20.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text("Sign in to YouTube", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
          Text(
            "This sign-in is used only for trailers. StreamDek never receives or stores your login details; YouTube keeps the session inside its secure web page.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodySmall,
          )
        }
        AndroidView(
          modifier = Modifier.fillMaxWidth().weight(1f),
          factory = { context ->
            WebView(context).apply {
              setBackgroundColor(android.graphics.Color.WHITE)
              webChromeClient = WebChromeClient()
              webViewClient = WebViewClient()
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              settings.userAgentString = WebSettings.getDefaultUserAgent(context)
              settings.cacheMode = WebSettings.LOAD_DEFAULT
              val cookieManager = android.webkit.CookieManager.getInstance()
              cookieManager.setAcceptCookie(true)
              cookieManager.setAcceptThirdPartyCookies(this, true)
              loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F")
            }
          },
          onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
          },
        )
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss) { Text("Cancel") }
          Button(onClick = onRetry, shape = RoundedCornerShape(999.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Retry trailer", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun TrailerPlaybackView(
  url: String,
  modifier: Modifier = Modifier,
  autoPlay: Boolean = true,
  muted: Boolean = true,
  maxHeight: Int = 720,
  preferWebEmbed: Boolean = false,
  onReadyChanged: (Boolean) -> Unit = {},
  onLoadFailed: () -> Unit = {},
  onEnded: () -> Unit = {},
) {
  val isYoutubeTrailer = youtubeTrailerKey(url) != null
  val isVimeoTrailer = vimeoTrailerKey(url) != null
  val context = LocalContext.current
  val youtubeSessionPrefs = remember(context) { context.getSharedPreferences("streamdek_youtube_trailers", Context.MODE_PRIVATE) }
  var youtubeLoginRequired by rememberSaveable(url) { mutableStateOf(false) }
  var youtubeLoginAttempted by rememberSaveable(url) { mutableStateOf(youtubeSessionPrefs.getBoolean("signed_in_session", false)) }
  var nativeRetryKey by rememberSaveable(url) { mutableIntStateOf(0) }
  var resolution by remember(url, maxHeight) { mutableStateOf<TrailerPlaybackResolution?>(null) }
  var resolved by remember(url, maxHeight) { mutableStateOf(false) }

  if (youtubeLoginRequired) {
    YoutubeLoginDialog(
      onDismiss = { youtubeLoginRequired = false; onReadyChanged(false); onLoadFailed() },
      onRetry = {
        android.webkit.CookieManager.getInstance().flush()
        youtubeSessionPrefs.edit().putBoolean("signed_in_session", true).apply()
        youtubeLoginRequired = false
        youtubeLoginAttempted = true
        nativeRetryKey += 1
        onReadyChanged(false)
      },
    )
  }

  if (!isYoutubeTrailer && isVimeoTrailer && preferWebEmbed) {
    LaunchedEffect(url, autoPlay, muted) { onReadyChanged(false) }
    TrailerWebView(
      url = url,
      modifier = modifier,
      autoPlay = autoPlay,
      muted = muted,
      onReadyChanged = onReadyChanged,
      onLoginRequired = {},
      onLoadFailed = onLoadFailed,
      onEnded = onEnded,
      controlledEmbed = false,
    )
    return
  }

  var nativePlaybackFailed by remember(url, maxHeight, nativeRetryKey) { mutableStateOf(false) }

  LaunchedEffect(url, maxHeight, nativeRetryKey) {
    onReadyChanged(false)
    resolved = false
    val youtubeCookies = if (isYoutubeTrailer) android.webkit.CookieManager.getInstance().getCookie("https://www.youtube.com") else null
    resolution = resolveTrailerPlaybackSource(url, maxHeight, youtubeCookies)
    resolved = true
    if (resolution?.youtubeLoginRequired == true && !youtubeLoginAttempted) youtubeLoginRequired = true
  }

  val source = resolution?.source
  // When native extraction fails (YouTube regularly changes its internal API and
  // bot-checks even signed-in requests), fall back to the official iframe embed —
  // it keeps playing no matter what happens to the extraction path.
  val useYoutubeWebFallback = isYoutubeTrailer && resolved && !youtubeLoginRequired &&
    (nativePlaybackFailed || source == null)
  if (useYoutubeWebFallback) {
    TrailerWebView(
      url = url,
      modifier = modifier,
      autoPlay = autoPlay,
      muted = muted,
      onReadyChanged = onReadyChanged,
      onLoginRequired = { if (!youtubeLoginAttempted) youtubeLoginRequired = true else onLoadFailed() },
      onLoadFailed = onLoadFailed,
      onEnded = onEnded,
      controlledEmbed = true,
    )
    return
  }
  if (source == null) {
    if (resolved && resolution?.youtubeLoginRequired != true && !isYoutubeTrailer) {
      LaunchedEffect(url, nativeRetryKey) { onLoadFailed() }
    }
    Box(modifier = modifier)
    return
  }
  Media3TextureTrailerPlayer(
    url = source.url,
    modifier = modifier,
    playWhenReady = autoPlay,
    muted = muted,
    audioUrl = source.audioUrl,
    requestHeaders = source.requestHeaders,
    maxHeight = source.height ?: maxHeight,
    onReady = { onReadyChanged(true) },
    onError = {
      onReadyChanged(false)
      if (isYoutubeTrailer) nativePlaybackFailed = true else onLoadFailed()
    },
    onEnded = onEnded,
  )
}

private val trailerViewportScript = """
  (function() {
    document.documentElement.style.setProperty('background', '#000', 'important');
    if (document.body) document.body.style.setProperty('background', '#000', 'important');
    let style = document.getElementById('streamdek-trailer-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'streamdek-trailer-style';
      style.textContent = `
        html, body, #player, #movie_player, .html5-video-player, .html5-video-container {
          position: fixed !important;
          inset: 0 !important;
          width: 100vw !important;
          height: 100vh !important;
          margin: 0 !important;
          padding: 0 !important;
          overflow: hidden !important;
          background: #000 !important;
        }
        video.html5-main-video {
          position: absolute !important;
          inset: 0 !important;
          width: 100% !important;
          height: 100% !important;
          object-fit: cover !important;
          transform: none !important;
        }
        .ytp-chrome-top, .ytp-chrome-bottom, .ytp-gradient-top, .ytp-gradient-bottom,
        .ytp-title, .ytp-watermark, .ytp-pause-overlay, .ytp-cued-thumbnail-overlay,
        .ytp-ce-element, .ytp-endscreen-content, .ytp-show-cards-title, .ytp-cards-button,
        .ytp-player-content, .ytp-spinner, .ytp-bezel {
          display: none !important;
          opacity: 0 !important;
          pointer-events: none !important;
        }
      `;
      (document.head || document.documentElement).appendChild(style);
    }
    return true;
  })();
""".trimIndent()

@Composable
private fun TrailerWebView(
  url: String,
  modifier: Modifier = Modifier,
  autoPlay: Boolean,
  muted: Boolean,
  onReadyChanged: (Boolean) -> Unit,
  onLoginRequired: () -> Unit,
  onLoadFailed: () -> Unit,
  onEnded: () -> Unit,
  controlledEmbed: Boolean,
) {
  val latestReadyChanged = rememberUpdatedState(onReadyChanged)
  val latestLoginRequired = rememberUpdatedState(onLoginRequired)
  val latestLoadFailed = rememberUpdatedState(onLoadFailed)
  val latestEnded = rememberUpdatedState(onEnded)
  AndroidView(
    modifier = modifier.background(Color.Transparent),
    factory = { context ->
      WebView(context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isClickable = false
        isFocusable = false
        setOnTouchListener { _, _ -> true }
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
          private fun inspectPlayback(view: WebView, loadingAttempt: Int) {
            if (!controlledEmbed) view.evaluateJavascript(trailerViewportScript, null)
            val playbackProbe = if (controlledEmbed) {
              "(function(){return window.streamdekPlayback ? window.streamdekPlayback() : 'LOADING';})()"
            } else {
              "(function(){const t=((document.body&&document.body.innerText)||'').toLowerCase();if(t.includes('sign in to confirm')||t.includes('not a bot')||t.includes('login required'))return 'LOGIN_REQUIRED';const v=document.querySelector('video');if(v&&v.ended)return 'ENDED';if(v&&v.readyState>=2)return 'READY:'+v.paused+':'+v.currentTime;if(t.includes('video unavailable')||t.includes('an error occurred'))return 'ERROR';return 'LOADING';})()"
            }
            view.evaluateJavascript(playbackProbe) { state ->
              android.util.Log.d("TrailerWebView", "playback=$state attempt=$loadingAttempt")
              when {
                state.contains("LOGIN_REQUIRED") -> latestLoginRequired.value()
                state.contains("ENDED") -> latestEnded.value()
                state.contains("READY:") -> {
                  latestReadyChanged.value(true)
                  view.postDelayed({ inspectPlayback(view, 0) }, 1_000)
                }
                state.contains("ERROR") -> latestLoadFailed.value()
                loadingAttempt < 15 -> view.postDelayed({ inspectPlayback(view, loadingAttempt + 1) }, 1_000)
                controlledEmbed && youtubeTrailerKey(url) != null -> latestLoginRequired.value()
                else -> latestLoadFailed.value()
              }
            }
          }

          override fun onPageFinished(view: WebView, pageUrl: String?) {
            super.onPageFinished(view, pageUrl)
            android.util.Log.d("TrailerWebView", "loaded url=$pageUrl title=${view.title}")
            if (!controlledEmbed) view.evaluateJavascript(trailerViewportScript, null)
            view.postDelayed({ inspectPlayback(view, 0) }, 500)
          }

          override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
              android.util.Log.w("TrailerWebView", "main-frame error=${error.errorCode} ${error.description}")
              latestLoadFailed.value()
            }
          }
        }
        settings.javaScriptEnabled = true
        settings.userAgentString = WebSettings.getDefaultUserAgent(context)
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = !autoPlay
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)
        val embedUrl = trailerEmbedUrl(url, autoPlay = autoPlay, muted = muted)
        tag = "${if (controlledEmbed) "controlled" else "direct"}:$embedUrl"
        if (controlledEmbed) {
          loadDataWithBaseURL("https://www.youtube.com/", trailerEmbedHtml(url, autoPlay, muted), "text/html", "UTF-8", null)
        } else {
          loadUrl(embedUrl, mapOf("Referer" to "https://streamdek.com/"))
        }
      }
    },
    update = { webView ->
      webView.settings.mediaPlaybackRequiresUserGesture = !autoPlay
      val embedUrl = trailerEmbedUrl(url, autoPlay = autoPlay, muted = muted)
      val pageKey = "${if (controlledEmbed) "controlled" else "direct"}:$embedUrl"
      if (webView.tag != pageKey) {
        webView.tag = pageKey
        if (controlledEmbed) {
          webView.loadDataWithBaseURL("https://www.youtube.com/", trailerEmbedHtml(url, autoPlay, muted), "text/html", "UTF-8", null)
        } else {
          webView.loadUrl(embedUrl, mapOf("Referer" to "https://streamdek.com/"))
        }
      }
    },
    onRelease = { webView ->
      webView.stopLoading()
      webView.loadUrl("about:blank")
      webView.destroy()
    },
  )
}
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun Media3TextureTrailerPlayer(
  url: String,
  modifier: Modifier = Modifier,
  playWhenReady: Boolean,
  muted: Boolean,
  audioUrl: String?,
  requestHeaders: Map<String, String>,
  maxHeight: Int,
  onReady: () -> Unit,
  onError: () -> Unit,
  onEnded: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val latestOnReady = rememberUpdatedState(onReady)
  val latestOnError = rememberUpdatedState(onError)
  val latestOnEnded = rememberUpdatedState(onEnded)
  var attachedContainer by remember(url) { mutableStateOf<TrailerTextureContainer?>(null) }

  val player = remember(url, audioUrl, requestHeaders, maxHeight) {
    val trackSelector = DefaultTrackSelector(context).apply {
      parameters = buildUponParameters()
        .setMaxVideoSize(Int.MAX_VALUE, maxHeight.coerceAtLeast(360))
        .build()
    }
    val loadControl = DefaultLoadControl.Builder()
      .setBufferDurationsMs(12_000, 45_000, 1_500, 4_000)
      .setPrioritizeTimeOverSizeThresholds(true)
      .build()
    val httpFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(requestHeaders)
    val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
    ExoPlayer.Builder(context).setTrackSelector(trackSelector).setLoadControl(loadControl).build().apply {
      val factory = ProgressiveMediaSource.Factory(dataSourceFactory)
      // YouTube HLS manifests come from manifest.googlevideo.com without a .m3u8
      // extension, so detect HLS explicitly — a progressive source cannot parse them.
      val looksLikeHls = url.contains(".m3u8", ignoreCase = true) ||
        url.contains("/hls_", ignoreCase = true) ||
        url.contains("api/manifest/hls", ignoreCase = true)
      when {
        !audioUrl.isNullOrBlank() -> setMediaSource(
          MergingMediaSource(
            factory.createMediaSource(ExoMediaItem.fromUri(url)),
            factory.createMediaSource(ExoMediaItem.fromUri(audioUrl)),
          ),
        )
        looksLikeHls -> setMediaSource(HlsMediaSource.Factory(dataSourceFactory).createMediaSource(ExoMediaItem.fromUri(url)))
        else -> setMediaSource(factory.createMediaSource(ExoMediaItem.fromUri(url)))
      }
      repeatMode = Player.REPEAT_MODE_OFF
      volume = if (muted) 0f else 1f
      prepare()
    }
  }

  DisposableEffect(player, lifecycleOwner) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) latestOnReady.value()
        if (playbackState == Player.STATE_ENDED) latestOnEnded.value()
      }
      override fun onVideoSizeChanged(videoSize: VideoSize) {
        attachedContainer?.setVideoSize(videoSize)
      }
      override fun onPlayerError(error: PlaybackException) {
        latestOnError.value()
      }
    }
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> { if (playWhenReady) player.play() }
        Lifecycle.Event.ON_STOP -> player.pause()
        else -> Unit
      }
    }
    player.addListener(listener)
    if (player.playbackState == Player.STATE_READY) latestOnReady.value()
    attachedContainer?.setVideoSize(player.videoSize)
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      player.removeListener(listener)
      player.release()
    }
  }

  LaunchedEffect(player, playWhenReady) {
    player.playWhenReady = playWhenReady
  }

  LaunchedEffect(player, muted) {
    player.volume = if (muted) 0f else 1f
  }

  AndroidView(
    modifier = modifier,
    factory = { viewContext ->
      TrailerTextureContainer(viewContext).also { container ->
        attachedContainer = container
        container.layoutParams = android.view.ViewGroup.LayoutParams(
          android.view.ViewGroup.LayoutParams.MATCH_PARENT,
          android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        container.attachPlayer(player)
        container.setVideoSize(player.videoSize)
      }
    },
    update = { view ->
      attachedContainer = view
      view.attachPlayer(player)
      view.setVideoSize(player.videoSize)
    },
    onRelease = { view ->
      if (attachedContainer === view) attachedContainer = null
      view.detachPlayer(player)
    },
  )
}

private class TrailerTextureContainer(context: Context) : FrameLayout(context) {
  private val textureView = TextureView(context)
  private val textureTransform = Matrix()
  private var videoAspectRatio = 16f / 9f
  private var attachedPlayer: ExoPlayer? = null

  init {
    clipChildren = false
    clipToPadding = false
    isFocusable = false
    isClickable = false
    addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    textureView.isFocusable = false
    textureView.isClickable = false
  }

  fun attachPlayer(player: ExoPlayer) {
    if (attachedPlayer === player) return
    attachedPlayer?.clearVideoTextureView(textureView)
    attachedPlayer = player
    player.setVideoTextureView(textureView)
  }

  fun detachPlayer(player: ExoPlayer) {
    if (attachedPlayer === player) {
      player.clearVideoTextureView(textureView)
      attachedPlayer = null
    }
  }

  private fun updateScaleTransform() {
    val w = width.toFloat()
    val h = height.toFloat()
    if (w <= 0f || h <= 0f || videoAspectRatio <= 0f) return
    val viewAR = w / h
    textureTransform.reset()
    if (viewAR > videoAspectRatio) {
      textureTransform.setScale(1f, viewAR / videoAspectRatio, w / 2f, h / 2f)
    } else {
      textureTransform.setScale(videoAspectRatio / viewAR, 1f, w / 2f, h / 2f)
    }
    textureView.setTransform(textureTransform)
  }

  fun setVideoSize(videoSize: VideoSize) {
    if (videoSize.width <= 0 || videoSize.height <= 0) return
    videoAspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
    updateScaleTransform()
  }

  override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
    super.onSizeChanged(w, h, oldW, oldH)
    updateScaleTransform()
  }
}

@Composable
private fun ClassicDetailHero(
  detail: MediaDetail,
  backdrop: String?,
  metadataLine: String,
  scrollOffset: () -> Int,
  autoPlayTrailer: Boolean,
  trailerResolution: Int,
  hazeState: HazeState,
  streamCount: Int,
  streamLoading: Boolean,
  primaryPlayLabel: String,
  selectedTab: String,
  showEpisodes: Boolean,
  showStreamsList: Boolean,
  inWatchlist: Boolean,
  showMediaActions: Boolean,
  showWatchedAction: Boolean,
  watched: Boolean,
  ratingsEnabled: Boolean,
  externalRatingsEnabled: Boolean,
  enabledRatingProviders: Set<String>,
  overview: String,
  overviewExpanded: Boolean,
  onOverviewExpandedChange: (Boolean) -> Unit,
  onPlay: () -> Unit,
  onTrailer: () -> Unit,
  onAbout: () -> Unit,
  onStreams: () -> Unit,
  onEpisodes: () -> Unit,
  onSave: () -> Unit,
  onToggleWatched: () -> Unit,
  showFavouriteAction: Boolean = false,
  isFavourite: Boolean = false,
  proxyStatus: Boolean? = null,
  onToggleFavourite: () -> Unit = {},
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val artworkHeight = (maxWidth * 1.18f).coerceIn(440.dp, 670.dp)
    val foreground = MaterialTheme.colorScheme.onBackground
    val background = MaterialTheme.colorScheme.background
    val lightDetail = background.luminance() > 0.5f

    val trailerControlContent = if (lightDetail) MaterialTheme.colorScheme.onSurface else Color.White
    var overviewCanExpand by remember(overview) { mutableStateOf(false) }
    var trailerPlaying by rememberSaveable(detail.id, "classicTrailer") { mutableStateOf(autoPlayTrailer && !detail.trailerUrl.isNullOrBlank()) }
    var trailerReady by remember(detail.id, detail.trailerUrl) { mutableStateOf(false) }
    var trailerFailed by remember(detail.id, detail.trailerUrl) { mutableStateOf(false) }
    var trailerMuted by rememberSaveable(detail.id, "classicTrailerMuted") { mutableStateOf(true) }
    var trailerPlaybackKey by rememberSaveable(detail.id, "classicTrailerKey") { mutableIntStateOf(0) }
    LaunchedEffect(autoPlayTrailer) {
      if (!autoPlayTrailer) {
        trailerPlaying = false
        trailerReady = false
        trailerFailed = false
      }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
      Box(modifier = Modifier.fillMaxWidth().height(artworkHeight).clip(RectangleShape)) {
        Box(modifier = Modifier.fillMaxSize()) {
          AsyncImage(
            model = backdrop,
            contentDescription = detail.title,
            modifier = Modifier.fillMaxSize().graphicsLayer {
              translationY = scrollOffset().toFloat() * 0.42f
              scaleX = 1.04f
              scaleY = 1.04f
            },
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
          )
          if (trailerPlaying && !detail.trailerUrl.isNullOrBlank()) {
            key(trailerPlaybackKey) {
              // Keep the platform video view out of parallax/blur layers. Transforming an
              // Android video surface while a LazyColumn scrolls can crash RenderThread on
              // some OnePlus/GPU combinations.
              TrailerPlaybackView(
                url = detail.trailerUrl,
                modifier = Modifier.fillMaxSize(),
                autoPlay = true,
                muted = trailerMuted,
                maxHeight = trailerResolution,
                onReadyChanged = { trailerReady = it },
                onLoadFailed = { trailerFailed = true; trailerPlaying = false },
                onEnded = { trailerPlaying = false; trailerReady = false },
              )
            }
          }
        }
        if (!lightDetail) {
        Box(
          modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
              colorStops = arrayOf(
                0.00f to Color.Black.copy(alpha = 0.04f),
                0.50f to Color.Transparent,
                0.76f to background.copy(alpha = 0.42f),
                0.93f to background.copy(alpha = 0.94f),
                1.00f to background,
              ),
            ),
          ),
        )
        }

        if (trailerPlaying) {
          if (!lightDetail) {
            Column(
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 90.dp)
                .fillMaxWidth()
                .height(180.dp),
            ) {
              Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Brush.verticalGradient(listOf(Color.Transparent, background))))
              Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Brush.verticalGradient(listOf(background, Color.Transparent))))
            }
          }
          Box(
            modifier = Modifier
              .fillMaxSize()
              .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { trailerMuted = !trailerMuted },
          )
        }
        if (trailerPlaying && !trailerReady && !trailerFailed) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.86f))
          }
        }
        if (autoPlayTrailer && !detail.trailerUrl.isNullOrBlank()) {
          Row(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FrostedGlassSurface(
              modifier = Modifier.size(48.dp),
              shape = CircleShape,
                // Sample the same page-level haze state as the back button so the
                // controls share its frosted backdrop-blur look.
                hazeStateOverride = hazeState,
                blurRadius = 68f,
                tintAlpha = if (lightDetail) 0.14f else 0.06f,
                borderAlpha = if (lightDetail) 0.10f else 0.08f,
                baseAlpha = if (lightDetail) 0.28f else 0.08f,
                fillColorOverride = if (lightDetail) null else Color.White,
                showEdgeGradient = false,
              ) {
              IconButton(
                onClick = {
                  if (trailerPlaying) {
                    trailerMuted = !trailerMuted
                  } else {
                    trailerFailed = false
                    trailerReady = false
                    trailerMuted = true
                    trailerPlaybackKey += 1
                    trailerPlaying = true
                  }
                },
              ) {
                Icon(
                  imageVector = if (!trailerPlaying) Icons.Rounded.Refresh else if (trailerMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                  contentDescription = if (!trailerPlaying) "Replay trailer" else if (trailerMuted) "Unmute trailer" else "Mute trailer",
                  tint = Color.White,
                )
              }
            }
            if (trailerPlaying) {
              FrostedGlassSurface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                // Sample the same page-level haze state as the back button so the
                // controls share its frosted backdrop-blur look.
                hazeStateOverride = hazeState,
                blurRadius = 68f,
                tintAlpha = if (lightDetail) 0.14f else 0.06f,
                borderAlpha = if (lightDetail) 0.10f else 0.08f,
                baseAlpha = if (lightDetail) 0.28f else 0.08f,
                fillColorOverride = if (lightDetail) null else Color.White,
                showEdgeGradient = false,
              ) {
                IconButton(
                  onClick = {
                    trailerPlaying = false
                    trailerReady = false
                  },
                ) {
                  Icon(Icons.Rounded.Close, contentDescription = "Stop trailer", tint = trailerControlContent)
                }
              }
            }
          }
        }
      }
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val overlap = 112.dp.roundToPx()
            layout(placeable.width, (placeable.height - overlap).coerceAtLeast(0)) {
              placeable.placeRelative(0, -overlap)
            }
          }
          .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(18.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          if (!detail.poster.isNullOrBlank()) {
            AsyncImage(
              model = detail.poster,
              contentDescription = "${detail.title} poster",
              modifier = Modifier.width(112.dp).height(168.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(18.dp)),
              contentScale = ContentScale.Crop,
            )
          }
          Column(
            // Light mode has no bottom fade on the hero, so anchor the title block to the
            // top of the overlapped row: the hero's bottom edge sits exactly 112dp below
            // the row top, letting us pin the logo just above the edge (with padding) and
            // start the tagline just below it.
            modifier = if (lightDetail) Modifier.weight(1f).align(Alignment.Top) else Modifier.weight(1f).padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(if (lightDetail) 8.dp else 9.dp),
          ) {
            if (!detail.titleLogo.isNullOrBlank()) {
              HeroTitleArtwork(
                url = detail.titleLogo,
                title = detail.title,
                modifier = if (lightDetail) Modifier.fillMaxWidth().height(106.dp).padding(bottom = 8.dp) else Modifier.fillMaxWidth().height(74.dp),
                alignment = if (lightDetail) Alignment.BottomStart else Alignment.CenterStart,
              )
            } else if (lightDetail) {
              Box(modifier = Modifier.fillMaxWidth().height(106.dp).padding(bottom = 8.dp), contentAlignment = Alignment.BottomStart) {
                Text(detail.title, color = foreground, style = MaterialTheme.typography.headlineMedium.copy(shadow = Shadow(Color.Black.copy(alpha = 0.48f), Offset(0f, 1.5f), 4f)), fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
              }
            } else {
              Text(detail.title, color = foreground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            detail.tagline?.takeIf { it.isNotBlank() }?.let {
              Text("\"$it\"", color = foreground.copy(alpha = 0.64f), style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (metadataLine.isNotBlank()) Text(metadataLine, color = foreground.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          DetailRatingBadges(detail, ratingsEnabled, externalRatingsEnabled, enabledRatingProviders)
        }
        if (proxyStatus != null) {
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LiveProxyStatusBadge(proxied = proxyStatus)
          }
        }
        StreamDekDetailActions(
          streamCount = streamCount, streamLoading = streamLoading, primaryPlayLabel = primaryPlayLabel, selectedTab = selectedTab,
          showEpisodes = showEpisodes, showStreamsList = showStreamsList, inWatchlist = inWatchlist, showMediaActions = showMediaActions,
          showWatchedAction = showWatchedAction, watched = watched, autoPlayTrailer = autoPlayTrailer && !detail.trailerUrl.isNullOrBlank(),
          onPlay = onPlay, hasTrailer = showMediaActions && !detail.trailerUrl.isNullOrBlank(), onTrailer = onTrailer, onAbout = onAbout,
          onStreams = onStreams, onEpisodes = onEpisodes, onSave = onSave, onToggleWatched = onToggleWatched,
          showFavouriteAction = showFavouriteAction, isFavourite = isFavourite, onToggleFavourite = onToggleFavourite,
        )
        if (overview.isNotBlank()) {
          Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
              overview,
              color = foreground.copy(alpha = 0.76f),
              style = MaterialTheme.typography.bodyMedium,
              lineHeight = 23.sp,
              maxLines = if (overviewExpanded) 8 else 4,
              overflow = TextOverflow.Ellipsis,
              onTextLayout = { if (!overviewExpanded) overviewCanExpand = it.hasVisualOverflow },
            )
            if (overviewCanExpand || overviewExpanded) {
              Text(if (overviewExpanded) "Show less" else "Show more", color = foreground.copy(alpha = 0.62f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOverviewExpandedChange(!overviewExpanded) })
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DetailHero(
  detail: MediaDetail,
  backdrop: String?,
  metadataLine: String,
  style: DetailPageStyle,
  scrollOffset: () -> Int,
  autoPlayTrailer: Boolean,
  trailerResolution: Int,
  hazeState: HazeState,
  streamCount: Int,
  streamLoading: Boolean,
  primaryPlayLabel: String,
  selectedTab: String,
  showEpisodes: Boolean,
  showStreamsList: Boolean,
  inWatchlist: Boolean,
  showMediaActions: Boolean,
  showWatchedAction: Boolean,
  watched: Boolean,
  ratingsEnabled: Boolean,
  externalRatingsEnabled: Boolean,
  enabledRatingProviders: Set<String>,
  overview: String,
  overviewExpanded: Boolean,
  onOverviewExpandedChange: (Boolean) -> Unit,
  onPlay: () -> Unit,
  onTrailer: () -> Unit,
  onAbout: () -> Unit,
  onStreams: () -> Unit,
  onEpisodes: () -> Unit,
  onSave: () -> Unit,
  onToggleWatched: () -> Unit,
  showFavouriteAction: Boolean = false,
  isFavourite: Boolean = false,
  proxyStatus: Boolean? = null,
  onToggleFavourite: () -> Unit = {},
) {
  if (style == DetailPageStyle.Classic) {
    ClassicDetailHero(
      detail, backdrop, metadataLine, scrollOffset, autoPlayTrailer, trailerResolution, hazeState, streamCount, streamLoading, primaryPlayLabel, selectedTab, showEpisodes, showStreamsList, inWatchlist, showMediaActions, showWatchedAction, watched, ratingsEnabled, externalRatingsEnabled, enabledRatingProviders, overview, overviewExpanded, onOverviewExpandedChange, onPlay, onTrailer, onAbout, onStreams, onEpisodes, onSave, onToggleWatched,
      showFavouriteAction = showFavouriteAction, isFavourite = isFavourite, proxyStatus = proxyStatus, onToggleFavourite = onToggleFavourite,
    )
    return
  }
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val heroHeight = ((maxWidth * if (style == DetailPageStyle.Centered) 1.36f else 1.30f).coerceIn(500.dp, 760.dp))
    val lightDetail = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val detailForeground = MaterialTheme.colorScheme.onBackground
    val trailerControlContent = if (lightDetail) MaterialTheme.colorScheme.onSurface else Color.White
    var overviewCanExpand by remember(overview) { mutableStateOf(false) }
    var trailerReady by remember(detail.id, detail.trailerUrl) { mutableStateOf(false) }
    var trailerFailed by remember(detail.id, detail.trailerUrl) { mutableStateOf(false) }
    var trailerPlaying by rememberSaveable(detail.id, detail.trailerUrl) { mutableStateOf(autoPlayTrailer && !detail.trailerUrl.isNullOrBlank()) }
    var trailerMuted by rememberSaveable(detail.id, detail.trailerUrl) { mutableStateOf(true) }
    var trailerPlaybackKey by rememberSaveable(detail.id, detail.trailerUrl) { mutableIntStateOf(0) }
    LaunchedEffect(autoPlayTrailer) {
      if (!autoPlayTrailer) {
        trailerPlaying = false
        trailerReady = false
        trailerFailed = false
      }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(heroHeight)
          .clip(RectangleShape),
      ) {
        AsyncImage(
          model = backdrop,
          contentDescription = detail.title,
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
              translationY = scrollOffset().toFloat() * 0.5f
              scaleX = 1.08f
              scaleY = 1.08f
            },
          contentScale = ContentScale.Crop,
          alignment = Alignment.Center,
        )
        androidx.compose.animation.AnimatedVisibility(
          visible = trailerPlaying && !detail.trailerUrl.isNullOrBlank(),
          // Same parallax layer as the static hero image, so scrolling glides the
          // page content over the trailer while the video drifts at half speed.
          modifier = Modifier.fillMaxSize(),
          enter = fadeIn(animationSpec = tween(420)),
          exit = fadeOut(animationSpec = tween(520)),
        ) {
          key(trailerPlaybackKey) {
            TrailerPlaybackView(
              url = detail.trailerUrl.orEmpty(),
              modifier = Modifier.fillMaxSize(),
              autoPlay = true,
              muted = trailerMuted,
              maxHeight = trailerResolution,
              onReadyChanged = { trailerReady = it },
              onLoadFailed = { trailerFailed = true; trailerPlaying = false },
              onEnded = { trailerPlaying = false; trailerReady = false },
            )
          }
        }
        if (trailerPlaying) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { trailerMuted = !trailerMuted },
          )
        }
        if (trailerPlaying && !trailerReady && !trailerFailed) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.86f))
          }
        }
        if (autoPlayTrailer && !detail.trailerUrl.isNullOrBlank()) {
          Row(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FrostedGlassSurface(
              modifier = Modifier.size(48.dp),
              shape = CircleShape,
                // Sample the same page-level haze state as the back button so the
                // controls share its frosted backdrop-blur look.
                hazeStateOverride = hazeState,
                blurRadius = 68f,
                tintAlpha = if (lightDetail) 0.14f else 0.06f,
                borderAlpha = if (lightDetail) 0.10f else 0.08f,
                baseAlpha = if (lightDetail) 0.28f else 0.08f,
                fillColorOverride = if (lightDetail) null else Color.White,
                showEdgeGradient = false,
              ) {
              IconButton(
                onClick = {
                  if (trailerPlaying) trailerMuted = !trailerMuted else {
                    trailerFailed = false
                    trailerReady = false
                    trailerMuted = true
                    trailerPlaybackKey += 1
                    trailerPlaying = true
                  }
                },
              ) {
                Icon(
                  imageVector = if (!trailerPlaying) Icons.Rounded.Refresh else if (trailerMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                  contentDescription = if (!trailerPlaying) "Replay trailer" else if (trailerMuted) "Unmute trailer" else "Mute trailer",
                  tint = Color.White,
                )
              }
            }
            if (trailerPlaying) {
              FrostedGlassSurface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                // Sample the same page-level haze state as the back button so the
                // controls share its frosted backdrop-blur look.
                hazeStateOverride = hazeState,
                blurRadius = 68f,
                tintAlpha = if (lightDetail) 0.14f else 0.06f,
                borderAlpha = if (lightDetail) 0.10f else 0.08f,
                baseAlpha = if (lightDetail) 0.28f else 0.08f,
                fillColorOverride = if (lightDetail) null else Color.White,
                showEdgeGradient = false,
              ) {
                IconButton(onClick = { trailerPlaying = false; trailerReady = false }) {
                  Icon(Icons.Rounded.Close, contentDescription = "Stop trailer", tint = trailerControlContent)
                }
              }
            }
          }
        }
        if (!lightDetail) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
        if (!lightDetail) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(360.dp)
            .background(
              Brush.verticalGradient(
                colorStops = arrayOf(
                  0.00f to Color.Transparent,
                  0.18f to Color.Black.copy(alpha = 0.08f),
                  0.42f to Color.Black.copy(alpha = 0.32f),
                  0.68f to Color.Black.copy(alpha = 0.70f),
                  1.00f to Color.Black,
                ),
              ),
            ),
        )
        }

        Column(
          modifier = Modifier
            .align(if (style == DetailPageStyle.Centered) Alignment.BottomCenter else Alignment.BottomStart)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 22.dp),
          horizontalAlignment = if (style == DetailPageStyle.Centered) Alignment.CenterHorizontally else Alignment.Start,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (detail.titleLogo != null) {
            HeroTitleArtwork(
              url = detail.titleLogo,
              title = detail.title,
              modifier = Modifier.fillMaxWidth(if (style == DetailPageStyle.Centered) 1f else 0.68f).height(92.dp).padding(horizontal = if (style == DetailPageStyle.Centered) 40.dp else 0.dp),
              alignment = if (style == DetailPageStyle.Centered) Alignment.Center else Alignment.CenterStart,
            )
          } else {
            Text(
              detail.title,
              style = MaterialTheme.typography.headlineMedium.copy(shadow = if (lightDetail) Shadow(Color.Black.copy(alpha = 0.62f), Offset(0f, 2f), 5f) else null),
              fontWeight = FontWeight.Black,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
              color = Color.White,
              textAlign = if (style == DetailPageStyle.Centered) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
            )
          }
          DetailRatingBadges(
            detail = detail,
            ratingsEnabled = ratingsEnabled,
            externalRatingsEnabled = externalRatingsEnabled,
            enabledRatingProviders = enabledRatingProviders,
          )
          if (metadataLine.isNotBlank()) {
            Text(
              metadataLine,
              color = Color.White.copy(alpha = 0.72f),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (proxyStatus != null) {
            LiveProxyStatusBadge(proxied = proxyStatus)
          }
        }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              colors = when {
                lightDetail && style == DetailPageStyle.Centered -> listOf(Color.Transparent, Color.Transparent, Color.Transparent)
                lightDetail -> listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)
                else -> listOf(Color.Black, Color.Black.copy(alpha = 0.82f), Color.Transparent)
              },
            ),
          )
          .padding(horizontal = 24.dp)
          .padding(top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        StreamDekDetailActions(
          streamCount = streamCount,
          streamLoading = streamLoading,
          selectedTab = selectedTab,
          showEpisodes = showEpisodes,
          showStreamsList = showStreamsList,
          inWatchlist = inWatchlist,
          showMediaActions = showMediaActions,
          autoPlayTrailer = autoPlayTrailer && !detail.trailerUrl.isNullOrBlank(),
          primaryPlayLabel = primaryPlayLabel,
          onPlay = onPlay,
          hasTrailer = !detail.trailerUrl.isNullOrBlank(),
          onTrailer = onTrailer,
          onAbout = onAbout,
          onStreams = onStreams,
          onEpisodes = onEpisodes,
          onSave = onSave,
          showWatchedAction = showWatchedAction,
          watched = watched,
          onToggleWatched = onToggleWatched,
          showFavouriteAction = showFavouriteAction,
          isFavourite = isFavourite,
          onToggleFavourite = onToggleFavourite,
          modifier = Modifier.padding(top = if (lightDetail && style == DetailPageStyle.Centered) 10.dp else 0.dp),
          actionSpacing = if (lightDetail && style == DetailPageStyle.Centered) 19.dp else 24.dp,
        )
        if (overview.isNotBlank()) {
          Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
              overview,
              color = detailForeground.copy(alpha = 0.76f),
              style = MaterialTheme.typography.bodyMedium,
              lineHeight = 23.sp,
              maxLines = if (overviewExpanded) 8 else 4,
              overflow = TextOverflow.Ellipsis,
              onTextLayout = { layout ->
                if (!overviewExpanded) overviewCanExpand = layout.hasVisualOverflow
              },
            )
            if (overviewCanExpand || overviewExpanded) {
              Text(
                if (overviewExpanded) "Show less" else "Show more",
                color = detailForeground.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onOverviewExpandedChange(!overviewExpanded) },
              )
            }
          }
        }
      }
    }
  }
}

private fun formatReleaseDate(value: String?): String {
  if (value.isNullOrBlank()) return "Unknown"
  val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value) }.getOrNull()
  if (parsed != null) return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(parsed)
  return value
}

private fun isFutureReleaseDate(value: String?): Boolean {
  val date = value?.take(10) ?: return false
  if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) return false
  val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
  return date > today
}

private fun movieStatusLabel(detail: MediaDetail): String =
  if (detail.type == "movie" && isFutureReleaseDate(detail.releaseDate)) "Unreleased" else "Released"

@Composable
private fun StreamDekDetailActions(
  streamCount: Int,
  streamLoading: Boolean,
  primaryPlayLabel: String,
  selectedTab: String,
  showEpisodes: Boolean,
  showStreamsList: Boolean,
  inWatchlist: Boolean,
  showMediaActions: Boolean,
  showWatchedAction: Boolean,
  watched: Boolean,
  autoPlayTrailer: Boolean,
  onPlay: () -> Unit,
  hasTrailer: Boolean,
  onTrailer: () -> Unit,
  onAbout: () -> Unit,
  onStreams: () -> Unit,
  onEpisodes: () -> Unit,
  onSave: () -> Unit,
  onToggleWatched: () -> Unit,
  modifier: Modifier = Modifier,
  actionSpacing: Dp = 24.dp,
  showFavouriteAction: Boolean = false,
  isFavourite: Boolean = false,
  onToggleFavourite: () -> Unit = {},
) {
  val foreground = MaterialTheme.colorScheme.onBackground
  val background = MaterialTheme.colorScheme.background
  val lightMode = background.luminance() > 0.5f
  val primaryContainer = if (lightMode) MaterialTheme.colorScheme.surface else foreground
  val primaryContent = if (lightMode) MaterialTheme.colorScheme.onSurface else background
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(actionSpacing), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Button(
        onClick = onPlay,
        enabled = primaryPlayLabel != "Unreleased",
        modifier = Modifier.weight(1f).height(50.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = primaryContainer, contentColor = primaryContent),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (lightMode) 3.dp else 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
      ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(primaryPlayLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
      }
      if (showMediaActions && !autoPlayTrailer) {
        if (lightMode) {
          Button(
            onClick = onTrailer,
            enabled = hasTrailer,
            modifier = Modifier.size(50.dp),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryContainer, contentColor = primaryContent),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
            contentPadding = PaddingValues(0.dp),
          ) {
            Icon(Icons.Rounded.Movie, contentDescription = "Trailer", modifier = Modifier.size(21.dp))
          }
        } else {
          OutlinedButton(
            onClick = onTrailer,
            enabled = hasTrailer,
            modifier = Modifier.size(50.dp),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, foreground.copy(alpha = if (hasTrailer) 0.26f else 0.10f)),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = foreground.copy(alpha = 0.08f),
              contentColor = foreground,
              disabledContentColor = foreground.copy(alpha = 0.34f),
            ),
            contentPadding = PaddingValues(0.dp),
          ) {
            Icon(Icons.Rounded.Movie, contentDescription = "Trailer", modifier = Modifier.size(21.dp))
          }
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      DetailPillTab("About", selectedTab == DetailTab.About.name, onAbout, modifier = Modifier.weight(1f))
      if (showEpisodes) {
        DetailPillTab("Episodes", selectedTab == DetailTab.Episodes.name, onEpisodes, modifier = Modifier.weight(1.18f))
      } else if (showStreamsList) {
        DetailPillTab("Streams ($streamCount)", selectedTab == DetailTab.Streams.name, onStreams, modifier = Modifier.weight(1.18f), loading = streamLoading)
      }
      if (showMediaActions) {
        DetailIconOnlyPill(if (inWatchlist) "Saved" else "Save", inWatchlist, onSave) { Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(19.dp)) }
      }
      if (showFavouriteAction) {
        DetailIconOnlyPill(if (isFavourite) "Favourited" else "Favourite", isFavourite, onToggleFavourite) {
          Icon(if (isFavourite) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = null, modifier = Modifier.size(19.dp))
        }
      }
      if (showWatchedAction) {
        DetailIconOnlyPill(if (watched) "Watched" else "Seen", watched, onToggleWatched) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = if (watched) Color(0xFF22C55E) else LocalContentColor.current, modifier = Modifier.size(19.dp))
        }
      }
    }
  }
}

@Composable
private fun DetailPillTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false) {
  val foreground = MaterialTheme.colorScheme.onBackground
  OutlinedButton(
    modifier = modifier,
    onClick = onClick,
    shape = RoundedCornerShape(999.dp),
    border = BorderStroke(1.dp, foreground.copy(alpha = if (selected) 0.82f else 0.18f)),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = foreground.copy(alpha = if (selected) 0.10f else 0.04f),
      contentColor = foreground,
    ),
    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(13.dp),
        strokeWidth = 1.7.dp,
        color = foreground.copy(alpha = 0.86f),
      )
      Spacer(modifier = Modifier.width(7.dp))
    }
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun DetailIconOnlyPill(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
  val selectedColor = Color(0xFF22C55E)
  val foreground = MaterialTheme.colorScheme.onBackground
  OutlinedButton(
    onClick = onClick,
    shape = RoundedCornerShape(999.dp),
    border = BorderStroke(1.dp, if (selected) selectedColor.copy(alpha = 0.92f) else foreground.copy(alpha = 0.16f)),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = if (selected) selectedColor.copy(alpha = 0.18f) else foreground.copy(alpha = 0.04f),
      contentColor = if (selected) selectedColor else foreground,
    ),
    contentPadding = PaddingValues(0.dp),
    modifier = Modifier.size(48.dp),
  ) {
    AnimatedContent(targetState = selected, label = "detail_action_" + label) { _ -> icon() }
  }
}
@Composable
private fun DetailIconTab(label: String, selected: Boolean, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    shape = RoundedCornerShape(999.dp),
    border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.70f else 0.16f)),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = if (selected) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.12f),
      contentColor = Color.White,
    ),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun DetailActionIconPill(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    shape = RoundedCornerShape(999.dp),
    border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.70f else 0.16f)),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = if (selected) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.12f),
      contentColor = Color.White,
    ),
    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
  ) {
    Icon(icon, contentDescription = label, modifier = Modifier.size(17.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
  }
}
@Composable
private fun DetailFactsSection(detail: MediaDetail) {
  Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("Movie details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      DetailFactCard("RELEASE DATE", formatReleaseDate(detail.releaseDate), Modifier.weight(1f))
      DetailFactCard("STATUS", movieStatusLabel(detail), Modifier.weight(1f))
      DetailFactCard("DURATION", detail.runtimeMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "N/A", Modifier.weight(1f))
    }
  }
}

@Composable
private fun DetailFactCard(label: String, value: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
      .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.42f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
    Text(value, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun DetailCastSection(cast: List<CastMember>, onOpenPerson: (CastMember) -> Unit) {
  if (cast.isEmpty()) return
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("Cast", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 24.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
      items(cast, key = { it.id.ifBlank { it.name } }) { member ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.width(92.dp),
        ) {
          Box(
            modifier = Modifier
              .size(78.dp)
              .clip(CircleShape)
              .clickable { onOpenPerson(member) }
              .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f))
              .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            if (member.photo != null) {
              AsyncImage(model = member.photo, contentDescription = member.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
              Text(member.name.take(1), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black)
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(member.name, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          member.character?.let {
            Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
  }
}

@Composable
private fun DetailMoreLikeThisSection(items: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
  if (items.isEmpty()) return
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("More Like This", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 24.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
      items(items, key = { "similar-${it.type}-${it.id}" }) { item ->
        PosterCard(item = item, onClick = { onOpen(item) })
      }
    }
  }
}

@Composable
private fun PersonDetailDialog(person: PersonDetail, onDismiss: () -> Unit, onOpenWork: (MediaItem) -> Unit) {
  var bioExpanded by rememberSaveable(person.id, "bio") { mutableStateOf(false) }
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.94f),
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(28.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)),
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text(person.name, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
          TextButton(onClick = onDismiss) { Text("Close") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            if (person.photo != null) {
              AsyncImage(model = person.photo, contentDescription = person.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
              Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f), modifier = Modifier.size(44.dp))
            }
          }
          Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            person.knownFor?.let { Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
            person.birthday?.let { Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f), style = MaterialTheme.typography.bodySmall) }
            person.placeOfBirth?.let { Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
          }
        }
        person.biography?.takeIf { it.isNotBlank() }?.let { bio ->
          val bioScroll = rememberScrollState()
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              bio,
              maxLines = if (bioExpanded) Int.MAX_VALUE else 6,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
              style = MaterialTheme.typography.bodySmall,
              lineHeight = 19.sp,
              modifier = Modifier.heightIn(max = if (bioExpanded) 280.dp else 126.dp).verticalScroll(bioScroll),
            )
            if (bio.length > 260) {
              Text(
                if (bioExpanded) "Show less" else "Read more",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { bioExpanded = !bioExpanded },
              )
            }
          }
        }
        if (person.popularWorks.isNotEmpty()) {
          Text("Known For", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(person.popularWorks.take(10), key = { "person-work-${it.type}-${it.id}" }) { item ->
              Column(
                modifier = Modifier.width(82.dp).clickable { onOpenWork(item) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                AsyncImage(model = item.poster ?: item.backdrop, contentDescription = item.title, modifier = Modifier.height(122.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Text(item.title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DetailAvailableOnSection(detail: MediaDetail) {
  val providers = detail.availableOn
  if (providers.isEmpty()) return
  val context = LocalContext.current
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Available On", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 24.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      items(providers, key = { it.id.ifBlank { it.name } }) { provider ->
        ServiceProviderPill(provider = provider, onClick = { openExternalUrl(context, watchProviderUrl(provider, detail.title)) })
      }
    }
  }
}

private data class TraktCommentPreview(val author: String, val rating: String?, val body: String, val likes: Int)

@Composable
private fun DetailTraktCommentSection(detail: MediaDetail, uiState: AppUiState) {
  val comments = detail.traktComments.map {
    TraktCommentPreview(
      author = it.author,
      rating = it.rating?.let { rating -> "Rating $rating/10" },
      body = it.body,
      likes = it.likes,
    )
  }
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Trakt Comments", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 24.dp))
    if (comments.isEmpty()) {
      Box(modifier = Modifier.padding(horizontal = 24.dp)) {
        GlassScrim(modifier = Modifier.fillMaxWidth()) {
          Text(
            if (uiState.traktStatus.connected) "No Trakt comments available for this title yet." else "Connect Trakt in Settings to load comments when available.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    } else {
      LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        items(comments, key = { "${it.author}-${it.body.hashCode()}" }) { comment ->
          TraktCommentCard(comment = comment)
        }
      }
    }
  }
}

@Composable
private fun TraktCommentCard(comment: TraktCommentPreview) {
  Column(
    modifier = Modifier
      .width(310.dp)
      .height(206.dp)
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
      .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
      .padding(18.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(comment.author, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      comment.rating?.let {
        Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)).border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
          Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
      Text(comment.body, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f), fontSize = 15.sp, lineHeight = 22.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
    Text("${comment.likes} likes", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun ServiceProviderPill(provider: WatchProvider, onClick: () -> Unit) {
  val initials = provider.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "TV" }
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(999.dp))
      .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
      .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier = Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)).border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      if (provider.logo != null) {
        AsyncImage(model = provider.logo, contentDescription = provider.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
      } else {
        Text(initials, color = MaterialTheme.colorScheme.onBackground, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
      }
    }
    Text(provider.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

private fun watchProviderUrl(provider: WatchProvider, title: String): String {
  provider.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { return it }
  val query = android.net.Uri.encode(title)
  return when {
    provider.name.contains("Netflix", true) -> "https://www.netflix.com/search?q=$query"
    provider.name.contains("Prime", true) || provider.name.contains("Amazon", true) -> "https://www.primevideo.com/search/ref=atv_nb_sr?phrase=$query"
    provider.name.contains("Disney", true) -> "https://www.disneyplus.com/search/$query"
    provider.name.contains("Apple TV", true) -> "https://tv.apple.com/search?term=$query"
    provider.name.contains("Paramount", true) -> "https://www.paramountplus.com/search/?q=$query"
    provider.name.contains("Hulu", true) -> "https://www.hulu.com/search?q=$query"
    provider.name.contains("Max", true) || provider.name.contains("HBO", true) -> "https://www.max.com/search?q=$query"
    provider.name.contains("Peacock", true) -> "https://www.peacocktv.com/search?q=$query"
    provider.name.contains("YouTube", true) -> "https://www.youtube.com/results?search_query=$query"
    else -> "https://www.google.com/search?q=" + android.net.Uri.encode("${provider.name} $title")
  }
}

private fun streamProviderNames(streams: List<AddonStream>): List<String> =
  streams.mapNotNull { it.addonName.takeIf(String::isNotBlank) ?: it.name?.takeIf(String::isNotBlank) }.distinct()

@Composable
private fun StreamProviderFilterRow(
  providers: List<String>,
  selectedProvider: String,
  onProviderSelected: (String) -> Unit,
  horizontalPadding: Dp = 24.dp,
) {
  if (providers.size <= 1) return
  val filterForeground = MaterialTheme.colorScheme.onSurface
  val filterBackground = MaterialTheme.colorScheme.surface
  LazyRow(
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = horizontalPadding),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(listOf("All") + providers, key = { it }) { provider ->
      val selected = selectedProvider == provider
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(if (selected) filterForeground else filterForeground.copy(alpha = 0.10f))
          .border(1.dp, filterForeground.copy(alpha = if (selected) 0f else 0.12f), RoundedCornerShape(999.dp))
          .clickable { onProviderSelected(provider) }
          .padding(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Text(provider, color = if (selected) filterBackground else filterForeground, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun StreamListContent(
  uiState: AppUiState,
  selectedEpisode: EpisodeItem?,
  onPlayStream: (AddonStream, EpisodeItem?) -> Unit,
  onDownloadStream: (AddonStream) -> Unit = {},
  isStreamDownloadEligible: (AddonStream) -> Boolean = { false },
  compact: Boolean = false,
  selectedProviderOverride: String? = null,
  onProviderSelectedOverride: ((String) -> Unit)? = null,
  showProviderFilters: Boolean = true,
  horizontalPadding: Dp = 24.dp,
) {
  val streamLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val streamForeground = MaterialTheme.colorScheme.onSurface
  val streamCardColor = if (streamLightMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.075f)
  var longPressedStream by remember { mutableStateOf<AddonStream?>(null) }
  longPressedStream?.let { stream ->
    AlertDialog(
      onDismissRequest = { longPressedStream = null },
      title = { Text(stream.title?.takeIf { it.isNotBlank() } ?: stream.name?.takeIf { it.isNotBlank() } ?: "Stream options") },
      text = {
        Row(
          modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
            onDownloadStream(stream)
            longPressedStream = null
          }.padding(vertical = 12.dp, horizontal = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("Download for offline playback")
        }
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = { longPressedStream = null }) { Text("Cancel") } },
    )
  }
  val providers = remember(uiState.availableStreams) { streamProviderNames(uiState.availableStreams) }
  var localProviderFilter by rememberSaveable(providers) { mutableStateOf("All") }
  val providerFilter = selectedProviderOverride ?: localProviderFilter
  val onProviderSelected: (String) -> Unit = onProviderSelectedOverride ?: { localProviderFilter = it }
  LaunchedEffect(providers, providerFilter) {
    if (providerFilter != "All" && providerFilter !in providers) onProviderSelected("All")
  }
  val filteredStreams = remember(uiState.availableStreams, providerFilter) {
    if (providerFilter == "All") uiState.availableStreams
    else uiState.availableStreams.filter { (it.addonName.takeIf(String::isNotBlank) ?: it.name?.takeIf(String::isNotBlank)) == providerFilter }
  }

  if (uiState.streamLoading && uiState.availableStreams.isEmpty()) {
    Box(modifier = Modifier.padding(horizontal = horizontalPadding).fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = streamForeground)
    }
    return
  }
  if (uiState.availableStreams.isEmpty()) {
    Text("Searching sources automatically. If none appear, refresh the Sources tab.", modifier = Modifier.padding(horizontal = horizontalPadding), color = streamForeground.copy(alpha = 0.68f), style = MaterialTheme.typography.bodyMedium)
    return
  }
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (uiState.pendingStreamSources > 0) {
      Row(
        modifier = Modifier
          .padding(horizontal = horizontalPadding)
          .fillMaxWidth()
          .clip(RoundedCornerShape(18.dp))
          .background(streamForeground.copy(alpha = if (streamLightMode) 0.08f else 0.06f))
          .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularProgressIndicator(color = streamForeground, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
          "Searching ${uiState.pendingStreamSources} more source${if (uiState.pendingStreamSources == 1) "" else "s"}...",
          color = streamForeground.copy(alpha = 0.76f),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    if (showProviderFilters) {
      StreamProviderFilterRow(
        providers = providers,
        selectedProvider = providerFilter,
        onProviderSelected = onProviderSelected,
        horizontalPadding = horizontalPadding,
      )
    }
    filteredStreams.take(if (compact) 3 else 40).forEach { stream ->
      val primaryText = stream.title?.takeIf { it.isNotBlank() }
        ?: stream.name?.takeIf { it.isNotBlank() }
        ?: stream.filename?.takeIf { it.isNotBlank() }
        ?: stream.description?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(stream.addonName, stream.quality, stream.size).joinToString(" ").ifBlank { "Stream source" }
      val secondaryText = stream.description
        ?.takeIf { it.isNotBlank() && it != primaryText }
        ?: stream.filename?.takeIf { it.isNotBlank() && it != primaryText }
      Card(
        modifier = Modifier.padding(horizontal = horizontalPadding).fillMaxWidth().pointerInput(stream) {
          detectTapGestures(
            onTap = { onPlayStream(stream, selectedEpisode) },
            onLongPress = { if (isStreamDownloadEligible(stream)) longPressedStream = stream },
          )
        },
        colors = CardDefaults.cardColors(containerColor = streamCardColor),
        shape = RoundedCornerShape(22.dp),
      ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
          // "Top" places the badges at the very top of the card so the setting is
          // visibly honored for every addon result, including ones without a
          // secondary description line.
          if (uiState.fusionBadgesEnabled && uiState.badgePosition.equals("Top", ignoreCase = true)) {
            FusionBadgeRow(stream = stream, uiState = uiState)
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(primaryText, color = streamForeground, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
              Text(listOfNotNull(stream.addonName.takeIf { it.isNotBlank() }, stream.source?.takeIf { it.isNotBlank() }).distinct().joinToString(" • ").ifBlank { "Tap to play" }, color = streamForeground.copy(alpha = 0.62f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Single size badge for the card — the duplicate that used to render
            // inside the badge row below has been removed.
            if (uiState.showSizeBadges) {
              stream.size?.takeIf { it.isNotBlank() }?.let { size ->
                Box(
                  modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(oxbloodRed).padding(horizontal = 9.dp, vertical = 4.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    size.uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                  )
                }
              }
            }
            if (stream.cachedBy.isNotEmpty()) {
              Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(streamForeground).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("Cached", color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
              }
            }
          }
          secondaryText?.let {
            Text(
              it,
              color = streamForeground.copy(alpha = 0.76f),
              style = MaterialTheme.typography.bodyMedium,
              maxLines = if (compact) 2 else 4,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (uiState.fusionBadgesEnabled && uiState.badgePosition.equals("Bottom", ignoreCase = true)) {
            FusionBadgeRow(stream = stream, uiState = uiState)
          }
        }
      }
    }
  }
}
private val oxbloodRed = Color(0xFF5B1216)

private fun ratingColor(rating: Double, lightMode: Boolean = false): Color = when {
  rating >= 7.0 -> if (lightMode) Color(0xFF087F3D) else Color(0xFF00E676)
  rating >= 5.0 -> if (lightMode) Color(0xFF9A6700) else Color(0xFFFFD740)
  else -> Color(0xFFC97070)
}

private fun ratingProviderLogoRes(provider: String): Int = when (provider.lowercase()) {
  "imdb" -> R.drawable.rating_imdb_logo
  "trakt" -> R.drawable.rating_trakt_logo
  "rt", "rotten", "tomatoes", "rotten tomatoes" -> R.drawable.rating_rotten_tomatoes_logo
  "metacritic" -> R.drawable.rating_metacritic_logo
  "letterboxd" -> R.drawable.rating_letterboxd_logo
  "audience score", "audience" -> R.drawable.rating_audience_score
  else -> R.drawable.rating_tmdb_logo
}

private fun ratingProviderId(provider: String): String {
  val key = provider.trim().lowercase().replace(Regex("[^a-z]"), "")
  return when (key) {
    "imdb" -> "imdb"
    "tmdb", "themoviedb" -> "tmdb"
    "rt", "rotten", "tomatoes", "rottentomatoes", "rottentomato" -> "tomatoes"
    "mc", "metacritic", "metascore" -> "metacritic"
    "trakt" -> "trakt"
    "lb", "letterbox", "letterboxd" -> "letterboxd"
    "aud", "audience", "audiencescore", "rtaudience", "popcornmeter" -> "audience"
    else -> key
  }
}

private fun normalizedRatingProvider(provider: String): String {
  val key = provider.lowercase().replace(" ", "").replace("_", "").replace("-", "")
  return when {
    key == "tmdb" || key == "themoviedb" || key.contains("themoviedb") -> "TMDB"
    key == "imdb" -> "IMDb"
    key == "rottentomatoes" || key == "tomatoes" || key.contains("tomato") || key == "rt" || key == "rotten" -> "Rotten Tomatoes"
    key == "metacritic" -> "Metacritic"
    key == "trakt" -> "Trakt"
    key == "letterboxd" -> "Letterboxd"
    key == "audience" || key == "audiencescore" || key == "rtaudience" || key == "popcornmeter" -> "Audience Score"
    else -> provider
  }
}

@Composable
private fun ImdbInlineRating(rating: Double, scale: Float = 1f, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(RoundedCornerShape(999.dp))
      .background(Color.Black.copy(alpha = 0.58f))
      .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
      .padding(horizontal = 6.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier.height(20.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFF5C518)).padding(horizontal = 6.dp, vertical = 2.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text("IMDb", color = Color.Black, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
    }
    Text(
      "${"%.1f".format(rating)}",
      color = Color.White,
      fontWeight = FontWeight.Black,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
    )
  }
}
@Composable
private fun BoxScope.CardImdbRatingBadge(rating: Double?, topPadding: Dp = 5.dp) {
  rating ?: return
  ImdbInlineRating(
    rating = rating,
    scale = 0.70f,
    modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding),
  )
}

private fun ratingLabelForProvider(provider: String, rating: Double?): String {
  if (rating == null) return ""
  val normalized = normalizedRatingProvider(provider)
  return when (normalized) {
    "Rotten Tomatoes", "Audience Score" -> "${rating.toInt()}%"
    "TMDB", "Metacritic", "Trakt" -> if (rating > 10.0) rating.toInt().toString() else "${"%.1f".format(rating)}"
    else -> "${"%.1f".format(rating)}"
  }
}

@Composable
private fun RatingProviderBadge(
  provider: String,
  rating: Double?,
  modifier: Modifier = Modifier,
  label: String? = null,
) {
  val lightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Image(
      painter = painterResource(ratingProviderLogoRes(provider)),
      contentDescription = provider,
      modifier = Modifier.height(14.dp),
      contentScale = ContentScale.Fit,
    )
    Text(
      label?.takeIf { it.isNotBlank() } ?: ratingLabelForProvider(provider, rating).ifBlank { normalizedRatingProvider(provider) },
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Black,
      color = rating?.let { ratingColor(it, lightMode) } ?: MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
      maxLines = 1,
    )
  }
}

@Composable
private fun LiveProxyStatusBadge(proxied: Boolean, modifier: Modifier = Modifier) {
  val tint = if (proxied) Color(0xFF22C55E) else Color(0xFF94A3B8)
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(999.dp))
      .background(tint.copy(alpha = 0.16f))
      .border(1.dp, tint.copy(alpha = 0.38f), RoundedCornerShape(999.dp))
      .padding(horizontal = 10.dp, vertical = 5.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(if (proxied) Icons.Rounded.Shield else Icons.Rounded.Public, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
    Text(if (proxied) "Proxied Stream" else "Direct Stream", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tint)
  }
}

@Composable
private fun DetailRatingBadges(
  detail: MediaDetail,
  ratingsEnabled: Boolean,
  externalRatingsEnabled: Boolean,
  enabledRatingProviders: Set<String>,
  modifier: Modifier = Modifier,
) {
  if (!ratingsEnabled || enabledRatingProviders.isEmpty()) return
  val externalByProvider = if (externalRatingsEnabled) {
    detail.externalRatings
      .filter { it.rating != null }
      .map { it.copy(provider = normalizedRatingProvider(it.provider)) }
      .groupBy { ratingProviderId(it.provider) }
  } else {
    emptyMap()
  }
  val ratings = buildList {
    DEFAULT_RATING_PROVIDER_ORDER.forEach { providerId ->
      if (providerId !in enabledRatingProviders) return@forEach
      val external = externalByProvider[providerId]?.firstOrNull()
      when {
        external != null -> add(external)
        providerId == "imdb" && detail.imdbRating != null -> add(ExternalRating("IMDb", detail.imdbRating))
        providerId == "tmdb" && detail.tmdbRating != null -> add(ExternalRating("TMDB", detail.tmdbRating))
      }
    }
  }
  AnimatedVisibility(
    visible = ratings.isNotEmpty(),
    enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(320)) + slideInVertically(initialOffsetY = { it / 3 }, animationSpec = androidx.compose.animation.core.tween(360)),
    exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(180)),
  ) {
    Row(
      modifier = modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ratings.forEach { item ->
        RatingProviderBadge(provider = item.provider, rating = item.rating, label = item.label)
      }
    }
  }
}

@Composable
private fun FusionBadgeRow(stream: AddonStream, uiState: AppUiState, modifier: Modifier = Modifier) {
  val badges = remember(stream, uiState.fusionBadgeSources, uiState.activeFusionBadgeUrl, uiState.fusionBadgeUrls) {
    matchFusionBadgeFilters(stream, activeFusionSources(uiState)).take(10)
  }
  if (badges.isEmpty()) return
  val context = LocalContext.current
  val badgeShape = RoundedCornerShape(6.dp)
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    badges.forEach { badge ->
      val isFlag = badge.groupId == LANGUAGE_BADGE_GROUP_ID
      Box(
        modifier = Modifier
          .height(24.dp)
          .width(if (isFlag) 24.dp else 46.dp)
          .clip(badgeShape)
          .padding(horizontal = 4.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
      ) {
        AsyncImage(
          model = ImageRequest.Builder(context)
            .data(badge.imageUrl)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build(),
          contentDescription = badge.name,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit,
        )
      }
    }
  }
}
@Composable
private fun PosterCard(item: MediaItem, showMeta: Boolean = true, dimmed: Boolean = false, onClick: () -> Unit, onLongPress: () -> Unit = {}) {
  Column(
    modifier = Modifier
      .width(138.dp)
      .alpha(if (dimmed) 0.4f else 1f)
      .pointerInput(item.id, item.type) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() }) },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(204.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(Color(0xFF171717)),
    ) {
      AsyncImage(
        model = item.poster ?: item.backdrop,
        contentDescription = item.title,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
      Box(
        modifier = Modifier.fillMaxSize().background(
          Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Transparent, Color(0x8C000000)),
          ),
        ),
      )

      CardImdbRatingBadge(rating = item.rating)
    }
    if (showMeta) {
      Column(modifier = Modifier.height(56.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          item.title,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
          lineHeight = 16.sp,
        )
        Text(
          item.year ?: item.type.replaceFirstChar(Char::uppercase),
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
        )
      }
    }
  }
}

@Composable
private fun ContinueWatchingCard(item: MediaItem, style: ContinueWatchingStyle, onClick: () -> Unit, onLongPress: () -> Unit = {}) {
  val watchedPercent = (item.progress ?: 0.0).toInt().coerceIn(0, 100)
  val progressFraction = (watchedPercent / 100f).coerceIn(0f, 1f)
  val imageModel = item.backdrop ?: item.poster
  val progressBar: @Composable () -> Unit = {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(5.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(progressFraction)
          .height(5.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(MaterialTheme.colorScheme.primary),
      )
    }
  }

  when (style) {
    ContinueWatchingStyle.Cinematic, ContinueWatchingStyle.Glass -> {
      val overlayAlpha = if (style == ContinueWatchingStyle.Glass) 0.54f else 0.76f
      val cardHazeState = rememberHazeState()
      Box(
        modifier = Modifier
          .width(288.dp)
          .aspectRatio(16f / 9f)
          .clip(RoundedCornerShape(14.dp))
          .background(Color(0xFF141414))
          .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
          .pointerInput(item.id, item.type, style) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() }) },
      ) {
        AsyncImage(
          model = imageModel,
          contentDescription = item.title,
          modifier = Modifier
            .fillMaxSize()
            .then(if (style == ContinueWatchingStyle.Glass) Modifier.hazeSource(cardHazeState) else Modifier),
          contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (style == ContinueWatchingStyle.Glass) 0.26f else 0.38f)))
        if (style == ContinueWatchingStyle.Glass) {
          FrostedGlassSurface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(60.5.dp),
            shape = RectangleShape,
            hazeStateOverride = cardHazeState,
            blurRadius = 38f,
            tintAlpha = 0.10f,
            borderAlpha = 0f,
            baseAlpha = 0.08f,
            fillColorOverride = Color.Black,
            showEdgeGradient = false,
          ) {}
        }
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = overlayAlpha), Color.Black.copy(alpha = 0.94f))))
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = Color.White)
          Text(item.year ?: item.type.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
          progressBar()
          Text("$watchedPercent% watched", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
        }
      }
    }
    ContinueWatchingStyle.Ticket -> {
      Box(
        modifier = Modifier
          .width(316.dp)
          .height(88.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color(0xFF121212))
          .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
          .pointerInput(item.id, item.type, style) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() }) },
      ) {
        AsyncImage(model = imageModel, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(listOfNotNull(item.year, item.rating?.let { "%.1f".format(it) }).joinToString(" • ").ifBlank { item.type.replaceFirstChar(Char::uppercase) }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
          }
          Text("$watchedPercent%", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
        }
        Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) { progressBar() }
      }
    }
    ContinueWatchingStyle.Mini -> {
      Row(
        modifier = Modifier
          .width(292.dp)
          .height(78.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF121218))
          .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
          .pointerInput(item.id, item.type, style) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() }) },
      ) {
        AsyncImage(model = imageModel, contentDescription = item.title, modifier = Modifier.width(120.dp).fillMaxSize(), contentScale = ContentScale.Crop)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
          Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, color = Color.White)
          Text(item.year ?: item.type.replaceFirstChar(Char::uppercase), fontSize = 10.sp, color = Color.White.copy(alpha = 0.66f))
          progressBar()
        }
      }
    }
    ContinueWatchingStyle.Stacked -> {
      Column(
        modifier = Modifier
          .width(155.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF121218))
          .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
          .pointerInput(item.id, item.type, style) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() }) },
      ) {
        Box(modifier = Modifier.fillMaxWidth().height(88.dp)) {
          AsyncImage(model = imageModel, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
          Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) { progressBar() }
        }
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, color = Color.White)
          Text(item.year ?: item.type.replaceFirstChar(Char::uppercase), fontSize = 10.sp, color = Color.White.copy(alpha = 0.66f))
          Text("$watchedPercent% watched", fontSize = 10.sp, color = Color.White.copy(alpha = 0.72f))
        }
      }
    }
  }
}
@Composable
private fun SearchResultRow(item: MediaItem, onClick: () -> Unit) {
  GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Box(modifier = Modifier.size(width = 96.dp, height = 132.dp).clip(RoundedCornerShape(18.dp))) {
        AsyncImage(model = item.poster ?: item.backdrop, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        CardImdbRatingBadge(rating = item.rating)
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(listOfNotNull(item.type.replaceFirstChar(Char::uppercase), item.year).joinToString(" \u2022 "), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))

        Text(item.description.ifBlank { "No description available." }, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
      }
    }
  }
}

@Composable
private fun LibraryMediaCard(item: MediaItem) {
  GlassCard {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(item.title, fontWeight = FontWeight.Bold)
      Text(listOfNotNull(item.type.replaceFirstChar(Char::uppercase), item.year, item.progress?.let { "${it.toInt()}%" }).joinToString(" \u2022 "), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
      item.description.takeIf { it.isNotBlank() }?.let {
        Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
      }
    }
  }
}

@Composable
private fun DetailInfoGrid(detail: MediaDetail) {
  val items = listOfNotNull(
    "Type" to detail.type.replaceFirstChar(Char::uppercase),
    detail.year?.let { "Year" to it },
    detail.runtimeMinutes?.let { "Runtime" to "${it} min" },
    detail.seasonsCount?.takeIf { detail.type == "tv" }?.let { "Seasons" to "$it" },
    detail.rating?.let { "Rating" to "${"%.1f".format(it)}/10" },
    detail.imdbId?.let { "IMDb" to it },
  )
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    items.chunked(2).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { (label, value) ->
          GlassScrim(modifier = Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
              Text(value, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
          }
        }
        if (row.size == 1) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun SeasonSelector(
  seasons: List<SeasonSummary>,
  selectedSeasonNumber: Int?,
  style: SeasonTabStyle,
  fallbackPoster: String?,
  onSelect: (SeasonSummary) -> Unit,
) {
  val effectiveStyle = if (style == SeasonTabStyle.Posters && seasons.none { !it.poster.isNullOrBlank() }) SeasonTabStyle.Regular else style
  LazyRow(horizontalArrangement = Arrangement.spacedBy(if (effectiveStyle == SeasonTabStyle.Posters) 10.dp else 8.dp)) {
    items(seasons, key = { "season-${it.seasonNumber}" }) { season ->
      val selected = selectedSeasonNumber == season.seasonNumber
      if (effectiveStyle == SeasonTabStyle.Posters) {
        Column(
          modifier = Modifier.width(71.dp).clickable { onSelect(season) },
          verticalArrangement = Arrangement.spacedBy(8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          AsyncImage(
            model = season.poster ?: fallbackPoster,
            contentDescription = season.name,
            modifier = Modifier
              .fillMaxWidth()
              .height(106.dp)
              .clip(RoundedCornerShape(14.dp))
              .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop,
          )
          Text(
            season.name.ifBlank { "Season ${season.seasonNumber}" },
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      } else {
        FilterChip(
          selected = selected,
          onClick = { onSelect(season) },
          label = { Text("Season ${season.seasonNumber}") },
        )
      }
    }
  }
}

@Composable
private fun SeasonSelectorSkeleton(style: SeasonTabStyle) {
  val posterStyle = style == SeasonTabStyle.Posters
  LazyRow(horizontalArrangement = Arrangement.spacedBy(if (posterStyle) 10.dp else 8.dp)) {
    items(4) { _ ->
      if (posterStyle) {
        Column(
          modifier = Modifier.width(71.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          SkeletonBlock(modifier = Modifier.fillMaxWidth().height(106.dp), radius = 14.dp)
          SkeletonBlock(modifier = Modifier.fillMaxWidth(0.84f).height(12.dp), radius = 8.dp)
        }
      } else {
        SkeletonBlock(modifier = Modifier.width(102.dp).height(32.dp), radius = 999.dp)
      }
    }
  }
}
private fun isEpisodeUnreleased(episode: EpisodeItem): Boolean {
  val date = episode.airDate?.take(10) ?: return false
  if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) return false
  val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
  return date > today
}

private fun formatEpisodeAirDateLabel(date: String): String {
  val normalized = date.take(10)
  return runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val parsed = parser.parse(normalized)
    if (parsed != null) {
      val today = parser.format(java.util.Date())
      val prefix = if (normalized > today) "Airs " else "Aired "
      prefix + SimpleDateFormat("d MMM yyyy", Locale.US).format(parsed)
    } else {
      normalized
    }
  }.getOrDefault(normalized)
}

@Composable
private fun LockedEpisodeOverlay(label: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
    Box(
      modifier = Modifier
        .padding(12.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
        .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
      Text(label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
    }
  }
}

@Composable
private fun EpisodeContentBlurModifier(locked: Boolean): Modifier = if (locked) Modifier.blur(10.dp) else Modifier
@Composable
private fun EpisodeViewportCard(
  episode: EpisodeItem,
  watched: Boolean,
  blurUnwatched: Boolean,
  onToggleWatched: () -> Unit,
  onOpen: () -> Unit,
) {
  val unreleased = isEpisodeUnreleased(episode)
  val locked = blurUnwatched && !watched
  Box(
    modifier = Modifier
      .width(314.dp)
      .height(202.dp)
      .clip(RoundedCornerShape(24.dp))
      .clickable(onClick = onOpen),
  ) {
    AsyncImage(model = episode.still, contentDescription = episode.name, modifier = Modifier.fillMaxSize().then(EpisodeContentBlurModifier(locked)), contentScale = ContentScale.Crop)
    Box(
      modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(
          colors = listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.48f), Color.Black.copy(alpha = 0.88f)),
        ),
      ),
    )
    if (locked && unreleased) LockedEpisodeOverlay("Upcoming")
    Column(
      modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
      verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Box(modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.58f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text("S${episode.seasonNumber}E${episode.episodeNumber}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
      }
      Text(episode.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
      episode.airDate?.let { airDate ->
        Text(formatEpisodeAirDateLabel(airDate), color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
      }
      Text(episode.overview.ifBlank { "Tap to view streams." }, color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        episode.runtime?.takeIf { it > 0 }?.let { Text("${it}m", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        TextButton(onClick = onToggleWatched) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = if (watched) Color(0xFF22C55E) else Color(0xFFF2F2EE), modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(5.dp))
          Text(if (watched) "Watched" else "Mark watched", color = if (watched) Color(0xFF22C55E) else Color(0xFFF2F2EE))
        }
      }
    }
  }
}

@Composable
private fun EpisodeStreamsPage(
  detail: MediaDetail,
  episode: EpisodeItem,
  uiState: AppUiState,
  watched: Boolean,
  onBack: () -> Unit,
  onReload: () -> Unit,
  onToggleWatched: () -> Unit,
  onPlayStream: (AddonStream, EpisodeItem?) -> Unit,
  onDownloadStream: (AddonStream, String) -> Unit = { _, _ -> },
  isStreamDownloadEligible: (AddonStream) -> Boolean = { false },
) {
  val heroImage = episode.still ?: detail.backdrop ?: detail.poster
  val providers = remember(uiState.availableStreams) { streamProviderNames(uiState.availableStreams) }
  var providerFilter by rememberSaveable(detail.id, episode.id) { mutableStateOf("All") }
  val episodeHazeState = rememberHazeState()
  val lightStreamsPage = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val streamsPageForeground = MaterialTheme.colorScheme.onSurface
  val filterPanelHeight = if (providers.size > 1) 102.dp else 58.dp
  LaunchedEffect(providers, providerFilter) {
    if (providerFilter != "All" && providerFilter !in providers) providerFilter = "All"
  }
  BackHandler(onBack = onBack)
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      if (uiState.vividAmbient) {
        AsyncImage(
          model = heroImage,
          contentDescription = null,
          modifier = Modifier.fillMaxSize().blur(34.dp),
          contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = ambientTintAlpha(if (lightStreamsPage) 0.58f else 0.46f, uiState.ambientTintPercent))))
      }
      Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(330.dp).hazeSource(episodeHazeState)) {
          AsyncImage(model = heroImage, contentDescription = episode.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
          Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.26f), Color.Black))))
          Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Text(
              "S${episode.seasonNumber} E${episode.episodeNumber}",
              color = Color.White.copy(alpha = 0.82f),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Black,
            )
            Text(
              episode.name,
              color = Color.White,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Black,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                detail.title,
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(999.dp))
                  .background(if (watched) Color(0xFF22C55E).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f))
                  .border(1.dp, if (watched) Color(0xFF22C55E).copy(alpha = 0.38f) else Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                  .clickable(onClick = onToggleWatched)
                  .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = if (watched) Color(0xFF22C55E) else Color(0xFFF2F2EE),
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  if (watched) "Watched" else "Mark watched",
                  color = if (watched) Color(0xFF22C55E) else Color(0xFFF2F2EE),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(episodeHazeState),
            contentPadding = PaddingValues(top = filterPanelHeight + 10.dp, bottom = 120.dp),
          ) {
            item {
              StreamListContent(
                uiState = uiState,
                selectedEpisode = episode,
                onPlayStream = onPlayStream,
                onDownloadStream = { stream -> onDownloadStream(stream, detail.title) },
                isStreamDownloadEligible = isStreamDownloadEligible,
                selectedProviderOverride = providerFilter,
                onProviderSelectedOverride = { providerFilter = it },
                showProviderFilters = false,
                horizontalPadding = 24.dp,
              )
            }
          }
          FrostedGlassSurface(
            modifier = Modifier.align(Alignment.TopCenter).zIndex(3f).fillMaxWidth().height(filterPanelHeight),
            shape = RectangleShape,
            hazeStateOverride = episodeHazeState,
            blurRadius = 68f,
            contentPadding = PaddingValues(top = 10.dp, bottom = 8.dp),
            tintAlpha = if (lightStreamsPage) 0.042f else 0.036f,
            borderAlpha = if (lightStreamsPage) 0.024f else 0f,
            baseAlpha = if (lightStreamsPage) 0.084f else 0.048f,
            fillColorOverride = if (lightStreamsPage) null else Color.White,
            showEdgeGradient = false,
          ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Sources", modifier = Modifier.padding(horizontal = 24.dp), color = streamsPageForeground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
              StreamProviderFilterRow(providers = providers, selectedProvider = providerFilter, onProviderSelected = { providerFilter = it }, horizontalPadding = 24.dp)
            }
          }
        }
      }
      GlassCircleButton(
        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 24.dp, top = 24.dp).zIndex(5f),
        hazeState = episodeHazeState,
        navigationHazeStyle = true,
        onClick = onBack,
      ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
      }
      GlassCircleButton(
        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 24.dp, top = 24.dp).zIndex(5f),
        hazeState = episodeHazeState,
        navigationHazeStyle = true,
        onClick = onReload,
      ) {
        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color.White)
      }
    }
  }
}
@Composable
private fun GlassEpisodeRow(episode: EpisodeItem, selected: Boolean, onLoadStreams: () -> Unit) {
  GlassScrim(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, if (selected) Color.White.copy(alpha = 0.42f) else Color.Transparent, RoundedCornerShape(28.dp))
      .clickable(onClick = onLoadStreams),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AsyncImage(
        model = episode.still,
        contentDescription = episode.name,
        modifier = Modifier.width(116.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)),
        contentScale = ContentScale.Crop,
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          "S${episode.seasonNumber.toString().padStart(2, '0')} \u2022 E${episode.episodeNumber.toString().padStart(2, '0')} \u2022 ${episode.name}",
          fontWeight = FontWeight.Bold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        episode.runtime?.takeIf { it > 0 }?.let {
          Text("${it}m", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f), style = MaterialTheme.typography.labelSmall)
        }
        episode.airDate?.let {
          Text(formatEpisodeAirDateLabel(it), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
          episode.overview.ifBlank { "Load sources for this episode." },
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
        )
      }
      TextButton(onClick = onLoadStreams) { Text(if (selected) "Selected" else "Streams") }
    }
  }
}

private fun Modifier.frostedBlur(blurRadius: Float, shape: Shape): Modifier =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    graphicsLayer {
      this.shape = shape
      clip = true
      renderEffect = AndroidRenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP).asComposeRenderEffect()
    }
  } else {
    clip(shape).blur((blurRadius / 3.2f).dp)
  }

@Composable
private fun FrostedGlassSurface(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(28.dp),
  hazeStateOverride: HazeState? = null,
  blurRadius: Float = 42f,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  tintAlpha: Float = 0.16f,
  borderAlpha: Float = 0.22f,
  baseAlpha: Float = 0.08f,
  fillColorOverride: Color? = null,
  showEdgeGradient: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val lightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val hazeTintColor = if (lightSurface) MaterialTheme.colorScheme.surface else Color(0xFF050608)
  val baseColor = fillColorOverride ?: hazeTintColor
  val topTint = fillColorOverride ?: if (lightSurface) MaterialTheme.colorScheme.surface else Color(0xFF141820)
  val middleTint = fillColorOverride ?: if (lightSurface) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF0B0E14)
  val bottomTint = fillColorOverride ?: if (lightSurface) MaterialTheme.colorScheme.surface else Color(0xFF020304)
  val edgeColor = MaterialTheme.colorScheme.onSurface

  val hazeState = hazeStateOverride ?: LocalStreamDekHazeState.current
  val hazeStyle = HazeMaterials.thin()
  val backdropEffect = if (hazeState == null) {
    Modifier
  } else {
    Modifier.hazeEffect(state = hazeState) {
      blurEffect {
        style = hazeStyle
        this.blurRadius = (blurRadius / 2.4f).dp
        colorEffects = listOf(HazeColorEffect.tint(hazeTintColor.copy(alpha = if (lightSurface) 0.28f else 0.18f)))
        noiseFactor = 0.035f
      }
    }
  }

  Box(
    modifier = modifier
      .clip(shape)
      .then(backdropEffect),
  ) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(baseColor.copy(alpha = if (hazeState == null) maxOf(baseAlpha, 0.32f) else baseAlpha)),
    )
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(
          Brush.linearGradient(
            colors = listOf(
              topTint.copy(alpha = tintAlpha * if (lightSurface) 0.90f else 0.55f),
              middleTint.copy(alpha = tintAlpha * if (lightSurface) 0.78f else 0.38f),
              bottomTint.copy(alpha = tintAlpha * if (lightSurface) 0.86f else 0.66f),
            ),
          ),
        ),
    )
    if (showEdgeGradient) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                edgeColor.copy(alpha = 0.045f),
                Color.Transparent,
                edgeColor.copy(alpha = if (lightSurface) 0.08f else 0.18f),
              ),
            ),
          ),
      )
    }
    if (borderAlpha > 0f) Box(modifier = Modifier.matchParentSize().border(1.dp, edgeColor.copy(alpha = borderAlpha * 0.68f), shape))
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding), content = content)
  }
}
@Composable
private fun GlassPill(text: String) {
  FrostedGlassSurface(
    shape = CircleShape,
    blurRadius = 28f,
    tintAlpha = 0.14f,
    borderAlpha = 0.16f,
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
  ) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
private fun GlassCircleButton(
  modifier: Modifier = Modifier,
  hazeState: HazeState? = null,
  navigationHazeStyle: Boolean = false,
  onClick: () -> Unit,
  content: @Composable BoxScope.() -> Unit,
) {
  val lightNavigation = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val interactionSource = remember { MutableInteractionSource() }
  val clickModifier = if (navigationHazeStyle) {
    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
  } else {
    Modifier.clickable(onClick = onClick)
  }
  FrostedGlassSurface(
    modifier = modifier.size(52.dp).then(clickModifier),
    shape = CircleShape,
    blurRadius = if (navigationHazeStyle) 68f else 56f,
    hazeStateOverride = hazeState,
    tintAlpha = if (navigationHazeStyle) {
      if (lightNavigation) 0.14f else 0.06f
    } else {
      0.12f
    },
    borderAlpha = if (navigationHazeStyle) {
      if (lightNavigation) 0.10f else 0.08f
    } else {
      0.38f
    },
    baseAlpha = if (navigationHazeStyle) {
      if (lightNavigation) 0.28f else 0.08f
    } else {
      0.08f
    },
    fillColorOverride = if (navigationHazeStyle && !lightNavigation) Color.White else null,
    showEdgeGradient = !navigationHazeStyle,
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
  }
}

@Composable
private fun GlassScrim(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  FrostedGlassSurface(
    modifier = modifier,
    shape = RoundedCornerShape(28.dp),
    blurRadius = 42f,
    contentPadding = PaddingValues(18.dp),
    tintAlpha = 0.15f,
    borderAlpha = 0.20f,
  ) {
    Column(modifier = Modifier.fillMaxWidth(), content = content)
  }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, containerAlpha: Float = 0.82f, content: @Composable ColumnScope.() -> Unit) {
  val tint = (0.10f + ((containerAlpha - 0.58f).coerceIn(0f, 0.30f) / 0.30f) * 0.08f).coerceIn(0.10f, 0.18f)
  FrostedGlassSurface(
    modifier = modifier,
    shape = RoundedCornerShape(28.dp),
    blurRadius = 42f,
    contentPadding = PaddingValues(18.dp),
    tintAlpha = tint,
    borderAlpha = 0.18f,
  ) {
    Column(modifier = Modifier.fillMaxWidth(), content = content)
  }
}
























































