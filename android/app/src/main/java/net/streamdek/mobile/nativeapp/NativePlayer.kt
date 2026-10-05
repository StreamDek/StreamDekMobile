package net.streamdek.mobile.nativeapp

import androidx.annotation.StringRes
import net.streamdek.mobile.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import net.streamdek.mobile.BuildConfig
import net.streamdek.mobile.MainActivity
import net.streamdek.mobile.mpv.MPVView
import net.streamdek.mobile.mpv.MpvTrackInfo
import net.streamdek.mobile.peer.PeerStreamService
import net.streamdek.mobile.peer.SwarmStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import android.widget.Toast

private enum class PlayerPanel { None, Sources, Audio, Subtitles, Speed, Engine, Info, Captions, Episodes }

/**
 * Whether the player draws its Live / VOD badge, and the in-player switch for it.
 *
 * Provided around the player by the app, from the Player settings value, so the switch in the live
 * controls and the one in Settings are the same setting. Visual only: a stream is still classified
 * as live or on-demand exactly as before, and everything that hangs off that is untouched.
 */
internal data class PlayerBadgeSetting(val visible: Boolean = true, val onToggle: () -> Unit = {})

internal val LocalPlayerBadgeSetting = androidx.compose.runtime.compositionLocalOf { PlayerBadgeSetting() }
private enum class PlayerAdjustmentKind { Brightness, Volume }


internal fun adjustedPlayerLevel(initial: Float, totalDragY: Float, playerHeight: Float): Float =
  (initial - (totalDragY / playerHeight.coerceAtLeast(1f)) * 1.5f).coerceIn(0f, 1f)
/** How many sources the player's Sources panel lists. The playing source is hoisted above this
 *  cut, so it is always listed however far down the unsorted list it started. */
private const val MAX_PLAYER_SOURCE_ROWS = 30
internal const val PLAYBACK_SEEK_BUFFERING_GRACE_MS = 8_000L

/** A seek-driven cache refill is expected and must not count as a playback stall. */
internal fun shouldReportPlaybackBuffering(
  isBuffering: Boolean,
  seekIssuedAtMs: Long,
  nowMs: Long,
): Boolean = isBuffering &&
  (seekIssuedAtMs <= 0L || nowMs - seekIssuedAtMs >= PLAYBACK_SEEK_BUFFERING_GRACE_MS)

internal enum class ActivePlaybackEngine { Media3, MPV, VLC }

// Engine names are product names, the same in every language, so they are constants rather than
// string resources.
internal const val EXOPLAYER_ENGINE_NAME = "ExoPlayer"
internal const val MPV_ENGINE_NAME = "mpv"
internal const val LIBVLC_ENGINE_NAME = "libVLC"

/** The engine's name as a viewer sees it, in the engine panel and the Stream Info panel. */
internal val ActivePlaybackEngine.displayName: String
  get() = when (this) {
    ActivePlaybackEngine.Media3 -> EXOPLAYER_ENGINE_NAME
    ActivePlaybackEngine.MPV -> MPV_ENGINE_NAME
    ActivePlaybackEngine.VLC -> LIBVLC_ENGINE_NAME
  }

/**
 * Which engine a session starts on. "Auto" starts on Media3, as it always has; libVLC is used only
 * when it has been chosen by name.
 */
internal fun initialPlaybackEngine(preference: String): ActivePlaybackEngine = when {
  preference.equals("MPV", ignoreCase = true) -> ActivePlaybackEngine.MPV
  preference.equals("VLC", ignoreCase = true) -> ActivePlaybackEngine.VLC
  else -> ActivePlaybackEngine.Media3
}
/** How often playback position is written back while a title is running. */
private const val PROGRESS_CHECKPOINT_SECONDS = 30.0

internal fun playerResumePosition(durationSec: Double, exactPositionSec: Double, percent: Double): Double {
  if (durationSec <= 0.0) return 0.0
  val requested = exactPositionSec.takeIf { it > 0.0 } ?: (durationSec * (percent / 100.0))
  return requested.coerceIn(0.0, (durationSec - 5.0).coerceAtLeast(0.0))
}

/** A live channel's source being started, as the loading screen names it. */
internal data class LiveSourceAttempt(val position: Int, val total: Int, val failingOver: Boolean)

/** How long a live source may take to show a picture before the next source is tried. */
internal const val LIVE_SOURCE_START_TIMEOUT_MS = 13_000L

/** The notices that announce a change of engine; shown for longer than a skip notice. */
private val PLAYER_ENGINE_NOTICES = setOf(
  R.string.player_engine_notice_fallback,
  R.string.player_engine_notice_headers,
  R.string.player_engine_notice_protected,
  R.string.player_engine_notice_dolby_vision,
)

internal fun shouldAutoFallbackToMpv(preference: String, activeEngine: ActivePlaybackEngine, fallbackUsed: Boolean): Boolean =
  preference.equals("Auto", ignoreCase = true) && activeEngine == ActivePlaybackEngine.Media3 && !fallbackUsed
internal fun nextUntriedPlaybackSource(
  availableStreams: List<AddonStream>,
  currentStream: AddonStream?,
  failedKeys: Set<String>,
): AddonStream? {
  val excluded = if (currentStream == null) failedKeys else failedKeys + playerStreamIdentity(currentStream)
  // Below the source that just failed, before anything above it.
  //
  // This used to take the first untried source in the whole list, which is the same thing only
  // while playback is working its own way down from the top - and that is the case every test
  // here covered. It is not the case when the viewer reaches down the list themselves: they pick
  // the eighth source, it drops, and every source above it is still "untried", so the first one
  // is what plays. Someone who scrolled past seven sources has said something about those seven,
  // and answering them with the one at the top is the opposite of what they asked for.
  //
  // The list above is still reached, but only once nothing below it is left. Playback continuing
  // matters more than the order it is arrived at.
  val currentIndex = currentStream
    ?.let { current -> availableStreams.indexOfFirst { playerStreamIdentity(it) == playerStreamIdentity(current) } }
    ?: -1
  return availableStreams.drop(currentIndex + 1).firstOrNull { playerStreamIdentity(it) !in excluded }
    ?: availableStreams.firstOrNull { playerStreamIdentity(it) !in excluded }
}
/**
 * The subtitle panel's tabs.
 *
 * The constant name is the *stored* value - it is what `subtitleDefaultSource` persists and what
 * [normalizeSubtitleDefaultSource] compares against - so it stays as it is, and the word on the
 * pill comes from a resource beside it. Drawing `tab.name` was why these four stayed English on a
 * translated phone.
 */
private enum class SubtitlePanelTab(@StringRes val labelRes: Int, val isSource: Boolean = true) {
  All(R.string.subtitle_tab_all),
  BuiltIn(R.string.subtitle_tab_built_in),
  Addons(R.string.subtitle_tab_addons),
  // Controls rather than places subtitles come from, so neither is remembered as the landing tab.
  Style(R.string.subtitle_tab_style, isSource = false),
  Timing(R.string.subtitle_tab_timing, isSource = false),
}
internal enum class ExternalSubtitleOrigin { BuiltIn, Addon }
internal fun externalSubtitleOrigin(sourceId: String): ExternalSubtitleOrigin =
  if (sourceId.startsWith("addon:")) ExternalSubtitleOrigin.Addon else ExternalSubtitleOrigin.BuiltIn

internal fun subtitleOriginVisible(tab: String, origin: ExternalSubtitleOrigin): Boolean = when (tab) {
  "All" -> true
  "BuiltIn" -> origin == ExternalSubtitleOrigin.BuiltIn
  "Addons" -> origin == ExternalSubtitleOrigin.Addon
  else -> false
}
internal fun subtitleSourceAllowsOrigin(selection: String?, origin: ExternalSubtitleOrigin): Boolean =
  subtitleOriginVisible(normalizeSubtitleDefaultSource(selection), origin)

internal fun preferredSubtitleLanguageAllowed(
  language: String?,
  primary: String?,
  secondary: String?,
  strict: Boolean,
): Boolean = !strict || Languages.normalize(language) in preferredSubtitleLanguages(primary.orEmpty(), secondary.orEmpty())
private data class ExternalSubtitle(
  val id: String,
  val language: String,
  val label: String,
  val url: String,
  val origin: ExternalSubtitleOrigin,
  val sourceName: String,
  val release: String? = null,
)
private data class SkipSegment(val type: String, val startSeconds: Double, val endSeconds: Double)

