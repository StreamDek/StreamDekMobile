package net.streamdek.mobile.nativeapp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.rememberHazeState
import net.streamdek.mobile.R
import kotlinx.coroutines.launch
import net.streamdek.mobile.nativeapp.mediaserver.JELLYFIN_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReachability
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import net.streamdek.mobile.nativeapp.mediaserver.PLEX_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerResume
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerRow
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerUiState
import net.streamdek.mobile.nativeapp.mediaserver.OfflineReason

/*
 * The Plex page, the combined Library page and the Plex mark on Plex lists.
 *
 * Their own file on purpose: StreamDekNativeApp.kt compiles to one JVM class at the class-file
 * limit (65,535 constants), and anything added there can stop the debug build with "Class too
 * large". These screens take plain values rather than the app state, so they need nothing private
 * to that file.
 */

/** How far the list scrolls before a header has fully condensed. */
private val CondenseDistance = 96.dp

/** 0 at the top of the list, 1 once it has scrolled [distancePx] or past its first item. */
private fun LazyListState.condenseProgress(distancePx: Float): Float =
  if (firstVisibleItemIndex > 0) 1f else (firstVisibleItemScrollOffset / distancePx).coerceIn(0f, 1f)

/**
 * The Plex colour wash: purple, blue, red and green fields of light drifting slowly behind a Plex
 * page. Drawn by this modifier behind the node's content - put it after the page's glass source so
 * the header's glass picks it up. Quieter on a light theme, and still when the app's reduced-motion
 * setting is on. The drift is read while drawing, so it redraws the wash and recomposes nothing.
 */
@Composable
internal fun Modifier.plexAmbientGlow(): Modifier {
  val motionless = LocalMotionSettings.current.motionless
  val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val strength = if (dark) 0.34f else 0.18f
  val drift: State<Float> = if (motionless) {
    remember { mutableFloatStateOf(0.5f) }
  } else {
    rememberInfiniteTransition(label = "plexAmbient").animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing), RepeatMode.Reverse),
      label = "plexAmbientDrift",
    )
  }
  return drawBehind {
    val d = drift.value
    val w = size.width
    val h = size.height
    val radius = maxOf(w, h) * 0.55f
    fun glow(color: Color, x: Float, y: Float, scale: Float = 1f) {
      val center = Offset(x * w, y * h)
      drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = strength), color.copy(alpha = 0f)), center = center, radius = radius * scale),
        radius = radius * scale,
        center = center,
      )
    }
    glow(PlexAmbientPurple, 0.10f + 0.10f * d, 0.06f + 0.05f * d, 1.05f)
    glow(PlexAmbientBlue, 0.92f - 0.08f * d, 0.12f + 0.08f * d)
    glow(PlexAmbientRed, 0.18f + 0.06f * d, 0.58f - 0.07f * d, 0.9f)
    glow(PlexAmbientGreen, 0.86f - 0.10f * d, 0.78f - 0.05f * d, 0.95f)
  }
}

private val PlexAmbientPurple = Color(0xFF8B5CF6)
private val PlexAmbientBlue = Color(0xFF3B82F6)
private val PlexAmbientRed = Color(0xFFEF4444)
private val PlexAmbientGreen = Color(0xFF22C55E)

/**
 * Jellyfin's colour wash: orange and red fields of light rising out of black, drifting as Plex's
 * does. The page is first taken down toward black in a dark theme, so the colours glow rather than
 * tint grey; on a light theme it stays a light touch.
 */
@Composable
internal fun Modifier.jellyfinAmbientGlow(): Modifier {
  val motionless = LocalMotionSettings.current.motionless
  val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val strength = if (dark) 0.34f else 0.16f
  val drift: State<Float> = if (motionless) {
    remember { mutableFloatStateOf(0.5f) }
  } else {
    rememberInfiniteTransition(label = "jellyfinAmbient").animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing), RepeatMode.Reverse),
      label = "jellyfinAmbientDrift",
    )
  }
  return drawBehind {
    if (dark) drawRect(Color.Black.copy(alpha = 0.55f))
    val d = drift.value
    val w = size.width
    val h = size.height
    val radius = maxOf(w, h) * 0.55f
    fun glow(color: Color, x: Float, y: Float, scale: Float = 1f, weight: Float = 1f) {
      val center = Offset(x * w, y * h)
      drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = strength * weight), color.copy(alpha = 0f)), center = center, radius = radius * scale),
        radius = radius * scale,
        center = center,
      )
    }
    glow(JellyfinAmbientOrange, 0.12f + 0.10f * d, 0.08f + 0.05f * d, 1.05f)
    glow(JellyfinAmbientRed, 0.90f - 0.08f * d, 0.16f + 0.08f * d)
    glow(JellyfinAmbientEmber, 0.20f + 0.06f * d, 0.62f - 0.07f * d, 0.9f, 0.8f)
    glow(JellyfinAmbientCrimson, 0.84f - 0.10f * d, 0.82f - 0.05f * d, 0.95f)
  }
}

