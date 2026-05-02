package com.anonymous.streamdekmobile.torrent

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMapKeySetIterator
import com.facebook.react.bridge.ReadableMap

class TorrentServerModule(
  private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
  override fun getName(): String = "TorrentServerModule"

  @ReactMethod
  fun ensureStarted(configMap: ReadableMap, promise: Promise) {
    try {
      val config = TorrentServerConfig.fromReadableMap(configMap)
      startService(config)
      promise.resolve(Arguments.makeNativeMap(awaitServiceStatus(config)))
    } catch (error: Throwable) {
      promise.reject("TORRENT_SERVER_START_FAILED", error)
    }
  }

  @ReactMethod
  fun updateConfig(configMap: ReadableMap, promise: Promise) {
    try {
      val config = TorrentServerConfig.fromReadableMap(configMap)
      startService(config)
      promise.resolve(Arguments.makeNativeMap(awaitServiceStatus(config)))
    } catch (error: Throwable) {
      promise.reject("TORRENT_SERVER_UPDATE_FAILED", error)
    }
  }

  @ReactMethod
  fun getStatus(promise: Promise) {
    try {
      val prefs = reactContext.getSharedPreferences("streamdek_torrent_server", Context.MODE_PRIVATE)
      val config = TorrentServerConfig.fromPreferences(prefs)
      promise.resolve(Arguments.makeNativeMap(TorrentServerService.snapshot(config)))
    } catch (error: Throwable) {
      promise.reject("TORRENT_SERVER_STATUS_FAILED", error)
    }
  }

  @ReactMethod
  fun stopServer(promise: Promise) {
    try {
      val prefs = reactContext.getSharedPreferences("streamdek_torrent_server", Context.MODE_PRIVATE)
      val config = TorrentServerConfig.fromPreferences(prefs)
      TorrentServerService.markStopped()
      reactContext.stopService(Intent(reactContext, TorrentServerService::class.java))
      promise.resolve(Arguments.makeNativeMap(TorrentServerService.snapshot(config)))
    } catch (error: Throwable) {
      promise.reject("TORRENT_SERVER_STOP_FAILED", error)
    }
  }

  @ReactMethod
  fun createProxySession(upstreamUrl: String, headersMap: ReadableMap?, promise: Promise) {
    try {
      val prefs = reactContext.getSharedPreferences("streamdek_torrent_server", Context.MODE_PRIVATE)
      val config = TorrentServerConfig.fromPreferences(prefs)
      val headers = mutableMapOf<String, String>()
      if (headersMap != null) {
        val iterator: ReadableMapKeySetIterator = headersMap.keySetIterator()
        while (iterator.hasNextKey()) {
          val key = iterator.nextKey()
          headers[key] = headersMap.getString(key) ?: ""
        }
      }
      ensureServiceRunning(config)
      promise.resolve(TorrentServerService.createProxyUrl(config, upstreamUrl, headers))
    } catch (error: Throwable) {
      promise.reject("TORRENT_SERVER_PROXY_FAILED", error)
    }
  }

  @ReactMethod
  fun createTorrentSession(infoHash: String, magnetLink: String, preferredFilename: String?, promise: Promise) {
    try {
      val prefs = reactContext.getSharedPreferences("streamdek_torrent_server", Context.MODE_PRIVATE)
      val config = TorrentServerConfig.fromPreferences(prefs)
      ensureServiceRunning(config)
      promise.resolve(
        TorrentServerService.createTorrentProxyUrl(
          config = config,
          infoHash = infoHash,
          magnetLink = magnetLink,
          preferredFilename = preferredFilename,
        )
      )
    } catch (error: Throwable) {
      promise.reject("TORRENT_SERVER_TORRENT_PROXY_FAILED", error)
    }
  }

  private fun ensureServiceRunning(config: TorrentServerConfig) {
    if (TorrentServerService.isOnline || TorrentServerService.lifecycleState == "starting") {
      return
    }
    startService(config)
  }

  private fun startService(config: TorrentServerConfig) {
    val intent = TorrentServerService.createIntent(reactContext, config)
    // We start from an active React screen, so a normal service start is enough and avoids
    // Android 15 foreground-start restrictions during repeated torrent session creation.
    val componentName = reactContext.startService(intent)

    if (componentName == null) {
      throw IllegalStateException("Android rejected the server service start request.")
    }
  }

  private fun awaitServiceStatus(config: TorrentServerConfig): Map<String, Any> {
    var snapshot = TorrentServerService.snapshot(config)

    repeat(20) {
      val isOnline = snapshot["isOnline"] as? Boolean ?: false
      val lastStartupError = snapshot["lastStartupError"] as? String ?: ""
      if (isOnline || lastStartupError.isNotBlank()) {
        return snapshot
      }

      SystemClock.sleep(100)
      snapshot = TorrentServerService.snapshot(config)
    }

    return snapshot
  }
}
