package net.streamdek.mobile.nativeapp

/**
 * The length the account cut live favourite ids to before storing them.
 *
 * A CloudStream channel's id carries its provider's whole link, which for some providers (a News
 * list, say) is a block of stream details well past this. The account kept the first 512 characters,
 * so after a refresh the phone held ids that no longer matched the channels they were starred from:
 * the stars went out, and a favourite opened from the Favourites row could not be decoded. The
 * backend now keeps longer ids; these helpers repair the lists it already cut short.
 */
internal const val ACCOUNT_FAVOURITE_ID_LIMIT = 512

/** Whether stored favourite id [stored] is [id] itself, or [id] as the account cut it short. */
internal fun favouriteChannelIdMatches(stored: String, id: String): Boolean =
  stored == id || (stored.length == ACCOUNT_FAVOURITE_ID_LIMIT && id.length > stored.length && id.startsWith(stored))

/**
 * [favourites] with each id the account cut short given back in full, wherever [known] holds the
 * channel it was cut from. Returns [favourites] itself when nothing needed restoring, so a caller can
 * tell by identity whether there is anything to save.
 */
internal fun restoreTruncatedFavouriteIds(favourites: List<MediaItem>, known: List<MediaItem>): List<MediaItem> {
  if (known.isEmpty() || favourites.none { it.id.length == ACCOUNT_FAVOURITE_ID_LIMIT }) return favourites
  var changed = false
  val restored = favourites.map { favourite ->
    if (favourite.id.length != ACCOUNT_FAVOURITE_ID_LIMIT) return@map favourite
    val full = known.firstOrNull { favouriteChannelIdMatches(favourite.id, it.id) && it.id != favourite.id } ?: return@map favourite
    changed = true
    favourite.copy(id = full.id, directStreamUrl = favourite.directStreamUrl ?: full.directStreamUrl)
  }
  return if (changed) restored.distinctBy { it.id } else favourites
}

/**
 * The account's copy of the favourites, read against the phone's own: ids it cut short come back in
 * full, and the stream link it did not keep is filled in from the phone's entry for that channel.
 */
internal fun mergeAccountFavourites(account: List<MediaItem>, local: List<MediaItem>): List<MediaItem> {
  val localById = local.associateBy { it.id }
  return restoreTruncatedFavouriteIds(account, local).map { item ->
    val mine = localById[item.id] ?: return@map item
    if (item.directStreamUrl != null || mine.directStreamUrl == null) item else item.copy(directStreamUrl = mine.directStreamUrl)
  }
}
