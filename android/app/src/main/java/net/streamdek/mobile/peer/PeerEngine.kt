package net.streamdek.mobile.peer

import android.content.Context
import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SessionParams
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.swig.alert
import com.frostwire.jlibtorrent.swig.settings_pack
import com.frostwire.jlibtorrent.alerts.DhtBootstrapAlert
import com.frostwire.jlibtorrent.alerts.ListenFailedAlert
import com.frostwire.jlibtorrent.alerts.ListenSucceededAlert
import com.frostwire.jlibtorrent.alerts.TrackerErrorAlert
import com.frostwire.jlibtorrent.alerts.TrackerReplyAlert
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
      sessionManager.addListener(discoveryListener)
      // The mask has to be part of the session's own parameters. Applied afterwards it changed
      // nothing — the session had already decided what it would post, and ninety seconds of a
      // failing download produced not one alert, not even the listen result every session emits on
      // startup. Errors, tracker traffic, status and DHT only; the peer-level categories would log
      // every block of every piece.
      val mask = alert.error_notification.to_int() or
        alert.tracker_notification.to_int() or
        alert.status_notification.to_int() or
        alert.dht_notification.to_int()
      val params = runCatching {
        SessionParams(SettingsPack().setInteger(settings_pack.int_types.alert_mask.swigValue(), mask))
      }.getOrElse {
        android.util.Log.w(TAG, "could not set the alert mask: ${it.message}")
        SessionParams()
      }
      sessionManager.start(params)
      started = true
      // What the session bound is the first thing worth knowing when nothing can be found: no
      // endpoint means no socket, and no socket means no tracker and no DHT, whatever the source.
      val endpoints = runCatching { sessionManager.listenEndpoints().joinToString() }.getOrDefault("unavailable")
      android.util.Log.d(
        TAG,
        "peer session started in ${System.currentTimeMillis() - startedAt}ms; " +
          "dht=${runCatching { sessionManager.isDhtRunning() }.getOrDefault(false)} listening on [$endpoints]",
      )
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
    mediaTitle: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
  ): PeerPlaybackSession {
    ensureStarted(config)

    val normalizedInfoHash = infoHash.lowercase()
    val existing = sessionsByInfoHash[normalizedInfoHash]
    if (existing != null) {
      if (existing.preferredFilename != preferredFilename ||
        existing.seasonNumber != seasonNumber || existing.episodeNumber != episodeNumber
      ) {
        existing.preferredFilename = preferredFilename
        existing.mediaTitle = mediaTitle
        existing.seasonNumber = seasonNumber
        existing.episodeNumber = episodeNumber
        existing.fileIndex = -1
        existing.filePath = ""
        existing.fileLength = 0L
      }
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
      mediaTitle = mediaTitle,
      seasonNumber = seasonNumber,
      episodeNumber = episodeNumber,
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

  fun waitForAvailableBytes(
    sessionId: String,
    startByte: Long,
    targetByteExclusive: Long,
    timeoutMs: Long = 45_000L,
  ): Boolean {
    val session = getPlaybackSession(sessionId) ?: return false
    val handle = session.handle
    val torrentInfo = handle.torrentFile() ?: return false
    val pieceLength = torrentInfo.pieceLength().toLong().coerceAtLeast(1L)
    val fileOffset = torrentInfo.files().fileOffset(session.fileIndex)
    val firstPiece = ((fileOffset + startByte.coerceAtLeast(0L)) / pieceLength).toInt()
    val lastPiece = ((fileOffset + (targetByteExclusive - 1).coerceAtLeast(startByte)) / pieceLength).toInt()
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeoutMs) {
      ensureTargetFile(session)
      if ((firstPiece..lastPiece).all(handle::havePiece)) {
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

  /**
   * What the session is told by the network, rather than what can be inferred from a peer count.
   *
   * Polling `numPeers()` says only that nothing was found; it cannot say whether a socket was ever
   * bound, whether a tracker answered or refused, or whether the DHT reached anyone. Those arrive
   * as alerts, and without them a failure to find peers looks the same whether the swarm is empty,
   * the trackers are unreachable or the engine never got a socket at all.
   */
  private val discoveryListener = object : AlertListener {
    // Null rather than a filter: the filtered form delivered nothing at all, not even the listen
    // result that every session posts on startup, so the filter itself was suspect. Everything
    // arrives here and the `when` below keeps only what is worth a line.
    override fun types(): IntArray? = null

    override fun alert(alert: Alert<*>) {
      when (alert) {
        is ListenSucceededAlert ->
          android.util.Log.d(TAG, "bound ${alert.socketType()} on ${alert.address()}:${alert.port()}")
        is ListenFailedAlert ->
          android.util.Log.w(
            TAG,
            "could not bind ${alert.socketType()} on ${alert.listenInterface()}: ${alert.error().message()}",
          )
        is DhtBootstrapAlert -> android.util.Log.d(TAG, "dht bootstrapped")
        is TrackerReplyAlert ->
          android.util.Log.d(TAG, "tracker ${alert.trackerUrl()} returned ${alert.numPeers()} peer(s)")
        is TrackerErrorAlert ->
          android.util.Log.w(
            TAG,
            "tracker ${alert.trackerUrl()} failed (${alert.timesInRow()}x): " +
              alert.errorMessage().ifBlank { alert.error().message() },
          )
        else -> Unit
      }
    }
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
    var lastProgressBucket = -1L
    val deadline = startedAt + TORRENT_METADATA_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      // Every ten seconds, what the wait is actually waiting on: a DHT climbing off zero and
      // trackers the engine accepted mean the swarm is simply slow; both flat mean it never got out.
      val waited = System.currentTimeMillis() - startedAt
      if (waited > 0 && waited / 10_000 != lastProgressBucket) {
        lastProgressBucket = waited / 10_000
        val live = runCatching { handle.status() }.getOrNull()
        android.util.Log.d(
          TAG,
          "waiting on ${session.infoHash}: ${waited / 1000}s dht=${runCatching { sessionManager.dhtNodes() }.getOrDefault(-1L)} " +
            "trackers=${runCatching { handle.trackers().size }.getOrDefault(-1)} " +
            "seeds=${live?.numSeeds() ?: -1} peers=${live?.numPeers() ?: -1}",
        )
      }
      val torrentFile = handle.torrentFile()
      if (torrentFile != null) {
        val files = torrentFile.files()
        val target = selectPeerVideoFile(
          candidates = (0 until torrentFile.numFiles()).map { index ->
            PeerFileCandidate(index, files.filePath(index), files.fileSize(index))
          },
          preferredFilename = session.preferredFilename,
          title = session.mediaTitle,
          season = session.seasonNumber,
          episode = session.episodeNumber,
        )
        session.fileIndex = target.index
        session.filePath = target.path
        session.fileLength = target.size
        val priorities = Array(torrentFile.numFiles()) { Priority.IGNORE }
        priorities[session.fileIndex] = Priority.NORMAL
        handle.prioritizeFiles(priorities)
        android.util.Log.d(
          TAG,
          "metadata for ${session.infoHash} arrived in ${System.currentTimeMillis() - startedAt}ms; " +
            "playing file ${target.path} (${target.size} bytes)",
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
    // The DHT node count separates the two reasons a wait ends with nothing. A swarm nobody is
    // sharing leaves the node count healthy and the peer count at zero; a network that blocks
    // peer-to-peer traffic leaves both at zero, because the same UDP the DHT needs is the UDP the
    // trackers answer on. Without it the app can only say "no peers" and the device looks broken.
    val dhtNodes = runCatching { sessionManager.dhtNodes() }.getOrDefault(-1L)
    val dhtRunning = runCatching { sessionManager.isDhtRunning() }.getOrDefault(false)
    android.util.Log.w(
      TAG,
      "no metadata for ${session.infoHash} after ${TORRENT_METADATA_TIMEOUT_MS}ms; " +
        "seeds=$seeds peers=$peers dhtRunning=$dhtRunning dhtNodes=$dhtNodes",
    )
    throw IllegalStateException(
      when {
        // Not a word about this source: the engine has not reached a single peer-to-peer node on
        // this network, which no choice of source can fix. Saying "try another source" here sent
        // people through every result in the list, each failing the same way for a minute.
        dhtRunning && dhtNodes == 0L ->
          "This network is blocking peer-to-peer traffic, so no source can be found to download " +
            "from. A VPN on a server that does not allow it will do this — try another network, or " +
            "a premium service instead."
        seeds == 0 && peers == 0 ->
          "No peers are sharing this source right now — nothing was found to download from. Try another source."
        else ->
          "This source found $peers peer(s) but did not send its file list in time. Try another source."
      },
    )
  }

  private fun resolveTargetFile(session: PeerPlaybackSession): File {
    return File(session.saveDirectory, session.filePath)
  }

}
