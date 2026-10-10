package net.streamdek.mobile.nativeapp

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.streamdek.mobile.R

private enum class BrowseItemKind(
  @PluralsRes val countRes: Int,
  @PluralsRes val readingCountRes: Int,
  @StringRes val readingRes: Int,
  @StringRes val loadMoreRes: Int,
) {
  Titles(R.plurals.browse_count_titles, R.plurals.browse_reading_count_titles, R.string.browse_reading_titles, R.string.browse_load_more_titles),
  Channels(R.plurals.browse_count_channels, R.plurals.browse_reading_count_channels, R.string.browse_reading_channels, R.string.browse_load_more_channels),
  Episodes(R.plurals.browse_count_episodes, R.plurals.browse_reading_count_episodes, R.string.browse_reading_episodes, R.string.browse_load_more_episodes),
}

@Composable
internal fun BrowseSectionScreen(row: HomeRow, loadedItems: List<MediaItem>, returnItemId: String?, headerStyle: HeaderStyle, lastWatchedChannel: MediaItem? = null, networkCardStyle: NetworkCardStyle = NetworkCardStyle.Branded, liveLandscapeCards: Boolean, categoriesEnabled: Boolean = true, watchlistItems: List<MediaItem>, favouriteItems: List<MediaItem> = emptyList(), addons: List<InstalledAddon> = emptyList(), handoffDevices: List<LinkedTvDevice> = emptyList(), onRefreshHandoffDevices: () -> Unit = {}, onHandoffLive: suspend (MediaItem, LinkedTvDevice) -> Result<PlaybackHandoffReceipt> = { _, _ -> Result.failure(IllegalStateException("Handoff is unavailable.")) }, onBack: () -> Unit, onOpen: (MediaItem) -> Unit, onToggleWatchlist: (MediaItem) -> Unit, onToggleFavourite: (MediaItem) -> Unit = {}, onClearFavourites: () -> Unit = {}, onEnableAddon: (InstalledAddon) -> Unit = {}, onMarkWatched: (MediaItem) -> Unit, pageableRowIds: Set<String> = emptySet(), onLoadMore: (suspend (String, MediaItem?, Int) -> List<MediaItem>)? = null, plexAmbient: Boolean = false) {
  fun isFavourite(item: MediaItem): Boolean = favouriteItems.hasFavouriteChannel(item)
  var filter by rememberSaveable(row.id) { mutableStateOf(MediaFilter.All) }
  var layout by rememberSaveable(row.id) { mutableStateOf(BrowseLayout.Cards3) }
  var actionItem by remember { mutableStateOf<MediaItem?>(null) }
  var disabledAddonPrompt by remember { mutableStateOf<InstalledAddon?>(null) }
  var showClearFavouritesConfirm by rememberSaveable(row.id) { mutableStateOf(false) }
  val isFavouritesRow = row.id == "favourites"
  fun addonFor(item: MediaItem): InstalledAddon? = item.sourceAddonId?.let { id -> addons.firstOrNull { it.id == id } }
  fun handleOpen(item: MediaItem) {
    val addon = if (isFavouritesRow) addonFor(item) else null
    if (addon != null && !addon.enabled) disabledAddonPrompt = addon else onOpen(item)
  }
  val browseHazeState = rememberHazeState()
  val modernHeader = headerStyle == HeaderStyle.Modern
  val showStatusBarScrim = !isMediaServerBrowseRowId(row.id)
  val isM3uRow = row.id.startsWith("m3u_playlists_")
  // Both of these decide what kind of row this is, and both used to scan the entire catalogue on
  // every recomposition — isLiveCatalogItem() compiles regexes, so on a 200k-channel playlist VOD
  // row (where the id and title checks do not short-circuit) that was enough to stall composition
  // on its own. The answer cannot change for a given row, and for a catalogue that is homogeneous
  // by construction a sample off the front is as good an answer as the full sweep.
  val isLiveRow = remember(row.id, row.title, row.items.size) {
    row.id == "m3u_playlists_live" || row.title.contains("live", true) || row.title.contains("sport", true) || isLiveCatalogRowId(row.id) ||
      row.items.asSequence().take(BROWSE_ROW_KIND_SAMPLE).any(MediaItem::isLiveCatalogItem)
  }
  val isNetworkRow = remember(row.id, row.items.size) {
    row.id == "streaming_networks" ||
      (!isM3uRow && row.items.asSequence().take(BROWSE_ROW_KIND_SAMPLE).any { it.type == "network" })
  }
  // Live channels get their own page. A grid of posters is the wrong shape for a channel list --
  // most carry no artwork, and what a viewer does here is search, resume or star rather than
  // browse pictures. See LiveChannelsBrowseScreen.
  if (isLiveRow || isM3uRow || isFavouritesRow) {
    LiveChannelsBrowseScreen(
      title = row.title,
      items = loadedItems,
      favouriteItems = favouriteItems,
      categoriesEnabled = categoriesEnabled,
      isFavouritesRow = isFavouritesRow,
      lastWatched = lastWatchedChannel,
      modernHeader = modernHeader,
      liveLandscapeCards = liveLandscapeCards,
      onBack = onBack,
      onOpen = ::handleOpen,
      onToggleFavourite = onToggleFavourite,
      onClearFavourites = if (isFavouritesRow) onClearFavourites else null,
    )
    return
  }
  // The shape the row's add-on asked for, read the same way Home reads it so a landscape row opens
  // onto a landscape grid. New Episodes browses as posters, as it always has.
  val addonShape = remember(row.id, row.items.size) {
    val declared = dominantPosterShape(row.items.asSequence().take(BROWSE_ROW_KIND_SAMPLE).map(MediaItem::posterShape).asIterable())
    resolveRowPosterShape(if (row.id == "new-episodes") PosterShape.Poster else null, declared, PosterShape.Poster)
  }
  val landscapeArtwork = ((isLiveRow || isLiveCatalogRowId(row.id)) && liveLandscapeCards) || isNetworkRow || addonShape == PosterShape.Landscape
  val showsList = layout == BrowseLayout.List
  // Landscape artwork is unreadable three across, so those rows toggle straight between their
  // card grid and the text list.
  fun nextLayout(): BrowseLayout = when {
    showsList -> BrowseLayout.Cards3
    landscapeArtwork -> BrowseLayout.List
    layout == BrowseLayout.Cards3 -> BrowseLayout.Cards2
    else -> BrowseLayout.List
  }
  // The chosen density, before the window has its say.
  val compactColumns = when {
    showsList -> 1
    landscapeArtwork -> 2
    layout == BrowseLayout.Cards2 -> 2
    else -> 3
  }
  // A text list stays one column at any width — its rows are full-width by nature, and the
  // container's maximum readable width is what keeps them from stretching.
  val adaptiveColumns = if (showsList) 1 else adaptiveMediaColumns(compactColumns, landscapeArtwork)
  fun contentColumns(): Int = adaptiveColumns
  var query by rememberSaveable(row.id) { mutableStateOf("") }
  var browseSort by rememberSaveable(row.id) { mutableStateOf(BrowseSort.Original) }
  // Live TV and playlist catalogs browse as categories first, channels second — a flat list of
  // tens of thousands of channels is unusable on a phone. Everything else keeps the flat grid.
  val supportsCategories = categoriesEnabled && (isM3uRow || isLiveRow)
  var selectedCategory by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
  var selectedSource by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
  var categories by remember(row.id) { mutableStateOf<List<BrowseCategory>>(emptyList()) }
  var sourceNames by remember(row.id) { mutableStateOf<List<String>>(emptyList()) }
  var scopedItems by remember(row.id) { mutableStateOf(loadedItems) }
  var categorizing by remember(row.id) { mutableStateOf(false) }
  // "View All" starts from whatever the home row already fetched, then pages in more from the
  // same add-on catalog as the user scrolls — most add-on catalogs only return a handful of
  // items per request so large add-on catalogs are fetched incrementally.
  var isLoadingMore by remember(row.id) { mutableStateOf(false) }
  var canLoadMore by remember(row.id) {
    mutableStateOf(
      onLoadMore != null && !isM3uRow && row.items.isNotEmpty() &&
        (
          row.items.last().let { it.sourceAddonId != null && it.sourceCatalogType != null && it.sourceCatalogId != null } ||
            row.id in pageableRowIds
        ),
    )
  }
  // M3U parser IDs are already unique; do not allocate another full list just to deduplicate a
  // potentially huge playlist. Other rows keep their existing defensive de-duplication.
  val uniqueItems = remember(row.id, loadedItems) { if (isM3uRow) loadedItems else loadedItems.distinctBy(::mediaCollectionKey) }
  val hasMovies = remember(uniqueItems, isM3uRow) { !isM3uRow && uniqueItems.any { it.type.equals("movie", true) } }
  val hasSeries = remember(uniqueItems, isM3uRow) { !isM3uRow && uniqueItems.any { it.type.equals("tv", true) || it.type.equals("series", true) } }
  val showsTypeFilters = hasMovies && hasSeries
  // Every View all list offers search, so every one of them gets the same header: the title row
  // condensing away, the search field pinned with the layout button beside it. A list that decided
  // for itself — search only past twenty-four titles, or once it paged — left short lists such as
  // Trending On Trakt and Recommended For You with a header of their own that simply slid off.
  // Filtering and sorting run on Default rather than the UI thread so very large IPTV lists remain
  // responsive.
  val showSearch = true
  var filteredItems by remember(row.id) { mutableStateOf<List<MediaItem>>(emptyList()) }
  // Grouping a 50k-channel playlist is far too much work for the main thread, so categories are
  // built on Default alongside the search/sort pass below.
  LaunchedEffect(uniqueItems, supportsCategories, selectedSource) {
    if (!supportsCategories) {
      categories = emptyList()
      sourceNames = emptyList()
      scopedItems = uniqueItems
      categorizing = false
      return@LaunchedEffect
    }
    // Sorting the same catalogue again on every visit is wasted work the user has to watch, so a
    // page that has already been sorted comes straight back from the cache without the notice.
    val cacheKey = BrowseCategoryCache.key(row.id, selectedSource, uniqueItems)
    val computed = BrowseCategoryCache.get(cacheKey) ?: run {
      categorizing = true
      withContext(Dispatchers.Default) {
        val sources = uniqueItems.mapNotNull { it.sourceAddonName?.takeIf(String::isNotBlank) }.distinct()
        val scoped = selectedSource
          ?.takeIf { name -> sources.any { it == name } }
          ?.let { name -> uniqueItems.filter { it.sourceAddonName == name } }
          ?: uniqueItems
        BrowseCategoryResult(sources, scoped, buildBrowseCategories(scoped))
      }.also { BrowseCategoryCache.put(cacheKey, it) }
    }
    sourceNames = computed.sourceNames
    scopedItems = computed.scopedItems
    categories = computed.categories
    // A playlist can be removed while its "View All" page is open; don't leave the picker
    // pointing at a source that is no longer there.
    if (selectedSource != null && sourceNames.none { it == selectedSource }) selectedSource = null
    categorizing = false
  }
  // What the search/sort pass runs over: one category's channels once the user has drilled in,
  // otherwise everything currently in scope.
  val baseItems = when {
    !supportsCategories -> uniqueItems
    selectedCategory != null -> categories.firstOrNull { it.name == selectedCategory }?.items.orEmpty()
    else -> scopedItems
  }
  LaunchedEffect(baseItems, filter, showsTypeFilters, query, browseSort) {
    filteredItems = withContext(Dispatchers.Default) {
      val typeFiltered = if (showsTypeFilters) baseItems.filteredBy(filter) else baseItems
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
  // Typing in the search box drops straight to matching channels across the whole catalog, which
  // is how an IPTV app behaves — categories are a browsing aid, not a wall in front of search.
  val showCategoryGrid = supportsCategories && selectedCategory == null && query.isBlank() && categories.size >= 2
  // Held back briefly so a playlist small enough to group instantly doesn't flash a progress
  // notice for two frames. Only the category level shows it; drilling in has nothing to wait for.
  var showCategorizingNotice by remember(row.id) { mutableStateOf(false) }
  LaunchedEffect(categorizing, selectedCategory, query) {
    if (!categorizing || selectedCategory != null || query.isNotBlank()) {
      showCategorizingNotice = false
      return@LaunchedEffect
    }
    delay(180)
    showCategorizingNotice = true
  }
  val showHeaderFilters = !isNetworkRow && showsTypeFilters && !showCategoryGrid
  val searchExtra = if (showSearch) 72.dp else 0.dp
  val modernHeaderHeight = (if (showHeaderFilters) 140.dp else 100.dp) + searchExtra
  val modernContentTop = (if (showHeaderFilters) 210.dp else 170.dp) + searchExtra
  val classicContentTop = (if (showHeaderFilters) 202.dp else 152.dp) + searchExtra
  val clearFavouritesAction: (() -> Unit)? =
    if (isFavouritesRow && uniqueItems.isNotEmpty()) ({ showClearFavouritesConfirm = true }) else null
  if (showClearFavouritesConfirm) {
    AlertDialog(
      onDismissRequest = { showClearFavouritesConfirm = false },
      title = { Text(stringResource(R.string.live_clear_favourites_title_caps)) },
      text = { Text(pluralStringResource(R.plurals.live_clear_favourites_detail_undone, uniqueItems.size, uniqueItems.size)) },
      confirmButton = { Button(onClick = { showClearFavouritesConfirm = false; onClearFavourites() }) { Text(stringResource(R.string.live_clear_all)) } },
      dismissButton = { TextButton(onClick = { showClearFavouritesConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
  // Inside a category, back steps up to the category list rather than leaving the page.
  BackHandler(onBack = { if (selectedCategory != null) selectedCategory = null else onBack() })

  val gridState = rememberLazyGridState()
  // Match Library's 96dp collapse by absolute position: reversing direction mid-list
  // keeps media-server headers compact, including after returning from a title.
  val browseCollapseDistance = with(LocalDensity.current) { 96.dp.toPx() }
  val browseHeaderFraction: (() -> Float)? = if (isMediaServerBrowseRowId(row.id)) ({
    val progress = if (gridState.firstVisibleItemIndex > 0) 1f
      else (gridState.firstVisibleItemScrollOffset / browseCollapseDistance).coerceIn(0f, 1f)
    progress * ScrollChromeMachine.COMPACT
  }) else null
  LaunchedEffect(returnItemId, filteredItems.size, query, browseSort) {
    val targetId = returnItemId ?: return@LaunchedEffect
    val targetIndex = withContext(Dispatchers.Default) { filteredItems.indexOfFirst { it.id == targetId } }
    if (targetIndex >= 0) gridState.scrollToItem(targetIndex)
  }
  // Changing level resets the scroll, but only on a real change: the initial value matches the
  // restored selection so returning from a channel keeps the position the grid state saved.
  var lastLevelKey by remember(row.id) { mutableStateOf(selectedCategory to selectedSource) }
  LaunchedEffect(selectedCategory, selectedSource) {
    val levelKey = selectedCategory to selectedSource
    if (levelKey != lastLevelKey) {
      lastLevelKey = levelKey
      gridState.scrollToItem(0)
    }
  }
  // Only auto-page while the user is looking at the unfiltered list: once "skip" no longer
  // lines up with what's on screen (a search/type filter is active), fetching more would just
  // silently re-request items already loaded.
  // The category screen shows categories, not channels, so "scrolled near the last channel"
  // means nothing there — it offers an explicit "Load more channels" footer instead.
  val canAutoPage = canLoadMore && filter == MediaFilter.All && query.isBlank() &&
    browseSort == BrowseSort.Original && !showCategoryGrid
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
  // Shared by the scroll-driven auto-page below and the category screen's explicit footer button.
  suspend fun fetchNextPage() {
    if (isLoadingMore || !canLoadMore) return
    val anchor = row.items.lastOrNull() ?: return
    val loader = onLoadMore ?: return
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
  // isLoadingMore is intentionally NOT a key here: it's mutated inside fetchNextPage, and a
  // LaunchedEffect cancels-and-relaunches itself the instant one of its own keys changes. With
  // it listed as a key, setting it to true immediately restarted this effect, which cancelled
  // the in-flight fetch before it could ever finish and left loading stuck forever — the exact
  // "never loads more" bug. It only needs to be checked (as a re-entrancy guard), not restarted on.
  LaunchedEffect(row.id, nearEnd, canAutoPage) {
    android.util.Log.d("StreamDekPaging", "effect check row=${row.id} nearEnd=$nearEnd canAutoPage=$canAutoPage isLoadingMore=$isLoadingMore canLoadMore=$canLoadMore items=${uniqueItems.size}")
    if (!nearEnd || !canAutoPage) return@LaunchedEffect
    fetchNextPage()
  }
  val pagingScope = rememberCoroutineScope()
  val gridColumns = if (showCategoryGrid) 2 else contentColumns()
  // Playlist VOD entries carry a direct stream URL, which makes isLiveRow true for them as well —
  // they are still titles, not channels.
  val itemKind = when {
    row.id == "new-episodes" -> BrowseItemKind.Episodes
    isLiveRow && row.id != "m3u_playlists_vod" -> BrowseItemKind.Channels
    else -> BrowseItemKind.Titles
  }
  val headerTitle = selectedCategory ?: row.title
  // A list from the viewer's own Plex server says so, in its search field, where it stays in view.
  val browseProvider = mediaServerProviderOfRowId(row.id)
  val plexSearchBadge: (@Composable () -> Unit)? = if (isMediaServerBrowseRowId(row.id)) ({ PlexSearchBadge(browseProvider ?: net.streamdek.mobile.nativeapp.mediaserver.PLEX_PROVIDER_ID) }) else null
  // A media server list's header is clear at the very top, so the page and its colour wash show
  // through; once the list scrolls a little it is the ordinary scroll-aware header.
  val clearHeaderPx = with(LocalDensity.current) { 24.dp.toPx() }
  val mediaServerHeaderFade: (() -> Float)? = if (isMediaServerBrowseRowId(row.id)) ({
    if (gridState.firstVisibleItemIndex > 0) 1f else (gridState.firstVisibleItemScrollOffset / clearHeaderPx).coerceIn(0f, 1f)
  }) else null
  val headerCount = when {
    showCategoryGrid -> stringResource(
      R.string.browse_categories_and_items,
      categories.size,
      pluralStringResource(itemKind.countRes, scopedItems.size, scopedItems.size.formattedItemCount()),
    )
    isNetworkRow -> pluralStringResource(R.plurals.browse_count_networks, filteredItems.size, filteredItems.size.formattedItemCount())
    else -> pluralStringResource(itemKind.countRes, filteredItems.size, filteredItems.size.formattedItemCount())
  }
  val headerUpAction: (() -> Unit)? = if (selectedCategory != null) ({ selectedCategory = null }) else null

  // Landscape on a tablet has width to spare and very little height, so the header stops being a
  // band across the top and becomes a column down the side: the title, filters and search sit
  // beside the results instead of eating the first third of a short window. Portrait keeps the
  // banner exactly as it is, and no phone reaches this.
  // Only the frosted header has been moved into a column so far; the classic header still draws as
  // a band across the top, and shifting the grid across for it would leave an empty gutter beside a
  // full-width header.
  val sideHeader = LocalWindowSize.current.supportsTwoPanes && modernHeader
  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyVerticalGrid(
      state = gridState,
      columns = GridCells.Fixed(gridColumns),
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .glassSource(browseHazeState)
        // A Plex list wears the Plex page's colour wash when that is switched on, inside the glass
        // source so the header's glass carries it too.
        .then(
          when {
            !plexAmbient || !isMediaServerBrowseRowId(row.id) -> Modifier
            else -> Modifier.mediaServerAmbientGlow(browseProvider)
          },
        ),
      contentPadding = run {
        val sideMargin = if (showsList) 20.dp else MediaGridSideMargin
        if (sideHeader) {
          PaddingValues(start = BrowseSideHeaderWidth + sideMargin, end = sideMargin, top = 20.dp, bottom = 126.dp)
        } else {
          PaddingValues(start = sideMargin, end = sideMargin, top = if (modernHeader && !modernHeaderHeldFixed(modernHeader)) modernContentTop else classicContentTop, bottom = 126.dp)
        }
      },
      horizontalArrangement = Arrangement.spacedBy(LocalStreamDekSpacing.current.gridGap),
      verticalArrangement = Arrangement.spacedBy(if (showsList) 6.dp else MediaGridRowGap),
    ) {
      if (showCategorizingNotice) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          BrowseCategorizingNotice(itemCount = uniqueItems.size, itemKind = itemKind)
        }
      }
      if (showCategoryGrid) {
        if (sourceNames.size > 1) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            BrowseSourceChips(sources = sourceNames, selected = selectedSource, onSelect = { selectedSource = it })
          }
        }
        gridItems(categories, key = { "category-${it.name}" }) { category ->
          BrowseCategoryTile(category = category, itemKind = itemKind, onClick = { selectedCategory = category.name })
        }
        if (canLoadMore) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
              if (isLoadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), strokeWidth = 2.5.dp)
              } else {
                TextButton(onClick = { pagingScope.launch { fetchNextPage() } }) {
                  Text(stringResource(itemKind.loadMoreRes), fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      } else if (filteredItems.isEmpty()) {
        // "Nothing here" would be wrong while the grouping pass is still running.
        if (!showCategorizingNotice) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryEmptyState(icon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f), modifier = Modifier.size(54.dp)) }, title = stringResource(R.string.browse_no_titles_here), subtitle = stringResource(R.string.browse_no_titles_here_detail))
          }
        }
      } else {
        gridItems(filteredItems, key = ::mediaCollectionKey) { item ->
          val disabled = isFavouritesRow && addonFor(item)?.enabled == false
          // Long-pressing anywhere on a live row opens the channel menu, so the TV list has to be
          // refreshed for every layout — not just the landscape-card one.
          val openActions: () -> Unit = { actionItem = item; if (isLiveRow) onRefreshHandoffDevices() }
          when {
            showsList -> BrowseListRow(item = item, favourite = isFavourite(item), dimmed = disabled, onClick = { handleOpen(item) }, onLongPress = openActions, networkStyle = if (isNetworkRow) networkCardStyle else null)
            (isLiveRow || isLiveCatalogRowId(row.id)) && liveLandscapeCards -> NetworkHomeCard(item = item, sports = true, modifier = Modifier.fillMaxWidth(), dimmed = disabled, favourite = isFavourite(item), onClick = { handleOpen(item) }, onLongPress = openActions)
            isNetworkRow -> NetworkHomeCard(item = item, sports = false, branded = networkCardStyle == NetworkCardStyle.Branded, modifier = Modifier.fillMaxWidth(), dimmed = disabled, favourite = isFavourite(item), onClick = { handleOpen(item) })
            else -> LibraryPosterTile(item = item, modifier = Modifier.alpha(if (disabled) 0.4f else 1f), showMeta = row.id == "new-episodes", shape = addonShape, favourite = isLiveRow && isFavourite(item), onClick = { handleOpen(item) }, onLongPress = openActions)
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
      if (!sideHeader && showStatusBarScrim) ChromeStatusBarScrim(modifier = Modifier.align(Alignment.TopCenter).zIndex(4f))
      // A side column gives back no vertical room by moving, so it stays put. As a column it also
      // takes the height its own content needs, rather than the fixed band across the top.
      ScrollAwareHeader(
        surface = ScrollAwareHeaderSurface.Glass(browseHazeState),
        modifier = Modifier
          .align(if (sideHeader) Alignment.TopStart else Alignment.TopCenter)
          .zIndex(4f)
          .then(if (sideHeader) Modifier.width(BrowseSideHeaderWidth) else Modifier.fillMaxWidth())
          .statusBarsPadding(),
        enabled = !sideHeader,
        keepAnchorVisible = showSearch,
        fractionOverride = browseHeaderFraction,
        panelPadding = if (sideHeader) {
          PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp)
        } else {
          PaddingValues(start = HeaderSearchInset.modernPanel, end = HeaderSearchInset.modernPanel, top = 12.dp, bottom = 6.dp)
        },
        panelHeight = if (sideHeader) null else modernHeaderHeight,
        contentPadding = PaddingValues(horizontal = HeaderSearchInset.content, vertical = 14.dp),
        // Nothing is pinned beneath this header, so it grounds itself when headers are fixed.
        backdropWhenFixed = !sideHeader,
      ) {
        BrowseSectionHeaderContent(
          title = headerTitle,
          compact = true,
          countLabel = headerCount,
          onUpNavigate = headerUpAction,
          showLayoutToggle = !showCategoryGrid,
          layout = layout,
          onCycleLayout = { layout = nextLayout() },
          selectedFilter = filter,
          showFilters = showHeaderFilters,
          onFilterChange = { filter = it },
          query = query,
          showSearch = showSearch,
          onQueryChange = { query = it },
          showSort = isM3uRow && !showCategoryGrid,
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
          onClearAll = clearFavouritesAction,
          clearAllDescription = "Clear Live Favourites",
          searchBadge = plexSearchBadge,
          layoutButtonHazeState = browseHazeState,
        )
      }
    } else {
      Column(modifier = Modifier.align(Alignment.TopCenter).zIndex(4f).fillMaxWidth()) {
        if (showStatusBarScrim) {
          DefaultHeaderStatusStrip(color = MaterialTheme.colorScheme.background, fadesWithHeader = true, restFade = mediaServerHeaderFade)
        } else {
          Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
        }
        ScrollAwareHeader(
          surface = ScrollAwareHeaderSurface.Solid(MaterialTheme.colorScheme.background, pillAroundAnchor = showSearch, hazeState = browseHazeState, restFade = mediaServerHeaderFade),
          modifier = Modifier.fillMaxWidth(),
          keepAnchorVisible = showSearch,
          fractionOverride = browseHeaderFraction,
          contentPadding = PaddingValues(horizontal = HeaderSearchInset.content, vertical = 12.dp),
        ) {
          BrowseSectionHeaderContent(
          title = headerTitle,
          countLabel = headerCount,
          onUpNavigate = headerUpAction,
          showLayoutToggle = !showCategoryGrid,
          layout = layout,
          onCycleLayout = { layout = nextLayout() },
          selectedFilter = filter,
          showFilters = showHeaderFilters,
          onFilterChange = { filter = it },
          query = query,
          showSearch = showSearch,
          onQueryChange = { query = it },
          showSort = isM3uRow && !showCategoryGrid,
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
          onClearAll = clearFavouritesAction,
          clearAllDescription = "Clear Live Favourites",
          searchBadge = plexSearchBadge,
          layoutButtonHazeState = null,
        )
        }
      }
    }
  }
  actionItem?.let { item ->
    if (isLiveRow) {
      LiveChannelActionsDialog(
        item = item,
        isFavourite = isFavourite(item),
        devices = handoffDevices,
        onToggleFavourite = { onToggleFavourite(item) },
        onRefreshDevices = onRefreshHandoffDevices,
        onHandoff = { device -> onHandoffLive(item, device) },
        onDismiss = { actionItem = null },
      )
    } else {
      MediaCardActionsDialog(
        item = item,
        inWatchlist = watchlistItems.containsMedia(item),
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
      title = { Text(stringResource(R.string.addon_disabled_title)) },
      text = { Text(stringResource(R.string.addon_disabled_detail, addon.manifest.name)) },
      confirmButton = {
        Button(onClick = { onEnableAddon(addon); disabledAddonPrompt = null }) { Text(stringResource(R.string.action_enable)) }
      },
      dismissButton = { TextButton(onClick = { disabledAddonPrompt = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun ScrollAwareHeaderScope.BrowseSectionHeaderContent(
  title: String,
  compact: Boolean = false,
  countLabel: String,
  layout: BrowseLayout,
  onCycleLayout: () -> Unit,
  selectedFilter: MediaFilter,
  showFilters: Boolean,
  onFilterChange: (MediaFilter) -> Unit,
  query: String = "",
  showSearch: Boolean = false,
  onQueryChange: (String) -> Unit = {},
  showSort: Boolean = false,
  sortLabel: String = "",
  onToggleSort: () -> Unit = {},
  onClearAll: (() -> Unit)? = null,
  clearAllDescription: String = "Clear all",
  onUpNavigate: (() -> Unit)? = null,
  showLayoutToggle: Boolean = true,
  layoutButtonHazeState: HazeState? = null,
  searchBadge: (@Composable () -> Unit)? = null,
) {
  // The layout button stays when the rest of the title row goes, coming down beside the search field
  // as it narrows — the same movement as a network's page.
  val joinSearchRow = showSearch && showLayoutToggle
  val goes = if (showSearch) Modifier.compactsAway() else Modifier
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    // With no search field there is nothing to compact down to, so the title row stays opaque and
    // the whole header simply slides away.
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      // Only shown one level deep (inside a category), where back means "up", not "leave".
      if (onUpNavigate != null) {
        GlassCircleButton(modifier = goes.size(if (compact) 48.dp else 52.dp), borderless = true, onClick = onUpNavigate) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.a11y_all_categories), modifier = Modifier.size(if (compact) 22.dp else 24.dp), tint = MaterialTheme.colorScheme.onBackground)
        }
      }
      Column(modifier = Modifier.weight(1f).then(goes), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AdaptivePageTitle(title = title, compact = compact)
        Text(countLabel, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
      }
      if (showSort) {
        TextButton(modifier = goes, onClick = onToggleSort) {
          Text(sortLabel, fontWeight = FontWeight.Bold)
        }
      }
      if (onClearAll != null) {
        GlassCircleButton(modifier = goes.size(if (compact) 48.dp else 52.dp), borderless = true, onClick = onClearAll) {
          Icon(Icons.Rounded.DeleteSweep, contentDescription = clearAllDescription, modifier = Modifier.size(if (compact) 22.dp else 24.dp), tint = MaterialTheme.colorScheme.onBackground)
        }
      }
      if (showLayoutToggle) {
        GlassCircleButton(
          // Without glass (the Default style) it takes the Watchlist page's 48dp treatment.
          modifier = (if (joinSearchRow) Modifier.joinsAnchorRow() else Modifier).size(if (compact || layoutButtonHazeState == null) 48.dp else 52.dp),
          hazeState = layoutButtonHazeState,
          borderless = true,
          onClick = onCycleLayout,
        ) {
          Icon(
            when (layout) {
              BrowseLayout.Cards3 -> Icons.Rounded.ViewAgenda
              BrowseLayout.Cards2 -> Icons.Rounded.ViewModule
              BrowseLayout.List -> Icons.AutoMirrored.Rounded.ViewList
            },
            contentDescription = stringResource(R.string.a11y_change_layout),
            modifier = Modifier.size(if (compact) 22.dp else 24.dp), tint = MaterialTheme.colorScheme.onBackground,
          )
        }
      }
    }
    if (showSearch) {
      HeaderSearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.hint_search_this_list),
        compactPlaceholder = stringResource(R.string.hint_search_named_list, title),
        compactTrailingLabel = countLabel,
        yieldsToJoinedControl = showLayoutToggle,
        leadingBadge = searchBadge,
      )
    }
    if (showFilters) {
      Row(modifier = if (showSearch) Modifier.compactsAway(order = 1, belowAnchor = true) else Modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MediaFilter.values().forEach { option ->
          FilterChip(selected = selectedFilter == option, onClick = { onFilterChange(option) }, label = { Text(stringResource(option.labelRes)) })
        }
      }
    }
  }
}
/**
 * Shown while a catalog is being grouped. Sorting a large IPTV playlist takes a visible moment,
 * and an empty screen in that gap reads as "there is nothing here" rather than "working on it".
 * The sweeping bar and cycling dots are what make it read as progress rather than a frozen frame.
 */
@Composable
private fun BrowseCategorizingNotice(itemCount: Int, itemKind: BrowseItemKind) {
  val transition = rememberInfiniteTransition(label = "categorizing")
  val sweep by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
    label = "sweep",
  )
  val dots by transition.animateFloat(
    initialValue = 0f,
    targetValue = 3.99f,
    animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
    label = "dots",
  )
  val accent = MaterialTheme.colorScheme.onBackground
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(StreamDekRadius.cardShape)
      .background(accent.copy(alpha = 0.05f))
      .padding(horizontal = 18.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accent.copy(alpha = 0.75f), strokeWidth = 2.dp)
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          stringResource(R.string.browse_sorting_into_categories) + ".".repeat(dots.toInt()),
          color = accent,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
        )
        Text(
          if (itemCount > 0) {
            pluralStringResource(itemKind.readingCountRes, itemCount, itemCount.formattedItemCount())
          } else {
            stringResource(itemKind.readingRes)
          },
          color = accent.copy(alpha = 0.55f),
          fontSize = 12.sp,
        )
      }
    }
    // Indeterminate sweep: the grouping pass reports no percentage, so a bar that travels is
    // honest about "still working" without inventing progress it does not know.
    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(StreamDekRadius.pill).background(accent.copy(alpha = 0.10f))) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val trackWidth = maxWidth
        val barWidth = trackWidth * 0.34f
        Box(
          modifier = Modifier
            .offset(x = (trackWidth + barWidth) * sweep - barWidth)
            .width(barWidth)
            .fillMaxHeight()
            .clip(StreamDekRadius.pill)
            .background(accent.copy(alpha = 0.55f)),
        )
      }
    }
  }
}

/**
 * A category on the Live TV / playlist browse screen. Fixed height so a row of tiles stays even
 * whether the category name wraps to one line or two.
 */
@Composable
private fun BrowseCategoryTile(category: BrowseCategory, itemKind: BrowseItemKind, onClick: () -> Unit) {
  val accent = browseCategoryAccent(category.name)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(132.dp)
      .clip(StreamDekRadius.cardShape)
      .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
      .clickable(onClick = onClick)
      .padding(14.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Box(
      modifier = Modifier.size(40.dp).clip(StreamDekRadius.controlShape).background(accent.copy(alpha = 0.18f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(browseCategoryIcon(category.name), contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        category.name,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        pluralStringResource(itemKind.countRes, category.items.size, category.items.size.formattedItemCount()),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        fontSize = 12.sp,
        maxLines = 1,
      )
    }
  }
}

/** Playlist/add-on picker shown above the categories when the row draws on more than one source. */
@Composable
private fun BrowseSourceChips(sources: List<String>, selected: String?, onSelect: (String?) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.browse_all_sources)) })
    sources.forEach { name ->
      FilterChip(
        selected = selected == name,
        onClick = { onSelect(if (selected == name) null else name) },
        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      )
    }
  }
}