private fun episodeContext(session: PlayerSession): String? = if (session.seasonNumber != null && session.episodeNumber != null) {
  listOf("S${session.seasonNumber}", "E${session.episodeNumber}", session.episodeTitle?.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
} else null

@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
fun NativePlayerScreen(
  session: PlayerSession,
  /** Metadata-only phase before a playable URL exists; no decoder or media session is created. */
  resolving: Boolean = false,
  resolvingSourceLabel: String? = null,
  resolvingPeerHash: String? = null,
  onCancelResolving: () -> Unit = {},
  availableStreams: List<AddonStream>,
  handoffDevices: List<LinkedTvDevice> = emptyList(),
  onHandoff: suspend (LinkedTvDevice, Double) -> Result<PlaybackHandoffReceipt> = { _, _ -> Result.failure(IllegalStateException("Handoff is unavailable.")) },
  onBack: (Double) -> Unit,
  onScrobble: (String, Double) -> Unit,
  onProgressCheckpoint: (Double, Double) -> Unit,
  onSelectStream: (AddonStream, Double) -> Unit,
  onReloadStreams: () -> Unit,
  onPlaybackEnded: () -> Unit,
  onRecommendedPlaybackEnded: (MediaItem) -> Unit = { onPlaybackEnded() },
  recommendationWatchlistIds: Set<String> = emptySet(),
  onAddRecommendationToWatchlist: (MediaItem) -> Unit = {},
  nextEpisodeAvailability: NextEpisodeAvailability = NextEpisodeAvailability.None,
  nextEpisodeLabel: String? = null,
  nextEpisodeArtwork: String? = null,
  nextEpisodeLoading: Boolean = false,
  nextEpisodeLoadingLabel: String? = null,
  onPreviousEpisode: () -> Unit = {},
  onNextEpisode: () -> Unit = {},
  onPrepareNextEpisodeAtEnding: () -> Unit = {},
  onNextEpisodeAtEnding: () -> Unit = onNextEpisode,
  onUnairedEpisodeAtEnding: () -> Unit = {},
  isFavourite: Boolean = false,
  onToggleFavourite: () -> Unit = {},
  liveChannels: List<MediaItem> = emptyList(),
  liveChannelsLoading: Boolean = false,
  channelSwitchLoading: Boolean = false,
  channelSwitchLoadingLabel: String? = null,
  onCancelChannelSwitch: () -> Unit = {},
  onChannelSwitchPlaybackStarted: () -> Unit = {},
  channelSwitchFallbackAvailable: Boolean = false,
  favouriteChannels: List<MediaItem> = emptyList(),
  favouriteDrawerCards: Boolean = false,
  onSelectLiveChannel: (MediaItem) -> Unit = {},
  onToggleFavouriteDrawerCards: (Boolean) -> Unit = {},
  onClearFavourites: () -> Unit = {},
  downloadsEnabled: Boolean = false,
  onDownloadStream: (AddonStream) -> Unit = {},
  /** Adjustments made from the player's own controls, so they outlive this session. */
  onSubtitleTextSizeChange: (Int) -> Unit = {},
  onSubtitleVerticalOffsetChange: (Int) -> Unit = {},
  onSubtitleSourceChange: (String) -> Unit = {},
  onToggleSourceFavourite: (String) -> Unit = {},
  /** Normal or Minimal, switched from the player's header and kept as the Settings choice. */
  onPlayerControlLayoutChange: (String) -> Unit = {},
  /**
   * The series being played, for the in-player episode browser. Null for a film, a live channel
   * or anything else with no episodes, which is also what keeps the Episodes control off screen.
   */
  episodeBrowser: PlayerEpisodeBrowser? = null,
) {
  if ((AdultContentFilter.isBlockedItem(title = session.title) || AdultContentFilter.isBlocked(session.url, session.mediaId, session.sourceLabel)) || session.currentStream?.let(::streamIsAdult) == true) {
    val context = LocalContext.current
    LaunchedEffect(session) {
      android.widget.Toast.makeText(context, context.getString(R.string.content_safety_blocked), android.widget.Toast.LENGTH_LONG).show()
      onBack(0.0)
    }
    return
  }
  // Hoisted above the provisional/real-session split so the last swarm reading survives the URL
  // handoff instead of disappearing for one polling interval. Polling stops at the first frame.
  var peerSwarm by remember(resolvingPeerHash) { mutableStateOf<SwarmStats?>(null) }
  var pollPeerSwarm by remember(resolvingPeerHash) { mutableStateOf(!resolvingPeerHash.isNullOrBlank()) }
  LaunchedEffect(resolvingPeerHash, pollPeerSwarm) {
    if (resolvingPeerHash.isNullOrBlank() || !pollPeerSwarm) return@LaunchedEffect
    while (true) {
      peerSwarm = withContext(Dispatchers.IO) { runCatching { PeerStreamService.latestSwarmStats(resolvingPeerHash) }.getOrNull() }
      delay(700)
    }
  }
  if (resolving) {
    PlayerResolvingScreen(
      session = session,
      sourceLabel = resolvingSourceLabel,
      peerHash = resolvingPeerHash,
      swarm = peerSwarm,
      onBack = onCancelResolving,
    )
    return
  }
  val playerContext = LocalContext.current
  val playerScope = rememberCoroutineScope()
  // findActivity(), not a cast: ProvideAppLocale wraps LocalContext, and this activity is what
  // turns the phone landscape for playback and hands the orientation back afterwards.
  val activity = playerContext.findActivity()
  val audioManager = remember(playerContext) { playerContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
  // Manually added subtitle sources plus any installed addon that advertises the
  // Stremio "subtitles" resource.
  val userSubtitleSources = remember(session.url, playerContext, session.userSubtitleSources, session.addonSubtitleSources) {
    (session.userSubtitleSources.filter { it.enabled } + session.addonSubtitleSources)
      .distinctBy { it.baseUrl.trimEnd('/').lowercase() }
  }
  val surfaceInteractionSource = remember { MutableInteractionSource() }
  // A live channel switch reuses the same decoder/surface instance instead of tearing it down
  // (see ExoPlaybackView's background-prepare-then-swap and mpv's `loadfile ... replace`), so
  // the engine + view-reference state below is keyed by this stable identity rather than
  // session.url for live sessions - resetting them on every channel would lose the very
  // references that let the swap happen in place. Everything else (hasLoaded, error, duration,
  // currentTime) stays keyed by session.url, since those SHOULD reset per channel - that's what
  // drives the "switching..." overlay while the new channel's own load callback hasn't fired yet.
  val liveEngineKey = if (session.isLive) "live" else session.url
  val playbackIdentity = remember(session.mediaType, session.mediaId, session.seasonNumber, session.episodeNumber, session.url) {
    listOf(session.mediaType, session.mediaId, session.seasonNumber, session.episodeNumber, session.url).joinToString(":")
  }
  val playback = remember(playbackIdentity) { PlayerPlaybackState() }
  // One slot for every piece of per-source state; see [PlayerSourceState].
  val source = remember(session.url) { PlayerSourceState() }
  var isPaused by playback.isPaused
  var pausedForAudioFocus by playback.pausedForAudioFocus
  var currentTime by playback.currentTime
  var duration by playback.duration
  var seekIssuedAtMs by playback.seekIssuedAtMs
  var error by source.error
  var hasLoaded by playback.hasLoaded
  var playerView by remember(liveEngineKey) { mutableStateOf<MPVView?>(null) }
  var exoPlayerView by remember(liveEngineKey) { mutableStateOf<ExoPlaybackView?>(null) }
  var vlcPlayerView by remember(liveEngineKey) { mutableStateOf<VlcPlaybackView?>(null) }
  // What is known about this source before it is opened that rules libVLC out: request headers it
  // cannot send, or ClearKey protection. Such a source is not tried on libVLC at all.
  val vlcBlocker = libVlcBlocker(
    session.requestHeaders,
    clearKeyProtected = session.drmLicenseType.equals("clearkey", ignoreCase = true) && session.drmClearKeys.isNotEmpty(),
  )
  var activeEngine by remember(liveEngineKey, session.playerEngine, vlcBlocker) { mutableStateOf(routedPlaybackEngine(session.playerEngine, vlcBlocker)) }
  var autoFallbackUsed by remember(liveEngineKey, session.playerEngine, vlcBlocker) { mutableStateOf(false) }
  // Which engines this source has been on and which could not play it, so none is tried twice.
  val engineTrail = remember(session.url, session.playerEngine, vlcBlocker) { PlaybackEngineTrail(activeEngine) }
  var pendingEngineResumeSeconds by source.pendingEngineResumeSeconds
  var reloadedInPlace by source.reloadedInPlace
  // Start every source in the edge-to-edge Full screen scale. The viewer can still cycle to
  // Stretch or Normal afterwards, but a new video must never begin letterboxed or distorted.
  val resizeModeState = rememberSaveable(session.url) { mutableStateOf("cover") }
  var resizeMode by resizeModeState
  val customZoomState = rememberSaveable(session.url) { mutableFloatStateOf(1f) }
  var customZoom by customZoomState
  val playbackSpeedState = rememberSaveable(session.url) { mutableFloatStateOf(1f) }
  var playbackSpeed by playbackSpeedState
  // The one place the screen decides which engine it is talking to; see [PlaybackEngineControls].
  // Resolved at each call rather than held, because these helpers are captured by callbacks that
  // outlive the composition they were created in, and the view behind an engine changes.
  fun activeControls(): PlaybackEngineControls? = when (activeEngine) {
    ActivePlaybackEngine.Media3 -> exoPlayerView?.asEngineControls()
    ActivePlaybackEngine.MPV -> playerView?.asEngineControls()
    ActivePlaybackEngine.VLC -> vlcPlayerView?.asEngineControls()
  }
  fun activeAddSubtitle(path: String, language: String?) { activeControls()?.addSubtitleFile(path, language) }
  fun activeReload() { activeControls()?.reloadSource() }
  fun activeSetPaused(paused: Boolean) { activeControls()?.setPaused(paused) }
  fun activeSeekTo(seconds: Double) {
    seekIssuedAtMs = android.os.SystemClock.elapsedRealtime()
    activeControls()?.seekTo(seconds)
  }
  fun activeSetAudioTrack(id: Int) { activeControls()?.setAudioTrack(id) }
  fun activeDisableSubtitleTrack() { activeControls()?.disableSubtitleTrack() }
  fun activeSetSubtitleTrack(id: Int) { activeControls()?.setSubtitleTrack(id) }
  fun activeSetSubtitleFontSize(size: Int) { activeControls()?.setSubtitleFontSize(size) }
  fun activeSetSubtitlePosition(position: Int) { activeControls()?.setSubtitlePosition(position) }
  fun activeSetSubtitleBackgroundColor(color: String) { activeControls()?.setSubtitleBackgroundColor(color) }
  fun activeSetSubtitleOutline(enabled: Boolean, color: String) { activeControls()?.setSubtitleOutline(enabled, color) }
  fun activeSetSubtitleBold(bold: Boolean) { activeControls()?.setSubtitleBold(bold) }
  fun activeSetSubtitleDelay(seconds: Double) { activeControls()?.setSubtitleDelay(seconds) }
  fun activeSetAudioDelay(seconds: Double) { activeControls()?.setAudioDelay(seconds) }
  // mpv moves its audio in every output mode; ExoPlayer cannot while tunneled. See [AudioDelayControl].
  fun activeAudioDelaySupported(): Boolean = activeControls()?.audioDelaySupported() != false
  fun activeSetSpeed(speed: Double) { activeControls()?.setSpeed(speed) }

  val audioFocusListener = remember(playbackIdentity) {
    AudioManager.OnAudioFocusChangeListener { change ->
      when (change) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          if (!isPaused) pausedForAudioFocus = true
          isPaused = true
        }
        // Notification sounds normally request ducking rather than exclusive audio focus. Keep
        // the video running for that brief sound; Android may lower its audio without turning an
        // ordinary notification into a visible pause/resume cycle.
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit
        AudioManager.AUDIOFOCUS_GAIN -> if (pausedForAudioFocus) {
          pausedForAudioFocus = false
          isPaused = false
        }
      }
    }
  }
  val audioFocusRequest = remember(audioFocusListener) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build(),
        )
        .setOnAudioFocusChangeListener(audioFocusListener, Handler(Looper.getMainLooper()))
        .setWillPauseWhenDucked(false)
        .build()
    } else null
  }

  fun requestPlayerAudioFocus(): Boolean {
    val manager = audioManager ?: return true
    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
      manager.requestAudioFocus(audioFocusRequest)
    } else {
      @Suppress("DEPRECATION")
      manager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }

  fun abandonPlayerAudioFocus() {
    val manager = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
      manager.abandonAudioFocusRequest(audioFocusRequest)
    } else {
      @Suppress("DEPRECATION")
      manager.abandonAudioFocus(audioFocusListener)
    }
  }

  val audioBecomingNoisyReceiver = remember(playbackIdentity) {
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
          pausedForAudioFocus = false
          isPaused = true
        }
      }
    }
  }
  DisposableEffect(playerContext, audioBecomingNoisyReceiver) {
    val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      playerContext.registerReceiver(audioBecomingNoisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      @Suppress("DEPRECATION")
      playerContext.registerReceiver(audioBecomingNoisyReceiver, filter)
    }
    onDispose { runCatching { playerContext.unregisterReceiver(audioBecomingNoisyReceiver) } }
  }

  val nativeMediaSession = remember(playerContext, playbackIdentity) {
    MediaSession(playerContext, "StreamDekPlayback")
  }
  DisposableEffect(nativeMediaSession) {
    nativeMediaSession.setCallback(object : MediaSession.Callback() {
      override fun onPlay() {
        if (requestPlayerAudioFocus()) {
          pausedForAudioFocus = false
          isPaused = false
        }
      }
      override fun onPause() { pausedForAudioFocus = false; isPaused = true }
      override fun onStop() { pausedForAudioFocus = false; isPaused = true }
      override fun onSeekTo(pos: Long) {
        currentTime = (pos / 1000.0).coerceIn(0.0, duration.takeIf { it > 0.0 } ?: Double.MAX_VALUE)
        activeSeekTo(currentTime)
      }
    }, Handler(Looper.getMainLooper()))
    nativeMediaSession.isActive = true
    onDispose {
      nativeMediaSession.isActive = false
      nativeMediaSession.release()
      abandonPlayerAudioFocus()
    }
  }
  LaunchedEffect(session.title, session.seasonNumber, session.episodeNumber) {
    nativeMediaSession.setMetadata(
      MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, session.title)
        .putString(
          MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
          if (session.seasonNumber != null && session.episodeNumber != null) "S${session.seasonNumber} E${session.episodeNumber}" else null,
        )
        .build(),
    )
  }
  LaunchedEffect(isPaused, currentTime, playbackSpeed) {
    nativeMediaSession.setPlaybackState(
      PlaybackState.Builder()
        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP)
        .setState(
          if (isPaused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
          (currentTime * 1000.0).toLong(),
          playbackSpeed,
        )
        .build(),
    )
  }
  LaunchedEffect(playbackIdentity) {
    if (!requestPlayerAudioFocus()) isPaused = true
  }
  val activePanelState = rememberSaveable(session.url) { mutableStateOf(PlayerPanel.None) }
  var activePanel by activePanelState
  var playbackStats by source.playbackStats
  val subtitleDelayState = rememberSaveable(session.url) { mutableFloatStateOf(0f) }
  var subtitleDelay by subtitleDelayState
  // Starts from this device's default (Settings > Audio) and is then this video's own: a correction
  // made for one stream is not carried into the next, where it would put a synced one out.
  val audioDelayState = rememberSaveable(session.url) { mutableFloatStateOf(AudioSyncOptions.defaultDelaySeconds.toFloat()) }
  val audioDelay by audioDelayState
  // Seeded from settings rather than from a constant, and keyed on the setting rather than on the
  // stream, so a size or colour chosen once carries into the next episode instead of resetting.
  val subtitleSizeState = remember(session.subtitleTextSize) { mutableIntStateOf(session.subtitleTextSize) }
  var subtitleSize by subtitleSizeState
  val subtitlePositionState = remember(session.subtitleVerticalOffset) { mutableIntStateOf(session.subtitleVerticalOffset) }
  var subtitlePosition by subtitlePositionState
  var subtitleColor by remember(session.subtitleTextColor) { mutableStateOf(session.subtitleTextColor) }
  val selectedAudioTrackIdState = source.selectedAudioTrackId
  var selectedAudioTrackId by selectedAudioTrackIdState
  val selectedSubtitleTrackIdState = source.selectedSubtitleTrackId
  var selectedSubtitleTrackId by selectedSubtitleTrackIdState
  val preferredAudioTrackKeyState = source.preferredAudioTrackKey
  var preferredAudioTrackKey by preferredAudioTrackKeyState
  val preferredSubtitleTrackKeyState = source.preferredSubtitleTrackKey
  var preferredSubtitleTrackKey by preferredSubtitleTrackKeyState
  val subtitleTabState = remember(session.subtitleDefaultSource) {
    mutableStateOf(
      SubtitlePanelTab.entries.firstOrNull { it.name == normalizeSubtitleDefaultSource(session.subtitleDefaultSource) }
        ?: SubtitlePanelTab.All,
    )
  }
  var subtitleTab by subtitleTabState
  val configuredSubtitleSource = normalizeSubtitleDefaultSource(session.subtitleDefaultSource)
  val availableSubtitleTabs = remember(configuredSubtitleSource) {
    when (configuredSubtitleSource) {
      "BuiltIn" -> listOf(SubtitlePanelTab.BuiltIn, SubtitlePanelTab.Style, SubtitlePanelTab.Timing)
      "Addons" -> listOf(SubtitlePanelTab.Addons, SubtitlePanelTab.Style, SubtitlePanelTab.Timing)
      else -> SubtitlePanelTab.entries
    }
  }
  val subtitleDisabledByUserState = source.subtitleDisabledByUser
  var subtitleDisabledByUser by subtitleDisabledByUserState
  /** Shown in the add-on tab when a chosen subtitle could not be loaded at all. */
  val subtitleErrorMessageState = source.subtitleErrorMessage
  var subtitleErrorMessage by subtitleErrorMessageState
  val userPickedAudioState = source.userPickedAudio
  var userPickedAudio by userPickedAudioState
  val userPickedSubtitleState = source.userPickedSubtitle
  var userPickedSubtitle by userPickedSubtitleState
  var externalSubtitles by source.externalSubtitles
  val selectedExternalSubtitleIdState = source.selectedExternalSubtitleId
  var selectedExternalSubtitleId by selectedExternalSubtitleIdState
  var externalSubtitleNeedsReapply by source.externalSubtitleNeedsReapply
  var subtitlesLoading by source.subtitlesLoading
  var skipSegments by source.skipSegments
  var autoSkippedSegments by playback.autoSkippedSegments
  var autoSkipNotice by playback.autoSkipNotice
  val audioTracks = source.audioTracks
  val subtitleTracks = source.subtitleTracks
  var scrobbleStarted by source.scrobbleStarted
  var showControls by source.showControls
  val controlsLockedState = rememberSaveable(session.url) { mutableStateOf(false) }
  var controlsLocked by controlsLockedState
  var showUnlockControl by source.showUnlockControl
  var unlockActivityVersion by source.unlockActivityVersion
  var controlActivityVersion by source.controlActivityVersion
  var playbackEnded by playback.playbackEnded
  var completionDispatched by playback.completionDispatched
  var endOfPlaybackPhase by remember(playbackIdentity) { mutableStateOf(EndOfPlaybackPhase.Idle) }
  var upNextCountdown by remember(playbackIdentity) { mutableStateOf<Int?>(null) }
  var nextEpisodePreparationRequested by remember(playbackIdentity) { mutableStateOf(false) }
  var transitionClaimed by remember(playbackIdentity) { mutableStateOf(false) }
  var liveReconnectVersion by source.liveReconnectVersion
  var liveStalled by source.liveStalled
  var liveRetryAttempts by source.liveRetryAttempts
  var lastLiveRetryAtMs by source.lastLiveRetryAtMs
  var showPausedInfo by source.showPausedInfo
  /** Set while a press-and-hold is boosting playback; cleared the moment the finger lifts. */
  var speedBoostActive by source.speedBoostActive
  /**
   * Where a horizontal scrub currently points, in seconds. Non-null only while the finger is
   * down: the seek is committed on release so a long drag is one seek, not hundreds.
   */
  var scrubTargetSeconds by source.scrubTargetSeconds
  /**
   * Lifting after a hold or a scrub is still an "up" as far as the tap handler is concerned, and
   * without this the controls would flash on every time one of those gestures ended.
   */
  var suppressNextTap by source.suppressNextTap

  // Boost is applied on top of whatever speed the viewer chose in the speed panel, and putting it
  // in one effect means release always restores that speed — including if the finger is still
  // down when the session changes underneath it.
  LaunchedEffect(speedBoostActive, playbackSpeed, activeEngine) {
    val target = if (speedBoostActive) {
      (playbackSpeed * session.holdToSpeedMultiplier).coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
    } else {
      playbackSpeed
    }
    activeSetSpeed(target.toDouble())
  }
  // The favourites list is matched on channel id, the same identity toggleFavouriteChannel
  // stores and removes by, so the tray's stars agree with the header star and the drawer.
  val favouriteChannelIds = remember(favouriteChannels) { favouriteChannels.mapTo(HashSet()) { it.id } }
  var showLiveChannels by source.showLiveChannels
  // Keyed on the live identity rather than session.url so the choice carries across a channel
  // switch, and on the setting so changing it from Settings re-seeds a session already playing.
  val showLiveProgressState = remember(liveEngineKey, session.showLiveProgressBar) { mutableStateOf(session.showLiveProgressBar) }
  var showLiveProgress by showLiveProgressState
  var showFavouriteDrawer by source.showFavouriteDrawer
  var pendingChannelSelection by source.pendingChannelSelection
  var showChannelSwipeCue by source.showChannelSwipeCue
  var didApplyResume by playback.didApplyResume
  var lastCheckpointSecond by playback.lastCheckpointSecond
  var slowLoadHintVisible by source.slowLoadHintVisible
  var avMismatchFallbackTried by source.avMismatchFallbackTried
  var loadedVideoWidth by source.loadedVideoWidth
  var loadedVideoHeight by source.loadedVideoHeight
  val failedSourceKeys = remember(session.mediaId, session.seasonNumber, session.episodeNumber) { mutableStateListOf<String>() }
  var recentPlaybackStalls by playback.recentPlaybackStalls
  var smartSwitchCandidate by playback.smartSwitchCandidate
  // Keep the grace period when the URL changes underneath this same movie/episode. Keying this to
  // playbackIdentity reset it on every source switch and allowed the replacement to be judged
  // immediately, before it had any chance to establish stable playback.
  val smartSwitchCooldownUntilState = remember(session.mediaId, session.seasonNumber, session.episodeNumber) { mutableStateOf(0L) }
  var smartSwitchCooldownUntil by smartSwitchCooldownUntilState
  var handoffPickerVisible by source.handoffPickerVisible
  var handoffLoading by source.handoffLoading
  var handoffError by source.handoffError
  val adjustmentKindState = remember { mutableStateOf<PlayerAdjustmentKind?>(null) }
  var adjustmentKind by adjustmentKindState
  val adjustmentLevelState = remember { mutableFloatStateOf(0f) }
  var adjustmentLevel by adjustmentLevelState
  var adjustmentFeedbackVersion by remember { mutableIntStateOf(0) }
  fun currentWindowBrightness(): Float {
    val windowValue = activity?.window?.attributes?.screenBrightness ?: -1f
    if (windowValue in 0f..1f) return windowValue.coerceIn(0.02f, 1f)
    return runCatching {
      Settings.System.getInt(playerContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f).coerceIn(0.02f, 1f)
  }
  fun currentMediaVolume(): Float {
    val manager = audioManager ?: return 0.5f
    val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum.toFloat()
  }
  fun applyBrightness(level: Float) {
    val target = level.coerceIn(0.02f, 1f)
    activity?.window?.let { window ->
      val attributes = window.attributes
      attributes.screenBrightness = target
      window.attributes = attributes
    }
    adjustmentKind = PlayerAdjustmentKind.Brightness
    adjustmentLevel = target
    adjustmentFeedbackVersion += 1
  }
  fun applyMediaVolume(level: Float) {
    val manager = audioManager ?: return
    val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val target = (level.coerceIn(0f, 1f) * maximum).roundToInt().coerceIn(0, maximum)
    manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    adjustmentKind = PlayerAdjustmentKind.Volume
    adjustmentLevel = target.toFloat() / maximum.toFloat()
    adjustmentFeedbackVersion += 1
  }
  fun progressPercent(): Double =
    if (duration > 0.0) ((currentTime / duration) * 100.0).coerceIn(0.0, 100.0) else 0.0

  val isLoading = nextEpisodeLoading || (!hasLoaded && error.isNullOrBlank())
  val liveSourceAttempt: LiveSourceAttempt? = if (!session.isLive || availableStreams.size < 2) null else {
    val index = availableStreams.indexOfFirst { playerStreamIdentity(it) == playerStreamIdentity(session.currentStream) }
    if (index < 0) null else LiveSourceAttempt(index + 1, availableStreams.size, failingOver = failedSourceKeys.isNotEmpty())
  }
  LaunchedEffect(isLoading) {
    if (!isLoading) pollPeerSwarm = false
  }
  val currentProgressPercent = progressPercent()
  val structuralOutro = skipSegments.firstOrNull { it.type == "outro" }
  val activeSkipSegment = skipSegments.firstOrNull { segment ->
    currentTime >= segment.startSeconds && currentTime < segment.endSeconds && when (segment.type) {
      "intro" -> session.skipIntroEnabled
      "recap" -> session.skipRecapEnabled
      "outro" -> session.skipEndingEnabled || (session.mediaType == "tv" && session.autoPlayNextEpisode)
      else -> false
    }
  }
  val meaningfulEnd = AdaptiveEndOfPlaybackTrigger.estimate(
    durationSec = duration,
    timing = RecommendationTiming.fromKey(session.recommendationTiming),
    structuralOutroStartSec = structuralOutro?.startSeconds,
    structuralOutroEndSec = structuralOutro?.endSeconds,
  )
  val nextEpisodeExists = nextEpisodeAvailability != NextEpisodeAvailability.None
  val confirmedAiredNextEpisode = nextEpisodeAvailability == NextEpisodeAvailability.Aired
  val upNextDecision = EndOfPlaybackCoordinator.decide(
    nextEpisodeId = if (nextEpisodeExists) nextEpisodeLabel ?: "next-episode" else null,
    currentMediaId = session.mediaId,
    recommendationIds = (if (session.mediaType == "tv" && nextEpisodeAvailability != NextEpisodeAvailability.Unaired) emptyList() else session.recommendations)
      .filterNot { it.title.trim().equals(session.title.trim(), ignoreCase = true) }
      .map { it.id },
    recommendationLimit = session.recommendationItemCount,
  )
  val recommendationById = remember(session.recommendations) { session.recommendations.associateBy { it.id } }
  val primaryRecommendation = upNextDecision?.takeIf { it.primaryKind == UpNextKind.Recommendation }?.primaryId?.let(recommendationById::get)
  val alternativeRecommendations = upNextDecision?.alternativeIds.orEmpty().mapNotNull(recommendationById::get)
  val nextEpisodeActionAvailable = activeSkipSegment?.type == "outro" && confirmedAiredNextEpisode &&
    AdaptiveEndOfPlaybackTrigger.isReached(currentTime, meaningfulEnd)

  LaunchedEffect(currentTime, meaningfulEnd, upNextDecision, session.endOfPlaybackRecommendationsEnabled, isLoading) {
    if (session.isLive || isLoading || transitionClaimed || endOfPlaybackPhase == EndOfPlaybackPhase.Dismissed) return@LaunchedEffect
    val allowed = upNextDecision?.primaryKind == UpNextKind.NextEpisode || session.endOfPlaybackRecommendationsEnabled
    if (allowed && upNextDecision != null && AdaptiveEndOfPlaybackTrigger.isReached(currentTime, meaningfulEnd)) {
      if (endOfPlaybackPhase == EndOfPlaybackPhase.Idle || endOfPlaybackPhase == EndOfPlaybackPhase.Armed) {
        upNextCountdown = if (upNextDecision.primaryKind == UpNextKind.NextEpisode && confirmedAiredNextEpisode && session.autoPlayNextEpisode) {
          AdaptiveEndOfPlaybackTrigger.countdownSeconds(currentTime, meaningfulEnd)
        } else null
        if (upNextDecision.primaryKind == UpNextKind.NextEpisode && confirmedAiredNextEpisode && !nextEpisodePreparationRequested) {
          nextEpisodePreparationRequested = true
          onPrepareNextEpisodeAtEnding()
        }
        endOfPlaybackPhase = if (upNextCountdown != null) EndOfPlaybackPhase.Countdown else EndOfPlaybackPhase.Presented
      }
      showControls = false
    } else if (endOfPlaybackPhase == EndOfPlaybackPhase.Idle) {
      endOfPlaybackPhase = EndOfPlaybackPhase.Armed
    }
  }

  LaunchedEffect(currentTime, meaningfulEnd?.triggerPositionSec) {
    if (EndOfPlaybackCoordinator.shouldResetAfterSeek(currentTime, meaningfulEnd?.triggerPositionSec) &&
      endOfPlaybackPhase in setOf(EndOfPlaybackPhase.Presented, EndOfPlaybackPhase.Countdown, EndOfPlaybackPhase.Dismissed)
    ) {
      upNextCountdown = null
      nextEpisodePreparationRequested = false
      endOfPlaybackPhase = EndOfPlaybackPhase.Armed
    }
  }

  fun claimTransition(block: () -> Unit) {
    if (transitionClaimed) return
    transitionClaimed = true
    upNextCountdown = null
    endOfPlaybackPhase = EndOfPlaybackPhase.Transitioning
    showControls = false
    isPaused = true
    block()
  }

  LaunchedEffect(currentTime, meaningfulEnd, endOfPlaybackPhase, session.autoPlayNextEpisode, nextEpisodeAvailability) {
    if (endOfPlaybackPhase != EndOfPlaybackPhase.Countdown || !session.autoPlayNextEpisode || !confirmedAiredNextEpisode) return@LaunchedEffect
    val remaining = AdaptiveEndOfPlaybackTrigger.countdownSeconds(currentTime, meaningfulEnd) ?: return@LaunchedEffect
    upNextCountdown = remaining
    if (remaining == 0 && AdaptiveEndOfPlaybackTrigger.isIntendedEndReached(currentTime, meaningfulEnd)) {
      claimTransition(onNextEpisodeAtEnding)
    }
  }

  fun closePlayer() = onBack(progressPercent())
  fun finishPlayback() {
    if (completionDispatched) return
    completionDispatched = true
    if (session.isLive) return
    isPaused = true
    onScrobble("stop", 100.0)
    if (transitionClaimed) return
    if (confirmedAiredNextEpisode && session.autoPlayNextEpisode) {
      claimTransition(onNextEpisodeAtEnding)
      return
    }
    endOfPlaybackPhase = EndOfPlaybackPhase.Completed
    playbackEnded = true
  }
  fun keepControlsVisible() {
    showControls = true
    controlActivityVersion += 1
  }

  // Selecting a channel from the tray/favourites drawer both starts that drawer's exit
  // animation (closing it) and swaps session.url, which resets nearly every remember(session.url)
  // state in this whole screen. Doing both in the same click/frame raced the drawer's
  // AnimatedVisibility exit-transition measurement against the session-wide recomposition and
  // crashed with "LayoutNode should be attached to an owner" on large channel lists. Deferring
  // the actual selection to the next frame lets the drawer finish starting its close animation
  // on its own, uninterrupted composition pass first.
  LaunchedEffect(pendingChannelSelection) {
    val channel = pendingChannelSelection ?: return@LaunchedEffect
    pendingChannelSelection = null
    onSelectLiveChannel(channel)
  }

  BackHandler {
    when {
      showFavouriteDrawer -> showFavouriteDrawer = false
      showLiveChannels -> showLiveChannels = false
      channelSwitchLoading -> onCancelChannelSwitch()
      session.isLive && channelSwitchFallbackAvailable && !error.isNullOrBlank() -> onCancelChannelSwitch()
      // Back out of the episode browser to the picture, not out of the player.
      activePanel == PlayerPanel.Episodes -> activePanel = PlayerPanel.None
      !controlsLocked -> closePlayer()
    }
  }

  PlayerBehaviourEffects(
    session = session,
    source = source,
    playback = playback,
    playbackIdentity = playbackIdentity,
    playerContext = playerContext,
    activity = activity,
    activeEngine = activeEngine,
    playerView = playerView,
    exoPlayerView = exoPlayerView,
    vlcPlayerView = vlcPlayerView,
    isLoading = isLoading,
    activeSkipSegment = activeSkipSegment,
    nextEpisodeActionAvailable = nextEpisodeActionAvailable,
    userSubtitleSources = userSubtitleSources,
    adjustmentFeedbackVersion = adjustmentFeedbackVersion,
    activePanelState = activePanelState,
    controlsLockedState = controlsLockedState,
    subtitleDisabledByUserState = subtitleDisabledByUserState,
    selectedSubtitleTrackIdState = selectedSubtitleTrackIdState,
    selectedExternalSubtitleIdState = selectedExternalSubtitleIdState,
    userPickedSubtitleState = userPickedSubtitleState,
    adjustmentKindState = adjustmentKindState,
    activeSeekTo = ::activeSeekTo,
    activeAddSubtitle = ::activeAddSubtitle,
    progressPercent = ::progressPercent,
    onScrobble = onScrobble,
    onPlaybackEnded = onPlaybackEnded,
  )
  // Shared budget check for every live-feed retry trigger (stall watchdog below AND mpv's own
  // error callback). Both used to bump liveReconnectVersion directly, but only the watchdog
  // respected the 5-attempt cap — a stream that mpv rejects outright (bad URL, wrong protocol,
  // missing required headers) errors out again within milliseconds of every reload, so the
  // uncapped path re-triggered itself continuously with only a ~300ms backoff: exactly what
  // "keeps reloading and reloading" looks like. Routing both triggers through this one function
  // means every retry — however it was triggered — counts against the same budget before
  // failing over to the next source or giving up.
  /**
   * Moves a live channel on to a source it has not tried yet. Tried sources are remembered for the
   * channel, so a channel with three dead sources walks through them once instead of bouncing
   * between the first two. False when every source has been tried.
   */
  fun failOverToNextLiveSource(reason: String): Boolean {
    session.currentStream?.let { current ->
      val key = playerStreamIdentity(current)
      if (key !in failedSourceKeys) failedSourceKeys.add(key)
    }
    val nextSource = nextUntriedPlaybackSource(availableStreams, session.currentStream, failedSourceKeys.toSet()) ?: run {
      android.util.Log.w("StreamDekLivePlayer", "$reason for ${session.url}; every source has been tried")
      return false
    }
    android.util.Log.w("StreamDekLivePlayer", "$reason for ${session.url}; trying the next source")
    // No error line: the loading screen that follows says which source is being tried.
    onSelectStream(nextSource, 0.0)
    return true
  }

  fun retryOrFailoverLiveFeed() {
    val now = System.currentTimeMillis()
    // A long gap since the previous retry means the feed recovered — reset the budget.
    if (now - lastLiveRetryAtMs > 60_000L) liveRetryAttempts = 0
    // A source that has never played gets one reconnect before the next source is tried: one that
    // is rejected outright fails the same way every time, and the viewer is left watching a spinner.
    val budget = if (hasLoaded) 5 else 1
    if (liveRetryAttempts >= budget) {
      android.util.Log.w("StreamDekLivePlayer", "retry budget exhausted for ${session.url}, looking for a next source")
      if (!failOverToNextLiveSource("retry budget exhausted")) {
        error = "This live feed keeps stalling. Tap retry or choose another source."
      }
      return
    }
    lastLiveRetryAtMs = now
    liveRetryAttempts += 1
    android.util.Log.d("StreamDekLivePlayer", "retry attempt $liveRetryAttempts for ${session.url}")
    liveReconnectVersion += 1
  }

  LaunchedEffect(liveReconnectVersion) {
    if (session.isLive && liveReconnectVersion > 0) {
      // Back off a little on repeated attempts so a dead feed isn't hammered.
      delay((300L + (liveRetryAttempts - 1).coerceAtLeast(0) * 700L).coerceAtMost(3_000L))
      error = null
      liveStalled = false
      activeReload()
      activeSetPaused(false)
    }
  }

  // Only treat mpv's paused-for-cache signal as a live stall. Many healthy HLS/DASH live
  // manifests expose a static or discontinuous time-pos, so using a quiet time-pos as failure
  // evidence caused StreamDek itself to reload working CNCVerse feeds every 20 seconds.
  // Real demux/decode failures still flow through onError/onEnd below.
  LaunchedEffect(session.url, session.isLive, isPaused, hasLoaded, liveStalled, liveReconnectVersion, error) {
    if (!session.isLive || isPaused || !hasLoaded || !liveStalled || !error.isNullOrBlank()) return@LaunchedEffect
    delay(20_000L)
    if (liveStalled) retryOrFailoverLiveFeed()
  }


  // A live source that connects but never produces a picture raises no error at all, so nothing
  // above would ever move on from it. Past the start window, the next source is tried. Restarted by
  // every reconnect and engine swap, each of which is a fresh attempt at starting.
  LaunchedEffect(session.url, session.isLive, hasLoaded, activeEngine, liveReconnectVersion) {
    if (!session.isLive || hasLoaded) return@LaunchedEffect
    delay(LIVE_SOURCE_START_TIMEOUT_MS)
    if (hasLoaded) return@LaunchedEffect
    if (!failOverToNextLiveSource("no picture after ${LIVE_SOURCE_START_TIMEOUT_MS / 1000}s") && error.isNullOrBlank()) {
      error = "This channel isn't responding. Tap retry or choose another source."
    }
  }

  // A live stream that's simply slow to start looks identical to a stuck one until the
  // 20s stall watchdog or 5-attempt retry budget above kicks in. Give the user an earlier,
  // reassuring signal instead of leaving the generic spinner up the whole time - this only
  // fires while nothing has errored, i.e. a connection was actually established.
  LaunchedEffect(session.url, session.isLive, hasLoaded, error) {
    slowLoadHintVisible = false
    if (session.isLive && !hasLoaded && error.isNullOrBlank()) {
      delay(6_000)
      if (!hasLoaded && error.isNullOrBlank()) slowLoadHintVisible = true
    }
  }

  val playerLoadCallback: (Double, Int, Int) -> Unit = { loadedDuration, width, height ->
    hasLoaded = true
    loadedVideoWidth = width
    loadedVideoHeight = height
    // Every live start, not only a channel switch's: the source that worked is remembered either way.
    if (session.isLive) onChannelSwitchPlaybackStarted()
    duration = loadedDuration
    error = null
    if (session.autoLoadSubtitles && !userPickedSubtitle && !subtitleDisabledByUser && selectedSubtitleTrackId == null &&
      subtitleSourceAllowsOrigin(session.subtitleDefaultSource, ExternalSubtitleOrigin.BuiltIn)
    ) {
      val preferredLanguages = preferredSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
      subtitleTracks.firstOrNull { normalizeSubtitleLanguage(it.language ?: it.title) in preferredLanguages }?.let { preferredSubtitle ->
        selectedSubtitleTrackId = preferredSubtitle.id
        activeSetSubtitleTrack(preferredSubtitle.id)
      }
    }
    if (pendingEngineResumeSeconds > 0.0 && loadedDuration > 0.0) {
      val resumeAt = pendingEngineResumeSeconds.coerceIn(0.0, (loadedDuration - 2.0).coerceAtLeast(0.0))
      pendingEngineResumeSeconds = 0.0
      activeSeekTo(resumeAt)
      currentTime = resumeAt
    } else if (!didApplyResume && (session.resumePositionSec > 0.0 || session.resumePercent > 0.0) && loadedDuration > 0.0) {
      val requested = session.resumePositionSec.takeIf { it > 0.0 }
        ?: (loadedDuration * (session.resumePercent / 100.0))
      val resumeAt = playerResumePosition(loadedDuration, session.resumePositionSec, session.resumePercent)
      activeSeekTo(resumeAt)
      currentTime = resumeAt
      didApplyResume = true
      Log.i("StreamDekPlayer", "[Player] content=${session.mediaType}:${session.mediaId}:${session.seasonNumber ?: "-"}:${session.episodeNumber ?: "-"} requestedResumePosition=${requested.toInt()} seekApplied=${resumeAt.toInt()}")
    }
  }
  val playerProgressCallback: (Double, Double) -> Unit = { position, total ->
    currentTime = position
    duration = total
    if (!session.isLive && !isPaused && total > 0.0 && position >= total - 0.75) finishPlayback()
    // One early checkpoint so a title opened and abandoned still remembers something, then every
    // thirty seconds. The position only has to be right when playback stops, and pause, exit and
    // completion all write on their own -- so a tighter cadence was three times the traffic to the
    // account for a number nobody reads in between.
    val checkpointDue = lastCheckpointSecond <= 0.0 || position - lastCheckpointSecond >= PROGRESS_CHECKPOINT_SECONDS
    if (!session.isLive && !isPaused && total > 0.0 && position >= 10.0 && checkpointDue) {
      lastCheckpointSecond = position
      onProgressCheckpoint(position, total)
    }
  }
  // Shared engine-swap path for automatic Media3 -> mpv error fallback and a user-initiated
  // engine change. Position is retained across the decoder swap.
  fun switchEngine(target: ActivePlaybackEngine, reason: String) {
    if (target == activeEngine) return
    pendingEngineResumeSeconds = currentTime.coerceAtLeast(0.0)
    hasLoaded = false
    error = null
    liveStalled = false
    audioTracks.clear()
    subtitleTracks.clear()
    selectedAudioTrackId = null
    selectedSubtitleTrackId = null
    externalSubtitleNeedsReapply = selectedExternalSubtitleId != null
    loadedVideoWidth = 0
    loadedVideoHeight = 0
    // Only the engine being switched to keeps a view; the others' are let go with their surfaces.
    if (target != ActivePlaybackEngine.MPV) playerView = null
    if (target != ActivePlaybackEngine.Media3) exoPlayerView = null
    if (target != ActivePlaybackEngine.VLC) vlcPlayerView = null
    engineTrail.moveTo(target)
    android.util.Log.w("StreamDekPlayer", "Switching playback engine to $target ($reason) at ${pendingEngineResumeSeconds}s; engines so far: ${engineTrail.describe()}")
    activeEngine = target
  }
  val playerErrorCallback: (String) -> Unit = { message ->
    // libVLC failing on a source it never got going is the engine, not the source: hand the source
    // to the next engine that has not already failed on it, and say so. Once it has played, a
    // failure is the connection, and is handled below the same way for every engine.
    val afterLibVlc = if (activeEngine == ActivePlaybackEngine.VLC && !hasLoaded) {
      engineTrail.nextAfterFailure(ActivePlaybackEngine.VLC, vlcBlocker)
    } else null
    if (afterLibVlc != null) {
      autoSkipNotice = R.string.player_engine_notice_fallback
      switchEngine(afterLibVlc, "libVLC error: $message")
    } else if (
      shouldAutoFallbackToMpv(effectiveEnginePreference(session.playerEngine, activeEngine), activeEngine, autoFallbackUsed) &&
      !engineTrail.hasFailed(ActivePlaybackEngine.MPV)
    ) {
      autoFallbackUsed = true
      engineTrail.markFailed(ActivePlaybackEngine.Media3)
      switchEngine(ActivePlaybackEngine.MPV, (exoPlayerView?.lastFailureReason ?: ExoFallbackReason.PlaybackFatalError.code) + " Media3 error: $message")
    } else if (session.isLive) {
      android.util.Log.w("StreamDekLivePlayer", "player error for ${session.url}: $message")
      error = "Live feed interrupted. Reconnecting..."
      retryOrFailoverLiveFeed()
    } else if (hasLoaded && !reloadedInPlace) {
      // This source has already put a picture on screen, so whether it works is settled. An error
      // arriving minutes later is the connection dropping or a signed link lapsing, and the same
      // source hands out a working one again - so ask it, at the same second, before considering
      // anything else. Switching straight away cost the viewer the source they had chosen for a
      // fault that was never the source's.
      reloadedInPlace = true
      pendingEngineResumeSeconds = currentTime.coerceAtLeast(0.0)
      error = null
      android.util.Log.w(
        "StreamDekPlayer",
        "playing source dropped at ${currentTime}s; reloading it in place: $message",
      )
      activeReload()
    } else {
      session.currentStream?.let { current ->
        val currentKey = playerStreamIdentity(current)
        if (currentKey !in failedSourceKeys) failedSourceKeys.add(currentKey)
      }
      val nextSource = nextUntriedPlaybackSource(availableStreams, session.currentStream, failedSourceKeys.toSet())
      if (nextSource != null) {
        failedSourceKeys.add(playerStreamIdentity(nextSource))
        error = "Source failed. Switching to another stream..."
        onSelectStream(nextSource, progressPercent())
      } else {
        error = message
      }
    }
  }
  val playerEndCallback: () -> Unit = {
    if (session.isLive) {
      android.util.Log.d("StreamDekLivePlayer", "player end-of-file for ${session.url}")
      retryOrFailoverLiveFeed()
    } else {
      finishPlayback()
    }
  }
  val playerStallCallback: (Boolean) -> Unit = playerStallCallback@{ stalled ->
    if (session.isLive) {
      liveStalled = stalled
    } else {
      val now = android.os.SystemClock.elapsedRealtime()
      if (!shouldReportPlaybackBuffering(stalled, seekIssuedAtMs, now)) return@playerStallCallback
      recentPlaybackStalls = (recentPlaybackStalls + now).filter { now - it <= 120_000L }
      if (recentPlaybackStalls.size >= 3 && now >= smartSwitchCooldownUntil && smartSwitchCandidate == null) {
        session.currentStream?.let { current ->
          val key = playerStreamIdentity(current)
          if (key !in failedSourceKeys) failedSourceKeys.add(key)
        }
        smartSwitchCandidate = nextUntriedPlaybackSource(availableStreams, session.currentStream, failedSourceKeys.toSet())
      }
    }
  }
  val playerTracksCallback: (List<MpvTrackInfo>, List<MpvTrackInfo>, Int?, Int?) -> Unit = { audio, subtitles, audioId, subtitleId ->
    audioTracks.clear()
    subtitleTracks.clear()
    audioTracks.addAll(audio)
    subtitleTracks.addAll(subtitles)
    if (userPickedAudio) {
      val preferredAudio = preferredAudioTrackKey?.let { key -> audio.firstOrNull { trackPreferenceKey(it) == key } }
      selectedAudioTrackId = preferredAudio?.id ?: audioId ?: selectedAudioTrackId
      if (preferredAudio != null && audioId != preferredAudio.id) activeSetAudioTrack(preferredAudio.id)
    } else {
      selectedAudioTrackId = audioId
      val preferredLanguage = normalizePreferredAudioLanguage(session.preferredAudioLanguage)
      val preferredAudio = preferredAudioLanguageTags(preferredLanguage)
        .map(::normalizeSubtitleLanguage)
        .firstNotNullOfOrNull { wanted -> audio.firstOrNull { normalizeSubtitleLanguage(it.language) == wanted } }
      if (preferredLanguage != "original" && preferredAudio != null && audioId != preferredAudio.id) {
        selectedAudioTrackId = preferredAudio.id
        activeSetAudioTrack(preferredAudio.id)
      }
    }
    if (userPickedSubtitle || subtitleDisabledByUser) {
      when {
        subtitleDisabledByUser -> selectedSubtitleTrackId = null
        selectedExternalSubtitleId != null && subtitleId != null -> selectedSubtitleTrackId = subtitleId
        else -> {
          val preferredSubtitle = preferredSubtitleTrackKey?.let { key -> subtitles.firstOrNull { trackPreferenceKey(it) == key } }
          selectedSubtitleTrackId = preferredSubtitle?.id ?: subtitleId
          if (preferredSubtitle != null && subtitleId != preferredSubtitle.id) activeSetSubtitleTrack(preferredSubtitle.id)
        }
      }
    } else if (hasLoaded) {
      selectedSubtitleTrackId = subtitleId
      val allowedLanguages = preferredSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
      val preferredSubtitle = subtitles.firstOrNull {
        !(session.isLive && it.speculative) && normalizeSubtitleLanguage(it.language ?: it.title) in allowedLanguages
      }
      when {
        session.autoLoadSubtitles && subtitleSourceAllowsOrigin(session.subtitleDefaultSource, ExternalSubtitleOrigin.BuiltIn) &&
          preferredSubtitle != null && subtitleId != preferredSubtitle.id -> {
          selectedSubtitleTrackId = preferredSubtitle.id
          activeSetSubtitleTrack(preferredSubtitle.id)
        }
        (!session.autoLoadSubtitles || preferredSubtitle == null ||
          !subtitleSourceAllowsOrigin(session.subtitleDefaultSource, ExternalSubtitleOrigin.BuiltIn)) && subtitleId != null -> {
          selectedSubtitleTrackId = null
          activeDisableSubtitleTrack()
        }
      }
    }
  }
  // Neither engine reports "no video renderer" directly, but both report a 0x0 decoded
  // frame size and an empty audio-track list, which is the best available signal that one
  // half of the stream isn't actually playing. Give the engine a few seconds after it claims
  // to have loaded (mpv/Media3 can report tracks a moment after the load callback fires), then
  // swap to the other engine once - not on every recheck - so a genuinely audio-only or
  // video-only source doesn't get bounced back and forth forever.
  LaunchedEffect(session.url, activeEngine, hasLoaded) {
    if (!hasLoaded || avMismatchFallbackTried) return@LaunchedEffect
    // Live manifests often publish their audio rendition metadata a few seconds after video
    // starts. Treating that transient empty track list as a broken decoder caused an already
    // playing channel to switch engines and reload 3.5 seconds after a successful handoff.
    // Explicit player errors and the live stall watchdog still provide safe fallback signals.
    if (session.isLive) return@LaunchedEffect
    delay(3_500)
    if (avMismatchFallbackTried || !hasLoaded || duration <= 0.0) return@LaunchedEffect
    // Media3 is asked what is actually wrong with its audio rather than judged by an empty list:
    // "no track", "a track this device cannot decode", "a track nobody selected" and "a decoder
    // that fell over" are different faults, and all but the second can be put right in place.
    // Handing the source to mpv is what is left when that has been tried, or cannot be.
    val exoView = exoPlayerView.takeIf { activeEngine == ActivePlaybackEngine.Media3 }
    var exoAudioFinding = exoView?.diagnoseAudio()
    if (exoView != null && exoAudioFinding != null && exoView.recoverAudio(exoAudioFinding)) {
      delay(4_000)
      if (activeEngine != ActivePlaybackEngine.Media3 || exoPlayerView !== exoView || avMismatchFallbackTried) return@LaunchedEffect
      exoAudioFinding = exoView.diagnoseAudio()
      if (exoAudioFinding == null) {
        android.util.Log.i("StreamDekPlayer", "Media3 audio recovered in place; staying on Media3")
        return@LaunchedEffect
      }
    }
    val noVideo = loadedVideoWidth <= 0 && loadedVideoHeight <= 0
    val noAudio = if (exoView != null) exoAudioFinding != null else audioTracks.isEmpty()
    if (noVideo != noAudio) {
      avMismatchFallbackTried = true
      // libVLC has its own, firmer check below: it is judged by what it has actually decoded
      // rather than by a track list and a picture size that it reports late.
      if (activeEngine == ActivePlaybackEngine.VLC) return@LaunchedEffect
      val target = if (activeEngine == ActivePlaybackEngine.Media3) ActivePlaybackEngine.MPV else ActivePlaybackEngine.Media3
      // An engine that has already failed on this source is not handed it back.
      if (engineTrail.hasFailed(target)) return@LaunchedEffect
      engineTrail.markFailed(activeEngine)
      switchEngine(
        target,
        exoAudioFinding?.let { it.reason.code + " cause=" + it.cause.letter + " (" + it.cause.summary + "): " + it.detail }
          ?: if (noAudio) "no audio detected" else "no video detected",
      )
    }
  }

  // libVLC playing only half of a source - a picture with no sound, or sound with no picture -
  // hands it to the next engine, once. Its own counters decide: a track that exists and is
  // selected but has produced nothing while the other half runs. Two looks a moment apart, so a
  // decoder that is merely slow to start is not mistaken for one that never will.
  LaunchedEffect(session.url, activeEngine, hasLoaded, vlcPlayerView) {
    if (activeEngine != ActivePlaybackEngine.VLC || !hasLoaded || session.isLive) return@LaunchedEffect
    delay(6_000)
    val problem = vlcPlayerView?.compatibilityProblem() ?: return@LaunchedEffect
    delay(2_000)
    if (activeEngine != ActivePlaybackEngine.VLC || vlcPlayerView?.compatibilityProblem() != problem) return@LaunchedEffect
    val target = engineTrail.nextAfterFailure(ActivePlaybackEngine.VLC, vlcBlocker) ?: return@LaunchedEffect
    autoSkipNotice = R.string.player_engine_notice_fallback
    switchEngine(target, if (problem == LibVlcCompatibilityProblem.NoAudio) "libVLC decoded no audio" else "libVLC showed no picture")
  }

  // A source the viewer's choice of libVLC cannot open is played on the Auto path instead, and
  // they are told why rather than left to wonder which engine they are on.
  LaunchedEffect(session.url, vlcBlocker, session.playerEngine) {
    if (vlcBlocker == null || !session.playerEngine.equals("VLC", ignoreCase = true)) return@LaunchedEffect
    autoSkipNotice = if (vlcBlocker == LibVlcBlocker.ClearKey) R.string.player_engine_notice_protected else R.string.player_engine_notice_headers
    android.util.Log.i("StreamDekPlayer", "libVLC skipped for this source ($vlcBlocker): ${headersLibVlcCannotSend(session.requestHeaders)}")
  }

  if (handoffPickerVisible) {
    AlertDialog(
      onDismissRequest = { if (!handoffLoading) handoffPickerVisible = false },
      title = { Text(stringResource(R.string.handoff_continue_on_a_tv), fontWeight = FontWeight.Black) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(stringResource(R.string.handoff_choose_tv, formatClock(currentTime)))
          if (handoffDevices.isEmpty()) {
            Text(stringResource(R.string.handoff_no_linked_tvs), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
          } else {
            handoffDevices.forEach { device ->
              Button(
                onClick = {
                  handoffLoading = true
                  handoffError = null
                  playerScope.launch {
                    onHandoff(device, currentTime)
                      .onSuccess {
                        isPaused = true
                        activeSetPaused(true)
                        handoffLoading = false
                        handoffPickerVisible = false
                      }
                      .onFailure { failure ->
                        handoffLoading = false
                        handoffError = failure.message ?: "Could not send playback to that TV."
                      }
                  }
                },
                enabled = !handoffLoading,
                modifier = Modifier.fillMaxWidth(),
              ) { Text(if (handoffLoading) "Sending…" else device.name) }
            }
          }
          handoffError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
      },
      confirmButton = { TextButton(onClick = { handoffPickerVisible = false }, enabled = !handoffLoading) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    PlayerSurface(
      session = session,
      engineKey = liveEngineKey,
      activeEngine = activeEngine,
      isPaused = isPaused,
      resizeMode = resizeMode,
      customZoom = customZoom,
      playbackSpeed = playbackSpeed,
      subtitleDelay = subtitleDelay,
      audioDelay = audioDelay,
      subtitleSize = subtitleSize,
      subtitlePosition = subtitlePosition,
      subtitleColor = subtitleColor,
      onLoad = playerLoadCallback,
      onProgress = playerProgressCallback,
      onError = playerErrorCallback,
      onSubtitleError = { subtitleErrorMessage = it },
      onEnd = playerEndCallback,
      onStallChanged = playerStallCallback,
      onTracksChanged = playerTracksCallback,
      onExoViewCreated = { view ->
        exoPlayerView = view
        // Keep native DV7 when supported; accept one compatibility handoff per source.
        view.onDolbyVisionProfile7Callback = {
          if (activeEngine == ActivePlaybackEngine.Media3 && !autoFallbackUsed) {
            autoFallbackUsed = true
            switchEngine(ActivePlaybackEngine.MPV, "Dolby Vision profile 7 compatibility")
            true
          } else false
        }
      },
      onMpvViewCreated = { playerView = it },
      onVlcViewCreated = { view ->
        vlcPlayerView = view
        // libVLC plays a Dolby Vision-only stream as plain HEVC, without its metadata. Media3 can
        // use the device's Dolby Vision decoder, and already knows when to pass profile 7 to mpv,
        // so the source goes there - unless Media3 has already failed on it.
        view.onDolbyVisionCallback = {
          if (activeEngine == ActivePlaybackEngine.VLC && !engineTrail.hasFailed(ActivePlaybackEngine.Media3)) {
            engineTrail.markFailed(ActivePlaybackEngine.VLC)
            autoSkipNotice = R.string.player_engine_notice_dolby_vision
            switchEngine(ActivePlaybackEngine.Media3, "Dolby Vision without HDR10 signalling")
            true
          } else false
        }
      },
    )

    PlayerSurfaceOverlays(
      session = session,
      source = source,
      playback = playback,
      isLoading = isLoading,
      liveSourceAttempt = liveSourceAttempt,
      peerSwarm = peerSwarm,
      peerSourceLabel = session.sourceLabel,
      channelSwitchLoading = channelSwitchLoading,
      channelSwitchLoadingLabel = channelSwitchLoadingLabel,
      nextEpisodeLoadingLabel = nextEpisodeLoadingLabel,
      activeSkipSegment = activeSkipSegment,
      nextEpisodeActionAvailable = nextEpisodeActionAvailable,
      audioManager = audioManager,
      surfaceInteractionSource = surfaceInteractionSource,
      smartSwitchCooldownUntilState = smartSwitchCooldownUntilState,
      nextEpisodeLoading = nextEpisodeLoading,
      activePanelState = activePanelState,
      controlsLockedState = controlsLockedState,
      adjustmentKindState = adjustmentKindState,
      adjustmentLevelState = adjustmentLevelState,
      resizeModeState = resizeModeState,
      customZoomState = customZoomState,
      activeSeekTo = ::activeSeekTo,
      applyBrightness = ::applyBrightness,
      applyMediaVolume = ::applyMediaVolume,
      currentWindowBrightness = ::currentWindowBrightness,
      currentMediaVolume = ::currentMediaVolume,
      keepControlsVisible = ::keepControlsVisible,
      progressPercent = ::progressPercent,
      onSelectStream = onSelectStream,
      onNextEpisodeAtEnding = onNextEpisodeAtEnding,
      upNextVisible = endOfPlaybackPhase == EndOfPlaybackPhase.Presented || endOfPlaybackPhase == EndOfPlaybackPhase.Countdown,
      primaryRecommendation = primaryRecommendation,
      alternativeRecommendations = alternativeRecommendations,
      showNextEpisode = upNextDecision?.primaryKind == UpNextKind.NextEpisode,
      nextEpisodeAvailability = nextEpisodeAvailability,
      nextEpisodeLabel = nextEpisodeLabel,
      nextEpisodeArtwork = nextEpisodeArtwork,
      upNextCountdown = upNextCountdown,
      currentTitle = session.title,
      watchlistIds = recommendationWatchlistIds,
      onPlayNextEpisode = {
        if (nextEpisodeAvailability == NextEpisodeAvailability.Unaired) claimTransition(onUnairedEpisodeAtEnding)
        else claimTransition(onNextEpisodeAtEnding)
      },
      onPlayRecommendation = { item -> claimTransition { onRecommendedPlaybackEnded(item) } },
      onAddToWatchlist = onAddRecommendationToWatchlist,
      onDismissUpNext = {
        upNextCountdown = null
        endOfPlaybackPhase = EndOfPlaybackPhase.Dismissed
      },
      onTogglePause = {
        val nextPaused = !isPaused
        if (nextPaused) {
          pausedForAudioFocus = false
          abandonPlayerAudioFocus()
          onScrobble("pause", currentProgressPercent)
          isPaused = true
        } else if (requestPlayerAudioFocus()) {
          showPausedInfo = false
          onScrobble("start", currentProgressPercent)
          isPaused = false
        }
      },
    )

    // Seeded from the session, then owned here, so switching from the header takes effect at once
    // rather than on the next session built from the saved setting.
    val controlLayoutState = rememberSaveable { mutableStateOf(session.playerControlLayout) }
    PlayerOverlays(
      controlLayoutState = controlLayoutState,
      onControlLayoutChange = onPlayerControlLayoutChange,
      session = session,
      source = source,
      playback = playback,
      isFavourite = isFavourite,
      isLoading = isLoading,
      currentProgressPercent = currentProgressPercent,
      activePanelState = activePanelState,
      playbackSpeedState = playbackSpeedState,
      controlsLockedState = controlsLockedState,
      resizeModeState = resizeModeState,
      customZoomState = customZoomState,
      showLiveProgressState = showLiveProgressState,
      adjustmentKindState = adjustmentKindState,
      adjustmentLevelState = adjustmentLevelState,
      activeSeekTo = ::activeSeekTo,
      requestPlayerAudioFocus = ::requestPlayerAudioFocus,
      abandonPlayerAudioFocus = ::abandonPlayerAudioFocus,
      keepControlsVisible = ::keepControlsVisible,
      closePlayer = ::closePlayer,
      onBack = onBack,
      onScrobble = onScrobble,
      onToggleFavourite = onToggleFavourite,
      onHandoff = onHandoff,
      onNextEpisode = onNextEpisode,
      onPreviousEpisode = onPreviousEpisode,
      showEpisodes = episodeBrowser != null && !session.isLive,
    )

    PlayerPanels(
      episodeBrowser = episodeBrowser,
      // The position the viewer is leaving, written before the switch so the episode they came
      // from shows as part watched in the list they are choosing from - and resumes there later.
      onBeforeEpisodeSwitch = { if (!session.isLive && duration > 0.0 && currentTime > 0.0) onProgressCheckpoint(currentTime, duration) },
      session = session,
      playerContext = playerContext,
      playerScope = playerScope,
      activeEngine = activeEngine,
      duration = duration,
      currentProgressPercent = currentProgressPercent,
      switchEngine = ::switchEngine,
      audioTracks = audioTracks,
      subtitleTracks = subtitleTracks,
      availableSubtitleTabs = availableSubtitleTabs,
      externalSubtitles = externalSubtitles,
      subtitlesLoading = subtitlesLoading,
      playbackStats = playbackStats,
      availableStreams = availableStreams,
      downloadsEnabled = downloadsEnabled,
      activePanelState = activePanelState,
      playbackSpeedState = playbackSpeedState,
      subtitleDelayState = subtitleDelayState,
      audioDelayState = audioDelayState,
      subtitleSizeState = subtitleSizeState,
      subtitlePositionState = subtitlePositionState,
      subtitleTabState = subtitleTabState,
      subtitleDisabledByUserState = subtitleDisabledByUserState,
      subtitleErrorMessageState = subtitleErrorMessageState,
      selectedAudioTrackIdState = selectedAudioTrackIdState,
      selectedSubtitleTrackIdState = selectedSubtitleTrackIdState,
      selectedExternalSubtitleIdState = selectedExternalSubtitleIdState,
      preferredAudioTrackKeyState = preferredAudioTrackKeyState,
      preferredSubtitleTrackKeyState = preferredSubtitleTrackKeyState,
      userPickedAudioState = userPickedAudioState,
      userPickedSubtitleState = userPickedSubtitleState,
      activeSetAudioTrack = ::activeSetAudioTrack,
      activeSetSubtitleTrack = ::activeSetSubtitleTrack,
      activeDisableSubtitleTrack = ::activeDisableSubtitleTrack,
      activeSetSubtitleFontSize = ::activeSetSubtitleFontSize,
      activeSetSubtitlePosition = ::activeSetSubtitlePosition,
      activeSetSubtitleDelay = ::activeSetSubtitleDelay,
      activeSetAudioDelay = ::activeSetAudioDelay,
      activeAudioDelaySupported = ::activeAudioDelaySupported,
      activeSetSpeed = ::activeSetSpeed,
      activeAddSubtitle = ::activeAddSubtitle,
      onSubtitleSourceChange = onSubtitleSourceChange,
      onSubtitleTextSizeChange = onSubtitleTextSizeChange,
      onSubtitleVerticalOffsetChange = onSubtitleVerticalOffsetChange,
      onSelectStream = onSelectStream,
      onReloadStreams = onReloadStreams,
      onDownloadStream = onDownloadStream,
      onToggleSourceFavourite = onToggleSourceFavourite,
    )
    PlayerLiveOverlays(
      session = session,
      source = source,
      playback = playback,
      isLoading = isLoading,
      channelSwitchLoading = channelSwitchLoading,
      channelSwitchLoadingLabel = channelSwitchLoadingLabel,
      liveChannels = liveChannels,
      liveChannelsLoading = liveChannelsLoading,
      favouriteChannels = favouriteChannels,
      favouriteChannelIds = favouriteChannelIds,
      favouriteDrawerCards = favouriteDrawerCards,
      activePanelState = activePanelState,
      controlsLockedState = controlsLockedState,
      onSelectLiveChannel = onSelectLiveChannel,
      onToggleFavourite = onToggleFavourite,
      onToggleFavouriteDrawerCards = onToggleFavouriteDrawerCards,
      onClearFavourites = onClearFavourites,
    )
  }
}

/**
 * Invisible, muted background probe used while switching live channels. It prepares the
 * newly-resolved stream on its own throwaway Media3 instance without ever touching the
 * currently-visible player, so the channel already on screen keeps playing (video and
 * audio) undisturbed until this confirms the new one actually decodes - only then does the
 * caller swap the real player over to it. Always uses Media3 here regardless of the user's
 * engine preference: it's cheap to spin up and tear down a second instance of, unlike a
 * second native mpv context.
 */
/**
 * The video surface itself — either engine's [AndroidView], whichever is active.
 *
 * Split out of NativePlayerScreen so each stays inside ART's ~10,000 code-unit limit for JIT
 * compilation: a composable over that limit is left interpreted for the life of the process, which
 * on the player means every frame of control/overlay recomposition runs slowly while video plays.
 */
@Composable
private fun PlayerSurface(
  session: PlayerSession,
  engineKey: String,
  activeEngine: ActivePlaybackEngine,
  isPaused: Boolean,
  resizeMode: String,
  customZoom: Float,
  playbackSpeed: Float,
  subtitleDelay: Float,
  audioDelay: Float,
  subtitleSize: Int,
  subtitlePosition: Int,
  subtitleColor: String,
  onLoad: (Double, Int, Int) -> Unit,
  onProgress: (Double, Double) -> Unit,
  onError: (String) -> Unit,
  onSubtitleError: (String) -> Unit,
  onEnd: () -> Unit,
  onStallChanged: (Boolean) -> Unit,
  onTracksChanged: (List<MpvTrackInfo>, List<MpvTrackInfo>, Int?, Int?) -> Unit,
  onExoViewCreated: (ExoPlaybackView) -> Unit,
  onMpvViewCreated: (MPVView) -> Unit,
  onVlcViewCreated: (VlcPlaybackView) -> Unit,
) {
  key(engineKey, activeEngine) {
    if (activeEngine == ActivePlaybackEngine.VLC) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          VlcPlaybackView(context).apply {
            onVlcViewCreated(this)
            // Everything the source is opened with goes in before setSource: libVLC reads the
            // decoder mode, headers, languages and subtitle appearance when it opens a source.
            setLoadWaitsForPlayback(session.isLive)
            onLoadCallback = onLoad
            onProgressCallback = onProgress
            onErrorCallback = onError
            onEndCallback = onEnd
            onStallChangedCallback = onStallChanged
            onTracksChangedCallback = onTracksChanged
            setResizeMode(if (resizeMode == "custom") "contain" else resizeMode)
            setVideoZoom(customZoom)
            setDecoderMode(session.decoderMode)
            setRenderSurface(session.renderSurface)
            setSpeed(playbackSpeed.toDouble())
            setSubtitleDelay(subtitleDelay.toDouble())
            setAudioDelay(audioDelay.toDouble())
            setSubtitleFontSize(subtitleSize)
            setSubtitlePosition(subtitlePosition)
            setSubtitleColor(subtitleColor)
            setSubtitleBackgroundColor(session.subtitleBackgroundColor)
            setSubtitleOutline(session.subtitleOutline, session.subtitleOutlineColor)
            setSubtitleBold(session.subtitleBold)
            setHeaders(session.requestHeaders)
            setPreferredAudioLanguage(session.preferredAudioLanguage)
            setSecondaryAudioLanguage(session.secondaryAudioLanguage)
            setSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
            setSource(session.url)
            setPaused(false)
          }
        },
        update = { view ->
          // See the equivalent comment in the Media3 branch below - same reason.
          view.setLoadWaitsForPlayback(session.isLive)
          view.onLoadCallback = onLoad
          view.onProgressCallback = onProgress
          view.onErrorCallback = onError
          view.onEndCallback = onEnd
          view.onStallChangedCallback = onStallChanged
          view.onTracksChangedCallback = onTracksChanged
          view.setHeaders(session.requestHeaders)
          view.setDecoderMode(session.decoderMode)
          view.setRenderSurface(session.renderSurface)
          view.setPreferredAudioLanguage(session.preferredAudioLanguage)
          view.setSecondaryAudioLanguage(session.secondaryAudioLanguage)
          view.setSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
          view.setSubtitleFontSize(subtitleSize)
          view.setSubtitlePosition(subtitlePosition)
          view.setSubtitleColor(subtitleColor)
          view.setSubtitleBackgroundColor(session.subtitleBackgroundColor)
          view.setSubtitleOutline(session.subtitleOutline, session.subtitleOutlineColor)
          view.setSubtitleBold(session.subtitleBold)
          view.setSource(session.url)
          view.setPaused(isPaused)
          view.setResizeMode(if (resizeMode == "custom") "contain" else resizeMode)
          view.setVideoZoom(customZoom)
          view.setSpeed(playbackSpeed.toDouble())
          view.setSubtitleDelay(subtitleDelay.toDouble())
          view.setAudioDelay(audioDelay.toDouble())
        },
      )
    } else if (activeEngine == ActivePlaybackEngine.Media3) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          ExoPlaybackView(context).apply {
            onExoViewCreated(this)
            onLoadCallback = onLoad
            onProgressCallback = onProgress
            onErrorCallback = onError
            onExternalSubtitleErrorCallback = onSubtitleError
            onEndCallback = onEnd
            onStallChangedCallback = onStallChanged
            onTracksChangedCallback = onTracksChanged
            setResizeMode(if (resizeMode == "custom") "contain" else resizeMode)
            setVideoZoom(customZoom)
            setSpeed(playbackSpeed.toDouble())
            setSubtitleDelay(subtitleDelay.toDouble())
            setAudioDelay(audioDelay.toDouble())
            setSubtitleFontSize(subtitleSize)
            setSubtitlePosition(subtitlePosition)
            setSubtitleColor(subtitleColor)
            setSubtitleBackgroundColor(session.subtitleBackgroundColor)
            setSubtitleOutline(session.subtitleOutline, session.subtitleOutlineColor)
            setSubtitleBold(session.subtitleBold)
            setHeaders(session.requestHeaders)
            setDrmClearKeys(session.drmLicenseType, session.drmClearKeys)
            setPreferredAudioLanguage(session.preferredAudioLanguage)
            setSecondaryAudioLanguage(session.secondaryAudioLanguage)
            setSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage, session.useForcedSubtitles)
            setCaptionProbe(session.isLive)
            setSource(session.url)
            setPaused(false)
          }
        },
        update = { view ->
          // Reassigned on every recomposition, not just at creation - a live channel
          // switch reuses this same view (see key(engineKey, ...) above) instead of
          // recreating it, so these closures must stay pointed at the *current*
          // session's hasLoaded/error/etc. state or the new channel's own load/error
          // signal is silently swallowed by stale callbacks still bound to the state
          // objects from the channel that was just switched away from.
          view.onLoadCallback = onLoad
          view.onProgressCallback = onProgress
          view.onErrorCallback = onError
          view.onExternalSubtitleErrorCallback = onSubtitleError
          view.onEndCallback = onEnd
          view.onStallChangedCallback = onStallChanged
          view.onTracksChangedCallback = onTracksChanged
          view.setHeaders(session.requestHeaders)
          view.setDrmClearKeys(session.drmLicenseType, session.drmClearKeys)
          view.setPreferredAudioLanguage(session.preferredAudioLanguage)
          view.setSecondaryAudioLanguage(session.secondaryAudioLanguage)
          view.setSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage, session.useForcedSubtitles)
          view.setCaptionProbe(session.isLive)
          view.setSource(session.url)
          view.setPaused(isPaused)
          view.setResizeMode(if (resizeMode == "custom") "contain" else resizeMode)
          view.setVideoZoom(customZoom)
          view.setSpeed(playbackSpeed.toDouble())
          view.setSubtitleDelay(subtitleDelay.toDouble())
          view.setAudioDelay(audioDelay.toDouble())
          view.setSubtitleFontSize(subtitleSize)
          view.setSubtitlePosition(subtitlePosition)
          view.setSubtitleColor(subtitleColor)
          view.setSubtitleBackgroundColor(session.subtitleBackgroundColor)
          view.setSubtitleOutline(session.subtitleOutline, session.subtitleOutlineColor)
          view.setSubtitleBold(session.subtitleBold)
        },
      )
    } else {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          MPVView(context).apply {
            onMpvViewCreated(this)
            // Before setSource below, so the first load already knows what counts as loaded.
            setLoadWaitsForPlayback(session.isLive)
            onLoadCallback = onLoad
            onProgressCallback = onProgress
            onErrorCallback = onError
            onEndCallback = onEnd
            onStallChangedCallback = onStallChanged
            onTracksChangedCallback = onTracksChanged
            setResizeMode(if (resizeMode == "custom") "contain" else resizeMode)
            setVideoZoom(customZoom)
            setDecoderMode(session.decoderMode)
            setRenderSurface(session.renderSurface)
            setSpeed(playbackSpeed.toDouble())
            setSubtitleDelay(subtitleDelay.toDouble())
            setAudioDelay(audioDelay.toDouble())
            setSubtitleFontSize(subtitleSize)
            setSubtitlePosition(subtitlePosition)
            setSubtitleColor(subtitleColor)
            setSubtitleBackgroundColor(session.subtitleBackgroundColor)
            setSubtitleOutline(session.subtitleOutline, session.subtitleOutlineColor)
            setSubtitleBold(session.subtitleBold)
            setHeaders(session.requestHeaders)
            setPreferredAudioLanguage(session.preferredAudioLanguage)
            setSecondaryAudioLanguage(session.secondaryAudioLanguage)
            setSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
            setSource(session.url)
            setPaused(false)
          }
        },
        update = { view ->
          // See the equivalent comment in the Media3 branch above - same reason.
          view.setLoadWaitsForPlayback(session.isLive)
          view.onLoadCallback = onLoad
          view.onProgressCallback = onProgress
          view.onErrorCallback = onError
          view.onEndCallback = onEnd
          view.onStallChangedCallback = onStallChanged
          view.onTracksChangedCallback = onTracksChanged
          view.setHeaders(session.requestHeaders)
          view.setPreferredAudioLanguage(session.preferredAudioLanguage)
          view.setSecondaryAudioLanguage(session.secondaryAudioLanguage)
          view.setSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
          view.setSource(session.url)
          view.setPaused(isPaused)
          view.setResizeMode(if (resizeMode == "custom") "contain" else resizeMode)
          view.setVideoZoom(customZoom)
          view.setDecoderMode(session.decoderMode)
          view.setRenderSurface(session.renderSurface)
          view.setSpeed(playbackSpeed.toDouble())
          view.setSubtitleDelay(subtitleDelay.toDouble())
          view.setAudioDelay(audioDelay.toDouble())
          view.setSubtitleFontSize(subtitleSize)
          view.setSubtitlePosition(subtitlePosition)
          view.setSubtitleColor(subtitleColor)
          view.setSubtitleBackgroundColor(session.subtitleBackgroundColor)
          view.setSubtitleOutline(session.subtitleOutline, session.subtitleOutlineColor)
          view.setSubtitleBold(session.subtitleBold)
        },
      )
    }
  }
}

