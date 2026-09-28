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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReachability
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
 * The Plex page's colour wash: purple, blue, red and green fields of light drifting slowly behind
 * the page, under the header's glass so the blur picks them up. Quieter on a light theme, and still
 * when the app's reduced-motion setting is on.
 */
@Composable
private fun PlexAmbientBackground(modifier: Modifier = Modifier) {
  val motionless = LocalMotionSettings.current.motionless
  val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val strength = if (dark) 0.34f else 0.18f
  val drift = if (motionless) {
    0.5f
  } else {
    val transition = rememberInfiniteTransition(label = "plexAmbient")
    val value by transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing), RepeatMode.Reverse),
      label = "plexAmbientDrift",
    )
    value
  }
  val purple = Color(0xFF8B5CF6)
  val blue = Color(0xFF3B82F6)
  val red = Color(0xFFEF4444)
  val green = Color(0xFF22C55E)
  Box(
    modifier = modifier.fillMaxSize().drawBehind {
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
      glow(purple, 0.10f + 0.10f * drift, 0.06f + 0.05f * drift, 1.05f)
      glow(blue, 0.92f - 0.08f * drift, 0.12f + 0.08f * drift)
      glow(red, 0.18f + 0.06f * drift, 0.58f - 0.07f * drift, 0.9f)
      glow(green, 0.86f - 0.10f * drift, 0.78f - 0.05f * drift, 0.95f)
    },
  )
}

/**
 * The Plex page: the viewer's own library, in StreamDek's look.
 *
 * Plex's Continue Watching first, then each enabled library's Recently Added, the libraries and
 * their collections, grouped under their server when there is more than one. The header condenses
 * into a floating glass pill as the page scrolls, with the same glass as Search. A server that is
 * away says so in one compact line rather than emptying the page, and the page keeps the rows it
 * had while it reloads, so coming back never shows it blank or jumps the scroll.
 */
@Composable
internal fun PlexTab(
  state: MediaServerUiState,
  serverContinueWatching: List<MediaServerResume>,
  pageRows: List<MediaServerRow>,
  pageLoading: Boolean,
  continueWatchingStyle: ContinueWatchingStyle,
  homeCardTextMode: HomeCardTextMode,
  watchlist: List<MediaItem>,
  headerStyle: HeaderStyle,
  ambient: Boolean,
  onLoad: (Boolean) -> Unit,
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
) {
  LaunchedEffect(state.linked) { if (state.linked) onLoad(false) }
  val listState = rememberLazyListState()
  ReportScrollTop { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
  val continueItems = remember(serverContinueWatching) {
    serverContinueWatching.sortedByDescending { it.lastViewedAtMs }.map { it.item }
  }
  val rows = remember(pageRows) { pageRows.filter { it.items.isNotEmpty() } }
  val enabledServers = state.servers.filter { it.enabled }
  val multipleServers = enabledServers.size > 1
  val offline = enabledServers.filter { it.reachability is MediaServerReachability.Offline }
  val loading = pageLoading || state.refreshing
  val density = LocalDensity.current
  val hazeState = rememberHazeState()
  val headerScope = remember { ScrollAwareHeaderScope() }
  val condensePx = with(density) { CondenseDistance.toPx() }
  var headerHeight by remember { mutableStateOf(120.dp) }

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // The glass samples everything in here, the colour wash included.
    Box(modifier = Modifier.fillMaxSize().glassSource(hazeState)) {
      if (ambient) PlexAmbientBackground()
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = headerHeight + 8.dp, bottom = 126.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        offline.forEach { server ->
          item(key = "plex-offline-${server.id}") {
            val refused = (server.reachability as? MediaServerReachability.Offline)?.reason == OfflineReason.Unauthorized
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF59E0B).copy(alpha = 0.14f)).clickable(onClick = onOpenSettings).padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(Modifier.size(8.dp).background(Color(0xFFF59E0B), CircleShape))
              Text(
                stringResource(if (refused) R.string.plex_page_server_refused else R.string.plex_page_server_offline, server.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
              )
            }
          }
        }
        if (continueItems.isNotEmpty()) {
          item(key = "plex-continue") {
            HomeStrip(
              rowId = "continue", title = stringResource(R.string.plex_page_continue), items = continueItems,
              continueWatchingStyle = continueWatchingStyle, homeCardTextMode = homeCardTextMode, liveLandscapeCards = false,
              watchlistItems = watchlist, onOpen = onOpen, onViewAll = {}, onToggleWatchlist = onToggleWatchlist,
              onMarkWatched = onMarkWatched, onMarkEarlierEpisodesWatched = onMarkEarlierEpisodesWatched, onRestartFromBeginning = onRestartFromBeginning,
              onRemoveFromContinueWatching = onRemoveFromContinueWatching, onPlayContinueWatching = onPlayContinueWatching,
            )
          }
        }
        rows.forEachIndexed { index, row ->
          if (multipleServers && (index == 0 || rows[index - 1].serverId != row.serverId)) {
            item(key = "plex-server-${row.serverId}-$index") {
              Text(row.serverName, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PlexGold)
            }
          }
          item(key = "plex-row-$index-${row.id}") {
            HomeStrip(
              rowId = row.id.ifEmpty { "plex-collections-$index" }, title = row.title, items = row.items,
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
          item(key = "plex-empty") {
            when {
              loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PlexGold) }
              offline.isNotEmpty() && offline.size == enabledServers.size -> LibraryEmptyState(
                icon = { Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(54.dp)) },
                title = stringResource(R.string.plex_page_offline_title), subtitle = stringResource(R.string.plex_page_offline_note),
              )
              else -> LibraryEmptyState(
                icon = { Icon(PlexIcons.Chevron, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(54.dp)) },
                title = stringResource(R.string.plex_page_empty_title), subtitle = stringResource(R.string.plex_page_empty_note),
              )
            }
          }
        }
      }
    }
    if (!modernHeaderHeldFixed(headerStyle == HeaderStyle.Modern)) ChromeStatusBarScrim(modifier = Modifier.align(Alignment.TopCenter).zIndex(4f))
    // Glass in both header styles: this page's header floats over its own colour, as Search's does.
    ScrollAwareHeader(
      surface = ScrollAwareHeaderSurface.Glass(hazeState),
      modifier = Modifier.align(Alignment.TopCenter).zIndex(5f).fillMaxWidth().statusBarsPadding()
        .onSizeChanged { size -> headerHeight = maxOf(headerHeight, with(density) { size.height.toDp() }) },
      keepAnchorVisible = true,
      panelPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
      anchorPaddingHorizontal = 12.dp,
      anchorPaddingVertical = 8.dp,
      headerScope = headerScope,
      fractionOverride = { listState.condenseProgress(condensePx) },
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().compactAnchor(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Image(painterResource(R.drawable.plex_logo), contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape))
        Column(Modifier.weight(1f)) {
          Text(stringResource(R.string.media_server_plex), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
          val subtitle = listOfNotNull(state.accountName, enabledServers.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.name }).joinToString(" · ")
          if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loading) {
          CircularProgressIndicator(modifier = Modifier.padding(horizontal = 12.dp).size(18.dp), strokeWidth = 2.dp, color = PlexGold)
        } else {
          IconButton(onClick = { onLoad(true) }) { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.plex_refresh), tint = MaterialTheme.colorScheme.onBackground) }
        }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.plex_manage), tint = MaterialTheme.colorScheme.onBackground) }
      }
    }
  }
}

