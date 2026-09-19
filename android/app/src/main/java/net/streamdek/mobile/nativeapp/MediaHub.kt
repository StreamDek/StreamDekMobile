package net.streamdek.mobile.nativeapp

/** Source-qualified identity: equal titles/IDs from different providers are not interchangeable. */
internal fun mediaHubItemKey(item: MediaItem): String =
  listOf(item.sourceAddonId.orEmpty(), item.type, item.id).joinToString("\u001f")

/**
 * Whether a source's catalogue belongs in StreamDek Fuse.
 *
 * Live channels only. Fuse is where the viewer's channel sources live - the add-ons and plugins
 * that serve live TV, the playlists they have loaded, and the channels they have starred - and a
 * playlist's own VOD section rides along with the playlist that brought it.
 *
 * An ordinary catalogue add-on does not belong here however many films it lists: a discovery
 * catalogue is a row of titles like StreamDek's own, and treating one as Fuse material both buried
 * it in a page about channels and took its row off Home. This used to accept every media type,
 * which is exactly what it did.
 */
internal fun isMediaHubLiveCatalogType(type: String): Boolean = type.trim().lowercase() in liveCatalogTypes

/**
 * The StreamDek catalogue rows that lead Home, straight after the viewer's own rows and ahead of the
 * live rows, the Fuse and Streaming Networks. Their artwork is what Home opens on; with a channel row
 * first, Home looked empty until the viewer scrolled. The television places them the same way.
 */
internal val LEADING_HOME_CATALOG_IDS = listOf("new_movies", "new_series")

/**
 * Keeps the personal rows and the leading catalogue rows first and replaces source rows without
 * changing the disabled layout. The hub goes after the unbroken run of those rows at the top - so in
 * Mixed, where New Movies may sit anywhere the viewer put it, it still does not drag the hub down.
 */
internal fun mediaHubHomeOrder(ids: List<String>, eligible: Set<String>, enabled: Boolean): List<String> {
  if (!enabled) return ids
  val remaining = ids.filterNot { it in eligible || it == MEDIA_HUB_ROW_ID }.toMutableList()
  val leading = setOf("continue", "new-episodes") + LEADING_HOME_CATALOG_IDS
  val position = remaining.indexOfFirst { it !in leading }.takeIf { it >= 0 } ?: remaining.size
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

/** First visits discover every catalogue; explicit pagination advances a bounded batch. */
internal fun mediaHubPendingCatalogs(
  catalogs: List<MediaHubCatalog>,
  loadMore: Boolean,
  retry: Boolean,
  searching: Boolean,
  pageFor: (MediaHubCatalog) -> MediaHubPage?,
): List<MediaHubCatalog> {
  val pending = catalogs.filter { source ->
    val page = pageFor(source)
    source.localItems == null && (page == null ||
      (loadMore && !page.end && (!page.failed || retry)))
  }.sortedBy { pageFor(it)?.nextOffset ?: -1 }
  // Never make an unseen source depend on the viewer requesting another page of titles.
  val unseen = pending.filter { pageFor(it) == null }
  val existing = pending.filter { pageFor(it) != null }
  return unseen + if (searching) existing else existing.take(4)
}
