package net.streamdek.mobile.nativeapp

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import net.streamdek.mobile.R

/**
 * The Home rows settings page: which rows appear on Home, in what order, and how that order is read.
 *
 * Its own file rather than another few hundred lines of the main app file, which generates a single
 * class that is already at the JVM's size ceiling - but mostly because this page is one coherent
 * thing: a saved layout, two ways of arranging it, and the drag machinery both ways share. See
 * [HomeRowMode] for the model underneath it.
 */

/**
 * Everything the Home rows page can change, as one object.
 *
 * One argument rather than six, because the settings screen is composed from a call that already
 * builds several dozen method references into a single generated method, and that method is within
 * a few hundred bytes of the JVM's 64KB ceiling. Bundling these here is what keeps adding a setting
 * to this page from being a refactor of the whole settings screen.
 */
@Immutable
internal data class HomeRowsSettingsActions(
  val onDefaultAppCatalogsEnabledChange: (Boolean) -> Unit,
  val onRowEnabledChange: (String, Boolean) -> Unit,
  val onMoveRow: (String, Int) -> Unit,
  val onRemoveRows: (Set<String>) -> Unit,
  val onModeChange: (HomeRowMode) -> Unit,
  val onMoveSource: (String, Int) -> Unit,
)

@Composable
internal fun CatalogHomeLayoutSettings(
  /** The saved layout, in its own order - not the order Home draws, which is derived below. */
  rows: List<HomeCatalogRow>,
  /** What an add-on row's group is named after, and what says whether that add-on is switched off. */
  addons: List<InstalledAddon>,
  defaultAppCatalogsEnabled: Boolean,
  rowMode: HomeRowMode,
  sourceOrder: List<String>,
  actions: HomeRowsSettingsActions,
  dragScrollBy: suspend (Float) -> Float,
) {
  val onDefaultAppCatalogsEnabledChange = actions.onDefaultAppCatalogsEnabledChange
  val onHomeCatalogRowEnabledChange = actions.onRowEnabledChange
  val onMoveHomeCatalogRow = actions.onMoveRow
  val onRemoveHomeCatalogRows = actions.onRemoveRows
  val onHomeRowModeChange = actions.onModeChange
  val onMoveHomeRowSource = actions.onMoveSource
  val density = LocalDensity.current
  val reorderThresholdPx = with(density) { 56.dp.toPx() }
  // Read outside the remember below, which is not a composable, and keyed into it so the grouping
  // re-forms when the interface language changes.
  val orphanGroupTitle = stringResource(R.string.home_row_group_no_longer_installed)
  val fallbackAddonName = stringResource(R.string.home_row_group_unknown_addon)
  // The saved arrangement, held locally so a drag can step through positions at the speed of the
  // finger rather than at the speed of a round trip through the view model.
  var localRows by remember { mutableStateOf(rows) }
  LaunchedEffect(rows) {
    localRows = rows
  }
  var localSourceOrder by remember { mutableStateOf(sourceOrder) }
  LaunchedEffect(sourceOrder) {
    localSourceOrder = sourceOrder
  }
  // Each loaded CloudStream provider's rows go under the plugin that registered it. Worked out on
  // every pass rather than inside the remember below: plugins load after this screen can first be
  // composed, and a map remembered from before they had would leave every provider in a group of
  // its own for as long as the rows themselves did not change.
  val cloudStreamGroups = currentCloudStreamRowGroups()
  // Where each CloudStream plugin comes from ("CloudStream · CNC Repo"), for its group's header.
  // Every provider a plugin registers shares its collection, so any one of them answers for it.
  val cloudStreamGroupLabels = if (!CloudStreamPlugins.isInitialized) {
    emptyMap()
  } else {
    runCatching {
      CloudStreamPluginLoader.loadedPlugins().mapNotNull { plugin ->
        plugin.providers.firstOrNull()
          ?.let { provider -> cloudStreamProviderOriginLabel(provider.name) }
          ?.takeIf { it.isNotBlank() }
          ?.let { label -> "cloudstream-plugin:${plugin.filePath}" to label }
      }.toMap()
    }.getOrDefault(emptyMap())
  }
  // A CloudStream row is offered only while its source is loaded. Switched off, in a collection
  // that is switched off, or failing to load, it has nothing to put on Home, so it is left out of
  // the list — but kept in the saved layout, so turning the source back on brings its rows back
  // exactly as they were. Moving rows still works on the full layout, so their places hold too.
  val offeredRows = remember(localRows, cloudStreamGroups) {
    localRows.filter { row ->
      !isCloudStreamHomeRowId(row.id) || homeCatalogRowAddonId(row.id) in cloudStreamGroups
    }
  }
  // What the screen lists, in the order Home will draw it. In By source that is the grouped view of
  // the saved layout; in Mixed it is the saved layout itself. Either way the list on this screen and
  // the screen it configures are the same order, which is the whole promise of the setting.
  val orderedRows = remember(offeredRows, rowMode, localSourceOrder, cloudStreamGroups) {
    orderedHomeCatalogRows(offeredRows, rowMode, localSourceOrder, cloudStreamGroups)
  }
  val groups = remember(orderedRows, addons, defaultAppCatalogsEnabled, orphanGroupTitle, fallbackAddonName, cloudStreamGroups) {
    buildHomeRowGroups(
      orderedRows,
      addons,
      defaultAppCatalogsEnabled,
      orphanGroupTitle = orphanGroupTitle,
      fallbackAddonName = fallbackAddonName,
      cloudStreamGroups = cloudStreamGroups,
    )
      // Rows whose add-on is gone cannot reach Home whatever order they are in, so they sit at the
      // end rather than in the middle of an arrangement of sources that do.
      .sortedBy { it.key == ORPHAN_ROW_GROUP_KEY }
  }
  var expandedGroups by rememberSaveable { mutableStateOf(emptySet<String>()) }
  // Dropping a whole group's rows in one tap is worth asking about, and the count is the part the
  // viewer needs to see before they agree to it.
  var confirmClearCount by remember { mutableStateOf<Int?>(null) }

  confirmClearCount?.let { count ->
    AlertDialog(
      onDismissRequest = { confirmClearCount = null },
      title = { Text(stringResource(R.string.home_rows_remove_orphans_title)) },
      text = {
        Text(
          pluralStringResource(R.plurals.home_rows_remove_orphans_detail, count, count),
        )
      },
      confirmButton = {
        TextButton(onClick = {
          confirmClearCount = null
          val orphaned = groups.firstOrNull { it.key == ORPHAN_ROW_GROUP_KEY }?.rows.orEmpty().map { it.id }.toSet()
          localRows = localRows.filterNot { it.id in orphaned }
          onRemoveHomeCatalogRows(orphaned)
        }) { Text(stringResource(R.string.action_remove)) }
      },
      dismissButton = { TextButton(onClick = { confirmClearCount = null }) { Text(stringResource(R.string.action_keep)) } },
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
    SettingsSection(stringResource(R.string.settings_m_home_rows)) {
      SettingsSwitchRow("GRID", Color(0xFF22C55E), stringResource(R.string.settings_m_streamdek_home_rows), stringResource(R.string.settings_m_show_the_rows_that_come_with_streamdek), defaultAppCatalogsEnabled, onDefaultAppCatalogsEnabledChange)
      SettingsDivider()
      SettingsChoiceRow(
        icon = "ORD",
        iconColor = Color(0xFF8B5CF6),
        title = stringResource(R.string.settings_m_home_row_mode),
        subtitle = stringResource(R.string.settings_m_home_row_mode_detail),
        options = HomeRowMode.entries.map { it.name },
        selected = rowMode.name,
        choice = SettingsChoice.HomeRowMode,
        onSelected = { option -> onHomeRowModeChange(HomeRowMode.entries.first { it.name == option }) },
      )
    }
    when (rowMode) {
      HomeRowMode.BySource -> GroupedHomeRowsSection(
        groups = groups,
        expandedGroups = expandedGroups,
        sourceLabels = cloudStreamGroupLabels,
        reorderThresholdPx = reorderThresholdPx,
        dragScrollBy = dragScrollBy,
        onToggleGroup = { key -> expandedGroups = if (key in expandedGroups) expandedGroups - key else expandedGroups + key },
        onClearOrphans = { count -> confirmClearCount = count },
        onMoveGroup = { groupKey, visibleDelta ->
          // The distance in the *saved* order, which can hold sources this screen is not showing -
          // one that is switched off, or installed on another device. Stepping by one would hop
          // over a visible neighbour whenever a hidden key sat between them.
          val fullOrder = homeRowSourceOrder(localRows, localSourceOrder, cloudStreamGroups)
          val visibleKeys = groups.map { it.key }
          val from = visibleKeys.indexOf(groupKey)
          val neighbour = visibleKeys.getOrNull(from + visibleDelta)
          if (from >= 0 && neighbour != null) {
            val delta = fullOrder.indexOf(neighbour) - fullOrder.indexOf(groupKey)
            if (delta != 0) {
              localSourceOrder = moveHomeRowSource(fullOrder, groupKey, delta)
              onMoveHomeRowSource(groupKey, delta)
            }
          }
        },
        onRowEnabledChange = { rowId, enabled ->
          localRows = localRows.map { current -> if (current.id == rowId) current.copy(enabled = enabled) else current }
          onHomeCatalogRowEnabledChange(rowId, enabled)
        },
        onMoveRowWithin = { rowId, siblingIds, direction ->
          moveRowAmongVisible(localRows, rowId, siblingIds, direction)?.let { (reordered, delta) ->
            localRows = reordered
            onMoveHomeCatalogRow(rowId, delta)
          }
        },
      )
      HomeRowMode.Mixed -> MixedHomeRowsSection(
        rows = orderedRows,
        groups = groups,
        reorderThresholdPx = reorderThresholdPx,
        dragScrollBy = dragScrollBy,
        onRowEnabledChange = { rowId, enabled ->
          localRows = localRows.map { current -> if (current.id == rowId) current.copy(enabled = enabled) else current }
          onHomeCatalogRowEnabledChange(rowId, enabled)
        },
        onMoveRowWithin = { rowId, visibleIds, direction ->
          moveRowAmongVisible(localRows, rowId, visibleIds, direction)?.let { (reordered, delta) ->
            localRows = reordered
            onMoveHomeCatalogRow(rowId, delta)
          }
        },
      )
    }
  }
}

/**
 * One step of a drag, expressed against the rows the viewer can actually see.
 *
 * The saved layout is one flat list shared with Home, and the rows on screen need not be next to
 * each other in it: a source's rows can be scattered through it, and a filtered list shows a handful
 * out of seventy. So a step is the distance to the *visible* neighbour rather than a fixed one,
 * which is what lets a row move exactly one place on screen while everything else keeps its place.
 *
 * @return the reordered layout and the distance moved, or null when there is no neighbour that way.
 */
private fun moveRowAmongVisible(
  rows: List<HomeCatalogRow>,
  rowId: String,
  visibleIds: List<String>,
  direction: Int,
): Pair<List<HomeCatalogRow>, Int>? {
  val from = rows.indexOfFirst { it.id == rowId }
  if (from < 0) return null
  val positions = visibleIds.mapNotNull { id -> rows.indexOfFirst { it.id == id }.takeIf { it >= 0 } }.sorted()
  val neighbour = positions.getOrNull(positions.indexOf(from) + direction) ?: return null
  val reordered = rows.toMutableList().apply { add(neighbour, removeAt(from)) }
  return reordered to (neighbour - from)
}

/**
 * Home rows kept under the source that offers them, with the sources themselves rearrangeable.
 *
 * Two levels of drag, and they mean different things: dragging a source header moves that whole
 * source through Home, and dragging a row inside an open source moves it within that source only.
 * Neither can disturb the other, which is what makes this safe to fiddle with - putting an add-on
 * above StreamDek cannot cost you the order you arranged inside it.
 */
@Composable
private fun GroupedHomeRowsSection(
  groups: List<HomeRowGroup>,
  expandedGroups: Set<String>,
  sourceLabels: Map<String, String>,
  reorderThresholdPx: Float,
  dragScrollBy: suspend (Float) -> Float,
  onToggleGroup: (String) -> Unit,
  onClearOrphans: (Int) -> Unit,
  onMoveGroup: (String, Int) -> Unit,
  onRowEnabledChange: (String, Boolean) -> Unit,
  onMoveRowWithin: (String, List<String>, Int) -> Unit,
) {
  SettingsSection(stringResource(R.string.settings_m_choose_and_reorder_rows)) {
    Text(
      stringResource(R.string.home_rows_reorder_hint),
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
      style = MaterialTheme.typography.bodySmall,
    )
    groups.forEachIndexed { groupIndex, group ->
      key(group.key) {
        if (groupIndex > 0) SettingsDivider()
        val expanded = group.gatedNoteRes == null && group.key in expandedGroups
        HomeRowGroupHeader(
          title = group.title,
          rowCount = group.rows.size,
          enabledCount = group.rows.count { it.enabled },
          expanded = expanded,
          gatedNote = group.gatedNoteRes?.let { stringResource(it) },
          sourceLabel = sourceLabels[group.key],
          onClear = if (group.key == ORPHAN_ROW_GROUP_KEY && group.rows.isNotEmpty()) {
            { onClearOrphans(group.rows.size) }
          } else {
            null
          },
          // Rows from a vanished add-on are kept only so they can be recognised and removed; there
          // is no position for them to hold on a screen they cannot reach.
          onMove = if (group.key == ORPHAN_ROW_GROUP_KEY) null else { direction -> onMoveGroup(group.key, direction) },
          reorderThresholdPx = reorderThresholdPx,
          dragScrollBy = dragScrollBy,
          onToggle = { onToggleGroup(group.key) },
        )
        if (expanded) {
          val siblingIds = group.rows.map { it.id }
          group.rows.forEach { row ->
            key(row.id) {
              HomeCatalogRowItem(
                row = row,
                onEnabledChange = { enabled -> onRowEnabledChange(row.id, enabled) },
                onMove = { direction -> onMoveRowWithin(row.id, siblingIds, direction) },
                reorderThresholdPx = reorderThresholdPx,
                dragScrollBy = dragScrollBy,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Every Home row in one list, in the order Home draws them, whatever source each came from.
 *
 * The problem this has to solve is scale: a phone with a few add-ons and a plugin collection can
 * offer seventy rows, and seventy rows with nothing but names is a list nobody can navigate. So each
 * row says where it comes from in a quiet badge rather than a second line of prose, the counts at
 * the top say how much of the list is switched on, and a filter narrows the list by row or source
 * name. Dragging inside a filtered list still means "one place on screen" - see [moveRowAmongVisible]
 * - so filtering is a way to move one row a long way, not a mode where dragging stops working.
 */
@Composable
private fun MixedHomeRowsSection(
  rows: List<HomeCatalogRow>,
  groups: List<HomeRowGroup>,
  reorderThresholdPx: Float,
  dragScrollBy: suspend (Float) -> Float,
  onRowEnabledChange: (String, Boolean) -> Unit,
  onMoveRowWithin: (String, List<String>, Int) -> Unit,
) {
  // The source each row belongs to, named the way the grouped view names it - including "No longer
  // installed" for a row whose add-on is gone, which is the one label a row cannot supply itself.
  val sourceNames = remember(groups) {
    buildMap { groups.forEach { group -> group.rows.forEach { row -> put(row.id, group.title) } } }
  }
  // Why a row's source cannot reach Home, by row. In the grouped view this is said once on a source
  // that is greyed out and will not open; a flat list has no group header to say it on, so each row
  // has to carry it - without which switching StreamDek's own rows off leaves thirty rows here
  // looking exactly as switchable as the ones that still work.
  val gatedNotes = remember(groups) {
    buildMap {
      groups.forEach { group ->
        group.gatedNoteRes?.let { note -> group.rows.forEach { row -> put(row.id, note) } }
      }
    }
  }
  var query by rememberSaveable { mutableStateOf("") }
  var enabledOnly by rememberSaveable { mutableStateOf(false) }
  val filtered = remember(rows, query, enabledOnly, sourceNames) {
    val needle = query.trim().lowercase()
    rows.filter { row ->
      (!enabledOnly || row.enabled) &&
        (
          needle.isEmpty() ||
            homeRowDisplayTitle(row).lowercase().contains(needle) ||
            sourceNames[row.id].orEmpty().lowercase().contains(needle)
          )
    }
  }
  SettingsSection(stringResource(R.string.settings_m_choose_and_reorder_rows)) {
    Text(
      stringResource(R.string.home_rows_mixed_hint),
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
      style = MaterialTheme.typography.bodySmall,
    )
    // The filter earns its space only once the list is long enough to be hard to walk. Below that
    // it is one more control between the viewer and the rows they came here to drag.
    if (rows.size > 10) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = StreamDekRadius.controlShape,
        placeholder = { Text(stringResource(R.string.home_rows_filter_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { query = "" }) {
              Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear))
            }
          }
        },
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      FilterChip(
        selected = enabledOnly,
        onClick = { enabledOnly = !enabledOnly },
        label = { Text(stringResource(R.string.home_rows_filter_enabled_only)) },
      )
      Text(
        // Rows whose source is switched off are not on Home, whatever their own switch says, so
        // they are not counted here either.
        pluralStringResource(
          R.plurals.home_rows_on_of_count,
          rows.size,
          rows.count { it.enabled && it.id !in gatedNotes },
          rows.size,
        ),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        style = MaterialTheme.typography.bodySmall,
      )
    }
    if (filtered.isEmpty()) {
      Text(
        stringResource(R.string.home_rows_filter_no_matches),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    val visibleIds = filtered.map { it.id }
    filtered.forEach { row ->
      key(row.id) {
        HomeCatalogRowItem(
          row = row,
          sourceLabel = sourceNames[row.id],
          gatedNote = gatedNotes[row.id]?.let { stringResource(it) },
          onEnabledChange = { enabled -> onRowEnabledChange(row.id, enabled) },
          onMove = { direction -> onMoveRowWithin(row.id, visibleIds, direction) },
          reorderThresholdPx = reorderThresholdPx,
          dragScrollBy = dragScrollBy,
        )
      }
    }
  }
}

/** The key StreamDek's own rows group under; no add-on id can collide with it. */
internal const val STREAMDEK_ROW_GROUP_KEY = "__streamdek__"

/** Rows whose add-on is no longer installed, kept together rather than one nameless group each. */
internal const val ORPHAN_ROW_GROUP_KEY = "__orphaned__"

internal data class HomeRowGroup(
  val key: String,
  val title: String,
  /** Why this group's rows cannot reach Home, as a resource, or null when they can. */
  @StringRes val gatedNoteRes: Int?,
  val rows: List<HomeCatalogRow>,
)

/**
 * Splits the saved layout into one group per source, in the order the sources first appear in it.
 *
 * The saved layout is only a list of ids and on/off flags -- a row's title and the add-on it came
 * from are reconstructed at load time from whichever add-ons are currently installed. So the name
 * has to come from [addons] here, not from the row: a row belonging to an add-on that is switched
 * off has no candidate to be rebuilt from and arrives carrying an empty subtitle.
 *
 * That same lookup is what tells the group whether its add-on is switched off, which is the other
 * reason its rows would not be on Home.
 */
internal fun buildHomeRowGroups(
  rows: List<HomeCatalogRow>,
  addons: List<InstalledAddon>,
  streamDekRowsEnabled: Boolean,
  /**
   * What to call the group of rows whose add-on is gone, already in the interface language.
   *
   * Passed in rather than resolved here: this function is pure and unit-tested, and giving it a
   * Context to look a string up with would be the only reason it needed one.
   */
  orphanGroupTitle: String,
  /** What to call an add-on whose manifest gives no name. */
  fallbackAddonName: String,
  /**
   * The group a CloudStream row belongs in, keyed by its row-id source (one provider), as a group
   * key and title. One plugin can register several providers — CNC Verse registers Netflix, Prime
   * Video and more — and their rows belong together under the plugin, the way an add-on's
   * catalogues sit under the add-on. A provider missing here keeps a group of its own.
   */
  cloudStreamGroups: Map<String, Pair<String, String>> = emptyMap(),
): List<HomeRowGroup> {
  val addonsById = addons.associateBy { it.id }
  val cloudStreamGroupTitles = cloudStreamGroups.values.associate { (key, title) -> key to title }
  // Until the add-on list has arrived there is nothing to say a row is orphaned, and routing every
  // add-on row into "no longer installed" for the second the list takes to load would be alarming
  // and wrong. While it is empty, rows keep their own add-on's group and simply go unnamed.
  val addonsKnown = addons.isNotEmpty()
  return rows
    .groupBy { row ->
      val addonId = if (row.builtin) null else homeCatalogRowAddonId(row.id)
      when {
        row.builtin -> STREAMDEK_ROW_GROUP_KEY
        addonId == null -> ORPHAN_ROW_GROUP_KEY
        // A CloudStream provider is not an add-on, so the add-on list cannot vouch for it; its rows
        // are grouped under the provider rather than as "no longer installed".
        isCloudStreamHomeRowId(row.id) -> cloudStreamGroups[addonId]?.first ?: addonId
        addonsKnown && addonId !in addonsById -> ORPHAN_ROW_GROUP_KEY
        else -> addonId
      }
    }
    .map { (key, groupRows) ->
      val addon = addonsById[key]
      val title = when {
        key == STREAMDEK_ROW_GROUP_KEY -> "StreamDek"
        key == ORPHAN_ROW_GROUP_KEY -> orphanGroupTitle
        else -> cloudStreamGroupTitles[key]?.trim()?.takeIf { it.isNotEmpty() }
          ?: addon?.manifest?.name?.trim()?.takeIf { it.isNotEmpty() }
          // The add-on's own name, read from the row that carries it. This used to strip "From "
          // off the front of the subtitle, which recovered the right answer only for as long as
          // that subtitle was English.
          ?: groupRows.firstNotNullOfOrNull { row -> row.subtitleArg?.trim()?.takeIf { it.isNotEmpty() } }
          // A saved CloudStream row whose provider has not loaded yet carries no name; its slug does.
          ?: key.takeIf { it.startsWith(CLOUDSTREAM_ROW_SOURCE_PREFIX) }?.removePrefix(CLOUDSTREAM_ROW_SOURCE_PREFIX)
          ?: fallbackAddonName
      }
      val gatedNoteRes = when {
        key == STREAMDEK_ROW_GROUP_KEY && !streamDekRowsEnabled -> R.string.home_row_group_hidden_streamdek_rows
        key == ORPHAN_ROW_GROUP_KEY -> R.string.home_row_group_addon_not_installed
        addon != null && !addon.enabled -> R.string.home_row_group_hidden_addon_off
        else -> null
      }
      HomeRowGroup(key = key, title = title, gatedNoteRes = gatedNoteRes, rows = groupRows)
    }
}

/**
 * One source in the Home rows list, collapsed by default.
 *
 * [gatedNote] says why the group's rows cannot currently reach Home: the master switch is off, the
 * add-on they come from is off, or that add-on is gone. A gated group is greyed and does not open.
 * It stays listed rather than being removed, so the viewer can see their rows are kept and why
 * they are not showing.
 *
 * [onClear] is offered only where the rows can never come back on their own -- the add-on they came
 * from is gone, so there is nothing left to switch on and no reason to keep the entries.
 *
 * [onMove] drags the whole source through Home. A gated group keeps its handle: a source that is
 * switched off still has a place in the order, and being able to put it where it belongs *before*
 * switching it on is the more useful way round.
 */
@Composable
private fun HomeRowGroupHeader(
  title: String,
  rowCount: Int,
  enabledCount: Int,
  expanded: Boolean,
  gatedNote: String?,
  /**
   * Where the source itself comes from — "CloudStream · CNC Repo" for a CloudStream plugin — shown
   * beside its name. It belongs to the source, so it is said once here rather than on every row.
   */
  sourceLabel: String? = null,
  onClear: (() -> Unit)? = null,
  onMove: ((Int) -> Unit)? = null,
  reorderThresholdPx: Float = 0f,
  dragScrollBy: suspend (Float) -> Float = { 0f },
  onToggle: () -> Unit,
) {
  val gatedOff = gatedNote != null
  val contentAlpha = if (gatedOff) 0.38f else 1f
  ReorderableRow(
    itemKey = title,
    enabled = onMove != null,
    reorderThresholdPx = reorderThresholdPx,
    dragScrollBy = dragScrollBy,
    onMove = onMove ?: {},
    modifier = Modifier
      .fillMaxWidth()
      .clip(StreamDekRadius.thumbShape)
      .then(if (gatedOff) Modifier else Modifier.clickable(onClick = onToggle))
      .padding(vertical = 12.dp),
  ) { handleModifier, dragging ->
    if (onMove != null) {
      Box(
        modifier = handleModifier
          .size(36.dp)
          .clip(StreamDekRadius.thumbShape)
          .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.14f else 0.06f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Rounded.DragHandle,
          contentDescription = stringResource(R.string.a11y_drag_source_to_reorder),
          tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.92f else 0.54f),
          modifier = Modifier.size(20.dp),
        )
      }
    }
    Icon(
      if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
      contentDescription = stringResource(if (expanded) R.string.a11y_collapse_named else R.string.a11y_expand_named, title),
      tint = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha * 0.7f),
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        title,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
      )
      // On its own line under the name, so the name keeps the full width rather than sharing it.
      sourceLabel?.let {
        Text(
          it,
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha * 0.48f),
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        when {
          dragging -> stringResource(R.string.home_rows_moving_release)
          gatedNote != null -> stringResource(
            R.string.home_rows_kept_with_reason,
            pluralStringResource(R.plurals.home_rows_kept_count, rowCount, rowCount),
            gatedNote,
          )
          else -> pluralStringResource(R.plurals.home_rows_on_of_count, rowCount, enabledCount, rowCount)
        },
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha * 0.68f),
        style = MaterialTheme.typography.bodySmall,
      )
    }
    if (onClear != null) {
      TextButton(onClick = onClear) { Text(stringResource(R.string.action_remove)) }
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
  /**
   * The source this row came from, shown as a badge beside the name.
   *
   * Only in Mixed, where rows from every source sit in one list and the name alone does not say
   * which "Trending" this is. Under a source's own heading the answer is already on screen, and
   * repeating it on every row would be noise.
   */
  sourceLabel: String? = null,
  /**
   * Why this row cannot reach Home - its source is switched off, or gone - or null when it can.
   *
   * A gated row is greyed and its switch is inert, matching the grouped view, where a gated source
   * will not open at all. It stays in the list rather than being hidden: its place in the
   * arrangement is kept, and seeing it is how the viewer works out why it is not on Home.
   */
  gatedNote: String? = null,
) {
  val gated = gatedNote != null
  val contentAlpha = if (gated) 0.38f else 1f
  ReorderableRow(
    itemKey = row.id,
    reorderThresholdPx = reorderThresholdPx,
    dragScrollBy = dragScrollBy,
    onMove = onMove,
    modifier = Modifier
      .fillMaxWidth()
      .clip(StreamDekRadius.cardShape)
      .padding(horizontal = 8.dp, vertical = 10.dp),
  ) { handleModifier, dragging ->
    Box(
      modifier = handleModifier
        .size(44.dp)
        .clip(StreamDekRadius.thumbShape)
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.14f else 0.06f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.a11y_drag_to_reorder), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.92f else 0.54f))
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        row.titleRes?.let { stringResource(it) } ?: homeRowDisplayTitle(row),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
      )
      when {
        dragging -> Text(
          stringResource(R.string.home_rows_moving_release),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
        )
        // "AIOStreams, hidden while this add-on is off" - the source and the reason together,
        // because on this row the reason is only useful if you know what it is about.
        gatedNote != null -> Text(
          sourceLabel?.let { stringResource(R.string.home_rows_kept_with_reason, it, gatedNote) } ?: gatedNote,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        // The badge replaces the second line rather than joining it: "From AIOStreams" said twice,
        // once as a sentence and once as a chip, is the sort of thing that makes a list of seventy
        // rows unreadable.
        sourceLabel != null -> Box(
          modifier = Modifier
            .clip(StreamDekRadius.pill)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
          Text(
            sourceLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        row.subtitleRes != null -> Text(
          if (row.subtitleArg != null) stringResource(row.subtitleRes, row.subtitleArg) else stringResource(row.subtitleRes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Switch(checked = row.enabled, onCheckedChange = onEnabledChange, enabled = !gated)
  }
}

/**
 * A row that can be dragged up and down a settings list.
 *
 * Shared by the rows and the source headers above them, which drag identically and differ only in
 * what one step *means* - see [moveRowAmongVisible]. The drag is expressed as whole steps rather
 * than as a free-floating offset: the list underneath is an ordinary Column of settings rows, not a
 * reorderable list, so what moves is the arrangement itself, one position each time the finger has
 * travelled the height of a row.
 *
 * Near the top and bottom edges the list scrolls, and the scrolled distance is fed back into the
 * drag so the row stays under the finger and keeps stepping while the list moves - which is what
 * makes it possible to drag a row from the bottom of seventy to the top.
 */
@Composable
private fun ReorderableRow(
  itemKey: String,
  reorderThresholdPx: Float,
  dragScrollBy: suspend (Float) -> Float,
  onMove: (Int) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable RowScope.(handleModifier: Modifier, dragging: Boolean) -> Unit,
) {
  val latestOnMove by rememberUpdatedState(onMove)
  var dragging by remember(itemKey) { mutableStateOf(false) }
  var dragOffsetY by remember(itemKey) { mutableFloatStateOf(0f) }
  var itemTopInRoot by remember(itemKey) { mutableFloatStateOf(0f) }
  var autoScrollStep by remember(itemKey) { mutableFloatStateOf(0f) }
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

  val handleModifier = if (!enabled) {
    Modifier
  } else {
    Modifier.pointerInput(itemKey) {
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
    }
  }

  val moveUp = stringResource(R.string.a11y_move_up)
  val moveDown = stringResource(R.string.a11y_move_down)
  Row(
    modifier = modifier
      .onGloballyPositioned { itemTopInRoot = it.positionInRoot().y }
      .zIndex(if (dragging) 2f else 0f)
      .graphicsLayer {
        translationY = dragOffsetY
        scaleX = if (dragging) 1.02f else 1f
        scaleY = if (dragging) 1.02f else 1f
        shadowElevation = if (dragging) 18f else 0f
      }
      .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f) else Color.Transparent)
      // A drag handle is unusable with a screen reader or a remote, so the same two moves are
      // offered as actions on the row itself. They step by one *visible* place, exactly as a drag
      // does.
      .semantics {
        if (enabled) {
          customActions = listOf(
            CustomAccessibilityAction(moveUp) { latestOnMove(-1); true },
            CustomAccessibilityAction(moveDown) { latestOnMove(1); true },
          )
        }
      },
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    content(handleModifier, dragging)
  }
}

