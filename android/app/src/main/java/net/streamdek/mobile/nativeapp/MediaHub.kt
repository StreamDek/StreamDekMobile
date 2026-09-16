package net.streamdek.mobile.nativeapp

/** Source-qualified identity: equal titles/IDs from different providers are not interchangeable. */
internal fun mediaHubItemKey(item: MediaItem): String =
  listOf(item.sourceAddonId.orEmpty(), item.type, item.id).joinToString("\u001f")

internal fun isMediaHubCatalogType(type: String): Boolean = type.lowercase() in setOf(
  "movie", "movies", "series", "tv", "live", "channel", "iptv", "sport", "sports", "events", "anime", "livestream",
)

/** Keeps the personal rows first and replaces source rows without changing the disabled layout. */
internal fun mediaHubHomeOrder(ids: List<String>, eligible: Set<String>, enabled: Boolean): List<String> {
  if (!enabled) return ids
  val remaining = ids.filterNot { it in eligible || it == MEDIA_HUB_ROW_ID }.toMutableList()
  val position = remaining.indexOfLast { it == "new-episodes" || it == "continue" } + 1
  remaining.add(position, MEDIA_HUB_ROW_ID)
  return remaining
}

/** Home Rows' on/off switches, by the part of a row id that survives an add-on reordering its catalogues. */
internal fun mediaHubRowSwitches(rows: List<HomeCatalogRow>): Map<String, Boolean> =
  rows.associate { homeCatalogRowMatchKey(it.id) to it.enabled }

/** The Home Rows match key for an add-on catalogue, as [addonHomeCatalogCandidates] writes its id. */
internal fun mediaHubAddonRowId(addonId: String, catalogType: String, catalogId: String): String =
  "addon:$addonId:${catalogType.trim().lowercase()}:$catalogId"

/**
 * Whether Home Rows leaves a catalogue on. A catalogue it does not list takes the default that
 * Home Rows would give it: add-on catalogues arrive switched on, CloudStream rows switched off.
 */
internal fun isMediaHubRowSwitchedOn(rowId: String, switches: Map<String, Boolean>, offWhenUnlisted: Boolean): Boolean =
  switches[homeCatalogRowMatchKey(rowId)] ?: !offWhenUnlisted

internal const val MEDIA_HUB_ROW_ID = "media_hub"
internal const val MEDIA_HUB_PREFERENCE = "media_hub_enabled"

internal data class MediaHubCatalog(
  val key: String,
  val sourceKey: String,
  val sourceName: String,
  val title: String,
  val live: Boolean,
  val addon: InstalledAddon? = null,
  val catalog: AddonCatalog? = null,
  val genre: String? = null,
  val cloudRowId: String? = null,
  val localItems: List<MediaItem>? = null,
) {
  val supportsSearch: Boolean get() = localItems != null || catalog?.supportsSearch == true || cloudRowId != null
}

internal data class MediaHubPage(
  val items: List<MediaItem> = emptyList(),
  val nextOffset: Int = 0,
  val end: Boolean = false,
  val failed: Boolean = false,
)
