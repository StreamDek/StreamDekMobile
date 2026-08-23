package net.streamdek.mobile.nativeapp

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.View
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HideSource
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SettingsOverscan
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.Dispatchers
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
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import kotlin.math.roundToInt

private enum class PlayerPanel { None, Sources, Audio, Subtitles, Speed, Engine, Info }
private enum class PlayerAdjustmentKind { Brightness, Volume }

internal fun adjustedPlayerLevel(initial: Float, totalDragY: Float, playerHeight: Float): Float =
  (initial - (totalDragY / playerHeight.coerceAtLeast(1f)) * 1.5f).coerceIn(0f, 1f)
/** How many sources the player's Sources panel lists. The playing source is hoisted above this
 *  cut, so it is always listed however far down the unsorted list it started. */
private const val MAX_PLAYER_SOURCE_ROWS = 30

internal enum class ActivePlaybackEngine { Media3, MPV }
internal fun initialPlaybackEngine(preference: String): ActivePlaybackEngine =
  if (preference.equals("MPV", ignoreCase = true)) ActivePlaybackEngine.MPV else ActivePlaybackEngine.Media3
/** How often playback position is written back while a title is running. */
private const val PROGRESS_CHECKPOINT_SECONDS = 30.0

internal fun shouldAutoFallbackToMpv(preference: String, activeEngine: ActivePlaybackEngine, fallbackUsed: Boolean): Boolean =
  preference.equals("Auto", ignoreCase = true) && activeEngine == ActivePlaybackEngine.Media3 && !fallbackUsed
internal fun nextUntriedPlaybackSource(
  availableStreams: List<AddonStream>,
  currentStream: AddonStream?,
  failedKeys: Set<String>,
): AddonStream? {
  val excluded = if (currentStream == null) failedKeys else failedKeys + playerStreamIdentity(currentStream)
  return availableStreams.firstOrNull { playerStreamIdentity(it) !in excluded }
}
private enum class SubtitlePanelTab { BuiltIn, Addons, Style }
private data class ExternalSubtitle(val id: String, val language: String, val label: String, val url: String)
private data class SkipSegment(val type: String, val startSeconds: Double, val endSeconds: Double)