/**
 * Library: Continue Watching and the Watchlist on one page, shown in place of those two tabs while
 * a media server is connected so the navigation keeps its five places. In-progress titles first,
 * then the watchlist; a title in both appears once, under Continue Watching, where it resumes.
 *
 * The section heading rides in the filter row, as Discover does on Search: it reads Continue
 * Watching until the Watchlist heading scrolls up to the row, then Watchlist.
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
  val switchPx = with(LocalDensity.current) { 24.dp.roundToPx() }
  // Derived, so scrolling recomposes the row only on the frame the heading changes.
  val pinnedTitle by remember(hasContinue, hasWatchlist, continueTitle, watchlistTitle) {
    derivedStateOf {
      when {
        !hasContinue && !hasWatchlist -> null
        !hasContinue -> watchlistTitle
        !hasWatchlist -> continueTitle
        else -> {
          val info = listState.layoutInfo
          val heading = info.visibleItemsInfo.firstOrNull { it.key == WatchlistHeadingKey }
          val reached = when {
            heading != null -> heading.offset <= switchPx
            // Off screen: past it if anything after it is showing at the top.
            else -> info.visibleItemsInfo.firstOrNull()?.key == WatchlistGridKey
          }
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
      if (hasContinue) item(key = WatchlistHeadingKey) { LibrarySectionTitle(watchlistTitle, library.watchlist.size) }
      item(key = WatchlistGridKey) {
        MediaGrid(library.watchlist, onOpen, columns = columns, showMeta = false, onToggleWatchlist = onToggleWatchlist, watchlistItems = watchlistItems, includeRemoveAction = true, onMarkWatched = onMarkWatched)
      }
    }
  }
}

private const val WatchlistHeadingKey = "library-watchlist-title"
private const val WatchlistGridKey = "library-watchlist"

@Composable
private fun LibrarySectionTitle(title: String, count: Int) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Text(count.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
  }
}

/** The Plex mark in a Plex list's search field, before the search icon, so the list reads as Plex's. */
@Composable
internal fun PlexSearchBadge() {
  Image(
    painterResource(R.drawable.plex_logo),
    contentDescription = stringResource(R.string.plex_search_badge),
    modifier = Modifier.size(22.dp).clip(CircleShape),
  )
}
