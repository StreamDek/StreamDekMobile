package net.streamdek.mobile.nativeapp

import net.streamdek.mobile.nativeapp.mediaserver.JELLYFIN_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import net.streamdek.mobile.nativeapp.mediaserver.PLEX_PROVIDER_ID

/*
 * How Search lays its results out: one section per place a result came from.
 *
 * Results used to arrive as a stack of grids with little to tell them apart - the media server
 * section named after whichever server answered first while holding the others' titles too, the
 * catalogue grid with no heading at all, and every add-on merged into one. Each section here is
 * one source, named, so a title found in several places is offered from each and the viewer can
 * see at a glance which one plays from home.
 */

/** Where a section's results came from. The order is the order sections appear in. */
internal enum class SearchSourceKind { Plex, Jellyfin, Catalogue, Addon, Plugin }

internal data class SearchResultSection(
  /** Stable across recompositions and unique on the page; used as the list key and to remember expansion. */
  val key: String,
  val kind: SearchSourceKind,
  /** The server, add-on or plugin's own name. Null for StreamDek's catalogue, which is named by the screen. */
  val name: String?,
  val items: List<MediaItem>,
)

/** The most one section ever holds; past this the rest is noise for a search. */
internal const val SEARCH_SECTION_LIMIT = 60

/** Rows of cards a section shows before "Show all". */
internal const val SEARCH_SECTION_PREVIEW_ROWS = 2

/** Which media server a result came from, from its source or, failing that, its id. */
internal fun mediaServerProviderOf(item: MediaItem): String? =
  MediaServerReference.providerOfSource(item.sourceAddonId) ?: MediaServerReference.decode(item.id)?.provider

/**
 * Splits everything a search found into sections, in this order: the viewer's own Plex servers,
 * their Jellyfin servers, StreamDek's catalogue, each add-on, each plugin. Within a kind, sections
 * keep the order their first result arrived in. Empty sections are left out.
 *
 * A media server gets one section per server, so a second Plex server or a Jellyfin server is
 * never folded under the name of the first. [plugins] are grouped by the provider name each card
 * carries in [MediaItem.cardSubtitle], as they always have been.
 */
internal fun searchResultSections(
  library: List<MediaItem>,
  catalogue: List<MediaItem>,
  addons: List<MediaItem>,
  plugins: List<MediaItem>,
): List<SearchResultSection> = buildList {
  val byServer = library.groupBy { it.sourceAddonId ?: mediaServerProviderOf(it).orEmpty() }
  listOf(SearchSourceKind.Plex, SearchSourceKind.Jellyfin).forEach { kind ->
    byServer.forEach { (server, items) ->
      val provider = mediaServerProviderOf(items.first())
      val itemKind = if (provider == JELLYFIN_PROVIDER_ID) SearchSourceKind.Jellyfin else SearchSourceKind.Plex
      if (itemKind != kind) return@forEach
      add(
        SearchResultSection(
          key = "server:${provider ?: PLEX_PROVIDER_ID}:$server",
          kind = kind,
          name = items.firstNotNullOfOrNull { it.sourceAddonName?.takeIf(String::isNotBlank) },
          items = items.take(SEARCH_SECTION_LIMIT),
        ),
      )
    }
  }
  if (catalogue.isNotEmpty()) {
    add(SearchResultSection("catalogue", SearchSourceKind.Catalogue, null, catalogue.take(SEARCH_SECTION_LIMIT)))
  }
  addons.groupBy { it.sourceAddonId ?: it.sourceAddonName.orEmpty() }.forEach { (addon, items) ->
    add(
      SearchResultSection(
        key = "addon:$addon",
        kind = SearchSourceKind.Addon,
        name = items.firstNotNullOfOrNull { it.sourceAddonName?.takeIf(String::isNotBlank) },
        items = items.take(SEARCH_SECTION_LIMIT),
      ),
    )
  }
  plugins.groupBy { it.cardSubtitle.orEmpty() }.forEach { (provider, items) ->
    add(
      SearchResultSection(
        key = "plugin:$provider",
        kind = SearchSourceKind.Plugin,
        name = provider.takeIf(String::isNotBlank),
        items = items.take(SEARCH_SECTION_LIMIT),
      ),
    )
  }
}

/**
 * How many of a section's results to draw. Folded, a section shows two rows of cards, so a long
 * list from one source never pushes every other source off the screen; a page with only one
 * section shows it whole, since there is nothing for it to push away.
 */
internal fun searchSectionVisibleCount(size: Int, columns: Int, expanded: Boolean, onlySection: Boolean): Int =
  if (expanded || onlySection) size else minOf(size, columns.coerceAtLeast(1) * SEARCH_SECTION_PREVIEW_ROWS)

/** Whether a section has more than it shows folded, and so needs "Show all". */
internal fun searchSectionFolds(size: Int, columns: Int, onlySection: Boolean): Boolean =
  !onlySection && size > columns.coerceAtLeast(1) * SEARCH_SECTION_PREVIEW_ROWS
