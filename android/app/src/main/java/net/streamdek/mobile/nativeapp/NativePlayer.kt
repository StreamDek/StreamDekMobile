package net.streamdek.mobile.nativeapp

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.blur
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
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import kotlin.math.roundToInt
import android.widget.Toast

private enum class PlayerPanel { None, Sources, Audio, Subtitles, Speed, Engine, Info }
private enum class PlayerAdjustmentKind { Brightness, Volume }

/** A slightly softened play mark; the stock triangle has visibly sharp corners at player scale. */
private val RoundedPlayerPlayIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "RoundedPlayerPlay",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
      moveTo(7.3f, 5.25f)
      curveTo(7.3f, 4.16f, 8.51f, 3.49f, 9.43f, 4.07f)
      lineTo(19.17f, 10.33f)
      curveTo(20.4f, 11.12f, 20.4f, 12.88f, 19.17f, 13.67f)
      lineTo(9.43f, 19.93f)
      curveTo(8.51f, 20.51f, 7.3f, 19.84f, 7.3f, 18.75f)
      close()
    }
  }.build()
}

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

internal fun playerResumePosition(durationSec: Double, exactPositionSec: Double, percent: Double): Double {
  if (durationSec <= 0.0) return 0.0
  val requested = exactPositionSec.takeIf { it > 0.0 } ?: (durationSec * (percent / 100.0))
  return requested.coerceIn(0.0, (durationSec - 5.0).coerceAtLeast(0.0))
}

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
private enum class SubtitlePanelTab { All, BuiltIn, Addons, Style }
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
  nextEpisodeLoading: Boolean = false,
  nextEpisodeLoadingLabel: String? = null,
  onPreviousEpisode: () -> Unit = {},
  onNextEpisode: () -> Unit = {},
  onNextEpisodeAtEnding: () -> Unit = onNextEpisode,
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
) {
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
  var error by source.error
  var hasLoaded by playback.hasLoaded
  var playerView by remember(liveEngineKey) { mutableStateOf<MPVView?>(null) }
  var exoPlayerView by remember(liveEngineKey) { mutableStateOf<ExoPlaybackView?>(null) }
  var activeEngine by remember(liveEngineKey, session.playerEngine) { mutableStateOf(initialPlaybackEngine(session.playerEngine)) }
  var autoFallbackUsed by remember(liveEngineKey, session.playerEngine) { mutableStateOf(false) }
  var pendingEngineResumeSeconds by source.pendingEngineResumeSeconds
  // Start every source in the edge-to-edge Full screen scale. The viewer can still cycle to
  // Stretch or Normal afterwards, but a new video must never begin letterboxed or distorted.
  val resizeModeState = rememberSaveable(session.url) { mutableStateOf("cover") }
  var resizeMode by resizeModeState
  val customZoomState = rememberSaveable(session.url) { mutableFloatStateOf(1f) }
  var customZoom by customZoomState
  val playbackSpeedState = rememberSaveable(session.url) { mutableFloatStateOf(1f) }
  var playbackSpeed by playbackSpeedState
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
      "BuiltIn" -> listOf(SubtitlePanelTab.BuiltIn, SubtitlePanelTab.Style)
      "Addons" -> listOf(SubtitlePanelTab.Addons, SubtitlePanelTab.Style)
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
  var recommendationDismissed by remember(playbackIdentity) { mutableStateOf(false) }
  var recommendationVisible by remember(playbackIdentity) { mutableStateOf(false) }
  var queuedRecommendation by remember(playbackIdentity) { mutableStateOf<MediaItem?>(null) }
  var queuedNextEpisode by remember(playbackIdentity) { mutableStateOf(false) }
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
  )
  val nextEpisodeActionAvailable = activeSkipSegment?.type == "outro" && session.mediaType == "tv" && session.autoPlayNextEpisode &&
    AdaptiveEndOfPlaybackTrigger.isReached(currentTime, meaningfulEnd)

  LaunchedEffect(currentTime, duration, meaningfulEnd, session.endOfPlaybackRecommendationsEnabled, session.recommendations) {
    if (!session.endOfPlaybackRecommendationsEnabled || session.isLive || recommendationDismissed || isLoading) return@LaunchedEffect
    if (AdaptiveEndOfPlaybackTrigger.isReached(currentTime, meaningfulEnd) && (session.mediaType == "tv" || session.recommendations.isNotEmpty())) {
      recommendationVisible = true
      showControls = false
    }
  }

  fun closePlayer() = onBack(progressPercent())
  fun finishPlayback() {
    if (completionDispatched) return
    completionDispatched = true
    if (session.isLive) return
    isPaused = true
    onScrobble("stop", 100.0)
    if (queuedNextEpisode) {
      recommendationVisible = false
      onNextEpisodeAtEnding()
      return
    }
    queuedRecommendation?.let {
      recommendationVisible = false
      onRecommendedPlaybackEnded(it)
      return
    }
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
  val playerStallCallback: (Boolean) -> Unit = { stalled ->
    if (session.isLive) {
      liveStalled = stalled
    } else if (stalled) {
      val now = android.os.SystemClock.elapsedRealtime()
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
        normalizeSubtitleLanguage(it.language ?: it.title) in allowedLanguages
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
      customZoom = customZoom,
      playbackSpeed = playbackSpeed,
      subtitleDelay = subtitleDelay,
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

    PlayerSurfaceOverlays(
      session = session,
      source = source,
      playback = playback,
      isLoading = isLoading,
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
      recommendationVisible = recommendationVisible,
      recommendations = session.recommendations.takeUnless { session.mediaType == "tv" }.orEmpty(),
      showNextEpisodeRecommendation = session.mediaType == "tv",
      queuedRecommendationId = queuedRecommendation?.id,
      nextEpisodeQueued = queuedNextEpisode,
      currentTitle = session.title,
      onQueueRecommendation = { item ->
        queuedRecommendation = item
        queuedNextEpisode = false
      },
      onQueueNextEpisode = {
        queuedNextEpisode = true
        queuedRecommendation = null
      },
      onDismissRecommendation = {
        recommendationVisible = false
        recommendationDismissed = true
        queuedRecommendation = null
        queuedNextEpisode = false
      },
      onRecommendationTimeout = {
        recommendationVisible = false
        recommendationDismissed = true
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

    PlayerOverlays(
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
    )

    PlayerPanels(
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
) {
  key(engineKey, activeEngine) {
    if (activeEngine == ActivePlaybackEngine.Media3) {
      AndroidView(
        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = customZoom; scaleY = customZoom },
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
          view.onExternalSubtitleErrorCallback = onSubtitleError
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
          view.setResizeMode(if (resizeMode == "custom") "contain" else resizeMode)
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
        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = customZoom; scaleY = customZoom },
        factory = { context ->
          MPVView(context).apply {
            onMpvViewCreated(this)
            onLoadCallback = onLoad
            onProgressCallback = onProgress
            onErrorCallback = onError
            onEndCallback = onEnd
            onStallChangedCallback = onStallChanged
            onTracksChangedCallback = onTracksChanged
            setResizeMode(if (resizeMode == "custom") "contain" else resizeMode)
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
          view.setResizeMode(if (resizeMode == "custom") "contain" else resizeMode)
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
        swarm == null -> if (peerHash.isNullOrBlank()) "Preparing stream..." else "Preparing peer stream..."
        !swarm.hasMetadata -> "Finding peers..."
        else -> "Buffering from peers..."
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
        .size(if (minimal) 48.dp else 44.dp)
        .clip(CircleShape)
        .then(if (minimal) Modifier else Modifier.background(Color.White.copy(alpha = 0.10f)).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape))
        .clickable(onClick = onBack),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(if (minimal) 30.dp else 24.dp))
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
          PlayerSwarmStat("PEERS", stats.peers.toString())
          PlayerSwarmStat("SPEED", playerRateLabel(stats.downloadRateBytesPerSecond.toLong()))
        }
      }
      sourceLabel?.takeIf { it.isNotBlank() }?.let {
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
      val minimal = layout == "Minimal"
      if (showSeeking) PlayerRoundAction(icon = Icons.Rounded.Replay10, label = "Rewind 10 seconds", minimal = minimal, onClick = { onSeek(-10.0) }) else Spacer(modifier = Modifier.size(74.dp))
      Box(
        modifier = Modifier
          .size(90.dp)
          .clip(CircleShape)
          .clickable(onClick = onPauseToggle),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier.size(if (minimal) 90.dp else 81.dp).clip(CircleShape)
            .then(if (minimal) Modifier else Modifier.background(Color.White.copy(alpha = 0.14f)).border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(if (isPaused) RoundedPlayerPlayIcon else Icons.Rounded.Pause, contentDescription = if (isPaused) "Play" else "Pause", tint = Color.White, modifier = Modifier.size(if (isPaused) 51.dp else 54.dp))
        }
      }
      if (showSeeking) PlayerRoundAction(icon = Icons.Rounded.Forward10, label = "Forward 10 seconds", minimal = minimal, onClick = { onSeek(10.0) }) else Spacer(modifier = Modifier.size(74.dp))
    }
  }
}

@Composable
private fun PlayerRoundAction(icon: ImageVector, label: String, minimal: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier.size(74.dp).clip(CircleShape).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier.size(if (minimal) 74.dp else 67.dp).clip(CircleShape)
        .then(if (minimal) Modifier else Modifier.background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.90f), modifier = Modifier.size(34.dp))
    }
  }
}

