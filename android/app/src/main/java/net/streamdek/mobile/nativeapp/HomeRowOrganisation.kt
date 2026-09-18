package net.streamdek.mobile.nativeapp

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import java.util.Locale
import org.json.JSONArray
import net.streamdek.mobile.R

/**
 * The device-independent key the row mode is stored under, in the *profile's* settings file.
 *
 * Profile-scoped rather than device-local, unlike [HOME_DENSITY_PREFERENCE]: how close a screen is
 * held is a property of the screen, but which rows a viewer wants and in what order is a property
 * of the viewer. Two profiles on the same phone keep separate answers, and the answer travels with
 * the profile through cloud preferences.
 */
internal const val HOME_ROW_MODE_PREFERENCE = "home_row_mode"

/** Where the source order is stored, beside [HOME_ROW_MODE_PREFERENCE]. See [homeRowSourceOrder]. */
internal const val HOME_ROW_SOURCE_ORDER_PREFERENCE = "home_row_source_order"

/**
 * How Home's rows are organised: kept together under the source that offers them, or arranged
 * freely one row at a time.
 *
 * The two modes are two views of the *same* saved layout rather than two saved layouts. The layout
 * is one flat list of rows ([HomeCatalogRow], in the order the viewer arranged), and a separate
 * list of source keys ([homeRowSourceOrder]). [HomeRowMode.Mixed] draws the flat list as it stands;
 * [HomeRowMode.BySource] draws it grouped, with the groups in the source order. Neither view can
 * destroy the other's arrangement, so switching back and forth costs nothing.
 */
enum class HomeRowMode(
  /** The persisted form. Stable across releases; the enum name is not the storage contract. */
  val key: String,
  @StringRes val labelRes: Int,
  @StringRes val descriptionRes: Int,
) {
  BySource(
    key = "by_source",
    labelRes = R.string.home_row_mode_by_source,
    descriptionRes = R.string.home_row_mode_by_source_description,
  ),
  Mixed(
    key = "mixed",
    labelRes = R.string.home_row_mode_mixed,
    descriptionRes = R.string.home_row_mode_mixed_description,
  );

  companion object {
    /** What Home has always done, so nobody's screen changes because this setting arrived. */
    val Default = BySource

    /** Accepts the stored key and the enum name; anything else is the default. */
    fun fromKey(key: String?): HomeRowMode {
      val normalized = key?.trim()?.lowercase().orEmpty()
      if (normalized.isEmpty()) return Default
      return entries.firstOrNull { it.key == normalized || it.name.lowercase() == normalized } ?: Default
    }
  }
}

/**
 * What to call a row in the settings list when nothing is left to name it.
 *
 * A saved layout stores ids and on/off flags only; a row's name is rebuilt from the source that
 * offers it. A source that is switched off offers nothing, so its rows come back with their own id
 * where the name should be - which the grouped view never showed, because a switched-off source
 * will not open. A flat list shows every row, so it has to have something better to print than
 * `addon:389b7f7d-…:series:search.series:24`.
 *
 * The catalogue segment of the id is the part a person can read: "search.series" becomes "Search
 * Series", "mal.airing" becomes "Mal Airing". It is a stand-in until the add-on is switched back
 * on and says what it calls the row itself.
 */
internal fun homeRowDisplayTitle(row: HomeCatalogRow): String {
  val title = row.title.trim()
  if (title.isNotEmpty() && title != row.id) return title
  val parts = row.id.split(":")
  val catalogue = if (row.id.startsWith("addon:") && parts.size >= 4) parts[3] else row.id
  val words = catalogue.split('.', '_', '-', ' ').filter { it.isNotBlank() }
  if (words.isEmpty()) return title.ifEmpty { row.id }
  return words.joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
  }
}

/**
 * How Home's rows are arranged: the mode, and the source order the grouped mode reads.
 *
 * One object rather than two fields on the app state, and not for tidiness. Kotlin generates a
 * `copy` for that state taking every property at once, and a dex method can be handed at most 255
 * argument registers - a ceiling the app state sits a few properties below. Two more properties
 * there produce a class the runtime verifier rejects outright, which surfaces as the whole app
 * failing to start rather than as anything resembling a compile error. Related state therefore
 * travels together. See AppUiStateSizeTest, which fails before a device has to.
 */
@Immutable
data class HomeRowArrangement(
  val mode: HomeRowMode = HomeRowMode.Default,
  /** The source keys in the viewer's order; see [homeRowSourceOrder]. */
  val sourceOrder: List<String> = emptyList(),
)

