package net.streamdek.mobile.nativeapp

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI

/**
 * Cookies a stream's own servers set, remembered for the life of one player.
 *
 * Some IPTV CDNs authorise only the first request: Jio's HLSPartner feeds take a token in the
 * master playlist's query string, answer with a `Set-Cookie` carrying it, and then refuse every
 * variant playlist and segment (relative URLs, no query) that arrives without that cookie.
 * Media3's DefaultHttpDataSource keeps no cookies, so those streams failed with 403 one request in.
 *
 * Deliberately not [java.net.CookieHandler.setDefault]: that would change every HttpURLConnection
 * in the app. This jar is scoped to a single player, and a request is only touched when a server
 * in that session actually set a cookie that matches it - everything else goes out exactly as
 * before.
 */
internal class StreamCookieJar {
  // Only used for its store and its domain policy; never installed as the default handler.
  private val manager = java.net.CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

  fun store(url: String, responseHeaders: Map<String, List<String>>) {
    if (responseHeaders.keys.none { it.equals("Set-Cookie", ignoreCase = true) || it.equals("Set-Cookie2", ignoreCase = true) }) return
    val uri = runCatching { URI(url) }.getOrNull() ?: return
    // CookieManager skips the null status-line key itself; a malformed cookie is simply not kept.
    runCatching { manager.put(uri, responseHeaders) }
  }

  /**
   * The Cookie header to send to [url]: [existing] (from the playlist or addon) with any cookies
   * this session was given layered on top, a server-set cookie replacing a same-named one.
   * Returns null when the jar has nothing for [url], meaning the request should be left alone.
   */
  fun cookieHeaderFor(url: String, existing: String?): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
    val secure = uri.scheme.equals("https", ignoreCase = true)
    val cookies = runCatching { manager.cookieStore.get(uri) }.getOrNull().orEmpty().filter { cookie ->
      !cookie.hasExpired() &&
        (!cookie.secure || secure) &&
        path.startsWith(cookie.path?.takeIf { it.isNotEmpty() } ?: "/") &&
        (cookie.domain == null || HttpCookie.domainMatches(cookie.domain, host) || cookie.domain.equals(host, ignoreCase = true))
    }
    if (cookies.isEmpty()) return null
    // Written out by hand: HttpCookie.toString() quotes values of cookies it guesses are RFC 2965
    // ("max-age" is enough), which would alter signed tokens.
    val merged = linkedMapOf<String, String>()
    existing?.split(';')?.forEach { part ->
      val pair = part.trim()
      if (pair.isNotEmpty()) merged[pair.substringBefore('=').trim()] = pair
    }
    cookies.forEach { cookie -> merged[cookie.name] = "${cookie.name}=${cookie.value}" }
    return merged.values.joinToString("; ")
  }
}

/**
 * Wraps the HTTP data source factory so each request sends the player's [StreamCookieJar] cookies
 * and each response's cookies are kept. [staticHeaders] are the stream's own request headers; the
 * Cookie header is written back under whatever spelling they already use, since a second header
 * differing only by case would be sent alongside it.
 */
@OptIn(UnstableApi::class)
internal class CookieJarDataSourceFactory(
  private val upstream: DataSource.Factory,
  private val staticHeaders: Map<String, String>,
  private val jar: StreamCookieJar = StreamCookieJar(),
) : DataSource.Factory {
  override fun createDataSource(): DataSource = CookieJarDataSource(upstream.createDataSource(), staticHeaders, jar)
}

@OptIn(UnstableApi::class)
private class CookieJarDataSource(
  private val upstream: DataSource,
  private val staticHeaders: Map<String, String>,
  private val jar: StreamCookieJar,
) : DataSource {
  override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

  override fun open(dataSpec: DataSpec): Long {
    val requestUrl = dataSpec.uri.toString()
    val spec = if (requestUrl.startsWith("http", ignoreCase = true)) {
      val cookieKey = (dataSpec.httpRequestHeaders.keys + staticHeaders.keys).firstOrNull { it.equals("Cookie", ignoreCase = true) } ?: "Cookie"
      val existing = dataSpec.httpRequestHeaders[cookieKey] ?: staticHeaders[cookieKey]
      jar.cookieHeaderFor(requestUrl, existing)?.let { dataSpec.withAdditionalHeaders(mapOf(cookieKey to it)) } ?: dataSpec
    } else {
      dataSpec
    }
    val length = upstream.open(spec)
    // The URI after redirects, so a cookie is filed under the host that actually set it.
    runCatching { jar.store((upstream.uri ?: dataSpec.uri).toString(), upstream.responseHeaders) }
    return length
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)

  override fun getUri(): Uri? = upstream.uri

  override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

  override fun close() = upstream.close()
}