private val JellyfinAmbientOrange = Color(0xFFF97316)
private val JellyfinAmbientRed = Color(0xFFEF4444)
private val JellyfinAmbientEmber = Color(0xFFEA580C)
private val JellyfinAmbientCrimson = Color(0xFFB91C1C)

/**
 * The media page: the viewer's own libraries, in StreamDek's look.
 *
 * One page for every connected server. With only Plex or only Jellyfin it is that server's page;
 * with both, a Plex / Jellyfin switch sits in the header and the two pages sit side by side, so a
 * swipe moves between them too. Each page carries its server's Continue Watching first, then Next
 * Up where the server has it, each enabled library's Recently Added, the libraries themselves and
 * their collections, grouped under their server when there is more than one. The header condenses
 * into a floating glass pill as the page scrolls, and the colour wash is the server's own.
 */
@Composable
internal fun PlexTab(
  plexState: MediaServerUiState,
  jellyfinState: MediaServerUiState,
  initialProvider: String,
  serverContinueWatching: List<MediaServerResume>,
  pageRows: List<MediaServerRow>,
  pageLoading: Boolean,
  continueWatchingStyle: ContinueWatchingStyle,
  homeCardTextMode: HomeCardTextMode,
  watchlist: List<MediaItem>,
  headerStyle: HeaderStyle,
  plexAmbient: Boolean,
  jellyfinAmbient: Boolean,
  onLoad: (Boolean) -> Unit,
  onProviderShown: (String) -> Unit,
  onOpen: (MediaItem) -> Unit,
  onOpenCollection: (MediaItem) -> Unit,
  onPlayContinueWatching: (MediaItem) -> Unit,
  onViewAll: (HomeRow) -> Unit,
  onToggleWatchlist: (MediaItem) -> Unit,
  onMarkWatched: (MediaItem) -> Unit,
  onMarkEarlierEpisodesWatched: (MediaItem) -> Unit,
  onRestartFromBeginning: (MediaItem) -> Unit,
  onRemoveFromContinueWatching: (MediaItem) -> Unit,
  onOpenSettings: (String) -> Unit,
) {
  val providers = remember(plexState.navigationVisible, jellyfinState.navigationVisible) {
    buildList {
      if (plexState.navigationVisible) add(PLEX_PROVIDER_ID)
      if (jellyfinState.navigationVisible) add(JELLYFIN_PROVIDER_ID)
    }.ifEmpty { listOf(if (jellyfinState.linked && !plexState.linked) JELLYFIN_PROVIDER_ID else PLEX_PROVIDER_ID) }
  }
  val linked = plexState.linked || jellyfinState.linked
  LaunchedEffect(linked) { if (linked) onLoad(false) }
  val pagerState = androidx.compose.foundation.pager.rememberPagerState(
    initialPage = providers.indexOf(initialProvider).coerceAtLeast(0),
    pageCount = { providers.size },
  )
  val current = providers.getOrElse(pagerState.currentPage) { providers.first() }
  LaunchedEffect(current) { onProviderShown(current) }
  val listStates = remember { mutableMapOf<String, LazyListState>() }
  fun listStateOf(provider: String) = listStates.getOrPut(provider) { LazyListState() }
  val currentList = listStateOf(current)
  ReportScrollTop { currentList.firstVisibleItemIndex == 0 && currentList.firstVisibleItemScrollOffset == 0 }
  val loading = pageLoading || plexState.refreshing || jellyfinState.refreshing
  val density = LocalDensity.current
  var headerHeight by remember { mutableStateOf(120.dp) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val currentState = if (current == JELLYFIN_PROVIDER_ID) jellyfinState else plexState

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // The glass samples everything in here, each page's colour wash included.
    Box(modifier = Modifier.fillMaxSize()) {
      androidx.compose.foundation.pager.HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { providers[it] },
      ) { page ->
        val provider = providers[page]
        MediaServerProviderPage(
          provider = provider,
          state = if (provider == JELLYFIN_PROVIDER_ID) jellyfinState else plexState,
          listState = listStateOf(provider),
          serverContinueWatching = serverContinueWatching,
          pageRows = pageRows,
          loading = loading,
          ambient = if (provider == JELLYFIN_PROVIDER_ID) jellyfinAmbient else plexAmbient,
          headerBottom = headerHeight,
          continueWatchingStyle = continueWatchingStyle,
          homeCardTextMode = homeCardTextMode,
          watchlist = watchlist,
          onOpen = onOpen,
          onOpenCollection = onOpenCollection,
          onPlayContinueWatching = onPlayContinueWatching,
          onViewAll = onViewAll,
          onToggleWatchlist = onToggleWatchlist,
          onMarkWatched = onMarkWatched,
          onMarkEarlierEpisodesWatched = onMarkEarlierEpisodesWatched,
          onRestartFromBeginning = onRestartFromBeginning,
          onRemoveFromContinueWatching = onRemoveFromContinueWatching,
          onOpenSettings = { onOpenSettings(provider) },
          onRetry = { onLoad(true) },
        )
      }
    }
    // The header stays put and has nothing behind it, scrolled or not: the page's colour shows
    // through. The lists begin below it and fade out at its edge, so titles pass out of sight
    // beneath it rather than behind its words.
    Box(
      modifier = Modifier.align(Alignment.TopCenter).zIndex(5f).fillMaxWidth()
        .onSizeChanged { size -> headerHeight = with(density) { size.height.toDp() } }
        .statusBarsPadding()
        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (current == JELLYFIN_PROVIDER_ID) {
            Image(painterResource(R.drawable.jellyfin_logo), contentDescription = null, modifier = Modifier.size(34.dp))
          } else {
            Image(painterResource(R.drawable.plex_logo), contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape))
          }
          Column(Modifier.weight(1f)) {
            Text(
              stringResource(if (current == JELLYFIN_PROVIDER_ID) R.string.media_server_jellyfin else R.string.media_server_plex),
              style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1,
            )
            val enabledServers = currentState.servers.filter { it.enabled }
            val subtitle = listOfNotNull(currentState.accountName, enabledServers.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.name }).joinToString(" · ")
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(horizontal = 12.dp).size(18.dp), strokeWidth = 2.dp, color = accentOf(current))
          } else {
            IconButton(onClick = { onLoad(true) }) { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.plex_refresh), tint = MaterialTheme.colorScheme.onBackground) }
          }
          IconButton(onClick = { onOpenSettings(current) }) { Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.plex_manage), tint = MaterialTheme.colorScheme.onBackground) }
        }
        if (providers.size > 1) {
          // Both servers connected: which one this page shows. A swipe does the same.
          Row(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            providers.forEachIndexed { index, provider ->
              val selected = provider == current
              Row(
                modifier = Modifier.clip(RoundedCornerShape(50))
                  .background(if (selected) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f) else Color.Transparent)
                  .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                  .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  if (provider == JELLYFIN_PROVIDER_ID) JellyfinIcons.Mark else PlexIcons.Chevron,
                  contentDescription = null,
                  tint = if (selected) accentOf(provider) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  stringResource(if (provider == JELLYFIN_PROVIDER_ID) R.string.media_server_jellyfin else R.string.media_server_plex),
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selected) 1f else 0.7f),
                )
              }
            }
          }
        }
      }
    }
  }
}

