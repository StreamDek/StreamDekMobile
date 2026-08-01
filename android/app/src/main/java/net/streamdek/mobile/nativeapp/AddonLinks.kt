package net.streamdek.mobile.nativeapp

import java.net.URI

/**
 * True when [host] only makes sense relative to *this* device's own network stack: loopback
 * addresses, RFC1918 private ranges, the Android-emulator host alias, and bare mDNS/.local
 * names. Manifest URLs pointed at hosts like this can't be resolved by installing them through
 * a remote backend (a URL like http://127.0.0.1:11470 means something different on every
 * machine) — they need to be fetched directly from the phone instead.
 */
internal fun isLocalNetworkHost(host: String): Boolean {
  val value = host.trim().lowercase().removeSuffix(".")
  if (value.isEmpty()) return false
  if (value == "localhost" || value == "10.0.2.2" || value.endsWith(".local")) return true
  val octets = value.split(".").mapNotNull { it.toIntOrNull() }
  if (octets.size != 4 || octets.any { it !in 0..255 }) return false
  val (a, b, _, _) = octets
  return when {
    a == 127 -> true // loopback
    a == 10 -> true // 10.0.0.0/8
    a == 192 && b == 168 -> true // 192.168.0.0/16
    a == 172 && b in 16..31 -> true // 172.16.0.0/12
    a == 169 && b == 254 -> true // link-local
    else -> false
  }
}

internal fun isLocalNetworkUrl(rawUrl: String): Boolean =
  runCatching { URI(rawUrl.trim()).host?.let(::isLocalNetworkHost) }.getOrNull() ?: false

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
