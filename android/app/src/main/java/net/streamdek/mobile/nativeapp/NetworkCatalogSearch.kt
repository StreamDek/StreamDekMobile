package net.streamdek.mobile.nativeapp

internal data class NetworkCatalogPages(
  val items: List<MediaItem> = emptyList(),
  val page: Int = 0,
  val totalPages: Int = 1,
) {
  val hasMore: Boolean get() = page < totalPages

  fun append(next: DiscoverPage) = NetworkCatalogPages(
    items = (items + next.items).distinctBy { "${it.type}:${it.id}" },
    page = next.page,
    totalPages = if (next.page > page) next.totalPages else next.page,
  )
}
