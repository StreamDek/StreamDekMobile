package net.streamdek.mobile.peer

import android.content.Context
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import java.io.File
import java.util.UUID

class PeerEngine(
  context: Context,
) {
  companion object {
    private const val TAG = "StreamDekPeer"
    private const val SESSION_POLL_INTERVAL_MS = 250L
    private const val TORRENT_ADD_TIMEOUT_MS = 20_000L

    /**
     * How long to wait for the torrent file itself to arrive from peers.
     *
     * Twenty seconds was too short to be a real answer on a mobile connection — a torrent with few
     * seeds routinely needs longer, and giving up early reported a live source as a dead one. The
     * wait is now visible on the launch screen (seeds, peers, rate) and cancellable with back, so a
     * longer window costs a viewer nothing they cannot see or escape.
     */
    private const val TORRENT_METADATA_TIMEOUT_MS = 60_000L
  }

  private val storage = PeerStorageManager(context)
  private val sessionManager = SessionManager()
  private val playbackSessions = linkedMapOf<String, PeerPlaybackSession>()
  private val sessionsByInfoHash = linkedMapOf<String, PeerPlaybackSession>()
  @Volatile private var started = false

  /**
   * The session currently being warmed up, published the moment its handle exists.
   *
   * Held separately from the maps, and deliberately without a lock: [createPlaybackSession] holds
   * this object's monitor for as long as the metadata wait lasts, so anything that synchronised to
   * read progress would block for exactly the period it was meant to describe.
   */
  @Volatile private var latestSession: PeerPlaybackSession? = null

  @Synchronized
  fun ensureStarted(config: PeerStreamConfig) {
    if (!started) {
      val startedAt = System.currentTimeMillis()
      sessionManager.start()
      started = true
      android.util.Log.d(TAG, "peer session started in ${System.currentTimeMillis() - startedAt}ms")
    }
    applyProfile(config.profile)
    storage.enforceLimit(config.cacheSizeGb, sessionsByInfoHash.keys)
  }

  @Synchronized
  fun stop() {
    if (!started) return
    latestSession = null
    playbackSessions.clear()
    sessionsByInfoHash.clear()
    sessionManager.stop()
    started = false
  }

  @Synchronized
  fun createPlaybackSession(
    config: PeerStreamConfig,
    infoHash: String,
    magnetLink: String,
    preferredFilename: String?,
  ): PeerPlaybackSession {
    ensureStarted(config)

    val normalizedInfoHash = infoHash.lowercase()
    val existing = sessionsByInfoHash[normalizedInfoHash]
    if (existing != null) {
      storage.touch(normalizedInfoHash)
      ensureTargetFile(existing)
      return existing
    }

    val saveDirectory = storage.sessionDirectory(normalizedInfoHash)
    val handle = findOrAddSource(normalizedInfoHash, magnetLink, saveDirectory)
    val session = PeerPlaybackSession(
      sessionId = UUID.randomUUID().toString(),
      infoHash = normalizedInfoHash,
      magnetLink = magnetLink,
      saveDirectory = saveDirectory,
      preferredFilename = preferredFilename,
      handle = handle,
    )

    handle.resume()
    try {
      handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
    } catch (_: Throwable) {
    }

    // Published before the metadata wait, not after it. Registering only on success meant the
    // launch screen had no session to report on for the whole time it was waiting — which is the
    // one moment the seed and peer counts are worth showing.
    latestSession = session
    playbackSessions[session.sessionId] = session
    sessionsByInfoHash[normalizedInfoHash] = session
    storage.touch(normalizedInfoHash)
    try {
      ensureTargetFile(session)
    } catch (error: Throwable) {
      // A torrent that never produced a file list is not a session anything can play from.
      playbackSessions.remove(session.sessionId)
      sessionsByInfoHash.remove(normalizedInfoHash)
      throw error
    }
    return session
  }

  fun getPlaybackSession(sessionId: String): PeerPlaybackSession? {
    return playbackSessions[sessionId]
  }

  /**
   * What the swarm looks like right now, for the session started most recently.
   *
   * Warming a torrent up is the one part of playback with no upper bound on how long it may take —
   * metadata has to arrive from peers before there is a file to read. Reported so the viewer sees
   * peers being found rather than a spinner that either succeeds or, after a silent wait, does not.
   */
  fun latestSwarmStats(expectedInfoHash: String?): SwarmStats? {
    val session = latestSession ?: return null
    // Scoped to the torrent being launched, so a cached premium stream does not inherit the
    // numbers from whatever peer-to-peer source was warmed up before it.
    if (expectedInfoHash != null && !session.infoHash.equals(expectedInfoHash.trim(), ignoreCase = true)) return null
    return runCatching {
      val status = session.handle.status()
      SwarmStats(
        hasMetadata = session.fileLength > 0L || session.handle.torrentFile() != null,
        seeds = status.numSeeds(),
        peers = status.numPeers(),
        downloadRateBytesPerSecond = status.downloadPayloadRate(),
        downloadedBytes = status.totalDone(),
        fileLengthBytes = session.fileLength,
      )
    }.getOrNull()
  }

  fun prepareForByteRange(sessionId: String, startByte: Long) {
    val session = getPlaybackSession(sessionId) ?: return
    ensureTargetFile(session)
    val handle = session.handle
    val torrentInfo = handle.torrentFile() ?: return
    if (session.fileIndex < 0 || session.fileLength <= 0L) return

    val pieceLength = torrentInfo.pieceLength().toLong().coerceAtLeast(1L)
    val fileStorage = torrentInfo.files()
    val fileOffset = fileStorage.fileOffset(session.fileIndex) + startByte.coerceAtLeast(0L)
    val firstPiece = (fileOffset / pieceLength).toInt()
    for (piece in firstPiece until (firstPiece + 32)) {
      try {
        handle.setPieceDeadline(piece, 0)
      } catch (_: Throwable) {
        break
      }
    }
  }

  fun waitForAvailableBytes(sessionId: String, targetByteExclusive: Long, timeoutMs: Long = 45_000L): Boolean {
    val session = getPlaybackSession(sessionId) ?: return false
    val handle = session.handle
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeoutMs) {
      ensureTargetFile(session)
      val availableBytes = estimateAvailableBytes(session, handle)
      if (availableBytes >= targetByteExclusive) {
        storage.touch(session.infoHash)
        return true
      }
      Thread.sleep(SESSION_POLL_INTERVAL_MS)
    }
    return false
  }

  fun estimateAvailableBytes(sessionId: String): Long {
    val session = getPlaybackSession(sessionId) ?: return 0L
    return estimateAvailableBytes(session, session.handle)
  }

  fun storagePath(): String = storage.rootPath()

  fun storageUsageBytes(): Long = storage.totalSizeBytes()

  @Synchronized
  fun enforceCacheLimit(cacheSizeGb: Int) {
    storage.enforceLimit(cacheSizeGb, sessionsByInfoHash.keys)
  }

  private fun estimateAvailableBytes(session: PeerPlaybackSession, handle: TorrentHandle): Long {
    val file = resolveTargetFile(session)
    if (file.exists()) {
      return minOf(file.length(), session.fileLength.takeIf { it > 0L } ?: file.length())
    }

    val totalDone = handle.status().totalDone()
    return session.fileLength
      .takeIf { it > 0L }
      ?.let { minOf(totalDone, it) }
      ?: totalDone
  }

  private fun findOrAddSource(infoHash: String, magnetLink: String, saveDirectory: File): TorrentHandle {
    val sha1Hash = Sha1Hash(infoHash)
    val existing = sessionManager.find(sha1Hash)
    if (existing != null && existing.isValid) {
      return existing
    }

    val trackerCount = magnetLink.split("&tr=").size - 1
    android.util.Log.d(TAG, "adding $infoHash to the session with $trackerCount tracker(s)")
    sessionManager.download(magnetLink, saveDirectory)

    val deadline = System.currentTimeMillis() + TORRENT_ADD_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val handle = sessionManager.find(sha1Hash)
      if (handle != null && handle.isValid) {
        return handle
      }
      Thread.sleep(SESSION_POLL_INTERVAL_MS)
    }

    android.util.Log.w(TAG, "session never accepted $infoHash within ${TORRENT_ADD_TIMEOUT_MS}ms")
    throw IllegalStateException("The peer-to-peer engine did not accept this source. Try another one.")
  }

  private fun applyProfile(profile: String) {
    val settings = SettingsPack()
    when (profile) {
      "soft" -> {
        settings.connectionsLimit(40)
        settings.downloadRateLimit(2 * 1024 * 1024)
        settings.activeDownloads(1)
      }
      "fast" -> {
        settings.connectionsLimit(120)
        settings.downloadRateLimit(0)
        settings.activeDownloads(3)
      }
      "ultra_fast" -> {
        settings.connectionsLimit(200)
        settings.downloadRateLimit(0)
        settings.activeDownloads(5)
      }
      else -> {
        settings.connectionsLimit(80)
        settings.downloadRateLimit(0)
        settings.activeDownloads(2)
      }
    }
    sessionManager.applySettings(settings)
  }

  private fun ensureTargetFile(session: PeerPlaybackSession) {
    if (session.fileIndex >= 0 && session.filePath.isNotBlank() && session.fileLength > 0L) {
      return
    }

    val handle = session.handle
    val startedAt = System.currentTimeMillis()
    val deadline = startedAt + TORRENT_METADATA_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val torrentFile = handle.torrentFile()
      if (torrentFile != null) {
        val target = selectTargetFile(torrentFile, session.preferredFilename)
        session.fileIndex = target.first
        session.filePath = target.second
        session.fileLength = target.third
        val priorities = Array(torrentFile.numFiles()) { Priority.IGNORE }
        priorities[session.fileIndex] = Priority.NORMAL
        handle.prioritizeFiles(priorities)
        android.util.Log.d(
          TAG,
          "metadata for ${session.infoHash} arrived in ${System.currentTimeMillis() - startedAt}ms; " +
            "playing file ${target.second} (${target.third} bytes)",
        )
        return
      }
      Thread.sleep(SESSION_POLL_INTERVAL_MS)
    }

    // What the swarm looked like when the wait ran out is the whole diagnosis: no peers at all is a
    // dead torrent, whereas peers without metadata is a slow one. Saying so beats "timed out".
    val status = runCatching { handle.status() }.getOrNull()
    val seeds = status?.numSeeds() ?: 0
    val peers = status?.numPeers() ?: 0
    android.util.Log.w(TAG, "no metadata for ${session.infoHash} after ${TORRENT_METADATA_TIMEOUT_MS}ms; seeds=$seeds peers=$peers")
    throw IllegalStateException(
      if (seeds == 0 && peers == 0) {
        "No peers are sharing this source right now — nothing was found to download from. Try another source."
      } else {
        "This source found $peers peer(s) but did not send its file list in time. Try another source."
      },
    )
  }

  private fun selectTargetFile(
    torrentInfo: TorrentInfo,
    preferredFilename: String?,
  ): Triple<Int, String, Long> {
    val files = torrentInfo.files()
    val preferred = preferredFilename?.trim()?.lowercase()
    var bestIndex = 0
    var bestPath = files.filePath(0)
    var bestSize = files.fileSize(0)

    for (index in 0 until torrentInfo.numFiles()) {
      val path = files.filePath(index)
      val size = files.fileSize(index)
      val normalizedPath = path.lowercase()
      if (!preferred.isNullOrBlank() && normalizedPath.contains(preferred)) {
        return Triple(index, path, size)
      }
      if (isLikelyVideoFile(path) && size >= bestSize) {
        bestIndex = index
        bestPath = path
        bestSize = size
      }
    }

    return Triple(bestIndex, bestPath, bestSize)
  }

  private fun resolveTargetFile(session: PeerPlaybackSession): File {
    return File(session.saveDirectory, session.filePath)
  }

  private fun isLikelyVideoFile(path: String): Boolean {
    val normalized = path.lowercase()
    return normalized.endsWith(".mp4")
      || normalized.endsWith(".mkv")
      || normalized.endsWith(".avi")
      || normalized.endsWith(".mov")
      || normalized.endsWith(".wmv")
      || normalized.endsWith(".m4v")
      || normalized.endsWith(".webm")
  }
}