@Composable
private fun LiveChannelSwipeCue() {
  val transition = rememberInfiniteTransition(label = "channel_swipe_cue")
  val offset by transition.animateFloat(
    initialValue = 3f,
    targetValue = -5f,
    animationSpec = infiniteRepeatable(tween(720), repeatMode = RepeatMode.Reverse),
    label = "channel_swipe_cue_offset",
  )
  val shadow = androidx.compose.ui.graphics.Shadow(
    color = Color.Black.copy(alpha = 0.78f), offset = Offset(0f, 2f), blurRadius = 14f,
  )
  Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(
      stringResource(R.string.live_swipe_up_all_channels), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
      style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow),
    )
    Icon(
      StreamDekPlayerIcons.ChevronUp, contentDescription = null, tint = Color.White.copy(alpha = 0.92f),
      modifier = Modifier.size(22.dp).graphicsLayer { translationY = offset; shadowElevation = 8f },
    )
  }
}

@Composable
private fun LiveChannelTray(
  channels: List<MediaItem>,
  currentChannelId: String,
  loading: Boolean,
  favouriteChannelIds: Set<String>,
  onSelect: (MediaItem) -> Unit,
) {
  val currentIndex = channels.indexOfFirst { it.id == currentChannelId }.coerceAtLeast(0)
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
  LaunchedEffect(channels.size, currentChannelId) {
    channels.indexOfFirst { it.id == currentChannelId }.takeIf { it >= 0 }?.let { listState.animateScrollToItem(it) }
  }
  Column(
    modifier = Modifier.fillMaxWidth().background(
      Brush.verticalGradient(
        colorStops = arrayOf(0f to Color.Transparent, 0.28f to Color.Black.copy(alpha = 0.20f), 1f to Color.Black.copy(alpha = 0.58f)),
      ),
    ).padding(top = 18.dp, bottom = 18.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Same treatment as the modal panel's heading: the hint on the right keeps its natural width
      // and the heading takes the rest, so a longer translation cannot squeeze it away.
      Text(
        if (loading) stringResource(R.string.live_loading_all_channels) else stringResource(R.string.live_all_channels),
        modifier = Modifier.weight(1f),
        color = Color.White,
        fontWeight = FontWeight.Bold, fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.titleSmall.copy(
          shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 12f),
        ),
      )
      Text(stringResource(R.string.live_swipe_down_close), color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
    }
    if (channels.isEmpty()) {
      Box(modifier = Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        else Text(stringResource(R.string.live_no_channels_from_addon), color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
      }
    } else {
      LazyRow(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(channels, key = { "${it.sourceAddonId}-${it.sourceCatalogId}-${it.type}-${it.id}" }) { channel ->
          val selected = channel.id == currentChannelId
          val favourite = channel.id in favouriteChannelIds
          Box(
            modifier = Modifier.width(160.dp).height(90.dp).clip(StreamDekRadius.controlShape)
              .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.20f), StreamDekRadius.controlShape)
              .clickable { onSelect(channel) },
          ) {
            AsyncImage(model = channel.backdrop ?: channel.poster, contentDescription = channel.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.86f)))))
            Text(
              channel.title, modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 9.dp, vertical = 7.dp),
              color = Color.White, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
              maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (selected) Text(
              stringResource(R.string.live_now_playing), modifier = Modifier.align(Alignment.TopStart).padding(7.dp), color = Color.White,
              fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp,
            )
            // Same filled yellow star as the player header, in the corner the NOW PLAYING marker
            // does not use, so a channel that is both still shows both.
            if (favourite) Box(
              modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(StreamDekPlayerIcons.Star, contentDescription = stringResource(R.string.a11y_in_favourites), tint = Color(0xFFFACC15), modifier = Modifier.size(13.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LiveFavouriteDrawer(
  favourites: List<MediaItem>,
  currentChannelId: String,
  cardView: Boolean,
  onClose: () -> Unit,
  onSelect: (MediaItem) -> Unit,
  onToggleCardView: (Boolean) -> Unit = {},
  onClearAll: () -> Unit = {},
) {
  var showClearConfirm by remember { mutableStateOf(false) }
  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      icon = { Icon(StreamDekPlayerIcons.ClearAll, contentDescription = null) },
      title = { Text(stringResource(R.string.live_clear_favourites_title)) },
      text = { Text(pluralStringResource(R.plurals.live_clear_favourites_detail, favourites.size, favourites.size)) },
      confirmButton = { Button(onClick = { showClearConfirm = false; onClearAll() }) { Text(stringResource(R.string.live_clear_all)) } },
      dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
  Box(
    modifier = Modifier.fillMaxSize().background(
      Brush.horizontalGradient(
        colorStops = arrayOf(
          0f to Color.Transparent,
          0.18f to Color(0xA613161D),
          0.48f to Color(0xDD13161D),
          1f to Color(0xF013161D),
        ),
      ),
    ),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onClose) { Icon(StreamDekPlayerIcons.ChevronRight, contentDescription = stringResource(R.string.a11y_close_favourites), tint = Color.White) }
        Column(modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.live_favourites), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Text(pluralStringResource(R.plurals.live_channel_count, favourites.size, favourites.size), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
        }
        IconButton(onClick = { onToggleCardView(!cardView) }, modifier = Modifier.size(32.dp)) {
          Icon(
            if (cardView) StreamDekPlayerIcons.ListView else StreamDekPlayerIcons.Sources,
            contentDescription = if (cardView) "Switch to text list" else "Switch to card view",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
          )
        }
        IconButton(onClick = { showClearConfirm = true }, enabled = favourites.isNotEmpty(), modifier = Modifier.size(32.dp)) {
          Icon(
            StreamDekPlayerIcons.ClearAll, contentDescription = stringResource(R.string.a11y_clear_all_favourites),
            tint = Color.White.copy(alpha = if (favourites.isNotEmpty()) 0.85f else 0.3f),
            modifier = Modifier.size(18.dp),
          )
        }
      }
      if (favourites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(stringResource(R.string.player_favourites_empty), color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(if (cardView) 10.dp else 2.dp)) {
          items(favourites, key = { "${it.type}-${it.id}" }) { channel ->
            val selected = channel.id == currentChannelId
            if (cardView) {
              Column(
                modifier = Modifier.fillMaxWidth().clip(StreamDekRadius.thumbShape).background(if (selected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f)).clickable { onSelect(channel) }.padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                AsyncImage(model = channel.backdrop ?: channel.poster, contentDescription = channel.title, modifier = Modifier.fillMaxWidth().height(62.dp).clip(StreamDekRadius.controlShape), contentScale = ContentScale.Crop)
                Text(channel.title, color = Color.White, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            } else {
              // The drawer is anchored to the right edge of the screen, so the text list reads
              // right-aligned: channel names end on a straight edge against that side and the
              // now-playing dot sits in a fixed column beyond them.
              Row(
                modifier = Modifier.fillMaxWidth().clip(StreamDekRadius.controlShape).background(if (selected) Color.White.copy(alpha = 0.13f) else Color.Transparent).clickable { onSelect(channel) }.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
              ) {
                Text(
                  channel.title,
                  color = Color.White.copy(alpha = if (selected) 1f else 0.82f),
                  fontSize = 11.5.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  textAlign = TextAlign.End,
                  // fill = false keeps short names hugging the right instead of stretching, while
                  // long ones still ellipsize inside the drawer rather than pushing the dot off it.
                  modifier = Modifier.weight(1f, fill = false),
                )
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (selected) Color(0xFFE11D48) else Color.White.copy(alpha = 0.32f)))
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(androidx.activity.ExperimentalActivityApi::class)
@Composable
private fun PlayerResolvingScreen(
  session: PlayerSession,
  sourceLabel: String?,
  peerHash: String?,
  swarm: SwarmStats?,
  onBack: () -> Unit,
) {
  // A gesture-back callback owns the whole gesture. Mutating the route from a plain BackHandler
  // while the edge swipe was still completing exposed MainScene/Activity to the tail of that same
  // gesture and could close the app. Wait for the progress flow to finish, then cancel playback;
  // a cancelled gesture throws and deliberately changes nothing.
  PredictiveBackHandler { progress ->
    try {
      progress.collect { }
      onBack()
    } catch (_: CancellationException) {
      // The viewer reversed the gesture before committing it.
    }
  }
  Box(modifier = Modifier.fillMaxSize()) {
    PlayerLoadingBackdrop(
      session = session,
      message = when {
        swarm == null -> if (peerHash.isNullOrBlank()) stringResource(R.string.player_preparing_stream)
          else stringResource(R.string.player_preparing_peer_stream)
        !swarm.hasMetadata -> stringResource(R.string.player_finding_peers)
        else -> stringResource(R.string.player_buffering_peers)
      },
      swarm = swarm,
      sourceLabel = sourceLabel,
    )
    val minimal = session.playerControlLayout == "Minimal"
    Box(
      modifier = Modifier
        .statusBarsPadding()
        .padding(20.dp)
        .align(Alignment.TopStart)
        .size(48.dp)
        .clip(CircleShape)
        .clickable(role = Role.Button, onClick = onBack),
      contentAlignment = Alignment.Center,
    ) {
      PlayerGlyph(StreamDekPlayerIcons.Back, stringResource(R.string.action_back), if (minimal) 30.dp else 28.dp)
    }
  }
}

@Composable
private fun PlayerLoadingBackdrop(
  session: PlayerSession,
  message: String,
  swarm: SwarmStats? = null,
  sourceLabel: String? = null,
) {
  val pulse by rememberInfiniteTransition(label = "player_loading_logo").animateFloat(
    initialValue = 0.42f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse),
    label = "player_loading_logo_alpha",
  )
  Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
    AsyncImage(
      model = session.backdrop,
      contentDescription = null,
      modifier = Modifier.fillMaxSize().blur(26.dp),
      contentScale = ContentScale.Crop,
    )
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.88f)))))
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(horizontal = 36.dp)) {
      if (!session.titleLogo.isNullOrBlank()) {
        AsyncImage(
          model = session.titleLogo,
          contentDescription = session.title,
          modifier = Modifier.fillMaxWidth(0.42f).height(92.dp).graphicsLayer { alpha = pulse },
          contentScale = ContentScale.Fit,
        )
      } else {
        Text(session.title, color = Color.White.copy(alpha = pulse), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
      session.synopsis?.takeIf { it.isNotBlank() }?.let {
        Text(
          text = it,
          color = Color.White.copy(alpha = 0.82f),
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth(0.72f),
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.86f)))
        Text(message, color = Color.White.copy(alpha = 0.84f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      }
      swarm?.let { stats ->
        val fraction = stats
          .takeIf { it.hasMetadata && it.fileLengthBytes > 0L }
          ?.let { (it.downloadedBytes.toFloat() / it.fileLengthBytes.toFloat()).coerceIn(0f, 1f) }
        if (fraction != null) {
          LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(0.72f).height(4.dp).clip(CircleShape),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.16f),
          )
        } else {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.72f).height(4.dp).clip(CircleShape),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.16f),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
          PlayerSwarmStat("SEEDS", stats.seeds.toString())
          PlayerSwarmStat(stringResource(R.string.player_swarm_peers), stats.peers.toString())
          PlayerSwarmStat(stringResource(R.string.player_swarm_speed), playerRateLabel(stats.downloadRateBytesPerSecond.toLong()))
        }
      }
      (sourceLabel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_finding_a_source)).let {
        Text(
          it,
          color = Color.White.copy(alpha = 0.50f),
          style = MaterialTheme.typography.bodySmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(0.72f),
        )
      }
    }
  }
}

