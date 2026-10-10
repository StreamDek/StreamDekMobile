package net.streamdek.mobile.nativeapp

import net.streamdek.mobile.nativeapp.mediaserver.EMBY_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.JELLYFIN_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerReference
import net.streamdek.mobile.nativeapp.mediaserver.MediaServerResume
import net.streamdek.mobile.nativeapp.mediaserver.PLEX_PROVIDER_ID

/**
 * Where a media server's in-progress titles are shown, chosen for Plex and for Jellyfin apart.
 *
 * It decides display and nothing else. Playback through a server is reported to that server in
 * both, so its own Continue Watching, watched state and resume points stay right, and switching
 * between them never deletes a position: the server keeps every one, and they reappear wherever the
 * viewer next says to show them.
 *
 * Synced as `plexContinueWatchingLocation` / `jellyfinContinueWatchingLocation` /
 * `embyContinueWatchingLocation` under `home`, the
 * same keys the television and the web portal read.
 */
enum class MediaServerContinueLocation(val key: String) {
  /**
   * The server's in-progress titles join StreamDek's Continue Watching on Home and its own page,
   * once each beside titles from add-ons, as well as the row on the server's page. What the app
   * has always done, so it is the default.
   */
  StreamDek("streamdek"),

  /** Only the server's page has them; StreamDek's Continue Watching is left to everything else. */
  ServerLibrary("server"),
  ;

  companion object {
    fun fromKey(key: String?): MediaServerContinueLocation = entries.firstOrNull { it.key == key } ?: StreamDek
  }
}

/** Each provider's choice. Providers with no setting of their own (none yet) follow the default. */
data class MediaServerContinueLocations(
  val plex: MediaServerContinueLocation = MediaServerContinueLocation.StreamDek,
  val jellyfin: MediaServerContinueLocation = MediaServerContinueLocation.StreamDek,
  val emby: MediaServerContinueLocation = MediaServerContinueLocation.StreamDek,
) {
  fun of(provider: String?): MediaServerContinueLocation = when (provider) {
    PLEX_PROVIDER_ID -> plex
    JELLYFIN_PROVIDER_ID -> jellyfin
    EMBY_PROVIDER_ID -> emby
    else -> MediaServerContinueLocation.StreamDek
  }
}

/** The servers' in-progress titles that StreamDek's own Continue Watching should include. */
internal fun List<MediaServerResume>.shownInStreamDek(locations: MediaServerContinueLocations): List<MediaServerResume> =
  filter { locations.of(MediaServerReference.providerOfSource(it.item.sourceAddonId)) == MediaServerContinueLocation.StreamDek }
