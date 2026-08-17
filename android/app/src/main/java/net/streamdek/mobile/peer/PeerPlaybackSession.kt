package net.streamdek.mobile.peer

import com.frostwire.jlibtorrent.TorrentHandle

/**
 * A live view of one torrent's swarm, for the launch screen.
 *
 * [hasMetadata] is the meaningful milestone: until the torrent file itself has been fetched from
 * peers there is no file list, no length and nothing to play, so a wait that never gets past it is
 * a wait for peers rather than for bandwidth.
 */
data class SwarmStats(
  val hasMetadata: Boolean,
  val seeds: Int,
  val peers: Int,
  val downloadRateBytesPerSecond: Int,
  val downloadedBytes: Long,
  val fileLengthBytes: Long,
)

data class PeerPlaybackSession(
  val sessionId: String,
  val infoHash: String,
  val magnetLink: String,
  val saveDirectory: java.io.File,
  val preferredFilename: String?,
  val handle: TorrentHandle,
  @Volatile var fileIndex: Int = -1,
  @Volatile var filePath: String = "",
  @Volatile var fileLength: Long = 0L,
)
