package net.streamdek.mobile.torrent

import android.content.SharedPreferences

data class TorrentServerConfig(
  val enabled: Boolean = true,
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

    fun fromPreferences(prefs: SharedPreferences): TorrentServerConfig = TorrentServerConfig(
      enabled = prefs.getBoolean("enabled", true),
      streamingMode = prefs.getString("streamingMode", DEFAULT_STREAMING_MODE) ?: DEFAULT_STREAMING_MODE,
      profile = prefs.getString("profile", DEFAULT_PROFILE) ?: DEFAULT_PROFILE,
      cacheSizeGb = prefs.getInt("cacheSizeGb", DEFAULT_CACHE_SIZE_GB),
      port = prefs.getInt("port", DEFAULT_PORT),
      runAsForegroundService = prefs.getBoolean("runAsForegroundService", false),
    )
  }

  fun persist(editor: SharedPreferences.Editor) {
    editor
      .putBoolean("enabled", enabled)
      .putString("streamingMode", streamingMode)
      .putString("profile", profile)
      .putInt("cacheSizeGb", cacheSizeGb)
      .putInt("port", port)
      .putBoolean("runAsForegroundService", runAsForegroundService)
      .apply()
  }
}
