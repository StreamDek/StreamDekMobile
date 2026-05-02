package com.anonymous.streamdekmobile.torrent

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

class TorrentEngine(
  context: Context,
) {
  companion object {
    private const val SESSION_POLL_INTERVAL_MS = 250L
    private const val TORRENT_ADD_TIMEOUT_MS = 4_000L
    private const val TORRENT_METADATA_TIMEOUT_MS = 4_000L
  }

  private val storage = TorrentStorageManager(context)
  private val sessionManager = SessionManager()
  private val playbackSessions = linkedMapOf<String, TorrentPlaybackSession>()
  private val sessionsByInfoHash = linkedMapOf<String, TorrentPlaybackSession>()
  @Volatile private var started = false

  @Synchronized
  fun ensureStarted(config: TorrentServerConfig) {
    if (!started) {
      sessionManager.start()
      started = true
    }
    applyProfile(config.profile)
    storage.enforceLimit(config.cacheSizeGb, sessionsByInfoHash.keys)
  }

  @Synchronized
  fun stop() {
    if (!started) return
    playbackSessions.clear()
    sessionsByInfoHash.clear()
    sessionManager.stop()
    started = false
  }

  @Synchronized
  fun createPlaybackSession(
    config: TorrentServerConfig,
    infoHash: String,
    magnetLink: String,
    preferredFilename: String?,
  ): TorrentPlaybackSession {
    ensureStarted(config)

    val normalizedInfoHash = infoHash.lowercase()
    val existing = sessionsByInfoHash[normalizedInfoHash]
    if (existing != null) {
      storage.touch(normalizedInfoHash)
      ensureTargetFile(existing)
      return existing
    }

    val saveDirectory = storage.sessionDirectory(normalizedInfoHash)
    val handle = findOrAddTorrent(normalizedInfoHash, magnetLink, saveDirectory)
    val session = TorrentPlaybackSession(
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

    ensureTargetFile(session)
    playbackSessions[session.sessionId] = session
    sessionsByInfoHash[normalizedInfoHash] = session
    storage.touch(normalizedInfoHash)
    return session
  }

  fun getPlaybackSession(sessionId: String): TorrentPlaybackSession? {
    return playbackSessions[sessionId]
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

  fun waitForAvailableBytes(sessionId: String, targetByteExclusive: Long, timeoutMs: Long = 15_000L): Boolean {
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

  private fun estimateAvailableBytes(session: TorrentPlaybackSession, handle: TorrentHandle): Long {
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

  private fun findOrAddTorrent(infoHash: String, magnetLink: String, saveDirectory: File): TorrentHandle {
    val sha1Hash = Sha1Hash(infoHash)
    val existing = sessionManager.find(sha1Hash)
    if (existing != null && existing.isValid) {
      return existing
    }

    sessionManager.download(magnetLink, saveDirectory)

    val deadline = System.currentTimeMillis() + TORRENT_ADD_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val handle = sessionManager.find(sha1Hash)
      if (handle != null && handle.isValid) {
        return handle
      }
      Thread.sleep(SESSION_POLL_INTERVAL_MS)
    }

    throw IllegalStateException("Timed out while adding torrent session.")
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

  private fun ensureTargetFile(session: TorrentPlaybackSession) {
    if (session.fileIndex >= 0 && session.filePath.isNotBlank() && session.fileLength > 0L) {
      return
    }

    val handle = session.handle
    val deadline = System.currentTimeMillis() + TORRENT_METADATA_TIMEOUT_MS
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
        return
      }
      Thread.sleep(SESSION_POLL_INTERVAL_MS)
    }

    throw IllegalStateException("Timed out while waiting for torrent metadata.")
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

  private fun resolveTargetFile(session: TorrentPlaybackSession): File {
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