@Composable
private fun PlayerSwarmStat(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
    Text(label, color = Color.White.copy(alpha = 0.42f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
  }
}

private fun playerRateLabel(bytesPerSecond: Long): String {
  val rate = bytesPerSecond.coerceAtLeast(0L)
  val kib = 1024.0
  val mib = kib * 1024.0
  return when {
    rate <= 0L -> "—"
    rate >= mib -> String.format(Locale.US, "%.1f MB/s", rate / mib)
    rate >= kib -> String.format(Locale.US, "%.0f KB/s", rate / kib)
    else -> "$rate B/s"
  }
}

@Composable
private fun PlayerPausedGradient() {
  Box(
    modifier = Modifier.fillMaxSize().background(
      Brush.horizontalGradient(
        colorStops = arrayOf(
          0.0f to Color.Transparent,
          0.48f to Color.Transparent,
          0.72f to Color.Black.copy(alpha = 0.30f),
          1.0f to Color.Black.copy(alpha = 0.82f),
        ),
      ),
    ),
  )
}

@Composable
private fun PlayerPausedContent(session: PlayerSession) {
  Column(
    modifier = Modifier.fillMaxSize().padding(end = 26.dp),
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.Center,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(0.38f).padding(horizontal = 18.dp, vertical = 22.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!session.titleLogo.isNullOrBlank()) {
        AsyncImage(model = session.titleLogo, contentDescription = session.title, modifier = Modifier.fillMaxWidth().height(84.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterEnd)
      } else {
        Text(session.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
      }
      episodeContext(session)?.let { label ->
        Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
      }
      session.synopsis?.takeIf { it.isNotBlank() }?.let {
        Text(it, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
      }
    }
  }
}
@Composable
private fun PlayerCenterControls(isPaused: Boolean, onPauseToggle: () -> Unit, onSeek: (Double) -> Unit, showEpisodeNavigation: Boolean, onPreviousEpisode: () -> Unit, onNextEpisode: () -> Unit, showSeeking: Boolean = true, layout: String = "Normal") {
  // Fixed button sizes and a fixed gap between them, centered in whatever space is
  // available - a width-based padding/arrangement here (as this used to have) shrinks
  // the available room on narrower screens and squashes the buttons together instead of
  // just centering a smaller group, so this deliberately never scales with screen width.
  Row(
    modifier = Modifier.fillMaxSize(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(48.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Bare icons in every layout: no disc behind play, pause or either seek. The targets keep
      // the size the discs had - 90dp and 74dp - so nothing is harder to hit than it was.
      if (showSeeking) PlayerRoundAction(icon = StreamDekPlayerIcons.Replay10, label = pluralStringResource(R.plurals.player_rewind_seconds, 10, 10), onClick = { onSeek(-10.0) }) else Spacer(modifier = Modifier.size(74.dp))
      val playPauseLabel = stringResource(if (isPaused) R.string.action_play else R.string.action_pause)
      Box(
        modifier = Modifier
          .size(90.dp)
          .clip(CircleShape)
          .clickable(role = Role.Button, onClick = onPauseToggle),
        contentAlignment = Alignment.Center,
      ) {
        PlayerGlyph(if (isPaused) StreamDekPlayerIcons.Play else StreamDekPlayerIcons.Pause, playPauseLabel, if (isPaused) 51.dp else 54.dp)
      }
      if (showSeeking) PlayerRoundAction(icon = StreamDekPlayerIcons.Forward10, label = pluralStringResource(R.plurals.player_forward_seconds, 10, 10), onClick = { onSeek(10.0) }) else Spacer(modifier = Modifier.size(74.dp))
    }
  }
}

@Composable
private fun PlayerRoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier.size(74.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    PlayerGlyph(icon, label, 36.dp)
  }
}

/**
 * A player icon drawn straight onto the picture, with no disc behind it.
 *
 * The disc was doing one real job, which was keeping a white icon readable over a white scene. A
 * soft shadow of the icon's own shape does that job without drawing a shape of its own: the icon
 * is laid down twice, once dark and blurred a little below, then in its tint on top. On Android 11
 * and earlier, where a blur is not available, the same dark copy sits unblurred a pixel below the
 * icon, which reads as a crisp edge and serves the same purpose.
 *
 * The shadow carries no description, so a screen reader announces the control once.
 */
@Composable
private fun PlayerGlyph(icon: ImageVector, contentDescription: String?, size: Dp, tint: Color = Color.White) {
  Box(contentAlignment = Alignment.Center) {
    Icon(
      icon,
      contentDescription = null,
      tint = Color.Black.copy(alpha = 0.42f),
      modifier = Modifier.size(size).offset(y = 1.dp).blur(2.5.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
    )
    Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size))
  }
}

/**
 * One control in the player's header: a bare icon in a 48dp round target.
 *
 * The target is the whole 48dp, not the icon, and the press ripple fills it - which is also what
 * shows the control took the tap now that there is no disc to light up.
 */
@Composable
private fun PlayerHeaderAction(icon: ImageVector, label: String, onClick: () -> Unit, iconSize: Dp = 26.dp, tint: Color = Color.White) {
  Box(
    modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    PlayerGlyph(icon, label, iconSize, tint)
  }
}

@Composable
private fun RefinedPlayerSlider(
  progress: Float,
  onProgressChange: (Float) -> Unit,
  onProgressFinished: () -> Unit,
  modifier: Modifier = Modifier,
  buffered: List<BufferedSegment> = emptyList(),
  bufferedColor: Color = Color.Transparent,
) {
  val activeColor = MaterialTheme.colorScheme.primary
  val trackColor = Color.White.copy(alpha = 0.26f)
  val safeProgress = progress.coerceIn(0f, 1f)
  Slider(
    value = safeProgress,
    onValueChange = onProgressChange,
    onValueChangeFinished = onProgressFinished,
    colors = SliderDefaults.colors(
      thumbColor = Color.Transparent,
      activeTrackColor = Color.Transparent,
      inactiveTrackColor = Color.Transparent,
      disabledThumbColor = Color.Transparent,
      disabledActiveTrackColor = Color.Transparent,
      disabledInactiveTrackColor = Color.Transparent,
    ),
    modifier = modifier
      .height(48.dp)
      .drawBehind {
        val centerY = size.height / 2f
        // Material Slider reserves roughly half a thumb at both ends when mapping touch position
        // to value. Draw to that same inset so the visible thumb stays exactly under the finger.
        val inset = 10.dp.toPx().coerceAtMost(size.width / 2f)
        val trackWidth = (size.width - inset * 2f).coerceAtLeast(0f)
        val thumbX = inset + trackWidth * safeProgress
        // 4dp, the three lines together: at 2dp the bar was easy to lose against a busy picture.
        drawLine(trackColor, Offset(inset, centerY), Offset(inset + trackWidth, centerY), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        // Buffered, between the track and the watched line. Slightly heavier than either so the
        // softer tone still reads on a line this fine; the watched line and thumb are drawn over it.
        buffered.forEach { segment ->
          val from = inset + trackWidth * segment.start
          val to = inset + trackWidth * segment.end
          if (to > from) drawLine(bufferedColor, Offset(from, centerY), Offset(to, centerY), strokeWidth = 4.5.dp.toPx(), cap = StrokeCap.Butt)
        }
        // No dot at the playhead: it covered the start of the buffered stretch. The end of the
        // watched line is the position.
        drawLine(activeColor, Offset(inset, centerY), Offset(thumbX, centerY), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
      },
  )
}

/**
 * Material's own slider track with the buffered tone laid over its unplayed part.
 *
 * The stock track is kept so the bar looks exactly as it did; this only adds one draw pass on top,
 * starting where the unplayed track starts (past the thumb and the gap Material leaves beside it)
 * and shaped to the track's own corners so nothing shows outside it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BufferedSliderTrack(
  sliderState: androidx.compose.material3.SliderState,
  progress: Float,
  buffered: List<BufferedSegment>,
  bufferedColor: Color,
) {
  // No handle, so no gap either side of one and no end marker: watched runs straight into
  // buffered, and buffered into what is left. See the note where the slider is built.
  val thumbGap = 0.dp
  val insideCorner = 0.dp
  Box {
    SliderDefaults.Track(
      sliderState = sliderState,
      drawStopIndicator = null,
      thumbTrackGapSize = thumbGap,
      trackInsideCornerSize = insideCorner,
    )
    if (buffered.isNotEmpty()) {
      Box(
        modifier = Modifier.matchParentSize().drawBehind {
          val inside = androidx.compose.ui.geometry.CornerRadius(insideCorner.toPx(), insideCorner.toPx())
          val outside = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
          val unplayedStart = size.width * progress.coerceIn(0f, 1f) + thumbGap.toPx()
          buffered.forEach { segment ->
            val left = maxOf(size.width * segment.start, unplayedStart)
            val right = (size.width * segment.end).coerceAtMost(size.width)
            if (right - left >= 1f) {
              val reachesEnd = right >= size.width - 0.5f
              val shape = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                  androidx.compose.ui.geometry.RoundRect(
                    left = left,
                    top = 0f,
                    right = right,
                    bottom = size.height,
                    topLeftCornerRadius = inside,
                    topRightCornerRadius = if (reachesEnd) outside else inside,
                    bottomRightCornerRadius = if (reachesEnd) outside else inside,
                    bottomLeftCornerRadius = inside,
                  ),
                )
              }
              drawPath(shape, bufferedColor)
            }
          }
        },
      )
    }
  }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBottomControls(
  currentTime: Double,
  duration: Double,
  progress: Float,
  error: String?,
  resizeMode: String,
  speed: Float,
  onProgressChange: (Float) -> Unit,
  onProgressFinished: () -> Unit,
  onZoom: () -> Unit,
  onSpeed: () -> Unit,
  onSubtitles: () -> Unit,
  isLive: Boolean,
  isVod: Boolean,
  showLiveProgress: Boolean,
  onToggleLiveProgress: () -> Unit,
  onAudio: () -> Unit,
  onSources: () -> Unit,
  onEngine: () -> Unit,
  onInfo: () -> Unit,
  showLabels: Boolean,
  layout: String,
  /** A live channel's captions, once the stream has been seen to carry some. */
  captionsAvailable: Boolean = false,
  captionsOn: Boolean = false,
  onCaptions: () -> Unit = {},
  /** What the engine is holding, drawn on the bar ahead of the playhead. */
  bufferedRanges: List<BufferedRange> = emptyList(),
  /** Only a series has episodes to browse; for everything else the control is not drawn at all. */
  showEpisodes: Boolean = false,
  onEpisodes: () -> Unit = {},
) {
  val badge = LocalPlayerBadgeSetting.current
  // Measured against the time the bar is showing, which follows the finger during a drag, so the
  // buffered tone never appears behind the thumb.
  val buffered = remember(bufferedRanges, duration, currentTime) { bufferedSegments(bufferedRanges, duration, currentTime) }
  // A live channel with no seekable window reports no duration, so there is no bar to draw and
  // nothing to drag. The elapsed time and a LIVE marker still answer what the toggle was asked
  // for - how long this has been playing - rather than showing a slider pinned at zero.
  //
  // Only live sessions take that path. A VOD reports no duration too for the moment before it
  // has loaded, and there the slider it is about to fill is the right thing to show.
  val liveWithoutWindow = isLive && duration <= 0.0
  val showProgressRow = !isLive || showLiveProgress
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.78f))))
      .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (!error.isNullOrBlank()) Text(error, color = Color(0xFFFF9A9A), style = MaterialTheme.typography.bodySmall)
    if (showProgressRow) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.width(78.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.44f)).padding(vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) { Text(formatClock(currentTime), color = Color.White.copy(alpha = 0.92f)) }
        Spacer(Modifier.weight(0.075f))
        if (liveWithoutWindow) {
          Box(modifier = Modifier.weight(0.85f).height(48.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
          }
        } else {
          // The progress bar takes the dark scheme's colours in every appearance, because the
          // surface it is drawn on is the video rather than a page. Left to the ambient scheme it
          // picked up Light Mode's `surfaceVariant` for the unplayed track, which is very nearly
          // white: a bright bar laid across the picture, disconnected from the white-on-black
          // controls either side of it.
          //
          // Done by handing this one control the dark scheme rather than by naming colours here, so
          // played, unplayed and thumb keep exactly the relationship Dark Mode already gives them —
          // including the accent from the viewer's chosen theme — with nothing to keep in step.
          MaterialTheme(colorScheme = LocalDarkColorScheme.current ?: MaterialTheme.colorScheme) {
            // Watched is the theme's accent, as before; buffered is that same accent, softened.
            val bufferedColor = playerBufferedColor(MaterialTheme.colorScheme.primary)
            if (layout == "Minimal") {
              RefinedPlayerSlider(
                progress = progress,
                onProgressChange = onProgressChange,
                onProgressFinished = onProgressFinished,
                modifier = Modifier.weight(0.85f),
                buffered = buffered,
                bufferedColor = bufferedColor,
              )
            } else {
              // Normal and Compact retain the original Material player progress bar. The
              // surrounding 7.5% spacers shorten it by 15% while keeping it exactly centred.
              Slider(
                value = progress,
                onValueChange = onProgressChange,
                onValueChangeFinished = onProgressFinished,
                modifier = Modifier.weight(0.85f).graphicsLayer { scaleY = if (layout == "Compact") 0.92f else 1f },
                // The bar draws no handle: the end of the watched fill is the position, and a
                // handle sat exactly where the buffered stretch begins and hid it. The whole bar
                // still takes the drag, so seeking is unchanged.
                thumb = { androidx.compose.foundation.layout.Spacer(Modifier.size(0.dp)) },
                track = { sliderState -> BufferedSliderTrack(sliderState, progress, buffered, bufferedColor) },
              )
            }
          }
        }
        // With the badge switched off a window-less channel has nothing to put in this pill, so the
        // pill goes too and the rule takes its room, rather than leaving an empty capsule behind.
        if (!liveWithoutWindow || badge.visible) Spacer(Modifier.weight(0.075f))
        if (!liveWithoutWindow || badge.visible) Box(
          modifier = Modifier.width(96.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.44f)).padding(vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          if (liveWithoutWindow) {
            // Scaled by 0.6 to match the badge over the picture. The pill around it keeps its size:
            // it is shared with the duration clock this replaces, and shrinking it here would move
            // the controls row about depending on whether the source happens to be live.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.6.dp)) {
              if (isVod) {
                Icon(StreamDekPlayerIcons.Play, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(7.2.dp))
              } else {
                Box(modifier = Modifier.size(3.6.dp).clip(CircleShape).background(Color(0xFFE11D48)))
              }
              Text(if (isVod) "VOD" else "LIVE", color = Color.White.copy(alpha = 0.92f), fontSize = 6.6.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.36.sp)
            }
          } else {
            Text(formatClock(duration), color = Color.White.copy(alpha = 0.92f))
          }
        }
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      val minimal = layout == "Minimal"
      Row(
        modifier = Modifier
          .then(if (minimal) Modifier.widthIn(max = 620.dp).horizontalScroll(rememberScrollState()) else Modifier)
          .clip(if (minimal) RoundedCornerShape(14.dp) else StreamDekRadius.sheetShape)
          .background(if (minimal) Color.Black.copy(alpha = 0.30f) else Color(0xD9161A23))
          .then(if (minimal) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), StreamDekRadius.sheetShape))
          .padding(
            horizontal = when (layout) { "Compact" -> 12.dp; "Minimal" -> 7.dp; else -> 18.dp },
            // Normal is 48dp touch targets inside 4dp of padding top and bottom, 56dp overall.
            // Minimal retains its own low-profile treatment.
            vertical = when (layout) { "Compact" -> 5.dp; "Minimal" -> 2.dp; else -> 4.dp },
          ),
        horizontalArrangement = Arrangement.spacedBy(when (layout) { "Compact" -> 32.dp; "Minimal" -> 6.dp; else -> 15.5.dp }),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Episodes leads the dock: it is the one control here that changes what is playing rather
        // than how, and the first place is where it is found without reading the row.
        if (showEpisodes) {
          PlayerDockButton(stringResource(R.string.detail_episodes), StreamDekNavIcons.LibraryOutline, onEpisodes, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
        }
        PlayerDockButton(stringResource(R.string.player_zoom), StreamDekPlayerIcons.Zoom, onZoom, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
        if (isLive) {
          // A channel's own captions, first after zoom as a film's subtitles are; only once the
          // stream has actually carried some, so the button never offers nothing.
          if (captionsAvailable) {
            PlayerDockButton(
              stringResource(R.string.player_captions),
              if (captionsOn) StreamDekPlayerIcons.Captions else StreamDekPlayerIcons.CaptionsOff,
              onCaptions,
              active = captionsOn,
              showLabel = showLabels && !minimal,
              compact = layout == "Compact",
              minimal = minimal,
            )
          }
          PlayerDockButton(
            stringResource(R.string.player_progress),
            if (showLiveProgress) StreamDekPlayerIcons.Progress else StreamDekPlayerIcons.ProgressOff,
            onToggleLiveProgress,
            active = showLiveProgress,
            showLabel = showLabels && !minimal,
            compact = layout == "Compact",
            minimal = minimal,
          )
          PlayerDockButton(
            stringResource(R.string.player_live_badge),
            StreamDekPlayerIcons.LiveBadge,
            badge.onToggle,
            active = badge.visible,
            showLabel = showLabels && !minimal,
            compact = layout == "Compact",
            minimal = minimal,
          )
        } else {
          PlayerDockButton(stringResource(R.string.player_speed), StreamDekPlayerIcons.Speed, onSpeed, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
          PlayerDockButton(stringResource(R.string.player_subs), StreamDekPlayerIcons.Subtitles, onSubtitles, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
          PlayerDockButton(stringResource(R.string.player_audio), StreamDekPlayerIcons.Audio, onAudio, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
        }
        PlayerDockButton(stringResource(R.string.player_sources), StreamDekPlayerIcons.Sources, onSources, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
        PlayerDockButton(stringResource(R.string.player_engine), StreamDekPlayerIcons.Engine, onEngine, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
        PlayerDockButton(stringResource(R.string.player_info), StreamDekPlayerIcons.Info, onInfo, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
      }
    }
  }
}

@Composable
private fun PlayerDockButton(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
  active: Boolean = false,
  showLabel: Boolean = true,
  compact: Boolean = false,
  minimal: Boolean = false,
) {
  val context = LocalContext.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    // Centred, which only shows in Minimal: there the button is wider than its lone icon, and
    // start-aligned the spare width all fell to the icon's right - so the dock had a margin after
    // its last icon and none before its first.
    horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp, Alignment.CenterHorizontally),
    modifier = Modifier
      .heightIn(min = 48.dp)
      .then(if (minimal) Modifier.widthIn(min = 42.dp) else Modifier)
      .combinedClickable(onClick = onClick, onLongClick = { Toast.makeText(context, label, Toast.LENGTH_SHORT).show() }),
  ) {
    Icon(icon, contentDescription = label, tint = if (active) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.92f), modifier = Modifier.size(if (minimal) 18.dp else if (compact) 19.dp else 21.dp))
    if (showLabel) Text(label, color = if (active) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.78f), fontSize = if (compact) 10.sp else 11.sp, maxLines = 1)
  }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PlayerTopHeader(
  session: PlayerSession,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  isFavourite: Boolean = false,
  onToggleFavourite: () -> Unit = {},
  onHandoff: () -> Unit = {},
  onLock: () -> Unit = {},
  /** The live layout, which can differ from the session's once switched from here. */
  layout: String = session.playerControlLayout,
  onToggleLayout: () -> Unit = {},
) {
  var fullTitleVisible by remember(session.title) { mutableStateOf(false) }
  val reducedMotion = LocalReducedMotion.current
  if (fullTitleVisible) {
    AlertDialog(
      onDismissRequest = { fullTitleVisible = false },
      title = { Text(stringResource(R.string.player_full_title)) },
      text = { Text(listOfNotNull(session.title, episodeContext(session), session.year?.toString()).joinToString(" | ")) },
      confirmButton = { TextButton(onClick = { fullTitleVisible = false }) { Text(stringResource(R.string.action_close)) } },
    )
  }
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.38f), Color.Transparent)))
      // The bottom controls sit inside the navigation bar's inset; so does this, or the two rows
      // would stop lining up whenever the bar is on a side edge.
      .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
      // The trailing edge lines the last icon up with the duration pill below it, which ends 24dp
      // from the edge. The icon is 26dp, centred in a 48dp target, so the target ends 11dp past the
      // icon and the padding is 24 - 11.
      .padding(start = 18.dp, top = 16.dp, end = 13.dp, bottom = 16.dp),
    // 10dp between 48dp targets puts the icons where 14dp between 44dp discs had them.
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top,
  ) {
    val minimal = layout == "Minimal"
    // No disc behind any of the header's controls, in either layout: each is a bare icon over the
    // header's own gradient, in a 48dp target - the same area the discs gave, or a little more.
    PlayerHeaderAction(StreamDekPlayerIcons.Back, stringResource(R.string.action_back), onBack, iconSize = if (minimal) 30.dp else 28.dp)
    Column(modifier = Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      val yearLabel = session.year?.toString().orEmpty()
      val titleLine = listOfNotNull(session.title, episodeContext(session), yearLabel.takeIf { it.isNotBlank() }).joinToString(" | ")
      if (session.playerTitleDisplay != "Hidden") Text(
        text = titleLine,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = if (session.playerTitleDisplay == "Scrolling" && !reducedMotion) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = Modifier
          .fillMaxWidth()
          .then(if (session.playerTitleDisplay == "Scrolling" && !reducedMotion) Modifier.basicMarquee() else Modifier)
          .clickable { fullTitleVisible = true },
      )
      if (session.isLive) {
        Text(
          text = if (session.isProxied) "Proxied stream" else "Direct stream",
          color = (if (session.isProxied) Color(0xFF22C55E) else Color(0xFFCBD5E1)).copy(alpha = 0.86f),
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      } else {
        val detailLine = listOfNotNull(
          session.qualityLabel?.takeIf { it.isNotBlank() },
          session.sizeLabel?.takeIf { it.isNotBlank() },
          session.sourceLabel?.takeIf { it.isNotBlank() },
        ).joinToString(" | ")
        if (detailLine.isNotBlank()) {
          Text(text = detailLine, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    // The layout switch lives in this header because the header is the one part of the controls
    // that both layouts share: in the dock it would sit inside the thing it changes, and someone in
    // Minimal could lose the way back. The icon says which way a tap goes — fewer controls or more.
    PlayerHeaderAction(
      if (minimal) StreamDekPlayerIcons.MoreControls else StreamDekPlayerIcons.FewerControls,
      stringResource(if (minimal) R.string.player_switch_to_normal_controls else R.string.player_switch_to_minimal_controls),
      onToggleLayout,
    )
    // Lock sits beside handoff rather than in the bottom dock, which is where the info control
    // now is. Both are one-tap actions on the session rather than settings, so they belong to the
    // same group.
    PlayerHeaderAction(StreamDekPlayerIcons.LockOpen, stringResource(R.string.a11y_lock_controls), onLock)
    // Live streams get the same handoff control as VOD, and it sits to the left of the
    // favourites star because it is declared first in this Row.
    PlayerHeaderAction(StreamDekPlayerIcons.HandOff, stringResource(R.string.player_hand_off_to_tv), onHandoff)
    if (session.isLive) {
      val favouriteLabel = if (isFavourite) "Remove from favourites" else "Add to favourites"
      PlayerHeaderAction(
        if (isFavourite) StreamDekPlayerIcons.Star else StreamDekPlayerIcons.StarOutline,
        favouriteLabel,
        onToggleFavourite,
        tint = if (isFavourite) Color(0xFFFACC15) else Color.White,
      )
    }
  }
}

@Composable
private fun PlayerSourceCard(
  stream: AddonStream,
  active: Boolean,
  onClick: () -> Unit,
  showDownload: Boolean = false,
  onDownload: () -> Unit = {},
) {
  val header = listOfNotNull(stream.addonName.takeIf { it.isNotBlank() } ?: stream.addonId, stream.source?.takeIf { it.isNotBlank() }, stream.quality?.takeIf { it.isNotBlank() }).distinct().joinToString("  ")
  // Deduplicated across all three fields, not just joined. Plenty of sources fill `name`, `title`
  // and `description` with the same string, and this row printed it once per field -- "FebBox - 4K
  // FebBox - 4K" over a third copy of itself. Whichever field said it first keeps it.
  val said = hashSetOf<String>()
  fun freshText(value: String?): String? =
    value?.takeIf { it.isNotBlank() && said.add(streamTextFingerprint(it)) }
  val metaLine = listOfNotNull(freshText(stream.name), freshText(stream.title)).joinToString("  ")
  val supportingLine = listOfNotNull(
    freshText(stream.description),
    stream.cachedBy.takeIf { it.isNotEmpty() }?.joinToString(", "),
  ).joinToString(" | ")
  val shape: Shape = StreamDekRadius.panelShape
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(if (active) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f))
      .border(1.dp, if (active) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f), shape)
      .clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = if (showDownload) 58.dp else 18.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(header, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
        // Beside the name, as on the television. Switching source mid-film is usually a choice
        // between things that have already disappointed you once, and "which of these is even the
        // same kind of source" was not answerable from a list of names.
        streamOriginLabel(stream, stringResource(R.string.stream_origin_addon))?.let {
          Text(
            it,
            color = Color.White.copy(alpha = 0.52f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.weight(1f))
      }
      if (metaLine.isNotBlank()) {
        Text(metaLine, color = Color.White.copy(alpha = 0.74f), style = MaterialTheme.typography.bodyMedium)
      }
      if (supportingLine.isNotBlank()) {
        Text(supportingLine, color = Color.White.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StreamInfoPill(icon = Icons.Rounded.HighQuality, label = stream.quality ?: "Stream")
        // Read out of the add-on's whole text rather than the size field alone: most sources put it
        // in the release name, and asking only for the field left this pill off nearly every row
        // while the loading screen a second later showed the size it had scraped from the same text.
        streamSizeLabel(stream)?.let {
          StreamInfoPill(icon = Icons.Rounded.Sensors, label = stringResource(R.string.stream_info_bracketed, it), containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), contentColor = MaterialTheme.colorScheme.primary)
        }
        if (active) {
          StreamInfoPill(
            icon = StreamDekPlayerIcons.Engine,
            label = stringResource(R.string.player_now_playing),
            containerColor = Color(0xFF22C55E).copy(alpha = 0.20f),
            contentColor = Color(0xFF22C55E),
          )
        }
      }
    }
    if (showDownload) {
      IconButton(onClick = onDownload, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(36.dp)) {
        Icon(StreamDekPlayerIcons.Download, contentDescription = stringResource(R.string.a11y_download_offline), tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(18.dp))
      }
    }
  }
}

@Composable
private fun StreamInfoPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  containerColor: Color = Color.Black.copy(alpha = 0.28f),
  contentColor: Color = Color.White.copy(alpha = 0.82f),
) {
  Row(
    modifier = Modifier.clip(StreamDekRadius.pill).background(containerColor).padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
    Text(label, color = contentColor, style = MaterialTheme.typography.labelMedium)
  }
}

private fun buildPlayerSourceLabel(stream: AddonStream): String? =
  listOfNotNull(stream.addonName.takeIf { it.isNotBlank() }, stream.source?.takeIf { it.isNotBlank() }, stream.quality, stream.size?.takeIf { it.isNotBlank() }?.let { "[$it]" }).distinct().joinToString(" • ").ifBlank { stream.name }

@Composable
private fun PlayerModalPanel(title: String, onClose: () -> Unit, trailing: @Composable (() -> Unit)? = null, compact: Boolean = false, content: @Composable () -> Unit) {
  val panelSizeModifier = if (compact) Modifier.fillMaxWidth(0.44f) else Modifier.fillMaxWidth(0.62f).fillMaxHeight(0.86f)
  Box(
    modifier = Modifier.fillMaxSize().clickable(onClick = onClose),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = panelSizeModifier
        .clip(StreamDekRadius.sheetShape)
        .background(Color(0xEE111722))
        .clickable(onClick = {})
        .padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Weighted, and the buttons are not. A Row measures its unweighted children first, so the
        // close button keeps its natural width and the heading takes whatever is left - which is
        // what stops a long title (German's "Wiedergabegeschwindigkeit" against English's
        // "Playback Speed") from crushing the button into a column one letter wide.
        Text(
          title,
          modifier = Modifier.weight(1f),
          color = Color.White,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Black,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          trailing?.invoke()
          Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White), shape = StreamDekRadius.cardShape) { Text(stringResource(R.string.action_close)) }
        }
      }
      val panelContentModifier = if (compact) Modifier.fillMaxWidth().heightIn(max = 220.dp) else Modifier.weight(1f).fillMaxWidth()
      Column(
        modifier = panelContentModifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        content()
      }
    }
  }
}
/**
 * What is playing and how it is arriving.
 *
 * Split into what the source said about itself (provider, transport, advertised size and quality)
 * and what the engine is actually seeing (resolution, codecs, transfer rate). Those two disagree
 * often enough — a 1080p-labelled release that decodes at 720p, a "cached" source crawling at
 * 200 KB/s — that collapsing them into one list would hide the disagreement worth seeing.
 */
