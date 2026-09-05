package net.streamdek.mobile.peer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.streamdek.mobile.R
import net.streamdek.mobile.nativeapp.localizedAppContext

/**
 * What a "Clear Storage Now" actually achieved, measured from the disk.
 *
 * Two numbers rather than one, because "nothing was freed" has two very different causes and the
 * screen has to tell them apart: there was nothing stored, or there was and it could not be
 * removed. Reporting only the delta meant the second was announced as the first.
 */
data class PeerStorageClearResult(val freedBytes: Long, val remainingBytes: Long) {
  /** True when the stores are now empty, whether or not this call is what emptied them. */
  val isEmpty: Boolean get() = remainingBytes <= 0L
}

class PeerStreamService : Service() {
  private lateinit var prefs: android.content.SharedPreferences
  private lateinit var cacheStore: StreamCacheStore
  private lateinit var peerEngine: PeerEngine
  private var config = PeerStreamConfig()
  private var server: LocalStreamingHttpServer? = null

  /**
   * Where the service's blocking work happens. Single-threaded so repeated start commands queue
   * behind each other rather than racing to start two sessions on the same port.
   */
  private val startupExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "streamdek-peer-stream").apply { isDaemon = true }
  }

  override fun onCreate() {
    super.onCreate()
    prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    migrateLegacyPreferences()
    cacheStore = StreamCacheStore(this)
    peerEngine = PeerEngine(this)
    peerEngineRef = peerEngine
    cacheStoreRef = cacheStore
    cacheStorePath = cacheStore.cacheDirectoryPath()
    peerStorePath = peerEngine.storagePath()
    lifecycleState = "created"
    createNotificationChannel()
  }

  /**
   * Adopts settings written under the previous name of this service.
   *
   * Renaming the preferences file would otherwise silently reset every viewer's peer-to-peer
   * configuration — cache size, profile, whether it runs at all — on the update that renamed it.
   */
  private fun migrateLegacyPreferences() {
    if (prefs.all.isNotEmpty()) return
    val legacy = getSharedPreferences("streamdek_torrent_server", Context.MODE_PRIVATE)
    if (legacy.all.isEmpty()) return
    prefs.edit().apply {
      legacy.all.forEach { (key, value) ->
        when (value) {
          is Boolean -> putBoolean(key, value)
          is Int -> putInt(key, value)
          is Long -> putLong(key, value)
          is Float -> putFloat(key, value)
          is String -> putString(key, value)
        }
      }
    }.apply()
    legacy.edit().clear().apply()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      lifecycleState = "stopping"
      stopSelf()
      return START_NOT_STICKY
    }

    val newConfig = if (intent?.hasExtra(EXTRA_PORT) == true) {
      PeerStreamConfig(
        enabled = intent.getBooleanExtra(EXTRA_ENABLED, true),
        streamingMode = intent.getStringExtra(EXTRA_STREAMING_MODE) ?: PeerStreamConfig.DEFAULT_STREAMING_MODE,
        profile = intent.getStringExtra(EXTRA_PROFILE) ?: PeerStreamConfig.DEFAULT_PROFILE,
        cacheSizeGb = intent.getIntExtra(EXTRA_CACHE_SIZE_GB, PeerStreamConfig.DEFAULT_CACHE_SIZE_GB),
        port = intent.getIntExtra(EXTRA_PORT, PeerStreamConfig.DEFAULT_PORT),
        runAsForegroundService = intent.getBooleanExtra(EXTRA_RUN_AS_FOREGROUND, false),
      )
    } else {
      PeerStreamConfig.fromPreferences(prefs)
    }

    lifecycleState = "start_command"
    config = newConfig
    config.persist(prefs.edit())
    // Promotion has to happen on this thread and quickly — Android gives a service started with
    // startForegroundService a few seconds to call startForeground before it kills it.
    ensureForegroundState()
    // Everything else is deliberately off the main thread. Starting a libtorrent session, walking
    // the cache directory and binding a socket are all blocking work, and doing them here is what
    // produced the ANR in PeerEngine.ensureStarted that killed the process mid-playback — so no
    // torrent ever reached the player.
    startupExecutor.execute {
      runCatching { startOrUpdate(config) }.onFailure { error ->
        lastStartupError = error.message ?: error.javaClass.simpleName
        lifecycleState = "start_failed"
        isOnline = false
      }
    }
    return START_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    stopSelf()
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    // Shutting a libtorrent session down blocks too, so it goes to the same thread the start ran
    // on. The flags are cleared here so nothing treats the server as live in the meantime.
    val closing = server
    server = null
    startupExecutor.execute {
      runCatching { closing?.stop() }
      runCatching { peerEngine.stop() }
    }
    startupExecutor.shutdown()
    peerEngineRef = null
    cacheStoreRef = null
    isOnline = false
    recoveryMode = "idle"
    isForegroundMode = false
    lastStartupError = null
    lifecycleState = "destroyed"
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  /** Runs on [startupExecutor], never the main thread. */
  private fun startOrUpdate(newConfig: PeerStreamConfig) {
    recoveryMode = if (isOnline) "recovering" else "starting"
    lastStartupError = null
    lifecycleState = "starting"
    cacheStore.enforceLimit(newConfig.cacheSizeGb)
    peerEngine.ensureStarted(newConfig)
    peerEngine.enforceCacheLimit(newConfig.cacheSizeGb)
    startHttpServer()
  }

  private fun startHttpServer() {
    server?.stop()
    server = null

    val candidatePorts = linkedSetOf<Int>().apply {
      add(config.port)
      for (offset in 1..10) add(config.port + offset)
    }

    for (port in candidatePorts) {
      try {
        val nextServer = LocalStreamingHttpServer(
          configProvider = { config },
          statusProvider = { snapshot(config) },
          cacheStore = cacheStore,
          peerEngine = peerEngine,
        )
        val boundPort = nextServer.start(port)

        server = nextServer
        activePort = boundPort
        isOnline = true
        recoveryMode = if (boundPort == config.port) "running" else "recovering"
        lastStartupError = null
        lifecycleState = "running"
        updateForegroundNotification()
        return
      } catch (error: Throwable) {
        lastStartupError = error.message ?: error.javaClass.simpleName
        lifecycleState = "start_failed"
        // Try the next localhost port before giving up.
      }
    }

    isOnline = false
    recoveryMode = "recovering"
    lifecycleState = "start_failed"
    updateForegroundNotification()
  }

  private fun ensureForegroundState() {
    if (config.runAsForegroundService) {
      val notificationsAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }

      if (!notificationsAllowed) {
        isForegroundMode = false
        foregroundDowngradeReason = "Foreground mode needs notification permission on this device."
        return
      }

      try {
        isForegroundMode = true
        startForeground(NOTIFICATION_ID, buildNotification())
      } catch (error: Throwable) {
        isForegroundMode = false
        foregroundDowngradeReason = error.message ?: "Foreground mode unavailable"
      }
    } else {
      isForegroundMode = false
      foregroundDowngradeReason = null
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        stopForeground(STOP_FOREGROUND_REMOVE)
      } else {
        @Suppress("DEPRECATION")
        stopForeground(true)
      }
    }
  }

  private fun updateForegroundNotification() {
    if (!config.runAsForegroundService || !isForegroundMode) return

    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(NOTIFICATION_ID, buildNotification())
  }

  private fun buildNotification(): Notification {
    val text = if (isOnline) {
      "Local server online at http://127.0.0.1:$activePort"
    } else {
      "Recovering local streaming server"
    }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("StreamDek Server")
      .setContentText(text)
      .setSmallIcon(R.drawable.ic_stat_streamdek)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
      CHANNEL_ID,
      "StreamDek Server",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      // See EpisodeNotificationSystem: a service has no composition, and the application context
      // is never locale-wrapped.
      description = localizedAppContext(this@PeerStreamService).getString(R.string.peer_service_channel_description)
    }
    manager.createNotificationChannel(channel)
  }

  companion object {
    private const val PREFS_NAME = "streamdek_peer_stream"
    private const val CHANNEL_ID = "streamdek_peer_stream"
    private const val NOTIFICATION_ID = 11001

    const val ACTION_STOP = "net.streamdek.mobile.peer.STOP"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_STREAMING_MODE = "streamingMode"
    const val EXTRA_PROFILE = "profile"
    const val EXTRA_CACHE_SIZE_GB = "cacheSizeGb"
    const val EXTRA_PORT = "port"
    const val EXTRA_RUN_AS_FOREGROUND = "runAsForegroundService"

    @Volatile var isOnline: Boolean = false
    @Volatile var isForegroundMode: Boolean = false
    @Volatile var activePort: Int = PeerStreamConfig.DEFAULT_PORT
    @Volatile var recoveryMode: String = "idle"
    @Volatile var lastStartupError: String? = null
    @Volatile var foregroundDowngradeReason: String? = null
    @Volatile var lifecycleState: String = "idle"
    @Volatile private var cacheStorePath: String? = null
    @Volatile private var peerStorePath: String? = null
    private val proxySessions = ConcurrentHashMap<String, ProxySession>()
    @Volatile private var peerEngineRef: PeerEngine? = null
    @Volatile private var cacheStoreRef: StreamCacheStore? = null

    fun createIntent(context: Context, config: PeerStreamConfig): Intent {
      return Intent(context, PeerStreamService::class.java).apply {
        putExtra(EXTRA_ENABLED, config.enabled)
        putExtra(EXTRA_STREAMING_MODE, config.streamingMode)
        putExtra(EXTRA_PROFILE, config.profile)
        putExtra(EXTRA_CACHE_SIZE_GB, config.cacheSizeGb)
        putExtra(EXTRA_PORT, config.port)
        putExtra(EXTRA_RUN_AS_FOREGROUND, config.runAsForegroundService)
      }
    }

    fun createProxyUrl(config: PeerStreamConfig, upstreamUrl: String, headers: Map<String, String>): String {
      val sessionId = UUID.randomUUID().toString()
      proxySessions[sessionId] = ProxySession(
        upstreamUrl = upstreamUrl,
        headers = headers,
        cacheKey = cacheKeyFor(upstreamUrl),
      )
      val port = if (isOnline) activePort else config.port
      return "http://127.0.0.1:$port/proxy/$sessionId"
    }

    fun createPeerProxyUrl(
      config: PeerStreamConfig,
      infoHash: String,
      magnetLink: String,
      preferredFilename: String?,
      mediaTitle: String?,
      seasonNumber: Int?,
      episodeNumber: Int?,
    ): String {
      val engine = peerEngineRef ?: throw IllegalStateException("Torrent engine is not ready.")
      val playbackSession = engine.createPlaybackSession(
        config, infoHash, magnetLink, preferredFilename, mediaTitle, seasonNumber, episodeNumber,
      )
      val port = if (isOnline) activePort else config.port
      return "http://127.0.0.1:$port/torrent/${playbackSession.sessionId}"
    }

    /** The swarm behind the given torrent while it warms up, or null when it is not the live one. */
    fun latestSwarmStats(expectedInfoHash: String?): SwarmStats? =
      peerEngineRef?.latestSwarmStats(expectedInfoHash)

    fun getProxySession(sessionId: String): ProxySession? = proxySessions[sessionId]

    fun getPeerPlaybackSession(sessionId: String): PeerPlaybackSession? {
      return peerEngineRef?.getPlaybackSession(sessionId)
    }

    fun preparePeerRange(sessionId: String, startByte: Long) {
      peerEngineRef?.prepareForByteRange(sessionId, startByte)
    }

    fun awaitPeerByte(sessionId: String, offset: Long, timeoutMs: Long): Boolean {
      return peerEngineRef?.awaitByteAvailable(sessionId, offset, timeoutMs) ?: false
    }

    fun peerContiguousBytes(sessionId: String, offset: Long): Long {
      return peerEngineRef?.contiguousAvailableFrom(sessionId, offset) ?: 0L
    }

    fun peerTargetFile(sessionId: String): File? = peerEngineRef?.targetFile(sessionId)

    /**
     * Empties both stores behind the "Storage Used" figure.
     *
     * Both, because that figure is their sum: the peer store holding torrent data and the cache
     * holding proxied web streams. Clearing one and reporting the other would leave a viewer who
     * pressed the button looking at a number that had barely moved.
     *
     * [activePeerSessionId] is the peer playback the viewer is watching right now, if any — the
     * `sessionId` from the local URL the player is reading. Its torrent is the one thing spared;
     * everything else goes. Pass null, as the settings screen does, and nothing is spared, because
     * nothing can be playing while that screen is on top.
     *
     * Both figures come from measuring the directories before and after rather than from what the
     * delete calls claim to have removed. That distinction is the whole point: a file that could
     * not be unlinked used to be counted as freed, so a clear that achieved nothing still reported
     * a total, and — when the total came out at zero — said there had been nothing there at all.
     */
    fun clearStoredData(activePeerSessionId: String? = null): PeerStorageClearResult {
      val before = measureStoredBytes()
      val activeInfoHashes = activePeerSessionId
        ?.let { peerEngineRef?.getPlaybackSession(it)?.infoHash }
        ?.let { setOf(it) }
        .orEmpty()
      // Through the live objects when the service is running, so that whatever is being played is
      // spared; straight off disk when it is not, because then there is nothing to spare and the
      // files are still there to delete.
      peerEngineRef?.clearStorage(activeInfoHashes) ?: deleteDirectoryContents(peerStoreDirectory())
      cacheStoreRef?.clearAll() ?: deleteDirectoryContents(cacheDirectory())
      // Re-measured rather than deduced, which also refreshes the ten-second cache behind
      // `snapshot` — without that the settings screen would go on showing the old total for that
      // long after the files were gone.
      val remaining = measureStoredBytes()
      return PeerStorageClearResult(
        freedBytes = (before - remaining).coerceAtLeast(0L),
        remainingBytes = remaining,
      )
    }

    /**
     * Walks both stores now, ignoring the cache in [totalCacheUsageBytes], and adopts the result as
     * the current figure.
     *
     * Call it off the main thread. The settings screen uses it so that the number it shows is what
     * is on disk at the moment it is opened, rather than whatever was last measured — which on a
     * launch where nothing has been played is nothing at all, and left the screen reporting "None"
     * over gigabytes with its Clear button greyed out.
     */
    fun measureStoredBytes(): Long {
      val measured = directorySize(peerStoreDirectory()) + directorySize(cacheDirectory())
      cachedUsageBytes = measured
      cachedUsageAt = System.currentTimeMillis()
      return measured
    }

    fun snapshot(config: PeerStreamConfig): Map<String, Any> {
      val port = if (isOnline) activePort else config.port
      return mapOf(
        "isOnline" to isOnline,
        "isForeground" to (if (isOnline) isForegroundMode else config.runAsForegroundService),
        "requestedForeground" to config.runAsForegroundService,
        "port" to port,
        "streamingMode" to config.streamingMode,
        "url" to "http://127.0.0.1:$port",
        "profile" to config.profile,
        "cacheSizeGb" to config.cacheSizeGb,
        "cacheDirectory" to cacheDirectory(),
        "peerStoreDirectory" to peerStoreDirectory(),
        "cacheUsageBytes" to totalCacheUsageBytes(),
        "recoveryMode" to recoveryMode,
        "lastStartupError" to (lastStartupError ?: ""),
        "foregroundDowngradeReason" to (foregroundDowngradeReason ?: ""),
        "lifecycleState" to lifecycleState,
      )
    }

    fun cacheDirectory(): String = cacheStorePath ?: ""

    fun peerStoreDirectory(): String = peerStorePath ?: ""

    /**
     * Works out where the two stores live without the service having run in this process.
     *
     * Both paths are otherwise only filled in by `onCreate`, so on a launch where nothing has been
     * played the settings screen measured two empty strings and reported no storage at all — while
     * gigabytes of it sat in the cache directory, with the button to clear it disabled because the
     * figure said there was nothing there.
     */
    fun primeStoragePaths(context: Context) {
      val cacheDir = context.applicationContext.cacheDir
      if (peerStorePath.isNullOrBlank()) peerStorePath = File(cacheDir, "streamdek-peer-store").absolutePath
      if (cacheStorePath.isNullOrBlank()) cacheStorePath = File(cacheDir, "streamdek-server-cache").absolutePath
    }

    fun markStopped() {
      isOnline = false
      isForegroundMode = false
      recoveryMode = "idle"
      lifecycleState = "stopped"
      foregroundDowngradeReason = null
      lastStartupError = null
    }

    /** How long a measured cache size is reused before the directories are walked again. */
    private const val CACHE_USAGE_TTL_MS = 10_000L
    @Volatile private var cachedUsageBytes = 0L
    @Volatile private var cachedUsageAt = 0L

    /**
     * Recursively measuring a cache that may hold gigabytes is not something to do on every call,
     * and [snapshot] is read by each HTTP request as well as by the settings screen.
     */
    private fun totalCacheUsageBytes(): Long {
      val now = System.currentTimeMillis()
      if (now - cachedUsageAt < CACHE_USAGE_TTL_MS) return cachedUsageBytes
      val measured = directorySize(peerStoreDirectory()) + directorySize(cacheDirectory())
      cachedUsageBytes = measured
      cachedUsageAt = now
      return measured
    }

    /**
     * Empties a store directory in place.
     *
     * Says nothing about what it freed; [clearStoredData] measures that itself, from the disk, so
     * that a delete which quietly failed cannot be reported as a success.
     */
    private fun deleteDirectoryContents(path: String) {
      if (path.isBlank()) return
      val dir = File(path).takeIf { it.isDirectory } ?: return
      dir.listFiles()?.forEach { entry -> runCatching { entry.deleteRecursively() } }
    }

    private fun directorySize(path: String): Long {
      if (path.isBlank()) return 0L
      return try {
        File(path)
          .takeIf { it.exists() && it.isDirectory }
          ?.walkTopDown()
          ?.filter { it.isFile }
          ?.fold(0L) { total, file -> total + file.length() }
          ?: 0L
      } catch (_: Throwable) {
        0L
      }
    }

    private fun cacheKeyFor(upstreamUrl: String): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(upstreamUrl.toByteArray())
      return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
  }

  override fun onLowMemory() {
    super.onLowMemory()
    peerEngine.enforceCacheLimit(config.cacheSizeGb)
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= TRIM_MEMORY_RUNNING_LOW) {
      peerEngine.enforceCacheLimit(config.cacheSizeGb)
    }
  }

  override fun onRebind(intent: Intent?) {
    super.onRebind(intent)
  }

  override fun onUnbind(intent: Intent?): Boolean {
    return super.onUnbind(intent)
  }
}