private val JellyfinAccent = Color(0xFFAA5CC3)

/** Fades the top [fade] of what is drawn to nothing, so content leaving the list's top edge melts away. */
private fun Modifier.fadeTopEdge(fade: androidx.compose.ui.unit.Dp): Modifier =
  graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
      drawContent()
      val band = fade.toPx().coerceAtMost(size.height)
      drawRect(
        brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = band),
        size = androidx.compose.ui.geometry.Size(size.width, band),
        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
      )
    }

private fun accentOf(provider: String): Color = if (provider == JELLYFIN_PROVIDER_ID) JellyfinAccent else PlexGold

/** One server's page inside the media page. */
@Composable
private fun MediaServerProviderPage(
  provider: String,
  state: MediaServerUiState,
  listState: LazyListState,
  serverContinueWatching: List<MediaServerResume>,
  pageRows: List<MediaServerRow>,
  loading: Boolean,
  ambient: Boolean,
  headerBottom: androidx.compose.ui.unit.Dp,
  continueWatchingStyle: ContinueWatchingStyle,
  homeCardTextMode: HomeCardTextMode,
  watchlist: List<MediaItem>,
  onOpen: (MediaItem) -> Unit,
  onOpenCollection: (MediaItem) -> Unit,
  onPlayContinueWatching: (MediaItem) -> Unit,
  onViewAll: (HomeRow) -> Unit,
  onToggleWatchlist: (MediaItem) -> Unit,
  onMarkWatched: (MediaItem) -> Unit,
  onMarkEarlierEpisodesWatched: (MediaItem) -> Unit,
  onRestartFromBeginning: (MediaItem) -> Unit,
  onRemoveFromContinueWatching: (MediaItem) -> Unit,
  onOpenSettings: () -> Unit,
  onRetry: () -> Unit,
) {
  val jellyfin = provider == JELLYFIN_PROVIDER_ID
  val continueItems = remember(serverContinueWatching, provider) {
    serverContinueWatching
      .filter { MediaServerReference.providerOfSource(it.item.sourceAddonId) == provider }
      .sortedByDescending { it.lastViewedAtMs }.map { it.item }
  }
  val rows = remember(pageRows, provider) { pageRows.filter { it.items.isNotEmpty() && mediaServerProviderOfRowId(it.id) == provider } }
  val enabledServers = state.servers.filter { it.enabled }
  val multipleServers = enabledServers.size > 1
  val offline = enabledServers.filter { it.reachability is MediaServerReachability.Offline }
  val problem = enabledServers.firstNotNullOfOrNull { it.problem }
  Box(modifier = Modifier.fillMaxSize()) {
    // The pages sit side by side and nothing clips them, so each page's light is kept on its own page.
    if (ambient) Box(Modifier.fillMaxSize().clipToBounds().then(if (jellyfin) Modifier.jellyfinAmbientGlow() else Modifier.plexAmbientGlow()))
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().padding(top = headerBottom).fadeTopEdge(18.dp),
      contentPadding = PaddingValues(top = 10.dp, bottom = 126.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      offline.forEach { server ->
        item(key = "media-offline-${server.id}") {
          val refused = (server.reachability as? MediaServerReachability.Offline)?.reason == OfflineReason.Unauthorized
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
              .background(Color(0xFFF59E0B).copy(alpha = 0.14f)).clickable(onClick = onOpenSettings).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(Modifier.size(8.dp).background(Color(0xFFF59E0B), CircleShape))
            Text(
              stringResource(
                when {
                  !refused -> R.string.plex_page_server_offline
                  jellyfin -> R.string.jellyfin_page_server_refused
                  else -> R.string.plex_page_server_refused
                },
                server.name,
              ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            )
          }
        }
      }
      if (continueItems.isNotEmpty()) {
        item(key = "media-continue") {
          HomeStrip(
            rowId = "continue", title = stringResource(R.string.plex_page_continue), items = continueItems,
            continueWatchingStyle = continueWatchingStyle, homeCardTextMode = homeCardTextMode, liveLandscapeCards = false,
            watchlistItems = watchlist, onOpen = onOpen, onViewAll = {}, onToggleWatchlist = onToggleWatchlist,
            onMarkWatched = onMarkWatched, onMarkEarlierEpisodesWatched = onMarkEarlierEpisodesWatched, onRestartFromBeginning = onRestartFromBeginning,
            onRemoveFromContinueWatching = onRemoveFromContinueWatching, onPlayContinueWatching = onPlayContinueWatching,
          )
        }
      }
      if (rows.isEmpty() && continueItems.isNotEmpty() && !loading && offline.size < enabledServers.size) {
        // Continue Watching came but the libraries did not: say so, and what the server said.
        item(key = "media-rows-missing") {
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)).clickable(onClick = onRetry)
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(stringResource(R.string.media_server_rows_missing_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.media_server_rows_missing_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            if (problem != null) Text(problem, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
          }
        }
      }
      rows.forEachIndexed { index, row ->
        if (multipleServers && (index == 0 || rows[index - 1].serverId != row.serverId)) {
          item(key = "media-server-${row.serverId}-$index") {
            Text(row.serverName, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accentOf(provider))
          }
        }
        item(key = "media-row-$index-${row.id}") {
          HomeStrip(
            rowId = row.id.ifEmpty { "media-collections-$index" }, title = row.title, items = row.items,
            continueWatchingStyle = continueWatchingStyle, homeCardTextMode = homeCardTextMode, liveLandscapeCards = false,
            watchlistItems = watchlist,
            onOpen = { item -> if (item.type == "collection") onOpenCollection(item) else onOpen(item) },
            onViewAll = { if (row.id.isNotEmpty()) onViewAll(HomeRow(row.id, row.title, row.items)) },
            onToggleWatchlist = onToggleWatchlist, onMarkWatched = onMarkWatched, onMarkEarlierEpisodesWatched = onMarkEarlierEpisodesWatched,
            onRestartFromBeginning = onRestartFromBeginning, onRemoveFromContinueWatching = onRemoveFromContinueWatching, onPlayContinueWatching = onPlayContinueWatching,
          )
        }
      }
      if (rows.isEmpty() && continueItems.isEmpty()) {
        item(key = "media-empty") {
          when {
            loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accentOf(provider)) }
            offline.isNotEmpty() && offline.size == enabledServers.size -> LibraryEmptyState(
              icon = { Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(54.dp)) },
              title = stringResource(if (jellyfin) R.string.jellyfin_page_offline_title else R.string.plex_page_offline_title),
              subtitle = stringResource(R.string.plex_page_offline_note),
            )
            else -> LibraryEmptyState(
              icon = { Icon(if (jellyfin) JellyfinIcons.Mark else PlexIcons.Chevron, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(54.dp)) },
              title = stringResource(R.string.plex_page_empty_title),
              subtitle = stringResource(if (jellyfin) R.string.jellyfin_page_empty_note else R.string.plex_page_empty_note) + (problem?.let { "\n\n$it" } ?: ""),
            )
          }
        }
      }
    }
  }
}