@Composable
private fun PlayerStreamInfo(
  session: PlayerSession,
  stats: PlaybackStats?,
  engine: ActivePlaybackEngine,
  duration: Double,
) {
  val stream = session.currentStream
  val transport = remember(stream, session.url) { streamTransport(stream, session.url) }
  val sourceRows = buildList {
    streamProviderLabel(stream, session.sourceLabel)?.let { add(stringResource(R.string.player_info_provider) to it) }
    // The television's wording, and its distinction: the provider is who served this, and this is
    // what they are on this account — an add-on, a plugin out of a named collection, or a file
    // already here. Two providers with the same name can be different things entirely.
    streamOriginLabel(stream, stringResource(R.string.stream_origin_addon))?.let { add(stringResource(R.string.player_info_installed_as) to it) }
    add(stringResource(R.string.player_info_delivery) to stringResource(transport.labelRes))
    session.sizeLabel?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.player_info_size) to it) }
    session.qualityLabel?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.player_quality) to it) }
    stream?.filename?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.player_info_file) to it) }
    if (session.isLive) {
      add(
        stringResource(R.string.player_info_route) to
          stringResource(if (session.isProxied) R.string.player_info_route_proxied else R.string.player_info_route_direct),
      )
    }
  }
  val playbackRows = buildList {
    formatTransferRate(stats?.bytesPerSecond)?.let { add(stringResource(R.string.player_info_speed) to it) }
    formatResolution(stats?.width ?: 0, stats?.height ?: 0)?.let { add(stringResource(R.string.player_info_resolution) to it) }
    val videoLine = listOfNotNull(
      prettyCodecName(stats?.videoCodec),
      stats?.videoProfile,
      stats?.videoBitDepth?.let { String.format(Locale.US, "%d-bit", it) },
      formatBitrate(stats?.videoBitrateBps),
      stats?.frameRate?.let { String.format(Locale.US, "%.0f fps", it) },
    ).joinToString(" · ")
    if (videoLine.isNotBlank()) add(stringResource(R.string.player_info_video) to videoLine)
    val audioLine = listOfNotNull(
      prettyCodecName(stats?.audioCodec),
      stats?.audioChannels?.let { channels ->
        if (channels > 2) "${channels}ch" else stringResource(if (channels == 2) R.string.player_audio_stereo else R.string.player_audio_mono)
      },
      stats?.audioSampleRateHz?.let { String.format(Locale.US, "%.1f kHz", it / 1000.0) },
      formatBitrate(stats?.audioBitrateBps),
      stats?.audioLanguage?.let { Languages.label(it) },
    ).joinToString(" · ")
    if (audioLine.isNotBlank()) add(stringResource(R.string.player_audio) to audioLine)
    formatBitrate(stats?.contentBitrateBps)?.let { add(stringResource(R.string.player_info_bitrate_now) to it) }
    stats?.decodedFrames?.let { decoded ->
      add(stringResource(R.string.player_info_frames) to stringResource(R.string.player_info_frames_value, decoded.toString(), (stats?.droppedFrames ?: 0L).toString()))
    }
    stats?.bufferedSeconds?.let { add(stringResource(R.string.player_info_buffered) to String.format(Locale.US, "%.0f s ahead", it)) }
    stats?.hardwareDecoder?.let { add(stringResource(R.string.player_info_decoder) to it) }
    add(stringResource(R.string.player_engine) to engine.displayName)
    if (duration > 0.0) add(stringResource(R.string.player_info_runtime) to formatClock(duration))
  }
  Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
    PlayerInfoSection(stringResource(R.string.player_info_source), sourceRows)
    PlayerInfoSection(stringResource(R.string.player_info_playback), playbackRows)
    if (stats == null) {
      Text(
        stringResource(R.string.player_reading_details),
        color = Color.White.copy(alpha = 0.52f),
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun PlayerInfoSection(heading: String, rows: List<Pair<String, String>>) {
  if (rows.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
    Text(
      heading.uppercase(Locale.US),
      color = Color.White.copy(alpha = 0.44f),
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 1.2.sp,
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(StreamDekRadius.cardShape)
        .background(Color.White.copy(alpha = 0.06f))
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
      rows.forEach { (label, value) ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
          Text(label, color = Color.White.copy(alpha = 0.56f), style = MaterialTheme.typography.bodyMedium)
          Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun PlayerOptionRow(label: String, selected: Boolean, supportingText: String? = null, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clip(StreamDekRadius.thumbShape).background(if (selected) Color.White else Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(label, color = if (selected) Color.Black else Color.White, style = MaterialTheme.typography.titleMedium)
      supportingText?.takeIf { it.isNotBlank() }?.let {
        Text(it, color = if (selected) Color.Black.copy(alpha = 0.68f) else Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
      }
    }
    if (selected) Icon(StreamDekPlayerIcons.Check, contentDescription = stringResource(R.string.a11y_selected), tint = Color.Black, modifier = Modifier.size(22.dp))
  }
}

/**
 * A track's language spelled out, rather than the tag the container happened to carry.
 *
 * Containers say "eng", "fre", "pt-BR" and occasionally "English"; none of those is what a viewer
 * is looking for when they open this list to find their own language. [Languages] already knows
 * every spelling, so the tag is resolved through it and the full name shown instead. A tag it does
 * not recognise is left as it was written — a track labelled with something private to one encoder
 * is still better identified by that than by "Unknown".
 */
/** "CEA-608" and the like, for a caption row, from whatever the engine called the format. */
private fun captionFormatLabel(codec: String?): String? {
  val value = codec?.lowercase() ?: return null
  return when {
    "608" in value -> "CEA-608"
    "708" in value -> "CEA-708"
    "webvtt" in value || "vtt" in value -> "WebVTT"
    "ttml" in value || "stpp" in value -> "TTML"
    "dvb" in value -> "DVB"
    else -> null
  }
}

private fun trackLanguageName(raw: String?): String? {
  val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val normalized = Languages.normalize(value)
  return if (normalized.isEmpty()) value.uppercase() else Languages.label(normalized)
}

private fun trackLabel(track: MpvTrackInfo): String =
  listOfNotNull(trackLanguageName(track.language), track.title, track.codec).joinToString(" - ").ifBlank { "Track ${track.id}" }

/** How many copies of the same language to try before telling the viewer none of them loaded. */
private const val SUBTITLE_ATTEMPT_LIMIT = 4

/** How long a finger has to stay put before a press becomes a speed boost. */
private const val HOLD_TO_SPEED_DELAY_MS = 350L

/** A full sweep across the screen scrubs this far, so the gesture feels the same at any runtime. */
private const val SCRUB_FULL_WIDTH_SECONDS = 120f

private const val MIN_PLAYBACK_SPEED = 0.25f
private const val MAX_PLAYBACK_SPEED = 4f

/** "2x" rather than "2.0x", but "1.5x" keeps its half. */
/**
 * A playback multiplier as the chosen language writes it: "1,5" in German, "1.5" in English.
 *
 * This used `"%.2f".format(...)`, which formats against the *device* locale rather than the app's.
 * On an English phone set to German that produced "1.5x" beside otherwise German text, and on a
 * German phone set to English the reverse. Going through [AppFormats] ties the separator to the
 * language actually on screen.
 */
@Composable
internal fun formatPlaybackSpeed(speed: Float): String {
  val clamped = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
  val language = LocalAppLanguage.current
  // Whole numbers stay whole - "2x", never "2,00x".
  return if (clamped % 1f == 0f) AppFormats.number(language, clamped.toInt())
  else AppFormats.number(language, clamped, decimals = 2).trimEnd('0').trimEnd(',', '.')
}

private fun formatClock(seconds: Double): String {
  if (seconds.isNaN() || seconds <= 0.0) return "0:00"
  val total = seconds.toInt()
  val hours = total / 3600
  val minutes = (total % 3600) / 60
  val secs = total % 60
  return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}
internal fun playerStreamIdentity(stream: AddonStream?): String =
  stream?.let(::addonStreamPlaybackIdentity).orEmpty()

/** A source favourite deliberately excludes URL, headers, tokens and debrid links: those expire. */
internal fun stableSourceFavouriteKey(stream: AddonStream): String = listOf(
  stream.addonId.ifBlank { stream.addonName },
  stream.source.orEmpty(),
  stream.name.orEmpty(),
  stream.title.orEmpty(),
  stream.quality.orEmpty(),
).joinToString("|") { it.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ") }.take(512)

internal fun playerDoubleTapSeekDelta(
  horizontalFraction: Float,
  enabled: Boolean,
  stepSeconds: Int,
  seekable: Boolean,
): Int? = when {
  !enabled || !seekable -> null
  horizontalFraction < 0.35f -> -stepSeconds.coerceIn(5, 15)
  horizontalFraction > 0.65f -> stepSeconds.coerceIn(5, 15)
  else -> null
}

internal fun isPlayerCenterDoubleTap(horizontalFraction: Float, enabled: Boolean): Boolean =
  enabled && horizontalFraction in 0.35f..0.65f

internal fun clampedPlayerSeekPosition(positionSeconds: Double, deltaSeconds: Int, durationSeconds: Double): Double =
  (positionSeconds + deltaSeconds).coerceIn(0.0, durationSeconds.coerceAtLeast(0.0))

private val playerHttpClient = OkHttpClient()

/**
 * What a subtitle download presents itself as.
 *
 * The same reasoning as the plugin sandbox's: a request with no User-Agent is refused outright by
 * some of the hosts these add-ons point at, and a refusal arrives as a subtitle that simply never
 * appears. Matching a browser is what gets the file.
 */
private const val SUBTITLE_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/131.0.0.0 Safari/537.36"

/**
 * The two-letter code for a subtitle's language, however the source spelled it.
 *
 * Delegates to [Languages] rather than the nine-language table this used to be: a viewer whose
 * language was not on that list had every subtitle sorted as "other", so their own language ranked
 * below English.
 */
private fun normalizeSubtitleLanguage(language: String?): String = Languages.normalize(language)

/** Where a subtitle language sits in the viewer's order of preference. */
private fun subtitleLanguageRank(language: String?, session: PlayerSession): Int {
  val normalized = Languages.normalize(language)
  val preferred = preferredSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
  val index = preferred.indexOf(normalized)
  return when {
    index >= 0 -> index
    // English is a reasonable third choice for most viewers, but never above a stated preference.
    normalized == "en" -> preferred.size
    else -> preferred.size + 1
  }
}

/**
 * Drops subtitles in languages the viewer did not ask for, when they have asked for that.
 *
 * Never empties the list: a filter that leaves nothing to choose from is worse than an unfiltered
 * list, since the viewer is then stuck with no subtitles and no way to pick any from here.
 */
private fun List<ExternalSubtitle>.filterPreferredSubtitleLanguages(session: PlayerSession): List<ExternalSubtitle> {
  val preferred = preferredSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
  if (preferred.isEmpty()) return this
  val matching = filter { Languages.normalize(it.language) in preferred }
  if (session.showOnlyPreferredSubtitleLanguages) return matching
  if (session.addonSubtitleLoading != "preferred") return this
  val builtIn = filter { it.origin == ExternalSubtitleOrigin.BuiltIn }
  val addons = filter { it.origin == ExternalSubtitleOrigin.Addon }
  val matchingAddons = addons.filter { Languages.normalize(it.language) in preferred }
  return builtIn + matchingAddons.ifEmpty { addons }
}

/**
 * What this subtitle actually is, read from the file rather than from its address.
 *
 * The extension used to be taken from the URL, which for these sources answers nothing useful --
 * `substringAfterLast('.')` on "https://subs5.strem.io/en/download/.../file/1962235234" returns
 * the whole tail after ".io", so every file was saved as .srt whatever it held. A WebVTT or ASS
 * file handed to a player as SubRip parses to nothing, and nothing is exactly what the viewer
 * sees: no error, no subtitles, no way to tell which happened.
 */
internal fun subtitleExtensionFor(text: String): String {
  val head = text.take(4_096)
  return when {
    head.trimStart().startsWith("WEBVTT") -> "vtt"
    head.contains("[Events]", ignoreCase = true) && head.contains("Dialogue:", ignoreCase = true) -> "ass"
    head.contains("[Script Info]", ignoreCase = true) -> "ass"
    Regex("""<tt[\s>]""", RegexOption.IGNORE_CASE).containsMatchIn(head) -> "ttml"
    else -> "srt"
  }
}

/**
 * Text out of whatever the source encoded it in.
 *
 * OpenSubtitles alone serves both UTF-8 and CP1252 for the same title -- it says so in the listing
 * -- and a CP1252 file read as UTF-8 loses every accented character to a replacement glyph. Strict
 * decoding is what tells the two apart: real UTF-8 either decodes or throws, so a failure is a
 * reliable signal to fall back rather than a guess. Everything is rewritten as UTF-8 so the players
 * only ever see one encoding.
 */
internal fun decodeSubtitleBytes(bytes: ByteArray): String {
  if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
    return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
  }
  if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
    return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
  }
  val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
    bytes.copyOfRange(3, bytes.size)
  } else {
    bytes
  }
  return runCatching {
    Charsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(body))
      .toString()
  }.getOrElse { String(body, charset("windows-1252")) }
}

internal fun subtitleTextHasCues(text: String, extension: String = subtitleExtensionFor(text)): Boolean {
  val sample = text.take(256_000)
  return when (extension) {
    "vtt", "srt" -> Regex("""(?m)^\s*(?:\d{1,2}:)?\d{2}:\d{2}[,.]\d{3}\s*-->\s*(?:\d{1,2}:)?\d{2}:\d{2}[,.]\d{3}""").containsMatchIn(sample)
    "ass" -> Regex("""(?mi)^Dialogue:\s*\d+,""").containsMatchIn(sample)
    "ttml" -> Regex("""(?is)<p\b[^>]*(?:begin|end|dur)=""").containsMatchIn(sample)
    else -> false
  }
}

/**
 * Downloads a remote subtitle to the app cache and returns the local path.
 *
 * mpv's sub-add blocks the playback loop while it opens network streams, so feeding it a local
 * file keeps the video playing during subtitle switches. The file is normalised on the way in --
 * named for what it actually is, and written as UTF-8 -- so that by the time either engine is
 * handed it, the only thing left that can go wrong is the choosing.
 */
private suspend fun downloadSubtitleToCache(context: Context, url: String): String? = withContext(Dispatchers.IO) {
  runCatching {
    if (!url.startsWith("http", ignoreCase = true)) return@runCatching url
    val stem = File(context.cacheDir, "subtitles/${url.hashCode().toUInt()}")
    // Any extension already written for this URL will do; the content decided it last time.
    stem.parentFile?.listFiles { file -> file.name.startsWith(stem.name + ".") }
      ?.firstOrNull { it.length() > 0L }
      ?.let { cached ->
        if (runCatching { subtitleTextHasCues(cached.readText()) }.getOrDefault(false)) return@runCatching cached.absolutePath
        cached.delete()
      }
    stem.parentFile?.mkdirs()
    // Sent as a browser, because several of these hosts answer anything else with a refusal
    // rather than a file -- PenguPlay's returns 403 to a header-less request. The plugin sandbox
    // already learned this lesson; the subtitle fetcher had not. A referer is offered too, since
    // hot-link protection is the usual reason behind the filter.
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", SUBTITLE_USER_AGENT)
      .header("Accept", "*/*")
      .apply {
        runCatching { java.net.URI(url) }.getOrNull()
          ?.let { uri -> uri.scheme?.let { scheme -> uri.host?.let { host -> "$scheme://$host/" } } }
          ?.let { header("Referer", it) }
      }
      .build()
    val bytes = playerHttpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) error("Subtitle download failed: ${response.code}")
      response.body?.bytes() ?: error("Empty subtitle body")
    }
    val text = decodeSubtitleBytes(bytes)
    if (text.isBlank()) error("Subtitle file was empty")
    val extension = subtitleExtensionFor(text)
    if (!subtitleTextHasCues(text, extension)) error("Subtitle response contained no timed cues")
    val file = File(stem.parentFile, stem.name + "." + extension)
    file.writeText(text, Charsets.UTF_8)
    Log.i("StreamDekSubtitles", "cached " + file.name + " (" + bytes.size + " bytes) from " + url.substringBefore('?').take(90))
    file.absolutePath
  }.onFailure {
    Log.w("StreamDekSubtitles", "could not cache subtitle " + url.substringBefore('?').take(90), it)
  }.getOrNull()
}

private suspend fun fetchExternalSubtitles(session: PlayerSession, userSources: List<UserSubtitleSource>): List<ExternalSubtitle> = withContext(Dispatchers.IO) {
  // Every add-on in this fan-out is a Stremio subtitles endpoint, and those are addressed by IMDb
  // id -- there is no other identifier to ask them with. A session that reaches here without one
  // therefore has no add-on subtitles available at all, which is worth saying out loud: silence
  // here is indistinguishable from every source having nothing, and it is not the same problem.
  val imdbId = session.imdbId?.takeIf { it.startsWith("tt") } ?: run {
    Log.w(
      "StreamDekSubtitles",
      "no IMDb id on this session (title=" + session.title + ", id=" + session.imdbId + "), " +
        "so no add-on can be asked for subtitles",
    )
    return@withContext emptyList()
  }
  val series = session.mediaType == "tv" || session.mediaType == "series"
  val videoId = if (series) {
    val season = session.seasonNumber ?: return@withContext emptyList()
    val episode = session.episodeNumber ?: return@withContext emptyList()
    "$imdbId:$season:$episode"
  } else imdbId
  val type = if (series) "series" else "movie"
  val sourcePreference = normalizeSubtitleDefaultSource(session.subtitleDefaultSource)
  val sources = buildList {
    if (sourcePreference != "Addons") {
      add(UserSubtitleSource("opensubtitles", "OpenSubtitles", "https://opensubtitles-v3.strem.io"))
    }
    addAll(userSources.filterNot { source ->
      val origin = externalSubtitleOrigin(source.id)
      !subtitleSourceAllowsOrigin(sourcePreference, origin) ||
        (origin == ExternalSubtitleOrigin.Addon && session.addonSubtitleLoading == "off")
    })
  }.distinctBy { it.baseUrl.lowercase() }

  // Every source is reported, answered or not. Failures used to be swallowed whole -- a source
  // that 404s, times out, is configured wrong or simply has nothing for this title all produced
  // the same empty list and the same silent absence from the panel, which is not a thing anyone
  // can debug from the outside. One line per source says which of those happened.
  Log.i("StreamDekSubtitles", "asking " + sources.size + " source(s) for " + type + "/" + videoId)
  sources.flatMap { source ->
    runCatching {
      val endpoint = "${source.baseUrl.trimEnd('/')}/subtitles/$type/$videoId.json"
      val request = Request.Builder().url(endpoint).header("Accept", "application/json").build()
      playerHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Log.w("StreamDekSubtitles", source.name + " answered HTTP " + response.code + " for " + endpoint)
          return@use emptyList()
        }
        val entries = JSONObject(response.body?.string().orEmpty()).optJSONArray("subtitles") ?: JSONArray()
        buildList {
          for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val url = item.optString("url").trim()
            val language = sequenceOf("lang", "language", "languageCode", "locale")
              .map { key -> normalizeSubtitleLanguage(item.optString(key)) }
              .firstOrNull { it.isNotBlank() }
              .orEmpty()
            if (id.isBlank() || url.isBlank() || language.isBlank()) continue
            val release = item.optString("m").trim()
            // Named, not coded. "FR - <release> - OpenSubtitles" asks a viewer to know that FR is
            // French before they can pick their own language out of eighty rows; the add-on's
            // two-letter tag is what this list is sorted by, not what it should be read by.
            val origin = externalSubtitleOrigin(source.id)
            val label = listOf(trackLanguageName(language).orEmpty(), release, source.name).filter { it.isNotBlank() }.joinToString(" - ")
            add(ExternalSubtitle("${source.id}:$id", language, label, url, origin, source.name, release.ifBlank { null }))
          }
        }.also { parsed ->
          Log.i(
            "StreamDekSubtitles",
            source.name + " returned " + entries.length() + " entries, " + parsed.size + " usable",
          )
        }
      }
    }.onFailure {
      Log.w("StreamDekSubtitles", source.name + " could not be reached at " + source.baseUrl, it)
    }.getOrDefault(emptyList())
  }
    .distinctBy { it.url }
    .filterPreferredSubtitleLanguages(session)
    .sortedWith(compareBy<ExternalSubtitle> { subtitleLanguageRank(it.language, session) }.thenBy { it.label })
    .take(80)
}
private fun parseSegmentTime(value: Any?): Double? {
  if (value is Number) return value.toDouble().takeIf { it.isFinite() && it >= 0.0 }
  val text = value?.toString()?.trim().orEmpty()
  text.toDoubleOrNull()?.let { return it.takeIf { number -> number.isFinite() && number >= 0.0 } }
  val parts = text.split(':').mapNotNull { it.toDoubleOrNull() }
  if (parts.size !in 2..3) return null
  return if (parts.size == 2) parts[0] * 60.0 + parts[1] else parts[0] * 3600.0 + parts[1] * 60.0 + parts[2]
}

private fun normalizeSkipSegment(item: JSONObject, fallbackType: String? = null): SkipSegment? {
  val rawType = item.optString("segment_type").ifBlank { item.optString("type") }.ifBlank { item.optString("kind") }.ifBlank { fallbackType.orEmpty() }
  val type = when (rawType.lowercase()) { "credits", "credit" -> "outro"; else -> rawType.lowercase() }
  if (type !in setOf("intro", "recap", "outro")) return null
  val start = listOf("start_sec", "start", "startSeconds", "start_seconds").firstNotNullOfOrNull { key -> if (item.has(key)) parseSegmentTime(item.opt(key)) else null }
  val end = listOf("end_sec", "end", "endSeconds", "end_seconds").firstNotNullOfOrNull { key -> if (item.has(key)) parseSegmentTime(item.opt(key)) else null }
  if (start == null || end == null || end <= start) return null
  return SkipSegment(type, start, end)
}

