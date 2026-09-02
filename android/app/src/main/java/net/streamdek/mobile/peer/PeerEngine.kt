package net.streamdek.mobile.peer

import android.content.Context
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.alerts.DhtBootstrapAlert
import org.libtorrent4j.alerts.ListenFailedAlert
import org.libtorrent4j.alerts.ListenSucceededAlert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.TrackerErrorAlert
import org.libtorrent4j.alerts.TrackerReplyAlert
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

    /**
     * Where the DHT is told to look for its first nodes.
     *
     * This list is not a refinement — without it the DHT has one node to try. `SessionManager`
     * only fills in a bootstrap list from its no-argument `start()`; the `start(SessionParams)`
     * overload used below does not, and the list compiled into the native library — checked in
     * both 1.2 and 2.1 — is the single host `dht.libtorrent.org:25401`. One host, on a port
     * nothing else uses, is precisely what a
     * mobile network drops — and a DHT that never bootstraps cannot find a peer for a magnet
     * link, which is every source that reaches this engine.
     */
    private const val DHT_BOOTSTRAP_NODES =
      "dht.libtorrent.org:25401," +
        "router.bittorrent.com:6881," +
        "dht.transmissionbt.com:6881"

    /**
     * How far ahead of the read head pieces are given a deadline, in bytes.
     *
     * Measured in bytes rather than pieces because piece length scales with torrent size: a 7 GB
     * release uses 16MB pieces, so a fixed count of 48 marked three quarters of a gigabyte urgent
     * at once. Spread that thin, libtorrent asked a churning swarm for blocks all over the file
     * and completed none of them — a measured 1200 blocks arrived, some 19MB, without a single
     * piece finishing, and a piece is the smallest thing a reader can read.
     */
    private const val DEADLINE_WINDOW_BYTES = 24L * 1024L * 1024L

    /** Bounds on that window, for torrents whose pieces are very large or very small. */
    private const val MIN_DEADLINE_PIECES = 2
    private const val MAX_DEADLINE_PIECES = 64

    /** Longest the reader blocks on one piece before the swarm is treated as stalled. */
    private const val PIECE_WAIT_TIMEOUT_MS = 60_000L

    /** Alerts a busy swarm posts by the thousand, which drown the log rather than informing it. */
    private val HIGH_VOLUME_ALERTS = setOf(
      "PeerConnectAlert",
      "PeerDisconnectedAlert",
      "BlockFinishedAlert",
      "BlockDownloadingAlert",
      "BlockTimeoutAlert",
      "IncomingConnectionAlert",
      "PeerBlockedAlert",
      "DhtOutgoingGetPeersAlert",
      "TrackerAnnounceAlert",
    )
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

  /**
   * Signalled whenever a piece lands, so a blocked reader wakes on the event rather than a clock.
   *
   * The reader used to poll `havePiece` every 250ms, which on the piece it is actually waiting for
   * adds up to a quarter of a second of stall per piece for no reason. Waits on this monitor still
   * pass a timeout, so a dropped alert costs a slow re-check instead of a hang.
   */
  private val pieceArrival = Object()

  @Synchronized
  fun ensureStarted(config: PeerStreamConfig) {
    if (!started) {
      val startedAt = System.currentTimeMillis()
      sessionManager.addListener(discoveryListener)
      // No alert mask is set here on purpose. `SessionManager.start(SessionParams)` overwrites
      // whatever this pack carries for `alert_mask` with its own value — every category except
      // the log-only ones — so a mask set here has never had any effect either way. The listener
      // below already receives everything it asks for.
      val params = runCatching {
        SessionParams(sessionSettings())
      }.getOrElse {
        android.util.Log.w(TAG, "could not build the session settings: ${it.message}")
        SessionParams()
      }
      sessionManager.start(params)
      started = true
      // Deliberately not reporting the bound endpoints or the DHT here. Both are filled in from
      // alerts that have not been delivered yet a few milliseconds after start, so this line used
      // to read "dht=false listening on []" on every run — a healthy session and a session that
      // bound nothing at all looked exactly alike. The listener below reports each as it happens.
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
        existing.deadlineAnchorPiece = -1
        runCatching { existing.handle.clearPieceDeadlines() }
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

    // Both are needed, and in this order. libtorrent4j ORs the flags passed to `download` onto the
    // defaults rather than replacing them, and those defaults include auto-managed and paused — so
    // a torrent added for playback would be resumed here and then quietly re-paused by the queue
    // manager the moment it decided something else deserved the slot.
    handle.unsetFlags(TorrentFlags.AUTO_MANAGED)
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

    val window = fileWindow(session) ?: return
    val firstPiece = window.pieceAt(startByte.coerceAtLeast(0L))
    val lastPiece = window.pieceAt(window.fileLength - 1)
    val windowPieces = window.piecesSpanning(DEADLINE_WINDOW_BYTES)

    // A seek that lands outside the window we were already working on leaves deadlines behind on
    // pieces nobody is going to read. Clearing them first keeps libtorrent working on the part of
    // the file the viewer is actually at.
    val anchor = session.deadlineAnchorPiece
    if (anchor < 0 || firstPiece < anchor || firstPiece > anchor + windowPieces) {
      runCatching { handle.clearPieceDeadlines() }
    }
    session.deadlineAnchorPiece = firstPiece

    // Deadlines are milliseconds from now, so increasing them across the window states the order
    // the reader will want the pieces in. Giving every piece the same deadline says only that all
    // of them are urgent, which is the same as saying none of them is. A second per piece, rather
    // than the tenth of a second this used to spend, because the reader needs the first one far
    // more than it needs the twentieth and the gap should say so.
    var position = 0
    for (piece in firstPiece..minOf(lastPiece, firstPiece + windowPieces - 1)) {
      try {
        handle.setPieceDeadline(piece, position * 1_000)
      } catch (_: Throwable) {
        break
      }
      position += 1
    }
  }

  /**
   * How many bytes can be read from [offset] right now without waiting for the swarm.
   *
   * Answered from the piece picker rather than the file's length: the file is allocated sparse, so
   * its length is the finished size from the moment it is created and says nothing at all about
   * what has actually arrived.
   *
   * That a piece the picker reports is a piece the reader can read is libtorrent 2's doing — its
   * mmap disk backend writes through, so there is no write cache holding data the file does not
   * have yet. Under libtorrent 1.2 this needed `cache_size` pinned to zero to be true at all, and
   * the setting no longer exists.
   */
  fun contiguousAvailableFrom(sessionId: String, offset: Long): Long {
    val session = getPlaybackSession(sessionId) ?: return 0L
    return fileWindow(session)?.contiguousFrom(offset) ?: 0L
  }

  /**
   * Blocks until the byte at [offset] is readable, or [timeoutMs] passes with it still missing.
   *
   * Woken by the piece alerts rather than by a poll, so the reader resumes when the piece lands
   * instead of up to a quarter-second afterwards. The bounded wait is a backstop for an alert that
   * never arrives, not the mechanism.
   */
  fun awaitByteAvailable(sessionId: String, offset: Long, timeoutMs: Long = PIECE_WAIT_TIMEOUT_MS): Boolean {
    val session = getPlaybackSession(sessionId) ?: return false
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
      if (contiguousAvailableFrom(sessionId, offset) > 0L) {
        storage.touch(session.infoHash)
        return true
      }
      val remaining = deadline - System.currentTimeMillis()
      if (remaining <= 0L) return false
      synchronized(pieceArrival) {
        pieceArrival.wait(minOf(remaining, SESSION_POLL_INTERVAL_MS * 2))
      }
    }
  }

  /** Where on disk the chosen file lives, for a reader that has its own view of what has arrived. */
  fun targetFile(sessionId: String): File? {
    val session = getPlaybackSession(sessionId) ?: return null
    if (session.filePath.isBlank()) return null
    return resolveTargetFile(session)
  }

  fun storagePath(): String = storage.rootPath()

  fun storageUsageBytes(): Long = storage.totalSizeBytes()

  @Synchronized
  fun enforceCacheLimit(cacheSizeGb: Int) {
    storage.enforceLimit(cacheSizeGb, sessionsByInfoHash.keys)
  }

  /**
   * Throws away every stored torrent except [activeInfoHashes], and returns the bytes freed.
   *
   * The set is supplied by the caller rather than taken from [sessionsByInfoHash], which is what
   * this used to do and what made "Clear Storage Now" unable to clear anything. That map is only
   * emptied by [stop]: nothing removes an entry when a playback ends, so every torrent opened since
   * the engine came up stayed in it and was spared here as though it were still on screen. With
   * "Keep Engine Ready" on the engine does not stop between viewings, so after watching one
   * peer-to-peer source the button could no longer delete a single byte — and reported that as
   * there being nothing to delete.
   *
   * Whoever presses the button knows what is playing; this does not, and guessing from a map that
   * only grows is worse than being told.
   */
  @Synchronized
  fun clearStorage(activeInfoHashes: Set<String>): Long =
    storage.clearAll(activeInfoHashes.map { it.lowercase() }.toSet())

  /**
   * The chosen file's place in the torrent's piece layout.
   *
   * Everything the reader needs is derived from this: a torrent's pieces span the whole payload,
   * so a byte offset inside one file only means something once the file's own offset is added to
   * it.
   */
  private class FileWindow(
    private val handle: TorrentHandle,
    private val pieceLength: Long,
    private val fileOffset: Long,
    val fileLength: Long,
  ) {
    fun pieceAt(offset: Long): Int = ((fileOffset + offset.coerceAtLeast(0L)) / pieceLength).toInt()

    /** How many pieces it takes to cover [bytes], within the bounds a deadline window may take. */
    fun piecesSpanning(bytes: Long): Int =
      ((bytes + pieceLength - 1) / pieceLength).toInt()
        .coerceIn(MIN_DEADLINE_PIECES, MAX_DEADLINE_PIECES)

    val pieceLengthBytes: Long get() = pieceLength

    fun contiguousFrom(offset: Long): Long {
      if (offset < 0L || offset >= fileLength) return 0L
      var piece = pieceAt(offset)
      if (!has(piece)) return 0L
      // The rest of the piece the offset falls in, then whole pieces for as long as they are here.
      var available = pieceLength - ((fileOffset + offset) % pieceLength)
      while (offset + available < fileLength && has(piece + 1)) {
        available += pieceLength
        piece += 1
      }
      return minOf(available, fileLength - offset)
    }

    private fun has(piece: Int): Boolean = runCatching { handle.havePiece(piece) }.getOrDefault(false)
  }

  private fun fileWindow(session: PeerPlaybackSession): FileWindow? {
    val torrentInfo = session.handle.torrentFile() ?: return null
    if (session.fileIndex < 0 || session.fileLength <= 0L) return null
    val pieceLength = torrentInfo.pieceLength().toLong().coerceAtLeast(1L)
    return FileWindow(
      handle = session.handle,
      pieceLength = pieceLength,
      fileOffset = torrentInfo.files().fileOffset(session.fileIndex),
      fileLength = session.fileLength,
    )
  }

  private fun findOrAddSource(infoHash: String, magnetLink: String, saveDirectory: File): TorrentHandle {
    val sha1Hash = Sha1Hash.parseHex(infoHash)
    val existing = sessionManager.find(sha1Hash)
    if (existing != null && existing.isValid) {
      return existing
    }

    val trackerCount = magnetLink.split("&tr=").size - 1
    android.util.Log.d(TAG, "adding $infoHash to the session with $trackerCount tracker(s)")
    sessionManager.download(magnetLink, saveDirectory, TorrentFlags.SEQUENTIAL_DOWNLOAD)

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
        // Both of these are what a blocked reader is waiting on: a piece it needs, or the file
        // list that tells it which pieces those are.
        is PieceFinishedAlert, is MetadataReceivedAlert ->
          synchronized(pieceArrival) { pieceArrival.notifyAll() }
        is ListenSucceededAlert ->
          android.util.Log.d(TAG, "bound ${alert.socketType()} on ${alert.address()}:${alert.port()}")
        is ListenFailedAlert ->
          android.util.Log.w(
            TAG,
            "could not bind ${alert.socketType()} on ${alert.listenInterface()}: ${alert.error().message}",
          )
        is DhtBootstrapAlert -> android.util.Log.d(TAG, "dht bootstrapped")
        is TrackerReplyAlert ->
          android.util.Log.d(TAG, "tracker ${alert.trackerUrl()} returned ${alert.numPeers()} peer(s)")
        is TrackerErrorAlert ->
          android.util.Log.w(
            TAG,
            "tracker ${alert.trackerUrl()} failed (${alert.timesInRow()}x): " +
              alert.errorMessage().ifBlank { alert.error().message },
          )
        // Everything else by name only, at verbose, minus the per-peer and per-block chatter.
        //
        // Silence from this listener is ambiguous on its own — it reads the same whether the
        // session is posting nothing or the categories above simply have not happened yet — and
        // telling those apart is most of diagnosing a stalled swarm. But a busy swarm posts
        // connect and disconnect alerts by the thousand, which pushed everything else out of the
        // log buffer and cost more than it explained. The name is all that is safe to read in any
        // case: `message()` crosses into native code that aborts the process for some alert types,
        // which is a SIGABRT no `runCatching` can hold.
        else -> {
          val name = alert.javaClass.simpleName
          if (name !in HIGH_VOLUME_ALERTS) android.util.Log.v(TAG, "alert $name")
        }
      }
    }
  }

  /**
   * The settings the session is created with, as opposed to the ones a profile changes later.
   *
   * These belong in the session's own parameters rather than an `applySettings` call afterwards:
   * the bootstrap list is read when the DHT starts, which happens as the session is constructed,
   * so a list applied a moment later would not be used until something restarted the DHT.
   */
  private fun sessionSettings(): SettingsPack = SettingsPack().apply {
    // An ephemeral IPv4 port, rather than libtorrent's default of `0.0.0.0:6881,[::]:6881`.
    //
    // 6881 is the port every firewall knows peer-to-peer traffic by, and IPv6 is not available on
    // every mobile network, so neither is worth asking for by name. Note that this is not the cure
    // for the `bind: Permission denied` the session still reports on cellular — an ephemeral port
    // and an IPv4-only bind were both measured against it and neither changed the error — it is
    // only the narrowest request the engine can make, which is what it should be making anyway.
    setString(settings_pack.string_types.listen_interfaces.swigValue(), "0.0.0.0:0")
    setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), DHT_BOOTSTRAP_NODES)
    setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
    // Everything that wants a multicast group is off: local discovery, and the two port-mapping
    // protocols, which announce over SSDP. Android guards multicast separately from ordinary
    // sockets, and these are the parts of a libtorrent session that ask for it. On a phone they
    // buy nothing in any case — port mapping needs a router this device is behind rather than a
    // carrier NAT, and local discovery only finds a seed sitting on the same home network.
    setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), false)
    setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false)
    setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false)
    // A mobile client is almost always behind a NAT it cannot open a port on, so it will be making
    // the connections rather than receiving them. UDP trackers answer fastest when they answer.
    setBoolean(settings_pack.bool_types.prefer_udp_trackers.swigValue(), true)
    // Finish pieces already started before beginning new ones. A player cannot read a byte until
    // the whole piece containing it is complete, so a picker spreading its requests over the swarm
    // is the worst possible shape for streaming: 19MB of blocks arrived on one measured attempt
    // without a single piece finishing, and the reader waited its whole timeout for byte zero.
    setBoolean(settings_pack.bool_types.prioritize_partial_pieces.swigValue(), true)
    // Keep a peer's requests within one piece's region rather than scattering them, for the same
    // reason: whole pieces sooner beats a uniformly incomplete file.
    setBoolean(settings_pack.bool_types.piece_extent_affinity.swigValue(), true)
    // uTP as well as TCP in both directions. Some mobile networks shape one and not the other,
    // and a torrent that can only speak the shaped one looks identical to a dead swarm.
    setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)
    setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), true)
    setBoolean(settings_pack.bool_types.enable_outgoing_tcp.swigValue(), true)
    setBoolean(settings_pack.bool_types.enable_incoming_tcp.swigValue(), true)
    // Announce to every tracker in every tier at once. The default walks the tiers in order and
    // waits for each to answer or time out, which on a magnet carrying a dozen announce URLs is
    // minutes before the last one is tried — longer than anybody waits at a launch screen.
    setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
    setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
    setString(
      settings_pack.string_types.user_agent.swigValue(),
      "StreamDek/${net.streamdek.mobile.BuildConfig.VERSION_NAME} libtorrent/1.2.0",
    )
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
        priorities[session.fileIndex] = Priority.DEFAULT
        handle.prioritizeFiles(priorities)
        android.util.Log.d(
          TAG,
          "metadata for ${session.infoHash} arrived in ${System.currentTimeMillis() - startedAt}ms; " +
            "playing file ${target.path} (${target.size} bytes) " +
            // Piece length decides how much has to arrive before a single byte can be read, so it
            // is the number that explains a reader waiting on a torrent that is plainly downloading.
            "in ${torrentFile.pieceLength()}-byte pieces",
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