/**
 * Library: Continue Watching and the Watchlist on one page, shown in place of those two tabs while
 * a media server is connected so the navigation keeps its five places. In-progress titles first,
 * then the watchlist; a title in both appears once, under Continue Watching, where it resumes.
 *
 * The section heading rides in the filter row, as Discover does on Search. It reads Continue
 * Watching until the Watchlist heading arrives, and the change is carried by the scroll itself:
 * as the Watchlist heading rises toward the row it slides and shrinks into exactly the row's title
 * position and size, pushing Continue Watching up and out, and docks there. Scrolling back reverses
 * it. Everything here is read while drawing, so the hand-off costs no recomposition per frame.
 */
@Composable
internal fun LibraryTab(
  continueWatching: List<MediaItem>,
  watchlistItems: List<MediaItem>,
  headerStyle: HeaderStyle,
  handoffDevices: List<LinkedTvDevice>,
  onOpen: (MediaItem) -> Unit,
  onPlay: (MediaItem) -> Unit,
  onOpenDetails: (MediaItem) -> Unit,
  onToggleWatchlist: (MediaItem) -> Unit,
  onMarkWatched: (MediaItem) -> Unit,
  onMarkEarlierEpisodesWatched: (MediaItem) -> Unit,
  onRestartFromBeginning: (MediaItem) -> Unit,
  onRemoveFromContinueWatching: (MediaItem) -> Unit,
  onRefreshHandoffDevices: () -> Unit,
  onHandoffContinueWatching: suspend (MediaItem, LinkedTvDevice) -> Result<PlaybackHandoffReceipt>,
) {
  var filter by rememberSaveable { mutableStateOf(MediaFilter.All) }
  var columns by rememberSaveable { mutableStateOf(3) }
  val watchlist = remember(watchlistItems) {
    watchlistItems.sortedWith(compareByDescending<MediaItem> { it.addedAt ?: Long.MIN_VALUE }.thenByDescending { it.updatedAt ?: Long.MIN_VALUE })
  }
  val library = remember(continueWatching, watchlist, filter) { unifiedLibrary(continueWatching.filteredBy(filter), watchlist.filteredBy(filter)) }
  val listState = rememberLazyListState()
  ReportScrollTop { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
  val continueTitle = stringResource(R.string.library_section_continue)
  val watchlistTitle = stringResource(R.string.library_section_watchlist)
  val hasContinue = library.continueWatching.isNotEmpty()
  val hasWatchlist = library.watchlist.isNotEmpty()
  val density = LocalDensity.current
  // How far below the row the incoming heading starts to move in.
  val handoffPx = with(density) { 72.dp.toPx() }
  // Where the filter row and its heading are, and where the Watchlist heading is, in window pixels.
  var rowCenterY by remember { mutableFloatStateOf(Float.NaN) }
  var rowTitleLeft by remember { mutableFloatStateOf(0f) }
  var headingCenterY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
  var headingLeft by remember { mutableFloatStateOf(0f) }
  /** 0 while the Watchlist heading is well below the row, 1 once it has reached it. */
  fun handoff(): Float {
    if (rowCenterY.isNaN() || headingCenterY == Float.POSITIVE_INFINITY) return 0f
    return (1f - (headingCenterY - rowCenterY) / handoffPx).coerceIn(0f, 1f)
  }
  val pinnedTitle by remember(hasContinue, hasWatchlist, continueTitle, watchlistTitle) {
    derivedStateOf {
      when {
        !hasContinue && !hasWatchlist -> null
        !hasContinue -> watchlistTitle
        !hasWatchlist -> continueTitle
        else -> {
          val info = listState.layoutInfo
          val headingShown = info.visibleItemsInfo.any { it.key == WatchlistHeadingKey }
          val reached = if (headingShown) headingCenterY <= rowCenterY
            // Off screen: past it if the Watchlist grid is what is showing at the top.
            else info.visibleItemsInfo.firstOrNull()?.key == WatchlistGridKey
          if (reached) watchlistTitle else continueTitle
        }
      }
    }
  }
  LibraryPage(
    title = stringResource(R.string.nav_library),
    count = library.continueWatching.size + library.watchlist.size,
    selectedFilter = filter,
    onFilterChange = { filter = it },
    columns = columns,
    onToggleColumns = { columns = if (columns == 3) 2 else 3 },
    style = headerStyle,
    listState = listState,
    trailingAction = null,
    pinnedTitle = pinnedTitle,
    // Only Continue Watching is pushed out; once Watchlist has docked it stays put.
    pinnedTitleExit = { if (pinnedTitle == continueTitle && hasWatchlist) handoff() else 0f },
    onPinnedRowPlaced = { top, height, titleLeft ->
      rowCenterY = top + height / 2f
      rowTitleLeft = titleLeft
    },
  ) {
    if (library.isEmpty) {
      item(key = "library-empty") {
        LibraryEmptyState(
          icon = { Icon(Icons.Rounded.VideoLibrary, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(54.dp)) },
          title = stringResource(R.string.library_unified_empty), subtitle = stringResource(R.string.library_unified_empty_note),
        )
      }
    }
    // Continue Watching's heading is the filter row's own at rest, so it is not repeated here.
    if (hasContinue) {
      item(key = "library-continue") {
        MediaGrid(
          library.continueWatching, onPlay, columns = columns, onToggleWatchlist = onToggleWatchlist, watchlistItems = watchlistItems,
          onMarkWatched = onMarkWatched, onMarkEarlierEpisodesWatched = onMarkEarlierEpisodesWatched, continueWatchingActions = true,
          onRestartFromBeginning = onRestartFromBeginning, onRemoveFromContinueWatching = onRemoveFromContinueWatching, onOpenDetails = onOpenDetails,
          handoffDevices = handoffDevices, onRefreshHandoffDevices = onRefreshHandoffDevices, onHandoffToTv = onHandoffContinueWatching,
        )
      }
    }
    if (hasWatchlist) {
      // Only when Continue Watching precedes it; alone, the row already names it.
      if (hasContinue) item(key = WatchlistHeadingKey) {
        DockingSectionTitle(
          title = watchlistTitle,
          count = library.watchlist.size,
          progress = ::handoff,
          targetLeft = { rowTitleLeft },
          onPlaced = { centerY, left ->
            headingCenterY = centerY
            headingLeft = left
          },
          startLeft = { headingLeft },
        )
      }
      item(key = WatchlistGridKey) {
        MediaGrid(library.watchlist, onOpen, columns = columns, showMeta = false, onToggleWatchlist = onToggleWatchlist, watchlistItems = watchlistItems, includeRemoveAction = true, onMarkWatched = onMarkWatched)
      }
    }
  }
}

private const val WatchlistHeadingKey = "library-watchlist-title"
private const val WatchlistGridKey = "library-watchlist"

/**
 * A section heading that docks into the pinned filter row.
 *
 * Styled as the row's own heading (22sp bold), so that as [progress] runs to 1 it can slide to the
 * row's heading position ([targetLeft]) and shrink to the row's compact size and be indistinguishable
 * from it at the moment the row takes it over; it is hidden from then on, the row showing it. Its
 * count fades on the way, since the row's heading carries none. Position is reported from outside
 * the moving layer, so the movement never feeds back into what is measured.
 */
@Composable
private fun DockingSectionTitle(
  title: String,
  count: Int,
  progress: () -> Float,
  targetLeft: () -> Float,
  startLeft: () -> Float,
  onPlaced: (centerY: Float, left: Float) -> Unit,
) {
  val motionless = LocalMotionSettings.current.motionless
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      title,
      style = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 1,
      modifier = Modifier
        .onGloballyPositioned { c -> onPlaced(c.positionInWindow().y + c.size.height / 2f, c.positionInWindow().x) }
        .graphicsLayer {
          val t = progress()
          alpha = if (t >= 1f) 0f else 1f
          if (!motionless) {
            val scale = 1f - (1f - DockedTitleScale) * t
            scaleX = scale
            scaleY = scale
            translationX = (targetLeft() - startLeft()) * t
          }
          transformOrigin = TransformOrigin(0f, 0.5f)
        },
    )
    Text(
      count.toString(),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
      modifier = Modifier.graphicsLayer { alpha = 1f - progress() },
    )
  }
}

