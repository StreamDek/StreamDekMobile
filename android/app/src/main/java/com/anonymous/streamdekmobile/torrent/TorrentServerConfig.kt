package com.anonymous.streamdekmobile.torrent

import android.content.SharedPreferences
import com.facebook.react.bridge.ReadableMap

data class TorrentServerConfig(
  val streamingMode: String = DEFAULT_STREAMING_MODE,
  val profile: String = DEFAULT_PROFILE,
  val cacheSizeGb: Int = DEFAULT_CACHE_SIZE_GB,
  val port: Int = DEFAULT_PORT,
  val runAsForegroundService: Boolean = false,
) {
  companion object {
    const val DEFAULT_STREAMING_MODE = "regular_http"
    const val DEFAULT_PROFILE = "default"
    const val DEFAULT_CACHE_SIZE_GB = 5
    const val DEFAULT_PORT = 11100

    fun fromReadableMap(map: ReadableMap?): TorrentServerConfig {
      if (map == null) return TorrentServerConfig()

      return TorrentServerConfig(
        streamingMode = map.getString("streamingMode")?.takeIf { it in listOf("server", "regular_http") } ?: DEFAULT_STREAMING_MODE,
        profile = map.getString("profile")?.takeIf { it in listOf("default", "soft", "fast", "ultra_fast") } ?: DEFAULT_PROFILE,
        cacheSizeGb = map.takeIf { it.hasKey("cacheSizeGb") }?.getInt("cacheSizeGb")?.takeIf { it in listOf(0, 2, 5, 10, 20) } ?: DEFAULT_CACHE_SIZE_GB,
        port = map.takeIf { it.hasKey("port") }?.getInt("port")?.takeIf { it > 0 } ?: DEFAULT_PORT,
        runAsForegroundService = map.takeIf { it.hasKey("runAsForegroundService") }?.getBoolean("runAsForegroundService") ?: false,
      )
    }

    fun fromPreferences(prefs: SharedPreferences): TorrentServerConfig {
      return TorrentServerConfig(
        streamingMode = prefs.getString("streamingMode", DEFAULT_STREAMING_MODE) ?: DEFAULT_STREAMING_MODE,
        profile = prefs.getString("profile", DEFAULT_PROFILE) ?: DEFAULT_PROFILE,
        cacheSizeGb = prefs.getInt("cacheSizeGb", DEFAULT_CACHE_SIZE_GB),
        port = prefs.getInt("port", DEFAULT_PORT),
        runAsForegroundService = prefs.getBoolean("runAsForegroundService", false),
      )
    }
  }

  fun persist(editor: SharedPreferences.Editor) {
    editor
      .putString("streamingMode", streamingMode)
      .putString("profile", profile)
      .putInt("cacheSizeGb", cacheSizeGb)
      .putInt("port", port)
      .putBoolean("runAsForegroundService", runAsForegroundService)
      .apply()
  }
}
