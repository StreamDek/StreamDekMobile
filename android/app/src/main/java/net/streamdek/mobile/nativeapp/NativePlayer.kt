package net.streamdek.mobile.nativeapp

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SettingsOverscan
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import net.streamdek.mobile.MainActivity
import net.streamdek.mobile.mpv.MPVView
import net.streamdek.mobile.mpv.MpvTrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private enum class PlayerPanel { None, Sources, Audio, Subtitles, Speed }
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
  onBack: (Double) -> Unit,
  onScrobble: (String, Double) -> Unit,
  onProgressCheckpoint: (Double) -> Unit,
  onSelectStream: (AddonStream, Double) -> Unit,
  onReloadStreams: () -> Unit,
  onPlaybackEnded: () -> Unit,
  nextEpisodeLoading: Boolean = false,
  nextEpisodeLoadingLabel: String? = null,
  onPreviousEpisode: () -> Unit = {},
  onNextEpisode: () -> Unit = {},
) {
  val playerContext = LocalContext.current
  val playerScope = rememberCoroutineScope()
  val activity = playerContext as? Activity
  val userSubtitleSources = remember(session.url, playerContext) { UserSubtitleSourceStore.load(playerContext).filter { it.enabled } }
  val surfaceInteractionSource = remember { MutableInteractionSource() }
  var isPaused by remember(session.url) { mutableStateOf(false) }
  var currentTime by remember(session.url) { mutableDoubleStateOf(0.0) }
  var duration by remember(session.url) { mutableDoubleStateOf(0.0) }
  var error by remember(session.url) { mutableStateOf<String?>(null) }
  var hasLoaded by remember(session.url) { mutableStateOf(false) }
  var playerView by remember(session.url) { mutableStateOf<MPVView?>(null) }
  var resizeMode by rememberSaveable(session.url) { mutableStateOf("cover") }
  var playbackSpeed by rememberSaveable(session.url) { mutableFloatStateOf(1f) }
  var activePanel by rememberSaveable(session.url) { mutableStateOf(PlayerPanel.None) }
  var subtitleDelay by rememberSaveable(session.url) { mutableFloatStateOf(0f) }
  var subtitleSize by rememberSaveable(session.url) { mutableIntStateOf(55) }
  var subtitlePosition by rememberSaveable(session.url) { mutableIntStateOf(92) }
  var subtitleColor by rememberSaveable(session.url) { mutableStateOf("#FFFFFFFF") }
  var selectedAudioTrackId by remember(session.url) { mutableStateOf<Int?>(null) }
  var selectedSubtitleTrackId by remember(session.url) { mutableStateOf<Int?>(null) }
  var subtitleTab by rememberSaveable(session.url) { mutableStateOf(SubtitlePanelTab.BuiltIn) }
  var subtitleDisabledByUser by remember(session.url) { mutableStateOf(false) }
  var externalSubtitles by remember(session.url) { mutableStateOf<List<ExternalSubtitle>>(emptyList()) }
  var selectedExternalSubtitleId by remember(session.url) { mutableStateOf<String?>(null) }
  var subtitlesLoading by remember(session.url) { mutableStateOf(false) }
  var skipSegments by remember(session.url) { mutableStateOf<List<SkipSegment>>(emptyList()) }
  val audioTracks = remember(session.url) { mutableStateListOf<MpvTrackInfo>() }
  val subtitleTracks = remember(session.url) { mutableStateListOf<MpvTrackInfo>() }
  var scrobbleStarted by remember(session.url) { mutableStateOf(false) }
  var showControls by remember(session.url) { mutableStateOf(true) }
  var controlActivityVersion by remember(session.url) { mutableIntStateOf(0) }
  var playbackEnded by remember(session.url) { mutableStateOf(false) }
  var completionDispatched by remember(session.url) { mutableStateOf(false) }
  var liveReconnectVersion by remember(session.url) { mutableIntStateOf(0) }
  var showPausedInfo by remember(session.url) { mutableStateOf(false) }
  var didApplyResume by remember(session.url) { mutableStateOf(false) }
  var lastCheckpointSecond by remember(session.url) { mutableDoubleStateOf(0.0) }
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

  BackHandler { closePlayer() }

  DisposableEffect(activity) {
    val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    val decorView = activity?.window?.decorView
    val previousSystemUi = decorView?.systemUiVisibility ?: 0
    MainActivity.pipShouldEnter = session.pictureInPictureEnabled
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
      activity?.requestedOrientation = previous
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

  LaunchedEffect(playbackEnded) {
    if (playbackEnded) {
      delay(450)
      onPlaybackEnded()
    }
  }

  LaunchedEffect(session.url, session.skipIntroEnabled, session.skipRecapEnabled, session.skipEndingEnabled, session.isLive) {
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

  LaunchedEffect(session.url, duration, session.autoLoadSubtitles, playerView, subtitleDisabledByUser, session.isLive, userSubtitleSources) {
    if (session.isLive) return@LaunchedEffect
    if (!session.autoLoadSubtitles || duration <= 0.0 || playerView == null || subtitleDisabledByUser) return@LaunchedEffect
    delay(1_200)
    subtitlesLoading = true
    val results = fetchExternalSubtitles(session, userSubtitleSources)
    externalSubtitles = results
    if (selectedSubtitleTrackId == null && selectedExternalSubtitleId == null && !subtitleDisabledByUser) {
      results.firstOrNull { it.language == "en" }?.let { subtitle ->
        selectedExternalSubtitleId = subtitle.id
        // Download off the player thread first — handing mpv a remote URL stalls
        // playback while it fetches the file.
        val localPath = downloadSubtitleToCache(playerContext, subtitle.url)
        if (localPath != null && selectedExternalSubtitleId == subtitle.id) playerView?.addSubtitleFile(localPath)
      }
    }
    subtitlesLoading = false
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
  LaunchedEffect(liveReconnectVersion) {
    if (session.isLive && liveReconnectVersion > 0) {
      delay(300)
      error = null
      playerView?.reloadSource()
      playerView?.setPaused(false)
    }
  }


  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    key(session.url) {
      AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        MPVView(context).apply {
          playerView = this
          onLoadCallback = { loadedDuration, _, _ ->
            hasLoaded = true
            duration = loadedDuration
            error = null
            if (!didApplyResume && session.resumePercent > 0.0 && loadedDuration > 0.0) {
              val resumeAt = (loadedDuration * (session.resumePercent / 100.0)).coerceIn(0.0, (loadedDuration - 5.0).coerceAtLeast(0.0))
              seekTo(resumeAt)
              currentTime = resumeAt
              didApplyResume = true
            }
          }
          onProgressCallback = { position, total ->
            currentTime = position
            duration = total
            if (!session.isLive && !isPaused && total > 0.0 && position >= total - 0.75) finishPlayback()
            if (!session.isLive && !isPaused && total > 0.0 && position >= 10.0 && position - lastCheckpointSecond >= 10.0) {
              lastCheckpointSecond = position
              onProgressCheckpoint(((position / total) * 100.0).coerceIn(0.0, 100.0))
            }
          }
          onErrorCallback = { message ->
            if (session.isLive) {
              error = "Live feed interrupted. Reconnecting..."
              liveReconnectVersion += 1
            } else {
              error = message
            }
          }
          onEndCallback = { if (session.isLive) liveReconnectVersion += 1 else finishPlayback() }
          onTracksChangedCallback = { audio, subtitles, audioId, subtitleId ->
            audioTracks.clear()
            subtitleTracks.clear()
            audioTracks.addAll(audio)
            subtitleTracks.addAll(subtitles)
            selectedAudioTrackId = audioId
            val englishAudio = audio.firstOrNull { normalizeSubtitleLanguage(it.language) == "en" }
            if (englishAudio != null && audioId != englishAudio.id) {
              selectedAudioTrackId = englishAudio.id
              setAudioTrack(englishAudio.id)
            }
            selectedSubtitleTrackId = subtitleId
            if (!subtitleDisabledByUser) {
              val englishSubtitle = subtitles.firstOrNull { normalizeSubtitleLanguage(it.language) == "en" }
              when {
                englishSubtitle != null && subtitleId != englishSubtitle.id -> {
                  selectedSubtitleTrackId = englishSubtitle.id
                  setSubtitleTrack(englishSubtitle.id)
                }
                englishSubtitle == null && subtitleId != null -> {
                  selectedSubtitleTrackId = null
                  disableSubtitleTrack()
                }
              }
            }
          }
          setResizeMode(resizeMode)
          setDecoderMode(session.decoderMode)
          setRenderSurface(session.renderSurface)
          setSpeed(playbackSpeed.toDouble())
          setSubtitleDelay(subtitleDelay.toDouble())
          setSubtitleFontSize(subtitleSize)
          setSubtitlePosition(subtitlePosition)
          setSubtitleColor(subtitleColor)
          setHeaders(session.requestHeaders)
          setSource(session.url)
          setPaused(false)
        }
      },
      update = { view ->
        view.setHeaders(session.requestHeaders)
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
        view.setHeaders(session.requestHeaders)
        },
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
            onNextEpisode()
          } else {
            activeSkipSegment?.let { segment ->
              currentTime = segment.endSeconds
              playerView?.seekTo(segment.endSeconds)
              skipSegments = skipSegments - segment
            }
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        shape = RoundedCornerShape(22.dp),
      ) { Text(if (nextEpisodeActionAvailable) "Next Episode" else when (activeSkipSegment?.type) { "recap" -> "Skip Recap"; "outro" -> "Skip Ending"; else -> "Skip Intro" }, fontWeight = FontWeight.Bold) }
    }
    if (isLoading) {
      PlayerLoadingBackdrop(session = session, message = if (nextEpisodeLoading) listOfNotNull("Loading next episode", nextEpisodeLoadingLabel).joinToString(" · ") else "Preparing stream...")
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
        .background(Color.Black.copy(alpha = if (isLoading || (!showControls && activePanel == PlayerPanel.None)) 0.0f else if (activePanel == PlayerPanel.None) 0.18f else 0.58f))
        .clickable(enabled = !isLoading, interactionSource = surfaceInteractionSource, indication = null) {
          if (activePanel == PlayerPanel.None) {
            showPausedInfo = false
            showControls = !showControls
          }
          },
      )

    AnimatedVisibility(
      visible = !isLoading && (showControls || activePanel != PlayerPanel.None || !error.isNullOrBlank()),
      enter = fadeIn(animationSpec = tween(220)),
      exit = fadeOut(animationSpec = tween(220)),
    ) {
      PlayerTopHeader(
        session = session,
        onBack = { closePlayer() },
        modifier = Modifier.align(Alignment.TopStart),
      )
    }

    AnimatedVisibility(
      visible = !isLoading && activePanel == PlayerPanel.None && (showControls || !error.isNullOrBlank()),
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
          playerView?.seekTo(currentTime)
          keepControlsVisible()
        },
        showSeeking = !session.isLive,
      )
    }

    AnimatedVisibility(
      visible = !isLoading && activePanel == PlayerPanel.None && (showControls || !error.isNullOrBlank()),
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
      enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it / 10 }, animationSpec = tween(280)),
      exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(targetOffsetY = { it / 12 }, animationSpec = tween(220)),
    ) {
      PlayerBottomControls(
        currentTime = currentTime,
        duration = duration,
        isLive = session.isLive,
        progress = if (!session.isLive && duration > 0.0) (currentTime / duration).toFloat().coerceIn(0f, 1f) else 0f,
        error = error,
        resizeMode = resizeMode,
        speed = playbackSpeed,
        onProgressChange = { fraction -> currentTime = duration * fraction; keepControlsVisible() },
        onProgressFinished = { playerView?.seekTo(currentTime); keepControlsVisible() },
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
      )
    }

    when (activePanel) {
      PlayerPanel.Audio -> PlayerModalPanel(title = "Audio", onClose = { activePanel = PlayerPanel.None }) {
        if (audioTracks.isEmpty()) {
          PlayerOptionRow("Default audio", selected = true, onClick = {})
        } else {
          audioTracks.forEach { track ->
            PlayerOptionRow(trackLabel(track), selected = selectedAudioTrackId == track.id) {
              selectedAudioTrackId = track.id
              playerView?.setAudioTrack(track.id)
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
                .clickable { subtitleTab = tab }
                .padding(vertical = 11.dp),
              contentAlignment = Alignment.Center,
            ) { Text(if (tab == SubtitlePanelTab.BuiltIn) "Built-in" else tab.name, color = if (selected) Color.Black else Color.White.copy(alpha = 0.72f), fontWeight = FontWeight.Bold) }
          }
        }
        when (subtitleTab) {
          SubtitlePanelTab.BuiltIn -> {
            PlayerOptionRow("None", selected = selectedSubtitleTrackId == null && selectedExternalSubtitleId == null) {
              subtitleDisabledByUser = true
              selectedSubtitleTrackId = null
              selectedExternalSubtitleId = null
              playerView?.disableSubtitleTrack()
            }
            if (subtitleTracks.isEmpty()) Text("No embedded subtitle tracks.", color = Color.White.copy(alpha = 0.64f))
            subtitleTracks.forEach { track ->
              PlayerOptionRow(trackLabel(track), selected = selectedSubtitleTrackId == track.id) {
                subtitleDisabledByUser = false
                selectedExternalSubtitleId = null
                selectedSubtitleTrackId = track.id
                playerView?.setSubtitleTrack(track.id)
              }
            }
          }
          SubtitlePanelTab.Addons -> {
            if (subtitlesLoading) Text("Searching subtitle addons...", color = Color.White.copy(alpha = 0.72f))
            if (!subtitlesLoading && externalSubtitles.isEmpty()) Text("No matching addon subtitles found.", color = Color.White.copy(alpha = 0.64f))
            externalSubtitles.forEach { subtitle ->
              PlayerOptionRow(subtitle.label, selected = selectedExternalSubtitleId == subtitle.id) {
                subtitleDisabledByUser = false
                selectedSubtitleTrackId = null
                selectedExternalSubtitleId = subtitle.id
                // Fetch in the background and hand mpv a local file so the video
                // keeps playing while the new subtitle loads.
                playerScope.launch {
                  val localPath = downloadSubtitleToCache(playerContext, subtitle.url)
                  if (localPath != null && selectedExternalSubtitleId == subtitle.id) playerView?.addSubtitleFile(localPath)
                }
              }
            }
          }
          SubtitlePanelTab.Style -> {
            Text("Subtitle size: $subtitleSize", color = Color.White.copy(alpha = 0.72f))
            Slider(value = subtitleSize.toFloat(), valueRange = 28f..84f, onValueChange = {
              subtitleSize = it.toInt()
              playerView?.setSubtitleFontSize(subtitleSize)
            })
            Text("Position: $subtitlePosition", color = Color.White.copy(alpha = 0.72f))
            Slider(value = subtitlePosition.toFloat(), valueRange = 50f..110f, onValueChange = {
              subtitlePosition = it.toInt()
              playerView?.setSubtitlePosition(subtitlePosition)
            })
            Text("Delay: ${"%.1f".format(subtitleDelay)}s", color = Color.White.copy(alpha = 0.72f))
            Slider(value = subtitleDelay, valueRange = -5f..5f, onValueChange = {
              subtitleDelay = it
              playerView?.setSubtitleDelay(it.toDouble())
            })
          }
        }
      }
      PlayerPanel.Sources -> PlayerModalPanel(title = "Sources", onClose = { activePanel = PlayerPanel.None }, trailing = {
        TextButton(onClick = onReloadStreams, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Reload") }
      }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
          availableStreams
            .take(16)
            .forEach { stream ->
              PlayerSourceCard(
                stream = stream,
                active = streamsRepresentSameSource(stream, session.currentStream),
                onClick = {
                  activePanel = PlayerPanel.None
                  onSelectStream(stream, currentProgressPercent)
                },
              )
            }
          if (availableStreams.isEmpty()) {
            Text("No loaded sources yet.", color = Color.White.copy(alpha = 0.66f))
          }
        }
      }
      PlayerPanel.Speed -> PlayerModalPanel(title = "Speed", onClose = { activePanel = PlayerPanel.None }, compact = true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
            FilterChip(
              selected = playbackSpeed == speed,
              onClick = {
                playbackSpeed = speed
                playerView?.setSpeed(speed.toDouble())
              },
              label = { Text(if (speed == 1f) "1x" else "${speed}x") },
            )
          }
        }
      }
      PlayerPanel.None -> Unit
    }
    if (session.isLive && hasLoaded && !isLoading && error.isNullOrBlank()) {
      Surface(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp).zIndex(12f),
        color = Color(0xFFE11D48),
        shape = CircleShape,
      ) {
        Row(modifier = Modifier.padding(horizontal = 13.5.dp, vertical = 6.3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(modifier = Modifier.size(6.3.dp).clip(CircleShape).background(Color.White))
          Text("LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.8.sp, letterSpacing = 0.9.sp)
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
  Row(
    modifier = Modifier.fillMaxSize().padding(horizontal = 300.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
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
  onAudio: () -> Unit,
  onSources: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.78f))))
      .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (!error.isNullOrBlank()) Text(error, color = Color(0xFFFF9A9A), style = MaterialTheme.typography.bodySmall)
    if (!isLive) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.width(78.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.44f)).padding(vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) { Text(formatClock(currentTime), color = Color.White.copy(alpha = 0.92f)) }
        Slider(value = progress, onValueChange = onProgressChange, onValueChangeFinished = onProgressFinished, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
        Box(
          modifier = Modifier.width(96.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.44f)).padding(vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) { Text(formatClock(duration), color = Color.White.copy(alpha = 0.92f)) }
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
        PlayerDockButton("Speed", Icons.Rounded.SlowMotionVideo, onSpeed)
        PlayerDockButton("Subs", Icons.Rounded.Subtitles, onSubtitles)
        PlayerDockButton("Audio", Icons.Rounded.VolumeUp, onAudio)
        PlayerDockButton("Sources", Icons.Rounded.GridView, onSources)
      }
    }
  }
}