/** The pinned row's compact heading scale; see PinnedSectionChrome. */
private const val DockedTitleScale = 0.82f

/** The server's mark in one of its lists' search field, before the search icon, so the list reads as that server's. */
@Composable
internal fun PlexSearchBadge(provider: String = PLEX_PROVIDER_ID) {
  if (provider == JELLYFIN_PROVIDER_ID) {
    Image(
      painterResource(R.drawable.jellyfin_logo),
      contentDescription = stringResource(R.string.jellyfin_search_badge),
      modifier = Modifier.size(20.dp),
    )
  } else {
    Image(
      painterResource(R.drawable.plex_logo),
      contentDescription = stringResource(R.string.plex_search_badge),
      modifier = Modifier.size(22.dp).clip(CircleShape),
    )
  }
}

/**
 * Critics' reviews of a Plex title, from the viewer's own server: a second row under the Trakt
 * comments, in the same cards. A review with a link opens it in full outside the app.
 */
@Composable
internal fun PlexReviewsSection(reviews: List<net.streamdek.mobile.nativeapp.mediaserver.MediaServerReview>) {
  val context = androidx.compose.ui.platform.LocalContext.current
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(R.string.plex_reviews_title),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Black,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.padding(horizontal = 24.dp),
    )
    androidx.compose.foundation.lazy.LazyRow(
      contentPadding = PaddingValues(horizontal = 24.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      items(reviews.size) { index ->
        val review = reviews[index]
        PlexReviewCard(review = review, onOpen = review.link?.let { link ->
          {
            runCatching {
              context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                  .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
              )
            }
          }
        })
      }
    }
  }
}

@Composable
private fun PlexReviewCard(review: net.streamdek.mobile.nativeapp.mediaserver.MediaServerReview, onOpen: (() -> Unit)?) {
  val ink = MaterialTheme.colorScheme.onBackground
  Column(
    modifier = Modifier
      .width(310.dp)
      .height(206.dp)
      .clip(StreamDekRadius.panelShape)
      .background(ink.copy(alpha = 0.07f))
      .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
      .padding(18.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
          Text(review.author, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          review.publication?.let {
            Text(it, color = ink.copy(alpha = 0.62f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
        review.positive?.let { fresh ->
          Box(modifier = Modifier.clip(StreamDekRadius.pill).background(ink.copy(alpha = if (fresh) 0.16f else 0.08f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
              stringResource(if (fresh) R.string.plex_review_fresh else R.string.plex_review_rotten),
              color = ink.copy(alpha = 0.80f),
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
      Text(review.text, color = ink.copy(alpha = 0.76f), fontSize = 15.sp, lineHeight = 22.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
    if (onOpen != null) {
      Text(stringResource(R.string.plex_review_read), color = ink.copy(alpha = 0.70f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
  }
}

