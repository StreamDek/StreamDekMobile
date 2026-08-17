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
import net.streamdek.mobile.R
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
      .setSmallIcon(R.mipmap.ic_launcher)
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
      description = "Keeps the local mobile streaming server alive when foreground mode is enabled."
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
    ): String {
      val engine = peerEngineRef ?: throw IllegalStateException("Torrent engine is not ready.")
      val playbackSession = engine.createPlaybackSession(config, infoHash, magnetLink, preferredFilename)
      val port = if (isOnline) activePort else config.port
      return "http://127.0.0.1:$port/peer/${playbackSession.sessionId}"
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

    fun waitForPeerBytes(sessionId: String, targetByteExclusive: Long, timeoutMs: Long): Boolean {
      return peerEngineRef?.waitForAvailableBytes(sessionId, targetByteExclusive, timeoutMs) ?: false
    }

    fun peerBytesAvailable(sessionId: String): Long {
      return peerEngineRef?.estimateAvailableBytes(sessionId) ?: 0L
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