@Composable
private fun PlayerDockButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.clickable(onClick = onClick),
  ) {
    Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(21.dp))
    Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp, maxLines = 1)
  }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PlayerTopHeader(
  session: PlayerSession,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
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
}

@Composable
private fun PlayerSourceCard(
  stream: AddonStream,
  active: Boolean,
  onClick: () -> Unit,
) {
  val header = listOfNotNull(stream.addonName.takeIf { it.isNotBlank() } ?: stream.addonId, stream.source?.takeIf { it.isNotBlank() }, stream.quality?.takeIf { it.isNotBlank() }).distinct().joinToString("  ")
  val metaLine = listOfNotNull(
    stream.name?.takeIf { it.isNotBlank() },
    stream.title?.takeIf { it.isNotBlank() },
  ).joinToString("  ")
  val supportingLine = listOfNotNull(
    stream.description?.takeIf { it.isNotBlank() },
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
    Text(header, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (metaLine.isNotBlank()) {
      Text(metaLine, color = Color.White.copy(alpha = 0.74f), style = MaterialTheme.typography.bodyMedium)
    }
    if (supportingLine.isNotBlank()) {
      Text(supportingLine, color = Color.White.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      StreamInfoPill(icon = Icons.Rounded.HighQuality, label = stream.quality ?: "Stream")
      stream.size?.takeIf { it.isNotBlank() }?.let {
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

private fun trackLabel(track: MpvTrackInfo): String =
  listOfNotNull(track.language?.uppercase(), track.title, track.codec).joinToString(" - ").ifBlank { "Track ${track.id}" }

private fun formatClock(seconds: Double): String {
  if (seconds.isNaN() || seconds <= 0.0) return "0:00"
  val total = seconds.toInt()
  val hours = total / 3600
  val minutes = (total % 3600) / 60
  val secs = total % 60
  return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}
private val playerHttpClient = OkHttpClient()

private fun normalizeSubtitleLanguage(language: String?): String = when (language?.trim()?.lowercase()) {
  "eng", "english" -> "en"
  "spa", "spanish" -> "es"
  "por", "portuguese", "pt-br", "pob" -> "pt"
  else -> language?.trim()?.lowercase().orEmpty().substringBefore('-')
}

// Downloads a remote subtitle to the app cache and returns the local path.
// mpv's sub-add blocks the playback loop while it opens network streams, so
// feeding it a local file keeps the video playing during subtitle switches.
private suspend fun downloadSubtitleToCache(context: Context, url: String): String? = withContext(Dispatchers.IO) {
  runCatching {
    if (!url.startsWith("http", ignoreCase = true)) return@runCatching url
    val extension = url.substringAfterLast('.', "srt").substringBefore('?')
      .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) } ?: "srt"
    val file = File(context.cacheDir, "subtitles/${url.hashCode().toUInt()}.$extension")
    if (file.exists() && file.length() > 0L) return@runCatching file.absolutePath
    file.parentFile?.mkdirs()
    playerHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
      if (!response.isSuccessful) error("Subtitle download failed: ${response.code}")
      file.writeBytes(response.body?.bytes() ?: error("Empty subtitle body"))
    }
    file.absolutePath
  }.getOrNull()
}

private suspend fun fetchExternalSubtitles(session: PlayerSession, userSources: List<UserSubtitleSource>): List<ExternalSubtitle> = withContext(Dispatchers.IO) {
  val imdbId = session.imdbId?.takeIf { it.startsWith("tt") } ?: return@withContext emptyList()
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

  sources.flatMap { source ->
    runCatching {
      val endpoint = "${source.baseUrl.trimEnd('/')}/subtitles/$type/$videoId.json"
      val request = Request.Builder().url(endpoint).header("Accept", "application/json").build()
      playerHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@use emptyList()
        val entries = JSONObject(response.body?.string().orEmpty()).optJSONArray("subtitles") ?: JSONArray()
        buildList {
          for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val url = item.optString("url").trim()
            val language = normalizeSubtitleLanguage(item.optString("lang"))
            if (id.isBlank() || url.isBlank() || language.isBlank()) continue
            val release = item.optString("m").trim()
            val label = listOf(language.uppercase(), release, source.name).filter { it.isNotBlank() }.joinToString(" - ")
            add(ExternalSubtitle("${source.id}:$id", language, label, url))
          }
        }
      }
    }.getOrDefault(emptyList())
  }
    .distinctBy { it.url }
    .sortedWith(compareBy<ExternalSubtitle> { if (it.language == normalizeSubtitleLanguage(session.subtitleLanguage)) 0 else if (it.language == "en") 1 else 2 }.thenBy { it.label })
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

private suspend fun fetchSkipSegments(session: PlayerSession): List<SkipSegment> = withContext(Dispatchers.IO) {
  val imdbId = session.imdbId?.takeIf { it.startsWith("tt") } ?: return@withContext emptyList()
  val season = session.seasonNumber ?: return@withContext emptyList()
  val episode = session.episodeNumber ?: return@withContext emptyList()
  runCatching {
    val request = Request.Builder().url("https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=$episode").header("Accept", "application/json").build()
    val segments = playerHttpClient.newCall(request).execute().use { response -> if (response.isSuccessful) extractSkipSegments(response.body?.string().orEmpty()) else emptyList() }.toMutableList()
    if (segments.none { it.type == "intro" }) {
      val legacy = Request.Builder().url("https://api.introdb.app/intro?imdb=$imdbId&imdb_id=$imdbId&season=$season&episode=$episode").header("Accept", "application/json").build()
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
  if (!candidate.url.isNullOrBlank() && candidate.url == current.url) return true
  if (!candidate.infoHash.isNullOrBlank() && candidate.infoHash == current.infoHash && candidate.fileIdx == current.fileIdx) return true
  return candidate.addonId == current.addonId && candidate.name == current.name && candidate.title == current.title && candidate.filename == current.filename
}