private fun episodeContext(session: PlayerSession): String? = if (session.seasonNumber != null && session.episodeNumber != null) {
  listOf("S${session.seasonNumber}", "E${session.episodeNumber}", session.episodeTitle?.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
} else null

@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
fun NativePlayerScreen(
  session: PlayerSession,
  availableStreams: List<AddonStream>,
  handoffDevices: List<LinkedTvDevice> = emptyList(),
  onHandoff: suspend (LinkedTvDevice, Double) -> Result<PlaybackHandoffReceipt> = { _, _ -> Result.failure(IllegalStateException("Handoff is unavailable.")) },
  onBack: (Double) -> Unit,
  onScrobble: (String, Double) -> Unit,
  onProgressCheckpoint: (Double, Double) -> Unit,
  onSelectStream: (AddonStream, Double) -> Unit,
  onReloadStreams: () -> Unit,
  onPlaybackEnded: () -> Unit,
  nextEpisodeLoading: Boolean = false,
  nextEpisodeLoadingLabel: String? = null,
  onPreviousEpisode: () -> Unit = {},
  onNextEpisode: () -> Unit = {},
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
) {
  val playerContext = LocalContext.current
  val playerScope = rememberCoroutineScope()
  val activity = playerContext as? Activity
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
  var isPaused by remember(session.url) { mutableStateOf(false) }
  var currentTime by remember(session.url) { mutableDoubleStateOf(0.0) }
  var duration by remember(session.url) { mutableDoubleStateOf(0.0) }
  var error by remember(session.url) { mutableStateOf<String?>(null) }
  var hasLoaded by remember(session.url) { mutableStateOf(false) }
  var playerView by remember(liveEngineKey) { mutableStateOf<MPVView?>(null) }
  var exoPlayerView by remember(liveEngineKey) { mutableStateOf<ExoPlaybackView?>(null) }
  var activeEngine by remember(liveEngineKey, session.playerEngine) { mutableStateOf(initialPlaybackEngine(session.playerEngine)) }
  var autoFallbackUsed by remember(liveEngineKey, session.playerEngine) { mutableStateOf(false) }
  var pendingEngineResumeSeconds by remember(session.url) { mutableDoubleStateOf(0.0) }
  fun activeAddSubtitle(path: String, language: String?) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.addSubtitleFile(path, language) else playerView?.addSubtitleFile(path, language) }
  fun activeReload() { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.reloadSource() else playerView?.reloadSource() }
  fun activeSetPaused(paused: Boolean) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.setPaused(paused) else playerView?.setPaused(paused) }
  fun activeSeekTo(seconds: Double) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.seekTo(seconds) else playerView?.seekTo(seconds) }
  fun activeSetAudioTrack(id: Int) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.setAudioTrack(id) else playerView?.setAudioTrack(id) }
  fun activeDisableSubtitleTrack() { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.disableSubtitleTrack() else playerView?.disableSubtitleTrack() }
  fun activeSetSubtitleTrack(id: Int) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.setSubtitleTrack(id) else playerView?.setSubtitleTrack(id) }
  fun activeSetSubtitleFontSize(size: Int) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.setSubtitleFontSize(size) else playerView?.setSubtitleFontSize(size) }
  fun activeSetSubtitlePosition(position: Int) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.setSubtitlePosition(position) else playerView?.setSubtitlePosition(position) }
  fun activeSetSubtitleBackgroundColor(color: String) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.setSubtitleBackgroundColor(color) else playerView?.setSubtitleBackgroundColor(color) }
  fun activeSetSubtitleOutline(enabled: Boolean, color: String) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.setSubtitleOutline(enabled, color) else playerView?.setSubtitleOutline(enabled, color) }
  fun activeSetSubtitleBold(bold: Boolean) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.setSubtitleBold(bold) else playerView?.setSubtitleBold(bold) }
  fun activeSetSubtitleDelay(seconds: Double) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.setSubtitleDelay(seconds) else playerView?.setSubtitleDelay(seconds) }
  fun activeSetSpeed(speed: Double) { if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.setSpeed(speed) else playerView?.setSpeed(speed) }
  var resizeMode by rememberSaveable(session.url) { mutableStateOf("cover") }
  var playbackSpeed by rememberSaveable(session.url) { mutableFloatStateOf(1f) }
  var activePanel by rememberSaveable(session.url) { mutableStateOf(PlayerPanel.None) }
  var playbackStats by remember(session.url) { mutableStateOf<PlaybackStats?>(null) }
  var subtitleDelay by rememberSaveable(session.url) { mutableFloatStateOf(0f) }
  // Seeded from settings rather than from a constant, and keyed on the setting rather than on the
  // stream, so a size or colour chosen once carries into the next episode instead of resetting.
  var subtitleSize by remember(session.subtitleTextSize) { mutableIntStateOf(session.subtitleTextSize) }
  var subtitlePosition by remember(session.subtitleVerticalOffset) { mutableIntStateOf(session.subtitleVerticalOffset) }
  var subtitleColor by remember(session.subtitleTextColor) { mutableStateOf(session.subtitleTextColor) }
  var selectedAudioTrackId by remember(session.url) { mutableStateOf<Int?>(null) }
  var selectedSubtitleTrackId by remember(session.url) { mutableStateOf<Int?>(null) }
  var preferredAudioTrackKey by remember(session.url) { mutableStateOf<String?>(null) }
  var preferredSubtitleTrackKey by remember(session.url) { mutableStateOf<String?>(null) }
  var subtitleTab by remember(session.subtitleDefaultSource) {
    mutableStateOf(SubtitlePanelTab.entries.firstOrNull { it.name == session.subtitleDefaultSource } ?: SubtitlePanelTab.BuiltIn)
  }
  var subtitleDisabledByUser by remember(session.url) { mutableStateOf(false) }
  /** Shown in the add-on tab when a chosen subtitle could not be loaded at all. */
  var subtitleErrorMessage by remember(session.url) { mutableStateOf<String?>(null) }
  var userPickedAudio by remember(session.url) { mutableStateOf(false) }
  var userPickedSubtitle by remember(session.url) { mutableStateOf(false) }
  var externalSubtitles by remember(session.url) { mutableStateOf<List<ExternalSubtitle>>(emptyList()) }
  var selectedExternalSubtitleId by remember(session.url) { mutableStateOf<String?>(null) }
  var externalSubtitleNeedsReapply by remember(session.url) { mutableStateOf(false) }
  var subtitlesLoading by remember(session.url) { mutableStateOf(false) }
  var skipSegments by remember(session.url) { mutableStateOf<List<SkipSegment>>(emptyList()) }
  val audioTracks = remember(session.url) { mutableStateListOf<MpvTrackInfo>() }
  val subtitleTracks = remember(session.url) { mutableStateListOf<MpvTrackInfo>() }
  var scrobbleStarted by remember(session.url) { mutableStateOf(false) }
  var showControls by remember(session.url) { mutableStateOf(true) }
  var controlsLocked by rememberSaveable(session.url) { mutableStateOf(false) }
  var showUnlockControl by remember(session.url) { mutableStateOf(false) }
  var unlockActivityVersion by remember(session.url) { mutableIntStateOf(0) }
  var controlActivityVersion by remember(session.url) { mutableIntStateOf(0) }
  var playbackEnded by remember(session.url) { mutableStateOf(false) }
  var completionDispatched by remember(session.url) { mutableStateOf(false) }
  var liveReconnectVersion by remember(session.url) { mutableIntStateOf(0) }
  var liveStalled by remember(session.url) { mutableStateOf(false) }
  var liveRetryAttempts by remember(session.url) { mutableIntStateOf(0) }
  var lastLiveRetryAtMs by remember(session.url) { mutableStateOf(0L) }
  var showPausedInfo by remember(session.url) { mutableStateOf(false) }
  /** Set while a press-and-hold is boosting playback; cleared the moment the finger lifts. */
  var speedBoostActive by remember(session.url) { mutableStateOf(false) }
  /**
   * Where a horizontal scrub currently points, in seconds. Non-null only while the finger is
   * down: the seek is committed on release so a long drag is one seek, not hundreds.
   */
  var scrubTargetSeconds by remember(session.url) { mutableStateOf<Double?>(null) }
  /**
   * Lifting after a hold or a scrub is still an "up" as far as the tap handler is concerned, and
   * without this the controls would flash on every time one of those gestures ended.
   */
  var suppressNextTap by remember(session.url) { mutableStateOf(false) }

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
  var showLiveChannels by remember(session.url) { mutableStateOf(false) }
  // Keyed on the live identity rather than session.url so the choice carries across a channel
  // switch, and on the setting so changing it from Settings re-seeds a session already playing.
  var showLiveProgress by remember(liveEngineKey, session.showLiveProgressBar) { mutableStateOf(session.showLiveProgressBar) }
  var showFavouriteDrawer by remember(session.url) { mutableStateOf(false) }
  var pendingChannelSelection by remember(session.url) { mutableStateOf<MediaItem?>(null) }
  var showChannelSwipeCue by remember(session.url) { mutableStateOf(false) }
  var didApplyResume by remember(session.url) { mutableStateOf(false) }
  var lastCheckpointSecond by remember(session.url) { mutableDoubleStateOf(0.0) }
  var slowLoadHintVisible by remember(session.url) { mutableStateOf(false) }
  var avMismatchFallbackTried by remember(session.url) { mutableStateOf(false) }
  var loadedVideoWidth by remember(session.url) { mutableIntStateOf(0) }
  var loadedVideoHeight by remember(session.url) { mutableIntStateOf(0) }
  val failedSourceKeys = remember(session.mediaId, session.seasonNumber, session.episodeNumber) { mutableStateListOf<String>() }
  var handoffPickerVisible by remember(session.url) { mutableStateOf(false) }
  var handoffLoading by remember(session.url) { mutableStateOf(false) }
  var handoffError by remember(session.url) { mutableStateOf<String?>(null) }
  var adjustmentKind by remember { mutableStateOf<PlayerAdjustmentKind?>(null) }
  var adjustmentLevel by remember { mutableFloatStateOf(0f) }
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
  val currentProgressPercent = progressPercent()
  val activeSkipSegment = skipSegments.firstOrNull { currentTime >= it.startSeconds && currentTime < it.endSeconds }
  val nextEpisodeActionAvailable = activeSkipSegment?.type == "outro" && session.mediaType == "tv" && session.autoPlayNextEpisode && duration > 0.0 && run {
    val thresholdStart = if (session.nextEpisodeThresholdMode == "percent") {
      duration * (session.nextEpisodeThresholdPercent.coerceIn(50, 99) / 100.0)
    } else {
      duration - session.nextEpisodeThresholdMinutes.coerceIn(1, 15) * 60.0
    }
    currentTime >= maxOf(activeSkipSegment.startSeconds, thresholdStart)
  }

  fun closePlayer() = onBack(progressPercent())
  fun finishPlayback() {
    if (completionDispatched) return
    completionDispatched = true
    if (session.isLive) return
    isPaused = true
    onScrobble("stop", 100.0)
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
      channelSwitchLoading -> onCancelChannelSwitch()
      session.isLive && channelSwitchFallbackAvailable && !error.isNullOrBlank() -> onCancelChannelSwitch()
      showFavouriteDrawer -> showFavouriteDrawer = false
      showLiveChannels -> showLiveChannels = false
      !controlsLocked -> closePlayer()
    }
  }

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

  DisposableEffect(activity) {
    val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    val previousBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
    val decorView = activity?.window?.decorView
    val previousSystemUi = decorView?.systemUiVisibility ?: 0
    MainActivity.pipShouldEnter = session.pictureInPictureEnabled
    // Claimed before the rotation is requested: the activity re-applies its own orientation policy
    // on configuration changes, and the rotation asked for here arrives as one of those.
    MainActivity.playerOwnsOrientation = true
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    decorView?.systemUiVisibility = (
      View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      )
    onDispose {
      MainActivity.pipShouldEnter = false
      MainActivity.playerOwnsOrientation = false
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
  LaunchedEffect(activePanel, activeEngine, exoPlayerView, playerView) {
    if (activePanel != PlayerPanel.Info) {
      playbackStats = null
      return@LaunchedEffect
    }
    while (true) {
      playbackStats = if (activeEngine == ActivePlaybackEngine.Media3) exoPlayerView?.playbackStats() else playerView?.playbackStats()
      delay(1_000)
    }
  }

  LaunchedEffect(playbackEnded) {
    if (playbackEnded) {
      delay(450)
      onPlaybackEnded()
    }
  }

  LaunchedEffect(session.url, session.skipIntroEnabled, session.skipRecapEnabled, session.skipEndingEnabled, session.introdbApiKey, session.isLive) {
    if (session.isLive) {
      skipSegments = emptyList()
      return@LaunchedEffect
    }
    skipSegments = fetchSkipSegments(session).filter { segment ->
      when (segment.type) {
        "intro" -> session.skipIntroEnabled
        "recap" -> session.skipRecapEnabled
        "outro" -> session.skipEndingEnabled
        else -> false
      }
    }
  }

  // Deliberately not keyed on duration or on subtitleDisabledByUser.
  //
  // Duration is revised as a stream loads -- a usenet assembly or a growing HLS window can report
  // 0 and then several different figures -- and every revision cancelled this effect and restarted
  // it, delay and all, so the lookup could be perpetually one revision away from running. Whether
  // the viewer has switched subtitles off is not a reason to have no list either: the panel is
  // where they go to switch them back on, and it has to have something in it when they get there.
  // The list is fetched once per source, and what is done with it is decided below.
  LaunchedEffect(session.url, session.autoLoadSubtitles, playerView, exoPlayerView, session.isLive, userSubtitleSources) {
    if (session.isLive) return@LaunchedEffect
    if (playerView == null && exoPlayerView == null) return@LaunchedEffect
    delay(1_200)
    subtitlesLoading = true
    val results = fetchExternalSubtitles(session, userSubtitleSources)
    externalSubtitles = results
    // Auto-selection is the part that has to respect those two, not the lookup above.
    if (session.autoLoadSubtitles && selectedSubtitleTrackId == null && selectedExternalSubtitleId == null &&
      !subtitleDisabledByUser && !userPickedSubtitle
    ) {
      results.firstOrNull { it.language == "en" }?.let { subtitle ->
        selectedExternalSubtitleId = subtitle.id
        // Download off the player thread first — handing mpv a remote URL stalls
        // playback while it fetches the file.
        val localPath = downloadSubtitleToCache(playerContext, subtitle.url)
        if (localPath != null && selectedExternalSubtitleId == subtitle.id) activeAddSubtitle(localPath, subtitle.language)
      }
    }
    subtitlesLoading = false
  }
  LaunchedEffect(activeEngine, playerView, externalSubtitleNeedsReapply, selectedExternalSubtitleId, externalSubtitles) {
    if (activeEngine != ActivePlaybackEngine.MPV || playerView == null || !externalSubtitleNeedsReapply) return@LaunchedEffect
    val subtitle = externalSubtitles.firstOrNull { it.id == selectedExternalSubtitleId } ?: return@LaunchedEffect
    delay(700)
    val localPath = downloadSubtitleToCache(playerContext, subtitle.url)
    if (localPath != null && activeEngine == ActivePlaybackEngine.MPV && selectedExternalSubtitleId == subtitle.id) {
      playerView?.addSubtitleFile(localPath, subtitle.language)
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
  // Shared budget check for every live-feed retry trigger (stall watchdog below AND mpv's own
  // error callback). Both used to bump liveReconnectVersion directly, but only the watchdog
  // respected the 5-attempt cap — a stream that mpv rejects outright (bad URL, wrong protocol,
  // missing required headers) errors out again within milliseconds of every reload, so the
  // uncapped path re-triggered itself continuously with only a ~300ms backoff: exactly what
  // "keeps reloading and reloading" looks like. Routing both triggers through this one function
  // means every retry — however it was triggered — counts against the same budget before
  // failing over to the next source or giving up.
  fun retryOrFailoverLiveFeed() {
    val now = System.currentTimeMillis()
    // A long gap since the previous retry means the feed recovered — reset the budget.
    if (now - lastLiveRetryAtMs > 60_000L) liveRetryAttempts = 0
    if (liveRetryAttempts >= 5) {
      android.util.Log.w("StreamDekLivePlayer", "retry budget exhausted for ${session.url}, looking for a next source")
      val currentKey = playerStreamIdentity(session.currentStream)
      val currentIndex = availableStreams.indexOfFirst { playerStreamIdentity(it) == currentKey }
      val nextSource = availableStreams.drop((currentIndex + 1).coerceAtLeast(0)).firstOrNull { playerStreamIdentity(it) != currentKey }
      if (nextSource != null) {
        error = "Switching to another source..."
        onSelectStream(nextSource, 0.0)
      } else {
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
    if (session.isLive && channelSwitchLoading) onChannelSwitchPlaybackStarted()
    duration = loadedDuration
    error = null
    if (session.autoLoadSubtitles && !userPickedSubtitle && !subtitleDisabledByUser && selectedSubtitleTrackId == null) {
      subtitleTracks.firstOrNull { normalizeSubtitleLanguage(it.language) == "en" }?.let { englishSubtitle ->
        selectedSubtitleTrackId = englishSubtitle.id
        activeSetSubtitleTrack(englishSubtitle.id)
      }
    }
    if (pendingEngineResumeSeconds > 0.0 && loadedDuration > 0.0) {
      val resumeAt = pendingEngineResumeSeconds.coerceIn(0.0, (loadedDuration - 2.0).coerceAtLeast(0.0))
      pendingEngineResumeSeconds = 0.0
      activeSeekTo(resumeAt)
      currentTime = resumeAt
    } else if (!didApplyResume && session.resumePercent > 0.0 && loadedDuration > 0.0) {
      val resumeAt = (loadedDuration * (session.resumePercent / 100.0)).coerceIn(0.0, (loadedDuration - 5.0).coerceAtLeast(0.0))
      activeSeekTo(resumeAt)
      currentTime = resumeAt
      didApplyResume = true
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
    if (target == ActivePlaybackEngine.Media3) playerView = null else exoPlayerView = null
    android.util.Log.w("StreamDekPlayer", "Switching playback engine to $target ($reason) at ${pendingEngineResumeSeconds}s")
    activeEngine = target
  }
  val playerErrorCallback: (String) -> Unit = { message ->
    if (shouldAutoFallbackToMpv(session.playerEngine, activeEngine, autoFallbackUsed)) {
      autoFallbackUsed = true
      switchEngine(ActivePlaybackEngine.MPV, "Media3 error: $message")
    } else if (session.isLive) {
      android.util.Log.w("StreamDekLivePlayer", "player error for ${session.url}: $message")
      error = "Live feed interrupted. Reconnecting..."
      retryOrFailoverLiveFeed()
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
  val playerStallCallback: (Boolean) -> Unit = { stalled -> if (session.isLive) liveStalled = stalled }
  fun trackPreferenceKey(track: MpvTrackInfo): String = listOf(
    normalizeSubtitleLanguage(track.language).orEmpty(),
    track.title.orEmpty().trim().lowercase(),
  ).joinToString("|")
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
      val englishSubtitle = subtitles.firstOrNull { normalizeSubtitleLanguage(it.language) == "en" }
      when {
        session.autoLoadSubtitles && englishSubtitle != null && subtitleId != englishSubtitle.id -> {
          selectedSubtitleTrackId = englishSubtitle.id
          activeSetSubtitleTrack(englishSubtitle.id)
        }
        (!session.autoLoadSubtitles || englishSubtitle == null) && subtitleId != null -> {
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
    val noVideo = loadedVideoWidth <= 0 && loadedVideoHeight <= 0
    val noAudio = audioTracks.isEmpty()
    if (noVideo != noAudio) {
      avMismatchFallbackTried = true
      val target = if (activeEngine == ActivePlaybackEngine.Media3) ActivePlaybackEngine.MPV else ActivePlaybackEngine.Media3
      switchEngine(target, if (noAudio) "no audio detected" else "no video detected")
    }
  }

  if (handoffPickerVisible) {
    AlertDialog(
      onDismissRequest = { if (!handoffLoading) handoffPickerVisible = false },
      title = { Text("Continue on a TV", fontWeight = FontWeight.Black) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Choose a TV linked to your StreamDek account. Playback will resume at ${formatClock(currentTime)}.")
          if (handoffDevices.isEmpty()) {
            Text("No linked TVs are available. Link a TV from Settings > Connect to TV first.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
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
      confirmButton = { TextButton(onClick = { handoffPickerVisible = false }, enabled = !handoffLoading) { Text("Cancel") } },
    )
  }
  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    PlayerSurface(
      session = session,
      engineKey = liveEngineKey,
      activeEngine = activeEngine,
      isPaused = isPaused,
      resizeMode = resizeMode,
      playbackSpeed = playbackSpeed,
      subtitleDelay = subtitleDelay,
      subtitleSize = subtitleSize,
      subtitlePosition = subtitlePosition,
      subtitleColor = subtitleColor,
      onLoad = playerLoadCallback,
      onProgress = playerProgressCallback,
      onError = playerErrorCallback,
      onEnd = playerEndCallback,
      onStallChanged = playerStallCallback,
      onTracksChanged = playerTracksCallback,
      onExoViewCreated = { view ->
        exoPlayerView = view
        // Dolby Vision profile 7 is the one case Media3 loses silently: it decodes, reports frames,
        // and shows black, so the error-driven fallback never fires. The stream is recognised the
        // moment its track is selected and handed to mpv, which decodes the HEVC base layer. Not
        // gated on the Auto engine preference the way an error is -- turning the setting on is
        // itself the instruction to play these files with whatever can. See Dv7Hevc.
        view.onDolbyVisionProfile7Callback = {
          if (activeEngine == ActivePlaybackEngine.Media3 && !autoFallbackUsed) {
            autoFallbackUsed = true
            switchEngine(ActivePlaybackEngine.MPV, "Dolby Vision profile 7")
          }
        }
      },
      onMpvViewCreated = { playerView = it },
    )

    AnimatedVisibility(
      visible = !isLoading && activePanel == PlayerPanel.None && activeSkipSegment != null,
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 118.dp).zIndex(4f),
      enter = fadeIn(animationSpec = tween(180)),
      exit = fadeOut(animationSpec = tween(140)),
    ) {
      Button(
        onClick = {
          if (nextEpisodeActionAvailable) {
            onNextEpisode()
          } else {
            activeSkipSegment?.let { segment ->
              currentTime = segment.endSeconds
              activeSeekTo(segment.endSeconds)
              skipSegments = skipSegments - segment
            }
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        shape = RoundedCornerShape(22.dp),
      ) { Text(if (nextEpisodeActionAvailable) "Next Episode" else when (activeSkipSegment?.type) { "recap" -> "Skip Recap"; "outro" -> "Skip Ending"; else -> "Skip Intro" }, fontWeight = FontWeight.Bold) }
    }
    // A live-channel switch has its own compact status indicator below. Keeping the
    // general loading backdrop here as well produced two competing loading messages.
    if (isLoading && !(session.isLive && channelSwitchLoading)) {
      PlayerLoadingBackdrop(
        session = session,
        message = when {
          nextEpisodeLoading -> listOfNotNull("Loading next episode", nextEpisodeLoadingLabel).joinToString(" · ")
          session.isLive && slowLoadHintVisible -> "This channel is available but is taking a while to load…"
          else -> "Preparing stream..."
        },
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
        .pointerInput(session.url, session.isLive, isLoading, controlsLocked, audioManager, session.swipeToSeekEnabled, duration) {
          if (!isLoading && !controlsLocked) {
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
                  verticalGesture && dragStartX < size.width * 0.33f -> {
                    applyBrightness(adjustedPlayerLevel(initialBrightness, totalY, size.height.toFloat()))
                    didAdjustLevel = true
                  }
                  verticalGesture && dragStartX >= size.width * 0.67f -> {
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
        .clickable(enabled = !isLoading, interactionSource = surfaceInteractionSource, indication = null) {
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
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
          Text(
            "${formatPlaybackSpeed(playbackSpeed * session.holdToSpeedMultiplier)}x speed",
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
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          Icon(
            if (delta < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
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
        color = Color(0xD9161A23),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          Icon(
            when (adjustmentKind) {
              PlayerAdjustmentKind.Brightness -> Icons.Rounded.Brightness6
              PlayerAdjustmentKind.Volume -> if (adjustmentLevel <= 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp
              null -> Icons.Rounded.VolumeUp
            },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
          )
          Text(
            "${if (adjustmentKind == PlayerAdjustmentKind.Brightness) "Brightness" else "Volume"} ${(adjustmentLevel * 100f).toInt()}%",
            color = Color.White,
            fontWeight = FontWeight.Bold,
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
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
          .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
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
          Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
          Text("Hold to unlock", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            onScrobble("pause", currentProgressPercent)
          } else {
            showPausedInfo = false
            onScrobble("start", currentProgressPercent)
          }
          isPaused = !isPaused
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
            "cover" -> "contain"
            "contain" -> "stretch"
            else -> "cover"
          }
        },
        onSpeed = { keepControlsVisible(); activePanel = PlayerPanel.Speed },
        onSubtitles = { keepControlsVisible(); activePanel = PlayerPanel.Subtitles },
        onAudio = { keepControlsVisible(); activePanel = PlayerPanel.Audio },
        onSources = { keepControlsVisible(); activePanel = PlayerPanel.Sources },
        onEngine = { keepControlsVisible(); activePanel = PlayerPanel.Engine },
        onInfo = { keepControlsVisible(); activePanel = PlayerPanel.Info },
      )
    }

    when (activePanel) {
      PlayerPanel.Audio -> PlayerModalPanel(title = "Audio", onClose = { activePanel = PlayerPanel.None }, compact = true) {
        if (audioTracks.isEmpty()) {
          PlayerOptionRow("Default audio", selected = true, onClick = {})
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
      }
      PlayerPanel.Subtitles -> PlayerModalPanel(title = "Subtitles", onClose = { activePanel = PlayerPanel.None }) {
        Row(
          modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.06f)).padding(6.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          SubtitlePanelTab.entries.forEach { tab ->
            val selected = subtitleTab == tab
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) Color.White else Color.Transparent)
                .clickable {
                  subtitleTab = tab
                  // Style is a set of controls, not a place subtitles come from, so it is not
                  // remembered as the picker's landing tab.
                  if (tab != SubtitlePanelTab.Style) onSubtitleSourceChange(tab.name)
                }
                .padding(vertical = 11.dp),
              contentAlignment = Alignment.Center,
            ) { Text(if (tab == SubtitlePanelTab.BuiltIn) "Built-in" else tab.name, color = if (selected) Color.Black else Color.White.copy(alpha = 0.72f), fontWeight = FontWeight.Bold) }
          }
        }
        when (subtitleTab) {
          SubtitlePanelTab.BuiltIn -> {
            PlayerOptionRow("None", selected = selectedSubtitleTrackId == null && selectedExternalSubtitleId == null) {
              subtitleDisabledByUser = true
              userPickedSubtitle = true
              selectedSubtitleTrackId = null
              selectedExternalSubtitleId = null
              preferredSubtitleTrackKey = null
              activeDisableSubtitleTrack()
            }
            if (subtitleTracks.isEmpty()) Text("No embedded subtitle tracks.", color = Color.White.copy(alpha = 0.64f))
            subtitleTracks.forEach { track ->
              PlayerOptionRow(trackLabel(track), selected = selectedSubtitleTrackId == track.id) {
                subtitleDisabledByUser = false
                userPickedSubtitle = true
                selectedExternalSubtitleId = null
                selectedSubtitleTrackId = track.id
                preferredSubtitleTrackKey = trackPreferenceKey(track)
                activeSetSubtitleTrack(track.id)
              }
            }
          }
          SubtitlePanelTab.Addons -> {
            if (subtitlesLoading) Text("Searching subtitle addons...", color = Color.White.copy(alpha = 0.72f))
            if (!subtitlesLoading && externalSubtitles.isEmpty()) Text("No matching addon subtitles found.", color = Color.White.copy(alpha = 0.64f))
            externalSubtitles.forEach { subtitle ->
              PlayerOptionRow(subtitle.label, selected = selectedExternalSubtitleId == subtitle.id) {
                subtitleDisabledByUser = false
                userPickedSubtitle = true
                selectedSubtitleTrackId = null
                selectedExternalSubtitleId = subtitle.id
                subtitleErrorMessage = null
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
                    if (selectedExternalSubtitleId != subtitle.id) return@launch
                    val localPath = downloadSubtitleToCache(playerContext, candidate.url) ?: continue
                    if (selectedExternalSubtitleId != subtitle.id) return@launch
                    activeAddSubtitle(localPath, candidate.language)
                    applied = true
                    if (candidate.id != subtitle.id) {
                      Log.i("StreamDekSubtitles", "fell through to " + candidate.label)
                    }
                    break
                  }
                  if (!applied && selectedExternalSubtitleId == subtitle.id) {
                    selectedExternalSubtitleId = null
                    subtitleErrorMessage =
                      "That subtitle could not be downloaded, and neither could the others in " +
                        (trackLanguageName(subtitle.language) ?: "that language") + ". Try another source."
                  }
                }
              }
            }
            subtitleErrorMessage?.let {
              Text(it, color = Color(0xFFF08A8A), style = MaterialTheme.typography.bodyMedium)
            }
          }
          SubtitlePanelTab.Style -> {
            // Applied live as the slider moves, saved when it is let go: writing to preferences on
            // every frame of a drag would be dozens of commits for one adjustment.
            Text("Subtitle size: $subtitleSize", color = Color.White.copy(alpha = 0.72f))
            Slider(
              value = subtitleSize.toFloat(),
              valueRange = 28f..84f,
              onValueChange = {
                subtitleSize = it.toInt()
                activeSetSubtitleFontSize(subtitleSize)
              },
              onValueChangeFinished = { onSubtitleTextSizeChange(subtitleSize) },
            )
            Text("Position: $subtitlePosition", color = Color.White.copy(alpha = 0.72f))
            Slider(
              value = subtitlePosition.toFloat(),
              valueRange = 50f..110f,
              onValueChange = {
                subtitlePosition = it.toInt()
                activeSetSubtitlePosition(subtitlePosition)
              },
              onValueChangeFinished = { onSubtitleVerticalOffsetChange(subtitlePosition) },
            )
            Text("Colour, outline and background are in Settings > Subtitles.", color = Color.White.copy(alpha = 0.52f), fontSize = 11.5.sp)
            Text("Delay: ${"%.1f".format(subtitleDelay)}s", color = Color.White.copy(alpha = 0.72f))
            Slider(value = subtitleDelay, valueRange = -5f..5f, onValueChange = {
              subtitleDelay = it
              activeSetSubtitleDelay(it.toDouble())
            })
          }
        }
      }
      PlayerPanel.Sources -> PlayerModalPanel(title = "Sources", onClose = { activePanel = PlayerPanel.None }, trailing = {
        TextButton(onClick = onReloadStreams, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Reload") }
      }) {
        // The source being played is hoisted to the top, with everything else keeping its order.
        // Hoisting before the cap also guarantees it is listed at all — a source further down a
        // long list would otherwise be cut off by take(), leaving nothing marked as playing.
        val orderedStreams = remember(availableStreams, session.currentStream) {
          val distinct = availableStreams.distinctBy(::addonStreamPlaybackIdentity)
          val current = session.currentStream
          val ordered = if (current == null) {
            distinct
          } else {
            val (playing, rest) = distinct.partition { streamsRepresentSameSource(it, current) }
            playing + rest
          }
          ordered.take(MAX_PLAYER_SOURCE_ROWS)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
          orderedStreams.forEach { stream ->
              PlayerSourceCard(
                stream = stream,
                active = streamsRepresentSameSource(stream, session.currentStream),
                onClick = {
                  activePanel = PlayerPanel.None
                  onSelectStream(stream, currentProgressPercent)
                },
                showDownload = downloadsEnabled && !session.isLive && stream.infoHash.isNullOrBlank() && !stream.url.isNullOrBlank(),
                onDownload = { onDownloadStream(stream) },
              )
            }
          if (availableStreams.isEmpty()) {
            Text("No loaded sources yet.", color = Color.White.copy(alpha = 0.66f))
          }
        }
      }
      PlayerPanel.Engine -> PlayerModalPanel(title = "Player Engine", onClose = { activePanel = PlayerPanel.None }, compact = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          PlayerOptionRow("ExoPlayer", selected = activeEngine == ActivePlaybackEngine.Media3) {
            switchEngine(ActivePlaybackEngine.Media3, "manual switch")
            activePanel = PlayerPanel.None
          }
          PlayerOptionRow("mpv", selected = activeEngine == ActivePlaybackEngine.MPV) {
            switchEngine(ActivePlaybackEngine.MPV, "manual switch")
            activePanel = PlayerPanel.None
          }
          Text(
            "Switching keeps your playback position. If a stream plays with no sound or a black screen, try the other engine.",
            color = Color.White.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
      PlayerPanel.Speed -> PlayerModalPanel(title = "Speed", onClose = { activePanel = PlayerPanel.None }, compact = true) {
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
      PlayerPanel.Info -> PlayerModalPanel(title = "Stream info", onClose = { activePanel = PlayerPanel.None }) {
        PlayerStreamInfo(
          session = session,
          stats = playbackStats,
          engine = activeEngine,
          duration = duration,
        )
      }
      PlayerPanel.None -> Unit
    }
    AnimatedVisibility(
      visible = session.isLive && !isLoading && !controlsLocked && showChannelSwipeCue && !showControls && activePanel == PlayerPanel.None && !showLiveChannels && !showFavouriteDrawer,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).zIndex(18f),
      enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(280)),
      exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 3 }, animationSpec = tween(220)),
    ) { LiveChannelSwipeCue() }

    AnimatedVisibility(
      visible = session.isLive && !isLoading && !controlsLocked && !showFavouriteDrawer,
      modifier = Modifier.align(Alignment.CenterEnd).zIndex(19f),
      enter = fadeIn(tween(180)) + slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(260)),
      exit = fadeOut(tween(160)) + slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)),
    ) {
      Surface(
        modifier = Modifier.width(36.dp).height(92.dp).clickable { showLiveChannels = false; showFavouriteDrawer = true },
        color = Color(0xB3151820),
        shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
      ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Open favourites", tint = Color.White, modifier = Modifier.size(28.dp)) } }
    }

    AnimatedVisibility(
      visible = session.isLive && showLiveChannels,
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(24f),
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
      modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.275f).fillMaxHeight().zIndex(26f),
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
    if (session.isLive && hasLoaded && !isLoading && error.isNullOrBlank() && !showLiveChannels && !showFavouriteDrawer) {
      Surface(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp).zIndex(12f),
        color = if (session.isVod) Color(0xFF2563EB) else Color(0xFFE11D48),
        shape = CircleShape,
      ) {
        Row(modifier = Modifier.padding(horizontal = 13.5.dp, vertical = 6.3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          if (session.isVod) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
          } else {
            Box(modifier = Modifier.size(6.3.dp).clip(CircleShape).background(Color.White))
          }
          Text(if (session.isVod) "VOD" else "LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.8.sp, letterSpacing = 0.9.sp)
        }
      }
    }
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
  playbackSpeed: Float,
  subtitleDelay: Float,
  subtitleSize: Int,
  subtitlePosition: Int,
  subtitleColor: String,
  onLoad: (Double, Int, Int) -> Unit,
  onProgress: (Double, Double) -> Unit,
  onError: (String) -> Unit,
  onEnd: () -> Unit,
  onStallChanged: (Boolean) -> Unit,
  onTracksChanged: (List<MpvTrackInfo>, List<MpvTrackInfo>, Int?, Int?) -> Unit,
  onExoViewCreated: (ExoPlaybackView) -> Unit,
  onMpvViewCreated: (MPVView) -> Unit,
) {
  key(engineKey, activeEngine) {
    if (activeEngine == ActivePlaybackEngine.Media3) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          ExoPlaybackView(context).apply {
            onExoViewCreated(this)
            onLoadCallback = onLoad
            onProgressCallback = onProgress
            onErrorCallback = onError
            onEndCallback = onEnd
            onStallChangedCallback = onStallChanged
            onTracksChangedCallback = onTracksChanged
            setResizeMode(resizeMode)
            setSpeed(playbackSpeed.toDouble())
            setSubtitleDelay(subtitleDelay.toDouble())
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
          view.onEndCallback = onEnd
          view.onStallChangedCallback = onStallChanged
          view.onTracksChangedCallback = onTracksChanged
          view.setHeaders(session.requestHeaders)
          view.setDrmClearKeys(session.drmLicenseType, session.drmClearKeys)
          view.setPreferredAudioLanguage(session.preferredAudioLanguage)
          view.setSecondaryAudioLanguage(session.secondaryAudioLanguage)
          view.setSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage, session.useForcedSubtitles)
          view.setSource(session.url)
          view.setPaused(isPaused)
          view.setResizeMode(resizeMode)
          view.setSpeed(playbackSpeed.toDouble())
          view.setSubtitleDelay(subtitleDelay.toDouble())
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
            onLoadCallback = onLoad
            onProgressCallback = onProgress
            onErrorCallback = onError
            onEndCallback = onEnd
            onStallChangedCallback = onStallChanged
            onTracksChangedCallback = onTracksChanged
            setResizeMode(resizeMode)
            setDecoderMode(session.decoderMode)
            setRenderSurface(session.renderSurface)
            setSpeed(playbackSpeed.toDouble())
            setSubtitleDelay(subtitleDelay.toDouble())
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
          view.setResizeMode(resizeMode)
          view.setDecoderMode(session.decoderMode)
          view.setRenderSurface(session.renderSurface)
          view.setSpeed(playbackSpeed.toDouble())
          view.setSubtitleDelay(subtitleDelay.toDouble())
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
      "Swipe up in the middle for all channels", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
      style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow),
    )
    Icon(
      Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color.White.copy(alpha = 0.92f),
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
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        if (loading) "Loading all channels…" else "All channels", color = Color.White,
        fontWeight = FontWeight.Bold, fontSize = 14.sp,
        style = MaterialTheme.typography.titleSmall.copy(
          shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 12f),
        ),
      )
      Text("Swipe down to close", color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
    }
    if (channels.isEmpty()) {
      Box(modifier = Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        else Text("No channels were returned by this add-on.", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
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
            modifier = Modifier.width(160.dp).height(90.dp).clip(RoundedCornerShape(10.dp))
              .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
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
              "NOW PLAYING", modifier = Modifier.align(Alignment.TopStart).padding(7.dp), color = Color.White,
              fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp,
            )
            // Same filled yellow star as the player header, in the corner the NOW PLAYING marker
            // does not use, so a channel that is both still shows both.
            if (favourite) Box(
              modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(Icons.Rounded.Star, contentDescription = "In favourites", tint = Color(0xFFFACC15), modifier = Modifier.size(13.dp))
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
      icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
      title = { Text("Clear all favourites?") },
      text = { Text("This removes all ${favourites.size} channels from your Live TV favourites.") },
      confirmButton = { Button(onClick = { showClearConfirm = false; onClearAll() }) { Text("Clear all") } },
      dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } },
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
        IconButton(onClick = onClose) { Icon(Icons.Rounded.ChevronRight, contentDescription = "Close favourites", tint = Color.White) }
        Column(modifier = Modifier.weight(1f)) {
          Text("Favourites", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Text("${favourites.size} channels", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
        }
        IconButton(onClick = { onToggleCardView(!cardView) }, modifier = Modifier.size(32.dp)) {
          Icon(
            if (cardView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
            contentDescription = if (cardView) "Switch to text list" else "Switch to card view",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
          )
        }
        IconButton(onClick = { showClearConfirm = true }, enabled = favourites.isNotEmpty(), modifier = Modifier.size(32.dp)) {
          Icon(
            Icons.Rounded.DeleteSweep, contentDescription = "Clear all favourites",
            tint = Color.White.copy(alpha = if (favourites.isNotEmpty()) 0.85f else 0.3f),
            modifier = Modifier.size(18.dp),
          )
        }
      }
      if (favourites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("Favourite channels appear here.", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(if (cardView) 10.dp else 2.dp)) {
          items(favourites, key = { "${it.type}-${it.id}" }) { channel ->
            val selected = channel.id == currentChannelId
            if (cardView) {
              Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f)).clickable { onSelect(channel) }.padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                AsyncImage(model = channel.backdrop ?: channel.poster, contentDescription = channel.title, modifier = Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Text(channel.title, color = Color.White, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            } else {
              // The drawer is anchored to the right edge of the screen, so the text list reads
              // right-aligned: channel names end on a straight edge against that side and the
              // now-playing dot sits in a fixed column beyond them.
              Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(if (selected) Color.White.copy(alpha = 0.13f) else Color.Transparent).clickable { onSelect(channel) }.padding(horizontal = 10.dp, vertical = 10.dp),
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

@Composable
private fun PlayerLoadingBackdrop(session: PlayerSession, message: String) {
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
    }
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
private fun PlayerCenterControls(isPaused: Boolean, onPauseToggle: () -> Unit, onSeek: (Double) -> Unit, showEpisodeNavigation: Boolean, onPreviousEpisode: () -> Unit, onNextEpisode: () -> Unit, showSeeking: Boolean = true) {
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
      if (showSeeking) PlayerRoundAction(icon = Icons.Rounded.Replay10, onClick = { onSeek(-10.0) }) else Spacer(modifier = Modifier.size(74.dp))
      Box(
        modifier = Modifier
          .size(90.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.14f))
          .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
          .clickable(onClick = onPauseToggle),
        contentAlignment = Alignment.Center,
      ) {
        Icon(if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
      }
      if (showSeeking) PlayerRoundAction(icon = Icons.Rounded.Forward10, onClick = { onSeek(10.0) }) else Spacer(modifier = Modifier.size(74.dp))
    }
  }
}

@Composable
private fun PlayerRoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .size(74.dp)
      .clip(CircleShape)
      .background(Color.White.copy(alpha = 0.08f))
      .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(34.dp))
  }
}

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
) {
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
        if (liveWithoutWindow) {
          Box(modifier = Modifier.weight(1f).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
          }
        } else {
          Slider(value = progress, onValueChange = onProgressChange, onValueChangeFinished = onProgressFinished, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
        }
        Box(
          modifier = Modifier.width(96.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.44f)).padding(vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          if (liveWithoutWindow) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              if (isVod) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(12.dp))
              } else {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE11D48)))
              }
              Text(if (isVod) "VOD" else "LIVE", color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            }
          } else {
            Text(formatClock(duration), color = Color.White.copy(alpha = 0.92f))
          }
        }
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(30.dp))
          .background(Color(0xD9161A23))
          .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
          .padding(horizontal = 18.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(15.5.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PlayerDockButton("Zoom", Icons.Rounded.SettingsOverscan, onZoom)
        if (isLive) {
          PlayerDockButton(
            "Progress",
            if (showLiveProgress) Icons.Rounded.Timeline else Icons.Rounded.HideSource,
            onToggleLiveProgress,
            active = showLiveProgress,
          )
        } else {
          PlayerDockButton("Speed", Icons.Rounded.SlowMotionVideo, onSpeed)
          PlayerDockButton("Subs", Icons.Rounded.Subtitles, onSubtitles)
          PlayerDockButton("Audio", Icons.Rounded.VolumeUp, onAudio)
        }
        PlayerDockButton("Sources", Icons.Rounded.GridView, onSources)
        PlayerDockButton("Engine", Icons.Rounded.Tune, onEngine)
        // Lock moved up beside the handoff control in the header, so the dock has room for this
        // without growing wide enough to wrap on a phone.
        PlayerDockButton("Info", Icons.Rounded.Info, onInfo)
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
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.clickable(onClick = onClick),
  ) {
    Icon(icon, contentDescription = label, tint = if (active) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.92f), modifier = Modifier.size(21.dp))
    Text(label, color = if (active) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.78f), fontSize = 11.sp, maxLines = 1)
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
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.38f), Color.Transparent)))
      .padding(horizontal = 18.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.10f))
        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
        .clickable(onClick = onBack),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
    }
    Column(modifier = Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      val yearLabel = session.year?.toString().orEmpty()
      val titleLine = listOfNotNull(session.title, episodeContext(session), yearLabel.takeIf { it.isNotBlank() }).joinToString(" | ")
      Text(text = titleLine, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    // Lock sits beside handoff rather than in the bottom dock, which is where the info control
    // now is. Both are one-tap actions on the session rather than settings, so they belong to the
    // same group.
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.10f))
        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
        .clickable(onClick = onLock),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Rounded.LockOpen, contentDescription = "Lock the controls", tint = Color.White)
    }
    // Live streams get the same handoff control as VOD, and it sits to the left of the
    // favourites star because it is declared first in this Row.
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.10f))
        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
        .clickable(onClick = onHandoff),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Rounded.Tv, contentDescription = "Hand off to TV", tint = Color.White)
    }
    if (session.isLive) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.10f))
          .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
          .clickable(onClick = onToggleFavourite),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          if (isFavourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
          contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
          tint = if (isFavourite) Color(0xFFFACC15) else Color.White,
        )
      }
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
  val shape: Shape = RoundedCornerShape(24.dp)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(if (active) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f))
      .border(1.dp, if (active) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f), shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(header, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
      // Beside the name, as on the television. Switching source mid-film is usually a choice
      // between things that have already disappointed you once, and "which of these is even the
      // same kind of source" was not answerable from a list of names.
      streamOriginLabel(stream)?.let {
        Text(
          it,
          color = Color.White.copy(alpha = 0.52f),
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.weight(1f))
      if (showDownload) {
        IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
          Icon(Icons.Rounded.Download, contentDescription = "Download for offline playback", tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(18.dp))
        }
      }
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
        StreamInfoPill(icon = Icons.Rounded.Sensors, label = "[$it]", containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), contentColor = MaterialTheme.colorScheme.primary)
      }
      if (active) {
        StreamInfoPill(
          icon = Icons.Rounded.Tune,
          label = "Playing",
          containerColor = Color(0xFF22C55E).copy(alpha = 0.20f),
          contentColor = Color(0xFF22C55E),
        )
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
    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(containerColor).padding(horizontal = 10.dp, vertical = 6.dp),
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
        .clip(RoundedCornerShape(28.dp))
        .background(Color(0xEE111722))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
        .clickable(onClick = {})
        .padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          trailing?.invoke()
          Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White), shape = RoundedCornerShape(20.dp)) { Text("Close") }
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
    streamProviderLabel(stream, session.sourceLabel)?.let { add("Provider" to it) }
    // The television's wording, and its distinction: the provider is who served this, and this is
    // what they are on this account — an add-on, a plugin out of a named collection, or a file
    // already here. Two providers with the same name can be different things entirely.
    streamOriginLabel(stream)?.let { add("Installed as" to it) }
    add("Delivery" to transport.label)
    session.sizeLabel?.takeIf { it.isNotBlank() }?.let { add("Size" to it) }
    session.qualityLabel?.takeIf { it.isNotBlank() }?.let { add("Quality" to it) }
    stream?.filename?.takeIf { it.isNotBlank() }?.let { add("File" to it) }
    if (session.isLive) add("Route" to if (session.isProxied) "Proxied" else "Direct")
  }
  val playbackRows = buildList {
    formatTransferRate(stats?.bytesPerSecond)?.let { add("Speed" to it) }
    formatResolution(stats?.width ?: 0, stats?.height ?: 0)?.let { add("Resolution" to it) }
    val videoLine = listOfNotNull(
      prettyCodecName(stats?.videoCodec),
      formatBitrate(stats?.videoBitrateBps),
      stats?.frameRate?.let { String.format(Locale.US, "%.0f fps", it) },
    ).joinToString(" · ")
    if (videoLine.isNotBlank()) add("Video" to videoLine)
    val audioLine = listOfNotNull(
      prettyCodecName(stats?.audioCodec),
      stats?.audioChannels?.let { channels -> if (channels > 2) "${channels}ch" else if (channels == 2) "Stereo" else "Mono" },
    ).joinToString(" · ")
    if (audioLine.isNotBlank()) add("Audio" to audioLine)
    stats?.bufferedSeconds?.let { add("Buffered" to String.format(Locale.US, "%.0f s ahead", it)) }
    stats?.hardwareDecoder?.let { add("Decoder" to it) }
    add("Engine" to if (engine == ActivePlaybackEngine.Media3) "ExoPlayer" else "mpv")
    if (duration > 0.0) add("Runtime" to formatClock(duration))
  }
  Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
    PlayerInfoSection("Source", sourceRows)
    PlayerInfoSection("Playback", playbackRows)
    if (stats == null) {
      Text(
        "Reading playback details from the engine…",
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
        .clip(RoundedCornerShape(18.dp))
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
private fun PlayerOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) Color.White else Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = if (selected) Color.Black else Color.White, style = MaterialTheme.typography.titleMedium)
    if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(22.dp))
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
internal fun formatPlaybackSpeed(speed: Float): String {
  val clamped = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
  return if (clamped % 1f == 0f) clamped.toInt().toString() else "%.2f".format(clamped).trimEnd('0').trimEnd('.')
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
  // Two settings can narrow this list: the viewer's "only my languages" switch, and asking add-ons
  // for their preferred languages only. Either one is reason enough to filter.
  val narrowToPreferred = session.showOnlyPreferredSubtitleLanguages ||
    session.addonSubtitleLoading == "preferred"
  if (!narrowToPreferred) return this
  val preferred = preferredSubtitleLanguages(session.subtitleLanguage, session.secondarySubtitleLanguage)
  if (preferred.isEmpty()) return this
  val matching = filter { Languages.normalize(it.language) in preferred }
  return matching.ifEmpty { this }
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
  }.getOrElse { String(body, Charsets.ISO_8859_1) }
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
      ?.let { return@runCatching it.absolutePath }
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
    val file = File(stem.parentFile, stem.name + "." + subtitleExtensionFor(text))
    file.writeText(text, Charsets.UTF_8)
    Log.i("StreamDekSubtitles", "cached " + file.name + " (" + bytes.size + " bytes) from " + url.substringBefore('?').take(90))
    file.absolutePath
  }.onFailure {
    Log.w("StreamDekSubtitles", "could not cache subtitle " + url.substringBefore('?').take(90), it)
  }.getOrNull()
}