private fun extractSkipSegments(body: String): List<SkipSegment> {
  val trimmed = body.trim()
  if (trimmed.isBlank()) return emptyList()
  val entries = mutableListOf<Pair<JSONObject, String?>>()
  if (trimmed.startsWith("[")) {
    val array = JSONArray(trimmed)
    for (index in 0 until array.length()) array.optJSONObject(index)?.let { entries += it to null }
  } else {
    val root = JSONObject(trimmed)
    val array = root.optJSONArray("segments") ?: root.optJSONArray("data")
    if (array != null) {
      for (index in 0 until array.length()) array.optJSONObject(index)?.let { entries += it to null }
    } else {
      listOf("intro", "recap", "outro", "credits").forEach { key -> root.optJSONObject(key)?.let { entries += it to key } }
      if (entries.isEmpty()) entries += root to null
    }
  }
  return entries.mapNotNull { (item, type) -> normalizeSkipSegment(item, type) }.distinctBy { Triple(it.type, it.startSeconds, it.endSeconds) }.sortedBy { it.startSeconds }
}

/**
 * IntroDB reads work without credentials, so a key only raises the rate limit the request is
 * counted against. The viewer's own key wins; otherwise the build supplies StreamDek's, which is
 * blank in builds that were not given one and simply leaves the request anonymous.
 */
private fun introdbRequest(url: String, apiKey: String): Request = Request.Builder()
  .url(url)
  .header("Accept", "application/json")
  .apply { if (apiKey.isNotBlank()) header("x-introdb-api-key", apiKey) }
  .build()

private suspend fun fetchSkipSegments(session: PlayerSession): List<SkipSegment> = withContext(Dispatchers.IO) {
  val durationSec = session.runtimeMinutes?.takeIf { it > 0 }?.times(60.0)
  fun valid(segments: List<SkipSegment>): List<SkipSegment> = segments.filter { segment ->
    segment.startSeconds >= 0.0 && segment.endSeconds > segment.startSeconds &&
      (durationSec == null || (segment.startSeconds < durationSec && segment.endSeconds <= durationSec + 2.0))
  }.distinctBy { Triple(it.type, it.startSeconds, it.endSeconds) }.sortedBy { it.startSeconds }

  fun theIntroDb(): List<SkipSegment> {
    val tmdbId = session.tmdbId ?: return emptyList()
    val url = buildString {
      append(BuildConfig.API_BASE_URL.trimEnd('/')).append(API_PATH_PREFIX).append("/services/timings/theintrodb?tmdb_id=").append(tmdbId)
      session.seasonNumber?.takeIf { session.mediaType == "tv" }?.let { append("&season=").append(it) }
      session.episodeNumber?.takeIf { session.mediaType == "tv" }?.let { append("&episode=").append(it) }
      durationSec?.times(1000.0)?.toLong()?.let { append("&duration_ms=").append(it) }
    }
    val request = Request.Builder().url(url).header("Accept", "application/json").apply {
      session.timingApiToken.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
      session.theIntroDbApiKey.takeIf { it.isNotBlank() }?.let { header("x-theintrodb-api-key", it) }
    }.build()
    val media = runCatching {
      playerHttpClient.newCall(request).apply { timeout().timeout(4500, java.util.concurrent.TimeUnit.MILLISECONDS) }.execute().use { response ->
        if (!response.isSuccessful) return@use null
        TheIntroDbClient.parseMedia(response.body?.string().orEmpty())
      }
    }.getOrNull() ?: return emptyList()
    if (media.tmdbId != null && media.tmdbId != tmdbId) return emptyList()
    if (media.type != null && media.type != session.mediaType) return emptyList()
    fun mapped(type: String, values: List<TheIntroDbTimestamp>) = values.mapNotNull { value ->
      val start = value.startMs / 1000.0
      val end = value.endMs?.div(1000.0) ?: durationSec
      end?.let { SkipSegment(type, start, it) }
    }
    return valid(mapped("intro", media.intro) + mapped("recap", media.recap) + mapped("outro", media.credits))
  }

  fun introDb(): List<SkipSegment> {
    if (session.mediaType != "tv") return emptyList()
    val imdbId = session.imdbId?.takeIf { it.startsWith("tt") } ?: return emptyList()
    val season = session.seasonNumber ?: return emptyList()
    val episode = session.episodeNumber ?: return emptyList()
    val apiKey = session.introdbApiKey.trim()
    return runCatching {
    val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/') + API_PATH_PREFIX + "/services/timings/introdb?imdb_id=$imdbId&season=$season&episode=$episode"
    val request = introdbRequest(baseUrl, apiKey).newBuilder().apply {
      session.timingApiToken.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
    }.build()
    val segments = playerHttpClient.newCall(request).apply { timeout().timeout(4500, java.util.concurrent.TimeUnit.MILLISECONDS) }.execute().use { response -> if (response.isSuccessful) extractSkipSegments(response.body?.string().orEmpty()) else emptyList() }.toMutableList()
    if (segments.none { it.type == "intro" }) {
      val legacy = introdbRequest("$baseUrl&legacy=1", apiKey).newBuilder().apply {
        session.timingApiToken.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
      }.build()
      playerHttpClient.newCall(legacy).execute().use { response ->
        if (response.isSuccessful) {
          val root = JSONObject(response.body?.string().orEmpty())
          val normalized = JSONObject()
            .put("segment_type", "intro")
            .put("start", root.opt("start_sec") ?: root.opt("start") ?: root.opt("intro_start"))
            .put("end", root.opt("end_sec") ?: root.opt("end") ?: root.opt("intro_end"))
          normalizeSkipSegment(normalized)?.let { segments += it }
        }
      }
    }
    valid(segments)
  }.getOrDefault(emptyList())
  }

  val preferred = session.timingProvider.takeIf { it in setOf("introdb", "theintrodb") } ?: "introdb"
  val primary = if (preferred == "theintrodb") theIntroDb() else introDb()
  if (primary.isNotEmpty()) {
    Log.i("StreamDekPlayback", "timing provider=$preferred fallback=none segments=${primary.size}")
    return@withContext primary
  }
  if (!session.timingProviderFallbackEnabled) {
    Log.i("StreamDekPlayback", "timing provider=none preferred=$preferred fallback=disabled")
    return@withContext emptyList()
  }
  val alternate = if (preferred == "theintrodb") introDb() else theIntroDb()
  val actual = if (alternate.isNotEmpty()) if (preferred == "theintrodb") "introdb" else "theintrodb" else "none"
  Log.i("StreamDekPlayback", "timing provider=$actual preferred=$preferred fallback=no_usable_data segments=${alternate.size}")
  alternate
}
private fun streamsRepresentSameSource(candidate: AddonStream, current: AddonStream?): Boolean {
  current ?: return false
  if (addonStreamPlaybackIdentity(candidate) == addonStreamPlaybackIdentity(current)) return true
  // The address alone is not enough: the stream being played can carry a different URL from the
  // row it was chosen from — a debrid link, a proxied or re-signed address — and on that mismatch
  // the playing source was neither pinned to the top nor marked as playing. Same add-on, and the
  // same description of itself, is the same listing.
  return candidate.addonId.isNotBlank() && candidate.addonId == current.addonId &&
    streamListingIdentity(candidate) == streamListingIdentity(current)
}

private fun streamListingIdentity(stream: AddonStream): String =
  listOf(stream.infoHash, stream.fileIdx?.toString(), stream.bingeGroup, stream.filename, stream.name, stream.title, stream.quality, stream.size)
    .joinToString("|") { it.orEmpty().trim().lowercase() }


/**
 * The player's modal panels - audio, subtitles, sources, speed, zoom, engine and info.
 *
 * Lifted out of [NativePlayerScreen] because that composable had grown to 22,050 dex code units,
 * past ART's 10,000-unit huge-method threshold, so the whole player screen was rejected by the JIT
 * and ran interpreted on every device. The panels are the largest self-contained block in it, and
 * only one of them is on screen at a time.
 *
 * The state they write to is passed as [MutableState] handles rather than as value-plus-callback
 * pairs, which keeps the panel bodies below byte-identical to what they were inside the screen --
 * a mechanical move rather than a rewrite of playback-critical UI.
 */
/** Identifies a track across reloads by what it is, not by the index the engine gave it. */
private fun trackPreferenceKey(track: MpvTrackInfo): String = listOf(
  normalizeSubtitleLanguage(track.language).orEmpty(),
  track.title.orEmpty().trim().lowercase(),
).joinToString("|")

/**
 * State that belongs to the piece of content being played rather than to the source it came from.
 *
 * Split from [PlayerSourceState] because it is keyed differently: switching to another source for
 * the same episode keeps the position and paused state, while moving to another episode resets
 * them. Same reasoning as that class -- one remembered slot instead of thirteen.
 */
private class PlayerPlaybackState {
  val isPaused = mutableStateOf(false)
  val pausedForAudioFocus = mutableStateOf(false)
  val currentTime = mutableDoubleStateOf(0.0)
  val duration = mutableDoubleStateOf(0.0)
  val seekIssuedAtMs = mutableStateOf(0L)
  val hasLoaded = mutableStateOf(false)
  val autoSkippedSegments = mutableStateOf(emptySet<String>())
  val autoSkipNotice = mutableStateOf<Int?>(null)
  val playbackEnded = mutableStateOf(false)
  val completionDispatched = mutableStateOf(false)
  val didApplyResume = mutableStateOf(false)
  val lastCheckpointSecond = mutableDoubleStateOf(0.0)
  val recentPlaybackStalls = mutableStateOf(emptyList<Long>())
  val smartSwitchCandidate = mutableStateOf<AddonStream?>(null)
}
/**
 * Everything the player tracks about the source that is currently loaded.
 *
 * These were 42 separate `remember(session.url)` calls in [NativePlayerScreen]. Each one compiles
 * to its own slot lookup, emptiness check and key comparison, and together they were a large part
 * of why that composable reached 15,345 dex code units in release -- past ART's 10,000-unit
 * huge-method threshold, so the whole player screen was rejected by the JIT and ran interpreted.
 *
 * Grouping them behind one `remember` keyed the same way is behaviour-preserving: the holder is
 * rebuilt when the source URL changes, which is exactly when each field used to be rebuilt.
 */
private class PlayerSourceState {
  val error = mutableStateOf<String?>(null)
  val pendingEngineResumeSeconds = mutableDoubleStateOf(0.0)
  /**
   * Whether this source has already been asked again in place after failing mid-playback.
   *
   * One per source, which is what this holder's key gives for free: a switch brings a new URL and
   * a new holder, so the next source arrives with its own attempt. A source that fails twice has
   * said enough and the player moves on.
   */
  val reloadedInPlace = mutableStateOf(false)
  val playbackStats = mutableStateOf<PlaybackStats?>(null)
  /** What the engine is holding, for the seek bar. Empty whenever the bar is not on screen. */
  val bufferedRanges = mutableStateOf<List<BufferedRange>>(emptyList())
  val selectedAudioTrackId = mutableStateOf<Int?>(null)
  val selectedSubtitleTrackId = mutableStateOf<Int?>(null)
  val preferredAudioTrackKey = mutableStateOf<String?>(null)
  val preferredSubtitleTrackKey = mutableStateOf<String?>(null)
  val subtitleDisabledByUser = mutableStateOf(false)
  val subtitleErrorMessage = mutableStateOf<String?>(null)
  val userPickedAudio = mutableStateOf(false)
  val userPickedSubtitle = mutableStateOf(false)
  val externalSubtitles = mutableStateOf<List<ExternalSubtitle>>(emptyList())
  val selectedExternalSubtitleId = mutableStateOf<String?>(null)
  val externalSubtitleNeedsReapply = mutableStateOf(false)
  val subtitlesLoading = mutableStateOf(false)
  val skipSegments = mutableStateOf<List<SkipSegment>>(emptyList())
  val audioTracks = mutableStateListOf<MpvTrackInfo>()
  val subtitleTracks = mutableStateListOf<MpvTrackInfo>()
  val scrobbleStarted = mutableStateOf(false)
  val showControls = mutableStateOf(true)
  val showUnlockControl = mutableStateOf(false)
  val unlockActivityVersion = mutableIntStateOf(0)
  val controlActivityVersion = mutableIntStateOf(0)
  val liveReconnectVersion = mutableIntStateOf(0)
  val liveStalled = mutableStateOf(false)
  val liveRetryAttempts = mutableIntStateOf(0)
  val lastLiveRetryAtMs = mutableStateOf(0L)
  val showPausedInfo = mutableStateOf(false)
  val speedBoostActive = mutableStateOf(false)
  val scrubTargetSeconds = mutableStateOf<Double?>(null)
  val suppressNextTap = mutableStateOf(false)
  val showLiveChannels = mutableStateOf(false)
  val showFavouriteDrawer = mutableStateOf(false)
  val pendingChannelSelection = mutableStateOf<MediaItem?>(null)
  val showChannelSwipeCue = mutableStateOf(false)
  val slowLoadHintVisible = mutableStateOf(false)
  val avMismatchFallbackTried = mutableStateOf(false)
  val loadedVideoWidth = mutableIntStateOf(0)
  val loadedVideoHeight = mutableIntStateOf(0)
  val handoffPickerVisible = mutableStateOf(false)
  val handoffLoading = mutableStateOf(false)
  val handoffError = mutableStateOf<String?>(null)
}

@Composable
private fun PlayerPanels(
  session: PlayerSession,
  playerContext: android.content.Context,
  playerScope: kotlinx.coroutines.CoroutineScope,
  activeEngine: ActivePlaybackEngine,
  duration: Double,
  currentProgressPercent: Double,
  switchEngine: (ActivePlaybackEngine, String) -> Unit,
  audioTracks: List<MpvTrackInfo>,
  subtitleTracks: List<MpvTrackInfo>,
  availableSubtitleTabs: List<SubtitlePanelTab>,
  externalSubtitles: List<ExternalSubtitle>,
  subtitlesLoading: Boolean,
  playbackStats: PlaybackStats?,
  availableStreams: List<AddonStream>,
  downloadsEnabled: Boolean,
  activePanelState: MutableState<PlayerPanel>,
  playbackSpeedState: MutableFloatState,
  subtitleDelayState: MutableFloatState,
  audioDelayState: MutableFloatState,
  subtitleSizeState: MutableIntState,
  subtitlePositionState: MutableIntState,
  subtitleTabState: MutableState<SubtitlePanelTab>,
  subtitleDisabledByUserState: MutableState<Boolean>,
  subtitleErrorMessageState: MutableState<String?>,
  selectedAudioTrackIdState: MutableState<Int?>,
  selectedSubtitleTrackIdState: MutableState<Int?>,
  selectedExternalSubtitleIdState: MutableState<String?>,
  preferredAudioTrackKeyState: MutableState<String?>,
  preferredSubtitleTrackKeyState: MutableState<String?>,
  userPickedAudioState: MutableState<Boolean>,
  userPickedSubtitleState: MutableState<Boolean>,
  activeSetAudioTrack: (Int) -> Unit,
  activeSetSubtitleTrack: (Int) -> Unit,
  activeDisableSubtitleTrack: () -> Unit,
  activeSetSubtitleFontSize: (Int) -> Unit,
  activeSetSubtitlePosition: (Int) -> Unit,
  activeSetSubtitleDelay: (Double) -> Unit,
  activeSetAudioDelay: (Double) -> Unit,
  activeAudioDelaySupported: () -> Boolean,
  activeSetSpeed: (Double) -> Unit,
  activeAddSubtitle: (String, String?) -> Unit,
  onSubtitleSourceChange: (String) -> Unit,
  onSubtitleTextSizeChange: (Int) -> Unit,
  onSubtitleVerticalOffsetChange: (Int) -> Unit,
  onSelectStream: (AddonStream, Double) -> Unit,
  onReloadStreams: () -> Unit,
  onDownloadStream: (AddonStream) -> Unit,
  onToggleSourceFavourite: (String) -> Unit,
  episodeBrowser: PlayerEpisodeBrowser? = null,
  onBeforeEpisodeSwitch: () -> Unit = {},
) {
  // Re-bound so the panel bodies below read and write exactly as they did in the screen.
  var activePanel by activePanelState
  var playbackSpeed by playbackSpeedState
  var subtitleDelay by subtitleDelayState
  var audioDelay by audioDelayState
  var subtitleSize by subtitleSizeState
  var subtitlePosition by subtitlePositionState
  var subtitleTab by subtitleTabState
  var subtitleDisabledByUser by subtitleDisabledByUserState
  var subtitleErrorMessage by subtitleErrorMessageState
  var selectedAudioTrackId by selectedAudioTrackIdState
  var selectedSubtitleTrackId by selectedSubtitleTrackIdState
  var selectedExternalSubtitleId by selectedExternalSubtitleIdState
  var preferredAudioTrackKey by preferredAudioTrackKeyState
  var preferredSubtitleTrackKey by preferredSubtitleTrackKeyState
  var userPickedAudio by userPickedAudioState
  var userPickedSubtitle by userPickedSubtitleState
  var subtitleSelectionGeneration by remember(session.url) { mutableIntStateOf(0) }
  LaunchedEffect(subtitleErrorMessage) {
    if (subtitleErrorMessage == null) return@LaunchedEffect
    delay(3_000)
    subtitleErrorMessage = null
  }
  when (activePanel) {
    PlayerPanel.Audio -> PlayerModalPanel(title = stringResource(R.string.player_audio), onClose = { activePanel = PlayerPanel.None }) {
      if (audioTracks.isEmpty()) {
        PlayerOptionRow(stringResource(R.string.player_default_audio), selected = true, onClick = {})
      } else {
        audioTracks.forEach { track ->
          PlayerOptionRow(trackLabel(track), selected = selectedAudioTrackId == track.id) {
            userPickedAudio = true
            selectedAudioTrackId = track.id
            preferredAudioTrackKey = trackPreferenceKey(track)
            activeSetAudioTrack(track.id)
          }
        }
      }
      Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
      AudioDelayControl(
        valueSeconds = audioDelay.toDouble(),
        supported = remember(activeEngine, audioTracks) { activeAudioDelaySupported() },
        onChange = { seconds ->
          audioDelay = seconds.toFloat()
          activeSetAudioDelay(seconds)
        },
      )
    }
    PlayerPanel.Subtitles -> PlayerModalPanel(title = stringResource(R.string.player_subtitles), onClose = { activePanel = PlayerPanel.None }) {
      Row(
        modifier = Modifier.fillMaxWidth().clip(StreamDekRadius.panelShape).background(Color.White.copy(alpha = 0.06f)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        availableSubtitleTabs.forEach { tab ->
          val selected = subtitleTab == tab
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(StreamDekRadius.cardShape)
              .background(if (selected) Color.White else Color.Transparent)
              .clickable {
                subtitleTab = tab
                // Style is a set of controls, not a place subtitles come from, so it is not
                // remembered as the picker's landing tab.
                if (tab.isSource) onSubtitleSourceChange(tab.name)
              }
              .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              stringResource(tab.labelRes),
              color = if (selected) Color.Black else Color.White.copy(alpha = 0.72f),
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      when (subtitleTab) {
        SubtitlePanelTab.All, SubtitlePanelTab.BuiltIn, SubtitlePanelTab.Addons -> {
          PlayerOptionRow(stringResource(R.string.subtitle_none), selected = selectedSubtitleTrackId == null && selectedExternalSubtitleId == null) {
            subtitleSelectionGeneration += 1
            subtitleDisabledByUser = true
            userPickedSubtitle = true
            selectedSubtitleTrackId = null
            selectedExternalSubtitleId = null
            preferredSubtitleTrackKey = null
            activeDisableSubtitleTrack()
          }
          val visibleEmbeddedTracks = if (subtitleSourceIncludesBuiltIn(subtitleTab.name)) subtitleTracks.filter { track ->
            preferredSubtitleLanguageAllowed(
              track.language ?: track.title,
              session.subtitleLanguage,
              session.secondarySubtitleLanguage,
              session.showOnlyPreferredSubtitleLanguages,
            )
          } else emptyList()
          val visibleExternalSubtitles = externalSubtitles.filter { subtitle ->
            subtitleOriginVisible(subtitleTab.name, subtitle.origin) && preferredSubtitleLanguageAllowed(
              subtitle.language,
              session.subtitleLanguage,
              session.secondarySubtitleLanguage,
              session.showOnlyPreferredSubtitleLanguages,
            )
          }
          if (visibleEmbeddedTracks.isNotEmpty()) {
            Text(stringResource(R.string.player_subtitles_embedded), color = Color.White.copy(alpha = 0.64f), fontWeight = FontWeight.Bold)
          }
          visibleEmbeddedTracks.forEach { track ->
            PlayerOptionRow(
              label = trackLanguageName(track.language) ?: track.title ?: "Subtitle ${track.id}",
              supportingText = listOfNotNull(stringResource(R.string.subtitle_embedded), track.title, track.codec).distinct().joinToString(" • "),
              selected = selectedSubtitleTrackId == track.id,
            ) {
              subtitleSelectionGeneration += 1
              subtitleDisabledByUser = false
              userPickedSubtitle = true
              selectedExternalSubtitleId = null
              selectedSubtitleTrackId = track.id
              preferredSubtitleTrackKey = trackPreferenceKey(track)
              activeSetSubtitleTrack(track.id)
            }
          }
          if (subtitlesLoading) Text(stringResource(R.string.player_searching_subtitle_sources), color = Color.White.copy(alpha = 0.72f))
          if (visibleExternalSubtitles.isNotEmpty()) {
            Text(
              when (subtitleTab) {
                SubtitlePanelTab.BuiltIn -> stringResource(R.string.subtitle_streamdek_sources)
                SubtitlePanelTab.Addons -> stringResource(R.string.subtitle_addon_sources)
                else -> stringResource(R.string.subtitle_online)
              },
              color = Color.White.copy(alpha = 0.64f),
              fontWeight = FontWeight.Bold,
            )
          }
          visibleExternalSubtitles.forEachIndexed { index, subtitle ->
            val duplicateNumber = visibleExternalSubtitles.take(index + 1).count {
              it.language == subtitle.language && it.sourceName == subtitle.sourceName
            }
            val duplicateCount = visibleExternalSubtitles.count {
              it.language == subtitle.language && it.sourceName == subtitle.sourceName
            }
            PlayerOptionRow(
              label = trackLanguageName(subtitle.language) ?: stringResource(R.string.track_unknown_language),
              supportingText = listOfNotNull(
                subtitle.sourceName,
                if (subtitle.origin == ExternalSubtitleOrigin.BuiltIn) stringResource(R.string.subtitle_built_in_source) else stringResource(R.string.stream_origin_addon),
                subtitle.release?.takeIf { it.isNotBlank() },
                if (duplicateCount > 1) stringResource(R.string.subtitle_option_number, duplicateNumber) else null,
              ).joinToString(" • "),
              selected = selectedExternalSubtitleId == subtitle.id,
            ) {
              subtitleDisabledByUser = false
              userPickedSubtitle = true
              selectedSubtitleTrackId = null
              selectedExternalSubtitleId = subtitle.id
              subtitleErrorMessage = null
              val requestGeneration = ++subtitleSelectionGeneration
              // Fetched in the background so the video keeps playing while it loads, and moved
              // on from when it will not load. These files sit on hosts that expire links and
              // refuse requests -- one of PenguPlay's answers 403 outright -- and a chosen
              // subtitle that silently fails is the single worst outcome here: the row looks
              // selected, nothing appears, and there is no way to tell a broken link from a
              // player that cannot render it. The next copy in the same language is tried
              // instead, exactly as a failed stream falls through to the next source, and only
              // once nothing in that language works is the viewer told.
              playerScope.launch {
                val alternates = externalSubtitles.filter {
                  it.id != subtitle.id && Languages.matches(it.language, subtitle.language)
                }
                var applied = false
                for (candidate in (listOf(subtitle) + alternates).take(SUBTITLE_ATTEMPT_LIMIT)) {
                  if (selectedExternalSubtitleId != subtitle.id || subtitleSelectionGeneration != requestGeneration) return@launch
                  val localPath = downloadSubtitleToCache(playerContext, candidate.url) ?: continue
                  if (selectedExternalSubtitleId != subtitle.id || subtitleSelectionGeneration != requestGeneration) return@launch
                  activeAddSubtitle(localPath, candidate.language)
                  applied = true
                  Log.i("StreamDekSubtitles", "[Subtitle] source=${candidate.id.substringBefore(':')} language=${candidate.language} format=${localPath.substringAfterLast('.')} load=success trackAttached=true")
                  if (candidate.id != subtitle.id) {
                    Log.i("StreamDekSubtitles", "fell through to " + candidate.label)
                  }
                  break
                }
                if (!applied && selectedExternalSubtitleId == subtitle.id && subtitleSelectionGeneration == requestGeneration) {
                  selectedExternalSubtitleId = null
                  subtitleErrorMessage =
                    "That subtitle could not be downloaded, and neither could the others in " +
                      (trackLanguageName(subtitle.language) ?: "that language") + ". Try another source."
                }
              }
            }
          }
          if (!subtitlesLoading && visibleEmbeddedTracks.isEmpty() && visibleExternalSubtitles.isEmpty()) {
            val requestedLanguages = preferredSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
              .joinToString(", ") { Languages.label(it) }
            Text(
              if (session.showOnlyPreferredSubtitleLanguages && requestedLanguages.isNotBlank()) {
                stringResource(R.string.player_no_subtitles_for_languages, requestedLanguages)
              } else when (subtitleTab) {
                SubtitlePanelTab.BuiltIn -> stringResource(R.string.player_no_builtin_subtitles)
                SubtitlePanelTab.Addons -> stringResource(R.string.player_no_addon_subtitles)
                else -> stringResource(R.string.player_no_matching_subtitles)
              },
              color = Color.White.copy(alpha = 0.64f),
            )
          }
          subtitleErrorMessage?.let {
            Text(it, color = Color(0xFFF08A8A), style = MaterialTheme.typography.bodyMedium)
          }
        }
        SubtitlePanelTab.Style -> {
          // Applied live as the slider moves, saved when it is let go: writing to preferences on
          // every frame of a drag would be dozens of commits for one adjustment.
          Text(stringResource(R.string.player_subtitle_size_value, subtitleSize), color = Color.White.copy(alpha = 0.72f))
          Slider(
            value = subtitleSize.toFloat(),
            valueRange = 28f..84f,
            onValueChange = {
              subtitleSize = it.toInt()
              activeSetSubtitleFontSize(subtitleSize)
            },
            onValueChangeFinished = { onSubtitleTextSizeChange(subtitleSize) },
          )
          Text(stringResource(R.string.player_subtitle_position_value, subtitlePosition), color = Color.White.copy(alpha = 0.72f))
          Slider(
            value = subtitlePosition.toFloat(),
            valueRange = 50f..110f,
            onValueChange = {
              subtitlePosition = it.toInt()
              activeSetSubtitlePosition(subtitlePosition)
            },
            onValueChangeFinished = { onSubtitleVerticalOffsetChange(subtitlePosition) },
          )
          Text(stringResource(R.string.player_subtitle_styling_note), color = Color.White.copy(alpha = 0.52f), fontSize = 11.5.sp)
        }
        SubtitlePanelTab.Timing -> SubtitleDelayControl(subtitleDelay.toDouble()) { seconds ->
          subtitleDelay = seconds.toFloat()
          activeSetSubtitleDelay(seconds)
        }
      }
    }
    PlayerPanel.Sources -> {
      // The source being played leads the list and scrolls with it; everything else keeps its order.
      // When the playing stream is not among the loaded results at all, it leads as it is, so the
      // top of the list always says what is playing.
      val currentStream = session.currentStream
      val distinctStreams = remember(availableStreams) { availableStreams.distinctBy(::addonStreamPlaybackIdentity) }
      val playingStream = remember(distinctStreams, currentStream) {
        currentStream?.let { current -> distinctStreams.firstOrNull { streamsRepresentSameSource(it, current) } ?: current }
      }
      val otherStreams = remember(distinctStreams, playingStream) {
        distinctStreams
          .filterNot { playingStream != null && streamsRepresentSameSource(it, playingStream) }
          .take(MAX_PLAYER_SOURCE_ROWS - if (playingStream != null) 1 else 0)
      }
      val sourceRow: @Composable (AddonStream, Boolean) -> Unit = { stream, active ->
        PlayerSourceCard(
          stream = stream,
          active = active,
          onClick = {
            activePanel = PlayerPanel.None
            onSelectStream(stream, currentProgressPercent)
          },
          showDownload = downloadsEnabled && !session.isLive && stream.infoHash.isNullOrBlank() && !stream.url.isNullOrBlank(),
          onDownload = { onDownloadStream(stream) },
        )
      }
      PlayerModalPanel(
        title = stringResource(R.string.player_sources),
        onClose = { activePanel = PlayerPanel.None },
        trailing = {
          TextButton(onClick = onReloadStreams, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text(stringResource(R.string.player_reload)) }
        },
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
          playingStream?.let { stream -> sourceRow(stream, true) }
          otherStreams.forEach { stream -> sourceRow(stream, false) }
          if (availableStreams.isEmpty() && playingStream == null) {
            Text(stringResource(R.string.player_no_loaded_sources), color = Color.White.copy(alpha = 0.66f))
          }
        }
      }
    }
    PlayerPanel.Engine -> PlayerModalPanel(title = stringResource(R.string.player_engine_panel_title), onClose = { activePanel = PlayerPanel.None }, compact = true) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PlayerOptionRow("ExoPlayer", selected = activeEngine == ActivePlaybackEngine.Media3) {
          switchEngine(ActivePlaybackEngine.Media3, "manual switch")
          activePanel = PlayerPanel.None
        }
        PlayerOptionRow("mpv", selected = activeEngine == ActivePlaybackEngine.MPV) {
          switchEngine(ActivePlaybackEngine.MPV, "manual switch")
          activePanel = PlayerPanel.None
        }
        PlayerOptionRow(ActivePlaybackEngine.VLC.displayName, selected = activeEngine == ActivePlaybackEngine.VLC) {
          switchEngine(ActivePlaybackEngine.VLC, "manual switch")
          activePanel = PlayerPanel.None
        }
        Text(
          stringResource(R.string.player_engine_switch_note),
          color = Color.White.copy(alpha = 0.58f),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
    PlayerPanel.Speed -> PlayerModalPanel(title = stringResource(R.string.player_playback_speed), onClose = { activePanel = PlayerPanel.None }, compact = true) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
          FilterChip(
            selected = playbackSpeed == speed,
            onClick = {
              playbackSpeed = speed
              activeSetSpeed(speed.toDouble())
            },
            label = { Text(if (speed == 1f) "1x" else "${speed}x") },
          )
        }
      }
    }
    PlayerPanel.Captions -> PlayerModalPanel(title = stringResource(R.string.player_captions), onClose = { activePanel = PlayerPanel.None }, compact = true) {
      // Only what the stream has been seen to carry: a placeholder track would be a row that does
      // nothing when chosen.
      val captionTracks = subtitleTracks.filterNot { it.speculative }
      val captionsOff = selectedSubtitleTrackId == null && selectedExternalSubtitleId == null
      Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PlayerOptionRow(stringResource(R.string.player_captions_off_option), selected = captionsOff) {
          subtitleDisabledByUser = true
          userPickedSubtitle = false
          selectedSubtitleTrackId = null
          selectedExternalSubtitleId = null
          activeDisableSubtitleTrack()
          activePanel = PlayerPanel.None
        }
        captionTracks.forEachIndexed { index, track ->
          val format = captionFormatLabel(track.codec)
          PlayerOptionRow(
            trackLanguageName(track.language) ?: track.title ?: stringResource(R.string.player_caption_track_fallback, index + 1),
            selected = !captionsOff && selectedSubtitleTrackId == track.id,
            supportingText = listOfNotNull(format, track.title.takeIf { trackLanguageName(track.language) != null }).joinToString(" • ").ifBlank { null },
          ) {
            subtitleDisabledByUser = false
            userPickedSubtitle = true
            selectedExternalSubtitleId = null
            selectedSubtitleTrackId = track.id
            preferredSubtitleTrackKey = trackPreferenceKey(track)
            activeSetSubtitleTrack(track.id)
            activePanel = PlayerPanel.None
          }
        }
        if (captionTracks.isEmpty()) {
          Text(stringResource(R.string.player_captions_none_now), color = Color.White.copy(alpha = 0.66f), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.player_captions_detected_note), color = Color.White.copy(alpha = 0.52f), style = MaterialTheme.typography.bodySmall)
      }
    }
    PlayerPanel.Info -> PlayerModalPanel(title = stringResource(R.string.player_stream_info), onClose = { activePanel = PlayerPanel.None }) {
      PlayerStreamInfo(
        session = session,
        stats = playbackStats,
        engine = activeEngine,
        duration = duration,
      )
    }
    PlayerPanel.Episodes -> if (episodeBrowser != null) {
      PlayerEpisodePanel(
        browser = episodeBrowser,
        currentFraction = (currentProgressPercent / 100.0).toFloat().takeIf { duration > 0.0 },
        onClose = { activePanel = PlayerPanel.None },
        onSelectEpisode = { episode ->
          // Closed first, so the player's own loading state is what the viewer sees next.
          activePanel = PlayerPanel.None
          onBeforeEpisodeSwitch()
          episodeBrowser.onSelectEpisode(episode)
        },
      )
    } else {
      LaunchedEffect(Unit) { activePanel = PlayerPanel.None }
    }
    PlayerPanel.None -> Unit
  }
}


