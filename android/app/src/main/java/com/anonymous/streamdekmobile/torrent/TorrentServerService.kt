package com.anonymous.streamdekmobile.torrent

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
import com.anonymous.streamdekmobile.R
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TorrentServerService : Service() {
  private lateinit var prefs: android.content.SharedPreferences
  private lateinit var cacheStore: StreamCacheStore
  private lateinit var torrentEngine: TorrentEngine
  private var config = TorrentServerConfig()
  private var server: LocalStreamingHttpServer? = null

  override fun onCreate() {
    super.onCreate()
    prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    cacheStore = StreamCacheStore(this)
    torrentEngine = TorrentEngine(this)
    torrentEngineRef = torrentEngine
    cacheStorePath = cacheStore.cacheDirectoryPath()
    torrentStorePath = torrentEngine.storagePath()
    lifecycleState = "created"
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      lifecycleState = "stopping"
      stopSelf()
      return START_NOT_STICKY
    }

    val newConfig = if (intent?.hasExtra(EXTRA_PORT) == true) {
      TorrentServerConfig(
        streamingMode = intent.getStringExtra(EXTRA_STREAMING_MODE) ?: TorrentServerConfig.DEFAULT_STREAMING_MODE,
        profile = intent.getStringExtra(EXTRA_PROFILE) ?: TorrentServerConfig.DEFAULT_PROFILE,
        cacheSizeGb = intent.getIntExtra(EXTRA_CACHE_SIZE_GB, TorrentServerConfig.DEFAULT_CACHE_SIZE_GB),
        port = intent.getIntExtra(EXTRA_PORT, TorrentServerConfig.DEFAULT_PORT),
        runAsForegroundService = intent.getBooleanExtra(EXTRA_RUN_AS_FOREGROUND, false),
      )
    } else {
      TorrentServerConfig.fromPreferences(prefs)
    }

    lifecycleState = "start_command"
    startOrUpdate(newConfig)
    return START_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    stopSelf()
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    server?.stop()
    server = null
    torrentEngine.stop()
    torrentEngineRef = null
    isOnline = false
    recoveryMode = "idle"
    isForegroundMode = false
    lastStartupError = null
    lifecycleState = "destroyed"
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startOrUpdate(newConfig: TorrentServerConfig) {
    config = newConfig
    config.persist(prefs.edit())
    cacheStore.enforceLimit(config.cacheSizeGb)
    torrentEngine.ensureStarted(config)
    torrentEngine.enforceCacheLimit(config.cacheSizeGb)

    recoveryMode = if (isOnline) "recovering" else "starting"
    lastStartupError = null
    foregroundDowngradeReason = null
    lifecycleState = "starting"
    ensureForegroundState()
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
          torrentEngine = torrentEngine,
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
      "Local server online at http://127.0.0.1:${config.port}"
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
    private const val PREFS_NAME = "streamdek_torrent_server"
    private const val CHANNEL_ID = "streamdek_torrent_server"
    private const val NOTIFICATION_ID = 11001

    const val ACTION_STOP = "com.anonymous.streamdekmobile.torrent.STOP"
    const val EXTRA_STREAMING_MODE = "streamingMode"
    const val EXTRA_PROFILE = "profile"
    const val EXTRA_CACHE_SIZE_GB = "cacheSizeGb"
    const val EXTRA_PORT = "port"
    const val EXTRA_RUN_AS_FOREGROUND = "runAsForegroundService"

    @Volatile var isOnline: Boolean = false
    @Volatile var isForegroundMode: Boolean = false
    @Volatile var activePort: Int = TorrentServerConfig.DEFAULT_PORT
    @Volatile var recoveryMode: String = "idle"
    @Volatile var lastStartupError: String? = null
    @Volatile var foregroundDowngradeReason: String? = null
    @Volatile var lifecycleState: String = "idle"
    @Volatile private var cacheStorePath: String? = null
    @Volatile private var torrentStorePath: String? = null
    private val proxySessions = ConcurrentHashMap<String, ProxySession>()
    @Volatile private var torrentEngineRef: TorrentEngine? = null

    fun createIntent(context: Context, config: TorrentServerConfig): Intent {
      return Intent(context, TorrentServerService::class.java).apply {
        putExtra(EXTRA_STREAMING_MODE, config.streamingMode)
        putExtra(EXTRA_PROFILE, config.profile)
        putExtra(EXTRA_CACHE_SIZE_GB, config.cacheSizeGb)
        putExtra(EXTRA_PORT, config.port)
        putExtra(EXTRA_RUN_AS_FOREGROUND, config.runAsForegroundService)
      }
    }

    fun createProxyUrl(config: TorrentServerConfig, upstreamUrl: String, headers: Map<String, String>): String {
      val sessionId = UUID.randomUUID().toString()
      proxySessions[sessionId] = ProxySession(
        upstreamUrl = upstreamUrl,
        headers = headers,
        cacheKey = cacheKeyFor(upstreamUrl),
      )
      val port = if (isOnline) activePort else config.port
      return "http://127.0.0.1:$port/proxy/$sessionId"
    }

    fun createTorrentProxyUrl(
      config: TorrentServerConfig,
      infoHash: String,
      magnetLink: String,
      preferredFilename: String?,
    ): String {
      val engine = torrentEngineRef ?: throw IllegalStateException("Torrent engine is not ready.")
      val playbackSession = engine.createPlaybackSession(config, infoHash, magnetLink, preferredFilename)
      val port = if (isOnline) activePort else config.port
      return "http://127.0.0.1:$port/torrent/${playbackSession.sessionId}"
    }

    fun getProxySession(sessionId: String): ProxySession? = proxySessions[sessionId]

    fun getTorrentPlaybackSession(sessionId: String): TorrentPlaybackSession? {
      return torrentEngineRef?.getPlaybackSession(sessionId)
    }

    fun prepareTorrentRange(sessionId: String, startByte: Long) {
      torrentEngineRef?.prepareForByteRange(sessionId, startByte)
    }

    fun waitForTorrentBytes(sessionId: String, targetByteExclusive: Long, timeoutMs: Long): Boolean {
      return torrentEngineRef?.waitForAvailableBytes(sessionId, targetByteExclusive, timeoutMs) ?: false
    }

    fun torrentBytesAvailable(sessionId: String): Long {
      return torrentEngineRef?.estimateAvailableBytes(sessionId) ?: 0L
    }

    fun snapshot(config: TorrentServerConfig): Map<String, Any> {
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
        "torrentStoreDirectory" to torrentStoreDirectory(),
        "cacheUsageBytes" to totalCacheUsageBytes(),
        "recoveryMode" to recoveryMode,
        "lastStartupError" to (lastStartupError ?: ""),
        "foregroundDowngradeReason" to (foregroundDowngradeReason ?: ""),
        "lifecycleState" to lifecycleState,
      )
    }

    fun cacheDirectory(): String = cacheStorePath ?: ""

    fun torrentStoreDirectory(): String = torrentStorePath ?: ""

    fun markStopped() {
      isOnline = false
      isForegroundMode = false
      recoveryMode = "idle"
      lifecycleState = "stopped"
      foregroundDowngradeReason = null
      lastStartupError = null
    }

    private fun totalCacheUsageBytes(): Long {
      val torrentBytes = directorySize(torrentStoreDirectory())
      val proxyBytes = directorySize(cacheDirectory())
      return torrentBytes + proxyBytes
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
    torrentEngine.enforceCacheLimit(config.cacheSizeGb)
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= TRIM_MEMORY_RUNNING_LOW) {
      torrentEngine.enforceCacheLimit(config.cacheSizeGb)
    }
  }

  override fun onRebind(intent: Intent?) {
    super.onRebind(intent)
  }

  override fun onUnbind(intent: Intent?): Boolean {
    return super.onUnbind(intent)
  }
}
