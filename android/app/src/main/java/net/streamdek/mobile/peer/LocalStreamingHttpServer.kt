package net.streamdek.mobile.peer

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale

class LocalStreamingHttpServer(
  private val configProvider: () -> PeerStreamConfig,
  private val statusProvider: () -> Map<String, Any>,
  private val cacheStore: StreamCacheStore,
  private val peerEngine: PeerEngine,
) {
  companion object {
    private const val TAG = "StreamDekPeer"

    /**
     * How long the first byte of a range may take before the request is refused.
     *
     * Only the first byte waits. Everything after it is waited for inside the response body, where
     * a slow swarm shows up as playback buffering rather than as a request that fails.
     */
    private const val FIRST_BYTE_TIMEOUT_MS = 45_000L

    /**
     * How long a stalled read head is given before the response is abandoned.
     *
     * Closing the connection is the right answer to a swarm that has stopped: the player treats it
     * as a broken stream and re-requests the range it still needs, which is a far better position
     * than being handed a truncated file it believes is complete.
     */
    private const val STALL_TIMEOUT_MS = 60_000L

    private const val STREAM_BUFFER_BYTES = 128 * 1024
  }
  private data class ParsedRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
  )

  private var serverSocket: ServerSocket? = null
  private val running = AtomicBoolean(false)
  private val acceptExecutor = Executors.newSingleThreadExecutor()
  private val requestExecutor = Executors.newCachedThreadPool()

  fun start(port: Int): Int {
    stop()
    val socket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
    socket.reuseAddress = true
    serverSocket = socket
    running.set(true)

    acceptExecutor.execute {
      while (running.get()) {
        try {
          val client = socket.accept()
          requestExecutor.execute { handleClient(client) }
        } catch (_: SocketException) {
          running.set(false)
        } catch (_: Throwable) {
          // Keep the accept loop alive when possible.
        }
      }
    }

    return socket.localPort
  }

  fun stop() {
    running.set(false)
    try {
      serverSocket?.close()
    } catch (_: Throwable) {
    }
    serverSocket = null
  }

  private fun handleClient(socket: Socket) {
    socket.use { client ->
      val output = client.getOutputStream()
      try {
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val request = readRequest(reader) ?: return

        when {
          request.path == "/" || request.path.startsWith("/health") -> writeJson(
            output,
            """{"ok":true,"server":"streamdek-mobile","url":"http://127.0.0.1:${configProvider().port}"}"""
          )
          request.path.startsWith("/status") -> writeJson(output, toJson(statusProvider()))
          request.path.startsWith("/proxy/") -> proxyStream(
            output,
            request.method,
            request.path.removePrefix("/proxy/"),
            request.headers,
          )
          request.path.startsWith("/torrent/") -> streamPeerSession(
            output,
            request.method,
            request.path.removePrefix("/torrent/"),
            request.headers,
          )
          else -> writeResponse(output, 404, "text/plain; charset=utf-8", "Not found")
        }
      } catch (error: Throwable) {
        if (isClientDisconnect(error)) return
        try {
          writeResponse(output, 500, "text/plain; charset=utf-8", "Local server request failed")
        } catch (_: Throwable) {
        }
      }
    }
  }

  private fun readRequest(reader: BufferedReader): ParsedRequest? {
    val requestLine = reader.readLine() ?: return null
    val parts = requestLine.split(" ")
    val method = parts.getOrNull(0)?.uppercase(Locale.US) ?: "GET"
    val path = parts.getOrNull(1) ?: "/"
    return ParsedRequest(
      method = method,
      path = path,
      headers = readHeaders(reader),
    )
  }

  private fun readHeaders(reader: BufferedReader): Map<String, String> {
    val headers = linkedMapOf<String, String>()
    while (true) {
      val line = reader.readLine() ?: break
      if (line.isBlank()) break
      val idx = line.indexOf(':')
      if (idx <= 0) continue
      val key = line.substring(0, idx).trim().lowercase(Locale.US)
      val value = line.substring(idx + 1).trim()
      headers[key] = value
    }
    return headers
  }

  private fun proxyStream(
    output: OutputStream,
    method: String,
    sessionId: String,
    requestHeaders: Map<String, String>,
  ) {
    val session = PeerStreamService.getProxySession(sessionId)
    if (session == null) {
      writeResponse(output, 404, "text/plain; charset=utf-8", "Proxy session not found")
      return
    }

    val cacheKey = session.cacheKey
    val cacheEnabled = cacheKey != null && cacheStore.isEnabled(configProvider().cacheSizeGb)
    if (cacheEnabled && cacheKey != null && cacheStore.hasCompletedFile(cacheKey)) {
      serveCachedFile(output, method, cacheKey, requestHeaders)
      return
    }

    val connection = (URL(session.upstreamUrl).openConnection() as HttpURLConnection).apply {
      instanceFollowRedirects = true
      connectTimeout = 15_000
      readTimeout = 30_000
      requestMethod = if (method == "HEAD") "HEAD" else "GET"
      session.headers.forEach { (key, value) -> setRequestProperty(key, value) }
      requestHeaders["range"]?.let { setRequestProperty("Range", it) }
    }

    try {
      val statusCode = connection.responseCode
      val contentType = connection.contentType
      val bodyStream = try {
        connection.inputStream
      } catch (_: Throwable) {
        connection.errorStream
      }
      val shouldCache = cacheEnabled
        && requestHeaders["range"].isNullOrBlank()
        && statusCode == 200

      val cacheOutput = if (shouldCache && cacheKey != null) {
        cacheStore.openPartOutput(cacheKey)
      } else {
        null
      }

      writeProxyHeaders(output, connection, statusCode)
      if (method != "HEAD") {
        bodyStream?.use { stream ->
          cacheOutput?.use { fileOutput ->
            copyStream(stream, output, fileOutput)
          } ?: copyStream(stream, output)
        }
        output.flush()
      }
      if (shouldCache && cacheKey != null) {
        cacheStore.commitPart(cacheKey, contentType)
        cacheStore.enforceLimit(configProvider().cacheSizeGb)
      }
    } catch (error: Throwable) {
      if (cacheKey != null) cacheStore.abortPart(cacheKey)
      if (!isClientDisconnect(error)) {
        writeResponse(output, 502, "text/plain; charset=utf-8", "Upstream stream failed")
      }
    } finally {
      connection.disconnect()
    }
  }

  private fun streamPeerSession(
    output: OutputStream,
    method: String,
    sessionId: String,
    requestHeaders: Map<String, String>,
  ) {
    val session = PeerStreamService.getPeerPlaybackSession(sessionId)
    if (session == null) {
      writeResponse(output, 404, "text/plain; charset=utf-8", "Torrent session not found")
      return
    }

    val totalLength = session.fileLength
    if (session.filePath.isBlank() || totalLength <= 0L) {
      writeResponse(output, 503, "text/plain; charset=utf-8", "Torrent metadata not ready")
      return
    }
    val targetFile = PeerStreamService.peerTargetFile(sessionId) ?: File(session.saveDirectory, session.filePath)

    val rangeHeader = requestHeaders["range"]
    val (start, requestedEnd, statusCode) = parseRange(rangeHeader, totalLength)
    if (statusCode == 416) {
      writeRangeNotSatisfiable(output, totalLength)
      return
    }
    // The whole of what was asked for. Capping this at a fixed window was what made peer-to-peer
    // playback impossible rather than merely slow: a player is told by Content-Length how long the
    // file it is reading is, so a capped response is a complete file that happens to be a few
    // seconds long, and an MP4 with its index at the end cannot be opened at all.
    val end = minOf(requestedEnd, totalLength - 1)

    PeerStreamService.preparePeerRange(sessionId, start)
    // Only the first byte is waited for before the response begins. The rest is waited for while
    // the body is being written, which is what lets playback start on a torrent that is still
    // arriving instead of only on one that has already arrived.
    val waitStartedAt = System.currentTimeMillis()
    if (!PeerStreamService.awaitPeerByte(sessionId, start, FIRST_BYTE_TIMEOUT_MS)) {
      // A refusal here is the one outcome a player cannot explain for itself: it sees a failed
      // source and nothing about why. Said plainly so a dead swarm is distinguishable from a
      // reader that asked for a byte the engine was never going to fetch.
      android.util.Log.w(
        TAG,
        "no first byte at $start after ${System.currentTimeMillis() - waitStartedAt}ms; refusing range $start-$end",
      )
      writeResponse(output, 503, "text/plain; charset=utf-8", "Torrent data not ready")
      return
    }
    android.util.Log.d(
      TAG,
      "serving $start-$end/$totalLength; first byte took ${System.currentTimeMillis() - waitStartedAt}ms",
    )

    val contentLength = end - start + 1
    val contentType = guessContentType(session.filePath)
    val responseCode = if (statusCode == 206 || start > 0L || end < totalLength - 1) 206 else 200

    val response = buildString {
      append("HTTP/1.1 $responseCode ${reasonPhrase(responseCode)}\r\n")
      append("Content-Type: $contentType\r\n")
      append("Content-Length: $contentLength\r\n")
      append("Accept-Ranges: bytes\r\n")
      if (responseCode == 206) append("Content-Range: bytes $start-$end/$totalLength\r\n")
      append("Connection: close\r\n")
      append("\r\n")
    }.toByteArray(StandardCharsets.UTF_8)
    output.write(response)

    if (method == "HEAD") {
      output.flush()
      return
    }

    streamAsPiecesArrive(output, targetFile, sessionId, start, contentLength)
    output.flush()
  }

  /**
   * Writes [contentLength] bytes from [start], waiting at the read head for pieces still in flight.
   *
   * The file is read directly rather than through libtorrent, so the piece picker — not the file's
   * length — decides what may be read: a sparse file reports its finished size from the moment it
   * is created, and reading past what has arrived would quietly serve zeroes.
   */
  private fun streamAsPiecesArrive(
    output: OutputStream,
    targetFile: File,
    sessionId: String,
    start: Long,
    contentLength: Long,
  ) {
    val buffer = ByteArray(STREAM_BUFFER_BYTES)
    var offset = start
    var remaining = contentLength

    RandomAccessFile(targetFile, "r").use { file ->
      file.seek(start)
      while (remaining > 0L) {
        var available = PeerStreamService.peerContiguousBytes(sessionId, offset)
        if (available <= 0L) {
          // The read head has caught up with the swarm. Move the deadline window to where the
          // reader actually is before waiting, so the pieces being asked for are the next ones.
          PeerStreamService.preparePeerRange(sessionId, offset)
          if (!PeerStreamService.awaitPeerByte(sessionId, offset, STALL_TIMEOUT_MS)) break
          available = PeerStreamService.peerContiguousBytes(sessionId, offset)
          if (available <= 0L) break
        }

        val chunk = minOf(available, remaining, buffer.size.toLong()).toInt()
        val read = file.read(buffer, 0, chunk)
        if (read <= 0) {
          android.util.Log.w(TAG, "read returned $read at $offset; ${remaining}B of the range unserved")
          break
        }
        output.write(buffer, 0, read)
        offset += read
        remaining -= read
      }
    }
    logStreamOutcome(start, offset, remaining)
  }

  /** Whether a stalled read head or a finished range ended the body, and how much got through. */
  private fun logStreamOutcome(start: Long, offset: Long, remaining: Long) {
    if (remaining <= 0L) {
      android.util.Log.d(TAG, "served the whole range from $start (${offset - start} bytes)")
    } else {
      android.util.Log.w(TAG, "stalled at $offset after ${offset - start} bytes; ${remaining}B unserved")
    }
  }

  private fun serveCachedFile(
    output: OutputStream,
    method: String,
    cacheKey: String,
    requestHeaders: Map<String, String>,
  ) {
    val file = cacheStore.completedFile(cacheKey)
    if (!file.exists()) {
      writeResponse(output, 404, "text/plain; charset=utf-8", "Cached stream not found")
      return
    }

    cacheStore.touch(cacheKey)
    val totalLength = file.length()
    val rangeHeader = requestHeaders["range"]
    val (start, end, statusCode) = parseRange(rangeHeader, totalLength)
    if (statusCode == 416) {
      writeRangeNotSatisfiable(output, totalLength)
      return
    }
    val contentLength = end - start + 1
    val contentType = cacheStore.readContentType(cacheKey) ?: "application/octet-stream"

    val response = buildString {
      append("HTTP/1.1 $statusCode ${reasonPhrase(statusCode)}\r\n")
      append("Content-Type: $contentType\r\n")
      append("Content-Length: $contentLength\r\n")
      append("Accept-Ranges: bytes\r\n")
      if (statusCode == 206) append("Content-Range: bytes $start-$end/$totalLength\r\n")
      append("Connection: close\r\n")
      append("\r\n")
    }.toByteArray(StandardCharsets.UTF_8)
    output.write(response)
    if (method == "HEAD") {
      output.flush()
      return
    }

    FileInputStream(file).use { input ->
      skipFully(input, start)
      copyFixedLength(input, output, contentLength)
    }
    output.flush()
  }

  private fun writeProxyHeaders(output: OutputStream, connection: HttpURLConnection, statusCode: Int) {
    val contentType = connection.contentType ?: "application/octet-stream"
    val contentLength = connection.getHeaderField("Content-Length")
    val acceptRanges = connection.getHeaderField("Accept-Ranges")
    val contentRange = connection.getHeaderField("Content-Range")

    val response = buildString {
      append("HTTP/1.1 $statusCode ${reasonPhrase(statusCode)}\r\n")
      append("Content-Type: $contentType\r\n")
      if (!contentLength.isNullOrBlank()) append("Content-Length: $contentLength\r\n")
      if (!acceptRanges.isNullOrBlank()) append("Accept-Ranges: $acceptRanges\r\n")
      if (!contentRange.isNullOrBlank()) append("Content-Range: $contentRange\r\n")
      append("Connection: close\r\n")
      append("\r\n")
    }.toByteArray(StandardCharsets.UTF_8)

    output.write(response)
  }

  private fun writeRangeNotSatisfiable(output: OutputStream, totalLength: Long) {
    val response = buildString {
      append("HTTP/1.1 416 ${reasonPhrase(416)}\r\n")
      append("Content-Range: bytes */$totalLength\r\n")
      append("Content-Length: 0\r\n")
      append("Connection: close\r\n")
      append("\r\n")
    }.toByteArray(StandardCharsets.UTF_8)
    output.write(response)
    output.flush()
  }

  private fun copyStream(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val read = input.read(buffer)
      if (read <= 0) break
      output.write(buffer, 0, read)
    }
  }

  private fun copyStream(input: InputStream, output: OutputStream, cacheOutput: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val read = input.read(buffer)
      if (read <= 0) break
      output.write(buffer, 0, read)
      cacheOutput.write(buffer, 0, read)
    }
  }

  private fun copyFixedLength(input: InputStream, output: OutputStream, length: Long) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = length
    while (remaining > 0) {
      val nextRead = minOf(buffer.size.toLong(), remaining).toInt()
      val read = input.read(buffer, 0, nextRead)
      if (read <= 0) break
      output.write(buffer, 0, read)
      remaining -= read
    }
  }

  private fun skipFully(input: InputStream, bytes: Long) {
    var remaining = bytes
    while (remaining > 0) {
      val skipped = input.skip(remaining)
      if (skipped <= 0) break
      remaining -= skipped
    }
  }

  private fun writeJson(output: OutputStream, body: String) {
    writeResponse(output, 200, "application/json; charset=utf-8", body)
  }

  private fun writeResponse(output: OutputStream, statusCode: Int, contentType: String, body: String) {
    val payload = body.toByteArray(StandardCharsets.UTF_8)
    val response = buildString {
      append("HTTP/1.1 $statusCode ${reasonPhrase(statusCode)}\r\n")
      append("Content-Type: $contentType\r\n")
      append("Content-Length: ${payload.size}\r\n")
      append("Connection: close\r\n")
      append("\r\n")
    }.toByteArray(StandardCharsets.UTF_8)

    output.write(response)
    output.write(payload)
    output.flush()
  }

  private fun isClientDisconnect(error: Throwable): Boolean {
    val message = error.message?.lowercase(Locale.US).orEmpty()
    return error is SocketException
      || "broken pipe" in message
      || "connection reset" in message
      || "socket closed" in message
  }

  private fun reasonPhrase(statusCode: Int): String {
    return when (statusCode) {
      200 -> "OK"
      206 -> "Partial Content"
      503 -> "Service Unavailable"
      500 -> "Internal Server Error"
      502 -> "Bad Gateway"
      404 -> "Not Found"
      416 -> "Range Not Satisfiable"
      else -> "OK"
    }
  }

  private fun parseRange(rangeHeader: String?, totalLength: Long): Triple<Long, Long, Int> {
    if (totalLength <= 0L) {
      return Triple(0L, 0L, 200)
    }

    if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) {
      return Triple(0L, totalLength - 1, 200)
    }

    return try {
      val rangeValue = rangeHeader.removePrefix("bytes=")
      val parts = rangeValue.split("-", limit = 2)
      val startText = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
      val endText = parts.getOrNull(1)?.takeIf { it.isNotBlank() }

      val (start, end) = when {
        startText == null && endText != null -> {
          val suffixLength = endText.toLong().coerceAtLeast(0L)
          val boundedSuffixLength = minOf(suffixLength, totalLength)
          (totalLength - boundedSuffixLength) to (totalLength - 1)
        }
        startText != null -> {
          val start = startText.toLong()
          val requestedEnd = endText?.toLong()
          start to minOf(requestedEnd ?: (totalLength - 1), totalLength - 1)
        }
        else -> 0L to (totalLength - 1)
      }

      if (start < 0L || start >= totalLength || end < start) {
        Triple(0L, 0L, 416)
      } else {
        Triple(start, end, 206)
      }
    } catch (_: Throwable) {
      Triple(0L, totalLength - 1, 200)
    }
  }

  private fun toJson(value: Any?): String {
    return when (value) {
      null -> "null"
      is Number, is Boolean -> value.toString()
      is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
      is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
        toJson(key.toString()) + ":" + toJson(item)
      }
      is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { item -> toJson(item) }
      else -> toJson(value.toString())
    }
  }

  private fun guessContentType(path: String): String {
    val normalized = path.lowercase(Locale.US)
    return when {
      normalized.endsWith(".mp4") || normalized.endsWith(".m4v") -> "video/mp4"
      normalized.endsWith(".mkv") -> "video/x-matroska"
      normalized.endsWith(".webm") -> "video/webm"
      normalized.endsWith(".avi") -> "video/x-msvideo"
      normalized.endsWith(".mov") -> "video/quicktime"
      else -> "application/octet-stream"
    }
  }
}
