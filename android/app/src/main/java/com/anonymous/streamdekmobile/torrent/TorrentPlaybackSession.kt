package com.anonymous.streamdekmobile.torrent

import com.frostwire.jlibtorrent.TorrentHandle

data class TorrentPlaybackSession(
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