/**
 * The overlays that sit on top of the video: the hold-to-speed badge, the seek and adjustment
 * readouts, the paused card, the handoff sheet and the control chrome itself.
 *
 * Extracted from [NativePlayerScreen] for the same reason as [PlayerPanels] and the state holders:
 * each `AnimatedVisibility` here carries its own visibility expression and enter/exit transition
 * specs inline, and the lambdas passed to them are memoised in whichever composable declares them.
 * Eighteen of those in one function was a large share of why the player screen exceeded ART's
 * huge-method threshold and ran interpreted.
 */
@Composable
private fun BoxScope.PlayerOverlays(
  session: PlayerSession,
  source: PlayerSourceState,
  playback: PlayerPlaybackState,
  isFavourite: Boolean,
  isLoading: Boolean,
  currentProgressPercent: Double,
  activePanelState: MutableState<PlayerPanel>,
  playbackSpeedState: MutableFloatState,
  controlsLockedState: MutableState<Boolean>,
  resizeModeState: MutableState<String>,
  customZoomState: MutableFloatState,
  showLiveProgressState: MutableState<Boolean>,
  adjustmentKindState: MutableState<PlayerAdjustmentKind?>,
  adjustmentLevelState: MutableFloatState,
  activeSeekTo: (Double) -> Unit,
  requestPlayerAudioFocus: () -> Boolean,
  abandonPlayerAudioFocus: () -> Unit,
  keepControlsVisible: () -> Unit,
  closePlayer: () -> Unit,
  onBack: (Double) -> Unit,
  onScrobble: (String, Double) -> Unit,
  onToggleFavourite: () -> Unit,
  onHandoff: suspend (LinkedTvDevice, Double) -> Result<PlaybackHandoffReceipt>,
  onNextEpisode: () -> Unit,
  onPreviousEpisode: () -> Unit,
  controlLayoutState: MutableState<String>,
  onControlLayoutChange: (String) -> Unit,
  showEpisodes: Boolean = false,
) {
  // Re-bound so the overlay bodies below read and write exactly as they did in the screen.
  var controlLayout by controlLayoutState
  var activePanel by activePanelState
  var playbackSpeed by playbackSpeedState
  var controlsLocked by controlsLockedState
  var resizeMode by resizeModeState
  var customZoom by customZoomState
  var showLiveProgress by showLiveProgressState
  var isPaused by playback.isPaused
  var pausedForAudioFocus by playback.pausedForAudioFocus
  var currentTime by playback.currentTime
  var duration by playback.duration
  var error by source.error
  var showControls by source.showControls
  var showUnlockControl by source.showUnlockControl
  var unlockActivityVersion by source.unlockActivityVersion
  var showPausedInfo by source.showPausedInfo
  var speedBoostActive by source.speedBoostActive
  var scrubTargetSeconds by source.scrubTargetSeconds
  var handoffPickerVisible by source.handoffPickerVisible
  var handoffError by source.handoffError
  var adjustmentKind by adjustmentKindState
  var adjustmentLevel by adjustmentLevelState
  // Hold-to-speed indicator. Sits above the video so the viewer can see why it sped up, and
  // disappears the instant the finger lifts.
  AnimatedVisibility(
    visible = speedBoostActive && !controlsLocked,
    modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp).zIndex(19f),
    enter = fadeIn(animationSpec = tween(120)),
    exit = fadeOut(animationSpec = tween(160)),
  ) {
    Surface(
      color = Color(0xD9161A23),
      shape = StreamDekRadius.pill,
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(StreamDekPlayerIcons.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(
          stringResource(R.string.player_speed_multiplier, formatPlaybackSpeed(playbackSpeed * session.holdToSpeedMultiplier)),
          color = Color.White,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }

  // Scrub preview. Shows where release will land and how far that is from here.
  AnimatedVisibility(
    visible = scrubTargetSeconds != null && !controlsLocked,
    modifier = Modifier.align(Alignment.Center).zIndex(19f),
    enter = fadeIn(animationSpec = tween(90)),
    exit = fadeOut(animationSpec = tween(140)),
  ) {
    val target = scrubTargetSeconds ?: currentTime
    val delta = (target - currentTime).roundToInt()
    Surface(
      color = Color(0xD9161A23),
      shape = StreamDekRadius.panelShape,
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Icon(
          if (delta < 0) StreamDekPlayerIcons.FastRewind else StreamDekPlayerIcons.FastForward,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(28.dp),
        )
        Text(formatClock(target), color = Color.White, fontWeight = FontWeight.Bold)
        Text(
          (if (delta < 0) "−" else "+") + formatClock(kotlin.math.abs(delta).toDouble()),
          color = Color.White.copy(alpha = 0.7f),
        )
      }
    }
  }

  AnimatedVisibility(
    visible = adjustmentKind != null && !controlsLocked,
    modifier = Modifier.align(Alignment.Center).zIndex(19f),
    enter = fadeIn(animationSpec = tween(120)),
    exit = fadeOut(animationSpec = tween(180)),
  ) {
    Surface(
      color = Color.Black.copy(alpha = 0.46f),
      shape = StreamDekRadius.pill,
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Icon(
          when (adjustmentKind) {
            PlayerAdjustmentKind.Brightness -> StreamDekPlayerIcons.Brightness
            PlayerAdjustmentKind.Volume -> if (adjustmentLevel <= 0f) StreamDekPlayerIcons.AudioOff else StreamDekPlayerIcons.Audio
            null -> StreamDekPlayerIcons.Audio
          },
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(19.dp),
        )
        Text(
          // Whole sentences per kind, and the percentage through the locale's own formatter: a
          // trailing "%" is not universal punctuation and the two labels do not inflect alike.
          stringResource(
            if (adjustmentKind == PlayerAdjustmentKind.Brightness) R.string.player_brightness_level else R.string.player_volume_level,
            AppFormats.percent(LocalAppLanguage.current, adjustmentLevel.toDouble()),
          ),
          color = Color.White,
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
        )
      }
    }
  }

  AnimatedVisibility(
    visible = !isLoading && controlsLocked && showUnlockControl,
    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp).zIndex(20f),
    enter = fadeIn(animationSpec = tween(180)),
    exit = fadeOut(animationSpec = tween(140)),
  ) {
    Surface(
      color = Color(0xD9161A23),
      shape = StreamDekRadius.panelShape,
      modifier = Modifier
        .border(1.dp, Color.White.copy(alpha = 0.12f), StreamDekRadius.panelShape)
        .pointerInput(session.url) {
          detectTapGestures(onLongPress = {
            controlsLocked = false
            showUnlockControl = false
            keepControlsVisible()
          })
        },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(StreamDekPlayerIcons.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.player_hold_to_unlock), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      }
    }
  }

  AnimatedVisibility(
    visible = !isLoading && !controlsLocked && (showControls || activePanel != PlayerPanel.None || !error.isNullOrBlank()),
    enter = fadeIn(animationSpec = tween(220)),
    exit = fadeOut(animationSpec = tween(220)),
  ) {
    PlayerTopHeader(
      session = session,
      layout = controlLayout,
      onToggleLayout = {
        val next = if (controlLayout == "Minimal") "Normal" else "Minimal"
        controlLayout = next
        onControlLayoutChange(next)
        keepControlsVisible()
      },
      onBack = { closePlayer() },
      isFavourite = isFavourite,
      onToggleFavourite = onToggleFavourite,
      onHandoff = { handoffError = null; handoffPickerVisible = true },
      onLock = {
        controlsLocked = true
        showUnlockControl = true
        unlockActivityVersion += 1
        showControls = false
        showPausedInfo = false
        activePanel = PlayerPanel.None
      },
      modifier = Modifier.align(Alignment.TopStart),
    )
  }

  AnimatedVisibility(
    visible = !isLoading && !controlsLocked && activePanel == PlayerPanel.None && (showControls || !error.isNullOrBlank()),
    enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it / 12 }, animationSpec = tween(280)),
    exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(targetOffsetY = { it / 14 }, animationSpec = tween(220)),
  ) {
    PlayerCenterControls(
      isPaused = isPaused,
      onPauseToggle = {
        val nextPaused = !isPaused
        if (nextPaused) {
          pausedForAudioFocus = false
          abandonPlayerAudioFocus()
          onScrobble("pause", currentProgressPercent)
        } else {
          if (!requestPlayerAudioFocus()) return@PlayerCenterControls
          showPausedInfo = false
          onScrobble("start", currentProgressPercent)
        }
        isPaused = nextPaused
        keepControlsVisible()
      },
      onPreviousEpisode = onPreviousEpisode,
      onNextEpisode = onNextEpisode,
      showEpisodeNavigation = session.mediaType == "tv" && session.autoPlayNextEpisode,
      onSeek = { delta ->
        currentTime = (currentTime + delta).coerceIn(0.0, duration.takeIf { it > 0.0 } ?: Double.MAX_VALUE)
        activeSeekTo(currentTime)
        keepControlsVisible()
      },
      // A live source showing a seekable bar is one the double-tap gestures can move too;
      // without a bar (or without a seekable window) there is nothing for them to act on.
      showSeeking = !session.isLive || (showLiveProgress && duration > 0.0),
      layout = controlLayout,
    )
  }

  AnimatedVisibility(
    visible = !isLoading && !controlsLocked && activePanel == PlayerPanel.None && (showControls || !error.isNullOrBlank()),
    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it / 10 }, animationSpec = tween(280)),
    exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(targetOffsetY = { it / 12 }, animationSpec = tween(220)),
  ) {
    PlayerBottomControls(
      currentTime = currentTime,
      duration = duration,
      bufferedRanges = source.bufferedRanges.value,
      isLive = session.isLive,
      isVod = session.isVod,
      showLiveProgress = showLiveProgress,
      onToggleLiveProgress = { showLiveProgress = !showLiveProgress; keepControlsVisible() },
      progress = if (duration > 0.0) (currentTime / duration).toFloat().coerceIn(0f, 1f) else 0f,
      error = error,
      resizeMode = resizeMode,
      speed = playbackSpeed,
      onProgressChange = { fraction -> currentTime = duration * fraction; keepControlsVisible() },
      onProgressFinished = { activeSeekTo(currentTime); keepControlsVisible() },
      onZoom = {
        keepControlsVisible()
        resizeMode = when (resizeMode) {
          "contain" -> "cover"
          "cover" -> "stretch"
          else -> "contain"
        }
        customZoom = 1f
      },
      onSpeed = { keepControlsVisible(); activePanel = PlayerPanel.Speed },
      onSubtitles = { keepControlsVisible(); activePanel = PlayerPanel.Subtitles },
      onAudio = { keepControlsVisible(); activePanel = PlayerPanel.Audio },
      onSources = { keepControlsVisible(); activePanel = PlayerPanel.Sources },
      onEngine = { keepControlsVisible(); activePanel = PlayerPanel.Engine },
      onInfo = { keepControlsVisible(); activePanel = PlayerPanel.Info },
      showEpisodes = showEpisodes,
      onEpisodes = { keepControlsVisible(); activePanel = PlayerPanel.Episodes },
      showLabels = session.showPlayerControlLabels,
      layout = controlLayout,
      captionsAvailable = session.isLive && source.subtitleTracks.any { !it.speculative },
      captionsOn = source.selectedSubtitleTrackId.value != null || source.selectedExternalSubtitleId.value != null,
      onCaptions = { keepControlsVisible(); activePanel = PlayerPanel.Captions },
    )
  }
}


/**
 * The player's background behaviour: control auto-hide, the immersive-mode window flags, skip
 * segments and their notices, external subtitle loading and re-application, and the progress
 * checkpoints.
 *
 * Fifteen `LaunchedEffect`/`DisposableEffect` calls, each with its own key list. Every key compiles
 * to a `Composer.changed` comparison in whichever composable declares the effect, and those
 * comparisons were among the largest remaining contributors to [NativePlayerScreen] exceeding
 * ART's huge-method threshold. Moving them here does not change when any of them run: the keys and
 * their order are unchanged, and this composable is called unconditionally from the same position.
 */
@Composable
private fun PlayerBehaviourEffects(
  session: PlayerSession,
  source: PlayerSourceState,
  playback: PlayerPlaybackState,
  playbackIdentity: String,
  playerContext: android.content.Context,
  activity: android.app.Activity?,
  activeEngine: ActivePlaybackEngine,
  playerView: MPVView?,
  exoPlayerView: ExoPlaybackView?,
  vlcPlayerView: VlcPlaybackView?,
  isLoading: Boolean,
  activeSkipSegment: SkipSegment?,
  nextEpisodeActionAvailable: Boolean,
  userSubtitleSources: List<UserSubtitleSource>,
  adjustmentFeedbackVersion: Int,
  activePanelState: MutableState<PlayerPanel>,
  controlsLockedState: MutableState<Boolean>,
  subtitleDisabledByUserState: MutableState<Boolean>,
  selectedSubtitleTrackIdState: MutableState<Int?>,
  selectedExternalSubtitleIdState: MutableState<String?>,
  userPickedSubtitleState: MutableState<Boolean>,
  adjustmentKindState: MutableState<PlayerAdjustmentKind?>,
  activeSeekTo: (Double) -> Unit,
  activeAddSubtitle: (String, String?) -> Unit,
  progressPercent: () -> Double,
  onScrobble: (String, Double) -> Unit,
  onPlaybackEnded: () -> Unit,
) {
  // Re-bound so the effect bodies below read and write exactly as they did in the screen.
  var activePanel by activePanelState
  var controlsLocked by controlsLockedState
  var subtitleDisabledByUser by subtitleDisabledByUserState
  var selectedSubtitleTrackId by selectedSubtitleTrackIdState
  var selectedExternalSubtitleId by selectedExternalSubtitleIdState
  var userPickedSubtitle by userPickedSubtitleState
  var adjustmentKind by adjustmentKindState
  var isPaused by playback.isPaused
  var currentTime by playback.currentTime
  var duration by playback.duration
  var playbackEnded by playback.playbackEnded
  var autoSkipNotice by playback.autoSkipNotice
  var autoSkippedSegments by playback.autoSkippedSegments
  var error by source.error
  var showControls by source.showControls
  var showUnlockControl by source.showUnlockControl
  var unlockActivityVersion by source.unlockActivityVersion
  var controlActivityVersion by source.controlActivityVersion
  var showPausedInfo by source.showPausedInfo
  var showChannelSwipeCue by source.showChannelSwipeCue
  var scrobbleStarted by source.scrobbleStarted
  var skipSegments by source.skipSegments
  var externalSubtitles by source.externalSubtitles
  var externalSubtitleNeedsReapply by source.externalSubtitleNeedsReapply
  var subtitlesLoading by source.subtitlesLoading
  var playbackStats by source.playbackStats
LaunchedEffect(session.url, session.isLive) {
  if (!session.isLive) return@LaunchedEffect
  while (true) {
    showChannelSwipeCue = true
    delay(3_500)
    showChannelSwipeCue = false
    delay(11_500)
  }
}

LaunchedEffect(controlsLocked, showUnlockControl, unlockActivityVersion) {
  if (controlsLocked && showUnlockControl) {
    delay(2_600)
    showUnlockControl = false
  }
}

LaunchedEffect(adjustmentFeedbackVersion) {
  if (adjustmentFeedbackVersion > 0) {
    delay(900)
    adjustmentKind = null
  }
}

fun applyPlayerSystemUi() {
  val showStatus = session.fullscreenStatusBar == "Always show" ||
    (session.fullscreenStatusBar == "Automatic" && (showControls || activePanel != PlayerPanel.None))
  activity?.window?.decorView?.systemUiVisibility =
    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
      View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
      (if (showStatus) 0 else View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
}
LaunchedEffect(session.fullscreenStatusBar, showControls, activePanel) { applyPlayerSystemUi() }

DisposableEffect(activity) {
  val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
  val previousBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
  val decorView = activity?.window?.decorView
  val previousSystemUi = decorView?.systemUiVisibility ?: 0
  MainActivity.pipShouldEnter = session.pictureInPictureEnabled
  // Claimed before the rotation is requested: the activity re-applies its own orientation policy
  // on configuration changes, and the rotation asked for here arrives as one of those.
  MainActivity.playerOwnsOrientation = true
  (activity as? MainActivity)?.applyRefreshRatePolicy()
  activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
  applyPlayerSystemUi()
  onDispose {
    MainActivity.pipShouldEnter = false
    MainActivity.playerOwnsOrientation = false
    (activity as? MainActivity)?.applyRefreshRatePolicy()
    activity?.requestedOrientation = previous
    activity?.window?.let { window ->
      val attributes = window.attributes
      attributes.screenBrightness = previousBrightness
      window.attributes = attributes
    }
    decorView?.systemUiVisibility = previousSystemUi
    if (scrobbleStarted && !session.isLive) onScrobble("stop", progressPercent())
  }
}

LaunchedEffect(showControls, isPaused, activePanel, duration, error, controlActivityVersion) {
  if (showControls && !isPaused && activePanel == PlayerPanel.None && duration > 0.0 && error.isNullOrBlank()) {
    delay(3_500)
    showControls = false
  }
}

LaunchedEffect(isPaused, showControls, activePanel, isLoading, controlActivityVersion) {
  if (isLoading || !isPaused || activePanel != PlayerPanel.None) {
    showPausedInfo = false
    return@LaunchedEffect
  }
  if (showControls) {
    delay(2_400)
    if (isPaused && activePanel == PlayerPanel.None) showControls = false
  } else {
    delay(180)
    if (isPaused && activePanel == PlayerPanel.None) showPausedInfo = true
  }
}

LaunchedEffect(isLoading) {
  if (isLoading) {
    showControls = false
    activePanel = PlayerPanel.None
  }
}

// The engines are polled rather than made to push, and only while the panel that reads them is
// open — a transfer rate is a moving number that nothing else on screen depends on, so paying
// for it every second of a two-hour film to answer a question nobody asked is waste.
LaunchedEffect(activePanel, activeEngine, exoPlayerView, playerView, vlcPlayerView) {
  if (activePanel != PlayerPanel.Info) {
    playbackStats = null
    return@LaunchedEffect
  }
  while (true) {
    playbackStats = when (activeEngine) {
      ActivePlaybackEngine.Media3 -> exoPlayerView?.playbackStats()
      ActivePlaybackEngine.MPV -> playerView?.playbackStats()
      ActivePlaybackEngine.VLC -> vlcPlayerView?.playbackStats()
    }
    delay(1_000)
  }
}

// The seek bar's buffered state, sampled the same way and for the same reason: only while the bar
// that draws it is on screen. The holder it is written to belongs to one source, so a new stream
// starts from nothing; the engine is a key so a switch of engine does too. The state is only
// written when the answer changed, so a paused, fully buffered film costs no recomposition.
LaunchedEffect(showControls, isLoading, controlsLocked, activePanel, activeEngine, exoPlayerView, playerView, vlcPlayerView) {
  val barOnScreen = showControls && !isLoading && !controlsLocked && activePanel == PlayerPanel.None
  if (source.bufferedRanges.value.isNotEmpty()) source.bufferedRanges.value = emptyList()
  if (!barOnScreen) return@LaunchedEffect
  while (true) {
    val sampled = runCatching {
      when (activeEngine) {
        ActivePlaybackEngine.Media3 -> exoPlayerView?.bufferedRanges()
        ActivePlaybackEngine.MPV -> playerView?.bufferedRanges()
        ActivePlaybackEngine.VLC -> vlcPlayerView?.bufferedRanges()
      }
    }.getOrNull().orEmpty()
    if (sampled != source.bufferedRanges.value) source.bufferedRanges.value = sampled
    delay(500)
  }
}

LaunchedEffect(playbackEnded) {
  if (playbackEnded) {
    delay(450)
    onPlaybackEnded()
  }
}

LaunchedEffect(playbackIdentity, session.skipIntroEnabled, session.skipRecapEnabled, session.skipEndingEnabled, session.autoSkipIntroEnabled, session.autoSkipRecapEnabled, session.autoSkipEndingEnabled, session.introdbApiKey, session.theIntroDbApiKey, session.timingProvider, session.timingProviderFallbackEnabled, session.isLive) {
  if (session.isLive) {
    skipSegments = emptyList()
    return@LaunchedEffect
  }
  skipSegments = fetchSkipSegments(session).filter { segment ->
    when (segment.type) {
      "intro" -> session.skipIntroEnabled || session.autoSkipIntroEnabled
      "recap" -> session.skipRecapEnabled || session.autoSkipRecapEnabled
      "outro" -> session.skipEndingEnabled || session.autoSkipEndingEnabled || session.endOfPlaybackRecommendationsEnabled
      else -> false
    }
  }
}

LaunchedEffect(activeSkipSegment, isLoading, nextEpisodeActionAvailable) {
  val segment = activeSkipSegment ?: return@LaunchedEffect
  if (isLoading || (segment.type == "outro" && session.mediaType == "tv" && session.autoPlayNextEpisode)) return@LaunchedEffect
  val enabled = when (segment.type) {
    "intro" -> session.autoSkipIntroEnabled
    "recap" -> session.autoSkipRecapEnabled
    "outro" -> session.autoSkipEndingEnabled
    else -> false
  }
  val segmentKey = "${segment.type}:${segment.startSeconds}:${segment.endSeconds}"
  if (!enabled || segmentKey in autoSkippedSegments) return@LaunchedEffect
  autoSkippedSegments = autoSkippedSegments + segmentKey
  currentTime = segment.endSeconds
  activeSeekTo(segment.endSeconds)
  skipSegments = skipSegments - segment
  autoSkipNotice = when (segment.type) {
    "recap" -> R.string.player_recap_skipped
    "outro" -> R.string.player_ending_skipped
    else -> R.string.player_intro_skipped
  }
}

LaunchedEffect(autoSkipNotice) {
  val notice = autoSkipNotice ?: return@LaunchedEffect
  // A change of engine is a sentence to read, not a word to glance at.
  delay(if (notice in PLAYER_ENGINE_NOTICES) 5_000 else 2_200)
  autoSkipNotice = null
}

// Deliberately not keyed on duration or on subtitleDisabledByUser.
//
// Duration is revised as a stream loads -- a usenet assembly or a growing HLS window can report
// 0 and then several different figures -- and every revision cancelled this effect and restarted
// it, delay and all, so the lookup could be perpetually one revision away from running. Whether
// the viewer has switched subtitles off is not a reason to have no list either: the panel is
// where they go to switch them back on, and it has to have something in it when they get there.
// The list is fetched once per source, and what is done with it is decided below.
LaunchedEffect(playbackIdentity, session.autoLoadSubtitles, playerView, exoPlayerView, vlcPlayerView, session.isLive, userSubtitleSources) {
  if (session.isLive) return@LaunchedEffect
  if (playerView == null && exoPlayerView == null && vlcPlayerView == null) return@LaunchedEffect
  delay(1_200)
  subtitlesLoading = true
  val results = fetchExternalSubtitles(session, userSubtitleSources)
  externalSubtitles = results
  // Auto-selection is the part that has to respect those two, not the lookup above.
  if (session.autoLoadSubtitles && selectedSubtitleTrackId == null && selectedExternalSubtitleId == null &&
    !subtitleDisabledByUser && !userPickedSubtitle
  ) {
    val preferredLanguages = preferredSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
    results.firstOrNull { Languages.normalize(it.language) in preferredLanguages }?.let { subtitle ->
      selectedExternalSubtitleId = subtitle.id
      // Download off the player thread first — handing mpv a remote URL stalls
      // playback while it fetches the file.
      val localPath = downloadSubtitleToCache(playerContext, subtitle.url)
      if (localPath != null && selectedExternalSubtitleId == subtitle.id) activeAddSubtitle(localPath, subtitle.language)
    }
  }
  subtitlesLoading = false
}
LaunchedEffect(activeEngine, playerView, vlcPlayerView, externalSubtitleNeedsReapply, selectedExternalSubtitleId, externalSubtitles) {
  // mpv and libVLC both take an external subtitle as a file added beside the stream, so an
  // engine switch into either has to hand the chosen one over again.
  val reapplyEngine = activeEngine
  val engineReady = when (reapplyEngine) {
    ActivePlaybackEngine.MPV -> playerView != null
    ActivePlaybackEngine.VLC -> vlcPlayerView != null
    ActivePlaybackEngine.Media3 -> false
  }
  if (!engineReady || !externalSubtitleNeedsReapply) return@LaunchedEffect
  val subtitle = externalSubtitles.firstOrNull { it.id == selectedExternalSubtitleId } ?: return@LaunchedEffect
  delay(700)
  val localPath = downloadSubtitleToCache(playerContext, subtitle.url)
  if (localPath != null && activeEngine == reapplyEngine && selectedExternalSubtitleId == subtitle.id) {
    if (reapplyEngine == ActivePlaybackEngine.VLC) vlcPlayerView?.addSubtitleFile(localPath, subtitle.language)
    else playerView?.addSubtitleFile(localPath, subtitle.language)
    externalSubtitleNeedsReapply = false
  }
}
LaunchedEffect(session.url, isPaused, duration, session.isLive) {
  if (session.isLive) return@LaunchedEffect
  if (isPaused || duration <= 0.0) return@LaunchedEffect
  if (!scrobbleStarted) {
    onScrobble("start", 0.0)
    scrobbleStarted = true
  }
  while (true) {
    delay(60_000)
    if (!isPaused) onScrobble("pause", progressPercent())
  }
}
}


/**
 * What sits directly on the video surface: the loading and paused cards, the skip-segment prompt,
 * the slow-load hint, and the gesture layer that handles tap, scrub, brightness and volume drags.
 *
 * Extracted from [NativePlayerScreen] for the same reason as its siblings -- the gesture layer
 * alone declares a dozen lambdas, and each is memoised in whichever composable declares it.
 */
@Composable
private fun UpNextPanel(
  primaryRecommendation: MediaItem?,
  alternativeRecommendations: List<MediaItem>,
  showNextEpisode: Boolean,
  nextEpisodeAvailability: NextEpisodeAvailability,
  nextEpisodeLabel: String?,
  nextEpisodeArtwork: String?,
  countdown: Int?,
  currentTitle: String,
  watchlistIds: Set<String>,
  onPlayNextEpisode: () -> Unit,
  onPlayRecommendation: (MediaItem) -> Unit,
  onAddToWatchlist: (MediaItem) -> Unit,
  onDismiss: () -> Unit,
) {
  val primary = primaryRecommendation
  Surface(
    modifier = Modifier.widthIn(min = 320.dp, max = 400.dp),
    color = Color(0xF214171C),
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    tonalElevation = 10.dp,
  ) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(stringResource(R.string.player_up_next_heading), color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (showNextEpisode) {
          val description = when {
            nextEpisodeAvailability == NextEpisodeAvailability.Unaired -> stringResource(R.string.player_next_episode_unaired_description)
            nextEpisodeAvailability == NextEpisodeAvailability.Unknown -> stringResource(R.string.player_next_episode_unknown_release_description)
            countdown != null -> stringResource(R.string.player_next_episode_autoplay_description)
            else -> stringResource(R.string.player_next_episode_manual_description)
          }
          val actionLabel = if (nextEpisodeAvailability == NextEpisodeAvailability.Unaired) {
            stringResource(R.string.action_skip_ending)
          } else {
            stringResource(R.string.action_next_episode)
          }
          RecommendationChoice(null, nextEpisodeLabel ?: stringResource(R.string.action_next_episode), nextEpisodeArtwork, description, false, null, onPlayNextEpisode, actionLabel)
        } else if (primary != null) {
          RecommendationChoice(primary, primary.title, null, stringResource(R.string.player_because_you_watched, currentTitle), primary.id in watchlistIds, { onAddToWatchlist(primary) }, { onPlayRecommendation(primary) })
        }
        if (alternativeRecommendations.isNotEmpty()) {
          Text(stringResource(R.string.player_you_might_also_like), color = Color.White.copy(alpha = 0.58f), style = MaterialTheme.typography.labelSmall)
          Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            alternativeRecommendations.forEach { item ->
              RecommendationChoice(item, item.title, null, null, item.id in watchlistIds, { onAddToWatchlist(item) }, { onPlayRecommendation(item) })
            }
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
          countdown?.let { Text(stringResource(R.string.player_next_episode_starts_in, it), color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.labelMedium) }
          TextButton(onClick = onDismiss, modifier = Modifier.height(38.dp)) {
            Text(if (countdown != null) stringResource(R.string.action_stay) else stringResource(R.string.action_dismiss), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium)
          }
        }
    }
  }
}