private suspend fun fetchExternalSubtitles(session: PlayerSession, userSources: List<UserSubtitleSource>): List<ExternalSubtitle> = withContext(Dispatchers.IO) {
  // "Off" means do not ask. Worth honouring before any request goes out rather than fetching and
  // discarding: each source is a network call on the way into playback.
  if (session.addonSubtitleLoading == "off") {
    Log.i("StreamDekSubtitles", "add-on subtitles are switched off in settings")
    return@withContext emptyList()
  }
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
  val sources = buildList {
    add(UserSubtitleSource("opensubtitles", "OpenSubtitles", "https://opensubtitles-v3.strem.io"))
    addAll(userSources)
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
            val language = normalizeSubtitleLanguage(item.optString("lang"))
            if (id.isBlank() || url.isBlank() || language.isBlank()) continue
            val release = item.optString("m").trim()
            // Named, not coded. "FR - <release> - OpenSubtitles" asks a viewer to know that FR is
            // French before they can pick their own language out of eighty rows; the add-on's
            // two-letter tag is what this list is sorted by, not what it should be read by.
            val label = listOf(trackLanguageName(language).orEmpty(), release, source.name).filter { it.isNotBlank() }.joinToString(" - ")
            add(ExternalSubtitle("${source.id}:$id", language, label, url))
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
private fun introdbApiKeyFor(session: PlayerSession): String =
  session.introdbApiKey.trim().ifBlank { BuildConfig.INTRODB_API_KEY.trim() }

private fun introdbRequest(url: String, apiKey: String): Request = Request.Builder()
  .url(url)
  .header("Accept", "application/json")
  .apply { if (apiKey.isNotBlank()) header("X-API-Key", apiKey) }
  .build()

private suspend fun fetchSkipSegments(session: PlayerSession): List<SkipSegment> = withContext(Dispatchers.IO) {
  val imdbId = session.imdbId?.takeIf { it.startsWith("tt") } ?: return@withContext emptyList()
  val season = session.seasonNumber ?: return@withContext emptyList()
  val episode = session.episodeNumber ?: return@withContext emptyList()
  val apiKey = introdbApiKeyFor(session)
  runCatching {
    val request = introdbRequest("https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=$episode", apiKey)
    val segments = playerHttpClient.newCall(request).execute().use { response -> if (response.isSuccessful) extractSkipSegments(response.body?.string().orEmpty()) else emptyList() }.toMutableList()
    if (segments.none { it.type == "intro" }) {
      val legacy = introdbRequest("https://api.introdb.app/intro?imdb=$imdbId&imdb_id=$imdbId&season=$season&episode=$episode", apiKey)
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
    segments.distinctBy { Triple(it.type, it.startSeconds, it.endSeconds) }.sortedBy { it.startSeconds }
  }.getOrDefault(emptyList())
}
private fun streamsRepresentSameSource(candidate: AddonStream, current: AddonStream?): Boolean {
  current ?: return false
  return addonStreamPlaybackIdentity(candidate) == addonStreamPlaybackIdentity(current)
}