@Composable
private fun RefinedPlayerSlider(
  progress: Float,
  onProgressChange: (Float) -> Unit,
  onProgressFinished: () -> Unit,
  modifier: Modifier = Modifier,
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
        drawLine(trackColor, Offset(inset, centerY), Offset(inset + trackWidth, centerY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(activeColor, Offset(inset, centerY), Offset(thumbX, centerY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(activeColor, radius = 4.5.dp.toPx(), center = Offset(thumbX, centerY))
      },
  )
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
  showLabels: Boolean,
  layout: String,
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
            if (layout == "Minimal") {
              RefinedPlayerSlider(
                progress = progress,
                onProgressChange = onProgressChange,
                onProgressFinished = onProgressFinished,
                modifier = Modifier.weight(0.85f),
              )
            } else {
              // Normal and Compact retain the original Material player progress bar. The
              // surrounding 7.5% spacers shorten it by 15% while keeping it exactly centred.
              Slider(
                value = progress,
                onValueChange = onProgressChange,
                onValueChangeFinished = onProgressFinished,
                modifier = Modifier.weight(0.85f).graphicsLayer { scaleY = if (layout == "Compact") 0.92f else 1f },
              )
            }
          }
        }
        Spacer(Modifier.weight(0.075f))
        Box(
          modifier = Modifier.width(96.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.44f)).padding(vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          if (liveWithoutWindow) {
            // Scaled by 0.6 to match the badge over the picture. The pill around it keeps its size:
            // it is shared with the duration clock this replaces, and shrinking it here would move
            // the controls row about depending on whether the source happens to be live.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.6.dp)) {
              if (isVod) {
                Icon(RoundedPlayerPlayIcon, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(7.2.dp))
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
            // Normal drops from 66dp to roughly 58dp overall (about 12%) while the controls keep
            // their full 48dp touch targets. Minimal retains its own low-profile treatment.
            vertical = when (layout) { "Compact" -> 5.dp; "Minimal" -> 2.dp; else -> 5.dp },
          ),
        horizontalArrangement = Arrangement.spacedBy(when (layout) { "Compact" -> 32.dp; "Minimal" -> 6.dp; else -> 15.5.dp }),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PlayerDockButton("Zoom", Icons.Rounded.SettingsOverscan, onZoom, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
        if (isLive) {
          PlayerDockButton(
            "Progress",
            if (showLiveProgress) Icons.Rounded.Timeline else Icons.Rounded.HideSource,
            onToggleLiveProgress,
            active = showLiveProgress,
            showLabel = showLabels && !minimal,
            compact = layout == "Compact",
            minimal = minimal,
          )
        } else {
          PlayerDockButton("Speed", Icons.Rounded.SlowMotionVideo, onSpeed, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
          PlayerDockButton("Subs", Icons.Rounded.Subtitles, onSubtitles, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
          PlayerDockButton("Audio", Icons.Rounded.VolumeUp, onAudio, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
        }
        PlayerDockButton("Sources", Icons.Rounded.GridView, onSources, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
        PlayerDockButton("Engine", Icons.Rounded.Tune, onEngine, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
        PlayerDockButton("Info", Icons.Rounded.Info, onInfo, showLabel = showLabels && !minimal, compact = layout == "Compact", minimal = minimal)
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
    horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
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
) {
  var fullTitleVisible by remember(session.title) { mutableStateOf(false) }
  val reducedMotion = LocalReducedMotion.current
  if (fullTitleVisible) {
    AlertDialog(
      onDismissRequest = { fullTitleVisible = false },
      title = { Text("Full title") },
      text = { Text(listOfNotNull(session.title, episodeContext(session), session.year?.toString()).joinToString(" | ")) },
      confirmButton = { TextButton(onClick = { fullTitleVisible = false }) { Text("Close") } },
    )
  }
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.38f), Color.Transparent)))
      .padding(horizontal = 18.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.Top,
  ) {
    val minimal = session.playerControlLayout == "Minimal"
    Box(
      modifier = Modifier.size(if (minimal) 48.dp else 44.dp).clip(CircleShape)
        .then(if (minimal) Modifier else Modifier.background(Color.White.copy(alpha = 0.10f)).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape))
        .clickable(onClick = onBack),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(if (minimal) 30.dp else 24.dp))
    }
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
    if (showDownload) {
      IconButton(onClick = onDownload, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(36.dp)) {
        Icon(Icons.Rounded.Download, contentDescription = "Download for offline playback", tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(18.dp))
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
        .border(1.dp, Color.White.copy(alpha = 0.08f), StreamDekRadius.sheetShape)
        .clickable(onClick = {})
        .padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          trailing?.invoke()
          Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White), shape = StreamDekRadius.cardShape) { Text("Close") }
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
private fun introdbApiKeyFor(session: PlayerSession): String =
  session.introdbApiKey.trim().ifBlank { BuildConfig.INTRODB_API_KEY.trim() }

private fun introdbRequest(url: String, apiKey: String): Request = Request.Builder()
  .url(url)
  .header("Accept", "application/json")
  .apply { if (apiKey.isNotBlank()) header("X-API-Key", apiKey) }
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
      append(BuildConfig.API_BASE_URL.trimEnd('/')).append("/services/timings/theintrodb?tmdb_id=").append(tmdbId)
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
    val apiKey = introdbApiKeyFor(session)
    return runCatching {
    val request = introdbRequest("https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=$episode", apiKey)
    val segments = playerHttpClient.newCall(request).apply { timeout().timeout(4500, java.util.concurrent.TimeUnit.MILLISECONDS) }.execute().use { response -> if (response.isSuccessful) extractSkipSegments(response.body?.string().orEmpty()) else emptyList() }.toMutableList()
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
  return addonStreamPlaybackIdentity(candidate) == addonStreamPlaybackIdentity(current)
}


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
  val hasLoaded = mutableStateOf(false)
  val autoSkippedSegments = mutableStateOf(emptySet<String>())
  val autoSkipNotice = mutableStateOf<String?>(null)
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
  val playbackStats = mutableStateOf<PlaybackStats?>(null)
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
  activeSetSpeed: (Double) -> Unit,
  activeAddSubtitle: (String, String?) -> Unit,
  onSubtitleSourceChange: (String) -> Unit,
  onSubtitleTextSizeChange: (Int) -> Unit,
  onSubtitleVerticalOffsetChange: (Int) -> Unit,
  onSelectStream: (AddonStream, Double) -> Unit,
  onReloadStreams: () -> Unit,
  onDownloadStream: (AddonStream) -> Unit,
  onToggleSourceFavourite: (String) -> Unit,
) {
  // Re-bound so the panel bodies below read and write exactly as they did in the screen.
  var activePanel by activePanelState
  var playbackSpeed by playbackSpeedState
  var subtitleDelay by subtitleDelayState
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
                if (tab != SubtitlePanelTab.Style) onSubtitleSourceChange(tab.name)
              }
              .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              when (tab) {
                SubtitlePanelTab.All -> "All"
                SubtitlePanelTab.BuiltIn -> "Built-in"
                else -> tab.name
              },
              color = if (selected) Color.Black else Color.White.copy(alpha = 0.72f),
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
      when (subtitleTab) {
        SubtitlePanelTab.All, SubtitlePanelTab.BuiltIn, SubtitlePanelTab.Addons -> {
          PlayerOptionRow("None", selected = selectedSubtitleTrackId == null && selectedExternalSubtitleId == null) {
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
            Text("Embedded in video", color = Color.White.copy(alpha = 0.64f), fontWeight = FontWeight.Bold)
          }
          visibleEmbeddedTracks.forEach { track ->
            PlayerOptionRow(
              label = trackLanguageName(track.language) ?: track.title ?: "Subtitle ${track.id}",
              supportingText = listOfNotNull("Embedded", track.title, track.codec).distinct().joinToString(" • "),
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
          if (subtitlesLoading) Text("Searching subtitle sources...", color = Color.White.copy(alpha = 0.72f))
          if (visibleExternalSubtitles.isNotEmpty()) {
            Text(
              when (subtitleTab) {
                SubtitlePanelTab.BuiltIn -> "StreamDek sources"
                SubtitlePanelTab.Addons -> "Subtitle add-ons"
                else -> "Online subtitles"
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
              label = trackLanguageName(subtitle.language) ?: "Unknown language",
              supportingText = listOfNotNull(
                subtitle.sourceName,
                if (subtitle.origin == ExternalSubtitleOrigin.BuiltIn) "Built-in source" else "Add-on",
                subtitle.release?.takeIf { it.isNotBlank() },
                if (duplicateCount > 1) "Option $duplicateNumber" else null,
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
              .joinToString(" or ") { Languages.label(it) }
            Text(
              if (session.showOnlyPreferredSubtitleLanguages && requestedLanguages.isNotBlank()) {
                "No subtitles found for $requestedLanguages."
              } else when (subtitleTab) {
                SubtitlePanelTab.BuiltIn -> "No matching embedded or StreamDek subtitles found."
                SubtitlePanelTab.Addons -> "No matching subtitle add-on results found."
                else -> "No matching subtitles found."
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
          Slider(value = subtitleDelay, valueRange = -15f..15f, onValueChange = {
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
) {
  // Re-bound so the overlay bodies below read and write exactly as they did in the screen.
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
      shape = StreamDekRadius.panelShape,
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
            PlayerAdjustmentKind.Brightness -> Icons.Rounded.Brightness6
            PlayerAdjustmentKind.Volume -> if (adjustmentLevel <= 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp
            null -> Icons.Rounded.VolumeUp
          },
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(19.dp),
        )
        Text(
          "${if (adjustmentKind == PlayerAdjustmentKind.Brightness) "Brightness" else "Volume"} ${(adjustmentLevel * 100f).toInt()}%",
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
      layout = session.playerControlLayout,
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
      showLabels = session.showPlayerControlLabels,
      layout = session.playerControlLayout,
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
  activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
  applyPlayerSystemUi()
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
    "recap" -> "Recap skipped"
    "outro" -> "Ending skipped"
    else -> "Intro skipped"
  }
}

LaunchedEffect(autoSkipNotice) {
  if (autoSkipNotice == null) return@LaunchedEffect
  delay(2_200)
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
LaunchedEffect(playbackIdentity, session.autoLoadSubtitles, playerView, exoPlayerView, session.isLive, userSubtitleSources) {
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
}


/**
 * What sits directly on the video surface: the loading and paused cards, the skip-segment prompt,
 * the slow-load hint, and the gesture layer that handles tap, scrub, brightness and volume drags.
 *
 * Extracted from [NativePlayerScreen] for the same reason as its siblings -- the gesture layer
 * alone declares a dozen lambdas, and each is memoised in whichever composable declares it.
 */
@Composable
private fun RecommendationPanel(
  recommendations: List<MediaItem>,
  showNextEpisode: Boolean,
  queuedRecommendationId: String?,
  nextEpisodeQueued: Boolean,
  currentTitle: String,
  onQueueRecommendation: (MediaItem) -> Unit,
  onQueueNextEpisode: () -> Unit,
  onDismiss: () -> Unit,
  onTimeout: () -> Unit,
) {
  val visibleItems = recommendations.take(2)
  var secondsRemaining by remember { mutableIntStateOf(45) }
  LaunchedEffect(Unit) {
    while (secondsRemaining > 0) {
      delay(1_000)
      secondsRemaining -= 1
    }
    onTimeout()
  }
  Surface(
    modifier = Modifier.widthIn(max = 720.dp),
    color = Color(0xF214171C),
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    tonalElevation = 10.dp,
  ) {
    BoxWithConstraints {
      val useColumns = !showNextEpisode && visibleItems.size == 2 && maxWidth >= 700.dp
      Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recommended for you", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (showNextEpisode) {
          RecommendationChoice(null, "Continue when this episode finishes", nextEpisodeQueued, onQueueNextEpisode)
        } else if (useColumns) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            visibleItems.forEach { item ->
              RecommendationChoice(item, "Because you watched $currentTitle", queuedRecommendationId == item.id, { onQueueRecommendation(item) }, Modifier.weight(1f))
            }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            visibleItems.forEach { item ->
              RecommendationChoice(item, "Because you watched $currentTitle", queuedRecommendationId == item.id, { onQueueRecommendation(item) })
            }
          }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).height(38.dp)) {
          Text("Dismiss · ${secondsRemaining}s", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@Composable
private fun RecommendationChoice(
  item: MediaItem?,
  reason: String?,
  queued: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.widthIn(min = 280.dp, max = 350.dp).background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(12.dp)).padding(8.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val artwork = item?.backdrop ?: item?.poster
    Box(Modifier.width(92.dp).height(56.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF272C35)), contentAlignment = Alignment.Center) {
      if (!artwork.isNullOrBlank()) {
        AsyncImage(model = artwork, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      } else {
        Icon(Icons.Rounded.Tv, contentDescription = null, tint = Color.White.copy(alpha = 0.30f))
      }
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(item?.title ?: "Next Episode", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
      reason?.takeIf { it.isNotBlank() }?.let {
        Text(it, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Button(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(38.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (queued) Color.White.copy(alpha = 0.12f) else Color(0xFFF0BA66), contentColor = if (queued) Color.White else Color(0xFF171A20)),
      ) { Text(if (queued) "Selected - Queued next" else "Watch after this", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
    }
  }
}

@Composable
private fun BoxScope.PlayerSurfaceOverlays(
  session: PlayerSession,
  source: PlayerSourceState,
  playback: PlayerPlaybackState,
  isLoading: Boolean,
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
  recommendationVisible: Boolean,
  recommendations: List<MediaItem>,
  showNextEpisodeRecommendation: Boolean,
  queuedRecommendationId: String?,
  nextEpisodeQueued: Boolean,
  currentTitle: String,
  onQueueRecommendation: (MediaItem) -> Unit,
  onQueueNextEpisode: () -> Unit,
  onDismissRecommendation: () -> Unit,
  onRecommendationTimeout: () -> Unit,
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
        Icon(if (displayedSeekFeedbackAmount < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward, contentDescription = null, tint = Color.White)
        Text("${if (displayedSeekFeedbackAmount > 0) "+" else "−"}${kotlin.math.abs(displayedSeekFeedbackAmount)}s", color = Color.White, fontWeight = FontWeight.Bold)
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
      Icon(if (playPauseFeedback == true) Icons.Rounded.Pause else RoundedPlayerPlayIcon, contentDescription = null, tint = Color.White, modifier = Modifier.padding(16.dp).size(30.dp))
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
          Text("This source keeps buffering", color = Color.White, fontWeight = FontWeight.Black)
          Text("Switch to the next ranked source and keep your position?", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = {
          smartSwitchCandidate = null
          smartSwitchCooldownUntil = android.os.SystemClock.elapsedRealtime() + 180_000L
          recentPlaybackStalls = emptyList()
        }) { Text("Keep") }
        Button(onClick = {
          val next = smartSwitchCandidate ?: return@Button
          smartSwitchCandidate = null
          recentPlaybackStalls = emptyList()
          smartSwitchCooldownUntil = android.os.SystemClock.elapsedRealtime() + 60_000L
          onSelectStream(next, progressPercent())
        }) { Text("Switch") }
      }
    }
  }

  if (recommendationVisible && !isLoading && activePanel == PlayerPanel.None) {
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
    visible = recommendationVisible && !isLoading && activePanel == PlayerPanel.None,
    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp).zIndex(6f),
    enter = fadeIn(animationSpec = StreamDekMotion.enterSpec()) + slideInVertically(initialOffsetY = { it / 3 }, animationSpec = StreamDekMotion.enterSpec()),
    exit = fadeOut(animationSpec = StreamDekMotion.exitSpec()) + slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = StreamDekMotion.exitSpec()),
  ) {
    RecommendationPanel(
      recommendations = recommendations,
      showNextEpisode = showNextEpisodeRecommendation,
      queuedRecommendationId = queuedRecommendationId,
      nextEpisodeQueued = nextEpisodeQueued,
      currentTitle = currentTitle,
      onQueueRecommendation = onQueueRecommendation,
      onQueueNextEpisode = onQueueNextEpisode,
      onDismiss = onDismissRecommendation,
      onTimeout = onRecommendationTimeout,
    )
  }

  AnimatedVisibility(
    visible = !isLoading && activePanel == PlayerPanel.None && activeSkipSegment != null,
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
    ) { Text(if (nextEpisodeActionAvailable) "Next Episode" else when (activeSkipSegment?.type) { "recap" -> "Skip Recap"; "outro" -> "Skip Ending"; else -> "Skip Intro" }, fontWeight = FontWeight.Bold) }
  }
  AnimatedVisibility(
    visible = autoSkipNotice != null,
    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 118.dp).zIndex(5f),
    enter = fadeIn(animationSpec = tween(160)),
    exit = fadeOut(animationSpec = tween(180)),
  ) {
    Surface(color = Color(0xD91A1D24), shape = StreamDekRadius.pill) {
      Text(autoSkipNotice.orEmpty(), modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
    }
  }
  // A live-channel switch has its own compact status indicator below. Keeping the
  // general loading backdrop here as well produced two competing loading messages.
  if (isLoading && !(session.isLive && channelSwitchLoading)) {
    PlayerLoadingBackdrop(
      session = session,
      message = when {
        nextEpisodeLoading -> listOfNotNull("Loading next episode", nextEpisodeLoadingLabel).joinToString(" · ")
        session.isLive && slowLoadHintVisible -> "This channel is available but is taking a while to load…"
        peerSwarm != null -> "Buffering from peers..."
        else -> "Preparing stream..."
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
      .pointerInput(session.url, session.isLive, isLoading, controlsLocked, audioManager, session.swipeToSeekEnabled, session.levelGesturesEnabled, duration) {
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
      // Every dimension here is the previous one scaled by 0.6 — the badge reads as a label on the
      // picture rather than a control competing with it.
      Row(modifier = Modifier.padding(horizontal = 8.1.dp, vertical = 3.8.dp), horizontalArrangement = Arrangement.spacedBy(3.6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (session.isVod) {
          Icon(RoundedPlayerPlayIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(7.2.dp))
        } else {
          Box(modifier = Modifier.size(3.8.dp).clip(CircleShape).background(Color.White))
        }
        Text(if (session.isVod) "VOD" else "LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 6.5.sp, letterSpacing = 0.54.sp)
      }
    }
  }
}
