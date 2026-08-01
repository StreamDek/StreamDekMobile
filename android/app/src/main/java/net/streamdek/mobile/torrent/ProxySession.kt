package net.streamdek.mobile.torrent

data class ProxySession(
  val upstreamUrl: String,
  val headers: Map<String, String> = emptyMap(),
  val cacheKey: String? = null,
)
