package net.streamdek.mobile.nativeapp

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Largest span googlevideo will serve in one request. Measured, not guessed: 10 MiB is answered,
 * 16 MiB and a whole-file request are both refused. 8 MiB leaves room for the cap to move without
 * taking trailers down again, and still covers a typical trailer in three requests.
 */
private const val GOOGLEVIDEO_CHUNK_BYTES = 8L * 1024 * 1024

/**
 * googlevideo refuses to serve a media URL to an open-ended byte request.
 *
 * The URLs YouTube's player API hands back carry `rqh=1`, and the server holds to it: no Range
 * header answers 403, `bytes=0-` answers 403, and a bounded range larger than about 10 MiB answers
 * 403. Only a bounded request for a modest span is served. ExoPlayer's progressive source opens the
 * whole file in one unbounded request, so every native trailer failed with "Source error /
 * Response code: 403" and fell through to the iframe embed, which then reported ERROR of its own —
 * the trailer simply never appeared.
 *
 * This splits one logical read into a series of bounded requests. It uses YouTube's own `range`
 * query parameter rather than an HTTP Range header: the response is then an ordinary 200 whose body
 * is exactly the requested span, so the upstream source needs no partial-content handling.
 *
 * Only googlevideo media URLs are rewritten. Everything else — a direct .mp4 trailer, an HLS
 * playlist, a local file — is passed straight through, so this cannot affect any other playback.
 */
@UnstableApi
internal class ChunkedGoogleVideoDataSource(
  private val upstream: DataSource,
  private val chunkBytes: Long = GOOGLEVIDEO_CHUNK_BYTES,
) : DataSource {

  private var originalSpec: DataSpec? = null
  private var chunking = false
  private var position = 0L
  /** Bytes still owed to the caller, or [C.LENGTH_UNSET] when the total size is unknown. */
  private var bytesRemaining = C.LENGTH_UNSET.toLong()
  private var chunkRequested = 0L
  private var chunkReceived = 0L
  private var upstreamOpen = false

  override fun addTransferListener(transferListener: TransferListener) {
    upstream.addTransferListener(transferListener)
  }

  override fun getUri(): Uri? = upstream.uri ?: originalSpec?.uri

  override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

  override fun open(dataSpec: DataSpec): Long {
    val url = dataSpec.uri.toString()
    chunking = requiresChunkedRange(url)
    if (!chunking) {
      upstreamOpen = true
      return upstream.open(dataSpec)
    }
    originalSpec = dataSpec
    position = dataSpec.position
    bytesRemaining = when {
      dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
      // Every one of these URLs states its own size in `clen`, which spares the extra round trip
      // a probe request would cost and lets the player know the real length up front.
      else -> contentLengthOf(url)?.let { (it - position).coerceAtLeast(0L) }
        ?: C.LENGTH_UNSET.toLong()
    }
    openChunk()
    return bytesRemaining
  }

  private fun openChunk() {
    val spec = originalSpec ?: return
    chunkRequested = if (bytesRemaining == C.LENGTH_UNSET.toLong()) chunkBytes else minOf(chunkBytes, bytesRemaining)
    chunkReceived = 0L
    val rangedUri = spec.uri.buildUpon()
      .appendQueryParameter("range", "$position-${position + chunkRequested - 1}")
      .build()
    // The chunk response is the span itself, starting at byte zero of its own body.
    upstream.open(spec.buildUpon().setUri(rangedUri).setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build())
    upstreamOpen = true
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (!chunking) return upstream.read(buffer, offset, length)
    if (length == 0) return 0
    if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

    val read = upstream.read(buffer, offset, length)
    if (read != C.RESULT_END_OF_INPUT) {
      position += read
      chunkReceived += read
      if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
      return read
    }

    // A chunk that came up short is the end of the file — the only other way out of this loop when
    // the total size is unknown, and the guard that stops it spinning on an empty response.
    if (chunkReceived < chunkRequested) return C.RESULT_END_OF_INPUT
    if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
    upstream.close()
    upstreamOpen = false
    openChunk()
    return read(buffer, offset, length)
  }

  override fun close() {
    if (upstreamOpen) {
      upstreamOpen = false
      upstream.close()
    }
    originalSpec = null
    chunking = false
    position = 0L
    bytesRemaining = C.LENGTH_UNSET.toLong()
    chunkRequested = 0L
    chunkReceived = 0L
  }
}

/**
 * Whether [url] is one of the media URLs that has to be fetched in bounded spans.
 *
 * Deliberately narrow. HLS segments live on the same hosts but carry their range in the path and
 * are already small, so rewriting them would break a working path for nothing.
 *
 * Reads the URL as text rather than through [Uri] so the rule stays a plain function that a unit
 * test can call.
 */
internal fun requiresChunkedRange(url: String): Boolean {
  val withoutScheme = url.substringAfter("://", missingDelimiterValue = "")
  if (withoutScheme.isEmpty()) return false
  val authority = withoutScheme.substringBefore('/').substringBefore('?')
  val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
  if (!host.endsWith("googlevideo.com")) return false
  val path = withoutScheme.substringBefore('?').removePrefix(authority)
  if (path.contains("/range/", ignoreCase = true)) return false
  if (urlParameter(url, "range") != null) return false
  return urlParameter(url, "clen") != null || urlParameter(url, "rqh") == "1"
}

/** The `clen` parameter googlevideo puts on every one of these URLs: the full size of the stream. */
internal fun contentLengthOf(url: String): Long? =
  urlParameter(url, "clen")?.toLongOrNull()?.takeIf { it > 0 }

private fun urlParameter(url: String, name: String): String? {
  val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
  if (query.isEmpty()) return null
  return query.split('&').firstNotNullOfOrNull { pair ->
    val separator = pair.indexOf('=')
    when {
      separator <= 0 -> null
      pair.substring(0, separator) != name -> null
      else -> pair.substring(separator + 1)
    }
  }
}

/**
 * Data sources for trailer playback: ordinary HTTP, with googlevideo URLs fetched in bounded spans.
 */
@UnstableApi
internal fun trailerDataSourceFactory(context: Context, requestHeaders: Map<String, String>): DataSource.Factory {
  val httpFactory = DataSource.Factory {
    val http = DefaultHttpDataSource.Factory()
      .setDefaultRequestProperties(requestHeaders)
      .setConnectTimeoutMs(15_000)
      .setReadTimeoutMs(15_000)
      .setAllowCrossProtocolRedirects(true)
      .createDataSource()
    ChunkedGoogleVideoDataSource(http)
  }
  return DefaultDataSource.Factory(context, httpFactory)
}
