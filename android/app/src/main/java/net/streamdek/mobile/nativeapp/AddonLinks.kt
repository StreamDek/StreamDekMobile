package net.streamdek.mobile.nativeapp

import java.net.URI

internal fun normalizeAddonManifestUrl(rawUrl: String): String? {
  val raw = rawUrl.trim()
  val normalized = when {
    raw.startsWith("stremio://", ignoreCase = true) -> "https://" + raw.substringAfter("://")
    raw.startsWith("https://", ignoreCase = true) || raw.startsWith("http://", ignoreCase = true) -> raw
    else -> return null
  }
  val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
  if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
  if (!uri.path.orEmpty().endsWith("/manifest.json", ignoreCase = true)) return null
  return normalized
}