@Composable
private fun RecommendationChoice(
  item: MediaItem?,
  title: String,
  artworkOverride: String?,
  reason: String?,
  saved: Boolean,
  onAddToWatchlist: (() -> Unit)?,
  onClick: () -> Unit,
  actionLabel: String = "Play Now",
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(12.dp)).padding(8.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val artwork = artworkOverride ?: item?.backdrop ?: item?.poster
    Box(Modifier.width(84.dp).height(52.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF272C35)), contentAlignment = Alignment.Center) {
      if (!artwork.isNullOrBlank()) {
        AsyncImage(model = artwork, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      } else {
        Icon(StreamDekPlayerIcons.HandOff, contentDescription = null, tint = Color.White.copy(alpha = 0.30f))
      }
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      reason?.takeIf { it.isNotBlank() }?.let {
        Text(
          text = it,
          modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
          color = Color.White.copy(alpha = 0.55f),
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Clip,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
          onClick = onClick,
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 11.dp, vertical = 4.dp),
          modifier = Modifier.height(34.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0BA66), contentColor = Color(0xFF171A20)),
        ) { Text(actionLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        onAddToWatchlist?.let { add ->
          TextButton(
            onClick = add,
            enabled = !saved,
            modifier = Modifier.height(34.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          ) {
            Text(if (saved) "In Watchlist" else "Add to Watchlist", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }
  }
}

@Composable
private fun BoxScope.PlayerSurfaceOverlays(
  session: PlayerSession,
  source: PlayerSourceState,
  playback: PlayerPlaybackState,
  isLoading: Boolean,
  /** Which of a live channel's sources is starting; null for a channel with one source. */
  liveSourceAttempt: LiveSourceAttempt?,
  peerSwarm: SwarmStats?,
  peerSourceLabel: String?,
  channelSwitchLoading: Boolean,
  channelSwitchLoadingLabel: String?,
  nextEpisodeLoadingLabel: String?,
  activeSkipSegment: SkipSegment?,
  nextEpisodeActionAvailable: Boolean,
  audioManager: android.media.AudioManager?,
  surfaceInteractionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
  smartSwitchCooldownUntilState: MutableState<Long>,
  nextEpisodeLoading: Boolean,
  activePanelState: MutableState<PlayerPanel>,
  controlsLockedState: MutableState<Boolean>,
  adjustmentKindState: MutableState<PlayerAdjustmentKind?>,
  adjustmentLevelState: MutableFloatState,
  resizeModeState: MutableState<String>,
  customZoomState: MutableFloatState,
  activeSeekTo: (Double) -> Unit,
  applyBrightness: (Float) -> Unit,
  applyMediaVolume: (Float) -> Unit,
  currentWindowBrightness: () -> Float,
  currentMediaVolume: () -> Float,
  keepControlsVisible: () -> Unit,
  progressPercent: () -> Double,
  onSelectStream: (AddonStream, Double) -> Unit,
  onNextEpisodeAtEnding: () -> Unit,
  upNextVisible: Boolean,
  primaryRecommendation: MediaItem?,
  alternativeRecommendations: List<MediaItem>,
  showNextEpisode: Boolean,
  nextEpisodeAvailability: NextEpisodeAvailability,
  nextEpisodeLabel: String?,
  nextEpisodeArtwork: String?,
  upNextCountdown: Int?,
  currentTitle: String,
  watchlistIds: Set<String>,
  onPlayNextEpisode: () -> Unit,
  onPlayRecommendation: (MediaItem) -> Unit,
  onAddToWatchlist: (MediaItem) -> Unit,
  onDismissUpNext: () -> Unit,
  onTogglePause: () -> Unit,
) {
  // Re-bound so the bodies below read and write exactly as they did in the screen.
  var activePanel by activePanelState
  var controlsLocked by controlsLockedState
  var adjustmentKind by adjustmentKindState
  var adjustmentLevel by adjustmentLevelState
  var customZoom by customZoomState
  var isPaused by playback.isPaused
  var currentTime by playback.currentTime
  var duration by playback.duration
  var autoSkipNotice by playback.autoSkipNotice
  var recentPlaybackStalls by playback.recentPlaybackStalls
  var smartSwitchCandidate by playback.smartSwitchCandidate
  var showControls by source.showControls
  var showUnlockControl by source.showUnlockControl
  var unlockActivityVersion by source.unlockActivityVersion
  var showPausedInfo by source.showPausedInfo
  var showChannelSwipeCue by source.showChannelSwipeCue
  var showFavouriteDrawer by source.showFavouriteDrawer
  var showLiveChannels by source.showLiveChannels
  var scrubTargetSeconds by source.scrubTargetSeconds
  var speedBoostActive by source.speedBoostActive
  var suppressNextTap by source.suppressNextTap
  var slowLoadHintVisible by source.slowLoadHintVisible
  var skipSegments by source.skipSegments
  var smartSwitchCooldownUntil by smartSwitchCooldownUntilState
  var seekFeedbackAmount by remember(session.url) { mutableIntStateOf(0) }
  // AnimatedVisibility keeps composing during its exit. Retain the last non-zero amount so
  // clearing visibility cannot turn a fading rewind indicator into a one-frame forward icon.
  var displayedSeekFeedbackAmount by remember(session.url) { mutableIntStateOf(0) }
  var seekFeedbackVersion by remember(session.url) { mutableIntStateOf(0) }
  var playPauseFeedback by remember(session.url) { mutableStateOf<Boolean?>(null) }
  LaunchedEffect(seekFeedbackVersion) {
    if (seekFeedbackVersion > 0) {
      delay(850)
      seekFeedbackAmount = 0
    }
  }
  LaunchedEffect(playPauseFeedback) {
    if (playPauseFeedback != null) {
      delay(650)
      playPauseFeedback = null
    }
  }
  AnimatedVisibility(
    visible = seekFeedbackAmount != 0 && !controlsLocked,
    modifier = Modifier.align(if (displayedSeekFeedbackAmount < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 48.dp).zIndex(21f),
    enter = fadeIn(tween(100)),
    exit = fadeOut(tween(180)),
  ) {
    Surface(color = Color.Black.copy(alpha = 0.48f), shape = CircleShape) {
      Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (displayedSeekFeedbackAmount < 0) StreamDekPlayerIcons.FastRewind else StreamDekPlayerIcons.FastForward, contentDescription = null, tint = Color.White)
        Text(
          stringResource(
            if (displayedSeekFeedbackAmount > 0) R.string.player_seek_feedback_forward else R.string.player_seek_feedback_back,
            kotlin.math.abs(displayedSeekFeedbackAmount),
          ),
          color = Color.White,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
  AnimatedVisibility(
    visible = playPauseFeedback != null && !controlsLocked,
    modifier = Modifier.align(Alignment.Center).zIndex(21f),
    enter = fadeIn(tween(100)),
    exit = fadeOut(tween(180)),
  ) {
    Surface(color = Color.Black.copy(alpha = 0.42f), shape = CircleShape) {
      Icon(if (playPauseFeedback == true) StreamDekPlayerIcons.Pause else StreamDekPlayerIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.padding(16.dp).size(30.dp))
    }
  }
  AnimatedVisibility(
    visible = smartSwitchCandidate != null && !controlsLocked,
    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).padding(bottom = 30.dp).zIndex(20f),
    enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { it / 2 }),
    exit = fadeOut(animationSpec = tween(140)) + slideOutVertically(targetOffsetY = { it / 2 }),
  ) {
    Surface(
      color = Color(0xEA161A23),
      shape = StreamDekRadius.panelShape,
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(stringResource(R.string.player_source_keeps_buffering), color = Color.White, fontWeight = FontWeight.Black)
          Text(stringResource(R.string.player_switch_next_ranked_prompt), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = {
          smartSwitchCandidate = null
          smartSwitchCooldownUntil = android.os.SystemClock.elapsedRealtime() + 180_000L
          recentPlaybackStalls = emptyList()
        }) { Text(stringResource(R.string.action_keep)) }
        Button(onClick = {
          val next = smartSwitchCandidate ?: return@Button
          smartSwitchCandidate = null
          recentPlaybackStalls = emptyList()
          smartSwitchCooldownUntil = android.os.SystemClock.elapsedRealtime() + 60_000L
          onSelectStream(next, progressPercent())
        }) { Text(stringResource(R.string.action_switch)) }
      }
    }
  }

  if (upNextVisible && !isLoading && activePanel == PlayerPanel.None) {
    // The recommendation surface is modal to touch. A transparent hit target above the video
    // consumes taps and drags, while the panel itself remains above it and fully interactive.
    Box(
      Modifier.fillMaxSize()
        .zIndex(5.5f)
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              event.changes.forEach { it.consume() }
            }
          }
        },
    )
  }

  AnimatedVisibility(
    visible = upNextVisible && !isLoading && activePanel == PlayerPanel.None,
    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp).zIndex(6f),
    enter = fadeIn(animationSpec = StreamDekMotion.enterSpec()) + slideInVertically(initialOffsetY = { it / 3 }, animationSpec = StreamDekMotion.enterSpec()),
    exit = fadeOut(animationSpec = StreamDekMotion.exitSpec()) + slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = StreamDekMotion.exitSpec()),
  ) {
    UpNextPanel(
      primaryRecommendation = primaryRecommendation,
      alternativeRecommendations = alternativeRecommendations,
      showNextEpisode = showNextEpisode,
      nextEpisodeAvailability = nextEpisodeAvailability,
      nextEpisodeLabel = nextEpisodeLabel,
      nextEpisodeArtwork = nextEpisodeArtwork,
      countdown = upNextCountdown,
      currentTitle = currentTitle,
      watchlistIds = watchlistIds,
      onPlayNextEpisode = onPlayNextEpisode,
      onPlayRecommendation = onPlayRecommendation,
      onAddToWatchlist = onAddToWatchlist,
      onDismiss = onDismissUpNext,
    )
  }

  AnimatedVisibility(
    visible = !upNextVisible && !isLoading && activePanel == PlayerPanel.None && activeSkipSegment != null,
    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 118.dp).zIndex(4f),
    enter = fadeIn(animationSpec = tween(180)),
    exit = fadeOut(animationSpec = tween(140)),
  ) {
    Button(
      onClick = {
        if (nextEpisodeActionAvailable) {
          onNextEpisodeAtEnding()
        } else {
          activeSkipSegment?.let { segment ->
            currentTime = segment.endSeconds
            activeSeekTo(segment.endSeconds)
            skipSegments = skipSegments - segment
          }
        }
      },
      colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
      shape = StreamDekRadius.panelShape,
    ) { Text(stringResource(if (nextEpisodeActionAvailable) R.string.action_next_episode else when (activeSkipSegment?.type) { "recap" -> R.string.settings_m_skip_recap; "outro" -> R.string.action_skip_ending; else -> R.string.settings_m_skip_intro }), fontWeight = FontWeight.Bold) }
  }
  AnimatedVisibility(
    visible = autoSkipNotice != null,
    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 118.dp).zIndex(5f),
    enter = fadeIn(animationSpec = tween(160)),
    exit = fadeOut(animationSpec = tween(180)),
  ) {
    Surface(color = Color(0xD91A1D24), shape = StreamDekRadius.pill) {
      Text(autoSkipNotice?.let { stringResource(it) }.orEmpty(), modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
    }
  }
  // A live-channel switch has its own compact status indicator below. Keeping the
  // general loading backdrop here as well produced two competing loading messages.
  if (isLoading && !(session.isLive && channelSwitchLoading)) {
    PlayerLoadingBackdrop(
      session = session,
      message = when {
        // The label beside it is the episode's own code, so the two are joined rather than written
        // as one sentence - only the first half is StreamDek's words.
        nextEpisodeLoading -> listOfNotNull(stringResource(R.string.player_loading_next_episode), nextEpisodeLoadingLabel).joinToString(" · ")
        // A channel with several sources says which one it is on, so a switch reads as the player
        // working through them rather than as a stall that happens to recover.
        session.isLive && liveSourceAttempt != null && slowLoadHintVisible ->
          stringResource(R.string.player_live_source_slow, liveSourceAttempt.position, liveSourceAttempt.total)
        session.isLive && liveSourceAttempt != null && liveSourceAttempt.failingOver ->
          stringResource(R.string.player_live_trying_source, liveSourceAttempt.position, liveSourceAttempt.total)
        session.isLive && liveSourceAttempt != null ->
          stringResource(R.string.player_live_connecting_source, liveSourceAttempt.position, liveSourceAttempt.total)
        session.isLive && slowLoadHintVisible -> stringResource(R.string.player_channel_slow_to_load)
        peerSwarm != null -> stringResource(R.string.player_buffering_peers)
        else -> stringResource(R.string.player_preparing_stream)
      },
      swarm = peerSwarm,
      sourceLabel = peerSourceLabel,
    )
  }

  if (!isLoading && isPaused) {
    AnimatedVisibility(
      visible = showPausedInfo && activePanel == PlayerPanel.None,
      enter = fadeIn(animationSpec = tween(320)),
      exit = fadeOut(animationSpec = tween(180)),
    ) { PlayerPausedGradient() }
    AnimatedVisibility(
      visible = showPausedInfo && activePanel == PlayerPanel.None,
      enter = fadeIn(animationSpec = tween(240)) + slideInVertically(initialOffsetY = { it / 8 }, animationSpec = tween(360)),
      exit = fadeOut(animationSpec = tween(160)) + slideOutVertically(targetOffsetY = { it / 10 }, animationSpec = tween(220)),
    ) { PlayerPausedContent(session = session) }
  }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = if (isLoading || controlsLocked || (!showControls && activePanel == PlayerPanel.None)) 0.0f else if (activePanel == PlayerPanel.None) 0.18f else 0.58f))
      .pointerInput(session.url, isLoading, controlsLocked) {
        if (isLoading || controlsLocked) return@pointerInput
        detectTransformGestures { _, _, zoom, _ ->
          if (zoom != 1f) {
            customZoom = (customZoom * zoom).coerceIn(1f, 3f)
            resizeModeState.value = if (customZoom <= 1.01f) "contain" else "custom"
          }
        }
      }
      // Press and hold anywhere to play faster, for as long as the finger stays down. Kept in
      // its own non-consuming detector so the tap and drag handlers below still see every
      // event exactly as they did before.
      .pointerInput(session.url, isLoading, controlsLocked, session.holdToSpeedEnabled) {
        if (isLoading || controlsLocked || !session.holdToSpeedEnabled) return@pointerInput
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          var boosted = false
          try {
            // A press that ends, or wanders, before the delay is somebody tapping or swiping.
            val settled = withTimeoutOrNull(HOLD_TO_SPEED_DELAY_MS) {
              while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Unit
                if (!change.pressed) return@withTimeoutOrNull Unit
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                  return@withTimeoutOrNull Unit
                }
              }
              @Suppress("UNREACHABLE_CODE") Unit
            }
            if (settled == null) {
              boosted = true
              speedBoostActive = true
              while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
              }
            }
          } finally {
            if (boosted) {
              speedBoostActive = false
              suppressNextTap = true
            }
          }
        }
      }
      .pointerInput(session.url, session.isLive, isLoading, channelSwitchLoading, controlsLocked, audioManager, session.swipeToSeekEnabled, session.levelGesturesEnabled, duration) {
        // A channel switch counts as loading, but the lists are how a viewer picks a different channel
        // instead of waiting for this one, so their swipes stay available through it.
        if ((!isLoading || (session.isLive && channelSwitchLoading)) && !controlsLocked) {
          var totalX = 0f
          var totalY = 0f
          var dragStartX = 0f
          var initialBrightness = 0.5f
          var initialVolume = 0.5f
          var didAdjustLevel = false
          var scrubFromSeconds = 0.0
          // Scrubbing is for material with a known length: a linear channel has nothing to
          // scrub through, and the horizontal swipe there already opens the favourites drawer.
          val canScrub = session.swipeToSeekEnabled && !session.isLive && duration > 1.0
          val canAdjustLevels = session.levelGesturesEnabled
          detectDragGestures(
            onDragStart = { offset ->
              totalX = 0f
              totalY = 0f
              dragStartX = offset.x
              initialBrightness = currentWindowBrightness()
              initialVolume = currentMediaVolume()
              didAdjustLevel = false
              scrubFromSeconds = currentTime
            },
            onDragEnd = {
              scrubTargetSeconds?.let { target ->
                activeSeekTo(target)
                currentTime = target
                scrubTargetSeconds = null
                suppressNextTap = true
                keepControlsVisible()
              }
              val startedInChannelZone = dragStartX >= size.width * 0.33f && dragStartX < size.width * 0.67f
              if (session.isLive && !didAdjustLevel) {
                when {
                  startedInChannelZone && totalY < -90f && kotlin.math.abs(totalY) > kotlin.math.abs(totalX) -> {
                    showFavouriteDrawer = false
                    showLiveChannels = true
                    showChannelSwipeCue = false
                    showControls = false
                    activePanel = PlayerPanel.None
                  }
                  totalX < -90f && kotlin.math.abs(totalX) > kotlin.math.abs(totalY) -> {
                    showLiveChannels = false
                    showFavouriteDrawer = true
                    showControls = false
                    activePanel = PlayerPanel.None
                  }
                  totalY > 90f && showLiveChannels -> showLiveChannels = false
                }
              }
            },
            onDrag = { change, amount ->
              totalX += amount.x
              totalY += amount.y
              val verticalGesture = kotlin.math.abs(totalY) > kotlin.math.abs(totalX) && kotlin.math.abs(totalY) > 12f
              val horizontalGesture = kotlin.math.abs(totalX) > kotlin.math.abs(totalY) && kotlin.math.abs(totalX) > 16f
              when {
                // Turned off, the two level drags simply are not here: the branches fall through,
                // nothing is adjusted, and `didAdjustLevel` stays false so a vertical swipe is
                // still available to the live channel list below.
                canAdjustLevels && verticalGesture && dragStartX < size.width * 0.33f -> {
                  applyBrightness(adjustedPlayerLevel(initialBrightness, totalY, size.height.toFloat()))
                  didAdjustLevel = true
                }
                canAdjustLevels && verticalGesture && dragStartX >= size.width * 0.67f -> {
                  applyMediaVolume(adjustedPlayerLevel(initialVolume, totalY, size.height.toFloat()))
                  didAdjustLevel = true
                }
                horizontalGesture && canScrub && !didAdjustLevel -> {
                  // A full sweep of the screen covers SCRUB_FULL_WIDTH_SECONDS, so the same
                  // finger movement means the same jump whatever the runtime.
                  val offsetSeconds = (totalX / size.width.toFloat()) * SCRUB_FULL_WIDTH_SECONDS
                  scrubTargetSeconds = (scrubFromSeconds + offsetSeconds).coerceIn(0.0, duration)
                }
              }
              change.consume()
            },
          )
        }
      }
      .pointerInput(session.url, isLoading, controlsLocked, activePanel, duration, session.doubleTapSeekEnabled, session.doubleTapSeekSeconds, session.doubleTapPlayPauseEnabled) {
        if (isLoading) return@pointerInput
        detectTapGestures(
          onDoubleTap = { offset ->
            if (controlsLocked || activePanel != PlayerPanel.None) return@detectTapGestures
            val fraction = offset.x / size.width.coerceAtLeast(1).toFloat()
            val seekDelta = playerDoubleTapSeekDelta(
              horizontalFraction = fraction,
              enabled = session.doubleTapSeekEnabled,
              stepSeconds = session.doubleTapSeekSeconds,
              seekable = duration > 0.0,
            )
            when {
              seekDelta != null -> {
                val amount = seekDelta
                currentTime = clampedPlayerSeekPosition(currentTime, amount, duration)
                activeSeekTo(currentTime)
                val sameDirection = (seekFeedbackAmount < 0 && amount < 0) || (seekFeedbackAmount > 0 && amount > 0)
                val nextFeedbackAmount = if (sameDirection) seekFeedbackAmount + amount else amount
                seekFeedbackAmount = nextFeedbackAmount
                displayedSeekFeedbackAmount = nextFeedbackAmount
                seekFeedbackVersion += 1
              }
              isPlayerCenterDoubleTap(fraction, session.doubleTapPlayPauseEnabled) -> {
                playPauseFeedback = !isPaused
                onTogglePause()
              }
            }
          },
          onTap = {
            if (suppressNextTap) {
          // The finger that just lifted was holding to speed up, or scrubbing.
              suppressNextTap = false
            } else if (controlsLocked) {
              showUnlockControl = true
              unlockActivityVersion += 1
            } else if (showLiveChannels) {
              showLiveChannels = false
              showControls = false
            } else if (showFavouriteDrawer) {
              showFavouriteDrawer = false
              showControls = false
            } else if (activePanel == PlayerPanel.None) {
              showPausedInfo = false
              showControls = !showControls
            }
          },
        )
      },
    )
}


/**
 * The live-channel furniture: the swipe cue, the channel-switch progress card, the LIVE/VOD badge,
 * the channel tray and the favourites drawer.
 *
 * Only ever visible for a live session, but it was being composed -- and its lambdas memoised -- in
 * [NativePlayerScreen] for every session, live or not.
 */
@Composable
private fun BoxScope.PlayerLiveOverlays(
  session: PlayerSession,
  source: PlayerSourceState,
  playback: PlayerPlaybackState,
  isLoading: Boolean,
  channelSwitchLoading: Boolean,
  channelSwitchLoadingLabel: String?,
  liveChannels: List<MediaItem>,
  liveChannelsLoading: Boolean,
  favouriteChannels: List<MediaItem>,
  favouriteChannelIds: Set<String>,
  favouriteDrawerCards: Boolean,
  activePanelState: MutableState<PlayerPanel>,
  controlsLockedState: MutableState<Boolean>,
  onSelectLiveChannel: (MediaItem) -> Unit,
  onToggleFavourite: () -> Unit,
  onToggleFavouriteDrawerCards: (Boolean) -> Unit,
  onClearFavourites: () -> Unit,
) {
  // Re-bound so the bodies below read and write exactly as they did in the screen.
  var activePanel by activePanelState
  var controlsLocked by controlsLockedState
  var error by source.error
  var hasLoaded by playback.hasLoaded
  var showControls by source.showControls
  var showChannelSwipeCue by source.showChannelSwipeCue
  var showFavouriteDrawer by source.showFavouriteDrawer
  var showLiveChannels by source.showLiveChannels
  var slowLoadHintVisible by source.slowLoadHintVisible
  var pendingChannelSelection by source.pendingChannelSelection
  AnimatedVisibility(
    visible = session.isLive && !isLoading && !controlsLocked && showChannelSwipeCue && !showControls && activePanel == PlayerPanel.None && !showLiveChannels && !showFavouriteDrawer,
    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).zIndex(18f),
    enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(280)),
    exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 3 }, animationSpec = tween(220)),
  ) { LiveChannelSwipeCue() }

  AnimatedVisibility(
    visible = session.isLive && (!isLoading || channelSwitchLoading) && !controlsLocked && !showFavouriteDrawer,
    // Above the switch's tint, so the favourites stay one tap away while a channel loads.
    modifier = Modifier.align(Alignment.CenterEnd).zIndex(31f),
    enter = fadeIn(tween(180)) + slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(260)),
    exit = fadeOut(tween(160)) + slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)),
  ) {
    Surface(
      modifier = Modifier.width(36.dp).height(92.dp).clickable { showLiveChannels = false; showFavouriteDrawer = true },
      color = Color(0xB3151820),
      shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
    ) { Box(contentAlignment = Alignment.Center) { Icon(StreamDekPlayerIcons.ChevronLeft, contentDescription = stringResource(R.string.a11y_open_favourites), tint = Color.White, modifier = Modifier.size(28.dp)) } }
  }

  AnimatedVisibility(
    // Above the switching tint and label (29f, 30f): a channel can be picked while another loads.
    visible = session.isLive && showLiveChannels,
    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(32f),
    enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(340)),
    exit = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(260)),
  ) {
    LiveChannelTray(
      channels = liveChannels,
      currentChannelId = session.mediaId,
      loading = liveChannelsLoading,
      favouriteChannelIds = favouriteChannelIds,
      onSelect = { channel -> showLiveChannels = false; pendingChannelSelection = channel },
    )
  }

  AnimatedVisibility(
    visible = session.isLive && showFavouriteDrawer,
    modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.275f).fillMaxHeight().zIndex(33f),
    enter = fadeIn(tween(180)) + slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(360)),
    exit = fadeOut(tween(160)) + slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280)),
  ) {
    LiveFavouriteDrawer(
      favourites = favouriteChannels,
      currentChannelId = session.mediaId,
      cardView = favouriteDrawerCards,
      onClose = { showFavouriteDrawer = false },
      onSelect = { channel -> showFavouriteDrawer = false; pendingChannelSelection = channel },
      onToggleCardView = onToggleFavouriteDrawerCards,
      onClearAll = onClearFavourites,
    )
  }

  AnimatedVisibility(
    visible = session.isLive && channelSwitchLoading,
    modifier = Modifier.fillMaxSize().zIndex(29f),
    enter = fadeIn(tween(160)),
    exit = fadeOut(tween(160)),
  ) {
    // The previous channel keeps decoding underneath (see ExoPlaybackView's background
    // swap / mpv's loadfile replace) - this is a tint over it, not a solid cover, so it
    // stays visible while the new one loads instead of cutting to black.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
  }

  AnimatedVisibility(
    visible = session.isLive && channelSwitchLoading,
    modifier = Modifier.align(Alignment.Center).zIndex(30f),
    enter = fadeIn(tween(160)),
    exit = fadeOut(tween(160)),
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
      Text(
        if (slowLoadHintVisible) {
          channelSwitchLoadingLabel?.let { "$it is available but is taking a while to load…" } ?: "This channel is available but is taking a while to load…"
        } else {
          channelSwitchLoadingLabel?.let { "Switching to $it" } ?: "Switching channel"
        },
        color = Color.White,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 12f)),
      )
    }
  }
  if (session.isLive && LocalPlayerBadgeSetting.current.visible && hasLoaded && !isLoading && error.isNullOrBlank() && !showLiveChannels && !showFavouriteDrawer) {
    Surface(
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp).zIndex(12f),
      color = if (session.isVod) Color(0xFF2563EB) else Color(0xFFE11D48),
      shape = CircleShape,
    ) {
      // Every dimension here is the previous one scaled by 0.6 — the badge reads as a label on the
      // picture rather than a control competing with it.
      Row(modifier = Modifier.padding(horizontal = 8.1.dp, vertical = 3.8.dp), horizontalArrangement = Arrangement.spacedBy(3.6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (session.isVod) {
          Icon(StreamDekPlayerIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(7.2.dp))
        } else {
          Box(modifier = Modifier.size(3.8.dp).clip(CircleShape).background(Color.White))
        }
        Text(if (session.isVod) "VOD" else "LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 6.5.sp, letterSpacing = 0.54.sp)
      }
    }
  }
}