/**
 * The source a row belongs to, as a key that survives everything a source can do to itself.
 *
 * Read from the row id alone - never from a position in a list, an add-on's manifest order, or the
 * row's own `builtin` flag, none of which a saved layout can rely on. An add-on row carries its
 * add-on's id; a CloudStream row carries its provider's slug, mapped to the plugin that registered
 * it when that plugin is loaded (one plugin registers several providers, and their rows belong
 * together under it); anything else is one of StreamDek's own rows.
 *
 * The mapping is only consulted, never required: a plugin that has not loaded leaves its rows keyed
 * by provider, which is still stable and still orders them together.
 */
internal fun homeRowSourceKey(
  row: HomeCatalogRow,
  cloudStreamGroups: Map<String, Pair<String, String>> = emptyMap(),
): String = homeRowSourceKey(row.id, cloudStreamGroups)

internal fun homeRowSourceKey(
  rowId: String,
  cloudStreamGroups: Map<String, Pair<String, String>> = emptyMap(),
): String {
  val addonId = homeCatalogRowAddonId(rowId) ?: return STREAMDEK_ROW_GROUP_KEY
  if (isCloudStreamHomeRowId(rowId)) return cloudStreamGroups[addonId]?.first ?: addonId
  return addonId
}

/**
 * The source order actually in force: what the viewer arranged, then anything new underneath it.
 *
 * Two rules, and both matter more than they look:
 *
 * A saved key is kept even when no row currently carries it. A source that is switched off, failing
 * to load, or simply not installed on *this* device has not been demoted - it is absent - and
 * dropping its key would quietly forget where it goes, so its rows would reappear at the bottom the
 * next time it worked. Nothing prunes this list except the viewer removing the rows themselves.
 *
 * A source the saved order has never seen goes underneath, in the order its rows appear in the
 * layout. A new add-on therefore lands at the bottom of Home rather than in the middle of an
 * arrangement somebody made deliberately.
 */
internal fun homeRowSourceOrder(
  rows: List<HomeCatalogRow>,
  savedOrder: List<String>,
  cloudStreamGroups: Map<String, Pair<String, String>> = emptyMap(),
): List<String> {
  val saved = savedOrder.filter { it.isNotBlank() }.distinct()
  val known = saved.toHashSet()
  val discovered = rows.map { homeRowSourceKey(it, cloudStreamGroups) }.distinct().filterNot { it in known }
  return saved + discovered
}

/**
 * The layout as Home should draw it, for the selected [mode].
 *
 * [HomeRowMode.Mixed] is the saved list itself - the viewer placed each row where it is, and that is
 * the whole point of the mode. [HomeRowMode.BySource] sorts it by source, *stably*, so each source's
 * rows keep the order they were given inside the group they are gathered into.
 *
 * Every row survives either way, including rows whose source is currently absent: this decides
 * order, not visibility. Whether a row reaches the screen is [applyHomeCatalogLayout]'s answer.
 */
internal fun orderedHomeCatalogRows(
  rows: List<HomeCatalogRow>,
  mode: HomeRowMode,
  savedSourceOrder: List<String>,
  cloudStreamGroups: Map<String, Pair<String, String>> = emptyMap(),
): List<HomeCatalogRow> {
  if (mode == HomeRowMode.Mixed || rows.isEmpty()) return rows
  val rank = homeRowSourceOrder(rows, savedSourceOrder, cloudStreamGroups)
    .withIndex()
    .associate { (index, key) -> key to index }
  return rows.withIndex()
    .sortedWith(
      compareBy(
        { rank[homeRowSourceKey(it.value, cloudStreamGroups)] ?: Int.MAX_VALUE },
        { it.index },
      ),
    )
    .map { it.value }
}

/**
 * Moves one source [delta] places through the order.
 *
 * [delta] is a distance in the *whole* order, not a step of one: the settings screen shows only the
 * sources that currently offer rows, and a hidden key can sit between two of them. The screen works
 * out how far its neighbour actually is and says so, which is what keeps absent sources in place
 * while the visible ones move around them.
 */
internal fun moveHomeRowSource(order: List<String>, sourceKey: String, delta: Int): List<String> {
  if (delta == 0 || order.isEmpty()) return order
  val index = order.indexOf(sourceKey)
  if (index < 0) return order
  val target = (index + delta).coerceIn(0, order.lastIndex)
  if (target == index) return order
  return order.toMutableList().apply { add(target, removeAt(index)) }
}

internal fun serializeHomeRowSourceOrder(order: List<String>): String =
  JSONArray().apply { order.filter { it.isNotBlank() }.distinct().forEach(::put) }.toString()

internal fun parseHomeRowSourceOrder(raw: String?): List<String> {
  if (raw.isNullOrBlank()) return emptyList()
  return runCatching {
    val source = JSONArray(raw)
    buildList {
      for (index in 0 until source.length()) {
        source.optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
      }
    }.distinct()
  }.getOrDefault(emptyList())
}
